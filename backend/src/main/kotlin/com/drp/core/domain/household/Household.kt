package com.drp.core.domain.household

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
    /** Informada mientras corre el periodo de gracia de la baja (ADR-012). */
    val closure: HouseholdClosure? = null,
) {
    /**
     * Si el hogar ha pedido marcharse. **No cambia nada de lo que se puede
     * hacer dentro**: durante la gracia el hogar funciona igual, porque dejarlo
     * de solo lectura castiga precisamente a quien todavia puede cancelar.
     */
    val isClosing: Boolean get() = closure != null

    /** Si la gracia ya venció y el recorrido diario puede purgarlo. */
    fun isPurgeable(now: Instant): Boolean = closure?.let { !now.isBefore(it.effectiveAt) } ?: false
}

/**
 * La baja solicitada de un hogar, con su periodo de gracia (ADR-012).
 *
 * Va como objeto de valor y no como tres columnas sueltas por lo mismo que
 * [com.drp.core.domain.identity.Avatar]: «se pidio la baja pero no consta
 * cuando vence» no es un estado que deba poder representarse. La base de datos
 * lo defiende con un `CHECK` de las tres a la vez; aqui lo defiende el tipo.
 *
 * [effectiveAt] se guarda y no se calcula al leer. El plazo es una decision de
 * producto que puede cambiar, y derivarlo cada noche moveria la fecha que ya se
 * le habia dicho a una persona: lo que se prometio es una fecha concreta.
 *
 * [requestedBy] apunta a la **pertenencia** de quien la pidio, como toda la
 * autoria del modelo, y nunca a la identidad.
 */
data class HouseholdClosure(
    val requestedAt: Instant,
    val requestedBy: UUID,
    val effectiveAt: Instant,
) {
    companion object {
        /** Reconstruye desde las columnas. Nulo si el hogar no ha pedido la baja. */
        fun from(requestedAt: Instant?, requestedBy: UUID?, effectiveAt: Instant?): HouseholdClosure? =
            if (requestedAt != null && requestedBy != null && effectiveAt != null) {
                HouseholdClosure(requestedAt, requestedBy, effectiveAt)
            } else {
                null
            }
    }
}

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
