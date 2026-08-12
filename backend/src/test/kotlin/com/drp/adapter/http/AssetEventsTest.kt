package com.drp.adapter.http

import com.drp.application.event.IdempotentEventHandler
import com.drp.domain.event.DomainEvent
import com.drp.test.SpringIntegrationTest
import com.drp.test.TestHousehold
import com.drp.test.deleteJson
import com.drp.test.extract
import com.drp.test.getJson
import com.drp.test.patchJson
import com.drp.test.postJson
import com.drp.test.registerHousehold
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Que los eventos **salen de verdad**, con un modulo suscrito de por medio.
 *
 * Es lo que la estrategia de pruebas del README pide para este nivel: «ejecutar
 * `CreateAsset` y comprobar que persiste **y que se publica el evento**». Sin
 * nadie escuchando, un `publish` olvidado no se distingue de uno que funciona:
 * el estado queda igual de bien y la prueba de recorrido pasa igual.
 *
 * Y el caso que mas se rompe sin darse cuenta: `RegisterConsumableIntake`
 * publica `AssetCreated` la primera vez y `AssetQuantityChanged` la segunda, con
 * la misma peticion.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AssetEventsTest : SpringIntegrationTest() {

    @Autowired private lateinit var http: TestRestTemplate

    @Autowired private lateinit var module: SubscribedModule

    @BeforeEach
    fun forgetPreviousEvents() = module.received.clear()

    @Test
    @DisplayName("la primera entrada anuncia AssetCreated y la segunda AssetQuantityChanged")
    fun `la entrada publica un evento distinto segun cree o sume`() {
        val home = http.registerHousehold()
        val pantry = home.createLocation("""{"name":"Despensa","type":"ROOM"}""")
        val sugar = home.createArticle(
            """{"name":"Azúcar","categoryId":"${home.category("Alimentación")}","unit":"GRAM"}""",
        )
        module.received.clear()

        home.intake(sugar, pantry, 300)
        module.types().shouldContainExactly(listOf("AssetCreated"))

        module.received.clear()
        home.intake(sugar, pantry, 700)

        module.types().shouldContainExactly(listOf("AssetQuantityChanged"))
        val changed = module.received.single()
        changed.payload["previousQuantity"].toString().shouldBe("300")
        changed.payload["quantity"].toString().shouldBe("1000")
        changed.payload["reason"].shouldBe("INTAKE")
    }

    @Test
    @DisplayName("crear el articulo en la misma entrada anuncia ArticleCreated ANTES que el AssetCreated")
    fun `la entrada con articulo nuevo publica los dos`() {
        val home = http.registerHousehold()
        val pantry = home.createLocation("""{"name":"Despensa","type":"ROOM"}""")
        module.received.clear()

        http.postJson(
            "/api/v1/assets/intake",
            """{"article":{"name":"Garbanzos","categoryId":"${home.category("Alimentación")}","unit":"GRAM"},
                "ownerId":"${home.memberId}","quantity":500,"location":{"type":"LOCATION","id":"$pantry"}}""",
            home.accessToken,
        ).statusCode.shouldBe(HttpStatus.CREATED)

        module.types().shouldContainExactly(listOf("ArticleCreated", "AssetCreated"))
    }

    @Test
    @DisplayName("el alta de un duradero anuncia AssetCreated")
    fun `el alta publica AssetCreated`() {
        val home = http.registerHousehold()
        module.received.clear()

        http.postJson(
            "/api/v1/assets",
            """{"name":"Escalera","type":"DURABLE","categoryId":"${home.category("Herramientas")}"}""",
            home.accessToken,
        ).statusCode.shouldBe(HttpStatus.CREATED)

        module.types().shouldContainExactly(listOf("AssetCreated"))
    }

    @Test
    @DisplayName("mover entre ubicaciones anuncia solo AssetMoved; entrar o salir de un asset anuncia ademas la jerarquia")
    fun `el movimiento distingue sitio de composicion`() {
        val home = http.registerHousehold()
        val category = home.category("Mobiliario")
        val roomA = home.createLocation("""{"name":"Salón","type":"ROOM"}""")
        val roomB = home.createLocation("""{"name":"Cocina","type":"ROOM"}""")
        val cupboard = home.createAsset("""{"name":"Alacena","type":"DURABLE","categoryId":"$category"}""")
        val box = home.createAsset(
            """{"name":"Caja","type":"DURABLE","categoryId":"$category","location":{"type":"LOCATION","id":"$roomA"}}""",
        )

        // De ubicacion a ubicacion: cambia el sitio, no la composicion.
        module.received.clear()
        http.patchJson("/api/v1/assets/$box", """{"location":{"type":"LOCATION","id":"$roomB"}}""", home.accessToken)
            .statusCode.shouldBe(HttpStatus.OK)
        module.types().shouldContainExactly(listOf("AssetMoved"))

        // Al meterla dentro de un asset, cambian las dos cosas.
        module.received.clear()
        http.patchJson("/api/v1/assets/$box", """{"location":{"type":"ASSET","id":"$cupboard"}}""", home.accessToken)
            .statusCode.shouldBe(HttpStatus.OK)
        module.types().shouldContainExactly(listOf("AssetMoved", "AssetHierarchyChanged"))

        val hierarchy = module.received.last()
        hierarchy.payload["previousParentAssetId"].shouldBe(null)
        hierarchy.payload["parentAssetId"].toString().shouldBe(cupboard)
    }

    @Test
    @DisplayName("la fusion publica los dos eventos que ya existen, correlacionados por payload")
    fun `la fusion correlaciona destino y origen`() {
        val home = http.registerHousehold()
        val pantry = home.createLocation("""{"name":"Despensa","type":"ROOM"}""")
        val garage = home.createLocation("""{"name":"Trastero","type":"ROOM"}""")
        val sugar = home.createArticle(
            """{"name":"Azúcar","categoryId":"${home.category("Alimentación")}","unit":"GRAM"}""",
        )
        val inPantry = home.intake(sugar, pantry, 300)
        val inGarage = home.intake(sugar, garage, 500)
        module.received.clear()

        http.postJson("/api/v1/assets/$inGarage/merge", """{"targetAssetId":"$inPantry"}""", home.accessToken)
            .statusCode.shouldBe(HttpStatus.OK)

        module.types().shouldContainExactly(listOf("AssetQuantityChanged", "AssetDeactivated"))

        // La referencia cruzada es lo que deja a Warehouse distinguir una mudanza
        // de una perdida: sin ella, el origen a cero pareceria material perdido.
        val onTarget = module.received.first()
        onTarget.aggregateId.shouldBe(inPantry)
        onTarget.payload["reason"].shouldBe("MERGE")
        onTarget.payload["mergedFromAssetId"].toString().shouldBe(inGarage)

        val onSource = module.received.last()
        onSource.aggregateId.shouldBe(inGarage)
        onSource.payload["mergedIntoAssetId"].toString().shouldBe(inPantry)
    }

    @Test
    @DisplayName("dar de baja una existencia con resto cuenta primero la cantidad perdida y luego la baja")
    fun `la baja con resto publica los dos en orden`() {
        val home = http.registerHousehold()
        val pantry = home.createLocation("""{"name":"Despensa","type":"ROOM"}""")
        val rice = home.createArticle(
            """{"name":"Arroz","categoryId":"${home.category("Alimentación")}","unit":"GRAM"}""",
        )
        val stockItem = home.intake(rice, pantry, 400)
        module.received.clear()

        http.deleteJson("/api/v1/assets/$stockItem", home.accessToken).statusCode.shouldBe(HttpStatus.NO_CONTENT)

        // El orden importa: al reves, un modulo veria el ajuste sobre algo que
        // para el ya no existe.
        module.types().shouldContainExactly(listOf("AssetQuantityChanged", "AssetDeactivated"))
        module.received.first().payload["reason"].shouldBe("DECOMMISSION")
    }

    @Test
    @DisplayName("dar de baja un duradero anuncia solo la baja: no hay cantidad que perder")
    fun `la baja sin cantidad publica uno solo`() {
        val home = http.registerHousehold()
        val asset = home.createAsset(
            """{"name":"Silla","type":"DURABLE","categoryId":"${home.category("Mobiliario")}"}""",
        )
        module.received.clear()

        http.deleteJson("/api/v1/assets/$asset", home.accessToken).statusCode.shouldBe(HttpStatus.NO_CONTENT)

        module.types().shouldContainExactly(listOf("AssetDeactivated"))
    }

    @Test
    @DisplayName("crear una ubicacion anuncia LocationCreated, y crear una categoria no anuncia nada")
    fun `las categorias no publican evento`() {
        val home = http.registerHousehold()
        module.received.clear()

        http.postJson("/api/v1/locations", """{"name":"Buhardilla","type":"ROOM"}""", home.accessToken)
            .statusCode.shouldBe(HttpStatus.CREATED)
        module.types().shouldContainExactly(listOf("LocationCreated"))

        // Ninguna de las cuatro de categorias publica: es clasificacion interna
        // y ningun modulo previsto reacciona a ella.
        module.received.clear()
        val category = http.postJson("/api/v1/categories", """{"name":"Náutica"}""", home.accessToken)
            .body!!.extract("id")
        http.patchJson("/api/v1/categories/$category", """{"name":"Náutica y pesca"}""", home.accessToken)
        http.deleteJson("/api/v1/categories/$category", home.accessToken)

        module.types().shouldContainExactly(emptyList())
    }

    @Test
    @DisplayName("todo evento llega con el hogar en el sobre: sin el, un handler tras el commit no sabria situarlo")
    fun `el sobre lleva siempre el hogar`() {
        val a = http.registerHousehold()
        val b = http.registerHousehold()
        module.received.clear()

        a.createAsset("""{"name":"De A","type":"DURABLE","categoryId":"${a.category("Mobiliario")}"}""")
        b.createAsset("""{"name":"De B","type":"DURABLE","categoryId":"${b.category("Mobiliario")}"}""")

        val households = module.received.map { it.householdId }.distinct()
        households.size.shouldBe(2)
    }

    @TestConfiguration
    class Subscriber {
        @Bean fun subscribedModule() = SubscribedModule()
    }

    /** Hace de modulo suscrito. Sin nadie escuchando, no habria nada que comprobar. */
    class SubscribedModule : IdempotentEventHandler("SubscribedModule") {
        val received = CopyOnWriteArrayList<DomainEvent>()
        override fun handle(event: DomainEvent) {
            received += event
        }
    }

    private fun SubscribedModule.types() = received.map { it.type }

    private fun TestHousehold.createAsset(body: String): String {
        val created = http.postJson("/api/v1/assets", body, accessToken)
        created.statusCode.shouldBe(HttpStatus.CREATED)
        return created.body!!.extract("id")
    }

    private fun TestHousehold.createLocation(body: String): String =
        http.postJson("/api/v1/locations", body, accessToken).body!!.extract("id")

    private fun TestHousehold.createArticle(body: String): String =
        http.postJson("/api/v1/articles", body, accessToken).body!!.extract("id")

    private fun TestHousehold.intake(articleId: String, locationId: String, quantity: Int): String =
        http.postJson(
            "/api/v1/assets/intake",
            """{"articleId":"$articleId","ownerId":"$memberId","quantity":$quantity,
                "location":{"type":"LOCATION","id":"$locationId"}}""",
            accessToken,
        ).body!!.extract("id")

    private fun TestHousehold.category(name: String): String {
        val body = http.getJson("/api/v1/categories", accessToken).body!!
        val entry = Regex("\\{[^{}]*\"name\":\"$name\"[^{}]*\\}").find(body)
            ?: error("No aparece la categoría sembrada «$name»:\n$body")
        return entry.value.extract("id")
    }
}
