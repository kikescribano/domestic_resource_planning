package com.drp.module.warehouse.application

import com.drp.platform.event.DomainEvent
import com.drp.platform.event.EventBus
import com.drp.platform.tenant.TenantContext
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Clock
import java.util.UUID

/**
 * Lo que Warehouse le cuenta al resto, con una funcion por tipo.
 *
 * Es la misma forma que `CoreEvents` tiene en el core, y por el mismo motivo:
 * para que ningun sitio escriba a mano el nombre de un evento ni la forma de su
 * `payload`. Aqui pesa mas todavia, porque **hay consumidor previsto** --Compras,
 * en el Hito 4-- y lo que se declare aqui es lo que ese modulo podra leer.
 *
 * **Dos eventos y no mas, y es el criterio del catalogo (README 5.2.3): que
 * alguien lo necesite.** Proveedores no publico ninguno porque no tenia lector
 * hasta el Hito 4; aqui el lector existe y son estos dos los que necesita para
 * decidir que se compra. Publicar ademas «se ha movido un yogur» seria inventarse
 * el contrato de una conversacion que nadie ha pedido, y un evento publicado se
 * retira peor de lo que se anade.
 *
 * **Se publican en el cruce y no en cada cambio.** Mientras el articulo siga por
 * debajo del minimo no se vuelve a publicar, y vuelve a armarse cuando la
 * cantidad sube por encima. Un evento por cada cucharada de azucar dejaria a
 * Compras recibiendo cientos al dia para no decir nada nuevo.
 *
 * El `householdId` y el instante **no se reciben**: salen del `TenantContext` y
 * del reloj, exactamente igual que en el core. Aceptarlos como parametro daria
 * pie a publicar un evento con el hogar de otro, que desde un handler --que corre
 * fuera de una peticion-- es un descuido facilisimo de cometer.
 */
@Service
class WarehouseEvents(
    private val bus: EventBus,
    private val tenantContext: TenantContext,
    private val clock: Clock,
) {

    /**
     * El total vivo de un articulo **acaba de cruzar hacia abajo** su minimo.
     *
     * Dice lo que Warehouse sabe, no lo que hay que hacer: «esto esta bajo
     * minimos» no significa «complalo». Cuanto y cuando es de Compras.
     */
    fun stockBelowMinimum(articleId: UUID, quantity: BigDecimal, minimum: BigDecimal, unit: String) =
        publish(
            "StockBelowMinimum",
            articleId,
            mapOf("quantity" to quantity, "minimumQuantity" to minimum, "unit" to unit),
        )

    /**
     * No queda nada de un articulo en todo el hogar.
     *
     * Es un hecho distinto de estar bajo minimos y no su caso extremo: un articulo
     * sin minimo declarado nunca esta bajo minimos y **si** puede acabarse. Es
     * ademas el caso que la pregunta abierta del Hito 4 --«que pasa cuando un
     * consumible llega a cero»-- tiene que resolver, y hasta que se resuelva este
     * evento es lo unico que lo cuenta.
     */
    fun stockDepleted(articleId: UUID, unit: String) =
        publish("StockDepleted", articleId, mapOf("unit" to unit))

    private fun publish(type: String, aggregateId: UUID, payload: Map<String, Any?>) {
        // Sin hogar no hay evento que valga, igual que en el core: el handler que
        // lo reciba corre despues del commit, cuando ya no queda contexto del que
        // deducirlo. Que esto salte es un fallo de programacion y conviene que se
        // vea, no que se entregue un evento que nadie puede situar.
        val householdId = tenantContext.currentHousehold()
            ?: error("No se puede publicar $type sin hogar en el contexto")

        bus.publish(
            DomainEvent(
                eventId = UUID.randomUUID(),
                type = type,
                occurredAt = clock.instant(),
                householdId = householdId,
                aggregateId = aggregateId.toString(),
                version = DomainEvent.INITIAL_VERSION,
                payload = payload,
            ),
        )
    }
}
