package com.drp.module.purchasing.application

import com.drp.core.application.port.SessionClaims
import com.drp.core.application.usecase.IntakeCommand
import com.drp.core.application.usecase.Patch
import com.drp.core.application.usecase.RegisterConsumableIntake
import com.drp.core.application.usecase.orKeep
import com.drp.core.domain.inventory.AssetLocation
import com.drp.core.domain.inventory.LocationRefKind
import com.drp.module.purchasing.domain.ItemOrigin
import com.drp.module.purchasing.domain.ItemStatus
import com.drp.module.purchasing.domain.Purchase
import com.drp.module.purchasing.domain.PurchaseDetail
import com.drp.module.purchasing.domain.PurchaseStatus
import com.drp.module.purchasing.domain.ShoppingListEntry
import com.drp.module.purchasing.domain.ShoppingListItem
import com.drp.platform.directory.MasterData
import com.drp.platform.directory.MasterDataEntry
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
import java.util.UUID

/**
 * Las diez operaciones del modulo.
 *
 * **Las dos fronteras, tal y como se cumplen aqui:**
 *
 * - **Contra Warehouse.** Ninguna de estas clases mira existencias ni minimos. Lo
 *   que Warehouse sabe llega por el bus a `PurchasingEventHandler`, y lo que se
 *   decide con ello --que se compra, cuanto y cuando-- se decide aqui.
 * - **Contra el core.** [ReceivePurchase] cierra el ciclo **invocando el caso de
 *   uso del core** en lugar de tocar su tabla, que es la direccion `modulo ->
 *   core` que la ADR-010 permite. Warehouse la usaba para mover un contador que
 *   existia; aqui se usa para **crear existencias**, que es su forma mas fuerte.
 */

/**
 * La clave del modulo que posee el dato maestro que este lee.
 *
 * **Es una cadena y no `SuppliersModule.KEY`, y eso es exactamente el punto.**
 * ArchUnit falla la construccion si un modulo referencia a otro, asi que lo unico
 * que Compras conoce de Proveedores es su clave --el mismo identificador que
 * escribe un administrador al encenderlo-- y lo que el puerto de plataforma le
 * quiera contar. Si Proveedores desapareciera del despliegue, esto no dejaria de
 * compilar: dejaria de encontrar directorio, que es un caso ya contemplado.
 */
private const val SUPPLIERS = "SUPPLIERS"

/** Cuantas entradas devuelve el selector de donde se compra. */
private const val SUPPLIER_CHOICES = 50

data class ShoppingItemCommand(
    val articleId: UUID?,
    val name: String?,
    val quantity: BigDecimal?,
    val note: String?,
)

data class ShoppingItemPatch(
    val articleId: Patch<UUID> = Patch.Absent,
    val quantity: Patch<BigDecimal?> = Patch.Absent,
    val note: Patch<String?> = Patch.Absent,
)

data class PurchaseCommand(
    val supplierId: UUID?,
    val note: String?,
    val itemIds: List<UUID>,
)

/**
 * Lo que quien recibe la compra dice de una linea: **cuanta entro, de quien es y
 * donde va**.
 *
 * Los tres son opcionales y los tres tienen una salida por omision, que es la
 * decision de producto de este hito: la lista dice «hace falta arroz», no «de
 * quien es y en que balda va», asi que **eso se decide al recibir** y no al
 * apuntar. Ver [ReceivePurchase].
 */
data class ReceiptLine(
    val itemId: UUID,
    val quantity: BigDecimal?,
    val ownerId: UUID?,
    val locationId: UUID?,
)

data class ReceiptCommand(val lines: List<ReceiptLine>)

// ---------------------------------------------------------------------------
// La lista de la compra
// ---------------------------------------------------------------------------

@Service
class ListShoppingList(private val purchasing: PurchasingRepository) {

    @Transactional(readOnly = true)
    fun handle(filter: ShoppingListFilter, pagination: Pagination): Page<ShoppingListEntry> =
        purchasing.listItems(filter.copy(query = filter.query?.trim()?.takeIf { it.isNotEmpty() }), pagination)
}

