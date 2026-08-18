package com.drp.platform.tenant

import java.util.UUID

/**
 * Que hogares hay en la instalacion.
 *
 * Es lo unico que el recorrido periodico de plataforma necesita saber para
 * empezar: la lista de identificadores sobre la que va a iterar, fijando
 * `app.household_id` en cada uno. **Solo identificadores, nunca datos** --lo que
 * hay dentro de un hogar sigue exigiendo contexto y sigue pasando por la
 * politica-- que es la misma acotacion que la ADR-003 le pone a la resolucion de
 * inquilino, y por lo mismo: es el unico punto que mira fuera del hogar actual.
 *
 * **Lo declara plataforma y lo implementa el core**, que es la inversion que la
 * ADR-011 explica y la misma forma que ya tiene `ModuleSeeder`. Al reves
 * --plataforma leyendo la tabla de hogares o el `TenantResolver` del core-- seria
 * plataforma apoyandose en el core, que es justo la tercera regla de ArchUnit de
 * la ADR-010.
 */
interface HouseholdDirectory {

    /**
     * Todos los hogares, para recorrerlos uno a uno.
     *
     * La lista se toma **una vez** al empezar el recorrido: un hogar creado a
     * mitad no entra hasta la pasada siguiente, y uno borrado por el camino
     * --`PurgeUnverifiedHouseholds` lo hace-- deja de tener nada que mirar. Las
     * dos cosas son inofensivas porque toda comprobacion es idempotente.
     */
    fun allHouseholdIds(): List<UUID>
}
