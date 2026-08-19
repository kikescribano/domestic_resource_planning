package com.drp.core.adapter.storage

import com.drp.core.application.port.ContentTooLarge
import com.drp.core.application.port.FileStorage
import com.drp.core.application.port.StagedContent
import com.drp.core.application.port.WrittenBytes
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.DigestOutputStream
import java.security.MessageDigest

/**
 * El almacen sobre el sistema de ficheros del servidor (ADR-005).
 *
 * La raiz apunta a un **volumen separado** del sistema operativo y de PostgreSQL,
 * montado con `noexec,nodev,nosuid` y fuera de cualquier arbol que sirva el
 * servidor web. La separacion no es cosmetica: sin ella, un disco lleno de fotos
 * no degrada las subidas, sino que tumba la base de datos y con ella la
 * aplicacion entera. Con ella, llenarlo solo impide subir mas.
 *
 * Montar el volumen es cosa del despliegue; lo que este adaptador si garantiza es
 * lo que puede: que ninguna clave se salga de la raiz, y que bajo la clave
 * definitiva no llegue a existir un fichero a medias.
 */
@Component
class FilesystemFileStorage(
    @Value("\${drp.storage.root}") root: String,
) : FileStorage {

    private val root: Path = Path.of(root).toAbsolutePath().normalize()
    private val staging: Path = this.root.resolve(STAGING)

    init {
        Files.createDirectories(staging)
    }

    override fun stage(source: InputStream, maxBytes: Long): StagedContent {
        val temporary = Files.createTempFile(staging, "upload-", ".part")
        return runCatching {
            val written = Files.newOutputStream(temporary).use { source.copyCapped(it, maxBytes) }
            TemporaryFile(temporary, written)
        }.getOrElse { failure ->
            Files.deleteIfExists(temporary)
            throw failure
        }
    }

    /**
     * Escribe a un temporal y **mueve despues**.
     *
     * El movimiento dentro del mismo sistema de ficheros es atomico, asi que bajo
     * la clave definitiva o no hay nada o esta el fichero entero. Sin ese rodeo,
     * una subida cortada dejaria en el destino un fichero truncado con la fila
     * todavia sin cerrar --y el siguiente intento lo daria por bueno.
     *
     * El SHA-256 se calcula **al escribir** y no releyendo despues: releer
     * duplicaria la lectura y, sobre todo, mediria un fichero que ya no es
     * necesariamente el que se escribio.
     */
    override fun write(key: String, source: InputStream): WrittenBytes {
        val destination = resolve(key)
        Files.createDirectories(destination.parent)
        allowServerToRead(destination.parent, DIRECTORY_PERMISSIONS)

        val temporary = Files.createTempFile(staging, "write-", ".part")
        val digest = MessageDigest.getInstance(DIGEST)

        return runCatching {
            val size = Files.newOutputStream(temporary).use { out ->
                DigestOutputStream(out, digest).use { source.copyTo(it) }
            }
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING)
            allowServerToRead(destination, FILE_PERMISSIONS)
            WrittenBytes(size, digest.digest().toHex())
        }.getOrElse { failure ->
            Files.deleteIfExists(temporary)
            throw failure
        }
    }

    /**
     * Deja el fichero legible para **el servidor web**, que no corre como la
     * aplicacion.
     *
     * No es cosmetica de permisos: quien entrega los bytes es nginx (5.8.4), y un
     * fichero escrito solo para su dueno le da un `403` --con la firma correcta y
     * la fila correcta--. Y `createTempFile` crea justo eso, `rw-------`, porque
     * un temporal del sistema tiene que ser privado; al mover, el fichero
     * definitivo hereda esos permisos.
     *
     * Se descubrio en la CI y no en local: en Docker Desktop sobre Windows el
     * montaje ignora los permisos POSIX, asi que la prueba de nginx pasaba aqui y
     * fallaba alli. En un despliegue de verdad habria fallado igual que en la CI.
     *
     * En un sistema sin POSIX no hay nada que hacer y no es un error: se ignora.
     */
    private fun allowServerToRead(path: Path, permissions: String) {
        runCatching { Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions)) }
    }

    override fun openRead(key: String): InputStream? {
        val path = resolve(key)
        return if (Files.isRegularFile(path)) Files.newInputStream(path) else null
    }

    override fun delete(key: String) {
        Files.deleteIfExists(resolve(key))
    }

    /**
     * Borra el subarbol entero, de las hojas hacia la raiz.
     *
     * El recorrido va en orden inverso --`sorted().reversed()` sobre las rutas--
     * porque un directorio no se borra con algo dentro. Se cuentan solo los
     * ficheros: lo que la baja de un hogar promete es que no queda un byte suyo,
     * y un directorio vacio de mas no es un byte suyo.
     *
     * Tolera que el prefijo no exista, que es el caso de un hogar que nunca
     * subio nada, y no se inventa un error por ello. Y pasa por [resolve] igual
     * que cualquier clave: es la valla que impide que un prefijo se salga de la
     * raiz --aqui con mas motivo que en ningun sitio, porque lo que se borra no
     * es un fichero sino todo lo que cuelgue.
     */
    override fun deleteTree(prefix: String): Int {
        val root = resolve(prefix)
        if (!Files.isDirectory(root)) return 0

        var files = 0
        Files.walk(root).use { walk ->
            walk.sorted().toList().reversed().forEach { path ->
                if (Files.isRegularFile(path)) files++
                Files.deleteIfExists(path)
            }
        }
        return files
    }

    /**
     * La ultima valla contra una clave que se salga de la raiz.
     *
     * Hoy todas las claves las genera `StorageKeys` a partir de identificadores,
     * asi que no deberia poder llegar aqui ninguna con `..` dentro. Se comprueba
     * igual: el dia que alguien construya una clave con algo que venga del
     * cliente, esto falla ruidosamente en lugar de escribir donde no debe.
     */
    private fun resolve(key: String): Path {
        val resolved = root.resolve(key).normalize()
        require(resolved.startsWith(root) && !resolved.startsWith(staging)) {
            "Clave de almacenamiento fuera de la raiz: $key"
        }
        return resolved
    }

    /**
     * Copia contando, y **aborta en cuanto se pasa** en lugar de copiar entero y
     * medir despues: medir despues significa haber escrito ya en disco todo lo
     * que se pretendia rechazar.
     */
    private fun InputStream.copyCapped(target: OutputStream, maxBytes: Long): Long {
        val buffer = ByteArray(BUFFER_BYTES)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) return total
            total += read
            if (total > maxBytes) throw ContentTooLarge(maxBytes)
            target.write(buffer, 0, read)
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private class TemporaryFile(private val path: Path, override val sizeBytes: Long) : StagedContent {

        override fun open(): InputStream = Files.newInputStream(path)

        override fun close() {
            Files.deleteIfExists(path)
        }
    }

    private companion object {
        /**
         * Los temporales viven **dentro** de la raiz para que el movimiento al
         * destino no cruce sistemas de ficheros --si lo cruzara, dejaria de ser
         * atomico y volveria a existir el fichero a medias.
         */
        const val STAGING = ".staging"

        const val DIGEST = "SHA-256"
        const val BUFFER_BYTES = 16 * 1024

        /**
         * Legible por el servidor web, escribible solo por la aplicacion. El
         * volumen va montado `noexec`, asi que el bit de ejecucion no aporta
         * nada ni aqui ni en los directorios.
         */
        const val FILE_PERMISSIONS = "rw-r--r--"
        const val DIRECTORY_PERMISSIONS = "rwxr-xr-x"
    }
}
