package com.drp.core.domain.token

import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Un token de un solo uso que viaja por correo.
 *
 * Sirve para la verificacion del correo y para el restablecimiento de
 * contrasena, que se comportan igual pese a vivir en **tablas distintas**. Que
 * sean dos tablas y no una con un campo `purpose` es deliberado: con una sola,
 * un filtro mal escrito convierte un token de verificacion en uno de cambio de
 * contrasena, que es una clase de vulnerabilidad conocida. Compartir el tipo del
 * dominio no reintroduce ese riesgo, porque cada repositorio sigue mirando solo
 * a su tabla.
 *
 * Del token nunca se guarda el valor en claro, solo su hash.
 */
data class SingleUseToken(
    val id: UUID,
    val identityId: UUID,
    val tokenHash: String,
    val expiresAt: Instant,
    val usedAt: Instant?,
) {
    fun isUsableAt(now: Instant): Boolean = usedAt == null && expiresAt.isAfter(now)

    companion object {
        /**
         * El de verificacion dura lo mismo que la retencion del hogar sin
         * verificar, para que reenviarlo siga siendo posible mientras el hogar
         * exista.
         */
        val VERIFICATION_LIFETIME: Duration = Duration.ofDays(7)

        /**
         * El de restablecimiento dura **una hora**, no siete dias: este token
         * cambia una credencial y una invitacion solo propone entrar en un
         * hogar. No corren el mismo riesgo, asi que no merecen el mismo plazo.
         */
        val PASSWORD_RESET_LIFETIME: Duration = Duration.ofHours(1)
    }
}
