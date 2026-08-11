# Foundations

| Campo | Valor |
|---|---|
| Estado | Borrador |
| Responsable | Equipo DRP |
| Ámbito | frontend |
| Última revisión | 2026-08-10 |

## Propósito

Recoger las decisiones visuales de las que cuelga todo lo demás: color,
tipografía, espacio, densidad, forma y movimiento. Un componente no decide
ninguna de estas cosas; las consume.

Estos documentos explican **por qué** cada valor es el que es. Los nombres y
valores en sí están en [`tokens/`](../tokens/README.md), y la implementación
única en [`frontend/src/index.css`](../../../../frontend/src/index.css).

## Alcance

### Incluido

- [`color.md`](color.md): la paleta, los estados del dominio y el contraste.
- [`typography.md`](typography.md): las dos familias, la escala y dónde va cada una.
- [`space.md`](space.md): la rejilla, las medidas con nombre y la composición.
- [`density.md`](density.md): las dos densidades y qué decide cuál se usa.
- [`shape-and-elevation.md`](shape-and-elevation.md): radios, bordes y elevación.
- [`iconography.md`](iconography.md): el juego de iconos y la regla de ilustración.
- [`motion.md`](motion.md): duraciones, curvas y reducción de movimiento.

### Fuera de alcance

- La intención de producto que justifica estas decisiones, que vive en
  [`look-and-feel.md`](../../product-design/look-and-feel.md).
- Anatomía y comportamiento de componentes concretos, que irán en
  `components/` a medida que se construyan.
- Los criterios de verificación de accesibilidad, en
  [`accessibility/`](../../accessibility/README.md).

## Decisiones abiertas

- Ninguna en las ocho dimensiones. Lo que queda por decidir son consecuencias de
  implementación, y está anotado en cada documento.

## Referencias

- [`look-and-feel.md`](../../product-design/look-and-feel.md)
- [`ADR-006`](../../../common/architecture/decisions/ADR-006-frontend-stack-and-design-system.md)

## Historial de cambios

| Fecha | Cambio | Autor |
|---|---|---|
| 2026-08-10 | Creación del directorio con las siete fundaciones del Hito 1. | Equipo DRP |
