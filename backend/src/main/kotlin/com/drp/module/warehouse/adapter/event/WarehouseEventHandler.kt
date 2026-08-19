package com.drp.module.warehouse.adapter.event

import com.drp.module.warehouse.WarehouseModule
import com.drp.module.warehouse.application.StockSynchronizer
import com.drp.module.warehouse.domain.MovementKind
import com.drp.platform.event.DomainEvent
import com.drp.platform.module.ModuleActivation
import com.drp.platform.module.ModuleEventHandler
import com.drp.platform.tenant.TenantContext
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import java.math.BigDecimal
import java.util.UUID

/**
 * **El primer modulo que reacciona a lo que pasa en el core.**
 *
 * Consume seis de los trece eventos del catalogo (README 5.2.3) por
 * `ModuleEventHandler`, que resuelve por el las tres garantias del bus --correr
 * despues del commit, aislarse y descartar el evento ya atendido--, comprueba la
 * activacion **para el hogar de ese evento** y, sobre todo, **abre la transaccion
 * `REQUIRES_NEW`**. Esa ultima es la unica regla cuyo incumplimiento puede tumbar
 * al core, y desde el Hito 0 no hay que acordarse de ella: la abre la clase base.
 *
 * **Un solo handler y no seis.** Seis clases serian seis suscriptores recibiendo
 * los trece eventos cada uno para descartar doce, seis conjuntos de idempotencia
 * y seis transacciones abiertas por evento. Con uno, la reaccion del modulo al
 * core esta escrita en un sitio --que ademas es donde hay que mirar cuando algo no
 * cuadra-- y las seis ramas no pueden estorbarse, porque atienden tipos distintos.
 *
 * **Que hace cada rama cuando el modulo no tiene nada de ese hogar todavia: abrir
 * lo que necesite.** No hay ninguna comprobacion de «¿ya sembro?», y es
 * deliberado: [StockSynchronizer] es la misma clase que usa la siembra, asi que
 * abrir la ficha que falta es exactamente lo que la siembra habria hecho. La
 * alternativa --abandonar si no encuentra la ficha-- convierte una carrera de
 * milisegundos entre la activacion y el primer evento en un dato que falta para
 * siempre.
 *
 * **Lo que el modulo escribe en el cuaderno lo escribe siempre desde aqui**, y
 * nunca desde una operacion suya. Un solo escritor es lo que garantiza que un
 * consumo hecho desde la pantalla de Warehouse y uno hecho con el `PATCH` del core
 * produzcan exactamente el mismo asiento.
 */
@Component
class WarehouseEventHandler(
    private val stock: StockSynchronizer,
    activation: ModuleActivation,
    tenantContext: TenantContext,
    transactionManager: PlatformTransactionManager,
) : ModuleEventHandler(
    WarehouseModule.KEY,
    "WarehouseEventHandler",
    activation,
    tenantContext,
    transactionManager,
) {

    override fun handleActive(event: DomainEvent) {
        when (event.type) {
            // Se abre la ficha del articulo: es donde vivira su minimo y su
            // antelacion el dia que alguien los fije.
            "ArticleCreated" -> stock.openArticleFile(event.aggregate())

            // Y la del sitio, por lo mismo: «en la nevera avisame con tres dias»
            // es una regla del sitio, y el core no tiene donde ponerla.
            "LocationCreated" -> stock.openLocationFile(event.aggregate())

            // Una existencia nueva. El evento **no lleva la cantidad dentro**
            // --`AssetCreated` describe el alta, no el contador-- asi que se lee
            // del core, que es lo mismo que hace la siembra.
            "AssetCreated" -> if (event.payload["type"] == "CONSUMABLE") {
                stock.openStockItem(event.aggregate(), MovementKind.INTAKE, event.eventId)
            }

            // El asiento con el motivo que trae el evento. Los cuatro motivos del
            // core se llaman aqui igual **a proposito**: renombrarlos daria dos
            // vocabularios para el mismo hecho.
            "AssetQuantityChanged" -> stock.recordQuantityChange(
                assetId = event.aggregate(),
                kind = event.movementKind(),
                previous = event.decimal("previousQuantity"),
                quantity = event.decimal("quantity"),
                occurredAt = event.occurredAt,
                eventId = event.eventId,
            )

            "AssetMoved" -> stock.recordRelocation(event.aggregate(), event.occurredAt, event.eventId)

            // Los lotes de algo que ya no esta dejan de vigilarse. **No se asienta
            // ningun movimiento de cantidad**: la baja de una existencia con resto
            // ya publica su `AssetQuantityChanged` con motivo `DECOMMISSION`, y
            // asentar aqui otra vez duplicaria la salida.
            "AssetDeactivated" -> stock.closeStockItem(event.aggregate())
        }
    }
}

private fun DomainEvent.aggregate(): UUID = UUID.fromString(aggregateId)

/**
 * El motivo del core traducido al del cuaderno, **con el mismo nombre**.
 *
 * Un motivo que el core anadiera y este modulo no conociera se asienta como
 * `ADJUSTMENT` en lugar de reventar: perder el matiz de un asiento es mucho menos
 * grave que perder el asiento entero, y el fallo de un handler no se ve en ninguna
 * parte salvo en el registro.
 */
private fun DomainEvent.movementKind(): MovementKind =
    runCatching { MovementKind.valueOf(payload["reason"] as String) }.getOrDefault(MovementKind.ADJUSTMENT)

/**
 * Un numero del `payload`, que viaja como `BigDecimal` pero cuya forma exacta
 * depende de por donde haya pasado el evento.
 */
private fun DomainEvent.decimal(field: String): BigDecimal = when (val value = payload[field]) {
    is BigDecimal -> value
    is Number -> BigDecimal(value.toString())
    is String -> BigDecimal(value)
    else -> BigDecimal.ZERO
}
