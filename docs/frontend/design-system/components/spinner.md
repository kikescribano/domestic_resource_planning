# Spinner

| Campo | Valor |
|---|---|
| Estado | Vigente |
| Responsable | Equipo DRP |
| Ámbito | frontend |
| Última revisión | 2026-08-20 |

## Propósito y situaciones de uso

Decir que hay algo en curso **y qué es ese algo**. No es un disco que gira: es
una línea de texto con un disco al lado, y el texto es obligatorio.

Esa obligación es la decisión de diseño del componente. Un indicador de carga
mudo obliga a adivinar qué se está esperando, y en una pantalla con dos
peticiones a la vez no distingue cuál de las dos va lenta.

[`look-and-feel.md`](../../product-design/look-and-feel.md) distingue **tres
situaciones de espera y les da tres respuestas distintas**, que es lo que evita
el spinner universal. `Spinner` solo es la respuesta correcta en una de ellas:

| Situación | Respuesta correcta | ¿Existe? |
|---|---|---|
| Primera carga de una vista | Esqueleto con la forma real del contenido | **No.** Hace falta un `Skeleton` |
| Actualización de algo que ya se ve | Barra de progreso fina bajo la cabecera, sin vaciar lo que se está leyendo | **No** |
| Acción del usuario | El botón pasa a ocupado y conserva su anchura | Sí, en [`Button`](button.md) con `busy` |
| Espera sin contenido previo que enseñar | **`Spinner`** | Sí |

La cuarta fila es la suya, y es más estrecha de lo que parece: la pantalla de
verificación de correo, donde no hay nada que esqueletizar porque no hay
contenido, solo un token consumiéndose.

## Anatomía, variantes y estados

Dos piezas en línea, con 8 px entre ellas (`gap-2`):

1. **El disco**, un `<span>` vacío con `aria-hidden="true"`.
2. **La etiqueta**, texto suelto dentro del `<p>`.

| Rasgo | Valor | Token |
|---|---|---|
| Contenedor | `<p>` con `role="status"`, `flex items-center` | — |
| Texto | 14 px, tinta secundaria | `text-body-sm text-ink-muted` |
| Disco | 16 × 16 px | `size-4` |
| Grosor del anillo | 2 px | `border-2` |
| Anillo | El borde de control | `border-border` |
| Segmento que gira | El acento | `border-t-accent` |
| Forma | Círculo | `rounded-full` |
| Animación | `animate-spin`, la utilidad de Tailwind | — |

El anillo usa `border` y no `border-subtle`, así que el disco entero se queda por
encima de los 3:1 que pide el criterio 1.4.11 para un elemento no textual:
**3,49:1 en claro y 4,30:1 en oscuro**, medidos. El segmento de acento va a
5,56:1 y 6,64:1 sobre el papel. Los tres números salen de
[`check-contrast.py`](../../../../scripts/check-contrast.py).

No hay variantes, no hay tamaños y no hay estados. El componente está montado o
no lo está.

## API pública

```ts
function Spinner(props: { label: string }): JSX.Element
```

| Propiedad | Qué hace |
|---|---|
| `label` | **Obligatoria y sin valor por defecto.** No hay forma de construir un indicador mudo, y eso es deliberado |

Una sola propiedad, y ni `className` ni `id`. Es el componente más cerrado del
sistema.

## Comportamiento responsive y con contenido extremo

- **Ocupa el ancho de su contenedor**, porque es un `<p>`, pero su contenido se
  alinea a la izquierda: no está centrado ni se centra solo. Donde se usa hoy
  —dentro de la columna de [`AuthCard`](card.md)— queda alineado con el resto del
  texto, no en mitad de la pantalla.
- **El disco no se encoge**… salvo que se le apriete: `size-4` fija los 16 px,
  pero **no lleva `shrink-0`**. Dentro de un contenedor flex estrecho con una
  etiqueta larga, el disco puede aplastarse a un óvalo. No pasa hoy porque las
  dos etiquetas que existen caben, y pasará en cuanto alguien escriba una frase
  larga en una columna estrecha.
- **La etiqueta fluye en varias líneas** si no cabe, y el `items-center` centra
  el disco respecto del bloque entero de texto, no de su primera línea. Con dos
  líneas ya se nota; `Notice` resuelve el mismo problema con `items-start` y
  `mt-0.5`.
- **A 375 px** no hay nada que ajustar.

## Teclado, foco, semántica y anuncios asistivos

- **`role="status"`**, que es una región viva *polite*: se anuncia sin
  interrumpir y **sin mover el foco**. Es lo que permite que aparezca en mitad de
  una pantalla sin robarle el sitio a lo que el usuario estaba haciendo.
- **No es enfocable** y no entra en el orden de tabulación.
- **El disco va `aria-hidden`**: quien no ve la pantalla no necesita saber que
  hay un círculo girando, necesita saber qué se está esperando. Eso lo dice la
  etiqueta.
- Los dos usos actuales montan y desmontan el componente entero, en lugar de
  cambiar el texto dentro de una región viva que ya existiera. Es lo habitual y
  suele anunciarse bien, pero **no está verificado contra un lector de pantalla
  real**: los anuncios asistivos son de lo que
  [`accessibility/`](../../accessibility/README.md) deja explícitamente pendiente
  hasta el Hito 4.
