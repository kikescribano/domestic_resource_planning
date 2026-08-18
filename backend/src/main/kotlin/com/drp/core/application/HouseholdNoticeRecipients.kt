package com.drp.core.application

import com.drp.core.application.port.HouseholdMemberRepository
import com.drp.core.application.port.IdentityRepository
import com.drp.platform.mail.EmailAddress
import com.drp.platform.notice.NoticeRecipients
import com.drp.platform.page.Pagination
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * A quien va el resumen diario, contestado por el core.
 *
 * Plataforma declara la pregunta y el core la contesta, que es la inversion de
 * la ADR-011: quien sabe de personas --pertenencias, identidades, correos
 * verificados-- es el core, y plataforma no puede preguntarselo sin apoyarse en
 * el. Es la misma forma que tienen `ModuleSeeder` y `HouseholdDirectory`.
 *
 * **Van todas las personas activas del hogar y no solo quien administra.** Un
 * aviso doméstico --que caduca algo, que hay un préstamo sin devolver-- lo puede
 * atender cualquiera, y hacerlo llegar solo a quien administra convierte al
 * administrador en el cuello de botella de la casa. El día que alguien quiera
 * elegir, lo que hay que cambiar es esta clase y nada más.
 *
 * **Solo con el correo verificado**, y esa es la regla que no se puede saltar:
 * una dirección sin verificar es una dirección que **alguien tecleó**, no una
 * que su dueño confirmó. Mandarle el resumen del hogar sería contarle a un
 * desconocido lo que pasa en una casa ajena, y es exactamente lo que el
 * enrolamiento en dos pasos existe para impedir.
 */
@Component
class HouseholdNoticeRecipients(
    private val members: HouseholdMemberRepository,
    private val identities: IdentityRepository,
) : NoticeRecipients {

    @Transactional(readOnly = true)
    override fun current(): List<EmailAddress> =
        members.list(includeDeactivated = false, Pagination(0, MAX_MEMBERS_PER_HOUSEHOLD)).items
            .mapNotNull { identities.findById(it.identityId) }
            .filter { it.isVerified }
            .map { it.email }
            // Una identidad no puede tener hoy dos pertenencias activas, pero la
            // deduplicacion no cuesta nada y es lo que impide que el dia que se
            // retire ese indice unico alguien reciba el mismo resumen dos veces.
            .distinct()

    private companion object {
        /**
         * Un hogar domestico tiene unas pocas personas, asi que este limite es
         * holgura y no una pagina de verdad. Es el mismo que usa la purga de
         * hogares sin verificar, y por lo mismo.
         */
        const val MAX_MEMBERS_PER_HOUSEHOLD = 500
    }
}
