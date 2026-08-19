package com.drp

import com.drp.core.adapter.http.createDurable
import com.drp.core.application.port.PasswordHasher
import com.drp.test.DrpPostgres
import com.drp.test.SpringIntegrationTest
import com.drp.test.extract
import com.drp.test.getJson
import com.drp.test.postJson
import com.drp.test.registerHousehold
import com.drp.test.seededCategory
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.imageio.ImageIO
import kotlin.math.roundToLong
import kotlin.system.measureNanoTime

/**
 * La medición que decide el VPS al cerrar la Fase 1.
 *
 * **No es una prueba: es un instrumento.** No afirma nada, no falla si un número
 * sube, y por eso lleva `@Tag("capacity")` y queda fuera del `test` de siempre —
 * una medición que rompe la construcción cuando la máquina va lenta solo enseña
 * a ignorarla. Se ejecuta a mano o en su propio trabajo de la CI:
 *
 * ```
 * ./gradlew capacityMeasurement --no-daemon
 * ```
 *
 * Mide dos cosas que se comportan muy distinto:
 *
 * - **Los bytes son portables.** Lo que ocupa una fila en PostgreSQL no depende
 *   de la máquina, así que medirlos aquí vale para cualquier servidor. Se
 *   siembran **tres tamaños** —1, 5 y 25 hogares— y no uno, porque lo que hace
 *   falta es la **pendiente** por hogar y no un promedio: con un solo punto, el
 *   coste fijo del esquema —índices vacíos, catálogo, las categorías sembradas—
 *   se reparte entre los hogares medidos y sale un número que empeora o mejora
 *   solo con cambiar cuántos había.
 * - **Y desde la Fase 2, los bytes de lo que el hogar *hace*, que son otra cosa.**
 *   Hasta el Hito 3 todo el modelo crecía con lo que el hogar **tiene** —cuántos
 *   assets, cuántos artículos, cuántos sitios— y eso tiene techo: una casa no
 *   pasa de unos cientos de cosas. El cuaderno de Warehouse, la lista de la
 *   compra, las compras, las intervenciones de CMMS y los avisos crecen con lo
 *   que el hogar **hace**, y eso no tiene techo: crece mientras el hogar exista.
 *   Un solo número de «bytes por hogar» mezcla las dos magnitudes y **envejece
 *   mal**, porque significa una cosa el primer día y otra al tercer año. Por eso
 *   son dos mediciones y no una.
 * - **La CPU no es portable, y aquí eso se dice en lugar de disimularlo.** Lo que
 *   se mide es coste **relativo** de las tres operaciones caras del core, y el
 *   número absoluto solo significa algo en la máquina donde se tomó. De ahí que
 *   la CI lo ejecute en su runner de Linux con 2 vCPU, que se parece mucho más a
 *   un VPS-2 que un portátil con Docker Desktop.
 */
