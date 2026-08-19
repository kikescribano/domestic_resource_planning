package com.drp.module.maintenance.adapter.http

import com.drp.module.maintenance.application.MaintenanceMachineDetail
import com.drp.module.maintenance.application.MaintenancePlanDetail
import com.drp.module.maintenance.domain.InterventionKind
import com.drp.module.maintenance.domain.MaintenanceIntervention
import com.drp.module.maintenance.domain.MaintenanceMachine
import com.drp.module.maintenance.domain.MaintenancePlan
import com.drp.platform.directory.MasterDataEntry
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PositiveOrZero
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Las formas que declara `openapi.yaml` para este modulo.
 *
 * **El nombre de una maquina viaja aqui y no se guarda en ninguna tabla del
 * modulo**: sale de `assets` --o de su articulo-- al leer. Es la frontera de la
 * ficha vista desde el contrato: el cliente recibe un nombre porque solo hay uno,
 * no porque se hayan reconciliado dos.
 *
 * **Y el servicio tecnico viaja de dos formas distintas, que es la asimetria que
 * mas facil parece un descuido:**
 *
 * - En un **plan**, `supplier` se **resuelve al leer** por el puerto de
 *   plataforma, porque un plan es una regla viva y tiene que decir el nombre de
 *   hoy: a ese senor es a quien hay que llamar el mes que viene. Nulo si no hay,
 *   si ya no existe o si Proveedores esta apagado, sin que el cliente pueda --ni
 *   deba-- distinguir los tres casos.
 * - En una **intervencion**, `supplier` es el nombre **copiado aquel dia**, porque
 *   una intervencion es historia y aquello siguio siendo cierto.
 */

data class MaintenanceMachineResponse(
    val assetId: UUID,
    /** Del core. Ver la nota de arriba. */
    val name: String,
    val manualDocumentId: UUID?,
    val notes: String?,
    val planCount: Int,
    /** La mas proxima de sus fechas previstas, o nula si no tiene ningun plan vivo. */
    val nextDueOn: LocalDate?,
) {
    companion object {
        fun of(machine: MaintenanceMachine) = MaintenanceMachineResponse(
            assetId = machine.assetId,
            name = machine.assetName,
            manualDocumentId = machine.manualDocumentId,
            notes = machine.notes,
            planCount = machine.planCount,
            nextDueOn = machine.nextDueOn,
        )
    }
}

data class MaintenanceMachineDetailResponse(
    val machine: MaintenanceMachineResponse,
    val plans: List<MaintenancePlanResponse>,
    val interventions: List<MaintenanceInterventionResponse>,
) {
    companion object {
        fun of(detail: MaintenanceMachineDetail) = MaintenanceMachineDetailResponse(
            machine = MaintenanceMachineResponse.of(detail.machine),
            plans = detail.plans.map(MaintenancePlanResponse::of),
            interventions = detail.interventions.map(MaintenanceInterventionResponse::of),
        )
    }
}

data class MaintenancePlanResponse(
    val id: UUID,
    val assetId: UUID,
    val name: String,
    val intervalMonths: Int,
    val leadDays: Int,
    val nextDueOn: LocalDate,
    val lastPerformedOn: LocalDate?,
    val supplierId: UUID?,
    val notes: String?,
    val cancelledAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun of(plan: MaintenancePlan) = MaintenancePlanResponse(
            id = plan.id,
            assetId = plan.assetId,
            name = plan.name,
            intervalMonths = plan.intervalMonths,
            leadDays = plan.leadDays,
            nextDueOn = plan.nextDueOn,
            lastPerformedOn = plan.lastPerformedOn,
            supplierId = plan.supplierId,
            notes = plan.notes,
            cancelledAt = plan.cancelledAt,
            createdAt = plan.createdAt,
            updatedAt = plan.updatedAt,
        )
    }
}

/**
 * `notifiedStage` y `notifiedFor` **no salen en la respuesta**, y no es un olvido:
 * son el estado interno con el que el modulo se acuerda de no repetir un aviso, no
 * algo sobre lo que el cliente tenga que decidir nada. Sacarlos convertiria un
 * detalle de implementacion en contrato, y con el la libertad de cambiar como se
 * rearma el aviso.
 */
