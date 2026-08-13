package com.drp.application.usecase

import com.drp.application.event.CoreEvents
import com.drp.application.port.ArticleRepository
import com.drp.application.port.AssetRepository
import com.drp.application.port.DocumentFilter
import com.drp.application.port.DocumentRepository
import com.drp.application.port.Page
import com.drp.application.port.Pagination
import com.drp.application.port.SessionClaims
import com.drp.application.port.StoredFileRepository
import com.drp.domain.BusinessRuleViolation
import com.drp.domain.ErrorCode
import com.drp.domain.ResourceNotFound
import com.drp.domain.file.Document
import com.drp.domain.file.DocumentContent
import com.drp.domain.file.DocumentTarget
import com.drp.domain.file.DocumentTargetKind
import com.drp.domain.file.DocumentType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

/**
 * Lo que llega del cliente al adjuntar. Las dos parejas excluyentes vienen como
 * cuatro campos anulables porque asi las declara el contrato; convertirlas a los
 * objetos que no admiten el estado invalido es lo primero que hace el caso de
 * uso, y de ahi salen sus dos `409`.
 */
data class DocumentCommand(
    val assetId: UUID?,
    val articleId: UUID?,
    val type: DocumentType,
    val url: String?,
    val fileId: UUID?,
    val description: String?,
    val date: LocalDate?,
    val validUntil: LocalDate?,
)

@Service
class ListDocuments(private val documents: DocumentRepository) {

    @Transactional(readOnly = true)
    fun handle(filter: DocumentFilter, pagination: Pagination): Page<Document> = documents.list(filter, pagination)
}

/**
 * Adjunta un documento a un asset o a un articulo (`AttachDocument`, 5.7).
 *
 * Las cuatro comprobaciones que hace, y por que ninguna sobra:
 *
 * 1. **Exactamente un destino** y **exactamente un contenido**. Los dos `CHECK`
 *    de la tabla los garantizan tambien, pero llegando ahi el fallo sale como un
 *    `500` por violacion de restriccion en vez de como el `409` con codigo que
 *    declara el contrato.
 * 2. **El destino se resuelve contra su repositorio.** No es redundante con la
 *    clave ajena: **la comprobacion de una clave ajena no pasa por RLS**, asi que
 *    un asset de otro hogar es invisible a un `SELECT` y aun asi se puede
 *    referenciar.
 * 3. **El fichero, igual, y ademas libre.** Un fichero se adjunta una sola vez.
 */
@Service
class AttachDocument(
    private val documents: DocumentRepository,
    private val assets: AssetRepository,
    private val articles: ArticleRepository,
    private val files: StoredFileRepository,
    private val events: CoreEvents,
    private val clock: Clock,
) {

    @Transactional
    fun handle(session: SessionClaims, command: DocumentCommand): Document {
        val target = DocumentTarget.from(command.assetId, command.articleId)
            ?: throw BusinessRuleViolation(
                ErrorCode.DOCUMENT_TARGET_INVALID,
                "Un documento cuelga de un asset o de un artículo, nunca de ambos ni de ninguno",
            )

        val content = DocumentContent.from(command.url?.trim()?.takeIf { it.isNotEmpty() }, command.fileId)
            ?: throw BusinessRuleViolation(
                ErrorCode.DOCUMENT_CONTENT_INVALID,
                "Un documento apunta a un enlace o a un fichero, nunca a los dos ni a ninguno",
            )

        requireTarget(target)
        if (content is DocumentContent.StoredFileRef) requireAttachableFile(content.fileId)

        val now = clock.instant()
        val attached = documents.save(
            Document(
                id = UUID.randomUUID(),
                target = target,
                type = command.type,
                content = content,
                description = command.description,
                date = command.date,
                validUntil = command.validUntil,
                createdAt = now,
                updatedAt = now,
                createdBy = session.memberId,
                updatedBy = session.memberId,
            ),
        )

        events.documentAttached(attached.id, target.assetId, target.articleId, attached.type.name, attached.fileId)
        return attached
    }

    private fun requireTarget(target: DocumentTarget) {
        when (target.kind) {
            DocumentTargetKind.ASSET ->
                assets.findById(target.id) ?: throw ResourceNotFound("Asset no encontrado")
            DocumentTargetKind.ARTICLE ->
                articles.findById(target.id) ?: throw ResourceNotFound("Artículo no encontrado")
        }
    }

    /**
     * `404` y no `403` cuando el fichero no es de este hogar, a proposito:
     * confirmar que existe delataria un fichero ajeno.
     */
    private fun requireAttachableFile(fileId: UUID) {
        val file = files.findById(fileId)
        if (file == null || !file.isUsable) throw ResourceNotFound("Fichero no encontrado")

        if (files.isAttached(fileId)) {
            throw BusinessRuleViolation(
                ErrorCode.FILE_ALREADY_ATTACHED,
                "El fichero ya está adjunto en otro sitio: un fichero se adjunta una sola vez",
            )
        }
    }
}

/**
 * Borra un documento (`DeleteDocument`, 5.7).
 *
 * Borrado **real**: no lo referencia ninguna otra entidad, asi que no hay
 * historial que preservar. Y si tenia fichero, **la misma transaccion lo marca**:
 * la cuota se libera en el acto y los bytes los desenlaza el proceso diario.
 *
 * El orden importa. Marcar el fichero antes de borrar el documento lo dejaria
 * viendose todavia adjunto; hacerlo en dos transacciones dejaria una ventana en
 * la que un fallo se lleva el documento y deja el fichero ocupando cuota para
 * siempre --sin nadie que lo referencie, no habria quien lo soltase.
 */
@Service
class DeleteDocument(
    private val documents: DocumentRepository,
    private val release: ReleaseFile,
) {

    @Transactional
    fun handle(documentId: UUID) {
        val document = documents.findById(documentId) ?: throw ResourceNotFound("Documento no encontrado")

        documents.delete(documentId)
        document.fileId?.let(release::handle)
    }
}
