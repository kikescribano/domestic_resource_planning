package com.drp.core.application.usecase

import com.drp.core.application.EnrollmentEmails
import com.drp.core.application.event.CoreEvents
import com.drp.core.application.port.AssetRepository
import com.drp.platform.mail.EmailMessage
import com.drp.platform.mail.EmailSender
import com.drp.core.application.port.HouseholdMemberRepository
import com.drp.core.application.port.LoanAccessTokenRepository
import com.drp.core.application.port.LoanFilter
import com.drp.core.application.port.LoanRepository
import com.drp.core.application.port.LoanTokenClaims
import com.drp.core.application.port.LoanTokenIssuer
import com.drp.platform.page.Page
import com.drp.platform.page.Pagination
import com.drp.core.application.port.SessionClaims
import com.drp.core.application.port.TenantResolver
import com.drp.platform.tenant.TenantContext
import com.drp.platform.error.BusinessRuleViolation
import com.drp.platform.error.ErrorCode
import com.drp.platform.error.ResourceNotFound
import com.drp.platform.error.ValidationFailure
import com.drp.platform.mail.EmailAddress
import com.drp.core.domain.inventory.AssetCondition
import com.drp.core.domain.inventory.AssetStatus
import com.drp.core.domain.loan.ExternalParty
import com.drp.core.domain.loan.Loan
import com.drp.core.domain.loan.LoanAccessToken
import com.drp.core.domain.loan.LoanParticipant
import com.drp.core.domain.loan.LoanRole
import com.drp.core.domain.loan.LoanStatus
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
    /**
     * Las dos condiciones **si salen de casa**, y no es un descuido de la
     * proyeccion acotada: ninguna dice nada del hogar. La de entrega describe la
     * cosa que esta persona tiene en las manos --y saber en que estado se la
     * dieron le protege a ella-- y la de vuelta es lo que acaba de escribir ella
     * misma, devuelto para que lo vea hecho.
     */
    val conditionAtStart: AssetCondition?,
    val conditionOnReturn: AssetCondition?,
)

/** Un extremo del prestamo tal y como llega del cliente: uno de los dos, nunca ambos. */
data class LoanParticipantCommand(val userId: UUID?, val external: ExternalParty?)

