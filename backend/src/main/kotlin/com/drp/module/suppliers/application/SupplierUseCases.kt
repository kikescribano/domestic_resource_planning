package com.drp.module.suppliers.application

import com.drp.core.application.port.SessionClaims
import com.drp.core.application.usecase.Patch
import com.drp.core.application.usecase.orKeep
import com.drp.module.suppliers.domain.LinkTarget
import com.drp.module.suppliers.domain.LinkedTarget
import com.drp.module.suppliers.domain.ServiceCategory
import com.drp.module.suppliers.domain.Supplier
import com.drp.module.suppliers.domain.SupplierLink
import com.drp.platform.error.BusinessRuleViolation
import com.drp.platform.error.ErrorCode
import com.drp.platform.error.ResourceNotFound
import com.drp.platform.page.Page
import com.drp.platform.page.Pagination
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

/**
 * Las siete operaciones del modulo.
 *
 * Son pocas a proposito: lo que la Fase 2 prueba aqui es **el camino completo de
 * un modulo** --ficha, dominio, migracion, contrato, gate, siembra, pantallas y
 * recorrido vertical--, no su dominio. El dominio grande llega con Warehouse.
 *
 * **Del core toma tres cosas y ninguna es del dominio**: `SessionClaims`, de
 * donde sale la autoria; y `Patch` con su `orKeep`, que es la semantica de
 * `PATCH` del contrato entero --ausente no se toca, presente a nulo se borra-- y
 * duplicarla aqui daria dos versiones de una regla sutil. La direccion
 * `modulo -> core` esta permitida por la ADR-010 y es deliberada; la contraria no
 * la admite ninguna regla.
 *
 * Ninguna publica evento. El criterio del catalogo (README 5.2.3) es que alguien
 * lo necesite, y los dos consumidores de este dato maestro llegan en los Hitos 4
 * y 5: publicar hoy seria inventar el contrato de una conversacion que no ha
 * empezado, y un evento publicado se retira peor de lo que se anade.
 */

data class SupplierCommand(
    val name: String,
    val serviceCategory: ServiceCategory,
    val contactName: String?,
    val phone: String?,
    val email: String?,
    val website: String?,
    val address: String?,
    val notes: String?,
)

data class SupplierPatch(
    val name: Patch<String> = Patch.Absent,
    val serviceCategory: Patch<ServiceCategory> = Patch.Absent,
    val contactName: Patch<String?> = Patch.Absent,
    val phone: Patch<String?> = Patch.Absent,
    val email: Patch<String?> = Patch.Absent,
    val website: Patch<String?> = Patch.Absent,
    val address: Patch<String?> = Patch.Absent,
    val notes: Patch<String?> = Patch.Absent,
)

/** Un contacto con sus enlaces resueltos, que es lo que su ficha necesita. */
data class SupplierDetail(
    val supplier: Supplier,
    val links: List<LinkedTarget>,
)

@Service
class ListSuppliers(private val suppliers: SupplierRepository) {

    @Transactional(readOnly = true)
    fun handle(
        serviceCategory: ServiceCategory?,
        query: String?,
        includeRetired: Boolean,
        pagination: Pagination,
    ): Page<Supplier> = suppliers.list(serviceCategory, query?.trim()?.takeIf { it.isNotEmpty() }, includeRetired, pagination)
}

@Service
class GetSupplier(private val suppliers: SupplierRepository) {

    @Transactional(readOnly = true)
    fun handle(supplierId: UUID): SupplierDetail {
        val supplier = suppliers.find(supplierId) ?: throw ResourceNotFound("Contacto de servicio no encontrado")
        return SupplierDetail(supplier, suppliers.linksOf(supplierId))
    }
}

@Service
class CreateSupplier(
    private val suppliers: SupplierRepository,
    private val clock: Clock,
) {

    @Transactional
    fun handle(session: SessionClaims, command: SupplierCommand): Supplier {
        val now = clock.instant()
        val supplier = Supplier(
            id = UUID.randomUUID(),
            name = command.name.trim(),
            serviceCategory = command.serviceCategory,
            contactName = command.contactName.tidy(),
            phone = command.phone.tidy(),
            email = command.email.tidy(),
            website = command.website.tidy(),
            address = command.address.tidy(),
            notes = command.notes.tidy(),
            createdAt = now,
            updatedAt = now,
            retiredAt = null,
            createdBy = session.memberId,
            updatedBy = session.memberId,
        )

        requireReachable(supplier)
        requireNameAvailable(suppliers, supplier.name, exceptId = null)

        return suppliers.save(supplier)
    }
}

@Service
class UpdateSupplier(
    private val suppliers: SupplierRepository,
    private val clock: Clock,
) {

    @Transactional
    fun handle(session: SessionClaims, supplierId: UUID, patch: SupplierPatch): Supplier {
        val current = suppliers.find(supplierId) ?: throw ResourceNotFound("Contacto de servicio no encontrado")

        val updated = current.copy(
            name = patch.name.orKeep(current.name).trim(),
            serviceCategory = patch.serviceCategory.orKeep(current.serviceCategory),
            contactName = patch.contactName.orKeep(current.contactName).tidy(),
            phone = patch.phone.orKeep(current.phone).tidy(),
            email = patch.email.orKeep(current.email).tidy(),
            website = patch.website.orKeep(current.website).tidy(),
            address = patch.address.orKeep(current.address).tidy(),
            notes = patch.notes.orKeep(current.notes).tidy(),
            updatedAt = clock.instant(),
            updatedBy = session.memberId,
        )

        requireReachable(updated)
        // Solo si cambia, y solo entre los vigentes: renombrar un retirado no
        // choca con nadie porque el indice unico tampoco lo mira.
        if (updated.name != current.name && current.isLive) {
            requireNameAvailable(suppliers, updated.name, exceptId = supplierId)
        }

        return suppliers.save(updated)
    }
}

