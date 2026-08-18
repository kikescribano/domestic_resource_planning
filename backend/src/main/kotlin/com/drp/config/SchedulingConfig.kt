package com.drp.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * Enciende el programador (ADR-011).
 *
 * Hasta este hito **no habia un solo `@EnableScheduling` en el codigo de
 * produccion**: los tres procesos diarios existian y no los invocaba nadie. Esto
 * es lo que cierra ese hueco, y va en la raiz de composicion porque encender el
 * programador es cablear la aplicacion, no una capacidad de plataforma --que es
 * quien pone el recorrido, en `com.drp.platform.schedule`.
 *
 * **Se puede apagar, y hace falta que se pueda.** Con el encendido, la pasada
 * diaria corre dentro de cualquier contexto de Spring que la suite levante --y
 * esa pasada marca prestamos, borra ficheros y **purga hogares**, en medio de
 * otra prueba y sobre la base de datos que todas comparten--. La condicion sobre
 * la propiedad lo deja encendido por omision, que es lo que quiere un despliegue,
 * y las pruebas lo apagan en `SpringIntegrationTest`.
 *
 * **Una instancia, un programador.** El despliegue elegido con consumo medido es
 * un VPS unico, asi que `@Scheduled` basta y no hace falta coordinacion entre
 * nodos. Es una premisa y no una propiedad: el dia que haya dos instancias los
 * procesos se ejecutan dos veces y **ninguno avisa de ello**. La ADR-011 lo
 * recoge con su condicion de revision.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = ["drp.schedule.enabled"], havingValue = "true", matchIfMissing = true)
class SchedulingConfig
