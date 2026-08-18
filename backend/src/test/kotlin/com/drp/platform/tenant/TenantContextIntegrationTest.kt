package com.drp.platform.tenant

import com.drp.core.application.port.HouseholdRepository
import com.drp.platform.page.Pagination
import com.drp.core.application.port.HouseholdMemberRepository
import com.drp.core.application.port.TenantResolver
import com.drp.platform.tenant.TenantContext
import com.drp.test.DrpPostgres
import com.drp.test.SeededHousehold
import com.drp.test.SpringIntegrationTest
import com.drp.test.seedHousehold
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.support.TransactionTemplate

/**
 * El puente entre las dos capas de aislamiento, atravesando la aplicacion entera.
 *
 * Las pruebas de esquema comprueban que PostgreSQL filtra; estas comprueban que
 * la aplicacion **le cuenta a PostgreSQL en nombre de quien trabaja**, en cada
 * transaccion y sin que ningun repositorio tenga que acordarse.
 *
 * Se usa `TransactionTemplate` en lugar de `@Transactional` sobre la prueba a
 * proposito: lo que se mide son los limites de transaccion, y anotar la prueba
 * entera la envolveria en una sola --justo lo contrario de lo que hay que
 * observar.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TenantContextIntegrationTest : SpringIntegrationTest() {

    @Autowired private lateinit var tenantContext: TenantContext
    @Autowired private lateinit var transactions: TransactionTemplate
    @Autowired private lateinit var households: HouseholdRepository
    @Autowired private lateinit var members: HouseholdMemberRepository
    @Autowired private lateinit var tenantResolver: TenantResolver
    @Autowired private lateinit var directory: HouseholdDirectory

    private lateinit var householdA: SeededHousehold
    private lateinit var householdB: SeededHousehold

    @BeforeAll
    fun seed() {
        DrpPostgres.instance.ownerConnection().use {
            householdA = it.seedHousehold("Contexto A")
            householdB = it.seedHousehold("Contexto B")
        }
    }

    @Test
    @DisplayName("dentro del contexto de un hogar solo se ve ese hogar")
    fun `el contexto acota lo que se lee`() {
        val seen = tenantContext.runAs(householdA.householdId) {
            transactions.execute { households.findCurrent() }
        }

        seen!!.id.shouldBe(householdA.householdId)
        seen.name.shouldBe("Contexto A")
    }

    @Test
    @DisplayName("sin contexto no se ve nada, y la transaccion no falla")
    fun `sin contexto la lectura sale vacia`() {
        val seen = tenantContext.runAs(null) {
            transactions.execute { households.findCurrent() }
        }

        seen.shouldBeNull()
    }

    @Test
    @DisplayName("dos transacciones seguidas con hogares distintos no se contaminan")
    fun `el contexto no se filtra entre transacciones`() {
        // Es el fallo que importa y el que no se ve a simple vista: la conexion
        // vuelve al pool despues de cada transaccion, asi que si el ajuste no
        // fuera LOCAL, la segunda peticion heredaria el hogar de la primera.
        val first = tenantContext.runAs(householdA.householdId) {
            transactions.execute { households.findCurrent() }
        }
        val second = tenantContext.runAs(householdB.householdId) {
            transactions.execute { households.findCurrent() }
        }
        val third = tenantContext.runAs(null) {
            transactions.execute { households.findCurrent() }
        }

        first!!.id.shouldBe(householdA.householdId)
        second!!.id.shouldBe(householdB.householdId)
        third.shouldBeNull()
    }

    @Test
    @DisplayName("los repositorios no filtran por hogar y aun asi no devuelven filas ajenas")
    fun `el filtro lo pone la politica`() {
        // list() no lleva ni un WHERE household_id: lo pone PostgreSQL.
        val listed = tenantContext.runAs(householdA.householdId) {
            transactions.execute { members.list(includeDeactivated = true, Pagination(0, 50)) }
        }!!

        listed.total.shouldBe(1)
        listed.items.single().id.shouldBe(householdA.memberId)
    }

    @Test
    @DisplayName("la resolucion de inquilino ve los dos hogares, y solo sus identificadores")
    fun `el resolutor alcanza donde la politica no deja`() {
        // Sin contexto ninguno: es justo el escenario de los procesos diarios.
        val ids = tenantContext.runAs(null) { directory.allHouseholdIds() }

        ids.shouldContainAll(listOf(householdA.householdId, householdB.householdId))
    }

    @Test
    @DisplayName("el resolutor encuentra el hogar de una identidad antes de que haya sesion")
    fun `el resolutor resuelve el hogar del login`() {
        val resolved = tenantContext.runAs(null) {
            tenantResolver.householdOfActiveMember(householdB.identityId)
        }

        resolved.shouldBe(householdB.householdId)
    }
}
