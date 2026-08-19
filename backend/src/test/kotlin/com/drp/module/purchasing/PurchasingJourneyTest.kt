package com.drp.module.purchasing

import com.drp.test.SpringIntegrationTest
import com.drp.test.TestHousehold
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
 * El recorrido de Compras, por HTTP y contra PostgreSQL real.
 *
 * **Lo que este modulo estrena y ninguno anterior podia comprobar:**
 *
 *  1. **Dos modulos que se hablan sin depender uno de que el otro este activo**,
 *     que es el riesgo arquitectonico principal de la fase. Las dos mitades estan
 *     medidas --Warehouse apagado y Compras encendido, y al reves-- y las dos
 *     contra un hogar con los dos encendidos, que es lo que distingue «el gate
 *     funciona» de «el handler esta roto».
 *  2. **Un modulo que lee el dato maestro de otro**, por un puerto de plataforma,
 *     y que **degrada con ese otro apagado** sin una sola rama propia.
 *  3. **Un modulo que escribe en el core.** Cerrar la compra invoca la entrada de
 *     consumibles, que **crea existencias**; y con Warehouse encendido eso aparece
 *     en su cuaderno, que es el ciclo cerrandose.
 *
 * **No estrena contexto de Spring**: mismo `@SpringBootTest` que las demas pruebas
 * de recorrido, sin propiedades propias ni `@TestConfiguration`. Cada combinacion
 * nueva reserva un pool entero que no se suelta mientras siga en cache, y eso ya
 * tumbo tres pruebas de otros hitos en la Fase 1. Los handlers se ejercitan **por
 * la API de verdad**, que es ademas como corren en produccion.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PurchasingJourneyTest : SpringIntegrationTest() {

    @Autowired private lateinit var http: TestRestTemplate

    // -----------------------------------------------------------------------
    // El gate, sobre una ruta que existe
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("apagado responde 403 MODULE_INACTIVE y encendido responde 200")
    fun `las dos mitades del gate`() {
        val home = http.registerHousehold()

        val closed = http.getJson("$PURCHASING/list", home.accessToken)
        closed.statusCode.shouldBe(HttpStatus.FORBIDDEN)
        closed.body!!.shouldContain("MODULE_INACTIVE")

        // El gate cuelga del prefijo, asi que alcanza a todo lo que hay debajo
        // --incluida la ruta que lee el dato maestro de otro modulo.
        http.getJson("$PURCHASING/purchases", home.accessToken).statusCode.shouldBe(HttpStatus.FORBIDDEN)
        http.getJson("$PURCHASING/suppliers", home.accessToken).statusCode.shouldBe(HttpStatus.FORBIDDEN)

        home.activate(PURCHASING_KEY)

        val opened = http.getJson("$PURCHASING/list", home.accessToken)
        opened.statusCode.shouldBe(HttpStatus.OK)
        opened.body!!.shouldNotContain("MODULE_INACTIVE")
    }

    // -----------------------------------------------------------------------
    // La prueba que este hito existe para hacer: las dos mitades
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Warehouse apagado y Compras encendido: la lista no se llena sola, y se llena a mano")
    fun `sin Warehouse nadie detecta la falta`() {
        val home = http.registerHousehold()
        home.activate(PURCHASING_KEY)

        // Toda la actividad que Warehouse convertiria en un evento: dar de alta un
        // consumible con minimo... salvo que no hay donde fijar un minimo, porque
        // ese modulo esta apagado. Asi que se gasta hasta cero por el PATCH del
        // core, que es lo que hace un hogar sin almacen.
        val pantry = home.location("Despensa")
        val rice = home.article("Arroz", "GRAM")
        val stock = home.intake(rice, pantry, 900)
        home.adjust(stock, 0)

        // **Nada.** No es que lleguen eventos vacios: es que no llegan.
        http.getJson("$PURCHASING/list", home.accessToken).body!!.shouldContain("\"total\":0")

        // Y la lista sigue sirviendo, porque se llena a mano. Esta operacion existe
        // por esto y no como comodidad.
        http.postJson("$PURCHASING/list", """{"articleId":"$rice","quantity":1000}""", home.accessToken)
            .statusCode.shouldBe(HttpStatus.CREATED)

        val list = http.getJson("$PURCHASING/list", home.accessToken).body!!
        list.shouldContain("\"total\":1")
        list.shouldContain("\"origin\":\"MANUAL\"")
    }

    @Test
    @DisplayName("Compras apagado y Warehouse encendido: Warehouse publica, escribe su cuaderno y nadie escucha")
    fun `sin Compras nadie escucha`() {
        val home = http.registerHousehold()
        home.activate(WAREHOUSE_KEY)

        val pantry = home.location("Despensa")
        val rice = home.article("Arroz", "GRAM")
        val stock = home.intake(rice, pantry, 900)
        http.patchJson("$WAREHOUSE/articles/$rice", """{"minimumQuantity":100}""", home.accessToken)
            .statusCode.shouldBe(HttpStatus.OK)
        home.adjust(stock, 0)

        // Warehouse sigue funcionando entero: su cuaderno tiene el asiento.
        http.getJson("$WAREHOUSE/movements", home.accessToken).body!!.shouldContain("\"kind\":\"ADJUSTMENT\"")

        // Y Compras no ha escrito ni una fila. Se enciende **despues** para poder
        // mirarlo: la siembra recorre lo que hay ahora --un articulo a cero-- asi
        // que lo que demuestra que el handler no escribio es el ORIGEN de lo que
        // aparece, no que no aparezca nada.
        home.activate(PURCHASING_KEY)
        val list = http.getJson("$PURCHASING/list", home.accessToken).body!!
        list.shouldContain("\"origin\":\"DEPLETED\"")
        list.shouldNotContain("\"origin\":\"LOW_STOCK\"")
    }

    @Test
    @DisplayName("con los dos encendidos, la lista se llena sola: es la comparacion la que dice algo")
    fun `los dos encendidos cierran el circuito`() {
        val home = http.registerHousehold()
        home.activate(WAREHOUSE_KEY)
        home.activate(PURCHASING_KEY)

        val pantry = home.location("Despensa")
        val rice = home.article("Arroz", "GRAM")
        val stock = home.intake(rice, pantry, 900)
        http.patchJson("$WAREHOUSE/articles/$rice", """{"minimumQuantity":100}""", home.accessToken)
            .statusCode.shouldBe(HttpStatus.OK)

        // Baja del minimo sin llegar a cero: una sola noticia.
        home.adjust(stock, 50)
        val low = http.getJson("$PURCHASING/list", home.accessToken).body!!
        low.shouldContain("\"total\":1")
        low.shouldContain("\"origin\":\"LOW_STOCK\"")

        // Y ahora se acaba. **No deja una segunda linea** --el indice unico parcial
        // lo impide-- y la que hay **sube de nivel**, porque acabarse es una
        // noticia distinta de estar bajo minimos.
        home.adjust(stock, 0)
        val gone = http.getJson("$PURCHASING/list", home.accessToken).body!!
        gone.shouldContain("\"total\":1")
        gone.shouldContain("\"origin\":\"DEPLETED\"")
    }

    @Test
    @DisplayName("un articulo SIN minimo que llega a cero entra en la lista igual")
    fun `acabarse no depende de haber fijado un minimo`() {
        val home = http.registerHousehold()
        home.activate(WAREHOUSE_KEY)
        home.activate(PURCHASING_KEY)

        // Sin tocar `/warehouse/articles`: este articulo no tiene minimo, que es el
        // caso de casi todo lo que hay en una despensa. Hasta este hito
        // `StockDepleted` colgaba de la rama de bajo minimos y no se publicaba
        // nunca aqui, con lo que «lo que llega a cero entra en la lista» era falso
        // para casi todo.
        val pantry = home.location("Despensa")
        val paprika = home.article("Pimentón", "GRAM")
        val stock = home.intake(paprika, pantry, 40)
        home.adjust(stock, 0)

        val list = http.getJson("$PURCHASING/list", home.accessToken).body!!
        list.shouldContain("\"origin\":\"DEPLETED\"")
        list.shouldContain("Pimentón")
    }

    // -----------------------------------------------------------------------
    // El cierre del ciclo
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("comprar y recibir suma sobre la existencia de esa ubicacion, y Warehouse lo asienta")
    fun `el ciclo se cierra`() {
        val home = http.registerHousehold()
        home.activate(WAREHOUSE_KEY)
        home.activate(PURCHASING_KEY)

        val pantry = home.location("Despensa")
        val rice = home.article("Arroz", "GRAM")
        val stock = home.intake(rice, pantry, 200)

        val item = http.postJson("$PURCHASING/list", """{"articleId":"$rice","quantity":800}""", home.accessToken)
            .body!!.extract("id")
        val purchase = http.postJson("$PURCHASING/purchases", """{"itemIds":["$item"]}""", home.accessToken)
            .also { it.statusCode.shouldBe(HttpStatus.CREATED) }
            .body!!.extract("id")

        val received = http.postJson("$PURCHASING/purchases/$purchase/receipt", """{"lines":[]}""", home.accessToken)
        received.statusCode.shouldBe(HttpStatus.OK)
        received.body!!.shouldContain("\"status\":\"BOUGHT\"")

        // **Suma, no crea otra.** La existencia es la misma de antes, con 200 + 800
        // dentro: sin heredar la ubicacion de donde el arroz ya vivia, la entrada
        // del core habria abierto una segunda fila y la despensa tendria dos
        // arroces.
        val asset = http.getJson("/api/v1/assets/$stock", home.accessToken).body!!
        asset.shouldContain("\"quantity\":1000")

        // Y **el ciclo se ve desde el otro lado**: el core publico al sumar, asi
        // que el handler de Warehouse asento la entrada sin que ninguno de los dos
        // modulos sepa del otro.
        val ledger = http.getJson("$WAREHOUSE/movements?assetId=$stock", home.accessToken).body!!
        ledger.shouldContain("\"kind\":\"INTAKE\"")
    }

    @Test
    @DisplayName("recibir dos veces la misma compra no da entrada dos veces")
    fun `el cierre es idempotente`() {
        val home = http.registerHousehold()
        home.activate(PURCHASING_KEY)

        val pantry = home.location("Despensa")
        val rice = home.article("Arroz", "GRAM")
        val stock = home.intake(rice, pantry, 200)

        val item = http.postJson("$PURCHASING/list", """{"articleId":"$rice","quantity":800}""", home.accessToken)
            .body!!.extract("id")
        val purchase = http.postJson("$PURCHASING/purchases", """{"itemIds":["$item"]}""", home.accessToken)
            .body!!.extract("id")

        http.postJson("$PURCHASING/purchases/$purchase/receipt", """{"lines":[]}""", home.accessToken)
            .statusCode.shouldBe(HttpStatus.OK)

        // La segunda no pasa del estado de la compra. Aunque pasara, cada linea se
        // reclama con un cambio condicional antes de darle entrada.
        val again = http.postJson("$PURCHASING/purchases/$purchase/receipt", """{"lines":[]}""", home.accessToken)
        again.statusCode.shouldBe(HttpStatus.CONFLICT)
        again.body!!.shouldContain("PURCHASE_NOT_OPEN")

        http.getJson("/api/v1/assets/$stock", home.accessToken).body!!.shouldContain("\"quantity\":1000")
    }

    @Test
    @DisplayName("una linea de texto suelto se compra y no entra en el inventario")
    fun `el texto suelto no inventa un articulo`() {
        val home = http.registerHousehold()
        home.activate(PURCHASING_KEY)

        val item = http.postJson("$PURCHASING/list", """{"name":"Pilas AA"}""", home.accessToken)
            .body!!.extract("id")
        val purchase = http.postJson("$PURCHASING/purchases", """{"itemIds":["$item"]}""", home.accessToken)
            .body!!.extract("id")

        val received = http.postJson("$PURCHASING/purchases/$purchase/receipt", """{"lines":[]}""", home.accessToken)
        received.statusCode.shouldBe(HttpStatus.OK)
        // Comprada, y sin existencia detras: inventarle una categoria y una unidad
        // seria decidir por el hogar.
        received.body!!.shouldContain("\"status\":\"BOUGHT\"")
        received.body!!.shouldContain("\"receivedAssetId\":null")
    }

    @Test
    @DisplayName("anular una compra devuelve sus lineas a la lista")
    fun `lo que hacia falta sigue haciendo falta`() {
        val home = http.registerHousehold()
        home.activate(PURCHASING_KEY)

        val item = http.postJson("$PURCHASING/list", """{"name":"Pilas AA"}""", home.accessToken)
            .body!!.extract("id")
        val purchase = http.postJson("$PURCHASING/purchases", """{"itemIds":["$item"]}""", home.accessToken)
            .body!!.extract("id")

        http.getJson("$PURCHASING/list", home.accessToken).body!!.shouldContain("\"status\":\"IN_PURCHASE\"")

        http.deleteJson("$PURCHASING/purchases/$purchase", home.accessToken)
            .statusCode.shouldBe(HttpStatus.NO_CONTENT)

        http.getJson("$PURCHASING/list", home.accessToken).body!!.shouldContain("\"status\":\"NEEDED\"")
    }

    // -----------------------------------------------------------------------
    // El dato maestro de Proveedores, y su degradacion
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("con Proveedores encendido se puede decir donde se compro, y el nombre se queda dentro")
    fun `la compra guarda el nombre de aquel dia`() {
        val home = http.registerHousehold()
        home.activate(PURCHASING_KEY)
        home.activate(SUPPLIERS_KEY)

        val shop = http.postJson(
            "/api/v1/suppliers",
            """{"name":"Mercado de la plaza","serviceCategory":"OTHER","phone":"600100200"}""",
            home.accessToken,
        ).body!!.extract("id")

        // El selector cuelga de **este** modulo y no del de Proveedores.
        http.getJson("$PURCHASING/suppliers", home.accessToken).body!!.shouldContain("Mercado de la plaza")

        val item = http.postJson("$PURCHASING/list", """{"name":"Pilas AA"}""", home.accessToken)
            .body!!.extract("id")
        val purchase = http.postJson(
            "$PURCHASING/purchases",
            """{"supplierId":"$shop","itemIds":["$item"]}""",
            home.accessToken,
        ).body!!.extract("id")

        // Y ahora se apaga Proveedores. La compra **sigue diciendo donde se
        // compro**, porque el nombre se copio: una clave ajena hacia la tabla del
        // otro modulo habria sido una dependencia de esquema, y leerlo hoy daria
        // vacio.
        home.deactivate(SUPPLIERS_KEY)
        http.getJson("$PURCHASING/purchases/$purchase", home.accessToken).body!!
            .shouldContain("Mercado de la plaza")
    }

    @Test
    @DisplayName("con Proveedores apagado el selector responde vacio, y no 403")
    fun `la degradacion la pone plataforma`() {
        val home = http.registerHousehold()
        home.activate(PURCHASING_KEY)

        // 200 con lista vacia y **no** el 403 MODULE_INACTIVE que daria la ruta del
        // otro modulo: la degradacion la pone la fachada de plataforma, asi que
        // Compras no tiene una sola rama para ella.
        val shops = http.getJson("$PURCHASING/suppliers", home.accessToken)
        shops.statusCode.shouldBe(HttpStatus.OK)
        shops.body!!.shouldBe("[]")

        val item = http.postJson("$PURCHASING/list", """{"name":"Pilas AA"}""", home.accessToken)
            .body!!.extract("id")

        // Nombrar un proveedor con el modulo apagado se rechaza igual que uno
        // inventado, y **no distingue los dos casos**: distinguirlos obligaria a
        // Compras a saber que modulos tiene encendidos el hogar.
        val rejected = http.postJson(
            "$PURCHASING/purchases",
            """{"supplierId":"${java.util.UUID.randomUUID()}","itemIds":["$item"]}""",
            home.accessToken,
        )
        rejected.statusCode.shouldBe(HttpStatus.CONFLICT)
        rejected.body!!.shouldContain("PURCHASE_SUPPLIER_UNKNOWN")

        // Y una compra **sin** proveedor se abre igual, que es lo que hace que la
        // degradacion no recorte ninguna funcionalidad.
        http.postJson("$PURCHASING/purchases", """{"itemIds":["$item"]}""", home.accessToken)
            .statusCode.shouldBe(HttpStatus.CREATED)
    }

    // -----------------------------------------------------------------------
    // La siembra
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("encender siembra con lo que el hogar ya tiene a cero, y reactivar no duplica")
    fun `la siembra lee el contador del core y es idempotente`() {
        val home = http.registerHousehold()

        // Antes de encender nada: una despensa con algo agotado y algo lleno.
        val pantry = home.location("Despensa")
        val empty = home.article("Sal", "GRAM")
        val full = home.article("Azúcar", "GRAM")
        home.adjust(home.intake(empty, pantry, 100), 0)
        home.intake(full, pantry, 500)

        home.activate(PURCHASING_KEY)

        // Solo lo que esta a cero, y **leido del contador del core**: el minimo
        // vive en una tabla de Warehouse y no se puede mirar desde aqui.
        val seeded = http.getJson("$PURCHASING/list", home.accessToken).body!!
        seeded.shouldContain("\"total\":1")
        seeded.shouldContain("Sal")
        seeded.shouldNotContain("Azúcar")

        // Apagar y volver a encender **vuelve a sembrar** --lo hace `ActivateModule`
        // desde siempre-- y no duplica ni una linea, por el indice unico parcial.
        home.deactivate(PURCHASING_KEY)
        home.activate(PURCHASING_KEY)
        http.getJson("$PURCHASING/list", home.accessToken).body!!.shouldContain("\"total\":1")
    }

    @Test
    @DisplayName("apagar conserva los datos: al volver a encender siguen ahi")
    fun `desactivar no borra nada`() {
        val home = http.registerHousehold()
        home.activate(PURCHASING_KEY)

        http.postJson("$PURCHASING/list", """{"name":"Pilas AA"}""", home.accessToken)
            .statusCode.shouldBe(HttpStatus.CREATED)

        home.deactivate(PURCHASING_KEY)
        home.activate(PURCHASING_KEY)

        http.getJson("$PURCHASING/list", home.accessToken).body!!.shouldContain("Pilas AA")
    }

    // -----------------------------------------------------------------------
    // Las reglas de la lista
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("no hay dos lineas vivas para el mismo articulo, y lo comprado no bloquea")
    fun `una sola linea viva por articulo`() {
        val home = http.registerHousehold()
        home.activate(PURCHASING_KEY)

        val rice = home.article("Arroz", "GRAM")
        val first = http.postJson("$PURCHASING/list", """{"articleId":"$rice"}""", home.accessToken)
            .body!!.extract("id")

        val duplicate = http.postJson("$PURCHASING/list", """{"articleId":"$rice"}""", home.accessToken)
        duplicate.statusCode.shouldBe(HttpStatus.CONFLICT)
        duplicate.body!!.shouldContain("SHOPPING_ITEM_DUPLICATE")

        // Descartada deja de ser viva, asi que el mismo articulo vuelve a caber:
        // lo comprado el mes pasado no puede bloquear que hoy vuelva a hacer falta.
        http.deleteJson("$PURCHASING/list/$first", home.accessToken).statusCode.shouldBe(HttpStatus.NO_CONTENT)
        http.postJson("$PURCHASING/list", """{"articleId":"$rice"}""", home.accessToken)
            .statusCode.shouldBe(HttpStatus.CREATED)
    }

    @Test
    @DisplayName("una linea es un articulo o un nombre, nunca las dos cosas ni ninguna")
    fun `la primera invariante por la API`() {
        val home = http.registerHousehold()
        home.activate(PURCHASING_KEY)
        val rice = home.article("Arroz", "GRAM")

        http.postJson("$PURCHASING/list", """{"articleId":"$rice","name":"Arroz"}""", home.accessToken)
            .statusCode.shouldBe(HttpStatus.BAD_REQUEST)
        http.postJson("$PURCHASING/list", """{}""", home.accessToken)
            .statusCode.shouldBe(HttpStatus.BAD_REQUEST)
    }

    @Test
    @DisplayName("asignarle un articulo a una linea de texto suelto la deja entrar en el inventario")
    fun `el patch resuelve el texto suelto`() {
        val home = http.registerHousehold()
        home.activate(PURCHASING_KEY)

        val item = http.postJson("$PURCHASING/list", """{"name":"Pilas AA"}""", home.accessToken)
            .body!!.extract("id")
        val batteries = home.article("Pilas AA", "UNIT")

        val patched = http.patchJson(
            "$PURCHASING/list/$item",
            """{"articleId":"$batteries","quantity":4}""",
            home.accessToken,
        )
        patched.statusCode.shouldBe(HttpStatus.OK)
        // El nombre suelto se va con la asignacion: el nombre pasa a ser el del
        // articulo, y solo hay uno.
        patched.body!!.shouldContain("\"articleId\":\"$batteries\"")

        val purchase = http.postJson("$PURCHASING/purchases", """{"itemIds":["$item"]}""", home.accessToken)
            .body!!.extract("id")
        http.postJson("$PURCHASING/purchases/$purchase/receipt", """{"lines":[]}""", home.accessToken)
            .body!!.shouldNotContain("\"receivedAssetId\":null")
    }

    @Test
    @DisplayName("una linea con articulo y sin cantidad no se recibe a medias: se rechaza antes de empezar")
    fun `el cierre valida entero antes de tocar nada`() {
        val home = http.registerHousehold()
        home.activate(PURCHASING_KEY)

        val rice = home.article("Arroz", "GRAM")
        val item = http.postJson("$PURCHASING/list", """{"articleId":"$rice"}""", home.accessToken)
            .body!!.extract("id")
        val purchase = http.postJson("$PURCHASING/purchases", """{"itemIds":["$item"]}""", home.accessToken)
            .body!!.extract("id")

        // Ni la linea ni el cuerpo dicen cuanta entro. El cierre no cabe en una
        // transaccion, asi que un fallo a mitad no se puede deshacer y lo unico que
        // queda es no empezar.
        http.postJson("$PURCHASING/purchases/$purchase/receipt", """{"lines":[]}""", home.accessToken)
            .statusCode.shouldBe(HttpStatus.BAD_REQUEST)

        // La compra sigue abierta y su linea sigue dentro: no se toco nada.
        http.getJson("$PURCHASING/purchases/$purchase", home.accessToken).body!!
            .shouldContain("\"status\":\"OPEN\"")

        // Diciendolo, entra.
        http.postJson(
            "$PURCHASING/purchases/$purchase/receipt",
            """{"lines":[{"itemId":"$item","quantity":500}]}""",
            home.accessToken,
        ).statusCode.shouldBe(HttpStatus.OK)
    }

    @Test
    @DisplayName("una compra sin lineas no se abre")
    fun `la compra vacia se rechaza`() {
        val home = http.registerHousehold()
        home.activate(PURCHASING_KEY)

        http.postJson("$PURCHASING/purchases", """{"itemIds":[]}""", home.accessToken)
            .statusCode.shouldBe(HttpStatus.BAD_REQUEST)
    }

    // -----------------------------------------------------------------------
    // Apoyo
    // -----------------------------------------------------------------------

    private fun TestHousehold.activate(key: String) {
        http.postJson("/api/v1/modules/$key/activation", "", accessToken).statusCode.shouldBe(HttpStatus.OK)
    }

    private fun TestHousehold.deactivate(key: String) {
        http.deleteJson("/api/v1/modules/$key/activation", accessToken).statusCode.shouldBe(HttpStatus.OK)
    }

    /**
     * Un sitio del hogar, **reutilizando el que ya haya con ese nombre**, igual que
     * en la bateria de Warehouse: sin esto, una prueba que reponga en el mismo
     * sitio dos veces choca con `LOCATION_DUPLICATE`, que es una regla del core y
     * no lo que se esta midiendo aqui.
     */
    private fun TestHousehold.location(name: String): String {
        val created = http.postJson("/api/v1/locations", """{"name":"$name","type":"ROOM"}""", accessToken)
        if (created.statusCode == HttpStatus.CREATED) return created.body!!.extract("id")

        val existing = http.getJson("/api/v1/locations", accessToken).body!!
        val entry = Regex("""\{[^{}]*"name":"$name"[^{}]*\}""").find(existing)
            ?: error("No aparece la ubicación «$name»: $existing")
        return entry.value.extract("id")
    }

    private fun TestHousehold.article(name: String, unit: String): String =
        http.postJson(
            "/api/v1/articles",
            """{"name":"$name","categoryId":"${http.seededCategory(accessToken, "Alimentación")}","unit":"$unit"}""",
            accessToken,
        ).body!!.extract("id")

    private fun TestHousehold.intake(articleId: String, locationId: String, quantity: Int): String {
        val done = http.postJson(
            "/api/v1/assets/intake",
            """{"articleId":"$articleId","ownerId":"$memberId","quantity":$quantity,
                "location":{"type":"LOCATION","id":"$locationId"}}""",
            accessToken,
        )
        done.statusCode.is2xxSuccessful.shouldBe(true)
        return done.body!!.extract("id")
    }

    private fun TestHousehold.adjust(assetId: String, quantity: Int) {
        http.patchJson("/api/v1/assets/$assetId", """{"quantity":$quantity}""", accessToken)
            .statusCode.shouldBe(HttpStatus.OK)
    }

    private companion object {
        const val PURCHASING = "/api/v1/purchasing"
        const val WAREHOUSE = "/api/v1/warehouse"
        const val PURCHASING_KEY = "PURCHASING"
        const val WAREHOUSE_KEY = "WAREHOUSE"
        const val SUPPLIERS_KEY = "SUPPLIERS"
    }
}
