package com.drp.core.adapter.storage

import com.drp.core.domain.file.StoredContentType

/**
 * Que es un fichero **segun sus bytes**, no segun lo que declare quien lo sube.
 *
 * Es el control mas repetido de la File Upload Cheat Sheet de OWASP y el que mas
 * se implementa mal: mirar la extension o el `Content-Type` de la peticion es
 * preguntarle al atacante que ha subido. Aqui no se mira ninguno de los dos.
 *
 * Y es solo **la mitad** de la comprobacion. Estas firmas dicen que el fichero
 * empieza como un JPEG, no que sea un JPEG: lo segundo solo lo demuestra
 * decodificarlo entero, que es lo que hace el paso siguiente. Un fichero con
 * cabecera valida y contenido roto pasa por aqui y muere al recodificar.
 *
 * **Con una excepcion que conviene tener presente: el PDF no se decodifica.** No
 * hay lector de PDF en el camino, asi que de un `application/pdf` esta firma es
 * la unica comprobacion que se hace, y sus bytes se guardan tal cual. Lo que
 * compensa eso no esta aqui sino en la entrega (5.8.4): nunca incrustado,
 * siempre como adjunto, con `nosniff` y desde otro dominio.
 *
 * Lista blanca, nunca lista negra. Las firmas son las de los cuatro tipos
 * admitidos y no hay caso por defecto: lo que no reconoce, se rechaza.
 */
object ContentSniffer {

    /** Con menos de esto no se puede decidir nada; ningun fichero util es tan corto. */
    const val HEAD_BYTES = 32

    fun detect(head: ByteArray): StoredContentType? = when {
        head.startsWith(JPEG) -> StoredContentType.JPEG
        head.startsWith(PNG) -> StoredContentType.PNG
        // WebP es un contenedor RIFF: "RIFF", cuatro bytes de tamano, y "WEBP".
        // Comprobar solo "RIFF" aceptaria tambien un WAV o un AVI.
        head.startsWith(RIFF) && head.startsWith(WEBP, offset = 8) -> StoredContentType.WEBP
        // Estricto en el offset 0. La especificacion admite basura delante, y
        // varios lectores la toleran; aceptarla aqui es justamente lo que permite
        // un poliglota que sea PDF para el visor y otra cosa para quien lo sirva.
        head.startsWith(PDF) -> StoredContentType.PDF
        else -> null
    }

    private fun ByteArray.startsWith(signature: ByteArray, offset: Int = 0): Boolean {
        if (size < offset + signature.size) return false
        return signature.indices.all { this[offset + it] == signature[it] }
    }

    private val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
    private val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private val RIFF = "RIFF".toByteArray(Charsets.US_ASCII)
    private val WEBP = "WEBP".toByteArray(Charsets.US_ASCII)
    private val PDF = "%PDF-".toByteArray(Charsets.US_ASCII)
}
