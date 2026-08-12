# Field

| Campo | Valor |
|---|---|
| Estado | Borrador |
| Responsable | Equipo DRP |
| Ámbito | frontend |
| Última revisión | 2026-08-12 |

## Propósito y situaciones de uso

Un campo de texto con su etiqueta, su ayuda opcional y su mensaje de error, atados
entre sí por identificador. Es la pieza que hace que **la regla «nada se dice solo
con color» se cumpla en los formularios sin que cada pantalla se acuerde**: el
error viene con borde, icono y mensaje porque los pone el componente, no la
pantalla.

Se usa para todo lo que sea un `<input>`: nombre, correo, contraseña, número de
serie, cantidad. **No cubre `<select>`, `<textarea>`, casilla ni grupo de
opciones**; hoy no existe ninguno de ellos como componente.

## Anatomía, variantes y estados

Cuatro piezas en columna, con 6 px entre ellas (`gap-1.5`):

1. **`<label>`** — `text-body-sm font-medium text-ink`, con `htmlFor`.
2. **`<input>`** — la única pieza obligatoria además de la etiqueta.
3. **Ayuda** — `<p>` de `text-caption text-ink-muted`, solo si hay `hint` **y no
   hay error**.
4. **Error** — `<p>` de `text-caption text-danger` con icono y texto, solo si hay
   `error`.

El campo en sí:

| Rasgo | Valor | Token |
|---|---|---|
| Alto mínimo | 44 px | `min-h-touch` |
| Relleno | 12 px horizontal, 8 px vertical | `px-3 py-2` |
| Radio | 8 px | `rounded-md`, el radio de control |
| Fondo | La superficie elevada | `bg-surface-raised` |
| Texto | **16 px** | `text-body` |
| Marcador de posición | `placeholder:text-ink-subtle` | — |
| Borde en reposo | `border-border` | ≥ 3:1, obligado por WCAG 1.4.11 |
| Borde con error | `border-danger` | — |

**Los 16 px del campo no son negociables**: por debajo, Safari en iOS hace zoom
automático al enfocar y deja la página descuadrada. Está razonado en
[`typography.md`](../foundations/typography.md).

No hay variantes. Los estados son tres, y solo uno lo dibuja el componente:

| Estado | Cómo se pinta |
|---|---|
| Reposo | `border-border` |
| Foco | El anillo de la capa base; el borde no cambia |
| Con error | `border-danger` + `aria-invalid="true"` + el `<p>` con icono y mensaje |

No hay estado desactivado propio: `disabled` se pasa al `<input>` y lo pinta el
navegador.

## API pública

```ts
interface FieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string
  hint?: string
  error?: string
}
```

| Propiedad | Qué hace |
|---|---|
| `label` | **Obligatoria.** No hay forma de construir un campo sin etiqueta visible, y eso es deliberado |
| `hint` | Ayuda bajo el campo. **Desaparece cuando hay error**: los dos no compiten por el mismo sitio |
| `error` | El mensaje. Su presencia es lo que activa todo el estado de error |
| `id` | Opcional. Si no se da, sale de `useId()`, así que dos campos con la misma etiqueta en la misma página no colisionan |
| `className` | Se concatena a las clases **del `<input>`**, no del contenedor |
| Resto | Todo lo de `<input>`: `type`, `value`, `onChange`, `required`, `autoComplete`, `placeholder`… |

Los identificadores derivados son `${fieldId}-hint` y `${fieldId}-error`, y de
ahí sale el `aria-describedby`.

## Comportamiento responsive y con contenido extremo

- **Siempre a una columna.** El componente es un `flex flex-col`; el ancho lo
  pone el contenedor, que en los formularios de hoy es `--container-form`
  (544 px). [`look-and-feel.md`](../../product-design/look-and-feel.md) mantiene
  el formulario a una columna incluso cuando caben dos.
- **A 375 px** el campo ocupa el ancho disponible menos el `px-gutter` del
  contenedor. No hay nada que reajustar.
- **Etiqueta larga**: se parte en varias líneas y empuja el campo hacia abajo. No
  se trunca, que es lo correcto — una etiqueta a medias no identifica el campo.
- **Mensaje de error largo**: el `<p>` es `flex items-start`, así que el icono se
  queda arriba y el texto fluye en varias líneas sin descolocarlo.
