package com.drp.core.application

import com.drp.core.application.port.HouseholdRepository
import com.drp.platform.tenant.HouseholdCalendar
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate

/**
 * El dia del hogar, contestado por el core a partir de `households.time_zone`.
 *
 * Plataforma declara la pregunta y el core la contesta, que es la misma
 * inversion de [com.drp.platform.tenant.HouseholdDirectory] y de
 * `NoticeRecipients` y por el mismo motivo: quien sabe de hogares es el core.
 *
 * **El precedente ya estaba escrito.** `RequestHouseholdClosure` convierte el
 * instante en el que el hogar desaparecera a dia con `household.timeZone`, con
 * el comentario exacto al lado: es un dia del calendario de quien lo lee, no un
 * instante del servidor. Aquello convertia un instante **futuro**; esto contesta
 * cual es **hoy**, que es la otra mitad de la misma idea y la que faltaba.
 *
 * **Una lectura por pregunta, y no una cache.** El hogar se lee de la tabla cada
 * vez que alguien pregunta qué día es. Es una fila por su clave primaria dentro
 * de una transaccion que ya esta abierta, y quien pregunta lo hace **una vez por
 * caso de uso** —una intervencion registrada, una pasada de la comprobacion
 * nocturna—, no en un bucle. Cachear la zona por hogar ahorraria esa fila y
 * traeria a cambio la pregunta de cuando invalidarla, que es cara de contestar
 * bien y barata de contestar mal.
 */
@Component
class HouseholdTimeZoneCalendar(
    private val households: HouseholdRepository,
    private val clock: Clock,
) : HouseholdCalendar {

    @Transactional(readOnly = true)
    override fun today(): LocalDate {
        // Sin contexto de inquilino la politica no deja ver ninguna fila, asi
        // que esto es nulo. Se falla en lugar de caer a UTC: un respaldo aqui
        // daria un dia perfectamente plausible y equivocado.
        val household = checkNotNull(households.findCurrent()) {
            "Resolver el día del hogar exige contexto de inquilino"
        }

        return LocalDate.ofInstant(clock.instant(), household.timeZone)
    }
}
