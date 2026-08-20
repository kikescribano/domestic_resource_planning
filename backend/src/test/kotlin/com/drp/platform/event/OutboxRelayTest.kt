package com.drp.platform.event

import com.drp.module.testbed.TestbedModule
import com.drp.test.DrpMailpit
import com.drp.test.DrpPostgres
import com.drp.test.SpringIntegrationTest
import com.drp.test.count
import com.drp.test.execute
import com.drp.test.getJson
import com.drp.test.postJson
import com.drp.test.registerHousehold
import com.drp.platform.tenant.TenantContext
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.annotation.Bean
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.context.event.EventListener
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * **La prueba que justifica el hito**: publicar, cortar antes de entregar,
 * arrancar y comprobar que el handler recibe (ADR-013).
 *
 * Hasta el outbox esta prueba no se podia escribir. El bus era in-process y sin
 * estado, asi que entre el `COMMIT` del caso de uso y el `AFTER_COMMIT` que
 * reparte habia una ventana en la que caerse significaba **perder el evento**:
 * no habia nada que mirar despues, ni fila, ni registro, ni sintoma. Con la fila
 * en la cola hay algo que comprobar y algo que recoger.
 *
 * ### Como se corta el proceso sin matar la JVM
 *
 * No hace falta matarla, y falsearlo con un doble tampoco haria falta: **hay un
 * corte de verdad al alcance**. Un `@EventListener` a pelo que revienta hace que
 * el difusor de Spring pare ahi, de modo que los handlers que van detras --y eso
 * incluye a los que heredan de la clase base-- no llegan a apuntarse siquiera.
 * El resultado es exactamente el estado que deja una caida: **la fila
 * comprometida en la base de datos y nadie que la haya repartido**. Es ademas la
 * limitacion que `EventBusSweepTest` media desde la Fase 1 sin poder arreglarla.
 *
 * Y el «arrancar» es literal para lo que importa: la guarda de idempotencia del
 * handler esta **vacia para ese `eventId`**, porque nunca lo llego a atender.
 *
 * ### El testigo es el modulo de prueba
 *
 * `com.drp.module.testbed`, el del Hito 0 de la Fase 2, que vive en el arbol de
 * pruebas y existe exactamente para esto: tiene tabla, ruta y handler, y su
 * handler **no es idempotente a proposito** --escribe una nota cada vez que
 * pasa-- que es justo lo que permite contar entregas.
 *
 * No hereda de [SpringIntegrationTest] por lo mismo que `SchedulingEnabledTest`:
 * hay que **encender** el relay, la propiedad que lo apaga la pone la clase base
 * y quien gana cuando dos `@DynamicPropertySource` declaran la misma no esta
 * garantizado por ningun contrato. Al cablearlo todo en el mismo metodo el orden
 * es el de las lineas.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("el relay del outbox: lo que nadie repartió llega igual")
class OutboxRelayTest {

    @Autowired private lateinit var http: TestRestTemplate
    @Autowired private lateinit var bus: EventBus
    @Autowired private lateinit var outbox: EventOutbox
    @Autowired private lateinit var relay: OutboxRelay
    @Autowired private lateinit var tenantContext: TenantContext
    @Autowired private lateinit var transactions: TransactionTemplate
    @Autowired private lateinit var cut: CutTheProcess
    @Autowired private lateinit var withFallback: RelayWitnessWithFallback
    @Autowired private lateinit var withoutFallback: RelayWitnessWithoutFallback

