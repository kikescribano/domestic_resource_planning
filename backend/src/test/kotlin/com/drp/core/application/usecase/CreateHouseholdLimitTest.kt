package com.drp.core.application.usecase

import com.drp.core.application.EnrollmentEmails
import com.drp.core.application.port.CategoryRepository
import com.drp.core.application.port.EmailVerificationTokenRepository
import com.drp.core.application.port.HouseholdMemberRepository
import com.drp.core.application.port.HouseholdRepository
import com.drp.core.application.port.IdentityRepository
import com.drp.core.application.port.IssuedSecret
import com.drp.core.application.port.PasswordHasher
import com.drp.core.application.port.SecretGenerator
import com.drp.core.application.port.SeededCategory
import com.drp.core.domain.catalog.Category
import com.drp.core.domain.household.Household
import com.drp.core.domain.household.HouseholdMember
import com.drp.core.domain.identity.Identity
import com.drp.core.domain.identity.PasswordPolicy
import com.drp.core.domain.token.SingleUseToken
import com.drp.platform.error.BusinessRuleViolation
import com.drp.platform.error.ErrorCode
import com.drp.platform.mail.EmailAddress
import com.drp.platform.mail.EmailMessage
import com.drp.platform.mail.EmailSender
import com.drp.platform.tenant.HouseholdDirectory
import com.drp.platform.tenant.TenantContext
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/**
 * **El tope de hogares de la instalacion** (EnrollmentPolicy): la cota de
 * dimensionado que el despliegue fija y el desarrollo deja a cero.
 *
 * La propiedad que de verdad importa no es que el alta de mas falle --eso lo
 * afirmaria cualquier prueba-- sino **como** falla: con el tope alcanzado la
 * respuesta es la misma exista o no el correo, y esa rama no toca nada --ni
 * hashea, ni busca la identidad, ni escribe una fila, ni manda un correo--. Si
 * tocara, el 409 dejaria de ser identico para todo el mundo y volveria el
 * oraculo que `CreateHouseholdHashesBothWaysTest` guarda por el otro flanco.
 *
 * Unitaria y con dobles por lo mismo que aquella: contar llamadas a un bean del
 * contexto estrenaria contexto de Spring, y aqui no hace falta ninguno. Los
 * dobles de esta rama **revientan si se les llama**, asi que terminar ya es la
 * afirmacion.
 */
class CreateHouseholdLimitTest {

    @Test
    @DisplayName("con el tope alcanzado el alta responde HOUSEHOLD_LIMIT_REACHED sin tocar nada")
    fun `el alta de mas se rechaza sin hashear ni escribir`() {
        val untouched = UntouchableEverything()

        val failure = shouldThrow<BusinessRuleViolation> {
            createHousehold(untouched, maxHouseholds = 2, existing = 2).handle(command())
        }

        failure.code shouldBe ErrorCode.HOUSEHOLD_LIMIT_REACHED
        untouched.outbox.size shouldBe 0
    }

    @Test
    @DisplayName("cero es sin tope, por muchos hogares que haya")
    fun `sin tope el alta pasa`() {
        val montage = UntouchableEverything(tolerant = true)

        createHousehold(montage, maxHouseholds = 0, existing = 500).handle(command())

        // El unico correo es el de verificacion del hogar recien creado.
        montage.outbox.size shouldBe 1
    }

    @Test
    @DisplayName("por debajo del tope el alta pasa")
    fun `el ultimo hueco se puede ocupar`() {
        val montage = UntouchableEverything(tolerant = true)

        createHousehold(montage, maxHouseholds = 3, existing = 2).handle(command())

        montage.outbox.size shouldBe 1
    }

    // -----------------------------------------------------------------------
    // El montaje
    // -----------------------------------------------------------------------

    private fun createHousehold(
        montage: UntouchableEverything,
        maxHouseholds: Int,
        existing: Int,
    ) = CreateHousehold(
        identities = montage.identities,
        households = montage.households,
        members = montage.members,
        categories = montage.categories,
        verificationTokens = montage.tokens,
        passwords = montage.passwords,
        passwordPolicy = PasswordPolicy(emptySet()),
        secrets = FixedSecrets,
        emails = EnrollmentEmails("https://drp.test"),
        emailSender = montage.sender,
        tenantContext = PassThroughTenantContext,
        transactions = TransactionTemplate(NoOpTransactionManager),
        clock = Clock.fixed(Instant.parse("2026-08-21T10:00:00Z"), ZoneOffset.UTC),
        policy = EnrollmentPolicy(maxHouseholds),
        directory = FixedDirectory(existing),
    )

