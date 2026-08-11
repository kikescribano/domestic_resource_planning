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

Previsto, y sin contenido todavía:

- `components/`: anatomía, variantes, estados, comportamiento y accesibilidad.
- `patterns/`: formularios, tablas, navegación, feedback y composición.
- `content/`: terminología, voz, tono, etiquetas y mensajes.
- `assets/`: iconos, logotipos y recursos con sus fuentes editables.

> Los primeros componentes reales —botón, campo, aviso, distintivo de estado y el
> shell responsive— ya existen en
> [`frontend/src/ui/primitives.tsx`](../../../frontend/src/ui/primitives.tsx), pero
> todavía no tienen ficha aquí: se escriben cuando el Hito 2 los ponga a prueba
> contra listados largos, que es donde se sabrá si su anatomía aguanta.

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
