package com.drp.core.adapter.http

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.web.util.matcher.IpAddressMatcher
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.io.ByteArrayInputStream
import java.time.Clock
import java.time.Duration
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

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
    @Value("\${drp.rate-limit.trusted-proxies:}") trustedProxies: List<String>,
) : OncePerRequestFilter() {

    private val limiter = FixedWindowRateLimiter(clock, window)
    private val clientIps = ClientIpResolver(trustedProxies)

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

        val retryAfter = limiter.consume("ip:$path:${clientIps.resolve(request)}", perIpLimit)
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
 * De quien es una peticion, para poder contarsela.
 *
 * ## Por que no basta `remoteAddr`
 *
 * Con nginx delante --que es como se despliega (5.8.4)-- `remoteAddr` es
 * **siempre la IP del proxy**, la misma para todo el mundo. El cubo `ip:<ruta>`
 * pasaba entonces a ser **uno solo para toda la instalacion**: veinte peticiones
 * cada cinco minutos entre todos los hogares juntos. Cualquiera podia dejar sin
 * login, sin refresco y sin restablecer contrasena a la instalacion entera con
 * veinte peticiones sin credencial, repetibles cada ventana; y una casa con uso
 * normal podia bloquearse sola. De paso, contar por IP dejaba de encarecer la
 * enumeracion, que es justo para lo que existe ese cubo.
 *
 * Estaba anotado en el codigo como pendiente «cuando llegue nginx en el Hito 3».
 * nginx llego --y manda las dos cabeceras-- y esta mitad no se escribio.
 *
 * ## Por que no basta leer `X-Forwarded-For`
 *
 * Porque **la escribe quien hace la peticion**. Sin comprobar de quien viene,
 * basta variarla en cada llamada para estrenar cubo siempre: el limite dejaria
 * de existir en vez de arreglarse, que es peor que el problema de partida. De
 * ahi que solo se lea cuando el salto inmediato --`remoteAddr`-- es un proxy
 * declarado de confianza.
 *
 * ## Y por que la **ultima** entrada de la cabecera
 *
 * `X-Forwarded-For` es una lista `cliente, proxy1, proxy2` y nginx la construye
 * con `$proxy_add_x_forwarded_for`, que **anade al final la IP que el mismo
 * observo**. Asi que si el cliente manda una cabecera inventada, nginx la deja
 * delante y pone detras la de verdad: la ultima entrada es la unica que no ha
 * podido escribir el atacante. Tomar la primera --el reflejo habitual, porque
 * «es el cliente original»-- seria leer exactamente el valor que el atacante
 * elige, con un solo proxy de confianza por delante.
 *
 * @param trustedProxies IP o rangos CIDR de los proxies de confianza. **Vacio
 *   significa no fiarse de nadie**, que es lo correcto sin proxy delante y lo
 *   que viene por defecto: quien despliega detras de nginx lo declara.
 */
class ClientIpResolver(trustedProxies: List<String>) {

    /**
     * Se construyen al arrancar a proposito: un rango mal escrito revienta la
     * aplicacion al levantarla y no en la primera peticion, que es cuando ya no
     * lo esta mirando nadie.
     */
    private val trusted = trustedProxies
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { IpAddressMatcher(it) }

    /**
     * Una vez por proceso basta: el sintoma se repite en cada peticion y un log
     * que grita lo mismo mil veces se deja de leer.
     */
    private val warnedUndeclaredProxy = AtomicBoolean(false)

    fun resolve(request: HttpServletRequest): String {
        val peer = request.remoteAddr?.takeIf { it.isNotBlank() } ?: return UNKNOWN
        if (trusted.none { it.matches(peer) }) {
            warnIfLooksLikeUndeclaredProxy(request, peer)
            return peer
        }

        return request.forwardedFor() ?: request.realIp() ?: peer
    }

    /**
     * La otra mitad del aviso de arranque de `TrustedProxiesStartupNotice`: la
     * firma en caliente del proxy sin declarar.
     *
     * Si no hay **ningun** proxy declarado y aun asi llegan las cabeceras que un
     * proxy pone, una de dos: o delante hay un proxy que nadie declaro --y
     * entonces el limite por IP esta contando a todos sus clientes en el mismo
     * cubo-- o quien llama manda la cabecera por su cuenta, que se ignora y ya
     * esta. El log no puede distinguirlas, y el mensaje dice las dos.
     *
     * Solo con la lista vacia, a proposito: con proxies declarados el operador
     * ya hizo su parte, y avisar de cada cliente que invente una cabecera seria
     * dejar que quien llama escriba en el log.
     */
    private fun warnIfLooksLikeUndeclaredProxy(request: HttpServletRequest, peer: String) {
        if (trusted.isNotEmpty()) return
        if (request.getHeader(FORWARDED_FOR) == null && request.getHeader(REAL_IP) == null) return
        if (!warnedUndeclaredProxy.compareAndSet(false, true)) return
        log.warn(
            "Llega X-Forwarded-For o X-Real-IP desde {} sin ningun proxy declarado en " +
                "drp.rate-limit.trusted-proxies. Si eso es un proxy, el limite por IP esta contando " +
                "a todos sus clientes en el mismo cubo: declara DRP_TRUSTED_PROXIES. Si no lo es, " +
                "alguien envia la cabecera por su cuenta y se ignora, que es lo correcto. " +
                "Solo se avisa una vez.",
            peer,
        )
    }

    /** La ultima entrada: la que puso el proxy de confianza y no quien llamo. */
    private fun HttpServletRequest.forwardedFor(): String? =
        getHeader(FORWARDED_FOR)
            ?.split(',')
            ?.map { it.trim() }
            ?.lastOrNull { it.isNotEmpty() }

    /**
     * Respaldo para un proxy que solo mande `X-Real-IP`. La nuestra la fija con
     * `$remote_addr`, que **sustituye** lo que trajera la peticion en lugar de
     * anadirse, asi que tampoco la elige quien llama.
     */
    private fun HttpServletRequest.realIp(): String? =
        getHeader(REAL_IP)?.trim()?.takeIf { it.isNotEmpty() }

    private companion object {
        val log = LoggerFactory.getLogger(ClientIpResolver::class.java)

        const val FORWARDED_FOR = "X-Forwarded-For"
        const val REAL_IP = "X-Real-IP"

        /**
         * Un contenedor de servlets siempre da `remoteAddr`; esto cubre el caso
         * de una peticion simulada sin el. Todas caen en el mismo cubo, que es
         * el lado seguro por el que equivocarse.
         */
        const val UNKNOWN = "desconocida"
    }
}

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
