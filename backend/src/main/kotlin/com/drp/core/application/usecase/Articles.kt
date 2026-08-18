package com.drp.core.application.usecase

import com.drp.core.application.PhotoFileResolver
import com.drp.core.application.event.CoreEvents
import com.drp.core.application.port.ArticleFilter
import com.drp.core.application.port.ArticleRepository
import com.drp.core.application.port.CategoryRepository
import com.drp.platform.page.Page
import com.drp.platform.page.Pagination
import com.drp.core.application.port.SessionClaims
import com.drp.platform.error.BusinessRuleViolation
import com.drp.platform.error.ErrorCode
import com.drp.platform.error.ResourceNotFound
import com.drp.core.domain.catalog.Article
import com.drp.core.domain.catalog.MeasurementUnit
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.util.UUID

/**
 * Las cinco operaciones del catalogo de articulos.
 *
 * Dos reglas dependen de cuantas existencias vivas le quedan al articulo, y las
 * dos son de este fichero: la `unit` **deja de ser modificable** en cuanto hay
 * cantidad contada en ella, y el articulo no se puede retirar mientras quede
 * algo.
 */

data class ArticleCommand(
    val name: String,
    val categoryId: UUID,
    val unit: MeasurementUnit,
    val brand: String? = null,
    val model: String? = null,
    val barcode: String? = null,
    val packSize: BigDecimal? = null,
    val photoUrl: String? = null,
    val photoFileId: UUID? = null,
    val notes: String? = null,
)

data class ArticlePatch(
    val name: Patch<String> = Patch.Absent,
    val categoryId: Patch<UUID> = Patch.Absent,
    val unit: Patch<MeasurementUnit> = Patch.Absent,
    val brand: Patch<String?> = Patch.Absent,
    val model: Patch<String?> = Patch.Absent,
    val barcode: Patch<String?> = Patch.Absent,
    val packSize: Patch<BigDecimal?> = Patch.Absent,
    val photoUrl: Patch<String?> = Patch.Absent,
    val photoFileId: Patch<UUID?> = Patch.Absent,
    val notes: Patch<String?> = Patch.Absent,
)

/**
 * Un articulo con el nombre de su categoria ya resuelto.
 *
 * El contrato devuelve `categoryId` **y** `category`, y esa resolucion va en el
 * caso de uso y no en el controlador: pintar un listado de cien articulos sin
 * ella obligaria al cliente a pedirse el catalogo entero para traducir cien
 * identificadores.
 */
data class ArticleView(val article: Article, val categoryName: String?)

@Service
class ListArticles(
    private val articles: ArticleRepository,
    private val categories: CategoryRepository,
) {

    @Transactional(readOnly = true)
    fun handle(filter: ArticleFilter, pagination: Pagination): Page<ArticleView> {
        val page = articles.list(filter, pagination)

        // Una sola lectura por categoria distinta, no una por articulo: un
        // listado de cien articulos de cinco categorias hace cinco consultas.
        val names = page.items.map { it.categoryId }.distinct()
            .associateWith { categories.findById(it)?.name }

        return Page(
            page.items.map { ArticleView(it, names[it.categoryId]) },
            page.page,
            page.size,
            page.total,
        )
    }
}

@Service
class GetArticle(
    private val articles: ArticleRepository,
    private val categories: CategoryRepository,
) {

    @Transactional(readOnly = true)
    fun handle(articleId: UUID): ArticleView {
        val article = articles.findById(articleId) ?: throw ResourceNotFound("Artículo no encontrado")
        return ArticleView(article, categories.findById(article.categoryId)?.name)
    }
}

@Service
class CreateArticle(
    private val articles: ArticleRepository,
    private val categories: CategoryRepository,
    private val files: PhotoFileResolver,
    private val events: CoreEvents,
    private val clock: Clock,
) {

    @Transactional
    fun handle(session: SessionClaims, command: ArticleCommand): ArticleView {
        val created = create(session, command)
        events.articleCreated(created.id, created.name, created.categoryId, created.unit.name)
        return ArticleView(created, categories.findById(created.categoryId)?.name)
    }

    /**
     * Crea sin publicar el evento, para que `RegisterConsumableIntake` pueda
     * hacerlo dentro de su propia orquestacion: esa operacion crea el articulo si
     * hace falta y publica `ArticleCreated` **junto** con el `AssetCreated` o el
     * `AssetQuantityChanged` que le corresponda.
     */
    @Transactional
    fun create(session: SessionClaims, command: ArticleCommand): Article {
        requireLiveCategory(categories, command.categoryId)
        requireNameAvailable(articles, command.name, exceptId = null)
        command.barcode?.let { requireBarcodeAvailable(articles, it, exceptId = null) }
        files.requireAttachable(command.photoFileId)

        val now = clock.instant()
        return articles.save(
            Article(
                id = UUID.randomUUID(),
                name = command.name.trim(),
                categoryId = command.categoryId,
                unit = command.unit,
                brand = command.brand,
                model = command.model,
                barcode = command.barcode?.trim()?.takeIf { it.isNotEmpty() },
                packSize = command.packSize,
                photoUrl = command.photoUrl,
                photoFileId = command.photoFileId,
                notes = command.notes,
                createdAt = now,
                updatedAt = now,
                retiredAt = null,
                createdBy = session.memberId,
                updatedBy = session.memberId,
            ),
        )
    }
}

