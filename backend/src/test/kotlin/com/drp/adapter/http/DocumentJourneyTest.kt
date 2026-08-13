package com.drp.adapter.http

import com.drp.test.SpringIntegrationTest
import com.drp.test.TestHousehold
import com.drp.test.deleteJson
import com.drp.test.extract
import com.drp.test.extractRaw
import com.drp.test.getJson
import com.drp.test.imageBytes
import com.drp.test.patchJson
import com.drp.test.pdfBytes
import com.drp.test.postJson
import com.drp.test.registerHousehold
import com.drp.test.seededCategory
import com.drp.test.uploadFile
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus

/**
 * Adjuntar documentos, y las reglas que sostienen que un fichero cuelgue de un
 * solo sitio.
 *
 * La mitad de estas comprobaciones existen porque **la base de datos no las
 * cubre**: el indice `documents_file_unique` impide que dos documentos compartan
 * fichero y ahi se acaba. Que un documento y una foto lo compartan, o que dos
 * assets compartan foto, solo lo impide el caso de uso.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DocumentJourneyTest : SpringIntegrationTest() {

    @Autowired
    private lateinit var http: TestRestTemplate

    @Test
    fun `subir una factura, adjuntarla a un asset y descargarla`() {
        val household = http.registerHousehold()
        val assetId = createAsset(household)
        val fileId = upload(household, pdfBytes(), "factura.pdf", "application/pdf")

        val attached = http.postJson(
            "/api/v1/documents",
            """{"assetId":"$assetId","type":"INVOICE","fileId":"$fileId","date":"2026-03-14"}""",
            household.accessToken,
        )

        attached.statusCode shouldBe HttpStatus.CREATED
        attached.body!!.extract("type") shouldBe "INVOICE"
        attached.body!!.extract("fileId") shouldBe fileId

        // Aparece en el listado del asset, y no en el de otro filtro.
        http.getJson("/api/v1/documents?assetId=$assetId", household.accessToken)
            .body!!.extractRaw("total") shouldBe "1"
        http.getJson("/api/v1/documents?type=MANUAL", household.accessToken)
            .body!!.extractRaw("total") shouldBe "0"

        // Y se descarga por el endpoint autorizado, como adjunto y sin husmeo.
        val download = http.getJson("/api/v1/files/$fileId/content", household.accessToken)
        download.statusCode shouldBe HttpStatus.OK
        download.headers.getFirst("Content-Disposition")!! shouldContain "attachment"
        download.headers.getFirst("Content-Disposition")!! shouldContain "factura.pdf"
        download.headers.getFirst("X-Content-Type-Options") shouldBe "nosniff"
        download.headers.getFirst("Content-Security-Policy")!! shouldContain "default-src 'none'"
    }

    @Test
    fun `un documento cuelga de un asset o de un articulo, nunca de ambos ni de ninguno`() {
        val household = http.registerHousehold()
        val assetId = createAsset(household)

        val neither = http.postJson(
            "/api/v1/documents",
            """{"type":"MANUAL","url":"https://ejemplo.test/manual.pdf"}""",
            household.accessToken,
        )
        neither.statusCode shouldBe HttpStatus.CONFLICT
        neither.body!!.extract("code") shouldBe "DOCUMENT_TARGET_INVALID"

        val both = http.postJson(
            "/api/v1/documents",
            """{"assetId":"$assetId","articleId":"$assetId","type":"MANUAL","url":"https://ejemplo.test/m.pdf"}""",
            household.accessToken,
        )
        both.body!!.extract("code") shouldBe "DOCUMENT_TARGET_INVALID"
    }

    @Test
    fun `un documento apunta a un enlace o a un fichero, nunca a los dos ni a ninguno`() {
        val household = http.registerHousehold()
        val assetId = createAsset(household)
        val fileId = upload(household, pdfBytes(), "m.pdf", "application/pdf")

        val neither = http.postJson(
            "/api/v1/documents",
            """{"assetId":"$assetId","type":"MANUAL"}""",
            household.accessToken,
        )
        neither.body!!.extract("code") shouldBe "DOCUMENT_CONTENT_INVALID"

        val both = http.postJson(
            "/api/v1/documents",
            """{"assetId":"$assetId","type":"MANUAL","url":"https://ejemplo.test/m.pdf","fileId":"$fileId"}""",
            household.accessToken,
        )
        both.body!!.extract("code") shouldBe "DOCUMENT_CONTENT_INVALID"
    }

    /**
     * **Un fichero se adjunta una sola vez.** Compartirlo haria ambiguo que pasa
     * al borrarlo y que cuenta en la cuota; para que dos unidades identicas
     * compartan manual ya esta el articulo.
     */
    @Test
    fun `un fichero no se puede adjuntar dos veces`() {
        val household = http.registerHousehold()
        val assetId = createAsset(household)
        val otherAssetId = createAsset(household)
        val fileId = upload(household, pdfBytes(), "manual.pdf", "application/pdf")

        http.postJson(
            "/api/v1/documents",
            """{"assetId":"$assetId","type":"MANUAL","fileId":"$fileId"}""",
            household.accessToken,
        ).statusCode shouldBe HttpStatus.CREATED

        val again = http.postJson(
            "/api/v1/documents",
            """{"assetId":"$otherAssetId","type":"MANUAL","fileId":"$fileId"}""",
            household.accessToken,
        )
        again.statusCode shouldBe HttpStatus.CONFLICT
        again.body!!.extract("code") shouldBe "FILE_ALREADY_ATTACHED"
    }

    /**
     * La que la base de datos **no** cubre: el indice unico solo mira `documents`.
     * Un fichero ya adjunto como documento se podia usar ademas como foto.
     */
    @Test
    fun `un fichero adjunto a un documento no se puede usar ademas como foto`() {
        val household = http.registerHousehold()
        val assetId = createAsset(household)
        val fileId = upload(household, imageBytes("jpeg"), "foto.jpg", "image/jpeg")

        http.postJson(
            "/api/v1/documents",
            """{"assetId":"$assetId","type":"OTHER","fileId":"$fileId"}""",
            household.accessToken,
        ).statusCode shouldBe HttpStatus.CREATED

        val asPhoto = http.patchJson(
            "/api/v1/assets/$assetId",
            """{"photoFileId":"$fileId"}""",
            household.accessToken,
        )
        asPhoto.statusCode shouldBe HttpStatus.CONFLICT
        asPhoto.body!!.extract("code") shouldBe "FILE_ALREADY_ATTACHED"
    }

    /** Y al reves: dos assets tampoco comparten foto, que tampoco lo impide ningun indice. */
    @Test
    fun `dos assets no pueden compartir la misma foto`() {
        val household = http.registerHousehold()
        val first = createAsset(household)
        val second = createAsset(household)
        val fileId = upload(household, imageBytes("jpeg"), "foto.jpg", "image/jpeg")

        http.patchJson("/api/v1/assets/$first", """{"photoFileId":"$fileId"}""", household.accessToken)
            .statusCode shouldBe HttpStatus.OK

        http.patchJson("/api/v1/assets/$second", """{"photoFileId":"$fileId"}""", household.accessToken)
            .body!!.extract("code") shouldBe "FILE_ALREADY_ATTACHED"
    }

    /**
     * Reenviar la foto que ya se tenia **no es adjuntarla dos veces**. Es lo que
     * hace un formulario que manda todos sus campos, y sin esta salvedad
     * responderia 409 por estar adjunta... a si misma.
     */
    @Test
    fun `reenviar la misma foto en un PATCH no da conflicto`() {
        val household = http.registerHousehold()
        val assetId = createAsset(household)
        val fileId = upload(household, imageBytes("jpeg"), "foto.jpg", "image/jpeg")

        http.patchJson("/api/v1/assets/$assetId", """{"photoFileId":"$fileId"}""", household.accessToken)
            .statusCode shouldBe HttpStatus.OK

        val again = http.patchJson(
            "/api/v1/assets/$assetId",
            """{"photoFileId":"$fileId","notes":"otra cosa"}""",
            household.accessToken,
        )
        again.statusCode shouldBe HttpStatus.OK
        again.body!!.extract("photoThumbnailUrl") shouldContain "/f/thumbnail/"
    }

    @Test
    fun `un fichero de otro hogar no se puede adjuntar, y responde 404`() {
        val mine = http.registerHousehold()
        val theirs = http.registerHousehold()

        val assetId = createAsset(mine)
        val theirFileId = upload(theirs, pdfBytes(), "suyo.pdf", "application/pdf")

        val response = http.postJson(
            "/api/v1/documents",
            """{"assetId":"$assetId","type":"INVOICE","fileId":"$theirFileId"}""",
            mine.accessToken,
        )

        // 404 y no 403 a proposito: confirmar que existe delataria un fichero ajeno.
        response.statusCode shouldBe HttpStatus.NOT_FOUND
    }

    /**
     * Borrar el documento suelta el fichero **en la misma transaccion**: la cuota
     * se libera en el acto y los bytes los desenlaza el proceso diario.
     */
    @Test
    fun `borrar el documento libera la cuota de su fichero`() {
        val household = http.registerHousehold()
        val assetId = createAsset(household)
        val fileId = upload(household, pdfBytes(), "factura.pdf", "application/pdf")

        val documentId = http.postJson(
            "/api/v1/documents",
            """{"assetId":"$assetId","type":"INVOICE","fileId":"$fileId"}""",
            household.accessToken,
        ).body!!.extract("id")

        http.getJson("/api/v1/storage", household.accessToken).body!!.extractRaw("usedBytes").toLong() shouldBe
            pdfBytes().size.toLong()

        http.deleteJson("/api/v1/documents/$documentId", household.accessToken)
            .statusCode shouldBe HttpStatus.NO_CONTENT

        http.getJson("/api/v1/storage", household.accessToken).body!!.extractRaw("usedBytes") shouldBe "0"
        // Y el fichero ya no se puede ver: esta marcado para su retirada.
        http.getJson("/api/v1/files/$fileId", household.accessToken).statusCode shouldBe HttpStatus.NOT_FOUND
    }

    /** Un fichero adjunto no se borra por su cuenta: primero se desadjunta. */
    @Test
    fun `un fichero adjunto responde FILE_IN_USE al borrarlo`() {
        val household = http.registerHousehold()
        val assetId = createAsset(household)
        val fileId = upload(household, pdfBytes(), "garantia.pdf", "application/pdf")

        http.postJson(
            "/api/v1/documents",
            """{"assetId":"$assetId","type":"WARRANTY","fileId":"$fileId"}""",
            household.accessToken,
        ).statusCode shouldBe HttpStatus.CREATED

        val deleted = http.deleteJson("/api/v1/files/$fileId", household.accessToken)
        deleted.statusCode shouldBe HttpStatus.CONFLICT
        deleted.body!!.extract("code") shouldBe "FILE_IN_USE"
    }

    /** Un `DURABLE` sin articulo necesita nombre y categoria propios. */
    private fun createAsset(household: TestHousehold): String {
        val categoryId = http.seededCategory(household.accessToken, "Herramientas")
        return http.postJson(
            "/api/v1/assets",
            """{"name":"Taladro ${java.util.UUID.randomUUID()}","type":"DURABLE","categoryId":"$categoryId"}""",
            household.accessToken,
        ).body!!.extract("id")
    }

    private fun upload(household: TestHousehold, bytes: ByteArray, name: String, type: String): String =
        http.uploadFile(household.accessToken, bytes, name, type).body!!.extract("id")
}
