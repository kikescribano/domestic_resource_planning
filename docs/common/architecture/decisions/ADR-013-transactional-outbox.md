# ADR-013: Transactional Outbox

- Estado: accepted
- Fecha: 2026-08-20
- Responsables: Equipo DRP
- Ámbito: common
- Sustituye: Ninguna

## Contexto

El event bus de DRP es **in-process y sin estado**: `SpringEventBus` publica
sobre el `ApplicationEventPublisher` de Spring y los handlers corren en
`AFTER_COMMIT`, es decir, cuando la transacción del caso de uso ya se cerró. Eso
resuelve lo que la sección 5.2.2 del [README](../../../../README.md) pide —el
core persiste y responde con independencia de si un módulo falla— y deja una
ventana que nadie había cerrado:

> **Entre el `COMMIT` del caso de uso y el `AFTER_COMMIT` que reparte el evento,
> caerse significa perder el evento.** Sin error, sin traza y sin ningún síntoma
> salvo un módulo que no se enteró.

Mientras no hubo módulos, eso era teórico. Con **cuatro escuchando** desde la
Fase 2 deja de serlo: un `AssetQuantityChanged` perdido es un asiento que falta
en el cuaderno de Warehouse, y lo que se ve meses después es una cifra que no
cuadra y ninguna forma de saber por qué. La propia sección 5.2.2 lo nombraba
—«candidato de evolución: patrón Transactional Outbox»— y el
[cierre de la Fase 2](../../product/phase-2-roadmap.md) lo dejó como uno de los
huecos abiertos a propósito.

Tres cosas vienen dadas y esta ADR no las revisa:

1. **La siembra de un módulo se hace desde el estado actual y no reproduciendo
   eventos** ([ADR-010](ADR-010-module-boundaries-and-activation.md), sección 6),
   y el roadmap de la Fase 2 dice explícitamente que el día que haya outbox eso
   **no cambia**.
2. **Nada que no nazca de una petición puede usar `BYPASSRLS`**
   ([ADR-003](ADR-003-row-level-security.md)), y un relay periódico no nace de
   ninguna.
3. **El outbox es de plataforma y no puede nombrar a ningún módulo**
   ([ADR-010](ADR-010-module-boundaries-and-activation.md)): el core publica sin
   saber quién escucha, y esa es la propiedad entera del bus.

## Decisión

### 1. Lo que cambia es que el evento no se pierda. **La garantía no cambia**

La entrega era at-least-once y **sigue siendo at-least-once**. Los handlers
siguen teniendo que ser idempotentes, y `IdempotentEventHandler` sigue siendo la
clase de la que hereda todo handler.

Esto se escribe con estas palabras a propósito, porque la lectura contraria es
cara y verosímil: alguien retira la idempotencia de un handler —«ya no hace
falta, ahora hay outbox»— y el fallo aparece meses después en forma de **una
línea de la compra duplicada** que nadie sabe explicar. El outbox resuelve que un
evento **publicado** llegue; no promete que llegue una sola vez, y de hecho
**hace que las reentregas ocurran de verdad** por primera vez.

### 2. Publicar deja constancia dentro de la transacción de quien publica

`EventBus.publish` escribe una fila en `event_outbox` **uniéndose a la
transacción abierta**. Es toda la decisión del patrón: o se guardan el cambio de
estado y su evento, o no se guarda ninguno. **La firma de `EventBus` no cambia**,
que es todo lo que el core nota.

La tabla lleva `household_id`, RLS y `FORCE` como cualquier otra del modelo, que
pasa de 28 a **29 tablas**. La política hace aquí el mismo trabajo que en
`household_notices` y por el mismo motivo: quien lee estas filas **no nace de una
petición**.

### 3. El `catch` de `SpringEventBus` deja de cubrir la escritura

Es una línea y es la que más importa. `publish` envolvía la publicación en un
`try/catch` para que un suscriptor mal escrito no tumbara al core. En cuanto
publicar significa **escribir una fila**, ese mismo `catch` se tragaría el fallo
de escritura y **perdería el evento en silencio** — exactamente lo que el outbox
viene a impedir, y sin un solo error visible.

Así que la escritura va **fuera** del `try` y su fallo sube al caso de uso, que
es donde se puede deshacer la transacción entera. Y hay una segunda mitad: **un
reparto que lanza ya no se confirma**. Si el difusor cortó, los handlers que iban
detrás no recibieron nada, así que la fila se queda en la cola y el relay la
reparte después.

