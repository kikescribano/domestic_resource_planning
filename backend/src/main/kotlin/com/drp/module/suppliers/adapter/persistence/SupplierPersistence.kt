package com.drp.module.suppliers.adapter.persistence

import com.drp.module.suppliers.application.SupplierRepository
import com.drp.module.suppliers.domain.LinkTarget
import com.drp.module.suppliers.domain.LinkedTarget
import com.drp.module.suppliers.domain.ServiceCategory
import com.drp.module.suppliers.domain.Supplier
import com.drp.module.suppliers.domain.SupplierLink
import com.drp.platform.page.Page
import com.drp.platform.page.Pagination
import com.drp.platform.tenant.TenantContext
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID
import org.springframework.data.domain.Page as SpringPage

/**
 * Los contactos de servicio, en PostgreSQL.
 *
 * Planas y sin `household_id` en las consultas, igual que los repositorios del
 * core y los dos de plataforma: el filtro lo pone la politica de RLS a partir del
 * `app.household_id` que fija el gestor de transacciones. Escribir el filtro a
 * mano aqui daria la impresion de que es el que protege, y el dia que a alguien
 * se le olvide en una consulta nueva no fallaria nada.
 */
@Entity
@Table(name = "suppliers")
class SupplierEntity(
    @Id var id: UUID,
    var householdId: UUID,
    var name: String,
    @Enumerated(EnumType.STRING) var serviceCategory: ServiceCategory,
    var contactName: String?,
    var phone: String?,
    var email: String?,
    var website: String?,
    var address: String?,
    var notes: String?,
    var createdAt: Instant,
    var updatedAt: Instant,
    var retiredAt: Instant?,
    var createdBy: UUID?,
    var updatedBy: UUID?,
)

/**
 * El enlace, con **las dos columnas excluyentes** tal y como estan en la tabla.
 *
 * Hacia arriba se traducen a un [LinkTarget], que no puede representar ninguno de
 * los dos estados invalidos --ninguna o las dos--. Es la misma forma que el core
 * usa en `loans` para los dos extremos de un prestamo.
 */
@Entity
@Table(name = "supplier_links")
class SupplierLinkEntity(
    @Id var id: UUID,
    var householdId: UUID,
    var supplierId: UUID,
    var assetId: UUID?,
    var locationId: UUID?,
    var createdAt: Instant,
    var createdBy: UUID?,
)

interface SupplierJpaRepository : JpaRepository<SupplierEntity, UUID> {

    /**
     * Normaliza con `immutable_unaccent`, que es **la misma funcion que usa el
     * indice unico** `suppliers_name_unique_live`. De ahi que sea nativa y no
     * JPQL: comparar con `lower()` a secas dejaria pasar «Fontaneria Perez»
     * frente a «Fontanería Pérez» --que el indice si rechaza-- y convertiria un
     * 409 limpio en un 500 por violacion de restriccion.
     */
    @Query(
        value = """
            SELECT * FROM suppliers
            WHERE lower(immutable_unaccent(name)) = lower(immutable_unaccent(:name))
              AND retired_at IS NULL
            LIMIT 1
        """,
        nativeQuery = true,
    )
    fun findLiveByNormalizedName(@Param("name") name: String): SupplierEntity?

