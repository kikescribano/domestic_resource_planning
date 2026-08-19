package com.drp.module.warehouse.adapter.http

import com.drp.core.adapter.http.JsonPatch
import com.drp.core.application.port.SessionClaims
import com.drp.core.application.usecase.Patch
import com.drp.module.warehouse.WarehouseModule
import com.drp.module.warehouse.application.DiscardStockLot
import com.drp.module.warehouse.application.GetStockItem
import com.drp.module.warehouse.application.ListStock
import com.drp.module.warehouse.application.ListStockLots
import com.drp.module.warehouse.application.ListStockMovements
import com.drp.module.warehouse.application.LotCommand
import com.drp.module.warehouse.application.LotPatch
import com.drp.module.warehouse.application.RecordStockConsumption
import com.drp.module.warehouse.application.RegisterStockLot
import com.drp.module.warehouse.application.StockFilter
import com.drp.module.warehouse.application.UpdateStockLot
import com.drp.module.warehouse.application.UpdateWarehouseArticle
import com.drp.module.warehouse.application.UpdateWarehouseLocation
import com.drp.module.warehouse.application.WarehouseArticlePatch
import com.drp.module.warehouse.application.WarehouseLocationPatch
import com.drp.platform.page.PageResponse
import com.drp.platform.page.Pagination
import com.fasterxml.jackson.databind.JsonNode
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Las diez operaciones del modulo, en HTTP.
 *
 * Tan fino como los del core y los de Proveedores: traduce HTTP a un caso de uso y
 * su resultado a la forma que declara `openapi.yaml`, sin decidir nada. **No
 * recibe `householdId`**; sale del token.
 *
 * **Y no sabe nada del gate.** Todo lo que cuelga de este prefijo responde `403
 * MODULE_INACTIVE` cuando el hogar no tiene el modulo encendido, y eso lo pone un
 * filtro de plataforma sobre el prefijo que declara `WarehouseModule`.
 *
 * Reutiliza `JsonPatch` del core --la lectura del cuerpo de un `PATCH` como arbol
 * JSON, que es la unica forma de distinguir «no menciones este campo» de «ponlo a
 * nulo»--. Aqui esa diferencia decide algo real: `{"minimumQuantity": null}`
 * **deja de vigilar** un artículo, y un cuerpo que no lo nombre lo conserva.
 */
