package com.drp.adapter.http

import com.drp.application.usecase.Patch
import com.drp.domain.inventory.Capacity
import com.drp.domain.inventory.CapacityType
import com.drp.domain.inventory.EnvironmentalConditions
import com.drp.domain.inventory.LightExposure
import com.drp.domain.inventory.Location
import com.drp.domain.inventory.LocationType
import com.fasterxml.jackson.databind.JsonNode
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class LocationInput(
    @field:NotBlank @field:Size(max = 160) val name: String,
    @field:NotNull val type: LocationType?,
    val parentLocationId: UUID? = null,
    @field:Valid val capacity: CapacityInput? = null,
    @field:Valid val environmentalConditions: EnvironmentalConditionsInput? = null,
    val photoUrl: String? = null,
    val photoFileId: UUID? = null,
    val notes: String? = null,
)

data class CapacityInput(
    @field:NotNull val type: CapacityType?,
    @field:NotNull @field:Positive val max: BigDecimal?,
    @field:NotBlank val unit: String?,
) {
    fun toDomain() = Capacity(type!!, max!!, unit!!.trim())
}

data class EnvironmentalConditionsInput(
    val minTemperature: BigDecimal? = null,
    val maxTemperature: BigDecimal? = null,
    val minHumidity: BigDecimal? = null,
    val maxHumidity: BigDecimal? = null,
    val lightExposure: LightExposure? = null,
) {
    fun toDomain() = EnvironmentalConditions(
        minTemperature = minTemperature,
        maxTemperature = maxTemperature,
        minHumidity = minHumidity,
        maxHumidity = maxHumidity,
        lightExposure = lightExposure,
    )
}

/**
 * La capacidad y las condiciones **de salida**, con forma propia y no la del
 * dominio.
 *
 * Es la convencion de `Dtos.kt` --el contrato es fuente de verdad y evoluciona
 * por su cuenta-- y aqui ademas evita un error concreto: devolver la `Capacity`
 * del dominio tal cual metia en la respuesta cualquier propiedad calculada que
 * alguien le anadiera, y el contrato solo declara tres campos.
 */
data class CapacityResponse(val type: CapacityType, val max: BigDecimal, val unit: String) {
    companion object {
        fun of(capacity: Capacity) = CapacityResponse(capacity.type, capacity.max, capacity.unit)
    }
}

data class EnvironmentalConditionsResponse(
    val minTemperature: BigDecimal?,
    val maxTemperature: BigDecimal?,
    val minHumidity: BigDecimal?,
    val maxHumidity: BigDecimal?,
    val lightExposure: LightExposure?,
) {
    companion object {
        fun of(conditions: EnvironmentalConditions) = EnvironmentalConditionsResponse(
            minTemperature = conditions.minTemperature,
            maxTemperature = conditions.maxTemperature,
            minHumidity = conditions.minHumidity,
            maxHumidity = conditions.maxHumidity,
            lightExposure = conditions.lightExposure,
        )
    }
}

data class LocationResponse(
    val id: UUID,
    val name: String,
    val type: LocationType,
    val parentLocationId: UUID?,
    val capacity: CapacityResponse?,
    val environmentalConditions: EnvironmentalConditionsResponse?,
    val photoUrl: String?,
    val photoThumbnailUrl: String?,
    val photoFileId: UUID?,
    val notes: String?,
    val createdAt: Instant,
    val createdBy: UUID?,
    val updatedBy: UUID?,
) {
    companion object {
        fun of(location: Location) = LocationResponse(
            id = location.id,
            name = location.name,
            type = location.type,
            parentLocationId = location.parentLocationId,
            capacity = location.capacity?.let(CapacityResponse::of),
            environmentalConditions = location.environmentalConditions?.let(EnvironmentalConditionsResponse::of),
            photoUrl = location.photoUrl,
            // La miniatura es una URL firmada sobre un fichero subido, y eso
            // llega con el Hito 3. De un enlace externo no se genera ninguna.
            photoThumbnailUrl = null,
            photoFileId = location.photoFileId,
            notes = location.notes,
            createdAt = location.createdAt,
            createdBy = location.createdBy,
            updatedBy = location.updatedBy,
        )
    }
}

/**
 * El cuerpo de un `PATCH`, leido como arbol JSON en crudo.
 *
 * No es capricho: un `data class` con campos anulables **no distingue** «no
 * mencionado» de «mencionado a nulo», y en este contrato esa diferencia decide
 * cosas. `{"notes": null}` borra las notas; un cuerpo que no nombre `notes` las
 * conserva. Con un `String?` las dos llegan como `null` y una de las dos hace lo
 * contrario de lo que pide.
 *
 * El caso que mas duele es `parentLocationId`: a nulo convierte la ubicacion en
 * raiz --que es lo que representa una vivienda--, asi que confundirlo con «no lo
 * toques» sacaria del sitio a cualquier ubicacion que se renombrase.
 */
class JsonPatch(private val body: JsonNode) {

    fun has(field: String): Boolean = body.has(field)

    private fun node(field: String): JsonNode? = body.get(field)?.takeUnless { it.isNull }

    fun text(field: String): Patch<String?> =
        if (!has(field)) Patch.Absent else Patch.Set(node(field)?.asText())

    fun requiredText(field: String): Patch<String> =
        if (!has(field)) Patch.Absent else Patch.Set(node(field)?.asText().orEmpty())

    fun uuid(field: String): Patch<UUID?> =
        if (!has(field)) Patch.Absent else Patch.Set(node(field)?.asText()?.let(UUID::fromString))

    /** Para los campos que se pueden cambiar pero no vaciar, como la categoria de un articulo. */
    fun requiredUuid(field: String): Patch<UUID> =
        if (!has(field)) Patch.Absent else Patch.Set(UUID.fromString(rawText(field)))

    fun decimal(field: String): Patch<BigDecimal?> =
        if (!has(field)) Patch.Absent else Patch.Set(node(field)?.decimalValue())

    inline fun <reified E : Enum<E>> enum(field: String): Patch<E> =
        if (!has(field)) Patch.Absent else Patch.Set(enumValueOf(rawText(field)))

    fun rawText(field: String): String =
        body.get(field)?.takeUnless { it.isNull }?.asText()
            ?: throw IllegalArgumentException("El campo $field no admite nulo")

    fun capacity(): Patch<Capacity?> {
        if (!has("capacity")) return Patch.Absent
        val value = node("capacity") ?: return Patch.Set(null)
        return Patch.Set(
            Capacity(
                type = enumValueOf(value.get("type").asText()),
                max = value.get("max").decimalValue(),
                unit = value.get("unit").asText(),
            ),
        )
    }

    fun environmentalConditions(): Patch<EnvironmentalConditions?> {
        if (!has("environmentalConditions")) return Patch.Absent
        val value = node("environmentalConditions") ?: return Patch.Set(null)
        return Patch.Set(
            EnvironmentalConditions(
                minTemperature = value.get("minTemperature")?.takeUnless { it.isNull }?.decimalValue(),
                maxTemperature = value.get("maxTemperature")?.takeUnless { it.isNull }?.decimalValue(),
                minHumidity = value.get("minHumidity")?.takeUnless { it.isNull }?.decimalValue(),
                maxHumidity = value.get("maxHumidity")?.takeUnless { it.isNull }?.decimalValue(),
                lightExposure = value.get("lightExposure")?.takeUnless { it.isNull }
                    ?.let { enumValueOf<LightExposure>(it.asText()) },
            ),
        )
    }
}
