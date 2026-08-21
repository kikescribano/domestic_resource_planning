package com.drp.core.adapter.persistence

import com.drp.core.application.port.ArticleFilter
import com.drp.core.application.port.ArticleRepository
import com.drp.core.application.port.AssetFilter
import com.drp.core.application.port.AssetRepository
import com.drp.core.application.port.CategoryRepository
import com.drp.core.application.port.SeededCategory
import com.drp.core.application.port.TagRepository
import com.drp.core.application.port.DocumentFilter
import com.drp.core.application.port.DocumentRepository
import com.drp.core.application.port.EmailVerificationTokenRepository
import com.drp.core.application.port.HierarchyLock
import com.drp.core.application.port.HouseholdMemberRepository
import com.drp.core.application.port.HouseholdRepository
import com.drp.core.application.port.IdentityRepository
import com.drp.core.application.port.InvitationRepository
import com.drp.core.application.port.LoanAccessTokenRepository
import com.drp.core.application.port.LoanFilter
import com.drp.core.application.port.LoanRepository
import com.drp.core.application.port.LocationLoad
import com.drp.core.application.port.LocationRepository
import com.drp.platform.page.Page
import com.drp.platform.page.Pagination
import com.drp.core.application.port.PasswordResetTokenRepository
import com.drp.core.application.port.RefreshTokenRepository
import com.drp.core.application.port.StoredFileFilter
import com.drp.core.application.port.StoredFileRepository
import com.drp.core.application.port.TenantResolver
import com.drp.platform.tenant.HouseholdDirectory
import com.drp.platform.tenant.TenantContext
import com.drp.core.domain.catalog.Article
import com.drp.core.domain.catalog.Category
import com.drp.core.domain.catalog.Tag
import com.drp.core.domain.file.Document
import com.drp.core.domain.file.DocumentContent
import com.drp.core.domain.file.DocumentTarget
import com.drp.core.domain.file.StoredContentType
import com.drp.core.domain.file.StoredFile
import com.drp.core.domain.household.Household
import com.drp.core.domain.household.HouseholdClosure
import com.drp.core.domain.household.HouseholdMember
import com.drp.core.domain.household.MemberRole
import com.drp.core.domain.identity.Avatar
import com.drp.platform.mail.EmailAddress
import com.drp.core.domain.identity.Identity
import com.drp.core.domain.inventory.Asset
import com.drp.core.domain.inventory.AssetLocation
import com.drp.core.domain.inventory.Location
import com.drp.core.domain.invitation.Invitation
import com.drp.core.domain.loan.Loan
import com.drp.core.domain.loan.LoanAccessToken
import com.drp.core.domain.loan.LoanParticipant
import com.drp.core.domain.token.SingleUseToken
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * Los adaptadores que traducen entre el dominio y JPA.
 *
 * Es la unica capa que sabe que debajo hay PostgreSQL. Hacia arriba solo se ven
 * los puertos de `com.drp.core.application.port`, escritos en tipos del dominio.
 */

@Repository
class HouseholdRepositoryAdapter(
    private val households: HouseholdJpaRepository,
    private val jdbc: JdbcTemplate,
) : HouseholdRepository {

    override fun save(household: Household): Household =
        households.save(
            HouseholdEntity(
                id = household.id,
                name = household.name,
                timeZone = household.timeZone.id,
                createdAt = household.createdAt,
                updatedAt = household.updatedAt,
                closureRequestedAt = household.closure?.requestedAt,
                closureRequestedBy = household.closure?.requestedBy,
                closureEffectiveAt = household.closure?.effectiveAt,
            ),
        ).toDomain()

    /**
     * El hogar de la sesion. No recibe identificador porque no hace falta: con
     * el contexto de inquilino fijado, la politica deja exactamente una fila
     * visible en esta tabla, y si no hay contexto no deja ninguna.
     */
    override fun findCurrent(): Household? = households.findAll().firstOrNull()?.toDomain()

    /**
     * Va por SQL plano porque JPA no tiene forma de pedir un `FOR UPDATE` sin
     * traerse la entidad, y aqui **no hace falta la fila**: hace falta el
     * cerrojo. Sin `WHERE`, como todo lo demas: la politica deja visible
     * exactamente la del hogar de la sesion.
     *
     * Sin contexto de inquilino no bloquea nada --cero filas visibles-- y eso
     * seria un cerrojo que parece tomado y no lo esta, asi que se exige el
     * contexto en lugar de dejarlo pasar en silencio.
     */
    override fun lockCurrent() {
        val locked = jdbc.queryForList("SELECT id FROM households FOR UPDATE", UUID::class.java)
        check(locked.isNotEmpty()) { "Bloquear el hogar exige contexto de inquilino" }
    }

    override fun deleteCurrent() {
        households.deleteAll(households.findAll())
    }

    private fun HouseholdEntity.toDomain() = Household(
        id = id,
        name = name,
        timeZone = ZoneId.of(timeZone),
        createdAt = createdAt,
        updatedAt = updatedAt,
        closure = HouseholdClosure.from(closureRequestedAt, closureRequestedBy, closureEffectiveAt),
    )
}

