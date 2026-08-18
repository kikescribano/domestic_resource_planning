package com.drp.core.application.usecase

import com.drp.platform.schedule.DailySweep
import com.drp.test.DrpPostgres
import com.drp.test.SpringIntegrationTest
import com.drp.test.TestHousehold
import com.drp.test.count
import com.drp.test.execute
import com.drp.test.extract
import com.drp.test.deleteJson
import com.drp.test.imageBytes
import com.drp.test.patchJson
import com.drp.test.pdfBytes
import com.drp.test.postJson
import com.drp.test.registerHousehold
import com.drp.test.seededCategory
import com.drp.test.uploadFile
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import java.nio.file.Files
import java.util.UUID

/**
 * El proceso diario que desenlaza del disco lo que ya no sirve.
 *
 * Comprueba las cuatro cosas que lo hacen delicado: que recoge **los tres** casos
 * que le tocan --borrados, nunca adjuntados y reservas cortadas--, que **no toca**
 * lo que si sirve, que borra los bytes de verdad y no solo la fila, y que recorre
 * los hogares **sin `BYPASSRLS`**, que es lo que la ADR-003 exige de los tres
 * procesos diarios.
 *
 * Las fechas se manipulan por SQL directo porque no hay otra forma de tener un
 * fichero de hace veinticinco horas: el reloj de la aplicacion es el del sistema.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PurgeUnusedFilesTest : SpringIntegrationTest() {

    @Autowired private lateinit var sweep: DailySweep
    @Autowired private lateinit var http: TestRestTemplate

    private val postgres = DrpPostgres.instance

    @Test
    @DisplayName("un fichero borrado hace mas de 24 h pierde sus bytes y su fila")
    fun `recoge los borrados`() {
        val household = http.registerHousehold()
        val fileId = upload(household, imageBytes("jpeg"), "foto.jpg", "image/jpeg")

        http.deleteJson("/api/v1/files/$fileId", household.accessToken)
        // La cuota ya se libero, pero los bytes siguen ahi: es la ventana para
        // recuperar un borrado por error sin ir a la copia de seguridad.
        bytesExist(household, fileId) shouldBe true

        ageColumn(fileId, "deleted_at", hours = 25)
        sweep.run()

        bytesExist(household, fileId) shouldBe false
        thumbnailExists(household, fileId) shouldBe false
        rowsFor(fileId) shouldBe 0
    }

    @Test
    @DisplayName("un fichero subido y nunca adjuntado se retira a las 24 h")
    fun `recoge los que no cuelgan de nada`() {
        val household = http.registerHousehold()
        val fileId = upload(household, imageBytes("png"), "suelta.png", "image/png")

        ageColumn(fileId, "uploaded_at", hours = 25)
        sweep.run()

        bytesExist(household, fileId) shouldBe false
        rowsFor(fileId) shouldBe 0
    }

    @Test
    @DisplayName("una reserva que nunca se completo se retira al cabo de una hora")
    fun `recoge las subidas cortadas a medias`() {
        val household = http.registerHousehold()
        val fileId = upload(household, pdfBytes(), "a-medias.pdf", "application/pdf")

        // Una subida cortada deja la fila con uploaded_at a nulo: ya ocupa cuota
        // y todavia no se puede adjuntar.
        postgres.ownerConnection().use {
            it.execute(
                "UPDATE files SET uploaded_at = NULL, created_at = now() - make_interval(hours => 2) WHERE id = ?",
                UUID.fromString(fileId),
            )
        }

        sweep.run()

        rowsFor(fileId) shouldBe 0
    }

    @Test
    @DisplayName("no toca un fichero que cuelga de un documento, por viejo que sea")
    fun `no toca lo que si sirve`() {
        val household = http.registerHousehold()
        val assetId = createAsset(household)
        val fileId = upload(household, pdfBytes(), "factura.pdf", "application/pdf")

        http.postJson(
            "/api/v1/documents",
            """{"assetId":"$assetId","type":"INVOICE","fileId":"$fileId"}""",
            household.accessToken,
        )

        // Muy por encima de cualquiera de los tres plazos.
        ageColumn(fileId, "uploaded_at", hours = 24 * 30)
        sweep.run()

        bytesExist(household, fileId) shouldBe true
        rowsFor(fileId) shouldBe 1
    }

    @Test
    @DisplayName("tampoco toca una foto: colgar de un asset tambien cuenta como adjunta")
    fun `no toca las fotos`() {
        val household = http.registerHousehold()
        val assetId = createAsset(household)
        val fileId = upload(household, imageBytes("jpeg"), "foto.jpg", "image/jpeg")

        http.patchJson("/api/v1/assets/$assetId", """{"photoFileId":"$fileId"}""", household.accessToken)

        ageColumn(fileId, "uploaded_at", hours = 24 * 30)
        sweep.run()

        bytesExist(household, fileId) shouldBe true
    }

    @Test
    @DisplayName("recorre los hogares uno a uno y recoge lo de cada uno")
    fun `alcanza a todos los hogares sin BYPASSRLS`() {
        val first = http.registerHousehold()
        val second = http.registerHousehold()

        val firstFile = upload(first, imageBytes("png"), "a.png", "image/png")
        val secondFile = upload(second, imageBytes("png"), "b.png", "image/png")
        ageColumn(firstFile, "uploaded_at", hours = 25)
        ageColumn(secondFile, "uploaded_at", hours = 25)

        sweep.run()

        // Los dos, y **sin** que el proceso haya podido saltarse la politica: el
        // usuario con el que corre no tiene BYPASSRLS, asi que llegar a los dos
        // solo es posible fijando `app.household_id` en cada uno.
        rowsFor(firstFile) shouldBe 0
        rowsFor(secondFile) shouldBe 0
    }

    @Test
    @DisplayName("es idempotente: pasarlo dos veces no cambia nada ni falla")
    fun `es idempotente`() {
        val household = http.registerHousehold()
        val fileId = upload(household, imageBytes("png"), "a.png", "image/png")
        ageColumn(fileId, "uploaded_at", hours = 25)

        sweep.run()
        // La segunda pasada no encuentra nada que hacer, y sobre todo no revienta
        // intentando borrar unos bytes que ya no estan.
        sweep.run()

        rowsFor(fileId) shouldBe 0
    }

    private fun upload(household: TestHousehold, bytes: ByteArray, name: String, type: String): String =
        http.uploadFile(household.accessToken, bytes, name, type).body!!.extract("id")

    private fun createAsset(household: TestHousehold): String {
        val categoryId = http.seededCategory(household.accessToken, "Herramientas")
        return http.postJson(
            "/api/v1/assets",
            """{"name":"Taladro ${UUID.randomUUID()}","type":"DURABLE","categoryId":"$categoryId"}""",
            household.accessToken,
        ).body!!.extract("id")
    }

    /** Envejece una fecha de la fila. Es la unica forma de tener un fichero de ayer. */
    private fun ageColumn(fileId: String, column: String, hours: Long) {
        postgres.ownerConnection().use {
            it.execute(
                "UPDATE files SET $column = now() - make_interval(hours => ?) WHERE id = ?",
                hours.toInt(),
                UUID.fromString(fileId),
            )
        }
    }

    /**
     * Se cuenta con el **propietario del esquema**, no con el usuario de la
     * aplicacion: la pregunta es si la fila existe, y con RLS de por medio «no la
     * veo» y «no esta» no son lo mismo.
     */
    private fun rowsFor(fileId: String): Int = postgres.ownerConnection().use {
        it.count("SELECT count(*) FROM files WHERE id = ?", UUID.fromString(fileId))
    }

    private fun bytesExist(household: TestHousehold, fileId: String) =
        Files.isRegularFile(storedPath(household, fileId, "original"))

    private fun thumbnailExists(household: TestHousehold, fileId: String) =
        Files.isRegularFile(storedPath(household, fileId, "thumbnail"))

    /**
     * La ruta en disco, compuesta **igual que la compone `StorageKeys`**.
     *
     * Se compone aqui en vez de leerse de la fila a proposito, y por dos motivos:
     * porque despues de la purga la fila ya no existe, y porque componerla es lo
     * que comprueba que la clave se deriva de identificadores y no del nombre con
     * el que llego el fichero.
     */
    private fun storedPath(household: TestHousehold, fileId: String, variant: String) =
        storageRoot.resolve(variant).resolve(household.householdId).resolve(fileId.take(2)).resolve(fileId)
}
