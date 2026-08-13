package com.drp.application.port

import com.drp.domain.file.StoredContentType
import java.io.InputStream

/**
 * Decide **que es** lo subido y lo deja listo para guardar (5.8.3, pasos 4 y 5).
 *
 * Es un puerto y no una funcion suelta porque debajo hay plataforma: la deteccion
 * mira bytes crudos y la recodificacion depende de un decodificador nativo para
 * WebP que la JVM no trae.
 *
 * Las dos mitades van juntas a proposito. Detectar el tipo y recodificar la
 * imagen parecen pasos separados y son el mismo: **una imagen que no se puede
 * decodificar no es una imagen**, por mucho que sus primeros bytes lo digan, y
 * lo mas fiable que se puede afirmar sobre un fichero es que se ha vuelto a
 * escribir entero. Partirlos dejaria un hueco por el que pasa un fichero con
 * cabecera valida y contenido que no lo es.
 */
interface FileContentProcessor {

    /**
     * Nulo si el tipo real no esta en la lista blanca, o si dice ser una imagen y
     * no se puede decodificar. Las dos cosas responden `415`: por fuera son lo
     * mismo --esto no es lo que dices que es-- y distinguirlas solo ayudaria a
     * quien esta probando que cuela.
     *
     * [open] se puede invocar varias veces sobre el mismo contenido.
     */
    fun process(open: () -> InputStream): ProcessedContent?
}

/**
 * Lo que hay que guardar, ya normalizado.
 *
 * En una imagen, [openContent] **no** devuelve los bytes que llegaron sino los
 * recodificados, y esa es la razon principal de todo el paso: una foto hecha con
 * el movil dentro de casa lleva las coordenadas GPS de la casa incrustadas en el
 * EXIF. Recodificar las borra, y de paso destruye cualquier carga util escondida
 * en el fichero. En un PDF devuelve lo que llego: no se recodifica, se sirve
 * siempre como adjunto y nunca incrustado.
 */
class ProcessedContent(
    val contentType: StoredContentType,
    val openContent: () -> InputStream,
    /**
     * Nula en un PDF: solo las imagenes tienen miniatura. En una imagen **nunca**
     * es nula, porque generarla es parte de la misma recodificacion: si no se
     * pudo generar, tampoco se pudo recodificar, y entonces el fichero se rechaza
     * (README 4.1.1).
     */
    val openThumbnail: (() -> InputStream)?,
)
