package com.drp.core.application.usecase

import com.drp.core.application.EnrollmentEmails
import com.drp.core.application.SessionIssuer
import com.drp.core.application.TokenPair
import com.drp.platform.mail.EmailSender
import com.drp.core.application.port.HouseholdMemberRepository
import com.drp.core.application.port.IdentityRepository
import com.drp.core.application.port.PasswordHasher
import com.drp.core.application.port.PasswordResetTokenRepository
import com.drp.core.application.port.RefreshTokenRepository
import com.drp.core.application.port.SecretGenerator
import com.drp.core.application.port.SessionClaims
import com.drp.core.application.port.TenantResolver
import com.drp.platform.tenant.TenantContext
import com.drp.platform.error.BusinessRuleViolation
import com.drp.platform.error.ErrorCode
import com.drp.platform.mail.EmailAddress
import com.drp.core.domain.identity.PasswordPolicy
import com.drp.core.domain.token.SingleUseToken
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.util.UUID

/**
 * Pedir el restablecimiento. Sin credencial de ningun tipo.
 *
 * Responde siempre igual exista o no el correo y este o no dada de baja la
 * identidad, por el mismo motivo que el alta de un hogar.
 */
@Service
class RequestPasswordReset(
    private val identities: IdentityRepository,
    private val resetTokens: PasswordResetTokenRepository,
    private val secrets: SecretGenerator,
    private val emails: EnrollmentEmails,
    private val emailSender: EmailSender,
    private val transactions: TransactionTemplate,
    private val clock: Clock,
) {

    fun handle(rawEmail: String) {
        val email = EmailAddress.of(rawEmail)
        val identity = identities.findByEmail(email)

        // Una identidad dada de baja no restablece contrasena, y una que no
        // existe tampoco. Ninguna de las dos recibe correo, y la respuesta es la
        // misma que en cualquier otro caso.
        //
        // OJO: salir por aqui delata por el reloj mientras la entrega sea
        // sincrona. Un correo desconocido responde en el acto y uno registrado
        // tarda la ida y vuelta entera con el servidor SMTP, y aqui --a
        // diferencia del alta de un hogar-- no hay ningun hash de Argon2id que
        // enmascare la diferencia. El hueco esta anotado con su motivo en
        // SmtpEmailSender y en 4.1.7; lo que lo cierra es sacar la entrega del
        // hilo de la peticion, que es un cambio con entidad propia.
        if (identity == null || !identity.isActive) return

        val now = clock.instant()
        val reset = secrets.generate()

        // Ni identities ni password_reset_tokens llevan household_id, asi que
        // este camino funciona sin contexto de inquilino: es de la persona, no de
        // ninguno de sus hogares.
        transactions.execute {
            // Solo hay un token de restablecimiento vivo por identidad: pedir
            // otro invalida el anterior. Sin esto, cada peticion dejaria uno mas
            // vivo y bastaria con que se filtrase cualquiera de ellos.
            resetTokens.markAllUsedForIdentity(identity.id, now)
            resetTokens.save(
                SingleUseToken(
                    id = UUID.randomUUID(),
                    identityId = identity.id,
                    tokenHash = reset.hash,
                    expiresAt = now.plus(SingleUseToken.PASSWORD_RESET_LIFETIME),
                    usedAt = null,
                ),
            )
        }

        // Fuera de la transaccion (ADR-009).
        emailSender.send(emails.passwordReset(email, reset.plain))
    }
}

/**
 * Fijar la contrasena nueva con el token recibido.
 *
 * Tres cosas lo diferencian de los demas tokens del sistema, y las tres estan
 * aqui: dura una hora, **verifica el correo** --recibir el token prueba el
 * control de la direccion-- y **revoca todas** las sesiones antes de emitir el
 * par nuevo.
 */
