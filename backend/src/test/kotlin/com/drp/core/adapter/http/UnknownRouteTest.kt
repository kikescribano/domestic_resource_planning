package com.drp.core.adapter.http

import com.drp.test.SpringIntegrationTest
import com.drp.test.getJson
import com.drp.test.postJson
import com.drp.test.registerHousehold
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Lo que responde una ruta **autenticada que no corresponde a ningun manejador**.
 *
 * El contrato declara `404` para eso, y lo que salia era `500 INTERNAL_ERROR`: el
 * `@ExceptionHandler(Exception::class)` del [ApiExceptionHandler] es lo ultimo de
 * la cadena y se tragaba tambien la `NoResourceFoundException` que lanza el
 * despachador cuando nadie atiende la ruta.
 *
 * No es una fuga --el cuerpo del `500` no lleva traza ni mensaje de la excepcion--
 * sino dos cosas distintas:
 *
 * - **Contrato.** Un cliente generico decide por el codigo de estado, y `500` le
 *   dice «reintenta, el servidor esta mal» cuando lo cierto es «esa ruta no
 *   existe, no la vuelvas a pedir».
 * - **Registro.** Cada `404` de un cliente quedaba anotado con `log.error` y su
 *   traza, de modo que un enlace viejo o un barrido ajeno ensucian el registro con
 *   errores del servidor que no lo son. Lo que se pierde con eso no es el ruido:
 *   es la senal, porque un `500` de verdad deja de distinguirse del fondo.
 *
 * El barrido va por **forma de la ruta** y no por recurso, porque la forma es lo
 * que se olvida: la barra final es la primera que aparece en un cliente escrito a
 * mano, y desde Spring 6 ya no hay correspondencia por barra final.
 *
 * La ultima prueba es la que **acota** el arreglo, y sin ella el arreglo seria
 * peor que el fallo: convertir en `404` cualquier excepcion no reconocida
 * esconderia los fallos reales del servidor detras de la respuesta mas inocente
 * que hay.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UnknownRouteTest : SpringIntegrationTest() {

    @Autowired private lateinit var http: TestRestTemplate

    @Test
    @DisplayName("una ruta que no existe responde 404 con la forma del contrato, y no 500")
    fun `la ruta sin manejador es un error del cliente`() {
        val household = http.registerHousehold()
        val id = UUID.randomUUID()

        // Cada entrada es una peticion que no puede casar con ningun manejador,
        // por un motivo distinto. Se recogen todas las desviaciones en vez de
        // cortar en la primera: un barrido que se para en la primera no dice
        // cuantas hay.
        val deviations = buildList {
            fun check(description: String, response: ResponseEntity<String>) {
                val body = response.body.orEmpty()
                if (response.statusCode != HttpStatus.NOT_FOUND) {
                    add("$description → ${response.statusCode.value()} $body")
                }
            }

            check(
                "barra final en el detalle",
                http.getJson("/api/v1/assets/$id/", household.accessToken),
            )
            check(
                "barra final en la coleccion",
                http.getJson("/api/v1/assets/", household.accessToken),
            )
            check(
                "subrecurso inexistente",
                http.getJson("/api/v1/assets/$id/owner", household.accessToken),
            )
            check(
                "coleccion mal escrita",
                http.getJson("/api/v1/assetsx/$id", household.accessToken),
            )
            check(
                "ruta en mayusculas",
                http.getJson("/API/V1/ASSETS/$id", household.accessToken),
            )
            check(
                "segmento de mas tras la accion",
                http.postJson("/api/v1/assets/$id/merge/extra", "{}", household.accessToken),
            )
        }

        deviations.shouldBe(emptyList())
    }

    /**
     * La barra olvidada en `/{id}/merge`, que **no** es una ruta sin manejador y
     * por eso no entra en el barrido de arriba.
     *
     * `/api/v1/assets/{id}merge` casa con el patron `/{id}` --tomando `{id}merge`
     * entero como identificador-- que atienden `GET`, `PATCH` y `DELETE`. Lo unico
     * que no encaja es el `POST`, asi que lo que corresponde es `405` y no `404`:
     * decir que ahi no hay nada seria mentir en el otro sentido.
     *
     * Sale del mismo defecto que el `404` --un error del cliente registrado como
     * error del servidor-- y se comprueba aparte porque su respuesta es otra.
     */
    @Test
    @DisplayName("una ruta que existe pero no para ese método responde 405 con Allow")
    fun `el metodo no admitido no es ni 404 ni 500`() {
        val household = http.registerHousehold()
        val id = UUID.randomUUID()

        val response = http.postJson("/api/v1/assets/${id}merge", "{}", household.accessToken)

        response.statusCode.shouldBe(HttpStatus.METHOD_NOT_ALLOWED)
        response.body!!.shouldContain("\"code\":\"METHOD_NOT_ALLOWED\"")

        // RFC 9110 exige `Allow` en toda respuesta 405: sin ella el cliente no
        // sabe con que reintentar y solo le queda probar.
        val allow = response.headers.getFirst("Allow").orEmpty()
        allow.shouldContain("GET")
        allow.shouldContain("PATCH")
        allow.shouldContain("DELETE")
    }

    @Test
    @DisplayName("el cuerpo del 404 es el ErrorResponse del contrato y no filtra nada")
    fun `el 404 de ruta desconocida tiene la forma unica de error`() {
        val household = http.registerHousehold()

        val response = http.getJson("/api/v1/assets/", household.accessToken)

        response.statusCode.shouldBe(HttpStatus.NOT_FOUND)
        val body = response.body!!
        body.shouldContain("\"code\":\"NOT_FOUND\"")
        body.shouldContain("\"message\"")

        // Ni la excepcion, ni la ruta pedida, ni nada del interior: la traza va al
        // registro, no a la respuesta.
        body.shouldNotContain("NoResourceFoundException")
        body.shouldNotContain("com.drp")
        body.shouldNotContain("org.springframework")
    }

    @Test
    @DisplayName("un fallo de verdad del servidor sigue siendo 500")
    fun `el arreglo no convierte en 404 lo que si es culpa del servidor`() {
        val household = http.registerHousehold()

        // Una ruta que **si** tiene manejador y revienta dentro. Es exactamente lo
        // que el catch-all existe para atender, y tiene que seguir haciendolo.
        val response = http.getJson(BoomController.PATH, household.accessToken)

        response.statusCode.shouldBe(HttpStatus.INTERNAL_SERVER_ERROR)
        response.body!!.shouldContain("\"code\":\"INTERNAL_ERROR\"")
    }

}

