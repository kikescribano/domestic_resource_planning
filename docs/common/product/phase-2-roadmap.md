# Roadmap de la Fase 2 (Módulos activables)

| Campo | Valor |
|---|---|
| Estado | Vigente |
| Responsable | Equipo DRP |
| Ámbito | Ejecución de la Fase 2 |
| Última revisión | 2026-08-19 |

> El estado de **las fases** vive en la sección 8 del
> [`README principal`](../../../README.md), y solo allí. Este documento baja al
> detalle de la Fase 2: qué entra en cada hito, en qué estado va y cómo se
> trabaja. El de la Fase 1 se conserva en [`roadmap.md`](roadmap.md) como
> historia de cómo se hizo el core.

## Alcance

La Fase 2 entrega **los cuatro módulos de prioridad alta** de la sección 4.2 del
README —Proveedores y contactos de servicio, Warehouse, Compras y lista de la
compra, y Mantenimiento (CMMS)— sobre **el mecanismo que permite activarlos y
desactivarlos hogar por hogar**, y con **la capacidad de plataforma** que
programa las comprobaciones periódicas y entrega los avisos.

Son tres cosas y no una, y el orden importa: sin el mecanismo de activación, un
módulo no es un módulo sino una funcionalidad más del core; sin la plataforma, la
caducidad de Warehouse y la revisión de CMMS solo se ven si alguien entra a
mirar.

**Por qué cuatro módulos y no uno.** Un solo módulo no demuestra lo que la
arquitectura promete. Lo que hay que retirar como riesgo son cuatro cosas
distintas, y cada una necesita su testigo:

| Riesgo que la Fase 2 retira | Quién lo demuestra |
|---|---|
| Un módulo aislado del core, con sus propias tablas y su propio contrato | Proveedores, el más pequeño de los cuatro |
| Un módulo que reacciona a lo que pasa en el core sin que el core lo sepa | Warehouse, que consume seis de los trece eventos del catálogo |
| **Dos módulos que se hablan entre sí sin depender uno de que el otro esté activo** | Warehouse → Compras, el par que cierra el ciclo de la reposición |
| Un módulo que lee el dato maestro de otro módulo | CMMS y Compras leyendo Proveedores |

Y una cosa que ningún módulo demuestra por sí solo: **que el hogar que no los
quiere no los ve**, y que el core sigue funcionando igual con los cuatro
apagados. Eso lo demuestra el Hito 0.

### El punto de corte

La fase es larga: siete hitos, y siete sesiones de trabajo. **Al terminar el Hito
4 la arquitectura está entera y hay tres módulos funcionando**; el Hito 5 —CMMS—
es el mayor de los cuatro y no aporta ningún riesgo arquitectónico que no esté ya
retirado. Si en algún momento hay que partir la fase, se parte ahí: Hitos 0–4 y
el cierre por un lado, CMMS por otro. **No se parte en ningún otro sitio**,
porque cualquier otro corte deja un módulo a medias o la activación sin
consumidor.

> **Alcanzado el 2026-08-19, y declarado: la fase no se parte y sigue con CMMS.**
> El Hito 4 retiró el cuarto riesgo, así que el corte está disponible; se decide
> no tomarlo porque partir aquí no ahorra nada —quedan dos hitos, y el 6 no se
> puede ejecutar sin el 5— y dejaría un módulo de prioridad alta a medio camino.
> El razonamiento completo está al final del Hito 4.

## Cómo se trabaja

Igual que en la Fase 1, con «módulo» donde antes decía «hito»:

- **Un hito por sesión.** Cada hito se ejecuta en una sesión propia, que arranca
  leyendo este documento. Son bloques grandes y mezclarlos hace que ninguno se
  cierre del todo.
- **Un pull request por hito**, y no se abre el siguiente hasta que el anterior
  está fusionado. Al fusionar, seguir el procedimiento de alineación local del
  [`CLAUDE.md`](../../../CLAUDE.md): las ramas remotas se borran al fusionar y las
  referencias locales no se enteran solas.
- **Cada hito atraviesa las capas en vertical** —dominio, aplicación, adaptador,
  frontend y sus pruebas—, no una capa entera de cada vez.
- **Cada hito de módulo escribe primero su ficha** en
  [`docs/backend/modules/`](../../backend/modules/README.md) a partir de
  [`module-template.md`](../../backend/modules/module-template.md), y **antes** de
  la primera línea de código: el catálogo existe para que un módulo declare sus
  límites antes de depender de otro, y una ficha escrita después es una
  descripción, no un límite.
- **Al cerrar un hito** se actualiza su estado aquí, se mueve el módulo a «En
  desarrollo» o «En producción» en la sección 4.2 del README —que es donde vive
  ese dato y solo allí— y se añade la fila de historial al README.

## Las tres decisiones de arranque

Están tomadas y son la premisa del plan, no una pregunta de ningún hito. Las
formalizan dos ADR nuevas en los Hitos 0 y 1; aquí quedan escritas para que esos
hitos no tengan que volver a decidirlas.

**1. La activación es por hogar y la decide su administrador.** Un
`HOUSEHOLD_ADMIN` activa y desactiva cualquier módulo desplegado desde una
pantalla del hogar, sobre el catálogo completo. No hay un segundo nivel de
«módulos ofrecibles por instalación»: sería una tabla y una configuración que hoy
no resuelven ningún problema, y añadirlas el día que existan planes o licencias
es una migración pequeña.

**2. Desactivar conserva los datos y los oculta.** Un módulo desactivado deja de
publicar sus rutas, deja de atender eventos y desaparece de la navegación, pero
sus filas siguen donde estaban y reactivarlo las devuelve tal cual. Se descartó
borrar al desactivar —destructivo e irreversible, y obligaría a definir el
borrado en cascada de cuatro módulos antes de escribir el primero— y se descartó
bloquear la desactivación cuando hay datos, que convierte la activación en casi
irreversible y castiga justo al hogar que probó el módulo.

**3. La programación y la entrega de avisos son plataforma, no un módulo.** Es lo
que la sección 4.2 del README ya había decidido al descartar un módulo de avisos
centralizado: **cada módulo posee su regla** de qué se avisa y cuándo, y
plataforma pone el recorrido periódico por hogares y el canal de entrega. Entra
en la Fase 2 con hito propio porque tiene un consumidor **hoy mismo**, antes que
cualquier módulo: los tres procesos diarios del core existen como casos de uso y
no los invoca nadie más que las pruebas.

## Lo que hay que construir antes que el primer módulo

Cuatro huecos que la Fase 1 no dejó abiertos por descuido sino porque no tenían
consumidor. Ahora lo tienen, y son el contenido de los Hitos 0 y 1.

### El backend no está empaquetado por módulos

Hoy es `com.drp.{domain,application,adapter}`, con subpaquetes por agregado
dentro del dominio. Es el reparto correcto para un core, y no expresa ninguna
frontera de módulo: nada impide que el día de mañana Warehouse importe una clase
de CMMS y la dependencia solo se descubra al querer apagar uno de los dos.

La propuesta que el Hito 0 debe fijar en su ADR:

```text
com.drp.platform.*     Bus, TenantContext, puertos compartidos, programación y avisos
com.drp.core.*         El core actual, con su reparto en capas intacto
com.drp.module.<key>.* Un árbol por módulo, con su propio domain/application/adapter
```

