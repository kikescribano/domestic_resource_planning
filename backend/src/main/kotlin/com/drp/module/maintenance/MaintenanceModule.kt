package com.drp.module.maintenance

import com.drp.platform.module.ModuleDescriptor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * La declaracion del modulo: lo que le permite existir en el catalogo que un hogar
 * puede encender.
 *
 * El Hito 0 dejo aqui solo esto, sin dominio; el **Hito 5 lo ha llenado**, y con
 * el quedan construidos los cuatro modulos de prioridad alta de la Fase 2. Su
 * ficha esta en `docs/backend/modules/maintenance.md` y se escribio antes que la
 * primera linea de codigo, con **la frontera contra el planificador de tareas
 * escrita por adelantado y sin ambiguedad**:
 *
 * > **De CMMS es el CUANDO. Del planificador de tareas, el QUIEN LO HACE.**
 *
 * Es la unica frontera de la fase escrita **sin el otro lado delante** --Warehouse
 * escribio la suya contra el core, que existia; Compras contra Warehouse, que
 * existia-- y por eso importaba dejarla clavada antes de que hubiera una tabla que
 * la contradijese. Un plan es una **regla** sobre una maquina: no tiene
 * responsable, no tiene dia y no se completa. Una tarea es un **encargo**: tiene
 * las tres cosas. De ahi que aqui ninguna tabla lleve responsable y no haya
 * ninguna con una fila por ocurrencia futura.
 *
 * Y trae dos cosas que ningun modulo anterior podia traer:
 *
 * - **Es el segundo consumidor del puerto de dato maestro**, que es su prueba de
 *   verdad: `MasterDataDirectory` se diseno con un consumidor delante. Se decide
 *   **no ensancharlo** --filtrar por categoria seria la funcionalidad equivocada,
 *   y agrupar el selector ya cabe en el `detail` que entrega--, y de paso se
 *   ejercita por fin la garantia que Proveedores declaro por adelantado y para
 *   este caso: **un contacto retirado sigue siendo legible por su identificador**.
 * - **Es el primero cuyo aviso por fecha tiene que volver a armarse.** Una revision
 *   es periodica, asi que el estado del aviso cuelga de **la proxima fecha
 *   prevista** y no del plan: cualquier camino que mueva esa fecha lo rearma sin
 *   tener que acordarse.
 *
 * El prefijo de ruta lo declara el modulo y no plataforma, que es lo que obliga a
 * que el prefijo que el gate protege y el que el controlador publica sean por
 * fuerza el mismo.
 */
@Configuration
class MaintenanceModule {

    @Bean
    fun maintenanceModuleDescriptor() = ModuleDescriptor(
        key = KEY,
        name = "Mantenimiento",
        description = "Planes de mantenimiento preventivo sobre lo duradero, intervenciones correctivas e histórico.",
        routePrefix = ROUTE_PREFIX,
    )

    companion object {
        const val KEY = "MAINTENANCE"
        const val ROUTE_PREFIX = "/api/v1/maintenance"
    }
}