- **Bajo `prefers-reduced-motion: reduce` el disco deja de girar del todo.** La
  regla de [`index.css`](../../../../frontend/src/index.css) fuerza
  `animation-duration: 1ms` y `animation-iteration-count: 1` con `!important`
  sobre todos los elementos, y alcanza también a este. Es correcto y no pierde
  información —el papel de región viva y la etiqueta siguen ahí—, pero conviene
  tenerlo escrito: [`motion.md`](../foundations/motion.md) dice «nada se mueve en
  bucle **salvo el indicador de carga**», y con la preferencia activa tampoco se
  mueve el indicador de carga. La excepción de la regla no sobrevive a la
  preferencia, y no hace falta que sobreviva.

## Ejemplos correctos, antiusos y evidencias de prueba

Correcto — una espera sin contenido previo, que es el caso para el que existe:

```tsx
<AuthCard title="Confirmando tu correo">
  <Spinner label="Un momento…" />
</AuthCard>
```

Correcto de momento, y sustituible — la carga de una lista, que en cuanto haya un
`Skeleton` deja de ser un spinner:

```tsx
{users.isPending && <Spinner label="Cargando las personas del hogar…" />}
```

Antiusos:

| Antiuso | Por qué |
|---|---|
| `label` genérica: «Cargando…» | Con dos peticiones a la vez no dice cuál. La etiqueta nombra lo que se espera |
| Dejarlo mientras se refresca algo que ya se ve | Vaciar lo que el usuario estaba leyendo para poner un disco es peor que no avisar. Ahí va la barra fina |
| Un `Spinner` por fila de una lista | Es una espera, no cinco |
| Sustituir una pantalla entera por un spinner centrado | Es el caso del esqueleto, que además dice cuánto va a aparecer |
| Meterlo dentro de un `<button>` | No se puede: renderiza un `<p>`, y un `<p>` dentro de un `<button>` es HTML inválido |
| Ponerlo sin quitarlo al fallar | Un spinner eterno es la aplicación colgada. Al fallar se sustituye por un `Notice` |

Evidencias de prueba, en
[`App.test.tsx`](../../../../frontend/src/App.test.tsx):

- **Ninguna aserción lo nombra.** Las pruebas de verificación de correo
  —«consume el token del enlace y abre sesión»— **atraviesan** la pantalla que lo
  pinta, porque el `<Spinner>` es lo que se ve mientras el efecto resuelve, y
  esperan al encabezado siguiente con `findByRole`. Es decir: está cubierto de
  paso, no comprobado. Nadie verifica su `role="status"` ni que la etiqueta
  llegue.

## Estado de implementación y enlace al componente real

**Implementado.**
[`frontend/src/ui/primitives.tsx`](../../../../frontend/src/ui/primitives.tsx),
función `Spinner`.

Dos usos: `VerifyEmailPage` en
[`enrollment.tsx`](../../../../frontend/src/routes/enrollment.tsx) y `UsersPage`
en [`household.tsx`](../../../../frontend/src/routes/household.tsx).

### Lo que falta

- **No cabe dentro de un botón.** El elemento raíz es un `<p>`, que es contenido
  de flujo, y `<button>` solo admite contenido de frase. La ficha de
  [`Button`](button.md) apunta que un `Spinner` dentro del botón «es lo natural»
  para el estado ocupado: hoy no se puede sin partir el componente en dos —el
  disco por un lado, la línea de estado por otro—.
- **No hay `Skeleton`, que es lo que el Hito 2 necesita de verdad.** Las cuatro
  superficies nuevas —árbol, listado de artículos, listado de assets y ficha—
  cargan contenido con forma conocida, y la dirección visual pide pintar esa
  forma, no un disco. Es el componente previsto más caro de seguir aplazando,
  porque cada vista que se escriba mientras tanto se escribe con el spinner.
- **No hay barra de progreso de actualización**, la respuesta a la segunda de las
  tres situaciones. Con TanStack Query es `isFetching && !isPending`, y no hay
  nada que pintar con ese booleano.
- **Aparece al instante.** No hay retardo, así que una respuesta de 80 ms produce
  un parpadeo de disco que es peor que no mostrar nada. Lo habitual es esperar
  entre 150 y 300 ms antes de pintarlo, y esa decisión no está tomada.
- **No tiene tamaño alternativo.** 16 px sirve junto a una línea de texto; una
  espera a pantalla completa pediría más, y una dentro de un control, menos.
- **El disco no lleva `shrink-0`**, con la consecuencia descrita más arriba.
- **La alineación vertical es `items-center`** y debería ser `items-start` con
  `mt-0.5` en cuanto la etiqueta ocupe dos líneas, que es como lo resuelve
  `Notice`.

## Referencias

- [`../README.md`](../README.md): la ficha mínima.
- [`foundations/motion.md`](../foundations/motion.md): la excepción del
  indicador de carga y la reducción de movimiento.
- [`button.md`](button.md): el estado ocupado, que es la otra mitad de esto.
- [`patterns/feedback.md`](../patterns/feedback.md): las tres esperas y cuál toca
  en cada una.
- [`accessibility/`](../../accessibility/README.md)

## Historial de cambios

| Fecha | Cambio | Autor |
|---|---|---|
| 2026-08-20 | **Repasada entera contra el código y no cambia ni una afirmación**: el elemento raíz sigue siendo un `<p>` —así que sigue sin caber dentro de un `<button>`—, sigue sin haber `Skeleton`, ni barra de actualización, ni retardo antes de aparecer, ni tamaño alternativo. Lo único que se corrige es el marco temporal de la lista. | Equipo DRP |
| 2026-08-12 | Creación de la ficha sobre la implementación del Hito 1. | Equipo DRP |
