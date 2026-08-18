# Roadmap de la Fase 2 (Módulos activables)

| Campo | Valor |
|---|---|
| Estado | Vigente |
| Responsable | Equipo DRP |
| Ámbito | Ejecución de la Fase 2 |
| Última revisión | 2026-08-18 |

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

### Hito 2 — Proveedores y contactos de servicio · **Pendiente**

El primer módulo de verdad, y el más pequeño a propósito: lo que se está probando
aquí es el camino completo de un módulo, no su dominio.

- [ ] **Ficha del módulo** en `docs/backend/modules/`, escrita antes del código.
- [ ] **Dominio**: el contacto de servicio —quién arregla, quién cobra, quién
      responde de una garantía— con su categoría de servicio, sus datos de
      contacto y su relación opcional con assets y ubicaciones del core.
- [ ] **Su migración**, con `household_id`, RLS y `FORCE` en todas sus tablas.
- [ ] **Sus operaciones en el contrato** bajo `/api/v1/suppliers`, y su pantalla
      bajo `/proveedores`.
- [ ] **Su siembra al activarse**, aunque en este módulo esté vacía: se escribe
      igual, porque es el punto de extensión que los tres siguientes sí usan.
- [ ] **Recorrido vertical propio**, añadido a la batería existente y no en una
      suite paralela.

> **Un dato maestro sin lector todavía.** Proveedores no tiene consumidor hasta el
> Hito 4, y eso es deliberado: es el módulo con el que se aprende el camino antes
> de recorrerlo con un dominio grande encima.

### Hito 3 — Warehouse · **Pendiente**

El módulo con más dominio de los cuatro y el que más habla con el core.

- [ ] **Ficha del módulo**, con la frontera contra el core escrita sin ambigüedad:
      el core mantiene **un contador**, y consumos, mínimos, caducidad y lotes son
      de aquí.
- [ ] **Dominio**: movimientos de existencias, stock mínimo por artículo,
      caducidad y lotes.
- [ ] **Consumo de eventos del core**: `ArticleCreated`, `AssetCreated`,
      `AssetMoved`, `AssetQuantityChanged`, `AssetDeactivated` y `LocationCreated`,
      todos por la base de handler del Hito 0.
- [ ] **Su siembra al activarse**, recorriendo las existencias que el hogar ya
      tenga: es la primera de verdad, y la que demuestra la regla.
- [ ] **Sus avisos** —caducidad próxima, mínimo alcanzado— sobre la plataforma del
      Hito 1, con su regla propia.
- [ ] **Sus eventos publicados**, que son la materia prima del Hito 4.
- [ ] **Resolver el peso y el volumen de un asset** (ver más abajo).
- [ ] **Sus pantallas** bajo `/almacen`, y su recorrido vertical.

### Hito 4 — Compras y lista de la compra · **Pendiente**

El hito que cierra el ciclo y el que retira el riesgo arquitectónico principal:
**dos módulos que se hablan sin depender uno de que el otro esté activo**.

- [ ] **Ficha del módulo**, con la frontera contra Warehouse: Warehouse detecta la
      falta, Compras decide qué se compra y cuándo.
- [ ] **Dominio**: lista de la compra, estado de lo pedido, y el cierre de la
      compra que termina en una entrada de consumible del core.
- [ ] **Consumo de los eventos de Warehouse**, con la prueba que importa:
      **Warehouse apagado y Compras encendido, y ninguno de los dos se rompe**; y
      al revés.
- [ ] **Lectura de Proveedores** para saber dónde se compra, degradando
      limpiamente si ese módulo está apagado.
- [ ] **El cierre del ciclo**: comprar y recibir acaba llamando a la entrada de
      consumibles del core, que ya existe y **suma** sobre la existencia de esa
      ubicación.
- [ ] **Sus pantallas** bajo `/compras`, y su recorrido vertical.

> **Aquí está el punto de corte de la fase.** Cerrado este hito, los cuatro
> riesgos de la tabla de alcance están retirados y hay tres módulos funcionando.

### Hito 5 — Mantenimiento (CMMS) · **Pendiente**

- [ ] **Ficha del módulo**, con la frontera contra el planificador de tareas —que
      no existe todavía— escrita por adelantado.
- [ ] **Dominio**: planes de mantenimiento preventivo sobre un asset `DURABLE`,
      intervenciones correctivas, e histórico.
