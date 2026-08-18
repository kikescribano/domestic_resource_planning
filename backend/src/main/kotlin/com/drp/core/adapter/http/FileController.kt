package com.drp.core.adapter.http

import com.drp.core.application.port.DocumentFilter
import com.drp.core.application.port.FileStorage
import com.drp.platform.page.Pagination
import com.drp.core.application.port.SessionClaims
import com.drp.core.application.port.StoredFileFilter
import com.drp.core.application.usecase.AttachDocument
import com.drp.core.application.usecase.DeleteDocument
import com.drp.core.application.usecase.DeleteFile
import com.drp.core.application.usecase.GetFile
import com.drp.core.application.usecase.GetStorageUsage
import com.drp.core.application.usecase.ListDocuments
import com.drp.core.application.usecase.ListFiles
import com.drp.core.application.usecase.ReceivedFile
import com.drp.core.application.usecase.UploadFile
import com.drp.core.application.usecase.UploadSource
import com.drp.core.domain.ResourceNotFound
import com.drp.core.domain.ValidationFailure
import com.drp.core.domain.file.DocumentType
import com.drp.core.domain.file.StoredContentType
import com.drp.core.domain.file.StoredFile
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.io.InputStreamResource
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartHttpServletRequest
import java.nio.charset.StandardCharsets
import java.util.UUID

@RestController
@RequestMapping("/api/v1/files")
class FileController(
    private val listFiles: ListFiles,
    private val getFile: GetFile,
    private val uploadFile: UploadFile,
    private val deleteFile: DeleteFile,
    private val storage: FileStorage,
    private val urls: SignedFileUrls,
    private val uploadLimiter: UploadRateLimiter,
    @Value("\${drp.files.accel-redirect}") private val accelRedirect: Boolean,
    @Value("\${drp.files.accel-prefix}") private val accelPrefix: String,
) {

    @GetMapping
    fun list(
        @RequestParam(required = false) attached: Boolean?,
        @RequestParam(required = false) type: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): PageResponse<StoredFileResponse> = PageResponse.of(
        listFiles.handle(StoredFileFilter(attached, type?.let(::contentTypeOrReject)), Pagination(page, size)),
    ) { StoredFileResponse.of(it, urls) }

    /**
     * Sube un fichero.
     *
     * **La peticion se recibe en dos tiempos, y ese es el diseno.** Con
     * `resolve-lazily` activado, entrar aqui no ha parseado todavia el multipart:
     * lo que hay es la cabecera. Eso permite reservar la cuota contra el
     * `Content-Length` --con la fila del hogar bloqueada durante milisegundos-- y
     * solo despues empezar a recibir. Pedir el fichero como `@RequestParam`
     * invertiria el orden sin que se notara: Spring parsearia el cuerpo entero
     * antes de que este metodo existiera, y la reserva pasaria a ocurrir con los
     * 25 MB ya en disco.
     *
     * El limite de frecuencia va **por identidad** y no por IP: aqui ya se sabe
     * quien es, y una casa entera comparte IP.
     */
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun upload(
        @AuthenticationPrincipal session: SessionClaims,
        request: MultipartHttpServletRequest,
    ): StoredFileResponse {
        uploadLimiter.consume(session.identityId)
        return StoredFileResponse.of(uploadFile.handle(session, MultipartUpload(request)), urls)
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): StoredFileResponse = StoredFileResponse.of(getFile.handle(id), urls)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: UUID) = deleteFile.handle(id)

    /**
     * El camino de los **documentos** y de cualquier descarga explicita: la
     * aplicacion comprueba el hogar **en cada peticion** y delega los bytes en el
     * servidor web.
     *
     * Una imagen no pasa por aqui para mostrarse --se pinta con la URL firmada que
     * ya trae la entidad-- y el reparto es de proporcion: lo que pierde la
     * comprobacion por peticion durante quince minutos es una foto de un estante,
     * ya recodificada y sin EXIF; lo que conserva es la factura con nombre y
     * direccion.
     */
    @GetMapping("/{id}/content")
    fun download(@PathVariable id: UUID): ResponseEntity<InputStreamResource> {
        val file = getFile.handle(id)
        val headers = downloadHeaders(file)

        if (accelRedirect) {
            // nginx sustituye el cuerpo por el fichero de la ruta interna y
            // conserva estas cabeceras. La ruta es interna de verdad --`internal`
            // en su `location`--, asi que nadie puede pedirla desde fuera.
            return ResponseEntity.ok()
                .headers(headers)
                .header(ACCEL_REDIRECT, "$accelPrefix/${file.storageKey}")
                .build()
        }

        // Sin proxy delante, el mismo endpoint transmite los bytes (5.8.4). Si la
        // fila esta y los bytes no, la base de datos y el disco han divergido:
        // 404 y no 500, porque para quien pregunta el fichero no esta.
        val content = storage.openRead(file.storageKey)
            ?: throw ResourceNotFound("Fichero no encontrado")

        return ResponseEntity.ok()
            .headers(headers)
            .contentLength(file.sizeBytes)
            .body(InputStreamResource(content))
    }

    /**
     * Las tres que no son opcionales en ninguno de los dos caminos, mas la de
     * `Referer`.
     *
     * El `attachment` **no impide** que un `<img>` pinte la imagen: el navegador
     * solo lo honra en navegaciones y descargas, no en subrecursos. Es decir, se
     * puede exigir siempre sin romper nada.
     */
    private fun downloadHeaders(file: StoredFile) = HttpHeaders().apply {
        contentType = MediaType.parseMediaType(file.contentType.value)
        contentDisposition = ContentDisposition.attachment()
            // RFC 6266: el nombre va ademas en `filename*` codificado, para que
            // una tilde o un espacio no lo trunquen ni lo rompan.
            .filename(file.originalName, StandardCharsets.UTF_8)
            .build()
        set(CONTENT_SECURITY_POLICY, CSP)
        set(X_CONTENT_TYPE_OPTIONS, NOSNIFF)
        set(REFERRER_POLICY, NO_REFERRER)
    }

    private fun contentTypeOrReject(value: String): StoredContentType =
        StoredContentType.from(value) ?: throw IllegalArgumentException("Tipo de contenido no admitido: $value")

    private companion object {
        const val ACCEL_REDIRECT = "X-Accel-Redirect"
        const val NOSNIFF = "nosniff"
        const val NO_REFERRER = "no-referrer"
        const val CSP = "default-src 'none'; sandbox"
    }
}

