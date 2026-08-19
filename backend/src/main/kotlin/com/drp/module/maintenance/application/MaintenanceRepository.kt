package com.drp.module.maintenance.application

import com.drp.module.maintenance.domain.DueStage
import com.drp.module.maintenance.domain.InterventionKind
import com.drp.module.maintenance.domain.MaintenanceIntervention
import com.drp.module.maintenance.domain.MaintenanceItem
import com.drp.module.maintenance.domain.MaintenanceMachine
import com.drp.module.maintenance.domain.MaintenancePlan
import com.drp.platform.page.Page
import com.drp.platform.page.Pagination
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * El almacen del modulo.
 *
 * **Ninguna operacion recibe el hogar**, exactamente igual que los repositorios
 * del core, los de plataforma y los de los tres modulos anteriores: el filtro lo
 * pone la politica de RLS a partir del `app.household_id` que fija el gestor de
 * transacciones. Un parametro `householdId` aqui seria la puerta por la que se
 * cuela leer el hogar de al lado, porque tarde o temprano alguien lo tomaria del
 * cuerpo de la peticion.
 *
 * Las tres ultimas leen **estado del core** --assets y documentos-- y no tablas
 * de este modulo. Es la direccion `modulo -> core` que la ADR-010 permite, en su
 * forma mas suave: aqui **solo se lee**. Este modulo no invoca ningun caso de uso
 * del core ni escribe en ninguna de sus tablas, al contrario que Warehouse, que
 * movia un contador, y que Compras, que crea existencias.
 */
interface MaintenanceRepository {

    // -----------------------------------------------------------------------
    // La ficha de una maquina
    // -----------------------------------------------------------------------

    /**
     * Abre la ficha de un asset **si es un `DURABLE` vivo de este hogar y no la
     * tenia ya**.
     *
     * Es la funcion que comparten la siembra y dos de los tres handlers, que es lo
     * que hace que «el modulo aun no ha sembrado» deje de ser un caso. Dos cosas
     * que la hacen segura sin ninguna comprobacion previa:
     *
     * - **`ON CONFLICT DO NOTHING` sobre el indice unico** de una ficha por
     *   maquina. Comprobar y despues insertar deja una ventana entre las dos cosas
     *   por la que caben dos entregas simultaneas del mismo evento; es la leccion
     *   que dejaron escrita los Hitos 3 y 4.
     * - **`INSERT … SELECT` sobre `assets`**, de modo que un identificador que no
     *   sea de un `DURABLE` vivo simplemente no escribe nada. Es lo que protege la
     *   rama de `DocumentAttached`, cuyo `assetId` puede ser nulo o de un
     *   consumible.
     *
     * @return cierto si abrio una ficha nueva.
     */
    fun openItem(assetId: UUID, at: Instant, by: UUID?): Boolean

    fun findItem(assetId: UUID): MaintenanceItem?

    fun saveItem(item: MaintenanceItem): MaintenanceItem

    /**
     * Deja [documentId] como manual de referencia de esa maquina, **abriendo la
     * ficha si falta**.
     *
     * @return cierto si lo dejo apuntado.
     */
    fun setManual(assetId: UUID, documentId: UUID, at: Instant, by: UUID?): Boolean

    /** Las maquinas que el modulo vigila: los `DURABLE` vivos, con lo que cuelga de ellos. */
    fun listMachines(query: String?, pagination: Pagination): Page<MaintenanceMachine>

    fun findMachine(assetId: UUID): MaintenanceMachine?

    // -----------------------------------------------------------------------
    // Los planes
    // -----------------------------------------------------------------------

    fun listPlans(filter: PlanFilter, pagination: Pagination): Page<MaintenancePlan>

    fun findPlan(planId: UUID): MaintenancePlan?

    fun livePlansOf(assetId: UUID): List<MaintenancePlan>

    /**
     * El plan vivo de esa maquina que se llama asi, **normalizando igual que el
     * indice unico**: `lower(immutable_unaccent(...))`.
     *
     * Va por consulta y no comparando en memoria con `equals(ignoreCase = true)`,
     * y no es un detalle: sin quitar los acentos, «Revision anual» no chocaria con
     * «Revisión anual» en la comprobacion previa **y si en el indice**, asi que el
     * duplicado saldria como un `500` en vez de como el `409` que el contrato
     * declara. Es la misma forma que usan las cuatro comprobaciones de nombre
     * unico del core y la de Proveedores.
     */
    fun findLivePlanByName(assetId: UUID, name: String): MaintenancePlan?

    fun savePlan(plan: MaintenancePlan): MaintenancePlan

    /**
     * Cancela los planes vivos de una maquina y devuelve cuantos cancelo.
     *
     * Lo invoca el handler de `AssetDeactivated`: lo que ya no esta en casa no se
     * revisa. El historico **no se toca**, que es lo que distingue cancelar de
     * borrar.
     */
    fun cancelPlansOf(assetId: UUID, at: Instant, by: UUID?): Int

    /**
     * Los planes vivos cuya fecha prevista cae dentro del horizonte.
     *
     * Acota a los candidatos y nada mas: **la fase exacta la decide el dominio**,
     * plan a plan, porque la antelacion es de cada uno y repetir esa regla en SQL
     * daria dos sitios donde vive lo mismo.
     */
    fun plansDueBy(horizon: LocalDate): List<DuePlan>

    /** Marca que un plan ya aviso de [stage] **para su fecha prevista de ahora**. */
    fun markPlanNotified(planId: UUID, stage: DueStage, forDate: LocalDate, at: Instant)

    // -----------------------------------------------------------------------
    // El historico
    // -----------------------------------------------------------------------

    fun listInterventions(assetId: UUID?, planId: UUID?, pagination: Pagination): Page<MaintenanceIntervention>

    fun recentInterventions(assetId: UUID?, planId: UUID?, limit: Int): List<MaintenanceIntervention>

    /** Se anade y no se toca: no hay `saveIntervention`, hay `record`. */
    fun record(intervention: MaintenanceIntervention): MaintenanceIntervention

    // -----------------------------------------------------------------------
    // Lo que se lee del core
    // -----------------------------------------------------------------------

    /** Cierto si ese asset es un `DURABLE` **vivo de este hogar**: lo unico que CMMS vigila. */
    fun isEligibleAsset(assetId: UUID): Boolean

    /** Los `DURABLE` vivos del hogar. Es lo que recorre la siembra. */
    fun eligibleAssetIds(): List<UUID>

    /** El nombre del asset, resuelto del articulo cuando lo tiene. Para el texto de un aviso. */
    fun assetName(assetId: UUID): String?

    /** Cierto si ese documento cuelga de ese asset **en este hogar**. */
    fun isDocumentOf(documentId: UUID, assetId: UUID): Boolean
}

/**
 * Los filtros de la lista de planes.
 *
 * `dueWithinDays` es la pregunta que este modulo existe para responder --«¿que
 * toca?»-- y por eso es filtro del servidor y no de la pantalla. Es ademas **lo
 * que el planificador de tareas preguntara el dia que exista**, en lugar de
 * copiarse el calendario: ver la ficha del modulo.
 */
data class PlanFilter(
    val assetId: UUID? = null,
    val query: String? = null,
    val dueWithinDays: Int? = null,
    val includeCancelled: Boolean = false,
)

/** Un plan candidato a avisar, con lo que hace falta para escribir el texto. */
data class DuePlan(val plan: MaintenancePlan, val assetName: String)
