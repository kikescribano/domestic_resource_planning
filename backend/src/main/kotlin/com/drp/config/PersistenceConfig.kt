package com.drp.config

import com.drp.adapter.persistence.TenantAwareTransactionManager
import com.drp.application.tenant.TenantContext
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.EntityManagerFactory
import org.hibernate.cfg.AvailableSettings
import org.hibernate.type.format.jackson.JacksonJsonFormatMapper
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager

@Configuration
class PersistenceConfig {

    /**
     * Sustituye al gestor de transacciones que Spring Boot configura solo.
     *
     * Al llamarse `transactionManager` --el nombre que espera la
     * autoconfiguracion-- toda transaccion de la aplicacion pasa por aqui, y con
     * ella por el `SET LOCAL app.household_id`. Eso es justo lo que se busca:
     * que no exista forma de abrir una transaccion sin fijar el contexto de
     * inquilino, ni siquiera olvidandose.
     */
    @Bean
    fun transactionManager(
        entityManagerFactory: EntityManagerFactory,
        tenantContext: TenantContext,
    ): PlatformTransactionManager = TenantAwareTransactionManager(entityManagerFactory, tenantContext)

    /**
     * Le da a Hibernate **el `ObjectMapper` de la aplicacion** para leer y escribir
     * las columnas `jsonb`.
     *
     * Sin esto, Hibernate se construye uno propio con `new ObjectMapper()`, sin el
     * modulo de Kotlin, y entonces no sabe reconstruir una `data class`: los
     * objetos de valor del dominio no tienen constructor sin argumentos, asi que
     * escribir la columna funciona y **leerla falla** con "Could not deserialize
     * string to java type". Es un fallo que no aparece hasta que alguien vuelve a
     * pedir la fila, no al guardarla.
     *
     * Compartir el mapper tiene ademas un efecto deseable: el JSON que se guarda y
     * el que sale por la API son el mismo, que es justo lo que el contrato declara
     * para `capacity` y `environmentalConditions`.
     */
    @Bean
    fun jsonFormatMapperCustomizer(objectMapper: ObjectMapper) = HibernatePropertiesCustomizer { properties ->
        properties[AvailableSettings.JSON_FORMAT_MAPPER] = JacksonJsonFormatMapper(objectMapper)
    }
}
