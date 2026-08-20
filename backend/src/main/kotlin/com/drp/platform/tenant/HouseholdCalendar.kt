package com.drp.platform.tenant

import java.time.LocalDate

/**
 * **Que dia es hoy para el hogar en cuyo nombre se esta trabajando.**
 *
 * La frontera no es «CMMS» ni «Warehouse», es `LocalDate` frente a `Instant`.
 * Comparar instantes no necesita ninguna zona, y por eso el vencimiento de un
 * prestamo no la usa: `dueAt` es un `timestamptz` y «la fecha prevista ya paso»
 * es una comparacion de instantes. Un **dia de calendario** es otra cosa: no
 * significa nada sin una zona, y la unica defendible para una regla de un hogar
 * es la del hogar.
 *
 * De ahi que esto exista en lugar de derivar el dia del reloj. El bean `Clock`
 * es `Clock.systemUTC()`, asi que su zona es **la del despliegue** y no la de
 * nadie; y cambiarla ahi no arregla nada, porque un hogar por instancia no es el
 * modelo de este proyecto. Lo que hace falta no es un reloj distinto sino la
 * pregunta correcta, que es esta.
 *
 * **Lo declara plataforma y lo implementa el core**, igual que
 * [HouseholdDirectory] y [com.drp.platform.notice.NoticeRecipients]: quien sabe
 * de hogares es el core, y plataforma no puede preguntarselo sin apoyarse en el
 * --la tercera regla de ArchUnit de la ADR-010--. Y quien pregunta es un modulo,
 * que tampoco puede importar al core.
 *
 * **Se descarto llevar la zona en `SessionClaims`.** Habria evitado la lectura,
 * pero engorda justo la unica excepcion nombrada de esa regla --que tiene una
 * prueba afirmando que sigue teniendo un solo nombre-- y ademas no sirve donde
 * mas falta hace: el recorrido diario no tiene sesion de la que sacarla.
 */
interface HouseholdCalendar {

    /**
     * El dia de hoy en la zona del hogar actual.
     *
     * **Exige contexto de inquilino** y falla ruidosamente sin el, por lo mismo
     * que [TenantAwareTransactionManager]: devolver el dia de UTC «por si acaso»
     * convertiria «no he podido resolver el hogar» en «hoy es otro dia», y desde
     * arriba las dos cosas son indistinguibles --la regla se aplica, con la
     * fecha equivocada, y nadie se entera.
     */
    fun today(): LocalDate
}
