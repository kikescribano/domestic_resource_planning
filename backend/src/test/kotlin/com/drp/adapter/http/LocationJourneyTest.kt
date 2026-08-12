package com.drp.adapter.http

import com.drp.test.SpringIntegrationTest
import com.drp.test.deleteJson
import com.drp.test.extract
import com.drp.test.getJson
import com.drp.test.patchJson
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
 * Las seis operaciones de ubicaciones, y sobre todo el **anti-ciclo**.
 *
 * Esa comprobacion vive en el caso de uso porque la base de datos no puede
 * hacerla --un `CHECK` solo ve la fila que se inserta-- y por eso hay que
 * insistir en los caminos por los que se podria esquivar: el ciclo de un solo
 * nodo, el de dos, el de tres, y el que se intenta por `PATCH` en lugar de por el
 * alta.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LocationJourneyTest : SpringIntegrationTest() {

    @Autowired private lateinit var http: TestRestTemplate

    @Test
    @DisplayName("crear la jerarquia de una vivienda, navegarla por niveles y deshacerla")
    fun `el recorrido de la jerarquia de ubicaciones`() {
        val home = http.registerHousehold()

        val house = home.createLocation("""{"name":"Casa del Pinar","type":"HOUSE"}""")
        val floor = home.createLocation("""{"name":"Planta baja","type":"FLOOR","parentLocationId":"$house"}""")
        val garage = home.createLocation("""{"name":"Garaje","type":"ROOM","parentLocationId":"$floor"}""")

        // El arbol entero de una vez, que es como lo pinta el cliente.
        val all = http.getJson("/api/v1/locations", home.accessToken)
        all.statusCode.shouldBe(HttpStatus.OK)
        all.body!!.shouldContain("\"total\":3")

        // Y por niveles, que es como se carga bajo demanda.
        val children = http.getJson("/api/v1/locations/$floor/children", home.accessToken)
        children.body!!.shouldContain("Garaje")
        children.body!!.shouldNotContain("Casa del Pinar")

        // No se borra lo que tiene algo dentro.
        val blocked = http.deleteJson("/api/v1/locations/$floor", home.accessToken)
        blocked.statusCode.shouldBe(HttpStatus.CONFLICT)
        blocked.body!!.shouldContain("LOCATION_HAS_CHILDREN")

        // De abajo arriba si.
        http.deleteJson("/api/v1/locations/$garage", home.accessToken).statusCode.shouldBe(HttpStatus.NO_CONTENT)
        http.deleteJson("/api/v1/locations/$floor", home.accessToken).statusCode.shouldBe(HttpStatus.NO_CONTENT)
    }

    @Test
    @DisplayName("una ubicacion no puede colgar de si misma")
    fun `el ciclo de un solo nodo se rechaza`() {
        val home = http.registerHousehold()
        val room = home.createLocation("""{"name":"Cuarto","type":"ROOM"}""")

        val attempt = http.patchJson(
            "/api/v1/locations/$room",
            """{"parentLocationId":"$room"}""",
            home.accessToken,
        )

        attempt.statusCode.shouldBe(HttpStatus.CONFLICT)
        attempt.body!!.shouldContain("LOCATION_CYCLE")
    }

    @Test
    @DisplayName("una ubicacion no puede colgar de su propia hija")
    fun `el ciclo de dos nodos se rechaza`() {
        val home = http.registerHousehold()
        val parent = home.createLocation("""{"name":"Salón","type":"ROOM"}""")
        val child = home.createLocation("""{"name":"Vitrina","type":"FURNITURE","parentLocationId":"$parent"}""")

        val attempt = http.patchJson(
            "/api/v1/locations/$parent",
            """{"parentLocationId":"$child"}""",
            home.accessToken,
        )

        attempt.statusCode.shouldBe(HttpStatus.CONFLICT)
        attempt.body!!.shouldContain("LOCATION_CYCLE")
    }

    /**
     * El caso que se escapa si solo se comprueba el hijo directo. Es igual de
     * mortal que el de dos: con el ciclo hecho, recorrer la jerarquia no termina.
     */
    @Test
    @DisplayName("una ubicacion no puede colgar de su nieta: el anti-ciclo mira el camino entero")
    fun `el ciclo de tres nodos se rechaza`() {
        val home = http.registerHousehold()
        val a = home.createLocation("""{"name":"Nivel A","type":"ROOM"}""")
        val b = home.createLocation("""{"name":"Nivel B","type":"FURNITURE","parentLocationId":"$a"}""")
        val c = home.createLocation("""{"name":"Nivel C","type":"SHELF","parentLocationId":"$b"}""")

        val attempt = http.patchJson("/api/v1/locations/$a", """{"parentLocationId":"$c"}""", home.accessToken)

        attempt.statusCode.shouldBe(HttpStatus.CONFLICT)
        attempt.body!!.shouldContain("LOCATION_CYCLE")

        // Y la jerarquia sigue intacta: A no se ha movido.
        http.getJson("/api/v1/locations/$a", home.accessToken)
            .body!!.shouldContain("\"parentLocationId\":null")
    }

    @Test
    @DisplayName("mover una rama entera a otro sitio si vale: no todo movimiento es un ciclo")
    fun `mover una rama valida funciona`() {
        val home = http.registerHousehold()
        val first = home.createLocation("""{"name":"Casa principal","type":"HOUSE"}""")
        val second = home.createLocation("""{"name":"Casa de verano","type":"HOUSE"}""")
        val room = home.createLocation("""{"name":"Trastero","type":"ROOM","parentLocationId":"$first"}""")

        val moved = http.patchJson(
            "/api/v1/locations/$room",
            """{"parentLocationId":"$second"}""",
            home.accessToken,
        )

        moved.statusCode.shouldBe(HttpStatus.OK)
        moved.body!!.shouldContain("\"parentLocationId\":\"$second\"")
    }

    @Test
    @DisplayName("poner el padre a nulo convierte la ubicacion en raiz, que es lo que representa una vivienda")
    fun `el padre a nulo la sube a raiz`() {
        val home = http.registerHousehold()
        val house = home.createLocation("""{"name":"Casa","type":"HOUSE"}""")
        val room = home.createLocation("""{"name":"Estudio","type":"ROOM","parentLocationId":"$house"}""")

        val promoted = http.patchJson("/api/v1/locations/$room", """{"parentLocationId":null}""", home.accessToken)

        promoted.statusCode.shouldBe(HttpStatus.OK)
        promoted.body!!.shouldContain("\"parentLocationId\":null")
    }

    /**
     * La razon por la que el `PATCH` lee el cuerpo como arbol JSON: con un DTO de
     * campos anulables, este cuerpo --que no menciona el padre-- llegaria con
     * `parentLocationId` a nulo y sacaria la ubicacion de su sitio.
     */
    @Test
    @DisplayName("renombrar sin mencionar el padre no la mueve")
    fun `lo que el patch no menciona no se toca`() {
        val home = http.registerHousehold()
        val house = home.createLocation("""{"name":"Chalet","type":"HOUSE"}""")
        val room = home.createLocation("""{"name":"Despensa","type":"ROOM","parentLocationId":"$house"}""")

        val renamed = http.patchJson("/api/v1/locations/$room", """{"name":"Despensa grande"}""", home.accessToken)

        renamed.statusCode.shouldBe(HttpStatus.OK)
        renamed.body!!.shouldContain("\"parentLocationId\":\"$house\"")
        renamed.body!!.shouldContain("Despensa grande")
    }

    @Test
    @DisplayName("el nombre es unico entre hermanas, no en todo el hogar")
    fun `dos hermanas no pueden llamarse igual pero dos primas si`() {
        val home = http.registerHousehold()
        val garageA = home.createLocation("""{"name":"Garaje A","type":"ROOM"}""")
        val garageB = home.createLocation("""{"name":"Garaje B","type":"ROOM"}""")

        home.createLocation("""{"name":"Estantería 2","type":"SHELF","parentLocationId":"$garageA"}""")

        // La misma «Estantería 2» en otro garaje: perfectamente legitima.
        http.postJson(
            "/api/v1/locations",
            """{"name":"Estantería 2","type":"SHELF","parentLocationId":"$garageB"}""",
            home.accessToken,
        ).statusCode.shouldBe(HttpStatus.CREATED)

        // En el mismo garaje, no.
        val clash = http.postJson(
            "/api/v1/locations",
            """{"name":"estanteria 2","type":"SHELF","parentLocationId":"$garageA"}""",
            home.accessToken,
        )
        clash.statusCode.shouldBe(HttpStatus.CONFLICT)
        clash.body!!.shouldContain("LOCATION_DUPLICATE")
    }

    @Test
    @DisplayName("dos raices tampoco pueden llamarse igual: el NULLS NOT DISTINCT del indice tambien las cubre")
    fun `las raices tambien compiten entre si`() {
        val home = http.registerHousehold()
        home.createLocation("""{"name":"Vivienda","type":"HOUSE"}""")

        val clash = http.postJson("/api/v1/locations", """{"name":"vivienda","type":"HOUSE"}""", home.accessToken)

        clash.statusCode.shouldBe(HttpStatus.CONFLICT)
        clash.body!!.shouldContain("LOCATION_DUPLICATE")
    }

    @Test
    @DisplayName("la capacidad y las condiciones ambientales van y vuelven enteras por sus dos jsonb")
    fun `los dos jsonb se guardan y se leen`() {
        val home = http.registerHousehold()

        val created = http.postJson(
            "/api/v1/locations",
            """
            {"name":"Bodega","type":"ROOM",
             "capacity":{"type":"UNITS","max":120,"unit":"botellas"},
             "environmentalConditions":{"minTemperature":10,"maxTemperature":16,"lightExposure":"DARKNESS"}}
            """.trimIndent(),
            home.accessToken,
        )
        created.statusCode.shouldBe(HttpStatus.CREATED)

        val reread = http.getJson("/api/v1/locations/${created.body!!.extract("id")}", home.accessToken)
        reread.body!!.shouldContain("\"type\":\"UNITS\"")
        reread.body!!.shouldContain("botellas")
        reread.body!!.shouldContain("\"lightExposure\":\"DARKNESS\"")
    }

    @Test
    @DisplayName("poner la capacidad a nulo la borra; no mencionarla la conserva")
    fun `el patch distingue borrar de no tocar`() {
        val home = http.registerHousehold()
        val id = home.createLocation(
            """{"name":"Alacena","type":"FURNITURE","capacity":{"type":"UNITS","max":30,"unit":"botes"}}""",
        )

        // No la menciona: sigue ahi.
        http.patchJson("/api/v1/locations/$id", """{"notes":"la de la izquierda"}""", home.accessToken)
        http.getJson("/api/v1/locations/$id", home.accessToken).body!!.shouldContain("botes")

        // La menciona a nulo: desaparece.
        http.patchJson("/api/v1/locations/$id", """{"capacity":null}""", home.accessToken)
        http.getJson("/api/v1/locations/$id", home.accessToken).body!!.shouldContain("\"capacity\":null")
    }

    @Test
    @DisplayName("un photoFileId inventado da 404, no un 500 por clave ajena")
    fun `la foto por fichero se resuelve antes de insertar`() {
        val home = http.registerHousehold()

        // Los ficheros llegan con el Hito 3, asi que hoy ninguno existe. Lo que
        // se comprueba aqui es que la referencia se RESUELVE antes de insertar:
        // la clave ajena no pasa por RLS y sin esto seria un 500.
        val attempt = http.postJson(
            "/api/v1/locations",
            """{"name":"Con foto","type":"ROOM","photoFileId":"${UUID.randomUUID()}"}""",
            home.accessToken,
        )

        attempt.statusCode.shouldBe(HttpStatus.NOT_FOUND)
    }

    @Test
    @DisplayName("colgar de una ubicacion de otro hogar da 404, aunque la clave ajena la aceptaria")
    fun `no se cuelga de una ubicacion ajena`() {
        val a = http.registerHousehold()
        val b = http.registerHousehold()
        val ofA = a.createLocation("""{"name":"Sótano de A","type":"ROOM"}""")

        // Ni al crear...
        http.postJson(
            "/api/v1/locations",
            """{"name":"Intrusa","type":"SHELF","parentLocationId":"$ofA"}""",
            b.accessToken,
        ).statusCode.shouldBe(HttpStatus.NOT_FOUND)

        // ...ni al mover una propia debajo de ella.
        val ofB = b.createLocation("""{"name":"Propia de B","type":"ROOM"}""")
        http.patchJson("/api/v1/locations/$ofB", """{"parentLocationId":"$ofA"}""", b.accessToken)
            .statusCode.shouldBe(HttpStatus.NOT_FOUND)

        // Ni se lee, ni se edita, ni se borra.
        http.getJson("/api/v1/locations/$ofA", b.accessToken).statusCode.shouldBe(HttpStatus.NOT_FOUND)
        http.patchJson("/api/v1/locations/$ofA", """{"name":"Robada"}""", b.accessToken)
            .statusCode.shouldBe(HttpStatus.NOT_FOUND)
        http.deleteJson("/api/v1/locations/$ofA", b.accessToken).statusCode.shouldBe(HttpStatus.NOT_FOUND)
        http.getJson("/api/v1/locations/$ofA/children", b.accessToken).statusCode.shouldBe(HttpStatus.NOT_FOUND)
    }

    @Test
    @DisplayName("un tipo de ubicacion inventado es error de forma, no un 500")
    fun `el tipo invalido da 400`() {
        val home = http.registerHousehold()
        val id = home.createLocation("""{"name":"Rincón","type":"OTHER"}""")

        http.postJson("/api/v1/locations", """{"name":"X","type":"CUEVA"}""", home.accessToken)
            .statusCode.shouldBe(HttpStatus.BAD_REQUEST)
        http.patchJson("/api/v1/locations/$id", """{"type":"CUEVA"}""", home.accessToken)
            .statusCode.shouldBe(HttpStatus.BAD_REQUEST)
    }

    private fun com.drp.test.TestHousehold.createLocation(body: String): String {
        val created = http.postJson("/api/v1/locations", body, accessToken)
        created.statusCode.shouldBe(HttpStatus.CREATED)
        return created.body!!.extract("id")
    }
}
