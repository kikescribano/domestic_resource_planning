package com.drp.module.suppliers

import com.drp.test.SpringIntegrationTest
import com.drp.test.deleteJson
import com.drp.test.extract
import com.drp.test.getJson
import com.drp.test.patchJson
import com.drp.test.postJson
import com.drp.test.registerHousehold
import com.drp.test.seededCategory
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus

/**
 * El recorrido del primer modulo de verdad, por HTTP y contra PostgreSQL real.
 *
 * Comprueba dos cosas que hasta ahora no tenian donde comprobarse:
 *
 *  1. **Las dos mitades del gate sobre una ruta que existe.** El modulo de prueba
 *     del Hito 0 mide el mecanismo, pero ningun modulo del catalogo tenia
 *     controlador: encendido respondia `404` porque no habia nada detras. Aqui
 *     apagado responde `403 MODULE_INACTIVE` y encendido responde `200`, que es
 *     la diferencia entera.
 *  2. **Que apagar conserva los datos**, sobre filas de un modulo desplegado y no
 *     sobre las del modulo de prueba.
 *
 * No estrena contexto de Spring: mismo `@SpringBootTest` que las demas pruebas de
 * recorrido, sin propiedades propias ni `@TestConfiguration`. Cada combinacion
 * nueva reserva un pool entero que no se suelta mientras siga en cache, y eso ya
 * tumbo tres pruebas de otros hitos en la Fase 1.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SupplierJourneyTest : SpringIntegrationTest() {

    @Autowired private lateinit var http: TestRestTemplate

    @Test
    @DisplayName("apagado responde 403 MODULE_INACTIVE en una ruta que EXISTE, y encendido responde 200")
    fun `las dos mitades del gate sobre una ruta de verdad`() {
        val household = http.registerHousehold()

        val closed = http.getJson(SUPPLIERS, household.accessToken)
        closed.statusCode.shouldBe(HttpStatus.FORBIDDEN)
        closed.body!!.shouldContain("MODULE_INACTIVE")

        // Y no solo el listado: el gate cuelga del prefijo, asi que alcanza a
        // todo lo que hay debajo aunque el controlador lo publique o no.
        http.postJson(SUPPLIERS, """{"name":"X","serviceCategory":"OTHER","phone":"600"}""", household.accessToken)
            .statusCode.shouldBe(HttpStatus.FORBIDDEN)

        activateSuppliers(household.accessToken)

        val opened = http.getJson(SUPPLIERS, household.accessToken)
        opened.statusCode.shouldBe(HttpStatus.OK)
        opened.body!!.shouldContain("\"total\":0")
        opened.body!!.shouldNotContain("MODULE_INACTIVE")
    }

    @Test
    @DisplayName("dar de alta, enlazar con una ubicacion del core, renombrar y retirar")
    fun `el recorrido entero de un contacto de servicio`() {
        val household = http.registerHousehold()
        activateSuppliers(household.accessToken)

        val created = http.postJson(
            SUPPLIERS,
            """
            {"name":"Fontanería Pérez","serviceCategory":"PLUMBING",
             "contactName":"Luis","phone":"600 100 200","notes":"Va los sábados"}
            """.trimIndent(),
            household.accessToken,
        )
        created.statusCode.shouldBe(HttpStatus.CREATED)
        val supplierId = created.body!!.extract("id")
        // La autoria sale del token, nunca del cuerpo.
        created.body!!.shouldContain("\"createdBy\":\"${household.memberId}\"")

        // Un enlace con algo del core. El nombre lo resuelve el servidor al leer,
        // asi que la respuesta trae «Cocina» y no un identificador.
        val locationId = http.postJson(
            "/api/v1/locations",
            """{"name":"Cocina","type":"ROOM"}""",
            household.accessToken,
        ).body!!.extract("id")

        val linked = http.postJson(
            "$SUPPLIERS/$supplierId/links",
            """{"locationId":"$locationId"}""",
            household.accessToken,
        )
        linked.statusCode.shouldBe(HttpStatus.CREATED)
        linked.body!!.shouldContain("\"targetType\":\"LOCATION\"")
        linked.body!!.shouldContain("\"targetName\":\"Cocina\"")

        val detail = http.getJson("$SUPPLIERS/$supplierId", household.accessToken)
        detail.body!!.shouldContain("\"targetName\":\"Cocina\"")

        // El `PATCH` toca lo que menciona y **solo** lo que menciona: cambiar el
        // telefono no puede vaciar las notas de paso.
        val renamed = http.patchJson(
            "$SUPPLIERS/$supplierId",
            """{"phone":"600 999 999"}""",
            household.accessToken,
        )
        renamed.statusCode.shouldBe(HttpStatus.OK)
        renamed.body!!.shouldContain("600 999 999")
        renamed.body!!.shouldContain("Va los sábados")

        // Retirada logica: fuera del listado por defecto, dentro si se piden.
        http.deleteJson("$SUPPLIERS/$supplierId", household.accessToken)
            .statusCode.shouldBe(HttpStatus.NO_CONTENT)
        http.getJson(SUPPLIERS, household.accessToken).body!!.shouldNotContain("Fontanería Pérez")
        http.getJson("$SUPPLIERS?includeRetired=true", household.accessToken)
            .body!!.shouldContain("Fontanería Pérez")

        // Y sus enlaces siguen ahi: la caldera la sigue habiendo instalado quien
        // la instalo.
        http.getJson("$SUPPLIERS/$supplierId", household.accessToken)
            .body!!.shouldContain("\"targetName\":\"Cocina\"")
    }

    @Test
    @DisplayName("apagar el modulo conserva sus filas, y volver a encenderlo las devuelve")
    fun `desactivar conserva los datos`() {
        val household = http.registerHousehold()
        activateSuppliers(household.accessToken)

        http.postJson(
            SUPPLIERS,
            """{"name":"Taller Ramírez","serviceCategory":"VEHICLE","phone":"600 300 400"}""",
            household.accessToken,
        ).statusCode.shouldBe(HttpStatus.CREATED)

        http.deleteJson("/api/v1/modules/SUPPLIERS/activation", household.accessToken)
            .statusCode.shouldBe(HttpStatus.OK)
        http.getJson(SUPPLIERS, household.accessToken).statusCode.shouldBe(HttpStatus.FORBIDDEN)

        activateSuppliers(household.accessToken)
        http.getJson(SUPPLIERS, household.accessToken).body!!.shouldContain("Taller Ramírez")
    }

    @Test
    @DisplayName("el nombre repetido da 409 con su codigo, no un 500 por violacion de restriccion")
    fun `rechaza el nombre duplicado`() {
        val household = http.registerHousehold()
        activateSuppliers(household.accessToken)

        val body = """{"name":"Cerrajería Sol","serviceCategory":"LOCKSMITH","phone":"600 500 600"}"""
        http.postJson(SUPPLIERS, body, household.accessToken).statusCode.shouldBe(HttpStatus.CREATED)

        // Sin acentos y en minuscula: el caso de uso tiene que normalizar igual
        // que `suppliers_name_unique_live`, o esto seria un 500 en vez de un 409.
        val clash = http.postJson(
            SUPPLIERS,
            """{"name":"cerrajeria sol","serviceCategory":"LOCKSMITH","phone":"600 500 601"}""",
            household.accessToken,
        )
        clash.statusCode.shouldBe(HttpStatus.CONFLICT)
        clash.body!!.shouldContain("SUPPLIER_DUPLICATE")
    }

    @Test
    @DisplayName("un contacto sin telefono, correo ni web no se puede dar de alta ni dejar asi")
    fun `exige al menos una forma de contacto`() {
        val household = http.registerHousehold()
        activateSuppliers(household.accessToken)

        val naked = http.postJson(
            SUPPLIERS,
            """{"name":"Alguien","serviceCategory":"OTHER","address":"Calle Mayor 1"}""",
            household.accessToken,
        )
        naked.statusCode.shouldBe(HttpStatus.CONFLICT)
        naked.body!!.shouldContain("SUPPLIER_CONTACT_REQUIRED")

        // Y la otra mitad, que es la que se olvida: **vaciar** la unica forma de
        // contacto que tenia deja el mismo estado invalido por la puerta de atras.
        val id = http.postJson(
            SUPPLIERS,
            """{"name":"Pintor Gómez","serviceCategory":"PAINTING","phone":"600 700 800"}""",
            household.accessToken,
        ).body!!.extract("id")

        val emptied = http.patchJson("$SUPPLIERS/$id", """{"phone":null}""", household.accessToken)
        emptied.statusCode.shouldBe(HttpStatus.CONFLICT)
        emptied.body!!.shouldContain("SUPPLIER_CONTACT_REQUIRED")
    }

    @Test
    @DisplayName("un enlace apunta a una cosa: ni a ninguna ni a dos, ni dos veces a la misma")
    fun `las reglas del enlace`() {
        val household = http.registerHousehold()
        activateSuppliers(household.accessToken)

        val supplierId = http.postJson(
            SUPPLIERS,
            """{"name":"Servicio Técnico Caldera","serviceCategory":"HEATING_COOLING","phone":"900 100 100"}""",
            household.accessToken,
        ).body!!.extract("id")
        val locationId = http.postJson(
            "/api/v1/locations",
            """{"name":"Sala de calderas","type":"ROOM"}""",
            household.accessToken,
        ).body!!.extract("id")

        val nothing = http.postJson("$SUPPLIERS/$supplierId/links", "{}", household.accessToken)
        nothing.statusCode.shouldBe(HttpStatus.CONFLICT)
        nothing.body!!.shouldContain("SUPPLIER_LINK_TARGET_INVALID")

        http.postJson("$SUPPLIERS/$supplierId/links", """{"locationId":"$locationId"}""", household.accessToken)
            .statusCode.shouldBe(HttpStatus.CREATED)

        val again = http.postJson(
            "$SUPPLIERS/$supplierId/links",
            """{"locationId":"$locationId"}""",
            household.accessToken,
        )
        again.statusCode.shouldBe(HttpStatus.CONFLICT)
        again.body!!.shouldContain("SUPPLIER_LINK_DUPLICATE")
    }

    /**
     * **Un modulo no puede bloquear una operacion del core.**
     *
     * Es la razon por la que las claves ajenas hacia el core van con `ON DELETE
     * CASCADE` y no con el `RESTRICT` que da por omision el reflejo del resto del
     * modelo. `DeleteLocation` borra la fila de verdad y comprueba antes que no
     * cuelgue nada de ella mirando ubicaciones y assets, que es todo lo que el
     * core conoce: con una clave ajena que restrinja, un enlace de este modulo
     * convertiria ese borrado en un `500` **del core causado por un modulo**.
     */
    @Test
    @DisplayName("borrar una ubicacion enlazada funciona, y se lleva el enlace por delante")
    fun `el modulo no bloquea al core`() {
        val household = http.registerHousehold()
        activateSuppliers(household.accessToken)

        val supplierId = http.postJson(
            SUPPLIERS,
            """{"name":"Electricidad Ruiz","serviceCategory":"ELECTRICITY","phone":"600 800 900"}""",
            household.accessToken,
        ).body!!.extract("id")
        val locationId = http.postJson(
            "/api/v1/locations",
            """{"name":"Trastero","type":"ROOM"}""",
            household.accessToken,
        ).body!!.extract("id")
        http.postJson("$SUPPLIERS/$supplierId/links", """{"locationId":"$locationId"}""", household.accessToken)
            .statusCode.shouldBe(HttpStatus.CREATED)

        http.deleteJson("/api/v1/locations/$locationId", household.accessToken)
            .statusCode.shouldBe(HttpStatus.NO_CONTENT)

        val detail = http.getJson("$SUPPLIERS/$supplierId", household.accessToken)
        detail.statusCode.shouldBe(HttpStatus.OK)
        detail.body!!.shouldContain("\"links\":[]")
    }

    /**
     * El destino se resuelve contra el core **antes** de insertar, y por eso esto
     * es un `404` y no un `500` ni un enlace que cuela: la comprobacion de una
     * clave ajena no pasa por RLS, asi que una fila de otro hogar es invisible a
     * un SELECT y se puede referenciar igual.
     */
    @Test
    @DisplayName("enlazar con una ubicacion de otro hogar responde 404, no un enlace")
    fun `no se enlaza con el hogar de al lado`() {
        val vecino = http.registerHousehold()
        activateSuppliers(vecino.accessToken)
        val ajena = http.postJson(
            "/api/v1/locations",
            """{"name":"Garaje del vecino","type":"ROOM"}""",
            vecino.accessToken,
        ).body!!.extract("id")

        val household = http.registerHousehold()
        activateSuppliers(household.accessToken)
        val supplierId = http.postJson(
            SUPPLIERS,
            """{"name":"Jardines Luna","serviceCategory":"GARDENING","phone":"600 111 222"}""",
            household.accessToken,
        ).body!!.extract("id")

        http.postJson("$SUPPLIERS/$supplierId/links", """{"locationId":"$ajena"}""", household.accessToken)
            .statusCode.shouldBe(HttpStatus.NOT_FOUND)
    }

    @Test
    @DisplayName("un contacto retirado no admite enlaces nuevos")
    fun `el retirado no se enlaza`() {
        val household = http.registerHousehold()
        activateSuppliers(household.accessToken)

        val supplierId = http.postJson(
            SUPPLIERS,
            """{"name":"Limpiezas Aurora","serviceCategory":"CLEANING","phone":"600 222 333"}""",
            household.accessToken,
        ).body!!.extract("id")
        val locationId = http.postJson(
            "/api/v1/locations",
            """{"name":"Portal","type":"OTHER"}""",
            household.accessToken,
        ).body!!.extract("id")

        http.deleteJson("$SUPPLIERS/$supplierId", household.accessToken)

        val rejected = http.postJson(
            "$SUPPLIERS/$supplierId/links",
            """{"locationId":"$locationId"}""",
            household.accessToken,
        )
        rejected.statusCode.shouldBe(HttpStatus.CONFLICT)
        rejected.body!!.shouldContain("SUPPLIER_RETIRED")
    }

    /**
     * El enlace va anidado bajo su contacto y eso tiene que significar algo. Sin
     * la comprobacion del caso de uso, `DELETE /suppliers/{otro}/links/{id}`
     * borraria igual y la ruta seria decorativa.
     */
    @Test
    @DisplayName("un enlace no se borra desde la ruta de otro contacto")
    fun `el enlace pertenece a su contacto`() {
        val household = http.registerHousehold()
        activateSuppliers(household.accessToken)

        val mine = http.postJson(
            SUPPLIERS,
            """{"name":"Albañilería Vega","serviceCategory":"MASONRY","phone":"600 444 555"}""",
            household.accessToken,
        ).body!!.extract("id")
        val other = http.postJson(
            SUPPLIERS,
            """{"name":"Carpintería Sanz","serviceCategory":"CARPENTRY","phone":"600 666 777"}""",
            household.accessToken,
        ).body!!.extract("id")
        val locationId = http.postJson(
            "/api/v1/locations",
            """{"name":"Buhardilla","type":"ROOM"}""",
            household.accessToken,
        ).body!!.extract("id")

        val linkId = http.postJson(
            "$SUPPLIERS/$mine/links",
            """{"locationId":"$locationId"}""",
            household.accessToken,
        ).body!!.extract("id")

        http.deleteJson("$SUPPLIERS/$other/links/$linkId", household.accessToken)
            .statusCode.shouldBe(HttpStatus.NOT_FOUND)
        http.deleteJson("$SUPPLIERS/$mine/links/$linkId", household.accessToken)
            .statusCode.shouldBe(HttpStatus.NO_CONTENT)
    }

    /**
     * El nombre efectivo de un asset **puede venir de su articulo**: el core no lo
     * guarda dos veces, asi que un asset con articulo tiene `name` a nulo. Sin el
     * `coalesce` del repositorio el enlace saldria sin nombre --y con el
     * `mapNotNull` delante, directamente no saldria-- y la ficha ensenaria un
     * hueco donde tiene que decir «Caldera Vaillant».
     */
    @Test
    @DisplayName("el enlace con un asset sin nombre propio toma el de su articulo")
    fun `el nombre efectivo de un asset sale del articulo`() {
        val household = http.registerHousehold()
        activateSuppliers(household.accessToken)

        val categoryId = http.seededCategory(household.accessToken, "Mobiliario")
        val articleId = http.postJson(
            "/api/v1/articles",
            """{"name":"Caldera Vaillant","categoryId":"$categoryId","unit":"UNIT"}""",
            household.accessToken,
        ).body!!.extract("id")

        // Sin `name` propio: el nombre efectivo es el del articulo.
        val assetId = http.postJson(
            "/api/v1/assets",
            """{"type":"DURABLE","articleId":"$articleId"}""",
            household.accessToken,
        ).body!!.extract("id")

        val supplierId = http.postJson(
            SUPPLIERS,
            """{"name":"Suministros Del Río","serviceCategory":"UTILITIES","phone":"600 888 999"}""",
            household.accessToken,
        ).body!!.extract("id")

        val linked = http.postJson(
            "$SUPPLIERS/$supplierId/links",
            """{"assetId":"$assetId"}""",
            household.accessToken,
        )
        linked.statusCode.shouldBe(HttpStatus.CREATED)
        linked.body!!.shouldContain("\"targetType\":\"ASSET\"")
        linked.body!!.shouldContain("\"targetName\":\"Caldera Vaillant\"")
    }

    private fun activateSuppliers(accessToken: String) {
        http.postJson("/api/v1/modules/SUPPLIERS/activation", "", accessToken)
            .statusCode.shouldBe(HttpStatus.OK)
    }

    private companion object {
        const val SUPPLIERS = "/api/v1/suppliers"
    }
}
