package com.drp.config

import com.drp.adapter.security.JwtAuthenticationFilter
import com.drp.adapter.security.SecurityProperties
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import org.springframework.security.crypto.password.DelegatingPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import java.time.Clock
import java.time.Duration

@Configuration
@EnableMethodSecurity
class SecurityConfig {

    /**
     * Argon2id con la configuracion minima que recomienda OWASP: **19 MiB de
     * memoria, 2 iteraciones y grado de paralelismo 1**. Es un suelo, no un
     * objetivo: subirlo es correcto si el hardware lo aguanta, y bajarlo no.
     *
     * Va envuelto en un [DelegatingPasswordEncoder] para que el hash guardado
     * lleve delante su algoritmo --`{argon2}...`--. Eso convierte el cambio de
     * algoritmo en algo reversible: el dia que haya que migrar, los hashes
     * viejos se siguen validando con el suyo mientras los nuevos nacen con el
     * nuevo, en lugar de invalidar la contrasena de todo el mundo a la vez.
     */
    @Bean
    fun passwordEncoder(): PasswordEncoder {
        val argon2 = Argon2PasswordEncoder(SALT_BYTES, HASH_BYTES, PARALLELISM, MEMORY_KIB, ITERATIONS)
        return DelegatingPasswordEncoder(ARGON2, mapOf(ARGON2 to argon2))
    }

    @Bean
    fun clock(): Clock = Clock.systemUTC()

    /**
     * Impide que el filtro de JWT se registre **dos veces**.
     *
     * Al ser un `@Component` de tipo `Filter`, Spring Boot lo registra por su
     * cuenta en el contenedor de servlets, y ademas se anade a la cadena de
     * seguridad mas abajo. Hoy la segunda pasada no hace nada --`OncePerRequestFilter`
     * la descarta-- pero el dia que el filtro rechace una peticion, hacerlo dos
     * veces y en dos sitios distintos deja de ser inocuo.
     *
     * Su sitio es la cadena de seguridad, donde el orden respecto a la
     * autorizacion esta declarado; el registro automatico lo pondria fuera de
     * ella. De ahi que se desactive este y no el otro.
     */
    @Bean
    fun jwtFilterServletRegistration(
        filter: JwtAuthenticationFilter,
    ): FilterRegistrationBean<JwtAuthenticationFilter> =
        FilterRegistrationBean(filter).apply { isEnabled = false }

    @Bean
    fun securityProperties(
        environment: Environment,
        @Value("\${drp.security.jwt.secret}") jwtSecret: String,
        @Value("\${drp.security.jwt.access-token-ttl}") accessTokenTtl: Duration,
        @Value("\${drp.security.jwt.refresh-token-ttl}") refreshTokenTtl: Duration,
    ): SecurityProperties {
        // Sin ningun perfil activo se considera desarrollo, que es como arranca
        // `./gradlew bootRun` en una maquina local. Un despliegue de verdad
        // declara su perfil, y ahi la clave de ejemplo deja de valer.
        val development = environment.activeProfiles.isEmpty() ||
            environment.activeProfiles.any { it in DEVELOPMENT_PROFILES }

        return SecurityProperties(jwtSecret, accessTokenTtl, refreshTokenTtl)
            .also { it.validate(developmentEnvironment = development) }
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity, jwtFilter: JwtAuthenticationFilter): SecurityFilterChain {
        http
            // La API no usa cookies de sesion, asi que no hay nada que un sitio
            // ajeno pueda hacer enviar al navegador por su cuenta: el token va en
            // una cabecera que solo pone el propio cliente.
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { requests ->
                requests
                    // Los ocho endpoints sin autenticar del contrato. Cada uno
                    // lleva lo suyo --el login credenciales, la verificacion y la
                    // aceptacion un token de un solo uso recibido por correo--
                    // salvo el alta de un hogar, que es la unica escritura sin
                    // credencial alguna de toda la API porque es lo que da
                    // existencia a un inquilino.
                    .requestMatchers(HttpMethod.POST, "/api/v1/households").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/verify-email").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/resend-verification").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/password-reset").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/password-reset/confirm").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/invitations/accept").permitAll()
                    .anyRequest().authenticated()
            }
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter::class.java)
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            // Sin esto, Spring Security responde 403 a una peticion sin
            // credencial --su comportamiento por defecto cuando no hay forma de
            // pedirlas-- y el contrato declara 401 para "falta el token, ha
            // caducado o no es valido". No es cosmetico: el cliente distingue por
            // el codigo si tiene que renovar la sesion (401) o si simplemente no
            // le corresponde esa operacion (403), y confundirlos deja al frontend
            // reintentando lo que nunca va a poder hacer.
            //
            // Estos dos manejadores cubren lo que se decide en la cadena de
            // filtros. Lo que rechaza @PreAuthorize ya dentro del controlador lo
            // traduce el ApiExceptionHandler, y las dos rutas responden igual.
            .exceptionHandling { handling ->
                handling
                    .authenticationEntryPoint { _, response, _ ->
                        response.writeError(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Falta el token o no es válido")
                    }
                    .accessDeniedHandler { _, response, _ ->
                        response.writeError(HttpStatus.FORBIDDEN, "FORBIDDEN", "No autorizado para esta operación")
                    }
            }

        return http.build()
    }

    private fun HttpServletResponse.writeError(status: HttpStatus, code: String, message: String) {
        this.status = status.value()
        contentType = MediaType.APPLICATION_JSON_VALUE
        characterEncoding = Charsets.UTF_8.name()
        writer.write("""{"code":"$code","message":"$message"}""")
    }

    private companion object {
        val DEVELOPMENT_PROFILES = setOf("dev", "test", "local")

        const val ARGON2 = "argon2"

        // Los tres de OWASP. Memoria en KiB, que es como los cuenta Argon2.
        const val MEMORY_KIB = 19 * 1024
        const val ITERATIONS = 2
        const val PARALLELISM = 1

        const val SALT_BYTES = 16
        const val HASH_BYTES = 32
    }
}
