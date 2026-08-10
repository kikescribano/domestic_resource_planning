# ADR-003: Row-Level Security como segunda capa de aislamiento entre hogares

- Estado: accepted
- Fecha: 2026-08-07
- Responsables: Equipo DRP
- Ámbito: common
- Sustituye: Ninguna (refuerza [ADR-002](ADR-002-multi-tenancy-and-backend-framework.md))

## Contexto

La [ADR-002](ADR-002-multi-tenancy-and-backend-framework.md) aceptó que varios
hogares compartan la misma base de datos, con aislamiento por `household_id`
aplicado en la capa de aplicación, y señaló como principal riesgo que ese
aislamiento depende de que **ningún** repositorio olvide el filtro. Ese riesgo no
disminuye con el tiempo: crece con cada consulta nueva, cada join y cada
optimización, y su modo de fallo es una fuga de datos entre hogares —
silenciosa, difícil de detectar en revisión y grave cuando ocurre.

PostgreSQL ofrece Row-Level Security, un mecanismo que aplica la restricción en
el motor, con independencia de lo que la aplicación recuerde hacer.

## Decisión

Se activa **Row-Level Security en todas las tablas con `household_id`**, además
del filtrado en la aplicación. Las dos capas se mantienen: no sustituye una a la
otra.

```sql
ALTER TABLE assets ENABLE ROW LEVEL SECURITY;
ALTER TABLE assets FORCE ROW LEVEL SECURITY;

CREATE POLICY assets_household_isolation ON assets
    USING (household_id = current_setting('app.household_id')::uuid);
```

La aplicación fija `SET LOCAL app.household_id` al abrir cada transacción, a
partir del claim `householdId` del token verificado. Las políticas se versionan
como migraciones (ver [ADR-004](ADR-004-database-migrations.md)), no se aplican a
mano sobre un entorno.

Dos condiciones de las que depende la protección entera:

- El usuario de base de datos de la aplicación **no** puede ser superusuario ni
  tener el atributo `BYPASSRLS`, porque ambos ignoran las políticas.
- Hace falta `FORCE ROW LEVEL SECURITY` para que la política se aplique también
  al propietario de la tabla.

## Alternativas consideradas

- **Solo filtrado en la aplicación (statu quo de la ADR-002):** más simple de
  arrancar, pero convierte cada consulta nueva en una posible fuga y deja la
  garantía en manos de la disciplina del equipo. Se descarta por la gravedad del
  modo de fallo, no por su probabilidad.
- **Diferir RLS a antes del primer despliegue con más de un hogar:** aplazar
  obliga a retrofitar las políticas sobre un conjunto de consultas ya escrito y
  a revisarlas todas, justo cuando hay presión por desplegar. El coste de
  adoptarlo ahora, sin código escrito, es el mínimo posible.
- **Un esquema o una base de datos por hogar:** aislamiento por construcción, ya
  descartado en la ADR-002 por el coste operativo de migraciones, backups y
  provisión.

## Consecuencias

### Positivas

- Un repositorio que olvide el filtro por `household_id` deja de ser un
  incidente de seguridad: devuelve cero filas en lugar de filas ajenas.
- La regla de aislamiento queda expresada en un único sitio verificable por
  entidad, en lugar de repetida en cada consulta.
- Adoptarlo antes de escribir código evita el retrofit descrito arriba.

### Costes y riesgos

- Cada transacción debe fijar `app.household_id`; olvidarlo hace que las
  consultas no devuelvan nada. Es un fallo ruidoso y detectable, preferible a
  una fuga silenciosa, pero exige un punto único de configuración de la sesión.
- Las pruebas de integración deben ejecutarse con un usuario sujeto a RLS; usar
  un superusuario en tests daría una falsa sensación de cobertura.
- Operaciones administrativas legítimas que crucen hogares (backup lógico,
  soporte, tareas de mantenimiento) necesitan un rol específico y una política
  explícita, no saltarse RLS de forma genérica.
- Hay un coste de planificación por consulta, irrelevante a escala doméstica.

## Validación o reversión

La decisión se valida con una prueba de integración que, con el usuario de
aplicación y `app.household_id` fijado al hogar A, compruebe que las tablas con
`household_id` no devuelven ni permiten modificar filas del hogar B — incluyendo
el acceso por identificador directo. Esta prueba refuerza la exigida por la
ADR-002 y debe ejecutarse contra PostgreSQL real, no contra una base en memoria.

Desactivar RLS requerirá una nueva ADR que explique qué mecanismo asume el
aislamiento en su lugar.

## Posterior a esta decisión

El cuerpo de esta ADR se conserva tal y como se aceptó. La decisión no cambia,
pero la **expresión concreta de la política** sí se ha afinado al probarla contra
PostgreSQL 16 en el entorno local (2026-08-10):

- Tal y como aparece más arriba, `current_setting('app.household_id')::uuid`
  **lanza un error** cuando el ajuste no está fijado en la sesión y, menos
  evidente, después de un `RESET`, que lo deja en **cadena vacía** en lugar de
  sin valor. La forma vigente añade el segundo argumento de `current_setting` y
  un `nullif`, de modo que en ambos casos la comparación dé `NULL` y no se
  devuelva ninguna fila: falla cerrada, en vez de reventar con un error de
  conversión. El razonamiento completo está en
  [`data-model.md`](../data-model.md), que es la forma que hay que migrar.
- Queda confirmado en el entorno local que el usuario de la aplicación se crea
  sin `SUPERUSER` ni `BYPASSRLS`, y que sin `app.household_id` fijado no se
  devuelve fila alguna — tampoco pidiendo el identificador de otro hogar.