    private fun command() = CreateHouseholdCommand(
        name = "Casa",
        timeZone = "Europe/Madrid",
        adminName = "Kike",
        adminEmail = "nuevo@example.test",
        adminPassword = "el gato duerme en el sofa",
    )

    /** Tantos hogares como se pidan; los identificadores dan igual. */
    private class FixedDirectory(private val count: Int) : HouseholdDirectory {
        override fun allHouseholdIds(): List<UUID> = List(count) { UUID.randomUUID() }
    }

    /**
     * Todo lo que la rama del tope alcanzado no puede tocar, junto. Con
     * `tolerant` se vuelve el montaje callado de las ramas que si escriben.
     */
    private class UntouchableEverything(private val tolerant: Boolean = false) {
        val outbox = mutableListOf<EmailMessage>()

        private fun forbidden(what: String): Nothing = error("La rama del tope alcanzado $what")

        val passwords = object : PasswordHasher {
            override fun hash(rawPassword: String): String =
                if (tolerant) "un-hash" else forbidden("hasheo una contrasena")

            override fun matches(rawPassword: String, hash: String) = false
        }

        val identities = object : IdentityRepository {
            override fun save(identity: Identity) = if (tolerant) identity else forbidden("guardo una identidad")
            override fun findById(identityId: UUID): Identity? = null
            override fun findByEmail(email: EmailAddress): Identity? =
                if (tolerant) null else forbidden("busco un correo")

            override fun delete(identityId: UUID) = Unit
        }

        val households = object : HouseholdRepository {
            override fun save(household: Household) = if (tolerant) household else forbidden("creo un hogar")
            override fun findCurrent(): Household? = null
            override fun lockCurrent() = Unit
            override fun deleteCurrent() = Unit
        }

        val members = object : HouseholdMemberRepository {
            override fun save(member: HouseholdMember) = if (tolerant) member else forbidden("creo una pertenencia")
            override fun findById(memberId: UUID): HouseholdMember? = null
            override fun findByIdentity(identityId: UUID): HouseholdMember? = null
            override fun list(includeDeactivated: Boolean, pagination: com.drp.platform.page.Pagination) =
                error("no se usa")

            override fun countActiveAdmins(): Long = 0
        }

        val categories = object : CategoryRepository {
            override fun seed(categories: List<SeededCategory>, at: Instant) =
                if (tolerant) Unit else forbidden("sembro categorias")

            override fun countCurrent(): Long = 0
            override fun save(category: Category): Category = error("no se usa")
            override fun findById(categoryId: UUID): Category? = null
            override fun findLiveByName(name: String): Category? = null
            override fun list(includeRetired: Boolean, pagination: com.drp.platform.page.Pagination) =
                error("no se usa")
        }

        val tokens = object : EmailVerificationTokenRepository {
            override fun save(token: SingleUseToken) =
                if (tolerant) token else forbidden("emitio un token de verificacion")

            override fun findByTokenHash(tokenHash: String): SingleUseToken? = null
            override fun markAllUsedForIdentity(identityId: UUID, at: Instant) = Unit
        }

        val sender = object : EmailSender {
            override fun send(message: EmailMessage) {
                if (!tolerant) forbidden("mando un correo")
                outbox += message
            }
        }
    }

    private object FixedSecrets : SecretGenerator {
        override fun generate() = IssuedSecret(plain = "token-en-claro", hash = "token-hasheado")
        override fun hash(plain: String) = "token-hasheado"
    }

    private object PassThroughTenantContext : TenantContext {
        override fun currentHousehold(): UUID? = null
        override fun <T> runAs(householdId: UUID?, block: () -> T): T = block()
    }

    private object NoOpTransactionManager : PlatformTransactionManager {
        override fun getTransaction(definition: TransactionDefinition?): TransactionStatus = SimpleTransactionStatus()
        override fun commit(status: TransactionStatus) = Unit
        override fun rollback(status: TransactionStatus) = Unit
    }
}
