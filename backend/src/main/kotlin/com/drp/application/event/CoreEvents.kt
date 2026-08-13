package com.drp.application.event

import com.drp.application.port.EventBus
import com.drp.application.tenant.TenantContext
import com.drp.domain.event.DomainEvent
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Clock
import java.util.UUID

/**
 * El catalogo de eventos del core, con una funcion por tipo.
 *
 * Existe para que ningun caso de uso escriba a mano el nombre de un evento ni la
 * forma de su `payload`. Con `bus.publish(DomainEvent("AssetMoved", ...))`
 * repartido por diez sitios, el dia que un modulo empiece a escuchar descubre
 * diez formas parecidas y ninguna igual; aqui la forma se decide una vez.
 *
 * El `householdId` y el instante **no se reciben**: salen del `TenantContext` y
 * del reloj. Aceptarlos como parametro seria abrir la misma puerta que la
 * ADR-002 cierra en los repositorios --que el hogar llegue de fuera-- y ademas
 * daria pie a publicar un evento con el hogar de otro.
 *
 * Los tipos son los del catalogo inicial de README 5.2.3. Las categorias no
 * estan, y no es un olvido: crear o retirar una categoria no publica nada porque
 * ningun modulo previsto reacciona a ello.
 */
@Service
class CoreEvents(
    private val bus: EventBus,
    private val tenantContext: TenantContext,
    private val clock: Clock,
) {

    /**
     * Un hogar queda **verificado y utilizable**, que no es cuando se inserta su
     * fila: un modulo activo no deberia sembrar datos para un hogar que quiza no
     * llegue a existir de verdad.
     */
    fun householdCreated(householdId: UUID) =
        publish("HouseholdCreated", householdId, emptyMap())

    fun articleCreated(articleId: UUID, name: String, categoryId: UUID, unit: String) =
        publish(
            "ArticleCreated",
            articleId,
            mapOf("name" to name, "categoryId" to categoryId, "unit" to unit),
        )

    fun locationCreated(locationId: UUID, name: String, type: String, parentLocationId: UUID?) =
        publish(
            "LocationCreated",
            locationId,
            mapOf("name" to name, "type" to type, "parentLocationId" to parentLocationId),
        )

    /** Incluida la primera existencia de un articulo en una ubicacion. */
    fun assetCreated(assetId: UUID, type: String, articleId: UUID?, categoryId: UUID?, location: EventLocation?) =
        publish(
            "AssetCreated",
            assetId,
            mapOf(
                "type" to type,
                "articleId" to articleId,
                "categoryId" to categoryId,
                "location" to location?.asPayload(),
            ),
        )

    /** Cambia la ubicacion de un asset, apunte a una `Location` o a otro asset. */
    fun assetMoved(assetId: UUID, from: EventLocation?, to: EventLocation?) =
        publish(
            "AssetMoved",
            assetId,
            mapOf("from" to from?.asPayload(), "to" to to?.asPayload()),
        )

    /**
     * Cambia el asset que contiene a otro, que es un cambio de composicion y no
     * solo de sitio. Acompana a [assetMoved] cuando el origen o el destino del
     * movimiento es un asset, porque son dos preguntas distintas: donde esta algo
     * y de que forma parte.
     */
    fun assetHierarchyChanged(assetId: UUID, previousParentAssetId: UUID?, parentAssetId: UUID?) =
        publish(
            "AssetHierarchyChanged",
            assetId,
            mapOf("previousParentAssetId" to previousParentAssetId, "parentAssetId" to parentAssetId),
        )

    /**
     * Cambia la cantidad de una existencia, por entrada, por ajuste, por fusion o
     * porque una baja da por perdido lo que quedaba.
     *
     * `mergedFromAssetId` es la mitad de la correlacion de `MergeStockItems`: la
     * otra es el `mergedIntoAssetId` del [assetDeactivated] del origen. Asi un
     * modulo que solo escuche cambios de cantidad no se pierde el del destino, y
     * uno que necesite saber que las existencias se mudaron --en vez de haberse
     * perdido-- lo distingue por la referencia cruzada.
     */
    fun assetQuantityChanged(
        assetId: UUID,
        previousQuantity: BigDecimal,
        quantity: BigDecimal,
        reason: QuantityChangeReason,
        mergedFromAssetId: UUID? = null,
    ) = publish(
        "AssetQuantityChanged",
        assetId,
        mapOf(
            "previousQuantity" to previousQuantity,
            "quantity" to quantity,
            "reason" to reason.name,
            "mergedFromAssetId" to mergedFromAssetId,
        ),
    )

    /** Se da de baja un asset, o una existencia se fusiona en otra. */
    fun assetDeactivated(assetId: UUID, mergedIntoAssetId: UUID? = null) =
        publish(
            "AssetDeactivated",
            assetId,
            mapOf("mergedIntoAssetId" to mergedIntoAssetId),
        )

    /**
     * **El unico evento de los ficheros**, y no lo publica un fichero sino un
     * documento.
     *
     * Un fichero recien subido y todavia sin adjuntar no significa nada para
     * nadie: lo que le interesa a otro modulo --el CMMS enlazando el manual en el
     * plan de mantenimiento que genera-- es que se adjunto. Un `FileUploaded`
     * obligaria a cada suscriptor a esperar un segundo evento para saber si
     * aquello llego a servir para algo (ver 5.7).
     *
     * Lleva el `fileId` cuando el documento se subio aqui, y nulo cuando es un
     * enlace externo: quien reaccione necesita saber por cual de los dos caminos
     * llegar al contenido.
     */
    fun documentAttached(documentId: UUID, assetId: UUID?, articleId: UUID?, type: String, fileId: UUID?) =
        publish(
            "DocumentAttached",
            documentId,
            mapOf("assetId" to assetId, "articleId" to articleId, "documentType" to type, "fileId" to fileId),
        )

    private fun publish(type: String, aggregateId: UUID, payload: Map<String, Any?>) {
        // Sin hogar no hay evento que valga: el handler corre despues del commit,
        // cuando ya no queda contexto del que deducirlo. Que esto salte es un
        // fallo de programacion --alguien publicando fuera de `runAs` o de una
        // peticion autenticada-- y conviene que se vea, no que se entregue un
        // evento que nadie puede situar.
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

/** Por que cambio la cantidad. Es lo que deja a Warehouse distinguir un consumo de una compra. */
enum class QuantityChangeReason { INTAKE, ADJUSTMENT, MERGE, DECOMMISSION }

/** La ubicacion polimorfica, tal y como viaja en el payload de un evento. */
data class EventLocation(val type: LocationKind, val id: UUID) {
    fun asPayload(): Map<String, Any?> = mapOf("type" to type.name, "id" to id)
}

enum class LocationKind { ASSET, LOCATION }