@Service
class UpdateArticle(
    private val articles: ArticleRepository,
    private val categories: CategoryRepository,
    private val files: PhotoFileResolver,
    private val clock: Clock,
) {

    @Transactional
    fun handle(session: SessionClaims, articleId: UUID, patch: ArticlePatch): ArticleView {
        val current = articles.findById(articleId) ?: throw ResourceNotFound("Artículo no encontrado")

        val newUnit = patch.unit.orKeep(current.unit)
        if (newUnit != current.unit && articles.countLiveStockItems(articleId) > 0) {
            // Cambiar la unidad reinterpretaria en silencio toda la cantidad ya
            // contada: 500 gramos pasarian a ser 500 kilos sin que nadie tocara
            // el numero. Con existencias vivas no se admite.
            throw BusinessRuleViolation(
                ErrorCode.ARTICLE_UNIT_IMMUTABLE,
                "El artículo ya tiene existencias contadas en su unidad actual",
            )
        }

        val newName = patch.name.orKeep(current.name)
        if (newName != current.name) requireNameAvailable(articles, newName, exceptId = articleId)

        val newCategoryId = patch.categoryId.orKeep(current.categoryId)
        if (newCategoryId != current.categoryId) requireLiveCategory(categories, newCategoryId)

        val newBarcode = patch.barcode.orKeep(current.barcode)?.trim()?.takeIf { it.isNotEmpty() }
        if (newBarcode != null && newBarcode != current.barcode) {
            requireBarcodeAvailable(articles, newBarcode, exceptId = articleId)
        }

        if (patch.photoFileId is Patch.Set) {
            files.requireAttachable(patch.photoFileId.value, alreadyHeld = current.photoFileId)
        }

        val saved = articles.save(
            current.copy(
                name = newName.trim(),
                categoryId = newCategoryId,
                unit = newUnit,
                brand = patch.brand.orKeep(current.brand),
                model = patch.model.orKeep(current.model),
                barcode = newBarcode,
                packSize = patch.packSize.orKeep(current.packSize),
                photoUrl = patch.photoUrl.orKeep(current.photoUrl),
                photoFileId = patch.photoFileId.orKeep(current.photoFileId),
                notes = patch.notes.orKeep(current.notes),
                updatedAt = clock.instant(),
                updatedBy = session.memberId,
            ),
        )

        return ArticleView(saved, categories.findById(saved.categoryId)?.name)
    }
}

/**
 * Retirada **logica**, nunca borrado: las existencias dadas de baja siguen
 * apuntando al articulo, asi que la fila tiene que permanecer. Deja de ofrecerse
 * en el alta y no admite entradas nuevas.
 */
@Service
class RetireArticle(
    private val articles: ArticleRepository,
    private val clock: Clock,
) {

    @Transactional
    fun handle(session: SessionClaims, articleId: UUID) {
        val article = articles.findById(articleId) ?: throw ResourceNotFound("Artículo no encontrado")
        if (!article.isLive) return

        if (articles.countLiveStockItems(articleId) > 0) {
            throw BusinessRuleViolation(
                ErrorCode.ARTICLE_HAS_EXISTENCES,
                "Al artículo todavía le quedan existencias vivas",
            )
        }

        val now = clock.instant()
        articles.save(article.copy(retiredAt = now, updatedAt = now, updatedBy = session.memberId))
    }
}

/**
 * La categoria se **resuelve** contra su repositorio antes de insertar, y ademas
 * tiene que estar vigente.
 *
 * Resolverla no es redundante con la clave ajena compuesta: la comprobacion de
 * una clave ajena no pasa por RLS, asi que la categoria de otro hogar es
 * invisible a un `SELECT` y aun asi se puede referenciar. Sin esta linea seria un
 * 500 --o algo peor-- en lugar del 404 que declara el contrato.
 */
internal fun requireLiveCategory(categories: CategoryRepository, categoryId: UUID) {
    val category = categories.findById(categoryId) ?: throw ResourceNotFound("Categoría no encontrada")
    if (!category.isLive) {
        throw ResourceNotFound("La categoría está retirada y ya no se ofrece para clasificar")
    }
}

private fun requireNameAvailable(articles: ArticleRepository, name: String, exceptId: UUID?) {
    val clash = articles.findLiveByName(name.trim()) ?: return
    if (clash.id == exceptId) return

    throw BusinessRuleViolation(ErrorCode.ARTICLE_DUPLICATE, "Ya existe un artículo vigente con ese nombre")
}

private fun requireBarcodeAvailable(articles: ArticleRepository, barcode: String, exceptId: UUID?) {
    val clash = articles.findLiveByBarcode(barcode.trim()) ?: return
    if (clash.id == exceptId) return

    throw BusinessRuleViolation(ErrorCode.ARTICLE_DUPLICATE, "Ya existe un artículo vigente con ese código de barras")
}
