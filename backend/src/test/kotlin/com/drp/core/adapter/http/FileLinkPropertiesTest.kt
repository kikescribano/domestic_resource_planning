package com.drp.core.adapter.http

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * El secreto que firma las URL de ficheros, comprobado al arrancar.
 *
 * Es la pareja de lo que `JwtAccessTokenIssuerTest` fija para la clave del JWT.
 * Que uno tuviera esta comprobacion y el otro no era exactamente lo que hacia
 * peligroso al segundo: el fallo silencioso estaba descrito y remediado en una
 * punta y no en la otra.
 */
class FileLinkPropertiesTest {

    @Test
    @DisplayName("el secreto de ejemplo del repositorio no arranca fuera de desarrollo")
    fun `el secreto de desarrollo no llega a produccion`() {
        val defaulted = FileLinkProperties(FileLinkProperties.DEVELOPMENT_SECRET)

        // Con el puesto en produccion, la firma deja de autorizar nada: mide 38
        // bytes --asi que pasa el minimo de longitud-- y esta publicado tanto en
        // el application.yml como en el compose.yaml, de modo que cualquiera
        // arma la firma de cualquier ruta con la caducidad que quiera. Las
        // claves de avatar se derivan solo del identityId, asi que conocer ese
        // UUID basta para construir la URL entera.
        assertThrows<IllegalArgumentException> {
            defaulted.validate(developmentEnvironment = false)
        }

        // En desarrollo sigue valiendo, que es para lo que existe --y ademas
        // tiene que coincidir con el que se le pasa a nginx en el compose.
        defaulted.validate(developmentEnvironment = true)
    }

    @Test
    @DisplayName("un secreto demasiado corto se rechaza al arrancar, tambien en desarrollo")
    fun `el secreto corto falla pronto`() {
        val weak = FileLinkProperties("corto")

        assertThrows<IllegalArgumentException> {
            weak.validate(developmentEnvironment = true)
        }
    }

    @Test
    @DisplayName("un secreto propio y suficientemente largo arranca en produccion")
    fun `el secreto de despliegue vale`() {
        FileLinkProperties("un-secreto-de-despliegue-con-mas-de-32-bytes")
            .validate(developmentEnvironment = false)
    }
}
