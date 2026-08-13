package com.drp.adapter.http

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.util.UUID

/**
 * Limite de frecuencia de las subidas, **por identidad**.
 *
 * Es el control «limitar tamano y frecuencia» de la File Upload Cheat Sheet, y va
 * aparte del [RateLimitFilter] por dos motivos que no son de estilo:
 *
 * - **Por identidad y no por IP.** Aqui ya se sabe quien pide, y una casa entera
 *   comparte IP: limitar por ella castigaria a quien no ha hecho nada.
 * - **Sin tocar el cuerpo.** El filtro lee el cuerpo para sacar el correo, y
 *   hacerlo aqui significaria reservar 25 MB en memoria antes de decidir si la
 *   peticion se atiende. Este se aplica en el controlador, con la cabecera leida
 *   y el cuerpo todavia sin recibir.
 */
@Component
class UploadRateLimiter(
    clock: Clock,
    @Value("\${drp.rate-limit.window}") window: Duration,
    @Value("\${drp.rate-limit.per-identity-upload}") private val limit: Int,
) {

    private val limiter = FixedWindowRateLimiter(clock, window)

    fun consume(identityId: UUID) {
        limiter.consume("upload:$identityId", limit)?.let { throw RateLimited(it) }
    }
}

/** `429` con el `Retry-After` que el contrato declara. */
class RateLimited(val retryAfterSeconds: Long) : RuntimeException("Demasiadas peticiones; espera un poco")
