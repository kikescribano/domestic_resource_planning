package com.drp.core.domain.catalog

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
    /**
     * Lo que pesa y lo que ocupa **una** `unit` del articulo, en gramos y en
     * mililitros. Nulo es «no se sabe», que es el caso normal.
     *
     * Llegaron en la Fase 2 con el Hito 3, y son la respuesta a la pregunta que
     * la Fase 1 dejo abierta en 4.1.7: el aviso de capacidad de una ubicacion
     * solo podia contar unidades porque nada en el modelo decia cuanto ocupa una
     * cosa. La pregunta venia dirigida al modulo Warehouse y **resulto ser del
     * core**: el aviso de capacidad es una regla del core, y una regla del core
     * no puede depender de un modulo que se puede apagar.
     *
     * Y estan aqui y no en `Asset` porque en una existencia el peso total es
     * cantidad x peso unitario: en la fila del asset se quedaria viejo en cada
     * cambio de cantidad, y aqui no envejece nunca. Es la misma regla que el core
     * ya aplica al nombre y a la categoria --cuando el asset tiene articulo, no
     * se guardan por duplicado-- y el mismo sitio donde vive `packSize`.
     *
     * En gramos y mililitros y no en la `unit` del articulo, que dice en que se
     * cuenta: para poder **sumar entre articulos distintos** dentro de una
     * ubicacion hace falta una unidad comun, y con la de cada uno no habria
     * ninguna suma que hacer.
     */
    val unitWeightGrams: BigDecimal?,
    val unitVolumeMl: BigDecimal?,
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
