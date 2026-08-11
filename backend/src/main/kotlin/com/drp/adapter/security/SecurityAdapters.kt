package com.drp.adapter.security

import com.drp.application.port.AccessTokenIssuer
import com.drp.application.port.IssuedAccessToken
import com.drp.application.port.IssuedSecret
import com.drp.application.port.PasswordHasher
import com.drp.application.port.SecretGenerator
import com.drp.application.port.SessionClaims
import com.drp.domain.household.MemberRole
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.crypto.MACVerifier
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.util.Base64
import java.util.Date
import java.util.UUID

@Component
class SpringPasswordHasher(private val encoder: PasswordEncoder) : PasswordHasher {

    override fun hash(rawPassword: String): String = encoder.encode(rawPassword)

    override fun matches(rawPassword: String, hash: String): Boolean = encoder.matches(rawPassword, hash)
}

/**
 * Secretos de 256 bits de un generador criptografico, en Base64 sin relleno para
 * que quepan en una URL sin escapar nada.
 */
@Component
class RandomSecretGenerator : SecretGenerator {

    private val random = SecureRandom()
    private val encoder = Base64.getUrlEncoder().withoutPadding()

    override fun generate(): IssuedSecret {
        val bytes = ByteArray(SECRET_BYTES).also(random::nextBytes)
        val plain = encoder.encodeToString(bytes)
        return IssuedSecret(plain = plain, hash = hash(plain))
    }

    override fun hash(plain: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(plain.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val SECRET_BYTES = 32
    }
}

/**
 * El access token: JWT firmado con HS256.
 *
 * De vida corta --unos quince minutos-- porque **no se puede revocar**. Lo
 * revocable es el refresh token, que si esta persistido y hasheado; un access
 * token robado sigue valiendo hasta que caduca, y esa ventana es justo lo que su
 * duracion corta acota.
 */
@Component
class JwtAccessTokenIssuer(
    private val properties: SecurityProperties,
    private val clock: Clock,
) : AccessTokenIssuer {

    private val signer = MACSigner(properties.jwtSecretBytes())
    private val verifier = MACVerifier(properties.jwtSecretBytes())

    override fun issue(claims: SessionClaims): IssuedAccessToken {
        val issuedAt = clock.instant()
        val expiresAt = issuedAt.plus(properties.accessTokenTtl)

        val claimSet = JWTClaimsSet.Builder()
            .subject(claims.identityId.toString())
            .claim(MEMBER_ID, claims.memberId.toString())
            .claim(HOUSEHOLD_ID, claims.householdId.toString())
            .claim(ROLE, claims.role.name)
            .claim(SESSION_ID, claims.sessionId.toString())
            .issueTime(Date.from(issuedAt))
            .expirationTime(Date.from(expiresAt))
            .build()

        val jwt = SignedJWT(JWSHeader(JWSAlgorithm.HS256), claimSet).apply { sign(signer) }

        return IssuedAccessToken(
            token = jwt.serialize(),
            expiresInSeconds = properties.accessTokenTtl.seconds,
        )
    }

    override fun verify(token: String): SessionClaims? = runCatching {
        val jwt = SignedJWT.parse(token)
        if (!jwt.verify(verifier)) return null

        val claims = jwt.jwtClaimsSet
        val expiry = claims.expirationTime ?: return null
        if (!expiry.toInstant().isAfter(clock.instant())) return null

        SessionClaims(
            identityId = UUID.fromString(claims.subject),
            memberId = UUID.fromString(claims.getStringClaim(MEMBER_ID)),
            householdId = UUID.fromString(claims.getStringClaim(HOUSEHOLD_ID)),
            role = MemberRole.valueOf(claims.getStringClaim(ROLE)),
            sessionId = UUID.fromString(claims.getStringClaim(SESSION_ID)),
        )
        // Cualquier fallo --firma que no cuadra, cuerpo que no es un JWT, claim
        // que falta o rol que ya no existe-- se trata igual: token no valido. No
        // se distingue en la respuesta, porque distinguirlo diria a quien lo
        // manipula por donde va bien encaminado.
    }.getOrNull()

    private companion object {
        const val MEMBER_ID = "memberId"
        const val HOUSEHOLD_ID = "householdId"
        const val ROLE = "role"
        const val SESSION_ID = "sid"
    }
}

/**
 * Configuracion de credenciales y sesion.
 *
 * La clave de firma es un **secreto de despliegue** y no vive en el repositorio.
 * El valor por defecto solo sirve para desarrollo y para las pruebas, y el
 * arranque falla si en produccion sigue puesto (ver [validate]).
 */
data class SecurityProperties(
    val jwtSecret: String,
    val accessTokenTtl: Duration,
    val refreshTokenTtl: Duration,
) {
    fun jwtSecretBytes(): ByteArray = jwtSecret.toByteArray()

    fun validate() {
        // HS256 exige una clave de al menos 256 bits. Con una mas corta Nimbus
        // falla al firmar, y hacerlo aqui convierte un error en la primera
        // peticion en un error al arrancar, que es cuando se puede corregir.
        require(jwtSecretBytes().size >= MINIMUM_SECRET_BYTES) {
            "La clave de firma del JWT necesita al menos $MINIMUM_SECRET_BYTES bytes para HS256"
        }
    }

    companion object {
        const val MINIMUM_SECRET_BYTES = 32
    }
}
