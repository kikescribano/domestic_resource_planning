# Feedback

| Campo | Valor |
|---|---|
| Estado | Borrador |
| Responsable | Equipo DRP |
| Ámbito | frontend |
| Última revisión | 2026-08-17 |

## Propósito

Decidir qué le dice la interfaz al usuario cuando algo pasa: cuándo un aviso,
cuándo un distintivo, cuándo una espera y cuándo nada. Y sobre todo, **separar
tres cosas que llegan por el mismo cable y no son lo mismo**: un error de forma,
un error de regla de negocio y una advertencia sobre una operación que ha salido
bien.

Esa tercera es la que entra con el Hito 2 y la que más fácil es pintar mal.

## Alcance

### Incluido

- Las tres respuestas de la API y dónde se pinta cada una.
- Qué hace el cliente con los códigos de negocio del Hito 2.
- Las tres esperas, el éxito y las operaciones destructivas.

### Fuera de alcance

- La anatomía del aviso, en [`components/notice.md`](../components/notice.md).
- El distintivo de estado, en
  [`components/status-badge.md`](../components/status-badge.md).
- Dónde se coloca el error dentro de un formulario, en [`form.md`](form.md).

## Estado

| Parte | Estado |
|---|---|
| Aviso en el sitio donde ocurrió, con sus cuatro tonos | **Implementado** (`Notice`) |
| Reparto entre error de campo y fallo de operación | **Implementado** en ocho formularios |
| Traducción de código a lenguaje de casa | **Implementado** y compartido: `humanMessage` en [`client.ts`](../../../../frontend/src/api/client.ts), que usan todas las rutas |
| Advertencia sobre una operación con éxito | **Implementado** en el Hito 2: el array `warnings` de la respuesta se pinta con un aviso por advertencia en [`assets.tsx`](../../../../frontend/src/routes/assets.tsx) |
| Aviso efímero con deshacer | **Previsto**: no hay `Toast` |
| Error bloqueante a pantalla completa | **Previsto** como pieza del sistema. Hay un caso resuelto a mano —`BrokenLink`, en la pantalla externa de un préstamo— y tres sin resolver |
| Sesión caducada | **Resuelto por otro camino**: la sesión se renueva con el refresh en vez de morir a los quince minutos, así que la pantalla de sesión caducada ya no es el remedio principal. Sigue haciendo falta para cuando el refresh también falla |

## Contenido

### Tres respuestas, no dos

El backend responde de tres formas distintas y el cliente tiene que distinguirlas
**por su forma, no por su texto**. Las dos primeras las fija
[`ApiExceptionHandler`](../../../../backend/src/main/kotlin/com/drp/core/adapter/http/ApiExceptionHandler.kt)
y la tercera es una decisión de este hito.

| Qué ha pasado | Respuesta | Cómo se reconoce | Dónde se pinta |
|---|---|---|---|
| **La petición está mal construida** | `400` con `VALIDATION_ERROR` | Trae `details`: un mensaje por atributo rechazado | Bajo cada campo, con `Field error=` |
| **Una regla del hogar lo impide** | `409` con su código | Trae `code`, de una lista cerrada | `Notice tone="danger"` sobre el formulario o junto a la acción |
| **Salió bien, pero conviene mirarlo** | `200` o `201` con `warnings` | Es un éxito: el recurso viene entero, con un array al lado | `Notice tone="warning"` **después** del cambio, no en lugar de él |

La primera columna es la que hay que tener clara: **una advertencia no es un
error**. Que la contraseña sea corta es forma; que el nombre de la ubicación ya
exista entre sus hermanas es regla; que el armario se pase de capacidad es un
aviso sobre algo que **ya se ha guardado**.

### El aviso de capacidad, que advierte sin bloquear

Es el caso que estrena la tercera fila y merece su párrafo, porque la tentación
de tratarlo como error es fuerte.

Una ubicación puede declarar capacidad, y
[`core-model.md`](../../../common/product/core-model.md) decidió hace tiempo que
superarla **advierte pero no bloquea**: bloquear con datos incompletos impediría
guardar algo que sí cabe. Y avisa **solo cuando puede demostrarlo** — un artículo
puede no declarar su peso ni su volumen, así que si lo conocido ya se pasa se
avisa, y si cabe pero falta por medir se calla.

