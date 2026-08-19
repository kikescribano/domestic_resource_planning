package com.drp.module.warehouse

import com.drp.platform.module.ModuleDescriptor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * La declaracion del modulo: lo que le permite existir en el catalogo que un
 * hogar puede encender.
 *
 * El Hito 0 dejo aqui solo esto, sin dominio; el **Hito 3 lo ha llenado**. Su
 * ficha esta en `docs/backend/modules/warehouse.md` y se escribio antes que la
 * primera linea de codigo, con una seccion que ninguna otra necesitaba todavia:
 * **la frontera contra el core sin ambiguedad**. El core mantiene un contador
 * --`quantity`, en la `unit` que pone el articulo-- y consumos, minimos,
 * caducidad y lotes son de aqui; este modulo **no lleva un segundo contador**.
 *
 * Es ademas el primero que **reacciona a lo que pasa en el core**: consume seis
 * de los trece eventos del catalogo y publica dos que Compras leera en el Hito 4.
 *
 * El prefijo de ruta lo declara el modulo y no plataforma, que es lo que obliga a
 * que el prefijo que el gate protege y el que el controlador publica sean por
 * fuerza el mismo.
 */
@Configuration
class WarehouseModule {

    @Bean
    fun warehouseModuleDescriptor() = ModuleDescriptor(
        key = KEY,
        name = "Almacén",
        description = "Existencias de la despensa, el garaje y el trastero: consumos, mínimos, caducidad y lotes.",
        routePrefix = ROUTE_PREFIX,
    )

    companion object {
        const val KEY = "WAREHOUSE"
        const val ROUTE_PREFIX = "/api/v1/warehouse"

        /**
         * Con cuanta antelacion se avisa de una caducidad cuando ni el sitio ni el
         * articulo lo fijan.
         *
         * Una semana: lo justo para poder gastarlo antes de tirarlo sin llenar la
         * bandeja de avisos de cosas que caducan el mes que viene. Que sea una
         * constante y no un ajuste por hogar esta anotado en las decisiones
         * abiertas de la ficha, con quien lo va a pedir primero.
         */
        const val DEFAULT_EXPIRY_LEAD_DAYS = 7
    }
}