    private val mailpit = DrpMailpit.instance

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            SpringIntegrationTest.datasource(registry)
            registry.add(TestbedModule.ENABLED) { true }
            // El relay, encendido. El **programador** sigue apagado --lo deja asi
            // la linea de arriba-- de modo que aqui hay bean y no hay pasada
            // automatica: lo que se invoca es `relay.run()` a mano, que es el
            // mismo metodo que invoca el `@Scheduled`.
            registry.add("drp.outbox.enabled") { true }
            // **Sin gracia.** En un despliegue el relay solo mira lo que lleva
            // pendiente mas de treinta segundos, para no pisar al reparto en el
            // acto; aqui esperar medio minuto por prueba no mediria nada mas.
            // La gracia tiene su propia prueba, mas abajo, con su valor puesto.
            registry.add("drp.outbox.grace") { "0s" }
        }
    }

    @BeforeEach
    fun silence() {
        cut.enabled = false
        withFallback.reset()
        withoutFallback.reset()
    }

    // -----------------------------------------------------------------------
    // 1. El corte
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("publicado el evento y cortado el proceso antes de repartirlo, el handler lo recibe al arrancar")
    fun `lo que no se repartio se reparte despues`() {
        val home = http.registerHousehold(mailpit)
        http.activateTestbed(home.accessToken)
        val before = http.noteCount(home.accessToken)

        cut.enabled = true
        val event = anEvent(home.householdId(), "LocationCreated", mapOf("name" to "Garaje del corte"))
        tenantContext.runAs(home.householdId()) { transactions.executeWithoutResult { bus.publish(event) } }

        withClue("el corte tiene que haber impedido el reparto: ninguna nota nueva") {
            http.noteCount(home.accessToken).shouldBe(before)
        }
        withClue("y la fila tiene que seguir en la cola, que es lo unico que salva al evento") {
            pendingRows(home.householdId()).shouldBe(1)
        }

        // «Arrancar»: el relay reparte lo que nadie confirmo.
        cut.enabled = false
        val report = relay.run()

        report.redelivered.shouldBeGreaterThan(0)
        withClue("el handler del modulo de prueba tenia que recibirlo por fin") {
            http.notes(home.accessToken).shouldContain("Garaje del corte")
        }
        withClue("y la fila desaparece al repartirse: el outbox es una cola, no un archivo") {
            pendingRows(home.householdId()).shouldBe(0)
        }
    }

    // -----------------------------------------------------------------------
    // 2. La fila se escribe dentro de la transaccion del caso de uso
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("si la transacción que publicó se deshace, no queda fila: el evento no ocurrió")
    fun `la fila viaja con la transaccion`() {
        val home = http.registerHousehold(mailpit)
        val event = anEvent(home.householdId(), "LocationCreated", mapOf("name" to "Desván deshecho"))

        runCatching {
            tenantContext.runAs(home.householdId()) {
                transactions.executeWithoutResult {
                    bus.publish(event)
                    throw IllegalStateException("algo falló después de publicar")
                }
            }
        }

        // Es la decision entera del patron: la fila del evento y el cambio de
        // estado que lo produjo se guardan juntos o no se guarda ninguno. Con
        // `REQUIRES_NEW` en la escritura, esto dejaria un evento de algo que no
        // llego a pasar.
        pendingRows(home.householdId()).shouldBe(0)

        // Y sin fila no hay nada que recuperar: el relay ni siquiera ve a este
        // hogar, porque no puede inventarse un evento de algo que no paso.
        outbox.householdsWithPendingEvents(Instant.now()).shouldNotContain(home.householdId())
    }

    @Test
    @DisplayName("el reparto normal no deja rastro en la cola")
    fun `lo entregado en el acto no lo vuelve a repartir nadie`() {
        val home = http.registerHousehold(mailpit)
        http.activateTestbed(home.accessToken)

        val event = anEvent(home.householdId(), "LocationCreated", mapOf("name" to "Cocina"))
        tenantContext.runAs(home.householdId()) { transactions.executeWithoutResult { bus.publish(event) } }

        val delivered = http.noteCount(home.accessToken)
        withClue("el reparto en el acto sigue ocurriendo: el outbox convive con él, no lo sustituye") {
            delivered.shouldBeGreaterThan(0)
        }
        pendingRows(home.householdId()).shouldBe(0)

        // Y una pasada del relay no repite nada, porque no queda fila que
        // repartir. Es la respuesta a «qué significa una fila que el otro camino
        // ya entregó»: no significa nada, porque no existe.
        relay.run()
        http.noteCount(home.accessToken).shouldBe(delivered)
    }

    @Test
    @DisplayName("dentro del periodo de gracia el relay no toca lo recién publicado")
    fun `la gracia protege al reparto en el acto`() {
        val home = http.registerHousehold(mailpit)
        cut.enabled = true
        val event = anEvent(home.householdId(), "LocationCreated", mapOf("name" to "Recién nacido"))
        tenantContext.runAs(home.householdId()) { transactions.executeWithoutResult { bus.publish(event) } }
        cut.enabled = false

        // Con la gracia de un despliegue --treinta segundos-- una fila de hace un
        // instante todavia no es asunto del relay: el reparto en el acto puede
        // estar a punto de confirmarla. Sin esta ventana, el relay repartiria dos
        // veces **en el caso normal** y no en el excepcional.
        val fresh = outbox.householdsWithPendingEvents(Instant.now().minusSeconds(30))

        fresh.shouldNotContain(home.householdId())
        outbox.householdsWithPendingEvents(Instant.now()).shouldContain(home.householdId())
    }

    // -----------------------------------------------------------------------
    // 3. Lo que el camino del relay le exige a la clase base
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("el relay reparte fuera de toda transacción, así que sin fallbackExecution no llegaría nada")
    fun `el fallback es lo que hace posible el camino del relay`() {
        // Es la decision sobre `ModuleEventHandler` medida y no razonada. Los dos
        // testigos escuchan lo mismo y se diferencian **en una sola linea**: uno
        // hereda de `IdempotentEventHandler` --`AFTER_COMMIT` con
        // `fallbackExecution`-- y el otro declara `AFTER_COMMIT` a secas.
        //
        // Si el relay repartiera dentro de una transaccion, los dos recibirian al
        // cerrarla y esta prueba no distinguiria nada. Repartiendo fuera, el
        // segundo **no recibe**: sin `fallbackExecution`, el camino de
        // recuperacion entero no entregaria nada, y no entregarlo se veria
        // exactamente igual que no tener nada que entregar.
        val home = http.registerHousehold(mailpit)
        cut.enabled = true
        val event = anEvent(home.householdId(), "OutboxFallbackProbe", emptyMap())
        tenantContext.runAs(home.householdId()) { transactions.executeWithoutResult { bus.publish(event) } }
        cut.enabled = false

        relay.run()

        withFallback.received.shouldContain(event.eventId)
        withClue("un handler sin fallbackExecution se quedaría fuera del camino del relay") {
            withoutFallback.received.shouldNotContain(event.eventId)
        }
        withClue("y no hay transacción abierta cuando el handler corre, que es la razón de lo anterior") {
            withFallback.sawAnActiveTransaction.shouldBe(false)
        }
    }

    @Test
    @DisplayName("el handler repartido por el relay sí puede escribir: el REQUIRES_NEW sigue haciendo falta")
    fun `el handler del modulo escribe desde el camino del relay`() {
        // La otra mitad de la decision. `AFTER_COMMIT` deja de tener transaccion
        // del core a la que unirse, pero `REQUIRES_NEW` **no se retira**: sigue
        // siendo lo que abre la transaccion donde el modulo escribe, y sin ella
        // el handler correria sin `app.household_id` y no veria ni escribiria
        // ninguna fila. La nota del modulo de prueba en su propia tabla, con su
        // politica de RLS delante, es lo que lo demuestra.
        val home = http.registerHousehold(mailpit)
        http.activateTestbed(home.accessToken)
        val before = http.noteCount(home.accessToken)

        cut.enabled = true
        val event = anEvent(home.householdId(), "LocationCreated", mapOf("name" to "Trastero del relay"))
        tenantContext.runAs(home.householdId()) { transactions.executeWithoutResult { bus.publish(event) } }
        cut.enabled = false

        relay.run()

        http.noteCount(home.accessToken).shouldBe(before + 1)
    }

    // -----------------------------------------------------------------------
    // 4. El aislamiento, y el hogar que desaparece por el camino
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("el relay entrega a cada hogar lo suyo, hogar a hogar y sin BYPASSRLS")
    fun `un hogar no recibe el evento de otro`() {
        val one = http.registerHousehold(mailpit)
        val other = http.registerHousehold(mailpit)
        http.activateTestbed(one.accessToken)
        http.activateTestbed(other.accessToken)

        cut.enabled = true
        tenantContext.runAs(one.householdId()) {
            transactions.executeWithoutResult {
                bus.publish(anEvent(one.householdId(), "LocationCreated", mapOf("name" to "Sólo del primero")))
            }
        }
        cut.enabled = false

        relay.run()

        http.notes(one.accessToken).shouldContain("Sólo del primero")
        http.notes(other.accessToken).shouldNotContainText("Sólo del primero")
    }

    @Test
    @DisplayName("un hogar que desaparece entre que se lista y se procesa no rompe la pasada")
    fun `el relay tolera que el hogar se haya ido`() {
        // Desde el Hito 0 el recorrido diario no purga solo hogares sin verificar
        // sino **hogares con datos dentro** (ADR-012), y `event_outbox` cuelga de
        // `households`: un hogar purgado se lleva sus entregas pendientes, que es
        // lo correcto --no hay a quien entregarselas--. Lo que hay que comprobar
        // es que el relay lo aguanta, porque entre listar y procesar hay una
        // ventana de verdad.
        val doomed = http.registerHousehold(mailpit)
        val householdId = doomed.householdId()

        cut.enabled = true
        tenantContext.runAs(householdId) {
            transactions.executeWithoutResult {
                bus.publish(anEvent(householdId, "LocationCreated", mapOf("name" to "Casa que se va")))
            }
        }
        cut.enabled = false

        outbox.householdsWithPendingEvents(Instant.now()).shouldContain(householdId)

        DrpPostgres.instance.ownerConnection().use { it.execute("DELETE FROM households WHERE id = ?", householdId) }

        // Es exactamente lo que le pasa al relay con la lista ya en la mano: pide
        // lo pendiente de un hogar que ya no existe. No revienta y no encuentra
        // nada, porque la cascada se llevo la fila y la politica no dejaria verla
        // de todos modos.
        val pending = tenantContext.runAs(householdId) { outbox.pending(Instant.now(), 100) }
        pending.shouldBe(emptyList())
        relay.run()
    }

    // -----------------------------------------------------------------------

    private fun anEvent(householdId: UUID, type: String, payload: Map<String, Any?>) = DomainEvent(
        eventId = UUID.randomUUID(),
        type = type,
        occurredAt = Instant.now(),
        householdId = householdId,
        aggregateId = UUID.randomUUID().toString(),
        version = DomainEvent.INITIAL_VERSION,
        payload = payload,
    )

    private fun pendingRows(householdId: UUID): Int =
        DrpPostgres.instance.ownerConnection().use {
            it.count("SELECT count(*) FROM event_outbox WHERE household_id = ?", householdId)
        }

    private fun com.drp.test.TestHousehold.householdId(): UUID = UUID.fromString(householdId)

    private fun TestRestTemplate.activateTestbed(accessToken: String) =
        postJson("/api/v1/modules/${TestbedModule.KEY}/activation", "", accessToken)
            .also { it.statusCode.shouldBe(HttpStatus.OK) }

    private fun TestRestTemplate.notes(accessToken: String): String =
        getJson(TestbedModule.ROUTE, accessToken).body.orEmpty()

    private fun TestRestTemplate.noteCount(accessToken: String): Int {
        val response = getJson(TestbedModule.ROUTE, accessToken)
        if (response.statusCode != HttpStatus.OK) return 0
        return Regex("evento:").findAll(response.body.orEmpty()).count()
    }

    private fun String.shouldNotContainText(text: String) = withClue("no debería aparecer «$text» en $this") {
        contains(text).shouldBe(false)
    }

    /**
     * Los tres testigos de esta prueba, en su propia configuracion.
     *
     * Van aqui y no en el arbol de produccion por lo mismo que los de
     * `EventBusSweepTest`: son handlers de mentira que existen para medir el
     * mecanismo, no para hacer nada.
     */
    @TestConfiguration
    class RelayWitnesses {
        @Bean fun cutTheProcess() = CutTheProcess()

        @Bean fun relayWitnessWithFallback() = RelayWitnessWithFallback()

        @Bean fun relayWitnessWithoutFallback() = RelayWitnessWithoutFallback()
    }
}

