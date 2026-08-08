# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Lo primero: aquí todavía no hay código

DRP está en la transición de Fase 0 (definición) a Fase 1 (Core MVP). El
repositorio contiene **solo documentación**: no hay build, ni tests, ni
dependencias, ni estructura `backend/` o `frontend/`. No busques un `package.json`
o un `build.gradle.kts` — no existen todavía.

La documentación está **en español de España**. Manténla así.

## Dónde vive la verdad

El [`README.md`](README.md) raíz (~720 líneas) es la **fuente vigente** de toda la
definición del core: modelo de dominio, modelo de datos, casos de uso, event bus,
API y estrategia de testing. No es un README de presentación: es el documento de
diseño.

`docs/` es en su mayor parte andamiaje — índices que declaran «Contenido
previsto». Aplicando su propia regla de ubicación, parte del README debería vivir
allí; **el reparto está deliberadamente aplazado a la Fase 1**, con el motivo y el
destino de cada sección en [`docs/README.md`](docs/README.md). No lo adelantes sin
que te lo pidan.

El contrato de la API vive en [`openapi.yaml`](openapi.yaml) (OpenAPI 3.0.3), en la
raíz. Los ejemplos comentados están en la sección 5.4.3 del README: si cambias uno,
cambia el otro.

## Verificación

No hay suite de tests, pero sí dos comprobaciones que conviene pasar antes de
entregar un cambio documental. Requieren `pip install pyyaml openapi-spec-validator`.

Validar el contrato de la API:

```bash
python -c "from openapi_spec_validator import validate; from openapi_spec_validator.readers import read_from_filename; s,_=read_from_filename('openapi.yaml'); validate(s); print('OK')"
```

Comprobar que ningún enlace relativo está roto (son ~170 y se rompen con facilidad
al renumerar secciones):

```bash
python -c "import re,pathlib;bad=[f'{m.as_posix()} -> {l}' for m in pathlib.Path('.').rglob('*.md') if '.git' not in m.parts for l in re.findall(r'\]\(([^)#][^)]*)\)', m.read_text(encoding='utf-8')) if not l.startswith(('http','mailto')) and not (m.parent/l.split('#')[0]).resolve().exists()];print('\n'.join(bad) or 'OK')"
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

## Al empezar la Fase 1

El criterio de validación ya está fijado en la ADR-001: un **recorrido vertical**
que atraviese frontend, API autenticada, aplicación, dominio y PostgreSQL, con
pruebas en los tres niveles. Ese recorrido es también lo que valida en la práctica
las decisiones de las ADR-002/003/004.
