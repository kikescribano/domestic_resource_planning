# Densidad

| Campo | Valor |
|---|---|
| Estado | Borrador |
| Responsable | Equipo DRP |
| Ámbito | frontend |
| Última revisión | 2026-08-10 |

## Propósito

Resolver la tensión que trae consigo una dirección visual cálida: **en un
inventario de cientos de assets, la calidez estorba**. Este documento dice dónde
se contiene y cómo se comprueba que se está conteniendo.

## Alcance

### Incluido

- Las dos densidades y qué decide cuál se aplica.
- El presupuesto de calidez: dónde se gasta la personalidad en una vista de
  listado y qué queda prohibido.
- Cómo se implementa con tokens.

### Fuera de alcance

- La anatomía de la tabla y de la tarjeta de listado, que irán en `patterns/`.
- La rejilla de espaciado en sí, en [`space.md`](space.md).

## Contenido

### El problema, dicho sin rodeos

Serif, sombras, esquinas generosas e ilustración funcionan en una pantalla de
alta, en un estado vacío o en el onboarding, donde hay un elemento y sitio de
sobra. En un listado esos mismos recursos se multiplican por el número de filas:
trescientas sombras son una textura, trescientos titulares en serif son ruido, y
trescientas esquinas de 12 px son una manta de burbujas. Peor todavía: cada
adorno roba altura, y la altura de fila es la variable que decide cuánta
información cabe en una pantalla sin scroll.

No se resuelve solo, y no se resuelve pidiendo mesura. Se resuelve con una regla
que se pueda comprobar leyendo el código de un componente.

### Dos densidades, decididas por el dispositivo de entrada

**La densidad no es una preferencia estética ni una opción de usuario en la
Fase 1: es una consecuencia de con qué se está tocando la pantalla.**

| | Cómoda | Compacta |
|---|---|---|
| Cuándo | Con el dedo: móvil y tableta vertical, por debajo de `md` | Con puntero y teclado: desde `md` |
| Forma del listado | Tarjetas apiladas | Tabla con cabecera fija |
| Alto de fila | ≥ 44 px | ≈ 36 px |
| Relleno vertical | `--spacing-row` (12 px) | `--spacing-row-compact` (6 px) |
| Cuerpo de texto | `--text-body` (16 px) | `--text-body-sm` (14 px) |
| Cifras | Alineadas a la derecha | Alineadas a la derecha, `tabular-nums` |

El razonamiento es el que cierra la discusión: **con el dedo no cabe ser denso**,
porque los 44 px de objetivo mínimo imponen holgura de todas formas; **con
puntero, esa misma holgura se convierte en scroll**, que es el coste real. Así
que la interfaz nunca está siendo densa y cálida a la vez en el mismo sitio,
porque nunca está siendo densa en el sitio donde la calidez se ve.

De ahí que no haga falta un conmutador de densidad. Si algún día lo pide alguien
con un catálogo enorme, lo que cambia es el valor de dos tokens de espaciado y
uno de tipografía, no la estructura.

### El presupuesto de calidez

En una vista de listado, la personalidad se gasta en **tres sitios y en ninguno
más**:

1. **El papel de fondo.** El listado se dibuja sobre la misma base cálida que el
   resto de la aplicación. Es lo que impide que la tabla parezca una hoja de
   cálculo y no cuesta ni un píxel de altura.
2. **La acción principal.** El único relleno de terracota de la pantalla.
3. **El estado.** El único color dentro de la fila, con su etiqueta y su icono.

Y esta es la lista de lo que queda **prohibido dentro de una fila**, que es la
parte que se puede revisar en un diff:

| Prohibido | Por qué |
|---|---|
| `font-display` (serif) | Multiplicada por N filas deja de ser carácter |
| Cualquier `shadow-*` | La separación entre filas es una línea de `border-subtle` |
| Radio mayor que `--radius-sm` | Las filas no son tarjetas |
| Ilustración o imagen decorativa | La única imagen admitida es la foto real del asset, a 1:1 y pequeña |
| Un segundo peso tipográfico | La jerarquía dentro de la fila se hace con posición y color de tinta |
| Fondo de color | Salvo `surface-hover` (puntero) y `accent-soft` (selección) |
| Animación | Salvo la respuesta al puntero, de 80 ms |

La ilustración tiene además una regla propia y global: **como máximo una por
pantalla, y ninguna en una vista con filas** (ver
[`iconography.md`](iconography.md)).

### Cómo se comprueba

Tres cosas observables, sin necesidad de juicio estético:

1. Un componente de fila no contiene ninguna clase de la tabla de prohibidos.
2. A 1280 px, un listado muestra al menos 18 filas sin scroll.
3. A 375 px, ningún elemento pulsable de la tarjeta baja de 44 px.

## Decisiones abiertas

- **Conmutador de densidad por usuario.** No entra en la Fase 1 porque no hay
  todavía ningún hogar con un catálogo lo bastante grande como para saber si hace
  falta. La implementación está preparada —son dos tokens de espaciado y uno de
  tipografía— y el sitio natural sería un atributo `data-density` en la raíz, con
  la misma mecánica que `data-theme`.

## Referencias

- [`look-and-feel.md`](../../product-design/look-and-feel.md), sección «Cómo se
  contiene la calidez en los listados densos».
- [`space.md`](space.md), [`typography.md`](typography.md),
  [`shape-and-elevation.md`](shape-and-elevation.md)

## Historial de cambios

| Fecha | Cambio | Autor |
|---|---|---|
| 2026-08-10 | Creación del documento con las dos densidades y el presupuesto de calidez. | Equipo DRP |
