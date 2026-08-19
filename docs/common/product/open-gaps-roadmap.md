# Roadmap del cierre de huecos

| Campo | Valor |
|---|---|
| Estado | En curso — Hito 0 cerrado |
| Responsable | Equipo DRP |
| Ámbito | Los cuatro huecos que las Fases 1 y 2 dejaron abiertos a propósito |
| Última revisión | 2026-08-20 |

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

Son seis hitos y seis sesiones. **Si hay que partirlo, se parte entre el Hito 2 y
el Hito 3**: los tres primeros son huecos técnicos con una ADR cada uno, y los dos
siguientes son atributos de producto que no dependen de ellos. No se parte en
ningún otro sitio, porque cualquier otro corte deja una ADR escrita sin lo que la
valida, o un atributo a medio camino entre el dominio y la pantalla.

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

Once cosas que este trabajo se va a encontrar. No son avisos genéricos: cada una
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
      claves ajenas: las **veintitrés que llevan `household_id`**, las de los cuatro
      módulos incluidas, y aparte las **cinco que no lo llevan** (5.6), que son
      exactamente donde está el nudo. Una tabla que se quede fuera no da ningún
      error — deja filas de un hogar que pidió marcharse.
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

### Hito 1 — Transactional Outbox · **Pendiente**

- [ ] **ADR-013 — Transactional Outbox**: qué garantía cambia —**ninguna**, sigue
      siendo at-least-once—, el recorrido del relay sin `BYPASSRLS`, qué pasa con
      `AFTER_COMMIT` y `REQUIRES_NEW`, y por qué la siembra desde estado no cambia.
- [ ] **Migración `V15`**: `event_outbox` con `household_id`, RLS y `FORCE` como
      cualquier tabla del modelo, más la **cuarta función `SECURITY DEFINER`** en
      el rol `drp_resolver`, que devuelve **solo identificadores** de los hogares
      con entregas pendientes.
- [ ] **La publicación escribe la fila dentro de la transacción del core.** Es lo
      único que hace que el evento no se pierda, y es todo lo que el core nota: la
      firma de `EventBus` no cambia.
- [ ] **El relay**, con periodo propio medido en segundos y la forma de
      `DailySweep`: hogar a hogar, `app.household_id` en cada transacción, nunca
      `BYPASSRLS`.
- [ ] **La decisión sobre `ModuleEventHandler`, medida y no razonada.** Si
      `AFTER_COMMIT` deja de significar algo, se retira con una prueba delante; si
      se queda, se queda con la prueba que dice por qué.
- [ ] **La guarda de idempotencia se muda**, que es lo que su propio comentario
      dice que pasa «el día que haya outbox».
- [ ] **La prueba que hoy no se puede escribir**: publicar, cortar el proceso antes
      de entregar, arrancar y comprobar que el handler recibe. El testigo es el
      **módulo de prueba** del Hito 0 de la Fase 2, que vive en el árbol de pruebas
      y existe exactamente para esto.
- [ ] **Medida de capacidad**: si la fila entregada se conserva, `event_outbox` es
      una **sexta** tabla que crece con lo que el hogar hace, y entra en la lista de
      la purga con su criterio de retención.
- [ ] **Este hito no tiene frontend, y no se le inventa uno.** Nada de lo que hace
      es visible en una pantalla, y añadir una para cumplir la forma sería añadir
      código que no defiende nada. Su recorrido vertical es dominio → aplicación →
      adaptador → PostgreSQL, y la batería E2E no se toca.

### Hito 2 — Conversión de HEIC · **Pendiente**

- [ ] **Las dos medidas, y antes de decidir**: el peso real del decodificador wasm
      sobre el bundle vuelto a medir, y el coste en servidor con los números del
      runner de la CI. Las dos quedan escritas donde viven los números, no en una
      conversación.
- [ ] **ADR-014 — Conversión de HEIC**, escrita **con las medidas delante** y con
      las tres salidas evaluadas, incluida la de no convertir.
- [ ] **La implementación del camino que gane**, entera: no hay media conversión.
- [ ] **Si gana el servidor, la enmienda de 5.8.3 es parte del hito** —con su
      motivo—, y la ADR-005 se enlaza hacia adelante desde la nueva, nunca se
      reescribe. Si gana el cliente, lo que se cierra es la nota de la ficha de
      [`upload-field`](../../frontend/design-system/components/upload-field.md), y
      su sección «Lo que falta» pierde la primera línea.
