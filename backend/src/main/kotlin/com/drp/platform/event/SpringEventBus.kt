package com.drp.platform.event

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
 * **Dos limitaciones medidas**, que conviene conocer antes de escribir el primer
 * modulo. Estan comprobadas en `EventBusSweepTest`, no razonadas:
 *
 * - Esta red **no salva a los handlers que van detras**. Si un `@EventListener` a
 *   pelo propaga, el difusor de Spring corta ahi: los siguientes no reciben el
 *   evento, y eso incluye a los que si heredan de la clase base. Lo unico que
 *   consigue este `catch` es que el core no se entere.
 * - Tampoco salva del handler que se une a la transaccion del core con
 *   `@Transactional` y falla. Ese la marca `rollbackOnly` y el fallo aparece al
 *   **cerrarla**, no al publicar, asi que este `catch` ya ha vuelto cuando se
 *   produce.
 *
 * Las dos tienen la misma raiz --un modulo que no usa la clase base-- y la misma
 * respuesta: el aislamiento de verdad esta alli y no aqui.
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
