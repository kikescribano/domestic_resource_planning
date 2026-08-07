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

**2. Un asset es todo material del hogar**, no solo lo económicamente relevante, y
se divide en dos naturalezas que se comportan distinto (README 4.1.1):

- `DURADERO` — identidad propia, una fila por unidad física. Es el único que puede
  actuar como ubicación de otros assets y el único que puede prestarse.
- `CONSUMIBLE` — se agota y se repone, una fila por existencia con `cantidad` +
  `unidad`. Ceder un consumible es un ajuste de cantidad, no un préstamo.

El `tipo` es inmutable tras el alta. El core mantiene solo un contador: el
seguimiento de existencias (consumos, mínimos, caducidad, lotes) es del módulo
Warehouse, no del core.

## Convenciones documentales

Las reglas completas están en [`docs/README.md`](docs/README.md). Las que más se
incumplen sin querer:

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
