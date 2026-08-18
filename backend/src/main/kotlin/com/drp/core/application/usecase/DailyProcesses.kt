package com.drp.core.application.usecase

import com.drp.core.application.StorageKeys
import com.drp.core.application.event.CoreEvents
import com.drp.core.application.port.FileStorage
import com.drp.core.application.port.HouseholdMemberRepository
import com.drp.core.application.port.HouseholdRepository
import com.drp.core.application.port.IdentityRepository
import com.drp.core.application.port.LoanRepository
import com.drp.core.application.port.StoredFileRepository
import com.drp.core.domain.loan.LoanStatus
import com.drp.platform.notice.NoticeDraft
import com.drp.platform.page.Pagination
import com.drp.platform.schedule.CheckOwner
import com.drp.platform.schedule.ScheduledCheck
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Las tres comprobaciones diarias del core.
 *
 * **Ya no recorren los hogares.** Hasta el Hito 1 de la Fase 2 cada una pedia la
 * lista al `TenantResolver` y abria transaccion por hogar, y ninguna la invocaba
 * nadie mas que las pruebas. Ahora quien recorre es `DailySweep`, en plataforma,
 * y esto son [ScheduledCheck] que miran **el hogar actual**: cuando llegan aqui
 * el `runAs` ya esta puesto, la transaccion abierta y `app.household_id` fijado,
 * asi que la politica de RLS acota lo que ven igual que en una peticion.
 *
 * La razon de que perdieran su recorrido no es de estilo: con el suyo mas el de
 * plataforma habria **dos**, y solo uno de los dos puede saltarse los hogares
 * que tengan apagado el modulo que pide la comprobacion (ADR-011).
 *
 * Las tres declaran `CheckOwner.Core`, que significa **corren en todos los
 * hogares**: el core no se apaga, asi que un prestamo vence igual en un hogar
 * que no ha encendido ningun modulo.
 */

/**
 * Borra los hogares que nunca llegaron a verificarse.
 *
 * Es **el unico borrado real del core** --todo lo demas es baja logica-- y se
 * justifica porque ahi no hay nada que conservar: unas categorias sembradas y
 * una identidad que nunca llego a entrar. Habra hogares asi porque el registro
 * es abierto, y es el precio de que lo sea.
 *
 * Es idempotente: solo mira lo que ya sobra.
 *
 * **No produce ningun aviso**, y no podria: el destinatario de ese aviso seria un
 * hogar que acaba de dejar de existir. Es ademas la unica comprobacion que puede
 * hacer desaparecer el hogar en curso, y por eso conviene saber que el recorrido
 * ordena las comprobaciones por nombre --esta va la ultima de las tres-- y que lo
 * que corriera detras no encontraria nada, porque el borrado se lleva en cascada
 * todo lo suyo.
 */
@Service
class PurgeUnverifiedHouseholds(
    private val households: HouseholdRepository,
    private val members: HouseholdMemberRepository,
    private val identities: IdentityRepository,
    private val clock: Clock,
) : ScheduledCheck {

    private val log = LoggerFactory.getLogger(javaClass)

    override val name: String = "PurgeUnverifiedHouseholds"
    override val owner: CheckOwner = CheckOwner.Core

    override fun check(): List<NoticeDraft> {
        val cutoff = clock.instant().minus(RETENTION)
        if (purgeIfUnverified(cutoff)) log.info("Purgado un hogar sin verificar")
        return emptyList()
    }

    private fun purgeIfUnverified(cutoff: Instant): Boolean {
        val household = households.findCurrent() ?: return false
        if (household.createdAt.isAfter(cutoff)) return false

        // Todas las pertenencias del hogar, incluidas las dadas de baja: si
        // alguna persona llego a verificarse alguna vez, el hogar es real y no se
        // toca aunque hoy no quede nadie activo.
        val memberships = members.list(includeDeactivated = true, Pagination(0, MAX_MEMBERS_PER_HOUSEHOLD)).items
        val identitiesInHousehold = memberships.mapNotNull { identities.findById(it.identityId) }

        if (identitiesInHousehold.any { it.isVerified }) return false

        // El hogar arrastra en cascada sus pertenencias, invitaciones y
        // categorias. Las identidades no cuelgan de el --una persona no pertenece
        // a un hogar-- asi que se borran aparte, y solo las que nunca se
        // verificaron.
        households.deleteCurrent()
        identitiesInHousehold.filterNot { it.isVerified }.forEach { identities.delete(it.id) }

        return true
    }

    private companion object {
        /**
         * Siete dias. El token de verificacion caduca mucho antes, asi que una
         * semana da margen de sobra para reenviarlo; alargarlo solo acumula
         * hogares fantasma y mantiene retenido un correo que quiza ni era de
         * quien lo tecleo.
         */
        val RETENTION: Duration = Duration.ofDays(7)

        /**
         * Un hogar sin verificar tiene una pertenencia --la de quien lo abrio--,
         * asi que este limite es holgura, no una pagina de verdad.
         */
        const val MAX_MEMBERS_PER_HOUSEHOLD = 500
    }
}

