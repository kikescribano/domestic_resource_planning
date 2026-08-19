package com.drp.module.maintenance.application

import com.drp.core.application.port.SessionClaims
import com.drp.core.application.usecase.Patch
import com.drp.core.application.usecase.orKeep
import com.drp.module.maintenance.domain.InterventionKind
import com.drp.module.maintenance.domain.MaintenanceIntervention
import com.drp.module.maintenance.domain.MaintenanceItem
import com.drp.module.maintenance.domain.MaintenanceMachine
import com.drp.module.maintenance.domain.MaintenancePlan
import com.drp.platform.directory.MasterData
import com.drp.platform.directory.MasterDataEntry
import com.drp.platform.error.BusinessRuleViolation
import com.drp.platform.error.ErrorCode
import com.drp.platform.error.ResourceNotFound
import com.drp.platform.error.ValidationFailure
import com.drp.platform.page.Page
import com.drp.platform.page.Pagination
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Las once operaciones del modulo.
 *
 * **La frontera que este fichero cumple sin decirlo en cada clase:** ninguna de
 * ellas asigna trabajo a nadie. No hay `assignTo`, no hay «marcar hecho por», no
 * hay una fecha en la agenda de una persona. Lo que hay es **cuando toca** --una
 * regla sobre una maquina-- y **que se hizo** --un apunte en un libro--. El
 * **quien lo hace** es del planificador de tareas, que no existe todavia, y esta
 * es la unica frontera de la fase escrita sin el otro lado delante.
 *
 * **Y la otra, contra el core: aqui solo se lee.** Ninguna de estas clases invoca
 * un caso de uso del core ni escribe en sus tablas, al contrario que Warehouse
 * --que movia un contador-- y que Compras --que crea existencias--. Por eso
 * ninguna tiene la excepcion de aquellas dos: **todas abren su transaccion**, y no
 * hay ninguna que tenga que renunciar a ella para que el `AFTER_COMMIT` de otro
 * dispare.
 */

/**
 * La clave del modulo que posee el dato maestro que este lee.
 *
 * **Es una cadena y no `SuppliersModule.KEY`, y eso es exactamente el punto.**
 * ArchUnit falla la construccion si un modulo referencia a otro, asi que lo unico
 * que CMMS conoce de Proveedores es su clave --el mismo identificador que escribe
 * un administrador al encenderlo-- y lo que el puerto de plataforma le quiera
 * contar. Si Proveedores desapareciera del despliegue, esto no dejaria de
 * compilar: dejaria de encontrar directorio, que es un caso ya contemplado.
 */
private const val SUPPLIERS = "SUPPLIERS"

/** Cuantas entradas devuelve el selector de a quien se llama. */
private const val SUPPLIER_CHOICES = 50

/** Un plan con lo que hace falta para pintarlo: su maquina, su servicio tecnico y su historia. */
data class MaintenancePlanDetail(
    val plan: MaintenancePlan,
    val assetName: String,
    /** Resuelto por el puerto **al leer**, no copiado. Nulo si no hay, si no existe o si Proveedores esta apagado. */
    val supplier: MasterDataEntry?,
    val interventions: List<MaintenanceIntervention>,
)

/** La ficha de una maquina: lo que cuelga de ella y no de ninguno de sus planes. */
data class MaintenanceMachineDetail(
    val machine: MaintenanceMachine,
    val plans: List<MaintenancePlan>,
    val interventions: List<MaintenanceIntervention>,
)

data class PlanCommand(
    val assetId: UUID,
    val name: String,
    val intervalMonths: Int,
    val leadDays: Int?,
    val nextDueOn: LocalDate,
    val supplierId: UUID?,
    val notes: String?,
)

data class PlanPatch(
    val name: Patch<String> = Patch.Absent,
    val intervalMonths: Patch<Int> = Patch.Absent,
    val leadDays: Patch<Int> = Patch.Absent,
    val nextDueOn: Patch<LocalDate> = Patch.Absent,
    val supplierId: Patch<UUID?> = Patch.Absent,
    val notes: Patch<String?> = Patch.Absent,
)

data class MachinePatch(
    val manualDocumentId: Patch<UUID?> = Patch.Absent,
    val notes: Patch<String?> = Patch.Absent,
)

data class InterventionCommand(
    val assetId: UUID,
    val planId: UUID?,
    val kind: InterventionKind,
    val performedOn: LocalDate,
    val summary: String,
    val supplierId: UUID?,
    val notes: String?,
)