Eso cierra de paso la peor limitación conocida del bus, medida en
`EventBusSweepTest` desde la Fase 1 y hasta hoy sin respuesta: **un
`@EventListener` a pelo que revienta deja sin evento a los handlers que van
detrás**, porque corta el bucle del difusor antes de que lleguen a apuntarse.
Ahora lo reciben, unos segundos más tarde, por el relay.

### 4. El outbox **convive** con la entrega en el acto; no la sustituye

Es la primera de las dos preguntas que este hito tenía asignadas, y se decide por
convivencia:

- **`SpringEventBus` sigue repartiendo en el acto**, después del commit y antes
  de que la petición responda. Nada cambia para quien usa la aplicación.
- **`OutboxRelay` es el camino de recuperación**, y solo mira lo que nadie
  confirmó.

Se descartó **el outbox como único camino**, que es la forma canónica del patrón
y tiene a favor una sola semántica que razonar y un relay que, al ser el único
camino, no puede pudrirse sin que se note. Pesa más lo que cuesta: la entrega
pasaría a ser **asíncrona respecto a la petición**, de modo que crear una
existencia y abrir el cuaderno de Warehouse dejarían de ser consistentes entre sí
durante unos segundos. Eso no es un detalle de latencia sino un cambio en lo que
la aplicación garantiza al responder, y lo haría **en los cuatro módulos a la
vez**, sin ninguna pantalla nueva que lo justificara. También invalidaría el
cuerpo de comportamiento medido de `EventBusSweepTest`, que es conocimiento caro
y todavía cierto.

El precio de convivir es tener que decir **qué significa una fila que el otro
camino ya entregó**, y la respuesta la da la decisión siguiente: no significa
nada, porque no existe.

### 5. La fila entregada **se borra**. El outbox es una cola, no un archivo

Es la segunda pregunta del hito. Su estado normal es **vacía**, y que crezca es
el síntoma.

Se descartó **conservarla** con una marca de entregado, que daría un registro de
todo lo publicado. Se descarta por tres cosas:

- Sería una **segunda copia de cada `payload`**, indefinida, de hechos que ya
  guardan el estado del core y las tablas de los módulos —que es justo para lo
  que existen cuatro de las cinco tablas de historial.
- Convertiría `event_outbox` en una **sexta tabla que crece sin techo**, con un
  criterio de retención que habría que inventar para una necesidad que nadie ha
  expresado. La [medición de capacidad](../../../backend/operations/capacity-measurements.md)
  acaba de cerrar exactamente esa discusión para las otras cinco.
- Y perdería la propiedad más útil que tiene esta tabla: **su tamaño es el
  indicador**. Con las filas entregadas dentro, «cuántas hay pendientes» pasa a
  ser una consulta con filtro y la tabla deja de significar nada de un vistazo.

### 6. El relay reutiliza la forma de `DailySweep`, pero no su vehículo

**La forma sí**: hogar a hogar, fijando `app.household_id` en cada transacción y
**nunca con `BYPASSRLS`**.

**El vehículo no: no es una `ScheduledCheck`.** Un evento que tarda un día en
llegar a Warehouse no es una entrega diferida, es una entrega rota. El relay
tiene su propio periodo, **medido en segundos** (`drp.outbox.period`, cinco por
omisión), con `fixedDelay` y no `fixedRate` para que una pasada larga —mil
eventos atrasados tras un reinicio— no se solape consigo misma.

Y para no recorrer en vacío mil hogares cada pocos segundos, empieza por la
pregunta estrecha: **qué hogares tienen algo pendiente**. La responde una función
`SECURITY DEFINER` más, `list_households_with_pending_events`, la **sexta** de la
familia y con su mismo criterio de admisión, que es lo único que la hace
aceptable: **solo devuelve identificadores de hogar**, responde a una pregunta
cerrada y pertenece a `drp_resolver`, que no es superusuario y no tiene
`BYPASSRLS`. Una función que devolviera un dato dejaría de ser una grieta acotada
para ser un agujero.

### 7. El periodo de gracia, que es lo que evita duplicar el trabajo normal

El relay solo mira filas de hace más de `drp.outbox.grace` —treinta segundos por
omisión—. Sin esa ventana cogería por sistema filas que el reparto en el acto
está a punto de confirmar, y repartiría dos veces **en el caso normal** y no en
el excepcional. Lo que sobrevive a la ventana es, por definición, lo que nadie
repartió.

### 8. El relay garantiza el reparto, no el éxito de cada handler

Un handler que falla se aísla como siempre y **no se reintenta**. La fila es del
evento y no de cada suscriptor, así que reintentarla volvería a llamar a los que
sí acabaron. Reintentar por handler exigiría una fila por handler, que es la
tabla que la decisión 10 descarta.

