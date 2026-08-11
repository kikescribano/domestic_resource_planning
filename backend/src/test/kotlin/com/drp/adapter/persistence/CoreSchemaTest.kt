package com.drp.adapter.persistence

import com.drp.test.DrpPostgres
import com.drp.test.SeededHousehold
import com.drp.test.count
import com.drp.test.execute
import com.drp.test.seedHousehold
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
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
 * Lo que el esquema garantiza por si mismo: `CHECK`, indices unicos parciales y
 * claves ajenas compuestas.
 *
 * Corre con el **propietario del esquema**, que es superusuario y se salta las
 * politicas de RLS. No es un descuido sino el planteamiento: aqui se comprueba
 * que la base de datos rechaza estas cosas **aunque la primera capa fallara y
 * aunque la segunda no estuviera puesta**. Si estas pruebas corriesen sujetas a
 * RLS, la politica denegaria antes de que la restriccion llegara a opinar y no
 * se sabria cual de las dos capas esta funcionando.
 *
 * El aislamiento propiamente dicho se comprueba en [RowLevelSecurityTest], que
 * si corre con el usuario de la aplicacion.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CoreSchemaTest {

    private val postgres = DrpPostgres.instance

    private lateinit var connection: Connection
    private lateinit var home: SeededHousehold
    private lateinit var neighbour: SeededHousehold

    @BeforeAll
    fun prepare() {
        connection = postgres.ownerConnection()
        home = connection.seedHousehold("Esquema propio")
        neighbour = connection.seedHousehold("Esquema vecino")
    }

    @AfterAll
    fun close() = connection.close()

    // -----------------------------------------------------------------------
    // Nombres normalizados: es lo que exige la extension unaccent
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("dos categorias que solo difieren en acento y caja chocan")
    fun `el nombre de categoria se compara sin acentos ni mayusculas`() {
        connection.insertCategory(home, "Jardín")

        val duplicate = assertThrows<SQLException> { connection.insertCategory(home, "JARDIN") }

        duplicate.message!!.shouldContain("categories_name_unique_live")
    }

    @Test
    @DisplayName("retirar una categoria libera su nombre, porque el indice es parcial")
    fun `una categoria retirada no ocupa el nombre`() {
        val retired = connection.insertCategory(home, "Bricolaje")
        connection.execute("UPDATE categories SET retired_at = now() WHERE id = ?", retired)

        connection.insertCategory(home, "bricolaje")

        connection.count(
            "SELECT count(*) FROM categories WHERE household_id = ? AND lower(name) = 'bricolaje'",
            home.householdId,
        ).shouldBe(2)
    }

    @Test
    @DisplayName("el mismo nombre en dos hogares distintos no choca")
    fun `la unicidad del nombre es por hogar`() {
        connection.insertCategory(home, "Camping")
        connection.insertCategory(neighbour, "Camping")
    }

    // -----------------------------------------------------------------------
    // Autoria: la clave ajena compuesta
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("la clave ajena compuesta rechaza atribuir un cambio a alguien de otro hogar")
    fun `la autoria no cruza de hogar`() {
        val rejected = assertThrows<SQLException> {
            connection.execute(
                "INSERT INTO categories (id, household_id, name, created_by) VALUES (?, ?, 'Ajena', ?)",
                UUID.randomUUID(),
                home.householdId,
                neighbour.memberId,
            )
        }

        rejected.message!!.shouldContain("categories_created_by_same_household")
    }

    @Test
    @DisplayName("la autoria a nulo se admite: es el cambio que hizo el sistema y no una persona")
    fun `la autoria admite nulo`() {
        connection.execute(
            "INSERT INTO categories (id, household_id, name, created_by) VALUES (?, ?, 'Del sistema', NULL)",
            UUID.randomUUID(),
            home.householdId,
        )
    }

    // -----------------------------------------------------------------------
    // Pertenencia
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("una identidad no puede tener dos pertenencias activas mientras dure el MVP")
    fun `solo una pertenencia activa por identidad`() {
        val rejected = assertThrows<SQLException> {
            connection.execute(
                "INSERT INTO household_members (id, household_id, identity_id, role) " +
                    "VALUES (?, ?, ?, 'HOUSEHOLD_MEMBER')",
                UUID.randomUUID(),
                neighbour.householdId,
                home.identityId,
            )
        }

        rejected.message!!.shouldContain("household_members_single_active_membership")
    }

    @Test
    @DisplayName("dejar un hogar libera a la identidad para entrar en otro")
    fun `una pertenencia dada de baja no bloquea`() {
        val leaver = connection.insertIdentity()
        val membership = connection.insertMember(home, leaver)
        connection.execute("UPDATE household_members SET deactivated_at = now() WHERE id = ?", membership)

        connection.insertMember(neighbour, leaver)
    }

    @Test
    @DisplayName("el correo es unico en toda la instalacion y no distingue mayusculas")
    fun `el correo se compara en minusculas`() {
        val email = "Kike@Example.test"
        connection.insertIdentity(email)

        val rejected = assertThrows<SQLException> { connection.insertIdentity(email.lowercase()) }

        rejected.message!!.shouldContain("identities_email_unique")
    }

    // -----------------------------------------------------------------------
    // Invitaciones
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("solo una invitacion viva por correo y hogar")
    fun `no hay dos invitaciones vivas para el mismo correo`() {
        connection.insertInvitation(home, "vecino@example.test")

        val rejected = assertThrows<SQLException> {
            connection.insertInvitation(home, "VECINO@example.test")
        }

        rejected.message!!.shouldContain("invitations_one_live_per_email")
    }

    @Test
    @DisplayName("revocar una invitacion permite volver a invitar al mismo correo")
    fun `una invitacion revocada no bloquea`() {
        val revoked = connection.insertInvitation(home, "arrepentido@example.test")
        connection.execute("UPDATE invitations SET revoked_at = now() WHERE id = ?", revoked)

        connection.insertInvitation(home, "arrepentido@example.test")
    }

    // -----------------------------------------------------------------------
    // Assets: las dos naturalezas y el indice de existencias
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("un DURABLE no admite cantidad y un CONSUMABLE la exige junto al articulo")
    fun `la cantidad va con la naturaleza del asset`() {
        val article = connection.insertArticle(home, "Azucar")

        assertThrows<SQLException> {
            connection.execute(
                "INSERT INTO assets (id, household_id, name, category_id, type, status, quantity) " +
                    "VALUES (?, ?, 'Taladro', ?, 'DURABLE', 'AVAILABLE', 3)",
                UUID.randomUUID(),
                home.householdId,
                home.categoryId,
            )
        }

        assertThrows<SQLException> {
            connection.execute(
                "INSERT INTO assets (id, household_id, article_id, type, status, quantity) " +
                    "VALUES (?, ?, ?, 'CONSUMABLE', 'AVAILABLE', NULL)",
                UUID.randomUUID(),
                home.householdId,
                article,
            )
        }
    }

    @Test
    @DisplayName("un consumible nunca esta prestado")
    fun `solo un duradero puede estar prestado`() {
        val article = connection.insertArticle(home, "Detergente")

        val rejected = assertThrows<SQLException> {
            connection.execute(
                "INSERT INTO assets (id, household_id, article_id, type, status, quantity) " +
                    "VALUES (?, ?, ?, 'CONSUMABLE', 'LENT', 1)",
                UUID.randomUUID(),
                home.householdId,
                article,
            )
        }

        rejected.message!!.shouldContain("assets_only_durable_is_lent")
    }

    @Test
    @DisplayName("una sola existencia viva por articulo y ubicacion, tambien sin ubicar")
    fun `el indice de existencias cubre la ubicacion sin asignar`() {
        val article = connection.insertArticle(home, "Arroz")
        connection.insertStockItem(home, article, quantity = 2)

        // El NULLS NOT DISTINCT es lo que hace que esto choque: sin el, cada
        // entrada sin ubicar crearia una fila nueva.
        val rejected = assertThrows<SQLException> {
            connection.insertStockItem(home, article, quantity = 5)
        }

        rejected.message!!.shouldContain("assets_live_stock_item_unique")
    }

    @Test
    @DisplayName("una existencia dada de baja no bloquea su ubicacion para siempre")
    fun `dar de baja una existencia libera su hueco`() {
        val article = connection.insertArticle(home, "Lentejas")
        val first = connection.insertStockItem(home, article, quantity = 1)

        connection.execute(
            "UPDATE assets SET status = 'DECOMMISSIONED', quantity = 0 WHERE id = ?",
            first,
        )

        // Sin la exclusion de DECOMMISSIONED en el indice, ningun
        // RegisterConsumableIntake posterior podria volver a usar esa ubicacion.
        connection.insertStockItem(home, article, quantity = 4)
    }

    // -----------------------------------------------------------------------
    // Ubicaciones
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("dos ubicaciones raiz no pueden llamarse igual, y dos hermanas tampoco")
    fun `el nombre de ubicacion es unico entre hermanas`() {
        connection.insertLocation(home, "Trastero", parent = null)

        val rejected = assertThrows<SQLException> {
            connection.insertLocation(home, "trastero", parent = null)
        }
        rejected.message!!.shouldContain("locations_name_unique_among_siblings")

        // Pero el mismo nombre en otro padre si vale: dos armarios pueden
        // llamarse igual en cuartos distintos.
        val kitchen = connection.insertLocation(home, "Cocina", parent = null)
        val hall = connection.insertLocation(home, "Pasillo", parent = null)
        connection.insertLocation(home, "Armario", parent = kitchen)
        connection.insertLocation(home, "Armario", parent = hall)
    }

    // -----------------------------------------------------------------------
    // Documentos
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("un documento cuelga de exactamente un destino y tiene exactamente un contenido")
    fun `el documento exige un destino y un contenido`() {
        val article = connection.insertArticle(home, "Lavadora")

        // Sin destino.
        assertThrows<SQLException> {
            connection.execute(
                "INSERT INTO documents (id, household_id, type, url) VALUES (?, ?, 'MANUAL', 'https://x.test/m')",
                UUID.randomUUID(),
                home.householdId,
            )
        }

        // Sin contenido.
        assertThrows<SQLException> {
            connection.execute(
                "INSERT INTO documents (id, household_id, article_id, type) VALUES (?, ?, ?, 'MANUAL')",
                UUID.randomUUID(),
                home.householdId,
                article,
            )
        }

        connection.execute(
            "INSERT INTO documents (id, household_id, article_id, type, url) " +
                "VALUES (?, ?, ?, 'MANUAL', 'https://x.test/m')",
            UUID.randomUUID(),
            home.householdId,
            article,
        )
    }

    @Test
    @DisplayName("una garantia no puede caducar antes de emitirse")
    fun `la validez no precede a la fecha`() {
        val article = connection.insertArticle(home, "Horno")

        val rejected = assertThrows<SQLException> {
            connection.execute(
                "INSERT INTO documents (id, household_id, article_id, type, url, date, valid_until) " +
                    "VALUES (?, ?, ?, 'WARRANTY', 'https://x.test/g', DATE '2026-05-01', DATE '2026-01-01')",
                UUID.randomUUID(),
                home.householdId,
                article,
            )
        }

        rejected.message!!.shouldContain("documents_valid_until_after_date")
    }

    // -----------------------------------------------------------------------
    // Prestamos
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("un prestamo vencido sigue ocupando el asset")
    fun `no hay dos prestamos abiertos sobre el mismo asset`() {
        val asset = connection.insertDurable(home, "Taladro prestable")
        connection.insertLoan(home, asset, status = "OVERDUE")

        val rejected = assertThrows<SQLException> {
            connection.insertLoan(home, asset, status = "ACTIVE")
        }

        rejected.message!!.shouldContain("loans_one_open_per_asset")
    }

    @Test
    @DisplayName("un externo sin forma de contactarlo no vale: el enlace del token tiene que llegarle")
    fun `el contacto externo exige nombre y un canal`() {
        val asset = connection.insertDurable(home, "Escalera")

        val rejected = assertThrows<SQLException> {
            connection.execute(
                "INSERT INTO loans (id, household_id, asset_id, lender_member_id, borrower_external, status) " +
                    "VALUES (?, ?, ?, ?, '{\"name\": \"Vecino\"}'::jsonb, 'ACTIVE')",
                UUID.randomUUID(),
                home.householdId,
                asset,
                home.memberId,
            )
        }

        rejected.message!!.shouldContain("loans_borrower_external_contactable")
    }
}

