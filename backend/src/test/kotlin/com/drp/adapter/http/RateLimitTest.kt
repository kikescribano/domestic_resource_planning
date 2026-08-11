package com.drp.adapter.http

import com.drp.test.DrpMailpit
import com.drp.test.DrpPostgres
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.client.exchange
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.util.UUID

/**
 * El limite de frecuencia de los endpoints sin autenticar (`429 RATE_LIMITED`).
 *
 * Tiene sus propias propiedades porque las demas pruebas lo desactivan de hecho
 * --hacen muchas llamadas seguidas desde la misma IP-- y aqui es justo lo que se
 * quiere medir. Al ser un contexto de Spring distinto, no comparte contadores
 * con ninguna otra prueba.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RateLimitTest {

    @Autowired private lateinit var http: TestRestTemplate

    companion object {
        private const val PER_IP = 6
        private const val PER_EMAIL = 2

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            val postgres = DrpPostgres.instance
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { DrpPostgres.APP_USERNAME }
            registry.add("spring.datasource.password") { DrpPostgres.APP_PASSWORD }
            registry.add("spring.flyway.user") { DrpPostgres.OWNER_USERNAME }
            registry.add("spring.flyway.password") { DrpPostgres.OWNER_PASSWORD }

            val mailpit = DrpMailpit.instance
            registry.add("spring.mail.host") { mailpit.host }
            registry.add("spring.mail.port") { mailpit.smtpPort }

            registry.add("drp.rate-limit.window") { "5m" }
            registry.add("drp.rate-limit.per-ip") { PER_IP }
            registry.add("drp.rate-limit.per-email") { PER_EMAIL }
        }
    }

    @Test
    @DisplayName("pasado el limite por IP se responde 429 con Retry-After")
    fun `el limite por ip corta`() {
        // Un correo distinto en cada intento, para que lo que corte sea el limite
        // por IP y no el otro. Y una ruta distinta de la que usa la prueba de al
        // lado, porque el contador es por IP **y ruta**: compartirla haria que
        // una prueba se gastara el presupuesto de la otra.
        val responses = (1..PER_IP + 2).map {
            postJson("/api/v1/auth/resend-verification", "distinta-${UUID.randomUUID()}@example.test")
        }

        responses.take(PER_IP).forEach { it.statusCode.shouldBe(HttpStatus.ACCEPTED) }

        val rejected = responses.last()
        rejected.statusCode.shouldBe(HttpStatus.TOO_MANY_REQUESTS)
        rejected.body!!.shouldContain("RATE_LIMITED")
        rejected.headers.getFirst("Retry-After").shouldNotBeNull()
    }

    @Test
    @DisplayName("el limite por correo impide inundar el buzon de una persona concreta")
    fun `el limite por correo corta antes`() {
        // El mismo destinatario una y otra vez. Sin este limite, cualquiera puede
        // pedir "restablece tu contrasena" contra una direccion ajena tantas
        // veces como quiera, y repartir la peticion entre varias IP para esquivar
        // el limite anterior. Quien lo paga es alguien que no ha hecho nada.
        val victim = "victima-${UUID.randomUUID()}@example.test"

        val responses = (1..PER_EMAIL + 1).map { postJson("/api/v1/auth/password-reset", victim) }

        responses.take(PER_EMAIL).forEach { it.statusCode.shouldBe(HttpStatus.ACCEPTED) }
        responses.last().statusCode.shouldBe(HttpStatus.TOO_MANY_REQUESTS)
    }

    @Test
    @DisplayName("los endpoints autenticados no llevan limite")
    fun `lo autenticado no se limita`() {
        // El limite protege lo que se puede llamar sin credencial. Ponerlo
        // tambien donde hace falta token seria estorbar a un hogar de verdad para
        // no ganar nada: quien tiene token ya ha pasado por el que si limita.
        repeat(PER_IP + 5) {
            val response = http.exchange<String>(
                "/api/v1/users",
                HttpMethod.GET,
                HttpEntity<Void>(HttpHeaders()),
            )
            // Sin token responde 401, no 429: no lo esta cortando el limitador.
            response.statusCode.shouldBe(HttpStatus.UNAUTHORIZED)
        }
    }

    private fun postJson(path: String, email: String) = http.exchange<String>(
        path,
        HttpMethod.POST,
        HttpEntity(
            """{"email":"$email"}""",
            HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON },
        ),
    )
}
