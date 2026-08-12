package com.drp.application.usecase

import com.drp.application.PhotoFileResolver
import com.drp.application.event.CoreEvents
import com.drp.application.event.EventLocation
import com.drp.application.event.LocationKind
import com.drp.application.event.QuantityChangeReason
import com.drp.application.port.ArticleRepository
import com.drp.application.port.AssetFilter
import com.drp.application.port.AssetRepository
import com.drp.application.port.CategoryRepository
import com.drp.application.port.HierarchyLock
import com.drp.application.port.HouseholdMemberRepository
import com.drp.application.port.LocationRepository
import com.drp.application.port.Page
import com.drp.application.port.Pagination
import com.drp.application.port.SessionClaims
import com.drp.domain.BusinessRuleViolation
import com.drp.domain.ErrorCode
import com.drp.domain.ResourceNotFound
import com.drp.domain.ValidationFailure
import com.drp.domain.catalog.MeasurementUnit
import com.drp.domain.inventory.Asset
import com.drp.domain.inventory.AssetLocation
import com.drp.domain.inventory.AssetStatus
import com.drp.domain.inventory.AssetType
import com.drp.domain.inventory.CapacityType
import com.drp.domain.inventory.LocationRefKind
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

/**
 * Las ocho operaciones de assets, que son mas casos de uso que endpoints.
 *
 * El contrato no expone `MoveAsset` ni `AdjustAssetQuantity`: los dos entran por
 * `PATCH /assets/{id}`, que despacha segun lo que traiga el cuerpo y publica un
 * evento distinto en cada caso. De ahi que [UpdateAsset] no sea un CRUD.
 *
 * Aqui viven las **tres validaciones que la base de datos no puede garantizar**:
 *
 * 1. El anti-ciclo de la jerarquia de assets, con el mismo helper que ubicaciones.
 * 2. Que la ubicacion destino sea `DURABLE` cuando es un asset: una estanteria
 *    contiene cosas y un paquete de harina no.
 * 3. El aviso de capacidad, que **advierte sin bloquear**.
 */

/** Un aviso que acompana a una operacion con exito. No es un error. */
data class OperationWarning(val code: String, val message: String)

/**
 * Un asset con lo heredado ya resuelto y los avisos de la operacion.
 *
 * El nombre, la categoria y la unidad **no se guardan por duplicado**: cuando el
 * asset tiene articulo son los suyos, y se resuelven al leer.
 */
data class AssetView(
    val asset: Asset,
    val name: String,
    val categoryId: UUID?,
    val categoryName: String?,
    val unit: MeasurementUnit?,
    val warnings: List<OperationWarning> = emptyList(),
)

data class CreateAssetCommand(
    val name: String?,
    val type: AssetType,
    val categoryId: UUID?,
    val articleId: UUID?,
    val ownerId: UUID?,
    val location: AssetLocation?,
    val serialNumber: String?,
    val acquiredOn: LocalDate?,
    val photoUrl: String?,
    val photoFileId: UUID?,
    val notes: String?,
)

data class IntakeCommand(
    val articleId: UUID?,
    val newArticle: ArticleCommand?,
    val ownerId: UUID,
    val location: AssetLocation?,
    val quantity: BigDecimal,
)

data class AssetPatch(
    val name: Patch<String> = Patch.Absent,
    val categoryId: Patch<UUID> = Patch.Absent,
    val articleId: Patch<UUID> = Patch.Absent,
    val ownerId: Patch<UUID?> = Patch.Absent,
    val location: Patch<AssetLocation?> = Patch.Absent,
    val quantity: Patch<BigDecimal> = Patch.Absent,
    val photoUrl: Patch<String?> = Patch.Absent,
    val photoFileId: Patch<UUID?> = Patch.Absent,
    val notes: Patch<String?> = Patch.Absent,
)

/**
 * Resuelve lo heredado del articulo y la categoria.
 *
 * Se comparte entre las ocho operaciones para que ninguna se invente su propia
 * forma de resolver: si una devolviese el `name` de la fila y otra el del
 * articulo, el mismo asset se llamaria distinto segun por donde se pidiera.
 */
