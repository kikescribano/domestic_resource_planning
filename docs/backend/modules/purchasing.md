# Módulo: Compras y lista de la compra

| Campo | Valor |
|---|---|
| Estado | Vigente |
| Responsable | Equipo DRP |
| Clave | `PURCHASING` |
| Prefijo de ruta | `/api/v1/purchasing` |
| Última revisión | 2026-08-19 |

> **Esta ficha se escribió antes que la primera línea de código del módulo**, que
> es la regla que el [catálogo](README.md) fija. En [Proveedores](suppliers.md)
> importaba porque no había consumidor al que preguntar y en
> [Warehouse](warehouse.md) porque sí lo había; aquí importa por una tercera
> razón: **Compras es el primer módulo que habla con otros dos** —lee el dato
> maestro de Proveedores y escucha lo que publica Warehouse— y **el primero que
> escribe en el core**. Tres conversaciones, y ninguna de ellas puede resolverse
> con un `import`.
>
> Escribirla por delante volvió a pagar, y las cuatro cosas están más abajo:
> obligó a **elegir cómo se lee el dato maestro de otro módulo** —la pregunta que
> Proveedores dejó abierta con destinatario en este hito—, destapó que
> **`StockDepleted` no se publica cuando un artículo no tiene mínimo declarado**,
> lo que dejaba sin contenido la pregunta de qué pasa al llegar a cero; obligó a
> decidir **qué siembra un módulo cuya materia prima es de otro**; y dejó claro
> que **cerrar una compra no puede envolverse en una transacción propia**, que es
> la trampa que Warehouse midió y anotó para quien viniera detrás.

## Responsabilidad

**Qué falta, qué hay que reponer, qué está pedido y qué ha entrado en casa.**
Compras lleva la lista de la compra del hogar —lo que hace falta, venga de donde
venga—, agrupa lo que se va a comprar en una **compra** con su sitio y su fecha,
y al recibirla **cierra el ciclo dando entrada a los consumibles en el core**.

Es un módulo y no una parte del core porque **un hogar puede no querer nada de
esto**: quien solo quiere saber dónde está el taladro no necesita una lista de la
compra, y el core sigue funcionando igual con Compras apagado.

## La frontera contra Warehouse

Esta sección es el motivo de que el Hito 4 pida una ficha, y se escribe sin
ambigüedad por lo mismo que Warehouse escribió la suya contra el core: es la que
más fácil se difumina con una tabla de más.

**Warehouse detecta la falta. Compras decide qué se compra y cuándo.**

| De Warehouse | De Compras |
|---|---|
| **Que algo ha bajado del mínimo**, y cuál es ese mínimo | **Que eso hay que comprarlo**, o que no |
| **Que algo se ha acabado** | **Cuánto** se compra, y en qué presentación |
| El histórico de movimientos y los lotes | **Dónde** se compra y **cuándo** |
| La caducidad y su aviso | Que la compra ha llegado, y **darle entrada** |

**Compras no mira existencias y no sabe qué hay en la despensa.** No hay en sus
tablas ninguna columna que guarde una cantidad disponible, ni un mínimo, ni una
caducidad: cuando Warehouse encuentra una falta la publica, y lo que llega aquí
es **un hecho sobre un artículo del core**, no una consulta al módulo de al lado.

Y lo contrario también: **Warehouse no sabe que Compras existe**. Sus dos eventos
—`StockBelowMinimum` y `StockDepleted`— dicen lo que Warehouse sabe, no lo que
hay que hacer, y su ficha lo declara así desde el Hito 3.

**Los dos lados funcionan solos, y eso es lo que este hito retira como riesgo.**

- **Con Warehouse apagado**, sus dos eventos **no llegan** —no llegan vacíos, no
  llegan—, así que la lista de la compra deja de llenarse sola. Sigue habiendo
  lista, y se añade a mano: es la operación `AddShoppingListItem`, que existe por
  esto y no como comodidad.
- **Con Compras apagado**, Warehouse publica igual y no lo escucha nadie. Su
  cuaderno, sus avisos y sus mínimos siguen exactamente como estaban.
- **Con los dos encendidos**, el ciclo se cierra: falta algo, entra en la lista,
  se compra, se recibe, el core suma sobre la existencia de esa ubicación y
  **Warehouse asienta esa entrada en su cuaderno** porque el core publica al
  hacerlo. Ninguno de los dos módulos sabe del otro en ninguna línea de código.

**La única cosa de Warehouse que Compras nombra es el `articleId` que viaja en el
evento**, y ese identificador es del core: su ficha lo declara legible aunque
Warehouse se apague, precisamente para que un consumidor no quede colgando.

## La frontera contra el core

Más corta que la anterior, pero hay que escribirla porque **este es el primer
módulo que escribe en el core** y eso es un escalón por encima de lo que hizo
Warehouse.

| Del core | De Compras |
|---|---|
| `Asset.quantity`: cuánto hay | Cuánto **hace falta** |
| `Article`: qué es una cosa, su `unit` y su `packSize` | Qué hay que **comprar** de eso |
| Dar de alta o sumar una existencia (`RegisterConsumableIntake`) | **Pedirlo**, cuando la compra llega |

