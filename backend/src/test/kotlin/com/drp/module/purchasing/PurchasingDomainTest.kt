package com.drp.module.purchasing

import com.drp.module.purchasing.domain.ItemOrigin
import com.drp.module.purchasing.domain.ItemStatus
import com.drp.module.purchasing.domain.ShoppingListEntry
import com.drp.module.purchasing.domain.ShoppingListItem
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Las invariantes del dominio, **sin base de datos**.
 *
 * Las que la base de datos puede expresar viven ademas en el esquema, y eso no es
 * duplicacion sino la regla del core: la comprobacion da el mensaje que el
 * contrato declara y la restriccion cierra la carrera entre dos peticiones
 * simultaneas, que ninguna comprobacion previa puede cerrar.
 */
class PurchasingDomainTest {

    @Test
    @DisplayName("una linea apunta a un articulo o lleva un nombre, nunca las dos cosas y nunca ninguna")
    fun `la primera invariante`() {
        item(articleId = UUID.randomUUID(), name = null).identifiesOneThing().shouldBeTrue()
        item(articleId = null, name = "Pilas AA").identifiesOneThing().shouldBeTrue()

        // Las dos: el nombre del articulo y el suelto dirian cosas distintas y
        // nadie sabria cual pintar.
        item(articleId = UUID.randomUUID(), name = "Pilas AA").identifiesOneThing().shouldBeFalse()

        // Ninguna: una linea que no dice que hace falta.
        item(articleId = null, name = null).identifiesOneThing().shouldBeFalse()
    }

    @Test
    @DisplayName("solo una linea con articulo puede dar entrada en el core")
    fun `el texto suelto no entra en el inventario`() {
        item(articleId = UUID.randomUUID(), name = null).isStockable.shouldBeTrue()

        // No es un caso degradado: es lo que impide inventarle una categoria y una
        // unidad a algo que el hogar no ha dado de alta.
        item(articleId = null, name = "Pilas AA").isStockable.shouldBeFalse()
    }

    @Test
    @DisplayName("vivas son las dos que cuentan para la regla de una sola linea por articulo")
    fun `que linea esta viva`() {
        item(status = ItemStatus.NEEDED).isLive.shouldBeTrue()
        item(status = ItemStatus.IN_PURCHASE).isLive.shouldBeTrue()

        // Lo comprado el mes pasado no puede bloquear que hoy vuelva a hacer
        // falta, que es justo lo que el indice unico parcial deja fuera.
        item(status = ItemStatus.BOUGHT).isLive.shouldBeFalse()
        item(status = ItemStatus.DISMISSED).isLive.shouldBeFalse()
    }

    @Test
    @DisplayName("el orden de los origenes decide que noticia manda, y no es alfabetico")
    fun `acabarse es mas fuerte que bajar del minimo`() {
        ItemOrigin.MANUAL.isWeakerThan(ItemOrigin.LOW_STOCK).shouldBeTrue()
        ItemOrigin.LOW_STOCK.isWeakerThan(ItemOrigin.DEPLETED).shouldBeTrue()

        // Y no al reves: que algo que se acabo siga bajo minimos ya se sabia, asi
        // que no es una noticia nueva y no puede bajar el nivel de la linea.
        ItemOrigin.DEPLETED.isWeakerThan(ItemOrigin.LOW_STOCK).shouldBeFalse()
        ItemOrigin.LOW_STOCK.isWeakerThan(ItemOrigin.LOW_STOCK).shouldBeFalse()
    }

    @Test
    @DisplayName("lo que se lee de una linea es el nombre del articulo, o el suelto")
    fun `el rotulo sale de uno de los dos sitios`() {
        entry(articleName = "Arroz", name = null).label.shouldBe("Arroz")
        entry(articleName = null, name = "Pilas AA").label.shouldBe("Pilas AA")
    }

    @Test
    @DisplayName("la presentacion de compra se compone del envase del core y no se guarda")
    fun `el pack se compone`() {
        // Es la respuesta de este hito a la media pregunta heredada de la Fase 1:
        // no hace falta nombre propio, porque `packSize` y `unit` ya lo dicen.
        entry(articleName = "Leche", packSize = BigDecimal("6"), unit = "LITER").packLabel.shouldBe("6 LITER")

        // Casi nada de una despensa se compra por paquetes de tamano fijo, asi que
        // lo normal es que no haya nada que componer.
        entry(articleName = "Arroz", packSize = null).packLabel.shouldBe(null)
    }

    private fun item(
        articleId: UUID? = UUID.randomUUID(),
        name: String? = null,
        status: ItemStatus = ItemStatus.NEEDED,
    ) = ShoppingListItem(
        id = UUID.randomUUID(),
        articleId = articleId,
        name = name,
        quantity = null,
        origin = ItemOrigin.MANUAL,
        status = status,
        note = null,
        purchaseId = null,
        receivedAssetId = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        createdBy = null,
        updatedBy = null,
    )

    private fun entry(
        articleName: String?,
        name: String? = null,
        unit: String? = "UNIT",
        packSize: BigDecimal? = null,
    ) = ShoppingListEntry(
        item = item(articleId = articleName?.let { UUID.randomUUID() }, name = name),
        articleName = articleName,
        unit = unit,
        packSize = packSize,
    )
}
