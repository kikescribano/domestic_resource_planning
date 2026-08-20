package com.drp.platform.event

import com.drp.core.application.event.CoreEvents
import com.drp.platform.event.IdempotentEventHandler
import com.drp.platform.event.EventBus
import com.drp.platform.tenant.TenantContext
import com.drp.platform.event.DomainEvent
import com.drp.test.DrpPostgres
import com.drp.test.SpringIntegrationTest
import com.drp.test.seedHousehold
import io.kotest.matchers.collections.shouldHaveSize
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
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * El bus contra la aplicacion entera y PostgreSQL real, que es donde se ve lo
 * que la prueba unitaria no puede ver: **cuando** corre un handler respecto al
 * commit, y que le pasa al core cuando el handler falla.
 *
 * Es la promesa de README 5.2.2 --"un fallo en el handler de un modulo no debe
 * afectar a la transaccion del core"--, y es facil de incumplir sin enterarse:
 * con un `@EventListener` normal el handler corre **dentro** de la transaccion,
 * asi que el aislamiento no es algo que se pueda dar por supuesto ni escribir en
 * un comentario. Aqui se ejecuta.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EventBusIntegrationTest : SpringIntegrationTest() {

    @Autowired private lateinit var events: CoreEvents

    @Autowired private lateinit var bus: EventBus

    @Autowired private lateinit var tenantContext: TenantContext

    @Autowired private lateinit var transactions: TransactionTemplate

    @Autowired private lateinit var recorder: RecordingHandler

    @Autowired private lateinit var saboteur: SabotagingHandler

    @Autowired private lateinit var carelessModule: CarelessListener

    /**
     * Un hogar **de verdad**, y desde el Hito 1 del cierre de huecos tiene que
     * serlo: hasta el outbox esto era un `UUID` inventado, porque publicar no
     * escribia nada en ninguna parte. Ahora publicar deja una fila en
     * `event_outbox` con su clave ajena contra `households`, asi que **publicar
     * un evento de un hogar que no existe deja de ser posible** --que es lo
     * correcto, y de paso lo unico que hace que estas pruebas hablen del mismo
     * mundo que la aplicacion.
     */
    private lateinit var household: UUID

    @BeforeAll
    fun seed() {
        DrpPostgres.instance.ownerConnection().use { household = it.seedHousehold("Hogar del bus").householdId }
    }

    @BeforeEach
    fun resetHandlers() {
        recorder.received.clear()
        saboteur.attempts.set(0)
        carelessModule.attempts.set(0)
        carelessModule.enabled = false
    }

    @Test
    @DisplayName("el handler no ve el evento hasta que la transaccion del core hace commit")
    fun `entrega despues del commit`() {
        tenantContext.runAs(household) {
            transactions.execute {
                events.locationCreated(UUID.randomUUID(), "Garaje", "ROOM", null)

                // Todavia dentro de la transaccion: si el handler ya hubiera
                // corrido, estaria reaccionando a un estado que aun puede
                // desaparecer.
                recorder.received.shouldHaveSize(0)
            }
        }

        recorder.received.shouldHaveSize(1)
        recorder.received.first().type.shouldBe("LocationCreated")
    }

    @Test
    @DisplayName("si la transaccion se deshace, el evento no se entrega: nadie reacciona a lo que no paso")
    fun `no entrega lo que no se guardo`() {
        runCatching {
            tenantContext.runAs(household) {
                transactions.execute<Unit> {
                    events.locationCreated(UUID.randomUUID(), "Desvan", "ROOM", null)
                    throw IllegalStateException("algo fallo despues de publicar")
                }
            }
        }

        recorder.received.shouldHaveSize(0)
    }

    @Test
    @DisplayName("un handler de la clase base que revienta no deshace la transaccion del core")
    fun `el fallo de un handler aislado no alcanza al core`() {
        val committed = tenantContext.runAs(household) {
            transactions.execute {
                events.assetDeactivated(UUID.randomUUID())
                "el core termino"
            }
        }

        committed.shouldBe("el core termino")
        saboteur.attempts.get().shouldBe(1)
        // Y el handler sano recibio el evento igual. Que lo reciba no depende del
        // orden de registro: cada uno se aisla por su cuenta, asi que ninguno
        // puede dejar sin evento al siguiente.
        recorder.received.shouldHaveSize(1)
    }

    /**
     * El caso que el aislamiento de la clase base **no** cubre, y que por eso
     * tiene red en [SpringEventBus]: un modulo que se registra con un
     * `@EventListener` a pelo corre dentro de la transaccion del core.
     *
     * Sin el `catch` del adaptador, su excepcion subiria por `publishEvent` hasta
     * el caso de uso y se llevaria por delante el alta que la origino.
     */
    @Test
    @DisplayName("un modulo que se registra a pelo y revienta tampoco deshace la transaccion del core")
    fun `el fallo de un listener descuidado no alcanza al core`() {
        carelessModule.enabled = true

        val committed = tenantContext.runAs(household) {
            transactions.execute {
                events.assetDeactivated(UUID.randomUUID())
                "el core termino"
            }
        }

        committed.shouldBe("el core termino")
        carelessModule.attempts.get().shouldBe(1)
    }

    @Test
    @DisplayName("reentregar el mismo evento no lo procesa dos veces")
    fun `la guarda de idempotencia funciona sobre el bus real`() {
        val event = DomainEvent(
            eventId = UUID.randomUUID(),
            type = "AssetQuantityChanged",
            occurredAt = Instant.now(),
            householdId = household,
            aggregateId = UUID.randomUUID().toString(),
            version = DomainEvent.INITIAL_VERSION,
            payload = emptyMap(),
        )

        // Dos publicaciones del **mismo** eventId, que es lo que hara el
        // Transactional Outbox del dia que exista al reintentar una entrega.
        bus.publish(event)
        bus.publish(event)

        recorder.received.shouldHaveSize(1)
    }

    @Test
    @DisplayName("el evento lleva el hogar del contexto, que es lo unico que situa a un handler tras el commit")
    fun `el sobre lleva el hogar`() {
        tenantContext.runAs(household) {
            transactions.execute { events.householdCreated(household) }
        }

        val event = recorder.received.single()
        event.householdId.shouldBe(household)
        event.aggregateId.shouldBe(household.toString())
        event.version.shouldBe(DomainEvent.INITIAL_VERSION)
    }

    @Test
    @DisplayName("publicar sin hogar en el contexto es un fallo de programacion y se nota")
    fun `no publica sin hogar`() {
        val failure = runCatching {
            tenantContext.runAs(null) {
                transactions.execute { events.assetDeactivated(UUID.randomUUID()) }
            }
        }

        failure.isFailure.shouldBe(true)
        recorder.received.shouldHaveSize(0)
    }

    @TestConfiguration
    class Handlers {
        @Bean fun sabotagingHandler() = SabotagingHandler()

        @Bean fun recordingHandler() = RecordingHandler()

        @Bean fun carelessListener() = CarelessListener()
    }

    class RecordingHandler : IdempotentEventHandler("RecordingHandler") {
        val received = CopyOnWriteArrayList<DomainEvent>()
        override fun handle(event: DomainEvent) {
            received += event
        }
    }

    class SabotagingHandler : IdempotentEventHandler("SabotagingHandler") {
        val attempts = AtomicInteger()
        override fun handle(event: DomainEvent) {
            attempts.incrementAndGet()
            throw IllegalStateException("este modulo esta roto a proposito")
        }
    }

    /** Un modulo mal escrito: ni clase base, ni `AFTER_COMMIT`, ni idempotencia. */
    class CarelessListener {
        val attempts = AtomicInteger()

        /** Apagado salvo en la prueba que lo necesita: si no, romperia a las demas. */
        @Volatile var enabled = false

        @EventListener
        fun onEvent(event: DomainEvent) {
            if (!enabled) return
            attempts.incrementAndGet()
            throw IllegalStateException("este modulo se registro a pelo y ademas falla")
        }
    }
}
