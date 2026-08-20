# Componentes

| Campo | Valor |
|---|---|
| Estado | Vigente |
| Responsable | Equipo DRP |
| Ámbito | frontend |
| Última revisión | 2026-08-20 |

## Propósito

Documentar los componentes reutilizables de la interfaz: qué hacen, qué API
exponen, cómo se comportan al estrecharse la pantalla y qué se les puede exigir
con el teclado y con un lector de pantalla.

Estas fichas **describen lo que el código hace hoy**, no lo que convendría que
hiciera. Cuando un hito necesita algo que la primitiva todavía no tiene, va en
un apartado propio de «Lo que falta» y se marca como previsto, en lugar de
escribirse como si existiera. Esa distinción es el único motivo por el que estas
fichas sirven de algo: la documentación del Hito 1 falló exactamente por ahí.

> **Y describir lo que el código hace hoy caduca solo**, que es lo que el repaso
> del 2026-08-20 encontró: dos fichas seguían diciendo «Previsto. No existe» de
> componentes construidos en el Hito 3, y otras tres arrastraban entradas de «Lo
> que falta» que otro hito había resuelto sin volver por aquí. **Una ficha se
> queda desfasada por omisión, no por error**, así que la regla práctica es la
> misma que el `CLAUDE.md` fija para los documentos autoprogramados: quien
> construye lo que una ficha daba por pendiente, la cierra en el mismo hito.
>
> Lo que **no** se hace al repasar es lo contrario del arreglo: si una ficha
> describe algo que el componente no tiene, se marca como pendiente y no se borra
> la especificación. Es lo que pasó con **Cancelar** en `upload-field.md`, que
> llevaba dos fases descrito sin existir y acabó construyéndose por estar escrito
> — y con el **nombre accesible de la celda** en `file-gallery.md`, que estaba
> especificado al detalle, incumplido en el código y sin ninguna prueba que lo
> notara. Las dos veces, lo que hizo falta para arreglarlo fue que siguiera
> escrito.

> **Un detalle de vocabulario sin resolver.** El campo `Estado` de la cabecera es
> el **estado documental** —`Borrador`, `En revisión`, `Vigente`, `Obsoleto`, que
> define [`docs/README.md`](../../../README.md)— y no el de la implementación, que
> vive en su propio apartado al final de cada ficha. Seis de estas doce lo usan
> así; las otras seis escriben ahí `Implementado`, que no es ninguno de los cuatro.
> Queda anotado en vez de unificado a la brava: decidir cuál de las dos lecturas
> vale es una revisión del convenio y no de estas fichas.

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
| `DangerZone` | [`danger-zone.md`](danger-zone.md) | **Implementado** — ficha escrita antes que el componente, cierre de huecos Hito 0 | [`primitives.tsx`](../../../../frontend/src/ui/primitives.tsx) |
| `LoanExternalPage` | [`loan-external-page.md`](loan-external-page.md) | **Implementado** como `ExternalLoanPage` | [`routes/loans.tsx`](../../../../frontend/src/routes/loans.tsx) |
| `SuppliersPage` | [`suppliers-page.md`](suppliers-page.md) | **Implementada** — ficha escrita antes que la pantalla, Fase 2 Hito 2 | [`routes/suppliers.tsx`](../../../../frontend/src/routes/suppliers.tsx) |
| `TagField` y `TagChip` | [`tag-field.md`](tag-field.md) | **Implementados** — ficha escrita antes que el componente, cierre de huecos Hito 4 | [`ui/catalog.tsx`](../../../../frontend/src/ui/catalog.tsx) |
| `CategoryMarker` e `IconColorPicker` | [`category-identity.md`](category-identity.md) | **Implementados** — ficha escrita antes que los componentes, cierre de huecos Hito 4 | [`ui/catalog.tsx`](../../../../frontend/src/ui/catalog.tsx) |

**Dieciocho componentes reutilizables —diez en `primitives.tsx`, cuatro en
`files.tsx` y cuatro en `catalog.tsx`— más dos pantallas, en dieciocho filas
—`CategoryMarker` y su selector comparten una, y `TagField` otra con su pastilla,
porque en los dos casos son mitades de una sola anatomía— y catorce de ellas
tienen ficha**:
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

