package com.drp.adapter.event

import com.drp.application.port.EventBus
import com.drp.domain.event.DomainEvent
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/**
 * El bus, sobre el `ApplicationEventPublisher` de Spring.
 *
 * In-process y sin infraestructura propia, que es lo que corresponde a un
 * monolito modular (ADR-001): un broker aqui seria pagar el precio de los
 * microservicios sin tener ninguno.
 *
 * El `try` no es defensa contra los handlers bien escritos --esos ya se aislan
 * solos, ver `IdempotentEventHandler`-- sino contra los que no lo esten: un
 * `@EventListener` puesto a mano por un modulo futuro corre **dentro** de la
 * transaccion del core, y sin esta red su excepcion se llevaria por delante el
 * alta que la origino.
 *
 * Y una limitacion que conviene conocer antes de escribir el primer modulo: esa
 * red **no salva a los handlers que van detras**. Si uno propaga, el difusor de
 * Spring corta ahi y los siguientes no reciben el evento; lo unico que consigue
 * este `catch` es que el core no se entere. Por eso el aislamiento de verdad esta
 * en la clase base y no aqui.
 */
@Component
class SpringEventBus(private val publisher: ApplicationEventPublisher) : EventBus {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun publish(event: DomainEvent) {
        try {
            publisher.publishEvent(event)
        } catch (failure: Exception) {
            log.error(
                "Un suscriptor fallo atendiendo {} {}; el core sigue adelante",
                event.type,
                event.eventId,
                failure,
            )
        }
    }
}
