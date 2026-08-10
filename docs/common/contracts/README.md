# Contratos compartidos

| Campo | Valor |
|---|---|
| Estado | Borrador |
| Responsable | Por decidir |
| Ámbito | Integración frontend–backend |
| Última revisión | 2026-08-10 |

Este directorio define el comportamiento observable que ambos componentes deben
respetar. No describe controladores, clientes HTTP ni detalles internos.

## Contrato vigente

La especificación OpenAPI 3.0 del core se mantiene en
[`openapi.yaml`](../../../openapi.yaml) (versión `0.2.0-fase1`): **37 rutas, 54
operaciones y 46 esquemas**, que cubren hogares, autenticación, categorías,
artículos, assets, ubicaciones, documentos, ficheros, usuarios, invitaciones y
préstamos. Los ejemplos comentados de request/response viven en la sección 5.4.3
de [`json-examples.md`](json-examples.md), trasladados desde la sección 5.4.3 del
[`README principal`](../../../README.md) al arrancar la Fase 1.

La [`ADR-007`](../architecture/decisions/ADR-007-openapi-contract-as-source-of-truth.md)
lo declara **fuente de verdad**: se edita antes que el código que lo implementa,
el frontend genera de él sus tipos y su cliente, y el backend escribe los
controladores a mano y se verifica contra él con las pruebas de adaptador del
15 %. Mientras la especificación permanezca en la raíz del repositorio, este
directorio documenta las convenciones que la gobiernan, no una copia de los
esquemas.

## Convenciones ya fijadas

- **Versionado** en la ruta del servidor (`/api/v1`).
- **Paginación uniforme** en las diez colecciones: envoltura
  `{ items, page, size, total }` con parámetros `page` (desde 0) y `size`. Sin
  excepción por tamaño esperado.
- **Errores en dos familias.** Los de forma responden `400` con el código
  `VALIDATION_ERROR`; los de regla de negocio, `409` con su código concreto. Los
  41 códigos están **enumerados en el esquema**, no descritos en prosa.
- **Validación en cada integración**: `scripts/validate-openapi.py` —que además
  exige `operationId` en todas las operaciones— y Spectral con
  [`.spectral.yaml`](../../../.spectral.yaml).

## Contenido previsto

- Autenticación y autorización observables desde el contrato.
- Idempotencia y concurrencia, que el contrato todavía no expresa.
- Compatibilidad y política de retirada de contratos.
- Convenciones de filtrado y ordenación, hoy resueltas caso a caso.

La documentación de cómo el backend sirve estos contratos vive en
[`../../backend/api/`](../../backend/api/README.md); la forma de consumirlos vive
en [`../../frontend/api-integration/`](../../frontend/api-integration/README.md).
