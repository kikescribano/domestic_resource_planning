package com.drp.adapter.http

import com.drp.application.usecase.DocumentCommand
import com.drp.application.usecase.StorageUsage
import com.drp.domain.file.Document
import com.drp.domain.file.DocumentContent
import com.drp.domain.file.DocumentType
import com.drp.domain.file.StoredFile
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Un fichero, tal y como lo declara el contrato.
 *
 * Las dos URL **se emiten ya firmadas** en vez de ofrecer un endpoint que las
 * pida: es lo que permite que un `<img src>` nativo las pinte, con
 * `loading="lazy"`, `srcset` y cache del navegador. Caducan con el access token
 * que las genero, asi que **no se guardan**: valen para pintar ahora.
 */
data class StoredFileResponse(
    val id: UUID,
    val originalName: String,
    val contentType: String,
    val sizeBytes: Long,
    val checksum: String,
    val url: String,
    /** Nula en un PDF: solo las imagenes tienen miniatura. */
    val thumbnailUrl: String?,
    val uploadedAt: Instant?,
    val createdAt: Instant,
    val createdBy: UUID?,
) {
    companion object {
        fun of(file: StoredFile, urls: SignedFileUrls) = StoredFileResponse(
            id = file.id,
            originalName = file.originalName,
            contentType = file.contentType.value,
            sizeBytes = file.sizeBytes,
            checksum = file.checksum,
            url = urls.original(file.storageKey),
            thumbnailUrl = if (file.hasThumbnail) urls.thumbnail(file.storageKey) else null,
            uploadedAt = file.uploadedAt,
            createdAt = file.createdAt,
            createdBy = file.createdBy,
        )
    }
}

/**
 * Lo que llega al adjuntar un documento.
 *
 * Las dos exclusiones --destino y contenido-- **no se validan aqui** con
 * anotaciones, y no es un descuido: Bean Validation responderia `400
 * VALIDATION_ERROR` y el contrato declara `409` con codigo propio para las dos.
 * Son reglas de negocio, no de forma, asi que las comprueba el caso de uso.
 */
data class DocumentInput(
    val assetId: UUID? = null,
    val articleId: UUID? = null,
    @field:NotNull val type: DocumentType? = null,
    val url: String? = null,
    val fileId: UUID? = null,
    @field:Size(max = 500) val description: String? = null,
    val date: LocalDate? = null,
    val validUntil: LocalDate? = null,
) {
    fun toCommand() = DocumentCommand(
        assetId = assetId,
        articleId = articleId,
        type = type!!,
        url = url,
        fileId = fileId,
        description = description,
        date = date,
        validUntil = validUntil,
    )
}

data class DocumentResponse(
    val id: UUID,
    val assetId: UUID?,
    val articleId: UUID?,
    val type: DocumentType,
    val url: String?,
    val fileId: UUID?,
    val description: String?,
    val date: LocalDate?,
    val validUntil: LocalDate?,
    val createdAt: Instant,
    val createdBy: UUID?,
    val updatedBy: UUID?,
) {
    companion object {
        fun of(document: Document) = DocumentResponse(
            id = document.id,
            assetId = document.target.assetId,
            articleId = document.target.articleId,
            type = document.type,
            url = (document.content as? DocumentContent.ExternalLink)?.url,
            fileId = document.fileId,
            description = document.description,
            date = document.date,
            validUntil = document.validUntil,
            createdAt = document.createdAt,
            createdBy = document.createdBy,
            updatedBy = document.updatedBy,
        )
    }
}

/**
 * Ocupacion y cuota. Existe para **avisar antes de que una subida falle**, en
 * lugar de despues.
 */
data class StorageUsageResponse(
    val usedBytes: Long,
    val quotaBytes: Long,
    val maxFileBytes: Long,
) {
    companion object {
        fun of(usage: StorageUsage) = StorageUsageResponse(
            usedBytes = usage.usedBytes,
            quotaBytes = usage.policy.quotaBytes,
            maxFileBytes = usage.policy.maxFileBytes,
        )
    }
}
