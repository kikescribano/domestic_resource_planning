# Color

| Campo | Valor |
|---|---|
| Estado | Borrador |
| Responsable | Equipo DRP |
| Ámbito | frontend |
| Última revisión | 2026-08-10 |

## Propósito

Explicar de qué está hecha la paleta de DRP, qué significa cada color y por qué
sus valores son esos y no otros. Los nombres y valores exactos, en los dos modos,
están en [`tokens/`](../tokens/README.md).

## Alcance

### Incluido

- La estructura de la paleta: superficies, tinta, bordes, acento, feedback y
  estados del dominio.
- Por qué se escribe en `oklch()` y por qué se mantiene dentro del gamut sRGB.
- Cómo se deriva el modo oscuro.

### Fuera de alcance

- La tabla de ratios de contraste medidos, en
  [`accessibility/`](../../accessibility/README.md).
- Qué color usa cada componente, que se documentará con cada componente.

## Contenido

### Por qué `oklch()`

Toda la paleta se escribe como `oklch(L C H)` porque su claridad es **perceptual**:
dos tokens con la misma `L` se ven igual de claros aunque cambie el tono. Eso es
lo que permite tres cosas que en hexadecimal cuestan mucho:

1. Derivar el modo oscuro **moviendo la claridad** y ajustando el croma, sin
   volver a elegir colores.
2. Fijar la regla de que todo texto de estado vive en la banda `L 45–52 %` en
   claro y `L 68–80 %` en oscuro, y que por tanto los cinco estados pesan
   visualmente lo mismo. Ninguno grita más que otro **salvo el vencido**, que
   lleva el croma más alto (0,16) a propósito.
3. Comprobar de un vistazo si un color nuevo encaja, sin abrir una herramienta.

**Todos los tokens están dentro del gamut sRGB.** Es una restricción
autoimpuesta y tiene un motivo concreto: un color fuera de gamut se ve más
saturado en una pantalla P3 que en una sRGB, y entonces el ratio de contraste
medido deja de ser el ratio que ve el usuario. Al mantener la paleta dentro de
sRGB, el número auditado es el número real en cualquier pantalla. Dos tonos
suaves —el del distintivo de vencido y el de sin existencias— se bajaron de croma
justamente por esto.

### La estructura de la paleta

**Superficies.** Cuatro planos, de atrás hacia delante: `surface-sunken` (huecos,
cabecera de tabla), `surface` (la página), `surface-raised` (tarjetas y
diálogos) y `surface-hover` (la fila bajo el puntero). El fondo no es blanco sino
un papel cálido, `oklch(98.4% 0.006 85)`: es el único gesto de la dirección que
está presente en absolutamente todas las pantallas y no cuesta ni un píxel de
densidad. Además hay `scrim`, la capa translúcida que atenúa el fondo de un
diálogo.

Una consecuencia que conviene tener presente: en modo claro, `surface` y
`surface-raised` se diferencian en 1,04:1. **La elevación no se comunica con el
relleno**, sino con la sombra y el borde. En modo oscuro pasa lo contrario, la
sombra apenas se ve y quien separa planos es el escalón de claridad.

**Tinta.** `ink` para el contenido, `ink-muted` para lo secundario, `ink-subtle`
para metadatos y placeholders, `ink-disabled` para lo desactivado e `ink-inverse`
para escribir sobre un relleno sólido. Los cuatro primeros están por encima de
4,5:1 sobre todas las superficies donde se usan, `ink-subtle` incluido: el
placeholder es texto y la norma no lo exime. El único exento es `ink-disabled`,
y aun así se le exige 3:1, para que un formulario a medio rellenar siga siendo
legible.

**Bordes.** Tres, y **no son intercambiables**:

| Token | Para qué | Obligación |
|---|---|---|
| `border-subtle` | Separar filas, delimitar una tarjeta | Decorativo, sin mínimo |
| `border` | Delimitar un control: `input`, `select`, casilla, botón secundario | **≥ 3:1** (WCAG 1.4.11) |
| `border-strong` | Énfasis, control activo o seleccionado | ≥ 3:1 con margen |

Usar `border-subtle` en un `input` es un incumplimiento, no una preferencia
estética. Es el error más fácil de cometer de todo el sistema de color, porque el
resultado es bonito.

