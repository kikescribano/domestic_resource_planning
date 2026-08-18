package com.drp.platform.schedule

import com.drp.platform.module.ModuleActivation
import com.drp.platform.notice.Notice
import com.drp.platform.notice.NoticeDigest
import com.drp.platform.notice.NoticeRepository
import com.drp.platform.tenant.HouseholdDirectory
import com.drp.platform.tenant.TenantContext
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.util.UUID

/**
 * El recorrido periodico por hogares (ADR-011).
 *
 * Es el hueco que la Fase 1 dejo abierto sin nombrarlo: `PurgeUnverifiedHouseholds`,
 * `PurgeUnusedFiles` y `MarkOverdueLoans` estaban escritos y probados, y **no los
 * invocaba nadie mas que las pruebas**. Desde aqui se ejecutan de verdad, y con
 * ellos las comprobaciones que cada modulo anada.
 *
 * **Un solo recorrido, y por eso los tres perdieron el suyo.** Cada uno pedia la
 * lista de hogares y abria transaccion por hogar; si plataforma pusiera el suyo
 * encima habria dos, y solo uno de los dos puede saltarse los hogares con el
 * modulo apagado. La direccion se invierte con [ScheduledCheck], que el core
 * implementa, exactamente como hace `ModuleSeeder` con la siembra: plataforma no
 * puede importar al core (ADR-010).
 *
 * **La forma es la que ya tenian: hogar a hogar, fijando `app.household_id` en
 * cada transaccion y nunca con `BYPASSRLS`.** La salida facil --dar `BYPASSRLS`
 * al usuario de la aplicacion-- desactivaria la segunda capa para **toda** la
 * aplicacion y no solo para el proceso, y por eso hay una prueba que afirma que
 * ese usuario no lo tiene.
 *
 * **Y se salta los hogares que no tengan activo el modulo que pide la
 * comprobacion.** La respuesta la da [ModuleActivation], que funciona igual
 * dentro de un `runAs` que dentro de una peticion; su cache va indexada por
 * hogar, que es lo que la hace segura en este camino --un solo hilo pasando por
 * todos los hogares seguidos-- y hay una prueba que lo mide en lugar de darlo por
 * bueno leyendo el comentario.
 */
@Component
class DailySweep(
    private val directory: HouseholdDirectory,
    private val tenantContext: TenantContext,
    private val transactions: TransactionTemplate,
    private val activation: ModuleActivation,
    private val notices: NoticeRepository,
    private val digest: NoticeDigest,
    private val clock: Clock,
    checks: List<ScheduledCheck>,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Ordenadas por nombre, para que dos pasadas hagan lo mismo en el mismo
     * orden. Sin esto el orden lo decide el de los beans, que no es contrato de
     * nada y cambia al anadir un modulo.
     */
    private val checks: List<ScheduledCheck> = checks.sortedBy { it.name }

    /**
     * La pasada diaria.
     *
     * `@Scheduled` va aqui y no en una envoltura a proposito: asi lo que invocan
     * las pruebas es **el mismo metodo** que invoca el programador, y no un
     * hermano que se le parece. Que el programador este encendido o no lo decide
     * `com.drp.config.SchedulingConfig`, que es quien lleva el `@EnableScheduling`.
     *
     * La lista de hogares se toma **una vez**: uno creado a mitad no entra hasta
     * la pasada siguiente, y uno borrado por el camino --`PurgeUnverifiedHouseholds`
     * lo hace-- deja de tener nada que mirar, porque el borrado se lleva en
     * cascada todo lo suyo. Las dos cosas son inofensivas porque toda
     * comprobacion es idempotente.
     */
    @Scheduled(cron = "\${drp.schedule.daily-cron}", zone = "\${drp.schedule.zone}")
    fun run(): SweepReport {
        val households = directory.allHouseholdIds()
        var raised = 0
        var delivered = 0

        for (householdId in households) {
            tenantContext.runAs(householdId) {
                raised += runChecks(householdId)
                if (deliverDigest(householdId)) delivered++
            }
        }

        log.info(
            "Recorrido diario: {} hogares, {} avisos nuevos, {} resúmenes entregados",
            households.size,
            raised,
            delivered,
        )
        return SweepReport(households.size, raised, delivered)
    }

    private fun runChecks(householdId: UUID): Int {
        var raised = 0

        for (check in checks) {
            if (!appliesTo(check)) continue

            // Cada comprobacion en su transaccion y con su red: que una falle no
            // puede dejar sin pasada a las demas ni a los hogares que vienen
            // detras. Sin este `catch`, un solo hogar con un dato raro apaga el
            // recorrido entero para toda la instalacion **y no avisa a nadie**,
            // que es la peor forma de fallar que tiene un proceso que nadie mira.
            raised += runCatching { transactions.execute { record(check) } ?: 0 }
                .onFailure { log.error("La comprobación {} falló en el hogar {}", check.name, householdId, it) }
                .getOrDefault(0)
        }

        return raised
    }

    /**
     * La unica pregunta que plataforma le hace al catalogo, y la razon de que
     * [CheckOwner] exista.
     *
     * Una comprobacion del core corre en todos los hogares --el core no se
     * apaga-- y una de modulo, solo donde ese modulo esta encendido. Sin la
     * declaracion no habria forma de distinguirlas, y la caducidad de Warehouse
     * se calcularia en hogares que no tienen Warehouse.
     */
    private fun appliesTo(check: ScheduledCheck): Boolean = when (val owner = check.owner) {
        is CheckOwner.Core -> true
        is CheckOwner.Module -> activation.isActive(owner.key)
    }

    /**
     * Sella lo que la comprobacion devuelve y lo guarda.
     *
     * El hogar no lo pone la comprobacion sino la politica de RLS, a partir del
     * `app.household_id` que esta transaccion acaba de fijar. Es lo que hace
     * imposible que una regla mal escrita avise al hogar de al lado.
     */
    private fun record(check: ScheduledCheck): Int {
        val now = clock.instant()

        val drafts = check.check()
        drafts.forEach { draft ->
            notices.save(
                Notice(
                    id = UUID.randomUUID(),
                    moduleKey = check.owner.moduleKey,
                    kind = draft.kind,
                    title = draft.title,
                    body = draft.body,
                    createdAt = now,
                    readAt = null,
                    readBy = null,
                    notifiedAt = null,
                ),
            )
        }

        return drafts.size
    }

    /**
     * El resumen va **detras de las comprobaciones del mismo hogar**, para que
     * incluya lo que se acaba de encontrar hoy en lugar de dejarlo para manana.
     *
     * Con su red por lo mismo que las comprobaciones: un fallo entregando no
     * puede dejar sin recorrido a los hogares que vienen detras. El propio
     * `SmtpEmailSender` ya se traga los fallos de entrega, asi que lo que este
     * `catch` atrapa es lo que ocurre **antes** de llegar a el.
     */
    private fun deliverDigest(householdId: UUID): Boolean =
        runCatching { digest.deliver() }
            .onFailure { log.error("El resumen diario falló en el hogar {}", householdId, it) }
            .getOrDefault(false)
}

/** Lo que hizo una pasada. Se registra, y las pruebas lo miran. */
data class SweepReport(
    val households: Int,
    val noticesRaised: Int,
    val digestsDelivered: Int,
)
