package com.drp.core.domain.loan

import com.drp.core.domain.inventory.AssetCondition
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * El concepto minimo de prestamo del core (README 4.1.5): que asset se presta,
 * quien lo presta, quien lo recibe y en que estado esta.
 *
 * Los tres estados y sus transiciones:
 *
 * ```
 * [*] --> ACTIVE : se inicia
 * ACTIVE --> OVERDUE : el proceso diario ve la fecha prevista superada
 * ACTIVE --> RETURNED : devolucion confirmada
 * OVERDUE --> RETURNED : devolucion confirmada
 * ```
 *
 * **`OVERDUE` no es un final ni una salida**: sigue ocupando el asset igual que
 * `ACTIVE`, porque vencer no es devolver. De ahi que [isOpen] mire a los dos y
 * que el indice unico parcial de la tabla los cubra juntos.
 *
 * Que el estado se persista en lugar de derivarse al leer es lo que permite
 * publicar `LoanOverdue`: un valor calculado no tiene momento en el que ocurrir,
 * y sin ese momento no hay evento al que enganchar un recordatorio.
 */
data class Loan(
    val id: UUID,
    val assetId: UUID,
    val lender: LoanParticipant,
    val borrower: LoanParticipant,
    val status: LoanStatus,
    val startedAt: Instant,
    /**
     * Opcional, y su ausencia significa algo: **un prestamo sin fecha nunca
     * vence**. Es un prestamo sin plazo, no un plazo infinito, y el proceso
     * diario lo ignora.
     */
    val dueAt: Instant?,
    val returnedAt: Instant?,
    /**
     * En que estado salio de casa, anotado al iniciar el prestamo.
     *
     * **La pareja es el dato**, no cada mitad por separado: [conditionOnReturn]
     * sin esta no dice si la cosa volvio peor, que es justo para lo que existen
     * las dos.
     */
    val conditionAtStart: AssetCondition?,
    /**
     * En que estado volvio. **Solo se escribe al confirmar la devolucion**, que
     * es cuando se sabe, y ninguna otra operacion la toca: un prestamo abierto
     * con condicion de vuelta seria una afirmacion sobre algo que todavia esta
     * fuera de casa. El `CHECK` de la tabla lo garantiza contra `returned_at`.
     *
     * Anotarla **no cambia el asset**. Que el taladro haya vuelto rayado es una
     * afirmacion de quien lo devuelve --que puede ser alguien de fuera, con un
     * token acotado-- y el estado de conservacion de la ficha lo corrige el
     * hogar por su cuenta.
     */
    val conditionOnReturn: AssetCondition?,
    val notes: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val createdBy: UUID?,
    val updatedBy: UUID?,
) {
    /** Un prestamo abierto ocupa su asset. `OVERDUE` cuenta, `RETURNED` no. */
    val isOpen: Boolean get() = status.isOpen

    /**
     * Si le ha pasado la fecha. Devuelve `false` sin `dueAt`, que es la regla que
     * el proceso diario necesita y la que se olvida al escribirlo con un `!!`.
     */
    fun isOverdueAt(now: Instant): Boolean =
        status == LoanStatus.ACTIVE && dueAt != null && dueAt.isBefore(now)

    /** En que extremo esta una pertenencia concreta, o `null` si en ninguno. */
    fun roleOf(memberId: UUID): LoanRole? = when (memberId) {
        lender.memberId -> LoanRole.LENDER
        borrower.memberId -> LoanRole.BORROWER
        else -> null
    }

    fun participantFor(role: LoanRole): LoanParticipant =
        if (role == LoanRole.LENDER) lender else borrower

    companion object {
        /**
         * Lo que vive el token acotado de un externo.
         *
         * Noventa dias y no quince minutos como el access token, porque no se
         * parecen en nada: aquel se renueva solo con el refresh y este viaja en
         * un correo que su destinatario abrira cuando le venga bien. Tiene que
         * durar mas que el prestamo, y un prestamo domestico sin `dueAt` puede
         * durar meses.
         *
         * **La consecuencia hay que decirla**: un prestamo abierto mas de
         * noventa dias deja al externo sin enlace, y no hay forma de reemitirlo
         * porque `GenerateExternalAccessToken` (5.7) no tiene ruta en el
         * contrato. El hogar siempre puede confirmar la devolucion por su lado,
         * asi que nada se queda bloqueado; lo que se pierde es la comodidad de
         * que lo haga el otro.
         */
        val ACCESS_TOKEN_LIFETIME: Duration = Duration.ofDays(90)
    }
}

enum class LoanStatus {
    ACTIVE,
    RETURNED,
    OVERDUE,
    ;

    /** `OVERDUE` sigue ocupando el asset: vencer no es devolver. */
    val isOpen: Boolean get() = this == ACTIVE || this == OVERDUE
}