Y el orden de las dos operaciones es el mismo que la
[ADR-011](ADR-011-scheduled-checks-and-notice-delivery.md) eligió para el resumen
diario, por el mismo motivo: **repartir primero y confirmar después**. Las dos
opciones son at-least-once imperfectas y hay que elegir cuál falla mejor;
confirmar antes y caerse pierde el evento para siempre —nadie vuelve a mirarlo—
mientras que repartir y caerse antes de confirmar lo repite. Repetir se nota y se
aguanta; perder, ni se nota.

### 9. `AFTER_COMMIT` y `REQUIRES_NEW` se quedan, con una prueba que dice por qué

La pregunta era legítima: con un relay que reparte **fuera** de la transacción
del core, las dos razones por las que `ModuleEventHandler` abre `REQUIRES_NEW` y
corre en `AFTER_COMMIT` dejan de tener el mismo sujeto, porque ya no hay
transacción del core a la que unirse. La respuesta, medida y no razonada:

- **`AFTER_COMMIT` sigue significando lo mismo en el camino en el acto**, que es
  el que atiende toda petición. No ha cambiado nada de él.
- **`fallbackExecution` pasa de conveniencia a imprescindible.** El relay reparte
  sin transacción abierta, así que sin esa bandera **el camino de recuperación
  entero no entregaría nada** — y no entregarlo se vería exactamente igual que no
  tener nada que entregar. Hay dos testigos que se diferencian en esa única
  línea, y solo el que la lleva recibe.
- **`REQUIRES_NEW` no se retira.** Ya no hay una transacción del core a la que
  unirse, pero sigue siendo lo que abre la transacción donde el módulo escribe, y
  sin ella el handler correría sin `app.household_id` y no vería ni escribiría
  ninguna fila. Se mide con la tabla del módulo de prueba y su política de RLS
  delante.

### 10. La guarda de idempotencia se muda: su mitad duradera es la fila

El comentario de `IdempotentEventHandler` decía desde la Fase 1 que su guarda
vivía en memoria «porque el bus también: un reinicio no reentrega nada —pierde el
evento—, así que persistirlo no protegería de nada que pueda pasar hoy», y
cerraba con «el día que haya outbox, esa guarda se muda con él». **Ese día es
este**, porque tras un reinicio el relay reentrega el mismo `eventId` y la guarda
en memoria está vacía.

Lo que se muda es **la mitad duradera**, y su casa es la fila del outbox: el
`eventId` es la clave primaria de `event_outbox`, la fila se reserva al publicar
y se borra al repartir, y esa es la única reserva que sobrevive a un reinicio. Lo
que queda en memoria es la mitad que **solo tiene sentido dentro de un proceso**
—varios hilos publicando el mismo evento, y un handler que republica el suyo y se
reentra—, que son los dos casos que `EventBusSweepTest` mide.

Se descartó **una tabla de `(handler, eventId)`**, que es la salida evidente, por
dos razones:

- **No cerraría la ventana, solo la estrecharía**, salvo que se escribiera dentro
  de la misma transacción que el efecto del handler. Y eso sería prometer
  **exactamente-una-vez por handler**, que es justo lo que la decisión 1 se niega
  a prometer.
- **No le haría falta a ningún handler que exista.** Los tres módulos desplegados
  ya son idempotentes **por construcción y en sus propias tablas**: un índice
  único por `event_id` en `warehouse_movements`, uno por asset en
  `maintenance_items` y uno parcial por artículo vivo en `shopping_list_items`.
  Esa es la reserva puesta donde sí puede ser transaccional con el efecto, y hay
  una prueba que lo mide entregando el mismo `eventId` a dos instancias del
  handler —que es lo que un proceso recién arrancado tiene: memoria vacía.

### 11. La siembra desde estado no cambia

La [ADR-010](ADR-010-module-boundaries-and-activation.md) manda sembrar un módulo
desde el estado actual del core y no reproduciendo eventos, y sigue siendo así.
Son dos problemas que se parecen y no son el mismo: **el outbox resuelve que un
evento publicado llegue; la siembra, que un módulo encendido hoy conozca lo de
antes de existir.** Reproducir un año de eventos para sembrar seguiría siendo
peor que leer el estado — y además no habría qué reproducir, porque la cola se
vacía al repartir.

### 12. Lo pendiente de un hogar purgado se va con el hogar

