package com.drp.core.adapter.http

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.io.ByteArrayInputStream
import java.time.Clock
import java.time.Duration
import java.util.Locale

/**
 * Limite de frecuencia sobre los endpoints sin autenticar (`429 RATE_LIMITED`).
 *
 * Se aplica en dos dimensiones, y las dos hacen falta porque protegen de cosas
 * distintas:
 *
 * - **Por IP.** Es lo que encarece enumerar: sin limite, quien quiera averiguar
 *   que correos estan registrados puede probar miles: las respuestas son
 *   constantes por diseno, pero nada impide intentarlo indefinidamente.
 * - **Por correo.** Es lo que impide inundar el buzon de una persona concreta.
 *   Sin el, cualquiera puede pedir "restablece tu contrasena" mil veces contra
 *   una direccion ajena, y repartir la peticion entre varias IP para esquivar el
 *   limite anterior. El destinatario no ha hecho nada y recibe mil correos.
 *
 * El limite por correo solo se aplica a los tres endpoints que **envian correo a
 * una direccion que llega en el cuerpo**. En el login limitar por correo seria
 * regalar una forma de bloquear la cuenta de otro.
 */
@Component
@Order(RATE_LIMIT_FILTER_ORDER)
class RateLimitFilter(
    private val objectMapper: ObjectMapper,
    clock: Clock,
    @Value("\${drp.rate-limit.window}") window: Duration,
    @Value("\${drp.rate-limit.per-ip}") private val perIpLimit: Int,
    @Value("\${drp.rate-limit.per-email}") private val perEmailLimit: Int,
) : OncePerRequestFilter() {

    private val limiter = FixedWindowRateLimiter(clock, window)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        // servletPath y no requestURI: este ultimo incluye el context-path por
        // especificacion de Servlet. Hoy es "/" y las dos formas coinciden, pero
        // el dia que la aplicacion se despliegue bajo un prefijo, ninguna ruta
        // casaria y **el limitador dejaria de aplicarse a los ocho endpoints sin
        // error y sin log**, que es la peor forma de fallo para un control de
        // seguridad: silenciosa y con apariencia de normalidad.
        val path = request.servletPath.ifEmpty { request.requestURI }
        if (path !in LIMITED_PATHS) {
            filterChain.doFilter(request, response)
            return
        }

        // El cuerpo se lee aqui para sacar el correo, y hay que dejarlo
        // disponible para el controlador: el flujo de una peticion solo se puede
        // leer una vez.
        val cached = CachedBodyRequest(request)
        if (cached.tooLarge) {
            response.rejectOversized()
            return
        }

        val retryAfter = limiter.consume("ip:$path:${request.clientIp()}", perIpLimit)
            ?: cached.emailBucketFor(path)?.let { limiter.consume(it, perEmailLimit) }

        if (retryAfter != null) {
            response.reject(retryAfter)
            return
        }

        filterChain.doFilter(cached, response)
    }

    /**
     * El cubo por correo, **solo** en los tres endpoints que mandan un correo a
     * una direccion que llega en el cuerpo.
     *
     * La restriccion no es cosmetica. Aplicarlo tambien al login regala una
     * forma de bloquear la cuenta de otro: basta conocer la direccion de alguien
     * y gastar su cubo para que reciba `429` durante toda la ventana, sin haber
     * hecho nada. Ese cubo existe para proteger a quien recibe los correos, no
     * para limitar a quien intenta entrar --de eso ya se ocupa el de IP.
     */
    private fun CachedBodyRequest.emailBucketFor(path: String): String? {
        if (path !in EMAIL_SENDING_PATHS) return null
        return emailInBody()?.let { "email:$path:$it" }
    }

    /**
     * La IP del cliente.
     *
     * **`X-Forwarded-For` se ignora a proposito mientras no haya proxy delante.**
     * La cabecera solo es fiable si la pone un intermediario de confianza; sin
     * el, la escribe entera quien hace la peticion, y basta variarla en cada
     * llamada para tener un cubo nuevo siempre --es decir, para no tener limite.
     * nginx llega en el Hito 3, y es entonces cuando esta cabecera pasa a
     * leerse, y solo si `remoteAddr` es el suyo.
     */
    private fun HttpServletRequest.clientIp(): String = remoteAddr ?: "desconocida"

    /** Busca `email` en la raiz y en `admin.email`, que son las dos formas del contrato. */
    private fun CachedBodyRequest.emailInBody(): String? = runCatching {
        if (body.isEmpty()) return null
        val root = objectMapper.readTree(body)
        val email = root.path("email").asText(null)
            ?: root.path("admin").path("email").asText(null)
        // Se normaliza igual que `EmailAddress` --recortando ademas de bajar a
        // minusculas-- para que el cubo no dependa de que la validacion de forma,
        // que corre despues que este filtro, rechace los rellenos.
        email?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    private fun HttpServletResponse.rejectOversized() {
        status = HttpStatus.PAYLOAD_TOO_LARGE.value()
        contentType = MediaType.APPLICATION_JSON_VALUE
        characterEncoding = Charsets.UTF_8.name()
        writer.write(
            objectMapper.writeValueAsString(
                ErrorResponse("VALIDATION_ERROR", "El cuerpo de la petición es demasiado grande"),
            ),
        )
    }

    private fun HttpServletResponse.reject(retryAfterSeconds: Long) {
        status = HttpStatus.TOO_MANY_REQUESTS.value()
        contentType = MediaType.APPLICATION_JSON_VALUE
        characterEncoding = Charsets.UTF_8.name()
        setHeader(HttpHeaders.RETRY_AFTER, retryAfterSeconds.toString())
        writer.write(
            objectMapper.writeValueAsString(
                ErrorResponse("RATE_LIMITED", "Demasiadas peticiones; espera un poco"),
            ),
        )
    }

    private companion object {
        /**
         * Los ocho endpoints sin autenticar del contrato. `/auth/refresh` entra
         * porque es la unica via de convertir un refresh robado en acceso, y el
         * alta de hogar porque es la unica escritura sin credencial de la API.
         */
        val LIMITED_PATHS = setOf(
            "/api/v1/households",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/verify-email",
            "/api/v1/auth/resend-verification",
            "/api/v1/auth/password-reset",
            "/api/v1/auth/password-reset/confirm",
            "/api/v1/invitations/accept",
        )

        /**
         * Los tres que mandan un correo a una direccion que viene en el cuerpo, y
         * los unicos donde el cubo por correo tiene sentido.
         */
        val EMAIL_SENDING_PATHS = setOf(
            "/api/v1/households",
            "/api/v1/auth/resend-verification",
            "/api/v1/auth/password-reset",
        )
    }
}

