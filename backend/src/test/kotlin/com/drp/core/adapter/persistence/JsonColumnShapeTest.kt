package com.drp.core.adapter.persistence

import com.drp.test.DrpPostgres
import com.drp.test.SpringIntegrationTest
import com.drp.test.extract
import com.drp.test.postJson
import com.drp.test.queryOne
import com.drp.test.registerHousehold
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus

/**
 * Las claves que acaban **dentro** de las columnas `jsonb`, leidas de PostgreSQL.
 *
 * Existe por una contrapartida concreta de mapear los objetos de valor del
 * dominio con `@JdbcTypeCode(SqlTypes.JSON)`: renombrar un campo de `Capacity` o
 * de `EnvironmentalConditions` cambia en silencio las claves del JSON guardado.
 * Compila, pasa las pruebas de recorrido --que escriben y leen con el mismo
 * codigo, asi que se equivocan igual en los dos sentidos-- y deja ilegibles las
 * filas que ya estuvieran escritas.
 *
 * La unica forma de cazarlo es preguntarle a la base de datos que claves hay de
 * verdad, y compararlas con las que declara `openapi.yaml`.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JsonColumnShapeTest : SpringIntegrationTest() {

    @Autowired private lateinit var http: TestRestTemplate

    @Test
    @DisplayName("las claves guardadas en los dos jsonb son las que declara el contrato")
    fun `el json guardado tiene la forma del contrato`() {
        val home = http.registerHousehold()

        val created = http.postJson(
            "/api/v1/locations",
            """
            {"name":"Nevera del garaje","type":"FURNITURE",
             "capacity":{"type":"VOLUME","max":180.5,"unit":"l"},
             "environmentalConditions":{
               "minTemperature":2,"maxTemperature":6,
               "minHumidity":40,"maxHumidity":60,"lightExposure":"DARKNESS"}}
            """.trimIndent(),
            home.accessToken,
        )
        created.statusCode.shouldBe(HttpStatus.CREATED)
        val locationId = created.body!!.extract("id")

        // Se lee con el propietario del esquema porque esta conexion no tiene
        // fijado el `app.household_id`: aqui no se comprueba aislamiento --de eso
        // van otras pruebas-- sino la forma de lo guardado.
        DrpPostgres.instance.ownerConnection().use { connection ->
            val capacityKeys = connection.jsonKeys("capacity", locationId)
            capacityKeys.shouldContainExactlyInAnyOrder("type", "max", "unit")

            val conditionKeys = connection.jsonKeys("environmental_conditions", locationId)
            conditionKeys.shouldContainExactlyInAnyOrder(
                "minTemperature",
                "maxTemperature",
                "minHumidity",
                "maxHumidity",
                "lightExposure",
            )

            // Y los valores llegan enteros, no como texto ni redondeados: un
            // `max` que se guardase como cadena pasaria la comprobacion de claves
            // y rompería cualquier suma posterior.
            val stored = connection.queryOne(
                "SELECT capacity ->> 'unit', (capacity ->> 'max')::numeric FROM locations WHERE id = ?::uuid",
                locationId,
            ) { it.getString(1) to it.getBigDecimal(2) }

            stored!!.first.shouldBe("l")
            stored.second.compareTo(java.math.BigDecimal("180.5")).shouldBe(0)
        }
    }

    private fun java.sql.Connection.jsonKeys(column: String, locationId: String): List<String> {
        prepareStatement("SELECT jsonb_object_keys($column) FROM locations WHERE id = ?::uuid").use { statement ->
            statement.setString(1, locationId)
            statement.executeQuery().use { rows ->
                val keys = mutableListOf<String>()
                while (rows.next()) keys += rows.getString(1)
                return keys
            }
        }
    }
}
