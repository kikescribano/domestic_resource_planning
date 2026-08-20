# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Lo primero: el core, los cuatro primeros módulos y el cierre de huecos están implementados

**La Fase 1 (Core MVP) se cerró el 2026-08-17**, **la Fase 2 (Módulos
activables) el 2026-08-19** y **el cierre de huecos —un bloque entre fases que no
es una fase— el 2026-08-20**, en cinco, siete y siete hitos, un pull request cada
uno. Lo que hay es un monorepo con `backend/` (Kotlin, Gradle con Kotlin DSL) y
`frontend/` (TypeScript, React sobre Vite), más `compose.yaml`, `scripts/` y
`.github/workflows/`, y dentro:

- **El core entero**: los tres procesos diarios, el almacenamiento de ficheros con
  nginx delante y un cliente web para todos los flujos.
- **El mecanismo de activación por hogar** y **la plataforma** que programa las
  comprobaciones periódicas y entrega los avisos.
- **Los cuatro módulos de prioridad alta** de la sección 4.2: Proveedores,
  Warehouse, Compras y Mantenimiento (CMMS).
- **Lo que las dos fases dejaron a deber, ya saldado**: la baja de un hogar con
  treinta días de gracia y el cierre de cuenta (ADR-012), el Transactional Outbox
  con su relay sin `BYPASSRLS` (ADR-013), la conversión de HEIC en el cliente
  (ADR-014), los cuatro atributos propuestos —estado de conservación, condición
  en préstamo, etiquetas, e icono y color de categoría dentro de un juego
  certificado (ADR-015)— y el «hoy» de las reglas de calendario resuelto con la
  zona del hogar vía el puerto `HouseholdCalendar` (ADR-011 ampliada).

En cifras: **106 operaciones** en el contrato, **31 tablas** con RLS y `FORCE`, y
**diez recorridos verticales** en un navegador de verdad.

Así que la advertencia es la contraria a la que había aquí: **no supongas que algo
no existe**. Antes de escribir un caso de uso, una tabla o una pantalla, busca si
ya está —y si lo que quieres es cambiarlo, hay pruebas que lo fijan y documentos
que lo explican.

**Lo siguiente es la Fase 3 —los nueve módulos restantes de 4.2— y todavía no está
planificada.** Convertir una fase en un plan es su propia sesión de trabajo y su
propio pull request, como lo fue para la 2: no se improvisa al final de otra cosa.
Entre la Fase 2 y la 3 hubo un bloque más, **ya cerrado**: el
[cierre de huecos](docs/common/product/open-gaps-roadmap.md), que saldó lo que
las dos fases dejaron abierto a propósito y cuyo documento se conserva como
historia, igual que [`roadmap.md`](docs/common/product/roadmap.md) y
[`phase-2-roadmap.md`](docs/common/product/phase-2-roadmap.md). **El estado de
las fases vive en la sección 8 del README y solo allí**; el de cada módulo, en
la 4.2.

Lo que sí conviene tener presente antes de tocar nada es lo que las dos fases
dejaron montado, porque condiciona todo lo que venga. El backend está empaquetado
por módulos:

```text
com.drp.platform.*      Bus, TenantContext, paginación y la activación de módulos
com.drp.core.*          El core, con su reparto en capas intacto
com.drp.module.<key>.*  Un árbol por módulo, con su propio domain/application/adapter
com.drp                 DrpApplication y config: quien cablea todo
```

Y **cuatro reglas de ArchUnit fallan la construcción** si alguien cruza una
frontera: un módulo no referencia a otro, el core no referencia a ninguno, y
plataforma no se apoya en el core —con **una** excepción nombrada dentro de la
propia regla, `SessionClaims`, que tiene además una prueba que afirma que sigue
siendo una sola—. La dirección `módulo → core` sí está permitida: un módulo lee
el estado del core. La [ADR-010](docs/common/architecture/decisions/ADR-010-module-boundaries-and-activation.md)
lo recoge entero, incluido el gate en tres capas y el `403 MODULE_INACTIVE`.

