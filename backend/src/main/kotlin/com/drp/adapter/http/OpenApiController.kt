package com.drp.adapter.http

import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class OpenApiController {

    /**
     * Sirve el openapi.yaml desde los recursos de la aplicación.
     *
     * Swagger UI lo solicita desde http://localhost:8080/openapi.yaml
     * para cargar la especificación de la API.
     */
    @GetMapping("/openapi.yaml", produces = ["application/yaml"])
    fun getOpenApiSpec(): ResponseEntity<String> {
        val resource = ClassPathResource("openapi.yaml")
        val content = resource.inputStream.bufferedReader().use { it.readText() }
        return ResponseEntity.ok()
            .contentType(MediaType("application", "yaml"))
            .body(content)
    }
}
