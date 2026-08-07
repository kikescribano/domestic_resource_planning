# Contratos compartidos

| Campo | Valor |
|---|---|
| Estado | Borrador |
| Responsable | Por decidir |
| Ámbito | Integración frontend–backend |
| Última revisión | 2026-08-06 |

Este directorio define el comportamiento observable que ambos componentes deben
respetar. No describe controladores, clientes HTTP ni detalles internos.

## Contrato vigente

La especificación OpenAPI 3.0 del core se mantiene en
[`openapi.yaml`](../../../openapi.yaml) (versión `0.1.0-fase0`): 12 operaciones y
15 esquemas que cubren autenticación, assets, locations, users y loans. Los
ejemplos comentados de request/response viven en la sección 5.4.3 del
[`README principal`](../../../README.md).

Mientras la especificación permanezca en la raíz del repositorio, este directorio
documenta las convenciones que la gobiernan, no una copia de los esquemas.

## Contenido previsto

- Convenciones de la API REST y estrategia de versionado.
- Esquemas de petición y respuesta, errores y códigos HTTP.
- Autenticación y autorización observables desde el contrato.
- Paginación, filtrado, ordenación, idempotencia y concurrencia.
- Compatibilidad y política de retirada de contratos.
- Ubicación y proceso de validación de la especificación OpenAPI.

La documentación de cómo el backend sirve estos contratos vive en
[`../../backend/api/`](../../backend/api/README.md); la forma de consumirlos vive
en [`../../frontend/api-integration/`](../../frontend/api-integration/README.md).
