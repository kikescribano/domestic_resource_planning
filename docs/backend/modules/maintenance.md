# Módulo: Mantenimiento (CMMS)

| Campo | Valor |
|---|---|
| Estado | Vigente |
| Responsable | Equipo DRP |
| Clave | `MAINTENANCE` |
| Prefijo de ruta | `/api/v1/maintenance` |
| Última revisión | 2026-08-19 |

> **Esta ficha se escribió antes que la primera línea de código del módulo**, que
> es la regla que el [catálogo](README.md) fija. En [Proveedores](suppliers.md)
> importaba porque no había consumidor al que preguntar, en
> [Warehouse](warehouse.md) porque sí lo había y en [Compras](purchasing.md)
> porque hablaba con otros dos; aquí importa por una cuarta razón, y es la más
> incómoda de las cuatro: **la frontera que este módulo tiene que escribir no
> tiene otro lado**. Warehouse escribió la suya contra el core, que existía;
> Compras contra Warehouse, que existía. CMMS tiene que escribirla contra **el
> planificador de tareas, que no existe**, y sin nadie con quien contrastarla.
>
> Escribirla por delante volvió a pagar, y las cuatro cosas están más abajo:
> obligó a decidir **de quién es el cuándo y de quién es el quién lo hace** antes
> de que hubiera una tabla que lo diera por supuesto; obligó a contestar **qué
> crea la siembra**, que el catálogo de eventos daba por hecho sin haberlo
> decidido; destapó que **`DocumentAttached` no trae el asset donde los otros dos
> módulos aprendieron a buscarlo**; y obligó a decidir **de qué cuelga un aviso
> que se rearma**, que es la vuelta que Warehouse no tuvo que dar.

## Responsabilidad

**Qué hay que revisar de las máquinas de la casa, cada cuánto, cuándo toca la
próxima vez y qué se hizo la última.** CMMS lleva los **planes de mantenimiento
preventivo** sobre un asset `DURABLE` —la revisión anual de la caldera, el filtro
cada tres meses, la ITV cada dos años—, las **intervenciones** que se hacen sobre
esas máquinas —las preventivas que cumplen un plan y las correctivas que arreglan
una avería— y el **histórico** que queda de todas ellas.

De los planes sale su aviso por fecha —lo que toca pronto y lo que ya se pasó— y
de las intervenciones sale lo único que rearma ese aviso: **haberlo hecho**.

Es un módulo y no una parte del core porque **un hogar puede no querer nada de
esto**: quien solo quiere saber dónde está el taladro no necesita un plan de
revisiones, y el core sigue funcionando igual con CMMS apagado.

## La frontera contra el planificador de tareas

Esta sección es el motivo de que el Hito 5 pida una ficha, y **se escribe por
adelantado contra un módulo que todavía no existe**. Eso la hace más difícil que
las dos anteriores y también más necesaria: si la línea queda difusa, el
planificador nacerá duplicando el calendario de CMMS, y el día que eso pase ya
habrá dos sitios donde mirar cuándo toca revisar la caldera.

La línea es esta, y no admite matices:

> **De CMMS es el CUÁNDO. Del planificador de tareas es el QUIÉN LO HACE.**

| De CMMS | Del planificador de tareas |
|---|---|
| **Qué máquina** hay que revisar, y **cada cuánto**: una regla recurrente sobre un asset `DURABLE` | **A quién le toca**, y **qué día concreto**: algo que una persona tiene que hacer el jueves |
| **Cuándo toca la próxima**, derivado del plan y de la última intervención | El **reparto** entre miembros del hogar, los turnos y las rotaciones |
| **Quién viene de fuera** a hacerlo: el servicio técnico asignado al plan | **Quién de casa** se encarga de que se haga |
| El **aviso por fecha** de lo que toca pronto y de lo que se pasó | El **recordatorio personal** de una tarea asignada |
| El **histórico** de lo que se hizo sobre esa máquina | El histórico de lo que hizo **cada persona** |

**Un plan de mantenimiento no es una tarea, y la diferencia no es de tamaño sino
de naturaleza.** Un plan es una **regla**: «la caldera se revisa cada doce
meses». No tiene responsable, no tiene día, no se completa —se cumple una vez y
sigue vigente— y no desaparece cuando alguien la atiende. Una tarea es un
**encargo**: «Kike revisa la caldera el jueves». Tiene responsable, tiene día, se
completa una vez y deja de existir.

De esa distinción salen tres consecuencias que este módulo **cumple hoy**, y que
son lo que impide que la frontera se difumine mientras el otro lado no exista:

1. **Ningún plan lleva `assigneeId`.** No hay en ninguna de las tres tablas de
   este módulo una columna que apunte a un miembro del hogar como responsable de
   que algo se haga. La autoría —`createdBy`, `updatedBy`— apunta a quien
   escribió la fila, que es otra cosa; y una intervención guarda **quién la
   registró** y **quién vino a hacerla**, que es historia y no asignación.
2. **CMMS no tiene calendario.** No hay una tabla con una fila por ocurrencia
   futura. Lo que hay es `nextDueOn`, **una sola fecha por plan**, que es la
   próxima y se calcula al registrar la intervención. Materializar las
   ocurrencias sería exactamente construir la mitad del planificador: en cuanto
   existan filas por día, alguien las querrá asignar.
3. **CMMS no reparte nada cuando alguien deja el hogar.** `UserDeactivated` es un
   evento del catálogo cuyo consumidor previsto es el planificador —«reparte sus
   rutinas entre el resto»— y **este módulo no lo consume**, porque no tiene
   rutinas de nadie que repartir.

