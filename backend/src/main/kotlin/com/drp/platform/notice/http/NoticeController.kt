package com.drp.platform.notice.http

import com.drp.core.application.port.SessionClaims
import com.drp.platform.notice.ListNotices
import com.drp.platform.notice.MarkAllNoticesRead
import com.drp.platform.notice.MarkNoticeRead
import com.drp.platform.notice.Notice
import com.drp.platform.page.PageResponse
import com.drp.platform.page.Pagination
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

/**
 * La bandeja de avisos del hogar.
 *
 * `/api/v1/notices` **no cuelga de ningun prefijo de modulo**, y no puede
 * colgar: aqui llegan tambien los avisos del core --un prestamo vencido-- y los
 * de un modulo que se apago despues de producirlos. Un gate encima escondería
 * justo lo que hay que seguir viendo.
 *
 * **Sin restriccion de rol.** Un aviso es del hogar y lo lee cualquiera de sus
 * personas; marcarlo leido tampoco destruye nada, y quien no administra es
 * ademas quien mas probablemente se ocupe de la caducidad del yogur.
 */
@RestController
@RequestMapping("/api/v1/notices")
class NoticeController(
    private val listNotices: ListNotices,
    private val markNoticeRead: MarkNoticeRead,
    private val markAllNoticesRead: MarkAllNoticesRead,
) {

    /**
     * Del mas reciente al mas antiguo, que es como se lee una bandeja.
     *
     * `unreadOnly` no es un lujo: la pantalla arranca en «lo que falta por ver»,
     * y sin el filtro tendria que traerse el historico entero para tachar lo ya
     * leido en el cliente.
     */
    @GetMapping
    fun list(
        @RequestParam(defaultValue = "false") unreadOnly: Boolean,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): PageResponse<NoticeResponse> =
        PageResponse.of(listNotices.handle(unreadOnly, Pagination(page, size)), NoticeResponse::of)

    @PostMapping("/{id}/read")
    fun read(
        @AuthenticationPrincipal session: SessionClaims,
        @PathVariable id: UUID,
    ): NoticeResponse = NoticeResponse.of(markNoticeRead.handle(session, id))

    /**
     * Vaciar la bandeja entera. `204` y sin cuerpo: lo que la pantalla hace
     * despues es volver a leer la lista, no fiarse de lo que le devolvieran aqui.
     */
    @PostMapping("/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun readAll(@AuthenticationPrincipal session: SessionClaims) {
        markAllNoticesRead.handle(session)
    }
}

data class NoticeResponse(
    val id: UUID,
    val moduleKey: String?,
    val kind: String,
    val title: String,
    val body: String,
    val createdAt: Instant,
    val readAt: Instant?,
    val readBy: UUID?,
) {
    companion object {
        /**
         * `notifiedAt` **no sale**: es contabilidad del resumen diario, no algo
         * que la persona que lee la bandeja necesite saber. Sacarlo obligaria
         * ademas a explicar en el contrato una entrega que el cliente no
         * gobierna.
         */
        fun of(notice: Notice) = NoticeResponse(
            id = notice.id,
            moduleKey = notice.moduleKey,
            kind = notice.kind,
            title = notice.title,
            body = notice.body,
            createdAt = notice.createdAt,
            readAt = notice.readAt,
            // La autoria de la lectura sale por lo mismo que `Authorship` en el
            // core: es la unica forma de saber a quien preguntarle por un aviso
            // que alguien ya dio por visto.
            readBy = notice.readBy,
        )
    }
}
