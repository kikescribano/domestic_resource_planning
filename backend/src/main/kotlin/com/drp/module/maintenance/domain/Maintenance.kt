package com.drp.module.maintenance.domain

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * El dominio del modulo Mantenimiento: **que hay que revisar, cada cuanto, cuando
 * toca la proxima vez y que se hizo la ultima**.
 *
 * La ficha esta en `docs/backend/modules/maintenance.md` y se escribio antes que
 * esto. Lo que hay que tener presente al leer este fichero es la frontera que
 * declara, porque es la unica de la fase escrita **sin el otro lado delante**:
 *
 * **De CMMS es el CUANDO; del planificador de tareas, el QUIEN LO HACE.** Un plan
 * es una **regla** --«la caldera se revisa cada doce meses»--: no tiene
 * responsable, no tiene dia, no se completa y no desaparece cuando alguien la
 * atiende. Una tarea es un **encargo** --«Kike revisa la caldera el jueves»--:
 * tiene responsable, tiene dia y se completa una vez.
 *
 * De ahi las dos cosas que **no** hay en este fichero, y que son lo que impide
 * que la frontera se difumine mientras el otro lado no exista: ninguna clase
 * lleva un responsable, y no hay ninguna que represente una ocurrencia futura.
 * Lo que hay es [MaintenancePlan.nextDueOn], **una sola fecha por plan**.
 */

/**
 * La ficha del modulo sobre una maquina del core: su entrada en el radar de CMMS.
 *
 * **No la crea nadie a mano.** La abren la siembra y dos de los tres handlers,
 * compartiendo funcion, y es lo que hace que «el modulo aun no ha sembrado» deje
 * de ser un caso --la regla que el Hito 3 dejo escrita.
 *
 * Es ademas la respuesta a la pregunta que la definicion no contestaba. El
 * catalogo de eventos (README 5.2.3) decia que CMMS «genera un plan de
 * mantenimiento por defecto» al darse de alta un asset, y eso no se sostiene:
 * **por defecto ¿de que?** Una caldera pide revision anual y una silla no pide
 * nada, y el core no modela de que clase es cada maquina. Asi que lo que se abre
 * es esto --una por maquina-- y el plan lo pone quien sabe si su caldera es de
 * gas.
 *
 * @param manualDocumentId **cual** de los documentos de esa maquina es el manual
 *   que hay que tener a mano al revisarla. Es una decision del modulo y no del
 *   core: el core sabe que un documento es de tipo `MANUAL`, no cual de los tres
 *   adjuntos es el bueno. Es un **puntero**, no una copia.
 */
data class MaintenanceItem(
    val id: UUID,
    val assetId: UUID,
    val manualDocumentId: UUID?,
    val notes: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val createdBy: UUID?,
    val updatedBy: UUID?,
)

/**
 * Una **regla recurrente** sobre una maquina.
 *
 * @param intervalMonths cada cuantos meses toca. **En meses y no en dias**, y no
 *   es comodidad: `plusMonths` conserva el dia del mes, mientras que una revision
 *   «anual» de 365 dias se desplaza un dia cada ano bisiesto y a los veinte anos
 *   cae en una fecha que nadie eligio.
 * @param leadDays con cuanta antelacion avisar. Cero es legitimo --«avisame el dia
 *   que toque»--; lo que no cabe es una antelacion mas larga que el propio
 *   periodo, que dejaria el plan permanentemente a punto de tocar.
 * @param nextDueOn **cuando toca la proxima**. Se guarda y no se calcula al leer,
 *   porque de ella cuelga el estado del aviso: un valor derivado al vuelo no tiene
 *   un momento en el que cambiar, y sin ese momento no hay donde rearmar nada.
 * @param supplierId el servicio tecnico, **sin su nombre al lado**. Al reves que
 *   en una intervencion, y es la misma regla aplicada dos veces: un plan es una
 *   regla **viva** y tiene que decir el nombre de hoy --a ese senor es a quien hay
 *   que llamar el mes que viene--, mientras que una intervencion es historia y se
 *   queda con el nombre de aquel dia. Se resuelve al leer, por el puerto de
 *   plataforma.
 * @param notifiedStage en que fase aviso, y **[notifiedFor] es la fecha a la que
 *   esa fase se refiere**. Los dos juntos son la respuesta a «de que cuelga un
 *   aviso que se rearma»: de la **fecha prevista** y no del plan. Ver
 *   [hasAnnounced].
 */
