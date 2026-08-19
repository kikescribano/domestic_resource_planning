package com.drp.module.maintenance.application

import com.drp.module.maintenance.MaintenanceModule
import com.drp.platform.module.ModuleSeeder
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * **La siembra que tuvo que decidir que CREAR, y no solo que abrir.**
 *
 * Warehouse abria fichas vacias --sin minimo-- y Compras abria lineas para lo que
 * ya estaba a cero. Aqui el catalogo de eventos (README 5.2.3) decia otra cosa:
 * que CMMS «genera un plan de mantenimiento por defecto» al darse de alta un
 * asset. Esa frase se escribio en la Fase 0 como ejemplo de para que sirve el bus,
 * no como una decision de producto, y al construirlo no se sostiene: **por defecto
 * ¿de que?** Una caldera pide revision anual y una silla no pide nada.
 *
 * Las tres salidas, y por que se elige la tercera:
 *
 * - **Un plan por cada `DURABLE`** inunda el hogar el dia que enciende el modulo
 *   --una casa normal tiene decenas de cosas duraderas-- y llena la bandeja de
 *   avisos de revisiones que nadie pidio. El modulo se apagaria en una semana.
 * - **Un plan solo para algunos** exige saber que clase de maquina es cada cosa, y
 *   **el core no lo modela**: su `Category` es un catalogo por hogar cuyos nombres
 *   son datos en castellano que cada casa edita. Una lista de categorias «que
 *   piden mantenimiento» seria conocimiento de producto inventado aqui, y lo
 *   contradiria el primer hogar que llame «Aparatos» a sus electrodomesticos.
 * - **Nada, como Proveedores**, deja al handler de `AssetCreated` sin trabajo
 *   --que es justo el ejemplo con el que el README explica el bus desde la Fase
 *   0-- y a la pantalla del modulo recien encendido sin nada que ensenar.
 *
 * **Asi que lo que se abre es la ficha de cada maquina, y ningun plan.** Encender
 * CMMS deja al hogar viendo lo que ya tenia, con «sin planes todavia», y crear el
 * primero es un clic desde ahi. Es la misma forma que Warehouse eligio al abrir
 * fichas de articulo sin minimo: el modulo prepara el sitio y la regla la pone
 * quien sabe si su caldera es de gas.
 *
 * **Y su idempotencia es mas dificil que las dos anteriores**, que es lo segundo
 * que hubo que resolver. Warehouse se apoyo en un indice unico por articulo y en
 * uno solo `OPENING` por existencia; Compras, en una sola linea viva por articulo.
 * Aqui **un `DURABLE` puede tener legitimamente varios planes** --revision anual y
 * cambio de filtro cada tres meses-- asi que «un plan por asset» es falso y no hay
 * clave natural que impida duplicarlos. La respuesta tiene dos mitades, y **las
 * dos son indices y no comprobaciones previas**:
 *
 * 1. **Esta siembra no crea planes**, asi que no hay nada que duplicar. Lo que
 *    crea es la ficha, y de esa si hay exactamente una por maquina: indice unico
 *    sobre `(household_id, asset_id)` con `ON CONFLICT DO NOTHING` detras.
 * 2. **Y los planes llevan igualmente el suyo** --un nombre por maquina entre los
 *    vivos-- aunque hoy solo los cree una persona. No es ceremonia: es lo que hace
 *    que el dia que algo automatico quiera crear un plan no pueda duplicarlo, y
 *    que la carrera entre dos pulsaciones del mismo boton la cierre la base de
 *    datos y no la suerte.
 *
 * Lo que la reactivacion **no** reconstruye es el historico del periodo apagado:
 * las intervenciones de esos meses no ocurrieron para este modulo y no se
 * inventan.
 *
 * Corre **dentro de la transaccion de la activacion**: si falla, el modulo no
 * queda encendido.
 */
@Component
class MaintenanceSeeder(
    private val machines: MaintenanceSynchronizer,
    private val maintenance: MaintenanceRepository,
) : ModuleSeeder {

    private val log = LoggerFactory.getLogger(javaClass)

    override val moduleKey: String = MaintenanceModule.KEY

    override fun seed() {
        // Sin `WHERE household_id`: la politica del core ya deja visibles solo los
        // assets del hogar que se esta activando, que es la misma forma que usan la
        // siembra de Warehouse, la de Compras y la del modulo de prueba.
        val assets = maintenance.eligibleAssetIds().onEach { machines.watch(it) }

        log.info("Mantenimiento sembrado: {} máquinas entran en el radar, sin ningún plan", assets.size)
    }
}
