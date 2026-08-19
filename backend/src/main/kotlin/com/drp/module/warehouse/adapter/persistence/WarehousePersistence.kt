package com.drp.module.warehouse.adapter.persistence

import com.drp.module.warehouse.application.CoreStockFacts
import com.drp.module.warehouse.application.DueLot
import com.drp.module.warehouse.application.StockFilter
import com.drp.module.warehouse.application.WarehouseRepository
import com.drp.module.warehouse.domain.ArticleStockTotal
import com.drp.module.warehouse.domain.ExpiryStage
import com.drp.module.warehouse.domain.MovementKind
import com.drp.module.warehouse.domain.StockItem
import com.drp.module.warehouse.domain.StockLot
import com.drp.module.warehouse.domain.StockMovement
import com.drp.module.warehouse.domain.WarehouseArticle
import com.drp.module.warehouse.domain.WarehouseLocation
import com.drp.platform.page.Page
import com.drp.platform.page.Pagination
import com.drp.platform.tenant.TenantContext
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Las cuatro tablas del modulo, en PostgreSQL.
 *
 * **Por SQL directo y no por JPA**, al contrario que Proveedores, y es una
 * decision y no una preferencia: la mitad de lo que este modulo necesita **no
 * esta en sus tablas**. La lista de existencias es un `JOIN` de `assets`,
 * `articles` y `locations` --tres tablas del core-- con una de aqui, y el total
 * de un articulo es una suma sobre `assets`. Con entidades JPA habria que
 * declarar las del core en este arbol, que es exactamente la dependencia que la
 * ficha se compromete a no tener: **el modulo lee el estado del core, no su
 * persistencia**.
 *
 * **Ninguna consulta lleva `WHERE household_id`.** Lo pone la politica de RLS a
 * partir del `app.household_id` que fija el gestor de transacciones, igual que en
 * el core. Escribirlo a mano daria la impresion de que es el que protege, y el
 * dia que a alguien se le olvidara en una consulta nueva no fallaria nada.
 * Insertar si lo lleva, porque una fila nueva necesita decir de quien es --y la
 * politica comprueba que coincida.
 */
