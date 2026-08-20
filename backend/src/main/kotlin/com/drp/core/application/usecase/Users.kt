package com.drp.core.application.usecase

import com.drp.core.application.event.CoreEvents
import com.drp.core.application.port.FileStorage
import com.drp.core.application.port.HouseholdMemberRepository
import com.drp.core.application.port.IdentityRepository
import com.drp.platform.page.Page
import com.drp.platform.page.Pagination
import com.drp.core.application.port.RefreshTokenRepository
import com.drp.core.application.port.SessionClaims
import com.drp.core.application.port.TenantResolver
import com.drp.platform.error.BusinessRuleViolation
import com.drp.platform.error.ErrorCode
import com.drp.platform.error.ResourceNotFound
import com.drp.core.domain.household.HouseholdMember
import com.drp.core.domain.household.MemberRole
import com.drp.core.domain.identity.Identity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

/**
 * Una pertenencia con los datos de la persona ya resueltos.
 *
 * El `User` del contrato es una **pertenencia**, no una identidad: su `id` es el
 * `memberId`, que es lo que referencian el propietario de un asset, los
 * prestamos y la autoria. El nombre y el correo se resuelven desde la identidad,
 * que vive fuera del hogar.
 */
data class HouseholdUser(
    val member: HouseholdMember,
    val identity: Identity,
)

@Service
class ListUsers(
    private val members: HouseholdMemberRepository,
    private val identities: IdentityRepository,
) {

    @Transactional(readOnly = true)
    fun handle(includeDeactivated: Boolean, pagination: Pagination): Page<HouseholdUser> {
        val page = members.list(includeDeactivated, pagination)

        // Se resuelve identidad a identidad y por identificador, nunca listando
        // `identities`: es la unica tabla con datos personales sin RLS debajo, y
        // su repositorio no puede ofrecer un listado sin convertirse en el
        // agujero por el que salen los correos de toda la instalacion. Aqui cada
        // identidad se pide porque una pertenencia **de este hogar** --que si ha
        // pasado por la politica-- la nombra.
        val users = page.items.mapNotNull { member ->
            identities.findById(member.identityId)?.let { HouseholdUser(member, it) }
        }

        return Page(users, page.page, page.size, page.total)
    }
}

@Service
class ChangeUserRole(
    private val members: HouseholdMemberRepository,
    private val identities: IdentityRepository,
    private val clock: Clock,
) {

    @Transactional
    fun handle(session: SessionClaims, memberId: UUID, role: MemberRole): HouseholdUser {
        val member = members.findById(memberId) ?: throw ResourceNotFound("Usuario no encontrado")

        // Un hogar sin administrador no puede invitar, cambiar roles ni dar de
        // baja a nadie: quedaria bloqueado sin forma de salir. No es expresable
        // como CHECK porque depende del resto de filas del hogar.
        if (member.isAdmin && role != MemberRole.HOUSEHOLD_ADMIN && members.countActiveAdmins() <= 1) {
            throw BusinessRuleViolation(
                ErrorCode.USER_LAST_ADMIN,
                "Dejaría al hogar sin ningún administrador activo",
            )
        }

        val now = clock.instant()
        val updated = members.save(member.copy(role = role, updatedAt = now, updatedBy = session.memberId))
        val identity = identities.findById(updated.identityId) ?: throw ResourceNotFound("Usuario no encontrado")

        return HouseholdUser(updated, identity)
    }
}

/**
 * Sacar a alguien del hogar. Baja **logica** y sobre la pertenencia, no sobre la
 * cuenta: la persona deja de ver este hogar y su identidad sigue existiendo.
 */
@Service
class DeactivateUser(
    private val members: HouseholdMemberRepository,
    private val refreshTokens: RefreshTokenRepository,
    private val events: CoreEvents,
    private val clock: Clock,
) {

    @Transactional
    fun handle(session: SessionClaims, memberId: UUID) {
        val member = members.findById(memberId)
            ?.takeIf { it.isActive }
            ?: throw ResourceNotFound("Usuario no encontrado")

        if (member.isAdmin && members.countActiveAdmins() <= 1) {
            throw BusinessRuleViolation(
                ErrorCode.USER_LAST_ADMIN,
                "Es el único administrador activo del hogar",
            )
        }

        val now = clock.instant()
        members.save(member.copy(deactivatedAt = now, updatedAt = now, updatedBy = session.memberId))

        // La fila permanece --los prestamos y el historial la referencian-- y
        // sus assets quedan SIN PROPIETARIO, sin reasignarse solos: exigir el
        // destino de todo lo suyo en el mismo gesto convertiria una baja en un
        // inventario completo, y con cuarenta cosas a su nombre eso significa
        // que la baja no se hace. Se localizan despues con
        // GET /assets?withoutOwner=true, que llega con el Hito 2.
        refreshTokens.revokeAllForIdentity(member.identityId, now)

        events.userDeactivated(member.id)
    }
}

