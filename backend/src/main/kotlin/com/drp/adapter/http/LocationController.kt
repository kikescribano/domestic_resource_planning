package com.drp.adapter.http

import com.drp.application.port.Pagination
import com.drp.application.port.SessionClaims
import com.drp.application.usecase.CreateLocation
import com.drp.application.usecase.DeleteLocation
import com.drp.application.usecase.GetLocation
import com.drp.application.usecase.ListLocations
import com.drp.application.usecase.LocationCommand
import com.drp.application.usecase.LocationPatch
import com.drp.application.usecase.UpdateLocation
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

@RestController
@RequestMapping("/api/v1/locations")
class LocationController(
    private val listLocations: ListLocations,
    private val getLocation: GetLocation,
    private val createLocation: CreateLocation,
    private val updateLocation: UpdateLocation,
    private val deleteLocation: DeleteLocation,
) {

    /**
     * Sin `parentLocationId` devuelve el hogar entero, que es lo que el arbol del
     * cliente necesita para pintarse de una vez. Con el, solo las hijas directas.
     */
    @GetMapping
    fun list(
        @RequestParam(required = false) parentLocationId: UUID?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): PageResponse<LocationResponse> = PageResponse.of(
        listLocations.handle(parentLocationId, onlyChildren = parentLocationId != null, Pagination(page, size)),
        LocationResponse::of,
    )

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): LocationResponse = LocationResponse.of(getLocation.handle(id))

    /** Las hijas directas. Es lo que permite cargar el arbol por niveles. */
    @GetMapping("/{id}/children")
    fun children(
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): PageResponse<LocationResponse> {
        // Se resuelve la madre primero para responder 404 si no existe o es de
        // otro hogar, en lugar de una lista vacia que no distingue "no tiene
        // hijas" de "no es tuya".
        getLocation.handle(id)
        return PageResponse.of(
            listLocations.handle(id, onlyChildren = true, Pagination(page, size)),
            LocationResponse::of,
        )
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @AuthenticationPrincipal session: SessionClaims,
        @Valid @RequestBody input: LocationInput,
    ): LocationResponse = LocationResponse.of(
        createLocation.handle(
            session,
            LocationCommand(
                name = input.name,
                type = input.type!!,
                parentLocationId = input.parentLocationId,
                capacity = input.capacity?.toDomain(),
                environmentalConditions = input.environmentalConditions?.toDomain(),
                photoUrl = input.photoUrl,
                photoFileId = input.photoFileId,
                notes = input.notes,
            ),
        ),
    )

    /**
     * El cuerpo llega como arbol JSON y no como DTO: es la unica forma de
     * distinguir «no menciones este campo» de «ponlo a nulo», y aqui esa
     * diferencia decide si una ubicacion se queda donde esta o pasa a ser raiz.
     */
    @PatchMapping("/{id}")
    fun update(
        @AuthenticationPrincipal session: SessionClaims,
        @PathVariable id: UUID,
        @RequestBody body: JsonNode,
    ): LocationResponse {
        val patch = JsonPatch(body)
        return LocationResponse.of(
            updateLocation.handle(
                session,
                id,
                LocationPatch(
                    name = patch.requiredText("name"),
                    type = patch.enum("type"),
                    parentLocationId = patch.uuid("parentLocationId"),
                    capacity = patch.capacity(),
                    environmentalConditions = patch.environmentalConditions(),
                    photoUrl = patch.text("photoUrl"),
                    photoFileId = patch.uuid("photoFileId"),
                    notes = patch.text("notes"),
                ),
            ),
        )
    }

    /** Borrado **real**: una ubicacion vacia no deja historial que preservar. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: UUID) = deleteLocation.handle(id)
}
