package com.drp.module.warehouse.adapter.http

import com.drp.module.warehouse.application.StockItemDetail
import com.drp.module.warehouse.domain.MovementKind
import com.drp.module.warehouse.domain.StockItem
import com.drp.module.warehouse.domain.StockLot
import com.drp.module.warehouse.domain.StockMovement
import com.drp.module.warehouse.domain.WarehouseArticle
import com.drp.module.warehouse.domain.WarehouseLocation
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Las formas que declara `openapi.yaml` para este modulo.
 *
 * **La `quantity` de una existencia viaja aqui y no se guarda en ninguna tabla
 * del modulo**: sale de `assets` al leer. Es la frontera de la ficha vista desde
 * el contrato — el cliente recibe una sola cifra porque solo hay una, no porque se
 * hayan reconciliado dos.
 */

data class StockItemResponse(
    val assetId: UUID,
    val articleId: UUID,
    val article: String,
    val unit: String,
    val locationId: UUID?,
    val location: String?,
    /** **Del core.** Ver la nota de arriba. */
    val quantity: BigDecimal,
    val minimumQuantity: BigDecimal?,
    val belowMinimum: Boolean,
    val nearestExpiry: LocalDate?,
    val lotCount: Int,
) {
    companion object {
        fun of(item: StockItem) = StockItemResponse(
            assetId = item.assetId,
            articleId = item.articleId,
            article = item.articleName,
            unit = item.unit,
            locationId = item.locationId,
            location = item.locationName,
            quantity = item.quantity,
            minimumQuantity = item.minimumQuantity,
            belowMinimum = item.isBelowMinimum,
            nearestExpiry = item.nearestExpiry,
            lotCount = item.lotCount,
        )
    }
}

data class StockItemDetailResponse(
    val item: StockItemResponse,
    val expiryLeadDays: Int?,
    val lots: List<StockLotResponse>,
    val movements: List<StockMovementResponse>,
) {
    companion object {
        fun of(detail: StockItemDetail) = StockItemDetailResponse(
            item = StockItemResponse.of(detail.item),
            expiryLeadDays = detail.expiryLeadDays,
            lots = detail.lots.map(StockLotResponse::of),
            movements = detail.movements.map(StockMovementResponse::of),
        )
    }
}

/**
 * Un asiento del cuaderno.
 *
 * Lleva `location` --el nombre de aquel dia-- y no solo el identificador, porque
 * el sitio puede haberse borrado: `DeleteLocation` borra la fila de verdad, y el
 * nombre guardado es lo unico que sigue diciendo donde paso aquello.
 */
data class StockMovementResponse(
    val id: UUID,
    val assetId: UUID,
    val articleId: UUID?,
    val locationId: UUID?,
    val location: String?,
    val kind: MovementKind,
    val previousQuantity: BigDecimal?,
    val quantity: BigDecimal?,
    val delta: BigDecimal?,
    val occurredAt: Instant,
) {
    companion object {
        fun of(movement: StockMovement) = StockMovementResponse(
            id = movement.id,
            assetId = movement.assetId,
            articleId = movement.articleId,
            locationId = movement.locationId,
            location = movement.locationName,
            kind = movement.kind,
            previousQuantity = movement.previousQuantity,
            quantity = movement.quantity,
            delta = movement.delta,
            occurredAt = movement.occurredAt,
        )
    }
}

data class StockLotResponse(
    val id: UUID,
    val assetId: UUID,
    val articleId: UUID,
    val lotCode: String?,
    val expiresOn: LocalDate,
    val quantity: BigDecimal,
    val consumedAt: Instant?,
) {
    companion object {
        fun of(lot: StockLot) = StockLotResponse(
            id = lot.id,
            assetId = lot.assetId,
            articleId = lot.articleId,
            lotCode = lot.lotCode,
            expiresOn = lot.expiresOn,
            quantity = lot.quantity,
            consumedAt = lot.consumedAt,
        )
    }
}

data class WarehouseArticleResponse(
    val articleId: UUID,
    val minimumQuantity: BigDecimal?,
    val expiryLeadDays: Int?,
    val lowStockSince: Instant?,
) {
    companion object {
        fun of(file: WarehouseArticle) = WarehouseArticleResponse(
            articleId = file.articleId,
            minimumQuantity = file.minimumQuantity,
            expiryLeadDays = file.expiryLeadDays,
            lowStockSince = file.lowStockSince,
        )
    }
}

data class WarehouseLocationResponse(
    val locationId: UUID,
    val expiryLeadDays: Int?,
    val notes: String?,
) {
    companion object {
        fun of(file: WarehouseLocation) = WarehouseLocationResponse(
            locationId = file.locationId,
            expiryLeadDays = file.expiryLeadDays,
            notes = file.notes,
        )
    }
}

/** Un delta, no un absoluto: es la diferencia entera con el `PATCH` del core. */
data class ConsumptionInput(
    @field:NotNull @field:Positive val quantity: BigDecimal?,
)

data class StockLotInput(
    @field:NotNull val assetId: UUID?,
    val lotCode: String? = null,
    @field:NotNull val expiresOn: LocalDate?,
    @field:NotNull @field:Positive val quantity: BigDecimal?,
)
