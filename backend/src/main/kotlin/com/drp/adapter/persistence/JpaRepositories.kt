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

interface CategoryJpaRepository : JpaRepository<CategoryEntity, UUID> {

    /**
     * Normaliza con `immutable_unaccent`, que es **la misma funcion que usa el
     * indice unico** `categories_name_unique_live`. De ahi que sea una consulta
     * nativa y no JPQL: JPQL no sabe llamar a una funcion del esquema, y
     * comparar aqui con `lower()` a secas dejaria pasar «Bricolaje» frente a
     * «bricolage» --que el indice si rechaza-- y convertiria un 409 limpio en un
     * 500 por violacion de restriccion.
     *
     * Sin `WHERE household_id`, como el resto: lo pone la politica de RLS.
     */
    @Query(
        value = """
            SELECT * FROM categories
            WHERE lower(immutable_unaccent(name)) = lower(immutable_unaccent(:name))
              AND retired_at IS NULL
            LIMIT 1
        """,
        nativeQuery = true,
    )
    fun findLiveByNormalizedName(@Param("name") name: String): CategoryEntity?

    fun findAllByRetiredAtIsNull(pageable: Pageable): org.springframework.data.domain.Page<CategoryEntity>
}

interface AssetJpaRepository : JpaRepository<AssetEntity, UUID> {

    /**
     * La existencia viva de un articulo en una ubicacion.
     *
     * El `IS NOT DISTINCT FROM` no es una forma rebuscada de `=`: es lo que hace
     * que dos nulos se consideren **iguales**, exactamente como el
     * `NULLS NOT DISTINCT` del indice `assets_live_stock_item_unique`. Con un `=`
     * normal, una existencia todavia sin ubicar nunca se encontraria a si misma,
     * cada entrada crearia una fila nueva y el indice las rechazaria: 500 en vez
     * de sumar.
     *
     * Y `status <> 'DECOMMISSIONED'` por el mismo motivo que el indice lo
     * excluye: una existencia dada de baja no ocupa el hueco, asi que se puede
     * volver a dar entrada de ese articulo en esa ubicacion.
     */
    @Query(
        value = """
            SELECT * FROM assets
            WHERE type = 'CONSUMABLE'
              AND status <> 'DECOMMISSIONED'
              AND article_id = CAST(:articleId AS uuid)
              AND location_asset_id IS NOT DISTINCT FROM CAST(:locationAssetId AS uuid)
              AND location_id IS NOT DISTINCT FROM CAST(:locationId AS uuid)
            LIMIT 1
        """,
        nativeQuery = true,
    )
    fun findLiveStockItem(
        @Param("articleId") articleId: UUID,
        @Param("locationAssetId") locationAssetId: UUID?,
        @Param("locationId") locationId: UUID?,
    ): AssetEntity?

    @Query(
        value = """
            SELECT count(*) FROM assets
            WHERE location_asset_id = CAST(:assetId AS uuid) AND status <> 'DECOMMISSIONED'
        """,
        nativeQuery = true,
    )
    fun countLiveChildren(@Param("assetId") assetId: UUID): Long

    @Query(
        value = """
            SELECT count(*) FROM assets
            WHERE status <> 'DECOMMISSIONED'
              AND location_asset_id IS NOT DISTINCT FROM CAST(:locationAssetId AS uuid)
              AND location_id IS NOT DISTINCT FROM CAST(:locationId AS uuid)
        """,
        nativeQuery = true,
    )
    fun countLiveIn(
        @Param("locationAssetId") locationAssetId: UUID?,
        @Param("locationId") locationId: UUID?,
    ): Long

    /** Igual que la de ubicaciones, y por lo mismo: bajo RLS y con tope de profundidad. */
    @Query(
        value = """
            WITH RECURSIVE chain AS (
                SELECT a.location_asset_id AS id, 1 AS depth
                FROM assets a
                WHERE a.id = CAST(:assetId AS uuid) AND a.location_asset_id IS NOT NULL
                UNION ALL
                SELECT a.location_asset_id, c.depth + 1
                FROM assets a
                JOIN chain c ON a.id = c.id
                WHERE a.location_asset_id IS NOT NULL AND c.depth < 100
            )
            SELECT id FROM chain
        """,
        nativeQuery = true,
    )
    fun ancestorIdsOf(@Param("assetId") assetId: UUID): List<UUID>