/**
 * La resolucion de inquilino, apoyada en las tres funciones acotadas de
 * `V4__tenant_resolution.sql`.
 *
 * Va por `JdbcTemplate` y no por JPA a proposito: son llamadas a funciones que
 * devuelven un identificador, no consultas sobre entidades, y dejarlas a la
 * vista en SQL plano hace evidente que son el unico punto del core que mira
 * fuera del hogar actual.
 *
 * Implementa ademas el [HouseholdDirectory] de plataforma, que es la cuarta
 * funcion acotada --`list_household_ids`-- vista desde el otro lado: el
 * recorrido periodico la necesita y plataforma no puede pedirsela al core, asi
 * que declara el puerto y el core lo cumple (ADR-011).
 */
@Repository
class SqlTenantResolver(private val jdbc: JdbcTemplate) : TenantResolver, HouseholdDirectory {

    override fun allHouseholdIds(): List<UUID> =
        jdbc.queryForList("SELECT * FROM list_household_ids()", UUID::class.java)

    override fun householdOfActiveMember(identityId: UUID): UUID? =
        jdbc.query(
            "SELECT find_household_for_active_member(?)",
            { rows, _ -> rows.getObject(1, UUID::class.java) },
            identityId,
        ).firstOrNull()

    override fun householdOfInvitationToken(tokenHash: String): UUID? =
        jdbc.query(
            "SELECT find_household_for_invitation_token(?)",
            { rows, _ -> rows.getObject(1, UUID::class.java) },
            tokenHash,
        ).firstOrNull()

    override fun householdOfLoanToken(tokenHash: String): UUID? =
        jdbc.query(
            "SELECT find_household_for_loan_token(?)",
            { rows, _ -> rows.getObject(1, UUID::class.java) },
            tokenHash,
        ).firstOrNull()

    /**
     * `SELECT * FROM` y no `SELECT f(?)` porque devuelve un conjunto y no un
     * valor, igual que `list_household_ids()`.
     */
    override fun householdsOfIdentity(identityId: UUID): List<UUID> =
        jdbc.queryForList("SELECT * FROM list_households_for_identity(?)", UUID::class.java, identityId)
}

@Repository
class IdentityRepositoryAdapter(
    private val identities: IdentityJpaRepository,
) : IdentityRepository {

    override fun save(identity: Identity): Identity =
        identities.save(
            IdentityEntity(
                id = identity.id,
                name = identity.name,
                email = identity.email.value,
                phone = identity.phone,
                passwordHash = identity.passwordHash,
                emailVerifiedAt = identity.emailVerifiedAt,
                lastLoginAt = identity.lastLoginAt,
                avatarUrl = null,
                avatarStorageKey = identity.avatar?.storageKey,
                avatarContentType = identity.avatar?.contentType,
                avatarSizeBytes = identity.avatar?.sizeBytes,
                createdAt = identity.createdAt,
                updatedAt = identity.updatedAt,
                deactivatedAt = identity.deactivatedAt,
            ),
        ).toDomain()

    override fun findById(identityId: UUID): Identity? =
        identities.findById(identityId).orElse(null)?.toDomain()

    override fun findByEmail(email: EmailAddress): Identity? =
        identities.findByEmailIgnoringCase(email.value)?.toDomain()

    override fun delete(identityId: UUID) = identities.deleteById(identityId)
}

internal fun IdentityEntity.toDomain() = Identity(
    id = id,
    name = name,
    email = EmailAddress.of(email),
    phone = phone,
    passwordHash = passwordHash,
    emailVerifiedAt = emailVerifiedAt,
    lastLoginAt = lastLoginAt,
    avatar = Avatar.from(avatarStorageKey, avatarContentType, avatarSizeBytes),
    createdAt = createdAt,
    updatedAt = updatedAt,
    deactivatedAt = deactivatedAt,
)

@Repository
class HouseholdMemberRepositoryAdapter(
    private val members: HouseholdMemberJpaRepository,
) : HouseholdMemberRepository {

    override fun save(member: HouseholdMember): HouseholdMember =
        members.save(
            HouseholdMemberEntity(
                id = member.id,
                householdId = member.householdId,
                identityId = member.identityId,
                role = member.role,
                createdAt = member.createdAt,
                updatedAt = member.updatedAt,
                deactivatedAt = member.deactivatedAt,
                createdBy = member.createdBy,
                updatedBy = member.updatedBy,
            ),
        ).toDomain()

    override fun findById(memberId: UUID): HouseholdMember? =
        members.findById(memberId).orElse(null)?.toDomain()

    override fun findByIdentity(identityId: UUID): HouseholdMember? =
        members.findByIdentityId(identityId)?.toDomain()

    override fun list(includeDeactivated: Boolean, pagination: Pagination): Page<HouseholdMember> {
        // Por antiguedad en el hogar, con el id de desempate: sin un orden
        // estable, paginar puede repetir o saltarse filas entre paginas. Por
        // nombre no se puede aqui: vive en `identities` y se resuelve despues.
        val request = PageRequest.of(pagination.page, pagination.size, Sort.by("createdAt", "id"))
        val found = if (includeDeactivated) {
            members.findAll(request)
        } else {
            members.findAllByDeactivatedAtIsNull(request)
        }
        return Page(
            items = found.content.map { it.toDomain() },
            page = pagination.page,
            size = pagination.size,
            total = found.totalElements,
        )
    }

    override fun countActiveAdmins(): Long =
        members.countByRoleAndDeactivatedAtIsNull(MemberRole.HOUSEHOLD_ADMIN)

    private fun HouseholdMemberEntity.toDomain() = HouseholdMember(
        id = id,
        householdId = householdId,
        identityId = identityId,
        role = role,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deactivatedAt = deactivatedAt,
        createdBy = createdBy,
        updatedBy = updatedBy,
    )
}