Con **reglas de ArchUnit que fallen la construcción**, no con un acuerdo: ningún
`com.drp.module.a` puede referenciar `com.drp.module.b`, ningún módulo puede ser
referenciado desde `com.drp.core`, y todos pueden apoyarse en `com.drp.platform`.
La regla que de verdad protege es la segunda: es la que impide que el core acabe
sabiendo quién le escucha.

**El coste está en mover el core a `com.drp.core`**, que es un renombrado
mecánico de todo el backend. La alternativa —dejar el core donde está y colgar
los módulos aparte— ahorra ese trabajo y deja una asimetría que hay que explicar
cada vez; se descarta por eso, y el renombrado se hace en un commit propio y sin
ningún otro cambio dentro, para que su revisión sea trivial.

### No existe ninguna noción de activación

Ni tabla, ni claim en el token, ni comprobación en la API, ni nada en el
frontend. Hay que construirlo entero, y **gatea en tres sitios**:

| Capa | Qué hace | Detalle que no se puede olvidar |
|---|---|---|
| HTTP | Las rutas del módulo responden `403` con código `MODULE_INACTIVE` si el hogar del token no lo tiene activo | Es `403` y no `404`: un módulo apagado no es un secreto, y el frontend necesita distinguir «no existe» de «actívalo» para poder ofrecer la activación |
| Event bus | Un handler de módulo no hace nada si el módulo está inactivo **para el hogar de ese evento** | La comprobación va **dentro** del handler, sobre el `TenantContext`, y respeta `REQUIRES_NEW` como cualquier otro acceso a datos desde un `AFTER_COMMIT` |
| Frontend | La navegación y las rutas del módulo solo aparecen si está activo; entrar a mano en una ruta apagada lleva a una pantalla que la ofrece si eres administrador | Se resuelve con una sola consulta al arrancar la sesión, no con una por pantalla |

Y una regla que se olvida y luego cuesta: **activar un módulo lo siembra desde el
estado actual del core, no reproduciendo eventos**. La entrega del bus es
at-least-once y en memoria; un módulo activado hoy no vio el `AssetCreated` de
hace un mes y no hay dónde ir a buscarlo. Así que cada módulo expone su propia
siembra —Warehouse recorre las existencias que ya hay, CMMS los assets `DURABLE`
que ya existen— y `HouseholdCreated` deja de ser la única puerta de entrada que
el catálogo de 5.2.3 sugería. El día que haya Transactional Outbox esto no
cambia: reproducir un año de eventos para sembrar sigue siendo peor que leer el
estado.

**Persistencia.** Una tabla `household_modules` con `household_id`, la clave del
módulo, su estado y la autoría de la activación, con **RLS y `FORCE` como
cualquier tabla del core**: es un dato del hogar y no una configuración global.
La clave del módulo es un identificador y va en inglés y en `UPPER_SNAKE_CASE`
—`SUPPLIERS`, `WAREHOUSE`, `PURCHASING`, `MAINTENANCE`—; el nombre que ve el
usuario es un dato y va en castellano.

### La navegación no admite cuatro entradas más

El shell tiene hoy **ocho** enlaces y es barra inferior en móvil. Cuatro módulos
lo llevan a doce, y doce no caben en el pulgar. No es un detalle de estilo: la
misma navegación es un único `<nav>` recolocado con CSS —a propósito, para no
duplicar landmarks— y la batería de accesibilidad recorre sus paradas con el
teclado.

El Hito 0 tiene que resolverlo con el criterio de
[`look-and-feel.md`](../../frontend/product-design/look-and-feel.md) y dejarlo
comprobado en la batería E2E, no darlo por bueno mirando la pantalla. La
dirección propuesta es **separar el core de los módulos** en la navegación en vez
de alargar la lista, de forma que un hogar sin módulos activos vea exactamente lo
que ve hoy.

### La programación no existe

`PurgeUnverifiedHouseholds`, `MarkOverdueLoans` y la purga de ficheros huérfanos
están escritos, probados y **sin programar**: no hay un solo `@Scheduled` en el
código de producción. La Fase 1 los dejó así con criterio —lo que hacía falta
demostrar era que recorren los hogares sin `BYPASSRLS`, y eso lo demuestra la
prueba— pero en producción hoy no se ejecutan.

El Hito 1 pone el recorrido periódico como capacidad de plataforma, con la misma
forma que ya tienen esos tres procesos: **hogar a hogar, fijando
`app.household_id` en cada transacción, nunca con `BYPASSRLS`**, y saltándose los
hogares que no tengan activo el módulo que pide la comprobación.

Sobre la entrega: **un resumen diario por hogar y no un correo por aviso**. Cinco
módulos avisando por fecha, cada uno con su regla, producen una bandeja de
entrada que se deja de leer en una semana; y el hogar que quiere el detalle lo
tiene en la aplicación. El canal reutiliza el `EmailSender` de la
[ADR-009](../architecture/decisions/ADR-009-outbound-email.md) sin inventar nada.

## Hitos

### Hito 0 — Fronteras de módulo y activación por hogar · **Completado (2026-08-18)**

Sin funcionalidad de producto, como el Hito 0 de la Fase 1: es lo que permite que
los hitos siguientes sean módulos y no funcionalidades.

- [x] **ADR-010 — Fronteras de módulo y activación por hogar**: el empaquetado de
      arriba, las reglas de ArchUnit, el gate en tres capas, el `403
      MODULE_INACTIVE` y la siembra desde estado. Con su criterio de validación y
      de reversión, como toda ADR.
- [x] **Renombrado del core a `com.drp.core`** en un commit propio y sin ningún
      otro cambio dentro, más `com.drp.platform` con lo que hoy es compartido:
      bus, `TenantContext`, puertos e `IdempotentEventHandler`.
- [x] **Reglas de ArchUnit** que fallen la construcción, con una prueba por regla
      y **medidas en los dos sentidos**: introducida la dependencia prohibida, la
      prueba falla.
- [x] **Migración `V7`**: `household_modules` con su RLS, su `FORCE` y su autoría.
- [x] **Registro de módulos en código** —clave, nombre, descripción— y el puerto
      que responde «¿está activo para este hogar?», resuelto una vez por petición.
- [x] **Tres operaciones nuevas en el contrato**: listar el catálogo con el estado
      del hogar, activar y desactivar. Solo administrador. Con `operationId`, que
      es lo que la
      [ADR-007](../architecture/decisions/ADR-007-openapi-contract-as-source-of-truth.md)
      necesita.
- [x] **El gate HTTP** sobre el prefijo de ruta del módulo, con su código de
      error nuevo enumerado en el esquema.
- [x] **La base de handler de módulo**, que extiende `IdempotentEventHandler` y
      añade la comprobación de activación sin romper ninguna de las tres garantías
      que esa clase ya resuelve.
- [x] **Frontend**: pantalla de módulos del hogar, navegación reorganizada para
      que doce entradas quepan, y la pantalla que ofrece activar cuando entras en
      una ruta apagada.
- [x] **Recorrido vertical**: con el módulo apagado su API responde `403` y su
      navegación no está; se activa desde la pantalla, aparece; se desactiva,
      desaparece y **los datos siguen ahí** al volver a activarlo.

