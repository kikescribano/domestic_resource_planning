package com.drp.core.adapter.http

import com.drp.core.application.TokenPair
import com.drp.platform.page.Page
import com.drp.core.application.usecase.HouseholdUser
import com.drp.core.domain.catalog.Category
import com.drp.core.domain.household.MemberRole
import com.drp.core.domain.invitation.Invitation
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

/**
 * Las formas que viajan por HTTP, tal y como las declara `openapi.yaml`.
 *
 * Son tipos propios y no los del dominio a proposito: el contrato es fuente de
 * verdad (ADR-007) y evoluciona por su cuenta, asi que atarlo a la forma interna
 * convertiria cualquier refactor del dominio en un cambio incompatible de la API.
 *
 * Las validaciones de forma --obligatorio, correo, longitud minima-- se declaran
 * aqui con Bean Validation y producen `400 VALIDATION_ERROR`. Las de negocio no
 * estan aqui: van en los casos de uso y producen `409` con su codigo.
 */

data class HouseholdInput(
    @field:NotBlank val name: String,
    @field:NotBlank val timeZone: String,
    @field:Valid val admin: AdminInput,
)

data class AdminInput(
    @field:NotBlank val name: String,
    @field:NotBlank @field:Email val email: String,
    // El minimo va tambien aqui, no solo en la politica de dominio: asi un
    // cuerpo mal formado se rechaza como error de FORMA (400) sin llegar a
    // gastar un hash de Argon2id.
    @field:Size(min = 12) val password: String,
)

data class TokenPairResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
) {
    companion object {
        fun of(pair: TokenPair) = TokenPairResponse(pair.accessToken, pair.refreshToken, pair.expiresIn)
    }
}

data class TokenInput(@field:NotBlank val token: String)

data class EmailInput(@field:NotBlank @field:Email val email: String)

data class LoginInput(
    @field:NotBlank @field:Email val email: String,
    // Sin minimo declarado a proposito: aqui se comprueba una credencial
    // existente, no se fija una nueva. Rechazarla por forma daria 400 donde debe
    // darse 401.
    @field:NotBlank val password: String,
)

data class RefreshInput(@field:NotBlank val refreshToken: String)

data class PasswordResetConfirmInput(
    @field:NotBlank val token: String,
    @field:Size(min = 12) val newPassword: String,
)

data class ChangePasswordInput(
    @field:NotBlank val currentPassword: String,
    @field:Size(min = 12) val newPassword: String,
)

data class InvitationInput(
    @field:NotBlank @field:Email val email: String,
    val role: MemberRole,
)

data class InvitationAcceptInput(
    @field:NotBlank val token: String,
    val name: String? = null,
    val password: String? = null,
)

data class RoleInput(val role: MemberRole)

data class InvitationResponse(
    val id: UUID,
    val email: String,
    val role: MemberRole,
    val expiresAt: Instant,
    val acceptedAt: Instant?,
    val revokedAt: Instant?,
    val createdAt: Instant,
    val createdBy: UUID?,
) {
    companion object {
        fun of(invitation: Invitation) = InvitationResponse(
            id = invitation.id,
            email = invitation.email.value,
            role = invitation.role,
            expiresAt = invitation.expiresAt,
            acceptedAt = invitation.acceptedAt,
            revokedAt = invitation.revokedAt,
            createdAt = invitation.createdAt,
            createdBy = invitation.createdBy,
        )
    }
}

/**
 * Una **pertenencia**, no una identidad: `id` es el `memberId`, que es lo que
 * referencian el propietario de un asset, los prestamos y la autoria. El nombre y
 * el correo se resuelven desde la identidad, que vive fuera del hogar.
 */
data class UserResponse(
    val id: UUID,
    val identityId: UUID,
    val name: String,
    val email: String,
    val phone: String?,
    val role: MemberRole,
    val avatarUrl: String?,
    val lastLoginAt: Instant?,
    val emailVerifiedAt: Instant?,
    val deactivatedAt: Instant?,
    val createdBy: UUID?,
    val updatedBy: UUID?,
) {
    companion object {
        fun of(user: HouseholdUser, avatarUrl: String?) = UserResponse(
            id = user.member.id,
            identityId = user.identity.id,
            name = user.identity.name,
            email = user.identity.email.value,
            phone = user.identity.phone,
            role = user.member.role,
            // URL firmada, igual que una foto. Nula cuando la persona no ha
            // subido ninguno, que es lo normal.
            avatarUrl = avatarUrl,
            lastLoginAt = user.identity.lastLoginAt,
            emailVerifiedAt = user.identity.emailVerifiedAt,
            deactivatedAt = user.member.deactivatedAt,
            createdBy = user.member.createdBy,
            updatedBy = user.member.updatedBy,
        )
    }
}

data class CategoryInput(
    @field:NotBlank @field:Size(max = 120) val name: String,
    val notes: String? = null,
)

data class CategoryResponse(
    val id: UUID,
    val name: String,
    val notes: String?,
    val createdAt: Instant,
    val retiredAt: Instant?,
    val createdBy: UUID?,
    val updatedBy: UUID?,
) {
    companion object {
        fun of(category: Category) = CategoryResponse(
            id = category.id,
            name = category.name,
            notes = category.notes,
            createdAt = category.createdAt,
            retiredAt = category.retiredAt,
            createdBy = category.createdBy,
            updatedBy = category.updatedBy,
        )
    }
}

/**
 * La envoltura de paginacion, igual en las once colecciones sin excepcion por
 * tamano esperado: una sola forma que aprender en el cliente, y ninguna
 * migracion el dia que una lista que se creia pequena deje de serlo.
 */
data class PageResponse<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val total: Long,
) {
    companion object {
        fun <D, T> of(page: Page<D>, map: (D) -> T) =
            PageResponse(page.items.map(map), page.page, page.size, page.total)
    }
}

/** Forma unica de todo error de la API. */
data class ErrorResponse(
    val code: String,
    val message: String,
    val details: Map<String, String>? = null,
)
