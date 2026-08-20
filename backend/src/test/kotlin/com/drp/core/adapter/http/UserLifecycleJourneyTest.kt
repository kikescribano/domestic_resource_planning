package com.drp.core.adapter.http

import com.drp.platform.event.DomainEvent
import com.drp.platform.event.IdempotentEventHandler
import com.drp.test.DrpMailpit
import com.drp.test.DrpPostgres
import com.drp.test.SpringIntegrationTest
import com.drp.test.TestHousehold
import com.drp.test.count
import com.drp.test.deleteJson
import com.drp.test.extract
import com.drp.test.extractRaw
import com.drp.test.getJson
import com.drp.test.postJson
import com.drp.test.registerHousehold
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpStatus
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * La baja de un miembro y su vuelta, por HTTP y de punta a punta.
 *
 * Hasta la reactivacion, `DELETE /users/{id}` solo tenia pruebas negativas --el
 * 409 del ultimo administrador y el 404 de otro hogar-- y **ninguna del camino
 * feliz**: nadie comprobaba que la baja marcara la pertenencia, revocara las
 * sesiones ni sacara a la persona del listado. La ida y la vuelta se fijan
 * juntas aqui.
 *
 * La vuelta tiene ademas **dos puertas** y las dos se prueban: el interruptor de
 * un administrador (`POST /users/{id}/activation`) y aceptar una invitacion
 * nueva, que **revive** la pertenencia dada de baja en lugar de insertar una
 * segunda fila -- `household_members_identity_unique` convertiria ese insert en
 * un 500, y era exactamente lo que pasaba antes de este bloque.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserLifecycleJourneyTest : SpringIntegrationTest() {

    @Autowired private lateinit var http: TestRestTemplate

    @Autowired private lateinit var module: SubscribedModule

    private val postgres = DrpPostgres.instance
    private val mailpit = DrpMailpit.instance

    @BeforeEach
    fun forgetPreviousEvents() = module.received.clear()

    // -----------------------------------------------------------------------
    // La ida y la vuelta
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("la baja apaga la pertenencia y la reactivacion la vuelve a encender")
    fun `la ida y la vuelta`() {
        val home = http.registerHousehold()
        val guest = http.inviteMember(home)

        // La baja responde 204 y deja el rastro completo: la pertenencia
        // apagada, las sesiones revocadas y la puerta cerrada.
        http.deleteJson("/api/v1/users/${guest.memberId}", home.accessToken)
            .statusCode.shouldBe(HttpStatus.NO_CONTENT)

        deactivatedCount(guest.memberId).shouldBe(1)
        liveRefreshTokens(guest.identityId).shouldBe(0)
        http.postJson("/api/v1/auth/login", """{"email":"${guest.email}","password":"$GUEST_PASSWORD"}""")
            .statusCode.shouldBe(HttpStatus.UNAUTHORIZED)

        // El listado por defecto ya no la ensena; con includeDeactivated si,
        // que es lo que deja a un administrador encontrarla para traerla de
        // vuelta.
        http.getJson("/api/v1/users", home.accessToken).body!!.shouldNotContain(guest.email)
        http.getJson("/api/v1/users?includeDeactivated=true", home.accessToken).body!!.shouldContain(guest.email)

        // La vuelta: 200 con el usuario ya activo, mismo identificador y el rol
        // que tenia. La autoria del cambio es de quien reactiva.
        val reactivated = http.postJson("/api/v1/users/${guest.memberId}/activation", "", home.accessToken)
        reactivated.statusCode.shouldBe(HttpStatus.OK)
        reactivated.body!!.extract("id").shouldBe(guest.memberId)
        reactivated.body!!.extract("role").shouldBe("HOUSEHOLD_MEMBER")
        reactivated.body!!.extractRaw("deactivatedAt").shouldBe("null")
        reactivated.body!!.extract("updatedBy").shouldBe(home.memberId)

        deactivatedCount(guest.memberId).shouldBe(0)

        // Lo que la vuelta NO deshace: las sesiones revocadas no vuelven. La
        // persona entra de nuevo con sus credenciales, que siguen siendo suyas.
        liveRefreshTokens(guest.identityId).shouldBe(0)
        http.postJson("/api/v1/auth/login", """{"email":"${guest.email}","password":"$GUEST_PASSWORD"}""")
            .statusCode.shouldBe(HttpStatus.OK)
    }

    @Test
    @DisplayName("reactivar a quien ya esta activo devuelve el mismo estado: idempotente como los modulos")
    fun `reactivar lo activo no falla`() {
        val home = http.registerHousehold()
        val guest = http.inviteMember(home)

        val response = http.postJson("/api/v1/users/${guest.memberId}/activation", "", home.accessToken)

        response.statusCode.shouldBe(HttpStatus.OK)
        response.body!!.extractRaw("deactivatedAt").shouldBe("null")
    }

    @Test
    @DisplayName("reactivar es solo de administradores")
    fun `un miembro no reactiva`() {
        val home = http.registerHousehold()
        val guest = http.inviteMember(home)

        http.postJson("/api/v1/users/${guest.memberId}/activation", "", guest.accessToken)
            .statusCode.shouldBe(HttpStatus.FORBIDDEN)
    }

    // -----------------------------------------------------------------------
    // Los dos 409 de la vuelta
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("quien ya vive en otro hogar no se reactiva: la pertenencia activa es una sola")
    fun `la pertenencia unica corta la vuelta`() {
        val home = http.registerHousehold()
        val guest = http.inviteMember(home)
        http.deleteJson("/api/v1/users/${guest.memberId}", home.accessToken)
            .statusCode.shouldBe(HttpStatus.NO_CONTENT)

        // La misma persona entra en el hogar de al lado, que es justo lo que la
        // baja le permite hacer.
        val neighbour = http.registerHousehold()
        http.inviteMember(neighbour, email = guest.email)

        // Reactivarla aqui violaria el indice unico parcial; la comprobacion
        // del caso de uso lo convierte en el 409 del contrato y no en un 500.
        val refused = http.postJson("/api/v1/users/${guest.memberId}/activation", "", home.accessToken)
        refused.statusCode.shouldBe(HttpStatus.CONFLICT)
        refused.body!!.extract("code").shouldBe("IDENTITY_ALREADY_MEMBER")

        deactivatedCount(guest.memberId).shouldBe(1)
    }

    @Test
    @DisplayName("una cuenta cerrada no se reactiva: seria un miembro activo que nunca puede entrar")
    fun `la cuenta cerrada corta la vuelta`() {
        val home = http.registerHousehold()
        val guest = http.inviteMember(home)

        // La persona cierra su cuenta, que da de baja identidad y pertenencia.
        http.deleteJson("/api/v1/users/me", guest.accessToken).statusCode.shouldBe(HttpStatus.NO_CONTENT)

        val refused = http.postJson("/api/v1/users/${guest.memberId}/activation", "", home.accessToken)
        refused.statusCode.shouldBe(HttpStatus.CONFLICT)
        refused.body!!.extract("code").shouldBe("IDENTITY_CLOSED")
    }

    // -----------------------------------------------------------------------
    // La otra puerta de vuelta: una invitacion nueva
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("re-invitar a quien dejo el hogar revive su pertenencia, no crea otra")
    fun `la invitacion revive la pertenencia`() {
        val home = http.registerHousehold()
        val guest = http.inviteMember(home)
        http.deleteJson("/api/v1/users/${guest.memberId}", home.accessToken)
            .statusCode.shouldBe(HttpStatus.NO_CONTENT)

        // La invitacion nueva puede proponer otro rol: el que vuelve entra con
        // el de la invitacion, como cualquier invitado.
        http.postJson(
            "/api/v1/invitations",
            """{"email":"${guest.email}","role":"HOUSEHOLD_ADMIN"}""",
            home.accessToken,
        ).statusCode.shouldBe(HttpStatus.CREATED)

        val token = mailpit.awaitMessageTo(guest.email).token()
        // La identidad ya existe: ni nombre ni contrasena.
        http.postJson("/api/v1/invitations/accept", """{"token":"$token"}""")
            .statusCode.shouldBe(HttpStatus.OK)

        // Una sola fila --revivida, no duplicada--, activa, con el rol nuevo y
        // el mismo identificador: sus prestamos y su autoria siguen apuntando a
        // la misma persona.
        membershipRows(home, guest.identityId).shouldBe(1)
        deactivatedCount(guest.memberId).shouldBe(0)
        val listed = http.getJson("/api/v1/users", home.accessToken).body!!
        listed.shouldContain("\"id\":\"${guest.memberId}\"")
        Regex("\\{[^{}]*\"id\":\"${guest.memberId}\"[^{}]*\\}").find(listed)!!
            .value.extract("role").shouldBe("HOUSEHOLD_ADMIN")
    }

    @Test
    @DisplayName("una invitacion no reabre una cuenta cerrada")
    fun `la invitacion respeta el cierre de cuenta`() {
        val home = http.registerHousehold()
        val guest = http.inviteMember(home)
        http.deleteJson("/api/v1/users/me", guest.accessToken).statusCode.shouldBe(HttpStatus.NO_CONTENT)

        http.postJson(
            "/api/v1/invitations",
            """{"email":"${guest.email}","role":"HOUSEHOLD_MEMBER"}""",
            home.accessToken,
        ).statusCode.shouldBe(HttpStatus.CREATED)

        val token = mailpit.awaitMessageTo(guest.email).token()
        val refused = http.postJson("/api/v1/invitations/accept", """{"token":"$token"}""")

        refused.statusCode.shouldBe(HttpStatus.CONFLICT)
        refused.body!!.extract("code").shouldBe("IDENTITY_CLOSED")
    }

    // -----------------------------------------------------------------------
    // El evento
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("la baja publica UserDeactivated y la vuelta no publica nada")
    fun `la baja anuncia y la vuelta calla`() {
        val home = http.registerHousehold()
        val guest = http.inviteMember(home)
        module.received.clear()

        http.deleteJson("/api/v1/users/${guest.memberId}", home.accessToken)
            .statusCode.shouldBe(HttpStatus.NO_CONTENT)

        val event = module.received.single()
        event.type.shouldBe("UserDeactivated")
        event.aggregateId.shouldBe(guest.memberId)

        // La vuelta no publica: el criterio del catalogo es que un evento entra
        // el dia que un modulo lo necesita, no la simetria con su contrario.
        module.received.clear()
        http.postJson("/api/v1/users/${guest.memberId}/activation", "", home.accessToken)
            .statusCode.shouldBe(HttpStatus.OK)

        module.received.map { it.type }.shouldBe(emptyList<String>())
    }

    @Test
    @DisplayName("cerrar la cuenta tambien anuncia que la persona dejo el hogar")
    fun `el cierre de cuenta tambien anuncia`() {
        val home = http.registerHousehold()
        val guest = http.inviteMember(home)
        module.received.clear()

        http.deleteJson("/api/v1/users/me", guest.accessToken).statusCode.shouldBe(HttpStatus.NO_CONTENT)

        // Quien escuche va a querer saber que la persona ya no esta, no por que
        // puerta salio: las dos bajas de pertenencia publican el mismo evento.
        val event = module.received.single()
        event.type.shouldBe("UserDeactivated")
        event.aggregateId.shouldBe(guest.memberId)
    }

    // -----------------------------------------------------------------------
    // Utillaje
    // -----------------------------------------------------------------------

    @TestConfiguration
    class Subscriber {
        @Bean fun userLifecycleSubscribedModule() = SubscribedModule()
    }

    /** Hace de modulo suscrito. Sin nadie escuchando, no habria nada que comprobar. */
    class SubscribedModule : IdempotentEventHandler("UserLifecycleSubscriber") {
        val received = CopyOnWriteArrayList<DomainEvent>()
        override fun handle(event: DomainEvent) {
            received += event
        }
    }

    /** Una persona invitada y dentro, con lo que hace falta para volver a entrar. */
    private data class Guest(
        val accessToken: String,
        val email: String,
        val memberId: String,
        val identityId: String,
    )

    private fun TestRestTemplate.inviteMember(
        home: TestHousehold,
        email: String = "invitado-${UUID.randomUUID()}@example.test",
    ): Guest {
        postJson(
            "/api/v1/invitations",
            """{"email":"$email","role":"HOUSEHOLD_MEMBER"}""",
            home.accessToken,
        ).statusCode.shouldBe(HttpStatus.CREATED)

        val token = mailpit.awaitMessageTo(email).token()
        val accessToken = postJson(
            "/api/v1/invitations/accept",
            """{"token":"$token","name":"Invitada","password":"$GUEST_PASSWORD"}""",
        ).body!!.extract("accessToken")

        // El memberId sale del token, que es el unico sitio donde esta ligado a
        // la sesion: leer el primero del listado ya costo un fallo intermitente.
        return Guest(accessToken, email, claimOf(accessToken, "memberId"), claimOf(accessToken, "sub"))
    }

    /** Un claim del access token. Es el unico sitio donde vive: la API no los devuelve. */
    private fun claimOf(accessToken: String, name: String): String {
        val payload = String(java.util.Base64.getUrlDecoder().decode(accessToken.split(".")[1]))
        return payload.extract(name)
    }

    private fun deactivatedCount(memberId: String): Int = postgres.ownerConnection().use {
        it.count(
            "SELECT count(*) FROM household_members WHERE id = ? AND deactivated_at IS NOT NULL",
            UUID.fromString(memberId),
        )
    }

    private fun membershipRows(home: TestHousehold, identityId: String): Int =
        postgres.ownerConnection().use {
            it.count(
                "SELECT count(*) FROM household_members WHERE household_id = ? AND identity_id = ?",
                UUID.fromString(home.householdId),
                UUID.fromString(identityId),
            )
        }

    private fun liveRefreshTokens(identityId: String): Int = postgres.ownerConnection().use {
        it.count(
            "SELECT count(*) FROM refresh_tokens WHERE identity_id = ? AND revoked_at IS NULL",
            UUID.fromString(identityId),
        )
    }

    private companion object {
        const val GUEST_PASSWORD = "la lluvia cae sobre el tejado"
    }
}
