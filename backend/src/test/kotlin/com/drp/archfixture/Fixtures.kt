package com.drp.archfixture

/**
 * Un arbol de mentira con las tres dependencias que la ADR-010 prohibe.
 *
 * Existe para medir las reglas **en el otro sentido**. Una regla de ArchUnit que
 * solo se comprueba contra codigo que la cumple no demuestra que la regla mire
 * donde dice mirar: un patron de paquete mal escrito --`com.drp.modules..` en vez
 * de `com.drp.module..`-- pasa igual de verde y no protege de nada. Aqui las
 * mismas reglas se aplican a una raiz que **si** las incumple, y lo que se afirma
 * es que fallan.
 *
 * Va bajo `com.drp.archfixture` y no bajo `com.drp.module`, que es lo importante:
 * si viviera en el arbol de verdad, romperia la construccion en lugar de medirla.
 */
