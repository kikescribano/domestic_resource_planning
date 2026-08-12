package com.drp.adapter.http

import com.drp.test.SpringIntegrationTest
import com.drp.test.deleteJson
import com.drp.test.extract
import com.drp.test.getJson
import com.drp.test.patchJson
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

/**
 * Las cuatro operaciones del catalogo de categorias, por HTTP y contra
 * PostgreSQL real.
 *
 * La unicidad se comprueba dos veces --en el caso de uso y con el indice
 * `categories_name_unique_live`--, asi que estas pruebas insisten en el punto
 * donde las dos podrian discrepar: la **normalizacion**. Si el caso de uso
 * comparase con un `lowercase()` de Kotlin y el indice con `immutable_unaccent`,
 * «Bricolage» pasaria la primera valla y reventaria contra la segunda con un 500.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CategoryJourneyTest : SpringIntegrationTest() {

    @Autowired private lateinit var http: TestRestTemplate

    @Test
    @DisplayName("crear, renombrar, listar y retirar: el ciclo entero de una categoria")
    fun `el recorrido del catalogo de categorias`() {
        val household = http.registerHousehold()

        val created = http.postJson(
            "/api/v1/categories",
            """{"name":"Material de escalada","notes":"Cuerdas y arneses"}""",
            household.accessToken,
        )
        created.statusCode.shouldBe(HttpStatus.CREATED)
        val categoryId = created.body!!.extract("id")

        // La autoria sale del token, nunca del cuerpo.
        created.body!!.shouldContain("\"createdBy\":\"${household.memberId}\"")

        val renamed = http.patchJson(
            "/api/v1/categories/$categoryId",
            """{"name":"Escalada","notes":"Cuerdas, arneses y pies de gato"}""",
            household.accessToken,
        )
        renamed.statusCode.shouldBe(HttpStatus.OK)
        renamed.body!!.shouldContain("Escalada")

        val listed = http.getJson("/api/v1/categories", household.accessToken)
        listed.statusCode.shouldBe(HttpStatus.OK)
        // Las cinco sembradas al crear el hogar, mas la nueva.
        listed.body!!.shouldContain("\"total\":6")
        listed.body!!.shouldContain("Alimentación")

        http.deleteJson("/api/v1/categories/$categoryId", household.accessToken)
            .statusCode.shouldBe(HttpStatus.NO_CONTENT)

        // Retirada: fuera del listado por defecto, dentro si se piden.
        http.getJson("/api/v1/categories", household.accessToken).body!!.shouldNotContain("Escalada")
        http.getJson("/api/v1/categories?includeRetired=true", household.accessToken)
            .body!!.shouldContain("Escalada")
    }

    @Test
    @DisplayName("el nombre repetido da 409 con su codigo, no un 500 por violacion de restriccion")
    fun `rechaza el nombre duplicado`() {
        val household = http.registerHousehold()

        val clash = http.postJson(
            "/api/v1/categories",
            """{"name":"Herramientas"}""",
            household.accessToken,
        )

        clash.statusCode.shouldBe(HttpStatus.CONFLICT)
        clash.body!!.shouldContain("CATEGORY_DUPLICATE")
    }

    @Test
    @DisplayName("la comparacion ignora mayusculas y acentos, igual que el indice unico")
    fun `la unicidad se compara normalizada`() {
        val household = http.registerHousehold()

        // «Decoración» es una de las sembradas. Sin acento y en minuscula es la
        // misma para el indice, asi que tiene que serlo tambien para el caso de
        // uso: si no, esto seria un 500.
        val clash = http.postJson(
            "/api/v1/categories",
            """{"name":"decoracion"}""",
            household.accessToken,
        )

        clash.statusCode.shouldBe(HttpStatus.CONFLICT)
        clash.body!!.shouldContain("CATEGORY_DUPLICATE")
    }

    @Test
    @DisplayName("una categoria retirada libera su nombre: el indice unico solo mira las vigentes")
    fun `el nombre se libera al retirar`() {
        val household = http.registerHousehold()

        val first = http.postJson("/api/v1/categories", """{"name":"Bici"}""", household.accessToken)
        http.deleteJson("/api/v1/categories/${first.body!!.extract("id")}", household.accessToken)

        http.postJson("/api/v1/categories", """{"name":"Bici"}""", household.accessToken)
            .statusCode.shouldBe(HttpStatus.CREATED)
    }

    @Test
    @DisplayName("renombrar una categoria conservando su propio nombre no choca consigo misma")
    fun `no choca consigo misma`() {
        val household = http.registerHousehold()
        val created = http.postJson("/api/v1/categories", """{"name":"Jardín"}""", household.accessToken)
        val categoryId = created.body!!.extract("id")

        val updated = http.patchJson(
            "/api/v1/categories/$categoryId",
            """{"name":"Jardín","notes":"Macetas y herramienta de mano"}""",
            household.accessToken,
        )

        updated.statusCode.shouldBe(HttpStatus.OK)
        updated.body!!.shouldContain("Macetas")
    }

    @Test
    @DisplayName("retirar dos veces no es un error ni vuelve a fijar la fecha")
    fun `la retirada es idempotente`() {
        val household = http.registerHousehold()
        val created = http.postJson("/api/v1/categories", """{"name":"Camping"}""", household.accessToken)
        val categoryId = created.body!!.extract("id")

        http.deleteJson("/api/v1/categories/$categoryId", household.accessToken)
        val retiredAt = http.getJson("/api/v1/categories?includeRetired=true", household.accessToken)
            .body!!.let { Regex("\"retiredAt\":\"([^\"]+)\"").find(it)!!.groupValues[1] }

        http.deleteJson("/api/v1/categories/$categoryId", household.accessToken)
            .statusCode.shouldBe(HttpStatus.NO_CONTENT)

        http.getJson("/api/v1/categories?includeRetired=true", household.accessToken)
            .body!!.shouldContain(retiredAt)
    }

    @Test
    @DisplayName("las categorias de A no se ven, ni se editan, ni se retiran desde B")
    fun `las categorias no cruzan de hogar`() {
        val a = http.registerHousehold()
        val b = http.registerHousehold()

        val ofA = http.postJson("/api/v1/categories", """{"name":"Bodega"}""", a.accessToken)
        val categoryOfA = ofA.body!!.extract("id")

        // Ni en el listado...
        http.getJson("/api/v1/categories", b.accessToken).body!!.shouldNotContain("Bodega")

        // ...ni pidiendola por su identificador. Y 404, no 403: distinguir "no
        // existe" de "no es tuyo" convertiria el identificador en un oraculo.
        http.patchJson("/api/v1/categories/$categoryOfA", """{"name":"Robada"}""", b.accessToken)
            .statusCode.shouldBe(HttpStatus.NOT_FOUND)
        http.deleteJson("/api/v1/categories/$categoryOfA", b.accessToken)
            .statusCode.shouldBe(HttpStatus.NOT_FOUND)

        // Y sigue intacta en el suyo.
        http.getJson("/api/v1/categories", a.accessToken).body!!.shouldContain("Bodega")
    }

    @Test
    @DisplayName("un nombre vacio es error de forma, no de regla de negocio")
    fun `el nombre en blanco da 400`() {
        val household = http.registerHousehold()

        val rejected = http.postJson("/api/v1/categories", """{"name":"   "}""", household.accessToken)

        rejected.statusCode.shouldBe(HttpStatus.BAD_REQUEST)
        rejected.body!!.shouldContain("VALIDATION_ERROR")
    }
}
