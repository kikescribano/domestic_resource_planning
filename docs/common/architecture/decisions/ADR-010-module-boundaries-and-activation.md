# ADR-010: Fronteras de módulo y activación por hogar

- Estado: accepted
- Fecha: 2026-08-18
- Responsables: Equipo DRP
- Ámbito: common
- Sustituye: Ninguna

## Contexto

La Fase 1 entregó el core entero y la Fase 2 entrega **cuatro módulos**. Entre
una cosa y la otra falta lo que convierte un módulo en un módulo, y la
planificación de la Fase 2 destapó que **no existe nada de ello**:

- **El backend no está empaquetado por módulos.** Es
  `com.drp.{domain,application,adapter}`, que es el reparto correcto para un core
  y no expresa ninguna frontera: nada impide que Warehouse importe una clase de
  CMMS y que la dependencia solo se descubra el día que alguien quiera apagar uno
  de los dos.
- **No existe ninguna noción de activación.** Ni tabla, ni claim en el token, ni
  comprobación en la API, ni nada en el frontend.
- **La navegación no admite cuatro entradas más.** Ocho enlaces en una barra
  inferior de móvil ya daban 40 px de ancho a 320 px, por debajo de los 44 px que
  [`look-and-feel.md`](../../../frontend/product-design/look-and-feel.md) exige de
  todo objetivo táctil. Doce no caben de ninguna manera.

Sin esto, un módulo no es un módulo sino una funcionalidad más del core: se
despliega para todos, no se puede apagar, y la única frontera que lo separa del
core es la buena voluntad de quien lo escribe.

Tres decisiones de producto vienen dadas del
[roadmap de la Fase 2](../../product/phase-2-roadmap.md) y esta ADR las formaliza,
no las revisa: **la activación es por hogar y la decide su administrador sobre el
catálogo completo**, **desactivar conserva los datos y los oculta**, y
**programar y entregar avisos es plataforma y no un módulo** —eso último se
detalla en la ADR-011.

## Decisión

### 1. Tres árboles de paquetes y una raíz de composición

```text
com.drp.platform.*      Bus, TenantContext, paginación, activación de módulos
com.drp.core.*          El core, con su reparto en capas intacto
com.drp.module.<key>.*  Un árbol por módulo, con su propio domain/application/adapter
com.drp                 DrpApplication y config: quien cablea todo
```

El core se renombra entero a `com.drp.core` **en un commit propio y sin ningún
otro cambio dentro**. La alternativa —dejarlo donde estaba y colgar los módulos
aparte— ahorra ese trabajo y deja una asimetría que hay que explicar cada vez.

### 2. Cuatro reglas de ArchUnit que fallan la construcción

No un acuerdo: una prueba. Y **cada una medida en los dos sentidos**, ejecutando
la misma regla sobre un árbol de mentira que la incumple y afirmando que falla.
Sin esa segunda mitad, un patrón de paquete mal escrito —`com.drp.modules..` en
vez de `com.drp.module..`— pasa igual de verde sin vigilar nada.

| Regla | Por qué |
|---|---|
| Ningún `com.drp.module.a` referencia a `com.drp.module.b` | Un módulo que importa de otro deja de poder apagarse por separado |
| `com.drp.core` no referencia a ningún módulo | **Es la que de verdad protege:** impide que el core acabe sabiendo quién le escucha, que es la propiedad entera del event bus |
| `com.drp.platform` no se apoya en `com.drp.core` | Lo compartido no puede depender de lo que comparte |
| `com.drp.platform` no referencia a ningún módulo | Plataforma ofrece el mecanismo y no conoce a sus usuarios |

**La tercera regla tiene una excepción, y está nombrada dentro de la propia
regla:** `SessionClaims`. La sesión es hoy del core porque el core fue lo primero
que existió, y de ella sale la autoría de una activación. La lista vive en el
código de la regla y no en un comentario, de modo que ampliarla es un cambio
visible en la revisión; y hay una prueba que afirma que sigue teniendo **un solo
nombre**.

`module → core` **sí está permitido**, y es deliberado: un módulo lee el estado
del core —Warehouse las existencias, CMMS los `DURABLE`— y eso es exactamente lo
que la fase quiere demostrar. La dirección prohibida es la contraria.

