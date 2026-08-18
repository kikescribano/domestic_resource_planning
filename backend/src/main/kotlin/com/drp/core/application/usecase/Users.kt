package com.drp.core.application.usecase

import com.drp.core.application.port.HouseholdMemberRepository
import com.drp.core.application.port.IdentityRepository
import com.drp.platform.page.Page
import com.drp.platform.page.Pagination
import com.drp.core.application.port.RefreshTokenRepository
import com.drp.core.application.port.SessionClaims
import com.drp.core.domain.BusinessRuleViolation
import com.drp.core.domain.ErrorCode
import com.drp.core.domain.ResourceNotFound
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
    }
}
