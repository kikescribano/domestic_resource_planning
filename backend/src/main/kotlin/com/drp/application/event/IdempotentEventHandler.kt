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
 * de lo que dice README 5.2.2. Y no basta con capturar la excepcion en quien
 * publica, porque para entonces la transaccion ya esta marcada y el commit falla
 * igual. La unica forma de que un modulo no pueda tumbar al core es que corra
 * cuando ya no hay nada que tumbar, y eso es `AFTER_COMMIT`.
 *
 * Consecuencia que conviene tener presente: aqui dentro **no hay transaccion ni
 * `app.household_id` fijado**. Un handler que toque la base de datos tiene que
 * abrir la suya y situarse en el hogar del evento, que por eso viaja en el sobre.
 *
 * **2. Se aisla.** Su excepcion no sale de aqui. Con un solo difusor, un handler
 * que propaga deja sin evento a los que van detras, asi que aislarse no es
 * cortesia con el core sino con los demas modulos.
 *
 * **3. Es idempotente.** La entrega es at-least-once, asi que se descarta lo ya
 * procesado por `eventId`. Hoy el bus es in-process y sincrono --una publicacion,
 * una entrega-- y el duplicado no llega a darse; la guarda esta puesta desde el
 * principio porque el candidato de evolucion que nombra README 5.2.2, el
 * Transactional Outbox, reentrega **el mismo `eventId`**, y entonces un handler
 * escrito sin ella empieza a duplicar trabajo en silencio. Escribirla despues
 * significaria repasar todos los handlers que existan para entonces.
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
        if (processed.alreadyDone(event.eventId)) {
            log.debug("{} descarta {} {}: ya procesado", handlerName, event.type, event.eventId)
            return
        }

        try {
            handle(event)
            // Se marca **al terminar bien**, no antes: si el handler falla, una
            // reentrega posterior tiene que volver a intentarlo, no saltarselo.
            processed.done(event.eventId)
        } catch (failure: Exception) {
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

    @Synchronized
    fun alreadyDone(eventId: UUID): Boolean = eventId in seen

    @Synchronized
    fun done(eventId: UUID) {
        seen += eventId
    }
}
