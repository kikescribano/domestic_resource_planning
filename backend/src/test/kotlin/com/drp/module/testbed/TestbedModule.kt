package com.drp.module.testbed

import com.drp.platform.event.DomainEvent
import com.drp.platform.module.ModuleActivation
import com.drp.platform.module.ModuleDescriptor
import com.drp.platform.module.ModuleEventHandler
import com.drp.platform.module.ModuleSeeder
import com.drp.platform.notice.NoticeDraft
import com.drp.platform.schedule.CheckOwner
import com.drp.platform.schedule.ScheduledCheck
import com.drp.platform.tenant.TenantContext
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Un modulo de mentira, y el unico testigo que tiene el Hito 0.
 *
 * El hito construye el mecanismo de activacion y **no tiene ningun modulo real
 * que encender**: los cuatro del catalogo son declaraciones sin dominio hasta su
 * hito. Esperar al Hito 2 para comprobar el gate dejaria el mecanismo entero sin
 * prueba propia durante toda una sesion, asi que aqui hay un modulo minimo con
 * las tres cosas que el gate atraviesa: **una tabla, una ruta y un handler**.
 *
 * **Vive en el arbol de pruebas y no se despliega.** Sobrevive a la fase entera
 * como prueba de regresion del mecanismo: cuando un modulo de verdad falle al
 * activarse, esto es lo que dice si lo roto es el modulo o la activacion.
 *
 * El Hito 1 le anade **una comprobacion periodica**, que es la cuarta cosa que
 * el mecanismo atraviesa: es lo que deja demostrar que el recorrido diario se
 * salta los hogares con el modulo apagado sin esperar a que exista Warehouse.
 *
 * **Por que la condicion sobre una propiedad**, y no un `@TestConfiguration` que
 * se importe. El escaneo de componentes arranca en `com.drp` y alcanza al
 * classpath de pruebas, asi que un `@RestController` aqui aparece en **todos**
 * los contextos de la suite quiera uno o no --y con el, un handler mas
 * escuchando todos los eventos del core y abriendo su transaccion en cada uno,
 * justo donde `EventBusSweepTest` mide comportamiento transaccional fino--.
 * Declarar los beans sin anotacion de componente tampoco vale: desde Spring
 * Framework 6 el mapeo de peticiones **solo reconoce como manejador lo anotado
 * con `@Controller`**, y un `@RequestMapping` a secas se registra como bean y no
 * publica ninguna ruta. Medido: el bean existia y la ruta respondia `404`.
 *
 * Asi que el modulo se escanea siempre y se registra solo cuando alguien lo
 * pide, que es lo que hace la clase de prueba con
 * `@SpringBootTest(properties = ["$ENABLED=true"])`.
 */
@Configuration
@ConditionalOnProperty(name = [TestbedModule.ENABLED], havingValue = "true")
class TestbedModule {

    @Bean
    fun testbedModuleDescriptor() = ModuleDescriptor(
        key = KEY,
        name = "Módulo de prueba",
        description = "El testigo del mecanismo de activación. No existe fuera de las pruebas.",
        routePrefix = "/api/v1/testbed",
    )

    @Bean
    fun testbedNotes(jdbc: JdbcTemplate, tenantContext: TenantContext) = TestbedNotes(jdbc, tenantContext)

    @Bean
    fun testbedSeeder(notes: TestbedNotes, jdbc: JdbcTemplate) = TestbedSeeder(notes, jdbc)

    @Bean
    fun testbedCheck(notes: TestbedNotes) = TestbedCheck(notes)

    @Bean
    fun testbedHandler(
        notes: TestbedNotes,
        activation: ModuleActivation,
        tenantContext: TenantContext,
        transactionManager: PlatformTransactionManager,
    ) = TestbedHandler(notes, activation, tenantContext, transactionManager)

    companion object {
        const val KEY = "TESTBED"
        const val ROUTE = "/api/v1/testbed/notes"
        const val ENABLED = "drp.testbed.enabled"
    }
}

/**
 * Las notas del modulo, en su propia tabla y con su propia politica de RLS.
 *
 * Con propagacion normal a proposito, que es lo que puede escribir un modulo sin
 * pensar: cuando esto corre desde un evento, quien ya ha abierto la transaccion
 * `REQUIRES_NEW` es [ModuleEventHandler], y unirse a **esa** es lo correcto.
 * Abrir aqui otra dejaria ademas la siembra fuera de la transaccion de la
 * activacion, con lo que una activacion que fallara dejaria datos sembrados de un
 * modulo que no llego a encenderse.
 *
 * `open` explicito y no por el plugin de Kotlin: `allopen` abre las clases
 * **anotadas**, y aqui la anotacion esta en los metodos. Sin esto, Spring no
 * puede tejer el proxy transaccional sobre una clase final de Kotlin y el
 * contexto ni siquiera arranca.
 */