> **Un módulo de mentira para probar esto.** El Hito 0 no tiene módulos reales que
> encender, y esperar al Hito 2 para comprobar el mecanismo deja el mecanismo sin
> prueba propia. Se resuelve con un módulo de prueba mínimo **que vive en el árbol
> de pruebas y no se despliega**, con una tabla, una ruta y un handler: es el
> testigo del gate en las tres capas y sobrevive a la fase entera como prueba de
> regresión del mecanismo.

#### Cómo quedó, y las tres cosas que cambiaron sobre lo previsto

El mecanismo está entero y el catálogo, completo: los cuatro módulos aparecen
declarados —clave, nombre, descripción y prefijo de ruta— en su propio árbol
`com.drp.module.<clave>`, sin dominio. **Encenderlos y apagarlos funciona de
verdad**; lo que hay detrás de cada uno llega con su hito.

Tres cosas se decidieron distinto de como estaban escritas, y las tres quedan
anotadas con su alternativa descartada en [`decisions.md`](decisions.md):

- **El gate HTTP es un filtro y no un `HandlerInterceptor`.** Un interceptor solo
  alcanza a las rutas que ya tienen manejador, así que todo lo que un módulo aún
  no ha construido respondería `404` —justo la confusión que el `403` evita— y una
  operación añadida mañana bajo su prefijo nacería sin gate.
- **La barra inferior de móvil baja de ocho paradas a cuatro más «Más».** No era
  neutral conservarlas: a 320 px daban 40 px de ancho cada una, por debajo de los
  44 px que exige la dirección visual. El recorrido vertical lo mide ahora, para
  que el defecto no vuelva con el módulo siguiente.
- **«Módulos del hogar» es una entrada de navegación propia**, así que un hogar
  sin módulos activos ve nueve enlaces en escritorio y no ocho exactos. La
  alternativa —una tarjeta en la pantalla de inicio— escondía la única puerta de
  la funcionalidad detrás de un descubrimiento casual.

Y **`ModuleEventHandler` abre él mismo la transacción `REQUIRES_NEW`** en lugar
de dejarlo como norma para cada módulo, que era lo previsto. Los cuatro hitos de
módulo se ahorran acordarse de la única regla cuyo incumplimiento puede tumbar al
core.

**Lo que el hito no puede demostrar en el navegador, y dónde se demuestra.** El
recorrido vertical enseña el ciclo completo salvo una mitad: que los datos del
módulo siguen ahí al reactivarlo. No hay dónde verlo, porque ninguno de los
cuatro tiene todavía una sola fila. Eso lo demuestra el módulo de prueba en la
batería del backend —escribe, se apaga, deja de escribir, y al reactivarse
devuelve lo que había—, que es exactamente para lo que existe.

### Hito 1 — Plataforma: programación y avisos · **Completado (2026-08-18)**

- [x] **ADR-011 — Programación de comprobaciones y entrega de avisos**: el
      recorrido por hogares, la instancia única, el resumen diario y por qué esto
      no es un módulo.
- [x] **El recorrido periódico por hogar** como capacidad de `com.drp.platform`,
      con la forma que ya tienen los tres procesos diarios y saltando los hogares
      que no tengan activo el módulo que lo pide.
- [x] **Los tres procesos diarios del core, programados de verdad**, que es el
      hueco que la Fase 1 dejó abierto sin nombrarlo.
- [x] **Puerto de avisos** con su adaptador de correo sobre el `EmailSender`
      existente, y la tabla de avisos del hogar con su RLS.
- [x] **El resumen diario**: un correo por hogar con lo que haya, y ninguno cuando
      no hay nada — un correo diario vacío es la forma más rápida de que se
      filtren todos.
- [x] **Frontend**: la bandeja de avisos del hogar, con su estado leído/no leído.
- [x] **Pruebas**: el correo se lee de Mailpit de verdad, como el enrolamiento de
      la Fase 1; y una prueba que compruebe que un hogar con el módulo apagado
      **no** recibe su aviso.

> **Una instancia, un programador.** El despliegue elegido con
> [consumo medido](../../backend/operations/capacity-measurements.md) es un VPS
> único, así que `@Scheduled` basta y no hace falta coordinación entre nodos. Eso
> es una premisa, no una propiedad: queda escrito en la ADR-011 con su condición
> de revisión, porque el día que haya dos instancias los procesos se ejecutan dos
> veces y ninguno avisa de ello.

#### Cómo quedó, y las cuatro cosas que cambiaron sobre lo previsto

Los tres procesos diarios **se ejecutan de verdad** en un despliegue, y la
plataforma de avisos está entera: tabla con RLS, bandeja en el cliente y resumen
diario por correo. Lo que falta es lo que ningún módulo ha traído todavía —una
regla que avise por una fecha—, y eso llega con Warehouse y con CMMS.

Cuatro cosas se decidieron distinto de como estaban escritas, y las cuatro quedan
anotadas con su alternativa descartada en [`decisions.md`](decisions.md):

- **Los tres procesos diarios pierden su propio recorrido.** La definición decía
  «programados de verdad», que se lee como envolverlos en un `@Scheduled`; con el
  suyo más el de plataforma habría **dos recorridos**, y solo uno de los dos puede
  saltarse los hogares con el módulo apagado. Ahora miran el hogar actual y quien
  itera es `DailySweep`.
- **Enviar correo y saber qué hogares hay se mudan a `com.drp.platform`.** No
  estaba en el alcance y era inevitable: la tercera regla de ArchUnit lo obliga, y
  ampliar su lista de excepciones habría sido justo la señal que la ADR-010 marca
  como motivo de revisión. `EmailAddress` se muda con el puerto.
- **Cada comprobación declara de quién es con un tipo sellado**, no con una clave
  nullable: con un `String?`, «del core» y «se me olvidó declararlo» se escriben
  igual y el segundo caso no falla, solo corre donde no debía.
- **La bandeja de avisos entra en el grupo del hogar y no en la barra inferior.**
  El tope medido son cinco paradas a 320 px, así que lo que gana una pantalla
  nueva es sitio en la columna del escritorio y en «Más».

**Lo que el hito no puede demostrar todavía, y dónde se demostrará.** Ningún
módulo real tiene aún una regla de aviso, así que el testigo de que el recorrido
se salta un módulo apagado es **el módulo de prueba del Hito 0**, que gana aquí su
comprobación periódica. Cuando Warehouse traiga la caducidad, lo que hay que
añadir es su `ScheduledCheck` y nada más.

### Hito 2 — Proveedores y contactos de servicio · **Completado (2026-08-18)**

El primer módulo de verdad, y el más pequeño a propósito: lo que se está probando
aquí es el camino completo de un módulo, no su dominio.

- [x] **Ficha del módulo** en [`docs/backend/modules/suppliers.md`](../../backend/modules/suppliers.md),
      escrita antes del código y en un commit propio para que el orden se vea en
      la historia.
- [x] **Dominio**: el contacto de servicio —quién arregla, quién cobra, quién
      responde de una garantía— con su categoría de servicio, sus datos de
      contacto y su relación opcional con assets y ubicaciones del core.
- [x] **Su migración `V9`**, con `household_id`, RLS y `FORCE` en sus dos tablas.
      El recuento del esquema sube de diecisiete a diecinueve y la lista de
      tablas sin política no se toca.
