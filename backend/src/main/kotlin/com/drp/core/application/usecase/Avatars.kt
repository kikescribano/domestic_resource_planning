package com.drp.core.application.usecase

import com.drp.core.application.StorageKeys
import com.drp.core.application.port.ContentTooLarge
import com.drp.core.application.port.FileContentProcessor
import com.drp.core.application.port.FileStorage
import com.drp.core.application.port.IdentityRepository
import com.drp.core.application.port.SessionClaims
import com.drp.platform.error.BusinessRuleViolation
import com.drp.platform.error.ErrorCode
import com.drp.platform.error.ResourceNotFound
import com.drp.core.domain.identity.Avatar
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

/**
 * Sube o sustituye el avatar propio (`SetIdentityAvatar`, 5.7).
 *
 * Se parece a `UploadFile` y **no comparte casi nada con el**, porque el avatar
 * no es un fichero del hogar (README 4.1.1):
 *
 * - **No reserva cuota ni bloquea nada.** Una identidad no pertenece a ningun
 *   hogar, asi que no hay cuota a la que sumar. Su limite es un tamano maximo por
 *   fichero --1 MB-- y ya esta en el `CHECK` de la columna.
 * - **Sustituye siempre**, de modo que no acumula. La clave se deriva del
 *   `identityId`, no de un identificador de fichero que no existe, y por eso
 *   sobrescribir es lo natural: el avatar viejo desaparece con el nuevo.
 * - **Solo imagen.** Un PDF no retrata a nadie, y ademas seria lo unico del
 *   sistema que se pintaria sin miniatura en una lista de personas.
 *
 * «Me» resuelve a la **identidad** del token, no a la pertenencia: el avatar es
 * de la persona y sobrevive a cualquier hogar.
 */
@Service
class SetIdentityAvatar(
    private val identities: IdentityRepository,
    private val storage: FileStorage,
    private val processor: FileContentProcessor,
    private val clock: Clock,
    @Value("\${drp.storage.max-avatar-bytes}") private val maxBytes: Long,
) {

    @Transactional
    fun handle(session: SessionClaims, upload: UploadSource): Avatar {
        val identity = identities.findById(session.identityId)
            ?: throw ResourceNotFound("Identidad no encontrada")

        val received = upload.receive()

        val staged = try {
            received.content.use { storage.stage(it, maxBytes) }
        } catch (tooLarge: ContentTooLarge) {
            throw BusinessRuleViolation(
                ErrorCode.FILE_TOO_LARGE,
                "El avatar supera el tamaño máximo de ${tooLarge.maxBytes} bytes",
            )
        }

        val avatar = staged.use {
            val processed = processor.process(staged::open)
                ?: throw BusinessRuleViolation(ErrorCode.FILE_TYPE_NOT_ALLOWED, "El avatar tiene que ser una imagen")

            if (!processed.contentType.isImage) {
                throw BusinessRuleViolation(ErrorCode.FILE_TYPE_NOT_ALLOWED, "El avatar tiene que ser una imagen")
            }

            // Se guarda **la miniatura**, no el original: un avatar se pinta a 40
            // px en una lista de personas y a poco mas en su ficha, asi que
            // conservar la foto entera seria guardar cien veces lo que se usa. Y
            // pasa por la misma recodificacion que el resto, con lo que llega sin
            // EXIF.
            val bytes = requireNotNull(processed.openThumbnail) { "Una imagen siempre tiene miniatura" }
            val key = StorageKeys.avatar(identity.id)
            val written = bytes().use { storage.write(key, it) }

            Avatar(key, StorageKeys.AVATAR_CONTENT_TYPE, written.sizeBytes)
        }

        identities.save(identity.copy(avatar = avatar, updatedAt = clock.instant()))
        return avatar
    }
}

/**
 * Quita el avatar propio (`DeleteIdentityAvatar`, 5.7).
 *
 * Aqui **si se borran los bytes en el acto**, al contrario que en un fichero del
 * hogar. La diferencia no es de criterio sino de para que sirve el aplazamiento
 * alli: la ventana de 24 horas existe para poder recuperar un borrado por error
 * sin ir a la copia de seguridad, y un avatar se recupera volviendo a subirlo.
 */
@Service
class DeleteIdentityAvatar(
    private val identities: IdentityRepository,
    private val storage: FileStorage,
    private val clock: Clock,
) {

    @Transactional
    fun handle(identityId: UUID) {
        val identity = identities.findById(identityId) ?: return
        val avatar = identity.avatar ?: return

        identities.save(identity.copy(avatar = null, updatedAt = clock.instant()))
        storage.delete(avatar.storageKey)
    }
}
