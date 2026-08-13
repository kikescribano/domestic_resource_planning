package com.drp.domain.file

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Facturas, garantias y manuales (README 4.1.1).
 *
 * Cuelga de **un asset o de un articulo, nunca de ambos**, y esa distincion ya
 * estaba implicita en el modelo: la factura y la garantia son de la unidad fisica
 * que compraste, y el manual es del modelo. Colgarlo del articulo es lo que hace
 * que dos taladros identicos compartan manual sin duplicarlo.
 *
 * Y apunta **a un enlace externo o a un fichero subido, nunca a los dos**. Las
 * dos vias conviven porque las dos son reales: la factura ya esta en el correo y
 * el manual en la web del fabricante, asi que obligar a descargarlos y volverlos
 * a subir seria trabajo inventado; pero la garantia que llego en un sobre no
 * tiene URL ninguna.
 */
data class Document(
    val id: UUID,
    val target: DocumentTarget,
    val type: DocumentType,
    val content: DocumentContent,
    val description: String?,
    /** Cuando se emitio: la fecha de la factura, la de la garantia. */
    val date: LocalDate?,
    /** Cuando deja de valer, que en una garantia es el dato que importa. Distinto del anterior. */
    val validUntil: LocalDate?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val createdBy: UUID?,
    val updatedBy: UUID?,
) {
    /** El fichero del que cuelga, cuando vive aqui. Nulo si es un enlace externo. */
    val fileId: UUID? get() = (content as? DocumentContent.StoredFileRef)?.fileId
}

enum class DocumentType { INVOICE, WARRANTY, MANUAL, OTHER }

/**
 * De quien cuelga: un asset **o** un articulo, obligatoriamente uno.
 *
 * Mismo motivo que [com.drp.domain.inventory.AssetLocation]: «los dos a la vez» y
 * «ninguno» son estados que no deben poder representarse. Con dos `UUID?` sueltos
 * ambos son escribibles y solo los rechaza el `CHECK` de la tabla, que es tarde
 * --y llega como un 500 en vez de como el 409 que declara el contrato.
 */
data class DocumentTarget(val kind: DocumentTargetKind, val id: UUID) {
    val assetId: UUID? get() = id.takeIf { kind == DocumentTargetKind.ASSET }

    val articleId: UUID? get() = id.takeIf { kind == DocumentTargetKind.ARTICLE }

    companion object {
        /** Reconstruye desde las dos columnas, que es como vive en la tabla. */
        fun from(assetId: UUID?, articleId: UUID?): DocumentTarget? = when {
            assetId != null && articleId != null -> null
            assetId != null -> DocumentTarget(DocumentTargetKind.ASSET, assetId)
            articleId != null -> DocumentTarget(DocumentTargetKind.ARTICLE, articleId)
            else -> null
        }
    }
}

enum class DocumentTargetKind { ASSET, ARTICLE }

/**
 * Donde vive el documento: fuera o aqui.
 *
 * Va como jerarquia sellada y no como el par `kind` + `id` de [DocumentTarget]
 * porque las dos alternativas **no son del mismo tipo**: una URL es texto y un
 * fichero es un identificador. Meterlas en un solo campo obligaria a convertir en
 * cada lectura.
 */
sealed interface DocumentContent {

    /** El documento ya vivia fuera: el correo del fabricante, la web de la tienda. */
    data class ExternalLink(val url: String) : DocumentContent

    /** El documento se subio al servidor. Ver [StoredFile]. */
    data class StoredFileRef(val fileId: UUID) : DocumentContent

    companion object {
        /** Nulo si no viene exactamente uno, que es lo que el caso de uso traduce a `DOCUMENT_CONTENT_INVALID`. */
        fun from(url: String?, fileId: UUID?): DocumentContent? = when {
            url != null && fileId != null -> null
            url != null -> ExternalLink(url)
            fileId != null -> StoredFileRef(fileId)
            else -> null
        }
    }
}
