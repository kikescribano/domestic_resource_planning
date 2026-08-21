package com.drp.core.adapter.http

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.mock.web.MockHttpServletRequest

/**
 * De quien es una peticion cuando hay un proxy delante.
 *
 * Lo que se fija aqui es una pareja de fallos que se compensan mal: leer
 * `X-Forwarded-For` sin comprobar de quien viene **quita** el limite --cada
 * peticion estrena cubo-- y no leerla nunca lo convierte en **uno solo para
 * toda la instalacion** en cuanto hay un proxy. Las dos mitades tienen prueba.
 */
class ClientIpResolverTest {

    private val proxy = "10.0.0.7"
    private val client = "203.0.113.9"

    @Test
    @DisplayName("sin proxies declarados se ignora X-Forwarded-For, aunque venga")
    fun `sin confianza manda remoteAddr`() {
        // Es el caso de un despliegue sin proxy delante: la cabecera la escribe
        // entera quien llama, asi que hacerle caso seria regalar un cubo nuevo
        // por peticion sin mas que variarla.
        val resolver = ClientIpResolver(emptyList())

        val request = MockHttpServletRequest().apply {
            remoteAddr = client
            addHeader("X-Forwarded-For", "1.2.3.4")
        }

        resolver.resolve(request).shouldBe(client)
    }

    @Test
    @DisplayName("una peticion que no viene del proxy declarado se cuenta por su propia IP")
    fun `un desconocido no puede decir quien es`() {
        val resolver = ClientIpResolver(listOf(proxy))

        val request = MockHttpServletRequest().apply {
            remoteAddr = "198.51.100.4"
            addHeader("X-Forwarded-For", "1.2.3.4")
        }

        resolver.resolve(request).shouldBe("198.51.100.4")
    }

    @Test
    @DisplayName("detras del proxy de confianza, cada cliente recupera su propia IP")
    fun `tras el proxy se lee la cabecera`() {
        val resolver = ClientIpResolver(listOf(proxy))

        val request = MockHttpServletRequest().apply {
            remoteAddr = proxy
            addHeader("X-Forwarded-For", client)
        }

        // Sin esto, todas las peticiones caerian en el cubo del proxy y veinte
        // bastarian para dejar sin login a la instalacion entera.
        resolver.resolve(request).shouldBe(client)
    }

    @Test
    @DisplayName("una X-Forwarded-For falsificada por el cliente no elige el cubo")
    fun `la ultima entrada es la que no puede falsificarse`() {
        val resolver = ClientIpResolver(listOf(proxy))

        // Lo que nginx entrega cuando el cliente manda su propia cabecera: la
        // inventada se queda delante y la que el proxy observo va **al final**.
        // Tomar la primera --el reflejo habitual, porque «es el cliente
        // original»-- seria leer justo el valor que el atacante escribe.
        val request = MockHttpServletRequest().apply {
            remoteAddr = proxy
            addHeader("X-Forwarded-For", "1.2.3.4, $client")
        }

        resolver.resolve(request).shouldBe(client)
    }

    @Test
    @DisplayName("el proxy de confianza se puede declarar por rango CIDR")
    fun `el rango vale por la IP suelta`() {
        // Una red de contenedores no tiene una IP fija que declarar, asi que sin
        // CIDR esto no se podria configurar en un despliegue con compose.
        val resolver = ClientIpResolver(listOf("10.0.0.0/8"))

        val request = MockHttpServletRequest().apply {
            remoteAddr = proxy
            addHeader("X-Forwarded-For", client)
        }

        resolver.resolve(request).shouldBe(client)
    }

    @Test
    @DisplayName("sin X-Forwarded-For se recurre a X-Real-IP")
    fun `el respaldo es la otra cabecera`() {
        val resolver = ClientIpResolver(listOf(proxy))

        val request = MockHttpServletRequest().apply {
            remoteAddr = proxy
            addHeader("X-Real-IP", client)
        }

        resolver.resolve(request).shouldBe(client)
    }

    @Test
    @DisplayName("un proxy de confianza que no mande ninguna cabecera cae en su propia IP")
    fun `sin cabeceras queda el salto inmediato`() {
        val resolver = ClientIpResolver(listOf(proxy))

        val request = MockHttpServletRequest().apply { remoteAddr = proxy }

        resolver.resolve(request).shouldBe(proxy)
    }

    @Test
    @DisplayName("una cabecera de proxy sin ningun proxy declarado avisa en el log, y una sola vez")
    fun `el proxy sin declarar deja su firma en el log`() {
        // Es la mitad en caliente del aviso de arranque de
        // TrustedProxiesStartupNotice: si llega la cabecera que pone un proxy y
        // aqui no hay ninguno declarado, o hay un proxy que nadie declaro --y el
        // cubo por IP es uno para todos sus clientes-- o alguien la falsifica.
        // Las dos merecen una linea; ninguna merece mil.
        val resolver = ClientIpResolver(emptyList())

        val warnings = capturingWarnings {
            repeat(3) {
                resolver.resolve(
                    MockHttpServletRequest().apply {
                        remoteAddr = proxy
                        addHeader("X-Forwarded-For", client)
                    },
                )
            }
        }

        warnings.size shouldBe 1
        warnings.single().formattedMessage shouldContain "DRP_TRUSTED_PROXIES"
    }

    @Test
    @DisplayName("con proxies declarados, una cabecera de un desconocido no escribe en el log")
    fun `con proxy declarado no se avisa`() {
        // El operador ya hizo su parte. Avisar de cada peticion con la cabecera
        // inventada seria dejar que quien llama escriba en el log de otro.
        val resolver = ClientIpResolver(listOf(proxy))

        val warnings = capturingWarnings {
            resolver.resolve(
                MockHttpServletRequest().apply {
                    remoteAddr = "198.51.100.4"
                    addHeader("X-Forwarded-For", "1.2.3.4")
                },
            )
        }

        warnings.size shouldBe 0
    }

    private fun capturingWarnings(block: () -> Unit): List<ILoggingEvent> {
        val log = LoggerFactory.getLogger(ClientIpResolver::class.java) as ch.qos.logback.classic.Logger
        val appender = ListAppender<ILoggingEvent>().also { it.start() }
        log.addAppender(appender)
        try {
            block()
        } finally {
            log.detachAppender(appender)
        }
        return appender.list.filter { it.level == Level.WARN }
    }

    @Test
    @DisplayName("los espacios y las entradas vacias de la configuracion no rompen el arranque")
    fun `la configuracion se limpia`() {
        // Una lista separada por comas escrita a mano en una variable de entorno
        // trae espacios con facilidad, y una entrada vacia haria fallar al
        // constructor de IpAddressMatcher.
        val resolver = ClientIpResolver(listOf(" $proxy ", "", "   "))

        val request = MockHttpServletRequest().apply {
            remoteAddr = proxy
            addHeader("X-Forwarded-For", client)
        }

        resolver.resolve(request).shouldBe(client)
    }
}