El tercer hueco también está cerrado: **los tres procesos diarios se ejecutan de
verdad**. Ya no recorren los hogares por su cuenta —eso era el camino a tener dos
recorridos, y solo uno puede saltarse los hogares con el módulo apagado— sino que
son `ScheduledCheck` que miran **el hogar actual**, y quien itera es `DailySweep`
en `com.drp.platform.schedule`, hogar a hogar y nunca con `BYPASSRLS`. La
[ADR-011](docs/common/architecture/decisions/ADR-011-scheduled-checks-and-notice-delivery.md)
lo recoge entero: el recorrido, la tabla de avisos, el resumen diario —uno por
hogar con lo que haya, **ninguno cuando no hay nada**— y la premisa de la
instancia única con su condición de revisión.

Dos cosas que conviene tener presentes al tocar esto:

- **El programador se apaga con `drp.schedule.enabled`**, y `SpringIntegrationTest`
  lo apaga para toda la suite. Encendido, la pasada diaria correría dentro de
  cualquier contexto de prueba y **purgaría hogares** a mitad de otra.
- **Enviar correo y saber qué hogares hay son ya de plataforma**
  (`com.drp.platform.mail`, `HouseholdDirectory`). Se mudaron en lugar de ampliar
  la lista de excepciones de ArchUnit, que sigue teniendo un solo nombre. Lo que
  plataforma necesite del core se resuelve con un puerto que el core implemente
  —como `ScheduledCheck`, `HouseholdDirectory` y `NoticeRecipients`—, nunca con un
  import.

Ni las dos fases ni el cierre de huecos tienen continuación pendiente; lo único
que sigue abierto a propósito —el análisis antivirus, la Fase 3 sin planificar y
el despliegue— está listado al final de cada documento de fase, cada cosa con su
motivo.

Y tres cosas que los dos cierres dejaron fijadas y conviene no volver a abrir:

- **El barrido de aislamiento cubre el contrato entero**, en
  `TenantIsolationSweepTest`, organizado **por forma de ataque y no por recurso**.
  Una operación nueva se añade ahí, con el criterio de inclusión que la cabecera
  de esa clase explica; no nace una clase de barrido nueva.
- **La auditoría de accesibilidad de una pantalla nueva se hereda**: se añade a
  la lista `AUDITED_SCREENS` del recorrido vertical y con eso pasa teclado,
  reflujo y axe en los dos modos. No hace falta escribir nada más. Desde el
  cierre de huecos **no queda ninguna pantalla de la navegación fuera**.
- **La capacidad se mide en dos magnitudes**, no en una: lo que crece con lo que
  el hogar *tiene* y lo que crece con lo que *hace*. Están en
  [`capacity-measurements.md`](docs/backend/operations/capacity-measurements.md),
  junto al criterio de retención de las cinco tablas que crecen sin techo.

La documentación está **en español de España**. Manténla así. El código sigue la
misma frontera que la documentación: prosa y comentarios en castellano, todo
identificador en inglés.

## Dónde vive la verdad

**El reparto del README a `docs/` ya está hecho** (fue la tarea de arranque de la
Fase 1). El [`README.md`](README.md) raíz pasó de 1821 líneas a poco más de 600 y
conserva la visión de conjunto: qué es DRP, su alcance, su arquitectura, el stack,
la estrategia de testing y el roadmap. **El detalle del core ya no está ahí:**

| Buscas | Está en |
|---|---|
| Assets, artículos, ubicaciones, documentos (4.1.1–4.1.3) | [`docs/common/product/core-model.md`](docs/common/product/core-model.md) |
| Usuarios, roles, contraseñas y tokens (4.1.4) | [`docs/common/product/users-and-access.md`](docs/common/product/users-and-access.md) |
| Préstamos (4.1.5) | [`docs/common/product/loans.md`](docs/common/product/loans.md) |
| Decisiones de diseño (4.1.7) | [`docs/common/product/decisions.md`](docs/common/product/decisions.md) |
| Modelo de datos y RLS (5.6) | [`docs/common/architecture/data-model.md`](docs/common/architecture/data-model.md) |
| Casos de uso (5.7) | [`docs/common/product/use-cases/`](docs/common/product/use-cases/README.md) |
| Ejemplos JSON (5.4.3) | [`docs/common/contracts/json-examples.md`](docs/common/contracts/json-examples.md) |
| Ficheros (5.8) | [`docs/backend/`](docs/backend/architecture/file-storage.md), repartido entre architecture, security y operations |