@Repository
class InvitationRepositoryAdapter(
    private val invitations: InvitationJpaRepository,
) : InvitationRepository {

    override fun save(invitation: Invitation): Invitation =
        invitations.save(
            InvitationEntity(
                id = invitation.id,
                householdId = invitation.householdId,
                email = invitation.email.value,
                role = invitation.role,
                tokenHash = invitation.tokenHash,
                expiresAt = invitation.expiresAt,
                acceptedAt = invitation.acceptedAt,
                revokedAt = invitation.revokedAt,
                createdAt = invitation.createdAt,
                createdBy = invitation.createdBy,
            ),
        ).toDomain()

    override fun findById(invitationId: UUID): Invitation? =
        invitations.findById(invitationId).orElse(null)?.toDomain()

    override fun findByTokenHash(tokenHash: String): Invitation? =
        invitations.findByTokenHash(tokenHash)?.toDomain()

    override fun findLiveByEmail(email: EmailAddress, now: Instant): Invitation? =
        invitations.findLiveByEmail(email.value, now)?.toDomain()

    override fun listLive(now: Instant, pagination: Pagination): Page<Invitation> {
        // Por orden de emision y con desempate, por lo mismo que los miembros:
        // sin orden estable la paginacion no promete cubrir todas las filas.
        val request = PageRequest.of(pagination.page, pagination.size, Sort.by("createdAt", "id"))
        val found = invitations.findLive(now, request)
        return Page(
            items = found.content.map { it.toDomain() },
            page = pagination.page,
            size = pagination.size,
            total = found.totalElements,
        )
    }

    private fun InvitationEntity.toDomain() = Invitation(
        id = id,
        householdId = householdId,
        email = EmailAddress.of(email),
        role = role,
        tokenHash = tokenHash,
        expiresAt = expiresAt,
        acceptedAt = acceptedAt,
        revokedAt = revokedAt,
        createdAt = createdAt,
        createdBy = createdBy,
    )
}

@Repository
class EmailVerificationTokenRepositoryAdapter(
    private val tokens: EmailVerificationTokenJpaRepository,
) : EmailVerificationTokenRepository {

    override fun save(token: SingleUseToken): SingleUseToken =
        tokens.save(
            EmailVerificationTokenEntity(
                id = token.id,
                identityId = token.identityId,
                tokenHash = token.tokenHash,
                expiresAt = token.expiresAt,
                usedAt = token.usedAt,
            ),
        ).let { SingleUseToken(it.id, it.identityId, it.tokenHash, it.expiresAt, it.usedAt) }

    override fun findByTokenHash(tokenHash: String): SingleUseToken? =
        tokens.findByTokenHash(tokenHash)
            ?.let { SingleUseToken(it.id, it.identityId, it.tokenHash, it.expiresAt, it.usedAt) }

    override fun markAllUsedForIdentity(identityId: UUID, at: Instant) =
        tokens.markAllUsed(identityId, at)
}

@Repository
class PasswordResetTokenRepositoryAdapter(
    private val tokens: PasswordResetTokenJpaRepository,
) : PasswordResetTokenRepository {

    override fun save(token: SingleUseToken): SingleUseToken =
        tokens.save(
            PasswordResetTokenEntity(
                id = token.id,
                identityId = token.identityId,
                tokenHash = token.tokenHash,
                expiresAt = token.expiresAt,
                usedAt = token.usedAt,
            ),
        ).let { SingleUseToken(it.id, it.identityId, it.tokenHash, it.expiresAt, it.usedAt) }

    override fun findByTokenHash(tokenHash: String): SingleUseToken? =
        tokens.findByTokenHash(tokenHash)
            ?.let { SingleUseToken(it.id, it.identityId, it.tokenHash, it.expiresAt, it.usedAt) }

    override fun markAllUsedForIdentity(identityId: UUID, at: Instant) =
        tokens.markAllUsed(identityId, at)
}

@Repository
class RefreshTokenRepositoryAdapter(
    private val tokens: RefreshTokenJpaRepository,
) : RefreshTokenRepository {

    override fun save(token: SingleUseToken): SingleUseToken =
        tokens.save(
            RefreshTokenEntity(
                id = token.id,
                identityId = token.identityId,
                tokenHash = token.tokenHash,
                expiresAt = token.expiresAt,
                revokedAt = token.usedAt,
            ),
        ).toDomain()

    override fun findByTokenHash(tokenHash: String): SingleUseToken? =
        tokens.findByTokenHash(tokenHash)?.toDomain()

    override fun revoke(tokenId: UUID, at: Instant) {
        val token = tokens.findById(tokenId).orElse(null) ?: return
        token.revokedAt = at
        tokens.save(token)
    }

    override fun revokeAllForIdentity(identityId: UUID, at: Instant) = tokens.revokeAllFor(identityId, at)

    override fun revokeAllForIdentityExcept(identityId: UUID, keepTokenId: UUID?, at: Instant) {
        if (keepTokenId == null) tokens.revokeAllFor(identityId, at)
        else tokens.revokeAllForExcept(identityId, keepTokenId, at)
    }

    /**
     * `revoked_at` viaja en el hueco de `usedAt`. Un refresh token no se
     * "consume" sino que se revoca --rota en cada uso y el anterior queda
     * invalidado-- pero para el dominio ambos significan lo mismo: a partir de
     * ese instante ya no sirve.
     */
    private fun RefreshTokenEntity.toDomain() =
        SingleUseToken(id, identityId, tokenHash, expiresAt, revokedAt)
}

