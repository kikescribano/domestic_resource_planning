package com.drp.module.suppliers.domain

import java.time.Instant
import java.util.UUID

/**
 * Un contacto de servicio del hogar: quien arregla, quien cobra y quien responde
 * de una garantia.
 *
 * **No distingue empresa de persona.** El servicio tecnico de la caldera y el
 * electricista del barrio se guardan igual porque lo que el hogar necesita de los
 * dos es lo mismo: como se llaman, a que se dedican y como se les llama. Meter la
 * distincion obligaria a decidir que campos son de cual, y ninguno de los dos
 * tipos se comporta distinto en ninguna regla.
 *
 * La ficha del modulo esta en `docs/backend/modules/suppliers.md` y se escribio
 * antes que esta clase.
 */
data class Supplier(
    val id: UUID,
    val name: String,
    val serviceCategory: ServiceCategory,
    val contactName: String?,
    val phone: String?,
    val email: String?,
    val website: String?,
    val address: String?,
    val notes: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val retiredAt: Instant?,
    val createdBy: UUID?,
    val updatedBy: UUID?,
) {
    /** Vigente: se ofrece al enlazar y cuenta para el nombre unico. */
    val isLive: Boolean get() = retiredAt == null

    /**
     * **Un contacto de servicio del que no se sabe como llamar no es un
     * contacto.** Es la unica invariante del agregado que no habla de relaciones
     * sino del propio dato, y la que mas facil es incumplir dando de alta a
     * alguien «para acordarse del nombre».
     *
     * La direccion postal no cuenta: sirve para llegar, no para avisar, y un
     * contacto de servicio se usa a la inversa --se le llama para que venga.
     */
    fun isReachable(): Boolean =
        !phone.isNullOrBlank() || !email.isNullOrBlank() || !website.isNullOrBlank()
}

/**
 * A que se dedica.
 *
 * **Lista cerrada, y es lo contrario de lo que el core decidio para su
 * `Category`.** La diferencia es deliberada y esta razonada en la ficha del
 * modulo: la categoria del core clasifica **lo que el hogar tiene**, que no tiene
 * fin porque una casa compra cosas que nadie previo; esta clasifica **a que se
 * dedica quien viene a casa**, que es un mundo mucho mas corto. `OTHER` es la
 * salida, y a la vez el disparador de revisarlo: el dia que sea la mas usada, la
 * lista dejo de clasificar y toca convertirla en catalogo por hogar.
 *
 * Los valores son identificadores, asi que van en ingles y en
 * `UPPER_SNAKE_CASE`. El rotulo que se lee en pantalla es un dato, va en
 * castellano y lo pone el cliente: guardarlo aqui daria dos versiones del mismo
 * nombre, que es el defecto que el catalogo de modulos existe para evitar.
 */
enum class ServiceCategory {
    PLUMBING,
    ELECTRICITY,
    HEATING_COOLING,
    APPLIANCES,
    CARPENTRY,
    MASONRY,
    PAINTING,
    LOCKSMITH,
    CLEANING,
    GARDENING,
    PEST_CONTROL,
    VEHICLE,
    UTILITIES,
    OTHER,
}

/**
 * La relacion de un contacto con algo que el core ya tiene dado de alta: el
 * servicio tecnico **de la caldera**, el taller **del coche**, el fontanero **del
 * piso de arriba**.
 *
 * No tiene vida propia: no se consulta sin su contacto, no se transfiere y
 * desaparece con el.
 */
data class SupplierLink(
    val id: UUID,
    val supplierId: UUID,
    val target: LinkTarget,
    val createdAt: Instant,
    val createdBy: UUID?,
)

/**
 * Un enlace apunta a **exactamente una** cosa, y con un tipo sellado eso deja de
 * ser una regla que alguien tiene que recordar.
 *
 * Es la misma eleccion que la ADR-011 hizo con `CheckOwner`: con dos `UUID?` a
 * secas, «ninguno» y «los dos» se escriben igual de facil que el caso correcto y
 * no fallan hasta que alguien los lee. La tabla los guarda en dos columnas
 * excluyentes con su `CHECK`, que es como estan tambien `documents` y `loans` en
 * el core; la traduccion vive en el repositorio y hacia arriba solo existe esto.
 */
sealed interface LinkTarget {

    data class Asset(val assetId: UUID) : LinkTarget

    data class Location(val locationId: UUID) : LinkTarget

    companion object {

        /**
         * Lo que llega del cuerpo de la peticion --dos identificadores
         * opcionales-- convertido en un destino, o **nulo si no es ninguno de
         * los dos casos validos**.
         *
         * Vive en el dominio y no en el controlador porque es una regla y no una
         * forma: «exactamente uno» es lo que el contrato declara con un codigo de
         * error propio, igual que hace el core con el destino de un documento.
         * Devolver nulo en lugar de lanzar deja que quien llama elija el codigo,
         * que es lo que separa el dominio del transporte.
         */
        fun from(assetId: UUID?, locationId: UUID?): LinkTarget? = when {
            assetId != null && locationId == null -> Asset(assetId)
            locationId != null && assetId == null -> Location(locationId)
            else -> null
        }
    }
}

/**
 * Un enlace con el nombre de aquello a lo que apunta.
 *
 * Existe porque una ficha que ensene «asset 3f2a...» no sirve de nada, y el nombre
 * lo tiene el core. El modulo lo lee --por SQL, sin importar ninguna clase suya--
 * y no lo copia: si la caldera se renombra, esto lo dice al leer.
 */
data class LinkedTarget(
    val link: SupplierLink,
    val name: String,
)