/**
 * Desenlaza del disco los ficheros que ya no sirven (`PurgeUnusedFiles`, 5.7).
 *
 * Existe porque **la base de datos y el disco pueden divergir**, y este es el
 * lado que las vuelve a juntar. Recoge tres cosas distintas, y cada una tiene su
 * plazo porque cada una significa algo distinto:
 *
 * - **Los borrados hace mas de 24 h.** Borrar un fichero marca la fila y libera
 *   la cuota en el acto; los bytes esperan. Ese margen **no es una funcion de
 *   deshacer** --no hay ningun gesto que restaure lo borrado-- sino la ventana en
 *   la que un operador todavia puede recuperar un borrado por error sin ir a la
 *   copia de seguridad.
 * - **Los subidos y nunca adjuntados**, tambien a las 24 h. Un fichero que no
 *   cuelga de nada ocupa cuota sin servir para nada.
 * - **Las reservas que nunca se completaron**, al cabo de una hora: son subidas
 *   cortadas a medias, con `uploadedAt` a nulo. Una hora es de sobra para la
 *   subida mas lenta que el tope de 25 MB permite.
 *
 * El orden dentro de cada fichero es el inverso al de la subida, y por el mismo
 * motivo: alli los bytes se escriben **antes** que la fila, para que el disco
 * contenga siempre todo lo que la base de datos da por bueno; aqui se borran
 * **despues**, para no dejar nunca una fila viva apuntando a unos bytes que ya no
 * estan. Fallar entre medias deja bytes huerfanos, que es inofensivo --los recoge
 * la pasada siguiente-- y nunca una referencia rota.
 *
 * Es idempotente: solo mira lo que ya sobra.
 *
 * **No produce aviso.** Recoger basura es mantenimiento y no noticia: nadie
 * quiere un correo diario diciendo que se han borrado unos bytes que ya habia
 * borrado el mes pasado.
 */
@Service
class PurgeUnusedFiles(
    private val files: StoredFileRepository,
    private val storage: FileStorage,
    private val clock: Clock,
) : ScheduledCheck {

    private val log = LoggerFactory.getLogger(javaClass)

    override val name: String = "PurgeUnusedFiles"
    override val owner: CheckOwner = CheckOwner.Core

    override fun check(): List<NoticeDraft> {
        val purged = purgeHousehold(clock.instant())
        if (purged > 0) log.info("Desenlazados {} ficheros que ya no servían", purged)
        return emptyList()
    }

    private fun purgeHousehold(now: Instant): Int {
        val purgeable = files.findPurgeable(
            deletedBefore = now.minus(DELETED_GRACE),
            neverAttachedBefore = now.minus(UNATTACHED_GRACE),
            reservedBefore = now.minus(RESERVATION_GRACE),
        )

        for (file in purgeable) {
            // Los bytes primero, la fila despues. Al reves, un fallo entre medias
            // dejaria una fila viva apuntando a unos bytes que ya no estan --y eso
            // si es un error duro, porque nada volveria a mirarlo.
            storage.delete(file.storageKey)
            storage.delete(StorageKeys.thumbnailOf(file.storageKey))
            files.delete(file.id)
        }

        return purgeable.size
    }

    private companion object {
        /** La ventana para recuperar un borrado por error sin ir a la copia de seguridad. */
        val DELETED_GRACE: Duration = Duration.ofHours(24)

        /** Lo que se sube y no se adjunta ocupa cuota; a las 24 h se retira. */
        val UNATTACHED_GRACE: Duration = Duration.ofHours(24)

        /** Una subida cortada a medias. Una hora es de sobra para el fichero mas grande admitido. */
        val RESERVATION_GRACE: Duration = Duration.ofHours(1)
    }
}

