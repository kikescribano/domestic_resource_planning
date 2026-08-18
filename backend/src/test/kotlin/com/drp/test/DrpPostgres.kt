package com.drp.test

import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.sql.Connection
import java.sql.DriverManager

/**
 * Contenedor de PostgreSQL con el mismo corte de privilegios que el despliegue.
 *
 * Es la pieza que impide la cobertura falsa. Toda la segunda capa de aislamiento
 * (ADR-003) depende de que la aplicacion conecte con un usuario **sujeto a RLS**:
 * probar como superusuario haria pasar todas las pruebas de aislamiento sin que
 * hubiese aislamiento ninguno, porque un superusuario se salta las politicas
 * pase lo que pase.
 *
 * Por eso el rol de la aplicacion no se crea aqui a mano sino ejecutando **el
 * fichero real** `docker/postgres/init/01-app-role.sql`, el mismo que inicializa
 * el contenedor de `compose.yaml`. Asi el entorno de pruebas y el de desarrollo
 * no pueden separarse sin que alguien lo note: si alguien anadiese `BYPASSRLS`
 * al rol, las pruebas de aislamiento empezarian a fallar en el acto.
 *
 * El contenedor se comparte entre todas las clases de prueba --arrancar uno por
 * clase multiplicaria el tiempo de construccion sin comprobar nada mas-- y muere
 * con la JVM.
 */
class DrpPostgres : PostgreSQLContainer<DrpPostgres>(DockerImageName.parse(IMAGE)) {
    companion object {
        private const val IMAGE = "postgres:16-alpine"

        /** Propietario del esquema: es quien ejecuta las migraciones de Flyway. */
        const val OWNER_USERNAME = "drp_owner"
        const val OWNER_PASSWORD = "drp_owner"

        /** Usuario de la aplicacion: NOSUPERUSER y NOBYPASSRLS. */
        const val APP_USERNAME = "drp_app"
        const val APP_PASSWORD = "drp_app"

        private const val APP_ROLE_SCRIPT = "docker/postgres/init/01-app-role.sql"

        val instance: DrpPostgres by lazy {
            DrpPostgres()
                .withDatabaseName("drp")
                .withUsername(OWNER_USERNAME)
                .withPassword(OWNER_PASSWORD)
                .withCopyFileToContainer(
                    MountableFile.forHostPath(appRoleScript()),
                    "/docker-entrypoint-initdb.d/01-app-role.sql",
                )
                .also {
                    it.start()
                    it.migrate()
                    it.createTestbedSchema()
                }
        }

        /**
         * Localiza el script del rol subiendo desde el directorio de trabajo. Se
         * busca en lugar de fijar una ruta relativa porque Gradle lanza las
         * pruebas desde `backend/` y no desde la raiz del repositorio.
         */
        private fun appRoleScript(): Path {
            var directory: Path? = Paths.get("").toAbsolutePath()
            while (directory != null) {
                val candidate = directory.resolve(APP_ROLE_SCRIPT)
                if (Files.isRegularFile(candidate)) return candidate
                directory = directory.parent
            }
            error("No se encuentra $APP_ROLE_SCRIPT subiendo desde ${Paths.get("").toAbsolutePath()}")
        }
    }

    /**
     * Migra con el propietario del esquema, no con el usuario de la aplicacion:
     * crear tablas y politicas exige permisos que `drp_app` no tiene ni debe
     * tener.
     */
    private fun migrate() {
        Flyway.configure()
            .dataSource(jdbcUrl, OWNER_USERNAME, OWNER_PASSWORD)
            .locations("classpath:db/migration")
            .placeholders(mapOf("applicationRole" to APP_USERNAME))
            .load()
            .migrate()
    }

    /**
     * Crea el esquema del **modulo de prueba** del Hito 0.
     *
     * Va aqui y no en una migracion de Flyway porque ese modulo no se despliega:
     * es el testigo del mecanismo de activacion y vive en el arbol de pruebas.
     * Si viajara en `db/migration`, la aplicacion de verdad se lo llevaria a
     * produccion; y si viajara en una localizacion de Flyway solo para pruebas,
     * el Flyway que arranca dentro de la aplicacion encontraria una migracion
     * aplicada que no sabe resolver y fallaria la validacion.
     */
    private fun createTestbedSchema() {
        val script = checkNotNull(javaClass.getResourceAsStream("/db/testbed/schema.sql")) {
            "No se encuentra db/testbed/schema.sql en el classpath de pruebas"
        }.use { it.readBytes().toString(Charsets.UTF_8) }

        ownerConnection().use { connection -> connection.createStatement().use { it.execute(script) } }
    }

    /** Conexion como la aplicacion: sujeta a las politicas de RLS. */
    fun appConnection(): Connection = DriverManager.getConnection(jdbcUrl, APP_USERNAME, APP_PASSWORD)

    /**
     * Conexion como propietario del esquema. Solo para preparar datos de prueba:
     * es superusuario, asi que se salta las politicas y no sirve para comprobar
     * aislamiento.
     */
    fun ownerConnection(): Connection = DriverManager.getConnection(jdbcUrl, OWNER_USERNAME, OWNER_PASSWORD)
}
