package com.drp.core.adapter.persistence

import com.drp.test.DrpPostgres
import com.drp.test.count
import com.drp.test.execute
import com.drp.test.forgetHousehold
import com.drp.test.queryAll
import com.drp.test.queryOne
import com.drp.test.seedHousehold
import com.drp.test.seedLoanWithToken
import com.drp.test.useHousehold
import com.drp.test.SeededHousehold
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import java.sql.Connection
import java.sql.SQLException
import java.util.UUID

/**
 * La segunda capa de aislamiento, comprobada contra PostgreSQL real (ADR-003).
 *
 * Todo lo de aqui corre con el usuario de la aplicacion, que no es superusuario
 * ni tiene `BYPASSRLS`. Es la condicion que hace que estas pruebas signifiquen
 * algo: con un superusuario pasarian todas sin que hubiese aislamiento.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RowLevelSecurityTest {

    private val postgres = DrpPostgres.instance

    private lateinit var owner: Connection
    private lateinit var application: Connection
    private lateinit var householdA: SeededHousehold
    private lateinit var householdB: SeededHousehold

    @BeforeAll
    fun prepare() {
        owner = postgres.ownerConnection()
        application = postgres.appConnection()
        householdA = owner.seedHousehold("Hogar A")
        householdB = owner.seedHousehold("Hogar B")
    }

    @AfterAll
    fun close() {
        owner.close()
        application.close()
    }

    @Test
    @DisplayName("el usuario de la aplicacion no es superusuario ni tiene BYPASSRLS")
    fun `el usuario de la aplicacion esta sujeto a las politicas`() {
        val privileged = application.queryOne(
            "SELECT rolsuper OR rolbypassrls FROM pg_roles WHERE rolname = current_user",
        ) { it.getBoolean(1) }

        privileged.shouldBe(false)
    }

    @Test
    @DisplayName("autenticado como hogar A no se ve ninguna fila del hogar B")
    fun `un hogar no ve las filas de otro`() {
        application.useHousehold(householdA.householdId)

        application.count("SELECT count(*) FROM households").shouldBe(1)
        application.count("SELECT count(*) FROM household_members").shouldBe(1)
        application.count("SELECT count(*) FROM categories").shouldBe(1)

        application.queryOne("SELECT id FROM households") { it.getObject(1, UUID::class.java) }
            .shouldBe(householdA.householdId)
    }

    @Test
    @DisplayName("ni siquiera preguntando por el identificador directo de una fila ajena")
    fun `el identificador directo tampoco alcanza`() {
        application.useHousehold(householdA.householdId)

        application.count("SELECT count(*) FROM households WHERE id = ?", householdB.householdId).shouldBe(0)
        application.count("SELECT count(*) FROM categories WHERE id = ?", householdB.categoryId).shouldBe(0)
        application.count(
            "SELECT count(*) FROM household_members WHERE id = ?",
            householdB.memberId,
        ).shouldBe(0)
    }

    @Test
    @DisplayName("sin contexto de inquilino no se ve nada, y no revienta")
    fun `sin contexto la peticion falla cerrada`() {
        application.forgetHousehold()

        application.count("SELECT count(*) FROM households").shouldBe(0)
        application.count("SELECT count(*) FROM categories").shouldBe(0)
    }

    @Test
    @DisplayName("con el ajuste en cadena vacia tampoco, que es lo que deja un RESET")
    fun `la cadena vacia no produce error de conversion`() {
        // Es el caso que rompe la version directa de la politica: ''::uuid no es
        // convertible, asi que current_setting(...)::uuid a secas lanzaria un
        // error en lugar de denegar. Con nullif la comparacion da NULL y no pasa
        // ninguna fila.
        application.useHousehold(null)

        application.count("SELECT count(*) FROM households").shouldBe(0)
        application.count("SELECT count(*) FROM categories").shouldBe(0)
    }

    @Test
    @DisplayName("no se puede escribir en un hogar que no es el de la sesion")
    fun `la politica tambien alcanza a las escrituras`() {
        application.useHousehold(householdA.householdId)

        // Sin WITH CHECK propio, PostgreSQL aplica la expresion de USING tambien
        // a las filas nuevas: insertar en el hogar B estando en el A se rechaza.
        assertThrows<SQLException> {
            application.execute(
                "INSERT INTO categories (id, household_id, name) VALUES (?, ?, 'Intrusa')",
                UUID.randomUUID(),
                householdB.householdId,
            )
        }

        owner.count(
            "SELECT count(*) FROM categories WHERE household_id = ? AND name = 'Intrusa'",
            householdB.householdId,
        ).shouldBe(0)
    }

    @Test
    @DisplayName("un UPDATE no puede alcanzar una fila ajena: no la ve, asi que no la toca")
    fun `un update no alcanza una fila ajena`() {
        application.useHousehold(householdA.householdId)

        application.execute("UPDATE categories SET name = 'Robada' WHERE id = ?", householdB.categoryId)

        owner.queryOne("SELECT name FROM categories WHERE id = ?", householdB.categoryId) { it.getString(1) }
            .shouldBe("Herramientas")
    }

    @Test
    @DisplayName("toda tabla con household_id lleva RLS activado, forzado y con politica")
    fun `ninguna tabla del core se queda sin politica`() {
        val offenders = owner.queryAllTables()
            .filter { it.needsPolicy }
            .filterNot { it.rowSecurity && it.forced && it.policies > 0 }

        offenders.map { it.name }.shouldBe(emptyList())
    }

    @Test
    @DisplayName("las cinco tablas sin politica son exactamente las documentadas")
    fun `las excepciones son las cinco de siempre`() {
        // Si alguien anade una tabla nueva sin household_id, aparece aqui y hay
        // que decidir a proposito si es una excepcion legitima o un descuido.
        val withoutPolicy = owner.queryAllTables()
            .filterNot { it.needsPolicy }
            .map { it.name }
            .sorted()

        withoutPolicy.shouldBe(
            listOf(
                "email_verification_tokens",
                "identities",
                "loan_access_tokens",
                "password_reset_tokens",
                "refresh_tokens",
            ),
        )
    }

    /**
     * Cuenta solo `public`, que es donde vive el modelo. El modulo de prueba del
     * Hito 0 crea la suya en un esquema propio a proposito: una tabla de mentira
     * en `public` obligaria a excluirla por nombre aqui, y esa exclusion taparia
     * ademas cualquier tabla nueva que alguien olvidara.
     */
    @Test
    @DisplayName("el esquema tiene las veintinueve tablas del modelo, ni una mas")
    fun `el esquema tiene veintinueve tablas`() {
        // Quince del core, dos de plataforma --`household_modules`, de la
        // activacion de modulos del Hito 0 (ADR-010), y `household_notices`, de
        // la plataforma de avisos del Hito 1 (ADR-011)-- y **once de los cuatro
        // modulos de verdad**: `suppliers` y `supplier_links` del Hito 2, las
        // cuatro de Warehouse del Hito 3 --`warehouse_articles`,
        // `warehouse_locations`, `warehouse_lots` y `warehouse_movements`--, las
        // dos de Compras del Hito 4 --`purchases` y `shopping_list_items`-- y las
        // tres de Mantenimiento del Hito 5: `maintenance_items`,
        // `maintenance_plans` y `maintenance_interventions`.
        //
        // Las once llevan `household_id`, RLS y `FORCE`, asi que ninguna aparece
        // en la lista de excepciones de mas arriba: **este recuento sube y el
        // otro no**, que es justamente lo que hay que mirar cuando un modulo trae
        // tablas. Se actualiza a proposito y no para que pase.
        //
        // La vigesimonovena es `event_outbox`, la tercera de plataforma: la cola
        // del Transactional Outbox del Hito 1 del cierre de huecos (ADR-013).
        // Lleva `household_id`, RLS y `FORCE` como cualquier otra --y aqui la
        // politica hace mas trabajo que en casi ninguna, porque quien la lee no
        // nace de una peticion--, asi que tampoco toca la lista de las cinco.
        owner.queryAllTables().size.shouldBe(29)
    }

    @Test
    @DisplayName("la resolucion de inquilino no corre con superusuario ni con BYPASSRLS")
    fun `la excepcion de aislamiento no descansa en un privilegio`() {
        // Es la propiedad de la que depende que la grieta deliberada sea
        // estrecha, y no se ve en ninguna parte del codigo: una funcion SECURITY
        // DEFINER se ejecuta con los privilegios de su PROPIETARIO, asi que si
        // ese propietario fuese superusuario, las tres funciones tendrian acceso
        // total a la base en lugar del acceso de solo lectura a tres tablas que
        // sus politicas les conceden.
        //
        // Sin esta prueba, cambiar el dueno de una funcion --o dejarlo por
        // omision en quien ejecute la migracion-- ampliaria el radio de la
        // excepcion sin que nada fallara.
        val owners = owner.queryAll(
            """
            SELECT p.proname, r.rolname, r.rolsuper, r.rolbypassrls
            FROM pg_proc p
            JOIN pg_roles r ON r.oid = p.proowner
            JOIN pg_namespace n ON n.oid = p.pronamespace
            WHERE n.nspname = 'public'
              AND p.prosecdef
            ORDER BY p.proname
            """.trimIndent(),
        ) { Triple(it.getString(1), it.getString(2), it.getBoolean(3) || it.getBoolean(4)) }

        owners.map { it.first }.shouldBe(
            listOf(
                "find_household_for_active_member",
                "find_household_for_invitation_token",
                "find_household_for_loan_token",
                "list_household_ids",
                // La quinta, con la baja de hogar (ADR-012): en que hogares
                // consta una persona, para saber si se queda sin ninguna
                // pertenencia al purgar uno. Entra en esta lista **a mano y a
                // proposito**: una funcion `SECURITY DEFINER` nueva tiene que
                // costar una linea aqui, porque es la unica grieta deliberada
                // del aislamiento y crecer sin que nadie lo note es justo lo que
                // esta prueba impide.
                "list_households_for_identity",
                // Y la sexta, con el Transactional Outbox (ADR-013): que hogares
                // tienen entregas pendientes, para que el relay no recorra en
                // vacio mil hogares cada pocos segundos. Cumple las tres
                // propiedades de la familia --solo identificadores de hogar,
                // pregunta cerrada y de `drp_resolver`-- y entra en esta lista
                // por la misma razon que la anterior: la unica grieta deliberada
                // del aislamiento no puede crecer sin que nadie lo note.
                "list_households_with_pending_events",
            ),
        )
        owners.forEach { (function, role, privileged) ->
            withClue("$function la posee $role, que es superusuario o tiene BYPASSRLS") {
                privileged.shouldBeFalse()
            }
        }
    }

    @Test
    @DisplayName("y aun asi resuelven: la puerta se la abre una politica, no un privilegio")
    fun `la resolucion funciona sin privilegios`() {
        // La otra mitad de la prueba anterior. Que el propietario no tenga
        // privilegios no serviria de nada si las funciones hubiesen dejado de
        // funcionar: lo que se comprueba junto es que resuelven **y** que lo
        // hacen por las politicas de SELECT que la V5 les concede.
        application.useHousehold(null)

        val households = application.queryAll("SELECT * FROM list_household_ids()") {
            it.getObject(1, UUID::class.java)
        }
        val resolved = application.queryOne(
            "SELECT find_household_for_active_member(?)",
            householdA.identityId,
        ) { it.getObject(1, UUID::class.java) }

        households.contains(householdA.householdId).shouldBe(true)
        resolved.shouldBe(householdA.householdId)

        // Y lo que devuelven son identificadores, no acceso: leer el hogar sigue
        // exigiendo contexto.
        application.count("SELECT count(*) FROM households").shouldBe(0)
    }

    @Test
    @DisplayName("el token acotado de un prestamo resuelve su hogar sin contexto previo")
    fun `el token de prestamo resuelve el hogar y nada mas`() {
        // El cuarto momento en el que todavia no se sabe cual es el hogar (V6).
        // Tiene una vuelta que los otros tres no tienen: `loan_access_tokens` no
        // lleva `household_id` --por eso es una de las cinco tablas sin
        // politica-- asi que el identificador hay que ir a buscarlo al prestamo,
        // que si esta protegido.
        val tokenHash = "hash-de-prueba-${UUID.randomUUID()}"
        owner.seedLoanWithToken(householdA, tokenHash)

        application.useHousehold(null)

        val resolved = application.queryOne(
            "SELECT find_household_for_loan_token(?)",
            tokenHash,
        ) { it.getObject(1, UUID::class.java) }

        resolved.shouldBe(householdA.householdId)

        // Y lo de siempre: lo que devuelve es un identificador, no acceso. Sin
        // fijar el contexto, el prestamo cuyo hogar acaba de resolver sigue sin
        // verse. Si esto dejara de ser cierto, la funcion habria pasado de
        // resolver el aislamiento a esquivarlo.
        application.count("SELECT count(*) FROM loans").shouldBe(0)
    }

    @Test
    @DisplayName("un hash que no existe resuelve a nulo, sin distinguir de uno ajeno")
    fun `un hash desconocido no resuelve nada`() {
        application.useHousehold(null)

        application.queryOne(
            "SELECT find_household_for_loan_token(?)",
            "hash-que-nadie-emitio",
        ) { it.getObject(1, UUID::class.java) }.shouldBe(null)
    }

    @Test
    @DisplayName("con contexto de otro hogar, el prestamo ajeno sigue sin verse")
    fun `resolver el hogar no abre la puerta al prestamo`() {
        val tokenHash = "hash-ajeno-${UUID.randomUUID()}"
        val loanId = owner.seedLoanWithToken(householdB, tokenHash)

        // Alguien que tuviera el token de un prestamo del hogar B y una sesion
        // legitima en el hogar A no puede combinarlos: la politica de `loans`
        // sigue mirando el contexto, no el token.
        application.useHousehold(householdA.householdId)

        application.count("SELECT count(*) FROM loans WHERE id = ?", loanId).shouldBe(0)
    }

    @Test
    @DisplayName("identities queda fuera de RLS, que es justo por lo que su repositorio no puede listar")
    fun `identities no tiene politica`() {
        val identities = owner.queryAllTables().single { it.name == "identities" }

        identities.rowSecurity.shouldBeFalse()
        identities.policies.shouldBe(0)
    }
}

