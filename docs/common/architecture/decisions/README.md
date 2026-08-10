# Decisiones arquitectónicas

Este directorio conserva las Architecture Decision Records (ADR) que afectan a
toda la solución. Las decisiones exclusivas de un componente pueden almacenarse
en su propia arquitectura, pero deben enlazarse desde aquí si condicionan un
contrato compartido.

## Convención

- Nombre: `ADR-NNN-titulo-breve.md`.
- Estados: `proposed`, `accepted`, `superseded` o `rejected`.
- Una decisión aceptada no se reescribe para ocultar su historia.
- Un cambio material genera otra ADR y enlaza la decisión sustituida.
- Toda ADR debe indicar cómo validar o revertir la decisión.

Usa [`ADR-template.md`](ADR-template.md) para crear una entrada.

## Índice

| ID | Título | Estado | Sustituye |
|---|---|---|---|
| [ADR-001](ADR-001-solution-architecture-baseline.md) | Base arquitectónica de DRP | accepted | — |
| [ADR-002](ADR-002-multi-tenancy-and-backend-framework.md) | Multi-tenancy por hogar y framework de backend | accepted | — |
| [ADR-003](ADR-003-row-level-security.md) | Row-Level Security como segunda capa de aislamiento entre hogares | accepted | — |
| [ADR-004](ADR-004-database-migrations.md) | Flyway como herramienta de migraciones de base de datos | accepted | — |
| [ADR-005](ADR-005-local-file-storage.md) | Almacenamiento local de ficheros en el servidor | accepted | — |
| [ADR-006](ADR-006-frontend-stack-and-design-system.md) | Stack de frontend y sistema de diseño | accepted | — |
| [ADR-007](ADR-007-openapi-contract-as-source-of-truth.md) | El contrato OpenAPI como fuente de verdad de la API | accepted | — |
| [ADR-008](ADR-008-repository-layout-and-build-chain.md) | Estructura del repositorio y cadena de construcción | accepted | — |
| [ADR-009](ADR-009-outbound-email.md) | Envío de correo saliente | accepted | — |
