package com.drp.core.domain.inventory

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Cualquier material presente en el hogar (README 4.1.1): un taladro, pero
 * tambien un bote de detergente o un paquete de arroz.
 *
 * Las **dos naturalezas** comparten tabla y se comportan distinto:
 *
 * - `DURABLE` tiene identidad propia, una fila por unidad fisica. Es el unico
 *   que puede alojar otros assets y el unico que se presta.
 * - `CONSUMABLE` es una **existencia**: un articulo en una ubicacion, con
 *   cantidad. Cederlo es un ajuste de cantidad, no un prestamo.
 *
 * El `type` se fija en el alta y no se modifica: cambiar la naturaleza equivale
 * a dar de baja y crear otro.
 */
data class Asset(
    val id: UUID,
    val type: AssetType,
    val articleId: UUID?,
    /** Propio del asset, o **nulo** cuando lo hereda de su articulo. No se guarda por duplicado. */
    val name: String?,
    val categoryId: UUID?,
    val ownerId: UUID?,
    val location: AssetLocation?,
    val status: AssetStatus,
    val quantity: BigDecimal?,
    val serialNumber: String?,
    val acquiredOn: LocalDate?,
    val photoUrl: String?,
    val photoFileId: UUID?,
    val notes: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val createdBy: UUID?,
    val updatedBy: UUID?,
) {
    val isDurable: Boolean get() = type == AssetType.DURABLE

    val isConsumable: Boolean get() = type == AssetType.CONSUMABLE

    /** Una existencia dada de baja libera su hueco: se puede volver a dar entrada en esa ubicacion. */
    val isLive: Boolean get() = status != AssetStatus.DECOMMISSIONED

    /** Un `OVERDUE` sigue ocupando el asset igual que un `ACTIVE`. */
    val isLent: Boolean get() = status == AssetStatus.LENT
}

enum class AssetType { DURABLE, CONSUMABLE }

/** Un `CONSUMABLE` nunca esta `LENT`: no se presta, se consume o se entrega. */
enum class AssetStatus { AVAILABLE, LENT, DECOMMISSIONED }

/**
 * La ubicacion polimorfica de un asset: **o** otro asset **o** una `Location`,
 * nunca las dos a la vez y nunca ninguna obligatoria --un asset recien dado de
 * alta puede estar pendiente de clasificar.
 *
 * Es un tipo propio y no dos campos anulables sueltos porque «las dos a la vez»
 * es justo el estado que no debe poder representarse. Con dos `UUID?` esa
 * combinacion es escribible y solo la rechaza el `CHECK` de la tabla, que es
 * tarde.
 */
data class AssetLocation(val kind: LocationRefKind, val id: UUID) {
    val assetId: UUID? get() = id.takeIf { kind == LocationRefKind.ASSET }

    val locationId: UUID? get() = id.takeIf { kind == LocationRefKind.LOCATION }

    companion object {
        /** Reconstruye desde las dos columnas, que es como vive en la tabla. */
        fun from(assetId: UUID?, locationId: UUID?): AssetLocation? = when {
            assetId != null -> AssetLocation(LocationRefKind.ASSET, assetId)
            locationId != null -> AssetLocation(LocationRefKind.LOCATION, locationId)
            else -> null
        }
    }
}

enum class LocationRefKind { ASSET, LOCATION }