data class StartLoanCommand(
    val assetId: UUID,
    val lender: LoanParticipantCommand,
    val borrower: LoanParticipantCommand,
    val dueAt: Instant?,
    val conditionAtStart: AssetCondition?,
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
            conditionAtStart = command.conditionAtStart,
            // Se anota al confirmar la devolucion y en ningun otro sitio: aqui la
            // cosa acaba de salir de casa.
            conditionOnReturn = null,
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
                ?.let { emails.loan(EmailAddress.of(it), assetName.orEmpty(), role, loan.id, issued.token) }
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

/**
 * Quien ha llegado con un token acotado, y a que prestamo.
 *
 * Es lo que sustituye a `SessionClaims` en las dos operaciones que ese token
 * alcanza. **No lleva `memberId` ni `identityId`** porque no hay persona
 * detras: hay un prestamo y un extremo.
 */
data class LoanAccess(
    val loanId: UUID,
    val role: LoanRole,
    val tokenId: UUID,
    val householdId: UUID,
)

/**
 * Resuelve un token acotado: firma, hogar y fila.
 *
 * Las tres cosas en este orden y no en otro, porque cada una necesita la
 * anterior:
 *
 * 1. **La firma y la caducidad del JWT**, que se comprueban sin tocar la base de
 *    datos. Lo manipulado o caducado muere aqui, sin consultar nada.
 * 2. **El hogar**, con la funcion acotada de la V6. Es imprescindible antes de
 *    leer nada: `loans` tiene politica de RLS y quien pregunta no tiene sesion.
 * 3. **La fila**, ya dentro del contexto. Es lo que hace el token **revocable**:
 *    borrar la fila lo invalida aunque la firma siga siendo correcta.
 *
 * Cualquier fallo devuelve nulo sin distinguir cual: decir si el token existio,
 * si caduco o si el prestamo es de otro hogar diria a quien prueba tokens por
 * donde va bien encaminado.
 */
@Service
class AuthenticateLoanToken(
    private val loanTokens: LoanTokenIssuer,
    private val tokens: LoanAccessTokenRepository,
    private val tenantResolver: TenantResolver,
    private val tenantContext: TenantContext,
    private val transactions: TransactionTemplate,
    private val clock: Clock,
) {

    fun authenticate(rawToken: String): LoanAccess? {
        val claims = loanTokens.verify(rawToken) ?: return null
        val tokenHash = loanTokens.hash(rawToken)

        val householdId = tenantResolver.householdOfLoanToken(tokenHash) ?: return null

        return tenantContext.runAs(householdId) {
            transactions.execute {
                val stored = tokens.findByTokenHash(tokenHash) ?: return@execute null
                if (!stored.isReadableAt(clock.instant())) return@execute null

                // El claim firmado y la fila tienen que decir lo mismo. Que no
                // coincidan solo puede significar dos cosas --la clave de firma
                // comprometida, o un fallo al emitir-- y ninguna merece dejar
                // pasar la peticion.
                if (stored.loanId != claims.loanId || stored.role != claims.role) return@execute null

                LoanAccess(
                    loanId = stored.loanId,
                    role = stored.role,
                    tokenId = stored.id,
                    householdId = householdId,
                )
            }
        }
    }
}

/**
 * El prestamo visto por un externo.
 *
 * Recibe el [LoanAccess] entero y no un `loanId` suelto, y esa firma es la
 * defensa: **no hay forma de invocarlo para un prestamo que no sea el del
 * token**, porque el identificador sale de la credencial y no del parametro.
 */
@Service
class GetLoanForExternal(
    private val loans: LoanRepository,
) {
    @Transactional(readOnly = true)
    fun handle(access: LoanAccess): ExternalLoanView {
        val loan = loans.findById(access.loanId) ?: throw ResourceNotFound("Préstamo no encontrado")

        return ExternalLoanView(
            id = loan.id,
            assetName = loans.assetNameOf(loan),
            role = access.role,
            status = loan.status,
            startedAt = loan.startedAt,
            dueAt = loan.dueAt,
            returnedAt = loan.returnedAt,
            conditionAtStart = loan.conditionAtStart,
            conditionOnReturn = loan.conditionOnReturn,
        )
    }
}

/**
 * Confirmar la devolucion, por cualquiera de los dos caminos.
 *
 * Lo puede hacer un usuario del hogar **o** el externo con su token acotado, y
 * el efecto es el mismo: el prestamo pasa a `RETURNED` y el asset vuelve a
 * `AVAILABLE`. Lo que cambia es la autoria --`updatedBy` solo existe cuando
 * detras hay una pertenencia-- y lo que se devuelve, que sigue la proyeccion de
 * la credencial.
 *
 * Un `OVERDUE` se devuelve igual que un `ACTIVE`: vencer no es un callejon sin
 * salida sino el estado en el que mas falta hace poder cerrarlo.
 *
 * **Aqui y solo aqui se anota en que estado volvio.** No hay `PATCH` que la
 * corrija despues, y es deliberado: es lo que alguien afirmo en el momento de
 * devolverlo, no un campo de la ficha. Por el mismo motivo **no toca el asset**:
 * quien confirma puede ser una persona de fuera con un token acotado, y dejarle
 * escribir el estado de conservacion del inventario seria darle una fila del
 * hogar que su credencial no alcanza.
 */
@Service
class ConfirmReturn(
    private val loans: LoanRepository,
    private val tokens: LoanAccessTokenRepository,
    private val assets: AssetRepository,
    private val events: CoreEvents,
    private val clock: Clock,
) {

    /** Desde el hogar. */
    @Transactional
    fun handle(session: SessionClaims, loanId: UUID, conditionOnReturn: AssetCondition?): LoanView {
        val returned = close(loanId, session.memberId, usedTokenId = null, conditionOnReturn = conditionOnReturn)
        return LoanView(returned, loans.assetNameOf(returned))
    }

    /** Desde el enlace del correo, sin sesion. */
    @Transactional
    fun handle(access: LoanAccess, conditionOnReturn: AssetCondition?): ExternalLoanView {
        val returned = close(
            access.loanId,
            memberId = null,
            usedTokenId = access.tokenId,
            conditionOnReturn = conditionOnReturn,
        )

        return ExternalLoanView(
            id = returned.id,
            assetName = loans.assetNameOf(returned),
            role = access.role,
            status = returned.status,
            startedAt = returned.startedAt,
            dueAt = returned.dueAt,
            returnedAt = returned.returnedAt,
            conditionAtStart = returned.conditionAtStart,
            conditionOnReturn = returned.conditionOnReturn,
        )
    }

    private fun close(
        loanId: UUID,
        memberId: UUID?,
        usedTokenId: UUID?,
        conditionOnReturn: AssetCondition?,
    ): Loan {
        val now = clock.instant()

        // **Bloqueando la fila**, y solo aqui. El enlace del correo se abre desde
        // dos sitios o se pulsa dos veces, y con una lectura corriente las dos
        // peticiones ven `ACTIVE`, las dos pasan la comprobacion de abajo y las
        // dos cierran: medido con cuatro devoluciones simultaneas del mismo
        // token, cuatro `200` donde el contrato promete uno y tres `409`, con
        // `LoanReturned` publicado cuatro veces y el `returnedAt` pisado.
        //
        // No sirve aqui el patron del alta --dejar que un indice unico rechace a
        // la segunda-- porque no se inserta nada: es un `UPDATE` sobre una fila
        // que ya existe. La segunda tiene que esperar a la primera para poder ver
        // lo que hizo.
        val loan = loans.findByIdForUpdate(loanId) ?: throw ResourceNotFound("Préstamo no encontrado")

        if (!loan.isOpen) {
            throw BusinessRuleViolation(ErrorCode.LOAN_ALREADY_RETURNED, "Ese préstamo ya estaba devuelto")
        }

        val returned = loans.save(
            loan.copy(
                status = LoanStatus.RETURNED,
                returnedAt = now,
                // Lo unico que la devolucion escribe ademas del cierre, y lo unico
                // que un token acotado puede escribir en todo el hogar. Nulo
                // cuando no se anoto, que es lo corriente: no hay valor por
                // defecto, porque un desplegable preseleccionado convertiria en
                // dato lo que nadie llego a mirar.
                conditionOnReturn = conditionOnReturn,
                updatedAt = now,
                // Nulo cuando lo confirma un externo: no hay pertenencia a la que
                // atribuirlo, y eso es exactamente lo que nulo significa.
                updatedBy = memberId,
            ),
        )

        // El asset se libera. Es la otra mitad de que prestarlo lo ocupase, y sin
        // ella el hueco del indice unico quedaria libre pero el asset seguiria
        // diciendo que esta fuera de casa.
        assets.findById(loan.assetId)?.let {
            assets.save(it.copy(status = AssetStatus.AVAILABLE, updatedAt = now, updatedBy = memberId))
        }

        // **Aqui y solo aqui se gasta el token.** Leer no lo consume: el GET es
        // idempotente y quien acaba de confirmar recarga la pagina para verlo
        // hecho. Lo que esta marca queda registrado es quien cerro el prestamo y
        // con que credencial.
        usedTokenId?.let { id ->
            tokens.findById(id)?.let { tokens.save(it.copy(usedAt = now)) }
        }

        events.loanReturned(returned.id, returned.assetId, now)
        return returned
    }
}

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
