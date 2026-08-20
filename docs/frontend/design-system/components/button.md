# Button

| Campo | Valor |
|---|---|
| Estado | Vigente |
| Responsable | Equipo DRP |
| Ámbito | frontend |
| Última revisión | 2026-08-20 |

## Propósito y situaciones de uso

Toda acción que el usuario dispara y que no es una navegación. Si lleva a otro
sitio es un enlace, aunque parezca un botón: la diferencia la nota quien navega
con teclado, quien abre en una pestaña nueva y quien usa lector de pantalla.

`Button` es además donde vive, en código y no en una convención, la regla de
**una acción principal por pantalla**: solo `variant="primary"` pinta el relleno
de acento, así que dos rellenos en la misma vista se ven de un vistazo en el
diff.

Situaciones de uso, con la variante que le toca a cada una:

| Situación | Variante |
|---|---|
| La acción que la pantalla existe para hacer | `primary` |
| Cualquier otra acción con peso: cancelar, salir, una segunda opción real | `secondary` |
| Acción terciaria, de las que acompañan sin competir | `ghost` |
| Acción destructiva o que retira algo | `danger` |

## Anatomía, variantes y estados

El elemento es un `<button>` y nada más: una caja `inline-flex` centrada, con
`gap-2` entre lo que haya dentro. No hay ranura de icono, ni de contador, ni
indicador de carga (ver [Lo que falta](#lo-que-falta)).

Medidas, todas por token:

| Rasgo | Valor | Token |
|---|---|---|
| Alto mínimo | 44 px | `min-h-touch` |
| Relleno | 16 px horizontal, 8 px vertical | `px-4 py-2` |
| Radio | 8 px | `rounded-md`, el radio de control |
| Texto | 16 px, peso medio | `text-body font-medium` |
| Transición | `transition-colors`, 140 ms por defecto | `--duration-fast` |

Las cuatro variantes, tal y como están escritas en `BUTTON_VARIANTS`:

| Variante | Clases |
|---|---|
| `primary` | `bg-accent text-ink-inverse hover:bg-accent-hover` |
| `secondary` | `border border-border bg-surface-raised text-ink hover:bg-surface-hover` |
| `ghost` | `text-accent-ink hover:bg-surface-hover` |
| `danger` | `border border-danger text-danger hover:bg-danger-soft` |

Tres cosas que se leen de esa tabla y conviene no perder:

- **`danger` no tiene relleno.** Es un borde rojo sobre la superficie, no un
  bloque rojo. Un relleno sólido de peligro competiría con el acento y rompería
  la regla del único relleno por pantalla.
- **`ghost` usa `accent-ink` y no `accent`**, porque ahí el acento es texto y el
  texto necesita 4,5:1 contra el papel, no los 3:1 de un relleno.
- **`secondary` es el valor por defecto.** Escribir `<Button>` sin variante da un
  botón secundario, que es la elección segura: para que un botón sea el principal
  hay que decirlo.

Estados:

| Estado | Cómo se pinta | Dónde se declara |
|---|---|---|
| Reposo | La variante | `BUTTON_VARIANTS` |
| Puntero encima | El `hover:` de la variante | `BUTTON_VARIANTS` |
| Foco | Anillo de 2 px de `--color-focus` con 2 px de separación | La capa base de [`index.css`](../../../../frontend/src/index.css), **no el componente** |
| Desactivado | `disabled:cursor-not-allowed disabled:opacity-60` | El componente |
| Ocupado | `disabled` + `aria-busy` + el texto pasa a `busyLabel` | El componente |

**Ocupado implica desactivado**: `disabled={disabled || busy}`. No hay forma de
tener un botón ocupado que se pueda volver a pulsar, que es el doble envío
clásico.

## API pública

```ts
type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant   // por defecto 'secondary'
  busy?: boolean            // por defecto false
  busyLabel?: string
}
```

| Propiedad | Qué hace |
|---|---|
| `variant` | Elige el juego de colores. Por defecto `'secondary'` |
| `busy` | Desactiva el botón y pone `aria-busy="true"` |
| `busyLabel` | Texto que sustituye a `children` **mientras** `busy`. Si no se da, el texto no cambia y el botón solo se desactiva |
| `className` | Se concatena al final, así que gana en caso de conflicto. Se usa hoy solo para el margen exterior |
| Resto | Todo lo de `<button>`: `type`, `onClick`, `disabled`, `form`… |

`type` no tiene valor por defecto en el componente, así que hereda el del HTML
(`submit` dentro de un formulario). Los botones que no envían llevan su `onClick`
y no están dentro de un `<form>`, que es como se resuelve hoy en
[`household.tsx`](../../../../frontend/src/routes/household.tsx).

## Comportamiento responsive y con contenido extremo

- **No ocupa el ancho completo por su cuenta.** Es `inline-flex`; dentro del
  `flex flex-col` de un formulario se estira porque lo estira el contenedor, no
  porque el botón lo pida. En una fila `flex-wrap` conserva su tamaño.
- **El texto no se trunca ni se parte en dos líneas**: `items-center
  justify-center` sin `whitespace` declarado, así que una etiqueta larga hace
  crecer el botón. Es lo correcto para un botón —una acción con nombre largo se
  renombra, no se recorta— pero significa que la etiqueta es responsabilidad de
  quien la escribe.
- **El objetivo táctil se cumple por altura**, no por área declarada: los 44 px
  son `min-h`, así que un botón con una palabra corta sigue siendo alto aunque
  sea estrecho.
- **A 375 px** no hay nada que ajustar: el botón cabe en la columna de
  `--container-form` y en la de contenido con `px-gutter`.

## Teclado, foco, semántica y anuncios asistivos

- Es un `<button>` nativo: entra en el orden de tabulación, responde a `Enter` y
  a `Espacio`, y anuncia su papel sin ayuda.
- **El foco no lo dibuja el componente.** Lo pone `:focus-visible` en la capa
  base con `--color-focus`, medido a 9,69:1 en claro y 11,68:1 en oscuro sobre el
  fondo (ver [`accessibility/`](../../accessibility/README.md)). Los 2 px de
  separación existen para el caso difícil, que es justamente este: sobre un
  `primary` de relleno sólido, un anillo pegado se confundiría con el relleno.
- **`aria-busy` además del texto.** Cambiar «Entrar» por «Entrando…» no anuncia
  nada por sí solo; `aria-busy="true"` sí dice que hay algo en curso.
- Desactivado se hace con el atributo `disabled` nativo, no con `aria-disabled`:
  el botón sale del orden de tabulación, que es el comportamiento esperado de una
  acción que no se puede hacer.

## Ejemplos correctos, antiusos y evidencias de prueba

Correcto — la acción principal de un formulario, con su texto de ocupado:

```tsx
<Button type="submit" variant="primary" busy={busy} busyLabel="Creando…">
  Crear el hogar
</Button>
```

Correcto — retirar una invitación, que es destructivo y por eso lleva el verbo en
la etiqueta y no un icono de papelera:

```tsx
<Button variant="danger" onClick={() => revoke.mutate(invitation.id)} busy={revoke.isPending} busyLabel="Retirando…">
  Retirar
</Button>
```

Antiusos:

| Antiuso | Por qué |
|---|---|
| Dos `variant="primary"` en la misma pantalla | Una de las dos no era la principal. Es la regla que el componente existe para hacer visible |
| Un `Button` que navega | Usa un enlace. `Cerrar sesión` es una acción y va en botón; `Personas` es un sitio y va en `NavLink` |
| `busy` sin `busyLabel` en una operación de más de 400 ms | El botón queda desactivado y mudo, que se percibe como aplicación colgada |
| Un icono como única etiqueta | El componente no da nombre accesible por su cuenta; hoy quedaría un botón sin nombre |
| Sobrescribir el color con `className` | Si hace falta otro color, falta una variante |

Evidencias de prueba, en
[`App.test.tsx`](../../../../frontend/src/App.test.tsx):

- Se localiza por papel y nombre —`getByRole('button', { name: 'Entrar' })`— en
  nueve puntos del fichero, lo que verifica que el nombre accesible es el texto
  visible.
- La prueba «un miembro no ve el formulario de invitar» comprueba la **ausencia**
  del botón por el mismo papel y nombre.
- **No hay ninguna prueba del estado ocupado**, de las variantes ni del
  comportamiento con `disabled`. Es un hueco conocido.

## Estado de implementación y enlace al componente real

**Implementado.**
[`frontend/src/ui/primitives.tsx`](../../../../frontend/src/ui/primitives.tsx),
función `Button`.

En uso hoy en [`enrollment.tsx`](../../../../frontend/src/routes/enrollment.tsx)
y [`household.tsx`](../../../../frontend/src/routes/household.tsx), con las
variantes `primary`, `secondary` y `danger`. **`ghost` está implementada y no se
usa en ninguna pantalla todavía.**

### Lo que falta

Se escribió como «lo que el Hito 2 va a pedir», y ese hito se cerró hace dos
fases: los listados y las fichas se construyeron **rodeando** cada una de estas
carencias en lugar de resolverlas. Repasado el 2026-08-20 contra el código,
**sigue sin existir nada de esto**:

- **La anchura no se conserva al pasar a ocupado.** El comentario del código dice
  que sí, y no es lo que hace: `busyLabel` sustituye a `children` sin reservar el
  ancho anterior, así que «Crear el hogar» → «Creando…» **encoge** el botón. En un
  formulario a una columna el salto es pequeño; en una barra de acciones de la
  ficha de asset se notará. Se arregla reservando la anchura, no cambiando el
  texto.
- **Ranura de icono.** El `gap-2` ya está puesto y no hay nada que separar.
  [`iconography.md`](../foundations/iconography.md) pide 24 px en acción y nombre
  accesible propio cuando el icono **es** el botón; ninguna de las dos cosas está
  resuelta.
- **Un tamaño compacto.** [`density.md`](../foundations/density.md) fija la fila
  de tabla en ≈ 36 px desde `md`, y `min-h-touch` son 44 px: **un `Button` no cabe
  hoy dentro de una fila compacta sin romper su altura.** Los listados del Hito 2
  necesitan resolverlo, y la salida no es bajar los 44 px en móvil.
- **Desactivado usa `opacity-60`, no `--color-ink-disabled`.** El token existe y
  está exento de 4,5:1 a propósito; la opacidad, en cambio, se aplica sobre el
  botón entero y hace que el par que se ve deje de ser el par medido por
  [`check-contrast.py`](../../../../scripts/check-contrast.py).
- **Indicador visual de carga.** Hoy el estado ocupado es solo texto. Un
  `Spinner` dentro del botón es lo natural, y hace falta decidir si sustituye al
  texto o lo acompaña.

## Referencias

- [`../README.md`](../README.md): la ficha mínima.
- [`foundations/color.md`](../foundations/color.md) y
  [`foundations/shape-and-elevation.md`](../foundations/shape-and-elevation.md).
- [`patterns/form.md`](../patterns/form.md) y
  [`patterns/listing.md`](../patterns/listing.md): dónde se coloca la acción
  principal.
- [`look-and-feel.md`](../../product-design/look-and-feel.md), principio 3.

## Historial de cambios

| Fecha | Cambio | Autor |
|---|---|---|
| 2026-08-20 | **Repasada entera contra el código y no cambia ni una afirmación**: la anchura sigue sin conservarse al pasar a ocupado —con el comentario del código diciendo que sí—, el desactivado sigue usando `opacity-60` en vez del token, y siguen sin existir la ranura de icono, el tamaño compacto y el indicador de carga. Lo único que se corrige es el marco: la lista se presentaba como «lo que el Hito 2 va a pedir», y ese hito se cerró hace dos fases. | Equipo DRP |
| 2026-08-12 | Creación de la ficha sobre la implementación del Hito 1. | Equipo DRP |
