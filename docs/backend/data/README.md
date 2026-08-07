# Datos y persistencia

| Campo | Valor |
|---|---|
| Estado | Borrador |
| Tecnología confirmada | PostgreSQL 16 o superior; migraciones con Flyway ([ADR-004](../../common/architecture/decisions/ADR-004-database-migrations.md)) |
| Responsable | Por decidir |
| Última revisión | 2026-08-07 |

## Restricciones aceptadas

- El aislamiento entre hogares se defiende en dos capas: filtrado por
  `household_id` en la aplicación y Row-Level Security en PostgreSQL
  ([ADR-003](../../common/architecture/decisions/ADR-003-row-level-security.md)).
  El usuario de base de datos de la aplicación no puede ser superusuario ni
  tener `BYPASSRLS`.
- Toda configuración de esquema —tablas, `CHECK`, índices y políticas de RLS— se
  versiona como migración Flyway en SQL plano. Nada se aplica a mano sobre un
  entorno.
- Las pruebas que tocan la base de datos se ejecutan con un usuario sujeto a RLS;
  usar un superusuario daría una falsa sensación de cobertura.

## Contenido previsto

- Estrategia de esquemas y propiedad de datos por módulo.
- Modelo lógico y decisiones de mapeo a persistencia.
- Convenciones, versionado y ejecución de migraciones.
- Límites transaccionales, bloqueo y control de concurrencia.
- Integridad, retención y clasificación de datos.
- Fixtures, datos de prueba y anonimización.
- Copias de seguridad, restauración y verificación de recuperación.

Los detalles de una tabla no deben convertirse en un contrato entre módulos; la
colaboración se realiza mediante puertos, APIs o eventos explícitos.
