package com.drp.module.warehouse.application

import com.drp.core.application.port.SessionClaims
import com.drp.core.application.usecase.AssetPatch
import com.drp.core.application.usecase.Patch
import com.drp.core.application.usecase.UpdateAsset
import com.drp.core.application.usecase.orKeep
import com.drp.module.warehouse.domain.StockItem
import com.drp.module.warehouse.domain.StockLot
import com.drp.module.warehouse.domain.StockMovement
import com.drp.module.warehouse.domain.WarehouseArticle
import com.drp.module.warehouse.domain.WarehouseLocation
import com.drp.platform.error.BusinessRuleViolation
import com.drp.platform.error.ErrorCode
import com.drp.platform.error.ResourceNotFound
import com.drp.platform.error.ValidationFailure
import com.drp.platform.page.Page
import com.drp.platform.page.Pagination
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

/**
 * Las diez operaciones del modulo.
 *
 * Son mas que las siete de Proveedores porque **este si tiene dominio**: lo que la
 * Fase 2 probaba alli era el camino de un modulo, y aqui lo que se prueba es que
 * un modulo reaccione al core sin que el core lo sepa.
 *
 * **La frontera con el core, tal y como se cumple aqui:** ninguna de estas clases
 * guarda «cuanto hay». [RecordStockConsumption] lo lee del core para no dejar
 * consumir de mas, y para cambiarlo **invoca el caso de uso del core** en lugar de
 * tocar su tabla. Es la direccion `modulo -> core` que la ADR-010 permite, y la
 * misma que el Hito 4 usara para cerrar la compra contra
 * `RegisterConsumableIntake`.
 */

data class StockItemDetail(
    val item: StockItem,
    val lots: List<StockLot>,
    val movements: List<StockMovement>,
    val expiryLeadDays: Int?,
)

data class LotCommand(
    val assetId: UUID,
    val lotCode: String?,
    val expiresOn: LocalDate,
    val quantity: BigDecimal,
)

data class LotPatch(
    val lotCode: Patch<String?> = Patch.Absent,
    val expiresOn: Patch<LocalDate> = Patch.Absent,
    val quantity: Patch<BigDecimal> = Patch.Absent,
)

data class WarehouseArticlePatch(
    val minimumQuantity: Patch<BigDecimal?> = Patch.Absent,
    val expiryLeadDays: Patch<Int?> = Patch.Absent,
)

data class WarehouseLocationPatch(
    val expiryLeadDays: Patch<Int?> = Patch.Absent,
    val notes: Patch<String?> = Patch.Absent,
)

// ---------------------------------------------------------------------------
// Lectura
// ---------------------------------------------------------------------------

@Service
class ListStock(private val warehouse: WarehouseRepository) {

    @Transactional(readOnly = true)
    fun handle(filter: StockFilter, pagination: Pagination): Page<StockItem> =
        warehouse.listStock(filter.copy(query = filter.query?.trim()?.takeIf { it.isNotEmpty() }), pagination)
}

@Service
class GetStockItem(private val warehouse: WarehouseRepository) {

    @Transactional(readOnly = true)
    fun handle(assetId: UUID): StockItemDetail {
        val item = warehouse.findStockItem(assetId)
            ?: throw ResourceNotFound("Esa existencia no está en el almacén")

        return StockItemDetail(
            item = item,
            lots = warehouse.liveLotsOf(assetId),
            movements = warehouse.recentMovements(assetId, RECENT_MOVEMENTS),
            expiryLeadDays = item.locationId?.let { warehouse.findLocationFile(it)?.expiryLeadDays }
                ?: warehouse.findArticleFile(item.articleId)?.expiryLeadDays,
        )
    }

    private companion object {
        /** Los ultimos veinte: la ficha es un resumen, y el cuaderno entero tiene su operacion. */
        const val RECENT_MOVEMENTS = 20
    }
}

@Service
class ListStockMovements(private val warehouse: WarehouseRepository) {

    @Transactional(readOnly = true)
    fun handle(assetId: UUID?, articleId: UUID?, pagination: Pagination): Page<StockMovement> =
        warehouse.listMovements(assetId, articleId, pagination)
}

@Service
class ListStockLots(private val warehouse: WarehouseRepository) {

    @Transactional(readOnly = true)
    fun handle(assetId: UUID?, includeConsumed: Boolean, pagination: Pagination): Page<StockLot> =
        warehouse.listLots(assetId, includeConsumed, pagination)
}

