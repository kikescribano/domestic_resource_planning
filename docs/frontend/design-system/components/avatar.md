# Avatar

| Campo | Valor |
|---|---|
| Estado | Previsto |
| Responsable | Equipo DRP |
| Ámbito | frontend |
| Última revisión | 2026-08-13 |

> **Esta ficha describe algo que todavía no existe.** `User` ya trae `avatarUrl`
> en [`client.ts`](../../../../frontend/src/api/client.ts) y **nadie lo pinta**:
> la lista de personas de `UsersPage`, en
> [`household.tsx`](../../../../frontend/src/routes/household.tsx), muestra
> nombre, correo y rol. Es la **especificación** de la anatomía que el Hito 3
> tiene que construir.

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

Evidencias de prueba: **ninguna todavía**. Lo que el hito tendrá que cubrir: que
sin `src` se pintan las iniciales correctas, que con nombre de una palabra sale
una sola letra, que la imagen rota cae a las iniciales, y que el `PUT` va seguido
de la relectura de la persona.

## Estado de implementación y enlace al componente real

**Previsto.** No existe ni `Avatar` ni `AvatarField`. El dato sí:
`User.avatarUrl` está tipado en
[`client.ts`](../../../../frontend/src/api/client.ts) desde el Hito 1 y no lo
consume nadie.

### Lo que falta

- **`api.setAvatar` y `api.deleteAvatar` no existen.** El contrato tiene las dos
  operaciones —`PUT` y `DELETE /users/me/avatar`— y el cliente no las expone. La
  primera necesita además la misma función de subida que
  [`UploadField`](upload-field.md), porque `client.ts` no sabe enviar
  `multipart/form-data`.
- **No hay `GET /users/me`.** Después del `204` hay que volver a leer a la
  persona, y la única lectura que existe es `listUsers`, que devuelve el hogar
  entero. Funciona, pero pedir la lista completa para refrescar la propia cara es
  un rodeo que conviene mirar.
- **Quitar el avatar no tiene sitio.** `DELETE /users/me/avatar` existe y ninguna
  pantalla lo ofrece.
- **El recorte no está resuelto.** El encuadre es 1:1 con recorte centrado, y una
  foto apaisada pierde media cara sin que nadie pueda elegir el encuadre.
  Recortar en el cliente es trabajo aparte y no está decidido.
- **El par de color de las iniciales no está medido**, como se dice más arriba.
- **El tope de 1 MB es estrecho para una foto de móvil**, que ronda los 3-8 MB.
  Sin reducción en el cliente, subir el avatar va a fallar más veces de las que
  va a funcionar. Es el mismo hueco que arrastra `UploadField`, y aquí se nota
  antes.

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
| 2026-08-13 | Creación de la ficha al arrancar el Hito 3. El componente está **previsto**: el dato `avatarUrl` existe y no lo pinta nadie. | Equipo DRP |
