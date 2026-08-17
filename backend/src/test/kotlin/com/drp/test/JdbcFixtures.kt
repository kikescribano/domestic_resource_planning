package com.drp.test

import java.sql.Connection
import java.util.UUID

/**
 * Utilidades minimas de JDBC para las pruebas que hablan con el esquema en
 * crudo. Las pruebas de esquema y de RLS comprueban comportamiento de PostgreSQL
 * --politicas, indices, claves ajenas--, asi que van por JDBC directo y no por
 * el ORM: mezclar el mapeo con lo que se quiere medir solo anade una capa que
 * puede explicar un fallo sin ser la culpable.
 */

/**
 * Fija el hogar de la sesion.
 *
 * Es el equivalente a nivel de sesion del `SET LOCAL` que la aplicacion ejecuta
 * al abrir cada transaccion. Aqui se fija a nivel de sesion (`is_local = false`)
 * porque estas pruebas trabajan en autocommit, donde un `SET LOCAL` se perderia
 * al terminar la sentencia.
 */
fun Connection.useHousehold(householdId: UUID?) {
    prepareStatement("SELECT set_config('app.household_id', ?, false)").use { statement ->
        statement.setString(1, householdId?.toString() ?: "")
        statement.executeQuery().close()
    }
}

/** Deja el ajuste sin fijar en esta sesion. */
fun Connection.forgetHousehold() = execute("RESET app.household_id")

fun Connection.execute(sql: String, vararg parameters: Any?) {
    prepareStatement(sql).use { statement ->
        parameters.forEachIndexed { index, parameter -> statement.setObject(index + 1, parameter) }
        statement.execute()
    }
}

fun Connection.count(sql: String, vararg parameters: Any?): Int =
    queryOne(sql, *parameters) { it.getInt(1) } ?: 0

fun <T> Connection.queryOne(sql: String, vararg parameters: Any?, read: (java.sql.ResultSet) -> T): T? {
    prepareStatement(sql).use { statement ->
        parameters.forEachIndexed { index, parameter -> statement.setObject(index + 1, parameter) }
        statement.executeQuery().use { rows -> return if (rows.next()) read(rows) else null }
    }
}

fun <T> Connection.queryAll(sql: String, vararg parameters: Any?, read: (java.sql.ResultSet) -> T): List<T> {
    prepareStatement(sql).use { statement ->
        parameters.forEachIndexed { index, parameter -> statement.setObject(index + 1, parameter) }
        statement.executeQuery().use { rows ->
            val results = mutableListOf<T>()
            while (rows.next()) results += read(rows)
            return results
        }
    }
}

/** Un hogar sembrado con lo justo para poder comprobar aislamiento. */
data class SeededHousehold(
    val householdId: UUID,
    val identityId: UUID,
    val memberId: UUID,
    val categoryId: UUID,
)

/**
 * Siembra un hogar completo usando la conexion del propietario del esquema.
 *
 * Se usa el propietario a proposito: es superusuario, se salta las politicas y
 * puede preparar los dos hogares que la prueba necesita comparar. Lo que **no**
 * puede hacerse con esta conexion es comprobar aislamiento, porque no esta
 * sujeta a el.
 */
fun Connection.seedHousehold(name: String): SeededHousehold {
    val householdId = UUID.randomUUID()
    val identityId = UUID.randomUUID()
    val memberId = UUID.randomUUID()
    val categoryId = UUID.randomUUID()

    execute(
        "INSERT INTO households (id, name, time_zone) VALUES (?, ?, 'Europe/Madrid')",
        householdId,
        name,
    )
    execute(
        "INSERT INTO identities (id, name, email, password_hash) VALUES (?, ?, ?, '{argon2}fake')",
        identityId,
        "Persona de $name",
        "${UUID.randomUUID()}@example.test",
    )
    execute(
        "INSERT INTO household_members (id, household_id, identity_id, role) VALUES (?, ?, ?, 'HOUSEHOLD_ADMIN')",
        memberId,
        householdId,
        identityId,
    )
    execute(
        "INSERT INTO categories (id, household_id, name, created_by) VALUES (?, ?, 'Herramientas', ?)",
        categoryId,
        householdId,
        memberId,
    )

    return SeededHousehold(householdId, identityId, memberId, categoryId)
}

/**
 * Siembra un asset duradero prestado y el token acotado de su receptor externo.
 *
 * Hace falta para comprobar la resolucion de inquilino por token (V6), que es el
 * unico camino del core que empieza sin hogar **y** sin identidad: quien llega
 * con ese token no tiene cuenta. Se siembra con la conexion del propietario por
 * el mismo motivo que [seedHousehold] --preparar dos hogares exige saltarse las
 * politicas-- y por eso no sirve para comprobar aislamiento, solo para montarlo.
 *
 * @return el identificador del prestamo.
 */
fun Connection.seedLoanWithToken(household: SeededHousehold, tokenHash: String): UUID {
    val assetId = UUID.randomUUID()
    val loanId = UUID.randomUUID()

    execute(
        """
        INSERT INTO assets (id, household_id, category_id, name, type, owner_id, status, created_by)
        VALUES (?, ?, ?, 'Taladro', 'DURABLE', ?, 'LENT', ?)
        """.trimIndent(),
        assetId,
        household.householdId,
        household.categoryId,
        household.memberId,
        household.memberId,
    )
    execute(
        """
        INSERT INTO loans (id, household_id, asset_id, lender_member_id, borrower_external, status, created_by)
        VALUES (?, ?, ?, ?, ?::jsonb, 'ACTIVE', ?)
        """.trimIndent(),
        loanId,
        household.householdId,
        assetId,
        household.memberId,
        """{"name": "Vecino del 3.º", "email": "vecino@example.test"}""",
        household.memberId,
    )
    execute(
        """
        INSERT INTO loan_access_tokens (id, loan_id, token_hash, role, expires_at)
        VALUES (?, ?, ?, 'BORROWER', now() + interval '90 days')
        """.trimIndent(),
        UUID.randomUUID(),
        loanId,
        tokenHash,
    )

    return loanId
}
