package com.drp.platform.module

import com.drp.module.testbed.TestbedModule
import com.drp.test.DrpMailpit
import com.drp.test.SpringIntegrationTest
import com.drp.test.deleteJson
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
 * El ciclo completo de la activacion, de la API a PostgreSQL y con el modulo de
 * prueba encendiendose y apagandose de verdad.
 *
 * Es lo que demuestra las tres capas del gate a la vez: **la ruta** responde
 * `403 MODULE_INACTIVE`, **el handler** no hace nada, y --lo que mas facil es
 * romper sin enterarse-- **los datos siguen ahi** al volver a encender.
 *
 * El modulo que se enciende es el de mentira, y esa es toda la razon de que
 * exista: los cuatro del catalogo son declaraciones sin dominio hasta su hito,
 * asi que ninguno tiene tabla que sobreviva a una desactivacion. Ver
 * [TestbedModule].
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    // El modulo de mentira se escanea siempre y se registra solo aqui. El porque
    // esta en [TestbedModule]: encenderlo en toda la suite metria un handler mas
    // en las pruebas que miden el comportamiento transaccional del bus.
    properties = [TestbedModule.ENABLED + "=true"],
)
class ModuleActivationJourneyTest : SpringIntegrationTest() {

    @Autowired private lateinit var http: TestRestTemplate

    private val mailpit = DrpMailpit.instance

