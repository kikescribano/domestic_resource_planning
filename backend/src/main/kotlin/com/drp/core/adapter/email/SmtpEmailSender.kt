package com.drp.core.adapter.email

import com.drp.core.application.port.EmailMessage
import com.drp.core.application.port.EmailSender
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.MailException
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component

/**
 * Adaptador SMTP de [EmailSender] (ADR-009).
 *
 * En desarrollo y en pruebas apunta a Mailpit, que captura todo lo que se envia
 * y lo expone por API --que es como el recorrido vertical lee el enlace de
 * verificacion sin ningun paso manual--. En produccion apunta al SMTP del
 * proveedor que se elija al desplegar, sin tocar codigo.
 *
 * **La entrega es sincrona, y eso deja un hueco conocido.** La ADR-009 pide que
 * la respuesta al cliente no dependa del envio, y aqui no depende de su
 * *resultado* --un fallo se registra y se sigue-- pero si de su *duracion*: en
 * `RequestPasswordReset` y `ResendVerification` solo se envia cuando la
 * identidad existe, asi que un correo registrado tarda la ida y vuelta SMTP
 * entera y uno desconocido responde en el acto. Los dos devuelven `202`, pero el
 * reloj los distingue.
 *
 * No afecta al alta de un hogar, que es el camino mas expuesto: ese envia en las
 * **dos** ramas --verificacion, o aviso a quien ya tenia cuenta-- y ademas paga
 * un Argon2id que domina el tiempo.
 *
 * Se intento sacar la entrega a un pool propio y se revirtio: cada mensaje abre
 * una conexion SMTP nueva que en algunos equipos cuesta segundos, la cola se
 * acumulaba por detras y el recorrido vertical dejaba de ser verificable. Sacarlo
 * del hilo exige antes reutilizar la conexion o encolar de verdad, y eso es un
 * cambio con entidad propia, no un ajuste. Queda anotado en 4.1.7.
 */
@Component
class SmtpEmailSender(
    private val mailSender: JavaMailSender,
    @Value("\${drp.mail.from}") private val from: String,
) : EmailSender {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun send(message: EmailMessage) {
        val mail = SimpleMailMessage().apply {
            setFrom(from)
            setTo(message.to.value)
            setSubject(message.subject)
            setText(message.body)
        }

        try {
            mailSender.send(mail)
        } catch (failure: MailException) {
            // No se propaga. La respuesta al cliente no depende del resultado del
            // envio, porque hacerla depender reintroduciria por el lado del
            // tiempo de respuesta la fuga de informacion que las respuestas
            // constantes evitan: un correo que tarda mas en fallar delata que la
            // direccion existia.
            //
            // Se registra el destinatario y el motivo --nunca el token, que es
            // una credencial-- porque es la unica forma de diagnosticar un hogar
            // que se purgo a los siete dias sin que nadie se enterase.
            log.error("No se pudo entregar el correo a {}: {}", message.to.value, failure.message)
        }
    }
}
