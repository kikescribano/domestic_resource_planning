package com.drp.core.adapter.security

import com.drp.config.SecurityConfig
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Prueba de contrato del adaptador de contrasenas.
 *
 * Se construye con la configuracion real --la del [SecurityConfig]-- y no con un
 * codificador de juguete, porque lo que se quiere medir son justo sus
 * parametros: un Argon2id con otra memoria u otras iteraciones pasaria estas
 * pruebas siendo otra cosa.
 */
class PasswordHasherTest {

    private val hasher = SpringPasswordHasher(SecurityConfig().passwordEncoder())

    @Test
    @DisplayName("el hash lleva delante su algoritmo, que es lo que deja migrar sin invalidar nada")
    fun `el hash va prefijado`() {
        hasher.hash("una frase larga de prueba").shouldStartWith("{argon2}")
    }

    @Test
    @DisplayName("la misma contrasena da hashes distintos y ambos validan")
    fun `cada hash lleva su propia sal`() {
        val password = "el gato duerme en el sofa"

        val first = hasher.hash(password)
        val second = hasher.hash(password)

        first.shouldNotBe(second)
        hasher.matches(password, first).shouldBeTrue()
        hasher.matches(password, second).shouldBeTrue()
    }

    @Test
    @DisplayName("dos contrasenas de mas de 72 bytes que comparten los primeros 72 no se confunden")
    fun `no hay truncado a los 72 bytes`() {
        // Es exactamente lo que BCrypt no garantiza: ignora en silencio todo lo
        // que pase de 72 bytes --no falla, trunca-- asi que estas dos serian la
        // misma contrasena para el sistema. Con una politica que favorece frases
        // largas eso deja de ser un caso de laboratorio.
        val shared = "a".repeat(72)
        val first = shared + "primera cola distinta"
        val second = shared + "segunda cola distinta"

        val hash = hasher.hash(first)

        hasher.matches(first, hash).shouldBeTrue()
        hasher.matches(second, hash).shouldBeFalse()
    }

    @Test
    @DisplayName("una contrasena equivocada no valida")
    fun `rechaza la contrasena equivocada`() {
        val hash = hasher.hash("doce  chars!")

        hasher.matches("otra cosa distinta", hash).shouldBeFalse()
    }
}
