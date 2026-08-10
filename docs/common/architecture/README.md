# Arquitectura de la solución

| Campo | Valor |
|---|---|
| Estado | Borrador |
| Responsable | Por decidir |
| Ámbito | Frontend, backend y sus límites |
| Última revisión | 2026-08-06 |

Aquí se documentan las vistas que requieren conocer la solución completa. La
arquitectura interna de cada componente permanece en su área correspondiente.

## Documentos vigentes

- [`data-model.md`](data-model.md): el modelo de datos del core en PostgreSQL —
  las 16 tablas, los índices únicos parciales, las políticas de Row-Level
  Security y lo que la base de datos *no* puede garantizar. Trasladado desde la
  sección 5.6 del [`README principal`](../../../README.md) al arrancar la Fase 1.
- [`decisions/`](decisions/README.md): registro de decisiones arquitectónicas.

## Documentos previstos

- `system-context.md`: actores, sistemas externos y límites de confianza.
- `containers.md`: frontend, backend, PostgreSQL y relaciones principales.
- `cross-component-flows.md`: autenticación y recorridos funcionales relevantes.
- `quality-attributes.md`: seguridad, mantenibilidad, rendimiento y disponibilidad.

Cada vista debe explicar su propósito, alcance, supuestos y fecha de revisión. Un
diagrama nunca sustituye la explicación de sus límites y consecuencias.