- [x] **Siete operaciones en el contrato** bajo `/api/v1/suppliers` con su
      `operationId`, cinco códigos de error nuevos en el esquema, y su pantalla
      bajo `/proveedores`.
- [x] **Su siembra al activarse**, vacía y escrita igual: es el punto de
      extensión que los tres siguientes sí usan.
- [x] **Recorrido vertical propio**, añadido a la batería existente y no en una
      suite paralela.

> **Un dato maestro sin lector todavía.** Proveedores no tiene consumidor hasta el
> Hito 4, y eso es deliberado: es el módulo con el que se aprende el camino antes
> de recorrerlo con un dominio grande encima.

#### Cómo quedó, y lo que hubo que decidir por el camino

El camino de un módulo está recorrido entero y **medido en los dos extremos**:
ficha antes del código, dominio, migración, contrato, gate, siembra, pantallas y
recorrido vertical. Lo que este hito añade a lo que ya se sabía es que **el gate
por fin tapa algo que existe**: hasta ahora un módulo encendido respondía `404`
porque no había controlador detrás, así que el `403 MODULE_INACTIVE` nunca había
protegido una ruta de verdad. Ahora apagado responde `403` y encendido responde
`200`, y las dos mitades están medidas sobre la ruta real.

Las decisiones que la definición no preveía quedan anotadas con su alternativa
descartada en [`decisions.md`](decisions.md). Las tres que condicionan a los
módulos siguientes:

- **`ErrorCode` y la familia de `DomainError` se mudan a `com.drp.platform.error`.**
  Era la deuda que la ADR-010 dejó con esta condición de revisión exacta, y lo
  peligroso del caso es que **no falla nada**: lanzar un código propio desde un
  módulo compila y ArchUnit sigue verde, mientras el core acaba enumerando las
  reglas de sus módulos. Fija el patrón de los tres módulos siguientes.
- **Las claves ajenas de un módulo hacia el core van con `ON DELETE CASCADE`.**
  Con el `RESTRICT` que rige por omisión, un enlace de Proveedores convertiría el
  borrado de una ubicación en un `500` **del core causado por un módulo**.
- **La categoría de servicio es lista cerrada**, al contrario que la `Category`
  del core, con `OTHER` como salida y como disparador de revisarlo.

Y **`ModuleScreen` pasa de sustituir a envolver**: era un guardián que no sabía
enseñar nada, y con eso se cierra la promesa autoprogramada que el Hito 0 dejó
escrita en la pantalla —«sus pantallas llegan en el Hito 2»— en lugar de dejarla
apuntando al pasado.

**Lo que este hito no hace, y dónde se hace.** El **barrido de aislamiento** de
las operaciones nuevas se queda en el **Hito 6**, que es donde el roadmap lo puso
para toda la fase: hoy `TenantIsolationSweepTest` cubre las treinta y ocho
operaciones de la Fase 1 y ninguna de plataforma ni de módulo. Se dice aquí
explícitamente para que no se dé por supuesto. Lo que sí se comprueba ya es que
un enlace no puede apuntar a una ubicación de otro hogar, porque esa es una
regla del módulo y no del barrido.

### Hito 3 — Warehouse · **Completado (2026-08-19)**

El módulo con más dominio de los cuatro y el que más habla con el core.

- [x] **Ficha del módulo** en [`docs/backend/modules/warehouse.md`](../../backend/modules/warehouse.md),
      escrita antes del código y en un commit propio, con la frontera contra el
      core escrita sin ambigüedad: el core mantiene **un contador** —`quantity`,
      en la `unit` que pone el artículo— y consumos, mínimos, caducidad y lotes
      son de aquí. **Warehouse no lleva un segundo contador**: lo lee del core.
- [x] **Dominio**: movimientos de existencias, stock mínimo por artículo,
      caducidad y lotes, en cuatro tablas con `household_id`, RLS y `FORCE`. El
      recuento del esquema sube de diecinueve a veintitrés y la lista de tablas
      sin política no se toca.
- [x] **Consumo de los seis eventos del core** por la base de handler del Hito 0,
      con la prueba que este hito estrena: **un hogar con el módulo apagado no ve
      ni una fila escrita**, y el de al lado sí.
- [x] **Su siembra al activarse**, recorriendo artículos, sitios y las existencias
      que el hogar ya tenga: es la primera de verdad, y **es idempotente porque
      reactivar la ejecuta** —ver más abajo.
- [x] **Sus dos avisos** —caducidad próxima y mínimo alcanzado— sobre la
      plataforma del Hito 1, con su `ScheduledCheck` propia, leídos del Mailpit de
      verdad, y **sin repetirse cada noche**.
- [x] **Sus dos eventos publicados** —`StockBelowMinimum` y `StockDepleted`—, con
      Compras declarado como consumidor previsto en la ficha.
- [x] **Resuelto el peso y el volumen de un asset**: van al core y van en el
      artículo. Ver más abajo.
- [x] **Diez operaciones en el contrato** bajo `/api/v1/warehouse` con su
      `operationId`, cinco códigos de error nuevos en `com.drp.platform.error`, y
      sus pantallas bajo `/almacen` con recorrido vertical **añadido a la batería
      existente**.

#### Cómo quedó, y lo que hubo que decidir por el camino

**El riesgo que este hito retira es el segundo de la tabla de alcance: un módulo
que reacciona a lo que pasa en el core sin que el core lo sepa.** Seis de los
trece eventos del catálogo tienen por fin un consumidor de verdad, y las dos
capas del gate que Proveedores no podía enseñar están medidas: con el módulo
apagado, ninguno de los seis handlers escribe una fila, y el recorrido nocturno
no entra en ese hogar.

Las once decisiones que la definición no preveía están anotadas con su
alternativa descartada en [`decisions.md`](decisions.md). Las tres que
condicionan a los dos módulos siguientes:

- **Reactivar vuelve a sembrar, y por eso toda siembra tiene que ser
  idempotente.** La ADR-010 dice lo contrario de lo que `ActivateModule` hace, y
  nadie lo había notado porque la siembra de Proveedores está vacía. Se conserva
  el código: un módulo que vuelve con el cuaderno de hace tres meses **miente en
  silencio** sobre lo que hay en la despensa.
- **Un aviso se levanta cuando la condición empieza a ser cierta y no se repite
  mientras siga siéndolo.** Ninguno de los dos ejemplos previos servía de modelo
  —el core no repite, el módulo de prueba repite a propósito—, y un yogur que
  avisara treinta noches seguidas filtra el resumen diario entero.
- **Un handler no comprueba si el módulo ya sembró: abre lo que necesite**,
  compartiendo función con la siembra. Con eso, «aún no ha sembrado» deja de ser
  un caso y los dos caminos no pueden divergir.

Y **el `Combobox` se construye aquí** en lugar de aplazarse por tercera vez:
llevaba pendiente desde el Hito 2 de la Fase 1, y buscar un artículo entre los
cientos de una despensa no lo resuelve un `SelectField`.

