package com.drp.platform.tenant

import java.util.UUID

/**
 * El hogar en cuyo nombre se esta trabajando ahora mismo.
 *
 * Es la unica fuente del `householdId` para todo el codigo de aplicacion. Nunca
 * se toma de un parametro, de un cuerpo de peticion ni de una ruta: viene del
 * claim del token, lo pone el filtro de seguridad al autenticar y de aqui lo lee
 * el gestor de transacciones para fijar `app.household_id` en PostgreSQL.
 *
 * Que el valor sea nulo es un estado normal y no un error: lo estan las
 * peticiones sin autenticar --el alta de un hogar, el login, la verificacion--.
 * Lo que ocurre entonces es que la sesion de base de datos se queda sin hogar y
 * las politicas no dejan pasar ninguna fila, que es la unica forma segura de
 * fallar.
 */
interface TenantContext {

    /** El hogar actual, o nulo si no se esta trabajando en nombre de ninguno. */
    fun currentHousehold(): UUID?

    /**
     * Ejecuta [block] en nombre de [householdId], restaurando al terminar el
     * hogar que hubiera antes.
     *
     * Hay dos sitios donde hace falta fijarlo a mano en lugar de heredarlo del
     * token. Uno es `CreateHousehold`, que no parte de ningun hogar porque lo
     * esta creando: la aplicacion genera el identificador y abre la transaccion
     * ya dentro de ese contexto, para que todo lo que inserta nazca dentro del
     * inquilino. El otro son los procesos diarios, que recorren los hogares uno
     * a uno.
     */
    fun <T> runAs(householdId: UUID?, block: () -> T): T
}
