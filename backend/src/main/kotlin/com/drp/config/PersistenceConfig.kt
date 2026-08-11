package com.drp.config

import com.drp.adapter.persistence.TenantAwareTransactionManager
import com.drp.application.tenant.TenantContext
import jakarta.persistence.EntityManagerFactory
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
}
