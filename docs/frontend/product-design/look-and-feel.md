# Look and feel

| Campo | Valor |
|---|---|
| Estado | Borrador |
| Responsable | Por decidir |
| Ámbito | Interfaz web responsive |
| Última revisión | 2026-08-06 |

## Propósito

Definir la dirección visual y de interacción de DRP de forma comprobable. Este
documento expresa intención; los tokens y componentes implementables viven en el
[`design system`](../design-system/README.md).

## Personalidad de la interfaz

Por decidir. Debe concretarse mediante entre tres y cinco atributos acompañados
de comportamientos observables y antiatributos. Ejemplo de formato:

| Atributo | Se manifiesta en | Evitar |
|---|---|---|
| Por decidir | Evidencia visual o de interacción | Antipatrón asociado |

## Principios visuales

Por decidir. Cada principio deberá justificar decisiones sobre jerarquía,
densidad, color, espacio y movimiento.

## Dirección visual

| Dimensión | Decisión vigente | Evidencia o referencia |
|---|---|---|
| Color | Por decidir | Pendiente |
| Tipografía | Por decidir | Pendiente |
| Densidad de información | Por decidir | Pendiente |
| Espaciado y ritmo | Por decidir | Pendiente |
| Formas, bordes y elevación | Por decidir | Pendiente |
| Iconografía e ilustración | Por decidir | Pendiente |
| Fotografía y recursos | Por decidir | Pendiente |
| Movimiento y transiciones | Por decidir | Pendiente |

## Estados de experiencia

La dirección elegida deberá cubrir, como mínimo:

- Estado inicial y onboarding.
- Carga, carga progresiva y actualización.
- Vacío con una siguiente acción clara.
- Éxito, confirmación y feedback no intrusivo.
- Error recuperable y error bloqueante.
- Modo sin conexión o conectividad degradada, si se acepta como requisito.
- Operaciones destructivas y su confirmación.

## Comportamiento responsive

La interfaz debe conservar jerarquía, legibilidad y capacidad de acción desde un
dispositivo equivalente a iPhone X hasta una pantalla ultrawide. Los breakpoints
se definirán a partir del contenido y del layout, no de modelos concretos.

| Escenario | Qué validar |
|---|---|
| Móvil vertical mínimo | Navegación, formularios, acciones y ausencia de scroll horizontal accidental. |
| Móvil horizontal | Reflujo, teclado, overlays y contenido crítico visible. |
| Tablet o ventana intermedia | Transición de navegación y aprovechamiento del espacio. |
| Escritorio | Jerarquía, densidad y eficiencia con teclado y puntero. |
| Ultrawide | Límites de lectura, composición y uso intencional del espacio adicional. |

## Accesibilidad visual y motriz

- El color no será el único medio para transmitir significado.
- El foco y los estados interactivos deberán ser visibles.
- Contraste, tamaños táctiles, zoom, reducción de movimiento y navegación por
  teclado se concretarán en la documentación de
  [`accesibilidad`](../accessibility/README.md).

## Validación

Antes de pasar a `Vigente`, este documento debe incluir referencias visuales,
prototipos responsive, revisión de accesibilidad y evidencia de validación sobre
los principales flujos de usuario.

## Historial

| Fecha | Cambio |
|---|---|
| 2026-08-06 | Se crea la plantilla inicial; no hay dirección visual aprobada. |
