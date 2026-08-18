package com.drp.platform.module.http

import com.drp.core.application.port.SessionClaims
import com.drp.platform.module.ActivateModule
import com.drp.platform.module.DeactivateModule
import com.drp.platform.module.ListModules
import com.drp.platform.module.ModuleView
import com.drp.platform.page.PageResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/**
 * Las tres operaciones de la activacion.
 *
 * `/api/v1/modules` **no cuelga de ningun prefijo de modulo**, y no puede
 * colgar: es la ruta con la que se enciende uno, asi que un gate sobre ella
 * dejaria un modulo apagado sin forma de encenderse.
 *
 * La activacion es una **sub-recurso** del modulo y no un `PATCH` de su estado.
 * Asi encender y apagar son dos operaciones distintas con su propio
 * `operationId`, que es lo que la ADR-007 necesita para generar el cliente, y
 * no una sola cuyo efecto dependa del cuerpo.
 */
@RestController
@RequestMapping("/api/v1/modules")
class ModuleController(
    private val listModules: ListModules,
    private val activateModule: ActivateModule,
    private val deactivateModule: DeactivateModule,
) {

    /**
     * El catalogo entero con el estado del hogar. **Sin restriccion de rol**: ver
     * que existe un modulo no es poder encenderlo, y quien no administra necesita
     * saber que hay para poder pedirlo.
     */
    @GetMapping
    fun list(): PageResponse<ModuleResponse> {
        val modules = listModules.handle().map(ModuleResponse::of)
        return PageResponse(modules, page = 0, size = modules.size, total = modules.size.toLong())
    }

    /**
     * Solo administra quien administra. El `@PreAuthorize` va en las dos
     * operaciones que **cambian** algo y no en el listado, que es la decision de
     * producto que este hito tomo.
     */
    @PostMapping("/{key}/activation")
    @PreAuthorize("hasRole('HOUSEHOLD_ADMIN')")
    fun activate(
        @AuthenticationPrincipal session: SessionClaims,
        @PathVariable key: String,
    ): ModuleResponse = ModuleResponse.of(activateModule.handle(session, key))

    /**
     * Apagar **no borra nada**. La respuesta lleva la fila con su nuevo estado
     * justamente para que la pantalla pueda decirlo: los datos siguen ahi y
     * volver a encenderlo los devuelve.
     */
    @DeleteMapping("/{key}/activation")
    @PreAuthorize("hasRole('HOUSEHOLD_ADMIN')")
    fun deactivate(
        @AuthenticationPrincipal session: SessionClaims,
        @PathVariable key: String,
    ): ModuleResponse = ModuleResponse.of(deactivateModule.handle(session, key))
}

data class ModuleResponse(
    val key: String,
    val name: String,
    val description: String,
    val routePrefix: String,
    val status: String,
    val activatedAt: Instant?,
    val deactivatedAt: Instant?,
) {
    companion object {
        fun of(view: ModuleView) = ModuleResponse(
            key = view.descriptor.key,
            name = view.descriptor.name,
            description = view.descriptor.description,
            routePrefix = view.descriptor.routePrefix,
            // Sin fila, el estado es INACTIVE. La ausencia significa apagado, y
            // la respuesta no tiene por que ensenar esa diferencia: para el
            // cliente son el mismo caso.
            status = if (view.isActive) "ACTIVE" else "INACTIVE",
            activatedAt = view.activation?.activatedAt,
            deactivatedAt = view.activation?.deactivatedAt,
        )
    }
}
