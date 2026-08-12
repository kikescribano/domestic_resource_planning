package com.drp.domain

/**
 * Codigos de error de negocio, tal y como los enumera el contrato.
 *
 * La lista no se inventa aqui: es la del esquema `Error` de `openapi.yaml`, que
 * la ADR-007 declara fuente de verdad. Un codigo que no este ahi es un fallo del
 * contrato, no un caso que el frontend deba adivinar, asi que este enumerado
 * existe para que anadir uno obligue a tocar tambien el contrato.
 *
 * Estan los del Hito 1 --enrolamiento-- y los del Hito 2 --catalogo, ubicaciones
 * y assets--. Los de ficheros y prestamos entran con los suyos.
 */
enum class ErrorCode {
    ALREADY_MEMBER,
    ARTICLE_DUPLICATE,
    ARTICLE_HAS_EXISTENCES,
    ARTICLE_UNIT_IMMUTABLE,
    ASSET_HAS_ACTIVE_LOAN,
    ASSET_HAS_CHILDREN,
    ASSET_LOCATION_CONFLICT,
    ASSET_QUANTITY_NEGATIVE,
    ASSET_QUANTITY_NOT_APPLICABLE,
    CATEGORY_DUPLICATE,
    CURRENT_PASSWORD_INVALID,
    EMAIL_NOT_VERIFIED,
    EXISTENCE_ALREADY_IN_LOCATION,
    IDENTITY_ALREADY_MEMBER,
    INTAKE_QUANTITY_NOT_POSITIVE,
    INVITATION_ALREADY_PENDING,
    INVITATION_TOKEN_INVALID,
    LOCATION_CYCLE,
    LOCATION_DUPLICATE,
    LOCATION_HAS_ASSETS,
    LOCATION_HAS_CHILDREN,
    MERGE_ARTICLE_MISMATCH,
    MERGE_ASSET_DEACTIVATED,
    MERGE_NOT_CONSUMABLE,
    MERGE_SAME_ASSET,
    RESET_TOKEN_INVALID,
    USER_LAST_ADMIN,
    VERIFICATION_TOKEN_INVALID,
}

/**
 * Una regla de negocio incumplida. Se traduce a `409` con su codigo.
 *
 * Es distinto de un error de forma --cuerpo mal construido, campo que falta--,
 * que responde `400` con `VALIDATION_ERROR`. La separacion es del contrato y no
 * un detalle de implementacion: el cliente decide que hacer por el codigo, y
 * mezclar las dos familias le obliga a distinguir por el texto.
 */
class BusinessRuleViolation(
    val code: ErrorCode,
    override val message: String,
) : RuntimeException(message)

/**
 * El cuerpo o los parametros no cumplen el contrato. Se traduce a `400` con
 * `VALIDATION_ERROR` y un campo por atributo rechazado.
 *
 * Que la contrasena sea demasiado corta o demasiado comun cae aqui y no en un
 * `409`: es la forma de lo enviado lo que falla, no una regla de negocio sobre
 * el estado del hogar.
 */
class ValidationFailure(val details: Map<String, String>) : RuntimeException(
    details.entries.joinToString { "${it.key}: ${it.value}" },
)

/**
 * Lo pedido no existe, o no existe **para quien lo pide**.
 *
 * Las dos cosas responden igual a proposito. Distinguir "no existe" de "existe
 * pero no es tuyo" convierte cualquier identificador en un oraculo con el que
 * averiguar que hay en otros hogares.
 */
class ResourceNotFound(override val message: String) : RuntimeException(message)
