package com.drp.application

import com.drp.application.port.StoredFileRepository
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
 * **Hoy responde 404 siempre**, y es correcto: la subida de ficheros llega con el
 * Hito 3 (ADR-005), asi que la tabla `files` no puede tener ninguna fila. Cuando
 * ese hito exista, este codigo ya hace lo que tiene que hacer y lo unico que
 * cambia es que empiece a encontrar algo. El `photoUrl` --un enlace externo, una
 * columna de texto y nada mas-- si funciona desde este hito.
 */
@Service
class PhotoFileResolver(private val files: StoredFileRepository) {

    fun requireUsable(fileId: UUID?) {
        if (fileId == null) return
        if (!files.existsUsable(fileId)) throw ResourceNotFound("Fichero no encontrado")
    }
}
