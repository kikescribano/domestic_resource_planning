# Formulario

| Campo | Valor |
|---|---|
| Estado | Borrador |
| Responsable | Equipo DRP |
| Ámbito | frontend |
| Última revisión | 2026-08-12 |

## Propósito

Fijar cómo se compone un formulario en DRP: en qué orden van las piezas, dónde
aterriza cada tipo de error, qué pasa mientras se envía y qué se hace cuando sale
bien. Es el patrón más rodado del sistema —hay ocho formularios en producción— y
también el que el Hito 2 va a estirar en una dirección que todavía no ha visto.

## Alcance

### Incluido

- El formulario de una columna: alta, edición y todo lo que hoy existe.
- El reparto de errores entre el campo y el aviso, que es la decisión central.
- La operación corta sobre una fila que ya existe, que es lo que trae el Hito 2.

### Fuera de alcance

- La anatomía de un campo, en [`components/field.md`](../components/field.md).
- Los tonos y papeles del aviso, en
  [`components/notice.md`](../components/notice.md).
- Qué se hace con cada código de error del contrato, en
  [`feedback.md`](feedback.md).

## Estado

| Parte | Estado |
|---|---|
| Formulario de una columna, con envío y errores | **Implementado**, en ocho formularios |
| Operación corta sobre una fila existente | **Previsto**: no hay ninguna, y le falta el `Dialog` |
| Confirmación destructiva | **Previsto**: no existe ninguna en la aplicación |
| Campos que no son `<input>` | **Previsto**: ni `Select`, ni `Textarea`, ni numérico, ni `Combobox` |

## Contenido

### El formulario de una columna

Los ocho formularios de hoy tienen exactamente la misma forma, y no por
casualidad: se copiaron unos de otros. Merece la pena escribirla porque es la que
hay que repetir.

```tsx
<form onSubmit={submit} className="flex flex-col gap-4" noValidate>
  {/* 1. El fallo de la operación completa, si lo hubo */}
  {/* 2. Los campos, en columna */}
  {/* 3. Una acción principal, la última */}
</form>
```

| Regla | Cómo se cumple hoy |
|---|---|
| **Una columna, siempre** | `flex flex-col`, en los ocho. Aunque quepan dos, no se ponen dos: lo fija [`look-and-feel.md`](../../product-design/look-and-feel.md) |
| **16 px entre campos** | `gap-4`, en los ocho |
| **Sin validación del navegador** | `noValidate`, en los ocho. Si no, el mensaje del navegador se adelanta al del servidor y dice otra cosa, en otro idioma y en otro sitio |
| **Una acción principal** | Un `Button variant="primary"` por formulario, el último elemento |
| **La acción dice lo que hace** | «Crear el hogar», «Enviar la invitación», «Cambiar la contraseña». Nunca «Aceptar» ni «Guardar» a secas |
| **Estado ocupado con texto** | `busy` + `busyLabel` en los ocho: «Creando…», «Entrando…», «Guardando…» |

**La anchura no está resuelta igual en los tres sitios**, y es el defecto visible
del patrón: en el enrolamiento la pone
[`AuthCard`](../components/card.md) con su `max-w-form`; en `AccountPage` la
declara el propio `<form>` con `max-w-form`; y en el formulario de invitar de
`UsersPage` **no la pone nadie**, así que sus campos se estiran hasta el
`max-w-shell` de la columna de contenido. A 1440 px eso es un campo de correo de
más de mil píxeles de ancho. La anchura de formulario debería venir del patrón y
no de quien se acuerde.

### Dónde aterriza cada error

Es la decisión que da valor al patrón, y viene del backend:
[`ApiExceptionHandler`](../../../../backend/src/main/kotlin/com/drp/core/adapter/http/ApiExceptionHandler.kt)
responde **`400` con `VALIDATION_ERROR` y un detalle por campo** para los errores
de forma, y **`409` con un código concreto** para los de regla de negocio. Son dos
familias que no se mezclan, así que tampoco se pintan en el mismo sitio:

