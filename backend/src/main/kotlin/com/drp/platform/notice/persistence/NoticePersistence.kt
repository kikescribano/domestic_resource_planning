package com.drp.platform.notice.persistence

import com.drp.platform.notice.Notice
import com.drp.platform.notice.NoticeRepository
import com.drp.platform.page.Page
import com.drp.platform.page.Pagination
import com.drp.platform.tenant.TenantContext
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID
import org.springframework.data.domain.Page as SpringPage

/**
 * Los avisos, en PostgreSQL.
 *
 * Plana y sin `household_id` en las consultas, igual que los repositorios del
 * core y el de la activacion: el filtro lo pone la politica de RLS a partir del
 * `app.household_id` que fija el gestor de transacciones. Aqui esa propiedad
 * hace mas trabajo que en ningun otro sitio, porque quien mas escribe en esta
 * tabla **no nace de una peticion** sino del recorrido periodico, que pasa por
 * todos los hogares seguidos y en el mismo hilo.
 */
@Entity
@Table(name = "household_notices")
class HouseholdNoticeEntity(
    @Id var id: UUID,
    var householdId: UUID,
    var moduleKey: String?,
    var kind: String,
    var title: String,
    var body: String,
    var createdAt: Instant,
    var readAt: Instant?,
    var readBy: UUID?,
    var notifiedAt: Instant?,
)

interface HouseholdNoticeJpaRepository : JpaRepository<HouseholdNoticeEntity, UUID> {

    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): SpringPage<HouseholdNoticeEntity>

    fun findAllByReadAtIsNullOrderByCreatedAtDesc(pageable: Pageable): SpringPage<HouseholdNoticeEntity>

    fun findAllByNotifiedAtIsNullOrderByCreatedAtAsc(): List<HouseholdNoticeEntity>

    /**
     * En bloque y no fila a fila, que es la diferencia entre una consulta y
     * tantas como avisos tenga el hogar.
     *
     * `clearAutomatically` porque una modificacion masiva pasa **por encima** del
     * contexto de persistencia: sin limpiarlo, una entidad ya cargada en esta
     * misma transaccion seguiria diciendo que esta sin leer.
     *
     * La politica de RLS alcanza igual a un `UPDATE` sin `WHERE household_id`
     * --el `USING` gobierna tambien las escrituras, por eso la V8 no lleva `WITH
     * CHECK` propio-- asi que esto solo puede tocar filas del hogar de la sesion.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE HouseholdNoticeEntity n SET n.readAt = :at, n.readBy = :readBy WHERE n.readAt IS NULL")
    fun markAllRead(@Param("at") at: Instant, @Param("readBy") readBy: UUID): Int

    @Modifying(clearAutomatically = true)
    @Query("UPDATE HouseholdNoticeEntity n SET n.notifiedAt = :at WHERE n.id IN :ids")
    fun markNotified(@Param("ids") ids: List<UUID>, @Param("at") at: Instant): Int
}

@Repository
class NoticeRepositoryAdapter(
    private val notices: HouseholdNoticeJpaRepository,
    private val tenantContext: TenantContext,
) : NoticeRepository {

    /**
     * El `householdId` sale del contexto y **nunca de quien llama**, igual que en
     * la activacion. La politica lo rechazaria de todos modos si fuera otro, y
     * asi el descuido es un error de programacion visible en lugar de una fila
     * que no se escribe.
     */
    override fun save(notice: Notice): Notice {
        val householdId = checkNotNull(tenantContext.currentHousehold()) {
            "Guardar un aviso exige contexto de inquilino"
        }

        return notices.save(
            HouseholdNoticeEntity(
                id = notice.id,
                householdId = householdId,
                moduleKey = notice.moduleKey,
                kind = notice.kind,
                title = notice.title,
                body = notice.body,
                createdAt = notice.createdAt,
                readAt = notice.readAt,
                readBy = notice.readBy,
                notifiedAt = notice.notifiedAt,
            ),
        ).toDomain()
    }

    @Transactional(readOnly = true)
    override fun find(noticeId: UUID): Notice? = notices.findById(noticeId).orElse(null)?.toDomain()

    @Transactional(readOnly = true)
    override fun list(unreadOnly: Boolean, pagination: Pagination): Page<Notice> {
        val request = PageRequest.of(pagination.page, pagination.size)
        val found = if (unreadOnly) {
            notices.findAllByReadAtIsNullOrderByCreatedAtDesc(request)
        } else {
            notices.findAllByOrderByCreatedAtDesc(request)
        }

        return Page(
            items = found.content.map { it.toDomain() },
            page = pagination.page,
            size = pagination.size,
            total = found.totalElements,
        )
    }

    /**
     * Del mas antiguo al mas reciente, que es el orden en que se leen en el
     * correo: lo que lleva mas tiempo esperando va arriba.
     */
    @Transactional(readOnly = true)
    override fun findPendingDigest(): List<Notice> =
        notices.findAllByNotifiedAtIsNullOrderByCreatedAtAsc().map { it.toDomain() }

    override fun markNotified(noticeIds: List<UUID>, at: Instant): Int =
        if (noticeIds.isEmpty()) 0 else notices.markNotified(noticeIds, at)

    override fun markAllRead(at: Instant, readBy: UUID): Int = notices.markAllRead(at, readBy)
}

private fun HouseholdNoticeEntity.toDomain() = Notice(
    id = id,
    moduleKey = moduleKey,
    kind = kind,
    title = title,
    body = body,
    createdAt = createdAt,
    readAt = readAt,
    readBy = readBy,
    notifiedAt = notifiedAt,
)
