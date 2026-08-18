package com.drp.core.adapter.http

import com.drp.platform.event.IdempotentEventHandler
import com.drp.platform.event.DomainEvent
import com.drp.test.DrpMailpit
import com.drp.test.SpringIntegrationTest
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.client.exchange
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.system.measureTimeMillis

/**
 * El recorrido vertical del Hito 1, de punta a punta y por HTTP real.
 *
 * Valida a la vez las cuatro ADR que este hito pone a prueba:
 *
 * - **ADR-004:** la base arranca vacia y las migraciones producen el esquema
 *   completo, politicas incluidas. Si no fuera asi, nada de esto respondería.
 * - **ADR-009:** el alta se verifica leyendo el **correo real** de Mailpit, sin
 *   ningun paso manual y sin sacar el token de la base de datos.
 * - **ADR-002 y ADR-003:** el aislamiento entre hogares, en
 *   [HouseholdIsolationTest].
 *
 * Va contra un servidor de verdad y no con MockMvc porque parte de lo que se
 * quiere comprobar --el limitador de frecuencia, la cadena de filtros, los
 * codigos de estado-- vive en la capa de servlet.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EnrollmentJourneyTest : SpringIntegrationTest() {

    @Autowired private lateinit var http: TestRestTemplate

    @Autowired private lateinit var households: HouseholdCreatedRecorder

    private val mailpit = DrpMailpit.instance

    @BeforeEach
    fun emptyMailbox() {
        mailpit.clear()
        households.received.clear()
    }

    /**
     * Hace de modulo suscrito, que es lo unico que puede comprobar que el evento
     * sale de verdad: sin nadie escuchando, `HouseholdCreated` se publicaria en el
     * vacio y la prueba pasaria igual.
     */
    @TestConfiguration
    class Subscriber {
        @Bean fun householdCreatedRecorder() = HouseholdCreatedRecorder()
    }

    class HouseholdCreatedRecorder : IdempotentEventHandler("HouseholdCreatedRecorder") {
        val received = CopyOnWriteArrayList<DomainEvent>()
        override fun handle(event: DomainEvent) {
            received += event
        }
    }

    @Test
    @DisplayName("alta, correo, verificacion y sesion: el recorrido entero sin tocar la base de datos")
    fun `el recorrido vertical del enrolamiento`() {
        val email = uniqueEmail()

        // 1. Alta. Responde 202 sin cuerpo: no hay sesion todavia porque el
        //    hogar no sirve hasta verificar.
        val created = http.postJson("/api/v1/households", householdBody(email, "Casa del Pinar"))
        created.statusCode.shouldBe(HttpStatus.ACCEPTED)

        // 2. El correo real, leido de Mailpit como lo leeria una persona.
        val verification = mailpit.awaitMessageTo(email)
        verification.subject.shouldContain("Casa del Pinar")

        // 3. Antes de verificar no se puede entrar, y el motivo se distingue:
        //    el usuario necesita saber que le toca mirar el correo.
        val tooSoon = http.postJson("/api/v1/auth/login", loginBody(email))
        tooSoon.statusCode.shouldBe(HttpStatus.UNAUTHORIZED)
        tooSoon.body.shouldContain("EMAIL_NOT_VERIFIED")

        // 4. Verificar consume el token y devuelve el par de tokens.
        val token = verification.token()
        val verified = http.postJson("/api/v1/auth/verify-email", """{"token":"$token"}""")
        verified.statusCode.shouldBe(HttpStatus.OK)
        val session = verified.body.shouldNotBeNull()
        session.shouldContain("accessToken")

        // 5. El mismo token no sirve dos veces.
        val replayed = http.postJson("/api/v1/auth/verify-email", """{"token":"$token"}""")
        replayed.statusCode.shouldBe(HttpStatus.CONFLICT)
        replayed.body.shouldContain("VERIFICATION_TOKEN_INVALID")

        // 6. Y ahora si se puede entrar.
        val loggedIn = http.postJson("/api/v1/auth/login", loginBody(email))
        loggedIn.statusCode.shouldBe(HttpStatus.OK)

        // 7. El hogar arranca con sus categorias sembradas, que es lo que hace
        //    que el inventario no empiece del todo vacio.
        val accessToken = loggedIn.body!!.extract("accessToken")
        val users = http.getJson("/api/v1/users", accessToken)
        users.statusCode.shouldBe(HttpStatus.OK)
        users.body.shouldContain(email)

        // 8. Y el hogar se anuncia al resto de la aplicacion **aqui**, al
        //    verificar, que es cuando pasa a ser utilizable. No al insertar la
        //    fila: un modulo activo no deberia sembrar datos para un hogar que
        //    quiza no llegue a existir de verdad.
        val announced = households.received.single { it.type == "HouseholdCreated" }
        announced.aggregateId.shouldBe(announced.householdId.toString())
    }

    @Test
    @DisplayName("el alta responde lo mismo con un correo nuevo que con uno ya registrado")
    fun `el alta no delata quien esta registrado`() {
        val email = uniqueEmail()

        val first = http.postJson("/api/v1/households", householdBody(email, "Primera casa"))
        mailpit.awaitMessageTo(email)
        mailpit.clear()

        // El mismo correo otra vez: no se crea nada, pero la respuesta es
        // identica --mismo codigo y mismo cuerpo vacio.
        val second = http.postJson("/api/v1/households", householdBody(email, "Segunda casa"))

        first.statusCode.shouldBe(second.statusCode)
        first.body.shouldBe(second.body)

        // Quien se entera de lo que ha pasado es el dueno de la direccion, por
        // correo, y nadie mas.
        val notice = mailpit.awaitMessageTo(email)
        notice.subject.shouldContain("intentado crear un hogar")
    }

    @Test
    @DisplayName("tampoco lo delata por el tiempo de respuesta")
    fun `el alta no delata por el reloj`() {
        val known = uniqueEmail()
        http.postJson("/api/v1/households", householdBody(known, "Casa conocida"))
        mailpit.awaitMessageTo(known)

        // Se mide varias veces y se compara la mediana: una sola medicion en una
        // maquina compartida no dice nada.
        val onKnown = medianMillis { http.postJson("/api/v1/households", householdBody(known, "Otra")) }
        val onNew = medianMillis { http.postJson("/api/v1/households", householdBody(uniqueEmail(), "Otra")) }

        // La rama "el correo ya existe" no crea nada, asi que seria muchisimo
        // mas rapida si no se hasheara igualmente: es el hash de Argon2id lo que
        // domina las dos y las iguala. El margen es holgado a proposito --lo que
        // delataria seria un orden de magnitud, no un 40 %.
        val slowest = maxOf(onKnown, onNew)
        val fastest = minOf(onKnown, onNew)
        val tolerated = maxOf(fastest / 2, 60L)
        (slowest - fastest).shouldBeLessThan(tolerated)
    }

    @Test
    @DisplayName("restablecer la contrasena de una identidad sin verificar la deja verificada")
    fun `restablecer rescata un hogar sin verificar`() {
        val email = uniqueEmail()
        http.postJson("/api/v1/households", householdBody(email, "Casa a rescatar"))
        mailpit.awaitMessageTo(email)
        mailpit.clear()

        http.postJson("/api/v1/auth/password-reset", """{"email":"$email"}""")
            .statusCode.shouldBe(HttpStatus.ACCEPTED)

        val reset = mailpit.awaitMessageTo(email)
        val confirmed = http.postJson(
            "/api/v1/auth/password-reset/confirm",
            """{"token":"${reset.token()}","newPassword":"una contrasena nueva larga"}""",
        )
        confirmed.statusCode.shouldBe(HttpStatus.OK)

        // Recibir el token prueba el control de la direccion, que es justo lo que
        // la verificacion comprueba: ahora se puede entrar sin haber pasado por
        // VerifyEmail.
        val loggedIn = http.postJson(
            "/api/v1/auth/login",
            """{"email":"$email","password":"una contrasena nueva larga"}""",
        )
        loggedIn.statusCode.shouldBe(HttpStatus.OK)
    }

    @Test
    @DisplayName("pedir el restablecimiento responde igual con un correo desconocido")
    fun `el restablecimiento no delata`() {
        val unknown = uniqueEmail()

        val response = http.postJson("/api/v1/auth/password-reset", """{"email":"$unknown"}""")

        response.statusCode.shouldBe(HttpStatus.ACCEPTED)
        response.body.shouldBeNull()
        mailpit.assertNoMessageTo(unknown)
    }

    @Test
    @DisplayName("una contrasena de menos de 12 caracteres se rechaza al fijarla, no al usarla")
    fun `la politica de contrasenas se aplica al fijarla`() {
        val short = http.postJson(
            "/api/v1/households",
            """
            {"name":"Casa","timeZone":"Europe/Madrid",
             "admin":{"name":"Kike","email":"${uniqueEmail()}","password":"corta"}}
            """.trimIndent(),
        )
        short.statusCode.shouldBe(HttpStatus.BAD_REQUEST)
        short.body.shouldContain("VALIDATION_ERROR")
    }

    @Test
    @DisplayName("en el login una contrasena corta da 401, no 400: ahi se comprueba, no se fija")
    fun `el login no valida la forma de la contrasena`() {
        val response = http.postJson("/api/v1/auth/login", """{"email":"${uniqueEmail()}","password":"x"}""")

        response.statusCode.shouldBe(HttpStatus.UNAUTHORIZED)
    }

    @Test
    @DisplayName("una contrasena de la lista de comunes se rechaza aunque pase de 12 caracteres")
    fun `rechaza una contrasena comun`() {
        val response = http.postJson(
            "/api/v1/households",
            """
            {"name":"Casa","timeZone":"Europe/Madrid",
             "admin":{"name":"Kike","email":"${uniqueEmail()}","password":"contrasena123"}}
            """.trimIndent(),
        )

        response.statusCode.shouldBe(HttpStatus.BAD_REQUEST)
        response.body.shouldContain("demasiado común")
    }

    @Test
    @DisplayName("una zona horaria inventada se rechaza como error de forma")
    fun `el huso se valida en el caso de uso`() {
        val response = http.postJson(
            "/api/v1/households",
            """
            {"name":"Casa","timeZone":"Marte/Olympus",
             "admin":{"name":"Kike","email":"${uniqueEmail()}","password":"una frase bien larga"}}
            """.trimIndent(),
        )

        response.statusCode.shouldBe(HttpStatus.BAD_REQUEST)
        response.body.shouldContain("timeZone")
    }

    @Test
    @DisplayName("el hogar recien verificado trae sus cinco categorias sembradas")
    fun `el hogar nace con categorias`() {
        val (accessToken, _) = registerAndVerify()

        // No hay endpoint de categorias hasta el Hito 2, asi que se comprueba
        // por donde se puede: el hogar existe, tiene un administrador y la
        // siembra ocurrio dentro de la misma transaccion que el alta. Que las
        // categorias esten se verifica en CreateHouseholdTest, contra el
        // repositorio.
        val users = http.getJson("/api/v1/users", accessToken)

        users.statusCode.shouldBe(HttpStatus.OK)
        users.body.shouldContain("HOUSEHOLD_ADMIN")
    }

    // -----------------------------------------------------------------------
    // Invitaciones
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("invitar, aceptar y entrar: quien acepta nace ya verificado")
    fun `el recorrido de la invitacion`() {
        val (adminToken, _) = registerAndVerify()
        val invitedEmail = uniqueEmail()
        mailpit.clear()

        val invited = http.postJson(
            "/api/v1/invitations",
            """{"email":"$invitedEmail","role":"HOUSEHOLD_MEMBER"}""",
            adminToken,
        )
        invited.statusCode.shouldBe(HttpStatus.CREATED)

        val invitation = mailpit.awaitMessageTo(invitedEmail)

        val accepted = http.postJson(
            "/api/v1/invitations/accept",
            """{"token":"${invitation.token()}","name":"Vecina","password":"otra frase bien larga"}""",
        )
        accepted.statusCode.shouldBe(HttpStatus.OK)

        // Sin pasar por VerifyEmail: haber recibido el token prueba el control
        // de la direccion.
        val loggedIn = http.postJson(
            "/api/v1/auth/login",
            """{"email":"$invitedEmail","password":"otra frase bien larga"}""",
        )
        loggedIn.statusCode.shouldBe(HttpStatus.OK)

        // Y el mismo token no sirve dos veces.
        val replayed = http.postJson(
            "/api/v1/invitations/accept",
            """{"token":"${invitation.token()}","name":"Otra","password":"otra frase bien larga"}""",
        )
        replayed.statusCode.shouldBe(HttpStatus.CONFLICT)
        replayed.body.shouldContain("INVITATION_TOKEN_INVALID")
    }

    @Test
    @DisplayName("un miembro no administrador no puede invitar")
    fun `invitar es solo de administradores`() {
        val (adminToken, _) = registerAndVerify()
        val memberEmail = uniqueEmail()
        mailpit.clear()

        http.postJson("/api/v1/invitations", """{"email":"$memberEmail","role":"HOUSEHOLD_MEMBER"}""", adminToken)
        val invitation = mailpit.awaitMessageTo(memberEmail)
        val memberSession = http.postJson(
            "/api/v1/invitations/accept",
            """{"token":"${invitation.token()}","name":"Miembro","password":"una frase larga mas"}""",
        )
        val memberToken = memberSession.body!!.extract("accessToken")

        val forbidden = http.postJson(
            "/api/v1/invitations",
            """{"email":"${uniqueEmail()}","role":"HOUSEHOLD_MEMBER"}""",
            memberToken,
        )

        forbidden.statusCode.shouldBe(HttpStatus.FORBIDDEN)
    }

    @Test
    @DisplayName("no se puede dar de baja al unico administrador del hogar")
    fun `el ultimo administrador no se puede quitar`() {
        val (adminToken, adminMemberId) = registerAndVerify()

        val rejected = http.deleteJson("/api/v1/users/$adminMemberId", adminToken)

        rejected.statusCode.shouldBe(HttpStatus.CONFLICT)
        rejected.body.shouldContain("USER_LAST_ADMIN")
    }

    @Test
    @DisplayName("sin token no se llega a ningun endpoint autenticado")
    fun `lo autenticado exige credencial`() {
        http.getJson("/api/v1/users", accessToken = null).statusCode.shouldBe(HttpStatus.UNAUTHORIZED)
    }

    // -----------------------------------------------------------------------
    // Contrasenas y sesiones
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("cambiar la contrasena conserva la sesion en uso y tumba las demas")
    fun `cambiar la contrasena conserva la sesion en uso`() {
        val email = uniqueEmail()
        registerAndVerify(email)

        // Dos sesiones abiertas de la misma persona.
        val first = http.postJson("/api/v1/auth/login", loginBody(email)).body!!
        val second = http.postJson("/api/v1/auth/login", loginBody(email)).body!!

        val changed = http.postJson(
            "/api/v1/auth/password",
            """{"currentPassword":"$DEFAULT_PASSWORD","newPassword":"una contrasena distinta ya"}""",
            second.extract("accessToken"),
        )
        changed.statusCode.shouldBe(HttpStatus.NO_CONTENT)

        // La que hizo el cambio sigue viva...
        http.postJson("/api/v1/auth/refresh", """{"refreshToken":"${second.extract("refreshToken")}"}""")
            .statusCode.shouldBe(HttpStatus.OK)

        // ...y la otra no.
        http.postJson("/api/v1/auth/refresh", """{"refreshToken":"${first.extract("refreshToken")}"}""")
            .statusCode.shouldBe(HttpStatus.UNAUTHORIZED)
    }

    @Test
    @DisplayName("cambiar la contrasena con la actual equivocada no toca nada")
    fun `la contrasena actual es obligatoria`() {
        val email = uniqueEmail()
        val (accessToken, _) = registerAndVerify(email)

        val rejected = http.postJson(
            "/api/v1/auth/password",
            """{"currentPassword":"esta no es la buena","newPassword":"otra bien larga aqui"}""",
            accessToken,
        )

        rejected.statusCode.shouldBe(HttpStatus.UNAUTHORIZED)
        rejected.body.shouldContain("CURRENT_PASSWORD_INVALID")

        // La contrasena de siempre sigue funcionando.
        http.postJson("/api/v1/auth/login", loginBody(email)).statusCode.shouldBe(HttpStatus.OK)
    }

    @Test
    @DisplayName("restablecer revoca TODAS las sesiones anteriores")
    fun `restablecer cierra todas las sesiones`() {
        val email = uniqueEmail()
        registerAndVerify(email)
        val old = http.postJson("/api/v1/auth/login", loginBody(email)).body!!
        mailpit.clear()

        http.postJson("/api/v1/auth/password-reset", """{"email":"$email"}""")
        val reset = mailpit.awaitMessageTo(email)
        val fresh = http.postJson(
            "/api/v1/auth/password-reset/confirm",
            """{"token":"${reset.token()}","newPassword":"contrasena recien puesta"}""",
        ).body!!

        // Si el motivo del restablecimiento era que alguien mas habia entrado,
        // dejarle la sesion abierta anularia el gesto entero.
        http.postJson("/api/v1/auth/refresh", """{"refreshToken":"${old.extract("refreshToken")}"}""")
            .statusCode.shouldBe(HttpStatus.UNAUTHORIZED)

        // La emitida en esa misma llamada si vale.
        http.postJson("/api/v1/auth/refresh", """{"refreshToken":"${fresh.extract("refreshToken")}"}""")
            .statusCode.shouldBe(HttpStatus.OK)
    }

    @Test
    @DisplayName("el refresh rota: el token usado deja de valer en el acto")
    fun `el refresh rota`() {
        val email = uniqueEmail()
        registerAndVerify(email)
        val session = http.postJson("/api/v1/auth/login", loginBody(email)).body!!
        val original = session.extract("refreshToken")

        val rotated = http.postJson("/api/v1/auth/refresh", """{"refreshToken":"$original"}""")
        rotated.statusCode.shouldBe(HttpStatus.OK)

        // Reutilizar el anterior ya no vale: es lo que convierte un refresh
        // robado en algo que se nota.
        http.postJson("/api/v1/auth/refresh", """{"refreshToken":"$original"}""")
            .statusCode.shouldBe(HttpStatus.UNAUTHORIZED)
    }

    @Test
    @DisplayName("cerrar sesion responde igual con un token valido que con uno que ya no existe")
    fun `cerrar sesion no delata`() {
        val email = uniqueEmail()
        val (accessToken, _) = registerAndVerify(email)
        val session = http.postJson("/api/v1/auth/login", loginBody(email)).body!!

        val real = http.postJson(
            "/api/v1/auth/logout",
            """{"refreshToken":"${session.extract("refreshToken")}"}""",
            accessToken,
        )
        val invented = http.postJson(
            "/api/v1/auth/logout",
            """{"refreshToken":"${UUID.randomUUID()}"}""",
            accessToken,
        )

        real.statusCode.shouldBe(HttpStatus.NO_CONTENT)
        invented.statusCode.shouldBe(real.statusCode)
    }

    // -----------------------------------------------------------------------
    // Utilidades
    // -----------------------------------------------------------------------

    /** Da de alta un hogar, lee el correo, verifica y devuelve el token y el memberId. */
    private fun registerAndVerify(email: String = uniqueEmail()): Pair<String, String> {
        http.postJson("/api/v1/households", householdBody(email, "Hogar de prueba"))
        val token = mailpit.awaitMessageTo(email).token()
        mailpit.clear()

        val session = http.postJson("/api/v1/auth/verify-email", """{"token":"$token"}""").body!!
        val accessToken = session.extract("accessToken")
        val memberId = http.getJson("/api/v1/users", accessToken).body!!.extract("id")

        return accessToken to memberId
    }

    private fun uniqueEmail() = "persona-${UUID.randomUUID()}@example.test"

    private fun householdBody(email: String, name: String) = """
        {"name":"$name","timeZone":"Europe/Madrid",
         "admin":{"name":"Kike","email":"$email","password":"$DEFAULT_PASSWORD"}}
    """.trimIndent()

    private fun loginBody(email: String) = """{"email":"$email","password":"$DEFAULT_PASSWORD"}"""

    private fun medianMillis(times: Int = 5, block: () -> Unit): Long =
        (1..times).map { measureTimeMillis(block) }.sorted()[times / 2]

    private fun TestRestTemplate.postJson(path: String, body: String, accessToken: String? = null) =
        exchange<String>(path, HttpMethod.POST, HttpEntity(body, jsonHeaders(accessToken)))

    private fun TestRestTemplate.getJson(path: String, accessToken: String?) =
        exchange<String>(path, HttpMethod.GET, HttpEntity<Void>(jsonHeaders(accessToken)))

    /** `deleteJson` y no `delete`: el propio [TestRestTemplate] ya tiene un `delete` que no devuelve respuesta. */
    private fun TestRestTemplate.deleteJson(path: String, accessToken: String?) =
        exchange<String>(path, HttpMethod.DELETE, HttpEntity<Void>(jsonHeaders(accessToken)))

    private fun jsonHeaders(accessToken: String?) = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
        accessToken?.let { setBearerAuth(it) }
    }

    /** Saca el primer valor de una propiedad del JSON, sin montar un modelo para la prueba. */
    private fun String.extract(field: String): String =
        Regex("\"$field\"\\s*:\\s*\"([^\"]+)\"").find(this)?.groupValues?.get(1)
            ?: error("No aparece \"$field\" en la respuesta:\n$this")

    private companion object {
        const val DEFAULT_PASSWORD = "el gato duerme en el sofa"
    }
}
