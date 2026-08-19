package com.drp.module.purchasing

import com.drp.platform.module.ModuleDescriptor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * La declaracion del modulo: lo que le permite existir en el catalogo que un hogar
 * puede encender.
 *
 * El Hito 0 dejo aqui solo esto, sin dominio; el **Hito 4 lo ha llenado**. Su ficha
 * esta en `docs/backend/modules/purchasing.md` y se escribio antes que la primera
 * linea de codigo, con la frontera contra Warehouse sin ambiguedad: **Warehouse
 * detecta la falta, Compras decide que se compra y cuando**.
 *
 * Es el modulo con el que se retira **el riesgo arquitectonico principal de la
 * fase** --dos modulos que se hablan sin depender uno de que el otro este activo--
 * y trae dos primeras veces:
 *
 * - **Lee el dato maestro de otro modulo**, por un puerto de plataforma que no
 *   nombra a ningun modulo (`MasterDataDirectory`) y con la degradacion puesta en
 *   plataforma: con Proveedores apagado el directorio responde vacio y aqui no hay
 *   una sola rama para ello.
 * - **Escribe en el core.** Cerrar una compra invoca `RegisterConsumableIntake`,
 *   que **crea existencias**; Warehouse solo movia un contador que ya existia. Y
 *   como el core publica al hacerlo, cerrar una compra deja un asiento en el
 *   cuaderno de Warehouse sin que ninguno de los dos modulos sepa del otro.
 *
 * El prefijo de ruta lo declara el modulo y no plataforma, que es lo que obliga a
 * que el prefijo que el gate protege y el que el controlador publica sean por
 * fuerza el mismo.
 */
@Configuration
class PurchasingModule {

    @Bean
    fun purchasingModuleDescriptor() = ModuleDescriptor(
        key = KEY,
        name = "Compras y lista de la compra",
        description = "Qué falta, qué hay que reponer y qué está pedido, hasta que la compra entra en casa.",
        routePrefix = ROUTE_PREFIX,
    )

    companion object {
        const val KEY = "PURCHASING"
        const val ROUTE_PREFIX = "/api/v1/purchasing"
    }
}
