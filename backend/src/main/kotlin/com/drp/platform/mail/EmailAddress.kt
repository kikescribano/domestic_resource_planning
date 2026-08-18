package com.drp.platform.mail

/**
 * Un correo ya normalizado.
 *
 * La normalizacion vive en el tipo y no en cada sitio que compara correos, que
 * es como se cuela el fallo: basta olvidarla una vez --en el login, en la
 * invitacion, en el restablecimiento-- para que `Kike@x.com` y `kike@x.com`
 * pasen a ser dos personas distintas. Al no haber constructor publico, no existe
 * forma de construir uno sin normalizar.
 *
 * **Vive en plataforma desde el Hito 1 de la Fase 2**, y no por afan de mudanza:
 * [EmailSender] es plataforma --el resumen diario de avisos lo entrega ella-- y
 * un puerto que hable de correo tiene que poder nombrar una direccion. La
 * alternativa era que el puerto hablara de texto y perdiera en la frontera la
 * unica garantia que este tipo da. La ADR-011 lo cuenta entero; el core la sigue
 * usando igual, porque la direccion permitida es esa.
 */
@JvmInline
value class EmailAddress private constructor(val value: String) {

    override fun toString(): String = value

    companion object {
        fun of(raw: String): EmailAddress = EmailAddress(raw.trim().lowercase())
    }
}
