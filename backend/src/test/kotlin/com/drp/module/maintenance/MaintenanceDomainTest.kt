package com.drp.module.maintenance

import com.drp.module.maintenance.domain.DueStage
import com.drp.module.maintenance.domain.InterventionKind
import com.drp.module.maintenance.domain.MaintenanceIntervention
import com.drp.module.maintenance.domain.MaintenancePlan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Las reglas del modulo **sin base de datos**.
 *
 * Lo que se comprueba aqui es lo que decide el dominio y no la consulta: en que
 * fase esta una fecha prevista, si esa fase merece un aviso nuevo, **si el aviso
 * se rearma al avanzar la fecha** y si la antelacion cabe en el periodo. La
 * consulta solo acota candidatos; si la regla viviera en el SQL, esta clase no
 * podria existir y las dos preguntas que mas facil se equivocan --el limite del
 * «hoy» y el rearme-- solo se verian levantando Postgres.
 */
class MaintenanceDomainTest {

    private val today = LocalDate.of(2026, 8, 19)

    // -----------------------------------------------------------------------
    // Las dos fases y su limite
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("el dia que toca todavia es DUE_SOON: se pasa al dia siguiente")
    fun `el limite entre tocar y haberse pasado`() {
        plan(nextDueOn = today).stageOn(today).shouldBe(DueStage.DUE_SOON)
        plan(nextDueOn = today.minusDays(1)).stageOn(today).shouldBe(DueStage.OVERDUE)
    }

    @Test
    @DisplayName("dentro de la ventana de antelacion es DUE_SOON, y su ultimo dia tambien")
    fun `la ventana incluye su ultimo dia`() {
        plan(nextDueOn = today.plusDays(15)).stageOn(today).shouldBe(DueStage.DUE_SOON)
        plan(nextDueOn = today.plusDays(1)).stageOn(today).shouldBe(DueStage.DUE_SOON)
    }

    @Test
    @DisplayName("fuera de la ventana no hay nada que decir todavia")
    fun `mas alla de la antelacion no se avisa`() {
        plan(nextDueOn = today.plusDays(16)).stageOn(today).shouldBeNull()
    }

    @Test
    @DisplayName("con antelacion cero solo avisa el mismo dia, que es lo que cero significa")
    fun `antelacion cero no es lo mismo que sin antelacion`() {
        plan(nextDueOn = today.plusDays(1), leadDays = 0).stageOn(today).shouldBeNull()
        plan(nextDueOn = today, leadDays = 0).stageOn(today).shouldBe(DueStage.DUE_SOON)
    }

    // -----------------------------------------------------------------------
    // El aviso: no se repite, la fase que avanza si, y se rearma
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("sin haber avisado nunca, cualquier fase es noticia")
    fun `un plan que no ha dicho nada avisa`() {
        plan().shouldAnnounce(DueStage.DUE_SOON).shouldBe(true)
        plan().shouldAnnounce(DueStage.OVERDUE).shouldBe(true)
    }

    @Test
    @DisplayName("la misma fase sobre la misma fecha NO se repite: es la regla de Warehouse")
    fun `un aviso por condicion y no uno por noche`() {
        val announced = plan().copy(notifiedStage = DueStage.DUE_SOON, notifiedFor = DUE)

        announced.shouldAnnounce(DueStage.DUE_SOON).shouldBe(false)
    }

    @Test
    @DisplayName("pero la fase que AVANZA si es noticia nueva: pasarse no es lo mismo que estar cerca")
    fun `de DUE_SOON a OVERDUE se vuelve a avisar`() {
        val announced = plan().copy(notifiedStage = DueStage.DUE_SOON, notifiedFor = DUE)

        announced.shouldAnnounce(DueStage.OVERDUE).shouldBe(true)
    }

    @Test
    @DisplayName("y hacia atras no: de OVERDUE no se vuelve a DUE_SOON")
    fun `la fase no retrocede`() {
        val announced = plan().copy(notifiedStage = DueStage.OVERDUE, notifiedFor = DUE)

        announced.shouldAnnounce(DueStage.DUE_SOON).shouldBe(false)
    }

    /**
     * **La vuelta que Warehouse no tuvo que dar.** Alli el rearme era reponer por
     * encima del minimo; aqui es que la fecha avance, y esta prueba es la que
     * afirma que **no hace falta limpiar nada a mano**: basta con que `notifiedFor`
     * deje de coincidir.
     */
    @Test
    @DisplayName("registrar la intervencion rearma el aviso SIN limpiar ninguna marca")
    fun `el aviso se rearma solo al avanzar la fecha`() {
        val announced = plan().copy(notifiedStage = DueStage.OVERDUE, notifiedFor = DUE)

        val advanced = announced.performedOn(today)

        // La marca sigue puesta --nadie la ha borrado-- y aun asi vuelve a avisar,
        // porque ya no habla de la fecha que ahora toca.
        advanced.notifiedStage.shouldBe(DueStage.OVERDUE)
        advanced.shouldAnnounce(DueStage.DUE_SOON).shouldBe(true)
    }

    @Test
    @DisplayName("cambiar la fecha a mano tambien lo rearma: es el camino que una marca suelta olvidaria")
    fun `cualquier movimiento de la fecha rearma`() {
        val announced = plan().copy(notifiedStage = DueStage.OVERDUE, notifiedFor = DUE)

        announced.copy(nextDueOn = DUE.plusMonths(1)).shouldAnnounce(DueStage.DUE_SOON).shouldBe(true)
    }

