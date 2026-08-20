package com.drp.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
class CorsConfig {

    /**
     * Configuración CORS para desarrollo. Permite peticiones desde
     * localhost sin restricciones de origen. En producción debería ser
     * más restrictiva, especificando solo los orígenes permitidos.
     */
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        // Con allowCredentials, los comodines exigen patrones y no origenes
        // literales. El patron de 192.168.* deja probar desde otro dispositivo
        // de la red local (movil contra el Vite con --host).
        configuration.allowedOriginPatterns = listOf(
            "http://localhost:*",   // Frontend Vite y Swagger UI
            "http://127.0.0.1:*",
            "http://192.168.*:*",   // Otros dispositivos de la red local
        )
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        configuration.allowedHeaders = listOf("*")
        configuration.allowCredentials = true
        configuration.maxAge = 3600

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/api/**", configuration)
        source.registerCorsConfiguration("/swagger-ui/**", configuration)
        source.registerCorsConfiguration("/v3/api-docs/**", configuration)
        return source
    }
}
