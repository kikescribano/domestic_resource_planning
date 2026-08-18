package com.drp.core.adapter.http

import com.drp.core.application.usecase.ExternalLoanView
import com.drp.core.application.usecase.LoanParticipantCommand
import com.drp.core.application.usecase.LoanView
import com.drp.core.domain.loan.ExternalParty
import com.drp.core.domain.loan.LoanRole
import com.drp.core.domain.loan.LoanStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class LoanInput(
    @field:NotNull val assetId: UUID?,
    @field:NotNull @field:Valid val lender: LoanParticipantInput?,
    @field:NotNull @field:Valid val borrower: LoanParticipantInput?,
    val dueAt: Instant? = null,
    @field:Size(max = 2000) val notes: String? = null,
)

/**
 * Un extremo del prestamo. El contrato lo declara con un `oneOf` de dos ramas
 * --`userId` **o** `external`--, y que llegue mal formado se rechaza en el caso
 * de uso para poder senalar el campo en el `400`.
 */
data class LoanParticipantInput(
    val userId: UUID? = null,
    @field:Valid val external: ExternalPartyInput? = null,
) {
    fun toCommand() = LoanParticipantCommand(userId, external?.toDomain())
}

data class ExternalPartyInput(
    @field:NotBlank @field:Size(max = 160) val name: String?,
    @field:Email @field:Size(max = 320) val email: String? = null,
    @field:Size(max = 40) val phone: String? = null,
) {
    fun toDomain() = ExternalParty(
        name = name.orEmpty().trim(),
        email = email?.trim()?.takeIf { it.isNotEmpty() },
        phone = phone?.trim()?.takeIf { it.isNotEmpty() },
    )
}

/**
 * La vista completa: la que ve un usuario del hogar.
 *
 * Es un tipo distinto de [ExternalLoanResponse] y no el mismo con campos a nulo.
 * Con un solo tipo, olvidarse de vaciar `lender` seria un descuido silencioso;
 * con dos, la respuesta acotada **no puede ni construirse** con los campos que
 * no le tocan.
 */
data class LoanResponse(
    val id: UUID,
    val assetId: UUID,
    val assetName: String?,
    val lender: LoanParticipantResponse,
    val borrower: LoanParticipantResponse,
    val status: LoanStatus,
    val startedAt: Instant,
    val dueAt: Instant?,
    val returnedAt: Instant?,
    val notes: String?,
    val createdBy: UUID?,
    val updatedBy: UUID?,
) {
    companion object {
        fun of(view: LoanView) = LoanResponse(
            id = view.loan.id,
            assetId = view.loan.assetId,
            assetName = view.assetName,
            lender = LoanParticipantResponse.of(view.loan.lender),
            borrower = LoanParticipantResponse.of(view.loan.borrower),
            status = view.loan.status,
            startedAt = view.loan.startedAt,
            dueAt = view.loan.dueAt,
            returnedAt = view.loan.returnedAt,
            notes = view.loan.notes,
            createdBy = view.loan.createdBy,
            updatedBy = view.loan.updatedBy,
        )
    }
}

data class LoanParticipantResponse(
    val userId: UUID? = null,
    val external: ExternalPartyResponse? = null,
) {
    companion object {
        fun of(participant: com.drp.core.domain.loan.LoanParticipant) = LoanParticipantResponse(
            userId = participant.memberId,
            external = participant.external?.let {
                ExternalPartyResponse(it.name, it.email, it.phone)
            },
        )
    }
}

data class ExternalPartyResponse(val name: String, val email: String?, val phone: String?)

/**
 * La vista acotada: lo que ve quien llega con un token de prestamo.
 *
 * **Es la unica respuesta de toda la API que existe por duplicado**, y lo que
 * decide cual se devuelve es la credencial, no el recurso. Lleva `role` --que la
 * completa no tiene-- porque no es un dato del prestamo sino de quien pregunta.
 */
data class ExternalLoanResponse(
    val id: UUID,
    val assetName: String?,
    val role: LoanRole,
    val status: LoanStatus,
    val startedAt: Instant,
    val dueAt: Instant?,
    val returnedAt: Instant?,
) {
    companion object {
        fun of(view: ExternalLoanView) = ExternalLoanResponse(
            id = view.id,
            assetName = view.assetName,
            role = view.role,
            status = view.status,
            startedAt = view.startedAt,
            dueAt = view.dueAt,
            returnedAt = view.returnedAt,
        )
    }
}
