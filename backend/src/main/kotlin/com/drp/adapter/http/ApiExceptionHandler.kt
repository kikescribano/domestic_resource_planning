package com.drp.adapter.http

import com.drp.domain.BusinessRuleViolation
import com.drp.domain.ResourceNotFound
import com.drp.domain.ValidationFailure
import com.drp.application.usecase.AuthenticationFailed
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

/**
 * La traduccion de errores a la forma unica del contrato.
 *
 * Las dos familias no se mezclan, y esa separacion es del contrato y no un
 * detalle: los errores de **forma** responden `400` con `VALIDATION_ERROR`, y los
 * de **regla de negocio** responden `409` con su codigo concreto. El cliente
 * decide que hacer por el codigo, asi que un codigo nuevo aqui obliga a anadirlo
 * tambien al enumerado de `openapi.yaml`.
 */
@RestControllerAdvice
class ApiExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(BusinessRuleViolation::class)
    fun onBusinessRule(failure: BusinessRuleViolation): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse(failure.code.name, failure.message))

    @ExceptionHandler(ValidationFailure::class)
    fun onValidationFailure(failure: ValidationFailure): ResponseEntity<ErrorResponse> =
        ResponseEntity.badRequest()
            .body(ErrorResponse(VALIDATION_ERROR, "El cuerpo no cumple el contrato", failure.details))

    /** Lo que rechaza Bean Validation: un campo por atributo, como pide el contrato. */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun onInvalidBody(failure: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val details = failure.bindingResult.fieldErrors.associate {
            it.field to (it.defaultMessage ?: "no válido")
        }
        return ResponseEntity.badRequest()
            .body(ErrorResponse(VALIDATION_ERROR, "El cuerpo no cumple el contrato", details))
    }

    @ExceptionHandler(HttpMessageNotReadableException::class, MethodArgumentTypeMismatchException::class)
    fun onUnreadable(failure: Exception): ResponseEntity<ErrorResponse> =
        ResponseEntity.badRequest()
            .body(ErrorResponse(VALIDATION_ERROR, "El cuerpo o los parámetros no se pueden interpretar"))

    /**
     * Credenciales. `401` y no `403`: no se ha podido establecer quien eres.
     *
     * El cuerpo lleva codigo solo cuando el contrato lo declara --el caso del
     * correo sin verificar, que el usuario necesita distinguir para saber que le
     * toca mirar el correo--. Una contrasena equivocada no lleva ninguno: no hay
     * nada que el cliente deba hacer distinto, y decirlo ayudaria a quien prueba.
     */
    @ExceptionHandler(AuthenticationFailed::class)
    fun onAuthenticationFailed(failure: AuthenticationFailed): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse(failure.code?.name ?: UNAUTHORIZED, failure.message))

    @ExceptionHandler(AccessDeniedException::class)
    fun onAccessDenied(): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse(FORBIDDEN, "No autorizado para esta operación"))

    /**
     * `404` tambien cuando el recurso existe pero es de otro hogar.
     *
     * Distinguir "no existe" de "existe pero no es tuyo" convertiria cualquier
     * identificador en un oraculo con el que averiguar que hay en otros hogares,
     * que es justo lo que la ADR-002 quiere impedir.
     */
    @ExceptionHandler(ResourceNotFound::class)
    fun onNotFound(failure: ResourceNotFound): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(NOT_FOUND, failure.message))

    /** El cuerpo nunca lleva detalle interno: la traza va al log, no a la respuesta. */
    @ExceptionHandler(Exception::class)
    fun onUnexpected(failure: Exception): ResponseEntity<ErrorResponse> {
        log.error("Error no previsto atendiendo la petición", failure)
        return ResponseEntity.internalServerError()
            .body(ErrorResponse(INTERNAL_ERROR, "Error interno"))
    }

    private companion object {
        const val VALIDATION_ERROR = "VALIDATION_ERROR"
        const val INTERNAL_ERROR = "INTERNAL_ERROR"

        // No estan en el enumerado del contrato porque ahi solo viven los
        // codigos de regla de negocio; el significado de estos tres lo lleva ya
        // el codigo de estado.
        const val UNAUTHORIZED = "UNAUTHORIZED"
        const val FORBIDDEN = "FORBIDDEN"
        const val NOT_FOUND = "NOT_FOUND"
    }
}