/**
 * Marca los prestamos vencidos (`MarkOverdueLoans`, 4.1.5 y 5.7).
 *
 * **El unico cambio de estado del core que no lo provoca nadie.** Todo lo demas
 * ocurre porque alguien pulsa algo; esto ocurre porque pasa el tiempo, y de ahi
 * que haga falta un proceso que lo mire.
 *
 * Que el estado se persista en lugar de derivarse al leer es justo lo que
 * permite publicar `LoanOverdue`: un valor calculado no tiene momento en el que
 * ocurrir, y sin ese momento no hay evento del que colgar los recordatorios que
 * la gestion avanzada de prestamos (4.2) necesitara.
 *
 * Tres reglas, y las tres estan en la consulta y no aqui, para que no se puedan
 * olvidar al llamar:
 *
 * - Solo `ACTIVE`. Un `OVERDUE` no se vuelve a marcar, que es lo que hace la
 *   pasada **idempotente**: la segunda no encuentra nada, no publica nada y **no
 *   avisa por segunda vez**.
 * - Solo con `dueAt` informada. **Un prestamo sin plazo no vence nunca**: es un
 *   prestamo sin fecha, no un plazo infinito.
 * - Solo si la fecha ya paso.
 *
 * **El asset no se toca**: sigue `LENT`, porque vencer no es devolver. Es la
 * regla que mas facil es incumplir aqui, y por eso hay una prueba que la mira.
 *
 * `updatedBy` queda a **nulo**, que en este esquema no es un hueco sino un dato:
 * significa que el cambio lo hizo el sistema y no una persona. Este proceso es
 * el caso que el contrato pone de ejemplo al documentar `Authorship`.
 *
 * Es la unica de las tres que **si avisa**, y con **un aviso por pasada y no uno
 * por prestamo**: el resumen diario existe para que cinco reglas no produzcan
 * treinta correos, y empezar a partirlo aqui seria deshacerlo desde dentro.
 */
@Service
class MarkOverdueLoans(
    private val loans: LoanRepository,
    private val events: CoreEvents,
    private val clock: Clock,
) : ScheduledCheck {

    private val log = LoggerFactory.getLogger(javaClass)

    override val name: String = "MarkOverdueLoans"
    override val owner: CheckOwner = CheckOwner.Core

    override fun check(): List<NoticeDraft> {
        val now = clock.instant()
        val overdue = loans.findOverdueCandidates(now)
        if (overdue.isEmpty()) return emptyList()

        val names = mutableListOf<String>()
        for (loan in overdue) {
            loans.assetNameOf(loan)?.let { names.add(it) }
            loans.save(loan.copy(status = LoanStatus.OVERDUE, updatedAt = now, updatedBy = null))
            events.loanOverdue(loan.id, loan.assetId, loan.dueAt!!)
        }

        log.info("Marcados {} préstamos como vencidos", overdue.size)
        return listOf(noticeFor(overdue.size, names))
    }

    /**
     * El aviso lleva los nombres y no solo la cuenta, que es la diferencia entre
     * un correo que se lee y uno que obliga a entrar para saber de que va. Si
     * ninguno tiene nombre --un consumible no se presta, pero un duradero puede
     * no tenerlo-- se queda en la cuenta en lugar de escribir una lista vacia.
     */
    private fun noticeFor(count: Int, names: List<String>): NoticeDraft {
        val what = if (count == 1) "1 préstamo ha vencido" else "$count préstamos han vencido"
        val detail = if (names.isEmpty()) "" else names.joinToString(", ") + ". "

        return NoticeDraft(
            kind = KIND,
            title = what,
            body = detail + "Siguen contando como prestados hasta que confirmes la devolución.",
        )
    }

    private companion object {
        const val KIND = "LOANS_OVERDUE"
    }
}