**Los números de sección se conservan tras el traslado**, porque hay más de cien
referencias cruzadas del tipo «ver 4.1.1» por todo el repositorio: 4.1.1 sigue
llamándose 4.1.1 aunque viva en `docs/`. No los renumeres.

De ahí la regla que más fácil es incumplir ahora: **el detalle no se escribe en el
README**. Si tocas el modelo de datos, los casos de uso o la definición del core,
va a su documento; el README solo se toca si cambia el resumen. El mapa completo
está en su sección 9.1.

El contrato de la API vive en [`openapi.yaml`](openapi.yaml) (OpenAPI 3.0.3), en la
raíz. Los ejemplos comentados están en la sección 5.4.3 del README: si cambias uno,
cambia el otro.

## Verificación

[La CI](.github/workflows/ci.yml) tiene **cinco trabajos**: contrato y
documentación, backend, frontend, recorrido vertical y medición de capacidad.
Reprodúcelos en local antes de entregar un cambio; con tocar lo que tu cambio
alcanza basta, salvo que estés cerrando algo.

Validar el contrato de la API. Requiere `pip install pyyaml openapi-spec-validator`.
Además de validar el esquema, exige `operationId` en todas las operaciones, que es
lo que la ADR-007 necesita para generar el cliente:

```bash
python scripts/validate-openapi.py
```

Comprobar que ningún enlace relativo está roto. Son cerca de 800 y se rompen con
facilidad al renumerar secciones o al mover un fichero:

```bash
python scripts/check-links.py
```

Comprobar el contraste de los tokens de color: lee los valores `oklch()` reales de
`frontend/src/index.css` y mide los 49 pares **en los dos modos** —doce de ellos
son los colores de categoría que el usuario puede elegir—, así que un
retoque de paleta que baje de WCAG AA rompe la construcción en lugar de
descubrirse en una auditoría:

```bash
python scripts/check-contrast.py
```

Construir y probar el backend. Las pruebas que tocan la base de datos levantan
PostgreSQL con Testcontainers y corren con un usuario **sujeto a RLS** — nunca
como superusuario, que daría cobertura falsa:

```bash
cd backend && ./gradlew build --no-daemon
```

Comprobar tipos, construir y probar el frontend:

```bash
cd frontend && npm run build && npm test
```

Y el recorrido vertical en un navegador de verdad, que es lo único que comprueba
que una persona llega: el enlace del correo, el teclado con su anillo de foco, la
auditoría axe en los dos modos y el reflujo de 320 px a ultrawide. **Este sí
necesita el `compose.yaml` arriba** —PostgreSQL y Mailpit—, porque ejecuta la
aplicación y no un doble; el backend y Vite los arranca Playwright por su cuenta:

```bash
docker compose up -d postgres mailpit && cd frontend && npm run test:e2e
```

Dos avisos sobre este último, que cuestan un rato de diagnóstico si no se saben.
Si mides colores aplicados, espera a que termine la transición: cambiar de tema
abre 140 ms en los que cada color es una **mezcla de los dos modos**, y medir ahí
da un contraste que no corresponde a ningún color del sistema. Y no audites una
pantalla con el `Spinner` puesto: axe recorre cuatro elementos y pasa.

## Entorno local: para desarrollar, no para probar

`compose.yaml` levanta PostgreSQL, Mailpit y **nginx** —que llegó con el Hito 3,
con `X-Accel-Redirect` y su URL firmada— para ejecutar la aplicación, no para las
pruebas: las de integración usan Testcontainers, que arranca su propio PostgreSQL
efímero y lo destruye al terminar. Si solo vas a construir y probar, no hace falta
levantar nada.

```bash
docker compose up -d
```

La excepción es el recorrido vertical de más arriba, que ejecuta la aplicación de
verdad y necesita **PostgreSQL y Mailpit**; nginx no, porque la entrega directa es
lo que 5.8.4 describe para desarrollo. La prueba de nginx es del backend y levanta
el suyo con la misma plantilla del `compose.yaml`.

Y **no montes un hogar a mano para mirar algo**: hay uno de demostración con
catorce meses de histórico para el core y los cuatro módulos en
[`scripts/seed-demo-data.sql`](scripts/seed-demo-data.sql), que se carga con un
comando, es idempotente y solo toca su propio hogar. Cómo se lanza y qué trae,
en [`demo-dataset.md`](docs/backend/operations/demo-dataset.md); el arranque
entero, en la skill `run-local`.

