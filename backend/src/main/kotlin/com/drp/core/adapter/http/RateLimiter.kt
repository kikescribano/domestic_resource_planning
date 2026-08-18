package com.drp.core.adapter.http

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Contador de ventana fija, en memoria del proceso.
 *
 * En memoria porque DRP es un monolito en un VPS: no hay segunda instancia con
 * la que compartir el recuento, asi que meter Redis solo para esto seria una
 * pieza mas que desplegar y vigilar a cambio de nada. El dia que haya mas de un
 * proceso, este es el sitio que hay que sustituir --y por eso esta detras de una
 * interfaz de dos metodos y no repartido por los controladores.
 *
 * Ventana fija y no deslizante a proposito: la deslizante exige guardar la marca
 * de cada intento, y aqui basta con encarecer el abuso. El caso peor conocido de
 * la ventana fija --el doble del limite a caballo entre dos ventanas-- no cambia
 * nada de lo que esto protege.
 */
class FixedWindowRateLimiter(
    private val clock: Clock,
    private val window: Duration,
) {

    private val counters = ConcurrentHashMap<String, Window>()

    /**
     * Consume un intento. Devuelve nulo si cabe, o los segundos que hay que
     * esperar si se ha pasado del limite.
     */
    fun consume(key: String, limit: Int): Long? {
        val now = clock.instant()
        evictExpired(now)

        val current = counters.compute(key) { _, existing ->
            if (existing == null || !existing.isOpenAt(now)) Window(now.plus(window)) else existing
        }!!

        return if (current.attempts.incrementAndGet() > limit) {
            Duration.between(now, current.endsAt).seconds.coerceAtLeast(1)
        } else {
            null
        }
    }

    /**
     * Las ventanas caducadas se retiran al pasar por aqui.
     *
     * Sin esto el mapa crece con cada IP y cada correo que haya llamado alguna
     * vez, que en un endpoint publico es una fuga de memoria a la que solo le
     * hace falta tiempo.
     */
    private fun evictExpired(now: Instant) {
        if (counters.size < EVICTION_THRESHOLD) return
        counters.entries.removeIf { !it.value.isOpenAt(now) }
    }

    private class Window(val endsAt: Instant) {
        val attempts = AtomicInteger(0)

        fun isOpenAt(now: Instant): Boolean = endsAt.isAfter(now)
    }

    private companion object {
        const val EVICTION_THRESHOLD = 1_000
    }
}
