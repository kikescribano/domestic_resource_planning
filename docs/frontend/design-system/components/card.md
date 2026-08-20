# AuthCard

| Campo | Valor |
|---|---|
| Estado | Vigente |
| Responsable | Equipo DRP |
| Ámbito | frontend |
| Última revisión | 2026-08-20 |

## Propósito y situaciones de uso

La plantilla de las pantallas de **una sola cosa**: una columna centrada en la
ventana, con un titular en serif, un subtítulo opcional y lo que haga falta
debajo. Es lo que ven las siete pantallas anteriores al login —alta de hogar,
verificación, reenvío, entrada, recuperación, contraseña nueva e invitación—, y
ninguna otra.

Resuelve tres cosas de golpe, y por eso es un componente y no un `<div>` copiado
siete veces: el `<main>` de la página, la anchura de lectura de un formulario y
la única aparición de la serif por pantalla.

**El nombre engaña y conviene decirlo antes que nada: no es una tarjeta.** No
tiene fondo elevado, ni borde, ni sombra, ni esquinas redondeadas. Es una columna
sobre el papel de la página. Está detallado en
[Lo que falta](#lo-que-falta), porque el nombre va a hacer que alguien lo use
esperando otra cosa.

## Anatomía, variantes y estados

Un `<main>` en columna con tres piezas, 24 px entre ellas (`gap-6`):

1. **La cabecera**, un `<header>` en columna con 8 px internos (`gap-2`).
2. **El `<h1>`**, obligatorio.
3. **El subtítulo**, un `<p>` opcional.

Y debajo, `children`, que **entra como hermano directo del `<header>`**: los
24 px de `gap-6` separan también entre sí los elementos de primer nivel que se
le pasen. Un formulario y un párrafo de pie quedan a 24 px uno de otro sin que la
pantalla lo pida.

| Rasgo | Valor | Token |
|---|---|---|
| Elemento raíz | `<main>` | — |
| Anchura máxima | 544 px, centrada | `max-w-form` (`--container-form`), `mx-auto` |
| Margen lateral | 16 px | `px-gutter` |
| Margen vertical | 40 px | `py-10` |
| Alto mínimo | La ventana entera | `min-h-dvh` |
| Colocación | Centrada verticalmente | `justify-center` |
| Separación entre bloques | 24 px | `gap-6` |
| Titular | 28 → 40 px, serif | `text-display text-ink` |
| Subtítulo | 17 px, tinta secundaria | `text-lead text-ink-muted` |

Dos detalles que no se ven leyendo las clases:

- **La serif no se pide aquí.** El componente no lleva `font-display`: la trae el
  `h1 { font-family: var(--font-display) }` de la capa base de
  [`index.css`](../../../../frontend/src/index.css). Es la aplicación literal de
  la regla de [`typography.md`](../foundations/typography.md) —un `h1` por
  pantalla, y la serif atada a él— y significa que quien cambie ese elemento por
  un `<div role="heading">` pierde la tipografía sin enterarse.
- **`min-h-dvh` es un suelo, no una altura.** Cuando el contenido no llena la
  ventana, `justify-center` lo centra; cuando la pasa, la columna crece y la
  página desplaza con normalidad. No hay contenido inalcanzable por arriba. Y es
  `dvh` y no `vh`, que es lo que mantiene el centrado mientras la barra del
  navegador móvil aparece y desaparece.

No hay variantes ni estados.

## API pública

```ts
function AuthCard(props: {
  title: string
  subtitle?: ReactNode
  children: ReactNode
}): JSX.Element
```

| Propiedad | Qué hace |
|---|---|
| `title` | **Obligatorio**, y `string`: el `h1` de la pantalla. Sin él no hay componente |
| `subtitle` | Opcional, y **`ReactNode`, no `string`**: admite marcado, que es lo que permite meter el correo interpolado o un enlace |
| `children` | Todo lo demás. Sus hijos de primer nivel se separan con los 24 px del `gap-6` |

No acepta `className`, ni `id`, ni atributos de `<main>`.

## Comportamiento responsive y con contenido extremo

- **A 375 px** la columna ocupa el ancho disponible menos los 16 px de
  `px-gutter` a cada lado. Los 544 px de `max-w-form` no llegan a actuar hasta
  bien pasado el móvil.
- **En ultrawide no se estira.** `max-w-form` la corta a 544 px y `mx-auto`
  reparte el sobrante en margen, que es la regla del sobrante de
  [`space.md`](../foundations/space.md) aplicada al caso más simple.
- **Titular largo**: se parte en varias líneas y crece hacia abajo. A 40 px y en
  serif, un titular de ocho palabras ocupa media pantalla de móvil, así que la
  contención está en el texto y no en el componente.
- **Contenido más alto que la ventana** —el alta de hogar, que son cuatro campos,
  un botón y dos párrafos—: la columna crece por encima de `min-h-dvh` y la
  página desplaza. El centrado deja de aplicarse porque ya no sobra sitio.
- **Móvil horizontal con el teclado abierto** es el caso que
  [`look-and-feel.md`](../../product-design/look-and-feel.md) marca como
  arriesgado, y aquí no está verificado: el teclado se come la mitad de la
  altura, y qué hace `dvh` con eso depende del navegador. Es de lo que la
  auditoría del Hito 4 tiene que mirar sobre pantallas reales.

## Teclado, foco, semántica y anuncios asistivos

- **Es el landmark `main` de la página.** Eso trae una consecuencia dura:
  **no se puede anidar dentro del shell**, que ya declara el suyo en
  [`household.tsx`](../../../../frontend/src/routes/household.tsx) con
  `<main id="contenido">`. Dos `main` en la misma página es HTML inválido y deja
  a quien navega por landmarks con dos regiones principales.
- **Un `h1` por pantalla**, que es lo que permite que las pruebas localicen la
  pantalla por su encabezado de nivel 1 y no por un texto cualquiera.
- **No lleva enlace de salto al contenido**, y no le hace falta: no hay
  navegación por delante que saltarse. El shell sí lo lleva, porque ahí el primer
  tabulador cae en la navegación.
- **No mueve el foco al montarse.** Al cambiar de pantalla dentro del
  enrolamiento —de «Crea tu hogar» a «Mira tu correo»— el encabezado cambia y el
  foco se queda donde estaba. En una aplicación de una sola página eso es un
  cambio de vista que un lector de pantalla no anuncia, y no hay hoy nada que lo
  resuelva; está anotado en [`patterns/navigation.md`](../patterns/navigation.md).

## Ejemplos correctos, antiusos y evidencias de prueba

Correcto — pantalla con formulario y pie, con el subtítulo aprovechando que es
`ReactNode`:

```tsx
<AuthCard title="Mira tu correo" subtitle={`Hemos escrito a ${email}.`}>
  <Notice tone="success" title="Ya casi está">…</Notice>
  <p className="text-body-sm text-ink-muted">…</p>
</AuthCard>
```

Correcto — la misma plantilla para una espera, que es lo que la hace útil:

```tsx
<AuthCard title="Confirmando tu correo">
  <Spinner label="Un momento…" />
</AuthCard>
```

Antiusos:

| Antiuso | Por qué |
|---|---|
| Usarlo dentro del shell autenticado | Anida un `main` dentro de otro. Detrás del login la cabecera de pantalla es `PageHeading`, que vive en `household.tsx` |
| Dos `AuthCard` en la misma vista | Dos `main` y dos `h1` |
| Meter un `h2` en `children` para el mismo tema del `h1` | Si hay dos titulares, la pantalla estaba haciendo dos cosas |
| Esperar que dibuje una tarjeta | No dibuja ninguna: ni borde, ni fondo, ni sombra |
| Pasarle un `title` que cambia mientras la pantalla está viva | El titular identifica la pantalla; si cambia, es otra pantalla |

Evidencias de prueba, en
[`App.test.tsx`](../../../../frontend/src/App.test.tsx):

- **Tres aserciones localizan su `h1` por papel, nivel y nombre**:
  `findByRole('heading', { level: 1, name: 'Entrar' })`, `'Mira tu correo'` y
  `'Este enlace ya no vale'`. Eso verifica lo que de verdad aporta el
  componente —que cada pantalla tiene un encabezado de nivel 1 con el texto
  visible— y es la razón por la que las pruebas pueden decir «estoy en esta
  pantalla» sin depender de la maquetación.
- Las otras aserciones de encabezado de nivel 1 del fichero —«Tu hogar»,
  «Personas»— **no son de este componente**: salen de `PageHeading`, dentro del
  shell.
- No hay ninguna prueba de la anchura, del centrado ni del landmark.

## Estado de implementación y enlace al componente real

**Implementado.**
[`frontend/src/ui/primitives.tsx`](../../../../frontend/src/ui/primitives.tsx),
función `AuthCard`.

**Once usos, todos en
[`enrollment.tsx`](../../../../frontend/src/routes/enrollment.tsx)**, repartidos
entre sus siete pantallas: cuatro de ellas lo instancian dos veces porque tienen
dos estados —el formulario y el «ya está enviado»— y cada uno es una pantalla
distinta con su propio titular.

### Lo que falta

- **El nombre no describe lo que es.** Se llama `AuthCard` y no es una tarjeta:
  es una plantilla de página de una columna. Lo que
  [`shape-and-elevation.md`](../foundations/shape-and-elevation.md) llama tarjeta
  lleva `surface-raised`, borde y `rounded-lg`, y aquí no hay ninguno de los
  tres. O se renombra a lo que es —una plantilla— o se convierte en lo que dice
  ser; lo que no puede quedarse es la ambigüedad, porque el Hito 2 **sí** trae
  tarjetas de verdad: cada fila de un listado en móvil es una.
- ~~**No hay equivalente para las pantallas de dentro.**~~ **Resuelto en el Hito 2
  de la Fase 2**, y por donde esta ficha señalaba: `PageHeading` salió de
  `household.tsx` y es hoy un componente de
  [`primitives.tsx`](../../../../frontend/src/ui/primitives.tsx), con su `h1` y su
  acción opcional a la derecha. **No tiene ficha propia**, que es lo que queda
  pendiente de él.
- **No tiene sitio para la ilustración.**
  [`iconography.md`](../foundations/iconography.md) admite ilustración en tres
  sitios, y el onboarding es uno; la dirección visual habla de «la ilustración de
  la casa» en el alta de hogar. No hay ranura donde ponerla ni ilustración que
  poner.
- **No tiene pie ni ranura de vuelta atrás.** Los enlaces de «¿Ya tienes
  cuenta?» y «Volver a entrar» se cuelan como último hijo de `children`, y cada
  pantalla los maqueta a su manera: una con `flex flex-col gap-1`, otra con un
  `<p>` suelto, otra con un `<Link>` pelado.
- **No gestiona el título del documento.** Las siete pantallas comparten el
  `<title>DRP</title>` de
  [`index.html`](../../../../frontend/index.html), así que el historial del
  navegador y las pestañas no distinguen una de otra. El componente ya recibe el
  texto que haría falta.

## Referencias

- [`../README.md`](../README.md): la ficha mínima.
- [`foundations/space.md`](../foundations/space.md): las anchuras de composición
  y la regla del sobrante.
- [`foundations/typography.md`](../foundations/typography.md): la serif, el `h1`
  y por qué no baja de 20 px.
- [`patterns/form.md`](../patterns/form.md): lo que va dentro.
- [`patterns/navigation.md`](../patterns/navigation.md): la otra plantilla, la de
  detrás del login.

## Historial de cambios

| Fecha | Cambio | Autor |
|---|---|---|
| 2026-08-20 | **`PageHeading` dejó de estar dentro de `household.tsx`** en el Hito 2 de la Fase 2 y es hoy un componente de `primitives.tsx`, exactamente por donde esta ficha señalaba. Se tacha esa entrada de «Lo que falta» y se anota lo que queda de ella: no tiene ficha propia. | Equipo DRP |
| 2026-08-12 | Creación de la ficha sobre la implementación del Hito 1. | Equipo DRP |
