package com.drp.core.application.usecase

import com.drp.core.application.port.HouseholdRepository
import com.drp.core.application.port.SessionClaims
import com.drp.core.domain.household.Household
import com.drp.core.domain.household.HouseholdClosure
import com.drp.platform.error.BusinessRuleViolation
import com.drp.platform.error.ErrorCode
import com.drp.platform.error.ResourceNotFound
import com.drp.platform.notice.Notice
import com.drp.platform.notice.NoticeRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/**
 * La baja de un hogar, con periodo de gracia (ADR-012).
 *
 * **Se pide y se puede cancelar**, y entre las dos cosas hay treinta dias en los
 * que el hogar funciona **exactamente igual**. Nada de solo lectura: eso
 * castigaria justo a quien todavia puede arrepentirse, y arrepentirse es la
 * razon entera de que la gracia exista. Lo unico que distingue a un hogar en
 * gracia es que lo dice --en el aviso que se levanta al pedirla y en la lectura
 * de su estado.
 *
 * Quien borra de verdad no es ninguno de estos casos de uso sino
 * [PurgeClosedHouseholds], que es una comprobacion mas del recorrido diario que
 * ya existe. Eso importa: pedir la baja **no borra nada**, ni siquiera cuando la
 * fecha ya ha pasado, hasta que la escoba pasa por ahi.
 */

/**
 * El estado del hogar (`GetCurrentHousehold`, 5.7).
 *
 * Es una **lectura nueva** y no un campo anadido a una respuesta que ya
 * existiera: hasta la baja de hogar, el contrato tenia `POST /households` y nada
 * mas --ni nombre, ni zona horaria, ni estado--, asi que no habia donde ensenar
 * que el hogar se esta dando de baja.
 *
 * **Y no se resuelve con un claim del token.** El access token vive quince
 * minutos y se emite al entrar, asi que un hogar marcado despues mentiria hasta
 * la siguiente renovacion --y lo haria justo en la pantalla que sirve para
 * cancelar la baja.
 *
 * La ve cualquier miembro y no solo quien administra: que la casa esta a punto
 * de desaparecer no es informacion reservada a nadie.
 */
@Service
class GetCurrentHousehold(private val households: HouseholdRepository) {

    @Transactional(readOnly = true)
    fun handle(): Household = households.findCurrent() ?: throw ResourceNotFound("Hogar no encontrado")
}

/**
 * Pide la baja del hogar (`RequestHouseholdClosure`, 5.7).
 *
 * Solo `HOUSEHOLD_ADMIN`, que es quien gestiona personas y modulos, y por la
 * misma razon: es la supresion irreversible de la casa entera.
 *
 * **Levanta un aviso, y uno solo.** No lo produce el recorrido diario --que lo
 * repetiria cada noche mientras la condicion siguiera siendo cierta, y la
 * condicion va a ser cierta treinta dias seguidos-- sino este caso de uso, en la
 * misma transaccion. La ADR-011 lo permite sin excepcion ninguna: quien escribe
 * en `household_notices` es plataforma, y el core la usa como usa la paginacion.
 *
 * **No es idempotente a proposito.** Pedir la baja dos veces responde `409` en
 * lugar de reiniciar los treinta dias en silencio: la fecha en la que el hogar
 * desaparece es lo unico que se le ha prometido a una persona, y moverla sin
 * decirlo es la peor forma posible de tratarla.
 */
@Service
class RequestHouseholdClosure(
    private val households: HouseholdRepository,
    private val notices: NoticeRepository,
    private val clock: Clock,
) {

    @Transactional
    fun handle(session: SessionClaims): Household {
        val household = households.findCurrent() ?: throw ResourceNotFound("Hogar no encontrado")

        if (household.isClosing) {
            throw BusinessRuleViolation(
                ErrorCode.HOUSEHOLD_CLOSURE_ALREADY_REQUESTED,
                "El hogar ya tiene una baja solicitada",
            )
        }

        val now = clock.instant()
        val closure = HouseholdClosure(
            requestedAt = now,
            requestedBy = session.memberId,
            effectiveAt = now.plus(GRACE),
        )

        val saved = households.save(household.copy(closure = closure, updatedAt = now))
        notices.save(noticeFor(saved, now))
        return saved
    }

    /**
     * El aviso lleva **la fecha** y no «en 30 dias», que es la diferencia entre
     * saber cuando y tener que contar. Va en la zona horaria del hogar: es un dia
     * del calendario de quien lo lee, no un instante del servidor.
     */
    private fun noticeFor(household: Household, now: Instant) = Notice(
        id = UUID.randomUUID(),
        moduleKey = null,
        kind = KIND,
        title = "El hogar se borrará el ${dayOf(household)}",
        body = "Lo pidió una persona que administra el hogar. Hasta esa fecha todo sigue funcionando " +
            "igual, y quien administre puede cancelarlo. Después no se puede recuperar nada.",
        createdAt = now,
        readAt = null,
        readBy = null,
        notifiedAt = null,
    )

    private fun dayOf(household: Household): String =
        LocalDate.ofInstant(household.closure!!.effectiveAt, household.timeZone).format(DAY)

    companion object {
        /**
         * Treinta dias. Es plazo de sobra para que alguien que no estaba de
         * acuerdo se entere por el resumen diario y reaccione, y lo bastante
         * corto para que quien de verdad se va no tenga sus datos ahi meses.
         */
        val GRACE: Duration = Duration.ofDays(30)

        const val KIND = "HOUSEHOLD_CLOSURE_REQUESTED"

        private val DAY: DateTimeFormatter =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-ES"))
    }
}

/**
 * Cancela la baja del hogar (`CancelHouseholdClosure`, 5.7).
 *
 * Solo `HOUSEHOLD_ADMIN`, como pedirla. Vuelve a dejar las tres columnas a nulo
 * y con eso el hogar sale de la cola de purga; **no hay nada que restaurar**,
 * porque durante la gracia no se ha tocado un solo dato.
 *
 * El aviso que se levanto al pedirla **se queda**. Es la regla de la ADR-011: un
 * aviso es el texto que se escribio ese dia y no una vista de lo que ocurre hoy,
 * asi que «se pidio la baja» siguio siendo cierto aunque despues se cancelara.
 * Borrarlo dejaria sin rastro un gesto que conviene que lo tenga.
 */
@Service
class CancelHouseholdClosure(
    private val households: HouseholdRepository,
    private val clock: Clock,
) {

    @Transactional
    fun handle(): Household {
        val household = households.findCurrent() ?: throw ResourceNotFound("Hogar no encontrado")

        if (!household.isClosing) {
            throw BusinessRuleViolation(
                ErrorCode.HOUSEHOLD_CLOSURE_NOT_REQUESTED,
                "El hogar no tiene ninguna baja solicitada",
            )
        }

        return households.save(household.copy(closure = null, updatedAt = clock.instant()))
    }
}
