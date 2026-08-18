package com.drp.platform.page

/**
 * La envoltura de paginacion, igual en todas las colecciones sin excepcion por
 * tamano esperado: una sola forma que aprender en el cliente, y ninguna
 * migracion el dia que una lista que se creia pequena deje de serlo.
 *
 * Vive junto a [Page] y no en el core por lo mismo que ella: la declara el
 * contrato para **toda** coleccion, tambien las de los modulos, y dejarla en el
 * core obligaria a cada modulo a importar del core para devolver una lista.
 */
data class PageResponse<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val total: Long,
) {
    companion object {
        fun <D, T> of(page: Page<D>, map: (D) -> T) =
            PageResponse(page.items.map(map), page.page, page.size, page.total)
    }
}
