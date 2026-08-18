package com.drp.core.adapter.http

import com.drp.test.SpringIntegrationTest
import com.drp.test.extract
import com.drp.test.extractRaw
import com.drp.test.getJson
import com.drp.test.imageBytes
import com.drp.test.registerHousehold
import com.drp.test.uploadFile
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * La cuota del hogar, ajustada para que **quepa exactamente un fichero**.
 *
 * Es la tercera de las tres pruebas que la ADR-005 exige para darse por validada,
 * y la unica que no se puede hacer con una peticion detras de otra: lo que se
 * comprueba es que **dos reservas simultaneas contra el final de la cuota dejan
 * pasar una sola**.
 *
 * La cuota se calcula al arrancar el contexto a partir de la imagen que van a
 * subir las pruebas, y no se escribe como una constante redonda. No es
 * ceremonia: lo que se reserva es el `Content-Length`, que es la imagen mas la
 * envoltura del multipart, asi que una cifra fija dejaria la prueba a merced de
 * cuanto comprima el PNG en cada version de la JVM --pasando unas veces y
 * fallando otras sin que nadie hubiera tocado nada.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FileQuotaTest : SpringIntegrationTest() {

    @Autowired
    private lateinit var http: TestRestTemplate

    @Test
    fun `pasarse de la cuota responde 409 con su codigo`() {
        val household = http.registerHousehold()

        // La primera cabe; la segunda ya no, porque la primera se quedo el sitio.
        http.uploadFile(household.accessToken, IMAGE, "una.png", "image/png").statusCode shouldBe HttpStatus.CREATED

        val second = http.uploadFile(household.accessToken, IMAGE, "otra.png", "image/png")
        second.statusCode shouldBe HttpStatus.CONFLICT
        second.body!!.extract("code") shouldBe "STORAGE_QUOTA_EXCEEDED"
    }

    /**
     * Ocho subidas a la vez contra una cuota en la que cabe **una**.
     *
     * Es la prueba que distingue reservar de comprobar. Sin el cerrojo sobre la
     * fila del hogar, las ocho leen la misma suma --cero-- antes de que ninguna
     * haya escrito, las ocho deciden que caben y el hogar acaba con ocho ficheros
     * dentro de una cuota de uno. Con el cerrojo tomado **antes de leer**, se
     * serializan y solo la primera encuentra sitio.
     *
     * Se comprueban las dos caras: cuantas pasaron, y que la ocupacion final no
     * se ha pasado de la cuota. La segunda es la que importa de verdad, porque es
     * la que seguiria fallando si alguien cambiase el orden del cerrojo.
     */
    @Test
    fun `dos reservas simultaneas contra el final de la cuota dejan pasar una sola`() {
        val household = http.registerHousehold()

        val threads = 8
        val ready = CountDownLatch(threads)
        val go = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(threads)

        val results = try {
            val futures = (1..threads).map { index ->
                pool.submit<HttpStatus> {
                    ready.countDown()
                    go.await(10, TimeUnit.SECONDS)
                    http.uploadFile(household.accessToken, IMAGE, "foto-$index.png", "image/png")
                        .statusCode as HttpStatus
                }
            }

            ready.await(10, TimeUnit.SECONDS)
            go.countDown()
            futures.map { it.get(60, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }

        results.count { it == HttpStatus.CREATED } shouldBe 1
        results.count { it == HttpStatus.CONFLICT } shouldBe threads - 1

        val used = http.getJson("/api/v1/storage", household.accessToken).body!!.extractRaw("usedBytes").toLong()
        used shouldBeLessThanOrEqual quotaBytes()
    }

    /**
     * Una subida que falla **suelta su reserva en el acto**, sin esperar al
     * proceso diario.
     *
     * Si no lo hiciera, un fichero rechazado por su tipo dejaria su hueco ocupado
     * durante una hora y el siguiente intento --el bueno-- no cabria. Es el caso
     * que mas se nota: quien acaba de equivocarse de fichero reintenta enseguida.
     */
    @Test
    fun `un fichero rechazado no deja la cuota ocupada`() {
        val household = http.registerHousehold()

        http.uploadFile(household.accessToken, "no soy una imagen".toByteArray(), "x.png", "image/png")
            .statusCode shouldBe HttpStatus.UNSUPPORTED_MEDIA_TYPE

        http.getJson("/api/v1/storage", household.accessToken).body!!.extractRaw("usedBytes") shouldBe "0"
    }

    companion object {

        /**
         * La imagen que suben todas las pruebas de esta clase. Es la misma en
         * todas porque la cuota se calcula a partir de ella.
         */
        private val IMAGE = imageBytes("png", width = 200, height = 200)

        /**
         * Justo lo que ocupa una subida y no dos.
         *
         * El margen cubre la envoltura del multipart --limites, cabeceras de
         * parte-- que viaja dentro del `Content-Length` y por tanto entra en lo
         * reservado. Es holgado para una peticion y estrecho para dos, que es
         * exactamente lo que estas pruebas necesitan distinguir.
         */
        private fun quotaBytes(): Long = IMAGE.size + MULTIPART_ENVELOPE

        /**
         * Lo que anade la envoltura del multipart: los limites, la cabecera de
         * disposicion con el nombre del fichero y la del tipo. Ronda los 250
         * bytes, y 800 deja margen sin dejar sitio para una segunda subida.
         */
        private const val MULTIPART_ENVELOPE = 800L

        @JvmStatic
        @DynamicPropertySource
        fun quota(registry: DynamicPropertyRegistry) {
            registry.add("drp.storage.quota-bytes") { quotaBytes() }
        }
    }
}
