package com.drp.core.adapter.persistence

import com.drp.core.application.port.HouseholdMemberRepository
import com.drp.core.application.port.InvitationRepository
import com.drp.platform.page.Pagination
import com.drp.platform.tenant.TenantContext
import com.drp.test.DrpPostgres
import com.drp.test.SeededHousehold
import com.drp.test.SpringIntegrationTest
import com.drp.test.execute
import com.drp.test.seedHousehold
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID

/**
 * El orden de los listados que no llevan `ORDER BY` escrito en la consulta.
 *
 * Sin un orden estable, PostgreSQL devuelve las filas en el orden que le
 * convenga a cada peticion, y paginar sobre eso puede repetir o saltarse
 * elementos entre paginas. Estos dos listados --miembros e invitaciones vivas--
 * ordenan desde el `Pageable`, asi que su promesa no se ve leyendo el SQL y se
 * fija aqui.
 *
 * La siembra inserta a proposito en un orden distinto del cronologico: si el
 * listado saliera bien por casualidad --orden de insercion--, aqui saldria mal.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ListOrderingIntegrationTest : SpringIntegrationTest() {

    @Autowired private lateinit var tenantContext: TenantContext
    @Autowired private lateinit var transactions: TransactionTemplate
    @Autowired private lateinit var members: HouseholdMemberRepository
    @Autowired private lateinit var invitations: InvitationRepository

    private lateinit var household: SeededHousehold
    private val elderMemberId = UUID.randomUUID()
    private val middleMemberId = UUID.randomUUID()
    private val invitationIds = List(3) { UUID.randomUUID() }

    @BeforeAll
    fun seed() {
        DrpPostgres.instance.ownerConnection().use { db ->
            household = db.seedHousehold("Orden de listados")

            // Dos pertenencias mas, insertadas despues del administrador pero
            // con mas antiguedad que el.
            listOf(
                elderMemberId to "2 days",
                middleMemberId to "1 day",
            ).forEach { (memberId, age) ->
                val identityId = UUID.randomUUID()
                db.execute(
                    "INSERT INTO identities (id, name, email, password_hash) VALUES (?, ?, ?, '{argon2}fake')",
                    identityId,
                    "Persona $memberId",
                    "$identityId@example.test",
                )
                db.execute(
                    """
                    INSERT INTO household_members (id, household_id, identity_id, role, created_at)
                    VALUES (?, ?, ?, 'HOUSEHOLD_MEMBER', now() - interval '$age')
                    """.trimIndent(),
                    memberId,
                    household.householdId,
                    identityId,
                )
            }

            // Tres invitaciones vivas, insertadas de la mas reciente a la mas
            // antigua.
            listOf(
                invitationIds[2] to "1 day",
                invitationIds[1] to "2 days",
                invitationIds[0] to "3 days",
            ).forEach { (invitationId, age) ->
                db.execute(
                    """
                    INSERT INTO invitations (id, household_id, email, role, token_hash, expires_at, created_at)
                    VALUES (?, ?, ?, 'HOUSEHOLD_MEMBER', ?, now() + interval '7 days', now() - interval '$age')
                    """.trimIndent(),
                    invitationId,
                    household.householdId,
                    "$invitationId@example.test",
                    "hash-$invitationId",
                )
            }
        }
    }

    @Test
    @DisplayName("los miembros salen por antiguedad en el hogar, pagina a pagina y sin repetir")
    fun `los miembros paginan en orden estable`() {
        val ids = tenantContext.runAs(household.householdId) {
            transactions.execute {
                (0..1).flatMap { page ->
                    members.list(includeDeactivated = true, Pagination(page, 2)).items.map { it.id }
                }
            }
        }!!

        ids.shouldBe(listOf(elderMemberId, middleMemberId, household.memberId))
    }

    @Test
    @DisplayName("las invitaciones vivas salen por orden de emision, pagina a pagina y sin repetir")
    fun `las invitaciones paginan en orden estable`() {
        val ids = tenantContext.runAs(household.householdId) {
            transactions.execute {
                (0..1).flatMap { page ->
                    invitations.listLive(Instant.now(), Pagination(page, 2)).items.map { it.id }
                }
            }
        }!!

        ids.shouldBe(invitationIds)
    }
}
