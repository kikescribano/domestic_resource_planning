package com.drp.core.adapter.persistence

import com.drp.core.domain.catalog.MeasurementUnit
import com.drp.core.domain.file.DocumentType
import com.drp.core.domain.household.MemberRole
import com.drp.core.domain.inventory.AssetStatus
import com.drp.core.domain.inventory.AssetType
import com.drp.core.domain.inventory.Capacity
import com.drp.core.domain.inventory.EnvironmentalConditions
import com.drp.core.domain.inventory.LocationType
import com.drp.core.domain.loan.ExternalParty
import com.drp.core.domain.loan.LoanRole
import com.drp.core.domain.loan.LoanStatus
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
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
 * Con las dos de prestamos, que llegan con el Hito 4, **el esquema queda mapeado
 * entero**: quince tablas, quince entidades.
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
    // El avatar vive aqui y no en `files`: una identidad no pertenece a ningun
    // hogar, asi que no tiene cuota a la que sumar ni politica de RLS detras
    // (README 4.1.1). `avatar_url` queda para un enlace externo y hoy nadie lo
    // rellena: el contrato solo ofrece subir un fichero.
    var avatarUrl: String?,
    var avatarStorageKey: String?,
    var avatarContentType: String?,
    var avatarSizeBytes: Long?,
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

/**
 * Los metadatos de un binario que vive en disco.
 *
 * `contentType` va como `String` y no como enumerado, al reves que el resto:
 * la columna ya tiene un `CHECK` con la lista blanca, y mapearla a un `enum`
 * aqui haria que una fila con un valor que la aplicacion no conoce --escrita por
 * una version futura, o por una migracion-- reventase al leerla en vez de
 * poderse listar. La traduccion al enumerado del dominio ocurre un piso mas
 * arriba, donde se puede decidir que hacer.
 *
 * No lleva `updatedAt` ni `updatedBy`: un fichero no se modifica (README 4.1.1).
 */
@Entity
@Table(name = "files")
class StoredFileEntity(
    @Id var id: UUID,
    var householdId: UUID,
    var originalName: String,
    var contentType: String,
    var sizeBytes: Long,
    var checksum: String,
    var storageKey: String,
    var createdAt: Instant,
    var createdBy: UUID?,
    var uploadedAt: Instant?,
    var deletedAt: Instant?,
)

/**
 * Las dos parejas excluyentes --destino y contenido-- van como columnas sueltas
 * y anulables porque asi esta la tabla, con sendos `CHECK` que impiden informar
 * las dos. Hacia arriba se traducen a `DocumentTarget` y `DocumentContent`, que
 * no pueden representar ese estado invalido.
 */
@Entity
@Table(name = "documents")
class DocumentEntity(
    @Id var id: UUID,
    var householdId: UUID,
    var assetId: UUID?,
    var articleId: UUID?,
    var fileId: UUID?,
    @Enumerated(EnumType.STRING) var type: DocumentType,
    var url: String?,
    var description: String?,
    var date: LocalDate?,
    var validUntil: LocalDate?,
    var createdAt: Instant,
    var updatedAt: Instant,
    var createdBy: UUID?,
    var updatedBy: UUID?,
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
 * Las dos ubicaciones van como columnas separadas y anulables porque asi esta la
 * tabla, con un `CHECK` que impide informar las dos. Hacia arriba se traducen a
 * un `AssetLocation`, que no puede representar ese estado invalido.
 */
@Entity
@Table(name = "assets")
class AssetEntity(
    @Id var id: UUID,
    var householdId: UUID,
    var articleId: UUID?,
    var categoryId: UUID?,
    var name: String?,
    @Enumerated(EnumType.STRING) var type: AssetType,
    var ownerId: UUID?,
    var locationAssetId: UUID?,
    var locationId: UUID?,
    var quantity: BigDecimal?,
    @Enumerated(EnumType.STRING) var status: AssetStatus,
    var serialNumber: String?,
    var acquiredOn: LocalDate?,
    var photoUrl: String?,
    var photoFileId: UUID?,
    var notes: String?,
    var createdAt: Instant,
    var updatedAt: Instant,
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

/**
 * Cada extremo del prestamo va en **dos columnas excluyentes** --la pertenencia o
 * el `jsonb` del externo-- porque asi esta la tabla, con sendos `CHECK` que
 * impiden informar las dos y exigen una. Hacia arriba se traducen a un
 * `LoanParticipant`, que no puede representar ninguno de los dos estados
 * invalidos.
 *
 * Los dos `jsonb` se mapean con `@JdbcTypeCode(SqlTypes.JSON)` sobre el objeto de
 * valor del dominio, igual que los de `locations` y con la misma contrapartida:
 * renombrar un campo de [com.drp.core.domain.loan.ExternalParty] cambiaria en silencio
 * las claves del JSON guardado. Aqui ademas rompe un `CHECK`, porque la tabla
 * exige que el objeto tenga `name` y al menos uno de `email` o `phone`. De ahi
 * que haya una prueba que le pregunte a PostgreSQL que claves hay de verdad.
 */
@Entity
@Table(name = "loans")
class LoanEntity(
    @Id var id: UUID,
    var householdId: UUID,
    var assetId: UUID,
    var lenderMemberId: UUID?,
    @JdbcTypeCode(SqlTypes.JSON) var lenderExternal: ExternalParty?,
    var borrowerMemberId: UUID?,
    @JdbcTypeCode(SqlTypes.JSON) var borrowerExternal: ExternalParty?,
    @Enumerated(EnumType.STRING) var status: LoanStatus,
    var notes: String?,
    var startedAt: Instant,
    var dueAt: Instant?,
    var returnedAt: Instant?,
    var createdAt: Instant,
    var updatedAt: Instant,
    var createdBy: UUID?,
    var updatedBy: UUID?,
)

/**
 * El token acotado de un externo.
 *
 * **No lleva `householdId`**, y es la unica entidad del hito de la que eso se
 * puede decir: cuelga del prestamo, igual que los tokens de correo cuelgan de la
 * identidad. Por eso su tabla es una de las cinco sin politica de RLS, y por eso
 * resolver el hogar a partir de un token exige la funcion de la V6 en lugar de
 * una consulta normal.
 */
@Entity
@Table(name = "loan_access_tokens")
class LoanAccessTokenEntity(
    @Id var id: UUID,
    var loanId: UUID,
    var tokenHash: String,
    @Enumerated(EnumType.STRING) var role: LoanRole,
    var expiresAt: Instant,
    var usedAt: Instant?,
)
