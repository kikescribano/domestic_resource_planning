package com.drp.application.port

import com.drp.domain.household.MemberRole
import com.drp.domain.loan.LoanRole
import java.time.Instant
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

/**
 * Lo que va dentro del token acotado de un externo.
 *
 * **No lleva `sub`**, y esa ausencia es la definicion de lo que es: no
 * identifica a una persona, porque quien lo trae no tiene cuenta. Identifica un
 * prestamo y un extremo, y su alcance son las dos operaciones de ese prestamo.
 */
data class LoanTokenClaims(val loanId: UUID, val role: LoanRole)

/**
 * Emite y verifica los tokens acotados de prestamo.
 *
 * Devuelve el JWT **y su hash** juntos, igual que [IssuedSecret], porque las dos
 * capas son inseparables: el JWT viaja en el correo y el hash se guarda para
 * poder revocarlo. Emitir uno sin guardar el otro daria una credencial que nadie
 * puede cortar.
 *
 * [verify] comprueba **solo la firma y la caducidad**. Que la fila siga viva es
 * una regla de negocio y se decide en el caso de uso, ya con el contexto de
 * inquilino fijado, igual que la invitacion no comprueba su vigencia en la
 * funcion que resuelve el hogar.
 */
interface LoanTokenIssuer {
    fun issue(claims: LoanTokenClaims, expiresAt: Instant): IssuedLoanToken

    /** Nulo si la firma no cuadra, ha caducado o el cuerpo no es lo que se espera. */
    fun verify(token: String): LoanTokenClaims?

    /** El hash con el que buscar la fila, para poder resolver un token que llega. */
    fun hash(token: String): String
}

data class IssuedLoanToken(val token: String, val tokenHash: String)
