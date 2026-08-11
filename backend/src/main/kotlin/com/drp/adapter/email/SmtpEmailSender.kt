package com.drp.adapter.email

import com.drp.application.port.EmailMessage
import com.drp.application.port.EmailSender
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
