package com.drp.platform.module

import com.drp.platform.event.DomainEvent
import com.drp.platform.event.IdempotentEventHandler
import com.drp.platform.tenant.TenantContext
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate

/**
 * La base de todo handler de evento **de un modulo**.
 *
 * Anade una sola cosa a [IdempotentEventHandler]: no hacer nada si el modulo
 * esta inactivo **para el hogar de ese evento**. Todo lo demas --correr despues
 * del commit, aislarse y descartar el evento ya atendido-- lo sigue resolviendo
 * la clase de abajo, y conviene leer su comentario antes de tocar nada aqui:
 * documenta tres cosas medidas que lo evidente contradice.
 *
 * **Tres decisiones sobre las que descansa esto, y ninguna es obvia:**
 *
 * **1. La comprobacion va dentro del handler, no en el bus.** Filtrar al
 * publicar exigiria que el bus supiera que modulo escucha cada evento, que es
 * exactamente lo que README 5.2 evita: el core publica sin saber quien escucha.
 * Y no serviria igual, porque un mismo evento puede tener detras un modulo
 * activo y otro apagado.
 *
 * **2. El hogar sale del evento, no del contexto.** Un handler corre
 * `AFTER_COMMIT` y **el hogar sigue en el `TenantContext`** --medido, no
 * supuesto-- pero eso vale para lo que nace de una peticion; lo que nace de un
 * recorrido periodico o de otro handler no lo garantiza. El unico sitio que
 * situa a un handler con certeza es el sobre del evento, que por eso lleva
 * `householdId`. Se vuelve a fijar con `runAs`, que ademas restaura al salir.
 *
 * **3. Todo lo que toque la base de datos va en una transaccion `REQUIRES_NEW`,
 * y la abre esta clase.** Es la regla de todo acceso a datos desde un
 * `AFTER_COMMIT` y aqui hay dos motivos, no uno. Unirse a la del core devolveria
 * **cero filas** --su `SET LOCAL app.household_id` ya no vale-- y un modulo
 * activo pareceria apagado sin un solo error. Y unirse tiene la otra mitad, peor:
 * un handler unido a la transaccion del core que falle la marca `rollbackOnly` y
 * **se lleva por delante el alta que origino el evento**, que es la unica forma
 * conocida de que un modulo tumbe al core.
 *
 * Se abre aqui y no en cada modulo justamente por eso: es una regla que solo se
 * incumple una vez y el sintoma --un modulo que no ve nada, o un alta del core
 * que desaparece-- no se parece a la causa. Lo que escriba dentro un modulo se
 * une a **esta** transaccion, que ya es la buena, asi que su codigo no tiene que
 * acordarse de nada. La comprobacion de activacion va antes y abre la suya, que
 * lleva declarada [HouseholdModuleRepository.activeKeys].
 */
abstract class ModuleEventHandler(
    private val moduleKey: String,
    handlerName: String,
    private val activation: ModuleActivation,
    private val tenantContext: TenantContext,
    transactionManager: PlatformTransactionManager,
) : IdempotentEventHandler(handlerName) {

    /**
     * Programatica y no `@Transactional`, y hace falta que sea asi: `receive` lo
     * invoca Spring sobre el proxy y de ahi baja a `handle` por dentro, asi que
     * una anotacion en este metodo seria una autoinvocacion y **no se aplicaria**.
     * El sintoma seria justo el que la clase existe para evitar.
     */
    private val inItsOwnTransaction = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    /**
     * `final` a proposito: un modulo que reimplementara esto se saltaria el gate
     * sin que nada fallara, y el sintoma --un modulo apagado que sigue
     * escribiendo-- no se parece a la causa.
     */
    final override fun handle(event: DomainEvent) {
        tenantContext.runAs(event.householdId) {
            if (!activation.isActive(moduleKey)) return@runAs
            inItsOwnTransaction.executeWithoutResult { handleActive(event) }
        }
    }

    /** Lo que hace el modulo cuando esta encendido para el hogar del evento. */
    protected abstract fun handleActive(event: DomainEvent)
}