// ---------------------------------------------------------------------------
// Las maquinas
// ---------------------------------------------------------------------------

@Service
class ListMaintenanceMachines(private val maintenance: MaintenanceRepository) {

    @Transactional(readOnly = true)
    fun handle(query: String?, pagination: Pagination): Page<MaintenanceMachine> =
        maintenance.listMachines(query?.trim()?.takeIf { it.isNotEmpty() }, pagination)
}

@Service
class GetMaintenanceMachine(private val maintenance: MaintenanceRepository) {

    @Transactional(readOnly = true)
    fun handle(assetId: UUID): MaintenanceMachineDetail {
        val machine = maintenance.findMachine(assetId)
            ?: throw ResourceNotFound("Esa máquina no está en mantenimiento")

        return MaintenanceMachineDetail(
            machine = machine,
            plans = maintenance.livePlansOf(assetId),
            interventions = maintenance.recentInterventions(assetId, planId = null, limit = RECENT),
        )
    }

    private companion object {
        /** Las ultimas diez: la ficha es un resumen, y el historico entero tiene su operacion. */
        const val RECENT = 10
    }
}

/**
 * La nota de una maquina y **cual de sus documentos es su manual**.
 *
 * Lo segundo existe por dos motivos, y el primero se olvida: el handler de
 * `DocumentAttached` deja apuntado **el ultimo manual adjuntado**, y eso puede no
 * ser el bueno --alguien sube el manual de otro modelo--. Sin esta operacion, esa
 * eleccion no tendria correccion posible desde la pantalla.
 */
@Service
class UpdateMaintenanceMachine(
    private val maintenance: MaintenanceRepository,
    private val clock: Clock,
) {

    @Transactional
    fun handle(session: SessionClaims, assetId: UUID, patch: MachinePatch): MaintenanceMachineDetail {
        val item = maintenance.findItem(assetId)
            ?: throw ResourceNotFound("Esa máquina no está en mantenimiento")

        val manual = patch.manualDocumentId.orKeep(item.manualDocumentId)
        // Que el documento sea de **esta** maquina y no de otra: el core responde
        // igual a lo que no existe y a lo que es de otro hogar, asi que esta
        // comprobacion es la que impide colgar de la caldera la factura del coche.
        if (manual != null && manual != item.manualDocumentId && !maintenance.isDocumentOf(manual, assetId)) {
            throw ValidationFailure(mapOf("manualDocumentId" to "ese documento no es de esta máquina"))
        }

        val now = clock.instant()
        maintenance.saveItem(
            item.copy(
                manualDocumentId = manual,
                notes = patch.notes.orKeep(item.notes)?.trim()?.takeIf { it.isNotEmpty() },
                updatedAt = now,
                updatedBy = session.memberId,
            ),
        )

        return MaintenanceMachineDetail(
            machine = maintenance.findMachine(assetId) ?: throw ResourceNotFound("Esa máquina no está en mantenimiento"),
            plans = maintenance.livePlansOf(assetId),
            interventions = maintenance.recentInterventions(assetId, planId = null, limit = 10),
        )
    }
}

// ---------------------------------------------------------------------------
// Los planes
// ---------------------------------------------------------------------------

@Service
class ListMaintenancePlans(private val maintenance: MaintenanceRepository) {

    @Transactional(readOnly = true)
    fun handle(filter: PlanFilter, pagination: Pagination): Page<MaintenancePlan> =
        maintenance.listPlans(filter.copy(query = filter.query?.trim()?.takeIf { it.isNotEmpty() }), pagination)
}

@Service
class GetMaintenancePlan(
    private val maintenance: MaintenanceRepository,
    private val masterData: MasterData,
) {

    @Transactional(readOnly = true)
    fun handle(planId: UUID): MaintenancePlanDetail {
        val plan = maintenance.findPlan(planId) ?: throw ResourceNotFound("Ese plan no existe")

        return MaintenancePlanDetail(
            plan = plan,
            assetName = maintenance.assetName(plan.assetId).orEmpty(),
            // **Aqui se ejercita la garantia que Proveedores declaro por
            // adelantado y para este caso**: un contacto retirado sigue siendo
            // legible por su identificador, porque un plan que apunte a quien ya
            // no se llama tiene que poder decir a quien apuntaba. Y con Proveedores
            // apagado esto responde nulo, sin ninguna rama aqui: la degradacion la
            // pone plataforma.
            supplier = plan.supplierId?.let { masterData.find(SUPPLIERS, it) },
            interventions = maintenance.recentInterventions(plan.assetId, planId, limit = 10),
        )
    }
}

