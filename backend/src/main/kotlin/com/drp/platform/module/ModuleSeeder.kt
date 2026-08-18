package com.drp.platform.module

/**
 * Como se pone al dia un modulo que acaba de encenderse.
 *
 * **Se siembra desde el estado actual del core, no reproduciendo eventos**, y
 * esta es la regla que mas facil es incumplir. La entrega del bus es
 * at-least-once y en memoria: un modulo activado hoy no vio el `AssetCreated` de
 * hace un mes y no hay donde ir a buscarlo. Asi que cada modulo lee lo que ya
 * hay --Warehouse las existencias, CMMS los `DURABLE`-- en lugar de esperar a
 * que el core le cuente lo que ya paso.
 *
 * El dia que exista el Transactional Outbox que nombra README 5.2.2 esto no
 * cambia: reproducir un ano de eventos para sembrar seguiria siendo peor que
 * leer el estado.
 *
 * Corre **dentro de la transaccion de la activacion**. Si la siembra falla, el
 * modulo no queda encendido: es preferible un error visible a un modulo activo
 * con la mitad de los datos, que es un estado que nadie sabria reparar.
 */
interface ModuleSeeder {

    val moduleKey: String

    fun seed()
}