### 3. La activación vive en una tabla del hogar

`household_modules`, con `household_id`, **RLS y `FORCE`** como cualquier tabla
del core: qué módulos tiene un hogar es un dato suyo, no una configuración
global. Guarda estado y las dos fechas —cuándo se encendió y cuándo se apagó—,
la autoría apunta a la **pertenencia** y la clave ajena es compuesta, como en
todo el core.

**No hay `CHECK` con la lista de claves** —el catálogo vive en código, y
congelarlo en el esquema obligaría a una migración por módulo nuevo y dejaría
fuera al módulo de prueba— y **no se siembra ninguna fila al crear un hogar**: la
ausencia de fila ya significa inactivo.

### 4. El catálogo se declara en código, y lo declara cada módulo

Un `ModuleDescriptor` por módulo —clave, nombre, descripción y prefijo de ruta—
declarado en el árbol del propio módulo. El `ModuleRegistry` los recoge todos y
comprueba al arrancar que no hay claves repetidas ni prefijos que se solapen: las
dos cosas se ven al arrancar o no se ven nunca.

Que el prefijo lo declare el módulo es lo que obliga a que el que protege el gate
y el que publica el controlador sean por fuerza el mismo.

### 5. El gate, en tres capas

| Capa | Qué hace |
|---|---|
| **HTTP** | Todo lo que cuelga del prefijo de un módulo inactivo responde `403` con código `MODULE_INACTIVE` |
| **Event bus** | Un handler de módulo no hace nada si el módulo está inactivo **para el hogar de ese evento** |
| **Frontend** | La navegación y las rutas del módulo solo aparecen si está activo; entrar a mano en una ruta apagada lleva a la pantalla que la ofrece |

**Es `403` y no `404`.** Un módulo apagado no es un secreto —el hogar lo tiene en
su catálogo— y el cliente necesita distinguir «esa ruta no existe» de «actívalo»
para poder ofrecer la activación en lugar de enseñar un error.

**En HTTP es un filtro y no un `HandlerInterceptor`**, que era lo previsto. Es el
mismo argumento por el que el alcance del token de préstamo se comprueba en el
filtro y no en el controlador: un interceptor solo alcanza a las rutas que ya
tienen manejador, así que lo que un módulo aún no ha construido respondería `404`
—justo la confusión que el `403` evita— y una operación añadida mañana bajo el
prefijo nacería sin gate hasta que alguien se acordase de registrarla.

Va **detrás de la autorización** en la cadena de seguridad. Puesto antes, una
petición sin token a la ruta de un módulo respondería `403 MODULE_INACTIVE` —sin
hogar en el contexto no hay ningún módulo activo— en lugar del `401` que le
corresponde.

**En el event bus la comprobación va dentro del handler**, sobre el `householdId`
del evento y no sobre el contexto: lo que nace de un recorrido periódico o de
otro handler no garantiza contexto, y el único sitio que sitúa a un handler con
certeza es el sobre del evento. Filtrar al publicar exigiría que el bus supiera
qué módulo escucha cada evento, que es precisamente lo que la sección 5.2 del
README evita.

**Y `ModuleEventHandler` abre la transacción `REQUIRES_NEW` por el módulo**, en
lugar de dejarlo escrito como norma. Es una regla que solo se incumple una vez y
cuyos dos síntomas no se parecen a la causa: unirse a la transacción del core
devuelve **cero filas** —su `SET LOCAL app.household_id` ya no vale— y un handler
unido que falle la marca `rollbackOnly` y **se lleva por delante el alta que
originó el evento**, que es la única forma conocida de que un módulo tumbe al
core.

La respuesta se resuelve **una vez por petición**, en una caché indexada por
hogar: indexarla es lo que la hace segura por construcción, porque un hilo que
volviera del pool con la entrada anterior no puede responder por el hogar
equivocado.

### 6. Activar un módulo lo siembra desde el estado actual, no reproduciendo eventos

