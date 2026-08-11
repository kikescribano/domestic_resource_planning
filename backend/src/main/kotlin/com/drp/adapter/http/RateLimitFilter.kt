package com.drp.adapter.http

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
        val path = request.requestURI
        if (path !in LIMITED_PATHS) {
            filterChain.doFilter(request, response)
            return
        }

        // El cuerpo se lee aqui para sacar el correo, y hay que dejarlo
        // disponible para el controlador: el flujo de una peticion solo se puede
        // leer una vez.
        val cached = CachedBodyRequest(request)

        val retryAfter = limiter.consume("ip:$path:${request.clientIp()}", perIpLimit)
            ?: cached.emailInBody()?.let { limiter.consume("email:$path:$it", perEmailLimit) }

        if (retryAfter != null) {
            response.reject(retryAfter)
            return
        }

        filterChain.doFilter(cached, response)
    }

    private fun HttpServletRequest.clientIp(): String =
        getHeader("X-Forwarded-For")
            // Detras de nginx (Hito 3) la IP real es la primera de la lista; la
            // ultima es la del propio proxy. Sin proxy delante, esta cabecera no
            // deberia llegar nunca del cliente.
            ?.substringBefore(',')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: remoteAddr
            ?: "desconocida"

    /** Busca `email` en la raiz y en `admin.email`, que son las dos formas del contrato. */
    private fun CachedBodyRequest.emailInBody(): String? = runCatching {
        if (body.isEmpty()) return null
        val root = objectMapper.readTree(body)
        val email = root.path("email").asText(null)
            ?: root.path("admin").path("email").asText(null)
        email?.lowercase(Locale.ROOT)
    }.getOrNull()

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
 */
private class CachedBodyRequest(request: HttpServletRequest) : HttpServletRequestWrapper(request) {

    val body: ByteArray = request.inputStream.readBytes()

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
}
