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
aplicación. Un bloque por pantalla con tres piezas: la explicación, un **caso de
uso** en situación doméstica reconocible —el yogur que caduca, la hidrolimpiadora
prestada— y el enlace «Ir a …». Un buscador filtra bloques enteros con la misma
normalización que el catálogo (sin mayúsculas ni acentos).

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
la navegación), un párrafo de propósito, un `Field type="search"` y la lista de
bloques. Cada bloque es un `<article>` dentro de un `<li>`: cabecera con `h2`
—icono de la pantalla y su nombre— y el enlace «Ir a …» a la derecha; debajo, la
descripción y el caso de uso rotulado «Cuándo te sirve». Cuando el filtro no
encuentra nada, `EmptyState` con «Ningún bloque coincide» — la lista vacía sin
explicación sería indistinguible de una rota.

Sin variantes y sin estados remotos: el único estado es el texto del buscador.

## API pública o propiedades relevantes

Ninguna propiedad: se monta desde la ruta. El dato es `HELP_TOPICS`, una lista
local de `{ title, path, icon, description, useCase }` donde título e icono son
**los del menú** — la ayuda no estrena nombres. La búsqueda concatena título,
descripción y caso de uso, normalizados.

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
| 2026-08-20 | Creación de la ficha con la pantalla ya implementada, y con el apartado que es su motivo: la regla de alineación con las pantallas y el registro de deuda para saldarla en lote. | Equipo DRP |
