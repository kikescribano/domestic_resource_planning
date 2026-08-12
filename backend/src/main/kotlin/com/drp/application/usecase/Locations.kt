package com.drp.application.usecase

import com.drp.application.PhotoFileResolver
import com.drp.application.event.CoreEvents
import com.drp.application.port.HierarchyLock
import com.drp.application.port.LocationRepository
import com.drp.application.port.Page
import com.drp.application.port.Pagination
import com.drp.application.port.SessionClaims
import com.drp.domain.BusinessRuleViolation
import com.drp.domain.ErrorCode
import com.drp.domain.ResourceNotFound
import com.drp.domain.inventory.Capacity
import com.drp.domain.inventory.EnvironmentalConditions
import com.drp.domain.inventory.Location
import com.drp.domain.inventory.LocationType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

/**
 * Las seis operaciones de ubicaciones.
 *
 * Aqui vive la primera de las tres validaciones que la base de datos no puede
 * garantizar: el **anti-ciclo**. Un `CHECK` solo ve la fila que se inserta, y la
 * pregunta --«¿acabaria esta ubicacion siendo su propia antecesora?»-- es sobre el
 * camino entero hasta la raiz.
 */

data class LocationCommand(
    val name: String,
    val type: LocationType,
    val parentLocationId: UUID?,
    val capacity: Capacity?,
    val environmentalConditions: EnvironmentalConditions?,
    val photoUrl: String?,
    val photoFileId: UUID?,
    val notes: String?,
)

/**
 * La modificacion parcial. Cada campo distingue **tres** situaciones y no dos:
 * ausente --no se toca--, presente con valor --se cambia-- y presente a nulo --se
 * borra--. Un `UUID?` a secas confundiria las dos primeras, y entonces cualquier
 * `PATCH` que no mencionase el padre convertiria la ubicacion en raiz.
 */
data class LocationPatch(
    val name: Patch<String> = Patch.Absent,
    val type: Patch<LocationType> = Patch.Absent,
    val parentLocationId: Patch<UUID?> = Patch.Absent,
    val capacity: Patch<Capacity?> = Patch.Absent,
    val environmentalConditions: Patch<EnvironmentalConditions?> = Patch.Absent,
    val photoUrl: Patch<String?> = Patch.Absent,
    val photoFileId: Patch<UUID?> = Patch.Absent,
    val notes: Patch<String?> = Patch.Absent,
)

/** Ausente o presente, para que «no lo menciones» y «ponlo a nulo» no sean lo mismo. */
sealed interface Patch<out T> {
    data object Absent : Patch<Nothing>

    data class Set<T>(val value: T) : Patch<T>
}

/**
 * El valor nuevo si el campo venia en el cuerpo, y [current] si no.
 *
 * Va como funcion de extension y **no** como metodo de la interfaz, que fue el
 * primer intento. Un `fun orKeep(current: @UnsafeVariance T): T` dentro de
 * `Patch<out T>` obliga a `Absent`, que es un `Patch<Nothing>`, a generar un
 * puente cuyo retorno se castea a `Nothing` --borrado a `Void`--, y toda llamada
 * sobre un campo ausente revienta con `String cannot be cast to Void` en tiempo
 * de ejecucion. Compila sin una queja, asi que solo se ve ejecutandolo.
 */
fun <T> Patch<T>.orKeep(current: T): T = when (this) {
    is Patch.Set -> value
    Patch.Absent -> current
}

@Service
class ListLocations(private val locations: LocationRepository) {

    @Transactional(readOnly = true)
    fun handle(parentLocationId: UUID?, onlyChildren: Boolean, pagination: Pagination): Page<Location> =
        locations.list(parentLocationId, onlyChildren, pagination)
}

@Service
class GetLocation(private val locations: LocationRepository) {

    @Transactional(readOnly = true)
    fun handle(locationId: UUID): Location =
        locations.findById(locationId) ?: throw ResourceNotFound("Ubicación no encontrada")
}

@Service
class CreateLocation(
    private val locations: LocationRepository,
    private val files: PhotoFileResolver,
    private val events: CoreEvents,
    private val clock: Clock,
) {

    @Transactional
    fun handle(session: SessionClaims, command: LocationCommand): Location {
        // El padre se RESUELVE contra el repositorio antes de insertar. No es
        // redundante con la clave ajena compuesta: **la comprobacion de una clave
        // ajena no pasa por RLS**, asi que una fila de otro hogar es invisible a
        // un SELECT y aun asi se puede referenciar. Sin esta linea, colgar de una
        // ubicacion ajena daria un 500 --o peor, colaria-- en vez de un 404.
        command.parentLocationId?.let { parentId ->
            locations.findById(parentId) ?: throw ResourceNotFound("Ubicación padre no encontrada")
        }
        files.requireUsable(command.photoFileId)

        requireNameFreeAmongSiblings(locations, command.name, command.parentLocationId, exceptId = null)

        val now = clock.instant()
        val created = locations.save(
            Location(
                id = UUID.randomUUID(),
                name = command.name.trim(),
                type = command.type,
                parentLocationId = command.parentLocationId,
                capacity = command.capacity,
                environmentalConditions = command.environmentalConditions,
                photoUrl = command.photoUrl,
                photoFileId = command.photoFileId,
                notes = command.notes,
                createdAt = now,
                updatedAt = now,
                createdBy = session.memberId,
                updatedBy = session.memberId,
            ),
        )

        // No hace falta comprobar ciclo al crear: una ubicacion recien nacida no
        // tiene descendientes, asi que ningun padre puede estar por debajo de ella.
        events.locationCreated(created.id, created.name, created.type.name, created.parentLocationId)
        return created
    }
}

