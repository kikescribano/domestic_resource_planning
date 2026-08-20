package com.drp.module.maintenance

import com.drp.platform.schedule.DailySweep
import com.drp.test.DrpMailpit
import com.drp.test.HOUSEHOLD_ZONE
import com.drp.test.SpringIntegrationTest
import com.drp.test.TestHousehold
import com.drp.test.extract
import com.drp.test.getJson
import com.drp.test.postJson
import com.drp.test.registerHousehold
import com.drp.test.seededCategory
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpStatus
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * **La franja en la que un hogar no podia apuntar lo que acababa de hacer.**
 *
 * El reloj de la aplicacion es `Clock.systemUTC()`, asi que derivar de el un dia
 * de calendario da el dia **de Greenwich**. Entre la medianoche local de un hogar
 * peninsular y la de Greenwich --dos horas en verano, una en invierno-- la fecha
 * que esa persona tiene delante es, para la aplicacion, manana: `400
 * VALIDATION_ERROR`, «una intervención no puede ser del futuro».
 *
 * **El reloj se fija, y esa es la mitad que hace que esto no pueda volver.** Una
 * prueba que se apoyara en la hora a la que se lanza la suite pasaria entera de
 * dia y fallaria de madrugada, que es exactamente como se colo el defecto. Aqui
 * el instante es un dato de la prueba: las 23:30 de Greenwich, que en
 * [HOUSEHOLD_ZONE] es la 01:30 del **dia siguiente**.
 *
 * Y por eso hay un [saltoDeDia] que se comprueba antes que nada: si alguien
 * moviera el instante a una hora donde las dos fechas coinciden, todo lo demas
 * seguiria pasando **sin medir nada**.
 *
 * **Estrena contexto de Spring, y es la unica forma de hacerlo.** El bean del
 * reloj lo pone `SecurityConfig` y no hay propiedad que lo mueva; lo que se paga
 * es un pool de cinco conexiones mas mientras el contexto siga en cache. El
 * reloj sustituto es `@Primary` en lugar de reemplazar el bean por su nombre,
 * que exigiria abrir `spring.main.allow-bean-definition-overriding` para toda la
 * clase --un interruptor que tapa el proximo choque de nombres de verdad.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HouseholdCalendarDayTest : SpringIntegrationTest() {

    @Autowired private lateinit var http: TestRestTemplate

    /**
     * Se invoca a mano, que es el mismo metodo que invoca el `@Scheduled`: el
     * programador esta apagado en toda la suite porque, encendido, la pasada
     * correria dentro de cualquier contexto de prueba y purgaria hogares a mitad
     * de otra.
     */
    @Autowired private lateinit var sweep: DailySweep

    private val mailpit = DrpMailpit.instance

    /**
     * Que el instante elegido **de verdad cambia de dia** entre Greenwich y el
     * hogar. Es la premisa de todo lo demas, y sin ella las cuatro pruebas de
     * abajo pasarian igual sin comprobar nada.
     */
    @Test
    @DisplayName("el instante de la prueba cae en la franja: en el hogar ya es mañana")
    fun saltoDeDia() {
        HOUSEHOLD_DAY.shouldBe(GREENWICH_DAY.plusDays(1))
    }

    // -----------------------------------------------------------------------
    // La regla que se destapo: una intervencion no puede ser del futuro
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("el día del hogar se acepta aunque en Greenwich todavía sea ayer")
    fun `lo que acaba de hacerse se puede apuntar`() {
        val home = http.registerHousehold(mailpit)
        home.activate()
        val boiler = home.durable("Caldera de gas")

        val recorded = http.postJson(
            "$CMMS/interventions",
            """{"assetId":"$boiler","kind":"CORRECTIVE","performedOn":"$HOUSEHOLD_DAY",
                "summary":"Purgada al llegar a casa"}""",
            home.accessToken,
        )

        // Con el dia de Greenwich esto era un `400`: la fecha que la persona
        // tenia delante le llegaba a la aplicacion como manana.
        recorded.statusCode.shouldBe(HttpStatus.CREATED)
        recorded.body!!.shouldContain("\"performedOn\":\"$HOUSEHOLD_DAY\"")
    }

    /**
     * Y la regla **sigue existiendo**: lo que se corrige es contra que dia se
     * mide, no que se deje de mirar. Sin esto, quitar la comprobacion entera
     * tambien pondria verde la prueba de arriba.
     */
    @Test
    @DisplayName("el día siguiente al del hogar sigue siendo futuro")
    fun `mañana se rechaza igual`() {
        val home = http.registerHousehold(mailpit)
        home.activate()
        val boiler = home.durable("Termo")

        val rejected = http.postJson(
            "$CMMS/interventions",
            """{"assetId":"$boiler","kind":"CORRECTIVE","performedOn":"${HOUSEHOLD_DAY.plusDays(1)}",
                "summary":"Lo haré mañana"}""",
            home.accessToken,
        )

        rejected.statusCode.shouldBe(HttpStatus.BAD_REQUEST)
        rejected.body!!.shouldContain("VALIDATION_ERROR")
        rejected.body!!.shouldContain("performedOn")
    }

    /**
     * **El dia es de cada hogar y no de la instalacion**, que es lo que ninguna
     * prueba de un solo hogar puede ensenar y lo que obliga a que esto sea un
     * puerto y no una constante de despliegue.
     *
     * El mismo instante, dos hogares: en Madrid ya es dia 16 y en Honolulu son
     * las 13:30 del 15. La misma fecha que uno puede apuntar es futuro para el
     * otro, y las dos respuestas son correctas.
     */
    @Test
    @DisplayName("el mismo instante da dos días distintos en dos hogares distintos")
    fun `cada hogar con su calendario`() {
        val madrid = http.registerHousehold(mailpit)
        val honolulu = http.registerHousehold(mailpit, ZoneId.of("Pacific/Honolulu"))
        listOf(madrid, honolulu).forEach { it.activate() }

        http.postJson(
            "$CMMS/interventions",
            """{"assetId":"${madrid.durable("Caldera")}","kind":"CORRECTIVE",
                "performedOn":"$HOUSEHOLD_DAY","summary":"Aquí ya es día 16"}""",
            madrid.accessToken,
        ).statusCode.shouldBe(HttpStatus.CREATED)

        http.postJson(
            "$CMMS/interventions",
            """{"assetId":"${honolulu.durable("Caldera")}","kind":"CORRECTIVE",
                "performedOn":"$HOUSEHOLD_DAY","summary":"Aquí todavía es día 15"}""",
            honolulu.accessToken,
        ).statusCode.shouldBe(HttpStatus.BAD_REQUEST)
    }

    // -----------------------------------------------------------------------
    // Y la otra mitad: lo que la comprobacion nocturna considera "hoy"
    // -----------------------------------------------------------------------

    /**
     * La comprobacion periodica se equivoca **como mucho en un dia**, que es lo
     * que la hace facil de no ver: el aviso de lo que toca hoy llega manana.
     *
     * El plan avisa **el mismo dia** --`leadDays` a cero, que la ficha del modulo
     * declara legitimo: «avísame el día que toque»-- y toca el dia del hogar. Con
     * el dia de Greenwich, `stageOn` no devuelve ninguna fase y el hogar no recibe
     * nada esa noche.
     */
    @Test
    @DisplayName("la comprobación nocturna avisa el día que toca en el hogar, no el siguiente")
    fun `el aviso llega la noche que toca`() {
        val home = http.registerHousehold(mailpit)
        home.activate()
        val plan = http.postJson(
            "$CMMS/plans",
            """{"assetId":"${home.durable("Caldera de la cocina")}","name":"Revisión anual",
                "intervalMonths":12,"leadDays":0,"nextDueOn":"$HOUSEHOLD_DAY"}""",
            home.accessToken,
        )
        plan.statusCode.shouldBe(HttpStatus.CREATED)

        sweep.run()

        val notices = http.getJson("/api/v1/notices", home.accessToken).body!!
        notices.shouldContain("MAINTENANCE_DUE_SOON")
        // Y no «se ha pasado», que seria pasarse un dia por el otro lado.
        notices.shouldNotContain("MAINTENANCE_OVERDUE")
    }

    // -----------------------------------------------------------------------

    private fun TestHousehold.activate() {
        http.postJson("/api/v1/modules/MAINTENANCE/activation", "", accessToken)
            .statusCode.shouldBe(HttpStatus.OK)
    }

    private fun TestHousehold.durable(name: String): String = http.postJson(
        "/api/v1/assets",
        """{"name":"$name","type":"DURABLE","categoryId":"${http.seededCategory(accessToken, "Herramientas")}"}""",
        accessToken,
    ).body!!.extract("id")

    /**
     * El reloj de la aplicacion, parado en la franja.
     *
     * `@Primary` y no un bean llamado `clock`: con el mismo nombre que el de
     * `SecurityConfig` esto seria una redefinicion, que Spring Boot prohibe salvo
     * que se abra `spring.main.allow-bean-definition-overriding` --y ese
     * interruptor deja de avisar del proximo choque de nombres que si sea un
     * error.
     */
    @TestConfiguration
    class StoppedClock {
        @Bean
        @Primary
        fun midnightInMadrid(): Clock = Clock.fixed(NIGHT, ZoneOffset.UTC)
    }

    private companion object {
        const val CMMS = "/api/v1/maintenance"
    }
}

/**
 * Las 23:30 de Greenwich de un dia de julio.
 *
 * **Fija y no relativa a hoy**, al reves que en las demas pruebas de CMMS: aqui
 * la fecha no es un detalle del que haya que abstraerse sino el sujeto de la
 * prueba, y lo unico que importa de ella es en que lado de la medianoche cae
 * cada zona. En julio [HOUSEHOLD_ZONE] va en CEST --dos horas por delante-- que
 * es el caso peor de la franja.
 */
private val NIGHT: Instant = Instant.parse("2026-07-15T23:30:00Z")

/** El dia que ese instante tiene en Greenwich: el 15. */
private val GREENWICH_DAY: LocalDate = LocalDate.ofInstant(NIGHT, ZoneOffset.UTC)

/** Y el que tiene en el hogar: el 16, que es el que la persona ve en su reloj. */
private val HOUSEHOLD_DAY: LocalDate = LocalDate.ofInstant(NIGHT, HOUSEHOLD_ZONE)
