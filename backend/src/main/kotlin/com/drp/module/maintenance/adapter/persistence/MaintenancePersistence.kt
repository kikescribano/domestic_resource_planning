package com.drp.module.maintenance.adapter.persistence

import com.drp.module.maintenance.application.DuePlan
import com.drp.module.maintenance.application.MaintenanceRepository
import com.drp.module.maintenance.application.PlanFilter
import com.drp.module.maintenance.domain.DueStage
import com.drp.module.maintenance.domain.InterventionKind
import com.drp.module.maintenance.domain.MaintenanceIntervention
import com.drp.module.maintenance.domain.MaintenanceItem
import com.drp.module.maintenance.domain.MaintenanceMachine
import com.drp.module.maintenance.domain.MaintenancePlan
import com.drp.platform.page.Page
import com.drp.platform.page.Pagination
import com.drp.platform.tenant.TenantContext
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Las tres tablas del modulo, en PostgreSQL.
 *
 * **Por SQL directo y no por JPA**, por lo mismo que Warehouse y Compras: parte de
 * lo que este modulo necesita **no esta en sus tablas**. La lista de maquinas es
 * un `JOIN` de `assets` y `articles` --dos tablas del core-- con una de aqui, y
 * abrir una ficha es una insercion que se apoya en el estado del core para decidir
 * si el asset es elegible. Con entidades JPA habria que declarar las del core en
 * este arbol, que es exactamente la dependencia que la ficha se compromete a no
 * tener: **el modulo lee el estado del core, no su persistencia**.
 *
 * **Ninguna consulta lleva `WHERE household_id`.** Lo pone la politica de RLS a
 * partir del `app.household_id` que fija el gestor de transacciones, igual que en
 * el core. Escribirlo a mano daria la impresion de que es el que protege, y el dia
 * que a alguien se le olvidara en una consulta nueva no fallaria nada. Insertar si
 * lo lleva, porque una fila nueva necesita decir de quien es --y la politica
 * comprueba que coincida.
 */
