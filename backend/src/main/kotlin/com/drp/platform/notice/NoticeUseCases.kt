package com.drp.platform.notice

import com.drp.core.application.port.SessionClaims
import com.drp.platform.error.ResourceNotFound
import com.drp.platform.page.Page
import com.drp.platform.page.Pagination
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

/**
 * Las tres operaciones de la bandeja de avisos.
 *
 * Dependen del core en **el mismo unico punto** que las de la activacion:
 * `SessionClaims`, de donde sale quien marca un aviso como leido. Es la
 * excepcion que la ADR-010 nombra una a una en la regla de ArchUnit, y este hito
 * no la ensancha --lo que hacia falta del core, el correo y la lista de hogares,
 * se mudo a plataforma en lugar de anadirse a la lista.
 *
 * **No hay operacion de crear ni de borrar**, y eso es deliberado: un aviso lo
 * escribe el recorrido periodico, no una persona, y borrarlo seria perder el
 * unico registro de que algo paso ese dia. Lo que una persona hace con un aviso
 * es leerlo.
 */

@Service
class ListNotices(private val notices: NoticeRepository) {

    @Transactional(readOnly = true)
    fun handle(unreadOnly: Boolean, pagination: Pagination): Page<Notice> =
        notices.list(unreadOnly, pagination)
}

/**
 * Marcar leido es **del hogar y no de cada persona**.
 *
 * Un hogar es un grupo pequeno que comparte la bandeja igual que comparte el
 * inventario: si alguien ya se ocupo de la caducidad del yogur, el resto no
 * tiene que volver a verla. Un estado por persona multiplicaria las filas por
 * miembro para responder una pregunta que nadie se hace. Quien lo marco si queda
 * registrado, que es lo que permite saber a quien preguntarle.
 *
 * Es idempotente: volver a marcar lo ya leido devuelve lo mismo y **no mueve la
 * fecha**, para que «cuando se vio esto» siga significando la primera vez.
 */
@Service
class MarkNoticeRead(
    private val notices: NoticeRepository,
    private val clock: Clock,
) {

    @Transactional
    fun handle(session: SessionClaims, noticeId: UUID): Notice {
        val notice = notices.find(noticeId) ?: throw ResourceNotFound("No existe el aviso $noticeId")
        if (notice.isRead) return notice

        return notices.save(notice.copy(readAt = clock.instant(), readBy = session.memberId))
    }
}

/**
 * Vaciar la bandeja de una vez.
 *
 * Existe porque sin ella la unica forma de dejar de ver el punto rojo es pulsar
 * treinta veces, y lo que se hace entonces es dejar de mirar la bandeja. Devuelve
 * cuantos habia sin leer, que es lo que la pantalla necesita para decir algo mas
 * util que «hecho».
 */
@Service
class MarkAllNoticesRead(
    private val notices: NoticeRepository,
    private val clock: Clock,
) {

    @Transactional
    fun handle(session: SessionClaims): Int = notices.markAllRead(clock.instant(), session.memberId)
}
