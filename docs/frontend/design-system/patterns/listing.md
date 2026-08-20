# Listado

| Campo | Valor |
|---|---|
| Estado | Borrador |
| Responsable | Equipo DRP |
| Ámbito | frontend |
| Última revisión | 2026-08-12 |

## Propósito

Fijar cómo se presenta una colección —artículos, assets, ubicaciones,
categorías— y cómo se llega desde una de sus filas al elemento completo. Es el
patrón que la dirección visual escribió pensando en él: **la calidez de DRP
estorba en una lista de trescientas filas**, y
[`density.md`](../foundations/density.md) existe para contenerla.

## Alcance

### Incluido

- Las dos formas del listado y qué decide cuál se usa.
- La paginación, que es uniforme en las doce colecciones del contrato.
- Los filtros y los tres estados vacíos.
- La ficha del elemento seleccionado, y cómo se distingue en ella lo propio de lo
  heredado del artículo.

### Fuera de alcance

- Los valores de densidad, altura de fila y presupuesto de calidez, en
  [`density.md`](../foundations/density.md).
- El árbol de ubicaciones, que es una colección **jerárquica** y tiene su propio
  patrón: [`hierarchy.md`](hierarchy.md).
- Los formularios que se abren desde una fila, en [`form.md`](form.md).

## Estado

**Previsto casi por completo.** No existe ningún listado paginado, ninguna tabla,
ningún filtro, ninguna ficha de detalle y ningún estado vacío. Este documento
describe pantallas que el Hito 2 tiene que construir, y decirlo es lo que hace
que sirva.

Lo único que existe hoy son **dos listas cortas** en `UsersPage`, dentro de
[`household.tsx`](../../../../frontend/src/routes/household.tsx): las personas
del hogar y las invitaciones pendientes. Son un `<ul>` de `<li>` con borde, fondo
elevado y `rounded-lg`, y **no siguen este patrón**: no paginan, no filtran, no
pasan a tabla en ningún ancho y llevan por fila un radio de tarjeta que
`density.md` prohíbe dentro de una fila. A esa escala —los miembros de una casa—
no hace daño y no hay que corregirlo con urgencia; lo que no se puede es tomarlo
como ejemplo.

## Contenido

### Dos formas, decididas por el dispositivo de entrada

Es la decisión de [`density.md`](../foundations/density.md) y aquí solo se
aplica: **tarjetas apiladas por debajo de `md`, tabla compacta desde `md`**. No
es una preferencia ni un conmutador, es la consecuencia de que con el dedo los
44 px de objetivo mínimo imponen holgura y con puntero esa holgura se convierte
en scroll.

Lo que sí decide este patrón es que **son el mismo listado y no dos**, igual que
el shell tiene un solo `<nav>` recolocado con CSS. Pintar una tabla con
`hidden md:table` y unas tarjetas con `md:hidden` duplica cada fila en el DOM: un
lector de pantalla recorre la colección dos veces y una búsqueda del navegador
encuentra cada nombre dos veces. Que una esté oculta lo salva en la práctica y
basta un cambio de clase para que deje de estarlo.

Dentro de la fila mandan las prohibiciones de `density.md` —sin serif, sin
sombra, sin radio mayor que `sm`, sin segundo peso, sin ilustración— y el
presupuesto de calidez: **el único color de la fila es el estado**, con su
[`StatusBadge`](../components/status-badge.md).

**Con una excepción desde el cierre de huecos, y está acotada**: el marcador de
la categoría, que abre la fila de un asset con el icono y el color que el hogar
eligió ([ADR-015](../../../common/architecture/decisions/ADR-015-user-chosen-category-identity.md)).
No rompe el presupuesto porque no compite con el estado —el marcador va al
principio y el distintivo al final, uno es un cuadradito y el otro una pastilla—
y porque **no dice nada por sí solo**: el nombre de la categoría sigue estando al
lado en tinta. Una etiqueta puesta, en cambio, sí va sin color: una pastilla de
`surface-sunken` con su texto, y nunca un `StatusBadge`, que tiene cinco tonos de
dominio detrás y diría algo que no significa nada.

