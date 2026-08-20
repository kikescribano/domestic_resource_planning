package com.drp.core.adapter.http

import com.drp.core.application.port.ArticleFilter
import com.drp.core.application.port.SessionClaims
import com.drp.core.application.usecase.ArticleCommand
import com.drp.core.application.usecase.ArticlePatch
import com.drp.core.application.usecase.ArticleView
import com.drp.core.application.usecase.CreateArticle
import com.drp.core.application.usecase.CreateCategory
import com.drp.core.application.usecase.CreateTag
import com.drp.core.application.usecase.GetArticle
import com.drp.core.application.usecase.ListArticles
import com.drp.core.application.usecase.ListCategories
import com.drp.core.application.usecase.ListTags
import com.drp.core.application.usecase.RetireArticle
import com.drp.core.application.usecase.RetireCategory
import com.drp.core.application.usecase.RetireTag
import com.drp.core.application.usecase.UpdateArticle
import com.drp.core.application.usecase.UpdateCategory
import com.drp.core.application.usecase.UpdateTag
import com.drp.platform.page.Page
import com.drp.platform.page.PageResponse
import com.drp.platform.page.Pagination
import com.fasterxml.jackson.databind.JsonNode
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
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

/**
 * Los controladores del catalogo del Hito 2.
 *
 * Tan finos como los del enrolamiento y por el mismo motivo: traducen HTTP a un
 * caso de uso y su resultado a la forma que declara `openapi.yaml`, sin decidir
 * nada. **Ninguno recibe `householdId`**; sale del token.
 *
 * Van en su propio fichero y no en `Controllers.kt` porque este hito trae
 * veintitres operaciones: juntarlas todas daria un fichero que ya no se lee de
 * una sentada.
 */
@RestController
@RequestMapping("/api/v1/categories")
class CategoryController(
    private val listCategories: ListCategories,
    private val createCategory: CreateCategory,
    private val updateCategory: UpdateCategory,
    private val retireCategory: RetireCategory,
) {

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "false") includeRetired: Boolean,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): PageResponse<CategoryResponse> =
        PageResponse.of(listCategories.handle(includeRetired, Pagination(page, size)), CategoryResponse::of)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @AuthenticationPrincipal session: SessionClaims,
        @Valid @RequestBody input: CategoryInput,
    ): CategoryResponse = CategoryResponse.of(createCategory.handle(session, input.toCommand()))

    @PatchMapping("/{id}")
    fun update(
        @AuthenticationPrincipal session: SessionClaims,
        @PathVariable id: UUID,
        @Valid @RequestBody input: CategoryInput,
    ): CategoryResponse = CategoryResponse.of(updateCategory.handle(session, id, input.toCommand()))

    /** Retirada logica, no borrado: los assets y los articulos que la tenian la conservan. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun retire(
        @AuthenticationPrincipal session: SessionClaims,
        @PathVariable id: UUID,
    ) = retireCategory.handle(session, id)
}

/**
 * El catalogo de etiquetas: las mismas cuatro operaciones que el de categorias.
 *
 * **Poner y quitar etiquetas a un asset no esta aqui**: viaja en `tagIds` dentro
 * de `POST /assets` y `PATCH /assets/{id}`. Etiquetar es corregir la ficha de un
 * asset, no una operacion del catalogo, y una operacion propia habria sido una
 * segunda escritura sobre el inventario para hacer lo que el `PATCH` ya hace.
 */