    /**
     * El listado con sus tres filtros, todos opcionales, y con el mismo
     * `immutable_unaccent` en la busqueda por texto: buscar «perez» tiene que
     * encontrar «Pérez».
     *
     * **La busqueda mira el nombre y no los datos de contacto**, que es una
     * decision de seguridad y no de comodidad: el telefono y el correo del
     * fontanero son datos personales de alguien que no es usuario del sistema, y
     * un buscador por correo convierte la lista en un directorio consultable.
     */
    @Query(
        value = """
            SELECT * FROM suppliers
            WHERE (:includeRetired OR retired_at IS NULL)
              AND (CAST(:serviceCategory AS text) IS NULL OR service_category = CAST(:serviceCategory AS text))
              AND (
                CAST(:query AS text) IS NULL
                OR lower(immutable_unaccent(name)) LIKE '%' || lower(immutable_unaccent(CAST(:query AS text))) || '%'
              )
            ORDER BY retired_at NULLS FIRST, lower(immutable_unaccent(name))
        """,
        countQuery = """
            SELECT count(*) FROM suppliers
            WHERE (:includeRetired OR retired_at IS NULL)
              AND (CAST(:serviceCategory AS text) IS NULL OR service_category = CAST(:serviceCategory AS text))
              AND (
                CAST(:query AS text) IS NULL
                OR lower(immutable_unaccent(name)) LIKE '%' || lower(immutable_unaccent(CAST(:query AS text))) || '%'
              )
        """,
        nativeQuery = true,
    )
    fun search(
        @Param("serviceCategory") serviceCategory: String?,
        @Param("query") query: String?,
        @Param("includeRetired") includeRetired: Boolean,
        pageable: Pageable,
    ): SpringPage<SupplierEntity>
}

interface SupplierLinkJpaRepository : JpaRepository<SupplierLinkEntity, UUID> {

    fun findAllBySupplierIdOrderByCreatedAt(supplierId: UUID): List<SupplierLinkEntity>

    fun existsBySupplierIdAndAssetId(supplierId: UUID, assetId: UUID): Boolean

    fun existsBySupplierIdAndLocationId(supplierId: UUID, locationId: UUID): Boolean
}

