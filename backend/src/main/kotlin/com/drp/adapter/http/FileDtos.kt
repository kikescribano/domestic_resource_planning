package com.drp.adapter.http

import com.drp.application.usecase.StorageUsage
import com.drp.domain.file.StoredFile
import java.time.Instant
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
