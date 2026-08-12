# Navegación

| Campo | Valor |
|---|---|
| Estado | Borrador |
| Responsable | Equipo DRP |
| Ámbito | frontend |
| Última revisión | 2026-08-12 |

## Propósito

Documentar el shell de la aplicación —lo que rodea a todas las pantallas de
detrás del login— y las reglas que lo sostienen: un solo `<nav>` recolocado,
el salto al contenido, y el sitio fijo de la acción principal.

Es, junto al formulario, el patrón que **sí está implementado**, y también el que
el Hito 2 va a poner a prueba: pasa de tres destinos a seis.

## Alcance

### Incluido

- El shell: barra inferior en móvil, columna lateral desde `md`.
- La cabecera de pantalla y dónde va la acción principal.
- Lo que un cambio de ruta tiene que hacer y hoy no hace.

### Fuera de alcance

- La plantilla de las pantallas **anteriores** al login, en
  [`components/card.md`](../components/card.md).
- El árbol de ubicaciones, que también es navegación pero de datos:
  [`hierarchy.md`](hierarchy.md).
- Los nombres de las rutas y por qué las públicas van en castellano, en
  [`App.tsx`](../../../../frontend/src/App.tsx).

## Estado

| Parte | Estado |
|---|---|
| Shell responsive con navegación única | **Implementado** |
| Salto al contenido | **Implementado** |
| Cabecera de pantalla con ranura de acción | **Implementada, y la ranura sin usar** |
| Sitio de la acción principal en móvil | **Sin resolver**: la banda inferior ya está ocupada |
| Anuncio del cambio de ruta | **Previsto**: no hay nada |
| Conmutador de tema | **Previsto**: ninguna de las tres piezas que el sistema pide |

## Contenido

### Un solo `<nav>`, recolocado con CSS

Es la decisión que da valor al patrón y está tomada en
[`household.tsx`](../../../../frontend/src/routes/household.tsx): **el mismo
elemento** es barra inferior por debajo de `md` —donde llega el pulgar— y columna
lateral a partir de ahí.

La versión fácil sería una barra con `md:hidden` y una lateral con
`hidden md:flex`. Pinta los mismos enlaces dos veces en el DOM, y quien navega
con lector de pantalla se encuentra **dos landmarks de navegación idénticos** y
recorre la lista dos veces. Que uno esté oculto con `display:none` lo salva en la
práctica, y basta un cambio de clase para que deje de estarlo. El fallo no se ve
mirando la pantalla, que es lo que lo hace peligroso.

La misma regla reaparece en [`listing.md`](listing.md) —tarjetas y tabla son el
mismo listado— y en [`hierarchy.md`](hierarchy.md). Conviene reconocerla: **una
estructura, dos presentaciones**.

Anatomía, tal y como está escrita:

| Pieza | Por debajo de `md` | Desde `md` |
|---|---|---|
| Contenedor | Columna | Fila (`md:flex-row`) |
| `<header>` | Fijo abajo, con `border-t` | Estático, 256 px de ancho, con `border-r` |
| Marca «DRP» | Oculta | Visible, en serif |
| `<nav aria-label="Principal">` | Fila de enlaces repartidos | Columna |
| Enlace | `min-h-touch`, `flex-1`, centrado | Alineado a la izquierda, con radio y fondo al pasar |
| `<main id="contenido">` | `max-w-shell`, con `pb-24` para no quedar bajo la barra | `md:pb-6` |

Tres detalles que se pierden al leer las clases por encima:

- **El estado activo no se dice solo con color.** Lleva `font-medium` además del
  acento, y `aria-current`, que `NavLink` pone por su cuenta. Es la aplicación
  literal de la regla en el sitio donde más fácil sería saltársela.
- **El `pb-24` de `main` no es margen decorativo**: reserva la altura de la barra
  fija para que el final del contenido no quede debajo de ella.
- **Cerrar sesión no está en la navegación.** Es una acción, no un sitio, y
  ponerla entre los enlaces la deja al lado de «Personas» esperando a que alguien
  la pulse con el pulgar por error. Vive en `AccountPage`, como botón.

### El salto al contenido

Primer elemento tabulable de la página, invisible hasta que recibe el foco
(`sr-only focus:not-sr-only`). Es lo que evita recorrer la navegación entera en
cada pantalla con el teclado, y su destino es el `id="contenido"` del `<main>`.

Con tres enlaces se agradece; con los seis que trae el Hito 2, deja de ser un
detalle de cortesía.

### La cabecera de pantalla, y el problema de la acción principal

Dentro del shell, cada pantalla abre con `PageHeading`: un `<h1>` y una **ranura
de acción a la derecha**, que envuelve en `flex-wrap` para que la acción baje de
línea antes que aplastar el titular.

Hoy **ninguna de las tres pantallas usa esa ranura**, así que el sitio de la
acción principal no está probado por nada. Y ahí hay una contradicción que el
Hito 2 tiene que resolver, no heredar:

