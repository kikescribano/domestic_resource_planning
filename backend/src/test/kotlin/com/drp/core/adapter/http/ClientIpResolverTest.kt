package com.drp.core.adapter.http

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
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
