package com.drp.core.domain.catalog

import java.time.Instant
import java.util.UUID

/**
 * Una etiqueta libre del hogar (README 4.1.1).
 *
 * Es el vocabulario propio con el que una casa clasifica **por mas de una cosa a
 * la vez**. La categoria responde a «que clase de cosa es esto» y es una sola;
 * la etiqueta responde a «para que la tengo» y son las que hagan falta: el
 * taladro es de Herramientas, y a la vez es *camping* y *heredado del abuelo*.
 *
 * **Es un catalogo y no una columna de texto en el asset**, que era la pregunta
 * de este hito. Un texto no se puede renombrar de una vez, ni deduplicar sin
 * distinguir mayusculas ni acentos, ni autocompletar sin recorrer todos los
 * assets de la casa. Las tres cosas salen gratis con una tabla, y las tres son
 * la razon de que una etiqueta sirva para algo.
 *
 * **Es del core**, con el criterio que la Fase 2 uso para el peso y el volumen:
 * clasificar un asset es del core --`Category` lo es-- y el filtro por etiqueta
 * cae dentro de `ListAssets`, que es una query del core. Una etiqueta que
 * viviera en un modulo se llevaria la clasificacion del inventario el dia que
 * ese modulo se apagase.
 *
 * **No lleva `householdId`**, igual que [Category] y por el mismo motivo: solo
 * se crea desde una peticion autenticada, asi que su hogar sale del
 * `TenantContext` y lo pone el adaptador.
 */
data class Tag(
    val id: UUID,
    val name: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val retiredAt: Instant?,
    val createdBy: UUID?,
    val updatedBy: UUID?,
) {
    /**
     * Una etiqueta retirada deja de ofrecerse al clasificar y de sugerirse al
     * escribir, pero **los assets que la llevaban la conservan**: se retira y no
     * se borra, igual que una categoria, porque borrarla se llevaria por delante
     * la clasificacion de todo lo que la tuviera y eso no se puede deshacer.
     */
    val isLive: Boolean get() = retiredAt == null
}