**Deja la máquina como la encontraste.** Lo que se arranca para comprobar algo
se apaga en cuanto la comprobación está hecha:

```bash
docker compose down && rm -rf .data
```

`.data/` es el volcado de PostgreSQL que crea el arranque; se regenera solo. Y
el `--no-daemon` de más arriba no es un detalle: **el daemon de Gradle no muere
al acabar el build**, sobrevive unas tres horas para acelerar el siguiente. Eso
está bien trabajando en bucle y estorba en una ejecución puntual, que es por lo
que la CI también lo usa. Si acabas con daemons vivos, `--stop` solo alcanza a
los de **su misma versión**: con varias conviviendo hay que invocar cada
distribución por separado.

Cuidado al hacer limpieza con un proceso que lo parece y no lo es: el servidor
de la extensión Gradle de VS Code (`vscode-gradle`, reconocible por su
`--parentPid`) es del editor, no del build.

## Al fusionar un PR: alinear el estado local

En este repositorio **las ramas remotas se borran al fusionar**. Comprobar que un
PR está `MERGED` no cierra la tarea: las referencias locales no se enteran solas,
así que quedan apuntando a ramas muertas, las ramas locales quedan huérfanas y un
worktree puede seguir sentado sobre una rama ya fusionada y borrada. Nada de esto
da error — se acumula en silencio hasta que estorba.

Después de confirmar el merge y hacer `pull` de la rama base:

```bash
git fetch --prune
```

Eso elimina las referencias obsoletas y marca las ramas locales huérfanas como
`: gone` en `git branch -vv`. **Antes de borrar ninguna, comprueba que no se
pierde nada:**

```bash
git log --oneline main..<rama>   # debe dar 0 commits
```

Solo entonces, y siempre con `-d`, nunca con `-D`:

```bash
git branch -d <rama>
```

Si `-d` se niega, es que quedaban commits sin fusionar: para y míralo en lugar de
forzar.

Si un worktree tiene tomada una de esas ramas, apárcalo antes con `git switch
--detach <commit de main>`. No puede tomar la rama base directamente, porque git
no admite la misma rama en dos worktrees a la vez, y mantenerlo sobre la rama
vieja impide borrarla. **Nunca elimines el worktree desde el que estás
trabajando.**

## Decisiones ya cerradas (no las reabras sin motivo)

La Fase 0 terminó sin decisiones de diseño abiertas. Están fijadas:

- **Backend:** Kotlin + Spring Boot, monolito modular, Clean Architecture.
- **Persistencia:** PostgreSQL 16+, migraciones con Flyway en SQL plano.
- **Frontend:** TypeScript + React, mobile-first desde 375px hasta ultrawide.
- **Comunicación:** API REST autenticada (JWT) + event bus in-process entre módulos.
- **Testing:** 60 % unitario de dominio, 25 % integración de casos de uso, 15 %
  contrato de adaptadores y E2E.

Las **quince ADR** en [`docs/common/architecture/decisions/`](docs/common/architecture/decisions/README.md)
recogen el porqué y las alternativas descartadas: las cuatro de la Fase 0 —línea
base, multi-tenancy, RLS y migraciones—, las cinco de la Fase 1 —ficheros
locales, stack de frontend y sistema de diseño, el contrato como fuente de
verdad, monorepo y cadena de construcción, y correo saliente—, las dos de la
Fase 2 —fronteras de módulo y activación por hogar, y programación de
comprobaciones y entrega de avisos— y las cuatro del cierre de huecos: supresión
de datos, Transactional Outbox, conversión de HEIC, y color e icono elegidos por
el usuario dentro de un juego certificado. Léelas antes de proponer un cambio
estructural, y recuerda que **una ADR aceptada no se reescribe** — se amplía con
una sección hacia adelante, como hicieron la ADR-002, la ADR-005, la ADR-006 y
la ADR-011.

## Dos invariantes que condicionan todo el código futuro

