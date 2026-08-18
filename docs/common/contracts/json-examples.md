# 5.4.3 Contratos JSON (ejemplos)

| Campo | Valor |
|---|---|
| Estado | Vigente |
| Responsable | Equipo DRP |
| Ámbito | Ejemplos de request y response de la API |
| Última revisión | 2026-08-17 |

> Trasladado desde la sección 5.4.3 del [`README principal`](../../../README.md) al iniciar la Fase 1. **Los números de sección se conservan**: hay más de cien referencias cruzadas del tipo «ver 4.1.1» repartidas por el repositorio, y renumerarlas las rompería todas.

El contrato completo, con todos los recursos, parámetros y esquemas de error, se mantiene versionado en el archivo `openapi.yaml` adjunto a este documento (especificación OpenAPI 3.0). Aquí se muestran ejemplos ilustrativos de los recursos más representativos.

**`POST /api/v1/assets`** — request, asset **duradero**
```json
{
  "name": "Estantería de trastero",
  "type": "DURABLE",
  "categoryId": "c1a70de5-...-00000000000b",
  "ownerId": "3d0a1e2c-...-000000000001",
  "location": { "type": "ASSET", "id": "9f21b4a0-...-000000000002" }
}
```

**`POST /api/v1/assets`** — response (`201 Created`)
```json
{
  "id": "7c44f8b1-...-000000000003",
  "name": "Estantería de trastero",
  "type": "DURABLE",
  "categoryId": "c1a70de5-...-00000000000b",
  "category": "Mobiliario",
  "ownerId": "3d0a1e2c-...-000000000001",
  "location": { "type": "ASSET", "id": "9f21b4a0-...-000000000002" },
  "status": "AVAILABLE",
  "createdAt": "2026-08-06T10:15:00Z"
}
```
> `categoryId` es lo que se escribe; `category` es su nombre resuelto para lectura. Mismo patrón que `name` y `unit` con el artículo: se guarda una vez y se resuelve al leer.

**`POST /api/v1/assets`** — la misma alta, cuando la ubicación se queda corta (`201 Created`)
```json
{
  "id": "7c44f8b1-...-000000000004",
  "name": "Caja de tornillos",
  "type": "DURABLE",
  "categoryId": "c1a70de5-...-00000000000c",
  "category": "Herramientas",
  "location": { "type": "LOCATION", "id": "a5b3c7d1-...-000000000010" },
  "status": "AVAILABLE",
  "warnings": [
    {
      "code": "LOCATION_CAPACITY_EXCEEDED",
      "message": "La ubicación declara un máximo de 20 unidades y ya contiene 21"
    }
  ],
  "createdAt": "2026-08-12T09:00:00Z"
}
```
> **El asset se ha creado**: es un `201`, no un `409`. Superar la capacidad declarada de una ubicación **advierte pero no bloquea** (ver 4.1.2), porque el sistema no sabe cuánto ocupa cada cosa —el asset no lleva peso ni volumen— y bloquear con datos incompletos impediría guardar algo que sí cabe. El aviso solo se calcula con capacidad de tipo `UNITS`, que es lo único que se puede contar con certeza. `warnings` va vacío en el caso normal, y aparece igual en `PATCH /assets/{id}` al mover un asset a una ubicación llena.

**`POST /api/v1/documents`** — adjuntar el manual a un **artículo**, no a una unidad
```json
{
  "articleId": "e71c0d93-...-000000000009",
  "type": "MANUAL",
  "url": "https://ejemplo.com/manual-taladro.pdf",
  "description": "Manual de usuario"
}
```
> Colgado del artículo, lo comparten todas las unidades idénticas. La factura y la garantía irían con `assetId`, porque son de la unidad concreta que se compró. Informar los dos, o ninguno, se rechaza con `409` y el código `DOCUMENT_TARGET_INVALID`.

**`POST /api/v1/files`** — response, subir la garantía escaneada
```json
{
  "id": "3f2a55c1-...-00000000001a",
  "originalName": "garantia-caldera.pdf",
  "contentType": "application/pdf",
  "sizeBytes": 184320,
  "checksum": "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
  "url": "https://files.drp.example/f/orig/3f2a55c1-...?e=1786400000&s=Wm5rSk...",
  "thumbnailUrl": null,
  "uploadedAt": "2026-08-10T18:22:04Z",
  "createdAt": "2026-08-10T18:22:01Z",
  "createdBy": "b2d1f0a4-...-000000000003"
}
```
> El `contentType` es el **detectado**, no el que declaró el cliente, y el `sizeBytes` es el del fichero ya almacenado — si era una imagen, después de recodificarla. La `url` viene **firmada y caduca en unos quince minutos**: sirve para enseñar el fichero recién subido, no para guardarla. La `thumbnailUrl` es nula porque un PDF no tiene miniatura. Un tipo fuera de la lista blanca se rechaza con `415` y `FILE_TYPE_NOT_ALLOWED`; pasar de 25 MB, con `413`; y agotar el gigabyte del hogar, con `409` y `STORAGE_QUOTA_EXCEEDED`.

