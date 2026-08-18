package com.drp.core.adapter.storage

import com.drp.core.application.port.FileContentProcessor
import com.drp.core.application.port.ProcessedContent
import com.drp.platform.error.BusinessRuleViolation
import com.drp.platform.error.ErrorCode
import com.drp.core.domain.file.StoredContentType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/**
 * La deteccion del tipo real y la recodificacion de las imagenes (5.8.3, pasos 4
 * y 5), sobre ImageIO.
 *
 * **Recodificar es sobre todo por el EXIF.** Una foto hecha con el movil dentro
 * de casa lleva incrustadas las coordenadas GPS de la casa. Es el dato mas
 * sensible que va a atravesar este mecanismo y nadie lo introduce a sabiendas:
 * quitar los metadatos al recodificar no es un ahorro de bytes, es la razon
 * principal para hacerlo.
 *
 * Y se consigue **por construccion, no por borrado**: no se busca ni se elimina
 * ningun bloque de metadatos. Se decodifica la imagen a una rejilla de pixeles,
 * se pinta sobre un lienzo nuevo y se escribe ese lienzo. Lo que no son pixeles
 * --EXIF, XMP, perfiles, comentarios, y cualquier carga util escondida entre
 * segmentos-- sencillamente no llega al escritor. Es mas fiable que una lista de
 * bloques a borrar, que envejece con cada formato nuevo.
 */