@Repository
class JdbcWarehouseRepository(
    private val jdbc: JdbcTemplate,
    private val tenantContext: TenantContext,
) : WarehouseRepository {

    private val householdId: UUID
        get() = checkNotNull(tenantContext.currentHousehold()) {
            "Warehouse no puede escribir sin hogar en el contexto"
        }

    // -----------------------------------------------------------------------
    // Las dos fichas
    // -----------------------------------------------------------------------

    /**
     * `ON CONFLICT DO NOTHING` sobre el indice unico, que es lo que la hace
     * idempotente **sin comprobar antes**: comprobar y despues insertar deja una
     * ventana entre las dos cosas por la que caben dos handlers simultaneos.
     */
    override fun openArticleFile(articleId: UUID, at: Instant, by: UUID?) {
        jdbc.update(
            """
            INSERT INTO warehouse_articles (id, household_id, article_id, created_at, updated_at, created_by, updated_by)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (household_id, article_id) DO NOTHING
            """.trimIndent(),
            UUID.randomUUID(), householdId, articleId, at.at(), at.at(), by, by,
        )
    }

    override fun openLocationFile(locationId: UUID, at: Instant, by: UUID?) {
        jdbc.update(
            """
            INSERT INTO warehouse_locations (id, household_id, location_id, created_at, updated_at, created_by, updated_by)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (household_id, location_id) DO NOTHING
            """.trimIndent(),
            UUID.randomUUID(), householdId, locationId, at.at(), at.at(), by, by,
        )
    }

    override fun findArticleFile(articleId: UUID): WarehouseArticle? = jdbc.query(
        "SELECT * FROM warehouse_articles WHERE article_id = ?",
        ARTICLE_FILE,
        articleId,
    ).firstOrNull()

    override fun findLocationFile(locationId: UUID): WarehouseLocation? = jdbc.query(
        "SELECT * FROM warehouse_locations WHERE location_id = ?",
        LOCATION_FILE,
        locationId,
    ).firstOrNull()

    override fun saveArticleFile(file: WarehouseArticle): WarehouseArticle {
        jdbc.update(
            """
            UPDATE warehouse_articles
               SET minimum_quantity = ?, expiry_lead_days = ?, low_stock_since = ?,
                   low_stock_notified_at = ?, updated_at = ?, updated_by = ?
             WHERE id = ?
            """.trimIndent(),
            file.minimumQuantity, file.expiryLeadDays, file.lowStockSince.at(),
            file.lowStockNotifiedAt.at(), file.updatedAt.at(), file.updatedBy, file.id,
        )
        return file
    }

    override fun saveLocationFile(file: WarehouseLocation): WarehouseLocation {
        jdbc.update(
            """
            UPDATE warehouse_locations
               SET expiry_lead_days = ?, notes = ?, updated_at = ?, updated_by = ?
             WHERE id = ?
            """.trimIndent(),
            file.expiryLeadDays, file.notes, file.updatedAt.at(), file.updatedBy, file.id,
        )
        return file
    }

    // -----------------------------------------------------------------------
    // El cuaderno
    // -----------------------------------------------------------------------

    /**
     * Los dos `ON CONFLICT` no son el mismo caso y por eso van los dos: uno
     * descarta el evento ya asentado y el otro, el segundo `OPENING` de una
     * existencia que llega al resembrar. `DO NOTHING` sin columnas cubre
     * cualquiera de los indices unicos de la tabla, que es justo lo que hace
     * falta.
     */
    override fun record(movement: StockMovement) {
        jdbc.update(
            """
            INSERT INTO warehouse_movements (
                id, household_id, asset_id, article_id, location_id, location_name, kind,
                previous_quantity, quantity, occurred_at, event_id, created_by
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT DO NOTHING
            """.trimIndent(),
            movement.id, householdId, movement.assetId, movement.articleId, movement.locationId,
            movement.locationName, movement.kind.name, movement.previousQuantity, movement.quantity,
            movement.occurredAt.at(), movement.eventId, movement.createdBy,
        )
    }

    override fun listMovements(assetId: UUID?, articleId: UUID?, pagination: Pagination): Page<StockMovement> {
        val where = """
            WHERE (CAST(? AS uuid) IS NULL OR asset_id = CAST(? AS uuid))
              AND (CAST(? AS uuid) IS NULL OR article_id = CAST(? AS uuid))
        """.trimIndent()
        val filters = arrayOf<Any?>(assetId, assetId, articleId, articleId)

        val total = jdbc.queryForObject(
            "SELECT count(*) FROM warehouse_movements $where",
            Long::class.java,
            *filters,
        ) ?: 0L

        val items = jdbc.query(
            "SELECT * FROM warehouse_movements $where ORDER BY occurred_at DESC, id LIMIT ? OFFSET ?",
            MOVEMENT,
            *filters, pagination.size, pagination.page * pagination.size,
        )
        return Page(items, pagination.page, pagination.size, total)
    }

    override fun recentMovements(assetId: UUID, limit: Int): List<StockMovement> = jdbc.query(
        "SELECT * FROM warehouse_movements WHERE asset_id = ? ORDER BY occurred_at DESC, id LIMIT ?",
        MOVEMENT,
        assetId, limit,
    )

    // -----------------------------------------------------------------------
    // Los lotes
    // -----------------------------------------------------------------------

    override fun findLot(lotId: UUID): StockLot? =
        jdbc.query("SELECT * FROM warehouse_lots WHERE id = ?", LOT, lotId).firstOrNull()

    override fun saveLot(lot: StockLot): StockLot {
        val updated = jdbc.update(
            """
            UPDATE warehouse_lots
               SET lot_code = ?, expires_on = ?, quantity = ?, consumed_at = ?,
                   notified_stage = ?, updated_at = ?, updated_by = ?
             WHERE id = ?
            """.trimIndent(),
            lot.lotCode, lot.expiresOn, lot.quantity, lot.consumedAt.at(),
            lot.notifiedStage?.name, lot.updatedAt.at(), lot.updatedBy, lot.id,
        )
        if (updated == 0) {
            jdbc.update(
                """
                INSERT INTO warehouse_lots (
                    id, household_id, asset_id, article_id, lot_code, expires_on, quantity,
                    consumed_at, notified_stage, created_at, updated_at, created_by, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                lot.id, householdId, lot.assetId, lot.articleId, lot.lotCode, lot.expiresOn,
                lot.quantity, lot.consumedAt.at(), lot.notifiedStage?.name, lot.createdAt.at(),
                lot.updatedAt.at(), lot.createdBy, lot.updatedBy,
            )
        }
        return lot
    }

    override fun listLots(assetId: UUID?, includeConsumed: Boolean, pagination: Pagination): Page<StockLot> {
        val where = """
            WHERE (CAST(? AS uuid) IS NULL OR asset_id = CAST(? AS uuid))
              AND (? OR consumed_at IS NULL)
        """.trimIndent()
        val filters = arrayOf<Any?>(assetId, assetId, includeConsumed)

        val total = jdbc.queryForObject(
            "SELECT count(*) FROM warehouse_lots $where",
            Long::class.java,
            *filters,
        ) ?: 0L

        val items = jdbc.query(
            "SELECT * FROM warehouse_lots $where ORDER BY expires_on, id LIMIT ? OFFSET ?",
            LOT,
            *filters, pagination.size, pagination.page * pagination.size,
        )
        return Page(items, pagination.page, pagination.size, total)
    }

    override fun liveLotsOf(assetId: UUID): List<StockLot> = jdbc.query(
        "SELECT * FROM warehouse_lots WHERE asset_id = ? AND consumed_at IS NULL ORDER BY expires_on",
        LOT,
        assetId,
    )

    override fun consumeLotsOf(assetId: UUID, at: Instant): Int = jdbc.update(
        "UPDATE warehouse_lots SET consumed_at = ?, updated_at = ? WHERE asset_id = ? AND consumed_at IS NULL",
        at.at(), at.at(), assetId,
    )

    /**
     * La cadena de antelacion --sitio, articulo, y si ninguno la fija, la del
     * modulo-- resuelta con dos `LEFT JOIN` y un `coalesce`, y no en memoria.
     *
     * El sitio manda sobre el articulo porque es lo que gobierna de verdad: el
     * mismo yogur aguanta distinto en la nevera que fuera de ella.
     */
    override fun lotsDueBy(horizon: LocalDate, defaultLeadDays: Int): List<DueLot> = jdbc.query(
        """
        SELECT l.*, a.name AS article_name,
               coalesce(wl.expiry_lead_days, wa.expiry_lead_days, ?) AS lead_days
          FROM warehouse_lots l
          JOIN articles a ON a.id = l.article_id
          LEFT JOIN warehouse_articles wa ON wa.article_id = l.article_id
          LEFT JOIN assets ast ON ast.id = l.asset_id
          LEFT JOIN warehouse_locations wl ON wl.location_id = ast.location_id
         WHERE l.consumed_at IS NULL
           AND l.expires_on <= ?
         ORDER BY l.expires_on, l.id
        """.trimIndent(),
        { rows, _ ->
            DueLot(
                lot = LOT.mapRow(rows, 0)!!,
                leadDays = rows.getInt("lead_days"),
                articleName = rows.getString("article_name"),
            )
        },
        defaultLeadDays, horizon,
    )

    override fun markLotNotified(lotId: UUID, stage: ExpiryStage, at: Instant) {
        jdbc.update(
            "UPDATE warehouse_lots SET notified_stage = ?, updated_at = ? WHERE id = ?",
            stage.name, at.at(), lotId,
        )
    }

    // -----------------------------------------------------------------------
    // Lo que se lee del core
    // -----------------------------------------------------------------------

    /**
     * La lista de existencias: **la cantidad sale de `assets` y no de aqui**.
     *
     * Es la consulta que hace visible la frontera del modulo. Todo lo de la
     * izquierda --`assets`, `articles`, `locations`-- es del core y se lee; lo que
     * este modulo aporta es el minimo, el recuento de lotes y la caducidad mas
     * proxima.
     */
    override fun listStock(filter: StockFilter, pagination: Pagination): Page<StockItem> {
        val where = STOCK_WHERE
        val filters = arrayOf<Any?>(
            filter.query, filter.query,
            filter.locationId, filter.locationId,
            filter.belowMinimum,
            filter.expiringWithinDays, filter.expiringWithinDays,
        )

        val total = jdbc.queryForObject(
            "SELECT count(*) FROM ($STOCK_SELECT $where) AS matched",
            Long::class.java,
            *filters,
        ) ?: 0L

        val items = jdbc.query(
            "$STOCK_SELECT $where ORDER BY article_name, location_name NULLS LAST LIMIT ? OFFSET ?",
            STOCK_ITEM,
            *filters, pagination.size, pagination.page * pagination.size,
        )
        return Page(items, pagination.page, pagination.size, total)
    }

    /**
     * `AND` y no `WHERE`: [STOCK_SELECT] ya trae el suyo con las dos condiciones
     * que definen «existencia que el almacen vigila» --viva y de consumible--, y
     * son parte de la definicion, no un filtro que se pueda saltar aqui.
     */
    override fun findStockItem(assetId: UUID): StockItem? = jdbc.query(
        "$STOCK_SELECT AND ast.id = ?",
        STOCK_ITEM,
        assetId,
    ).firstOrNull()

    override fun totalOf(articleId: UUID): BigDecimal = jdbc.queryForObject(
        """
        SELECT coalesce(sum(quantity), 0) FROM assets
         WHERE article_id = ? AND type = 'CONSUMABLE' AND status <> 'DECOMMISSIONED'
        """.trimIndent(),
        BigDecimal::class.java,
        articleId,
    ) ?: BigDecimal.ZERO

    override fun totalsForWatchedArticles(): List<ArticleStockTotal> = jdbc.query(
        """
        SELECT wa.article_id,
               coalesce((
                   SELECT sum(a.quantity) FROM assets a
                    WHERE a.article_id = wa.article_id
                      AND a.type = 'CONSUMABLE' AND a.status <> 'DECOMMISSIONED'
               ), 0) AS total
          FROM warehouse_articles wa
         WHERE wa.minimum_quantity IS NOT NULL
        """.trimIndent(),
        { rows, _ -> ArticleStockTotal(rows.uuid("article_id")!!, rows.getBigDecimal("total")) },
    )

    override fun coreStockFacts(assetId: UUID): CoreStockFacts? = jdbc.query(
        """
        SELECT a.id, a.article_id, a.location_id, a.quantity, a.type, a.status, l.name AS location_name
          FROM assets a
          LEFT JOIN locations l ON l.id = a.location_id
         WHERE a.id = ?
        """.trimIndent(),
        { rows, _ ->
            CoreStockFacts(
                assetId = rows.uuid("id")!!,
                articleId = rows.uuid("article_id"),
                locationId = rows.uuid("location_id"),
                locationName = rows.getString("location_name"),
                quantity = rows.getBigDecimal("quantity"),
                isLiveConsumable = rows.getString("type") == "CONSUMABLE" &&
                    rows.getString("status") != "DECOMMISSIONED",
            )
        },
        assetId,
    ).firstOrNull()

    override fun articleUnit(articleId: UUID): String? =
        jdbc.queryForList("SELECT unit FROM articles WHERE id = ?", String::class.java, articleId).firstOrNull()

    override fun articleName(articleId: UUID): String? =
        jdbc.queryForList("SELECT name FROM articles WHERE id = ?", String::class.java, articleId).firstOrNull()

    override fun liveArticleIds(): List<UUID> =
        jdbc.queryForList("SELECT id FROM articles WHERE retired_at IS NULL", UUID::class.java)

    override fun allLocationIds(): List<UUID> =
        jdbc.queryForList("SELECT id FROM locations", UUID::class.java)

    override fun liveStockItemIds(): List<UUID> = jdbc.queryForList(
        "SELECT id FROM assets WHERE type = 'CONSUMABLE' AND status <> 'DECOMMISSIONED' ORDER BY created_at",
        UUID::class.java,
    )

    private companion object {

        /**
         * El `SELECT` de una existencia vista por Warehouse.
         *
         * Los dos subconsultas de lotes van como escalares y no como `JOIN` con
         * `GROUP BY`: con el `JOIN`, una existencia con tres lotes se triplicaria
         * y habria que agrupar por las once columnas de arriba.
         */
        const val STOCK_SELECT = """
            SELECT ast.id AS asset_id, ast.article_id, ast.quantity, ast.location_id,
                   ar.name AS article_name, ar.unit,
                   loc.name AS location_name,
                   wa.minimum_quantity,
                   (SELECT min(expires_on) FROM warehouse_lots wl
                     WHERE wl.asset_id = ast.id AND wl.consumed_at IS NULL) AS nearest_expiry,
                   (SELECT count(*) FROM warehouse_lots wl
                     WHERE wl.asset_id = ast.id AND wl.consumed_at IS NULL) AS lot_count
              FROM assets ast
              JOIN articles ar ON ar.id = ast.article_id
              LEFT JOIN locations loc ON loc.id = ast.location_id
              LEFT JOIN warehouse_articles wa ON wa.article_id = ast.article_id
             WHERE ast.type = 'CONSUMABLE' AND ast.status <> 'DECOMMISSIONED'
        """

        /**
         * Los cuatro filtros, todos opcionales.
         *
         * El texto usa `immutable_unaccent`, que es la misma funcion con la que el
         * core indexa el nombre del articulo: buscar «platano» tiene que encontrar
         * «Plátano».
         */
        const val STOCK_WHERE = """
              AND (
                CAST(? AS text) IS NULL
                OR lower(immutable_unaccent(ar.name)) LIKE '%' || lower(immutable_unaccent(CAST(? AS text))) || '%'
              )
              AND (CAST(? AS uuid) IS NULL OR ast.location_id = CAST(? AS uuid))
              AND (NOT CAST(? AS boolean) OR (wa.minimum_quantity IS NOT NULL AND ast.quantity <= wa.minimum_quantity))
              AND (
                CAST(? AS integer) IS NULL
                OR EXISTS (
                    SELECT 1 FROM warehouse_lots wl
                     WHERE wl.asset_id = ast.id AND wl.consumed_at IS NULL
                       AND wl.expires_on <= current_date + CAST(? AS integer)
                )
              )
        """

        val ARTICLE_FILE = RowMapper { rows, _ ->
            WarehouseArticle(
                id = rows.uuid("id")!!,
                articleId = rows.uuid("article_id")!!,
                minimumQuantity = rows.getBigDecimal("minimum_quantity"),
                expiryLeadDays = rows.intOrNull("expiry_lead_days"),
                lowStockSince = rows.instant("low_stock_since"),
                lowStockNotifiedAt = rows.instant("low_stock_notified_at"),
                createdAt = rows.instant("created_at")!!,
                updatedAt = rows.instant("updated_at")!!,
                createdBy = rows.uuid("created_by"),
                updatedBy = rows.uuid("updated_by"),
            )
        }

        val LOCATION_FILE = RowMapper { rows, _ ->
            WarehouseLocation(
                id = rows.uuid("id")!!,
                locationId = rows.uuid("location_id")!!,
                expiryLeadDays = rows.intOrNull("expiry_lead_days"),
                notes = rows.getString("notes"),
                createdAt = rows.instant("created_at")!!,
                updatedAt = rows.instant("updated_at")!!,
                createdBy = rows.uuid("created_by"),
                updatedBy = rows.uuid("updated_by"),
            )
        }

        val MOVEMENT = RowMapper { rows, _ ->
            StockMovement(
                id = rows.uuid("id")!!,
                assetId = rows.uuid("asset_id")!!,
                articleId = rows.uuid("article_id"),
                locationId = rows.uuid("location_id"),
                locationName = rows.getString("location_name"),
                kind = MovementKind.valueOf(rows.getString("kind")),
                previousQuantity = rows.getBigDecimal("previous_quantity"),
                quantity = rows.getBigDecimal("quantity"),
                occurredAt = rows.instant("occurred_at")!!,
                eventId = rows.uuid("event_id"),
                createdBy = rows.uuid("created_by"),
            )
        }

        val LOT = RowMapper { rows, _ ->
            StockLot(
                id = rows.uuid("id")!!,
                assetId = rows.uuid("asset_id")!!,
                articleId = rows.uuid("article_id")!!,
                lotCode = rows.getString("lot_code"),
                expiresOn = rows.getObject("expires_on", LocalDate::class.java),
                quantity = rows.getBigDecimal("quantity"),
                consumedAt = rows.instant("consumed_at"),
                notifiedStage = rows.getString("notified_stage")?.let { ExpiryStage.valueOf(it) },
                createdAt = rows.instant("created_at")!!,
                updatedAt = rows.instant("updated_at")!!,
                createdBy = rows.uuid("created_by"),
                updatedBy = rows.uuid("updated_by"),
            )
        }

        val STOCK_ITEM = RowMapper { rows, _ ->
            StockItem(
                assetId = rows.uuid("asset_id")!!,
                articleId = rows.uuid("article_id")!!,
                articleName = rows.getString("article_name"),
                unit = rows.getString("unit"),
                locationId = rows.uuid("location_id"),
                locationName = rows.getString("location_name"),
                quantity = rows.getBigDecimal("quantity") ?: BigDecimal.ZERO,
                minimumQuantity = rows.getBigDecimal("minimum_quantity"),
                nearestExpiry = rows.getObject("nearest_expiry", LocalDate::class.java),
                lotCount = rows.getInt("lot_count"),
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
 * Va por `OffsetDateTime` y no por `Timestamp`: `Timestamp` interpreta el
 * instante en la zona de la JVM y una prueba que corriera en otra zona veria
 * fechas distintas de las que escribio.
 */
private fun Instant?.at(): java.time.OffsetDateTime? = this?.atOffset(java.time.ZoneOffset.UTC)

private fun ResultSet.uuid(column: String): UUID? = getObject(column, UUID::class.java)

private fun ResultSet.instant(column: String): Instant? =
    getObject(column, java.time.OffsetDateTime::class.java)?.toInstant()

/** `getInt` devuelve cero para un nulo, que aqui significaria «sin antelacion». */
private fun ResultSet.intOrNull(column: String): Int? = getObject(column)?.let { getInt(column) }
