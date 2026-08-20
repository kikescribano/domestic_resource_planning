# CategoryMarker e IconColorPicker

| Campo | Valor |
|---|---|
| Estado | **Previsto** — ficha escrita antes que los componentes |
| Responsable | Equipo DRP |
| Ámbito | frontend |
| Última revisión | 2026-08-20 |

> **Esta ficha se escribió antes de que existieran los componentes**, como las de
> [`danger-zone.md`](danger-zone.md) y [`tag-field.md`](tag-field.md).
>
> **Y son dos piezas en una ficha porque son las dos mitades de una sola cosa**:
> cómo se elige la identidad visual de una categoría y cómo se pinta. El selector
> no dibuja opciones propias — dibuja marcadores, que es exactamente lo que se
> está eligiendo. Separarlas en dos fichas obligaría a describir el marcador dos
> veces y dejaría la más importante de las dos reglas —que el color nunca va
> solo— escrita en solo una.

## Propósito y situaciones de uso

**`CategoryMarker`** es cómo se reconoce una categoría de un vistazo: un icono
dentro de un cuadradito de su color.

**`IconColorPicker`** es cómo el hogar elige ese icono y ese color, dentro de un
juego cerrado de dieciséis iconos y seis colores
([ADR-015](../../../common/architecture/decisions/ADR-015-user-chosen-category-identity.md)).

Aparecen en:

| Dónde | Cuál | Para qué |
|---|---|---|
| Catálogo → Categorías, formulario de alta y de edición | `IconColorPicker` | Elegir |
| Catálogo → Categorías, cada fila | `CategoryMarker` | Ver lo elegido |
| Inventario, cada fila | `CategoryMarker` | Distinguir de un vistazo en una lista larga |
| Ficha de un asset | `CategoryMarker` | Junto al nombre de la categoría |
| Ficha de un asset **sin foto** | `CategoryMarker`, en grande | El hueco de la foto que falta, que [`iconography.md`](../foundations/iconography.md) prometía desde el primer día |

**Dónde no aparece, y por qué se dice.** El plan del hito pedía la categoría con
su icono «en la navegación móvil, que es donde un icono se gana el sitio». No
entra ahí: **en la navegación no hay ninguna categoría**. Sus cinco paradas son
pantallas —Hogar, Inventario, Sitios, Préstamos y Más— y darles icono es
iconografía de navegación, que es otro trabajo y toca un ancho de 44 px ya medido
al límite a 320 px. El sitio donde el icono de una categoría sí se gana el
espacio en un móvil es el hueco de la foto que falta, que es la fila de arriba.

## Anatomía, variantes y estados

### `CategoryMarker`

Un `<span>` cuadrado con el icono dentro:

- Fondo `bg-category-<color>-soft`, icono `text-category-<color>`.
- Sin color elegido: fondo `surface-sunken` e icono `ink-muted`. **No se inventa
  un color por defecto**, porque «nadie lo eligió» no es ninguno de los seis.
- Sin icono elegido: la caja, que es el genérico del juego.
- Esquinas de `radius-md`, como el recorte de una foto — es lo que hace que el
  marcador y la miniatura ocupen el mismo hueco.

Tres tamaños, y cada uno tiene su sitio:

| Tamaño | Medida | Dónde |
|---|---|---|
| `sm` | 28 px, icono de 20 | Fila de listado y opción del selector |
| `md` | 40 px, icono de 24 | Ficha, junto al nombre |
| `lg` | 96 px, icono de 40 | El hueco de una foto que falta |

**Es decorativo y va con `aria-hidden`.** El nombre de la categoría está siempre
al lado, en texto, así que anunciarlo lo diría dos veces. La única excepción es
el tamaño `lg` de la foto que falta, donde no hay texto que lo acompañe: ahí
lleva `role="img"` y su nombre accesible, «Sin foto. Categoría: Herramientas».

### `IconColorPicker`

Dos rejillas de botones dentro de un `<fieldset>` cada una:

1. **Icono**: dieciséis botones, cada uno con su icono y su nombre accesible en
   castellano —«Herramienta», «Sofá», «Planta»—.
2. **Color**: seis botones, cada uno pintando un `CategoryMarker` con el icono ya
   elegido. Elegir color enseña **el resultado**, no una muestra de pintura.

Estados de un botón: sin elegir, elegido y bajo el puntero. El elegido lleva
`aria-pressed="true"` **y** un anillo de `border-strong` de 2 px: el color de
relleno no puede ser lo único que diga cuál está marcado, porque el relleno es
justo lo que se está eligiendo.

Los dos grupos admiten **quedarse sin elegir**, con un botón «Ninguno» al
principio de cada rejilla. Una categoría sin icono ni color es el caso normal de
un hogar que acaba de empezar.

## API pública

```tsx
<CategoryMarker icon={category.icon} color={category.color} size="sm" />

<IconColorPicker
  icon={draft.icon}
  color={draft.color}
  onChange={(identity) => setDraft({ ...draft, ...identity })}
/>
```

| Propiedad | Tipo | Para qué |
|---|---|---|
| `icon` | `CategoryIcon \| null` | Uno de los dieciséis, o nada |
| `color` | `CategoryColor \| null` | Uno de los seis, o nada |
| `size` | `'sm' \| 'md' \| 'lg'` | Solo en el marcador; `sm` por omisión |
| `label` | `string?` | Solo en el marcador de tamaño `lg`: el nombre de la categoría, para su nombre accesible |
| `onChange` | `(v: { icon, color }) => void` | Solo en el selector. Devuelve **los dos**, como `TagField` devuelve la lista entera |

