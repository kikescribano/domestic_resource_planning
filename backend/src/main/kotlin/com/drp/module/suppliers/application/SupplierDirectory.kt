package com.drp.module.suppliers.application

import com.drp.module.suppliers.SuppliersModule
import com.drp.module.suppliers.domain.Supplier
import com.drp.platform.directory.MasterDataDirectory
import com.drp.platform.directory.MasterDataEntry
import com.drp.platform.page.Pagination
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * **Lo que Proveedores ensena a quien no es Proveedores.**
 *
 * Es la mitad de este modulo que responde la pregunta que su ficha dejo abierta
 * en el Hito 2 --«como lee otro modulo el dato maestro de Proveedores»-- y que
 * vence en el Hito 4 con Compras delante. La respuesta es un puerto de
 * plataforma, y este es su lado de aca.
 *
 * **Lo que este modulo ya se habia comprometido a cumplir**, y aqui se cumple:
 *
 * - **Es de solo lectura para quien no es Proveedores.** No hay ningun metodo que
 *   cree, modifique ni retire nada, y no por omision: es lo que la ficha declara
 *   que un consumidor puede dar por hecho.
 * - **Un contacto retirado sigue siendo legible por su identificador.** Una compra
 *   de marzo tiene que poder decir donde se compro aunque ese sitio ya no se
 *   ofrezca. Por eso [find] no filtra por retirada y [search] si: lo primero es
 *   leer historia y lo segundo, elegir.
 *
 * **Y no se abre nada nuevo por la ventana.** La entrada lleva el nombre y la
 * categoria, no el telefono ni el correo: los datos personales de terceros que la
 * ficha protege se quedan en este modulo. Un consumidor que necesitara llamar al
 * fontanero no necesita este puerto, necesita la pantalla de Proveedores.
 *
 * En `detail` va el **identificador** de la categoria y no su rotulo, que es lo
 * que la ficha decidio: el texto que se lee en pantalla es un dato en castellano
 * y lo pone el cliente, y componerlo aqui daria dos versiones del mismo nombre.
 *
 * **Abre transaccion propia** porque quien pregunta puede no tener ninguna
 * abierta --el `ReceivePurchase` de Compras corre sin transaccion a proposito--
 * y sin ella no hay `app.household_id`, asi que la politica de RLS devolveria
 * cero filas diciendo «no existe» sobre algo que si existe.
 */
@Component
class SupplierDirectory(private val suppliers: SupplierRepository) : MasterDataDirectory {

    override val moduleKey: String = SuppliersModule.KEY

    @Transactional(readOnly = true)
    override fun find(id: UUID): MasterDataEntry? = suppliers.find(id)?.asEntry()

    @Transactional(readOnly = true)
    override fun search(query: String?, limit: Int): List<MasterDataEntry> = suppliers.list(
        serviceCategory = null,
        query = query?.trim()?.takeIf { it.isNotEmpty() },
        includeRetired = false,
        pagination = Pagination(0, limit),
    ).items.map { it.asEntry() }

    private fun Supplier.asEntry() = MasterDataEntry(id, name, serviceCategory.name)
}