/**
 * Apuntar algo a mano.
 *
 * **Es la operacion que hace que la lista siga sirviendo con Warehouse apagado**,
 * y por eso no es una comodidad sino parte de la frontera: sin Warehouse nadie
 * detecta la falta, y una lista de la compra que solo se llena sola seria una
 * lista vacia en la mitad de los hogares.
 */
@Service
class AddShoppingListItem(
    private val purchasing: PurchasingRepository,
    private val clock: Clock,
) {

    @Transactional
    fun handle(session: SessionClaims, command: ShoppingItemCommand): ShoppingListEntry {
        val name = command.name?.trim()?.takeIf { it.isNotEmpty() }

        if ((command.articleId != null) == (name != null)) {
            throw ValidationFailure(
                mapOf("articleId" to "una línea apunta a un artículo o lleva un nombre, nunca las dos cosas"),
            )
        }
        command.quantity?.let {
            if (it.signum() <= 0) throw ValidationFailure(mapOf("quantity" to "la cantidad es mayor que cero"))
        }

        if (command.articleId != null) {
            if (!purchasing.isLiveArticle(command.articleId)) throw ResourceNotFound("Artículo no encontrado")

            // La comprobacion previa da el mensaje que el contrato declara; la que
            // de verdad cierra la carrera entre dos peticiones es el indice unico
            // parcial, y la lanza el repositorio. Las dos, como en el core.
            purchasing.findLiveItemByArticle(command.articleId)?.let {
                throw BusinessRuleViolation(
                    ErrorCode.SHOPPING_ITEM_DUPLICATE,
                    "Eso ya está en la lista de la compra",
                )
            }
        }

        val now = clock.instant()
        val saved = purchasing.saveItem(
            ShoppingListItem(
                id = UUID.randomUUID(),
                articleId = command.articleId,
                name = name,
                quantity = command.quantity,
                origin = ItemOrigin.MANUAL,
                status = ItemStatus.NEEDED,
                note = command.note?.trim()?.takeIf { it.isNotEmpty() },
                purchaseId = null,
                receivedAssetId = null,
                createdAt = now,
                updatedAt = now,
                createdBy = session.memberId,
                updatedBy = session.memberId,
            ),
        )
        return purchasing.findItem(saved.id) ?: error("La línea recién creada no se puede leer")
    }
}

@Service
class UpdateShoppingListItem(
    private val purchasing: PurchasingRepository,
    private val clock: Clock,
) {

    @Transactional
    fun handle(session: SessionClaims, itemId: UUID, patch: ShoppingItemPatch): ShoppingListEntry {
        val current = purchasing.findItem(itemId)?.item ?: throw ResourceNotFound("Esa línea no está en la lista")

        if (!current.isLive) {
            throw BusinessRuleViolation(
                ErrorCode.SHOPPING_ITEM_NOT_PENDING,
                "Una línea que ya se compró o se descartó no se cambia",
            )
        }

        val quantity = patch.quantity.orKeep(current.quantity)
        quantity?.let {
            if (it.signum() <= 0) throw ValidationFailure(mapOf("quantity" to "la cantidad es mayor que cero"))
        }

        // **Asignarle un articulo a una linea de texto suelto** es la unica forma
        // de que «pilas AA» acabe entrando en el almacen, y por eso es lo que este
        // `PATCH` existe para permitir. Al reves no: quitarle el articulo a una
        // linea la dejaria sin nombre, porque el nombre es del articulo.
        val articleId = patch.articleId.orKeep(current.articleId)
        if (articleId != current.articleId && articleId != null) {
            if (!purchasing.isLiveArticle(articleId)) throw ResourceNotFound("Artículo no encontrado")
            purchasing.findLiveItemByArticle(articleId)?.let {
                throw BusinessRuleViolation(
                    ErrorCode.SHOPPING_ITEM_DUPLICATE,
                    "Eso ya está en la lista de la compra",
                )
            }
        }

        val updated = current.copy(
            articleId = articleId,
            // El nombre suelto se va con la asignacion del articulo: conservarlo
            // dejaria la fila incumpliendo la invariante 1 y la restriccion del
            // esquema la rechazaria, que es una forma peor de decir lo mismo.
            name = if (articleId != null) null else current.name,
            quantity = quantity,
            note = patch.note.orKeep(current.note)?.trim()?.takeIf { it.isNotEmpty() },
            updatedAt = clock.instant(),
            updatedBy = session.memberId,
        )
        purchasing.saveItem(updated)
        return purchasing.findItem(itemId) ?: error("La línea recién guardada no se puede leer")
    }
}

