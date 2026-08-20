package com.drp.platform.event

import com.drp.platform.tenant.TenantContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * El camino de recuperacion del outbox: reparte lo que nadie confirmo (ADR-013).
 *
 * **Reutiliza la forma de `DailySweep`, pero no su vehiculo.** La forma si:
 * hogar a hogar, fijando `app.household_id` en cada transaccion y **nunca con
 * `BYPASSRLS`** --la salida facil desactivaria la segunda capa para toda la
 * aplicacion y no solo para este proceso, que es lo que la ADR-003 prohibe--. El
 * vehiculo no: **esto no es una `ScheduledCheck`**. Un evento que tarda un dia en
 * llegar a Warehouse no es una entrega diferida, es una entrega rota, asi que el
 * relay tiene su propio periodo y se mide en segundos.
 *
 * Y para no recorrer en vacio mil hogares cada pocos segundos, empieza por la
 * pregunta estrecha --que hogares tienen algo pendiente-- que responde la funcion
 * acotada de la V15: solo identificadores, de `drp_resolver`, sin `BYPASSRLS`.
 *
 * ### El periodo de gracia, que es lo que evita que esto duplique el trabajo
 *
 * El outbox **no sustituye al reparto en el acto**: convive con el (ADR-013).
 * `SpringEventBus` escribe la fila, reparte despues del commit y confirma
 * borrandola, todo en la misma peticion y en milisegundos. Si el relay mirase la
 * cola entera, cogeria por sistema filas que estan a punto de confirmarse y
 * repartiria dos veces **en el caso normal**, no en el excepcional.
 *
 * De ahi que solo mire lo que ya deberia haber llegado: filas de hace mas de
 * [grace]. Lo que sobrevive a esa ventana es, por definicion, lo que nadie
 * repartio --el proceso se cayo entre el commit y el reparto, o el difusor corto
 * antes de llegar a los handlers--, que es exactamente lo que este hito existe
 * para recoger.
 *
 * ### Lo que el relay garantiza, y lo que no
 *
 * Garantiza que el evento **se reparte**. No garantiza que cada handler termine
 * bien: un handler que falla se aisla como siempre y no se reintenta, porque la
 * fila es del evento y no de cada suscriptor --reintentarla volveria a llamar a
 * los que si acabaron--. Es la misma frontera que el bus tenia desde la Fase 1, y
 * el outbox no la mueve.
 *
 * ### Se apaga, y hace falta que se pueda
 *
 * Con el encendido, una pasada correria **cada pocos segundos dentro de cualquier
 * contexto que la suite levante**, sobre la base que todas comparten y a mitad de
 * otra prueba. El programador de la ADR-011 ya se apaga con
 * `drp.schedule.enabled`, pero eso no basta: hay una prueba que lo enciende a
 * proposito --`SchedulingEnabledTest`-- y ahi el relay tiene que seguir apagado.
 * De ahi un interruptor propio, y medido en los dos sentidos como el otro: un
 * `@ConditionalOnProperty` mal escrito compila igual.
 *
 * Apagarlo en un despliegue tambien es legitimo --una segunda instancia, una
 * ventana de mantenimiento-- y no pierde nada: las filas se quedan en la cola y
 * la pasada siguiente las recoge.
 */
@Component
@ConditionalOnProperty(name = ["drp.outbox.enabled"], havingValue = "true", matchIfMissing = true)
class OutboxRelay(
    private val outbox: EventOutbox,
    private val tenantContext: TenantContext,
    private val publisher: ApplicationEventPublisher,
    private val clock: Clock,
    @Value("\${drp.outbox.grace}") private val grace: Duration,
    @Value("\${drp.outbox.batch-size}") private val batchSize: Int,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Una pasada.
     *
     * `@Scheduled` va aqui y no en una envoltura, igual que en `DailySweep`: asi
     * lo que invocan las pruebas es **el mismo metodo** que invoca el
     * programador, y no un hermano que se le parece.
     *
     * `fixedDelay` y no `fixedRate`: lo que hay que separar es el final de una
     * pasada del principio de la siguiente. Con `fixedRate`, una pasada larga
     * --mil eventos atrasados tras un reinicio-- se solaparia consigo misma y
     * repartiria dos veces lo mismo por pura carrera.
     */
    @Scheduled(
        fixedDelayString = "\${drp.outbox.period}",
        initialDelayString = "\${drp.outbox.period}",
    )
    fun run(): RelayReport {
        // El corte se calcula **una vez** por pasada. Recalcularlo por hogar
        // haria que la ventana se moviera mientras se recorre, y dos hogares
        // seguidos se medirian con reglas distintas.
        val threshold = clock.instant() - grace
        val households = outbox.householdsWithPendingEvents(threshold)
        if (households.isEmpty()) return RelayReport(0, 0)

        var redelivered = 0
        for (householdId in households) {
            redelivered += runCatching { deliverPending(householdId, threshold) }
                // Un hogar que falle no puede dejar sin pasada a los que vienen
                // detras, que es la misma red que la ADR-011 le puso al recorrido
                // diario. Aqui hay ademas un caso que no es un fallo: **el hogar
                // puede haber dejado de existir** entre que se listo y se
                // procesa, porque la purga de la ADR-012 borra hogares con datos
                // dentro. Eso no revienta --la politica no deja ver ninguna fila
                // de un hogar borrado-- y devuelve cero.
                .onFailure { log.error("El reparto pendiente falló en el hogar {}", householdId, it) }
                .getOrDefault(0)
        }

        log.info(
            "Relay del outbox: {} hogares con pendientes, {} eventos repartidos de nuevo",
            households.size,
            redelivered,
        )
        return RelayReport(households.size, redelivered)
    }

    /**
     * Lo pendiente de un hogar, repartido **fuera de toda transaccion**.
     *
     * Que sea fuera no es un detalle: los handlers heredan de
     * [IdempotentEventHandler], que corre en `AFTER_COMMIT`. Repartiendo dentro
     * de una transaccion abierta, el reparto quedaria **aplazado** al cierre de
     * esa transaccion y la confirmacion podria adelantarsele. Fuera, el
     * `fallbackExecution` de la clase base los ejecuta en el acto y al volver de
     * `publishEvent` el reparto ha terminado de verdad.
     *
     * La lectura si va en transaccion, que es la que fija `app.household_id`, y
     * la abre el propio adaptador.
     */
    private fun deliverPending(householdId: UUID, threshold: Instant): Int =
        tenantContext.runAs(householdId) {
            val pending = outbox.pending(threshold, batchSize)

            for (event in pending) {
                log.warn(
                    "Reparto pendiente de {} {} en el hogar {}: nadie lo confirmó",
                    event.type,
                    event.eventId,
                    householdId,
                )
                // Con su red, por lo mismo que en `SpringEventBus`: un
                // suscriptor mal escrito que propague no puede cortar la pasada.
                runCatching { publisher.publishEvent(event) }
                    .onFailure { log.error("Un suscriptor falló atendiendo {} {}", event.type, event.eventId, it) }
                outbox.confirm(event)
            }

            pending.size
        }
}

/** Lo que hizo una pasada. Se registra, y las pruebas lo miran. */
data class RelayReport(
    val householdsWithPending: Int,
    val redelivered: Int,
)