@Component
class ImageIoFileContentProcessor(
    @Value("\${drp.storage.max-image-pixels}") private val maxImagePixels: Long,
    @Value("\${drp.storage.thumbnail-pixels}") private val thumbnailPixels: Int,
) : FileContentProcessor {

    private val log = LoggerFactory.getLogger(javaClass)

    init {
        // Sin esto, ImageIO usa un cache en disco para los flujos y deja
        // temporales en el directorio del sistema, que es justo lo que 5.8.1
        // quiere evitar: ficheros del hogar fuera de su volumen.
        ImageIO.setUseCache(false)
    }

    override fun process(open: () -> InputStream): ProcessedContent? {
        val head = open().use { it.readNBytes(ContentSniffer.HEAD_BYTES) }
        val detected = ContentSniffer.detect(head) ?: return null

        // Un PDF no se recodifica ni tiene miniatura. Se acepta porque los
        // manuales y las facturas son PDF, y se compensa por el otro lado: nunca
        // se muestra incrustado y siempre se sirve como adjunto (5.8.4).
        if (!detected.isImage) return ProcessedContent(detected, open, null)

        val source = open().use { decode(it) } ?: return null
        val content = encode(source, detected) ?: return null

        // Si no se puede generar la miniatura tampoco se ha podido recodificar de
        // verdad, asi que el fichero se rechaza entero en vez de guardarse sin
        // ella (README 4.1.1).
        val thumbnail = encode(scaleDown(source, thumbnailPixels), StoredContentType.WEBP) ?: return null

        return ProcessedContent(detected, { content.inputStream() }, { thumbnail.inputStream() })
    }

    /**
     * Decodifica **comprobando las dimensiones antes** y **exigiendo que la
     * imagen entre entera**.
     *
     * El orden es todo el control: un PNG de 50 000 x 50 000 ocupa unos pocos
     * kilobytes comprimido --pasa de sobra el tope de 25 MB-- y son diez
     * gigabytes de pixeles al abrirlo. Reventaria la memoria al decodificar, no
     * al leer, asi que preguntar el tamano despues de `read()` llega tarde. El
     * lector de ImageIO responde `getWidth`/`getHeight` leyendo solo la cabecera.
     *
     * **Y no basta con que `read` no lance.** Un JPEG cortado por la mitad *no*
     * da error en la JVM: el lector rellena lo que falta, devuelve una imagen del
     * tamano que declaraba la cabecera y se limita a **avisar**. Sin escuchar esos
     * avisos, un fichero roto entraba como bueno; PNG y WebP si lanzan, asi que el
     * agujero era solo de JPEG y por eso no se veia.
     *
     * **Se escuchan los avisos de truncamiento, no todos**, y la diferencia esta
     * medida en `ReaderWarningProbeTest`. Rechazar ante cualquier aviso parecia lo
     * prudente y es peor que el agujero: un JPEG **legitimo** con bytes de relleno
     * antes del EOI --lo que producen varias camaras y varios editores-- avisa
     * «Corrupt JPEG data: N extraneous bytes before marker», se ve perfectamente
     * en cualquier visor, y pasaria a rechazarse con un 415 incomprensible.
     *
     * Lo que si significa que la imagen no llego entera es «premature end» o
     * «Truncated File», y aparece en las dos variantes del ataque: la cortada a
     * secas y la cortada con un EOI pegado detras para disimular --que es lo que
     * derrota a cualquier comprobacion de «¿termina en EOI?».
     *
     * Se compara por texto porque **estos mensajes no se traducen**: vienen tal
     * cual de libjpeg. Medido en tres idiomas. Si algun dia cambiaran, lo que se
     * pone rojo es la prueba de truncamiento, que es justo la alarma que se quiere.
     */
    private fun decode(input: InputStream): BufferedImage? {
        ImageIO.createImageInputStream(input).use { stream ->
            val reader = ImageIO.getImageReaders(stream).asSequence().firstOrNull() ?: return null
            reader.setInput(stream, true, true)

            var incomplete = false
            reader.addIIOReadWarningListener { _, warning ->
                if (TRUNCATION_WARNINGS.any { warning.contains(it, ignoreCase = true) }) {
                    incomplete = true
                    log.debug("El lector avisa de un fichero incompleto, se rechaza: {}", warning)
                } else {
                    log.debug("Aviso benigno del lector, la imagen se acepta igual: {}", warning)
                }
            }

            try {
                val pixels = reader.getWidth(0).toLong() * reader.getHeight(0).toLong()
                if (pixels > maxImagePixels) {
                    throw BusinessRuleViolation(
                        ErrorCode.FILE_TOO_LARGE,
                        "La imagen tiene demasiados píxeles para procesarse",
                    )
                }
                val image = reader.read(0)
                return if (incomplete) null else image
            } catch (failure: IOException) {
                // Dice ser una imagen y no se deja decodificar: no lo es.
                log.debug("Imagen no decodificable, se rechaza", failure)
                return null
            } finally {
                reader.dispose()
            }
        }
    }

    /**
     * El lienzo nuevo sobre el que se pinta, y donde se decide la transparencia.
     *
     * JPEG **no la admite**: escribir una imagen con canal alfa da un fichero
     * corrupto o un fallo del escritor segun la version, asi que se compone sobre
     * blanco. PNG y WebP si, y ahi conservarla importa --un logotipo recortado
     * sobre negro se ve mal en modo claro.
     */
    private fun canvasFor(image: BufferedImage, type: StoredContentType, width: Int, height: Int): BufferedImage {
        val transparency = type != StoredContentType.JPEG && image.colorModel.hasAlpha()
        val canvas = BufferedImage(
            width,
            height,
            if (transparency) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB,
        )
        canvas.createGraphics().apply {
            if (!transparency) {
                color = java.awt.Color.WHITE
                fillRect(0, 0, width, height)
            }
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            drawImage(image, 0, 0, width, height, null)
            dispose()
        }
        return canvas
    }

    /**
     * Reduce **a la mitad cada vez** hasta acercarse al destino, y solo entonces
     * ajusta.
     *
     * De 4 000 px a 320 px en un solo paso, la interpolacion mira una fraccion
     * minima de los pixeles de origen y el resultado sale con dientes de sierra
     * --justo en la rejilla de miniaturas, que es donde mas se nota. Halvar
     * promedia todos los pixeles por el camino.
     */
    private fun scaleDown(image: BufferedImage, longestSide: Int): BufferedImage {
        if (maxOf(image.width, image.height) <= longestSide) return image

        var current = image
        while (maxOf(current.width, current.height) > longestSide * 2) {
            current = canvasFor(
                current,
                StoredContentType.WEBP,
                (current.width / 2).coerceAtLeast(1),
                (current.height / 2).coerceAtLeast(1),
            )
        }

        val ratio = longestSide.toDouble() / maxOf(current.width, current.height)
        return canvasFor(
            current,
            StoredContentType.WEBP,
            (current.width * ratio).toInt().coerceAtLeast(1),
            (current.height * ratio).toInt().coerceAtLeast(1),
        )
    }

    /**
     * Escribe la imagen, con perdida y sin metadatos.
     *
     * Los parametros de compresion se piden al escritor en vez de fijarse por
     * formato: cada uno declara los tipos que sabe, y preguntarselos evita
     * escribir aqui una tabla que envejece con cada libreria. Al escritor se le
     * pasa un `IIOImage` **sin metadatos**, que es lo que garantiza que no salga
     * nada que no sean pixeles.
     */
    private fun encode(image: BufferedImage, type: StoredContentType): ByteArray? {
        val writer = ImageIO.getImageWritersByMIMEType(type.value).asSequence().firstOrNull() ?: return null
        val canvas = canvasFor(image, type, image.width, image.height)
        val output = ByteArrayOutputStream()

        return try {
            ImageIO.createImageOutputStream(output).use { stream ->
                writer.output = stream
                writer.write(null, IIOImage(canvas, null, null), writer.compressionParams())
            }
            output.toByteArray()
        } catch (failure: IOException) {
            log.warn("No se ha podido recodificar a {}", type.value, failure)
            null
        } finally {
            writer.dispose()
        }
    }

    private fun javax.imageio.ImageWriter.compressionParams(): ImageWriteParam = defaultWriteParam.apply {
        if (!canWriteCompressed()) return@apply
        compressionMode = ImageWriteParam.MODE_EXPLICIT
        // El tipo se fija antes que la calidad: con varios tipos declarados,
        // pedir calidad sin haber elegido tipo lanza IllegalStateException.
        compressionTypes?.firstOrNull { it.equals(LOSSY, ignoreCase = true) }?.let { compressionType = it }
        compressionQuality = QUALITY
    }

    private companion object {
        /** Suficiente para una foto de un estante y para un manual escaneado; imperceptible en pantalla. */
        const val QUALITY = 0.85f

        /** Como llama al modo con perdida el escritor de WebP. JPEG no declara tipos, y no pasa nada. */
        const val LOSSY = "Lossy"

        /**
         * Los dos avisos que significan «la imagen no llego entera».
         *
         * Cualquier otro --el de bytes de relleno, sin ir mas lejos-- describe un
         * fichero valido con ruido, y rechazarlo seria rechazar fotos que
         * cualquier visor abre.
         */
        val TRUNCATION_WARNINGS = listOf("premature end", "Truncated File")
    }
}
