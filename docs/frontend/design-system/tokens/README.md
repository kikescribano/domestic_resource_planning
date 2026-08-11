# Tokens

| Campo | Valor |
|---|---|
| Estado | Borrador |
| Responsable | Equipo DRP |
| Ámbito | frontend |
| Última revisión | 2026-08-10 |

## Propósito

Ser el catálogo de los nombres del sistema de diseño: qué significa cada token,
cuándo se usa, qué valor tiene en cada modo y de dónde sale.

**La implementación vive en un solo sitio**,
[`frontend/src/index.css`](../../../../frontend/src/index.css), declarada con
`@theme` de Tailwind CSS v4. No hay `tailwind.config.js` y no debe haberlo: la
[ADR-006](../../../common/architecture/decisions/ADR-006-frontend-stack-and-design-system.md)
fija Tailwind v4, cuya configuración es CSS.

## Alcance

### Incluido

- El catálogo completo de tokens con su significado y su valor en los dos modos.
- Cómo funciona el modo oscuro y qué contrato tiene con la aplicación.
- Las reglas de uso que un componente no puede saltarse.

### Fuera de alcance

- El porqué de cada valor, que está en [`foundations/`](../foundations/README.md).
- Los ratios de contraste medidos, en [`accessibility/`](../../accessibility/README.md).

## Cómo se nombra

Los nombres son **semánticos**: dicen para qué sirve el color, no cuál es.
`--color-state-overdue`, nunca `--color-red-600`. Un componente que necesita
saber qué color hay detrás de un token está mal escrito, porque impide cambiar el
tema sin tocarlo.

Todos los identificadores van en inglés y la prosa que los explica, en
castellano; es la frontera del repositorio y aquí se nota más que en ningún otro
sitio, porque el fichero es medio código y medio documentación.

Cada token de Tailwind genera su utilidad automáticamente: `--color-surface` da
`bg-surface` y `text-surface`, `--spacing-row` da `p-row` y `gap-row`,
`--radius-lg` da `rounded-lg`, `--text-body` da `text-body`. Esa es la razón de
usar los espacios de nombres de Tailwind en lugar de variables sueltas.

## Color

Los valores se escriben en `oklch()` y con `light-dark()`, que resuelve contra
`color-scheme`: una sola declaración por token cubre los dos modos. Todos están
dentro del gamut sRGB, para que el ratio medido sea el ratio que se ve.

### Superficies

| Token | Qué es | Claro | Oscuro |
|---|---|---|---|
| `--color-surface` | El papel de la página | `oklch(98.4% 0.006 85)` · `#fcf9f5` | `oklch(19% 0.012 70)` · `#17130e` |
| `--color-surface-raised` | Tarjeta, diálogo, menú: lo que está por encima | `oklch(99.6% 0.003 85)` · `#fffefb` | `oklch(23% 0.014 70)` · `#211c16` |
| `--color-surface-sunken` | Hueco: cabecera de tabla, campo de búsqueda, marcador de foto | `oklch(96% 0.009 82)` · `#f5f1eb` | `oklch(15.5% 0.01 70)` · `#0f0c08` |
| `--color-surface-hover` | La fila bajo el puntero | `oklch(96% 0.014 70)` · `#f8f0e8` | `oklch(27% 0.016 70)` · `#2c251e` |
| `--color-scrim` | La capa que atenúa el fondo de un diálogo | `oklch(26% 0.02 60 / 0.5)` | `oklch(10% 0.01 60 / 0.65)` |

### Tinta

| Token | Cuándo se usa | Claro | Oscuro |
|---|---|---|---|
| `--color-ink` | Contenido, títulos, valores | `oklch(26% 0.02 60)` · `#2b221a` | `oklch(95% 0.008 85)` · `#f1eee9` |
| `--color-ink-muted` | Secundario: etiquetas, descripciones, ayuda | `oklch(47% 0.02 60)` · `#645850` | `oklch(76% 0.012 80)` · `#b5b0a9` |
| `--color-ink-subtle` | Metadatos, marcas de tiempo y **placeholder** | `oklch(52% 0.018 60)` · `#71675f` | `oklch(68% 0.012 80)` · `#9c9890` |
| `--color-ink-disabled` | Control desactivado. Único exento de 4,5:1 | `oklch(65% 0.012 60)` · `#958e88` | `oklch(50% 0.01 70)` · `#67625d` |
| `--color-ink-inverse` | Texto sobre un relleno sólido de acento o de peligro | `oklch(99% 0.004 85)` · `#fdfcf9` | `oklch(18% 0.02 60)` · `#180f09` |

