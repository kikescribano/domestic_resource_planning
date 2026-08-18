package com.drp.core.adapter.http

import com.drp.test.SpringIntegrationTest
import com.drp.test.TestHousehold
import com.drp.test.deleteJson
import com.drp.test.extract
import com.drp.test.extractRaw
import com.drp.test.getJson
import com.drp.test.imageBytes
import com.drp.test.jpegWithBenignPadding
import com.drp.test.jpegWithExif
import com.drp.test.pdfBytes
import com.drp.test.registerHousehold
import com.drp.test.uploadFile
import com.drp.test.uploadFileChunked
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.asSequence

/**
 * El mecanismo de subida entero, por HTTP y contra PostgreSQL real.
 *
 * Comprueba las tres cosas que caracterizan la ADR-005 --tipo real, EXIF y
 * cuota-- y lo hace **mirando los bytes guardados** donde la respuesta de la API
 * no sirve de prueba: escribir y leer con el mismo codigo se equivoca igual en
 * los dos sentidos, asi que una miniatura o un EXIF se comprueban en el disco.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FileUploadJourneyTest : SpringIntegrationTest() {

    @Autowired
    private lateinit var http: TestRestTemplate

    @Test
    fun `una imagen se sube, se recodifica y aparece con su miniatura`() {
        val household = http.registerHousehold()

        val response = http.uploadFile(household.accessToken, imageBytes("jpeg"), "estante.jpg", "image/jpeg")
        response.statusCode shouldBe HttpStatus.CREATED

        val body = response.body!!
        body.extract("contentType") shouldBe "image/jpeg"
        body.extract("originalName") shouldBe "estante.jpg"
        // El checksum es SHA-256 en hexadecimal: 64 caracteres.
        body.extract("checksum").length shouldBe 64
        body.extract("uploadedAt").shouldNotBeNull()

        // Las dos URL van firmadas, y la miniatura existe porque es una imagen.
        body.extract("url") shouldContain "?e="
        body.extract("thumbnailUrl") shouldContain "/f/thumbnail/"

        // Y los dos ficheros estan en disco de verdad, no solo en la respuesta.
        storedFiles("original", household).size shouldBe 1
        storedFiles("thumbnail", household).size shouldBe 1
    }

    @Test
    fun `un PDF se guarda tal cual y no tiene miniatura`() {
        val household = http.registerHousehold()

        val body = http.uploadFile(household.accessToken, pdfBytes(), "factura.pdf", "application/pdf").body!!

        body.extract("contentType") shouldBe "application/pdf"
        body.extractRaw("thumbnailUrl") shouldBe "null"
        storedFiles("thumbnail", household).size shouldBe 0
    }

    /**
     * El control central de la File Upload Cheat Sheet: **el contenido manda
     * sobre lo declarado**. El fichero dice llamarse `.jpg` y viaja con
     * `Content-Type: image/jpeg`; por dentro es PHP.
     */
    @Test
    fun `un fichero disfrazado se rechaza por su contenido real`() {
        val household = http.registerHousehold()
        val disguised = "<?php system(\$_GET['c']); ?>".toByteArray()

        val response = http.uploadFile(household.accessToken, disguised, "foto.jpg", "image/jpeg")

        response.statusCode shouldBe HttpStatus.UNSUPPORTED_MEDIA_TYPE
        response.body!!.extract("code") shouldBe "FILE_TYPE_NOT_ALLOWED"
        // Y no ha quedado nada en disco: lo que se rechaza no se guarda.
        storedFiles("original", household).size shouldBe 0
    }

    /**
     * SVG queda fuera de la lista blanca por ser XML con scripts dentro, y este
     * no cuela ni declarandose `image/webp`.
     */
    @Test
    fun `un SVG no entra aunque diga ser otra cosa`() {
        val household = http.registerHousehold()
        val svg = """<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script></svg>""".toByteArray()

        val response = http.uploadFile(household.accessToken, svg, "icono.webp", "image/webp")

        response.statusCode shouldBe HttpStatus.UNSUPPORTED_MEDIA_TYPE
    }

    /**
     * El motivo principal de recodificar (5.8.3): una foto hecha con el movil
     * dentro de casa lleva las coordenadas GPS de la casa incrustadas.
     *
     * Se comprueba **sobre el fichero de disco** y no sobre la respuesta. Que la
     * API no devuelva el EXIF no demuestra nada: no lo devolveria tampoco si
     * siguiera ahi.
     */
    @Test
    fun `el EXIF desaparece de los bytes guardados`() {
        val household = http.registerHousehold()
        val marker = "GPS-40.4168-3.7038-CASA"

        val original = jpegWithExif(marker)
        String(original, Charsets.ISO_8859_1) shouldContain marker

        http.uploadFile(household.accessToken, original, "cocina.jpg", "image/jpeg").statusCode shouldBe
            HttpStatus.CREATED

        val stored = storedFiles("original", household).single()
        String(Files.readAllBytes(stored), Charsets.ISO_8859_1) shouldNotContain marker
    }

    @Test
    fun `la miniatura no pasa de 320 px en el lado largo`() {
        val household = http.registerHousehold()

        http.uploadFile(household.accessToken, imageBytes("jpeg", width = 1200, height = 800), "a.jpg", "image/jpeg")

        val thumbnail = storedFiles("thumbnail", household).single()
        val image = javax.imageio.ImageIO.read(thumbnail.toFile())
        image.width shouldBe 320
        image.height shouldBe 213

        // Y pesa mucho menos que el original, que es para lo que esta.
        Files.size(thumbnail) shouldBeLessThan Files.size(storedFiles("original", household).single())
    }

    /** El nombre original es **solo un dato**: nunca forma parte de la ruta en disco. */
    @Test
    fun `un nombre con travesia de directorios no llega al disco`() {
        val household = http.registerHousehold()

        val body = http.uploadFile(
            household.accessToken,
            imageBytes("png"),
            "../../../etc/passwd.png",
            "image/png",
        ).body!!

        body.extract("originalName") shouldBe "passwd.png"

        // La ruta sale del identificador, no del nombre: termina en el UUID.
        val stored = storedFiles("original", household).single()
        stored.fileName.toString() shouldBe body.extract("id")
    }

    @Test
    fun `el uso de almacenamiento refleja lo subido y lo que se borra`() {
        val household = http.registerHousehold()

        val empty = http.getJson("/api/v1/storage", household.accessToken).body!!
        empty.extractRaw("usedBytes") shouldBe "0"
        empty.extractRaw("quotaBytes") shouldBe "1073741824"
        empty.extractRaw("maxFileBytes") shouldBe "26214400"

        val fileId = http.uploadFile(household.accessToken, imageBytes("png"), "a.png", "image/png")
            .body!!.extract("id")

        val used = http.getJson("/api/v1/storage", household.accessToken).body!!.extractRaw("usedBytes").toLong()
        used shouldBe Files.size(storedFiles("original", household).single())

        // La miniatura NO cuenta en la cuota: la decide el sistema, no el hogar.
        // Existe en disco y ocupa lo suyo, y aun asi `usedBytes` es solo el original.
        Files.size(storedFiles("thumbnail", household).single()) shouldBeGreaterThan 0

        http.deleteJson("/api/v1/files/$fileId", household.accessToken).statusCode shouldBe HttpStatus.NO_CONTENT

        // La cuota se libera **en el acto**, aunque el disco tarde hasta 24 h.
        http.getJson("/api/v1/storage", household.accessToken).body!!.extractRaw("usedBytes") shouldBe "0"
        storedFiles("original", household).size shouldBe 1
    }

    @Test
    fun `el listado ordena por tamano descendente y sabe cuales no cuelgan de nada`() {
        val household = http.registerHousehold()

        http.uploadFile(household.accessToken, imageBytes("png", 32, 32), "pequena.png", "image/png")
        http.uploadFile(household.accessToken, imageBytes("png", 400, 400), "grande.png", "image/png")

        val listed = http.getJson("/api/v1/files", household.accessToken).body!!
        (listed.indexOf("grande.png") < listed.indexOf("pequena.png")) shouldBe true

        // Ninguno cuelga de nada todavia, asi que los dos salen con attached=false.
        val unattached = http.getJson("/api/v1/files?attached=false", household.accessToken).body!!
        unattached.extractRaw("total") shouldBe "2"
        http.getJson("/api/v1/files?attached=true", household.accessToken).body!!.extractRaw("total") shouldBe "0"

        // Y el filtro por tipo distingue.
        http.getJson("/api/v1/files?type=application/pdf", household.accessToken)
            .body!!.extractRaw("total") shouldBe "0"
    }

    /**
     * **Un JPEG legitimo con ruido no se rechaza**, aunque el lector avise.
     *
     * Es el reverso de la comprobacion de truncamiento, y es la que impide que
     * esa se pase de ancha. Un JPEG con bytes de relleno antes del marcador de
     * fin --lo que producen varias camaras y varios editores-- hace avisar al
     * lector de la JVM exactamente igual que uno cortado. Rechazar ante cualquier
     * aviso seria devolver un 415 incomprensible por una foto que cualquier visor
     * abre sin pestanear, que es peor que el agujero que se pretendia cerrar.
     */
    @Test
    fun `un JPEG con ruido benigno se acepta, aunque el lector avise`() {
        val household = http.registerHousehold()

        val response = http.uploadFile(household.accessToken, jpegWithBenignPadding(), "camara.jpg", "image/jpeg")

        response.statusCode shouldBe HttpStatus.CREATED
        response.body!!.extract("contentType") shouldBe "image/jpeg"
        storedFiles("original", household).size shouldBe 1
    }

    /**
     * Una subida **sin `Content-Length`**, que es lo que envia un cliente que
     * transmite sin conocer el tamano de antemano.
     *
     * No hay tamano declarado contra el que reservar, asi que el caso de uso
     * reserva el maximo por fichero: conservador y correcto, porque cerrar la
     * fila solo puede devolver lo que sobraba. Lo que **no** puede es fallar, que
     * es lo que comprueba esta prueba --y lo que el contrato no contempla
     * rechazar.
     */
    @Test
    fun `una subida troceada, sin Content-Length, tambien funciona`() {
        val household = http.registerHousehold()

        val response = http.uploadFileChunked(household.accessToken, imageBytes("png"), "sin-tamano.png", "image/png")

        response.statusCode shouldBe HttpStatus.CREATED
        // Y la fila acaba con el tamano REAL, no con los 25 MB reservados.
        response.body!!.extractRaw("sizeBytes").toLong() shouldBe Files.size(storedFiles("original", household).single())
    }

    /**
     * Los ficheros que hay ahora mismo bajo una variante, **en el volumen de
     * verdad** y acotados al hogar de la prueba.
     *
     * Acotar por hogar no es cosmetica: la raiz se comparte entre las pruebas de
     * la clase, asi que sin ese filtro cada una contaria tambien lo que subieron
     * las anteriores.
     */
    private fun storedFiles(variant: String, household: TestHousehold): List<Path> {
        val root = storageRoot.resolve(variant).resolve(household.householdId)
        if (!Files.isDirectory(root)) return emptyList()
        return Files.walk(root).use { paths -> paths.asSequence().filter { Files.isRegularFile(it) }.toList() }
    }
}
