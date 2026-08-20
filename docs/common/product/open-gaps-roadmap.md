# Roadmap del cierre de huecos

| Campo | Valor |
|---|---|
| Estado | Cerrado — **completado el 2026-08-20**, con sus siete hitos |
| Responsable | Equipo DRP |
| Ámbito | Los cuatro huecos que las Fases 1 y 2 dejaron abiertos a propósito, más el quinto que apareció ejecutándolos |
| Última revisión | 2026-08-20 |

> **El bloque está completo.** Este documento se conserva como historia de cómo
> se hizo, igual que [`roadmap.md`](roadmap.md) y
> [`phase-2-roadmap.md`](phase-2-roadmap.md); lo que sigue —la Fase 3— no está
> planificado, y planificarlo es su propia sesión de trabajo.

> El estado de **las fases** vive en la sección 8 del
> [`README principal`](../../../README.md), y solo allí. Este documento baja al
> detalle de un bloque de trabajo **que no es una fase**: qué entra en cada hito,
> en qué estado va y cómo se trabaja. Los de la Fase 1 y la Fase 2 se conservan en
> [`roadmap.md`](roadmap.md) y [`phase-2-roadmap.md`](phase-2-roadmap.md) como
> historia de cómo se hizo el core y los cuatro primeros módulos.

## Alcance

Las dos fases cerradas dejaron una lista corta de cosas **abiertas a propósito**,
cada una con su motivo escrito y —cuando lo tenía— su destinatario. Este bloque
cierra cuatro de ellas:

| Hueco | Dónde quedó escrito | Por qué sigue abierto |
|---|---|---|
| **La baja de un hogar** y el borrado de sus ficheros, junto con la del avatar al cerrar la cuenta | [Fase 1](roadmap.md) y [`decisions.md`](decisions.md) | No es una pregunta de ficheros sino de un caso de uso que el core no contempla. La del avatar **resultó ser la misma pregunta**, y por eso se fundieron |
| **La conversión de HEIC** | [Fase 1](roadmap.md), 5.8.3 y la ficha de [`upload-field`](../../frontend/design-system/components/upload-field.md) | 5.8.3 se la asigna al frontend y el frontend no tiene con qué. Cerrarlo son dos caminos, y ninguno es un ajuste |
| **El Transactional Outbox** que nombra 5.2.2 | [Fase 2](phase-2-roadmap.md) | Con cuatro módulos escuchando, perder un evento en un reinicio deja de ser teórico. La siembra desde estado lo mitiga y no lo arregla |
| **Los cuatro atributos propuestos** —estado de conservación, condición en préstamo, etiquetas libres, e icono y color de categoría— | [`decisions.md`](decisions.md), revisados el 2026-08-09 | «No entran hasta que haya un caso de uso que los pida», y ese criterio lleva dos fases sin disparar |

> **Y un quinto que no estaba en ninguna lista, porque nadie lo había visto
> (2026-08-20).** Ejecutando el Hito 1 se destapó que **el «hoy» de las reglas de
> calendario era el de UTC y no el del hogar**, con lo que un hogar peninsular no
> podía registrar entre la medianoche local y la de Greenwich lo que acababa de
> hacer. No es un hueco dejado a propósito sino un defecto, y entra aquí por lo
> mismo que los otros cuatro: es saldo de las dos fases cerradas, no avance del
> producto. Su hito es el **5** —**cerrado el 2026-08-20**—, y el detalle de cómo
> se encontró está más abajo.

**Por qué ahora y no dentro de la Fase 3.** Tres de los cuatro **se encarecen con
cada módulo nuevo**, y el cuarto no cambia de precio:

- El **outbox** toca el camino por el que los eventos llegan a los handlers. Hoy
  hay cuatro módulos escuchando; después de la Fase 3 puede haber trece. Cada uno
  es un handler más al que revisarle la idempotencia y la transacción, y un
  consumidor más de un evento que hoy se puede perder.
- La **baja de un hogar** borra en cascada las tablas de todos los módulos
  desplegados. Con cuatro es una lista corta; con trece es una lista que se
  olvida, y lo que se olvida ahí son filas de un hogar que pidió marcharse.
- **HEIC** es la carencia más visible para el usuario más probable de una
  aplicación doméstica y no depende de ningún módulo: no se resuelve sola ni la
  resuelve nadie por el camino.

Y una razón que vale para los cuatro: **la Fase 3 no está planificada**, y
planificarla es su propia sesión de trabajo. Colgar este bloque de una fase que
todavía no existe sería, o bien planificarla de rebote —mal, y al final de otra
cosa—, o bien dejar un hito huérfano sin documento de alcance al que pertenecer.

### Dónde encaja esto en el roadmap

**Es un bloque entre fases: ni una fase, ni un hito de la Fase 3.** Aparece en la
sección 8 del README con su propia fila y su propio estado, delante de la Fase 3,
y se ejecuta como se ejecuta una fase: hitos, una sesión por hito y un pull
request por hito.

Se descartaron las dos alternativas:

- **Un hito 0 de la Fase 3.** Obliga a tener planificada la Fase 3 para poder
  empezar por su hito 0, que es el orden inverso al que este proyecto usa; y mete
  cuatro cosas que **no son módulos** dentro de una fase cuyo enunciado es «los
  nueve módulos restantes de 4.2».
- **Una fase propia numerada.** Tendría que ser la Fase 3 —renumerando a Fase 4
  los nueve módulos en tres documentos, uno de ellos cerrado— o un número
  intermedio que no significa nada. La numeración de fases describe el avance del
  producto; esto no es avance del producto, sino saldo de lo que las dos primeras
  dejaron a deber.

**Lo que este bloque no toca:** el estado de la sección 4.2. Ningún módulo cambia
de estado aquí, porque aquí no se construye ninguno.

### El punto de corte

Son siete hitos y siete sesiones. **Si hay que partirlo, se parte entre el Hito 2
y el Hito 3**: los tres primeros son huecos técnicos con una ADR cada uno, y los
tres siguientes son producto que no depende de ellos —dos de atributos y uno de
una regla de dominio—. No se parte en ningún otro sitio, porque cualquier otro
corte deja una ADR escrita sin lo que la valida, o un atributo a medio camino
entre el dominio y la pantalla.

## Cómo se trabaja

Igual que en las Fases 1 y 2:

- **Un hito por sesión.** Cada hito se ejecuta en una sesión propia, que arranca
  leyendo este documento. Son bloques grandes y mezclarlos hace que ninguno se
  cierre del todo.
- **Un pull request por hito**, y no se abre el siguiente hasta que el anterior
  está fusionado. Al fusionar, seguir el procedimiento de alineación local del
  [`CLAUDE.md`](../../../CLAUDE.md): las ramas remotas se borran al fusionar y las
  referencias locales no se enteran solas.
- **Cada hito atraviesa las capas en vertical** —dominio, aplicación, adaptador,
  frontend y sus pruebas—, no una capa entera de cada vez. **Con una excepción
  declarada**, el Hito 1, que no tiene frontend y al que no se le inventa uno; su
  ficha lo explica.
- **Las ADR se escriben en el hito que las estrena**, no aquí. Este documento dice
  cuáles hacen falta y dónde; escribirlas al planificar sería decidir sin lo que
  la implementación enseña, que es justo lo que las Fases 1 y 2 evitaron.
- **Al cerrar un hito** se actualiza su estado aquí y se añade la fila de historial
  al README. Cada dato en un solo sitio.
- **Lo mecánico va en un commit propio** y sin ningún otro cambio dentro, para que
  su revisión sea trivial.

## Las cuatro decisiones de arranque

Están tomadas y son la premisa del plan, no una pregunta de ningún hito. Aquí
quedan escritas para que esos hitos no tengan que volver a decidirlas; su
alternativa descartada está en [`decisions.md`](decisions.md).

**1. La baja de un hogar es con periodo de gracia.** El administrador la solicita,
el hogar queda marcado y **lo purga el recorrido diario que ya existe** — una
`ScheduledCheck` más con `CheckOwner.Core`, que es la forma que la
[ADR-011](../architecture/decisions/ADR-011-scheduled-checks-and-notice-delivery.md)
ya fijó y que `PurgeUnverifiedHouseholds` ya usa. Se descartó el borrado
inmediato: en la supresión irreversible de la casa entera, **poder arrepentirse
importa**. Durante la gracia el hogar sigue funcionando igual —solo lo distingue
un aviso—, porque dejarlo de solo lectura castiga precisamente a quien todavía
puede cancelar.

**2. El avatar al cerrar la cuenta se resuelve con la baja, no aparte.** Ya se
intentó tratarlo como pregunta de avatares en el Hito 3 de la Fase 1 y **no tenía
dónde engancharse**: `DeactivateUser` da de baja la pertenencia, no la identidad.
La regla de 4.1.4 lleva escrita desde entonces esperando al caso de uso que la
active, y ese caso de uso entra en el Hito 0.

**3. Los cuatro atributos entran, los cuatro.** Con lo que eso obliga a hacer, que
es decir **qué ha cambiado** respecto al criterio que los dejaba fuera. Está en
«Los cuatro atributos contradicen su propio motivo de aplazamiento», más abajo.

**4. HEIC no se decide por gusto.** Este plan **no elige dirección**: la asigna a
un hito que la cierre con una ADR nueva y **con dos medidas delante**. Cuáles, y
por qué esas dos, en «HEIC no se decide por gusto» más abajo.

## Lo que hay que tener delante antes de escribir la primera línea

Doce cosas que este trabajo se va a encontrar. No son avisos genéricos: cada una
tiene detrás una decisión ya tomada en el repositorio que conviene no volver a
tomar del revés.

### El outbox no cambia la garantía, y decirlo evita el error caro

La entrega es hoy **at-least-once**, y por eso los handlers son idempotentes
(`IdempotentEventHandler`). Con outbox **sigue siendo at-least-once**: lo que
añade es que el evento **no se pierda**, no que llegue una sola vez. La ADR del
Hito 1 tiene que decirlo con esas palabras.

Si este plan dejara creer lo contrario, alguien retiraría la idempotencia de un
handler —«ya no hace falta, ahora hay outbox»— y el fallo aparecería meses
después, en forma de una línea de la compra duplicada que nadie sabe explicar.

