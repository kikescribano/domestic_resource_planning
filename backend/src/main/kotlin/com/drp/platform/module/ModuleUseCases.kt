package com.drp.platform.module

import com.drp.core.application.port.SessionClaims
import com.drp.platform.error.ResourceNotFound
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

/**
 * Las tres operaciones de la activacion.
 *
 * Dependen del core en **un solo punto**: `SessionClaims`, que es de donde sale
 * la autoria de la activacion. Es una asimetria conocida y acotada --la sesion es
 * hoy del core porque el core fue lo primero que existio-- y esta nombrada una a
 * una en la regla de ArchUnit, de modo que ampliar la lista es un cambio visible
 * y no un descuido. Lo que ninguna regla admite es la direccion contraria.
 */

/** Una entrada del catalogo con lo que este hogar ha decidido sobre ella. */
data class ModuleView(
    val descriptor: ModuleDescriptor,
    val activation: HouseholdModule?,
) {
    val isActive: Boolean get() = activation?.isActive == true
}

/**
 * El catalogo **entero** con el estado del hogar, tambien para quien no
 * administra.
 *
 * No se recorta por rol. Quien no administra no puede encender nada --eso lo
 * corta la autorizacion de las otras dos operaciones-- pero si necesita ver que
 * existe, porque es la unica forma de que sepa que puede pedirlo. Devolver dos
 * catalogos distintos segun quien pregunte dejaria ademas al frontend sin poder
 * distinguir «apagado» de «no me lo ensenan».
 */
@Service
class ListModules(
    private val registry: ModuleRegistry,
    private val modules: HouseholdModuleRepository,
) {

    @Transactional(readOnly = true)
    fun handle(): List<ModuleView> {
        val decided = modules.findAll().associateBy { it.key }
        return registry.catalogue.map { ModuleView(it, decided[it.key]) }
    }
}

@Service
class ActivateModule(
    private val registry: ModuleRegistry,
    private val modules: HouseholdModuleRepository,
    private val seeders: List<ModuleSeeder>,
    private val cache: ModuleActivationCache,
    private val clock: Clock,
) {

    /**
     * Encender es idempotente: volver a encender lo ya encendido no vuelve a
     * sembrar. Sembrar dos veces es justo lo que ningun `ModuleSeeder` puede
     * garantizar que sea inocuo.
     */
    @Transactional
    fun handle(session: SessionClaims, moduleKey: String): ModuleView {
        val descriptor = registry.find(moduleKey) ?: throw ResourceNotFound("El módulo «$moduleKey» no existe")
        val existing = modules.find(moduleKey)

        if (existing != null && existing.isActive) return ModuleView(descriptor, existing)

        val now = clock.instant()
        val saved = modules.save(
            existing?.copy(
                status = ModuleStatus.ACTIVE,
                activatedAt = now,
                deactivatedAt = null,
                updatedAt = now,
                updatedBy = session.memberId,
            ) ?: HouseholdModule(
                id = UUID.randomUUID(),
                key = moduleKey,
                status = ModuleStatus.ACTIVE,
                activatedAt = now,
                deactivatedAt = null,
                createdAt = now,
                updatedAt = now,
                createdBy = session.memberId,
                updatedBy = session.memberId,
            ),
        )

        seeders.filter { it.moduleKey == moduleKey }.forEach { it.seed() }

        // La respuesta de esta misma peticion tiene que ver el modulo encendido,
        // y el gate de la siguiente tambien.
        cache.clear()
        return ModuleView(descriptor, saved)
    }
}

/**
 * Apagar **conserva los datos y los oculta**.
 *
 * No borra nada: el modulo deja de publicar sus rutas, deja de atender eventos y
 * desaparece de la navegacion, y sus filas siguen donde estaban. Se descarto
 * borrar al desactivar --destructivo, irreversible, y obligaria a definir el
 * borrado en cascada de cuatro modulos antes de escribir el primero-- y se
 * descarto bloquear la desactivacion cuando hay datos, que convierte la
 * activacion en casi irreversible y castiga justo al hogar que probo el modulo.
 */
@Service
class DeactivateModule(
    private val registry: ModuleRegistry,
    private val modules: HouseholdModuleRepository,
    private val cache: ModuleActivationCache,
    private val clock: Clock,
) {

    @Transactional
    fun handle(session: SessionClaims, moduleKey: String): ModuleView {
        val descriptor = registry.find(moduleKey) ?: throw ResourceNotFound("El módulo «$moduleKey» no existe")
        val existing = modules.find(moduleKey)

        // Apagar lo que nunca se encendio no inserta una fila: la ausencia ya
        // significa inactivo, y escribirla solo dejaria rastro de una decision
        // que nadie tomo.
        if (existing == null) return ModuleView(descriptor, null)
        if (!existing.isActive) return ModuleView(descriptor, existing)

        val now = clock.instant()
        val saved = modules.save(
            existing.copy(
                status = ModuleStatus.INACTIVE,
                deactivatedAt = now,
                updatedAt = now,
                updatedBy = session.memberId,
            ),
        )

        cache.clear()
        return ModuleView(descriptor, saved)
    }
}
