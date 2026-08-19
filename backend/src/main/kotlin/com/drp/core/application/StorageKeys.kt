package com.drp.core.application

import java.util.UUID

/**
 * De donde sale la ruta de un fichero en el volumen (5.8.1).
 *
 * **La genera la aplicacion y jamas se acepta de la peticion.** Es el control que
 * cierra de una vez el path traversal, los bytes nulos y las dobles extensiones
 * --`x.pdf.php`--: el nombre original es un dato de la fila y no participa en la
 * ruta. Y va sin extension a proposito: nada debe poder interpretarse por su
 * nombre.
 *
 * ```
 * original/<householdId>/<2 primeros caracteres del fileId>/<fileId>
 * ```
 *
 * El troceado por dos caracteres evita directorios de decenas de miles de
 * entradas. Que el `householdId` aparezca **no es lo que aisla**: lo que aisla es
 * que para construir la ruta hay que haber leido antes la fila, y esa lectura ya
 * paso por la politica de RLS. De ahi una consecuencia que parece una
 * optimizacion desaprovechada y no lo es: no se deduplica contenido entre
 * hogares, porque compartir una ruta romperia justo esa herencia.
 */
object StorageKeys {

    /** El fichero tal y como se guarda. Es lo que va en la columna `storage_key`. */
    fun original(householdId: UUID, fileId: UUID): String =
        "$ORIGINAL/$householdId/${shardOf(fileId)}/$fileId"

    /**
     * La miniatura, **junto al original y bajo otro prefijo**.
     *
     * Se deriva de la clave guardada y no se recalcula desde los identificadores
     * por el mismo motivo por el que la clave se guarda en vez de recalcularse:
     * el dia que cambie la distribucion en disco, las filas viejas tienen que
     * seguir resolviendo sus dos variantes sin reescribir la historia.
     */
    fun thumbnailOf(originalKey: String): String {
        require(originalKey.startsWith("$ORIGINAL/")) { "No es la clave de un original: $originalKey" }
        return THUMBNAIL + originalKey.removePrefix(ORIGINAL)
    }

    /**
     * El avatar de una identidad, **fuera del arbol de los hogares**.
     *
     * No cuelga de ninguno porque una `Identity` no pertenece a ningun hogar (ver
     * 4.1.4): no tiene cuota a la que sumar ni politica de RLS que la cubra. Es
     * uno solo y siempre se sustituye, asi que la clave se deriva del
     * `identityId` y no de un identificador de fichero que no existe.
     */
    fun avatar(identityId: UUID): String = "$AVATAR/${shardOf(identityId)}/$identityId"

    /**
     * Los prefijos bajo los que vive **todo** lo de un hogar, para borrarlo
     * entero al darlo de baja (ADR-012).
     *
     * Son **dos y no uno**, y ese es el detalle que el dibujo de 5.8.1 no
     * ensena: el troceado empieza por el tipo --`original/` y `thumbnail/`-- y
     * el hogar viene despues, asi que no hay un solo directorio del hogar que
     * borrar. Quien escriba la ruta a ojo desde el diagrama se dejara las
     * miniaturas puestas, que es un fallo que no da ningun error.
     *
     * **El avatar no esta aqui, y es a proposito.** Vive fuera de los dos arboles
     * porque una identidad no pertenece a ningun hogar; se lo lleva
     * `CloseAccount` y no la baja del hogar.
     */
    fun householdTrees(householdId: UUID): List<String> =
        listOf("$ORIGINAL/$householdId", "$THUMBNAIL/$householdId")

    /**
     * Un avatar se guarda **siempre en WebP**, porque lo que se guarda es la
     * miniatura: se pinta a 40 px en una lista de personas y a poco mas en su
     * ficha, asi que conservar la foto entera seria guardar cien veces lo que se
     * usa.
     */
    const val AVATAR_CONTENT_TYPE = "image/webp"

    private fun shardOf(id: UUID) = id.toString().take(SHARD_LENGTH)

    private const val SHARD_LENGTH = 2
    private const val ORIGINAL = "original"
    private const val THUMBNAIL = "thumbnail"
    private const val AVATAR = "avatar"
}
