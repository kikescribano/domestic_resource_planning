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
    /**
     * En que estado esta la cosa. **Solo un `DURABLE`**, igual que las dos de
     * arriba y por el mismo motivo: describe una unidad fisica.
     *
     * Nulo es el caso normal y significa **que nadie lo anoto**, no que este
     * bien: un inventario domestico se llena a ratos y la mayoria de las fichas
     * no llegan a tener esta.
     */
    val condition: AssetCondition?,
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

/**
 * En que estado de conservacion esta una cosa (README 4.1.1).
 *
 * **Un enumerado cerrado y no texto libre**, que es la decision: texto libre ya
 * existe --`notes`-- y un atributo que no se puede filtrar ni comparar no anade
 * nada sobre una nota. Lo que este si permite es preguntar «que hay para tirar»
 * y comparar dos momentos de un prestamo.
 *
 * **Es la misma escala en el asset y en el prestamo**, y por eso vive aqui y no
 * en `loan`: el motivo entero de la condicion en prestamo es poder decir «salio
 * bien y volvio rayado», y con dos escalas distintas esa frase no se puede
 * construir.
 *
 * Van del mejor al peor a proposito. El orden de declaracion es el que ofrece el
 * desplegable y el que hace que la escala signifique algo; nada del codigo
 * depende hoy de el, y por eso no hay ningun `compareTo` escrito que haya que
 * mantener.
 */
enum class AssetCondition { NEW, GOOD, WORN, DAMAGED, UNUSABLE }

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
