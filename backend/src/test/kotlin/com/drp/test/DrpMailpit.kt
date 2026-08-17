package com.drp.test

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Mailpit levantado por Testcontainers.
 *
 * Es lo que permite cumplir el criterio de validacion de la ADR-009 --leer el
 * correo real de verificacion, sin ningun paso manual-- **sin depender de
 * `compose.yaml`**. La distincion importa: el compose es para ejecutar la
 * aplicacion, y las pruebas levantan y destruyen lo suyo. Asi la CI corre sin
 * arrancar nada a mano y la maquina queda como estaba.
 */
class DrpMailpit : GenericContainer<DrpMailpit>(DockerImageName.parse(IMAGE)) {

    companion object {
        private const val IMAGE = "axllent/mailpit:v1.21"
        private const val SMTP_PORT = 1025
        private const val HTTP_PORT = 8025

        val instance: DrpMailpit by lazy {
            DrpMailpit()
                .withExposedPorts(SMTP_PORT, HTTP_PORT)
                .withEnv("MP_SMTP_AUTH_ACCEPT_ANY", "1")
                .withEnv("MP_SMTP_AUTH_ALLOW_INSECURE", "1")
                .waitingFor(Wait.forHttp("/readyz").forPort(HTTP_PORT))
                .also { it.start() }
        }
    }

    private val http: HttpClient = HttpClient.newHttpClient()
    private val json = ObjectMapper()

    val smtpPort: Int get() = getMappedPort(SMTP_PORT)

    private val apiBase: String get() = "http://$host:${getMappedPort(HTTP_PORT)}/api/v1"

    /** Borra el buzon. Se llama entre pruebas para que ninguna vea el correo de otra. */
    fun clear() {
        http.send(
            HttpRequest.newBuilder(URI("$apiBase/messages")).DELETE().build(),
            HttpResponse.BodyHandlers.discarding(),
        )
    }

    /**
     * El ultimo mensaje entregado a esa direccion, con su cuerpo.
     *
     * Espera activamente porque el envio ocurre **fuera de la transaccion**
     * (ADR-009) y porque Mailpit tarda un instante en indexar lo que recibe:
     * cuando la prueba recibe el `202`, el correo puede no estar todavia en su
     * API.
     *
     * El plazo es holgado a proposito. La conexion SMTP contra el contenedor
     * cuesta segundos en algunos equipos, y un plazo justo convierte esa lentitud
     * en una prueba intermitente --que es peor que una que falla, porque se
     * aprende a ignorarla.
     */
    fun awaitMessageTo(recipient: String, timeoutMillis: Long = 30_000): MailpitMessage {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            findMessageTo(recipient)?.let { return it }
            Thread.sleep(100)
        }
        error("No llegó ningún correo a $recipient en $timeoutMillis ms")
    }

    /**
     * Comprueba que **no** llega correo. Espera el plazo entero a proposito.
     *
     * Tiene que ser mayor que lo que tarda una entrega real: si fuese mas corto,
     * esta comprobacion pasaria tambien cuando el correo si se envia y solo va
     * despacio, que es justo lo contrario de lo que quiere demostrar.
     */
    fun assertNoMessageTo(recipient: String, waitMillis: Long = 8_000) {
        Thread.sleep(waitMillis)
        val found = findMessageTo(recipient)
        check(found == null) { "Llegó un correo a $recipient y no debía: ${found?.subject}" }
    }

    fun messageCount(): Int = getJson("$apiBase/messages").path("total").asInt()

    private fun findMessageTo(recipient: String): MailpitMessage? {
        val summary = getJson("$apiBase/messages").path("messages")
            .firstOrNull { message ->
                message.path("To").any { it.path("Address").asText().equals(recipient, ignoreCase = true) }
            }
            ?: return null

        val id = summary.path("ID").asText()
        val full = getJson("$apiBase/message/$id")
        return MailpitMessage(
            subject = full.path("Subject").asText(),
            body = full.path("Text").asText(),
        )
    }

    private fun getJson(url: String): JsonNode {
        val response = http.send(
            HttpRequest.newBuilder(URI(url)).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        return json.readTree(response.body())
    }
}

data class MailpitMessage(val subject: String, val body: String) {

    /**
     * El token del enlace que viaja en el correo.
     *
     * Se extrae del cuerpo tal cual lo recibe una persona, y no de la base de
     * datos ni de un gancho de pruebas: la gracia del recorrido vertical es que
     * atraviese el mismo camino que atraviesa un usuario.
     */
    fun token(): String = TOKEN_IN_LINK.find(body)?.groupValues?.get(1)
        ?: error("El correo no lleva ningún enlace con token:\n$body")

    private companion object {
        /**
         * El punto entra en la clase por el token acotado de prestamo, que es el
         * unico de los cinco que **no es un secreto opaco sino un JWT**: viaja
         * como `cabecera.cuerpo.firma`, asi que sin el punto se extraia solo la
         * cabecera y el enlace parecia no valer. Los otros cuatro son base64url
         * sin puntos, de modo que ampliar la clase no los afecta.
         *
         * El enlace va siempre en una linea propia, asi que no hay punto final de
         * frase que se pueda colar detras.
         */
        val TOKEN_IN_LINK = Regex("""[?&]token=([A-Za-z0-9_\-.]+)""")
    }
}
