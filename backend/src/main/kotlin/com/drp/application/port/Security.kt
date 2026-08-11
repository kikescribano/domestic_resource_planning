package com.drp.application.port

import com.drp.domain.household.MemberRole
import java.util.UUID

/**
 * Los puertos de credenciales y sesion.
 *
 * La aplicacion no sabe que el hash es Argon2id ni que el token es un JWT
 * firmado con HS256: eso vive en `com.drp.adapter.security`. Aqui solo esta lo
 * que los casos de uso necesitan pedir.
 */

/**
 * El hash de una contrasena.
 *
 * Es Argon2id y no BCrypt por un motivo concreto: BCrypt **ignora en silencio**
 * todo lo que pase de 72 bytes --no falla, trunca-- y la politica de DRP favorece
 * frases largas. Dos contrasenas distintas que compartieran los primeros 72
 * bytes serian la misma para el sistema.
 */
interface PasswordHasher {
    fun hash(rawPassword: String): String

    fun matches(rawPassword: String, hash: String): Boolean
}

/**
 * Un secreto recien emitido: el valor que viaja y el hash que se guarda.
 *
 * Van juntos porque es la unica forma de no equivocarse. El valor en claro solo
 * existe el tiempo de meterlo en un correo; lo que se persiste es siempre el
 * hash, igual que en las invitaciones, en la verificacion y en los tokens
 * acotados de prestamo.
 */
data class IssuedSecret(val plain: String, val hash: String)

/**
 * Genera los secretos que viajan por correo y los hashea para guardarlos.
 *
 * El hash de estos **no** es Argon2id sino SHA-256, y no es una incoherencia con
 * las contrasenas. Un derivador lento existe para encarecer la fuerza bruta
 * sobre secretos que una persona ha elegido y que por tanto tienen poca
 * entropia. Estos son 256 bits aleatorios: no hay diccionario que probar, asi
 * que un KDF lento no anadiria seguridad y si coste en un camino que se recorre
 * en cada verificacion.
 */
interface SecretGenerator {
    fun generate(): IssuedSecret

    fun hash(plain: String): String
}

/**
 * Lo que va dentro del access token.
 *
 * El `identityId` identifica a la **persona** y el `memberId` a su
 * **pertenencia** al hogar del token. Van los dos desde el principio aunque en
 * el MVP haya una sola pertenencia por identidad: el dia que haya varias, el
 * token no cambia de forma, solo deja de resolverse sola cual es.
 */
data class SessionClaims(
    val identityId: UUID,
    val memberId: UUID,
    val householdId: UUID,
    val role: MemberRole,
    /**
     * Cual de las sesiones de esta persona es esta: el identificador del refresh
     * token con el que se emitio.
     *
     * Es un quinto claim sobre los cuatro que documenta el contrato, y lo pide
     * una regla que sin el no se puede cumplir: `ChangePassword` revoca las
     * **demas** sesiones y conserva la que esta en uso, y para conservarla hay
     * que saber cual es. La alternativa era pedir el refresh token en el cuerpo
     * de esa operacion, que si cambiaria el contrato --y ademas obligaria al
     * cliente a mandar una credencial de vida larga donde no hacia falta.
     *
     * No es un secreto: identifica una fila cuyo valor real esta hasheado.
     */
    val sessionId: UUID,
)

interface AccessTokenIssuer {
    fun issue(claims: SessionClaims): IssuedAccessToken

    /** Devuelve nulo si el token no es valido, esta caducado o la firma no cuadra. */
    fun verify(token: String): SessionClaims?
}

data class IssuedAccessToken(val token: String, val expiresInSeconds: Long)
