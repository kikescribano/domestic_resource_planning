package com.drp.core.domain.invitation

import com.drp.core.domain.household.MemberRole
import com.drp.core.domain.identity.EmailAddress
import java.time.Instant
import java.util.UUID

enum class InvitationStatus {
    PENDING,
    ACCEPTED,
    REVOKED,
    EXPIRED,
}

/**
 * Una invitacion a un hogar existente. Es la unica via de alta desde que se
 * descarto el alta directa: un administrador no crea cuentas ajenas, invita.
 *
 * El estado **no es una columna**: se deduce de las tres fechas y del reloj. Por
 * eso [statusAt] recibe el instante en lugar de leerlo por su cuenta, que ademas
 * es lo que la deja comprobable sin esperar siete dias.
 */
data class Invitation(
    val id: UUID,
    val householdId: UUID,
    val email: EmailAddress,
    val role: MemberRole,
    val tokenHash: String,
    val expiresAt: Instant,
    val acceptedAt: Instant?,
    val revokedAt: Instant?,
    val createdAt: Instant,
    val createdBy: UUID?,
) {
    fun statusAt(now: Instant): InvitationStatus = when {
        // El orden importa. Revocada gana a aceptada y a caducada porque es la
        // unica que alguien decidio a proposito; y aceptada gana a caducada
        // porque una invitacion ya usada no "caduca" despues.
        revokedAt != null -> InvitationStatus.REVOKED
        acceptedAt != null -> InvitationStatus.ACCEPTED
        !expiresAt.isAfter(now) -> InvitationStatus.EXPIRED
        else -> InvitationStatus.PENDING
    }

    fun isUsableAt(now: Instant): Boolean = statusAt(now) == InvitationStatus.PENDING

    companion object {
        /**
         * Siete dias, la misma ventana que la retencion de hogares sin verificar
         * y por el mismo motivo: da margen de sobra para llegar al correo sin
         * acumular invitaciones fantasma.
         */
        val LIFETIME: java.time.Duration = java.time.Duration.ofDays(7)
    }
}