### El relay no nace de una petición, así que es el caso de los procesos diarios

No tiene token del que sacar el hogar. La salida fácil —`BYPASSRLS` para el
usuario de la aplicación— desactiva la segunda capa para **toda** la aplicación y
no solo para el proceso, que es lo que la
[ADR-003](../architecture/decisions/ADR-003-row-level-security.md) prohíbe
expresamente y lo que una prueba afirma que no ocurre.

**El relay reutiliza la forma de `DailySweep`, pero no su vehículo**, y las dos
mitades importan:

- **La forma sí**: hogar a hogar, fijando `app.household_id` en cada transacción,
  nunca con `BYPASSRLS`.
- **El vehículo no**: no es una `ScheduledCheck` de la pasada diaria. Un evento
  que tarda un día en llegar a Warehouse no es una entrega diferida, es una
  entrega rota. El relay tiene su propio periodo, medido en segundos.

Y para recorrer la lista hay que poder leerla, que es el paso que la Fase 1 ya
resolvió una vez: hay **tres funciones `SECURITY DEFINER`** en el rol
`drp_resolver` que devuelven **solo identificadores de hogar** —ni un dato, ni una
fila, ni un correo— y tras las cuales el caso de uso vuelve a trabajar bajo la
política. La dirección propuesta para el Hito 1 es **una más**, que devuelva
los hogares con entregas pendientes: así el relay no recorre en vacío mil hogares
cada pocos segundos para encontrar el que tiene una fila.

> **Ojo al número, que ha cambiado.** El Hito 0 añadió
> `list_households_for_identity` —«en qué hogares consta esta persona», que es lo
> que la baja de hogar necesita para saber si alguien se queda sin ninguna
> pertenencia— así que la del outbox no será la cuarta. Lo que no cambia es el
> criterio de admisión: **solo identificadores de hogar**, pregunta cerrada, y de
> `drp_resolver`.

### `ModuleEventHandler` hay que volver a mirarlo, no dejarlo estar

Hoy abre `REQUIRES_NEW` y corre en `AFTER_COMMIT`, y las dos cosas están ahí por
razones **medidas** y no supuestas: unido a la transacción del core, un handler ve
cero filas y, si falla, se lleva por delante el alta que originó el evento.

Con un relay que entrega **fuera** de la transacción del core, esas dos razones
dejan de tener el mismo sujeto: ya no hay transacción del core abierta a la que
unirse, ni contexto de inquilino heredado. Puede que la decisión se quede como
está; lo que no puede es quedarse **sin haberlo pensado**. El Hito 1 lo resuelve
con una prueba delante, igual que `EventBusSweepTest` afirmó en su día el
comportamiento real en lugar del deseado.

Lo mismo vale para la guarda de idempotencia: su comentario ya dice que «el día
que haya outbox, esa guarda se muda con él». Ese día es este hito.

### La siembra desde estado no desaparece con el outbox, y ya está escrito

La [ADR-010](../architecture/decisions/ADR-010-module-boundaries-and-activation.md)
manda sembrar un módulo **desde el estado actual del core** y no reproduciendo
eventos, y el roadmap de la Fase 2 dice explícitamente que el día que haya outbox
eso **no cambia**: reproducir un año de eventos para sembrar sigue siendo peor que
leer el estado.

El Hito 1 no puede proponer lo contrario sin enfrentarse a ese párrafo. Y no hay
motivo para hacerlo: el outbox resuelve que un evento **publicado** llegue; la
siembra resuelve que un módulo **encendido hoy** conozca lo de antes de existir.
Son dos problemas distintos que se parecen.

### El nudo de la baja de hogar es `identities`, y no las cascadas

Toda tabla del core y de los módulos lleva `household_id`, así que ahí la cascada
es mecánica. Pero **`identities` no lo lleva, y es la única tabla del modelo con
datos personales sin política de RLS**: una persona no pertenece a un hogar, su
pertenencia sí.

De ahí que borrar un hogar **no pueda borrar la identidad**, salvo que no le quede
ninguna pertenencia — y ese caso es exactamente «cerrar la cuenta», que hoy **no
existe como caso de uso** aunque su regla lleve escrita desde la Fase 1. Así que
son dos bajas y no una:

| | Qué se lleva | Quién la pide |
|---|---|---|
| **Baja de hogar** | El hogar entero: sus filas, las de todos los módulos y **sus ficheros en disco** | Un `HOUSEHOLD_ADMIN` |
| **Baja de identidad** («cerrar la cuenta») | Las credenciales de una persona y **su avatar**, que es lo único que la retrata | La propia persona |

**Y la dirección está fijada: la baja del hogar puede activar la de la identidad,
nunca al revés.** Un hogar que se va deja identidades sin ninguna pertenencia, y
esas hay que resolverlas; una persona que cierra su cuenta **no se lleva la casa
por delante** — se va ella, y el hogar sigue con quien quede. Lo que falta por
decidir es si esa identidad huérfana se da de baja o se borra de verdad, y es una
de las preguntas de más abajo.

### Los ficheros están fuera de PostgreSQL, así que el borrado no es transaccional

La [ADR-005](../architecture/decisions/ADR-005-local-file-storage.md) los pone en
disco, con las rutas troceadas por hogar. **`PurgeUnusedFiles` ya resolvió el
orden, y su motivo está escrito**: los bytes primero y la fila después, porque al
revés un fallo entre medias deja una fila viva apuntando a unos bytes que ya no
están, y eso sí es un error duro porque nada volvería a mirarlo. El Hito 0
reutiliza ese criterio en lugar de inventar otro.

Hay además una promesa autoprogramada que este hito cumple. La ficha de
`PurgeUnverifiedHouseholds` en [5.7](use-cases/README.md) dice que no puede dejar
bytes huérfanos porque sin correo verificado no hay sesión y sin sesión no hay
subidas — «**el día que eso cambie, tendrá que borrar también el directorio del
hogar**». Ese día llega con la baja de un hogar que sí tiene ficheros: las dos
purgas comparten el mismo camino de borrado.

### HEIC no se decide por gusto: lo que hay que medir, y antes de decidir

Dos números, los dos obtenibles hoy:

- **El peso real del decodificador wasm en el bundle**, no el estimado. El bundle
  de referencia al planificar son **402 kB**, y el hito lo vuelve a medir antes de
  comparar. Un megabyte encima de eso no es un incremento: es un cambio de
  categoría, y hay que decirlo con el número delante.
- **El coste en servidor con los números del runner de la CI**, que ya existen en
  [`capacity-measurements.md`](../../backend/operations/capacity-measurements.md):
  recodificar una foto de 12 MP son **776 ms de mediana** con 2 vCPU, y Argon2id
  ya se lleva **19 MiB por login simultáneo**. Decodificar HEIC se suma a eso, en
  la operación que ya era la cara por un orden de magnitud.

Con esas dos cifras la decisión se toma; sin ellas se opina. Y hay una tercera
salida que conviene tener en la lista aunque solo sea para descartarla por
escrito: **pedirle al usuario que cambie el ajuste de la cámara**, que es lo que
hoy ocurre de hecho, y en forma de `415`.

### Y la trampa: 5.8.3 asigna la conversión al frontend

Así que el camino del servidor no es «hacerlo»: es **enmendar una sección que
decidió lo contrario**, con su motivo. Si sale ese camino, la enmienda de
[5.8.3](../../backend/architecture/file-storage.md) es parte del trabajo del hito
y no un flequillo posterior. La ADR-005 **no se reescribe** —una ADR aceptada
nunca— sino que se enlaza hacia adelante desde la nueva, como hace la ADR-002 al
final.

Dos detalles que ahorran una discusión:

- **La lista blanca vive también en la base de datos**, como un `CHECK` sobre
  `files.content_type` (ver 5.6), y ampliarla exige una migración — esa fricción
  es deliberada. Pero el tipo que se guarda es el **detectado tras recodificar**, y
  el paso 5 de 5.8.3 ya recodifica toda imagen: si el servidor convierte al
  recibir, puede que HEIC no llegue nunca a escribirse en esa columna. El hito
  tiene que comprobarlo antes de dar por hecha la migración, o por hecha su
  ausencia.
- **Recodificar es sobre todo por el EXIF**, no por los bytes. Cualquier camino
  que admita HEIC tiene que seguir pasando por el lienzo nuevo del que salen los
  píxeles sin metadatos, porque ahí es donde se quedan las coordenadas GPS de la
  casa.

### Los cuatro atributos contradicen su propio motivo de aplazamiento

En [`decisions.md`](decisions.md) está escrito que no entran «hasta que haya un
caso de uso que los pida», y entran. **Contradecirlo en silencio es lo único que
aquí no se hace**, así que el plan dice las dos cosas: qué ha cambiado en cada uno,
y por qué el criterio se retira para los cuatro.

| Atributo | Qué ha cambiado desde el 2026-08-09 |
|---|---|
| **Estado de conservación** | Su destinatario asignado era CMMS. **CMMS llegó y no lo quiso**: su ficha declara que registrar una intervención «no toca el asset», y ninguna de sus tres tablas guarda condición. El destinatario contestó, y contestó que no era suyo |
| **Condición en préstamo** | Su destinatario es la gestión avanzada de préstamos, que es **prioridad Baja** entre los nueve que quedan. Esperar a que llegue es, en la práctica, decidir que no exista |
| **Etiquetas libres** | El motivo era «otra entidad recién después de añadir el catálogo de categorías». Ese catálogo lleva dos fases funcionando, y clasificar por **una sola** categoría se le queda corto a un inventario con veintiocho tablas detrás |
| **Icono y color de categoría** | El motivo era que son presentación. Lo siguen siendo — pero ahora el sistema de diseño tiene tokens medidos y **un script que falla la construcción** si un par baja de WCAG AA, que es exactamente la defensa que faltaba para dejar elegir |

**Y el criterio se retira, con su razón.** «Hasta que haya un caso de uso que lo
pida» era un buen filtro mientras el producto se estaba definiendo, y hoy produce
un bloqueo: tres de los cuatro tienen su destinatario entre módulos que, o ya los
rechazaron, o están al final de la cola. Además la Fase 2 introdujo un hecho que
el criterio no preveía —**un módulo no puede añadir una columna a una tabla del
core**—, así que si estos atributos han de existir, la decisión es del core y no
de un módulo que aún no está escrito.

