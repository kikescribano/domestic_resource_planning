package com.drp.adapter.persistence

import com.drp.application.tenant.TenantContext
import jakarta.persistence.EntityManagerFactory
import org.springframework.orm.jpa.EntityManagerHolder
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * Fija `app.household_id` al abrir cada transaccion, a partir del hogar del
 * token (ADR-003).
 *
 * Es el punto donde la primera capa de aislamiento --la aplicacion-- le cuenta a
 * la segunda --PostgreSQL-- en nombre de quien se esta trabajando. Va en el
 * gestor de transacciones y no en un aspecto ni en un interceptor por una razon
 * concreta: `SET LOCAL` solo tiene efecto **dentro** de una transaccion y sobre
 * **la misma conexion** que la ejecuta. Ponerlo antes de abrirla lo dejaria en
 * una conexion cualquiera del pool, y ponerlo despues dejaria sin proteger a las
 * consultas que ya hubieran corrido.
 *
 * Se ajusta en **todas** las transacciones, tambien en las que no tienen hogar.
 * En esas se fija a cadena vacia a proposito en vez de no tocar nada: la
 * politica la convierte en NULL --de ahi el `nullif`-- y no deja pasar ninguna
 * fila. Confiar en que la conexion venga limpia del pool seria confiar en el
 * estado que dejo la peticion anterior.
 *
 * Se usa `set_config(..., true)` y no la sentencia `SET LOCAL` porque esta
 * ultima no admite parametros: habria que interpolar el identificador en el SQL,
 * que es la forma de abrir una inyeccion en el sitio mas caro posible.
 */
class TenantAwareTransactionManager(
    entityManagerFactory: EntityManagerFactory,
    private val tenantContext: TenantContext,
) : JpaTransactionManager(entityManagerFactory) {

    override fun doBegin(transaction: Any, definition: TransactionDefinition) {
        super.doBegin(transaction, definition)
        applyHousehold()
    }

    private fun applyHousehold() {
        // Falla ruidosamente en lugar de seguir sin contexto. Una transaccion sin
        // `app.household_id` no es una fuga --la politica no deja pasar nada--
        // pero convierte "no he podido fijar el contexto" en "no hay datos", y
        // desde arriba las dos cosas son indistinguibles: la peticion responde
        // una lista vacia y nadie se entera de que el mecanismo se ha roto.
        val factory = checkNotNull(entityManagerFactory) {
            "El gestor de transacciones necesita un EntityManagerFactory para fijar el contexto de inquilino"
        }
        val holder = checkNotNull(TransactionSynchronizationManager.getResource(factory) as? EntityManagerHolder) {
            "No hay EntityManager enlazado a la transaccion: no se puede fijar el contexto de inquilino"
        }

        holder.entityManager
            .createNativeQuery("SELECT set_config('app.household_id', ?1, true)")
            .setParameter(1, tenantContext.currentHousehold()?.toString() ?: "")
            .singleResult
    }
}