/**
 * Retirada **logica**, igual que la de una categoria del core: los enlaces la
 * referencian, asi que borrar la fila romperia el historial de quien instalo la
 * caldera. Deja de ofrecerse al enlazar y conserva lo que ya colgaba de ella.
 */
@Service
class RetireSupplier(
    private val suppliers: SupplierRepository,
    private val clock: Clock,
) {

    @Transactional
    fun handle(session: SessionClaims, supplierId: UUID) {
        val supplier = suppliers.find(supplierId) ?: throw ResourceNotFound("Contacto de servicio no encontrado")

        // Retirar dos veces no es un error: el resultado es el mismo y volver a
        // fijar la fecha falsearia cuando se dejo de llamar a esta gente.
        if (!supplier.isLive) return

        val now = clock.instant()
        suppliers.save(supplier.copy(retiredAt = now, updatedAt = now, updatedBy = session.memberId))
    }
}

@Service
class LinkSupplier(
    private val suppliers: SupplierRepository,
    private val clock: Clock,
) {

    @Transactional
    fun handle(session: SessionClaims, supplierId: UUID, assetId: UUID?, locationId: UUID?): LinkedTarget {
        val supplier = suppliers.find(supplierId) ?: throw ResourceNotFound("Contacto de servicio no encontrado")

        // Ni ninguno ni los dos. Es una regla del contrato con codigo propio y no
        // un `400` de forma, igual que el destino de un documento en el core: lo
        // que falla no es como esta escrito el cuerpo sino que lo que pide no
        // existe como relacion.
        val target = LinkTarget.from(assetId, locationId)
            ?: throw BusinessRuleViolation(
                ErrorCode.SUPPLIER_LINK_TARGET_INVALID,
                "Un enlace apunta a un asset o a una ubicación, y a una sola cosa",
            )

        // Un contacto retirado no admite enlaces nuevos. Los que ya tenia se
        // conservan: la caldera la sigue habiendo instalado quien la instalo.
        if (!supplier.isLive) {
            throw BusinessRuleViolation(
                ErrorCode.SUPPLIER_RETIRED,
                "Ese contacto está retirado y no admite enlaces nuevos",
            )
        }

        // El destino se RESUELVE contra el core antes de insertar, igual que el
        // padre de una ubicacion. No es redundante con la clave ajena compuesta:
        // **la comprobacion de una clave ajena no pasa por RLS**, asi que una fila
        // de otro hogar es invisible a un SELECT y aun asi se puede referenciar.
        // Sin esta linea, enlazar con la caldera del vecino daria un 500 --o peor,
        // colaria-- en vez de un 404.
        val name = suppliers.targetName(target)
            ?: throw ResourceNotFound("Eso con lo que quieres enlazar no existe en este hogar")

        if (suppliers.hasLink(supplierId, target)) {
            throw BusinessRuleViolation(
                ErrorCode.SUPPLIER_LINK_DUPLICATE,
                "Ese contacto ya está enlazado con eso",
            )
        }

        val link = suppliers.saveLink(
            SupplierLink(
                id = UUID.randomUUID(),
                supplierId = supplierId,
                target = target,
                createdAt = clock.instant(),
                createdBy = session.memberId,
            ),
        )
        return LinkedTarget(link, name)
    }
}

@Service
class UnlinkSupplier(private val suppliers: SupplierRepository) {

    @Transactional
    fun handle(supplierId: UUID, linkId: UUID) {
        val link = suppliers.findLink(linkId) ?: throw ResourceNotFound("Ese enlace no existe")

        // Que el enlace sea de ESE contacto, y no solo que exista. Sin esto,
        // `DELETE /suppliers/{otro}/links/{id}` borraria igual: la ruta anida el
        // enlace bajo su contacto y la respuesta tiene que respetarlo.
        if (link.supplierId != supplierId) throw ResourceNotFound("Ese enlace no existe")

        suppliers.deleteLink(linkId)
    }
}

/**
 * El nombre es unico **entre los vigentes** del hogar, sin distinguir mayusculas
 * ni acentos.
 *
 * Se comprueba aqui aunque el indice unico ya lo garantice, y las dos vallas
 * hacen falta por lo mismo que en el catalogo del core: el indice convierte el
 * choque en un 500 por violacion de restriccion y lo que el contrato declara es
 * un `409`; la comprobacion da el mensaje bueno y el indice cierra la carrera
 * entre dos peticiones simultaneas, que ninguna comprobacion previa puede cerrar.
 */
private fun requireNameAvailable(suppliers: SupplierRepository, name: String, exceptId: UUID?) {
    val clash = suppliers.findLiveByName(name) ?: return
    if (clash.id == exceptId) return

    throw BusinessRuleViolation(
        ErrorCode.SUPPLIER_DUPLICATE,
        "Ya hay un contacto de servicio con ese nombre",
    )
}

private fun requireReachable(supplier: Supplier) {
    if (supplier.isReachable()) return

    throw BusinessRuleViolation(
        ErrorCode.SUPPLIER_CONTACT_REQUIRED,
        "Hace falta al menos un teléfono, un correo o una web",
    )
}

/**
 * Un campo opcional que llega en blanco es un campo vacio, no un espacio.
 *
 * Importa mas de lo que parece: sin esto, un `phone` con un espacio dentro
 * cumpliria la restriccion `NOT NULL OR ...` de la tabla y dejaria pasar
 * exactamente el caso que esa regla existe para impedir.
 */
private fun String?.tidy(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
