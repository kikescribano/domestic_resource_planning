package com.drp.platform.notice

import com.drp.platform.page.Page
import com.drp.platform.page.Pagination
import java.time.Instant
import java.util.UUID

/**
 * Algo que el hogar tiene que saber, encontrado por una comprobacion periodica.
 *
 * Es **el texto que se escribio ese dia** y no una vista de la fila que lo
 * origino: si el prestamo se devuelve manana, el aviso de que vencio siguio
 * siendo cierto. De ahi que lleve el titulo y el cuerpo dentro en lugar de una
 * referencia, y de ahi tambien que plataforma pueda guardarlo sin saber nada del
 * esquema de quien lo produjo (ADR-011).
 *
 * @param moduleKey de que modulo es la regla que lo genero, o **nulo si es del
 *   core**. Es lo que hace que un aviso de prestamo vencido siga llegando a un
 *   hogar sin ningun modulo encendido.
 * @param kind que clase de aviso es. Identificador, asi que ingles y
 *   `UPPER_SNAKE_CASE`; el cliente lo usa para agrupar sin tener que leer el
 *   titulo.
 * @param notifiedAt cuando entro en un resumen diario. Nulo es **pendiente de
 *   entregar**, y es lo unico que impide que el resumen de manana repita el de
 *   hoy.
 */
data class Notice(
    val id: UUID,
    val moduleKey: String?,
    val kind: String,
    val title: String,
    val body: String,
    val createdAt: Instant,
    val readAt: Instant?,
    val readBy: UUID?,
    val notifiedAt: Instant?,
) {
    val isRead: Boolean get() = readAt != null
}

/**
 * Lo que una comprobacion devuelve, antes de que plataforma le ponga el resto.
 *
 * La comprobacion dice **que** ha encontrado; el hogar, el modulo, el
 * identificador y la fecha los pone quien la esta ejecutando. Es lo que permite
 * que el codigo de un modulo no tenga que acordarse de sellar sus avisos con el
 * hogar correcto --que es justo la clase de descuido que la ADR-002 no perdona.
 */
data class NoticeDraft(
    val kind: String,
    val title: String,
    val body: String,
)

/**
 * El puerto de persistencia de los avisos.
 *
 * Como los del core, **no recibe `householdId`**: sale del `TenantContext` y lo
 * aplica la politica de RLS. Aceptarlo como parametro seria abrir la puerta que
 * la ADR-002 cierra, y aqui con mas motivo que en ningun sitio: quien mas llama
 * a este puerto es el recorrido periodico, que pasa por todos los hogares
 * seguidos y en el mismo hilo.
 */
interface NoticeRepository {

    fun save(notice: Notice): Notice

    fun find(noticeId: UUID): Notice?

    fun list(unreadOnly: Boolean, pagination: Pagination): Page<Notice>

    /** Los del hogar actual que todavia no han entrado en ningun resumen. */
    fun findPendingDigest(): List<Notice>

    /** Devuelve cuantos ha marcado. */
    fun markNotified(noticeIds: List<UUID>, at: Instant): Int

    /** Devuelve cuantos estaban sin leer. */
    fun markAllRead(at: Instant, readBy: UUID): Int
}
