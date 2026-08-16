package com.drp.application.usecase

import com.drp.application.EnrollmentEmails
import com.drp.application.event.CoreEvents
import com.drp.application.port.AssetRepository
import com.drp.application.port.EmailMessage
import com.drp.application.port.EmailSender
import com.drp.application.port.HouseholdMemberRepository
import com.drp.application.port.LoanAccessTokenRepository
import com.drp.application.port.LoanFilter
import com.drp.application.port.LoanRepository
import com.drp.application.port.LoanTokenClaims
import com.drp.application.port.LoanTokenIssuer
import com.drp.application.port.Page
import com.drp.application.port.Pagination
import com.drp.application.port.SessionClaims
import com.drp.domain.BusinessRuleViolation
import com.drp.domain.ErrorCode
import com.drp.domain.ResourceNotFound
import com.drp.domain.ValidationFailure
import com.drp.domain.identity.EmailAddress
import com.drp.domain.inventory.AssetStatus
import com.drp.domain.loan.ExternalParty
import com.drp.domain.loan.Loan
import com.drp.domain.loan.LoanAccessToken
import com.drp.domain.loan.LoanParticipant
import com.drp.domain.loan.LoanRole
import com.drp.domain.loan.LoanStatus
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * Las cuatro operaciones de prestamos, con las que el contrato queda completo.
 *
 * Iniciar un prestamo es la operacion mas acoplada del core: comprueba,
 * inserta, **cambia el estado del asset**, emite credenciales para gente que no
 * tiene cuenta, manda correos y publica un evento. Se escribe junta a proposito,
 * porque partirla dejaria estados intermedios que ninguna regla describe --un
 * prestamo sin token, o un asset `LENT` sin prestamo.
 */

/** El prestamo con el nombre del asset ya resuelto, que es lo unico del asset que se lee. */
data class LoanView(val loan: Loan, val assetName: String?)

/**
 * Lo que ve quien llega con un token acotado (`LoanExternalView` del contrato).
 *
 * Es un tipo aparte y no un `LoanView` con campos a nulo, y esa es la decision:
 * con un solo tipo, olvidar borrar un campo es un descuido silencioso; con dos,
 * lo que no se puede enviar **no se puede ni construir**. `role` no es un dato
 * del prestamo sino de quien pregunta, y por eso solo esta aqui.
 */
data class ExternalLoanView(
    val id: UUID,
    val assetName: String?,
    val role: LoanRole,
    val status: LoanStatus,
    val startedAt: Instant,
    val dueAt: Instant?,
    val returnedAt: Instant?,
)

/** Un extremo del prestamo tal y como llega del cliente: uno de los dos, nunca ambos. */
data class LoanParticipantCommand(val userId: UUID?, val external: ExternalParty?)

data class StartLoanCommand(
    val assetId: UUID,
    val lender: LoanParticipantCommand,
    val borrower: LoanParticipantCommand,
    val dueAt: Instant?,
    val notes: String?,
)

/**
 * Iniciar un prestamo.
 *
 * Las tres reglas de 4.1.5, y cual las defiende:
 *
 * 1. **Solo un `DURABLE` se presta.** Un consumible se consume o se entrega, y
 *    la semantica de devolucion no le aplica. La comprueba este caso de uso.
 * 2. **Un asset no puede tener dos prestamos abiertos**, y `OVERDUE` cuenta como
 *    abierto. La comprueba este caso de uso **y** el indice unico parcial, que es
 *    el que de verdad la garantiza (ver [asBusinessViolation]).
 * 3. **Exactamente un prestador y un receptor**, cada uno miembro o externo. La
 *    comprueba el tipo del dominio al construirse, y el `CHECK` de la tabla
 *    debajo.
 */
