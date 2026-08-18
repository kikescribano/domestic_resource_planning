package com.drp.core.application.usecase

import com.drp.core.application.SessionIssuer
import com.drp.core.application.TokenPair
import com.drp.core.application.port.HouseholdMemberRepository
import com.drp.core.application.port.IdentityRepository
import com.drp.core.application.port.PasswordHasher
import com.drp.core.application.port.RefreshTokenRepository
import com.drp.core.application.port.SecretGenerator
import com.drp.core.application.port.TenantResolver
import com.drp.platform.tenant.TenantContext
import com.drp.platform.error.ErrorCode
import com.drp.platform.mail.EmailAddress
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock

/**
 * Credenciales invalidas.
 *
 * Lleva el codigo dentro para poder distinguir el correo sin verificar --que el
 * contrato expone como `EMAIL_NOT_VERIFIED`, porque el usuario necesita saber que
 * le toca mirar el correo-- de la contrasena equivocada, que no dice nada.
 */
class AuthenticationFailed(val code: ErrorCode?, override val message: String) : RuntimeException(message)

data class LoginCommand(val email: String, val password: String)

@Service
class Login(
    private val identities: IdentityRepository,
    private val members: HouseholdMemberRepository,
    private val passwords: PasswordHasher,
    private val tenantResolver: TenantResolver,
    private val tenantContext: TenantContext,
    private val transactions: TransactionTemplate,
    private val sessions: SessionIssuer,
    private val clock: Clock,
) {

    fun handle(command: LoginCommand): TokenPair {
        val identity = identities.findByEmail(EmailAddress.of(command.email))

        // Se comprueba la contrasena tambien cuando no hay identidad, contra un
        // hash de descarte. Sin esto, un correo desconocido responderia sin pagar
        // el coste de Argon2id y quien probase direcciones sabria cuales existen
        // solo mirando el reloj.
        val matches = passwords.matches(command.password, identity?.passwordHash ?: DISCARD_HASH)

        if (identity == null || !matches) {
            throw AuthenticationFailed(null, "Credenciales no válidas")
        }
        if (!identity.isActive) {
            // Una cuenta cerrada responde como una credencial equivocada: que
            // exista pero este dada de baja no es asunto de quien pregunta.
            throw AuthenticationFailed(null, "Credenciales no válidas")
        }
        if (!identity.isVerified) {
            throw AuthenticationFailed(ErrorCode.EMAIL_NOT_VERIFIED, "El correo no está verificado")
        }

        val householdId = tenantResolver.householdOfActiveMember(identity.id)
            ?: throw AuthenticationFailed(null, "Credenciales no válidas")

        return tenantContext.runAs(householdId) {
            transactions.execute {
                val member = members.findByIdentity(identity.id)
                    ?: throw AuthenticationFailed(null, "Credenciales no válidas")

                identities.save(identity.copy(lastLoginAt = clock.instant(), updatedAt = clock.instant()))
                sessions.issueFor(identity.id, member)
            }!!
        }
    }

    private companion object {
        /**
         * Un hash de Argon2id con la misma forma que los reales, para que
         * comprobarlo cueste lo mismo. No corresponde a ninguna contrasena
         * conocida, asi que no puede validar nada.
         */
        const val DISCARD_HASH =
            "{argon2}\$argon2id\$v=19\$m=19456,t=2,p=1\$c29tZXNhbHRzb21lc2FsdA\$" +
                "RdescudvJCsgt3ub+b+dWRWJTmaaJObG"
    }
}

/**
 * Renovar la sesion rotando el refresh token.
 *
 * La rotacion es lo que convierte un refresh robado en algo detectable: en
 * cuanto el legitimo lo use, el del atacante ya no vale, y al reves.
 */
@Service
class RefreshSession(
    private val refreshTokens: RefreshTokenRepository,
    private val identities: IdentityRepository,
    private val members: HouseholdMemberRepository,
    private val secrets: SecretGenerator,
    private val tenantResolver: TenantResolver,
    private val tenantContext: TenantContext,
    private val transactions: TransactionTemplate,
    private val sessions: SessionIssuer,
    private val clock: Clock,
) {

    fun handle(refreshToken: String): TokenPair {
        val now = clock.instant()
        val stored = refreshTokens.findByTokenHash(secrets.hash(refreshToken))
            ?: throw AuthenticationFailed(null, "Refresh token no válido")

        if (!stored.isUsableAt(now)) {
            throw AuthenticationFailed(null, "Refresh token no válido")
        }

        val identity = identities.findById(stored.identityId)
            ?.takeIf { it.canAuthenticate }
            ?: throw AuthenticationFailed(null, "Refresh token no válido")

        val householdId = tenantResolver.householdOfActiveMember(identity.id)
            ?: throw AuthenticationFailed(null, "Refresh token no válido")

        return tenantContext.runAs(householdId) {
            transactions.execute {
                val member = members.findByIdentity(identity.id)
                    ?: throw AuthenticationFailed(null, "Refresh token no válido")

                refreshTokens.revoke(stored.id, now)
                sessions.issueFor(identity.id, member)
            }!!
        }
    }
}

/**
 * Cerrar sesion.
 *
 * Revoca el refresh token, que es lo unico revocable: un access token no se
 * puede retirar y sigue valiendo hasta que caduque, unos quince minutos.
 *
 * Responde igual con un token valido que con uno que ya no existe, asi que no
 * lanza nada: no hay nada que averiguar por la respuesta.
 */
@Service
class RevokeSession(
    private val refreshTokens: RefreshTokenRepository,
    private val secrets: SecretGenerator,
    private val clock: Clock,
) {

    fun handle(refreshToken: String) {
        val stored = refreshTokens.findByTokenHash(secrets.hash(refreshToken)) ?: return
        refreshTokens.revoke(stored.id, clock.instant())
    }
}