@Service
class CreateMaintenancePlan(
    private val maintenance: MaintenanceRepository,
    private val masterData: MasterData,
    private val clock: Clock,
) {

    @Transactional
    fun handle(session: SessionClaims, command: PlanCommand): MaintenancePlan {
        requireEligible(maintenance, command.assetId)

        val name = command.name.trim()
        if (name.isEmpty()) throw ValidationFailure(mapOf("name" to "un plan necesita nombre"))

        val plan = MaintenancePlan(
            id = UUID.randomUUID(),
            assetId = command.assetId,
            name = name,
            intervalMonths = command.intervalMonths,
            leadDays = command.leadDays ?: DEFAULT_LEAD_DAYS,
            nextDueOn = command.nextDueOn,
            lastPerformedOn = null,
            supplierId = command.supplierId?.also { requireReadableSupplier(masterData, it) },
            notes = command.notes?.trim()?.takeIf { it.isNotEmpty() },
            notifiedStage = null,
            notifiedFor = null,
            cancelledAt = null,
            createdAt = clock.instant(),
            updatedAt = clock.instant(),
            createdBy = session.memberId,
            updatedBy = session.memberId,
        )
        requireShape(plan)

        // La comprobacion previa da el mensaje que el contrato declara; el indice
        // unico parcial cierra la carrera entre dos peticiones simultaneas, que
        // ninguna comprobacion previa puede cerrar. Las dos hacen falta, y es la
        // regla del core y no una duplicacion.
        requireNameFree(maintenance, plan)

        return maintenance.savePlan(plan)
    }

    /**
     * La antelacion por omision: **quince días**.
     *
     * Vive en el codigo y no en una tabla de configuracion, igual que los siete
     * dias de Warehouse, y por eso las dos comparten decision abierta: dos tablas
     * de configuracion de dos modulos para lo mismo serian la senal de que lo que
     * falta es una de plataforma.
     */
    private companion object {
        const val DEFAULT_LEAD_DAYS = 15
    }
}

@Service
class UpdateMaintenancePlan(
    private val maintenance: MaintenanceRepository,
    private val masterData: MasterData,
    private val clock: Clock,
) {

    @Transactional
    fun handle(session: SessionClaims, planId: UUID, patch: PlanPatch): MaintenancePlan {
        val current = maintenance.findPlan(planId) ?: throw ResourceNotFound("Ese plan no existe")
        if (!current.isLive) {
            throw BusinessRuleViolation(ErrorCode.MAINTENANCE_PLAN_CANCELLED, "Ese plan está cancelado")
        }

        val supplierId = patch.supplierId.orKeep(current.supplierId)
        if (patch.supplierId is Patch.Set && supplierId != null) requireReadableSupplier(masterData, supplierId)

        val name = patch.name.orKeep(current.name).trim()
        if (name.isEmpty()) throw ValidationFailure(mapOf("name" to "un plan necesita nombre"))

        val updated = current.copy(
            name = name,
            intervalMonths = patch.intervalMonths.orKeep(current.intervalMonths),
            leadDays = patch.leadDays.orKeep(current.leadDays),
            nextDueOn = patch.nextDueOn.orKeep(current.nextDueOn),
            supplierId = supplierId,
            notes = patch.notes.orKeep(current.notes)?.trim()?.takeIf { it.isNotEmpty() },
            updatedAt = clock.instant(),
            updatedBy = session.memberId,
        )
        requireShape(updated)
        if (patch.name is Patch.Set) requireNameFree(maintenance, updated)

        // **El aviso se rearma solo.** No hay ninguna linea aqui que borre
        // `notifiedStage`: al mover la fecha, `notifiedFor` deja de coincidir con
        // `nextDueOn` y el plan vuelve a poder avisar. Es exactamente lo que esa
        // segunda columna existe para conseguir, y el motivo de que no sea una
        // marca suelta que cada camino tenga que acordarse de limpiar.
        return maintenance.savePlan(updated)
    }
}

