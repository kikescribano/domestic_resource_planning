package com.drp.test

import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.client.exchange
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Lo minimo para hablar con la API por HTTP desde una prueba.
 *
 * Vive aqui y no en cada clase de prueba porque el Hito 2 trae veintitres
 * operaciones nuevas y con ellas la tentacion de copiar el `register()` a cada
 * fichero. Con tres copias, la que se olvida de verificar el correo pasa por
 * buena hasta que alguien mira por que su hogar no ve nada.
 *
 * Se habla por HTTP y no por MockMvc a proposito: parte de lo que estas pruebas
 * comprueban --el limitador, la cadena de filtros, los codigos de estado-- vive
 * en la capa de servlet y no existe si no hay servidor.
 */

/** Un hogar dado de alta, verificado y listo para pedir cosas. */
data class TestHousehold(
    val accessToken: String,
    val memberId: String,
    val email: String,
    /**
     * Sale del propio token, que es el unico sitio donde esta: la API no lo
     * devuelve nunca. Lo necesitan las pruebas que miran el disco, porque la ruta
     * de un fichero se troce por hogar.
     */
    val householdId: String,
    /**
     * La **identidad**, que no es la pertenencia. La ruta de un avatar se deriva
     * de ella, porque una identidad no pertenece a ningun hogar.
     */
    val identityId: String,
)

/** Los claims que lleva dentro el access token. Solo los usan las pruebas. */
private fun String.claim(name: String): String {
    val payload = String(java.util.Base64.getUrlDecoder().decode(split(".")[1]))
    return payload.extract(name)
}

/**
 * La zona del hogar que estas pruebas dan de alta.
 *
 * Vive aqui y no repetida en cada cuerpo JSON porque **es la misma que usa
 * [today]**: desde que el dia de calendario de una regla es el del hogar, la
 * fecha que una prueba envia y la zona con la que se dio de alta el hogar son el
 * mismo dato, y separarlos deja una prueba que miente en cuanto alguien cambia
 * uno de los dos.
 */
val HOUSEHOLD_ZONE: ZoneId = ZoneId.of("Europe/Madrid")

/**
 * Da de alta un hogar y lo deja verificado, leyendo el enlace del **correo real**
 * de Mailpit igual que lo leeria una persona.
 */
fun TestRestTemplate.registerHousehold(
    mailpit: DrpMailpit = DrpMailpit.instance,
    timeZone: ZoneId = HOUSEHOLD_ZONE,
): TestHousehold {
    val email = "persona-${UUID.randomUUID()}@example.test"
    postJson(
        "/api/v1/households",
        """
        {"name":"Hogar ${UUID.randomUUID()}","timeZone":"$timeZone",
         "admin":{"name":"Alguien","email":"$email","password":"el gato duerme en el sofa"}}
        """.trimIndent(),
    )

    val token = mailpit.awaitMessageTo(email).token()
    val session = postJson("/api/v1/auth/verify-email", """{"token":"$token"}""").body!!
    val accessToken = session.extract("accessToken")
    val memberId = getJson("/api/v1/users", accessToken).body!!.extract("id")

    return TestHousehold(accessToken, memberId, email, accessToken.claim("householdId"), accessToken.claim("sub"))
}

/**
 * Una de las categorias que **siembra el alta del hogar**.
 *
 * Un `DURABLE` sin articulo necesita nombre y categoria propios, asi que casi
 * toda prueba que cree un asset pasa por aqui. Los nombres de las categorias
 * sembradas son **datos** y van en castellano, al contrario que los
 * identificadores.
 */
fun TestRestTemplate.seededCategory(accessToken: String, name: String): String {
    val body = getJson("/api/v1/categories", accessToken).body!!
    val entry = Regex("\\{[^{}]*\"name\":\"$name\"[^{}]*\\}").find(body)
        ?: error("No aparece la categoría sembrada «$name»:\n$body")
    return entry.value.extract("id")
}

fun TestRestTemplate.postJson(path: String, body: String, accessToken: String? = null): ResponseEntity<String> =
    exchange(path, HttpMethod.POST, HttpEntity(body, jsonHeaders(accessToken)))

fun TestRestTemplate.patchJson(path: String, body: String, accessToken: String? = null): ResponseEntity<String> =
    exchange(path, HttpMethod.PATCH, HttpEntity(body, jsonHeaders(accessToken)))

fun TestRestTemplate.getJson(path: String, accessToken: String? = null): ResponseEntity<String> =
    exchange(path, HttpMethod.GET, HttpEntity<Void>(jsonHeaders(accessToken)))

fun TestRestTemplate.deleteJson(path: String, accessToken: String? = null): ResponseEntity<String> =
    exchange(path, HttpMethod.DELETE, HttpEntity<Void>(jsonHeaders(accessToken)))

private fun jsonHeaders(accessToken: String?) = HttpHeaders().apply {
    contentType = MediaType.APPLICATION_JSON
    accessToken?.let { setBearerAuth(it) }
}

/**
 * El primer valor de un campo del JSON, por expresion regular.
 *
 * Deliberadamente tonto: deserializar la respuesta con Jackson ataria la prueba a
 * las mismas clases que produce la respuesta, y entonces un fallo de mapeo
 * pasaria inadvertido porque las dos partes se equivocarian igual.
 */
fun String.extract(field: String): String =
    Regex("\"$field\"\\s*:\\s*\"([^\"]+)\"").find(this)?.groupValues?.get(1)
        ?: error("No aparece \"$field\" en la respuesta:\n$this")

/** El primer valor **no textual** --numero, booleano o nulo-- de un campo del JSON. */
fun String.extractRaw(field: String): String =
    Regex("\"$field\"\\s*:\\s*([^,}\\s]+)").find(this)?.groupValues?.get(1)
        ?: error("No aparece \"$field\" en la respuesta:\n$this")

/**
 * **El dia de hoy tal y como lo ve la aplicacion**, que no es el de la maquina.
 *
 * Toda regla que compare fechas de calendario --la de CMMS que rechaza una
 * intervencion «del futuro», las dos comprobaciones nocturnas-- resuelve su
 * «hoy» en **la zona del hogar**, por `HouseholdCalendar`. Una prueba que
 * enviara `LocalDate.now()` mandaria la fecha de la zona de la maquina, que no
 * tiene por que ser ninguna de las dos: en un corredor de CI puesto en UTC, a la
 * 01:30 de Madrid la maquina esta en el dia anterior al del hogar, asi que «hoy»
 * le llega como manana y responde `400`.
 *
 * El sintoma no se parece a la causa --la suite pasa entera de dia y falla de
 * madrugada, siempre en CMMS-- asi que la fecha se pide aqui, y aqui sigue a la
 * aplicacion: [HOUSEHOLD_ZONE] es la zona con la que [registerHousehold] da de
 * alta el hogar.
 *
 * Antes de este hito esto era `LocalDate.now(ZoneOffset.UTC)`, que era lo
 * correcto cuando la aplicacion resolvia el dia contra UTC. Cambio con ella; de
 * no hacerlo habria vuelto a mentir, en la otra direccion.
 */
fun today(zone: ZoneId = HOUSEHOLD_ZONE): LocalDate = LocalDate.now(zone)