@Repository
class CategoryRepositoryAdapter(
    private val categories: CategoryJpaRepository,
    private val tenantContext: TenantContext,
) : CategoryRepository {

    override fun seed(categoriesToSeed: List<SeededCategory>, at: Instant) {
        // El household_id sale del contexto de inquilino, jamas de un parametro.
        // Es la misma regla que cumple el resto del core, aplicada al unico sitio
        // donde el dominio no trae ya el hogar dentro del objeto: si viniera como
        // argumento, esta firma seria la grieta por la que un identificador ajeno
        // podria colarse. Y aunque se colara, la politica rechazaria el INSERT.
        val householdId = requireNotNull(tenantContext.currentHousehold()) {
            "Sembrar categorias exige contexto de inquilino"
        }

        categories.saveAll(
            categoriesToSeed.map { seeded ->
                CategoryEntity(
                    id = UUID.randomUUID(),
                    householdId = householdId,
                    name = seeded.name,
                    notes = null,
                    icon = seeded.icon,
                    color = seeded.color,
                    createdAt = at,
                    updatedAt = at,
                    retiredAt = null,
                    createdBy = null,
                    updatedBy = null,
                )
            },
        )
    }

    override fun countCurrent(): Long = categories.count()

    /**
     * El `household_id` lo pone el contexto, nunca el objeto de dominio --que por
     * eso no lo lleva-- ni un parametro. Al modificar se conserva el que ya tenia
     * la fila, que es el mismo: la politica no habria dejado leerla si no.
     */
    override fun save(category: Category): Category {
        val householdId = requireNotNull(tenantContext.currentHousehold()) {
            "Guardar una categoria exige contexto de inquilino"
        }

        return categories.save(
            CategoryEntity(
                id = category.id,
                householdId = householdId,
                name = category.name,
                notes = category.notes,
                icon = category.icon,
                color = category.color,
                createdAt = category.createdAt,
                updatedAt = category.updatedAt,
                retiredAt = category.retiredAt,
                createdBy = category.createdBy,
                updatedBy = category.updatedBy,
            ),
        ).toDomain()
    }

    override fun findById(categoryId: UUID): Category? =
        categories.findById(categoryId).orElse(null)?.toDomain()

    override fun findLiveByName(name: String): Category? =
        categories.findLiveByNormalizedName(name)?.toDomain()

    override fun list(includeRetired: Boolean, pagination: Pagination): Page<Category> {
        val request = PageRequest.of(pagination.page, pagination.size, Sort.by("name"))
        val found = if (includeRetired) {
            categories.findAll(request)
        } else {
            categories.findAllByRetiredAtIsNull(request)
        }
        return Page(found.content.map { it.toDomain() }, pagination.page, pagination.size, found.totalElements)
    }
}

internal fun CategoryEntity.toDomain() = Category(
    id = id,
    name = name,
    notes = notes,
    icon = icon,
    color = color,
    createdAt = createdAt,
    updatedAt = updatedAt,
    retiredAt = retiredAt,
    createdBy = createdBy,
    updatedBy = updatedBy,
)

/**
 * El catalogo de etiquetas y su union con los assets, en un solo adaptador
 * porque son un solo puerto.
 */