    @Test
    @DisplayName("el catalogo llega entero y todo apagado en un hogar recien creado")
    fun `un hogar nuevo no tiene ningun modulo activo`() {
        val home = http.registerHousehold(mailpit)

        val catalogue = http.getJson("/api/v1/modules", home.accessToken)

        catalogue.statusCode.shouldBe(HttpStatus.OK)
        val body = catalogue.body!!
        // Los cuatro de la Fase 2 mas el de mentira. La ausencia de fila es lo
        // que significa apagado: el alta de un hogar no siembra ninguna.
        listOf("SUPPLIERS", "WAREHOUSE", "PURCHASING", "MAINTENANCE", TestbedModule.KEY)
            .forEach { body.shouldContain(""""key":"$it"""") }
        body.shouldNotContain(""""status":"ACTIVE"""")
    }

    @Test
    @DisplayName("apagado: la ruta responde 403 MODULE_INACTIVE y el handler no escribe")
    fun `un modulo apagado no existe para el hogar`() {
        val home = http.registerHousehold(mailpit)

        val closed = http.getJson(TestbedModule.ROUTE, home.accessToken)
        closed.statusCode.shouldBe(HttpStatus.FORBIDDEN)
        closed.body!!.shouldContain("MODULE_INACTIVE")

        // El evento del core llega al bus igual --el core publica sin saber quien
        // escucha-- y el handler lo descarta. Lo que se comprueba despues es que
        // no escribio nada.
        http.createLocation(home.accessToken, "Garaje")

        http.activate(home.accessToken)
        // Ya encendido, lo unico que hay es lo que sembro la activacion: la nota
        // del evento de antes no esta, porque activar **no reproduce eventos**.
        http.getJson(TestbedModule.ROUTE, home.accessToken).body!!.shouldNotContain("evento: Garaje")
    }

    @Test
    @DisplayName("activar siembra desde el estado actual del core, no reproduciendo eventos")
    fun `la siembra lee lo que ya hay`() {
        val home = http.registerHousehold(mailpit)
        http.createLocation(home.accessToken, "Cocina")
        http.createLocation(home.accessToken, "Despensa")

        http.activate(home.accessToken)

        http.getJson(TestbedModule.ROUTE, home.accessToken).body!!
            .shouldContain("sembrado: 2 ubicaciones")
    }

    @Test
    @DisplayName("encendido: la ruta se abre y el handler escribe")
    fun `un modulo activo atiende sus rutas y sus eventos`() {
        val home = http.registerHousehold(mailpit)
        http.activate(home.accessToken)

        val open = http.getJson(TestbedModule.ROUTE, home.accessToken)
        open.statusCode.shouldBe(HttpStatus.OK)

        http.createLocation(home.accessToken, "Trastero")

        http.getJson(TestbedModule.ROUTE, home.accessToken).body!!.shouldContain("evento: Trastero")
    }

    @Test
    @DisplayName("desactivar oculta el modulo y CONSERVA sus datos; reactivar los devuelve")
    fun `apagar no borra nada`() {
        val home = http.registerHousehold(mailpit)
        http.activate(home.accessToken)
        http.createLocation(home.accessToken, "Altillo")
        http.getJson(TestbedModule.ROUTE, home.accessToken).body!!.shouldContain("evento: Altillo")

        val off = http.deleteJson("/api/v1/modules/${TestbedModule.KEY}/activation", home.accessToken)
        off.statusCode.shouldBe(HttpStatus.OK)
        off.body!!.shouldContain(""""status":"INACTIVE"""")

        // Apagado: ni ruta ni handler.
        http.getJson(TestbedModule.ROUTE, home.accessToken).statusCode.shouldBe(HttpStatus.FORBIDDEN)
        http.createLocation(home.accessToken, "Buhardilla")

        // Y al volver a encenderlo, lo de antes sigue donde estaba y lo que paso
        // mientras estuvo apagado no aparece por ningun lado.
        http.activate(home.accessToken)
        val recovered = http.getJson(TestbedModule.ROUTE, home.accessToken).body!!
        recovered.shouldContain("evento: Altillo")
        recovered.shouldNotContain("evento: Buhardilla")
    }

    @Test
    @DisplayName("reactivar no vuelve a sembrar")
    fun `activar dos veces siembra una`() {
        val home = http.registerHousehold(mailpit)

        http.activate(home.accessToken)
        http.activate(home.accessToken)

        val notes = http.getJson(TestbedModule.ROUTE, home.accessToken).body!!
        Regex("sembrado").findAll(notes).count().shouldBe(1)
    }

    @Test
    @DisplayName("la activacion de un hogar no alcanza a otro")
    fun `cada hogar decide lo suyo`() {
        val home = http.registerHousehold(mailpit)
        val neighbour = http.registerHousehold(mailpit)

        http.activate(home.accessToken)

        http.getJson(TestbedModule.ROUTE, neighbour.accessToken).statusCode.shouldBe(HttpStatus.FORBIDDEN)
        http.getJson("/api/v1/modules", neighbour.accessToken).body!!.shouldNotContain(""""status":"ACTIVE"""")
    }

    @Test
    @DisplayName("quien no administra ve el catalogo pero no puede encender nada")
    fun `activar es solo de administradores`() {
        val home = http.registerHousehold(mailpit)
        val memberToken = http.inviteMember(home.accessToken)

        http.getJson("/api/v1/modules", memberToken).statusCode.shouldBe(HttpStatus.OK)

        val rejected = http.postJson("/api/v1/modules/${TestbedModule.KEY}/activation", "", memberToken)
        rejected.statusCode.shouldBe(HttpStatus.FORBIDDEN)
        // Y no es el 403 del gate: es el de autorizacion, que son cosas distintas
        // y el cliente las distingue por el codigo.
        rejected.body!!.shouldNotContain("MODULE_INACTIVE")
    }

    @Test
    @DisplayName("una clave que no esta en el catalogo responde 404, no 403")
    fun `un modulo inventado no existe`() {
        val home = http.registerHousehold(mailpit)

        http.postJson("/api/v1/modules/NO_EXISTE/activation", "", home.accessToken)
            .statusCode.shouldBe(HttpStatus.NOT_FOUND)
    }

    @Test
    @DisplayName("sin token la ruta de un modulo responde 401 y no 403")
    fun `el gate va detras de la autorizacion`() {
        // Es el orden del filtro en la cadena, y se nota justo aqui: puesto
        // antes, esto responderia MODULE_INACTIVE --sin hogar en el contexto no
        // hay ningun modulo activo-- y el cliente creeria que tiene algo que
        // activar cuando lo que le falta es entrar.
        http.getJson(TestbedModule.ROUTE).statusCode.shouldBe(HttpStatus.UNAUTHORIZED)
    }

    private fun TestRestTemplate.activate(accessToken: String) =
        postJson("/api/v1/modules/${TestbedModule.KEY}/activation", "", accessToken)
            .also { it.statusCode.shouldBe(HttpStatus.OK) }

    private fun TestRestTemplate.createLocation(accessToken: String, name: String) =
        postJson("/api/v1/locations", """{"name":"$name","type":"ROOM"}""", accessToken)
            .also { it.statusCode.shouldBe(HttpStatus.CREATED) }

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