@Service
class AssetViewFactory(
    private val articles: ArticleRepository,
    private val categories: CategoryRepository,
) {

    fun of(asset: Asset, warnings: List<OperationWarning> = emptyList()): AssetView {
        val article = asset.articleId?.let { articles.findById(it) }
        val categoryId = article?.categoryId ?: asset.categoryId

        return AssetView(
            asset = asset,
            name = article?.name ?: asset.name.orEmpty(),
            categoryId = categoryId,
            categoryName = categoryId?.let { categories.findById(it)?.name },
            unit = article?.unit,
            warnings = warnings,
        )
    }

    fun ofAll(page: Page<Asset>): Page<AssetView> =
        Page(page.items.map { of(it) }, page.page, page.size, page.total)
}

@Service
class ListAssets(
    private val assets: AssetRepository,
    private val views: AssetViewFactory,
) {

    @Transactional(readOnly = true)
    fun handle(filter: AssetFilter, pagination: Pagination): Page<AssetView> =
        views.ofAll(assets.list(filter, pagination))
}

@Service
class GetAsset(
    private val assets: AssetRepository,
    private val views: AssetViewFactory,
) {

    @Transactional(readOnly = true)
    fun handle(assetId: UUID): AssetView =
        views.of(assets.findById(assetId) ?: throw ResourceNotFound("Asset no encontrado"))
}

@Service
class CreateAsset(
    private val assets: AssetRepository,
    private val references: AssetReferenceResolver,
    private val capacity: CapacityAdvisor,
    private val views: AssetViewFactory,
    private val events: CoreEvents,
    private val clock: Clock,
) {

    @Transactional
    fun handle(session: SessionClaims, command: CreateAssetCommand): AssetView {
        // Un consumible no entra por aqui: su alta es una ENTRADA, que resuelve
        // la existencia y suma sobre ella. Crearlo por esta via se saltaria el
        // indice de existencias y dejaria dos filas del mismo articulo en la
        // misma ubicacion.
        if (command.type != AssetType.DURABLE) {
            throw ValidationFailure(
                mapOf("type" to "un CONSUMABLE se da de alta con POST /assets/intake"),
            )
        }
        if (command.articleId == null && (command.name.isNullOrBlank() || command.categoryId == null)) {
            throw ValidationFailure(
                mapOf("name" to "sin articleId son obligatorios name y categoryId"),
            )
        }

        references.resolveAll(command.articleId, command.categoryId, command.ownerId, command.photoFileId)
        command.location?.let { references.requireUsableAsContainer(it) }

        val now = clock.instant()
        val created = assets.save(
            Asset(
                id = UUID.randomUUID(),
                type = AssetType.DURABLE,
                articleId = command.articleId,
                // Cuando hay articulo, el nombre y la categoria son los suyos y
                // no se copian: se resuelven al leer.
                name = command.name?.trim()?.takeIf { command.articleId == null },
                categoryId = command.categoryId?.takeIf { command.articleId == null },
                ownerId = command.ownerId,
                location = command.location,
                status = AssetStatus.AVAILABLE,
                quantity = null,
                serialNumber = command.serialNumber,
                acquiredOn = command.acquiredOn,
                photoUrl = command.photoUrl,
                photoFileId = command.photoFileId,
                notes = command.notes,
                createdAt = now,
                updatedAt = now,
                createdBy = session.memberId,
                updatedBy = session.memberId,
            ),
        )

        events.assetCreated(
            created.id,
            created.type.name,
            created.articleId,
            created.categoryId,
            created.location?.asEvent(),
        )

        return views.of(created, capacity.adviseOn(command.location))
    }
}

/**
 * Traer otro paquete de azucar **no da de alta nada nuevo**: resuelve el
 * articulo --creandolo si hace falta--, busca la existencia de ese articulo en
 * esa ubicacion y **suma** sobre ella; si no hay ninguna, la crea.
 *
 * Por eso responde `200` cuando sumo y `201` cuando creo, y por eso publica
 * eventos distintos en cada caso.
 */