// ---------------------------------------------------------------------------
// Insercciones de apoyo
// ---------------------------------------------------------------------------

private fun Connection.insertCategory(household: SeededHousehold, name: String): UUID {
    val id = UUID.randomUUID()
    execute(
        "INSERT INTO categories (id, household_id, name) VALUES (?, ?, ?)",
        id,
        household.householdId,
        name,
    )
    return id
}

private fun Connection.insertIdentity(email: String = "${UUID.randomUUID()}@example.test"): UUID {
    val id = UUID.randomUUID()
    execute(
        "INSERT INTO identities (id, name, email, password_hash) VALUES (?, 'Alguien', ?, '{argon2}fake')",
        id,
        email,
    )
    return id
}

private fun Connection.insertMember(household: SeededHousehold, identityId: UUID): UUID {
    val id = UUID.randomUUID()
    execute(
        "INSERT INTO household_members (id, household_id, identity_id, role) VALUES (?, ?, ?, 'HOUSEHOLD_MEMBER')",
        id,
        household.householdId,
        identityId,
    )
    return id
}

private fun Connection.insertInvitation(household: SeededHousehold, email: String): UUID {
    val id = UUID.randomUUID()
    execute(
        "INSERT INTO invitations (id, household_id, email, role, token_hash, expires_at) " +
            "VALUES (?, ?, ?, 'HOUSEHOLD_MEMBER', ?, now() + interval '7 days')",
        id,
        household.householdId,
        email,
        UUID.randomUUID().toString(),
    )
    return id
}

