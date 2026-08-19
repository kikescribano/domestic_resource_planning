package com.drp.module.warehouse.application

import com.drp.module.warehouse.domain.MovementKind
import com.drp.module.warehouse.domain.StockMovement
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * Lo que Warehouse hace para **estar al dia del core**, en un solo sitio.
 *
 * Existe por una decision de la ficha que conviene no perder: **la siembra y los
 * handlers comparten estas funciones**. Con eso, «el modulo aun no ha sembrado»
 * deja de ser un caso que ningun handler tenga que contemplar --cada rama abre
 * por su cuenta la ficha que necesite-- y, sobre todo, la siembra y los handlers
 * **no pueden divergir**: no hay dos versiones de «asienta lo que hay» que se
 * puedan quedar distintas.
 *
 * La alternativa era que el handler abandonara si no encontraba la ficha. Es peor
 * por dos motivos: convierte una carrera de milisegundos entre la activacion y el
 * primer evento en un dato que falta **para siempre**, y deja que los dos caminos
 * se separen sin que nada lo delate.
 *
 * **Nada de esto abre transaccion.** Corre siempre dentro de una que ya esta
 * abierta: la de la activacion cuando lo llama la siembra, y la `REQUIRES_NEW`
 * que `ModuleEventHandler` abre por el modulo cuando lo llama un handler. Abrir
 * otra dejaria la siembra fuera de la transaccion de la activacion, y una
 * activacion que fallara dejaria datos sembrados de un modulo que no llego a
 * encenderse.
 */