/** En que extremo del prestamo esta alguien. Es tambien el claim `role` del token acotado. */
enum class LoanRole { LENDER, BORROWER }

/**
 * Uno de los dos extremos del prestamo: **o** una pertenencia al hogar **o** una
 * persona externa, exactamente uno de los dos.
 *
 * Es un tipo propio y no dos campos anulables sueltos por el mismo motivo que
 * `AssetLocation`: «los dos a la vez» y «ninguno de los dos» son justo los
 * estados que no deben poder representarse. Con dos campos anulables ambos son
 * escribibles y solo los rechaza el `CHECK` de la tabla, que es tarde.
 *
 * Ojo con lo que significa `memberId`: apunta a la **pertenencia** y no a la
 * identidad, como todo lo que el dominio llama usuario.
 */
sealed interface LoanParticipant {

    val memberId: UUID?

    val external: ExternalParty?

    /** Un miembro del hogar. */
    data class Member(val id: UUID) : LoanParticipant {
        override val memberId: UUID get() = id
        override val external: ExternalParty? get() = null
    }

    /** Alguien de fuera, que solo alcanza este prestamo y con un token acotado. */
    data class External(val party: ExternalParty) : LoanParticipant {
        override val memberId: UUID? get() = null
        override val external: ExternalParty get() = party
    }

    companion object {
        /** Reconstruye desde las dos columnas, que es como vive en la tabla. */
        fun from(memberId: UUID?, external: ExternalParty?): LoanParticipant? = when {
            memberId != null -> Member(memberId)
            external != null -> External(external)
            else -> null
        }
    }
}

/**
 * El contacto de quien no tiene cuenta.
 *
 * Nombre y **al menos un canal**, correo o telefono, y no por completismo: es
 * por donde se le manda el enlace con el token acotado (README 4.1.5). Un texto
 * suelto con el nombre del vecino no serviria para eso, y la tabla lo exige con
 * un `CHECK` sobre el `jsonb`.
 *
 * Los nombres de estos tres campos son **las claves del JSON guardado**, asi que
 * renombrarlos en Kotlin cambia en silencio lo que hay escrito en la columna y
 * rompe el `CHECK`. Hay una prueba que le pregunta a PostgreSQL que claves hay
 * de verdad, en lugar de confiar en que nadie los toque.
 */
data class ExternalParty(
    val name: String,
    val email: String?,
    val phone: String?,
) {
    /**
     * Va como **funcion y no como propiedad calculada**, y el nombre evita
     * empezar por `is`, por la misma razon que `Capacity.countsUnits()`: esta
     * clase se serializa a la columna `jsonb`, y Jackson trata un
     * `val isContactable` como un campo mas.
     *
     * No es teorico. La primera version lo escribio como propiedad y la prueba
     * que le pregunta a PostgreSQL que claves hay de verdad encontro una
     * `isContactable` guardada en la tabla: un valor **derivado y congelado**
     * dentro del dato del que se deriva, que a la primera correccion de correo
     * pasaria a mentir.
     */
    fun hasContactChannel(): Boolean = !email.isNullOrBlank() || !phone.isNullOrBlank()
}

/**
 * El token acotado de un externo, tal y como se guarda.
 *
 * **Es el cuarto token hasheado del core** --verificacion de correo,
 * restablecimiento, invitacion y este-- pero el unico que ademas es un JWT
 * firmado. Las dos capas hacen cosas distintas:
 *
 * - La **firma** lo hace infalsificable y lleva dentro `loanId` y `role`, asi
 *   que lo caducado o manipulado se rechaza sin tocar la base de datos.
 * - La **fila** lo hace revocable, que un JWT por si solo no puede ser. Aqui
 *   hace falta: el argumento de «dura tan poco que no merece la pena revocarlo»
 *   vale para un access token de quince minutos y no para uno de noventa dias.
 *
 * Ademas la fila hay que leerla igualmente, porque `loan_access_tokens` no lleva
 * `household_id` y el hogar se resuelve desde ella (V6).
 *
 * `usedAt` **lo marca la devolucion y solo la devolucion**. Leer no gasta el
 * token: el `GET` es idempotente y quien acaba de confirmar recarga la pagina
 * para verlo hecho, asi que marcarlo al primer acceso lo dejaria inservible en
 * el uso normal. Lo que registra es quien cerro el prestamo y con que
 * credencial.
 */
data class LoanAccessToken(
    val id: UUID,
    val loanId: UUID,
    val tokenHash: String,
    val role: LoanRole,
    val expiresAt: Instant,
    val usedAt: Instant?,
) {
    /**
     * Vale para leer mientras no haya caducado, **aunque ya se haya usado**: el
     * gasto solo cierra la accion, no la consulta.
     */
    fun isReadableAt(now: Instant): Boolean = expiresAt.isAfter(now)
}
