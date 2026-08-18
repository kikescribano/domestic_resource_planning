package com.drp.core.application

import com.drp.core.application.port.AccessTokenIssuer
import com.drp.core.application.port.RefreshTokenRepository
import com.drp.core.application.port.SecretGenerator
import com.drp.core.application.port.SessionClaims
import com.drp.core.domain.household.HouseholdMember
import com.drp.core.domain.token.SingleUseToken
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.util.UUID

/** Cuanto vive un refresh token. Es revocable, asi que puede durar mucho mas que el access. */
data class SessionPolicy(val refreshTokenTtl: Duration)

/** Lo que la API devuelve al abrir sesion. El refresh viaja **en claro una sola vez**. */
data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
)

/**
 * Emite el par de tokens.
 *
 * Lo usan los cinco caminos que abren sesion --login, verificacion de correo,
 * aceptacion de invitacion, restablecimiento de contrasena y renovacion-- y
 * existe para que la forma de la sesion se decida en un solo sitio. Cinco copias
 * de esto serian cinco sitios donde olvidarse de persistir el refresh hasheado.
 */
@Service
class SessionIssuer(
    private val accessTokens: AccessTokenIssuer,
    private val secrets: SecretGenerator,
    private val refreshTokens: RefreshTokenRepository,
    private val policy: SessionPolicy,
    private val clock: Clock,
) {
    fun issueFor(identityId: UUID, member: HouseholdMember): TokenPair {
        // El refresh se emite primero porque su identificador viaja dentro del
        // access token: es lo que deja saber despues **cual** de las sesiones de
        // esta persona es la que esta en uso.
        //
        // Del refresh se guarda el hash, nunca el valor. Si la base de datos se
        // filtrase, lo que hay dentro no sirve para abrir ninguna sesion.
        val refresh = secrets.generate()
        val sessionId = UUID.randomUUID()
        refreshTokens.save(
            SingleUseToken(
                id = sessionId,
                identityId = identityId,
                tokenHash = refresh.hash,
                expiresAt = clock.instant().plus(policy.refreshTokenTtl),
                usedAt = null,
            ),
        )

        val access = accessTokens.issue(
            SessionClaims(
                identityId = identityId,
                memberId = member.id,
                householdId = member.householdId,
                role = member.role,
                sessionId = sessionId,
            ),
        )

        return TokenPair(
            accessToken = access.token,
            refreshToken = refresh.plain,
            expiresIn = access.expiresInSeconds,
        )
    }
}
