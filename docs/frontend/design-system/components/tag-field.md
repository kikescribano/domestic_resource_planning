# TagField

| Campo | Valor |
|---|---|
| Estado | **Implementado** — ficha escrita antes que el componente |
| Responsable | Equipo DRP |
| Ámbito | frontend |
| Última revisión | 2026-08-20 |

> **Esta ficha se escribió antes de que existiera el componente**, como las de
> [`danger-zone.md`](danger-zone.md), [`loan-external-page.md`](loan-external-page.md)
> y [`suppliers-page.md`](suppliers-page.md). Es la sexta vez que se hace, y las
> cinco anteriores encontraron algo cuando todavía no había código que rehacer:
> un hueco del contrato, un rótulo que chocaba en nombre accesible, dos tokens
> que no existen.
>
> **Y esta encontró dos cosas.** Que la entrada de un consumible no puede llevar
> el campo —está abajo, en «Propósito»— y que el desplegable del filtro y el
> combobox del campo comparten nombre accesible con su `listbox`, así que buscarlo
> por rótulo casa dos elementos. Lo segundo no es un defecto sino el patrón de
> ARIA 1.2, pero obliga a que las pruebas lo busquen por rol.
>
> Los valores de esta ficha son ya los del componente construido.

## Propósito y situaciones de uso

El campo con el que un asset se clasifica **por más de una cosa a la vez**.

La categoría de un asset es una y obligatoria —«Herramientas»— y responde a la
pregunta «qué clase de cosa es esto». Una etiqueta responde a otra que la
categoría no puede: «para qué la tengo». El taladro es de Herramientas, y a la
vez es *camping* y *heredado del abuelo*. Con una sola categoría hay que elegir
una de las tres y perder las otras dos.

| Qué se está pidiendo | Con qué se pide |
|---|---|
| Qué clase de cosa es, una sola respuesta | El desplegable de categoría, con `SelectField` |
| Para qué la tengo, varias respuestas y vocabulario propio del hogar | `TagField` |
| Buscar un artículo entre cientos | `Combobox`, que es de lo que este campo está hecho |

Aparece en dos sitios: el **alta de un duradero** y la **ficha de un asset**. No
aparece en la entrada de un consumible, y eso es una decisión y no un olvido: dar
entrada suma sobre una existencia que puede llevar ya sus etiquetas, así que el
campo tendría que decidir si las reemplaza o las funde, y ninguna de las dos
respuestas es evidente. Se etiqueta después, desde la ficha.

## Anatomía, variantes y estados

Tres piezas en columna, dentro de un `<div>` con la misma anatomía que
[`Field`](field.md): rótulo, control y pista.

1. **La lista de lo ya puesto**, un `<ul>` horizontal de pastillas. Cada pastilla
   lleva el nombre y un botón de quitar. No es un [`StatusBadge`](status-badge.md):
   aquel no es pulsable y este lleva un control dentro.
2. **La caja de búsqueda**, que es un `Combobox` entero y no una copia suya. Se
   reutiliza porque la accesibilidad de un combobox es cara y ya está resuelta y
   probada ahí: el foco no sale de la caja, las flechas mueven
   `aria-activedescendant`, `Escape` cierra sin elegir.
3. **La pista**, que dice lo único que no se deduce mirando: que escribir un
   nombre que no existe **lo crea**.

Y una cuarta pieza que salió al construirlo y que no estaba en esta ficha:
**`TagChip`**, la pastilla de solo lectura con la que una etiqueta se pinta en
una fila del listado y en la ficha de un asset. No es la del campo —aquella lleva
un botón dentro— y no es un [`StatusBadge`](status-badge.md): aquel dice **en qué
estado está** una cosa y tiene cinco tonos de dominio detrás, así que pintar una
etiqueta con uno de ellos diría algo que no significa nada.

Estados:

| Estado | Qué se ve |
|---|---|
| Vacío | Solo la caja, con su marcador de posición. Ninguna pastilla y ningún hueco reservado |
| Con etiquetas | Las pastillas encima de la caja, en el orden en que se pusieron |
| Escribiendo, con coincidencias | La lista del combobox con las etiquetas vivas que casan |
| Escribiendo, sin coincidencias | Una única opción, «Crear …», que da de alta la etiqueta y la pone |
| Creando | La caja sigue viva y la opción de crear no se ofrece dos veces |
| Error | El del combobox, bajo el campo, con icono y mensaje |

**La opción de crear no aparece cuando el nombre ya existe**, ni siquiera
escrito con otras mayúsculas o sin acentos: el catálogo compara normalizado, así
que ofrecer «Crear camping» con «Camping» ya en la lista sería ofrecer un `409`.

## API pública

```tsx
<TagField
  label="Etiquetas"
  value={tags}                 // Tag[]: lo que ya está puesto
  onChange={setTags}           // recibe la lista entera, no un delta
  accessToken={accessToken}
  hint="Escribe para buscar. Si no existe, se crea."
/>
```

