package com.drp.core.application.usecase

import com.drp.core.application.EnrollmentEmails
import com.drp.core.application.SessionIssuer
import com.drp.core.application.TokenPair
import com.drp.core.application.port.EmailSender
import com.drp.core.application.port.HouseholdMemberRepository
import com.drp.core.application.port.HouseholdRepository
import com.drp.core.application.port.IdentityRepository
import com.drp.core.application.port.InvitationRepository
import com.drp.platform.page.Page
import com.drp.platform.page.Pagination
import com.drp.core.application.port.PasswordHasher
import com.drp.core.application.port.SecretGenerator
import com.drp.core.application.port.SessionClaims
import com.drp.core.application.port.TenantResolver
import com.drp.platform.tenant.TenantContext
import com.drp.core.domain.BusinessRuleViolation
import com.drp.core.domain.ErrorCode
import com.drp.core.domain.ResourceNotFound
import com.drp.core.domain.ValidationFailure
import com.drp.core.domain.household.HouseholdMember
import com.drp.core.domain.household.MemberRole
import com.drp.core.domain.identity.EmailAddress
import com.drp.core.domain.identity.Identity
import com.drp.core.domain.identity.PasswordPolicy
import com.drp.core.domain.invitation.Invitation
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.util.UUID

data class InviteUserCommand(val email: String, val role: MemberRole)

/**
 * Invitar a alguien al hogar.
 *
 * Un administrador no crea cuentas ajenas: invita. Esto sustituye al alta
 * directa, y con ella desaparecio `mustChangePassword` --era el apano para que
 * alguien cambiara una contrasena que otro le habia puesto, y ya nadie pone la
 * contrasena de nadie.
 */
@Service
class InviteUser(
    private val invitations: InvitationRepository,
    private val members: HouseholdMemberRepository,
    private val identities: IdentityRepository,
    private val households: HouseholdRepository,
    private val secrets: SecretGenerator,
    private val emails: EnrollmentEmails,
    private val emailSender: EmailSender,
    private val transactions: TransactionTemplate,
    private val clock: Clock,
) {

    fun handle(session: SessionClaims, command: InviteUserCommand): Invitation {
        val email = EmailAddress.of(command.email)
        val now = clock.instant()
        val secret = secrets.generate()

        val result = transactions.execute {
            // No se puede invitar a quien ya es miembro activo del hogar. La
            // busqueda por correo se resuelve sobre identities --que no tiene
            // RLS-- y despues por la pertenencia, que si la tiene: si la
            // identidad existe pero pertenece a otro hogar, findByIdentity no la
            // encuentra y la invitacion es legitima.
            val existing = identities.findByEmail(email)
            if (existing != null && members.findByIdentity(existing.id)?.isActive == true) {
                throw BusinessRuleViolation(ErrorCode.ALREADY_MEMBER, "Ya es miembro activo del hogar")
            }

            if (invitations.findLiveByEmail(email, now) != null) {
                throw BusinessRuleViolation(
                    ErrorCode.INVITATION_ALREADY_PENDING,
                    "Ya hay una invitación viva para ese correo",
                )
            }

            val invitation = invitations.save(
                Invitation(
                    id = UUID.randomUUID(),
                    householdId = session.householdId,
                    email = email,
                    role = command.role,
                    tokenHash = secret.hash,
                    expiresAt = now.plus(Invitation.LIFETIME),
                    acceptedAt = null,
                    revokedAt = null,
                    createdAt = now,
                    createdBy = session.memberId,
                ),
            )

            invitation to households.findCurrent()?.name.orEmpty()
        }!!

        emailSender.send(emails.invitation(email, result.second, secret.plain))
        return result.first
    }
}

@Service
class ListInvitations(
    private val invitations: InvitationRepository,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun handle(pagination: Pagination): Page<Invitation> = invitations.listLive(clock.instant(), pagination)
}

@Service
class RevokeInvitation(
    private val invitations: InvitationRepository,
    private val clock: Clock,
) {
    @Transactional
    fun handle(invitationId: UUID) {
        val now = clock.instant()
        // Si es de otro hogar, la politica no la deja ver y esto devuelve nulo:
        // "no existe" y "no es tuya" responden igual a proposito.
        val invitation = invitations.findById(invitationId)
            ?: throw ResourceNotFound("Invitación no encontrada")

        if (!invitation.isUsableAt(now)) {
            throw ResourceNotFound("Invitación no encontrada")
        }

        invitations.save(invitation.copy(revokedAt = now))
    }
}