/**
 * Las dos cabeceras que Spring no declara como constante.
 *
 * `Content-Security-Policy: default-src 'none'; sandbox` y
 * `X-Content-Type-Options: nosniff` no son opcionales en ninguno de los dos
 * caminos de entrega (5.8.4). `Referrer-Policy: no-referrer` va con ellas para
 * que la firma de una URL no viaje en la cabecera `Referer` de ninguna
 * navegacion posterior: sin ella, el propio navegador filtra la credencial al
 * siguiente sitio que se visite.
 */
internal const val CONTENT_SECURITY_POLICY = "Content-Security-Policy"
internal const val X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options"
internal const val REFERRER_POLICY = "Referrer-Policy"

@RestController
@RequestMapping("/api/v1/storage")
class StorageController(private val usage: GetStorageUsage) {

    @GetMapping
    fun get(): StorageUsageResponse = StorageUsageResponse.of(usage.handle())
}

@RestController
@RequestMapping("/api/v1/documents")
class DocumentController(
    private val listDocuments: ListDocuments,
    private val attachDocument: AttachDocument,
    private val deleteDocument: DeleteDocument,
) {

    @GetMapping
    fun list(
        @RequestParam(required = false) assetId: UUID?,
        @RequestParam(required = false) articleId: UUID?,
        @RequestParam(required = false) type: DocumentType?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): PageResponse<DocumentResponse> = PageResponse.of(
        listDocuments.handle(DocumentFilter(assetId, articleId, type), Pagination(page, size)),
        DocumentResponse::of,
    )

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun attach(
        @AuthenticationPrincipal session: SessionClaims,
        @Valid @RequestBody input: DocumentInput,
    ): DocumentResponse = DocumentResponse.of(attachDocument.handle(session, input.toCommand()))

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: UUID) = deleteDocument.handle(id)
}

/**
 * Sirve los ficheros firmados **cuando no hay nginx delante**.
 *
 * En un despliegue de verdad esto no existe: los bytes los sirve nginx desde otro
 * dominio y esta ruta no llega nunca a la aplicacion. En desarrollo si hace
 * falta, porque sin ella una miniatura no se pinta y el recorrido vertical no se
 * puede hacer.
 *
 * Va detras de una condicion y no de un perfil a proposito: el dia que se
 * despliegue con nginx, dejar esto activado seria una **segunda puerta a los
 * bytes**, en el dominio de la aplicacion y compartiendo origen con la sesion,
 * que es exactamente lo que la ADR-005 separa. Se apaga con
 * `drp.files.accel-redirect`, la misma propiedad que enciende la delegacion:
 * asi no hay forma de tener las dos cosas a la vez.
 */
@RestController
@ConditionalOnProperty(name = ["drp.files.accel-redirect"], havingValue = "false", matchIfMissing = true)
class LocalSignedFileController(
    private val storage: FileStorage,
    private val urls: SignedFileUrls,
) {

    @GetMapping("${SignedFileUrls.PREFIX}/**")
    fun serve(
        request: HttpServletRequest,
        @RequestParam(name = "e", required = false) expires: String?,
        @RequestParam(name = "s", required = false) signature: String?,
    ): ResponseEntity<InputStreamResource> {
        val path = request.requestURI
        if (!urls.verify(path, expires, signature)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build()

        // La clave sale de la ruta ya verificada por la firma, y aun asi el
        // almacen comprueba que no se sale de su raiz: dos vallas para lo mismo,
        // porque esto es lo unico de la aplicacion que atiende sin autenticar.
        val key = path.removePrefix("${SignedFileUrls.PREFIX}/")
        val content = storage.openRead(key) ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok()
            .header(CONTENT_SECURITY_POLICY, "default-src 'none'; sandbox")
            .header(X_CONTENT_TYPE_OPTIONS, "nosniff")
            .header(REFERRER_POLICY, "no-referrer")
            .body(InputStreamResource(content))
    }
}

/**
 * La peticion multipart, vista como fuente de bytes.
 *
 * `declaredBytes` **no parsea nada**: es la cabecera de la peticion. `receive()`
 * si, y por eso se invoca despues de la reserva de cuota. Ese reparto en dos
 * tiempos es lo que hace posible reservar antes de transmitir (5.8.3).
 *
 * La usan los dos sitios que reciben un fichero --la subida al hogar y el avatar
 * de una identidad-- porque el camino es el mismo aunque lo que hagan despues no
 * lo sea.
 */
internal class MultipartUpload(private val request: MultipartHttpServletRequest) : UploadSource {

    override val declaredBytes: Long get() = request.contentLengthLong

    override fun receive(): ReceivedFile {
        val part = request.getFile(FIELD)
            ?: throw ValidationFailure(mapOf(FIELD to "no se ha enviado ningún fichero"))
        return ReceivedFile(part.originalFilename ?: FIELD, part.inputStream)
    }

    private companion object {
        const val FIELD = "file"
    }
}
