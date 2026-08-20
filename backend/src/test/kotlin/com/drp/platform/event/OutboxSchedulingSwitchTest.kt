package com.drp.platform.event

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
 * El interruptor del relay, **encendido**, que es como corre un despliegue.
 *
 * El relay tiene el suyo y no cuelga del de la pasada diaria, y la razon es
 * concreta: `SchedulingEnabledTest` enciende el programador a proposito para
 * medir que la pasada queda registrada, y con el relay colgando de aquella
 * propiedad empezaria a repartir eventos **cada cinco segundos** dentro de ese
 * contexto, sobre la base que toda la suite comparte y a mitad de otra prueba.
 * La otra mitad de esta medicion --que apagado no se registra nada, con el
 * programador encendido-- vive justamente alli, que es donde estaba el peligro.
 *
 * Que este apagado en la suite no se puede afirmar «porque lo pone una
 * propiedad»: un `@ConditionalOnProperty` mal escrito --nombre equivocado,
 * `matchIfMissing` al reves-- compila igual y la suite empezaria a fallar de
 * formas que no se parecen a la causa.
 *
 * No hereda de [SpringIntegrationTest] por lo mismo que `SchedulingEnabledTest`:
 * las propiedades de `@DynamicPropertySource` se recogen de toda la jerarquia y
 * quien gana cuando dos las declaran no esta garantizado por ningun contrato. Al
 * cablear las dos cosas en el mismo metodo, el orden es el de las lineas.
 */
@SpringBootTest
@DisplayName("el relay del outbox encendido, que es como corre un despliegue")
class OutboxSchedulingSwitchTest {

    @Autowired private lateinit var context: ApplicationContext

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            SpringIntegrationTest.datasource(registry)
            registry.add("drp.schedule.enabled") { true }
            registry.add("drp.outbox.enabled") { true }
            // La pasada diaria, programada para una fecha que no va a llegar
            // mientras corre la prueba: lo que se mide es que **queda
            // registrada**, no la hora.
            registry.add("drp.schedule.daily-cron") { "0 0 0 1 1 *" }
            // Y el relay, con un periodo largo por el mismo motivo. Lo que se
            // mide es que la tarea existe; con los cinco segundos de produccion
            // esta prueba se traeria una pasada de verdad por el camino.
            registry.add("drp.outbox.period") { "1h" }
        }
    }

    @Test
    @DisplayName("la pasada del relay queda programada, y es el mismo método que invocan las pruebas")
    fun `el relay se programa de verdad`() {
        val scheduled = context.scheduledTaskDescriptions()

        withClue("las tareas programadas son: $scheduled") {
            scheduled.any { it.contains("OutboxRelay") && it.contains("run") }.shouldBe(true)
        }
    }

    @Test
    @DisplayName("y no comparte interruptor con la pasada diaria: las dos quedan registradas por separado")
    fun `los dos interruptores son independientes`() {
        // Con los dos encendidos hay **dos** tareas y no una. Es lo que hace que
        // la mitad contraria --el relay apagado con el programador encendido, en
        // `SchedulingEnabledTest`-- signifique algo: si compartieran interruptor,
        // aquella prueba pasaria sin haber medido nada.
        val scheduled = context.scheduledTaskDescriptions()

        withClue("las tareas programadas son: $scheduled") {
            scheduled.any { it.contains("DailySweep") && it.contains("run") }.shouldBe(true)
            scheduled.any { it.contains("OutboxRelay") && it.contains("run") }.shouldBe(true)
        }
    }
}

/**
 * Lo que el contexto tiene programado, en texto. Es la misma lectura que hace
 * `SchedulingSwitchTest`, y por el mismo motivo: lo que hay que saber no es si
 * existe la clase sino si Spring llego a registrar la tarea.
 */
private fun ApplicationContext.scheduledTaskDescriptions(): List<String> =
    getBeansOfType(ScheduledTaskHolder::class.java).values
        .flatMap { it.scheduledTasks }
        .map { it.task.runnable.toString() }
