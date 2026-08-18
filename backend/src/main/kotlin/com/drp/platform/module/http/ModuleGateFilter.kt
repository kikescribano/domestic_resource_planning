package com.drp.platform.module.http

import com.drp.platform.module.ModuleActivation
import com.drp.platform.module.ModuleActivationCache
import com.drp.platform.module.ModuleDescriptor
import com.drp.platform.module.ModuleRegistry
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * El gate HTTP: las rutas de un modulo inactivo responden `403 MODULE_INACTIVE`.
 *
 * **`403` y no `404`**, que es la decision que hay detras. Un modulo apagado no
 * es un secreto: el hogar sabe que existe porque lo tiene en su catalogo, y el
 * frontend necesita distinguir «esa ruta no existe» de «actívalo» para poder
 * ofrecer la activacion en lugar de ensenar una pantalla de error.
 *
 * **Es un filtro y no un `HandlerInterceptor`**, que era lo previsto, y el motivo
 * es el mismo por el que el alcance del token de prestamo se comprueba en el
 * filtro y no en el controlador (README 4.1.5): un interceptor solo alcanza a las
 * rutas que ya tienen manejador, asi que una operacion anadida manana bajo el
 * prefijo de un modulo nace **sin** gate hasta que alguien se acuerde de
 * registrarla, y todo lo que un modulo aun no ha construido responderia `404`
 * --justo la confusion que el `403` existe para evitar--. Como filtro, el prefijo
 * entero esta cerrado desde el primer dia, tenga controladores o no.
 *
 * Va **detras de la autorizacion** en la cadena de seguridad. Puesto antes,
 * una peticion sin token a la ruta de un modulo responderia `403 MODULE_INACTIVE`
 * en lugar del `401` que le corresponde: sin hogar en el contexto no hay ningun
 * modulo activo, asi que este filtro no sabe distinguir «no lo tienes encendido»
 * de «no me has dicho quien eres». Quien sabe eso es la cadena, y por eso opina
 * antes.
 *
 * El cuerpo se escribe aqui a mano y no por el `ApiExceptionHandler`: un filtro
 * corre fuera del `DispatcherServlet`, asi que su excepcion no llega a ningun
 * `@RestControllerAdvice`. Es lo mismo que hacen los dos manejadores de la
 * cadena de seguridad, y por el mismo motivo.
 */
@Component
class ModuleGateFilter(
    private val registry: ModuleRegistry,
    private val activation: ModuleActivation,
    private val cache: ModuleActivationCache,
    private val json: ObjectMapper,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        try {
            val module = registry.covering(request.servletPath)

            if (module != null && !activation.isActive(module.key)) {
                response.writeModuleInactive(module)
                return
            }

            filterChain.doFilter(request, response)
        } finally {
            // La peticion se acaba aqui, y con ella lo que se recordo de la
            // activacion. La cache va indexada por hogar, asi que esto no es lo
            // que impide responder por el hogar equivocado --eso lo impide la
            // clave-- sino no dejar memoria colgando de un hilo del pool.
            cache.clear()
        }
    }

    private fun HttpServletResponse.writeModuleInactive(module: ModuleDescriptor) {
        status = HttpStatus.FORBIDDEN.value()
        contentType = MediaType.APPLICATION_JSON_VALUE
        characterEncoding = Charsets.UTF_8.name()
        writer.write(
            json.writeValueAsString(
                mapOf(
                    "code" to MODULE_INACTIVE,
                    "message" to "El módulo «${module.name}» no está activo en este hogar",
                    "details" to mapOf("module" to module.key),
                ),
            ),
        )
    }

    companion object {
        const val MODULE_INACTIVE = "MODULE_INACTIVE"
    }
}