@Service
class ResetPassword(
    private val resetTokens: PasswordResetTokenRepository,
    private val identities: IdentityRepository,
    private val members: HouseholdMemberRepository,
    private val refreshTokens: RefreshTokenRepository,
    private val passwords: PasswordHasher,
    private val passwordPolicy: PasswordPolicy,
    private val secrets: SecretGenerator,
    private val tenantResolver: TenantResolver,
    private val tenantContext: TenantContext,
    private val transactions: TransactionTemplate,
    private val sessions: SessionIssuer,
    private val clock: Clock,
) {

    fun handle(token: String, newPassword: String): TokenPair {
        passwordPolicy.require(newPassword)

        val now = clock.instant()
        val stored = resetTokens.findByTokenHash(secrets.hash(token))
        if (stored == null || !stored.isUsableAt(now)) {
            throw BusinessRuleViolation(ErrorCode.RESET_TOKEN_INVALID, "Token de restablecimiento no válido")
        }

        val identity = identities.findById(stored.identityId)
            ?.takeIf { it.isActive }
            ?: throw BusinessRuleViolation(ErrorCode.RESET_TOKEN_INVALID, "Token de restablecimiento no válido")

        val householdId = tenantResolver.householdOfActiveMember(identity.id)
            ?: throw BusinessRuleViolation(ErrorCode.RESET_TOKEN_INVALID, "Token de restablecimiento no válido")

        val passwordHash = passwords.hash(newPassword)

        return tenantContext.runAs(householdId) {
            transactions.execute {
                identities.save(
                    identity.copy(
                        passwordHash = passwordHash,
                        // Restablecer verifica el correo, por lo mismo que
                        // aceptar una invitacion. Un hogar creado y nunca
                        // verificado se rescata por aqui, y sale de la cola de
                        // purga.
                        emailVerifiedAt = identity.emailVerifiedAt ?: now,
                        updatedAt = now,
                    ),
                )
                resetTokens.markAllUsedForIdentity(identity.id, now)

                // ANTES de emitir el par nuevo, no despues. Si el motivo del
                // restablecimiento era que alguien mas habia entrado, dejarle la
                // sesion abierta anula el gesto entero.
                refreshTokens.revokeAllForIdentity(identity.id, now)

                val member = members.findByIdentity(identity.id)
                    ?: throw BusinessRuleViolation(
                        ErrorCode.RESET_TOKEN_INVALID,
                        "Token de restablecimiento no válido",
                    )

                sessions.issueFor(identity.id, member)
            }!!
        }
    }
}

/**
 * Cambiarla estando dentro.
 *
 * Exige la actual ademas de la nueva. No es burocracia: sin ese requisito, quien
 * se hiciera con un access token robado podria cambiar la contrasena y dejar
 * fuera al dueno de la cuenta.
 */
@Service
class ChangePassword(
    private val identities: IdentityRepository,
    private val refreshTokens: RefreshTokenRepository,
    private val passwords: PasswordHasher,
    private val passwordPolicy: PasswordPolicy,
    private val secrets: SecretGenerator,
    private val clock: Clock,
) {

    @Transactional
    fun handle(session: SessionClaims, currentPassword: String, newPassword: String) {
        passwordPolicy.require(newPassword)

        val identity = identities.findById(session.identityId)
            ?: throw AuthenticationFailed(ErrorCode.CURRENT_PASSWORD_INVALID, "La contraseña actual no es correcta")

        if (!passwords.matches(currentPassword, identity.passwordHash)) {
            // Falla sin tocar nada: ni la contrasena, ni las sesiones.
            throw AuthenticationFailed(ErrorCode.CURRENT_PASSWORD_INVALID, "La contraseña actual no es correcta")
        }

        val now = clock.instant()
        identities.save(identity.copy(passwordHash = passwords.hash(newPassword), updatedAt = now))

        // Revoca las DEMAS y conserva la que esta en uso, que es lo que
        // distingue cambiarla de restablecerla: aqui el dueno de la cuenta esta
        // dentro y no tiene por que echarse a si mismo. Cual es la que esta en
        // uso lo dice el claim de sesion del propio access token.
        refreshTokens.revokeAllForIdentityExcept(identity.id, session.sessionId, now)
    }
}