open class TestbedNotes(private val jdbc: JdbcTemplate, private val tenantContext: TenantContext) {

    @Transactional
    open fun write(text: String): UUID {
        val householdId = checkNotNull(tenantContext.currentHousehold()) { "El modulo de prueba exige hogar" }
        val id = UUID.randomUUID()
        jdbc.update(
            "INSERT INTO module_testbed.notes (id, household_id, text) VALUES (?, ?, ?)",
            id,
            householdId,
            text,
        )
        return id
    }

    /** Sin `WHERE household_id`: lo pone la politica, igual que en el core. */
    @Transactional(readOnly = true)
    open fun all(): List<String> =
        jdbc.queryForList("SELECT text FROM module_testbed.notes ORDER BY created_at, text", String::class.java)
}

/**
 * La ruta del modulo. Cuelga de su prefijo, asi que el gate la cierra entera
 * cuando el hogar no lo tiene activo, y este controlador no sabe nada de eso.
 */
@RestController
@RequestMapping("/api/v1/testbed")
@ConditionalOnProperty(name = [TestbedModule.ENABLED], havingValue = "true")
class TestbedController(private val notes: TestbedNotes) {

    @GetMapping("/notes")
    fun list(): Map<String, List<String>> = mapOf("items" to notes.all())

    @PostMapping("/notes")
    fun add(@RequestParam text: String): Map<String, String> = mapOf("id" to notes.write(text).toString())
}

/**
 * La siembra al activarse, **desde el estado actual del core y no reproduciendo
 * eventos**.
 *
 * Cuenta las ubicaciones que el hogar ya tiene, que es lo mismo que hara
 * Warehouse con las existencias y CMMS con los `DURABLE`. Lee la tabla del core
 * por SQL y no importando nada suyo: un modulo puede leer el estado del core, y
 * lo que ninguna regla admite es la direccion contraria.
 */
class TestbedSeeder(private val notes: TestbedNotes, private val jdbc: JdbcTemplate) : ModuleSeeder {

    override val moduleKey: String = TestbedModule.KEY

    override fun seed() {
        // Sin `WHERE household_id`: la politica del core ya deja visibles solo
        // las del hogar que se esta activando.
        val locations = jdbc.queryForObject("SELECT count(*) FROM locations", Long::class.java) ?: 0
        notes.write("sembrado: $locations ubicaciones")
    }
}

/**
 * El handler. Extiende [ModuleEventHandler], asi que no hace nada cuando el
 * modulo esta apagado **para el hogar de ese evento**, y no tiene que acordarse
 * de comprobarlo ni de abrir su transaccion.
 */
class TestbedHandler(
    private val notes: TestbedNotes,
    activation: ModuleActivation,
    tenantContext: TenantContext,
    transactionManager: PlatformTransactionManager,
) : ModuleEventHandler(TestbedModule.KEY, "TestbedHandler", activation, tenantContext, transactionManager) {

    override fun handleActive(event: DomainEvent) {
        if (event.type != "LocationCreated") return
        notes.write("evento: ${event.payload["name"]}")
    }
}

/**
 * La comprobacion periodica del modulo de prueba.
 *
 * Declara `CheckOwner.Module`, asi que **el recorrido diario solo la ejecuta en
 * los hogares que tengan el modulo encendido**. Es el testigo de esa regla: deja
 * dos rastros distintos --una nota en su tabla y un aviso en la bandeja del
 * hogar-- para que la prueba pueda distinguir «no corrio» de «corrio y no
 * aviso».
 *
 * No es idempotente a proposito, al contrario que las tres del core: escribe una
 * nota y un aviso cada vez que pasa, que es justo lo que permite contar pasadas.
 */
class TestbedCheck(private val notes: TestbedNotes) : ScheduledCheck {

    override val name: String = "TestbedCheck"
    override val owner: CheckOwner = CheckOwner.Module(TestbedModule.KEY)

    override fun check(): List<NoticeDraft> {
        notes.write("comprobado")
        return listOf(
            NoticeDraft(
                kind = "TESTBED_CHECK",
                title = "El módulo de prueba pasó por aquí",
                body = "Lo escribe su comprobación periódica, que solo corre donde el módulo está encendido.",
            ),
        )
    }
}
