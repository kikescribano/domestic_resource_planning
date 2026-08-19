# Módulo: Warehouse

| Campo | Valor |
|---|---|
| Estado | Vigente |
| Responsable | Equipo DRP |
| Clave | `WAREHOUSE` |
| Prefijo de ruta | `/api/v1/warehouse` |
| Última revisión | 2026-08-19 |

> **Esta ficha se escribió antes que la primera línea de código del módulo**, que
> es la regla que el [catálogo](README.md) fija. En [Proveedores](suppliers.md)
> importaba porque no había consumidor al que preguntar; aquí importa por lo
> contrario: **Warehouse sí tiene consumidor previsto** —Compras, en el Hito 4—
> y además es el primer módulo que **reacciona a lo que pasa en el core**. Lo que
> se escriba aquí es a la vez el límite del módulo y el contrato de una
> conversación que empieza en el hito siguiente.
>
> Escribirla por delante volvió a pagar, y las cuatro cosas están más abajo:
> obligó a resolver **dónde vive el peso y el volumen de un asset** —una pregunta
> heredada de la Fase 1 que resultó ser del core y no de aquí—, destapó que la
> [ADR-010](../../common/architecture/decisions/ADR-010-module-boundaries-and-activation.md)
> **dice de la reactivación lo contrario de lo que hace el código**, obligó a
> decidir **si un aviso se repite cada noche** antes de escribir la primera
> comprobación, y dejó claro que la ficha tenía que declarar **la frontera contra
> el core sin ambigüedad** antes de que hubiera una tabla que la contradijese.

## Responsabilidad

**Qué hay, cuánto queda y cuándo se acaba o se estropea.** Warehouse es el
cuaderno de la despensa, el garaje y el trastero: lleva el **histórico de
movimientos** de cada existencia, el **mínimo** por debajo del cual hay que
reponer, y los **lotes con su fecha de caducidad**. De esas tres cosas salen sus
dos avisos —caducidad próxima y mínimo alcanzado— y los dos eventos con los que
Compras sabrá qué hay que comprar.

Es un módulo y no una parte del core porque **un hogar puede no querer nada de
esto**: quien solo quiere saber dónde está el taladro no necesita un cuaderno de
consumos, y el core sigue funcionando igual con Warehouse apagado.

## La frontera contra el core

Esta sección es el motivo de que el Hito 3 pida una ficha, y se escribe sin
ambigüedad porque es la que más fácil se difumina con una tabla de más.

**El core mantiene un contador, y solo uno.**

| Del core | De Warehouse |
|---|---|
| `Asset.quantity`: **cuánto hay ahora mismo** de una existencia | **Cómo llegó a ser eso**: el histórico de movimientos |
| `Article.unit`: en qué se cuenta, y la fija el artículo | El **mínimo** por debajo del cual hay que reponer |
| `Article.packSize`: cuánto trae un envase, en la unidad del artículo | La **caducidad** y los **lotes** |
| `Article.unitWeightGrams` / `unitVolumeMl`: cuánto pesa y ocupa una unidad | El **aviso** de que algo se acaba o caduca |
| El aviso de capacidad de una ubicación | La **conversión de una entrada** de envases a unidades, usando el `packSize` del core |

**Warehouse no lleva un segundo contador.** No hay ninguna columna en ninguna de
sus cuatro tablas que guarde «cuánto hay» de una existencia: cuando lo necesita
—para pintar la lista, para comparar con el mínimo, para no dejar consumir más de
lo que queda— lo **lee del core**. Dos contadores es lo único que esta frontera no
puede permitirse, porque el día que discrepen no hay forma de saber cuál miente.

La única cantidad que Warehouse guarda es la de un **lote**, y no es la misma
pregunta: «de los 900 g de arroz que hay, 400 son del lote que caduca en marzo».
La suma de los lotes de una existencia no puede pasarse de lo que el core dice que
hay —lo comprueba el caso de uso— pero **puede quedarse corta**, y eso es normal:
lo que no está en ningún lote es lo que nadie se molestó en fechar.