@Repository
class TagRepositoryAdapter(
    private val tags: TagJpaRepository,
    private val assetTags: AssetTagJpaRepository,
    private val tenantContext: TenantContext,
) : TagRepository {

    override fun save(tag: Tag): Tag {
        val householdId = requireNotNull(tenantContext.currentHousehold()) {
            "Guardar una etiqueta exige contexto de inquilino"
        }

        return tags.save(
            TagEntity(
                id = tag.id,
                householdId = householdId,
                name = tag.name,
                createdAt = tag.createdAt,
                updatedAt = tag.updatedAt,
                retiredAt = tag.retiredAt,
                createdBy = tag.createdBy,
                updatedBy = tag.updatedBy,
            ),
        ).toDomain()
    }

    override fun findById(tagId: UUID): Tag? = tags.findById(tagId).orElse(null)?.toDomain()

    override fun findByName(name: String): Tag? = tags.findByNormalizedName(name)?.toDomain()

    override fun list(includeRetired: Boolean, query: String?, pagination: Pagination): Page<Tag> {
        val found = tags.search(includeRetired, query, PageRequest.of(pagination.page, pagination.size))
        return Page(found.content.map { it.toDomain() }, pagination.page, pagination.size, found.totalElements)
    }

    /**
     * Dos consultas y no una por asset: la de union y la de las etiquetas que
     * nombra. Con una sola por fila, un listado de doscientos assets serian
     * doscientas.
     */
    override fun tagsOf(assetIds: List<UUID>): Map<UUID, List<Tag>> {
        if (assetIds.isEmpty()) return emptyMap()

        val links = assetTags.findAllByAssetIdIn(assetIds)
        if (links.isEmpty()) return emptyMap()

        val byId = tags.findAllById(links.map { it.tagId }.distinct()).associate { it.id to it.toDomain() }
        return links
            .groupBy { it.assetId }
            .mapValues { (_, rows) -> rows.mapNotNull { byId[it.tagId] }.sortedBy { it.name } }
    }

    /**
     * Absoluto: pone las que faltan y quita las que sobran.
     *
     * Se calcula la diferencia en lugar de borrar todo y volver a insertar, y no
     * es optimizacion prematura: reinsertar cambiaria el `created_at` y el
     * `created_by` de una etiqueta que ya estaba, o sea que corregir las notas de
     * un asset reescribiria la autoria de quien lo etiqueto el ano pasado.
     */
    override fun replaceTagsOf(assetId: UUID, tagIds: List<UUID>, by: UUID?, at: Instant) {
        val householdId = requireNotNull(tenantContext.currentHousehold()) {
            "Etiquetar un asset exige contexto de inquilino"
        }

        val current = assetTags.findAllByAssetId(assetId).map { it.tagId }.toSet()
        val wanted = tagIds.toSet()

        val removed = current - wanted
        if (removed.isNotEmpty()) assetTags.deleteByAssetIdAndTagIdIn(assetId, removed)

        val added = wanted - current
        if (added.isNotEmpty()) {
            assetTags.saveAll(
                added.map { tagId ->
                    AssetTagEntity(
                        assetId = assetId,
                        tagId = tagId,
                        householdId = householdId,
                        createdAt = at,
                        createdBy = by,
                    )
                },
            )
        }
    }
}

internal fun TagEntity.toDomain() = Tag(
    id = id,
    name = name,
    createdAt = createdAt,
    updatedAt = updatedAt,
    retiredAt = retiredAt,
    createdBy = createdBy,
    updatedBy = updatedBy,
)

@Repository
class AssetRepositoryAdapter(
    private val assets: AssetJpaRepository,
    private val tenantContext: TenantContext,
) : AssetRepository {

    override fun save(asset: Asset): Asset {
        val householdId = requireNotNull(tenantContext.currentHousehold()) {
            "Guardar un asset exige contexto de inquilino"
        }

        return assets.save(
            AssetEntity(
                id = asset.id,
                householdId = householdId,
                articleId = asset.articleId,
                categoryId = asset.categoryId,
                name = asset.name,
                type = asset.type,
                ownerId = asset.ownerId,
                locationAssetId = asset.location?.assetId,
                locationId = asset.location?.locationId,
                quantity = asset.quantity,
                status = asset.status,
                serialNumber = asset.serialNumber,
                acquiredOn = asset.acquiredOn,
                condition = asset.condition,
                photoUrl = asset.photoUrl,
                photoFileId = asset.photoFileId,
                notes = asset.notes,
                createdAt = asset.createdAt,
                updatedAt = asset.updatedAt,
                createdBy = asset.createdBy,
                updatedBy = asset.updatedBy,
            ),
        ).toDomain()
    }

    override fun findById(assetId: UUID): Asset? = assets.findById(assetId).orElse(null)?.toDomain()

    override fun findLiveStockItem(articleId: UUID, location: AssetLocation?): Asset? =
        assets.findLiveStockItem(articleId, location?.assetId, location?.locationId)?.toDomain()

    override fun countChildren(assetId: UUID): Long = assets.countLiveChildren(assetId)

    override fun ancestorsOf(assetId: UUID): List<UUID> = assets.ancestorIdsOf(assetId)

    override fun countLiveIn(location: AssetLocation): Long =
        assets.countLiveIn(location.assetId, location.locationId)

    override fun measureLiveIn(location: AssetLocation): LocationLoad =
        assets.measureLiveIn(location.assetId, location.locationId).let {
            LocationLoad(
                weightGrams = it.getWeight(),
                volumeMl = it.getVolume(),
                unmeasuredWeight = it.getUnknownWeight(),
                unmeasuredVolume = it.getUnknownVolume(),
            )
        }

    override fun hasOpenLoan(assetId: UUID): Boolean = assets.countOpenLoans(assetId) > 0

    override fun list(filter: AssetFilter, pagination: Pagination): Page<Asset> {
        val found = assets.search(
            query = filter.query,
            locationId = filter.locationId,
            parentAssetId = filter.parentAssetId,
            ownerId = filter.ownerId,
            withoutOwner = filter.withoutOwner,
            status = filter.status?.name,
            type = filter.type?.name,
            articleId = filter.articleId,
            categoryId = filter.categoryId,
            condition = filter.condition?.name,
            tagId = filter.tagId,
            pageable = PageRequest.of(pagination.page, pagination.size),
        )
        return Page(found.content.map { it.toDomain() }, pagination.page, pagination.size, found.totalElements)
    }
}

internal fun AssetEntity.toDomain() = Asset(
    id = id,
    type = type,
    articleId = articleId,
    name = name,
    categoryId = categoryId,
    ownerId = ownerId,
    location = AssetLocation.from(locationAssetId, locationId),
    status = status,
    quantity = quantity,
    serialNumber = serialNumber,
    acquiredOn = acquiredOn,
    condition = condition,
    photoUrl = photoUrl,
    photoFileId = photoFileId,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
    createdBy = createdBy,
    updatedBy = updatedBy,
)

