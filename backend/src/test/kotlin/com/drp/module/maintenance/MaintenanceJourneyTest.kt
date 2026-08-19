package com.drp.module.maintenance

import com.drp.platform.schedule.DailySweep
import com.drp.test.DrpMailpit
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
import java.time.LocalDate

/**
 * El recorrido de Mantenimiento, por HTTP y contra PostgreSQL real.
 *
 * Lo que este modulo estrena y ninguno anterior podia comprobar:
 *
 *  1. **La trampa de `DocumentAttached`**, cuyo agregado es el DOCUMENTO y no el
 *     asset. Los otros dos modulos leen `aggregateId` como «la cosa que ha
 *     cambiado» y les vale; aqui eso enlazaria el manual a una maquina que no
 *     existe **sin fallar**.
 *  2. **Un aviso periodico que se rearma.** Warehouse fijo «no repetir mientras la
 *     condicion siga siendo cierta»; aqui hay que volver a armarlo, y el rearme es
 *     un gesto de una persona: registrar la intervencion.
 *  3. **Las dos mitades de la garantia que Proveedores declaro por adelantado y
 *     para este caso**: un contacto retirado sigue siendo legible por su
 *     identificador y deja de ofrecerse en el selector. Nadie las habia
 *     ejercitado, porque Compras copia el nombre y no vuelve a preguntar.
 *  4. **Una siembra que decide que CREAR y no solo que abrir**, y cuya idempotencia
 *     no se puede apoyar en «uno por asset», porque una maquina puede tener varios
 *     planes.
 *
 * **No estrena contexto de Spring**: mismo `@SpringBootTest` que las demas pruebas
 * de recorrido, sin propiedades propias ni `@TestConfiguration`. Cada combinacion
 * nueva reserva un pool entero que no se suelta mientras siga en cache, y eso ya
 * tumbo tres pruebas de otros hitos en la Fase 1. Los Hitos 3 y 4 anadieron cero y
 * aqui se hace lo mismo: los handlers se ejercitan **por la API de verdad** y el
 * recorrido nocturno se invoca a mano.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MaintenanceJourneyTest : SpringIntegrationTest() {

    @Autowired private lateinit var http: TestRestTemplate

    /**
     * El recorrido diario **se invoca a mano**, que es el mismo metodo que invoca
     * el `@Scheduled`: el programador esta apagado en toda la suite porque,
     * encendido, la pasada correria dentro de cualquier contexto de prueba y
     * purgaria hogares a mitad de otra.
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

        val closed = http.getJson("$CMMS/plans", home.accessToken)
        closed.statusCode.shouldBe(HttpStatus.FORBIDDEN)
        closed.body!!.shouldContain("MODULE_INACTIVE")

        // Y no solo la lista de planes: el gate cuelga del prefijo, asi que alcanza
        // a todo lo que hay debajo.
        http.getJson("$CMMS/machines", home.accessToken).statusCode.shouldBe(HttpStatus.FORBIDDEN)
        http.getJson("$CMMS/interventions", home.accessToken).statusCode.shouldBe(HttpStatus.FORBIDDEN)
        http.getJson("$CMMS/suppliers", home.accessToken).statusCode.shouldBe(HttpStatus.FORBIDDEN)

        home.activate()

        val opened = http.getJson("$CMMS/plans", home.accessToken)
        opened.statusCode.shouldBe(HttpStatus.OK)
        opened.body!!.shouldNotContain("MODULE_INACTIVE")
    }

    // -----------------------------------------------------------------------
    // El gate del bus: con el modulo apagado no se escribe NADA
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("con el modulo apagado, los tres handlers no escriben ni una fila")
    fun `un hogar sin el modulo no ve nada escrito`() {
        val home = http.registerHousehold()

        // Toda la actividad del core que CMMS escucha, con el modulo apagado: dar
        // de alta una maquina, adjuntarle su manual y darla de baja.
        val boiler = home.durable("Caldera")
        home.attachManual(boiler)
        http.deleteJson("/api/v1/assets/$boiler", home.accessToken).statusCode.shouldBe(HttpStatus.NO_CONTENT)

        // Ahora se enciende. La siembra recorre lo que **hay ahora**, y lo que hay
        // ahora es una maquina dada de baja: no hay nada que sembrar.
        home.activate()

        http.getJson("$CMMS/machines", home.accessToken).body!!.shouldContain("\"total\":0")
        http.getJson("$CMMS/plans", home.accessToken).body!!.shouldContain("\"total\":0")
    }

    @Test
    @DisplayName("y el hogar de al lado, con el modulo encendido, si los ve: no es que no lleguen")
    fun `el vecino con el modulo encendido si escribe`() {
        val off = http.registerHousehold()
        val on = http.registerHousehold()
        on.activate()

        // El mismo gesto en los dos hogares, a la vez y por la misma ruta.
        listOf(off, on).forEach { home -> home.durable("Lavadora") }

        // Es la comparacion la que dice algo: sin ella, cero maquinas podria
        // significar «el handler esta roto» en vez de «el gate funciona».
        http.getJson("$CMMS/machines", on.accessToken).body!!.shouldContain("Lavadora")
        http.getJson("$CMMS/machines", off.accessToken).statusCode.shouldBe(HttpStatus.FORBIDDEN)

        // Y al encenderlo despues, la siembra le da la ficha de lo que HAY --pero
        // no los eventos que se perdio, que aqui no dejaban nada mas.
        off.activate()
        http.getJson("$CMMS/machines", off.accessToken).body!!.shouldContain("Lavadora")
    }

    // -----------------------------------------------------------------------
    // La trampa: el agregado de DocumentAttached es el DOCUMENTO
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("el manual acaba en la MAQUINA y no en el identificador del documento")
    fun `el agregado de DocumentAttached se lee del payload`() {
        val home = http.registerHousehold()
        home.activate()
        val boiler = home.durable("Caldera de gas")

        val documentId = home.attachManual(boiler)

        // Si el handler hubiera leido `aggregateId` --el documento-- en lugar de
        // `payload.assetId`, esto **no habria fallado**: simplemente no habria
        // encontrado ninguna maquina con ese identificador, y la ficha se habria
        // quedado sin manual para siempre.
        val machine = http.getJson("$CMMS/machines/$boiler", home.accessToken)
        machine.statusCode.shouldBe(HttpStatus.OK)
        machine.body!!.shouldContain("\"manualDocumentId\":\"$documentId\"")
    }

    @Test
    @DisplayName("un manual de ARTICULO no enlaza nada: ese documento no cuelga de ninguna maquina")
    fun `un documento sin asset no escribe nada`() {
        val home = http.registerHousehold()
        home.activate()
        val boiler = home.durable("Termo eléctrico")
        val article = home.article("Filtro de agua")

        // `assetId` viaja **anulable** en el payload, y este es el caso que lo hace
        // nulo. Un handler que diera por hecho que siempre hay asset reventaria o,
        // peor, enlazaria el manual del filtro a lo que hubiera con ese
        // identificador.
        http.postJson(
            "/api/v1/documents",
            """{"articleId":"$article","type":"MANUAL","url":"https://example.test/filtro.pdf"}""",
            home.accessToken,
        ).statusCode.shouldBe(HttpStatus.CREATED)

        http.getJson("$CMMS/machines/$boiler", home.accessToken).body!!
            .shouldContain("\"manualDocumentId\":null")
    }

    @Test
    @DisplayName("una factura no es el manual: solo el tipo MANUAL se apunta")
    fun `el modulo elige que documento le importa`() {
        val home = http.registerHousehold()
        home.activate()
        val boiler = home.durable("Caldera mural")

        http.postJson(
            "/api/v1/documents",
            """{"assetId":"$boiler","type":"INVOICE","url":"https://example.test/factura.pdf"}""",
            home.accessToken,
        ).statusCode.shouldBe(HttpStatus.CREATED)

        http.getJson("$CMMS/machines/$boiler", home.accessToken).body!!
            .shouldContain("\"manualDocumentId\":null")
    }

    // -----------------------------------------------------------------------
    // La siembra: que crea, y que apagar y encender no duplique
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("encender sobre las maquinas que ya habia abre su ficha y NO crea ningun plan")
    fun `la siembra abre fichas y no inventa planes`() {
        val home = http.registerHousehold()
        home.durable("Caldera")
        home.durable("Coche")
        // Un consumible no entra: un paquete de arroz no se revisa.
        home.consumable("Arroz")

        home.activate()

        val machines = http.getJson("$CMMS/machines", home.accessToken).body!!
        machines.shouldContain("\"total\":2")
        machines.shouldContain("Caldera")
        machines.shouldNotContain("Arroz")
        // **Ningun plan.** Es la decision de este hito: por defecto ¿de que? Una
        // caldera pide revision anual y una silla no pide nada.
        machines.shouldContain("\"planCount\":0")
        http.getJson("$CMMS/plans", home.accessToken).body!!.shouldContain("\"total\":0")
    }

    @Test
    @DisplayName("apagar y volver a encender no duplica ni una ficha ni un plan")
    fun `la siembra es idempotente porque reactivar la ejecuta`() {
        val home = http.registerHousehold()
        val boiler = home.durable("Caldera de condensación")
        home.activate()
        home.plan(boiler, "Revisión anual")

        http.getJson("$CMMS/machines", home.accessToken).body!!.shouldContain("\"total\":1")

        // Apagar conserva; reactivar **vuelve a sembrar**. Sin la idempotencia por
        // indice unico, aqui habria dos fichas de la misma maquina.
        home.deactivate()
        home.activate()
        http.getJson("$CMMS/machines", home.accessToken).body!!.shouldContain("\"total\":1")
        // Y el plan que el hogar habia creado sigue ahi, uno y no dos: la siembra
        // no crea planes, y el indice unico de nombre por maquina cerraria la
        // puerta a cualquier via que quisiera hacerlo.
        http.getJson("$CMMS/plans", home.accessToken).body!!.shouldContain("\"total\":1")

        // Y una tercera vez, por si acaso la segunda fuese casualidad.
        home.deactivate()
        home.activate()
        http.getJson("$CMMS/machines", home.accessToken).body!!.shouldContain("\"total\":1")
        http.getJson("$CMMS/plans", home.accessToken).body!!.shouldContain("\"total\":1")
    }

    @Test
    @DisplayName("lo que llego mientras estaba apagado lo recupera la siembra al reactivar")
    fun `reactivar pone al dia lo que se perdio`() {
        val home = http.registerHousehold()
        home.activate()
        home.durable("Caldera")

        home.deactivate()
        // Esto no lo ve nadie: el modulo esta apagado.
        home.durable("Aire acondicionado")

        home.activate()

        val machines = http.getJson("$CMMS/machines", home.accessToken).body!!
        machines.shouldContain("\"total\":2")
        machines.shouldContain("Aire acondicionado")
    }

    @Test
    @DisplayName("dar de baja la maquina cancela sus planes y conserva su historico")
    fun `AssetDeactivated cancela y no borra`() {
        val home = http.registerHousehold()
        home.activate()
        val car = home.durable("Coche")
        val planId = home.plan(car, "ITV", intervalMonths = 24)
        home.intervene(car, planId, LocalDate.now().minusDays(1), "Pasada sin incidencias")

        http.deleteJson("/api/v1/assets/$car", home.accessToken).statusCode.shouldBe(HttpStatus.NO_CONTENT)

        // El plan deja de vigilarse...
        http.getJson("$CMMS/plans", home.accessToken).body!!.shouldContain("\"total\":0")
        http.getJson("$CMMS/plans?includeCancelled=true", home.accessToken).body!!.shouldContain("\"total\":1")
        // ...y el historico se queda: que el coche paso la ITV siguio siendo cierto
        // despues de venderlo.
        http.getJson("$CMMS/interventions", home.accessToken).body!!.shouldContain("Pasada sin incidencias")
    }

    // -----------------------------------------------------------------------
    // Los planes y sus reglas
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("un plan cuelga de un DURABLE vivo: ni de un consumible ni de uno dado de baja")
    fun `mantenimiento solo vigila lo duradero`() {
        val home = http.registerHousehold()
        home.activate()
        val rice = home.consumable("Arroz")

        val rejected = http.postJson(
            "$CMMS/plans",
            """{"assetId":"$rice","name":"Revisión","intervalMonths":12,"nextDueOn":"${inDays(30)}"}""",
            home.accessToken,
        )
        rejected.statusCode.shouldBe(HttpStatus.CONFLICT)
        rejected.body!!.shouldContain("MAINTENANCE_ASSET_NOT_ELIGIBLE")
    }

    @Test
    @DisplayName("dos planes vivos con el mismo nombre en la misma maquina chocan, sin acentos ni caja")
    fun `el nombre de un plan es unico por maquina`() {
        val home = http.registerHousehold()
        home.activate()
        val boiler = home.durable("Caldera")
        home.plan(boiler, "Revisión anual")

        val clash = http.postJson(
            "$CMMS/plans",
            """{"assetId":"$boiler","name":"revision ANUAL","intervalMonths":12,"nextDueOn":"${inDays(60)}"}""",
            home.accessToken,
        )
        clash.statusCode.shouldBe(HttpStatus.CONFLICT)
        clash.body!!.shouldContain("MAINTENANCE_PLAN_DUPLICATE")

        // Pero **varios planes distintos sobre la misma maquina son legitimos**, y
        // es justo lo que hace que «un plan por asset» no sirva como clave.
        http.postJson(
            "$CMMS/plans",
            """{"assetId":"$boiler","name":"Cambio de filtro","intervalMonths":3,"nextDueOn":"${inDays(20)}"}""",
            home.accessToken,
        ).statusCode.shouldBe(HttpStatus.CREATED)
    }

    @Test
    @DisplayName("cancelar libera el nombre, porque el indice es parcial")
    fun `un plan cancelado no ocupa su nombre para siempre`() {
        val home = http.registerHousehold()
        home.activate()
        val boiler = home.durable("Caldera")
        val planId = home.plan(boiler, "Revisión anual")

        http.deleteJson("$CMMS/plans/$planId", home.accessToken).statusCode.shouldBe(HttpStatus.NO_CONTENT)
        // Cancelar dos veces no es un error.
        http.deleteJson("$CMMS/plans/$planId", home.accessToken).statusCode.shouldBe(HttpStatus.NO_CONTENT)

        http.postJson(
            "$CMMS/plans",
            """{"assetId":"$boiler","name":"Revisión anual","intervalMonths":12,"nextDueOn":"${inDays(30)}"}""",
            home.accessToken,
        ).statusCode.shouldBe(HttpStatus.CREATED)
    }

    @Test
    @DisplayName("avisar con mas antelacion que el propio periodo deja el plan siempre a punto de tocar")
    fun `la antelacion cabe en el periodo`() {
        val home = http.registerHousehold()
        home.activate()
        val boiler = home.durable("Caldera")

        val impossible = http.postJson(
            "$CMMS/plans",
            """{"assetId":"$boiler","name":"Filtro","intervalMonths":3,"leadDays":200,"nextDueOn":"${inDays(30)}"}""",
            home.accessToken,
        )
        impossible.statusCode.shouldBe(HttpStatus.CONFLICT)
        impossible.body!!.shouldContain("MAINTENANCE_LEAD_EXCEEDS_INTERVAL")
    }

    @Test
    @DisplayName("un periodo fuera de rango es un 400: lo que falla es la forma de lo enviado")
    fun `el periodo esta acotado`() {
        val home = http.registerHousehold()
        home.activate()
        val boiler = home.durable("Caldera")

        http.postJson(
            "$CMMS/plans",
            """{"assetId":"$boiler","name":"Nunca","intervalMonths":0,"nextDueOn":"${inDays(30)}"}""",
            home.accessToken,
        ).statusCode.shouldBe(HttpStatus.BAD_REQUEST)
    }

    // -----------------------------------------------------------------------
    // El servicio tecnico, leido por el puerto de plataforma
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("con Proveedores apagado el selector responde 200 vacio, y un plan CON servicio se rechaza")
    fun `la degradacion la pone plataforma`() {
        val home = http.registerHousehold()
        home.activate()
        val boiler = home.durable("Caldera")

        // `200` con lista vacia y no el `403` que daria la ruta de Proveedores: la
        // operacion cuelga del prefijo de CMMS.
        val empty = http.getJson("$CMMS/suppliers", home.accessToken)
        empty.statusCode.shouldBe(HttpStatus.OK)
        empty.body!!.shouldBe("[]")

        val orphan = http.postJson(
            "$CMMS/plans",
            """{"assetId":"$boiler","name":"Revisión","intervalMonths":12,
                "nextDueOn":"${inDays(30)}","supplierId":"${java.util.UUID.randomUUID()}"}""",
            home.accessToken,
        )
        orphan.statusCode.shouldBe(HttpStatus.CONFLICT)
        orphan.body!!.shouldContain("MAINTENANCE_SUPPLIER_UNKNOWN")

        // Y **un plan sin servicio tecnico se crea igual**: llamar a alguien es un
        // adorno del plan y no su razon de ser.
        http.postJson(
            "$CMMS/plans",
            """{"assetId":"$boiler","name":"Revisión","intervalMonths":12,"nextDueOn":"${inDays(30)}"}""",
            home.accessToken,
        ).statusCode.shouldBe(HttpStatus.CREATED)
    }

    /**
     * **La garantia que Proveedores declaro por adelantado y para este caso**, y
     * que nadie habia ejercitado: Compras copia el nombre al crear la compra, asi
     * que nunca vuelve a preguntar. Aqui el plan guarda solo el identificador y
     * resuelve el nombre al leer, que es lo que obliga a que `find` funcione sobre
     * un contacto retirado y a que `search` no lo ofrezca.
     */
    @Test
    @DisplayName("un contacto RETIRADO sigue siendo legible por el plan, y deja de ofrecerse en el selector")
    fun `las dos mitades del puerto de dato maestro`() {
        val home = http.registerHousehold()
        home.activate()
        home.activateSuppliers()
        val boiler = home.durable("Caldera")

        val supplierId = http.postJson(
            "/api/v1/suppliers",
            """{"name":"Servicio Técnico Pérez","serviceCategory":"HEATING_COOLING","phone":"600123123"}""",
            home.accessToken,
        ).body!!.extract("id")

        val planId = home.plan(boiler, "Revisión anual", supplierId = supplierId)

        // Se ofrece, con su categoria dentro para que el cliente agrupe.
        val offered = http.getJson("$CMMS/suppliers", home.accessToken).body!!
        offered.shouldContain("Servicio Técnico Pérez")
        offered.shouldContain("HEATING_COOLING")

        http.deleteJson("/api/v1/suppliers/$supplierId", home.accessToken)
            .statusCode.shouldBe(HttpStatus.NO_CONTENT)

        // 1. `search` **deja de ofrecerlo**: elegir a quien ya no se llama seria un
        //    error nuevo.
        http.getJson("$CMMS/suppliers", home.accessToken).body!!
            .shouldNotContain("Servicio Técnico Pérez")

        // 2. Y `find` **sigue leyendolo**: un plan que apunte a quien ya no se
        //    llama tiene que poder decir a quien apuntaba. Es la mitad que la ficha
        //    de Proveedores se comprometio a cumplir «para este caso exacto», y sin
        //    esta prueba nadie la habria comprobado nunca.
        http.getJson("$CMMS/plans/$planId", home.accessToken).body!!
            .shouldContain("Servicio Técnico Pérez")
    }

    // -----------------------------------------------------------------------
    // La intervencion: lo que rearma el ciclo
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("registrar una intervencion avanza la proxima fecha desde LO QUE SE HIZO")
    fun `la intervencion avanza el plan`() {
        val home = http.registerHousehold()
        home.activate()
        val boiler = home.durable("Caldera")
        val planId = home.plan(boiler, "Revisión anual", intervalMonths = 12, nextDueOn = inDays(-40))

        val done = LocalDate.now().minusDays(2)
        home.intervene(boiler, planId, done, "Revisada y limpiada")

        val plan = http.getJson("$CMMS/plans/$planId", home.accessToken).body!!
        plan.shouldContain("\"nextDueOn\":\"${done.plusMonths(12)}\"")
        plan.shouldContain("\"lastPerformedOn\":\"$done\"")
    }

    @Test
    @DisplayName("una correctiva no cuelga de ningun plan y no avanza nada")
    fun `una averia es historia y no cumple una regla`() {
        val home = http.registerHousehold()
        home.activate()
        val boiler = home.durable("Caldera")
        val planId = home.plan(boiler, "Revisión anual", nextDueOn = inDays(90))

        http.postJson(
            "$CMMS/interventions",
            """{"assetId":"$boiler","kind":"CORRECTIVE","performedOn":"${LocalDate.now()}",
                "summary":"Se cambió la válvula"}""",
            home.accessToken,
        ).statusCode.shouldBe(HttpStatus.CREATED)

        http.getJson("$CMMS/plans/$planId", home.accessToken).body!!
            .shouldContain("\"nextDueOn\":\"${inDays(90)}\"")
        http.getJson("$CMMS/interventions?assetId=$boiler", home.accessToken).body!!
            .shouldContain("\"kind\":\"CORRECTIVE\"")
    }

    @Test
    @DisplayName("una intervencion del futuro no vale: adelantaria la fecha sobre algo que no ha pasado")
    fun `no se registra lo que no ha ocurrido`() {
        val home = http.registerHousehold()
        home.activate()
        val boiler = home.durable("Caldera")

        http.postJson(
            "$CMMS/interventions",
            """{"assetId":"$boiler","kind":"CORRECTIVE","performedOn":"${inDays(1)}","summary":"Mañana"}""",
            home.accessToken,
        ).statusCode.shouldBe(HttpStatus.BAD_REQUEST)
    }

    @Test
    @DisplayName("un plan cancelado no admite intervenciones nuevas")
    fun `lo que ya no se vigila no se rearma`() {
        val home = http.registerHousehold()
        home.activate()
        val boiler = home.durable("Caldera")
        val planId = home.plan(boiler, "Revisión anual")
        http.deleteJson("$CMMS/plans/$planId", home.accessToken).statusCode.shouldBe(HttpStatus.NO_CONTENT)

        val rejected = http.postJson(
            "$CMMS/interventions",
            """{"assetId":"$boiler","planId":"$planId","kind":"PREVENTIVE",
                "performedOn":"${LocalDate.now()}","summary":"Tarde"}""",
            home.accessToken,
        )
        rejected.statusCode.shouldBe(HttpStatus.CONFLICT)
        rejected.body!!.shouldContain("MAINTENANCE_PLAN_CANCELLED")
    }

    @Test
    @DisplayName("la intervencion COPIA el nombre del servicio tecnico, al reves que el plan")
    fun `una intervencion es historia y el plan es una regla viva`() {
        val home = http.registerHousehold()
        home.activate()
        home.activateSuppliers()
        val boiler = home.durable("Caldera")
        val supplierId = http.postJson(
            "/api/v1/suppliers",
            """{"name":"Calderas Ruiz","serviceCategory":"HEATING_COOLING","phone":"600999888"}""",
            home.accessToken,
        ).body!!.extract("id")

        http.postJson(
            "$CMMS/interventions",
            """{"assetId":"$boiler","kind":"CORRECTIVE","performedOn":"${LocalDate.now()}",
                "summary":"Cambio de bomba","supplierId":"$supplierId"}""",
            home.accessToken,
        ).statusCode.shouldBe(HttpStatus.CREATED)

        // Apagar Proveedores no borra el nombre de aquel dia: la intervencion lo
        // lleva dentro, que es la misma regla que la ADR-011 da para el texto de un
        // aviso.
        http.deleteJson("/api/v1/modules/SUPPLIERS/activation", home.accessToken)
            .statusCode.shouldBe(HttpStatus.OK)
        http.getJson("$CMMS/interventions?assetId=$boiler", home.accessToken).body!!
            .shouldContain("Calderas Ruiz")
    }

    // -----------------------------------------------------------------------
    // La ficha de una maquina
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("el manual se puede corregir a mano, porque el handler deja el ultimo que llegue")
    fun `la eleccion del manual tiene correccion`() {
        val home = http.registerHousehold()
        home.activate()
        val boiler = home.durable("Caldera")
        val first = home.attachManual(boiler, "https://example.test/manual-viejo.pdf")
        val second = home.attachManual(boiler, "https://example.test/manual-nuevo.pdf")

        // Gana el ultimo, que es la semantica normal de «el manual de esto».
        http.getJson("$CMMS/machines/$boiler", home.accessToken).body!!
            .shouldContain("\"manualDocumentId\":\"$second\"")

        // Y se corrige, que es para lo que existe el `PATCH` de la ficha.
        val fixed = http.patchJson(
            "$CMMS/machines/$boiler",
            """{"manualDocumentId":"$first","notes":"La de la cocina"}""",
            home.accessToken,
        )
        fixed.statusCode.shouldBe(HttpStatus.OK)
        fixed.body!!.shouldContain("\"manualDocumentId\":\"$first\"")
        fixed.body!!.shouldContain("La de la cocina")
    }

    @Test
    @DisplayName("un documento de otra maquina no se puede colgar de esta")
    fun `el manual tiene que ser suyo`() {
        val home = http.registerHousehold()
        home.activate()
        val boiler = home.durable("Caldera")
        val car = home.durable("Coche")
        val carManual = home.attachManual(car)

        http.patchJson("$CMMS/machines/$boiler", """{"manualDocumentId":"$carManual"}""", home.accessToken)
            .statusCode.shouldBe(HttpStatus.BAD_REQUEST)
    }

    // -----------------------------------------------------------------------
    // Los avisos, y el rearme que Warehouse no tuvo que resolver
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("lo que toca pronto avisa, y el resumen se lee del Mailpit de verdad")
    fun `el aviso de revision llega al correo real`() {
        val home = http.registerHousehold(mailpit)
        home.activate()
        val boiler = home.durable("Caldera de gas")
        home.plan(boiler, "Revisión anual", nextDueOn = inDays(5))

        mailpit.clear()
        sweep.run()

        // En la bandeja...
        http.getJson("/api/v1/notices", home.accessToken).body!!.shouldContain("MAINTENANCE_DUE_SOON")

        // ...y en el buzon de verdad, que es lo que la ADR-009 pide demostrar.
        val digest = mailpit.awaitMessageTo(home.email)
        digest.body.shouldContain("Toca revisar: Caldera de gas")
        digest.body.shouldContain("/avisos")
    }

    @Test
    @DisplayName("el mismo plan NO vuelve a avisar la noche siguiente")
    fun `un aviso por condicion y no uno por noche`() {
        val home = http.registerHousehold(mailpit)
        home.activate()
        val boiler = home.durable("Termo")
        home.plan(boiler, "Revisión anual", nextDueOn = inDays(3))

        sweep.run()
        countNotices(home.accessToken, "MAINTENANCE_DUE_SOON").shouldBe(1)

        sweep.run()
        sweep.run()
        countNotices(home.accessToken, "MAINTENANCE_DUE_SOON").shouldBe(1)
    }

    @Test
    @DisplayName("un plan ya vencido avisa de que se pasó, que es una noticia distinta")
    fun `las dos fases de una fecha prevista`() {
        val home = http.registerHousehold(mailpit)
        home.activate()
        val boiler = home.durable("Caldera vieja")
        home.plan(boiler, "Revisión anual", nextDueOn = inDays(-10))

        sweep.run()

        val notices = http.getJson("/api/v1/notices", home.accessToken).body!!
        notices.shouldContain("MAINTENANCE_OVERDUE")
        // Y no las dos a la vez: lo que ya se pasó no «toca pronto».
        notices.shouldNotContain("MAINTENANCE_DUE_SOON")
    }

    /**
     * **La vuelta que Warehouse no tuvo que dar.** Alli el rearme era reponer por
     * encima del minimo, un hecho que ocurre solo; aqui es registrar la
     * intervencion, que es un gesto de una persona. Y la fecha nueva vuelve a
     * entrar en ventana, asi que el ciclo se cierra entero dentro de la prueba.
     */
    @Test
    @DisplayName("registrar la intervencion REARMA el aviso: el ciclo siguiente vuelve a decirlo")
    fun `el aviso se rearma al registrar la intervencion`() {
        val home = http.registerHousehold(mailpit)
        home.activate()
        val boiler = home.durable("Caldera de la cocina")
        // Un plan mensual con quince dias de antelacion, que toca pasado mañana.
        val planId = home.plan(boiler, "Purga mensual", intervalMonths = 1, nextDueOn = inDays(2))

        sweep.run()
        countNotices(home.accessToken, "MAINTENANCE_DUE_SOON").shouldBe(1)
        sweep.run()
        countNotices(home.accessToken, "MAINTENANCE_DUE_SOON").shouldBe(1)

        // Se hace. La proxima pasa a ser dentro de un mes, y el aviso se rearma
        // **sin que nadie limpie ninguna marca**: `notifiedFor` deja de coincidir.
        home.intervene(boiler, planId, LocalDate.now(), "Purgada")

        // Todavia fuera de la ventana --queda un mes--, asi que no dice nada.
        sweep.run()
        countNotices(home.accessToken, "MAINTENANCE_DUE_SOON").shouldBe(1)

        // Y al acercarse la nueva fecha, vuelve a avisar. Sin rearme, un plan
        // avisaria **una sola vez en toda su vida**, que es el defecto que esta
        // prueba existe para impedir.
        http.patchJson("$CMMS/plans/$planId", """{"nextDueOn":"${inDays(4)}"}""", home.accessToken)
            .statusCode.shouldBe(HttpStatus.OK)
        sweep.run()
        countNotices(home.accessToken, "MAINTENANCE_DUE_SOON").shouldBe(2)
    }

    @Test
    @DisplayName("un hogar con Mantenimiento apagado no recibe sus avisos, y el de al lado si")
    fun `el recorrido se salta el modulo apagado`() {
        val on = http.registerHousehold(mailpit)
        val off = http.registerHousehold(mailpit)
        on.activate()

        // El mismo plan a punto de tocar en los dos hogares. En el apagado hay que
        // encender para poder escribirlo --su ruta esta cerrada por el gate-- y
        // volver a apagar, que es lo que deja el dato dentro con el modulo off.
        listOf(on to true, off to false).forEach { (home, keepOn) ->
            if (!keepOn) home.activate()
            val boiler = home.durable("Caldera")
            home.plan(boiler, "Revisión anual", nextDueOn = inDays(3))
            if (!keepOn) home.deactivate()
        }

        sweep.run()

        // Es la comparacion la que dice algo: sin el hogar encendido al lado, «no
        // hay aviso» podria significar que la comprobacion esta rota.
        http.getJson("/api/v1/notices", on.accessToken).body!!.shouldContain("MAINTENANCE_DUE_SOON")
        http.getJson("/api/v1/notices", off.accessToken).body!!.shouldNotContain("MAINTENANCE_DUE_SOON")
    }

    /** Cuantos avisos de esa clase tiene el hogar. La bandeja no filtra por `kind`. */
    private fun countNotices(accessToken: String, kind: String): Int =
        Regex(""""kind":"$kind"""")
            .findAll(http.getJson("/api/v1/notices?size=200", accessToken).body.orEmpty())
            .count()

    // -----------------------------------------------------------------------
    // Apoyo
    // -----------------------------------------------------------------------

    private fun TestHousehold.activate() {
        http.postJson("/api/v1/modules/MAINTENANCE/activation", "", accessToken)
            .statusCode.shouldBe(HttpStatus.OK)
    }

    private fun TestHousehold.deactivate() {
        http.deleteJson("/api/v1/modules/MAINTENANCE/activation", accessToken)
            .statusCode.shouldBe(HttpStatus.OK)
    }

    private fun TestHousehold.activateSuppliers() {
        http.postJson("/api/v1/modules/SUPPLIERS/activation", "", accessToken)
            .statusCode.shouldBe(HttpStatus.OK)
    }

    private fun TestHousehold.durable(name: String): String = http.postJson(
        "/api/v1/assets",
        """{"name":"$name","type":"DURABLE","categoryId":"${http.seededCategory(accessToken, "Herramientas")}"}""",
        accessToken,
    ).body!!.extract("id")

    private fun TestHousehold.article(name: String): String = http.postJson(
        "/api/v1/articles",
        """{"name":"$name","categoryId":"${http.seededCategory(accessToken, "Alimentación")}","unit":"UNIT"}""",
        accessToken,
    ).body!!.extract("id")

    private fun TestHousehold.consumable(name: String): String {
        val locationId = http.postJson(
            "/api/v1/locations",
            """{"name":"Despensa","type":"ROOM"}""",
            accessToken,
        ).body!!.extract("id")

        return http.postJson(
            "/api/v1/assets/intake",
            """{"articleId":"${article(name)}","ownerId":"$memberId","quantity":1,
                "location":{"type":"LOCATION","id":"$locationId"}}""",
            accessToken,
        ).body!!.extract("id")
    }

    private fun TestHousehold.attachManual(
        assetId: String,
        url: String = "https://example.test/manual.pdf",
    ): String = http.postJson(
        "/api/v1/documents",
        """{"assetId":"$assetId","type":"MANUAL","url":"$url"}""",
        accessToken,
    ).body!!.extract("id")

    private fun TestHousehold.plan(
        assetId: String,
        name: String,
        intervalMonths: Int = 12,
        nextDueOn: String = inDays(300),
        supplierId: String? = null,
    ): String {
        val supplier = supplierId?.let { ""","supplierId":"$it"""" }.orEmpty()
        val created = http.postJson(
            "$CMMS/plans",
            """{"assetId":"$assetId","name":"$name","intervalMonths":$intervalMonths,
                "nextDueOn":"$nextDueOn"$supplier}""",
            accessToken,
        )
        created.statusCode.shouldBe(HttpStatus.CREATED)
        return created.body!!.extract("id")
    }

    private fun TestHousehold.intervene(assetId: String, planId: String?, on: LocalDate, summary: String) {
        val plan = planId?.let { ""","planId":"$it"""" }.orEmpty()
        http.postJson(
            "$CMMS/interventions",
            """{"assetId":"$assetId","kind":"PREVENTIVE","performedOn":"$on","summary":"$summary"$plan}""",
            accessToken,
        ).statusCode.shouldBe(HttpStatus.CREATED)
    }

    /**
     * Una fecha relativa a hoy y no fija: una fecha escrita a mano caduca
     * --literalmente-- y convierte la prueba en una que empieza a fallar sola un
     * dia cualquiera.
     */
    private fun inDays(days: Long): String = LocalDate.now().plusDays(days).toString()

    private companion object {
        const val CMMS = "/api/v1/maintenance"
    }
}
