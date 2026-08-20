package com.drp.core.adapter.http

import com.drp.core.application.StorageKeys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.util.Base64

/**
 * Emite las URL firmadas con las que el navegador pinta una imagen (5.8.4).
 *
 * **Por que una URL firmada y no la cabecera `Authorization`.** Un `<img src>` no
 * puede enviarla: el HTML no ofrece ninguna forma de adjuntar cabeceras a la carga
 * de un subrecurso. Autenticar la imagen por cabecera obligaria al frontend a
 * descargarla con JavaScript y a renunciar a `loading="lazy"`, a `srcset`, a la
 * cache del navegador y al renderizado progresivo, justo en la pantalla donde mas
 * se nota: una rejilla de existencias en un movil.
 *
 * **La firma no es un token de sesion.** Es una autorizacion de solo lectura
 * sobre un unico objeto, con caducidad, que no da acceso a la cuenta ni a ningun
 * otro fichero.
 *
 * ## Por que MD5 y no HMAC-SHA256
 *
 * La seccion 5.8.4 la llama HMAC. Lo que verifica nginx **de serie** es el modulo
 * `secure_link`, y ese modulo solo sabe hacer MD5 con clave: no hay forma de que
 * compruebe un HMAC-SHA256 sin meter njs o Lua en el proxy. Y que nginx pueda
 * verificarla **sin preguntar a la aplicacion** es justamente el punto de la
 * decision, asi que lo que cede es el algoritmo.
 *
 * La debilidad real de MD5 en este uso es la **extension de longitud**, que
 * permitiria alargar un mensaje ya firmado si el secreto fuese por delante. Va
 * por detras --`caducidad + ruta + secreto`-- que es la posicion en la que ese
 * ataque no aplica. Las colisiones de MD5 tampoco sirven aqui: exigen elegir los
 * dos mensajes, y quien firma es el servidor.
 *
 * Queda anotado en 4.1.7 como desviacion consciente de lo escrito en 5.8.4.
 */
@Component
class SignedFileUrls(
    @Value("\${drp.files.base-url}") private val baseUrl: String,
    linkProperties: FileLinkProperties,
    @Value("\${drp.security.jwt.access-token-ttl}") private val ttl: Duration,
    private val clock: Clock,
) {

    /**
     * El secreto llega por [FileLinkProperties] y no por `@Value` para que **no
     * haya forma de usarlo sin haberlo validado**: quien lo construye es el bean
     * que comprueba al arrancar que no sigue siendo el de desarrollo.
     */
    private val secret: String = linkProperties.secret

    /** El original: lo que descarga quien pulsa una foto. */
    fun original(storageKey: String): String = sign(storageKey)

    /** La miniatura de 320 px, que es lo que va en una rejilla. */
    fun thumbnail(storageKey: String): String = sign(StorageKeys.thumbnailOf(storageKey))

    /**
     * **Caduca con el access token que la genero**, unos quince minutos. La
     * simetria no es estetica: cuando el frontend renueva el token vuelve a leer
     * las entidades y recibe URL frescas, asi que no hay dos relojes que cuadrar.
     *
     * De ahi una consecuencia para el contrato: **un `photoUrl` no se guarda**.
     * Vale para pintar ahora, no para almacenar en el estado del cliente ni para
     * compartir.
     */
    private fun sign(key: String): String {
        val path = "$PREFIX/$key"
        val expires = clock.instant().plus(ttl).epochSecond
        return "$baseUrl$path?e=$expires&s=${signatureOf(path, expires)}"
    }

    /**
     * Verifica una firma **igual que la verificaria nginx**.
     *
     * En produccion no la llama nadie: los ficheros los sirve nginx desde otro
     * dominio y esta ruta no llega a la aplicacion. Existe para el entorno de
     * desarrollo, donde no hay proxy delante y el mismo proceso tiene que
     * transmitir los bytes (5.8.4) — y para poder comprobar en una prueba que
     * una caducidad manipulada se rechaza, sin depender de que haya un nginx
     * levantado.
     *
     * La comparacion es en **tiempo constante**. Con el secreto en juego, un
     * `equals` normal filtra por cuanto tarda cuantos caracteres iniciales
     * acerto quien prueba.
     */
    fun verify(path: String, expires: String?, signature: String?): Boolean {
        val deadline = expires?.toLongOrNull() ?: return false
        if (deadline < clock.instant().epochSecond) return false

        val expected = signatureOf(path, deadline).toByteArray(Charsets.US_ASCII)
        val given = (signature ?: "").toByteArray(Charsets.US_ASCII)
        return MessageDigest.isEqual(expected, given)
    }

    /**
     * El mensaje es exactamente el que arma nginx en `secure_link_md5`, y el
     * orden importa en las dos puntas: cambiarlo aqui sin cambiarlo alli deja de
     * verificar **todo**, no una URL suelta.
     *
     * El secreto va **al final**, que es lo que impide la extension de longitud.
     */
    private fun signatureOf(path: String, expires: Long): String {
        val digest = MessageDigest.getInstance(DIGEST).digest("$expires$path $secret".toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    companion object {
        /** El `location` que nginx atiende con `secure_link`. */
        const val PREFIX = "/f"

        private const val DIGEST = "MD5"
    }
}

/**
 * El secreto con el que se firman las URL de ficheros, comprobado al arrancar.
 *
 * Es un **secreto de despliegue** exactamente igual que la clave del JWT, y lo
 * comparten dos procesos: lo usa la aplicacion para firmar y nginx para
 * verificar. El valor por defecto del `application.yml` solo sirve para
 * desarrollo --esta ademas en claro en el `compose.yaml`, porque nginx tiene que
 * conocerlo-- y el arranque falla si en produccion sigue puesto.
 *
 * Existe esta clase, y no un `@Value` suelto en [SignedFileUrls], porque un
 * secreto sin validar no se distingue de uno validado en el sitio donde se usa.
 */
data class FileLinkProperties(val secret: String) {

    /**
     * @param developmentEnvironment si el arranque es de desarrollo o de
     *   pruebas, unicos sitios donde se tolera el secreto de ejemplo.
     */
    fun validate(developmentEnvironment: Boolean) {
        require(secret.toByteArray(Charsets.UTF_8).size >= MINIMUM_SECRET_BYTES) {
            "El secreto de firma de ficheros necesita al menos $MINIMUM_SECRET_BYTES bytes"
        }

        // La que de verdad importa, y la que no existia. Con el secreto de
        // ejemplo puesto en produccion, cualquiera arma la firma de cualquier
        // ruta con la caducidad que quiera: la URL firmada deja de autorizar
        // nada y pasa a ser una direccion publica calculable.
        require(developmentEnvironment || secret != DEVELOPMENT_SECRET) {
            "El secreto de firma de ficheros sigue siendo el de desarrollo. " +
                "Define DRP_FILES_LINK_SECRET con un secreto propio antes de arrancar."
        }
    }

    companion object {
        /** El mismo suelo que la clave del JWT: no hay motivo para que sea menor. */
        const val MINIMUM_SECRET_BYTES = 32

        /**
         * El valor por defecto del `application.yml`. Vive aqui para que el
         * arranque pueda reconocerlo y rechazarlo fuera de desarrollo; si se
         * cambia alli --y en el `compose.yaml`, que tiene que coincidir-- hay que
         * cambiarlo aqui.
         */
        const val DEVELOPMENT_SECRET = "desarrollo-local-no-usar-en-produccion"
    }
}