@Repository
class SupplierRepositoryAdapter(
    private val suppliers: SupplierJpaRepository,
    private val links: SupplierLinkJpaRepository,
    private val tenantContext: TenantContext,
    private val jdbc: JdbcTemplate,
) : SupplierRepository {

    @Transactional(readOnly = true)
    override fun list(
        serviceCategory: ServiceCategory?,
        query: String?,
        includeRetired: Boolean,
        pagination: Pagination,
    ): Page<Supplier> {
        val found = suppliers.search(
            serviceCategory?.name,
            query,
            includeRetired,
            PageRequest.of(pagination.page, pagination.size),
        )

        return Page(
            items = found.content.map { it.toDomain() },
            page = pagination.page,
            size = pagination.size,
            total = found.totalElements,
        )
    }

    @Transactional(readOnly = true)
    override fun find(supplierId: UUID): Supplier? = suppliers.findById(supplierId).orElse(null)?.toDomain()

    @Transactional(readOnly = true)
    override fun findLiveByName(name: String): Supplier? =
        suppliers.findLiveByNormalizedName(name.trim())?.toDomain()

    /**
     * El `householdId` sale del contexto y **nunca de quien llama**, igual que en
     * plataforma. La politica lo rechazaria de todos modos si fuera otro, y asi
     * el descuido es un error de programacion visible en lugar de una fila que no
     * se escribe.
     */
    override fun save(supplier: Supplier): Supplier = suppliers.save(
        SupplierEntity(
            id = supplier.id,
            householdId = requireHousehold(),
            name = supplier.name,
            serviceCategory = supplier.serviceCategory,
            contactName = supplier.contactName,
            phone = supplier.phone,
            email = supplier.email,
            website = supplier.website,
            address = supplier.address,
            notes = supplier.notes,
            createdAt = supplier.createdAt,
            updatedAt = supplier.updatedAt,
            retiredAt = supplier.retiredAt,
            createdBy = supplier.createdBy,
            updatedBy = supplier.updatedBy,
        ),
    ).toDomain()

    /**
     * Los enlaces con el nombre que el core les da **hoy**: se resuelve al leer y
     * no se copia, asi que renombrar la caldera se ve aqui sin que nadie
     * sincronice nada.
     *
     * Un enlace cuyo destino ya no exista no puede aparecer: la clave ajena va
     * con `ON DELETE CASCADE`, asi que borrar la ubicacion se lleva el enlace por
     * delante. El `mapNotNull` es el cinturon de esa regla, no su sustituto.
     */
    @Transactional(readOnly = true)
    override fun linksOf(supplierId: UUID): List<LinkedTarget> =
        links.findAllBySupplierIdOrderByCreatedAt(supplierId).mapNotNull { entity ->
            val link = entity.toDomain()
            targetName(link.target)?.let { LinkedTarget(link, it) }
        }

    @Transactional(readOnly = true)
    override fun findLink(linkId: UUID): SupplierLink? = links.findById(linkId).orElse(null)?.toDomain()

    @Transactional(readOnly = true)
    override fun hasLink(supplierId: UUID, target: LinkTarget): Boolean = when (target) {
        is LinkTarget.Asset -> links.existsBySupplierIdAndAssetId(supplierId, target.assetId)
        is LinkTarget.Location -> links.existsBySupplierIdAndLocationId(supplierId, target.locationId)
    }

    override fun saveLink(link: SupplierLink): SupplierLink = links.save(
        SupplierLinkEntity(
            id = link.id,
            householdId = requireHousehold(),
            supplierId = link.supplierId,
            assetId = (link.target as? LinkTarget.Asset)?.assetId,
            locationId = (link.target as? LinkTarget.Location)?.locationId,
            createdAt = link.createdAt,
            createdBy = link.createdBy,
        ),
    ).toDomain()

    override fun deleteLink(linkId: UUID) = links.deleteById(linkId)

    /**
     * Lee el estado del core **por SQL y sin importar ninguna clase suya**, que es
     * la misma forma que usa la siembra del modulo de prueba. Importar
     * `AssetEntity` compilaria --la direccion `modulo -> core` esta permitida-- y
     * ataria este modulo a la persistencia del core, que es peor que atarlo a dos
     * nombres de tabla.
     *
     * El nombre de un asset puede venir **de su articulo**: el core no lo guarda
     * dos veces, asi que una existencia de consumible tiene `name` a nulo y el
     * nombre efectivo es el del articulo. De ahi el `coalesce`, que es la misma
     * regla que el core aplica al leer.
     *
     * Sin `WHERE household_id`: lo pone la politica, igual que en el resto. Por
     * eso el nulo significa a la vez «no existe» y «no es de este hogar», que es
     * como el core responde a proposito.
     */
    @Transactional(readOnly = true)
    override fun targetName(target: LinkTarget): String? = when (target) {
        is LinkTarget.Asset -> jdbc.query(
            """
            SELECT coalesce(a.name, ar.name)
            FROM assets a
            LEFT JOIN articles ar ON ar.id = a.article_id
            WHERE a.id = ?
            """.trimIndent(),
            { rs, _ -> rs.getString(1) },
            target.assetId,
        ).firstOrNull()

        is LinkTarget.Location -> jdbc.query(
            "SELECT name FROM locations WHERE id = ?",
            { rs, _ -> rs.getString(1) },
            target.locationId,
        ).firstOrNull()
    }

    private fun requireHousehold(): UUID = checkNotNull(tenantContext.currentHousehold()) {
        "Escribir en el modulo de proveedores exige contexto de inquilino"
    }
}

private fun SupplierEntity.toDomain() = Supplier(
    id = id,
    name = name,
    serviceCategory = serviceCategory,
    contactName = contactName,
    phone = phone,
    email = email,
    website = website,
    address = address,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
    retiredAt = retiredAt,
    createdBy = createdBy,
    updatedBy = updatedBy,
)

/**
 * Las dos columnas excluyentes vuelven a ser un solo destino.
 *
 * El `error` no es defensivo de mas: la tabla tiene un `CHECK` que lo impide, asi
 * que llegar aqui significaria que alguien escribio saltandose el esquema, y eso
 * es un fallo que hay que ver y no un caso que tratar.
 */
private fun SupplierLinkEntity.toDomain() = SupplierLink(
    id = id,
    supplierId = supplierId,
    target = assetId?.let(LinkTarget::Asset)
        ?: locationId?.let(LinkTarget::Location)
        ?: error("El enlace $id no apunta a nada, y la tabla no lo permite"),
    createdAt = createdAt,
    createdBy = createdBy,
)
