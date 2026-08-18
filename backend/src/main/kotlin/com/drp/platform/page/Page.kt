package com.drp.platform.page

/**
 * Pagina de resultados, con la misma envoltura que devuelve la API.
 *
 * Vive en `com.drp.platform` y no en el core porque es vocabulario compartido:
 * todo listado del contrato --el del core y el de cualquier modulo-- responde
 * `{ items, page, size, total }`. Dejarla en el core obligaria a cada modulo a
 * importar del core para paginar, que es una dependencia que no dice nada.
 */
data class Page<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val total: Long,
)

data class Pagination(val page: Int, val size: Int)