data class MaintenancePlanDetailResponse(
    val plan: MaintenancePlanResponse,
    val machineName: String,
    /** Resuelto al leer, no copiado. Ver la nota de la cabecera. */
    val supplier: MaintenanceSupplierResponse?,
    val interventions: List<MaintenanceInterventionResponse>,
) {
    companion object {
        fun of(detail: MaintenancePlanDetail) = MaintenancePlanDetailResponse(
            plan = MaintenancePlanResponse.of(detail.plan),
            machineName = detail.assetName,
            supplier = detail.supplier?.let(MaintenanceSupplierResponse::of),
            interventions = detail.interventions.map(MaintenanceInterventionResponse::of),
        )
    }
}

data class MaintenanceInterventionResponse(
    val id: UUID,
    val assetId: UUID,
    val planId: UUID?,
    val kind: InterventionKind,
    val performedOn: LocalDate,
    val summary: String,
    val supplierId: UUID?,
    /** El nombre de aquel dia. Ver la nota de la cabecera. */
    val supplier: String?,
    val notes: String?,
    val createdAt: Instant,
) {
    companion object {
        fun of(intervention: MaintenanceIntervention) = MaintenanceInterventionResponse(
            id = intervention.id,
            assetId = intervention.assetId,
            planId = intervention.planId,
            kind = intervention.kind,
            performedOn = intervention.performedOn,
            summary = intervention.summary,
            supplierId = intervention.supplierId,
            supplier = intervention.supplierName,
            notes = intervention.notes,
            createdAt = intervention.createdAt,
        )
    }
}

/**
 * A quien se puede llamar, con **lo minimo que el puerto de plataforma deja
 * pasar**: quien es y como se llama.
 *
 * `detail` trae el identificador de la categoria de servicio, no su rotulo, y es
 * lo que el cliente usa para **agrupar** el selector. Es tambien la razon de que
 * este hito **no ensanche el puerto**: lo que hacia falta --distinguir de un
 * vistazo a quien se llama-- ya cabe aqui, y filtrar por categoria en el servidor
 * escondería justo al contacto que hace falta.
 */
data class MaintenanceSupplierResponse(val id: UUID, val name: String, val detail: String?) {
    companion object {
        fun of(entry: MasterDataEntry) = MaintenanceSupplierResponse(entry.id, entry.name, entry.detail)
    }
}

// ---------------------------------------------------------------------------
// Lo que entra
// ---------------------------------------------------------------------------

/**
 * Un plan nuevo.
 *
 * **`leadDays` es opcional** y cae en los quince dias del modulo; el resto de las
 * reglas --que el periodo esta acotado y que la antelacion cabe dentro de el-- no
 * se validan aqui sino en el caso de uso, porque comparan dos campos entre si y
 * eso no lo expresa ninguna anotacion de Bean Validation sin escribir una propia.
 */
data class PlanInput(
    @field:NotNull(message = "un plan cuelga de una máquina")
    val assetId: UUID?,
    @field:NotNull(message = "un plan necesita nombre")
    val name: String?,
    @field:NotNull(message = "hay que decir cada cuántos meses")
    val intervalMonths: Int?,
    @field:PositiveOrZero(message = "la antelación no es negativa")
    val leadDays: Int?,
    @field:NotNull(message = "hay que decir cuándo toca la próxima")
    val nextDueOn: LocalDate?,
    val supplierId: UUID?,
    val notes: String?,
)

/**
 * Lo que se hizo.
 *
 * **`planId` es opcional a proposito**: una correctiva no cuelga de ningun plan, y
 * una preventiva puede no colgar tampoco --cambiar el filtro sin tener plan
 * escrito es un caso normal, y exigirlo obligaria a inventarse un plan para poder
 * apuntar algo que ya se ha hecho.
 */
data class InterventionInput(
    @field:NotNull(message = "una intervención es sobre una máquina")
    val assetId: UUID?,
    val planId: UUID?,
    @field:NotNull(message = "hay que decir si es preventiva o correctiva")
    val kind: InterventionKind?,
    @field:NotNull(message = "hay que decir cuándo se hizo")
    val performedOn: LocalDate?,
    @field:NotNull(message = "hay que decir qué se hizo")
    val summary: String?,
    val supplierId: UUID?,
    val notes: String?,
)
