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
import java.net.Socket
import java.nio.file.Path
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/**
 * Las cabeceras de seguridad del nginx **de produccion** (hallazgo 10 de la
 * auditoria OWASP), comprobadas sobre la plantilla de verdad — desde la
 * ampliacion de TLS de la ADR-016, con su certificado y sus dos nombres.
 *
 * Existe ademas de [NginxDeliveryTest] porque son dos plantillas: la de
 * desarrollo proxya todo al backend y la de produccion sirve la SPA, y las
 * cabeceras de la SPA **solo existen en la segunda**. Sin esta clase, la CSP
 * del frontend seria una afirmacion que nadie vigila: el recorrido vertical
 * corre contra Vite, no contra este nginx.
 *
 * Lo que se fija, en concreto:
 *
 * 1. Que las cabeceras salen en la SPA por el 443, **incluida la ruta
 *    profunda** que resuelve `try_files`, y que la CSP no lleva `unsafe-eval`
 *    --el trabajo de la variante csp de heic-to (ADR-014) se hizo para eso.
 * 2. Que `/assets/` y `/index.html` **no las pierden**: nginx hereda las
 *    `add_header` de arriba solo si el location no declara ninguna, y esos dos
 *    declaran su Cache-Control. Es la trampa que la propia plantilla documenta
 *    y la unica forma de que no vuelva es medirla.
 * 3. Que la sustitucion de la plantilla funciono: el origen de ficheros de
 *    `img-src` es el valor de `DRP_FILES_PUBLIC_URL`, no el literal.
 * 4. Que HSTS **SI se emite**, tambien donde un Cache-Control propio rompe la
 *    herencia. Esta afirmacion fue la contraria hasta que llego el TLS: la
 *    version anterior fijaba que sobre HTTP no se emitia (RFC 6797) y estaba
 *    escrita para fallar el dia que se descomentara — fallo, y esto es venir a
 *    fijar lo contrario, como aquella pedia.
 * 5. Que el 80 **redirige todo a https salvo el desafio ACME**: si alguien
 *    mete el desafio bajo la redireccion, la emision inicial sobrevive
 *    (standalone) pero **la renovacion muere en silencio** dos meses despues.
 *    Esta es la prueba que lo hace ruidoso hoy.
 * 6. Que el servidor de ficheros --copia declarada del de desarrollo, elegido
 *    ahora por nombre (SNI) y no por puerto-- sigue rechazando sin firma y con
 *    sus cabeceras puestas.
 *
 * No arranca la aplicacion: la SPA aqui es un index y un bundle de mentira, el
 * certificado es un autofirmado de fixture (CN=drp.test, SAN con los dos
 * nombres, generado una vez con openssl) y el upstream de /api apunta a un
 * puerto cerrado. Las peticiones https van por un socket TLS a mano en lugar
 * de HttpClient, por dos motivos: el SNI decide que servidor contesta y
 * HttpClient no deja fijarlo aparte del host de la URI, y desactivar su
 * verificacion de nombre exige una propiedad de sistema que se lee una sola
 * vez por JVM — fragil en una suite compartida.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProductionWebTemplateTest {

    private lateinit var nginx: GenericContainer<*>

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
            // El certificado de fixture, en la misma ruta que el de verdad.
            .withCopyToContainer(Transferable.of(CERT_PEM), "/etc/letsencrypt/live/$APP_HOST/fullchain.pem")
            .withCopyToContainer(Transferable.of(KEY_PEM), "/etc/letsencrypt/live/$APP_HOST/privkey.pem")
            .withEnv("DRP_UPSTREAM", "localhost:65535")
            .withEnv("DRP_FILES_LINK_SECRET", "irrelevante-para-estas-pruebas")
            .withEnv("DRP_APP_HOST", APP_HOST)
            .withEnv("DRP_FILES_HOST", FILES_HOST)
            .withEnv("DRP_FILES_PUBLIC_URL", FILES_ORIGIN)
            .withExposedPorts(HTTP_PORT, TLS_PORT)
            .waitingFor(Wait.forListeningPorts(HTTP_PORT, TLS_PORT))

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
            val response = https(APP_HOST, path)

            response.status shouldBe 200
            response.assertSecurityHeaders()
        }
    }

    @Test
    @DisplayName("la CSP no lleva unsafe-eval y su img-src apunta al origen de ficheros sustituido")
    fun `la csp es la medida y la plantilla se sustituyo`() {
        val csp = https(APP_HOST, "/").header("content-security-policy")

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
        val asset = https(APP_HOST, "/assets/app.js")
        asset.status shouldBe 200
        asset.header("cache-control") shouldContain "immutable"
        asset.assertSecurityHeaders()

        val index = https(APP_HOST, "/index.html")
        index.header("cache-control") shouldContain "no-cache"
        index.assertSecurityHeaders()
    }

    @Test
    @DisplayName("el 80 redirige todo a https, menos el desafio ACME que renueva el certificado")
    fun `el 80 solo hace acme y redireccion`() {
        // La redireccion conserva la ruta: el enlace http de un marcador viejo
        // aterriza en la misma pantalla, no en la portada.
        val redirected = http("/entrar")
        redirected.status shouldBe 301
        redirected.header("location") shouldBe "https://$APP_HOST/entrar"

        // Y el desafio NO se redirige: Let's Encrypt lo pide por http, y una
        // redireccion aqui mataria la renovacion en silencio. 404 significa
        // «atendido y sin fichero», que es lo correcto sin renovacion en curso.
        val challenge = http("/.well-known/acme-challenge/inexistente")
        challenge.status shouldBe 404
    }

    @Test
    @DisplayName("HSTS se emite sobre TLS, tambien donde el Cache-Control rompe la herencia")
    fun `hsts esta encendido`() {
        // La version anterior de esta prueba fijaba lo contrario --sobre HTTP
        // la RFC 6797 lo prohibe-- y estaba escrita para fallar el dia que el
        // TLS llegara. Llego.
        listOf("/", "/index.html", "/assets/app.js").forEach { path ->
            https(APP_HOST, path).header("strict-transport-security") shouldContain "max-age=31536000"
        }
    }

    @Test
    @DisplayName("el servidor de ficheros, elegido por nombre, sigue rechazando sin firma y con sus cabeceras")
    fun `el servidor de ficheros no se ha desviado`() {
        // Es una copia declarada del de desarrollo («si se toca alli, se toca
        // aqui»), y una copia se desvia en silencio: esto no la compara linea a
        // linea, pero fija su comportamiento visible. El SNI es quien elige
        // este servidor y no el de la SPA: con el nombre equivocado, esta misma
        // peticion devolveria el index y la prueba fallaria.
        val unsigned = https(FILES_HOST, "/f/cualquiera.png")
        unsigned.status shouldBe 403
        unsigned.header("x-content-type-options") shouldBe "nosniff"
        unsigned.header("content-security-policy") shouldContain "sandbox"
        unsigned.header("strict-transport-security") shouldContain "max-age"

        https(FILES_HOST, "/").status shouldBe 404
    }

    private fun RawResponse.assertSecurityHeaders() {
        header("x-content-type-options") shouldBe "nosniff"
        header("content-security-policy") shouldContain "frame-ancestors 'none'"
        header("referrer-policy") shouldBe "no-referrer"
        header("permissions-policy") shouldContain "camera=()"
    }

    // -----------------------------------------------------------------------
    // HTTP a mano: status y cabeceras, que es lo unico que esta clase mide
    // -----------------------------------------------------------------------

    private class RawResponse(val status: Int, private val headers: Map<String, String>) {
        fun header(name: String): String = headers[name.lowercase()] ?: ""
    }

    /** GET por el 443 con el SNI que decide que servidor contesta. */
    private fun https(sni: String, path: String): RawResponse {
        val context = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(TRUST_FIXTURE), SecureRandom())
        }
        val socket = context.socketFactory.createSocket(nginx.host, nginx.getMappedPort(TLS_PORT)) as SSLSocket
        socket.use {
            it.sslParameters = it.sslParameters.apply { serverNames = listOf(SNIHostName(sni)) }
            it.startHandshake()
            return request(it, sni, path)
        }
    }

    /** GET por el 80, sin seguir la redireccion: la redireccion ES el dato. */
    private fun http(path: String): RawResponse =
        Socket(nginx.host, nginx.getMappedPort(HTTP_PORT)).use { request(it, APP_HOST, path) }

    private fun request(socket: Socket, host: String, path: String): RawResponse {
        socket.outputStream.write("GET $path HTTP/1.1\r\nHost: $host\r\nConnection: close\r\n\r\n".toByteArray())
        socket.outputStream.flush()
        val head = socket.inputStream.readBytes().toString(Charsets.ISO_8859_1).substringBefore("\r\n\r\n")
        val lines = head.split("\r\n")
        val status = lines.first().split(" ")[1].toInt()
        val headers = lines.drop(1)
            .mapNotNull { line ->
                val i = line.indexOf(':')
                if (i < 0) null else line.substring(0, i).trim().lowercase() to line.substring(i + 1).trim()
            }
            .toMap()
        return RawResponse(status, headers)
    }

    private companion object {
        const val HTTP_PORT = 80
        const val TLS_PORT = 443

        const val APP_HOST = "drp.test"
        const val FILES_HOST = "files.drp.test"
        const val FILES_ORIGIN = "https://files.drp.test"

        /**
         * La plantilla **del despliegue de verdad** (deploy/), no una copia para
         * pruebas ni la de desarrollo: aqui se mide lo que sirve la SPA en
         * produccion.
         */
        val TEMPLATE: Path = Path.of("..", "deploy", "nginx", "templates", "default.conf.template")
            .toAbsolutePath()
            .normalize()

        /** Se fia de cualquier certificado: el de enfrente es el fixture de abajo. */
        val TRUST_FIXTURE = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }

        /**
         * Autofirmado de fixture, generado una vez y valido cien anos:
         *
         *   openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 \
         *     -keyout key.pem -out cert.pem -days 36500 -nodes \
         *     -subj "/CN=drp.test" -addext "subjectAltName=DNS:drp.test,DNS:files.drp.test"
         *
         * nginx solo necesita que el par sea coherente para arrancar; los
         * nombres van en el SAN por prolijidad, no porque nada los verifique.
         */
        val CERT_PEM = """
            -----BEGIN CERTIFICATE-----
            MIIBoTCCAUigAwIBAgIUM9lJqTqga3aPF0biDopuFuAGwGYwCgYIKoZIzj0EAwIw
            EzERMA8GA1UEAwwIZHJwLnRlc3QwIBcNMjYwODIxMDQzMDU1WhgPMjEyNjA3Mjgw
            NDMwNTVaMBMxETAPBgNVBAMMCGRycC50ZXN0MFkwEwYHKoZIzj0CAQYIKoZIzj0D
            AQcDQgAEu37etJE1b5OyMd1906PsdaMbfEaFyrlKJevInGrsJOU38RP36Y+cOc4f
            g6GcwOfN5e1XpuUkpxphEYI8H1opCaN4MHYwHQYDVR0OBBYEFCEmOAnaQ5JPV7w9
            oLAMKHokE7xuMB8GA1UdIwQYMBaAFCEmOAnaQ5JPV7w9oLAMKHokE7xuMA8GA1Ud
            EwEB/wQFMAMBAf8wIwYDVR0RBBwwGoIIZHJwLnRlc3SCDmZpbGVzLmRycC50ZXN0
            MAoGCCqGSM49BAMCA0cAMEQCIAvEFAGDAW83fO7CwfhkLxdUpapq8LXLFQS6iFCS
            G7j1AiACc8q3Hs64lE/1XwvZZ+XiTFYjI635ldIV9JYeMdEj0Q==
            -----END CERTIFICATE-----
        """.trimIndent()

        val KEY_PEM = """
            -----BEGIN PRIVATE KEY-----
            MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgeFH2JV+iexH9bbRY
            xE6EHyZVelNogOAmGNGWKGGGbpahRANCAAS7ft60kTVvk7Ix3X3To+x1oxt8RoXK
            uUol68icauwk5TfxE/fpj5w5zh+DoZzA583l7Vem5SSnGmERgjwfWikJ
            -----END PRIVATE KEY-----
        """.trimIndent()
    }
}
