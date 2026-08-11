package com.drp.application.usecase

import com.drp.application.EnrollmentEmails
import com.drp.application.SessionIssuer
import com.drp.application.TokenPair
import com.drp.application.port.CategoryRepository
import com.drp.application.port.EmailSender
import com.drp.application.port.EmailVerificationTokenRepository
import com.drp.application.port.HouseholdMemberRepository
import com.drp.application.port.HouseholdRepository
import com.drp.application.port.IdentityRepository
import com.drp.application.port.PasswordHasher
import com.drp.application.port.SecretGenerator
import com.drp.application.port.TenantResolver
import com.drp.application.tenant.TenantContext
import com.drp.domain.BusinessRuleViolation
import com.drp.domain.ErrorCode
import com.drp.domain.ValidationFailure
import com.drp.domain.household.Household
import com.drp.domain.household.HouseholdMember
import com.drp.domain.household.MemberRole
import com.drp.domain.identity.EmailAddress
import com.drp.domain.identity.Identity
import com.drp.domain.identity.PasswordPolicy
import com.drp.domain.identity.PasswordValidation
import com.drp.domain.token.SingleUseToken
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.DateTimeException
import java.time.ZoneId
import java.util.UUID

/**
 * Las categorias con las que arranca todo hogar (README 4.1.1).
 *
 * Son **datos** que se le muestran al usuario y que edita a su gusto, no valores
 * de un enumerado, asi que van en castellano y no siguen la regla de nombrar en
 * ingles.
 */
val DEFAULT_CATEGORIES = listOf("Mobiliario", "Alimentación", "Limpieza", "Herramientas", "Decoración")

data class CreateHouseholdCommand(
    val name: String,
    val timeZone: String,
    val adminName: String,
    val adminEmail: String,
    val adminPassword: String,
)

/**
 * El alta de un hogar: la unica escritura sin credencial alguna de la API.
 *
 * Dos cosas se derivan de que el endpoint sea anonimo, y las dos estan
 * implementadas aqui y no en el controlador, porque son reglas y no forma:
 *
 * 1. **No puede delatar quien esta registrado.** Responde igual exista o no el
 *    correo. Quien se entera de lo que ha pasado es el dueno de la direccion, por
 *    correo.
 * 2. **No puede delatarlo por el tiempo tampoco**, que es la parte que se olvida:
 *    ver el comentario sobre el hash mas abajo.
 */
