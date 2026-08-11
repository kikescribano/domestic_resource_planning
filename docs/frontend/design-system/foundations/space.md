# Espacio y composición

| Campo | Valor |
|---|---|
| Estado | Borrador |
| Responsable | Equipo DRP |
| Ámbito | frontend |
| Última revisión | 2026-08-10 |

## Propósito

Fijar la rejilla de espaciado, las medidas con nombre que se repiten en todas las
pantallas y las anchuras que gobiernan la composición desde 375 px hasta
ultrawide.

## Alcance

### Incluido

- La rejilla base y las medidas con nombre.
- Los objetivos táctiles y su relación con WCAG.
- Anchuras de composición y breakpoints.
- Qué se hace con el espacio sobrante en pantallas muy anchas.

### Fuera de alcance

- La densidad de los listados, en [`density.md`](density.md).
- El layout concreto de cada pantalla, que es de `patterns/`.

## Contenido

### La rejilla es de 4 px

`--spacing: 0.25rem`, y de ahí sale toda la escala numérica de Tailwind: `p-2`
son 8 px, `gap-6` son 24 px. Cuatro píxeles es un múltiplo cómodo para 16 px de
texto y para 44 px de objetivo táctil, que son las dos medidas de las que cuelga
todo lo demás.

Sobre esa rejilla hay cuatro medidas **con nombre**, porque se repiten en todas
las pantallas y conviene poder cambiarlas de una vez:

| Token | Valor | Para qué |
|---|---|---|
| `--spacing-gutter` | 16 px | Margen lateral de la página en móvil, separación entre bloques hermanos |
| `--spacing-gutter-lg` | 24 px | El mismo margen a partir de `md`, y la separación entre secciones |
| `--spacing-row` | 12 px | Relleno vertical de una fila cómoda, la de móvil |
| `--spacing-row-compact` | 6 px | Relleno vertical de una fila compacta, la de escritorio |

### Objetivos táctiles

| Token | Valor | Origen |
|---|---|---|
| `--spacing-touch` | 44 px | La regla de DRP |
| `--spacing-touch-min` | 24 px | El mínimo del criterio 2.5.8 de WCAG 2.2, nivel AA |

DRP fija **44 px**, casi el doble de lo que exige la norma, y la razón es de uso:
esto se consulta de pie en una cocina o en un trastero, con una mano, a veces con
la otra ocupada. `touch-min` existe solo para las excepciones que la propia norma
admite —un enlace dentro de un párrafo, un control cuya separación con sus
vecinos ya garantiza el objetivo— y usarlo es una decisión consciente, no el
valor por defecto.

Esta medida es lo que hace imposible un listado denso en móvil, y por eso la
densidad se decide donde se decide (ver [`density.md`](density.md)).

### Anchuras de composición

| Token | Valor | Para qué |
|---|---|---|
| `--container-form` | 544 px | La columna de un formulario. Un campo más ancho que esto se lee peor, no mejor |
| `--container-reading` | 68ch | Texto continuo: descripciones, notas, ayuda, correo |
| `--container-shell` | 1536 px | El tope de la aplicación entera |

### Breakpoints y el problema del sobrante

Los breakpoints se derivan del contenido, no de modelos de dispositivo. Los de
Tailwind cubren el tramo habitual —`sm` 640, `md` 768, `lg` 1024, `xl` 1280,
`2xl` 1536— y DRP añade **uno solo**: `--breakpoint-3xl: 100rem` (1600 px). Ahí
es donde la ventana deja de crecer útilmente y hay que decidir qué hacer con el
espacio de más.

Los dos puntos que de verdad importan y que hay que validar son:

- **768 px (`md`)**, donde el listado deja de ser tarjetas y pasa a tabla, y la
  navegación deja la barra inferior y pasa a lateral. Es el único breakpoint del
  sistema que cambia la estructura de una vista, no solo su holgura.
- **1600 px (`3xl`)**, donde entra la regla del sobrante.

**La regla del sobrante** es explícita porque es donde más fácil es hacerlo mal:
en ultrawide el contenido **no se estira**. Se limita a `shell`, el texto continuo
a `reading`, y lo que sobra se reparte en margen o —esta es la opción buena— en
una **segunda columna con el detalle del elemento seleccionado**, que convierte
el espacio en capacidad en lugar de en distancia. Lo que nunca ocurre es que el
nombre de un asset quede a un palmo de su estado porque la fila mide 3000 px.

### Ritmo vertical

El aire crece hacia fuera, no hacia dentro. Al ensanchar la ventana crecen los
márgenes de página y la separación entre bloques; **no** crecen el interlineado,
el relleno de las filas ni la longitud de línea. Es la aplicación directa del
primer principio visual: la calidez está en el marco.

## Decisiones abiertas

- Ninguna.

## Referencias

- [`tokens/`](../tokens/README.md)
- [`density.md`](density.md)
- [`look-and-feel.md`](../../product-design/look-and-feel.md), tabla de
  comportamiento responsive.

## Historial de cambios

| Fecha | Cambio | Autor |
|---|---|---|
| 2026-08-10 | Creación del documento con la rejilla y las anchuras del Hito 1. | Equipo DRP |