La entrega del bus es at-least-once y **en memoria**: un módulo activado hoy no
vio el `AssetCreated` de hace un mes y no hay dónde ir a buscarlo. Cada módulo
expone su `ModuleSeeder`, que **lee lo que ya hay**. La siembra corre **dentro de
la transacción de la activación**: si falla, el módulo no queda encendido, porque
un módulo activo con la mitad de los datos es un estado que nadie sabría reparar.
Y activar es idempotente, así que reactivar no vuelve a sembrar.

El día que exista el Transactional Outbox esto no cambia: reproducir un año de
eventos para sembrar seguiría siendo peor que leer el estado.

### 7. Tres operaciones en el contrato

`listModules`, `activateModule` y `deactivateModule`, bajo `/api/v1/modules`, que
**no cuelga de ningún prefijo de módulo** —un gate sobre ella dejaría un módulo
apagado sin forma de encenderse—. La activación es un sub-recurso con `POST` y
`DELETE` y no un `PATCH` de estado, de modo que encender y apagar son dos
operaciones con `operationId` propio.

**Listar no se recorta por rol**; encender y apagar son solo de administrador.

### 8. La navegación se parte en dos grupos

Un mismo `<nav>` con **el hogar** y **los módulos**. En móvil, cuatro paradas y
«Más», que es una pantalla con el resto; desde `md`, la columna las enseña todas.
Un hogar sin módulos activos conserva sus ocho enlaces del core y ve una novena
entrada, que es la puerta para encender alguno.

Sigue siendo **un solo `<nav>`**: los rótulos de grupo son párrafos referenciados
con `aria-labelledby` —un `h2` ahí saldría antes que el `h1` del contenido— y lo
que no toca en móvil se oculta con CSS, sin duplicar el landmark.

## Alternativas consideradas

- **Dejar el core donde estaba y colgar los módulos aparte.** Ahorra el
  renombrado mecánico de todo el backend y deja una asimetría —dos árboles con
  reglas distintas— que hay que explicar cada vez que alguien pregunte dónde va
  algo. Se descarta por eso, y el renombrado se aísla en un commit propio para
  que su revisión sea trivial.
- **Un segundo nivel de «módulos ofrecibles por instalación».** Sería una tabla y
  una configuración que hoy no resuelven ningún problema; añadirlas el día que
  existan planes o licencias es una migración pequeña.
- **Borrar los datos al desactivar.** Destructivo e irreversible, y obligaría a
  definir el borrado en cascada de cuatro módulos antes de escribir el primero.
- **Bloquear la desactivación cuando hay datos.** Convierte la activación en casi
  irreversible y castiga justo al hogar que se atrevió a probar el módulo.
- **Responder `404` en las rutas de un módulo apagado.** Es lo que haría un
  sistema que quisiera ocultar su existencia, y aquí no hay nada que ocultar: el
  precio es que el cliente no puede distinguir «no existe» de «actívalo» y acaba
  enseñando un error donde debería ofrecer un botón.
- **El gate como `HandlerInterceptor`.** Es lo que la planificación proponía y
  cubre menos: solo las rutas con manejador. Se descarta por lo dicho arriba.
- **Un `ErrorHandler` global en el difusor de eventos** para que un handler que
  propague no deje sin evento a los demás. Ya estaba descartado en la Fase 1
  —ese manejador es global y se tragaría también los fallos de los eventos de
  ciclo de vida de Spring— y la norma de heredar de la clase base lo evita.
- **Sembrar reproduciendo el histórico de eventos.** No hay histórico: el bus es
  in-process y no persiste nada.
- **Alargar la lista de navegación hasta doce.** No cabe en el pulgar, y las
  ocho de antes ya incumplían el mínimo de 44 px a 320 px.

## Consecuencias

### Positivas

- Un módulo puede apagarse sin romper a ninguno de los otros, y la construcción
  lo comprueba en lugar de confiarlo a una convención.
- El core sigue sin saber quién le escucha, ahora con una regla que lo impide.
- Un hogar que no quiere módulos ve exactamente el producto que veía antes.
- Los cuatro hitos de módulo de la Fase 2 encuentran el camino hecho: declararse
  en el catálogo, publicar bajo su prefijo, escuchar eventos y sembrarse.
- El módulo de prueba —una tabla, una ruta y un handler, en el árbol de pruebas y
  sin desplegar— queda como prueba de regresión del mecanismo para toda la fase:
  cuando un módulo de verdad falle al activarse, es lo que dice si lo roto es el
  módulo o la activación.