Lo que este hito añade es **por dónde viaja el aviso**: en un array `warnings`
dentro del propio recurso devuelto, con `code` y `message` por entrada. La
operación responde `201` o `200`, el asset vuelve creado o movido, y la
advertencia va al lado.

Cómo se pinta, que es lo que decide este patrón:

- **`tone="warning"`, nunca `danger`.** No es un fallo. Y hay una consecuencia
  técnica además de estética: `Notice` reserva `role="alert"` para el tono
  `danger`, así que un `warning` se anuncia *polite* y no interrumpe. Es
  exactamente lo que corresponde a algo que no exige actuar.
- **Después del cambio, no en su lugar.** Primero el asset aparece donde se ha
  puesto; luego el aviso explica que ahí sobra.
- **El aviso no ofrece deshacer**, porque no hay nada que deshacer: la operación
  es la que el usuario pidió.
- **Un aviso por advertencia**, con su texto propio. Si algún día vienen dos, se
  apilan; no se resumen en «hay 2 avisos».

> **Dónde mirarlo.** El campo `warnings` ya está en
> [`openapi.yaml`](../../../../openapi.yaml), en el esquema `Asset`, junto al
> esquema `Warning` que declara sus códigos. Hoy solo hay uno,
> `LOCATION_CAPACITY_EXCEEDED`. Hay un ejemplo comentado en
> [`json-examples.md`](../../../common/contracts/json-examples.md), con el caso
> completo: un `201` que crea el asset **y** avisa de que la ubicación se queda
> corta.

### Qué hace el cliente con cada código

Los códigos de regla de negocio están enumerados en el esquema `Error` de
`openapi.yaml` —47 en total— y el backend los declara en
[`DomainError.kt`](../../../../backend/src/main/kotlin/com/drp/platform/error/DomainError.kt),
que hoy lleva 43. Los del Hito 2 son estos, y esta tabla es la que evita que cada
pantalla improvise:

> **Ese fichero ya no es del core.** Desde el Hito 2 de la Fase 2 vive en
> `com.drp.platform.error`, porque el primer módulo con reglas de negocio dejó
> claro que el core no puede ser quien enumere las reglas de sus módulos. Para el
> cliente no cambia nada —el contrato sigue teniendo **un solo** enumerado de
> errores— y por eso esta tabla se lee igual; lo que cambió es quién lo posee.
> Cinco de los códigos son ya de un módulo y no del core: los `SUPPLIER_*`, que
> el cliente solo puede recibir si ese módulo está encendido.

