package com.drp.module.warehouse.domain

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * El dominio del modulo Warehouse: **como llego a haber lo que hay**, cuanto
 * habria que tener siempre y cuando se estropea.
 *
 * La ficha esta en `docs/backend/modules/warehouse.md` y se escribio antes que
 * esto. Lo que hay que tener presente al leer este fichero es la frontera que
 * declara, porque es lo unico que impide que este modulo se convierta en una
 * segunda version del core:
 *
 * **El core mantiene un contador y aqui no hay ninguno.** Ninguna de estas
 * clases guarda «cuanto hay» de una existencia: cuando hace falta se lee de
 * `assets.quantity`. La unica cantidad de este fichero es la de un [StockLot], y
 * responde a otra pregunta --cuanto de lo que hay es de ESTE lote-- cuya suma
 * puede quedarse por debajo del contador sin que nada este mal: lo que no esta en
 * ningun lote es lo que nadie fecho.
 */

/**
 * Un asiento del cuaderno.
 *
 * **No se modifica nunca.** Se escribe y se queda, que es lo que lo convierte en
 * un libro y no en una vista: si el arroz se acaba manana, el apunte de que ayer
 * entraron 900 g siguio siendo cierto.
 *
 * Lleva el nombre del sitio dentro por la misma razon, y no es redundancia:
 * `DeleteLocation` borra la fila de verdad, asi que el identificador puede
 * quedarse apuntando a nada y el nombre es lo unico que seguira diciendo donde
 * paso aquello.
 */
data class StockMovement(
    val id: UUID,
    val assetId: UUID,
    val articleId: UUID?,
    val locationId: UUID?,
    val locationName: String?,
    val kind: MovementKind,
    /** Nulas en una [MovementKind.RELOCATION], que no cambia ninguna cantidad. */
    val previousQuantity: BigDecimal?,
    val quantity: BigDecimal?,
    val occurredAt: Instant,
    val eventId: UUID?,
    val createdBy: UUID?,
) {
    /** Lo que entro o salio. Nulo cuando el asiento no habla de cantidad. */
    val delta: BigDecimal?
        get() = if (quantity != null && previousQuantity != null) quantity - previousQuantity else null
}

/**
 * Por que se movio.
 *
 * Los cuatro de en medio son **los del `QuantityChangeReason` del core, con el
 * mismo nombre a proposito**: renombrarlos daria dos vocabularios para el mismo
 * hecho, y quien leyera el cuaderno tendria que traducir.
 *
 * Los dos propios son [OPENING] --lo que habia el dia que el modulo se encendio,
 * que no lo cuenta ningun evento porque paso antes-- y [RELOCATION], que es un
 * movimiento de existencias que no cambia ninguna cantidad. Va en el mismo libro
 * porque «movimiento de existencias» incluye mover algo de sitio, y separarlo en
 * dos tablas obligaria a leer las dos para saber que le paso a algo.
 */
enum class MovementKind {
    OPENING,
    INTAKE,
    ADJUSTMENT,
    MERGE,
    DECOMMISSION,
    RELOCATION,
    ;

    /** Cierto si el asiento habla de cantidad, que es todo menos mover de sitio. */
    fun movesQuantity(): Boolean = this != RELOCATION
}

/**
 * La ficha del modulo sobre un articulo del core: **donde vive la regla**.
 *
 * No duplica nada de `Article`. El nombre, la unidad y el envase son del core y
 * se leen de alli; lo de aqui es cuanto habria que tener siempre y con cuanta
 * antelacion avisar.
 *
 * @param lowStockSince **desde cuando** esta por debajo del minimo, y no un
 *   booleano. Un booleano se puede calcular al vuelo y por eso no sirve: sin
 *   fecha de entrada, la comprobacion nocturna no distingue «acaba de caer» de
 *   «lleva tres semanas caido» y avisaria las treinta noches.
 * @param lowStockNotifiedAt si ya se dijo. Se borra junto con [lowStockSince] al
 *   reponer, que es lo que **vuelve a armar** el aviso para la proxima caida.
 */
data class WarehouseArticle(
    val id: UUID,
    val articleId: UUID,
    val minimumQuantity: BigDecimal?,
    val expiryLeadDays: Int?,
    val lowStockSince: Instant?,
    val lowStockNotifiedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val createdBy: UUID?,
    val updatedBy: UUID?,
) {
    val watchesMinimum: Boolean get() = minimumQuantity != null

    val isLowOnStock: Boolean get() = lowStockSince != null

    /** Bajo minimos incluye estar **en** el minimo: tener justo lo ultimo ya es reponer. */
    fun isBelowMinimum(available: BigDecimal): Boolean =
        minimumQuantity?.let { available <= it } == true
}

