# ADR-002: Multi-tenancy por hogar y framework de backend

- Estado: accepted
- Fecha: 2026-08-07
- Responsables: Equipo DRP
- Ámbito: common
- Sustituye: Ninguna (complementa [ADR-001](ADR-001-solution-architecture-baseline.md))

## Contexto

La [ADR-001](ADR-001-solution-architecture-baseline.md) fijó la base de la
solución dejando explícitamente abiertos los frameworks y el esquema de
autenticación. Al detallar el modelo de datos y los casos de uso del core (Fase
0) fue necesario resolver dos cuestiones que condicionan el esquema de
persistencia, la autorización y la forma de todos los casos de uso:

- Cómo conviven varios hogares en la instalación: cada hogar es una unidad
  natural de aislamiento, y de esa decisión depende si `household_id` atraviesa
  el modelo de datos completo o no existe.
- Qué framework de backend en Kotlin se usa, porque determina cómo se implementa
  la autenticación y qué mecanismos de autorización están disponibles.

## Decisión

- **Multi-tenancy con base de datos compartida.** Varios hogares comparten la
  misma instancia y el mismo esquema de PostgreSQL. El aislamiento se implementa
  a nivel de aplicación: toda tabla del core lleva `household_id`, y todo caso de
  uso y repositorio filtra por el `householdId` presente en el token de quien
  realiza la petición. Un `householdId` recibido como parámetro del cliente nunca
  se considera fuente de verdad.
- **Spring Boot como framework de backend**, con Spring Security para la
  autenticación: JWT firmado (HS256) con claims `sub`, `householdId` y `role`;
  contraseñas con BCrypt; access token corto acompañado de refresh token rotativo
  persistido hasheado; y tokens acotados por préstamo para participantes externos
  sin cuenta.

El detalle operativo de ambas decisiones vive en las secciones 5.4.1 y 5.6 del
[`README principal`](../../../../README.md).

## Alternativas consideradas

- **Una base de datos por hogar:** aísla por construcción y elimina la clase de
  fallo "fuga entre hogares", pero multiplica el coste de migraciones, backups y
  provisión por cada alta. Se descarta para un producto doméstico donde el número
  de hogares crece de uno en uno y la operación debe permanecer simple.
- **Esquema de PostgreSQL por hogar:** término medio que conserva una sola
  instancia, pero traslada el problema a la gestión dinámica de esquemas y sigue
  penalizando las migraciones. Se descarta por el mismo motivo.
- **Ktor en lugar de Spring Boot:** más ligero y idiomático en Kotlin, pero
  obliga a construir a mano lo que Spring Security ya ofrece resuelto
  (autorización por rol, filtros, gestión de contraseñas). Se descarta al no
  existir una restricción de tamaño o arranque que lo justifique.
- **Sesiones con estado en servidor en lugar de JWT:** simplifica la revocación,
  pero introduce estado compartido en un backend que se quiere desplegable como
  unidad simple. Se descarta; la revocación se cubre con refresh tokens
  persistidos y revocables.

## Consecuencias

### Positivas

- El modelo de datos y los casos de uso tienen una regla de aislamiento única y
  verificable, expresable como test de arquitectura.
- La autenticación se apoya en mecanismos estándar y auditados en lugar de
  código propio.
- La operación (migraciones, backup, despliegue) permanece en una sola unidad,
  coherente con el monolito modular de la ADR-001.

### Costes y riesgos

- El aislamiento depende de que **ningún** repositorio olvide el filtro por
  `household_id`: es un riesgo de fuga entre hogares que exige verificación
  automatizada, no revisión manual.
- Spring Boot introduce peso de framework en el arranque y una tentación de
  acoplamiento; la regla de dependencia de Clean Architecture debe mantener el
  dominio libre de anotaciones de Spring.
- Quedan pendientes de decidir (ver 4.1.7 del README): activación de Row-Level
  Security de PostgreSQL como defensa en profundidad, flujo de invitación de
  usuarios a un hogar existente, y librería de migraciones (Flyway o Liquibase).

## Validación o reversión

El aislamiento se considerará validado cuando exista una prueba de integración
que, autenticada como usuario del hogar A, verifique que ninguna operación del
catálogo de casos de uso (5.7) devuelve ni modifica datos del hogar B —
incluyendo el acceso por identificador directo. La elección de framework se
validará con el primer recorrido vertical descrito en la ADR-001.

Sustituir el modelo de base de datos compartida por aislamiento físico, o
cambiar de framework, requerirá una nueva ADR con evidencia de la necesidad
(incidente de fuga, requisito regulatorio o límite de rendimiento medido).

## Posterior a esta decisión

El cuerpo de esta ADR se conserva tal y como se aceptó. Las cuestiones que
quedaban abiertas en «Costes y riesgos» se han resuelto después:

- El riesgo de que un repositorio olvide el filtro por `household_id` se mitiga
  con Row-Level Security, aceptado en [ADR-003](ADR-003-row-level-security.md).
  La decisión de esta ADR no cambia: el filtrado en aplicación se mantiene como
  primera capa.
- La librería de migraciones se resuelve en
  [ADR-004](ADR-004-database-migrations.md): Flyway.
- El flujo de alta de usuarios en un hogar existente se decide en la sección
  4.1.7 del [`README principal`](../../../../README.md). Se resolvió primero como
  alta directa por un administrador y se revisó después, al hacerse obligatoria
  la verificación de correo: hoy es **invitación por email**, y el alta directa
  queda descartada.
- El algoritmo de hash de contraseñas pasa de **BCrypt a Argon2id**, decidido en
  la sección 4.1.7 del [`README principal`](../../../../README.md). El motivo es
  concreto: BCrypt ignora en silencio todo lo que exceda de 72 bytes, y la
  política de contraseñas adoptada favorece frases largas, así que dos
  contraseñas distintas que compartieran ese prefijo serían la misma para el
  sistema. Su configuración mínima es la recomendada por OWASP: 19 MiB de
  memoria, 2 iteraciones y paralelismo 1. El resto de lo que fija esta ADR sobre autenticación —JWT firmado,
  access token corto, refresh rotativo hasheado y tokens acotados por préstamo—
  no cambia.
