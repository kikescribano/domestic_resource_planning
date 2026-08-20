# Tipografía

| Campo | Valor |
|---|---|
| Estado | Borrador |
| Responsable | Equipo DRP |
| Ámbito | frontend |
| Última revisión | 2026-08-20 |

## Propósito

Fijar qué familias tipográficas usa DRP, dónde va cada una y con qué escala,
para que «tipografía con carácter» no acabe siendo carácter repartido por todas
partes.

## Alcance

### Incluido

- Las dos familias, su reparto y la regla que las mantiene separadas.
- La escala por papel y las dos medidas fluidas.
- Reglas de número, truncado y longitud de línea.

### Fuera de alcance

- El tamaño concreto de cada componente, que se documenta con el componente.
- La voz y el vocabulario, que irán en `content/`.

## Contenido

### Dos familias con un reparto explícito

| Token | Pila | Dónde va |
|---|---|---|
| `--font-display` | `Baloo 2` → `ui-rounded` → `Segoe UI` → `system-ui` → sans-serif | El `h1` de cada página, los titulares de sección cuando abren un bloque, el texto grande de un estado vacío y el nombre del producto |
| `--font-sans` | `ui-sans-serif` → `system-ui` → `Segoe UI` → `Roboto` → … | Todo lo demás: interfaz, formularios, tablas, avisos |
| `--font-mono` | `ui-monospace` → `Cascadia Mono` → `SFMono-Regular` → `Menlo` → `Consolas` | Número de serie, código de barras, identificadores y códigos de error |

La display es **Baloo 2, la redonda gruesa del logotipo**: es lo que hace que
DRP se lea como una casa —la misma letra de la marca— y no como un panel de
control. Sustituyó a la pila serif de sistema el 2026-08-20, y conserva los
**dos límites duros** de aquella: no baja de 20 px y **no entra nunca en una
fila de listado**. Un titular con carácter es carácter; trescientas filas con él
son ruido. Solo el `h1` la lleva atada en la capa base —es único por página y
jamás cae dentro de una fila—, con peso 700 declarado ahí mismo (el preflight
de Tailwind deja los encabezados en `font-weight: inherit`); del `h2` hacia
abajo se pide a mano con la clase `font-display`, y solo cuando titula una
sección, donde resuelve al peso 600.

**La display se autoaloja y las demás siguen siendo de sistema.** Baloo 2 se
sirve desde `node_modules` vía `@fontsource` —los pesos 600, 700 y 800 y nada
más—, así que sigue sin haber dependencia de ningún dominio externo en una
aplicación que la [ADR-006](../../../common/architecture/decisions/ADR-006-frontend-stack-and-design-system.md)
despliega como estáticos detrás de nginx: los `woff2` viajan con el propio
build. La sans de trabajo y la mono conservan sus pilas de sistema: cero bytes
donde el carácter no se juega. Y como el token se llama `display` y no
`baloo`, el día que la marca cambie de letra ningún componente se entera.

### La escala, nombrada por papel

Se nombra por función y no por tamaño, para que subir el cuerpo del texto un día
no obligue a renombrar nada.

| Token | Tamaño | Interlineado | Para qué |
|---|---|---|---|
| `--text-caption` | 13 px | 1,4 | Metadatos, marcas de tiempo, ayuda bajo un campo |
| `--text-body-sm` | 14 px | 1,45 | Fila de tabla compacta, texto secundario |
| `--text-body` | 16 px | 1,6 | Cuerpo por defecto y **todos los campos de formulario** |
| `--text-lead` | 17 px | 1,55 | Entradilla de un vacío o de un diálogo |
| `--text-title-sm` | 18 px | 1,35 | Título de tarjeta, cabecera de diálogo |
| `--text-title` | 22 → 26 px | 1,25 | Título de sección |
| `--text-display` | 28 → 40 px | 1,15 | `h1`, titular de estado vacío |

Los dos últimos usan `clamp()` porque el salto de 375 px a ultrawide no se
resuelve con un valor fijo: un `h1` de 40 px aplasta un móvil y uno de 28 px se
pierde en una pantalla de 3440 px.

**16 px en los campos de formulario no es negociable.** Por debajo de eso, Safari
en iOS hace zoom automático al enfocar un campo, y el usuario se queda con la
página descuadrada y sin forma evidente de volver.

El suelo del sistema son 13 px, y solo para metadatos. No hay ningún tamaño de
12 px o menos: en una aplicación que se consulta de pie en una despensa, ese
tamaño no se lee.

### Números, nombres y longitud de línea

- **Cifras tabulares en todo lo que se compara en columna**: cantidades, unidades
  y fechas de un listado llevan `tabular-nums` y van alineadas a la derecha. Sin
  eso, una columna de cantidades baila y hay que leerla cifra a cifra.
- **Los nombres de asset se truncan a dos líneas**, no a una: «Taladro
  percutor Bosch PSB 1800» sin las dos últimas palabras es otro taladro. El
  estado nunca se trunca.
- **La línea de texto continuo se limita a `--container-reading` (68ch)**. Es la
  medida que impide que en ultrawide un párrafo se convierta en una línea de 200
  caracteres.
- Peso: como máximo dos en la misma vista. Lo que se quiere destacar se destaca
  con tamaño y espacio, no con una tercera negrita.

## Decisiones abiertas

- Ninguna. La que hubo —autoalojar una fuente propia para que DRP se viera
  igual en todas las plataformas— se resolvió el 2026-08-20 con Baloo 2 vía
  `@fontsource`, con un matiz sobre lo previsto: se autoaloja **solo la
  display**, que es donde se juega la identidad, y la sans y la mono siguen
  siendo de sistema.

## Referencias

- [`tokens/`](../tokens/README.md)
- [`density.md`](density.md): dónde la serif tiene prohibida la entrada.
- [`look-and-feel.md`](../../product-design/look-and-feel.md)

## Historial de cambios

| Fecha | Cambio | Autor |
|---|---|---|
| 2026-08-20 | **La display pasa de la pila serif de sistema a Baloo 2**, la redonda gruesa del logotipo, autoalojada vía `@fontsource` en los pesos 600/700/800. Sus dos límites duros —20 px de suelo y prohibida en filas— se conservan tal cual; el peso del `h1` queda declarado en la capa base. | Equipo DRP |
| 2026-08-10 | Creación del documento con las dos familias y la escala del Hito 1. | Equipo DRP |