**`POST /api/v1/documents`** — el mismo documento, ahora con el fichero recién subido
```json
{
  "assetId": "9c4e1f77-...-000000000004",
  "type": "WARRANTY",
  "fileId": "3f2a55c1-...-00000000001a",
  "description": "Garantía de la caldera",
  "validUntil": "2030-03-14"
}
```
> `url` y `fileId` son excluyentes: informar los dos, o ninguno, se rechaza con `409` y `DOCUMENT_CONTENT_INVALID` — el mismo patrón que `DOCUMENT_TARGET_INVALID` y por el mismo motivo. Adjuntar un fichero que ya cuelga de otro sitio se rechaza con `FILE_ALREADY_ATTACHED`, y uno de otro hogar responde `404`: contestar otra cosa confirmaría que existe.

**`POST /api/v1/households`** — request, alta de un hogar
```json
{
  "name": "Casa de los Escribano",
  "timeZone": "Europe/Madrid",
  "admin": {
    "name": "Kike",
    "email": "kike@ejemplo.com",
    "password": "..."
  }
}
```
> Sin autenticar. Responde `202 Accepted` **sin cuerpo y siempre igual**, exista o no ya ese correo: contestar otra cosa permitiría averiguar quién está registrado. Lo que ocurra de verdad se explica en el correo que llega. No devuelve sesión — el hogar no es utilizable hasta verificar.

**`POST /api/v1/auth/verify-email`** — request y respuesta
```json
{ "token": "9f3c1a...-token-de-un-solo-uso" }
```
> Devuelve `200` con el par de tokens y publica `HouseholdCreated` si era el alta de un hogar. Un token caducado o ya usado se rechaza con `409` y el código `VERIFICATION_TOKEN_INVALID`.

**`POST /api/v1/articles`** — request, artículo del catálogo
```json
{
  "name": "Harina de trigo",
  "categoryId": "8e3b91a4-...-00000000000c",
  "unit": "GRAM",
  "brand": "Marca Blanca",
  "barcode": "8412345678905"
}
```
> Publica `ArticleCreated`. Un nombre ya existente en el hogar (comparado normalizado) o un `barcode` repetido se rechazan con `409` y el código `ARTICLE_DUPLICATE`.

**`POST /api/v1/assets/intake`** — request, entrada de un **consumible** con artículo ya existente
```json
{
  "articleId": "e71c0d93-...-000000000009",
  "ownerId": "3d0a1e2c-...-000000000001",
  "location": { "type": "LOCATION", "id": "5b83c7d2-...-000000000005" },
  "quantity": 1000
}
```
> La `quantity` va siempre en la `unit` del artículo (aquí, gramos). En lugar de `articleId` puede enviarse un objeto `article` con los mismos campos que `POST /api/v1/articles`, y el artículo se crea en la misma operación.

**`POST /api/v1/assets/intake`** — response cuando **ya había** existencia en esa ubicación (`200 OK`)
```json
{
  "id": "b0f5a217-...-00000000000a",
  "name": "Harina de trigo",
  "type": "CONSUMABLE",
  "categoryId": "8e3b91a4-...-00000000000c",
  "category": "Alimentación",
  "articleId": "e71c0d93-...-000000000009",
  "ownerId": "3d0a1e2c-...-000000000001",
  "location": { "type": "LOCATION", "id": "5b83c7d2-...-000000000005" },
  "status": "AVAILABLE",
  "quantity": 1300,
  "unit": "GRAM",
  "createdAt": "2026-08-06T10:15:00Z"
}
```
> `name`, `category` y `unit` se devuelven resueltos desde el artículo, aunque no se guarden en la fila del asset. La respuesta es `200` porque sumó sobre una existencia previa (había 300 g) y publica `AssetQuantityChanged`; si no hubiera existido, sería `201` con `AssetCreated`.

**`POST /api/v1/assets/{id}/merge`** — fusionar dos existencias del mismo artículo
```json
{ "targetAssetId": "b0f5a217-...-00000000000a" }
```
> `{id}` es la existencia que **desaparece**: queda a `quantity = 0` y `status = DECOMMISSIONED`, y su cantidad se suma a la del destino, que conserva su ubicación y su propietario. La respuesta es `200` con el asset destino ya actualizado. Fusionar existencias de artículos distintos se rechaza con `409` y el código `MERGE_ARTICLE_MISMATCH`.

**`PATCH /api/v1/assets/{id}`** — corregir la cantidad de un consumible
```json
{ "quantity": 700 }
```
> A diferencia de la entrada, aquí la cantidad es **absoluta**: sustituye, no suma. Publica `AssetQuantityChanged`. Enviar `quantity` sobre un `DURABLE`, o un valor negativo, se rechaza con `409` y el código `ASSET_QUANTITY_NOT_APPLICABLE` / `ASSET_QUANTITY_NEGATIVE`.

