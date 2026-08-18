package com.drp.platform.module

import com.drp.platform.tenant.TenantContext
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * «¿Esta activo este modulo para este hogar?»
 *
 * Es la pregunta que hacen las tres capas del gate --HTTP, event bus y, a traves
 * del contrato, el frontend-- y por eso vive en un puerto y no en cada una.
 */
interface ModuleActivation {

    /** Las claves activas del hogar del contexto. Vacio si no hay hogar. */
    fun activeKeys(): Set<String>

    fun isActive(moduleKey: String): Boolean = moduleKey in activeKeys()
}

@Component
class HouseholdModuleActivation(
    private val modules: HouseholdModuleRepository,
    private val tenantContext: TenantContext,
    private val cache: ModuleActivationCache,
) : ModuleActivation {

    /**
     * Sin hogar en el contexto no hay activacion que consultar, y la respuesta es
     * el conjunto vacio.
     *
     * Eso deja **todo modulo apagado** para una peticion sin autenticar, que es
     * la unica forma segura de fallar: lo contrario seria decidir el acceso a
     * partir de una consulta que la politica de RLS devolveria vacia de todos
     * modos, y confundir «no hay contexto» con «no lo tiene activo».
     */
    override fun activeKeys(): Set<String> {
        val household = tenantContext.currentHousehold() ?: return emptySet()

        cache.get(household)?.let { return it }

        val active = modules.activeKeys()
        cache.put(household, active)
        return active
    }
}

/**
 * La respuesta, resuelta **una vez por peticion**.
 *
 * Sin esto, una peticion a una ruta de modulo consultaria la tabla en el gate y
 * otra vez en cada handler que despertase, y la pantalla de modulos del frontend
 * pagaria una consulta por pantalla en lugar de una por sesion.
 *
 * **Va indexada por hogar y no a secas**, que es lo que la hace segura por
 * construccion: un hilo que volviera del pool con la entrada de la peticion
 * anterior no puede responder por el hogar equivocado, porque la clave no
 * coincide y se vuelve a consultar. La limpieza del [ModuleGateFilter] es
 * higiene --no dejar memoria colgando del hilo--, no la defensa.
 *
 * Fuera de una peticion --los recorridos periodicos, que van hogar a hogar-- la
 * entrada sobrevive al hogar en curso y se sustituye al pasar al siguiente, con
 * lo que cada hogar ve como mucho una consulta. Una activacion que cambie a mitad
 * de un recorrido no se ve hasta el siguiente, y eso es lo deseable: un barrido
 * que cambiara de opinion a la mitad seria peor de explicar.
 */
@Component
class ModuleActivationCache {

    private val entry = ThreadLocal<Entry?>()

    fun get(householdId: UUID): Set<String>? = entry.get()?.takeIf { it.householdId == householdId }?.keys

    fun put(householdId: UUID, keys: Set<String>) = entry.set(Entry(householdId, keys))

    /** Se llama al terminar la peticion, y tambien cuando alguien cambia la activacion. */
    fun clear(): Unit = entry.remove()

    private data class Entry(val householdId: UUID, val keys: Set<String>)
}