private data class TableSecurity(
    val name: String,
    val rowSecurity: Boolean,
    val forced: Boolean,
    val policies: Int,
    val needsPolicy: Boolean,
)

/**
 * Estado de RLS de cada tabla del esquema, leido del catalogo.
 *
 * Se pregunta al catalogo y no a una lista escrita a mano para que una tabla
 * nueva entre sola en la comprobacion: una lista se queda desfasada en silencio,
 * y este es justo el sitio donde eso no puede pasar.
 */
private fun Connection.queryAllTables(): List<TableSecurity> =
    queryAll(
        """
        SELECT c.relname,
               c.relrowsecurity,
               c.relforcerowsecurity,
               (SELECT count(*) FROM pg_policy p WHERE p.polrelid = c.oid) AS policies,
               (c.relname = 'households'
                   OR EXISTS (SELECT 1 FROM pg_attribute a
                              WHERE a.attrelid = c.oid
                                AND a.attname = 'household_id'
                                AND a.attnum > 0
                                AND NOT a.attisdropped)) AS needs_policy
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public'
          AND c.relkind = 'r'
          AND c.relname <> 'flyway_schema_history'
        ORDER BY c.relname
        """.trimIndent(),
    ) {
        TableSecurity(
            name = it.getString(1),
            rowSecurity = it.getBoolean(2),
            forced = it.getBoolean(3),
            policies = it.getInt(4),
            needsPolicy = it.getBoolean(5),
        )
    }
