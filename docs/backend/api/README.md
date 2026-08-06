# API del backend

Este espacio explica cómo el backend implementa la entrada REST. Los esquemas y
el comportamiento observable se definen en
[`common/contracts`](../../common/contracts/README.md).

## Contenido previsto

- Mapeo entre operaciones REST y casos de uso.
- Controladores, validación de entrada y serialización.
- Integración con autenticación y autorización.
- Traducción de errores internos al contrato HTTP.
- Generación o validación de OpenAPI.
- Pruebas de contrato, compatibilidad e idempotencia.
- Límites de tamaño, rate limiting y medidas defensivas.

No deben documentarse aquí reglas de negocio que pertenecen al dominio.
