package com.drp.platform.mail

/**
 * El puerto de correo saliente (ADR-009).
 *
 * Es deliberadamente la misma jugada que la ADR-005 hace con el almacenamiento:
 * la decision que se aplaza --que proveedor-- queda detras de una frontera, y
 * cambiarla es escribir un segundo adaptador, no tocar los casos de uso.
 *
 * **El envio ocurre siempre fuera de la transaccion.** Un fallo al entregar el
 * correo no puede deshacer el alta de un hogar ni la creacion de una invitacion:
 * el token ya esta persistido y sigue siendo valido, asi que lo correcto es
 * registrar el fallo y seguir. La contrapartida --puede haber token sin correo
 * enviado-- es el compromiso aceptado en la ADR, y para eso existe reenviar.
 */
interface EmailSender {
    fun send(message: EmailMessage)
}

data class EmailMessage(
    val to: EmailAddress,
    val subject: String,
    val body: String,
)
