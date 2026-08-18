package com.drp.platform.event


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
 * Tres garantias, **todas condicionadas a heredar de
 * [com.drp.platform.event.IdempotentEventHandler]**. Esa condicion no es
 * burocracia: un modulo que se registre por su cuenta con un `@EventListener` a
 * pelo se queda sin las tres, y ademas puede dejar sin evento a los demas. Los
 * limites exactos, medidos y no supuestos, estan en esa clase.
 *
 * - **Entrega at-least-once.** Un handler puede recibir el mismo evento mas de
 *   una vez, asi que tiene que ser idempotente. La clase base lo resuelve
 *   reservando el `eventId` antes de atenderlo.
 * - **Un handler que falla no tumba al core.** Los datos del core ya estan
 *   escritos cuando el handler arranca, y su excepcion no sale de el. **Con una
 *   excepcion conocida**: un handler que se una a la transaccion del core con
 *   `@Transactional` y falle **si** la tumba. Por eso la regla para tocar la base
 *   de datos desde un handler es `REQUIRES_NEW`, siempre.
 * - **Un handler que falla no deja sin evento a los demas.** Cada uno se aisla
 *   por su cuenta, que es la unica forma de conseguirlo con un solo difusor.
 */
interface EventBus {
    fun publish(event: DomainEvent)
}
