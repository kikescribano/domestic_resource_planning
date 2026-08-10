# Arquitectura del backend

| Campo | Valor |
|---|---|
| Estado | Borrador |
| Responsable | Por decidir |
| Ámbito | Backend Kotlin |
| Última revisión | 2026-08-06 |

Este directorio documentará la estructura interna del monolito modular y la
aplicación de Clean Architecture en el backend.

## Documentos vigentes

- [`file-storage.md`](file-storage.md): dónde viven los bytes y cómo se recorren
  los caminos de subida y descarga, incluida la entrega por URL firmada.
  Trasladado desde la sección 5.8 del
  [`README principal`](../../../README.md) al arrancar la Fase 1. Los controles
  de la OWASP File Upload Cheat Sheet están en
  [`../security/`](../security/file-upload-controls.md) y el dimensionado del
  volumen en [`../operations/`](../operations/storage-sizing-and-backups.md).

## Contenido previsto

- Mapa de capas: dominio, aplicación, adaptadores e infraestructura.
- Reglas de dependencias y mecanismo para verificarlas.
- Composición de la aplicación y límites de módulos.
- Uso del event bus, entrega, orden, reintentos y gestión de fallos.
- Gestión de transacciones y consistencia entre módulos.
- Decisiones específicas de frameworks y librerías.
- Diagramas de secuencia para flujos internos relevantes.

Las decisiones que cambien un contrato compartido o la topología de la solución
deben registrarse en las
[`ADRs comunes`](../../common/architecture/decisions/README.md).
