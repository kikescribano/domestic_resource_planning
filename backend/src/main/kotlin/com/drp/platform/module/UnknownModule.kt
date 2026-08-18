package com.drp.platform.module

/**
 * Alguien pidio activar o desactivar una clave que no esta en el catalogo.
 *
 * Es un `404` y se traduce en el `ApiExceptionHandler` del core, que es quien
 * sabe de HTTP. Existe en lugar de reutilizar el `ResourceNotFound` del core por
 * una razon de frontera y no de estilo: es lo unico que plataforma necesitaba de
 * alli aparte de la sesion, y quitarlo deja la regla de ArchUnit con **una sola**
 * excepcion nombrada en lugar de dos.
 */
class UnknownModule(moduleKey: String) : RuntimeException("El módulo «$moduleKey» no existe")
