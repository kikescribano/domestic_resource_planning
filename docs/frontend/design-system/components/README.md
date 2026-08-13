# Componentes

| Campo | Valor |
|---|---|
| Estado | Borrador |
| Responsable | Equipo DRP |
| Ámbito | frontend |
| Última revisión | 2026-08-13 |

## Propósito

Documentar los componentes reutilizables de la interfaz: qué hacen, qué API
exponen, cómo se comportan al estrecharse la pantalla y qué se les puede exigir
con el teclado y con un lector de pantalla.

Estas fichas **describen lo que el código hace hoy**, no lo que convendría que
hiciera. Cuando un hito necesita algo que la primitiva todavía no tiene, va en
un apartado propio de «Lo que falta» y se marca como previsto, en lugar de
escribirse como si existiera. Esa distinción es el único motivo por el que estas
fichas sirven de algo: la documentación del Hito 1 falló exactamente por ahí.

Hay un segundo tipo de ficha, que estrena el Hito 3: la de una anatomía **entera
en previsto**, escrita antes de existir para que la implementación se guíe por
ella. No es una excepción a la regla de arriba sino su forma extrema — se marca
en la cabecera, se avisa en la primera línea y no afirma nada del código, porque
no hay código del que hablar. Es lo mismo que hacen dos de los cinco
[patrones](../patterns/README.md).

El índice de cada ficha es la **[ficha mínima de un componente](../README.md)**,
con sus siete puntos. No se reordena ni se recorta.

## Alcance

### Incluido

- Una ficha por componente que existe en
  [`frontend/src/ui/primitives.tsx`](../../../../frontend/src/ui/primitives.tsx).
- Una ficha por anatomía que el hito en curso tiene que construir y que ninguna
  de las anteriores cubre, marcada como **prevista** de arriba abajo.
- El estado de implementación de cada uno y el hueco que deja para el hito en
  curso.

### Fuera de alcance

- Las decisiones visuales que un componente consume sin decidir —color,
  tipografía, espacio, densidad, forma, iconografía y movimiento—, en
  [`foundations/`](../foundations/README.md).
- Los nombres y valores de los tokens, en [`tokens/`](../tokens/README.md).
- La composición de varios componentes en una vista —listado, jerarquía,
  formulario, feedback y navegación—, en [`patterns/`](../patterns/README.md).
- Terminología, voz y etiquetas, que irán en `content/`.

## Registro

| Componente | Ficha | Estado | Dónde vive |
|---|---|---|---|
| `Button` | [`button.md`](button.md) | **Implementado** | [`primitives.tsx`](../../../../frontend/src/ui/primitives.tsx) |
| `Field` | [`field.md`](field.md) | **Implementado** | [`primitives.tsx`](../../../../frontend/src/ui/primitives.tsx) |
| `SelectField` | Sin ficha | **Implementado** en el Hito 2 | [`primitives.tsx`](../../../../frontend/src/ui/primitives.tsx) |
| `Notice` | [`notice.md`](notice.md) | **Implementado** | [`primitives.tsx`](../../../../frontend/src/ui/primitives.tsx) |
| `StatusBadge` | [`status-badge.md`](status-badge.md) | **Implementado** | [`primitives.tsx`](../../../../frontend/src/ui/primitives.tsx) |
| `Spinner` | [`spinner.md`](spinner.md) | **Implementado** | [`primitives.tsx`](../../../../frontend/src/ui/primitives.tsx) |
| `AuthCard` | [`card.md`](card.md) | **Implementado** | [`primitives.tsx`](../../../../frontend/src/ui/primitives.tsx) |
| `PageHeading` | Sin ficha | **Implementado** en el Hito 2 | [`primitives.tsx`](../../../../frontend/src/ui/primitives.tsx) |
| `EmptyState` | Sin ficha | **Implementado** en el Hito 2 | [`primitives.tsx`](../../../../frontend/src/ui/primitives.tsx) |
| `UploadField` | [`upload-field.md`](upload-field.md) | **Previsto** (Hito 3) | — |
| `FileGallery` | [`file-gallery.md`](file-gallery.md) | **Previsto** (Hito 3) | — |
| `Avatar` | [`avatar.md`](avatar.md) | **Previsto** (Hito 3) | — |

**Nueve componentes implementados en un único fichero de 376 líneas, y solo seis
con ficha**: `SelectField`, `PageHeading` y `EmptyState` entraron con el Hito 2 y
se quedaron sin la suya. Las tres del Hito 3 van al revés —ficha sin
implementación—, y su primera línea lo dice.

Sigue sin haber un directorio por componente, ni una carpeta de historias, ni una
galería: mientras quepan en un fichero que se lee de una sentada, partirlos
añadiría estructura sin resolver nada. Con tres piezas más —y la de la galería
trae rejilla, celda y sus marcadores— ese «de una sentada» se acaba, y la
decisión de dónde partir está más abajo.

## Lo que falta por construir

Todos estos están **previstos**: ninguno tiene implementación hoy.

Del Hito 2, que se cerró sin ellos. Los patrones que los usan sí están escritos y
dicen en cada punto qué pieza falta: ver
[`patterns/`](../patterns/README.md).

