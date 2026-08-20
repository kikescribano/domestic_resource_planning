package com.drp.module.suppliers.adapter.http

import com.drp.core.application.port.SessionClaims
import com.drp.core.adapter.http.JsonPatch
import com.drp.module.suppliers.SuppliersModule
import com.drp.module.suppliers.application.CreateSupplier
import com.drp.module.suppliers.application.GetSupplier
import com.drp.module.suppliers.application.LinkSupplier
import com.drp.module.suppliers.application.ListSuppliers
import com.drp.module.suppliers.application.RetireSupplier
import com.drp.module.suppliers.application.SupplierCommand
import com.drp.module.suppliers.application.SupplierPatch
import com.drp.module.suppliers.application.UnlinkSupplier
import com.drp.module.suppliers.application.UpdateSupplier
import com.drp.module.suppliers.domain.ServiceCategory
import com.drp.platform.page.PageResponse
import com.drp.platform.page.Pagination
import com.fasterxml.jackson.databind.JsonNode
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Las siete operaciones del modulo, en HTTP.
 *
 * Tan fino como los del core y por el mismo motivo: traduce HTTP a un caso de uso
 * y su resultado a la forma que declara `openapi.yaml`, sin decidir nada. **No
 * recibe `householdId`**; sale del token.
 *
 * **Y no sabe nada del gate.** Todo lo que cuelga de este prefijo responde `403
 * MODULE_INACTIVE` cuando el hogar no tiene el modulo encendido, y eso lo pone un
 * filtro de plataforma sobre el prefijo que declara `SuppliersModule`: que el
 * prefijo lo declare el modulo es lo que obliga a que el que se protege y el que
 * se publica sean por fuerza el mismo. Este es el primer controlador de verdad
 * detras de ese filtro.
 *
 * Reutiliza `JsonPatch` del core --la lectura del cuerpo de un `PATCH` como arbol
 * JSON, que es la unica forma de distinguir «no menciones este campo» de «ponlo a
 * nulo»--. Es la direccion `modulo -> core`, que la ADR-010 permite a proposito;
 * duplicar ese helper daria dos versiones de una regla sutil del contrato.
 */
@RestController
@RequestMapping(SuppliersModule.ROUTE_PREFIX)
class SupplierController(
    private val listSuppliers: ListSuppliers,
    private val getSupplier: GetSupplier,
    private val createSupplier: CreateSupplier,
    private val updateSupplier: UpdateSupplier,
    private val retireSupplier: RetireSupplier,
    private val linkSupplier: LinkSupplier,
    private val unlinkSupplier: UnlinkSupplier,
) {

    @GetMapping
    fun list(
        @RequestParam(required = false) serviceCategory: ServiceCategory?,
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "false") includeRetired: Boolean,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): PageResponse<SupplierResponse> = PageResponse.of(
        listSuppliers.handle(serviceCategory, q, includeRetired, Pagination(page, size)),
        SupplierResponse::of,
    )

    /** Con sus enlaces resueltos: el identificador de la caldera no le dice nada a nadie. */
    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): SupplierDetailResponse =
        SupplierDetailResponse.of(getSupplier.handle(id))

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @AuthenticationPrincipal session: SessionClaims,
        @Valid @RequestBody input: SupplierInput,
    ): SupplierResponse = SupplierResponse.of(
        createSupplier.handle(
            session,
            SupplierCommand(
                name = input.name,
                serviceCategory = input.serviceCategory!!,
                contactName = input.contactName,
                phone = input.phone,
                email = input.email,
                website = input.website,
                address = input.address,
                notes = input.notes,
            ),
        ),
    )

    /**
     * El cuerpo llega como arbol JSON y no como DTO. Aqui la diferencia decide si
     * un `PATCH` que solo cambia el telefono **borra las notas** o las conserva.
     */
    @PatchMapping("/{id}")
    fun update(
        @AuthenticationPrincipal session: SessionClaims,
        @PathVariable id: UUID,
        @RequestBody body: JsonNode,
    ): SupplierResponse {
        val patch = JsonPatch(body)
        return SupplierResponse.of(
            updateSupplier.handle(
                session,
                id,
                SupplierPatch(
                    name = patch.requiredText("name"),
                    serviceCategory = patch.requiredEnum("serviceCategory"),
                    contactName = patch.text("contactName"),
                    phone = patch.text("phone"),
                    email = patch.text("email"),
                    website = patch.text("website"),
                    address = patch.text("address"),
                    notes = patch.text("notes"),
                ),
            ),
        )
    }

    /** Retirada **logica**: deja de ofrecerse y conserva lo que ya colgaba de el. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun retire(@AuthenticationPrincipal session: SessionClaims, @PathVariable id: UUID) =
        retireSupplier.handle(session, id)

    @PostMapping("/{id}/links")
    @ResponseStatus(HttpStatus.CREATED)
    fun link(
        @AuthenticationPrincipal session: SessionClaims,
        @PathVariable id: UUID,
        @RequestBody input: SupplierLinkInput,
    ): SupplierLinkResponse = SupplierLinkResponse.of(
        linkSupplier.handle(session, id, input.assetId, input.locationId),
    )

    /**
     * El enlace va anidado bajo su contacto, y no es decoracion: el caso de uso
     * comprueba que ese enlace es **de ese** contacto antes de borrarlo.
     */
    @DeleteMapping("/{id}/links/{linkId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unlink(@PathVariable id: UUID, @PathVariable linkId: UUID) = unlinkSupplier.handle(id, linkId)
}