// ---------------------------------------------------------------------------
// El consumo, que es lo que este modulo existe para registrar
// ---------------------------------------------------------------------------

/**
 * La lectura de una existencia **en su propia transaccion de solo lectura**.
 *
 * Existe por [RecordStockConsumption], que es la unica operacion del modulo que
 * **no puede abrir transaccion propia**: la de `UpdateAsset` tiene que ser la de
 * fuera para que al cerrarse dispare el `AFTER_COMMIT` que escribe el asiento. Y
 * sin transaccion no hay `SET LOCAL app.household_id`, asi que la politica de RLS
 * no ve hogar y toda consulta devuelve cero filas.
 *
 * Es un bean aparte y no un metodo privado porque `@Transactional` sobre una
 * autoinvocacion **no se aplica**: Spring teje la transaccion en el proxy, y una
 * llamada desde dentro de la misma clase no pasa por el. Es el mismo motivo por
 * el que `ModuleEventHandler` abre la suya de forma programatica.
 */
@Service
class StockReader(private val warehouse: WarehouseRepository) {

    @Transactional(readOnly = true)
    fun stockItem(assetId: UUID): StockItem? = warehouse.findStockItem(assetId)
}

/**
 * «Me he gastado 200 g de azúcar.»
 *
 * **Es la operacion que hace visible la frontera**, y por eso tiene tres cosas
 * que ninguna otra del modulo:
 *
 * **1. Convierte un delta en un absoluto.** El `PATCH` del core es absoluto
 * --sustituye-- y la pregunta que se hace una persona en la cocina es la
 * contraria. Convertir es de aqui: el core mantiene el contador y no tiene por
 * que saber de consumos.
 *
 * **2. Cambia el contador invocando al core**, y no escribiendo en `assets`. La
 * direccion `modulo -> core` esta permitida y esta es su forma mas fuerte; lo que
 * ninguna regla admite es la contraria.
 *
 * **3. No abre transaccion, y es deliberado.** Si la abriera, la de `UpdateAsset`
 * se uniria a ella y el `AFTER_COMMIT` que escribe el asiento no se dispararia
 * hasta despues --o, peor, dentro de una transaccion que el handler no puede
 * ver--. Sin `@Transactional` aqui, la del core es la de fuera, cierra al volver y
 * el asiento queda escrito antes de responder. **El cuaderno lo escribe siempre el
 * handler y nunca esta operacion**, que es lo que garantiza que un consumo hecho
 * desde aqui y uno hecho con el `PATCH` del core produzcan el mismo asiento.
 */
@Service
class RecordStockConsumption(
    private val reader: StockReader,
    private val updateAsset: UpdateAsset,
) {

    fun handle(session: SessionClaims, assetId: UUID, consumed: BigDecimal) {
        if (consumed.signum() <= 0) {
            throw BusinessRuleViolation(
                ErrorCode.STOCK_CONSUMPTION_NOT_POSITIVE,
                "Lo que se consume tiene que ser mayor que cero",
            )
        }

        // La lectura va por [StockReader] y no por el repositorio directamente,
        // y no es ceremonia: **sin transaccion no hay `app.household_id`**, asi
        // que la politica de RLS no ve hogar y la consulta devuelve cero filas.
        // El sintoma es el peor posible --«esa existencia no esta en el almacen»
        // sobre una que si esta-- y no se parece nada a la causa.
        val item = reader.stockItem(assetId)
            ?: throw BusinessRuleViolation(
                ErrorCode.STOCK_ITEM_NOT_TRACKED,
                "El almacén solo sigue existencias vivas de consumible",
            )

        if (consumed > item.quantity) {
            throw BusinessRuleViolation(
                ErrorCode.STOCK_CONSUMPTION_EXCEEDS_QUANTITY,
                "No se puede consumir más de lo que hay: quedan ${item.quantity.toPlainString()}",
            )
        }

        updateAsset.handle(session, assetId, AssetPatch(quantity = Patch.Set(item.quantity - consumed)))
    }
}

// ---------------------------------------------------------------------------
// Los lotes
// ---------------------------------------------------------------------------

