package com.drp.platform.notice

import com.drp.platform.mail.EmailMessage
import com.drp.platform.mail.EmailSender
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock

/**
 * El resumen diario: **un correo por hogar con lo que haya, y ninguno cuando no
 * hay nada**.
 *
 * Las dos mitades de esa frase son decisiones, no detalles.
 *
 * **Un correo y no uno por aviso.** Cinco modulos avisando por fecha --caducidad
 * en Warehouse, revision en CMMS, vencimiento de garantia, devolucion de un
 * prestamo, vacuna o riego-- producen una bandeja de entrada que se deja de leer
 * en una semana. El detalle esta en la aplicacion; el correo solo tiene que
 * conseguir que alguien entre a mirar.
 *
 * **Y ninguno cuando no hay nada.** Un correo diario vacio es la forma mas
 * rapida de que se filtren todos: quien recibe treinta correos que no dicen nada
 * acaba creando una regla, y con ella se va tambien el que si decia algo. De ahi
 * que el caso normal de este componente sea no hacer nada.
 *
 * El canal es el [EmailSender] de la ADR-009 sin inventar nada: el mismo por el
 * que salen los cinco correos del core, y por eso este hito lo mudo a plataforma
 * en lugar de escribir un segundo.
 */
@Component
class NoticeDigest(
    private val notices: NoticeRepository,
    private val recipients: NoticeRecipients,
    private val email: EmailSender,
    private val transactions: TransactionTemplate,
    private val clock: Clock,
    @Value("\${drp.mail.base-url}") private val baseUrl: String,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Entrega el resumen del hogar del contexto. Devuelve `true` si mando algo.
     *
     * **El envio ocurre fuera de la transaccion**, que es la regla de la ADR-009
     * y aqui ademas es la unica forma de que un SMTP lento no mantenga abierta
     * una transaccion por cada hogar del recorrido.
     *
     * Se marca **despues** de enviar y no antes. Las dos opciones son
     * at-least-once imperfectas y hay que elegir cual falla mejor: marcar primero
     * y fallar el envio pierde el aviso para siempre --nadie lo vuelve a mirar--
     * mientras que enviar y fallar el marcado repite manana un resumen que ya se
     * leyo. Repetir se nota y se aguanta; perder, ni se nota.
     */
    fun deliver(): Boolean {
        val pending = transactions.execute { notices.findPendingDigest() }.orEmpty()
        if (pending.isEmpty()) return false

        val to = transactions.execute { recipients.current() }.orEmpty()
        if (to.isEmpty()) {
            // Sin destinatario no se marca nada: los avisos siguen pendientes y
            // saldran en el resumen del dia que alguien verifique su correo. Lo
            // contrario --marcarlos igual-- los perderia en silencio.
            log.debug("El hogar tiene {} avisos pendientes y nadie a quien escribir", pending.size)
            return false
        }

        // El mismo texto para todos: es un resumen **del hogar** y no de cada
        // persona, asi que se compone una vez y solo cambia el destinatario.
        val subject = "DRP · ${headline(pending)} de tu hogar"
        val body = body(pending)
        to.forEach { email.send(EmailMessage(it, subject, body)) }

        transactions.execute { notices.markNotified(pending.map { it.id }, clock.instant()) }
        log.info("Resumen diario con {} avisos entregado a {} direcciones", pending.size, to.size)
        return true
    }

    /**
     * El enlace apunta al **frontend** y no a la API, igual que los cinco correos
     * del core: quien recibe el correo abre una pantalla, no un endpoint.
     */
    private fun body(pending: List<Notice>): String {
        val items = pending.joinToString("\n\n") { "· ${it.title}\n  ${it.body}" }

        return """
            Tu hogar tiene ${headline(pending)} sin ver:

            $items

            Puedes verlos y marcarlos como leídos aquí:

            $baseUrl/avisos

            Este correo se manda una vez al día, y solo cuando hay algo que contar.
        """.trimIndent()
    }

    private fun headline(pending: List<Notice>) =
        if (pending.size == 1) "1 aviso" else "${pending.size} avisos"
}
