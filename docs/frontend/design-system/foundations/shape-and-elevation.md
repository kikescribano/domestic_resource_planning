# Forma, bordes y elevación

| Campo | Valor |
|---|---|
| Estado | Borrador |
| Responsable | Equipo DRP |
| Ámbito | frontend |
| Última revisión | 2026-08-10 |

## Propósito

Fijar los radios, el uso de los bordes y cómo se comunican los planos, con la
particularidad de que la respuesta no es la misma en modo claro y en modo oscuro.

## Alcance

### Incluido

- La escala de radios y qué radio lleva cada tipo de elemento.
- Los tres bordes y su obligación de contraste.
- Los cuatro niveles de elevación y por qué en oscuro funcionan de otra manera.

### Fuera de alcance

- Los valores exactos de sombra, en [`tokens/`](../tokens/README.md).
- Los ratios medidos, en [`accessibility/`](../../accessibility/README.md).

## Contenido

### Radios

La calidez está en el color y en el espacio, no en las esquinas. Los radios son
amables pero contenidos: por encima de cierto punto el redondeo deja de leerse
como acogedor y empieza a leerse como aplicación de juguete.

| Token | Valor | Dónde |
|---|---|---|
| `--radius-xs` | 4 px | Casilla de verificación, distintivo pequeño |
| `--radius-sm` | 6 px | **Fila de listado**, celda, chip de filtro |
| `--radius-md` | 8 px | Controles: botón, `input`, `select`, foto en miniatura |
| `--radius-lg` | 12 px | Tarjetas y paneles |
| `--radius-xl` | 16 px | Diálogos y hojas |
| `--radius-2xl` | 24 px | Ilustración enmarcada, tarjeta de bienvenida |
| Completo | — | Solo avatar y distintivo de estado |

La regla implícita: **cuanto más grande es el contenedor, mayor el radio**. Un
radio de 16 px en un botón de 32 px de alto lo convierte en una pastilla; el
mismo radio en un diálogo de 400 px se lee como una esquina suave.

### Bordes

Tres tokens con obligaciones distintas, detallado en
[`color.md`](color.md#la-estructura-de-la-paleta):

- `border-subtle` es decorativo: separa filas y delimita tarjetas.
- `border` delimita **controles** y está obligado a 3:1 por el criterio 1.4.11 de
  WCAG. Medido: 3,46:1 en claro y 4,33:1 en oscuro.
- `border-strong` es énfasis: control activo, seleccionado o con error.

Confundir los dos primeros es el error más fácil del sistema, porque un `input`
con borde sutil queda más bonito y deja de cumplir.

### Elevación, y por qué cambia con el modo

Cuatro niveles: `shadow-xs` (control levantado), `shadow-sm` (tarjeta),
`shadow-md` (menú, desplegable, aviso flotante), `shadow-lg` (diálogo y hoja).

Las sombras son **del tono de la paleta y nunca negras**: se construyen con un
pino translúcido (`oklch(30% 0.03 190 / …)`), porque una sombra gris neutra sobre
un papel teñido se ve ajena y ensucia toda la paleta. Cada nivel son dos capas:
una corta y densa que apoya el objeto, y una larga y difusa que lo separa del
fondo.

**En modo oscuro la sombra apenas se ve.** No hay truco que lo arregle: sobre un
fondo de `L 19 %` una sombra oscura no tiene contra qué contrastar. Así que en
oscuro los planos se comunican de otra forma:

| | Modo claro | Modo oscuro |
|---|---|---|
| Quién separa planos | La sombra, más una línea de `border-subtle` | El **escalón de claridad** de la superficie, más el borde |
| `surface` → `surface-raised` | 1,04:1 — imperceptible por sí solo | 1,09:1, y sube con cada plano |
| Papel de la sombra | Principal | Refuerzo; se conserva con más opacidad, pero no se depende de ella |

De ahí la regla que hay que recordar al escribir un componente: **una tarjeta no
puede identificarse solo por su relleno**. Lleva siempre borde, y la sombra es lo
que la levanta cuando el modo lo permite.

### Y en un listado, nada de esto

Dentro de una fila de listado no hay sombra ni radio mayor que `sm`. Las filas se
separan con una línea de `border-subtle` y punto. La justificación completa está
en [`density.md`](density.md).

## Decisiones abiertas

- Ninguna.

## Referencias

- [`tokens/`](../tokens/README.md)
- [`color.md`](color.md), [`density.md`](density.md)
- [`accessibility/`](../../accessibility/README.md)

## Historial de cambios

| Fecha | Cambio | Autor |
|---|---|---|
| 2026-08-10 | Creación del documento con radios y elevación del Hito 1. | Equipo DRP |