**Lo que este hito no hace, y dónde se hace.** El **barrido de aislamiento** de
las diez operaciones nuevas se queda en el **Hito 6**, igual que las siete de
Proveedores: se dice aquí explícitamente para que no se dé por supuesto. La
**purga de `household_notices`** sigue sin hito asignado, y este hito no la
resuelve pese a traer avisos —su sitio natural sigue siendo una comprobación más
del mismo recorrido—. **Volver a medir la capacidad** también es del Hito 6, y
este hito le deja un aviso escrito en la ficha: `warehouse_movements` es **la
primera tabla del modelo que crece con lo que el hogar hace y no con lo que
tiene**, así que es por donde hay que empezar a mirar.

### Hito 4 — Compras y lista de la compra · **Completado (2026-08-19)**

El hito que cierra el ciclo y el que retira el riesgo arquitectónico principal:
**dos módulos que se hablan sin depender uno de que el otro esté activo**.

- [x] **Ficha del módulo** en [`docs/backend/modules/purchasing.md`](../../backend/modules/purchasing.md),
      escrita antes del código y en un commit propio, con la frontera contra
      Warehouse sin ambigüedad: **Warehouse detecta la falta, Compras decide qué
      se compra y cuándo**. Compras no mira existencias ni mínimos, y Warehouse no
      sabe que Compras existe.
- [x] **Dominio**: lista de la compra, estado de lo pedido y el cierre de la
      compra, en dos tablas con `household_id`, RLS y `FORCE`. El recuento del
      esquema sube de veintitrés a veinticinco y la lista de tablas sin política
      no se toca.
- [x] **Consumo de los dos eventos de Warehouse** por la base de handler del Hito
      0, con **las dos mitades de la prueba que importa medidas**: Warehouse
      apagado y Compras encendido —los eventos no llegan, la lista deja de
      llenarse sola y se añade a mano—, y al revés —Warehouse publica, escribe su
      cuaderno y nadie escucha—, con un tercer hogar con los dos encendidos como
      comparación.
- [x] **Lectura de Proveedores** por un **puerto en plataforma que no nombra a
      ningún módulo**, con la degradación puesta en plataforma. Ver más abajo.
- [x] **El cierre del ciclo**: recibir una compra acaba llamando a
      `RegisterConsumableIntake`, que **suma** sobre la existencia de esa
      ubicación — y con Warehouse encendido, eso aparece en su cuaderno.
- [x] **Su siembra al activarse**, sobre los consumibles del core que están a
      cero, e idempotente por construcción.
- [x] **Diez operaciones en el contrato** bajo `/api/v1/purchasing` con su
      `operationId`, cinco códigos de error nuevos en `com.drp.platform.error`, y
      sus pantallas bajo `/compras` con recorrido vertical **añadido a la batería
      existente**.

#### Cómo quedó, y lo que hubo que decidir por el camino

**El riesgo que este hito retira es el tercero de la tabla de alcance, y era el
principal: dos módulos que se hablan sin depender uno de que el otro esté
activo.** Con él, **los cuatro riesgos están retirados**. Y trae dos primeras
veces que ningún módulo anterior podía traer: **leer el dato maestro de otro
módulo** y **escribir en el core** — Warehouse movía un contador que ya existía;
Compras crea existencias.

Las decisiones que la definición no preveía están anotadas con su alternativa
descartada en [`decisions.md`](decisions.md). Las tres que cierran preguntas
heredadas:

- **Cómo lee un módulo el dato maestro de otro: un puerto en plataforma, y
  llamado de modo que plataforma no nombre a ningún módulo.** Se descarta la
  alternativa de eventos con copia local por un motivo que no es de gusto: **esa
  copia no se puede sembrar**, porque el estado del que habría que sembrarla vive
  en las tablas del otro módulo. El puerto es `MasterDataDirectory` —el mecanismo,
  pedido por la clave del módulo dueño— y no `SupplierDirectory`, que es lo que
  evita el residuo que la mudanza de `ErrorCode` dejó anotado para vigilar. **La
  lista de excepciones de ArchUnit sigue teniendo un solo nombre.**
- **Lo que llega a cero entra solo en la lista** — y para que eso fuera cierto
  hubo que arreglar Warehouse: `StockDepleted` colgaba de la rama de bajo mínimos,
  así que **no se publicaba nunca para un artículo sin mínimo declarado**, que son
  casi todos. Nadie lo había notado porque hasta este hito no había consumidor.
- **La presentación de compra se compone y no se guarda.** Era la media pregunta
  que venía de la Fase 1: no necesita nombre propio, porque `packSize` y `unit` ya
  lo dicen, y un texto libre sería una segunda fuente de verdad que puede
  contradecir al envase.

**Lo que este hito no hace, y dónde se hace.** El **barrido de aislamiento** de
las diez operaciones nuevas se queda en el **Hito 6**, igual que las siete de
Proveedores y las diez de Warehouse: se dice aquí explícitamente para que no se dé
por supuesto. La **purga de `household_notices`** sigue sin hito asignado, y este
hito le añade **dos candidatos más al mismo sitio** —`shopping_list_items` y
`purchases`, que como el cuaderno de Warehouse crecen con lo que el hogar *hace* y
no con lo que *tiene*—, de modo que ya son cuatro tablas y no una. **Volver a
medir la capacidad** también es del Hito 6.

> **Aquí está el punto de corte de la fase, y queda declarado.** Cerrado este
> hito, **los cuatro riesgos de la tabla de alcance están retirados y hay tres
> módulos funcionando**: la arquitectura está entera y CMMS no aporta ningún
> riesgo arquitectónico que no esté ya retirado.
>
> **La fase no se parte aquí y sigue con CMMS.** El motivo es que partirla ahora
> no ahorra nada de lo que un corte sirve para ahorrar: los Hitos 5 y 6 son los
> dos únicos que quedan, el 6 **no se puede ejecutar sin el 5** —su barrido de
> aislamiento y su auditoría de accesibilidad cubren las operaciones y las
> pantallas de los cuatro módulos, y su barrido de promesas aplazadas tiene que
> alcanzar las que CMMS deje— y cerrar la fase sin el cuarto módulo dejaría la
> sección 4.2 del README con un módulo de prioridad alta a medio camino, que es
> justo lo que el criterio de corte quería evitar. Lo que el punto de corte
> garantiza sigue en pie y es lo que había que declarar: **si hubiera que
> pararlo, este es el único sitio donde el trabajo entregado se sostiene solo.**

### Hito 5 — Mantenimiento (CMMS) · **Completado (2026-08-19)**

El mayor de los cuatro, y **el último**: cerrado este hito, los cuatro módulos de
prioridad alta de la sección 4.2 están construidos.

- [x] **Ficha del módulo** en [`docs/backend/modules/maintenance.md`](../../backend/modules/maintenance.md),
      escrita antes del código y en un commit propio, con **la frontera contra el
      planificador de tareas escrita por adelantado y sin ambigüedad** aunque ese
      módulo no exista: **de CMMS es el cuándo y del planificador el quién lo
      hace**. Un plan es una regla sobre una máquina; una tarea, un encargo con
      responsable y día.
- [x] **Dominio**: planes de mantenimiento preventivo sobre un asset `DURABLE`,
      intervenciones correctivas e histórico, en tres tablas con `household_id`,
      RLS y `FORCE`. El recuento del esquema sube de veinticinco a veintiocho y la
      lista de tablas sin política no se toca.
