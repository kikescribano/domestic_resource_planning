package com.drp.module.purchasing.domain

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * El dominio del modulo Compras: **que falta, que esta pedido y que ha entrado en
 * casa**.
 *
 * La ficha esta en `docs/backend/modules/purchasing.md` y se escribio antes que
 * esto. Lo que hay que tener presente al leer este fichero son las dos fronteras
 * que declara, porque son lo unico que impide que este modulo se convierta en una
 * segunda version de los dos que tiene al lado:
 *
 * **Contra Warehouse: Warehouse detecta la falta, Compras decide que se compra y
 * cuando.** Ninguna clase de aqui guarda una cantidad disponible, ni un minimo,
 * ni una caducidad. Lo que llega es un hecho sobre un articulo del core.
 *
 * **Contra el core: el contador es suyo.** La [ShoppingListItem.quantity] de aqui
 * es *cuanto hace falta*, no cuanto hay; son preguntas distintas y por eso pueden
 * convivir sin contradecirse. Cuando la compra llega, quien suma es el core.
 */

/**
 * Una linea de la lista de la compra.
 *
 * **Apunta a un articulo del core o lleva un nombre suelto**, nunca las dos cosas
 * y nunca ninguna. El nombre suelto no es un caso degradado sino uno normal: una
 * lista de la compra de verdad lleva «pilas AA» antes de que nadie haya dado de
 * alta ese articulo, y obligar a crearlo primero convertiria apuntar algo en un
 * formulario.
 *
 * Lo que una linea asi no puede hacer es **dar entrada en el almacen**, porque
 * inventarle una categoria y una unidad seria decidir por el hogar. Se compra, se
 * marca comprada y ahi acaba; quien la quiera en el inventario le asigna un
 * articulo antes con el `PATCH`.
 */
data class ShoppingListItem(
    val id: UUID,
    val articleId: UUID?,
    val name: String?,
    /**
     * Cuanta falta, **si alguien lo ha dicho**. Nulo es legitimo: «hace falta
     * arroz» es una linea completa, y exigir un numero para apuntar algo es la
     * forma mas rapida de que nadie apunte nada.
     */
    val quantity: BigDecimal?,
    val origin: ItemOrigin,
    val status: ItemStatus,
    val note: String?,
    val purchaseId: UUID?,
    /** La existencia del core que resulto de recibirla: la traza de que el ciclo se cerro. */
    val receivedAssetId: UUID?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val createdBy: UUID?,
    val updatedBy: UUID?,
) {
    /** Viva: cuenta para la regla de una sola linea por articulo. */
    val isLive: Boolean get() = status == ItemStatus.NEEDED || status == ItemStatus.IN_PURCHASE

    /** Puede dar entrada en el core, que es tanto como decir que tiene articulo. */
    val isStockable: Boolean get() = articleId != null

    /**
     * La invariante 1, escrita como funcion y no como `init { require }`.
     *
     * Es la misma forma que usan `Supplier.isReachable()` y los lotes de
     * Warehouse: el dominio dice **si algo cumple**, y quien decide que hacer con
     * un incumplimiento --que error, con que mensaje, con que codigo del
     * contrato-- es el caso de uso. Un `require` aqui daria un `IllegalArgument`
     * que el manejador de errores traduciria a un `500`.
     */
    fun identifiesOneThing(): Boolean = (articleId != null) != (name != null)
}

/**
 * Por que entro la linea.
 *
 * **El orden importa y no es alfabetico**: el `ordinal` es lo que decide si un
 * origen manda sobre otro cuando el mismo articulo vuelve a dar noticia. Acabarse
 * es una noticia mas fuerte que bajar del minimo, y las dos lo son mas que
 * haberlo apuntado a mano --que ya estaba dicho--. Reordenar esto cambia la
 * regla.
 */
enum class ItemOrigin {
    /** Lo puso una persona. **Es el unico que existe con Warehouse apagado**, y esta bien que asi sea. */
    MANUAL,

