package com.drp.module.warehouse

import com.drp.module.warehouse.domain.ExpiryStage
import com.drp.module.warehouse.domain.MovementKind
import com.drp.module.warehouse.domain.StockItem
import com.drp.module.warehouse.domain.StockLot
import com.drp.module.warehouse.domain.StockMovement
import com.drp.module.warehouse.domain.WarehouseArticle
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Las reglas del modulo **sin base de datos**.
 *
 * Lo que se comprueba aqui es lo que decide el dominio y no la consulta: en que
 * fase esta un lote, si esa fase merece un aviso nuevo, y si un articulo esta bajo
 * minimos. La consulta solo acota candidatos; si la regla viviera en el SQL, esta
 * clase no podria existir y las dos preguntas que mas facil se equivocan --el
 * limite del «hoy» y la repeticion del aviso-- solo se verian levantando Postgres.
 */
class WarehouseDomainTest {

    private val today = LocalDate.of(2026, 8, 18)

    // -----------------------------------------------------------------------
    // Caducidad: las dos fases y su limite
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("caducar hoy ya es EXPIRED, no NEAR: el limite se cuenta hacia el lado seguro")
    fun `el dia de la caducidad ya cuenta como pasada`() {
        lot(expiresOn = today).stageOn(today, leadDays = 7).shouldBe(ExpiryStage.EXPIRED)
        lot(expiresOn = today.minusDays(1)).stageOn(today, leadDays = 7).shouldBe(ExpiryStage.EXPIRED)
    }

    @Test
    @DisplayName("dentro de la ventana es NEAR, y el ultimo dia de la ventana tambien")
    fun `la ventana de antelacion incluye su ultimo dia`() {
        lot(expiresOn = today.plusDays(7)).stageOn(today, leadDays = 7).shouldBe(ExpiryStage.NEAR)
        lot(expiresOn = today.plusDays(1)).stageOn(today, leadDays = 7).shouldBe(ExpiryStage.NEAR)
    }

    @Test
    @DisplayName("fuera de la ventana no hay nada que decir todavia")
    fun `mas alla de la antelacion no se avisa`() {
        lot(expiresOn = today.plusDays(8)).stageOn(today, leadDays = 7).shouldBeNull()
    }

    @Test
    @DisplayName("con antelacion cero solo avisa el mismo dia, que es lo que cero significa")
    fun `antelacion cero no es lo mismo que sin antelacion`() {
        lot(expiresOn = today.plusDays(1)).stageOn(today, leadDays = 0).shouldBeNull()
        lot(expiresOn = today).stageOn(today, leadDays = 0).shouldBe(ExpiryStage.EXPIRED)
    }

    // -----------------------------------------------------------------------
    // Repetir o no repetir: la decision del hito
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("un lote que ya dijo NEAR no lo repite la noche siguiente")
    fun `la misma fase no se anuncia dos veces`() {
        val announced = lot(expiresOn = today.plusDays(3), stage = ExpiryStage.NEAR)

        announced.shouldAnnounce(ExpiryStage.NEAR).shouldBe(false)
    }

    @Test
    @DisplayName("pero al caducar de verdad SI avisa otra vez: es una noticia nueva, no una repeticion")
    fun `la fase que avanza se anuncia`() {
        val announced = lot(expiresOn = today, stage = ExpiryStage.NEAR)

        announced.shouldAnnounce(ExpiryStage.EXPIRED).shouldBe(true)
    }

    @Test
    @DisplayName("y una vez caducado ya no vuelve a decir nada: dos avisos como mucho en toda su vida")
    fun `EXPIRED es la ultima palabra`() {
        val expired = lot(expiresOn = today, stage = ExpiryStage.EXPIRED)

        expired.shouldAnnounce(ExpiryStage.EXPIRED).shouldBe(false)
        // Ni siquiera hacia atras: un lote caducado no vuelve a «caduca pronto».
        expired.shouldAnnounce(ExpiryStage.NEAR).shouldBe(false)
    }

