package com.drp.module.suppliers

import com.drp.platform.module.ModuleDescriptor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * La declaracion del modulo, que es lo unico que el Hito 0 deja en este arbol.
 *
 * Su dominio, sus tablas y sus pantallas llegan con su hito y con su ficha en
 * `docs/backend/modules/`, escrita antes que la primera linea de codigo. Lo que
 * hace falta ya es que **exista en el catalogo**: sin descriptor no se puede
 * encender, y el mecanismo de activacion no tendria nada que activar.
 *
 * El prefijo de ruta lo declara el modulo y no plataforma, que es lo que obliga
 * a que el prefijo que el gate protege y el que el modulo publique sean por
 * fuerza el mismo. Hasta el Hito 2, todo lo que cuelga de el responde `403
 * MODULE_INACTIVE` si el modulo esta apagado y `404` si esta encendido: no hay
 * controlador todavia.
 */
@Configuration
class SuppliersModule {

    @Bean
    fun suppliersModuleDescriptor() = ModuleDescriptor(
        key = "SUPPLIERS",
        name = "Proveedores y contactos de servicio",
        description = "Quién arregla, quién cobra y quién responde de una garantía: el fontanero, el servicio técnico de la caldera, el taller.",
        routePrefix = "/api/v1/suppliers",
    )
}