    @Query(
        value = """
            SELECT count(*) FROM loans
            WHERE asset_id = CAST(:assetId AS uuid) AND status IN ('ACTIVE', 'OVERDUE')
        """,
        nativeQuery = true,
    )
    fun countOpenLoans(@Param("assetId") assetId: UUID): Long

    /**
     * El listado con sus ocho filtros. Excluye los `DECOMMISSIONED` **salvo que
     * se pida ese estado**, que es la unica forma de ver el historial.
     */
    @Query(
        value = """
            SELECT * FROM assets
            WHERE (CAST(:status AS text) IS NOT NULL OR status <> 'DECOMMISSIONED')
              AND (CAST(:status AS text) IS NULL OR status = CAST(:status AS text))
              AND (CAST(:locationId AS uuid) IS NULL OR location_id = CAST(:locationId AS uuid))
              AND (CAST(:parentAssetId AS uuid) IS NULL OR location_asset_id = CAST(:parentAssetId AS uuid))
              AND (CAST(:ownerId AS uuid) IS NULL OR owner_id = CAST(:ownerId AS uuid))
              AND (NOT :withoutOwner OR owner_id IS NULL)
              AND (CAST(:type AS text) IS NULL OR type = CAST(:type AS text))
              AND (CAST(:articleId AS uuid) IS NULL OR article_id = CAST(:articleId AS uuid))
              AND (CAST(:categoryId AS uuid) IS NULL OR category_id = CAST(:categoryId AS uuid))
            ORDER BY created_at DESC
        """,
        countQuery = """
            SELECT count(*) FROM assets
            WHERE (CAST(:status AS text) IS NOT NULL OR status <> 'DECOMMISSIONED')
              AND (CAST(:status AS text) IS NULL OR status = CAST(:status AS text))
              AND (CAST(:locationId AS uuid) IS NULL OR location_id = CAST(:locationId AS uuid))
              AND (CAST(:parentAssetId AS uuid) IS NULL OR location_asset_id = CAST(:parentAssetId AS uuid))
              AND (CAST(:ownerId AS uuid) IS NULL OR owner_id = CAST(:ownerId AS uuid))
              AND (NOT :withoutOwner OR owner_id IS NULL)
              AND (CAST(:type AS text) IS NULL OR type = CAST(:type AS text))
              AND (CAST(:articleId AS uuid) IS NULL OR article_id = CAST(:articleId AS uuid))
              AND (CAST(:categoryId AS uuid) IS NULL OR category_id = CAST(:categoryId AS uuid))
        """,
        nativeQuery = true,
    )
    @Suppress("LongParameterList")
    fun search(
        @Param("locationId") locationId: UUID?,
        @Param("parentAssetId") parentAssetId: UUID?,
        @Param("ownerId") ownerId: UUID?,
        @Param("withoutOwner") withoutOwner: Boolean,
        @Param("status") status: String?,
        @Param("type") type: String?,
        @Param("articleId") articleId: UUID?,
        @Param("categoryId") categoryId: UUID?,
        pageable: Pageable,
    ): org.springframework.data.domain.Page<AssetEntity>
}

interface ArticleJpaRepository : JpaRepository<ArticleEntity, UUID> {

    /** Mismo `immutable_unaccent` que el indice `articles_name_unique_live`. */
    @Query(
        value = """
            SELECT * FROM articles
            WHERE lower(immutable_unaccent(name)) = lower(immutable_unaccent(:name))
              AND retired_at IS NULL
            LIMIT 1
        """,
        nativeQuery = true,
    )
    fun findLiveByNormalizedName(@Param("name") name: String): ArticleEntity?

    fun findFirstByBarcodeAndRetiredAtIsNull(barcode: String): ArticleEntity?

