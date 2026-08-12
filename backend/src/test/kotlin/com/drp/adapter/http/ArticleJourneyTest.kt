package com.drp.adapter.http

import com.drp.test.SpringIntegrationTest
import com.drp.test.TestHousehold
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
import java.util.UUID

/**
 * Las cinco operaciones del catalogo de articulos.
 *
 * El foco esta en las dos reglas que dependen de las existencias vivas --la
 * `unit` inmutable y la retirada-- y en que la categoria se **resuelva** antes de
 * insertar. Las que exigen existencias de verdad se completan en el recorrido de
 * assets, que es quien puede crearlas.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ArticleJourneyTest : SpringIntegrationTest() {

    @Autowired private lateinit var http: TestRestTemplate

    @Test
    @DisplayName("crear, consultar, corregir, buscar y retirar: el ciclo entero de un articulo")
    fun `el recorrido del catalogo de articulos`() {
        val home = http.registerHousehold()
        val category = home.firstCategoryId()

        val created = http.postJson(
            "/api/v1/articles",
            """
            {"name":"Azúcar blanquilla","categoryId":"$category","unit":"GRAM",
             "brand":"Azucarera","barcode":"8410000000017","packSize":1000}
            """.trimIndent(),
            home.accessToken,
        )
        created.statusCode.shouldBe(HttpStatus.CREATED)
        val articleId = created.body!!.extract("id")

        // El nombre de la categoria viaja resuelto, no solo su identificador.
        created.body!!.shouldContain("\"category\":\"Alimentación\"")

        http.getJson("/api/v1/articles/$articleId", home.accessToken).body!!.shouldContain("Azucarera")

        val corrected = http.patchJson(
            "/api/v1/articles/$articleId",
            """{"brand":"Azucarera Española","notes":"la del paquete amarillo"}""",
            home.accessToken,
        )
        corrected.statusCode.shouldBe(HttpStatus.OK)
        corrected.body!!.shouldContain("Azucarera Española")

        // La busqueda compara normalizada, igual que la unicidad: «azucar» sin
        // acento tiene que encontrar «Azúcar».
        http.getJson("/api/v1/articles?q=azucar", home.accessToken).body!!.shouldContain("Azúcar")
        http.getJson("/api/v1/articles?barcode=8410000000017", home.accessToken)
            .body!!.shouldContain("\"total\":1")
        http.getJson("/api/v1/articles?categoryId=$category", home.accessToken)
            .body!!.shouldContain("\"total\":1")

        // Sin existencias vivas se retira sin problema.
        http.deleteJson("/api/v1/articles/$articleId", home.accessToken)
            .statusCode.shouldBe(HttpStatus.NO_CONTENT)

        http.getJson("/api/v1/articles", home.accessToken).body!!.shouldNotContain("Azúcar")
        http.getJson("/api/v1/articles?includeRetired=true", home.accessToken).body!!.shouldContain("Azúcar")
    }

    @Test
    @DisplayName("el nombre repetido da 409, y la comparacion ignora mayusculas y acentos")
    fun `rechaza el nombre duplicado normalizado`() {
        val home = http.registerHousehold()
        val category = home.firstCategoryId()
        home.createArticle("""{"name":"Café molido","categoryId":"$category","unit":"GRAM"}""")

        val clash = http.postJson(
            "/api/v1/articles",
            """{"name":"cafe molido","categoryId":"$category","unit":"GRAM"}""",
            home.accessToken,
        )

        clash.statusCode.shouldBe(HttpStatus.CONFLICT)
        clash.body!!.shouldContain("ARTICLE_DUPLICATE")
    }

    @Test
    @DisplayName("el codigo de barras repetido tambien da 409, con el mismo codigo")
    fun `rechaza el codigo de barras duplicado`() {
        val home = http.registerHousehold()
        val category = home.firstCategoryId()
        home.createArticle("""{"name":"Leche entera","categoryId":"$category","unit":"LITER","barcode":"8400000000001"}""")

        val clash = http.postJson(
            "/api/v1/articles",
            """{"name":"Leche desnatada","categoryId":"$category","unit":"LITER","barcode":"8400000000001"}""",
            home.accessToken,
        )

        clash.statusCode.shouldBe(HttpStatus.CONFLICT)
        clash.body!!.shouldContain("ARTICLE_DUPLICATE")
    }

    @Test
    @DisplayName("sin existencias, la unidad si se puede corregir")
    fun `la unidad se cambia mientras no haya nada contado`() {
        val home = http.registerHousehold()
        val category = home.firstCategoryId()
        val articleId = home.createArticle("""{"name":"Detergente","categoryId":"$category","unit":"UNIT"}""")

        val fixed = http.patchJson("/api/v1/articles/$articleId", """{"unit":"LITER"}""", home.accessToken)

        fixed.statusCode.shouldBe(HttpStatus.OK)
        fixed.body!!.shouldContain("\"unit\":\"LITER\"")
    }

    @Test
    @DisplayName("una categoria retirada ya no sirve para clasificar")
    fun `no se clasifica contra una categoria retirada`() {
        val home = http.registerHousehold()
        val category = http.postJson("/api/v1/categories", """{"name":"Provisional"}""", home.accessToken)
            .body!!.extract("id")
        http.deleteJson("/api/v1/categories/$category", home.accessToken)

        val rejected = http.postJson(
            "/api/v1/articles",
            """{"name":"Algo","categoryId":"$category","unit":"UNIT"}""",
            home.accessToken,
        )

        rejected.statusCode.shouldBe(HttpStatus.NOT_FOUND)
    }

    @Test
    @DisplayName("una categoria inventada da 404, no un 500 por clave ajena")
    fun `la categoria se resuelve antes de insertar`() {
        val home = http.registerHousehold()

        val rejected = http.postJson(
            "/api/v1/articles",
            """{"name":"Algo","categoryId":"${UUID.randomUUID()}","unit":"UNIT"}""",
            home.accessToken,
        )

        rejected.statusCode.shouldBe(HttpStatus.NOT_FOUND)
    }

    @Test
    @DisplayName("clasificar con la categoria de otro hogar da 404, aunque la clave ajena la aceptaria")
    fun `no se clasifica con una categoria ajena`() {
        val a = http.registerHousehold()
        val b = http.registerHousehold()
        val categoryOfA = a.firstCategoryId()

        // La categoria de A existe, asi que la clave ajena simple la aceptaria:
        // PostgreSQL comprueba las claves ajenas por dentro, sin pasar por las
        // politicas. Lo que lo impide es que el caso de uso la resuelva antes.
        val rejected = http.postJson(
            "/api/v1/articles",
            """{"name":"Intruso","categoryId":"$categoryOfA","unit":"UNIT"}""",
            b.accessToken,
        )

        rejected.statusCode.shouldBe(HttpStatus.NOT_FOUND)
    }

    @Test
    @DisplayName("los articulos de A no se ven, ni se editan, ni se retiran desde B")
    fun `los articulos no cruzan de hogar`() {
        val a = http.registerHousehold()
        val b = http.registerHousehold()
        val ofA = a.createArticle("""{"name":"Taladro Bosch","categoryId":"${a.firstCategoryId()}","unit":"UNIT"}""")

        http.getJson("/api/v1/articles", b.accessToken).body!!.shouldNotContain("Taladro")
        http.getJson("/api/v1/articles/$ofA", b.accessToken).statusCode.shouldBe(HttpStatus.NOT_FOUND)
        http.patchJson("/api/v1/articles/$ofA", """{"brand":"Robada"}""", b.accessToken)
            .statusCode.shouldBe(HttpStatus.NOT_FOUND)
        http.deleteJson("/api/v1/articles/$ofA", b.accessToken).statusCode.shouldBe(HttpStatus.NOT_FOUND)

        http.getJson("/api/v1/articles", a.accessToken).body!!.shouldContain("Taladro")
    }

    @Test
    @DisplayName("retirar libera el nombre y el codigo de barras: los dos indices solo miran los vigentes")
    fun `el nombre y el codigo se liberan al retirar`() {
        val home = http.registerHousehold()
        val category = home.firstCategoryId()
        val first = home.createArticle(
            """{"name":"Pilas AA","categoryId":"$category","unit":"UNIT","barcode":"8499999999999"}""",
        )
        http.deleteJson("/api/v1/articles/$first", home.accessToken)

        http.postJson(
            "/api/v1/articles",
            """{"name":"Pilas AA","categoryId":"$category","unit":"UNIT","barcode":"8499999999999"}""",
            home.accessToken,
        ).statusCode.shouldBe(HttpStatus.CREATED)
    }

    @Test
    @DisplayName("una unidad inventada es error de forma, no un 500")
    fun `la unidad invalida da 400`() {
        val home = http.registerHousehold()
        val category = home.firstCategoryId()

        http.postJson(
            "/api/v1/articles",
            """{"name":"X","categoryId":"$category","unit":"ARROBA"}""",
            home.accessToken,
        ).statusCode.shouldBe(HttpStatus.BAD_REQUEST)
    }

    @Test
    @DisplayName("un packSize de cero o negativo se rechaza por forma")
    fun `el packSize tiene que ser positivo`() {
        val home = http.registerHousehold()
        val category = home.firstCategoryId()

        http.postJson(
            "/api/v1/articles",
            """{"name":"X","categoryId":"$category","unit":"GRAM","packSize":0}""",
            home.accessToken,
        ).statusCode.shouldBe(HttpStatus.BAD_REQUEST)
    }

    private fun TestHousehold.createArticle(body: String): String {
        val created = http.postJson("/api/v1/articles", body, accessToken)
        created.statusCode.shouldBe(HttpStatus.CREATED)
        return created.body!!.extract("id")
    }

    /** «Alimentación» es una de las cinco que siembra el alta del hogar. */
    private fun TestHousehold.firstCategoryId(): String {
        val body = http.getJson("/api/v1/categories", accessToken).body!!
        val entry = Regex("\\{[^{}]*\"name\":\"Alimentación\"[^{}]*\\}").find(body)
            ?: error("No aparece la categoría sembrada «Alimentación»:\n$body")
        return entry.value.extract("id")
    }
}
