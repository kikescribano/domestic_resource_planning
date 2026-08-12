package com.drp.application.port

import com.drp.domain.event.DomainEvent

/**
 * Por donde el core anuncia lo que le pasa, sin saber quien escucha.
 *
 * **Solo publica.** El boceto de la Fase 0 (README 5.2.2) dibujaba tambien un
 * `subscribe(eventType, handler)`, y no se ha implementado: sobre
 * `ApplicationEventPublisher` la suscripcion es declarativa --un `@EventListener`
 * en el modulo-- y anadir un registro propio en paralelo daria dos mecanismos
 * para lo mismo. Ademas un `subscribe` obliga a alguien a tener el bus en la mano
 * para registrarse, mientras que lo declarativo mantiene la propiedad que la
 * seccion 5.2 pide de verdad: que el core no necesite saber que el modulo existe.
 *
 * Tres garantias que el adaptador cumple y de las que depende quien escuche:
 *
 * - **Entrega at-least-once.** Un handler puede recibir el mismo evento mas de
 *   una vez, asi que tiene que ser idempotente. No es una recomendacion: hay una
 *   clase base que lo resuelve, [com.drp.application.event.IdempotentEventHandler].
 * - **Un handler que falla no tumba al core.** La transaccion que origino el
 *   evento ya esta cerrada cuando el handler corre, y su excepcion no sale de el.
 * - **Un handler que falla no deja sin evento a los demas.** Cada uno se aisla
 *   por su cuenta, que es la unica forma de conseguirlo con un solo difusor.
 */
interface EventBus {
    fun publish(event: DomainEvent)
}