**`PATCH /api/v1/assets/{id}`** — apuntar el número de serie que no se tenía al dar de alta
```json
{ "serialNumber": "JU-88-2019-4471", "acquiredOn": "2019-11-03" }
```
> Los dos son **la simétrica de `quantity`**: solo valen sobre un `DURABLE`, porque describen una unidad física, y sobre una existencia se rechazan con `400` y `VALIDATION_ERROR` —no con un código de negocio: es la petición la que pide algo que ese tipo de asset no tiene—. Se corrigen después del alta a propósito, que es cuando se saben: la etiqueta con el número está pegada detrás del aparato. Enviarlos a `null` los borra, que es lo que hace falta cuando uno se copió mal.

**`GET /api/v1/loans/{id}`** — response con **token acotado de receptor**
```json
{
  "id": "1a2b3c4d-...-000000000004",
  "assetName": "Taladro",
  "role": "BORROWER",
  "status": "ACTIVE",
  "startedAt": "2026-08-01T09:00:00Z",
  "dueAt": "2026-08-15T09:00:00Z"
}
```
> **Esta es la única operación de toda la API que devuelve dos formas distintas según quién pregunta**, y por eso el contrato declara las dos: `LoanView` es un `oneOf` de `Loan` —la completa— y `LoanExternalView` —esta—. Dejarlo solo en la prosa habría hecho que el cliente generado prometiera `lender` y `borrower` a una pantalla que nunca los recibe.
>
> Lo que la vista acotada **no** lleva es tan deliberado como lo que lleva: ni `assetId`, ni `lender`, ni `borrower`, ni `notes`, ni la autoría. La credencial da acceso a un préstamo, no al hogar que lo registró. `returnedAt` aparece en cuanto se confirma la devolución, para que quien la confirmó la vea hecha.
>
> El `role` sí sale, y es el único campo que la vista acotada tiene y la completa no. No es un dato del préstamo sino de **quién pregunta**, y hace falta porque la mitad del texto de la pantalla externa depende de él: quien prestó reclama que le devuelvan y quien recibió confirma que ha devuelto. No revela nada —quien tiene el token ya sabe en qué extremo está— y evita que el cliente tenga que descodificar el claim del JWT para pintar una frase.

**`GET /api/v1/loans/{id}`** — response del mismo préstamo con **sesión del hogar**
```json
{
  "id": "1a2b3c4d-...-000000000004",
  "assetId": "7c44f8b1-...-000000000003",
  "assetName": "Taladro",
  "lender": { "userId": "3d0a1e2c-...-000000000001" },
  "borrower": { "external": { "name": "Vecino del 3.º", "email": "vecino@example.com" } },
  "status": "ACTIVE",
  "startedAt": "2026-08-01T09:00:00Z",
  "dueAt": "2026-08-15T09:00:00Z",
  "returnedAt": null,
  "notes": "Con la broca de widia",
  "createdBy": "3d0a1e2c-...-000000000001",
  "updatedBy": null
}
```
> Cada extremo es **exactamente uno** de `userId` o `external`, nunca los dos ni ninguno. Y el externo necesita nombre y al menos un canal —correo o teléfono— porque es por donde se le manda el enlace con el token acotado (ver 5.4.1); un texto suelto no serviría para eso.
>
> `updatedBy` a nulo no es un hueco: significa que el último cambio lo hizo el sistema y no una persona, que es justo el caso del préstamo que el proceso diario pasa a `OVERDUE`.

**`GET /api/v1/assets?locationId=5b83c7d2-...&page=0&size=2`** — cualquier colección
```json
{
  "items": [
    { "id": "b0f5a217-...-00000000000a", "name": "Harina de trigo", "type": "CONSUMABLE", "quantity": 1300, "unit": "GRAM" },
    { "id": "7c44f8b1-...-000000000003", "name": "Estantería de trastero", "type": "DURABLE", "status": "AVAILABLE" }
  ],
  "page": 0,
  "size": 2,
  "total": 137
}
```
> **Las diez colecciones responden con esta misma envoltura**, sin excepción por tamaño esperado: una sola forma que aprender en el cliente, y ninguna migración el día que una lista que se creía acotada —categorías, invitaciones— deje de serlo. `page` empieza en 0 y `total` cuenta los elementos que cumplen el filtro, no los devueltos.

**Formato de error (todos los endpoints)**

Un error de **forma** —cuerpo mal construido, identificador que no es un UUID, `size` fuera de rango— responde `400` con el código `VALIDATION_ERROR`, y `details` lleva un campo por atributo rechazado:
```json
{
  "code": "VALIDATION_ERROR",
  "message": "La petición no cumple el contrato",
  "details": { "quantity": "debe ser mayor que 0" }
}
```

Un error de **regla de negocio** responde `409` con el código concreto:
```json
{
  "code": "ASSET_LOCATION_CONFLICT",
  "message": "Un asset no puede tener como ubicación un Asset y una Location a la vez",
  "details": {}
}
```
> Los códigos están **enumerados en `openapi.yaml`**, no solo descritos en prosa: el cliente decide qué hacer por el código, y uno que no esté en la lista es un fallo del contrato y no un caso que el frontend deba adivinar. `message` es texto de diagnóstico, no para mostrar tal cual.