/**
 * Descartar sin comprar.
 *
 * **Es baja logica y no borrado**, igual que la retirada de un `Supplier`: que el
 * hogar haya descartado «sal» tres veces es un dato sobre lo que no quiere
 * comprar, y borrar la fila dejaria que el mismo evento la volviera a meter al
 * minuto siguiente. Descartar dos veces no es un error.
 */
@Service
class DismissShoppingListItem(
    private val purchasing: PurchasingRepository,
    private val clock: Clock,
) {

    @Transactional
    fun handle(session: SessionClaims, itemId: UUID) {
        val current = purchasing.findItem(itemId)?.item ?: throw ResourceNotFound("Esa línea no está en la lista")

        if (current.status == ItemStatus.DISMISSED) return
        if (current.status == ItemStatus.BOUGHT) {
            throw BusinessRuleViolation(
                ErrorCode.SHOPPING_ITEM_NOT_PENDING,
                "Lo que ya entró en casa no se descarta",
            )
        }

        purchasing.saveItem(
            current.copy(
                status = ItemStatus.DISMISSED,
                purchaseId = null,
                updatedAt = clock.instant(),
                updatedBy = session.memberId,
            ),
        )
    }
}

// ---------------------------------------------------------------------------
// La compra
// ---------------------------------------------------------------------------

@Service
class ListPurchases(private val purchasing: PurchasingRepository) {

    @Transactional(readOnly = true)
    fun handle(status: PurchaseStatus?, pagination: Pagination): Page<Purchase> =
        purchasing.listPurchases(status, pagination)
}

@Service
class GetPurchase(private val purchasing: PurchasingRepository) {

    @Transactional(readOnly = true)
    fun handle(purchaseId: UUID): PurchaseDetail {
        val purchase = purchasing.findPurchase(purchaseId) ?: throw ResourceNotFound("Esa compra no existe")
        return PurchaseDetail(purchase, purchasing.linesOf(purchaseId))
    }
}

/**
 * Abrir una compra con lo que se lleva.
 *
 * **Aqui es donde se lee el dato maestro de Proveedores**, y donde se ve la forma
 * que tiene esa lectura: se pide por la clave del modulo dueno a un puerto de
 * plataforma, que **comprueba la activacion antes de preguntar nada**. Con
 * Proveedores apagado el puerto responde nulo, igual que responderia ante un
 * identificador inventado, y esta operacion no distingue los dos casos ni tiene
 * por que: distinguirlos obligaria a Compras a saber que modulos tiene encendidos
 * el hogar.
 *
 * **Una compra sin proveedor es una compra valida** --«fui al mercado»--, que es
 * lo que hace aceptable el precio del puerto: la degradacion no recorta ninguna
 * funcionalidad.
 */
@Service
class CreatePurchase(
    private val purchasing: PurchasingRepository,
    private val masterData: MasterData,
    private val clock: Clock,
) {

    @Transactional
    fun handle(session: SessionClaims, command: PurchaseCommand): PurchaseDetail {
        val itemIds = command.itemIds.distinct()
        if (itemIds.isEmpty()) {
            throw BusinessRuleViolation(ErrorCode.PURCHASE_EMPTY, "Una compra sin nada que comprar no se abre")
        }

        val supplier = command.supplierId?.let { id ->
            masterData.find(SUPPLIERS, id) ?: throw BusinessRuleViolation(
                ErrorCode.PURCHASE_SUPPLIER_UNKNOWN,
                "No se puede leer dónde se compra: o ese sitio no existe, o Proveedores está apagado",
            )
        }

        val now = clock.instant()
        val purchase = purchasing.savePurchase(
            Purchase(
                id = UUID.randomUUID(),
                supplierId = supplier?.id,
                // **El nombre de aquel dia.** Se copia una vez y no se vuelve a
                // mirar: una compra de marzo tiene que poder decir donde se
                // compro aunque el sitio se retire o el hogar apague Proveedores.
                supplierName = supplier?.name,
                status = PurchaseStatus.OPEN,
                note = command.note?.trim()?.takeIf { it.isNotEmpty() },
                receivedAt = null,
                cancelledAt = null,
                createdAt = now,
                updatedAt = now,
                createdBy = session.memberId,
                updatedBy = session.memberId,
            ),
        )

        // Se reclaman las que sigan disponibles **en una sola sentencia**, y se
        // cuenta: leerlas antes para comprobarlas dejaria una ventana en la que
        // otra peticion podria llevarselas a otra compra.
        val taken = purchasing.claimItemsForPurchase(itemIds, purchase.id, now, session.memberId)
        if (taken == 0) {
            throw BusinessRuleViolation(
                ErrorCode.SHOPPING_ITEM_NOT_PENDING,
                "Ninguna de esas líneas está disponible: o ya van en otra compra, o ya se compraron",
            )
        }

        return PurchaseDetail(purchase, purchasing.linesOf(purchase.id))
    }
}

