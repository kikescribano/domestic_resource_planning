package com.drp.module.warehouse

import com.drp.module.warehouse.adapter.event.WarehouseEventHandler
import com.drp.module.warehouse.application.StockSynchronizer
import com.drp.platform.event.DomainEvent
import com.drp.platform.module.ModuleActivation
import com.drp.platform.tenant.TenantContext
import com.drp.test.DrpPostgres
import com.drp.test.SpringIntegrationTest
import com.drp.test.count
import com.drp.test.extract
import com.drp.test.postJson
import com.drp.test.registerHousehold
import com.drp.test.seededCategory
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import org.springframework.transaction.PlatformTransactionManager
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * **La prueba que decide dónde vive la guarda de idempotencia** (ADR-013).
 *
 * `IdempotentEventHandler` guarda en memoria los `eventId` ya atendidos, y su
 * comentario decia desde la Fase 1 que «el dia que haya outbox, esa guarda se
 * muda con el». Ese dia es este hito, porque tras un reinicio el relay reentrega
 * **el mismo `eventId`** y la guarda en memoria esta vacia.
 *
 * La salida evidente --una tabla de `(handler, eventId)`-- se descarta, y esto es
 * lo que lo mide en lugar de razonarlo: **el handler de un modulo desplegado ya
 * es idempotente por construccion**, con un indice unico por `event_id` en su
 * propia tabla, escrito dentro de **su misma transaccion**. Una tabla compartida
 * de plataforma no podria dar esa garantia sin meterse dentro de la transaccion
 * del efecto --y eso seria prometer exactamente-una-vez por handler, que es
 * justo lo que la ADR-013 se niega a prometer.
 *
 * ### Cómo se simula el reinicio sin matar la JVM
 *
 * Con **una instancia nueva del handler**. La guarda es un campo suyo, asi que un
 * handler recien construido es exactamente lo que tiene un proceso que acaba de
 * arrancar: memoria vacia y el mismo `eventId` llamando a la puerta. Lo que se
 * mide despues es lo unico que importa --que el efecto no se duplica-- y eso ya
 * no depende de ninguna guarda, sino del cuaderno de Warehouse.
 *
 * **No estrena contexto de Spring**: mismo `@SpringBootTest` que el recorrido del
 * modulo, sin propiedades propias. Cada combinacion nueva reserva un pool entero
 * que no se suelta mientras siga en cache, y eso ya tumbo tres pruebas de otros
 * hitos en la Fase 1.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("una reentrega tras el reinicio no duplica el asiento del cuaderno")
class WarehouseRedeliveryTest : SpringIntegrationTest() {

    @Autowired private lateinit var http: TestRestTemplate
    @Autowired private lateinit var stock: StockSynchronizer
    @Autowired private lateinit var activation: ModuleActivation
    @Autowired private lateinit var tenantContext: TenantContext
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    @Test
    @DisplayName("el mismo eventId entregado a dos procesos distintos asienta una sola vez")
    fun `la idempotencia duradera la pone el modulo, no una tabla de plataforma`() {
        val home = http.registerHousehold()
        home.activateWarehouse()
        val assetId = home.stockOfRice()

        val event = quantityChanged(UUID.fromString(home.householdId), assetId, from = 900, to = 650)

        // Dos procesos, o lo que es lo mismo aqui: dos instancias del handler con
        // su guarda en memoria recien estrenada. Es lo que ocurre cuando el relay
        // reentrega despues de un reinicio.
        aFreshHandler().receive(event)
        aFreshHandler().receive(event)

        withClue("el índice único por event_id de warehouse_movements es lo que cierra el caso de verdad") {
            movementsFor(event.eventId).shouldBe(1)
        }
    }

    @Test
    @DisplayName("y la guarda en memoria sigue haciendo su trabajo dentro de un mismo proceso")
    fun `dentro del proceso la guarda descarta el duplicado`() {
        val home = http.registerHousehold()
        home.activateWarehouse()
        val assetId = home.stockOfRice()

        val event = quantityChanged(UUID.fromString(home.householdId), assetId, from = 900, to = 700)

        // La misma instancia dos veces: aqui ni siquiera se llega a la base de
        // datos, que es la mitad de la guarda que **no** se muda porque solo
        // tiene sentido dentro de un proceso.
        val handler = aFreshHandler()
        handler.receive(event)
        handler.receive(event)

        movementsFor(event.eventId).shouldBe(1)
    }

    // -----------------------------------------------------------------------

    /**
     * Un handler construido a mano con los mismos colaboradores que el de
     * produccion. Lo unico que no comparte con el bean es **la guarda**, que es
     * exactamente lo que se quiere estrenar.
     */
    private fun aFreshHandler() =
        WarehouseEventHandler(stock, activation, tenantContext, transactionManager)

    private fun quantityChanged(householdId: UUID, assetId: String, from: Int, to: Int) = DomainEvent(
        eventId = UUID.randomUUID(),
        type = "AssetQuantityChanged",
        occurredAt = Instant.now(),
        householdId = householdId,
        aggregateId = assetId,
        version = DomainEvent.INITIAL_VERSION,
        payload = mapOf(
            "previousQuantity" to BigDecimal(from),
            "quantity" to BigDecimal(to),
            "reason" to "ADJUSTMENT",
            "mergedFromAssetId" to null,
        ),
    )

    private fun movementsFor(eventId: UUID): Int =
        DrpPostgres.instance.ownerConnection().use {
            it.count("SELECT count(*) FROM warehouse_movements WHERE event_id = ?", eventId)
        }

    private fun com.drp.test.TestHousehold.activateWarehouse() {
        http.postJson("/api/v1/modules/WAREHOUSE/activation", "", accessToken)
            .statusCode.shouldBe(HttpStatus.OK)
    }

    /** Una existencia de verdad del core, que es lo que el cuaderno necesita mirar. */
    private fun com.drp.test.TestHousehold.stockOfRice(): String {
        val pantry = http.postJson("/api/v1/locations", """{"name":"Despensa","type":"ROOM"}""", accessToken)
            .body!!.extract("id")
        val article = http.postJson(
            "/api/v1/articles",
            """{"name":"Arroz","categoryId":"${http.seededCategory(accessToken, "Alimentación")}","unit":"GRAM"}""",
            accessToken,
        ).body!!.extract("id")

        return http.postJson(
            "/api/v1/assets/intake",
            """{"articleId":"$article","ownerId":"$memberId","quantity":900,
                "location":{"type":"LOCATION","id":"$pantry"}}""",
            accessToken,
        ).body!!.extract("id")
    }
}
