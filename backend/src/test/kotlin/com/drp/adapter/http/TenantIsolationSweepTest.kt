package com.drp.adapter.http

import com.drp.test.SpringIntegrationTest
import com.drp.test.TestHousehold
import com.drp.test.deleteJson
import com.drp.test.extract
import com.drp.test.extractRaw
import com.drp.test.getJson
import com.drp.test.patchJson
import com.drp.test.postJson
import com.drp.test.registerHousehold
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import java.util.UUID

/**
 * El barrido de aislamiento de las **veintitres** operaciones del Hito 2.
 *
 * No sustituye a las pruebas de recorrido de cada recurso: comprueba una sola
 * cosa --la de la ADR-002-- sobre todas ellas y de forma sistematica.
 *
 * > Autenticado como hogar A, ninguna operacion devuelve ni modifica datos del
 * > hogar B, ni siquiera por identificador directo.
 *
 * Esta organizado por **forma de ataque** y no por recurso, porque es la forma de
 * ataque la que se olvida en una operacion nueva:
 *
 * 1. Por el identificador del recurso, en la ruta.
 * 2. Por una referencia dentro del cuerpo. Esta es la que la clave ajena no
 *    detiene limpiamente: su comprobacion no pasa por RLS, asi que la fila ajena
 *    es invisible a un `SELECT` y aun asi se puede nombrar. Sin resolverla en el
 *    caso de uso, la compuesta `(household_id, ...)` la rechaza con un error de
 *    restriccion --un `500`-- en vez del `404` que declara el contrato.
 * 3. Por los filtros de los listados, que aceptan identificadores igual que un
 *    cuerpo.
 * 4. Por **unicidad**: si el `409` de nombre duplicado cruzase de hogar, bastaria
 *    intentar crear para averiguar que hay en el de al lado sin leer una fila.
 *
 * Dos decisiones de metodo que sostienen todo lo demas:
 *
 * - **Cada comprobacion negativa lleva su control positivo.** El mismo cuerpo,
 *   con un identificador propio, tiene que funcionar. Sin el, un `404` podria
 *   venir de un cuerpo mal formado y la prueba pasaria sin comprobar nada.
 * - **Ajeno e inexistente tienen que responder lo mismo.** Es la formulacion
 *   exacta de «no hay oraculo»: si difieren, el identificador delata.
 *
 * Las desviaciones se **registran** en lugar de cortar la ejecucion en la
 * primera: un barrido que se para en la primera no dice cuantas hay.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TenantIsolationSweepTest : SpringIntegrationTest() {

    @Autowired private lateinit var http: TestRestTemplate

    private lateinit var a: TestHousehold
    private lateinit var b: TestHousehold

    /** Lo que el hogar A tiene y B intentara alcanzar. */
    private lateinit var catA: String
    private lateinit var catA2: String
    private lateinit var artA: String
    private lateinit var artA2: String
    private lateinit var locA: String
    private lateinit var locA2: String
    private lateinit var durA: String
    private lateinit var stockA: String
    private lateinit var stockA2: String

    /** Lo propio de B, con lo que se construyen los controles positivos. */
    private lateinit var catB: String
    private lateinit var catB2: String
    private lateinit var artB: String
    private lateinit var artB2: String
    private lateinit var locB: String
    private lateinit var locB2: String
    private lateinit var durB: String
    private lateinit var durBWithArticle: String
    private lateinit var stockB: String

    /** El estado de A antes de que B lo intente, para comprobar que sigue igual. */
    private val snapshotOfA = mutableMapOf<String, String>()

    private val checks = mutableListOf<String>()
    private val deviations = mutableListOf<String>()
    private val log = mutableListOf<String>()

    private val barcodeOfA = "8400000000001"

    @BeforeAll
    fun poblarLosDosHogares() {
        a = http.registerHousehold()
        b = http.registerHousehold()

        catA = a.createCategory("""{"name":"CategoriaDeA"}""")
        catA2 = a.createCategory("""{"name":"SegundaCategoriaDeA"}""")
        artA = a.createArticle("""{"name":"AzucarDeA","categoryId":"$catA","unit":"GRAM","barcode":"$barcodeOfA"}""")
        locA = a.createLocation("""{"name":"CasaDeA","type":"HOUSE"}""")
        locA2 = a.createLocation("""{"name":"CocinaDeA","type":"ROOM","parentLocationId":"$locA"}""")
        durA = a.createAsset(
            """{"name":"TaladroDeA","type":"DURABLE","categoryId":"$catA",
                "location":{"type":"LOCATION","id":"$locA"}}""",
        )
        stockA = a.intake(artA, locA, 1000)
        stockA2 = a.intake(artA, locA2, 500)

        catB = b.createCategory("""{"name":"CategoriaDeB"}""")
        catB2 = b.createCategory("""{"name":"SegundaCategoriaDeB"}""")
        artB = b.createArticle("""{"name":"AzucarDeB","categoryId":"$catB","unit":"GRAM","barcode":"8400000000002"}""")
        artB2 = b.createArticle("""{"name":"TaladroModeloDeB","categoryId":"$catB","unit":"UNIT"}""")
        locB = b.createLocation("""{"name":"CasaDeB","type":"HOUSE"}""")
        locB2 = b.createLocation("""{"name":"CocinaDeB","type":"ROOM","parentLocationId":"$locB"}""")
        // Sin articulo a proposito: es el unico asset sobre el que un
        // `PATCH {"articleId": ...}` llega de verdad a resolver la referencia. Con
        // articulo ya puesto, el caso de uso lo rechaza antes por otra regla.
        durB = b.createAsset("""{"name":"TaladroDeB","type":"DURABLE","categoryId":"$catB"}""")
        durBWithArticle = b.createAsset("""{"type":"DURABLE","articleId":"$artB2"}""")
        stockB = b.intake(artB, locB, 300)

        listOf(
            "/api/v1/categories?includeRetired=true",
            "/api/v1/articles?includeRetired=true",
            "/api/v1/articles/$artA",
            "/api/v1/locations",
            "/api/v1/locations/$locA",
            "/api/v1/locations/$locA/children",
            "/api/v1/assets",
            "/api/v1/assets/$durA",
            "/api/v1/assets/$durA/children",
            "/api/v1/assets/$stockA",
            "/api/v1/assets/$stockA2",
        ).forEach { snapshotOfA[it] = http.getJson(it, a.accessToken).body!! }
    }

    // ------------------------------------------------------------------
    // 1. Por el identificador del recurso
    // ------------------------------------------------------------------

    @Test
    @DisplayName("las catorce operaciones con identificador en la ruta responden 404 ante uno de otro hogar")
    fun `barrido por identificador del recurso`() {
        val section = "ruta"

        operationsWithPathId().forEach { (name, call) ->
            // 404 y no 403: el contrato no puede distinguir "no existe" de
            // "existe pero no es tuyo" sin convertir el identificador en oraculo.
            expectStatus("$section $name con identificador de A", HttpStatus.NOT_FOUND, call(foreignIdFor(name)))
        }

        // Las tres combinaciones de la fusion, que es la unica operacion con dos
        // identificadores: uno en la ruta y otro en el cuerpo.
        expectStatus(
            "$section POST /assets/{origen de A}/merge con destino de B",
            HttpStatus.NOT_FOUND,
            http.postJson("$ASSETS/$stockA/merge", """{"targetAssetId":"$stockB"}""", b.accessToken),
        )
        expectStatus(
            "$section POST /assets/{origen de B}/merge con destino de A",
            HttpStatus.NOT_FOUND,
            http.postJson("$ASSETS/$stockB/merge", """{"targetAssetId":"$stockA"}""", b.accessToken),
        )
        expectStatus(
            "$section POST /assets/{origen de A}/merge con destino de A",
            HttpStatus.NOT_FOUND,
            http.postJson("$ASSETS/$stockA/merge", """{"targetAssetId":"$stockA2"}""", b.accessToken),
        )

        // Un 404 que ademas hubiera modificado algo seria peor que un 200: cada
        // recurso de A se relee en su hogar y tiene que estar como estaba.
        val category = http.getJson("$CATEGORIES?includeRetired=true", a.accessToken)
        expectPresent("$section la categoria de A sigue viva", catA, category)
        expectPresent("$section la categoria de A conserva su nombre", "CategoriaDeA", category)

        val article = http.getJson("$ARTICLES/$artA", a.accessToken)
        expectStatus("$section el articulo de A sigue existiendo", HttpStatus.OK, article)
        expectPresent("$section el articulo de A sigue vigente", "\"retiredAt\":null", article)

        val location = http.getJson("$LOCATIONS/$locA", a.accessToken)
        expectStatus("$section la ubicacion de A sigue existiendo", HttpStatus.OK, location)
        expectPresent("$section la ubicacion de A conserva su nombre", "CasaDeA", location)
        expectPresent(
            "$section la hija de A sigue colgando de ella",
            locA2,
            http.getJson("$LOCATIONS/$locA/children", a.accessToken),
        )

        val asset = http.getJson("$ASSETS/$durA", a.accessToken)
        expectStatus("$section el asset de A sigue existiendo", HttpStatus.OK, asset)
        expectPresent("$section el asset de A sigue disponible", "\"status\":\"AVAILABLE\"", asset)
        expectPresent("$section el asset de A conserva sus notas vacias", "\"notes\":null", asset)
        expectQuantity("$section la existencia de A conserva su cantidad", stockA, "1000", a)
        expectQuantity("$section la segunda existencia de A conserva su cantidad", stockA2, "500", a)
        expectQuantity("$section la existencia de B tampoco se toco", stockB, "300", b)

        endOfSection(section)
    }

    @Test
    @DisplayName("un identificador ajeno responde exactamente lo mismo que uno inexistente")
    fun `no hay oraculo por identificador`() {
        val section = "oraculo"
        val invented = UUID.randomUUID().toString()

        operationsWithPathId().forEach { (name, call) ->
            val foreign = call(foreignIdFor(name))
            val unknown = call(invented)
            checks += "$section $name"
            log += "$section $name => ajeno ${foreign.statusCode.value()} / " +
                "inexistente ${unknown.statusCode.value()}"

            if (foreign.statusCode != unknown.statusCode) {
                deviations += "$section $name -> ajeno ${foreign.statusCode.value()} frente a inexistente " +
                    "${unknown.statusCode.value()}: el identificador delata. Cuerpo ajeno: ${foreign.body}"
            } else if (foreign.statusCode != HttpStatus.NOT_FOUND) {
                deviations += "$section $name -> ${foreign.statusCode.value()} en vez de 404; cuerpo: ${foreign.body}"
            }
        }

        endOfSection(section)
    }

    // ------------------------------------------------------------------
    // 2. Por una referencia dentro del cuerpo
    // ------------------------------------------------------------------

    @Test
    @DisplayName("ninguna referencia ajena en el cuerpo se acepta, y el mismo cuerpo con una propia si")
    fun `barrido por referencia en el cuerpo`() {
        // --- POST /articles
        bothWays("POST /articles categoryId", own = catB, foreign = catA, ok = CREATED) { id ->
            http.postJson(ARTICLES, """{"name":"Sonda${short()}","categoryId":"$id","unit":"UNIT"}""", b.accessToken)
        }
        bothWays("POST /articles photoFileId", own = null, foreign = anyFile(), ok = CREATED) { id ->
            http.postJson(
                ARTICLES,
                """{"name":"Sonda${short()}","categoryId":"$catB","unit":"UNIT","photoFileId":"$id"}""",
                b.accessToken,
            )
        }

        // --- PATCH /articles/{id}, cada caso sobre un articulo recien creado para
        // que el anterior no condicione al siguiente.
        bothWays("PATCH /articles categoryId", own = catB2, foreign = catA, ok = OK) { id ->
            http.patchJson("$ARTICLES/${freshArticleB()}", """{"categoryId":"$id"}""", b.accessToken)
        }
        bothWays("PATCH /articles photoFileId", own = null, foreign = anyFile(), ok = OK) { id ->
            http.patchJson("$ARTICLES/${freshArticleB()}", """{"photoFileId":"$id"}""", b.accessToken)
        }

        // --- POST /locations
        bothWays("POST /locations parentLocationId", own = locB, foreign = locA, ok = CREATED) { id ->
            http.postJson(
                LOCATIONS,
                """{"name":"Sonda${short()}","type":"ROOM","parentLocationId":"$id"}""",
                b.accessToken,
            )
        }
        bothWays("POST /locations photoFileId", own = null, foreign = anyFile(), ok = CREATED) { id ->
            http.postJson(
                LOCATIONS,
                """{"name":"Sonda${short()}","type":"ROOM","photoFileId":"$id"}""",
                b.accessToken,
            )
        }

        // --- PATCH /locations/{id}
        bothWays("PATCH /locations parentLocationId", own = locB2, foreign = locA, ok = OK) { id ->
            http.patchJson("$LOCATIONS/${freshLocationB()}", """{"parentLocationId":"$id"}""", b.accessToken)
        }
        bothWays("PATCH /locations photoFileId", own = null, foreign = anyFile(), ok = OK) { id ->
            http.patchJson("$LOCATIONS/${freshLocationB()}", """{"photoFileId":"$id"}""", b.accessToken)
        }

        // --- POST /assets
        bothWays("POST /assets categoryId", own = catB, foreign = catA, ok = CREATED) { id ->
            http.postJson(ASSETS, """{"name":"Sonda${short()}","type":"DURABLE","categoryId":"$id"}""", b.accessToken)
        }
        bothWays("POST /assets articleId", own = artB2, foreign = artA, ok = CREATED) { id ->
            http.postJson(ASSETS, """{"type":"DURABLE","articleId":"$id"}""", b.accessToken)
        }
        bothWays("POST /assets ownerId", own = b.memberId, foreign = a.memberId, ok = CREATED) { id ->
            http.postJson(
                ASSETS,
                """{"name":"Sonda${short()}","type":"DURABLE","categoryId":"$catB","ownerId":"$id"}""",
                b.accessToken,
            )
        }
        bothWays("POST /assets location LOCATION", own = locB, foreign = locA, ok = CREATED) { id ->
            http.postJson(
                ASSETS,
                """{"name":"Sonda${short()}","type":"DURABLE","categoryId":"$catB",
                    "location":{"type":"LOCATION","id":"$id"}}""",
                b.accessToken,
            )
        }
        bothWays("POST /assets location ASSET", own = durB, foreign = durA, ok = CREATED) { id ->
            http.postJson(
                ASSETS,
                """{"name":"Sonda${short()}","type":"DURABLE","categoryId":"$catB",
                    "location":{"type":"ASSET","id":"$id"}}""",
                b.accessToken,
            )
        }
        bothWays("POST /assets photoFileId", own = null, foreign = anyFile(), ok = CREATED) { id ->
            http.postJson(
                ASSETS,
                """{"name":"Sonda${short()}","type":"DURABLE","categoryId":"$catB","photoFileId":"$id"}""",
                b.accessToken,
            )
        }

        // --- POST /assets/intake. El `ok` admite 200 y 201 porque la entrada suma
        // sobre la existencia que ya haya en esa ubicacion en vez de crear otra.
        bothWays("POST /assets/intake articleId", own = artB, foreign = artA, ok = INTAKE_OK) { id ->
            http.postJson(INTAKE, """{"articleId":"$id","ownerId":"${b.memberId}","quantity":5}""", b.accessToken)
        }
        bothWays("POST /assets/intake ownerId", own = b.memberId, foreign = a.memberId, ok = INTAKE_OK) { id ->
            http.postJson(INTAKE, """{"articleId":"$artB","ownerId":"$id","quantity":5}""", b.accessToken)
        }
        bothWays("POST /assets/intake location LOCATION", own = locB2, foreign = locA, ok = INTAKE_OK) { id ->
            http.postJson(
                INTAKE,
                """{"articleId":"$artB","ownerId":"${b.memberId}","quantity":5,
                    "location":{"type":"LOCATION","id":"$id"}}""",
                b.accessToken,
            )
        }
        bothWays("POST /assets/intake location ASSET", own = durB, foreign = durA, ok = INTAKE_OK) { id ->
            http.postJson(
                INTAKE,
                """{"articleId":"$artB","ownerId":"${b.memberId}","quantity":5,
                    "location":{"type":"ASSET","id":"$id"}}""",
                b.accessToken,
            )
        }
        // El articulo **anidado**, que se crea en el mismo gesto: su categoria
        // tambien puede ser de otro hogar.
        bothWays("POST /assets/intake article.categoryId", own = catB, foreign = catA, ok = INTAKE_OK) { id ->
            http.postJson(
                INTAKE,
                """{"article":{"name":"Sonda${short()}","categoryId":"$id","unit":"UNIT"},
                    "ownerId":"${b.memberId}","quantity":5}""",
                b.accessToken,
            )
        }
        bothWays("POST /assets/intake article.photoFileId", own = null, foreign = anyFile(), ok = INTAKE_OK) { id ->
            http.postJson(
                INTAKE,
                """{"article":{"name":"Sonda${short()}","categoryId":"$catB","unit":"UNIT","photoFileId":"$id"},
                    "ownerId":"${b.memberId}","quantity":5}""",
                b.accessToken,
            )
        }

        // --- PATCH /assets/{id}, cada caso sobre un asset propio recien creado.
        bothWays("PATCH /assets categoryId", own = catB2, foreign = catA, ok = OK) { id ->
            http.patchJson("$ASSETS/${freshDurableB()}", """{"categoryId":"$id"}""", b.accessToken)
        }
        bothWays("PATCH /assets articleId", own = artB2, foreign = artA, ok = OK) { id ->
            http.patchJson("$ASSETS/${freshDurableB()}", """{"articleId":"$id"}""", b.accessToken)
        }
        bothWays("PATCH /assets ownerId", own = b.memberId, foreign = a.memberId, ok = OK) { id ->
            http.patchJson("$ASSETS/${freshDurableB()}", """{"ownerId":"$id"}""", b.accessToken)
        }
        bothWays("PATCH /assets location LOCATION", own = locB, foreign = locA, ok = OK) { id ->
            http.patchJson(
                "$ASSETS/${freshDurableB()}",
                """{"location":{"type":"LOCATION","id":"$id"}}""",
                b.accessToken,
            )
        }
        bothWays("PATCH /assets location ASSET", own = durB, foreign = durA, ok = OK) { id ->
            http.patchJson("$ASSETS/${freshDurableB()}", """{"location":{"type":"ASSET","id":"$id"}}""", b.accessToken)
        }
        bothWays("PATCH /assets photoFileId", own = null, foreign = anyFile(), ok = OK) { id ->
            http.patchJson("$ASSETS/${freshDurableB()}", """{"photoFileId":"$id"}""", b.accessToken)
        }

        // --- POST /assets/{id}/merge. El origen es propio y solo cambia el
        // destino, que es la referencia del cuerpo.
        val mergeArticle = b.createArticle("""{"name":"Sonda${short()}","categoryId":"$catB","unit":"GRAM"}""")
        val mergeSource = b.intake(mergeArticle, freshLocationB(), 40)
        val mergeTarget = b.intake(mergeArticle, freshLocationB(), 60)
        bothWays("POST /assets/{propio}/merge targetAssetId", own = mergeTarget, foreign = stockA, ok = OK) { id ->
            http.postJson("$ASSETS/$mergeSource/merge", """{"targetAssetId":"$id"}""", b.accessToken)
        }

        // --- La confusion de tipo en la referencia polimorfica: apuntar con
        // `ASSET` a una ubicacion de A, y con `LOCATION` a un asset de A. Los dos
        // son 404 y **por el mismo camino** que con un identificador propio del
        // tipo equivocado, asi que tampoco distinguen de quien es la fila.
        expectStatus(
            "ref POST /assets location ASSET apuntando a una LOCATION de A",
            HttpStatus.NOT_FOUND,
            http.postJson(
                ASSETS,
                """{"name":"Sonda${short()}","type":"DURABLE","categoryId":"$catB",
                    "location":{"type":"ASSET","id":"$locA"}}""",
                b.accessToken,
            ),
        )
        expectStatus(
            "ref POST /assets location ASSET apuntando a una LOCATION propia",
            HttpStatus.NOT_FOUND,
            http.postJson(
                ASSETS,
                """{"name":"Sonda${short()}","type":"DURABLE","categoryId":"$catB",
                    "location":{"type":"ASSET","id":"$locB"}}""",
                b.accessToken,
            ),
        )
        expectStatus(
            "ref POST /assets location LOCATION apuntando a un ASSET de A",
            HttpStatus.NOT_FOUND,
            http.postJson(
                ASSETS,
                """{"name":"Sonda${short()}","type":"DURABLE","categoryId":"$catB",
                    "location":{"type":"LOCATION","id":"$durA"}}""",
                b.accessToken,
            ),
        )
        expectStatus(
            "ref POST /assets location LOCATION apuntando a un ASSET propio",
            HttpStatus.NOT_FOUND,
            http.postJson(
                ASSETS,
                """{"name":"Sonda${short()}","type":"DURABLE","categoryId":"$catB",
                    "location":{"type":"LOCATION","id":"$durB"}}""",
                b.accessToken,
            ),
        )

        // --- Los dos casos donde la categoria del cuerpo se **ignora** por tener
        // articulo. No dan 404 --el alta es legitima-- pero la categoria ajena no
        // puede acabar guardada, que seria una referencia cruzada silenciosa.
        val created = http.postJson(
            ASSETS,
            """{"type":"DURABLE","articleId":"$artB2","categoryId":"$catA"}""",
            b.accessToken,
        )
        expectStatus("ref POST /assets con articulo propio y categoryId de A", HttpStatus.CREATED, created)
        expectAbsent("ref el asset creado no guarda la categoria de A", catA, created)

        val patched = http.patchJson("$ASSETS/$durBWithArticle", """{"categoryId":"$catA"}""", b.accessToken)
        expectStatus("ref PATCH /assets con articulo y categoryId de A", HttpStatus.OK, patched)
        expectAbsent("ref el asset con articulo no guarda la categoria de A", catA, patched)

        endOfSection("ref")
    }

    // ------------------------------------------------------------------
    // 3. Por los filtros de los listados
    // ------------------------------------------------------------------

    @Test
    @DisplayName("los cuatro listados no devuelven nada de otro hogar, ni filtrando por sus identificadores")
    fun `barrido de listados`() {
        val section = "listado"

        expectAbsent("$section GET /categories", catA, http.getJson(CATEGORIES, b.accessToken))
        expectAbsent(
            "$section GET /categories?includeRetired=true",
            catA,
            http.getJson("$CATEGORIES?includeRetired=true", b.accessToken),
        )

        expectAbsent("$section GET /articles", artA, http.getJson(ARTICLES, b.accessToken))
        expectAbsent(
            "$section GET /articles?includeRetired=true",
            artA,
            http.getJson("$ARTICLES?includeRetired=true", b.accessToken),
        )
        expectEmpty("$section GET /articles?categoryId de A", http.getJson("$ARTICLES?categoryId=$catA", b.accessToken))
        // El codigo de barras esta impreso en el envase, asi que es el dato ajeno
        // mas facil de conocer desde fuera: el filtro que mas tienta como oraculo.
        //
        // Estos dos se comprueban por AUSENCIA DE LA FILA DE A y no exigiendo un
        // listado vacio, y la diferencia importa. La unicidad de nombre y de
        // codigo de barras es **por hogar**, asi que B puede tener legitimamente
        // su propio articulo llamado igual y con el mismo codigo --lo crea la
        // comprobacion de unicidad de mas arriba--. Exigir vacio confundiria eso
        // con una fuga y daria un fallo que no lo es. Lo que no puede pasar, y es
        // lo que se mide, es que aparezca la fila de A.
        expectAbsent(
            "$section GET /articles?barcode de A",
            artA,
            http.getJson("$ARTICLES?barcode=$barcodeOfA", b.accessToken),
        )
        expectAbsent(
            "$section GET /articles?q con el nombre de A",
            artA,
            http.getJson("$ARTICLES?q=AzucarDeA", b.accessToken),
        )

        expectAbsent("$section GET /locations", locA, http.getJson(LOCATIONS, b.accessToken))
        expectEmpty(
            "$section GET /locations?parentLocationId de A",
            http.getJson("$LOCATIONS?parentLocationId=$locA", b.accessToken),
        )

        expectAbsent("$section GET /assets", durA, http.getJson(ASSETS, b.accessToken))
        expectAbsent("$section GET /assets no lista la existencia de A", stockA, http.getJson(ASSETS, b.accessToken))
        expectEmpty("$section GET /assets?locationId de A", http.getJson("$ASSETS?locationId=$locA", b.accessToken))
        expectEmpty("$section GET /assets?parentAssetId de A", http.getJson("$ASSETS?parentAssetId=$durA", b.accessToken))
        expectEmpty("$section GET /assets?ownerId de A", http.getJson("$ASSETS?ownerId=${a.memberId}", b.accessToken))
        expectEmpty("$section GET /assets?articleId de A", http.getJson("$ASSETS?articleId=$artA", b.accessToken))
        expectEmpty("$section GET /assets?categoryId de A", http.getJson("$ASSETS?categoryId=$catA", b.accessToken))
        expectAbsent(
            "$section GET /assets?status=DECOMMISSIONED",
            stockA,
            http.getJson("$ASSETS?status=DECOMMISSIONED", b.accessToken),
        )

        endOfSection(section)
    }

    // ------------------------------------------------------------------
    // 4. Por unicidad
    // ------------------------------------------------------------------

    @Test
    @DisplayName("la unicidad es del hogar: repetir un nombre de otro no choca ni lo delata")
    fun `barrido de oraculos por unicidad`() {
        val section = "unicidad"

        // Si alguna de estas devolviera 409, bastaria intentar crear para
        // averiguar que nombres existen en el hogar de al lado.
        expectStatus(
            "$section POST /categories con el nombre de una de A",
            HttpStatus.CREATED,
            http.postJson(CATEGORIES, """{"name":"CategoriaDeA"}""", b.accessToken),
        )
        expectStatus(
            "$section PATCH /categories renombrando al nombre de otra de A",
            HttpStatus.OK,
            http.patchJson("$CATEGORIES/${freshCategoryB()}", """{"name":"SegundaCategoriaDeA"}""", b.accessToken),
        )
        expectStatus(
            "$section POST /articles con el nombre y el codigo de barras de A",
            HttpStatus.CREATED,
            http.postJson(
                ARTICLES,
                """{"name":"AzucarDeA","categoryId":"$catB","unit":"GRAM","barcode":"$barcodeOfA"}""",
                b.accessToken,
            ),
        )
        expectStatus(
            "$section POST /locations raiz con el nombre de la raiz de A",
            HttpStatus.CREATED,
            http.postJson(LOCATIONS, """{"name":"CasaDeA","type":"HOUSE"}""", b.accessToken),
        )

        endOfSection(section)
    }

    // ------------------------------------------------------------------
    // Cierre: A tiene que estar exactamente como estaba
    // ------------------------------------------------------------------

    @AfterAll
    fun elHogarASigueIntacto() {
        snapshotOfA.forEach { (path, before) ->
            val now = http.getJson(path, a.accessToken)
            checks += "intacto $path"
            if (now.statusCode != HttpStatus.OK) {
                deviations += "intacto $path -> ${now.statusCode.value()} despues del barrido"
            } else if (now.body != before) {
                deviations += "intacto $path -> el cuerpo cambio.\nAntes: $before\nDespues: ${now.body}"
            }
        }

        println(
            """

            ================ BARRIDO DE AISLAMIENTO DEL HITO 2 ================
            Comprobaciones ejecutadas: ${checks.size}
            Desviaciones: ${deviations.size}

            --- Registro ---
            ${log.joinToString("\n")}

            --- Desviaciones ---
            ${if (deviations.isEmpty()) "Ninguna." else deviations.joinToString("\n")}
            ===================================================================
            """.trimIndent(),
        )

        check(deviations.isEmpty()) { "El barrido encontro ${deviations.size} desviaciones" }
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    /**
     * Las catorce operaciones con identificador en la ruta, cada una como una
     * llamada que solo espera el identificador. Es lo que permite ejecutarlas dos
     * veces --con el de A y con uno inventado-- sin repetir el cuerpo.
     */
    private fun operationsWithPathId(): List<Pair<String, (String) -> ResponseEntity<String>>> = listOf(
        "PATCH /categories/{id}" to { id ->
            http.patchJson("$CATEGORIES/$id", """{"name":"Sonda${short()}"}""", b.accessToken)
        },
        "DELETE /categories/{id}" to { id -> http.deleteJson("$CATEGORIES/$id", b.accessToken) },
        "GET /articles/{id}" to { id -> http.getJson("$ARTICLES/$id", b.accessToken) },
        "PATCH /articles/{id}" to { id ->
            http.patchJson("$ARTICLES/$id", """{"name":"Sonda${short()}"}""", b.accessToken)
        },
        "DELETE /articles/{id}" to { id -> http.deleteJson("$ARTICLES/$id", b.accessToken) },
        "GET /locations/{id}" to { id -> http.getJson("$LOCATIONS/$id", b.accessToken) },
        "GET /locations/{id}/children" to { id -> http.getJson("$LOCATIONS/$id/children", b.accessToken) },
        "PATCH /locations/{id}" to { id ->
            http.patchJson("$LOCATIONS/$id", """{"name":"Sonda${short()}"}""", b.accessToken)
        },
        "DELETE /locations/{id}" to { id -> http.deleteJson("$LOCATIONS/$id", b.accessToken) },
        "GET /assets/{id}" to { id -> http.getJson("$ASSETS/$id", b.accessToken) },
        "GET /assets/{id}/children" to { id -> http.getJson("$ASSETS/$id/children", b.accessToken) },
        "PATCH /assets/{id}" to { id -> http.patchJson("$ASSETS/$id", """{"notes":"sonda"}""", b.accessToken) },
        "DELETE /assets/{id}" to { id -> http.deleteJson("$ASSETS/$id", b.accessToken) },
        "POST /assets/{id}/merge" to { id ->
            http.postJson("$ASSETS/$id/merge", """{"targetAssetId":"$stockB"}""", b.accessToken)
        },
    )

    /** El recurso de A que le toca a cada operacion, por su ruta. */
    private fun foreignIdFor(operation: String): String = when {
        operation.contains("/categories") -> catA
        operation.contains("/articles") -> artA
        operation.contains("/locations") -> locA
        operation.contains("/merge") -> stockA
        else -> durA
    }

    /**
     * El par que da sentido a la comprobacion: primero con el identificador de A
     * --tiene que ser 404-- y despues **el mismo cuerpo** con uno propio, que
     * tiene que funcionar.
     *
     * Sin la segunda mitad, un cuerpo mal escrito daria 404 por su cuenta y la
     * prueba pasaria sin haber comprobado el aislamiento. Con [own] a nulo se
     * declara que no hay control posible, que hoy solo le pasa a `photoFileId`:
     * la tabla `files` no puede tener filas hasta el Hito 3, asi que **cualquier**
     * identificador de fichero da 404 y eso es lo correcto.
     */
    private fun bothWays(
        name: String,
        own: String?,
        foreign: String,
        ok: Set<HttpStatus>,
        send: (String) -> ResponseEntity<String>,
    ) {
        expectStatus("ref $name con identificador de A", HttpStatus.NOT_FOUND, send(foreign))

        if (own == null) {
            log += "ref $name => sin control positivo posible (files llega con el Hito 3)"
            return
        }

        val allowed = send(own)
        checks += "ref $name control positivo con identificador propio"
        log += "ref $name control positivo => ${allowed.statusCode.value()}"
        if (allowed.statusCode !in ok) {
            deviations += "ref $name control positivo -> ${allowed.statusCode.value()}, asi que el 404 del " +
                "caso ajeno no demuestra nada: podria venir del cuerpo. Cuerpo: ${allowed.body}"
        }
    }

    private fun expectStatus(label: String, expected: HttpStatus, response: ResponseEntity<String>) {
        checks += label
        log += "$label => ${response.statusCode.value()} ${response.body.orEmpty().take(160)}"
        if (response.statusCode != expected) {
            deviations += "$label -> ${response.statusCode.value()} en vez de ${expected.value()}; " +
                "cuerpo: ${response.body}"
        }
    }

    /** El identificador ajeno no puede aparecer ni siquiera dentro de una respuesta con exito. */
    private fun expectAbsent(label: String, needle: String, response: ResponseEntity<String>) {
        checks += label
        val body = response.body.orEmpty()
        if (response.statusCode != HttpStatus.OK && response.statusCode != HttpStatus.CREATED) {
            deviations += "$label -> ${response.statusCode.value()}, que no se esperaba; cuerpo: $body"
        } else if (body.contains(needle)) {
            deviations += "$label -> aparece «$needle» en la respuesta: $body"
        }
    }

    private fun expectPresent(label: String, needle: String, response: ResponseEntity<String>) {
        checks += label
        val body = response.body.orEmpty()
        if (!body.contains(needle)) {
            deviations += "$label -> no aparece «$needle»; cuerpo: $body"
        }
    }

    private fun expectEmpty(label: String, response: ResponseEntity<String>) {
        checks += label
        val body = response.body.orEmpty()
        when {
            response.statusCode != HttpStatus.OK ->
                deviations += "$label -> ${response.statusCode.value()}; cuerpo: $body"

            body.extractRaw("total") != "0" ->
                deviations += "$label -> el listado no viene vacio: $body"
        }
    }

    private fun expectQuantity(label: String, assetId: String, expected: String, owner: TestHousehold) {
        checks += label
        val body = http.getJson("$ASSETS/$assetId", owner.accessToken).body.orEmpty()
        val quantity = body.extractRaw("quantity")
        if (quantity.trimEnd('0').trimEnd('.') != expected.trimEnd('0').trimEnd('.')) {
            deviations += "$label -> cantidad $quantity en vez de $expected; cuerpo: $body"
        }
    }

    private fun endOfSection(section: String) {
        val failed = deviations.filter { it.startsWith(section) }
        check(failed.isEmpty()) {
            "El barrido de «$section» encontro ${failed.size} desviaciones:\n" + failed.joinToString("\n")
        }
    }

    private fun freshCategoryB() = b.createCategory("""{"name":"Sonda${short()}"}""")

    private fun freshArticleB() =
        b.createArticle("""{"name":"Sonda${short()}","categoryId":"$catB","unit":"UNIT"}""")

    private fun freshLocationB() = b.createLocation("""{"name":"Sonda${short()}","type":"ROOM"}""")

    private fun freshDurableB() =
        b.createAsset("""{"name":"Sonda${short()}","type":"DURABLE","categoryId":"$catB"}""")

    /** Un identificador de fichero cualquiera: hoy ninguno existe, y esa es la regla. */
    private fun anyFile() = UUID.randomUUID().toString()

    private fun short() = UUID.randomUUID().toString().take(8)

    private fun TestHousehold.createCategory(body: String) = created(CATEGORIES, body)

    private fun TestHousehold.createArticle(body: String) = created(ARTICLES, body)

    private fun TestHousehold.createLocation(body: String) = created(LOCATIONS, body)

    private fun TestHousehold.createAsset(body: String) = created(ASSETS, body)

    private fun TestHousehold.created(path: String, body: String): String {
        val response = http.postJson(path, body, accessToken)
        check(response.statusCode == HttpStatus.CREATED) {
            "No se pudo preparar el escenario con POST $path: ${response.statusCode} ${response.body}"
        }
        return response.body!!.extract("id")
    }

    private fun TestHousehold.intake(articleId: String, locationId: String, quantity: Int): String {
        val response = http.postJson(
            INTAKE,
            """{"articleId":"$articleId","ownerId":"$memberId","quantity":$quantity,
                "location":{"type":"LOCATION","id":"$locationId"}}""",
            accessToken,
        )
        check(response.statusCode == HttpStatus.CREATED) {
            "No se pudo preparar la existencia: ${response.statusCode} ${response.body}"
        }
        return response.body!!.extract("id")
    }

    private companion object {
        const val CATEGORIES = "/api/v1/categories"
        const val ARTICLES = "/api/v1/articles"
        const val LOCATIONS = "/api/v1/locations"
        const val ASSETS = "/api/v1/assets"
        const val INTAKE = "/api/v1/assets/intake"

        val OK = setOf(HttpStatus.OK)
        val CREATED = setOf(HttpStatus.CREATED)

        /** La entrada responde `201` si crea la existencia y `200` si suma sobre una que ya habia. */
        val INTAKE_OK = setOf(HttpStatus.OK, HttpStatus.CREATED)
    }
}