**1. Aislamiento multi-tenant en dos capas.** Varios hogares comparten base de
datos. Toda tabla del core lleva `household_id`; todo caso de uso y repositorio
filtra por el `householdId` del token, y **nunca** por uno recibido del cliente.
Además hay Row-Level Security en PostgreSQL como segunda capa (ADR-003). Dos
condiciones que la anulan entera: el usuario de BD de la aplicación no puede tener
`BYPASSRLS`, y hace falta `FORCE ROW LEVEL SECURITY`.

Ojo con lo que no nace de una petición: el proceso diario que marca los préstamos
vencidos (README 4.1.5) no tiene token del que sacar el hogar. La salida fácil
—`BYPASSRLS` para el usuario de la aplicación— desactiva la segunda capa para
**toda** la aplicación, no solo para el proceso. Debe recorrer los hogares uno a
uno fijando `app.household_id` en cada transacción, como cualquier petición.

El otro caso es `CreateHousehold` (README 4.1.4), que además es **la única
escritura sin autenticar** de la API: no parte de ningún hogar porque lo está
creando. Tampoco necesita excepción — la aplicación genera el identificador del
hogar antes de insertar, así que la transacción fija `app.household_id` con ese
valor y todo nace ya dentro del contexto del inquilino.

Toda fila del core lleva `created_by` y `updated_by`, que se toman **del token** y
nunca del cuerpo de la petición. Nulo no es un hueco: significa que el cambio lo
hizo el sistema y no una persona.

**Y una tabla se queda fuera de RLS con datos personales dentro:** `identities`
(README 4.1.4) no lleva `household_id` porque una persona no pertenece a un hogar
—su pertenencia sí—, así que no puede tener política. Es la única tabla con
nombre, correo y teléfono defendida por una sola capa. Su repositorio resuelve
siempre por identidad autenticada: nada de listados ni de búsquedas por correo
fuera del login.

**Persona y papel están separados.** `Identity` son las credenciales, únicas en la
instalación; `HouseholdMember` es el rol dentro de un hogar. Todo lo que el
dominio llama «usuario» —propietario de un asset, prestador, receptor,
`created_by`— apunta a la **pertenencia**; los refresh tokens, a la identidad. En
el MVP una identidad tiene como mucho una pertenencia activa, garantizado por un
índice único parcial que basta retirar para admitir varias.

**2. Un asset es todo material del hogar**, no solo lo económicamente relevante, y
se divide en dos naturalezas que se comportan distinto (README 4.1.1):

- `DURABLE` — identidad propia, una fila por unidad física. Es el único que puede
  actuar como ubicación de otros assets y el único que puede prestarse.
- `CONSUMABLE` — se agota y se repone. Una fila por **existencia**: un artículo en
  una ubicación, con `quantity`. Ceder un consumible es un ajuste de cantidad, no
  un préstamo.

El `type` es inmutable tras el alta. El core mantiene solo un contador: el
seguimiento de existencias (consumos, mínimos, caducidad, lotes) es del módulo
Warehouse, no del core.

**Definición y existencia van separadas.** La ficha de qué es algo vive en un
`Article` (tabla `articles`): `name`, `categoryId`, `unit`, y opcionalmente
marca y código de barras. Un artículo **no es un asset** — no ocupa sitio, no tiene
cantidad, no se presta. Es obligatorio en un `CONSUMABLE` y opcional en un
`DURABLE`, donde deja compartir modelo y documentación entre unidades idénticas.

De ahí tres consecuencias que se olvidan con facilidad:

- **La `unit` la fija el artículo, no la existencia.** Todas las existencias de un
  artículo van en la misma unidad; convertir entre unidad de compra y de consumo es
  de Warehouse.
- **Traer otro paquete de azúcar no da de alta nada.** Es
  `RegisterConsumableIntake` (`POST /api/v1/assets/intake`), que resuelve el
  artículo —creándolo si hace falta— y **suma** sobre la existencia que ya haya en
  esa ubicación. El `quantity` del `PATCH` es lo contrario: absoluto, sustituye.
- **Solo una existencia viva por artículo y ubicación**, garantizado por un índice
  único parcial con `NULLS NOT DISTINCT` que además excluye `status = 'DECOMMISSIONED'` — sin
  esa exclusión, una existencia dada de baja bloquearía su ubicación para siempre.
  Juntar dos que ya existen por separado es `MergeStockItems`
  (`POST /api/v1/assets/{id}/merge`), nunca un `MoveAsset`: la fusión decide qué
  ubicación y qué propietario sobreviven, y eso lo elige el usuario.
