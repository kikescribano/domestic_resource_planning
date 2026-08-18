package com.drp.module.suppliers.application

import com.drp.module.suppliers.domain.LinkTarget
import com.drp.module.suppliers.domain.LinkedTarget
import com.drp.module.suppliers.domain.ServiceCategory
import com.drp.module.suppliers.domain.Supplier
import com.drp.module.suppliers.domain.SupplierLink
import com.drp.platform.page.Page
import com.drp.platform.page.Pagination
import java.util.UUID

/**
 * El almacen del modulo.
 *
 * **Ninguna operacion recibe el hogar**, exactamente igual que los repositorios
 * del core y los dos de plataforma: el filtro lo pone la politica de RLS a partir
 * del `app.household_id` que fija el gestor de transacciones. Un parametro
 * `householdId` aqui seria la puerta por la que se cuela leer el hogar de al
 * lado, porque tarde o temprano alguien lo tomaria del cuerpo de la peticion.
 */
interface SupplierRepository {

    fun list(
        serviceCategory: ServiceCategory?,
        query: String?,
        includeRetired: Boolean,
        pagination: Pagination,
    ): Page<Supplier>

    fun find(supplierId: UUID): Supplier?

    /** Para la regla de nombre unico, que solo mira a los vigentes. */
    fun findLiveByName(name: String): Supplier?

    fun save(supplier: Supplier): Supplier

    /** Los enlaces de un contacto, con el nombre que el core les da hoy. */
    fun linksOf(supplierId: UUID): List<LinkedTarget>

    fun findLink(linkId: UUID): SupplierLink?

    fun hasLink(supplierId: UUID, target: LinkTarget): Boolean

    fun saveLink(link: SupplierLink): SupplierLink

    fun deleteLink(linkId: UUID)

    /**
     * El nombre de lo que hay al otro lado de un enlace, o nulo si no existe
     * **para este hogar**.
     *
     * Es una lectura del estado del core, que es la direccion permitida, y se
     * resuelve por SQL sin importar ninguna clase suya --igual que hace la siembra
     * del modulo de prueba--. Importar `AssetEntity` funcionaria y ataria el
     * modulo a la persistencia del core, que es peor que atarlo a dos nombres de
     * tabla.
     *
     * El nulo hace aqui el mismo trabajo que en el core: «no existe» y «existe
     * pero es de otro hogar» responden igual, porque distinguirlos convierte
     * cualquier identificador en un oraculo.
     */
    fun targetName(target: LinkTarget): String?
}
