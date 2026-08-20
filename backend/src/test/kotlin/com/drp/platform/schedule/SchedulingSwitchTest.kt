package com.drp.platform.schedule

import com.drp.test.SpringIntegrationTest
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.scheduling.config.ScheduledTaskHolder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

/**
 * El interruptor del programador, medido en los dos sentidos.
 *
 * Hasta este hito no habia ningun `@EnableScheduling` en el codigo de produccion
 * y los tres procesos diarios no los invocaba nadie. Al encenderlo aparece el
 * problema contrario: **la pasada diaria correria tambien dentro de la suite**,
 * sobre la base de datos que todas las pruebas comparten, y esa pasada marca
 * prestamos, borra ficheros y purga hogares.
 *
 * Que este apagado en las pruebas no se puede afirmar «porque lo pone una
 * propiedad»: si el `@ConditionalOnProperty` estuviera mal escrito --nombre
 * equivocado, `matchIfMissing` al reves-- todo seguiria compilando y la suite
 * empezaria a fallar de formas que no se parecen a la causa. Asi que se mide, y
 * se mide tambien la otra mitad: que encendido **si** programa la pasada, sin lo
 * cual esta prueba solo diria que la propiedad no hace nada.
 */
@SpringBootTest
@DisplayName("el programador apagado, que es como corre la suite")
class SchedulingDisabledTest : SpringIntegrationTest() {

    @Autowired private lateinit var context: ApplicationContext

    @Test
    @DisplayName("no hay ninguna tarea programada")
    fun `la pasada diaria no se registra`() {
        withClue("el programador está encendido en las pruebas: la suite se purgaría hogares sola") {
            context.scheduledTaskDescriptions().shouldBe(emptyList())
        }
    }
}

/**
 * El programador **encendido**, que es como corre un despliegue.
 *
 * No hereda de `SpringIntegrationTest` a proposito, y no es purismo: las
 * propiedades de `@DynamicPropertySource` se recogen de toda la jerarquia y
 * quien gana cuando dos las declaran no esta garantizado por ningun contrato. Al
 * cablear las dos cosas **en el mismo metodo**, el orden es el de las lineas y
 * no hay nada que suponer --que importa mucho aqui, porque una prueba que midiera
 * lo contrario de lo que dice medir pasaria en verde.
 */
@SpringBootTest
@DisplayName("el programador encendido, que es como corre un despliegue")
class SchedulingEnabledTest {

    @Autowired private lateinit var context: ApplicationContext

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            SpringIntegrationTest.datasource(registry)
            registry.add("drp.schedule.enabled") { true }
            // Programada de verdad, pero para una fecha que no va a llegar
            // mientras corre la prueba. Lo que se mide es que **queda
            // registrada**, no la hora; dejar la de produccion metería una
            // pasada suelta en la suite el dia que la CI corriera a las 3:15.
            registry.add("drp.schedule.daily-cron") { "0 0 0 1 1 *" }
        }
    }

    @Test
    @DisplayName("la pasada diaria queda programada, y es el mismo método que invocan las pruebas")
    fun `la pasada diaria se ejecuta de verdad`() {
        val scheduled = context.scheduledTaskDescriptions()

        withClue("las tareas programadas son: $scheduled") {
            scheduled.any { it.contains("DailySweep") && it.contains("run") }.shouldBe(true)
        }
    }

    @Test
    @DisplayName("y el relay del outbox NO, porque tiene su propio interruptor")
    fun `el relay no cuelga de este interruptor`() {
        // **Esta prueba vive aqui a proposito, que es donde estaba el peligro.**
        // El relay del Hito 1 del cierre de huecos (ADR-013) corre cada cinco
        // segundos, no una vez al dia. Si colgara de `drp.schedule.enabled`, esta
        // clase --que lo enciende para medir la pasada diaria-- pondria a repartir
        // eventos dentro de su contexto, sobre la base que toda la suite comparte
        // y a mitad de otra prueba.
        //
        // La mitad contraria --que encendido si se programa-- la mide
        // `OutboxSchedulingSwitchTest`. Sin las dos, esto solo diria que la
        // propiedad no hace nada.
        val scheduled = context.scheduledTaskDescriptions()

        withClue("las tareas programadas son: $scheduled") {
            scheduled.any { it.contains("OutboxRelay") }.shouldBe(false)
        }
    }
}

/**
 * Lo que el contexto tiene programado, en texto.
 *
 * Se pregunta por [ScheduledTaskHolder] y no por la configuracion, porque lo que
 * hay que saber no es si existe la clase sino si Spring llego a registrar la
 * tarea. Sin `@EnableScheduling` no hay ningun holder, y la lista sale vacia.
 */
private fun ApplicationContext.scheduledTaskDescriptions(): List<String> =
    getBeansOfType(ScheduledTaskHolder::class.java).values
        .flatMap { it.scheduledTasks }
        .map { it.task.runnable.toString() }