@Repository
class JdbcMaintenanceRepository(
    private val jdbc: JdbcTemplate,
    private val tenantContext: TenantContext,
) : MaintenanceRepository {

    private val householdId: UUID
        get() = checkNotNull(tenantContext.currentHousehold()) {
            "Mantenimiento no puede escribir sin hogar en el contexto"
        }

    // -----------------------------------------------------------------------
    // La ficha de una maquina
    // -----------------------------------------------------------------------

    /**
     * **`INSERT … SELECT` y no `INSERT … VALUES`**, y esa es la mitad que hace
     * segura a esta funcion.
     *
     * El `SELECT` sobre `assets` filtra por `DURABLE` vivo, asi que un
     * identificador que no sea de una maquina **no escribe nada** en lugar de
     * escribir una ficha huerfana. Es lo que protege la rama de `DocumentAttached`,
     * cuyo `assetId` viene del `payload` y puede apuntar a un consumible o a nada.
     *
     * Y el `ON CONFLICT DO NOTHING` sobre el indice unico es lo que la hace
     * idempotente **sin comprobar antes**: comprobar y despues insertar deja una
     * ventana entre las dos cosas por la que caben dos handlers simultaneos.
     */
    override fun openItem(assetId: UUID, at: Instant, by: UUID?): Boolean = jdbc.update(
        """
        INSERT INTO maintenance_items (id, household_id, asset_id, created_at, updated_at, created_by, updated_by)
        SELECT ?, ?, a.id, ?, ?, ?, ?
          FROM assets a
         WHERE a.id = ? AND a.type = 'DURABLE' AND a.status <> 'DECOMMISSIONED'
        ON CONFLICT (household_id, asset_id) DO NOTHING
        """.trimIndent(),
        UUID.randomUUID(), householdId, at.at(), at.at(), by, by, assetId,
    ) > 0

    override fun findItem(assetId: UUID): MaintenanceItem? = jdbc.query(
        "SELECT * FROM maintenance_items WHERE asset_id = ?",
        ITEM,
        assetId,
    ).firstOrNull()

    override fun saveItem(item: MaintenanceItem): MaintenanceItem {
        jdbc.update(
            """
            UPDATE maintenance_items
               SET manual_document_id = ?, notes = ?, updated_at = ?, updated_by = ?
             WHERE id = ?
            """.trimIndent(),
            item.manualDocumentId, item.notes, item.updatedAt.at(), item.updatedBy, item.id,
        )
        return item
    }

    /**
     * Apunta el manual **abriendo la ficha si falta**, en dos sentencias y no en
     * una: `openItem` ya sabe decidir si el asset es elegible, y repetir esa regla
     * en un `INSERT … ON CONFLICT DO UPDATE` daria dos sitios donde vive lo mismo.
     *
     * El `UPDATE` no lleva ninguna comprobacion de elegibilidad porque no le hace
     * falta: si la ficha no existe --y no existe si el asset no era elegible--
     * afecta a cero filas.
     */
    override fun setManual(assetId: UUID, documentId: UUID, at: Instant, by: UUID?): Boolean {
        openItem(assetId, at, by)
        return jdbc.update(
            """
            UPDATE maintenance_items
               SET manual_document_id = ?, updated_at = ?
             WHERE asset_id = ?
               AND EXISTS (SELECT 1 FROM documents d WHERE d.id = ? AND d.asset_id = ?)
            """.trimIndent(),
            documentId, at.at(), assetId, documentId, assetId,
        ) > 0
    }

    override fun listMachines(query: String?, pagination: Pagination): Page<MaintenanceMachine> {
        val where = """
            WHERE a.type = 'DURABLE' AND a.status <> 'DECOMMISSIONED'
              AND (CAST(? AS text) IS NULL
                   OR lower(immutable_unaccent(coalesce(a.name, ar.name))) LIKE
                      '%' || lower(immutable_unaccent(CAST(? AS text))) || '%')
        """.trimIndent()
        val filters = arrayOf<Any?>(query, query)

        val total = jdbc.queryForObject(
            """
            SELECT count(*)
              FROM maintenance_items i
              JOIN assets a ON a.id = i.asset_id
              LEFT JOIN articles ar ON ar.id = a.article_id
            $where
            """.trimIndent(),
            Long::class.java,
            *filters,
        ) ?: 0L

        val items = jdbc.query(
            """
            SELECT i.asset_id, coalesce(a.name, ar.name) AS asset_name, i.manual_document_id, i.notes,
                   (SELECT count(*) FROM maintenance_plans p
                     WHERE p.asset_id = i.asset_id AND p.cancelled_at IS NULL) AS plan_count,
                   (SELECT min(p.next_due_on) FROM maintenance_plans p
                     WHERE p.asset_id = i.asset_id AND p.cancelled_at IS NULL) AS next_due_on
              FROM maintenance_items i
              JOIN assets a ON a.id = i.asset_id
              LEFT JOIN articles ar ON ar.id = a.article_id
            $where
             ORDER BY next_due_on NULLS LAST, asset_name, i.asset_id
             LIMIT ? OFFSET ?
            """.trimIndent(),
            MACHINE,
            *filters, pagination.size, pagination.page * pagination.size,
        )
        return Page(items, pagination.page, pagination.size, total)
    }

    override fun findMachine(assetId: UUID): MaintenanceMachine? = jdbc.query(
        """
        SELECT i.asset_id, coalesce(a.name, ar.name) AS asset_name, i.manual_document_id, i.notes,
               (SELECT count(*) FROM maintenance_plans p
                 WHERE p.asset_id = i.asset_id AND p.cancelled_at IS NULL) AS plan_count,
               (SELECT min(p.next_due_on) FROM maintenance_plans p
                 WHERE p.asset_id = i.asset_id AND p.cancelled_at IS NULL) AS next_due_on
          FROM maintenance_items i
          JOIN assets a ON a.id = i.asset_id
          LEFT JOIN articles ar ON ar.id = a.article_id
         WHERE i.asset_id = ?
        """.trimIndent(),
        MACHINE,
        assetId,
    ).firstOrNull()

    // -----------------------------------------------------------------------
    // Los planes
    // -----------------------------------------------------------------------

    override fun listPlans(filter: PlanFilter, pagination: Pagination): Page<MaintenancePlan> {
        val where = """
            WHERE (? OR cancelled_at IS NULL)
              AND (CAST(? AS uuid) IS NULL OR asset_id = CAST(? AS uuid))
              AND (CAST(? AS integer) IS NULL
                   OR next_due_on <= current_date + CAST(? AS integer))
              AND (CAST(? AS text) IS NULL
                   OR lower(immutable_unaccent(name)) LIKE
                      '%' || lower(immutable_unaccent(CAST(? AS text))) || '%')
        """.trimIndent()
        val filters = arrayOf<Any?>(
            filter.includeCancelled,
            filter.assetId, filter.assetId,
            filter.dueWithinDays, filter.dueWithinDays,
            filter.query, filter.query,
        )

        val total = jdbc.queryForObject(
            "SELECT count(*) FROM maintenance_plans $where",
            Long::class.java,
            *filters,
        ) ?: 0L

        val items = jdbc.query(
            "SELECT * FROM maintenance_plans $where ORDER BY next_due_on, id LIMIT ? OFFSET ?",
            PLAN,
            *filters, pagination.size, pagination.page * pagination.size,
        )
        return Page(items, pagination.page, pagination.size, total)
    }

    override fun findPlan(planId: UUID): MaintenancePlan? =
        jdbc.query("SELECT * FROM maintenance_plans WHERE id = ?", PLAN, planId).firstOrNull()

    override fun livePlansOf(assetId: UUID): List<MaintenancePlan> = jdbc.query(
        "SELECT * FROM maintenance_plans WHERE asset_id = ? AND cancelled_at IS NULL ORDER BY next_due_on, id",
        PLAN,
        assetId,
    )

    /** Misma normalizacion que el indice `maintenance_plans_one_live_name_per_asset`. */
    override fun findLivePlanByName(assetId: UUID, name: String): MaintenancePlan? = jdbc.query(
        """
        SELECT * FROM maintenance_plans
         WHERE asset_id = ?
           AND cancelled_at IS NULL
           AND lower(immutable_unaccent(name)) = lower(immutable_unaccent(CAST(? AS text)))
        """.trimIndent(),
        PLAN,
        assetId, name,
    ).firstOrNull()

    override fun savePlan(plan: MaintenancePlan): MaintenancePlan {
        val updated = jdbc.update(
            """
            UPDATE maintenance_plans
               SET name = ?, interval_months = ?, lead_days = ?, next_due_on = ?, last_performed_on = ?,
                   supplier_id = ?, notes = ?, notified_stage = ?, notified_for = ?, cancelled_at = ?,
                   updated_at = ?, updated_by = ?
             WHERE id = ?
            """.trimIndent(),
            plan.name, plan.intervalMonths, plan.leadDays, plan.nextDueOn, plan.lastPerformedOn,
            plan.supplierId, plan.notes, plan.notifiedStage?.name, plan.notifiedFor, plan.cancelledAt.at(),
            plan.updatedAt.at(), plan.updatedBy, plan.id,
        )
        if (updated == 0) {
            jdbc.update(
                """
                INSERT INTO maintenance_plans (
                    id, household_id, asset_id, name, interval_months, lead_days, next_due_on,
                    last_performed_on, supplier_id, notes, notified_stage, notified_for, cancelled_at,
                    created_at, updated_at, created_by, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                plan.id, householdId, plan.assetId, plan.name, plan.intervalMonths, plan.leadDays,
                plan.nextDueOn, plan.lastPerformedOn, plan.supplierId, plan.notes,
                plan.notifiedStage?.name, plan.notifiedFor, plan.cancelledAt.at(),
                plan.createdAt.at(), plan.updatedAt.at(), plan.createdBy, plan.updatedBy,
            )
        }
        return plan
    }

    override fun cancelPlansOf(assetId: UUID, at: Instant, by: UUID?): Int = jdbc.update(
        """
        UPDATE maintenance_plans
           SET cancelled_at = ?, updated_at = ?, updated_by = ?
         WHERE asset_id = ? AND cancelled_at IS NULL
        """.trimIndent(),
        at.at(), at.at(), by, assetId,
    )

    /**
     * Los candidatos, con el nombre de la maquina ya resuelto.
     *
     * **Sin suelo a proposito**: un plan que se paso hace tres meses tiene que
     * seguir apareciendo, y un `BETWEEN current_date AND ?` habria dejado fuera
     * justo a los vencidos.
     */
    override fun plansDueBy(horizon: LocalDate): List<DuePlan> = jdbc.query(
        """
        SELECT p.*, coalesce(a.name, ar.name) AS asset_name
          FROM maintenance_plans p
          JOIN assets a ON a.id = p.asset_id
          LEFT JOIN articles ar ON ar.id = a.article_id
         WHERE p.cancelled_at IS NULL AND p.next_due_on <= ?
         ORDER BY p.next_due_on, p.id
        """.trimIndent(),
        { rows, index -> DuePlan(PLAN.mapRow(rows, index)!!, rows.getString("asset_name").orEmpty()) },
        horizon,
    )

    override fun markPlanNotified(planId: UUID, stage: DueStage, forDate: LocalDate, at: Instant) {
        jdbc.update(
            "UPDATE maintenance_plans SET notified_stage = ?, notified_for = ?, updated_at = ? WHERE id = ?",
            stage.name, forDate, at.at(), planId,
        )
    }

    // -----------------------------------------------------------------------
    // El historico
    // -----------------------------------------------------------------------

    override fun listInterventions(
        assetId: UUID?,
        planId: UUID?,
        pagination: Pagination,
    ): Page<MaintenanceIntervention> {
        val where = """
            WHERE (CAST(? AS uuid) IS NULL OR asset_id = CAST(? AS uuid))
              AND (CAST(? AS uuid) IS NULL OR plan_id = CAST(? AS uuid))
        """.trimIndent()
        val filters = arrayOf<Any?>(assetId, assetId, planId, planId)

        val total = jdbc.queryForObject(
            "SELECT count(*) FROM maintenance_interventions $where",
            Long::class.java,
            *filters,
        ) ?: 0L

        val items = jdbc.query(
            "SELECT * FROM maintenance_interventions $where ORDER BY performed_on DESC, id LIMIT ? OFFSET ?",
            INTERVENTION,
            *filters, pagination.size, pagination.page * pagination.size,
        )
        return Page(items, pagination.page, pagination.size, total)
    }

    override fun recentInterventions(assetId: UUID?, planId: UUID?, limit: Int): List<MaintenanceIntervention> =
        jdbc.query(
            """
            SELECT * FROM maintenance_interventions
             WHERE (CAST(? AS uuid) IS NULL OR asset_id = CAST(? AS uuid))
               AND (CAST(? AS uuid) IS NULL OR plan_id = CAST(? AS uuid))
             ORDER BY performed_on DESC, id
             LIMIT ?
            """.trimIndent(),
            INTERVENTION,
            assetId, assetId, planId, planId, limit,
        )

    override fun record(intervention: MaintenanceIntervention): MaintenanceIntervention {
        jdbc.update(
            """
            INSERT INTO maintenance_interventions (
                id, household_id, asset_id, plan_id, kind, performed_on, summary,
                supplier_id, supplier_name, notes, created_at, created_by
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            intervention.id, householdId, intervention.assetId, intervention.planId,
            intervention.kind.name, intervention.performedOn, intervention.summary,
            intervention.supplierId, intervention.supplierName, intervention.notes,
            intervention.createdAt.at(), intervention.createdBy,
        )
        return intervention
    }

    // -----------------------------------------------------------------------
    // Lo que se lee del core
    // -----------------------------------------------------------------------

    override fun isEligibleAsset(assetId: UUID): Boolean = jdbc.queryForObject(
        "SELECT count(*) FROM assets WHERE id = ? AND type = 'DURABLE' AND status <> 'DECOMMISSIONED'",
        Int::class.java,
        assetId,
    ) == 1

    override fun eligibleAssetIds(): List<UUID> = jdbc.query(
        "SELECT id FROM assets WHERE type = 'DURABLE' AND status <> 'DECOMMISSIONED' ORDER BY id",
        { rows, _ -> rows.uuid("id")!! },
    )

    override fun assetName(assetId: UUID): String? = jdbc.query(
        """
        SELECT coalesce(a.name, ar.name) AS asset_name
          FROM assets a
          LEFT JOIN articles ar ON ar.id = a.article_id
         WHERE a.id = ?
        """.trimIndent(),
        { rows, _ -> rows.getString("asset_name") },
        assetId,
    ).firstOrNull()

    override fun isDocumentOf(documentId: UUID, assetId: UUID): Boolean = jdbc.queryForObject(
        "SELECT count(*) FROM documents WHERE id = ? AND asset_id = ?",
        Int::class.java,
        documentId, assetId,
    ) == 1

    private companion object {

        val ITEM = RowMapper { rows, _ ->
            MaintenanceItem(
                id = rows.uuid("id")!!,
                assetId = rows.uuid("asset_id")!!,
                manualDocumentId = rows.uuid("manual_document_id"),
                notes = rows.getString("notes"),
                createdAt = rows.instant("created_at")!!,
                updatedAt = rows.instant("updated_at")!!,
                createdBy = rows.uuid("created_by"),
                updatedBy = rows.uuid("updated_by"),
            )
        }

        val PLAN = RowMapper { rows, _ ->
            MaintenancePlan(
                id = rows.uuid("id")!!,
                assetId = rows.uuid("asset_id")!!,
                name = rows.getString("name"),
                intervalMonths = rows.getInt("interval_months"),
                leadDays = rows.getInt("lead_days"),
                nextDueOn = rows.date("next_due_on")!!,
                lastPerformedOn = rows.date("last_performed_on"),
                supplierId = rows.uuid("supplier_id"),
                notes = rows.getString("notes"),
                notifiedStage = rows.getString("notified_stage")?.let { DueStage.valueOf(it) },
                notifiedFor = rows.date("notified_for"),
                cancelledAt = rows.instant("cancelled_at"),
                createdAt = rows.instant("created_at")!!,
                updatedAt = rows.instant("updated_at")!!,
                createdBy = rows.uuid("created_by"),
                updatedBy = rows.uuid("updated_by"),
            )
        }

        val INTERVENTION = RowMapper { rows, _ ->
            MaintenanceIntervention(
                id = rows.uuid("id")!!,
                assetId = rows.uuid("asset_id")!!,
                planId = rows.uuid("plan_id"),
                kind = InterventionKind.valueOf(rows.getString("kind")),
                performedOn = rows.date("performed_on")!!,
                summary = rows.getString("summary"),
                supplierId = rows.uuid("supplier_id"),
                supplierName = rows.getString("supplier_name"),
                notes = rows.getString("notes"),
                createdAt = rows.instant("created_at")!!,
                createdBy = rows.uuid("created_by"),
            )
        }

        val MACHINE = RowMapper { rows, _ ->
            MaintenanceMachine(
                assetId = rows.uuid("asset_id")!!,
                assetName = rows.getString("asset_name").orEmpty(),
                manualDocumentId = rows.uuid("manual_document_id"),
                notes = rows.getString("notes"),
                planCount = rows.getInt("plan_count"),
                nextDueOn = rows.date("next_due_on"),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Conversiones de apoyo
// ---------------------------------------------------------------------------

/**
 * `Instant` a lo que el driver entiende.
 *
 * Va por `OffsetDateTime` y no por `Timestamp`, igual que en los dos modulos
 * anteriores: `Timestamp` interpreta el instante en la zona de la JVM y una prueba
 * que corriera en otra zona veria fechas distintas de las que escribio.
 */
private fun Instant?.at(): OffsetDateTime? = this?.atOffset(ZoneOffset.UTC)

private fun ResultSet.uuid(column: String): UUID? = getObject(column, UUID::class.java)

private fun ResultSet.date(column: String): LocalDate? = getObject(column, LocalDate::class.java)

private fun ResultSet.instant(column: String): Instant? =
    getObject(column, OffsetDateTime::class.java)?.toInstant()
