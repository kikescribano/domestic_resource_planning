package com.drp.application

import com.drp.application.port.StoredFileRepository
import com.drp.domain.BusinessRuleViolation
import com.drp.domain.ErrorCode
import com.drp.domain.ResourceNotFound
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Comprueba que un `photoFileId` recibido del cliente se puede adjuntar.
 *
 * Lo usan las tres entidades que llevan foto --ubicacion, articulo y asset-- y
 * existe por la misma razon que la resolucion de cualquier otra referencia: **la
 * comprobacion de una clave ajena no pasa por RLS**, asi que el fichero de otro
 * hogar es invisible a un `SELECT` y aun asi se puede referenciar. Sin resolverlo
 * antes, adjuntar un identificador ajeno daria un 500 por violacion de
 * restriccion en vez del 404 que declara el contrato.
 *
 * Son **tres** comprobaciones y no una, y las tres hacen falta:
 *
 * 1. Que exista y sea de este hogar. Lo primero lo dice la fila; lo segundo, la
 *    politica de RLS, que hace invisible la de otro.
 * 2. Que este subido del todo. Una reserva a medias --`uploadedAt` nulo-- ya
 *    ocupa cuota y todavia no tiene bytes que ensenar.
 * 3. Que **no cuelgue ya de ningun sitio**. Un fichero se adjunta una sola vez
 *    (README 4.1.1): compartirlo haria ambiguo que pasa al borrarlo y que cuenta
 *    en la cuota. Esta es la que la base de datos **no** garantiza: el indice
 *    `documents_file_unique` impide que dos documentos compartan fichero y nada
 *    mas — que un documento y una foto lo compartan, o que dos assets compartan
 *    foto, solo lo impide esto.
 */
@Service
class PhotoFileResolver(private val files: StoredFileRepository) {

    /**
     * [alreadyHeld] es el fichero que la entidad ya tenia. Sin ese parametro, un
     * `PATCH` que reenvia la misma foto --que es lo que hace un formulario que
     * manda todos sus campos-- se rechazaria por estar adjunta... a si misma.
     */
    fun requireAttachable(fileId: UUID?, alreadyHeld: UUID? = null) {
        if (fileId == null || fileId == alreadyHeld) return

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