**Y cómo hablarán los dos el día que el planificador exista**, dicho aquí para
que ese día no haya que adivinarlo: el planificador **le preguntará a CMMS qué
toca**, no copiará su calendario. Lo que hoy responde esa pregunta ya está
publicado —`GET /api/v1/maintenance/plans?dueWithinDays=N`— y el día que haga
falta que la conversación sea por el bus, lo que CMMS publicará es «este plan ha
entrado en su ventana», con el `assetId` y el `planId` dentro. **Hoy no se
publica** porque nadie lo necesita, y publicar sin consumidor es inventarse un
contrato: ver [Eventos](#eventos-publicados).

Lo que **no** hará el planificador es escribir en las tablas de este módulo. Una
tarea cumplida no es una intervención: la intervención la registra quien atiende
la máquina, con lo que se hizo y quién vino, y eso es un gesto de este módulo.

## La frontera contra el core

Más corta, porque el core no modela nada de esto. Aun así hay que escribirla,
porque **este módulo cuelga de un asset y le añade un ciclo de vida**.

| Del core | De CMMS |
|---|---|
| Qué es la caldera: `Asset` `DURABLE`, con su nombre, su categoría y su sitio | Que **hay que revisarla cada doce meses** |
| Que la caldera está **viva o dada de baja** | Que sus planes se **cancelan** cuando se da de baja |
| El **manual**, que es un `Document` suyo | **Cuál** de los documentos de esa máquina es el manual que hay que tener a mano al revisarla |
| La documentación entera, con su fichero y su enlace | Nada: CMMS **no guarda documentos**, guarda un puntero |

**CMMS no escribe en las tablas del core y tampoco invoca ninguno de sus casos de
uso.** Es más suave que Warehouse y que Compras: aquellos movían un contador y
creaban existencias; aquí la dirección `módulo → core` se usa **solo para leer**
—qué assets `DURABLE` hay, cómo se llaman, qué documentos cuelgan de ellos—,
exactamente como la usaba Proveedores.

Registrar una intervención **no toca el asset**: ni cambia su estado, ni su
ubicación, ni su documentación. Que la caldera se haya revisado es un hecho sobre
la caldera que el core no necesita saber, y meterlo allí obligaría al core a
tener un concepto —«último mantenimiento»— que dejaría de tener sentido el día
que el hogar apague este módulo.

## Límites

### Incluido

- El **plan de mantenimiento preventivo** sobre un asset `DURABLE`: qué se
  revisa, cada cuántos meses, con cuánta antelación avisar y a quién se llama.
- La **próxima fecha prevista**, derivada del plan y de la última intervención.
- La **intervención**, en sus dos formas: la **preventiva**, que cumple un plan y
  lo rearma, y la **correctiva**, que arregla una avería y no cuelga de ningún
  plan.
- El **histórico** de intervenciones de una máquina, que no se modifica ni se
  borra.
- La **ficha de mantenimiento** de un `DURABLE`: la entrada de esa máquina en el
  módulo, con su nota y con **cuál de sus documentos es el manual**.
- Los **dos avisos por fecha** sobre la plataforma del Hito 1: lo que toca pronto
  y lo que ya se pasó.
- La lectura del **dato maestro de Proveedores** para el servicio técnico, con su
  degradación limpia cuando ese módulo está apagado.

### Fuera de alcance

- **A quién le toca hacerlo y qué día.** Es del planificador de tareas. Ver
  [La frontera contra el planificador](#la-frontera-contra-el-planificador-de-tareas).
- **Lo que cuesta una reparación.** Es de Gastos y presupuesto. Aquí no hay
  importes: ni tarifas, ni presupuestos, ni facturas —la factura es un `Document`
  del core, y este módulo como mucho apunta a ella.
- **Qué cubre una garantía y hasta cuándo.** Es de Garantías y seguros. CMMS
  guarda **qué se le hace** a una máquina, no **quién responde** de que falle.
- **El dato maestro de a quién se llama.** Es de Proveedores. CMMS **lee** y no
  crea, modifica ni retira un contacto.
- **El mantenimiento de un consumible.** No existe: un paquete de arroz no se
  revisa. CMMS **solo mira assets `DURABLE`**, que es la mitad del inventario que
  Warehouse no mira — las dos fronteras son complementarias y no se solapan en
  ninguna fila.
- **Repuestos y consumibles gastados en una intervención.** El aceite del cambio
  de filtro es una existencia del core que Warehouse cuenta; descontarlo al
  registrar la intervención sería que este módulo escribiera en el contador de
  otro. Ver [Decisiones abiertas](#decisiones-abiertas).
- **El destino de la máquina cuando ya no se arregla.** Es de Fin de vida.
- **La purga del histórico.** `maintenance_interventions` crece con lo que el
  hogar hace. Ver [Operación](#operación).

## Lenguaje de dominio

| Término | Significado |
|---|---|
| `MaintenanceItem` | La **ficha del módulo sobre un `DURABLE`**: su entrada en el radar de CMMS. Hoy guarda una nota y **cuál de sus documentos es el manual de referencia**. Se abre sola —al sembrar y al darse de alta el asset— y no la crea nadie a mano. |
| `MaintenancePlan` | Una **regla recurrente sobre una máquina**: qué se revisa, cada cuántos meses, con cuánta antelación se avisa, a quién se llama y **cuándo toca la próxima**. No tiene responsable ni día: eso es del planificador de tareas. |
| `MaintenanceIntervention` | **Lo que se hizo, y cuándo.** Preventiva si cumple un plan, correctiva si arregla algo. Es historia: se escribe y no se toca. |
| `InterventionKind` | `PREVENTIVE` cumple un plan y lo rearma; `CORRECTIVE` no cuelga de ninguno. Identificador, así que inglés y `UPPER_SNAKE_CASE`. |
| `DueStage` | En qué punto está la fecha prevista de un plan: `DUE_SOON` ha entrado en la ventana de antelación, `OVERDUE` ya se pasó. Es lo que se guarda para **no repetir el aviso**, y es la misma forma que el `ExpiryStage` de Warehouse. |
| Cancelar (`cancelledAt`) | El plan deja de vigilarse y **conserva su histórico**. Baja lógica, igual que la retirada de un `Supplier` o de una `Category` del core, y por el mismo motivo: hay filas que lo referencian. |
| Próxima fecha (`nextDueOn`) | Cuándo toca. **Se guarda y no se calcula al leer**, porque de ella cuelga el estado del aviso: un valor derivado al vuelo no tiene un momento en el que cambiar, y sin ese momento no hay dónde rearmar nada. |

## Casos de uso

| Caso de uso | Resultado observable |
|---|---|
| `ListMaintenanceItems` | Las máquinas que el módulo vigila —los `DURABLE` vivos del hogar—, con cuántos planes tienen y cuál es su próxima fecha. Filtrable por texto |
| `GetMaintenanceItem` | La ficha de una máquina: su nota, su manual, sus planes vivos y sus últimas intervenciones |
| `UpdateMaintenanceItem` | Cambia la nota y **cuál es el manual**, con la semántica de `PATCH` del contrato |
| `ListMaintenancePlans` | Los planes del hogar, paginados, filtrables por máquina, por texto, por **cuántos días faltan** y por si se incluyen los cancelados |
| `GetMaintenancePlan` | Un plan con su máquina, su servicio técnico **resuelto por el puerto** y sus últimas intervenciones |
| `CreateMaintenancePlan` | Un plan nuevo sobre un `DURABLE` vivo. Falla si ya hay uno vigente con ese nombre en esa máquina, si el intervalo no es positivo, si la antelación no cabe en el periodo o si el servicio técnico no se puede leer |
| `UpdateMaintenancePlan` | Modificación parcial. Cambiar el intervalo o la fecha prevista **rearma el aviso**, porque lo que se avisó era sobre la fecha anterior |
| `CancelMaintenancePlan` | El plan deja de vigilarse y conserva su histórico. Cancelar dos veces no es un error |
| `ListMaintenanceInterventions` | El histórico del hogar, paginado y filtrable por máquina y por plan |
| `RegisterMaintenanceIntervention` | **Lo que rearma el ciclo.** Anota lo que se hizo y, si cumple un plan, **avanza su próxima fecha** y borra el estado del aviso |
| `ListMaintenanceSuppliers` | A quién se puede llamar, leído de Proveedores por el puerto de plataforma. **Con Proveedores apagado devuelve la lista vacía**, no un error |

Once operaciones. La última es la misma decisión que Compras tomó y merece
leerse dos veces por el mismo motivo: **cuelga del prefijo de CMMS y no del de
Proveedores**, así que un hogar con Mantenimiento encendido y Proveedores apagado
recibe `200` con una lista vacía en lugar del `403` que le daría la ruta del otro
módulo. Que la degradación sea del servidor y no del cliente es lo que impide que
cada consumidor futuro tenga que acordarse de ella.

## Modelo de dominio

Dos agregados y un libro, que es la misma forma que tomó Warehouse:

- **`MaintenanceItem`** es la *ficha*: la máquina entra en el radar del módulo y
  ahí se cuelga lo que no es de ningún plan en concreto —la nota y el manual—. No
  la crea nadie: la abren la siembra y los handlers, compartiendo función.
- **`MaintenancePlan`** es la regla, y el único agregado con comportamiento: sabe
  cuándo toca, sabe si ya avisó y sabe avanzar.
- **`MaintenanceIntervention`** no es un agregado sino un **libro**: se añade y no
  se toca.

**Invariantes:**

1. **Un plan y una intervención cuelgan de un asset `DURABLE` vivo.** Ni un
   consumible —que no se revisa— ni uno dado de baja. Lo comprueba el caso de uso
   contra el estado del core, no contra una copia.
2. **El intervalo es un número entero de meses, positivo y no mayor de diez
   años.** En meses y no en días **porque `plusMonths` conserva el día del mes**:
   una revisión «anual» de 365 días se desplaza un día cada año bisiesto, y a los
   veinte años la caldera se revisa en una fecha que nadie eligió.
3. **La antelación del aviso no es negativa y cabe dentro del periodo.** Una
   antelación más larga que el propio intervalo deja el plan permanentemente
   «a punto de tocar», que es tanto como no avisar nunca.
4. **No hay dos planes vivos con el mismo nombre en la misma máquina**, sin
   distinguir mayúsculas ni acentos. Dos revisiones anuales de la misma caldera
   son un duplicado que se ve a la primera.
5. **Una intervención no es del futuro.** Registrar una de la semana que viene
   adelantaría la próxima fecha sobre algo que no ha pasado.
6. **Un plan cancelado no admite intervenciones nuevas** ni se modifica. Lo que
   ya no se vigila no se rearma.

Las seis se comprueban en el caso de uso **y**, las que la base de datos puede
expresar, en el esquema: es la regla del core y no una duplicación —la
comprobación da el mensaje que el contrato declara y la restricción cierra la
carrera entre dos peticiones simultáneas, que ninguna comprobación previa puede
cerrar.

### De qué cuelga un aviso que se rearma

Warehouse fijó la regla que este módulo hereda —**un aviso se levanta cuando la
condición empieza a ser cierta y no vuelve a levantarse mientras siga siéndolo**,
con el estado en las tablas del módulo y no consultando `household_notices`— y
este módulo le da la vuelta que allí no hacía falta dar: **una revisión es
periódica**. Hecha la de este año, la del año que viene tiene que volver a
armarse. En Warehouse el rearme era reponer por encima del mínimo, que es un
hecho que ocurre solo; aquí es **registrar la intervención**, que es un gesto de
una persona.

**El aviso cuelga de la próxima fecha prevista, no del plan.** Es la decisión, y
se guarda tal cual: el plan lleva `notifiedStage` —en qué fase avisó— **y
`notifiedFor`, la fecha a la que esa fase se refiere**. Un aviso se calla solo
cuando `notifiedFor` coincide con `nextDueOn`; en cuanto la fecha avanza, la
marca deja de valer y el plan vuelve a poder avisar sin que nadie tenga que
acordarse de limpiarla.

La alternativa era **una sola marca en el plan** —«ya avisé»— que hubiera que
borrar al registrar la intervención. Se descarta porque **hay más de un camino
que cambia la fecha**: además de la intervención, cambiar el intervalo o
corregir a mano la próxima fecha con el `PATCH`. Con una marca suelta, cada uno
de esos caminos tiene que acordarse de limpiarla, y el que se olvide deja un plan
que **no vuelve a avisar nunca** — el peor síntoma posible, porque no se parece a
nada y solo se descubre el día que la caldera lleva dos años sin revisar.

**Y dos fases, no una**, igual que un lote de Warehouse: `DUE_SOON` cuando entra
en la ventana de antelación —«toca el mes que viene»— y `OVERDUE` el día que la
fecha pasa —«se pasó»—. La segunda no es una repetición sino una noticia nueva, y
no darla dejaría al hogar con un aviso de hace tres semanas como única
advertencia. Una fase que **avanza** sí es noticia; la misma fase repetida, no.

## Cómo lee CMMS el dato maestro de Proveedores

**CMMS es el segundo consumidor del puerto de plataforma, y eso es su prueba de
verdad.** `MasterDataDirectory` se diseñó en el Hito 4 con un solo consumidor
delante, así que la pregunta que este módulo tiene que contestar no es cómo leer
—eso está decidido y no se reabre— sino **si lo que entrega basta cuando el
segundo llega**.

**Basta, y el puerto no se ensancha.** La entrada trae identificador, nombre y un
`detail` que Proveedores rellena con **el identificador de su categoría de
servicio**. Lo que CMMS podría querer y no tiene es **filtrar por categoría**
para ofrecer solo servicios técnicos, y la decisión es no pedirlo:

- **Filtrar sería la funcionalidad equivocada.** Un plan de mantenimiento
  doméstico llama al fontanero, al electricista, al servicio técnico de la
  caldera, al taller del coche, al cerrajero y al de la plaga de la cocina: de
  las catorce categorías cerradas de Proveedores, **casi todas son servicios
  técnicos**. Recortar la lista escondería justo al contacto que hace falta, y el
  hogar no tendría forma de saber por qué no aparece.
- **Lo que sí hace falta es distinguirlos de un vistazo**, y para eso el `detail`
  ya llega: el selector **agrupa por categoría** y el rótulo en castellano lo pone
  el cliente, que es exactamente lo que la ficha de Proveedores decidió al mandar
  el identificador y no el rótulo.
- **Y ensanchar tiene un precio conocido.** Un `search(moduleKey, category, …)`
  obligaría a plataforma a aprender que un dato maestro tiene categorías y cuáles
  son válidas, que es el residuo exacto que la mudanza de `ErrorCode` dejó
  anotado para vigilar en el Hito 2. La cuarta regla de ArchUnit seguiría verde
  —no es una dependencia de clase— y la frontera estaría igualmente peor.

**El disparador de revisarlo queda escrito**: el día que un consumidor necesite
del dato maestro ajeno algo que **no se pueda decidir mirando la entrada
completa** —un filtro que el dueño resuelva en su consulta porque traerlo entero
no cabe—, ensanchar el puerto deja de ser vocabulario prestado y pasa a ser
necesidad. Con catorce categorías y una lista doméstica, ese día no ha llegado.

**Y lo que este módulo sí comprueba por primera vez** es la garantía que
Proveedores declaró **por adelantado y para este caso**: «un contacto retirado
sigue siendo legible por su identificador, porque un plan de CMMS que apunte a
quien ya no se llama tiene que poder decir a quién apuntaba». Nadie la había
ejercitado —Compras copia el nombre al crear la compra, así que nunca vuelve a
preguntar—. Aquí **el plan guarda solo el `supplierId` y resuelve el nombre al
leer**, que es lo que obliga a que `find` funcione sobre un contacto retirado y a
que `search` no lo ofrezca. Las dos mitades tienen prueba.

**Por qué el plan no copia el nombre y la intervención sí**, que es la asimetría
que más fácil parece un descuido:

| | Guarda | Por qué |
|---|---|---|
| `MaintenancePlan` | Solo `supplierId` | **Un plan es una regla viva.** Si el fontanero cambia de nombre, el plan tiene que decir el nombre de hoy: a ese señor es a quien hay que llamar el mes que viene |
| `MaintenanceIntervention` | `supplierId` **y** `supplierName` | **Una intervención es historia.** Que el 3 de marzo vino aquel servicio técnico siguió siendo cierto aunque después se retire o el hogar apague Proveedores |

Es la misma regla que la ADR-011 da para que un aviso lleve su texto dentro, y la
misma que Compras aplicó a `purchases`. Lo que este módulo añade es el otro lado:
**no todo lo que apunta a un dato maestro es historia**, y copiar el nombre en lo
que no lo es produce una segunda versión que envejece.

## Contratos publicados

### API

Bajo `/api/v1/maintenance`, en [`openapi.yaml`](../../../openapi.yaml), con
`operationId` en las once operaciones. Todo lo que cuelga de ese prefijo responde
`403 MODULE_INACTIVE` mientras el hogar no lo tenga encendido, y eso no lo sabe
ningún controlador del módulo: lo pone el filtro de plataforma sobre el prefijo
que declara `MaintenanceModule`.

Códigos de error propios, enumerados en el esquema `Error` del contrato y
declarados en **`com.drp.platform.error`**, que es donde el Hito 2 dejó el
enumerado y no en el core:

`MAINTENANCE_ASSET_NOT_ELIGIBLE`, `MAINTENANCE_PLAN_DUPLICATE`,
`MAINTENANCE_PLAN_CANCELLED`, `MAINTENANCE_LEAD_EXCEEDS_INTERVAL` y
`MAINTENANCE_SUPPLIER_UNKNOWN`.

El último dice menos de lo que parece, y a propósito, igual que el
`PURCHASE_SUPPLIER_UNKNOWN` de Compras: se lanza tanto si el contacto no existe
como si Proveedores está apagado, y no distingue los dos casos. Distinguirlos
obligaría a CMMS a saber qué módulos tiene el hogar encendidos, que es justo lo
que el puerto evita.

### Eventos consumidos

Tres del core, por `ModuleEventHandler` —que resuelve las tres garantías del bus,
comprueba la activación **para el hogar de ese evento** y abre la transacción
`REQUIRES_NEW`.

| Evento | Qué hace CMMS con él |
|---|---|
| `AssetCreated` | Si es un `DURABLE`: **abre su ficha de mantenimiento**, sin ningún plan |
| `AssetDeactivated` | **Cancela los planes vivos** de esa máquina. La ficha y el histórico se quedan |
| `DocumentAttached` | Si el documento es un **manual** y cuelga de un asset: abre la ficha si falta y lo deja como manual de referencia de esa máquina |

**Un solo handler y no tres**, por lo mismo que escribieron Warehouse y Compras:
tres clases serían tres suscriptores recibiendo los quince eventos del catálogo
cada uno para descartar catorce, tres conjuntos de idempotencia y tres
transacciones abiertas por evento.

**Y ninguna rama comprueba si el módulo ya sembró**: cada una abre lo que
necesita, compartiendo función con la siembra. Es la regla que el Hito 3 dejó
escrita, y aquí tiene un caso que allí no se daba —un `DocumentAttached` puede
llegar sobre una máquina cuya ficha no existe todavía— resuelto sin ninguna rama
especial.

> **`DocumentAttached` trae una trampa que los otros dos módulos no tuvieron: su
> agregado es el DOCUMENTO, no el asset.**
>
> Warehouse y Compras leen `aggregateId` como «la cosa que ha cambiado» —un
> asset, un artículo— y les vale. Aquí `aggregateId` es el identificador del
> **documento**, y el asset viaja en el `payload` como `assetId` **anulable**,
> porque un documento puede colgar de un artículo en vez de un asset (ver 4.1.3).
>
> Un handler que copiara el patrón de los dos hitos anteriores enlazaría el manual
> a la ficha de un asset que no existe —el identificador del documento— **y no
> fallaría**: no encontraría nada, y el módulo se quedaría sin manuales sin que
> nada lo dijera. Por eso la rama lee `payload["assetId"]`, **descarta el nulo** y
> se apoya además en que abrir la ficha es una inserción que solo prospera sobre
> un `DURABLE` vivo: un identificador que no sea de una máquina no escribe nada.

**Lo que este módulo NO consume, y por qué:**

- **`AssetMoved` y `AssetHierarchyChanged`.** Que la caldera cambie de sitio no
  cambia cada cuánto se revisa. El sitio se lee del core al pintar la ficha.
- **`AssetQuantityChanged`.** Es de consumibles, y CMMS solo mira `DURABLE`.
- **`ArticleCreated`.** Un plan cuelga de **una máquina concreta** y no de un
  modelo: dos calderas idénticas se revisan cada una por su cuenta, y la que se
  cambió el año pasado no hereda la fecha de la otra.
- **`UserDeactivated`.** Es del planificador de tareas. Ver
  [la frontera](#la-frontera-contra-el-planificador-de-tareas).
- **`HouseholdCreated`.** Un hogar recién creado no tiene assets, así que no hay
  nada que abrir. Lo que pone al día un módulo recién encendido es su siembra.

### Eventos publicados

| Evento | Cuándo se publica | Consumidores conocidos | Versión |
|---|---|---|---|
| — | — | — | — |

**CMMS no publica ningún evento hoy, y no es un olvido.** Es el caso de
Proveedores y de Compras, no el de Warehouse: el criterio del catálogo (README
5.2.3) es que **alguien lo necesite**, y hoy no lo necesita nadie. Los
consumidores imaginables de «se ha intervenido esta máquina» o «este plan ha
entrado en su ventana» son **Fin de vida** —cuántas reparaciones lleva antes de
decidir que no se arregla más—, **Garantías y seguros** —una intervención dentro
de la cobertura— y **Gastos y presupuesto** —lo que costó—, y ninguno de los tres
entra en la Fase 2. El cuarto es el **planificador de tareas**, que es quien
convertiría «toca revisar» en el encargo del jueves, y tampoco.

Publicar hoy sería inventarse el contrato de cuatro conversaciones que no han
empezado —¿lleva el importe?, ¿lleva el servicio técnico?, ¿una por intervención
o una por plan?, ¿se publica al entrar en la ventana o el día que se pasa?— y
esas preguntas solo las contesta quien vaya a leerlas. Un evento publicado se
retira peor de lo que se añade.

**Lo que un consumidor futuro sí puede dar por hecho**, declarado aquí para que
ninguno de los cuatro tenga que adivinarlo:

- **El `assetId` es del core y sigue siendo legible aunque CMMS se apague.** Un
  consumidor que guarde ese identificador no queda colgando de este módulo.
- **Una intervención no se modifica ni se borra.** Es un libro, así que un
  consumidor que la copie no tiene que vigilar cambios.
- **CMMS no dice lo que hay que hacer, dice lo que sabe.** «Este plan toca el mes
  que viene» no significa «que lo haga alguien»: quién y qué día es del
  planificador de tareas.
- **Un consumidor tiene que degradar limpiamente si CMMS está apagado**, y no es
  cortesía: ninguna clase de `com.drp.module.maintenance` es importable desde
  otro módulo, y ArchUnit falla la construcción si alguien lo intenta.

## Dependencias consumidas

| De dónde | Qué | Para qué |
|---|---|---|
| `com.drp.platform.module` | `ModuleDescriptor`, `ModuleSeeder`, `ModuleEventHandler`, `ModuleActivation` | Existir en el catálogo, sembrarse y reaccionar al bus |
| `com.drp.platform.directory` | `MasterData`, `MasterDataEntry` | Leer el dato maestro de Proveedores sin importar ninguna clase suya |
| `com.drp.platform.event` | `DomainEvent` | Consumir los tres del core |
| `com.drp.platform.schedule` | `ScheduledCheck`, `CheckOwner` | Su comprobación periódica |
| `com.drp.platform.notice` | `NoticeDraft` | Sus dos avisos |
| `com.drp.platform.page` | `Page`, `Pagination`, `PageResponse` | La misma paginación que todo el contrato |
| `com.drp.platform.error` | `ErrorCode`, `BusinessRuleViolation`, `ValidationFailure`, `ResourceNotFound` | Sus cinco reglas, con la forma única del contrato |
| `com.drp.platform.tenant` | `TenantContext` | El hogar, en la siembra y en el handler |
| `com.drp.core.application.port` | `SessionClaims` | La autoría, que apunta a la **pertenencia** |
| `com.drp.core.application.usecase` | `Patch` | La semántica de `PATCH` del contrato |
| `com.drp.core.adapter.http` | `JsonPatch` | Distinguir «no menciones esto» de «ponlo a nulo» |
| `com.drp.core` (por SQL, sin importar nada) | `assets`, `documents` | Leer el estado: qué máquinas hay, cómo se llaman y qué documentación tienen |

**Ninguna dependencia hacia `com.drp.module.suppliers`**, y no por disciplina:
ArchUnit falla la construcción si alguien lo intenta. Lo que se lee de Proveedores
pasa por un puerto de plataforma.

**Y ninguna hacia el planificador de tareas**, que no existe — pero conviene
decirlo, porque el día que exista la tentación será un `import` y la respuesta ya
está escrita arriba: le pregunta a CMMS qué toca, y CMMS no sabe que existe.

## Datos y transacciones

**Tres tablas, las tres en `public` y las tres con `household_id`, RLS y
`FORCE`.** En `public` y no en un esquema propio, por lo mismo que las tres fichas
anteriores: el esquema aparte es la trampa del módulo de prueba del Hito 0, que lo
usa justo para **no** falsear el recuento de tablas del modelo.

| Tabla | Qué guarda |
|---|---|
| `maintenance_items` | La ficha de una máquina: su nota y cuál de sus documentos es el manual |
| `maintenance_plans` | La regla recurrente: qué, cada cuánto, con cuánta antelación, a quién se llama y cuándo toca |
| `maintenance_interventions` | El histórico. **La segunda tabla del modelo que crece sin techo**, después del cuaderno de Warehouse |

Los nombres son del módulo, llevan su prefijo y quedan declarados aquí para que
otro no los tome.

**Migración `V13`**, que deja el esquema completo desde una base vacía, políticas
incluidas. El recuento del modelo sube de **veinticinco a veintiocho** y la lista
de tablas **sin** política no se toca: sigue teniendo cinco.

### Las claves ajenas, y las tres formas que toman

**La regla es `ON DELETE CASCADE`**, que es lo que el Hito 2 decidió y por el
mismo motivo: con el `RESTRICT` que rige por omisión, una fila de un módulo
convertiría una operación del core en un `500` **del core causado por un módulo**.

**Y hay dos excepciones, cada una por su lado.**

**1. El servicio técnico va sin clave ajena, y en el histórico va con el nombre
dentro.** Ni `maintenance_plans.supplier_id` ni
`maintenance_interventions.supplier_id` tienen clave ajena hacia `suppliers`, por
los dos motivos que el Hito 4 escribió y que aquí valen igual: **una intervención
es historia** —que aquel día vino aquel servicio técnico siguió siendo cierto
aunque después se retire o el hogar apague Proveedores— y **una clave ajena hacia
la tabla de otro módulo es una dependencia de esquema que ArchUnit no puede ver**;
un `JOIN` desde aquí incumpliría la frontera igual que un `import`, solo que sin
nada que lo delate. La diferencia entre las dos tablas es que la intervención
**copia el nombre** y el plan **lo resuelve al leer**, por lo dicho en
[la sección del puerto](#cómo-lee-cmms-el-dato-maestro-de-proveedores).

**2. El manual se suelta, no arrastra: `ON DELETE SET NULL`.** Es la única clave
ajena hacia el core del modelo que no cae en ninguna de las dos formas
anteriores, y merece su párrafo. Un `Document` **sí se borra de verdad** —el core
tiene `DELETE /documents/{id}`—, así que `RESTRICT` convertiría ese borrado en un
`500` y **`CASCADE` borraría la ficha entera de la máquina** por haber borrado un
adjunto, y con ella su nota. Lo que se cae es el puntero, no la máquina: el
manual desaparece y la caldera sigue teniendo su ficha, sus planes y su
histórico.

`asset_id` sí lleva la cascada en las tres tablas, por lo mismo que el `asset_id`
del cuaderno de Warehouse: **un asset no se borra nunca —se da de baja—**, así que
la cascada solo se dispara al borrar el hogar entero. Y `plan_id` en el histórico
también, porque un plan se cancela y no se borra.

### Los tres índices, y qué garantiza cada uno

| Índice | Sobre | Qué garantiza |
|---|---|---|
| Único | `(household_id, asset_id)` en `maintenance_items` | **Una ficha por máquina.** Es la idempotencia de la siembra y de dos de los tres handlers |
| Único parcial | `(household_id, asset_id, lower(immutable_unaccent(name)))` en `maintenance_plans`, **solo entre los vivos** | **Ningún nombre repetido en la misma máquina**, sin que un plan cancelado bloquee para siempre volver a crearlo |
| De consulta | `(household_id, next_due_on)` en `maintenance_plans`, solo entre los vivos | Lo que la comprobación nocturna pregunta cada noche |

### Siembra

`MaintenanceSeeder` **recorre los assets `DURABLE` vivos del hogar y abre la ficha
de cada uno. No crea ni un plan**, y esa es la decisión que esta ficha tenía que
tomar.

**La pregunta que la definición no contestaba.** El catálogo de eventos (README
5.2.3) dice que CMMS «genera un plan de mantenimiento por defecto» al darse de
alta un asset, y esa frase se escribió en la Fase 0 como ejemplo de para qué
sirve el bus, no como una decisión de producto. Al construirlo resulta que no se
sostiene: **por defecto ¿de qué?** Una caldera pide revisión anual y una silla no
pide nada.

- **Crear un plan por cada `DURABLE`** inunda el hogar el día que enciende el
  módulo —una casa normal tiene decenas de cosas duraderas— y llena la bandeja de
  avisos de revisiones que nadie pidió. El módulo se apagaría en una semana.
- **Crear un plan solo para algunos** exige saber qué clase de máquina es cada
  cosa, y **el core no lo modela**: su `Category` es un catálogo por hogar cuyos
  nombres son datos en castellano que cada casa edita. Una lista de categorías
  «que piden mantenimiento» sería conocimiento de producto inventado aquí y
  contradicho por el primer hogar que llame «Aparatos» a su categoría de
  electrodomésticos.
- **No crear nada y dejar la siembra vacía**, como Proveedores, deja al handler de
  `AssetCreated` sin trabajo —que es justo el ejemplo con el que el README explica
  el bus desde la Fase 0— y a la pantalla del módulo recién encendido sin nada que
  enseñar.

**Así que lo que se crea es la ficha, que es una por máquina y no una por plan.**
Encender CMMS deja al hogar viendo sus máquinas —las que ya tenía— con «sin planes
todavía», y crear el primero es un clic desde ahí. Es la misma forma que Warehouse
eligió al abrir fichas de artículo **sin mínimo**: el módulo prepara el sitio y la
regla la pone el hogar, que es quien sabe si su caldera es de gas o su silla es
una silla.

**Y esto corrige la frase del catálogo de eventos, que se actualiza con su
motivo**: `AssetCreated` no genera un plan, **abre la ficha desde la que nace el
primer plan**.

### Y su idempotencia, que es más difícil que las dos anteriores

Warehouse la apoyó en un índice único por artículo y en uno solo `OPENING` por
existencia; Compras, en una sola línea viva por artículo. Aquí **«un plan por
asset» es falso**: un `DURABLE` puede tener legítimamente varios —revisión anual y
cambio de filtro cada tres meses— así que no hay ninguna clave natural que impida
duplicarlos, y sin ella resembrar los duplicaría.

La respuesta tiene dos mitades y **las dos son índices, no comprobaciones
previas**, que es la lección que los dos hitos anteriores dejaron escrita
—comprobar y después insertar deja una ventana entre las dos cosas por la que
caben dos entregas simultáneas del mismo evento—:

1. **La siembra no crea planes**, así que no hay nada que duplicar. Lo que crea es
   la ficha, y de esa sí hay exactamente una por máquina: índice único sobre
   `(household_id, asset_id)` con `ON CONFLICT DO NOTHING`.
2. **Y los planes llevan igualmente su índice** —un nombre por máquina entre los
   vivos— aunque hoy solo los cree una persona. No es ceremonia: es lo que hace
   que el día que algo automático quiera crear un plan —una plantilla, una
   importación, el planificador— no pueda duplicarlo, y que la carrera entre dos
   pulsaciones del mismo botón la cierre la base de datos y no la suerte.

Lo que la reactivación **no** reconstruye es el histórico del periodo apagado: las
intervenciones de esos meses no ocurrieron para este módulo y no se inventan. Y
tampoco los planes que el hogar hubiera creado y borrado, porque los planes no se
borran.

**Desactivar conserva.** Apagar el módulo no borra ni una fila: las tres tablas se
quedan como estaban y reactivarlo las devuelve tal cual —más las fichas que la
siembra complete.

### Transacciones

Tres formas, y ninguna nueva:

- **Los casos de uso**: la suya, con el gestor consciente de inquilino que fija
  `app.household_id`. Nada especial, y **este módulo no tiene la excepción de
  Warehouse y Compras**: no invoca ningún caso de uso del core, así que no hay
  ninguna operación que tenga que renunciar a su transacción para que el
  `AFTER_COMMIT` de otro dispare.
- **El handler de eventos**: `REQUIRES_NEW`, y **la abre `ModuleEventHandler`**
  desde el Hito 0, así que el módulo no tiene que acordarse.
- **La comprobación periódica**: la que `DailySweep` abre por ella, dentro del
  `runAs` del hogar. El aviso y la marca de haberlo dado se escriben en la misma,
  que es lo que impide que un fallo deje un plan marcado sin aviso.

## Seguridad

- **Aislamiento en dos capas, igual que el core.** Todo caso de uso filtra por el
  `householdId` del token y nunca por uno del cliente, y las tres tablas llevan
  política de RLS con `FORCE`.
- **Y la capa que el core no tiene**: el gate. Con el módulo apagado, todo lo que
  cuelga del prefijo responde `403 MODULE_INACTIVE` sin llegar al controlador, y
  **ninguno de los tres handlers escribe una fila** para un hogar que no lo tenga
  activo.
- **El hogar del handler sale del sobre del evento y no del contexto**, que es lo
  que lo sitúa con certeza cuando lo que despierta el evento no es una petición.
- **Ningún dato personal propio.** CMMS guarda fechas, periodos y texto sobre
  máquinas. El nombre del servicio técnico que copia en una intervención es un
  dato de terceros que ya vive en Proveedores, con RLS, y aquí llega **solo el
  nombre**: ni teléfono, ni correo, ni dirección — el puerto no los entrega.
- **Un plan no puede apuntar a un asset de otro hogar**, y no solo porque el caso
  de uso lo compruebe: la clave ajena es compuesta `(household_id, asset_id)`,
  como en todo el modelo.
- **Cualquier miembro del hogar puede leer y escribir.** Quien puede ver la
  caldera puede apuntar que se ha revisado. Encender y apagar el módulo sigue
  siendo solo de administrador, y eso lo corta plataforma.

## Verificación

| Nivel | Qué se comprueba |
|---|---|
| Dominio | Las seis invariantes y el avance de la fecha, sin base de datos |
| Aplicación (integración) | Las once operaciones contra PostgreSQL real, con usuario **sujeto a RLS** |
| Esquema | Que las tres tablas nuevas llevan `household_id`, RLS, `FORCE` y política, y que el recuento del modelo sube de veinticinco a veintiocho **sin tocar** la lista de tablas sin política |
| Event bus | Que un hogar con el módulo **apagado** no ve ni una fila escrita por ninguna de las tres ramas, y que el de al lado sí. Y que **el manual llega a la máquina y no al documento**, que es la trampa de `DocumentAttached` |
| El puerto de Proveedores | Que un contacto **retirado sigue siendo legible** por el plan que lo apunta y **deja de ofrecerse** en el selector: las dos mitades de la garantía que Proveedores declaró por adelantado y que nadie había ejercitado |
| Proveedores apagado | Que `ListMaintenanceSuppliers` responde `200` con la lista vacía, que un plan con servicio técnico se rechaza con `MAINTENANCE_SUPPLIER_UNKNOWN` y que **un plan sin servicio técnico se crea igual** |
| Siembra | Que enciende sobre los `DURABLE` que ya había, que **no crea ni un plan**, y que **apagar y volver a encender no duplica ni una ficha ni un plan** |
| Avisos | Que la comprobación produce su aviso **una vez** y no cada noche, que **la fase que avanza sí es noticia nueva**, que **registrar la intervención lo vuelve a armar** y que el resumen diario se lee del **Mailpit de verdad** |
| Adaptador HTTP | Las dos mitades del gate sobre la ruta real: apagado `403`, encendido `200` |
| Recorrido vertical | Añadido a la batería existente y no en una suite paralela: encender el módulo, ver la máquina que ya había, crear su plan, ver que toca, registrar la intervención y ver la fecha avanzada, con axe en los dos modos, foco, teclado y reflujo de 320 px a ultrawide |

**Sin estrenar contextos de Spring.** Hay treinta y ocho clases de prueba con
`@SpringBootTest`, y cada combinación nueva de propiedades estrena contexto y con
él un pool de cinco conexiones que no se suelta mientras siga en caché — eso ya
tumbó tres pruebas de otros hitos en la Fase 1. Los Hitos 3 y 4 tenían handlers y
comprobaciones periódicas que probar y añadieron **cero**: ejercitaron los
handlers por la API de verdad e invocaron el recorrido a mano con `DailySweep`
inyectado, que además es el mismo método que llama el `@Scheduled`. Aquí se hace
lo mismo.

**El barrido de aislamiento** —autenticado como hogar A, ninguna operación
devuelve ni modifica datos del hogar B— lo tiene asignado el **Hito 6**, que es
donde el roadmap lo puso para toda la fase. **Se deja allí a propósito y se dice
aquí para que no se dé por supuesto**: las once operaciones de este módulo entran
en ese barrido, junto con las siete de Proveedores, las diez de Warehouse y las
diez de Compras, no en este hito.

## Operación

- **Una comprobación periódica**, `MaintenanceDueCheck`, con
  `CheckOwner.Module("MAINTENANCE")`, así que el recorrido diario **solo entra
  donde el módulo está encendido**. Produce dos avisos distintos —
  `MAINTENANCE_DUE_SOON` y `MAINTENANCE_OVERDUE`— sobre la misma pregunta: qué
  planes han entrado en su ventana y cuáles se han pasado.
- **Sus avisos no se repiten mientras la condición siga siendo cierta, y se
  rearman al registrar la intervención.** Es la vuelta que Warehouse no tuvo que
  dar, y está razonada arriba: el estado cuelga de **la fecha prevista** y no del
  plan.
- **`maintenance_interventions` crece con lo que el hogar hace y no con lo que
  tiene**, igual que `warehouse_movements`, `shopping_list_items` y `purchases`.
  Es el **quinto candidato** a la purga que la ADR-011 dejó anotada sin hito para
  `household_notices`, y su sitio natural sigue siendo una comprobación más del
  mismo recorrido. Crece despacio —una casa registra unas pocas intervenciones al
  año, no varias al día— así que de los cinco es el menos urgente; se dice aquí
  para que quien escriba esa purga no lo descubra después. **Volver a medir la
  capacidad es del Hito 6** y aquí no se mide.
- **Recuperación.** Este módulo **sí** deriva parte de su estado del core —qué
  máquinas hay—, así que sí existe el caso «se ha quedado desincronizado»: un
  hogar que dio de alta la caldera con CMMS apagado. La salida es la siembra, que
  es idempotente y se ejecuta al reactivar. Lo que no recupera es lo que no
  ocurrió para este módulo: las intervenciones de esos meses.

## Decisiones abiertas

- **Si un plan debe poder colgar de una ubicación y no solo de un asset.** Hoy
  no: la revisión de la instalación eléctrica del piso no tiene una máquina a la
  que apuntar, y el hogar la resuelve dando de alta un `DURABLE` que la
  represente. Se anota porque es el primer caso que va a aparecer, y porque la
  salida —un destino polimórfico como el de `SupplierLink`— ya está escrita en
  otro módulo y sería barata. Responsable: quien abra el Hito 6, que es quien
  mira los datos reales de la fase.
- **Si una intervención debe descontar los repuestos que gastó.** Hoy no: el
  aceite del cambio de filtro es una existencia del core que Warehouse cuenta, y
  descontarlo desde aquí sería que este módulo escribiera en el contador de otro
  —o, peor, que dependiera de que Warehouse esté encendido para poder registrar
  una intervención—. La salida existe y es la del core: ajustar la cantidad. El
  disparador está escrito: **el día que Gastos exista**, «qué gastó esta
  reparación» deja de ser una pregunta de material y pasa a ser una de dinero, y
  entonces se decide de quién es.
- **Si la antelación del aviso necesita un valor por hogar.** Hoy la cadena es
  plan → **quince días**, que está en el código. Es la misma decisión abierta que
  dejó Warehouse con su antelación de caducidad, y por eso conviene que las dos se
  resuelvan a la vez: un ajuste por hogar sería una tabla de configuración, y dos
  tablas de configuración de dos módulos para lo mismo es la señal de que lo que
  falta es una de plataforma.
- **Si un plan cancelado se puede reactivar.** Hoy no, igual que un `Supplier`
  retirado y por el mismo motivo: nadie lo ha pedido y la salida —volver a
  crearlo— existe. Se anota porque el índice único **entre los vivos** hace que
  esa salida funcione sin chocar, que es lo mismo que hizo posible la de
  Proveedores.

## Referencias

- [`ADR-010`](../../common/architecture/decisions/ADR-010-module-boundaries-and-activation.md):
  fronteras de paquete, activación por hogar, el gate en tres capas y la siembra
  desde estado.
- [`ADR-011`](../../common/architecture/decisions/ADR-011-scheduled-checks-and-notice-delivery.md):
  el recorrido periódico y los avisos. **Este módulo es el ejemplo con el que esa
  ADR se justifica** —«la revisión en CMMS»— y el primero cuyo aviso tiene que
  volver a armarse.
- [`suppliers.md`](suppliers.md): el dato maestro que este módulo lee, y quien
  declaró **por adelantado y para este caso** que un contacto retirado sigue
  siendo legible por su identificador.
- [`warehouse.md`](warehouse.md): quien fijó **el patrón de avisos** que este
  módulo hereda, y la otra mitad del inventario — allí los consumibles, aquí lo
  duradero.
- [`purchasing.md`](purchasing.md): quien estrenó **el puerto por el que se lee el
  dato maestro de otro módulo**, y quien dejó anotadas las dos trampas de escribir
  en el core — que este módulo no necesita, porque no escribe.
- [`phase-2-roadmap.md`](../../common/product/phase-2-roadmap.md): el Hito 5 y su
  sitio en la fase.
- [`decisions.md`](../../common/product/decisions.md): las decisiones que este
  hito tomó y que la definición no preveía.
- [`core-model.md`](../../common/product/core-model.md): el asset `DURABLE` y su
  documentación, que esta ficha declara ajenos.
- Sección 4.2 del [`README`](../../../README.md): el estado y la prioridad del
  módulo, que viven allí y solo allí.

## Historial de cambios

| Fecha | Cambio | Autor |
|---|---|---|
| 2026-08-19 | Creación, **antes de la primera línea de código** del módulo. Declara las once operaciones, las tres tablas, las seis invariantes, los cinco códigos de error, los tres eventos consumidos —con la trampa de `DocumentAttached`, cuyo agregado es el documento y no el asset— y que **no publica ninguno**, con sus cuatro consumidores imaginables nombrados. Escribe **la frontera contra el planificador de tareas sin ambigüedad y por adelantado**: de CMMS es el cuándo, del planificador el quién lo hace. Y deja decididas las tres cosas que la definición no resolvía: **qué crea la siembra** —la ficha de cada máquina y ningún plan—, **cómo se rearma un aviso periódico** —cuelga de la próxima fecha y no del plan— y **si el puerto de dato maestro se ensancha** —no—. | Equipo DRP |
