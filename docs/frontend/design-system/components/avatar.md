# Avatar

| Campo | Valor |
|---|---|
| Estado | Implementado |
| Responsable | Equipo DRP |
| Ámbito | frontend |
| Última revisión | 2026-08-20 |

> **Esta ficha se escribió antes que el componente**, como especificación, y el
> Hito 3 lo construyó siguiéndola: vive en
> [`files.tsx`](../../../../frontend/src/ui/files.tsx), y la pantalla de cuenta
> —en [`household.tsx`](../../../../frontend/src/routes/household.tsx)— sube y
> quita el propio. Lo que sigue sin construirse está en
> [Lo que falta](#lo-que-falta).

## Propósito y situaciones de uso

La cara de una persona, en redondo, allí donde el nombre solo no basta para
reconocerla de un vistazo: la lista de personas del hogar, la cabecera del shell,
y —en el Hito 4— quién tiene prestado qué.

Se escribe aparte de [`UploadField`](upload-field.md) y de
[`FileGallery`](file-gallery.md) porque no es una variante de ninguna de las dos,
y las tres diferencias que lo separan son de fondo:

| | Foto o documento | Avatar |
|---|---|---|
| Operación | `POST /files`, y adjuntar después en otro paso | `PUT /api/v1/users/me/avatar`, sube y sustituye de una vez |
| A quién pertenece | Al hogar | A la **identidad**: es de la persona y sobrevive a cualquier hogar |
| Cuota | Suma al gigabyte del hogar | **No suma a ninguna**, y por eso no tiene detrás política de RLS |
| Tope | 25 MB | **1 MB** |
| Tipos | Las cuatro de la lista blanca, PDF incluido | Solo imagen |
| Acumulación | Cada fichero se queda | **Sustituye siempre**: no acumula |

Y la mitad que de verdad justifica la ficha: **el avatar se pinta en sitios donde
no hay ninguna subida**. En una lista de personas es un adorno de 32 px al lado
de un nombre; la subida solo aparece en la pantalla de la cuenta propia. Son dos
anatomías cosidas por el mismo dato, y la de mostrar es la que más se usa.

## Anatomía, variantes y estados

**Un círculo con una de dos cosas dentro**, y nada más:

1. **La imagen**, `<img>` con `object-cover` y `rounded-full`.
2. **Las iniciales**, cuando no hay imagen: una o dos letras del nombre,
   centradas, sobre `bg-accent-soft`.

El redondeo completo es una de las **dos únicas** excepciones que
[`shape-and-elevation.md`](../foundations/shape-and-elevation.md) admite —avatar
y distintivo de estado—; el resto del sistema va de 4 a 24 px.

| Tamaño | Lado | Dónde |
|---|---|---|
| `sm` | 24 px | Dentro de una fila de listado o junto a un texto |
| `md` | 32 px | La lista de personas del hogar |
| `lg` | 40 px | La cabecera del shell |
| `xl` | 96 px | La pantalla de la cuenta, la única donde se puede cambiar |

| Rasgo | Valor | Token |
|---|---|---|
| Forma | Círculo | `rounded-full` |
| Fondo de las iniciales | Acento tenue | `bg-accent-soft` |
| Iniciales | El acento como texto | `text-accent-ink` |
| Encuadre | 1:1, recorte centrado | `object-cover` |
| Borde | Ninguno | — |

**Sin sombra y sin borde.** Un avatar de 24 px con borde y sombra dentro de una
fila es exactamente lo que el presupuesto de calidez de
[`density.md`](../foundations/density.md) prohíbe.

Un aviso sobre el par de color: `accent-ink` sobre `accent-soft` **no está en los
36 pares medidos** de [`accessibility/`](../../accessibility/README.md). Antes de
darlo por bueno tiene que entrar en la auditoría de `check-contrast.py`, o
sustituirse por un par que ya esté medido. La ficha propone el par; no puede
afirmar su ratio.

### Estados

| Estado | Qué se ve |
|---|---|
| Con imagen | La foto |
| Sin imagen | Las iniciales. **Es lo normal**: nadie sube avatar el primer día |
| Sin nombre útil | El icono de persona, para el caso de una invitación aún sin aceptar |
| Imagen caducada o rota | Cae a las iniciales, en silencio |

**El fallo de la imagen cae a las iniciales y no se comenta.** Aquí no hay
recarga ni aviso, a diferencia de la galería: un avatar es decoración de apoyo, y
un mensaje de error de 24 px al lado de un nombre es peor que la inicial. La URL
del avatar subido es firmada y de vida corta, igual que las de la galería, así
que caducar en pantalla es un caso normal y no un fallo. La `avatarUrl` puede ser
además un **enlace externo**, y entonces no caduca nunca: el componente no
distingue ni tiene por qué.

## API pública

Prevista, en dos piezas:

```ts
function Avatar(props: {
  name: string
  src?: string | null
  size?: 'sm' | 'md' | 'lg' | 'xl'
  decorative?: boolean
}): JSX.Element
```

| Propiedad | Qué hace |
|---|---|
| `name` | **Obligatorio.** De él salen las iniciales y el texto alternativo. Sin nombre no hay avatar |
| `src` | La `avatarUrl` de la persona, tal y como vino. Nula es el caso normal |
| `size` | Uno de los cuatro. Por defecto `md` |
| `decorative` | Cierto cuando el nombre está escrito al lado, que es casi siempre |

La subida es aparte y **no la hace este componente**: es `AvatarField`, que vive
en la pantalla de la cuenta y compone `Avatar` con un disparador. Reutiliza el
mecanismo de [`UploadField`](upload-field.md) —selector oculto, comprobación
local de tamaño, progreso con `XMLHttpRequest`— y cambia tres cosas: llama a
`PUT /users/me/avatar`, no produce ningún `fileId` y **la respuesta es `204`**.

Ese `204` tiene una consecuencia que hay que escribir para que no se descubra
depurando: **la respuesta no trae la URL nueva**. Después de subir hay que volver
a leer a la persona para pintarla. Y como el avatar anterior deja de existir, la
imagen vieja se queda en pantalla hasta esa relectura.

## Comportamiento responsive y con contenido extremo

- **No cambia de tamaño con la pantalla.** Lo decide el sitio donde está, no el
  ancho: 32 px en una fila son 32 px a 375 px y a 3440 px.
- **`shrink-0` siempre.** Un círculo dentro de un `flex` con un nombre largo al
  lado se aplasta a un óvalo si no se le dice que no encoja. Es el mismo descuido
  que ya tiene el disco de [`spinner.md`](spinner.md), y aquí se ve más.
- **Iniciales**: una o dos, en mayúscula, tomadas de la primera letra de las dos
  primeras palabras del nombre. Con un nombre de una palabra, una sola. **No se
  parte por comas ni se intenta adivinar el apellido**, que es la clase de lógica
  que falla con la mitad de los nombres del mundo.
- **Nombre sin letras** —solo emoji, o vacío—: se cae al icono de persona en
  lugar de pintar un círculo mudo.
- **El texto de las iniciales escala con el círculo**: `text-caption` en `sm` y
  `md`, `text-body-sm` en `lg`, `text-title` en `xl`. Nunca en serif: la serif no
  entra en una fila de listado, y `sm` y `md` viven ahí.
- **A 96 px la imagen se ve ampliada si el original era pequeño.** No hay
  miniatura de avatar en el contrato —el tope de 1 MB hace de límite—, así que lo
  que se pinta es el original recodificado.

## Teclado, foco, semántica y anuncios asistivos

- **No es interactivo.** No es un botón, no es un enlace y no entra en el orden
  de tabulación. Si lleva a algún sitio, quien lleva es el elemento que lo
  contiene.
- **`alt=""` cuando el nombre está escrito al lado**, que es el caso de la lista
  de personas y de la cabecera. Anunciar «Foto de Marta» justo antes de leer
  «Marta» es ruido, y es el error más común con esta pieza.
- **`alt` con el nombre solo cuando el avatar va solo**: un avatar sin texto
  alrededor sí tiene que decir de quién es.
- **Las iniciales van `aria-hidden` cuando el nombre está al lado.** Un lector de
  pantalla leyendo «M» antes de «Marta» no aporta nada.
- **El fallo a iniciales no se anuncia.** No ha pasado nada que el usuario tenga
  que saber.
- **Contraste**: las iniciales son texto y necesitan 4,5:1; ver el aviso de más
  arriba sobre el par propuesto.

## Ejemplos correctos, antiusos y evidencias de prueba

Correcto — en la lista de personas del hogar, con el nombre al lado:

```tsx
<li className="flex items-center gap-3">
  <Avatar name={user.name} src={user.avatarUrl} size="md" decorative />
  <div className="min-w-0">
    <p className="text-body-sm text-ink">{user.name}</p>
    <p className="text-caption text-ink-muted">{user.email}</p>
  </div>
</li>
```

Antiusos:

| Antiuso | Por qué |
|---|---|
| Contar el avatar en la cuota del hogar | Es de la identidad, no del hogar, y el contrato lo dice expresamente |
| Subirlo con `POST /files` y adjuntarlo después | No hay `fileId` que adjuntar: el `PUT` sustituye y devuelve `204` |
| Guardar la `avatarUrl` en el estado o en `localStorage` | Cuando es un avatar subido es una URL firmada y caduca en quince minutos |
| Pintar un hueco gris cuando no hay foto | Las iniciales identifican; un cuadro gris no |
| `alt` con el nombre teniendo el nombre al lado | Se anuncia dos veces |
| Un avatar cuadrado | El redondeo completo es una de las dos excepciones del sistema, y esta es una de ellas |
| Borde y sombra en un avatar de fila | Prohibido por el presupuesto de calidez |
| Dar por hecho que hay foto tras el `PUT` | La respuesta es `204`: hay que volver a leer a la persona |

Evidencias de prueba: **ninguna, y esta vez la palabra es exacta**. El componente
se monta en varias pruebas —cualquiera que pinte la lista de personas lo hace— y
**ninguna afirma nada sobre él**: los usuarios de prueba llevan `avatarUrl: null`
y lo que se comprueba es el correo o el papel. Lo que sigue sin cubrirse es lo
mismo que se dijo al escribir la ficha:

- Que sin `url` se pintan las iniciales correctas, y que con un nombre de una
  sola palabra sale una sola letra.
- Que la imagen rota cae a las iniciales. **Esta no se puede probar en `jsdom`**,
  que no carga imágenes y por tanto no dispara `onError` — y hoy `Avatar` no lo
  maneja siquiera, ver «Lo que falta».
- Que el `PUT` va seguido de la relectura de la persona.

## Estado de implementación y enlace al componente real

**Implementado a medias, y la mitad que falta es la que esta ficha llama
`AvatarField`.**

- **`Avatar` existe**, en
  [`files.tsx`](../../../../frontend/src/ui/files.tsx) y no en `primitives.tsx`:
  acompaña a `UploadField` y `FileGallery` porque llegó con ellos en el Hito 3.
  Pinta la imagen si hay `url` y las iniciales si no, con los tres tamaños que
  esta ficha describe. Lo usa `UsersPage` y la sección «Tu foto» de
  [`household.tsx`](../../../../frontend/src/routes/household.tsx).
- **`AvatarField` no existe.** Lo que hay en su sitio es un `<label>` con su
  propio `<input type="file">` escrito dentro de `household.tsx`, que llama a
  `api.setOwnAvatar` y vuelve a leer a la persona. Hace lo que la ficha pide y
  **no es un componente**: no se puede reutilizar, y la única razón por la que
  eso no ha dolido todavía es que solo hay un sitio donde se cambia una foto de
  perfil.

El dato viaja desde el Hito 1: `User.avatarUrl` está tipado en
[`client.ts`](../../../../frontend/src/api/client.ts), y desde el Hito 3 lo
consume la pantalla.

### Lo que falta

**Dos entradas se han quedado atrás y se retiran diciéndolo**: `api.setAvatar` y
`api.deleteAvatar` existen desde el Hito 3 con otro nombre —`setOwnAvatar` y
`deleteOwnAvatar`, que dicen mejor de quién es la cara—, y quitar el avatar sí
tiene sitio: el botón **Quitarla** de «Tu foto». Lo que queda:

- **`AvatarField` no existe como componente**, según lo dicho arriba. Sacarlo de
  `household.tsx` no urge mientras haya un solo sitio que cambie una foto, y
  urgirá el día que haya dos.
- **`Avatar` no cae a las iniciales cuando la imagen falla.** Con `url` pinta un
  `<img>` sin `onError`, así que una URL firmada caducada —que caduca a los
  quince minutos, y eso aquí es lo normal y no lo raro— deja el icono de imagen
  rota del navegador en vez de las iniciales. `FileGallery` sí lo resuelve, con
  `onStale`, y es de donde hay que copiar la salida.
- **No hay `GET /users/me`.** Después del `204` hay que volver a leer a la
  persona, y la única lectura que existe es `listUsers`, que devuelve el hogar
  entero. Funciona, pero pedir la lista completa para refrescar la propia cara es
  un rodeo que conviene mirar. **El contrato tampoco lo tiene**: en `/users/me`
  solo hay un `delete`, que es cerrar la cuenta.
- **El recorte no está resuelto.** El encuadre es 1:1 con recorte centrado, y una
  foto apaisada pierde media cara sin que nadie pueda elegir el encuadre.
  Recortar en el cliente es trabajo aparte y no está decidido.
- **El par de color de las iniciales no está medido**, como se dice más arriba, y
  ahora se puede decir exactamente cuál: las iniciales son `accent-ink` sobre
  `accent-soft`, y [`check-contrast.py`](../../../../scripts/check-contrast.py)
  mide `accent-ink` sobre `surface` y sobre `surface-raised` — **este par no está
  en su lista**. Es el único texto del producto cuyo contraste se afirma en vez
  de comprobarse.
- **El tope de 1 MB es estrecho para una foto de móvil**, que ronda los 3-8 MB.
  Sin reducción en el cliente, subir el avatar va a fallar más veces de las que
  va a funcionar. Es el mismo hueco que arrastra `UploadField`, y aquí se nota
  antes. **Con una excepción desde el 2026-08-20, y por casualidad:** un HEIC se
  convierte a JPEG en el cliente antes de subirse
  ([ADR-014](../../../common/architecture/decisions/ADR-014-heic-conversion.md)),
  y una foto de 12 MP sale de ahí en unos 240 kB — así que **el caso que menos
  cabía es el único que ahora entra con holgura**. El hueco sigue abierto para
  todo lo demás.

**Y una que no falta, que estaba mal colocada aquí:** el `accept` del avatar
incluye HEIC, con la misma distinción que explica la ficha de
[`upload-field`](upload-field.md#el-accept-es-una-comodidad-nunca-una-validación)
— no es que la lista blanca del servidor haya crecido, sigue teniendo cuatro
tipos, sino que el cliente convierte antes de enviar. La conversión le llega sola
porque vive en `uploadFile`, que es por donde pasa también esta subida; lo único
propio de esta pantalla es decir «Convirtiendo…» mientras dura.

## Referencias

- [`../README.md`](../README.md): la ficha mínima de un componente.
- [`upload-field.md`](upload-field.md): el mecanismo de subida que `AvatarField`
  reutiliza, y por qué hace falta `XMLHttpRequest`.
- [`file-gallery.md`](file-gallery.md): la regla de las URL firmadas, que aquí
  también se aplica.
- [`foundations/shape-and-elevation.md`](../foundations/shape-and-elevation.md):
  el redondeo completo y sus dos únicas excepciones.
- [`accessibility/`](../../accessibility/README.md): los pares medidos, y el que
  falta.
- [`openapi.yaml`](../../../../openapi.yaml): `PUT`/`DELETE /users/me/avatar` y
  el campo `avatarUrl` de `User`.
- [`users-and-access.md`](../../../common/product/users-and-access.md): por qué
  el avatar es de la identidad y no de la pertenencia.

## Historial de cambios

| Fecha | Cambio | Autor |
|---|---|---|
| 2026-08-20 | **La ficha se pone al día con el código, siete días tarde.** Decía «Previsto. No existe ni `Avatar` ni `AvatarField`» con `Avatar` construido desde el Hito 3, y su «Lo que falta» arrastraba dos entradas hechas —`setOwnAvatar`/`deleteOwnAvatar` existen, y quitar la foto tiene su botón—. Lo que **sí** falta se queda y gana precisión: `AvatarField` no es un componente sino un `<input>` escrito dentro de `household.tsx`; `Avatar` **no cae a las iniciales cuando la imagen falla**, que con URL firmadas de quince minutos es el caso normal y no el raro; y el par de color de las iniciales se nombra por fin —`accent-ink` sobre `accent-soft`, que no está en la lista de `check-contrast.py`—. Las evidencias de prueba siguen siendo ninguna, y ahora se dice por qué eso no es lo mismo que no montarlo nunca. Se recoloca la nota del `accept` con HEIC, que el Hito 2 metió por error en «Lo que falta» describiendo algo que sí existe. | Equipo DRP |
| 2026-08-13 | Creación de la ficha al arrancar el Hito 3. El componente está **previsto**: el dato `avatarUrl` existe y no lo pinta nadie. | Equipo DRP |
