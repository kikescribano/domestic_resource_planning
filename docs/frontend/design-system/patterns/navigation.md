# Navegación

| Campo | Valor |
|---|---|
| Estado | Borrador |
| Responsable | Equipo DRP |
| Ámbito | frontend |
| Última revisión | 2026-08-20 |

## Propósito

Documentar el shell de la aplicación —lo que rodea a todas las pantallas de
detrás del login— y las reglas que lo sostienen: un solo `<nav>` recolocado,
el salto al contenido, y el sitio fijo de la acción principal.

Es, junto al formulario, el patrón que **sí está implementado**, y el que más
presión ha aguantado: de tres destinos pasó a ocho al cerrarse la Fase 1, y el
Hito 0 de la Fase 2 lo obligó a admitir doce.

## Alcance

### Incluido

- El shell: barra inferior en móvil, columna lateral desde `md`, y el reparto
  en dos grupos —el hogar y los módulos— que trajo la Fase 2.
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
| Reparto en tres grupos con orden único y tope de cinco paradas en móvil | **Implementado** (Fase 2, Hito 0; reagrupado el 2026-08-20) |
| Columna encogible a iconos en escritorio | **Implementado** (2026-08-20) |
| Salto al contenido | **Implementado** |
| Cabecera de pantalla con ranura de acción | **Implementada, y la ranura sin usar** |
| Sitio de la acción principal en móvil | **Sin resolver**: la banda inferior ya está ocupada |
| Cómo caben doce destinos en la barra inferior | **Resuelto** (Fase 2, Hito 0): no caben, y por eso hay «Más» |
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
| Marca «DRP» | Oculta en la barra —la enseñan las cabeceras de «Hogar» y «Más»— | Visible: el sello de la marca con nombre y lema, y debajo «Cuenta» y «Salir» |
| `<nav aria-label="Principal">` | Fila con las cuatro primeras paradas de «Tu hogar» y «Más» | Columna con tres grupos: Tu hogar, Datos maestros y Configuración —cuyas paradas de administración solo ve quien administra— |
| Rótulo de grupo | Oculto | Visible, en `text-caption`, y siempre referenciado con `aria-labelledby` |
| Enlace | `min-h-touch`, `flex-1`, centrado | Alineado a la izquierda, con radio y fondo al pasar |
| `<main id="contenido">` | `max-w-shell`, con `pb-24` para no quedar bajo la barra | `md:pb-6` |

Tres detalles que se pierden al leer las clases por encima:

- **El estado activo no se dice solo con color.** Lleva `font-medium` además del
  acento, y `aria-current`, que `NavLink` pone por su cuenta. Es la aplicación
  literal de la regla en el sitio donde más fácil sería saltársela.
- **El `pb-24` de `main` no es margen decorativo**: reserva la altura de la barra
  fija para que el final del contenido no quede debajo de ella.
- **Cerrar sesión no está entre las paradas.** Es una acción, no un sitio, y
  ponerla entre los enlaces la deja al lado de «Personas» esperando a que alguien
  la pulse con el pulgar por error. Desde el 2026-08-20 la salida rápida
  acompaña a la marca —«Cuenta» y «Salir» bajo el sello en escritorio, en el
  banner y fuera del landmark de navegación; en móvil, en el apartado que
  cierra «Más»— y `AccountPage` conserva la sección que explica qué pasa al
  salir. Lo que sigue sin existir es un «Salir» dentro de la lista.

### Tres grupos, y el tope de cinco paradas

El reparto en grupos lo trajo el **Hito 0 de la Fase 2** —dos, el hogar y los
módulos, porque cuatro módulos llevaban la navegación de ocho entradas a doce—
y el **2026-08-20** pasó a tres, con **un orden único para las dos
plataformas**. El orden de los grupos y el de sus paradas es una decisión de
producto, no una casualidad del código:

- **Tu hogar**: Hogar, Avisos, Inventario, Préstamos y, si están activos, los
  módulos Mantenimiento, Compras y Almacén. Es la actividad: lo que pasa y lo
  que hay que atender.
