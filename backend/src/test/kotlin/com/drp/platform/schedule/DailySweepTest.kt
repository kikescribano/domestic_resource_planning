package com.drp.platform.schedule

import com.drp.module.testbed.TestbedModule
import com.drp.test.DrpMailpit
import com.drp.test.SpringIntegrationTest
import com.drp.test.deleteJson
import com.drp.test.getJson
import com.drp.test.postJson
import com.drp.test.registerHousehold
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus

/**
 * El recorrido periodico, con el modulo de prueba encendido en un hogar y
 * apagado en el de al lado.
 *
 * Es lo que demuestra la promesa central del hito: **el recorrido se salta los
 * hogares que no tengan activo el modulo que pide la comprobacion**. Se hace con
 * el modulo de mentira del Hito 0 y no esperando a Warehouse, que es exactamente
 * para lo que ese modulo existe.
 *
 * Y de paso mide lo que un comentario afirmaba y nadie habia comprobado: que **la
 * cache de activacion, que es por hilo y fuera de una peticion no la limpia
 * nadie, no responde por el hogar equivocado**. El recorrido es justo ese camino
 * --un solo hilo pasando por todos los hogares seguidos-- asi que si la cache
 * fuera a secas en vez de ir indexada por hogar, el segundo hogar heredaria la
 * respuesta del primero y esta prueba fallaria en una de las dos direcciones.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [TestbedModule.ENABLED + "=true"],
)
class DailySweepTest : SpringIntegrationTest() {

    @Autowired private lateinit var sweep: DailySweep
    @Autowired private lateinit var http: TestRestTemplate
    @Autowired private lateinit var checks: List<ScheduledCheck>

    private val mailpit = DrpMailpit.instance

    @Test
    @DisplayName("un hogar con el módulo apagado no recibe su aviso, y el de al lado sí")
    fun `la comprobacion de un modulo solo corre donde esta encendido`() {
        val on = http.registerHousehold(mailpit)
        val off = http.registerHousehold(mailpit)
        http.activateTestbed(on.accessToken)

        sweep.run()

        withClue("el hogar con el módulo encendido tiene que haber recibido su aviso") {
            http.notices(on.accessToken).shouldContain("TESTBED_CHECK")
        }
        withClue("el hogar con el módulo apagado no puede recibir el aviso de un módulo que no tiene") {
            http.notices(off.accessToken).shouldNotContain("TESTBED_CHECK")
        }
    }

    @Test
    @DisplayName("la caché de activación no responde por el hogar equivocado en un mismo barrido")
    fun `la cache va indexada por hogar`() {
        // Las dos direcciones, porque el orden en que el recorrido visita los
        // hogares no esta garantizado y una sola direccion dejaria la mitad sin
        // medir. Primero uno encendido y otro apagado; despues se invierte, de
        // modo que en la segunda pasada cada hogar tiene la respuesta contraria a
        // la que la cache traeria del anterior.
        val first = http.registerHousehold(mailpit)
        val second = http.registerHousehold(mailpit)
        http.activateTestbed(first.accessToken)

        sweep.run()
        http.checkCount(first.accessToken).shouldBe(1)
        http.checkCount(second.accessToken).shouldBe(0)

        http.deactivateTestbed(first.accessToken)
        http.activateTestbed(second.accessToken)

        sweep.run()

        // Al que se apago hay que volver a encenderlo **solo para poder leer su
        // tabla**: su ruta esta cerrada por el gate mientras esta apagado.
        //
        // Reactivar **si vuelve a sembrar** --`ActivateModule` solo se ahorra la
        // siembra cuando el modulo YA estaba activo, y esto viene de `INACTIVE`--
        // pero eso no altera la cuenta que se va a mirar, porque se cuenta el
        // rastro de la comprobacion y no el de la siembra. La frase contraria
        // estuvo aqui hasta el Hito 3, que es el que se topo con ello: la ADR-010
        // dice «reactivar no vuelve a sembrar» y el codigo hace lo otro. Se
        // conserva el codigo, y por eso toda siembra tiene que ser idempotente.
        http.activateTestbed(first.accessToken)
        withClue("el hogar que se apagó siguió corriendo su comprobación") {
            http.checkCount(first.accessToken).shouldBe(1)
        }
        withClue("el hogar que se encendió no llegó a correr la suya") {
            http.checkCount(second.accessToken).shouldBe(1)
        }
    }

    @Test
    @DisplayName("lo que puede borrar el hogar corre al final, y por delante manda el alfabeto")
    fun `el orden pone las purgas al final`() {
        // El orden del recorrido es `purgesHousehold` primero y el nombre despues,
        // y las dos mitades importan.
        val ordered = checks.sortedWith(compareBy({ it.purgesHousehold }, { it.name }))
        val (harmless, purging) = ordered.partition { !it.purgesHousehold }

        // **Las purgas, al final.** Hasta la baja de hogar (ADR-012) solo habia
        // una comprobacion capaz de hacer desaparecer el hogar en curso y que
        // fuera la ultima era un accidente del alfabeto; con dos, el alfabeto deja
        // de bastar --`PurgeClosedHouseholds` caeria la segunda de cuatro-- y todo
        // lo que corriera detras lo haria sobre un hogar que ya no existe.
        withClue("las que purgan el hogar tienen que ir al final: ${ordered.map { it.name }}") {
            ordered.takeLast(purging.size).map { it.name }.toSet()
                .shouldBe(purging.map { it.name }.toSet())
        }

        // Las dos que hay declaradas, para que quitarle la marca a una lo rompa
        // aqui y no en produccion.
        purging.map { it.name }.toSet()
            .shouldBe(setOf("PurgeClosedHouseholds", "PurgeUnverifiedHouseholds"))

        // Y por delante, el alfabeto: dos pasadas hacen lo mismo en el mismo
        // orden, que es lo unico que la ADR-011 pedia de esta linea.
        harmless.map { it.name }.shouldBe(harmless.map { it.name }.sorted())
    }

    @Test
    @DisplayName("el resumen diario llega al correo de verdad, con lo que se encontró dentro")
    fun `el resumen se lee de Mailpit`() {
        val home = http.registerHousehold(mailpit)
        http.activateTestbed(home.accessToken)
        mailpit.clear()

        sweep.run()

        // Del buzon real y no de un doble, que es lo que la ADR-009 pide
        // demostrar y como se lee el enlace de verificacion desde la Fase 1.
        val digest = mailpit.awaitMessageTo(home.email)
        digest.subject.shouldContain("1 aviso")
        digest.body.shouldContain("El módulo de prueba pasó por aquí")
        // El enlace apunta al frontend y no a la API: quien lo recibe abre una
        // pantalla, no un endpoint.
        digest.body.shouldContain("/avisos")
    }

    @Test
    @DisplayName("no se envía ningún correo cuando no hay nada que contar")
    fun `un hogar sin avisos no recibe resumen`() {
        // Un correo diario vacio es la forma mas rapida de que se filtren todos:
        // quien recibe treinta que no dicen nada acaba creando una regla, y con
        // ella se va tambien el que si decia algo.
        val quiet = http.registerHousehold(mailpit)
        mailpit.clear()

        sweep.run()

        mailpit.assertNoMessageTo(quiet.email)
    }

    @Test
    @DisplayName("el resumen no repite mañana lo que ya entregó hoy")
    fun `lo ya entregado no vuelve al resumen`() {
        val home = http.registerHousehold(mailpit)
        http.activateTestbed(home.accessToken)

        sweep.run()

        // Apagar el modulo deja al hogar sin nada nuevo que encontrar, con lo que
        // la pasada siguiente solo podria mandar algo si repitiera lo de ayer. Y
        // ese es el caso que `notified_at` existe para cerrar.
        http.deactivateTestbed(home.accessToken)
        mailpit.clear()

        sweep.run()

        mailpit.assertNoMessageTo(home.email)
    }

    @Test
    @DisplayName("los avisos del core llegan igual a un hogar sin ningún módulo encendido")
    fun `el core no se apaga`() {
        // `CheckOwner.Core` significa exactamente esto: la comprobacion corre en
        // todos los hogares. Se comprueba por la bandeja, que responde igual sin
        // ningun modulo activo --y no cuelga de ningun prefijo de modulo, asi que
        // el gate no la alcanza.
        val bare = http.registerHousehold(mailpit)

        sweep.run()

        http.getJson("/api/v1/notices", bare.accessToken).statusCode.shouldBe(HttpStatus.OK)
    }

    // -----------------------------------------------------------------------

    private fun TestRestTemplate.activateTestbed(accessToken: String) =
        postJson("/api/v1/modules/${TestbedModule.KEY}/activation", "", accessToken)
            .also { it.statusCode.shouldBe(HttpStatus.OK) }

    private fun TestRestTemplate.deactivateTestbed(accessToken: String) =
        deleteJson("/api/v1/modules/${TestbedModule.KEY}/activation", accessToken)
            .also { it.statusCode.shouldBe(HttpStatus.OK) }

    private fun TestRestTemplate.notices(accessToken: String): String =
        getJson("/api/v1/notices", accessToken).body!!

    /**
     * Cuantas veces ha pasado la comprobacion del modulo por este hogar, contado
     * por el rastro que deja en **su propia tabla** y no por los avisos.
     *
     * Los dos rastros dicen cosas distintas y hacen falta los dos: el aviso
     * demuestra que plataforma lo recogio, y la nota demuestra que la
     * comprobacion llego a ejecutarse. Sin la nota, «no hay aviso» no distingue
     * «no corrio» de «corrio y no aviso».
     */
    private fun TestRestTemplate.checkCount(accessToken: String): Int {
        val notes = getJson(TestbedModule.ROUTE, accessToken)
        // Con el modulo apagado la ruta responde 403, que para esta cuenta
        // significa cero: si no corrio, no hay nada que contar.
        if (notes.statusCode != HttpStatus.OK) return 0
        return Regex("comprobado").findAll(notes.body.orEmpty()).count()
    }
}
