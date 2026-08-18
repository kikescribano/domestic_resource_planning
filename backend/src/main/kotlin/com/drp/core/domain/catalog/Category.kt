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
