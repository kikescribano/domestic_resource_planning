package com.drp.adapter.persistence

import com.drp.test.DrpPostgres
import com.drp.test.count
import com.drp.test.execute
import com.drp.test.forgetHousehold
import com.drp.test.queryAll
import com.drp.test.queryOne
import com.drp.test.seedHousehold
import com.drp.test.useHousehold
import com.drp.test.SeededHousehold
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

    @Test
    @DisplayName("el esquema tiene las quince tablas del modelo, ni una mas")
    fun `el esquema tiene quince tablas`() {
        owner.queryAllTables().size.shouldBe(15)
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