**Acento.** Uno solo: terracota. `accent` es el relleno de la acción principal,
`accent-hover` su estado bajo el puntero, `accent-ink` la versión oscurecida para
usarlo como **texto** —un enlace no puede ir del color de un botón, porque el
botón no necesita 4,5:1 contra el papel y el texto sí— y `accent-soft` el fondo
tenue de una fila seleccionada o de un distintivo.

**Feedback del sistema.** `success`, `warning`, `danger` e `info`, cada uno con su
variante `-soft` para el fondo de un aviso. Describen lo que le pasa a una
**petición**.

### Los cinco estados del dominio

Describen lo que le pasa a una **cosa del hogar**, que no es lo mismo. Tienen
tokens propios aunque tres de ellos compartan hoy valor con su equivalente de
feedback:

| Estado | Color | Por qué |
|---|---|---|
| `AVAILABLE` | Verde salvia | Está en casa y se puede usar |
| `LENT` | Ámbar miel | Está fuera, pero eso es normal: **no es una advertencia** |
| `OVERDUE` | Rojo teja | El único que sube el croma a 0,16, porque es el único que pide actuar |
| `DECOMMISSIONED` | Gris cálido neutro | Ya no cuenta; se ve, pero no compite |
| Sin existencias | Azul pizarra | Un consumible a cero |

La separación de nombres no es burocracia: **un préstamo vencido no es un error
del sistema**, y el día que el rojo de error cambie de tono el inventario no debe
repintarse detrás. Hoy los valores coinciden; los nombres permiten que dejen de
hacerlo sin tocar ningún componente.

**Por qué el azul.** Es el único color frío de un sistema deliberadamente cálido,
y está donde está por accesibilidad antes que por estética: una deuteranopia
confunde con facilidad el verde, el ámbar y el rojo entre sí, pero no confunde
ninguno de los tres con un azul. El estado que más probablemente hay que
distinguir de un vistazo en una despensa —«esto se ha acabado»— es el que se
apoya en el eje de color que sobrevive. El gris del dado de baja aporta el otro
extremo seguro.

Aun así, **el color nunca es el único portador**: cada estado lleva además su
etiqueta y su icono fijo (ver [`iconography.md`](iconography.md)).

### Cómo se deriva el modo oscuro

No es una inversión. La regla es:

- Las superficies suben de `L 15,5–27 %` manteniendo el tono cálido (H 70). El
  fondo oscuro de DRP es pardo, no azulado ni negro puro; el negro puro sobre una
  pantalla OLED produce halo alrededor del texto claro.
- La tinta baja de contraste absoluto respecto de una inversión ingenua: `ink` es
  `L 95 %`, no blanco, porque blanco puro sobre fondo muy oscuro produce
  deslumbramiento y falso desenfoque.
- Los colores con croma **suben de claridad y bajan un poco de croma**: un color
  saturado y oscuro sobre fondo oscuro es ilegible.
- Los fondos `-soft` no se aclaran, se oscurecen hasta `L 28–30 %` conservando el
  tono, para que un distintivo siga siendo reconocible por su color.

Los dos modos están auditados por separado y con los mismos pares. No se da por
supuesto que si el claro cumple, el oscuro también.

## Decisiones abiertas

- **Retirar la paleta por defecto de Tailwind** (`--color-*: initial`) para que
  `bg-blue-500` deje de existir y todo componente esté obligado a usar un token.
  Es la única forma de que la regla se verifique sola, pero hoy rompería en
  silencio cualquier componente en construcción, así que se propone activarlo al
  cerrar el Hito 1, cuando los componentes ya existan y se pueda comprobar el
  cambio de un vistazo.

## Referencias

- [`tokens/`](../tokens/README.md): nombres, valores y modos.
- [`accessibility/`](../../accessibility/README.md): la auditoría de contraste.
- [`look-and-feel.md`](../../product-design/look-and-feel.md): la dirección que
  esta paleta ejecuta.
- [`core-model.md`](../../../common/product/core-model.md) y
  [`loans.md`](../../../common/product/loans.md): de dónde salen los cinco estados.

## Historial de cambios

| Fecha | Cambio | Autor |
|---|---|---|
| 2026-08-10 | Creación del documento con la paleta del Hito 1. | Equipo DRP |
