package com.drp.core.adapter.http

import com.drp.core.application.usecase.AssetView
import com.drp.core.application.usecase.OperationWarning
import com.drp.core.application.usecase.Patch
import com.drp.core.domain.catalog.MeasurementUnit
import com.drp.core.domain.inventory.AssetLocation
import com.drp.core.domain.inventory.AssetStatus
import com.drp.core.domain.inventory.AssetType
import com.drp.core.domain.inventory.LocationRefKind
import com.fasterxml.jackson.databind.JsonNode
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * La referencia polimorfica de ubicacion, tal y como viaja por HTTP: los dos
 * campos van juntos --`type` dice si apunta a otro asset o a una ubicacion, e
 * `id` a cual.
 */
data class LocationRefInput(
    @field:NotNull val type: LocationRefKind?,
    @field:NotNull val id: UUID?,
) {
    fun toDomain() = AssetLocation(type!!, id!!)
}

data class AssetInput(
    @field:Size(max = 200) val name: String? = null,
    @field:NotNull val type: AssetType?,
    val categoryId: UUID? = null,
    val articleId: UUID? = null,
    val ownerId: UUID? = null,
    @field:Valid val location: LocationRefInput? = null,
    val serialNumber: String? = null,
    val acquiredOn: LocalDate? = null,
    val photoUrl: String? = null,
    val photoFileId: UUID? = null,
    val notes: String? = null,
)

data class AssetIntakeInput(
    val articleId: UUID? = null,
    @field:Valid val article: ArticleInput? = null,
    @field:NotNull val ownerId: UUID?,
    @field:Valid val location: LocationRefInput? = null,
    @field:NotNull @field:Positive val quantity: BigDecimal?,
)

data class AssetMergeInput(@field:NotNull val targetAssetId: UUID?)

data class LocationRefResponse(val type: LocationRefKind, val id: UUID) {
    companion object {
        fun of(location: AssetLocation) = LocationRefResponse(location.kind, location.id)
    }
}

data class WarningResponse(val code: String, val message: String) {
    companion object {
        fun of(warning: OperationWarning) = WarningResponse(warning.code, warning.message)
    }
}

/**
 * El asset de salida, con **lo heredado ya resuelto**: `name`, `categoryId` y
 * `unit` son los del articulo cuando el asset lo tiene, aunque no se guarden en
 * su fila. Es la contrapartida de no duplicar el dato.
 *
 * `warnings` va vacio en el caso normal. **No es un error**: la operacion tuvo
 * exito y el asset devuelto es el estado real.
 */
data class AssetResponse(
    val id: UUID,
    val name: String,
    val type: AssetType,
    val categoryId: UUID?,
    val category: String?,
    val articleId: UUID?,
    val ownerId: UUID?,
    val location: LocationRefResponse?,
    val status: AssetStatus,
    val quantity: BigDecimal?,
    val unit: MeasurementUnit?,
    val serialNumber: String?,
    val acquiredOn: LocalDate?,
    val photoUrl: String?,
    val photoThumbnailUrl: String?,
    val photoFileId: UUID?,
    val notes: String?,
    val warnings: List<WarningResponse>,
    val createdAt: Instant,
    val createdBy: UUID?,
    val updatedBy: UUID?,
) {
    companion object {
        fun of(view: AssetView, photoThumbnailUrl: String?) = AssetResponse(
            id = view.asset.id,
            name = view.name,
            type = view.asset.type,
            categoryId = view.categoryId,
            category = view.categoryName,
            articleId = view.asset.articleId,
            ownerId = view.asset.ownerId,
            location = view.asset.location?.let(LocationRefResponse::of),
            status = view.asset.status,
            quantity = view.asset.quantity,
            unit = view.unit,
            serialNumber = view.asset.serialNumber,
            acquiredOn = view.asset.acquiredOn,
            photoUrl = view.asset.photoUrl,
            photoThumbnailUrl = photoThumbnailUrl,
            photoFileId = view.asset.photoFileId,
            notes = view.asset.notes,
            warnings = view.warnings.map(WarningResponse::of),
            createdAt = view.asset.createdAt,
            createdBy = view.asset.createdBy,
            updatedBy = view.asset.updatedBy,
        )
    }
}

/**
 * La ubicacion dentro de un `PATCH`, que tiene **tres** estados como el resto de
 * campos: ausente --no se mueve--, informada --se mueve ahi-- y a nulo --se queda
 * sin ubicacion asignada, que es un estado legitimo: un asset recien dado de alta
 * puede estar pendiente de clasificar.
 */
fun JsonPatch.assetLocation(): Patch<AssetLocation?> {
    if (!has("location")) return Patch.Absent
    val value = locationNode() ?: return Patch.Set(null)
    return Patch.Set(
        AssetLocation(
            kind = enumValueOf(value.get("type").asText()),
            id = UUID.fromString(value.get("id").asText()),
        ),
    )
}

private fun JsonPatch.locationNode(): JsonNode? = rawNode("location")
