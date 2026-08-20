package com.drp.core.adapter.http

import com.drp.core.application.TokenPair
import com.drp.platform.page.Page
import com.drp.core.application.usecase.HouseholdUser
import com.drp.core.application.usecase.CategoryCommand
import com.drp.core.domain.catalog.Category
import com.drp.core.domain.catalog.CategoryColor
import com.drp.core.domain.catalog.CategoryIcon
import com.drp.core.domain.catalog.Tag
import com.drp.core.domain.household.Household
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

/**
 * El estado del hogar (`GET /households/current`).
 *
 * Es la primera lectura del hogar que tiene el contrato: hasta la baja de hogar
 * solo se podia crear uno. Devuelve lo que lo describe --nombre y zona horaria--
 * y, sobre todo, [closure], que es lo que la zona de peligro y el aviso
 * persistente del frontend necesitan saber.
 */
data class HouseholdResponse(
    val id: UUID,
    val name: String,
    val timeZone: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    /** Informada solo mientras corre el periodo de gracia. Nula es lo normal. */
    val closure: HouseholdClosureResponse?,
) {
    companion object {
        fun of(household: Household) = HouseholdResponse(
            id = household.id,
            name = household.name,
            timeZone = household.timeZone.id,
            createdAt = household.createdAt,
            updatedAt = household.updatedAt,
            closure = household.closure?.let {
                HouseholdClosureResponse(it.requestedAt, it.requestedBy, it.effectiveAt)
            },
        )
    }
}

/**
 * La baja solicitada.
 *
 * `effectiveAt` es **la fecha que se le prometio a una persona**, y viaja como
 * instante para que el cliente la pinte en la zona de quien mira. `requestedBy`
 * apunta a la **pertenencia**, como toda la autoria del contrato.
 */
data class HouseholdClosureResponse(
    val requestedAt: Instant,
    val requestedBy: UUID,
    val effectiveAt: Instant,
)

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
    /**
     * La cara de la categoria. Los dos son opcionales y **ausente es quitar**,
     * no conservar: el cuerpo de esta operacion es la categoria entera y no un
     * parche campo a campo, igual que ya pasaba con `notes` desde el Hito 2.
     */
    val icon: CategoryIcon? = null,
    val color: CategoryColor? = null,
) {
    fun toCommand() = CategoryCommand(name = name, notes = notes, icon = icon, color = color)
}

data class CategoryResponse(
    val id: UUID,
    val name: String,
    val notes: String?,
    val icon: CategoryIcon?,
    val color: CategoryColor?,
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
            icon = category.icon,
            color = category.color,
            createdAt = category.createdAt,
            retiredAt = category.retiredAt,
            createdBy = category.createdBy,
            updatedBy = category.updatedBy,
        )
    }
}

data class TagInput(@field:NotBlank @field:Size(max = 60) val name: String)

/**
 * Una etiqueta tal y como sale del catalogo.
 *
 * **Dentro de un asset se devuelve la misma forma**, con su identificador y su
 * nombre, y no solo el nombre: el filtro del listado va por identificador, asi
 * que devolver solo el texto obligaria a la pantalla a buscar la etiqueta en el
 * catalogo para poder filtrar por lo que acaba de pintar.
 */
data class TagResponse(
    val id: UUID,
    val name: String,
    val createdAt: Instant,
    val retiredAt: Instant?,
    val createdBy: UUID?,
    val updatedBy: UUID?,
) {
    companion object {
        fun of(tag: Tag) = TagResponse(
            id = tag.id,
            name = tag.name,
            createdAt = tag.createdAt,
            retiredAt = tag.retiredAt,
            createdBy = tag.createdBy,
            updatedBy = tag.updatedBy,
        )
    }
}

/** Forma unica de todo error de la API. */
data class ErrorResponse(
    val code: String,
    val message: String,
    val details: Map<String, String>? = null,
)