/**
 * Anular una compra.
 *
 * **Sus lineas vuelven a la lista en `NEEDED`**, que es lo unico que puede pasar:
 * lo que hacia falta sigue haciendo falta, y dejarlas colgando de una compra
 * anulada las escondería de la lista sin que nadie hubiera decidido no comprarlas.
 * Quien no las quiera las descarta, que es otra operacion y otra intencion.
 */
@Service
class CancelPurchase(
    private val purchasing: PurchasingRepository,
    private val clock: Clock,
) {

    @Transactional
    fun handle(session: SessionClaims, purchaseId: UUID) {
        val purchase = purchasing.findPurchase(purchaseId) ?: throw ResourceNotFound("Esa compra no existe")
        if (purchase.status == PurchaseStatus.CANCELLED) return

        val now = clock.instant()
        val changed = purchasing.changePurchaseStatus(
            purchaseId,
            from = PurchaseStatus.OPEN,
            to = PurchaseStatus.CANCELLED,
            at = now,
            by = session.memberId,
        )
        if (!changed) {
            throw BusinessRuleViolation(ErrorCode.PURCHASE_NOT_OPEN, "Esa compra ya se recibió")
        }

        purchasing.releaseItemsOf(purchaseId, now, session.memberId)
    }
}

/**
 * Las lecturas y los cambios de estado del cierre, **cada uno en su transaccion**.
 *
 * Existe por [ReceivePurchase], que es la unica operacion del modulo que **no
 * puede abrir transaccion propia**: la de `RegisterConsumableIntake` tiene que ser
 * la de fuera para que al cerrarse dispare el `AFTER_COMMIT` que escribe el
 * asiento en el cuaderno de Warehouse. Y sin transaccion no hay
 * `app.household_id`, asi que la politica de RLS no ve hogar y toda consulta
 * devuelve **cero filas** diciendo «eso no existe» sobre algo que si existe.
 *
 * Es un bean aparte y no un metodo privado porque `@Transactional` sobre una
 * autoinvocacion **no se aplica**: Spring teje la transaccion en el proxy, y una
 * llamada desde dentro de la misma clase no pasa por el. Es la misma forma que el
 * `StockReader` de Warehouse, que aprendio esto mismo un hito antes.
 */
@Service
class ReceiptSteps(private val purchasing: PurchasingRepository) {

    @Transactional(readOnly = true)
    fun purchase(purchaseId: UUID): Purchase? = purchasing.findPurchase(purchaseId)

    @Transactional(readOnly = true)
    fun lines(purchaseId: UUID): List<ShoppingListEntry> = purchasing.linesOf(purchaseId)

    @Transactional(readOnly = true)
    fun soleLocationOf(articleId: UUID): AssetLocation? = purchasing.soleLocationOf(articleId)

    @Transactional(readOnly = true)
    fun isActiveMember(memberId: UUID): Boolean = purchasing.isActiveMember(memberId)

    @Transactional(readOnly = true)
    fun isOwnLocation(locationId: UUID): Boolean = purchasing.isOwnLocation(locationId)

    @Transactional
    fun claim(itemId: UUID, at: java.time.Instant, by: UUID?): Boolean =
        purchasing.claimItemForReceipt(itemId, at, by)