    @Test
    @DisplayName("un lote ya consumido no avisa aunque su fecha diga que caduco")
    fun `lo consumido deja de vigilarse`() {
        val consumed = lot(expiresOn = today.minusDays(30)).copy(consumedAt = Instant.now())

        consumed.shouldAnnounce(ExpiryStage.EXPIRED).shouldBe(false)
    }

    // -----------------------------------------------------------------------
    // El minimo
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("estar EN el minimo ya es estar bajo minimos: tener lo ultimo es momento de reponer")
    fun `el minimo se compara con menor o igual`() {
        val file = articleFile(minimum = BigDecimal("2"))

        file.isBelowMinimum(BigDecimal("3")).shouldBe(false)
        file.isBelowMinimum(BigDecimal("2")).shouldBe(true)
        file.isBelowMinimum(BigDecimal("1")).shouldBe(true)
    }

    @Test
    @DisplayName("un articulo sin minimo declarado NUNCA esta bajo minimos, ni siquiera a cero")
    fun `sin minimo no hay nada por debajo`() {
        val file = articleFile(minimum = null)

        file.watchesMinimum.shouldBe(false)
        file.isBelowMinimum(BigDecimal.ZERO).shouldBe(false)
    }

    // -----------------------------------------------------------------------
    // El cuaderno
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("el delta se deriva y no se guarda: no puede discrepar de lo que asienta")
    fun `el delta sale de las dos cantidades`() {
        movement(previous = BigDecimal("900"), quantity = BigDecimal("700")).delta
            .shouldBe(BigDecimal("-200"))
    }

    @Test
    @DisplayName("una RELOCATION no tiene delta: mover de sitio no cambia ninguna cantidad")
    fun `mover no mueve cantidad`() {
        val moved = movement(previous = null, quantity = null, kind = MovementKind.RELOCATION)

        moved.delta.shouldBeNull()
        moved.kind.movesQuantity().shouldBe(false)
        MovementKind.INTAKE.movesQuantity().shouldBe(true)
    }

    // -----------------------------------------------------------------------
    // La frontera, vista desde el modelo de lectura
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("la existencia compara la cantidad DEL CORE con el minimo, que es lo unico de aqui")
    fun `el modelo de lectura junta las dos mitades`() {
        val item = StockItem(
            assetId = UUID.randomUUID(),
            articleId = UUID.randomUUID(),
            articleName = "Arroz",
            unit = "GRAM",
            locationId = null,
            locationName = null,
            // Del core.
            quantity = BigDecimal("400"),
            // De Warehouse.
            minimumQuantity = BigDecimal("500"),
            nearestExpiry = null,
            lotCount = 0,
        )

        item.isBelowMinimum.shouldBe(true)
        item.copy(minimumQuantity = null).isBelowMinimum.shouldBe(false)
    }

    // -----------------------------------------------------------------------

    private fun lot(expiresOn: LocalDate, stage: ExpiryStage? = null) = StockLot(
        id = UUID.randomUUID(),
        assetId = UUID.randomUUID(),
        articleId = UUID.randomUUID(),
        lotCode = null,
        expiresOn = expiresOn,
        quantity = BigDecimal.ONE,
        consumedAt = null,
        notifiedStage = stage,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        createdBy = null,
        updatedBy = null,
    )

    private fun articleFile(minimum: BigDecimal?) = WarehouseArticle(
        id = UUID.randomUUID(),
        articleId = UUID.randomUUID(),
        minimumQuantity = minimum,
        expiryLeadDays = null,
        lowStockSince = null,
        lowStockNotifiedAt = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        createdBy = null,
        updatedBy = null,
    )

    private fun movement(
        previous: BigDecimal?,
        quantity: BigDecimal?,
        kind: MovementKind = MovementKind.ADJUSTMENT,
    ) = StockMovement(
        id = UUID.randomUUID(),
        assetId = UUID.randomUUID(),
        articleId = null,
        locationId = null,
        locationName = null,
        kind = kind,
        previousQuantity = previous,
        quantity = quantity,
        occurredAt = Instant.EPOCH,
        eventId = null,
        createdBy = null,
    )
}
