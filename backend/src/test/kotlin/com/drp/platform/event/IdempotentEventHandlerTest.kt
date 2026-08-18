package com.drp.platform.event

import com.drp.platform.event.DomainEvent
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * Las tres garantias de la clase base, sin Spring de por medio.
 *
 * Aqui se prueba la semantica --contar entregas, provocar fallos--; que el
 * `AFTER_COMMIT` haga lo que promete necesita transaccion de verdad y esta en
 * [com.drp.core.adapter.event.EventBusIntegrationTest].
 */
class IdempotentEventHandlerTest {

    @Test
    @DisplayName("el mismo evento entregado dos veces se atiende una sola")
    fun `descarta el duplicado`() {
        val handler = CountingHandler()
        val event = anEvent()

        handler.receive(event)
        handler.receive(event)
        handler.receive(event)

        handler.handled.shouldBe(1)
    }

    @Test
    @DisplayName("dos eventos distintos se atienden los dos: la guarda no traga de mas")
    fun `no confunde eventos distintos`() {
        val handler = CountingHandler()

        handler.receive(anEvent())
        handler.receive(anEvent())

        handler.handled.shouldBe(2)
    }

    @Test
    @DisplayName("la excepcion de un handler no sale de el")
    fun `se aisla al fallar`() {
        val handler = FailingHandler(failures = 1)

        // Si esto propagara, la llamada romperia la prueba sin necesidad de
        // aserto: es justo lo que le pasaria a la transaccion del core.
        handler.receive(anEvent())

        handler.attempts.shouldBe(1)
    }

    @Test
    @DisplayName("un evento que fallo se vuelve a intentar al reentregarse, no se da por hecho")
    fun `no marca como procesado lo que fallo`() {
        val handler = FailingHandler(failures = 1)
        val event = anEvent()

        handler.receive(event) // falla
        handler.receive(event) // reentrega: tiene que volver a intentarlo

        handler.attempts.shouldBe(2)
        handler.succeeded.shouldBe(1)
    }

    private fun anEvent() = DomainEvent(
        eventId = UUID.randomUUID(),
        type = "AssetCreated",
        occurredAt = Instant.parse("2026-08-12T10:00:00Z"),
        householdId = UUID.randomUUID(),
        aggregateId = UUID.randomUUID().toString(),
        version = DomainEvent.INITIAL_VERSION,
        payload = emptyMap(),
    )

    private class CountingHandler : IdempotentEventHandler("CountingHandler") {
        var handled = 0
        override fun handle(event: DomainEvent) {
            handled++
        }
    }

    /** Falla las [failures] primeras veces y luego funciona, como un modulo con una caida pasajera. */
    private class FailingHandler(private val failures: Int) : IdempotentEventHandler("FailingHandler") {
        var attempts = 0
        var succeeded = 0

        override fun handle(event: DomainEvent) {
            attempts++
            if (attempts <= failures) throw IllegalStateException("el modulo esta caido")
            succeeded++
        }
    }
}
