package com.drp.module.purchasing.adapter.http

import com.drp.core.adapter.http.JsonPatch
import com.drp.core.application.port.SessionClaims
import com.drp.module.purchasing.PurchasingModule
import com.drp.module.purchasing.application.AddShoppingListItem
import com.drp.module.purchasing.application.CancelPurchase
import com.drp.module.purchasing.application.CreatePurchase
import com.drp.module.purchasing.application.DismissShoppingListItem
import com.drp.module.purchasing.application.GetPurchase
import com.drp.module.purchasing.application.ListPurchases
import com.drp.module.purchasing.application.ListPurchasingSuppliers
import com.drp.module.purchasing.application.ListShoppingList
import com.drp.module.purchasing.application.PurchaseCommand
import com.drp.module.purchasing.application.ReceiptCommand
import com.drp.module.purchasing.application.ReceiptLine
import com.drp.module.purchasing.application.ReceivePurchase
import com.drp.module.purchasing.application.ShoppingItemCommand
import com.drp.module.purchasing.application.ShoppingItemPatch
import com.drp.module.purchasing.application.ShoppingListFilter
import com.drp.module.purchasing.application.UpdateShoppingListItem
import com.drp.module.purchasing.domain.ItemStatus
import com.drp.module.purchasing.domain.PurchaseStatus
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
 * Tan fino como los del core y los de los dos modulos anteriores: traduce HTTP a
 * un caso de uso y su resultado a la forma que declara `openapi.yaml`, sin decidir
 * nada. **No recibe `householdId`**; sale del token.
 *
 * **Y no sabe nada del gate.** Todo lo que cuelga de este prefijo responde `403
 * MODULE_INACTIVE` cuando el hogar no tiene el modulo encendido, y eso lo pone un
 * filtro de plataforma sobre el prefijo que declara `PurchasingModule`.
 *
 * La ultima operacion merece leerse dos veces: **`/suppliers` cuelga de aqui y no
 * del prefijo de Proveedores**, asi que un hogar con Compras encendido y
 * Proveedores apagado recibe `200` con la lista vacia en lugar del `403` que le
 * daria la ruta del otro modulo. Que la degradacion sea del servidor y no del
 * cliente es lo que impide que cada consumidor futuro tenga que acordarse de ella.
 */
