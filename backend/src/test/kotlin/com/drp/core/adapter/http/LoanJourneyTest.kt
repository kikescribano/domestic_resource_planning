package com.drp.core.adapter.http

import com.drp.test.DrpMailpit
import com.drp.test.DrpPostgres
import com.drp.test.SpringIntegrationTest
import com.drp.test.extract
import com.drp.test.getJson
import com.drp.test.postJson
import com.drp.test.registerHousehold
import com.drp.test.seededCategory
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
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
 * El ciclo de vida del prestamo, de punta a punta.
 *
 * Va por HTTP contra PostgreSQL y Mailpit reales, y **lee el enlace del correo
 * como lo leeria una persona**: es la unica forma de comprobar que el token que
 * se emite es el que llega y que el que llega es el que abre la pantalla.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LoanJourneyTest : SpringIntegrationTest() {

    @Autowired private lateinit var http: TestRestTemplate

    @Test
    @DisplayName("prestar pone el asset en LENT y manda el enlace al externo")
    fun `iniciar un prestamo a un externo`() {
        val home = http.registerHousehold()
        val assetId = http.createDurable(home.accessToken, "Taladro")
        val vecino = "vecino-${UUID.randomUUID()}@example.test"

        val created = http.postJson(
            "/api/v1/loans",
            """
            {"assetId":"$assetId",
             "lender":{"userId":"${home.memberId}"},
             "borrower":{"external":{"name":"Vecino del 3.º","email":"$vecino"}},
             "notes":"Con la broca de widia"}
            """.trimIndent(),
            home.accessToken,
        )

        created.statusCode.shouldBe(HttpStatus.CREATED)
        created.body!!.extract("status").shouldBe("ACTIVE")
        created.body!!.extract("assetName").shouldBe("Taladro")

        // El asset acompana: pasa a LENT mientras el prestamo este abierto.
        http.getJson("/api/v1/assets/$assetId", home.accessToken).body!!
            .extract("status").shouldBe("LENT")

        // Y el enlace llega de verdad, con el asunto del extremo que le toca.
        val correo = DrpMailpit.instance.awaitMessageTo(vecino)
        correo.subject.shouldContain("Te han prestado")
        correo.body.shouldContain("Taladro")

        // Lo que el correo NO puede decir: ni de que hogar viene ni quien presta.
        // La pantalla de destino no puede mostrarlo, asi que el correo tampoco.
        correo.body.shouldNotContain(home.email)
    }

    @Test
    @DisplayName("un prestamo entre dos miembros del hogar no emite ningun token")
    fun `sin externos no hay credencial nueva`() {
        val home = http.registerHousehold()
        val assetId = http.createDurable(home.accessToken, "Escalera")

        val created = http.postJson(
            "/api/v1/loans",
            """
            {"assetId":"$assetId",
             "lender":{"userId":"${home.memberId}"},
             "borrower":{"userId":"${home.memberId}"}}
            """.trimIndent(),
            home.accessToken,
        )
        created.statusCode.shouldBe(HttpStatus.CREATED)

        val loanId = created.body!!.extract("id")
        countTokensOf(loanId).shouldBe(0)
    }

    @Test
    @DisplayName("un asset ya prestado no se puede volver a prestar, ni estando OVERDUE")
    fun `el segundo prestamo se rechaza`() {
        val home = http.registerHousehold()
        val assetId = http.createDurable(home.accessToken, "Carretilla")
        http.startSimpleLoan(home.accessToken, home.memberId, assetId).statusCode.shouldBe(HttpStatus.CREATED)

        val second = http.startSimpleLoan(home.accessToken, home.memberId, assetId)

        second.statusCode.shouldBe(HttpStatus.CONFLICT)
        second.body!!.extract("code").shouldBe("LOAN_ASSET_ALREADY_LENT")

        // Y vencer no libera nada: el hueco sigue ocupado.
        DrpPostgres.instance.ownerConnection().use { connection ->
            connection.prepareStatement("UPDATE loans SET status = 'OVERDUE' WHERE asset_id = ?::uuid").use {
                it.setString(1, assetId)
                it.executeUpdate()
            }
        }

        val third = http.startSimpleLoan(home.accessToken, home.memberId, assetId)
        third.statusCode.shouldBe(HttpStatus.CONFLICT)
        third.body!!.extract("code").shouldBe("LOAN_ASSET_ALREADY_LENT")
    }

    @Test
    @DisplayName("un consumible no se presta: se consume o se entrega")
    fun `solo se presta un durable`() {
        val home = http.registerHousehold()
        val categoryId = http.seededCategory(home.accessToken, "Alimentación")

        val intake = http.postJson(
            "/api/v1/assets/intake",
            """
            {"article":{"name":"Azúcar ${UUID.randomUUID()}","categoryId":"$categoryId","unit":"GRAM"},
             "ownerId":"${home.memberId}","quantity":1000}
            """.trimIndent(),
            home.accessToken,
        )
        intake.statusCode.shouldBe(HttpStatus.CREATED)
        val consumableId = intake.body!!.extract("id")

        val rejected = http.startSimpleLoan(home.accessToken, home.memberId, consumableId)

        rejected.statusCode.shouldBe(HttpStatus.CONFLICT)
        rejected.body!!.extract("code").shouldBe("LOAN_ASSET_NOT_DURABLE")
    }

    @Test
    @DisplayName("un externo sin ningun canal se rechaza: no habria por donde mandarle el enlace")
    fun `el externo necesita un canal`() {
        val home = http.registerHousehold()
        val assetId = http.createDurable(home.accessToken, "Sierra")

        val rejected = http.postJson(
            "/api/v1/loans",
            """
            {"assetId":"$assetId",
             "lender":{"userId":"${home.memberId}"},
             "borrower":{"external":{"name":"Alguien sin contacto"}}}
            """.trimIndent(),
            home.accessToken,
        )

        rejected.statusCode.shouldBe(HttpStatus.BAD_REQUEST)
        rejected.body!!.extract("code").shouldBe("VALIDATION_ERROR")
    }

    @Test
    @DisplayName("los dos extremos a la vez, o ninguno, se rechazan con el campo senalado")
    fun `cada extremo es exactamente uno de los dos`() {
        val home = http.registerHousehold()
        val assetId = http.createDurable(home.accessToken, "Martillo")

        val both = http.postJson(
            "/api/v1/loans",
            """
            {"assetId":"$assetId",
             "lender":{"userId":"${home.memberId}","external":{"name":"Los dos","email":"a@example.test"}},
             "borrower":{"userId":"${home.memberId}"}}
            """.trimIndent(),
            home.accessToken,
        )
        both.statusCode.shouldBe(HttpStatus.BAD_REQUEST)

        val neither = http.postJson(
            "/api/v1/loans",
            """
            {"assetId":"$assetId","lender":{},"borrower":{"userId":"${home.memberId}"}}
            """.trimIndent(),
            home.accessToken,
        )
        neither.statusCode.shouldBe(HttpStatus.BAD_REQUEST)
    }

    @Test
    @DisplayName("el jsonb del externo guarda las claves que declara el contrato")
    fun `la forma del json guardado es la del contrato`() {
        // La misma trampa que cazo el Hito 2 en `locations`: renombrar un campo de
        // ExternalParty cambiaria en silencio las claves guardadas, y aqui ademas
        // romperia el CHECK de la tabla, que exige `name` y un canal. Escribir y
        // leer con el mismo codigo no lo detecta, asi que se le pregunta a
        // PostgreSQL.
        val home = http.registerHousehold()
        val assetId = http.createDurable(home.accessToken, "Desbrozadora")
        val created = http.postJson(
            "/api/v1/loans",
            """
            {"assetId":"$assetId",
             "lender":{"userId":"${home.memberId}"},
             "borrower":{"external":{"name":"Vecina del 1.º","email":"vecina@example.test","phone":"600000000"}}}
            """.trimIndent(),
            home.accessToken,
        )
        created.statusCode.shouldBe(HttpStatus.CREATED)
        val loanId = created.body!!.extract("id")

        DrpPostgres.instance.ownerConnection().use { connection ->
            connection.prepareStatement(
                "SELECT jsonb_object_keys(borrower_external) FROM loans WHERE id = ?::uuid",
            ).use { statement ->
                statement.setString(1, loanId)
                statement.executeQuery().use { rows ->
                    val keys = mutableListOf<String>()
                    while (rows.next()) keys += rows.getString(1)
                    keys.shouldContainExactlyInAnyOrder("name", "email", "phone")
                }
            }
        }
    }

    @Test
    @DisplayName("un asset de otro hogar responde 404 y no un 500")
    fun `una referencia ajena no revienta`() {
        val alien = http.registerHousehold()
        val alienAsset = http.createDurable(alien.accessToken, "Taladro ajeno")
        val home = http.registerHousehold()

        // La comprobacion de una clave ajena NO pasa por RLS, asi que sin
        // resolver el asset contra su repositorio esto habria llegado a la
        // insercion y habria salido un 500.
        val response = http.startSimpleLoan(home.accessToken, home.memberId, alienAsset)

        response.statusCode.shouldBe(HttpStatus.NOT_FOUND)
    }

    @Test
    @DisplayName("el listado agrupa ACTIVE y OVERDUE con open=true")
    fun `el listado responde que hay fuera de casa`() {
        val home = http.registerHousehold()
        val prestado = http.createDurable(home.accessToken, "Gato hidráulico")
        val encasa = http.createDurable(home.accessToken, "Llave inglesa")
        http.startSimpleLoan(home.accessToken, home.memberId, prestado).statusCode.shouldBe(HttpStatus.CREATED)

        val open = http.getJson("/api/v1/loans?open=true", home.accessToken).body!!
        open.shouldContain(prestado)
        open.shouldNotContain(encasa)

        val byAsset = http.getJson("/api/v1/loans?assetId=$prestado", home.accessToken).body!!
        byAsset.shouldContain(prestado)
    }

    @Test
    @DisplayName("la condicion se anota en los dos momentos, y la de vuelta solo al devolver")
    fun `los dos momentos del prestamo`() {
        val home = http.registerHousehold()
        val assetId = http.createDurable(home.accessToken, "Cortacésped")

        val created = http.postJson(
            "/api/v1/loans",
            """{"assetId":"$assetId",
             "lender":{"userId":"${home.memberId}"},
             "borrower":{"userId":"${home.memberId}"},
             "conditionAtStart":"GOOD"}""",
            home.accessToken,
        )
        created.statusCode.shouldBe(HttpStatus.CREATED)
        created.body!!.shouldContain("\"conditionAtStart\":\"GOOD\"")
        // La de vuelta todavia no existe, y eso es una regla y no un descuido: la
        // cosa acaba de salir de casa.
        created.body!!.shouldContain("\"conditionOnReturn\":null")

        val loanId = created.body!!.extract("id")

        // No hay ninguna operacion que la escriba antes de la devolucion: el
        // `PATCH` de assets no la toca y el prestamo no tiene `PATCH`.
        val returned = http.postJson(
            "/api/v1/loans/$loanId/return",
            """{"conditionOnReturn":"DAMAGED"}""",
            home.accessToken,
        )
        returned.statusCode.shouldBe(HttpStatus.OK)
        returned.body!!.shouldContain("\"conditionOnReturn\":\"DAMAGED\"")
        // Y la pareja se lee junta, que es de donde sale «volvio peor».
        returned.body!!.shouldContain("\"conditionAtStart\":\"GOOD\"")

        // **El asset no se entera**: lo que se afirma al devolver es del
        // prestamo, y el estado de conservacion de la ficha lo corrige el hogar.
        http.getJson("/api/v1/assets/$assetId", home.accessToken).body!!
            .shouldContain("\"condition\":null")

        // Y se releen las dos: se guardaron, no se quedaron en la respuesta.
        val read = http.getJson("/api/v1/loans/$loanId", home.accessToken).body!!
        read.shouldContain("\"conditionAtStart\":\"GOOD\"")
        read.shouldContain("\"conditionOnReturn\":\"DAMAGED\"")
    }

    @Test
    @DisplayName("confirmar sin decir nada sigue valiendo: el cuerpo entero es opcional")
    fun `la devolucion sin cuerpo sigue siendo una peticion valida`() {
        val home = http.registerHousehold()
        val assetId = http.createDurable(home.accessToken, "Manguera")
        val loanId = http.startSimpleLoan(home.accessToken, home.memberId, assetId).body!!.extract("id")

        // Es como llamaba el cliente antes de que este campo existiera, y tiene
        // que seguir funcionando: ausente y vacio significan lo mismo.
        val returned = http.postJson("/api/v1/loans/$loanId/return", "", home.accessToken)

        returned.statusCode.shouldBe(HttpStatus.OK)
        returned.body!!.shouldContain("\"conditionOnReturn\":null")
    }

    @Test
    @DisplayName("una condicion inventada se rechaza con 400 y no la guarda nadie")
    fun `la escala del prestamo es cerrada`() {
        val home = http.registerHousehold()
        val assetId = http.createDurable(home.accessToken, "Carretilla de mano")

        http.postJson(
            "/api/v1/loans",
            """{"assetId":"$assetId",
             "lender":{"userId":"${home.memberId}"},
             "borrower":{"userId":"${home.memberId}"},
             "conditionAtStart":"REGULINCHI"}""",
            home.accessToken,
        ).statusCode.shouldBe(HttpStatus.BAD_REQUEST)

        val loanId = http.startSimpleLoan(home.accessToken, home.memberId, assetId).body!!.extract("id")
        http.postJson(
            "/api/v1/loans/$loanId/return",
            """{"conditionOnReturn":"REGULINCHI"}""",
            home.accessToken,
        ).statusCode.shouldBe(HttpStatus.BAD_REQUEST)

        // Y el prestamo sigue abierto: el 400 no dejo la devolucion a medias.
        http.getJson("/api/v1/loans/$loanId", home.accessToken).body!!
            .shouldContain("\"status\":\"ACTIVE\"")
    }

    private fun countTokensOf(loanId: String): Int =
        DrpPostgres.instance.ownerConnection().use { connection ->
            connection.prepareStatement(
                "SELECT count(*) FROM loan_access_tokens WHERE loan_id = ?::uuid",
            ).use { statement ->
                statement.setString(1, loanId)
                statement.executeQuery().use { rows -> if (rows.next()) rows.getInt(1) else 0 }
            }
        }
}

/** Un `DURABLE` con nombre y categoria propios, que es lo minimo que se puede prestar. */
internal fun TestRestTemplate.createDurable(accessToken: String, name: String): String {
    val categoryId = seededCategory(accessToken, "Herramientas")
    val created = postJson(
        "/api/v1/assets",
        """{"name":"$name","type":"DURABLE","categoryId":"$categoryId"}""",
        accessToken,
    )
    check(created.statusCode == HttpStatus.CREATED) { "No se pudo crear el asset: ${created.body}" }
    return created.body!!.extract("id")
}

/** El prestamo mas simple posible: de un miembro a si mismo, sin externos ni plazo. */
internal fun TestRestTemplate.startSimpleLoan(accessToken: String, memberId: String, assetId: String) =
    postJson(
        "/api/v1/loans",
        """{"assetId":"$assetId","lender":{"userId":"$memberId"},"borrower":{"userId":"$memberId"}}""",
        accessToken,
    )