`event_outbox` cuelga de `households` con `ON DELETE CASCADE`, así que la purga
de la [ADR-012](ADR-012-data-erasure-household-closure-and-account-closure.md) se
lleva sus entregas pendientes. **Es lo correcto**: no hay a quién entregárselas,
y conservarlas dejaría filas de un hogar que pidió marcharse — con su `payload`
dentro.

Conviene decirlo en vez de descubrirlo, porque desde el Hito 0 del cierre de
huecos el recorrido diario no purga solo hogares sin verificar sino **hogares con
datos dentro**. Y el relay tiene que tolerar que un hogar desaparezca **entre que
lo lista y lo procesa**: lo hace sin ninguna defensa especial, porque la política
de RLS no deja ver ninguna fila de un hogar borrado y la consulta devuelve cero.

> **Y esto destapó que `event_outbox` es la primera tabla del modelo que no puede
> estar llena.** La prueba de la cascada de la ADR-012 comprueba, antes de purgar,
> que **cada** tabla con `household_id` tiene algo dentro — sin ese retrato, una
> tabla que la siembra no llegue a tocar pasaría por purgada sin que nadie la
> hubiera purgado. Con la fila que se borra al repartirse, un hogar recién llenado
> deja la cola **vacía**, que es justamente lo que se quería. Así que es la única
> tabla del modelo que esa prueba llena **a mano**, dejando una entrega que nadie
> va a confirmar; y con ella comprueba de paso lo que esta sección promete.

### 13. El relay tiene su propio interruptor, y hace falta que lo tenga

`drp.outbox.enabled`, encendido por omisión. **No cuelga de
`drp.schedule.enabled`**, y el motivo es concreto: `SchedulingEnabledTest`
enciende el programador a propósito para medir que la pasada diaria queda
registrada, y con el relay colgando de aquella propiedad empezaría a repartir
eventos **cada cinco segundos** dentro de ese contexto, sobre la base que toda la
suite comparte y a mitad de otra prueba.

Que esté apagado en las pruebas no se afirma «porque lo pone una propiedad» —un
`@ConditionalOnProperty` mal escrito compila igual— sino midiéndolo en los dos
sentidos, como ya se hace con el programador.

## Alternativas consideradas

- **El outbox como único camino de entrega.** La forma canónica del patrón. Ver
  la decisión 4: se descarta porque volvería asíncrona respecto a la petición una
  entrega que hoy no lo es, en los cuatro módulos a la vez y sin nada que lo
  pida.
- **Conservar la fila entregada** con una marca en lugar de borrarla. Ver la
  decisión 5.
- **Una tabla de idempotencia `(handler, eventId)`.** Ver la decisión 10.
- **Que el relay fuera una `ScheduledCheck` más del recorrido diario.** Ahorra el
  interruptor, el periodo y una clase, y convierte una entrega perdida en una
  entrega de mañana, que no es una entrega.
- **`FOR UPDATE SKIP LOCKED` en lugar del periodo de gracia.** Resuelve la
  concurrencia entre relays y **no** la carrera que aquí importa, que es contra
  el reparto en el acto: ese no tiene la fila bloqueada mientras reparte, porque
  reparte fuera de toda transacción. Y sostener el bloqueo durante el reparto
  obligaría a repartir dentro de una transacción, que es justo lo que la decisión
  9 evita.
- **Reintentar por handler.** Ver la decisión 8.
- **Un broker de verdad** —una cola fuera del proceso—. Es pagar el precio de los
  microservicios sin tener ninguno, que es lo que la
  [ADR-001](ADR-001-solution-architecture-baseline.md) descartó para todo el
  proyecto.
- **Confirmar la fila en `afterCompletion` en vez de en `afterCommit`.** Garantiza
  ir detrás de todos los handlers sin depender de ningún orden, y Spring
  desaconseja expresamente el acceso transaccional a datos en esa fase. Se
  resuelve con orden explícito: cada handler declara `LOWEST_PRECEDENCE - 1` y la
  confirmación, el mínimo posible.

## Consecuencias

### Positivas

- **Un evento publicado no se pierde**, que es el hueco que la Fase 2 dejó
  abierto por escrito.
- **La peor limitación conocida del bus queda cerrada**: un `@EventListener` a
  pelo que revienta ya no deja sin evento a los handlers que van detrás.
- **El tamaño de `event_outbox` es un indicador operable.** Vacía es lo normal;
  que crezca dice que algo no está repartiendo, y lo dice sin instrumentar nada.
- **Publicar un evento de un hogar que no existe deja de ser posible**, por la
  clave ajena. Era una fábula que solo vivía en las pruebas.
