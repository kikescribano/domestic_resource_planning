package com.drp.module.warehouse.application

import com.drp.module.warehouse.WarehouseModule
import com.drp.module.warehouse.domain.ExpiryStage
import com.drp.platform.notice.NoticeDraft
import com.drp.platform.schedule.CheckOwner
import com.drp.platform.schedule.ScheduledCheck
import com.drp.platform.tenant.HouseholdCalendar
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate

/**
 * Las dos comprobaciones periodicas de Warehouse: **las primeras de un modulo de
 * verdad**.
 *
 * El Hito 1 dejo el punto de extension listo y hasta hoy solo lo usaba el modulo
 * de prueba. Las dos declaran `CheckOwner.Module`, asi que el recorrido diario
 * **solo entra en los hogares que tengan Warehouse encendido** — que es la regla
 * que la ADR-011 puso y que hasta ahora nadie real ejercitaba.
 *
 * **Y aqui hubo que decidir algo que ninguno de los dos ejemplos previos
 * resolvia.** Los tres procesos del core son idempotentes y no repiten; el modulo
 * de prueba repite **a proposito**, para poder contar pasadas. La decision, escrita
 * en la ficha del modulo:
 *
 * > Un aviso se levanta cuando la condicion empieza a ser cierta, y **no vuelve a
 * > levantarse mientras siga siendolo**.
 *
 * Un yogur caducado que avisara treinta noches seguidas es la forma mas rapida de
 * que se filtre el resumen diario entero, y con el, el aviso que si importaba.
 *
 * El estado que lo hace posible vive **en las tablas del modulo** --la fase
 * avisada de un lote, la fecha de entrada bajo minimos de un articulo-- y no
 * consultando `household_notices`. Consultarla habria sido la otra salida, y se
 * descarta porque obliga al modulo a leer una tabla de plataforma y a reconocer
 * sus propios avisos por el texto.
 */

/**
 * Lo que caduca pronto, y lo que ya caduco.
 *
 * **Un lote avisa dos veces como mucho, y no es una repeticion sino dos hechos
 * distintos**: `NEAR` cuando entra en la ventana de antelacion --«caduca el
 * jueves»-- y `EXPIRED` el dia que de verdad caduca --«se paso»--. La segunda es
 * una noticia nueva, y no darla dejaria al hogar con un aviso de hace tres semanas
 * como unica advertencia.
 *
 * La antelacion sale de la cadena **sitio → articulo → siete dias**, resuelta en
 * la consulta. El sitio manda sobre el articulo porque es lo que gobierna de
 * verdad: el mismo yogur aguanta distinto dentro de la nevera que fuera.
 */
@Component
class WarehouseExpiryCheck(
    private val warehouse: WarehouseRepository,
    private val calendar: HouseholdCalendar,
    private val clock: Clock,
) : ScheduledCheck {

    override val name: String = "WarehouseExpiryCheck"
    override val owner: CheckOwner = CheckOwner.Module(WarehouseModule.KEY)

    override fun check(): List<NoticeDraft> {
        // **El dia es el del hogar y no el del servidor.** Que un yogur haya
        // caducado es una frase sobre el calendario de la cocina, no sobre el de
        // Greenwich.
        val today = calendar.today()

        // El horizonte se acota por la antelacion mas larga posible, y la fase
        // exacta la decide el dominio lote a lote: la consulta no puede aplicar
        // una ventana por fila sin repetir la regla en SQL.
        val horizon = today.plusDays(MAX_LEAD_DAYS)

        return warehouse.lotsDueBy(horizon, WarehouseModule.DEFAULT_EXPIRY_LEAD_DAYS)
            .mapNotNull { due ->
                val stage = due.lot.stageOn(today, due.leadDays) ?: return@mapNotNull null
                if (!due.lot.shouldAnnounce(stage)) return@mapNotNull null

                // Se marca **antes** de devolver el aviso, no despues: quien
                // guarda el aviso es `DailySweep`, en esta misma transaccion, asi
                // que si algo falla se deshacen las dos cosas juntas. Marcar
                // despues exigiria un segundo paso que nadie invoca.
                warehouse.markLotNotified(due.lot.id, stage, clock.instant())
                draftFor(due.articleName, due.lot.expiresOn, due.lot.lotCode, stage)
            }
    }

    private fun draftFor(article: String, expiresOn: LocalDate, lotCode: String?, stage: ExpiryStage): NoticeDraft {
        val lot = lotCode?.let { " (lote $it)" }.orEmpty()
        return when (stage) {
            ExpiryStage.NEAR -> NoticeDraft(
                kind = "WAREHOUSE_EXPIRY_NEAR",
                title = "$article caduca pronto",
                body = "Caduca el $expiresOn$lot. Todavía estás a tiempo de gastarlo.",
            )

            ExpiryStage.EXPIRED -> NoticeDraft(
                kind = "WAREHOUSE_EXPIRED",
                title = "$article ha caducado",
                body = "Caducó el $expiresOn$lot. Míralo antes de usarlo.",
            )
        }
    }

    private companion object {
        /**
         * El horizonte de la consulta: un ano.
         *
         * No es la antelacion de nadie, sino el techo por encima del cual ninguna
         * antelacion configurable puede sacar un lote de la ventana. Acotarlo al
         * valor por omision dejaria fuera al sitio que pidiera avisar con noventa
         * dias, y no acotarlo traeria la despensa entera cada noche.
         */
        const val MAX_LEAD_DAYS = 365L
    }
}

