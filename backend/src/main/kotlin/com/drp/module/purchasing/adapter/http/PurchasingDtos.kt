package com.drp.module.purchasing.adapter.http

import com.drp.module.purchasing.domain.ItemOrigin
import com.drp.module.purchasing.domain.ItemStatus
import com.drp.module.purchasing.domain.Purchase
import com.drp.module.purchasing.domain.PurchaseDetail
import com.drp.module.purchasing.domain.PurchaseStatus
import com.drp.module.purchasing.domain.ShoppingListEntry
import com.drp.platform.directory.MasterDataEntry
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Las formas que declara `openapi.yaml` para este modulo.
 *
 * **El nombre, la unidad y el envase de una linea viajan aqui y no se guardan en
 * ninguna tabla del modulo**: salen de `articles` al leer. Es la frontera de la
 * ficha vista desde el contrato --el cliente recibe un nombre porque solo hay uno,
 * no porque se hayan reconciliado dos.
 *
 * **Y el proveedor viaja al reves**: `supplier` es el nombre **copiado el dia de
 * la compra**, no el de hoy. Es lo unico que se puede pintar cuando Proveedores
 * esta apagado, y ademas lo correcto: una compra es historia.
 */

data class ShoppingItemResponse(
    val id: UUID,
    val articleId: UUID?,
    /** El del articulo, o el nombre suelto de la linea. Siempre hay uno. */
    val name: String,
    val unit: String?,
    /** «Pack de 6» compuesto con el `packSize` del core, no guardado. Nulo si el articulo no declara envase. */
    val packLabel: String?,
    val quantity: BigDecimal?,
    val origin: ItemOrigin,
    val status: ItemStatus,
    val note: String?,
    val purchaseId: UUID?,
    /** La existencia del core que resulto: la traza de que el ciclo se cerro. */
    val receivedAssetId: UUID?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun of(entry: ShoppingListEntry) = ShoppingItemResponse(
            id = entry.item.id,
            articleId = entry.item.articleId,
            name = entry.label,
            unit = entry.unit,
            packLabel = entry.packLabel,
            quantity = entry.item.quantity,
            origin = entry.item.origin,
            status = entry.item.status,
            note = entry.item.note,
            purchaseId = entry.item.purchaseId,
            receivedAssetId = entry.item.receivedAssetId,
            createdAt = entry.item.createdAt,
            updatedAt = entry.item.updatedAt,
        )
    }
}

data class PurchaseResponse(
    val id: UUID,
    val supplierId: UUID?,
    /** El nombre de aquel dia. Ver la nota de arriba. */
    val supplier: String?,
    val status: PurchaseStatus,
    val note: String?,
    val receivedAt: Instant?,
    val cancelledAt: Instant?,
    val createdAt: Instant,
) {
    companion object {
        fun of(purchase: Purchase) = PurchaseResponse(
            id = purchase.id,
            supplierId = purchase.supplierId,
            supplier = purchase.supplierName,
            status = purchase.status,
            note = purchase.note,
            receivedAt = purchase.receivedAt,
            cancelledAt = purchase.cancelledAt,
            createdAt = purchase.createdAt,
        )
    }
}

data class PurchaseDetailResponse(
    val purchase: PurchaseResponse,
    val lines: List<ShoppingItemResponse>,
) {
    companion object {
        fun of(detail: PurchaseDetail) = PurchaseDetailResponse(
            purchase = PurchaseResponse.of(detail.purchase),
            lines = detail.lines.map(ShoppingItemResponse::of),
        )
    }
}

/**
 * Donde se puede comprar, con **lo minimo que el puerto de plataforma deja
 * pasar**: quien es y como se llama.
 *
 * `detail` trae el identificador de la categoria de servicio, no su rotulo: la
 * ficha de Proveedores decidio que el texto que se lee en pantalla es un dato en
 * castellano y lo pone el cliente, que ya tiene ese mapa para su propia pantalla.
 */
data class PurchasingSupplierResponse(val id: UUID, val name: String, val detail: String?) {
    companion object {
        fun of(entry: MasterDataEntry) = PurchasingSupplierResponse(entry.id, entry.name, entry.detail)
    }
}

// ---------------------------------------------------------------------------
// Lo que entra
// ---------------------------------------------------------------------------

/**
 * Apuntar algo.
 *
 * **`articleId` y `name` son excluyentes y no se validan aqui** sino en el caso de
 * uso: «uno de los dos y solo uno» no lo expresa ninguna anotacion de Bean
 * Validation sin escribir una propia, y el mensaje que el contrato declara sale
 * mejor de una linea de Kotlin que de un validador a medida.
 */
data class ShoppingItemInput(
    val articleId: UUID?,
    val name: String?,
    @field:Positive(message = "la cantidad es mayor que cero")
    val quantity: BigDecimal?,
    val note: String?,
)

data class PurchaseInput(
    val supplierId: UUID?,
    val note: String?,
    @field:NotEmpty(message = "una compra necesita al menos una línea")
    val itemIds: List<UUID>?,
)

/**
 * Lo que dice quien recibe la compra, linea a linea.
 *
 * **Los tres campos son opcionales**, y esa es la decision de producto del hito:
 * la lista dice «hace falta arroz» y no «de quien es y en que balda va», asi que
 * cuanto, de quien y donde **se deciden al recibir** y tienen salida por omision.
 * Una compra entera se puede recibir con `{"lines": []}` si las lineas ya traian
 * cantidad.
 */
data class ReceiptLineInput(
    val itemId: UUID?,
    @field:Positive(message = "la cantidad es mayor que cero")
    val quantity: BigDecimal?,
    val ownerId: UUID?,
    val locationId: UUID?,
)

data class ReceiptInput(val lines: List<ReceiptLineInput>?)