@Repository
class ArticleRepositoryAdapter(
    private val articles: ArticleJpaRepository,
    private val tenantContext: TenantContext,
) : ArticleRepository {

    override fun save(article: Article): Article {
        val householdId = requireNotNull(tenantContext.currentHousehold()) {
            "Guardar un articulo exige contexto de inquilino"
        }

        return articles.save(
            ArticleEntity(
                id = article.id,
                householdId = householdId,
                categoryId = article.categoryId,
                name = article.name,
                unit = article.unit,
                brand = article.brand,
                model = article.model,
                barcode = article.barcode,
                packSize = article.packSize,
                unitWeightGrams = article.unitWeightGrams,
                unitVolumeMl = article.unitVolumeMl,
                photoUrl = article.photoUrl,
                photoFileId = article.photoFileId,
                notes = article.notes,
                createdAt = article.createdAt,
                updatedAt = article.updatedAt,
                retiredAt = article.retiredAt,
                createdBy = article.createdBy,
                updatedBy = article.updatedBy,
            ),
        ).toDomain()
    }

    override fun findById(articleId: UUID): Article? =
        articles.findById(articleId).orElse(null)?.toDomain()

    override fun findLiveByName(name: String): Article? =
        articles.findLiveByNormalizedName(name)?.toDomain()

    override fun findLiveByBarcode(barcode: String): Article? =
        articles.findFirstByBarcodeAndRetiredAtIsNull(barcode)?.toDomain()

    override fun list(filter: ArticleFilter, pagination: Pagination): Page<Article> {
        val found = articles.search(
            query = filter.query?.takeIf { it.isNotBlank() },
            categoryId = filter.categoryId,
            barcode = filter.barcode?.takeIf { it.isNotBlank() },
            includeRetired = filter.includeRetired,
            pageable = PageRequest.of(pagination.page, pagination.size),
        )
        return Page(found.content.map { it.toDomain() }, pagination.page, pagination.size, found.totalElements)
    }

    override fun countLiveStockItems(articleId: UUID): Long = articles.countLiveStockItems(articleId)
}

internal fun ArticleEntity.toDomain() = Article(
    id = id,
    name = name,
    categoryId = categoryId,
    unit = unit,
    brand = brand,
    model = model,
    barcode = barcode,
    packSize = packSize,
    unitWeightGrams = unitWeightGrams,
    unitVolumeMl = unitVolumeMl,
    photoUrl = photoUrl,
    photoFileId = photoFileId,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
    retiredAt = retiredAt,
    createdBy = createdBy,
    updatedBy = updatedBy,
)

@Repository
class LocationRepositoryAdapter(
    private val locations: LocationJpaRepository,
    private val tenantContext: TenantContext,
) : LocationRepository {

    override fun save(location: Location): Location {
        val householdId = requireNotNull(tenantContext.currentHousehold()) {
            "Guardar una ubicacion exige contexto de inquilino"
        }

        return locations.save(
            LocationEntity(
                id = location.id,
                householdId = householdId,
                name = location.name,
                type = location.type,
                parentLocationId = location.parentLocationId,
                capacity = location.capacity,
                environmentalConditions = location.environmentalConditions,
                photoUrl = location.photoUrl,
                photoFileId = location.photoFileId,
                notes = location.notes,
                createdAt = location.createdAt,
                updatedAt = location.updatedAt,
                createdBy = location.createdBy,
                updatedBy = location.updatedBy,
            ),
        ).toDomain()
    }

    override fun findById(locationId: UUID): Location? =
        locations.findById(locationId).orElse(null)?.toDomain()

    override fun findByNameAmongSiblings(name: String, parentLocationId: UUID?): Location? =
        locations.findByNormalizedNameAmongSiblings(name, parentLocationId)?.toDomain()

    override fun list(parentLocationId: UUID?, onlyChildren: Boolean, pagination: Pagination): Page<Location> {
        val request = PageRequest.of(pagination.page, pagination.size, Sort.by("name"))
        val found = if (onlyChildren) {
            locations.findAllByParentLocationId(parentLocationId, request)
        } else {
            locations.findAll(request)
        }
        return Page(found.content.map { it.toDomain() }, pagination.page, pagination.size, found.totalElements)
    }

    override fun countChildren(locationId: UUID): Long = locations.countByParentLocationId(locationId)

    override fun countAssetsIn(locationId: UUID): Long = locations.countAssetsIn(locationId)

    override fun ancestorsOf(locationId: UUID): List<UUID> = locations.ancestorIdsOf(locationId)

    override fun delete(locationId: UUID) = locations.deleteById(locationId)
}

internal fun LocationEntity.toDomain() = Location(
    id = id,
    name = name,
    type = type,
    parentLocationId = parentLocationId,
    capacity = capacity,
    environmentalConditions = environmentalConditions,
    photoUrl = photoUrl,
    photoFileId = photoFileId,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
    createdBy = createdBy,
    updatedBy = updatedBy,
)

