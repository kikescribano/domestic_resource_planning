# Tokens

| Campo | Valor |
|---|---|
| Estado | Borrador |
| Responsable | Equipo DRP |
| Ámbito | frontend |
| Última revisión | 2026-08-20 |

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
| `--color-surface` | El papel de la página | `oklch(98.4% 0.006 180)` · `#f6fbfa` | `oklch(19% 0.018 190)` · `#0a1616` |
| `--color-surface-raised` | Tarjeta, diálogo, menú: lo que está por encima | `oklch(99.6% 0.003 180)` · `#fcfefe` | `oklch(23% 0.02 190)` · `#12201f` |
| `--color-surface-sunken` | Hueco: cabecera de tabla, campo de búsqueda, marcador de foto | `oklch(96% 0.009 177)` · `#ecf4f2` | `oklch(15.5% 0.014 190)` · `#060e0d` |
| `--color-surface-hover` | La fila bajo el puntero | `oklch(96% 0.014 183)` · `#e8f5f3` | `oklch(27% 0.022 190)` · `#1a2a29` |
| `--color-scrim` | La capa que atenúa el fondo de un diálogo | `oklch(26% 0.02 190 / 0.5)` | `oklch(10% 0.01 190 / 0.65)` |

### Tinta

| Token | Cuándo se usa | Claro | Oscuro |
|---|---|---|---|
| `--color-ink` | Contenido, títulos, valores | `oklch(26% 0.02 190)` · `#192726` | `oklch(95% 0.008 180)` · `#e9f0ef` |
| `--color-ink-muted` | Secundario: etiquetas, descripciones, ayuda | `oklch(47% 0.02 190)` · `#4e5f5d` | `oklch(76% 0.012 185)` · `#a9b4b2` |
| `--color-ink-subtle` | Metadatos, marcas de tiempo y **placeholder** | `oklch(52% 0.018 190)` · `#5e6c6b` | `oklch(68% 0.012 185)` · `#919b99` |
| `--color-ink-disabled` | Control desactivado. Único exento de 4,5:1 | `oklch(65% 0.012 190)` · `#879291` | `oklch(50% 0.01 190)` · `#5d6564` |
| `--color-ink-inverse` | Texto sobre un relleno sólido de acento o de peligro | `oklch(99% 0.004 180)` · `#f9fdfc` | `oklch(18% 0.025 190)` · `#041514` |

### Bordes

| Token | Cuándo se usa | Claro | Oscuro |
|---|---|---|---|
| `--color-border-subtle` | Separar filas, delimitar tarjetas. **Decorativo** | `oklch(92% 0.008 184)` · `#dfe6e5` | `oklch(30% 0.012 190)` · `#27302f` |
| `--color-border` | Delimitar un **control**. Obligado a 3:1 | `oklch(62% 0.015 186)` · `#7d8987` | `oklch(58% 0.018 190)` · `#6f7e7d` |
| `--color-border-strong` | Control activo, seleccionado o con énfasis | `oklch(45% 0.02 186)` · `#495957` | `oklch(72% 0.02 190)` · `#97a9a7` |

> Poner `border-subtle` en un `input` incumple WCAG 1.4.11. No son
> intercambiables.

### Acento y foco

| Token | Cuándo se usa | Claro | Oscuro |
|---|---|---|---|
| `--color-accent` | Relleno de la acción principal. **Una vez por pantalla** | `oklch(53% 0.076 185)` · `#2d7a71` | `oklch(78% 0.075 185)` · `#7ec7be` |
| `--color-accent-hover` | Ese mismo relleno bajo el puntero | `oklch(47% 0.078 185)` · `#116960` | `oklch(84% 0.07 185)` · `#96dad0` |
| `--color-accent-ink` | El acento **como texto**: enlaces y botones terciarios | `oklch(47% 0.075 187)` · `#176862` | `oklch(80% 0.08 185)` · `#7fcfc4` |
| `--color-accent-soft` | Fondo tenue: fila seleccionada, distintivo, selección de texto | `oklch(94% 0.03 180)` · `#d7f2ec` | `oklch(30% 0.045 188)` · `#0c3532` |
| `--color-focus` | El anillo de foco, el mismo en todo el sistema | `oklch(38% 0.05 190)` · `#1d4b48` | `oklch(85% 0.05 185)` · `#aad9d2` |

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

### Colores de categoría