- **Datos maestros**: Personas, Catálogo, Ubicaciones, Proveedores —si está
  activo— y Archivo. Es lo que las demás pantallas consultan: quién, qué y
  dónde. El nombre es el término de un ERP a conciencia — este es doméstico,
  pero es un ERP.
- **Configuración**: General (`/configuracion`, con su engranaje), Módulos del
  hogar y, cerrando el grupo y la navegación entera, **Ayuda** (`/ayuda`, con su
  interrogante). Las dos primeras **solo las ve quien administra** —un miembro
  no puede tocar nada de lo que hay dentro, y una puerta cerrada es peor que
  ninguna; la ruta tecleada a mano lo devuelve al inicio—, pero el grupo existe
  para todos los papeles porque «Ayuda», la guía de la herramienta, es de
  cualquiera que la use.

**Ya no hay grupo «Módulos»**: cada módulo vive donde su contenido pertenece, y
la puerta para encenderlos es «Módulos del hogar», dentro de Configuración.

**La barra inferior tiene un tope, y es aritmética.** A 320 px cada parada mide
320 dividido entre el número de paradas, así que **cinco es el máximo** que
respeta los 44 px de objetivo mínimo. Con ocho salían **40 px**, y así estuvo toda
la Fase 1 sin que nadie lo notara: el enlace tiene altura de sobra y solo falla a
lo ancho, que es justo lo que no se ve mirando la pantalla.

Así que:

- **En móvil**, la barra es **el recorte de las cuatro primeras paradas de «Tu
  hogar»** —Hogar, Avisos, Inventario, Préstamos— más «Más»: el mismo orden que
  la columna, no otra lista. Con el reagrupado, «Avisos» entró en el pulgar y
  «Ubicaciones» salió hacia «Datos maestros». El tope de cinco es una medida y
  no una preferencia, así que lo que se negocia es **qué cinco**, nunca el
  número.
- **«Más» enseña el resto con los mismos grupos y el mismo orden**: lo de «Tu
  hogar» que no cupo en la barra —los módulos de actividad—, «Datos maestros»
  entero y «Configuración», que para quien no administra se queda en su única
  parada abierta, «Ayuda». Abre con la marca igual que
  «Hogar» y cierra con el apartado de la cuenta —el enlace al detalle y la
  salida directa—, que es donde el móvil tiene lo que en escritorio vive bajo
  el sello.
- **Desde `md`**, la columna enseña los tres grupos rotulados, cada uno con sus
  paradas en el orden fijado.
- **Un módulo apagado no está**, ni en un sitio ni en el otro. Es la tercera capa
  del gate de la [`ADR-010`](../../../common/architecture/decisions/ADR-010-module-boundaries-and-activation.md);
  las otras dos son el `403 MODULE_INACTIVE` de la API y el silencio del handler.

Y la regla de siempre no se rompe: **sigue habiendo un solo `<nav>`**. Los rótulos
de grupo son **párrafos** referenciados con `aria-labelledby` y no encabezados —un
`h2` ahí saldría antes que el `h1` del contenido y dejaría el documento con los
niveles al revés— y lo que no toca en móvil se oculta con CSS en lugar de pintarse
dos veces. Desde el reagrupado, en móvil los grupos se disuelven con
`display: contents` para que las paradas de la barra y «Más», que viven en
listas distintas, compartan fila; el DOM conserva la jerarquía entera —div, ul,
li—, así que las listas siguen siendo listas para el lector de pantalla.

Lo comprueba el recorrido vertical midiendo **la caja de cada parada visible a
320 px**. Es la única forma de que el defecto no vuelva con el módulo siguiente:
un enlace de 40 px no se distingue de uno de 44 px a simple vista.

### La columna encogida a iconos

Solo en escritorio —la barra inferior no tiene nada que encoger— la columna se
pliega de 256 px a 80 px con el conmutador que la cierra por abajo, para que
quien trabaja con el contenido gane ese sitio. Las reglas del pliegue:

- **La etiqueta no se va: pasa a `sr-only` dentro del propio enlace.** El
  nombre accesible no cambia, así que el enlace se sigue encontrando por él, y
  el `title` hace de recordatorio para el puntero. Es la versión encogida de
  la regla de siempre: el icono orienta, no nombra.
