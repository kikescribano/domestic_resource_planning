package com.drp.adapter.http

import com.drp.application.port.ArticleFilter
import com.drp.application.port.Pagination
import com.drp.application.port.SessionClaims
import com.drp.application.usecase.ArticleCommand
import com.drp.application.usecase.ArticlePatch
import com.drp.application.usecase.CreateArticle
import com.drp.application.usecase.CreateCategory
import com.drp.application.usecase.GetArticle
import com.drp.application.usecase.ListArticles
import com.drp.application.usecase.ListCategories
import com.drp.application.usecase.RetireArticle
import com.drp.application.usecase.RetireCategory
import com.drp.application.usecase.UpdateArticle
import com.drp.application.usecase.UpdateCategory
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
    ): CategoryResponse = CategoryResponse.of(createCategory.handle(session, input.name, input.notes))

    @PatchMapping("/{id}")
    fun update(
        @AuthenticationPrincipal session: SessionClaims,
        @PathVariable id: UUID,
        @Valid @RequestBody input: CategoryInput,
    ): CategoryResponse = CategoryResponse.of(updateCategory.handle(session, id, input.name, input.notes))

    /** Retirada logica, no borrado: los assets y los articulos que la tenian la conservan. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun retire(
        @AuthenticationPrincipal session: SessionClaims,
        @PathVariable id: UUID,
    ) = retireCategory.handle(session, id)
}

@RestController
@RequestMapping("/api/v1/articles")
class ArticleController(
    private val listArticles: ListArticles,
    private val getArticle: GetArticle,
    private val createArticle: CreateArticle,
    private val updateArticle: UpdateArticle,
    private val retireArticle: RetireArticle,
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
    ): PageResponse<ArticleResponse> = PageResponse.of(
        listArticles.handle(ArticleFilter(q, categoryId, barcode, includeRetired), Pagination(page, size)),
        ArticleResponse::of,
    )

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): ArticleResponse = ArticleResponse.of(getArticle.handle(id))

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @AuthenticationPrincipal session: SessionClaims,
        @Valid @RequestBody input: ArticleInput,
    ): ArticleResponse = ArticleResponse.of(createArticle.handle(session, input.toCommand()))

    @PatchMapping("/{id}")
    fun update(
        @AuthenticationPrincipal session: SessionClaims,
        @PathVariable id: UUID,
        @RequestBody body: JsonNode,
    ): ArticleResponse {
        val patch = JsonPatch(body)
        return ArticleResponse.of(
            updateArticle.handle(
                session,
                id,
                ArticlePatch(
                    name = patch.requiredText("name"),
                    categoryId = patch.requiredUuid("categoryId"),
                    unit = patch.enum("unit"),
                    brand = patch.text("brand"),
                    model = patch.text("model"),
                    barcode = patch.text("barcode"),
                    packSize = patch.decimal("packSize"),
                    photoUrl = patch.text("photoUrl"),
                    photoFileId = patch.uuid("photoFileId"),
                    notes = patch.text("notes"),
                ),
            ),
        )
    }

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
    photoUrl = photoUrl,
    photoFileId = photoFileId,
    notes = notes,
)
