package com.drp.core.adapter.persistence

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

    fun countByRoleAndDeactivatedAtIsNull(role: com.drp.core.domain.household.MemberRole): Long
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

/**
 * Los metadatos de los ficheros del hogar.
 *
 * Casi todo es consulta nativa por un motivo comun: **la pregunta de si un
 * fichero esta adjunto cruza cuatro tablas** --documents y las tres que llevan
 * foto-- y JPQL obligaria a mapear asociaciones que las entidades no tienen a
 * proposito. Todas corren bajo RLS, asi que ninguna necesita `household_id` y
 * ninguna puede ver un fichero de otro hogar.
 */
interface StoredFileJpaRepository : JpaRepository<StoredFileEntity, UUID> {

    /**
     * Lo que ocupa el hogar. `coalesce` porque un hogar sin ficheros suma nulo, no
     * cero, y ese nulo llegaria como cero solo por casualidad del mapeo.
     *
     * Cuenta tambien las reservas a medias --`uploaded_at` nulo--: ocupan cuota
     * desde el instante en que se insertan, que es justo lo que hace que la
     * reserva sirva de algo.
     */
    @Query(value = "SELECT coalesce(sum(size_bytes), 0) FROM files WHERE deleted_at IS NULL", nativeQuery = true)
    fun sumLiveBytes(): Long

    @Query(
        value = """
            SELECT count(*) FROM files f
            WHERE f.id = CAST(:fileId AS uuid)
              AND ($ATTACHED_PREDICATE)
        """,
        nativeQuery = true,
    )
    fun countAttachments(@Param("fileId") fileId: UUID): Long

    /**
     * El listado, ordenado por tamano **descendente**: cuando la cuota se agota,
     * la pregunta real es que la esta ocupando.
     *
     * El `ORDER BY` va escrito aqui y no en el `Pageable` porque no es una
     * preferencia del cliente sino parte de lo que el contrato promete.
     */
    @Query(
        value = """
            SELECT * FROM files f
            WHERE f.deleted_at IS NULL
              AND (CAST(:contentType AS text) IS NULL OR f.content_type = CAST(:contentType AS text))
              AND (
                CAST(:attached AS boolean) IS NULL
                OR CAST(:attached AS boolean) = ($ATTACHED_PREDICATE)
              )
            ORDER BY f.size_bytes DESC, f.created_at DESC
        """,
        countQuery = """
            SELECT count(*) FROM files f
            WHERE f.deleted_at IS NULL
              AND (CAST(:contentType AS text) IS NULL OR f.content_type = CAST(:contentType AS text))
              AND (
                CAST(:attached AS boolean) IS NULL
                OR CAST(:attached AS boolean) = ($ATTACHED_PREDICATE)
              )
        """,
        nativeQuery = true,
    )
    fun search(
        @Param("attached") attached: Boolean?,
        @Param("contentType") contentType: String?,
        pageable: Pageable,
    ): org.springframework.data.domain.Page<StoredFileEntity>

    /**
     * Las tres cosas que sobran, en una sola pasada (`PurgeUnusedFiles`, 5.7).
     *
     * Cada motivo lleva su propio corte porque cada uno tiene su plazo: 24 h desde
     * el borrado, 24 h desde una subida que nunca se adjunto, y una hora desde una
     * reserva que nunca se completo --esas son las subidas cortadas a medias.
     */
    @Query(
        value = """
            SELECT * FROM files f
            WHERE (f.deleted_at IS NOT NULL AND f.deleted_at < :deletedBefore)
               OR (
                    f.deleted_at IS NULL
                    AND f.uploaded_at IS NOT NULL
                    AND f.uploaded_at < :neverAttachedBefore
                    AND NOT ($ATTACHED_PREDICATE)
                  )
               OR (f.deleted_at IS NULL AND f.uploaded_at IS NULL AND f.created_at < :reservedBefore)
        """,
        nativeQuery = true,
    )
    fun findPurgeable(
        @Param("deletedBefore") deletedBefore: Instant,
        @Param("neverAttachedBefore") neverAttachedBefore: Instant,
        @Param("reservedBefore") reservedBefore: Instant,
    ): List<StoredFileEntity>
}

/**
 * Que algo referencie al fichero `f`.
 *
 * Vive en una constante porque aparece en tres consultas y **desincronizarlas
 * seria un agujero silencioso**: si el listado y la purga no entienden lo mismo
 * por «adjunto», el proceso diario acabaria borrando los bytes de un fichero que
 * la aplicacion sigue mostrando.
 *
 * Las cuatro tablas son las cuatro que pueden apuntar a un fichero: `documents`
 * por `file_id`, y las tres que llevan foto por `photo_file_id`.
 */
