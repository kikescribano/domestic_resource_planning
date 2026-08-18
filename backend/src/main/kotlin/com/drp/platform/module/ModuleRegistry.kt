package com.drp.platform.module

import org.springframework.stereotype.Component

/**
 * El catalogo de lo que esta desplegado.
 *
 * Se construye con **todos** los [ModuleDescriptor] que haya en el contexto, que
 * es lo que permite que un modulo entre en el catalogo sin que plataforma tenga
 * que enumerarlo --y lo que deja al modulo de prueba, que vive en el arbol de
 * pruebas, aparecer solo cuando ese arbol esta cargado.
 *
 * Las dos comprobaciones del arranque no son ceremonia. Dos modulos con la misma
 * clave darian una activacion que enciende otra cosa; dos prefijos donde uno
 * cuelga del otro darian una ruta gobernada por el gate de dos modulos a la vez,
 * y cual gana dependeria del orden de los beans. Las dos cosas se ven al arrancar
 * o no se ven nunca.
 */
@Component
class ModuleRegistry(descriptors: List<ModuleDescriptor>) {

    val catalogue: List<ModuleDescriptor> = descriptors.sortedBy { it.key }

    init {
        val duplicated = catalogue.groupBy { it.key }.filterValues { it.size > 1 }.keys
        require(duplicated.isEmpty()) { "Hay modulos que comparten clave: $duplicated" }

        val overlapping = catalogue.filter { module ->
            catalogue.any { other -> other !== module && other.covers(module.routePrefix) }
        }
        require(overlapping.isEmpty()) {
            "Hay prefijos de ruta que se solapan: ${overlapping.map { it.key to it.routePrefix }}"
        }
    }

    fun find(key: String): ModuleDescriptor? = catalogue.firstOrNull { it.key == key }

    /** El modulo bajo cuyo prefijo cae [path], si alguno. */
    fun covering(path: String): ModuleDescriptor? = catalogue.firstOrNull { it.covers(path) }
}
