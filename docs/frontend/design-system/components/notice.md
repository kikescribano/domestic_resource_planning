# Notice

| Campo | Valor |
|---|---|
| Estado | Vigente |
| Responsable | Equipo DRP |
| Ámbito | frontend |
| Última revisión | 2026-08-21 |

## Propósito y situaciones de uso

Un aviso **en el sitio donde ocurrió**: un bloque en el flujo de la página que
explica algo que el usuario necesita saber ahora mismo. Se coloca donde está la
causa —sobre el formulario que falló, junto a la lista que no cargó— y se queda
ahí hasta que la situación cambia.

No es un aviso flotante. El aviso efímero de esquina que describe
[`look-and-feel.md`](../../product-design/look-and-feel.md) —cinco segundos, con
cierre y con deshacer— es otro componente, y **no existe todavía**.

Los cuatro tonos, y cuándo toca cada uno:

| Tono | Cuándo |
|---|---|
| `info` | Contexto que el usuario no ha pedido pero necesita |
| `success` | Salió bien y **no se ve en pantalla**. Si el cambio ya se ve, el aviso sobra |
| `warning` | Se puede seguir, pero conviene mirarlo |
| `danger` | Falló, o va a destruir algo |

El tono es el del **feedback del sistema**: describe lo que le ha pasado a una
petición. Lo que le pasa a una cosa del hogar es un estado del dominio y va en
[`StatusBadge`](status-badge.md).

## Anatomía, variantes y estados

Una caja horizontal con dos columnas y 12 px entre ellas (`gap-3`):

1. **El icono**, en un `<span>` con `mt-0.5 shrink-0` y `aria-hidden="true"`.
   Hereda el color del tono desde la caja, porque el texto de dentro lo
   sobrescribe.
2. **El cuerpo**: un `<p>` opcional con el título en `font-medium`, y el
   contenido en `text-ink-muted`.
3. **La equis de descarte**, solo si el montador pasa `onDismiss`: un botón de
   `size-11` —los 44 px del objetivo táctil— empujado a la derecha con
   `ml-auto` y con márgenes negativos para no engordar el aviso. El icono `X`
   va `aria-hidden`; el nombre lo pone `aria-label="Descartar el aviso"`.

| Rasgo | Valor | Token |
|---|---|---|
| Relleno | 12 px | `p-3` |
| Radio | 12 px | `rounded-lg`, el radio de tarjeta |
| Texto | 14 px | `text-body-sm` |
| Borde | 1 px del color del tono | — |
| Fondo | La variante `-soft` del tono | — |

Los cuatro tonos, tal y como están en `NOTICE_TONES`:

| Tono | Clases | Icono |
|---|---|---|
| `info` | `border-info bg-info-soft text-info` | Círculo con «i» |
| `success` | `border-success bg-success-soft text-success` | Círculo con marca |
| `warning` | `border-warning bg-warning-soft text-warning` | Triángulo con «!» |
| `danger` | `border-danger bg-danger-soft text-danger` | Círculo con «!» |

**El color del tono no llega al texto.** La caja declara `text-<tono>`, pero el
`<div>` interior lo sobrescribe con `text-ink` y el contenido con
`text-ink-muted`. Así que el tono se ve en tres sitios —borde, fondo `-soft` e
icono— y el texto va siempre en tinta. No es un descuido: es lo que mantiene el
contraste del texto en el par medido y hace que el aviso se entienda en blanco y
negro.

No hay estados: un `Notice` está o no está. Descartarlo no es un estado suyo
sino de quien lo monta: la equis solo llama a `onDismiss`, y **recordar la
decisión es del montador**, porque solo él sabe si «leído» dura una sesión o
para siempre — la portada, por ejemplo, lo apunta en `localStorage`.

## API pública

```ts
type NoticeTone = 'info' | 'success' | 'warning' | 'danger'

function Notice(props: {
  tone?: NoticeTone   // por defecto 'info'
  title?: string
  onDismiss?: () => void
  children: ReactNode
}): JSX.Element
```

