package com.drp.adapter.persistence

import com.drp.domain.catalog.MeasurementUnit
import com.drp.domain.household.MemberRole
import com.drp.domain.inventory.Capacity
import com.drp.domain.inventory.EnvironmentalConditions
import com.drp.domain.inventory.LocationType
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Las entidades JPA de las tablas que usa el Hito 1.
 *
 * Son deliberadamente planas: columnas y nada mas, con las claves ajenas como
 * `UUID` en lugar de asociaciones `@ManyToOne`. No es simplificar por
 * simplificar. Una asociacion perezosa dispara una consulta en un momento que no
 * se ve en el codigo, y aqui **cada consulta va contra una politica de RLS**:
 * una carga inesperada fuera del contexto de inquilino no falla, devuelve vacio,
 * que es la clase de fallo que no se nota hasta que es tarde. Con identificadores
 * planos, toda lectura esta escrita donde se lee.
 *
 * Estas clases no salen de este paquete: los casos de uso hablan del dominio, y
 * la traduccion entre ambos vive en los adaptadores de al lado.
 *
 * Las cuatro tablas restantes del esquema --documents, files, loans y
 * loan_access_tokens-- existen en la base de datos desde la primera migracion
 * pero no tienen entidad todavia: llegan con sus hitos. `ddl-auto: validate`
 * solo comprueba lo que esta mapeado, asi que no estorban.
 */

@Entity
@Table(name = "households")
class HouseholdEntity(
    @Id var id: UUID,
    var name: String,
    var timeZone: String,
    var createdAt: Instant,
    var updatedAt: Instant,
)

@Entity
@Table(name = "identities")
class IdentityEntity(
    @Id var id: UUID,
    var name: String,
    var email: String,
    var phone: String?,
    var passwordHash: String,
    var emailVerifiedAt: Instant?,
    var lastLoginAt: Instant?,
    var createdAt: Instant,
    var updatedAt: Instant,
    var deactivatedAt: Instant?,
)

@Entity
@Table(name = "household_members")
class HouseholdMemberEntity(
    @Id var id: UUID,
    var householdId: UUID,
    var identityId: UUID,
    @Enumerated(EnumType.STRING) var role: MemberRole,
    var createdAt: Instant,
    var updatedAt: Instant,
    var deactivatedAt: Instant?,
    var createdBy: UUID?,
    var updatedBy: UUID?,
)

@Entity
@Table(name = "invitations")
class InvitationEntity(
    @Id var id: UUID,
    var householdId: UUID,
    var email: String,
    @Enumerated(EnumType.STRING) var role: MemberRole,
    var tokenHash: String,
    var expiresAt: Instant,
    var acceptedAt: Instant?,
    var revokedAt: Instant?,
    var createdAt: Instant,
    var createdBy: UUID?,
)

@Entity
@Table(name = "email_verification_tokens")
class EmailVerificationTokenEntity(
    @Id var id: UUID,
    var identityId: UUID,
    var tokenHash: String,
    var expiresAt: Instant,
    var usedAt: Instant?,
)

@Entity
@Table(name = "password_reset_tokens")
class PasswordResetTokenEntity(
    @Id var id: UUID,
    var identityId: UUID,
    var tokenHash: String,
    var expiresAt: Instant,
    var usedAt: Instant?,
)

@Entity
@Table(name = "refresh_tokens")
class RefreshTokenEntity(
    @Id var id: UUID,
    var identityId: UUID,
    var tokenHash: String,
    var expiresAt: Instant,
    var revokedAt: Instant?,
)

@Entity
@Table(name = "categories")
class CategoryEntity(
    @Id var id: UUID,
    var householdId: UUID,
    var name: String,
    var notes: String?,
    var createdAt: Instant,
    var updatedAt: Instant,
    var retiredAt: Instant?,
    var createdBy: UUID?,
    var updatedBy: UUID?,
)

@Entity
@Table(name = "articles")
class ArticleEntity(
    @Id var id: UUID,
    var householdId: UUID,
    var categoryId: UUID,
    var name: String,
    @Enumerated(EnumType.STRING) var unit: MeasurementUnit,
    var brand: String?,
    var model: String?,
    var barcode: String?,
    var packSize: BigDecimal?,
    var photoUrl: String?,
    var photoFileId: UUID?,
    var notes: String?,
    var createdAt: Instant,
    var updatedAt: Instant,
    var retiredAt: Instant?,
    var createdBy: UUID?,
    var updatedBy: UUID?,
)

/**
 * Los dos `jsonb` --`capacity` y `environmental_conditions`-- se mapean con
 * `@JdbcTypeCode(SqlTypes.JSON)` directamente sobre los objetos de valor del
 * dominio, que es lo que evita escribir dos conversiones a mano por columna.
 *
 * Tiene una contrapartida que conviene saber: **renombrar un campo del dominio
 * cambia en silencio las claves del JSON guardado**, y con filas ya escritas eso
 * las deja ilegibles sin que nada falle al compilar. Por eso hay una prueba de
 * esquema que comprueba las claves reales en PostgreSQL contra las que declara el
 * contrato, en lugar de confiar en que nadie las toque.
 */
@Entity
@Table(name = "locations")
class LocationEntity(
    @Id var id: UUID,
    var householdId: UUID,
    var name: String,
    @Enumerated(EnumType.STRING) var type: LocationType,
    var parentLocationId: UUID?,
    @JdbcTypeCode(SqlTypes.JSON) var capacity: Capacity?,
    @JdbcTypeCode(SqlTypes.JSON) var environmentalConditions: EnvironmentalConditions?,
    var photoUrl: String?,
    var photoFileId: UUID?,
    var notes: String?,
    var createdAt: Instant,
    var updatedAt: Instant,
    var createdBy: UUID?,
    var updatedBy: UUID?,
)
