package com.drp.module.maintenance.adapter.http

import com.drp.core.adapter.http.JsonPatch
import com.drp.core.application.port.SessionClaims
import com.drp.module.maintenance.MaintenanceModule
import com.drp.module.maintenance.application.CancelMaintenancePlan
import com.drp.module.maintenance.application.CreateMaintenancePlan
import com.drp.module.maintenance.application.GetMaintenanceMachine
import com.drp.module.maintenance.application.GetMaintenancePlan
import com.drp.module.maintenance.application.InterventionCommand
import com.drp.module.maintenance.application.ListMaintenanceInterventions
import com.drp.module.maintenance.application.ListMaintenanceMachines
import com.drp.module.maintenance.application.ListMaintenancePlans
import com.drp.module.maintenance.application.ListMaintenanceSuppliers
import com.drp.module.maintenance.application.MachinePatch
import com.drp.module.maintenance.application.PlanCommand
import com.drp.module.maintenance.application.PlanFilter
import com.drp.module.maintenance.application.PlanPatch
import com.drp.module.maintenance.application.RegisterMaintenanceIntervention
import com.drp.module.maintenance.application.UpdateMaintenanceMachine
import com.drp.module.maintenance.application.UpdateMaintenancePlan
import com.drp.platform.page.PageResponse
import com.drp.platform.page.Pagination
import com.fasterxml.jackson.databind.JsonNode
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Las once operaciones del modulo, en HTTP.
 *
 * Tan fino como los del core y los de los tres modulos anteriores: traduce HTTP a
 * un caso de uso y su resultado a la forma que declara `openapi.yaml`, sin decidir
 * nada. **No recibe `householdId`**; sale del token.
 *
 * **Y no sabe nada del gate.** Todo lo que cuelga de este prefijo responde `403
 * MODULE_INACTIVE` cuando el hogar no tiene el modulo encendido, y eso lo pone un
 * filtro de plataforma sobre el prefijo que declara `MaintenanceModule`.
 *
 * Reutiliza `JsonPatch` del core --la lectura del cuerpo de un `PATCH` como arbol
 * JSON, que es la unica forma de distinguir «no menciones este campo» de «ponlo a
 * nulo»--. Aqui esa diferencia decide dos cosas reales: `{"supplierId": null}`
 * **quita el servicio tecnico** de un plan y `{"manualDocumentId": null}` **suelta
 * el manual** de una maquina, mientras que un cuerpo que no los nombre los
 * conserva.
 */
