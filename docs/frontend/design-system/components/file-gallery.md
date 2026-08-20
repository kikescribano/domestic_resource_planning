# FileGallery

| Campo | Valor |
|---|---|
| Estado | Implementado |
| Responsable | Equipo DRP |
| Ámbito | frontend |
| Última revisión | 2026-08-20 |

> **Esta ficha se escribió antes que el componente**, como especificación, y el
> Hito 3 lo construyó siguiéndola: vive en
> [`files.tsx`](../../../../frontend/src/ui/files.tsx). Lo que sigue sin
> construirse está en [Lo que falta](#lo-que-falta).

## Propósito y situaciones de uso

Enseñar los ficheros que cuelgan de **una** cosa —un asset, un artículo, una
ubicación— como una rejilla de cuadros de 1:1: las fotos con su miniatura, los
PDF con su marcador. Desde ahí se abre uno, se descarga o se quita.

Situaciones de uso:

| Dónde | Qué contiene | De dónde salen |
|---|---|---|
| Ficha de un asset | Su foto y sus documentos —factura, garantía— | `photoThumbnailUrl` del asset y `GET /documents?assetId=` |
| Ficha de un artículo | Su foto y su manual | `photoThumbnailUrl` del artículo y `GET /documents?articleId=` |
| Ficha de una ubicación | Su foto | `photoThumbnailUrl` de la ubicación |

### Por qué es un componente y no un patrón

Es la decisión que había que tomar antes de escribir nada, porque una galería es
una colección y las colecciones son territorio de
[`listing.md`](../patterns/listing.md). La respuesta es **componente**, por tres
razones y con una frontera:

1. **Tiene anatomía propia, API y estados**, que es la definición de componente
   que da [`patterns/`](../patterns/README.md): un patrón responde a «en qué
   orden van las piezas», y aquí las piezas y su orden son siempre los mismos.
2. **Se instancia dentro de otra pantalla**, tres o cuatro veces con la misma
   forma, en lugar de describir una pantalla entera. Los cinco patrones
   existentes describen pantallas; esto es un bloque dentro de una.
3. **No toma ninguna decisión de composición nueva.** Paginación, filtros y los
   tres vacíos ya están decididos en `listing.md`, y esta pieza los consume.

Y la frontera, que es lo que evita que esto pise el patrón del listado: **la
galería pinta los ficheros de una entidad; la pantalla de almacenamiento del
hogar no es una galería, es un listado.** `GET /files` viene ordenado por tamaño
descendente porque su pregunta es «qué me está ocupando el gigabyte», y esa
pregunta se responde con filas, tamaños y una acción de borrar — no con
cuadritos. Las dos comparten la celda; no comparten la forma.

Queda además la excepción explícita a la regla de las **dos formas** de
`listing.md` —tarjetas por debajo de `md`, tabla desde `md`—: una rejilla de
miniaturas **no pasa a tabla**, porque su contenido no es texto en columnas sino
la imagen misma. Lo que cambia al ensanchar la pantalla es el número de columnas.
No es una contradicción con el patrón: es que las cuatro colecciones que el
patrón nombra —assets, artículos, ubicaciones y categorías— son filas de datos, y
esta no.

## Anatomía, variantes y estados

Dos niveles: la rejilla y la celda.

**La rejilla** es un `<ul>` en `grid` con 12 px de separación (`gap-3`), columnas
de `minmax(6.5rem, 1fr)` en `auto-fill` y ningún borde ni fondo propio: se dibuja
sobre el papel de la ficha.

**La celda** es un `<li>` con dos partes en columna:

1. **El cuadro**, `aspect-square`, `rounded-md` (8 px, el radio de una foto en
   miniatura según [`shape-and-elevation.md`](../foundations/shape-and-elevation.md)),
   `overflow-hidden`, fondo `bg-surface-sunken`. Dentro va una de estas tres:
   - **La miniatura**: `<img>` con `object-cover`, `loading="lazy"` y
     `decoding="async"`.
   - **El marcador de un PDF**: icono de documento centrado, en `ink-subtle`,
     sobre la superficie hundida.
   - **El marcador de una imagen que no se ha podido pintar**: el mismo hueco,
     con el icono y la acción de recargar.
2. **El pie**, dos líneas de `text-caption`: el nombre truncado a una línea en
   `text-ink`, y el tipo y el tamaño en `text-ink-muted`.

| Rasgo | Valor | Token |
|---|---|---|
| Separación entre celdas | 12 px | `gap-3` |
| Lado mínimo de celda | 104 px | — |
| Lado máximo de celda | 160 px | ver [Responsive](#comportamiento-responsive-y-con-contenido-extremo) |
| Radio del cuadro | 8 px | `rounded-md` |
| Fondo del cuadro | Superficie hundida | `bg-surface-sunken` |
| Borde del cuadro | Sutil, decorativo | `border border-subtle` |
| Icono del marcador | 24 px, trazo 1,75 | `text-ink-subtle` |
| Pie | 13 px | `text-caption` |

**Ni sombra ni radio de tarjeta.** Doce celdas con sombra son una textura, que es
la misma razón por la que [`density.md`](../foundations/density.md) la prohíbe
dentro de una fila. El cuadro se delimita con `border-subtle` y ya.

**El marcador no es un rectángulo gris.** Es la regla de
[`iconography.md`](../foundations/iconography.md): el hueco de una foto se pinta
con el icono de la categoría sobre `surface-sunken`. En un documento no hay
categoría, así que el icono es el del documento. Si algún día se quiere un icono
distinto por `DocumentType` —factura, garantía, manual—, esa decisión es de
`iconography.md` y no de esta ficha.

### Estados de la rejilla

| Estado | Qué se pinta |
|---|---|
| Cargando | Celdas vacías con la forma real, tantas como se esperan. Hoy no hay `Skeleton` |
| Vacía | [`EmptyState`](README.md) con la acción que la llena: «Añadir una foto» |
| Con contenido | La rejilla |
| Sin permiso | El vacío por permiso de `listing.md`, sin ilustración |

### Estados de la celda

| Estado | Qué se ve |
|---|---|
| Con miniatura | La imagen |
| Sin miniatura por ser PDF | El marcador con el icono de documento. **Es lo normal, no un fallo** |
| Sin miniatura por ser enlace externo | El propio `photoUrl`, que es lo único que hay |
| Imagen caducada o rota | El marcador, «No se ha podido cargar» y la acción de recargar |
| Descargando | La celda en ocupado mientras se pide el contenido |

## API pública

Prevista. Es la propuesta de esta ficha:

```ts
interface GalleryItem {
  fileId: string
  name: string
  contentType: 'image/jpeg' | 'image/png' | 'image/webp' | 'application/pdf'
  sizeBytes: number
  thumbnailUrl: string | null
  caption?: string
}

interface FileGalleryProps {
  label: string
  items: GalleryItem[]
  onOpen: (item: GalleryItem) => void
  onRemove?: (item: GalleryItem) => void
  onStale: () => void
  emptyAction?: ReactNode
}
```

| Propiedad | Qué hace |
|---|---|
| `label` | Nombra la rejilla para quien no la ve: «Documentos de Taladro Bosch» |
| `items` | Lo que hay. **Se pasa ya resuelto**: la galería no llama a la API |
| `onOpen` | Al activar una celda. Quien lo recibe decide si abre un visor o descarga |
| `onRemove` | Opcional: sin él, la galería es de solo lectura. Quitar no significa lo mismo en una foto que en un documento (ver más abajo) |
| `onStale` | **La propiedad que hace falta explicar.** Se llama cuando una imagen falla al cargar, para que la pantalla vuelva a pedir la entidad y reciba URL frescas |
| `emptyAction` | El botón del vacío. Sin él, el vacío solo explica |

**No hay `photoUrl` ni `url` en `GalleryItem`, solo `thumbnailUrl`.** Es
deliberado: la URL de descarga no hace falta para pintar, y no tenerla a mano
quita la tentación de guardarla.

## La regla dura: una URL de imagen no se guarda

Las miniaturas son de 320 px en el lado largo, en WebP, y llegan en una **URL
firmada de vida corta que caduca con el access token que la generó** — unos
quince minutos (ver [`file-storage.md`](../../../backend/architecture/file-storage.md),
5.8.4). De ahí tres consecuencias que hay que respetar al escribir esto:

1. **La URL vale para pintar ahora.** No se guarda en `localStorage`, no se
   guarda en un estado propio del componente, no se comparte y no se mete en un
   enlace que alguien pueda copiar. Vive dentro de la entidad que la trajo y
   caduca con ella.
2. **La galería tiene que tolerar que una imagen caduque en pantalla.** No es un
   caso raro: es la pestaña que se queda abierta veinte minutos, que es
   exactamente lo que pasa cuando alguien deja el portátil y vuelve. El `onError`
   del `<img>` pinta el marcador y ofrece recargar; recargar es `onStale`, que
   vuelve a pedir la entidad.
3. **Recargar no es reintentar la imagen.** Volver a pedir la misma URL caducada
   da el mismo fallo. Lo que hay que pedir de nuevo es **la entidad**, que es
   quien trae la firma.

Y una nota sobre lo que hay hoy: los `queryKey` de las vistas no incluyen el
access token y la renovación de sesión **no invalida la caché** de TanStack
Query. El `staleTime` de 30 s de
[`App.tsx`](../../../../frontend/src/App.tsx) hace que casi cualquier remontaje o
refoco refresque la entidad —y con ella las URL—, pero «casi» no es «siempre»: la
pestaña que se queda quieta es justo el caso que falla. Por eso el punto 2 es una
exigencia del componente y no una precaución teórica.

## Los dos caminos de descarga, que no son intercambiables

Es lo que más fácil se implementa al revés, y la
[ADR-005](../../../common/architecture/decisions/ADR-005-local-file-storage.md)
lo decidió con motivo.

| | Imagen | Documento |
|---|---|---|
| Cómo llega | `<img src>` nativo, con la URL firmada que ya trae la entidad | `GET /api/v1/files/{id}/content`, con la cabecera `Authorization` |
| Quién autoriza | La firma, verificada por nginx sin preguntar a la aplicación | La aplicación, en cada petición |
| Desde dónde | El dominio de ficheros, distinto al de la aplicación | El mismo |
| Qué se conserva | `loading="lazy"`, la caché del navegador y el pintado progresivo | Nada de eso, y no hace falta |

**Una imagen no se descarga con `fetch()` para pintarla desde un blob.** Está
descartado y por escrito: hacerlo obliga a renunciar a la carga diferida, al
`srcset` y a la caché justo en la pantalla donde más se nota, que es una rejilla
de miniaturas en un móvil. La única razón para haber montado la URL firmada era
poder usar un `<img>` normal.

**Un documento sí.** Descargar una factura es un clic, y un clic ya es
JavaScript: ahí no hay ninguna razón para renunciar a comprobar el hogar en cada
petición. El camino es pedirlo con la cabecera, construir un objeto de URL local
con la respuesta, disparar la descarga y **liberarlo después**. No es una
contradicción con lo anterior: son dos riesgos distintos y por eso hay dos
caminos. Lo que viaja por el segundo es la factura con nombre y dirección; lo que
viaja por el primero es una foto de un estante ya recodificada y sin EXIF.

Un matiz de hoy: la ADR menciona `srcset` entre lo que se conserva, y **solo hay
una miniatura, de 320 px**, así que de eso no se aprovecha nada todavía. Lo que
sí se aprovecha desde el primer día es `loading="lazy"` y la caché.

**Y el PDF no se incrusta.** Ni `<embed>`, ni `<iframe>`, ni un visor dentro de
la aplicación: se descarga. El fichero se sirve siempre como adjunto y desde otro
dominio, precisamente para que no comparta origen con la sesión.

## Quitar: dos operaciones distintas con la misma etiqueta

Conviene saberlo antes de escribir el botón, porque la asimetría está en el
contrato y no se ve mirando la pantalla.

| Qué se quita | Llamada | Qué pasa con el fichero |
|---|---|---|
| Un documento | `DELETE /documents/{id}` | La misma transacción lo marca para su retirada y **libera la cuota en el acto** |
| Una foto | `PATCH` de la entidad con `photoFileId: null` | **Desadjunta sin borrar**: el fichero sigue vivo y sigue ocupando cuota |

Es decir: quitar una foto y no hacer nada más deja un fichero huérfano pagando
gigabyte. Hace falta un `DELETE /files/{id}` detrás —que responde `409` con
`FILE_IN_USE` si algo lo referencia todavía, y ese es el orden correcto: primero
desadjuntar, luego borrar—. Si la confirmación de quitar una foto ofrece
conservarla o borrarla, es una decisión de la pantalla; lo que no puede es
ignorar el asunto.

Quitar es destructivo, así que va con confirmación, nombrando el objeto y su
consecuencia, como pide
[`look-and-feel.md`](../../product-design/look-and-feel.md). No hay `Dialog`
todavía.

## Comportamiento responsive y con contenido extremo

- **A 375 px caben tres columnas** de unos 106 px: 375 menos los 16 px de
  `px-gutter` a cada lado, menos dos separaciones de 12. Muy por encima de los
  44 px de objetivo táctil, así que la celda entera es el objetivo y no hace
  falta añadir nada.
- **La celda no pasa de 160 px de lado**, y el motivo es físico: la miniatura son
  320 px, así que a partir de 160 px de CSS en una pantalla de densidad doble se
  está ampliando un `bitmap` y se ve blanda. En ultrawide lo que crece es el
  número de columnas, no el tamaño del cuadro; el sobrante se reparte en margen,
  que es la regla de [`space.md`](../foundations/space.md).
- **La imagen nunca deforma**: `object-cover` con encuadre centrado, que es lo
  que fija la dirección visual para toda la fotografía del producto. Una foto
  apaisada se recorta, no se aplasta.
- **Nombre largo**: una línea truncada, con el nombre completo en el `title` y en
  el nombre accesible de la celda. Dos líneas multiplicarían la altura por doce
  celdas.
- **Muchos ficheros**: por encima de doce se corta y se ofrece ver el resto. `GET
  /documents` pagina con la misma forma que el resto de colecciones del contrato
  —`page`, `size`, `total`— y no hay todavía nada que pinte una paginación (ver
  [`listing.md`](../patterns/listing.md)).
- **Ninguna animación al entrar.** Doce miniaturas apareciendo escalonadas es
  justo lo que [`motion.md`](../foundations/motion.md) llama movimiento que no
  explica ningún cambio.

## Teclado, foco, semántica y anuncios asistivos

- **Un `<ul>` de `<li>`**, con un `<button>` por celda. Es una lista de cosas, y
  un lector de pantalla que anuncia «lista de 4 elementos» ya está dando el dato
  que la rejilla da a la vista.
- **Nada de `role="grid"` ni de foco itinerante.** Un `grid` ARIA obliga a
  implementar el recorrido con flechas y a que solo una celda esté en el orden de
  tabulación; para cuatro o doce elementos es más máquina de la que hace falta y
  se rompe más fácil de lo que ayuda. Cada celda es una parada normal del
  tabulador.
- **El foco lo pone la capa base.** Como el objetivo es un `<button>` de verdad,
  aquí no hay ninguna excepción que hacer — a diferencia de
  [`UploadField`](upload-field.md), donde el elemento enfocable está oculto.
- **El nombre accesible de la celda lo lleva el botón**, no la imagen: «Abrir
  factura-taladro.pdf». La miniatura va con `alt=""` **porque el nombre ya está
  escrito al lado** y anunciarlo dos veces es ruido. La regla de
  `iconography.md` —«`alt` con el nombre del asset»— es para la foto principal de
  una ficha, que va sola y sin texto al lado; no es contradicción, son dos sitios
  distintos.
- **La rejilla se nombra con `aria-label`**, que es lo que distingue «Documentos
  de Taladro Bosch» de «Documentos de Sierra circular» cuando hay dos en la misma
  pantalla.
- **El marcador de un PDF y el de una imagen rota se distinguen por texto**, no
  solo por icono: el primero dice el tipo, el segundo dice que no se pudo cargar.
- **El resultado de recargar se anuncia** en la región `role="status"` de la
  pantalla. Que las imágenes vuelvan a aparecer no es información para quien no
  las ve.

## Ejemplos correctos, antiusos y evidencias de prueba

Correcto — los documentos de un asset, en su ficha:

```tsx
<FileGallery
  label={`Documentos de ${asset.name}`}
  items={documents.data?.items.map(toGalleryItem) ?? []}
  onOpen={(item) => downloadFile(item.fileId, item.name)}
  onRemove={(item) => confirmRemoval(item)}
  onStale={() => queryClient.invalidateQueries({ queryKey: ['asset', asset.id] })}
  emptyAction={<Button variant="secondary">Añadir un documento</Button>}
/>
```

`onStale` invalidando la consulta de la entidad es el patrón entero: la imagen
caducada no se arregla en la imagen, se arregla volviendo a leer quien la trajo.

Antiusos:

| Antiuso | Por qué |
|---|---|
| Descargar la miniatura con `fetch()` y pintarla desde un blob | Descartado con motivo en la ADR-005: pierde `loading="lazy"`, `srcset` y la caché |
| Guardar `thumbnailUrl` en el estado, en un contexto o en `localStorage` | Caduca en quince minutos y deja de servir. Se pinta y se olvida |
| Reintentar la misma URL cuando la imagen falla | La firma ya no vale. Lo que se vuelve a pedir es la entidad |
| Dejar un hueco roto en el PDF | Un PDF **no tiene** miniatura: eso no es un fallo, es el caso normal, y tiene su marcador |
| Un rectángulo gris de marcador | La dirección visual lo prohíbe expresamente: el hueco lleva icono sobre `surface-sunken` |
| Incrustar el PDF en un `<iframe>` | No se muestra dentro de la aplicación: se descarga |
| Sombra o radio de tarjeta en la celda | Doce sombras son una textura, y el cuadro se delimita con `border-subtle` |
| Hacer pulsable solo un icono de la esquina | La celda entera es el objetivo, igual que la fila entera en un listado |
| Ampliar la celda por encima de 160 px | Se está ampliando una miniatura de 320 px y se nota |
| Copiar una URL firmada para compartirla | Lleva credencial dentro y caduca. Compartir un fichero no está en el alcance de la Fase 1 |

Evidencias de prueba, en
[`files.test.tsx`](../../../../frontend/src/routes/files.test.tsx): que un PDF
pinta su marcador y **no** un `<img>` con `src` vacío, que quitar un fichero
refresca también la cuota, y que el `409` de un fichero en uso dice qué hacer.

Lo que se dijo al escribir la ficha y **sigue sin cubrirse**:

- Que el `onError` de una imagen dispara la invalidación de la entidad y no una
  segunda petición de la misma URL. `onStale` está cableado y nada lo mide.
- Que la celda es alcanzable y activable con teclado, y que **su nombre accesible
  incluye el nombre del fichero** — ver «Lo que falta», porque hoy no lo incluye
  en un caso.
- Que la rejilla vacía pinta el vacío con su acción.

Ojo con `jsdom`: no carga imágenes, así que ni `onLoad` ni `onError` se disparan
solos. Se prueban emparejando el evento a mano, y la comprobación de verdad —la
imagen que caduca en pantalla— es del recorrido con Playwright del Hito 4.

## Estado de implementación y enlace al componente real

**Implementado.** Vive en
[`files.tsx`](../../../../frontend/src/ui/files.tsx) y no en
[`primitives.tsx`](../../../../frontend/src/ui/primitives.tsx), que es donde esta
ficha lo situaba antes de existir: el fichero se partió, y esta pieza se fue con
`UploadField` y `Avatar` porque las tres llegaron con el Hito 3.

Lo usan dos pantallas y con dos formas distintas de leerlas, que es justo la
frontera que esta ficha argumenta: la documentación de un asset en
[`assets.tsx`](../../../../frontend/src/routes/assets.tsx) —lo que cuelga de una
cosa— y el archivo del hogar en
[`storage.tsx`](../../../../frontend/src/routes/storage.tsx), que es el gigabyte
entero ordenado por tamaño.

### Lo que falta

**Dos entradas se resolvieron sin que nadie las tachara**, y se retiran diciendo
cómo: la celda **sí** tiene dónde vivir —las dos pantallas comparten el
componente entero y no solo la celda, así que `FileTile` no ha hecho falta—, y
descargar un documento **está escrito**, aunque no por donde esta ficha esperaba:
va por `window.open` sobre `/api/v1/files/{id}/content`, no por `client.ts`. Eso
último deja en pie la mitad técnica del apunte, que se conserva más abajo.

Y una entrada nueva, que es un defecto y no una carencia:

- **La celda de un PDF no tiene nombre accesible.** El `<button>` de la celda
  toma su nombre del `alt` de la miniatura, y un PDF no tiene miniatura: se pinta
  un icono `aria-hidden` y el botón se queda **mudo**. Quien navega con lector de
  pantalla oye «botón» y nada más, justo en la rejilla donde todo son facturas y
  manuales. Se arregla con un `aria-label` en el botón, y **hace falta también la
  prueba**, porque esto ha pasado desapercibido por una razón que conviene saber:
  **la pantalla «Archivo» no está en la pasada sistemática de accesibilidad** —no
  aparece en `PHASE_TWO_SCREENS` ni tiene una llamada suelta a `checkAccessibility`
  en el recorrido vertical—, así que axe no la ha mirado nunca.
- **No hay visor de imagen.** Abrir una foto a tamaño completo pide un `Dialog` o
  una hoja, y no hay ninguno de los dos. Mientras tanto, abrir una imagen es
  descargarla, que funciona pero no es lo que se espera de tocar una foto.
- **No hay `Skeleton`**, así que la primera carga de la rejilla no puede pintar
  su forma real y cae en un `Spinner`, que es justo lo que
  [`spinner.md`](spinner.md) dice que no toca.
- **No hay paginación**, así que una entidad con muchos documentos se corta sin
  indicio. Es el mismo hueco que arrastra `listing.md`.
- **No hay diálogo de confirmación** para quitar, y quitar es destructivo.
- **Reordenar y elegir la foto principal no están resueltos.** El contrato tiene
  un `photoFileId` por entidad y una lista de documentos sin orden: si algún día
  se quiere «esta es la portada», es una pregunta para el contrato antes que para
  la interfaz.
- **La descarga no pasa por `client.ts`, y eso sigue teniendo consecuencias.**
  `readResponse` hace `response.json()` siempre, así que una respuesta binaria no
  cabe por ahí sin tocarlo; de momento se esquiva con `window.open`, que funciona
  y **renuncia a la cabecera `Authorization`**: la descarga viaja con la cookie de
  sesión que no existe, así que depende de que el endpoint acepte la petición
  como navegación. El día que haga falta descargar con progreso, o renombrar el
  fichero al vuelo, esto vuelve.

## Referencias

- [`../README.md`](../README.md): la ficha mínima de un componente.
- [`upload-field.md`](upload-field.md): lo que llena esta rejilla.
- [`avatar.md`](avatar.md): la otra imagen del producto.
- [`patterns/listing.md`](../patterns/listing.md): la colección, sus tres vacíos
  y su paginación. **Es lo que hay que leer antes que esto.**
- [`foundations/density.md`](../foundations/density.md) y
  [`foundations/iconography.md`](../foundations/iconography.md): la prohibición
  de sombra y el marcador que no es un rectángulo gris.
- [`ADR-005`](../../../common/architecture/decisions/ADR-005-local-file-storage.md)
  y [`file-storage.md`](../../../backend/architecture/file-storage.md): los dos
  caminos de descarga y la URL firmada.
- [`openapi.yaml`](../../../../openapi.yaml): `StoredFile`, `Document`,
  `photoThumbnailUrl` y qué hace `photoFileId: null`.

## Historial de cambios

| Fecha | Cambio | Autor |
|---|---|---|
| 2026-08-20 | **La ficha se pone al día con el código.** Decía «Previsto. No existe» de un componente construido en el Hito 3, y su «Lo que falta» arrastraba dos entradas resueltas: la celda compartida —que acabó siendo el componente entero, así que `FileTile` no hizo falta— y la descarga de un documento, que está escrita aunque no por donde esta ficha esperaba. Las evidencias de prueba pasan de «ninguna» a tres, con las tres que siguen faltando nombradas. **Y aparece un defecto que el repaso destapó**: la celda de un PDF **no tiene nombre accesible** —el `<button>` lo toma del `alt` de la miniatura y un PDF no tiene—, que ha pasado desapercibido porque **la pantalla «Archivo» no entra en la pasada sistemática de axe**. Se anota aquí y no se arregla: es código y prueba, no documentación. | Equipo DRP |
| 2026-08-13 | Creación de la ficha al arrancar el Hito 3. El componente está **previsto**. Se documenta por qué la galería es componente y no patrón, y dónde queda su frontera con el listado. | Equipo DRP |
