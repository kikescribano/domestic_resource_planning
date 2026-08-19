package com.drp.module.purchasing.adapter.persistence

import com.drp.core.domain.inventory.AssetLocation
import com.drp.module.purchasing.application.PurchasingRepository
import com.drp.module.purchasing.application.ShoppingListFilter
import com.drp.module.purchasing.domain.ItemOrigin
import com.drp.module.purchasing.domain.ItemStatus
import com.drp.module.purchasing.domain.Purchase
import com.drp.module.purchasing.domain.PurchaseStatus
import com.drp.module.purchasing.domain.ShoppingListEntry
import com.drp.module.purchasing.domain.ShoppingListItem
import com.drp.platform.page.Page
import com.drp.platform.page.Pagination
import com.drp.platform.tenant.TenantContext
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

/**
 * Las dos tablas del modulo, en PostgreSQL.
 *
 * **Por SQL directo y no por JPA**, igual que Warehouse y por el mismo motivo:
 * parte de lo que este modulo necesita **no esta en sus tablas**. Una linea de la
 * lista se lee siempre con el nombre, la unidad y el envase del articulo, que son
 * tres columnas del core; y la siembra es una suma sobre `assets`. Con entidades
 * JPA habria que declarar las del core en este arbol, que es exactamente la
 * dependencia que la ficha se compromete a no tener: **el modulo lee el estado del
 * core, no su persistencia**.
 *
 * **Ninguna consulta lleva `WHERE household_id`.** Lo pone la politica de RLS a
 * partir del `app.household_id` que fija el gestor de transacciones. Escribirlo a
 * mano daria la impresion de que es el que protege, y el dia que a alguien se le
 * olvidara en una consulta nueva no fallaria nada. Insertar si lo lleva, porque una
 * fila nueva necesita decir de quien es --y la politica comprueba que coincida.
 *
 * **Y ni un `JOIN` hacia `suppliers` ni hacia ninguna tabla de Warehouse.** Es la
 * frontera entre modulos, que aqui no la vigila ArchUnit --no hay clase que
 * importar-- sino esta nota y la revision. El nombre del proveedor se copia al
 * crear la compra, leido por el puerto de plataforma.
 */