- El camino nuevo es de **plataforma** y no nombra a ningún módulo: la lista de
  excepciones de ArchUnit sigue teniendo un solo nombre.

### Costes y riesgos

- **Cada publicación es ahora una escritura más.** Va en la transacción que ya
  estaba abierta, así que no añade ida y vuelta de transacción, pero sí una fila
  por evento y su borrado. Para el orden de magnitud medido —unos pocos eventos
  por hogar y día— es despreciable; conviene saberlo antes de publicar un evento
  por cada lectura.
- **El camino de recuperación se ejercita poco.** Es el precio de convivir: en
  producción solo corre cuando algo falló. Se compensa midiéndolo —la prueba del
  corte lo recorre entero— y con el registro, que deja una línea `WARN` por cada
  evento que hubo que repartir de nuevo.
- **La premisa de la instancia única sigue vigente**, y ahora con un sujeto más.
  Con dos instancias, dos relays reparten lo mismo dos veces; es at-least-once y
  los handlers lo aguantan, pero es una razón más para la condición de revisión
  que la ADR-011 ya fijó.
- **El periodo de gracia es un número elegido, no medido.** Treinta segundos son
  dos órdenes de magnitud más de lo que tarda una petición en repartir y
  confirmar, y nadie ha medido el peor caso real.
- **La sexta función `SECURITY DEFINER`.** La lista de excepciones deliberadas
  del aislamiento crece de cinco a seis. Sigue devolviendo solo identificadores y
  sigue perteneciendo a `drp_resolver`, pero conviene saber que el número subió —
  y `RowLevelSecurityTest` las enumera a mano para que subirlo cueste una línea
  visible.

## Validación o reversión

Se considera validada cuando:

1. **Publicado un evento y caído el proceso antes de repartirlo, el handler lo
   recibe al arrancar.** El corte se produce por el camino de verdad —un
   `@EventListener` que revienta antes de que los handlers lleguen a apuntarse— y
   el testigo es el módulo de prueba, cuyo handler no es idempotente a propósito.
2. **La entrega sigue siendo at-least-once y los handlers siguen siendo
   idempotentes**, comprobado entregando el mismo `eventId` a dos instancias de
   un handler de módulo y afirmando que el efecto no se duplica.
3. **La fila viaja con la transacción**: si la que publicó se deshace, no queda
   fila ni hay nada que recuperar.
4. **El reparto en el acto no deja rastro en la cola**, y una pasada del relay
   después no repite nada.
5. **El relay va hogar a hogar sin `BYPASSRLS`**, con la prueba de la ADR-003 que
   afirma que el usuario de la aplicación no lo tiene, y entrega a cada hogar lo
   suyo.
6. **El relay se apaga y se enciende por su cuenta**, medido en los dos sentidos
   y con el programador encendido, que es donde estaba el peligro.
7. **La tabla lleva `household_id`, RLS y `FORCE`**, y un arranque en limpio
   sobre una base vacía produce el esquema completo, la política de
   `drp_resolver` y la función acotada.

Revisar cuando ocurra cualquiera de estas cosas:

- **Aparece una segunda instancia de la aplicación.** Es la condición que la
  ADR-011 ya tenía, y el relay le añade un sujeto: dos relays reparten lo mismo.
  Lo que hace falta entonces es coordinación —un candado en PostgreSQL basta— y
  es una ADR nueva.
- **`event_outbox` deja de estar vacía en reposo.** Significa que algo no está
  repartiendo, o que el periodo de gracia se ha quedado corto frente a lo que
  tarda una petición en confirmar. Lo primero se mira en el registro; lo segundo
  se mide antes de tocar el número.
- **Un módulo necesita que su handler se reintente hasta que salga bien.** Esta
  decisión no lo cubre a propósito: el reintento por handler pide una fila por
  handler, y eso es otra decisión.
- **Alguien pide un registro de lo publicado** —auditoría, depuración de un
  módulo que se perdió algo—. Es lo que la decisión 5 descarta, y el día que haya
  una necesidad concreta lo que toca es decidir dónde vive ese registro, no
  quedarse las filas del outbox por si acaso.

**Revertir** es acotado y conviene que se sepa. Quitando la escritura y la
confirmación de `SpringEventBus` —y con ellas el relay— se vuelve exactamente al
estado anterior: el bus reparte en el acto igual que hoy y la tabla queda inerte,
sin perder ningún dato. Lo que **no** se revierte sin pensarlo es el `catch` de
`publish`: devolverlo a cubrir la escritura sería reabrir el agujero con la tabla
puesta, que es peor que no tenerla.