- [x] **Su migración `V13`**, que deja el esquema completo desde una base vacía,
      políticas incluidas.
- [x] **Consumo de `AssetCreated`, `AssetDeactivated` y `DocumentAttached`** por
      la base de handler del Hito 0, **con el agregado de la tercera leído del
      `payload` y no del `aggregateId`**, y con la prueba de que un hogar con el
      módulo apagado no ve ni una fila escrita y el de al lado sí.
- [x] **Lectura de Proveedores por el puerto de plataforma, sin ensancharlo**, y
      funcionando con ese módulo apagado. De paso, las dos mitades de la garantía
      que Proveedores declaró por adelantado quedan por fin ejercitadas.
- [x] **Su siembra al activarse**, sobre los `DURABLE` que el hogar ya tenga:
      **abre la ficha de cada máquina y no crea ningún plan**, e idempotente por
      índice y no por comprobación previa.
- [x] **Sus avisos de revisión** sobre la plataforma del Hito 1, leídos del
      Mailpit de verdad, sin repetirse cada noche y **volviéndose a armar al
      registrar la intervención**.
- [x] **Once operaciones en el contrato** bajo `/api/v1/maintenance` con su
      `operationId` —el contrato pasa de ochenta y siete a noventa y ocho—, cinco
      códigos de error nuevos en `com.drp.platform.error`, y sus pantallas bajo
      `/mantenimiento` con recorrido vertical **añadido a la batería existente**,
      que pasa de cinco recorridos a seis.

#### Cómo quedó, y lo que hubo que decidir por el camino

**Este hito no retira ningún riesgo arquitectónico, porque los cuatro estaban
retirados desde el Hito 4** — y eso era exactamente lo que el punto de corte
declaraba. Lo que trae es el cuarto módulo de prioridad alta y **tres primeras
veces que ningún hito anterior podía traer**:

- **Una frontera escrita sin el otro lado delante.** Warehouse escribió la suya
  contra el core y Compras contra Warehouse, los dos con quien contrastarla.
  Aquí el interlocutor —el planificador de tareas— no existe, y el catálogo de
  eventos ya le había asignado dos trabajos. La línea queda clavada con **tres
  consecuencias cumplidas en código**: ninguna tabla lleva responsable, no hay
  ninguna fila por ocurrencia futura y este módulo no consume `UserDeactivated`.
- **La prueba de verdad del puerto de dato maestro**, que se diseñó con un solo
  consumidor delante. **No se ensancha**, y el motivo está escrito con su
  disparador de revisión.
- **Un aviso periódico que se rearma**, que es la vuelta que Warehouse no tuvo
  que dar: allí el rearme era reponer por encima del mínimo y aquí es un gesto de
  una persona.

Las decisiones que la definición no preveía están anotadas con su alternativa
descartada en [`decisions.md`](decisions.md). Las cuatro que más lejos llegan:

- **La siembra abre la ficha de cada máquina y no crea ningún plan**, y con eso
  se corrige el catálogo de eventos de 5.2.3: «genera un plan de mantenimiento
  por defecto» se escribió en la Fase 0 como ejemplo del bus, no como decisión de
  producto, y no se sostiene — **por defecto ¿de qué?** Una caldera pide revisión
  anual y una silla no pide nada, y el core no modela de qué clase es cada
  máquina.
- **Un aviso periódico cuelga de la próxima fecha prevista y no del plan.** Con
  una marca suelta, cada camino que mueve la fecha tendría que acordarse de
  limpiarla, y el que se olvidara dejaría un plan que **no vuelve a avisar
  nunca** — un síntoma que solo se descubre dos años después.
- **`DocumentAttached` es el único evento del catálogo cuyo agregado no es la
  cosa que ha cambiado.** Copiar el patrón de los dos hitos anteriores habría
  enlazado el manual a una máquina inexistente **sin fallar**.
- **El campo `milestone` de `MODULE_SCREENS` se retira**, y con él la rama de
  `ModuleScreen` sin hijos: era la última de las cuatro promesas autoprogramadas
  del Hito 0, y un campo opcional que ningún módulo rellena es una invitación a
  volver a rellenarlo.

Y **el recorrido vertical volvió a encontrar lo que solo se ve en un navegador**,
como en el Hito 4: **recargar la pestaña devolvía al login con la sesión viva en
el servidor**. El refresh token es de un solo uso y rota, pero con `StrictMode` el
efecto de reanudación se monta dos veces y lanzaba dos renovaciones con el mismo
token. No era un defecto de este hito y llevaba cinco recorridos sin aparecer,
porque solo pasaba a veces.

**Lo que este hito no hace, y dónde se hace.** El **barrido de aislamiento** de
las once operaciones nuevas se queda en el **Hito 6**, igual que las siete de
Proveedores, las diez de Warehouse y las diez de Compras: se dice aquí
explícitamente para que no se dé por supuesto, que es lo que han hecho los tres
hitos anteriores. La **purga de `household_notices`** sigue sin hito asignado, y
este hito le añade un **quinto candidato al mismo sitio**,
`maintenance_interventions`, que como el cuaderno de Warehouse y las dos tablas
de Compras crece con lo que el hogar *hace* y no con lo que *tiene* — aunque es el
menos urgente de los cinco, porque una casa registra unas pocas intervenciones al
año y no varias al día. **Volver a medir la capacidad** también es del Hito 6.

> **Los cuatro módulos de prioridad alta están construidos, y con esto la Fase 2
> solo tiene pendiente su cierre.** Lo que queda del alcance —«los cuatro módulos
> de prioridad alta sobre el mecanismo de activación y la plataforma de avisos»—
> está entero: el mecanismo (Hito 0), la plataforma (Hito 1) y los cuatro módulos
> (Hitos 2 a 5). El Hito 6 no añade producto: consolida.

### Hito 6 — Cierre de la fase · **Pendiente**

Existe porque la Fase 1 aprendió que no se cierra solo: el Hito 4 dio por hecho un
criterio de accesibilidad que no había cubierto, y seis documentos del frontend se
quedaron diciendo «esto llega en el Hito 4» hasta que una sesión posterior volvió
a por ellos.

**Y ahora la lista está completa delante**, que es lo que este hito no tenía hasta
cerrar el 5. Lo que le queda, con las cifras reales:

- [ ] **Barrido de aislamiento sobre las operaciones nuevas**, con la misma forma
      que el de la Fase 1: autenticado como hogar A, ninguna operación de ningún
      módulo devuelve ni modifica datos del hogar B, ni por identificador directo.
      Hoy `TenantIsolationSweepTest` cubre **las treinta y ocho operaciones de la
      Fase 1 y ninguna de plataforma ni de módulo**, y lo que falta son **cuarenta
      y una**: las tres de la activación (Hito 0), las tres de avisos (Hito 1),
      las siete de Proveedores, las diez de Warehouse, las diez de Compras y las
      once de Mantenimiento. Los cuatro hitos de módulo lo dejaron aquí a
      propósito y lo dijeron cada uno en su ficha.
- [ ] **Auditoría de accesibilidad completa** de las pantallas nuevas: teclado,
      foco visible en cada parada, reflujo de 320 px a ultrawide y axe en los dos
      modos. No basta con axe. Son **seis pantallas** —módulos, avisos,
      proveedores, almacén, compras y mantenimiento— y la batería tiene ya seis
      recorridos que las tocan; lo que falta es la pasada sistemática, no la
      primera visita.
