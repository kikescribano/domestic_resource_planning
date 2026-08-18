package com.drp.core.application.usecase

import com.drp.core.application.StorageKeys
import com.drp.core.application.port.ContentTooLarge
import com.drp.core.application.port.FileContentProcessor
import com.drp.core.application.port.FileStorage
import com.drp.core.application.port.HouseholdRepository
import com.drp.platform.page.Page
import com.drp.platform.page.Pagination
import com.drp.core.application.port.SessionClaims
import com.drp.core.application.port.StoredFileFilter
import com.drp.core.application.port.StoredFileRepository
import com.drp.platform.error.BusinessRuleViolation
import com.drp.platform.error.ErrorCode
import com.drp.platform.error.ResourceNotFound
import com.drp.core.domain.file.StoredContentType
import com.drp.core.domain.file.StoredFile
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.io.InputStream
import java.time.Clock
import java.util.UUID

/**
 * Los dos limites de tamano, en un solo sitio.
 *
 * El del hogar **no es un `CHECK`** porque no es una propiedad de una fila sino
 * una suma sobre todas las vivas; el del fichero si lo es, y esta ademas en la
 * tabla. Aqui viven los dos juntos porque quien pregunta --el frontend, por
 * `GET /storage`-- los necesita a la vez para avisar antes de que una subida
 * falle, en lugar de despues.
 */
data class StoragePolicy(val quotaBytes: Long, val maxFileBytes: Long)

data class StorageUsage(val usedBytes: Long, val policy: StoragePolicy)

/**
 * De donde salen los bytes de una subida, sin que el caso de uso sepa que hay un
 * multipart debajo.
 *
 * La separacion en dos tiempos **es el diseno, no una comodidad**: [declaredBytes]
 * se conoce sin haber leido el cuerpo --es el `Content-Length` de la peticion-- y
 * [receive] es lo que dispara la recepcion de verdad. Eso permite reservar la
 * cuota antes de transmitir, que es lo que hace que el bloqueo dure milisegundos
 * en vez de toda la subida (5.8.3).
 */
interface UploadSource {
    val declaredBytes: Long

    fun receive(): ReceivedFile
}

class ReceivedFile(val originalName: String, val content: InputStream)

/**
 * Sube un fichero (`UploadFile`, 5.7).
 *
 * Son **tres transacciones cortas y un tramo largo sin ninguna**, y ese reparto
 * es el motivo de que el caso de uso no lleve `@Transactional` en el metodo:
 *
 * 1. **Reserva.** Se bloquea la fila del hogar, se suma lo vivo mas el tamano
 *    declarado y, si cabe, se inserta la fila con `uploadedAt` a nulo. Confirmar
 *    y soltar. Dura milisegundos.
 * 2. **Recepcion, validacion y escritura**, ya sin ningun bloqueo.
 * 3. **Cierre de la fila** con el tamano real, el checksum, el tipo detectado y
 *    `uploadedAt`.
 *
 * **Por que la reserva y no comprobar la cuota y ya.** Si el bloqueo se tomara
 * antes de transmitir y se soltara al confirmar, duraria toda la subida: un
 * fichero de 25 MB por una conexion mala dejaria al hogar entero sin poder subir
 * nada durante un minuto. El precio es una fila a medias mientras dura la subida,
 * que es exactamente lo que `uploadedAt` a nulo significa.
 *
 * El tamano real solo puede ser **menor** que el reservado --recodificar encoge y
 * la recepcion aborta si se pasa--, asi que cerrar la fila nunca aumenta lo
 * consumido: solo devuelve lo que sobraba.
 */
