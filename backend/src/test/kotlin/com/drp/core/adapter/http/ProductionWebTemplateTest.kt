package com.drp.core.adapter.http

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.Transferable
import org.testcontainers.utility.MountableFile
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path

/**
 * Las cabeceras de seguridad del nginx **de produccion** (hallazgo 10 de la
 * auditoria OWASP), comprobadas sobre la plantilla de verdad.
 *
 * Existe ademas de [NginxDeliveryTest] porque son dos plantillas: la de
 * desarrollo proxya todo al backend y la de produccion sirve la SPA, y las
 * cabeceras de la SPA **solo existen en la segunda**. Sin esta clase, la CSP
 * del frontend seria una afirmacion que nadie vigila: el recorrido vertical
 * corre contra Vite, no contra este nginx.
 *
 * Lo que se fija, en concreto:
 *
 * 1. Que las cabeceras salen en la SPA, **incluida la ruta profunda** que
 *    resuelve `try_files`, y que la CSP no lleva `unsafe-eval` --el trabajo de
 *    la variante csp de heic-to (ADR-014) se hizo para eso.
 * 2. Que `/assets/` y `/index.html` **no las pierden**: nginx hereda las
 *    `add_header` de arriba solo si el location no declara ninguna, y esos dos
 *    declaran su Cache-Control. Es la trampa que la propia plantilla documenta
 *    y la unica forma de que no vuelva es medirla.
 * 3. Que la sustitucion de la plantilla funciono: el origen de ficheros de
 *    `img-src` es el valor de `DRP_FILES_PUBLIC_URL`, no el literal
 *    `${DRP_FILES_PUBLIC_URL}`.
 * 4. Que HSTS **no** se emite todavia: la RFC 6797 (7.2) lo prohibe sobre HTTP.
 *    El dia que llegue el TLS pendiente de la ADR-016 y se descomente, esta
 *    afirmacion fallara a proposito: es el recordatorio de venir aqui a fijar
 *    lo contrario.
 * 5. Que el servidor de ficheros del puerto 81 --que es una copia del de
 *    desarrollo y puede desviarse en silencio-- sigue rechazando sin firma y
 *    con sus cabeceras puestas.
 *
 * No arranca la aplicacion: la SPA aqui es un index y un bundle de mentira,
 * porque lo que se mide son cabeceras del proxy, no comportamiento del
 * frontend. El upstream de /api apunta a un puerto cerrado y no se toca.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProductionWebTemplateTest {

    private lateinit var nginx: GenericContainer<*>

    private val client: HttpClient = HttpClient.newBuilder().build()

    @BeforeAll
    fun startNginx() {
        nginx = GenericContainer("nginx:1.27-alpine")
            .withCopyFileToContainer(
                MountableFile.forHostPath(TEMPLATE),
                "/etc/nginx/templates/default.conf.template",
            )
            // Una SPA de mentira: lo que se mide son las cabeceras, no el frontend.
            .withCopyToContainer(Transferable.of("<!doctype html><title>drp</title>"), "/srv/drp/web/index.html")
            .withCopyToContainer(Transferable.of("console.log('drp')"), "/srv/drp/web/assets/app.js")
            .withEnv("DRP_UPSTREAM", "localhost:65535")
            .withEnv("DRP_FILES_LINK_SECRET", "irrelevante-para-estas-pruebas")
            .withEnv("DRP_FILES_PUBLIC_URL", FILES_ORIGIN)
            .withExposedPorts(APP_PORT, FILES_PORT)
            .waitingFor(Wait.forListeningPorts(APP_PORT, FILES_PORT))

        nginx.start()
    }

    @AfterAll
    fun stopNginx() {
        if (::nginx.isInitialized) nginx.stop()
    }

    @Test
    @DisplayName("la SPA sale con sus cabeceras de seguridad, tambien en una ruta profunda")
    fun `la spa lleva sus cabeceras`() {
        // La ruta profunda es la que resuelve try_files: el enlace de un correo
        // de verificacion entra por aqui, no por la raiz.
        listOf("/", "/verificar-correo").forEach { path ->
            val response = get(app(path))

            response.statusCode() shouldBe 200
            response.assertSecurityHeaders()
        }
    }

    @Test
    @DisplayName("la CSP no lleva unsafe-eval y su img-src apunta al origen de ficheros sustituido")
    fun `la csp es la medida y la plantilla se sustituyo`() {
        val csp = get(app("/")).headers().firstValue("content-security-policy").orElse("")

        csp shouldNotContain "unsafe-eval"
        // El worker de la conversion HEIC nace de un URL.createObjectURL.
        csp shouldContain "worker-src 'self' blob:"
        // Si la sustitucion de la plantilla no corrio, aqui estaria el literal
        // ${DRP_FILES_PUBLIC_URL} y ninguna imagen firmada se pintaria.
        csp shouldContain "img-src 'self' $FILES_ORIGIN"
        csp shouldNotContain "\$"
    }

    @Test
    @DisplayName("los location con Cache-Control propio no pierden las cabeceras de seguridad")
    fun `assets e index no pierden las cabeceras`() {
        // La trampa de herencia de add_header: un location que declara una
        // cabecera pierde todas las del servidor, sin error y sin aviso.
        val asset = get(app("/assets/app.js"))
        asset.statusCode() shouldBe 200
        asset.headers().firstValue("cache-control").orElse("") shouldContain "immutable"
        asset.assertSecurityHeaders()

        val index = get(app("/index.html"))
        index.headers().firstValue("cache-control").orElse("") shouldContain "no-cache"
        index.assertSecurityHeaders()
    }

    @Test
    @DisplayName("HSTS no se emite: sobre HTTP lo prohibe la RFC 6797, y el TLS sigue pendiente")
    fun `hsts sigue apagado a proposito`() {
        // Cuando el TLS de la ADR-016 llegue y el add_header se descomente, esta
        // prueba fallara: es la señal de venir a fijar que SI se emite.
        get(app("/")).headers().firstValue("strict-transport-security").isEmpty shouldBe true
    }

    @Test
    @DisplayName("el servidor de ficheros copiado sigue rechazando sin firma y con sus cabeceras")
    fun `el servidor de ficheros no se ha desviado`() {
        // Es una copia declarada del de desarrollo («si se toca alli, se toca
        // aqui»), y una copia se desvia en silencio: esto no la compara linea a
        // linea, pero fija su comportamiento visible.
        val unsigned = get(files("/f/cualquiera.png"))
        unsigned.statusCode() shouldBe 403
        unsigned.headers().firstValue("x-content-type-options").orElse("") shouldBe "nosniff"
        unsigned.headers().firstValue("content-security-policy").orElse("") shouldContain "sandbox"

        get(files("/")).statusCode() shouldBe 404
    }

    private fun HttpResponse<*>.assertSecurityHeaders() {
        headers().firstValue("x-content-type-options").orElse("") shouldBe "nosniff"
        headers().firstValue("content-security-policy").orElse("") shouldContain "frame-ancestors 'none'"
        headers().firstValue("referrer-policy").orElse("") shouldBe "no-referrer"
        headers().firstValue("permissions-policy").orElse("") shouldContain "camera=()"
    }

    private fun app(path: String): URI = URI("http://${nginx.host}:${nginx.getMappedPort(APP_PORT)}$path")

    private fun files(path: String): URI = URI("http://${nginx.host}:${nginx.getMappedPort(FILES_PORT)}$path")

    private fun get(uri: URI): HttpResponse<ByteArray> =
        client.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofByteArray())

    private companion object {
        const val APP_PORT = 80
        const val FILES_PORT = 81

        const val FILES_ORIGIN = "http://files.drp.test:8081"

        /**
         * La plantilla **del despliegue de verdad** (deploy/), no una copia para
         * pruebas ni la de desarrollo: aqui se mide lo que sirve la SPA en
         * produccion.
         */
        val TEMPLATE: Path = Path.of("..", "deploy", "nginx", "templates", "default.conf.template")
            .toAbsolutePath()
            .normalize()
    }
}
