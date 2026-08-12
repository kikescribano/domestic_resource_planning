package com.drp.adapter.persistence

import com.drp.application.port.CategoryRepository
import com.drp.application.port.EmailVerificationTokenRepository
import com.drp.application.port.HierarchyLock
import com.drp.application.port.HouseholdMemberRepository
import com.drp.application.port.HouseholdRepository
import com.drp.application.port.IdentityRepository
import com.drp.application.port.InvitationRepository
import com.drp.application.port.LocationRepository
import com.drp.application.port.Page
import com.drp.application.port.Pagination
import com.drp.application.port.PasswordResetTokenRepository
import com.drp.application.port.RefreshTokenRepository
import com.drp.application.port.StoredFileRepository
import com.drp.application.port.TenantResolver
import com.drp.application.tenant.TenantContext
import com.drp.domain.catalog.Category
import com.drp.domain.household.Household
import com.drp.domain.household.HouseholdMember
import com.drp.domain.household.MemberRole
import com.drp.domain.identity.EmailAddress
import com.drp.domain.identity.Identity
import com.drp.domain.inventory.Location
import com.drp.domain.invitation.Invitation
import com.drp.domain.token.SingleUseToken
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
 * los puertos de `com.drp.application.port`, escritos en tipos del dominio.
 */

@Repository
class HouseholdRepositoryAdapter(
    private val households: HouseholdJpaRepository,
) : HouseholdRepository {

    override fun save(household: Household): Household =
        households.save(
            HouseholdEntity(
                id = household.id,
                name = household.name,
                timeZone = household.timeZone.id,
                createdAt = household.createdAt,
                updatedAt = household.updatedAt,
            ),
        ).toDomain()

    /**
     * El hogar de la sesion. No recibe identificador porque no hace falta: con
     * el contexto de inquilino fijado, la politica deja exactamente una fila
     * visible en esta tabla, y si no hay contexto no deja ninguna.
     */
    override fun findCurrent(): Household? = households.findAll().firstOrNull()?.toDomain()

    override fun deleteCurrent() {
        households.deleteAll(households.findAll())
    }

    private fun HouseholdEntity.toDomain() = Household(
        id = id,
        name = name,
        timeZone = ZoneId.of(timeZone),
        createdAt = createdAt,
        updatedAt = updatedAt,
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
 */
@Repository
class SqlTenantResolver(private val jdbc: JdbcTemplate) : TenantResolver {

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
        val request = PageRequest.of(pagination.page, pagination.size)
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
        val found = invitations.findLive(now, PageRequest.of(pagination.page, pagination.size))
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

    override fun seed(names: List<String>, at: Instant) {
        // El household_id sale del contexto de inquilino, jamas de un parametro.
        // Es la misma regla que cumple el resto del core, aplicada al unico sitio
        // donde el dominio no trae ya el hogar dentro del objeto: si viniera como
        // argumento, esta firma seria la grieta por la que un identificador ajeno
        // podria colarse. Y aunque se colara, la politica rechazaria el INSERT.
        val householdId = requireNotNull(tenantContext.currentHousehold()) {
            "Sembrar categorias exige contexto de inquilino"
        }

        categories.saveAll(
            names.map { name ->
                CategoryEntity(
                    id = UUID.randomUUID(),
                    householdId = householdId,
                    name = name,
                    notes = null,
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

/**
 * Va por JDBC y sin entidad JPA a proposito: la pregunta es de si o no, y mapear
 * `files` entera es trabajo del Hito 3. La consulta corre bajo RLS, asi que el
 * fichero de otro hogar no aparece --y hoy no aparece ninguno, porque nada puede
 * insertar en esa tabla todavia.
 */
@Repository
class StoredFileRepositoryAdapter(private val jdbc: JdbcTemplate) : StoredFileRepository {

    override fun existsUsable(fileId: UUID): Boolean =
        jdbc.queryForObject(
            """
            SELECT count(*) FROM files
            WHERE id = ? AND deleted_at IS NULL AND uploaded_at IS NOT NULL
            """.trimIndent(),
            Long::class.java,
            fileId,
        )!! > 0
}

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
