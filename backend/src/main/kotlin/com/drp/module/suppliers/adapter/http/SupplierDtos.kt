package com.drp.module.suppliers.adapter.http

import com.drp.module.suppliers.application.SupplierDetail
import com.drp.module.suppliers.domain.LinkTarget
import com.drp.module.suppliers.domain.LinkedTarget
import com.drp.module.suppliers.domain.ServiceCategory
import com.drp.module.suppliers.domain.Supplier
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

/**
 * Lo que entra y lo que sale, con la forma que declara `openapi.yaml`.
 *
 * Los cinco datos de contacto son opcionales **uno a uno** y obligatorios **en
 * conjunto**: al menos telefono, correo o web. Esa regla no se puede expresar con
 * anotaciones por campo, asi que vive en el dominio y responde
 * `409 SUPPLIER_CONTACT_REQUIRED`, no un `400`: lo que falla no es la forma del
 * cuerpo sino lo que ese contacto seria.
 */
data class SupplierInput(
    @field:NotBlank @field:Size(max = 160) val name: String,
    @field:NotNull val serviceCategory: ServiceCategory?,
    @field:Size(max = 160) val contactName: String? = null,
    @field:Size(max = 40) val phone: String? = null,
    @field:Size(max = 320) val email: String? = null,
    @field:Size(max = 500) val website: String? = null,
    @field:Size(max = 500) val address: String? = null,
    val notes: String? = null,
)

/**
 * El destino de un enlace, con los dos identificadores opcionales.
 *
 * **Sin anotacion que exija uno de los dos**, a proposito: «exactamente uno» es
 * una regla del contrato con codigo propio --`SUPPLIER_LINK_TARGET_INVALID`-- y
 * la decide el caso de uso. Una anotacion aqui la convertiria en un `400` de
 * forma y el cliente perderia la unica manera de distinguirla de un cuerpo
 * ilegible.
 */
data class SupplierLinkInput(
    val assetId: UUID? = null,
    val locationId: UUID? = null,
)

data class SupplierResponse(
    val id: UUID,
    val name: String,
    val serviceCategory: ServiceCategory,
    val contactName: String?,
    val phone: String?,
    val email: String?,
    val website: String?,
    val address: String?,
    val notes: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val retiredAt: Instant?,
    val createdBy: UUID?,
    val updatedBy: UUID?,
) {
    companion object {
        fun of(supplier: Supplier) = SupplierResponse(
            id = supplier.id,
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
        )
    }
}

/**
 * El enlace, aplanado a `targetType` + `targetId` + `targetName`.
 *
 * Dentro es un tipo sellado y fuera son tres campos, y esa asimetria es
 * deliberada: un contrato JSON no tiene tipos sellados, y dos claves opcionales
 * excluyentes obligarian al cliente a comprobar cual viene informada cada vez que
 * pinta una fila. El `targetName` lo resuelve el servidor contra el core al leer,
 * asi que renombrar la caldera se ve sin que nadie sincronice nada.
 */
data class SupplierLinkResponse(
    val id: UUID,
    val targetType: String,
    val targetId: UUID,
    val targetName: String,
    val createdAt: Instant,
    val createdBy: UUID?,
) {
    companion object {
        fun of(linked: LinkedTarget) = SupplierLinkResponse(
            id = linked.link.id,
            targetType = when (linked.link.target) {
                is LinkTarget.Asset -> "ASSET"
                is LinkTarget.Location -> "LOCATION"
            },
            targetId = when (val target = linked.link.target) {
                is LinkTarget.Asset -> target.assetId
                is LinkTarget.Location -> target.locationId
            },
            targetName = linked.name,
            createdAt = linked.link.createdAt,
            createdBy = linked.link.createdBy,
        )
    }
}

data class SupplierDetailResponse(
    val supplier: SupplierResponse,
    val links: List<SupplierLinkResponse>,
) {
    companion object {
        fun of(detail: SupplierDetail) = SupplierDetailResponse(
            supplier = SupplierResponse.of(detail.supplier),
            links = detail.links.map(SupplierLinkResponse::of),
        )
    }
}
