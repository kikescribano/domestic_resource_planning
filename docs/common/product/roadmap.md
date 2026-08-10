# Roadmap de la Fase 1 (Core MVP)

| Campo | Valor |
|---|---|
| Estado | Vigente |
| Responsable | Equipo DRP |
| Ámbito | Ejecución de la Fase 1 |
| Última revisión | 2026-08-10 |

> El estado de **las fases** vive en la sección 8 del
> [`README principal`](../../../README.md), y solo allí. Este documento baja al
> detalle de la Fase 1: qué entra en cada hito, en qué estado va y cómo se
> trabaja.

## Alcance

El **core completo** definido en la Fase 0 —36 comandos, 16 queries y las 54
operaciones del contrato—, con cliente web para todos sus flujos. Se descartó
recortarlo: cada pieza que se deja fuera deja sin validar invariantes que ya
están definidos.

## Cómo se trabaja

- **Un hito por sesión.** Cada hito se ejecuta en una sesión propia, que arranca
  leyendo este documento. Son bloques grandes y mezclarlos hace que ninguno se
  cierre del todo.
- **Un pull request por hito**, y no se abre el siguiente hasta que el anterior
  está fusionado. Al fusionar, seguir el procedimiento de alineación local del
  `CLAUDE.md`: las ramas remotas se borran al fusionar y las referencias locales
  no se enteran solas.
- **Cada hito atraviesa las capas en vertical** —dominio, aplicación, adaptador,
  frontend y sus pruebas—, no una capa entera de cada vez. Construir por capas
  horizontales deja la validación para el final, que es justo lo que el criterio
  de aceptación quiere evitar.

## Criterio de aceptación

No hay que inventarlo: está repartido entre las ADR y basta con consolidarlo.

| Origen | Qué debe demostrarse |
|---|---|
| [ADR-001](../architecture/decisions/ADR-001-solution-architecture-baseline.md) | Un recorrido vertical que atraviese frontend, API autenticada, aplicación, dominio y PostgreSQL, con pruebas en los tres niveles |
| [ADR-002](../architecture/decisions/ADR-002-multi-tenancy-and-backend-framework.md) | Autenticado como hogar A, ninguna operación devuelve ni modifica datos del hogar B, ni siquiera por identificador directo |
| [ADR-003](../architecture/decisions/ADR-003-row-level-security.md) | Las pruebas corren contra PostgreSQL real con un usuario **sujeto a RLS**; los tres procesos diarios recorren los hogares sin `BYPASSRLS` |
| [ADR-004](../architecture/decisions/ADR-004-database-migrations.md) | Un arranque en limpio sobre una base vacía produce el esquema completo, políticas incluidas |
| [ADR-005](../architecture/decisions/ADR-005-local-file-storage.md) | Subir una foto, verla adjunta a un asset y descargarla; más las pruebas de tipo real, EXIF, cuota concurrente y URL firmada |
| [ADR-009](../architecture/decisions/ADR-009-outbound-email.md) | El alta de un hogar se verifica leyendo el correo real de Mailpit, sin pasos manuales |

## Hitos

### Hito 0 — Andamiaje, contrato y reparto documental · **Completado (2026-08-10)**

Sin funcionalidad de producto: es lo que permite que los hitos siguientes se
midan solos.

- [x] **Decisiones que faltaban**, en cuatro ADR nuevas: [frontend y sistema de diseño](../architecture/decisions/ADR-006-frontend-stack-and-design-system.md), [el contrato como fuente de verdad](../architecture/decisions/ADR-007-openapi-contract-as-source-of-truth.md), [monorepo y construcción](../architecture/decisions/ADR-008-repository-layout-and-build-chain.md) y [correo saliente](../architecture/decisions/ADR-009-outbound-email.md). Esta última cubría un hueco que no estaba ni marcado: todo el enrolamiento dependía del correo y nada decía cómo salía.
- [x] **Monorepo**: `backend/` con Gradle y Kotlin DSL, `frontend/` con Vite y React 19, `compose.yaml`, `scripts/` y `.github/workflows/`.
- [x] **Contrato saneado**: `operationId` en las 54 operaciones, paginación uniforme en las once colecciones, respuestas de error transversales y los 41 códigos enumerados en el esquema.
- [x] **Reparto del README a `docs/`**, la tarea de arranque que la Fase 0 había aplazado: 1192 líneas trasladadas.
- [x] **Entorno local verificado** contra PostgreSQL 16 real: el usuario de la aplicación nace sin `SUPERUSER` ni `BYPASSRLS`, y el aislamiento entre hogares funciona.

- [x] **CI verde**, verificada de verdad y no solo en local: contrato y documentación en 28 s, frontend en 22 s y backend en 2 min 13 s.

