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
| [ADR-010](ADR-010-module-boundaries-and-activation.md) | Fronteras de módulo y activación por hogar | accepted | — |
| [ADR-011](ADR-011-scheduled-checks-and-notice-delivery.md) | Programación de comprobaciones y entrega de avisos | accepted | — |
| [ADR-012](ADR-012-data-erasure-household-closure-and-account-closure.md) | Supresión de datos: baja de hogar y cierre de cuenta | accepted | — |
| [ADR-013](ADR-013-transactional-outbox.md) | Transactional Outbox | accepted | — |
| [ADR-014](ADR-014-heic-conversion.md) | Conversión de HEIC | accepted | — |
| [ADR-015](ADR-015-user-chosen-category-identity.md) | Color e icono elegidos por el usuario, dentro de un juego certificado | accepted | — |
| [ADR-016](ADR-016-production-deployment.md) | Despliegue de producción en un VPS | accepted | — |
