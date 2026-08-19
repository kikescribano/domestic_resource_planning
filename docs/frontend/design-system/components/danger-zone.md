# DangerZone

| Campo | Valor |
|---|---|
| Estado | **Implementado** — ficha escrita antes que el componente |
| Responsable | Equipo DRP |
| Ámbito | frontend |
| Última revisión | 2026-08-19 |

> **Esta ficha se escribió antes de que existiera el componente**, como las de
> [`loan-external-page.md`](loan-external-page.md) y
> [`suppliers-page.md`](suppliers-page.md), y por el mismo motivo: es la quinta
> vez que se hace y las cuatro anteriores encontraron un hueco cuando todavía no
> había código que rehacer.
>
> **Y volvió a pagar, en el sitio de siempre**: la anatomía pedía un
> `text-danger-ink` y un `border-danger` interior que **no existen** —el sistema
> tiene `--color-danger` y `--color-danger-soft`, y nada más—, así que se habría
> escrito una clase que Tailwind no resuelve y que no falla: simplemente no pinta.
> Los valores de esta ficha son ya los del componente construido.

## Propósito y situaciones de uso

El sitio donde vive **una acción que no se puede deshacer**, separada del resto
de la pantalla y con una confirmación que hay que **escribir**, no pulsar.

Aparece con la baja de hogar y el cierre de cuenta (ADR-012), que son las dos
primeras operaciones del producto que borran datos de verdad y para siempre. Todo
lo demás que el sistema llama «baja» es lógica: un asset dado de baja sigue en su
fila, una categoría retirada sigue clasificando lo que ya clasificaba, y una
persona que deja el hogar conserva su historial. Aquí no: vencida la gracia, del
hogar no queda ni una fila ni un fichero.

Esa diferencia es la que justifica una pieza propia en lugar de un
[`Button`](button.md) de variante `danger`, que es lo que el sistema ya tiene:

| Qué se está pidiendo | Con qué se pide |
|---|---|
| Una acción destructiva **reversible** — retirar una invitación, quitar una foto, dar de baja un asset | [`Button`](button.md) con `variant="danger"` |
| Una acción **irreversible** sobre todo lo que hay detrás | `DangerZone` |

**Y la confirmación escrita no es teatro.** Un diálogo de «¿seguro?» se contesta
que sí por reflejo —es el gesto que se hace cincuenta veces al día para cerrar
avisos— y su coste es exactamente un clic más, que es lo que cuesta también
equivocarse. Escribir el nombre del hogar obliga a leer qué se está borrando y a
teclear justo eso, y ninguna de las dos cosas se hace sin querer.

## Anatomía, variantes y estados

Una `<section>` al final de la pantalla, después de todo lo demás, con una regla
que la separa. Cuatro partes en orden fijo:

1. **Encabezado** (`h2`), con el nombre de lo que se va a destruir.
2. **Consecuencia**, en prosa y enumerando lo que se pierde. No «esta acción es
   irreversible» sino qué desaparece: las cosas del inventario, las fotos, los
   préstamos, las personas.
3. **El campo de confirmación** ([`Field`](field.md)), con la etiqueta diciendo
   qué hay que escribir.
4. **El botón**, `variant="danger"`, **deshabilitado mientras lo escrito no
   coincida**.

| Rasgo | Valor | Token |
|---|---|---|
| Separación del contenido de arriba | Borde superior y 24 px | `border-t border-border-subtle pt-6 mt-10` |
| Ancho | El del formulario, no el de la página | `max-w-form` |
| Color del encabezado | Peligro, pleno | `text-danger` |
| Espacio entre partes | 16 px | `gap-4` |

**No lleva fondo de color.** La superficie roja de bloque es lo que
[`color.md`](../foundations/color.md) reserva para un error que ya ha ocurrido; lo
de aquí todavía no ha ocurrido y depende de quien mira. Lo que la señala es el
borde, el encabezado y su posición al final.

Dos variantes, por lo que se escribe:

| Variante | Qué se teclea | Dónde |
|---|---|---|
| Nombre | El nombre del hogar, exacto | Baja de hogar |
| Palabra fija | `CERRAR` | Cierre de cuenta, donde no hay ningún nombre propio que copiar |

Estados: **inerte** (lo escrito no coincide, botón deshabilitado), **armado**
(coincide), **en curso** (`busy`, con su `busyLabel`) y **fallido**, que pinta un
[`Notice`](notice.md) de tono `danger` dentro de la sección y **no borra lo
escrito**: obligar a teclearlo otra vez tras un fallo de red castiga a quien no
tuvo la culpa.

## API pública

```tsx
<DangerZone
  title="Dar de baja el hogar"
  confirmation="Casa de Kike"        // lo que hay que escribir, exacto
  confirmationLabel="Escribe el nombre del hogar para confirmarlo"
  action="Dar de baja el hogar"      // el rótulo del botón
  busyLabel="Dando de baja…"
  busy={request.isPending}
  error={problem}                    // string | null
  onConfirm={() => request.mutate()}
>
  <p>Se borrará todo…</p>            {/* la consecuencia, en prosa */}
</DangerZone>
```

| Propiedad | Tipo | Nota |
|---|---|---|
| `title` | `string` | Va en un `h2` |
| `confirmation` | `string` | Comparación **exacta**, sin recortar ni ignorar mayúsculas: es el punto entero del componente |
| `confirmationLabel` | `string` | Etiqueta del campo. Dice qué escribir, no «confirma» |
| `action` | `string` | Rótulo del botón, en imperativo y nombrando la consecuencia |
| `busyLabel` | `string` | — |
| `busy` | `boolean` | — |
| `error` | `string \| null` | Se pinta dentro de la sección, no arriba de la página |
| `onConfirm` | `() => void` | Solo se invoca con lo escrito coincidiendo |
| `children` | `ReactNode` | La consecuencia |

