package com.drp.core.adapter.security

import com.drp.core.application.port.AccessTokenIssuer
import com.drp.core.application.port.IssuedAccessToken
import com.drp.core.application.port.IssuedLoanToken
import com.drp.core.application.port.IssuedSecret
import com.drp.core.application.port.LoanTokenClaims
import com.drp.core.application.port.LoanTokenIssuer
import com.drp.core.application.port.PasswordHasher
import com.drp.core.application.port.SecretGenerator
import com.drp.core.application.port.SessionClaims
import com.drp.core.domain.household.MemberRole
import com.drp.core.domain.loan.LoanRole
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
import java.time.Instant
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
 * El token acotado de un externo: JWT firmado **sin `sub`**.
 *
 * Comparte la clave de firma con el access token a proposito. Son dos
 * credenciales del mismo emisor y separarlas anadiria un segundo secreto de
 * despliegue que gestionar, sin ganar nada: lo que impide que uno se haga pasar
 * por el otro no es la clave sino la forma del cuerpo --este no tiene `sub`,
 * `memberId` ni `householdId`, y aquel no tiene `loanId`-- y que cada verificador
 * exija los claims que le tocan y rechace lo demas.
 *
 * A diferencia del access token, este **si es revocable**, y no por si mismo: lo
 * es porque su hash queda en `loan_access_tokens`. De ahi que [issue] devuelva
 * las dos cosas.
 */
@Component
class JwtLoanTokenIssuer(
    private val properties: SecurityProperties,
    private val secrets: SecretGenerator,
    private val clock: Clock,
) : LoanTokenIssuer {

    private val signer = MACSigner(properties.jwtSecretBytes())
    private val verifier = MACVerifier(properties.jwtSecretBytes())

    override fun issue(claims: LoanTokenClaims, expiresAt: Instant): IssuedLoanToken {
        val claimSet = JWTClaimsSet.Builder()
            // Sin subject: quien lo trae no es un usuario. Lo que hay es un
            // prestamo y un extremo.
            .claim(LOAN_ID, claims.loanId.toString())
            .claim(ROLE, claims.role.name)
            .issueTime(Date.from(clock.instant()))
            .expirationTime(Date.from(expiresAt))
            .build()

        val jwt = SignedJWT(JWSHeader(JWSAlgorithm.HS256), claimSet).apply { sign(signer) }.serialize()

        return IssuedLoanToken(token = jwt, tokenHash = hash(jwt))
    }

    override fun verify(token: String): LoanTokenClaims? = runCatching {
        val jwt = SignedJWT.parse(token)
        if (!jwt.verify(verifier)) return null

        val claims = jwt.jwtClaimsSet
        val expiry = claims.expirationTime ?: return null
        if (!expiry.toInstant().isAfter(clock.instant())) return null

        // Un access token valido **no** puede colarse por aqui: lleva `sub` y no
        // lleva `loanId`, asi que se rechaza por las dos. Comprobar las dos cosas
        // y no solo la que falta es lo que mantiene la separacion aunque manana
        // alguien anada un claim a la otra credencial.
        if (claims.subject != null) return null

        LoanTokenClaims(
            loanId = UUID.fromString(claims.getStringClaim(LOAN_ID)),
            role = LoanRole.valueOf(claims.getStringClaim(ROLE)),
        )
    }.getOrNull()

    /**
     * El mismo SHA-256 que los demas secretos, para que la tabla guarde lo mismo
     * que las otras tres de tokens y una comparacion no dependa del algoritmo con
     * que se escribio la fila.
     */
    override fun hash(token: String): String = secrets.hash(token)

    private companion object {
        const val LOAN_ID = "loanId"
        const val ROLE = "role"
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
    /**
     * Charset explicito. Con el de la plataforma, la misma clave podria dar
     * bytes distintos en dos maquinas y los tokens firmados en una no valdrian
     * en la otra.
     */
    fun jwtSecretBytes(): ByteArray = jwtSecret.toByteArray(Charsets.UTF_8)

    /**
     * @param developmentEnvironment si el arranque es de desarrollo o de
     *   pruebas, unicos sitios donde se tolera la clave de ejemplo.
     */
    fun validate(developmentEnvironment: Boolean) {
        // HS256 exige una clave de al menos 256 bits. Con una mas corta Nimbus
        // falla al firmar, y hacerlo aqui convierte un error en la primera
        // peticion en un error al arrancar, que es cuando se puede corregir.
        require(jwtSecretBytes().size >= MINIMUM_SECRET_BYTES) {
            "La clave de firma del JWT necesita al menos $MINIMUM_SECRET_BYTES bytes para HS256"
        }

        // Y la comprobacion que de verdad importa. La clave de ejemplo del
        // application.yml mide 43 bytes, asi que pasaba de sobra el minimo de
        // longitud: un despliegue que olvidara DRP_JWT_SECRET arrancaba sin un
        // solo aviso, firmando con una clave **publicada en el repositorio**.
        //
        // Y no seria un fallo cualquiera. El householdId del token es lo que
        // alimenta el TenantContext y con el el app.household_id de las
        // politicas, asi que quien pudiera forjar un token no atravesaria una
        // capa de aislamiento sino las dos a la vez: la segunda obedeceria al
        // identificador que el atacante hubiese escrito.
        require(developmentEnvironment || jwtSecret != DEVELOPMENT_SECRET) {
            "La clave de firma del JWT sigue siendo la de desarrollo. " +
                "Define DRP_JWT_SECRET con un secreto propio antes de arrancar."
        }
    }

    companion object {
        const val MINIMUM_SECRET_BYTES = 32

        /**
         * El valor por defecto del `application.yml`. Vive aqui para que el
         * arranque pueda reconocerlo y rechazarlo fuera de desarrollo; si se
         * cambia alli, hay que cambiarlo aqui.
         */
        const val DEVELOPMENT_SECRET = "desarrollo-local-no-usar-en-produccion-32b+"
    }
}
