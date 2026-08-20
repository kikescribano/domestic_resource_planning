# SuppliersPage

| Campo | Valor |
|---|---|
| Estado | **Implementada** — la ficha se escribió antes que la pantalla |
| Responsable | Equipo DRP |
| Ámbito | frontend |
| Última revisión | 2026-08-18 |

> **Esta ficha se escribió antes que la pantalla**, como especificación de lo que
> el Hito 2 de la Fase 2 tiene que construir. Es el mismo orden que usó
> `LoanExternalPage` en la Fase 1, y se repite por lo que aquello demostró:
> escribirla por delante **encontró un hueco del contrato cuando no había código
> que arreglar**. La bandeja de avisos del Hito 1 se entregó sin ficha, y es la
> única pantalla de la Fase 2 de la que no se puede decir lo mismo.
>
> **La pantalla ya existe**, y lo que se construyó distinto se dice en [Estado de
> implementación](#estado-de-implementación-y-enlace-al-componente-real) con su
> motivo, **sin reescribir la especificación para que parezca que acertó**.

## Propósito y situaciones de uso

La pantalla del módulo Proveedores: **la agenda de a quién se llama** cuando algo
de la casa hay que arreglar, revisar o reclamar.

Se llega por dos caminos y los dos importan:

| | Qué ocurre |
|---|---|
| **Desde la navegación** | «Proveedores» aparece en el grupo «Datos maestros» —columna en escritorio, «Más» en móvil— **solo si el módulo está activo**. Es el camino normal |
| **Escribiendo `/proveedores` a mano** | Con el módulo apagado no lleva a un error sino a la pantalla que **lo ofrece**. Es el motivo por el que el backend responde `403` y no `404`, y el único sitio del cliente donde esa diferencia sirve de algo |

El segundo camino es el que fija la anatomía entera de esta ficha, y tiene una
consecuencia que se olvida: **la pantalla no es la ruta**. La ruta es un
guardián, `ModuleScreen`, que decide entre tres cosas —módulo desconocido,
módulo apagado, módulo encendido— y solo en el tercer caso pinta esto.

### Lo que esta pantalla NO hace

- **No enseña el histórico de lo que vino a arreglar nadie.** Eso es CMMS.
- **No enseña cuánto costó.** Eso es Gastos y presupuesto.
- **No busca por teléfono ni por correo.** El filtro de texto mira el nombre, y
  es una decisión de seguridad: son datos personales de alguien que no es
  usuario del sistema, y un buscador por correo convierte la lista en un
  directorio consultable. El contrato lo declara así.

## Anatomía, variantes y estados

Dos vistas bajo la misma ruta, como en ubicaciones: **el listado** y **la ficha
de un contacto**, esta última desplegada dentro del listado y no en una ruta
propia. Un contacto de servicio tiene ocho campos y como mucho un puñado de
enlaces; una ruta propia por contacto costaría una navegación completa para
enseñar lo que cabe en una tarjeta.

```text
┌─ PageHeading «Proveedores» ────────────────────────────────┐
│  Filtro de categoría (SelectField)   Buscar (Field)        │
│  [ Añadir contacto ]  ← única acción primaria              │
├────────────────────────────────────────────────────────────┤
│  ▸ Fontanería Pérez        Fontanería        600 100 200   │
│  ▾ Servicio Técnico Caldera  Climatización   900 100 100   │
│      Luis · sat@caldera.example · Calle Mayor 1            │
│      Enlazado con: Sala de calderas (ubicación) [quitar]   │
│      [ Enlazar con… ]  [ Editar ]  [ Retirar ]             │
│  ▸ Taller Ramírez  · RETIRADO ·   Vehículos   600 300 400  │
└────────────────────────────────────────────────────────────┘
```

**Estados de la vista:**

| Estado | Qué se ve |
|---|---|
| Cargando | `Spinner` con etiqueta. **No se audita con axe en este estado** — axe recorrería cuatro elementos y pasaría sin comprobar nada |
| Vacío | `EmptyState` con la acción de añadir dentro. Es el estado **normal** al encender el módulo, porque su siembra está vacía a propósito: aquí no hay nada que heredar del core |
| Con datos | El listado, vigentes primero |
| Vacío por filtro | Distinto del vacío de verdad, y hay que distinguirlo: «no hay ninguno» y «no hay ninguno **que cumpla esto**» piden acciones opuestas |
| Error | `Notice tone="danger"`, en el sitio donde ocurrió |

**Estados de una fila:** vigente y **retirado**. El retirado lleva
`StatusBadge tone="neutral"` con la etiqueta «Retirado», no solo un gris: nada se
dice solo con color (regla 3 de la dirección visual). Solo aparece si se piden.

## API pública o propiedades relevantes

No es una primitiva y no expone API: es una pantalla de ruta, igual que
`LoanExternalPage`. Lo que sí es API es **cómo la envuelve el guardián**:

```tsx
<Route path="/proveedores" element={<ModuleScreen moduleKey="SUPPLIERS"><SuppliersPage /></ModuleScreen>} />
```

`ModuleScreen` pasa de ser **un guardián que no sabe enseñar nada** —hoy pinta
«Encendido, y todavía sin nada que enseñar…»— a **un guardián que envuelve**. Las
dos mitades siguen ahí y la de apagado no se puede perder por el camino: es la
tercera capa del gate de la
[ADR-010](../../../common/architecture/decisions/ADR-010-module-boundaries-and-activation.md),
y sin ella entrar a mano en la ruta de un
módulo apagado enseñaría una lista vacía en lugar de ofrecer encenderlo.

Con esto, el `milestone` de `MODULE_SCREENS` deja de tener sentido para
`SUPPLIERS` y se retira de esa entrada: era la promesa autoprogramada de que
«sus pantallas llegan en el Hito 2», y este es ese hito.

## Comportamiento responsive y con contenido extremo

- **De 320 px a ultrawide**, como todo. El listado es una columna en móvil y
  gana la categoría y el teléfono en línea desde `sm`.
- **La navegación no gana ninguna parada en el pulgar.** «Proveedores» entra en
  el grupo «Datos maestros», que en móvil vive dentro de «Más»: la barra
  inferior sigue en cuatro paradas y «Más», que es el tope medido a 320 px.
  Esto **hay que volver a medirlo**, no darlo por bueno: el recorrido vertical
  mide el ancho de cada parada visible y ese es el sitio donde el defecto se
  caza.
- **Contenido extremo:** un nombre largo se envuelve y no recorta la fila; una
  web larga se corta con elipsis porque no se lee, se pulsa. La ficha desplegada
  no fuerza `overflow-x` en el documento: si algo desborda, desborda **dentro de
  su contenedor**.

## Teclado, foco, semántica y anuncios asistivos

- **El listado es una `<ul>`**, no una tabla: una tabla obligaría a decidir
  cabeceras para tres columnas que en móvil se apilan.
- **Desplegar la ficha es un `<button>`** con `aria-expanded`, no un `<div>` con
  `onClick`. Es lo que hace que se llegue con el tabulador y se abra con `Enter` y
  con `Espacio`.
- **El anillo de foco no se declara aquí.** Está en la capa base de `index.css`
  para que ninguna pantalla pueda olvidarlo — ni quitarlo (regla 2).
- **44 px de objetivo táctil** en todo lo pulsable, incluidos los «quitar» de
  cada enlace, que son los que más fácil se quedan cortos.
- **Una sola acción principal**: «Añadir contacto». «Editar», «Retirar» y
  «Enlazar» son secundarias.
- **El resultado de una acción se anuncia una vez.** Retirar un contacto lo
  saca de la lista y deja un `Notice`; sin anuncio, quien navega con lector de
  pantalla no sabe si pasó algo.

## Ejemplos correctos, antiusos y evidencias de prueba

**Antiusos, y los tres primeros son errores que ya se han cometido en este
repositorio:**

1. **Auditar con axe la pantalla mientras carga.** Con el `Spinner` puesto, axe
   recorre cuatro elementos y pasa. La auditoría va **después** de que la lista
   esté pintada.
2. **Medir color aplicado durante la transición de tema.** Cambiar de modo abre
   140 ms en los que cada color es una mezcla de los dos, y ahí el contraste no
   corresponde a ningún color del sistema.
3. **Duplicar la navegación por breakpoint.** Un solo `<nav>` recolocado con CSS;
   esta pantalla no añade ninguno.
4. **Ofrecer «enlazar» en un contacto retirado.** El backend responde `409
   SUPPLIER_RETIRED`, así que el botón no se pinta: un control que solo sirve
   para recibir un error es peor que no tenerlo.

**Evidencias de prueba:**

| Nivel | Qué comprueba |
|---|---|
| Componente (`suppliers.test.tsx`) | El listado, el alta, el error de duplicado y el de contacto obligatorio, y que el guardián sigue ofreciendo la activación con el módulo apagado |
| Recorrido vertical | Encender el módulo, dar de alta un contacto, enlazarlo con una ubicación y verlo en su ficha, **añadido a la batería existente y no en una suite paralela**, con axe en los dos modos, foco, teclado y reflujo de 320 px a ultrawide |

## Estado de implementación y enlace al componente real

**Implementada.** Vive en
[`routes/suppliers.tsx`](../../../../frontend/src/routes/suppliers.tsx) como
`SuppliersPage`, y el guardián en
[`routes/modules.tsx`](../../../../frontend/src/routes/modules.tsx). Sus pruebas
de componente están en
[`suppliers.test.tsx`](../../../../frontend/src/routes/suppliers.test.tsx) y su
tramo del recorrido vertical, dentro de la batería existente en
[`vertical-journey.spec.ts`](../../../../frontend/e2e/vertical-journey.spec.ts).

**Lo que se construyó distinto de lo especificado, y por qué:**

- **El filtro se llama «Filtrar por categoría» y no «Categoría de servicio».** El
  boceto de arriba le daba el mismo rótulo que al campo del formulario de alta, y
  dos controles con **el mismo nombre accesible** en la misma pantalla son
  indistinguibles para quien navega con lector de pantalla. No se vio mirando la
  pantalla: lo delató una prueba que seleccionaba en el control equivocado, que es
  exactamente el síntoma que sufre quien no la ve.
- **No hay botón «Editar» en la ficha desplegada.** El contrato tiene su `PATCH`
  y el cliente su llamada, pero la pantalla se entrega sin formulario de edición:
  lo que un hogar hace con un contacto de servicio es consultarlo, y corregir un
  teléfono es un caso menos frecuente que enlazarlo o retirarlo. Queda como lo
  primero que añadir aquí, y no como una promesa autoprogramada: nada en la
  pantalla lo anuncia.
- **Enlazar solo ofrece ubicaciones, no assets.** La API admite las dos cosas y el
  módulo las guarda igual; lo que faltaba en el cliente era el `Combobox` con el
  que buscar entre los assets de una casa, que pueden ser cientos. Con ubicaciones
  —decenas— el `SelectField` aguanta. **El `Combobox` existe desde el Hito 3 de la
  Fase 2**, así que esto ya no está bloqueado por una pieza que falta: es trabajo
  pendiente de esta pantalla, y lo primero que añadir aquí junto con la edición.

**Lo que esta pantalla necesita y el sistema de diseño no tiene**, sin fingir que
existe:

- ~~`Combobox`~~ — **construido en el Hito 3 de la Fase 2** (2026-08-19), cuando
  Warehouse lo pidió de verdad. Esta pantalla sigue resolviendo el enlace con
  `SelectField` y solo con ubicaciones, que en una casa son decenas; lo que
  cambia es que **ya no es una pieza que falta sino trabajo pendiente aquí**, y la
  diferencia importa porque una deuda de sistema de diseño y una de pantalla no
  las paga el mismo hito.
- `Dialog` / hoja inferior — la confirmación de retirada. **Se resuelve sin
  confirmación**: retirar es reversible dando de alta otra vez, y una
  confirmación que no protege de nada solo añade un clic.
- `Pagination` — el contrato pagina, y el cliente no tiene control que lo pinte.
  **Se pide una página grande**, como hacen las demás pantallas.

## Historial de cambios

| Fecha | Cambio | Autor |
|---|---|---|
| 2026-08-18 | Creación, **antes que la pantalla**, como especificación del Hito 2 de la Fase 2. Fija que la ruta es un guardián que envuelve, que la ficha de un contacto se despliega dentro del listado en lugar de tener ruta propia, y las tres piezas del sistema de diseño que faltan con su solución provisional. | Equipo DRP |
| 2026-08-18 | La pantalla se construye y la ficha pasa a **implementada**. Tres cosas salieron distintas y quedan dichas con su motivo, sin retocar la especificación: el rótulo del filtro —que chocaba en nombre accesible con el del formulario—, la edición de un contacto, que no se entrega, y el enlace, que de momento solo ofrece ubicaciones porque los assets piden el `Combobox` que sigue sin existir. | Equipo DRP |
