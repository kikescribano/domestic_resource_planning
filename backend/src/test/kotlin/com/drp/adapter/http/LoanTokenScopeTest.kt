package com.drp.adapter.http

import com.drp.test.DrpMailpit
import com.drp.test.DrpPostgres
import com.drp.test.SpringIntegrationTest
import com.drp.test.deleteJson
import com.drp.test.extract
import com.drp.test.getJson
import com.drp.test.postJson
import com.drp.test.registerHousehold
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import java.util.UUID

/**
 * El alcance del token acotado, medido por sus bordes.
 *
 * Es la credencial mas peculiar del sistema: **no identifica a una persona** y
 * tiene que alcanzar exactamente dos operaciones de un prestamo y nada mas. Esta
 * prueba comprueba las dos que alcanza y, sobre todo, la lista de las que no.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LoanTokenScopeTest : SpringIntegrationTest() {

    @Autowired private lateinit var http: TestRestTemplate

    @Test
    @DisplayName("el enlace del correo abre el prestamo, y devuelve MENOS campos que la sesion")
    fun `la proyeccion acotada esconde lo que no le toca`() {
        val loan = http.lendToStranger()

        val external = http.getJson("/api/v1/loans/${loan.loanId}", loan.token)
        external.statusCode.shouldBe(HttpStatus.OK)

        val body = external.body!!
        body.extract("assetName").shouldBe("Taladro")
        body.extract("status").shouldBe("ACTIVE")
        body.extract("role").shouldBe("BORROWER")

        // Y lo que no puede salir de casa. Cada uno de estos es una via para
        // saber algo del hogar que presto: quien es, que mas tiene, o que se
        // apunto sobre el prestamo.
        body.shouldNotContain("\"lender\"")
        body.shouldNotContain("\"borrower\"")
        body.shouldNotContain("\"assetId\"")
        body.shouldNotContain("\"notes\"")
        body.shouldNotContain("\"createdBy\"")
        body.shouldNotContain(loan.assetId)

        // La misma peticion con sesion del hogar si devuelve todo eso: la
        // diferencia esta en la credencial y no en el recurso.
        val full = http.getJson("/api/v1/loans/${loan.loanId}", loan.accessToken).body!!
        full.shouldContain("\"lender\"")
        full.shouldContain(loan.assetId)
    }

    @Test
    @DisplayName("con el token no se llega a NADA mas del hogar")
    fun `el token no alcanza ninguna otra cosa`() {
        val loan = http.lendToStranger()

        // Cada una de estas es una pregunta distinta al mismo hogar, y las
        // catorce tienen que responder igual: aqui esto no es una credencial.
        val forbidden = listOf(
            "GET" to "/api/v1/loans",
            "GET" to "/api/v1/loans?open=true",
            "GET" to "/api/v1/assets",
            "GET" to "/api/v1/assets/${loan.assetId}",
            "GET" to "/api/v1/categories",
            "GET" to "/api/v1/articles",
            "GET" to "/api/v1/locations",
            "GET" to "/api/v1/documents",
            "GET" to "/api/v1/files",
            "GET" to "/api/v1/storage",
            "GET" to "/api/v1/users",
            "GET" to "/api/v1/invitations",
        )

        forbidden.forEach { (method, path) ->
            val response = if (method == "GET") http.getJson(path, loan.token) else http.getJson(path, loan.token)
            withClue(path) { response.statusCode.shouldBe(HttpStatus.UNAUTHORIZED) }
        }

        // Y tampoco escribe.
        http.postJson("/api/v1/categories", """{"name":"Intrusa"}""", loan.token)
            .statusCode.shouldBe(HttpStatus.UNAUTHORIZED)
        http.deleteJson("/api/v1/assets/${loan.assetId}", loan.token)
            .statusCode.shouldBe(HttpStatus.UNAUTHORIZED)
    }

    @Test
    @DisplayName("el token de un prestamo no abre otro, ni del mismo hogar")
    fun `un token solo vale para su prestamo`() {
        val first = http.lendToStranger()

        // Segundo prestamo del MISMO hogar, con otro asset.
        val otherAsset = http.createDurable(first.accessToken, "Sierra de calar")
        val second = http.postJson(
            "/api/v1/loans",
            """
            {"assetId":"$otherAsset",
             "lender":{"userId":"${first.memberId}"},
             "borrower":{"userId":"${first.memberId}"}}
            """.trimIndent(),
            first.accessToken,
        )
        second.statusCode.shouldBe(HttpStatus.CREATED)
        val otherLoanId = second.body!!.extract("id")

        // El token del primero no llega al segundo. Sin la comparacion del
        // identificador de la ruta contra el del token, esto habria pasado.
        http.getJson("/api/v1/loans/$otherLoanId", first.token)
            .statusCode.shouldBe(HttpStatus.UNAUTHORIZED)
        http.postJson("/api/v1/loans/$otherLoanId/return", "", first.token)
            .statusCode.shouldBe(HttpStatus.UNAUTHORIZED)
    }

    @Test
    @DisplayName("devolver con el token cierra el prestamo, libera el asset y gasta el token")
    fun `el externo confirma la devolucion`() {
        val loan = http.lendToStranger()

        val returned = http.postJson("/api/v1/loans/${loan.loanId}/return", "", loan.token)

        returned.statusCode.shouldBe(HttpStatus.OK)
        returned.body!!.extract("status").shouldBe("RETURNED")
        // La devolucion no ensancha lo que ese token ve.
        returned.body!!.shouldNotContain("\"lender\"")

        // El asset vuelve a casa.
        http.getJson("/api/v1/assets/${loan.assetId}", loan.accessToken).body!!
            .extract("status").shouldBe("AVAILABLE")

        // El token queda gastado, pero **sigue sirviendo para leer**: quien acaba
        // de confirmar recarga la pagina para verlo hecho.
        usedAtOf(loan.loanId).shouldBe(true)
        val reread = http.getJson("/api/v1/loans/${loan.loanId}", loan.token)
        reread.statusCode.shouldBe(HttpStatus.OK)
        reread.body!!.extract("status").shouldBe("RETURNED")

        // Y no se puede devolver dos veces.
        val again = http.postJson("/api/v1/loans/${loan.loanId}/return", "", loan.token)
        again.statusCode.shouldBe(HttpStatus.CONFLICT)
        again.body!!.extract("code").shouldBe("LOAN_ALREADY_RETURNED")
    }

    @Test
    @DisplayName("el hogar tambien puede confirmar la devolucion, y ahi si hay autoria")
    fun `la devolucion desde casa deja autoria`() {
        val loan = http.lendToStranger()

        val returned = http.postJson("/api/v1/loans/${loan.loanId}/return", "", loan.accessToken)

        returned.statusCode.shouldBe(HttpStatus.OK)
        returned.body!!.extract("status").shouldBe("RETURNED")
        returned.body!!.extract("updatedBy").shouldBe(loan.memberId)
    }

    @Test
    @DisplayName("un token revocado deja de valer aunque su firma siga siendo buena")
    fun `borrar la fila revoca el token`() {
        val loan = http.lendToStranger()
        http.getJson("/api/v1/loans/${loan.loanId}", loan.token).statusCode.shouldBe(HttpStatus.OK)

        // Esta es la mitad que un JWT por si solo no puede dar, y la razon de que
        // ademas del token firmado se guarde su hash.
        DrpPostgres.instance.ownerConnection().use { connection ->
            connection.prepareStatement("DELETE FROM loan_access_tokens WHERE loan_id = ?::uuid").use {
                it.setString(1, loan.loanId)
                it.executeUpdate()
            }
        }

        http.getJson("/api/v1/loans/${loan.loanId}", loan.token).statusCode.shouldBe(HttpStatus.UNAUTHORIZED)
    }

    @Test
    @DisplayName("un token caducado no vale, y la fila sigue ahi")
    fun `la caducidad se comprueba`() {
        val loan = http.lendToStranger()

        DrpPostgres.instance.ownerConnection().use { connection ->
            connection.prepareStatement(
                "UPDATE loan_access_tokens SET expires_at = now() - interval '1 day' WHERE loan_id = ?::uuid",
            ).use {
                it.setString(1, loan.loanId)
                it.executeUpdate()
            }
        }

        http.getJson("/api/v1/loans/${loan.loanId}", loan.token).statusCode.shouldBe(HttpStatus.UNAUTHORIZED)
    }

    @Test
    @DisplayName("una firma manipulada no vale, aunque el prestamo exista")
    fun `la firma se comprueba`() {
        val loan = http.lendToStranger()
        val tampered = loan.token.dropLast(4) + "AAAA"

        http.getJson("/api/v1/loans/${loan.loanId}", tampered).statusCode.shouldBe(HttpStatus.UNAUTHORIZED)
    }

    @Test
    @DisplayName("un access token de sesion no vale como token de prestamo, ni al reves")
    fun `las dos credenciales no se confunden`() {
        val loan = http.lendToStranger()

        // El de sesion lleva `sub` y no lleva `loanId`; el de prestamo, al reves.
        // Comparten clave de firma, asi que lo que los separa es la forma del
        // cuerpo y que cada verificador exija la suya.
        http.getJson("/api/v1/assets", loan.token).statusCode.shouldBe(HttpStatus.UNAUTHORIZED)
        http.getJson("/api/v1/loans/${loan.loanId}", loan.accessToken).statusCode.shouldBe(HttpStatus.OK)
    }

    @Test
    @DisplayName("el token de un hogar no abre un prestamo de otro")
    fun `el token no cruza hogares`() {
        val mine = http.lendToStranger()
        val alien = http.lendToStranger()

        http.getJson("/api/v1/loans/${alien.loanId}", mine.token).statusCode.shouldBe(HttpStatus.UNAUTHORIZED)
        // Y con sesion del hogar propio, el prestamo ajeno no existe.
        http.getJson("/api/v1/loans/${alien.loanId}", mine.accessToken).statusCode.shouldBe(HttpStatus.NOT_FOUND)
    }

    private fun usedAtOf(loanId: String): Boolean =
        DrpPostgres.instance.ownerConnection().use { connection ->
            connection.prepareStatement(
                "SELECT used_at IS NOT NULL FROM loan_access_tokens WHERE loan_id = ?::uuid",
            ).use { statement ->
                statement.setString(1, loanId)
                statement.executeQuery().use { rows -> rows.next() && rows.getBoolean(1) }
            }
        }
}

/** Un prestamo a un externo, con el token ya leido del correo como lo leeria una persona. */
internal data class LentToStranger(
    val loanId: String,
    val assetId: String,
    val token: String,
    val accessToken: String,
    val memberId: String,
)

internal fun TestRestTemplate.lendToStranger(): LentToStranger {
    val home = registerHousehold()
    val assetId = createDurable(home.accessToken, "Taladro")
    val vecino = "vecino-${UUID.randomUUID()}@example.test"

    val created = postJson(
        "/api/v1/loans",
        """
        {"assetId":"$assetId",
         "lender":{"userId":"${home.memberId}"},
         "borrower":{"external":{"name":"Vecino del 3.º","email":"$vecino"}},
         "notes":"Con la broca de widia"}
        """.trimIndent(),
        home.accessToken,
    )
    check(created.statusCode == HttpStatus.CREATED) { "No se pudo prestar: ${created.body}" }

    return LentToStranger(
        loanId = created.body!!.extract("id"),
        assetId = assetId,
        token = DrpMailpit.instance.awaitMessageTo(vecino).token(),
        accessToken = home.accessToken,
        memberId = home.memberId,
    )
}
