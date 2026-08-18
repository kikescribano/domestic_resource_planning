package com.drp.platform.notice

import com.drp.module.testbed.TestbedModule
import com.drp.platform.schedule.DailySweep
import com.drp.test.DrpMailpit
import com.drp.test.SpringIntegrationTest
import com.drp.test.extract
import com.drp.test.getJson
import com.drp.test.postJson
import com.drp.test.registerHousehold
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import java.util.UUID

/**
 * La bandeja de avisos, de la API a PostgreSQL.
 *
 * Los avisos no se pueden crear por la API --los escribe el recorrido periodico y
 * nadie mas-- asi que estas pruebas los producen como se producen de verdad:
 * encendiendo el modulo de prueba y pasando el barrido.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [TestbedModule.ENABLED + "=true"],
)
class NoticeInboxTest : SpringIntegrationTest() {

    @Autowired private lateinit var sweep: DailySweep
    @Autowired private lateinit var http: TestRestTemplate

    private val mailpit = DrpMailpit.instance

    @Test
    @DisplayName("un hogar recién creado tiene la bandeja vacía")
    fun `sin avisos la bandeja responde vacia`() {
        val home = http.registerHousehold(mailpit)

        val inbox = http.getJson("/api/v1/notices", home.accessToken)

        inbox.statusCode.shouldBe(HttpStatus.OK)
        inbox.body!!.shouldContain(""""total":0""")
    }

    @Test
    @DisplayName("marcar leído deja la fecha y a quién lo leyó, y no se mueve al repetirlo")
    fun `marcar leido es idempotente`() {
        val home = withOneNotice()
        val noticeId = http.firstNoticeId(home.accessToken)

        val read = http.postJson("/api/v1/notices/$noticeId/read", "", home.accessToken)
        read.statusCode.shouldBe(HttpStatus.OK)
        read.body!!.extract("readBy").shouldBe(home.memberId)

        // Se compara contra **lo que quedo guardado** y no contra lo que devolvio
        // la primera llamada, y la diferencia no es teorica: esa respuesta lleva
        // el instante que la aplicacion tenia en memoria, con nanosegundos, y
        // PostgreSQL guarda `timestamptz` con microsegundos. Los dos valores
        // designan el mismo instante y no se escriben igual.
        val stored = http.getJson("/api/v1/notices", home.accessToken).body!!.extract("readAt")

        // La fecha significa **cuando se vio por primera vez**, asi que volver a
        // marcarlo no puede moverla: si lo hiciera, «esto lleva sin mirarse una
        // semana» dejaria de poder responderse.
        val again = http.postJson("/api/v1/notices/$noticeId/read", "", home.accessToken)
        again.body!!.extract("readAt").shouldBe(stored)
    }

    @Test
    @DisplayName("unreadOnly deja fuera lo ya leído")
    fun `el filtro de no leidos funciona`() {
        val home = withOneNotice()
        val noticeId = http.firstNoticeId(home.accessToken)

        http.getJson("/api/v1/notices?unreadOnly=true", home.accessToken).body!!.shouldContain(noticeId)

        http.postJson("/api/v1/notices/$noticeId/read", "", home.accessToken)

        http.getJson("/api/v1/notices?unreadOnly=true", home.accessToken).body!!.shouldNotContain(noticeId)
        // Y sigue estando en la bandeja completa: marcar leido no borra nada.
        http.getJson("/api/v1/notices", home.accessToken).body!!.shouldContain(noticeId)
    }

    @Test
    @DisplayName("vaciar la bandeja de una vez responde 204 y no deja nada sin leer")
    fun `marcar todo leido`() {
        val home = withOneNotice()

        val all = http.postJson("/api/v1/notices/read", "", home.accessToken)

        all.statusCode.shouldBe(HttpStatus.NO_CONTENT)
        http.getJson("/api/v1/notices?unreadOnly=true", home.accessToken).body!!.shouldContain(""""total":0""")
    }

    @Test
    @DisplayName("el aviso de otro hogar no se ve ni se puede marcar por su identificador")
    fun `un aviso no cruza de hogar`() {
        val home = withOneNotice()
        val neighbour = http.registerHousehold(mailpit)
        val noticeId = http.firstNoticeId(home.accessToken)

        http.getJson("/api/v1/notices", neighbour.accessToken).body!!.shouldNotContain(noticeId)

        // `404` y no `403`: distinguir «no existe» de «existe pero no es tuyo»
        // convertiria cualquier identificador en un oraculo con el que averiguar
        // que pasa en otros hogares.
        http.postJson("/api/v1/notices/$noticeId/read", "", neighbour.accessToken)
            .statusCode.shouldBe(HttpStatus.NOT_FOUND)
    }

    @Test
    @DisplayName("un identificador inventado responde 404")
    fun `un aviso que no existe`() {
        val home = http.registerHousehold(mailpit)

        http.postJson("/api/v1/notices/${UUID.randomUUID()}/read", "", home.accessToken)
            .statusCode.shouldBe(HttpStatus.NOT_FOUND)
    }

    @Test
    @DisplayName("quien no administra también lee y marca: un aviso es del hogar")
    fun `la bandeja no es solo de administradores`() {
        val home = withOneNotice()
        val member = http.inviteMember(home.accessToken)
        val noticeId = http.firstNoticeId(home.accessToken)

        http.getJson("/api/v1/notices", member).body!!.shouldContain(noticeId)
        http.postJson("/api/v1/notices/$noticeId/read", "", member).statusCode.shouldBe(HttpStatus.OK)
    }

    // -----------------------------------------------------------------------

    /** Un hogar con exactamente un aviso, producido como se produce de verdad. */
    private fun withOneNotice() = http.registerHousehold(mailpit).also { home ->
        http.postJson("/api/v1/modules/${TestbedModule.KEY}/activation", "", home.accessToken)
            .statusCode.shouldBe(HttpStatus.OK)
        sweep.run()
    }

    private fun TestRestTemplate.firstNoticeId(accessToken: String): String =
        getJson("/api/v1/notices", accessToken).body!!.extract("id")

    /** Un miembro de verdad, invitado y aceptado como lo haria una persona. */
    private fun TestRestTemplate.inviteMember(adminToken: String): String {
        val email = "miembro-${UUID.randomUUID()}@example.test"
        postJson("/api/v1/invitations", """{"email":"$email","role":"HOUSEHOLD_MEMBER"}""", adminToken)
        val invitation = mailpit.awaitMessageTo(email)
        return postJson(
            "/api/v1/invitations/accept",
            """{"token":"${invitation.token()}","name":"Miembro","password":"una frase larga mas"}""",
        ).body!!.extract("accessToken")
    }
}
