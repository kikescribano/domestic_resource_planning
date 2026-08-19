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
import com.drp.core.domain.catalog.Category
import com.drp.core.domain.household.Household
import com.drp.core.domain.household.HouseholdMember
import com.drp.core.domain.identity.Identity
import com.drp.core.domain.identity.PasswordPolicy
import com.drp.core.domain.token.SingleUseToken
import com.drp.platform.mail.EmailAddress
import com.drp.platform.mail.EmailMessage
import com.drp.platform.mail.EmailSender
import com.drp.platform.tenant.TenantContext
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
 * **Que el alta de un hogar pague el hash en las dos ramas**, contado en lugar de
 * cronometrado.
 *
 * La propiedad es esta, y es de seguridad: `POST /households` responde igual
 * exista o no el correo, pero si la rama «ese correo ya existe» se saltara el
 * Argon2id respondería **muchísimo antes** —19 MiB y dos pasadas es lo que domina
 * esa operación— y la duración de la respuesta pasaría a ser el oráculo que el
 * código de estado constante evita. Es la única forma conocida de reabrir la fuga.
 *
 * ## Por qué esta prueba existe, y qué sustituye
 *
 * Hasta el Hito 6 de la Fase 2 la propiedad se comprobaba **con un reloj**, en
 * `EnrollmentJourneyTest`: se medía la mediana de cada rama y se exigía que
 * ninguna bajara del coste de un hash. Era la única prueba de la suite que
 * cronometraba, y **falló dos veces en la CI sin que nada hubiera cambiado en el
 * código**: la primera por un umbral absoluto de 60 ms —que medía la velocidad
 * del disco del runner— y la segunda, ya con la comparación en proporción, en la
 * mitad que compara contra el coste de un hash.
 *
 * Las dos veces la respuesta fue aflojar el umbral, y ese camino no tiene final:
 * un umbral que se relaja cada vez que el runner va lento acaba pasando siempre,
 * y entonces la propiedad ya no está comprobada aunque la prueba siga en verde.
 *
 * **Lo que estaba mal no era el umbral sino el instrumento.** El reloj mide el
 * *síntoma* —esta rama tarda lo mismo que aquella— sobre una máquina compartida
 * que introduce dos órdenes de ruido: en el equipo de desarrollo, dos pasadas
 * seguidas del mismo hash dan 32 ms y 87 ms. Lo que de verdad se quiere afirmar
 * es la *causa*, y la causa se cuenta: **`hash` se llama exactamente una vez,
 * pasen las dos ramas por donde pasen**.
 *
 * Contarlo es determinista, no depende de ninguna máquina y **falla antes**: el
 * día que alguien escriba un `if (existe) return` delante del hash, esta prueba
 * se pone roja en el acto, mientras que la del reloj necesitaba que la diferencia
 * fuera lo bastante grande como para salir del ruido.
 *
 * ## Por qué es unitaria y sin Spring
 *
 * Contar llamadas a un bean del contexto exigiría sustituirlo, y sustituirlo
 * —`@MockitoBean`, un `@TestConfiguration`, un `@Primary`— **estrena contexto de
 * Spring**, que es justo lo que esta suite no puede permitirse: cada combinación
 * nueva reserva un pool que no se suelta mientras siga en caché, y eso ya tumbó
 * tres pruebas de otros hitos en la Fase 1. Aquí no hace falta ninguno: el caso
 * de uso se construye a mano con dobles, que además es donde la pirámide de
 * pruebas de la sección 7 pone una regla de dominio.
 *
 * Los dobles de lo que **no** debería tocarse en la rama del correo repetido
 * fallan si se les llama, así que esta prueba afirma de paso la otra mitad de la
 * regla: que esa rama **no escribe nada**.
 */
class CreateHouseholdHashesBothWaysTest {

    @Test
    @DisplayName("las dos ramas del alta pagan exactamente un hash")
    fun `el alta hashea exista o no el correo`() {
        val known = "yavive@example.test"

        val onNew = CountingHasher()
        createHousehold(onNew, existing = null).handle(command("nuevo@example.test"))

        val onKnown = CountingHasher()
        createHousehold(onKnown, existing = identityOf(known)).handle(command(known))

        // La comprobación entera, y es una igualdad y no un «mayor que cero»:
        // hashear dos veces en una rama sería tan delator como no hacerlo en la
        // otra, solo que al revés.
        onNew.calls shouldBe 1
        onKnown.calls shouldBe 1
    }