| Propiedad | Qué hace |
|---|---|
| `tone` | Elige colores, icono y **el papel ARIA**. Por defecto `'info'` |
| `title` | Primera línea en `font-medium`. Opcional: la mitad de los usos no lo lleva |
| `onDismiss` | Pinta la equis de descarte y la conecta. Sin ella no hay botón: un aviso que refleja un estado vivo —el formulario falló— no debe poder descartarse |
| `children` | El cuerpo. Admite marcado, y se usa para meter un enlace dentro del aviso |

No acepta `className`, ni `id`, ni el resto de atributos de un `<div>`. La API es
cerrada a propósito: un aviso no se recoloca desde fuera.

## Comportamiento responsive y con contenido extremo

- **Ocupa el ancho de su contenedor** y no tiene ancho propio. En los formularios
  de hoy eso es `--container-form`; en el shell, la columna de contenido.
- **El icono no se encoge** (`shrink-0`) y el texto fluye a su lado en tantas
  líneas como haga falta. El `mt-0.5` lo alinea con la primera línea de texto en
  lugar de centrarlo verticalmente, que es lo que se ve mal cuando el aviso tiene
  cuatro líneas.
- **A 375 px** el aviso mantiene las dos columnas: el icono son 20 px y el resto
  es texto. No hay reflujo que hacer.
- **Texto muy largo**: crece hacia abajo sin límite. No está limitado a
  `--container-reading`, así que en un contenedor ancho un aviso de varias frases
  puede pasar de 68 caracteres por línea. Con la anchura de formulario actual no
  ocurre.

## Teclado, foco, semántica y anuncios asistivos

Esta es la parte que decide la calidad del componente:

- **`role="alert"` solo en el tono `danger`; `role="status"` en los otros tres.**
  Un `alert` interrumpe al lector de pantalla en cuanto aparece. Hacerlo para
  confirmar un éxito es exactamente el feedback intrusivo que la dirección visual
  rechaza; hacerlo para un fallo es lo correcto, porque el usuario tiene que
  enterarse antes de seguir escribiendo.
- **Los dos papeles son regiones vivas** (`alert` es `assertive`, `status` es
  `polite`), así que el aviso se anuncia al montarse **sin mover el foco**. Que
  no mueva el foco es la razón por la que se puede usar en mitad de un
  formulario.
- **No es enfocable ni recibe tabulación.** Si el aviso lleva un enlace dentro,
  el enlace sí; y si lleva `onDismiss`, la equis es un botón con su parada de
  tabulador **después** de los enlaces del cuerpo, porque va al final del DOM.
- **El icono va `aria-hidden`**: el texto ya dice lo que pasa. El de la equis
  también, y su nombre accesible lo pone el `aria-label` del botón.

## Ejemplos correctos, antiusos y evidencias de prueba

Correcto — el fallo de una operación completa, sobre el formulario y
conservando lo escrito:

```tsx
{error && !fieldError?.details && (
  <Notice tone="danger" title="No se ha podido crear">
    {messageFor(error)}
  </Notice>
)}
```

La condición importa tanto como el aviso: **si el error trae `details`, es de
campo y va bajo el campo**, no aquí. Es la separación entre error recuperable y
fallo de operación.

Correcto — un aviso que ofrece la salida, con el enlace dentro:

```tsx
<Notice tone={needsVerification ? 'warning' : 'danger'}>
  {messageFor(error)}
  {needsVerification && <Link to="/reenviar-confirmacion">Reenviar el enlace</Link>}
</Notice>
```

Antiusos:

| Antiuso | Por qué |
|---|---|
| `tone="success"` para confirmar algo que ya se ve en pantalla | El éxito por defecto es el propio cambio. Un asset que aparece movido no necesita que se lo cuenten |
| `tone="danger"` para una advertencia que no bloquea | `danger` interrumpe con `role="alert"`. El aviso de capacidad del Hito 2 es `warning`, y esa diferencia es de producto, no de estilo |
| Un `Notice` por cada campo con error | El error de un campo va bajo su campo. El aviso es para lo que no cabe ahí |
| Meter el mensaje crudo de la API | `message` es texto de diagnóstico. Lo que se muestra sale de una traducción por código, como hace `messageFor` |
| Usar `Notice` como aviso efímero | No se cierra, no caduca y no flota. Hace falta un `Toast`, y no existe |

