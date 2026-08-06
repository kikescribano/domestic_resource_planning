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