| Código | Cuándo llega | Qué hace la interfaz |
|---|---|---|
| `CATEGORY_DUPLICATE` | Alta o renombrado de categoría | Bajo el campo del nombre: ya existe una vigente así |
| `ARTICLE_DUPLICATE` | Alta o edición de artículo, y entrada que crea artículo | Bajo el nombre o el código de barras, y **ofrece el artículo que ya existe** en lugar de dejar en un callejón |
| `ARTICLE_UNIT_IMMUTABLE` | Cambiar la unidad de un artículo con existencias | Junto al campo de unidad, explicando que la unidad la fija el artículo para todas sus existencias |
| `ARTICLE_HAS_EXISTENCES` | Retirar un artículo que aún tiene existencias vivas | Aviso con **enlace a esas existencias**: la dependencia se nombra y se enlaza |
| `LOCATION_DUPLICATE` | Alta o renombrado de ubicación | Bajo el nombre. Y se dice lo que no es obvio: el nombre solo es único **entre hermanas** |
| `LOCATION_CYCLE` | Mover una ubicación dentro de su propia descendencia | Aviso junto al selector de destino. Mejor aún: no ofrecer descendientes como destino |
| `LOCATION_HAS_CHILDREN` | Eliminar una ubicación que no está vacía | Aviso con el número de hijas y enlace a ellas |
| `LOCATION_HAS_ASSETS` | Eliminar una ubicación con assets dentro | Igual, enlazando a lo que hay dentro |
| `EXISTENCE_ALREADY_IN_LOCATION` | Mover una existencia a donde ya hay otra del mismo artículo | **El caso que no es un error sino una bifurcación**: se ofrece fusionar, que es `POST /assets/{id}/merge` |
| `MERGE_SAME_ASSET`, `MERGE_ARTICLE_MISMATCH`, `MERGE_NOT_CONSUMABLE`, `MERGE_ASSET_DEACTIVATED` | Fusión mal planteada | Junto al selector de destino. Los cuatro son evitables filtrando lo que se ofrece |
| `ASSET_HAS_CHILDREN` | Baja de un asset que contiene otros | En el diálogo de confirmación, con la lista de lo que cuelga |
| `ASSET_HAS_ACTIVE_LOAN` | Baja de un asset prestado | Igual, enlazando al préstamo |
| `ASSET_QUANTITY_NOT_APPLICABLE` | Cantidad sobre un `DURABLE` | No debería llegar nunca: es un fallo de la interfaz, que ha ofrecido un campo que no tocaba |
| `ASSET_QUANTITY_NEGATIVE`, `INTAKE_QUANTITY_NOT_POSITIVE` | Cantidad fuera de rango | Bajo el campo de cantidad |
| `ASSET_LOCATION_CONFLICT` | Ubicación no válida para ese asset | Junto al selector de ubicación |

Dos lecturas de esa tabla que valen más que la tabla:

1. **Media docena de esos códigos son evitables desde la interfaz.** Los cuatro
   de fusión y el ciclo de jerarquía se evitan filtrando lo que se ofrece en el
   selector. Que el servidor los rechace es la red de seguridad, no el camino
   normal.
2. **Cuando hay una dependencia, se dice cuál y se enlaza.** Lo pide
   [`look-and-feel.md`](../../product-design/look-and-feel.md) y afecta a cinco
   de estos códigos. «No se puede eliminar» a secas obliga a buscar a mano qué lo
   impide.

Y una regla que atraviesa todo: **el `message` de la API no se muestra tal
cual.** Es texto de diagnóstico. Lo que se pinta sale de una traducción por
código, como hace hoy `messageFor` en
[`enrollment.tsx`](../../../../frontend/src/routes/enrollment.tsx).

### Las tres esperas

[`look-and-feel.md`](../../product-design/look-and-feel.md) distingue tres, y esa
distinción es lo que evita el spinner universal:

| Situación | Respuesta | ¿Existe? |
|---|---|---|
| Primera carga de una vista | Esqueleto con la forma real del contenido | **No** |
| Actualización de algo ya visible | Barra fina bajo la cabecera; **no se vacía lo que se está leyendo** | **No** |
| Acción del usuario | El botón pasa a ocupado, conserva su anchura y se desactiva | Sí |

El reintento automático ya está configurado y conviene conocerlo: el
`QueryClient` de [`App.tsx`](../../../../frontend/src/App.tsx) **no reintenta un
`401`, un `403` ni un `404`** —repetirlos solo retrasa el mensaje— y reintenta
dos veces lo demás. Lo que falta es el reintento **manual**, que es lo que
`look-and-feel.md` exige además del automático, y para el que `Notice` no tiene
ranura de acción.

### Cuándo no se dice nada

**El éxito por defecto es el propio cambio en pantalla.** Si el asset ya aparece
movido, un aviso que diga «asset movido» es ruido. Solo se anuncia lo que no se
ve: una operación en segundo plano, un correo enviado, algo fuera de la vista
actual. Es la regla que hoy se cumple: los tres únicos avisos de tono `success`
de la aplicación —«Ya casi está» tras crear el hogar, «Invitación enviada.» y
«Contraseña cambiada.»— confirman los tres algo que ocurre fuera de la pantalla,
en un correo o en otras sesiones.

### Antiusos