@Repository
class StoredFileRepositoryAdapter(
    private val files: StoredFileJpaRepository,
    private val tenantContext: TenantContext,
) : StoredFileRepository {

    override fun save(file: StoredFile): StoredFile {
        val householdId = requireNotNull(tenantContext.currentHousehold()) {
            "Guardar un fichero exige contexto de inquilino"
        }

        return files.save(
            StoredFileEntity(
                id = file.id,
                householdId = householdId,
                originalName = file.originalName,
                contentType = file.contentType.value,
                sizeBytes = file.sizeBytes,
                checksum = file.checksum,
                storageKey = file.storageKey,
                createdAt = file.createdAt,
                createdBy = file.createdBy,
                uploadedAt = file.uploadedAt,
                deletedAt = file.deletedAt,
            ),
        ).toDomain()
    }

    override fun findById(fileId: UUID): StoredFile? =
        files.findById(fileId).orElse(null)?.toDomain()

    override fun findAllUsable(fileIds: Collection<UUID>): List<StoredFile> =
        files.findAllById(fileIds).map { it.toDomain() }.filter { it.isUsable }

    override fun list(filter: StoredFileFilter, pagination: Pagination): Page<StoredFile> {
        val found = files.search(
            attached = filter.attached,
            contentType = filter.contentType?.value,
            pageable = PageRequest.of(pagination.page, pagination.size),
        )
        return Page(found.content.map { it.toDomain() }, pagination.page, pagination.size, found.totalElements)
    }

    override fun usedBytes(): Long = files.sumLiveBytes()

    override fun isAttached(fileId: UUID): Boolean = files.countAttachments(fileId) > 0

    override fun findPurgeable(
        deletedBefore: Instant,
        neverAttachedBefore: Instant,
        reservedBefore: Instant,
    ): List<StoredFile> =
        files.findPurgeable(deletedBefore, neverAttachedBefore, reservedBefore).map { it.toDomain() }

    override fun delete(fileId: UUID) {
        files.deleteById(fileId)
    }
}

/**
 * El `contentType` guardado tiene un `CHECK` con la lista blanca detras, asi que
 * un valor desconocido no deberia poder existir. El `error` esta igualmente:
 * si algun dia existe, se entera quien lo lea y no el usuario mirando una ficha
 * con un hueco.
 */
internal fun StoredFileEntity.toDomain() = StoredFile(
    id = id,
    originalName = originalName,
    contentType = StoredContentType.from(contentType)
        ?: error("Tipo de contenido no admitido en la fila $id: $contentType"),
    sizeBytes = sizeBytes,
    checksum = checksum,
    storageKey = storageKey,
    createdAt = createdAt,
    createdBy = createdBy,
    uploadedAt = uploadedAt,
    deletedAt = deletedAt,
)

@Repository
class DocumentRepositoryAdapter(
    private val documents: DocumentJpaRepository,
    private val tenantContext: TenantContext,
) : DocumentRepository {

    override fun save(document: Document): Document {
        val householdId = requireNotNull(tenantContext.currentHousehold()) {
            "Guardar un documento exige contexto de inquilino"
        }

        return documents.save(
            DocumentEntity(
                id = document.id,
                householdId = householdId,
                assetId = document.target.assetId,
                articleId = document.target.articleId,
                fileId = (document.content as? DocumentContent.StoredFileRef)?.fileId,
                type = document.type,
                url = (document.content as? DocumentContent.ExternalLink)?.url,
                description = document.description,
                date = document.date,
                validUntil = document.validUntil,
                createdAt = document.createdAt,
                updatedAt = document.updatedAt,
                createdBy = document.createdBy,
                updatedBy = document.updatedBy,
            ),
        ).toDomain()
    }

    override fun findById(documentId: UUID): Document? =
        documents.findById(documentId).orElse(null)?.toDomain()

    override fun list(filter: DocumentFilter, pagination: Pagination): Page<Document> {
        val found = documents.search(
            assetId = filter.assetId,
            articleId = filter.articleId,
            type = filter.type?.name,
            pageable = PageRequest.of(pagination.page, pagination.size),
        )
        return Page(found.content.map { it.toDomain() }, pagination.page, pagination.size, found.totalElements)
    }

    override fun delete(documentId: UUID) {
        documents.deleteById(documentId)
    }
}

/**
 * Los dos `CHECK` de la tabla garantizan que hay exactamente un destino y
 * exactamente un contenido, asi que las dos reconstrucciones no pueden dar nulo.
 * Se comprueba igual: una fila escrita por otra via --una migracion, una
 * restauracion a medias-- no puede pasar por buena y salir como un documento sin
 * destino.
 */
internal fun DocumentEntity.toDomain() = Document(
    id = id,
    target = DocumentTarget.from(assetId, articleId)
        ?: error("Documento $id sin destino unico: asset=$assetId articulo=$articleId"),
    type = type,
    content = DocumentContent.from(url, fileId)
        ?: error("Documento $id sin contenido unico"),
    description = description,
    date = date,
    validUntil = validUntil,
    createdAt = createdAt,
    updatedAt = updatedAt,
    createdBy = createdBy,
    updatedBy = updatedBy,
)

