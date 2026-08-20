package com.drp.platform.event

import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
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
 * dos casos que si pueden darse: varios hilos publicando el mismo evento, y un
 * handler que republique el suyo y se reentre.
 *
 * **Y aqui hay una guarda que se mudo, que es lo que este comentario prometia**
 * (ADR-013). La version anterior razonaba que lo recordado vivia en memoria
 * «porque el bus tambien: un reinicio no reentrega nada --pierde el evento--,
 * asi que persistirlo no protegeria de nada que pueda pasar hoy», y cerraba con
 * «el dia que haya outbox, esa guarda se muda con el». Ese dia llego: tras un
 * reinicio, `OutboxRelay` reentrega **el mismo `eventId`**.
 *
 * Lo que se mudo es **la mitad duradera**, y su casa es la fila del outbox: el
 * `eventId` es la clave primaria de `event_outbox`, la fila se reserva al
 * publicar y se borra al repartir, y esa es la unica reserva que sobrevive a un
 * reinicio. Lo que queda aqui es la mitad que **solo tiene sentido dentro de un
 * proceso** --los dos casos del parrafo anterior-- y para la que una tabla no
 * anadiria nada.
 *
 * Se descarto una tabla de `(handler, eventId)`, y por dos motivos que conviene
 * dejar escritos. **No cerraria la ventana, solo la estrecharia**, salvo que se
 * escribiera dentro de la misma transaccion que el efecto del handler --y eso
 * seria prometer exactamente-una-vez por handler, que es justo lo que la ADR-013
 * se niega a prometer--. Y **no le haria falta a ningun handler que exista**:
 * los tres modulos desplegados ya son idempotentes por construccion en sus
 * propias tablas, con un indice unico por `event_id` en Warehouse y uno por
 * asset o por articulo en CMMS y en Compras, que es la reserva puesta donde si
 * puede ser transaccional con el efecto. Hay una prueba que lo mide.
 *
 * **La guarda no exime al handler de ser idempotente por dentro.** Es una red,
 * no una garantia: solo cubre reentregas del mismo `eventId` a este mismo
 * proceso. Un handler cuya operacion no se pueda repetir sin dano sigue estando
 * mal escrito --y con outbox lo esta mas, porque ahora las reentregas ocurren de
 * verdad.
 */
abstract class IdempotentEventHandler(private val handlerName: String) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val processed = ProcessedEvents()

    /**
     * `fallbackExecution` a true para que el handler tambien corra cuando quien
     * publica no tiene transaccion abierta. Sin eso, un evento publicado fuera de
     * una transaccion se descarta sin ruido, que es la clase de perdida que no se
     * nota hasta que un modulo pregunta por que no recibio nada.
     *
     * **Y desde el outbox tiene un segundo sujeto, que es el que lo hace
     * imprescindible** (ADR-013): `OutboxRelay` reparte **fuera de toda
     * transaccion**, asi que sin `fallbackExecution` el camino de recuperacion
     * entero no entregaria nada --y no entregarlo se veria exactamente igual que
     * no tener nada que entregar--. Hay una prueba que lo mide en las dos
     * direcciones.
     *
     * El `@Order` no es decoracion, y su valor tampoco. `SpringEventBus` borra la
     * fila del outbox con una sincronizacion de esta misma fase, y **tiene que ir
     * detras del ultimo handler**: confirmar antes de repartir daria por
     * entregado lo que aun no ha salido. Las sincronizaciones se ordenan por
     * [Ordered], asi que sin declarar nada un handler valdria `LOWEST_PRECEDENCE`
     * --lo mismo que la confirmacion-- y el desempate no seria de nadie.
     *
     * **Un escalon por delante del ultimo, y no el primero.** La tentacion es
     * `@Order(0)`, y adelanta a los handlers respecto a cualquier
     * `@EventListener` con orden declarado --lo mide `EventBusSweepTest`, que
     * tiene tres con ordenes 1, 2 y 3--. Cambiar quien recibe antes que quien no
     * es asunto de esta clase: lo unico que hace falta es quedar **antes de la
     * confirmacion**, y eso se dice con un escalon, no con un salto a la cabeza.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Order(Ordered.LOWEST_PRECEDENCE - 1)
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
