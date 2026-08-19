package com.drp.core.domain.inventory

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Un espacio fisico de almacenaje (README 4.1.2).
 *
 * A diferencia de un asset, **no es un recurso del hogar** sino el contenedor
 * donde se guardan los recursos. De ahi que se borre de verdad en lugar de darse
 * de baja: una ubicacion vacia no deja historial que preservar.
 *
 * Admite jerarquia --Vivienda → Planta → Garaje → Estanteria 2-- y de un mismo
 * hogar pueden colgar **varias viviendas**: cada una es una `Location` sin padre
 * y de tipo `HOUSE`. No hizo falta entidad nueva porque la jerarquia ya admite
 * varias raices.
 */
data class Location(
    val id: UUID,
    val name: String,
    val type: LocationType,
    val parentLocationId: UUID?,
    val capacity: Capacity?,
    val environmentalConditions: EnvironmentalConditions?,
    val photoUrl: String?,
    val photoFileId: UUID?,
    val notes: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val createdBy: UUID?,
    val updatedBy: UUID?,
) {
    val isRoot: Boolean get() = parentLocationId == null
}

/** Lista fija: describe la naturaleza del contenedor, no lo que el hogar guarda en el. */
enum class LocationType { HOUSE, FLOOR, ROOM, FURNITURE, SHELF, OTHER }

/**
 * La capacidad declarada de una ubicacion, con forma unica.
 *
 * No se modela por tipo de ubicacion --un estante aguanta kilos y un armario
 * litros, pero ambos caben en la misma terna-- y **superarla advierte, no
 * bloquea**: bloquear con datos incompletos impediria guardar algo que si cabe.
 *
 * **Las tres formas se pueden comprobar desde el Hito 3 de la Fase 2.** Hasta
 * entonces solo el recuento de unidades era fiable, porque el modelo no decia
 * cuanto ocupa una cosa y una ubicacion que declaraba «50 kg» no recibia ningun
 * aviso nunca. Ahora la medida la pone el articulo --`unitWeightGrams` y
 * `unitVolumeMl`-- y `CapacityAdvisor` suma lo que puede: si lo conocido ya se
 * pasa, avisa; si cabe y falta por medir, se calla, porque una suma incompleta
 * solo puede quedarse corta.
 */
data class Capacity(
    val type: CapacityType,
    val max: BigDecimal,
    val unit: String,
) {
    /**
     * Cierto si la comprobacion es **exacta**: contar piezas no necesita ninguna
     * medida, mientras que el peso y el volumen dependen de que los articulos la
     * lleven y pueden quedarse cortos.
     *
     * Va como **funcion y no como propiedad calculada**, y el nombre evita
     * empezar por `is`, porque esta clase se serializa a JSON por dos caminos: la
     * columna `jsonb` y la respuesta de la API. Jackson trata `val isCountable`
     * como un campo mas y lo escribia en los dos sitios --una clave que el
     * contrato no declara, guardada ademas en la base de datos.
     */
    fun countsUnits(): Boolean = type == CapacityType.UNITS
}

enum class CapacityType { WEIGHT, VOLUME, UNITS }

/** Todas opcionales: solo se informan si son relevantes para esa ubicacion. */
data class EnvironmentalConditions(
    val minTemperature: BigDecimal? = null,
    val maxTemperature: BigDecimal? = null,
    val minHumidity: BigDecimal? = null,
    val maxHumidity: BigDecimal? = null,
    val lightExposure: LightExposure? = null,
)

enum class LightExposure { DIRECT, INDIRECT, DARKNESS }