- **Los rótulos de grupo dejan la pantalla pero no el documento**: siguen
  existiendo para `aria-labelledby`, y su frontera visual la marca un filete
  entre grupos — también entre la identidad y «Hogar», que sin él se pegaban.
- **La marca queda en el sello** —el nombre pasa a sr-only— y del bloque de la
  cuenta queda **solo «Salir»**: «Cuenta» es un destino, y a icono junto al
  sello se confundía con una parada más. El detalle de la cuenta se recupera
  ensanchando la columna.
- **La elección persiste en `localStorage` (`drp.nav`)**, la misma casa que
  `drp.theme`: es una preferencia del dispositivo, no del hogar, así que no
  viaja a la API.
- **El conmutador va en `caption`**, como los rótulos: es utillaje de la
  columna y no una parada, y al tamaño de las paradas les disputaba el peso.

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

- **Dónde va la acción principal en móvil**, con lo dicho más arriba.
- ~~**Cómo caben seis destinos en la barra inferior**~~ — **resuelta en la Fase 2,
  Hito 0**: no caben. Cinco es el tope a 320 px, así que hay cuatro y «Más». Sin
  iconos *en lugar de* la etiqueta, que era la otra salida: una etiqueta corta se
  lee y un icono hay que aprendérselo. **Ampliación del 2026-08-20:** los iconos
  llegaron después, pero *acompañando* a la etiqueta y nunca en su lugar — en
  móvil el icono va encima de la etiqueta en columna, con el suelo de 44 px por
  parada intacto y medido, y cada sección repite su icono en la cabecera. El
  rechazo original era al icono solo, y sigue en pie.
- **Cómo se vuelve de una vista de detalle** a 375 px.
- **Dónde vive el shell.** `HouseholdShell`, `navLinkClass` y `PageHeading` están
  dentro de `household.tsx`, que es un fichero de rutas.

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
| 2026-08-20 | **«Ayuda» (`/ayuda`) remata «Configuración» y con ella la navegación entera**, en el mismo sitio para las dos plataformas —columna en escritorio, «Más» en móvil; nunca el pulgar—. El grupo deja de ser exclusivo de administración: sus dos paradas de configuración siguen siendo solo de quien administra, pero la guía de la herramienta es de todos los papeles, así que el grupo existe para todos. | Equipo DRP |
| 2026-08-20 | **La columna se puede encoger a iconos en escritorio** (256 → 80 px), con el conmutador cerrándola por abajo: etiquetas a `sr-only` sin perder el nombre accesible, `title` como recordatorio, filetes donde había rótulos —también entre «Salir» y «Hogar»—, la marca en el sello, solo «Salir» del bloque de la cuenta, y la elección persistida en `drp.nav`. Ensanchada, la marca pasa a centrarse en la anchura de la columna. | Equipo DRP |
| 2026-08-20 | **Reagrupado en tres** —Tu hogar, Datos maestros y Configuración— con un orden único para columna, barra y «Más»: «Avisos» entra en el pulgar y «Ubicaciones» sale hacia «Datos maestros», los módulos pierden su grupo y viven donde su contenido pertenece, y «Configuración» —solo para quien administra— estrena «General» (`/configuracion`), que hereda la baja del hogar de la pantalla «Hogar». | Equipo DRP |
| 2026-08-20 | **«Cuenta» deja de ser una parada y se va con la marca**, con la salida directa al lado: en escritorio bajo el sello —en el banner, fuera del landmark de navegación—; en móvil, en el apartado que cierra «Más», que además abre con la marca igual que «Hogar». La lista queda para lo que es del hogar, y «Salir» sigue sin pisar la lista de paradas. | Equipo DRP |
| 2026-08-18 | Fase 2, Hito 0: la navegación se parte en dos grupos —el hogar y los módulos— dentro del mismo `<nav>`, y la barra inferior de móvil baja a cuatro paradas más «Más». Queda resuelta la decisión abierta de cuántos destinos caben abajo: cinco a 320 px, medido y comprobado en el recorrido vertical. | Equipo DRP |
| 2026-08-12 | Creación del documento con el shell del Hito 1 y las tres decisiones que el Hito 2 tiene que tomar. | Equipo DRP |
