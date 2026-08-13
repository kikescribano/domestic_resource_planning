package com.drp.adapter.http

import com.drp.test.SpringIntegrationTest
import com.drp.test.extract
import com.drp.test.imageBytes
import com.drp.test.registerHousehold
import com.drp.test.uploadFile
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.testcontainers.Testcontainers
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.MountableFile
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path

/**
 * La entrega de ficheros **a traves de nginx de verdad**.
 *
 * Es la unica prueba del hito que levanta un proxy, y existe porque hay cuatro
 * cosas que ningun simulacro puede demostrar:
 *
 * 1. Que la firma que emite la aplicacion es **la que nginx acepta**. Las dos
 *    puntas arman el mismo mensaje por separado; si alguien cambia el orden en
 *    una, aqui se cae, y en cualquier otro sitio no.
 * 2. Que una caducidad manipulada se rechaza.
 * 3. Que `X-Accel-Redirect` entrega los bytes de verdad.
 * 4. Que el log de acceso **no contiene ninguna cadena de consulta**, que es
 *    condicion de la ADR-005 y no una mejora: con la firma dentro, el log seria
 *    un almacen de credenciales vivas.
 *
 * Usa **la misma plantilla** que `compose.yaml`, no una copia: una copia se
 * queda desfasada en silencio y entonces la prueba deja de decir nada sobre lo
 * que se despliega.
 *
 * El resto de la suite corre sin proxy, que es como 5.8.4 describe el entorno de
 * desarrollo: la aplicacion transmite los bytes ella misma.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["drp.files.accel-redirect=true"],
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NginxDeliveryTest : SpringIntegrationTest() {

    @Autowired
    private lateinit var http: TestRestTemplate

    @LocalServerPort
    private var appPort: Int = 0

    private lateinit var nginx: GenericContainer<*>

    private val client: HttpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()

    /**
     * nginx arranca **despues** de la aplicacion y no en un `@DynamicPropertySource`
     * porque necesita saber su puerto: es quien va a hacer de proxy contra ella.
     * `exposeHostPorts` abre el camino de vuelta desde el contenedor a la maquina,
     * y tiene que llamarse antes de arrancarlo.
     */
    @BeforeAll
    fun startNginx() {
        Testcontainers.exposeHostPorts(appPort)

        nginx = GenericContainer("nginx:1.27-alpine")
            .withCopyFileToContainer(
                MountableFile.forHostPath(TEMPLATE),
                "/etc/nginx/templates/default.conf.template",
            )
            .withFileSystemBind(storageRoot.toString(), "/srv/drp/files", BindMode.READ_ONLY)
            .withEnv("DRP_FILES_LINK_SECRET", SECRET)
            .withEnv("DRP_UPSTREAM", "host.testcontainers.internal:$appPort")
            .withExposedPorts(APP_PORT, FILES_PORT)
            .waitingFor(Wait.forListeningPorts(APP_PORT, FILES_PORT))

        nginx.start()
    }

    @AfterAll
    fun stopNginx() {
        if (::nginx.isInitialized) nginx.stop()
    }

    @Test
    fun `nginx acepta la firma que emite la aplicacion y sirve la miniatura`() {
        val household = http.registerHousehold()
        val file = http.uploadFile(household.accessToken, imageBytes("jpeg"), "estante.jpg", "image/jpeg").body!!

        val response = get(filesUrl(file.extract("thumbnailUrl")))

        response.statusCode() shouldBe 200
        response.body().size shouldBeGreaterThan 0
        // El contenedor RIFF/WEBP, comprobado sobre lo que devuelve nginx.
        String(response.body().copyOfRange(0, 4), Charsets.US_ASCII) shouldBe "RIFF"

        // Las cabeceras que la ADR-005 exige en los dos caminos de entrega.
        response.headers().firstValue("x-content-type-options").orElse("") shouldBe "nosniff"
        response.headers().firstValue("content-security-policy").orElse("") shouldContain "default-src 'none'"
        response.headers().firstValue("referrer-policy").orElse("") shouldBe "no-referrer"
        response.headers().firstValue("content-disposition").orElse("") shouldContain "attachment"
    }

    /**
     * **Alargar la caducidad editando el parametro invalida la firma**, porque el
     * mensaje firmado la cubre. Es la primera de las dos comprobaciones propias
     * que la ADR-005 pide de la entrega firmada.
     */
    @Test
    fun `una URL con la caducidad manipulada se rechaza`() {
        val household = http.registerHousehold()
        val file = http.uploadFile(household.accessToken, imageBytes("png"), "a.png", "image/png").body!!

        val signed = URI(file.extract("thumbnailUrl"))
        val stretched = signed.rawQuery.replace(Regex("e=\\d+"), "e=9999999999")

        get(URI("http://${nginx.host}:${nginx.getMappedPort(FILES_PORT)}${signed.rawPath}?$stretched"))
            .statusCode() shouldBe 403
    }

    @Test
    fun `sin firma no se sirve nada, y el dominio de ficheros no tiene nada mas`() {
        val household = http.registerHousehold()
        val file = http.uploadFile(household.accessToken, imageBytes("png"), "a.png", "image/png").body!!

        val path = URI(file.extract("thumbnailUrl")).rawPath
        get(URI("http://${nginx.host}:${nginx.getMappedPort(FILES_PORT)}$path")).statusCode() shouldBe 403

        // Ni raiz, ni listado: aqui no vive la aplicacion.
        get(URI("http://${nginx.host}:${nginx.getMappedPort(FILES_PORT)}/")).statusCode() shouldBe 404
    }

    /**
     * La descarga autorizada: **la aplicacion comprueba el hogar y nginx entrega
     * los bytes**. Lo que la aplicacion devuelve es una cabecera, no un cuerpo; lo
     * que llega al cliente son los bytes enteros.
     */
    @Test
    fun `X-Accel-Redirect entrega el fichero por el endpoint autorizado`() {
        val household = http.registerHousehold()
        val file = http.uploadFile(household.accessToken, imageBytes("jpeg"), "factura.jpg", "image/jpeg").body!!

        // Sin proxy delante, la aplicacion responde con la cabecera y sin cuerpo:
        // es lo que delega la entrega, y por si solo no descarga nada.
        val direct = http.exchange(
            "/api/v1/files/${file.extract("id")}/content",
            HttpMethod.GET,
            HttpEntity<Void>(HttpHeaders().apply { setBearerAuth(household.accessToken) }),
            ByteArray::class.java,
        )
        direct.statusCode shouldBe HttpStatus.OK
        (direct.headers.getFirst("X-Accel-Redirect") ?: "") shouldContain "/internal/original/"

        // Con el proxy delante, llegan los bytes.
        val proxied = get(
            URI("http://${nginx.host}:${nginx.getMappedPort(APP_PORT)}/api/v1/files/${file.extract("id")}/content"),
            household.accessToken,
        )

        proxied.statusCode() shouldBe 200
        proxied.body().size shouldBeGreaterThan 0
        proxied.headers().firstValue("content-disposition").orElse("") shouldContain "factura.jpg"
        proxied.headers().firstValue("content-type").orElse("") shouldBe "image/jpeg"
    }

    /**
     * La condicion sin la cual nada de esto seria aceptable.
     *
     * Se comprueba **leyendo el log del contenedor**, no el formato configurado:
     * que la directiva este escrita no demuestra que nginx la use.
     */
    @Test
    fun `el log de acceso no contiene ninguna cadena de consulta`() {
        val household = http.registerHousehold()
        val file = http.uploadFile(household.accessToken, imageBytes("png"), "a.png", "image/png").body!!
        val url = URI(file.extract("thumbnailUrl"))

        get(filesUrl(url.toString())).statusCode() shouldBe 200

        val log = nginx.execInContainer("cat", "/var/log/nginx/files.log").stdout

        // La peticion esta registrada...
        log shouldContain url.rawPath
        // ...y su firma no.
        log shouldNotContain "?"
        log shouldNotContain "e="
        log shouldNotContain "s="
    }

    /** La URL que emite la aplicacion apunta a su propio puerto; aqui se redirige al de nginx. */
    private fun filesUrl(emitted: String): URI {
        val signed = URI(emitted)
        return URI("http://${nginx.host}:${nginx.getMappedPort(FILES_PORT)}${signed.rawPath}?${signed.rawQuery}")
    }

    private fun get(uri: URI, accessToken: String? = null): HttpResponse<ByteArray> {
        val request = HttpRequest.newBuilder(uri).apply {
            accessToken?.let { header("Authorization", "Bearer $it") }
        }.GET().build()
        return client.send(request, HttpResponse.BodyHandlers.ofByteArray())
    }

    private companion object {
        /**
         * El secreto que comparten la aplicacion y nginx. Es el de desarrollo de
         * `application.yml`: si no coinciden, **todas** las imagenes dan 403.
         */
        const val SECRET = "desarrollo-local-no-usar-en-produccion"

        const val APP_PORT = 80
        const val FILES_PORT = 81

        /**
         * La plantilla **del despliegue**, no una copia para pruebas. Una copia se
         * queda desfasada sin que nada falle, y entonces esta clase deja de decir
         * nada sobre lo que se despliega de verdad.
         */
        val TEMPLATE: Path = Path.of("..", "docker", "nginx", "templates", "default.conf.template")
            .toAbsolutePath()
            .normalize()
    }
}