@Service
class StartLoan(
    private val loans: LoanRepository,
    private val tokens: LoanAccessTokenRepository,
    private val assets: AssetRepository,
    private val members: HouseholdMemberRepository,
    private val loanTokens: LoanTokenIssuer,
    private val emails: EnrollmentEmails,
    private val emailSender: EmailSender,
    private val events: CoreEvents,
    private val transactions: TransactionTemplate,
    private val clock: Clock,
) {

    fun handle(session: SessionClaims, command: StartLoanCommand): LoanView {
        val now = clock.instant()
        val lender = command.lender.resolve("lender")
        val borrower = command.borrower.resolve("borrower")

        if (command.dueAt != null && command.dueAt.isBefore(now)) {
            throw ValidationFailure(mapOf("dueAt" to "La fecha prevista de devolución ya ha pasado"))
        }

        val started = transactions.execute { start(session, command, lender, borrower, now) }!!

        // Los correos, fuera de la transaccion y despues del commit. Al reves, un
        // fallo al escribir dejaria enlaces vivos en el buzon de alguien
        // apuntando a un prestamo que no llego a existir.
        started.deliveries.forEach { emailSender.send(it) }
        return started.view
    }

    private fun start(
        session: SessionClaims,
        command: StartLoanCommand,
        lender: LoanParticipant,
        borrower: LoanParticipant,
        now: Instant,
    ): StartedLoan {
        // El asset se resuelve contra su repositorio ANTES de insertar. La
        // comprobacion de una clave ajena no pasa por RLS, asi que sin esto un
        // assetId de otro hogar llegaria hasta la insercion y saldria un 500
        // donde el contrato promete un 404.
        val asset = assets.findById(command.assetId)
            ?: throw ResourceNotFound("Asset no encontrado")

        if (!asset.isDurable) {
            throw BusinessRuleViolation(
                ErrorCode.LOAN_ASSET_NOT_DURABLE,
                "Solo se presta un asset DURABLE: ceder un consumible es un ajuste de cantidad",
            )
        }

        if (asset.status == AssetStatus.DECOMMISSIONED) {
            throw ResourceNotFound("Asset no encontrado")
        }

        // Y lo mismo con los dos extremos: una pertenencia de otro hogar es
        // invisible para la politica pero **referenciable** por la clave ajena.
        lender.memberId?.let { requireMember(it, "lender") }
        borrower.memberId?.let { requireMember(it, "borrower") }

        // La comprobacion previa, que sirve para dar el 409 en el caso normal.
        // No es la garantia: entre esta lectura y la insercion cabe otra
        // transaccion, y de eso se ocupa el indice.
        if (assets.hasOpenLoan(asset.id)) throw alreadyLent()

        val loan = Loan(
            id = UUID.randomUUID(),
            assetId = asset.id,
            lender = lender,
            borrower = borrower,
            status = LoanStatus.ACTIVE,
            startedAt = now,
            dueAt = command.dueAt,
            returnedAt = null,
            notes = command.notes,
            createdAt = now,
            updatedAt = now,
            createdBy = session.memberId,
            updatedBy = session.memberId,
        )

        val saved = try {
            loans.save(loan)
        } catch (violation: DataIntegrityViolationException) {
            throw asBusinessViolation(violation)
        }

        // El asset acompana al prestamo: pasa a LENT mientras este abierto. El
        // estado existe en el dominio desde el Hito 2 y hasta ahora no lo ponia
        // nadie.
        assets.save(asset.copy(status = AssetStatus.LENT, updatedAt = now, updatedBy = session.memberId))

        val assetName = loans.assetNameOf(saved)
        val deliveries = issueExternalTokens(saved, assetName, now)

        events.loanStarted(
            loanId = saved.id,
            assetId = saved.assetId,
            lender = saved.lender.describe(),
            borrower = saved.borrower.describe(),
            dueAt = saved.dueAt,
        )

        return StartedLoan(LoanView(saved, assetName), deliveries)
    }

    /**
     * Un token por cada extremo externo: cero, uno o dos.
     *
     * Cero es el caso corriente de un prestamo entre dos miembros del hogar, que
     * no necesita ninguna credencial nueva porque los dos ya entran con la suya.
     */
    private fun issueExternalTokens(loan: Loan, assetName: String?, now: Instant): List<EmailMessage> =
        LoanRole.entries.mapNotNull { role ->
            val party = loan.participantFor(role).external ?: return@mapNotNull null
            val expiresAt = now.plus(Loan.ACCESS_TOKEN_LIFETIME)
            val issued = loanTokens.issue(LoanTokenClaims(loan.id, role), expiresAt)

            tokens.save(
                LoanAccessToken(
                    id = UUID.randomUUID(),
                    loanId = loan.id,
                    // Del token solo se guarda el hash, igual que en los otros
                    // tres. El valor en claro existe el tiempo de meterlo en un
                    // correo.
                    tokenHash = issued.tokenHash,
                    role = role,
                    expiresAt = expiresAt,
                    usedAt = null,
                ),
            )

            // Sin correo no hay a donde mandarlo. La tabla exige un canal --correo
            // **o** telefono-- y el SMS no existe todavia, asi que un externo con
            // solo telefono se queda con el token emitido y sin entrega: el hogar
            // tendra que pasarle el enlace. Emitirlo igualmente es lo correcto,
            // porque el dia que haya SMS no hace falta reemitir nada.
            party.email
                ?.takeIf { it.isNotBlank() }
                ?.let { emails.loan(EmailAddress.of(it), assetName.orEmpty(), role, issued.token) }
        }

    private fun requireMember(memberId: UUID, field: String) {
        members.findById(memberId)?.takeIf { it.isActive }
            ?: throw ValidationFailure(mapOf(field to "Ese miembro del hogar no existe"))
    }

    /**
     * Traduce la violacion del indice unico al `409` del contrato.
     *
     * Sin esto, dos peticiones simultaneas sobre el mismo asset dan un 500 a la
     * que pierde: el indice hace lo correcto --impedir el segundo prestamo-- y la
     * respuesta seria un error del servidor por algo que es una regla de negocio.
     *
     * Se distingue por el nombre del indice y no por el tipo de la excepcion,
     * porque `DataIntegrityViolationException` cubre tambien las claves ajenas y
     * los `CHECK`: tragarselos todos como 409 convertiria un fallo de
     * programacion en un mensaje de negocio que despista.
     */
    private fun asBusinessViolation(violation: DataIntegrityViolationException): RuntimeException =
        if (violation.mostSpecificCause.message?.contains(OPEN_LOAN_INDEX) == true) alreadyLent() else violation

    private fun alreadyLent() = BusinessRuleViolation(
        ErrorCode.LOAN_ASSET_ALREADY_LENT,
        "Ese asset ya tiene un préstamo abierto: vencer no es devolver",
    )

    private data class StartedLoan(val view: LoanView, val deliveries: List<EmailMessage>)

    private companion object {
        const val OPEN_LOAN_INDEX = "loans_one_open_per_asset"
    }
}