### Qué lleva una fila

| Colección | Lo que identifica | Lo que se ve al lado |
|---|---|---|
| Assets | El marcador de su categoría **y** el nombre | Categoría, conservación, etiquetas, estado, y cantidad con unidad si es `CONSUMABLE` |
| Artículos | Nombre | Categoría, unidad, y si está retirado |
| Ubicaciones | Nombre | Tipo, y qué contiene |
| Categorías | El marcador **y** el nombre | Editar y retirar |
| Etiquetas | Nombre | Si está retirada |

Dos reglas que vienen de arriba: **la fila entera es pulsable**, no un icono de
24 px en su extremo; y **el nombre largo se trunca en dos líneas dejando el
estado siempre visible**, que es lo que
[`look-and-feel.md`](../../product-design/look-and-feel.md) exige a 375 px.

### Paginación

El contrato pagina **once colecciones** con la misma forma, y esa uniformidad es
deliberada: `page` (desde 0, por defecto 0), `size` (por defecto 50, máximo 200)
y `total`. Están en `PageMeta` de
[`openapi.yaml`](../../../../openapi.yaml), y el tipo `Page<T>` ya existe en
[`client.ts`](../../../../frontend/src/api/client.ts).

Lo que **no** existe es nada que lo pinte. Y hay una consecuencia que ya está
ocurriendo: `listUsers` se llama sin `page` ni `size`, y la pantalla renderiza
`items` ignorando `page`, `size` y `total`. Con el tamaño por defecto, **un hogar
de más de cincuenta miembros vería la lista cortada sin ningún indicio**. Hoy es
teórico; con los artículos y los assets del Hito 2 deja de serlo.

Decisiones que hay que tomar al construirlo, y que este documento no cierra:
paginación clásica frente a scroll infinito —el primero es más fácil de recorrer
con teclado y de compartir por URL—, y si el número de página viaja en la URL.

### Filtros

Cada colección trae los suyos en el contrato, y no son pocos: `GET /assets`
admite **diez** —ubicación, asset padre, propietario, estado, tipo, artículo,
categoría, conservación, etiqueta y «sin propietario»—, y `GET /articles` cuatro,
entre ellos el `q` de búsqueda por nombre normalizado que alimenta el
autocompletado. `GET /tags` trae el suyo por el mismo motivo.

**El de etiqueta va en un desplegable y no en pastillas**, al contrario que el de
naturaleza: el vocabulario de un hogar crece sin techo, y veinte pastillas en la
cabecera dejan el listado debajo del pliegue.

Tres reglas:

1. **El filtro aplicado se ve.** Un listado filtrado que parece un listado
   completo hace pensar que faltan cosas.
2. **El vacío por filtro no es el vacío inicial.** Lo separa
   `look-and-feel.md` porque la siguiente acción es distinta: limpiar el filtro,
   no crear nada.
3. **Filtrar no vacía lo que ya se está leyendo.** Es la segunda de las tres
   esperas: el contenido anterior se queda y se marca que se está actualizando
   (ver [`feedback.md`](feedback.md)).

### Los tres vacíos

| Vacío | Cuándo | Qué ofrece |
|---|---|---|
| Inicial | No hay nada todavía | Ilustración y el botón que lo llena |
| Por filtro | Hay datos y el filtro los esconde | Cuál es el filtro aplicado y cómo quitarlo |
| Por permiso | Existe pero este rol no lo ve | Quién puede darlo. **Sin ilustración** |

Ninguno existe, y `EmptyState` está en la lista de componentes previstos. Ojo con
una consecuencia del producto: el hogar **nace con sus categorías sembradas**, así
que la vista de categorías no tiene vacío inicial de verdad y la de assets sí.

### De la fila a la ficha, y el dato heredado