    @Test
    @DisplayName("y la rama del correo ya conocido no escribe ni una fila")
    fun `el alta sobre un correo existente no crea nada`() {
        val known = "yavive@example.test"
        val sent = mutableListOf<EmailMessage>()

        // Los repositorios de escritura de este montaje **revientan si alguien los
        // llama**, así que no hace falta afirmar nada más: llegar al final ya es
        // la afirmación.
        createHousehold(CountingHasher(), existing = identityOf(known), outbox = sent, forbidWrites = true)
            .handle(command(known))

        // Lo único que esa rama hace es avisar al dueño de la dirección, que es
        // quien tiene derecho a enterarse.
        sent.size shouldBe 1
        sent.single().to.value shouldBe known
    }

    // -----------------------------------------------------------------------
    // El montaje
    // -----------------------------------------------------------------------

    /**
     * `forbidWrites` es lo que convierte el montaje en la afirmacion de la
     * segunda prueba: con el puesto, los cuatro repositorios de escritura
     * revientan si alguien los llama. Va apagado por omision porque **la rama del
     * correo nuevo si escribe**, y ahi lo que se cuenta es otra cosa.
     */
    private fun createHousehold(
        passwords: PasswordHasher,
        existing: Identity?,
        outbox: MutableList<EmailMessage> = mutableListOf(),
        forbidWrites: Boolean = false,
    ) = CreateHousehold(
        identities = FakeIdentities(existing),
        households = if (forbidWrites) ForbiddenHouseholds else TolerantHouseholds,
        members = if (forbidWrites) ForbiddenMembers else TolerantMembers,
        categories = if (forbidWrites) ForbiddenCategories else TolerantCategories,
        verificationTokens = if (forbidWrites) ForbiddenTokens else TolerantTokens,
        passwords = passwords,
        passwordPolicy = PasswordPolicy(emptySet()),
        secrets = FixedSecrets,
        emails = EnrollmentEmails("https://drp.test"),
        emailSender = object : EmailSender {
            override fun send(message: EmailMessage) {
                outbox += message
            }
        },
        tenantContext = PassThroughTenantContext,
        transactions = TransactionTemplate(NoOpTransactionManager),
        clock = Clock.fixed(Instant.parse("2026-08-19T10:00:00Z"), ZoneOffset.UTC),
    )

    private fun command(email: String) = CreateHouseholdCommand(
        name = "Casa",
        timeZone = "Europe/Madrid",
        adminName = "Kike",
        adminEmail = email,
        adminPassword = "el gato duerme en el sofa",
    )

    private fun identityOf(email: String) = Identity(
        id = UUID.randomUUID(),
        name = "Quien ya estaba",
        email = EmailAddress.of(email),
        phone = null,
        passwordHash = "da igual",
        emailVerifiedAt = Instant.EPOCH,
        lastLoginAt = null,
        avatar = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        deactivatedAt = null,
    )

    /** El doble que hace de instrumento: cuenta, y devuelve algo distinto cada vez. */
    private class CountingHasher : PasswordHasher {
        var calls = 0
            private set

        override fun hash(rawPassword: String): String {
            calls++
            return "hash-$calls"
        }

        override fun matches(rawPassword: String, hash: String) = false
    }

    private class FakeIdentities(private val existing: Identity?) : IdentityRepository {
        override fun save(identity: Identity) = identity
        override fun findById(identityId: UUID): Identity? = null
        override fun findByEmail(email: EmailAddress): Identity? = existing?.takeIf { it.email == email }
        override fun delete(identityId: UUID) = Unit
    }

