package com.drp.module.maintenance.adapter.event

import com.drp.module.maintenance.MaintenanceModule
import com.drp.module.maintenance.application.MaintenanceSynchronizer
import com.drp.platform.event.DomainEvent
import com.drp.platform.module.ModuleActivation
import com.drp.platform.module.ModuleEventHandler
import com.drp.platform.tenant.TenantContext
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import java.util.UUID

/**
 * Los tres eventos del core que CMMS escucha.
 *
 * Por `ModuleEventHandler`, que resuelve las tres garantias del bus --correr
 * despues del commit, aislarse y descartar el evento ya atendido--, comprueba la
 * activacion **para el hogar de ese evento** y abre la transaccion `REQUIRES_NEW`.
 * Esa ultima es la unica regla cuyo incumplimiento puede tumbar al core, y desde
 * el Hito 0 no hay que acordarse de ella.
 *
 * **Un solo handler y no tres**, por lo mismo que escribieron Warehouse y Compras:
 * tres clases serian tres suscriptores recibiendo los quince eventos del catalogo
 * cada uno para descartar catorce, tres conjuntos de idempotencia y tres
 * transacciones abiertas por evento.
 *
 * **Y ninguna rama comprueba si el modulo ya sembro.** Es la regla que el Hito 3
 * dejo escrita: [MaintenanceSynchronizer] es la misma clase que usa la siembra,
 * asi que abrir la ficha que falta es exactamente lo que la siembra habria hecho.
 *
 * ---
 *
 * **`DocumentAttached` trae una trampa que los otros dos modulos no tuvieron: su
 * agregado es el DOCUMENTO, no el asset.**
 *
 * Warehouse y Compras leen `aggregateId` como «la cosa que ha cambiado» --un
 * asset, un articulo-- y les vale. Aqui `aggregateId` es el identificador del
 * **documento**, y el asset viaja en el `payload` como `assetId` **anulable**,
 * porque un documento puede colgar de un articulo en vez de un asset (ver 4.1.3).
 *
 * Un handler que copiara el patron de los dos hitos anteriores enlazaria el manual
 * a la ficha de un asset que no existe --el identificador del documento-- **y no
 * fallaria**: no encontraria nada, y el modulo se quedaria sin manuales sin que
 * nada lo dijera. De ahi las dos defensas: la rama lee `payload["assetId"]` y
 * descarta el nulo, y abrir la ficha es una insercion que **solo prospera sobre un
 * `DURABLE` vivo**, asi que un identificador que no sea de una maquina no escribe
 * nada aunque llegara por el sitio equivocado.
 */
@Component
class MaintenanceEventHandler(
    private val machines: MaintenanceSynchronizer,
    activation: ModuleActivation,
    tenantContext: TenantContext,
    transactionManager: PlatformTransactionManager,
) : ModuleEventHandler(
    MaintenanceModule.KEY,
    "MaintenanceEventHandler",
    activation,
    tenantContext,
    transactionManager,
) {

    override fun handleActive(event: DomainEvent) {
        when (event.type) {
            // Una maquina nueva entra en el radar. **No se le genera ningun plan**:
            // por defecto ¿de que? Una caldera pide revision anual y una silla no
            // pide nada, y el core no modela de que clase es cada cosa. Lo que se
            // abre es su ficha, desde la que nace el primer plan con un clic.
            //
            // El filtro por `DURABLE` va aqui **y** en la insercion: aqui porque el
            // evento ya trae el tipo y no hace falta preguntar, y alli porque las
            // otras dos ramas no tienen esa suerte.
            "AssetCreated" -> if (event.payload["type"] == "DURABLE") {
                machines.watch(event.aggregate())
            }

            // Lo que ya no esta en casa no se revisa: sus planes se cancelan. **El
            // historico y la ficha se quedan**, que es lo que distingue cancelar de
            // borrar --que la caldera se reviso tres veces siguio siendo cierto
            // despues de tirarla.
            "AssetDeactivated" -> machines.stopWatching(event.aggregate())

            // **Aqui esta la trampa.** El agregado es el DOCUMENTO; el asset viene
            // en el payload y puede ser nulo, porque un documento tambien cuelga de
            // un articulo. Ver el comentario de la clase.
            "DocumentAttached" -> if (event.payload["documentType"] == MANUAL) {
                event.assetFromPayload()?.let { machines.rememberManual(it, event.aggregate()) }
            }
        }
    }

    private companion object {
        /**
         * El tipo de documento que a este modulo le importa, tal y como el core lo
         * enumera. Es el que se quiere tener a mano al ir a revisar la maquina; la
         * factura y la garantia son de Gastos y de Garantias, que no existen.
         */
        const val MANUAL = "MANUAL"
    }
}

/**
 * El agregado del evento. **Es el documento en `DocumentAttached`** y el asset en
 * los otros dos, que es justamente lo que hace falta no confundir.
 */
private fun DomainEvent.aggregate(): UUID = UUID.fromString(aggregateId)

/**
 * El asset del `payload`, **que es anulable a proposito**: un documento puede
 * colgar de un articulo, y entonces aqui no hay ninguna maquina que mirar.
 *
 * Acepta las dos formas en las que un identificador puede llegar en un `payload`
 * --el `UUID` cuando el evento no ha salido del proceso y el texto si algun dia
 * pasa por una serializacion-- por lo mismo que Warehouse acepta tres formas de un
 * numero: la forma exacta depende de por donde haya pasado el evento, y un
 * `ClassCastException` dentro de un handler no se ve en ninguna parte salvo en el
 * registro.
 */
private fun DomainEvent.assetFromPayload(): UUID? = when (val value = payload["assetId"]) {
    is UUID -> value
    is String -> runCatching { UUID.fromString(value) }.getOrNull()
    else -> null
}