| Lo que devuelve la API | Dónde se pinta | Con qué |
|---|---|---|
| `400` con `details` | Bajo el campo que nombra cada clave | `Field` con `error=` |
| `409` con código | Sobre el formulario | `Notice tone="danger"` |
| `401`, `403`, `404`, `5xx` | Sobre el formulario | `Notice tone="danger"` |
| Fallo de red | Sobre el formulario | `Notice tone="danger"` |

La condición que separa las dos familias está escrita así en cuatro de las
pantallas:

```tsx
{error && !fieldError?.details && (
  <Notice tone="danger" title="No se ha podido crear">{messageFor(error)}</Notice>
)}
```

Léase: **si el error trae `details`, no es de aquí** — ya lo está pintando el
campo. Sin esa guarda, un fallo de validación saldría dos veces, arriba y abajo.

Y en todos los casos, **lo que el usuario había escrito se conserva**. Ningún
formulario se vacía al fallar: el estado vive en la pantalla y el error no lo
toca.

### Lo que pasa al enviar y al salir bien

Mientras se envía: el botón pasa a ocupado y se desactiva; **nada más de la
pantalla se bloquea**. No hay velo, ni campos deshabilitados, ni cursor de
espera.

Al salir bien, dos tratamientos y una regla para elegir:

- **Si el resultado se ve, no se anuncia.** Es el caso del enrolamiento: la
  pantalla se sustituye entera por la siguiente —«Mira tu correo»— y el titular
  ya dice que salió bien.
- **Si el resultado no se ve, un `Notice tone="success"`.** Es el caso de
  «Invitación enviada.» y «Contraseña cambiada.»: el efecto ocurre en un correo o
  en otras sesiones, y sin aviso no habría forma de saberlo.

### La forma que trae el Hito 2: la operación sobre una fila

Entrada, movimiento, fusión, ajuste y baja **no son altas**. Son formularios de
uno o dos campos que se abren desde una fila que ya existe, que llevan implícito
el objeto sobre el que actúan y que devuelven a donde estaban. Nada de esto
existe todavía, y lo que hace falta para escribirlo es esto:

| Operación | Campos | Lo que le falta al sistema |
|---|---|---|
| Entrada de consumible | Artículo, ubicación, propietario, cantidad | `Combobox` para el artículo, `Select` para ubicación y propietario, campo numérico con unidad |
| Movimiento | Ubicación destino | Selector de ubicación **sobre un árbol**, no un desplegable plano (ver [`hierarchy.md`](hierarchy.md)) |
| Fusión | Existencia destino | Un selector que solo ofrezca existencias del mismo artículo, y que deje ver qué ubicación y qué propietario sobreviven |
| Ajuste de cantidad | Cantidad | Campo numérico. Y dejar claro que **sustituye, no suma** |
| Baja | Ninguno: es una confirmación | Diálogo de confirmación, que no existe |

Tres reglas que ya están decididas y que estos formularios tienen que cumplir:

1. **La unidad la fija el artículo, no la existencia.** El campo de cantidad
   muestra la unidad y **no deja cambiarla**. Es una regla del core, no una
   preferencia de interfaz.
2. **El `quantity` del ajuste es absoluto y el de la entrada suma.** Son dos
   operaciones distintas del contrato —`PATCH /assets/{id}` y
   `POST /assets/intake`— y confundirlas descuadra el inventario en silencio. La
   etiqueta tiene que decir cuál es cuál.
3. **La confirmación destructiva nombra el objeto y la consecuencia.** «Dar de
   baja *Taladro Bosch*. Seguirá en el historial y dejará de poder prestarse»; el
   botón lleva el verbo, no «Aceptar»; y el foco arranca en cancelar. Está fijado
   en [`look-and-feel.md`](../../product-design/look-and-feel.md) y no hay que
   volver a decidirlo, solo implementarlo.

En móvil estas operaciones son **hoja inferior** y en escritorio **diálogo**, que
es la misma decisión de densidad que gobierna los listados. Ni una ni otra
existen.

### Antiusos

