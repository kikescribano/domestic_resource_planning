package com.drp.adapter.http

import com.drp.test.DrpPostgres
import com.drp.test.SpringIntegrationTest
import com.drp.test.extract
import com.drp.test.postJson
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import java.net.Socket
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.Date
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * El barrido de contencion del token acotado: **intentar salirse de verdad**.
 *
 * `LoanTokenScopeTest` comprueba los bordes por el camino limpio --otras rutas,
 * otro prestamo, otro hogar-- y esta prueba ataca las dos costuras que ese
 * camino no toca:
 *
 * 1. **La comparacion de rutas del filtro es texto contra texto**
 *    (`JwtAuthenticationFilter.withinScopeOf`). Si Tomcat o Spring normalizaran
 *    la ruta de forma distinta a como la ve el filtro, habria un sitio donde el
 *    filtro dice «esta es su ruta» y el despachador lleva a otra. Por eso aqui
 *    **no se usa `TestRestTemplate`**: reescribiria la ruta antes de mandarla.
 *    Se abre un socket y se escribe la linea de peticion tal cual, que es lo
 *    unico que garantiza que Tomcat recibio lo que se queria probar.
 * 2. **La credencial se puede forjar en una prueba**, porque el `application.yml`
 *    trae la clave de firma de desarrollo y las pruebas arrancan con ella. Eso
 *    permite separar lo que defiende la firma de lo que defiende la fila de
 *    `loan_access_tokens`: se forja un token con la clave buena y se le pone (o
 *    no) su fila a mano.
 *
 * Ninguna de las dos se puede afirmar leyendo el codigo, que es justo el motivo
 * de que este fichero exista.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LoanTokenContainmentSweepTest : SpringIntegrationTest() {

    @Autowired private lateinit var http: TestRestTemplate

    @LocalServerPort private var port: Int = 0

    // ---------------------------------------------------------------------
    // La ruta
    // ---------------------------------------------------------------------

    /**
     * Antes de barrer nada hay que demostrar que el barredor sabe llegar.
     *
     * Una prueba de contencion que solo afirma «no dio 200» pasa igual de bien
     * cuando el cliente esta roto y ninguna peticion llega a la aplicacion. Este
     * caso fija el suelo: por este mismo socket, con esta misma cabecera, la ruta
     * legitima **si** responde 200.
     */
    @Test
    @DisplayName("el cliente en crudo llega al 200 legitimo (si no, el barrido no probaria nada)")
    fun `el socket alcanza la ruta propia`() {
        val loan = http.lendToStranger()

        val ok = raw("GET", "/api/v1/loans/${loan.loanId}", loan.token)

        ok.status.shouldBe(200)
        ok.body.extract("status").shouldBe("ACTIVE")

        // La cadena de consulta no forma parte del `servletPath`, asi que escribir
        // otra ruta ahi dentro no lleva a ningun sitio: la peticion sigue siendo
        // la suya y la respuesta sigue siendo la acotada.
        val withQuery = raw("GET", "/api/v1/loans/${loan.loanId}?x=/api/v1/assets&open=true", loan.token)
        withQuery.status.shouldBe(200)
        withQuery.body.extract("id").shouldBe(loan.loanId)
        withQuery.body.externalKeys().shouldBe(EXTERNAL_FIELDS)
        withQuery.leaksNothing(loan.assetId, loan.memberId)

        // Y el manejador de errores de Spring Boot, que no es del contrato pero es
        // una ruta mas de la aplicacion: el token tampoco es credencial ahi.
        raw("GET", "/error", loan.token).status.shouldBe(401)
    }

    /**
     * Veintitantas formas de escribir «otra ruta» que el filtro podria leer como
     * la suya, o al reves.
     *
     * Lo que se busca es una grieta entre **lo que compara el filtro**
     * (`request.servletPath`, ya decodificado y normalizado por Tomcat) y **lo
     * que despacha Spring MVC** (que parte del `requestURI` en crudo). Donde las
     * dos no coincidan, un token acotado podria autenticarse para su prestamo y
     * acabar ejecutando otra cosa.
     */
    @Test
    @DisplayName("la ruta del token solo vale escrita exactamente, y ningun rodeo la ensancha")
    fun `los rodeos de la ruta no ensanchan el alcance`() {
        val loan = http.lendToStranger()
        val other = http.secondLoanOf(loan)
        val id = loan.loanId

        // Cada entrada es un intento de llegar a otro sitio --o al mismo por un
        // camino que el filtro no deberia reconocer-- con el token del prestamo.
        val attempts = listOf(
            // Barra final y barras repetidas. `withinScopeOf` hace `trimEnd('/')`,
            // asi que el filtro las acepta: lo que hay que comprobar es que al
            // otro lado no aparezca nada nuevo.
            "GET" to "/api/v1/loans/$id/",
            "GET" to "/api/v1/loans/$id//",
            "GET" to "//api/v1/loans/$id",
            "GET" to "/api/v1//loans/$id",
            // Segmentos relativos: el clasico para que el filtro vea una ruta y
            // el despachador otra.
            "GET" to "/api/v1/loans/$id/../${other.loanId}",
            "GET" to "/api/v1/loans/$id/../../assets",
            "GET" to "/api/v1/loans/$id/./",
            "GET" to "/api/v1/./loans/$id",
            "GET" to "/api/v1/loans/./$id",
            "GET" to "/./api/v1/loans/$id",
            "GET" to "/api/v1/assets/../loans/$id",
            // Los mismos, codificados: si algo decodifica despues de normalizar,
            // aqui se ve.
            "GET" to "/api/v1/loans/$id/%2e%2e/${other.loanId}",
            "GET" to "/api/v1/loans/$id/%2e%2e/%2e%2e/assets",
            "GET" to "/api/v1/loans/$id/..%2f${other.loanId}",
            "GET" to "/api/v1/loans/$id%2f..%2f${other.loanId}",
            // Barra codificada para fabricar el sufijo `/return` dentro de un
            // segmento: el filtro lo veria como ruta propia si decodificara antes
            // de comparar.
            "POST" to "/api/v1/loans/$id%2freturn",
            "GET" to "/api/v1/assets%2f..%2f..%2floans%2f$id",
            // Parametros de matriz, que Tomcat quita del `servletPath`.
            "GET" to "/api/v1/loans/$id;x=1",
            "GET" to "/api/v1/loans/$id;/../assets",
            "GET" to "/api/v1/assets;foo=/api/v1/loans/$id",
            // Prefijos y sufijos pegados sin separador: el fallo tipico de
            // comparar con `startsWith`.
            "GET" to "/api/v1/loans/${id}x",
            "POST" to "/api/v1/loans/${id}return",
            "POST" to "/api/v1/loans/$id/return/extra",
            "GET" to "/api/v1/loans/$id/asset",
            "GET" to "/api/v1/loansx/$id",
            // Mayusculas: el UUID del token se serializa en minusculas y la
            // comparacion es sensible.
            "GET" to "/api/v1/loans/${id.uppercase()}",
            "GET" to "/API/V1/LOANS/$id",
            // Y el hueco: si el filtro tolerase el vacio, esto seria el listado.
            "GET" to "/api/v1/loans/",
            "GET" to "/api/v1/loans",
        )

        var reachable = 0

        attempts.forEach { (method, target) ->
            val denied = raw(method, target, loan.token)
            // La MISMA ruta con la sesion del hogar. Es lo que distingue «el
            // filtro lo paro» de «Tomcat lo rechazo antes de que nadie mirase el
            // token»: sin esta segunda peticion, un barrido en el que todo diera
            // 400 pasaria entero sin haber ejercitado el filtro ni una vez.
            val withSession = raw(method, target, loan.accessToken)

            withClue("$method $target -> token ${denied.statusLine} | sesion ${withSession.statusLine}\n${denied.body}") {
                // Un 200 con el token significaria una de dos cosas, las dos
                // graves: o alcanzo algo que no es su prestamo, o alcanzo su
                // prestamo por una ruta que el filtro no compara igual que el
                // despachador.
                (denied.status == 200).shouldBe(false)
                denied.leaksNothing(loan.assetId, loan.memberId, other.assetId)

                if (withSession.status == 200) {
                    // La ruta existe y responde: aqui el 401 no es un accidente
                    // del enrutado, es el filtro diciendo que ese token no es una
                    // credencial en este sitio.
                    reachable++
                    denied.status.shouldBe(401)
                }
            }
        }

        // Medido: de los 29 rodeos, **dos** llegan a una ruta que responde 200 con
        // sesion del hogar. Los otros veintisiete los corta Tomcat o el
        // enrutado antes, asi que por si solos no dicen nada del filtro. El suelo
        // esta aqui para que eso no pase inadvertido el dia que un cambio de
        // enrutado deje el barrido entero en 400.
        withClue("ninguno de los ${attempts.size} intentos llegaba a una ruta viva: el barrido no probaria nada") {
            (reachable >= 2).shouldBe(true)
        }
    }

    /**
     * El UUID en mayusculas, que es el rodeo que **de verdad** ejercita el filtro.
     *
     * Es el unico del barrido de arriba en el que la ruta esta viva --con sesion
     * del hogar responde `200`, porque `@PathVariable UUID` no distingue
     * mayusculas-- y el token acotado se queda fuera. Ahi el `401` no es un
     * accidente del enrutado: es la comparacion de texto del filtro, que **si**
     * distingue, porque el UUID del token se serializa siempre en minusculas.
     *
     * De modo que la asimetria existe y es hacia el lado seguro: rechaza de mas,
     * nunca de menos. Escribirla aqui es lo que impide que manana alguien la
     * "arregle" pasando los dos lados a minusculas sin darse cuenta de que eso
     * ensancha el alcance de la credencial.
     */
    @Test
    @DisplayName("el UUID en mayusculas: la ruta existe para el hogar y no para el token")
    fun `la comparacion del identificador distingue mayusculas`() {
        val loan = http.lendToStranger()
        val shouted = "/api/v1/loans/${loan.loanId.uppercase()}"

        withClue("con sesion del hogar la ruta tiene que estar viva, o esto no prueba nada") {
            val withSession = raw("GET", shouted, loan.accessToken)
            withSession.status.shouldBe(200)
            withSession.body.extract("id").shouldBe(loan.loanId)
        }

        val denied = raw("GET", shouted, loan.token)
        withClue("${denied.statusLine}\n${denied.body}") {
            denied.status.shouldBe(401)
            denied.leaksNothing(loan.assetId, loan.memberId)
        }
    }

    /**
     * La barra final, que es el unico sitio donde el filtro deja pasar un token
     * acotado a algo que no es su `GET`.
     *
     * `withinScopeOf` hace `trimEnd('/')`, asi que para el filtro
     * `/api/v1/loans/{id}/` **es** la ruta propia. Para Spring no lo es: desde la
     * version 6 no hay correspondencia por barra final. Medido, el resultado es un
     * `500 INTERNAL_ERROR` --con sesion del hogar tambien, asi que no es cosa de
     * la credencial-- y lo que importa aqui es que ese 500 **no lleva nada del
     * prestamo dentro**.
     *
     * Se deja el `trimEnd` como esta: hoy no concede nada, y estrechar el filtro
     * taparia con un `401` un defecto de enrutado que sigue estando ahi para
     * cualquier sesion. Lo que no puede cambiar sin que esto avise es que por esa
     * ruta salgan datos.
     */
    @Test
    @DisplayName("la barra final no sirve datos, aunque el filtro la acepte")
    fun `la barra final no llega al prestamo`() {
        val loan = http.lendToStranger()
        val trailing = "/api/v1/loans/${loan.loanId}/"

        val denied = raw("GET", trailing, loan.token)

        withClue("${denied.statusLine}\n${denied.body}") {
            (denied.status == 200).shouldBe(false)
            denied.body.shouldNotContain("assetName")
            denied.body.shouldNotContain("\"status\":\"ACTIVE\"")
            denied.leaksNothing(loan.assetId, loan.memberId)
        }
    }

    /**
     * El UUID codificado carácter a carácter **es la misma ruta**, y se documenta
     * que lo es.
     *
     * `servletPath` llega decodificado, asi que `%61` y `a` son el mismo camino
     * para el filtro y para el despachador a la vez. No es un agujero --se llega
     * al mismo prestamo del token, que es lo unico que ese token puede ver-- pero
     * conviene tenerlo escrito: si manana alguien compara sobre `requestURI` en
     * vez de sobre `servletPath`, esta prueba cambia de resultado y avisa.
     */
    @Test
    @DisplayName("el UUID codificado en la ruta lleva al mismo prestamo, no a otro")
    fun `la codificacion porcentual del identificador no cambia el destino`() {
        val loan = http.lendToStranger()
        val encoded = loan.loanId.map { "%%%02X".format(it.code) }.joinToString("")

        val response = raw("GET", "/api/v1/loans/$encoded", loan.token)

        withClue("${response.statusLine}\n${response.body}") {
            // Medido: Tomcat decodifica el `servletPath`, asi que el filtro
            // compara exactamente la misma cadena y esto **es** la ruta propia.
            // Se afirma el 200 y no «cualquier cosa menos otro prestamo» a
            // proposito: una condicion que solo comprueba algo cuando se cumple
            // pasa igual de bien cuando no se cumple nunca.
            response.status.shouldBe(200)
            response.body.extract("id").shouldBe(loan.loanId)
            response.body.externalKeys().shouldBe(EXTERNAL_FIELDS)
            response.leaksNothing(loan.assetId, loan.memberId)
        }
    }

    /**
     * Solo `GET` en su ruta y solo `POST` en la de devolucion.
     *
     * `withinScopeOf` decide por `method.uppercase()`, asi que todo lo demas cae
     * al `else`. Importa comprobar `HEAD` en particular: Spring MVC lo despacha
     * al manejador del `GET`, de modo que si el filtro lo dejara pasar, la
     * cabecera de respuesta saldria del recurso completo.
     */
    @Test
    @DisplayName("ningun metodo mas que GET y POST, y cada uno solo en su ruta")
    fun `el alcance es por metodo y no solo por ruta`() {
        val loan = http.lendToStranger()
        val id = loan.loanId

        val attempts = listOf(
            "HEAD" to "/api/v1/loans/$id",
            "OPTIONS" to "/api/v1/loans/$id",
            "PUT" to "/api/v1/loans/$id",
            "PATCH" to "/api/v1/loans/$id",
            "DELETE" to "/api/v1/loans/$id",
            "POST" to "/api/v1/loans/$id",
            "GET" to "/api/v1/loans/$id/return",
            "HEAD" to "/api/v1/loans/$id/return",
            "DELETE" to "/api/v1/loans/$id/return",
            // Y el metodo escrito en minusculas, que Tomcat no normaliza: si
            // llegara hasta el filtro, `method.uppercase()` lo aceptaria. Tiene
            // que morir antes.
            "get" to "/api/v1/loans/$id",
        )

        attempts.forEach { (method, target) ->
            val response = raw(method, target, loan.token)

            withClue("$method $target -> ${response.statusLine}\n${response.body}") {
                (response.status == 200).shouldBe(false)
                response.leaksNothing(loan.assetId, loan.memberId)
            }
        }
    }

    /**
     * Las cabeceras con las que un proxy reescribe la ruta.
     *
     * `X-Forwarded-Prefix` la lee `ForwardedHeaderFilter`, y `X-Original-URL` y
     * `X-Rewrite-URL` son la convencion de IIS que algunas pilas honran. Si
     * alguna llegara a mover lo que el filtro compara, el alcance del token lo
     * decidiria quien manda la peticion.
     */
    @Test
    @DisplayName("ninguna cabecera de reescritura mueve la ruta que compara el filtro")
    fun `las cabeceras de proxy no reescriben el alcance`() {
        val loan = http.lendToStranger()
        val own = "/api/v1/loans/${loan.loanId}"

        // Con la cabecera puesta, pero pidiendo **otra** ruta: si moviera lo que
        // el filtro compara, el token pasaria a alcanzar lo que diga la cabecera.
        val elsewhere = listOf(
            Triple("GET", "/api/v1/assets", listOf("X-Original-URL: $own")),
            Triple("GET", "/api/v1/assets", listOf("X-Rewrite-URL: $own")),
            Triple("GET", "/api/v1/assets", listOf("X-Forwarded-Prefix: $own")),
            Triple("GET", "/api/v1/assets", listOf("X-Forwarded-Path: $own")),
            Triple("GET", "/api/v1/loans", listOf("X-Original-URL: $own", "X-Rewrite-URL: $own")),
            // El override de metodo: si `HiddenHttpMethodFilter` estuviera
            // activo, esto convertiria el POST permitido en otra cosa.
            Triple("POST", "/api/v1/loans", listOf("X-HTTP-Method-Override: GET")),
        )

        elsewhere.forEach { (method, target, headers) ->
            val response = raw(method, target, loan.token, headers)

            withClue("$method $target ${headers.joinToString()} -> ${response.statusLine}\n${response.body}") {
                (response.status == 200).shouldBe(false)
                response.leaksNothing(loan.assetId, loan.memberId)
            }
        }

        // Y el sentido contrario, que es el que de verdad demuestra que la
        // cabecera **no hace nada**: un prefijo que, de aplicarse, dejaria la
        // ruta propia irreconocible para el filtro y daria 401. Sigue dando 200 y
        // con la misma proyeccion acotada, asi que no se aplica.
        //
        // Comprobar solo el sentido de arriba no bastaba: un 401 alli tambien
        // sale cuando la cabecera se ignora, que es justo lo que hay que
        // distinguir.
        val ownPath = listOf(
            listOf("X-Forwarded-Prefix: /api/v1"),
            listOf("X-Forwarded-Prefix: /api/v1/loans/${loan.loanId}"),
            listOf("X-Forwarded-Host: intruso.example"),
        )

        ownPath.forEach { headers ->
            val response = raw("GET", own, loan.token, headers)

            withClue("${headers.joinToString()} -> ${response.statusLine}\n${response.body}") {
                response.status.shouldBe(200)
                response.body.extract("id").shouldBe(loan.loanId)
                response.body.externalKeys().shouldBe(EXTERNAL_FIELDS)
                response.leaksNothing(loan.assetId, loan.memberId)
            }
        }
    }

    // ---------------------------------------------------------------------
    // La credencial
    // ---------------------------------------------------------------------

    /**
     * Forjar el JWT sin la clave, y con ella pero sin fila.
     *
     * Las tres son la misma pregunta desde tres sitios: **que es lo que de
     * verdad autoriza**. Si bastara la forma del token, la primera pasaria; si
     * bastara la firma, pasaria la tercera.
     */
    @Test
    @DisplayName("un JWT forjado no vale: ni sin firma, ni con otra clave, ni con la buena sin fila")
    fun `la firma sola no autoriza`() {
        val loan = http.lendToStranger()
        val expires = Date.from(Instant.now().plus(30, ChronoUnit.DAYS))

        // 1. `alg: none`, el ataque de manual. Con firma vacia y con una firma
        //    inventada, porque los parsers fallan en sitios distintos segun cual.
        val unsignedHeader = base64Url("""{"alg":"none","typ":"JWT"}""")
        val unsignedBody = base64Url(
            """{"loanId":"${loan.loanId}","role":"BORROWER","exp":${expires.toInstant().epochSecond}}""",
        )

        val forged = listOf(
            "alg:none sin firma" to "$unsignedHeader.$unsignedBody.",
            "alg:none con firma inventada" to "$unsignedHeader.$unsignedBody.QUFBQQ",
            // 2. HS256 bien formado, firmado con OTRA clave de 32 bytes.
            "HS256 con otra clave" to signed(
                key = "otra-clave-de-treinta-y-dos-bytes-o-mas",
                expiresAt = expires,
            ) { it.claim("loanId", loan.loanId).claim("role", "BORROWER") },
            // 3. HS256 firmado con LA clave buena, con los claims correctos del
            //    prestamo. La firma cuadra: lo unico que falta es la fila, y eso
            //    es lo que tiene que bastar para rechazarlo.
            "HS256 con la clave buena y sin fila" to signed(expiresAt = expires) {
                it.claim("loanId", loan.loanId).claim("role", "BORROWER")
            },
            // 4. La misma, pero apuntando a un prestamo que ni siquiera existe.
            "HS256 con la clave buena y prestamo inventado" to signed(expiresAt = expires) {
                it.claim("loanId", UUID.randomUUID().toString()).claim("role", "LENDER")
            },
        )

        forged.forEach { (what, token) ->
            val response = raw("GET", "/api/v1/loans/${loan.loanId}", token)

            withClue("$what -> ${response.statusLine}\n${response.body}") {
                response.status.shouldBe(401)
                response.leaksNothing(loan.assetId, loan.memberId)
            }
        }
    }

    /**
     * Con la fila puesta a mano, para aislar lo que comprueba el JWT.
     *
     * Es la unica forma de medir por separado las comprobaciones de
     * `JwtLoanTokenIssuer.verify` y `AuthenticateLoanToken`: si el token no tiene
     * fila, todas fallan a la vez y no se sabe cual mordio. Aqui se inserta la
     * fila del hash del token forjado, de modo que lo unico que queda entre la
     * peticion y el prestamo son esas comprobaciones.
     *
     * El **control** es la primera entrada: un token forjado del todo correcto y
     * con su fila **si** entra. Sin el, las demas podrian estar fallando por
     * cualquier motivo y la prueba pasaria igual.
     */
    @Test
    @DisplayName("con fila en la tabla, el cuerpo del JWT sigue teniendo que cuadrar con ella")
    fun `el claim y la fila tienen que decir lo mismo`() {
        val loan = http.lendToStranger()
        val other = http.secondLoanOf(loan)
        val future = Date.from(Instant.now().plus(30, ChronoUnit.DAYS))

        // El control: bien formado, con su fila. Tiene que entrar.
        val legitimate = signed(expiresAt = future) {
            it.claim("loanId", loan.loanId).claim("role", "BORROWER")
        }
        registerLoanToken(legitimate, loan.loanId, "BORROWER")
        withClue("el control forjado deberia entrar; si no, el resto de la prueba no mide nada") {
            raw("GET", "/api/v1/loans/${loan.loanId}", legitimate).status.shouldBe(200)
        }

        // Sin `exp`. Un token que no caduca nunca es lo contrario de una
        // credencial acotada, y el verificador lo exige antes de mirar la fila.
        val noExpiry = signed(expiresAt = null) {
            it.claim("loanId", loan.loanId).claim("role", "BORROWER")
        }
        registerLoanToken(noExpiry, loan.loanId, "BORROWER")

        // Con `sub`: la confusion de credenciales por el lado del token de
        // prestamo. Comparte clave de firma con el access token, asi que lo unico
        // que los separa es la forma del cuerpo.
        val withSubject = signed(expiresAt = future) {
            it.subject(UUID.randomUUID().toString())
                .claim("loanId", loan.loanId)
                .claim("role", "BORROWER")
        }
        registerLoanToken(withSubject, loan.loanId, "BORROWER")

        // El claim apunta a un prestamo y la fila a otro. Es el caso que
        // justifica la comparacion de `AuthenticateLoanToken`: sin ella se
        // serviria el de la fila.
        val crossedLoan = signed(expiresAt = future) {
            it.claim("loanId", other.loanId).claim("role", "BORROWER")
        }
        registerLoanToken(crossedLoan, loan.loanId, "BORROWER")

        // Y lo mismo con el papel: el claim dice prestador y la fila receptor.
        val crossedRole = signed(expiresAt = future) {
            it.claim("loanId", loan.loanId).claim("role", "LENDER")
        }
        registerLoanToken(crossedRole, loan.loanId, "BORROWER")

        listOf(
            "sin exp" to noExpiry,
            "con sub" to withSubject,
            "loanId cruzado" to crossedLoan,
            "role cruzado" to crossedRole,
        ).forEach { (what, token) ->
            listOf(loan.loanId, other.loanId).forEach { target ->
                val response = raw("GET", "/api/v1/loans/$target", token)
                withClue("$what sobre $target -> ${response.statusLine}\n${response.body}") {
                    response.status.shouldBe(401)
                    response.leaksNothing(loan.assetId, other.assetId, loan.memberId)
                }
            }
        }
    }

    /**
     * La confusion de credenciales por el otro lado: un **access token de
     * sesion** dado de alta como si fuera un token de prestamo.
     *
     * El filtro prueba primero la sesion, asi que un token de sesion registrado
     * en `loan_access_tokens` para el prestamo de OTRO hogar no puede acabar
     * dando el hogar de la fila: el hogar sale del token de sesion, y con el, el
     * `app.household_id` de las politicas.
     */
    @Test
    @DisplayName("un access token de sesion registrado como token de prestamo no cambia de hogar")
    fun `la sesion no hereda el hogar de la fila`() {
        val mine = http.lendToStranger()
        val alien = http.lendToStranger()

        // La fila apunta al prestamo del hogar ajeno; la credencial es la sesion
        // del propio.
        registerLoanToken(mine.accessToken, alien.loanId, "BORROWER")

        val response = raw("GET", "/api/v1/loans/${alien.loanId}", mine.accessToken)

        withClue("${response.statusLine}\n${response.body}") {
            // 404 y no 200: el prestamo ajeno sigue sin existir para esta sesion.
            response.status.shouldBe(404)
            response.leaksNothing(alien.assetId, alien.memberId)
        }
    }

    // ---------------------------------------------------------------------
    // La proyeccion
    // ---------------------------------------------------------------------

    /**
     * Exactamente siete campos, en las dos respuestas y tambien en el error.
     *
     * `LoanTokenScopeTest` comprueba que no aparecen los campos prohibidos que
     * hoy existen. Esto es lo contrario y no envejece: la lista de los
     * **permitidos** es cerrada, asi que un campo nuevo en `ExternalLoanResponse`
     * rompe esta prueba aunque nadie se acuerde de anadirlo a ninguna lista
     * negra.
     */
    @Test
    @DisplayName("las dos respuestas y el 409 llevan exactamente los campos previstos y nada del hogar")
    fun `la proyeccion acotada no se ensancha por ninguna de las dos salidas`() {
        val loan = http.lendToStranger()
        // El hogar sale del propio access token, que es el unico sitio donde
        // esta: la API no lo devuelve nunca. Aqui hace de secreto a vigilar.
        val householdId = loan.accessToken.claimOf("householdId")

        val read = raw("GET", "/api/v1/loans/${loan.loanId}", loan.token)
        read.status.shouldBe(200)
        read.body.externalKeys().shouldBe(EXTERNAL_FIELDS)
        read.leaksNothing(loan.assetId, loan.memberId, householdId)

        val returned = raw("POST", "/api/v1/loans/${loan.loanId}/return", loan.token)
        returned.status.shouldBe(200)
        returned.body.externalKeys().shouldBe(EXTERNAL_FIELDS)
        returned.leaksNothing(loan.assetId, loan.memberId, householdId)
        // Y la cabecera: un `Location` con el asset dentro seria una fuga que el
        // cuerpo no delata.
        withClue(returned.headerBlock) {
            returned.headerBlock.lowercase().shouldNotContain("location:")
        }

        // El conflicto de la segunda devolucion. Un mensaje de error es el sitio
        // donde mas facil se escapa un identificador.
        val conflict = raw("POST", "/api/v1/loans/${loan.loanId}/return", loan.token)
        conflict.status.shouldBe(409)
        conflict.body.extract("code").shouldBe("LOAN_ALREADY_RETURNED")
        // Los tres campos de `ErrorResponse` y ninguno mas. `details` sale a nulo
        // --solo lo rellena el 400 de validacion-- y esa forma es la del contrato
        // para cualquier error, no una filtracion.
        conflict.body.externalKeys().shouldBe(setOf("code", "message", "details"))
        conflict.body.shouldContain("\"details\":null")
        conflict.leaksNothing(loan.assetId, loan.memberId, householdId)

        // Leer despues de devolver sigue valiendo --el GET es idempotente-- y no
        // se ensancha.
        val reread = raw("GET", "/api/v1/loans/${loan.loanId}", loan.token)
        reread.status.shouldBe(200)
        reread.body.extract("status").shouldBe("RETURNED")
        reread.body.externalKeys().shouldBe(EXTERNAL_FIELDS)
        reread.leaksNothing(loan.assetId, loan.memberId, householdId)

        // Y el 401, que es lo que responde el token fuera de su alcance: tampoco
        // dice de que hogar se trata. El asset va en la ruta pedida, asi que no
        // entra en la lista: lo que no puede volver es lo que no se pregunto.
        val denied = raw("GET", "/api/v1/assets/${loan.assetId}", loan.token)
        denied.status.shouldBe(401)
        denied.leaksNothing(loan.memberId, householdId)
    }

    /**
     * Dos devoluciones a la vez con el mismo token.
     *
     * El token viaja por correo, y un correo se abre desde dos sitios o se pulsa
     * dos veces. `ConfirmReturn` lee el prestamo, comprueba que esta abierto y
     * escribe: entre la lectura y la escritura cabe otra transaccion haciendo lo
     * mismo, asi que la pregunta es si el contrato --`409` a la segunda-- se
     * sostiene cuando las dos son simultaneas.
     */
    @Test
    @DisplayName("varias devoluciones simultaneas con el mismo token: solo una cierra el prestamo")
    fun `la devolucion concurrente no se duplica`() {
        val loan = http.lendToStranger()
        val attempts = 4
        val barrier = CyclicBarrier(attempts)
        val pool = Executors.newFixedThreadPool(attempts)

        val statuses = try {
            pool.invokeAll(
                (1..attempts).map {
                    Callable {
                        barrier.await(30, TimeUnit.SECONDS)
                        raw("POST", "/api/v1/loans/${loan.loanId}/return", loan.token).status
                    }
                },
            ).map { it.get(60, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }

        withClue("estados de las $attempts devoluciones simultaneas: $statuses") {
            // Una sola cierra; las demas ven el prestamo ya cerrado. Mas de un
            // 200 significaria dos cierres del mismo prestamo, con su evento
            // `LoanReturned` publicado dos veces y el `returnedAt` pisado.
            statuses.count { it == 200 }.shouldBe(1)
            statuses.count { it == 409 }.shouldBe(attempts - 1)
        }
    }

    // ---------------------------------------------------------------------
    // Utillaje
    // ---------------------------------------------------------------------

    /**
     * Un cliente HTTP de socket pelado.
     *
     * `TestRestTemplate` normaliza y recodifica la ruta antes de mandarla, de
     * modo que la mitad de los intentos de este fichero no llegarian a Tomcat tal
     * y como se escribieron: la prueba pasaria sin haber probado nada. Aqui la
     * linea de peticion se escribe literal.
     */
    private fun raw(
        method: String,
        target: String,
        token: String?,
        extraHeaders: List<String> = emptyList(),
    ): RawResponse {
        val request = buildString {
            append("$method $target HTTP/1.1\r\n")
            append("Host: localhost:$port\r\n")
            token?.let { append("Authorization: Bearer $it\r\n") }
            append("Accept: application/json\r\n")
            extraHeaders.forEach { append("$it\r\n") }
            if (method.uppercase() in METHODS_WITH_BODY) {
                append("Content-Type: application/json\r\n")
                append("Content-Length: 0\r\n")
            }
            // Sin reutilizacion de conexion: asi el cierre del servidor marca el
            // final del cuerpo y no hace falta interpretar el troceado.
            append("Connection: close\r\n\r\n")
        }

        val raw = Socket("127.0.0.1", port).use { socket ->
            socket.soTimeout = SOCKET_TIMEOUT_MILLIS
            socket.getOutputStream().apply {
                write(request.toByteArray(Charsets.ISO_8859_1))
                flush()
            }
            socket.getInputStream().readBytes().toString(Charsets.UTF_8)
        }

        val separator = raw.indexOf("\r\n\r\n")
        val head = if (separator < 0) raw else raw.substring(0, separator)
        val body = if (separator < 0) "" else raw.substring(separator + 4)

        return RawResponse(
            statusLine = head.lineSequence().firstOrNull().orEmpty(),
            headerBlock = head,
            body = body,
        )
    }

    private data class RawResponse(val statusLine: String, val headerBlock: String, val body: String) {

        /** 0 cuando el servidor cerro sin contestar, que tambien es un resultado. */
        val status: Int get() = statusLine.split(" ").getOrNull(1)?.toIntOrNull() ?: 0

        /**
         * Nada del hogar en ninguna parte de la respuesta, cabeceras incluidas.
         *
         * Se mira el intercambio entero y no solo el cuerpo porque una fuga por
         * `Location`, por `ETag` o por un mensaje de error no aparece en el JSON.
         *
         * Los secretos se pasan uno a uno y **nunca se incluye lo que iba en la
         * ruta pedida**: el cuerpo de error por defecto de Spring Boot lleva
         * dentro el `path`, asi que un identificador que el propio cliente
         * escribio vuelve en la respuesta sin que eso sea una fuga de nada. Lo
         * que no puede volver es lo que el cliente no sabia.
         */
        fun leaksNothing(vararg secrets: String) {
            val whole = "$headerBlock\n$body"

            secrets.forEach { secret -> withClue("se escapa $secret") { whole.shouldNotContain(secret) } }

            // Y los nombres de campo de la proyeccion completa. Que aparezca uno
            // solo significa que se sirvio un `LoanResponse` donde tocaba un
            // `ExternalLoanResponse`, aunque el valor concreto no delatase nada.
            FULL_PROJECTION_MARKERS.forEach { marker ->
                withClue("sale un campo de la proyeccion completa: $marker") { whole.shouldNotContain(marker) }
            }
        }
    }

    /** Un segundo prestamo del **mismo hogar**, para los cruces entre prestamos. */
    private fun TestRestTemplate.secondLoanOf(first: LentToStranger): LentToStranger {
        val assetId = createDurable(first.accessToken, "Sierra de calar")
        val created = postJson(
            "/api/v1/loans",
            """
            {"assetId":"$assetId",
             "lender":{"userId":"${first.memberId}"},
             "borrower":{"userId":"${first.memberId}"}}
            """.trimIndent(),
            first.accessToken,
        )
        check(created.statusCode == HttpStatus.CREATED) { "No se pudo prestar el segundo: ${created.body}" }

        return first.copy(loanId = created.body!!.extract("id"), assetId = assetId)
    }

    /**
     * Da de alta a mano la fila de un token forjado.
     *
     * Es lo que permite comprobar por separado la firma y la fila. Se escribe con
     * la conexion del propietario porque `loan_access_tokens` no tiene politica
     * --cuelga del prestamo, no del hogar-- y no hay contexto de inquilino que
     * fijar.
     */
    private fun registerLoanToken(token: String, loanId: String, role: String) {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray())
            .joinToString("") { "%02x".format(it) }

        DrpPostgres.instance.ownerConnection().use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO loan_access_tokens (id, loan_id, token_hash, role, expires_at, used_at)
                VALUES (?::uuid, ?::uuid, ?, ?, now() + interval '30 days', NULL)
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, UUID.randomUUID().toString())
                statement.setString(2, loanId)
                statement.setString(3, hash)
                statement.setString(4, role)
                statement.executeUpdate()
            }
        }
    }

    /** Un JWT HS256 firmado con la clave que las pruebas comparten con la aplicacion. */
    private fun signed(
        key: String = DEVELOPMENT_SECRET,
        expiresAt: Date?,
        claims: (JWTClaimsSet.Builder) -> JWTClaimsSet.Builder,
    ): String {
        val builder = JWTClaimsSet.Builder().issueTime(Date())
        expiresAt?.let { builder.expirationTime(it) }

        return SignedJWT(JWSHeader(JWSAlgorithm.HS256), claims(builder).build())
            .apply { sign(MACSigner(key.toByteArray(Charsets.UTF_8))) }
            .serialize()
    }

    private fun base64Url(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray())

    private companion object {
        /**
         * La clave de firma del `application.yml`, que es la que usan las pruebas
         * por no haber `DRP_JWT_SECRET` en el entorno. Estar aqui es lo que
         * permite forjar tokens con firma buena y medir que **no basta con la
         * firma**.
         */
        const val DEVELOPMENT_SECRET = "desarrollo-local-no-usar-en-produccion-32b+"

        const val SOCKET_TIMEOUT_MILLIS = 30_000

        val METHODS_WITH_BODY = setOf("POST", "PUT", "PATCH")

        /** Los siete campos de `ExternalLoanResponse`, y ninguno mas. */
        val EXTERNAL_FIELDS = setOf("id", "assetName", "role", "status", "startedAt", "dueAt", "returnedAt")

        /**
         * Campos que solo existen en `LoanResponse`. Uno solo de estos en una
         * respuesta al token acotado significa que se sirvio la proyeccion
         * completa.
         */
        val FULL_PROJECTION_MARKERS = listOf(
            "\"lender\"",
            "\"borrower\"",
            "\"assetId\"",
            "\"notes\"",
            "\"createdBy\"",
            "\"updatedBy\"",
        )
    }
}

/** Un claim del access token, leido del propio token porque la API no lo devuelve. */
private fun String.claimOf(name: String): String =
    String(Base64.getUrlDecoder().decode(split(".")[1])).extract(name)

/**
 * Las claves del JSON plano de la respuesta.
 *
 * Por expresion regular y no con Jackson, por lo mismo que `extract`: mapear la
 * respuesta con las clases que la produjeron haria que las dos partes se
 * equivocaran igual y el fallo pasara inadvertido. Aqui ademas hace falta lo
 * contrario a mapear --ver **todo** lo que salio, incluido lo que ninguna clase
 * de prueba declara.
 */
private fun String.externalKeys(): Set<String> =
    Regex("\"([A-Za-z][A-Za-z0-9_]*)\"\\s*:").findAll(this).map { it.groupValues[1] }.toSet()
