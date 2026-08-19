package com.drp.module.purchasing.adapter.event

import com.drp.module.purchasing.PurchasingModule
import com.drp.module.purchasing.application.ShoppingListSynchronizer
import com.drp.module.purchasing.domain.ItemOrigin
import com.drp.platform.event.DomainEvent
import com.drp.platform.module.ModuleActivation
import com.drp.platform.module.ModuleEventHandler
import com.drp.platform.tenant.TenantContext
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import java.util.UUID

/**
 * **El primer modulo que escucha a otro modulo**, y con el se retira el riesgo
 * arquitectonico principal de la fase: dos modulos que se hablan sin depender uno
 * de que el otro este activo.
 *
 * Consume los dos eventos que Warehouse publica, por `ModuleEventHandler`, que
 * resuelve las tres garantias del bus --correr despues del commit, aislarse y
 * descartar el evento ya atendido--, comprueba la activacion **para el hogar de
 * ese evento** y abre la transaccion `REQUIRES_NEW`.
 *
 * **Lo que hay que tener presente al leer esto es lo que NO hay:**
 *
 * **No hay ningun import de Warehouse.** Ni una clase, ni un enumerado, ni el
 * nombre de una tabla. Lo unico que cruza la frontera son dos cadenas --el tipo del
 * evento-- y un identificador que es **del core**: la ficha de Warehouse declara
 * que su `articleId` sigue siendo legible aunque ese modulo se apague,
 * precisamente para que un consumidor no quede colgando.
 *
 * **No hay ninguna rama para «Warehouse esta apagado».** Con Warehouse apagado
 * estos eventos **no llegan** --no llegan vacios, no llegan-- y este handler
 * simplemente no se despierta. La lista deja de llenarse sola y se llena a mano,
 * que es la operacion `AddShoppingListItem` y existe por esto.
 *
 * **Y no hay ninguna comprobacion de «¿ya sembro?».** Es la regla que el Hito 3
 * dejo escrita: `ShoppingListSynchronizer` es la misma clase que usa la siembra,
 * asi que abrir la linea que falta es exactamente lo que la siembra habria hecho.
 * La alternativa --abandonar si no encuentra nada-- convierte una carrera de
 * milisegundos entre la activacion y el primer evento en un dato que falta para
 * siempre.
 *
 * **Un solo handler y no dos**, por lo mismo que Warehouse escribio: dos clases
 * serian dos suscriptores recibiendo los quince eventos del catalogo cada uno para
 * descartar catorce, y dos conjuntos de idempotencia.
 */
@Component
class PurchasingEventHandler(
    private val list: ShoppingListSynchronizer,
    activation: ModuleActivation,
    tenantContext: TenantContext,
    transactionManager: PlatformTransactionManager,
) : ModuleEventHandler(
    PurchasingModule.KEY,
    "PurchasingEventHandler",
    activation,
    tenantContext,
    transactionManager,
) {

    override fun handleActive(event: DomainEvent) {
        when (event.type) {
            // «Esto bajo del minimo que el hogar fijo.» Entra en la lista, y el
            // evento **no dice cuanto hay que comprar**: cuanto y cuando es de
            // este lado, que es la frontera escrita en las dos fichas.
            "StockBelowMinimum" -> list.need(event.aggregate(), ItemOrigin.LOW_STOCK)

            // «Esto se acabo.» **Entra solo, sin que nadie lo diga**, que es la
            // respuesta de este hito a la pregunta que el catalogo de eventos daba
            // por hecha y nadie habia decidido. Es ademas una noticia mas fuerte
            // que la anterior, asi que sube el origen de la linea que ya hubiera.
            "StockDepleted" -> list.need(event.aggregate(), ItemOrigin.DEPLETED)
        }
    }
}

/**
 * El agregado del evento, que es **un articulo del core** y no una fila de
 * Warehouse. Es lo que permite guardarlo aqui sin quedar colgando de que aquel
 * modulo siga encendido.
 */
private fun DomainEvent.aggregate(): UUID = UUID.fromString(aggregateId)
