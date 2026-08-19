package com.drp.module.purchasing.application

import com.drp.module.purchasing.domain.ItemOrigin
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.UUID

/**
 * Lo que Compras hace para **ponerse al dia de lo que falta**, en un solo sitio.
 *
 * Existe por la misma decision que el `StockSynchronizer` de Warehouse, y aqui se
 * hereda tal cual porque el Hito 3 la dejo escrita como regla para los dos modulos
 * siguientes: **la siembra y el handler comparten esta funcion**. Con eso, «el
 * modulo aun no ha sembrado» deja de ser un caso que el handler tenga que
 * contemplar --abre la linea que necesite y ya-- y, sobre todo, los dos caminos
 * **no pueden divergir**: no hay dos versiones de «esto hace falta» que se puedan
 * quedar distintas.
 *
 * **Nada de esto abre transaccion.** Corre siempre dentro de una que ya esta
 * abierta: la de la activacion cuando lo llama la siembra, y la `REQUIRES_NEW` que
 * `ModuleEventHandler` abre por el modulo cuando lo llama el handler. Abrir otra
 * dejaria la siembra fuera de la transaccion de la activacion, y una activacion
 * que fallara dejaria lineas sembradas de un modulo que no llego a encenderse.
 */
@Service
class ShoppingListSynchronizer(
    private val purchasing: PurchasingRepository,
    private val clock: Clock,
) {

    /**
     * «Esto hace falta.»
     *
     * **Idempotente por el indice unico parcial** --una sola linea viva por
     * articulo-- y no por una comprobacion previa: la entrega del bus es
     * at-least-once, asi que el mismo `StockBelowMinimum` puede llegar dos veces y
     * comprobar antes de insertar dejaria una ventana entre las dos cosas.
     *
     * Cuando ya habia una linea viva, lo unico que puede pasar es que **el origen
     * suba**: acabarse es una noticia mas fuerte que bajar del minimo, y la lista
     * tiene que poder ordenarse por ella. Bajar no, porque lo contrario no es una
     * noticia --que algo que se acabo siga bajo minimos ya se sabia.
     *
     * **No trae cantidad.** Warehouse dice que falta, no cuanto hay que comprar:
     * eso es de este lado y lo decide una persona. Una linea sin cantidad es una
     * linea completa.
     */
    fun need(articleId: UUID, origin: ItemOrigin, by: UUID? = null) {
        val now = clock.instant()
        // El `by` es nulo cuando lo escribe un handler --**lo hizo el sistema, no
        // una persona**--, que es la misma convencion del core y aqui es
        // literalmente cierto.
        if (purchasing.openItemForArticle(articleId, origin, quantity = null, at = now, by = by)) return

        purchasing.raiseOrigin(articleId, origin, now)
    }
}
