package com.drp.domain.event

import java.time.Instant
import java.util.UUID

/**
 * La forma unica de todo evento que publica el core (ver README 5.2.1).
 *
 * Lleva un campo mas que el boceto de la Fase 0: **`householdId`**. No es un
 * adorno. Un handler corre `AFTER_COMMIT`, es decir, cuando la transaccion que
 * origino el evento ya se cerro y con ella el `app.household_id` que fijaba el
 * `SET LOCAL`. Si el handler necesita tocar la base de datos, tiene que volver a
 * fijar el hogar, y el unico sitio de donde puede sacarlo con certeza es el
 * propio evento: la peticion que lo provoco ya no existe. Meterlo en el
 * `payload` lo dejaria a criterio de cada emisor, que es justo como se olvida.
 *
 * El `payload` va como mapa y no como clase por tipo de evento a proposito: el
 * bus es in-process y ningun modulo existe todavia, asi que congelar hoy una
 * clase por evento seria fijar una forma que nadie ha usado. El `version` esta
 * para cuando eso cambie.
 */
data class DomainEvent(
    val eventId: UUID,
    val type: String,
    val occurredAt: Instant,
    val householdId: UUID,
    val aggregateId: String,
    val version: Int,
    val payload: Map<String, Any?>,
) {
    companion object {
        /** Version del esquema de todos los eventos del catalogo inicial. */
        const val INITIAL_VERSION = 1
    }
}
