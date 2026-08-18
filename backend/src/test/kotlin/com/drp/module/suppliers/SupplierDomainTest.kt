package com.drp.module.suppliers

import com.drp.module.suppliers.domain.LinkTarget
import com.drp.module.suppliers.domain.ServiceCategory
import com.drp.module.suppliers.domain.Supplier
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * Las dos reglas del dominio que se pueden comprobar sin base de datos.
 *
 * Las otras tres --nombre unico, enlace no repetido y contacto retirado sin
 * enlaces nuevos-- hablan del estado del hogar, asi que se comprueban en
 * `SupplierJourneyTest` contra PostgreSQL de verdad. Meterlas aqui con un doble
 * mediria el doble.
 */
class SupplierDomainTest {

    @Nested
    @DisplayName("un contacto del que no se sabe como llamar no es un contacto")
    inner class Reachability {

        @Test
        @DisplayName("basta con una de las tres: telefono, correo o web")
        fun `una cualquiera basta`() {
            supplier(phone = "600 100 200").isReachable().shouldBe(true)
            supplier(email = "sat@caldera.example").isReachable().shouldBe(true)
            supplier(website = "https://taller.example").isReachable().shouldBe(true)
        }

        @Test
        @DisplayName("sin ninguna de las tres, no")
        fun `sin ninguna no vale`() {
            supplier().isReachable().shouldBe(false)
        }

        /**
         * **La direccion postal no cuenta.** Sirve para llegar, no para avisar, y
         * un contacto de servicio se usa a la inversa: se le llama para que
         * venga. Sin esta prueba, anadir `address` a la lista de mas arriba
         * pareceria una mejora.
         */
        @Test
        @DisplayName("la direccion postal no es una forma de contacto")
        fun `la direccion no cuenta`() {
            supplier(address = "Calle Mayor 1").isReachable().shouldBe(false)
        }

        /**
         * Un espacio no es un telefono. Importa porque la restriccion de la tabla
         * solo sabe mirar si la columna es nula: sin esto, un campo en blanco
         * cumpliria el `CHECK` y dejaria pasar justo el caso que la regla existe
         * para impedir.
         */
        @Test
        @DisplayName("un campo en blanco no cuenta como forma de contacto")
        fun `el blanco no cuenta`() {
            supplier(phone = "   ").isReachable().shouldBe(false)
        }
    }

    @Nested
    @DisplayName("un enlace apunta a exactamente una cosa")
    inner class Targets {

        private val assetId = UUID.randomUUID()
        private val locationId = UUID.randomUUID()

        @Test
        @DisplayName("con uno de los dos, sale el destino que toca")
        fun `uno de los dos`() {
            LinkTarget.from(assetId, null).shouldBe(LinkTarget.Asset(assetId))
            LinkTarget.from(null, locationId).shouldBe(LinkTarget.Location(locationId))
        }

        @Test
        @DisplayName("con ninguno o con los dos, no hay destino")
        fun `ni ninguno ni los dos`() {
            LinkTarget.from(null, null).shouldBe(null)
            LinkTarget.from(assetId, locationId).shouldBe(null)
        }
    }

    private fun supplier(
        phone: String? = null,
        email: String? = null,
        website: String? = null,
        address: String? = null,
    ) = Supplier(
        id = UUID.randomUUID(),
        name = "Fontanería Pérez",
        serviceCategory = ServiceCategory.PLUMBING,
        contactName = null,
        phone = phone,
        email = email,
        website = website,
        address = address,
        notes = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        retiredAt = null,
        createdBy = null,
        updatedBy = null,
    )
}