    /** Warehouse dijo que bajo del minimo que el hogar habia fijado. */
    LOW_STOCK,

    /** Warehouse dijo que se acabo. */
    DEPLETED,
    ;

    /** Cierto si [other] es una noticia mas fuerte que esta. */
    fun isWeakerThan(other: ItemOrigin): Boolean = ordinal < other.ordinal
}

/**
 * En que punto esta una linea.
 *
 * `DISMISSED` es baja logica y no borrado, igual que la retirada de un `Supplier`
 * o de una `Category` del core: descartar «hace falta sal» tres veces seguidas es
 * un dato sobre lo que el hogar no quiere comprar, y borrarlo dejaria que el
 * mismo evento la volviera a meter al minuto siguiente.
 */
enum class ItemStatus { NEEDED, IN_PURCHASE, BOUGHT, DISMISSED }

/**
 * Un acto de comprar: donde, cuando y que se lleva.
 *
 * Existe como cosa aparte de sus lineas porque **una compra se prepara antes de
 * hacerse**: se decide que se lleva, se va, y se recibe --o no--. Sin ella, la
 * lista tendria que responder «que estoy comprando ahora mismo» con un estado por
 * linea y sin nada que las agrupe.
 *
 * @param supplierName **el nombre de aquel dia**, copiado al crear la compra y no
 *   vuelto a mirar. Es la misma decision que el nombre del sitio en un asiento de
 *   Warehouse, y aqui tiene un segundo motivo: una clave ajena hacia la tabla de
 *   Proveedores seria una dependencia de esquema entre dos modulos, que es la que
 *   ArchUnit no puede ver.
 */
data class Purchase(
    val id: UUID,
    val supplierId: UUID?,
    val supplierName: String?,
    val status: PurchaseStatus,
    val note: String?,
    val receivedAt: Instant?,
    val cancelledAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val createdBy: UUID?,
    val updatedBy: UUID?,
) {
    val isOpen: Boolean get() = status == PurchaseStatus.OPEN
}

enum class PurchaseStatus { OPEN, RECEIVED, CANCELLED }

/**
 * Una linea con lo que el core sabe de ella, ya juntado.
 *
 * El nombre, la unidad y el envase **son del core y se leen al construir esto**,
 * no se copian en ninguna tabla de este modulo. Es la misma decision que el
 * `StockItem` de Warehouse toma con la cantidad, y por el mismo motivo: dos
 * copias del mismo nombre acaban diciendo cosas distintas el dia que alguien
 * renombre el articulo.
 */
data class ShoppingListEntry(
    val item: ShoppingListItem,
    /** Del core. Nulo cuando la linea lleva nombre suelto. */
    val articleName: String?,
    val unit: String?,
    /** Cuanto trae un envase, en la unidad del articulo. Del core: `Article.packSize`. */
    val packSize: BigDecimal?,
) {
    /** Lo que se lee en pantalla: el nombre del articulo, o el nombre suelto. */
    val label: String get() = articleName ?: item.name.orEmpty()

    /**
     * **La presentacion de compra, compuesta y no guardada.**
     *
     * Es la respuesta de este hito a la media pregunta que venia de la Fase 1:
     * «pack de 6» y «garrafa de 5 l» no necesitan nombre propio, porque el par
     * `packSize`/`unit` ya lo dice todo. Un texto libre seria una segunda fuente
     * de verdad que puede contradecir al envase --alguien escribe «pack de 6»
     * sobre un articulo cuyo envase trae 4-- y no anade nada.
     *
     * Nulo cuando el articulo no declara envase, que es el caso normal: casi nada
     * de una despensa se compra por paquetes de tamano fijo.
     */
    val packLabel: String? get() = packSize?.let { "${it.stripTrailingZeros().toPlainString()} ${unit.orEmpty()}" }
}

/** Una compra con sus lineas, que es como se lee siempre: sola no dice nada. */
data class PurchaseDetail(val purchase: Purchase, val lines: List<ShoppingListEntry>)