### Dos de los cuatro tienen dueño discutible, y aquí se resuelve

El criterio es el que la Fase 2 usó para el peso y el volumen: **una regla del
core no puede depender de un módulo que se puede apagar.**

- **Estado de conservación: del core.** Describe la cosa, no su mantenimiento. Un
  hogar con todos los módulos apagados sigue queriendo saber que el taladro está
  para tirarlo, igual que guarda su número de serie. Se descarta una tabla de
  CMMS, que haría desaparecer el dato al apagar el módulo; y se descarta texto
  libre, porque `notes` ya existe y un atributo que no se puede filtrar ni
  comparar no añade nada sobre una nota.
- **Condición en préstamo: del core.** El **momento** en que se captura es una
  operación del core —`StartLoan` y `ConfirmReturn`—, y un módulo que no existe no
  puede capturar nada. Cuando llegue la gestión avanzada de préstamos leerá lo que
  ya haya: la dirección `módulo → core` está permitida.
- **Etiquetas libres: del core.** Clasificar un asset es del core —`Category` lo
  es— y el filtro por etiqueta cae dentro de `ListAssets`, que es una query del
  core. Una etiqueta que viva en un módulo se lleva la clasificación del
  inventario el día que ese módulo se apague.

### El color que elige el usuario no pasa por `check-contrast.py`

El script mide **36 pares en los dos modos** leyendo los `oklch()` reales de
`frontend/src/index.css`, y su lista se mantiene a mano a propósito. Un color
elegido con un selector libre no está en ningún token, así que no lo mide nadie:
sería lo único de la interfaz cuyo contraste se afirma en vez de comprobarse.

Por eso **el color y el icono se eligen dentro de un juego cerrado** —tokens ya
medidos, iconos de los que el sistema de diseño ya trae— y los pares nuevos se
añaden a la lista del script. Se descartó el selector libre de color, que obliga a
elegir entre dos malas salidas: no certificar nada, o calcular automáticamente el
texto de encima, con lo que el usuario acaba sin el color que eligió.

### El «hoy» de una regla no es el del servidor, y son tres sitios con la misma línea

El bean `Clock` es `Clock.systemUTC()`
(`backend/src/main/kotlin/com/drp/config/SecurityConfig.kt`), así que todo lo que
derive un día de calendario de él sale en **hora de Greenwich**. Hay exactamente
tres sitios que lo hacen, y los tres tienen la misma línea copiada:

| Dónde | Qué regla gobierna |
|---|---|
| `RegisterMaintenanceIntervention` (`MaintenanceUseCases.kt`) | Que una intervención **no sea del futuro**: el `400 VALIDATION_ERROR` que se destapó |
| `MaintenanceDueCheck` (`MaintenanceChecks.kt`) | `MaintenancePlan.stageOn(today)` → `DUE_SOON` y `OVERDUE` |
| `WarehouseExpiryCheck` (`WarehouseChecks.kt`) | `StockLot.stageOn(today, leadDays)` → `NEAR` y `EXPIRED` |

**La frontera no es «CMMS», es `LocalDate` frente a `Instant`.** Comparar
instantes no necesita ninguna zona, y por eso [`decisions.md`](decisions.md) pudo
cerrar el 2026-08-17 que la del hogar **no interviene en el vencimiento** de un
préstamo: `dueAt` es un `timestamptz`. Esa decisión no se reabre —sigue siendo
cierta y por el motivo que dice—, pero su propio razonamiento, aplicado a un
`LocalDate`, da el resultado contrario: un día de calendario no significa nada sin
una zona, y la única defendible es la del hogar. El core no tiene ninguna regla de
calendario: sus fechas —`acquiredOn`, `Document.date`, `validUntil`— se guardan y
se pintan, nunca se comparan contra «hoy».

**El precedente ya está escrito y hace lo correcto.** `RequestHouseholdClosure`
convierte el instante de la baja a día con `household.timeZone`, con el comentario
exacto al lado: es un día del calendario de quien lo lee, no un instante del
servidor. Lo que falta es que las tres reglas hagan lo mismo.

**Las dos gravedades son distintas, y conviene no confundirlas.** La intervención
es un gesto de una persona a cualquier hora, y falla en toda la franja entre la
medianoche local y la de Greenwich —dos horas en CEST, una en CET—. Las dos
comprobaciones periódicas se equivocan como mucho en un día y solo en hogares
lejos de UTC: el barrido corre a las `03:15 Europe/Madrid`, que en verano es la
`01:15` UTC del mismo día, así que para un hogar peninsular la fecha coincide y
para uno en Auckland no.

**Y el frontend también es UTC**, lo que cambia el síntoma: `today()` en
`frontend/src/routes/maintenance.tsx` es `new Date().toISOString().slice(0, 10)`,
y ese valor se usa como valor inicial **y** como `max` del campo de fecha. Un hogar
de Madrid a las 00:30 no ve el `400`: ve el campo relleno con **ayer** y el
selector negándose a ofrecer hoy. Como síntoma de producto es peor que el error,
porque registra el día equivocado en silencio. `inMonths()`, que rellena el
`nextDueOn` de un plan, mezcla lo mismo: suma meses en local y formatea en UTC.

## Hitos

### Hito 0 — Baja de hogar y cierre de cuenta · **Hecho** (2026-08-20)

El primero porque es el que borra datos personales, y porque cierra un criterio
que otro documento dejó colgando del suyo: la retención de las cinco tablas que
crecen sin techo se resuelve, según
[`capacity-measurements.md`](../../backend/operations/capacity-measurements.md),
«con la baja del hogar».

- [x] **ADR-012 — Supresión de datos: baja de hogar y cierre de cuenta**: el
      periodo de gracia y su duración, la purga desde el recorrido diario que ya
      existe, el orden bytes → filas, y la frontera entre hogar e identidad. Con su
      criterio de validación y de reversión, como toda ADR.
- [x] **Migración `V14`**: la marca de baja en `households` —cuándo se pidió, quién
      la pidió y cuándo vence la gracia—, con la autoría apuntando a la
      pertenencia, como en todo el modelo.
- [x] **`RequestHouseholdClosure` y `CancelHouseholdClosure`**, solo
      `HOUSEHOLD_ADMIN`. Pedir la baja levanta **un** aviso en `household_notices`
      y no uno cada noche: un aviso no se repite mientras la condición siga siendo
      cierta, que es regla de la ADR-011.
- [x] **`CloseAccount`**, autenticado y sobre la propia identidad: marca
      `deactivatedAt`, revoca sus refresh tokens y **borra el avatar**. Es la regla
      de 4.1.4 que llevaba escrita desde la Fase 1 sin nada a lo que engancharse.
- [x] **`PurgeClosedHouseholds`**, una `ScheduledCheck` más con `CheckOwner.Core`.
      No hay recorrido nuevo: entra en `DailySweep` como las tres que ya están.
- [x] **El borrado del árbol de ficheros del hogar** detrás del puerto
      `FileStorage`, con el criterio de `PurgeUnusedFiles`: los bytes primero y las
      filas después.
- [x] **Qué se lleva la cascada, comprobado tabla por tabla** y no confiando en las
      claves ajenas: las **veintidós que cuelgan de `households` con `ON DELETE
      CASCADE`**, las de los cuatro módulos incluidas, y aparte las **cinco que no
      llevan `household_id`** (5.6), que son exactamente donde está el nudo. Una
      tabla que se quede fuera no da ningún error — deja filas de un hogar que
      pidió marcharse.
- [x] **Cuatro operaciones en el contrato**, con su `operationId`: solicitar la
      baja, cancelarla, verla en el estado del hogar y cerrar la cuenta.
- [x] **Frontend**: la zona de peligro en la pantalla del hogar, con confirmación
      escrita y no con un botón; el aviso persistente mientras dura la gracia, con
      la fecha en la que el hogar desaparece; y cerrar la cuenta desde la pantalla
      de la persona.
- [x] **Recorrido vertical**: solicitar la baja, ver el aviso, cancelarla y
      comprobar que todo sigue. La purga se demuestra en la batería del backend,
      que es donde se puede mover el reloj — y con ficheros de verdad en disco,
      porque el objetivo es justamente que no quede ninguno.
- [x] **Barrido de aislamiento**: las operaciones nuevas entran en
      `TenantIsolationSweepTest` con el criterio de inclusión de su cabecera.
- [x] **Los documentos que este hito cierra**: 4.1.4 (las dos bajas), 5.7 (los tres
      casos de uso nuevos y la promesa autoprogramada de
      `PurgeUnverifiedHouseholds`), 5.6, el criterio de retención de
      [`capacity-measurements.md`](../../backend/operations/capacity-measurements.md)
      y la ficha de [`scheduled-jobs.md`](../../backend/operations/scheduled-jobs.md).

> **El corte, si no cabe.** Primero la baja de hogar y después el cierre de
> cuenta. Se fundieron porque son **la misma pregunta**, no porque sean el mismo
> trabajo: la respuesta a qué se hace con una identidad huérfana la da la baja de
> hogar, y el cierre de cuenta la aplica.

### Hito 1 — Transactional Outbox · **Hecho** (2026-08-20)

- [x] **ADR-013 — Transactional Outbox**: qué garantía cambia —**ninguna**, sigue
      siendo at-least-once—, el recorrido del relay sin `BYPASSRLS`, qué pasa con
      `AFTER_COMMIT` y `REQUIRES_NEW`, y por qué la siembra desde estado no cambia.
      Con lo que le ocurre a lo pendiente cuando el hogar se purga, que la ADR-012
      hizo posible y nadie había escrito.
- [x] **Migración `V15`**: `event_outbox` con `household_id`, RLS y `FORCE` como
      cualquier tabla del modelo, más la **sexta función `SECURITY DEFINER`** en
      el rol `drp_resolver` —el plan la llamaba «la cuarta» y contando la de
      préstamos de la V6 y la que añadió el Hito 0 eran ya cinco—, que devuelve
      **solo identificadores** de los hogares con entregas pendientes. El modelo
      pasa de 28 a **29 tablas**.
