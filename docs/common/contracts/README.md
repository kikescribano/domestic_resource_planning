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
[`openapi.yaml`](../../../openapi.yaml) (versión `0.3.0-fase2`): **39 rutas, 57
operaciones y 51 esquemas**, que cubren hogares, autenticación, categorías,
artículos, assets, ubicaciones, documentos, ficheros, usuarios, invitaciones y
préstamos. Las **tres últimas no son del core**: son la activación de módulos por
hogar que introduce el Hito 0 de la Fase 2, y las sirve la plataforma
(ver [`ADR-010`](../architecture/decisions/ADR-010-module-boundaries-and-activation.md)). Los ejemplos comentados de request/response viven en la sección 5.4.3
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
- **Paginación uniforme** en las doce colecciones: envoltura
  `{ items, page, size, total }` con parámetros `page` (desde 0) y `size`. Sin
  excepción por tamaño esperado. Son doce endpoints y diez esquemas `*Page`:
  los dos listados de hijos —de un asset y de una ubicación— reutilizan el de su
  tipo. El catálogo de módulos la respeta aunque quepa entero en una página: una
  colección con envoltura propia por ser pequeña es una forma más que aprender.
- **Errores en dos familias.** Los de forma responden `400` con el código
  `VALIDATION_ERROR`; los de regla de negocio, `409` con su código concreto. Los
  42 códigos están **enumerados en el esquema**, no descritos en prosa. El
  último, `MODULE_INACTIVE`, es el único que responde `403`: lo escribe el gate
  de módulos, que corre en la cadena de filtros y no en un controlador.
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
