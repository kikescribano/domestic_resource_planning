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
        configuration.allowedOrigins = listOf(
            "http://localhost:5173",  // Frontend Vite
            "http://localhost:8080",  // Mismo servidor (Swagger UI)
            "http://127.0.0.1:5173",
            "http://127.0.0.1:8080",
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
