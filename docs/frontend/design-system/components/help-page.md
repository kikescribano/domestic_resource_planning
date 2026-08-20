# HelpPage

| Campo | Valor |
|---|---|
| Estado | Vigente |
| Responsable | Equipo DRP |
| Ámbito | frontend |
| Última revisión | 2026-08-20 |

La guía de la herramienta, en `/ayuda`. Es la tercera pantalla con ficha propia
—tras [`loan-external-page.md`](loan-external-page.md) y
[`suppliers-page.md`](suppliers-page.md)— y está aquí por un motivo que las otras
dos no tienen: **su contenido describe a las demás pantallas, así que caduca cada
vez que otra cambia**. Esta ficha lleva la regla que evita esa caducidad y el
registro de deuda que la hace practicable; ver el apartado de alineación, que es
el corazón del documento.

## Propósito y situaciones de uso

Explicar qué hace cada pantalla y qué problema resuelve, sin salir de la
aplicación. Una **sección** por pantalla —cabecera con icono, nombre y el
enlace «Ir a …»— y debajo sus **tarjetas**: una con la explicación general
—qué es la pantalla y qué papel juega— y una por cada caso de uso, con su
ejemplo práctico en situación doméstica reconocible —el yogur que caduca, la
hidrolimpiadora prestada—. Un buscador filtra **tarjeta a tarjeta** con la
misma normalización que el catálogo (sin mayúsculas ni acentos): sobrevive
cada tarjeta que coincide, bajo la cabecera de su sección, y una sección sin
tarjetas desaparece. El nombre de la pantalla cuenta para todas sus tarjetas,
así que buscarlo enseña la sección entera.

El contenido es **estático a propósito**: no pide nada a la API, así que sirve
igual con el hogar recién creado que con años de histórico, y no tiene estados de
carga ni de error.

Los bloques van en el orden de la navegación —Tu hogar, Datos maestros,
Configuración— y cierran con «Cuenta», que no es una parada de la lista porque
acompaña a la marca. Los cuatro módulos aparecen **siempre**, con el hogar los
tenga encendidos o no: la ayuda es también el escaparate de lo que se puede
encender, y cada bloque de módulo dice dónde.

### Lo que esta pantalla NO hace

- **No es un tour guiado** ni superpone nada sobre las demás pantallas; el
  onboarding sigue siendo el «Por dónde empezar» de la portada.
- **No lleva capturas**: los enlaces llevan a la pantalla de verdad, que siempre
  está a un clic y nunca desactualizada.
- **No documenta el producto para quien lo desarrolla** — eso es de `docs/` — ni
  repite los textos introductorios que cada pantalla ya lleva.
- **No oculta lo que el papel del usuario no puede tocar**: el bloque de
  «General» explica una pantalla que un miembro no ve, y lo dice, porque saber
  que existe y quién la maneja también es ayuda.

## Anatomía, variantes y estados

`PageHeading` con el interrogante (`CircleHelp`, el mismo icono que la parada de
la navegación), un párrafo de propósito, un `Field type="search"` y las
secciones. Cada pantalla es un `<section>` nombrado por su `h2` —icono y nombre
del menú— con el enlace «Ir a …» a la derecha, y debajo una rejilla de tarjetas
(una columna en móvil, dos desde `md`): cada tarjeta es un `<article>` con su
`h3` —«Explicación general» o el nombre del caso de uso—, el texto, y en los
casos el ejemplo práctico rotulado «Ejemplo:». La tarjeta general abre la
sección a todo el ancho, que es su jerarquía. Cuando el filtro no deja ninguna
tarjeta en ninguna sección, `EmptyState` con «Ninguna tarjeta coincide» — la
lista vacía sin explicación sería indistinguible de una rota.

Mientras hay búsqueda activa, **las coincidencias se resaltan** dentro de las
tarjetas con `<mark>`: la comparación normalizada encuentra y el recorte sale
del texto original —«prestamo» resalta «préstamo», tilde incluida—. El color
del resalte es la pareja que la capa base ya usa para el `::selection`
—`accent-soft` con la tinta normal—, así que no estrena combinación de
contraste.

Sin variantes y sin estados remotos: el único estado es el texto del buscador.

## API pública o propiedades relevantes

Ninguna propiedad: se monta desde la ruta. El dato es `HELP_TOPICS`, una lista
local de `{ title, path, icon, overview, useCases }` —con cada caso de uso como
`{ title, description, example }`— donde título e icono son **los del menú** —
la ayuda no estrena nombres. Para pintar y filtrar, cada pantalla se aplana en
tarjetas —la general y una por caso— y la búsqueda evalúa cada tarjeta por su
título, su texto y su ejemplo, siempre con el nombre de la pantalla delante.