private const val ATTACHED_PREDICATE = """
    EXISTS (SELECT 1 FROM documents d WHERE d.file_id = f.id)
    OR EXISTS (SELECT 1 FROM assets a WHERE a.photo_file_id = f.id)
    OR EXISTS (SELECT 1 FROM articles ar WHERE ar.photo_file_id = f.id)
    OR EXISTS (SELECT 1 FROM locations l WHERE l.photo_file_id = f.id)
"""

interface DocumentJpaRepository : JpaRepository<DocumentEntity, UUID> {

    @Query(
        value = """
            SELECT * FROM documents
            WHERE (CAST(:assetId AS uuid) IS NULL OR asset_id = CAST(:assetId AS uuid))
              AND (CAST(:articleId AS uuid) IS NULL OR article_id = CAST(:articleId AS uuid))
              AND (CAST(:type AS text) IS NULL OR type = CAST(:type AS text))
            ORDER BY created_at DESC
        """,
        countQuery = """
            SELECT count(*) FROM documents
            WHERE (CAST(:assetId AS uuid) IS NULL OR asset_id = CAST(:assetId AS uuid))
              AND (CAST(:articleId AS uuid) IS NULL OR article_id = CAST(:articleId AS uuid))
              AND (CAST(:type AS text) IS NULL OR type = CAST(:type AS text))
        """,
        nativeQuery = true,
    )
    fun search(
        @Param("assetId") assetId: UUID?,
        @Param("articleId") articleId: UUID?,
        @Param("type") type: String?,
        pageable: Pageable,
    ): org.springframework.data.domain.Page<DocumentEntity>
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

