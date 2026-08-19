package com.drp.module.warehouse.application

import com.drp.module.warehouse.WarehouseModule
import com.drp.module.warehouse.domain.MovementKind
import com.drp.platform.module.ModuleSeeder
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * **La primera siembra de verdad.**
 *
 * Proveedores escribio una vacia y lo explico bien: sembrar es leer el estado
 * actual del core para ponerse al dia de lo que paso antes de encenderse, y en el
 * core no hay ningun fontanero que leer. Aqui si lo hay --las existencias que el
 * hogar ya tiene-- y esta es la clase que demuestra la regla que la ADR-010 fija:
 * **se siembra desde el estado, no reproduciendo eventos**. La entrega del bus es
 * at-least-once y en memoria, asi que el `AssetCreated` de hace un mes no esta en
 * ninguna parte a la que ir a buscarlo.
 *
 * Recorre tres cosas, y las tres por lo mismo: son los tres ejes sobre los que
 * este modulo pone reglas.
 *
 * 1. **Los articulos vivos**, abriendo su ficha sin minimo.
 * 2. **Los sitios**, abriendo la suya sin antelacion.
 * 3. **Las existencias vivas de consumible**, asentando un `OPENING` con lo que
 *    hay ahora, para que el cuaderno empiece en el estado real y no en cero.
 *
 * **Y es idempotente, porque tiene que serlo.** Esta ficha destapo que la ADR-010
 * dice «reactivar no vuelve a sembrar» y que `ActivateModule` hace lo contrario:
 * solo se ahorra la siembra cuando el modulo **ya estaba activo**, asi que pasar
 * de `INACTIVE` a `ACTIVE` la ejecuta. Se conserva el comportamiento del codigo y
 * se corrige la frase, porque un hogar que apago Warehouse tres meses se ha
 * perdido todos los eventos de ese periodo y volveria con el cuaderno de hace tres
 * meses **sin que nada lo dijera**: la pantalla ensenaria una lista corta y
 * verosimil. Un modulo que miente en silencio sobre lo que hay en la despensa es
 * peor que uno que tarda un segundo mas en encenderse.
 *
 * La idempotencia no descansa en el cuidado de nadie sino en tres indices unicos:
 * las dos fichas se abren con `ON CONFLICT DO NOTHING` y el cuaderno admite **un
 * solo `OPENING` por existencia**. Resembrar completa lo que falte y no duplica ni
 * un asiento.
 *
 * Lo que la reactivacion **no** reconstruye es el historico del periodo apagado:
 * esos movimientos no ocurrieron para este modulo y no se inventan. El cuaderno
 * recoge desde donde estaba, y el `OPENING` sigue siendo el del dia en que se
 * encendio la primera vez, que es la verdad.
 *
 * Corre **dentro de la transaccion de la activacion**: si falla, el modulo no
 * queda encendido. Un modulo activo con la mitad de los datos es un estado que
 * nadie sabria reparar.
 */
@Component
class WarehouseSeeder(private val stock: StockSynchronizer, private val warehouse: WarehouseRepository) : ModuleSeeder {

    private val log = LoggerFactory.getLogger(javaClass)

    override val moduleKey: String = WarehouseModule.KEY

    override fun seed() {
        // Sin `WHERE household_id` en ninguna de las tres: la politica del core ya
        // deja visibles solo las del hogar que se esta activando, que es la misma
        // forma que usa la siembra del modulo de prueba.
        val articles = warehouse.liveArticleIds().onEach { stock.openArticleFile(it) }
        val locations = warehouse.allLocationIds().onEach { stock.openLocationFile(it) }
        val items = warehouse.liveStockItemIds().onEach { stock.openStockItem(it, MovementKind.OPENING) }

        log.info(
            "Warehouse sembrado: {} artículos, {} sitios, {} existencias",
            articles.size,
            locations.size,
            items.size,
        )
    }
}