data class MaintenancePlan(
    val id: UUID,
    val assetId: UUID,
    val name: String,
    val intervalMonths: Int,
    val leadDays: Int,
    val nextDueOn: LocalDate,
    val lastPerformedOn: LocalDate?,
    val supplierId: UUID?,
    val notes: String?,
    val notifiedStage: DueStage?,
    val notifiedFor: LocalDate?,
    val cancelledAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val createdBy: UUID?,
    val updatedBy: UUID?,
) {
    /** Vivo: se vigila, admite intervenciones y cuenta para el nombre unico. */
    val isLive: Boolean get() = cancelledAt == null

    /**
     * **La antelacion tiene que caber en el periodo.** Un plan que avisa con mas
     * dias de los que dura su ciclo esta siempre «a punto de tocar», que es tanto
     * como no avisar nunca.
     *
     * Se compara contra el periodo en dias medido sobre el calendario real
     * --desde hoy hasta hoy mas el intervalo-- y no contra `meses * 30`, para que
     * un plan trimestral de febrero no valga distinto que uno de julio.
     *
     * Va como funcion y no como `init { require }`, que es la forma que usan
     * `Supplier.isReachable()` y `ShoppingListItem.identifiesOneThing()`: el
     * dominio dice **si algo cumple**, y que error lanzar es del caso de uso.
     */
    fun leadFitsInterval(): Boolean {
        val from = nextDueOn.minusMonths(intervalMonths.toLong())
        return leadDays < java.time.temporal.ChronoUnit.DAYS.between(from, nextDueOn)
    }

    /** En que fase esta **hoy**, o nulo si todavia no hay nada que decir. */
    fun stageOn(today: LocalDate): DueStage? = when {
        today.isAfter(nextDueOn) -> DueStage.OVERDUE
        !nextDueOn.isAfter(today.plusDays(leadDays.toLong())) -> DueStage.DUE_SOON
        else -> null
    }

    /**
     * Si hay que avisar de [stage], que es **solo cuando la fase avanza sobre la
     * misma fecha**.
     *
     * Las dos condiciones hacen dos trabajos distintos y las dos hacen falta:
     *
     * - `notifiedFor != nextDueOn` es **el rearme**. En cuanto la fecha avanza
     *   --por una intervencion, por cambiar el intervalo o por corregirla a
     *   mano-- la marca deja de valer sola, sin que ningun camino tenga que
     *   acordarse de limpiarla. Es la diferencia con una marca suelta, que el que
     *   se olvide de borrar deja un plan que **no vuelve a avisar nunca**.
     * - `notifiedStage.ordinal < stage.ordinal` es lo que evita las treinta noches
     *   seguidas sobre la misma fecha, y a la vez deja pasar la fase que
     *   **avanza**: de `DUE_SOON` a `OVERDUE` se avisa otra vez porque es una
     *   noticia nueva, y no darla dejaria al hogar con un aviso de hace tres
     *   semanas como unica advertencia.
     */
    fun shouldAnnounce(stage: DueStage): Boolean = isLive && !hasAnnounced(stage)

    private fun hasAnnounced(stage: DueStage): Boolean =
        notifiedFor == nextDueOn && notifiedStage != null && notifiedStage.ordinal >= stage.ordinal

    /**
     * El plan despues de una intervencion: **la fecha avanza y el aviso se
     * rearma**.
     *
     * La proxima se cuenta desde **lo que se hizo** y no desde la que tocaba: si
     * la revision anual se hizo con dos meses de retraso, la siguiente es un ano
     * despues de la real y no un ano despues de la que se incumplio. Contarla
     * desde la teorica arrastraria el retraso para siempre y acabaria pidiendo dos
     * revisiones el mismo ano.
     *
     * El rearme del aviso **no se escribe aqui**: sale solo, porque `notifiedFor`
     * deja de coincidir con la fecha nueva. Es exactamente lo que esa columna
     * existe para conseguir.
     */
    fun performedOn(date: LocalDate): MaintenancePlan = copy(
        lastPerformedOn = date,
        nextDueOn = date.plusMonths(intervalMonths.toLong()),
    )
}

/**
 * Las dos fases de una fecha prevista, **en orden**: el `ordinal` es lo que decide
 * si una fase avanza sobre la anterior, asi que reordenarlas cambia la regla. Es
 * la misma forma que el `ExpiryStage` de Warehouse.
 */
enum class DueStage { DUE_SOON, OVERDUE }

/**
 * Lo que se hizo, y cuando. **Es un libro y no un agregado**: se escribe y no se
 * toca.
 *
 * @param planId nulo en una correctiva, que no cumple ningun plan. Una preventiva
 *   puede colgar de uno o no: cambiar el filtro sin tener plan escrito es un caso
 *   normal, y exigirlo obligaria a inventarse un plan para poder apuntar algo que
 *   ya se ha hecho.
 * @param supplierName **el nombre de aquel dia**, copiado al registrar y no vuelto
 *   a mirar. Ver la nota de [MaintenancePlan.supplierId] para la otra mitad de la
 *   asimetria.
 */
data class MaintenanceIntervention(
    val id: UUID,
    val assetId: UUID,
    val planId: UUID?,
    val kind: InterventionKind,
    val performedOn: LocalDate,
    val summary: String,
    val supplierId: UUID?,
    val supplierName: String?,
    val notes: String?,
    val createdAt: Instant,
    val createdBy: UUID?,
) {
    /** **Una intervencion no es del futuro**: adelantaria la fecha sobre algo que no ha pasado. */
    fun isInThePast(today: LocalDate): Boolean = !performedOn.isAfter(today)
}

enum class InterventionKind {
    /** Cumple un plan --o al menos lo parece: cambiar el filtro tambien lo es sin plan escrito. */
    PREVENTIVE,

    /** Arregla algo que se ha roto. Nunca cuelga de un plan. */
    CORRECTIVE,
}

/**
 * Una maquina del hogar tal y como CMMS la ve, ya juntada.
 *
 * El nombre y el estado **son del core y se leen al construir esto**, no se copian
 * en ninguna tabla del modulo. Es la misma decision que el `StockItem` de
 * Warehouse toma con la cantidad, y por el mismo motivo: dos copias del mismo
 * nombre acaban diciendo cosas distintas el dia que alguien renombre el asset.
 */
data class MaintenanceMachine(
    val assetId: UUID,
    /** **Del core.** Ver la nota de arriba. */
    val assetName: String,
    val manualDocumentId: UUID?,
    val notes: String?,
    val planCount: Int,
    /** La mas proxima de sus fechas previstas, o nula si no tiene ningun plan vivo. */
    val nextDueOn: LocalDate?,
)