- [x] **La publicación escribe la fila dentro de la transacción del core.** Es lo
      único que hace que el evento no se pierda, y es todo lo que el core nota: la
      firma de `EventBus` no cambia.
- [x] **El relay**, con periodo propio medido en segundos y la forma de
      `DailySweep`: hogar a hogar, `app.household_id` en cada transacción, nunca
      `BYPASSRLS`. Con **su propio interruptor**, medido en los dos sentidos como
      el del programador y por el mismo motivo elevado a segundos.
- [x] **La decisión sobre `ModuleEventHandler`, medida y no razonada.** Se quedan
      las dos, con la prueba que dice por qué: dos testigos que se diferencian en
      una línea, y solo el que declara `fallbackExecution` recibe por el camino del
      relay.
- [x] **La guarda de idempotencia se muda**, que es lo que su propio comentario
      dice que pasa «el día que haya outbox»: su mitad duradera es la fila del
      outbox, y en memoria queda lo que solo tiene sentido dentro de un proceso.
- [x] **La prueba que hoy no se puede escribir**: publicar, cortar el proceso antes
      de entregar, arrancar y comprobar que el handler recibe. El testigo es el
      **módulo de prueba** del Hito 0 de la Fase 2, que vive en el árbol de pruebas
      y existe exactamente para esto.
- [x] **Medida de capacidad**: la fila entregada **se borra**, así que no hay sexta
      tabla. Queda anotado en
      [`capacity-measurements.md`](../../backend/operations/capacity-measurements.md)
      para que nadie tenga que volver a derivarlo.
- [x] **Este hito no tiene frontend, y no se le inventa uno.** Nada de lo que hace
      es visible en una pantalla, y añadir una para cumplir la forma sería añadir
      código que no defiende nada. Su recorrido vertical es dominio → aplicación →
      adaptador → PostgreSQL, y la batería E2E no se toca.

### Hito 2 — Conversión de HEIC · **Hecho** (2026-08-20)

- [x] **Las dos medidas, y antes de decidir**, escritas en
      [`capacity-measurements.md`](../../backend/operations/capacity-measurements.md),
      que gana **una tercera magnitud**: lo que cuesta llegar al navegador. El
      bundle **no eran 402 kB sino 407,28** —volver a medirlo era la condición, y
      con razón— y el decodificador pesa **2 995 kB**. En servidor, de **×3,4 a
      ×5,6** sobre la operación que ya era la cara por un orden de magnitud.
- [x] **ADR-014 — Conversión de HEIC**, con las medidas dentro y **las tres
      salidas evaluadas**, la de no convertir incluida y descartada por escrito.
- [x] **La implementación del camino que gana, entera**: el cliente, en
      `uploadFile` —que es la única puerta por la que pasan las dos vías de
      subida— con el decodificador en un `import()` dinámico.
- [x] **Gana el cliente, así que 5.8.3 no se enmienda: se confirma.** Lo que se
      cierra es la nota de la ficha de
      [`upload-field`](../../frontend/design-system/components/upload-field.md), y
      la ADR-005 gana una sección «Posterior a esta decisión» que enlaza hacia
      adelante, sin tocar su cuerpo.
- [x] **El mensaje del `415` se queda como está**, y se dice por qué: sigue siendo
      la respuesta correcta para todo lo demás, y ya no es la que recibe una foto
      de iPhone.
- [x] **Recorrido vertical**: un HEIC de verdad —marca `heic`, 1280 × 960 y con
      **352 B de EXIF con coordenadas GPS dentro**— sube, se ve en la galería y en
      la ficha del asset, se guarda como `image/jpeg` y sale sin metadatos.

> **La trampa que el plan anunciaba estaba, y se resolvió del otro lado.** «Puede
> que HEIC no llegue nunca a `files.content_type`» era la sospecha correcta y el
> hito la comprobó en vez de suponerla: por el camino del cliente el servidor **no
> ve un HEIC nunca**, así que no hay migración, ni cambio de contrato, ni entrada
> nueva en el enumerado del dominio. El recorrido vertical lo afirma leyendo el
> `contentType` de lo guardado.

> **Y lo que la implementación destapó y el plan no preveía.** Tres cosas. La
> primera es que **el megabyte no cae sobre el bundle**: en un `import()` dinámico
> son 2,49 kB sobre la primera carga y 2 995 kB para quien elige un HEIC, así que
> el «cambio de categoría» que el plan temía era cierto para un supuesto que
> resultó evitable. La segunda es que **el camino del servidor no empezaba por
> escribir código**: el único plugin de ImageIO para HEIF de Maven Central exige
> **JDK 22** —con el proyecto en 17— y enlaza con el `libheif` del sistema, de modo
> que la comparación no era «una dependencia más» sino un cambio de plataforma. Y
> la tercera no es del hito: al escribir el estado **Convirtiendo** hubo que decir
> que **no** se puede cancelar, y la fila de al lado prometía que la subida sí —
> `UploadField` documenta desde la Fase 1 un botón de **Cancelar** que nunca se
> construyó, con su `AbortController` declarado y sin usar. No se arregla aquí,
> pero se deja de afirmar.

### Hito 3 — Estado de conservación y condición en préstamo · **Hecho** (2026-08-20)

Los dos atributos que no traen entidad nueva. Van juntos porque son el mismo tipo
de cambio: un enumerado corto que nace en el dominio y llega hasta un desplegable.

- [x] **Migración `V16`**: la condición del asset y las dos del préstamo —en la
      entrega y en la devolución—, todas anulables. Nulo no es un hueco: significa
      que nadie lo anotó. **Tres columnas y ninguna tabla**, así que el modelo se
      queda en 29. Con tres garantías de la base de datos: la escala cerrada en
      las tres columnas, la conservación **solo sobre un `DURABLE`** —ampliando la
      restricción que ya cubría el número de serie, en vez de añadir una segunda—
      y la condición de vuelta exigiendo que haya vuelta.
- [x] **Dominio y casos de uso**: `CreateAsset` y `UpdateAsset`, `StartLoan` y
      `ConfirmReturn`. La condición de devolución solo se admite al confirmarla,
      que es cuando se sabe. **Una sola escala de cinco valores para los tres
      campos**, porque comparar dos momentos es el motivo entero del atributo, y
      **la devolución no toca el asset**: lo que se afirma al devolver es del
      préstamo, y quien lo afirma puede ser alguien de fuera del hogar.
- [x] **El contrato no gana operaciones: ensancha esquemas.** Sigue en **102**, y
      lo que cambia son seis esquemas más dos nuevos —`AssetCondition` y el cuerpo
      **opcional** de la devolución—, un filtro en `listAssets` y los ejemplos de
      5.4.3.
- [x] **Frontend**: el campo en el alta y en la ficha del asset, el filtro en el
      listado y los dos momentos del préstamo. La vista externa lo ve y lo
      escribe, y esa es **la única escritura que un token acotado alcanza en todo
      el hogar**: lo que la contiene es que el cuerpo tiene un solo campo y es un
      enumerado cerrado, con una prueba que manda cinco campos de más y comprueba
      desde dentro de casa que ninguno se escribió.
- [x] **Documentos**: 4.1.1, 4.1.5, 5.6, 5.7, los ejemplos JSON y 4.1.7 con las
      ocho decisiones y sus alternativas descartadas.
- [x] **Barrido de aislamiento: no cambia, y se dice por qué.** El hito no añade
      operaciones, y el filtro nuevo de `ListAssets` es un enumerado: por el
      criterio de inclusión de `TenantIsolationSweepTest` —entra lo que puede
      **nombrar** algo del hogar A— un valor cerrado no tiene por dónde cruzar, y
      `listAssets` ya está dentro por los seis filtros que sí son identificadores.
- [x] **Auditoría de accesibilidad**: **«Inventario» entra en la lista**, que era
      una de las cuatro pantallas del core que seguían sin auditar y es la que
      este hito toca en sus tres sitios. Con un asset sembrado antes, porque una
      pantalla vacía pasa axe sin haber mirado ninguna fila.
- [x] **El hogar de demostración trae valores**, que si no la pantalla nueva
      saldría vacía justo en el hogar que existe para enseñarla: la mayoría de los
      duraderos con su estado, **diez sin anotar a propósito** y ninguno `NEW`
      —lo más joven de esa casa tiene once meses—, y los préstamos con sus dos
      condiciones, incluido el patinete que «volvió con un arañazo» y cuya ficha
      dice `WORN`.

> **Lo que se decidió y el plan no preguntaba.** Ocho decisiones, todas en 4.1.7.
> Las dos que más cambian el producto son que **la devolución no propaga la
> condición al asset** —propagarla le daría a un token acotado una escritura sobre
> el inventario, y CMMS ya había sentado el precedente contrario— y que **la
> condición no se pinta con color**: la ficha de `StatusBadge` declara antiuso dos
> distintivos en la misma fila y la paleta de dominio tiene cinco tonos elegidos
> para sobrevivir a una deuteranopia, así que un sexto para una escala de cinco
> valores sería color como único portador. De ahí que **`check-contrast.py` siga
> midiendo 36 pares**: no hay ningún color nuevo que certificar.

> **Y lo que la implementación destapó, que no era del hito.** La pantalla de
> Préstamos es **el único fichero del frontend que se pinta con tokens que no
> existen**: `text-muted` y `border-line` no generan ni una regla de CSS
> —comprobado sobre el CSS construido—, así que su texto secundario salía a plena
> tinta y sus tarjetas llevaban un borde en `currentColor` donde el resto de la
> aplicación lleva `border-border-subtle`. No fallaba nada: se veía como una
> pantalla que no se parece a las demás. Se arregla aquí, en un commit mecánico
> aparte, porque apareció al añadirle la condición y copiarle las clases habría
> propagado el defecto.

### Hito 4 — Etiquetas e identidad visual de las categorías · **Hecho** (2026-08-20)

Los **dos atributos que quedaban** de los cuatro que 4.1.7 dejó propuestos el
2026-08-09. Con ellos, esa propuesta queda cerrada entera.

