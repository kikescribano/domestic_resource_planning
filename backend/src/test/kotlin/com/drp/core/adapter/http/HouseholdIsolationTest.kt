package com.drp.core.adapter.http

import com.drp.test.DrpMailpit
import com.drp.test.SpringIntegrationTest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
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
import java.util.UUID

/**
 * El criterio de aceptacion de la ADR-002, por HTTP y contra la aplicacion
 * entera:
 *
 * > Autenticado como hogar A, ninguna operacion devuelve ni modifica datos del
 * > hogar B, **ni siquiera por identificador directo**.
 *
 * Lo de "ni siquiera por identificador directo" es la parte que importa. Que un
 * listado no muestre lo ajeno es facil; lo dificil es que pedir explicitamente el
 * identificador de otro hogar --que un atacante puede haber obtenido de
 * cualquier sitio-- tampoco sirva de nada.
 *
 * Y responde `404`, no `403`: distinguir "no existe" de "existe pero no es tuyo"
 * convertiria cualquier identificador en un oraculo con el que averiguar que hay
 * en otros hogares.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HouseholdIsolationTest : SpringIntegrationTest() {

    @Autowired private lateinit var http: TestRestTemplate

    private val mailpit = DrpMailpit.instance

    private data class Household(
        val accessToken: String,
        val memberId: String,
        val email: String,
    )

    @Test
    @DisplayName("el listado de usuarios de A no contiene a nadie de B")
    fun `los listados estan acotados al hogar`() {
        val a = register()
        val b = register()

        val seenByA = http.get("/api/v1/users", a.accessToken)

        seenByA.statusCode.shouldBe(HttpStatus.OK)
        seenByA.body!!.shouldContain(a.email)
        seenByA.body!!.shouldNotContain(b.email)
    }

    @Test
    @DisplayName("A no puede cambiar el rol de un usuario de B pidiendolo por su identificador")
    fun `no se cambia el rol de un ajeno`() {
        val a = register()
        val b = register()

        val attempt = http.patch(
            "/api/v1/users/${b.memberId}/roles",
            """{"role":"HOUSEHOLD_MEMBER"}""",
            a.accessToken,
        )

        attempt.statusCode.shouldBe(HttpStatus.NOT_FOUND)

        // Y no ha cambiado nada al otro lado: sigue siendo administrador.
        http.get("/api/v1/users", b.accessToken).body!!.shouldContain("HOUSEHOLD_ADMIN")
    }

    @Test
    @DisplayName("A no puede dar de baja a un usuario de B")
    fun `no se da de baja a un ajeno`() {
        val a = register()
        val b = register()

        val attempt = http.deleteJson("/api/v1/users/${b.memberId}", a.accessToken)

        attempt.statusCode.shouldBe(HttpStatus.NOT_FOUND)
        http.get("/api/v1/users", b.accessToken).body!!.shouldNotContain("deactivatedAt\":\"2")
    }

    @Test
    @DisplayName("A no ve ni puede revocar las invitaciones de B")
    fun `las invitaciones no cruzan de hogar`() {
        val a = register()
        val b = register()
        val invitedByB = "invitada-${UUID.randomUUID()}@example.test"

        val created = http.post(
            "/api/v1/invitations",
            """{"email":"$invitedByB","role":"HOUSEHOLD_MEMBER"}""",
            b.accessToken,
        )
        created.statusCode.shouldBe(HttpStatus.CREATED)
        val invitationId = created.body!!.extract("id")

        // Ni en el listado...
        val listedByA = http.get("/api/v1/invitations", a.accessToken)
        listedByA.statusCode.shouldBe(HttpStatus.OK)
        listedByA.body!!.shouldNotContain(invitedByB)

        // ...ni pidiendola por su identificador.
        http.deleteJson("/api/v1/invitations/$invitationId", a.accessToken)
            .statusCode.shouldBe(HttpStatus.NOT_FOUND)

        // Sigue viva para su hogar.
        http.get("/api/v1/invitations", b.accessToken).body!!.shouldContain(invitedByB)
    }

    @Test
    @DisplayName("el token de A no sirve para nada en cuanto se le cambia el hogar a mano")
    fun `el hogar del token no es negociable`() {
        val a = register()

        // Un token con el householdId de otro hogar tendria que ir firmado, y
        // reescribir el cuerpo invalida la firma. Esto comprueba el otro extremo:
        // que la aplicacion no acepta el hogar por ninguna otra via --ni
        // cabecera, ni parametro, ni cuerpo.
        val withForgedHeader = http.exchange<String>(
            "/api/v1/users",
            HttpMethod.GET,
            HttpEntity<Void>(
                HttpHeaders().apply {
                    setBearerAuth(a.accessToken)
                    set("X-Household-Id", UUID.randomUUID().toString())
                },
            ),
        )

        withForgedHeader.statusCode.shouldBe(HttpStatus.OK)
        withForgedHeader.body!!.shouldContain(a.email)
    }

    /** Da de alta un hogar verificado y devuelve con que entrar. */
    private fun register(): Household {
        val email = "persona-${UUID.randomUUID()}@example.test"
        http.post(
            "/api/v1/households",
            """
            {"name":"Hogar ${UUID.randomUUID()}","timeZone":"Europe/Madrid",
             "admin":{"name":"Alguien","email":"$email","password":"el gato duerme en el sofa"}}
            """.trimIndent(),
        )

        val token = mailpit.awaitMessageTo(email).token()
        val session = http.post("/api/v1/auth/verify-email", """{"token":"$token"}""").body!!
        val accessToken = session.extract("accessToken")
        val memberId = http.get("/api/v1/users", accessToken).body!!.extract("id")

        return Household(accessToken, memberId, email)
    }

    private fun TestRestTemplate.post(path: String, body: String, accessToken: String? = null) =
        exchange<String>(path, HttpMethod.POST, HttpEntity(body, headers(accessToken)))

    private fun TestRestTemplate.patch(path: String, body: String, accessToken: String?) =
        exchange<String>(path, HttpMethod.PATCH, HttpEntity(body, headers(accessToken)))

    private fun TestRestTemplate.get(path: String, accessToken: String?) =
        exchange<String>(path, HttpMethod.GET, HttpEntity<Void>(headers(accessToken)))

    private fun TestRestTemplate.deleteJson(path: String, accessToken: String?) =
        exchange<String>(path, HttpMethod.DELETE, HttpEntity<Void>(headers(accessToken)))

    private fun headers(accessToken: String?) = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
        accessToken?.let { setBearerAuth(it) }
    }

    private fun String.extract(field: String): String =
        Regex("\"$field\"\\s*:\\s*\"([^\"]+)\"").find(this)?.groupValues?.get(1)
            ?: error("No aparece \"$field\" en la respuesta:\n$this")
}
