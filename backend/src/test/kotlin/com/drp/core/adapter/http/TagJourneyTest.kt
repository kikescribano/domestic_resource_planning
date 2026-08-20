package com.drp.core.adapter.http

import com.drp.test.SpringIntegrationTest
import com.drp.test.deleteJson
import com.drp.test.extract
import com.drp.test.getJson
import com.drp.test.patchJson
import com.drp.test.postJson
import com.drp.test.registerHousehold
import com.drp.test.seededCategory
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
 * Las etiquetas libres, por HTTP y contra PostgreSQL real.
 *
 * Lo que se comprueba aqui es lo que **decidio la forma**: que la etiqueta sea un
 * catalogo y no una columna de texto se justifico con tres cosas --renombrar,
 * deduplicar sin distinguir mayusculas ni acentos y autocompletar--, asi que las
 * tres tienen su prueba. Una decision cuyo motivo no se mide en ningun sitio es
 * una decision que puede dejar de ser cierta sin que nadie lo note.
 *
 * Y la cuarta: la **reviviscencia**, que es lo que sustituye al indice parcial de
 * `categories` y sin lo cual crear una etiqueta que existe retirada seria un 409
 * sobre una fila que el usuario no ve.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TagJourneyTest : SpringIntegrationTest() {

    @Autowired private lateinit var http: TestRestTemplate

    @Test
    @DisplayName("crear, renombrar, listar y retirar: el ciclo entero de una etiqueta")
    fun `el recorrido del catalogo de etiquetas`() {
        val household = http.registerHousehold()

        val created = http.postJson(TAGS, """{"name":"Camping"}""", household.accessToken)
        created.statusCode.shouldBe(HttpStatus.CREATED)
        val tagId = created.body!!.extract("id")
        created.body!!.shouldContain("\"createdBy\":\"${household.memberId}\"")

        val renamed = http.patchJson("$TAGS/$tagId", """{"name":"Acampada"}""", household.accessToken)
        renamed.statusCode.shouldBe(HttpStatus.OK)
        renamed.body!!.shouldContain("Acampada")

        // Un hogar nace **sin ninguna** etiqueta, al contrario que con las
        // categorias: no hay vocabulario por omision que acertar.
        val listed = http.getJson(TAGS, household.accessToken)
        listed.body!!.shouldContain("\"total\":1")

        http.deleteJson("$TAGS/$tagId", household.accessToken).statusCode.shouldBe(HttpStatus.NO_CONTENT)
        http.getJson(TAGS, household.accessToken).body!!.shouldNotContain("Acampada")
        http.getJson("$TAGS?includeRetired=true", household.accessToken).body!!.shouldContain("Acampada")
    }

    @Test
    @DisplayName("renombrar no toca a los assets que la llevan, que es por lo que es un catalogo")
    fun `renombrar alcanza a todo lo etiquetado de una vez`() {
        val household = http.registerHousehold()
        val category = http.seededCategory(household.accessToken, "Herramientas")
        val tag = http.postJson(TAGS, """{"name":"Camping"}""", household.accessToken).body!!.extract("id")

        val first = http.createAsset(household.accessToken, "Hornillo", category, listOf(tag))
        val second = http.createAsset(household.accessToken, "Saco", category, listOf(tag))

        http.patchJson("$TAGS/$tag", """{"name":"Acampada"}""", household.accessToken)
            .statusCode.shouldBe(HttpStatus.OK)

        // Con una columna de texto habria que haber reescrito los dos assets.
        // Aqui no se ha tocado ninguno y los dos dicen ya el nombre nuevo.
        http.getJson("$ASSETS/$first", household.accessToken).body!!.shouldContain("Acampada")
        http.getJson("$ASSETS/$second", household.accessToken).body!!.shouldContain("Acampada")
    }

    @Test
    @DisplayName("no distingue mayusculas ni acentos, ni al buscar ni al chocar")
    fun `deduplica normalizando`() {
        val household = http.registerHousehold()

        val first = http.postJson(TAGS, """{"name":"Decoración"}""", household.accessToken)
        first.statusCode.shouldBe(HttpStatus.CREATED)

        // «decoracion» es la misma etiqueta escrita deprisa en un movil, no una
        // segunda: responde 200 con la que ya habia, no 201 con otra.
        val again = http.postJson(TAGS, """{"name":"decoracion"}""", household.accessToken)
        again.statusCode.shouldBe(HttpStatus.OK)
        again.body!!.extract("id").shouldBe(first.body!!.extract("id"))
        http.getJson(TAGS, household.accessToken).body!!.shouldContain("\"total\":1")

        // Y el autocompletado busca igual: sin acentos y sin mayusculas.
        http.getJson("$TAGS?q=DECORA", household.accessToken).body!!.shouldContain("Decoración")
        http.getJson("$TAGS?q=oracion", household.accessToken).body!!.shouldContain("Decoración")
        http.getJson("$TAGS?q=camping", household.accessToken).body!!.shouldContain("\"total\":0")
    }

    @Test
    @DisplayName("renombrar al nombre de otra da 409 con su codigo, no un 500 por violacion de restriccion")
    fun `rechaza el nombre duplicado al renombrar`() {
        val household = http.registerHousehold()

        http.postJson(TAGS, """{"name":"Camping"}""", household.accessToken)
        val other = http.postJson(TAGS, """{"name":"Herencia"}""", household.accessToken).body!!.extract("id")

        val clash = http.patchJson("$TAGS/$other", """{"name":"camping"}""", household.accessToken)
        clash.statusCode.shouldBe(HttpStatus.CONFLICT)
        clash.body!!.shouldContain("TAG_DUPLICATE")
    }

    @Test
    @DisplayName("crear una que existe retirada la revive, en vez de chocar con un indice que nadie ve")
    fun `revivir en lugar de chocar`() {
        val household = http.registerHousehold()

        val tag = http.postJson(TAGS, """{"name":"Camping"}""", household.accessToken).body!!.extract("id")
        http.deleteJson("$TAGS/$tag", household.accessToken).statusCode.shouldBe(HttpStatus.NO_CONTENT)

        val revived = http.postJson(TAGS, """{"name":"Camping"}""", household.accessToken)
        // 200 y no 201: es la misma fila, con el mismo identificador y con los
        // assets que la llevaban todavia puestos.
        revived.statusCode.shouldBe(HttpStatus.OK)
        revived.body!!.extract("id").shouldBe(tag)
        revived.body!!.shouldContain("\"retiredAt\":null")
        http.getJson(TAGS, household.accessToken).body!!.shouldContain("\"total\":1")
    }

    @Test
    @DisplayName("una etiqueta retirada no se puede poner en un asset")
    fun `no se etiqueta con una retirada`() {
        val household = http.registerHousehold()
        val category = http.seededCategory(household.accessToken, "Herramientas")
        val tag = http.postJson(TAGS, """{"name":"Camping"}""", household.accessToken).body!!.extract("id")
        http.deleteJson("$TAGS/$tag", household.accessToken)

        val rejected = http.postJson(
            ASSETS,
            """{"name":"Hornillo","type":"DURABLE","categoryId":"$category","tagIds":["$tag"]}""",
            household.accessToken,
        )
        rejected.statusCode.shouldBe(HttpStatus.NOT_FOUND)
    }

    @Test
    @DisplayName("las etiquetas de un asset son absolutas: la lista que llega es la que queda")
    fun `el patch sustituye y la lista vacia desetiqueta`() {
        val household = http.registerHousehold()
        val category = http.seededCategory(household.accessToken, "Herramientas")
        val camping = http.postJson(TAGS, """{"name":"Camping"}""", household.accessToken).body!!.extract("id")
        val herencia = http.postJson(TAGS, """{"name":"Herencia"}""", household.accessToken).body!!.extract("id")

        val asset = http.createAsset(household.accessToken, "Hornillo", category, listOf(camping))

        // Sustituye, no suma.
        val replaced = http.patchJson("$ASSETS/$asset", """{"tagIds":["$herencia"]}""", household.accessToken)
        replaced.statusCode.shouldBe(HttpStatus.OK)
        replaced.body!!.shouldContain("Herencia")
        replaced.body!!.shouldNotContain("Camping")

        // No mencionarlas no las toca, que es la diferencia entre ausente y vacio.
        val untouched = http.patchJson("$ASSETS/$asset", """{"notes":"sonda"}""", household.accessToken)
        untouched.body!!.shouldContain("Herencia")

        // Y la lista vacia las quita todas, que es como se desetiqueta.
        val cleared = http.patchJson("$ASSETS/$asset", """{"tagIds":[]}""", household.accessToken)
        cleared.body!!.shouldContain("\"tags\":[]")
    }

    @Test
    @DisplayName("el filtro por etiqueta devuelve lo que la lleva, y una sola vez aunque lleve varias")
    fun `el listado filtra por etiqueta`() {
        val household = http.registerHousehold()
        val category = http.seededCategory(household.accessToken, "Herramientas")
        val camping = http.postJson(TAGS, """{"name":"Camping"}""", household.accessToken).body!!.extract("id")
        val herencia = http.postJson(TAGS, """{"name":"Herencia"}""", household.accessToken).body!!.extract("id")

        http.createAsset(household.accessToken, "Hornillo", category, listOf(camping, herencia))
        http.createAsset(household.accessToken, "Taladro", category, emptyList())

        val filtered = http.getJson("$ASSETS?tagId=$camping", household.accessToken)
        filtered.statusCode.shouldBe(HttpStatus.OK)
        // Uno y no dos: con un `JOIN` en vez de un `EXISTS`, el asset con dos
        // etiquetas saldria repetido y el total mentiria.
        filtered.body!!.shouldContain("\"total\":1")
        filtered.body!!.shouldContain("Hornillo")
        filtered.body!!.shouldNotContain("Taladro")

        // Sin filtro salen los dos, y el de dos etiquetas sigue saliendo una vez.
        http.getJson(ASSETS, household.accessToken).body!!.shouldContain("\"total\":2")
    }

    @Test
    @DisplayName("un hogar nuevo trae sus cinco categorias con icono y color, todos distintos")
    fun `las categorias sembradas nacen con cara`() {
        val household = http.registerHousehold()

        val listed = http.getJson("/api/v1/categories", household.accessToken).body!!
        listed.shouldContain("\"icon\":\"SOFA\"")
        listed.shouldContain("\"icon\":\"UTENSILS\"")
        listed.shouldContain("\"color\":\"PLUM\"")
        listed.shouldContain("\"color\":\"MOSS\"")

        // Sin repetir color entre las cinco: sembrarlas todas del mismo tono
        // ensenaria que el atributo existe y no para que sirve.
        val colors = Regex("\"color\":\"([A-Z]+)\"").findAll(listed).map { it.groupValues[1] }.toList()
        colors.size.shouldBe(5)
        colors.toSet().size.shouldBe(5)
    }

    @Test
    @DisplayName("la cara de la categoria se elige, se corrige y se quita")
    fun `el icono y el color se editan`() {
        val household = http.registerHousehold()

        val created = http.postJson(
            "/api/v1/categories",
            """{"name":"Escalada","icon":"BIKE","color":"TEAL"}""",
            household.accessToken,
        )
        created.statusCode.shouldBe(HttpStatus.CREATED)
        val id = created.body!!.extract("id")
        created.body!!.shouldContain("\"icon\":\"BIKE\"")

        val corrected = http.patchJson(
            "/api/v1/categories/$id",
            """{"name":"Escalada","icon":"TOOL","color":"ROSE"}""",
            household.accessToken,
        )
        corrected.body!!.shouldContain("\"icon\":\"TOOL\"")
        corrected.body!!.shouldContain("\"color\":\"ROSE\"")

        // Ausente es quitar, no conservar: el cuerpo es la categoria entera.
        val cleared = http.patchJson("/api/v1/categories/$id", """{"name":"Escalada"}""", household.accessToken)
        cleared.body!!.shouldContain("\"icon\":null")
        cleared.body!!.shouldContain("\"color\":null")
    }

    @Test
    @DisplayName("un color fuera del juego cerrado es un 400, no una fila con un valor inventado")
    fun `el juego de colores es cerrado`() {
        val household = http.registerHousehold()

        val rejected = http.postJson(
            "/api/v1/categories",
            """{"name":"Escalada","color":"#ff0000"}""",
            household.accessToken,
        )
        rejected.statusCode.shouldBe(HttpStatus.BAD_REQUEST)
    }

    @Test
    @DisplayName("el asset resuelve la cara de su categoria, tambien cuando la hereda de su articulo")
    fun `la cara viaja resuelta en el asset`() {
        val household = http.registerHousehold()
        val token = household.accessToken

        val category = http.postJson(
            "/api/v1/categories",
            """{"name":"Escalada","icon":"BIKE","color":"TEAL"}""",
            token,
        ).body!!.extract("id")

        val own = http.createAsset(token, "Arnés", category, emptyList())
        http.getJson("$ASSETS/$own", token).body!!.shouldContain("\"categoryIcon\":\"BIKE\"")

        // Y por el camino largo: el asset no guarda su categoria porque la hereda
        // del articulo, asi que la cara tiene que resolverse dos saltos mas alla.
        val article = http.postJson(
            "/api/v1/articles",
            """{"name":"Cuerda","categoryId":"$category","unit":"METER"}""",
            token,
        ).body!!.extract("id")
        val inherited = http.postJson(
            ASSETS,
            """{"type":"DURABLE","articleId":"$article"}""",
            token,
        ).body!!.extract("id")

        val read = http.getJson("$ASSETS/$inherited", token).body!!
        read.shouldContain("\"categoryIcon\":\"BIKE\"")
        read.shouldContain("\"categoryColor\":\"TEAL\"")
    }

    private fun TestRestTemplate.createAsset(
        token: String,
        name: String,
        categoryId: String,
        tagIds: List<String>,
    ): String {
        val tags = tagIds.joinToString(",") { "\"$it\"" }
        val response = postJson(
            ASSETS,
            """{"name":"$name","type":"DURABLE","categoryId":"$categoryId","tagIds":[$tags]}""",
            token,
        )
        check(response.statusCode == HttpStatus.CREATED) {
            "No se pudo crear el asset: ${response.statusCode} ${response.body}"
        }
        return response.body!!.extract("id")
    }

    private companion object {
        const val TAGS = "/api/v1/tags"
        const val ASSETS = "/api/v1/assets"
    }
}