    /**
     * El listado con sus cuatro filtros, todos opcionales.
     *
     * El `q` compara normalizado igual que la unicidad --buscar «cafe» tiene que
     * encontrar «Café»-- y por eso la consulta es nativa: JPQL no sabe llamar a
     * `immutable_unaccent`.
     */
    @Query(
        value = """
            SELECT * FROM articles
            WHERE (:includeRetired OR retired_at IS NULL)
              AND (CAST(:categoryId AS uuid) IS NULL OR category_id = CAST(:categoryId AS uuid))
              AND (CAST(:barcode AS text) IS NULL OR barcode = CAST(:barcode AS text))
              AND (
                CAST(:query AS text) IS NULL
                OR lower(immutable_unaccent(name)) LIKE '%' || lower(immutable_unaccent(CAST(:query AS text))) || '%'
              )
            ORDER BY name
        """,
        countQuery = """
            SELECT count(*) FROM articles
            WHERE (:includeRetired OR retired_at IS NULL)
              AND (CAST(:categoryId AS uuid) IS NULL OR category_id = CAST(:categoryId AS uuid))
              AND (CAST(:barcode AS text) IS NULL OR barcode = CAST(:barcode AS text))
              AND (
                CAST(:query AS text) IS NULL
                OR lower(immutable_unaccent(name)) LIKE '%' || lower(immutable_unaccent(CAST(:query AS text))) || '%'
              )
        """,
        nativeQuery = true,
    )
    fun search(
        @Param("query") query: String?,
        @Param("categoryId") categoryId: UUID?,
        @Param("barcode") barcode: String?,
        @Param("includeRetired") includeRetired: Boolean,
        pageable: Pageable,
    ): org.springframework.data.domain.Page<ArticleEntity>

    /**
     * Las existencias vivas del articulo. `DECOMMISSIONED` no cuenta: una
     * existencia dada de baja sigue apuntando al articulo --por eso la fila
     * permanece-- pero no impide retirarlo.
     */
    @Query(
        value = """
            SELECT count(*) FROM assets
            WHERE article_id = CAST(:articleId AS uuid) AND status <> 'DECOMMISSIONED'
        """,
        nativeQuery = true,
    )
    fun countLiveStockItems(@Param("articleId") articleId: UUID): Long
}

interface LocationJpaRepository : JpaRepository<LocationEntity, UUID> {

    fun findAllByParentLocationId(
        parentLocationId: UUID?,
        pageable: Pageable,
    ): org.springframework.data.domain.Page<LocationEntity>

    fun countByParentLocationId(parentLocationId: UUID): Long

    /** Mismo `immutable_unaccent` que el indice `locations_name_unique_among_siblings`. */
    @Query(
        value = """
            SELECT * FROM locations
            WHERE lower(immutable_unaccent(name)) = lower(immutable_unaccent(:name))
              AND parent_location_id IS NOT DISTINCT FROM CAST(:parentLocationId AS uuid)
            LIMIT 1
        """,
        nativeQuery = true,
    )
    fun findByNormalizedNameAmongSiblings(
        @Param("name") name: String,
        @Param("parentLocationId") parentLocationId: UUID?,
    ): LocationEntity?

    /**
     * La cadena de ancestros, de padre a raiz, con un CTE recursivo.
     *
     * Corre **bajo RLS**, asi que el recorrido no puede salirse del hogar: una
     * fila de otro es invisible y el camino se corta ahi. Y lleva tope de
     * profundidad por si los datos ya estuvieran corrompidos con un ciclo --sin
     * el, la consulta que sirve para detectarlos seria la que se cuelga.
     */
    @Query(
        value = """
            WITH RECURSIVE chain AS (
                SELECT l.parent_location_id AS id, 1 AS depth
                FROM locations l
                WHERE l.id = CAST(:locationId AS uuid) AND l.parent_location_id IS NOT NULL
                UNION ALL
                SELECT l.parent_location_id, c.depth + 1
                FROM locations l
                JOIN chain c ON l.id = c.id
                WHERE l.parent_location_id IS NOT NULL AND c.depth < 100
            )
            SELECT id FROM chain
        """,
        nativeQuery = true,
    )
    fun ancestorIdsOf(@Param("locationId") locationId: UUID): List<UUID>

    @Query(value = "SELECT count(*) FROM assets WHERE location_id = CAST(:locationId AS uuid)", nativeQuery = true)
    fun countAssetsIn(@Param("locationId") locationId: UUID): Long
}