@Service
class RegisterConsumableIntake(
    private val assets: AssetRepository,
    private val articles: ArticleRepository,
    private val createArticle: CreateArticle,
    private val references: AssetReferenceResolver,
    private val capacity: CapacityAdvisor,
    private val views: AssetViewFactory,
    private val events: CoreEvents,
    private val clock: Clock,
) {

    data class Result(val view: AssetView, val created: Boolean)

    @Transactional
    fun handle(session: SessionClaims, command: IntakeCommand): Result {
        if (command.quantity <= BigDecimal.ZERO) {
            throw BusinessRuleViolation(
                ErrorCode.INTAKE_QUANTITY_NOT_POSITIVE,
                "La cantidad que entra tiene que ser mayor que cero",
            )
        }

        references.requireMember(command.ownerId)
        command.location?.let { references.requireUsableAsContainer(it) }

        val article = when {
            command.articleId != null -> articles.findById(command.articleId)
                ?.takeIf { it.isLive }
                ?: throw ResourceNotFound("Artículo no encontrado")

            command.newArticle != null -> createArticle.create(session, command.newArticle)
                .also { events.articleCreated(it.id, it.name, it.categoryId, it.unit.name) }

            else -> throw ValidationFailure(mapOf("articleId" to "hace falta articleId o article"))
        }

        val now = clock.instant()
        val existing = assets.findLiveStockItem(article.id, command.location)

        if (existing != null) {
            val before = existing.quantity ?: BigDecimal.ZERO
            val after = before + command.quantity
            val updated = assets.save(
                existing.copy(quantity = after, updatedAt = now, updatedBy = session.memberId),
            )
            events.assetQuantityChanged(updated.id, before, after, QuantityChangeReason.INTAKE)
            return Result(views.of(updated), created = false)
        }

        val created = assets.save(
            Asset(
                id = UUID.randomUUID(),
                type = AssetType.CONSUMABLE,
                articleId = article.id,
                // Un consumible siempre tiene articulo, asi que nombre y
                // categoria son siempre los del articulo.
                name = null,
                categoryId = null,
                ownerId = command.ownerId,
                location = command.location,
                status = AssetStatus.AVAILABLE,
                quantity = command.quantity,
                serialNumber = null,
                acquiredOn = null,
                photoUrl = null,
                photoFileId = null,
                notes = null,
                createdAt = now,
                updatedAt = now,
                createdBy = session.memberId,
                updatedBy = session.memberId,
            ),
        )

        events.assetCreated(created.id, created.type.name, created.articleId, null, created.location?.asEvent())
        return Result(views.of(created, capacity.adviseOn(command.location)), created = true)
    }
}

/**
 * El `PATCH`, que **no es un CRUD**: despacha a tres cosas distintas segun lo que
 * traiga el cuerpo, y cada una publica lo suyo.
 *
 * - Cambiar `location` es `MoveAsset` → `AssetMoved`, mas
 *   `AssetHierarchyChanged` si cambia el asset contenedor.
 * - Cambiar `quantity` es `AdjustAssetQuantity` → `AssetQuantityChanged`.
 * - Lo demas son correcciones de ficha, que no publican nada.
 */