`DangerZone` es la **quinta ficha escrita antes que su componente**, y la primera
de una pieza que no es una pantalla entera. Llegó con la baja de hogar y el cierre
de cuenta ([ADR-012](../../../common/architecture/decisions/ADR-012-data-erasure-household-closure-and-account-closure.md)),
que son las dos primeras operaciones del producto que borran datos para siempre:
todo lo demás que el sistema llama «baja» es lógico, y por eso le bastaba un
`Button` de variante `danger`.

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

**Y el tercer fichero llegó con el cierre de huecos**: `catalog.tsx`, con
`TagField`, `CategoryMarker` e `IconColorPicker`. Se parte **por dominio** y no
por el eje del Hito 3 —primitiva pura frente a pieza con peticiones—, que habría
mandado el marcador y el selector a `primitives.tsx` y solo el campo de etiquetas
fuera. El motivo es el juego de iconos: son **dieciséis dibujos**, una tabla de
datos cerrada más que un componente, y dentro de `primitives.tsx` acaban con el
«de una sentada» de arriba. El razonamiento entero está en
[`category-identity.md`](category-identity.md).

## Lo que falta por construir

Todos estos están **previstos**: ninguno tiene implementación hoy. **Con la Fase 1
cerrada, ya no los pide ningún hito en curso**: quedan como lista de lo que el
sistema de diseño no cubre, y cada uno se construirá cuando una pantalla lo
necesite de verdad.

Los cinco del Hito 2, de los que **uno ya está construido**. Los patrones que los
usan sí están escritos y dicen en cada punto qué pieza falta: ver
[`patterns/`](../patterns/README.md).

**`Combobox` se construyó en el Hito 3 de la Fase 2** (2026-08-19), que es cuando
dejó de ser una comodidad: buscar un artículo entre los cientos de una despensa no
lo resuelve un `SelectField`, y Warehouse lo pide en su pantalla. Vive en
[`primitives.tsx`](../../../../frontend/src/ui/primitives.tsx) con el patrón
combobox de ARIA 1.2 —foco que no sale de la caja, `aria-activedescendant`,
`listbox` referenciado por `aria-controls`, `Escape` que cierra sin elegir— y su
prueba de teclado. Los cuatro que siguen no.

| Componente previsto | Por qué se pide |
|---|---|
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

- ~~**`lucide-react` sigue sin estar en
  [`frontend/package.json`](../../../../frontend/package.json).**~~ **Resuelto en
  el cierre de huecos, Hito 4 (2026-08-20): la dependencia entra**, y con el
  número delante. Con los dieciséis iconos de categoría importados uno a uno, la
  primera carga pasa de 413,73 kB a 419,61 —**5,88 kB, 2,63 kB comprimidos**, un
  1,4 %—, y dibujarlos a mano cuesta aproximadamente lo mismo en bytes. Así que
  el peso, que era el motivo por el que la dependencia seguía fuera, no decidía
  nada: lo que decidía era que un juego **adoptado y no instalado** obliga a
  imitar a mano lo que ya se había elegido. Los cuatro iconos dibujados a mano se
  migraron en el mismo hito, en un commit propio, para no quedarse con dos
  vocabularios.