@RestController
@RequestMapping(PurchasingModule.ROUTE_PREFIX)
class PurchasingController(
    private val listShoppingList: ListShoppingList,
    private val addItem: AddShoppingListItem,
    private val updateItem: UpdateShoppingListItem,
    private val dismissItem: DismissShoppingListItem,
    private val listPurchases: ListPurchases,
    private val getPurchase: GetPurchase,
    private val createPurchase: CreatePurchase,
    private val receivePurchase: ReceivePurchase,
    private val cancelPurchase: CancelPurchase,
    private val listSuppliers: ListPurchasingSuppliers,
) {

    // -----------------------------------------------------------------------
    // La lista de la compra
    // -----------------------------------------------------------------------

    /**
     * La lista, con los dos estados vivos por omision.
     *
     * «Que me queda por comprar» son dos estados y no uno --lo que hace falta y lo
     * que ya va en una compra abierta--, asi que el parametro es `status` repetible
     * y su omision significa las dos cosas. Pedir lo comprado o lo descartado se
     * hace nombrandolo.
     */
    @GetMapping("/list")
    fun list(
        @RequestParam(required = false) status: List<ItemStatus>?,
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): PageResponse<ShoppingItemResponse> = PageResponse.of(
        listShoppingList.handle(
            ShoppingListFilter(
                statuses = status?.toSet()?.takeIf { it.isNotEmpty() } ?: LIVE_STATUSES,
                query = q,
            ),
            Pagination(page, size),
        ),
        ShoppingItemResponse::of,
    )

    @PostMapping("/list")
    @ResponseStatus(HttpStatus.CREATED)
    fun add(
        @AuthenticationPrincipal session: SessionClaims,
        @Valid @RequestBody input: ShoppingItemInput,
    ): ShoppingItemResponse = ShoppingItemResponse.of(
        addItem.handle(session, ShoppingItemCommand(input.articleId, input.name, input.quantity, input.note)),
    )

    /**
     * Reutiliza `JsonPatch` del core --la lectura del cuerpo como arbol JSON, que
     * es la unica forma de distinguir «no menciones este campo» de «ponlo a
     * nulo»--. Aqui esa diferencia decide algo real: `{"quantity": null}` **borra
     * la cantidad** de una linea y la deja en «hace falta, sin decir cuanto»,
     * mientras que un cuerpo que no la nombre la conserva.
     *
     * `articleId` va por `requiredUuid` y no por `uuid`: **asignarle un articulo a
     * una linea de texto suelto** es lo que este `PATCH` existe para permitir, pero
     * quitarselo la dejaria sin nombre, porque el nombre es del articulo.
     */
    @PatchMapping("/list/{id}")
    fun update(
        @AuthenticationPrincipal session: SessionClaims,
        @PathVariable id: UUID,
        @RequestBody body: JsonNode,
    ): ShoppingItemResponse {
        val patch = JsonPatch(body)
        return ShoppingItemResponse.of(
            updateItem.handle(
                session,
                id,
                ShoppingItemPatch(
                    articleId = patch.requiredUuid("articleId"),
                    quantity = patch.decimal("quantity"),
                    note = patch.text("note"),
                ),
            ),
        )
    }

    @DeleteMapping("/list/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun dismiss(@AuthenticationPrincipal session: SessionClaims, @PathVariable id: UUID) =
        dismissItem.handle(session, id)

    // -----------------------------------------------------------------------
    // La compra
    // -----------------------------------------------------------------------

    @GetMapping("/purchases")
    fun purchases(
        @RequestParam(required = false) status: PurchaseStatus?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): PageResponse<PurchaseResponse> = PageResponse.of(
        listPurchases.handle(status, Pagination(page, size)),
        PurchaseResponse::of,
    )

    @GetMapping("/purchases/{id}")
    fun purchase(@PathVariable id: UUID): PurchaseDetailResponse =
        PurchaseDetailResponse.of(getPurchase.handle(id))

    @PostMapping("/purchases")
    @ResponseStatus(HttpStatus.CREATED)
    fun open(
        @AuthenticationPrincipal session: SessionClaims,
        @Valid @RequestBody input: PurchaseInput,
    ): PurchaseDetailResponse = PurchaseDetailResponse.of(
        createPurchase.handle(session, PurchaseCommand(input.supplierId, input.note, input.itemIds.orEmpty())),
    )

    /**
     * **El cierre del ciclo**: la compra ha llegado a casa.
     *
     * Es un sub-recurso con `POST` y no un `PATCH` de estado, por lo mismo que la
     * activacion de un modulo: recibir no es cambiar un campo sino un acto con su
     * propio cuerpo --cuanto entro de cada cosa, de quien es y donde va-- que
     * ademas **escribe en el core**.
     *
     * Devuelve la compra entera y no `204`, al contrario que el consumo de
     * Warehouse: aqui hay algo que el cliente no puede componer por su cuenta
     * --que linea acabo en que existencia-- y volver a pedirlo seria una segunda
     * peticion para leer lo que esta acaba de decidir.
     */
    @PostMapping("/purchases/{id}/receipt")
    fun receive(
        @AuthenticationPrincipal session: SessionClaims,
        @PathVariable id: UUID,
        @Valid @RequestBody input: ReceiptInput,
    ): PurchaseDetailResponse = PurchaseDetailResponse.of(
        receivePurchase.handle(
            session,
            id,
            ReceiptCommand(
                input.lines.orEmpty().mapNotNull { line ->
                    line.itemId?.let { ReceiptLine(it, line.quantity, line.ownerId, line.locationId) }
                },
            ),
        ),
    )

    @DeleteMapping("/purchases/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun cancel(@AuthenticationPrincipal session: SessionClaims, @PathVariable id: UUID) =
        cancelPurchase.handle(session, id)

    // -----------------------------------------------------------------------
    // Donde se compra
    // -----------------------------------------------------------------------

    @GetMapping("/suppliers")
    fun suppliers(@RequestParam(required = false) q: String?): List<PurchasingSupplierResponse> =
        listSuppliers.handle(q).map(PurchasingSupplierResponse::of)

    private companion object {
        /** Lo que hace falta y lo que ya va en una compra: las dos mitades de «me queda por comprar». */
        val LIVE_STATUSES = setOf(ItemStatus.NEEDED, ItemStatus.IN_PURCHASE)
    }
}
