package com.drp.platform.event

import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.core.Ordered
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * El bus, sobre el `ApplicationEventPublisher` de Spring.
 *
 * In-process y sin infraestructura propia, que es lo que corresponde a un
 * monolito modular (ADR-001): un broker aqui seria pagar el precio de los
 * microservicios sin tener ninguno.
 *
 * **Publicar son ahora dos cosas, y solo la primera es una garantia** (ADR-013):
 *
 * 1. **Dejar constancia** en el outbox, *dentro de la transaccion de quien
 *    publica*. Es lo unico que hace que el evento no se pierda: o se guardan el
 *    cambio de estado y su evento, o no se guarda ninguno.
 * 2. **Repartirlo** a los suscriptores, que es lo que ya se hacia y sigue
 *    ocurriendo en el acto, despues del commit y antes de que la peticion
 *    responda.
 *
 * Y confirmar, que borra la fila cuando el reparto ha terminado. Lo que quede sin
 * confirmar --porque el proceso se cayo entre el commit y el reparto-- lo recoge
 * `OutboxRelay` unos segundos despues. **La garantia no cambia**: sigue siendo
 * at-least-once y los handlers siguen teniendo que ser idempotentes.
 *
 * ### El `catch` ya no cubre la escritura, y es la linea que mas importa aqui
 *
 * El `try` no es defensa contra los handlers bien escritos --esos ya se aislan
 * solos, ver `IdempotentEventHandler`-- sino contra los que no lo esten: un
 * `@EventListener` puesto a mano por un modulo corre **dentro** de la transaccion
 * del core, y sin esta red su excepcion se llevaria por delante el alta que la
 * origino.
 *
 * Lo que **no** puede cubrir es [EventOutbox.record]. Con la escritura dentro del
 * `try`, este mismo `catch` se tragaria el fallo de guardar la fila y perderia el
 * evento **en silencio** --exactamente lo que el outbox viene a impedir, y sin un
 * solo error visible--. Por eso la constancia va fuera y su fallo sube al caso de
 * uso, que es donde se puede deshacer la transaccion entera.
 *
 * ### Dos limitaciones medidas, y una que el outbox cierra
 *
 * Estan comprobadas en `EventBusSweepTest`, no razonadas:
 *
 * - Esta red **no salva a los handlers que van detras**. Si un `@EventListener` a
 *   pelo propaga, el difusor de Spring corta ahi: los siguientes no reciben el
 *   evento, y eso incluye a los que si heredan de la clase base. **Esto es lo que
 *   el outbox cierra**: la fila sigue en la cola porque nadie llego a
 *   confirmarla, y el relay reparte el evento a todos unos segundos despues.
 * - Tampoco salva del handler que se une a la transaccion del core con
 *   `@Transactional` y falla. Ese la marca `rollbackOnly` y el fallo aparece al
 *   **cerrarla**, no al publicar, asi que este `catch` ya ha vuelto cuando se
 *   produce. Aqui el outbox no cambia nada y es lo correcto: si la transaccion se
 *   deshace, la fila del evento se deshace con ella --no ocurrio.
 */
@Component
class SpringEventBus(
    private val publisher: ApplicationEventPublisher,
    private val outbox: EventOutbox,
) : EventBus {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun publish(event: DomainEvent) {
        // **Fuera del `try`, y a proposito.** Ver el comentario de la clase.
        outbox.record(event)

        try {
            publisher.publishEvent(event)
        } catch (failure: Exception) {
            // **Y no se confirma.** Si el difusor corto, el reparto quedo a
            // medias: los handlers que iban detras no recibieron nada. La fila se
            // queda en la cola y `OutboxRelay` reparte el evento a todos unos
            // segundos despues, que es la limitacion que `EventBusSweepTest`
            // media desde la Fase 1 --«esta red no salva a los que van detras»--
            // y que este hito cierra.
            log.error(
                "Un suscriptor falló atendiendo {} {}; el core sigue adelante y el evento queda en la cola",
                event.type,
                event.eventId,
                failure,
            )
            return
        }

        confirmWhenDispatched(event)
    }

    /**
     * Borra la fila **cuando el reparto ha terminado de verdad**, que no es
     * cuando `publishEvent` vuelve.
     *
     * Los handlers heredan de [IdempotentEventHandler], que corre en
     * `AFTER_COMMIT`: si hay una transaccion abierta, `publishEvent` solo los
     * **apunta** y el reparto ocurre al cerrarla. Confirmar aqui mismo daria por
     * entregado un evento que todavia no ha salido, y una caida en medio lo
     * perderia --que es justo el agujero que este hito tapa.
     *
     * De ahi las dos ramas:
     *
     * - **Con transaccion**, se apunta una sincronizacion propia en la misma
     *   fase que los handlers y **la ultima de todas**. Que sea la ultima no se
     *   confia al azar: `TransactionSynchronizationManager` ordena las
     *   sincronizaciones por [Ordered], cada handler declara
     *   `LOWEST_PRECEDENCE - 1` en la clase base y esta declara el minimo
     *   posible, un escalon por detras. Si la transaccion se deshace no se llama
     *   a nadie, y es lo correcto: la fila se deshizo con ella porque nunca
     *   ocurrio.
     * - **Sin transaccion**, el reparto es sincrono --por el `fallbackExecution`
     *   de la clase base-- asi que al volver de `publishEvent` ya ha terminado.
     */
    private fun confirmWhenDispatched(event: DomainEvent) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            outbox.confirm(event)
            return
        }

        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization, Ordered {
                override fun getOrder(): Int = Ordered.LOWEST_PRECEDENCE

                override fun afterCommit() {
                    // Con su propia red: un fallo confirmando no puede tumbar la
                    // peticion que ya respondio. Lo que deja es la fila en la
                    // cola, y el relay la repartira otra vez --at-least-once, que
                    // es la garantia declarada.
                    runCatching { outbox.confirm(event) }
                        .onFailure { log.error("No se pudo confirmar {} {}", event.type, event.eventId, it) }
                }
            },
        )
    }
}
