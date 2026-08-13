package com.drp.test

import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.nio.file.Files
import java.nio.file.Path

/**
 * Base de las pruebas que levantan la aplicacion entera contra PostgreSQL real.
 *
 * Apunta el origen de datos al mismo contenedor que usan las pruebas de esquema,
 * y con el mismo reparto de usuarios que el despliegue: **Flyway migra como
 * propietario y la aplicacion conecta como `drp_app`**, que no es superusuario ni
 * tiene `BYPASSRLS`. Es lo que hace que una prueba de aislamiento que pasa aqui
 * signifique algo.
 *
 * No lleva `@SpringBootTest`: cada prueba concreta decide si necesita servidor
 * web de verdad --las de recorrido vertical-- o le basta el contexto.
 */
abstract class SpringIntegrationTest {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun datasource(registry: DynamicPropertyRegistry) {
            val postgres = DrpPostgres.instance
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { DrpPostgres.APP_USERNAME }
            registry.add("spring.datasource.password") { DrpPostgres.APP_PASSWORD }
            registry.add("spring.flyway.user") { DrpPostgres.OWNER_USERNAME }
            registry.add("spring.flyway.password") { DrpPostgres.OWNER_PASSWORD }

            // Mailpit, tambien en Testcontainers: el recorrido vertical lee de
            // el el enlace de verificacion sin ningun paso manual (ADR-009).
            val mailpit = DrpMailpit.instance
            registry.add("spring.mail.host") { mailpit.host }
            registry.add("spring.mail.port") { mailpit.smtpPort }

            // Holgado a proposito: las pruebas hacen muchas llamadas seguidas
            // desde la misma IP, y lo que se comprueba del limitador tiene su
            // propia prueba, con sus propios valores.
            registry.add("drp.rate-limit.per-ip") { 10_000 }
            registry.add("drp.rate-limit.per-email") { 10_000 }
            registry.add("drp.rate-limit.per-identity-upload") { 10_000 }

            // El volumen de ficheros, en un directorio efimero. Se comparte entre
            // clases igual que el contenedor de PostgreSQL: crear uno por clase
            // multiplicaria los arranques sin comprobar nada mas.
            registry.add("drp.storage.root") { storageRoot.toString() }
        }

        /**
         * La raiz del volumen de ficheros durante las pruebas.
         *
         * Es publica porque hay comprobaciones que **no se pueden hacer por la
         * API**: que el EXIF ha desaparecido se afirma mirando los bytes
         * guardados, no la respuesta. Escribir y leer con el mismo codigo se
         * equivoca igual en los dos sentidos.
         */
        @JvmStatic
        val storageRoot: Path = Files.createTempDirectory("drp-files-test")
    }
}