- [ ] **Consumo de `AssetCreated`, `AssetDeactivated` y `DocumentAttached`**, que
      es el ejemplo con el que el README explica el bus desde la Fase 0: dar de
      alta la caldera genera su plan, y el manual adjunto queda enlazado en él.
- [ ] **Su siembra al activarse**, sobre los `DURABLE` que el hogar ya tenga.
- [ ] **Lectura de Proveedores** para el servicio técnico asignado a un plan.
- [ ] **Sus avisos de revisión** sobre la plataforma del Hito 1.
- [ ] **Sus pantallas** bajo `/mantenimiento`, y su recorrido vertical.

### Hito 6 — Cierre de la fase · **Pendiente**

Existe porque la Fase 1 aprendió que no se cierra solo: el Hito 4 dio por hecho un
criterio de accesibilidad que no había cubierto, y seis documentos del frontend se
quedaron diciendo «esto llega en el Hito 4» hasta que una sesión posterior volvió
a por ellos.

- [ ] **Barrido de aislamiento sobre las operaciones nuevas**, con la misma forma
      que el de la Fase 1: autenticado como hogar A, ninguna operación de ningún
      módulo devuelve ni modifica datos del hogar B, ni por identificador directo.
- [ ] **Auditoría de accesibilidad completa** de las pantallas nuevas: teclado,
      foco visible en cada parada, reflujo de 320 px a ultrawide y axe en los dos
      modos. No basta con axe.
- [ ] **Barrido de las promesas aplazadas**: buscar en la documentación toda frase
      del tipo «lo hará el hito siguiente» y cerrarla o convertirla en tarea con
      destinatario.
- [ ] **Volver a medir la capacidad**, porque cuatro módulos cambian el consumo de
      disco por hogar, que es lo que decidió el VPS.
- [ ] **Actualizar el deck de marketing**, que no avisa cuando se queda atrás.
- [ ] **README**: sección 4.2 con los cuatro módulos en su estado real, sección 8
      con la fase cerrada, sección 10 con su fila de historial.

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
del alcance nuevo. Se resuelven en el hito que las toca y se anotan en
[`decisions.md`](decisions.md), no aquí.

| Pregunta | Hito | Por qué vence ahora |
|---|---|---|
| **Peso y volumen de un asset**, de los que depende que el aviso de capacidad de una ubicación sirva de algo | 3 | Su destinatario asignado era Warehouse, y Warehouse llega. Hoy el aviso solo cuenta unidades |
| **Unidad de compra frente a unidad de consumo**, y su conversión | 3 y 4 | El core fija que la unidad la pone el artículo y que convertir es de Warehouse; Compras es quien lo necesita de verdad |
| **Qué pasa cuando un consumible llega a cero**: si entra solo en la lista de la compra o hace falta decirlo | 4 | El catálogo de eventos lo da por hecho, y nadie ha decidido si es automático |
| ~~**Qué ve un rol no administrador** en la pantalla de módulos~~ — **resuelta el 2026-08-18: el catálogo entero, con su estado y sin acciones** | 0 | Era una decisión de producto pequeña, y condicionaba la navegación |

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
| 2026-08-18 | **Hito 1 completado**: un solo recorrido periódico en `com.drp.platform`, hogar a hogar sin `BYPASSRLS` y saltándose los que tengan apagado el módulo que pide la comprobación; los tres procesos diarios del core pasan a ser comprobaciones y se ejecutan de verdad; `household_notices` con RLS y `FORCE`, tres operaciones en el contrato, bandeja en el cliente y resumen diario leído del Mailpit real. Correo y lista de hogares se mudan a plataforma en lugar de ensanchar la excepción de ArchUnit. ADR-011, y cuatro decisiones que la definición no preveía |
| 2026-08-18 | **Hito 0 completado**: fronteras de módulo con ArchUnit medido en los dos sentidos, `household_modules` con RLS y `FORCE`, gate en las tres capas, tres operaciones en el contrato, navegación en dos grupos y ADR-010. Se resuelve la pregunta que este hito tenía asignada y quedan anotadas tres decisiones que la definición no preveía |
| 2026-08-18 | Se crea al planificar la Fase 2: alcance de cuatro módulos sobre activación por hogar y capacidad de plataforma, siete hitos, criterio de aceptación con dos ADR nuevas, y las tres decisiones de arranque tomadas antes de empezar |