[`look-and-feel.md`](../../product-design/look-and-feel.md) da a la acción
principal un sitio fijo —«barra inferior en móvil, arriba a la derecha en
escritorio»— y reserva para ella una banda propia en móvil «que no se rellena con
otra cosa». **Pero en móvil la banda inferior ya la ocupa la navegación**, y
`PageHeading` coloca la acción arriba a la derecha en todos los anchos, porque no
es responsive.

Las salidas son conocidas y hay que elegir una: un botón flotante por encima de
la barra, la acción dentro de la propia barra, o aceptar que en móvil la acción
principal vive arriba y corregir la dirección visual. Lo que no vale es dejarlo
sin decidir y que cada pantalla del hito lo resuelva a su manera.

### Lo que el Hito 2 le pide

El hito añade al menos tres destinos —inventario, ubicaciones y catálogo— a los
tres que hay. Seis entradas en una barra inferior de 375 px salen a poco más de
60 px por entrada, y de ahí hay que descontar los 8 px de relleno lateral de cada
una: **«Ubicaciones» no cabe en una línea a 14 px**. Las opciones son las de
siempre —icono con etiqueta corta, agrupar destinos bajo uno solo, o una barra
desplazable, que es la mala— y ninguna está decidida.

Y aparece algo que hoy no existe: **vistas de detalle**. Una ficha de asset o de
ubicación no es un destino de la navegación, es un sitio al que se entra desde
una fila. Necesita una forma de volver que no sea el botón del navegador, y a
375 px eso es una migaja de pan o una flecha en la cabecera. `PageHeading` no
tiene ranura para ninguna de las dos.

### Antiusos

| Antiuso | Por qué |
|---|---|
| Una barra para móvil y otra para escritorio | Dos landmarks y dos recorridos |
| Un `Button` que navega | Si lleva a un sitio es un enlace, y se nota al abrir en otra pestaña o con lector de pantalla |
| Marcar el enlace activo solo con color | Incumple 1.4.1. Lleva peso y `aria-current` |
| Meter «Cerrar sesión» entre los destinos | No es un sitio |
| Esconder una acción frecuente tras un menú de tres puntos | Lo prohíbe la personalidad «táctil y de una mano» |
| Un segundo `<main>` dentro del shell | Es lo que pasa si se usa `AuthCard` aquí dentro |

## Decisiones abiertas

- **Dónde va la acción principal en móvil**, con lo dicho más arriba. Es la
  decisión que más pantallas del Hito 2 toca.
- **Cómo caben seis destinos en la barra inferior**, y si con iconos.
- **Cómo se vuelve de una vista de detalle** a 375 px.
- **Dónde vive el shell.** `HouseholdShell`, `navLinkClass` y `PageHeading` están
  dentro de `household.tsx`, que es un fichero de rutas. Los tres los va a usar
  todo el hito.

## Lo que falta

- **El cambio de ruta no se anuncia y no mueve el foco.** En una aplicación de una
  sola página, ir de «Personas» a «Tu cuenta» cambia el contenido sin que nada se
  lo diga a un lector de pantalla, y el foco se queda en el enlace pulsado. Lo
  habitual es llevar el foco al `<h1>` de la pantalla nueva, o anunciar el cambio
  por una región viva. No hay ninguna de las dos cosas.
- **El título del documento no cambia.** Las diez rutas comparten el
  `<title>DRP</title>` de
  [`index.html`](../../../../frontend/index.html), así que el historial del
  navegador y las pestañas no distinguen una pantalla de otra.
- **No hay conmutador de tema, y el sistema de diseño lo da por hecho.**
  [`tokens/README.md`](../tokens/README.md) fija un contrato de tres piezas que
  corresponden a quien construye el shell: un conmutador de tres estados —claro,
  oscuro y automático, donde automático **quita** el atributo—, la persistencia en
  `localStorage` con la clave `drp.theme`, y un script en línea en `index.html`
  antes de pintar para que no haya parpadeo. **No existe ninguna de las tres.**
  La aplicación tiene modo oscuro porque sigue al sistema operativo, y no hay
  forma de elegir desde dentro.
- **No hay migaja de pan ni vuelta atrás** para las vistas de detalle.
- **No hay búsqueda global.** El contrato ofrece búsqueda por colección
  —`GET /articles?q=`—, no una transversal; si algún día se quiere, es una
  pregunta para el contrato.

## Referencias

- [`look-and-feel.md`](../../product-design/look-and-feel.md): el principio 3 y
  la tabla de comportamiento responsive.
- [`tokens/README.md`](../tokens/README.md): el contrato de modo oscuro que le
  toca al shell.
- [`components/card.md`](../components/card.md): la plantilla de antes del login.
- [`listing.md`](listing.md) y [`hierarchy.md`](hierarchy.md): la misma regla de
  una estructura y dos presentaciones.
- [`accessibility/`](../../accessibility/README.md)

## Historial de cambios

| Fecha | Cambio | Autor |
|---|---|---|
| 2026-08-12 | Creación del documento con el shell del Hito 1 y las tres decisiones que el Hito 2 tiene que tomar. | Equipo DRP |