    @Transactional
    fun recordIntake(itemId: UUID, assetId: UUID, at: java.time.Instant) =
        purchasing.recordIntake(itemId, assetId, at)

    @Transactional
    fun close(purchaseId: UUID, at: java.time.Instant, by: UUID?): Boolean = purchasing.changePurchaseStatus(
        purchaseId,
        from = PurchaseStatus.OPEN,
        to = PurchaseStatus.RECEIVED,
        at = at,
        by = by,
    )
}

/**
 * **El cierre del ciclo.** La compra ha llegado a casa.
 *
 * Cada linea con articulo da entrada en el core invocando
 * `RegisterConsumableIntake`, que **suma** sobre la existencia que ya haya en esa
 * ubicacion. Y como el core publica al hacerlo, **cerrar una compra escribe un
 * asiento en el cuaderno de Warehouse** si Warehouse esta encendido, sin que
 * ninguno de los dos modulos sepa del otro. Eso es el ciclo cerrandose.
 *
 * **Tres cosas que esta operacion tiene y ninguna otra del modulo:**
 *
 * **1. No abre transaccion, y es deliberado.** Si la abriera, la de
 * `RegisterConsumableIntake` se uniria a ella y el `AFTER_COMMIT` que escribe el
 * asiento no se dispararia hasta despues --o, peor, dentro de una transaccion que
 * el handler no puede ver--. Todo lo que toca la base de datos va por
 * [ReceiptSteps].
 *
 * **2. Valida entero antes de tocar nada.** Cantidades, propietarios y destinos se
 * comprueban de una pasada **antes** de la primera entrada, porque el cierre no
 * cabe en una transaccion: un fallo a mitad no se puede deshacer, asi que lo unico
 * que queda es no empezar.
 *
 * **3. Reclama la linea antes de darle entrada**, con un `UPDATE` condicional que
 * devuelve una fila o ninguna. Es la respuesta a «cerrar dos veces una compra no
 * puede dar entrada dos veces al mismo consumible», y el orden es una eleccion con
 * su alternativa descartada: al reves --dar entrada y despues marcar-- dos
 * peticiones simultaneas darian las dos su entrada antes de que ninguna marcase,
 * que es exactamente el caso que hay que impedir. El precio es que un fallo de
 * infraestructura entre las dos cosas deje una linea marcada sin entrada, que es
 * **visible en la pantalla y corregible a mano**, mientras que una entrada
 * duplicada es un numero que nadie puede distinguir de uno real. Es la regla de la
 * ADR-011 --«hay que elegir cual falla mejor»-- resuelta en el sentido contrario y
 * a proposito: alli repetir era barato porque un correo repetido se nota; aqui
 * repetir crea existencias fantasma en la despensa.
 */
