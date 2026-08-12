package com.drp.application.event

import com.drp.domain.event.DomainEvent
import org.slf4j.LoggerFactory
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.util.Collections
import java.util.UUID

/**
 * La base de todo handler de eventos, dentro o fuera del core.
 *
 * Resuelve de una vez las tres garantias que el bus promete y que, si se dejan a
 * cada modulo, alguno olvidara:
 *
 * **1. Corre despues del commit.** Con `@EventListener` a secas, el handler se
 * ejecuta dentro de la transaccion del core: una excepcion suya la marca
 * `rollbackOnly` y **deshace el alta del asset**, que es exactamente lo contrario
 * de lo que dice README 5.2.2. Con `AFTER_COMMIT` los datos del core ya estan
 * escritos cuando el handler arranca.
 *
 * **Y aqui hay que ser preciso, porque lo evidente es falso.** Medido con
 * `EventBusSweepTest`, no supuesto: dentro del handler **la transaccion sigue
 * activa** --`AFTER_COMMIT` se dispara antes de soltar los recursos, no
 * despues-- y el hogar **sigue en el `TenantContext`**. De ahi salen dos
 * consecuencias que un modulo tiene que conocer:
 *
 * - **Un handler que toque la base de datos debe abrir su transaccion con
 *   `REQUIRES_NEW`.** Con la propagacion normal se une a la del core, que ya esta
 *   cerrada y cuyo `SET LOCAL app.household_id` ya no vale: la consulta no
 *   devuelve **ninguna fila** --medido-- porque la politica no ve hogar. Con
 *   `REQUIRES_NEW` se abre una transaccion propia, el gestor vuelve a fijar el
 *   hogar y todo funciona.
 * - **Un handler `@Transactional` que se una a la del core y falle SI tumba al
 *   core.** El interceptor la marca `rollbackOnly`, el commit revienta con
 *   `UnexpectedRollbackException` y **las filas del core no sobreviven**
 *   --medido--. El `AFTER_COMMIT` no protege de esto y el `catch` de mas abajo
 *   tampoco, porque el fallo aparece al cerrar la transaccion, no al atender el
 *   evento. Es la unica forma conocida de que un modulo se lleve por delante al
 *   core, y la unica defensa es no unirse: `REQUIRES_NEW` siempre.
 *
 * **2. Se aisla.** Su excepcion no sale de aqui. Con un solo difusor, un handler
 * que propaga deja sin evento a los que van detras, asi que aislarse no es
 * cortesia con el core sino con los demas modulos.
 *
 * **3. Es idempotente.** La entrega es at-least-once, asi que se **reserva** el
 * `eventId` antes de atenderlo y se descarta lo ya reservado. La reserva cubre
 * dos casos que hoy si pueden darse --varios hilos publicando el mismo evento, y
 * un handler que republique el suyo y se reentre-- ademas del que traera el
 * Transactional Outbox que nombra README 5.2.2, que reentrega **el mismo
 * `eventId`**. Escribir la guarda despues significaria repasar todos los
 * handlers que existan para entonces.
 *
 * Lo recordado vive en memoria y no en una tabla, porque el bus tambien: un
 * reinicio no reentrega nada --pierde el evento, que es justo lo que el outbox
 * viene a arreglar--, asi que persistirlo no protegeria de nada que pueda pasar
 * hoy. El dia que haya outbox, esa guarda se muda con el.
 *
 * **La guarda no exime al handler de ser idempotente por dentro.** Es una red,
 * no una garantia: solo cubre reentregas del mismo `eventId` a este mismo
 * proceso. Un handler cuya operacion no se pueda repetir sin dano sigue estando
 * mal escrito.
 */
abstract class IdempotentEventHandler(private val handlerName: String) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val processed = ProcessedEvents()

    /**
     * `fallbackExecution` a true para que el handler tambien corra cuando quien
     * publica no tiene transaccion abierta. Sin eso, un evento publicado fuera de
     * una transaccion se descarta sin ruido, que es la clase de perdida que no se
     * nota hasta que un modulo pregunta por que no recibio nada.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun receive(event: DomainEvent) {
        // Se **reserva** el evento antes de atenderlo, no despues. La version
        // anterior comprobaba primero y marcaba al terminar bien, y con eso la
        // guarda no servia de nada en los dos casos en los que hace falta: ocho
        // hilos con el mismo `eventId` pasaban los ocho la comprobacion antes de
        // que ninguno marcara --medido: ocho ejecuciones-- y un handler que
        // republicara su propio evento se llamaba a si mismo en cascada.
        if (!processed.claim(event.eventId)) {
            log.debug("{} descarta {} {}: ya procesado", handlerName, event.type, event.eventId)
            return
        }

        try {
            handle(event)
        } catch (failure: Exception) {
            // Se suelta la reserva: si el handler falla, una reentrega posterior
            // tiene que volver a intentarlo, no saltarselo por haberlo reservado.
            processed.release(event.eventId)
            log.error("{} fallo atendiendo {} {}", handlerName, event.type, event.eventId, failure)
        }
    }

    protected abstract fun handle(event: DomainEvent)
}

/**
 * Los `eventId` ya atendidos por un handler.
 *
 * Acotado a proposito: un proceso que corre meses publicando eventos llenaria un
 * conjunto sin limite hasta tirar la aplicacion, y una fuga de memoria es peor
 * problema que el duplicado del que protege. Al pasarse del tope se olvida lo mas
 * antiguo, que es lo que menos probabilidad tiene de reentregarse.
 */
private class ProcessedEvents(private val capacity: Int = 10_000) {

    private val seen: MutableSet<UUID> = Collections.newSetFromMap(
        object : LinkedHashMap<UUID, Boolean>(16, 0.75f, false) {
            override fun removeEldestEntry(eldest: Map.Entry<UUID, Boolean>): Boolean = size > capacity
        },
    )

    /**
     * Reserva [eventId] y dice si la reserva es **nueva**.
     *
     * Comprobar y reservar tienen que ser un solo paso indivisible: separarlos
     * deja una ventana entre el «no esta» y el «ya esta» por la que caben todas
     * las entregas simultaneas que quepan.
     */
    @Synchronized
    fun claim(eventId: UUID): Boolean = seen.add(eventId)

    @Synchronized
    fun release(eventId: UUID) {
        seen -= eventId
    }
}