- [ ] **El mensaje del `415`**, que hoy enumera los tipos admitidos y seguirá
      haciéndolo para todo lo demás.
- [ ] **Recorrido vertical**: subir una foto HEIC de verdad —un fichero de prueba,
      no un JPEG renombrado— y verla en la galería y en la ficha del asset.

### Hito 3 — Estado de conservación y condición en préstamo · **Pendiente**

Los dos atributos que no traen entidad nueva. Van juntos porque son el mismo tipo
de cambio: un enumerado corto que nace en el dominio y llega hasta un desplegable.

- [ ] **Migración `V16`**: la condición del asset y las dos del préstamo —en la
      entrega y en la devolución—, todas anulables. Nulo no es un hueco: significa
      que nadie lo anotó.
- [ ] **Dominio y casos de uso**: `CreateAsset` y `UpdateAsset`, `StartLoan` y
      `ConfirmReturn`. La condición de devolución solo se admite al confirmarla,
      que es cuando se sabe.
- [ ] **El contrato no gana operaciones: ensancha esquemas.** El validador se
      ejecuta igual, y los ejemplos de 5.4.3 se actualizan con ellos.
- [ ] **Frontend**: el campo en el alta y en la edición del asset, el filtro en el
      listado y los dos momentos del préstamo. La vista externa del préstamo
      también lo ve — es donde el receptor dice en qué estado lo devuelve.
- [ ] **Documentos**: 4.1.1, 4.1.5, 5.6, 5.7 y los ejemplos JSON.
- [ ] **Barrido de aislamiento y auditoría de accesibilidad**, que las pantallas
      heredan por la lista del recorrido vertical.

### Hito 4 — Etiquetas e identidad visual de las categorías · **Pendiente**

- [ ] **ADR-015 — Color e icono elegidos por el usuario dentro de una paleta
      certificada**: por qué el juego es cerrado, cómo entra en
      `scripts/check-contrast.py` y qué pasa el día que alguien quiera abrirlo.
      Extiende la
      [ADR-006](../architecture/decisions/ADR-006-frontend-stack-and-design-system.md),
      que no se reescribe.
- [ ] **Migración `V17`**: la etiqueta y su relación con el asset, **dos tablas más
      con `household_id`, RLS y `FORCE`**, más el icono y el color en `categories`.
      Con ellas y con la del Hito 1, el modelo pasa de 28 a 31.
- [ ] **La forma de la etiqueta**, que es la pregunta de este hito: catálogo por
      hogar frente a una columna de texto. Decidida con lo que cuesta cada una al
      renombrar, al deduplicar sin distinguir mayúsculas y al autocompletar.
- [ ] **Las operaciones que haga falta** en el contrato: listar, crear, retirar y
      el filtro de `ListAssets`. Con `operationId`, que es lo que la
      [ADR-007](../architecture/decisions/ADR-007-openapi-contract-as-source-of-truth.md)
      necesita.
- [ ] **La ficha del componente antes del componente**, en el registro del sistema
      de diseño: el campo de etiquetas y el selector de icono y color. Es la regla
      que la Fase 2 aplicó a los módulos y que en la Fase 1 pagó dos veces.
- [ ] **`scripts/check-contrast.py` con los pares nuevos.** Un color de categoría
      que no esté en esa lista es un color que nadie mide.
- [ ] **Frontend**: las etiquetas en el asset y en su filtro, y la categoría con su
      icono y su color en el catálogo, en los listados y en la navegación móvil,
      que es donde un icono se gana el sitio.
- [ ] **Recorrido vertical y auditoría axe en los dos modos**, con el color puesto:
      es el único hito de este bloque en el que la accesibilidad puede romperse por
      un dato del usuario y no por una decisión del sistema.

### Hito 5 — Cierre del bloque · **Pendiente**

No añade producto. Es corto a propósito, y existe porque las dos fases anteriores
enseñaron que lo que se deja para «al final de otro hito» no se cierra:

- [ ] **La capacidad, vuelta a medir** en sus dos magnitudes, con las tablas nuevas
      dentro y con el criterio de retención al día.
