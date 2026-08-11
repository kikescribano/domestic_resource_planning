package com.drp.adapter.persistence

import com.drp.application.tenant.TenantContext
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * El hogar actual, atado al hilo que atiende la peticion.
 *
 * Es deliberadamente lo mismo que hace `SecurityContextHolder` de Spring
 * Security, y por el mismo motivo: el modelo de ejecucion es un hilo por
 * peticion, asi que un `ThreadLocal` alcanza sin obligar a arrastrar el hogar
 * como parametro por toda la aplicacion --que es justo el sitio donde uno se
 * olvida de pasarlo.
 *
 * Se limpia siempre en un `finally`. Un hilo devuelto al pool con el hogar de
 * otra peticion puesto seria una fuga entre inquilinos de las peores: silenciosa
 * y dependiente de la carga.
 */
@Component
class ThreadLocalTenantContext : TenantContext {

    private val currentHousehold = ThreadLocal<UUID?>()

    override fun currentHousehold(): UUID? = currentHousehold.get()

    override fun <T> runAs(householdId: UUID?, block: () -> T): T {
        val previous = currentHousehold.get()
        set(householdId)
        try {
            return block()
        } finally {
            set(previous)
        }
    }

    private fun set(householdId: UUID?) {
        if (householdId == null) currentHousehold.remove() else currentHousehold.set(householdId)
    }
}
