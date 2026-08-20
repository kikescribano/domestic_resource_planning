package com.drp.core.domain.catalog

import java.time.Instant
import java.util.UUID

/**
 * La clasificacion funcional del hogar (README 4.1.1).
 *
 * No es un enumerado: es una tabla con una fila por categoria y por hogar, que
 * cada hogar edita. De ahi que sus nombres --«Mobiliario», «Alimentacion»-- vayan
 * en castellano: son **datos** que se le muestran al usuario, no identificadores
 * de programa.
 *
 * **No lleva `householdId`**, a diferencia de `HouseholdMember`, y no es un
 * olvido. Una pertenencia se crea en `CreateHousehold`, que genera el
 * identificador del hogar **antes** de que exista contexto y por eso tiene que
 * llevarlo dentro. Una categoria solo se crea desde una peticion autenticada, asi
 * que su hogar sale siempre del `TenantContext` y lo pone el adaptador. Un campo
 * aqui seria un sitio mas por el que un hogar ajeno podria entrar.
 */
data class Category(
    val id: UUID,
    val name: String,
    val notes: String?,
    /**
     * La cara de la categoria: uno de los dieciseis iconos y uno de los seis
     * colores, o nada.
     *
     * **Son enumerados y no texto libre**, y esa es la decision entera de la
     * ADR-015: un color elegido con un selector libre no esta en ningun token
     * del sistema de diseno, asi que no lo mide `scripts/check-contrast.py` y
     * seria lo unico de la interfaz cuyo contraste se afirma en vez de
     * comprobarse. Con el juego cerrado, los pares nuevos entran en esa lista.
     *
     * Nulo significa **que nadie lo eligio**, que es el caso normal: una
     * categoria recien sembrada no tiene por que tener cara.
     */
    val icon: CategoryIcon?,
    val color: CategoryColor?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val retiredAt: Instant?,
    val createdBy: UUID?,
    val updatedBy: UUID?,
) {
    /**
     * Una categoria retirada deja de ofrecerse al clasificar, pero los assets y
     * los articulos que ya la tenian la conservan: por eso se retira y no se
     * borra.
     */
    val isLive: Boolean get() = retiredAt == null
}

/**
 * El juego cerrado de iconos entre los que un hogar elige.
 *
 * Dieciseis, y no las mil y pico que trae Lucide: un buscador sobre el juego
 * entero obligaria a mantener una traduccion de mil nombres al castellano y
 * dejaria elegir una papelera para «Alimentacion». Que dieciseis, y con que
 * nombre se ven en pantalla, esta en
 * `docs/frontend/design-system/foundations/iconography.md`.
 *
 * `BOX` es ademas el que se pinta cuando no hay ninguno elegido.
 */
enum class CategoryIcon { BOX, SOFA, UTENSILS, SPRAY, TOOL, FRAME, PLUG, POT, PILL, MONITOR, SHIRT, BIKE, PENCIL, CAR, LEAF, PAW }

/**
 * Los seis colores entre los que un hogar elige, todos medidos.
 *
 * Ninguno cae encima de los cinco tonos del dominio ni del acento, y **el color
 * nunca es el unico portador**: el nombre de la categoria va siempre al lado.
 */
enum class CategoryColor { ROSE, PLUM, INDIGO, SKY, TEAL, MOSS }