@Service
class RegisterStockLot(
    private val warehouse: WarehouseRepository,
    private val clock: Clock,
) {

    @Transactional
    fun handle(session: SessionClaims, command: LotCommand): StockLot {
        val item = warehouse.findStockItem(command.assetId)
            ?: throw BusinessRuleViolation(
                ErrorCode.STOCK_ITEM_NOT_TRACKED,
                "El almacén solo sigue existencias vivas de consumible",
            )

        if (command.quantity.signum() <= 0) {
            throw ValidationFailure(mapOf("quantity" to "la cantidad de un lote es mayor que cero"))
        }

        val code = command.lotCode?.trim()?.takeIf { it.isNotEmpty() }
        val duplicate = warehouse.liveLotsOf(command.assetId)
            .any { it.lotCode == code && it.expiresOn == command.expiresOn }
        if (duplicate) {
            throw BusinessRuleViolation(
                ErrorCode.STOCK_LOT_DUPLICATE,
                "Esa existencia ya tiene un lote con esa fecha y ese código",
            )
        }

        requireLotsFit(warehouse, item, command.assetId, adding = command.quantity)

        val now = clock.instant()
        return warehouse.saveLot(
            StockLot(
                id = UUID.randomUUID(),
                assetId = command.assetId,
                articleId = item.articleId,
                lotCode = code,
                expiresOn = command.expiresOn,
                quantity = command.quantity,
                consumedAt = null,
                notifiedStage = null,
                createdAt = now,
                updatedAt = now,
                createdBy = session.memberId,
                updatedBy = session.memberId,
            ),
        )
    }
}

@Service
class UpdateStockLot(
    private val warehouse: WarehouseRepository,
    private val clock: Clock,
) {

    @Transactional
    fun handle(session: SessionClaims, lotId: UUID, patch: LotPatch): StockLot {
        val current = warehouse.findLot(lotId) ?: throw ResourceNotFound("Lote no encontrado")

        val quantity = patch.quantity.orKeep(current.quantity)
        if (quantity.signum() <= 0) {
            throw ValidationFailure(mapOf("quantity" to "la cantidad de un lote es mayor que cero"))
        }

        if (quantity > current.quantity) {
            val item = warehouse.findStockItem(current.assetId)
                ?: throw BusinessRuleViolation(
                    ErrorCode.STOCK_ITEM_NOT_TRACKED,
                    "El almacén solo sigue existencias vivas de consumible",
                )
            requireLotsFit(warehouse, item, current.assetId, adding = quantity - current.quantity)
        }

        val now = clock.instant()
        return warehouse.saveLot(
            current.copy(
                lotCode = patch.lotCode.orKeep(current.lotCode)?.trim()?.takeIf { it.isNotEmpty() },
                expiresOn = patch.expiresOn.orKeep(current.expiresOn),
                quantity = quantity,
                // **Se vuelve a armar el aviso si la fecha cambia.** Corregir una
                // caducidad mal apuntada tiene que poder volver a avisar; sin
                // esto, un lote que ya dijo `NEAR` con la fecha equivocada se
                // quedaria callado con la buena.
                notifiedStage = if (patch.expiresOn is Patch.Set) null else current.notifiedStage,
                updatedAt = now,
                updatedBy = session.memberId,
            ),
        )
    }
}

/**
 * Da el lote por consumido.
 *
 * **No toca el contador del core**, y es la distincion que este modulo tiene que
 * mantener clara: marcar un lote dice «de lo que hay, esto ya no es de este
 * lote», y gastarlo de verdad es [RecordStockConsumption]. Juntar las dos cosas
 * obligaria a decidir si tirar un lote caducado descuenta o no descuenta, y esa es
 * una pregunta de producto que nadie ha hecho.
 */
@Service
class DiscardStockLot(
    private val warehouse: WarehouseRepository,
    private val clock: Clock,
) {

    @Transactional
    fun handle(session: SessionClaims, lotId: UUID) {
        val current = warehouse.findLot(lotId) ?: throw ResourceNotFound("Lote no encontrado")
        // Descartar dos veces no es un error, igual que retirar dos veces un
        // contacto de servicio: el resultado que el cliente pidio ya se cumple.
        if (!current.isLive) return

        val now = clock.instant()
        warehouse.saveLot(current.copy(consumedAt = now, updatedAt = now, updatedBy = session.memberId))
    }
}

// ---------------------------------------------------------------------------
// Las dos fichas
// ---------------------------------------------------------------------------