@Service
class ReceivePurchase(
    private val steps: ReceiptSteps,
    private val intake: RegisterConsumableIntake,
    private val clock: Clock,
) {

    fun handle(session: SessionClaims, purchaseId: UUID, command: ReceiptCommand): PurchaseDetail {
        val purchase = steps.purchase(purchaseId) ?: throw ResourceNotFound("Esa compra no existe")
        if (!purchase.isOpen) {
            throw BusinessRuleViolation(ErrorCode.PURCHASE_NOT_OPEN, "Esa compra ya se cerró")
        }

        val lines = steps.lines(purchaseId)
        if (lines.isEmpty()) {
            throw BusinessRuleViolation(ErrorCode.PURCHASE_EMPTY, "Esa compra no tiene nada que recibir")
        }

        val said = command.lines.associateBy { it.itemId }
        val destinations = lines
            .filter { it.item.status == ItemStatus.IN_PURCHASE && it.item.isStockable }
            .associate { it.item.id to resolve(session, it, said[it.item.id]) }

        val now = clock.instant()
        for (line in lines) {
            if (line.item.status != ItemStatus.IN_PURCHASE) continue

            // Reclamar primero. Si otra peticion se la llevo, esta no hace nada
            // con ella --ni entrada, ni nada.
            if (!steps.claim(line.item.id, now, session.memberId)) continue

            // Una linea de texto suelto se compra y ahi acaba: inventarle una
            // categoria y una unidad para poder darle entrada seria decidir por el
            // hogar. Quien la quiera en el inventario le asigna un articulo antes.
            val destination = destinations[line.item.id] ?: continue

            val result = intake.handle(
                session,
                IntakeCommand(
                    articleId = line.item.articleId,
                    newArticle = null,
                    ownerId = destination.ownerId,
                    location = destination.location,
                    quantity = destination.quantity,
                ),
            )
            steps.recordIntake(line.item.id, result.view.asset.id, now)
        }

        steps.close(purchaseId, now, session.memberId)
        return PurchaseDetail(
            steps.purchase(purchaseId) ?: error("La compra recién cerrada no se puede leer"),
            steps.lines(purchaseId),
        )
    }

    /**
     * Cuanto entro, de quien es y donde va, con sus tres salidas por omision.
     *
     * **Es la decision de producto de este hito**, y no un detalle de mapeo:
     * `RegisterConsumableIntake` exige propietario y admite ubicacion, y la lista
     * de la compra dice «hace falta arroz» y no «de quien es y en que balda va».
     * Asi que **eso se decide al recibir**, que es cuando alguien tiene la bolsa
     * en la mano y puede contestar.
     *
     * - **Cuanto**: lo que diga quien recibe, o lo que la linea apuntaba. Si no
     *   hay ninguna de las dos cosas, la linea no puede entrar y se dice antes de
     *   empezar, no a mitad.
     * - **De quien**: quien recibe la compra. Es la respuesta correcta en una casa
     *   --lo que se compra es de quien lo compra salvo que se diga-- y ademas la
     *   unica que no obliga a rellenar un campo por linea.
     * - **Donde**: **donde ese articulo ya vive**, si vive en un solo sitio. No es
     *   una comodidad: `RegisterConsumableIntake` busca la existencia por articulo
     *   **y ubicacion**, asi que dar entrada sin ubicacion cuando el arroz esta en
     *   la despensa **crea una segunda fila** en lugar de sumar sobre la que hay,
     *   y la despensa acabaria con dos arroces. Con dos sitios o con ninguno
     *   decide quien recibe.
     */
    private fun resolve(session: SessionClaims, line: ShoppingListEntry, said: ReceiptLine?): Destination {
        val quantity = said?.quantity ?: line.item.quantity
        if (quantity == null || quantity.signum() <= 0) {
            throw ValidationFailure(
                mapOf("lines" to "«${line.label}» necesita una cantidad mayor que cero para entrar en el inventario"),
            )
        }

        val ownerId = said?.ownerId ?: session.memberId
        if (!steps.isActiveMember(ownerId)) {
            throw ValidationFailure(mapOf("ownerId" to "esa persona no es miembro activo del hogar"))
        }

        val location = when (val locationId = said?.locationId) {
            null -> line.item.articleId?.let { steps.soleLocationOf(it) }
            else -> {
                if (!steps.isOwnLocation(locationId)) {
                    throw ValidationFailure(mapOf("locationId" to "esa ubicación no es de este hogar"))
                }
                AssetLocation(LocationRefKind.LOCATION, locationId)
            }
        }

        return Destination(quantity, ownerId, location)
    }

    private data class Destination(
        val quantity: BigDecimal,
        val ownerId: UUID,
        val location: AssetLocation?,
    )
}

/**
 * Donde se puede comprar.
 *
 * **Cuelga del prefijo de Compras y no del de Proveedores**, y esa es la mitad
 * visible de la decision de este hito: un hogar con Compras encendido y
 * Proveedores apagado recibe `200` con la lista vacia en lugar del `403` que le
 * daria la ruta del otro modulo. Que la degradacion sea del servidor y no del
 * cliente es lo que impide que cada consumidor futuro tenga que acordarse de
 * ella.
 *
 * No abre transaccion: la abre el directorio del modulo dueno, que es quien sabe
 * lo que va a consultar.
 */
@Service
class ListPurchasingSuppliers(private val masterData: MasterData) {

    fun handle(query: String?): List<MasterDataEntry> =
        masterData.search(SUPPLIERS, query?.trim()?.takeIf { it.isNotEmpty() }, SUPPLIER_CHOICES)
}