/**
 * La vuelta de la baja: volver a encender la pertenencia que [DeactivateUser]
 * apago. La persona regresa **con el rol que tenia**, porque la fila es la misma
 * y el rol es suyo, no de la invitacion que ya no existe.
 *
 * Lo que **no** deshace, y es deliberado:
 *
 * - **Los refresh tokens revocados no vuelven.** Una revocacion no se puede
 *   des-revocar sin convertir la revocacion en una promesa vacia; la persona
 *   entra de nuevo con sus credenciales, que siguen siendo suyas.
 * - **Sus assets siguen sin propietario.** A estas alturas pueden tener dueño
 *   nuevo, y reasignarlos en bloque seria adivinar que todo lo huerfano era
 *   suyo. Se localizan donde siempre: `GET /assets?withoutOwner=true`.
 *
 * Es **idempotente como `ActivateModule`**, no un espejo del 404 de
 * [DeactivateUser]: reactivar a quien ya esta activo devuelve el mismo estado.
 * Dos administradores pulsando a la vez no merecen un error.
 *
 * Y **no publica ningun evento**: el criterio del catalogo de 5.2.3 es que un
 * evento entra el dia que un modulo lo necesita, no la simetria con
 * `UserDeactivated`. Ese dia sera del planificador de tareas.
 */
@Service
class ReactivateUser(
    private val members: HouseholdMemberRepository,
    private val identities: IdentityRepository,
    private val tenantResolver: TenantResolver,
    private val clock: Clock,
) {

    @Transactional
    fun handle(session: SessionClaims, memberId: UUID): HouseholdUser {
        val member = members.findById(memberId) ?: throw ResourceNotFound("Usuario no encontrado")
        val identity = identities.findById(member.identityId) ?: throw ResourceNotFound("Usuario no encontrado")

        if (member.isActive) {
            return HouseholdUser(member, identity)
        }

        // Una pertenencia activa cuya identidad no puede autenticarse nunca es
        // justo la incoherencia que `CloseAccount` evita al dar de baja las dos
        // cosas a la vez; reactivar solo una la fabricaria.
        if (!identity.isActive) {
            throw BusinessRuleViolation(
                ErrorCode.IDENTITY_CLOSED,
                "La cuenta de esa persona está cerrada",
            )
        }

        // Mientras el MVP admita una sola pertenencia activa, quien ya
        // pertenece a un hogar no puede volver a este. El indice unico parcial
        // lo impediria igualmente; se comprueba aqui para poder responder con
        // el codigo del contrato en lugar de con un 500 -- la misma pareja
        // comprobacion-indice que en `AcceptInvitation`.
        if (tenantResolver.householdOfActiveMember(member.identityId) != null) {
            throw BusinessRuleViolation(
                ErrorCode.IDENTITY_ALREADY_MEMBER,
                "Esa identidad ya pertenece a un hogar",
            )
        }

        val now = clock.instant()
        val updated = members.save(member.copy(deactivatedAt = null, updatedAt = now, updatedBy = session.memberId))

        return HouseholdUser(updated, identity)
    }
}