`confirmation` se compara **tal cual**. Recortar espacios o ignorar mayúsculas
haría pasar un `casa de kike ` que nadie tecleó a conciencia, y ahí la tolerancia
juega en contra: lo que se quiere es precisamente que cueste.

## Comportamiento responsive y con contenido extremo

Una sola columna en todos los tamaños, y por eso no tiene punto de ruptura: es un
formulario de un campo, y `max-w-form` ya lo mantiene legible en ultrawide.

- **A 320 px** el botón ocupa el ancho disponible con su suelo de `min-h-touch`,
  como cualquier otro del sistema.
- **Nombre de hogar muy largo**, que es el contenido extremo real: la etiqueta lo
  cita, así que lleva `break-words` la prosa de la consecuencia. La etiqueta del
  campo la pinta [`Field`](field.md) y parte por espacios como cualquier texto; un
  nombre de hogar de una sola palabra larguísima desborda el campo, y es el caso
  que hoy no está resuelto.
- **Nombre con espacios al final**, que es el caso incómodo de la comparación
  exacta: la etiqueta muestra el nombre entre comillas para que se vea dónde
  acaba.

## Teclado, foco, semántica y anuncios asistivos

- **No es un diálogo.** Es una sección de la página, así que no hay foco que
  atrapar ni `Escape` que atender, y eso es una ventaja: los dos defectos de
  accesibilidad más caros de un modal no pueden ocurrir aquí. Se descartó el
  `<dialog>` por eso y porque un modal esconde la consecuencia detrás de un clic.
- **El botón deshabilitado se anuncia deshabilitado** —`disabled` de verdad, no
  `aria-disabled` sobre un botón vivo—, y la etiqueta del campo explica por qué,
  de modo que quien navega sin ver sabe qué falta.
- **El campo lleva `autoComplete="off"`**: no es una credencial y el navegador no
  debe ofrecer rellenarlo. Sugerirlo sería justo lo contrario de lo que el campo
  existe para conseguir.
- **El fallo se anuncia**: el [`Notice`](notice.md) de error vive en una región
  `role="alert"`, como en el resto del producto.
- El recorrido con el tabulador es campo → botón, y sale de la sección. Es la
  última parada de la pantalla, porque la sección va al final.

## Ejemplos correctos, antiusos y evidencias de prueba

**Correcto**: la sección al final de «Tu hogar», con el nombre del hogar como
confirmación y la lista de lo que se pierde encima.

**Antiusos**:

- **Un botón «Borrar» y un `window.confirm`.** Es el gesto reflejo que esta ficha
  existe para evitar, y además `window.confirm` no se puede estilar, no respeta el
  tema y bloquea el hilo.
- **Usarla para algo reversible.** Si la acción se deshace, la fricción no compra
  nada y enseña a la gente a teclear la confirmación sin leerla — con lo que deja
  de proteger el día que sí importa.
- **Poner la confirmación escrita y aceptar además el `Enter` del campo.** El
  envío al pulsar `Enter` en un campo de texto es la forma más rápida de disparar
  la acción sin haber mirado el botón.
- **Comparar sin distinguir mayúsculas** «para que sea más cómodo».

**Evidencias**: la prueba de recorrido vertical solicita la baja escribiendo el
nombre, comprueba el aviso persistente, la cancela y verifica que todo sigue; y la
auditoría de accesibilidad de la pantalla se hereda por la lista de pantallas del
recorrido. La prueba unitaria del frontend cubre el botón deshabilitado con lo
escrito a medias.

## Estado de implementación y enlace al componente real

**Implementado**, en
[`primitives.tsx`](../../../../frontend/src/ui/primitives.tsx), por el Hito 0 del
cierre de huecos. Se usa en dos sitios y con sus dos variantes: la baja del hogar
en «Tu hogar» —con el nombre del hogar— y el cierre de cuenta en «Tu cuenta» —con
`CERRAR`—, los dos en
[`routes/household.tsx`](../../../../frontend/src/routes/household.tsx).

Dos cosas que la implementación decidió y esta ficha no había fijado:

- **Es un `div` y no un `form`.** En un formulario, `Enter` dentro del campo lo
  envía, que es la forma más rápida de disparar la acción sin haber mirado el
  botón — el mismo defecto que la sección de antiusos ya nombraba.
- **La zona de peligro solo aparece cuando la acción está disponible.** Con la
  baja ya pedida, «Tu hogar» enseña en su lugar la fecha y un botón normal de
  cancelar: **deshacer algo destructivo no merece fricción**, y ponérsela sería
  castigar el arrepentimiento, que es justo lo que el periodo de gracia existe
  para permitir.

## Referencias

- [ADR-012](../../../common/architecture/decisions/ADR-012-data-erasure-household-closure-and-account-closure.md),
  que es la decisión de la que sale.
- [`button.md`](button.md), [`field.md`](field.md) y [`notice.md`](notice.md),
  que son las tres piezas que compone.
- [`color.md`](../foundations/color.md), para la frontera entre el rojo de error y
  el rojo de acción destructiva.
- [`feedback.md`](../patterns/feedback.md), para el aviso en el sitio.

## Historial de cambios

| Fecha | Cambio |
|---|---|
| 2026-08-19 | Se crea, **antes que el componente**, al construir la baja de hogar y el cierre de cuenta (Hito 0 del cierre de huecos) |
| 2026-08-19 | Pasa a **implementada** el mismo día: se corrigen los dos tokens de color que la ficha había inventado, y se anotan las dos decisiones que la construcción trajo —el `div` en lugar del `form`, y que cancelar no lleva fricción— |
