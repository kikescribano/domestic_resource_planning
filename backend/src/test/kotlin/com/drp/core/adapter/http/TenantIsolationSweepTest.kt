package com.drp.core.adapter.http

import com.drp.platform.schedule.DailySweep
import com.drp.test.DrpMailpit
import com.drp.test.SpringIntegrationTest
import com.drp.test.TestHousehold
import com.drp.test.deleteJson
import com.drp.test.extract
import com.drp.test.extractRaw
import com.drp.test.getJson
import com.drp.test.imageBytes
import com.drp.test.patchJson
import com.drp.test.postJson
import com.drp.test.registerHousehold
import com.drp.test.today
import com.drp.test.uploadFile
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
import java.time.LocalDate
import java.util.UUID

/**
 * El barrido de aislamiento del contrato entero: las **treinta y ocho**
 * operaciones de la Fase 1, las **cuarenta y cuatro** que trajo la Fase 2 --tres
 * de activacion, tres de avisos, siete de Proveedores, diez de Warehouse, diez de
 * Compras y once de Mantenimiento--, las **cuatro** de la baja de hogar y el
 * cierre de cuenta (ADR-012), las **cuatro** del catalogo de etiquetas y las
 * **dos** de la gestion de miembros: la reactivacion que trajo el interruptor de
 * «Personas», y `DELETE /users/{id}`, que es de la Fase 1 y se quedo fuera del
 * barrido --su 404 vivia en `HouseholdIsolationTest`, anterior a esta clase--
 * hasta que sembrar a los miembros de A que las dos necesitan lo hizo barato.
 *
 * No sustituye a las pruebas de recorrido de cada recurso ni a las de gate de
 * cada modulo: comprueba una sola cosa --la de la ADR-002-- sobre todas ellas y
 * de forma sistematica.
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
 * ## Que operacion entra, y con que criterio
 *
 * Entra la que **puede nombrar o devolver algo del hogar A**, que son las cuatro
 * formas de arriba. Queda fuera la que no acepta ningun identificador --ni en la
 * ruta, ni en el cuerpo, ni en un filtro-- y no devuelve ninguna fila del hogar:
 * sin nada que nombrar no tiene por donde cruzar, y meterla igual seria una
 * prueba que **no puede fallar**. Es el mismo criterio con el que la Fase 1 cubrio
 * treinta y ocho de sus cincuenta y cuatro operaciones y dejo fuera las nueve de
 * autenticacion y las de identidad, que no hablan de hogares.
 *
 * De las cuarenta y cuatro de la Fase 2, **cuarenta y una** entran por una de las
 * cuatro secciones. Las **tres restantes** --`activateModule`, `deactivateModule`
 * y `markAllNoticesRead`-- no aceptan ningun identificador del hogar A: la clave
 * de un modulo es global y las tres actuan sobre «lo mio». Pero lo que pueden
 * romper es real --escribir en el hogar equivocado-- y no se mide con un `404`,
 * asi que **entran por el cierre**: B las ejecuta durante el barrido y el retrato
 * de A tiene que seguir siendo el mismo al terminar. Estan por tanto cubiertas las
 * cuarenta y cuatro, con dos instrumentos distintos y no con uno forzado.
 *
 * **De las cuatro de etiquetas, tres entran por las cuatro secciones y una no.**
 * `PATCH /tags/{id}` y `DELETE /tags/{id}` llevan identificador en la ruta
 * --seccion 1--, `POST /tags` puede delatar por unicidad --seccion 4-- y
 * `GET /tags` devuelve filas del hogar --seccion 3--. Y el hito trae ademas dos
 * referencias nuevas **dentro del cuerpo**, que es la seccion 2 y la forma que
 * la clave ajena no detiene limpiamente: el `tagIds` de `POST /assets` y el de
 * `PATCH /assets/{id}`. Van **dentro de un array**, que es lo que las hace
 * distintas de todas las anteriores: la referencia no es el campo sino cada uno
 * de sus elementos, asi que hay un caso con una etiqueta propia delante de la
 * ajena --si el resolutor solo mirase la primera, la de A pasaria detras de una
 * legitima y ninguna prueba de recorrido lo notaria.
 *
 * **Las cuatro de la baja de hogar caen todas en ese segundo grupo**, y con mas
 * motivo que ninguna: ni `getCurrentHousehold`, ni las dos de la baja, ni
 * `closeAccount` aceptan identificador --el sujeto lo pone el token--, y a la vez
 * son las que mas dano harian escribiendo en el hogar equivocado. Se miden en su
 * propia seccion, «la baja no cruza de hogar», con el mismo instrumento de
 * asimetria que la activacion: B pide su baja y el hogar A tiene que seguir sin
 * ninguna.
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
 *
 * ## Por que crece esta clase y no nace una por modulo
 *
 * **No estrena contexto de Spring, y aqui eso decide el diseno.** Una clase de
 * barrido por modulo serian cuatro clases mas; con la misma configuracion que
 * esta compartirian contexto, pero cada una tendria que sembrar sus dos hogares
 * enteros --el barrido de un modulo necesita el core debajo-- y ninguna podria
 * cruzar de modulo, que es justo donde estan las referencias interesantes: el
 * `supplierId` de un plan de CMMS, el `articleId` del core en una linea de la
 * lista de la compra. Creciendo aqui, el `@TestInstance(PER_CLASS)` con los dos
 * hogares sembrados **una vez** sigue valiendo, el contexto sigue siendo el
 * compartido por las treinta y nueve clases de la suite y el reparto por forma de
 * ataque --que es lo que se olvida en una operacion nueva-- se conserva.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TenantIsolationSweepTest : SpringIntegrationTest() {

    @Autowired private lateinit var http: TestRestTemplate

    /**
     * El recorrido diario, **invocado a mano**: es el unico camino por el que
     * nace un aviso, y sin un aviso de A la seccion de avisos no tendria nada que
     * atacar. Es el mismo metodo que invoca el `@Scheduled`, que en toda la suite
     * esta apagado.
     */
    @Autowired private lateinit var sweep: DailySweep

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
    private lateinit var fileA: String
    private lateinit var docA: String
    private lateinit var loanA: String
    private lateinit var lentAssetA: String
    private lateinit var tagA: String
    private lateinit var tagA2: String

    /**
     * Los miembros de A para las dos operaciones de la gestion de miembros: uno
     * **activo** --el unico al que una baja cruzada podria apagar-- y uno **dado
     * de baja**, que es lo unico que una reactivacion cruzada podria encender.
     * Con los papeles al reves, ninguna de las dos podria cambiar nada y la
     * comprobacion pasaria sin haber medido.
     */
    private lateinit var memberA: String
    private lateinit var exMemberA: String

    /** Lo que el hogar A tiene **en los cuatro modulos**, con los cuatro encendidos. */
    private lateinit var supA: String
    private lateinit var linkA: String
    private lateinit var lotA: String
    private lateinit var itemA: String
    private lateinit var purchaseA: String
    private lateinit var planA: String
    private lateinit var interventionA: String
    private lateinit var noticeA: String

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
    private lateinit var fileB: String
    private lateinit var lendableB: String
    private lateinit var docB: String
    private lateinit var tagB: String
    private lateinit var tagB2: String

    /** Y lo propio de B en los cuatro modulos, con lo que se hacen los controles positivos. */
    private lateinit var supB: String
    private lateinit var supB2: String
    private lateinit var linkB: String
    private lateinit var lotB: String
    private lateinit var itemB: String
    private lateinit var purchaseB: String
    private lateinit var planB: String

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

        // Las etiquetas del Hito 4 del cierre de huecos. La de A va **puesta** en
        // un asset suyo, que es lo que hace que el filtro `tagId` tenga algo que
        // devolver si cruzara: un filtro sobre una etiqueta que nadie lleva
        // devolveria vacio pase lo que pase.
        tagA = a.createTag("EtiquetaDeA")
        tagA2 = a.createTag("SegundaEtiquetaDeA")
        a.tagAsset(durA, tagA)

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

        // Los ficheros del Hito 3. Antes de el, `files` no podia tener ninguna
        // fila y todo identificador de fichero daba 404 por vacio; ahora hay uno
        // de cada hogar, asi que las comprobaciones de `photoFileId` y de `fileId`
        // pasan a tener control positivo de verdad.
        fileA = a.uploadImage()
        fileB = b.uploadImage()
        docA = a.attachDocument(durA, a.uploadImage())
        // B necesita el suyo desde el Hito 5 de la Fase 2: `manualDocumentId` de
        // una maquina de CMMS es una referencia del cuerpo, y sin documento propio
        // su control positivo no existiria.
        docB = b.attachDocument(durB, b.uploadImage())

        // Los prestamos del Hito 4. El de A queda ABIERTO a proposito: es el
        // estado en el que un intento ajeno puede hacer dano de verdad --darlo
        // por devuelto-- y no solo leerlo.
        lentAssetA = a.createAsset("""{"name":"PrestadoDeA","type":"DURABLE","categoryId":"$catA"}""")
        loanA = a.startLoan(lentAssetA)
        // Y B necesita un DURABLE libre para su control positivo, porque el suyo
        // de siempre lo usan otras secciones.
        lendableB = b.createAsset("""{"name":"PrestableDeB","type":"DURABLE","categoryId":"$catB"}""")

        tagB = b.createTag("EtiquetaDeB")
        tagB2 = b.createTag("SegundaEtiquetaDeB")

        // Los miembros de la gestion de miembros. La baja del segundo la hace A
        // sobre si mismo, que es el unico camino legitimo para dejar una
        // pertenencia apagada delante del barrido.
        memberA = a.inviteMember()
        exMemberA = a.inviteMember()
        check(http.deleteJson("$USERS/$exMemberA", a.accessToken).statusCode == HttpStatus.NO_CONTENT) {
            "No se pudo dar de baja al miembro que el barrido necesita apagado"
        }

        // -----------------------------------------------------------------
        // Los cuatro modulos de la Fase 2, encendidos en los dos hogares
        // -----------------------------------------------------------------
        //
        // **Encendidos en los dos y no en uno.** Con el modulo apagado en B, toda
        // ruta de modulo responderia `403 MODULE_INACTIVE` y el barrido pasaria
        // entero sin haber medido el aislamiento: el gate taparia la pregunta. Lo
        // que aqui se comprueba es lo contrario --que **con el modulo encendido**
        // sigue sin poderse alcanzar al vecino--, que es la unica configuracion en
        // la que la respuesta significa algo.
        listOf(a, b).forEach { home -> MODULES.forEach { home.activateModule(it) } }

        supA = a.createSupplier("FontaneroDeA", "PLUMBING")
        supB = b.createSupplier("FontaneroDeB", "PLUMBING")
        supB2 = b.createSupplier("ElectricistaDeB", "ELECTRICITY")
        linkA = a.linkSupplier(supA, """{"locationId":"$locA"}""")
        linkB = b.linkSupplier(supB, """{"locationId":"$locB"}""")

        // Los lotes caducan pronto **a proposito**: es lo que hace que la pasada
        // diaria levante un aviso, y sin aviso de A la seccion de avisos no
        // tendria identificador que atacar.
        lotA = a.createLot(stockA, "LOTE-DE-A")
        lotB = b.createLot(stockB, "LOTE-DE-B")

        itemA = a.addShoppingItem("""{"articleId":"$artA","quantity":2}""")
        itemB = b.addShoppingItem("""{"articleId":"$artB","quantity":2}""")
        // Uno aparte por hogar para la compra, porque crear una compra se lleva
        // sus lineas a `IN_PURCHASE` y dejaria sin sujeto las comprobaciones de la
        // lista.
        purchaseA = a.createPurchase(a.addShoppingItem("""{"name":"CompraDeA","quantity":1}"""), supA)
        purchaseB = b.createPurchase(b.addShoppingItem("""{"name":"CompraDeB","quantity":1}"""), supB)

        planA = a.createPlan(durA, "RevisionDeA")
        planB = b.createPlan(durB, "RevisionDeB")
        interventionA = a.registerIntervention(durA, "ArregloDeA")

        // La pasada diaria, que es el unico camino por el que nace un aviso. Se
        // ejecuta **antes del retrato** para que el de A entre en el con su estado
        // de no leido, que es lo que `markAllNoticesRead` de B no puede cambiar.
        sweep.run()
        noticeA = a.firstNotice()
        b.firstNotice()

        listOf(
            "/api/v1/categories?includeRetired=true",
            "/api/v1/tags?includeRetired=true",
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
            "/api/v1/documents",
            "/api/v1/loans",
            "/api/v1/loans/$loanA",
            // Las de la Fase 2. `/modules` y `/notices` no estan aqui por
            // simetria: son **el unico instrumento** que mide las tres operaciones
            // sin identificador que atacar --activar, desactivar y marcar todo
            // leido--. Si alguna de las tres escribiera en el hogar equivocado, lo
            // que cambiaria es este retrato y no ninguna respuesta.
            "/api/v1/modules",
            "/api/v1/notices",
            // Y el estado del hogar, que es el instrumento de las cuatro
            // operaciones de la baja: si la de B marcara el hogar equivocado, lo
            // que cambiaria es este cuerpo y ninguna respuesta.
            "/api/v1/households/current",
            // Las personas de A, CON las dadas de baja dentro: es lo unico que
            // cambiaria si una baja o una reactivacion cruzaran de hogar --el
            // listado por defecto excluye a los apagados, asi que sin el flag una
            // baja cruzada solo haria desaparecer una fila sin dejar rastro que
            // comparar. Sin avatares no lleva URL firmada, asi que el cuerpo es
            // estable y puede entrar en el retrato.
            "/api/v1/users?includeDeactivated=true",
            "/api/v1/suppliers",
            "/api/v1/suppliers/$supA",
            "/api/v1/warehouse/stock",
            "/api/v1/warehouse/lots",
            "/api/v1/purchasing/list",
            "/api/v1/purchasing/purchases/$purchaseA",
            "/api/v1/maintenance/plans/$planA",
            "/api/v1/maintenance/interventions",
        ).forEach { snapshotOfA[it] = http.getJson(it, a.accessToken).body!! }

        // Los ficheros no entran en el retrato: sus dos URL van firmadas y la
        // caducidad cambia con cada lectura, asi que el cuerpo nunca es igual dos
        // veces. Lo que hay que comprobar de ellos --que siguen ahi y que siguen
        // siendo suyos-- lo miran las secciones de ruta y de listado.
    }

    // ------------------------------------------------------------------
    // 1. Por el identificador del recurso
    // ------------------------------------------------------------------

    @Test
    @DisplayName("las cuarenta y seis operaciones con identificador en la ruta se niegan ante uno de otro hogar")
    fun `barrido por identificador del recurso`() {
        val section = "ruta"

        operationsWithPathId().forEach { (name, call) ->
            // 404 y no 403: el contrato no puede distinguir "no existe" de
            // "existe pero no es tuyo" sin convertir el identificador en oraculo.
            expectStatus("$section $name con identificador de A", refusalOf(name), call(foreignIdFor(name)))
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

        // Y las tres del desenlace de un proveedor, que es la otra operacion con
        // **dos identificadores en la ruta**. Las tres combinaciones importan
        // porque el enlace se resuelve por el par: bastaria resolver el segundo
        // sin comprobar que cuelga del primero para que el proveedor de B sirviera
        // de llave contra el enlace de A.
        expectStatus(
            "$section DELETE /suppliers/{de A}/links/{de A}",
            HttpStatus.NOT_FOUND,
            http.deleteJson("$SUPPLIERS/$supA/links/$linkA", b.accessToken),
        )
        expectStatus(
            "$section DELETE /suppliers/{de B}/links/{de A}",
            HttpStatus.NOT_FOUND,
            http.deleteJson("$SUPPLIERS/$supB/links/$linkA", b.accessToken),
        )
        expectStatus(
            "$section DELETE /suppliers/{de A}/links/{de B}",
            HttpStatus.NOT_FOUND,
            http.deleteJson("$SUPPLIERS/$supA/links/$linkB", b.accessToken),
        )

        // Un 404 que ademas hubiera modificado algo seria peor que un 200: cada
        // recurso de A se relee en su hogar y tiene que estar como estaba.
        val category = http.getJson("$CATEGORIES?includeRetired=true", a.accessToken)
        expectPresent("$section la categoria de A sigue viva", catA, category)
        expectPresent("$section la categoria de A conserva su nombre", "CategoriaDeA", category)

        val tags = http.getJson("$TAGS?includeRetired=true", a.accessToken)
        expectPresent("$section la etiqueta de A sigue vigente", "\"retiredAt\":null", tags)
        expectPresent("$section la etiqueta de A conserva su nombre", "EtiquetaDeA", tags)
        expectPresent(
            "$section el asset de A sigue llevandola puesta",
            tagA,
            http.getJson("$ASSETS/$durA", a.accessToken),
        )

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

        // Y lo mismo con lo de los cuatro modulos, que es donde estan las
        // operaciones que **escriben en el core**: consumir de una existencia y
        // recibir una compra. Que respondan 404 no basta --importa que no hayan
        // hecho nada por el camino--.
        expectPresent(
            "$section el proveedor de A sigue vigente",
            "\"retiredAt\":null",
            http.getJson("$SUPPLIERS/$supA", a.accessToken),
        )
        expectPresent("$section el enlace de A sigue puesto", linkA, http.getJson("$SUPPLIERS/$supA", a.accessToken))
        expectPresent("$section el lote de A sigue vivo", lotA, http.getJson(LOTS, a.accessToken))
        expectPresent("$section la linea de A sigue en su lista", itemA, http.getJson(SHOPPING_LIST, a.accessToken))
        expectPresent(
            "$section la compra de A sigue abierta",
            "\"status\":\"OPEN\"",
            http.getJson("$PURCHASES/$purchaseA", a.accessToken),
        )
        expectPresent(
            "$section el plan de A sigue vivo",
            "\"cancelledAt\":null",
            http.getJson("$PLANS/$planA", a.accessToken),
        )

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
            } else if (foreign.statusCode != refusalOf(name)) {
                deviations += "$section $name -> ${foreign.statusCode.value()} en vez de " +
                    "${refusalOf(name).value()}; cuerpo: ${foreign.body}"
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
        bothWays("POST /articles photoFileId", own = b.uploadImage(), foreign = fileA, ok = CREATED) { id ->
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
        bothWays("PATCH /articles photoFileId", own = b.uploadImage(), foreign = fileA, ok = OK) { id ->
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
        bothWays("POST /locations photoFileId", own = b.uploadImage(), foreign = fileA, ok = CREATED) { id ->
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
        bothWays("PATCH /locations photoFileId", own = b.uploadImage(), foreign = fileA, ok = OK) { id ->
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
        bothWays("POST /assets photoFileId", own = b.uploadImage(), foreign = fileA, ok = CREATED) { id ->
            http.postJson(
                ASSETS,
                """{"name":"Sonda${short()}","type":"DURABLE","categoryId":"$catB","photoFileId":"$id"}""",
                b.accessToken,
            )
        }

        // Las etiquetas del Hito 4. Van **dentro de un array**, que es la forma
        // que mas facil es dejar sin resolver: la referencia no es el campo sino
        // cada uno de sus elementos, y un bucle que se olvide de uno no falla en
        // ninguna prueba de recorrido.
        bothWays("POST /assets tagIds", own = tagB, foreign = tagA, ok = CREATED) { id ->
            http.postJson(
                ASSETS,
                """{"name":"Sonda${short()}","type":"DURABLE","categoryId":"$catB","tagIds":["$id"]}""",
                b.accessToken,
            )
        }
        // Y con una propia delante: si el resolutor solo mirase la primera, la de
        // A pasaria detras de una legitima.
        bothWays("POST /assets tagIds con una propia delante", own = tagB2, foreign = tagA, ok = CREATED) { id ->
            http.postJson(
                ASSETS,
                """{"name":"Sonda${short()}","type":"DURABLE","categoryId":"$catB","tagIds":["$tagB","$id"]}""",
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
        bothWays("POST /assets/intake article.photoFileId", own = b.uploadImage(), foreign = fileA, ok = INTAKE_OK) { id ->
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
        bothWays("PATCH /assets photoFileId", own = b.uploadImage(), foreign = fileA, ok = OK) { id ->
            http.patchJson("$ASSETS/${freshDurableB()}", """{"photoFileId":"$id"}""", b.accessToken)
        }
        bothWays("PATCH /assets tagIds", own = tagB, foreign = tagA, ok = OK) { id ->
            http.patchJson("$ASSETS/${freshDurableB()}", """{"tagIds":["$id"]}""", b.accessToken)
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

        // --- POST /documents, con sus tres referencias. Es la operacion del Hito
        // 3 con mas superficie: dos destinos posibles y un contenido, y las tres
        // llegan del cliente.
        bothWays("POST /documents assetId", own = freshDurableB(), foreign = durA, ok = CREATED) { id ->
            http.postJson(
                DOCUMENTS,
                """{"assetId":"$id","type":"MANUAL","url":"https://ejemplo.test/${short()}"}""",
                b.accessToken,
            )
        }
        bothWays("POST /documents articleId", own = freshArticleB(), foreign = artA, ok = CREATED) { id ->
            http.postJson(
                DOCUMENTS,
                """{"articleId":"$id","type":"MANUAL","url":"https://ejemplo.test/${short()}"}""",
                b.accessToken,
            )
        }
        bothWays("POST /documents fileId", own = b.uploadImage(), foreign = fileA, ok = CREATED) { id ->
            http.postJson(
                DOCUMENTS,
                """{"assetId":"${freshDurableB()}","type":"INVOICE","fileId":"$id"}""",
                b.accessToken,
            )
        }

        // --- POST /loans, con sus tres referencias. Es la operacion del Hito 4
        // con mas superficie, y las tres son de tipos distintos: un asset y dos
        // pertenencias.
        bothWays("POST /loans assetId", own = freshDurableB(), foreign = lentAssetA, ok = CREATED) { id ->
            http.postJson(
                LOANS,
                """{"assetId":"$id","lender":{"userId":"${b.memberId}"},"borrower":{"userId":"${b.memberId}"}}""",
                b.accessToken,
            )
        }
        // Las dos pertenencias responden 400 y no 404: el que no existe no es el
        // recurso de la ruta sino un campo del cuerpo, asi que el contrato lo
        // trata como error de forma. Lo que importa para el aislamiento es que
        // **no se acepte**, no cual de los dos codigos sale.
        bothWays(
            "POST /loans lender.userId",
            own = b.memberId,
            foreign = a.memberId,
            ok = CREATED,
            expectedForForeign = HttpStatus.BAD_REQUEST,
        ) { id ->
            http.postJson(
                LOANS,
                """{"assetId":"${freshDurableB()}","lender":{"userId":"$id"},
                    "borrower":{"userId":"${b.memberId}"}}""",
                b.accessToken,
            )
        }
        bothWays(
            "POST /loans borrower.userId",
            own = b.memberId,
            foreign = a.memberId,
            ok = CREATED,
            expectedForForeign = HttpStatus.BAD_REQUEST,
        ) { id ->
            http.postJson(
                LOANS,
                """{"assetId":"${freshDurableB()}","lender":{"userId":"${b.memberId}"},
                    "borrower":{"userId":"$id"}}""",
                b.accessToken,
            )
        }

        // ------------------------------------------------------------------
        // Y las de los cuatro modulos de la Fase 2
        // ------------------------------------------------------------------
        //
        // Aqui esta la mitad interesante del barrido de esta fase, y es la que una
        // clase por modulo no podria escribir: **casi todas cruzan la frontera**.
        // El destino de un enlace de Proveedores es del core; el `assetId` de un
        // lote es una existencia del core; el `supplierId` de un plan de CMMS es
        // de otro modulo, y llega por un puerto de plataforma que tiene su propia
        // forma de decir «no existe».

        // --- Proveedores: el destino polimorfico del enlace, exactamente uno de
        // los dos. Las dos mitades van sobre el proveedor **propio** de B.
        bothWays("POST /suppliers/{propio}/links assetId", own = freshDurableB(), foreign = durA, ok = CREATED) { id ->
            http.postJson("$SUPPLIERS/$supB2/links", """{"assetId":"$id"}""", b.accessToken)
        }
        bothWays(
            "POST /suppliers/{propio}/links locationId",
            own = freshLocationB(),
            foreign = locA,
            ok = CREATED,
        ) { id ->
            http.postJson("$SUPPLIERS/$supB2/links", """{"locationId":"$id"}""", b.accessToken)
        }

        // --- Warehouse: el lote cuelga de una existencia del core. Se niega con
        // el mismo `409 STOCK_ITEM_NOT_TRACKED` que el consumo, y por lo mismo:
        // este modulo no distingue «no existe» de «no la sigo».
        bothWays(
            "POST /warehouse/lots assetId",
            own = stockB,
            foreign = stockA,
            ok = CREATED,
            expectedForForeign = HttpStatus.CONFLICT,
        ) { id ->
            http.postJson(
                LOTS,
                """{"assetId":"$id","lotCode":"Sonda${short()}",
                    "expiresOn":"${today().plusMonths(6)}","quantity":1}""",
                b.accessToken,
            )
        }

        // --- Compras: el articulo del core en una linea de la lista, y las tres
        // referencias del recibo. `itemIds` es la unica del barrido que viaja
        // **dentro de un array**, que es la forma que mas facil es dejar sin
        // resolver: el bucle valida elemento a elemento o no valida ninguno.
        bothWays("POST /purchasing/list articleId", own = freshArticleB(), foreign = artA, ok = CREATED) { id ->
            http.postJson(SHOPPING_LIST, """{"articleId":"$id","quantity":1}""", b.accessToken)
        }
        bothWays("PATCH /purchasing/list articleId", own = freshArticleB(), foreign = artA, ok = OK) { id ->
            http.patchJson("$SHOPPING_LIST/${freshShoppingItemB()}", """{"articleId":"$id"}""", b.accessToken)
        }
        // Las dos de crear una compra se niegan con `409`, y las dos por decision
        // del modulo y no por descuido. `itemIds` responde «ninguna de esas lineas
        // esta disponible», que es la misma frase para una linea ya comprada y
        // para una que no existe. Y `supplierId` responde «o ese sitio no existe,
        // o Proveedores esta apagado», que es **la degradacion escrita**: el
        // puerto de dato maestro devuelve vacio con el modulo dueno apagado, y
        // distinguir los dos casos delataria que el vecino tiene Proveedores.
        bothWays(
            "POST /purchasing/purchases itemIds",
            own = freshShoppingItemB(),
            foreign = itemA,
            ok = CREATED,
            expectedForForeign = HttpStatus.CONFLICT,
        ) { id ->
            http.postJson(PURCHASES, """{"itemIds":["$id"]}""", b.accessToken)
        }
        bothWays(
            "POST /purchasing/purchases supplierId",
            own = supB,
            foreign = supA,
            ok = CREATED,
            expectedForForeign = HttpStatus.CONFLICT,
        ) { id ->
            http.postJson(
                PURCHASES,
                """{"supplierId":"$id","itemIds":["${freshShoppingItemB()}"]}""",
                b.accessToken,
            )
        }
        // El recibo, con sus tres referencias. Se prepara una compra propia por
        // caso porque recibir la cierra, y una compra cerrada ya no admite otro
        // recibo: sin esto, el control positivo del segundo caso fallaria por una
        // razon que no es la que se mide.
        // `lines.itemId` no cabe en `bothWays` porque su control positivo depende
        // de la compra que la propia llamada crea --la linea tiene que ser de esa
        // compra--, asi que el par se escribe a mano con la misma forma.
        val foreignLineTarget = freshPurchaseB()
        expectStatus(
            "ref POST /purchasing/purchases/{propia}/receipt lines.itemId con linea de A",
            HttpStatus.NOT_FOUND,
            http.postJson(
                "$PURCHASES/$foreignLineTarget/receipt",
                """{"lines":[{"itemId":"$itemA","quantity":1}]}""",
                b.accessToken,
            ),
        )
        expectStatus(
            "ref POST /purchasing/purchases/{propia}/receipt lines.itemId control positivo",
            HttpStatus.OK,
            http.postJson(
                "$PURCHASES/$foreignLineTarget/receipt",
                """{"lines":[{"itemId":"${lineOf(foreignLineTarget)}","quantity":1}]}""",
                b.accessToken,
            ),
        )
        // `ownerId` y `locationId` **solo se resuelven si la linea puede entrar en
        // el inventario**, y para eso tiene que llevar articulo: una linea de
        // texto suelto se compra y ahi acaba. La primera version de estas dos
        // sondas usaba lineas sueltas y pasaba sin haber ejecutado la
        // comprobacion, que es la forma exacta de escribir una prueba que no puede
        // fallar. Se niegan con `400`: lo que falta no es el recurso de la ruta
        // sino un campo del cuerpo, igual que las dos pertenencias de un prestamo.
        bothWays(
            "POST /purchasing/purchases/{propia}/receipt lines.ownerId",
            own = b.memberId,
            foreign = a.memberId,
            ok = OK,
            expectedForForeign = HttpStatus.BAD_REQUEST,
        ) { id ->
            val purchase = freshStockablePurchaseB()
            http.postJson(
                "$PURCHASES/$purchase/receipt",
                """{"lines":[{"itemId":"${lineOf(purchase)}","ownerId":"$id"}]}""",
                b.accessToken,
            )
        }
        bothWays(
            "POST /purchasing/purchases/{propia}/receipt lines.locationId",
            own = locB,
            foreign = locA,
            ok = OK,
            expectedForForeign = HttpStatus.BAD_REQUEST,
        ) { id ->
            val purchase = freshStockablePurchaseB()
            http.postJson(
                "$PURCHASES/$purchase/receipt",
                """{"lines":[{"itemId":"${lineOf(purchase)}","locationId":"$id"}]}""",
                b.accessToken,
            )
        }

        // --- Mantenimiento: el documento del core en la ficha de la maquina, y
        // **el proveedor de otro modulo** en las tres operaciones que lo aceptan.
        // Esa ultima es la que llega por `MasterDataDirectory`: el puerto pide por
        // la clave del modulo dueno y resuelve dentro del hogar actual, asi que un
        // identificador de A tiene que salir de ahi como inexistente.
        bothWays(
            "PATCH /maintenance/machines manualDocumentId",
            own = docB,
            foreign = docA,
            ok = OK,
            expectedForForeign = HttpStatus.BAD_REQUEST,
        ) { id ->
            http.patchJson("$MACHINES/$durB", """{"manualDocumentId":"$id"}""", b.accessToken)
        }
        // Las cuatro de asset y de proveedor se niegan con `409` y con codigo
        // propio: «Mantenimiento solo vigila cosas duraderas que sigan en casa» y
        // «no se puede leer ese servicio tecnico». La segunda es otra vez la
        // degradacion del puerto de dato maestro, escrita para que no distinga
        // entre «no existe» y «Proveedores esta apagado».
        bothWays(
            "POST /maintenance/plans assetId",
            own = freshDurableB(),
            foreign = durA,
            ok = CREATED,
            expectedForForeign = HttpStatus.CONFLICT,
        ) { id ->
            http.postJson(
                PLANS,
                """{"assetId":"$id","name":"Sonda${short()}","intervalMonths":12,
                    "nextDueOn":"${today().plusDays(30)}"}""",
                b.accessToken,
            )
        }
        bothWays(
            "POST /maintenance/plans supplierId",
            own = supB,
            foreign = supA,
            ok = CREATED,
            expectedForForeign = HttpStatus.CONFLICT,
        ) { id ->
            http.postJson(
                PLANS,
                """{"assetId":"${freshDurableB()}","name":"Sonda${short()}","intervalMonths":12,
                    "nextDueOn":"${today().plusDays(30)}","supplierId":"$id"}""",
                b.accessToken,
            )
        }
        bothWays(
            "PATCH /maintenance/plans supplierId",
            own = supB2,
            foreign = supA,
            ok = OK,
            expectedForForeign = HttpStatus.CONFLICT,
        ) { id ->
            http.patchJson("$PLANS/$planB", """{"supplierId":"$id"}""", b.accessToken)
        }
        bothWays(
            "POST /maintenance/interventions assetId",
            own = freshDurableB(),
            foreign = durA,
            ok = CREATED,
            expectedForForeign = HttpStatus.CONFLICT,
        ) { id ->
            http.postJson(
                INTERVENTIONS,
                """{"assetId":"$id","kind":"CORRECTIVE","performedOn":"${today()}",
                    "summary":"Sonda${short()}"}""",
                b.accessToken,
            )
        }
        // El plan de una intervencion preventiva: es la referencia cuyo fallo
        // **avanzaria la fecha prevista del plan de otro hogar**, que es de las
        // pocas escrituras cruzadas que nadie notaria hasta dos anos despues.
        bothWays("POST /maintenance/interventions planId", own = planB, foreign = planA, ok = CREATED) { id ->
            http.postJson(
                INTERVENTIONS,
                """{"assetId":"$durB","planId":"$id","kind":"PREVENTIVE",
                    "performedOn":"${today()}","summary":"Sonda${short()}"}""",
                b.accessToken,
            )
        }
        bothWays(
            "POST /maintenance/interventions supplierId",
            own = supB,
            foreign = supA,
            ok = CREATED,
            expectedForForeign = HttpStatus.CONFLICT,
        ) { id ->
            http.postJson(
                INTERVENTIONS,
                """{"assetId":"${freshDurableB()}","kind":"CORRECTIVE","performedOn":"${today()}",
                    "summary":"Sonda${short()}","supplierId":"$id"}""",
                b.accessToken,
            )
        }

        endOfSection("ref")
    }

    // ------------------------------------------------------------------
    // 3. Por los filtros de los listados
    // ------------------------------------------------------------------

    @Test
    @DisplayName("los veintiun listados no devuelven nada de otro hogar, ni filtrando por sus identificadores")
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
        // El del Hito 4. Se mide por vacio y no por ausencia: B no tiene ningun
        // asset con la etiqueta de A --no puede-- asi que cualquier fila aqui es
        // una fuga.
        expectEmpty("$section GET /assets?tagId de A", http.getJson("$ASSETS?tagId=$tagA", b.accessToken))
        expectAbsent("$section GET /tags", tagA, http.getJson(TAGS, b.accessToken))
        expectAbsent(
            "$section GET /tags?includeRetired=true",
            tagA,
            http.getJson("$TAGS?includeRetired=true", b.accessToken),
        )
        // El `q` es el filtro que mas tienta como oraculo de este recurso: una
        // etiqueta es texto que alguien escribio, asi que adivinarla es facil y
        // que aparezca diria que el vecino la tiene.
        expectAbsent(
            "$section GET /tags?q con el nombre de una de A",
            tagA,
            http.getJson("$TAGS?q=EtiquetaDeA", b.accessToken),
        )
        expectAbsent(
            "$section GET /assets?status=DECOMMISSIONED",
            stockA,
            http.getJson("$ASSETS?status=DECOMMISSIONED", b.accessToken),
        )

        // Los dos listados del Hito 3. El de ficheros no tiene filtro por
        // identificador --solo por adjuntado y por tipo-- asi que lo que se mide
        // es que ninguno de los dos deje ver el fichero de A.
        expectAbsent("$section GET /files", fileA, http.getJson(FILES, b.accessToken))
        expectAbsent("$section GET /files?attached=false", fileA, http.getJson("$FILES?attached=false", b.accessToken))
        expectAbsent("$section GET /files?attached=true", fileA, http.getJson("$FILES?attached=true", b.accessToken))
        expectAbsent(
            "$section GET /files?type=image/jpeg",
            fileA,
            http.getJson("$FILES?type=image/jpeg", b.accessToken),
        )

        expectAbsent("$section GET /documents", docA, http.getJson(DOCUMENTS, b.accessToken))
        expectEmpty("$section GET /documents?assetId de A", http.getJson("$DOCUMENTS?assetId=$durA", b.accessToken))
        expectEmpty("$section GET /documents?articleId de A", http.getJson("$DOCUMENTS?articleId=$artA", b.accessToken))

        // Y el uso de almacenamiento es del hogar que pregunta: si contase lo de
        // otro, seria un oraculo sobre cuanto tiene guardado el de al lado.
        expectPresent("""$section GET /storage cuenta solo lo de B""", "usedBytes", http.getJson(STORAGE, b.accessToken))

        // El listado del Hito 4. `assetId` es el filtro que mas tienta como
        // oraculo: preguntar por el asset de A diria si esta prestado.
        expectAbsent("$section GET /loans", loanA, http.getJson(LOANS, b.accessToken))
        expectAbsent("$section GET /loans?open=true", loanA, http.getJson("$LOANS?open=true", b.accessToken))
        expectEmpty("$section GET /loans?assetId de A", http.getJson("$LOANS?assetId=$lentAssetA", b.accessToken))
        // Por ausencia de la fila de A y no exigiendo vacio, por el mismo motivo
        // que el filtro por codigo de barras de mas arriba: B tiene prestamos
        // ACTIVE propios --los abren los controles positivos de la seccion de
        // referencias-- y exigir vacio confundiria eso con una fuga.
        expectAbsent(
            "$section GET /loans?status=ACTIVE",
            loanA,
            http.getJson("$LOANS?status=ACTIVE", b.accessToken),
        )

        // ------------------------------------------------------------------
        // Los catorce listados de la Fase 2
        // ------------------------------------------------------------------

        // Plataforma. La bandeja de avisos es el listado de la fase con el
        // contenido mas delicado: un aviso lleva escrito en el titulo **que le
        // pasa al hogar** --que se le caduca, que se le acaba, que le toca una
        // revision-- asi que una fuga aqui no filtra un identificador sino una
        // frase legible.
        expectAbsent("$section GET /notices", noticeA, http.getJson(NOTICES, b.accessToken))
        expectAbsent(
            "$section GET /notices?unreadOnly=true",
            noticeA,
            http.getJson("$NOTICES?unreadOnly=true", b.accessToken),
        )

        // Proveedores, con el filtro de texto libre --el nombre de un fontanero es
        // adivinable-- y el de categoria.
        expectAbsent("$section GET /suppliers", supA, http.getJson(SUPPLIERS, b.accessToken))
        expectAbsent(
            "$section GET /suppliers?includeRetired=true",
            supA,
            http.getJson("$SUPPLIERS?includeRetired=true", b.accessToken),
        )
        expectAbsent(
            "$section GET /suppliers?q con el nombre del de A",
            supA,
            http.getJson("$SUPPLIERS?q=FontaneroDeA", b.accessToken),
        )
        expectAbsent(
            "$section GET /suppliers?serviceCategory=PLUMBING",
            supA,
            http.getJson("$SUPPLIERS?serviceCategory=PLUMBING", b.accessToken),
        )

        // Warehouse: cuatro listados y cuatro filtros por identificador, tres de
        // ellos **del core**. `locationId` es el que mas tienta: preguntar por la
        // cocina de A diria que hay en ella.
        expectAbsent("$section GET /warehouse/stock", stockA, http.getJson(WAREHOUSE_STOCK, b.accessToken))
        expectEmpty(
            "$section GET /warehouse/stock?locationId de A",
            http.getJson("$WAREHOUSE_STOCK?locationId=$locA", b.accessToken),
        )
        expectAbsent("$section GET /warehouse/movements", stockA, http.getJson(MOVEMENTS, b.accessToken))
        expectEmpty(
            "$section GET /warehouse/movements?assetId de A",
            http.getJson("$MOVEMENTS?assetId=$stockA", b.accessToken),
        )
        expectEmpty(
            "$section GET /warehouse/movements?articleId de A",
            http.getJson("$MOVEMENTS?articleId=$artA", b.accessToken),
        )
        expectAbsent("$section GET /warehouse/lots", lotA, http.getJson(LOTS, b.accessToken))
        expectEmpty("$section GET /warehouse/lots?assetId de A", http.getJson("$LOTS?assetId=$stockA", b.accessToken))

        // Compras. El listado de proveedores es especial y por eso va aqui y no
        // con los de Proveedores: **no lo sirve ese modulo** sino el puerto de
        // dato maestro de plataforma, que es un camino distinto al repositorio y
        // podria filtrar por su cuenta.
        expectAbsent("$section GET /purchasing/list", itemA, http.getJson(SHOPPING_LIST, b.accessToken))
        expectAbsent(
            "$section GET /purchasing/list?status=NEEDED",
            itemA,
            http.getJson("$SHOPPING_LIST?status=NEEDED", b.accessToken),
        )
        expectAbsent("$section GET /purchasing/purchases", purchaseA, http.getJson(PURCHASES, b.accessToken))
        expectAbsent(
            "$section GET /purchasing/purchases?status=OPEN",
            purchaseA,
            http.getJson("$PURCHASES?status=OPEN", b.accessToken),
        )
        expectAbsent(
            "$section GET /purchasing/suppliers por el puerto de dato maestro",
            supA,
            http.getJson(PURCHASING_SUPPLIERS, b.accessToken),
        )
        expectAbsent(
            "$section GET /purchasing/suppliers?q con el nombre del de A",
            supA,
            http.getJson("$PURCHASING_SUPPLIERS?q=FontaneroDeA", b.accessToken),
        )

        // Mantenimiento. El de maquinas se sirve de los `DURABLE` del core, asi
        // que lo que no puede aparecer es el asset de A.
        expectAbsent("$section GET /maintenance/machines", durA, http.getJson(MACHINES, b.accessToken))
        expectAbsent(
            "$section GET /maintenance/machines?q con el nombre del de A",
            durA,
            http.getJson("$MACHINES?q=TaladroDeA", b.accessToken),
        )
        expectAbsent("$section GET /maintenance/plans", planA, http.getJson(PLANS, b.accessToken))
        expectEmpty("$section GET /maintenance/plans?assetId de A", http.getJson("$PLANS?assetId=$durA", b.accessToken))
        expectAbsent(
            "$section GET /maintenance/plans?includeCancelled=true",
            planA,
            http.getJson("$PLANS?includeCancelled=true", b.accessToken),
        )
        expectAbsent(
            "$section GET /maintenance/interventions",
            interventionA,
            http.getJson(INTERVENTIONS, b.accessToken),
        )
        expectEmpty(
            "$section GET /maintenance/interventions?assetId de A",
            http.getJson("$INTERVENTIONS?assetId=$durA", b.accessToken),
        )
        expectEmpty(
            "$section GET /maintenance/interventions?planId de A",
            http.getJson("$INTERVENTIONS?planId=$planA", b.accessToken),
        )
        expectAbsent(
            "$section GET /maintenance/suppliers por el puerto de dato maestro",
            supA,
            http.getJson(MAINTENANCE_SUPPLIERS, b.accessToken),
        )

        endOfSection(section)
    }

    // ------------------------------------------------------------------
    // 3 bis. El estado de activacion, que no tiene identificador que atacar
    // ------------------------------------------------------------------

    @Test
    @DisplayName("encender y apagar un modulo es del hogar que lo hace, y no se ve desde el de al lado")
    fun `la activacion no cruza de hogar`() {
        val section = "activacion"

        // Las tres operaciones de la activacion son las unicas del catalogo que no
        // aceptan ningun identificador del hogar de al lado: la clave de un modulo
        // es global. Lo que si pueden hacer mal es escribir en el hogar
        // equivocado, y eso **solo se mide con asimetria**: B apaga uno de los
        // cuatro y A tiene que seguir viendolo encendido.
        expectStatus(
            "$section B apaga Mantenimiento",
            HttpStatus.OK,
            http.deleteJson("$MODULES_PATH/MAINTENANCE/activation", b.accessToken),
        )

        val forB = http.getJson(MODULES_PATH, b.accessToken)
        expectPresent("$section B ve Mantenimiento apagado", "\"status\":\"INACTIVE\"", forB)
        // Y la otra mitad, que es la que de verdad se mide: el catalogo de A no se
        // ha enterado. Sin esta, apagarlo para todo el mundo pasaria por bueno.
        expectPresent("$section A sigue viendo los cuatro encendidos", "\"status\":\"ACTIVE\"", http.getJson(MODULES_PATH, a.accessToken))
        expectAbsent("$section el catalogo de A no trae ningun INACTIVE", "INACTIVE", http.getJson(MODULES_PATH, a.accessToken))
        // Y las rutas de A siguen respondiendo, que es el gate mirado desde el otro
        // lado: el `403 MODULE_INACTIVE` de B no puede alcanzar a A.
        expectStatus("$section las rutas de A siguen abiertas", HttpStatus.OK, http.getJson(PLANS, a.accessToken))
        expectStatus(
            "$section y las de B responden 403 con el suyo apagado",
            HttpStatus.FORBIDDEN,
            http.getJson(PLANS, b.accessToken),
        )

        // Se devuelve al estado en el que estaba: las demas secciones lo necesitan
        // encendido, y reactivar vuelve a sembrar --que es idempotente a proposito.
        expectStatus(
            "$section B lo vuelve a encender",
            HttpStatus.OK,
            http.postJson("$MODULES_PATH/MAINTENANCE/activation", "", b.accessToken),
        )
        expectStatus("$section y sus rutas vuelven", HttpStatus.OK, http.getJson(PLANS, b.accessToken))

        // Marcar todo leido es la tercera sin identificador, y se mide igual: B lo
        // hace y el aviso de A tiene que seguir sin leer. El retrato del cierre lo
        // vuelve a comprobar sobre el cuerpo entero.
        expectStatus(
            "$section B marca todo leido",
            HttpStatus.NO_CONTENT,
            http.postJson("$NOTICES/read", "", b.accessToken),
        )
        expectAbsent(
            "$section el aviso de A sigue sin leer",
            "\"readAt\":\"",
            http.getJson("$NOTICES?unreadOnly=true", a.accessToken),
        )

        endOfSection(section)
    }

    // ------------------------------------------------------------------
    // 3 ter. La baja del hogar y el cierre de cuenta, que tampoco tienen
    //        identificador que atacar
    // ------------------------------------------------------------------

    @Test
    @DisplayName("pedir la baja es del hogar que la pide, y el de al lado no se entera")
    fun `la baja no cruza de hogar`() {
        val section = "baja"

        // Las cuatro operaciones de la ADR-012 actuan sobre «lo mio»: el hogar y
        // la identidad salen del token y no hay nada que nombrar. Lo que si
        // pueden hacer mal --y seria lo mas caro del catalogo-- es marcar el hogar
        // equivocado, y eso **solo se mide con asimetria**.

        // Primero la lectura: cada uno ve el suyo, y solo el suyo.
        val houseB = http.getJson(HOUSEHOLD, b.accessToken)
        expectPresent("$section B ve su hogar", "\"id\":\"${b.householdId}\"", houseB)
        expectAbsent("$section y no el de A", a.householdId, houseB)

        // B pide la baja de su hogar.
        expectStatus(
            "$section B pide la baja",
            HttpStatus.OK,
            http.postJson("$HOUSEHOLD/closure", "", b.accessToken),
        )
        expectPresent(
            "$section B la ve pedida",
            "\"closure\":{",
            http.getJson(HOUSEHOLD, b.accessToken),
        )

        // Y la mitad que de verdad se mide: A no tiene ninguna baja. Sin esta,
        // marcar el hogar equivocado pasaria por bueno.
        expectPresent(
            "$section A sigue sin ninguna baja",
            "\"closure\":null",
            http.getJson(HOUSEHOLD, a.accessToken),
        )
        // Ni un aviso de baja en la bandeja de A, que es el otro rastro que deja.
        expectAbsent(
            "$section ni un aviso de baja en A",
            "HOUSEHOLD_CLOSURE_REQUESTED",
            http.getJson(NOTICES, a.accessToken),
        )

        // Cancelarla tampoco cruza: se devuelve el estado de B al que estaba, y A
        // sigue sin nada que cancelar --responde 409 y no 200, que es lo que
        // demostraria que la de B le habia llegado.
        expectStatus(
            "$section B la cancela",
            HttpStatus.OK,
            http.deleteJson("$HOUSEHOLD/closure", b.accessToken),
        )
        expectStatus(
            "$section A no tiene ninguna que cancelar",
            HttpStatus.CONFLICT,
            http.deleteJson("$HOUSEHOLD/closure", a.accessToken),
        )

        // Y cerrar la cuenta. Los dos hogares tienen un solo administrador activo,
        // asi que la regla del ultimo administrador lo impide en los dos --que es
        // justo lo que hace segura esta comprobacion dentro del barrido: se
        // recorre el camino entero, autorizacion incluida, sin dejar a ninguno de
        // los dos hogares sin quien lo gobierne para las secciones que vienen
        // detras.
        expectStatus(
            "$section B no puede cerrar su cuenta siendo el unico que administra",
            HttpStatus.CONFLICT,
            http.deleteJson("$USERS/me", b.accessToken),
        )
        expectAbsent(
            "$section y las personas de A siguen todas activas",
            "\"deactivatedAt\":\"",
            http.getJson(USERS, a.accessToken),
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
        // La etiqueta tiene un matiz propio: su indice unico **no es parcial por
        // retirada**, asi que el 409 tiene mas superficie que el de una categoria
        // --tambien choca contra las retiradas-- y por tanto mas por donde
        // delatar. `201` y no `200`: B no tiene ninguna con ese nombre, ni viva ni
        // retirada, asi que la crea de verdad.
        expectStatus(
            "$section POST /tags con el nombre de una de A",
            HttpStatus.CREATED,
            http.postJson(TAGS, """{"name":"EtiquetaDeA"}""", b.accessToken),
        )
        expectStatus(
            "$section PATCH /tags renombrando al nombre de otra de A",
            HttpStatus.OK,
            http.patchJson("$TAGS/$tagB2", """{"name":"SegundaEtiquetaDeA"}""", b.accessToken),
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

        // Los tres indices unicos que la Fase 2 anadio. Dos llevan `household_id`
        // dentro y el tercero **no**: el de lotes es `(asset_id, lot_code,
        // expires_on)`, y se sostiene porque el asset ya es del hogar. Esa es
        // justamente la forma que hay que medir en lugar de leer, porque el dia
        // que un indice de modulo cuelgue de algo que no lo sea, el 409 pasaria a
        // ser un oraculo sin que ninguna otra prueba lo notara.
        expectStatus(
            "$section POST /suppliers con el nombre del proveedor de A",
            HttpStatus.CREATED,
            http.postJson(
                SUPPLIERS,
                """{"name":"FontaneroDeA","serviceCategory":"PLUMBING","phone":"600000000"}""",
                b.accessToken,
            ),
        )
        expectStatus(
            "$section POST /warehouse/lots con el codigo y la fecha del lote de A",
            HttpStatus.CREATED,
            http.postJson(
                LOTS,
                """{"assetId":"$stockB","lotCode":"LOTE-DE-A",
                    "expiresOn":"${today().plusDays(1)}","quantity":1}""",
                b.accessToken,
            ),
        )
        expectStatus(
            "$section POST /maintenance/plans con el nombre del plan de A",
            HttpStatus.CREATED,
            http.postJson(
                PLANS,
                """{"assetId":"${freshDurableB()}","name":"RevisionDeA","intervalMonths":12,
                    "nextDueOn":"${today().plusDays(30)}"}""",
                b.accessToken,
            ),
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

            ========== BARRIDO DE AISLAMIENTO DE LAS 106 OPERACIONES DEL CONTRATO ==========
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
     * Las **cuarenta y seis** operaciones con identificador en la ruta --veintiuna
     * de la Fase 1, veintidos de la Fase 2, las dos de etiquetas del cierre de
     * huecos y la reactivacion de un miembro--, cada una como una llamada que
     * solo espera el identificador. Es lo que permite ejecutarlas dos veces --con
     * el de A y con uno inventado-- sin repetir el cuerpo.
     */
    private fun operationsWithPathId(): List<Pair<String, (String) -> ResponseEntity<String>>> = listOf(
        "PATCH /categories/{id}" to { id ->
            http.patchJson("$CATEGORIES/$id", """{"name":"Sonda${short()}"}""", b.accessToken)
        },
        "DELETE /categories/{id}" to { id -> http.deleteJson("$CATEGORIES/$id", b.accessToken) },
        // Las dos del Hito 4 del cierre de huecos. La de retirada es la que mas
        // dano haria: retirar la etiqueta de otro hogar la quitaria de todo lo
        // que la lleve alli, sin devolver ni una fila y por tanto sin que ninguna
        // otra comprobacion lo notara.
        "PATCH /tags/{id}" to { id ->
            http.patchJson("$TAGS/$id", """{"name":"Sonda${short()}"}""", b.accessToken)
        },
        "DELETE /tags/{id}" to { id -> http.deleteJson("$TAGS/$id", b.accessToken) },
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
        // Las cuatro del Hito 3. La de contenido es la que mas importa: es la
        // unica que devuelve BYTES, asi que un fallo aqui no filtra un nombre
        // sino la factura entera.
        "GET /files/{id}" to { id -> http.getJson("$FILES/$id", b.accessToken) },
        "GET /files/{id}/content" to { id -> http.getJson("$FILES/$id/content", b.accessToken) },
        "DELETE /files/{id}" to { id -> http.deleteJson("$FILES/$id", b.accessToken) },
        "DELETE /documents/{id}" to { id -> http.deleteJson("$DOCUMENTS/$id", b.accessToken) },
        // Las dos del Hito 4 con identificador en la ruta. Ojo con la de
        // devolucion: es la unica operacion del barrido que ademas de leer
        // **cierra** algo, asi que un fallo aqui no filtraria un dato ajeno sino
        // que daria por devuelto el prestamo de otro hogar.
        "GET /loans/{id}" to { id -> http.getJson("$LOANS/$id", b.accessToken) },
        "POST /loans/{id}/return" to { id -> http.postJson("$LOANS/$id/return", "", b.accessToken) },

        // Las dos de la gestion de miembros. Un fallo aqui no filtraria un
        // dato: echaria de su casa a la persona de otro hogar --revocandole las
        // sesiones--, o le meteria de vuelta a quien aquel hogar ya despidio.
        "DELETE /users/{id}" to { id -> http.deleteJson("$USERS/$id", b.accessToken) },
        "POST /users/{id}/activation" to { id -> http.postJson("$USERS/$id/activation", "", b.accessToken) },

        // --- Y las veintidos de la Fase 2 con identificador en la ruta.
        //
        // Una de plataforma: marcar leido un aviso ajeno diria que existe, y de
        // que clase es, sin devolver una sola fila.
        "POST /notices/{id}/read" to { id -> http.postJson("$NOTICES/$id/read", "", b.accessToken) },

        // Proveedores. La de enlaces va aqui con cuerpo **propio**: lo que se
        // ataca es el identificador de la ruta y no la referencia --esa tiene su
        // seccion--, y sin cuerpo valido el 404 podria venir de la forma.
        "GET /suppliers/{id}" to { id -> http.getJson("$SUPPLIERS/$id", b.accessToken) },
        "PATCH /suppliers/{id}" to { id ->
            http.patchJson("$SUPPLIERS/$id", """{"notes":"sonda"}""", b.accessToken)
        },
        "DELETE /suppliers/{id}" to { id -> http.deleteJson("$SUPPLIERS/$id", b.accessToken) },
        "POST /suppliers/{id}/links" to { id ->
            http.postJson("$SUPPLIERS/$id/links", """{"assetId":"$durB"}""", b.accessToken)
        },

        // Warehouse. Las dos de existencia van sobre el **asset del core**, que es
        // la clave de este modulo: no lleva un segundo contador, lo lee del core.
        // El consumo es ademas de las pocas que **escriben en el core**, asi que un
        // fallo aqui no filtraria un dato sino que moveria la despensa del vecino.
        "GET /warehouse/stock/{assetId}" to { id -> http.getJson("$WAREHOUSE_STOCK/$id", b.accessToken) },
        "POST /warehouse/stock/{assetId}/consumptions" to { id ->
            http.postJson("$WAREHOUSE_STOCK/$id/consumptions", """{"quantity":1}""", b.accessToken)
        },
        "PATCH /warehouse/lots/{lotId}" to { id ->
            http.patchJson("$LOTS/$id", """{"quantity":1}""", b.accessToken)
        },
        "DELETE /warehouse/lots/{lotId}" to { id -> http.deleteJson("$LOTS/$id", b.accessToken) },
        // Estas dos llevan en la ruta un identificador **del core** y no del
        // modulo: la ficha de almacen de un articulo y la de un sitio. Es la forma
        // que mas facil es dejar sin resolver, porque la fila que se escribe es
        // propia y solo el identificador viene de fuera.
        "PATCH /warehouse/articles/{articleId}" to { id ->
            http.patchJson("$WAREHOUSE_ARTICLES/$id", """{"minimumQuantity":5}""", b.accessToken)
        },
        "PATCH /warehouse/locations/{locationId}" to { id ->
            http.patchJson("$WAREHOUSE_LOCATIONS/$id", """{"expiryLeadDays":9}""", b.accessToken)
        },

        // Compras.
        "PATCH /purchasing/list/{id}" to { id ->
            http.patchJson("$SHOPPING_LIST/$id", """{"quantity":9}""", b.accessToken)
        },
        "DELETE /purchasing/list/{id}" to { id -> http.deleteJson("$SHOPPING_LIST/$id", b.accessToken) },
        "GET /purchasing/purchases/{id}" to { id -> http.getJson("$PURCHASES/$id", b.accessToken) },
        "DELETE /purchasing/purchases/{id}" to { id -> http.deleteJson("$PURCHASES/$id", b.accessToken) },
        // Recibir es la otra que escribe en el core --da entrada de consumibles--,
        // y ademas cierra la compra: un fallo aqui daria por recibida la compra de
        // otro hogar y le crearia existencias.
        "POST /purchasing/purchases/{id}/receipt" to { id ->
            http.postJson("$PURCHASES/$id/receipt", """{"lines":[]}""", b.accessToken)
        },

        // Mantenimiento. Las dos de maquina van sobre el asset del core, igual que
        // las de existencia de Warehouse.
        "GET /maintenance/machines/{assetId}" to { id -> http.getJson("$MACHINES/$id", b.accessToken) },
        "PATCH /maintenance/machines/{assetId}" to { id ->
            http.patchJson("$MACHINES/$id", """{"notes":"sonda"}""", b.accessToken)
        },
        "GET /maintenance/plans/{id}" to { id -> http.getJson("$PLANS/$id", b.accessToken) },
        "PATCH /maintenance/plans/{id}" to { id ->
            http.patchJson("$PLANS/$id", """{"notes":"sonda"}""", b.accessToken)
        },
        "DELETE /maintenance/plans/{id}" to { id -> http.deleteJson("$PLANS/$id", b.accessToken) },
    )

    /**
     * Con que codigo se niega cada operacion, **segun lo que declara el
     * contrato** y no segun lo que seria bonito.
     *
     * Casi todas responden `404`, y ese es el caso por omision. Tres de Warehouse
     * no lo declaran siquiera: para ellas «esa existencia no la sigo» es un `409`
     * con codigo propio, y **eso es correcto y ademas es mejor** --no distingue
     * «no existe» de «existe y no es consumible viva», asi que dice todavia menos
     * que un `404`--. Lo que el barrido exige no es un numero concreto sino las
     * dos cosas que de verdad importan: que **se niegue**, y que se niegue
     * **igual** ante un identificador ajeno que ante uno inventado.
     *
     * Fijarlo aqui en vez de aflojar la comprobacion es lo que hace que un cambio
     * de codigo tenga que pasar por esta tabla: si manana la de consumos empezara
     * a responder `404` para lo ajeno y `409` para lo inexistente, la seccion del
     * oraculo lo cazaria; si respondiera `404` para las dos, esta lista lo
     * obligaria a decidirlo aqui y de paso a tocar el contrato.
     */
    private fun refusalOf(operation: String): HttpStatus = when (operation) {
        "POST /warehouse/stock/{assetId}/consumptions" -> HttpStatus.CONFLICT
        else -> HttpStatus.NOT_FOUND
    }

    /**
     * El recurso de A que le toca a cada operacion, por su ruta.
     *
     * **Las de modulo se resuelven primero**, y el orden no es cosmetico: dos de
     * Warehouse llevan en la ruta los mismos segmentos que el core, /articles y
     * /locations. Con el orden al reves atacarian con el articulo y el sitio de A
     * --que resulta ser lo correcto-- pero por casualidad y no por decision, y el
     * dia que un modulo publique algo bajo /loans o /files la casualidad dejaria
     * de salir bien sin que nadie lo notara.
     */
    private fun foreignIdFor(operation: String): String = when {
        operation.contains("/notices") -> noticeA
        operation.contains("/suppliers") -> supA
        operation.contains("/warehouse/stock") -> stockA
        operation.contains("/warehouse/lots") -> lotA
        operation.contains("/warehouse/articles") -> artA
        operation.contains("/warehouse/locations") -> locA
        operation.contains("/purchasing/list") -> itemA
        operation.contains("/purchasing/purchases") -> purchaseA
        operation.contains("/maintenance/machines") -> durA
        operation.contains("/maintenance/plans") -> planA
        operation.contains("/categories") -> catA
        operation.contains("/tags") -> tagA
        operation.contains("/articles") -> artA
        operation.contains("/locations") -> locA
        operation.contains("/merge") -> stockA
        operation.contains("/files") -> fileA
        operation.contains("/documents") -> docA
        operation.contains("/loans") -> loanA
        // La reactivacion ataca al miembro APAGADO y la baja al encendido: al
        // reves, ninguna de las dos podria cambiar nada aunque cruzara.
        operation.contains("/activation") -> exMemberA
        operation.contains("/users") -> memberA
        else -> durA
    }

    /**
     * El par que da sentido a la comprobacion: primero con el identificador de A
     * --tiene que ser 404-- y despues **el mismo cuerpo** con uno propio, que
     * tiene que funcionar.
     *
     * Sin la segunda mitad, un cuerpo mal escrito daria 404 por su cuenta y la
     * prueba pasaria sin haber comprobado el aislamiento.
     *
     * Hasta el Hito 3, `photoFileId` era la unica referencia **sin control
     * positivo posible**: la tabla `files` no podia tener filas, asi que
     * cualquier identificador de fichero daba 404 y eso era lo correcto. Ahora
     * los dos hogares tienen ficheros de verdad, y el par vuelve a medir lo que
     * dice medir: el de A da 404 y el propio de B funciona.
     *
     * `expectedForForeign` existe por las dos pertenencias de un prestamo, que
     * el contrato trata como **error de forma** y no como recurso ausente: lo
     * que falta no es el recurso de la ruta sino un campo del cuerpo. Para el
     * aislamiento da igual cual de los dos codigos salga --lo que se mide es que
     * no se acepte-- pero fijarlo en la llamada evita que un cambio de codigo
     * pase por bueno sin que nadie lo decida.
     */
    private fun bothWays(
        name: String,
        own: String?,
        foreign: String,
        ok: Set<HttpStatus>,
        expectedForForeign: HttpStatus = HttpStatus.NOT_FOUND,
        send: (String) -> ResponseEntity<String>,
    ) {
        expectStatus("ref $name con identificador de A", expectedForForeign, send(foreign))

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

    /**
     * Una linea suelta de B, **sin articulo**: con el, el indice de una sola linea
     * viva por articulo haria que la segunda sonda chocase con la primera y el
     * control positivo fallaria por una razon que no es la que se mide.
     */
    private fun freshShoppingItemB() = b.addShoppingItem("""{"name":"Sonda${short()}","quantity":1}""")

    /** Una compra abierta de B con una linea dentro, que es el minimo que admite. */
    private fun freshPurchaseB() = b.createPurchase(freshShoppingItemB(), supB)

    /**
     * Lo mismo, pero con **articulo** en la linea: es la unica forma en que
     * recibir llega a dar entrada de consumible, y por tanto la unica en que
     * `ownerId` y `locationId` se llegan a resolver.
     */
    private fun freshStockablePurchaseB() = b.createPurchase(
        b.addShoppingItem("""{"articleId":"${freshArticleB()}","quantity":1}"""),
        supB,
    )

    /** La linea que lleva dentro una compra, leida de la propia compra. */
    private fun lineOf(purchaseId: String): String {
        val body = http.getJson("$PURCHASES/$purchaseId", b.accessToken).body.orEmpty()
        val lines = body.substringAfter(""""lines":[""", "")
        return checkNotNull(Regex("""\"id\":\"([0-9a-f-]{36})\"""").find(lines)?.groupValues?.get(1)) {
            "La compra $purchaseId no traia ninguna linea: $body"
        }
    }

    private fun freshDurableB() =
        b.createAsset("""{"name":"Sonda${short()}","type":"DURABLE","categoryId":"$catB"}""")

    /** Un identificador de fichero cualquiera: hoy ninguno existe, y esa es la regla. */
    private fun short() = UUID.randomUUID().toString().take(8)

    /** Una imagen de verdad subida por este hogar. Cada llamada sube una distinta. */
    private fun TestHousehold.uploadImage(): String =
        http.uploadFile(accessToken, imageBytes("png"), "sonda-${short()}.png", "image/png").body!!.extract("id")

    private fun TestHousehold.attachDocument(assetId: String, fileId: String): String = http.postJson(
        DOCUMENTS,
        """{"assetId":"$assetId","type":"INVOICE","fileId":"$fileId"}""",
        accessToken,
    ).body!!.extract("id")

    private fun TestHousehold.createCategory(body: String) = created(CATEGORIES, body)

    private fun TestHousehold.createArticle(body: String) = created(ARTICLES, body)

    /** `POST /tags` responde `201` al crear y `200` al revivir; aqui siempre crea. */
    private fun TestHousehold.createTag(name: String) = created(TAGS, """{"name":"$name"}""")

    private fun TestHousehold.tagAsset(assetId: String, tagId: String) {
        val response = http.patchJson("$ASSETS/$assetId", """{"tagIds":["$tagId"]}""", accessToken)
        check(response.statusCode == HttpStatus.OK) {
            "No se pudo etiquetar el asset: ${response.statusCode} ${response.body}"
        }
    }

    private fun TestHousehold.createLocation(body: String) = created(LOCATIONS, body)

    private fun TestHousehold.createAsset(body: String) = created(ASSETS, body)

    private fun TestHousehold.created(path: String, body: String): String {
        val response = http.postJson(path, body, accessToken)
        check(response.statusCode == HttpStatus.CREATED) {
            "No se pudo preparar el escenario con POST $path: ${response.statusCode} ${response.body}"
        }
        return response.body!!.extract("id")
    }

    /**
     * Invita a alguien, acepta por el y devuelve su `memberId`, que sale del
     * token de la sesion recien emitida: la API no lo devuelve suelto.
     */
    private fun TestHousehold.inviteMember(): String {
        val email = "miembro-${short()}@example.test"
        check(
            http.postJson(
                "/api/v1/invitations",
                """{"email":"$email","role":"HOUSEHOLD_MEMBER"}""",
                accessToken,
            ).statusCode == HttpStatus.CREATED,
        ) { "No se pudo invitar al miembro que el barrido necesita" }

        val token = DrpMailpit.instance.awaitMessageTo(email).token()
        val session = http.postJson(
            "/api/v1/invitations/accept",
            """{"token":"$token","name":"Miembro","password":"una frase larga mas"}""",
        ).body!!.extract("accessToken")

        val payload = String(java.util.Base64.getUrlDecoder().decode(session.split(".")[1]))
        return payload.extract("memberId")
    }

    /** Un prestamo abierto del hogar, de si mismo a si mismo. */
    private fun TestHousehold.startLoan(assetId: String): String {
        val response = http.postJson(
            LOANS,
            """{"assetId":"$assetId","lender":{"userId":"$memberId"},"borrower":{"userId":"$memberId"}}""",
            accessToken,
        )
        check(response.statusCode == HttpStatus.CREATED) {
            "No se pudo preparar el prestamo: ${response.statusCode} ${response.body}"
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

    // --- Los cuatro modulos de la Fase 2 -------------------------------------

    private fun TestHousehold.activateModule(key: String) {
        val response = http.postJson("$MODULES_PATH/$key/activation", "", accessToken)
        check(response.statusCode == HttpStatus.OK) {
            "No se pudo encender $key: ${response.statusCode} ${response.body}"
        }
    }

    /**
     * Un contacto de servicio. Lleva telefono porque el modulo exige **al menos
     * una via de contacto**: sin ella responde `409 SUPPLIER_CONTACT_REQUIRED`.
     */
    private fun TestHousehold.createSupplier(name: String, category: String): String =
        created(SUPPLIERS, """{"name":"$name","serviceCategory":"$category","phone":"600000000"}""")

    private fun TestHousehold.linkSupplier(supplierId: String, target: String): String =
        created("$SUPPLIERS/$supplierId/links", target)

    /**
     * Un lote que caduca **manana**, que es lo que hace que la pasada diaria
     * levante el aviso del que este barrido saca su `noticeA`.
     */
    private fun TestHousehold.createLot(assetId: String, code: String): String = created(
        LOTS,
        """{"assetId":"$assetId","lotCode":"$code","expiresOn":"${today().plusDays(1)}","quantity":10}""",
    )

    private fun TestHousehold.addShoppingItem(body: String): String = created(SHOPPING_LIST, body)

    private fun TestHousehold.createPurchase(itemId: String, supplierId: String): String =
        created(PURCHASES, """{"supplierId":"$supplierId","itemIds":["$itemId"]}""")

    private fun TestHousehold.createPlan(assetId: String, name: String): String = created(
        PLANS,
        """{"assetId":"$assetId","name":"$name","intervalMonths":12,
            "nextDueOn":"${today().plusDays(30)}"}""",
    )

    private fun TestHousehold.registerIntervention(assetId: String, summary: String): String = created(
        INTERVENTIONS,
        """{"assetId":"$assetId","kind":"CORRECTIVE","performedOn":"${today()}","summary":"$summary"}""",
    )

    /** El primer aviso del hogar, que la pasada diaria acaba de escribirle. */
    private fun TestHousehold.firstNotice(): String {
        val body = http.getJson(NOTICES, accessToken).body.orEmpty()
        val id = Regex("\"id\":\"([0-9a-f-]{36})\"").find(body)?.groupValues?.get(1)
        return checkNotNull(id) { "La pasada diaria no dejo ningun aviso al hogar: $body" }
    }

    private companion object {
        const val CATEGORIES = "/api/v1/categories"
        const val TAGS = "/api/v1/tags"
        const val ARTICLES = "/api/v1/articles"
        const val LOCATIONS = "/api/v1/locations"
        const val ASSETS = "/api/v1/assets"
        const val INTAKE = "/api/v1/assets/intake"
        const val FILES = "/api/v1/files"
        const val DOCUMENTS = "/api/v1/documents"
        const val STORAGE = "/api/v1/storage"
        const val LOANS = "/api/v1/loans"

        // Las rutas de la Fase 2: plataforma y los cuatro modulos.
        const val MODULES_PATH = "/api/v1/modules"
        const val HOUSEHOLD = "/api/v1/households/current"
        const val USERS = "/api/v1/users"
        const val NOTICES = "/api/v1/notices"
        const val SUPPLIERS = "/api/v1/suppliers"
        const val WAREHOUSE_STOCK = "/api/v1/warehouse/stock"
        const val MOVEMENTS = "/api/v1/warehouse/movements"
        const val LOTS = "/api/v1/warehouse/lots"
        const val WAREHOUSE_ARTICLES = "/api/v1/warehouse/articles"
        const val WAREHOUSE_LOCATIONS = "/api/v1/warehouse/locations"
        const val SHOPPING_LIST = "/api/v1/purchasing/list"
        const val PURCHASES = "/api/v1/purchasing/purchases"
        const val PURCHASING_SUPPLIERS = "/api/v1/purchasing/suppliers"
        const val MACHINES = "/api/v1/maintenance/machines"
        const val PLANS = "/api/v1/maintenance/plans"
        const val INTERVENTIONS = "/api/v1/maintenance/interventions"
        const val MAINTENANCE_SUPPLIERS = "/api/v1/maintenance/suppliers"

        /** El catalogo desplegado, en el orden en que la fase los construyo. */
        val MODULES = listOf("SUPPLIERS", "WAREHOUSE", "PURCHASING", "MAINTENANCE")

        val OK = setOf(HttpStatus.OK)
        val CREATED = setOf(HttpStatus.CREATED)

        /** La entrada responde `201` si crea la existencia y `200` si suma sobre una que ya habia. */
        val INTAKE_OK = setOf(HttpStatus.OK, HttpStatus.CREATED)
    }
}
