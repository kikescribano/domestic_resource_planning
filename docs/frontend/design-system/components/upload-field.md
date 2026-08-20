# UploadField

| Campo | Valor |
|---|---|
| Estado | Implementado |
| Responsable | Equipo DRP |
| Ámbito | frontend |
| Última revisión | 2026-08-20 |

> **Esta ficha se escribió antes que el componente**, como especificación, y el
> Hito 3 lo construyó siguiéndola: vive en
> [`files.tsx`](../../../../frontend/src/ui/files.tsx) y no en
> [`primitives.tsx`](../../../../frontend/src/ui/primitives.tsx), porque trae
> estado propio y una petición en curso. Los siete estados están, el séptimo
> incluido — y desde el **Hito 2 del cierre de huecos** son **ocho**: la
> conversión de HEIC ([ADR-014](../../../common/architecture/decisions/ADR-014-heic-conversion.md))
> añade un hueco que va **antes** del primer byte. Lo que sigue sin construirse
> está en [Lo que falta](#lo-que-falta), no disimulado.

## Propósito y situaciones de uso

Elegir un fichero del dispositivo, **subirlo** con `POST /api/v1/files` y
devolverle a la pantalla el `StoredFile` que la API responde. Nada más: es un
campo de formulario que produce un `fileId`.

Lo que **no** hace es adjuntarlo, y esa frontera es la decisión que da forma a
todo lo demás. Adjuntar es un paso aparte y posterior —`POST /documents` con
`fileId`, o un `PATCH` sobre el asset, el artículo o la ubicación con
`photoFileId`—, precisamente para que el usuario pueda estar subiendo la foto
mientras sigue rellenando el resto del formulario. De ahí el estado que hay que
tener presente al escribirlo: **fichero ya subido, formulario todavía sin
enviar**, que es lo normal y no una anomalía.

Se usa en:

| Situación | Qué produce | Quién lo adjunta después |
|---|---|---|
| Foto de un asset, de un artículo o de una ubicación | `fileId` de una imagen | El `POST`/`PATCH` de la entidad, en `photoFileId` |
| Documento de un asset o de un artículo —factura, garantía, manual— | `fileId` de imagen o PDF | `POST /documents` con `fileId` y `type` |

**No cubre el avatar**, aunque se le parezca: `PUT /api/v1/users/me/avatar` sube
y sustituye en una sola operación, tiene otro tope —1 MB—, no admite PDF y no
consume la cuota del hogar. Es otra anatomía y tiene su ficha:
[`avatar.md`](avatar.md).

Tampoco cubre el documento que **vive fuera**: `DocumentInput` acepta `url` o
`fileId`, y son excluyentes. Un enlace a la web del fabricante es un
[`Field`](field.md) de tipo `url`, no una subida.

## Anatomía, variantes y estados

Una columna, con la separación de un campo (`gap-1.5`), y cinco piezas: **las
tres primeras están siempre y las dos últimas aparecen con el fichero**.

1. **El disparador** — un `<label>` con aspecto de botón secundario, de 44 px de
   alto (`min-h-touch`), con su icono y su texto («Elegir foto», «Añadir
   documento»). Es el objetivo táctil.
2. **El `<input type="file">`**, oculto a la vista pero **no al foco ni al árbol
   de accesibilidad** (ver [Teclado](#teclado-foco-semántica-y-anuncios-asistivos)).
3. **La pista** — `text-caption text-ink-muted`: qué tipos y qué tope. Se pinta
   siempre, no solo al fallar: es más barato decirlo antes que explicarlo después.
4. **La fila del fichero** — miniatura o icono de tipo, nombre, tamaño legible y
   la acción de la derecha, que es **Cancelar** mientras sube y **Quitar** cuando
   ha terminado.
5. **La barra de progreso** y, bajo ella, **el mensaje de error** con icono y
   texto, exactamente como lo pinta [`Field`](field.md).

| Rasgo | Valor | Token |
|---|---|---|
| Alto del disparador | 44 px | `min-h-touch` |
| Radio del disparador y de la miniatura | 8 px | `rounded-md` |
| Cuadro de la miniatura | 40 × 40 px, 1:1 | `size-10`, `object-cover` |
| Fondo del hueco sin miniatura | La superficie hundida | `bg-surface-sunken` |
| Alto de la barra | 4 px | `h-1` |
| Canal de la barra | Superficie hundida | `bg-surface-sunken` |
| Relleno de la barra | El acento | `bg-accent` |
| Porcentaje y tamaño | 13 px, tinta secundaria | `text-caption text-ink-muted` |
| Nombre del fichero | 14 px, tinta principal | `text-body-sm text-ink` |
| Mensaje de error | 13 px, con icono | `text-caption text-danger` |

### Los ocho estados

El hito enumera seis —reposo, seleccionado, subiendo, terminado, error y
cancelado— y al escribir la anatomía aparece un séptimo, **Procesando**, que no
es un adorno: es el hueco real entre el último byte enviado y la respuesta del
servidor. El octavo llegó con la conversión de HEIC y es **el hueco simétrico**:
**Convirtiendo**, entre elegir el fichero y el primer byte. Hay que pintarlos los
ocho; saltarse uno es lo que produce el campo que parece colgado. Ninguno se dice
**solo** con color: cada uno tiene texto propio.

| Estado | Qué se ve | Qué se puede hacer |
|---|---|---|
| **Reposo** | Disparador y pista | Elegir un fichero |
| **Seleccionado** | Nombre y tamaño, sin barra | Nada: dura lo que tarda la comprobación local en pasar y el `XMLHttpRequest` en arrancar |
| **Convirtiendo** | Barra indeterminada, «Convirtiendo la foto…» | Nada. Solo aparece con un HEIC, y ocurre **en esta máquina**: ver más abajo |
| **Subiendo** | Barra al *n* %, porcentaje en texto y **Cancelar** | Cancelar, que aborta la petición y **devuelve el foco al disparador** |
| **Procesando** | Barra completa e indeterminada, «Procesando…» | Nada. Ver más abajo: no es lo mismo que terminado |
| **Terminado** | Miniatura o icono, nombre, tamaño y **Quitar** | Quitar, que devuelve a reposo |
| **Error** | El fichero elegido, el mensaje y qué hacer | Reintentar, elegir otro, o —si es la cuota— liberar espacio |
| **Cancelado** | Vuelta a reposo, con una línea que dice que se canceló | Elegir otro fichero |

> **Y las ocho filas describen ahora lo que el componente hace**, que hasta el
> 2026-08-20 no era cierto: **Cancelar** no existía y **Cancelado** no se
> alcanzaba. La única espera que sigue sin poder cortarse es **Convirtiendo**, y
> no por falta de botón — ver más abajo.

**El 100 % no es el final, y confundirlos es el fallo clásico de esta pieza.**
`upload.onprogress` mide **bytes enviados**; cuando llega a `total`, al servidor
todavía le queda inspeccionar el contenido, recodificar la imagen —que es lo que
borra el EXIF con las coordenadas GPS de la casa—, generar la miniatura y cerrar
la fila (ver [`file-storage.md`](../../../backend/architecture/file-storage.md),
5.8.3). Una barra clavada en 100 % durante dos segundos se lee como una
aplicación colgada. Por eso hay un estado **Procesando** entre el 100 % y el
`201`, con la barra indeterminada y su etiqueta.

**Y el otro hueco está antes del primer byte, no después del último.** Cuando lo
elegido es un HEIC hay que decodificarlo **aquí**, en el dispositivo de quien
sube, antes de que empiece la subida: 913 ms medidos en un escritorio con una foto
de 12 MP, y varias veces más en un móvil (ADR-014). Se dice «Convirtiendo la
foto…» y no «Procesando» a propósito, porque no es lo mismo ni ocurre en el mismo
sitio, y quien mira una barra quieta merece saber cuál de las dos esperas es.

La barra va **indeterminada**, como en Procesando y por el mismo motivo elevado a
regla: el decodificador no ofrece porcentaje —devuelve la imagen o no la
devuelve—, así que dar uno sería inventarlo.

**Y es el único de los ocho estados sin salida**, que es la asimetría que hay que
tener presente: subiendo se cancela y convirtiendo no, porque el decodificador no
ofrece por dónde abortar. Por eso el botón **no aparece** aquí en lugar de
aparecer y no hacer nada, que es la otra forma de resolverlo y la que convierte
una limitación en una mentira.

**Un fichero subido y no adjuntado no es basura que haya que recoger.** Ocupa
cuota desde que se reserva y no se puede adjuntar mientras `uploadedAt` siga a
nulo; el proceso diario retira las dos cosas —la subida que se quedó a medias y
la que terminó y nunca se adjuntó, a las 24 h—. Cancelar un formulario deja como
mucho una fila que se limpia sola: el componente **no** debe intentar borrarla en el
`unmount` —no hay garantía de que ese código llegue a ejecutarse, y una petición
de borrado en el camino de salida es exactamente lo que falla en el móvil que se
bloquea—.

No hay variantes de aspecto. Sí hay dos configuraciones, por lo que se acepta:
foto (solo imagen) y documento (imagen o PDF).

## API pública

Prevista. Es la propuesta de esta ficha, no un contrato ya escrito:

```ts
interface UploadFieldProps {
  label: string
  accept?: 'image' | 'document'
  hint?: string
  error?: string
  onUploaded: (file: StoredFile) => void
  onCleared: () => void
  disabled?: boolean
}
```

| Propiedad | Qué hace |
|---|---|
| `label` | **Obligatoria**, como en `Field`. El disparador siempre dice qué se está subiendo |
| `accept` | Decide los dos textos —pista y error— y el atributo `accept` del `<input>`. **No valida nada** |
| `hint` | Sustituye a la pista por defecto cuando la pantalla tiene algo más concreto que decir |
| `error` | El error que viene de fuera —de la operación de adjuntar, no de la subida—. El de la subida lo lleva el componente por dentro |
| `onUploaded` | Se llama **una vez**, con el `StoredFile` entero. Quien lo recibe se queda con `id`; lo demás sirve para pintar |
| `onCleared` | Al quitar o al cancelar. La pantalla borra el `fileId` que tuviera guardado |
| `disabled` | Mientras el formulario se envía |

Dos cosas que **no** son propiedades y conviene decir por qué:

- **No hay `multiple`.** El contrato es un fichero por petición, y admitir varios
  obliga a una cola con su progreso por elemento, sus errores individuales y el
  caso de la cuota que se agota a mitad. En el Hito 3 se sube de uno en uno; la
  cola está en [Lo que falta](#lo-que-falta).
- **No hay `value`.** El componente es el dueño de su propio estado de subida. Lo
  que la pantalla guarda es el `fileId` que le llega por `onUploaded`.

### Por qué esto no puede pasar por `request()`

**`fetch()` no informa del progreso de subida.** Da el de bajada y nada más, así
que una barra construida sobre él sería una animación inventada — y una barra que
miente es peor que no tener barra. El progreso real sale de `XMLHttpRequest` y su
evento `upload.onprogress`, con `event.lengthComputable`, `event.loaded` y
`event.total`.

Eso tiene una consecuencia estructural que hay que resolver **antes** de escribir
el componente: [`client.ts`](../../../../frontend/src/api/client.ts) está
construido entero sobre `fetch`, y con él la renovación de sesión ante un `401`
con código `UNAUTHORIZED`. Un `XMLHttpRequest` que se salte ese módulo se salta
también la cabecera `Authorization` y la renovación, y el síntoma sería el peor
posible: **la subida de 20 MB que falla con un `401` justo al terminar**, después
de que el usuario haya esperado. La subida necesita su propia función dentro de
`client.ts`, con la misma política de renovación y reintento único.

Y un detalle que se paga caro por descuido: con `FormData` **no se pone
`Content-Type` a mano**. Lo pone el navegador con el `boundary` que ha generado;
escribirlo rompe el `multipart` y produce un `400` sin más pista.

## Comportamiento responsive y con contenido extremo

- **A 375 px** el disparador ocupa el ancho de la columna. La fila del fichero es
  `flex` con `min-w-0` en el bloque del nombre, que es lo que permite truncar sin
  empujar fuera la acción de la derecha.
- **Nombre larguísimo** —`IMG_20260813_193045_HDR_edit_final.jpeg` es lo normal
  en una cámara—: se trunca a una línea con el nombre completo en `title`. CSS no
  sabe truncar por el medio, así que **la extensión se pierde de vista**; el tipo
  ya lo dice el icono de la fila, que es información más fiable que la extensión.
- **La barra no cambia de altura** entre estados, y el bloque de progreso reserva
  su sitio desde que se elige el fichero. Es la regla de
  [`look-and-feel.md`](../../product-design/look-and-feel.md) —«sin mover la
  maquetación»— aplicada donde más se nota, porque aquí el contenido cambia solo,
  sin que el usuario toque nada.
- **La barra se anima con `transform: scaleX()`**, no con `width`:
  [`motion.md`](../foundations/motion.md) reserva la animación a opacidad y
  transformación. Duración `--duration-fast` (140 ms) y `--ease-standard`. Con
  `prefers-reduced-motion: reduce` la transición cae a 1 ms y la barra salta al
  valor nuevo: no se pierde nada, porque el dato está también en el texto.
- **En escritorio no crece.** El campo vive dentro de la columna de formulario
  (`--container-form`, 544 px), que no se estira.
- **Arrastrar y soltar es un extra de escritorio, nunca la única vía.** Si se
  añade, el disparador sigue estando y con el mismo objetivo de 44 px: no hay
  forma de arrastrar un fichero con el pulgar.

## Teclado, foco, semántica y anuncios asistivos

- **El `<input type="file">` se oculta a la vista, no al foco.** `display:none` y
  el atributo `hidden` lo sacan del orden de tabulación y del árbol de
  accesibilidad: hay que ocultarlo con la técnica de solo-lectores —posición
  absoluta, 1 px, recortado— y atarlo a su `<label>` con `htmlFor`/`id`, igual
  que `Field`. Así sigue funcionando con teclado, lo anuncia el lector de
  pantalla como «botón de selección de archivo» y las pruebas lo encuentran con
  `getByLabelText`.
- **El anillo de foco es el único punto donde este componente choca con una regla
  del sistema.** La regla 2 de [`components/`](README.md) dice que el foco no se
  declara en un componente porque vive en la capa base de
  [`index.css`](../../../../frontend/src/index.css) — pero el elemento enfocable
  aquí es invisible, así que su anillo no se ve. Hay que llevarlo al `<label>`
  con `has-[:focus-visible]`, y eso **es** declarar foco en un componente. Está
  anotado como decisión abierta: o la capa base gana una regla para este patrón,
  o esta es su primera excepción documentada. Lo que no vale es dejar el campo
  sin foco visible.
- **La barra es un `role="progressbar"`** con `aria-valuemin="0"`,
  `aria-valuemax="100"`, `aria-valuenow` y `aria-valuetext="35 %"`. Mientras está
  en **Procesando** se le quita `aria-valuenow`, que es como se declara un
  progreso indeterminado.
- **Un `progressbar` no anuncia solo, y es lo correcto.** Anunciar cada
  actualización sería un lector de pantalla contando de uno en uno hasta cien. Lo
  que se anuncia son los **cambios de estado**, en una región `role="status"`
  —*polite*— con una frase: «Foto subida», «Subida cancelada». El porcentaje se
  consulta cuando se quiere, no se recita.
- **El error de la subida se pinta bajo el campo**, con `aria-invalid` en el
  `<input>` y `aria-describedby` apuntando al mensaje: es el mismo mecanismo de
  `Field` y hay que reutilizarlo, no reinventarlo. La excepción es la cuota, que
  no es un problema del fichero elegido (ver más abajo).
- **Cancelar devuelve el foco al disparador.** Si se queda en el botón que acaba
  de desaparecer, el foco cae al `<body>` y quien navega con teclado se queda sin
  sitio.

## Los tres errores, y por qué uno no es como los otros dos

La tabla de [`feedback.md`](../patterns/feedback.md) reparte las respuestas de la
API en tres filas. Estos tres códigos **añaden una cuarta situación** que ahí no
estaba: un error de petición que no trae `details` y que, sin embargo, se pinta
en el sitio del campo, porque el atributo culpable es el único que hay.

| Código | Estado | Qué ha pasado | Dónde se pinta | Qué se ofrece |
|---|---|---|---|---|
| `FILE_TOO_LARGE` | 413 | Pasa de 25 MB | Bajo el campo | Elegir otro fichero. El mensaje dice cuánto pesa el elegido **y** cuál es el tope |
| `FILE_TYPE_NOT_ALLOWED` | 415 | El tipo **real** no está en la lista blanca | Bajo el campo | Los cuatro tipos admitidos, dichos en cristiano: JPEG, PNG, WebP y PDF |
| `STORAGE_QUOTA_EXCEEDED` | 409 | El hogar ha agotado su gigabyte | **`Notice tone="danger"`**, junto a la acción | Enlace a los ficheros del hogar, empezando por los que no cuelgan de nada |

**El tercero es de otra clase y hay que tratarlo como tal.** Los dos primeros se
arreglan eligiendo otro fichero; la cuota no se arregla eligiendo nada, se
arregla borrando. Por eso no va bajo el campo —donde el mensaje se lee como «este
fichero no vale»— sino en un aviso junto a la acción, que es lo que
`feedback.md` reserva para «una regla del hogar lo impide». Y por eso el mensaje
tiene que llevar la salida: `GET /files?attached=false` devuelve justo lo que se
puede borrar sin perder nada, y `GET /files` viene ordenado por tamaño
descendente, que es la pregunta real cuando la cuota se agota.

Hay un cuarto, menos frecuente: la subida está limitada por frecuencia y puede
responder `429`. Se pinta como los demás fallos de operación, con el `Retry-After`
que `ApiError` ya guarda en `retryAfterSeconds`.

### Avisar antes, no después

`GET /storage` devuelve `usedBytes`, `quotaBytes` y `maxFileBytes`, y existe
exactamente para esto: **saber que no cabe antes de gastar la red**. Dos
comprobaciones locales, las dos baratas y ninguna sustituta de la del servidor:

1. `file.size > maxFileBytes` → no se envía nada; se pinta el mensaje de
   `FILE_TOO_LARGE` directamente. Subir 40 MB para que los rechacen en el
   servidor es un minuto de espera regalado.
2. `usedBytes + file.size > quotaBytes` → se avisa antes de arrancar, con el
   mismo texto que el `409`.

Y por encima del 90 % de ocupación, el campo enseña cuánto queda **antes** de que
nadie elija nada. Los valores llegan del mismo `GET /storage`, así que no cuesta
una petición nueva por campo si la pantalla ya lo tiene.

**El medidor de cuota no es un componente aparte**, y por eso no tiene ficha
propia: es esta misma barra con dos cambios. Uno es semántico y no es menor —un
nivel que no cambia solo no es una tarea en curso, así que su papel es `meter` y
no `progressbar`—, y el otro es el color, que pasa a `warning` por encima del
90 % y a `danger` al llegar al tope, siempre con la cifra escrita al lado, porque
nada se dice solo con color. La pantalla de almacenamiento del hogar reutiliza la
misma pieza con esa configuración.

### El `accept` es una comodidad, nunca una validación

Merece su apartado porque es el malentendido más caro de esta pieza.

`accept="image/jpeg,image/png,image/webp,application/pdf,image/heic,image/heif,.heic,.heif"`
solo **preselecciona el filtro del selector de ficheros del sistema**. No impide elegir otra cosa —el
usuario puede cambiar el filtro a «todos los archivos»—, algunos selectores de
móvil lo ignoran, y el `type` que el navegador pone en el `File` es una
suposición basada en la extensión. Renombrar un `.exe` a `.jpg` lo cuela por ahí
sin despeinarse.

**Quien decide el tipo es el servidor, inspeccionando el contenido**, y por eso
el `415` puede llegar aunque el `accept` estuviera puesto y el nombre acabara en
`.png`. La comprobación local de tipo, si se hace, es para no gastar una subida
inútil; nunca para dar por bueno un fichero.

**Y HEIC está en esa lista sin estar en la lista blanca**, que es la distinción
que hay que tener clara al leerlo. La lista blanca del servidor sigue teniendo
**cuatro tipos** —JPEG, PNG, WebP y PDF— en los tres sitios donde vive: el
enumerado del dominio, el `CHECK` de `files.content_type` y el contrato. Lo que
HEIC hace es entrar por el `accept` porque **el cliente lo convierte a JPEG antes
de enviarlo** ([ADR-014](../../../common/architecture/decisions/ADR-014-heic-conversion.md)),
así que el servidor no llega a verlo nunca.

Dejarlo fuera del `accept` sería el mismo muro que el `415` con otra cara: el
fichero aparecería en gris en el diálogo y no habría nada que hacer. Van los
tipos **y** las dos extensiones porque `image/heic` no está registrado en todos
los sistemas, y donde no lo está el diálogo solo casa por el final del nombre.

Lo que **no** cambia es la regla de arriba: la conversión es una comodidad, igual
que el `accept`. Detecta por los bytes —la caja `ftyp` y la marca del contenedor,
no la extensión, así que la foto que llega renombrada como `IMG_0042.JPG` también
se convierte— y si aun así llegara un HEIC sin convertir, lo rechaza el servidor
como siempre.

**Si la conversión falla, no se sube el original.** Sería gastar la conexión para
acabar en el mismo `415`, y con el mensaje equivocado: enumeraría los cuatro tipos
admitidos cuando el problema es otro. Sale un error propio —«No se ha podido
convertir esta foto. Vuelve a intentarlo, o súbela en JPEG.»— que además no se
confunde con un fallo de red.

## Ejemplos correctos, antiusos y evidencias de prueba

Correcto — el campo de la foto dentro del formulario de alta de un asset, que
sube ya y adjunta al enviar:

```tsx
<UploadField
  label="Foto"
  accept="image"
  onUploaded={(file) => setPhotoFileId(file.id)}
  onCleared={() => setPhotoFileId(null)}
/>
```

El `photoFileId` viaja después en el `POST /assets`, junto a lo demás. Si el
usuario abandona el formulario, el fichero queda subido y sin adjuntar, y el
proceso diario lo retira: **no hay nada que limpiar desde el cliente**.

Antiusos:

| Antiuso | Por qué |
|---|---|
| Simular el progreso con un temporizador | Una barra que no mide nada miente; con una conexión lenta llega al 100 % y ahí se queda |
| Fiarse del `accept` o de la extensión para dar por bueno un fichero | El tipo lo decide el servidor inspeccionando el contenido. El `accept` solo filtra el diálogo |
| Bloquear el formulario entero mientras sube | La subida existe aparte justamente para poder seguir escribiendo |
| Dar por terminada la subida al llegar al 100 % | Faltan la recodificación, la miniatura y el cierre de la fila. Ese hueco es el estado **Procesando** |
| Pintar `STORAGE_QUOTA_EXCEEDED` bajo el campo | Se lee como «este fichero no vale», y el fichero no tiene la culpa. Va en un aviso, con la salida |
| Ocultar el `<input>` con `hidden` o `display:none` | Lo saca del orden de tabulación: el campo deja de existir para el teclado |
| Borrar el fichero subido al desmontar el formulario | No hay garantía de que ese código se ejecute, y el servidor ya lo recoge a las 24 h |
| Guardar la `url` del `StoredFile` en el estado o en `localStorage` | Caduca en unos quince minutos. Vale para pintar ahora (ver [`file-gallery.md`](file-gallery.md)) |
| Un `Spinner` en lugar de la barra | Hay porcentaje real: enseñarlo es la diferencia entre esperar y no saber |

Evidencias de prueba, en [`files.test.tsx`](../../../../frontend/src/routes/files.test.tsx)
y [`heic.test.ts`](../../../../frontend/src/api/heic.test.ts): que la subida va
por `XMLHttpRequest` y no por `fetch` —con el progreso llegando—, que el `409` de
cuota se traduce a algo que se puede hacer, que el selector ofrece HEIC sin que la
lista blanca del servidor haya crecido, que la conversión detecta por bytes,
renombra a `.jpg` y no sube el original cuando falla, y que **cancelar aborta la
petición, devuelve el campo a reposo y deja el foco en el disparador**. Lo que se
dijo al escribir esta ficha, antes de que existieran:

- `userEvent.upload` sobre el `<input>` localizado por `getByLabelText`, que de
  paso verifica que la atadura etiqueta–campo sobrevive a ocultarlo.
- Los tres errores, con un doble de `XMLHttpRequest` que responda 413, 415 y 409:
  que el 413 y el 415 salen bajo el campo y el 409 en el aviso con enlace.
- Que cancelar aborta la petición, devuelve el campo a reposo y deja el foco en
  el disparador.
- Que el `fileId` llega al `POST` de la entidad **y** que abandonar el formulario
  no dispara ningún `DELETE`.

`jsdom` no implementa el evento `upload.progress`, así que el progreso se prueba
contra el doble, no contra el navegador. La comprobación de que la barra se mueve
de verdad es del recorrido con Playwright del Hito 4.

**Y el reparto vale igual para la conversión**, por una razón más dura: `jsdom` no
tiene `<canvas>` con contexto 2D, ni `Worker`, ni WebAssembly instanciable, y el
decodificador necesita las tres. Allí se dobla el módulo; el decodificador de
verdad lo ejecuta el recorrido vertical en Chromium, con **un HEIC de verdad y no
un JPEG renombrado**.

## Estado de implementación y enlace al componente real

**Implementado.** Vive en
[`frontend/src/ui/files.tsx`](../../../../frontend/src/ui/files.tsx) y no en
[`primitives.tsx`](../../../../frontend/src/ui/primitives.tsx), que es donde esta
ficha lo situaba antes de existir: trae estado propio y una petición en curso, y
ese fichero se había puesto a sí mismo el criterio de «mientras quepa en algo que
se lee de una sentada».

La conversión de HEIC **no está ahí sino en
[`api/heic.ts`](../../../../frontend/src/api/heic.ts)**, invocada desde
`uploadFile`. Es deliberado y está razonado en la
[ADR-014](../../../common/architecture/decisions/ADR-014-heic-conversion.md): hay
dos vías de subida —esta y el avatar, que tiene su propio `<input>`— y ponerla en
el campo dejaría la segunda sin ella.

### Lo que falta

**La lista se había quedado atrás cuatro veces**, y se pone al día en vez de
arrastrar lo que ya está hecho: la función de subida de `client.ts`, los cuatro
códigos de error sin tipar, la conversión de HEIC y **cancelar una subida**
estaban aquí y hoy existen las cuatro. Lo que queda:

- **Cancelar la conversión de HEIC sigue sin poder hacerse**, y no es lo mismo
  que lo anterior: el decodificador no expone ningún punto de aborto, así que esa
  espera se aguanta entera. No es una tarea pendiente sino una propiedad de la
  librería, y cambiarla exige cambiar de librería o partir la decodificación en
  trozos que se puedan abandonar.
- **La reducción de tamaño en el cliente.** Una foto de móvil ronda los 3-8 MB y
  el tope son 25, así que no falla casi nunca; pero recomprimirla a 2000 px antes
  de subir ahorra cuota, ancho de banda y tiempo de espera, y el hogar solo tiene
  un gigabyte. No está decidido si se hace. **Y ahora comparte camino con la
  conversión** —el mismo lienzo, el mismo sitio— sin ser la misma decisión.
- **La cola de varios ficheros.** Adjuntar seis fotos de una vez es la petición
  obvia en cuanto la primera funcione, y el contrato ya obliga a una petición por
  fichero.
- **El anillo de foco del disparador**, con el choque contra la regla 2 descrito
  más arriba.
- **La reanudación.** Una subida cortada por un cambio de red se pierde entera.
  Reanudar exige subida por trozos, que el contrato no contempla.

## Referencias

- [`../README.md`](../README.md): la ficha mínima de un componente.
- [`field.md`](field.md): la atadura etiqueta–campo, el mensaje de error y el
  `aria-describedby` que este componente reutiliza.
- [`file-gallery.md`](file-gallery.md): lo que se ve **después** de subir, y la
  regla de las URL firmadas.
- [`avatar.md`](avatar.md): la otra subida, la que sustituye.
- [`patterns/feedback.md`](../patterns/feedback.md): las tres respuestas de la
  API y qué hace el cliente con cada código.
- [`foundations/motion.md`](../foundations/motion.md): las duraciones y por qué
  la barra se anima con transformación.
- [`ADR-005`](../../../common/architecture/decisions/ADR-005-local-file-storage.md)
  y [`file-storage.md`](../../../backend/architecture/file-storage.md): el camino
  de subida, la lista blanca y la cuota.
- [`openapi.yaml`](../../../../openapi.yaml): `POST /files`, `GET /storage` y el
  esquema `StoredFile`.

## Historial de cambios

| Fecha | Cambio | Autor |
|---|---|---|
| 2026-08-20 | **Cancelar una subida deja de ser especificación.** La ficha lo describía desde su primer día —el botón en la fila del fichero, el estado **Cancelado** y el foco de vuelta al disparador— y el componente no lo tenía: el `AbortController` estaba declarado y nunca se le asignaba nada, así que `cancelled` era inalcanzable. La señal viaja ahora desde `uploadFile` hasta el `XMLHttpRequest`, cuyo gancho `onabort` llevaba puesto desde el Hito 3 de la Fase 1 esperando a que alguien lo llamara. **El botón no aparece en Convirtiendo**, y eso no es un olvido: el decodificador de HEIC no ofrece por dónde abortar, así que enseñarlo ahí convertiría una limitación en una mentira. Se retira el aviso de que dos filas de la tabla eran especificación, y «Lo que falta» pierde su cuarta entrada ya hecha. | Equipo DRP |
| 2026-08-20 | **La conversión de HEIC deja de faltar** (Hito 2 del cierre de huecos, [ADR-014](../../../common/architecture/decisions/ADR-014-heic-conversion.md)). Los estados pasan de siete a **ocho**: el nuevo, **Convirtiendo**, es el hueco simétrico del de Procesando —uno va antes del primer byte y ocurre en el dispositivo de quien sube; el otro, después del último y en el servidor—. El `accept` gana HEIC **sin que la lista blanca crezca**, que es la distinción que su apartado explica. Se pone al día el estado de la ficha, que seguía diciendo «previsto» con el componente construido, y **la lista de «Lo que falta» pierde tres entradas ya hechas** y gana la que faltaba de verdad: **cancelar una subida no está implementado**, aunque esta ficha lo describa desde el primer día. | Equipo DRP |
| 2026-08-13 | Creación de la ficha al arrancar el Hito 3. El componente está **previsto**: no existe nada de esto en el frontend. | Equipo DRP |
