package com.drp.domain.household

import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * La unidad de aislamiento que agrupa a todo lo demas.
 *
 * La zona horaria no es adorno: "la fecha prevista ya paso" no significa nada
 * sin saber respecto a que huso, y un hogar no tiene por que estar donde este el
 * servidor. La usa el proceso diario de prestamos vencidos.
 */
data class Household(
    val id: UUID,
    val name: String,
    val timeZone: ZoneId,
    val createdAt: Instant,
    val updatedAt: Instant,
)

enum class MemberRole {
    HOUSEHOLD_ADMIN,
    HOUSEHOLD_MEMBER,
}

/**
 * Que eres dentro de un hogar concreto.
 *
 * Todo lo que el dominio llama "usuario" apunta aqui y no a la identidad: el
 * propietario de un asset, el prestador y el receptor de un prestamo, y el
 * `createdBy`/`updatedBy` de cualquier fila. Asi funciona la clave ajena
 * compuesta que impide atribuir algo a alguien de otro hogar.
 */
data class HouseholdMember(
    val id: UUID,
    val householdId: UUID,
    val identityId: UUID,
    val role: MemberRole,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deactivatedAt: Instant?,
    val createdBy: UUID?,
    val updatedBy: UUID?,
) {
    /** Informado si esa persona ha dejado **este** hogar; no cierra su cuenta. */
    val isActive: Boolean get() = deactivatedAt == null

    val isAdmin: Boolean get() = role == MemberRole.HOUSEHOLD_ADMIN
}