@Service
class CreateHousehold(
    private val identities: IdentityRepository,
    private val households: HouseholdRepository,
    private val members: HouseholdMemberRepository,
    private val categories: CategoryRepository,
    private val verificationTokens: EmailVerificationTokenRepository,
    private val passwords: PasswordHasher,
    private val passwordPolicy: PasswordPolicy,
    private val secrets: SecretGenerator,
    private val emails: EnrollmentEmails,
    private val emailSender: EmailSender,
    private val tenantContext: TenantContext,
    private val transactions: TransactionTemplate,
    private val clock: Clock,
) {

    fun handle(command: CreateHouseholdCommand) {
        val email = EmailAddress.of(command.adminEmail)
        val timeZone = command.timeZone.toZoneId()
        passwordPolicy.require(command.adminPassword)

        // Se hashea SIEMPRE, incluso cuando el correo ya existe y no se va a
        // crear nada. Argon2id domina el coste de esta operacion --19 MiB y dos
        // pasadas-- asi que saltarselo en la rama "el correo ya existe" haria
        // que esa rama respondiera mucho antes, y la duracion de la respuesta
        // pasaria a ser el oraculo que el codigo de estado constante evita.
        val passwordHash = passwords.hash(command.adminPassword)

        val message = if (identities.findByEmail(email) != null) {
            emails.householdAttemptOnExistingAddress(email)
        } else {
            create(command, email, timeZone, passwordHash)
        }

        // Fuera de la transaccion (ADR-009): un fallo de entrega no puede
        // deshacer un alta que ya esta persistida y cuyo token sigue siendo
        // valido.
        emailSender.send(message)
    }

    private fun create(
        command: CreateHouseholdCommand,
        email: EmailAddress,
        timeZone: ZoneId,
        passwordHash: String,
    ) = run {
        val now = clock.instant()
        // El identificador lo genera la aplicacion ANTES de insertar nada. Es lo
        // que permite que este caso de uso no necesite ninguna excepcion al
        // aislamiento: la transaccion fija app.household_id con este valor y
        // todo lo que se escribe dentro nace ya dentro del contexto del
        // inquilino, incluida la fila del propio hogar.
        val householdId = UUID.randomUUID()
        val identityId = UUID.randomUUID()
        val memberId = UUID.randomUUID()
        val verification = secrets.generate()

        tenantContext.runAs(householdId) {
            transactions.execute {
                identities.save(
                    Identity(
                        id = identityId,
                        name = command.adminName,
                        email = email,
                        phone = null,
                        passwordHash = passwordHash,
                        // Sin verificar. Mientras lo este no se puede iniciar
                        // sesion, y el hogar no sirve para nada.
                        emailVerifiedAt = null,
                        lastLoginAt = null,
                        createdAt = now,
                        updatedAt = now,
                        deactivatedAt = null,
                    ),
                )

                households.save(
                    Household(
                        id = householdId,
                        name = command.name,
                        timeZone = timeZone,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )

                members.save(
                    HouseholdMember(
                        id = memberId,
                        householdId = householdId,
                        identityId = identityId,
                        role = MemberRole.HOUSEHOLD_ADMIN,
                        createdAt = now,
                        updatedAt = now,
                        deactivatedAt = null,
                        // Se atribuye a si misma: la pertenencia la crea la
                        // persona que abre el hogar, que en ese instante no
                        // tiene otra. Nulo esta reservado para lo que hace el
                        // sistema sin que haya nadie detras.
                        createdBy = memberId,
                        updatedBy = null,
                    ),
                )

                // Sembradas por el sistema, de ahi que no lleven autoria.
                categories.seed(DEFAULT_CATEGORIES, now)

                verificationTokens.save(
                    SingleUseToken(
                        id = UUID.randomUUID(),
                        identityId = identityId,
                        tokenHash = verification.hash,
                        expiresAt = now.plus(SingleUseToken.VERIFICATION_LIFETIME),
                        usedAt = null,
                    ),
                )
            }
        }

        emails.verification(email, command.name, verification.plain)
    }
}

/**
 * Consumir el token de verificacion. Es el momento en que el hogar pasa a ser
 * utilizable, y por tanto el momento en que se devuelve sesion.
 */
@Service
class VerifyEmail(
    private val verificationTokens: EmailVerificationTokenRepository,
    private val identities: IdentityRepository,
    private val members: HouseholdMemberRepository,
    private val tenantResolver: TenantResolver,
    private val tenantContext: TenantContext,
    private val transactions: TransactionTemplate,
    private val sessions: SessionIssuer,
    private val secrets: SecretGenerator,
    private val clock: Clock,
) {

    fun handle(token: String): TokenPair {
        val now = clock.instant()
        val stored = verificationTokens.findByTokenHash(secrets.hash(token))

        // Caducado, ya usado o inexistente son el mismo error a proposito:
        // distinguirlos diria a quien prueba tokens cual de ellos existio.
        if (stored == null || !stored.isUsableAt(now)) {
            throw BusinessRuleViolation(ErrorCode.VERIFICATION_TOKEN_INVALID, "Token de verificación no válido")
        }

        val identity = identities.findById(stored.identityId)
            ?: throw BusinessRuleViolation(ErrorCode.VERIFICATION_TOKEN_INVALID, "Token de verificación no válido")

        val householdId = tenantResolver.householdOfActiveMember(identity.id)
            ?: throw BusinessRuleViolation(ErrorCode.VERIFICATION_TOKEN_INVALID, "Token de verificación no válido")

        return tenantContext.runAs(householdId) {
            transactions.execute {
                identities.save(identity.copy(emailVerifiedAt = now, updatedAt = now))
                verificationTokens.markAllUsedForIdentity(identity.id, now)

                val member = members.findByIdentity(identity.id)
                    ?: throw BusinessRuleViolation(
                        ErrorCode.VERIFICATION_TOKEN_INVALID,
                        "Token de verificación no válido",
                    )

                sessions.issueFor(identity.id, member)
            }!!
        }
    }
}

/**
 * Reenviar el enlace de verificacion, para cuando caduco o se perdio.
 *
 * Responde igual exista o no la identidad, por el mismo motivo que el alta.
 */
@Service
class ResendVerification(
    private val identities: IdentityRepository,
    private val verificationTokens: EmailVerificationTokenRepository,
    private val households: HouseholdRepository,
    private val tenantResolver: TenantResolver,
    private val tenantContext: TenantContext,
    private val transactions: TransactionTemplate,
    private val secrets: SecretGenerator,
    private val emails: EnrollmentEmails,
    private val emailSender: EmailSender,
    private val clock: Clock,
) {

    fun handle(rawEmail: String) {
        val email = EmailAddress.of(rawEmail)
        val identity = identities.findByEmail(email) ?: return
        if (identity.isVerified || !identity.isActive) return

        val householdId = tenantResolver.householdOfActiveMember(identity.id) ?: return
        val now = clock.instant()
        val verification = secrets.generate()

        val householdName = tenantContext.runAs(householdId) {
            transactions.execute {
                // Emitir uno nuevo invalida el anterior. Sin esto quedarian
                // varios tokens vivos para la misma identidad y bastaria con que
                // uno se filtrase.
                verificationTokens.markAllUsedForIdentity(identity.id, now)
                verificationTokens.save(
                    SingleUseToken(
                        id = UUID.randomUUID(),
                        identityId = identity.id,
                        tokenHash = verification.hash,
                        expiresAt = now.plus(SingleUseToken.VERIFICATION_LIFETIME),
                        usedAt = null,
                    ),
                )
                households.findCurrent()?.name
            }
        } ?: return

        emailSender.send(emails.verification(email, householdName, verification.plain))
    }
}

/**
 * La regla de forma de las contrasenas, aplicada en los **cuatro** puntos donde
 * se fija una: al crear un hogar, al aceptar una invitacion, al restablecerla y
 * al cambiarla estando dentro.
 */
fun PasswordPolicy.require(rawPassword: String) {
    when (validate(rawPassword)) {
        PasswordValidation.Valid -> Unit
        PasswordValidation.TooShort -> throw ValidationFailure(
            mapOf("password" to "La contraseña debe tener al menos ${PasswordPolicy.MIN_LENGTH} caracteres"),
        )
        PasswordValidation.TooCommon -> throw ValidationFailure(
            mapOf("password" to "Esa contraseña es demasiado común; elige otra"),
        )
    }
}

/** El huso se valida aqui y no como `CHECK`: la lista cambia con el tiempo. */
fun String.toZoneId(): ZoneId = try {
    ZoneId.of(this)
} catch (invalid: DateTimeException) {
    throw ValidationFailure(mapOf("timeZone" to "Zona horaria no válida: ${invalid.message}"))
}