/**
 * Lo que ha bajado del minimo que el hogar fijo.
 *
 * **No lo calcula esta clase.** El estado --desde cuando esta bajo minimos-- lo
 * mantiene [StockSynchronizer] en el momento en que la cantidad cruza, que es
 * cuando se sabe de verdad; aqui solo se recorre lo que ya esta marcado y todavia
 * no se ha dicho. Calcularlo de nuevo cada noche daria dos sitios donde vive la
 * misma regla, y el segundo se quedaria viejo.
 *
 * Aun asi **se recalcula antes de mirar**, y no es contradictorio: la cantidad de
 * un articulo puede haber cambiado sin pasar por ningun evento del core --nunca
 * deberia, pero un modulo que se sincroniza no puede dar eso por hecho-- y esta
 * pasada es la red que lo corrige. Es barato: solo mira los articulos que tienen
 * minimo declarado.
 */
@Component
class WarehouseMinimumStockCheck(
    private val warehouse: WarehouseRepository,
    private val stock: StockSynchronizer,
    private val clock: Clock,
) : ScheduledCheck {

    override val name: String = "WarehouseMinimumStockCheck"
    override val owner: CheckOwner = CheckOwner.Module(WarehouseModule.KEY)

    override fun check(): List<NoticeDraft> {
        // La lista se toma **una vez** y se recorre dos: primero para poner al dia
        // el estado, y luego para mirar quien ha quedado caido y sin decir. Pedirla
        // dos veces daria dos consultas para responder lo mismo.
        val watched = warehouse.totalsForWatchedArticles()
        watched.forEach { stock.refreshArticle(it.articleId) }

        return watched.mapNotNull { total ->
            val file = warehouse.findArticleFile(total.articleId) ?: return@mapNotNull null

            // Caido y sin decir todavia. Las dos condiciones juntas son lo que
            // evita las treinta noches seguidas: la segunda noche
            // `lowStockNotifiedAt` ya no es nulo, y el articulo no vuelve a
            // aparecer hasta que se reponga --lo que borra las dos marcas-- y
            // vuelva a caer.
            if (!file.isLowOnStock || file.lowStockNotifiedAt != null) return@mapNotNull null

            val now = clock.instant()
            warehouse.saveArticleFile(file.copy(lowStockNotifiedAt = now, updatedAt = now))

            // El nombre y la unidad son del articulo, que es del core: se leen al
            // escribir el aviso y no se copian a ninguna tabla de aqui. El aviso
            // guarda el texto de ese dia, que es la regla de la ADR-011.
            val available = warehouse.totalOf(total.articleId).stripTrailingZeros().toPlainString()
            val minimum = file.minimumQuantity?.stripTrailingZeros()?.toPlainString()
            val unit = warehouse.articleUnit(total.articleId).orEmpty().lowercase()

            NoticeDraft(
                kind = "WAREHOUSE_STOCK_BELOW_MINIMUM",
                title = "Queda poco de ${warehouse.articleName(total.articleId) ?: "un artículo"}",
                body = "Quedan $available $unit y el mínimo que fijaste son $minimum.",
            )
        }
    }
}