@Service
class StockSynchronizer(
    private val warehouse: WarehouseRepository,
    private val events: WarehouseEvents,
    private val clock: Clock,
) {

    /** Abre la ficha del articulo si no la tenia. Idempotente por el indice unico. */
    fun openArticleFile(articleId: UUID, by: UUID? = null) =
        warehouse.openArticleFile(articleId, clock.instant(), by)

    /** Abre la ficha del sitio si no la tenia. Idempotente por el indice unico. */
    fun openLocationFile(locationId: UUID, by: UUID? = null) =
        warehouse.openLocationFile(locationId, clock.instant(), by)

    /**
     * Asienta un movimiento de cantidad, abriendo antes las fichas que hagan
     * falta.
     *
     * `previous` y `quantity` vienen del evento del core, que es quien sabe cual
     * era cual: deducir el anterior leyendo la fila daria el valor **de despues**,
     * porque el handler corre cuando la transaccion del core ya cerro.
     */
    fun recordQuantityChange(
        assetId: UUID,
        kind: MovementKind,
        previous: BigDecimal,
        quantity: BigDecimal,
        occurredAt: Instant,
        eventId: UUID?,
    ) {
        val facts = warehouse.coreStockFacts(assetId) ?: return
        val articleId = facts.articleId ?: return

        openFilesFor(articleId, facts.locationId)
        warehouse.record(
            StockMovement(
                id = UUID.randomUUID(),
                assetId = assetId,
                articleId = articleId,
                locationId = facts.locationId,
                locationName = facts.locationName,
                kind = kind,
                previousQuantity = previous,
                quantity = quantity,
                occurredAt = occurredAt,
                eventId = eventId,
                // Nulo: **lo escribio el sistema**, no una persona. Es la misma
                // convencion del core y aqui es literalmente cierto, porque quien
                // asienta es un handler de evento.
                createdBy = null,
            ),
        )
        refreshMinimum(articleId)
    }

    /**
     * Asienta el cambio de sitio, que no cambia ninguna cantidad y **va en el
     * mismo libro**: mover algo también es un movimiento de existencias, y
     * separarlo obligaria a leer dos tablas para saber que le paso a algo.
     */
    fun recordRelocation(assetId: UUID, occurredAt: Instant, eventId: UUID?) {
        val facts = warehouse.coreStockFacts(assetId) ?: return
        val articleId = facts.articleId ?: return
        if (!facts.isLiveConsumable) return

        openFilesFor(articleId, facts.locationId)
        warehouse.record(
            StockMovement(
                id = UUID.randomUUID(),
                assetId = assetId,
                articleId = articleId,
                locationId = facts.locationId,
                locationName = facts.locationName,
                kind = MovementKind.RELOCATION,
                previousQuantity = null,
                quantity = null,
                occurredAt = occurredAt,
                eventId = eventId,
                createdBy = null,
            ),
        )
    }

    /**
     * Lo que hay **ahora mismo** de una existencia, asentado como apertura.
     *
     * Lo usan la siembra --para empezar el cuaderno en el estado real y no en
     * cero-- y el handler de `AssetCreated`, que trae una existencia nueva cuya
     * cantidad el evento no lleva dentro y hay que leer del core.
     *
     * El indice unico parcial de la tabla admite **un solo `OPENING` por
     * existencia**, y es lo que hace idempotente a la siembra: reactivar el modulo
     * la vuelve a ejecutar, y sin ese indice un hogar que encendiera y apagara
     * tres veces tendria tres asientos de apertura.
     */
    fun openStockItem(assetId: UUID, kind: MovementKind, eventId: UUID? = null) {
        val facts = warehouse.coreStockFacts(assetId) ?: return
        val articleId = facts.articleId ?: return
        if (!facts.isLiveConsumable) return

        openFilesFor(articleId, facts.locationId)
        warehouse.record(
            StockMovement(
                id = UUID.randomUUID(),
                assetId = assetId,
                articleId = articleId,
                locationId = facts.locationId,
                locationName = facts.locationName,
                kind = kind,
                previousQuantity = BigDecimal.ZERO,
                quantity = facts.quantity ?: BigDecimal.ZERO,
                occurredAt = clock.instant(),
                eventId = eventId,
                createdBy = null,
            ),
        )
        refreshMinimum(articleId)
    }

    /** Una existencia que causa baja deja de tener lotes que vigilar. */
    fun closeStockItem(assetId: UUID) {
        val facts = warehouse.coreStockFacts(assetId)
        warehouse.consumeLotsOf(assetId, clock.instant())
        facts?.articleId?.let { refreshMinimum(it) }
    }

    /**
     * Recalcula si un articulo entra o sale de bajo minimos, y **publica solo en
     * el cruce**.
     *
     * El estado con fecha --`lowStockSince`-- es lo que hace posibles las dos
     * cosas a la vez: publicar una vez para Compras y avisar una vez en el
     * resumen diario. Un booleano calculado al vuelo no distingue «acaba de caer»
     * de «lleva tres semanas caido».
     *
     * Salir de bajo minimos **borra tambien la marca de avisado**, que es lo que
     * vuelve a armar el aviso para la proxima caida. Sin eso, un articulo avisaria
     * una sola vez en toda su vida.
     */
    fun refreshMinimum(articleId: UUID) {
        val file = warehouse.findArticleFile(articleId) ?: return
        val minimum = file.minimumQuantity ?: return

        val total = warehouse.totalOf(articleId)
        val below = total <= minimum
        val now = clock.instant()

        when {
            below && !file.isLowOnStock -> {
                warehouse.saveArticleFile(file.copy(lowStockSince = now, updatedAt = now))
                events.stockBelowMinimum(articleId, total, minimum, unitOf(articleId))
                if (total.signum() == 0) events.stockDepleted(articleId, unitOf(articleId))
            }

            !below && file.isLowOnStock ->
                warehouse.saveArticleFile(
                    file.copy(lowStockSince = null, lowStockNotifiedAt = null, updatedAt = now),
                )

            // Ya estaba caido y sigue caido: ni se toca la fecha de entrada --que
            // es la que el aviso mira-- ni se vuelve a publicar. Es el caso normal
            // y el que mas veces pasa.
            else -> Unit
        }
    }

    private fun openFilesFor(articleId: UUID, locationId: UUID?) {
        openArticleFile(articleId)
        locationId?.let { openLocationFile(it) }
    }

    /**
     * La unidad la pone el articulo, y el articulo es del core: **se lee, no se
     * copia**. Un evento que llevara una unidad guardada aqui diria una cosa
     * distinta de la del core el dia que alguien la cambiara.
     */
    private fun unitOf(articleId: UUID): String = warehouse.articleUnit(articleId).orEmpty()
}