**Y el sentido de la escritura.** Warehouse **no escribe en las tablas del core**.
Cuando una operación suya tiene que mover el contador —registrar un consumo— lo
hace **invocando el caso de uso del core** (`UpdateAsset`), que es la dirección
`módulo → core` que la ADR-010 permite y la misma que el Hito 4 usará para cerrar
la compra contra `RegisterConsumableIntake`. El movimiento que aparece en el
cuaderno no lo escribe esa operación: lo escribe **el handler del evento que el
core publica al hacerlo**. Ver [Datos y transacciones](#datos-y-transacciones).

## Límites

### Incluido

- El **movimiento de existencias**: un asiento por cada cambio de cantidad o de
  sitio de una existencia de consumible, con lo que había antes, lo que hay
  después y por qué.
- El **mínimo por artículo**, y el estado derivado de estar por debajo de él.
- Los **lotes con caducidad**: un código opcional, una fecha y una cantidad.
- La **antelación del aviso de caducidad**, por artículo y por sitio: la nevera no
  se avisa con la misma antelación que la despensa.
- Los **dos avisos por fecha** sobre la plataforma del Hito 1.
- Los **dos eventos** que Compras consumirá en el Hito 4.

### Fuera de alcance

- **El contador de cantidad.** Es del core, y esta ficha no lo duplica. Ver
  [La frontera contra el core](#la-frontera-contra-el-core).
- **Qué se compra, dónde y cuándo.** Es de Compras (Hito 4). Warehouse **detecta
  la falta** y lo publica; decidir que eso se compra, a quién y en qué cantidad es
  del otro lado. La frontera se escribe en las dos fichas y desde este lado es
  esta.
- **Lo que cuesta.** Es de Gastos y presupuesto. Aquí no hay importes: ni precio
  de compra, ni valoración de existencias, ni coste del consumo.
- **Recetas y consumo planificado.** Es de Recetas y menú semanal, que es el
  consumidor natural de este módulo y no entra en la Fase 2.
- **El mantenimiento de un `DURABLE`.** Es de CMMS. Warehouse **solo mira
  existencias de consumible**: un taladro no se agota ni caduca.
- **Nombrar la presentación de compra** —«pack de 6», «garrafa de 5 l»—. La
  *conversión* ya está resuelta y es del core (`packSize`); ponerle nombre es un
  concepto de compra y nace en el Hito 4. Ver
  [Decisiones abiertas](#decisiones-abiertas).
- **La purga del histórico.** `warehouse_movements` crece sin parar y este módulo
  no la escribe. Ver [Operación](#operación).

## Lenguaje de dominio

| Término | Significado |
|---|---|
| `StockMovement` | Un asiento del cuaderno: qué existencia, qué había antes, qué hay después, por qué y dónde. **No se modifica nunca**: se escribe y se queda. |
| `MovementKind` | Por qué se movió: `OPENING` (la siembra), `INTAKE`, `ADJUSTMENT`, `MERGE`, `DECOMMISSION` y `RELOCATION`. Los cuatro de en medio son los del `QuantityChangeReason` del core, con el mismo nombre a propósito: renombrarlos daría dos vocabularios para el mismo hecho. |
| `WarehouseArticle` | La **ficha del módulo sobre un artículo**: su mínimo, su antelación de aviso y desde cuándo está bajo mínimos. No duplica nada del `Article` del core. |
| `WarehouseLocation` | La ficha del módulo sobre un sitio: hoy solo su antelación de aviso y una nota. Existe porque «avísame con tres días en la nevera y con treinta en la despensa» es una regla **del sitio** y el core no tiene dónde ponerla. |
| `StockLot` | Un lote de una existencia: cantidad, fecha de caducidad y código opcional. Vive mientras no se consuma. |
| Bajo mínimos (`lowStockSince`) | El total vivo de un artículo ha caído a su mínimo o por debajo. Es un **estado con fecha de entrada**, no un cálculo al vuelo: es lo que permite avisar una vez y no cada noche. |

## Casos de uso

| Caso de uso | Resultado observable |
|---|---|
| `ListStock` | Las existencias vivas de consumible del hogar, con su cantidad **leída del core**, su mínimo, si está bajo mínimos y su caducidad más próxima. Filtrable por texto, por sitio, por bajo mínimos y por caducidad dentro de N días |
| `GetStockItem` | Una existencia con sus lotes y sus últimos movimientos |
| `RecordConsumption` | Descuenta lo consumido. Falla si no es positivo o si excede lo que hay |
| `ListStockMovements` | El cuaderno, paginado y filtrable por existencia y por artículo |
| `UpdateWarehouseArticle` | Fija o borra el mínimo y la antelación de aviso de un artículo |
| `UpdateWarehouseLocation` | Lo mismo para un sitio |
| `ListStockLots` | Los lotes vivos del hogar, ordenados por caducidad |
| `RegisterStockLot` | Da de alta un lote sobre una existencia. Falla si ya existe uno igual o si los lotes suman más de lo que hay |
| `UpdateStockLot` | Modificación parcial de un lote, con la semántica de `PATCH` del contrato |
| `DiscardStockLot` | Lo da por consumido. No toca el contador del core: consumir es `RecordConsumption` |

Diez operaciones. Es **el módulo con más dominio de los cuatro**, que es lo que le
toca: lo que se prueba aquí ya no es el camino de un módulo —eso lo dejó recorrido
Proveedores— sino que un módulo **reaccione al core sin que el core lo sepa**.

## Modelo de dominio

Dos agregados pequeños y un cuaderno:

- **`WarehouseArticle`** y **`WarehouseLocation`** son las dos *fichas* del
  módulo: donde se configura la regla. No tienen comportamiento más allá de
  guardar el mínimo y la antelación.
- **`StockLot`** cuelga de una existencia del core.
- **`StockMovement`** no es un agregado sino un **libro**: se añade y no se toca.

**Invariantes:**

1. **Warehouse solo sigue existencias vivas de consumible.** Un `DURABLE`, o una
   existencia dada de baja, no admite consumo ni lote.
2. **Un consumo es estrictamente positivo y no puede pasarse de lo que hay.** La
   segunda mitad la comprueba el caso de uso contra el contador del core, no
   contra una copia.
3. **Los lotes de una existencia no suman más que su cantidad.** Pueden sumar
   menos: lo que no está en un lote es lo que nadie fechó.
4. **No hay dos lotes vivos iguales** en la misma existencia —mismo código y misma
   fecha—, con `NULLS NOT DISTINCT` para que dos lotes sin código y con la misma
   fecha también choquen.
5. **El mínimo y la antelación no son negativos.**
6. **Un artículo y un sitio tienen como mucho una ficha** en el módulo.

Las seis se comprueban en el caso de uso **y**, las que la base de datos puede
expresar, en el esquema: es la regla del core y no una duplicación —la
comprobación da el mensaje que el contrato declara y la restricción cierra la
carrera entre dos peticiones simultáneas.

## Contratos publicados

### API

Bajo `/api/v1/warehouse`, en [`openapi.yaml`](../../../openapi.yaml), con
`operationId` en las diez operaciones. Todo lo que cuelga de ese prefijo responde
`403 MODULE_INACTIVE` mientras el hogar no lo tenga encendido, y eso no lo sabe
ningún controlador del módulo: lo pone el filtro de plataforma sobre el prefijo
que declara `WarehouseModule`.

Códigos de error propios, enumerados en el esquema `Error` del contrato y
declarados en **`com.drp.platform.error`**, que es donde el Hito 2 dejó el
enumerado y no en el core:

`STOCK_ITEM_NOT_TRACKED`, `STOCK_CONSUMPTION_NOT_POSITIVE`,
`STOCK_CONSUMPTION_EXCEEDS_QUANTITY`, `STOCK_LOT_DUPLICATE` y
`STOCK_LOT_EXCEEDS_QUANTITY`.

### Eventos consumidos

**Warehouse es el primer módulo que consume eventos del core**, y consume seis de
los trece del catálogo (README 5.2.3). Todos por `ModuleEventHandler`, que resuelve
por él las tres garantías del bus y **abre él mismo la transacción
`REQUIRES_NEW`** —la única regla cuyo incumplimiento puede tumbar al core.

| Evento | Qué hace Warehouse con él |
|---|---|
| `ArticleCreated` | Abre la ficha del artículo, sin mínimo y sin antelación |
| `LocationCreated` | Abre la ficha del sitio, sin antelación |
| `AssetCreated` | Si es una existencia de consumible: abre las dos fichas que le faltan y asienta su `INTAKE` inicial |
| `AssetQuantityChanged` | Asienta el movimiento con el motivo que trae el evento, y recalcula si el artículo entra o sale de bajo mínimos |
| `AssetMoved` | Asienta una `RELOCATION` |
| `AssetDeactivated` | Da por consumidos los lotes vivos de esa existencia y recalcula el artículo |

**Un solo handler y no seis.** Seis clases serían seis suscriptores recibiendo los
trece eventos cada uno para descartar doce, seis conjuntos de idempotencia y seis
transacciones abiertas por evento. Con uno, la reacción del módulo al core está
escrita en un sitio, que además es donde hay que mirar cuando algo no cuadra.

**Qué hace un handler cuando el módulo no tiene nada de ese hogar todavía.** Nada
especial, **y esa es la decisión**: cada rama abre por su cuenta lo que necesita en
lugar de suponer que la siembra ya pasó. La siembra y los handlers comparten las
mismas dos funciones —«abre la ficha de este artículo», «abre la ficha de este
sitio»—, así que «el módulo aún no ha sembrado» deja de ser un caso. La
alternativa —que el handler abandone si no encuentra la ficha— es peor por dos
motivos: convierte una carrera de milisegundos entre la activación y el primer
evento en un dato que falta para siempre, y deja que la siembra y los handlers
puedan divergir, que es exactamente lo que un módulo que se sincroniza no puede
permitirse.

### Eventos publicados

Al contrario que Proveedores, aquí **sí hay consumidor previsto**, así que el
criterio del catálogo —que alguien lo necesite— se cumple.

| Evento | Cuándo se publica | Consumidores conocidos | Versión |
|---|---|---|---|
| `StockBelowMinimum` | El total vivo de un artículo **cruza hacia abajo** su mínimo | Compras (Hito 4): entra en la lista de la compra | 1 |
| `StockDepleted` | El total vivo de un artículo llega a **cero** | Compras (Hito 4): es el caso que su pregunta abierta tiene que resolver | 1 |

Se publican **en el cruce y no en cada cambio**: mientras el artículo siga por
debajo del mínimo no se vuelve a publicar, y vuelve a armarse cuando la cantidad
sube por encima. Un evento por cada cucharada de azúcar dejaría a Compras
recibiendo cientos al día para no decir nada nuevo.

> **Corregido el 2026-08-19, en el Hito 4, cuando llegó el consumidor.**
> `StockDepleted` colgaba de la rama de «acaba de bajar del mínimo», así que un
> artículo **sin ficha de mínimo —que son casi todos— no lo publicaba jamás**, al
> contrario de lo que esta tabla declara. Nadie lo había notado porque hasta
> entonces no había nadie escuchando, y lo destapó Compras al construir sobre la
> regla «lo que llega a cero entra en la lista», que era falsa para casi toda una
> despensa. El cruce a cero se deduce ahora **del delta que ya trae el evento del
> core** —el total de antes es el de ahora menos lo que acaba de cambiar— y no
> hizo falta ninguna columna nueva. **Los dos avisos por fecha no cambian**: esos
> sí exigen ficha con mínimo, y para ellos es lo correcto.

**Lo que un consumidor puede dar por hecho**, declarado aquí para que el Hito 4 no
tenga que adivinarlo:

- **El evento dice lo que Warehouse sabe, no lo que hay que hacer.** «Este
  artículo está bajo mínimos» no significa «cómpralo»: cuánto y cuándo es de
  Compras.
- **El `articleId` es del core y sigue siendo legible aunque Warehouse se
  apague.** Un consumidor que guarde ese identificador no queda colgando de que
  este módulo esté encendido.
- **La cantidad que viaja es la del core** en el instante del cruce, en la `unit`
  del artículo. No es una cantidad de Warehouse porque Warehouse no tiene ninguna.
- **Un consumidor tiene que degradar limpiamente si Warehouse está apagado**, y no
  es cortesía: ninguna clase de `com.drp.module.warehouse` es importable desde otro
  módulo, y ArchUnit falla la construcción si alguien lo intenta. Con Warehouse
  apagado, estos dos eventos simplemente no llegan, y el otro módulo tiene que
  seguir funcionando sin ellos.
- **La entrega es at-least-once y en memoria.** Un consumidor que necesite estar al
  día de lo que pasó antes de encenderse **no lo pide reproduciendo eventos**: se
  siembra desde el estado, como hace todo el mundo desde la ADR-010.

## Dependencias consumidas

| De dónde | Qué | Para qué |
|---|---|---|
| `com.drp.platform.module` | `ModuleDescriptor`, `ModuleSeeder`, `ModuleEventHandler`, `ModuleActivation` | Existir en el catálogo, sembrarse y reaccionar al bus |
| `com.drp.platform.event` | `DomainEvent`, `EventBus` | Consumir los seis y publicar los dos |
| `com.drp.platform.schedule` | `ScheduledCheck`, `CheckOwner` | Sus dos comprobaciones periódicas |
| `com.drp.platform.notice` | `NoticeDraft` | Sus dos avisos |
| `com.drp.platform.page` | `Page`, `Pagination`, `PageResponse` | La misma paginación que todo el contrato |
| `com.drp.platform.error` | `ErrorCode`, `BusinessRuleViolation`, `ValidationFailure`, `ResourceNotFound` | Sus cinco reglas, con la forma única del contrato |
| `com.drp.platform.tenant` | `TenantContext` | El hogar, en la siembra y en los handlers |
| `com.drp.core.application.port` | `SessionClaims` | La autoría, que apunta a la **pertenencia** |
| `com.drp.core.application.usecase` | `UpdateAsset`, `Patch` | Mover el contador del core al registrar un consumo, y la semántica de `PATCH` |
| `com.drp.core.domain.catalog` | `MeasurementUnit` | La unidad, que es del artículo |
| `com.drp.core.adapter.http` | `JsonPatch` | Distinguir «no menciones esto» de «ponlo a nulo» |
| `com.drp.core` (por SQL, sin importar nada) | `assets`, `articles`, `locations` | Leer el estado: cantidad, unidad y nombre |

La dirección `módulo → core` **está permitida**, y aquí se usa más fuerte que en
Proveedores: además de leer el estado del core por SQL, este módulo **invoca un
caso de uso suyo** para mover el contador. Lo que sigue sin admitir ninguna regla
es la contraria — el core no sabe que Warehouse existe.

## Datos y transacciones

**Cuatro tablas, las cuatro en `public` y las cuatro con `household_id`, RLS y
`FORCE`.** En `public` y no en un esquema propio, por lo mismo que Proveedores: el
esquema aparte es la trampa del módulo de prueba del Hito 0, que lo usa
precisamente para **no** falsear el recuento de tablas del modelo.

| Tabla | Qué guarda |
|---|---|
| `warehouse_articles` | La ficha del módulo sobre un artículo: mínimo, antelación y estado de bajo mínimos |
| `warehouse_locations` | La ficha sobre un sitio: antelación y nota |
| `warehouse_lots` | Un lote de una existencia: código, caducidad y cantidad |
| `warehouse_movements` | El cuaderno. **La única tabla del modelo que crece sin techo** |

Los nombres son del módulo, llevan su prefijo y quedan declarados aquí para que
otro no los tome.

**Migración `V10`**, que deja el esquema completo desde una base vacía, políticas
incluidas. El recuento del modelo sube de **diecinueve a veintitrés** y la lista de
tablas **sin** política no se toca: las cuatro llevan `household_id`, RLS y
`FORCE`.

**Las claves ajenas hacia el core van con `ON DELETE CASCADE`**, que es lo que el
Hito 2 decidió y por el mismo motivo: con el `RESTRICT` que rige por omisión, una
fila de un módulo convertiría una operación del core en un `500`.

**Con una excepción, y es del cuaderno.** `warehouse_movements` guarda
`location_id` **sin clave ajena**, y guarda además el nombre del sitio tal y como
era ese día. `DeleteLocation` borra la fila de verdad, así que una clave ajena en
cascada **borraría el asiento** de que algo se movió al garaje el día que alguien
borre el garaje — y un libro que se reescribe cuando cambia el mundo no es un
libro. Es exactamente el argumento que la ADR-011 da para que un aviso lleve su
texto dentro y ninguna clave ajena hacia lo que describe: **lo que se escribió ese
día siguió siendo cierto**. El `asset_id` sí la lleva, porque un asset no se borra
nunca —se da de baja— y la cascada solo se dispara cuando se borra el hogar
entero.

**Transacciones.** Tres formas, y las tres importan:

- **Los casos de uso de lectura y de configuración**: la suya, con el gestor
  consciente de inquilino que fija `app.household_id`. Nada especial.
- **El handler de eventos**: `REQUIRES_NEW`, y **la abre `ModuleEventHandler`**
  desde el Hito 0, así que el módulo no tiene que acordarse. Es la única regla
  cuyo incumplimiento puede tumbar al core: unirse a la transacción del core
  devuelve cero filas, y un handler unido que falle se lleva por delante el alta
  que originó el evento.
- **`RecordConsumption`, que no abre ninguna.** Es deliberado y es la parte más
  sutil del módulo: la operación calcula la cantidad nueva e invoca `UpdateAsset`
  del core, cuya transacción tiene que ser **la de fuera** para que al cerrarse
  dispare el `AFTER_COMMIT` que escribe el asiento. Envolverla en una transacción
  propia dejaría el asiento para después de la respuesta —o, peor, dentro de una
  transacción que el handler no puede ver—. **El cuaderno lo escribe siempre el
  handler y nunca la operación**, que es lo que garantiza que un consumo hecho
  desde Warehouse y uno hecho con el `PATCH` del core produzcan exactamente el
  mismo asiento.

**Siembra.** `WarehouseSeeder` es **la primera siembra de verdad**: Proveedores
escribió una vacía porque en el core no hay ningún fontanero que leer, y aquí sí
hay algo. Recorre, para el hogar que se está encendiendo:

1. **Los artículos vivos**, abriendo su ficha sin mínimo.
2. **Los sitios**, abriendo la suya sin antelación.
3. **Las existencias vivas de consumible**, asentando un `OPENING` con lo que hay
   ahora, para que el cuaderno empiece en el estado real y no en cero.

**Y es idempotente**, porque tiene que serlo: ver la decisión de más abajo.

**Desactivar conserva.** Apagar el módulo no borra ni una fila: las cuatro tablas
se quedan como estaban.

### Reactivar vuelve a sembrar, y por eso la siembra es idempotente

Esta ficha destapó que **la ADR-010 dice de esto lo contrario de lo que hace el
código**, y hay que decidirlo aquí porque Warehouse es el primer módulo al que le
importa: la siembra de Proveedores está vacía, así que nadie lo había notado.

- La ADR-010 escribe «activar es idempotente, así que reactivar no vuelve a
  sembrar».
- `ActivateModule` **solo** se ahorra la siembra cuando el módulo ya está
  `ACTIVE`. Pasar de `INACTIVE` a `ACTIVE` —reactivar— la ejecuta.

**Se conserva el comportamiento del código y se corrige la frase**, con la
alternativa descartada escrita. El motivo: un hogar que apagó Warehouse tres meses
**se ha perdido todos los eventos de ese periodo**, porque el bus es in-process y
no guarda nada. Si reactivar no resembrase, el módulo volvería con el cuaderno de
hace tres meses, sin los artículos nuevos y sin las existencias nuevas —y **nada lo
diría**: la pantalla enseñaría una lista corta y verosímil. Un módulo que miente en
silencio sobre lo que hay en la despensa es peor que uno que tarda un segundo más
en encenderse.

La alternativa era **no resembrar y ofrecer una resincronización aparte**, con su
operación y su botón. Se descarta porque pone la corrección detrás de que alguien
sepa que hace falta, y quien reactiva un módulo es justo quien no lo sabe.

**El precio es que la siembra tiene que ser idempotente de verdad**, y lo es por
construcción y no por cuidado: las dos fichas se abren con `ON CONFLICT DO
NOTHING` sobre su índice único, y el `OPENING` del cuaderno lo protege un índice
único parcial —**un solo `OPENING` por existencia**—, de modo que resembrar
completa lo que falte y no duplica ni un asiento. Lo que la reactivación **no**
reconstruye es el histórico del periodo apagado: los movimientos de esos tres
meses no ocurrieron para este módulo y no se inventan. El cuaderno recoge desde
donde estaba y el `OPENING` sigue siendo el del día en que se encendió la primera
vez, que es la verdad.

## Seguridad

- **Aislamiento en dos capas, igual que el core.** Todo caso de uso filtra por el
  `householdId` del token y nunca por uno del cliente, y las cuatro tablas llevan
  política de RLS con `FORCE`.
- **Y la capa que el core no tiene**: el gate. Con el módulo apagado, todo lo que
  cuelga del prefijo responde `403 MODULE_INACTIVE` sin llegar al controlador, y
  **el handler de eventos no escribe ni una fila** para un hogar que no lo tenga
  activo. Lo segundo tiene prueba propia, porque es lo que este hito estrena.
- **El hogar del handler sale del sobre del evento y no del contexto**, que es lo
  que lo sitúa con certeza cuando lo que despierta el evento no es una petición.
- **Ningún dato personal.** Warehouse guarda cantidades y fechas. La autoría
  apunta a la pertenencia, con su clave ajena compuesta, como en todo el modelo.
- **Cualquier miembro del hogar puede leer y escribir.** Quien puede ver la
  despensa puede apuntar que se ha acabado el arroz. Encender y apagar el módulo
  sigue siendo solo de administrador, y eso lo corta plataforma.

## Verificación

| Nivel | Qué se comprueba |
|---|---|
| Dominio | Las seis invariantes, sin base de datos |
| Aplicación (integración) | Las diez operaciones contra PostgreSQL real, con usuario **sujeto a RLS** |
| Esquema | Que las cuatro tablas nuevas llevan `household_id`, RLS, `FORCE` y política, y que el recuento del modelo sube de diecinueve a veintitrés **sin tocar** la lista de tablas sin política |
| Event bus | Que un hogar con el módulo **apagado** no ve ni una fila escrita por ninguna de las seis ramas, y que el de al lado sí |
| Siembra | Que enciende sobre lo que ya había, y que **apagar y volver a encender no duplica ni un asiento** |
| Avisos | Que las dos comprobaciones producen su aviso **una vez** y no cada noche, y que el resumen diario se lee del **Mailpit de verdad** |
| Adaptador HTTP | Las dos mitades del gate sobre la ruta real: apagado `403`, encendido `200` |
| Recorrido vertical | Añadido a la batería existente y no en una suite paralela: encender el módulo, ver lo que ya había, fijar un mínimo, registrar un consumo, ver el asiento y dar de alta un lote con caducidad, con axe en los dos modos, foco, teclado y reflujo de 320 px a ultrawide |

**El barrido de aislamiento** —autenticado como hogar A, ninguna operación
devuelve ni modifica datos del hogar B— lo tuvo asignado el **Hito 6**, que es
donde el roadmap lo puso para toda la fase.

> **Cerrado el 2026-08-19, en el Hito 6, y este módulo es el que más sacó de
> él.** Las diez operaciones entraron en el barrido y **destapó dos defectos que
> ninguna prueba de recorrido podía ver**, porque las dos piden un identificador
> que el hogar no tiene: `PATCH /warehouse/articles/{articleId}` y
> `PATCH /warehouse/locations/{locationId}` respondían **`500`** ante un
> identificador de otro hogar, donde el contrato declara `404`. Abrían la ficha
> antes de comprobar nada, así que la clave ajena reventaba antes de llegar al
> `ResourceNotFound`. Ahora se comprueba primero que la fila del core existe —que
> con RLS significa a la vez «no existe» y «no es tuya», que es justo lo que hay
> que responder—, con `articleName` y un `locationName` nuevo del mismo corte.
>
> Y quedó anotado con su motivo lo que **no** era defecto: tres operaciones de
> este módulo se niegan con `409 STOCK_ITEM_NOT_TRACKED` y no con `404`, que es
> lo que el contrato declara y además dice **menos** que un `404` —no distingue
> «no existe» de «existe y no es consumible viva»—.

## Operación

- **Dos comprobaciones periódicas**, las primeras de un módulo de verdad:
  `WarehouseExpiryCheck` y `WarehouseMinimumStockCheck`, las dos con
  `CheckOwner.Module("WAREHOUSE")`, así que el recorrido diario **solo entra donde
  el módulo está encendido**.
- **Sus avisos no se repiten mientras la condición siga siendo cierta**, y esa fue
  una decisión y no un detalle. Ver más abajo.
- **`warehouse_movements` crece sin techo, y es lo primero de este módulo que
  cambia el orden de magnitud del disco por hogar.** Todo lo que había hasta ahora
  crece con lo que el hogar *tiene*; el cuaderno crece con lo que el hogar *hace*,
  y una casa que apunta sus consumos escribe varias filas al día para siempre.
  **Medido el 2026-08-19, en el Hito 6**, y el aviso valía: es la tabla que más
  crece de las cinco. La [medición](../operations/capacity-measurements.md) se
  partió en dos por esto —lo que el hogar *tiene* y lo que *hace* son magnitudes
  distintas y un solo número las mezcla— y la segunda da **2457 B por día, unos
  875 kiB por hogar y año**. La purga sigue sin escribirse **y ahora con un
  motivo y no por olvido**: a ese ritmo un hogar tarda diez años en llegar a 9 MB,
  así que borrar hoy el historial de una casa resolvería el problema equivocado.
  Su criterio de retención y su disparador están escritos allí.
- **Recuperación.** Este módulo **sí** deriva su estado del core, así que sí existe
  el caso «se ha quedado desincronizado» — y la salida es la siembra, que es
  idempotente y se ejecuta al reactivar.

### Un aviso por condición, no uno por noche

Los tres procesos del core son idempotentes y no repiten; el módulo de prueba del
Hito 0 repite **a propósito**, para poder contar pasadas. Ninguno de los dos es el
modelo para un módulo de verdad, así que hay que decidirlo, y se decide así:

**Un aviso se levanta cuando la condición empieza a ser cierta, y no vuelve a
levantarse mientras siga siéndolo.** Un yogur caducado que avisara treinta noches
seguidas es la forma más rápida de que se filtre el resumen diario entero — y con
él, el aviso que sí importaba.

Se implementa con estado en las tablas del módulo y no consultando los avisos ya
escritos: `warehouse_lots` lleva **en qué fase avisó** y `warehouse_articles`,
**desde cuándo está bajo mínimos** y **si ya se dijo**. Consultar
`household_notices` habría sido la otra salida, y se descarta porque obliga al
módulo a leer una tabla de plataforma y a reconocer sus propios avisos por el
texto.

**Un lote avisa dos veces como mucho, y no es una repetición sino dos hechos
distintos**: `NEAR` cuando entra en la ventana de antelación —«caduca el jueves»— y
`EXPIRED` el día que de verdad caduca —«se pasó»—. La segunda es una noticia nueva,
y no darla dejaría al hogar con un aviso de hace tres semanas como única
advertencia.

**El estado se vuelve a armar solo.** Reponer por encima del mínimo borra el estado
de bajo mínimos, así que la próxima vez que se caiga vuelve a avisar. Sin eso, el
aviso sería de una sola vez en la vida del artículo.

## Decisiones abiertas

- ~~**Cómo nombra Compras la presentación de compra.**~~ **Resuelta en el Hito 4
  (2026-08-19): no necesita nombre propio.** La *conversión* ya estaba resuelta y
  no era de aquí —el core la guarda en `Article.packSize`—, y lo que faltaba era
  el **nombre** de esa presentación. Compras decidió **componerlo y no guardarlo**:
  el par `packSize`/`unit` ya lo dice todo, y un texto libre sería una segunda
  fuente de verdad que puede contradecir al envase. El disparador de revisarlo
  está escrito en [`purchasing.md`](purchasing.md): el día que un artículo necesite
  dos presentaciones a la vez, deja de ser una etiqueta y pasa a ser una tabla.
- **Si el consumo debe poder repartirse entre lotes.** **Reafirmada el
  2026-08-19, al cerrar la fase: sigue sin repartirse, y el disparador pasa a ser
  algo que el propio módulo ya sabe ver.** Hoy `RecordConsumption` descuenta del
  contador del core y **no toca ningún lote**: quien se acabe un lote lo marca a
  mano con `DiscardStockLot`. Repartir automáticamente exigiría decidir un orden
  —el que antes caduca, presumiblemente— y ese orden es una regla de producto que
  nadie ha pedido.

  Lo que sí se puede cerrar sin datos reales es **dónde se mira**, que es lo que
  el disparador no decía. La invariante `requireLotsFit` impide que los lotes
  sumen más que la existencia **al crearlos o al ampliarlos**, pero un consumo
  baja el contador del core sin tocar ningún lote: **la desigualdad se puede
  romper por el otro lado, y el módulo lo tiene delante**. La suma de los lotes
  vivos de una existencia contra su `quantity` está en sus propias tablas y en la
  del core, así que el disparador es una consulta y no una impresión:

  > El día que **más de un hogar de cada cuatro** tenga alguna existencia cuyos
  > lotes vivos sumen por encima de su cantidad, la gente está consumiendo sin
  > marcar y el reparto automático deja de ser una comodidad.

  **Responsable**: la primera revisión de operación con hogares reales dentro, no
  un hito — igual que la de la categoría de servicio de
  [Proveedores](suppliers.md), y por el mismo motivo: son preguntas de uso y no
  de diseño.
- ~~**Si la antelación del aviso necesita un valor por hogar.**~~ **Resuelta el
  2026-08-19, y resuelta junto con la de [Mantenimiento](maintenance.md), que era
  la misma pregunta escrita dos veces: cuando haga falta será una tabla de
  plataforma, y no una de este módulo.** Hoy la cadena es sitio → artículo →
  **siete días**, que está en el código.

  Lo que la hace resoluble sin datos es que **no es una pregunta de uso sino de
  dónde vive el dato**. Este módulo tiene ya dos eslabones de anulación —el sitio
  y el artículo— y CMMS tiene el suyo —el plan—; lo que a las dos cadenas les
  falta es **el mismo último eslabón**, el valor por defecto del hogar. Ponerlo en
  cada módulo daría dos tablas de configuración para la misma preferencia, y esa
  duplicación es la señal, no el problema: **la antelación no es una regla del
  módulo sino una preferencia del hogar sobre la entrega**, y la entrega es de
  plataforma ([ADR-011](../../common/architecture/decisions/ADR-011-scheduled-checks-and-notice-delivery.md)).
  Cada módulo seguiría poseyendo **su regla** —qué se avisa y cuándo— que es lo
  que la sección 4.2 del README decidió; lo que se movería es solo el número.

  **No se construye ahora**, y el disparador es el tercero: **el día que un tercer
  módulo traiga una regla de fecha con antelación propia** —garantías, mascotas y
  plantas o préstamos avanzados son los candidatos— la duplicación pasa de dos a
  tres y ya no se puede llamar coincidencia. Responsable: quien abra ese módulo.
  Con dos, cambiar un número en el código sigue costando menos que una tabla que
  nadie ha pedido.

## Referencias

- [`ADR-010`](../../common/architecture/decisions/ADR-010-module-boundaries-and-activation.md):
  fronteras de paquete, activación por hogar, el gate en tres capas y la siembra
  desde estado — cuya frase sobre la reactivación **este módulo corrige**.
- [`ADR-011`](../../common/architecture/decisions/ADR-011-scheduled-checks-and-notice-delivery.md):
  el recorrido periódico y los avisos, que este módulo **sí** usa: es el primero.
- [`suppliers.md`](suppliers.md): la primera ficha de módulo terminada, y la
  referencia de qué contiene una.
- [`phase-2-roadmap.md`](../../common/product/phase-2-roadmap.md): el Hito 3 y su
  sitio en la fase.
- [`decisions.md`](../../common/product/decisions.md): las decisiones que este hito
  tomó y que la definición no preveía, incluida la del **peso y el volumen de un
  asset**, que resultó ser del core.
- [`core-model.md`](../../common/product/core-model.md): el contador, la unidad y
  el `packSize` que esta ficha declara ajenos.
- Sección 4.2 del [`README`](../../../README.md): el estado y la prioridad del
  módulo, que viven allí y solo allí.

## Historial de cambios

| Fecha | Cambio | Autor |
|---|---|---|
| 2026-08-19 | **El Hito 6 cierra los tres pendientes de esta ficha, y es la que más sacó del barrido de aislamiento**: destapó que las dos operaciones de ficha —artículo y sitio— respondían `500` ante un identificador de otro hogar donde el contrato declara `404`, porque abrían la ficha antes de comprobar nada. La **capacidad** está vuelta a medir y el aviso de esta ficha valía: `warehouse_movements` es la tabla que más crece, y la medición se partió en dos por ello. De las dos decisiones abiertas, el **reparto entre lotes** se reafirma con un disparador que ahora es una consulta sobre datos propios, y la **antelación por hogar** queda **resuelta junto con la de CMMS**: será de plataforma cuando llegue el tercer módulo con regla de fecha. | Equipo DRP |
| 2026-08-19 | **El Hito 4 trae el consumidor previsto y con él dos cierres.** `StockDepleted` **no se publicaba nunca sin mínimo declarado**, al contrario de lo que esta ficha declaraba: corregido deduciendo el cruce a cero del delta del evento, sin columna nueva. Y la decisión abierta sobre la presentación de compra queda resuelta desde el otro lado: no necesita nombre propio. | Equipo DRP |
| 2026-08-18 | Creación, **antes de la primera línea de código** del módulo. Declara las diez operaciones, las cuatro tablas, las seis invariantes, los cinco códigos de error, los seis eventos consumidos y los dos publicados con su consumidor previsto, y **la frontera contra el core sin ambigüedad**: el core mantiene un contador y Warehouse no lleva ninguno. Deja decididas las tres cosas que la definición no resolvía —qué hace un handler sin ficha, si reactivar resiembra y si un aviso se repite— y tres decisiones abiertas con su destinatario. | Equipo DRP |
