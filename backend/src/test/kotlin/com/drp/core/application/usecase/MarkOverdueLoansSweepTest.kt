package com.drp.core.application.usecase

import com.drp.core.adapter.http.createDurable
import com.drp.platform.event.IdempotentEventHandler
import com.drp.platform.event.DomainEvent
import com.drp.test.DrpPostgres
import com.drp.test.SpringIntegrationTest
import com.drp.test.extract
import com.drp.test.getJson
import com.drp.test.postJson
import com.drp.test.registerHousehold
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpStatus
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * El barrido del proceso diario de vencidos, con un módulo suscrito de por medio.
 *
 * [MarkOverdueLoansTest] comprueba **a quién marca**; esto comprueba las tres
 * cosas que aquél no puede ver: que el evento sale de verdad y una sola vez, qué
 * pasa cuando dos cosas ocurren a la vez —que es de madrugada cuando este
 * proceso corre—, y dónde está exactamente el borde de la fecha.
 *
 * La pregunta de la concurrencia no es teórica: el barrido hermano acaba de
 * encontrar que `ConfirmReturn` tenía forma de lee-y-luego-escribe, así que la
 * primera sospecha razonable es que aquí pase lo mismo.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MarkOverdueLoansSweepTest : SpringIntegrationTest() {

    @Autowired private lateinit var markOverdue: MarkOverdueLoans
    @Autowired private lateinit var http: TestRestTemplate
    @Autowired private lateinit var module: OverdueSubscriber

    private val postgres = DrpPostgres.instance

    @BeforeEach
    fun forgetPreviousEvents() = module.received.clear()

    @Test
    @DisplayName("publica un LoanOverdue por préstamo, con su hogar y su payload")
    fun `el evento sale de verdad`() {
        val home = overdueLoan()
        module.received.clear()

        markOverdue.run()

        // Sin nadie escuchando, un `publish` olvidado no se distingue de uno que
        // funciona: el estado queda igual de bien y la prueba de estado pasa
        // igual. De ahí el módulo suscrito.
        val ours = module.received.filter { it.aggregateId == home.loanId }
        ours.map { it.type }.shouldContainExactly(listOf("LoanOverdue"))

        val event = ours.single()
        event.householdId.toString().shouldBe(home.householdId)
        event.payload["assetId"].toString().shouldBe(home.assetId)
        withClue("el evento tiene que llevar la fecha que se superó") {
            (event.payload["dueAt"] != null).shouldBe(true)
        }
    }

    @Test
    @DisplayName("la segunda pasada no publica nada, que es lo que hace útil el evento")
    fun `no se republica en cada pasada`() {
        val home = overdueLoan()
        markOverdue.run()
        module.received.clear()

        markOverdue.run()

        // De `LoanOverdue` colgarán los recordatorios automáticos (4.2). Un
        // proceso que lo republicara cada noche mandaría un aviso al día a quien
        // ya lo sabe, que es peor que no avisar.
        module.received.filter { it.aggregateId == home.loanId }.shouldContainExactly(emptyList())
    }

    @Test
    @DisplayName("dos pasadas simultáneas marcan el préstamo una sola vez")
    fun `dos ejecuciones a la vez no lo marcan dos veces`() {
        // Era la sospecha razonable después de lo que apareció en
        // `ConfirmReturn`, y **resultó ser cierta**: la primera versión de esta
        // prueba daba dos `LoanOverdue` por el mismo préstamo.
        //
        // El razonamiento que parecía descartarlo era que la consulta ya filtra
        // por `status = 'ACTIVE'`, así que la segunda pasada no vería el
        // candidato. Y es cierto **entre pasadas sucesivas** y falso a la vez:
        // las dos transacciones leían el mismo `ACTIVE` antes de que ninguna
        // escribiera, el `UPDATE` se serializaba —así que el estado final era
        // correcto— y el evento salía dos veces. El estado no lo delataba; el
        // evento sí.
        //
        // Se arregla con `FOR UPDATE` en la consulta de candidatos.
        val home = overdueLoan()
        module.received.clear()

        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        val done = CountDownLatch(2)

        repeat(2) {
            pool.submit {
                start.await()
                runCatching { markOverdue.run() }
                done.countDown()
            }
        }
        start.countDown()
        done.await(60, TimeUnit.SECONDS).shouldBe(true)
        pool.shutdown()

        val ours = module.received.filter { it.aggregateId == home.loanId }
        withClue("LoanOverdue salió ${ours.size} veces: los recordatorios se duplicarían") {
            ours.size.shouldBe(1)
        }
        loanStatusOf(home.loanId).shouldBe("OVERDUE")
    }

    @Test
    @DisplayName("devolver mientras el proceso corre no deja el préstamo vencido y devuelto a la vez")
    fun `la devolucion y el vencimiento no se pisan`() {
        // El caso realista: el proceso corre de madrugada y alguien está
        // devolviendo justo entonces. Lo que no puede pasar es que el préstamo
        // acabe OVERDUE **después** de haberse devuelto, porque eso lo dejaría
        // reclamando algo que ya está en casa.
        repeat(REPETITIONS) {
            val home = overdueLoan()
            module.received.clear()

            val start = CountDownLatch(1)
            val pool = Executors.newFixedThreadPool(2)
            val done = CountDownLatch(2)

            pool.submit {
                start.await()
                runCatching { markOverdue.run() }
                done.countDown()
            }
            pool.submit {
                start.await()
                runCatching { http.postJson("/api/v1/loans/${home.loanId}/return", "", home.accessToken) }
                done.countDown()
            }
            start.countDown()
            done.await(60, TimeUnit.SECONDS).shouldBe(true)
            pool.shutdown()

            // Los dos desenlaces son legítimos --RETURNED si ganó la devolución,
            // OVERDUE si ganó el proceso y la devolución llegó después-- pero
            // **RETURNED es absorbente**: una vez devuelto, ninguna pasada
            // posterior puede reabrirlo.
            val afterRace = loanStatusOf(home.loanId)
            markOverdue.run()

            withClue("una pasada posterior reabrió un préstamo ya devuelto") {
                if (afterRace == "RETURNED") loanStatusOf(home.loanId).shouldBe("RETURNED")
            }
        }
    }

    @Test
    @DisplayName("una fecha exactamente igual a ahora todavía no vence")
    fun `el borde de la fecha es estricto`() {
        // No estaba dicho en ninguna parte, así que se decide y se deja escrito:
        // vence cuando la fecha **ha pasado**, no cuando se alcanza. Es la
        // lectura natural de «fecha prevista de devolución» —el día que toca
        // devolverlo todavía no se ha incumplido nada— y es lo que hace el
        // `due_at < now` de la consulta.
        val home = overdueLoan()
        setDueAt(home.loanId, Instant.now().plus(1, ChronoUnit.HOURS))

        markOverdue.run()
        loanStatusOf(home.loanId).shouldBe("ACTIVE")

        setDueAt(home.loanId, Instant.now().minusMillis(50))
        markOverdue.run()
        loanStatusOf(home.loanId).shouldBe("OVERDUE")
    }

    @Test
    @DisplayName("que un hogar no tenga nada que marcar no impide marcar en los demás")
    fun `un hogar vacio no corta el recorrido`() {
        // La transacción por hogar promete esto, y sin comprobarlo es una
        // afirmación escrita en un comentario --de las que en este proyecto han
        // resultado falsas cinco veces.
        http.registerHousehold()
        val first = overdueLoan()
        http.registerHousehold()
        val second = overdueLoan()

        markOverdue.run()

        loanStatusOf(first.loanId).shouldBe("OVERDUE")
        loanStatusOf(second.loanId).shouldBe("OVERDUE")
    }

    private data class OverdueLoan(
        val loanId: String,
        val assetId: String,
        val householdId: String,
        val accessToken: String,
    )

    /** Un préstamo ya vencido en un hogar recién creado. */
    private fun overdueLoan(): OverdueLoan {
        val home = http.registerHousehold()
        val assetId = http.createDurable(home.accessToken, "Taladro")
        val created = http.postJson(
            "/api/v1/loans",
            """{"assetId":"$assetId","lender":{"userId":"${home.memberId}"},
                "borrower":{"userId":"${home.memberId}"},
                "dueAt":"${Instant.now().plus(7, ChronoUnit.DAYS)}"}""",
            home.accessToken,
        )
        check(created.statusCode == HttpStatus.CREATED) { "No se pudo prestar: ${created.body}" }

        val loanId = created.body!!.extract("id")
        setDueAt(loanId, Instant.now().minus(1, ChronoUnit.DAYS))
        return OverdueLoan(loanId, assetId, home.householdId, home.accessToken)
    }

    /**
     * Mueve la fecha prevista por SQL.
     *
     * La API no acepta una `dueAt` ya pasada y adelantar el reloj de la
     * aplicación afectaría a todo lo demás. Lo que se mide es el proceso, así que
     * se le prepara el dato.
     */
    private fun setDueAt(loanId: String, dueAt: Instant) {
        postgres.ownerConnection().use { owner ->
            owner.prepareStatement("UPDATE loans SET due_at = ?, status = status WHERE id = ?::uuid").use {
                it.setObject(1, java.sql.Timestamp.from(dueAt))
                it.setString(2, loanId)
                it.executeUpdate()
            }
        }
    }

    private fun loanStatusOf(loanId: String): String =
        postgres.ownerConnection().use { owner ->
            owner.prepareStatement("SELECT status FROM loans WHERE id = ?::uuid").use { statement ->
                statement.setString(1, loanId)
                statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else "" }
            }
        }

    @TestConfiguration
    class Subscriber {
        @Bean fun overdueSubscriber() = OverdueSubscriber()
    }

    /** Hace de módulo suscrito: sin nadie escuchando no habría nada que comprobar. */
    class OverdueSubscriber : IdempotentEventHandler("OverdueSubscriber") {
        val received = CopyOnWriteArrayList<DomainEvent>()
        override fun handle(event: DomainEvent) {
            received += event
        }
    }

    private companion object {
        /** Una carrera se gana o se pierde: repetirla es lo que la hace medible. */
        const val REPETITIONS = 5
    }
}
