# Design system

Este directorio es la fuente documental de las decisiones visuales reutilizables
y de los componentes de interfaz. Implementa la dirección definida en
[`look-and-feel.md`](../product-design/look-and-feel.md) sin duplicarla.

## Estructura

Escrito, al cerrar el Hito 1 junto con las ocho dimensiones de la dirección
visual:

- [`foundations/`](foundations/README.md) — [color](foundations/color.md),
  [tipografía](foundations/typography.md), [espacio](foundations/space.md),
  [densidad](foundations/density.md),
  [forma y elevación](foundations/shape-and-elevation.md),
  [iconografía](foundations/iconography.md) y [movimiento](foundations/motion.md).
- [`tokens/`](tokens/README.md) — nombres semánticos, valores, modos y de dónde
  salen. Su implementación vive en
  [`frontend/src/index.css`](../../../frontend/src/index.css), y los ratios de
  contraste medidos, en [`accessibility/`](../accessibility/README.md).

Escrito al arrancar el Hito 2, que es cuando las primitivas se pusieron a prueba
contra algo más que un formulario:

- [`components/`](components/README.md) — anatomía, variantes, estados,
  comportamiento y accesibilidad, con ficha propia para
  [botón](components/button.md), [campo](components/field.md),
  [aviso](components/notice.md),
  [distintivo de estado](components/status-badge.md),
  [indicador de carga](components/spinner.md) y
  [tarjeta de acceso](components/card.md).
- [`patterns/`](patterns/README.md) — cómo se componen: [formulario](patterns/form.md),
  [listado](patterns/listing.md), [jerarquía navegable](patterns/hierarchy.md),
  [feedback](patterns/feedback.md) y [navegación](patterns/navigation.md).

Escrito al arrancar el Hito 3, y **previsto de arriba abajo**, porque describe
anatomías que todavía no existen: la [subida de fichero con
progreso](components/upload-field.md), la [galería de
ficheros](components/file-gallery.md) y el [avatar](components/avatar.md). Son la
especificación de lo que el hito tiene que construir, no el resumen de nada.

Previsto, y sin contenido todavía:

- `content/`: terminología, voz, tono, etiquetas y mensajes.
- `assets/`: iconos, logotipos y recursos con sus fuentes editables.

> **Lo que estas fichas dicen y lo que no.** Describen lo que
> [`frontend/src/ui/primitives.tsx`](../../../frontend/src/ui/primitives.tsx)
> hace hoy, no lo que convendría que hiciera: lo que el hito en curso necesita y
> todavía no está va en un apartado propio de «Lo que falta», marcado como
> previsto. Dos de los cinco patrones —listado y jerarquía— **están previstos
> enteros**, porque describen pantallas que aún no existen, y las tres fichas del
> Hito 3 también. Los componentes que hacen falta y no hay —`Combobox`,
> `Skeleton`, `Toast`, `Dialog` y `Pagination`— están listados en
> [`components/`](components/README.md) sin ficha propia, para que no se escriba
> antes de tiempo. El registro de ese documento dice cuáles de los nueve
> componentes implementados tienen ficha y cuáles no.

## Ficha mínima de un componente

- Propósito y situaciones de uso.
- Anatomía, variantes y estados.
- API pública o propiedades relevantes.
- Comportamiento responsive y con contenido extremo.
- Teclado, foco, semántica y anuncios asistivos.
- Ejemplos correctos, antiusos y evidencias de prueba.
- Estado de implementación y enlace al componente real.

Las galerías o herramientas visuales futuras deben enlazarse desde aquí, sin
convertirse en la única fuente de la decisión.
