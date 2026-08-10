# ADR-008: Estructura del repositorio y cadena de construcción

- Estado: accepted
- Fecha: 2026-08-10
- Responsables: Equipo DRP
- Ámbito: common
- Sustituye: Ninguna

## Contexto

Al terminar la Fase 0 el repositorio contiene **solo documentación**: no hay
build, ni dependencias, ni pruebas, ni integración continua, ni `.github/`. El
`.gitignore` son dos líneas que ignoran artefactos de Python, restos de la
herramienta que genera el material de marketing.

Las dos comprobaciones que sí existen —validar `openapi.yaml` y detectar enlaces
relativos rotos, unos 170— viven como órdenes `python -c` copiadas en
[`CLAUDE.md`](../../../../CLAUDE.md). Funcionan, pero dependen de que alguien las
recuerde y las pegue.

La Fase 1 mete a la vez un backend en Kotlin, un frontend en TypeScript y un
contrato compartido que la [ADR-007](ADR-007-openapi-contract-as-source-of-truth.md)
declara fuente de verdad de ambos. Hay que decidir dónde vive todo eso y cómo se
construye y se comprueba.

Pesa además una convención ya escrita en [`docs/README.md`](../../../README.md):
**la documentación cambia en el mismo incremento que el comportamiento** al que
se refiere.

## Decisión

**Monorepo**, en este mismo repositorio:

```
backend/            Gradle con Kotlin DSL
frontend/           Vite
docs/               documentación por ámbito
openapi.yaml        contrato compartido, en la raíz
compose.yaml        PostgreSQL 16, Mailpit y nginx
scripts/            validate-openapi.py, check-links.py
.github/workflows/  integración continua
```

El backend se organiza por capas de Clean Architecture —`domain`, `application`
con sus puertos, `adapter` y `config`— y dentro de la de aplicación, por módulos
del monolito.

**Cadena de pruebas:**

| Ámbito | Herramientas |
|---|---|
| Backend | **JUnit 5** como único runner, **Testcontainers** para PostgreSQL real, aserciones de **`kotest-assertions-core`** |
| Frontend | **Vitest** + Testing Library; **Playwright** para el recorrido vertical |

Toda prueba que toque la base de datos se ejecuta con un **usuario sujeto a RLS**,
nunca con superusuario ni con `BYPASSRLS`: es la restricción que
[`docs/backend/data/`](../../../backend/data/README.md) ya daba por aceptada, y
sin ella la cobertura del aislamiento sería falsa.

Las dos comprobaciones documentales se versionan en `scripts/` y las ejecuta la
integración continua, junto a Spectral, la construcción del backend y la del
frontend.

## Alternativas consideradas

- **Dos repositorios separados (`drp-backend`, `drp-frontend`):** separa ciclos
  de vida y despliegues. Se descarta porque el contrato es de los dos: cada
  cambio pasaría a ser tres pull requests coordinados en tres repositorios, y la
  convención de documentación síncrona dejaría de poder cumplirse sin ceremonia.
- **Monorepo con Nx o Turborepo:** orquestación de tareas y caché compartida.
  Aporta poco con dos paquetes de tecnologías distintas —Gradle ya orquesta el
  backend y Vite el frontend— y añade una capa más que aprender y actualizar.
- **Kotest como runner de pruebas:** encajaría bien con las cerca de cincuenta
  reglas de dominio ya redactadas, por sus especificaciones legibles y sus
  pruebas basadas en datos. Se descarta como runner porque `@SpringBootTest` se
  apoya en JUnit 5 de serie y tenerlos a la vez significa dos informes y dos
  formas de escribir un test. Sus **aserciones** sí se adoptan: es la parte que
  aporta legibilidad sin partir la infraestructura.
- **Maven en lugar de Gradle:** más convencional en el ecosistema Spring, pero
  Gradle con Kotlin DSL deja la configuración de la construcción en el mismo
  lenguaje que el código.

## Consecuencias

### Positivas

- Un solo pull request puede mover contrato, backend, frontend y documentación a
  la vez, que es exactamente lo que la convención pide.
- Las comprobaciones que hoy dependen de la memoria pasan a fallar solas.
- Un único punto de entrada para levantar el entorno completo, incluida la
  entrega de ficheros por nginx que exige la
  [ADR-005](ADR-005-local-file-storage.md).

### Costes y riesgos

- La integración continua construye las dos partes en cada cambio, aunque solo se
  toque una. Se mitiga con filtros por ruta, no cambiando de estructura.
- Un monorepo con dos tecnologías obliga a tener ambas cadenas instaladas para
  trabajar en cualquiera de las dos.
- Testcontainers exige Docker en la máquina de desarrollo y en la integración
  continua, y encarece las pruebas de integración frente a una base en memoria.
  Es un coste asumido a propósito: la ADR-003 no admite otra cosa.

## Validación o reversión

Se considera validada cuando la integración continua ejecute, sobre un repositorio
recién clonado y sin pasos manuales, la validación del contrato, la comprobación
de enlaces, las pruebas del backend contra PostgreSQL real y las del frontend; y
cuando `docker compose up` levante un entorno donde el recorrido vertical de la
ADR-001 se ejecute entero.

Revisar si backend y frontend llegan a tener ciclos de despliegue tan distintos
que la construcción conjunta estorbe más de lo que la coordinación aporta.
