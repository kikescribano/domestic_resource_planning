package com.drp.core.application.usecase

import com.drp.core.adapter.http.createDurable
import com.drp.platform.schedule.DailySweep
import com.drp.test.DrpPostgres
import com.drp.test.SpringIntegrationTest
import com.drp.test.extract
import com.drp.test.getJson
import com.drp.test.postJson
import com.drp.test.queryOne
import com.drp.test.registerHousehold
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * El tercer proceso diario, y el unico cambio de estado del core que no provoca
 * nadie.
 *
 * Comprueba las cuatro cosas que lo hacen delicado: que marque lo que toca, que
 * **no** toque lo que no, que sea idempotente, y que recorra los hogares sin
 * `BYPASSRLS`, que es lo que la ADR-003 exige demostrar de los tres.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MarkOverdueLoansTest : SpringIntegrationTest() {

    @Autowired private lateinit var sweep: DailySweep
    @Autowired private lateinit var http: TestRestTemplate

    private val postgres = DrpPostgres.instance

    @Test
    @DisplayName("pasa a OVERDUE los ACTIVE con la fecha superada, y deja el asset LENT")
    fun `marca lo vencido sin devolver nada`() {
        val home = http.registerHousehold()
        val assetId = http.createDurable(home.accessToken, "Taladro")
        val loanId = http.lend(home.accessToken, home.memberId, assetId, dueAt = inDays(7))

        expireDue(loanId, daysAgo = 1)
        sweep.run()

        http.getJson("/api/v1/loans/$loanId", home.accessToken).body!!
            .extract("status").shouldBe("OVERDUE")

        // **El asset sigue LENT.** Vencer no es devolver, y esta es la regla que
        // mas facil seria incumplir aqui: el prestamo cambia de estado y quien lo
        // escriba puede dar por hecho que el asset le acompana, como si se hubiera
        // devuelto.
        http.getJson("/api/v1/assets/$assetId", home.accessToken).body!!
            .extract("status").shouldBe("LENT")
    }

    @Test
    @DisplayName("un prestamo sin fecha prevista no vence nunca")
    fun `sin dueAt no hay vencimiento`() {
        val home = http.registerHousehold()
        val assetId = http.createDurable(home.accessToken, "Escalera")
        val loanId = http.lend(home.accessToken, home.memberId, assetId, dueAt = null)

        sweep.run()

        // Es un prestamo sin plazo, no un plazo infinito ya cumplido.
        http.getJson("/api/v1/loans/$loanId", home.accessToken).body!!
            .extract("status").shouldBe("ACTIVE")
    }

    @Test
    @DisplayName("un prestamo con la fecha aun por llegar tampoco")
    fun `el plazo vivo no se toca`() {
        val home = http.registerHousehold()
        val assetId = http.createDurable(home.accessToken, "Sierra")
        val loanId = http.lend(home.accessToken, home.memberId, assetId, dueAt = inDays(7))

        sweep.run()

        http.getJson("/api/v1/loans/$loanId", home.accessToken).body!!
            .extract("status").shouldBe("ACTIVE")
    }

    @Test
    @DisplayName("un prestamo ya devuelto no revive, por vieja que sea su fecha")
    fun `lo devuelto se queda devuelto`() {
        val home = http.registerHousehold()
        val assetId = http.createDurable(home.accessToken, "Martillo")
        val loanId = http.lend(home.accessToken, home.memberId, assetId, dueAt = inDays(7))
        http.postJson("/api/v1/loans/$loanId/return", "", home.accessToken)
            .statusCode.shouldBe(HttpStatus.OK)

        expireDue(loanId, daysAgo = 30)
        sweep.run()

        http.getJson("/api/v1/loans/$loanId", home.accessToken).body!!
            .extract("status").shouldBe("RETURNED")
    }

    @Test
    @DisplayName("la segunda pasada no encuentra nada, asi que no publica nada")
    fun `es idempotente`() {
        val home = http.registerHousehold()
        val assetId = http.createDurable(home.accessToken, "Carretilla")
        val loanId = http.lend(home.accessToken, home.memberId, assetId, dueAt = inDays(7))
        expireDue(loanId, daysAgo = 1)

        val first = sweep.run()
        val second = sweep.run()

        // La idempotencia no es cosmetica: de `LoanOverdue` colgaran los
        // recordatorios, y una pasada que volviera a publicarlo mandaria un aviso
        // al dia a quien ya lo sabe.
        // Se mira por el aviso y no por la cuenta de marcados: desde el Hito 1
        // de la Fase 2 quien recorre es el barrido de plataforma, y lo que
        // devuelve es lo que ha encontrado en toda la instalacion. Un aviso la
        // primera vez y ninguno la segunda dice exactamente lo mismo, y ademas
        // dice la otra mitad --que tampoco se avisa dos veces.
        withClue("la primera pasada tiene que marcar el préstamo y avisar") {
            (first.noticesRaised >= 1).shouldBe(true)
        }
        withClue("la segunda no puede volver a marcarlo ni volver a avisar") {
            second.noticesRaised.shouldBe(0)
        }
    }

    @Test
    @DisplayName("recorre los hogares uno a uno, sin BYPASSRLS y sin mezclarlos")
    fun `marca en todos los hogares sin saltarse el aislamiento`() {
        // Dos hogares distintos, cada uno con su prestamo vencido. Uno solo no
        // demostraria nada: el proceso podria estar leyendo con el contexto que
        // dejo la ultima peticion en vez de fijarlo el.
        val first = overdueLoanInNewHousehold()
        val second = overdueLoanInNewHousehold()

        sweep.run()

        http.getJson("/api/v1/loans/${first.loanId}", first.accessToken).body!!
            .extract("status").shouldBe("OVERDUE")
        http.getJson("/api/v1/loans/${second.loanId}", second.accessToken).body!!
            .extract("status").shouldBe("OVERDUE")

        // Y lo que hace significativo lo anterior: el usuario con el que corre
        // **no** se salta las politicas. Con `BYPASSRLS` esta prueba pasaria
        // igual y no estaria midiendo nada.
        postgres.appConnection().use { application ->
            val privileged = application.queryOne(
                "SELECT rolsuper OR rolbypassrls FROM pg_roles WHERE rolname = current_user",
            ) { it.getBoolean(1) }

            withClue("el proceso corre con un usuario que se salta RLS: la cobertura seria falsa") {
                privileged.shouldBe(false)
            }
        }
    }

    private data class OverdueLoan(val loanId: String, val accessToken: String)

    /** Un prestamo ya vencido en un hogar recien creado. */
    private fun overdueLoanInNewHousehold(): OverdueLoan {
        val home = http.registerHousehold()
        val assetId = http.createDurable(home.accessToken, "Taladro")
        val loanId = http.lend(home.accessToken, home.memberId, assetId, dueAt = inDays(7))
        expireDue(loanId, daysAgo = 1)
        return OverdueLoan(loanId, home.accessToken)
    }

    /**
     * Mueve la fecha prevista al pasado por SQL.
     *
     * La API no acepta una `dueAt` ya pasada --se rechaza con un `400`-- y
     * adelantar el reloj de la aplicacion afectaria a todo lo demas de la prueba.
     * Lo que se quiere medir es el proceso, asi que se le prepara el dato.
     */
    private fun expireDue(loanId: String, daysAgo: Long) {
        postgres.ownerConnection().use { owner ->
            owner.prepareStatement("UPDATE loans SET due_at = ? WHERE id = ?::uuid").use { statement ->
                statement.setObject(1, java.sql.Timestamp.from(Instant.now().minus(daysAgo, ChronoUnit.DAYS)))
                statement.setString(2, loanId)
                statement.executeUpdate()
            }
        }
    }

    private fun inDays(days: Long): Instant = Instant.now().plus(days, ChronoUnit.DAYS)
}

private fun TestRestTemplate.lend(
    accessToken: String,
    memberId: String,
    assetId: String,
    dueAt: Instant?,
): String {
    val due = dueAt?.let { ""","dueAt":"$it"""" } ?: ""
    val created = postJson(
        "/api/v1/loans",
        """{"assetId":"$assetId","lender":{"userId":"$memberId"},"borrower":{"userId":"$memberId"}$due}""",
        accessToken,
    )
    check(created.statusCode == HttpStatus.CREATED) { "No se pudo prestar: ${created.body}" }
    return created.body!!.extract("id")
}
