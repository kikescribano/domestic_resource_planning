package com.drp.module.warehouse.application

import com.drp.module.warehouse.domain.ArticleStockTotal
import com.drp.module.warehouse.domain.ExpiryStage
import com.drp.module.warehouse.domain.StockItem
import com.drp.module.warehouse.domain.StockLot
import com.drp.module.warehouse.domain.StockMovement
import com.drp.module.warehouse.domain.WarehouseArticle
import com.drp.module.warehouse.domain.WarehouseLocation
import com.drp.platform.page.Page
import com.drp.platform.page.Pagination
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * El almacen del modulo.
 *
 * **Ninguna operacion recibe el hogar**, igual que en el core, en plataforma y en
 * Proveedores: el filtro lo pone la politica de RLS a partir del
 * `app.household_id` que fija el gestor de transacciones. Aqui importa mas que en
 * ningun sitio anterior, porque **quien mas llama a este puerto no nace de una
 * peticion** --los handlers de eventos y el recorrido nocturno-- y un parametro
 * `householdId` seria la puerta por la que un handler apuntaria en el cuaderno
 * del vecino.
 *
 * Las lecturas que dicen «del core» leen `assets`, `articles` y `locations` **por
 * SQL, sin importar ninguna clase suya**, que es la misma forma que usan
 * Proveedores y la siembra del modulo de prueba. Importar `AssetEntity`
 * funcionaria y ataria el modulo a la persistencia del core, que es peor que
 * atarlo a tres nombres de tabla.
 */
interface WarehouseRepository {

    // --- Las dos fichas ----------------------------------------------------

    /**
     * Abre la ficha de un articulo si no la tenia, y **no hace nada si ya
     * existia**.
     *
     * Es la funcion que comparten la siembra y el handler de `ArticleCreated`, y
     * compartirla es la decision: mientras sea la misma, «el modulo aun no ha
     * sembrado» deja de ser un caso que ningun handler tenga que contemplar, y la
     * siembra y los handlers no pueden divergir.
     */
    fun openArticleFile(articleId: UUID, at: Instant, by: UUID?)

    /** Lo mismo para un sitio. Ver [openArticleFile]. */
    fun openLocationFile(locationId: UUID, at: Instant, by: UUID?)

    fun findArticleFile(articleId: UUID): WarehouseArticle?

    fun findLocationFile(locationId: UUID): WarehouseLocation?

    fun saveArticleFile(file: WarehouseArticle): WarehouseArticle

    fun saveLocationFile(file: WarehouseLocation): WarehouseLocation

    // --- El cuaderno -------------------------------------------------------

    /**
     * Asienta un movimiento, **descartando en silencio el que ya estuviera**.
     *
     * Los dos indices unicos parciales que lo protegen --uno por `event_id` y uno
     * por existencia para el `OPENING`-- son lo que hace idempotentes al handler
     * y a la siembra sin que ninguno de los dos tenga que comprobar nada antes.
     */
    fun record(movement: StockMovement)

    fun listMovements(assetId: UUID?, articleId: UUID?, pagination: Pagination): Page<StockMovement>

    /** Los ultimos de una existencia, para su ficha. */
    fun recentMovements(assetId: UUID, limit: Int): List<StockMovement>

    // --- Los lotes ---------------------------------------------------------

    fun findLot(lotId: UUID): StockLot?

    fun saveLot(lot: StockLot): StockLot

    fun listLots(assetId: UUID?, includeConsumed: Boolean, pagination: Pagination): Page<StockLot>

    /** Los lotes vivos de una existencia. Lo usa la regla de que no sumen de mas. */
    fun liveLotsOf(assetId: UUID): List<StockLot>

    /** Da por consumidos los lotes vivos de una existencia. Devuelve cuantos. */
    fun consumeLotsOf(assetId: UUID, at: Instant): Int

    /**
     * Los lotes vivos que caducan como muy tarde en [horizon], con la antelacion
     * que les toque ya resuelta.
     *
     * La cadena --sitio, articulo, y si ninguno la fija, la del modulo-- se
     * resuelve **en la consulta** y no en memoria: con la despensa entera cargada
     * para descartar el noventa por ciento, la comprobacion nocturna pagaria una
     * lectura completa por hogar y por noche.
     */
    fun lotsDueBy(horizon: LocalDate, defaultLeadDays: Int): List<DueLot>

    fun markLotNotified(lotId: UUID, stage: ExpiryStage, at: Instant)

    // --- Lo que se lee del core -------------------------------------------

    /** Las existencias vivas de consumible, con su cantidad **del core**. */
    fun listStock(filter: StockFilter, pagination: Pagination): Page<StockItem>

    fun findStockItem(assetId: UUID): StockItem?

    /**
     * Lo que hay vivo de un articulo en **todo el hogar**, sumando sus
     * existencias.
     *
     * Es lo que se compara con el minimo, y por eso suma en lugar de mirar una
     * existencia: tener tres botes de arroz repartidos por la casa no es estar
     * bajo minimos aunque ninguno llegue solo.
     */
    fun totalOf(articleId: UUID): BigDecimal

    /** Los articulos con minimo cuyo total ya esta por debajo o por encima, para el recalculo nocturno. */
    fun totalsForWatchedArticles(): List<ArticleStockTotal>

    /** Lo que el core sabe de una existencia: articulo, sitio y cantidad. */
    fun coreStockFacts(assetId: UUID): CoreStockFacts?

    /** La `unit` del articulo, que es del core. Se lee y no se copia. */
    fun articleUnit(articleId: UUID): String?

    /** El nombre del articulo, tambien del core y por lo mismo. */
    fun articleName(articleId: UUID): String?

    /**
     * El nombre del sitio, del core igual que el del articulo.
     *
     * Existe por la misma razon que [articleName] y no por simetria: **es como se
     * comprueba que el sitio existe en este hogar** antes de abrirle ficha. RLS
     * hace el resto --el de otro hogar no se ve-- asi que devolver nulo significa
     * las dos cosas a la vez, que es justo lo que el contrato quiere responder.
     */
    fun locationName(locationId: UUID): String?

    /** Los identificadores que la siembra necesita recorrer, del core. */
    fun liveArticleIds(): List<UUID>

    fun allLocationIds(): List<UUID>

    fun liveStockItemIds(): List<UUID>
}

/**
 * Lo que el core dice de una existencia en este instante.
 *
 * Se lee y no se guarda: es la mitad del modulo que **no** tiene tabla, y
 * tenerla en un tipo propio es lo que deja ver de un vistazo que ninguna de estas
 * cuatro cosas vive aqui.
 */
data class CoreStockFacts(
    val assetId: UUID,
    val articleId: UUID?,
    val locationId: UUID?,
    val locationName: String?,
    val quantity: BigDecimal?,
    val isLiveConsumable: Boolean,
)

/** Un lote candidato a avisar, con la antelacion que le corresponde ya resuelta. */
data class DueLot(val lot: StockLot, val leadDays: Int, val articleName: String)

data class StockFilter(
    val query: String? = null,
    val locationId: UUID? = null,
    val belowMinimum: Boolean = false,
    /** Solo lo que caduca dentro de tantos dias. Nulo no filtra. */
    val expiringWithinDays: Int? = null,
)
