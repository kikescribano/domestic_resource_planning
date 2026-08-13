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
    val avatar: Avatar?,
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

/**
 * La imagen que retrata a una persona. **No es un `StoredFile`** (README 4.1.1).
 *
 * Y no lo es por una razon que no es de comodidad: una `Identity` **no pertenece
 * a ningun hogar**, asi que su avatar no se puede cargar a ninguna cuota ni
 * proteger con Row-Level Security. De ahi las tres diferencias:
 *
 * - Vive en columnas de la propia `identities`, no en la tabla `files`.
 * - Es **uno solo y siempre se sustituye**, asi que no acumula. Sin acumulacion
 *   posible no hay nada que contar, y por eso su limite no es una cuota sino un
 *   tamano maximo por fichero: 1 MB.
 * - **Cerrar la cuenta lo borra.** Es la unica imagen que retrata a una persona,
 *   y la baja de la identidad es el momento en que deja de haber motivo para
 *   conservarla. Los ficheros del hogar no se van con ella: son del hogar.
 *
 * Va como objeto de valor y no como tres columnas sueltas porque «hay clave de
 * almacenamiento pero no tipo» no es un estado que deba poder representarse.
 */
data class Avatar(
    val storageKey: String,
    val contentType: String,
    val sizeBytes: Long,
) {
    companion object {
        /** Reconstruye desde las columnas. Nulo si no hay avatar. */
        fun from(storageKey: String?, contentType: String?, sizeBytes: Long?): Avatar? =
            if (storageKey != null && contentType != null && sizeBytes != null) {
                Avatar(storageKey, contentType, sizeBytes)
            } else {
                null
            }
    }
}
