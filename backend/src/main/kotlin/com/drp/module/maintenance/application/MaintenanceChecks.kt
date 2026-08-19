package com.drp.module.maintenance.application

import com.drp.module.maintenance.MaintenanceModule
import com.drp.module.maintenance.domain.DueStage
import com.drp.platform.notice.NoticeDraft
import com.drp.platform.schedule.CheckOwner
import com.drp.platform.schedule.ScheduledCheck
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * La comprobacion periodica de CMMS: **que toca revisar y que se ha pasado**.
 *
 * Declara `CheckOwner.Module`, asi que el recorrido diario **solo entra en los
 * hogares que tengan Mantenimiento encendido**. Es la regla que la ADR-011 puso, y
 * este modulo es **el ejemplo con el que esa ADR se justifica** --«la revision en
 * CMMS»--, asi que conviene que se cumpla aqui de verdad.
 *
 * **Hereda el patron de Warehouse y le da una vuelta que alli no hacia falta.** La
 * regla es la misma: un aviso se levanta cuando la condicion empieza a ser cierta
 * y no vuelve a levantarse mientras siga siendolo, con el estado en las tablas del
 * modulo y no consultando `household_notices` --que obligaria a un modulo a leer
 * una tabla de plataforma y a reconocer sus propios avisos por el texto--. Lo
 * nuevo es que **una revision es periodica**: hecha la de este ano, la del que
 * viene tiene que volver a armarse.
 *
 * En Warehouse el rearme era reponer por encima del minimo, que es un hecho que
 * ocurre solo; aqui es **registrar la intervencion**, que es un gesto de una
 * persona. Y por eso el estado **cuelga de la proxima fecha prevista y no del
 * plan**: el plan lleva `notifiedStage` y `notifiedFor`, y un aviso solo se calla
 * cuando esa segunda columna coincide con `nextDueOn`. En cuanto la fecha avanza
 * --por una intervencion, por cambiar el intervalo o por corregirla a mano-- la
 * marca deja de valer sola, sin que ningun camino tenga que acordarse de
 * limpiarla.
 *
 * La alternativa era una sola marca que hubiera que borrar en cada uno de esos
 * caminos. Se descarta porque el que se olvidara dejaria un plan que **no vuelve a
 * avisar nunca** --el peor sintoma posible, porque solo se descubre el dia que la
 * caldera lleva dos anos sin revisar.
 *
 * **Una comprobacion y dos avisos**, y no dos comprobaciones: las dos fases salen
 * de la misma pregunta --«¿que planes tienen la fecha encima?»-- y separarlas
 * daria dos consultas casi identicas al mismo indice, una por noche y por hogar.
 */
@Component
class MaintenanceDueCheck(
    private val maintenance: MaintenanceRepository,
    private val clock: Clock,
) : ScheduledCheck {

    override val name: String = "MaintenanceDueCheck"
    override val owner: CheckOwner = CheckOwner.Module(MaintenanceModule.KEY)

    override fun check(): List<NoticeDraft> {
        val today = LocalDate.ofInstant(clock.instant(), clock.zone ?: ZoneId.systemDefault())

        // El horizonte se acota por la antelacion mas larga posible, y la fase
        // exacta la decide el dominio plan a plan: la antelacion es de cada uno y
        // la consulta no puede aplicar una ventana por fila sin repetir la regla en
        // SQL. Lo que se pasa aqui es el techo por encima del cual ninguna
        // antelacion configurable puede meter un plan en la ventana.
        return maintenance.plansDueBy(today.plusDays(MAX_LEAD_DAYS))
            .mapNotNull { due ->
                val stage = due.plan.stageOn(today) ?: return@mapNotNull null
                if (!due.plan.shouldAnnounce(stage)) return@mapNotNull null

                // Se marca **antes** de devolver el aviso, no despues: quien guarda
                // el aviso es `DailySweep`, en esta misma transaccion, asi que si
                // algo falla se deshacen las dos cosas juntas. Marcar despues
                // exigiria un segundo paso que nadie invoca.
                maintenance.markPlanNotified(due.plan.id, stage, due.plan.nextDueOn, clock.instant())
                draftFor(due.assetName, due.plan.name, due.plan.nextDueOn, stage)
            }
    }

    private fun draftFor(machine: String, plan: String, dueOn: LocalDate, stage: DueStage): NoticeDraft = when (stage) {
        DueStage.DUE_SOON -> NoticeDraft(
            kind = "MAINTENANCE_DUE_SOON",
            title = "Toca revisar: $machine",
            body = "«$plan» toca el $dueOn. Si hace falta llamar a alguien, vas a tiempo.",
        )

        DueStage.OVERDUE -> NoticeDraft(
            kind = "MAINTENANCE_OVERDUE",
            title = "$machine lleva sin revisar",
            body = "«$plan» tocaba el $dueOn y sigue sin registrarse. Apúntalo cuando esté hecho.",
        )
    }

    private companion object {
        /**
         * El horizonte de la consulta, que es el techo de la antelacion y no la de
         * nadie. Acotarlo al valor por omision dejaria fuera al plan que pidiera
         * avisar con noventa dias; no acotarlo traeria los planes del hogar entero
         * cada noche.
         *
         * **Solo acota por arriba, y es a proposito**: la consulta no pone suelo,
         * de modo que un plan que se paso hace tres meses sigue apareciendo. Un
         * `BETWEEN hoy AND horizonte` habria dejado los vencidos fuera --que son
         * justo los que hay que decir.
         */
        const val MAX_LEAD_DAYS = 365L
    }
}
