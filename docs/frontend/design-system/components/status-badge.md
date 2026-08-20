# StatusBadge

| Campo | Valor |
|---|---|
| Estado | Vigente |
| Responsable | Equipo DRP |
| Ámbito | frontend |
| Última revisión | 2026-08-20 |

## Propósito y situaciones de uso

Una etiqueta corta y coloreada que dice **en qué situación está una cosa**. Es la
pieza donde se cobra la regla «nada se dice solo con color», porque un estado es
lo primero que se lee de un vistazo en una lista y lo primero que desaparece para
quien no distingue el verde del ámbar.

Hay que separar dos cosas que se parecen y no lo son, y el sistema de color las
separa a propósito:

| Qué se está diciendo | Con qué se dice |
|---|---|
| Lo que le ha pasado a una **petición** —falló, salió bien, conviene mirarlo— | [`Notice`](notice.md) |
| En qué situación está una **cosa del hogar** —un asset, una existencia, un préstamo— | `StatusBadge` |

Esa frontera está razonada en [`color.md`](../foundations/color.md): un préstamo
vencido no es un error del sistema, y el día que el rojo de error cambie de tono
el inventario no debe repintarse detrás.

**Lo que el componente hace hoy no respeta todavía esa frontera**, y conviene
saberlo antes de seguir leyendo: sus tonos son los del feedback, no los de los
estados del dominio. Está detallado en [Lo que falta](#lo-que-falta).

## Anatomía, variantes y estados

Un único `<span>` en línea, sin estructura interna:

| Rasgo | Valor | Token |
|---|---|---|
| Disposición | `inline-flex items-center`, 4 px entre piezas | `gap-1` |
| Relleno | 8 px horizontal, 2 px vertical | `px-2 py-0.5` |
| Radio | Completo | `rounded-full` |
| Texto | 13 px, peso medio | `text-caption font-medium` |
| Fondo | La variante `-soft` del tono | — |
| Color de texto | El tono pleno | — |

El redondeo completo es el que le corresponde:
[`shape-and-elevation.md`](../foundations/shape-and-elevation.md) reserva la
píldora para avatares y distintivos de estado, y para nada más.

Los tonos de feedback, el neutro y —desde la pasada de chips del 2026-08-20— el
de marca, tal y como están escritos en `STATUS_TONES` (los cinco de dominio
tienen su tabla más abajo):

| Tono | Clases | De dónde sale el color |
|---|---|---|
| `accent` | `bg-accent-soft text-accent-ink` | **La marca**: lo pendiente que no es feedback ni estado —«sin leer», «administra»— |
| `info` | `bg-info-soft text-info` | Feedback del sistema |
| `success` | `bg-success-soft text-success` | Feedback del sistema |
| `warning` | `bg-warning-soft text-warning` | Feedback del sistema |
| `danger` | `bg-danger-soft text-danger` | Feedback del sistema |
| `neutral` | `bg-state-decommissioned-soft text-state-decommissioned` | **Estado del dominio** |

El tipo de la propiedad admite las tres familias —feedback, marca y los cinco
de dominio— desde la Fase 2; la frontera que esta ficha vigila es no pedir un
tono de feedback para un estado del dominio, no que falte la forma de pedirlo.

No hay estados. Un distintivo está o no está, y no reacciona al puntero, al foco
ni a nada: no es interactivo.

**Y no lleva icono.** El componente pinta `children` y nada más. El icono fijo
que [`iconography.md`](../foundations/iconography.md) exige para cada estado
tiene que meterlo quien lo usa, dentro de `children` —para eso está el `gap-1`—,
y hoy no lo mete nadie.

## API pública

```ts
function StatusBadge(props: {
  tone: NoticeTone | 'accent' | 'neutral' | 'available' | 'lent' | 'overdue' | 'decommissioned' | 'out-of-stock'
  children: ReactNode
}): JSX.Element
```

| Propiedad | Qué hace |
|---|---|
| `tone` | **Obligatoria**, y sin valor por defecto: un distintivo sin situación no significa nada |
| `children` | La etiqueta. Es `ReactNode`, así que admite el icono al lado del texto |

No acepta `className`, ni `title`, ni atributos de `<span>`. La API es cerrada,
como la de `Notice`.

## Comportamiento responsive y con contenido extremo

- **Es `inline-flex`**, así que ocupa lo que ocupe su texto y se coloca donde lo
  ponga el contenedor. En las filas de hoy va dentro de un
  `flex flex-wrap items-center justify-between`, y al estrecharse la pantalla
  baja a la línea siguiente en lugar de aplastar el nombre.
- **La etiqueta no se trunca ni se parte.** No hay `truncate` ni `whitespace`
  declarados, así que una etiqueta larga ensancha la píldora. Con las cinco
  etiquetas del dominio —«Disponible», «Prestado», «Vencido», «Dado de baja»,
  «Sin existencias»— cabe de sobra a 375 px; con un texto libre, no está
  garantizado.
- **No cumple el objetivo táctil, y no tiene por qué.** No es pulsable: los 44 px
  de `min-h-touch` obligan a lo que se toca, y un distintivo no se toca. Si algún
  día se vuelve un filtro pulsable, deja de ser este componente.
- **A 375 px** dentro de una fila estrecha, [`iconography.md`](../foundations/iconography.md)
  admite reducir la etiqueta a su inicial visual siempre que no se pierdan ni el
  icono ni el nombre accesible. El componente **no ofrece nada para hacerlo**: hoy
  o se pinta el texto entero o se pinta otro texto.

## Teclado, foco, semántica y anuncios asistivos

- **No es enfocable y no entra en el orden de tabulación.** Es un `<span>` de
  texto.
- **No tiene papel ARIA ni región viva.** Un distintivo que cambia de valor no se
  anuncia solo; si el cambio importa, lo anuncia quien lo provoca. Es lo
  correcto: trescientas filas con región viva serían trescientos anuncios.
- **Su nombre accesible es su texto**, sin más, porque no hay `aria-label` que lo
  sustituya. De ahí que la etiqueta no pueda quedarse en un icono suelto: sin
  texto, el distintivo desaparece para un lector de pantalla.
- **El color no es el único portador… a medias.** Van el color y la etiqueta; el
  icono, que es la tercera pieza obligatoria, depende de quien lo pase.

Contraste, medido por
[`check-contrast.py`](../../../../scripts/check-contrast.py) sobre los valores
reales de `index.css`, en claro y en oscuro:

| Par | Claro | Oscuro |
|---|---|---|
| Distintivo `DISPONIBLE` | 6,10:1 | 7,50:1 |
| Distintivo `PRESTADO` | 5,68:1 | 8,08:1 |
| Distintivo `VENCIDO` | 6,00:1 | 5,65:1 |
| **Distintivo `DADO DE BAJA`** | **4,67:1** | 5,08:1 |
| Distintivo `SIN EXISTENCIAS` | 6,35:1 | 6,82:1 |

El de dado de baja es **el peor par de texto de todo el sistema**: cumple los
4,5:1 con 0,17 de margen, y es el primero que hay que volver a medir si alguien
toca la paleta (ver [`accessibility/`](../../accessibility/README.md)).

Esos cinco pares son los de los tokens `state-*`. Los cuatro tonos de feedback
que el componente usa de verdad están medidos aparte —«distintivo de éxito»
6,10:1, «de aviso» 5,68:1, «de error» 6,00:1, «informativo» 6,35:1 y «de
acento» 5,57:1 en claro— y dan **exactamente los mismos números** donde los
valores coinciden. Ese es
el motivo de que la confusión de tokens no se vea: no se ve, se mide.

## Ejemplos correctos, antiusos y evidencias de prueba

Correcto — el único uso que existe hoy, en la lista de personas del hogar:

```tsx
<StatusBadge tone={user.role === 'HOUSEHOLD_ADMIN' ? 'accent' : 'neutral'}>
  {ROLE_LABEL[user.role]}
</StatusBadge>
```

Merece un comentario, porque es un uso legítimo que **no es un estado del
dominio**: un papel no es una situación por la que la cosa pasa, es un atributo
que la describe. Funciona y se ve bien; lo que no se puede es deducir de aquí que
así se pintan los estados del asset.

Antiusos:

| Antiuso | Por qué |
|---|---|
| Un distintivo sin texto, solo con icono | El componente no da nombre accesible por su cuenta: quedaría mudo |
| `tone="danger"` para un estado que no pide actuar | `danger` es el rojo del sistema. Un asset dado de baja no es un fallo: es `neutral` |
| Dos distintivos en la misma fila | El presupuesto de calidez da **un** color por fila (ver [`density.md`](../foundations/density.md)). Si hacen falta dos, uno de los dos era un metadato en tinta |
| Usarlo para el resultado de una operación | Eso es feedback y va en [`Notice`](notice.md) |
| Meterlo dentro de un botón para hacerlo pulsable | Un objetivo pulsable son 44 px, y esto mide 22. Sería un filtro, que es otro componente |

**Evidencias de prueba: ninguna, y ya no es «el más barato de tapar».** Cuando se
escribió esto había un solo uso; hoy hay **diez repartidos por seis pantallas** —
inventario, catálogo, personas, préstamos y mantenimiento— y ninguna prueba
afirma nada sobre el distintivo. Las pruebas que lo montan comprueban lo que hay
a su lado: el correo de la persona, el nombre del asset, la fecha del préstamo.
Sigue siendo un hueco conocido, y ahora uno más ancho.

## Estado de implementación y enlace al componente real

**Implementado.**
[`frontend/src/ui/primitives.tsx`](../../../../frontend/src/ui/primitives.tsx),
función `StatusBadge`.

**Diez usos en seis pantallas**, que era uno solo cuando se escribió esta ficha:
el papel de cada persona en `UsersPage`, el estado de un asset en el inventario y
en su ficha, el artículo retirado del catálogo, los tres estados del préstamo y
cuatro cosas de mantenimiento. Se usan **los nueve tonos menos `warning`**.

### Lo que falta

- ~~**Sus tonos son los del feedback, no los de los estados del dominio.**~~
  **Corregido en el Hito 2 de la Fase 2**, y con la corrección que esta ficha
  proponía: `tone` admite hoy las dos familias, y la de dominio
  —`available`, `lent`, `overdue`, `decommissioned`, `out-of-stock`— está cableada
  a los tokens `state-*` en lugar de prestar el rojo de un aviso. La frontera que
  [`color.md`](../foundations/color.md) creó esos tokens para levantar deja de
  cruzarse. Queda **un resto**: `neutral` sigue apuntando a
  `state-decommissioned`, así que son dos nombres para el mismo par y el sistema
  no puede distinguir «sin estado» de «dado de baja».
- **No hay icono.** El comentario del código dice «un estado del dominio: color,
  etiqueta e icono. Los tres juntos, siempre», y el componente solo pinta
  `children`. Los cinco iconos que
  [`iconography.md`](../foundations/iconography.md) fija —`check-circle`,
  `hand-helping`, `alarm-clock`, `archive`, `package-open`— no están en ninguna
  parte, y su dependencia `lucide-react` tampoco está en
  [`package.json`](../../../../frontend/package.json). **Sin icono, el criterio
  1.4.1 se sostiene solo por la etiqueta de texto.**
- **El componente no sabe nada del dominio.** Cada pantalla tendría que repetir la
  correspondencia estado → tono + icono + etiqueta, que es exactamente el sitio
  donde se olvida una. Debería ser el propio componente quien reciba el estado y
  resuelva las tres piezas.
- **No hay etiqueta corta para fila estrecha**, que es la excepción que
  `iconography.md` admite a 375 px.
- **El mapa de tonos está tipado como `Record<string, string>`**, no como
  `Record<NoticeTone | 'neutral', string>`. Hoy no puede fallar, porque la
  propiedad es una unión cerrada; el día que se añada un tono al tipo y se olvide
  en el mapa, TypeScript no dirá nada y saldrá un `<span>` con la clase
  `undefined` y sin color.
- **El estado del dominio que el Hito 2 pone en juego no está en el contrato como
  un estado más.** `AssetStatus` enumera `AVAILABLE`, `LENT` y `DECOMMISSIONED`;
  «sin existencias» es un `CONSUMABLE` con `quantity` a cero y `OVERDUE` vive en
  el préstamo. Es decir: **dos de los cinco distintivos se calculan en el cliente**,
  y hace falta decidir dónde se calculan para no hacerlo en cada vista.

## Referencias

- [`../README.md`](../README.md): la ficha mínima.
- [`foundations/color.md`](../foundations/color.md): feedback del sistema frente
  a estados del dominio, y por qué tienen tokens distintos.
- [`foundations/iconography.md`](../foundations/iconography.md): el icono fijo de
  cada estado.
- [`notice.md`](notice.md): el otro lado de la frontera.
- [`patterns/listing.md`](../patterns/listing.md): dónde cae el distintivo dentro
  de una fila.
- [`accessibility/`](../../accessibility/README.md): los pares medidos.

## Historial de cambios

| Fecha | Cambio | Autor |
|---|---|---|
| 2026-08-20 | Entra el tono **`accent`** —la marca, para lo pendiente que no es feedback ni estado: «sin leer», «administra»— con su par medido (5,57:1 y 7,40:1). Se remiden los pares tras el giro del gris al pino y del ámbar al oro: el peor sigue siendo «dado de baja», ahora 4,67:1. | Equipo DRP |
| 2026-08-20 | **Se corrige lo que la ficha daba por pendiente y ya no lo estaba**: los tonos de dominio existen desde el Hito 2 de la Fase 2, cableados a los tokens `state-*` y con los cinco nombres que esta ficha proponía, así que la frontera de `color.md` deja de cruzarse — queda el resto de que `neutral` y `decommissioned` apuntan al mismo par. Y los datos de uso estaban congelados: eran «un solo uso y dos tonos», y son **diez usos en seis pantallas con nueve tonos**, lo que convierte el hueco de pruebas de barato en ancho. | Equipo DRP |
| 2026-08-12 | Creación de la ficha sobre la implementación del Hito 1. | Equipo DRP |