/**
 * Hace de fallo inesperado del servidor. Sin el, la prueba de contencion no
 * tendria nada que mirar: toda la API funciona, y esa es justo la razon por la que
 * un arreglo demasiado ancho pasaria inadvertido.
 *
 * `IllegalStateException` y no `IllegalArgumentException`: la segunda ya tiene
 * manejador propio --los `PATCH` que leen el cuerpo como arbol-- y acabaria en un
 * `400` que no comprueba nada de lo que esta prueba busca.
 *
 * **Va como componente de primer nivel y no en un `@TestConfiguration` a
 * proposito.** Un `@TestConfiguration` anidado cambia la clave del contexto de
 * Spring, asi que esta clase estrenaria contexto propio --y con el, otro pool de
 * conexiones vivo hasta el final de la JVM-- sobre los cuatro que el conjunto ya
 * mantiene. Ese quinto pool agota las conexiones del PostgreSQL compartido, y lo
 * que falla entonces no es esta prueba sino otras, con un `FATAL: remaining
 * connection slots` que no se parece en nada a su causa. Aqui lo recoge el barrido
 * de componentes del contexto que ya existe, sin estrenar ninguno.
 *
 * Solo esta en el classpath de pruebas: la aplicacion no la ve.
 */
@RestController
class BoomController {
    @GetMapping(PATH)
    fun boom(): Nothing = throw IllegalStateException("fallo simulado del servidor")

    companion object {
        const val PATH = "/api/v1/test/boom"
    }
}
