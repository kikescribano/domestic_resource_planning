package com.drp.platform.module

/**
 * Lo que un modulo declara de si mismo para poder existir en el catalogo.
 *
 * Se declara **en codigo y no en una tabla** a proposito. La clave, el nombre y
 * el prefijo de ruta no son datos del hogar sino propiedades de lo que esta
 * desplegado: guardarlos en la base de datos obligaria a una migracion por
 * modulo nuevo y dejaria la puerta abierta a que la fila y el codigo dijeran
 * cosas distintas. Lo unico que se guarda por hogar es **si esta activo**.
 *
 * Lo declara el propio modulo, no plataforma. Es lo que hace que el prefijo de
 * ruta que el gate protege y el que el controlador publica sean por fuerza el
 * mismo.
 *
 * @param key identificador del modulo. Va en ingles y en `UPPER_SNAKE_CASE`
 *   porque es un identificador y no un dato; lo comprueba tambien la migracion.
 * @param name lo que ve el usuario en la pantalla de modulos. Es un dato, asi
 *   que va en castellano.
 * @param description una frase que explique para que sirve, para quien esta
 *   decidiendo si lo enciende.
 * @param routePrefix el prefijo de ruta bajo el que publica sus operaciones. El
 *   gate responde `403 MODULE_INACTIVE` a **todo** lo que cuelgue de aqui, exista
 *   o no todavia un controlador que lo atienda.
 */
data class ModuleDescriptor(
    val key: String,
    val name: String,
    val description: String,
    val routePrefix: String,
) {
    init {
        require(KEY_SHAPE.matches(key)) { "La clave de un modulo va en UPPER_SNAKE_CASE: «$key»" }
        require(routePrefix.startsWith(API_ROOT)) { "El prefijo de «$key» debe colgar de $API_ROOT" }
        require(!routePrefix.endsWith("/")) { "El prefijo de «$key» no lleva barra final" }
    }

    /** Cierto si [path] es este modulo o algo que cuelga de el. */
    fun covers(path: String): Boolean = path == routePrefix || path.startsWith("$routePrefix/")

    private companion object {
        val KEY_SHAPE = Regex("^[A-Z][A-Z0-9_]*$")
        const val API_ROOT = "/api/v1/"
    }
}