@RestController
@RequestMapping(WarehouseModule.ROUTE_PREFIX)
class WarehouseController(
    private val listStock: ListStock,
    private val getStockItem: GetStockItem,
    private val recordConsumption: RecordStockConsumption,
    private val listMovements: ListStockMovements,
    private val listLots: ListStockLots,
    private val registerLot: RegisterStockLot,
    private val updateLot: UpdateStockLot,
    private val discardLot: DiscardStockLot,
    private val updateArticle: UpdateWarehouseArticle,
    private val updateLocation: UpdateWarehouseLocation,
) {

    /**
     * La despensa entera, con sus cuatro filtros.
     *
     * `belowMinimum` y `expiringWithinDays` son las dos preguntas que este modulo
     * existe para responder, y por eso son filtros del servidor y no de la
     * pantalla: filtrarlos en el cliente obligaria a traerse la despensa entera
     * para descartar el noventa por ciento.
     */
    @GetMapping("/stock")
    fun stock(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) locationId: UUID?,
        @RequestParam(defaultValue = "false") belowMinimum: Boolean,
        @RequestParam(required = false) expiringWithinDays: Int?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): PageResponse<StockItemResponse> = PageResponse.of(
        listStock.handle(StockFilter(q, locationId, belowMinimum, expiringWithinDays), Pagination(page, size)),
        StockItemResponse::of,
    )

    @GetMapping("/stock/{assetId}")
    fun stockItem(@PathVariable assetId: UUID): StockItemDetailResponse =
        StockItemDetailResponse.of(getStockItem.handle(assetId))

    /**
     * Registrar un consumo. **Es un delta y no un absoluto**, que es la diferencia
     * entera con el `PATCH` del core.
     *
     * Responde `204` y no la existencia actualizada, y es a proposito: el asiento
     * lo escribe el handler del evento que esto provoca, asi que devolver aqui la
     * existencia obligaria a leerla otra vez para incluir un cuaderno que puede
     * estar escribiendose. El cliente refresca, que es lo que hace de todos modos.
     */
    @PostMapping("/stock/{assetId}/consumptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun consume(
        @AuthenticationPrincipal session: SessionClaims,
        @PathVariable assetId: UUID,
        @Valid @RequestBody input: ConsumptionInput,
    ) = recordConsumption.handle(session, assetId, input.quantity!!)

    @GetMapping("/movements")
    fun movements(
        @RequestParam(required = false) assetId: UUID?,
        @RequestParam(required = false) articleId: UUID?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): PageResponse<StockMovementResponse> = PageResponse.of(
        listMovements.handle(assetId, articleId, Pagination(page, size)),
        StockMovementResponse::of,
    )

    @GetMapping("/lots")
    fun lots(
        @RequestParam(required = false) assetId: UUID?,
        @RequestParam(defaultValue = "false") includeConsumed: Boolean,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): PageResponse<StockLotResponse> = PageResponse.of(
        listLots.handle(assetId, includeConsumed, Pagination(page, size)),
        StockLotResponse::of,
    )

    @PostMapping("/lots")
    @ResponseStatus(HttpStatus.CREATED)
    fun createLot(
        @AuthenticationPrincipal session: SessionClaims,
        @Valid @RequestBody input: StockLotInput,
    ): StockLotResponse = StockLotResponse.of(
        registerLot.handle(
            session,
            LotCommand(input.assetId!!, input.lotCode, input.expiresOn!!, input.quantity!!),
        ),
    )

    @PatchMapping("/lots/{lotId}")
    fun patchLot(
        @AuthenticationPrincipal session: SessionClaims,
        @PathVariable lotId: UUID,
        @RequestBody body: JsonNode,
    ): StockLotResponse {
        val patch = JsonPatch(body)
        return StockLotResponse.of(
            updateLot.handle(
                session,
                lotId,
                LotPatch(
                    lotCode = patch.text("lotCode"),
                    // `requiredDate` no existe en `JsonPatch` y no hace falta: una
                    // caducidad a nulo no significa nada, asi que el nulo se
                    // rechaza aqui en lugar de inventar un helper.
                    expiresOn = patch.date("expiresOn").requireValue("expiresOn"),
                    quantity = patch.requiredDecimal("quantity"),
                ),
            ),
        )
    }

    /** Dar el lote por consumido. No toca el contador: eso es un consumo. */
    @DeleteMapping("/lots/{lotId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteLot(
        @AuthenticationPrincipal session: SessionClaims,
        @PathVariable lotId: UUID,
    ) = discardLot.handle(session, lotId)

    /**
     * El minimo y la antelacion de un articulo.
     *
     * `PATCH` sobre un recurso que puede no existir todavia y que **se crea al
     * tocarlo**: la ficha del modulo sobre un articulo no es algo que el usuario
     * de alta, sino algo que existe en cuanto el articulo existe. De ahi que no
     * haya un `POST` que la cree.
     */
    @PatchMapping("/articles/{articleId}")
    fun patchArticle(
        @AuthenticationPrincipal session: SessionClaims,
        @PathVariable articleId: UUID,
        @RequestBody body: JsonNode,
    ): WarehouseArticleResponse {
        val patch = JsonPatch(body)
        return WarehouseArticleResponse.of(
            updateArticle.handle(
                session,
                articleId,
                WarehouseArticlePatch(
                    minimumQuantity = patch.decimal("minimumQuantity"),
                    expiryLeadDays = patch.integer("expiryLeadDays"),
                ),
            ),
        )
    }

    @PatchMapping("/locations/{locationId}")
    fun patchLocation(
        @AuthenticationPrincipal session: SessionClaims,
        @PathVariable locationId: UUID,
        @RequestBody body: JsonNode,
    ): WarehouseLocationResponse {
        val patch = JsonPatch(body)
        return WarehouseLocationResponse.of(
            updateLocation.handle(
                session,
                locationId,
                WarehouseLocationPatch(
                    expiryLeadDays = patch.integer("expiryLeadDays"),
                    notes = patch.text("notes"),
                ),
            ),
        )
    }
}

/**
 * Un `Patch<T?>` que no admite el nulo, para los campos que se pueden cambiar
 * pero no vaciar.
 *
 * Vive aqui y no en `JsonPatch` porque es la unica operacion del contrato que lo
 * necesita para una fecha: anadir un `requiredDate` al helper del core por un solo
 * uso ensancharia una clase compartida por doce recursos.
 */
private fun <T : Any> Patch<T?>.requireValue(field: String): Patch<T> = when (this) {
    Patch.Absent -> Patch.Absent
    is Patch.Set -> Patch.Set(value ?: throw IllegalArgumentException("El campo $field no admite nulo"))
}
