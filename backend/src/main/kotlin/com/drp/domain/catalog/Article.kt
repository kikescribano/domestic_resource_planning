package com.drp.domain.catalog

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * La ficha reutilizable de **que** es algo (README 4.1.1).
 *
 * Un articulo **no es un asset**: no es material, no ocupa sitio, no tiene
 * cantidad y no se presta. Es lo que evita reescribir «Azucar / Alimentacion /
 * GRAM» en cada compra, y lo que deja que dos taladros identicos compartan
 * modelo y manual sin dejar de ser dos unidades distintas.
 *
 * Es obligatorio en un `CONSUMABLE` y opcional en un `DURABLE`.
 */
data class Article(
    val id: UUID,
    val name: String,
    val categoryId: UUID,
    val unit: MeasurementUnit,
    val brand: String?,
    val model: String?,
    val barcode: String?,
    val packSize: BigDecimal?,
    val photoUrl: String?,
    val photoFileId: UUID?,
    val notes: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val retiredAt: Instant?,
    val createdBy: UUID?,
    val updatedBy: UUID?,
) {
    /**
     * Un articulo retirado deja de ofrecerse en el alta y no admite entradas
     * nuevas, pero la fila permanece: las existencias dadas de baja siguen
     * apuntando a el.
     */
    val isLive: Boolean get() = retiredAt == null
}

/**
 * La unidad en la que se llevan **todas** las existencias del articulo.
 *
 * La fija el articulo y no la existencia: si el azucar se lleva en gramos, todas
 * sus existencias van en gramos. Comprar «un paquete de 1 kg» es una conversion
 * en la entrada, no otra unidad guardada --y convertir entre unidad de compra y
 * de consumo es del modulo Warehouse, no del core.
 */
enum class MeasurementUnit { UNIT, GRAM, KILOGRAM, MILLILITER, LITER, METER, PACK }
