# Datos y persistencia

| Campo | Valor |
|---|---|
| Estado | Borrador |
| Tecnología confirmada | PostgreSQL 16 o superior |
| Responsable | Por decidir |
| Última revisión | 2026-08-06 |

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