@Repository
class JdbcPurchasingRepository(
    private val jdbc: JdbcTemplate,
    private val tenantContext: TenantContext,
) : PurchasingRepository {

    private val householdId: UUID
        get() = checkNotNull(tenantContext.currentHousehold()) {
            "Compras no puede escribir sin hogar en el contexto"
        }

    // -----------------------------------------------------------------------
    // La lista
    // -----------------------------------------------------------------------

    override fun listItems(filter: ShoppingListFilter, pagination: Pagination): Page<ShoppingListEntry> {
        // Marcadores generados y no un `= ANY (?)`: pgjdbc **no sabe inferir el
        // tipo SQL de un array de Java** con `setObject`, que es lo que hace
        // `JdbcTemplate` con cada argumento suelto. El sintoma seria un
        // «Can't infer the SQL type» en ejecucion, no al compilar.
        val statuses = filter.statuses.map { it.name }
        val where = """
            WHERE i.status IN (${statuses.placeholders()})
              AND (
                CAST(? AS text) IS NULL
                OR lower(immutable_unaccent(coalesce(ar.name, i.name)))
                     LIKE '%' || lower(immutable_unaccent(CAST(? AS text))) || '%'
              )
        """.trimIndent()
        val filters = (statuses + listOf(filter.query, filter.query)).toTypedArray()

        val total = jdbc.queryForObject(
            "SELECT count(*) FROM shopping_list_items i LEFT JOIN articles ar ON ar.id = i.article_id $where",
            Long::class.java,
            *filters,
        ) ?: 0L

        val items = jdbc.query(
            """
            $ENTRY_SELECT
            $where
            ORDER BY i.origin DESC, i.created_at, i.id
            LIMIT ? OFFSET ?
            """.trimIndent(),
            ENTRY,
            *filters, pagination.size, pagination.page * pagination.size,
        )
        return Page(items, pagination.page, pagination.size, total)
    }

    override fun findItem(itemId: UUID): ShoppingListEntry? =
        jdbc.query("$ENTRY_SELECT WHERE i.id = ?", ENTRY, itemId).firstOrNull()

    override fun findLiveItemByArticle(articleId: UUID): ShoppingListItem? = jdbc.query(
        "$ENTRY_SELECT WHERE i.article_id = ? AND i.status IN ('NEEDED', 'IN_PURCHASE')",
        ENTRY,
        articleId,
    ).firstOrNull()?.item

    override fun saveItem(item: ShoppingListItem): ShoppingListItem {
        jdbc.update(
            """
            INSERT INTO shopping_list_items (
                id, household_id, article_id, name, quantity, origin, status, note,
                purchase_id, received_asset_id, created_at, updated_at, created_by, updated_by
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                article_id = excluded.article_id,
                name = excluded.name,
                quantity = excluded.quantity,
                origin = excluded.origin,
                status = excluded.status,
                note = excluded.note,
                purchase_id = excluded.purchase_id,
                received_asset_id = excluded.received_asset_id,
                updated_at = excluded.updated_at,
                updated_by = excluded.updated_by
            """.trimIndent(),
            item.id, householdId, item.articleId, item.name, item.quantity,
            item.origin.name, item.status.name, item.note, item.purchaseId, item.receivedAssetId,
            item.createdAt.at(), item.updatedAt.at(), item.createdBy, item.updatedBy,
        )
        return item
    }

    /**
     * **La idempotencia del lado del bus**, y va en una sola sentencia a
     * proposito: el `ON CONFLICT` cae sobre el indice unico parcial de una linea
     * viva por articulo, asi que el mismo `StockBelowMinimum` entregado dos veces
     * deja una linea y no dos. Comprobar antes e insertar despues dejaria una
     * ventana entre las dos cosas.
     */
    override fun openItemForArticle(
        articleId: UUID,
        origin: ItemOrigin,
        quantity: BigDecimal?,
        at: Instant,
        by: UUID?,
    ): Boolean = jdbc.update(
        """
        INSERT INTO shopping_list_items (
            id, household_id, article_id, quantity, origin, status, created_at, updated_at, created_by, updated_by
        ) VALUES (?, ?, ?, ?, ?, 'NEEDED', ?, ?, ?, ?)
        ON CONFLICT (household_id, article_id)
            WHERE article_id IS NOT NULL AND status IN ('NEEDED', 'IN_PURCHASE')
            DO NOTHING
        """.trimIndent(),
        UUID.randomUUID(), householdId, articleId, quantity, origin.name, at.at(), at.at(), by, by,
    ) > 0

    /**
     * Sube el origen **solo hacia arriba**, con la comparacion dentro de la
     * sentencia y no en Kotlin: leer, comparar y escribir serian tres pasos con
     * dos huecos, y el orden de los origenes es justamente lo que decide quien
     * gana cuando dos eventos llegan a la vez.
     */
    override fun raiseOrigin(articleId: UUID, origin: ItemOrigin, at: Instant): Boolean = jdbc.update(
        """
        UPDATE shopping_list_items
           SET origin = ?, updated_at = ?
         WHERE article_id = ?
           AND status IN ('NEEDED', 'IN_PURCHASE')
           AND $ORIGIN_RANK < ?
        """.trimIndent(),
        origin.name, at.at(), articleId, origin.ordinal,
    ) > 0

    override fun linesOf(purchaseId: UUID): List<ShoppingListEntry> = jdbc.query(
        "$ENTRY_SELECT WHERE i.purchase_id = ? ORDER BY coalesce(ar.name, i.name), i.id",
        ENTRY,
        purchaseId,
    )

    // -----------------------------------------------------------------------
    // La compra
    // -----------------------------------------------------------------------

    override fun listPurchases(status: PurchaseStatus?, pagination: Pagination): Page<Purchase> {
        val where = "WHERE (CAST(? AS text) IS NULL OR status = CAST(? AS text))"
        val filters = arrayOf<Any?>(status?.name, status?.name)

        val total = jdbc.queryForObject(
            "SELECT count(*) FROM purchases $where",
            Long::class.java,
            *filters,
        ) ?: 0L

        val items = jdbc.query(
            "SELECT * FROM purchases $where ORDER BY created_at DESC, id LIMIT ? OFFSET ?",
            PURCHASE,
            *filters, pagination.size, pagination.page * pagination.size,
        )
        return Page(items, pagination.page, pagination.size, total)
    }

    override fun findPurchase(purchaseId: UUID): Purchase? =
        jdbc.query("SELECT * FROM purchases WHERE id = ?", PURCHASE, purchaseId).firstOrNull()

    override fun savePurchase(purchase: Purchase): Purchase {
        jdbc.update(
            """
            INSERT INTO purchases (
                id, household_id, supplier_id, supplier_name, status, note,
                received_at, cancelled_at, created_at, updated_at, created_by, updated_by
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            purchase.id, householdId, purchase.supplierId, purchase.supplierName,
            purchase.status.name, purchase.note, purchase.receivedAt.at(), purchase.cancelledAt.at(),
            purchase.createdAt.at(), purchase.updatedAt.at(), purchase.createdBy, purchase.updatedBy,
        )
        return purchase
    }

    /**
     * `= 'NEEDED'` y no `<> 'BOUGHT'`: reclamar una linea que ya va en otra compra
     * abierta la robaria de esa compra sin decirselo a nadie. La cuenta de filas
     * cambiadas es lo que permite responder «ninguna estaba disponible» sin haber
     * leido y comprobado antes en dos pasos.
     */
    override fun claimItemsForPurchase(itemIds: List<UUID>, purchaseId: UUID, at: Instant, by: UUID?): Int =
        jdbc.update(
            """
            UPDATE shopping_list_items
               SET status = 'IN_PURCHASE', purchase_id = ?, updated_at = ?, updated_by = ?
             WHERE id IN (${itemIds.placeholders()}) AND status = 'NEEDED'
            """.trimIndent(),
            *(listOf<Any?>(purchaseId, at.at(), by) + itemIds).toTypedArray(),
        )

    override fun claimItemForReceipt(itemId: UUID, at: Instant, by: UUID?): Boolean = jdbc.update(
        """
        UPDATE shopping_list_items
           SET status = 'BOUGHT', updated_at = ?, updated_by = ?
         WHERE id = ? AND status = 'IN_PURCHASE'
        """.trimIndent(),
        at.at(), by, itemId,
    ) > 0

    override fun recordIntake(itemId: UUID, assetId: UUID, at: Instant) {
        jdbc.update(
            "UPDATE shopping_list_items SET received_asset_id = ?, updated_at = ? WHERE id = ?",
            assetId, at.at(), itemId,
        )
    }

    override fun releaseItemsOf(purchaseId: UUID, at: Instant, by: UUID?): Int = jdbc.update(
        """
        UPDATE shopping_list_items
           SET status = 'NEEDED', purchase_id = NULL, updated_at = ?, updated_by = ?
         WHERE purchase_id = ? AND status = 'IN_PURCHASE'
        """.trimIndent(),
        at.at(), by, purchaseId,
    )

    override fun changePurchaseStatus(
        purchaseId: UUID,
        from: PurchaseStatus,
        to: PurchaseStatus,
        at: Instant,
        by: UUID?,
    ): Boolean = jdbc.update(
        """
        UPDATE purchases
           SET status = CAST(? AS text),
               received_at = CASE WHEN CAST(? AS text) = 'RECEIVED' THEN ? ELSE received_at END,
               cancelled_at = CASE WHEN CAST(? AS text) = 'CANCELLED' THEN ? ELSE cancelled_at END,
               updated_at = ?, updated_by = ?
         WHERE id = ? AND status = CAST(? AS text)
        """.trimIndent(),
        to.name, to.name, at.at(), to.name, at.at(), at.at(), by, purchaseId, from.name,
    ) > 0

    // -----------------------------------------------------------------------
    // Lo que se lee del core
    // -----------------------------------------------------------------------

    override fun isLiveArticle(articleId: UUID): Boolean = jdbc.queryForObject(
        "SELECT count(*) FROM articles WHERE id = ? AND retired_at IS NULL",
        Long::class.java,
        articleId,
    ) == 1L

    /**
     * Los articulos cuyo total vivo es cero, **leyendo el contador del core**.
     *
     * `HAVING sum(...) <= 0` y no `quantity = 0` por existencia: un articulo con
     * una existencia a cero en la nevera y otra con medio kilo en la despensa no
     * esta agotado. La pregunta es del articulo, no de la fila.
     */
    override fun depletedArticleIds(): List<UUID> = jdbc.query(
        """
        SELECT article_id
          FROM assets
         WHERE type = 'CONSUMABLE' AND status <> 'DECOMMISSIONED' AND article_id IS NOT NULL
         GROUP BY article_id
        HAVING coalesce(sum(quantity), 0) <= 0
        """.trimIndent(),
    ) { rows, _ -> rows.uuid("article_id")!! }

    override fun soleLocationOf(articleId: UUID): AssetLocation? = jdbc.query(
        """
        SELECT location_asset_id, location_id
          FROM assets
         WHERE type = 'CONSUMABLE' AND status <> 'DECOMMISSIONED' AND article_id = ?
        """.trimIndent(),
        { rows, _ -> AssetLocation.from(rows.uuid("location_asset_id"), rows.uuid("location_id")) },
        articleId,
    ).singleOrNull()

    override fun isActiveMember(memberId: UUID): Boolean = jdbc.queryForObject(
        "SELECT count(*) FROM household_members WHERE id = ? AND deactivated_at IS NULL",
        Long::class.java,
        memberId,
    ) == 1L

    override fun isOwnLocation(locationId: UUID): Boolean = jdbc.queryForObject(
        "SELECT count(*) FROM locations WHERE id = ?",
        Long::class.java,
        locationId,
    ) == 1L

    private companion object {

        /**
         * El orden de los origenes, **generado desde el enumerado** para poder
         * comparar «cual es mas fuerte» dentro de la propia sentencia.
         *
         * Se genera y no se escribe a mano precisamente porque la regla vive en
         * [ItemOrigin] --acabarse es una noticia mas fuerte que bajar del minimo, y
         * las dos lo son mas que haberlo apuntado a mano--: un `CASE` copiado a
         * mano se quedaria atras el dia que alguien anada un origen, y el sintoma
         * seria una linea que no sube de nivel sin que nada falle.
         */
        val ORIGIN_RANK: String = ItemOrigin.entries.joinToString(
            separator = " ",
            prefix = "CASE origin ",
            postfix = " ELSE 0 END",
        ) { "WHEN '${it.name}' THEN ${it.ordinal}" }

        /**
         * Una linea con lo que el core sabe de ella.
         *
         * `LEFT JOIN` y no `JOIN`: una linea de texto suelto no tiene articulo, y
         * un `JOIN` la haria desaparecer de la lista sin que nada fallara.
         */
        const val ENTRY_SELECT = """
            SELECT i.*, ar.name AS article_name, ar.unit AS unit, ar.pack_size AS pack_size
              FROM shopping_list_items i
              LEFT JOIN articles ar ON ar.id = i.article_id
        """

        val ENTRY = RowMapper { rows, _ ->
            ShoppingListEntry(
                item = ShoppingListItem(
                    id = rows.uuid("id")!!,
                    articleId = rows.uuid("article_id"),
                    name = rows.getString("name"),
                    quantity = rows.getBigDecimal("quantity"),
                    origin = ItemOrigin.valueOf(rows.getString("origin")),
                    status = ItemStatus.valueOf(rows.getString("status")),
                    note = rows.getString("note"),
                    purchaseId = rows.uuid("purchase_id"),
                    receivedAssetId = rows.uuid("received_asset_id"),
                    createdAt = rows.instant("created_at")!!,
                    updatedAt = rows.instant("updated_at")!!,
                    createdBy = rows.uuid("created_by"),
                    updatedBy = rows.uuid("updated_by"),
                ),
                articleName = rows.getString("article_name"),
                unit = rows.getString("unit"),
                packSize = rows.getBigDecimal("pack_size"),
            )
        }

        val PURCHASE = RowMapper { rows, _ ->
            Purchase(
                id = rows.uuid("id")!!,
                supplierId = rows.uuid("supplier_id"),
                supplierName = rows.getString("supplier_name"),
                status = PurchaseStatus.valueOf(rows.getString("status")),
                note = rows.getString("note"),
                receivedAt = rows.instant("received_at"),
                cancelledAt = rows.instant("cancelled_at"),
                createdAt = rows.instant("created_at")!!,
                updatedAt = rows.instant("updated_at")!!,
                createdBy = rows.uuid("created_by"),
                updatedBy = rows.uuid("updated_by"),
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
 * Va por `OffsetDateTime` y no por `Timestamp`, igual que en Warehouse:
 * `Timestamp` interpreta el instante en la zona de la JVM y una prueba que
 * corriera en otra zona veria fechas distintas de las que escribio.
 */
private fun Instant?.at(): java.time.OffsetDateTime? = this?.atOffset(java.time.ZoneOffset.UTC)

/** Tantos `?` como elementos, para un `IN` que `JdbcTemplate` pueda rellenar. */
private fun Collection<*>.placeholders(): String = joinToString(", ") { "?" }

private fun ResultSet.uuid(column: String): UUID? = getObject(column, UUID::class.java)

private fun ResultSet.instant(column: String): Instant? =
    getObject(column, java.time.OffsetDateTime::class.java)?.toInstant()