## Comportamiento responsive y con contenido extremo

Los bloques ocupan el ancho del shell y los párrafos se limitan con `max-w-prose`;
la cabecera del bloque envuelve (`flex-wrap`), así que un título largo y el
enlace conviven a 320 px sin truncar nada. No hay tablas ni contenido que exija
desplazamiento horizontal. Está en la pasada sistemática del recorrido vertical,
que le mide el reflujo de 320 px a ultrawide.

## Teclado, foco, semántica y anuncios asistivos

Cada bloque es un `article` con `aria-labelledby` apuntando a su `h2`: un lector
de pantalla salta de bloque en bloque y los anuncia por su nombre. Los enlaces
dicen el destino en su nombre accesible —«Ir a Préstamos», nunca quince «Abrir»
indistinguibles—. El buscador es un `Field` con etiqueta visible y la pista en
`aria-describedby`. El foco es el de la capa base, como en todo el sistema.

## Alineación con las pantallas: la regla y su registro de deuda

Una guía escrita a mano se queda vieja **por omisión, no por error** — el mismo
mecanismo que el repaso de fichas del 2026-08-20 encontró en este directorio. La
regla es la de los documentos autoprogramados del `CLAUDE.md`, aplicada al
producto:

**Quien cambia sustantivamente una pantalla —lo que hace, un flujo, dónde vive
algo— alinea su bloque de `HELP_TOPICS` en el mismo cambio, o deja una fila en
la tabla de abajo.** La fila no es un fracaso sino la alternativa buena cuando el
cambio no da para pararse a escribir: deja la deuda contada para saldarla en
lote, varios bloques de una vez, en un cambio que es solo de la ayuda.

Sustantivo es lo que cambiaría lo que el bloque cuenta; un retoque visual o un
texto interno no lo son. La tabla vacía es el estado bueno, y **vaciarla entera
es una tarea que cabe en cualquier sesión**.

| Fecha | Pantalla | Qué cambió y qué falta contar en su bloque |
|---|---|---|

*Sin deuda apuntada.*

## Ejemplos correctos, antiusos y evidencias de prueba

Correcto: un bloque nuevo cuando nace una pantalla de la navegación, con caso de
uso en situación doméstica y el nombre del menú. Antiusos: un bloque que estrena
nombre o icono que el menú no tiene; capturas de pantalla; texto que documenta la
API en lugar del problema que la pantalla resuelve.

Evidencias: [`help.test.tsx`](../../../../frontend/src/routes/help.test.tsx)
cubre los bloques con su enlace, el filtrado sin acentos, el estado vacío y la
colocación en la navegación —remata «Configuración» y la ve también quien no
administra—; la pasada sistemática del recorrido vertical le aplica teclado,
reflujo y axe en los dos modos.

## Estado de implementación y enlace al componente real

**Implementada** en [`routes/help.tsx`](../../../../frontend/src/routes/help.tsx)
(2026-08-20), con su ruta en `App.tsx` y su parada cerrando el grupo
«Configuración» para todos los papeles. Entró en `AUDITED_SCREENS` el mismo día
que nació, que es como se hereda la auditoría.

## Historial de cambios

| Fecha | Cambio | Autor |
|---|---|---|
| 2026-08-20 | **Las coincidencias de la búsqueda se resaltan** dentro de las tarjetas con `<mark>`, recortando del texto original lo que encontró la comparación normalizada, y con la pareja de color del `::selection` de la capa base. | Equipo DRP |
| 2026-08-20 | **El bloque único se parte en tarjetas**: cada pantalla pasa a ser una sección —cabecera con su enlace— y debajo una tarjeta por la explicación general y una por caso de uso, en rejilla de dos columnas desde `md`. El motivo es el buscador, que ahora filtra **tarjeta a tarjeta** en lugar de bloques enteros: una palabra que solo vive en un ejemplo deja esa tarjeta sola bajo su cabecera. | Equipo DRP |
| 2026-08-20 | **Cada bloque se reparte en dos partes bajo subtítulo** —«Explicación general» y «Casos de uso»— y los casos pasan de uno a uno por funcionalidad, cada uno con su ejemplo práctico. La revisión que lo trajo corrigió además dos afirmaciones que la pantalla real desmintió: el papel se elige **al invitar** —no hay cambio de rol en Personas— y el avatar se pone en **Cuenta**. | Equipo DRP |
| 2026-08-20 | Creación de la ficha con la pantalla ya implementada, y con el apartado que es su motivo: la regla de alineación con las pantallas y el registro de deuda para saldarla en lote. | Equipo DRP |