@Tag("capacity")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// **El orden importa, y solo aquí.** Las dos mediciones de disco comparten base
// de datos, así que la primera que corra es la unica que ve el esquema vacio: sin
// fijar el orden, «coste fijo del esquema» significaba una cosa u otra segun por
// donde arrancara JUnit ese dia. La de existencias va primera porque su numero
// —el coste fijo— es el que solo se puede tomar en limpio; la de actividad mide
// una pendiente, y una pendiente no depende de lo que hubiera antes.
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class CapacityMeasurementTest : SpringIntegrationTest() {

    @Autowired private lateinit var http: TestRestTemplate
    @Autowired private lateinit var passwords: PasswordHasher

    @Test
    @Order(1)
    @DisplayName("cuánto ocupa un hogar en la base de datos")
    fun `bytes por hogar`() {
        val points = mutableListOf<Pair<Int, Long>>()
        val baseline = databaseBytes()

        var seeded = 0
        for (target in listOf(1, 5, 25)) {
            repeat(target - seeded) { seedRealisticHousehold() }
            seeded = target
            // `VACUUM ANALYZE` antes de medir: sin él, el tamaño incluye tuplas
            // muertas de las actualizaciones y la pendiente sale inflada por algo
            // que el servidor recuperaría solo.
            vacuum()
            points += target to (databaseBytes() - baseline)
        }

        report("BYTES EN LA BASE DE DATOS")
        report("  esquema vacío (coste fijo): ${mib(baseline)}")
        points.forEach { (households, bytes) ->
            report("  $households hogar(es): ${mib(bytes)} (${bytes / households} B por hogar acumulado)")
        }

        // La pendiente entre el primer y el último punto: es lo que cuesta el
        // hogar número N, ya sin el coste fijo repartido dentro.
        val (firstCount, firstBytes) = points.first()
        val (lastCount, lastBytes) = points.last()
        val slope = (lastBytes - firstBytes) / (lastCount - firstCount)
        report("  PENDIENTE: $slope B por hogar (${mib(slope)})")

        report("  Por tabla, con $lastCount hogares:")
        tableSizes().forEach { (table, bytes) -> report("    $table: ${mib(bytes)}") }
    }

    /**
     * Lo que cuesta **un año de vida** de un hogar, que es la magnitud que la
     * Fase 2 introduce y la de arriba no puede medir.
     *
     * La de arriba mide un hogar **recién sembrado**: la foto del día uno. Esta
     * mide la pendiente en el tiempo, sobre un hogar que ya existe y al que no se
     * le añade ni una cosa más — solo se le hace vivir. Lo que crece aquí es el
     * cuaderno de Warehouse, la lista de la compra, las compras y las
     * intervenciones, y ninguna de las cuatro se para nunca.
     *
     * **Dos puntos y no uno**, por el mismo motivo que allí son tres: PostgreSQL
     * reserva páginas de 8 kB, así que la primera actividad de cada tabla paga un
     * salto que no se repite. Con un punto, el salto se reparte entre los días
     * medidos y la pendiente sale inflada; con dos, se cancela en la resta.
     */
    @Test
    @Order(2)
    @DisplayName("cuánto ocupa un año de lo que el hogar hace")
    fun `bytes por año de actividad`() {
        val home = seedRealisticHousehold()
        vacuum()
        val baseline = databaseBytes()
        val tablesBefore = tableSizes().toMap()

        val points = mutableListOf<Pair<Int, Long>>()
        var lived = 0
        for (target in listOf(SHORT_RUN_DAYS, LONG_RUN_DAYS)) {
            repeat(target - lived) { liveOneDay(home) }
            lived = target
            vacuum()
            points += target to (databaseBytes() - baseline)
        }

        report("BYTES DE LO QUE EL HOGAR HACE")
        report("  perfil diario: $CONSUMPTIONS_PER_DAY consumos y $SHOPPING_LINES_PER_DAY líneas de la compra")
        points.forEach { (days, bytes) ->
            report("  $days días: ${mib(bytes)} (${bytes / days} B por día acumulado)")
        }

        val (firstDays, firstBytes) = points.first()
        val (lastDays, lastBytes) = points.last()
        val perDay = (lastBytes - firstBytes) / (lastDays - firstDays)
        report("  PENDIENTE: $perDay B por día, o sea ${mib(perDay * 365)} por hogar y año")

        report("  Lo que creció, por tabla:")
        tableSizes()
            .map { (table, bytes) -> table to bytes - (tablesBefore[table] ?: 0L) }
            .filter { (_, growth) -> growth > 0 }
            .sortedByDescending { (_, growth) -> growth }
            .forEach { (table, growth) -> report("    $table: +${mib(growth)}") }
    }

    /**
     * Un día de vida de la casa: se gasta lo que se gasta y se apunta lo que hace
     * falta.
     *
     * El perfil es deliberadamente modesto —tres consumos y dos líneas al día— y
     * conviene que se lea así: es una casa que **apunta las cosas**, que es el
     * caso peor razonable. Quien no apunte nada no escribe ninguna de estas filas.
     */
    private fun liveOneDay(home: LivingHousehold) {
        repeat(CONSUMPTIONS_PER_DAY) { index ->
            http.postJson(
                "/api/v1/warehouse/stock/${home.stock[index % home.stock.size]}/consumptions",
                """{"quantity":1}""",
                home.accessToken,
            )
        }
        repeat(SHOPPING_LINES_PER_DAY) {
            http.postJson(
                "/api/v1/purchasing/list",
                """{"name":"Cosa ${UUID.randomUUID()}","quantity":1}""",
                home.accessToken,
            )
        }
    }

    @Test
    @Order(3)
    @DisplayName("cuánto cuestan las tres operaciones caras del core")
    fun `coste de cpu`() {
        report("CPU (mediana y p95 de $ITERATIONS repeticiones)")

        // 1. Argon2id, que es deliberadamente caro: 19 MiB y 2 iteraciones. Es lo
        //    que domina un login, y por tanto lo que fija cuántos caben por
        //    segundo y por núcleo.
        val hashing = timeOf(ITERATIONS) { passwords.hash("el gato duerme en el sofa") }
        reportTiming("  Argon2id (login)", hashing)
        report("    ~${(1000.0 / median(hashing)).roundToLong()} logins/s por núcleo")

        // 2. y 3. La recodificación de una foto de 12 MP y su miniatura, que es
        //    lo que paga cada subida de imagen. Se mide con menos repeticiones:
        //    cuesta órdenes de magnitud más y cien pasadas no añaden precisión.
        val photo = photoOf(4000, 3000)
        val recoding = timeOf(IMAGE_ITERATIONS) { recode(photo, 4000) }
        reportTiming("  Recodificar 12 MP", recoding)

        val thumbnailing = timeOf(IMAGE_ITERATIONS) { recode(photo, 320) }
        reportTiming("  Miniatura 320 px", thumbnailing)

        report("  Núcleos disponibles en esta máquina: ${Runtime.getRuntime().availableProcessors()}")
        report("  Memoria máxima de la JVM: ${mib(Runtime.getRuntime().maxMemory())}")
    }

    /**
     * Un hogar como el de una casa de verdad, no un hogar de prueba.
     *
     * Las cifras salen de lo que un hogar tiene: unas decenas de cosas
     * duraderas, la despensa, unas cuantas habitaciones y algún préstamo. Medir
     * con un hogar vacío daría la pendiente del esquema y no la del uso.
     */
    private fun seedRealisticHousehold(): LivingHousehold {
        val home = http.registerHousehold()
        val tools = http.seededCategory(home.accessToken, "Herramientas")
        val food = http.seededCategory(home.accessToken, "Alimentación")

        val rooms = (1..ROOMS).map { room ->
            http.postJson(
                "/api/v1/locations",
                """{"name":"Habitación $room","type":"ROOM"}""",
                home.accessToken,
            ).body!!.extract("id")
        }

        val articles = (1..ARTICLES).map { index ->
            http.postJson(
                "/api/v1/articles",
                """{"name":"Artículo $index ${UUID.randomUUID()}","categoryId":"$food","unit":"GRAM"}""",
                home.accessToken,
            ).body!!.extract("id")
        }

        val durables = (1..DURABLES).map { index ->
            http.postJson(
                "/api/v1/assets",
                """{"name":"Cosa $index","type":"DURABLE","categoryId":"$tools",
                    "location":{"type":"LOCATION","id":"${rooms[index % rooms.size]}"}}""",
                home.accessToken,
            ).body!!.extract("id")
        }

        articles.forEachIndexed { index, articleId ->
            http.postJson(
                "/api/v1/assets/intake",
                """{"articleId":"$articleId","ownerId":"${home.memberId}","quantity":${(index + 1) * 100},
                    "location":{"type":"LOCATION","id":"${rooms[index % rooms.size]}"}}""",
                home.accessToken,
            )
        }

        repeat(DOCUMENTS) { index ->
            http.postJson(
                "/api/v1/documents",
                """{"assetId":"${durables[index % durables.size]}","type":"MANUAL",
                    "url":"https://ejemplo.test/${UUID.randomUUID()}"}""",
                home.accessToken,
            )
        }

        repeat(LOANS) { index ->
            http.postJson(
                "/api/v1/loans",
                """{"assetId":"${durables[index]}","lender":{"userId":"${home.memberId}"},
                    "borrower":{"external":{"name":"Vecino $index","email":"v$index@example.test"}}}""",
                home.accessToken,
            )
        }

        // **Los cuatro módulos encendidos**, que es lo que cambia esta medición
        // respecto a la de la Fase 1. Encenderlos al final y no al principio no es
        // indiferente: la siembra de cada módulo recorre el estado que ya hay
        // —artículos, sitios, existencias, duraderos— así que así se mide lo que
        // le cuesta a un hogar **que ya existía** encenderlos, que es el caso de
        // cualquiera que los active después. Un hogar que los tuviera desde el
        // primer día llega a las mismas filas por los handlers de eventos.
        MODULES.forEach { key ->
            http.postJson("/api/v1/modules/$key/activation", "", home.accessToken)
        }

        return LivingHousehold(home.accessToken)
    }

    /** Un hogar sembrado, con lo justo para poder hacerle vivir un año. */
    private inner class LivingHousehold(val accessToken: String) {

        /**
         * Unas cuantas existencias suyas, en **una sola consulta**.
         *
         * Se leen aquí y no durante la siembra a propósito: la medición de arriba
         * siembra veinticinco hogares y no necesita ninguna, así que pedirlas allí
         * añadiría mil llamadas a una medición que no las usa.
         */
        val stock: List<String> by lazy {
            val body = http.getJson("/api/v1/assets?type=CONSUMABLE&size=20", accessToken).body.orEmpty()
            Regex(""""id":"([0-9a-f-]{36})"""").findAll(body).map { it.groupValues[1] }.toList()
        }
    }

    private fun databaseBytes(): Long = queryLong("SELECT pg_database_size(current_database())")

    private fun vacuum() {
        DrpPostgres.instance.ownerConnection().use { it.createStatement().use { s -> s.execute("VACUUM ANALYZE") } }
    }

    private fun tableSizes(): List<Pair<String, Long>> =
        DrpPostgres.instance.ownerConnection().use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(
                    """
                    SELECT relname, pg_total_relation_size(c.oid)
                    FROM pg_class c
                    JOIN pg_namespace n ON n.oid = c.relnamespace
                    WHERE n.nspname = 'public' AND c.relkind = 'r'
                    ORDER BY pg_total_relation_size(c.oid) DESC
                    """.trimIndent(),
                ).use { rows ->
                    val sizes = mutableListOf<Pair<String, Long>>()
                    while (rows.next()) sizes += rows.getString(1) to rows.getLong(2)
                    sizes
                }
            }
        }

    private fun queryLong(sql: String): Long =
        DrpPostgres.instance.ownerConnection().use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { rows -> if (rows.next()) rows.getLong(1) else 0L }
            }
        }

    /** Los milisegundos de cada repetición, ya ordenados. */
    private fun timeOf(iterations: Int, action: () -> Unit): List<Double> {
        // Un calentamiento corto: la primera pasada paga la compilación JIT y el
        // arranque de los códecs, y meterla en la muestra desplaza la mediana.
        repeat(3) { action() }
        return (1..iterations)
            .map { measureNanoTime(action) / 1_000_000.0 }
            .sorted()
    }

    private fun median(samples: List<Double>): Double = samples[samples.size / 2]

    private fun reportTiming(label: String, samples: List<Double>) {
        val p95 = samples[(samples.size * 0.95).toInt().coerceAtMost(samples.size - 1)]
        report("$label: mediana ${"%.1f".format(median(samples))} ms, p95 ${"%.1f".format(p95)} ms")
    }

    private fun photoOf(width: Int, height: Int): BufferedImage =
        BufferedImage(width, height, BufferedImage.TYPE_INT_RGB).apply {
            // Ruido y no un color plano: un lienzo uniforme comprime a nada y
            // mediría un caso que ninguna foto real produce.
            val random = java.util.Random(42)
            for (y in 0 until height step 4) {
                for (x in 0 until width step 4) setRGB(x, y, random.nextInt())
            }
        }

    /** La misma forma que el almacén: decodificar a píxeles y pintar en un lienzo nuevo. */
    private fun recode(source: BufferedImage, maxSide: Int) {
        val scale = maxSide.toDouble() / maxOf(source.width, source.height)
        val width = (source.width * scale).toInt().coerceAtLeast(1)
        val height = (source.height * scale).toInt().coerceAtLeast(1)

        val canvas = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        canvas.createGraphics().apply {
            drawImage(source.getScaledInstance(width, height, java.awt.Image.SCALE_SMOOTH), 0, 0, null)
            dispose()
        }
        ImageIO.write(canvas, "jpeg", ByteArrayOutputStream())
    }

    private fun mib(bytes: Long): String =
        if (bytes < 1024 * 1024) "${bytes / 1024} kiB" else "%.1f MiB".format(bytes / 1024.0 / 1024.0)

    /** A la salida estándar a propósito: el resultado de esto se lee, no se afirma. */
    private fun report(line: String) = println("[capacidad] $line")

    private companion object {
        const val ITERATIONS = 100
        const val IMAGE_ITERATIONS = 10

        /** El catálogo desplegado al cerrar la Fase 2. */
        val MODULES = listOf("SUPPLIERS", "WAREHOUSE", "PURCHASING", "MAINTENANCE")

        // Los dos puntos de la medición de actividad, y el perfil de un día.
        //
        // El tramo es largo —cuatro meses entre los dos puntos— y esa es la
        // diferencia con la primera versión, que medía entre el día 30 y el 90 y
        // daba pendientes con un tercio de diferencia entre dos ejecuciones
        // seguidas. La causa no es el ruido de la máquina sino **la granularidad
        // de PostgreSQL**: reserva páginas de 8 kB y las divisiones de índice
        // llegan a saltos, así que sobre trescientas filas cada salto pesa
        // demasiado. Con seis veces más filas el mismo salto deja de decidir el
        // resultado.
        const val SHORT_RUN_DAYS = 60
        const val LONG_RUN_DAYS = 180
        const val CONSUMPTIONS_PER_DAY = 3
        const val SHOPPING_LINES_PER_DAY = 2

        // El perfil de un hogar de verdad.
        const val ROOMS = 8
        const val ARTICLES = 40
        const val DURABLES = 50
        const val DOCUMENTS = 15
        const val LOANS = 5
    }
}