data class AcceptInvitationCommand(
    val token: String,
    val name: String?,
    val password: String?,
)

/**
 * Aceptar una invitacion. Sin autenticar: lo autoriza el token recibido por
 * correo.
 *
 * **Aceptar verifica el correo por si solo.** Haber recibido el token demuestra
 * el control de esa direccion, que es exactamente lo que la verificacion
 * comprueba, asi que no hay un segundo paso para quien entra invitado.
 */
@Service
class AcceptInvitation(
    private val invitations: InvitationRepository,
    private val identities: IdentityRepository,
    private val members: HouseholdMemberRepository,
    private val passwords: PasswordHasher,
    private val passwordPolicy: PasswordPolicy,
    private val secrets: SecretGenerator,
    private val tenantResolver: TenantResolver,
    private val tenantContext: TenantContext,
    private val transactions: TransactionTemplate,
    private val sessions: SessionIssuer,
    private val clock: Clock,
) {

    fun handle(command: AcceptInvitationCommand): TokenPair {
        val tokenHash = secrets.hash(command.token)

        // Quien acepta no pertenece todavia a ningun hogar, asi que no hay
        // contexto del que partir: se resuelve cual es antes de leer nada.
        val householdId = tenantResolver.householdOfInvitationToken(tokenHash)
            ?: throw BusinessRuleViolation(ErrorCode.INVITATION_TOKEN_INVALID, "Invitación no válida")

        return tenantContext.runAs(householdId) {
            transactions.execute { accept(command, tokenHash) }!!
        }
    }

    private fun accept(command: AcceptInvitationCommand, tokenHash: String): TokenPair {
        val now = clock.instant()

        // Ya dentro del contexto, la invitacion se lee por la politica como
        // cualquier otra fila. Caducada, revocada o ya aceptada son el mismo
        // error: distinguirlas diria a quien prueba tokens cual existio.
        val invitation = invitations.findByTokenHash(tokenHash)
            ?.takeIf { it.isUsableAt(now) }
            ?: throw BusinessRuleViolation(ErrorCode.INVITATION_TOKEN_INVALID, "Invitación no válida")

        val existing = identities.findByEmail(invitation.email)

        val identity = if (existing == null) {
            val name = command.name?.takeIf { it.isNotBlank() }
                ?: throw ValidationFailure(mapOf("name" to "Hace falta el nombre para crear la cuenta"))
            val password = command.password
                ?: throw ValidationFailure(mapOf("password" to "Hace falta una contraseña para crear la cuenta"))
            passwordPolicy.require(password)

            identities.save(
                Identity(
                    id = UUID.randomUUID(),
                    name = name,
                    email = invitation.email,
                    phone = null,
                    passwordHash = passwords.hash(password),
                    // Nace YA VERIFICADA: recibir el token prueba el control del
                    // correo.
                    emailVerifiedAt = now,
                    lastLoginAt = null,
                    avatar = null,
                    createdAt = now,
                    updatedAt = now,
                    deactivatedAt = null,
                ),
            )
        } else {
            // Mientras el MVP admita una sola pertenencia activa, quien ya
            // pertenece a un hogar no puede entrar en otro. El indice unico
            // parcial lo impediria igualmente; se comprueba aqui para poder
            // responder con el codigo del contrato en lugar de con un 500.
            if (tenantResolver.householdOfActiveMember(existing.id) != null) {
                throw BusinessRuleViolation(
                    ErrorCode.IDENTITY_ALREADY_MEMBER,
                    "Esa identidad ya pertenece a un hogar",
                )
            }
            existing
        }

        val member = members.save(
            HouseholdMember(
                id = UUID.randomUUID(),
                householdId = invitation.householdId,
                identityId = identity.id,
                role = invitation.role,
                createdAt = now,
                updatedAt = now,
                deactivatedAt = null,
                // Lo atribuye quien invito, que es quien tomo la decision.
                createdBy = invitation.createdBy,
                updatedBy = null,
            ),
        )

        invitations.save(invitation.copy(acceptedAt = now))

        return sessions.issueFor(identity.id, member)
    }
}
