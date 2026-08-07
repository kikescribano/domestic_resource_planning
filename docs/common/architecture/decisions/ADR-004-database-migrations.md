# ADR-004: Flyway como herramienta de migraciones de base de datos

- Estado: accepted
- Fecha: 2026-08-07
- Responsables: Equipo DRP
- Ámbito: common
- Sustituye: Ninguna

## Contexto

El esquema del core está definido (sección 5.6 del
[`README principal`](../../../README.md)) y la [ADR-003](ADR-003-row-level-security.md)
añade políticas de Row-Level Security que forman parte del esquema tanto como
las tablas. Ambas cosas necesitan versionarse, aplicarse de forma reproducible en
cada entorno y ejecutarse durante el despliegue.

La elección es difícil de revertir en la práctica: el historial de migraciones
queda registrado en la propia base de datos, y cambiar de herramienta más
adelante obliga a migrar ese historial o a partir de una línea base artificial.

## Decisión

Se adopta **Flyway**, con migraciones en **SQL plano versionado**
(`V<n>__<descripcion>.sql`), integrado en el arranque de Spring Boot.

Las políticas de RLS, los `CHECK` de coherencia y los índices se versionan como
migraciones igual que las tablas: no hay configuración de base de datos aplicada
fuera de este mecanismo.

## Alternativas consideradas

- **Liquibase:** más potente, con rollback declarativo y changelogs en
  XML/YAML/JSON. Su ventaja principal es abstraer el motor de base de datos, algo
  que no aporta nada aquí porque PostgreSQL ya está fijado por la ADR-001. A
  cambio, esa abstracción hace más difícil leer y revisar una política de RLS,
  que en SQL plano se entiende de un vistazo.
- **Generación automática de esquema por el ORM (`hibernate.hbm2ddl`):** cómodo
  en desarrollo, inaceptable en cualquier entorno con datos: no versiona nada,
  no es revisable y no puede expresar políticas de RLS.
- **Scripts SQL aplicados a mano:** sin registro de qué se ha aplicado ni
  garantía de reproducibilidad entre entornos.

## Consecuencias

### Positivas

- El esquema y sus políticas de seguridad se revisan como código, en SQL legible.
- Cada entorno converge al mismo estado de forma reproducible y verificable.
- La integración con Spring Boot no requiere infraestructura adicional.

### Costes y riesgos

- Flyway no ofrece rollback declarativo en su versión libre: revertir exige una
  migración compensatoria escrita a mano, que hay que asumir como práctica.
- Una migración ya aplicada no debe editarse; corregir implica añadir otra.
- El SQL queda acoplado a PostgreSQL, consecuencia aceptada de la ADR-001.
- Las migraciones que se ejecutan durante un despliegue deben ser compatibles
  con la versión anterior de la aplicación mientras dure el solape.

## Validación o reversión

Se considera validada cuando el arranque en limpio de un entorno vacío produzca
el esquema completo, incluidas las políticas de RLS, y la prueba de aislamiento
exigida por la ADR-003 pase contra esa base recién migrada.

Cambiar de herramienta requerirá una nueva ADR que explique cómo se traslada el
historial de migraciones ya aplicado.
