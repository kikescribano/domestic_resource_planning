package com.drp.core.adapter.storage

import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * Sonda: **que avisa de verdad el lector de JPEG de la JVM**.
 *
 * No comprueba producto. Existe porque rechazar un fichero por el hecho de que
 * el lector avise es una decision con un riesgo concreto en la otra direccion:
 * si un JPEG **legitimo pero con ruido** avisa, la aplicacion pasaria a rechazar
 * fotos que cualquier visor abre sin pestanear, y el usuario solo veria un 415
 * incomprensible.
 *
 * El caso que hay que medir no es el truncado --ese ya esta medido-- sino el
 * benigno mas comun del mundo real: **bytes de relleno antes de un marcador**,
 * que libjpeg reporta como «extraneous bytes before marker» y que producen
 * varias camaras y varios editores. Si eso aviso, el rechazo por aviso es
 * demasiado ancho.
 */
class ReaderWarningProbeTest {

    @Test
    fun `que avisa el lector ante ruido benigno y ante truncamiento`() {
        val clean = jpeg()

        // ¿Se traducen los mensajes? De la respuesta depende si se puede
        // distinguir un aviso de otro por su texto o hay que buscar otra senal.
        val original = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.JAPANESE)
            report("[ja] JPEG truncado a la mitad", clean.copyOf(clean.size / 2))
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("es-ES"))
            report("[es] JPEG truncado a la mitad", clean.copyOf(clean.size / 2))
        } finally {
            java.util.Locale.setDefault(original)
        }

        report("JPEG limpio", clean)
        report("JPEG con bytes de relleno antes del EOI", withPaddingBeforeEoi(clean))
        report("JPEG con basura DETRAS del EOI", clean + ByteArray(64) { 0x41 })
        report("JPEG truncado a la mitad", clean.copyOf(clean.size / 2))
        report("JPEG truncado con un EOI pegado detras", clean.copyOf(clean.size / 2) + byteArrayOf(-1, -39))
    }

    /** Imprime si decodifica, si avisa y con que. La salida va al informe de la prueba. */
    private fun report(label: String, bytes: ByteArray) {
        val warnings = mutableListOf<String>()
        var decoded = false
        var failure: String? = null

        ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { stream ->
            val reader = ImageIO.getImageReaders(stream).asSequence().firstOrNull()
            if (reader == null) {
                println("SONDA | $label -> sin lector")
                return
            }
            reader.setInput(stream, true, true)
            reader.addIIOReadWarningListener { _, warning -> warnings += warning }
            try {
                reader.read(0)
                decoded = true
            } catch (error: Exception) {
                failure = error.javaClass.simpleName + ": " + error.message
            } finally {
                reader.dispose()
            }
        }

        println("SONDA | $label -> decodifica=$decoded avisos=${warnings.size} $warnings ${failure ?: ""}")
    }

    /**
     * Mete bytes de relleno **dentro del flujo comprimido**, justo antes del EOI.
     *
     * Es la forma de reproducir el «extraneous bytes before marker» que aparece en
     * ficheros reales sin que la imagen deje de verse.
     */
    private fun withPaddingBeforeEoi(jpeg: ByteArray): ByteArray {
        val eoi = jpeg.size - 2
        return jpeg.copyOfRange(0, eoi) + ByteArray(8) { 0x00 } + jpeg.copyOfRange(eoi, jpeg.size)
    }

    private fun jpeg(): ByteArray {
        val image = BufferedImage(120, 90, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until 120) {
            for (y in 0 until 90) image.setRGB(x, y, Color(x * 2 % 256, y * 3 % 256, (x + y) % 256).rgb)
        }
        return ByteArrayOutputStream().also { ImageIO.write(image, "jpeg", it) }.toByteArray()
    }
}
