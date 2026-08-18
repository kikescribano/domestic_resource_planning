package com.drp.core.adapter.security

import com.drp.core.application.port.SessionClaims
import com.drp.core.domain.household.MemberRole
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class JwtAccessTokenIssuerTest {

    private val now = Instant.parse("2026-08-11T10:00:00Z")
    private val properties = SecurityProperties(
        jwtSecret = "una-clave-de-pruebas-con-mas-de-32-bytes",
        accessTokenTtl = Duration.ofMinutes(15),
        refreshTokenTtl = Duration.ofDays(30),
    )

    private val claims = SessionClaims(
        identityId = UUID.randomUUID(),
        memberId = UUID.randomUUID(),
        householdId = UUID.randomUUID(),
        role = MemberRole.HOUSEHOLD_ADMIN,
        sessionId = UUID.randomUUID(),
    )

    private fun issuerAt(instant: Instant) =
        JwtAccessTokenIssuer(properties, Clock.fixed(instant, ZoneOffset.UTC))

    @Test
    @DisplayName("el token lleva los cuatro claims, y sub y memberId son cosas distintas")
    fun `el token conserva la identidad y la pertenencia`() {
        val issued = issuerAt(now).issue(claims)

        val recovered = issuerAt(now).verify(issued.token)

        recovered.shouldBe(claims)
        issued.expiresInSeconds.shouldBe(900)
    }

    @Test
    @DisplayName("un token caducado no vale, aunque su firma sea correcta")
    fun `el token caduca`() {
        val issued = issuerAt(now).issue(claims)

        // Un segundo despues del limite. La firma sigue siendo valida: lo que
        // falla es la ventana, que es lo unico que acota el dano de un access
        // token robado, porque revocarlo no se puede.
        val afterExpiry = issuerAt(now.plus(Duration.ofMinutes(15)).plusSeconds(1))

        afterExpiry.verify(issued.token).shouldBeNull()
    }

    @Test
    @DisplayName("justo antes de caducar sigue valiendo")
    fun `el limite es un limite y no una barrera`() {
        val issued = issuerAt(now).issue(claims)

        val justBefore = issuerAt(now.plus(Duration.ofMinutes(15)).minusSeconds(1))

        justBefore.verify(issued.token).shouldBe(claims)
    }

    @Test
    @DisplayName("un token firmado con otra clave no vale")
    fun `la firma se comprueba`() {
        val foreign = JwtAccessTokenIssuer(
            properties.copy(jwtSecret = "otra-clave-distinta-de-mas-de-32-bytes"),
            Clock.fixed(now, ZoneOffset.UTC),
        )
        val issued = foreign.issue(claims)

        issuerAt(now).verify(issued.token).shouldBeNull()
    }

    @Test
    @DisplayName("manipular el cuerpo invalida el token: cambiar de hogar por la brava no cuela")
    fun `el cuerpo no se puede reescribir`() {
        val issued = issuerAt(now).issue(claims)
        val parts = issued.token.split(".")

        // Se sustituye la carga util por otra con un householdId distinto,
        // conservando la firma original. Es el ataque evidente contra un sistema
        // multi-inquilino que confia en un claim.
        val forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
            """{"sub":"${claims.identityId}","householdId":"${UUID.randomUUID()}"}""".toByteArray(),
        )
        val forged = "${parts[0]}.$forgedPayload.${parts[2]}"

        issuerAt(now).verify(forged).shouldBeNull()
    }

    @Test
    @DisplayName("lo que no es un JWT tampoco revienta: simplemente no vale")
    fun `una cadena cualquiera no vale`() {
        issuerAt(now).verify("esto-no-es-un-token").shouldBeNull()
        issuerAt(now).verify("").shouldBeNull()
    }

    @Test
    @DisplayName("una clave por debajo de 256 bits se rechaza al arrancar, no en la primera peticion")
    fun `la clave corta falla pronto`() {
        val weak = properties.copy(jwtSecret = "demasiado-corta")

        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            weak.validate(developmentEnvironment = true)
        }
    }

    @Test
    @DisplayName("la clave de ejemplo del repositorio no arranca fuera de desarrollo")
    fun `la clave de desarrollo no llega a produccion`() {
        val defaulted = properties.copy(jwtSecret = SecurityProperties.DEVELOPMENT_SECRET)

        // Mide 43 bytes, asi que pasaba de sobra el minimo de longitud: sin esta
        // comprobacion, un despliegue que olvidara la variable de entorno
        // arrancaba en silencio firmando con una clave publicada en el
        // repositorio. Y como el householdId del token alimenta las politicas de
        // RLS, forjar uno atraviesa las dos capas de aislamiento a la vez.
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            defaulted.validate(developmentEnvironment = false)
        }

        // En desarrollo sigue valiendo, que es para lo que existe.
        defaulted.validate(developmentEnvironment = true)
    }
}