- **La ayuda y el error nunca se pintan a la vez**, así que en el caso normal
  —campo con ayuda que pasa a error— la altura se mantiene y no hay salto. En un
  campo **sin** ayuda, en cambio, aparecer el error sí desplaza lo que viene
  debajo: ver [Lo que falta](#lo-que-falta).

## Teclado, foco, semántica y anuncios asistivos

- **La etiqueta está atada al campo por `htmlFor`/`id`**, así que pulsarla enfoca
  el campo y el lector de pantalla anuncia el nombre correcto. Es lo que permite
  que las pruebas usen `getByLabelText`.
- **`aria-invalid="true"`** cuando hay error, y `undefined` cuando no — no
  `"false"`, que también se anuncia.
- **`aria-describedby`** apunta a la ayuda, al error, o a los dos.
- El icono del error va `aria-hidden`: el mensaje ya dice lo que pasa, y
  anunciarlo dos veces sería ruido. El icono está ahí para quien **ve** la
  pantalla en blanco y negro o con daltonismo.
- El campo no fuerza `noValidate`: lo ponen los formularios, para que la
  validación del navegador no se adelante a la del servidor con un mensaje que no
  es el nuestro.

## Ejemplos correctos, antiusos y evidencias de prueba

Correcto — un campo con ayuda y con el error del contrato enganchado por nombre
de atributo:

```tsx
<Field
  label="Contraseña"
  type="password"
  value={password}
  onChange={(event) => setPassword(event.target.value)}
  autoComplete="new-password"
  hint="Mínimo 12 caracteres. Una frase que recuerdes vale más que un jeroglífico."
  error={fieldError?.fieldError('admin.password')}
  required
/>
```

`fieldError(campo)` lee `details[campo]` del `VALIDATION_ERROR` que devuelve la
API, de modo que **el mensaje que se pinta bajo el campo lo escribe el servidor**
y el frontend solo lo coloca. La correspondencia entre el nombre del atributo del
contrato y el campo de la pantalla la decide la pantalla, y es donde se rompe:
`ResetPasswordPage` mira `password`, `CreateHouseholdPage` mira `admin.password`
y `AccountPage` mira `newPassword` con `password` de reserva.

Antiusos:

| Antiuso | Por qué |
|---|---|
| `placeholder` en lugar de `label` | Desaparece al escribir y no lo lee un lector de pantalla como nombre |
| Pintar el error solo cambiando el borde | Incumple 1.4.1; el componente no deja hacerlo, pero una pantalla que se salte `Field` sí |
| `border-subtle` en un campo | Incumple 1.4.11. El borde de un control es `border` |
| Usar `Field` para un `<select>` | No lo es. Hoy hay que escribirlo a mano, y por eso hace falta un `Select` |
| Meter el mensaje de error en un `Notice` sobre el formulario cuando es de un campo | El error recuperable se resuelve donde ocurrió |

Evidencias de prueba, en
[`App.test.tsx`](../../../../frontend/src/App.test.tsx):

- Las veinte interacciones con campos del fichero van por `getByLabelText`, lo
  que verifica la atadura etiqueta–campo en los flujos que las pruebas cubren:
  login, alta de hogar, recuperación de contraseña y envío de invitación.
- La prueba «muestra el motivo cuando la contraseña no cumple, en el propio
  campo» comprueba las tres cosas a la vez: que el mensaje del servidor se pinta,
  que el campo queda con `aria-invalid="true"` y que
  `toHaveAccessibleDescription` lo alcanza desde el campo.

## Estado de implementación y enlace al componente real

**Implementado.**
[`frontend/src/ui/primitives.tsx`](../../../../frontend/src/ui/primitives.tsx),
función `Field`.

Once usos en
[`enrollment.tsx`](../../../../frontend/src/routes/enrollment.tsx) —seis de sus
siete pantallas; la de verificación del correo no tiene formulario— y tres en
[`household.tsx`](../../../../frontend/src/routes/household.tsx).

### Lo que falta

- **El `aria-describedby` nombra la ayuda aunque la ayuda no se pinte.** Con
  `hint` y `error` a la vez, el atributo queda como `"x-hint x-error"` y el
  elemento `x-hint` no existe en el DOM, porque se renderiza con `hint && !error`.
  En la práctica el lector de pantalla lee solo el error, que es lo que se
  quería, pero la referencia colgada es una IDREF inválida y la auditoría axe del
  Hito 4 la va a encontrar.
- **El hueco del mensaje no está reservado.**
  [`look-and-feel.md`](../../product-design/look-and-feel.md) dice «sin mover la
  maquetación —el hueco del mensaje está reservado—», y lo que hay es un `<p>`
  que entra en el flujo: sin `hint` previo, aparecer el error empuja hacia abajo
  todo lo que viene después. Con `hint` no se nota porque uno sustituye al otro,
  que es por lo que ha pasado desapercibido.
- **El foco no va al primer campo con error.** Es la otra mitad de la misma regla
  de la dirección visual, y no está en ninguna parte: ni en `Field` —que no puede
  saber si es el primero— ni en los formularios. Corresponde al patrón, y está
  anotado en [`patterns/form.md`](../patterns/form.md).
- **No hay marca de campo obligatorio.** `required` llega al `<input>` y no se ve.
  Con formularios de cuatro campos donde todos lo son, no importa; el alta de
  asset del Hito 2 mezcla obligatorios y opcionales en la misma columna.
- **No hay `Select`, `Textarea` ni campo numérico.** Los tres los pide el Hito 2:
  categoría y unidad son desplegables, las notas son texto largo y la cantidad es
  un número con la unidad del artículo al lado. El `<select>` de invitar a alguien
  ya está escrito a mano en `household.tsx`, repitiendo la maquetación de `Field`
  sin compartir código: es la señal de que el componente falta.
- **No hay `Combobox`.** `GET /articles` lleva el parámetro `q` precisamente para
  alimentar un autocompletado de artículos, y no hay con qué pintarlo.

## Referencias

- [`../README.md`](../README.md): la ficha mínima.
- [`foundations/color.md`](../foundations/color.md): por qué el borde de un
  control es `border` y no `border-subtle`.
- [`foundations/typography.md`](../foundations/typography.md): los 16 px.
- [`patterns/form.md`](../patterns/form.md): validación, envío y confirmación.
- [`accessibility/`](../../accessibility/README.md)

## Historial de cambios

| Fecha | Cambio | Autor |
|---|---|---|
| 2026-08-12 | Creación de la ficha sobre la implementación del Hito 1. | Equipo DRP |
