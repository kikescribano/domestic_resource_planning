package com.drp.application.usecase

import com.drp.application.port.TenantResolver
import com.drp.test.DrpMailpit
import com.drp.test.DrpPostgres
import com.drp.test.SpringIntegrationTest
import com.drp.test.count
import com.drp.test.execute
import com.drp.test.queryOne
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.client.exchange
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import java.util.UUID

/**
 * El proceso diario que borra los hogares que nunca se verificaron.
 *
 * Comprueba las dos cosas que lo hacen delicado. La primera es de negocio: borra
 * lo que sobra y **no toca** lo que no. La segunda es de aislamiento, y es la que
 * la ADR-003 exige demostrar: el proceso recorre los hogares **sin `BYPASSRLS`**,
 * porque el usuario con el que corre no lo tiene --y si lo tuviera, la segunda
 * capa estaria desactivada para toda la aplicacion y no solo para el proceso.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PurgeUnverifiedHouseholdsTest : SpringIntegrationTest() {

    @Autowired private lateinit var purge: PurgeUnverifiedHouseholds
    @Autowired private lateinit var tenantResolver: TenantResolver
    @Autowired private lateinit var http: TestRestTemplate

    private val mailpit = DrpMailpit.instance
    private val postgres = DrpPostgres.instance

    @Test
    @DisplayName("borra un hogar sin verificar de mas de siete dias, con todo lo que se sembro")
    fun `purga el hogar caducado`() {
        val email = "sin-verificar-${UUID.randomUUID()}@example.test"
        createHousehold(email)
        val householdId = householdIdOf(email)

        // El hogar existe, con sus cinco categorias sembradas y su pertenencia.
        categoriesOf(householdId).shouldBe(5)

        ageHousehold(householdId, days = 8)
        purge.run()

        postgres.ownerConnection().use { owner ->
            owner.count("SELECT count(*) FROM households WHERE id = ?", householdId).shouldBe(0)
            owner.count("SELECT count(*) FROM categories WHERE household_id = ?", householdId).shouldBe(0)
            owner.count("SELECT count(*) FROM household_members WHERE household_id = ?", householdId).shouldBe(0)
            // La identidad que lo abrio se va con el, porque nunca llego a
            // verificarse: es lo unico del sistema que retrata a esa persona y no
            // hay nada que conservar.
            owner.count("SELECT count(*) FROM identities WHERE lower(email) = ?", email).shouldBe(0)
        }
    }

    @Test
    @DisplayName("no toca un hogar verificado por viejo que sea")
    fun `no purga un hogar verificado`() {
        val email = "verificado-${UUID.randomUUID()}@example.test"
        createHousehold(email)
        verify(email)
        val householdId = householdIdOf(email)

        ageHousehold(householdId, days = 400)
        purge.run()

        postgres.ownerConnection().use { owner ->
            owner.count("SELECT count(*) FROM households WHERE id = ?", householdId).shouldBe(1)
            owner.count("SELECT count(*) FROM identities WHERE lower(email) = ?", email).shouldBe(1)
        }
    }

    @Test
    @DisplayName("no toca un hogar sin verificar que aun no ha cumplido los siete dias")
    fun `no purga un hogar reciente`() {
        val email = "reciente-${UUID.randomUUID()}@example.test"
        createHousehold(email)
        val householdId = householdIdOf(email)

        ageHousehold(householdId, days = 6)
        purge.run()

        postgres.ownerConnection().use {
            it.count("SELECT count(*) FROM households WHERE id = ?", householdId).shouldBe(1)
        }
    }

    @Test
    @DisplayName("es idempotente: pasarlo dos veces no cambia nada la segunda")
    fun `el proceso es idempotente`() {
        val email = "idempotente-${UUID.randomUUID()}@example.test"
        createHousehold(email)
        ageHousehold(householdIdOf(email), days = 9)

        val first = purge.run()
        val second = purge.run()

        (first >= 1).shouldBe(true)
        // Solo mira lo que ya sobra, asi que la segunda pasada no encuentra nada
        // nuevo que borrar salvo lo que otras pruebas hayan dejado.
        (second < first).shouldBe(true)
    }

    @Test
    @DisplayName("el usuario con el que corre el proceso NO tiene BYPASSRLS")
    fun `el proceso no se salta las politicas`() {
        // Es la condicion sin la cual todo lo anterior no demuestra nada: si el
        // rol tuviera BYPASSRLS, el proceso funcionaria igual y el aislamiento
        // estaria desactivado para la aplicacion entera.
        postgres.appConnection().use { app ->
            val privileged = app.count(
                "SELECT count(*) FROM pg_roles WHERE rolname = current_user AND (rolsuper OR rolbypassrls)",
            )
            (privileged > 0).shouldBeFalse()
        }
    }

    @Test
    @DisplayName("recorre los hogares por la resolucion acotada, que solo devuelve identificadores")
    fun `el recorrido usa la funcion acotada`() {
        val email = "recorrido-${UUID.randomUUID()}@example.test"
        createHousehold(email)
        val householdId = householdIdOf(email)

        // Sin contexto de inquilino --que es como corre el proceso-- la lista
        // llega igualmente. Es lo que hace innecesario el BYPASSRLS.
        val ids = tenantResolver.allHouseholdIds()

        ids.shouldContain(householdId)

        // Y lo que la funcion devuelve son identificadores, no datos: leer el
        // hogar sigue exigiendo contexto.
        postgres.appConnection().use { app ->
            app.count("SELECT count(*) FROM households WHERE id = ?", householdId).shouldBe(0)
        }
    }

    @Test
    @DisplayName("purgar un hogar no arrastra al de al lado")
    fun `la purga no cruza de hogar`() {
        val doomed = "condenado-${UUID.randomUUID()}@example.test"
        val safe = "a-salvo-${UUID.randomUUID()}@example.test"
        createHousehold(doomed)
        createHousehold(safe)
        verify(safe)

        val doomedId = householdIdOf(doomed)
        val safeId = householdIdOf(safe)
        ageHousehold(doomedId, days = 10)
        ageHousehold(safeId, days = 10)

        purge.run()

        postgres.ownerConnection().use { owner ->
            owner.count("SELECT count(*) FROM households WHERE id = ?", doomedId).shouldBe(0)
            owner.count("SELECT count(*) FROM households WHERE id = ?", safeId).shouldBe(1)
        }
        tenantResolver.allHouseholdIds().let {
            it.shouldNotContain(doomedId)
            it.shouldContain(safeId)
        }
    }

    // -----------------------------------------------------------------------

    private fun createHousehold(email: String) {
        http.exchange<String>(
            "/api/v1/households",
            HttpMethod.POST,
            HttpEntity(
                """
                {"name":"Hogar de purga","timeZone":"Europe/Madrid",
                 "admin":{"name":"Alguien","email":"$email","password":"el gato duerme en el sofa"}}
                """.trimIndent(),
                HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON },
            ),
        )
        mailpit.awaitMessageTo(email)
    }

    private fun verify(email: String) {
        val token = mailpit.awaitMessageTo(email).token()
        http.exchange<String>(
            "/api/v1/auth/verify-email",
            HttpMethod.POST,
            HttpEntity(
                """{"token":"$token"}""",
                HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON },
            ),
        )
    }

    private fun householdIdOf(email: String): UUID = postgres.ownerConnection().use { owner ->
        owner.queryOne(
            """
            SELECT m.household_id
            FROM household_members m
            JOIN identities i ON i.id = m.identity_id
            WHERE lower(i.email) = ?
            """.trimIndent(),
            email.lowercase(),
        ) { it.getObject(1, UUID::class.java) } ?: error("No hay hogar para $email")
    }

    /**
     * Envejece el hogar tocando su `created_at`.
     *
     * Se hace por la conexion del propietario --superusuario, se salta las
     * politicas-- porque es preparacion de la prueba y no parte de lo que se
     * mide. Lo que se mide corre despues, con el usuario de la aplicacion.
     */
    private fun ageHousehold(householdId: UUID, days: Long) {
        postgres.ownerConnection().use {
            it.execute(
                "UPDATE households SET created_at = now() - make_interval(days => ?) WHERE id = ?",
                days.toInt(),
                householdId,
            )
        }
    }

    private fun categoriesOf(householdId: UUID): Int = postgres.ownerConnection().use {
        it.count("SELECT count(*) FROM categories WHERE household_id = ?", householdId)
    }
}
