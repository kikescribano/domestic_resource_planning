package com.drp.adapter.http

import com.drp.application.usecase.ArticleView
import com.drp.domain.catalog.Article
import com.drp.domain.catalog.MeasurementUnit
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class ArticleInput(
    @field:NotBlank @field:Size(max = 160) val name: String,
    @field:NotNull val categoryId: UUID?,
    @field:NotNull val unit: MeasurementUnit?,
    val brand: String? = null,
    val model: String? = null,
    val barcode: String? = null,
    @field:Positive val packSize: BigDecimal? = null,
    val photoUrl: String? = null,
    val photoFileId: UUID? = null,
    val notes: String? = null,
)

/**
 * El nombre de la categoria viaja **resuelto** para lectura, junto al
 * `categoryId`. Es lo que evita que el cliente tenga que pedir el catalogo
 * entero para pintar un listado de articulos.
 */
data class ArticleResponse(
    val id: UUID,
    val name: String,
    val categoryId: UUID,
    val category: String?,
    val unit: MeasurementUnit,
    val brand: String?,
    val model: String?,
    val barcode: String?,
    val packSize: BigDecimal?,
    val photoUrl: String?,
    val photoThumbnailUrl: String?,
    val photoFileId: UUID?,
    val notes: String?,
    val createdAt: Instant,
    val retiredAt: Instant?,
    val createdBy: UUID?,
    val updatedBy: UUID?,
) {
    companion object {
        fun of(view: ArticleView, photoThumbnailUrl: String?) =
            of(view.article, view.categoryName, photoThumbnailUrl)

        fun of(article: Article, categoryName: String?, photoThumbnailUrl: String?) = ArticleResponse(
            id = article.id,
            name = article.name,
            categoryId = article.categoryId,
            category = categoryName,
            unit = article.unit,
            brand = article.brand,
            model = article.model,
            barcode = article.barcode,
            packSize = article.packSize,
            photoUrl = article.photoUrl,
            photoThumbnailUrl = photoThumbnailUrl,
            photoFileId = article.photoFileId,
            notes = article.notes,
            createdAt = article.createdAt,
            retiredAt = article.retiredAt,
            createdBy = article.createdBy,
            updatedBy = article.updatedBy,
        )
    }
}