### Bordes

| Token | Cuándo se usa | Claro | Oscuro |
|---|---|---|---|
| `--color-border-subtle` | Separar filas, delimitar tarjetas. **Decorativo** | `oklch(92% 0.008 80)` · `#e7e4df` | `oklch(30% 0.012 70)` · `#322d27` |
| `--color-border` | Delimitar un **control**. Obligado a 3:1 | `oklch(62% 0.015 70)` · `#8c857d` | `oklch(58% 0.018 70)` · `#81796f` |
| `--color-border-strong` | Control activo, seleccionado o con énfasis | `oklch(45% 0.02 70)` · `#5d5449` | `oklch(72% 0.02 70)` · `#ada397` |

> Poner `border-subtle` en un `input` incumple WCAG 1.4.11. No son
> intercambiables.

### Acento y foco

| Token | Cuándo se usa | Claro | Oscuro |
|---|---|---|---|
| `--color-accent` | Relleno de la acción principal. **Una vez por pantalla** | `oklch(52% 0.13 42)` · `#a54a24` | `oklch(70% 0.12 45)` · `#dc855d` |
| `--color-accent-hover` | Ese mismo relleno bajo el puntero | `oklch(46% 0.13 42)` · `#91380e` | `oklch(76% 0.12 45)` · `#f0986f` |
| `--color-accent-ink` | El acento **como texto**: enlaces y botones terciarios | `oklch(47% 0.12 42)` · `#903f1c` | `oklch(78% 0.1 50)` · `#eca57d` |
| `--color-accent-soft` | Fondo tenue: fila seleccionada, distintivo, selección de texto | `oklch(94% 0.03 55)` · `#fce6d9` | `oklch(30% 0.04 45)` · `#3f271d` |
| `--color-focus` | El anillo de foco, el mismo en todo el sistema | `oklch(38% 0.05 60)` · `#563c26` | `oklch(85% 0.05 85)` · `#ddcca9` |

> `accent` y `accent-ink` no son el mismo color a propósito: un relleno solo
> necesita 3:1 contra el papel, y un texto necesita 4,5:1.

### Feedback del sistema

Describen lo que le pasa a una **petición**. Cada uno tiene su variante `-soft`
para el fondo del aviso, y el texto del aviso va del color base.

| Token | Cuándo | Claro | Oscuro |
|---|---|---|---|
| `--color-success` / `-soft` | La operación salió bien y no se ve en pantalla | `oklch(45% 0.1 152)` · `#1f6538` / `#e0f3e4` | `oklch(78% 0.11 152)` · `#7fcc94` / `#192f1f` |
| `--color-warning` / `-soft` | Se puede seguir, pero conviene mirarlo | `oklch(48% 0.09 70)` · `#7e541b` / `#faedd6` | `oklch(80% 0.1 80)` · `#e0b771` / `#362913` |
| `--color-danger` / `-soft` | Falló, o va a destruir algo | `oklch(48% 0.16 27)` · `#a52a26` / `#fbe8e5` | `oklch(72% 0.14 27)` · `#ef7f74` / `#3e1f1b` |
| `--color-info` / `-soft` | Contexto que el usuario no ha pedido pero necesita | `oklch(45% 0.12 250)` · `#0e5794` / `#e1effd` | `oklch(76% 0.1 250)` · `#7fb6ee` / `#192a3c` |

### Estados del dominio

Describen lo que le pasa a una **cosa del hogar**. Tienen nombre propio aunque
hoy tres de ellos compartan valor con su equivalente de feedback: un préstamo
vencido no es un error del sistema, y así el inventario no se repinta el día que
el rojo de error cambie.