- [ ] **Barrido de las promesas aplazadas**: buscar en la documentación toda frase
      del tipo «lo hará el hito siguiente» y cerrarla o convertirla en tarea con
      destinatario. **Las cuatro de `MODULE_SCREENS` ya están cerradas** —cada
      módulo perdió la suya al construir su pantalla, y el campo se retiró con la
      última—, así que lo que queda son las de la documentación.
- [ ] **Volver a medir la capacidad**, porque cuatro módulos cambian el consumo de
      disco por hogar, que es lo que decidió el VPS. **Por dónde empezar está
      dicho**: las cuatro tablas que crecen con lo que el hogar *hace* y no con lo
      que *tiene* —`warehouse_movements`, `shopping_list_items`, `purchases` y
      `maintenance_interventions`—, más `household_notices`, que la ADR-011 dejó
      anotada entre sus costes.
- [ ] **Decidir de quién es la purga de esas cinco tablas**, que sigue sin hito
      asignado desde el Hito 1 y ya no puede seguir aplazándose sin decir por qué:
      su sitio natural sigue siendo una comprobación más del recorrido diario, y
      es el único punto de la fase que ha ido creciendo hito a hito sin
      destinatario.
- [ ] **Resolver las decisiones abiertas que dejaron los cuatro módulos con
      «quien abra el Hito 6» como responsable**, que son las que solo se pueden
      contestar mirando datos reales: si la categoría de servicio de Proveedores
      tiene que pasar a catálogo por hogar, si el consumo de Warehouse debe
      repartirse entre lotes, si `StockDepleted` debería llegar también sin mínimo
      declarado en sus demás reglas, y si el plan de CMMS debe poder colgar de una
      ubicación. **Y una que es de dos módulos a la vez**: la antelación del aviso
      por hogar, que Warehouse y Mantenimiento dejaron abierta por separado y que
      conviene resolver junta — dos tablas de configuración de dos módulos para lo
      mismo serían la señal de que lo que falta es una de plataforma.
- [ ] **Decidir qué se hace con la prueba del reloj del enrolamiento**, que es la
      única de la suite que mide tiempos y **ya ha fallado dos veces en la CI sin
      que nada hubiera cambiado en el código**: la primera por un umbral absoluto
      de 60 ms —que se cambió por una proporción— y la segunda el 2026-08-19, en
      la comparación contra el coste de un hash, durante el Hito 5. Lo que mide
      importa —que la rama «ese correo ya existe» pague el hash igual, que es la
      única forma conocida de reabrir la fuga— así que **no se relaja sin más**;
      lo que hay que decidir es si esa propiedad se comprueba mejor de otra
      manera que cronometrando en un runner compartido.
- [ ] **Actualizar el deck de marketing**, que no avisa cuando se queda atrás.
- [ ] **README**: sección 4.2 con los cuatro módulos en su estado real, sección 8
      con la fase cerrada, sección 10 con su fila de historial.

> **El Hito 6 no añade producto: consolida.** Los cuatro riesgos de la tabla de
> alcance están retirados desde el Hito 4 y los cuatro módulos construidos desde
> el 5, así que lo que queda es lo que la Fase 1 aprendió que no se cierra solo.
> Es además el único hito de la fase que **no se puede ejecutar antes que los
> demás**, y por eso el punto de corte lo dejó fuera del corte.

## Criterio de aceptación

Como en la Fase 1, casi todo está ya escrito en las ADR y basta con consolidarlo.
Las dos últimas filas son lo que la Fase 2 añade.

| Origen | Qué debe demostrarse |
|---|---|
| [ADR-001](../architecture/decisions/ADR-001-solution-architecture-baseline.md) | Cada módulo tiene su recorrido vertical de frontend a PostgreSQL, con pruebas en los tres niveles |
| [ADR-002](../architecture/decisions/ADR-002-multi-tenancy-and-backend-framework.md) | Autenticado como hogar A, ninguna operación de ningún módulo devuelve ni modifica datos del hogar B |
| [ADR-003](../architecture/decisions/ADR-003-row-level-security.md) | Toda tabla nueva lleva `household_id`, RLS y `FORCE`; los recorridos periódicos van hogar a hogar **sin `BYPASSRLS`** |
| [ADR-004](../architecture/decisions/ADR-004-database-migrations.md) | Un arranque en limpio sobre una base vacía produce el esquema de los cuatro módulos, políticas incluidas |
| [ADR-006](../architecture/decisions/ADR-006-frontend-stack-and-design-system.md) | Las pantallas nuevas pasan teclado, foco, contraste aplicado, reflujo y axe en los dos modos |
| [ADR-007](../architecture/decisions/ADR-007-openapi-contract-as-source-of-truth.md) | Toda operación nueva está en el contrato con su `operationId`, y el validador pasa |
| [ADR-009](../architecture/decisions/ADR-009-outbound-email.md) | Los avisos se leen del correo real de Mailpit, sin pasos manuales |
| **ADR-010** (nueva) | Con un módulo inactivo: su API responde `403`, sus handlers no hacen nada y su navegación no existe. Activarlo lo siembra desde el estado actual. ArchUnit falla si un módulo referencia a otro |
| **ADR-011** (nueva) | Un recorrido programado solo entra en los hogares que tienen el módulo activo, y el core sigue funcionando con los cuatro apagados |

## Preguntas que esta fase tiene que resolver

Dos vienen heredadas con destinatario asignado y **vencen aquí**; las otras nacen
del alcance nuevo. **Las cinco están resueltas.** Se resuelven en el hito que las toca y se anotan en
[`decisions.md`](decisions.md), no aquí.

| Pregunta | Hito | Por qué vence ahora |
|---|---|---|
| ~~**Peso y volumen de un asset**~~ — **resuelta el 2026-08-19: van al core, y van en `articles`**, porque el aviso de capacidad es una regla del core y una regla del core no puede depender de un módulo que se puede apagar | 3 | Su destinatario asignado era Warehouse, y Warehouse llegó. Resultó no ser suya |
| ~~**Unidad de compra frente a unidad de consumo**~~ — **resuelta del todo el 2026-08-19**: la conversión ya estaba guardada y es del core (`Article.packSize`), y **la presentación de compra no necesita nombre propio** — se compone con `packSize` y `unit` en lugar de guardarse, porque un texto libre sería una segunda fuente de verdad que puede contradecir al envase | 3 y 4 | El core fija que la unidad la pone el artículo y que convertir es de Warehouse; Compras es quien lo necesita de verdad |
| ~~**Qué pasa cuando un consumible llega a cero**~~ — **resuelta el 2026-08-19: entra solo en la lista, sin que nadie lo diga**, porque quedarse sin algo es el disparador canónico de una lista de la compra y descartar una línea es un clic. Al escribirlo se destapó que `StockDepleted` **no se publicaba nunca sin mínimo declarado**, y se corrigió en Warehouse, que es de quien es | 4 | El catálogo de eventos lo da por hecho, y nadie ha decidido si es automático |
| ~~**Qué ve un rol no administrador** en la pantalla de módulos~~ — **resuelta el 2026-08-18: el catálogo entero, con su estado y sin acciones** | 0 | Era una decisión de producto pequeña, y condicionaba la navegación |
| ~~**Si el puerto de dato maestro basta cuando llega el segundo consumidor**~~ — **resuelta el 2026-08-19: no se ensancha**. Filtrar por categoría sería la funcionalidad equivocada —de las catorce de Proveedores casi todas son servicios técnicos, así que recortar escondería justo al contacto que hace falta— y lo que sí hacía falta, distinguirlos de un vistazo, ya cabe en el `detail` que el puerto entrega. El disparador de revisarlo queda escrito | 5 | El puerto se diseñó **con un solo consumidor delante** en el Hito 4, y CMMS es el segundo: es la primera ocasión real de comprobarlo |

