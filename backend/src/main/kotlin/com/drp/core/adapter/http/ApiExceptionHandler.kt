package com.drp.core.adapter.http

import com.drp.core.domain.BusinessRuleViolation
import com.drp.core.domain.ErrorCode
import com.drp.core.domain.ResourceNotFound
import com.drp.core.domain.ValidationFailure
import com.drp.platform.module.UnknownModule
import com.drp.core.application.usecase.AuthenticationFailed
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.web.servlet.resource.NoResourceFoundException

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

    /**
     * `409` salvo los dos que el contrato saca de esa familia: el tamano y el
     * tipo de un fichero tienen codigo de estado propio en HTTP --`413` y
     * `415`-- y usarlos es lo que deja que un cliente generico reaccione sin
     * leer el cuerpo.
     *
     * La correspondencia vive aqui y no en el enumerado del dominio porque es
     * informacion de transporte: el dominio no sabe que existe HTTP.
     */
    @ExceptionHandler(BusinessRuleViolation::class)
    fun onBusinessRule(failure: BusinessRuleViolation): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(STATUS_BY_CODE[failure.code] ?: HttpStatus.CONFLICT)
            .body(ErrorResponse(failure.code.name, failure.message))

    /**
     * El tope duro de 25 MB, que **corta Tomcat antes de que el cuerpo llegue al
     * caso de uso** (5.8.3, paso 1). Sin este manejador saldria como `500`, que
     * es mentir sobre de quien es la culpa.
     */
    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun onUploadTooLarge(): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(ErrorResponse(ErrorCode.FILE_TOO_LARGE.name, "El fichero supera el tamaño máximo permitido"))

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
     * Lo que rechazan los `PATCH`, que leen el cuerpo como arbol JSON en crudo
     * para poder distinguir «no menciones este campo» de «ponlo a nulo».
     *
     * Esa lectura no pasa por Bean Validation, asi que un enumerado inventado o
     * un identificador mal formado llegan como `IllegalArgumentException`. Sin
     * este manejador acabarian en el `catch` general y responderian `500`, que
     * es mentir sobre de quien es la culpa: el cuerpo esta mal, no el servidor.
     */
    @ExceptionHandler(IllegalArgumentException::class)
    fun onMalformedValue(failure: IllegalArgumentException): ResponseEntity<ErrorResponse> =
        ResponseEntity.badRequest()
            .body(ErrorResponse(VALIDATION_ERROR, "El cuerpo no cumple el contrato"))

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

    /** El limite de subidas por identidad, que se aplica ya dentro del controlador. */
    @ExceptionHandler(RateLimited::class)
    fun onRateLimited(failure: RateLimited): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header(org.springframework.http.HttpHeaders.RETRY_AFTER, failure.retryAfterSeconds.toString())
            .body(ErrorResponse(RATE_LIMITED, failure.message ?: "Demasiadas peticiones"))

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
     *
     * Lleva tambien la clave de modulo desconocida, que es lo mismo visto desde
     * plataforma: se declara aparte porque plataforma no puede lanzar el error
     * del core sin invertir la frontera que la ADR-010 fija.
     */
    @ExceptionHandler(ResourceNotFound::class, UnknownModule::class)
    fun onNotFound(failure: RuntimeException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(NOT_FOUND, failure.message ?: "No encontrado"))

    /**
     * Una ruta que **no corresponde a ningun manejador**: `404`, igual que un
     * recurso que no existe, y con la misma forma de error.
     *
     * Sin este manejador la excepcion caia en el `catch` general de mas abajo y
     * salia como `500`. Eso rompe dos cosas a la vez. Una es el contrato: `500` le
     * dice a un cliente generico «reintenta, el servidor esta mal» cuando lo
     * cierto es «esa ruta no existe, no la vuelvas a pedir». La otra es el
     * registro, y es la que mas cuesta despues: cada `404` de un cliente quedaba
     * anotado con `log.error` y su traza, asi que un enlace viejo o un barrido
     * ajeno ensucian el registro con errores del servidor que no lo son. Lo que se
     * pierde ahi no es el ruido: es la senal, porque un `500` de verdad deja de
     * distinguirse del fondo.
     *
     * Se declaran las **dos** excepciones a proposito. La que lanza Spring hoy es
     * `NoResourceFoundException`, porque el manejador de recursos estaticos cuelga
     * de la raiz y es quien acaba atendiendo lo que ningun controlador reclama;
     * `NoHandlerFoundException` es la que saldria si algun dia se activa
     * `spring.mvc.throw-exception-if-no-handler-found`. Cubrir solo la de hoy
     * dejaria el `500` esperando a que alguien toque esa propiedad.
     *
     * El mensaje es constante y no repite la ruta pedida: un cuerpo de error no es
     * sitio para devolver lo que mando el cliente. El detalle, al log y en `debug`,
     * que es el nivel que le corresponde a un error del cliente.
     */
    @ExceptionHandler(NoResourceFoundException::class, NoHandlerFoundException::class)
    fun onUnknownRoute(failure: Exception): ResponseEntity<ErrorResponse> {
        log.debug("Ninguna ruta atiende la petición: {}", failure.message)
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(NOT_FOUND, "La ruta solicitada no existe"))
    }

    /**
     * La ruta **si** existe, pero no para ese metodo: `405`, y no el `404` de
     * arriba ni el `500` de abajo.
     *
     * Es el mismo defecto que el anterior --un error del cliente contado como
     * error del servidor-- y hay que separarlo porque su respuesta correcta es
     * otra. El caso llega antes de lo que parece: `POST /api/v1/assets/{id}merge`,
     * con la barra olvidada, **casa** con el patron `/{id}` de `GET`, `PATCH` y
     * `DELETE` --tomando `{id}merge` entero como identificador-- y lo unico que
     * falla es el metodo. Decir `404` ahi seria mentir en el otro sentido:
     * afirmaria que no hay nada en esa ruta.
     *
     * La cabecera `Allow` no es adorno: RFC 9110 la exige en toda respuesta `405`,
     * y es lo que deja que un cliente sepa que hacer sin adivinar. La lista la da
     * la propia excepcion, que sabe que metodos declara el manejador.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun onMethodNotSupported(failure: HttpRequestMethodNotSupportedException): ResponseEntity<ErrorResponse> {
        log.debug("Método no admitido en la ruta: {}", failure.message)
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
            .apply { failure.supportedHttpMethods?.let { header(HttpHeaders.ALLOW, it.joinToString(", ")) } }
            .body(ErrorResponse(METHOD_NOT_ALLOWED, "El método no está permitido en esta ruta"))
    }

    /**
     * El cuerpo nunca lleva detalle interno: la traza va al log, no a la respuesta.
     *
     * **Este es el ultimo recurso y tiene que seguir siendolo.** Todo lo que se le
     * quite hay que quitarselo por su tipo concreto, como hacen los manejadores de
     * arriba: ensanchar una traduccion para que se lleve tambien lo que no
     * reconoce --devolver `404` ante cualquier excepcion desconocida, por
     * ejemplo-- esconde los fallos reales del servidor detras de la respuesta mas
     * inocente que hay, y ya no hay nada que los registre.
     */
    @ExceptionHandler(Exception::class)
    fun onUnexpected(failure: Exception): ResponseEntity<ErrorResponse> {
        log.error("Error no previsto atendiendo la petición", failure)
        return ResponseEntity.internalServerError()
            .body(ErrorResponse(INTERNAL_ERROR, "Error interno"))
    }

    private companion object {
        /** Los dos codigos de negocio a los que el contrato asigna un estado que no es `409`. */
        val STATUS_BY_CODE = mapOf(
            ErrorCode.FILE_TOO_LARGE to HttpStatus.PAYLOAD_TOO_LARGE,
            ErrorCode.FILE_TYPE_NOT_ALLOWED to HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        )

        const val VALIDATION_ERROR = "VALIDATION_ERROR"
        const val INTERNAL_ERROR = "INTERNAL_ERROR"

        // No estan en el enumerado del contrato porque ahi solo viven los
        // codigos de regla de negocio; el significado de estos lo lleva ya el
        // codigo de estado.
        const val UNAUTHORIZED = "UNAUTHORIZED"
        const val FORBIDDEN = "FORBIDDEN"
        const val NOT_FOUND = "NOT_FOUND"
        const val METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED"
        const val RATE_LIMITED = "RATE_LIMITED"
    }
}
