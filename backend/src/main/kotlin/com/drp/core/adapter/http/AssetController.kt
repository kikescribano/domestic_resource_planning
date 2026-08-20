package com.drp.core.adapter.http

import com.drp.core.application.port.AssetFilter
import com.drp.core.application.port.SessionClaims
import com.drp.core.application.usecase.ArticleCommand
import com.drp.core.application.usecase.AssetPatch
import com.drp.core.application.usecase.AssetView
import com.drp.core.application.usecase.CreateAsset
import com.drp.core.application.usecase.CreateAssetCommand
import com.drp.core.application.usecase.DecommissionAsset
import com.drp.core.application.usecase.GetAsset
import com.drp.core.application.usecase.IntakeCommand
import com.drp.core.application.usecase.ListAssets
import com.drp.core.application.usecase.MergeStockItems
import com.drp.core.application.usecase.RegisterConsumableIntake
import com.drp.core.application.usecase.UpdateAsset
import com.drp.core.domain.inventory.AssetCondition
import com.drp.core.domain.inventory.AssetStatus
import com.drp.core.domain.inventory.AssetType
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

@RestController
@RequestMapping("/api/v1/assets")
class AssetController(
    private val listAssets: ListAssets,
    private val getAsset: GetAsset,
    private val createAsset: CreateAsset,
    private val registerIntake: RegisterConsumableIntake,
    private val updateAsset: UpdateAsset,
    private val mergeStockItems: MergeStockItems,
    private val decommissionAsset: DecommissionAsset,
    private val photoUrls: PhotoUrls,
) {

    @GetMapping
    @Suppress("LongParameterList")
    fun list(
        @RequestParam(required = false) locationId: UUID?,
        @RequestParam(required = false) parentAssetId: UUID?,
        @RequestParam(required = false) ownerId: UUID?,
        @RequestParam(required = false) status: AssetStatus?,
        @RequestParam(required = false) type: AssetType?,
        @RequestParam(required = false) articleId: UUID?,
        @RequestParam(required = false) categoryId: UUID?,
        @RequestParam(required = false) condition: AssetCondition?,
        @RequestParam(required = false) tagId: UUID?,
        @RequestParam(defaultValue = "false") withoutOwner: Boolean,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): PageResponse<AssetResponse> = listAssets.handle(
        AssetFilter(
            locationId,
            parentAssetId,
            ownerId,
            status,
            type,
            articleId,
            categoryId,
            condition,
            tagId,
            withoutOwner,
        ),
        Pagination(page, size),
    ).withThumbnails()

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): AssetResponse = getAsset.handle(id).withThumbnail()

    /** Lo que este asset contiene. Es la otra mitad del arbol, junto al de ubicaciones. */
    @GetMapping("/{id}/children")
    fun children(
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): PageResponse<AssetResponse> {
        // Se resuelve el padre primero: una lista vacia no distingue "no tiene
        // nada dentro" de "no es tuyo".
        getAsset.handle(id)
        return listAssets.handle(AssetFilter(parentAssetId = id), Pagination(page, size)).withThumbnails()
    }

    /** Solo `DURABLE`: un consumible entra por `/intake`, que suma sobre la existencia. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @AuthenticationPrincipal session: SessionClaims,
        @Valid @RequestBody input: AssetInput,
    ): AssetResponse = createAsset.handle(
        session,
        CreateAssetCommand(
                name = input.name,
                type = input.type!!,
                categoryId = input.categoryId,
                articleId = input.articleId,
                ownerId = input.ownerId,
                location = input.location?.toDomain(),
                serialNumber = input.serialNumber,
                acquiredOn = input.acquiredOn,
            condition = input.condition,
            photoUrl = input.photoUrl,
            photoFileId = input.photoFileId,
            notes = input.notes,
            tagIds = input.tagIds,
        ),
    ).withThumbnail()

    /**
     * `201` si creo la primera existencia de ese articulo en esa ubicacion, y
     * `200` si sumo sobre una que ya habia. La diferencia la decide el caso de
     * uso, no el cliente: traer otro paquete de azucar es la misma peticion en
     * los dos casos.
     */
    @PostMapping("/intake")
    fun intake(
        @AuthenticationPrincipal session: SessionClaims,
        @Valid @RequestBody input: AssetIntakeInput,
    ): ResponseEntity<AssetResponse> {
        val result = registerIntake.handle(
            session,
            IntakeCommand(
                articleId = input.articleId,
                newArticle = input.article?.let {
                    ArticleCommand(
                        name = it.name,
                        categoryId = it.categoryId!!,
                        unit = it.unit!!,
                        brand = it.brand,
                        model = it.model,
                        barcode = it.barcode,
                        packSize = it.packSize,
                        photoUrl = it.photoUrl,
                        photoFileId = it.photoFileId,
                        notes = it.notes,
                    )
                },
                ownerId = input.ownerId!!,
                location = input.location?.toDomain(),
                quantity = input.quantity!!,
            ),
        )

        return ResponseEntity
            .status(if (result.created) HttpStatus.CREATED else HttpStatus.OK)
            .body(result.view.withThumbnail())
    }

    /**
     * Tres operaciones en una: mover, ajustar cantidad y corregir la ficha. El
     * cuerpo llega como arbol JSON para distinguir «no menciones este campo» de
     * «ponlo a nulo» --y aqui `location` a nulo significa «déjalo sin ubicar»,
     * que es un estado legitimo.
     */
    @PatchMapping("/{id}")
    fun update(
        @AuthenticationPrincipal session: SessionClaims,
        @PathVariable id: UUID,
        @RequestBody body: JsonNode,
    ): AssetResponse {
        val patch = JsonPatch(body)
        return updateAsset.handle(
            session,
            id,
            AssetPatch(
                name = patch.requiredText("name"),
                categoryId = patch.requiredUuid("categoryId"),
                articleId = patch.requiredUuid("articleId"),
                ownerId = patch.uuid("ownerId"),
                location = patch.assetLocation(),
                quantity = patch.requiredDecimal("quantity"),
                serialNumber = patch.text("serialNumber"),
                acquiredOn = patch.date("acquiredOn"),
                condition = patch.enum<AssetCondition>("condition"),
                photoUrl = patch.text("photoUrl"),
                photoFileId = patch.uuid("photoFileId"),
                notes = patch.text("notes"),
                // Absoluto: `[]` las quita todas y no mencionarlo no toca nada.
                tagIds = patch.uuidList("tagIds"),
            ),
        ).withThumbnail()
    }

    /**
     * Juntar dos existencias del mismo articulo. El asset de la ruta es el
     * **origen**, el que desaparece; el del cuerpo es el que sobrevive con la
     * suma.
     */
    @PostMapping("/{id}/merge")
    fun merge(
        @AuthenticationPrincipal session: SessionClaims,
        @PathVariable id: UUID,
        @Valid @RequestBody input: AssetMergeInput,
    ): AssetResponse = mergeStockItems.handle(session, id, input.targetAssetId!!).withThumbnail()

    /** Baja **logica**: nada se borra, para no perder el historial. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun decommission(
        @AuthenticationPrincipal session: SessionClaims,
        @PathVariable id: UUID,
    ) = decommissionAsset.handle(session, id)

    /**
     * La miniatura firmada de cada fila, resuelta **de una vez** para toda la
     * pagina. Es la pantalla donde mas importa: una rejilla de existencias en un
     * movil son cincuenta fotos, y resolverlas de una en una serian cincuenta
     * consultas.
     */
    private fun Page<AssetView>.withThumbnails(): PageResponse<AssetResponse> {
        val photos = photoUrls.index(items.map { it.asset.photoFileId })
        return PageResponse.of(this) { AssetResponse.of(it, photos.thumbnail(it.asset.photoFileId)) }
    }

    private fun AssetView.withThumbnail() = AssetResponse.of(this, photoUrls.thumbnail(asset.photoFileId))
}
