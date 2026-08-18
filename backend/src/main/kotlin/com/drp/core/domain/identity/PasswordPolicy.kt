package com.drp.core.domain.identity

/**
 * Politica de contrasenas del core (README 4.1.4).
 *
 * Minimo 12 caracteres y **ninguna regla de composicion**: se favorecen las
 * frases largas frente a la composicion clasica de ocho caracteres con mayuscula,
 * digito y simbolo, que produce secretos mas cortos y mas dificiles de recordar
 * sin ser mas dificiles de adivinar.
 *
 * La unica exclusion adicional es una lista local de contrasenas comunes. Se
 * descarto consultar Have I Been Pwned pese a su cobertura muy superior, porque
 * mete una dependencia externa con latencia y plan B en un camino critico y
 * rompe una instalacion sin salida a internet.
 *
 * Sin caducidad periodica ni historial: la primera produce variaciones triviales
 * del mismo secreto y el segundo obliga a conservar credenciales que ya no hacen
 * falta.
 *
 * La lista se recibe construida. Cargarla es responsabilidad de un adaptador, no
 * del dominio.
 */
class PasswordPolicy(commonPasswords: Set<String>) {

    private val commonPasswords: Set<String> = commonPasswords.mapTo(HashSet()) { it.lowercase() }

    fun validate(password: String): PasswordValidation = when {
        password.length < MIN_LENGTH -> PasswordValidation.TooShort
        password.lowercase() in commonPasswords -> PasswordValidation.TooCommon
        else -> PasswordValidation.Valid
    }

    companion object {
        const val MIN_LENGTH = 12
    }
}

sealed interface PasswordValidation {
    data object Valid : PasswordValidation

    data object TooShort : PasswordValidation

    data object TooCommon : PasswordValidation
}
