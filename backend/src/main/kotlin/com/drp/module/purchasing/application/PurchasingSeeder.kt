package com.drp.module.purchasing.application

import com.drp.module.purchasing.PurchasingModule
import com.drp.module.purchasing.domain.ItemOrigin
import com.drp.platform.module.ModuleSeeder
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * **La siembra que no era evidente**, y la mitad interesante es lo que NO siembra.
 *
 * Sembrar es leer el estado actual del core para ponerse al dia de lo que paso
 * antes de encenderse. Proveedores escribio una vacia --en el core no hay ningun
 * fontanero que leer-- y Warehouse recorrio las existencias. ¿Que recorre Compras?
 *
 * **Lo tentador era sembrar la lista con lo que ya esta bajo minimos, y eso no se
 * puede.** El minimo vive en `warehouse_articles`, que es una tabla de otro modulo:
 * ninguna clase suya es importable --ArchUnit falla la construccion-- y leer su
 * tabla por SQL seria la misma frontera rota, solo que sin nada que lo delate.
 *
 * **Lo que si se puede leer es el contador del core**, que es de quien es. «Que
 * articulos tiene el hogar a cero ahora mismo» es una pregunta que se le hace a
 * `assets` sin que Warehouse intervenga, y es exactamente la mitad de la regla del
 * modulo que se puede reconstruir mirando el estado: **lo que llega a cero entra en
 * la lista**. La siembra la aplica hacia atras.
 *
 * Asi que un hogar que enciende Compras con la despensa vacia **no nace ciego**, y
 * no hace falta que Warehouse este encendido para ello --que es justo lo que la
 * frontera promete: los dos lados funcionan solos.
 *
 * Lo que la siembra **no** reconstruye es lo que estuvo bajo minimos durante el
 * periodo apagado, ni lo que se compro entonces: eso no ocurrio para este modulo y
 * no se inventa.
 *
 * **Y es idempotente, porque tiene que serlo**: reactivar vuelve a sembrar desde el
 * Hito 3. La idempotencia no descansa en el cuidado de nadie sino en el indice
 * unico parcial de una linea viva por articulo, con `ON CONFLICT DO NOTHING`
 * detras. Resembrar completa lo que falte y no duplica ni una linea.
 *
 * Corre **dentro de la transaccion de la activacion**: si falla, el modulo no queda
 * encendido.
 */
@Component
class PurchasingSeeder(
    private val list: ShoppingListSynchronizer,
    private val purchasing: PurchasingRepository,
) : ModuleSeeder {

    private val log = LoggerFactory.getLogger(javaClass)

    override val moduleKey: String = PurchasingModule.KEY

    override fun seed() {
        // Sin `WHERE household_id`: la politica del core ya deja visibles solo las
        // existencias del hogar que se esta activando, que es la misma forma que
        // usan la siembra de Warehouse y la del modulo de prueba.
        val depleted = purchasing.depletedArticleIds()
        depleted.forEach { list.need(it, ItemOrigin.DEPLETED) }

        log.info("Compras sembrado: {} artículos a cero entran en la lista", depleted.size)
    }
}