@Service
class CancelMaintenancePlan(
    private val maintenance: MaintenanceRepository,
    private val clock: Clock,
) {

    @Transactional
    fun handle(session: SessionClaims, planId: UUID) {
        val plan = maintenance.findPlan(planId) ?: throw ResourceNotFound("Ese plan no existe")
        // Cancelar dos veces no es un error, igual que retirar un contacto o
        // descartar una linea de la lista: el resultado que el cliente pidio ya se
        // cumple, y un 409 solo le obligaria a distinguir dos casos iguales.
        if (!plan.isLive) return

        val now = clock.instant()
        maintenance.savePlan(plan.copy(cancelledAt = now, updatedAt = now, updatedBy = session.memberId))
    }
}

// ---------------------------------------------------------------------------
// El historico
// ---------------------------------------------------------------------------

@Service
class ListMaintenanceInterventions(private val maintenance: MaintenanceRepository) {

    @Transactional(readOnly = true)
    fun handle(assetId: UUID?, planId: UUID?, pagination: Pagination): Page<MaintenanceIntervention> =
        maintenance.listInterventions(assetId, planId, pagination)
}

/**
 * **Lo que rearma el ciclo**, y la operacion por la que existe este modulo.
 *
 * Warehouse rearmaba su aviso reponiendo por encima del minimo, que es un hecho
 * que ocurre solo; aqui el rearme es **un gesto de una persona**: haber hecho la
 * revision. De ahi que esta operacion haga dos cosas y no una: escribe el apunte
 * en el libro y, si cumple un plan, **le avanza la fecha**.
 *
 * La proxima se cuenta desde **lo que se hizo** y no desde la que tocaba: si la
 * revision anual se hizo con dos meses de retraso, la siguiente es un ano despues
 * de la real. Contarla desde la teorica arrastraria el retraso para siempre.
 */
@Service
class RegisterMaintenanceIntervention(
    private val maintenance: MaintenanceRepository,
    private val masterData: MasterData,
    private val clock: Clock,
) {

    @Transactional
    fun handle(session: SessionClaims, command: InterventionCommand): MaintenanceIntervention {
        requireEligible(maintenance, command.assetId)

        val summary = command.summary.trim()
        if (summary.isEmpty()) throw ValidationFailure(mapOf("summary" to "hay que decir qué se hizo"))

        val today = LocalDate.ofInstant(clock.instant(), clock.zone ?: ZoneId.systemDefault())

        val plan = command.planId?.let { planId ->
            val found = maintenance.findPlan(planId) ?: throw ResourceNotFound("Ese plan no existe")
            if (!found.isLive) {
                throw BusinessRuleViolation(ErrorCode.MAINTENANCE_PLAN_CANCELLED, "Ese plan está cancelado")
            }
            if (found.assetId != command.assetId) {
                throw ValidationFailure(mapOf("planId" to "ese plan no es de esta máquina"))
            }
            found
        }
        if (plan != null && command.kind != InterventionKind.PREVENTIVE) {
            throw ValidationFailure(mapOf("kind" to "una intervención correctiva no cumple ningún plan"))
        }

        // **El nombre se copia**, al reves que en el plan, porque una intervencion
        // es historia: que aquel dia vino aquel servicio tecnico siguio siendo
        // cierto aunque despues se retire o el hogar apague Proveedores. Con
        // Proveedores apagado no se puede copiar nada, asi que se rechaza — que es
        // preferible a guardar un identificador sin nombre que despues no se pueda
        // pintar.
        val supplier = command.supplierId?.let { readableSupplier(masterData, it) }

        val intervention = MaintenanceIntervention(
            id = UUID.randomUUID(),
            assetId = command.assetId,
            planId = plan?.id,
            kind = command.kind,
            performedOn = command.performedOn,
            summary = summary,
            supplierId = supplier?.id,
            supplierName = supplier?.name,
            notes = command.notes?.trim()?.takeIf { it.isNotEmpty() },
            createdAt = clock.instant(),
            createdBy = session.memberId,
        )
        if (!intervention.isInThePast(today)) {
            throw ValidationFailure(mapOf("performedOn" to "una intervención no puede ser del futuro"))
        }

        val recorded = maintenance.record(intervention)

        // Y aqui la fecha avanza. El aviso **no se limpia a mano**: `notifiedFor`
        // deja de coincidir con la fecha nueva y el plan vuelve a poder avisar
        // solo.
        if (plan != null) {
            maintenance.savePlan(
                plan.performedOn(command.performedOn).copy(
                    updatedAt = clock.instant(),
                    updatedBy = session.memberId,
                ),
            )
        }

        return recorded
    }
}

// ---------------------------------------------------------------------------
// A quien se llama
// ---------------------------------------------------------------------------

