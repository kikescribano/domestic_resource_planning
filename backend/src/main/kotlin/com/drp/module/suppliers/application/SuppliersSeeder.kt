package com.drp.module.suppliers.application

import com.drp.module.suppliers.SuppliersModule
import com.drp.platform.module.ModuleSeeder
import org.springframework.stereotype.Component

/**
 * La siembra del modulo, que **esta vacia**.
 *
 * Se escribe igual, y no es ceremonia: es el punto de extension que los tres
 * modulos siguientes si usan --Warehouse recorriendo las existencias que el hogar
 * ya tenga, CMMS los `DURABLE`--, y lo que este hito prueba es el camino
 * completo de un modulo. Un camino al que le falta un tramo no esta probado.
 *
 * Que aqui no haya nada se ve mejor desde la regla que desde el modulo: sembrar
 * es **leer el estado actual del core** para ponerse al dia de lo que paso antes
 * de encenderse, porque la entrega del bus es at-least-once y en memoria y un
 * modulo activado hoy no vio el `AssetCreated` de hace un mes. En el core no hay
 * ningun fontanero que leer. Este dato maestro nace vacio y lo llena una persona.
 *
 * **Si algun dia deja de estar vacia**, ya se sabe con que: la categoria de
 * servicio es hoy una lista cerrada, y el dia que pase a catalogo por hogar --su
 * disparador esta escrito en la ficha del modulo-- este es el sitio donde nacen
 * sus valores por defecto.
 *
 * Corre **dentro de la transaccion de la activacion**, asi que si algun dia falla,
 * el modulo no queda encendido.
 */
@Component
class SuppliersSeeder : ModuleSeeder {

    override val moduleKey: String = SuppliersModule.KEY

    override fun seed() {
        // A proposito. Ver arriba.
    }
}
