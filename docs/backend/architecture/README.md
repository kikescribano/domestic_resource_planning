# Arquitectura del backend

| Campo | Valor |
|---|---|
| Estado | Borrador |
| Responsable | Por decidir |
| Ámbito | Backend Kotlin |
| Última revisión | 2026-08-06 |

Este directorio documentará la estructura interna del monolito modular y la
aplicación de Clean Architecture en el backend.

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
