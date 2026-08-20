package com.drp.core.adapter.http

import com.drp.test.SpringIntegrationTest
import com.drp.test.TestHousehold
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

/**
 * Las ocho operaciones de assets, con sus dos naturalezas.
 *
 * Es la pieza mas acoplada del hito: la entrada, la fusion y el ajuste dependen
 * del **mismo** indice unico parcial de existencias, asi que probarlas por
 * separado no diria si encajan. De ahi que el recorrido principal las encadene.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AssetJourneyTest : SpringIntegrationTest() {

    @Autowired private lateinit var http: TestRestTemplate

    // ----------------------------------------------------------------------
    // DURABLE
    // ----------------------------------------------------------------------

    @Test
    @DisplayName("alta de un duradero, composicion, movimiento y baja")
    fun `el recorrido de un asset durable`() {
        val home = http.registerHousehold()
        val room = home.createLocation("""{"name":"Trastero","type":"ROOM"}""")

        val shelf = home.createAsset(
            """{"name":"Estantería de trastero","type":"DURABLE","categoryId":"${home.category("Mobiliario")}",
                "location":{"type":"LOCATION","id":"$room"}}""",
        )

        // Un DURABLE si puede alojar a otros: eso es la composicion.
        val box = home.createAsset(
            """{"name":"Caja de herramientas","type":"DURABLE","categoryId":"${home.category("Herramientas")}",
                "location":{"type":"ASSET","id":"$shelf"}}""",
        )

        val children = http.getJson("/api/v1/assets/$shelf/children", home.accessToken)
        children.statusCode.shouldBe(HttpStatus.OK)
        children.body!!.shouldContain("Caja de herramientas")

        // No se da de baja algo que tiene cosas dentro.
        val blocked = http.deleteJson("/api/v1/assets/$shelf", home.accessToken)
        blocked.statusCode.shouldBe(HttpStatus.CONFLICT)
        blocked.body!!.shouldContain("ASSET_HAS_CHILDREN")

        // Sacar la caja de la estanteria y ponerla en el suelo del trastero.
        val moved = http.patchJson(
            "/api/v1/assets/$box",
            """{"location":{"type":"LOCATION","id":"$room"}}""",
            home.accessToken,
        )
        moved.statusCode.shouldBe(HttpStatus.OK)
        moved.body!!.shouldContain("\"type\":\"LOCATION\"")

        // Y ahora si.
        http.deleteJson("/api/v1/assets/$shelf", home.accessToken).statusCode.shouldBe(HttpStatus.NO_CONTENT)

        // La baja es logica: fuera del listado por defecto, dentro pidiendo el estado.
        http.getJson("/api/v1/assets", home.accessToken).body!!.shouldNotContain("Estantería")
        http.getJson("/api/v1/assets?status=DECOMMISSIONED", home.accessToken)
            .body!!.shouldContain("Estantería")
    }

    @Test
    @DisplayName("un consumible no se da de alta por POST /assets: su alta es una entrada")
    fun `el alta de consumible no pasa por aqui`() {
        val home = http.registerHousehold()

        val rejected = http.postJson(
            "/api/v1/assets",
            """{"name":"Arroz","type":"CONSUMABLE","categoryId":"${home.category("Alimentación")}"}""",
            home.accessToken,
        )

        rejected.statusCode.shouldBe(HttpStatus.BAD_REQUEST)
    }

    @Test
    @DisplayName("sin articleId, el nombre y la categoria son obligatorios")
    fun `un asset sin articulo tiene que informar nombre y categoria`() {
        val home = http.registerHousehold()

        http.postJson("/api/v1/assets", """{"type":"DURABLE"}""", home.accessToken)
            .statusCode.shouldBe(HttpStatus.BAD_REQUEST)
    }

    @Test
    @DisplayName("con articulo, el nombre y la categoria se resuelven desde el y no se guardan por duplicado")
    fun `lo heredado del articulo se resuelve al leer`() {
        val home = http.registerHousehold()
        val article = home.createArticle(
            """{"name":"Taladro GSB 13","categoryId":"${home.category("Herramientas")}","unit":"UNIT",
                "brand":"Bosch"}""",
        )

        val asset = home.createAsset("""{"type":"DURABLE","articleId":"$article"}""")

        val read = http.getJson("/api/v1/assets/$asset", home.accessToken)
        read.body!!.shouldContain("Taladro GSB 13")
        read.body!!.shouldContain("\"category\":\"Herramientas\"")

        // Y renombrar el articulo cambia lo que se lee del asset, porque no hay
        // copia: hay resolucion.
        http.patchJson("/api/v1/articles/$article", """{"name":"Taladro Bosch GSB 13"}""", home.accessToken)
        http.getJson("/api/v1/assets/$asset", home.accessToken).body!!.shouldContain("Taladro Bosch GSB 13")
    }

    // ----------------------------------------------------------------------
    // CONSUMABLE: entrada, ajuste y fusion
    // ----------------------------------------------------------------------

    @Test
    @DisplayName("dar entrada dos veces del mismo articulo en la misma ubicacion deja UNA existencia con la suma")
    fun `la entrada suma sobre la existencia`() {
        val home = http.registerHousehold()
        val pantry = home.createLocation("""{"name":"Despensa","type":"ROOM"}""")
        val sugar = home.createArticle(
            """{"name":"Azúcar","categoryId":"${home.category("Alimentación")}","unit":"GRAM"}""",
        )

        val first = http.postJson(
            "/api/v1/assets/intake",
            """{"articleId":"$sugar","ownerId":"${home.memberId}","quantity":300,
                "location":{"type":"LOCATION","id":"$pantry"}}""",
            home.accessToken,
        )
        // La primera crea: 201 y AssetCreated.
        first.statusCode.shouldBe(HttpStatus.CREATED)
        val stockItem = first.body!!.extract("id")

        val second = http.postJson(
            "/api/v1/assets/intake",
            """{"articleId":"$sugar","ownerId":"${home.memberId}","quantity":1000,
                "location":{"type":"LOCATION","id":"$pantry"}}""",
            home.accessToken,
        )
        // La segunda suma: 200 y AssetQuantityChanged.
        second.statusCode.shouldBe(HttpStatus.OK)
        second.body!!.extract("id").shouldBe(stockItem)
        second.body!!.shouldContain("\"quantity\":1300")

        // Y sigue habiendo una sola fila, no dos «Azúcar» en la despensa.
        http.getJson("/api/v1/assets?articleId=$sugar", home.accessToken).body!!.shouldContain("\"total\":1")
    }

    @Test
    @DisplayName("el mismo articulo en dos ubicaciones son dos existencias distintas")
    fun `dos ubicaciones son dos existencias`() {
        val home = http.registerHousehold()
        val pantry = home.createLocation("""{"name":"Despensa","type":"ROOM"}""")
        val garage = home.createLocation("""{"name":"Trastero","type":"ROOM"}""")
        val sugar = home.createArticle(
            """{"name":"Azúcar","categoryId":"${home.category("Alimentación")}","unit":"GRAM"}""",
        )

        home.intake(sugar, pantry, 300)
        home.intake(sugar, garage, 500)

        http.getJson("/api/v1/assets?articleId=$sugar", home.accessToken).body!!.shouldContain("\"total\":2")
    }

    @Test
    @DisplayName("la entrada crea el articulo en el mismo gesto si aun no existe")
    fun `la entrada puede crear el articulo`() {
        val home = http.registerHousehold()
        val pantry = home.createLocation("""{"name":"Despensa","type":"ROOM"}""")

        val created = http.postJson(
            "/api/v1/assets/intake",
            """{"article":{"name":"Lentejas","categoryId":"${home.category("Alimentación")}","unit":"GRAM"},
                "ownerId":"${home.memberId}","quantity":500,
                "location":{"type":"LOCATION","id":"$pantry"}}""",
            home.accessToken,
        )

        created.statusCode.shouldBe(HttpStatus.CREATED)
        created.body!!.shouldContain("Lentejas")
        http.getJson("/api/v1/articles?q=lentejas", home.accessToken).body!!.shouldContain("\"total\":1")
    }

    @Test
    @DisplayName("una entrada de cero o negativa se rechaza")
    fun `la entrada tiene que ser positiva`() {
        val home = http.registerHousehold()
        val sugar = home.createArticle(
            """{"name":"Sal","categoryId":"${home.category("Alimentación")}","unit":"GRAM"}""",
        )

        http.postJson(
            "/api/v1/assets/intake",
            """{"articleId":"$sugar","ownerId":"${home.memberId}","quantity":0}""",
            home.accessToken,
        ).statusCode.shouldBe(HttpStatus.BAD_REQUEST)
    }

    @Test
    @DisplayName("el ajuste es absoluto y sustituye, al reves que la entrada, que suma")
    fun `el ajuste sustituye la cantidad`() {
        val home = http.registerHousehold()
        val pantry = home.createLocation("""{"name":"Despensa","type":"ROOM"}""")
        val rice = home.createArticle(
            """{"name":"Arroz","categoryId":"${home.category("Alimentación")}","unit":"GRAM"}""",
        )
        val stockItem = home.intake(rice, pantry, 1000)

        val adjusted = http.patchJson("/api/v1/assets/$stockItem", """{"quantity":650}""", home.accessToken)

        adjusted.statusCode.shouldBe(HttpStatus.OK)
        adjusted.body!!.shouldContain("\"quantity\":650")
    }

    @Test
    @DisplayName("una cantidad negativa se rechaza, y sobre un DURABLE no aplica en absoluto")
    fun `los dos errores de cantidad`() {
        val home = http.registerHousehold()
        val pantry = home.createLocation("""{"name":"Despensa","type":"ROOM"}""")
        val rice = home.createArticle(
            """{"name":"Arroz","categoryId":"${home.category("Alimentación")}","unit":"GRAM"}""",
        )
        val stockItem = home.intake(rice, pantry, 1000)
        val durable = home.createAsset(
            """{"name":"Sofá","type":"DURABLE","categoryId":"${home.category("Mobiliario")}"}""",
        )

        val negative = http.patchJson("/api/v1/assets/$stockItem", """{"quantity":-5}""", home.accessToken)
        negative.statusCode.shouldBe(HttpStatus.CONFLICT)
        negative.body!!.shouldContain("ASSET_QUANTITY_NEGATIVE")

        val notApplicable = http.patchJson("/api/v1/assets/$durable", """{"quantity":3}""", home.accessToken)
        notApplicable.statusCode.shouldBe(HttpStatus.CONFLICT)
        notApplicable.body!!.shouldContain("ASSET_QUANTITY_NOT_APPLICABLE")
    }

    @Test
    @DisplayName("llegar a cero no da de baja: un consumible agotado sigue existiendo")
    fun `cero no es baja`() {
        val home = http.registerHousehold()
        val pantry = home.createLocation("""{"name":"Despensa","type":"ROOM"}""")
        val rice = home.createArticle(
            """{"name":"Arroz","categoryId":"${home.category("Alimentación")}","unit":"GRAM"}""",
        )
        val stockItem = home.intake(rice, pantry, 1000)

        http.patchJson("/api/v1/assets/$stockItem", """{"quantity":0}""", home.accessToken)

        val read = http.getJson("/api/v1/assets/$stockItem", home.accessToken)
        read.body!!.shouldContain("\"status\":\"AVAILABLE\"")
        read.body!!.shouldContain("\"quantity\":0")
    }

    @Test
    @DisplayName("mover una existencia a una ubicacion que ya tiene otra del mismo articulo manda a la fusion")
    fun `mover no hace de fusion`() {
        val home = http.registerHousehold()
        val pantry = home.createLocation("""{"name":"Despensa","type":"ROOM"}""")
        val garage = home.createLocation("""{"name":"Trastero","type":"ROOM"}""")
        val sugar = home.createArticle(
            """{"name":"Azúcar","categoryId":"${home.category("Alimentación")}","unit":"GRAM"}""",
        )
        home.intake(sugar, pantry, 300)
        val inGarage = home.intake(sugar, garage, 500)

        val rejected = http.patchJson(
            "/api/v1/assets/$inGarage",
            """{"location":{"type":"LOCATION","id":"$pantry"}}""",
            home.accessToken,
        )

        rejected.statusCode.shouldBe(HttpStatus.CONFLICT)
        rejected.body!!.shouldContain("EXISTENCE_ALREADY_IN_LOCATION")
    }

    @Test
    @DisplayName("la fusion suma en el destino, deja el origen a cero y de baja, y libera su hueco")
    fun `la fusion junta dos existencias`() {
        val home = http.registerHousehold()
        val pantry = home.createLocation("""{"name":"Despensa","type":"ROOM"}""")
        val garage = home.createLocation("""{"name":"Trastero","type":"ROOM"}""")
        val sugar = home.createArticle(
            """{"name":"Azúcar","categoryId":"${home.category("Alimentación")}","unit":"GRAM"}""",
        )
        val inPantry = home.intake(sugar, pantry, 300)
        val inGarage = home.intake(sugar, garage, 500)

        val merged = http.postJson(
            "/api/v1/assets/$inGarage/merge",
            """{"targetAssetId":"$inPantry"}""",
            home.accessToken,
        )

        merged.statusCode.shouldBe(HttpStatus.OK)
        merged.body!!.extract("id").shouldBe(inPantry)
        merged.body!!.shouldContain("\"quantity\":800")

        // El destino conserva SU ubicacion, que es lo que la fusion decide.
        merged.body!!.shouldContain("\"id\":\"$pantry\"")

        val source = http.getJson("/api/v1/assets/$inGarage", home.accessToken)
        source.body!!.shouldContain("\"status\":\"DECOMMISSIONED\"")
        source.body!!.shouldContain("\"quantity\":0")

        // Y el hueco del trastero queda libre: la exclusion de DECOMMISSIONED del
        // indice es justo lo que permite volver a dar entrada ahi.
        home.intake(sugar, garage, 250)
    }

    @Test
    @DisplayName("la fusion rechaza los cuatro casos que no son fusion")
    fun `los limites de la fusion`() {
        val home = http.registerHousehold()
        val pantry = home.createLocation("""{"name":"Despensa","type":"ROOM"}""")
        val garage = home.createLocation("""{"name":"Trastero","type":"ROOM"}""")
        val sugar = home.createArticle(
            """{"name":"Azúcar","categoryId":"${home.category("Alimentación")}","unit":"GRAM"}""",
        )
        val salt = home.createArticle(
            """{"name":"Sal","categoryId":"${home.category("Alimentación")}","unit":"GRAM"}""",
        )
        val sugarInPantry = home.intake(sugar, pantry, 300)
        val saltInGarage = home.intake(salt, garage, 100)
        val durable = home.createAsset(
            """{"name":"Sofá","type":"DURABLE","categoryId":"${home.category("Mobiliario")}"}""",
        )

        // Consigo misma.
        http.postJson(
            "/api/v1/assets/$sugarInPantry/merge",
            """{"targetAssetId":"$sugarInPantry"}""",
            home.accessToken,
        ).body!!.shouldContain("MERGE_SAME_ASSET")

        // De articulos distintos.
        http.postJson(
            "/api/v1/assets/$saltInGarage/merge",
            """{"targetAssetId":"$sugarInPantry"}""",
            home.accessToken,
        ).body!!.shouldContain("MERGE_ARTICLE_MISMATCH")

        // Con un duradero de por medio.
        http.postJson(
            "/api/v1/assets/$durable/merge",
            """{"targetAssetId":"$sugarInPantry"}""",
            home.accessToken,
        ).body!!.shouldContain("MERGE_NOT_CONSUMABLE")

        // Con una ya dada de baja.
        http.deleteJson("/api/v1/assets/$saltInGarage", home.accessToken)
        http.postJson(
            "/api/v1/assets/$saltInGarage/merge",
            """{"targetAssetId":"$sugarInPantry"}""",
            home.accessToken,
        ).body!!.shouldContain("MERGE_ASSET_DEACTIVATED")
    }

    @Test
    @DisplayName("dar de baja una existencia con cantidad la deja a cero: lo que quedaba se da por perdido")
    fun `la baja de una existencia con resto la lleva a cero`() {
        val home = http.registerHousehold()
        val pantry = home.createLocation("""{"name":"Despensa","type":"ROOM"}""")
        val rice = home.createArticle(
            """{"name":"Arroz","categoryId":"${home.category("Alimentación")}","unit":"GRAM"}""",
        )
        val stockItem = home.intake(rice, pantry, 750)

        http.deleteJson("/api/v1/assets/$stockItem", home.accessToken).statusCode.shouldBe(HttpStatus.NO_CONTENT)

        val read = http.getJson("/api/v1/assets/$stockItem", home.accessToken)
        read.body!!.shouldContain("\"status\":\"DECOMMISSIONED\"")
        read.body!!.shouldContain("\"quantity\":0")
    }

    // ----------------------------------------------------------------------
    // Las tres validaciones que la base de datos no puede garantizar
    // ----------------------------------------------------------------------

    @Test
    @DisplayName("un CONSUMABLE no puede alojar otros assets: solo un DURABLE contiene cosas")
    fun `solo un durable hace de contenedor`() {
        val home = http.registerHousehold()
        val pantry = home.createLocation("""{"name":"Despensa","type":"ROOM"}""")
        val flour = home.createArticle(
            """{"name":"Harina","categoryId":"${home.category("Alimentación")}","unit":"GRAM"}""",
        )
        val flourStock = home.intake(flour, pantry, 1000)

        val rejected = http.postJson(
            "/api/v1/assets",
            """{"name":"Cuchara","type":"DURABLE","categoryId":"${home.category("Herramientas")}",
                "location":{"type":"ASSET","id":"$flourStock"}}""",
            home.accessToken,
        )

        rejected.statusCode.shouldBe(HttpStatus.CONFLICT)
        rejected.body!!.shouldContain("ASSET_LOCATION_CONFLICT")
    }

    @Test
    @DisplayName("un asset no puede acabar dentro de si mismo, ni de su hijo, ni de su nieto")
    fun `el anti-ciclo de la jerarquia de assets`() {
        val home = http.registerHousehold()
        val category = home.category("Mobiliario")
        val a = home.createAsset("""{"name":"Armario","type":"DURABLE","categoryId":"$category"}""")
        val b = home.createAsset(
            """{"name":"Cajón","type":"DURABLE","categoryId":"$category","location":{"type":"ASSET","id":"$a"}}""",
        )
        val c = home.createAsset(
            """{"name":"Caja","type":"DURABLE","categoryId":"$category","location":{"type":"ASSET","id":"$b"}}""",
        )

        // Dentro de si mismo.
        http.patchJson("/api/v1/assets/$a", """{"location":{"type":"ASSET","id":"$a"}}""", home.accessToken)
            .body!!.shouldContain("LOCATION_CYCLE")

        // Dentro de su hijo.
        http.patchJson("/api/v1/assets/$a", """{"location":{"type":"ASSET","id":"$b"}}""", home.accessToken)
            .body!!.shouldContain("LOCATION_CYCLE")

        // Dentro de su nieto: el que se escapa si solo se mira el hijo directo.
        val attempt = http.patchJson(
            "/api/v1/assets/$a",
            """{"location":{"type":"ASSET","id":"$c"}}""",
            home.accessToken,
        )
        attempt.statusCode.shouldBe(HttpStatus.CONFLICT)
        attempt.body!!.shouldContain("LOCATION_CYCLE")

        // Y la jerarquia sigue intacta.
        http.getJson("/api/v1/assets/$a", home.accessToken).body!!.shouldContain("\"location\":null")
    }

    @Test
    @DisplayName("superar la capacidad de una ubicacion ADVIERTE y deja pasar")
    fun `el aviso de capacidad no bloquea`() {
        val home = http.registerHousehold()
        val category = home.category("Herramientas")
        val shelf = home.createLocation(
            """{"name":"Estante estrecho","type":"SHELF","capacity":{"type":"UNITS","max":2,"unit":"cosas"}}""",
        )

        home.createAsset("""{"name":"Martillo","type":"DURABLE","categoryId":"$category",
            "location":{"type":"LOCATION","id":"$shelf"}}""")
        val second = http.postJson(
            "/api/v1/assets",
            """{"name":"Destornillador","type":"DURABLE","categoryId":"$category",
                "location":{"type":"LOCATION","id":"$shelf"}}""",
            home.accessToken,
        )
        // Justo en el limite: cabe y no avisa.
        second.statusCode.shouldBe(HttpStatus.CREATED)
        second.body!!.shouldContain("\"warnings\":[]")

        val third = http.postJson(
            "/api/v1/assets",
            """{"name":"Alicates","type":"DURABLE","categoryId":"$category",
                "location":{"type":"LOCATION","id":"$shelf"}}""",
            home.accessToken,
        )

        // Se pasa: se crea IGUAL --201, no 409-- y avisa.
        third.statusCode.shouldBe(HttpStatus.CREATED)
        third.body!!.shouldContain("LOCATION_CAPACITY_EXCEEDED")

        // Y esta ahi de verdad, no es un aviso sobre algo que no se guardo.
        http.getJson("/api/v1/assets?locationId=$shelf", home.accessToken).body!!.shouldContain("\"total\":3")
    }

    @Test
    @DisplayName("con capacidad en peso, sin medida no se avisa: nadie ha dicho cuanto pesa")
    fun `sin medida en el articulo el aviso de peso calla`() {
        val home = http.registerHousehold()
        val shelf = home.createLocation(
            """{"name":"Balda","type":"SHELF","capacity":{"type":"WEIGHT","max":1000,"unit":"g"}}""",
        )

        val created = http.postJson(
            "/api/v1/assets",
            """{"name":"Yunque","type":"DURABLE","categoryId":"${home.category("Herramientas")}",
                "location":{"type":"LOCATION","id":"$shelf"}}""",
            home.accessToken,
        )

        // Un DURABLE sin articulo no tiene de donde sacar el peso, asi que no
        // entra en la suma **y tampoco suma cero**: se calla, que es lo unico
        // honesto. Antes del Hito 3 de la Fase 2 esto callaba por otro motivo
        // --no habia medida en ninguna parte-- y callaba SIEMPRE.
        created.statusCode.shouldBe(HttpStatus.CREATED)
        created.body!!.shouldContain("\"warnings\":[]")
    }

    @Test
    @DisplayName("con capacidad en peso y articulos medidos, lo que se pasa SI avisa")
    fun `el aviso de peso suma la medida del articulo por la cantidad`() {
        val home = http.registerHousehold()
        val shelf = home.createLocation(
            """{"name":"Alacena","type":"SHELF","capacity":{"type":"WEIGHT","max":1000,"unit":"g"}}""",
        )
        // Un gramo por unidad, contado en gramos: 900 g caben en 1000 y 1200 no.
        val rice = home.createArticle(
            """{"name":"Arroz","categoryId":"${home.category("Alimentación")}",
                "unit":"GRAM","unitWeightGrams":1}""",
        )

        val fits = http.postJson(
            "/api/v1/assets/intake",
            """{"articleId":"$rice","ownerId":"${home.memberId}","quantity":900,
                "location":{"type":"LOCATION","id":"$shelf"}}""",
            home.accessToken,
        )
        fits.statusCode.shouldBe(HttpStatus.CREATED)
        fits.body!!.shouldContain("\"warnings\":[]")

        // Otros 300 g sobre los 900: la suma se pasa, y ahora hay con que verlo.
        val overflows = http.postJson(
            "/api/v1/assets/intake",
            """{"articleId":"$rice","ownerId":"${home.memberId}","quantity":300,
                "location":{"type":"LOCATION","id":"$shelf"}}""",
            home.accessToken,
        )

        // Sigue siendo un aviso y no un rechazo: 200 --suma sobre la existencia
        // que ya habia-- y la entrada se guarda igual.
        overflows.statusCode.shouldBe(HttpStatus.OK)
        overflows.body!!.shouldContain("LOCATION_CAPACITY_EXCEEDED")
        overflows.body!!.shouldContain("1200")
    }

    @Test
    @DisplayName("si lo conocido cabe pero falta por medir, se calla en vez de decir que cabe")
    fun `una suma incompleta que cabe no afirma nada`() {
        val home = http.registerHousehold()
        val shelf = home.createLocation(
            """{"name":"Vitrina","type":"SHELF","capacity":{"type":"WEIGHT","max":1000,"unit":"g"}}""",
        )
        val rice = home.createArticle(
            """{"name":"Arroz medido","categoryId":"${home.category("Alimentación")}",
                "unit":"GRAM","unitWeightGrams":1}""",
        )
        home.intake(rice, shelf, 100)

        // Un duradero sin medida en el mismo sitio: lo conocido son 100 g de
        // 1000, pero **nadie sabe cuanto pesa el yunque**. Afirmar que cabe seria
        // justo lo que la pregunta de 4.1.7 temia.
        val unmeasured = http.postJson(
            "/api/v1/assets",
            """{"name":"Yunque","type":"DURABLE","categoryId":"${home.category("Herramientas")}",
                "location":{"type":"LOCATION","id":"$shelf"}}""",
            home.accessToken,
        )

        unmeasured.statusCode.shouldBe(HttpStatus.CREATED)
        unmeasured.body!!.shouldContain("\"warnings\":[]")
    }

    @Test
    @DisplayName("mover a una ubicacion llena tambien avisa, y tambien deja pasar")
    fun `el aviso tambien salta al mover`() {
        val home = http.registerHousehold()
        val category = home.category("Herramientas")
        val shelf = home.createLocation(
            """{"name":"Estante","type":"SHELF","capacity":{"type":"UNITS","max":1,"unit":"cosas"}}""",
        )
        home.createAsset("""{"name":"Sierra","type":"DURABLE","categoryId":"$category",
            "location":{"type":"LOCATION","id":"$shelf"}}""")
        val loose = home.createAsset("""{"name":"Lima","type":"DURABLE","categoryId":"$category"}""")

        val moved = http.patchJson(
            "/api/v1/assets/$loose",
            """{"location":{"type":"LOCATION","id":"$shelf"}}""",
            home.accessToken,
        )

        moved.statusCode.shouldBe(HttpStatus.OK)
        moved.body!!.shouldContain("LOCATION_CAPACITY_EXCEEDED")
    }

    // ----------------------------------------------------------------------
    // Las dos reglas de articulo que necesitan existencias de verdad
    // ----------------------------------------------------------------------

    @Test
    @DisplayName("con existencias vivas, la unidad del articulo deja de admitirse")
    fun `la unidad es inmutable con existencias`() {
        val home = http.registerHousehold()
        val pantry = home.createLocation("""{"name":"Despensa","type":"ROOM"}""")
        val sugar = home.createArticle(
            """{"name":"Azúcar","categoryId":"${home.category("Alimentación")}","unit":"GRAM"}""",
        )
        val stockItem = home.intake(sugar, pantry, 500)

        val rejected = http.patchJson("/api/v1/articles/$sugar", """{"unit":"KILOGRAM"}""", home.accessToken)
        rejected.statusCode.shouldBe(HttpStatus.CONFLICT)
        rejected.body!!.shouldContain("ARTICLE_UNIT_IMMUTABLE")

        // Dada de baja la existencia, vuelve a poder corregirse: una existencia
        // DECOMMISSIONED no cuenta.
        http.deleteJson("/api/v1/assets/$stockItem", home.accessToken)
        http.patchJson("/api/v1/articles/$sugar", """{"unit":"KILOGRAM"}""", home.accessToken)
            .statusCode.shouldBe(HttpStatus.OK)
    }

    @Test
    @DisplayName("un articulo con existencias vivas no se retira")
    fun `no se retira un articulo con existencias`() {
        val home = http.registerHousehold()
        val pantry = home.createLocation("""{"name":"Despensa","type":"ROOM"}""")
        val sugar = home.createArticle(
            """{"name":"Azúcar","categoryId":"${home.category("Alimentación")}","unit":"GRAM"}""",
        )
        val stockItem = home.intake(sugar, pantry, 500)

        val rejected = http.deleteJson("/api/v1/articles/$sugar", home.accessToken)
        rejected.statusCode.shouldBe(HttpStatus.CONFLICT)
        rejected.body!!.shouldContain("ARTICLE_HAS_EXISTENCES")

        http.deleteJson("/api/v1/assets/$stockItem", home.accessToken)
        http.deleteJson("/api/v1/articles/$sugar", home.accessToken).statusCode.shouldBe(HttpStatus.NO_CONTENT)
    }

    @Test
    @DisplayName("una ubicacion con assets dentro no se borra")
    fun `no se borra una ubicacion ocupada`() {
        val home = http.registerHousehold()
        val room = home.createLocation("""{"name":"Salón","type":"ROOM"}""")
        home.createAsset("""{"name":"Sofá","type":"DURABLE","categoryId":"${home.category("Mobiliario")}",
            "location":{"type":"LOCATION","id":"$room"}}""")

        val blocked = http.deleteJson("/api/v1/locations/$room", home.accessToken)

        blocked.statusCode.shouldBe(HttpStatus.CONFLICT)
        blocked.body!!.shouldContain("LOCATION_HAS_ASSETS")
    }

    // ----------------------------------------------------------------------
    // Aislamiento
    // ----------------------------------------------------------------------

    @Test
    @DisplayName("ninguna de las ocho operaciones de assets cruza de hogar")
    fun `los assets no cruzan de hogar`() {
        val a = http.registerHousehold()
        val b = http.registerHousehold()
        val ofA = a.createAsset(
            """{"name":"Bicicleta de A","type":"DURABLE","categoryId":"${a.category("Mobiliario")}"}""",
        )

        http.getJson("/api/v1/assets", b.accessToken).body!!.shouldNotContain("Bicicleta de A")
        http.getJson("/api/v1/assets/$ofA", b.accessToken).statusCode.shouldBe(HttpStatus.NOT_FOUND)
        http.getJson("/api/v1/assets/$ofA/children", b.accessToken).statusCode.shouldBe(HttpStatus.NOT_FOUND)
        http.patchJson("/api/v1/assets/$ofA", """{"notes":"robada"}""", b.accessToken)
            .statusCode.shouldBe(HttpStatus.NOT_FOUND)
        http.deleteJson("/api/v1/assets/$ofA", b.accessToken).statusCode.shouldBe(HttpStatus.NOT_FOUND)
        http.postJson("/api/v1/assets/$ofA/merge", """{"targetAssetId":"$ofA"}""", b.accessToken)
            .statusCode.shouldBe(HttpStatus.CONFLICT) // MERGE_SAME_ASSET antes de mirar nada

        // Meter algo propio dentro de un asset ajeno: la clave ajena lo
        // aceptaria, porque no pasa por RLS. Lo impide resolver la referencia.
        http.postJson(
            "/api/v1/assets",
            """{"name":"Intrusa","type":"DURABLE","categoryId":"${b.category("Mobiliario")}",
                "location":{"type":"ASSET","id":"$ofA"}}""",
            b.accessToken,
        ).statusCode.shouldBe(HttpStatus.NOT_FOUND)

        // Y sigue intacta en el suyo.
        http.getJson("/api/v1/assets", a.accessToken).body!!.shouldContain("Bicicleta de A")
    }

    @Test
    @DisplayName("no se pone a nombre de un usuario de otro hogar")
    fun `el propietario tiene que ser del hogar`() {
        val a = http.registerHousehold()
        val b = http.registerHousehold()

        http.postJson(
            "/api/v1/assets",
            """{"name":"Algo","type":"DURABLE","categoryId":"${b.category("Mobiliario")}","ownerId":"${a.memberId}"}""",
            b.accessToken,
        ).statusCode.shouldBe(HttpStatus.NOT_FOUND)
    }

    // ----------------------------------------------------------------------
    // Numero de serie y fecha de adquisicion
    // ----------------------------------------------------------------------

    @Test
    @DisplayName("el numero de serie y la fecha de adquisicion se corrigen despues del alta")
    fun `los datos de la unidad fisica se pueden rellenar mas tarde`() {
        val home = http.registerHousehold()

        // El caso real: se da de alta sin ellos, porque la etiqueta con el numero
        // esta pegada al aparato y nadie lo levanta para apuntarla en ese momento.
        val boiler = home.createAsset(
            """{"name":"Taladro GSB 13","type":"DURABLE","categoryId":"${home.category("Herramientas")}"}""",
        )
        http.getJson("/api/v1/assets/$boiler", home.accessToken).body!!
            .shouldContain("\"serialNumber\":null")

        val filled = http.patchJson(
            "/api/v1/assets/$boiler",
            """{"serialNumber":"  JU-88-2019-4471  ","acquiredOn":"2019-11-03"}""",
            home.accessToken,
        )
        filled.statusCode.shouldBe(HttpStatus.OK)
        // Recortado: un numero de serie se copia a mano de una etiqueta y arrastra
        // espacios con facilidad, y dos que solo difieren en eso son el mismo.
        filled.body!!.shouldContain("\"serialNumber\":\"JU-88-2019-4471\"")
        filled.body!!.shouldContain("\"acquiredOn\":\"2019-11-03\"")

        // Y se relee igual: la correccion se guardo, no se quedo en la respuesta.
        val read = http.getJson("/api/v1/assets/$boiler", home.accessToken).body!!
        read.shouldContain("JU-88-2019-4471")
        read.shouldContain("2019-11-03")

        // Vaciarlos es legitimo: el numero se apunto mal y no se tiene el bueno.
        val cleared = http.patchJson(
            "/api/v1/assets/$boiler",
            """{"serialNumber":null,"acquiredOn":null}""",
            home.accessToken,
        )
        cleared.body!!.shouldContain("\"serialNumber\":null")
        cleared.body!!.shouldContain("\"acquiredOn\":null")
    }

    @Test
    @DisplayName("una existencia no lleva numero de serie ni fecha de adquisicion")
    fun `los datos de la unidad fisica no valen sobre un consumible`() {
        val home = http.registerHousehold()
        val pantry = home.createLocation("""{"name":"Despensa","type":"ROOM"}""")
        val sugar = home.createArticle(
            """{"name":"Azúcar","categoryId":"${home.category("Alimentación")}","unit":"KILOGRAM"}""",
        )
        val stock = home.intake(sugar, pantry, 2)

        // Es la simetrica de `quantity` sobre un DURABLE: cada reposicion suma
        // sobre la misma fila, asi que no hay unidad fisica de la que hablar.
        val rejected = http.patchJson(
            "/api/v1/assets/$stock",
            """{"serialNumber":"NO-APLICA"}""",
            home.accessToken,
        )
        rejected.statusCode.shouldBe(HttpStatus.BAD_REQUEST)
        rejected.body!!.shouldContain("serialNumber")

        http.patchJson("/api/v1/assets/$stock", """{"acquiredOn":"2026-01-01"}""", home.accessToken)
            .statusCode.shouldBe(HttpStatus.BAD_REQUEST)

        // Y el estado de conservacion, que llega con el cierre de huecos y es la
        // tercera del mismo grupo: dos kilos de azucar no estan «desgastados», y
        // lo que le pasa a un lote es de Warehouse.
        val condition = http.patchJson(
            "/api/v1/assets/$stock",
            """{"condition":"WORN"}""",
            home.accessToken,
        )
        condition.statusCode.shouldBe(HttpStatus.BAD_REQUEST)
        condition.body!!.shouldContain("condition")

        // Y lo que si vale sobre una existencia sigue valiendo: la negativa es
        // del campo, no del PATCH entero.
        http.patchJson("/api/v1/assets/$stock", """{"quantity":5}""", home.accessToken)
            .statusCode.shouldBe(HttpStatus.OK)
    }

    // ----------------------------------------------------------------------
    // Estado de conservacion
    // ----------------------------------------------------------------------

    @Test
    @DisplayName("el estado de conservacion se anota, se corrige, se retira y se puede filtrar por el")
    fun `el recorrido del estado de conservacion`() {
        val home = http.registerHousehold()
        val tools = home.category("Herramientas")

        // Se puede anotar en el alta...
        val drill = home.createAsset(
            """{"name":"Taladro","type":"DURABLE","categoryId":"$tools","condition":"GOOD"}""",
        )
        http.getJson("/api/v1/assets/$drill", home.accessToken).body!!
            .shouldContain("\"condition\":\"GOOD\"")

        // ...y tambien no anotarlo, que es el caso corriente. Nulo no es un
        // hueco: significa que nadie lo ha mirado.
        val ladder = home.createAsset("""{"name":"Escalera","type":"DURABLE","categoryId":"$tools"}""")
        http.getJson("/api/v1/assets/$ladder", home.accessToken).body!!
            .shouldContain("\"condition\":null")

        // Cambia con el tiempo sin que nadie toque la cosa, asi que se corrige.
        val worse = http.patchJson("/api/v1/assets/$drill", """{"condition":"DAMAGED"}""", home.accessToken)
        worse.statusCode.shouldBe(HttpStatus.OK)
        worse.body!!.shouldContain("\"condition\":\"DAMAGED\"")

        // El filtro, que es lo que justifica que sea un enumerado y no una nota:
        // sobre `notes` esta pregunta no se puede hacer.
        val damaged = http.getJson("/api/v1/assets?condition=DAMAGED", home.accessToken)
        damaged.statusCode.shouldBe(HttpStatus.OK)
        damaged.body!!.shouldContain("Taladro")
        damaged.body!!.shouldNotContain("Escalera")

        // Sin filtro salen los dos: lo que no se anoto no desaparece del
        // inventario, solo de los filtros por estado.
        val all = http.getJson("/api/v1/assets", home.accessToken).body!!
        all.shouldContain("Taladro")
        all.shouldContain("Escalera")

        // Retirar la anotacion es distinto de anotar cualquiera de los cinco
        // valores, y por eso el campo admite nulo.
        val cleared = http.patchJson("/api/v1/assets/$drill", """{"condition":null}""", home.accessToken)
        cleared.body!!.shouldContain("\"condition\":null")
        http.getJson("/api/v1/assets?condition=DAMAGED", home.accessToken).body!!
            .shouldNotContain("Taladro")
    }

    @Test
    @DisplayName("un estado de conservacion inventado se rechaza con 400 y no con 500")
    fun `la escala es cerrada`() {
        val home = http.registerHousehold()
        val tools = home.category("Herramientas")

        // En el alta lo rechaza Jackson al leer el cuerpo...
        http.postJson(
            "/api/v1/assets",
            """{"name":"Sierra","type":"DURABLE","categoryId":"$tools","condition":"REGULINCHI"}""",
            home.accessToken,
        ).statusCode.shouldBe(HttpStatus.BAD_REQUEST)

        // ...y en el PATCH, que lee el cuerpo como arbol JSON en crudo y no pasa
        // por Bean Validation, lo rechaza el manejador de `IllegalArgumentException`.
        val asset = home.createAsset("""{"name":"Sierra","type":"DURABLE","categoryId":"$tools"}""")
        http.patchJson("/api/v1/assets/$asset", """{"condition":"REGULINCHI"}""", home.accessToken)
            .statusCode.shouldBe(HttpStatus.BAD_REQUEST)

        // Y en el filtro del listado, que es un parametro de consulta y no un
        // cuerpo: sin manejador saldria un 500.
        http.getJson("/api/v1/assets?condition=REGULINCHI", home.accessToken)
            .statusCode.shouldBe(HttpStatus.BAD_REQUEST)
    }

    // ----------------------------------------------------------------------
    // Utilidades
    // ----------------------------------------------------------------------

    private fun TestHousehold.createAsset(body: String): String {
        val created = http.postJson("/api/v1/assets", body, accessToken)
        created.statusCode.shouldBe(HttpStatus.CREATED)
        return created.body!!.extract("id")
    }

    private fun TestHousehold.createLocation(body: String): String {
        val created = http.postJson("/api/v1/locations", body, accessToken)
        created.statusCode.shouldBe(HttpStatus.CREATED)
        return created.body!!.extract("id")
    }

    private fun TestHousehold.createArticle(body: String): String {
        val created = http.postJson("/api/v1/articles", body, accessToken)
        created.statusCode.shouldBe(HttpStatus.CREATED)
        return created.body!!.extract("id")
    }

    private fun TestHousehold.intake(articleId: String, locationId: String, quantity: Int): String {
        val done = http.postJson(
            "/api/v1/assets/intake",
            """{"articleId":"$articleId","ownerId":"$memberId","quantity":$quantity,
                "location":{"type":"LOCATION","id":"$locationId"}}""",
            accessToken,
        )
        done.statusCode.value().shouldBe(if (done.statusCode == HttpStatus.CREATED) 201 else 200)
        return done.body!!.extract("id")
    }

    /** Una de las cinco categorias que siembra el alta del hogar. */
    private fun TestHousehold.category(name: String): String {
        val body = http.getJson("/api/v1/categories", accessToken).body!!
        val entry = Regex("\\{[^{}]*\"name\":\"$name\"[^{}]*\\}").find(body)
            ?: error("No aparece la categoría sembrada «$name»:\n$body")
        return entry.value.extract("id")
    }
}
