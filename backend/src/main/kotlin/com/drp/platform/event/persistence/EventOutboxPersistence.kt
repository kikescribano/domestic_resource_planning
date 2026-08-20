package com.drp.platform.event.persistence

import com.drp.platform.event.DomainEvent
import com.drp.platform.event.EventOutbox
import com.drp.platform.tenant.TenantContext
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.sql.ResultSet
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * La cola del outbox contra PostgreSQL (ADR-013).
 *
 * Va por `JdbcTemplate` y no por JPA a proposito, por lo mismo que el resolutor
 * de inquilino: son cuatro sentencias sobre una tabla sin entidad de dominio
 * detras --el `payload` es `jsonb` y en el codigo es un mapa-- y dejarlas a la
 * vista en SQL plano hace evidente lo unico que importa de ellas, que es **con
 * que transaccion corre cada una**.
 *
 * Y ahi estan las tres decisiones de esta clase:
 *
 * **1. [record] se une a la transaccion que haya.** Es la decision entera del
 * patron: la fila del evento y el cambio de estado que lo produjo se guardan
 * juntos o no se guarda ninguno. Una propagacion `REQUIRES_NEW` aqui seria un
 * outbox que no sirve para nada --dejaria eventos de transacciones que despues
 * se deshacen-- y una `NOT_SUPPORTED`, lo mismo al reves.
 *
 * **2. [confirm] abre la suya.** Corre despues de repartir, es decir, cuando la
 * transaccion que escribio la fila ya se cerro. Es la misma forma que
 * `ModuleEventHandler` usa desde el Hito 0 de la Fase 2 y por el mismo motivo:
 * unirse a una transaccion cerrada no borra nada y no avisa de ello.
 *
 * **3. El hogar sale siempre del sobre del evento**, con `runAs`, y nunca del
 * contexto. Quien escribe y quien confirma pueden estar dentro de una peticion o
 * dentro de un recorrido periodico, y solo el evento los situa con certeza.
 */
@Repository
class EventOutboxPersistence(
    private val jdbc: JdbcTemplate,
    private val json: ObjectMapper,
    private val tenantContext: TenantContext,
    transactionManager: PlatformTransactionManager,
) : EventOutbox {

    /** Se une a la del caso de uso. Ver la decision 1 de la clase. */
    private val joiningTheCaller = TransactionTemplate(transactionManager)

    /** La suya propia. Ver la decision 2 de la clase. */
    private val onItsOwn = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    /**
     * `ON CONFLICT DO NOTHING` y no un error: la clave de la tabla **es** el
     * `eventId`, asi que recordar dos veces el mismo evento es la misma fila. El
     * caso se da de verdad --un handler que republica el evento que esta
     * atendiendo, y varios hilos publicando el mismo-- y esta medido en
     * `EventBusSweepTest` desde la Fase 1; reventar aqui convertiria una
     * reentrada inofensiva en un fallo del caso de uso que la origino.
     */
    override fun record(event: DomainEvent) {
        tenantContext.runAs(event.householdId) {
            joiningTheCaller.executeWithoutResult {
                jdbc.update(
                    """
                    INSERT INTO event_outbox
                        (event_id, household_id, type, occurred_at, aggregate_id, version, payload)
                    VALUES (?, ?, ?, ?, ?, ?, ?::jsonb)
                    ON CONFLICT (event_id) DO NOTHING
                    """.trimIndent(),
                    event.eventId,
                    event.householdId,
                    event.type,
                    event.occurredAt.atUtc(),
                    event.aggregateId,
                    event.version,
                    json.writeValueAsString(event.payload),
                )
            }
        }
    }

    override fun confirm(event: DomainEvent) {
        tenantContext.runAs(event.householdId) {
            onItsOwn.executeWithoutResult {
                jdbc.update("DELETE FROM event_outbox WHERE event_id = ?", event.eventId)
            }
        }
    }

    override fun householdsWithPendingEvents(createdBefore: Instant): List<UUID> =
        jdbc.queryForList(
            "SELECT * FROM list_households_with_pending_events(?)",
            UUID::class.java,
            createdBefore.atUtc(),
        )

    override fun pending(createdBefore: Instant, limit: Int): List<DomainEvent> =
        joiningTheCaller.execute {
            jdbc.query(
                """
                SELECT event_id, household_id, type, occurred_at, aggregate_id, version, payload
                FROM event_outbox
                WHERE created_at < ?
                ORDER BY created_at
                LIMIT ?
                """.trimIndent(),
                { rows, _ -> rows.toDomainEvent() },
                createdBefore.atUtc(),
                limit,
            )
        }.orEmpty()

    private fun ResultSet.toDomainEvent() = DomainEvent(
        eventId = getObject("event_id", UUID::class.java),
        type = getString("type"),
        occurredAt = getObject("occurred_at", OffsetDateTime::class.java).toInstant(),
        householdId = getObject("household_id", UUID::class.java),
        aggregateId = getString("aggregate_id"),
        version = getInt("version"),
        payload = json.readValue(getString("payload"), PAYLOAD),
    )

    private companion object {
        /**
         * El `payload` vuelve como mapa de tipos de JSON --texto, numero,
         * booleano, mapa-- y **no como los tipos con los que se publico**. No es
         * un descuido de esta clase sino la unica lectura posible de un `jsonb`
         * sin una clase por tipo de evento, y los handlers ya la contemplan: los
         * de Warehouse y CMMS aceptan desde la Fase 2 las dos o tres formas en
         * las que un numero y un identificador pueden llegarles, con el motivo
         * escrito en su propio comentario --«la forma exacta depende de por donde
         * haya pasado el evento».
         */
        val PAYLOAD = object : TypeReference<Map<String, Any?>>() {}
    }
}

/**
 * `timestamptz` sin depender de la zona de la maquina.
 *
 * `java.sql.Timestamp` obligaria al controlador a interpretar el instante con la
 * zona de la sesion; un `OffsetDateTime` en UTC no deja nada que interpretar, que
 * es lo que se quiere de una columna que compara instantes.
 */
private fun Instant.atUtc(): OffsetDateTime = atOffset(ZoneOffset.UTC)