@Service
class UpdateAsset(
    private val assets: AssetRepository,
    private val references: AssetReferenceResolver,
    private val hierarchyLock: HierarchyLock,
    private val capacity: CapacityAdvisor,
    private val views: AssetViewFactory,
    private val events: CoreEvents,
    private val clock: Clock,
) {

    @Transactional
    fun handle(session: SessionClaims, assetId: UUID, patch: AssetPatch): AssetView {
        val current = assets.findById(assetId) ?: throw ResourceNotFound("Asset no encontrado")

        val moves = patch.location is Patch.Set && patch.location.value != current.location
        if (moves) hierarchyLock.acquire()

        val newLocation = patch.location.orKeep(current.location)
        if (moves && newLocation != null) {
            references.requireUsableAsContainer(newLocation)
            if (newLocation.kind == LocationRefKind.ASSET) {
                requireNoAssetCycle(assetId, newLocation.id, assets::ancestorsOf)
            }
            // Mover una existencia a una ubicacion que ya tiene otra viva del
            // mismo articulo NO es un movimiento: es una fusion, y la fusion
            // decide que ubicacion y que propietario sobreviven. Eso lo elige el
            // usuario, asi que aqui se rechaza y se le manda a MergeStockItems.
            if (current.isConsumable && current.articleId != null) {
                assets.findLiveStockItem(current.articleId, newLocation)?.takeIf { it.id != assetId }?.let {
                    throw BusinessRuleViolation(
                        ErrorCode.EXISTENCE_ALREADY_IN_LOCATION,
                        "Esa ubicación ya tiene una existencia viva del mismo artículo; usa la fusión",
                    )
                }
            }
        }

        val newQuantity = resolveQuantity(current, patch)
        val newArticleId = resolveArticleId(current, patch)
        val newOwnerId = patch.ownerId.orKeep(current.ownerId)
        if (patch.ownerId is Patch.Set && newOwnerId != null) references.requireMember(newOwnerId)
        if (patch.photoFileId is Patch.Set) references.requirePhoto(patch.photoFileId.value)

        // El nombre y la categoria propios solo valen en un asset SIN articulo:
        // si lo tiene, se cambian en el articulo. Guardarlos aqui los duplicaria
        // y dejaria dos verdades para el mismo dato.
        val keepsOwnIdentity = newArticleId == null
        val newName = patch.name.orKeep(current.name ?: "").takeIf { keepsOwnIdentity && it.isNotBlank() }
        val newCategoryId = (patch.categoryId.orKeep(current.categoryId)).takeIf { keepsOwnIdentity }
        if (patch.categoryId is Patch.Set && keepsOwnIdentity) {
            requireLiveCategory(references.categories, patch.categoryId.value)
        }

        val updated = assets.save(
            current.copy(
                articleId = newArticleId,
                name = newName?.trim(),
                categoryId = newCategoryId,
                ownerId = newOwnerId,
                location = newLocation,
                quantity = newQuantity,
                photoUrl = patch.photoUrl.orKeep(current.photoUrl),
                photoFileId = patch.photoFileId.orKeep(current.photoFileId),
                notes = patch.notes.orKeep(current.notes),
                updatedAt = clock.instant(),
                updatedBy = session.memberId,
            ),
        )

        if (moves) publishMove(current, updated)
        if (newQuantity != null && current.quantity != null && newQuantity.compareTo(current.quantity) != 0) {
            events.assetQuantityChanged(
                updated.id,
                current.quantity,
                newQuantity,
                QuantityChangeReason.ADJUSTMENT,
            )
        }

        return views.of(updated, if (moves) capacity.adviseOn(newLocation) else emptyList())
    }

    private fun resolveQuantity(current: Asset, patch: AssetPatch): BigDecimal? {
        if (patch.quantity !is Patch.Set) return current.quantity

        if (current.isDurable) {
            throw BusinessRuleViolation(
                ErrorCode.ASSET_QUANTITY_NOT_APPLICABLE,
                "Un asset DURABLE no lleva cantidad: su unidad implícita es siempre 1",
            )
        }
        if (patch.quantity.value < BigDecimal.ZERO) {
            throw BusinessRuleViolation(ErrorCode.ASSET_QUANTITY_NEGATIVE, "La cantidad no puede ser negativa")
        }
        return patch.quantity.value
    }

    /** Solo para **asignar** articulo a un `DURABLE` que aun no lo tiene. */
    private fun resolveArticleId(current: Asset, patch: AssetPatch): UUID? {
        if (patch.articleId !is Patch.Set) return current.articleId
        if (current.articleId != null && current.articleId != patch.articleId.value) {
            throw ValidationFailure(mapOf("articleId" to "el artículo de un asset no se sustituye"))
        }
        references.requireLiveArticle(patch.articleId.value)
        return patch.articleId.value
    }

    /**
     * `AssetMoved` siempre que cambia el sitio; **ademas** `AssetHierarchyChanged`
     * cuando cambia el asset que lo contiene. Son dos preguntas distintas --donde
     * esta algo y de que forma parte-- y hay modulos que solo escuchan una.
     */
    private fun publishMove(before: Asset, after: Asset) {
        events.assetMoved(after.id, before.location?.asEvent(), after.location?.asEvent())

        val previousParent = before.location?.assetId
        val newParent = after.location?.assetId
        if (previousParent != newParent) {
            events.assetHierarchyChanged(after.id, previousParent, newParent)
        }
    }
}

/**
 * Juntar dos existencias del mismo articulo que ya existen por separado.
 *
 * **No es un movimiento**: la fusion decide que ubicacion y que propietario
 * sobreviven, y eso lo elige el usuario. El destino se queda con la suma y
 * conserva lo suyo; el origen queda a cero y dado de baja.
 */