| Antiuso | Por qué |
|---|---|
| Dos columnas de campos porque caben | La dirección visual mantiene una columna hasta 640 px y no la abandona después |
| Repetir el error de campo en un aviso arriba | El error recuperable se resuelve donde ocurrió |
| Vaciar el formulario al fallar | Es la forma más rápida de perder diez minutos de trabajo ajeno |
| `busy` sin `busyLabel` | Un botón desactivado y mudo se percibe como aplicación colgada |
| Deshabilitar los campos mientras se envía | Impide corregir lo que se acaba de ver mal, y no evita nada |
| Un formulario sin acción principal visible sin desplazar | A 375 px la acción tiene que verse sin cerrar el teclado |
| Enganchar el error por el texto del mensaje | Se engancha por el nombre del campo del contrato, y el código decide el resto |

## Decisiones abiertas

- **De dónde sale la anchura del formulario.** Hoy de tres sitios distintos, y en
  uno de ellos de ninguno. Lo natural es que la ponga el patrón —un componente
  `Form` o una clase compartida— en lugar del contenedor.
- **Dónde vive la correspondencia entre el campo del contrato y el campo de la
  pantalla.** Hoy cada pantalla mira las claves que se le ocurren:
  `ResetPasswordPage` mira `password`, `CreateHouseholdPage` mira
  `admin.password` y `AccountPage` mira `newPassword` con `password` de reserva.
  Con las 23 operaciones del Hito 2 esto deja de ser sostenible a mano.
- **Qué hace un formulario con los cambios sin guardar** cuando el usuario
  navega fuera. No hay nada, y con la ficha de asset —que es una edición larga—
  se va a notar.

## Lo que falta

Todo lo de aquí está **previsto**; nada existe hoy:

- **El foco no va al primer campo con error.**
  [`look-and-feel.md`](../../product-design/look-and-feel.md) lo pide
  explícitamente y no está en ninguna parte: ni en `Field`, que no puede saber si
  es el primero, ni en los formularios. Es responsabilidad de este patrón.
- **El hueco del mensaje de error no está reservado**, así que en un campo sin
  ayuda el error empuja hacia abajo lo que venga después. Detallado en
  [`field.md`](../components/field.md).
- **No hay marca de campo obligatorio.** Con formularios donde todo lo es no
  importa; el alta de asset mezcla obligatorios y opcionales en la misma columna.
- **Faltan cuatro tipos de campo**: `Select`, `Textarea`, numérico y `Combobox`.
  El `<select>` de invitar ya está escrito a mano dentro de
  [`household.tsx`](../../../../frontend/src/routes/household.tsx), repitiendo la
  maquetación de `Field` sin ser `Field`.
- **Faltan el `Dialog` y la hoja inferior**, sin los cuales no hay operación
  corta ni confirmación destructiva.
- **`AccountPage` no muestra nada si el cambio de contraseña falla por algo que
  no sea `CURRENT_PASSWORD_INVALID`.** Solo tiene esa rama y la de éxito, así que
  un `429`, un fallo de red o un `500` dejan el formulario en silencio: el botón
  deja de estar ocupado y no aparece ningún aviso. Es un defecto real del código
  de hoy, no una carencia del patrón.
- **`ResetPasswordPage` usa una guarda distinta a la de las demás.** Comprueba
  `!fieldError` sobre la clave `password` en lugar de `!fieldError?.details`, así
  que un `VALIDATION_ERROR` que nombre otra clave no se pinta bajo ningún campo
  **y** saca en el aviso el mensaje crudo del contrato, que es texto de
  diagnóstico y no de usuario.

## Referencias

- [`components/field.md`](../components/field.md) y
  [`components/button.md`](../components/button.md)
- [`components/notice.md`](../components/notice.md) y
  [`feedback.md`](feedback.md)
- [`look-and-feel.md`](../../product-design/look-and-feel.md): estados de
  experiencia y operaciones destructivas.
- [`openapi.yaml`](../../../../openapi.yaml): las dos familias de error y los
  campos de cada operación.

## Historial de cambios

| Fecha | Cambio | Autor |
|---|---|---|
| 2026-08-12 | Creación del documento con el patrón implementado en el Hito 1 y lo que el Hito 2 le añade. | Equipo DRP |
