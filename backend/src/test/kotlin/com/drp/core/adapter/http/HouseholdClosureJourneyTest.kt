package com.drp.core.adapter.http

import com.drp.core.application.usecase.RequestHouseholdClosure
import com.drp.test.DrpMailpit
import com.drp.test.DrpPostgres
import com.drp.test.SpringIntegrationTest
import com.drp.test.TestHousehold
import com.drp.test.count
import com.drp.test.deleteJson
import com.drp.test.extract
import com.drp.test.getJson
import com.drp.test.imageBytes
import com.drp.test.patchJson
import com.drp.test.postJson
import com.drp.test.registerHousehold
import com.drp.test.uploadFile
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Las cuatro operaciones que la baja de hogar y el cierre de cuenta anaden al
 * contrato (ADR-012), por HTTP y de punta a punta.
 *
 * Lo que **no** esta aqui es la purga, que necesita mover el reloj y vive en
 * `PurgeClosedHouseholdsTest`. Aqui esta lo que una persona hace: pedirla,
 * verla, cancelarla y cerrar su cuenta.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HouseholdClosureJourneyTest : SpringIntegrationTest() {

    @Autowired private lateinit var http: TestRestTemplate

    private val postgres = DrpPostgres.instance
    private val mailpit = DrpMailpit.instance

    // -----------------------------------------------------------------------
    // El estado del hogar
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("el estado del hogar se lee, y sin baja pedida la trae a nulo")
    fun `el hogar se lee`() {
        val home = http.registerHousehold()

        val body = http.getJson("/api/v1/households/current", home.accessToken).body!!

        body.extract("id").shouldBe(home.householdId)
        body.shouldContain("\"timeZone\":\"Europe/Madrid\"")
        // Nulo es lo normal, y es lo que el cliente mira para decidir si pinta el
        // aviso persistente.
        body.shouldContain("\"closure\":null")
    }

    @Test
    @DisplayName("el estado del hogar lo ve tambien quien no administra")
    fun `un miembro ve el estado`() {
        val home = http.registerHousehold()
        val member = http.inviteMember(home)

        // Que la casa esta a punto de desaparecer no es informacion reservada a
        // quien administra: enterarse no es lo mismo que poder hacerlo.
        http.getJson("/api/v1/households/current", member).statusCode.shouldBe(HttpStatus.OK)
    }

    @Test
    @DisplayName("sin sesion no se lee: NO es una operacion abierta como el alta")
    fun `el estado exige sesion`() {
        http.getJson("/api/v1/households/current").statusCode.shouldBe(HttpStatus.UNAUTHORIZED)
    }

    // -----------------------------------------------------------------------
    // Pedir la baja
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("pedir la baja marca el hogar con la fecha en la que desaparece")
    fun `la baja se pide`() {
        val home = http.registerHousehold()

        val requested = http.postJson("/api/v1/households/current/closure", "", home.accessToken)
        requested.statusCode.shouldBe(HttpStatus.OK)

        val closure = requested.body!!
        closure.shouldContain("\"closure\":")
        // La autoria apunta a la **pertenencia**, como en todo el modelo.
        closure.extract("requestedBy").shouldBe(home.memberId)

        // Y la gracia son treinta dias contados de verdad, no un campo puesto a
        // ojo: se mide contra el reloj, con holgura para no depender del segundo
        // exacto en que corriera la peticion.
        val effective = Instant.parse(closure.extract("effectiveAt"))
        val expected = Instant.now().plus(RequestHouseholdClosure.GRACE)
        val drift = Duration.between(effective, expected).abs()
        (drift < Duration.ofMinutes(5)).shouldBeTrue()

        // Se relee y sigue ahi: no es un dato de la respuesta sino del hogar.
        http.getJson("/api/v1/households/current", home.accessToken).body!!.shouldNotContain("\"closure\":null")
    }

    @Test
    @DisplayName("pedir la baja levanta UN aviso, con la fecha dentro")
    fun `la baja levanta un aviso`() {
        val home = http.registerHousehold()
        http.postJson("/api/v1/households/current/closure", "", home.accessToken)

        val notices = http.getJson("/api/v1/notices", home.accessToken).body!!
        notices.shouldContain("HOUSEHOLD_CLOSURE_REQUESTED")
        // Lleva **la fecha** y no «en 30 días», que es la diferencia entre
        // saberlo y tener que contar.
        notices.shouldContain("El hogar se borrará el")

        // Y **uno solo**: lo levanta el caso de uso, no el recorrido diario, que
        // lo repetiria cada noche durante treinta dias --y un aviso no se repite
        // mientras la condicion siga siendo cierta (ADR-011).
        countNotices(UUID.fromString(home.householdId)).shouldBe(1)
    }

    @Test
    @DisplayName("pedirla dos veces responde 409 y NO reinicia los treinta dias")
    fun `la baja no se pide dos veces`() {
        val home = http.registerHousehold()
        val first = http.postJson("/api/v1/households/current/closure", "", home.accessToken).body!!

        val again = http.postJson("/api/v1/households/current/closure", "", home.accessToken)
        again.statusCode.shouldBe(HttpStatus.CONFLICT)
        again.body!!.shouldContain("HOUSEHOLD_CLOSURE_ALREADY_REQUESTED")

        // La fecha prometida no se ha movido, que es el motivo entero de que esto
        // no sea idempotente.
        //
        // Se comparan **instantes y no cadenas**: la respuesta del `POST` sale
        // del objeto recien guardado, con la precision de nanosegundos del reloj
        // de Java, y la de la lectura sale de PostgreSQL, que guarda
        // microsegundos. Es el mismo instante truncado, y compararlo como texto
        // haria fallar esta prueba por algo que no es lo que mide.
        val now = http.getJson("/api/v1/households/current", home.accessToken).body!!
        Instant.parse(now.extract("effectiveAt")).truncatedTo(ChronoUnit.MILLIS)
            .shouldBe(Instant.parse(first.extract("effectiveAt")).truncatedTo(ChronoUnit.MILLIS))
    }

    @Test
    @DisplayName("un miembro no puede pedir la baja del hogar")
    fun `solo quien administra pide la baja`() {
        val home = http.registerHousehold()
        val member = http.inviteMember(home)

        http.postJson("/api/v1/households/current/closure", "", member)
            .statusCode.shouldBe(HttpStatus.FORBIDDEN)
    }

    @Test
    @DisplayName("durante la gracia el hogar funciona EXACTAMENTE igual")
    fun `la gracia no pone el hogar en solo lectura`() {
        val home = http.registerHousehold()
        http.postJson("/api/v1/households/current/closure", "", home.accessToken)

        // Escribir, subir y leer. Dejarlo de solo lectura castigaria justo a
        // quien todavia puede cancelar, que es la razon de que la gracia exista.
        http.postJson("/api/v1/categories", """{"name":"Después de pedirla"}""", home.accessToken)
            .statusCode.shouldBe(HttpStatus.CREATED)
        http.uploadFile(home.accessToken, imageBytes("png"), "sigue.png", "image/png")
            .statusCode.shouldBe(HttpStatus.CREATED)
        http.getJson("/api/v1/assets", home.accessToken).statusCode.shouldBe(HttpStatus.OK)
    }

    // -----------------------------------------------------------------------
    // Cancelarla
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("cancelar la baja la retira y no toca ningun dato")
    fun `la baja se cancela`() {
        val home = http.registerHousehold()
        val category = http.postJson(
            "/api/v1/categories",
            """{"name":"Antes de pedirla"}""",
            home.accessToken,
        ).body!!.extract("id")

        http.postJson("/api/v1/households/current/closure", "", home.accessToken)
        http.deleteJson("/api/v1/households/current/closure", home.accessToken)
            .statusCode.shouldBe(HttpStatus.OK)

        http.getJson("/api/v1/households/current", home.accessToken).body!!.shouldContain("\"closure\":null")
        // No habia nada que restaurar, y por eso sigue todo: durante la gracia no
        // se toco un solo dato.
        http.getJson("/api/v1/categories", home.accessToken).body!!.shouldContain(category)
    }

    @Test
    @DisplayName("el aviso de la solicitud SOBREVIVE a la cancelacion")
    fun `cancelar no borra el aviso`() {
        val home = http.registerHousehold()
        http.postJson("/api/v1/households/current/closure", "", home.accessToken)
        http.deleteJson("/api/v1/households/current/closure", home.accessToken)

        // Es la regla de la ADR-011: un aviso es el texto que se escribio ese dia
        // y no una vista de lo que ocurre hoy. «Se pidio la baja» siguio siendo
        // cierto aunque despues se cancelara.
        http.getJson("/api/v1/notices", home.accessToken).body!!.shouldContain("HOUSEHOLD_CLOSURE_REQUESTED")
    }

    @Test
    @DisplayName("cancelar sin nada que cancelar responde 409")
    fun `no se cancela lo que no se pidio`() {
        val home = http.registerHousehold()

        val nothing = http.deleteJson("/api/v1/households/current/closure", home.accessToken)
        nothing.statusCode.shouldBe(HttpStatus.CONFLICT)
        nothing.body!!.shouldContain("HOUSEHOLD_CLOSURE_NOT_REQUESTED")
    }

    @Test
    @DisplayName("un miembro no puede cancelar la baja")
    fun `solo quien administra cancela`() {
        val home = http.registerHousehold()
        val member = http.inviteMember(home)
        http.postJson("/api/v1/households/current/closure", "", home.accessToken)

        http.deleteJson("/api/v1/households/current/closure", member)
            .statusCode.shouldBe(HttpStatus.FORBIDDEN)
    }

    // -----------------------------------------------------------------------
    // Cerrar la cuenta
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("cerrar la cuenta da de baja la identidad, su pertenencia y borra su avatar")
    fun `la cuenta se cierra`() {
        val home = http.registerHousehold()
        val member = http.inviteMember(home)
        val memberIdentity = identityOf(member)

        http.uploadFile(
            member,
            imageBytes("png"),
            "cara.png",
            "image/png",
            path = "/api/v1/users/me/avatar",
            method = HttpMethod.PUT,
        ).statusCode.shouldBe(HttpStatus.NO_CONTENT)
        val avatar = storageRoot.resolve("avatar/${memberIdentity.toString().take(2)}/$memberIdentity")
        Files.exists(avatar).shouldBeTrue()

        http.deleteJson("/api/v1/users/me", member).statusCode.shouldBe(HttpStatus.NO_CONTENT)

        postgres.ownerConnection().use { owner ->
            // La identidad queda de baja, no borrada: le queda una pertenencia
            // donde constar, y el historial de este hogar la referencia.
            owner.count(
                "SELECT count(*) FROM identities WHERE id = ? AND deactivated_at IS NOT NULL",
                memberIdentity,
            ).shouldBe(1)
            // Y la pertenencia tambien. Sin esto, esta persona seguiria saliendo
            // activa en «Personas» y contaria como administradora de un hogar en
            // el que no puede entrar nunca mas.
            owner.count(
                """
                SELECT count(*) FROM household_members
                WHERE identity_id = ? AND deactivated_at IS NOT NULL
                """.trimIndent(),
                memberIdentity,
            ).shouldBe(1)
        }

        // El avatar es lo unico del sistema que retrata a una persona.
        Files.exists(avatar).shouldBeFalse()
    }

    @Test
    @DisplayName("cerrar la cuenta impide volver a entrar, con la contrasena correcta")
    fun `la cuenta cerrada no autentica`() {
        val home = http.registerHousehold()
        val invited = "invitado-${UUID.randomUUID()}@example.test"
        val member = http.inviteMember(home, invited)

        http.deleteJson("/api/v1/users/me", member).statusCode.shouldBe(HttpStatus.NO_CONTENT)

        val login = http.postJson(
            "/api/v1/auth/login",
            """{"email":"$invited","password":"$INVITED_PASSWORD"}""",
        )
        login.statusCode.shouldBe(HttpStatus.UNAUTHORIZED)
    }

    @Test
    @DisplayName("el unico administrador activo NO puede cerrar su cuenta")
    fun `el ultimo administrador no cierra su cuenta`() {
        val home = http.registerHousehold()

        val refused = http.deleteJson("/api/v1/users/me", home.accessToken)
        refused.statusCode.shouldBe(HttpStatus.CONFLICT)
        refused.body!!.shouldContain("USER_LAST_ADMIN")

        // Y sigue pudiendo entrar: no se ha quedado a medias.
        http.getJson("/api/v1/households/current", home.accessToken).statusCode.shouldBe(HttpStatus.OK)
    }

    @Test
    @DisplayName("con otra persona administrando, el que era unico ya puede cerrarla")
    fun `la salida del ultimo administrador es nombrar a otra persona`() {
        val home = http.registerHousehold()
        val invited = "relevo-${UUID.randomUUID()}@example.test"
        val member = http.inviteMember(home, invited)
        // Del **token** y no del primer elemento del listado, que ordena por su
        // cuenta: leyendo el primer `id` de `/users` se ascendia a administrador
        // a quien ya lo era, la cuenta de administradores seguia siendo uno y el
        // cierre respondia `409` por el motivo correcto en el momento
        // equivocado.
        val memberId = claimOf(member, "memberId")

        http.deleteJson("/api/v1/users/me", home.accessToken).statusCode.shouldBe(HttpStatus.CONFLICT)

        // La salida esta en sus manos, que es lo que hace aceptable la regla.
        http.patchJson("/api/v1/users/$memberId/roles", """{"role":"HOUSEHOLD_ADMIN"}""", home.accessToken)
            .statusCode.shouldBe(HttpStatus.OK)

        http.deleteJson("/api/v1/users/me", home.accessToken).statusCode.shouldBe(HttpStatus.NO_CONTENT)

        // Y **el hogar sigue**: cerrar una cuenta no se lleva la casa por delante.
        postgres.ownerConnection().use {
            it.count(
                "SELECT count(*) FROM households WHERE id = ?",
                UUID.fromString(home.householdId),
            ).shouldBe(1)
        }
    }

    @Test
    @DisplayName("cerrar la cuenta NO da de baja el hogar aunque tuviera la baja pedida")
    fun `cerrar la cuenta no arrastra al hogar`() {
        val home = http.registerHousehold()
        val member = http.inviteMember(home)

        http.postJson("/api/v1/households/current/closure", "", home.accessToken)
        http.deleteJson("/api/v1/users/me", member).statusCode.shouldBe(HttpStatus.NO_CONTENT)

        // La direccion es de una sola via: la baja del hogar puede activar la de
        // una identidad, nunca al reves.
        http.getJson("/api/v1/households/current", home.accessToken).body!!.shouldNotContain("\"closure\":null")
    }

    @Test
    @DisplayName("cerrar la cuenta revoca los refresh tokens de esa persona")
    fun `la cuenta cerrada no renueva`() {
        val home = http.registerHousehold()
        val invited = "sesion-${UUID.randomUUID()}@example.test"
        val member = http.inviteMember(home, invited)
        val refresh = http.postJson(
            "/api/v1/auth/login",
            """{"email":"$invited","password":"$INVITED_PASSWORD"}""",
        ).body!!.extract("refreshToken")

        http.deleteJson("/api/v1/users/me", member).statusCode.shouldBe(HttpStatus.NO_CONTENT)

        // Sin esto, la sesion abierta seguiria renovandose quince minutos mas
        // cada vez durante dias.
        http.postJson("/api/v1/auth/refresh", """{"refreshToken":"$refresh"}""")
            .statusCode.shouldBe(HttpStatus.UNAUTHORIZED)
    }

    // -----------------------------------------------------------------------
    // Apoyo
    // -----------------------------------------------------------------------

    /** Suma una persona al hogar por invitacion, y devuelve su access token. */
    private fun TestRestTemplate.inviteMember(
        home: TestHousehold,
        email: String = "invitado-${UUID.randomUUID()}@example.test",
    ): String {
        postJson(
            "/api/v1/invitations",
            """{"email":"$email","role":"HOUSEHOLD_MEMBER"}""",
            home.accessToken,
        ).statusCode.shouldBe(HttpStatus.CREATED)

        val token = mailpit.awaitMessageTo(email).token()
        return postJson(
            "/api/v1/invitations/accept",
            """{"token":"$token","name":"Invitada","password":"$INVITED_PASSWORD"}""",
        ).body!!.extract("accessToken")
    }

    private fun identityOf(accessToken: String): UUID = UUID.fromString(claimOf(accessToken, "sub"))

    /** Un claim del access token. Es el unico sitio donde vive: la API no los devuelve. */
    private fun claimOf(accessToken: String, name: String): String {
        val payload = String(java.util.Base64.getUrlDecoder().decode(accessToken.split(".")[1]))
        return payload.extract(name)
    }

    private fun countNotices(householdId: UUID): Int = postgres.ownerConnection().use {
        it.count(
            "SELECT count(*) FROM household_notices WHERE household_id = ? AND kind = 'HOUSEHOLD_CLOSURE_REQUESTED'",
            householdId,
        )
    }

    private companion object {
        const val INVITED_PASSWORD = "la lluvia cae sobre el tejado"
    }
}