Evidencias de prueba, en
[`App.test.tsx`](../../../../frontend/src/App.test.tsx):

- «con la contraseña equivocada no dice si el correo existe» localiza el aviso
  **por su papel** (`findByRole('alert')`), lo que verifica que un `Notice` de
  tono `danger` es una región viva assertive de verdad, y comprueba además que su
  texto no delata si la cuenta existe.
- «cuando el correo no está verificado lo dice y ofrece reenviar el enlace»
  comprueba el aviso de tono `warning` y el enlace que lleva dentro.

## Estado de implementación y enlace al componente real

**Implementado.**
[`frontend/src/ui/primitives.tsx`](../../../../frontend/src/ui/primitives.tsx),
función `Notice`, con `NOTICE_TONES` al lado.

Los cuatro tonos están en uso: `danger` y `success` en
[`enrollment.tsx`](../../../../frontend/src/routes/enrollment.tsx) y en
[`household.tsx`](../../../../frontend/src/routes/household.tsx), `info` en el
alta y en `HomePage`, `warning` en el login cuando el correo no está verificado.

### Lo que falta

- ~~**No se puede cerrar.**~~ **Resuelto el 2026-08-21, con el panel de
  «Hogar»**: `onDismiss` pinta la equis y el montador decide qué recordar y
  dónde. Sigue siendo correcto que un aviso de estado vivo no la lleve — por
  eso es opcional y ningún otro uso la ha ganado.
- **No tiene ranura de acción.** Hoy la acción se cuela como un `<Link>` dentro
  de `children`, que funciona para un enlace y no para un botón de «Reintentar».
  El fallo de carga de un listado lo va a pedir en el Hito 2.
- **No muestra el código de error.**
  [`look-and-feel.md`](../../product-design/look-and-feel.md) pide que el código
  del contrato quede visible en pequeño, para poder decirlo al pedir ayuda; hoy
  `messageFor` lo traduce y lo descarta. Con los 41 códigos del contrato y las 23
  operaciones del Hito 2, esto pasa de detalle a necesidad.
- **No hay `Toast`.** El aviso de esquina con cinco segundos, cierre y deshacer
  es otro componente, y no existe.
- **No hay error bloqueante a pantalla completa.** Sesión caducada, hogar sin
  acceso y error del servidor tienen su propio tratamiento en la dirección
  visual, con ilustración y una única salida; `Notice` no es eso.

## Referencias

- [`../README.md`](../README.md): la ficha mínima.
- [`patterns/feedback.md`](../patterns/feedback.md): cuándo aviso, cuándo
  distintivo y cuándo nada.
- [`foundations/color.md`](../foundations/color.md): feedback del sistema frente
  a estados del dominio.
- [`status-badge.md`](status-badge.md)

## Historial de cambios

| Fecha | Cambio | Autor |
|---|---|---|
| 2026-08-21 | **Gana `onDismiss`**, con el panel de «Hogar»: la equis de descarte —44 px, `aria-label`, empujada con `ml-auto`— aparece solo si el montador la pide, y recordar la decisión queda del lado del montador a propósito. Se cierra la primera entrada de «Lo que falta», que llevaba dos revisiones diciendo exactamente esto. | Equipo DRP |
| 2026-08-20 | **Repasada entera contra el código y no cambia ni una afirmación**: sigue sin poder cerrarse, sin ranura de acción, sin enseñar el código del contrato, y siguen sin existir `Toast` y el error bloqueante a pantalla completa. Lo único que se corrige es el marco temporal de la lista, que hablaba en futuro de un hito ya cerrado. | Equipo DRP |
| 2026-08-12 | Creación de la ficha sobre la implementación del Hito 1. | Equipo DRP |