Los seis entre los que un hogar elige la identidad visual de cada categoría
([ADR-015](../../../common/architecture/decisions/ADR-015-user-chosen-category-identity.md)).
**Son la excepción a la regla de nombres de arriba, y la única**: el significado
lo pone la persona que clasifica, no el sistema, así que un nombre semántico no
existe —`--color-category-3` sería ilegible en la migración, en el contrato y
aquí—. Ninguno cae encima de los cinco tonos del dominio; el que más se acerca
al acento es `teal`, a 10° del teal de marca, y se admite porque el color de una
categoría nunca va solo ni comparte forma con un botón (ver
[`color.md`](../foundations/color.md)).

| Token | Tono | Claro | Oscuro |
|---|---|---|---|
| `--color-category-rose` / `-soft` | 350 | `oklch(47% 0.14 350)` · `#913266` / `#fbe6ef` | `oklch(78% 0.12 350)` · `#f197c2` / `#391f2c` |
| `--color-category-plum` / `-soft` | 310 | `oklch(46% 0.14 310)` · `#703c91` / `#f2e9fa` | `oklch(78% 0.11 310)` · `#cda4ec` / `#302239` |
| `--color-category-indigo` / `-soft` | 275 | `oklch(46% 0.14 275)` · `#444da4` / `#e8ecfc` | `oklch(78% 0.11 275)` · `#a3b2fe` / `#22273f` |
| `--color-category-sky` / `-soft` | 230 | `oklch(46% 0.09 230)` · `#056180` / `#dcf1fb` | `oklch(78% 0.1 230)` · `#6ec3eb` / `#112c39` |
| `--color-category-teal` / `-soft` | 195 | `oklch(45% 0.07 195)` · `#156161` / `#daf3f2` | `oklch(78% 0.1 195)` · `#5ecbcb` / `#0b2f2f` |
| `--color-category-moss` / `-soft` | 130 | `oklch(45% 0.1 130)` · `#42601d` / `#e6f1dc` | `oklch(78% 0.11 130)` · `#a0c679` / `#222d17` |

**El croma no es uniforme a propósito**: `sky` y `teal` bajan a 0,09 y 0,07 en
modo claro porque con más se salen del gamut sRGB a esa claridad, y un color
recortado por el navegador deja de ser el color medido. Lo destapó la propia
comprobación de gamut de `check-contrast.py`.

Los doce pares que producen —cada color contra su `-soft` y contra
`surface-sunken`, que es el marcador de una foto que falta— están en la lista del
script, que es lo que impide que un color elegido por el usuario sea lo único de
la interfaz sin medir.

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
| `--shadow-xs` … `--shadow-lg` | Dos capas, tono de pino translúcido | Control · tarjeta · menú · diálogo |
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
  forma determinista, sin depender de la configuración de la máquina: el
  [recorrido vertical](../../../../frontend/e2e/vertical-journey.spec.ts) audita
  las dos pantallas que atraviesa poniendo el atributo, y así lo hace igual en
  cualquier portátil y en la CI.

> **Cambiar el atributo no repinta de golpe.** Los componentes llevan
> `transition-colors`, así que hay **140 ms en los que cada color es una mezcla de
> los dos modos** —y esa mezcla no la mide ningún script ni sale de ninguna
> decisión—. Al cerrar la Fase 1 eso acusó al botón principal de dar 3,55:1 en
> oscuro cuando sus tokens daban entonces 6,77:1. Quien mida colores aplicados tiene que
> esperar a que no quede ninguna transición viva; quien construya el conmutador de
> tema tiene aquí la razón por la que el cambio se ve y no se nota.

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
| 2026-08-20 | **La paleta de marca gira al esquema de la identidad comercial**: los neutros pasan del pardo cálido (tonos 60–85) al pino frío (tonos 177–190) y el acento deja la terracota por el teal `#2d7a71` / `#7ec7be`, los mismos que usan las presentaciones de DRP. Feedback, estados del dominio y colores de categoría no cambian. Los 48 pares siguen medidos y dentro de AA. | Equipo DRP |
| 2026-08-20 | Entran los **doce tokens de color de categoría** (cierre de huecos, Hito 4): seis colores con su variante suave, la primera familia que elige el usuario y la única con nombre descriptivo, con su excepción a la regla de nombres razonada. | Equipo DRP |
| 2026-08-10 | Creación del catálogo con los tokens del Hito 1 y el contrato de modo oscuro. | Equipo DRP |
| 2026-08-17 | La prueba determinista de los dos modos con `data-theme` ya existe, así que deja de anunciarse en futuro. Se anota lo que costó descubrirla: cambiar el atributo abre 140 ms de transición en los que cada color es una mezcla de los dos modos, y medir ahí da un contraste que no corresponde a ningún color del sistema. | Equipo DRP |