    @Test
    @DisplayName("un plan cancelado no avisa de nada")
    fun `lo que ya no se vigila no dice nada`() {
        plan().copy(cancelledAt = Instant.parse("2026-08-19T10:00:00Z"))
            .shouldAnnounce(DueStage.OVERDUE).shouldBe(false)
    }

    // -----------------------------------------------------------------------
    // El avance de la fecha
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("la proxima se cuenta desde LO QUE SE HIZO y no desde la que tocaba")
    fun `un retraso no se arrastra`() {
        // Tocaba en enero y se hizo en marzo: la proxima es marzo del ano que viene
        // y no enero. Contarla desde la teorica arrastraria el retraso para siempre
        // y acabaria pidiendo dos revisiones el mismo ano.
        val late = plan(nextDueOn = LocalDate.of(2026, 1, 15), intervalMonths = 12)

        val advanced = late.performedOn(LocalDate.of(2026, 3, 20))

        advanced.nextDueOn.shouldBe(LocalDate.of(2027, 3, 20))
        advanced.lastPerformedOn.shouldBe(LocalDate.of(2026, 3, 20))
    }

    @Test
    @DisplayName("en meses y no en dias: el aniversario no se desplaza con los bisiestos")
    fun `plusMonths conserva el dia del mes`() {
        // Con 365 dias, una revision anual hecha el 1 de marzo de un ano bisiesto
        // caeria el 28 de febrero. Con meses, cae el 1 de marzo, ano tras ano.
        val yearly = plan(intervalMonths = 12)

        yearly.performedOn(LocalDate.of(2028, 3, 1)).nextDueOn.shouldBe(LocalDate.of(2029, 3, 1))
    }

    @Test
    @DisplayName("y el 31 no se desborda: plusMonths lo recorta al ultimo dia del mes destino")
    fun `un dia que no existe en el mes destino se recorta`() {
        plan(intervalMonths = 1).performedOn(LocalDate.of(2026, 1, 31))
            .nextDueOn.shouldBe(LocalDate.of(2026, 2, 28))
    }

    // -----------------------------------------------------------------------
    // La antelacion cabe en el periodo
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("una antelacion mas larga que el periodo deja el plan siempre a punto de tocar")
    fun `la antelacion tiene que caber`() {
        // Trimestral con noventa dias de antelacion: son mas dias de los que dura
        // el ciclo, asi que el plan nunca sale de la ventana.
        plan(intervalMonths = 3, leadDays = 100, nextDueOn = LocalDate.of(2026, 9, 1))
            .leadFitsInterval().shouldBe(false)

        plan(intervalMonths = 3, leadDays = 30, nextDueOn = LocalDate.of(2026, 9, 1))
            .leadFitsInterval().shouldBe(true)
    }

    @Test
    @DisplayName("se mide sobre el calendario real y no sobre meses de treinta dias")
    fun `febrero no vale lo mismo que julio`() {
        // Mensual con 29 dias de antelacion: cabe en un marzo de 31 dias...
        plan(intervalMonths = 1, leadDays = 29, nextDueOn = LocalDate.of(2026, 4, 1))
            .leadFitsInterval().shouldBe(true)
        // ...y no cabe en un febrero de 28.
        plan(intervalMonths = 1, leadDays = 29, nextDueOn = LocalDate.of(2026, 3, 1))
            .leadFitsInterval().shouldBe(false)
    }

    // -----------------------------------------------------------------------
    // La intervencion
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("una intervencion de hoy vale; una de manana, no")
    fun `una intervencion no es del futuro`() {
        intervention(performedOn = today).isInThePast(today).shouldBe(true)
        intervention(performedOn = today.minusDays(1)).isInThePast(today).shouldBe(true)
        intervention(performedOn = today.plusDays(1)).isInThePast(today).shouldBe(false)
    }

    // -----------------------------------------------------------------------
    // Apoyo
    // -----------------------------------------------------------------------

    private fun plan(
        nextDueOn: LocalDate = DUE,
        intervalMonths: Int = 12,
        leadDays: Int = 15,
    ) = MaintenancePlan(
        id = UUID.randomUUID(),
        assetId = UUID.randomUUID(),
        name = "Revisión anual",
        intervalMonths = intervalMonths,
        leadDays = leadDays,
        nextDueOn = nextDueOn,
        lastPerformedOn = null,
        supplierId = null,
        notes = null,
        notifiedStage = null,
        notifiedFor = null,
        cancelledAt = null,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
        createdBy = null,
        updatedBy = null,
    )

    private fun intervention(performedOn: LocalDate) = MaintenanceIntervention(
        id = UUID.randomUUID(),
        assetId = UUID.randomUUID(),
        planId = null,
        kind = InterventionKind.CORRECTIVE,
        performedOn = performedOn,
        summary = "Se cambió la junta",
        supplierId = null,
        supplierName = null,
        notes = null,
        createdAt = Instant.parse("2026-08-19T10:00:00Z"),
        createdBy = null,
    )

    private companion object {
        val DUE: LocalDate = LocalDate.of(2026, 8, 25)
    }
}
