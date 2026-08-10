# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Lo primero: el código acaba de empezar

DRP está en la **Fase 1 (Core MVP)**, recién arrancada. El repositorio dejó de
ser solo documentación: hay un monorepo con `backend/` (Kotlin, Gradle con Kotlin
DSL) y `frontend/` (TypeScript, React sobre Vite), más `compose.yaml`, `scripts/`
y `.github/workflows/`.

Lo que hay es **andamiaje con una regla de dominio dentro**, no el core
implementado. El alcance, los cinco hitos y el criterio de aceptación están en la
sección 8.2 del README. No des por hecho que un caso de uso existe porque esté
definido: casi ninguno lo está todavía.

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

Las cuatro comprobaciones que ejecuta [la CI](.github/workflows/ci.yml). Pásalas
antes de entregar un cambio.

Validar el contrato de la API. Requiere `pip install pyyaml openapi-spec-validator`.
Además de validar el esquema, exige `operationId` en todas las operaciones, que es
lo que la ADR-007 necesita para generar el cliente:

```bash
python scripts/validate-openapi.py
```

Comprobar que ningún enlace relativo está roto. Son unos 160 y se rompen con
facilidad al renumerar secciones o al mover un fichero:

```bash
python scripts/check-links.py
```

Construir y probar el backend. Las pruebas que tocan la base de datos levantan
PostgreSQL con Testcontainers y corren con un usuario **sujeto a RLS** — nunca
como superusuario, que daría cobertura falsa:

```bash
cd backend && ./gradlew build
```

Comprobar tipos, construir y probar el frontend:

```bash
cd frontend && npm run build && npm test
```

Y para levantar el entorno local (PostgreSQL y Mailpit; nginx llega con el Hito 3):

```bash
docker compose up -d
```

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

Las cuatro ADR en [`docs/common/architecture/decisions/`](docs/common/architecture/decisions/README.md)
recogen el porqué y las alternativas descartadas. Léelas antes de proponer un
cambio estructural.

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

## Cómo se ejecuta la Fase 1: un hito por sesión

La Fase 1 se entrega en **cinco hitos**, y el Hito 0 ya está cerrado. La forma de
trabajar es explícita y conviene respetarla:

- **Un hito por sesión.** Arranca leyendo
  [`docs/common/product/roadmap.md`](docs/common/product/roadmap.md), que es la
  fuente del alcance, el criterio de aceptación y el estado. No mezcles hitos:
  son bloques grandes y mezclarlos hace que ninguno se cierre del todo.
- **Un pull request por hito**, y no se abre el siguiente hasta que el anterior
  está fusionado. Al fusionar, sigue el procedimiento de alineación local de más
  arriba.
- **Al cerrar un hito**, actualiza su estado en `roadmap.md` y añade la fila de
  historial al README. El estado de las *fases* vive en la sección 8 del README;
  el de los *hitos*, en `roadmap.md`. Cada dato en un solo sitio.
- **Cada hito atraviesa las capas en vertical** —dominio, aplicación, adaptador,
  frontend y sus pruebas—, no una capa entera de cada vez.

El criterio de validación estaba fijado desde la ADR-001: un **recorrido
vertical** que atraviese frontend, API autenticada, aplicación, dominio y
PostgreSQL, con pruebas en los tres niveles. Ese recorrido es también lo que
valida en la práctica las decisiones de las ADR-002/003/004, y la ADR-005 lo
amplía para que incluya subir una foto, verla adjunta y descargarla.
