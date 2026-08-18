package com.drp.core.adapter.storage

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * Que la JVM de esta maquina sabe leer y escribir WebP.
 *
 * No prueba codigo propio: prueba que la dependencia nativa esta y funciona. Y
 * existe porque **la JVM no trae WebP en ninguna de las dos direcciones**, y las
 * dos hacen falta: `image/webp` esta en la lista blanca de subida --hay que
 * decodificarlo para recodificarlo-- y la miniatura se escribe en WebP (5.8.3).
 *
 * Si esto falla en un runner nuevo, el sintoma en el resto de la suite seria una
 * miniatura que no se genera y un fichero rechazado sin motivo aparente. Aqui
 * falla con nombre y apellidos.
 */
class WebpCodecTest {

    @Test
    fun `el plugin de WebP se registra en ImageIO`() {
        ImageIO.getImageWritersByMIMEType("image/webp").hasNext() shouldBe true
        ImageIO.getImageReadersByMIMEType("image/webp").hasNext() shouldBe true
    }

    @Test
    fun `una imagen escrita en WebP se vuelve a leer con sus dimensiones`() {
        val original = BufferedImage(64, 32, BufferedImage.TYPE_INT_RGB).apply {
            for (x in 0 until width) for (y in 0 until height) setRGB(x, y, 0x3F7A5C)
        }

        val encoded = ByteArrayOutputStream().also { ImageIO.write(original, "webp", it) }.toByteArray()

        // El contenedor RIFF/WEBP, comprobado sobre los bytes y no sobre lo que
        // diga la libreria: "RIFF" ....  "WEBP".
        String(encoded.copyOfRange(0, 4), Charsets.US_ASCII) shouldBe "RIFF"
        String(encoded.copyOfRange(8, 12), Charsets.US_ASCII) shouldBe "WEBP"

        val decoded = ImageIO.read(encoded.inputStream())
        decoded shouldNotBe null
        decoded.width shouldBe 64
        decoded.height shouldBe 32
    }
}