@Service
class UploadFile(
    private val households: HouseholdRepository,
    private val files: StoredFileRepository,
    private val storage: FileStorage,
    private val processor: FileContentProcessor,
    private val transactions: TransactionTemplate,
    private val policy: StoragePolicy,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun handle(session: SessionClaims, upload: UploadSource): StoredFile {
        val reservation = transactions.execute { reserve(session, upload.declaredBytes) }!!

        return try {
            complete(reservation, upload)
        } catch (failure: Throwable) {
            // La reserva se suelta en el acto en vez de dejarsela al proceso
            // diario: quien acaba de ver fallar su subida no tiene por que
            // esperar una hora para recuperar su propio hueco. El proceso diario
            // sigue siendo la red de seguridad para lo que se corta sin llegar
            // hasta aqui --la conexion que se cae, el proceso que muere.
            release(reservation)
            throw failure
        }
    }

    /**
     * La transaccion corta que decide si cabe.
     *
     * **El cerrojo se toma antes de leer**, no entre la lectura y la escritura.
     * Es la unica posicion que sirve: bloquear despues de sumar deja abierta justo
     * la ventana que pretende cerrar, y dos subidas simultaneas contra el final de
     * la cuota pasarian las dos.
     */
    private fun reserve(session: SessionClaims, declaredBytes: Long): Reservation {
        households.lockCurrent()

        // Nunca por encima del tope por fichero: el contenedor de servlets ya
        // corto cualquier peticion mayor, pero el `Content-Length` incluye la
        // envoltura del multipart y podria pasarse por unos bytes del `CHECK` de
        // la tabla. Acotarlo sigue siendo una cota superior valida de lo que
        // acabara ocupando.
        //
        // Sin `Content-Length` --una peticion troceada-- se reserva el maximo:
        // conservador y correcto, porque el cierre solo puede devolver.
        val reserved = if (declaredBytes <= 0) policy.maxFileBytes else minOf(declaredBytes, policy.maxFileBytes)

        val used = files.usedBytes()
        if (used + reserved > policy.quotaBytes) {
            throw BusinessRuleViolation(
                ErrorCode.STORAGE_QUOTA_EXCEEDED,
                "El hogar ha agotado su espacio de almacenamiento",
            )
        }

        val fileId = UUID.randomUUID()
        val key = StorageKeys.original(session.householdId, fileId)

        // Mientras `uploadedAt` sea nulo, el nombre, el tipo y el checksum son
        // **provisionales**: no se conocen hasta haber recibido los bytes, y la
        // fila tiene que existir ya para que la cuota este reservada. El tipo va a
        // uno cualquiera de la lista blanca porque la columna tiene un `CHECK` que
        // no admite un valor centinela, y el nombre y el checksum vacios porque
        // nada los puede leer todavia.
        files.save(
            StoredFile(
                id = fileId,
                originalName = "",
                contentType = StoredContentType.PDF,
                sizeBytes = reserved,
                checksum = "",
                storageKey = key,
                createdAt = clock.instant(),
                createdBy = session.memberId,
                uploadedAt = null,
                deletedAt = null,
            ),
        )

        return Reservation(fileId, key, reserved)
    }

    /** El tramo largo: recibir, validar, recodificar y escribir. Sin ningun bloqueo tomado. */
    private fun complete(reservation: Reservation, upload: UploadSource): StoredFile {
        val received = upload.receive()

        val staged = try {
            received.content.use { storage.stage(it, reservation.reservedBytes) }
        } catch (tooLarge: ContentTooLarge) {
            // El cliente mintio en el `Content-Length`. Lo que se ha superado es
            // lo reservado, y por eso se detecta contando y no creyendo.
            throw BusinessRuleViolation(ErrorCode.FILE_TOO_LARGE, "El fichero supera el tamaño máximo permitido")
                .also { log.debug("Subida abortada al pasar de {} bytes", tooLarge.maxBytes) }
        }

        return staged.use {
            val processed = processor.process(staged::open)
                ?: throw BusinessRuleViolation(
                    ErrorCode.FILE_TYPE_NOT_ALLOWED,
                    "El tipo real del fichero no está admitido",
                )

            val written = processed.openContent().use { storage.write(reservation.key, it) }
            processed.openThumbnail?.let { open ->
                open().use { storage.write(StorageKeys.thumbnailOf(reservation.key), it) }
            }

            transactions.execute { closeRow(reservation, received.originalName, processed.contentType, written) }!!
        }
    }

    private fun closeRow(
        reservation: Reservation,
        originalName: String,
        contentType: StoredContentType,
        written: com.drp.core.application.port.WrittenBytes,
    ): StoredFile {
        val reserved = files.findById(reservation.fileId)
            ?: error("La reserva ${reservation.fileId} ha desaparecido mientras se subia")

        return files.save(
            reserved.copy(
                originalName = sanitise(originalName),
                contentType = contentType,
                sizeBytes = written.sizeBytes,
                checksum = written.checksum,
                uploadedAt = clock.instant(),
            ),
        )
    }

    /**
     * Suelta la reserva y los bytes que hubiera llegado a escribir.
     *
     * Va en su **propia transaccion** y traga cualquier fallo: se ejecuta
     * limpiando detras de un error, y un fallo aqui taparia el error original,
     * que es el que hay que contar.
     */
    private fun release(reservation: Reservation) {
        runCatching {
            storage.delete(reservation.key)
            storage.delete(StorageKeys.thumbnailOf(reservation.key))
            transactions.execute { files.delete(reservation.fileId) }
        }.onFailure { log.warn("No se ha podido soltar la reserva {}", reservation.fileId, it) }
    }

    /**
     * El nombre original **es solo un dato**: nunca forma parte de la ruta en
     * disco. Aun asi se sanea, porque acaba en una cabecera `Content-Disposition`
     * y en la interfaz.
     */
    private fun sanitise(name: String): String = name
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .filter { it.code >= MIN_PRINTABLE }
        .trim()
        .take(MAX_NAME_LENGTH)
        .ifEmpty { "fichero" }

    private data class Reservation(val fileId: UUID, val key: String, val reservedBytes: Long)

    private companion object {
        /** Fuera los caracteres de control, incluido el byte nulo. */
        const val MIN_PRINTABLE = 0x20

        const val MAX_NAME_LENGTH = 255
    }
}

