package com.drp.domain.identity

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Escenarios tomados literalmente de la seccion 7 del README.
 */
class PasswordPolicyTest {

    private val policy = PasswordPolicy(setOf("contrasena123", "password1234", "qwertyuiopas"))

    @Test
    @DisplayName("acepta una frase larga sin mayusculas ni simbolos")
    fun `acepta una frase larga sin mayusculas ni simbolos`() {
        policy.validate("el gato duerme en el sofa") shouldBe PasswordValidation.Valid
    }

    @Test
    @DisplayName("rechaza una de 11 caracteres por larga que sea la lista de requisitos que cumpla")
    fun `rechaza una de once caracteres`() {
        policy.validate("Abc1!Def2@G") shouldBe PasswordValidation.TooShort
    }

    @Test
    @DisplayName("acepta exactamente en el minimo, que es un limite y no una barrera")
    fun `acepta exactamente doce caracteres`() {
        policy.validate("doce  chars!") shouldBe PasswordValidation.Valid
    }

    @Test
    @DisplayName("rechaza una de la lista de comunes aunque supere los 12 caracteres")
    fun `rechaza una comun aunque sea larga`() {
        policy.validate("contrasena123") shouldBe PasswordValidation.TooCommon
    }

    @Test
    @DisplayName("la lista de comunes no distingue mayusculas: cambiar la caja no la salva")
    fun `la lista de comunes ignora la caja`() {
        policy.validate("ConTraSena123") shouldBe PasswordValidation.TooCommon
    }

    @Test
    @DisplayName("no impone composicion: una frase de solo minusculas y espacios vale")
    fun `no impone composicion`() {
        policy.validate("aaaaaaaaaaaaaaa") shouldBe PasswordValidation.Valid
    }
}