    /**
     * Los cuatro repositorios que la rama del correo repetido **no** puede tocar.
     *
     * Revientan en lugar de no hacer nada: un doble silencioso deja pasar la
     * escritura que esta prueba existe para prohibir, y el fallo aparecería
     * meses después en forma de dos hogares con el mismo administrador.
     */
    private object ForbiddenHouseholds : HouseholdRepository {
        override fun save(household: Household): Household = error("La rama del correo conocido creó un hogar")
        override fun findCurrent(): Household? = null
        override fun lockCurrent() = Unit
        override fun deleteCurrent() = Unit
    }

    private object ForbiddenMembers : HouseholdMemberRepository {
        override fun save(member: HouseholdMember): HouseholdMember =
            error("La rama del correo conocido creó una pertenencia")

        override fun findById(memberId: UUID): HouseholdMember? = null
        override fun findByIdentity(identityId: UUID): HouseholdMember? = null
        override fun list(includeDeactivated: Boolean, pagination: com.drp.platform.page.Pagination) =
            error("no se usa")

        override fun countActiveAdmins(): Long = 0
    }

    private object ForbiddenCategories : CategoryRepository {
        override fun seed(names: List<String>, at: Instant) = error("La rama del correo conocido sembró categorías")
        override fun countCurrent(): Long = 0
        override fun save(category: Category): Category = error("no se usa")
        override fun findById(categoryId: UUID): Category? = null
        override fun findLiveByName(name: String): Category? = null
        override fun list(includeRetired: Boolean, pagination: com.drp.platform.page.Pagination) = error("no se usa")
    }

    private object ForbiddenTokens : EmailVerificationTokenRepository {
        override fun save(token: SingleUseToken): SingleUseToken =
            error("La rama del correo conocido emitió un token de verificación")

        override fun findByTokenHash(tokenHash: String): SingleUseToken? = null
        override fun markAllUsedForIdentity(identityId: UUID, at: Instant) = Unit
    }

    /**
     * Los mismos cuatro, callados: es el montaje de la rama que **si** escribe, y
     * ahi lo que se cuenta son los hashes y no lo que se guarda.
     */
    private object TolerantHouseholds : HouseholdRepository {
        override fun save(household: Household): Household = household
        override fun findCurrent(): Household? = null
        override fun lockCurrent() = Unit
        override fun deleteCurrent() = Unit
    }

    private object TolerantMembers : HouseholdMemberRepository {
        override fun save(member: HouseholdMember): HouseholdMember = member
        override fun findById(memberId: UUID): HouseholdMember? = null
        override fun findByIdentity(identityId: UUID): HouseholdMember? = null
        override fun list(includeDeactivated: Boolean, pagination: com.drp.platform.page.Pagination) =
            error("no se usa")

        override fun countActiveAdmins(): Long = 0
    }

    private object TolerantCategories : CategoryRepository {
        override fun seed(names: List<String>, at: Instant) = Unit
        override fun countCurrent(): Long = 0
        override fun save(category: Category): Category = error("no se usa")
        override fun findById(categoryId: UUID): Category? = null
        override fun findLiveByName(name: String): Category? = null
        override fun list(includeRetired: Boolean, pagination: com.drp.platform.page.Pagination) = error("no se usa")
    }

    private object TolerantTokens : EmailVerificationTokenRepository {
        override fun save(token: SingleUseToken): SingleUseToken = token
        override fun findByTokenHash(tokenHash: String): SingleUseToken? = null
        override fun markAllUsedForIdentity(identityId: UUID, at: Instant) = Unit
    }

    private object FixedSecrets : SecretGenerator {
        override fun generate() = IssuedSecret(plain = "token-en-claro", hash = "token-hasheado")
        override fun hash(plain: String) = "token-hasheado"
    }

    private object PassThroughTenantContext : TenantContext {
        override fun currentHousehold(): UUID? = null
        override fun <T> runAs(householdId: UUID?, block: () -> T): T = block()
    }

    /** Lo justo para que `TransactionTemplate` ejecute el bloque y no haga nada más. */
    private object NoOpTransactionManager : PlatformTransactionManager {
        override fun getTransaction(definition: TransactionDefinition?): TransactionStatus = SimpleTransactionStatus()
        override fun commit(status: TransactionStatus) = Unit
        override fun rollback(status: TransactionStatus) = Unit
    }
}