| Token | Estado | Claro | Oscuro |
|---|---|---|---|
| `--color-state-available` / `-soft` | `AVAILABLE` | `oklch(45% 0.1 152)` · `#1f6538` / `#e0f3e4` | `oklch(78% 0.11 152)` · `#7fcc94` / `#192f1f` |
| `--color-state-lent` / `-soft` | `LENT` | `oklch(48% 0.09 70)` · `#7e541b` / `#faedd6` | `oklch(80% 0.1 80)` · `#e0b771` / `#362913` |
| `--color-state-overdue` / `-soft` | `OVERDUE` | `oklch(48% 0.16 27)` · `#a52a26` / `#fbe8e5` | `oklch(72% 0.14 27)` · `#ef7f74` / `#3e1f1b` |
| `--color-state-decommissioned` / `-soft` | `DECOMMISSIONED` | `oklch(52% 0.012 70)` · `#6e6862` / `#efece9` | `oklch(68% 0.012 70)` · `#9d9790` / `#2b2825` |
| `--color-state-out-of-stock` / `-soft` | Consumible con `quantity` a cero | `oklch(45% 0.12 250)` · `#0e5794` / `#e1effd` | `oklch(76% 0.1 250)` · `#7fb6ee` / `#192a3c` |

Un estado se pinta **siempre** con las tres piezas: color, icono y etiqueta. El
mapa de iconos está en [`iconography.md`](../foundations/iconography.md).

## Tipografía

| Token | Valor |
|---|---|
| `--font-sans` | `ui-sans-serif`, `system-ui`, `Segoe UI`, `Roboto`, … |
| `--font-display` | `Iowan Old Style`, `Palatino Linotype`, `Palatino`, `Book Antiqua`, `Georgia`, `Noto Serif`, serif |
| `--font-mono` | `ui-monospace`, `Cascadia Mono`, `SFMono-Regular`, `Menlo`, `Consolas` |
| `--text-caption` | 0.8125rem / 1.4 |
| `--text-body-sm` | 0.875rem / 1.45 |
| `--text-body` | 1rem / 1.6 |
| `--text-lead` | 1.0625rem / 1.55 |
| `--text-title-sm` | 1.125rem / 1.35 |
| `--text-title` | `clamp(1.375rem, 1.28rem + 0.4vw, 1.625rem)` / 1.25 |
| `--text-display` | `clamp(1.75rem, 1.5rem + 1.1vw, 2.5rem)` / 1.15 |

`--font-display` está atado al `h1` en la capa base. Del `h2` hacia abajo se pide
con la clase `font-display`, y **nunca dentro de una fila de listado**.

## Espacio

| Token | Valor | Para qué |
|---|---|---|
| `--spacing` | 0.25rem | La rejilla; de aquí sale toda la escala numérica (`p-4` = 16 px) |
| `--spacing-gutter` | 1rem | Margen lateral de página y separación entre bloques |
| `--spacing-gutter-lg` | 1.5rem | El mismo, desde `md`, y separación entre secciones |
| `--spacing-row` | 0.75rem | Relleno vertical de fila cómoda |
| `--spacing-row-compact` | 0.375rem | Relleno vertical de fila compacta |
| `--spacing-touch` | 2.75rem | **44 px**: el objetivo táctil de DRP |
| `--spacing-touch-min` | 1.5rem | 24 px: el mínimo de WCAG 2.5.8, solo para excepciones |
| `--container-form` | 34rem | Anchura de la columna de formulario |
| `--container-reading` | 68ch | Longitud máxima de línea de texto continuo |
| `--container-shell` | 96rem | Tope de la aplicación en pantallas anchas |
| `--breakpoint-3xl` | 100rem | El único breakpoint propio: el tramo ultrawide |

## Forma, elevación y movimiento

| Token | Valor | Para qué |
|---|---|---|
| `--radius-xs` … `--radius-2xl` | 4, 6, 8, 12, 16, 24 px | Casilla · fila · control · tarjeta · diálogo · ilustración |
| `--shadow-xs` … `--shadow-lg` | Dos capas, tono pardo translúcido | Control · tarjeta · menú · diálogo |
| `--duration-instant` | 80 ms | Respuesta al puntero |
| `--duration-fast` | 140 ms | Cambio de estado; también el `transition` por defecto |
| `--duration-base` | 220 ms | Desplegar y plegar |
| `--duration-slow` | 320 ms | Abrir o cerrar un diálogo |
| `--ease-standard` | `cubic-bezier(0.2, 0, 0.2, 1)` | Por defecto |
| `--ease-out-soft` | `cubic-bezier(0.16, 0.84, 0.44, 1)` | Lo que entra |
| `--ease-in-soft` | `cubic-bezier(0.5, 0, 0.75, 0)` | Lo que sale |