**Compras no escribe en las tablas del core.** Cuando una compra recibida tiene
que dar entrada a un consumible lo hace **invocando el caso de uso del core**,
que es la dirección `módulo → core` que la ADR-010 permite. Warehouse ya la usaba
para mover un contador que existía; aquí se usa para **crear existencias**, que
es la forma más fuerte de esa dirección que la fase va a ver.

**Y la presentación de compra no se guarda.** «Pack de 6» o «garrafa de 5 l» se
**muestran**, componiéndolos con el `packSize` y la `unit` que el core ya tiene;
no hay ninguna columna con ese texto. Ver
[Decisiones abiertas](#decisiones-abiertas).

## Límites

### Incluido

- La **lista de la compra**: qué hace falta, cuánto, por qué entró y en qué
  estado está.
- Entrar en la lista **solo**, cuando Warehouse dice que algo bajó del mínimo o
  se acabó, y **a mano** cuando lo dice una persona.
- La **compra**: dónde se compra, cuándo, y qué líneas se lleva.
- El **cierre de la compra**, que da entrada a los consumibles en el core.
- La lectura del **dato maestro de Proveedores** para saber dónde se compra, y su
  degradación limpia cuando ese módulo está apagado.

### Fuera de alcance

- **Detectar la falta.** Es de Warehouse. Ver
  [La frontera contra Warehouse](#la-frontera-contra-warehouse).
- **Lo que cuesta.** Es de Gastos y presupuesto, que no entra en la Fase 2. Aquí
  no hay importes: ni precio por unidad, ni total de la compra, ni presupuesto.
  Es además el motivo de que este módulo **no publique ningún evento** —ver
  [Eventos](#eventos).
- **El dato maestro de a quién se compra.** Es de Proveedores. Compras **lee** y
  no crea, modifica ni retira un contacto.
- **El contador de cantidad y el histórico de movimientos.** Del core y de
  Warehouse respectivamente. Recibir una compra produce **un** apunte, y lo
  escribe el handler de Warehouse a partir del evento del core, no este módulo.
- **La conversión entre unidad de compra y unidad de consumo.** Ya está resuelta
  y es del core: `Article.packSize`.
- **Avisar por una fecha.** Compras no tiene ninguna regla de fecha, así que **no
  declara ninguna `ScheduledCheck`** —ver [Operación](#operación).
- **Recetas y consumo planificado.** Es de Recetas y menú semanal, que sería el
  otro productor natural de líneas de lista y no entra en la Fase 2.

## Lenguaje de dominio

| Término | Significado |
|---|---|
| `ShoppingListItem` | Una línea de la lista: qué hace falta. Apunta a un `Article` del core **o** lleva un nombre suelto, nunca las dos cosas y nunca ninguna. |
| `ItemOrigin` | Por qué entró la línea: `MANUAL` la puso una persona, `LOW_STOCK` la puso Warehouse al bajar del mínimo, `DEPLETED` al acabarse. Es un identificador y va en inglés. |
| `ItemStatus` | En qué punto está: `NEEDED` hace falta, `IN_PURCHASE` va en una compra abierta, `BOUGHT` ya entró en casa, `DISMISSED` se descartó sin comprar. |
| `Purchase` | Un acto de comprar: dónde, cuándo y qué líneas se lleva. Tiene estado propio —`OPEN`, `RECEIVED`, `CANCELLED`— porque una compra se prepara antes de hacerse. |
| Recibir (`ReceivePurchase`) | Cerrar la compra: cada línea con artículo **da entrada en el core** y pasa a `BOUGHT`. Es lo único de este módulo que escribe fuera de él. |
| Presentación de compra | «Pack de 6», «garrafa de 5 l». **Se compone y no se guarda**, a partir del `packSize` y la `unit` del artículo. |

## Casos de uso

| Caso de uso | Resultado observable |
|---|---|
| `ListShoppingList` | La lista del hogar, paginada, filtrable por estado y por texto. Con el nombre y la unidad del artículo resueltos del core |
| `AddShoppingListItem` | Una línea nueva, a mano. Falla si ya hay una viva para ese artículo. **Es la operación que hace que la lista siga sirviendo con Warehouse apagado** |
| `UpdateShoppingListItem` | Modificación parcial con la semántica de `PATCH` del contrato: la cantidad, la nota, y **asignarle un artículo** a una línea de texto suelto |
| `DismissShoppingListItem` | Se descarta sin comprar. Descartar dos veces no es un error |
| `ListPurchases` | Las compras del hogar, de la más reciente a la más antigua, filtrables por estado |
| `GetPurchase` | Una compra con sus líneas, y el nombre de dónde se compró **tal y como era ese día** |
| `CreatePurchase` | Abre una compra con las líneas que se lleva, que pasan a `IN_PURCHASE`. Falla sin líneas, o si el proveedor no se puede leer |
| `ReceivePurchase` | **Cierra el ciclo.** Cada línea con artículo da entrada en el core, que suma sobre la existencia de esa ubicación; la línea pasa a `BOUGHT` y la compra a `RECEIVED` |
| `CancelPurchase` | La compra se anula y sus líneas **vuelven a la lista** en `NEEDED`: lo que hacía falta sigue haciendo falta |
| `ListPurchasingSuppliers` | Dónde se puede comprar, leído de Proveedores por el puerto de plataforma. **Con Proveedores apagado devuelve la lista vacía**, no un error |

Diez operaciones. La última es la que hace visible la decisión de este hito y
merece leerse dos veces: **cuelga del prefijo de Compras y no del de
Proveedores**, así que un hogar con Compras encendido y Proveedores apagado
recibe `200` con una lista vacía en lugar del `403` que le daría la ruta del otro
módulo. Que la degradación sea del servidor y no del cliente es lo que impide que
cada consumidor futuro tenga que acordarse de ella.

## Modelo de dominio

Dos agregados, y el segundo manda sobre las líneas del primero mientras las
tenga:

- **`ShoppingListItem`** es la unidad de trabajo: existe sola, entra en una
  compra y vuelve a salir si esa compra se anula.
- **`Purchase`** agrupa líneas y tiene el ciclo de vida que este módulo existe
  para llevar. Guarda **el nombre del proveedor tal y como era ese día**, por lo
  mismo que un asiento de Warehouse guarda el nombre del sitio.

**Invariantes:**

1. **Una línea apunta a un artículo o lleva un nombre suelto**, nunca las dos
   cosas y nunca ninguna. El nombre suelto es para lo que todavía no está en el
   catálogo —«pilas AA»— y es real: una lista de la compra de verdad las tiene.
2. **No hay dos líneas vivas para el mismo artículo.** Es lo que hace que el
   mismo `StockBelowMinimum` entregado dos veces no deje dos líneas.
3. **La cantidad, si la hay, es estrictamente positiva.** Puede no haberla:
   «hace falta arroz» es una línea completa.
4. **Una compra no se crea vacía**, y solo admite líneas en `NEEDED`.
5. **Una compra solo se recibe o se anula una vez.** Recibir dos veces no puede
   dar entrada dos veces, y es la invariante que más cuidado pide porque el
   cierre **no cabe en una sola transacción** —ver
   [Datos y transacciones](#datos-y-transacciones).
6. **Solo una línea con artículo da entrada al core.** Una de texto suelto se
   compra y ahí acaba: no se inventa un artículo con una categoría y una unidad
   que nadie ha elegido.

Las seis se comprueban en el caso de uso **y**, las que la base de datos puede
expresar, en el esquema: es la regla del core y no una duplicación —la
comprobación da el mensaje que el contrato declara y la restricción cierra la
carrera entre dos peticiones simultáneas, que ninguna comprobación previa puede
cerrar.

## Cómo lee Compras el dato maestro de Proveedores

**Es la decisión arquitectónica de este hito**, la pregunta que la ficha de
Proveedores dejó abierta con destinatario en el Hito 4, y la única cuya
alternativa habría cambiado el esquema de este módulo.

**Se elige un puerto en plataforma**, que es lo que la ADR-010 señala en su
condición de revisión —«si el caso es legítimo, lo que hace falta es un puerto en
plataforma, no una excepción»—. La alternativa, **eventos que Compras materialice
en su propio lado**, se descarta, y no por comodidad:

- **Una copia alimentada por eventos no se puede sembrar.** La entrega del bus es
  at-least-once y **en memoria**: un hogar que encienda Compras hoy no vio el
  `SupplierCreated` de hace un mes, y la ADR-010 resuelve ese caso mandando
  sembrar **desde el estado**. Pero el estado de Proveedores está en sus tablas, y
  leerlas desde aquí es exactamente la dependencia que la ficha de Proveedores se
  compromete a no tener. La copia nacería vacía **para siempre**, sin nada que la
  llene salvo que alguien dé de alta un fontanero nuevo. Es el mismo defecto que
  Warehouse describe como «mentir en silencio», y aquí no tiene salida.
- **Y obligaría a Proveedores a publicar tres eventos** —alta, cambio y retirada—
  para que el consumidor pudiera mantener la copia al día. Su ficha declara que no
  publica ninguno y que publicar sin necesidad es inventarse un contrato; esta
  necesidad no es de Proveedores sino de la copia, que es un artefacto de la
  solución y no del problema.

**El precio del puerto está aceptado y escrito**: Compras depende de que
Proveedores esté encendido **en el instante de la consulta**. Que eso sea
aceptable es lo que hace legítima la decisión: lo que se lee de Proveedores es
**dónde se compra**, que es un adorno de la compra y no su razón de ser. Una
compra sin proveedor es una compra perfectamente válida —«fui al mercado»— así
que la degradación no recorta ninguna funcionalidad. Si lo que se leyera fuese
imprescindible, esta decisión sería la contraria.

**Y el segundo precio, que es el que hay que vigilar: plataforma nombraría un
concepto de un módulo.** Es el residuo exacto que la mudanza de `ErrorCode` dejó
anotado en el Hito 2, y aquí se evita: el puerto **no se llama `SupplierDirectory`
ni habla de proveedores**. Se llama `MasterDataDirectory`, declara *el mecanismo*
—«un módulo publica un dato maestro que otro puede consultar por su clave»— y
sigue la misma forma que `ModuleSeeder` y `ScheduledCheck` ya tenían: una
interfaz de plataforma con un `moduleKey` dentro, implementada por quien lo sabe.
Plataforma no aprende la palabra «proveedor»; aprende que hay datos maestros y
que se piden por la clave del módulo que los posee. **La cuarta regla de ArchUnit
—plataforma no conoce a sus usuarios— sigue siendo cierta en la letra y en el
espíritu**, y la lista de excepciones de la tercera sigue teniendo un solo
nombre.

**La degradación la pone plataforma y no el consumidor.** La fachada `MasterData`
comprueba la activación del módulo dueño antes de preguntarle nada, así que un
directorio de un módulo apagado responde vacío sin que el consumidor tenga una
sola rama para ello. Compras **no sabe si Proveedores está apagado o si ese
proveedor no existe**, y no tiene por qué saberlo: las dos cosas responden igual,
que es la misma regla con la que el core responde a un identificador ajeno.

## Contratos publicados

### API

Bajo `/api/v1/purchasing`, en [`openapi.yaml`](../../../openapi.yaml), con
`operationId` en las diez operaciones. Todo lo que cuelga de ese prefijo responde
`403 MODULE_INACTIVE` mientras el hogar no lo tenga encendido, y eso no lo sabe
ningún controlador del módulo: lo pone el filtro de plataforma sobre el prefijo
que declara `PurchasingModule`.

Códigos de error propios, enumerados en el esquema `Error` del contrato y
declarados en **`com.drp.platform.error`**, que es donde el Hito 2 dejó el
enumerado y no en el core:

`SHOPPING_ITEM_DUPLICATE`, `SHOPPING_ITEM_NOT_PENDING`, `PURCHASE_EMPTY`,
`PURCHASE_NOT_OPEN` y `PURCHASE_SUPPLIER_UNKNOWN`.

El último merece una nota, porque **dice menos de lo que parece a propósito**: se
lanza tanto si el proveedor no existe como si Proveedores está apagado, y no
distingue los dos casos. Distinguirlos obligaría a Compras a saber qué módulos
tiene el hogar encendidos, que es justo lo que el puerto evita.

### Eventos consumidos

Dos, los que Warehouse publica, por `ModuleEventHandler` —que resuelve las tres
garantías del bus, comprueba la activación **para el hogar de ese evento** y abre
la transacción `REQUIRES_NEW`.

| Evento | Qué hace Compras con él |
|---|---|
| `StockBelowMinimum` | Abre una línea `NEEDED` con origen `LOW_STOCK` para ese artículo, si no había ya una viva |
| `StockDepleted` | Lo mismo con origen `DEPLETED`, y **sube a `DEPLETED` el origen de la línea que ya hubiera**: acabarse es una noticia distinta de estar bajo mínimos, y la lista tiene que poder ordenarse por ella |

**Un solo handler y no dos**, por lo mismo que Warehouse escribió: dos clases
serían dos suscriptores recibiendo los quince eventos del catálogo cada uno para
descartar catorce, y dos conjuntos de idempotencia.

**La idempotencia no descansa en una comprobación previa sino en un índice único
parcial** —una sola línea viva por artículo—, que es la lección que Warehouse dejó
escrita: comprobar y después insertar deja una ventana entre las dos cosas por la
que caben dos entregas simultáneas del mismo evento.

### Eventos

| Evento | Cuándo se publica | Consumidores conocidos | Versión |
|---|---|---|---|
| — | — | — | — |

**Compras no publica ningún evento hoy, y no es un olvido.** Es el caso de
Proveedores y no el de Warehouse: el criterio del catálogo (README 5.2.3) es que
**alguien lo necesite**, y el consumidor natural de «se ha comprado esto» es
**Gastos y presupuesto**, que no entra en la Fase 2. Publicar `PurchaseReceived`
hoy sería inventarse el contrato de una conversación que no ha empezado —¿lleva
importe?, ¿lleva las líneas?, ¿una por compra o una por línea?— y esas preguntas
solo las puede contestar quien vaya a leerlas. Un evento publicado se retira peor
de lo que se añade.

**Lo que sí ocurre al recibir una compra, y conviene no confundirlo con un evento
de este módulo:** el core publica los suyos —`AssetCreated` o
`AssetQuantityChanged`, y `ArticleCreated` si crea el artículo— porque la entrada
de consumibles es suya. **Cerrar una compra escribe un asiento en el cuaderno de
Warehouse** si Warehouse está encendido, y eso es el ciclo cerrándose sin que
ninguno de los dos módulos sepa del otro.

## Dependencias consumidas

| De dónde | Qué | Para qué |
|---|---|---|
| `com.drp.platform.module` | `ModuleDescriptor`, `ModuleSeeder`, `ModuleEventHandler`, `ModuleActivation` | Existir en el catálogo, sembrarse y reaccionar al bus |
| `com.drp.platform.directory` | `MasterData`, `MasterDataEntry` | Leer el dato maestro de Proveedores sin importar ninguna clase suya |
| `com.drp.platform.event` | `DomainEvent` | Consumir los dos de Warehouse |
| `com.drp.platform.page` | `Page`, `Pagination`, `PageResponse` | La misma paginación que todo el contrato |
| `com.drp.platform.error` | `ErrorCode`, `BusinessRuleViolation`, `ValidationFailure`, `ResourceNotFound` | Sus cinco reglas, con la forma única del contrato |
| `com.drp.platform.tenant` | `TenantContext` | El hogar, en la siembra y en el handler |
| `com.drp.core.application.port` | `SessionClaims` | La autoría, que apunta a la **pertenencia** |
| `com.drp.core.application.usecase` | `RegisterConsumableIntake`, `IntakeCommand`, `Patch` | **Cerrar el ciclo**, y la semántica de `PATCH` |
| `com.drp.core.adapter.http` | `JsonPatch` | Distinguir «no menciones esto» de «ponlo a nulo» |
| `com.drp.core.domain.inventory` | `AssetLocation` | Decir dónde entra lo que se recibe |
| `com.drp.core` (por SQL, sin importar nada) | `assets`, `articles`, `locations` | Leer el estado: nombre, unidad, envase y dónde vive ya un artículo |

**Ninguna dependencia hacia `com.drp.module.suppliers` ni hacia
`com.drp.module.warehouse`**, y no por disciplina: ArchUnit falla la construcción
si alguien lo intenta. Lo que llega de Warehouse llega por el bus y lo que se lee
de Proveedores, por un puerto de plataforma.

## Datos y transacciones

**Dos tablas, las dos en `public` y las dos con `household_id`, RLS y `FORCE`.**
En `public` y no en un esquema propio, por lo mismo que las dos fichas
anteriores: el esquema aparte es la trampa del módulo de prueba del Hito 0, que
lo usa precisamente para **no** falsear el recuento de tablas del modelo.

| Tabla | Qué guarda |
|---|---|
| `shopping_list_items` | La lista de la compra: qué hace falta, cuánto, por qué y en qué estado |
| `purchases` | La compra: dónde, cuándo, su estado y **el nombre del proveedor de aquel día** |

Los nombres son del módulo y quedan declarados aquí para que otro no los tome.

**Migración `V12`**, que deja el esquema completo desde una base vacía, políticas
incluidas. El recuento del modelo sube de **veintitrés a veinticinco** y la lista
de tablas **sin** política no se toca: sigue teniendo cinco.

**Las claves ajenas hacia el core van con `ON DELETE CASCADE`**, que es lo que el
Hito 2 decidió y por el mismo motivo: con el `RESTRICT` que rige por omisión, una
fila de un módulo convertiría una operación del core en un `500`.

**Y hay dos excepciones, las dos del mismo tipo que la del cuaderno de
Warehouse.** `purchases` guarda `supplier_id` **sin clave ajena** y con el nombre
del proveedor dentro, por dos razones que apuntan al mismo sitio:

1. **Una compra es historia.** Que el 3 de marzo se compró arroz en aquel sitio
   siguió siendo cierto aunque el sitio se retire o el hogar apague Proveedores.
   Es el mismo argumento que la ADR-011 da para que un aviso lleve su texto
   dentro.
2. **Y una clave ajena hacia `suppliers` sería una dependencia de esquema entre
   dos módulos**, que es la que ArchUnit no puede ver. Un `JOIN` desde aquí a la
   tabla de Proveedores incumpliría la frontera igual que un `import`, solo que
   sin nada que lo delate. El nombre se copia al crear la compra y no se vuelve a
   mirar.

**Transacciones.** Tres formas, y la tercera es la que este módulo tiene que
explicar:

- **Los casos de uso de lectura y los de la lista**: la suya, con el gestor
  consciente de inquilino que fija `app.household_id`. Nada especial.
- **El handler de eventos**: `REQUIRES_NEW`, y **la abre `ModuleEventHandler`**
  desde el Hito 0, así que el módulo no tiene que acordarse.
- **`ReceivePurchase`, que no abre ninguna.** Es la trampa que Warehouse midió y
  dejó anotada: la transacción de `RegisterConsumableIntake` tiene que ser **la de
  fuera** para que al cerrarse dispare el `AFTER_COMMIT` que escribe el asiento en
  el cuaderno de Warehouse. Envolverla aquí dejaría ese asiento para después de la
  respuesta —o, peor, dentro de una transacción que el handler no puede ver—. Y
  tiene la otra mitad, que es la que cuesta un rato de diagnóstico si no se sabe:
  **sin transacción no hay `app.household_id`**, así que la política de RLS no ve
  hogar y toda consulta devuelve **cero filas** diciendo «eso no existe» sobre
  algo que sí existe. De ahí que las lecturas y las escrituras de esta operación
  vayan por un bean aparte con su propia transacción, como el `StockReader` de
  Warehouse.

**Recibir dos veces no puede dar entrada dos veces, y no cabe en una
transacción.** El cierre da entrada línea a línea invocando al core, y cada
invocación es su propia transacción; no hay ninguna que las abarque. Así que la
idempotencia va **por línea y con un `UPDATE` condicional**: la línea se reclama
con un `UPDATE … WHERE status = 'IN_PURCHASE'` que devuelve una fila o ninguna
—una comparación y un cambio en una sola sentencia, sin ventana entre el «no
está» y el «ya está»— y **solo si la reclamó** se da la entrada.

El orden es **reclamar y después dar entrada**, y es una elección con su
alternativa descartada. Al revés —dar entrada y después marcar— dos peticiones
simultáneas darían las dos su entrada antes de que ninguna marcase, que es
exactamente el caso que hay que impedir. El precio del orden elegido es que un
fallo de infraestructura entre las dos cosas deja una línea marcada sin entrada:
**visible en la pantalla y corregible a mano**, mientras que una entrada
duplicada es un número que nadie puede distinguir de uno real. Es la regla de la
ADR-011 —«hay que elegir cuál falla mejor»— resuelta en el sentido contrario, y a
propósito: allí repetir era barato porque un correo repetido se nota; aquí
repetir crea existencias fantasma en la despensa.

**Siembra.** `PurchasingSeeder` **recorre los consumibles del core que están a
cero** y abre una línea `NEEDED` con origen `DEPLETED` para cada artículo.

Es la respuesta a la pregunta que esta ficha tenía que contestar, y la mitad
interesante es **lo que no siembra**: lo tentador era sembrar con lo que está bajo
mínimos, y eso **es un dato de Warehouse** —el mínimo vive en
`warehouse_articles`— del que ninguna clase es importable. Lo que sí se puede leer
es **el contador del core**, que es de quien es, y «cuántos artículos tiene el
hogar a cero ahora mismo» es una pregunta que se le hace a `assets` sin que
Warehouse intervenga.

Así que la siembra aplica hacia atrás la misma regla que el módulo aplica hacia
adelante —lo que llega a cero entra en la lista— sobre el estado que puede leer.
Un hogar que enciende Compras con la despensa vacía **no nace ciego**, y no hace
falta que Warehouse esté encendido para ello.

**Y es idempotente por construcción**, que es lo que la reactivación exige desde
el Hito 3: la línea se abre con `ON CONFLICT DO NOTHING` sobre el índice único
parcial de una línea viva por artículo. Resembrar completa lo que falte y no
duplica ni una línea. Lo que la reactivación **no** reconstruye es lo que se
compró durante el periodo apagado: eso no ocurrió para este módulo y no se
inventa.

**Desactivar conserva.** Apagar el módulo no borra ni una fila: las dos tablas se
quedan como estaban y reactivarlo las devuelve tal cual —más lo que la siembra
complete.

## Seguridad

- **Aislamiento en dos capas, igual que el core.** Todo caso de uso filtra por el
  `householdId` del token y nunca por uno del cliente, y las dos tablas llevan
  política de RLS con `FORCE`.
- **Y la capa que el core no tiene**: el gate. Con el módulo apagado, todo lo que
  cuelga del prefijo responde `403 MODULE_INACTIVE` sin llegar al controlador, y
  **el handler no escribe ni una fila** para un hogar que no lo tenga activo.
- **El hogar del handler sale del sobre del evento y no del contexto**, que es lo
  que lo sitúa con certeza cuando lo que despierta el evento no es una petición.
- **Escribir en el core no salta ninguna comprobación del core.**
  `RegisterConsumableIntake` se invoca **con la sesión de quien recibe la
  compra**, así que sus validaciones —que el propietario sea miembro del hogar,
  que la ubicación sirva de contenedor— corren igual que si la llamada viniera de
  la pantalla de inventario. Un módulo no es un canal privilegiado.
- **Ningún dato personal propio.** Compras guarda artículos, cantidades y fechas.
  El nombre del proveedor que copia es un dato de terceros que ya vive en
  Proveedores, con RLS, y aquí llega **solo el nombre**: ni teléfono, ni correo,
  ni dirección.
- **Cualquier miembro del hogar puede leer y escribir.** Quien puede ver la
  despensa puede apuntar que hace falta arroz. Encender y apagar el módulo sigue
  siendo solo de administrador, y eso lo corta plataforma.

## Verificación

| Nivel | Qué se comprueba |
|---|---|
| Dominio | Las seis invariantes, sin base de datos |
| Aplicación (integración) | Las diez operaciones contra PostgreSQL real, con usuario **sujeto a RLS** |
| Esquema | Que las dos tablas nuevas llevan `household_id`, RLS, `FORCE` y política, y que el recuento del modelo sube de veintitrés a veinticinco **sin tocar** la lista de tablas sin política |
| Event bus | **Las dos mitades de la prueba que este hito existe para hacer**: Warehouse apagado y Compras encendido —los eventos no llegan, la lista no se llena sola y se puede añadir a mano—, y Compras apagado con Warehouse encendido —Warehouse publica, escribe su cuaderno y nadie escucha—. Con un tercer hogar con **los dos encendidos** como comparación, que es lo que distingue «el gate funciona» de «el handler está roto» |
| Ciclo cerrado | Que recibir una compra llama a la entrada de consumibles del core y **suma** sobre la existencia de esa ubicación, y que con Warehouse encendido eso **aparece en su cuaderno** |
| Idempotencia | Que el mismo evento entregado dos veces no deja dos líneas, y que recibir dos veces la misma compra no da entrada dos veces |
| Proveedores apagado | Que `ListPurchasingSuppliers` responde `200` con la lista vacía y que una compra con proveedor se rechaza con `PURCHASE_SUPPLIER_UNKNOWN`, mientras una compra sin proveedor se crea igual |
| Siembra | Que enciende sobre los consumibles a cero que ya había, y que **apagar y volver a encender no duplica ni una línea** |
| Adaptador HTTP | Las dos mitades del gate sobre la ruta real: apagado `403`, encendido `200` |
| Recorrido vertical | Añadido a la batería existente y no en una suite paralela: encender el módulo, ver lo que la siembra dejó, añadir a mano, abrir una compra, recibirla y ver la existencia en el inventario, con axe en los dos modos, foco, teclado y reflujo de 320 px a ultrawide |

**Sin estrenar contextos de Spring.** Hay treinta y siete clases de prueba con
`@SpringBootTest` y solo cuatro con propiedades propias; cada combinación estrena
contexto y con él un pool de conexiones que no se suelta mientras siga en caché.
El Hito 3 tenía seis handlers que probar y añadió **cero**, ejercitándolos por la
API de verdad, que además es como corren en producción. Aquí se hace lo mismo.

**El barrido de aislamiento** —autenticado como hogar A, ninguna operación
devuelve ni modifica datos del hogar B— lo tuvo asignado el **Hito 6**, que es
donde el roadmap lo puso para toda la fase.

> **Cerrado el 2026-08-19, en el Hito 6**, y aquí también destapó algo:
> `POST /purchasing/purchases/{id}/receipt` **ignoraba en silencio** una
> `lines[].itemId` que no fuera de esa compra. Respondía `200` y descartaba lo que
> el cliente había dicho —cuánto entró, de quién es, dónde va—, que es la única
> forma en que esta operación puede hacer lo contrario de lo que le piden sin
> decirlo. Ahora responde `404`, que es lo que el contrato ya declaraba. No era
> una fuga —del hogar de al lado no se movía nada— y por eso hacía falta el
> barrido para verlo: una fuga se nota mirando el resultado y esto solo se nota
> mirando el código.
>
> Las dos negativas con `409` de este módulo quedaron anotadas con su motivo:
> `SHOPPING_ITEM_NOT_PENDING` dice lo mismo para una línea comprada que para una
> que no existe, y `PURCHASE_SUPPLIER_UNKNOWN` es **la degradación escrita** del
> puerto de dato maestro —distinguir «no existe» de «Proveedores está apagado»
> delataría al vecino—.

## Operación

- **Nada periódico.** Compras no declara ninguna `ScheduledCheck`, así que el
  recorrido diario no entra aquí ni con el módulo encendido. No tiene ninguna
  regla de fecha: lo que hay que comprar no caduca, y avisar por correo de que
  hay doce cosas en la lista es exactamente el ruido que la ADR-011 evita al
  decidir que un resumen vacío no se manda. Quien avisa de que algo se acaba es
  Warehouse, y ya lo hace.
- **Ningún aviso, ningún correo, ninguna métrica propia.**
- **Crecimiento.** `shopping_list_items` y `purchases` crecen con lo que el hogar
  **hace** y no con lo que tiene, igual que `warehouse_movements`: una casa que
  hace la compra semanal escribe filas para siempre. Son el **tercer y cuarto
  candidato** a la purga que la ADR-011 dejó anotada sin hito para
  `household_notices` y a la que el Hito 3 añadió el cuaderno de Warehouse. Su
  sitio natural sigue siendo una comprobación más del mismo recorrido. **Medido el
  2026-08-19, en el Hito 6**: `shopping_list_items` es la segunda que más crece,
  justo detrás del cuaderno de Warehouse, y `purchases` va muy por detrás —una
  compra a la semana—. El criterio de retención de las cinco y su disparador están
  en la [medición de capacidad](../operations/capacity-measurements.md).
- **Recuperación.** Este módulo deriva parte de su estado de otro, así que sí
  existe el caso «se ha quedado desincronizado» —un hogar que tuvo Compras apagado
  mientras se le acababa el arroz—. La salida es la siembra, que es idempotente y
  se ejecuta al reactivar; lo que recupera es **lo que está a cero ahora**, no lo
  que estuvo bajo mínimos entonces.

## Decisiones abiertas

- **Si la presentación de compra necesita nombre propio.** Es la media pregunta
  heredada de la Fase 1, y **este hito la contesta que no**: «pack de 6» y
  «garrafa de 5 l» **se componen** con el `packSize` y la `unit` que el core ya
  guarda, y no se almacenan en ninguna columna. Un texto libre sería una segunda
  fuente de verdad que puede contradecir al `packSize` —alguien escribe «pack de
  6» sobre un artículo cuyo envase trae 4— y no aporta nada que el par
  `packSize`/`unit` no diga. El disparador está escrito: **el día que un artículo
  necesite dos presentaciones a la vez** —«la garrafa en el mayorista y la botella
  en el súper»— deja de ser una etiqueta y pasa a ser una tabla de *artículo ×
  proveedor*, que nace aquí y probablemente con precio dentro, o sea del brazo de
  Gastos y presupuesto. Responsable: quien abra ese módulo.
- **Si una línea comprada debe poder deshacerse.** Hoy no: recibir una compra da
  entrada en el core y la salida —ajustar la cantidad— existe y es del core. Se
  anota porque es lo primero que va a pedir quien reciba una compra con una línea
  de más.
- **Si la lista debe ordenarse por urgencia además de por origen.** Hoy `DEPLETED`
  manda sobre `LOW_STOCK` y sobre `MANUAL`, que es orden suficiente para una lista
  de la compra doméstica. Un campo de prioridad por línea es lo que pedirá quien
  tenga cincuenta.
- ~~**Si `StockDepleted` debería llegar también sin mínimo declarado en las demás
  reglas de Warehouse.**~~ **Resuelta el 2026-08-19, al cerrar la fase: no, y las
  demás reglas están bien como están — pero la premisa con la que se anotó era
  medio falsa y se corrige aquí.**

  Lo que este hito corrigió fue el **evento**: `StockDepleted` colgaba de la rama
  de bajo mínimos y por tanto no se publicaba nunca para un artículo sin mínimo
  declarado, que son casi todos. Al anotar la pregunta se dejó escrito que «el
  aviso de caducidad y el de mínimo siguen exigiendo ficha con mínimo». **De las
  dos afirmaciones solo una es cierta**, y comprobarlo en el código —que es lo que
  el cierre de fase hizo en lugar de repetir la frase— da la respuesta entera:

  - **El aviso de caducidad no exige ninguna ficha ni ningún mínimo.** Su consulta
    entra por `warehouse_lots` y engancha la ficha del artículo y la del sitio con
    `LEFT JOIN`, solo para resolver la antelación; sin ellas cae en los siete días
    por omisión. Lo que ese aviso necesita es **un lote con fecha**, que es otra
    cosa y no tiene nada que ver con el mínimo.
  - **El aviso de mínimo sí lo exige, y no puede ser de otra manera**: «estar bajo
    mínimos» sin un mínimo declarado no significa nada, porque no hay contra qué
    comparar. No es una omisión que arreglar sino la definición de la regla.

  Así que **nunca hubo una asimetría que corregir**. La diferencia real es otra, y
  conviene dejarla escrita porque es la que explica por qué el evento sí era un
  defecto: **cero es un umbral absoluto y no hace falta declararlo**; «poco» es un
  umbral relativo y sí. `StockDepleted` colgaba del segundo cuando pertenecía al
  primero.

## Referencias

- [`ADR-010`](../../common/architecture/decisions/ADR-010-module-boundaries-and-activation.md):
  fronteras de paquete, activación por hogar, el gate en tres capas y la siembra
  desde estado — cuya **condición de revisión sobre el puerto en plataforma**
  este módulo estrena.
- [`ADR-011`](../../common/architecture/decisions/ADR-011-scheduled-checks-and-notice-delivery.md):
  el recorrido periódico y los avisos, que este módulo **no** usa, y la regla de
  «elegir cuál falla mejor», que aquí se resuelve al revés.
- [`warehouse.md`](warehouse.md): **el otro lado de la frontera**, y quien declaró
  por adelantado lo que este módulo puede consumir y dar por hecho.
- [`suppliers.md`](suppliers.md): el dato maestro que este módulo lee, y quien
  dejó abierta con destinatario aquí la pregunta de cómo leerlo.
- [`phase-2-roadmap.md`](../../common/product/phase-2-roadmap.md): el Hito 4 y su
  sitio en la fase, incluido **el punto de corte** que este hito declara.
- [`decisions.md`](../../common/product/decisions.md): las decisiones que este
  hito tomó y que la definición no preveía.
- [`core-model.md`](../../common/product/core-model.md): el contador, la unidad y
  el `packSize` que esta ficha declara ajenos.
- Sección 4.2 del [`README`](../../../README.md): el estado y la prioridad del
  módulo, que viven allí y solo allí.

## Historial de cambios

| Fecha | Cambio | Autor |
|---|---|---|
| 2026-08-19 | **El Hito 6 cierra los pendientes de esta ficha.** El barrido de aislamiento destapó que recibir una compra **ignoraba en silencio** una línea que no fuera suya —respondía `200` y descartaba lo que el cliente había dicho—, y ahora responde `404`. La **capacidad** está vuelta a medir: `shopping_list_items` es la segunda que más crece. Y la decisión abierta sobre `StockDepleted` queda **resuelta que no**, corrigiendo de paso la premisa con la que se anotó: el aviso de caducidad **no exige mínimo** —engancha las fichas con `LEFT JOIN` y lo que necesita es un lote con fecha—, así que nunca hubo asimetría. La diferencia real es que **cero es un umbral absoluto y «poco» es relativo**. | Equipo DRP |
| 2026-08-19 | Creación, **antes de la primera línea de código** del módulo. Declara las diez operaciones, las dos tablas, las seis invariantes, los cinco códigos de error, los dos eventos consumidos y que **no publica ninguno**, y **la frontera contra Warehouse sin ambigüedad**: Warehouse detecta la falta y Compras decide qué se compra y cuándo. Resuelve la pregunta heredada de cómo se lee el dato maestro de otro módulo —**un puerto en plataforma que no nombra al módulo**— y las dos que este hito tenía asignadas: qué pasa al llegar a cero y si la presentación de compra necesita nombre. | Equipo DRP |
