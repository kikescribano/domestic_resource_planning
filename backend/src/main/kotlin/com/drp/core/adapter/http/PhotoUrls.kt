package com.drp.core.adapter.http

import com.drp.core.application.port.StoredFileRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

/**
 * Convierte el `photoFileId` de un asset, un articulo o una ubicacion en la URL
 * firmada de su miniatura.
 *
 * ## Por que abre su propia transaccion
 *
 * Porque **fuera de una transaccion, RLS no devuelve nada**. El caso de uso cierra
 * la suya al devolver el resultado, y este mapeo ocurre despues; una consulta
 * lanzada aqui sin transaccion propia correria sin `app.household_id` y la
 * politica no dejaria pasar ninguna fila. El sintoma seria una miniatura en
 * blanco --nunca un error-- que es la peor forma de fallo posible: se ve como
 * «esa foto no tiene miniatura» y nadie sospecha del aislamiento.
 *
 * El contexto de inquilino sigue puesto, porque va atado al hilo que atiende la
 * peticion, asi que la transaccion nueva nace con el hogar correcto.
 *
 * ## Por que un indice y no una consulta por fila
 *
 * Un listado de cincuenta existencias son cincuenta fotos. Resolverlas de una en
 * una son cincuenta consultas para pintar una rejilla, que es justo la pantalla
 * que mas se abre desde un movil.
 */
@Component
class PhotoUrls(
    private val files: StoredFileRepository,
    private val urls: SignedFileUrls,
    private val transactions: TransactionTemplate,
) {

    /** Una sola fila. */
    fun thumbnail(fileId: UUID?): String? = index(listOf(fileId)).thumbnail(fileId)

    /** Una pagina entera, con una consulta. */
    fun index(fileIds: List<UUID?>): PhotoUrlIndex {
        val wanted = fileIds.filterNotNull().distinct()
        if (wanted.isEmpty()) return PhotoUrlIndex(emptyMap())

        val found = transactions.execute { files.findAllUsable(wanted) }.orEmpty()
        return PhotoUrlIndex(found.filter { it.hasThumbnail }.associate { it.id to urls.thumbnail(it.storageKey) })
    }
}

/**
 * Lo resuelto para una pagina. Un identificador que no este --borrado, de otro
 * hogar, o una subida a medias-- da nulo, que es lo que el contrato declara
 * cuando no hay miniatura.
 */
class PhotoUrlIndex(private val byFileId: Map<UUID, String>) {

    fun thumbnail(fileId: UUID?): String? = fileId?.let(byFileId::get)
}
