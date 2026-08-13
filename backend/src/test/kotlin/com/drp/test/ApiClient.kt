package com.drp.test

import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.client.exchange
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
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
)

/** El `householdId` que lleva dentro el access token. Solo lo usan las pruebas. */
private fun String.householdIdClaim(): String {
    val payload = String(java.util.Base64.getUrlDecoder().decode(split(".")[1]))
    return payload.extract("householdId")
}

/**
 * Da de alta un hogar y lo deja verificado, leyendo el enlace del **correo real**
 * de Mailpit igual que lo leeria una persona.
 */
fun TestRestTemplate.registerHousehold(mailpit: DrpMailpit = DrpMailpit.instance): TestHousehold {
    val email = "persona-${UUID.randomUUID()}@example.test"
    postJson(
        "/api/v1/households",
        """
        {"name":"Hogar ${UUID.randomUUID()}","timeZone":"Europe/Madrid",
         "admin":{"name":"Alguien","email":"$email","password":"el gato duerme en el sofa"}}
        """.trimIndent(),
    )

    val token = mailpit.awaitMessageTo(email).token()
    val session = postJson("/api/v1/auth/verify-email", """{"token":"$token"}""").body!!
    val accessToken = session.extract("accessToken")
    val memberId = getJson("/api/v1/users", accessToken).body!!.extract("id")

    return TestHousehold(accessToken, memberId, email, accessToken.householdIdClaim())
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
