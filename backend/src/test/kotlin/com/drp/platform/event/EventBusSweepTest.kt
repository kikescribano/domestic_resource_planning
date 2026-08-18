package com.drp.platform.event

import com.drp.core.application.event.CoreEvents
import com.drp.platform.event.IdempotentEventHandler
import com.drp.platform.event.EventBus
import com.drp.core.application.port.HouseholdRepository
import com.drp.platform.tenant.TenantContext
import com.drp.platform.event.DomainEvent
import com.drp.test.DrpPostgres
import com.drp.test.SeededHousehold
import com.drp.test.SpringIntegrationTest
import com.drp.test.count
import com.drp.test.seedHousehold
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Barrido de verificacion del bus: no lee lo que las clases prometen, intenta
 * romperlo.
 *
 * Cada prueba **afirma la promesa tal y como esta escrita**, de forma que un
 * fallo aqui no es un fallo de la prueba sino la prueba de que la promesa no se
 * sostiene. Todas ademas imprimen lo medido con el prefijo `[SWEEP]`, que es lo
 * que el informe cita.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EventBusSweepTest : SpringIntegrationTest() {

    @Autowired private lateinit var events: CoreEvents
    @Autowired private lateinit var bus: EventBus
    @Autowired private lateinit var tenantContext: TenantContext
    @Autowired private lateinit var transactions: TransactionTemplate
    @Autowired private lateinit var jdbc: JdbcTemplate

    @Autowired private lateinit var racer: RacingHandler
    @Autowired private lateinit var saboteurState: SaboteurState
    @Autowired private lateinit var trace: Trace
    @Autowired private lateinit var probe: HouseholdProbe
    @Autowired private lateinit var recorder: SweepRecorder
    @Autowired private lateinit var republisher: RepublishingHandler
    @Autowired private lateinit var deriver: DerivingHandler
    @Autowired private lateinit var sharedInstanceHandler: SharedInstanceHandler
    @Autowired private lateinit var twins: Twins

    private lateinit var seeded: SeededHousehold

    @BeforeAll
    fun seed() {
        DrpPostgres.instance.ownerConnection().use { seeded = it.seedHousehold("Hogar del barrido") }
    }

    @BeforeEach
    fun silenceEveryHandler() {
        racer.reset()
        saboteurState.reset()
        trace.reset()
        probe.reset()
        recorder.reset()
        republisher.reset()
        deriver.reset()
        sharedInstanceHandler.reset()
        twins.reset()
    }

    // ------------------------------------------------------------------
    // 1. La guarda de idempotencia, con varios hilos a la vez
    // ------------------------------------------------------------------

    /**
     * La guarda comprueba y **luego** marca, con el cuerpo del handler en medio.
     * Entre las dos operaciones no hay exclusion mutua: `@Synchronized` protege
     * cada una por separado, no la secuencia.
     */
    @Test
    @DisplayName("el mismo eventId entregado a la vez desde varios hilos se procesa una sola vez")
    fun `la guarda aguanta la concurrencia`() {
        racer.enabled = true
        val event = anEvent("SweepRace")
        val threads = 8
        val ready = CountDownLatch(threads)
        val go = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(threads)

        repeat(threads) {
            pool.submit {
                ready.countDown()
                go.await()
                bus.publish(event)
            }
        }
        ready.await(30, TimeUnit.SECONDS)
        go.countDown()
        pool.shutdown()
        pool.awaitTermination(60, TimeUnit.SECONDS)

        println(
            "[SWEEP] concurrencia: hilos=$threads ejecuciones=${racer.executions.get()} " +
                "pico simultaneo dentro de handle=${racer.peak.get()}",
        )
        racer.executions.get().shouldBe(1)
    }

    /**
     * El mismo agujero sin hilos: un handler que republica **el mismo evento**
     * desde dentro de `handle` vuelve a entrar antes de que la guarda haya
     * marcado nada.
     */
    @Test
    @DisplayName("reentrar con el mismo eventId desde dentro del propio handler tampoco lo duplica")
    fun `la guarda aguanta la reentrada`() {
        republisher.enabled = true
        val event = anEvent("SweepReentry")

        bus.publish(event)

        println(
            "[SWEEP] reentrada: ejecuciones=${republisher.executions.get()} " +
                "profundidad maxima=${republisher.maxDepth.get()} (tope de seguridad=${RepublishingHandler.LIMIT})",
        )
        republisher.executions.get().shouldBe(1)
    }

    // ------------------------------------------------------------------
    // 2. El tope de la guarda
    // ------------------------------------------------------------------

    @Test
    @DisplayName("pasado el tope, la guarda olvida lo mas antiguo y vuelve a procesarlo")
    fun `el tope de la guarda es de diez mil`() {
        val atCapacity = reprocessedAfterDistinctEvents(10_000)
        val overCapacity = reprocessedAfterDistinctEvents(10_001)
        val wellOver = reprocessedAfterDistinctEvents(20_000)

        println(
            "[SWEEP] tope: con 10000 eventos distintos el primero se reprocesa=$atCapacity; " +
                "con 10001=$overCapacity; con 20000=$wellOver",
        )
        atCapacity.shouldBe(false)
        overCapacity.shouldBe(true)
    }

    /**
     * El `LinkedHashMap` de la guarda se construye con `accessOrder` a false, asi
     * que recordar un evento **no lo rejuvenece**: se olvida el mas antiguo en
     * entrar, no el mas antiguo en usarse. La diferencia importa el dia que haya
     * outbox, porque un evento que se reentrega a menudo se olvidara igual.
     */
    @Test
    @DisplayName("usar un evento recordado no lo rejuvenece: el olvido va por antiguedad de insercion")
    fun `el olvido va por insercion y no por uso`() {
        val handler = CountingHandler()
        val first = anEvent("SweepAccessOrder")
        handler.receive(first)
        repeat(9_999) { handler.receive(anEvent("SweepAccessOrder")) }

        // Se usa el primero --se descarta por duplicado-- justo antes de meter
        // uno mas. Con orden de acceso, ese uso lo salvaria del desalojo.
        handler.receive(first)
        val before = handler.executions
        handler.receive(anEvent("SweepAccessOrder"))
        handler.receive(first)
        val forgotten = handler.executions > before + 1

        println("[SWEEP] olvido: tras usarlo y meter uno mas, el primero se reprocesa=$forgotten")
        forgotten.shouldBe(true)
    }

    /**
     * Entrega [total] eventos distintos --el primero incluido-- y reentrega el
     * primero. Devuelve si el handler volvio a ejecutar el cuerpo, es decir, si
     * la guarda se habia olvidado de el.
     *
     * Va por llamada directa a `receive`, sin Spring: lo que se mide es la
     * capacidad del conjunto, y meter diez mil publicaciones por el difusor solo
     * anadiria minutos.
     */
    private fun reprocessedAfterDistinctEvents(total: Int): Boolean {
        val handler = CountingHandler()
        val first = anEvent("SweepCapacity")
        handler.receive(first)
        repeat(total - 1) { handler.receive(anEvent("SweepCapacity")) }
        val before = handler.executions
        handler.receive(first)
        return handler.executions > before
    }

    // ------------------------------------------------------------------
    // 3. Un handler que se une a la transaccion del core y falla
    // ------------------------------------------------------------------

    /**
     * El tercer camino, el que no estaba probado: un modulo que se registra con
     * `@EventListener` **y ademas anota `@Transactional`**. El interceptor se une
     * a la transaccion del core y, al fallar, la marca `rollbackOnly`. El `catch`
     * de [SpringEventBus] traga la excepcion, pero la marca sigue puesta y el
     * commit revienta despues, cuando ya no hay nadie mirando.
     */
    @Test
    @DisplayName("un handler transaccional que revienta SI deshace la transaccion del core")
    fun `el fallo de un handler transaccional SI alcanza al core`() {
        saboteurState.enabled = true
        // Una escritura de verdad dentro de la transaccion del core, para ver si
        // sobrevive: sin ella la prueba solo mediria la excepcion, no el dano.
        val categoryId = UUID.randomUUID()

        val outcome = runCatching {
            tenantContext.runAs(seeded.householdId) {
                transactions.execute {
                    jdbc.update(
                        "INSERT INTO categories (id, household_id, name, created_by) VALUES (?, ?, ?, ?)",
                        categoryId,
                        seeded.householdId,
                        "Categoria del barrido",
                        seeded.memberId,
                    )
                    events.assetDeactivated(UUID.randomUUID())
                    "el core termino"
                }
            }
        }

        val failure = outcome.exceptionOrNull()
        val survived = DrpPostgres.instance.ownerConnection().use {
            it.count("SELECT count(*) FROM categories WHERE id = ?", categoryId)
        }
        println(
            "[SWEEP] handler transaccional: intentos=${saboteurState.attempts.get()} " +
                "resultado=${failure?.let { "${it::class.java.simpleName}: ${it.message}" } ?: outcome.getOrNull()} " +
                "filas del core que sobreviven=$survived",
        )
        // MEDIDO, y es lo contrario de lo que uno esperaria: el handler que se
        // une a la transaccion del core y falla **si** se lo lleva por delante.
        // El interceptor la marca rollbackOnly, el fallo aparece al CERRARLA
        // --no al publicar-- y para entonces el catch del adaptador ya ha
        // vuelto. Es la unica forma conocida de que un modulo tumbe al core, y
        // la unica defensa es no unirse: REQUIRES_NEW siempre.
        //
        // Se deja escrito como esta --y no como nos gustaria-- porque un
        // comentario que prometa lo contrario es justo la clase de afirmacion
        // falsa que estos barridos existen para cazar.
        outcome.isSuccess.shouldBe(false)
        failure.shouldNotBeNull()
        failure!!::class.java.simpleName.shouldBe("UnexpectedRollbackException")
        survived.shouldBe(0)
    }

    // ------------------------------------------------------------------
    // 4. Un handler que falla y los que van detras
    // ------------------------------------------------------------------

    /**
     * Los tres se declaran como beans en el orden a, b, c y llevan `@Order` **al
     * reves** --3, 2, 1--, de forma que el rastro dice cual de las dos cosas
     * manda: si sale `c, b, a` Spring respeta `@Order`; si sale `a, b, c` lo
     * ignora y lo que ordena es el registro.
     */
    @Test
    @DisplayName("con la clase base, el fallo del de en medio no deja sin evento al que va detras")
    fun `los handlers aislados no se estorban`() {
        trace.isolatedEnabled = true

        bus.publish(anEvent("SweepIsolatedOrder"))

        println("[SWEEP] clase base, declarados a-b-c con @Order 3-2-1: ${trace.steps}")
        trace.steps.size.shouldBe(3)
    }

    @Test
    @DisplayName("con @EventListener a pelo, el fallo del de en medio tampoco deja sin evento al que va detras")
    fun `los listeners a pelo no se estorban`() {
        trace.rawEnabled = true

        bus.publish(anEvent("SweepRawOrder"))

        println("[SWEEP] listeners a pelo, declarados a-b-c con @Order 3-2-1: ${trace.steps}")
        // MEDIDO: llegan dos de tres. El que propaga corta el bucle del difusor
        // y el siguiente no recibe nada. No se arregla con un ErrorHandler
        // global en el multicaster porque eso se tragaria tambien los errores de
        // los eventos de ciclo de vida de Spring, y un arranque roto pasaria
        // inadvertido. La respuesta es la clase base, que si se aisla.
        trace.steps.size.shouldBe(2)
    }

    /**
     * El cruce de los dos mundos, que es el que de verdad puede doler: un
     * `@EventListener` a pelo que propaga corta el bucle del difusor **antes** de
     * que los handlers de la clase base lleguen a registrar su sincronizacion, y
     * entonces el aislamiento de la clase base no les sirve de nada porque nunca
     * se les llama.
     */
    @Test
    @DisplayName("un listener a pelo que revienta SI deja sin evento a un handler de la clase base")
    fun `el listener a pelo arrastra a los aislados`() {
        trace.rawEnabled = true
        recorder.enabled = true

        tenantContext.runAs(seeded.householdId) {
            transactions.execute { events.householdCreated(seeded.householdId) }
        }

        println(
            "[SWEEP] cruce: listeners a pelo=${trace.steps} " +
                "eventos que le llegaron a la clase base=${recorder.received.map { it.type }}",
        )
        // MEDIDO: cero. El listener a pelo corta el bucle del difusor ANTES de
        // que la clase base registre su sincronizacion AFTER_COMMIT, asi que su
        // aislamiento no le sirve de nada: nunca se le llega a llamar.
        //
        // Es el peor de los limites conocidos del bus in-process, y queda escrito
        // aqui en lugar de prometerse resuelto. Hoy no hay ningun modulo, y la
        // norma --heredar de la clase base-- lo evita entero.
        recorder.received.size.shouldBe(0)
    }

    // ------------------------------------------------------------------
    // 5. El hogar del sobre, consultado de verdad
    // ------------------------------------------------------------------

    @Test
    @DisplayName("tras el commit la transaccion SIGUE activa y el hogar sigue en el contexto")
    fun `tras el commit todavia queda contexto`() {
        publishForProbe()

        println(
            "[SWEEP] contexto del handler: transaccion activa=${probe.txActive} " +
                "sincronizacion activa=${probe.syncActive} " +
                "hogar en el ThreadLocal=${probe.tenantAtEntry} " +
                "(el del evento=${seeded.householdId})",
        )
        // MEDIDO, y contradice lo evidente: AFTER_COMMIT se dispara ANTES de
        // soltar los recursos de la transaccion, no despues. De ahi que un
        // handler que abra la suya con la propagacion normal se una a una
        // transaccion ya cerrada -- y no vea ninguna fila.
        probe.txActive.shouldBe(true)
        probe.tenantAtEntry.shouldBe(seeded.householdId)
    }

    @Test
    @DisplayName("el handler solo ve las filas de su hogar si abre la transaccion con REQUIRES_NEW")
    fun `el sobre situa al handler solo con requires new`() {
        publishForProbe()

        println(
            "[SWEEP] lectura del hogar del sobre: con la plantilla por defecto=${probe.joinedRead} " +
                "(app.household_id visto=${probe.joinedSetting}); " +
                "con REQUIRES_NEW=${probe.newTxRead} (app.household_id visto=${probe.newTxSetting}); " +
                "errores=${probe.failures}",
        )
        // La regla practica para quien escriba un modulo, medida: unirse a la
        // transaccion del core devuelve NADA --su SET LOCAL app.household_id ya
        // no vale-- y REQUIRES_NEW funciona, porque el gestor vuelve a fijar el
        // hogar al abrirla. Es tambien la razon de que el householdId viaje en
        // el sobre y no se deduzca del contexto.
        probe.joinedRead.shouldBe(null)
        probe.newTxRead.shouldBe("Hogar del barrido")
    }

    @Test
    @DisplayName("sin el hogar del sobre, el handler no ve ninguna fila: la politica no le deja")
    fun `sin sobre no hay hogar`() {
        publishForProbe()

        println("[SWEEP] lectura a ciegas (sin hogar en el contexto, REQUIRES_NEW): ${probe.blindRead}")
        probe.blindRead.shouldBeNull()
    }

    private fun publishForProbe() {
        probe.enabled = true
        tenantContext.runAs(seeded.householdId) {
            transactions.execute { events.householdCreated(seeded.householdId) }
        }
    }

    // ------------------------------------------------------------------
    // Por cuenta propia: publicar desde dentro, y registrarse dos veces
    // ------------------------------------------------------------------

    @Test
    @DisplayName("un handler puede publicar otro evento desde dentro y los demas lo reciben")
    fun `un handler que publica desde dentro`() {
        deriver.enabled = true
        recorder.enabled = true

        bus.publish(anEvent("SweepOriginal"))

        // El orden depende de si el que deriva corre antes o despues del
        // grabador, asi que lo que se afirma es que los dos llegan; el orden
        // observado se imprime.
        println("[SWEEP] republicacion: el grabador vio ${recorder.received.map { it.type }}")
        recorder.received.map { it.type }.shouldContainExactlyInAnyOrder(listOf("SweepOriginal", "SweepDerived"))
    }

    @Test
    @DisplayName("el mismo bean registrado dos veces atiende el evento una sola vez")
    fun `el doble registro del mismo bean`() {
        sharedInstanceHandler.enabled = true

        bus.publish(anEvent("SweepDoubleRegistration"))

        println("[SWEEP] mismo bean con dos nombres: ejecuciones=${sharedInstanceHandler.executions.get()}")
        sharedInstanceHandler.executions.get().shouldBe(1)
    }

    @Test
    @DisplayName("dos beans distintos de la misma clase atienden el evento uno por barba")
    fun `dos instancias de la misma clase`() {
        twins.enabled = true

        bus.publish(anEvent("SweepTwins"))

        println("[SWEEP] dos instancias de la misma clase: ejecuciones=${twins.executions.get()}")
        twins.executions.get().shouldBe(2)
    }

    private fun anEvent(type: String) = DomainEvent(
        eventId = UUID.randomUUID(),
        type = type,
        occurredAt = Instant.parse("2026-08-12T10:00:00Z"),
        householdId = seeded.householdId,
        aggregateId = UUID.randomUUID().toString(),
        version = DomainEvent.INITIAL_VERSION,
        payload = emptyMap(),
    )

    @TestConfiguration
    class SweepHandlers {
        @Bean fun racingHandler() = RacingHandler()

        @Bean fun saboteurState() = SaboteurState()

        @Bean fun transactionalSaboteur(state: SaboteurState) = TransactionalSaboteur(state)

        @Bean fun trace() = Trace()

        @Bean fun isolatedA(trace: Trace) = IsolatedA(trace)

        @Bean fun isolatedB(trace: Trace) = IsolatedBFailing(trace)

        @Bean fun isolatedC(trace: Trace) = IsolatedC(trace)

        @Bean fun rawA(trace: Trace) = RawA(trace)

        @Bean fun rawB(trace: Trace) = RawBFailing(trace)

        @Bean fun rawC(trace: Trace) = RawC(trace)

        @Bean fun householdProbe(
            households: HouseholdRepository,
            transactions: TransactionTemplate,
            transactionManager: PlatformTransactionManager,
            tenantContext: TenantContext,
            jdbc: JdbcTemplate,
        ) = HouseholdProbe(households, transactions, transactionManager, tenantContext, jdbc)

        @Bean fun sweepRecorder() = SweepRecorder()

        @Bean fun republishingHandler(bus: EventBus) = RepublishingHandler(bus)

        @Bean fun derivingHandler(bus: EventBus) = DerivingHandler(bus)

        @Bean fun sharedInstanceHandler() = SharedInstanceHandler()

        /** El mismo objeto expuesto con un segundo nombre de bean. */
        @Bean fun sharedInstanceAlias(sharedInstanceHandler: SharedInstanceHandler) = sharedInstanceHandler

        @Bean fun twins() = Twins()

        @Bean fun twinOne(twins: Twins) = TwinHandler(twins)

        @Bean fun twinTwo(twins: Twins) = TwinHandler(twins)
    }

    /** Se entretiene dentro de `handle` para que varios hilos coincidan ahi. */
    class RacingHandler : IdempotentEventHandler("RacingHandler") {
        @Volatile var enabled = false
        val executions = AtomicInteger()
        val peak = AtomicInteger()
        private val inside = AtomicInteger()

        fun reset() {
            enabled = false
            executions.set(0)
            peak.set(0)
            inside.set(0)
        }

        override fun handle(event: DomainEvent) {
            if (!enabled) return
            executions.incrementAndGet()
            val simultaneous = inside.incrementAndGet()
            peak.accumulateAndGet(simultaneous) { a, b -> maxOf(a, b) }
            Thread.sleep(200)
            inside.decrementAndGet()
        }
    }

    /** Fuera de Spring: solo cuenta, para medir el tope de la guarda. */
    class CountingHandler : IdempotentEventHandler("CountingHandler") {
        var executions = 0
        override fun handle(event: DomainEvent) {
            executions++
        }
    }

    /**
     * Un modulo que se registra a pelo y **ademas** anota `@Transactional`, que
     * es lo que hace cualquiera que necesite tocar la base de datos y no conozca
     * la clase base.
     */
    /**
     * El estado del saboteador transaccional, fuera de el.
     *
     * No es capricho: el bean va envuelto en un proxy CGLIB --lo exige
     * `@Transactional`-- y el proxy es una subclase construida sin pasar por el
     * constructor, asi que sus campos estan a nulo y todo miembro `final` de
     * Kotlin se ejecuta sobre el proxy y no sobre el objeto real. Guardar el
     * interruptor aqui evita esa trampa por completo.
     */
    class SaboteurState {
        @Volatile var enabled = false
        val attempts = AtomicInteger()

        fun reset() {
            enabled = false
            attempts.set(0)
        }
    }

    open class TransactionalSaboteur(private val state: SaboteurState) {

        /**
         * `open` a mano: el `@Transactional` va en el metodo, y el plugin de
         * Kotlin para Spring solo abre las clases que llevan la anotacion en la
         * clase. Sin esto el contexto ni arranca --"Cannot subclass final
         * class"--, que es un tropiezo de Kotlin y no del bus.
         */
        @EventListener
        @Transactional
        open fun onEvent(event: DomainEvent) {
            if (!state.enabled) return
            state.attempts.incrementAndGet()
            throw IllegalStateException("este modulo se une a la transaccion del core y falla")
        }
    }

    /** Donde los handlers de orden dejan constancia de por donde paso el evento. */
    class Trace {
        val steps = CopyOnWriteArrayList<String>()

        @Volatile var rawEnabled = false

        @Volatile var isolatedEnabled = false

        fun reset() {
            steps.clear()
            rawEnabled = false
            isolatedEnabled = false
        }
    }

    @Order(3)
    class IsolatedA(private val trace: Trace) : IdempotentEventHandler("IsolatedA") {
        override fun handle(event: DomainEvent) {
            if (!trace.isolatedEnabled) return
            trace.steps += "isolated-a"
        }
    }

    /** El que revienta: lleva el `@Order` del medio y se declara el segundo. */
    @Order(2)
    class IsolatedBFailing(private val trace: Trace) : IdempotentEventHandler("IsolatedB") {
        override fun handle(event: DomainEvent) {
            if (!trace.isolatedEnabled) return
            trace.steps += "isolated-b"
            throw IllegalStateException("el de en medio revienta")
        }
    }

    @Order(1)
    class IsolatedC(private val trace: Trace) : IdempotentEventHandler("IsolatedC") {
        override fun handle(event: DomainEvent) {
            if (!trace.isolatedEnabled) return
            trace.steps += "isolated-c"
        }
    }

    class RawA(private val trace: Trace) {
        @Order(3)
        @EventListener
        fun onEvent(event: DomainEvent) {
            if (!trace.rawEnabled) return
            trace.steps += "raw-a"
        }
    }

    class RawBFailing(private val trace: Trace) {
        @Order(2)
        @EventListener
        fun onEvent(event: DomainEvent) {
            if (!trace.rawEnabled) return
            trace.steps += "raw-b"
            throw IllegalStateException("el de en medio revienta")
        }
    }

    class RawC(private val trace: Trace) {
        @Order(1)
        @EventListener
        fun onEvent(event: DomainEvent) {
            if (!trace.rawEnabled) return
            trace.steps += "raw-c"
        }
    }

    /**
     * Mira desde dentro del handler que queda del contexto de la peticion y que
     * hace falta para volver a situarse en el hogar del evento.
     *
     * Cada paso va en un `runCatching` porque la clase base traga las
     * excepciones: sin eso, un fallo aqui se veria como un valor nulo y no como
     * lo que es.
     */
    class HouseholdProbe(
        private val households: HouseholdRepository,
        private val transactions: TransactionTemplate,
        transactionManager: PlatformTransactionManager,
        private val tenantContext: TenantContext,
        private val jdbc: JdbcTemplate,
    ) : IdempotentEventHandler("HouseholdProbe") {

        private val ownTransaction = TransactionTemplate(transactionManager).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }

        @Volatile var enabled = false
        @Volatile var txActive: Boolean? = null
        @Volatile var syncActive: Boolean? = null
        @Volatile var tenantAtEntry: UUID? = null
        @Volatile var joinedRead: String? = null
        @Volatile var joinedSetting: String? = null
        @Volatile var newTxRead: String? = null
        @Volatile var newTxSetting: String? = null
        @Volatile var blindRead: String? = null
        val failures = CopyOnWriteArrayList<String>()

        fun reset() {
            enabled = false
            txActive = null
            syncActive = null
            tenantAtEntry = null
            joinedRead = null
            joinedSetting = null
            newTxRead = null
            newTxSetting = null
            blindRead = null
            failures.clear()
        }

        override fun handle(event: DomainEvent) {
            if (!enabled) return
            txActive = TransactionSynchronizationManager.isActualTransactionActive()
            syncActive = TransactionSynchronizationManager.isSynchronizationActive()
            tenantAtEntry = tenantContext.currentHousehold()

            // Lo que escribiria un modulo sin pensarlo: situarse en el hogar del
            // sobre y abrir una transaccion con la plantilla de siempre.
            step("plantilla por defecto") {
                tenantContext.runAs(event.householdId) {
                    transactions.execute {
                        joinedSetting = currentSetting()
                        joinedRead = households.findCurrent()?.name
                    }
                }
            }

            // Lo mismo pidiendo una transaccion nueva de verdad.
            step("REQUIRES_NEW") {
                tenantContext.runAs(event.householdId) {
                    ownTransaction.execute {
                        newTxSetting = currentSetting()
                        newTxRead = households.findCurrent()?.name
                    }
                }
            }

            // Y sin hogar ninguno, que es lo que tendra un handler el dia que no
            // comparta hilo con quien publico.
            step("a ciegas") {
                tenantContext.runAs(null) {
                    ownTransaction.execute { blindRead = households.findCurrent()?.name }
                }
            }
        }

        private fun step(name: String, block: () -> Unit) {
            runCatching(block).onFailure { failures += "$name -> ${it::class.java.simpleName}: ${it.message}" }
        }

        private fun currentSetting(): String =
            jdbc.queryForObject(
                "SELECT coalesce(nullif(current_setting('app.household_id', true), ''), '(sin hogar)')",
                String::class.java,
            ) ?: "(nulo)"
    }

    class SweepRecorder : IdempotentEventHandler("SweepRecorder") {
        @Volatile var enabled = false
        val received = CopyOnWriteArrayList<DomainEvent>()

        fun reset() {
            enabled = false
            received.clear()
        }

        override fun handle(event: DomainEvent) {
            if (!enabled) return
            received += event
        }
    }

    /** Republica **el mismo** evento desde dentro, con tope para no colgar la prueba. */
    class RepublishingHandler(private val bus: EventBus) : IdempotentEventHandler("RepublishingHandler") {
        @Volatile var enabled = false
        val executions = AtomicInteger()
        val maxDepth = AtomicInteger()
        private val depth = AtomicInteger()

        fun reset() {
            enabled = false
            executions.set(0)
            maxDepth.set(0)
            depth.set(0)
        }

        override fun handle(event: DomainEvent) {
            if (!enabled) return
            executions.incrementAndGet()
            val current = depth.incrementAndGet()
            maxDepth.accumulateAndGet(current) { a, b -> maxOf(a, b) }
            if (current < LIMIT) bus.publish(event)
            depth.decrementAndGet()
        }

        companion object {
            const val LIMIT = 4
        }
    }

    /** Publica un evento **distinto** desde dentro, que es el caso legitimo. */
    class DerivingHandler(private val bus: EventBus) : IdempotentEventHandler("DerivingHandler") {
        @Volatile var enabled = false

        fun reset() {
            enabled = false
        }

        override fun handle(event: DomainEvent) {
            if (!enabled || event.type == "SweepDerived") return
            bus.publish(event.copy(eventId = UUID.randomUUID(), type = "SweepDerived"))
        }
    }

    class SharedInstanceHandler : IdempotentEventHandler("SharedInstanceHandler") {
        @Volatile var enabled = false
        val executions = AtomicInteger()

        fun reset() {
            enabled = false
            executions.set(0)
        }

        override fun handle(event: DomainEvent) {
            if (!enabled) return
            executions.incrementAndGet()
        }
    }

    class Twins {
        @Volatile var enabled = false
        val executions = AtomicInteger()

        fun reset() {
            enabled = false
            executions.set(0)
        }
    }

    class TwinHandler(private val twins: Twins) : IdempotentEventHandler("TwinHandler") {
        override fun handle(event: DomainEvent) {
            if (!twins.enabled) return
            twins.executions.incrementAndGet()
        }
    }
}
