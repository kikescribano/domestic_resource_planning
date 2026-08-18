package com.drp.core.adapter.http

import com.drp.core.application.port.HierarchyLock
import com.drp.platform.tenant.TenantContext
import com.drp.test.SpringIntegrationTest
import com.drp.test.TestHousehold
import com.drp.test.extract
import com.drp.test.getJson
import com.drp.test.patchJson
import com.drp.test.postJson
import com.drp.test.registerHousehold
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Barrido del anti-ciclo: no revisa el codigo, intenta **esquivarlo**.
 *
 * Cuatro afirmaciones se ponen a prueba ejecutandolas: que el ciclo de N nodos
 * se rechaza y no solo el de dos, que el cerrojo cierra la carrera, que el
 * `PATCH` no es una puerta trasera y que el tope de profundidad 100 del CTE no
 * abre un agujero en una jerarquia legitima mas profunda que eso.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HierarchyCycleSweepTest : SpringIntegrationTest() {

    @Autowired private lateinit var http: TestRestTemplate

    @Autowired private lateinit var jdbc: JdbcTemplate

    @Autowired private lateinit var hierarchyLock: HierarchyLock

    @Autowired private lateinit var tenantContext: TenantContext

    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    @PersistenceContext private lateinit var entityManager: EntityManager

    private val json = ObjectMapper()

    // ----------------------------------------------------------------------
    // 1. El ciclo de N nodos, con cadenas largas y ramas laterales
    // ----------------------------------------------------------------------

    @Test
    @DisplayName("cadena de seis ubicaciones: colgar la raiz de cualquier descendiente se rechaza")
    fun `el ciclo de seis nodos se rechaza en ubicaciones`() {
        val home = http.registerHousehold()
        val chain = home.locationChain(6)

        // Contra CADA descendiente, no solo contra el ultimo.
        chain.drop(1).forEach { descendant ->
            val attempt = http.patchJson(
                "/api/v1/locations/${chain.first()}",
                """{"parentLocationId":"$descendant"}""",
                home.accessToken,
            )
            attempt.statusCode.shouldBe(HttpStatus.CONFLICT)
            attempt.body!!.shouldContain("LOCATION_CYCLE")
        }

        // Y desde el medio: el nivel 3 tampoco puede colgar del 6.
        val fromMiddle = http.patchJson(
            "/api/v1/locations/${chain[2]}",
            """{"parentLocationId":"${chain[5]}"}""",
            home.accessToken,
        )
        fromMiddle.statusCode.shouldBe(HttpStatus.CONFLICT)

        val (cycle, path) = home.locationPathFrom(chain.last())
        cycle.shouldBe(false)
        path.size.shouldBe(6)
    }

    @Test
    @DisplayName("cadena de diez ubicaciones con rama lateral: el ciclo por la rama tambien se rechaza")
    fun `el ciclo de diez nodos y por rama lateral se rechaza en ubicaciones`() {
        val home = http.registerHousehold()
        val trunk = home.locationChain(10, prefix = "T")

        // Una rama lateral que cuelga del cuarto nivel del tronco.
        val branch1 = home.createLocation("""{"name":"R1","type":"OTHER","parentLocationId":"${trunk[3]}"}""")
        val branch2 = home.createLocation("""{"name":"R2","type":"OTHER","parentLocationId":"$branch1"}""")

        // La raiz bajo la hoja del tronco: diez niveles de camino.
        http.patchJson("/api/v1/locations/${trunk[0]}", """{"parentLocationId":"${trunk[9]}"}""", home.accessToken)
            .let {
                it.statusCode.shouldBe(HttpStatus.CONFLICT)
                it.body!!.shouldContain("LOCATION_CYCLE")
            }

        // Y bajo la hoja de la RAMA, que no esta en el camino directo del tronco.
        http.patchJson("/api/v1/locations/${trunk[0]}", """{"parentLocationId":"$branch2"}""", home.accessToken)
            .let {
                it.statusCode.shouldBe(HttpStatus.CONFLICT)
                it.body!!.shouldContain("LOCATION_CYCLE")
            }

        // El nivel 4 --del que cuelga la rama-- bajo la hoja de su propia rama.
        http.patchJson("/api/v1/locations/${trunk[3]}", """{"parentLocationId":"$branch2"}""", home.accessToken)
            .let {
                it.statusCode.shouldBe(HttpStatus.CONFLICT)
                it.body!!.shouldContain("LOCATION_CYCLE")
            }

        // Y el movimiento legitimo, que es lo contrario: la rama entera bajo la hoja del tronco.
        http.patchJson("/api/v1/locations/$branch1", """{"parentLocationId":"${trunk[9]}"}""", home.accessToken)
            .statusCode.shouldBe(HttpStatus.OK)

        val (cycle, _) = home.locationPathFrom(branch2)
        cycle.shouldBe(false)
    }

    @Test
    @DisplayName("cadena de diez assets con rama lateral: el ciclo por la rama tambien se rechaza")
    fun `el ciclo de diez nodos y por rama lateral se rechaza en assets`() {
        val home = http.registerHousehold()
        val trunk = home.assetChain(10, prefix = "T")

        val branch1 = home.createAsset(home.assetBody("R1", parent = trunk[3]))
        val branch2 = home.createAsset(home.assetBody("R2", parent = branch1))

        http.patchJson(
            "/api/v1/assets/${trunk[0]}",
            """{"location":{"type":"ASSET","id":"${trunk[9]}"}}""",
            home.accessToken,
        ).let {
            it.statusCode.shouldBe(HttpStatus.CONFLICT)
            it.body!!.shouldContain("LOCATION_CYCLE")
        }

        http.patchJson(
            "/api/v1/assets/${trunk[0]}",
            """{"location":{"type":"ASSET","id":"$branch2"}}""",
            home.accessToken,
        ).let {
            it.statusCode.shouldBe(HttpStatus.CONFLICT)
            it.body!!.shouldContain("LOCATION_CYCLE")
        }

        http.patchJson(
            "/api/v1/assets/${trunk[3]}",
            """{"location":{"type":"ASSET","id":"$branch2"}}""",
            home.accessToken,
        ).let {
            it.statusCode.shouldBe(HttpStatus.CONFLICT)
            it.body!!.shouldContain("LOCATION_CYCLE")
        }

        val (cycle, _) = home.assetPathFrom(branch2)
        cycle.shouldBe(false)
    }

    // ----------------------------------------------------------------------
    // 2. El cerrojo y la carrera
    // ----------------------------------------------------------------------

    /**
     * Lo primero que hay que comprobar del cerrojo no es que serialice, sino que
     * este **puesto sobre la transaccion que hace la comprobacion**. El
     * `pg_advisory_xact_lock` va por `JdbcTemplate` y la lectura de ancestros por
     * JPA: si cada uno cogiera una conexion distinta del pool, el cerrojo se
     * soltaria al instante y no protegeria nada.
     */
    @Test
    @DisplayName("el cerrojo se toma en la MISMA conexion que la transaccion que comprueba")
    fun `el advisory lock vive en la transaccion del caso de uso`() {
        val household = UUID.randomUUID()

        val result = tenantContext.runAs(household) {
            TransactionTemplate(transactionManager).execute {
                val jpaPid = (entityManager.createNativeQuery("SELECT pg_backend_pid()").singleResult as Number).toInt()
                val jdbcPid = jdbc.queryForObject("SELECT pg_backend_pid()", Int::class.java)!!

                val before = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_locks WHERE locktype = 'advisory' AND pid = pg_backend_pid()",
                    Long::class.java,
                )!!
                hierarchyLock.acquire()
                val after = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_locks WHERE locktype = 'advisory' AND pid = pg_backend_pid()",
                    Long::class.java,
                )!!

                listOf(jpaPid.toLong(), jdbcPid.toLong(), before, after)
            }!!
        }

        val (jpaPid, jdbcPid, before, after) = result
        println("PID de JPA=$jpaPid, PID de JdbcTemplate=$jdbcPid, advisory antes=$before, despues=$after")
        jdbcPid.shouldBe(jpaPid)
        after.shouldBe(before + 1)
    }

    @Test
    @DisplayName("dos peticiones a la vez, A bajo B y B bajo A, no dejan un ciclo de dos nodos")
    fun `la carrera de dos nodos no deja ciclo en ubicaciones`() {
        val home = http.registerHousehold()
        val outcomes = mutableListOf<List<Int>>()

        repeat(ROUNDS) { round ->
            val a = home.createLocation("""{"name":"A$round","type":"OTHER"}""")
            val b = home.createLocation("""{"name":"B$round","type":"OTHER"}""")

            outcomes += race(
                { home.moveLocation(a, b) },
                { home.moveLocation(b, a) },
            )

            val (cycleFromA, pathA) = home.locationPathFrom(a)
            val (cycleFromB, pathB) = home.locationPathFrom(b)
            check(!cycleFromA) { "Ciclo desde A en la ronda $round: ${pathA.joinToString(" -> ")}" }
            check(!cycleFromB) { "Ciclo desde B en la ronda $round: ${pathB.joinToString(" -> ")}" }
        }

        println("Carrera de dos ubicaciones, $ROUNDS rondas: ${outcomes.groupingBy { it.sorted() }.eachCount()}")
        // Las dos no pueden salir bien: eso ya seria el ciclo.
        outcomes.none { it.count { code -> code == 200 } == 2 }.shouldBe(true)
    }

    /**
     * El caso que un `SELECT ... FOR UPDATE` no cerraria: tres peticiones que
     * bloquean parejas distintas y no coinciden en ninguna fila.
     */
    @Test
    @DisplayName("tres peticiones a la vez, A bajo B, B bajo C y C bajo A, no dejan un ciclo de tres")
    fun `la carrera de tres nodos no deja ciclo en ubicaciones`() {
        val home = http.registerHousehold()
        val outcomes = mutableListOf<List<Int>>()

        repeat(ROUNDS) { round ->
            val a = home.createLocation("""{"name":"A$round","type":"OTHER"}""")
            val b = home.createLocation("""{"name":"B$round","type":"OTHER"}""")
            val c = home.createLocation("""{"name":"C$round","type":"OTHER"}""")

            outcomes += race(
                { home.moveLocation(a, b) },
                { home.moveLocation(b, c) },
                { home.moveLocation(c, a) },
            )

            listOf(a, b, c).forEach { node ->
                val (cycle, path) = home.locationPathFrom(node)
                check(!cycle) { "Ciclo en la ronda $round desde $node: ${path.joinToString(" -> ")}" }
            }
        }

        println("Carrera de tres ubicaciones, $ROUNDS rondas: ${outcomes.groupingBy { it.sorted() }.eachCount()}")
        // Las tres aristas a la vez son exactamente el ciclo: alguna tiene que caer.
        outcomes.none { it.count { code -> code == 200 } == 3 }.shouldBe(true)
    }

    @Test
    @DisplayName("la misma carrera de tres, en la jerarquia de assets")
    fun `la carrera de tres nodos no deja ciclo en assets`() {
        val home = http.registerHousehold()
        val outcomes = mutableListOf<List<Int>>()

        repeat(ROUNDS) { round ->
            val a = home.createAsset(home.assetBody("A$round"))
            val b = home.createAsset(home.assetBody("B$round"))
            val c = home.createAsset(home.assetBody("C$round"))

            outcomes += race(
                { home.moveAsset(a, b) },
                { home.moveAsset(b, c) },
                { home.moveAsset(c, a) },
            )

            listOf(a, b, c).forEach { node ->
                val (cycle, path) = home.assetPathFrom(node)
                check(!cycle) { "Ciclo en la ronda $round desde $node: ${path.joinToString(" -> ")}" }
            }
        }

        println("Carrera de tres assets, $ROUNDS rondas: ${outcomes.groupingBy { it.sorted() }.eachCount()}")
        outcomes.none { it.count { code -> code == 200 } == 3 }.shouldBe(true)
    }

    // ----------------------------------------------------------------------
    // 3. El PATCH como puerta trasera
    // ----------------------------------------------------------------------

    @Test
    @DisplayName("cuerpos raros del PATCH: ninguno esquiva el anti-ciclo ni mueve lo que no se pide")
    fun `el patch no es una puerta trasera en ubicaciones`() {
        val home = http.registerHousehold()
        val chain = home.locationChain(4)
        val (root, second, third, leaf) = chain

        // a) El mismo padre que ya tenia: no se mueve nada y no hay ciclo que crear.
        http.patchJson("/api/v1/locations/$second", """{"parentLocationId":"$root"}""", home.accessToken)
            .let {
                it.statusCode.shouldBe(HttpStatus.OK)
                it.body!!.shouldContain("\"parentLocationId\":\"$root\"")
            }

        // b) Ausente: conserva el padre aunque cambien otros campos.
        http.patchJson("/api/v1/locations/$second", """{"notes":"sin tocar el padre"}""", home.accessToken)
            .body!!.shouldContain("\"parentLocationId\":\"$root\"")

        // c) A nulo: sube a raiz, que es legitimo y no es un ciclo. Se deshace luego.
        http.patchJson("/api/v1/locations/$leaf", """{"parentLocationId":null}""", home.accessToken)
            .let {
                it.statusCode.shouldBe(HttpStatus.OK)
                it.body!!.shouldContain("\"parentLocationId\":null")
            }
        http.patchJson("/api/v1/locations/$leaf", """{"parentLocationId":"$third"}""", home.accessToken)
            .statusCode.shouldBe(HttpStatus.OK)

        // d) El ciclo escondido entre otros campos, que es la puerta trasera de verdad.
        val mixed = http.patchJson(
            "/api/v1/locations/$root",
            """{"name":"Renombrada","notes":"nota","capacity":null,"type":"SHELF",
                "parentLocationId":"$leaf","environmentalConditions":null}""",
            home.accessToken,
        )
        mixed.statusCode.shouldBe(HttpStatus.CONFLICT)
        mixed.body!!.shouldContain("LOCATION_CYCLE")

        // Y no ha colado NADA del cuerpo: la transaccion entera se deshace.
        val after = http.getJson("/api/v1/locations/$root", home.accessToken).body!!
        after.shouldContain("\"parentLocationId\":null")
        after.shouldContain("\"name\":\"N0\"")

        // e) El campo repetido en el mismo objeto JSON.
        val duplicated = http.patchJson(
            "/api/v1/locations/$root",
            """{"parentLocationId":null,"parentLocationId":"$leaf"}""",
            home.accessToken,
        )
        println("PATCH con parentLocationId repetido -> ${duplicated.statusCode}: ${duplicated.body}")
        duplicated.statusCode.shouldBe(HttpStatus.CONFLICT)

        // f) Basura donde va un UUID: error de forma, nunca un 500.
        http.patchJson("/api/v1/locations/$root", """{"parentLocationId":"no-soy-un-uuid"}""", home.accessToken)
            .statusCode.value().shouldBe(400)

        val (cycle, path) = home.locationPathFrom(leaf)
        cycle.shouldBe(false)
        path.size.shouldBe(4)
    }

    @Test
    @DisplayName("los mismos cuerpos raros contra el PATCH de assets")
    fun `el patch no es una puerta trasera en assets`() {
        val home = http.registerHousehold()
        val chain = home.assetChain(4)
        val (root, second, _, leaf) = chain
        val room = home.createLocation("""{"name":"Cuarto","type":"ROOM"}""")

        // a) La misma ubicacion que ya tenia.
        http.patchJson(
            "/api/v1/assets/$second",
            """{"location":{"type":"ASSET","id":"$root"}}""",
            home.accessToken,
        ).statusCode.shouldBe(HttpStatus.OK)

        // b) Ausente: no se mueve.
        http.patchJson("/api/v1/assets/$second", """{"notes":"sin tocar"}""", home.accessToken)
            .body!!.shouldContain("\"id\":\"$root\"")

        // c) El ciclo escondido entre otros campos.
        val mixed = http.patchJson(
            "/api/v1/assets/$root",
            """{"name":"Renombrado","notes":"n","photoUrl":null,
                "location":{"type":"ASSET","id":"$leaf"}}""",
            home.accessToken,
        )
        mixed.statusCode.shouldBe(HttpStatus.CONFLICT)
        mixed.body!!.shouldContain("LOCATION_CYCLE")
        http.getJson("/api/v1/assets/$root", home.accessToken).body!!.shouldContain("\"name\":\"N0\"")

        // d) Mover a una LOCATION corta la cadena de assets: no es ciclo y debe pasar.
        http.patchJson(
            "/api/v1/assets/$root",
            """{"location":{"type":"LOCATION","id":"$room"}}""",
            home.accessToken,
        ).statusCode.shouldBe(HttpStatus.OK)

        // Y ahora el que antes era ciclo deja de serlo por el otro lado... salvo que
        // siga siendolo: root sigue por encima de leaf.
        http.patchJson(
            "/api/v1/assets/$root",
            """{"location":{"type":"ASSET","id":"$leaf"}}""",
            home.accessToken,
        ).let {
            it.statusCode.shouldBe(HttpStatus.CONFLICT)
            it.body!!.shouldContain("LOCATION_CYCLE")
        }

        val (cycle, _) = home.assetPathFrom(leaf)
        cycle.shouldBe(false)
    }

    // ----------------------------------------------------------------------
    // 4. El tope de profundidad 100 del CTE
    // ----------------------------------------------------------------------

    /**
     * La sospecha: `ancestorIdsOf` corta a 100 niveles, asi que en una jerarquia
     * legitima mas profunda la cadena que ve el anti-ciclo esta **incompleta**.
     */
    @Test
    @DisplayName("jerarquia de 102 ubicaciones: colgar la raiz de la hoja mas profunda que el tope")
    fun `el tope de profundidad del CTE en ubicaciones`() {
        val home = http.registerHousehold()
        val chain = home.locationChain(DEEP)
        val root = chain.first()

        // El ancestro numero 100 contando desde chain[100]: justo el ultimo que ve el CTE.
        val withinCap = http.patchJson(
            "/api/v1/locations/$root",
            """{"parentLocationId":"${chain[100]}"}""",
            home.accessToken,
        )
        println("Raiz bajo el nodo 101 (100 ancestros) -> ${withinCap.statusCode}: ${withinCap.body?.take(200)}")

        // Un nivel mas: la raiz queda fuera de los 100 que devuelve el CTE.
        val beyondCap = http.patchJson(
            "/api/v1/locations/$root",
            """{"parentLocationId":"${chain[101]}"}""",
            home.accessToken,
        )
        println("Raiz bajo el nodo 102 (101 ancestros) -> ${beyondCap.statusCode}: ${beyondCap.body?.take(200)}")

        val (cycle, path) = home.locationPathFrom(chain.last())
        println("Recorrido desde la hoja: ciclo=$cycle, longitud=${path.size}")

        withinCap.statusCode.shouldBe(HttpStatus.CONFLICT)
        beyondCap.statusCode.shouldBe(HttpStatus.CONFLICT)
        cycle.shouldBe(false)
    }

    @Test
    @DisplayName("jerarquia de 102 assets: el mismo tope, la misma pregunta")
    fun `el tope de profundidad del CTE en assets`() {
        val home = http.registerHousehold()
        val chain = home.assetChain(DEEP)
        val root = chain.first()

        val withinCap = http.patchJson(
            "/api/v1/assets/$root",
            """{"location":{"type":"ASSET","id":"${chain[100]}"}}""",
            home.accessToken,
        )
        println("Asset raiz bajo el nodo 101 -> ${withinCap.statusCode}: ${withinCap.body?.take(200)}")

        val beyondCap = http.patchJson(
            "/api/v1/assets/$root",
            """{"location":{"type":"ASSET","id":"${chain[101]}"}}""",
            home.accessToken,
        )
        println("Asset raiz bajo el nodo 102 -> ${beyondCap.statusCode}: ${beyondCap.body?.take(200)}")

        val (cycle, path) = home.assetPathFrom(chain.last())
        println("Recorrido desde la hoja: ciclo=$cycle, longitud=${path.size}")

        withinCap.statusCode.shouldBe(HttpStatus.CONFLICT)
        beyondCap.statusCode.shouldBe(HttpStatus.CONFLICT)
        cycle.shouldBe(false)
    }

    // ----------------------------------------------------------------------
    // 5. Por mi cuenta: las otras operaciones que tocan la ubicacion
    // ----------------------------------------------------------------------

    @Test
    @DisplayName("la entrada, la fusion y el alta no son otra via para colgar algo de si mismo")
    fun `las demas operaciones no mueven nada a su propio descendiente`() {
        val home = http.registerHousehold()
        val category = home.category("Herramientas")
        val article = home.createArticle("""{"name":"Azúcar ${UUID.randomUUID()}","categoryId":"$category","unit":"GRAM"}""")
        val box = home.createAsset(home.assetBody("Caja"))

        // La entrada solo alcanza a un DURABLE como contenedor.
        val intake = home.intakeInto(article, """{"type":"ASSET","id":"$box"}""")
        val stock = intake.body!!.extract("id")
        intake.statusCode.value().shouldBe(201)

        // Y un CONSUMABLE no puede contener nada: eso corta la jerarquia en el.
        val insideConsumable = http.postJson(
            "/api/v1/assets",
            """{"name":"Cuchara","type":"DURABLE","categoryId":"$category",
                "location":{"type":"ASSET","id":"$stock"}}""",
            home.accessToken,
        )
        insideConsumable.statusCode.shouldBe(HttpStatus.CONFLICT)
        insideConsumable.body!!.shouldContain("ASSET_LOCATION_CONFLICT")

        // Tampoco por entrada: la existencia no se mete dentro de otra existencia.
        home.intakeInto(article, """{"type":"ASSET","id":"$stock"}""")
            .let {
                println("Entrada dentro de una existencia -> ${it.statusCode}: ${it.body?.take(200)}")
                it.statusCode.shouldBe(HttpStatus.CONFLICT)
            }

        // Ni por PATCH de la propia existencia.
        http.patchJson(
            "/api/v1/assets/$stock",
            """{"location":{"type":"ASSET","id":"$stock"}}""",
            home.accessToken,
        ).let {
            println("Existencia dentro de si misma -> ${it.statusCode}: ${it.body?.take(200)}")
            it.statusCode.shouldBe(HttpStatus.CONFLICT)
        }

        // La fusion solo mueve cantidad, no jerarquia: exige dos existencias.
        val merge = http.postJson(
            "/api/v1/assets/$box/merge",
            """{"targetAssetId":"$stock"}""",
            home.accessToken,
        )
        merge.statusCode.shouldBe(HttpStatus.CONFLICT)
        merge.body!!.shouldContain("MERGE_NOT_CONSUMABLE")

        // El alta no puede referirse a si misma: el identificador lo pone el servidor.
        val ghost = UUID.randomUUID()
        http.postJson(
            "/api/v1/assets",
            """{"name":"Fantasma","type":"DURABLE","categoryId":"$category",
                "location":{"type":"ASSET","id":"$ghost"}}""",
            home.accessToken,
        ).statusCode.shouldBe(HttpStatus.NOT_FOUND)
        http.postJson(
            "/api/v1/locations",
            """{"name":"Fantasma","type":"ROOM","parentLocationId":"$ghost"}""",
            home.accessToken,
        ).statusCode.shouldBe(HttpStatus.NOT_FOUND)

        val (cycle, _) = home.assetPathFrom(stock)
        cycle.shouldBe(false)
    }

    /**
     * La lectura de `current` ocurre **antes** del cerrojo, y el `save` escribe
     * `current.copy(parentLocationId = newParentId)` aunque `movesInHierarchy`
     * sea falso. Un `PATCH` que no mueve nada --renombrar-- reescribe por tanto
     * el padre que leyo, sin cerrojo y sin comprobar el ciclo.
     *
     * La consecuencia directa y de dos peticiones es una **actualizacion
     * perdida**: si la reescritura cae despues del movimiento simultaneo, deshace
     * el movimiento que la API ya habia confirmado con un 200.
     */
    @Test
    @DisplayName("renombrar a la vez que se mueve: la reescritura del padre no comprueba nada")
    fun `el padre se reescribe sin cerrojo cuando el patch no lo menciona`() {
        val home = http.registerHousehold()
        val lost = mutableListOf<String>()

        repeat(LONG_ROUNDS) { round ->
            val target = home.createLocation("""{"name":"D$round","type":"OTHER"}""")
            val node = home.createLocation("""{"name":"M$round","type":"OTHER"}""")

            val codes = race(
                { home.moveLocation(node, target) },
                {
                    http.patchJson("/api/v1/locations/$node", """{"name":"M$round-bis"}""", home.accessToken)
                        .statusCode.value()
                },
            )

            val parent = home.parentOfLocation(node)
            if (codes[0] == 200 && parent != target) {
                lost += "ronda $round: mover devolvio 200 y el padre quedo en $parent"
            }
        }

        println("Movimientos confirmados y deshechos por el renombrado: ${lost.size}/$LONG_ROUNDS")
        lost.forEach(::println)
        lost.shouldBe(emptyList())
    }

    /**
     * La misma grieta, llevada al ciclo: hacen falta **tres** peticiones a la vez
     * porque con dos la reescritura solo puede devolver el arbol a un estado que
     * ya era valido.
     *
     * B cuelga de A. Una peticion sube B a raiz, otra mete A bajo B --legitimo en
     * cuanto B es raiz-- y la tercera reescribe el padre de B con el valor que
     * leyo, que era A. Si esa ultima escritura cae la ultima, quedan A→B y B→A.
     */
    @Test
    @DisplayName("tres peticiones, una de ellas sin mover: se busca el ciclo por la reescritura")
    fun `la reescritura del padre puede cerrar un ciclo con tres peticiones`() {
        val home = http.registerHousehold()
        val cycles = mutableListOf<String>()

        repeat(LONG_ROUNDS) { round ->
            val a = home.createLocation("""{"name":"A$round","type":"OTHER"}""")
            val b = home.createLocation("""{"name":"B$round","type":"OTHER","parentLocationId":"$a"}""")

            race(
                { home.promoteLocation(b) },
                { home.moveLocation(a, b) },
                { home.moveLocation(b, a) },
            )

            listOf(a, b).forEach { node ->
                val (cycle, path) = home.locationPathFrom(node)
                if (cycle) cycles += "ronda $round desde $node: ${path.joinToString(" -> ")}"
            }
            // Se deshace lo que quede para que la ronda siguiente empiece limpia.
            home.promoteLocation(a)
            home.promoteLocation(b)
        }

        println("Ciclos encontrados por la reescritura: ${cycles.size}/$LONG_ROUNDS rondas")
        cycles.forEach(::println)
        cycles.shouldBe(emptyList())
    }

    // ----------------------------------------------------------------------
    // Utillaje
    // ----------------------------------------------------------------------

    /** Lanza las llamadas a la vez de verdad: todas esperan al mismo pistoletazo. */
    private fun race(vararg calls: () -> Int): List<Int> {
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(calls.size)
        return try {
            val futures = calls.map { call ->
                pool.submit<Int> {
                    start.await()
                    call()
                }
            }
            start.countDown()
            futures.map { it.get(60, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
    }

    private fun TestHousehold.moveLocation(id: String, parent: String): Int =
        http.patchJson("/api/v1/locations/$id", """{"parentLocationId":"$parent"}""", accessToken)
            .statusCode.value()

    private fun TestHousehold.promoteLocation(id: String): Int =
        http.patchJson("/api/v1/locations/$id", """{"parentLocationId":null}""", accessToken)
            .statusCode.value()

    private fun TestHousehold.parentOfLocation(id: String): String? =
        json.readTree(http.getJson("/api/v1/locations/$id", accessToken).body!!)
            .get("parentLocationId")?.takeUnless { it.isNull }?.asText()

    private fun TestHousehold.moveAsset(id: String, container: String): Int =
        http.patchJson("/api/v1/assets/$id", """{"location":{"type":"ASSET","id":"$container"}}""", accessToken)
            .statusCode.value()

    /**
     * Sube por los padres hasta la raiz. Devuelve si encontro un ciclo y el
     * camino recorrido, que es la evidencia de que lo hay.
     */
    private fun TestHousehold.locationPathFrom(id: String): Pair<Boolean, List<String>> =
        pathFrom(id) { current ->
            val body = http.getJson("/api/v1/locations/$current", accessToken).body!!
            json.readTree(body).get("parentLocationId")?.takeUnless { it.isNull }?.asText()
        }

    private fun TestHousehold.assetPathFrom(id: String): Pair<Boolean, List<String>> =
        pathFrom(id) { current ->
            val body = http.getJson("/api/v1/assets/$current", accessToken).body!!
            val location = json.readTree(body).get("location")?.takeUnless { it.isNull }
            location?.takeIf { it.get("type").asText() == "ASSET" }?.get("id")?.asText()
        }

    private fun pathFrom(id: String, parentOf: (String) -> String?): Pair<Boolean, List<String>> {
        val path = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        var current: String? = id
        while (current != null) {
            path += current
            if (!seen.add(current)) return true to path
            if (path.size > WALK_LIMIT) return true to path
            current = parentOf(current)
        }
        return false to path
    }

    private fun TestHousehold.locationChain(depth: Int, prefix: String = "N"): List<String> {
        val ids = mutableListOf<String>()
        repeat(depth) { level ->
            val parent = ids.lastOrNull()
            val body = if (parent == null) {
                """{"name":"$prefix$level","type":"OTHER"}"""
            } else {
                """{"name":"$prefix$level","type":"OTHER","parentLocationId":"$parent"}"""
            }
            ids += createLocation(body)
        }
        return ids
    }

    private fun TestHousehold.assetChain(depth: Int, prefix: String = "N"): List<String> {
        val ids = mutableListOf<String>()
        repeat(depth) { level ->
            ids += createAsset(assetBody("$prefix$level", parent = ids.lastOrNull()))
        }
        return ids
    }

    private fun TestHousehold.assetBody(name: String, parent: String? = null): String {
        val location = parent?.let { ""","location":{"type":"ASSET","id":"$it"}""" }.orEmpty()
        return """{"name":"$name","type":"DURABLE","categoryId":"${category("Herramientas")}"$location}"""
    }

    private fun TestHousehold.intakeInto(articleId: String, location: String) = http.postJson(
        "/api/v1/assets/intake",
        """{"articleId":"$articleId","ownerId":"$memberId","quantity":100,"location":$location}""",
        accessToken,
    )

    private fun TestHousehold.createLocation(body: String): String {
        val created = http.postJson("/api/v1/locations", body, accessToken)
        created.statusCode.shouldBe(HttpStatus.CREATED)
        return created.body!!.extract("id")
    }

    private fun TestHousehold.createAsset(body: String): String {
        val created = http.postJson("/api/v1/assets", body, accessToken)
        created.statusCode.shouldBe(HttpStatus.CREATED)
        return created.body!!.extract("id")
    }

    private fun TestHousehold.createArticle(body: String): String {
        val created = http.postJson("/api/v1/articles", body, accessToken)
        created.statusCode.shouldBe(HttpStatus.CREATED)
        return created.body!!.extract("id")
    }

    /** Una de las cinco categorias que siembra el alta del hogar. */
    private fun TestHousehold.category(name: String): String {
        val body = http.getJson("/api/v1/categories", accessToken).body!!
        val entry = Regex("\\{[^{}]*\"name\":\"$name\"[^{}]*\\}").find(body)
            ?: error("No aparece la categoría sembrada «$name»:\n$body")
        return entry.value.extract("id")
    }

    private companion object {
        /** Rondas de cada carrera. Suficientes para que el solapamiento ocurra de verdad. */
        const val ROUNDS = 20

        /** Las carreras que buscan una ventana estrecha necesitan mas intentos. */
        const val LONG_ROUNDS = 60

        /**
         * Dos niveles mas que el tope del CTE: con 102 nodos, la raiz cae **fuera**
         * de los 100 ancestros que devuelve la consulta desde la hoja.
         */
        const val DEEP = 102

        const val WALK_LIMIT = 400
    }
}
