package com.drp.adapter.security

import com.drp.application.port.AccessTokenIssuer
import com.drp.application.port.SessionClaims
import com.drp.application.tenant.TenantContext
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Quien hace esta peticion, y en nombre de que hogar.
 *
 * Hace las dos cosas a la vez y en el mismo sitio a proposito. La autenticacion
 * y el contexto de inquilino salen **del mismo token**, asi que separarlas en dos
 * filtros solo crearia la posibilidad de que una se pusiera sin la otra: una
 * peticion autenticada sin hogar leeria cero filas, y --peor-- un hogar puesto
 * sin autenticacion seria acceso sin credencial.
 *
 * El hogar se fija envolviendo el resto de la cadena en `runAs`, que lo restaura
 * al salir pase lo que pase. Un hilo devuelto al pool con el hogar de otra
 * peticion puesto seria una fuga entre inquilinos silenciosa.
 */
@Component
class JwtAuthenticationFilter(
    private val accessTokens: AccessTokenIssuer,
    private val tenantContext: TenantContext,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val claims = request.bearerToken()?.let(accessTokens::verify)

        if (claims == null) {
            // Sin token valido no se autentica y no se fija hogar. No se responde
            // 401 desde aqui: hay endpoints anonimos legitimos, y quien decide si
            // esta peticion necesitaba credencial es la cadena de seguridad.
            filterChain.doFilter(request, response)
            return
        }

        SecurityContextHolder.getContext().authentication = AuthenticatedUser(claims)
        try {
            tenantContext.runAs(claims.householdId) {
                filterChain.doFilter(request, response)
            }
        } finally {
            SecurityContextHolder.clearContext()
        }
    }

    private fun HttpServletRequest.bearerToken(): String? =
        getHeader("Authorization")
            ?.takeIf { it.startsWith(BEARER_PREFIX, ignoreCase = true) }
            ?.substring(BEARER_PREFIX.length)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private companion object {
        const val BEARER_PREFIX = "Bearer "
    }
}

/**
 * El usuario autenticado, con la pertenencia dentro.
 *
 * El `principal` es el [SessionClaims] entero y no solo el identificador porque
 * casi todo lo que el core necesita es el `memberId` --la pertenencia--, no el
 * `sub`: el propietario de un asset, la autoria de una fila y los extremos de un
 * prestamo apuntan a la pertenencia.
 */
class AuthenticatedUser(private val claims: SessionClaims) : AbstractAuthenticationToken(
    listOf(SimpleGrantedAuthority("ROLE_${claims.role.name}")),
) {
    init {
        isAuthenticated = true
    }

    override fun getPrincipal(): SessionClaims = claims

    /**
     * No hay credenciales que guardar despues de autenticar. Devolver el token
     * aqui lo dejaria vivo en memoria durante toda la peticion sin que nadie lo
     * necesite.
     */
    override fun getCredentials(): Any? = null
}
