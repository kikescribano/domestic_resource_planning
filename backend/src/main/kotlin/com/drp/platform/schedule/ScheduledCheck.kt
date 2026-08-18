package com.drp.platform.schedule

import com.drp.platform.notice.NoticeDraft

/**
 * De quien es una comprobacion periodica.
 *
 * Existe porque el recorrido tiene que **saltarse los hogares que no tengan
 * activo el modulo que la pide**, y para eso hace falta que cada comprobacion
 * diga de quien es. Es un tipo sellado y no una clave nullable a proposito: con
 * un `String?`, «del core» y «se me olvido declararlo» se escriben igual, y el
 * segundo caso haria correr en todos los hogares algo que solo debia correr en
 * unos pocos --sin que nada fallara.
 */
sealed interface CheckOwner {

    /** La clave del modulo, o nulo si es del core. */
    val moduleKey: String?

    /**
     * Del core. **Corre en todos los hogares**, porque el core no se apaga: un
     * prestamo vence igual en un hogar que no ha encendido ningun modulo.
     */
    data object Core : CheckOwner {
        override val moduleKey: String? = null
    }

    /** De un modulo. Corre **solo** donde ese modulo esta encendido. */
    data class Module(val key: String) : CheckOwner {
        override val moduleKey: String = key
    }
}

/**
 * Lo que hay que mirar en un hogar, cada dia.
 *
 * Es el punto de extension de la plataforma de avisos: **cada modulo posee su
 * regla** de que se avisa y cuando --caducidad en Warehouse, revision en CMMS--
 * y plataforma pone el recorrido y el canal. El core tiene hoy tres, que son los
 * procesos diarios que la Fase 1 escribio y nadie programaba.
 *
 * **Se implementa siempre por hogar y nunca por instalacion.** Quien recorre los
 * hogares es [DailySweep], que invoca esto ya dentro del `runAs` del hogar y
 * dentro de una transaccion, de modo que `app.household_id` esta fijado y la
 * politica de RLS acota lo que se ve. Una comprobacion que iterase hogares por
 * su cuenta seria un segundo recorrido, y solo uno de los dos puede saltarse los
 * hogares con el modulo apagado.
 */
interface ScheduledCheck {

    /**
     * Como se llama en el registro. Es un identificador y va en ingles; se usa
     * ademas para ordenar el recorrido, de modo que dos ejecuciones hagan lo
     * mismo en el mismo orden.
     */
    val name: String

    /** Quien la pide. Ver [CheckOwner]. */
    val owner: CheckOwner

    /**
     * Mira el hogar actual y devuelve lo que haya que avisar.
     *
     * Devolver la lista vacia es el caso normal y no significa que no haya
     * pasado nada: una comprobacion puede haber hecho su trabajo --marcar,
     * purgar-- sin que eso sea noticia para nadie.
     *
     * **Tiene que ser idempotente.** Corre todos los dias, y un reinicio o un
     * despliegue pueden hacer que corra dos veces el mismo dia; producir el
     * mismo aviso dos veces es la forma mas rapida de que el resumen diario se
     * vuelva ilegible.
     */
    fun check(): List<NoticeDraft>
}
