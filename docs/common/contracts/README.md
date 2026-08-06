# Contratos compartidos

| Campo | Valor |
|---|---|
| Estado | Borrador |
| Responsable | Por decidir |
| Ámbito | Integración frontend–backend |
| Última revisión | 2026-08-06 |

Este directorio define el comportamiento observable que ambos componentes deben
respetar. No describe controladores, clientes HTTP ni detalles internos.

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
