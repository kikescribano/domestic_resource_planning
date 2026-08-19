package com.drp.module.purchasing.application

import com.drp.core.domain.inventory.AssetLocation
import com.drp.module.purchasing.domain.ItemOrigin
import com.drp.module.purchasing.domain.ItemStatus
import com.drp.module.purchasing.domain.Purchase
import com.drp.module.purchasing.domain.PurchaseStatus
import com.drp.module.purchasing.domain.ShoppingListEntry
import com.drp.module.purchasing.domain.ShoppingListItem
import com.drp.platform.page.Page
import com.drp.platform.page.Pagination
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * El almacen del modulo.
 *
 * **Ninguna operacion recibe el hogar**, exactamente igual que los repositorios
 * del core, los de plataforma y los de los dos modulos anteriores: el filtro lo
 * pone la politica de RLS a partir del `app.household_id` que fija el gestor de
 * transacciones. Un parametro `householdId` aqui seria la puerta por la que se
 * cuela leer el hogar de al lado, porque tarde o temprano alguien lo tomaria del
 * cuerpo de la peticion.
 *
 * Las cuatro ultimas leen **estado del core** --articulos y existencias-- y no
 * tablas de este modulo. Es la direccion `modulo -> core` que la ADR-010 permite,
 * resuelta por SQL y sin importar ninguna clase de su persistencia.
 */
interface PurchasingRepository {

    // -----------------------------------------------------------------------
    // La lista
    // -----------------------------------------------------------------------

    fun listItems(filter: ShoppingListFilter, pagination: Pagination): Page<ShoppingListEntry>

    fun findItem(itemId: UUID): ShoppingListEntry?

    fun findLiveItemByArticle(articleId: UUID): ShoppingListItem?

    fun saveItem(item: ShoppingListItem): ShoppingListItem

    /**
     * Abre una linea para un articulo **si no habia ninguna viva**.
     *
     * `ON CONFLICT DO NOTHING` sobre el indice unico parcial, que es lo que la
     * hace idempotente **sin comprobar antes**: comprobar y despues insertar deja
     * una ventana entre las dos cosas por la que caben dos entregas simultaneas
     * del mismo evento. Es la leccion que el Hito 3 dejo escrita.
     *
     * @return cierto si abrio una linea nueva.
     */
    fun openItemForArticle(
        articleId: UUID,
        origin: ItemOrigin,
        quantity: BigDecimal?,
        at: Instant,
        by: UUID?,
    ): Boolean

    /** Sube el origen de la linea viva de un articulo, si lo que llega es una noticia mas fuerte. */
    fun raiseOrigin(articleId: UUID, origin: ItemOrigin, at: Instant): Boolean

    fun linesOf(purchaseId: UUID): List<ShoppingListEntry>

    // -----------------------------------------------------------------------
    // La compra
    // -----------------------------------------------------------------------

    fun listPurchases(status: PurchaseStatus?, pagination: Pagination): Page<Purchase>

    fun findPurchase(purchaseId: UUID): Purchase?

    fun savePurchase(purchase: Purchase): Purchase

    /**
     * Mete en la compra las lineas que esten **exactamente** en [ItemStatus.NEEDED].
     *
     * @return cuantas entraron, que es lo que permite decir «una de estas ya no
     *   estaba disponible» sin haber leido y comprobado antes en dos pasos.
     */
    fun claimItemsForPurchase(itemIds: List<UUID>, purchaseId: UUID, at: Instant, by: UUID?): Int

    /**
     * Reclama una linea para recibirla: `IN_PURCHASE` -> `BOUGHT` **en una sola
     * sentencia**.
     *
     * Es la idempotencia del cierre, y va asi porque **el cierre no cabe en una
     * transaccion**: da entrada linea a linea invocando al core, y cada invocacion
     * abre la suya. Una comparacion y un cambio en una sola sentencia no dejan
     * ventana entre el «no esta» y el «ya esta», que es lo mismo que consigue un
     * indice unico donde se puede usar uno.
     *
     * @return cierto si esta llamada fue la que la reclamo.
     */
    fun claimItemForReceipt(itemId: UUID, at: Instant, by: UUID?): Boolean

    /** Anota que existencia del core resulto de recibir la linea. */
    fun recordIntake(itemId: UUID, assetId: UUID, at: Instant)

    /** Devuelve a la lista las lineas de una compra que se anula. */
    fun releaseItemsOf(purchaseId: UUID, at: Instant, by: UUID?): Int

    /**
     * Cambia el estado de una compra **solo si estaba en [from]**, por lo mismo
     * que [claimItemForReceipt].
     */
    fun changePurchaseStatus(
        purchaseId: UUID,
        from: PurchaseStatus,
        to: PurchaseStatus,
        at: Instant,
        by: UUID?,
    ): Boolean

    // -----------------------------------------------------------------------
    // Lo que se lee del core
    // -----------------------------------------------------------------------

    /** Cierto si el articulo existe y esta vigente **en este hogar**. */
    fun isLiveArticle(articleId: UUID): Boolean

    /**
     * Los articulos de consumible que el hogar tiene **a cero** ahora mismo.
     *
     * Es lo que siembra este modulo, y es una lectura del **contador del core**:
     * la suma de las existencias vivas de un articulo. Lo que NO se lee es el
     * minimo, que vive en una tabla de Warehouse.
     */
    fun depletedArticleIds(): List<UUID>

    /**
     * Donde vive ya ese articulo, **si vive en un solo sitio**.
     *
     * Es lo que evita que recibir una compra parta en dos una existencia que ya
     * existia: `RegisterConsumableIntake` busca la existencia por *articulo y
     * ubicacion*, asi que dar entrada sin ubicacion cuando el arroz esta en la
     * despensa **crea una segunda fila** en lugar de sumar. Con dos sitios o con
     * ninguno devuelve nulo y decide quien recibe.
     */
    fun soleLocationOf(articleId: UUID): AssetLocation?

    /** Cierto si esa pertenencia es del hogar y esta activa: el propietario de lo que entra. */
    fun isActiveMember(memberId: UUID): Boolean

    /** Cierto si esa ubicacion es del hogar: el destino de lo que entra. */
    fun isOwnLocation(locationId: UUID): Boolean
}

/**
 * Los filtros de la lista.
 *
 * `statuses` en plural y no un estado suelto porque la pregunta normal --«que me
 * queda por comprar»-- son dos estados y no uno: lo que hace falta y lo que ya va
 * en una compra abierta.
 */
data class ShoppingListFilter(
    val statuses: Set<ItemStatus>,
    val query: String? = null,
)