/**
 * Dónde buscar servicio tecnico, leido de Proveedores **por el puerto de
 * plataforma**.
 *
 * **Cuelga del prefijo de CMMS y no del de Proveedores**, que es la misma decision
 * que tomo Compras y por el mismo motivo: un hogar con Mantenimiento encendido y
 * Proveedores apagado recibe `200` con una lista vacia en lugar del `403` que le
 * daria la ruta del otro modulo. Que la degradacion sea del servidor y no del
 * cliente es lo que impide que cada consumidor futuro tenga que acordarse de ella.
 *
 * **Y no filtra por categoria**, que es la decision que este hito tenia que tomar
 * al ser el segundo consumidor del puerto. Filtrar seria la funcionalidad
 * equivocada --de las catorce categorias cerradas de Proveedores, casi todas son
 * servicios tecnicos: fontanero, electricista, calderas, electrodomesticos,
 * carpinteria, cerrajeria, vehiculo…-- y recortar la lista escondería justo al
 * contacto que hace falta. Lo que si hace falta --distinguirlos de un vistazo-- ya
 * cabe en el `detail` que el puerto entrega, y el rotulo en castellano lo pone el
 * cliente.
 */
@Service
class ListMaintenanceSuppliers(private val masterData: MasterData) {

    fun handle(query: String?): List<MasterDataEntry> =
        masterData.search(SUPPLIERS, query?.trim()?.takeIf { it.isNotEmpty() }, SUPPLIER_CHOICES)
}

// ---------------------------------------------------------------------------
// Apoyo
// ---------------------------------------------------------------------------

private fun requireEligible(maintenance: MaintenanceRepository, assetId: UUID) {
    if (!maintenance.isEligibleAsset(assetId)) {
        throw BusinessRuleViolation(
            ErrorCode.MAINTENANCE_ASSET_NOT_ELIGIBLE,
            "Mantenimiento solo vigila cosas duraderas que sigan en casa",
        )
    }
}

private fun requireShape(plan: MaintenancePlan) {
    if (plan.intervalMonths !in 1..MAX_INTERVAL_MONTHS) {
        throw ValidationFailure(mapOf("intervalMonths" to "el periodo va de 1 a $MAX_INTERVAL_MONTHS meses"))
    }
    if (plan.leadDays !in 0..MAX_LEAD_DAYS) {
        throw ValidationFailure(mapOf("leadDays" to "la antelación va de 0 a $MAX_LEAD_DAYS días"))
    }
    if (!plan.leadFitsInterval()) {
        throw BusinessRuleViolation(
            ErrorCode.MAINTENANCE_LEAD_EXCEEDS_INTERVAL,
            "Avisar con más antelación que el propio periodo deja el plan siempre a punto de tocar",
        )
    }
}

/**
 * **Normaliza igual que el indice unico**, que es lo que hace que un duplicado
 * responda el `409` que el contrato declara en vez de un `500`. La comprobacion
 * previa da el mensaje y la restriccion cierra la carrera entre dos peticiones
 * simultaneas: las dos hacen falta, y son la regla del core y no una duplicacion.
 */
private fun requireNameFree(maintenance: MaintenanceRepository, plan: MaintenancePlan) {
    val clash = maintenance.findLivePlanByName(plan.assetId, plan.name)
    if (clash != null && clash.id != plan.id) {
        throw BusinessRuleViolation(
            ErrorCode.MAINTENANCE_PLAN_DUPLICATE,
            "Esa máquina ya tiene un plan vivo con ese nombre",
        )
    }
}

/**
 * **No distingue «ese contacto no existe» de «Proveedores está apagado»**, y eso
 * es lo correcto: es la misma regla con la que el core responde igual a lo que no
 * existe y a lo que es de otro hogar, porque distinguirlo convierte cualquier
 * identificador en un oraculo — aqui, sobre que modulos tiene encendidos el hogar.
 */
private fun readableSupplier(masterData: MasterData, supplierId: UUID): MasterDataEntry =
    masterData.find(SUPPLIERS, supplierId) ?: throw BusinessRuleViolation(
        ErrorCode.MAINTENANCE_SUPPLIER_UNKNOWN,
        "No se puede leer ese servicio técnico",
    )

private fun requireReadableSupplier(masterData: MasterData, supplierId: UUID) {
    readableSupplier(masterData, supplierId)
}

private const val MAX_INTERVAL_MONTHS = 120
private const val MAX_LEAD_DAYS = 365
