package com.drp.core.adapter.http

import com.drp.test.SpringIntegrationTest
import com.drp.test.TestHousehold
import com.drp.test.deleteJson
import com.drp.test.extract
import com.drp.test.extractRaw
import com.drp.test.getJson
import com.drp.test.imageBytes
import com.drp.test.jpegWithExif
import com.drp.test.pdfBytes
import com.drp.test.registerHousehold
import com.drp.test.uploadFile
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.nio.file.Files
import java.nio.file.Path

/**
 * El avatar de una identidad, que **no es un fichero del hogar**.
 *
 * Lo que se comprueba aqui no es que funcione la subida --eso ya lo cubre
 * `FileUploadJourneyTest`-- sino las tres diferencias que lo separan de un
 * `StoredFile`: que no consume cuota de ningun hogar, que sustituye en vez de
 * acumular, y que solo admite imagen.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AvatarJourneyTest : SpringIntegrationTest() {

    @Autowired
    private lateinit var http: TestRestTemplate

    @Test
    fun `subir el avatar lo deja firmado en la ficha de la persona`() {
        val household = http.registerHousehold()

        http.getJson("/api/v1/users", household.accessToken).body!!.extractRaw("avatarUrl") shouldBe "null"

        setAvatar(household.accessToken, imageBytes("jpeg"), "yo.jpg", "image/jpeg")
            .statusCode shouldBe HttpStatus.NO_CONTENT

        val avatarUrl = http.getJson("/api/v1/users", household.accessToken).body!!.extract("avatarUrl")
        avatarUrl shouldContain "/f/avatar/"
        avatarUrl shouldContain "?e="

        // Y esta en disco, fuera del arbol de los hogares.
        avatarCount(household) shouldBe 1
    }

    /**
     * **No consume cuota de ningun hogar**, que es la consecuencia directa de que
     * una identidad no pertenezca a uno: no hay a que sumarlo.
     */
    @Test
    fun `el avatar no cuenta en la cuota del hogar`() {
        val household = http.registerHousehold()

        setAvatar(household.accessToken, imageBytes("png", 400, 400), "yo.png", "image/png")
            .statusCode shouldBe HttpStatus.NO_CONTENT

        http.getJson("/api/v1/storage", household.accessToken).body!!.extractRaw("usedBytes") shouldBe "0"
    }

    /** **Sustituye siempre**, asi que no acumula: sin acumulacion no hay nada que contar. */
    @Test
    fun `subir otro avatar sustituye al anterior en lugar de acumular`() {
        val household = http.registerHousehold()

        setAvatar(household.accessToken, imageBytes("png", 100, 100), "uno.png", "image/png")
        val firstSize = Files.size(avatarFile(household))

        setAvatar(household.accessToken, imageBytes("png", 300, 300), "dos.png", "image/png")

        // Un solo fichero en disco, y bajo la misma clave: la ruta sale del
        // identityId, no de un identificador de fichero. Lo que cambia es el
        // contenido, no la cantidad.
        avatarCount(household) shouldBe 1
        (Files.size(avatarFile(household)) != firstSize) shouldBe true
    }

    @Test
    fun `un PDF no vale como avatar`() {
        val household = http.registerHousehold()

        val response = setAvatar(household.accessToken, pdfBytes(), "yo.pdf", "application/pdf")

        response.statusCode shouldBe HttpStatus.UNSUPPORTED_MEDIA_TYPE
        response.body!!.extract("code") shouldBe "FILE_TYPE_NOT_ALLOWED"
        avatarCount(household) shouldBe 0
    }

    /** Pasa por la misma recodificacion que todo lo demas, asi que llega sin EXIF. */
    @Test
    fun `el avatar tambien pierde el EXIF`() {
        val household = http.registerHousehold()
        val marker = "GPS-40.4168-3.7038-CASA"

        setAvatar(household.accessToken, jpegWithExif(marker), "yo.jpg", "image/jpeg")
            .statusCode shouldBe HttpStatus.NO_CONTENT

        val stored = avatarFile(household)
        String(Files.readAllBytes(stored), Charsets.ISO_8859_1) shouldNotContain marker
    }

    /**
     * Quitarlo **si borra los bytes en el acto**, al contrario que un fichero del
     * hogar: alli la espera de 24 h existe para recuperar un borrado por error, y
     * un avatar se recupera volviendo a subirlo.
     */
    @Test
    fun `quitar el avatar lo borra del disco en el acto`() {
        val household = http.registerHousehold()
        setAvatar(household.accessToken, imageBytes("jpeg"), "yo.jpg", "image/jpeg")

        http.deleteJson("/api/v1/users/me/avatar", household.accessToken)
            .statusCode shouldBe HttpStatus.NO_CONTENT

        avatarCount(household) shouldBe 0
        http.getJson("/api/v1/users", household.accessToken).body!!.extractRaw("avatarUrl") shouldBe "null"
    }

    /** Quitar lo que no hay no es un error: la operacion es idempotente. */
    @Test
    fun `quitar un avatar que no existe responde igual`() {
        val household = http.registerHousehold()

        http.deleteJson("/api/v1/users/me/avatar", household.accessToken)
            .statusCode shouldBe HttpStatus.NO_CONTENT
    }

    private fun setAvatar(accessToken: String, bytes: ByteArray, name: String, type: String) =
        http.uploadFile(accessToken, bytes, name, type, path = "/api/v1/users/me/avatar", method = HttpMethod.PUT)

    /**
     * El avatar de esta identidad en el disco, si lo hay.
     *
     * Se acota por **identidad** y no por hogar, que es justo la diferencia: un
     * avatar no cuelga de ningun hogar, asi que su rama del volumen esta fuera
     * del arbol de estos. La ruta se compone igual que en `StorageKeys`, y que
     * esta prueba la sepa componer es parte de lo que comprueba: que la clave se
     * deriva del `identityId` y no de un identificador de fichero.
     */
    private fun avatarFile(household: TestHousehold): Path =
        storageRoot.resolve("avatar").resolve(household.identityId.take(2)).resolve(household.identityId)

    private fun avatarCount(household: TestHousehold) = if (Files.isRegularFile(avatarFile(household))) 1 else 0
}
