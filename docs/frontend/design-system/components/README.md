# Componentes

| Campo | Valor |
|---|---|
| Estado | Borrador |
| Responsable | Equipo DRP |
| Ámbito | frontend |
| Última revisión | 2026-08-18 |

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
| `UploadField` | [`upload-field.md`](upload-field.md) | **Implementado** | [`files.tsx`](../../../../frontend/src/ui/files.tsx) |
| `FileGallery` | [`file-gallery.md`](file-gallery.md) | **Implementado** | [`files.tsx`](../../../../frontend/src/ui/files.tsx) |
| `Avatar` | [`avatar.md`](avatar.md) | **Implementado** | [`files.tsx`](../../../../frontend/src/ui/files.tsx) |
| `QuotaMeter` | Sin ficha | **Implementado** en el Hito 3 | [`files.tsx`](../../../../frontend/src/ui/files.tsx) |
| `LoanExternalPage` | [`loan-external-page.md`](loan-external-page.md) | **Implementado** como `ExternalLoanPage` | [`routes/loans.tsx`](../../../../frontend/src/routes/loans.tsx) |
| `SuppliersPage` | [`suppliers-page.md`](suppliers-page.md) | **Implementada** — ficha escrita antes que la pantalla, Fase 2 Hito 2 | [`routes/suppliers.tsx`](../../../../frontend/src/routes/suppliers.tsx) |

**Trece componentes reutilizables —nueve en `primitives.tsx` y cuatro en
`files.tsx`— más dos pantallas, y once de las quince filas tienen ficha**:
`SelectField`, `PageHeading` y `EmptyState` entraron con el Hito 2 sin la suya, y
`QuotaMeter` con el Hito 3, documentado como variante dentro de la ficha de
`UploadField` y acabando en pieza aparte.

Porque `LoanExternalPage` no es una primitiva sino **una pantalla entera sin
sesión y sin shell**, la única superficie del producto que se ve sin cuenta: por
eso vive en su ruta y no en el sistema de diseño, y su ficha empieza argumentando
por qué aun así es componente y no patrón. Es también la cuarta ficha escrita
**antes** que su implementación, y la que mejor demuestra para qué sirve hacerlo:
encontró un hueco del contrato cuando no había código, y dejó escrita una regla de
anuncios que al cerrar la fase delató un defecto real.

`SuppliersPage` está aquí por lo mismo y por una razón más: es **la pantalla de un
módulo**, así que no se monta sola sino dentro de su guardián, y eso es parte de
su anatomía y no un detalle de enrutado. Su ficha se escribió antes que ella y
volvió a pagar en el sitio de siempre —un rótulo que chocaba en nombre accesible
con otro de la misma pantalla—, que es un fallo que no se ve mirándola.

Sigue sin haber un directorio por componente, ni una carpeta de historias, ni una
galería: mientras quepan en un fichero que se lee de una sentada, partirlos
añadiría estructura sin resolver nada. Con tres piezas más —y la de la galería
trae rejilla, celda y sus marcadores— ese «de una sentada» se acaba, y la
decisión de dónde partir está más abajo.

## Lo que falta por construir

Todos estos están **previstos**: ninguno tiene implementación hoy. **Con la Fase 1
cerrada, ya no los pide ningún hito en curso**: quedan como lista de lo que el
sistema de diseño no cubre, y cada uno se construirá cuando una pantalla lo
necesite de verdad.

Los cinco del Hito 2, que se cerró sin ellos y siguen sin construirse. Los
patrones que los usan sí están escritos y dicen en cada punto qué pieza falta: ver
[`patterns/`](../patterns/README.md).

| Componente previsto | Por qué se pide |
|---|---|
| `Combobox` | El artículo se busca antes de dar entrada a un consumible: `GET /articles` lleva un parámetro `q` que existe justamente para alimentar un autocompletado |
| `Skeleton` | La primera carga de una vista se pinta con la forma real del contenido, no con un `Spinner`. Es lo que fija [`look-and-feel.md`](../../product-design/look-and-feel.md) y lo que la primitiva de carga actual no puede hacer |
| `Toast` | El aviso efímero de esquina, con cierre y con deshacer. `Notice` es un aviso **en el sitio donde ocurrió**, que es otra cosa |
| `Dialog` y hoja inferior | La confirmación de baja y las operaciones de existencias, que en móvil son hoja y en escritorio diálogo |
| `Pagination` | Las colecciones del contrato paginan todas igual; hoy no hay ningún control que lo pinte |

Y uno del Hito 4, que tampoco se construyó:

| Componente previsto | Por qué se pide |
|---|---|
| `BlockingError` | La vista entera de error, con ilustración y como mucho una salida. El Hito 4 resolvió a mano el más fácil de sus cuatro casos —el enlace roto, que no lleva acción— con `BrokenLink`, local a su ruta; los otros tres siguen esperándola. Está argumentado en [`loan-external-page.md`](loan-external-page.md) |

Lo del Hito 3 **sí se construyó**: `UploadField`, `FileGallery` y `Avatar` están
en el registro de arriba, y con ellos el medidor de cuota, que su ficha
documentaba como variante y acabó siendo pieza aparte. La celda de fichero sigue
sin salir de `FileGallery`, y saldrá el día que la pantalla de almacenamiento del
hogar la necesite.

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
- ~~**Dónde vive el segundo componente.**~~ **Resuelto en el Hito 3**, y con el
  criterio que se había propuesto: los primitivos siguen en `primitives.tsx` (376
  líneas, nueve componentes) y las piezas con estado propio y peticiones en curso
  fueron a `files.tsx` (441 líneas, cuatro). El Hito 4 confirmó el criterio por el
  otro extremo: `ExternalLoanPage` no entró en ninguno de los dos, porque una
  pantalla no es una pieza del sistema de diseño aunque tenga ficha.
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
| 2026-08-14 | Las tres anatomías del Hito 3 pasan de previstas a **implementadas**, en un fichero propio —`files.tsx`— y no en `primitives.tsx`: traen estado y una petición en curso, que es otra clase de complejidad. Se añade `QuotaMeter`, que la ficha de `UploadField` documentaba como variante y acabó siendo pieza aparte. | Equipo DRP |
| 2026-08-13 | Se corrige el registro, que había quedado desfasado al cerrar el Hito 2: son **nueve** componentes en 376 líneas, no seis en 266, y `SelectField`, `PageHeading` y `EmptyState` están implementados sin ficha. Se dan de alta las tres anatomías del Hito 3 —`UploadField`, `FileGallery` y `Avatar`—, previstas y con ficha, y se anota la excepción de foco que trae la primera. | Equipo DRP |
| 2026-08-18 | Fase 2, Hito 2: entra `SuppliersPage`, la segunda pantalla con ficha propia y la **quinta escrita antes que su implementación**. Volvió a pagar: la especificación le daba al filtro el mismo rótulo que al campo del formulario de alta, y dos controles con el mismo nombre accesible en una pantalla son indistinguibles para quien no la ve. | Equipo DRP |
| 2026-08-17 | Cierre documental de la Fase 1. `LoanExternalPage` pasa a **implementada** —vive en su ruta y no en el sistema de diseño, porque es una pantalla— y `QuotaMeter` entra en el registro, del que faltaba. Se corrige «lo que falta por construir», que seguía dando por no construidas las tres piezas del Hito 3 mientras el registro las daba por hechas: la lista queda en los cinco del Hito 2 más `BlockingError`, y deja de estar atada a un hito en curso porque ya no hay ninguno. | Equipo DRP |
