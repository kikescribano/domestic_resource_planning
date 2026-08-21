package com.drp.config

import com.drp.core.adapter.http.FileLinkProperties
import com.drp.core.adapter.http.SignedFileUrls
import com.drp.core.adapter.security.JwtAuthenticationFilter
import com.drp.core.adapter.security.SecurityProperties
import com.drp.platform.module.http.ModuleGateFilter
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
import org.springframework.security.web.access.intercept.AuthorizationFilter
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

    /**
     * Lo mismo para el gate de modulos, y aqui la segunda pasada **si** haria
     * dano: registrado por Spring Boot correria fuera de la cadena de seguridad
     * --antes de que nadie haya autenticado-- y ahi no hay hogar en el contexto,
     * asi que todo modulo pareceria apagado y una peticion sin token a la ruta de
     * un modulo respondería 403 en lugar de 401.
     */
    @Bean
    fun moduleGateServletRegistration(
        filter: ModuleGateFilter,
    ): FilterRegistrationBean<ModuleGateFilter> =
        FilterRegistrationBean(filter).apply { isEnabled = false }

    @Bean
    fun securityProperties(
        environment: Environment,
        @Value("\${drp.security.jwt.secret}") jwtSecret: String,
        @Value("\${drp.security.jwt.access-token-ttl}") accessTokenTtl: Duration,
        @Value("\${drp.security.jwt.refresh-token-ttl}") refreshTokenTtl: Duration,
    ): SecurityProperties =
        SecurityProperties(jwtSecret, accessTokenTtl, refreshTokenTtl)
            .also { it.validate(developmentEnvironment = environment.isDevelopment()) }

    /**
     * El secreto que firma las URL de los ficheros, validado **igual que el del
     * JWT** y por el mismo motivo.
     *
     * Hasta ahora no lo validaba nadie: se inyectaba directo en [SignedFileUrls]
     * con el valor de ejemplo del `application.yml` de respaldo. Un despliegue
     * que olvidara `DRP_FILES_LINK_SECRET` firmaba con un secreto **publicado en
     * el repositorio** --y en el `compose.yaml`, en claro-- de modo que
     * cualquiera podia forjar la firma de cualquier ruta con la caducidad que
     * quisiera. La de quince minutos dejaba de significar nada, y las claves de
     * avatar se derivan solo del identityId: basta conocer ese UUID para
     * construir la URL entera.
     *
     * Que el del JWT tuviera esta comprobacion y este no era la desigualdad que
     * lo hacia peligroso: el fallo silencioso estaba descrito y remediado en una
     * punta y no en la otra.
     */
    @Bean
    fun fileLinkProperties(
        environment: Environment,
        @Value("\${drp.files.link-secret}") linkSecret: String,
    ): FileLinkProperties =
        FileLinkProperties(linkSecret)
            .also { it.validate(developmentEnvironment = environment.isDevelopment()) }

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        jwtFilter: JwtAuthenticationFilter,
        moduleGate: ModuleGateFilter,
    ): SecurityFilterChain {
        http
            // La API no usa cookies de sesion, asi que no hay nada que un sitio
            // ajeno pueda hacer enviar al navegador por su cuenta: el token va en
            // una cabecera que solo pone el propio cliente.
            .csrf { it.disable() }
            // Sin CORS, y es decision y no olvido (hallazgo 11 de la auditoria
            // OWASP): no existe ninguna peticion cross-origin en ninguna
            // topologia del proyecto. El frontend llama a /api en relativo
            // siempre; en desarrollo el servidor de Vite hace de proxy y en
            // produccion nginx sirve la SPA y /api desde el mismo origen
            // (ADR-016). Las imagenes firmadas del otro origen viajan por
            // <img>, que no participa en CORS. Sin configuracion se falla
            // cerrado: un origen ajeno no recibe cabeceras Access-Control-* y
            // el navegador bloquea. La configuracion de desarrollo que hubo
            // aqui --localhost cableado, allowedHeaders("*") y credenciales--
            // solo anadia superficie, y el dia que un origen remoto exista de
            // verdad esto vuelve como propiedad de despliegue, no como una
            // lista escrita en el codigo.
            .cors { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { requests ->
                requests
                    // Swagger UI y OpenAPI - documentacion interactiva (lectura solamente)
                    .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**", "/openapi.yaml").permitAll()
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
                    // La entrega de imagenes firmadas. **No lleva token a
                    // proposito**: un `<img src>` no puede enviar la cabecera
                    // `Authorization`, y de ahi toda la ADR-005. Lo que autoriza
                    // es la firma de la URL, que cubre ruta y caducidad, y que en
                    // un despliegue de verdad verifica nginx sin que esta ruta
                    // llegue nunca a la aplicacion.
                    .requestMatchers(HttpMethod.GET, "/f/**").permitAll()
                    .anyRequest().authenticated()
            }
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter::class.java)
            // El gate de modulos va **detras de la autorizacion**, que es la
            // ultima parada de la cadena. Puesto antes, una peticion sin token a
            // la ruta de un modulo responderia `403 MODULE_INACTIVE` --sin hogar
            // en el contexto no hay ningun modulo activo-- en lugar del `401` que
            // le corresponde. Aqui solo llega lo que ya esta autenticado y
            // autorizado, y lo unico que queda por decidir es si ese hogar tiene
            // encendido ese modulo.
            .addFilterAfter(moduleGate, AuthorizationFilter::class.java)
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

    private fun Environment.isDevelopment(): Boolean = isDevelopmentEnvironment(activeProfiles)

    companion object {
        val DEVELOPMENT_PROFILES = setOf("dev", "test", "local")

        /**
         * Si el arranque tolera los secretos de ejemplo del repositorio.
         *
         * **Solo un perfil declarado a proposito cuenta como desarrollo, y la
         * ausencia de perfil es produccion.** Antes era al reves --sin perfil
         * activo se asumia desarrollo, porque asi arrancaba `bootRun`-- y eso
         * convertia la comprobacion en decorativa justo donde tenia que morder:
         * un despliegue que olvidara `SPRING_PROFILES_ACTIVE` arrancaba **sin un
         * solo aviso** firmando con la clave publicada en el repositorio, que
         * mide 43 bytes y pasaba de sobra el minimo de longitud. Y con el
         * householdId del token alimentando `app.household_id`, forjar uno
         * atraviesa las dos capas de aislamiento a la vez.
         *
         * El precio de invertirlo es que **desarrollo y pruebas tienen que
         * declararse**, y por eso lo hacen desde la cadena de construccion y no
         * clase a clase: `bootRun` arranca con `dev` y la tarea de pruebas fija
         * `test` (ver `build.gradle.kts`). Es un sitio cada uno, y olvidarlo
         * falla ruidosamente al arrancar en vez de en silencio.
         */
        fun isDevelopmentEnvironment(activeProfiles: Array<String>): Boolean =
            activeProfiles.any { it in DEVELOPMENT_PROFILES }

        private const val ARGON2 = "argon2"

        // Los tres de OWASP. Memoria en KiB, que es como los cuenta Argon2.
        private const val MEMORY_KIB = 19 * 1024
        private const val ITERATIONS = 2
        private const val PARALLELISM = 1

        private const val SALT_BYTES = 16
        private const val HASH_BYTES = 32
    }
}
