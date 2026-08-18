package com.drp.platform.module.persistence

import com.drp.platform.module.HouseholdModule
import com.drp.platform.module.HouseholdModuleRepository
import com.drp.platform.module.ModuleStatus
import com.drp.platform.tenant.TenantContext
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * La activacion, en PostgreSQL.
 *
 * Plana y sin `household_id` en las consultas, igual que los repositorios del
 * core: el filtro lo pone la politica de RLS de `household_modules` a partir del
 * `app.household_id` que fija el gestor de transacciones. Una consulta sin
 * `WHERE` aqui devuelve exactamente las filas del hogar de la sesion, y fuera de
 * una transaccion con contexto no devuelve ninguna.
 */
@Entity
@Table(name = "household_modules")
class HouseholdModuleEntity(
    @Id var id: UUID,
    var householdId: UUID,
    var moduleKey: String,
    @Enumerated(EnumType.STRING) var status: ModuleStatus,
    var activatedAt: Instant?,
    var deactivatedAt: Instant?,
    var createdAt: Instant,
    var updatedAt: Instant,
    var createdBy: UUID?,
    var updatedBy: UUID?,
)

interface HouseholdModuleJpaRepository : JpaRepository<HouseholdModuleEntity, UUID> {

    fun findByModuleKey(moduleKey: String): HouseholdModuleEntity?
}

@Repository
class HouseholdModuleRepositoryAdapter(
    private val modules: HouseholdModuleJpaRepository,
    private val tenantContext: TenantContext,
) : HouseholdModuleRepository {

    /**
     * `REQUIRES_NEW`, y no por gusto.
     *
     * Es el unico acceso a datos de todo el gate, y lo llaman dos sitios que no
     * se parecen: el filtro HTTP, que corre antes de que exista ninguna
     * transaccion, y un handler de evento, que corre `AFTER_COMMIT` con la
     * transaccion del core todavia abierta pero con su `SET LOCAL
     * app.household_id` ya sin efecto. En el segundo caso, unirse a la
     * transaccion del core devolveria **cero filas** --medido en
     * `EventBusSweepTest` para el core-- y un modulo activo pareceria apagado sin
     * un solo error.
     *
     * Abrirla propia tiene ademas la otra mitad de la defensa: un handler que se
     * une a la transaccion del core y falla la marca `rollbackOnly` y **se lleva
     * por delante al core**. Con `REQUIRES_NEW` eso no puede pasar por aqui.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    override fun activeKeys(): Set<String> =
        modules.findAll().filter { it.status == ModuleStatus.ACTIVE }.map { it.moduleKey }.toSet()

    @Transactional(readOnly = true)
    override fun findAll(): List<HouseholdModule> = modules.findAll().map { it.toDomain() }

    @Transactional(readOnly = true)
    override fun find(moduleKey: String): HouseholdModule? = modules.findByModuleKey(moduleKey)?.toDomain()

    /**
     * El `householdId` sale del contexto y **nunca de quien llama**. La politica
     * lo rechazaria igual si fuera otro --el `WITH CHECK` implicito de la V7-- y
     * asi el fallo es un error de programacion visible en lugar de una fila que
     * no se escribe.
     */
    override fun save(module: HouseholdModule): HouseholdModule {
        val householdId = checkNotNull(tenantContext.currentHousehold()) {
            "Guardar la activacion de un modulo exige contexto de inquilino"
        }

        return modules.save(
            HouseholdModuleEntity(
                id = module.id,
                householdId = householdId,
                moduleKey = module.key,
                status = module.status,
                activatedAt = module.activatedAt,
                deactivatedAt = module.deactivatedAt,
                createdAt = module.createdAt,
                updatedAt = module.updatedAt,
                createdBy = module.createdBy,
                updatedBy = module.updatedBy,
            ),
        ).toDomain()
    }
}

private fun HouseholdModuleEntity.toDomain() = HouseholdModule(
    id = id,
    key = moduleKey,
    status = status,
    activatedAt = activatedAt,
    deactivatedAt = deactivatedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    createdBy = createdBy,
    updatedBy = updatedBy,
)