private fun Connection.insertArticle(household: SeededHousehold, name: String): UUID {
    val id = UUID.randomUUID()
    execute(
        "INSERT INTO articles (id, household_id, category_id, name, unit) VALUES (?, ?, ?, ?, 'UNIT')",
        id,
        household.householdId,
        household.categoryId,
        name,
    )
    return id
}

private fun Connection.insertStockItem(household: SeededHousehold, articleId: UUID, quantity: Int): UUID {
    val id = UUID.randomUUID()
    execute(
        "INSERT INTO assets (id, household_id, article_id, type, status, quantity) " +
            "VALUES (?, ?, ?, 'CONSUMABLE', 'AVAILABLE', ?)",
        id,
        household.householdId,
        articleId,
        quantity,
    )
    return id
}

private fun Connection.insertDurable(household: SeededHousehold, name: String): UUID {
    val id = UUID.randomUUID()
    execute(
        "INSERT INTO assets (id, household_id, name, category_id, type, status) " +
            "VALUES (?, ?, ?, ?, 'DURABLE', 'AVAILABLE')",
        id,
        household.householdId,
        name,
        household.categoryId,
    )
    return id
}

private fun Connection.insertLocation(household: SeededHousehold, name: String, parent: UUID?): UUID {
    val id = UUID.randomUUID()
    execute(
        "INSERT INTO locations (id, household_id, name, type, parent_location_id) VALUES (?, ?, ?, 'ROOM', ?)",
        id,
        household.householdId,
        name,
        parent,
    )
    return id
}

private fun Connection.insertLoan(household: SeededHousehold, assetId: UUID, status: String): UUID {
    val id = UUID.randomUUID()
    execute(
        "INSERT INTO loans (id, household_id, asset_id, lender_member_id, borrower_external, status) " +
            "VALUES (?, ?, ?, ?, '{\"name\": \"Vecino\", \"email\": \"v@example.test\"}'::jsonb, ?)",
        id,
        household.householdId,
        assetId,
        household.memberId,
        status,
    )
    return id
}