@Service
class MergeStockItems(
    private val assets: AssetRepository,
    private val views: AssetViewFactory,
    private val events: CoreEvents,
    private val clock: Clock,
) {

    @Transactional
    fun handle(session: SessionClaims, sourceId: UUID, targetId: UUID): AssetView {
        if (sourceId == targetId) {
            throw BusinessRuleViolation(ErrorCode.MERGE_SAME_ASSET, "Origen y destino son el mismo asset")
        }

        val source = assets.findById(sourceId) ?: throw ResourceNotFound("Asset no encontrado")
        val target = assets.findById(targetId) ?: throw ResourceNotFound("Asset de destino no encontrado")

        if (!source.isConsumable || !target.isConsumable) {
            throw BusinessRuleViolation(ErrorCode.MERGE_NOT_CONSUMABLE, "Solo se fusionan existencias de consumible")
        }
        if (!source.isLive || !target.isLive) {
            throw BusinessRuleViolation(ErrorCode.MERGE_ASSET_DEACTIVATED, "Alguna de las dos está dada de baja")
        }
        if (source.articleId != target.articleId) {
            throw BusinessRuleViolation(ErrorCode.MERGE_ARTICLE_MISMATCH, "Las dos existencias no son del mismo artículo")
        }

        val now = clock.instant()
        val moved = source.quantity ?: BigDecimal.ZERO
        val before = target.quantity ?: BigDecimal.ZERO
        val after = before + moved

        val merged = assets.save(target.copy(quantity = after, updatedAt = now, updatedBy = session.memberId))
        assets.save(
            source.copy(
                quantity = BigDecimal.ZERO,
                status = AssetStatus.DECOMMISSIONED,
                updatedAt = now,
                updatedBy = session.memberId,
            ),
        )

        // Los dos eventos que ya existen, correlacionados por payload. Asi un
        // modulo que solo escuche cambios de cantidad no se pierde el del
        // destino, y quien necesite saber que las existencias se **mudaron** --en
        // vez de haberse perdido-- lo distingue por la referencia cruzada.
        events.assetQuantityChanged(merged.id, before, after, QuantityChangeReason.MERGE, mergedFromAssetId = sourceId)
        events.assetDeactivated(sourceId, mergedIntoAssetId = merged.id)

        return views.of(merged)
    }
}

/**
 * Baja siempre **logica**: nada se borra, para no perder el historial.
 *
 * Si es una existencia con cantidad pendiente, la baja la lleva a cero --lo que
 * quedaba se da por perdido-- en lugar de dejar un resto colgando en una fila
 * muerta que ninguna suma de existencias volveria a mirar.
 */
@Service
class DecommissionAsset(
    private val assets: AssetRepository,
    private val events: CoreEvents,
    private val clock: Clock,
) {

    @Transactional
    fun handle(session: SessionClaims, assetId: UUID) {
        val asset = assets.findById(assetId) ?: throw ResourceNotFound("Asset no encontrado")
        if (!asset.isLive) return

        if (assets.countChildren(assetId) > 0) {
            throw BusinessRuleViolation(ErrorCode.ASSET_HAS_CHILDREN, "El asset todavía tiene assets dentro")
        }
        if (asset.isLent || assets.hasOpenLoan(assetId)) {
            throw BusinessRuleViolation(ErrorCode.ASSET_HAS_ACTIVE_LOAN, "El asset tiene un préstamo abierto")
        }

        val now = clock.instant()
        val remaining = asset.quantity

        assets.save(
            asset.copy(
                quantity = remaining?.let { BigDecimal.ZERO },
                status = AssetStatus.DECOMMISSIONED,
                updatedAt = now,
                updatedBy = session.memberId,
            ),
        )

        // El orden importa: primero se cuenta que la cantidad se pierde y luego
        // que el asset causa baja. Al reves, un modulo veria el ajuste sobre algo
        // que para el ya no existe.
        if (remaining != null && remaining > BigDecimal.ZERO) {
            events.assetQuantityChanged(
                assetId,
                remaining,
                BigDecimal.ZERO,
                QuantityChangeReason.DECOMMISSION,
            )
        }
        events.assetDeactivated(assetId)
    }
}

/**
 * Resuelve **contra su repositorio** todas las referencias que llegan del
 * cliente, antes de insertar.
 *
 * No es redundante con las claves ajenas compuestas: la comprobacion de una clave
 * ajena **no pasa por RLS**, asi que una fila de otro hogar es invisible a un
 * `SELECT` y aun asi se puede referenciar. Sin resolver, un identificador ajeno
 * daria un 500 --o algo peor-- en vez del 404 que declara el contrato.
 */
