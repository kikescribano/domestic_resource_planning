package com.drp.core.application.usecase

import com.drp.platform.event.DomainEvent
import com.drp.platform.event.EventOutbox
import com.drp.platform.schedule.DailySweep
import com.drp.test.DrpPostgres
import com.drp.test.SpringIntegrationTest
import com.drp.test.TestHousehold
import com.drp.test.count
import com.drp.test.deleteJson
import com.drp.test.execute
import com.drp.test.extract
import com.drp.test.imageBytes
import com.drp.test.patchJson
import com.drp.test.postJson
import com.drp.test.queryAll
import com.drp.test.registerHousehold
import com.drp.test.seededCategory
import com.drp.test.today
import com.drp.test.uploadFile
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * La purga de un hogar que pidio la baja (ADR-012).
 *
 * **La purga se demuestra aqui y no en el recorrido vertical**, porque aqui se
 * puede mover el reloj: en un navegador habria que esperar treinta dias.
 *
 * Lo que se comprueba es lo que la ADR promete, y son tres cosas distintas:
 *
 * 1. **Que no queda ni una fila.** Y no confiando en las claves ajenas sino
 *    **tabla por tabla**, con el hogar lleno y los cuatro modulos encendidos. La
 *    cascada esta probada desde la Fase 1, pero **con hogares vacios**: lo unico
 *    demostrado hasta hoy es que funciona donde no hay nada que arrastrar. Una
 *    tabla que se quedara fuera **no daria ningun error**: dejaria filas de un
 *    hogar que pidio marcharse, en silencio y para siempre.
 * 2. **Que no queda un solo byte**, con ficheros de verdad en disco. Es la unica
 *    forma de afirmarlo, porque el disco esta fuera de PostgreSQL y ninguna
 *    consulta lo ve.
 * 3. **Que la frontera con `identities` se respeta en los dos sentidos**: la
 *    persona que se queda sin ninguna pertenencia desaparece, y la que sigue
 *    constando en otro hogar **no**.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PurgeClosedHouseholdsTest : SpringIntegrationTest() {

    @Autowired private lateinit var sweep: DailySweep
    @Autowired private lateinit var http: TestRestTemplate
    @Autowired private lateinit var outbox: EventOutbox

    private val postgres = DrpPostgres.instance

    @Test
    @DisplayName("un hogar lleno y con los cuatro modulos encendidos no deja ni una fila")
    fun `la cascada se lo lleva todo`() {
        val household = http.registerHousehold()
        val id = UUID.fromString(household.householdId)
        fillHousehold(household)
        requestClosure(household)

        // El retrato de antes: **cada** tabla con algo dentro. Sin esta
        // comprobacion, la de despues no demostraria nada --una tabla vacia sigue
        // vacia se purgue o no-- y una tabla que la siembra no llegara a tocar
        // pasaria por purgada.
        rowsPerTable(id).filterValues { it == 0 }.keys.shouldBeEmpty()

        expireClosure(id)
        sweep.run()

        rowsPerTable(id).filterValues { it > 0 }.keys.shouldBeEmpty()
    }

    @Test
    @DisplayName("no deja un solo byte en disco, ni original ni miniatura")
    fun `se lleva los ficheros`() {
        val household = http.registerHousehold()
        val id = UUID.fromString(household.householdId)

        http.uploadFile(household.accessToken, imageBytes("png"), "foto.png", "image/png")
            .statusCode.shouldBe(HttpStatus.CREATED)

        // Se comprueba que **habia** algo antes de afirmar que no queda nada: sin
        // esto, la prueba pasaria igual con la subida rota.
        filesUnder("original/$id").shouldBeGreaterThan(0)
        filesUnder("thumbnail/$id").shouldBeGreaterThan(0)

        requestClosure(household)
        expireClosure(id)
        sweep.run()

        // Los dos prefijos, que son **dos** y no uno: el troceado empieza por el
        // tipo y el hogar viene despues, asi que no existe «el directorio del
        // hogar». Quien borre solo `original/` deja las miniaturas puestas y
        // ningun error lo delata.
        filesUnder("original/$id").shouldBe(0)
        filesUnder("thumbnail/$id").shouldBe(0)
    }

    @Test
    @DisplayName("se lleva el avatar de quien se queda sin ninguna pertenencia")
    fun `se lleva el avatar`() {
        val household = http.registerHousehold()
        val id = UUID.fromString(household.householdId)

        http.uploadFile(
            household.accessToken,
            imageBytes("png"),
            "cara.png",
            "image/png",
            path = "/api/v1/users/me/avatar",
            method = HttpMethod.PUT,
        ).statusCode.shouldBe(HttpStatus.NO_CONTENT)

        val avatar = avatarPathOf(UUID.fromString(household.identityId))
        Files.exists(avatar).shouldBeTrue()

        requestClosure(household)
        expireClosure(id)
        sweep.run()

        // Vive **fuera** de los dos arboles del hogar --una identidad no
        // pertenece a ninguno-- asi que borrar por prefijo no lo alcanza: se
        // borra aparte, y solo de quien se queda sin nada.
        Files.exists(avatar).shouldBeFalse()
    }

    @Test
    @DisplayName("la identidad que se queda sin ninguna pertenencia se borra de verdad")
    fun `la identidad huerfana desaparece`() {
        val household = http.registerHousehold()
        val id = UUID.fromString(household.householdId)

        requestClosure(household)
        expireClosure(id)
        sweep.run()

        postgres.ownerConnection().use { owner ->
            owner.count("SELECT count(*) FROM households WHERE id = ?", id).shouldBe(0)
            // Borrado real y no baja logica: conservarla retendria nombre, correo
            // y telefono de alguien que ya no puede entrar en ningun sitio, y
            // **no liberaria su correo** --el indice unico dejo de ser parcial
            // por baja-- asi que esa persona no podria volver a registrarse
            // nunca. Ver la ADR-012.
            owner.count(
                "SELECT count(*) FROM identities WHERE lower(email) = ?",
                household.email.lowercase(),
            ).shouldBe(0)
        }
    }

    @Test
    @DisplayName("la identidad que aun consta en otro hogar sobrevive, aunque sea de baja")
    fun `la identidad con otro hogar sobrevive`() {
        val doomed = http.registerHousehold()
        val survivor = http.registerHousehold()
        val doomedId = UUID.fromString(doomed.householdId)

        // Una pertenencia **dada de baja** en el hogar que sobrevive. Es el caso
        // que la resolucion acotada existe para atrapar: sin ella, esta persona
        // pareceria huerfana --no le queda ninguna pertenencia ACTIVA fuera del
        // hogar condenado-- y borrar su identidad arrastraria en cascada la fila
        // del hogar de al lado, con su historial apuntandola.
        postgres.ownerConnection().use { owner ->
            owner.execute(
                """
                INSERT INTO household_members (id, household_id, identity_id, role, deactivated_at)
                VALUES (?, ?, ?, 'HOUSEHOLD_MEMBER', now())
                """.trimIndent(),
                UUID.randomUUID(),
                UUID.fromString(survivor.householdId),
                UUID.fromString(doomed.identityId),
            )
        }

        requestClosure(doomed)
        expireClosure(doomedId)
        sweep.run()

        postgres.ownerConnection().use { owner ->
            owner.count("SELECT count(*) FROM households WHERE id = ?", doomedId).shouldBe(0)
            // La identidad sigue: le queda donde constar.
            owner.count(
                "SELECT count(*) FROM identities WHERE lower(email) = ?",
                doomed.email.lowercase(),
            ).shouldBe(1)
            // Y su rastro en el hogar de al lado, intacto.
            owner.count(
                "SELECT count(*) FROM household_members WHERE household_id = ? AND identity_id = ?",
                UUID.fromString(survivor.householdId),
                UUID.fromString(doomed.identityId),
            ).shouldBe(1)
        }
    }

    @Test
    @DisplayName("no toca un hogar cuya gracia todavia no ha vencido, y ese hogar funciona igual")
    fun `no purga antes de tiempo`() {
        val household = http.registerHousehold()
        val id = UUID.fromString(household.householdId)

        requestClosure(household)
        sweep.run()

        postgres.ownerConnection().use {
            it.count("SELECT count(*) FROM households WHERE id = ?", id).shouldBe(1)
        }

        // Y el hogar **funciona igual** durante la gracia, que es la mitad de la
        // decision que mas facil seria incumplir sin enterarse: nada de solo
        // lectura, porque eso castigaria a quien todavia puede cancelar.
        http.postJson(
            "/api/v1/categories",
            """{"name":"Durante la gracia"}""",
            household.accessToken,
        ).statusCode.shouldBe(HttpStatus.CREATED)
    }

    @Test
    @DisplayName("no toca un hogar que cancelo su baja, por vencida que estuviera")
    fun `no purga un hogar que cancelo`() {
        val household = http.registerHousehold()
        val id = UUID.fromString(household.householdId)

        requestClosure(household)
        expireClosure(id)
        http.deleteJson("/api/v1/households/current/closure", household.accessToken)
            .statusCode.shouldBe(HttpStatus.OK)

        sweep.run()

        postgres.ownerConnection().use {
            it.count("SELECT count(*) FROM households WHERE id = ?", id).shouldBe(1)
        }
    }

    @Test
    @DisplayName("es idempotente: pasarlo dos veces no cambia nada la segunda")
    fun `la purga es idempotente`() {
        val household = http.registerHousehold()
        val id = UUID.fromString(household.householdId)

        requestClosure(household)
        expireClosure(id)

        sweep.run()
        postgres.ownerConnection().use {
            it.count("SELECT count(*) FROM households WHERE id = ?", id).shouldBe(0)
        }

        // La segunda no encuentra nada y no revienta. Se mira por el estado y no
        // por lo que devuelve el barrido, que cuenta lo de toda la instalacion.
        sweep.run()
        postgres.ownerConnection().use {
            it.count("SELECT count(*) FROM households WHERE id = ?", id).shouldBe(0)
        }
    }

    @Test
    @DisplayName("purgar un hogar no toca ni una fila del de al lado")
    fun `la purga no cruza de hogar`() {
        val doomed = http.registerHousehold()
        val safe = http.registerHousehold()
        fillHousehold(safe)

        val doomedId = UUID.fromString(doomed.householdId)
        val safeId = UUID.fromString(safe.householdId)
        val before = rowsPerTable(safeId)

        requestClosure(doomed)
        expireClosure(doomedId)
        sweep.run()

        postgres.ownerConnection().use { owner ->
            owner.count("SELECT count(*) FROM households WHERE id = ?", doomedId).shouldBe(0)
        }
        // Tabla por tabla y con las mismas cuentas: es mas fuerte que comprobar
        // que el hogar sigue existiendo, que es lo que se rompe primero.
        rowsPerTable(safeId).shouldBe(before)
    }

    @Test
    @DisplayName("el usuario con el que corre la purga NO tiene BYPASSRLS")
    fun `la purga no se salta las politicas`() {
        // Es la condicion sin la cual nada de lo anterior demuestra nada: con
        // BYPASSRLS el proceso funcionaria igual y el aislamiento estaria
        // desactivado para la aplicacion entera.
        postgres.appConnection().use { app ->
            val privileged = app.count(
                "SELECT count(*) FROM pg_roles WHERE rolname = current_user AND (rolsuper OR rolbypassrls)",
            )
            (privileged > 0).shouldBeFalse()
        }
    }

    // -----------------------------------------------------------------------
    // Apoyo
    // -----------------------------------------------------------------------

    /**
     * Deja el hogar **con algo en cada una de las veintidos tablas**, y con los
     * cuatro modulos encendidos.
     *
     * Es la mitad cara de esta prueba y la que le da sentido. La unica que no se
     * siembra aqui es `household_notices`, que la deja la propia solicitud de
     * baja: es el aviso que `RequestHouseholdClosure` levanta.
     */
    private fun fillHousehold(household: TestHousehold) {
        val token = household.accessToken
        val tools = http.seededCategory(token, "Herramientas")
        val food = http.seededCategory(token, "Alimentación")

        val room = http.postJson("/api/v1/locations", """{"name":"Trastero","type":"ROOM"}""", token)
            .body!!.extract("id")

        val drill = http.postJson(
            "/api/v1/assets",
            """{"name":"Taladro","type":"DURABLE","categoryId":"$tools"}""",
            token,
        ).body!!.extract("id")

        val article = http.postJson(
            "/api/v1/articles",
            """{"name":"Arroz","categoryId":"$food","unit":"GRAM"}""",
            token,
        ).body!!.extract("id")

        val stock = http.postJson(
            "/api/v1/assets/intake",
            """{"articleId":"$article","ownerId":"${household.memberId}","quantity":900,
                "location":{"type":"LOCATION","id":"$room"}}""",
            token,
        ).body!!.extract("id")

        // Los cuatro modulos, **despues** de que exista el estado: activarlos
        // siembra desde lo que hay --la ficha de maquina del taladro, la apertura
        // de la existencia-- que es la regla de la ADR-010.
        for (key in listOf("SUPPLIERS", "WAREHOUSE", "PURCHASING", "MAINTENANCE")) {
            http.postJson("/api/v1/modules/$key/activation", "", token)
                .statusCode.shouldBe(HttpStatus.OK)
        }

        // Un fichero de verdad --con su miniatura en disco-- y el documento que
        // lo adjunta.
        val file = http.uploadFile(token, imageBytes("png"), "manual.png", "image/png").body!!.extract("id")
        http.postJson(
            "/api/v1/documents",
            """{"assetId":"$drill","type":"MANUAL","fileId":"$file"}""",
            token,
        ).statusCode.shouldBe(HttpStatus.CREATED)

        // Una invitacion viva.
        http.postJson(
            "/api/v1/invitations",
            """{"email":"invitado-${UUID.randomUUID()}@example.test","role":"HOUSEHOLD_MEMBER"}""",
            token,
        ).statusCode.shouldBe(HttpStatus.CREATED)

        // Un prestamo a alguien de fuera **con correo**, que es lo que emite el
        // token acotado y llena `loan_access_tokens`.
        http.postJson(
            "/api/v1/loans",
            """{"assetId":"$drill","lender":{"userId":"${household.memberId}"},
                "borrower":{"external":{"name":"Vecino","email":"vecino-${UUID.randomUUID()}@example.test"}}}""",
            token,
        ).statusCode.shouldBe(HttpStatus.CREATED)

        // Warehouse: movimiento, lote, ajuste de articulo y ajuste de ubicacion.
        http.postJson("$WAREHOUSE/stock/$stock/consumptions", """{"quantity":100}""", token)
            .statusCode.shouldBe(HttpStatus.NO_CONTENT)
        http.postJson(
            "$WAREHOUSE/lots",
            """{"assetId":"$stock","lotCode":"L-1","expiresOn":"2030-01-01","quantity":100}""",
            token,
        ).statusCode.shouldBe(HttpStatus.CREATED)
        http.patchJson("$WAREHOUSE/articles/$article", """{"minimumQuantity":100}""", token)
            .statusCode.shouldBe(HttpStatus.OK)
        http.patchJson("$WAREHOUSE/locations/$room", """{"expiryLeadDays":7}""", token)
            .statusCode.shouldBe(HttpStatus.OK)

        // Compras: una linea de la lista y una compra que la agrupa.
        val item = http.postJson(
            "$PURCHASING/list",
            """{"articleId":"$article","quantity":500}""",
            token,
        ).body!!.extract("id")
        http.postJson("$PURCHASING/purchases", """{"itemIds":["$item"]}""", token)
            .statusCode.shouldBe(HttpStatus.CREATED)

        // Proveedores: un contacto y su enlace.
        val supplier = http.postJson(
            "/api/v1/suppliers",
            """{"name":"Fontanero ${UUID.randomUUID()}","serviceCategory":"PLUMBING","phone":"600000000"}""",
            token,
        ).body!!.extract("id")
        http.postJson("/api/v1/suppliers/$supplier/links", """{"locationId":"$room"}""", token)
            .statusCode.shouldBe(HttpStatus.CREATED)

        // Mantenimiento: la ficha de maquina la sembro la activacion; aqui, el
        // plan y la intervencion.
        http.postJson(
            "$CMMS/plans",
            """{"assetId":"$drill","name":"Revisión anual","intervalMonths":12,"nextDueOn":"${inDays(30)}"}""",
            token,
        ).statusCode.shouldBe(HttpStatus.CREATED)
        http.postJson(
            "$CMMS/interventions",
            """{"assetId":"$drill","kind":"CORRECTIVE","performedOn":"${today()}",
                "summary":"Se cambió la escobilla"}""",
            token,
        ).statusCode.shouldBe(HttpStatus.CREATED)

        // Y una entrega pendiente en la cola del outbox, que es **la unica tabla
        // del modelo que hay que llenar a mano**.
        //
        // Todo lo de arriba ha publicado eventos de sobra, y ninguno deja rastro
        // aqui: la fila se borra al repartirse (ADR-013), asi que el estado
        // normal de `event_outbox` es vacia. Con la comprobacion de «cada tabla
        // con algo dentro» eso la dejaria fuera del retrato, y una tabla que la
        // siembra no llega a tocar **pasaria por purgada** sin que nadie la
        // hubiera purgado.
        //
        // Se deja una fila que nadie va a confirmar --el relay esta apagado en la
        // suite-- y con ella se comprueba de paso lo que la ADR-013 promete: **lo
        // pendiente de un hogar purgado se va con el hogar**. Es lo correcto, no
        // hay a quien entregarselo, y conservarlo dejaria el `payload` de un
        // hogar que pidio marcharse.
        outbox.record(
            DomainEvent(
                eventId = UUID.randomUUID(),
                type = "AssetDeactivated",
                occurredAt = Instant.now(),
                householdId = UUID.fromString(household.householdId),
                aggregateId = drill,
                version = DomainEvent.INITIAL_VERSION,
                payload = mapOf("mergedIntoAssetId" to null),
            ),
        )
    }

    private fun requestClosure(household: TestHousehold) {
        http.postJson("/api/v1/households/current/closure", "", household.accessToken)
            .statusCode.shouldBe(HttpStatus.OK)
    }

    /**
     * Envejece la baja hasta que la gracia haya vencido.
     *
     * Mueve **las dos fechas** y no solo el vencimiento, que es lo que el
     * `CHECK` de la V14 obliga a hacer y lo correcto de todas formas: una gracia
     * vencida es una solicitud vieja, no una solicitud de hoy con fecha de ayer.
     * Retroceder solo el vencimiento produce un estado que la aplicacion no
     * puede crear, y probar contra un estado imposible no demuestra nada.
     *
     * Por la conexion del propietario --superusuario, se salta las politicas--
     * porque es preparacion y no parte de lo que se mide. Lo que se mide corre
     * despues, con el usuario de la aplicacion.
     */
    private fun expireClosure(householdId: UUID) {
        postgres.ownerConnection().use {
            it.execute(
                """
                UPDATE households
                   SET closure_requested_at = now() - interval '31 days',
                       closure_effective_at = now() - interval '1 day'
                 WHERE id = ?
                """.trimIndent(),
                householdId,
            )
        }
    }

    /**
     * Cuantas filas tiene el hogar en **cada** tabla que cuelga de `households`.
     *
     * La lista sale del catalogo de PostgreSQL y no de una constante escrita a
     * mano, que es lo unico que hace que esta prueba siga valiendo cuando llegue
     * la tabla veintitres: una tabla nueva con `household_id` entra sola, y si la
     * cascada no se la lleva, esto falla sin que nadie tenga que acordarse.
     */
    private fun rowsPerTable(householdId: UUID): Map<String, Int> =
        postgres.ownerConnection().use { owner ->
            val tables = owner.queryAll(
                """
                SELECT c.relname
                FROM pg_attribute a
                JOIN pg_class c ON c.oid = a.attrelid
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE a.attname = 'household_id'
                  AND a.attnum > 0
                  AND NOT a.attisdropped
                  AND c.relkind = 'r'
                  AND n.nspname = 'public'
                ORDER BY c.relname
                """.trimIndent(),
            ) { it.getString(1) }

            (tables + "households").associateWith { table ->
                val column = if (table == "households") "id" else "household_id"
                owner.count("SELECT count(*) FROM $table WHERE $column = ?", householdId)
            }
        }

    private fun filesUnder(prefix: String): Int {
        val root = storageRoot.resolve(prefix)
        if (!Files.isDirectory(root)) return 0
        return Files.walk(root).use { walk -> walk.filter { Files.isRegularFile(it) }.count().toInt() }
    }

    private fun avatarPathOf(identityId: UUID): Path =
        storageRoot.resolve("avatar/${identityId.toString().take(2)}/$identityId")

    private fun inDays(days: Long): String = today().plusDays(days).toString()

    private companion object {
        const val WAREHOUSE = "/api/v1/warehouse"
        const val PURCHASING = "/api/v1/purchasing"
        const val CMMS = "/api/v1/maintenance"
    }
}