- [x] **ADR-015 — Color e icono elegidos por el usuario, dentro de un juego
      certificado**: por qué el juego es cerrado, cómo entra en
      `scripts/check-contrast.py` y qué haría falta el día que alguien quiera
      abrirlo. Extiende la
      [ADR-006](../architecture/decisions/ADR-006-frontend-stack-and-design-system.md),
      que **no se reescribe** y gana su sección hacia adelante.
- [x] **Migración `V17`**: `tags` y `asset_tags`, las dos con `household_id`, RLS
      y `FORCE`, más el icono y el color en `categories`. El modelo pasa de **29 a
      31** —el plan decía «de 28 a 31» porque se escribió antes del outbox.
- [x] **La forma de la etiqueta, decidida: catálogo por hogar.** Con las tres
      cosas medidas y cada una con su prueba: renombrar es una fila frente a
      recorrer todos los assets; deduplicar sin mayúsculas ni acentos lo hace el
      índice normalizado que ya existe, y sobre texto no hay ninguna fila común
      donde ponerlo; y autocompletar es un `SELECT` sobre una tabla pequeña frente
      a un `DISTINCT` sobre un campo repetido.
- [x] **Cuatro operaciones en el contrato**, con su `operationId`: listar, crear,
      renombrar y retirar. Etiquetar **no** es una quinta: viaja en `tagIds` dentro
      de `POST /assets` y `PATCH /assets/{id}`. El contrato pasa de **102 a 106**.
- [x] **Las dos fichas antes de los componentes**, y volvieron a pagar tres veces:
      la navegación no tiene categorías, la entrada de un consumible no puede
      llevar el campo, y los cinco iconos de estado no eran un problema de
      dependencia sino **dos documentos del sistema de diseño que se contradicen**.
- [x] **`scripts/check-contrast.py` con los doce pares nuevos**, que pasa de 36 a
      **48**. Tres de los doce valores nacieron fuera del gamut sRGB y lo destapó
      la propia comprobación de gamut del script.
- [x] **Frontend**: el marcador en cada fila del inventario y en la ficha, las
      etiquetas en la fila, en la ficha y en el filtro, y el selector en el
      catálogo —que de paso le da interfaz a `updateCategory`, que existía desde
      el Hito 2 sin nadie que lo llamara—. **En la navegación no entra, y se dice
      por qué**; a cambio se cumple la promesa de `iconography.md` sobre el hueco
      de una foto que falta.
- [x] **Recorrido vertical y auditoría axe en los dos modos, con el color
      puesto**: «Catálogo» entra en la lista y el recorrido **mide el contraste ya
      aplicado** leyéndolo del navegador, que es lo único que demuestra que el par
      medido es el par que llega a la pantalla.
- [x] **Barrido de aislamiento**: las dos operaciones con identificador en la
      ruta, las dos referencias nuevas en el cuerpo —la primera que viaja **dentro
      de un array**—, el filtro del listado y la unicidad. Comprobado quitando el
      resolutor: falla.
- [x] **El hogar de demostración**: las doce categorías con icono y once con
      color, seis etiquetas sobre trece parejas, una categoría sin color y una
      etiqueta retirada.

> **Lo que la implementación destapó y el plan no preveía.** Cuatro cosas. La
> primera es que **el índice único de una etiqueta no puede ser parcial por
> retirada** como el de una categoría: allí es inofensivo porque un asset tiene
> una sola categoría, y aquí dos etiquetas del mismo nombre acabarían pintando la
> misma fila dos veces. Lo que lo sustituye —crear una que existe retirada **la
> revive**— apareció al buscar una salida que no fuera un `409` sobre una fila que
> el usuario no ve, y de paso le da deshacer a la retirada sin una quinta
> operación.
>
> La segunda es de medición y costó una ejecución del recorrido: **Chrome conserva
> `oklch()` en `getComputedStyle`**, así que el ayudante que leía los tres números
> del color devolvía 1,00:1 para todo. Se resuelve pintando un píxel de lienzo.
>
> La tercera y la cuarta son defectos de accesibilidad que **solo aparecieron al
> escribir las pruebas**: JSX se come el espacio inicial de la línea, así que el
> `<span class="sr-only">` detrás de un rótulo producía «EditarAlimentación»; y el
> selector de icono y color puede estar **dos veces en la misma pantalla** —el del
> alta y el de la fila que se edita—, con lo que eran cuarenta y cuatro botones
> con veintidós nombres repetidos. Es el mismo fallo que la ficha de
> `SuppliersPage` encontró en su día al escribirse por delante.

### Hito 5 — El «hoy» de las reglas de calendario · **Hecho** (2026-08-20)

No venía del plan: salió de ejecutar el Hito 1, y el detalle de qué se encontró
está en la sección «El “hoy” de una regla no es el del servidor» de más arriba. Es el hito más
pequeño del bloque —sin migración, sin operación nueva y sin pantalla nueva—, y
tiene hito propio en lugar de colgarse del cierre porque **cambia una regla de
dominio** y el Hito 6 no añade producto.

- [x] **La pregunta del hito, decidida: el día es el del hogar.** Y no por venir
      recomendada en el plan sino por lo que significa el tipo: `performedOn` y
      `nextDueOn` son `LocalDate`, y un día de calendario no significa nada sin
      una zona. Se descarta **dejarlo en UTC**, que parece la opción neutral y no
      lo es —es la zona del despliegue— y cuesta que un hogar peninsular no pueda
      apuntar de madrugada lo que acaba de hacer. La decisión del 2026-08-17 sobre
      el vencimiento **no se reabre**: sigue siendo cierta, y la frontera que
      trazaba era `Instant` frente a `LocalDate`.
- [x] **La [ADR-011](../architecture/decisions/ADR-011-scheduled-checks-and-notice-delivery.md)
      ampliada y no reescrita**, con una sección que enlaza hacia adelante: a qué
      hora corre el recorrido sigue siendo del despliegue, **qué día mira es del
      hogar**. Ninguna ADR nueva, porque no había ninguna alternativa estructural
      que descartar.
- [x] **`HouseholdCalendar`, un puerto de plataforma que el core implementa**
      leyendo `households.time_zone`. Misma inversión que `HouseholdDirectory` y
      `NoticeRecipients`, y **la lista de excepciones de ArchUnit sigue teniendo un
      solo nombre**. Se descarta llevar la zona en `SessionClaims`, que la
      engordaría y además no sirve en el recorrido diario, que no tiene sesión.
- [x] **Los tres sitios pasan por él** y el `ZoneId` desaparece de los dos módulos.
      Con él se va el `?: ZoneId.systemDefault()` de las tres líneas, que era
      código muerto y sugería un respaldo imposible. El puerto, en cambio, **falla
      ruidosamente** sin contexto de inquilino: un respaldo a UTC daría un día
      plausible y equivocado.
- [x] **El `today()` del frontend**, que salía por dos sitios —valor inicial y
      `max`—, más `inMonths()`, que sumaba meses en local y formateaba en UTC. Sale
      de `useHouseholdToday()`, que lo resuelve con la zona que ya viaja en el
      contrato. Y con ellos **`dueStatus()`, que el plan no había visto**: restaba
      un día de calendario menos `Date.now()`, o sea un día contra un instante.
- [x] **La prueba de regresión con el reloj fijado**: 23:30 UTC, hogar en
      `Europe/Madrid`, intervención con la fecha local del hogar y el servidor
      aceptándola. Comprobado que sin el cambio da `400`. Lleva delante una prueba
      de que el instante elegido **de verdad cambia de día**, para que mover la
      hora no deje las otras cuatro pasando sin medir nada. Y una que ningún hogar
      solo puede dar: **el mismo instante, dos hogares y dos días**, con Madrid
      aceptando la fecha que Honolulu rechaza.
- [x] **El helper `today()` de `ApiClient.kt` sigue a la aplicación**: cuenta desde
      `HOUSEHOLD_ZONE`, que es la misma constante con la que `registerHousehold`
      da de alta el hogar. Separadas, la prueba vuelve a mentir en cuanto alguien
      cambia una de las dos.
- [x] **Sin migración y sin contrato nuevo.** El modelo se queda en **31 tablas** y
      el contrato en **106 operaciones**; lo único que cambia de `openapi.yaml` es
      la descripción de `timeZone`.
- [x] **Recorrido vertical**: dentro del de CMMS, que ya existía. El campo sale
      relleno con el día del hogar y su tope lo deja elegir, y **desde un navegador
      que está en otro día** —una de las dos antípodas horarias, la que caiga—
      sigue saliendo el del hogar: es lo que distingue «el día del hogar» de «el
      día de quien mira», y lo único que no puede comprobar el backend.
- [x] **Barrido de aislamiento: no cambia, y se dice por qué.** El hito no añade
      ninguna operación ni ningún filtro con identificador, así que por el criterio
      de inclusión de `TenantIsolationSweepTest` —entra lo que puede **nombrar** o
      **devolver** algo del hogar A— no hay nada que añadir. Lo más parecido a un
      riesgo entre hogares que introduce —que un hogar resuelva su día con la zona
      de otro— lo cierra `findCurrent()`, que va por la política, y lo mide la
      prueba de los dos hogares con el mismo reloj.
- [x] **La frase de [`users-and-access.md`](users-and-access.md) sobre `timeZone`**,
      corregida —y con ella **las otras dos que decían lo mismo** y que el plan no
      había localizado: la de [`data-model.md`](../architecture/data-model.md) y la
      del propio `openapi.yaml`. Ahora dicen para qué sirve de verdad y qué es lo
      que **no** la usa.
- [x] **El hogar de demostración**: una línea. Sus fechas eran relativas y no había
      nada que reescribir, pero salían de `CURRENT_DATE`, que depende de la zona de
      la sesión —la del servidor— y no de la del hogar. Se fija con `SET LOCAL`, que
      es la misma regla del hito aplicada al fichero que la enseña.