/**
 * El corte: un `@EventListener` a pelo, el primero de la fila, que revienta.
 *
 * Con el encendido, `publishEvent` propaga antes de que ningun
 * `@TransactionalEventListener` llegue a apuntarse, asi que **nadie reparte** y
 * la fila del outbox se queda tal cual la dejo la transaccion. Es el estado que
 * deja una caida entre el commit y el reparto, producido por el camino de verdad
 * y no por un doble del bus.
 *
 * `HIGHEST_PRECEDENCE` es lo que lo pone delante de los handlers, que declaran
 * `@Order(0)`.
 */
class CutTheProcess {

    @Volatile var enabled: Boolean = false

    @EventListener
    @Order(Ordered.HIGHEST_PRECEDENCE)
    fun cut(event: DomainEvent) {
        if (enabled) error("el proceso se cayó antes de repartir ${event.type}")
    }
}

/** Un handler normal, de los que heredan de la clase base. Recibe por los dos caminos. */
class RelayWitnessWithFallback : IdempotentEventHandler("RelayWitnessWithFallback") {

    val received: MutableList<UUID> = CopyOnWriteArrayList()

    @Volatile var sawAnActiveTransaction: Boolean = false

    override fun handle(event: DomainEvent) {
        sawAnActiveTransaction = TransactionSynchronizationManager.isActualTransactionActive()
        received += event.eventId
    }

    fun reset() {
        received.clear()
        sawAnActiveTransaction = false
    }
}

/**
 * El mismo handler **sin `fallbackExecution`**, que es la unica diferencia.
 *
 * Existe para medir el contrafactual: lo que le pasaria al camino del relay si la
 * clase base no lo declarase. No hereda de [IdempotentEventHandler] justamente
 * porque lo que se quiere es no tener su anotacion.
 */
class RelayWitnessWithoutFallback {

    val received: MutableList<UUID> = CopyOnWriteArrayList()

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Order(0)
    fun receive(event: DomainEvent) {
        received += event.eventId
    }

    fun reset() = received.clear()
}
