package com.drp.platform.directory

import com.drp.platform.module.ModuleActivation
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * **Como lee un modulo el dato maestro de otro.**
 *
 * Es la pregunta que la ficha de Proveedores dejo abierta con destinatario en el
 * Hito 4 y que la [ADR-010] senala en su condicion de revision: «un modulo
 * necesita saber de otro mas alla de lo que el bus entrega; si el caso es
 * legitimo, lo que hace falta es un puerto en plataforma, no una excepcion».
 * Este es ese puerto.
 *
 * **La alternativa descartada eran eventos que el consumidor materializase en su
 * propio lado**, y no se descarta por comodidad sino porque **una copia
 * alimentada por eventos no se puede sembrar**: la entrega del bus es
 * at-least-once y en memoria, asi que un hogar que encienda Compras hoy no vio el
 * alta de hace un mes, y la ADR-010 resuelve ese caso mandando sembrar **desde el
 * estado** --que en este caso vive en las tablas del otro modulo, justo lo que no
 * se puede leer--. La copia naceria vacia para siempre. El razonamiento entero
 * esta en `docs/backend/modules/purchasing.md`.
 *
 * **Y aqui esta el cuidado que este fichero tiene y que se olvida facil.** Un
 * puerto en plataforma tiene un precio conocido: que plataforma acabe **nombrando
 * un concepto de un modulo**, que es el residuo exacto que la mudanza de
 * `ErrorCode` dejo anotado para vigilar en el Hito 2. Por eso esto **no se llama
 * `SupplierDirectory` ni habla de proveedores en ninguna linea**: declara *el
 * mecanismo* --un modulo publica un dato maestro y otro lo consulta por la clave
 * de su dueno-- con la misma forma que `ModuleSeeder` y `ScheduledCheck` ya
 * tenian. Plataforma no aprende la palabra «proveedor»; aprende que hay datos
 * maestros. La cuarta regla de ArchUnit --plataforma no conoce a sus usuarios--
 * sigue siendo cierta en la letra y en el espiritu.
 *
 * Lo implementa **quien posee el dato**, en su propio arbol.
 */
interface MasterDataDirectory {

    /** De que modulo es este dato maestro. Es como se le pide, y como se sabe si esta encendido. */
    val moduleKey: String

    /** Una entrada por su identificador, o nulo si no existe **para este hogar**. */
    fun find(id: UUID): MasterDataEntry?

    /**
     * Las entradas ofrecibles, filtradas por texto si lo hay.
     *
     * Devuelve una lista y no una pagina a proposito: esto alimenta un selector,
     * no un listado. Quien quiera el listado entero tiene la API del modulo
     * dueno, que es donde vive con sus filtros y su paginacion.
     */
    fun search(query: String?, limit: Int): List<MasterDataEntry>
}

/**
 * Lo minimo que un dato maestro ensena a quien no es su dueno: **quien es y como
 * se llama**.
 *
 * Deliberadamente pobre. Todo lo que un consumidor necesita de un dato maestro
 * ajeno es poder elegirlo, guardarlo y volver a pintarlo; lo demas se lo pide a
 * su dueno, que es quien sabe que significa. Un puerto que devolviera la entidad
 * entera convertiria cada campo nuevo del modulo dueno en una negociacion con
 * todos sus lectores.
 *
 * @param detail lo que el dueno considere que distingue una entrada de otra
 *   parecida --a que se dedica, donde esta--, o nulo si el nombre basta. **Lo
 *   decide el dueno y plataforma no interpreta su contenido**: Proveedores pone
 *   aqui el identificador de su categoria de servicio, y no su rotulo, porque su
 *   ficha decidio que el texto que se lee en pantalla lo pone el cliente. Un
 *   dueno que ponga texto para leer tampoco se equivoca; lo que no cabe es que
 *   plataforma decida cual de las dos cosas es.
 */
data class MasterDataEntry(
    val id: UUID,
    val name: String,
    val detail: String? = null,
)

/**
 * Por donde se pregunta, y **donde vive la degradacion**.
 *
 * Esta clase existe por una sola razon, y es la que convierte el puerto en una
 * decision segura: **comprueba la activacion del modulo dueno antes de
 * preguntarle nada**. Un directorio de un modulo apagado responde vacio, y el
 * consumidor no tiene una sola rama para ello --ni tiene que acordarse de
 * ponerla, que es lo mismo pero peor--.
 *
 * De ahi que el consumidor **no pueda distinguir «ese modulo esta apagado» de
 * «esa entrada no existe»**, y que eso sea lo correcto: es la misma regla con la
 * que el core responde igual a lo que no existe y a lo que es de otro hogar,
 * porque distinguirlo convierte cualquier identificador en un oraculo. Aqui el
 * oraculo seria sobre que modulos tiene encendidos el hogar, que no es asunto de
 * quien pregunta.
 *
 * Los directorios se recogen del contexto, igual que los `ModuleDescriptor` y los
 * `ScheduledCheck`: plataforma no enumera a nadie.
 */
@Component
class MasterData(
    directories: List<MasterDataDirectory>,
    private val activation: ModuleActivation,
) {

    private val byModule: Map<String, MasterDataDirectory> = directories.associateBy { it.moduleKey }

    init {
        require(directories.size == byModule.size) {
            "Hay modulos con dos directorios de dato maestro: ${directories.map { it.moduleKey }}"
        }
    }

    fun find(moduleKey: String, id: UUID): MasterDataEntry? = active(moduleKey)?.find(id)

    fun search(moduleKey: String, query: String?, limit: Int): List<MasterDataEntry> =
        active(moduleKey)?.search(query, limit).orEmpty()

    /**
     * El directorio de [moduleKey], **solo si el hogar lo tiene encendido**.
     *
     * La comprobacion va aqui y no en cada directorio por lo mismo que
     * `ModuleEventHandler` abre la transaccion por el modulo: es una regla que se
     * incumple una sola vez y cuyo sintoma --un modulo apagado que sigue
     * respondiendo-- no se parece a la causa.
     */
    private fun active(moduleKey: String): MasterDataDirectory? =
        byModule[moduleKey]?.takeIf { activation.isActive(moduleKey) }
}