    /**
     * Igual que la de ubicaciones, y con el mismo motivo para no llevar tope de
     * profundidad: un tope no protege del ciclo, lo **provoca** en cuanto la
     * jerarquia es mas honda que el numero elegido. Ver `ancestorIdsOf` en
     * [LocationJpaRepository].
     */
    @Query(
        value = """
            WITH RECURSIVE chain AS (
                SELECT a.location_asset_id AS id
                FROM assets a
                WHERE a.id = CAST(:assetId AS uuid) AND a.location_asset_id IS NOT NULL
                UNION ALL
                SELECT a.location_asset_id
                FROM assets a
                JOIN chain c ON a.id = c.id
                WHERE a.location_asset_id IS NOT NULL
            ) CYCLE id SET is_cycle USING visited
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
     * La cadena de ancestros, **entera**, de padre a raiz.
     *
     * Corre bajo RLS, asi que el recorrido no puede salirse del hogar: una fila
     * de otro es invisible y el camino se corta ahi.
     *
     * **Aqui hubo un tope de profundidad y era un agujero.** La primera version
     * cortaba en `depth < 100` para no colgarse si los datos ya estuvieran
     * corrompidos con un ciclo. El efecto real era el contrario del buscado: en
     * una jerarquia de mas de 100 niveles la cadena volvia **truncada**, el
     * ancestro que faltaba no aparecia en ella, y el anti-ciclo del caso de uso
     * --que pregunta «¿esta este nodo entre mis ancestros?»-- respondia que no y
     * **dejaba crear el ciclo**. Es decir: la proteccion contra un ciclo abria
     * uno. Lo caza `HierarchyCycleSweepTest`, con 102 niveles.
     *
     * La clausula `CYCLE` de PostgreSQL 14+ resuelve las dos cosas a la vez y
     * mejor: recuerda el camino recorrido y deja de descender en cuanto un nodo
     * se repite, asi que **termina siempre** --tambien con datos ya ciclicos--
     * sin necesidad de adivinar una profundidad maxima. La fila que cierra el
     * ciclo se devuelve igual, que es lo que hace falta para que el caso de uso
     * lo detecte en lugar de ignorarlo.
     */
    @Query(
        value = """
            WITH RECURSIVE chain AS (
                SELECT l.parent_location_id AS id
                FROM locations l
                WHERE l.id = CAST(:locationId AS uuid) AND l.parent_location_id IS NOT NULL
                UNION ALL
                SELECT l.parent_location_id
                FROM locations l
                JOIN chain c ON l.id = c.id
                WHERE l.parent_location_id IS NOT NULL
            ) CYCLE id SET is_cycle USING visited
            SELECT id FROM chain
        """,
        nativeQuery = true,
    )
    fun ancestorIdsOf(@Param("locationId") locationId: UUID): List<UUID>

    @Query(value = "SELECT count(*) FROM assets WHERE location_id = CAST(:locationId AS uuid)", nativeQuery = true)
    fun countAssetsIn(@Param("locationId") locationId: UUID): Long
}

interface LoanJpaRepository : JpaRepository<LoanEntity, UUID> {

    /**
     * El prestamo con su fila bloqueada, para la devolucion.
     *
     * El `FOR UPDATE` va escrito en la consulta y no con `@Lock` porque en una
     * consulta nativa Spring Data ignora la anotacion, y un cerrojo que se cree
     * tomado y no lo este es peor que no tenerlo.
     *
     * Sin `household_id` en el `WHERE`, como todas las de este fichero: lo pone
     * la politica. Y con ella puesta el cerrojo es tambien del hogar, porque una
     * fila que la politica no deja ver tampoco se puede bloquear.
     */
    @Query(
        value = "SELECT * FROM loans WHERE id = CAST(:id AS uuid) FOR UPDATE",
        nativeQuery = true,
    )
    fun findByIdForUpdate(@Param("id") id: UUID): LoanEntity?

    /**
     * El listado con sus tres filtros. `open` agrupa `ACTIVE` y `OVERDUE`, que es
     * la pregunta habitual --que hay fuera de casa-- y por eso no se resuelve
     * pidiendo dos veces con `status`.
     */
    @Query(
        value = """
            SELECT * FROM loans
            WHERE (CAST(:status AS text) IS NULL OR status = CAST(:status AS text))
              AND (CAST(:assetId AS uuid) IS NULL OR asset_id = CAST(:assetId AS uuid))
              AND (NOT :open OR status IN ('ACTIVE', 'OVERDUE'))
            ORDER BY started_at DESC
        """,
        countQuery = """
            SELECT count(*) FROM loans
            WHERE (CAST(:status AS text) IS NULL OR status = CAST(:status AS text))
              AND (CAST(:assetId AS uuid) IS NULL OR asset_id = CAST(:assetId AS uuid))
              AND (NOT :open OR status IN ('ACTIVE', 'OVERDUE'))
        """,
        nativeQuery = true,
    )
    fun search(
        @Param("status") status: String?,
        @Param("assetId") assetId: UUID?,
        @Param("open") open: Boolean,
        pageable: Pageable,
    ): org.springframework.data.domain.Page<LoanEntity>

    /**
     * Los candidatos a vencer.
     *
     * Las dos condiciones que la definicion subraya estan aqui y no en el codigo
     * que llama: **solo `ACTIVE`** --marcar de nuevo un `OVERDUE` volveria a
     * publicar su evento en cada pasada-- y **`due_at IS NOT NULL`**, porque un
     * prestamo sin plazo no vence nunca. Juntas son lo que hace la pasada
     * idempotente **entre ejecuciones sucesivas**.
     *
     * El `FOR UPDATE` es lo que la hace idempotente **entre ejecuciones
     * simultaneas**, y no estaba: medido con dos pasadas a la vez, `LoanOverdue`
     * salia **dos veces** por el mismo prestamo. Las dos transacciones leian el
     * mismo candidato `ACTIVE`, las dos lo marcaban y las dos publicaban; el
     * `UPDATE` se serializaba pero el evento no.
     *
     * Con el cerrojo, la segunda espera al commit de la primera y PostgreSQL
     * **reevalua el `WHERE` sobre la fila ya bloqueada**: como su `status` ya no
     * es `ACTIVE`, deja de ser candidata y desaparece del resultado. Es decir, la
     * condicion no hay que repetirla en el codigo --la vuelve a comprobar el
     * motor-- y por eso este arreglo es una linea y no una comprobacion mas.
     *
     * No es un caso rebuscado: el proceso puede solaparse consigo mismo si una
     * pasada se alarga, y de `LoanOverdue` colgaran los recordatorios (4.2), asi
     * que duplicarlo son dos avisos a la misma persona por lo mismo.
     */
    @Query(
        value = """
            SELECT * FROM loans
            WHERE status = 'ACTIVE'
              AND due_at IS NOT NULL
              AND due_at < :now
            ORDER BY due_at
            FOR UPDATE
        """,
        nativeQuery = true,
    )
    fun findOverdue(@Param("now") now: Instant): List<LoanEntity>

    /**
     * El nombre efectivo del asset prestado: el suyo, o el de su articulo cuando
     * no tiene propio (README 4.1.1). Es el unico dato del asset que ve un token
     * acotado, asi que se resuelve aqui en vez de arrastrar el asset entero.
     */
    @Query(
        value = """
            SELECT coalesce(a.name, ar.name)
            FROM assets a
            LEFT JOIN articles ar ON ar.id = a.article_id
            WHERE a.id = CAST(:assetId AS uuid)
        """,
        nativeQuery = true,
    )
    fun assetNameOf(@Param("assetId") assetId: UUID): String?
}

/**
 * Los tokens acotados.
 *
 * Su tabla **no tiene politica de RLS** --no lleva `household_id`, cuelga del
 * prestamo-- asi que aqui la nota general de arriba no aplica: estas consultas
 * no las filtra PostgreSQL. Lo que las acota es su forma, que resuelve siempre
 * por un secreto que hay que traer. No hay ni un metodo con el que recorrer
 * tokens ajenos, y anadirlo seria abrir un agujero que ninguna politica cerraria
 * por detras.
 */
interface LoanAccessTokenJpaRepository : JpaRepository<LoanAccessTokenEntity, UUID> {

    fun findByTokenHash(tokenHash: String): LoanAccessTokenEntity?
}
