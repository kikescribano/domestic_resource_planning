package com.drp.platform.event

import java.time.Instant
import java.util.UUID

/**
 * La cola de eventos publicados y todavia sin repartir (ADR-013).
 *
 * Es lo unico que hace que un evento **no se pierda**. Hasta el outbox, entre el
 * `COMMIT` del caso de uso y el `AFTER_COMMIT` que reparte habia una ventana en
 * la que caerse significaba perder el evento: sin error, sin traza y sin nada que
 * lo delatara salvo un modulo que no se entero. [record] escribe la fila
 * **dentro de la transaccion del caso de uso**, asi que o se guardan las dos
 * cosas o no se guarda ninguna.
 *
 * **Lo que esto no cambia es la garantia.** La entrega sigue siendo
 * at-least-once y los handlers siguen teniendo que ser idempotentes; lo que el
 * outbox anade es que el evento no se pierda, no que llegue una sola vez.
 *
 * **Una fila es lo pendiente, y desaparece al confirmarse.** No hay estado
 * intermedio ni columna de entregado: el estado normal de la tabla es **vacia**,
 * y que crezca es el sintoma. Conservar la fila entregada habria dado un
 * registro de lo publicado a cambio de una sexta tabla que crece sin techo y de
 * una segunda copia de cada `payload`; la ADR-013 lo razona y lo descarta.
 *
 * Es un puerto de **plataforma** y no nombra a ningun modulo: el outbox no sabe
 * quien escucha, igual que el bus.
 */
interface EventOutbox {

    /**
     * Deja constancia de [event] **en la transaccion que este abierta**.
     *
     * Esa propagacion es la decision entera: unirse a la transaccion del caso de
     * uso es lo que hace atomicos el cambio de estado y su evento. Si no hubiera
     * ninguna transaccion abierta --publicar fuera de una peticion es legitimo--
     * se abre una propia, y el hogar sale del **sobre del evento**, que es el
     * unico sitio que lo situa con certeza.
     *
     * Recordar dos veces el mismo `eventId` es la misma fila y no un error: la
     * clave primaria es el identificador del evento.
     */
    fun record(event: DomainEvent)

    /**
     * Da por repartido [event] y **borra su fila**.
     *
     * Recibe el evento entero y no solo su identificador porque hace falta el
     * hogar: la fila esta protegida por la politica igual que cualquier otra, y
     * quien confirma corre fuera de la transaccion que la escribio --a veces
     * fuera de toda peticion--. El unico sitio que situa a esto con certeza es
     * el sobre del evento, que es el mismo motivo por el que `ModuleEventHandler`
     * saca el hogar de ahi y no del contexto.
     *
     * Se invoca despues de repartir y nunca antes. Las dos opciones son
     * at-least-once imperfectas y hay que elegir cual falla mejor, que es la
     * misma eleccion que la ADR-011 hizo con el resumen diario: confirmar antes
     * y caerse pierde el evento para siempre --nadie vuelve a mirarlo-- mientras
     * que repartir y caerse antes de confirmar lo repite. Repetir se nota y se
     * aguanta; perder, ni se nota.
     */
    fun confirm(event: DomainEvent)

    /**
     * Que hogares tienen algo pendiente de antes de [createdBefore].
     *
     * Es la pregunta con la que arranca cada pasada del relay, y va contra la
     * funcion acotada `list_households_with_pending_events` --solo
     * identificadores de hogar, de `drp_resolver`, sin `BYPASSRLS`-- porque
     * fuera de un hogar la politica no deja ver ninguna fila. Sin ella el relay
     * recorreria en vacio todos los hogares de la instalacion cada pocos
     * segundos.
     */
    fun householdsWithPendingEvents(createdBefore: Instant): List<UUID>

    /**
     * Lo pendiente **del hogar actual**, de lo mas antiguo a lo mas nuevo.
     *
     * Sin `WHERE household_id`: lo pone la politica, a partir del
     * `app.household_id` que la transaccion del relay acaba de fijar. Es lo que
     * hace imposible que una pasada mal escrita reparta el evento del hogar de
     * al lado.
     */
    fun pending(createdBefore: Instant, limit: Int): List<DomainEvent>
}
