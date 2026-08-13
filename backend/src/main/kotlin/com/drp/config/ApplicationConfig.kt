package com.drp.config

import com.drp.application.SessionPolicy
import com.drp.application.usecase.StoragePolicy
import com.drp.domain.identity.PasswordPolicy
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration

@Configuration
class ApplicationConfig {

    /**
     * La politica de contrasenas, con la lista de comunes ya cargada.
     *
     * Cargar el fichero es responsabilidad de un adaptador y no del dominio, que
     * es por lo que [PasswordPolicy] recibe la lista construida en lugar de
     * saber de donde sale.
     */
    @Bean
    fun passwordPolicy(): PasswordPolicy {
        val commonPasswords = ClassPathResource("common-passwords.txt").inputStream
            .bufferedReader()
            .useLines { lines ->
                lines.map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .toSet()
            }
        return PasswordPolicy(commonPasswords)
    }

    /**
     * Los dos limites de almacenamiento, juntos porque quien pregunta los
     * necesita a la vez: `GET /storage` avisa antes de que una subida falle.
     */
    @Bean
    fun storagePolicy(
        @Value("\${drp.storage.quota-bytes}") quotaBytes: Long,
        @Value("\${drp.storage.max-file-bytes}") maxFileBytes: Long,
    ): StoragePolicy = StoragePolicy(quotaBytes, maxFileBytes)

    @Bean
    fun sessionPolicy(
        @Value("\${drp.security.jwt.refresh-token-ttl}") refreshTokenTtl: Duration,
    ): SessionPolicy = SessionPolicy(refreshTokenTtl)

    /**
     * Para los casos de uso que tienen que fijar el contexto de inquilino
     * **antes** de abrir la transaccion, y por tanto no pueden delegar en
     * `@Transactional`: el alta de un hogar, la aceptacion de una invitacion, el
     * login y los procesos diarios.
     */
    @Bean
    fun transactionTemplate(transactionManager: PlatformTransactionManager): TransactionTemplate =
        TransactionTemplate(transactionManager)
}
