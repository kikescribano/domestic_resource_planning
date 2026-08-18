package com.drp.module.suppliers

import com.drp.platform.module.ModuleDescriptor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * La declaracion del modulo: lo que le permite existir en el catalogo que un
 * hogar puede encender.
 *
 * El Hito 0 dejo aqui solo esto, sin dominio; el **Hito 2 lo ha llenado** y es el
 * primero de los cuatro que recorre el camino entero. Su ficha esta en
 * `docs/backend/modules/suppliers.md` y se escribio antes que la primera linea de
 * codigo, que es lo que el catalogo exige: un modulo declara sus limites antes de
 * que otro dependa de el.
 *
 * El prefijo de ruta lo declara el modulo y no plataforma, que es lo que obliga a
 * que el prefijo que el gate protege y el que el controlador publica sean por
 * fuerza el mismo. Desde este hito hay un controlador de verdad detras, asi que
 * es tambien **la primera vez que el `403 MODULE_INACTIVE` tapa algo que
 * existe**: hasta ahora, un modulo encendido respondia `404` porque no habia nada
 * que atender.
 */
@Configuration
class SuppliersModule {

    @Bean
    fun suppliersModuleDescriptor() = ModuleDescriptor(
        key = KEY,
        name = "Proveedores y contactos de servicio",
        description = "Quién arregla, quién cobra y quién responde de una garantía: el fontanero, el servicio técnico de la caldera, el taller.",
        routePrefix = ROUTE_PREFIX,
    )

    companion object {
        const val KEY = "SUPPLIERS"
        const val ROUTE_PREFIX = "/api/v1/suppliers"
    }
}
