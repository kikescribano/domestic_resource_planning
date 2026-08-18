package com.drp.platform.notice

import com.drp.platform.mail.EmailAddress

/**
 * A quien va el resumen diario del hogar.
 *
 * **Lo declara plataforma y lo implementa el core**, igual que
 * [com.drp.platform.tenant.HouseholdDirectory] y por el mismo motivo: quien sabe
 * de personas es el core --pertenencias, identidades, correos verificados-- y
 * plataforma no puede preguntarselo sin apoyarse en el, que es la tercera regla
 * de ArchUnit de la ADR-010. Asi que plataforma declara la pregunta y el core la
 * contesta.
 *
 * Es tambien la frontera que deja el dia de manana cambiar a quien se avisa
 * --solo administradores, quien lo haya pedido, un canal que no sea el correo--
 * sin tocar el recorrido periodico.
 */
interface NoticeRecipients {

    /**
     * Las direcciones del hogar del contexto, o vacio si no hay a quien
     * escribir.
     *
     * Vacio es un estado normal y no un error: un hogar cuya unica persona
     * nunca verifico su correo no tiene destinatario, y ahi lo correcto es no
     * enviar nada --el aviso se queda pendiente y sale en el resumen del dia que
     * alguien verifique.
     */
    fun current(): List<EmailAddress>
}
