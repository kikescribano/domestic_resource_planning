package com.drp.module.warehouse

import com.drp.platform.schedule.DailySweep
import com.drp.test.DrpMailpit
import com.drp.test.SpringIntegrationTest
import com.drp.test.deleteJson
import com.drp.test.extract
import com.drp.test.extractRaw
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
import java.time.LocalDate

/**
 * El recorrido de Warehouse, por HTTP y contra PostgreSQL real.
 *
 * Lo que este modulo estrena y ninguno anterior podia comprobar:
 *
 *  1. **Un modulo que reacciona al core.** Los seis handlers escriben en el
 *     cuaderno a partir de lo que el core publica, y un hogar con el modulo
 *     **apagado** no ve ni una fila escrita — que es la segunda capa del gate,
 *     medida por fin sobre un modulo desplegado y no sobre el de prueba.
 *  2. **Una siembra que lee algo.** Encender sobre una despensa que ya existia
 *     asienta lo que hay, y **apagar y volver a encender no duplica ni un
 *     asiento**.
 *  3. **La frontera con el core.** Warehouse no lleva un segundo contador:
 *     registrar un consumo mueve el contador del core, y lo que aparece en el
 *     cuaderno lo escribe el handler del evento que eso provoca.
 *
 * **No estrena contexto de Spring**: mismo `@SpringBootTest` que las demas
 * pruebas de recorrido, sin propiedades propias ni `@TestConfiguration`. Cada
 * combinacion nueva reserva un pool entero que no se suelta mientras siga en
 * cache, y eso ya tumbo tres pruebas de otros hitos en la Fase 1. Este hito era
 * el que mas tentaba a anadir --seis handlers piden prueba-- y se resuelve con
 * cero contextos nuevos: los handlers se ejercitan **por la API de verdad**, que
 * es ademas como corren en produccion.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WarehouseJourneyTest : SpringIntegrationTest() {

    @Autowired private lateinit var http: TestRestTemplate

    /**
     * El recorrido diario **se invoca a mano**, que es el mismo metodo que
     * invoca el `@Scheduled`: el programador esta apagado en toda la suite
     * porque, encendido, la pasada correria dentro de cualquier contexto de
     * prueba y purgaria hogares a mitad de otra.
     */
    @Autowired private lateinit var sweep: DailySweep

    private val mailpit = DrpMailpit.instance

    // -----------------------------------------------------------------------
    // El gate, sobre una ruta que existe
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("apagado responde 403 MODULE_INACTIVE y encendido responde 200")
    fun `las dos mitades del gate`() {
        val home = http.registerHousehold()

        val closed = http.getJson("$WAREHOUSE/stock", home.accessToken)
        closed.statusCode.shouldBe(HttpStatus.FORBIDDEN)
        closed.body!!.shouldContain("MODULE_INACTIVE")

        // Y no solo el listado: el gate cuelga del prefijo, asi que alcanza a
        // todo lo que hay debajo.
        http.getJson("$WAREHOUSE/movements", home.accessToken).statusCode.shouldBe(HttpStatus.FORBIDDEN)
        http.getJson("$WAREHOUSE/lots", home.accessToken).statusCode.shouldBe(HttpStatus.FORBIDDEN)

        home.activate()

        val opened = http.getJson("$WAREHOUSE/stock", home.accessToken)
        opened.statusCode.shouldBe(HttpStatus.OK)
        opened.body!!.shouldNotContain("MODULE_INACTIVE")
    }

    // -----------------------------------------------------------------------
    // Lo que este hito estrena: el modulo apagado no escribe NADA
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("con el modulo apagado, los seis handlers no escriben ni una fila")
    fun `un hogar sin el modulo no ve nada escrito`() {
        val home = http.registerHousehold()

        // Toda la actividad del core que Warehouse escucha, con el modulo
        // apagado: dar de alta un articulo, un sitio, una existencia, moverla,
        // ajustarla y darla de baja.
        val pantry = home.location("Despensa")
        val garage = home.location("Garaje")
        val article = home.article("Arroz", "GRAM")
        val stock = home.intake(article, pantry, 900)
        home.move(stock, garage)
        home.adjust(stock, 700)
        http.deleteJson("/api/v1/assets/$stock", home.accessToken).statusCode.shouldBe(HttpStatus.NO_CONTENT)

        // Ahora se enciende. La siembra recorre lo que **hay ahora**, y lo que
        // hay ahora es una existencia dada de baja: no hay nada que sembrar.
        home.activate()

        // Cero movimientos. Si algun handler hubiera escrito con el modulo
        // apagado, aqui habria asientos de aquellos eventos.
        http.getJson("$WAREHOUSE/movements", home.accessToken).body!!.shouldContain("\"total\":0")
        http.getJson("$WAREHOUSE/stock", home.accessToken).body!!.shouldContain("\"total\":0")
    }

    @Test
    @DisplayName("y el hogar de al lado, con el modulo encendido, si los ve: no es que no lleguen")
    fun `el vecino con el modulo encendido si escribe`() {
        val off = http.registerHousehold()
        val on = http.registerHousehold()
        on.activate()

        // El mismo gesto en los dos hogares, a la vez y por la misma ruta.
        listOf(off, on).forEach { home ->
            val pantry = home.location("Despensa")
            val article = home.article("Lentejas", "GRAM")
            home.intake(article, pantry, 500)
        }

        // Es la comparacion la que dice algo: sin ella, cero movimientos podria
        // significar «el handler esta roto» en vez de «el gate funciona».
        http.getJson("$WAREHOUSE/movements", on.accessToken).body!!.shouldNotContain("\"total\":0")
        off.activate()
        // Al encenderlo despues, la siembra le da su apertura --lo que HAY-- pero
        // no los eventos que se perdio: un solo asiento y no dos.
        http.getJson("$WAREHOUSE/movements", off.accessToken).body!!.shouldContain("\"total\":1")
        http.getJson("$WAREHOUSE/movements", off.accessToken).body!!.shouldContain("\"kind\":\"OPENING\"")
    }

    // -----------------------------------------------------------------------
    // La siembra: la primera de verdad, y su idempotencia
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("encender sobre una despensa que ya existia asienta lo que hay")
    fun `la siembra recorre las existencias que el hogar ya tenia`() {
        val home = http.registerHousehold()
        val pantry = home.location("Despensa")
        val sugar = home.article("Azúcar", "GRAM")
        val rice = home.article("Arroz blanco", "GRAM")
        home.intake(sugar, pantry, 1000)
        home.intake(rice, pantry, 500)

        home.activate()

        val stock = http.getJson("$WAREHOUSE/stock", home.accessToken).body!!
        stock.shouldContain("\"total\":2")
        stock.shouldContain("Azúcar")
        // La cantidad viene **del core**, no de ninguna tabla de Warehouse.
        stock.shouldContain("\"quantity\":1000")

        val movements = http.getJson("$WAREHOUSE/movements", home.accessToken).body!!
        movements.shouldContain("\"total\":2")
        movements.shouldContain("\"kind\":\"OPENING\"")
    }

    @Test
    @DisplayName("apagar y volver a encender resiembra y NO duplica ni un asiento")
    fun `la siembra es idempotente porque reactivar la ejecuta`() {
        val home = http.registerHousehold()
        val pantry = home.location("Despensa")
        val sugar = home.article("Azúcar moreno", "GRAM")
        home.intake(sugar, pantry, 1000)

        home.activate()
        http.getJson("$WAREHOUSE/movements", home.accessToken).body!!.shouldContain("\"total\":1")

        // Apagar conserva; reactivar **vuelve a sembrar**, que es lo contrario de
        // lo que la ADR-010 escribio y lo que `ActivateModule` hace de verdad.
        // Sin la idempotencia por indice unico, aqui habria dos aperturas.
        home.deactivate()
        home.activate()
        http.getJson("$WAREHOUSE/movements", home.accessToken).body!!.shouldContain("\"total\":1")

        // Y una tercera vez, por si acaso el segundo caso fuese casualidad.
        home.deactivate()
        home.activate()
        http.getJson("$WAREHOUSE/movements", home.accessToken).body!!.shouldContain("\"total\":1")
    }

    @Test
    @DisplayName("lo que llego mientras estaba apagado lo recupera la siembra al reactivar")
    fun `reactivar pone al dia lo que se perdio`() {
        val home = http.registerHousehold()
        val pantry = home.location("Despensa")
        home.activate()
        home.intake(home.article("Sal", "GRAM"), pantry, 500)

        home.deactivate()
        // Esto no lo ve nadie: el modulo esta apagado.
        val oil = home.article("Aceite", "MILLILITER")
        home.intake(oil, pantry, 750)

        home.activate()

        // La existencia nueva aparece, y con su apertura. Es exactamente el caso
        // que hacia falta decidir: sin resembrar, la despensa se quedaria corta y
        // **nada lo diria**.
        val stock = http.getJson("$WAREHOUSE/stock", home.accessToken).body!!
        stock.shouldContain("\"total\":2")
        stock.shouldContain("Aceite")
    }

    // -----------------------------------------------------------------------
    // El cuaderno, escrito por los eventos del core
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("el cuaderno se escribe desde los eventos: entrada, ajuste, movimiento y baja")
    fun `los handlers asientan lo que pasa en el core`() {
        val home = http.registerHousehold()
        home.activate()

        val pantry = home.location("Despensa")
        val garage = home.location("Garaje")
        val article = home.article("Arroz redondo", "GRAM")

        // 1. Alta de la existencia -> AssetCreated -> INTAKE.
        val stock = home.intake(article, pantry, 900)
        // 2. Otra entrada sobre la misma -> AssetQuantityChanged(INTAKE).
        home.intake(article, pantry, 100)
        // 3. Un ajuste absoluto por el PATCH del core -> ADJUSTMENT.
        home.adjust(stock, 800)
        // 4. Moverla -> AssetMoved -> RELOCATION.
        home.move(stock, garage)

        val movements = http.getJson("$WAREHOUSE/movements?assetId=$stock", home.accessToken).body!!
        movements.shouldContain("\"total\":4")
        movements.shouldContain("\"kind\":\"INTAKE\"")
        movements.shouldContain("\"kind\":\"ADJUSTMENT\"")
        movements.shouldContain("\"kind\":\"RELOCATION\"")
        // El asiento guarda el nombre del sitio **de aquel dia**, no solo su
        // identificador: `DeleteLocation` borra la fila de verdad.
        movements.shouldContain("Garaje")

        // Y la baja cierra: el ajuste a cero que la baja publica se asienta como
        // DECOMMISSION, y no se asienta ademas otro por `AssetDeactivated`.
        http.deleteJson("/api/v1/assets/$stock", home.accessToken).statusCode.shouldBe(HttpStatus.NO_CONTENT)
        val afterRemoval = http.getJson("$WAREHOUSE/movements?assetId=$stock", home.accessToken).body!!
        afterRemoval.shouldContain("\"total\":5")
        afterRemoval.shouldContain("\"kind\":\"DECOMMISSION\"")
    }

    // -----------------------------------------------------------------------
    // El consumo: la operacion que hace visible la frontera
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("un consumo es un DELTA, mueve el contador del CORE y lo asienta el handler")
    fun `registrar un consumo atraviesa la frontera`() {
        val home = http.registerHousehold()
        home.activate()

        val pantry = home.location("Despensa")
        val article = home.article("Café", "GRAM")
        val stock = home.intake(article, pantry, 1000)

        val consumed = http.postJson(
            "$WAREHOUSE/stock/$stock/consumptions",
            """{"quantity":250}""",
            home.accessToken,
        )
        consumed.statusCode.shouldBe(HttpStatus.NO_CONTENT)

        // **El contador es el del core**, y ha bajado de verdad: se comprueba en
        // la API del core y no en la de Warehouse, que es lo unico que demuestra
        // que no hay dos contadores.
        http.getJson("/api/v1/assets/$stock", home.accessToken).body!!
            .extractRaw("quantity").shouldBe("750")

        // Y el asiento existe, escrito por el handler del evento que el core
        // publico. La operacion de Warehouse no escribe en el cuaderno.
        val movements = http.getJson("$WAREHOUSE/movements?assetId=$stock", home.accessToken).body!!
        movements.shouldContain("\"delta\":-250")
    }

    @Test
    @DisplayName("no se puede consumir mas de lo que hay, ni una cantidad que no sea positiva")
    fun `las dos reglas del consumo`() {
        val home = http.registerHousehold()
        home.activate()
        val stock = home.intake(home.article("Harina", "GRAM"), home.location("Despensa"), 500)

        val tooMuch = http.postJson(
            "$WAREHOUSE/stock/$stock/consumptions",
            """{"quantity":600}""",
            home.accessToken,
        )
        tooMuch.statusCode.shouldBe(HttpStatus.CONFLICT)
        tooMuch.body!!.shouldContain("STOCK_CONSUMPTION_EXCEEDS_QUANTITY")

        // Cero y negativo los corta la validacion del cuerpo, que es un 400: lo
        // que falla es la forma de lo enviado y no una regla sobre el estado.
        http.postJson("$WAREHOUSE/stock/$stock/consumptions", """{"quantity":0}""", home.accessToken)
            .statusCode.shouldBe(HttpStatus.BAD_REQUEST)

        // Y el contador del core no se ha movido en ninguno de los dos casos.
        http.getJson("/api/v1/assets/$stock", home.accessToken).body!!
            .extractRaw("quantity").shouldBe("500")
    }

    @Test
    @DisplayName("un DURABLE no es una existencia del almacen: no se consume ni admite lote")
    fun `warehouse solo sigue existencias de consumible`() {
        val home = http.registerHousehold()
        home.activate()

        val drill = http.postJson(
            "/api/v1/assets",
            """{"name":"Taladro","type":"DURABLE","categoryId":"${home.category("Herramientas")}"}""",
            home.accessToken,
        ).body!!.extract("id")

        val consumed = http.postJson(
            "$WAREHOUSE/stock/$drill/consumptions",
            """{"quantity":1}""",
            home.accessToken,
        )
        consumed.statusCode.shouldBe(HttpStatus.CONFLICT)
        consumed.body!!.shouldContain("STOCK_ITEM_NOT_TRACKED")

        val lot = http.postJson(
            "$WAREHOUSE/lots",
            """{"assetId":"$drill","expiresOn":"2027-01-01","quantity":1}""",
            home.accessToken,
        )
        lot.statusCode.shouldBe(HttpStatus.CONFLICT)
        lot.body!!.shouldContain("STOCK_ITEM_NOT_TRACKED")
    }

    // -----------------------------------------------------------------------
    // Los lotes
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("los lotes pueden sumar MENOS que la cantidad, pero nunca mas")
    fun `la regla que toca las dos mitades del modelo`() {
        val home = http.registerHousehold()
        home.activate()
        val stock = home.intake(home.article("Yogur", "UNIT"), home.location("Nevera"), 12)

        // Menos que lo que hay: perfectamente normal. Lo que no esta en ningun
        // lote es lo que nadie se molesto en fechar.
        http.postJson(
            "$WAREHOUSE/lots",
            """{"assetId":"$stock","lotCode":"L-1","expiresOn":"2026-09-01","quantity":6}""",
            home.accessToken,
        ).statusCode.shouldBe(HttpStatus.CREATED)

        // Otro que cabe.
        http.postJson(
            "$WAREHOUSE/lots",
            """{"assetId":"$stock","lotCode":"L-2","expiresOn":"2026-10-01","quantity":4}""",
            home.accessToken,
        ).statusCode.shouldBe(HttpStatus.CREATED)

        // Y uno que se pasaria: 6 + 4 + 5 = 15 sobre 12.
        val overflow = http.postJson(
            "$WAREHOUSE/lots",
            """{"assetId":"$stock","lotCode":"L-3","expiresOn":"2026-11-01","quantity":5}""",
            home.accessToken,
        )
        overflow.statusCode.shouldBe(HttpStatus.CONFLICT)
        overflow.body!!.shouldContain("STOCK_LOT_EXCEEDS_QUANTITY")
    }

    @Test
    @DisplayName("dos lotes vivos con el mismo codigo y la misma fecha chocan, tambien sin codigo")
    fun `no hay dos lotes vivos iguales`() {
        val home = http.registerHousehold()
        home.activate()
        val stock = home.intake(home.article("Leche", "LITER"), home.location("Nevera"), 6)

        val body = """{"assetId":"$stock","expiresOn":"2026-09-15","quantity":1}"""
        http.postJson("$WAREHOUSE/lots", body, home.accessToken).statusCode.shouldBe(HttpStatus.CREATED)

        // **Sin codigo los dos**, que es el caso que el `NULLS NOT DISTINCT` del
        // indice cubre: sin el, el caso mas comun --nadie apunta el codigo-- seria
        // el que se puede duplicar sin limite.
        val clash = http.postJson("$WAREHOUSE/lots", body, home.accessToken)
        clash.statusCode.shouldBe(HttpStatus.CONFLICT)
        clash.body!!.shouldContain("STOCK_LOT_DUPLICATE")
    }

    @Test
    @DisplayName("descartar un lote NO toca el contador del core: eso es un consumo")
    fun `descartar y consumir son cosas distintas`() {
        val home = http.registerHousehold()
        home.activate()
        val stock = home.intake(home.article("Nata", "MILLILITER"), home.location("Nevera"), 500)

        val lotId = http.postJson(
            "$WAREHOUSE/lots",
            """{"assetId":"$stock","expiresOn":"2026-09-20","quantity":200}""",
            home.accessToken,
        ).body!!.extract("id")

        http.deleteJson("$WAREHOUSE/lots/$lotId", home.accessToken).statusCode.shouldBe(HttpStatus.NO_CONTENT)
        // Descartar dos veces no es un error.
        http.deleteJson("$WAREHOUSE/lots/$lotId", home.accessToken).statusCode.shouldBe(HttpStatus.NO_CONTENT)

        http.getJson("/api/v1/assets/$stock", home.accessToken).body!!
            .extractRaw("quantity").shouldBe("500")
        http.getJson("$WAREHOUSE/lots?assetId=$stock", home.accessToken).body!!.shouldContain("\"total\":0")
    }

    // -----------------------------------------------------------------------
    // El minimo
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("fijar un minimo decide EN EL ACTO si el articulo esta por debajo")
    fun `el minimo no espera a la noche`() {
        val home = http.registerHousehold()
        home.activate()
        val article = home.article("Papel de cocina", "UNIT")
        val stock = home.intake(article, home.location("Trastero"), 2)

        val set = http.patchJson(
            "$WAREHOUSE/articles/$article",
            """{"minimumQuantity":3}""",
            home.accessToken,
        )
        set.statusCode.shouldBe(HttpStatus.OK)
        // Quien lo fija lo hace mirando la despensa y espera que la pantalla le dé
        // la razon ya.
        set.body!!.shouldNotContain("\"lowStockSince\":null")

        http.getJson("$WAREHOUSE/stock?belowMinimum=true", home.accessToken).body!!
            .shouldContain("Papel de cocina")

        // Y reponer lo saca. `belowMinimum` compara con **menor o igual**: con 3
        // sobre un minimo de 3 sigue estando bajo minimos, asi que hacen falta 4.
        home.intake(article, home.location("Trastero"), 2)
        http.getJson("$WAREHOUSE/stock?belowMinimum=true", home.accessToken).body!!
            .shouldContain("\"total\":0")
    }

    @Test
    @DisplayName("quitar el minimo limpia el estado que colgaba de el")
    fun `un articulo que ya no se vigila no se queda marcado`() {
        val home = http.registerHousehold()
        home.activate()
        val article = home.article("Servilletas", "UNIT")
        home.intake(article, home.location("Trastero"), 1)

        http.patchJson("$WAREHOUSE/articles/$article", """{"minimumQuantity":5}""", home.accessToken)
        http.getJson("$WAREHOUSE/stock?belowMinimum=true", home.accessToken).body!!
            .shouldContain("Servilletas")

        val cleared = http.patchJson(
            "$WAREHOUSE/articles/$article",
            """{"minimumQuantity":null}""",
            home.accessToken,
        )
        cleared.body!!.shouldContain("\"minimumQuantity\":null")
        cleared.body!!.shouldContain("\"lowStockSince\":null")
    }

    // -----------------------------------------------------------------------
    // La ficha de una existencia y las dos antelaciones
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("la antelacion del sitio manda sobre la del articulo")
    fun `la cadena de antelacion`() {
        val home = http.registerHousehold()
        home.activate()
        val fridge = home.location("Nevera")
        val article = home.article("Queso fresco", "UNIT")
        val stock = home.intake(article, fridge, 2)

        http.patchJson("$WAREHOUSE/articles/$article", """{"expiryLeadDays":30}""", home.accessToken)
            .statusCode.shouldBe(HttpStatus.OK)
        http.getJson("$WAREHOUSE/stock/$stock", home.accessToken).body!!
            .shouldContain("\"expiryLeadDays\":30")

        // El sitio gana: el mismo queso aguanta distinto dentro de la nevera.
        http.patchJson("$WAREHOUSE/locations/$fridge", """{"expiryLeadDays":3}""", home.accessToken)
            .statusCode.shouldBe(HttpStatus.OK)
        http.getJson("$WAREHOUSE/stock/$stock", home.accessToken).body!!
            .shouldContain("\"expiryLeadDays\":3")
    }

    @Test
    @DisplayName("la ficha de una existencia trae sus lotes y sus ultimos movimientos")
    fun `la ficha junta las dos mitades`() {
        val home = http.registerHousehold()
        home.activate()
        val stock = home.intake(home.article("Garbanzos", "GRAM"), home.location("Despensa"), 1000)
        http.postJson(
            "$WAREHOUSE/lots",
            """{"assetId":"$stock","expiresOn":"${LocalDate.now().plusMonths(6)}","quantity":400}""",
            home.accessToken,
        ).statusCode.shouldBe(HttpStatus.CREATED)

        val detail = http.getJson("$WAREHOUSE/stock/$stock", home.accessToken)
        detail.statusCode.shouldBe(HttpStatus.OK)
        detail.body!!.shouldContain("Garbanzos")
        detail.body!!.shouldContain("\"lotCount\":1")
        detail.body!!.shouldContain("\"kind\":\"INTAKE\"")
    }


    // -----------------------------------------------------------------------
    // Los avisos: los dos primeros de un modulo de verdad
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("la caducidad avisa, y el resumen se lee del Mailpit de verdad")
    fun `el aviso de caducidad llega al correo real`() {
        val home = http.registerHousehold(mailpit)
        home.activate()
        val stock = home.intake(home.article("Yogur natural", "UNIT"), home.location("Nevera"), 4)

        // Dentro de la ventana por omision del modulo, que son siete dias.
        http.postJson(
            "$WAREHOUSE/lots",
            """{"assetId":"$stock","lotCode":"L-9","expiresOn":"${LocalDate.now().plusDays(2)}","quantity":4}""",
            home.accessToken,
        ).statusCode.shouldBe(HttpStatus.CREATED)

        mailpit.clear()
        sweep.run()

        // En la bandeja...
        http.getJson("/api/v1/notices", home.accessToken).body!!
            .shouldContain("WAREHOUSE_EXPIRY_NEAR")

        // ...y en el buzon de verdad, que es lo que la ADR-009 pide demostrar y
        // como se lee el enlace de verificacion desde la Fase 1.
        val digest = mailpit.awaitMessageTo(home.email)
        digest.body.shouldContain("Yogur natural caduca pronto")
        digest.body.shouldContain("/avisos")
    }

    @Test
    @DisplayName("el mismo lote NO vuelve a avisar la noche siguiente")
    fun `un aviso por condicion y no uno por noche`() {
        val home = http.registerHousehold(mailpit)
        home.activate()
        val stock = home.intake(home.article("Requesón", "UNIT"), home.location("Nevera"), 2)
        http.postJson(
            "$WAREHOUSE/lots",
            """{"assetId":"$stock","expiresOn":"${LocalDate.now().plusDays(3)}","quantity":2}""",
            home.accessToken,
        ).statusCode.shouldBe(HttpStatus.CREATED)

        sweep.run()
        countNotices(home.accessToken, "WAREHOUSE_EXPIRY_NEAR").shouldBe(1)

        // Segunda y tercera pasada: la condicion **sigue siendo cierta** y no se
        // vuelve a avisar. Un yogur caducado que avisara treinta noches seguidas
        // es la forma mas rapida de que se filtre el resumen diario entero.
        sweep.run()
        sweep.run()
        countNotices(home.accessToken, "WAREHOUSE_EXPIRY_NEAR").shouldBe(1)
    }

    @Test
    @DisplayName("un lote ya caducado avisa de que se pasó, que es una noticia distinta")
    fun `las dos fases de un lote`() {
        val home = http.registerHousehold(mailpit)
        home.activate()
        val stock = home.intake(home.article("Kéfir", "UNIT"), home.location("Nevera"), 1)
        http.postJson(
            "$WAREHOUSE/lots",
            """{"assetId":"$stock","expiresOn":"${LocalDate.now().minusDays(1)}","quantity":1}""",
            home.accessToken,
        ).statusCode.shouldBe(HttpStatus.CREATED)

        sweep.run()

        val notices = http.getJson("/api/v1/notices", home.accessToken).body!!
        notices.shouldContain("WAREHOUSE_EXPIRED")
        // Y no las dos a la vez: lo que ya caducó no «caduca pronto».
        notices.shouldNotContain("WAREHOUSE_EXPIRY_NEAR")
    }

    @Test
    @DisplayName("el mínimo alcanzado avisa una vez, y vuelve a armarse al reponer")
    fun `el aviso de minimo se rearma`() {
        val home = http.registerHousehold(mailpit)
        home.activate()
        val pantry = home.location("Despensa")
        val article = home.article("Detergente", "LITER")
        home.intake(article, pantry, 1)
        http.patchJson("$WAREHOUSE/articles/$article", """{"minimumQuantity":2}""", home.accessToken)
            .statusCode.shouldBe(HttpStatus.OK)

        sweep.run()
        countNotices(home.accessToken, "WAREHOUSE_STOCK_BELOW_MINIMUM").shouldBe(1)

        // Sigue estando bajo minimos: no se repite.
        sweep.run()
        countNotices(home.accessToken, "WAREHOUSE_STOCK_BELOW_MINIMUM").shouldBe(1)

        // Se repone por encima --el minimo compara con menor o igual, asi que
        // hacen falta 3-- y se vuelve a gastar. Ahora **si** avisa otra vez: sin
        // rearmar, un articulo avisaria una sola vez en toda su vida.
        home.intake(article, pantry, 2)
        val stock = http.getJson("$WAREHOUSE/stock?q=Detergente", home.accessToken).body!!.extract("assetId")
        http.postJson("$WAREHOUSE/stock/$stock/consumptions", """{"quantity":2}""", home.accessToken)
            .statusCode.shouldBe(HttpStatus.NO_CONTENT)

        sweep.run()
        countNotices(home.accessToken, "WAREHOUSE_STOCK_BELOW_MINIMUM").shouldBe(2)
    }

    @Test
    @DisplayName("un hogar con Warehouse apagado no recibe sus avisos, y el de al lado si")
    fun `el recorrido se salta el modulo apagado`() {
        val on = http.registerHousehold(mailpit)
        val off = http.registerHousehold(mailpit)
        on.activate()

        // El mismo lote a punto de caducar en los dos hogares. En el apagado hay
        // que encender para poder escribirlo --su ruta esta cerrada por el gate--
        // y volver a apagar, que es lo que deja el dato dentro con el modulo off.
        listOf(on to true, off to false).forEach { (home, keepOn) ->
            if (!keepOn) home.activate()
            val stock = home.intake(home.article("Nata líquida", "MILLILITER"), home.location("Nevera"), 200)
            http.postJson(
                "$WAREHOUSE/lots",
                """{"assetId":"$stock","expiresOn":"${LocalDate.now().plusDays(1)}","quantity":200}""",
                home.accessToken,
            ).statusCode.shouldBe(HttpStatus.CREATED)
            if (!keepOn) home.deactivate()
        }

        sweep.run()

        // Es la comparacion la que dice algo: sin el hogar encendido al lado,
        // «no hay aviso» podria significar que la comprobacion esta rota.
        http.getJson("/api/v1/notices", on.accessToken).body!!.shouldContain("WAREHOUSE_EXPIRY_NEAR")
        http.getJson("/api/v1/notices", off.accessToken).body!!.shouldNotContain("WAREHOUSE_EXPIRY_NEAR")
    }

    /** Cuantos avisos de esa clase tiene el hogar. La bandeja no filtra por `kind`. */
    private fun countNotices(accessToken: String, kind: String): Int =
        Regex(""""kind":"$kind"""")
            .findAll(http.getJson("/api/v1/notices?size=200", accessToken).body.orEmpty())
            .count()

    // -----------------------------------------------------------------------
    // Apoyo
    // -----------------------------------------------------------------------

    private fun com.drp.test.TestHousehold.activate() {
        http.postJson("/api/v1/modules/WAREHOUSE/activation", "", accessToken)
            .statusCode.shouldBe(HttpStatus.OK)
    }

    private fun com.drp.test.TestHousehold.deactivate() {
        http.deleteJson("/api/v1/modules/WAREHOUSE/activation", accessToken)
            .statusCode.shouldBe(HttpStatus.OK)
    }

    private fun com.drp.test.TestHousehold.category(name: String) = http.seededCategory(accessToken, name)

    /**
     * Un sitio del hogar, **reutilizando el que ya haya con ese nombre**. Sin
     * esto, una prueba que reponga en el mismo sitio dos veces choca con
     * `LOCATION_DUPLICATE`, que es una regla del core y no lo que se esta
     * midiendo aqui.
     */
    private fun com.drp.test.TestHousehold.location(name: String): String {
        val created = http.postJson("/api/v1/locations", """{"name":"$name","type":"ROOM"}""", accessToken)
        if (created.statusCode == HttpStatus.CREATED) return created.body!!.extract("id")

        val existing = http.getJson("/api/v1/locations", accessToken).body!!
        val entry = Regex("""\{[^{}]*"name":"$name"[^{}]*\}""").find(existing)
            ?: error("No aparece la ubicación «$name»: $existing")
        return entry.value.extract("id")
    }

    private fun com.drp.test.TestHousehold.article(name: String, unit: String): String =
        http.postJson(
            "/api/v1/articles",
            """{"name":"$name","categoryId":"${category("Alimentación")}","unit":"$unit"}""",
            accessToken,
        ).body!!.extract("id")

    private fun com.drp.test.TestHousehold.intake(articleId: String, locationId: String, quantity: Int): String {
        val done = http.postJson(
            "/api/v1/assets/intake",
            """{"articleId":"$articleId","ownerId":"$memberId","quantity":$quantity,
                "location":{"type":"LOCATION","id":"$locationId"}}""",
            accessToken,
        )
        done.statusCode.is2xxSuccessful.shouldBe(true)
        return done.body!!.extract("id")
    }

    private fun com.drp.test.TestHousehold.adjust(assetId: String, quantity: Int) {
        http.patchJson("/api/v1/assets/$assetId", """{"quantity":$quantity}""", accessToken)
            .statusCode.shouldBe(HttpStatus.OK)
    }

    private fun com.drp.test.TestHousehold.move(assetId: String, locationId: String) {
        http.patchJson(
            "/api/v1/assets/$assetId",
            """{"location":{"type":"LOCATION","id":"$locationId"}}""",
            accessToken,
        ).statusCode.shouldBe(HttpStatus.OK)
    }

    private companion object {
        const val WAREHOUSE = "/api/v1/warehouse"
    }
}
