package com.drp.config

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Que cuenta como desarrollo, que es lo que decide si los secretos de ejemplo
 * del repositorio se toleran.
 *
 * La prueba que de verdad importa es la primera, y fija una **inversion**: antes
 * la ausencia de perfil se tomaba por desarrollo --porque asi arrancaba
 * `bootRun`-- y con ello un despliegue que olvidara declarar el suyo arrancaba
 * en silencio firmando con una clave publicada. La comprobacion existia y no
 * mordia justo donde tenia que morder.
 */
class DevelopmentEnvironmentTest {

    @Test
    @DisplayName("sin ningun perfil declarado se considera produccion, no desarrollo")
    fun `la ausencia de perfil es produccion`() {
        SecurityConfig.isDevelopmentEnvironment(emptyArray()).shouldBe(false)
    }

    @Test
    @DisplayName("los tres perfiles de desarrollo toleran los secretos de ejemplo")
    fun `los perfiles declarados son desarrollo`() {
        SecurityConfig.DEVELOPMENT_PROFILES.forEach { profile ->
            SecurityConfig.isDevelopmentEnvironment(arrayOf(profile)).shouldBe(true)
        }
    }

    @Test
    @DisplayName("un perfil de despliegue no tolera los secretos de ejemplo")
    fun `produccion no es desarrollo`() {
        SecurityConfig.isDevelopmentEnvironment(arrayOf("prod")).shouldBe(false)
        SecurityConfig.isDevelopmentEnvironment(arrayOf("vps", "metrics")).shouldBe(false)
    }

    @Test
    @DisplayName("basta uno de desarrollo entre varios activos")
    fun `un perfil de desarrollo entre varios cuenta`() {
        // Es lo que pasa al arrancar en local con un perfil extra para alguna
        // prueba manual: sigue siendo desarrollo.
        SecurityConfig.isDevelopmentEnvironment(arrayOf("dev", "metrics")).shouldBe(true)
    }
}
