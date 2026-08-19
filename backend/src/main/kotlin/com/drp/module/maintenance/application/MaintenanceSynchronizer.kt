package com.drp.module.maintenance.application

import org.springframework.stereotype.Service
import java.time.Clock
import java.util.UUID

/**
 * Lo que CMMS hace para **ponerse al dia de que maquinas hay**, en un solo sitio.
 *
 * Es la misma decision que tomaron el `StockSynchronizer` de Warehouse y el
 * `ShoppingListSynchronizer` de Compras, y aqui se hereda tal cual porque el Hito
 * 3 la dejo escrita como regla: **la siembra y los handlers comparten estas dos
 * funciones**. Con eso, «el modulo aun no ha sembrado» deja de ser un caso que un
 * handler tenga que contemplar --abre la ficha que necesite y ya-- y, sobre todo,
 * los dos caminos **no pueden divergir**: no hay dos versiones de «esta maquina
 * entra en el radar» que se puedan quedar distintas.
 *
 * Aqui esa regla tiene ademas un caso que en los dos modulos anteriores no se
 * daba: **un `DocumentAttached` puede llegar sobre una maquina cuya ficha no
 * existe todavia** --el hogar acaba de encender el modulo y adjunta el manual
 * antes de que nadie mire nada--. Se resuelve sin ninguna rama especial, porque
 * apuntar el manual abre la ficha si falta.
 *
 * **Nada de esto abre transaccion.** Corre siempre dentro de una que ya esta
 * abierta: la de la activacion cuando lo llama la siembra, y la `REQUIRES_NEW` que
 * `ModuleEventHandler` abre por el modulo cuando lo llama el handler. Abrir otra
 * dejaria la siembra fuera de la transaccion de la activacion, y una activacion
 * que fallara dejaria fichas de un modulo que no llego a encenderse.
 */
@Service
class MaintenanceSynchronizer(
    private val maintenance: MaintenanceRepository,
    private val clock: Clock,
) {

    /**
     * «Esta maquina entra en el radar del modulo.»
     *
     * **Idempotente por el indice unico** de una ficha por maquina, y no por una
     * comprobacion previa: la entrega del bus es at-least-once, asi que el mismo
     * `AssetCreated` puede llegar dos veces.
     *
     * Y **no crea ningun plan**, que es la decision de este hito: el catalogo de
     * eventos daba por hecho un «plan por defecto» que no se sostiene --una caldera
     * pide revision anual y una silla no pide nada, y el core no modela de que
     * clase es cada maquina--. El modulo prepara el sitio; la regla la pone el
     * hogar.
     *
     * El `by` es nulo cuando lo escribe un handler o la siembra: **lo hizo el
     * sistema y no una persona**, que es la convencion del core y aqui es
     * literalmente cierto.
     */
    fun watch(assetId: UUID, by: UUID? = null): Boolean =
        maintenance.openItem(assetId, clock.instant(), by)

    /**
     * «El manual de esta maquina es este documento.»
     *
     * **Gana el ultimo**, que es la semantica normal de «el manual de esto»: quien
     * adjunta un escaneo mejor espera que sea el que se abra al ir a revisarla.
     * Ganar el primero dejaria clavado el peor de los dos sin forma de cambiarlo
     * desde el bus, y de todos modos la eleccion se corrige a mano con el `PATCH`
     * de la ficha, que existe para eso.
     *
     * Abre la ficha si falta, por lo dicho arriba.
     */
    fun rememberManual(assetId: UUID, documentId: UUID, by: UUID? = null): Boolean =
        maintenance.setManual(assetId, documentId, clock.instant(), by)

    /**
     * «Esta maquina ya no esta en casa»: sus planes dejan de vigilarse.
     *
     * **El historico no se toca**, que es lo que distingue cancelar de borrar: que
     * la caldera se reviso tres veces siguio siendo cierto despues de tirarla. Y la
     * ficha tampoco, porque de ella cuelga ese historico.
     *
     * @return cuantos planes cancelo.
     */
    fun stopWatching(assetId: UUID, by: UUID? = null): Int =
        maintenance.cancelPlansOf(assetId, clock.instant(), by)
}