| Componente previsto | Por qué se pide |
|---|---|
| `Combobox` | El artículo se busca antes de dar entrada a un consumible: `GET /articles` lleva un parámetro `q` que existe justamente para alimentar un autocompletado |
| `Skeleton` | La primera carga de una vista se pinta con la forma real del contenido, no con un `Spinner`. Es lo que fija [`look-and-feel.md`](../../product-design/look-and-feel.md) y lo que la primitiva de carga actual no puede hacer |
| `Toast` | El aviso efímero de esquina, con cierre y con deshacer. `Notice` es un aviso **en el sitio donde ocurrió**, que es otra cosa |
| `Dialog` y hoja inferior | La confirmación de baja y las operaciones de existencias, que en móvil son hoja y en escritorio diálogo |
| `Pagination` | Las colecciones del contrato paginan todas igual; hoy no hay ningún control que lo pinte |

Del Hito 3, que es lo que estas tres fichas nuevas especifican:

| Componente previsto | Por qué se pide |
|---|---|
| [`UploadField`](upload-field.md) | Subir con progreso **real** —`XMLHttpRequest`, porque `fetch` no lo da—, con la subida separada de adjuntar y los tres errores del contrato |
| [`FileGallery`](file-gallery.md) | La rejilla de miniaturas de una entidad, con el marcador del PDF y la tolerancia a que una URL firmada caduque en pantalla |
| [`Avatar`](avatar.md) | La cara de una persona, que se pinta en sitios donde no hay ninguna subida y cuya subida es otra operación distinta |

Dos que el Hito 3 pide y **no** tienen ficha porque no son anatomía nueva: el
medidor de cuota de `GET /storage`, que es la misma barra determinada de
`UploadField` con otra semántica —`meter` en lugar de `progressbar`—, y la celda
de fichero, que sale de `FileGallery` el día que la pantalla de almacenamiento
del hogar la necesite. Los dos están anotados en la ficha que les corresponde.

## Reglas que ningún componente puede saltarse

Son las mismas de [`tokens/`](../tokens/README.md) y de
[`look-and-feel.md`](../../product-design/look-and-feel.md), recordadas aquí
porque es donde se incumplen:

1. **Nada de colores crudos.** Si falta un token, se añade antes de usarlo.
2. **El foco no se declara en un componente.** Está en la capa base de
   [`index.css`](../../../../frontend/src/index.css), para que ninguno pueda
   olvidarlo — ni quitarlo.
3. **Nada se dice solo con color.** Un estado lleva color, icono y etiqueta; un
   campo con error lleva borde, icono y mensaje.
4. **44 px de objetivo táctil** en todo lo pulsable (`min-h-touch`).
5. **Una acción principal por pantalla.** Solo `variant="primary"` pinta el
   relleno de acento.
6. **La serif no entra en una fila de listado.**

## Decisiones abiertas

- **`lucide-react` sigue sin estar en
  [`frontend/package.json`](../../../../frontend/package.json).**
  [`iconography.md`](../foundations/iconography.md) adopta Lucide como juego de
  iconos, pero los cuatro iconos que hay hoy están dibujados a mano dentro de
  `primitives.tsx`, siguiendo su geometría —cuadrícula de 24, trazo 1,75— sin ser
  Lucide. El Hito 2 se cerró sin dar de alta la dependencia y sin los cinco
  iconos de estado del dominio, así que `StatusBadge` sigue diciendo el estado con
  color y etiqueta, sin icono. El Hito 3 añade a la lista el icono de documento,
  el de persona y el de subir: cada hito que pasa hace más caro dibujarlos a mano
  y más raro no haber decidido.
- **Dónde vive el segundo componente.** `primitives.tsx` va por 376 líneas y nueve
  componentes, y las tres piezas del Hito 3 no son pequeñas. El criterio natural
  —los primitivos por un lado, las piezas con estado propio y llamadas a la API
  por otro— hay que fijarlo antes de escribirlas, no después.
- **El foco de un control cuyo elemento enfocable está oculto.** La regla 2 dice
  que el foco no se declara en un componente, y `UploadField` no puede cumplirla:
  su elemento enfocable es un `<input type="file">` invisible, así que el anillo
  de la capa base no se ve. O la capa base gana una regla para ese patrón, o esta
  es la primera excepción documentada. Está razonado en
  [`upload-field.md`](upload-field.md).

## Referencias

- [`../README.md`](../README.md): la ficha mínima de un componente.
- [`foundations/`](../foundations/README.md) y [`tokens/`](../tokens/README.md).
- [`accessibility/`](../../accessibility/README.md): la auditoría de contraste y
  lo que todavía no está comprobado sobre pantallas reales.
- [`ADR-006`](../../../common/architecture/decisions/ADR-006-frontend-stack-and-design-system.md)

## Historial de cambios

| Fecha | Cambio | Autor |
|---|---|---|
| 2026-08-12 | Creación del directorio con las seis fichas de los componentes que existen, y el registro de los que el Hito 2 va a pedir. | Equipo DRP |
| 2026-08-13 | Se corrige el registro, que había quedado desfasado al cerrar el Hito 2: son **nueve** componentes en 376 líneas, no seis en 266, y `SelectField`, `PageHeading` y `EmptyState` están implementados sin ficha. Se dan de alta las tres anatomías del Hito 3 —`UploadField`, `FileGallery` y `Avatar`—, previstas y con ficha, y se anota la excepción de foco que trae la primera. | Equipo DRP |