> **Lo que la implementación destapó y el plan no preveía.** Tres cosas. La
> primera es **un cuarto sitio que el plan no contaba**: `dueStatus()` en la
> pantalla de CMMS, que decide si un plan está «Al día», «Toca pronto» o «Se ha
> pasado» restando un `LocalDate` de `Date.now()`. No es una regla del dominio
> —quien avisa es el servidor— pero es lo que la persona lee, y en la franja
> entre dos medianoches pintaba «Se ha pasado» sobre un plan que toca hoy.
>
> La segunda es que **el día del hogar puede no estar todavía** cuando un
> formulario se pinta por primera vez, porque sale de una consulta. Se resuelve
> sin añadir ningún `Spinner`: el estado del campo arranca en nulo —«nadie lo ha
> tocado»— y lo que se pinta es la elección de la persona o, si no la hay, el día
> del hogar en cuanto llega. Un valor inicial de verdad habría exigido tenerlo en
> el primer render, que es justo cuando puede faltar.
>
> La tercera es de utillaje: la prueba del reloj fijado
> **estrena contexto de Spring** —el bean del reloj lo pone `SecurityConfig` y no
> hay propiedad que lo mueva— y el reloj sustituto tiene que ser `@Primary`. Con
> el mismo nombre de bean sería una redefinición, que Spring Boot prohíbe salvo
> abriendo `spring.main.allow-bean-definition-overriding` para toda la clase —un
> interruptor que además taparía el próximo choque de nombres que sí sea un error.

### Hito 6 — Cierre del bloque · **Hecho** (2026-08-20)

No añade producto. Es corto a propósito, y existe porque las dos fases anteriores
enseñaron que lo que se deja para «al final de otro hito» no se cierra:

- [x] **La capacidad, vuelta a medir** en sus dos magnitudes, con las tablas nuevas
      dentro y con el criterio de retención al día. La siembra gana lo que el
      bloque añadió —seis etiquetas con la mitad de los duraderos etiquetados,
      estado de conservación en cuatro de cada cinco y las categorías con icono y
      color— y la pendiente pasa de 116 394 a **141 994 B por hogar** y de 2457 a
      **2525 B por día**. **VPS-3 sigue en pie y sigue decidiéndolo el disco**, y
      el criterio de retención sigue como estaba: cerrado para cuatro tablas por
      la baja del hogar, abierto solo para `household_notices`, y `event_outbox`
      sin ser una sexta. La medición destapó además una precisión que faltaba:
      la cola del outbox aparece quinta en la tabla por tamaño **sin retener
      nada** — es la huella física del pico de la siembra, porque PostgreSQL no
      devuelve las páginas que una tabla llegó a ocupar.
- [x] **La lista de pantallas de la auditoría de accesibilidad**, renombrada:
      `PHASE_TWO_SCREENS` pasa a **`AUDITED_SCREENS`**, el rótulo de la prueba
      deja de decir «las diez pantallas» —eran once— y el docstring que describía
      «las seis pantallas de la Fase 2» —que además había quedado huérfano
      delante de otra prueba— se muda junto a la suya y describe lo que hay.
- [x] **Y las dos pantallas que faltaban entran, que la ficha no pedía y se
      decide aquí**: «Sitios» y «Personas» eran las dos últimas del core sin
      auditar, y un hito que existe porque «lo que se deja no se cierra» no deja
      una deuda contada en un comentario. Con una ubicación sembrada para Sitios
      —una pantalla vacía se audita sola— y a un coste de ~4 s de CI. **Desde
      este hito no queda ninguna pantalla de la navegación fuera de la pasada
      sistemática.**
- [x] **Los punteros hacia adelante** de [`roadmap.md`](roadmap.md) y
      [`phase-2-roadmap.md`](phase-2-roadmap.md), que pasan de «hay un plan» a
      «está hecho» con una nota nueva cada uno y su fila de historial, sin
      reescribir lo que aquellas fases dejaron dicho.
- [x] **[`decisions.md`](decisions.md), comprobado y sin nada que escribir**: las
      cinco preguntas del bloque están resueltas, cada una en su hito y con su
      alternativa descartada, y la tabla de más abajo las tiene tachadas con su
      fecha. El trabajo aquí era verificar que está completo, y lo está.
- [x] **El README**: la línea de «Fase actual», la fila del bloque en la sección
      8 —de «En curso» a **Completado**—, el cierre en la 8.4 y la fila de
      historial de la 10. La 4.1.7 ya estaba al día, hito a hito.
- [x] **`CLAUDE.md`, que la ficha no listaba y es parte del cierre**: es el
      fichero que lee toda sesión nueva y seguía contando el repositorio de antes
      del bloque — 98 operaciones, 28 tablas, siete recorridos, «las nueve ADR» y
      un `PHASE_TWO_SCREENS` que ya no existe. Ahora dice 106, 31, diez, quince y
      `AUDITED_SCREENS`, y cuenta el bloque como cerrado.
- [x] **El hogar de demostración, comprobado y sin nada que tocar**: ya trae las
      etiquetas, las categorías con cara y el estado de conservación desde los
      Hitos 3 y 4, y su zona de calendario fijada con `SET LOCAL` desde el 5.
      Este hito no añade producto, así que tampoco le añade nada que enseñar.
- [x] **El deck de [marketing](../marketing/README.md)**, que son cuatro piezas y
      no una, regeneradas desde sus generadores —nunca a mano— con las cifras de
      hoy verificadas contra el repositorio: `grep -c operationId` da 106, las
      migraciones crean 31 tablas y las ADR son quince. La pieza comercial
      mantiene su decisión escrita de **no contar el bloque** —queda por debajo
      de la altura a la que habla— y las cuatro conservan la diapositiva de
      agradecimiento que la licencia de Slidesgo exige. Verificadas con
      `qa-deck.py` y `preview-pptx.py`, y mirando el render: el único desborde
      nuevo —un pie de cifra en tres líneas— se corrigió en el generador.

## Criterio de aceptación

Casi todo está ya escrito en las ADR y basta con consolidarlo. Las cuatro últimas
filas son lo que este bloque añade.

| Origen | Qué debe demostrarse |
|---|---|
| [ADR-001](../architecture/decisions/ADR-001-solution-architecture-baseline.md) | Cada hito con frontend tiene su recorrido vertical de la pantalla a PostgreSQL, con pruebas en los tres niveles. El Hito 1 no lo tiene, y se dice por qué |
| [ADR-002](../architecture/decisions/ADR-002-multi-tenancy-and-backend-framework.md) | Autenticado como hogar A, ninguna operación nueva devuelve ni modifica datos del hogar B — ni las de baja, que son las que más daño harían |
| [ADR-003](../architecture/decisions/ADR-003-row-level-security.md) | Toda tabla nueva lleva `household_id`, RLS y `FORCE`; **el relay del outbox va hogar a hogar sin `BYPASSRLS`**, igual que el recorrido diario |
| [ADR-004](../architecture/decisions/ADR-004-database-migrations.md) | Un arranque en limpio sobre una base vacía produce el esquema entero, políticas y funciones acotadas incluidas |
| [ADR-005](../architecture/decisions/ADR-005-local-file-storage.md) | Dar de baja un hogar **no deja un solo byte suyo en disco**, y un fallo a mitad del borrado no deja nunca una fila viva apuntando a bytes que ya no están |
| [ADR-006](../architecture/decisions/ADR-006-frontend-stack-and-design-system.md) | Las pantallas nuevas pasan teclado, foco, contraste aplicado, reflujo y axe en los dos modos — **con el color de categoría puesto** |
| [ADR-007](../architecture/decisions/ADR-007-openapi-contract-as-source-of-truth.md) | Toda operación nueva está en el contrato con su `operationId`, y el validador pasa |
| [ADR-010](../architecture/decisions/ADR-010-module-boundaries-and-activation.md) | El outbox es de plataforma y **no nombra a ningún módulo**; la lista de excepciones de ArchUnit sigue teniendo un solo nombre; activar un módulo lo sigue sembrando desde el estado |
| [ADR-011](../architecture/decisions/ADR-011-scheduled-checks-and-notice-delivery.md) | La purga del hogar es **una `ScheduledCheck` más** del recorrido que ya existe, y el aviso de la gracia no se repite cada noche |
| **ADR-012** (nueva) | Solicitada la baja, el hogar sigue funcionando y se puede cancelar; vencida la gracia, no queda ni una fila ni un fichero suyos. Una identidad sin ninguna pertenencia no sobrevive por accidente |
| **ADR-013** (nueva) | Publicado un evento y caído el proceso antes de entregarlo, el handler lo recibe al arrancar. La entrega **sigue siendo at-least-once**, y los handlers siguen siendo idempotentes |
| **ADR-014** (nueva) | Una foto tomada por un iPhone con los ajustes de fábrica se sube y se ve, y el coste de que así sea está medido y escrito |
| **ADR-015** (nueva) | Todo par de color que un usuario pueda producir está en la lista de `scripts/check-contrast.py`, y el script falla si alguno baja de WCAG AA |
| **ADR-011** (ampliada) | Con el reloj en las 23:30 UTC, un hogar en `Europe/Madrid` registra una intervención con **su** fecha de hoy y el servidor la acepta; las dos comprobaciones periódicas resuelven su día con la zona del hogar y no con la del despliegue |

## Preguntas que este trabajo tiene que resolver

Se resuelven en el hito que las toca y se anotan en [`decisions.md`](decisions.md),
no aquí.