/**
 * El cerrojo de jerarquia, con un advisory lock de transaccion.
 *
 * Se elige `pg_advisory_xact_lock` y no un `SELECT ... FOR UPDATE` sobre las
 * filas implicadas porque el bloqueo de filas cierra el ciclo de dos nodos y no
 * el de tres: A→B, B→C y C→A a la vez bloquean parejas distintas y no coinciden
 * en ninguna. Un cerrojo por hogar los cierra todos.
 *
 * Se libera solo al cerrar la transaccion --de ahi el `xact`--, asi que no hay
 * forma de dejarselo puesto ni siquiera fallando a mitad.
 *
 * La clave se compone de un espacio de nombres fijo y del hash del hogar. Dos
 * hogares distintos podrian colisionar en el mismo hash, y el efecto seria que
 * uno espera al otro un instante: nunca un fallo de correccion.
 */
@Repository
class AdvisoryHierarchyLock(
    private val jdbc: JdbcTemplate,
    private val tenantContext: TenantContext,
) : HierarchyLock {

    override fun acquire() {
        val householdId = requireNotNull(tenantContext.currentHousehold()) {
            "Tomar el cerrojo de jerarquia exige contexto de inquilino"
        }

        jdbc.queryForObject(
            "SELECT pg_advisory_xact_lock(hashtext('drp.hierarchy'), hashtext(?))",
            String::class.java,
            householdId.toString(),
        )
    }
}

@Repository
class LoanRepositoryAdapter(
    private val loans: LoanJpaRepository,
    private val tenantContext: TenantContext,
) : LoanRepository {

    override fun save(loan: Loan): Loan {
        // Del contexto y nunca del cliente, como en los demas adaptadores. Sin
        // el, el `WITH CHECK` implicito de la politica rechazaria la insercion.
        val householdId = requireNotNull(tenantContext.currentHousehold()) {
            "Guardar un prestamo exige contexto de inquilino"
        }

        return loans.save(
            LoanEntity(
                id = loan.id,
                householdId = householdId,
                assetId = loan.assetId,
                lenderMemberId = loan.lender.memberId,
                lenderExternal = loan.lender.external,
                borrowerMemberId = loan.borrower.memberId,
                borrowerExternal = loan.borrower.external,
                status = loan.status,
                notes = loan.notes,
                startedAt = loan.startedAt,
                dueAt = loan.dueAt,
                returnedAt = loan.returnedAt,
                conditionAtStart = loan.conditionAtStart,
                conditionOnReturn = loan.conditionOnReturn,
                createdAt = loan.createdAt,
                updatedAt = loan.updatedAt,
                createdBy = loan.createdBy,
                updatedBy = loan.updatedBy,
            ),
        ).toDomain()
    }

    override fun findById(loanId: UUID): Loan? =
        loans.findById(loanId).orElse(null)?.toDomain()

    override fun findByIdForUpdate(loanId: UUID): Loan? =
        loans.findByIdForUpdate(loanId)?.toDomain()

    override fun list(filter: LoanFilter, pagination: Pagination): Page<Loan> {
        val found = loans.search(
            status = filter.status?.name,
            assetId = filter.assetId,
            open = filter.open,
            pageable = PageRequest.of(pagination.page, pagination.size),
        )
        return Page(
            items = found.content.map { it.toDomain() },
            page = pagination.page,
            size = pagination.size,
            total = found.totalElements,
        )
    }

    override fun findOverdueCandidates(now: Instant): List<Loan> =
        loans.findOverdue(now).map { it.toDomain() }

    override fun assetNameOf(loan: Loan): String? = loans.assetNameOf(loan.assetId)

    private fun LoanEntity.toDomain() = Loan(
        id = id,
        assetId = assetId,
        // Las dos columnas excluyentes se juntan aqui en un tipo que no admite
        // ni las dos ni ninguna. Que la tabla lo garantice con un CHECK es lo
        // que permite que este `error` sea de verdad inalcanzable.
        lender = LoanParticipant.from(lenderMemberId, lenderExternal)
            ?: error("El prestamo $id no tiene prestador, y el CHECK de la tabla lo impide"),
        borrower = LoanParticipant.from(borrowerMemberId, borrowerExternal)
            ?: error("El prestamo $id no tiene receptor, y el CHECK de la tabla lo impide"),
        status = status,
        startedAt = startedAt,
        dueAt = dueAt,
        returnedAt = returnedAt,
        conditionAtStart = conditionAtStart,
        conditionOnReturn = conditionOnReturn,
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt,
        createdBy = createdBy,
        updatedBy = updatedBy,
    )
}

@Repository
class LoanAccessTokenRepositoryAdapter(
    private val tokens: LoanAccessTokenJpaRepository,
) : LoanAccessTokenRepository {

    override fun save(token: LoanAccessToken): LoanAccessToken =
        tokens.save(
            LoanAccessTokenEntity(
                id = token.id,
                loanId = token.loanId,
                tokenHash = token.tokenHash,
                role = token.role,
                expiresAt = token.expiresAt,
                usedAt = token.usedAt,
            ),
        ).toDomain()

    override fun findByTokenHash(tokenHash: String): LoanAccessToken? =
        tokens.findByTokenHash(tokenHash)?.toDomain()

    override fun findById(tokenId: UUID): LoanAccessToken? =
        tokens.findById(tokenId).orElse(null)?.toDomain()

    private fun LoanAccessTokenEntity.toDomain() = LoanAccessToken(
        id = id,
        loanId = loanId,
        tokenHash = tokenHash,
        role = role,
        expiresAt = expiresAt,
        usedAt = usedAt,
    )
}
