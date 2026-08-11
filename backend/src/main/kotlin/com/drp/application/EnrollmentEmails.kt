package com.drp.application

import com.drp.application.port.EmailMessage
import com.drp.domain.identity.EmailAddress
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Los cuatro correos del enrolamiento.
 *
 * Los enlaces apuntan al **frontend** y no a la API: quien recibe el correo abre
 * una pantalla, no un endpoint. La pantalla es la que llama despues al endpoint
 * con el token.
 */
@Component
class EnrollmentEmails(
    @Value("\${drp.mail.base-url}") private val baseUrl: String,
) {

    fun verification(to: EmailAddress, householdName: String, token: String) = EmailMessage(
        to = to,
        subject = "Confirma tu correo para activar $householdName",
        body = """
            Has creado el hogar "$householdName" en DRP.

            Para activarlo, confirma esta dirección:

            ${link("/verificar-correo", token)}

            El enlace caduca en 7 días. Si no confirmas, el hogar y esta cuenta se
            eliminan solos pasado ese plazo.

            Si no has sido tú, no hace falta que hagas nada.
        """.trimIndent(),
    )

    /**
     * Se envia cuando alguien intenta crear un hogar con un correo que **ya esta
     * registrado**.
     *
     * Es la otra mitad de que el endpoint responda siempre lo mismo. La API no
     * puede decir "ese correo ya existe" sin convertirse en un comprobador de
     * quien usa el sistema, asi que quien se entera de lo que ha pasado es el
     * dueno de la direccion, por correo, y nadie mas.
     */
    fun householdAttemptOnExistingAddress(to: EmailAddress) = EmailMessage(
        to = to,
        subject = "Alguien ha intentado crear un hogar con tu correo",
        body = """
            Alguien ha intentado crear un hogar en DRP con esta dirección, que ya
            tiene una cuenta. No se ha creado nada.

            Si has sido tú, entra con tu cuenta de siempre. Si no recuerdas la
            contraseña, puedes restablecerla desde la pantalla de acceso.

            Si no has sido tú, no hace falta que hagas nada: nadie ha podido ver
            ni cambiar nada de tu cuenta.
        """.trimIndent(),
    )

    fun invitation(to: EmailAddress, householdName: String, token: String) = EmailMessage(
        to = to,
        subject = "Te han invitado al hogar $householdName",
        body = """
            Te han invitado a formar parte del hogar "$householdName" en DRP.

            Para aceptar:

            ${link("/aceptar-invitacion", token)}

            La invitación caduca en 7 días.

            Si no esperabas esta invitación, puedes ignorar este correo.
        """.trimIndent(),
    )

    fun passwordReset(to: EmailAddress, token: String) = EmailMessage(
        to = to,
        subject = "Restablece tu contraseña de DRP",
        body = """
            Has pedido restablecer tu contraseña.

            ${link("/restablecer-contrasena", token)}

            El enlace caduca en 1 hora y solo se puede usar una vez. Al usarlo se
            cerrarán todas tus sesiones abiertas.

            Si no lo has pedido tú, no hace falta que hagas nada: mientras no uses
            el enlace, tu contraseña sigue siendo la misma.
        """.trimIndent(),
    )

    private fun link(path: String, token: String): String =
        "$baseUrl$path?token=${URLEncoder.encode(token, StandardCharsets.UTF_8)}"
}