/**
 * Se ejecuta **antes** que la autenticacion.
 *
 * Es lo que se quiere: el limite tiene que aplicar aunque la peticion vaya a
 * fallar despues, porque son justo los intentos fallidos los que hay que
 * encarecer.
 */
const val RATE_LIMIT_FILTER_ORDER = -200

/**
 * Guarda el cuerpo para poder leerlo dos veces: una el limitador, para sacar el
 * correo, y otra el controlador.
 *
 * **Con tope.** Leer el cuerpo entero era lo primero que ocurria, antes incluso
 * de aplicar el limite, asi que un `POST` de cientos de megas contra un endpoint
 * anonimo se reservaba integro en el heap --y tambien cuando la peticion iba a
 * rechazarse con 429. Tomcat no acota por su cuenta un cuerpo `application/json`:
 * `max-http-form-post-size` solo cubre formularios.
 *
 * El tope es holgadisimo para lo que estos ocho endpoints reciben --el mayor es
 * un alta de hogar con cinco campos-- y sigue siendo pequeño para el heap.
 */
private class CachedBodyRequest(request: HttpServletRequest) : HttpServletRequestWrapper(request) {

    val body: ByteArray = request.inputStream.readNBytes(MAX_BODY_BYTES + 1)

    /** Si llego a leerse un byte de mas, el cuerpo pasa del tope. */
    val tooLarge: Boolean = body.size > MAX_BODY_BYTES

    override fun getInputStream(): ServletInputStream {
        val stream = ByteArrayInputStream(body)
        return object : ServletInputStream() {
            override fun read(): Int = stream.read()
            override fun isFinished(): Boolean = stream.available() == 0
            override fun isReady(): Boolean = true
            override fun setReadListener(listener: ReadListener?) = Unit
        }
    }

    override fun getReader(): java.io.BufferedReader =
        java.io.BufferedReader(java.io.InputStreamReader(inputStream, characterEncoding ?: Charsets.UTF_8.name()))

    companion object {
        /** 64 KiB. El cuerpo mas grande de estos ocho endpoints no llega a 1 KiB. */
        const val MAX_BODY_BYTES = 64 * 1024
    }
}
