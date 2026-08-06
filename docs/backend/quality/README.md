# Calidad del backend

Este espacio aterriza la estrategia de calidad para el componente Kotlin.

## Niveles de prueba

- **Dominio:** reglas e invariantes rápidas, deterministas y sin infraestructura.
- **Aplicación:** casos de uso integrados con puertos o adaptadores controlados.
- **Adaptadores y end-to-end:** contratos REST, PostgreSQL, event bus y recorridos críticos.

La distribución objetivo 60/25/15 se define en la
[`ADR-001`](../../common/architecture/decisions/ADR-001-solution-architecture-baseline.md).

## Contenido previsto

- Herramientas y convenciones de pruebas.
- Pirámide por módulo y criterios de selección de escenarios.
- Formato, análisis estático, compilación y cobertura acordada.
- Tests de arquitectura y límites modulares.
- Pruebas con PostgreSQL y control de datos de prueba.
- Puertas de CI y evidencias necesarias para entregar un cambio.