- **El nombre y la categoría no se guardan por duplicado.** Cuando el asset tiene
  artículo, son los suyos y se resuelven al leer.

## Convenciones documentales

Las reglas completas están en [`docs/README.md`](docs/README.md). Las que más se
incumplen sin querer:

- **La prosa va en castellano; todo nombre destinado a ser programado, en inglés.**
  Sin excepciones: clases y entidades (`Asset`, `Article`, `Loan`), atributos y
  campos de API en `camelCase`, columnas en `snake_case`, casos de uso y métodos en
  `PascalCase` (`CreateAsset`, `MarkOverdueLoans`) y valores de enumerado en
  `UPPER_SNAKE_CASE` (`DURABLE`, `OVERDUE`). La frontera está entre identificador y
  dato: los nombres de las categorías que siembra cada hogar se muestran al usuario,
  así que son datos y van en su idioma. Las tablas de atributos del README dan las
  dos formas — **Definición (`nombrePrograma`)** — y esa columna se rellena así
  siempre.

- **Una ADR aceptada no se reescribe.** Si queda desfasada, añade una sección que
  enlace hacia adelante (hay un ejemplo al final de la ADR-002) o escribe una nueva
  que la sustituya.
- **La sección 4.1.7 del README es el registro vivo de decisiones.** Las pendientes
  se anotan como pregunta; al resolverse pasan a validadas, con la alternativa
  descartada y su motivo.
- **Todo cambio sustantivo del README añade una fila a su sección 10** (historial),
  y actualiza la fecha de la cabecera.
- Los valores no decididos se marcan `Por decidir`; no se inventa una solución.
- Enlaces siempre relativos, nombres de fichero en `kebab-case.md`.
- La sección 11 del README explica qué tocar al avanzar el proyecto: léela antes de
  hacer una actualización amplia.

## Cómo se trabaja: un bloque grande por sesión

Es la forma con la que se entregó la Fase 1 —cinco hitos, cinco sesiones, cinco
pull requests— y **se conserva para la Fase 2**, con «módulo» donde antes decía
«hito»:

- **Un bloque por sesión.** Arranca leyendo el documento de alcance de lo que vas a
  hacer. Los dos que hay —[`roadmap.md`](docs/common/product/roadmap.md) de la
  Fase 1 y
  [`phase-2-roadmap.md`](docs/common/product/phase-2-roadmap.md) de la 2— están
  **cerrados** y se conservan como historia de cómo se hizo cada cosa; el de la
  Fase 3 no existe todavía, y **escribirlo es una sesión propia**. No mezcles
  bloques: son grandes y mezclarlos hace que ninguno se cierre del todo.
- **Un pull request por bloque**, y no se abre el siguiente hasta que el anterior
  está fusionado. Al fusionar, sigue el procedimiento de alineación local de más
  arriba.
- **Al cerrar un bloque**, actualiza su estado donde viva y añade la fila de
  historial al README. El estado de las *fases* vive en la sección 8 del README y
  el detalle de sus partes, en el documento de la fase. Cada dato en un solo sitio.
- **Cada bloque atraviesa las capas en vertical** —dominio, aplicación, adaptador,
  frontend y sus pruebas—, no una capa entera de cada vez.
- **Cierra también los documentos que se auto-programaron.** La Fase 1 dejó seis
  documentos del frontend diciendo «esto llega en el Hito 4» y hubo que volver a
  por ellos después: si escribes «lo comprobará el bloque siguiente», eso es una
  tarea del bloque siguiente y no se cierra sola.

El criterio de validación estaba fijado desde la ADR-001: un **recorrido
vertical** que atraviese frontend, API autenticada, aplicación, dominio y
PostgreSQL, con pruebas en los tres niveles. Ese recorrido es también lo que
valida en la práctica las decisiones de las ADR-002/003/004, la ADR-005 lo amplía
para que incluya subir una foto, verla adjunta y descargarla, y la ADR-006 le añade
lo que solo se puede medir en un navegador: foco, teclado, contraste aplicado y
reflujo. Existe, se ejecuta en la CI y **es el sitio al que añadir el recorrido de
un módulo nuevo** en lugar de empezar una suite paralela.