En ultrawide, [`space.md`](../foundations/space.md) da la respuesta buena al
espacio sobrante: **una segunda columna con el detalle del elemento
seleccionado**, en lugar de estirar la fila hasta que el nombre y su estado
queden a un palmo. Por debajo de eso, la ficha es una pantalla propia.

La ficha de un asset tiene una particularidad que ninguna pantalla del Hito 1
tenía: **parte de sus datos no son suyos**. Cuando el asset tiene artículo, el
nombre, la categoría y la unidad se resuelven desde él. El contrato lo dice campo
a campo —«Propio del asset, o resuelto desde su artículo cuando lo tiene»— y
`AssetPatch` lo cierra: *nombre y `categoryId` solo son válidos en un asset sin
artículo; si lo tiene, se cambian en el artículo*.

De ahí tres exigencias para la ficha:

- **Se distingue a la vista lo propio de lo heredado.** No es decoración: marca
  la diferencia entre editar aquí y editar en otro sitio.
- **Editar un dato heredado lleva al artículo y avisa del alcance.** Cambiar el
  nombre del artículo lo cambia para **todas** las unidades que lo comparten, que
  es justamente para lo que existe el artículo.
- **La herencia se deduce de `articleId`, no de una marca por campo.** La
  respuesta no dice qué campo vino de dónde: trae los valores resueltos y el
  `articleId`. Si algún día hace falta más finura, es una pregunta para el
  contrato.

Y un detalle de la unidad que se olvida: **la fija el artículo**, así que en la
ficha de una existencia la unidad se muestra y no se edita.

### Antiusos

| Antiuso | Por qué |
|---|---|
| Una tarjeta con sombra por fila en escritorio | Trescientas sombras son una textura, y cada adorno roba altura de fila |
| Cebra, borde, sombra y negrita a la vez | Acumular señales sobre la misma fila no es jerarquía |
| Un `Button` dentro de una fila compacta | Mide 44 px y la fila 36. El botón compacto no existe todavía; ver [`button.md`](../components/button.md) |
| Hacer pulsable solo el icono del final | La fila entera es el objetivo |
| Duplicar la colección en el DOM para tener tarjetas y tabla | Dos recorridos para quien navega con lector de pantalla |
| «No hay resultados» a secas | Un vacío dice por qué está vacío y ofrece la salida |
| Pintar un `Spinner` encima de una lista que ya se veía | Eso es una actualización, y no se vacía lo que se está leyendo |

## Decisiones abiertas

- **Paginación clásica o scroll infinito**, y si el estado del listado —página,
  filtros, orden— viaja en la URL.
- **Qué columnas lleva la tabla de assets** y en qué orden, que depende de qué se
  busca más: dónde está algo, o qué hay en un sitio.
- **Ordenación.** El contrato no expone ningún parámetro de orden en las
  colecciones del Hito 2, así que hoy el orden lo decide el servidor y el cliente
  no puede cambiarlo. Si hace falta ordenar por columna, es una pregunta para el
  contrato antes que para la interfaz.
- **Selección múltiple.** La dirección visual describe cómo se ve una fila
  seleccionada —fondo, casilla y barra de acento— pero no hay ninguna operación
  del core que actúe sobre varias filas a la vez.

## Referencias

- [`density.md`](../foundations/density.md): las dos densidades y el presupuesto
  de calidez. **Es el documento que hay que leer antes que este.**
- [`space.md`](../foundations/space.md): la regla del sobrante y la segunda
  columna.
- [`components/status-badge.md`](../components/status-badge.md): el único color
  de la fila.
- [`hierarchy.md`](hierarchy.md) y [`feedback.md`](feedback.md)
- [`openapi.yaml`](../../../../openapi.yaml): filtros, paginación y qué campos
  trae cada colección.
- [`core-model.md`](../../../common/product/core-model.md): artículo frente a
  existencia, y de dónde sale cada dato heredado.

## Historial de cambios

| Fecha | Cambio | Autor |
|---|---|---|
| 2026-08-12 | Creación del documento. El patrón está previsto: no hay ningún listado paginado en la aplicación. | Equipo DRP |