/**
 * Cerrar la cuenta (`CloseAccount`, 4.1.4 y 5.7).
 *
 * Es la regla que 4.1.4 llevaba escrita **desde la Fase 1 sin nada a lo que
 * engancharse**: `DeactivateUser` da de baja la pertenencia, no la identidad, y
 * no habia ninguna operacion que diera de baja a la persona. La baja de hogar
 * (ADR-012) la trae consigo, porque es la que produce el caso que obliga a
 * responder que se hace con una identidad.
 *
 * **La direccion importa y es de una sola via.** Cerrar la cuenta **no se lleva
 * la casa por delante**: se va la persona y el hogar sigue con quien quede. Al
 * reves si --la baja del hogar puede dejar identidades sin ninguna pertenencia, y
 * de esas se ocupa [PurgeClosedHouseholds].
 *
 * Hace cuatro cosas, y ninguna es opcional:
 *
 * - **Da de baja la identidad.** `deactivatedAt`, que es lo que impide
 *   autenticarse en cualquier hogar --`canAuthenticate` mira las dos cosas.
 * - **Da de baja la pertenencia.** No lo decia 4.1.4 y hace falta: sin esto, la
 *   persona sigue apareciendo activa en «Personas», cuenta como administrador a
 *   efectos de [ChangeUserRole] y el hogar se queda creyendo que tiene a alguien
 *   que no puede entrar nunca. Sus assets quedan **sin propietario**, igual que
 *   en [DeactivateUser] y por el mismo motivo.
 * - **Revoca sus refresh tokens**, o la sesion abierta seguiria renovandose
 *   quince minutos mas cada vez durante dias.
 * - **Borra el avatar.** Es lo unico del sistema que retrata a una persona, y la
 *   baja de la identidad es el momento en que deja de haber motivo para
 *   conservarlo. **Los ficheros del hogar se quedan**: son del hogar y no suyos.
 *
 * **El unico administrador activo no puede cerrar su cuenta** (`USER_LAST_ADMIN`,
 * el mismo codigo que ya usan [ChangeUserRole] y [DeactivateUser], porque es
 * literalmente la misma regla). Un hogar sin administrador no puede invitar,
 * cambiar roles, encender modulos **ni pedir su propia baja**: queda bloqueado
 * sin forma de salir, y bloqueado por una decision personal de alguien que ya no
 * esta para arreglarlo. La salida esta en sus manos: nombrar a otra persona
 * administradora, o pedir la baja del hogar y cerrar la cuenta despues de que se
 * purgue.
 *
 * Se descarto **eximir de la regla a un hogar que ya tenga la baja pedida**, que
 * parecia inofensivo porque ese hogar va a desaparecer igual. No lo es: dejaria
 * la baja **sin nadie que pueda cancelarla**, y poder cancelarla es la razon
 * entera de que haya treinta dias de gracia. Tambien se descarto **nombrar
 * administrador a otra persona automaticamente**, que decide quien gobierna la
 * casa en el gesto de irse de ella y sin preguntar a nadie.
 */
@Service
class CloseAccount(
    private val members: HouseholdMemberRepository,
    private val identities: IdentityRepository,
    private val refreshTokens: RefreshTokenRepository,
    private val storage: FileStorage,
    private val events: CoreEvents,
    private val clock: Clock,
) {

    @Transactional
    fun handle(session: SessionClaims) {
        val member = members.findById(session.memberId)
            ?.takeIf { it.isActive }
            ?: throw ResourceNotFound("Usuario no encontrado")

        if (member.isAdmin && members.countActiveAdmins() <= 1) {
            throw BusinessRuleViolation(
                ErrorCode.USER_LAST_ADMIN,
                "Eres el único administrador activo del hogar",
            )
        }

        val identity = identities.findById(session.identityId) ?: throw ResourceNotFound("Usuario no encontrado")
        val now = clock.instant()

        // Los bytes primero y la fila despues, que es el criterio de
        // `PurgeUnusedFiles`. Al reves --como hace `DeleteIdentityAvatar`, donde
        // es razonable porque una foto se vuelve a subir-- un fallo entre medias
        // dejaria la cara de esta persona en disco para siempre: nada vuelve a
        // mirar el avatar de una identidad dada de baja, asi que nadie lo
        // recogeria. Aqui el compromiso es justo el contrario, y por eso el
        // borrado va delante aunque la contrapartida sea una fila que apunte un
        // instante a unos bytes que ya no estan.
        identity.avatar?.let { storage.delete(it.storageKey) }

        identities.save(identity.copy(avatar = null, deactivatedAt = now, updatedAt = now))
        members.save(member.copy(deactivatedAt = now, updatedAt = now, updatedBy = session.memberId))
        refreshTokens.revokeAllForIdentity(identity.id, now)

        // La pertenencia tambien se apaga aqui, asi que el evento tambien sale
        // de aqui: quien escuche va a querer saber que la persona dejo el
        // hogar, no por que puerta.
        events.userDeactivated(member.id)
    }
}