@RestController
@RequestMapping("/api/v1/tags")
class TagController(
    private val listTags: ListTags,
    private val createTag: CreateTag,
    private val updateTag: UpdateTag,
    private val retireTag: RetireTag,
) {

    /** El `q` alimenta el autocompletado del campo de etiquetas de un asset. */
    @GetMapping
    fun list(
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "false") includeRetired: Boolean,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): PageResponse<TagResponse> =
        PageResponse.of(listTags.handle(includeRetired, q, Pagination(page, size)), TagResponse::of)

    /**
     * `201` si la creo y `200` si ya existia --viva, o **retirada y revivida**--.
     *
     * Es la misma forma que la entrada de un consumible, y por el mismo motivo:
     * quien la pide no tiene por que saber cual de las dos cosas va a pasar. El
     * indice unico de `tags` no es parcial por retirada, asi que sin esto crear
     * «Camping» teniendo una «camping» retirada seria un `409` sobre una fila que
     * el usuario no ve.
     */
    @PostMapping
    fun create(
        @AuthenticationPrincipal session: SessionClaims,
        @Valid @RequestBody input: TagInput,
    ): ResponseEntity<TagResponse> {
        val result = createTag.handle(session, input.name)
        return ResponseEntity
            .status(if (result.created) HttpStatus.CREATED else HttpStatus.OK)
            .body(TagResponse.of(result.tag))
    }

    @PatchMapping("/{id}")
    fun update(
        @AuthenticationPrincipal session: SessionClaims,
        @PathVariable id: UUID,
        @Valid @RequestBody input: TagInput,
    ): TagResponse = TagResponse.of(updateTag.handle(session, id, input.name))

    /** Retirada logica: los assets que la llevaban la conservan. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun retire(
        @AuthenticationPrincipal session: SessionClaims,
        @PathVariable id: UUID,
    ) = retireTag.handle(session, id)
}

@RestController
@RequestMapping("/api/v1/articles")
class ArticleController(
    private val listArticles: ListArticles,
    private val getArticle: GetArticle,
    private val createArticle: CreateArticle,
    private val updateArticle: UpdateArticle,
    private val retireArticle: RetireArticle,
    private val photoUrls: PhotoUrls,
) {

    /** El `q` alimenta el autocompletado del alta de consumibles. */
    @GetMapping
    fun list(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) categoryId: UUID?,
        @RequestParam(required = false) barcode: String?,
        @RequestParam(defaultValue = "false") includeRetired: Boolean,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): PageResponse<ArticleResponse> =
        listArticles.handle(ArticleFilter(q, categoryId, barcode, includeRetired), Pagination(page, size))
            .withThumbnails()

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): ArticleResponse = getArticle.handle(id).withThumbnail()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @AuthenticationPrincipal session: SessionClaims,
        @Valid @RequestBody input: ArticleInput,
    ): ArticleResponse = createArticle.handle(session, input.toCommand()).withThumbnail()

    @PatchMapping("/{id}")
    fun update(
        @AuthenticationPrincipal session: SessionClaims,
        @PathVariable id: UUID,
        @RequestBody body: JsonNode,
    ): ArticleResponse {
        val patch = JsonPatch(body)
        return updateArticle.handle(
            session,
            id,
            ArticlePatch(
                name = patch.requiredText("name"),
                categoryId = patch.requiredUuid("categoryId"),
                unit = patch.requiredEnum("unit"),
                brand = patch.text("brand"),
                model = patch.text("model"),
                barcode = patch.text("barcode"),
                packSize = patch.decimal("packSize"),
                unitWeightGrams = patch.decimal("unitWeightGrams"),
                unitVolumeMl = patch.decimal("unitVolumeMl"),
                photoUrl = patch.text("photoUrl"),
                photoFileId = patch.uuid("photoFileId"),
                notes = patch.text("notes"),
            ),
        ).withThumbnail()
    }

    /**
     * La miniatura firmada de cada fila, resuelta **de una vez** para toda la
     * pagina: un catalogo de cincuenta articulos con foto no puede ser cincuenta
     * consultas.
     */
    private fun Page<ArticleView>.withThumbnails(): PageResponse<ArticleResponse> {
        val photos = photoUrls.index(items.map { it.article.photoFileId })
        return PageResponse.of(this) { ArticleResponse.of(it, photos.thumbnail(it.article.photoFileId)) }
    }

    private fun ArticleView.withThumbnail() = ArticleResponse.of(this, photoUrls.thumbnail(article.photoFileId))

    /** Retirada logica: la fila permanece porque las existencias de baja la referencian. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun retire(
        @AuthenticationPrincipal session: SessionClaims,
        @PathVariable id: UUID,
    ) = retireArticle.handle(session, id)
}

private fun ArticleInput.toCommand() = ArticleCommand(
    name = name,
    categoryId = categoryId!!,
    unit = unit!!,
    brand = brand,
    model = model,
    barcode = barcode,
    packSize = packSize,
    unitWeightGrams = unitWeightGrams,
    unitVolumeMl = unitVolumeMl,
    photoUrl = photoUrl,
    photoFileId = photoFileId,
    notes = notes,
)