**Las clases de color se escriben enteras en un `Record` y no se componen.**
`bg-category-${color}-soft` no lo ve Tailwind, así que no genera ninguna regla y
el marcador sale transparente **sin que falle nada** — que es exactamente el
defecto que el Hito 3 destapó en la pantalla de Préstamos.

## Comportamiento responsive y con contenido extremo

- El marcador **no encoge**: `shrink-0`. En una fila estrecha lo que se parte es
  el nombre, no el distintivo.
- Las dos rejillas del selector son `flex-wrap`: cuatro iconos por línea a 320 px
  y ocho desde `sm`.
- Cada botón del selector tiene sus 44 px, que a 320 px es lo que fija cuántos
  caben. Con dieciséis iconos son cuatro líneas de cuatro, que cabe sin
  desbordamiento horizontal.
- Con el texto al 200 % el marcador **no crece**, porque está en `px` y no en
  `em`: un icono que se dobla al ampliar el texto se come la fila entera.

## Teclado, foco, semántica y anuncios asistivos

- Cada rejilla es un `<fieldset>` con su `<legend>` —«Icono», «Color»—, que es lo
  que hace que un lector de pantalla diga a qué grupo pertenece el botón que
  acaba de recibir el foco.
- Los botones son `<button type="button">` con `aria-pressed`, no radios. Se
  eligió así porque **se pueden desmarcar**: un grupo de radios sin ninguno
  marcado es un estado que el control nativo no representa bien, y aquí es el
  estado inicial de toda categoría.
- **Cada botón de color dice el nombre del color en castellano** —«Musgo»,
  «Índigo»—, nunca solo «color 4». Es lo único que tiene quien no lo ve.
- Ningún botón declara foco propio: lo pone la capa base de `index.css`.
- El marcador es `aria-hidden` salvo en `lg`, donde es `role="img"` con nombre.

## Ejemplos correctos, antiusos y evidencias de prueba

**Correcto**

```tsx
<li>
  <CategoryMarker icon={category.icon} color={category.color} />
  <span>{category.name}</span>
</li>
```

**Antiusos**

| Antiuso | Por qué |
|---|---|
| El marcador **sin** el nombre al lado | El color y el icono acompañan; el nombre manda. Sin él es color como único portador y se incumple 1.4.1 |
| Componer la clase con una plantilla, `bg-category-${color}-soft` | Tailwind no la genera y no falla nadie. Es el defecto que destapó la pantalla de Préstamos |
| Un selector libre de color | No está en ningún token, así que no lo mide `check-contrast.py`. Descartado en la [ADR-015](../../../common/architecture/decisions/ADR-015-user-chosen-category-identity.md) |
| Pintar el estado del asset con el color de su categoría | Dos distintivos en la misma fila es antiuso declarado en [`status-badge.md`](status-badge.md) |
| Usar el marcador para una etiqueta | Una etiqueta no tiene color ni icono, y ponérselos multiplicaría por veinte lo que hay que medir |

**Evidencias de prueba**

- `python scripts/check-contrast.py`: los doce pares de los seis colores, en los
  dos modos. Es lo que hace que el color elegido esté certificado.
- La pantalla «Catálogo» entra en la lista de la auditoría sistemática del
  recorrido vertical, **con una categoría de color puesto antes**: auditarla en
  gris no habría mirado ninguno de los seis.
- Elegir icono y color con el teclado y comprobar el anillo de foco en cada
  parada, que la pasada de teclado ya hace por estar la pantalla en la lista.

## Estado de implementación y enlace al componente real

**Previstos.** Los construye el Hito 4 del cierre de huecos, en
[`frontend/src/ui/catalog.tsx`](../../../../frontend/src/ui/catalog.tsx), junto a
[`TagField`](tag-field.md).

**Y ahí hay una decisión de reparto que el registro tenía a medias.** El criterio
del Hito 3 era «primitivas puras en `primitives.tsx`, piezas con estado y
peticiones en `files.tsx`». Por ese criterio el marcador y el selector irían a
`primitives.tsx` —no piden nada al servidor— y `TagField` a un tercer sitio. Se
parte **por dominio** y no por ese eje, con dos motivos: las tres piezas son la
clasificación de lo que hay en casa y se leen juntas, y sobre todo el juego de
iconos son **dieciséis dibujos**, que es una tabla de datos cerrada más que un
componente, y metida en `primitives.tsx` acabaría con lo que el registro llama
«un fichero que se lee de una sentada».

## Referencias

- [`ADR-015`](../../../common/architecture/decisions/ADR-015-user-chosen-category-identity.md):
  por qué el juego es cerrado.
- [`iconography.md`](../foundations/iconography.md): el juego de iconos y la
  promesa del marcador de una foto que falta.
- [`color.md`](../foundations/color.md) y [`tokens/`](../tokens/README.md): los
  seis colores y sus valores.
- [`core-model.md`](../../../common/product/core-model.md): qué es una categoría.

## Historial de cambios

| Fecha | Cambio | Autor |
|---|---|---|
| 2026-08-20 | Creación de la ficha, **antes que los componentes** (cierre de huecos, Hito 4). | Equipo DRP |
