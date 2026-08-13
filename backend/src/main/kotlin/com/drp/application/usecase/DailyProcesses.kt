package com.drp.application.usecase

import com.drp.application.StorageKeys
import com.drp.application.port.FileStorage
import com.drp.application.port.HouseholdMemberRepository
import com.drp.application.port.HouseholdRepository
import com.drp.application.port.IdentityRepository
import com.drp.application.port.Pagination
import com.drp.application.port.StoredFileRepository
import com.drp.application.port.TenantResolver
import com.drp.application.tenant.TenantContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration
import java.util.UUID

/**
 * Borra los hogares que nunca llegaron a verificarse.
 *
 * Es **el unico borrado real del core** --todo lo demas es baja logica-- y se
 * justifica porque ahi no hay nada que conservar: unas categorias sembradas y
 * una identidad que nunca llego a entrar. Habra hogares asi porque el registro
 * es abierto, y es el precio de que lo sea.
 *
 * Como los otros dos procesos diarios, **no nace de una peticion** y por tanto no
 * tiene token del que sacar el hogar: recorre los hogares uno a uno fijando
 * `app.household_id` en cada transaccion, **nunca con `BYPASSRLS`**, que
 * desactivaria la segunda capa para toda la aplicacion y no solo para el
 * proceso. La lista de hogares se obtiene de la funcion acotada de resolucion de
 * inquilino, que solo devuelve identificadores.
 *
 * Es idempotente: solo mira lo que ya sobra.
 */
@Service
class PurgeUnverifiedHouseholds(
    private val tenantResolver: TenantResolver,
    private val tenantContext: TenantContext,
    private val transactions: TransactionTemplate,
    private val households: HouseholdRepository,
    private val members: HouseholdMemberRepository,
    private val identities: IdentityRepository,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun run(): Int {
        val cutoff = clock.instant().minus(RETENTION)
        var purged = 0

        for (householdId in tenantResolver.allHouseholdIds()) {
            // Una transaccion por hogar, con su propio contexto. Que un hogar
            // falle no puede arrastrar a los demas ni dejar a medias el que ya
            // se habia borrado.
            val removed = tenantContext.runAs(householdId) {
                transactions.execute { purgeIfUnverified(cutoff) }
            } ?: false

            if (removed) purged++
        }

        if (purged > 0) log.info("Purgados {} hogares sin verificar", purged)
        return purged
    }

    private fun purgeIfUnverified(cutoff: java.time.Instant): Boolean {
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
 * Como los otros dos procesos diarios, **no nace de una peticion** y por tanto no
 * tiene token del que sacar el hogar: recorre los hogares uno a uno fijando
 * `app.household_id` en cada transaccion, **nunca con `BYPASSRLS`**.
 *
 * El orden dentro de cada fichero es el inverso al de la subida, y por el mismo
 * motivo: alli los bytes se escriben **antes** que la fila, para que el disco
 * contenga siempre todo lo que la base de datos da por bueno; aqui se borran
 * **despues**, para no dejar nunca una fila viva apuntando a unos bytes que ya no
 * estan. Fallar entre medias deja bytes huerfanos, que es inofensivo --los recoge
 * la pasada siguiente-- y nunca una referencia rota.
 *
 * Es idempotente: solo mira lo que ya sobra.
 */
@Service
class PurgeUnusedFiles(
    private val tenantResolver: TenantResolver,
    private val tenantContext: TenantContext,
    private val transactions: TransactionTemplate,
    private val files: StoredFileRepository,
    private val storage: FileStorage,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun run(): Int {
        val now = clock.instant()
        var purged = 0

        for (householdId in tenantResolver.allHouseholdIds()) {
            // Una transaccion por hogar y con su propio contexto, igual que en la
            // purga de hogares: que uno falle no puede arrastrar a los demas.
            purged += tenantContext.runAs(householdId) {
                transactions.execute { purgeHousehold(now) }
            } ?: 0
        }

        if (purged > 0) log.info("Desenlazados {} ficheros que ya no servian", purged)
        return purged
    }

    private fun purgeHousehold(now: java.time.Instant): Int {
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
