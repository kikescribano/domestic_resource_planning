package com.drp.core.adapter.security

import com.drp.core.application.port.AccessTokenIssuer
import com.drp.core.application.port.SessionClaims
import com.drp.platform.tenant.TenantContext
import com.drp.core.application.usecase.AuthenticateLoanToken
import com.drp.core.application.usecase.LoanAccess
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

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
    private val loanTokens: AuthenticateLoanToken,
    private val tenantContext: TenantContext,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val bearer = request.bearerToken()

        if (bearer == null) {
            filterChain.doFilter(request, response)
            return
        }

        val claims = accessTokens.verify(bearer)

        if (claims != null) {
            authenticated(AuthenticatedUser(claims), claims.householdId, request, response, filterChain)
            return
        }

        // No es una sesion del hogar. Puede ser el token acotado de un prestamo,
        // que es la otra credencial de la API. Se intenta **despues** y no antes
        // porque la de sesion es la habitual, y porque resolverla no cuesta una
        // consulta.
        val access = loanTokens.authenticate(bearer)

        if (access != null && request.withinScopeOf(access)) {
            authenticated(ExternalLoanUser(access), access.householdId, request, response, filterChain)
            return
        }

        // Sin credencial valida no se autentica y no se fija hogar. No se responde
        // 401 desde aqui: hay endpoints anonimos legitimos, y quien decide si
        // esta peticion necesitaba credencial es la cadena de seguridad.
        //
        // Un token de prestamo fuera de su alcance cae aqui igual que uno
        // invalido, y eso es lo que hace que **no exista** fuera de sus dos
        // operaciones: no es que se le deniegue el acceso al listado de assets,
        // es que ahi no es una credencial.
        filterChain.doFilter(request, response)
    }

    private fun authenticated(
        principal: AbstractAuthenticationToken,
        householdId: UUID,
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        SecurityContextHolder.getContext().authentication = principal
        try {
            tenantContext.runAs(householdId) {
                filterChain.doFilter(request, response)
            }
        } finally {
            SecurityContextHolder.clearContext()
        }
    }

    /**
     * Las **dos** operaciones que alcanza un token de prestamo, y solo del
     * prestamo para el que se emitio.
     *
     * Se comprueba aqui y no en el controlador a proposito. En el controlador
     * seria una autorizacion --el token es valido pero no puede hacer eso-- y
     * habria que acordarse de escribirla en cada endpoint nuevo. Aqui es
     * autenticacion: fuera de su alcance el token simplemente no identifica a
     * nadie, asi que un endpoint que se anada manana nace protegido sin que nadie
     * se acuerde de nada.
     *
     * El identificador de la ruta tiene que ser **el suyo**. Sin esa
     * comparacion, el token del prestamo A serviria para leer el B, que es
     * exactamente el agujero que este metodo existe para cerrar.
     */
    private fun HttpServletRequest.withinScopeOf(access: LoanAccess): Boolean {
        val path = servletPath.trimEnd('/')
        val own = "/api/v1/loans/${access.loanId}"

        return when (method.uppercase()) {
            "GET" -> path == own
            "POST" -> path == "$own/return"
            else -> false
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

/**
 * Quien llega con el token acotado de un prestamo.
 *
 * **No tiene rol de hogar**, y no por olvido: no pertenece a ninguno. Su unica
 * autoridad es `ROLE_LOAN_EXTERNAL`, que no aparece en ningun `@PreAuthorize`
 * del core, asi que no abre nada por si sola. Lo que le da acceso a sus dos
 * operaciones es que el filtro lo autentique, y solo lo autentica ahi.
 *
 * El `principal` es el [LoanAccess], que lleva el prestamo dentro. Por eso las
 * dos operaciones externas reciben la credencial entera en vez de un
 * identificador de la ruta: **el prestamo sale de la credencial**, no de lo que
 * el cliente escriba.
 */
class ExternalLoanUser(private val access: LoanAccess) : AbstractAuthenticationToken(
    listOf(SimpleGrantedAuthority("ROLE_LOAN_EXTERNAL")),
) {
    init {
        isAuthenticated = true
    }

    override fun getPrincipal(): LoanAccess = access

    override fun getCredentials(): Any? = null
}
