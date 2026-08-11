package com.drp.domain.identity

import java.time.Instant
import java.util.UUID

/**
 * Un correo ya normalizado.
 *
 * La normalizacion vive en el tipo y no en cada sitio que compara correos, que
 * es como se cuela el fallo: basta olvidarla una vez --en el login, en la
 * invitacion, en el restablecimiento-- para que `Kike@x.com` y `kike@x.com`
 * pasen a ser dos personas distintas. Al no haber constructor publico, no existe
 * forma de construir uno sin normalizar.
 */
@JvmInline
value class EmailAddress private constructor(val value: String) {

    override fun toString(): String = value

    companion object {
        fun of(raw: String): EmailAddress = EmailAddress(raw.trim().lowercase())
    }
}

/**
 * Quien eres en la instalacion, con independencia de a que hogares pertenezcas.
 *
 * No lleva `householdId` --una persona no pertenece a un hogar, su pertenencia
 * si-- y por eso queda fuera de Row-Level Security. Es la unica tabla con datos
 * personales defendida por una sola capa, asi que su repositorio resuelve
 * siempre por identidad autenticada: nada de listados ni de busquedas por correo
 * fuera del login.
 */
data class Identity(
    val id: UUID,
    val name: String,
    val email: EmailAddress,
    val phone: String?,
    val passwordHash: String,
    val emailVerifiedAt: Instant?,
    val lastLoginAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deactivatedAt: Instant?,
) {
    val isVerified: Boolean get() = emailVerifiedAt != null

    /** La cuenta entera, que es distinto de haber dejado un hogar. */
    val isActive: Boolean get() = deactivatedAt == null

    /**
     * Mientras el correo no este verificado no se puede iniciar sesion, y una
     * cuenta cerrada no se autentica en ningun hogar.
     */
    val canAuthenticate: Boolean get() = isVerified && isActive
}