@RestController
@RequestMapping(MaintenanceModule.ROUTE_PREFIX)
class MaintenanceController(
    private val listMachines: ListMaintenanceMachines,
    private val getMachine: GetMaintenanceMachine,
    private val updateMachine: UpdateMaintenanceMachine,
    private val listPlans: ListMaintenancePlans,
    private val getPlan: GetMaintenancePlan,
    private val createPlan: CreateMaintenancePlan,
    private val updatePlan: UpdateMaintenancePlan,
    private val cancelPlan: CancelMaintenancePlan,
    private val listInterventions: ListMaintenanceInterventions,
    private val registerIntervention: RegisterMaintenanceIntervention,
    private val listSuppliers: ListMaintenanceSuppliers,
) {

    // -----------------------------------------------------------------------
    // Las maquinas
    // -----------------------------------------------------------------------

    @GetMapping("/machines")
    fun machines(
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): PageResponse<MaintenanceMachineResponse> = PageResponse.of(
        listMachines.handle(q, Pagination(page, size)),
        MaintenanceMachineResponse::of,
    )

    /** Se pide por el **identificador del asset** y no por el de la ficha: la ficha no la crea nadie. */
    @GetMapping("/machines/{assetId}")
    fun machine(@PathVariable assetId: UUID): MaintenanceMachineDetailResponse =
        MaintenanceMachineDetailResponse.of(getMachine.handle(assetId))

    @PatchMapping("/machines/{assetId}")
    fun updateMachine(
        @AuthenticationPrincipal session: SessionClaims,
        @PathVariable assetId: UUID,
        @RequestBody body: JsonNode,
    ): MaintenanceMachineDetailResponse {
        val patch = JsonPatch(body)
        return MaintenanceMachineDetailResponse.of(
            updateMachine.handle(
                session,
                assetId,
                MachinePatch(
                    manualDocumentId = patch.uuid("manualDocumentId"),
                    notes = patch.text("notes"),
                ),
            ),
        )
    }

    // -----------------------------------------------------------------------
    // Los planes
    // -----------------------------------------------------------------------

    /**
     * `dueWithinDays` es la pregunta que este modulo existe para responder --«¿que
     * toca?»-- y por eso es filtro del servidor y no de la pantalla. Es ademas **lo
     * que el planificador de tareas preguntara el dia que exista**, en lugar de
     * copiarse el calendario: ver la ficha del modulo.
     */
    @GetMapping("/plans")
    fun plans(
        @RequestParam(required = false) assetId: UUID?,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) dueWithinDays: Int?,
        @RequestParam(defaultValue = "false") includeCancelled: Boolean,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): PageResponse<MaintenancePlanResponse> = PageResponse.of(
        listPlans.handle(PlanFilter(assetId, q, dueWithinDays, includeCancelled), Pagination(page, size)),
        MaintenancePlanResponse::of,
    )

    @GetMapping("/plans/{id}")
    fun plan(@PathVariable id: UUID): MaintenancePlanDetailResponse =
        MaintenancePlanDetailResponse.of(getPlan.handle(id))

    @PostMapping("/plans")
    @ResponseStatus(HttpStatus.CREATED)
    fun createPlan(
        @AuthenticationPrincipal session: SessionClaims,
        @Valid @RequestBody input: PlanInput,
    ): MaintenancePlanResponse = MaintenancePlanResponse.of(
        createPlan.handle(
            session,
            PlanCommand(
                assetId = input.assetId!!,
                name = input.name!!,
                intervalMonths = input.intervalMonths!!,
                leadDays = input.leadDays,
                nextDueOn = input.nextDueOn!!,
                supplierId = input.supplierId,
                notes = input.notes,
            ),
        ),
    )

    /**
     * `intervalMonths` y `nextDueOn` van por los `required…`: se pueden cambiar y
     * no se pueden vaciar, porque un plan sin cada-cuanto o sin proxima fecha no es
     * un plan. `supplierId` si se puede vaciar --dejar de llamar a nadie es una
     * decision legitima-- y por eso va por `uuid`.
     */
    @PatchMapping("/plans/{id}")
    fun updatePlan(
        @AuthenticationPrincipal session: SessionClaims,
        @PathVariable id: UUID,
        @RequestBody body: JsonNode,
    ): MaintenancePlanResponse {
        val patch = JsonPatch(body)
        return MaintenancePlanResponse.of(
            updatePlan.handle(
                session,
                id,
                PlanPatch(
                    name = patch.requiredText("name"),
                    intervalMonths = patch.requiredInteger("intervalMonths"),
                    leadDays = patch.requiredInteger("leadDays"),
                    nextDueOn = patch.requiredDate("nextDueOn"),
                    supplierId = patch.uuid("supplierId"),
                    notes = patch.text("notes"),
                ),
            ),
        )
    }

    @DeleteMapping("/plans/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun cancelPlan(@AuthenticationPrincipal session: SessionClaims, @PathVariable id: UUID) =
        cancelPlan.handle(session, id)

    // -----------------------------------------------------------------------
    // El historico
    // -----------------------------------------------------------------------

    @GetMapping("/interventions")
    fun interventions(
        @RequestParam(required = false) assetId: UUID?,
        @RequestParam(required = false) planId: UUID?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): PageResponse<MaintenanceInterventionResponse> = PageResponse.of(
        listInterventions.handle(assetId, planId, Pagination(page, size)),
        MaintenanceInterventionResponse::of,
    )

    /**
     * **Lo que rearma el ciclo.** Responde `201` con lo que se acaba de apuntar; el
     * plan avanzado se lee aparte, que es lo que hace el cliente de todos modos al
     * refrescar la lista.
     */
    @PostMapping("/interventions")
    @ResponseStatus(HttpStatus.CREATED)
    fun registerIntervention(
        @AuthenticationPrincipal session: SessionClaims,
        @Valid @RequestBody input: InterventionInput,
    ): MaintenanceInterventionResponse = MaintenanceInterventionResponse.of(
        registerIntervention.handle(
            session,
            InterventionCommand(
                assetId = input.assetId!!,
                planId = input.planId,
                kind = input.kind!!,
                performedOn = input.performedOn!!,
                summary = input.summary!!,
                supplierId = input.supplierId,
                notes = input.notes,
            ),
        ),
    )

    // -----------------------------------------------------------------------
    // A quien se llama
    // -----------------------------------------------------------------------

    /**
     * **Cuelga de aqui y no de `/suppliers`**, que es la decision que Compras tomo
     * y esta operacion repite: un hogar con Mantenimiento encendido y Proveedores
     * apagado recibe `200` con la lista vacia en lugar del `403` que le daria la
     * ruta del otro modulo.
     */
    @GetMapping("/suppliers")
    fun suppliers(@RequestParam(required = false) q: String?): List<MaintenanceSupplierResponse> =
        listSuppliers.handle(q).map(MaintenanceSupplierResponse::of)
}