- **Los cinco iconos de estado del dominio siguen sin estar**, y ahora la razón
  es otra y hay que decidirla. [`iconography.md`](../foundations/iconography.md)
  declara **normativa** la tabla de un icono por estado, «lo que sostiene el
  criterio 1.4.1»; [`status-badge.md`](status-badge.md) declara lo contrario y con
  su motivo —quince iconos idénticos en columna son ruido, no información— y el
  Hito 3 lo volvió a confirmar. Ya no es un problema de dependencia: **son dos
  documentos del sistema de diseño que se contradicen**, y quien lo resuelva tiene
  que tocar uno de los dos. Queda anotado y no se resuelve aquí, que es de otro
  alcance.
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
| 2026-08-20 | **«Catálogo» entra en la pasada sistemática de axe**, con una categoría de color puesto sembrada antes: es donde vive el selector, o sea las veintidós parejas de botones del hito, y en gris no habría mirado ninguno de los seis colores. Quedan **dos pantallas del core sin auditar**: Sitios y Personas. | Equipo DRP |
| 2026-08-20 | Entran **tres componentes y dos fichas**, las dos escritas antes que el código: [`tag-field.md`](tag-field.md) y [`category-identity.md`](category-identity.md) (cierre de huecos, Hito 4). Nace el **tercer fichero de componentes**, `catalog.tsx`, partido por dominio y no por el eje del Hito 3, y con su motivo. Se cierra la decisión abierta de **`lucide-react`** con la medida delante, y se abre en su sitio la que quedaba escondida detrás: `iconography.md` y `status-badge.md` se contradicen sobre el icono de estado. | Equipo DRP |
| 2026-08-20 | **Los dos defectos que el repaso destapó, arreglados.** La celda de la galería lleva su nombre accesible en el botón y la miniatura pasa a decorativa —tal y como `file-gallery.md` lo tenía especificado desde el primer día—, y **«Archivo» entra en la pasada sistemática de axe** con un fichero sembrado, porque auditarla vacía no habría mirado ninguna celda. Quedan **cuatro pantallas del core sin auditar**: Inventario, Sitios, Catálogo y Personas. | Equipo DRP |
| 2026-08-20 | **Repaso de las doce fichas contra el código, una a una.** `avatar.md` y `file-gallery.md` decían «Previsto. No existe» de componentes construidos en el Hito 3 —siete días antes—, y `card.md`, `field.md` y `status-badge.md` arrastraban en «Lo que falta» cosas que la Fase 2 había resuelto: `PageHeading` salió de `household.tsx`, llegó `SelectField` y los tonos de dominio del distintivo existen. `button.md`, `notice.md` y `spinner.md` **no cambian ni una afirmación** —lo suyo era el marco, que hablaba en futuro de un hito cerrado hace dos fases—, y `danger-zone.md`, `loan-external-page.md` y `suppliers-page.md` estaban al día. Lo que **no** se hizo: borrar la especificación de lo que no existe. Donde el componente se queda corto, la entrada se queda y gana precisión — así es como `Textarea`, el campo numérico y el `<select>` sin migrar de `household.tsx` siguen escritos. El repaso destapó además dos cosas que no son de documentación y quedan anotadas en la ficha de la galería: **la celda de un PDF no tiene nombre accesible** y **la pantalla «Archivo» no entra en ninguna pasada de axe**, que es por lo que no se había visto. Se anota el desajuste del campo `Estado`, que seis fichas usan como estado documental y seis como estado de implementación, y las seis primeras pasan de `Borrador` a `Vigente`: describen componentes construidos y auditados. | Equipo DRP |
| 2026-08-12 | Creación del directorio con las seis fichas de los componentes que existen, y el registro de los que el Hito 2 va a pedir. | Equipo DRP |
| 2026-08-14 | Las tres anatomías del Hito 3 pasan de previstas a **implementadas**, en un fichero propio —`files.tsx`— y no en `primitives.tsx`: traen estado y una petición en curso, que es otra clase de complejidad. Se añade `QuotaMeter`, que la ficha de `UploadField` documentaba como variante y acabó siendo pieza aparte. | Equipo DRP |
| 2026-08-13 | Se corrige el registro, que había quedado desfasado al cerrar el Hito 2: son **nueve** componentes en 376 líneas, no seis en 266, y `SelectField`, `PageHeading` y `EmptyState` están implementados sin ficha. Se dan de alta las tres anatomías del Hito 3 —`UploadField`, `FileGallery` y `Avatar`—, previstas y con ficha, y se anota la excepción de foco que trae la primera. | Equipo DRP |
| 2026-08-18 | Fase 2, Hito 2: entra `SuppliersPage`, la segunda pantalla con ficha propia y la **quinta escrita antes que su implementación**. Volvió a pagar: la especificación le daba al filtro el mismo rótulo que al campo del formulario de alta, y dos controles con el mismo nombre accesible en una pantalla son indistinguibles para quien no la ve. | Equipo DRP |
| 2026-08-17 | Cierre documental de la Fase 1. `LoanExternalPage` pasa a **implementada** —vive en su ruta y no en el sistema de diseño, porque es una pantalla— y `QuotaMeter` entra en el registro, del que faltaba. Se corrige «lo que falta por construir», que seguía dando por no construidas las tres piezas del Hito 3 mientras el registro las daba por hechas: la lista queda en los cinco del Hito 2 más `BlockingError`, y deja de estar atada a un hito en curso porque ya no hay ninguno. | Equipo DRP |