@Service
class AssetReferenceResolver(
    private val assets: AssetRepository,
    private val articles: ArticleRepository,
    internal val categories: CategoryRepository,
    private val members: HouseholdMemberRepository,
    private val locations: LocationRepository,
    private val photos: PhotoFileResolver,
) {

    fun resolveAll(articleId: UUID?, categoryId: UUID?, ownerId: UUID?, photoFileId: UUID?) {
        articleId?.let { requireLiveArticle(it) }
        // La categoria propia solo se usa cuando no hay articulo; con articulo se
        // hereda la suya, asi que no hay nada que resolver.
        if (articleId == null) categoryId?.let { requireLiveCategory(categories, it) }
        ownerId?.let { requireMember(it) }
        photos.requireUsable(photoFileId)
    }

    fun requireLiveArticle(articleId: UUID) {
        val article = articles.findById(articleId) ?: throw ResourceNotFound("Artículo no encontrado")
        if (!article.isLive) throw ResourceNotFound("El artículo está retirado del catálogo")
    }

    fun requireMember(memberId: UUID) {
        members.findById(memberId) ?: throw ResourceNotFound("Usuario no encontrado")
    }

    fun requirePhoto(fileId: UUID?) = photos.requireUsable(fileId)

    /**
     * La segunda validacion del hito: **solo un `DURABLE` puede alojar otros
     * assets**. Una estanteria contiene cosas y un paquete de harina no.
     */
    fun requireUsableAsContainer(location: AssetLocation) {
        when (location.kind) {
            LocationRefKind.LOCATION ->
                locations.findById(location.id) ?: throw ResourceNotFound("Ubicación no encontrada")

            LocationRefKind.ASSET -> {
                val container = assets.findById(location.id) ?: throw ResourceNotFound("Asset contenedor no encontrado")
                if (!container.isDurable) {
                    throw BusinessRuleViolation(
                        ErrorCode.ASSET_LOCATION_CONFLICT,
                        "Solo un asset DURABLE puede contener otros assets",
                    )
                }
                if (!container.isLive) {
                    throw BusinessRuleViolation(
                        ErrorCode.ASSET_LOCATION_CONFLICT,
                        "El asset contenedor está dado de baja",
                    )
                }
            }
        }
    }
}

/**
 * La tercera validacion del hito, y la unica que **no rechaza nada**.
 *
 * Superar la capacidad declarada de una ubicacion advierte y deja pasar, porque
 * el sistema no sabe cuanto ocupa cada cosa --el asset no lleva peso ni volumen--
 * y bloquear con datos incompletos impediria guardar algo que si cabe.
 *
 * Por eso solo se calcula con capacidad de tipo `UNITS`: es lo unico que se puede
 * contar con certeza. Con `WEIGHT` o `VOLUME` no hay nada que sumar hasta que el
 * asset lleve esos datos, que es terreno del modulo Warehouse.
 */
@Service
class CapacityAdvisor(
    private val assets: AssetRepository,
    private val locations: LocationRepository,
) {

    fun adviseOn(location: AssetLocation?): List<OperationWarning> {
        if (location == null || location.kind != LocationRefKind.LOCATION) return emptyList()

        val capacity = locations.findById(location.id)?.capacity ?: return emptyList()
        if (capacity.type != CapacityType.UNITS) return emptyList()

        val occupied = assets.countLiveIn(location)
        if (BigDecimal.valueOf(occupied) <= capacity.max) return emptyList()

        return listOf(
            OperationWarning(
                code = "LOCATION_CAPACITY_EXCEEDED",
                message = "La ubicación declara un máximo de ${capacity.max.toPlainString()} " +
                    "${capacity.unit} y ya contiene $occupied",
            ),
        )
    }
}

/**
 * El anti-ciclo de la jerarquia de assets. Es el **mismo helper** que el de
 * ubicaciones, con otra forma de recorrer: la regla no se duplica.
 *
 * El codigo que devuelve es `LOCATION_CYCLE` y no uno propio porque el contrato
 * solo declara ese: para el cliente el problema es el mismo --el destino esta por
 * debajo del origen-- lo recorra por donde lo recorra.
 */
private fun requireNoAssetCycle(assetId: UUID, newContainerId: UUID, ancestorsOf: (UUID) -> List<UUID>) {
    if (newContainerId == assetId) {
        throw BusinessRuleViolation(ErrorCode.LOCATION_CYCLE, "Un asset no puede estar dentro de sí mismo")
    }
    if (assetId in ancestorsOf(newContainerId)) {
        throw BusinessRuleViolation(
            ErrorCode.LOCATION_CYCLE,
            "El asset de destino está dentro del que se mueve",
        )
    }
}

private fun AssetLocation.asEvent() = EventLocation(
    type = if (kind == LocationRefKind.ASSET) LocationKind.ASSET else LocationKind.LOCATION,
    id = id,
)