> **Un detalle de la CI que conviene conocer.** Al abrir el pull request no se
> ejecutó nada —ni un *run* encolado—, probablemente porque el workflow acababa
> de nacer en el repositorio. Arrancó sola con el primer push posterior a la
> rama. Si un PR aparece sin comprobaciones, basta con empujar un commit.
>
> Queda pendiente algo menor: `actions/checkout`, `setup-java`,
> `upload-artifact` y `setup-gradle` van en v4 y el runner ya avisa de que Node
> 20 está descatalogado. Hoy es solo un aviso; el día que deje de serlo, la CI
> se cae sin haber tocado nada.

### Hito 1 — Aislamiento y enrolamiento · Pendiente

El recorrido vertical de verdad, y el hito que valida ADR-002, ADR-003, ADR-004 y
ADR-009.

- Flyway: esquema completo de las 16 tablas con sus `CHECK`, las claves ajenas compuestas de autoría y **todos** los índices únicos parciales; políticas de RLS con `FORCE`; extensión `unaccent`.
- `TenantContext` que fija `SET LOCAL app.household_id` al abrir cada transacción. Los procesos diarios recorren los hogares uno a uno, **nunca con `BYPASSRLS`**.
- Argon2id (19 MiB, 2 iteraciones, paralelismo 1) en los cuatro puntos donde se fija contraseña. JWT con `sub`, `memberId`, `householdId` y `role`; refresh rotativo y hasheado.
- Casos de uso de alta, verificación, invitación, contraseñas, roles y baja, más `PurgeUnverifiedHouseholds` y `RevokeSession`.
- Frontend: shell responsive, primera versión del sistema de diseño —cerrando las ocho dimensiones de [`look-and-feel.md`](../../frontend/product-design/look-and-feel.md)— y los flujos de enrolamiento.

> **Al escribir la política de RLS**, usar la forma con `nullif` que documenta
> [`data-model.md`](../architecture/data-model.md): la versión directa lanza un
> error de conversión cuando el ajuste está sin fijar o en cadena vacía, en vez
> de denegar limpiamente.
>
> **Este es el hito en el que aparece Testcontainers**, así que Docker tiene que
> estar arrancado para poder construir. Ojo con la distinción: Testcontainers
> levanta y destruye lo suyo, y `compose.yaml` es para ejecutar la aplicación —
> no hace falta para las pruebas. Al terminar, dejar la máquina como estaba (ver
> la sección de verificación del `CLAUDE.md`).

### Hito 2 — Catálogo, ubicaciones y assets · Pendiente

- Categorías, artículos y ubicaciones con su CRUD completo; assets con sus dos naturalezas; entrada de consumible, movimiento, fusión, ajuste y baja.
- Las validaciones que la base de datos no puede garantizar van en el caso de uso: anti-ciclo de las dos jerarquías, que la ubicación destino sea `DURABLE`, y que el aviso de capacidad **advierta sin bloquear**.
- Event bus: puerto propio sobre `ApplicationEventPublisher`, con handlers idempotentes desde el principio.
- Frontend: árbol de ubicaciones, ficha de asset y las operaciones de existencias.

### Hito 3 — Ficheros y documentos · Pendiente

La [ADR-005](../architecture/decisions/ADR-005-local-file-storage.md) entera. Es
el hito con más infraestructura propia, y por eso va después de que el resto
funcione.

- Puerto `FileStorage` con adaptador de sistema de ficheros; controles OWASP; cuota de 1 GB por hogar validada **con la fila del hogar bloqueada**; miniaturas y purga.
- **nginx entra en el `compose.yaml` en este hito**, con `X-Accel-Redirect`, dominio distinto al de la aplicación y URL firmada de vida corta. Hasta aquí no estaba, porque su configuración no era todavía nada real.
- Frontend: subida con progreso, galería, adjuntar documento, avatar y consumo de cuota.

### Hito 4 — Préstamos y cierre de fase · Pendiente

- Préstamos con token acotado para externos, y el vencimiento por proceso programado.
- Frontend: préstamos del hogar y la vista externa de quien llega con el token acotado.
- Cierre: batería E2E completa, medición del consumo real de disco y CPU, y **elección del VPS** —VPS-2 frente a VPS-3— con datos medidos en lugar de estimados.

## Lo que queda abierto a propósito

No son olvidos: están anotados con motivo en
[`decisions.md`](decisions.md) y no se tocan en la Fase 1.

- **Peso y volumen de un asset**, del que depende que el aviso de capacidad sirva de algo. Destinatario asignado: el módulo Warehouse.
- **Baja de un hogar** y la supresión de sus ficheros. No es una pregunta de ficheros sino de un caso de uso que el core no contempla.
- **Análisis antivirus** de lo subido: es la defensa que toca añadir el día que un fichero pueda salir del hogar que lo subió.
- **Cuatro atributos propuestos** —estado de conservación, condición en préstamo, etiquetas libres, e icono y color de categoría—, que no entran hasta que haya un caso de uso que los pida.

## Historial

| Fecha | Cambio |
|---|---|
| 2026-08-10 | Se crea al arrancar la Fase 1, con el detalle de los hitos trasladado desde la sección 8.2 del README. Hito 0 completado. |