| Antiuso | Por qué |
|---|---|
| `tone="danger"` para el aviso de capacidad | Interrumpe con `role="alert"` algo que salió bien |
| Tratar `warnings` como si fuera un error y no aplicar el cambio | La operación **tuvo éxito**. Descartarla porque trae aviso es perder el trabajo del usuario |
| Un `Notice tone="success"` para algo que ya se ve | El cambio es su propia confirmación |
| Mostrar el `message` crudo de la API | Es diagnóstico: en el Hito 1 ya sale «El cuerpo no cumple el contrato» por una ruta mal cubierta |
| Un `alert` por cada campo mal | El error de campo va bajo su campo, y en silencio |
| Distinguir en el mensaje si un correo existe | Los endpoints anónimos responden igual a propósito; decirlo en la interfaz reintroduce la fuga |
| Un spinner encima de una lista que ya se veía | Es una actualización, no una carga |

## Decisiones abiertas

- **Dónde vive la traducción de código a texto.** Hoy `messageFor` está dentro de
  `enrollment.tsx` y solo cubre siete códigos; `household.tsx` repite la idea con
  ternarios en línea en dos sitios más. Con los diecinueve de la tabla de arriba esto
  tiene que ser un módulo propio, y probablemente un mapa exhaustivo que
  TypeScript obligue a completar.
- **Si el código del error se muestra.** `look-and-feel.md` pide que quede visible
  en pequeño para poder decirlo al pedir ayuda, y hoy `messageFor` lo traduce y lo
  descarta.
- **Qué se hace con un código desconocido.** El cliente tipa hoy 15 códigos de los
  41 del contrato; cualquier otro cae en el `default` y saca el `message` crudo.

## Lo que falta

- **La sesión caducada no está resuelta, y es el hueco más serio.** El access
  token dura unos quince minutos y **nadie lo renueva**: `api.refresh`,
  `storedRefreshToken()` y `forgetStoredRefreshToken()` están escritos y **no los
  llama nadie**. Pasado ese rato, cada petición devuelve `401`, TanStack Query no
  reintenta —correctamente— y no hay nada que cierre la sesión ni que lo
  explique. Con el enrolamiento del Hito 1 apenas se nota, porque las pantallas
  son cortas; con un inventario en el que se pasa el rato, se va a notar en la
  primera sesión larga.
- **No hay error bloqueante a pantalla completa.** Sesión caducada, hogar sin
  acceso y error del servidor tienen tratamiento definido —ilustración,
  explicación en una frase, una única salida— y ningún componente.
- **No hay `Toast`.** El aviso efímero de esquina con cierre y deshacer no
  existe, y con él no existe el deshacer.
- **`Notice` no tiene ranura de acción ni cierre**, así que no hay «Reintentar» ni
  forma de descartar un aviso leído.
- **No hay franja de conexión perdida.** La degradación honesta que
  `look-and-feel.md` exige —decirlo, no perder lo escrito, marcar la lectura en
  caché— no está en ninguna parte.
- **No hay diálogo de confirmación**, así que las cinco bajas y borrados del Hito
  2 no tienen dónde confirmarse.

## Referencias

- [`components/notice.md`](../components/notice.md) y
  [`components/status-badge.md`](../components/status-badge.md): feedback del
  sistema frente a estado del dominio.
- [`components/spinner.md`](../components/spinner.md): la tercera espera.
- [`form.md`](form.md) y [`listing.md`](listing.md)
- [`look-and-feel.md`](../../product-design/look-and-feel.md): los siete estados
  de experiencia.
- [`openapi.yaml`](../../../../openapi.yaml) y
  [`DomainError.kt`](../../../../backend/src/main/kotlin/com/drp/platform/error/DomainError.kt)

## Historial de cambios

| Fecha | Cambio | Autor |
|---|---|---|
| 2026-08-12 | Creación del documento con las tres respuestas de la API, el aviso de capacidad del Hito 2 y el reparto por código. | Equipo DRP |
| 2026-08-17 | Corrección de estado al cerrar la Fase 1: el canal de advertencias y la traducción compartida de códigos están **implementados**, y la sesión caducada se resolvió por otro camino —renovación con el refresh—. El error bloqueante sigue previsto como pieza, con un caso resuelto a mano en la pantalla externa de un préstamo. | Equipo DRP |