### Costes y riesgos

- **El renombrado toca los 113 ficheros del backend.** Es mecánico, pero
  cualquier rama abierta sobre el árbol viejo se va a encontrar conflictos.
- **La excepción de `SessionClaims`** deja plataforma dependiendo del core en un
  punto. Está acotada y medida, pero es una grieta y hay que vigilar que no
  crezca.
- **La siembra corre dentro de la petición de activación.** Con cuatro módulos y
  un hogar grande puede ser lenta; para un hogar doméstico es aceptable, y el día
  que no lo sea habrá que sacarla a un proceso propio con estado visible.
- **La caché de activación es por hilo.** Fuera de una petición —un recorrido
  periódico— la entrada sobrevive al hogar en curso y se sustituye al pasar al
  siguiente: una activación que cambie a mitad de un barrido no se ve hasta el
  siguiente.
- **`ErrorCode` sigue siendo un enumerado del core** aunque el contrato tenga un
  solo enumerado de errores. El día que un módulo necesite un código propio habrá
  que mudarlo a plataforma.

## Validación o reversión

Se considera validada cuando, con un módulo inactivo:

1. **Su API responde `403 MODULE_INACTIVE`** —comprobado en el recorrido vertical
   contra la aplicación de verdad, con un token real y contra el filtro real, no
   contra un doble.
2. **Sus handlers no hacen nada**, y **sus datos siguen ahí** al volver a
   activarlo: lo demuestra el módulo de prueba, que escribe una fila, se apaga,
   deja de escribir, y al reactivarse devuelve lo que había.
3. **Su navegación no existe**, y entrar a mano en su ruta ofrece la activación.
4. **Activarlo lo siembra desde el estado actual** —el módulo de prueba cuenta
   las ubicaciones que ya había— y no reproduce eventos anteriores.
5. **ArchUnit falla** si un módulo referencia a otro, si el core referencia a un
   módulo o si plataforma se apoya en el core: comprobado introduciendo cada una
   de las tres dependencias prohibidas y afirmando que la regla revienta.
6. **Ninguna parada de la navegación baja de 44 px a 320 px**, medido sobre el
   DOM real.

Revisar cuando ocurra cualquiera de estas tres cosas:

- **La lista de excepciones de la tercera regla de ArchUnit deja de tener un solo
  nombre.** Es la señal de que la frontera entre plataforma y core se está
  disolviendo, y toca mudar `SessionClaims` —y `MemberRole` detrás— a plataforma.
- **Un módulo necesita saber de otro más allá de lo que el bus entrega.** Hoy la
  regla lo prohíbe; si el caso es legítimo, lo que hace falta es un puerto en
  plataforma, no una excepción.
- **Aparece un segundo nivel de activación** —planes, licencias, módulos
  ofrecibles por instalación—. Esta decisión lo deja fuera a propósito y lo
  convierte en una migración pequeña, pero es una ADR nueva y no un parche a
  esta.

**Revertir** es acotado y conviene que se sepa: quitando el filtro del gate y las
dos operaciones de escritura, todo lo demás queda inerte —la tabla se ignora, los
handlers de módulo dejan de comprobar nada y la navegación deja de tener grupo de
módulos— sin perder ningún dato. Lo que **no** es reversible barato es el
renombrado de paquetes, que es exactamente por lo que va en un commit propio.

## Posterior a esta decisión

Esta ADR **no se reescribe**; lo que sigue enlaza hacia adelante lo que la ha
alcanzado.