@Service
class UpdateWarehouseArticle(
    private val warehouse: WarehouseRepository,
    private val stock: StockSynchronizer,
    private val clock: Clock,
) {

    @Transactional
    fun handle(session: SessionClaims, articleId: UUID, patch: WarehouseArticlePatch): WarehouseArticle {
        // Se abre si no estaba: un hogar puede fijar el minimo de un articulo que
        // nacio antes de encender el modulo y que la siembra ya abrio, pero
        // tambien de uno que llegue por un camino que aun no exista. Abrir aqui
        // cuesta una sentencia y quita un caso de error que nadie sabria explicar.
        stock.openArticleFile(articleId, session.memberId)
        val current = warehouse.findArticleFile(articleId)
            ?: throw ResourceNotFound("Ese artículo no existe en este hogar")

        val minimum = patch.minimumQuantity.orKeep(current.minimumQuantity)
        if (minimum != null && minimum.signum() < 0) {
            throw ValidationFailure(mapOf("minimumQuantity" to "el mínimo no puede ser negativo"))
        }
        val lead = patch.expiryLeadDays.orKeep(current.expiryLeadDays)
        if (lead != null && lead < 0) {
            throw ValidationFailure(mapOf("expiryLeadDays" to "la antelación no puede ser negativa"))
        }

        val now = clock.instant()
        val saved = warehouse.saveArticleFile(
            current.copy(
                minimumQuantity = minimum,
                expiryLeadDays = lead,
                // Quitar el minimo tiene que limpiar el estado que colgaba de el:
                // un articulo que ya no se vigila no puede quedarse marcado como
                // bajo minimos para siempre.
                lowStockSince = current.lowStockSince.takeIf { minimum != null },
                lowStockNotifiedAt = current.lowStockNotifiedAt.takeIf { minimum != null },
                updatedAt = now,
                updatedBy = session.memberId,
            ),
        )

        // Fijar un minimo por primera vez tiene que decidir **ya** si el articulo
        // esta por debajo, y no esperar a la noche: quien lo fija lo hace mirando
        // la despensa y espera que la pantalla le de la razon en el acto.
        stock.refreshArticle(articleId)
        return warehouse.findArticleFile(articleId) ?: saved
    }
}

@Service
class UpdateWarehouseLocation(
    private val warehouse: WarehouseRepository,
    private val stock: StockSynchronizer,
    private val clock: Clock,
) {

    @Transactional
    fun handle(session: SessionClaims, locationId: UUID, patch: WarehouseLocationPatch): WarehouseLocation {
        stock.openLocationFile(locationId, session.memberId)
        val current = warehouse.findLocationFile(locationId)
            ?: throw ResourceNotFound("Ese sitio no existe en este hogar")

        val lead = patch.expiryLeadDays.orKeep(current.expiryLeadDays)
        if (lead != null && lead < 0) {
            throw ValidationFailure(mapOf("expiryLeadDays" to "la antelación no puede ser negativa"))
        }

        val now = clock.instant()
        return warehouse.saveLocationFile(
            current.copy(
                expiryLeadDays = lead,
                notes = patch.notes.orKeep(current.notes)?.trim()?.takeIf { it.isNotEmpty() },
                updatedAt = now,
                updatedBy = session.memberId,
            ),
        )
    }
}

/**
 * **Los lotes de una existencia no suman mas que su cantidad**, que es la unica
 * regla donde las dos mitades del modelo se tocan.
 *
 * Pueden sumar menos, y es normal: lo que no esta en ningun lote es lo que nadie
 * se molesto en fechar. Lo que no puede es pasarse, porque entonces el modulo
 * estaria afirmando que hay mas de lo que el core dice que hay — que es
 * exactamente la discrepancia que la frontera existe para impedir.
 */
private fun requireLotsFit(
    warehouse: WarehouseRepository,
    item: StockItem,
    assetId: UUID,
    adding: BigDecimal,
) {
    val already = warehouse.liveLotsOf(assetId).fold(BigDecimal.ZERO) { sum, lot -> sum + lot.quantity }
    if (already + adding > item.quantity) {
        throw BusinessRuleViolation(
            ErrorCode.STOCK_LOT_EXCEEDS_QUANTITY,
            "Los lotes sumarían ${(already + adding).toPlainString()} y solo hay " +
                "${item.quantity.toPlainString()}",
        )
    }
}