| Propiedad | Tipo | Para qué |
|---|---|---|
| `label` | `string` | Rótulo del campo. Va al `<label>` del combobox |
| `value` | `Tag[]` | Lo puesto. Lo controla quien lo usa, como en `Field` |
| `onChange` | `(tags: Tag[]) => void` | **La lista entera**, nunca «se ha quitado esta» |
| `accessToken` | `string` | Lo necesita para listar y para crear |
| `hint` | `string?` | Bajo el campo, en `aria-describedby` |

**`onChange` devuelve la lista completa a propósito.** El contrato de la API es
igual: `tagIds` en un `PATCH` es absoluto y sustituye. Dos formas distintas de
decir lo mismo a cada lado obligarían a traducir en la pantalla, que es donde se
olvida.

## Comportamiento responsive y con contenido extremo

- Las pastillas **fluyen** (`flex-wrap`): con quince etiquetas ocupan varias
  líneas en lugar de desbordar. A 320 px caben dos por línea.
- El botón de quitar tiene su objetivo táctil de 44 px, que es lo que decide el
  alto de la pastilla. Una pastilla de 20 px con una cruz diminuta es el antiuso
  clásico de este control.
- Un nombre largo **no se recorta**: se parte. Recortar una etiqueta con puntos
  suspensivos deja dos etiquetas indistinguibles cuando comparten prefijo.
- La lista de sugerencias es la del combobox, con su alto máximo y su
  desplazamiento propio.

## Teclado, foco, semántica y anuncios asistivos

Casi todo lo hereda del `Combobox`, que implementa el patrón de ARIA 1.2. Lo
propio de este campo:

- **Las pastillas van antes que la caja en el DOM y en el tabulador.** Al revés,
  quitar la tercera etiqueta obligaría a pasar por la caja de texto y a que la
  lista de sugerencias se abriese sola de camino.
- **Cada botón de quitar dice qué quita**: «Quitar la etiqueta Camping», no
  «Quitar». Quince botones llamados «Quitar» en una fila son quince controles
  indistinguibles para quien no ve las pastillas.
- **Quitar una etiqueta devuelve el foco a la caja**, porque el botón que lo
  tenía deja de existir y el foco se caería al `<body>`.
- **La lista de pastillas es un `<ul>` con `aria-label`**, para que se anuncie
  cuántas hay: «lista, 3 elementos» es exactamente el dato que falta cuando no se
  ven.
- La región `aria-live` del combobox ya anuncia cuántos resultados hay. No se
  añade una segunda: dos regiones vivas en un campo se pisan.

## Ejemplos correctos, antiusos y evidencias de prueba

**Correcto**

```tsx
<TagField label="Etiquetas" value={tags} onChange={setTags} accessToken={token} />
```

**Antiusos**

| Antiuso | Por qué |
|---|---|
| Usarlo para la categoría | La categoría es una y viene de un catálogo cerrado con icono y color. Ver [`category-identity.md`](category-identity.md) |
| Un `<input>` de texto separado por comas | No se puede renombrar, ni deduplicar sin distinguir mayúsculas, ni autocompletar. Es exactamente la alternativa que se descartó al decidir la forma de la etiqueta |
| Pastillas sin botón de quitar, con un aspa dibujada dentro del texto | Un aspa que no es un `<button>` no la alcanza el teclado |
| Ofrecer «Crear» con un nombre que ya existe | El catálogo compara sin mayúsculas ni acentos: sería ofrecer un `409` |

**Evidencias de prueba**

- Poner y quitar una etiqueta desde la ficha de un asset, y que la fila del
  listado la enseñe.
- Escribir un nombre nuevo, crearlo desde el propio campo, y que aparezca después
  en el catálogo de etiquetas.
- Filtrar el inventario por una etiqueta y que devuelva solo lo que la lleva.
- La pasada de teclado y de axe de la pantalla «Inventario», que ya está en la
  lista de la auditoría sistemática.

## Estado de implementación y enlace al componente real

**Implementado** en el Hito 4 del cierre de huecos (2026-08-20), en
[`frontend/src/ui/catalog.tsx`](../../../../frontend/src/ui/catalog.tsx) —y no en
`primitives.tsx`, porque hace peticiones, ni en `files.tsx`, que es de ficheros—.

Lo único que la implementación añadió a esta ficha es `TagChip`, arriba. La
opción de crear se distingue por un identificador centinela que no es un `uuid`,
así que no puede chocar con el de ninguna etiqueta.

## Referencias

- [`components/README.md`](README.md): el registro y las reglas comunes.
- [`category-identity.md`](category-identity.md): la otra mitad de este hito.
- [`field.md`](field.md) y [`status-badge.md`](status-badge.md).
- [`core-model.md`](../../../common/product/core-model.md): qué es una etiqueta.

## Historial de cambios

| Fecha | Cambio | Autor |
|---|---|---|
| 2026-08-20 | El componente existe: la ficha pasa a **implementada** y gana `TagChip`, que salió al construirlo. | Equipo DRP |
| 2026-08-20 | Creación de la ficha, **antes que el componente** (cierre de huecos, Hito 4). | Equipo DRP |