## Modos claro y oscuro

**Se implementan las dos vías, y no es redundancia.**

- **`prefers-color-scheme`** es el comportamiento por defecto: sin tocar nada, la
  aplicación sigue al sistema operativo. Funciona sin JavaScript.
- **`data-theme` en `<html>`**, con valor `light` o `dark`, **gana en las dos
  direcciones**. Hace falta porque DRP se consulta en la despensa a las once de
  la noche con el portátil en claro, y porque una preferencia de sistema no es
  una preferencia de aplicación. Además es lo que permite probar los dos modos de
  forma determinista con Playwright en el Hito 4, en lugar de depender de la
  configuración de la máquina.

La mecánica es `color-scheme` más `light-dark()`:

```css
:root                     { color-scheme: light dark; }  /* manda el sistema */
:root[data-theme='light'] { color-scheme: only light; }
:root[data-theme='dark']  { color-scheme: only dark; }
```

Como los tokens se declaran con `light-dark(claro, oscuro)`, cambiar el atributo
repinta la aplicación entera sin recargar. Y como `color-scheme` es un mecanismo
del navegador y no un truco propio, **los controles nativos acompañan solos**:
barras de scroll, selector de fecha, autocompletado y menús del sistema.

### Contrato con la aplicación

El sistema de diseño aporta el CSS; la aplicación tiene que aportar tres cosas,
que corresponden a quien construye el shell:

1. Un conmutador de **tres estados** —Claro / Oscuro / Automático—, donde
   «Automático» **quita** el atributo en lugar de calcular un valor.
2. Persistir la elección en `localStorage`, con la clave `drp.theme`.
3. Un script en línea en `index.html`, antes de pintar, que aplique el atributo
   guardado. Sin él hay un parpadeo de tema en cada recarga.

La variante `dark:` de Tailwind está redefinida en `index.css` para seguir la
misma regla que los tokens. Aun así, **un componente no debería necesitarla**: si
está usando tokens, ya funciona en los dos modos.

## Reglas de uso

1. **Nada de colores crudos.** Ni `#hex`, ni `rgb()`, ni las escalas por defecto
   de Tailwind (`bg-slate-100`). Si falta un token, se añade aquí antes de usarlo.
2. **Nada de modificadores de opacidad sobre un token de color** (`bg-surface/50`).
   Rompen la garantía de contraste, que está medida sobre el color pleno, y
   además `color-mix()` sobre `light-dark()` no está igual de asentado en todos
   los navegadores. Para un fondo tenue existe la variante `-soft`.
3. **El borde de un control es `border`**, no `border-subtle`.
4. **El acento sólido, una vez por pantalla.**
5. **Un estado nunca se pinta solo con color**: color, icono y etiqueta.
6. **La serif no entra en una fila de listado.**
7. Si un valor se repite en dos componentes, es un token que falta.

## Decisiones abiertas

- **Retirar la paleta por defecto de Tailwind** con `--color-*: initial`, para
  que la regla 1 se verifique sola en lugar de por revisión. Se propone hacerlo
  al cerrar el Hito 1: hoy rompería en silencio cualquier componente en
  construcción.
- **Exportar los tokens fuera de CSS** —a JSON o TypeScript— si algún día hace
  falta leerlos desde JavaScript, por ejemplo para dibujar una gráfica en canvas.
  No hace falta todavía y duplicar la fuente sin necesidad sería peor.

## Referencias

- [`frontend/src/index.css`](../../../../frontend/src/index.css): la
  implementación, y la única fuente de los valores.
- [`foundations/`](../foundations/README.md): el porqué de cada decisión.
- [`accessibility/`](../../accessibility/README.md): los ratios medidos.
- [`ADR-006`](../../../common/architecture/decisions/ADR-006-frontend-stack-and-design-system.md)

## Historial de cambios

| Fecha | Cambio | Autor |
|---|---|---|
| 2026-08-10 | Creación del catálogo con los tokens del Hito 1 y el contrato de modo oscuro. | Equipo DRP |
