package com.drp.application.port

import java.io.InputStream

/**
 * El almacen de bytes (ADR-005).
 *
 * Existe como puerto por una razon acotada y honesta: **la portabilidad se compra
 * donde no cuesta nada**. Hoy debajo hay un directorio del servidor; el dia que
 * haya un almacen de objetos, se sustituye el adaptador y ni el dominio ni los
 * casos de uso se enteran.
 *
 * Lo que **no** hace es aislar hogares. El sistema de ficheros no tiene
 * Row-Level Security: la clave que recibe cada metodo sale siempre de una fila
 * que ya paso por la politica, y esa es toda la herencia que hay. Construir una
 * clave con algo que venga del cliente desactiva el aislamiento sin producir
 * ningun error visible.
 */
interface FileStorage {

    /**
     * Copia el flujo a un area temporal, **contando bytes** y abortando en cuanto
     * se pasa de [maxBytes].
     *
     * Es el paso 3 de 5.8.3, el unico que ocurre sin ningun bloqueo tomado, y el
     * contador es lo que impide creerse el `Content-Length`: quien lo escribe es
     * el cliente.
     */
    fun stage(source: InputStream, maxBytes: Long): StagedContent

    /**
     * Escribe [source] bajo [key] y devuelve lo que se guardo de verdad.
     *
     * El adaptador escribe primero a un temporal y despues mueve, de modo que
     * bajo la clave definitiva no llega a existir un fichero a medias. Es lo que
     * sostiene el orden de 5.8.3: **los bytes antes que la fila**, para que en
     * cualquier instante el disco contenga todo lo que la base de datos da por
     * bueno.
     */
    fun write(key: String, source: InputStream): WrittenBytes

    /** Nulo si no hay nada bajo esa clave, que es lo que ocurre tras una restauracion desalineada. */
    fun openRead(key: String): InputStream?

    /** Idempotente: borrar lo que ya no esta no es un error. */
    fun delete(key: String)
}

/**
 * Lo subido, ya en disco y aun sin validar. Se cierra siempre --de ahi el
 * [AutoCloseable]--: un temporal que sobrevive a la peticion es basura que nadie
 * vuelve a mirar.
 */
interface StagedContent : AutoCloseable {
    val sizeBytes: Long

    /** Se puede abrir varias veces: la deteccion del tipo lee la cabecera y la recodificacion, todo. */
    fun open(): InputStream
}

/** Lo que de verdad quedo escrito. El [checksum] es SHA-256 en hexadecimal. */
data class WrittenBytes(val sizeBytes: Long, val checksum: String)

/**
 * Lo subido pasa de [StagedContent.sizeBytes] permitidos.
 *
 * Es distinto del tope que corta el contenedor de servlets antes de leer el
 * cuerpo: aqui ya se estan recibiendo bytes, y lo que se ha superado es **lo
 * reservado**, es decir, el cliente mintio en el `Content-Length`.
 */
class ContentTooLarge(val maxBytes: Long) : RuntimeException("El contenido supera los $maxBytes bytes reservados")