/**
 * La ficha del modulo sobre un sitio del core.
 *
 * Existe porque «en la nevera avisame con tres dias y en la despensa con treinta»
 * es una regla **del sitio**, y el core no tiene donde ponerla: su `Location`
 * describe el contenedor, no como se vigila lo que hay dentro.
 *
 * **No guarda el nombre**, al contrario que un asiento del cuaderno, y la
 * diferencia no es incoherencia sino la misma regla aplicada dos veces: un
 * asiento es historia y se queda con el nombre de aquel dia, mientras que esto es
 * configuracion viva y tiene que decir el nombre de hoy. Ademas no hay ningun
 * evento de renombrado de ubicacion, asi que una copia se quedaria vieja sin que
 * nada lo dijera.
 */
data class WarehouseLocation(
    val id: UUID,
    val locationId: UUID,
    val expiryLeadDays: Int?,
    val notes: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val createdBy: UUID?,
    val updatedBy: UUID?,
)

/**
 * Un lote de una existencia: cuanto de lo que hay caduca cuando.
 *
 * @param quantity **la unica cantidad que este modulo guarda**. No es un segundo
 *   contador: la suma de los lotes de una existencia puede quedarse por debajo de
 *   lo que el core dice que hay, y eso es normal --lo que no esta en ningun lote
 *   es lo que nadie se molesto en fechar--. Lo que no puede es pasarse.
 * @param notifiedStage en que fase aviso. Son **dos hechos distintos y no una
 *   repeticion**: `NEAR` cuando entra en la ventana de antelacion y `EXPIRED` el
 *   dia que caduca de verdad. Con esto un lote avisa como mucho dos veces en toda
 *   su vida, en lugar de todas las noches.
 */
data class StockLot(
    val id: UUID,
    val assetId: UUID,
    val articleId: UUID,
    val lotCode: String?,
    val expiresOn: LocalDate,
    val quantity: BigDecimal,
    val consumedAt: Instant?,
    val notifiedStage: ExpiryStage?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val createdBy: UUID?,
    val updatedBy: UUID?,
) {
    val isLive: Boolean get() = consumedAt == null

    /**
     * En que fase esta **hoy**, con la antelacion que le toque, o nulo si todavia
     * no hay nada que decir.
     *
     * Es el dominio y no la consulta quien decide esto, porque es la regla del
     * modulo: la consulta solo acota a los candidatos.
     */
    fun stageOn(today: LocalDate, leadDays: Int): ExpiryStage? = when {
        !expiresOn.isAfter(today) -> ExpiryStage.EXPIRED
        !expiresOn.isAfter(today.plusDays(leadDays.toLong())) -> ExpiryStage.NEAR
        else -> null
    }

    /**
     * Si hay que avisar de [stage], que es **solo cuando la fase avanza**.
     *
     * De `NEAR` a `EXPIRED` se avisa otra vez porque es una noticia nueva; de
     * `NEAR` a `NEAR`, no, que es lo que evita las treinta noches seguidas.
     */
    fun shouldAnnounce(stage: ExpiryStage): Boolean =
        isLive && (notifiedStage == null || notifiedStage.ordinal < stage.ordinal)
}

/**
 * Las dos fases de un lote, **en orden**: el `ordinal` es lo que decide si una
 * fase avanza sobre la anterior, asi que reordenarlas cambia la regla.
 */
enum class ExpiryStage { NEAR, EXPIRED }

/**
 * Lo que Warehouse ve de una existencia del core, ya juntado.
 *
 * La `quantity` de aqui **es la del core**, leida al construir esto y no
 * guardada en ninguna tabla del modulo. Va en la misma clase que el minimo y la
 * caducidad porque es la unica forma de responder la pregunta que este modulo
 * existe para responder --«¿queda poco?»-- sin que nadie tenga que cruzarlo a
 * mano.
 */
data class StockItem(
    val assetId: UUID,
    val articleId: UUID,
    val articleName: String,
    val unit: String,
    val locationId: UUID?,
    val locationName: String?,
    /** **Del core.** Ver la nota de arriba. */
    val quantity: BigDecimal,
    val minimumQuantity: BigDecimal?,
    val nearestExpiry: LocalDate?,
    val lotCount: Int,
) {
    val isBelowMinimum: Boolean get() = minimumQuantity?.let { quantity <= it } == true
}

/** Cuanto hay vivo de un articulo en todo el hogar, sumando sus existencias. */
data class ArticleStockTotal(val articleId: UUID, val quantity: BigDecimal)
