package com.drp.platform.module

import java.time.Instant
import java.util.UUID

enum class ModuleStatus { ACTIVE, INACTIVE }

/**
 * Lo que un hogar decide sobre un modulo.
 *
 * **Que no haya fila significa inactivo.** Un hogar recien creado no siembra
 * catorce filas apagadas: la ausencia ya dice lo mismo, y sembrarlas obligaria a
 * rellenar hacia atras todos los hogares que ya existen cada vez que se anade un
 * modulo.
 *
 * La fila aparece la primera vez que alguien lo enciende y **ya no se borra**:
 * desactivar la deja en `INACTIVE`, que es lo que hace que reactivar devuelva los
 * datos tal cual en lugar de empezar de cero.
 */
data class HouseholdModule(
    val id: UUID,
    val key: String,
    val status: ModuleStatus,
    val activatedAt: Instant?,
    val deactivatedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val createdBy: UUID?,
    val updatedBy: UUID?,
) {
    val isActive: Boolean get() = status == ModuleStatus.ACTIVE
}

/**
 * El puerto de persistencia de la activacion.
 *
 * Como los del core, **no recibe `householdId`**: sale del `TenantContext`, y
 * aceptarlo como parametro seria abrir la puerta que la ADR-002 cierra.
 */
interface HouseholdModuleRepository {

    /**
     * Las claves activas del hogar actual, **en una transaccion propia**.
     *
     * El `REQUIRES_NEW` no es una precaucion generica: es la unica propagacion
     * que sirve cuando quien pregunta es un handler de evento. Un handler corre
     * `AFTER_COMMIT` con la transaccion del core todavia abierta pero ya cerrada
     * su `SET LOCAL app.household_id`, asi que unirse a ella devolveria **cero
     * filas** --y un modulo activo pareceria apagado--. Ver
     * [com.drp.platform.event.IdempotentEventHandler].
     */
    fun activeKeys(): Set<String>

    /** Todas las decisiones tomadas por el hogar actual, activas o no. */
    fun findAll(): List<HouseholdModule>

    fun find(moduleKey: String): HouseholdModule?

    fun save(module: HouseholdModule): HouseholdModule
}