- **`ErrorCode` ya no es un enumerado del core.** Esta ADR lo dejó anotado entre
  sus costes con una condición explícita —«el día que un módulo necesite un
  código propio habrá que mudarlo a plataforma»— y ese día llegó con el **Hito 2
  de la Fase 2**, el primer módulo con reglas de negocio. La familia entera
  —`ErrorCode`, `BusinessRuleViolation`, `ValidationFailure` y
  `ResourceNotFound`— vive desde entonces en `com.drp.platform.error`.

  Lo que hizo urgente la mudanza es que **nada fallaba**: la dirección `módulo →
  core` que esta ADR permite hace que un módulo pueda lanzar un código propio sin
  que ninguna de las cuatro reglas se queje, y el resultado habría sido el core
  enumerando las reglas de sus módulos —lo mismo que la segunda regla impide en el
  otro sentido, pero sin nada que lo delate. La alternativa considerada era
  declarar el core catálogo único de códigos y aceptarlo por escrito; se descartó
  porque el contrato tiene un solo enumerado de errores en cualquiera de los dos
  casos, así que lo que se decidía no era el contrato sino **quién lo posee**. El
  razonamiento completo, con su residuo —plataforma nombra reglas de un módulo
  aunque no dependa de ninguna clase suya—, está en la sección 4.1.7 del
  [registro de decisiones](../../product/decisions.md).

  **Dos consecuencias sobre lo que esta ADR describe.** Los dos «no existe»
  propios de plataforma —`UnknownModule` y `UnknownNotice`— desaparecen: existían
  solo porque plataforma no podía lanzar el error del core sin invertir esta
  frontera. Y la lista de excepciones de la tercera regla **sigue teniendo un solo
  nombre**, `SessionClaims`, que es la condición de revisión que esta ADR fija.

- **El gate protege por fin una ruta que existe.** Mientras ningún módulo tuvo
  controlador, un módulo encendido respondía `404` —justo la confusión que el
  `403 MODULE_INACTIVE` evita—, así que el criterio de validación número 1 se
  comprobaba sobre una ruta vacía. Desde el Hito 2 el recorrido vertical mide las
  dos mitades sobre `/api/v1/suppliers`: `403` apagado y `200` encendido.

- **«Reactivar no vuelve a sembrar» dice lo contrario de lo que hace el
  código, y lo que se conserva es el código.** La sección 6 de esta ADR escribe
  «activar es idempotente, así que reactivar no vuelve a sembrar». Lo que
  `ActivateModule` hace es ahorrarse la siembra **solo cuando el módulo ya estaba
  `ACTIVE`**: pasar de `INACTIVE` a `ACTIVE` la ejecuta. Nadie lo había notado
  porque la siembra de Proveedores está vacía, y lo destapó el **Hito 3 de la
  Fase 2** al escribir la primera que lee algo.

  **La frase es la que estaba mal.** Un hogar que apagó Warehouse tres meses se
  perdió todos los eventos de ese periodo —el bus es in-process y no persiste
  nada, que es la premisa de la que sale la sección 6 entera— así que un módulo
  que no resembrara volvería con el cuaderno de hace tres meses y **nada lo
  diría**: la pantalla enseñaría una lista corta y verosímil. La alternativa
  considerada era **no resembrar y ofrecer una resincronización aparte**, con su
  operación y su botón; se descartó porque pone la corrección detrás de que
  alguien sepa que hace falta, y quien reactiva un módulo es justo quien no lo
  sabe.

  **La consecuencia para los módulos que vengan:** una siembra tiene que ser
  idempotente **por construcción y no por cuidado**. En Warehouse lo garantizan
  dos `ON CONFLICT DO NOTHING` sobre índices únicos y uno parcial que admite un
  solo asiento de apertura por existencia; resembrar completa lo que falte y no
  duplica nada. Lo que la reactivación **no** reconstruye es el histórico del
  periodo apagado, que para ese módulo no ocurrió y no se inventa. El
  razonamiento completo está en la sección 4.1.7 del
  [registro de decisiones](../../product/decisions.md) y en la
  [ficha de Warehouse](../../../backend/modules/warehouse.md).

- **El gate del event bus tapa por fin handlers que existen.** El criterio de
  validación número 2 de esta ADR se comprobaba con el módulo de prueba, que es
  para lo que existe. Desde el Hito 3 lo mide también un módulo desplegado: con
  Warehouse apagado, ninguno de los seis handlers que consume el core escribe una
  fila, y el hogar de al lado —con el módulo encendido— sí las tiene.

- **`ModuleScreen` envuelve a la pantalla del módulo en lugar de sustituirla.**
  La tercera capa del gate que esta ADR describe —«entrar a mano en una ruta
  apagada lleva a la pantalla que la ofrece»— sigue intacta y ahora convive con el
  contenido real del módulo, que solo se monta en la rama activa.