@Service
class ListFiles(private val files: StoredFileRepository) {

    @Transactional(readOnly = true)
    fun handle(filter: StoredFileFilter, pagination: Pagination): Page<StoredFile> = files.list(filter, pagination)
}

@Service
class GetFile(private val files: StoredFileRepository) {

    /**
     * Una reserva a medias no existe para quien pregunta: no tiene bytes, no se
     * puede adjuntar y su nombre y su tipo todavia son provisionales.
     */
    @Transactional(readOnly = true)
    fun handle(fileId: UUID): StoredFile =
        files.findById(fileId)?.takeIf { it.isUsable } ?: throw ResourceNotFound("Fichero no encontrado")
}

/**
 * Retira un fichero (`DeleteFile`, 5.7).
 *
 * Marca la fila y **libera la cuota en el acto**; los bytes los desenlaza el
 * proceso diario hasta 24 h despues. Ese margen no es una funcion de deshacer
 * --no hay ningun gesto que restaure lo borrado-- sino la ventana en la que un
 * operador todavia puede recuperar un borrado por error sin ir a la copia de
 * seguridad.
 */
@Service
class DeleteFile(
    private val files: StoredFileRepository,
    private val clock: Clock,
) {

    @Transactional
    fun handle(fileId: UUID) {
        val file = files.findById(fileId)?.takeIf { it.isUsable }
            ?: throw ResourceNotFound("Fichero no encontrado")

        // Solo sobre ficheros que no cuelgan de nada: primero se desadjunta. Si
        // se pudiera borrar lo adjunto, un documento se quedaria apuntando a unos
        // bytes que el proceso diario va a desenlazar.
        if (files.isAttached(fileId)) {
            throw BusinessRuleViolation(
                ErrorCode.FILE_IN_USE,
                "Un documento o una foto todavía referencian el fichero",
            )
        }

        files.save(file.copy(deletedAt = clock.instant()))
    }
}

@Service
class GetStorageUsage(
    private val files: StoredFileRepository,
    private val policy: StoragePolicy,
) {

    @Transactional(readOnly = true)
    fun handle(): StorageUsage = StorageUsage(files.usedBytes(), policy)
}

/**
 * Marca un fichero para su retirada **desde dentro de otra transaccion**.
 *
 * Es lo que necesita `DeleteDocument`, que tiene que soltar el fichero en la
 * misma transaccion que borra el documento, y no puede pasar por [DeleteFile]
 * porque ahi el fichero todavia esta adjunto --al documento que se esta
 * borrando-- y responderia `FILE_IN_USE`.
 */
@Service
class ReleaseFile(
    private val files: StoredFileRepository,
    private val clock: Clock,
) {

    @Transactional(propagation = Propagation.MANDATORY)
    fun handle(fileId: UUID) {
        val file = files.findById(fileId) ?: return
        if (file.deletedAt != null) return
        files.save(file.copy(deletedAt = clock.instant()))
    }
}