@Service
class UpdateLocation(
    private val locations: LocationRepository,
    private val hierarchyLock: HierarchyLock,
    private val files: PhotoFileResolver,
    private val clock: Clock,
) {

    @Transactional
    fun handle(session: SessionClaims, locationId: UUID, patch: LocationPatch): Location {
        // El cerrojo se toma **antes de leer** y en **toda** modificacion, no solo
        // en las que mueven. Las dos condiciones costaron un fallo de CI cada una.
        //
        // Antes de leer, porque lo que hay que hacer indivisible es el ciclo
        // entero de leer, comprobar y escribir: comprobar sobre un estado que
        // otra transaccion ya cambio es exactamente el ciclo que se busca evitar.
        //
        // Y en toda modificacion porque `save` reescribe **la fila entera**,
        // incluido `parent_location_id`. Un simple renombrado lo reescribe con el
        // valor que leyo al empezar, asi que un movimiento confirmado con un 200
        // en medio de esos dos instantes **se deshace en silencio** --y con tres
        // peticiones a la vez, esa reescritura puede cerrar un ciclo que la
        // comprobacion anti-ciclo nunca llego a ver, porque para ella no habia
        // movimiento que comprobar.
        hierarchyLock.acquire()

        val current = locations.findById(locationId) ?: throw ResourceNotFound("Ubicación no encontrada")

        val movesInHierarchy = patch.parentLocationId is Patch.Set &&
            patch.parentLocationId.value != current.parentLocationId

        val newParentId = patch.parentLocationId.orKeep(current.parentLocationId)
        if (movesInHierarchy && newParentId != null) {
            locations.findById(newParentId) ?: throw ResourceNotFound("Ubicación padre no encontrada")
            requireNoCycle(locationId, newParentId, locations::ancestorsOf)
        }

        val newName = patch.name.orKeep(current.name)
        if (newName != current.name || newParentId != current.parentLocationId) {
            requireNameFreeAmongSiblings(locations, newName, newParentId, exceptId = locationId)
        }

        if (patch.photoFileId is Patch.Set) files.requireUsable(patch.photoFileId.value)

        return locations.save(
            current.copy(
                name = newName.trim(),
                type = patch.type.orKeep(current.type),
                parentLocationId = newParentId,
                capacity = patch.capacity.orKeep(current.capacity),
                environmentalConditions = patch.environmentalConditions.orKeep(current.environmentalConditions),
                photoUrl = patch.photoUrl.orKeep(current.photoUrl),
                photoFileId = patch.photoFileId.orKeep(current.photoFileId),
                notes = patch.notes.orKeep(current.notes),
                updatedAt = clock.instant(),
                updatedBy = session.memberId,
            ),
        )
    }
}

/**
 * Borrado **real**, a diferencia de la baja de un asset o la retirada de un
 * articulo: una ubicacion vacia no deja historial que preservar.
 */
@Service
class DeleteLocation(
    private val locations: LocationRepository,
    private val hierarchyLock: HierarchyLock,
) {

    @Transactional
    fun handle(locationId: UUID) {
        // Tambien aqui: comprobar que no cuelga nada y borrar son dos pasos, y
        // entre ellos cabe un movimiento que meta algo dentro.
        hierarchyLock.acquire()

        locations.findById(locationId) ?: throw ResourceNotFound("Ubicación no encontrada")

        if (locations.countChildren(locationId) > 0) {
            throw BusinessRuleViolation(
                ErrorCode.LOCATION_HAS_CHILDREN,
                "La ubicación tiene otras ubicaciones dentro",
            )
        }
        if (locations.countAssetsIn(locationId) > 0) {
            throw BusinessRuleViolation(
                ErrorCode.LOCATION_HAS_ASSETS,
                "La ubicación todavía tiene assets dentro",
            )
        }

        locations.delete(locationId)
    }
}

/**
 * La comprobacion anti-ciclo, compartida por las dos jerarquias.
 *
 * La pregunta no es «¿es [newParentId] hijo de [nodeId]?» sino «¿esta [nodeId] en
 * algun punto **por encima** de [newParentId]?». Comprobar solo el hijo directo
 * dejaria pasar el ciclo de tres nodos, que es igual de mortal: recorrer la
 * jerarquia despues no termina nunca.
 *
 * Recibe [ancestorsOf] como funcion, y no un repositorio, porque assets y
 * ubicaciones tienen jerarquias distintas y la regla es la misma: una sola copia
 * de la logica, dos formas de recorrer.
 */
internal fun requireNoCycle(nodeId: UUID, newParentId: UUID, ancestorsOf: (UUID) -> List<UUID>) {
    if (newParentId == nodeId) {
        throw BusinessRuleViolation(ErrorCode.LOCATION_CYCLE, "Una ubicación no puede colgar de sí misma")
    }
    if (nodeId in ancestorsOf(newParentId)) {
        throw BusinessRuleViolation(
            ErrorCode.LOCATION_CYCLE,
            "El destino está por debajo de la ubicación que se mueve",
        )
    }
}

/**
 * Unico **entre hermanas**, no en todo el hogar: dos armarios pueden llamarse
 * igual en cuartos distintos, pero no en el mismo. Con el padre a nulo compara
 * entre las raices, que es lo que cubre el `NULLS NOT DISTINCT` del indice.
 */
private fun requireNameFreeAmongSiblings(
    locations: LocationRepository,
    name: String,
    parentLocationId: UUID?,
    exceptId: UUID?,
) {
    val clash = locations.findByNameAmongSiblings(name.trim(), parentLocationId) ?: return
    if (clash.id == exceptId) return

    throw BusinessRuleViolation(
        ErrorCode.LOCATION_DUPLICATE,
        "Ya hay una ubicación con ese nombre en el mismo sitio",
    )
}