- [ ] **La lista de pantallas de la auditoría de accesibilidad**, que se llama
      `PHASE_TWO_SCREENS` y ya no contiene solo pantallas de la Fase 2: o se
      renombra, o deja de describir lo que contiene. **El Hito 0 lo agravó a
      propósito**, metiendo en ella «Tu hogar» y «Tu cuenta», que son del core:
      el nombre ya es directamente falso.
- [ ] **Los punteros hacia adelante** de [`roadmap.md`](roadmap.md) y
      [`phase-2-roadmap.md`](phase-2-roadmap.md), que pasan de «hay un plan» a «está
      hecho», sin reescribir lo que aquellas fases dejaron dicho.
- [ ] **[`decisions.md`](decisions.md)**, con las preguntas de este bloque resueltas
      y su alternativa descartada.
- [ ] **El README**: la sección 8, la 4.1.7 y la fila de historial de la 10.
- [ ] **El deck de [marketing](../marketing/README.md)**, que resume el README y no
      avisa cuando se queda atrás.

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

## Preguntas que este trabajo tiene que resolver

Se resuelven en el hito que las toca y se anotan en [`decisions.md`](decisions.md),
no aquí.

| Pregunta | Hito | Por qué vence ahí |
|---|---|---|
| ~~**La identidad que se queda sin ninguna pertenencia: ¿baja lógica o borrado real?**~~ **Resuelta (2026-08-20): borrado real**, en la [ADR-012](../architecture/decisions/ADR-012-data-erasure-household-closure-and-account-closure.md) y en [`decisions.md`](decisions.md). Conservarla retiene datos personales de alguien que ya no puede entrar y **no libera su correo** —el índice único dejó de ser parcial por baja—, así que esa persona no puede volver nunca. Borrarla es lo que `PurgeUnverifiedHouseholds` ya hace, pero allí no había nada que conservar | 0 | Es lo que la baja de hogar produce, y sin respuesta no se puede escribir su purga |
| **¿El outbox es el único camino de entrega, o convive con la entrega in-process?** Un solo camino deja una sola semántica que razonar y añade la latencia del relay; convivir entrega al instante y obliga a decidir qué significa una fila que el otro camino ya entregó | 1 | De ello depende si `AFTER_COMMIT` sigue significando algo en `ModuleEventHandler` |
| **¿La fila entregada se borra o se conserva?** Borrarla deja el outbox sin crecimiento; conservarla da un registro de lo publicado y convierte `event_outbox` en la **sexta** tabla sin techo, con su criterio de retención | 1 | La medición de capacidad distingue lo que crece con lo que el hogar tiene de lo que crece con lo que hace, y esto es lo segundo |
| **HEIC: ¿cliente o servidor?** Con las dos medidas delante, y no antes | 2 | Es la decisión del hito, y su ADR no se puede escribir sin ella |
| **La etiqueta: ¿catálogo por hogar o columna de texto?** El catálogo cuesta una tabla y una relación; el texto no se puede renombrar, ni deduplicar sin distinguir mayúsculas, ni autocompletar sin recorrer todos los assets | 4 | Es lo que decide la migración, y con datos dentro cambiarla cuesta otra |

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

## Historial

| Fecha | Cambio |
|---|---|
| 2026-08-20 | **Hito 0 cerrado**: la baja de hogar con treinta días de gracia, el cierre de cuenta con su avatar y `PurgeClosedHouseholds` dentro del recorrido que ya existía. Con ellos, la **ADR-012**, la migración `V14` —tres columnas y una función acotada más—, cuatro operaciones en el contrato y la zona de peligro con confirmación escrita. Se resuelve la pregunta que el plan le había asignado —la identidad huérfana **se borra de verdad**— y se deciden otras cinco por el camino, entre ellas la del último administrador. Se cierran de paso dos promesas ajenas: la de `PurgeUnverifiedHouseholds` sobre el directorio del hogar y el criterio de retención de cuatro de las cinco tablas sin techo. La auditoría sistemática destapó además que **el limitador de frecuencia no daba para dos pantallas más**, con un síntoma que no se parecía a la causa |
| 2026-08-19 | Se crea al planificar el cierre de los cuatro huecos que las Fases 1 y 2 dejaron abiertos a propósito: seis hitos, cuatro ADR nuevas, cinco preguntas con su hito asignado y las cuatro decisiones de arranque tomadas antes de empezar. Se decide **dónde encaja**: un bloque entre fases, ni un hito 0 de la Fase 3 ni una fase numerada |