/**
 * Traduce el extremo que llega del cliente al tipo del dominio.
 *
 * Rechaza aqui los dos estados que el `oneOf` del contrato ya prohibe --los dos
 * campos a la vez, o ninguno-- para responder `400` con el campo senalado en vez
 * de dejar que reviente mas abajo.
 */
private fun LoanParticipantCommand.resolve(field: String): LoanParticipant {
    val party = external?.takeIf { it.name.isNotBlank() }

    if ((userId == null) == (party == null)) {
        throw ValidationFailure(
            mapOf(field to "Hace falta exactamente uno de userId o external"),
        )
    }

    if (party != null && !party.hasContactChannel()) {
        // Sin canal no hay forma de hacerle llegar el enlace, que es justo para
        // lo que se pide el contacto de un externo.
        throw ValidationFailure(
            mapOf("$field.external" to "Un participante externo necesita correo o teléfono"),
        )
    }

    return LoanParticipant.from(userId, party)!!
}

/** Como viaja un extremo en el payload del evento: sin el contacto del externo dentro. */
private fun LoanParticipant.describe(): String = if (memberId != null) "MEMBER" else "EXTERNAL"

@Service
class GetLoan(
    private val loans: LoanRepository,
) {
    @Transactional(readOnly = true)
    fun handle(loanId: UUID): LoanView {
        // Si es de otro hogar, la politica no lo deja ver y esto devuelve nulo:
        // "no existe" y "no es tuyo" responden igual a proposito.
        val loan = loans.findById(loanId) ?: throw ResourceNotFound("Préstamo no encontrado")
        return LoanView(loan, loans.assetNameOf(loan))
    }
}

@Service
class ListLoans(
    private val loans: LoanRepository,
) {
    @Transactional(readOnly = true)
    fun handle(filter: LoanFilter, pagination: Pagination): Page<LoanView> {
        val found = loans.list(filter, pagination)
        return Page(
            items = found.items.map { LoanView(it, loans.assetNameOf(it)) },
            page = found.page,
            size = found.size,
            total = found.total,
        )
    }
}
