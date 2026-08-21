package com.drp.config

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

/**
 * El aviso de arranque de `DRP_TRUSTED_PROXIES`.
 *
 * Lo que se fija es la condicion, no la prosa: **fuera de desarrollo y sin
 * ningun proxy declarado se escribe un WARN**, porque ese olvido no rompe nada
 * visible y convierte el limite por IP en un cubo unico detras de un proxy. Y
 * las dos condiciones que lo callan tienen su prueba, porque un aviso que
 * salta siempre es un aviso que nadie lee.
 */
class TrustedProxiesStartupNoticeTest {

    @Test
    @DisplayName("fuera de desarrollo y sin proxies declarados, el arranque avisa")
    fun `sin proxies fuera de desarrollo se avisa`() {
        val warnings = warningsFor(developmentEnvironment = false, trustedProxies = emptyList())

        warnings.size shouldBe 1
        // El mensaje tiene que decir que mando tocar, no solo que algo pasa.
        warnings.single().formattedMessage shouldContain "DRP_TRUSTED_PROXIES"
    }

    @Test
    @DisplayName("una lista de entradas en blanco cuenta como vacia")
    fun `los espacios no callan el aviso`() {
        // Es lo que llega si la variable existe pero esta en blanco: el binding
        // de Spring produce entradas vacias, no una lista vacia.
        warningsFor(developmentEnvironment = false, trustedProxies = listOf("", "  ")).size shouldBe 1
    }

    @Test
    @DisplayName("en desarrollo no se avisa: no hay proxy y seria ruido en cada arranque")
    fun `en desarrollo se calla`() {
        warningsFor(developmentEnvironment = true, trustedProxies = emptyList()).size shouldBe 0
    }

    @Test
    @DisplayName("con un proxy declarado no se avisa: la configuracion esta hecha")
    fun `con proxy declarado se calla`() {
        warningsFor(developmentEnvironment = false, trustedProxies = listOf("172.16.0.0/12")).size shouldBe 0
    }

    private fun warningsFor(developmentEnvironment: Boolean, trustedProxies: List<String>): List<ILoggingEvent> {
        val log = LoggerFactory.getLogger("trusted-proxies-notice-test") as ch.qos.logback.classic.Logger
        val appender = ListAppender<ILoggingEvent>().also { it.start() }
        log.addAppender(appender)
        try {
            TrustedProxiesStartupNotice.warnIfUndeclared(log, developmentEnvironment, trustedProxies)
        } finally {
            log.detachAppender(appender)
        }
        return appender.list.filter { it.level == Level.WARN }
    }
}