Y una que **no vence en esta fase** y conviene no confundir: el traspaso del
recordatorio de devolución al módulo de préstamos avanzados, único traspaso
previsto del catálogo de eventos. Ninguno de los dos módulos implicados entra
aquí.

## Lo que sigue fuera, y por qué

De la lista que dejó abierta la Fase 1, **estas dos no entran** y conviene decirlo
en vez de dejar que se dé por supuesto:

- **La baja de un hogar y el borrado de sus ficheros**, junto con la del avatar al
  cerrar la cuenta. Siguen sin caso de uso que las active, y ningún módulo de esta
  fase lo trae.
- **La conversión de HEIC**, que sigue siendo la carencia más visible para el
  usuario más probable y sigue costando lo mismo: un decodificador wasm en el
  cliente o decodificar en servidor contradiciendo la sección que lo excluyó. No
  la resuelve un módulo, así que no se cuela en uno.

Y una que la Fase 2 **acerca sin resolver**: el **Transactional Outbox** que
nombra la sección 5.2.2 del README. Con cuatro módulos escuchando, perder un
evento en un reinicio deja de ser teórico. La siembra desde estado que introduce
el Hito 0 lo mitiga —un módulo desincronizado se puede volver a sembrar— pero no
lo arregla. Queda anotado aquí como candidato de la Fase 3, con su síntoma
conocido.

## Historial

| Fecha | Cambio |
|---|---|
| 2026-08-19 | **Hito 5 completado, y con él los cuatro módulos de prioridad alta**: Mantenimiento (CMMS) llega con tres tablas que suben el recuento de veinticinco a veintiocho, once operaciones que llevan el contrato a noventa y ocho, y el sexto recorrido vertical de la batería. Es el único módulo cuya frontera principal se ha escrito **sin el otro lado delante** —**de CMMS es el cuándo y del planificador de tareas el quién lo hace**—, con sus tres consecuencias cumplidas en código: ninguna tabla lleva responsable, no hay ocurrencias materializadas y no consume `UserDeactivated`. Es también **la prueba de verdad del puerto de dato maestro**, que **no se ensancha** —filtrar por categoría escondería justo al contacto que hace falta— y que de paso ejercita por fin la garantía que Proveedores declaró por adelantado: un contacto retirado sigue siendo legible por su identificador. Y el primero cuyo **aviso por fecha se rearma**, colgado de la próxima fecha prevista y no del plan. Se corrige el catálogo de eventos de 5.2.3 —`AssetCreated` abre la **ficha** de la máquina y no un plan por defecto, que no se sostiene— y se retira la última promesa autoprogramada del Hito 0, el campo `milestone`. El recorrido vertical vuelve a encontrar lo que solo se ve en un navegador: **recargar devolvía al login con la sesión viva en el servidor**. Doce decisiones que la definición no preveía |
| 2026-08-19 | **Hito 4 completado**: el módulo que cierra el ciclo de la reposición y **retira el riesgo arquitectónico principal de la fase** —dos módulos que se hablan sin depender uno de que el otro esté activo—, con las dos mitades medidas y un tercer hogar con los dos encendidos como comparación. Vence la pregunta heredada de **cómo lee un módulo el dato maestro de otro**: un puerto en plataforma que **no nombra a ningún módulo**, con la degradación puesta en plataforma y la lista de excepciones de ArchUnit intacta. Compras es además **el primero que escribe en el core** —recibir invoca la entrada de consumibles, que crea existencias, y eso deja asiento en el cuaderno de Warehouse—. Dos tablas con RLS y `FORCE` que suben el recuento de veintitrés a veinticinco, diez operaciones en el contrato, siembra desde los consumibles a cero del core, y un arreglo en Warehouse que su primer consumidor destapó: `StockDepleted` no se publicaba sin mínimo declarado. **Con esto los cuatro riesgos están retirados**, y se declara el punto de corte: la fase no se parte y sigue con CMMS |
| 2026-08-19 | **Hito 3 completado**: el primer módulo que reacciona al core —seis de los trece eventos por la base de handler del Hito 0, con la prueba de que un hogar apagado no ve ni una fila escrita—, cuatro tablas con RLS y `FORCE` que suben el recuento de diecinueve a veintitrés, diez operaciones en el contrato, la primera siembra de verdad **e idempotente**, y los dos primeros avisos por fecha de un módulo, leídos del Mailpit real y sin repetirse cada noche. Se resuelven las dos preguntas heredadas: **peso y volumen van al core y van en el artículo** —con su propia migración `V11`, porque es un cambio del core— y la conversión de unidad de compra ya estaba guardada en `packSize`. Se construye el `Combobox` aplazado desde la Fase 1. Once decisiones que la definición no preveía |
| 2026-08-18 | **Hito 2 completado**: el primer módulo de verdad recorre el camino entero —ficha antes del código, dominio, migración `V9` con RLS y `FORCE`, siete operaciones en el contrato, siembra vacía escrita igual, pantallas bajo `/proveedores` y recorrido vertical en la batería existente—. El gate tapa por fin una ruta que existe: `403` apagado, `200` encendido. `ErrorCode` y la familia de `DomainError` se mudan a plataforma, que era la deuda que la ADR-010 dejó con su condición de revisión, y con ella se retiran `UnknownModule` y `UnknownNotice`. Diez decisiones que la definición no preveía |
| 2026-08-18 | **Hito 1 completado**: un solo recorrido periódico en `com.drp.platform`, hogar a hogar sin `BYPASSRLS` y saltándose los que tengan apagado el módulo que pide la comprobación; los tres procesos diarios del core pasan a ser comprobaciones y se ejecutan de verdad; `household_notices` con RLS y `FORCE`, tres operaciones en el contrato, bandeja en el cliente y resumen diario leído del Mailpit real. Correo y lista de hogares se mudan a plataforma en lugar de ensanchar la excepción de ArchUnit. ADR-011, y cuatro decisiones que la definición no preveía |
| 2026-08-18 | **Hito 0 completado**: fronteras de módulo con ArchUnit medido en los dos sentidos, `household_modules` con RLS y `FORCE`, gate en las tres capas, tres operaciones en el contrato, navegación en dos grupos y ADR-010. Se resuelve la pregunta que este hito tenía asignada y quedan anotadas tres decisiones que la definición no preveía |
| 2026-08-18 | Se crea al planificar la Fase 2: alcance de cuatro módulos sobre activación por hogar y capacidad de plataforma, siete hitos, criterio de aceptación con dos ADR nuevas, y las tres decisiones de arranque tomadas antes de empezar |
