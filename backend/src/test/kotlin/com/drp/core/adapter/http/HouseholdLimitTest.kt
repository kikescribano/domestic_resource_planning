package com.drp.core.adapter.http

import com.drp.test.DrpPostgres
import com.drp.test.SpringIntegrationTest
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
 * El tope de hogares de la instalacion, visto por HTTP real: el ultimo hueco se
 * ocupa con un 202 y el siguiente alta responde 409 `HOUSEHOLD_LIMIT_REACHED`
 * --**la misma respuesta exista o no el correo**, que es la parte facil de
 * romper: distinguirlas reabriria por esta puerta el oraculo de quien esta
 * registrado que el 202 constante cierra por la suya.
 *
 * El tope no es un numero escrito sino **lo que haya mas uno**, contado al
 * arrancar el contexto: la base de datos se comparte con el resto de la suite y
 * cada clase que pasa antes deja sus hogares dentro. Contar en el arranque vale
 * porque la suite ejecuta las clases en secuencia y este contexto es el unico
 * que crea hogares mientras sus pruebas corren.
 *
 * Es **una sola prueba** y no varias a proposito: todas compartirian el mismo
 * hueco final, y el orden de metodos no esta garantizado.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HouseholdLimitTest : SpringIntegrationTest() {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun capacity(registry: DynamicPropertyRegistry) {
            registry.add("drp.enrollment.max-households") {
                DrpPostgres.instance.ownerConnection().use { connection ->
                    connection.createStatement().use { statement ->
                        val rows = statement.executeQuery("SELECT count(*) FROM households")
                        rows.next()
                        rows.getInt(1) + 1
                    }
                }
            }
        }
    }

    @Autowired private lateinit var http: TestRestTemplate

    @Test
    @DisplayName("el ultimo hueco entra, el siguiente responde 409, y el 409 no delata a nadie")
    fun `el tope de hogares se aplica sin delatar quien esta registrado`() {
        val occupant = uniqueEmail()

        // 1. El ultimo hueco se ocupa con normalidad.
        val lastSlot = http.postJson("/api/v1/households", householdBody(occupant, "La que cabe"))
        lastSlot.statusCode.shouldBe(HttpStatus.ACCEPTED)

        // 2. Con el tope alcanzado, un correo nuevo recibe el 409 con su codigo.
        val overNew = http.postJson("/api/v1/households", householdBody(uniqueEmail(), "La que ya no"))
        overNew.statusCode.shouldBe(HttpStatus.CONFLICT)
        overNew.body.shouldContain("HOUSEHOLD_LIMIT_REACHED")

        // 3. Y un correo que SI existe --el del paso 1-- recibe exactamente la
        //    misma respuesta, codigo y cuerpo: la instalacion llena no convierte
        //    el alta en un comprobador de direcciones.
        val overKnown = http.postJson("/api/v1/households", householdBody(occupant, "La que tampoco"))
        overKnown.statusCode.shouldBe(overNew.statusCode)
        overKnown.body.shouldBe(overNew.body)
    }

    private fun uniqueEmail() = "persona-${UUID.randomUUID()}@example.test"

    private fun householdBody(email: String, name: String) = """
        {"name":"$name","timeZone":"Europe/Madrid",
         "admin":{"name":"Kike","email":"$email","password":"el gato duerme en el sofa"}}
    """.trimIndent()

    private fun TestRestTemplate.postJson(path: String, body: String) =
        exchange<String>(
            path,
            HttpMethod.POST,
            HttpEntity(body, HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }),
        )
}
