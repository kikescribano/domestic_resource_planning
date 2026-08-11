package com.drp.adapter.persistence

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

/**
 * Repositorios de Spring Data, uno por entidad.
 *
 * Ninguno filtra por `household_id` en sus consultas, y eso **no** es un
 * descuido: el filtro lo pone PostgreSQL con la politica de RLS, a partir del
 * `app.household_id` que el gestor de transacciones fija al abrir cada
 * transaccion. Una consulta sin `WHERE household_id = ?` aqui devuelve
 * exactamente las filas del hogar de la sesion.
 *
 * El detalle importante es el reverso: esto vale porque **todas** estas
 * consultas ocurren dentro de una transaccion con contexto. Fuera de una, o sin
 * hogar fijado, la respuesta es cero filas --nunca las de otro.
 */

interface HouseholdJpaRepository : JpaRepository<HouseholdEntity, UUID>

interface IdentityJpaRepository : JpaRepository<IdentityEntity, UUID> {

    /**
     * Comparado en minusculas, que es como esta el indice unico. El dominio ya
     * normaliza con `EmailAddress`, asi que el `lower()` de aqui es la segunda
     * de las dos vallas y no la unica.
     */
    @Query("SELECT i FROM IdentityEntity i WHERE lower(i.email) = lower(:email)")
    fun findByEmailIgnoringCase(@Param("email") email: String): IdentityEntity?
}

interface HouseholdMemberJpaRepository : JpaRepository<HouseholdMemberEntity, UUID> {

    fun findByIdentityId(identityId: UUID): HouseholdMemberEntity?

    fun findAllByDeactivatedAtIsNull(pageable: Pageable): org.springframework.data.domain.Page<HouseholdMemberEntity>

    fun countByRoleAndDeactivatedAtIsNull(role: com.drp.domain.household.MemberRole): Long
}

interface InvitationJpaRepository : JpaRepository<InvitationEntity, UUID> {

    fun findByTokenHash(tokenHash: String): InvitationEntity?

    @Query(
        """
        SELECT i FROM InvitationEntity i
        WHERE lower(i.email) = lower(:email)
          AND i.acceptedAt IS NULL
          AND i.revokedAt IS NULL
          AND i.expiresAt > :now
        """,
    )
    fun findLiveByEmail(@Param("email") email: String, @Param("now") now: Instant): InvitationEntity?

    @Query(
        """
        SELECT i FROM InvitationEntity i
        WHERE i.acceptedAt IS NULL
          AND i.revokedAt IS NULL
          AND i.expiresAt > :now
        """,
        countQuery = """
        SELECT count(i) FROM InvitationEntity i
        WHERE i.acceptedAt IS NULL
          AND i.revokedAt IS NULL
          AND i.expiresAt > :now
        """,
    )
    fun findLive(
        @Param("now") now: Instant,
        pageable: Pageable,
    ): org.springframework.data.domain.Page<InvitationEntity>
}

interface EmailVerificationTokenJpaRepository : JpaRepository<EmailVerificationTokenEntity, UUID> {

    fun findByTokenHash(tokenHash: String): EmailVerificationTokenEntity?

    @Modifying
    @Query(
        "UPDATE EmailVerificationTokenEntity t SET t.usedAt = :at " +
            "WHERE t.identityId = :identityId AND t.usedAt IS NULL",
    )
    fun markAllUsed(@Param("identityId") identityId: UUID, @Param("at") at: Instant)
}

interface PasswordResetTokenJpaRepository : JpaRepository<PasswordResetTokenEntity, UUID> {

    fun findByTokenHash(tokenHash: String): PasswordResetTokenEntity?

    @Modifying
    @Query(
        "UPDATE PasswordResetTokenEntity t SET t.usedAt = :at " +
            "WHERE t.identityId = :identityId AND t.usedAt IS NULL",
    )
    fun markAllUsed(@Param("identityId") identityId: UUID, @Param("at") at: Instant)
}

interface RefreshTokenJpaRepository : JpaRepository<RefreshTokenEntity, UUID> {

    fun findByTokenHash(tokenHash: String): RefreshTokenEntity?

    @Modifying
    @Query(
        "UPDATE RefreshTokenEntity t SET t.revokedAt = :at " +
            "WHERE t.identityId = :identityId AND t.revokedAt IS NULL",
    )
    fun revokeAllFor(@Param("identityId") identityId: UUID, @Param("at") at: Instant)

    @Modifying
    @Query(
        "UPDATE RefreshTokenEntity t SET t.revokedAt = :at " +
            "WHERE t.identityId = :identityId AND t.revokedAt IS NULL AND t.id <> :keep",
    )
    fun revokeAllForExcept(
        @Param("identityId") identityId: UUID,
        @Param("keep") keep: UUID,
        @Param("at") at: Instant,
    )
}

interface CategoryJpaRepository : JpaRepository<CategoryEntity, UUID>