| Pregunta | Hito | Por qué vence ahí |
|---|---|---|
| ~~**La identidad que se queda sin ninguna pertenencia: ¿baja lógica o borrado real?**~~ **Resuelta (2026-08-20): borrado real**, en la [ADR-012](../architecture/decisions/ADR-012-data-erasure-household-closure-and-account-closure.md) y en [`decisions.md`](decisions.md). Conservarla retiene datos personales de alguien que ya no puede entrar y **no libera su correo** —el índice único dejó de ser parcial por baja—, así que esa persona no puede volver nunca. Borrarla es lo que `PurgeUnverifiedHouseholds` ya hace, pero allí no había nada que conservar | 0 | Es lo que la baja de hogar produce, y sin respuesta no se puede escribir su purga |
| ~~**¿El outbox es el único camino de entrega, o convive con la entrega in-process?**~~ **Resuelta (2026-08-20): convive**, en la [ADR-013](../architecture/decisions/ADR-013-transactional-outbox.md) y en [`decisions.md`](decisions.md). Un solo camino volvería **asíncrona respecto a la petición** una entrega que hoy no lo es, en los cuatro módulos a la vez y sin ninguna pantalla que lo pida; el relay queda como camino de recuperación, con un periodo de gracia para no pisar al reparto en el acto | 1 | De ello depende si `AFTER_COMMIT` sigue significando algo en `ModuleEventHandler` |
| ~~**¿La fila entregada se borra o se conserva?**~~ **Resuelta (2026-08-20): se borra.** El outbox es una cola y no un archivo, así que **no hay sexta tabla**: su estado normal es vacía y su tamaño es el indicador. Conservarla habría dado un registro de lo publicado a cambio de una segunda copia de cada `payload` y de una retención inventada para una necesidad que nadie ha expresado | 1 | La medición de capacidad distingue lo que crece con lo que el hogar tiene de lo que crece con lo que hace, y esto es lo segundo |
| ~~**HEIC: ¿cliente o servidor?**~~ **Resuelta (2026-08-20): el cliente**, en la [ADR-014](../architecture/decisions/ADR-014-heic-conversion.md) y en [`decisions.md`](decisions.md). Con las dos medidas delante, y las dos cambiaron el resultado que se esperaba: el megabyte no cae sobre el bundle sino sobre un fragmento que solo descarga quien elige un HEIC —**2,49 kB** sobre la primera carga—, y el camino del servidor no era una dependencia más sino **JDK 22 y `libheif` del sistema**, con el proyecto en 17. La tercera salida —no convertir— se evaluó y se descartó por escrito | 2 | Es la decisión del hito, y su ADR no se puede escribir sin ella |
| ~~**La etiqueta: ¿catálogo por hogar o columna de texto?**~~ **Resuelta (2026-08-20): catálogo por hogar**, en la [ADR-015](../architecture/decisions/ADR-015-user-chosen-category-identity.md) y en [`decisions.md`](decisions.md). Las tres cosas que la decidían se midieron una a una y las tres tienen su prueba: renombrar es una fila frente a recorrer todos los assets de la casa; deduplicar sin mayúsculas ni acentos lo hace el índice normalizado que ya protege categorías y artículos, y sobre texto no hay ninguna fila común donde ponerlo; y autocompletar es un `SELECT` sobre una tabla pequeña frente a un `DISTINCT` sobre un campo repetido tantas veces como cosas tenga la casa | 4 | Es lo que decide la migración, y con datos dentro cambiarla cuesta otra |
| ~~**El «hoy» de una regla de calendario: ¿el del hogar o el de UTC?**~~ **Resuelta (2026-08-20): el del hogar**, en la [ADR-011 ampliada](../architecture/decisions/ADR-011-scheduled-checks-and-notice-delivery.md) y en [`decisions.md`](decisions.md). Se decide por lo que significa el tipo y no por la recomendación del plan: `performedOn` y `nextDueOn` son `LocalDate`, y un día de calendario no significa nada sin una zona. UTC **parece la opción neutral y no lo es** —es la zona del despliegue—, y su precio es que un hogar peninsular no pueda apuntar de madrugada lo que acaba de hacer. Sí hace falta el puerto: `HouseholdCalendar`, declarado en plataforma e implementado por el core, con la lista de excepciones de ArchUnit intacta | 5 | De ella depende si el puerto hace falta, y es lo único que ese hito decide |

## Lo que sigue fuera, y por qué

- **El análisis antivirus** de lo subido, que es la cuarta de la lista que dejó
  abierta la Fase 1 y **no entra aquí**. Su motivo no ha cambiado: es la defensa
  que toca añadir el día que un fichero pueda **salir del hogar que lo subió**, y
  nada de este bloque abre esa puerta. Un `clamd` residente cuesta del orden de
  1,5 GB de memoria, una fracción notable de la máquina prevista.
- **La purga de las cinco tablas que crecen sin techo.** Tiene criterio de
  retención, disparador y un número delante desde el cierre de la Fase 2, y su
  respuesta para cuatro de las cinco es precisamente la baja del hogar, que sí
  entra. Escribir hoy un borrado por antigüedad sobre el historial de una casa
  seguiría siendo resolver el problema equivocado.
- **La reducción de tamaño en el cliente y la cola de varias subidas**, que la
  ficha de `upload-field` lista junto a HEIC. Se parecen porque están en la misma
  pantalla, pero ninguna es un hueco: son mejoras que nadie ha decidido hacer.
- **La Fase 3 — los nueve módulos restantes de 4.2.** Planificarla es su propia
  sesión y su propio pull request, y este bloque no la adelanta ni la condiciona:
  ninguno de los nueve pide nada de lo que hay aquí.
- **El despliegue**, que es lo que separa «En desarrollo» de «En producción» y no
  es una tarea de ingeniería sino una decisión que nadie ha tomado.

  > **Tomada el 2026-08-21**: DRP corre en producción en el VPS, con la
  > [ADR-016](../architecture/decisions/ADR-016-production-deployment.md) y su
  > manual ([`deployment.md`](../../backend/operations/deployment.md)). De esta
  > lista quedan abiertos, cada uno con su motivo intacto, el análisis
  > antivirus y la Fase 3.

## Historial

