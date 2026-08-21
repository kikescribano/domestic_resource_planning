package com.drp.config

import jakarta.annotation.PostConstruct
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

/**
 * El aviso de arranque contra el olvido mas facil del despliegue:
 * `DRP_TRUSTED_PROXIES`.
 *
 * El limitador de frecuencia sabe recuperar la IP real del cliente detras de un
 * proxy, pero solo si el salto inmediato esta declarado en
 * `drp.rate-limit.trusted-proxies` --vacio por omision, que es lo correcto sin
 * proxy delante--. El fallo esta en que olvidar la variable al desplegar **no
 * rompe nada visible**: la aplicacion arranca, todo funciona, y el limite por IP
 * se ha vuelto en silencio un cubo unico para toda la instalacion, con el que
 * veinte peticiones sin credencial dejan sin login a todos los hogares a la vez.
 *
 * **Avisa y no falla, y esa es la decision.** Un despliegue sin proxy delante
 * con la lista vacia es una configuracion correcta, y desde dentro la aplicacion
 * no puede saber si hay proxy o no: fallar aqui inventaria un requisito que no
 * existe. Lo que si se puede es hacer que el olvido se lea en el log del
 * arranque, que es donde mira quien acaba de desplegar. La otra mitad --el aviso
 * en caliente cuando llega una `X-Forwarded-For` sin ningun proxy declarado--
 * vive en `ClientIpResolver`, que es quien ve las peticiones.
 *
 * Solo fuera de desarrollo: en local no hay proxy y el aviso seria ruido en
 * cada arranque de `bootRun` y en cada prueba.
 */
@Component
class TrustedProxiesStartupNotice(
    private val environment: Environment,
    @Value("\${drp.rate-limit.trusted-proxies:}") private val trustedProxies: List<String>,
) {

    @PostConstruct
    fun warnIfUndeclared() {
        warnIfUndeclared(
            log,
            developmentEnvironment = SecurityConfig.isDevelopmentEnvironment(environment.activeProfiles),
            trustedProxies = trustedProxies,
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(TrustedProxiesStartupNotice::class.java)

        /** Separado del bean para poder afirmarlo en una prueba sin contexto de Spring. */
        fun warnIfUndeclared(log: Logger, developmentEnvironment: Boolean, trustedProxies: List<String>) {
            if (developmentEnvironment) return
            if (trustedProxies.any { it.isNotBlank() }) return
            log.warn(
                "drp.rate-limit.trusted-proxies esta vacio: si esta instalacion tiene un proxy delante " +
                    "(nginx, por ejemplo), el limite de frecuencia por IP esta contando a todos los " +
                    "clientes en el mismo cubo y unas pocas peticiones sin credencial pueden dejar sin " +
                    "login a toda la instalacion. Declara DRP_TRUSTED_PROXIES con la IP o el rango del " +
                    "proxy. Sin proxy delante, este aviso se puede ignorar: vacio es lo correcto.",
            )
        }
    }
}
