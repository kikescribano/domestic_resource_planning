package com.drp.platform.error

/**
 * Codigos de error de negocio, tal y como los enumera el contrato.
 *
 * La lista no se inventa aqui: es la del esquema `Error` de `openapi.yaml`, que
 * la ADR-007 declara fuente de verdad. Un codigo que no este ahi es un fallo del
 * contrato, no un caso que el frontend deba adivinar, asi que este enumerado
 * existe para que anadir uno obligue a tocar tambien el contrato.
 *
 * Estan los cuatro hitos de la Fase 1 --enrolamiento; catalogo, ubicaciones y
 * assets; ficheros y documentos; y prestamos-- y, desde la Fase 2, los de los
 * modulos con reglas de negocio: Proveedores en el Hito 2, Warehouse en el 3,
 * Compras en el 4 y Mantenimiento en el 5.
 *
 * **Esto vivia en `com.drp.core.domain` y se mudo aqui en el Hito 2 de la Fase
 * 2**, que es el dia que la [ADR-010] nombraba en su condicion de revision. El
 * motivo no es de orden sino de frontera: un modulo puede lanzar
 * `BusinessRuleViolation` desde el primer dia --la direccion `modulo -> core`
 * esta permitida y ninguna regla de ArchUnit se queja-- y el resultado seria el
 * core enumerando las reglas de sus modulos, que es lo mismo que la segunda regla
 * impide en el otro sentido, solo que sin nada que lo delate. El contrato tiene un
 * unico enumerado de errores en cualquier caso; lo que se decide es **quien lo
 * posee**, y tiene que ser la capa de la que pueden depender los dos lados.
 *
 * Queda un residuo que conviene no perder de vista: plataforma nombra aqui reglas
 * de un modulo. No es una dependencia de codigo --la cuarta regla de ArchUnit
 * sigue midiendo eso, y sigue verde-- sino vocabulario compartido con el
 * contrato. Merece la misma vigilancia que `SessionClaims`.
 *
 * Tres de los de ficheros **no responden `409`**: el contrato les asigna `413` y
 * `415`, que es informacion de transporte y no de dominio. La correspondencia
 * vive en el manejador de errores, que es quien sabe de HTTP.
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
    DOCUMENT_CONTENT_INVALID,
    DOCUMENT_TARGET_INVALID,
    EMAIL_NOT_VERIFIED,
    EXISTENCE_ALREADY_IN_LOCATION,
    FILE_ALREADY_ATTACHED,
    FILE_IN_USE,
    FILE_TOO_LARGE,
    FILE_TYPE_NOT_ALLOWED,
    HOUSEHOLD_CLOSURE_ALREADY_REQUESTED,
    HOUSEHOLD_CLOSURE_NOT_REQUESTED,
    IDENTITY_ALREADY_MEMBER,
    IDENTITY_CLOSED,
    INTAKE_QUANTITY_NOT_POSITIVE,
    INVITATION_ALREADY_PENDING,
    INVITATION_TOKEN_INVALID,
    LOAN_ALREADY_RETURNED,
    LOAN_ASSET_ALREADY_LENT,
    LOAN_ASSET_NOT_DURABLE,
    LOCATION_CYCLE,
    LOCATION_DUPLICATE,
    LOCATION_HAS_ASSETS,
    LOCATION_HAS_CHILDREN,
    MAINTENANCE_ASSET_NOT_ELIGIBLE,
    MAINTENANCE_LEAD_EXCEEDS_INTERVAL,
    MAINTENANCE_PLAN_CANCELLED,
    MAINTENANCE_PLAN_DUPLICATE,
    MAINTENANCE_SUPPLIER_UNKNOWN,
    MERGE_ARTICLE_MISMATCH,
    MERGE_ASSET_DEACTIVATED,
    MERGE_NOT_CONSUMABLE,
    MERGE_SAME_ASSET,
    PURCHASE_EMPTY,
    PURCHASE_NOT_OPEN,
    PURCHASE_SUPPLIER_UNKNOWN,
    RESET_TOKEN_INVALID,
    SHOPPING_ITEM_DUPLICATE,
    SHOPPING_ITEM_NOT_PENDING,
    STOCK_CONSUMPTION_EXCEEDS_QUANTITY,
    STOCK_CONSUMPTION_NOT_POSITIVE,
    STOCK_ITEM_NOT_TRACKED,
    STOCK_LOT_DUPLICATE,
    STOCK_LOT_EXCEEDS_QUANTITY,
    STORAGE_QUOTA_EXCEEDED,
    SUPPLIER_CONTACT_REQUIRED,
    SUPPLIER_DUPLICATE,
    SUPPLIER_LINK_DUPLICATE,
    SUPPLIER_LINK_TARGET_INVALID,
    SUPPLIER_RETIRED,
    TAG_DUPLICATE,
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