| Fecha | Cambio |
|---|---|
| 2026-08-21 | Se anota, sin tocar lo que el bloque decidió, que **el despliegue —listado aquí como fuera del bloque— está hecho**: la [ADR-016](../architecture/decisions/ADR-016-production-deployment.md) y [`deployment.md`](../../backend/operations/deployment.md), con DRP corriendo en el VPS. |
| 2026-08-20 | **Hito 6 cerrado, y con él el bloque entero**: siete hitos y siete pull requests en dos días, con las quince ADR del proyecto escritas y los cuatro huecos —más el quinto que apareció por el camino— saldados. El hito no añade producto: consolida, y lo que consolidar destapó esta vez no fueron defectos de código sino **medidas y nombres que ya no describían lo que había**. La capacidad, vuelta a medir con lo que el bloque añadió dentro: **141 994 B por hogar y 2525 B por día** —desde 116 394 y 2457—, con VPS-3 en pie, el criterio de retención intacto y una precisión nueva: `event_outbox` aparece quinta en la tabla de tamaños **sin retener nada**, porque el tamaño físico de una cola es el de su pico y no el de su contenido. `PHASE_TWO_SCREENS` pasa a **`AUDITED_SCREENS`** —el nombre era falso por partida doble y el rótulo «las diez pantallas» contaba once— **y entran «Sitios» y «Personas»**, que la ficha no pedía y se decide aquí: eran las dos últimas pantallas del core sin auditar, y con ellas **la pasada sistemática cubre la navegación entera**, trece pantallas. Los punteros hacia adelante de las dos fases pasan de «hay un plan» a «está hecho» sin reescribir nada; `decisions.md` se comprueba y estaba completo; el README pasa el bloque a **Completado** en sus cuatro sitios; **`CLAUDE.md` deja de contar el repositorio de antes del bloque** —98, 28, siete y nueve donde hoy son 106, 31, diez y quince—; y las cuatro piezas del deck se regeneran desde sus generadores con las cifras verificadas contra el repositorio, la atribución de Slidesgo en su sitio y la decisión de la pieza comercial —no contar el bloque— mantenida y dicha |
| 2026-08-20 | **Hito 5 cerrado**: el «hoy» de las reglas de calendario pasa a ser **el del hogar**. Es el único hito del bloque que no venía del plan —salió de ejecutar el Hito 1— y el más pequeño: **sin migración, sin operación nueva y sin pantalla nueva**, con el modelo quieto en 31 tablas y el contrato en 106 operaciones. **La pregunta se decide por lo que significa el tipo** y no por venir recomendada: `performedOn` y `nextDueOn` son `LocalDate`, y un día de calendario no significa nada sin una zona; UTC parece la opción neutral y no lo es, porque es la del despliegue. **No hay ADR nueva y eso es parte de la decisión**: la **ADR-011 se amplía** con una sección hacia adelante que separa lo que su sección 7 confundía —a qué hora corre el recorrido es del despliegue, **qué día mira es del hogar**— y la decisión del 2026-08-17 sobre el vencimiento **no se reabre**, porque su frontera real era `Instant` frente a `LocalDate`. Llega `HouseholdCalendar`, un puerto que declara plataforma y implementa el core leyendo `households.time_zone`, con la misma inversión que `HouseholdDirectory` y `NoticeRecipients` y **con la lista de excepciones de ArchUnit intacta**; se descarta llevar la zona en `SessionClaims`, que la engordaría y no sirve en el recorrido diario, que no tiene sesión. Con las tres líneas idénticas se va el `?: ZoneId.systemDefault()`, que era código muerto y prometía un respaldo imposible: el puerto **falla ruidosamente** sin contexto de inquilino. En el cliente cambian `today()`, `inMonths()` y **un cuarto sitio que el plan no había visto**, `dueStatus()`, que restaba un día de calendario menos `Date.now()`. La regresión se fija **con el reloj parado** —23:30 UTC, hogar en `Europe/Madrid`—, comprobada dando `400` sin el cambio, y con dos pruebas que ninguna suite tenía: que el instante elegido de verdad cambia de día, y que **el mismo instante da dos días en dos hogares**. El recorrido vertical de CMMS gana el caso, y con él lo único que el backend no puede decir: **desde un navegador que está en otro día, el campo sigue enseñando el del hogar**. Se corrigen las **tres** frases que justificaban `timeZone` por «el proceso diario de vencidos» —el plan había localizado una— y el hogar de demostración gana una línea: sus `CURRENT_DATE` salían de la zona del servidor |
| 2026-08-20 | **Hito 4 cerrado**: las **etiquetas libres** y el **icono y color de una categoría**, que son los dos atributos que quedaban de los cuatro que 4.1.7 llevaba desde el 2026-08-09 dejando fuera — con lo que esa propuesta queda cerrada entera. Trae la **ADR-015**, que extiende la ADR-006 sin reescribirla y le añade su sección hacia adelante, la migración `V17` que lleva el modelo de **29 a 31 tablas** y **cuatro operaciones** que llevan el contrato de 102 a **106**. **La pregunta del hito se resuelve con las tres cosas medidas**: la etiqueta es un **catálogo por hogar** porque el texto libre no se puede renombrar de una vez, ni deduplicar sin distinguir mayúsculas ni acentos —no hay ninguna fila común donde poner la restricción—, ni autocompletar sin recorrer los assets enteros. Etiquetar **no gana operación**: viaja en `tagIds`, que es absoluto como la cantidad de una existencia, porque una operación más sería una segunda escritura sobre el inventario para hacer lo que el `PATCH` ya hace. El icono y el color van dentro de un **juego cerrado** —dieciséis y seis— y esa es la ADR entera: un color libre no está en ningún token, así que **no lo mide nadie** y sería lo único de la interfaz cuyo contraste se afirma en vez de comprobarse; `check-contrast.py` pasa de **36 pares a 48** y tres de los doce valores nuevos nacieron fuera del gamut sRGB. Se cierra además la decisión abierta de **`lucide-react`**, con el número delante —5,88 kB sobre la primera carga, 2,63 comprimidos— y se migran los cuatro iconos que llevaban dos fases dibujados a mano; detrás aparece una contradicción que la dependencia tapaba y que **no se resuelve aquí**: `iconography.md` y `status-badge.md` dicen lo contrario sobre el icono de estado. **«Catálogo» entra en la auditoría sistemática** con una categoría de color puesto, y el recorrido **mide el contraste ya aplicado** en los dos modos, que es lo único que demuestra que el par medido es el que llega. Lo que la implementación destapó: el índice de `tags` **no puede ser parcial por retirada** y lo que lo sustituye es que crear una retirada **la revive**; **Chrome conserva `oklch()` en `getComputedStyle`**, lo que hizo que la primera versión del medidor diera 1,00:1 para todo; y dos defectos de accesibilidad que solo aparecieron al escribir las pruebas —JSX se come el espacio inicial de la línea, y el selector puede estar dos veces en la misma pantalla—. La categoría **no entra en la navegación móvil**, que el plan pedía, porque en la navegación no hay ninguna categoría; a cambio se cumple la promesa que `iconography.md` llevaba desde la Fase 1 sobre el hueco de una foto que falta |
| 2026-08-20 | **Hito 3 cerrado**: el **estado de conservación** de un asset y la **condición en la entrega y la devolución** de un préstamo, que son dos de los cuatro atributos que 4.1.7 llevaba desde el 2026-08-09 dejando fuera. **Sin ADR y sin operación nueva**: tres columnas anulables en la `V16`, seis esquemas ensanchados y el contrato quieto en **102 operaciones**, que era la condición del plan. Lo que sí trae son ocho decisiones de producto. **Una sola escala de cinco valores** para los tres campos, porque el motivo entero de la condición en préstamo es poder decir «salió bien y volvió rayado» y dos escalas distintas no se comparan; **solo sobre un `DURABLE`**, ampliando la restricción que ya cubría el número de serie en vez de añadir una segunda; y **la devolución no toca el asset**, que es la decisión que más se discutió: propagarla le daría a un token acotado —el de quien no tiene cuenta— una escritura sobre el inventario, y CMMS ya había declarado que registrar una intervención «no toca el asset». La vista externa **se ensancha por primera vez** desde que se escribió, de siete campos a nueve, y con su motivo: las dos condiciones describen la cosa que quien pregunta tiene en las manos, no el hogar que se la prestó. La devolución gana un **cuerpo opcional** en lugar de una operación nueva, porque una operación más sería una segunda escritura que el token acotado tendría que alcanzar; hay una prueba que le manda cinco campos de más y comprueba desde dentro de casa que ninguno se escribió. **La condición no se pinta con color** —dos distintivos en una fila es antiuso declarado, y la paleta de dominio tiene cinco tonos elegidos para sobrevivir a una deuteranopia— así que `check-contrast.py` sigue midiendo 36 pares. «Inventario» entra en la auditoría sistemática, con un asset sembrado, y el hogar de demostración trae las tres columnas puestas con diez duraderos sin anotar a propósito. Y se destapa algo ajeno: **la pantalla de Préstamos era el único fichero del frontend pintado con tokens que no existen**, `text-muted` y `border-line`, que no generan ni una regla de CSS |
| 2026-08-20 | **Hito 2 cerrado**: la **conversión de HEIC**, con la **ADR-014** y las dos medidas delante en lugar de una opinión. **Gana el cliente**, que es lo que 5.8.3 ya decía —así que esa sección se confirma y no se enmienda—, y las dos cifras llegaron cambiando lo que el plan esperaba: **el megabyte no cae sobre el bundle** —en un `import()` dinámico son 2,49 kB sobre la primera carga y 2 995 kB para quien elige un HEIC—, y **el camino del servidor no era una dependencia más sino un cambio de plataforma**, porque el único plugin de ImageIO para HEIF exige JDK 22 con el proyecto en 17, además de `libheif` en la imagen y en el runner, y de ×3,4 a ×5,6 sobre la operación que ya era la cara. **La tercera salida se evalúa y se descarta por escrito**: pedir que se cambie el ajuste de la cámara cuesta cero y pierde porque no arregla la foto ya hecha, no toca el HEIC que llega de fuera del teléfono y se paga en la cuota del usuario. La trampa que el plan anunciaba se resolvió del otro lado —**HEIC no llega nunca a `files.content_type`**, así que no hay migración ni cambio de contrato— y aparece **una tercera magnitud de capacidad**: lo que cuesta llegar al navegador. Se decide además `heic-to` frente al que pesa la mitad, por decodificar fuera del hilo principal y no necesitar `unsafe-eval`. Y se destapa algo ajeno: **`UploadField` documenta desde la Fase 1 un botón de Cancelar que nunca se construyó**, que se deja de afirmar aunque no se arregle aquí |
| 2026-08-20 | **Se añade el Hito 5, que no venía del plan**: ejecutando el Hito 1 se destapó que las tres reglas de calendario del proyecto —la que rechaza una intervención «del futuro» y las dos comprobaciones de fecha de CMMS y Warehouse— resuelven su «hoy» contra la **fecha UTC** y no contra la del hogar, con la misma línea copiada en los tres sitios. La consecuencia es que un hogar peninsular **no puede registrar de madrugada lo que acaba de hacer**: entre la medianoche local y la de Greenwich, la fecha de hoy es futuro para la aplicación. El frontend arrastra el mismo criterio y lo empeora, porque el campo de fecha sale relleno con ayer y el selector no ofrece hoy. Se anota como hito propio y no dentro del cierre del bloque porque **cambia una regla de dominio** y el cierre no añade producto; el de cierre pasa a ser el **6**. La pregunta que decide —día del hogar o día de UTC— queda asignada al Hito 5, y **no se da por resuelta aquí**. La parte de las pruebas ya la arregló el Hito 1, alineando su «hoy» con el de la aplicación; lo que queda es si ese «hoy» es el correcto |
| 2026-08-20 | **Hito 1 cerrado**: el **Transactional Outbox**, con la **ADR-013**, la migración `V15` —`event_outbox` y la sexta función acotada— y el relay con su periodo en segundos y su propio interruptor. Se resuelven las dos preguntas que el plan le había asignado —**convive** con la entrega en el acto, y **la fila entregada se borra**— y se deciden otras seis por el camino. La trampa que el plan anunciaba estaba donde decía y era de una línea: el `try/catch` de `publish` habría pasado de proteger al core a **tragarse el fallo de escribir la fila**, perdiendo el evento en el silencio exacto que el outbox viene a impedir. Sacarla del `try` —y no confirmar cuando el reparto lanza— cierra de paso **la peor limitación conocida del bus**, medida desde la Fase 1 y hasta hoy sin respuesta: un `@EventListener` a pelo que revienta dejaba sin evento a los handlers que iban detrás. Lo que la implementación destapó y el plan no preveía: **el `@Order` de la clase base no puede ser `@Order(0)`** —adelantaría a los handlers respecto a listeners con orden declarado, y una prueba de la Fase 1 lo midió— así que va un escalón por delante de la confirmación y no a la cabeza; **publicar un evento de un hogar que no existe deja de ser posible**, por la clave ajena, lo que obligó a sembrar un hogar de verdad en una prueba del bus que llevaba desde la Fase 1 usando un `UUID` inventado; y **`event_outbox` es la primera tabla del modelo que no puede estar llena**, así que la prueba de la cascada del Hito 0 —que exige que cada tabla con `household_id` tenga algo dentro antes de purgar— la llena **a mano** con una entrega que nadie va a confirmar, y de paso comprueba que lo pendiente de un hogar purgado se va con él. La decisión sobre `ModuleEventHandler` se cierra **con las dos mitades medidas** y ninguna se retira; la guarda de idempotencia se muda a la fila del outbox y **no a una tabla de `(handler, eventId)`**, porque no cerraría la ventana y ningún handler desplegado la necesita — los tres ya son idempotentes por construcción en sus propias tablas, y hay una prueba que lo mide entregando el mismo `eventId` a dos instancias del handler. **Sin frontend y sin tocar la batería E2E**, que es la excepción declarada del plan |
| 2026-08-20 | **Hito 0 cerrado**: la baja de hogar con treinta días de gracia, el cierre de cuenta con su avatar y `PurgeClosedHouseholds` dentro del recorrido que ya existía. Con ellos, la **ADR-012**, la migración `V14` —tres columnas y una función acotada más—, cuatro operaciones en el contrato y la zona de peligro con confirmación escrita. Se resuelve la pregunta que el plan le había asignado —la identidad huérfana **se borra de verdad**— y se deciden otras cinco por el camino, entre ellas la del último administrador. Se cierran de paso dos promesas ajenas: la de `PurgeUnverifiedHouseholds` sobre el directorio del hogar y el criterio de retención de cuatro de las cinco tablas sin techo. La auditoría sistemática destapó además que **el limitador de frecuencia no daba para dos pantallas más**, con un síntoma que no se parecía a la causa |
| 2026-08-19 | Se crea al planificar el cierre de los cuatro huecos que las Fases 1 y 2 dejaron abiertos a propósito: seis hitos, cuatro ADR nuevas, cinco preguntas con su hito asignado y las cuatro decisiones de arranque tomadas antes de empezar. Se decide **dónde encaja**: un bloque entre fases, ni un hito 0 de la Fase 3 ni una fase numerada |
