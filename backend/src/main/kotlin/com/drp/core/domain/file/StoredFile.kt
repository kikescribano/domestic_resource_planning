package com.drp.core.domain.file

import java.time.Instant
import java.util.UUID

/**
 * Un binario guardado en el disco del servidor (README 4.1.1, 5.8).
 *
 * **No es un asset**: no ocupa sitio en el hogar, no se clasifica y no se presta.
 * Es un adjunto con dueno, tamano y fecha. Y **no se modifica nunca**: cambiar la
 * foto de un asset es subir otra y apuntar a ella, no editar la que habia. De ahi
 * que no lleve `updatedAt` ni `updatedBy` --lleva solo la mitad de la autoria-- y
 * de ahi tambien que el `checksum` siga significando algo.
 *
 * Los bytes viven fuera de la base de datos, asi que esta fila y el disco pueden
 * divergir. Los tres estados que separan `uploadedAt` y `deletedAt` son
 * precisamente lo que acota esa divergencia:
 *
 * - `uploadedAt` **nulo**: reserva de una subida en curso. La fila ya ocupa cuota
 *   y todavia no se puede adjuntar. Si la subida se corta, la recoge el proceso
 *   diario.
 * - `uploadedAt` informado y `deletedAt` nulo: el unico estado utilizable.
 * - `deletedAt` informado: retirado. La cuota se libera **en el acto**, y los
 *   bytes los desenlaza el proceso diario hasta 24 h despues. Ese desfase lo
 *   absorbe el dimensionado del volumen, no el usuario esperando.
 */
data class StoredFile(
    val id: UUID,
    /** El nombre con el que llego, saneado. Es **solo un dato**: nunca forma parte de la ruta en disco. */
    val originalName: String,
    /** El **detectado** al inspeccionar el contenido, no el que declaro quien subio el fichero. */
    val contentType: StoredContentType,
    /** El del fichero ya almacenado, despues de recodificarlo si era imagen. Es lo que suma la cuota. */
    val sizeBytes: Long,
    /** SHA-256 en hexadecimal. Detecta corrupcion silenciosa y permite cuadrar una restauracion. */
    val checksum: String,
    /**
     * Ruta relativa dentro del volumen de ficheros. **La genera la aplicacion** a
     * partir del identificador y no se acepta jamas de la peticion. Se guarda en
     * vez de recalcularse al vuelo para que cambiar la distribucion en disco --o
     * migrar a otro almacen-- no obligue a reescribir la historia.
     */
    val storageKey: String,
    val createdAt: Instant,
    val createdBy: UUID?,
    val uploadedAt: Instant?,
    val deletedAt: Instant?,
) {
    /** Vivo y subido del todo: el unico estado en el que se puede adjuntar o servir. */
    val isUsable: Boolean get() = uploadedAt != null && deletedAt == null

    /** Ocupa cuota: tanto la reserva a medias como el fichero terminado. Solo el borrado la libera. */
    val consumesQuota: Boolean get() = deletedAt == null

    /** Solo las imagenes tienen miniatura. Un PDF no. */
    val hasThumbnail: Boolean get() = contentType.isImage
}

/**
 * La lista blanca de 5.8.3, y la unica forma de nombrar un tipo de contenido en
 * el dominio.
 *
 * Es un enumerado y no un `String` porque la lista es cerrada por decision, no
 * por casualidad: ampliarla exige tocar esto, el `CHECK` de la tabla y el
 * contrato, y esa friccion es deliberada. Las dos exclusiones tambien lo son:
 * **SVG** queda fuera por ser XML con scripts dentro, y **HEIC** porque la JVM no
 * lo decodifica sin librerias nativas --lo convierte el frontend antes de subir.
 */
enum class StoredContentType(val value: String) {
    JPEG("image/jpeg"),
    PNG("image/png"),
    WEBP("image/webp"),
    PDF("application/pdf"),
    ;

    val isImage: Boolean get() = this != PDF

    companion object {
        /** Nulo si no esta en la lista blanca, que es lo que el caso de uso traduce a `415`. */
        fun from(value: String): StoredContentType? = entries.firstOrNull { it.value == value }
    }
}
