# Módulo: Proveedores y contactos de servicio

| Campo | Valor |
|---|---|
| Estado | Vigente |
| Responsable | Equipo DRP |
| Clave | `SUPPLIERS` |
| Prefijo de ruta | `/api/v1/suppliers` |
| Última revisión | 2026-08-18 |

> **Esta ficha se escribió antes que la primera línea de código del módulo**, que
> es la regla que el [catálogo](README.md) fija y el motivo por el que existe: un
> módulo declara sus límites **antes** de que otro dependa de él, y una ficha
> escrita después es una descripción y no un límite. Aquí importa más que en los
> tres módulos siguientes, porque Proveedores **no tiene consumidor hasta el Hito
> 4**: lo que hoy se escribe aquí es lo único que Compras y CMMS van a poder leer
> cuando les toque.
>
> Escribirla por delante pagó dos veces, y las dos están más abajo: obligó a
> resolver dónde vive `ErrorCode` —el core no puede ser quien enumere las reglas
> de un módulo— y destapó que una clave ajena hacia el core, puesta con el
> reflejo del resto del modelo, **habría dejado a un módulo bloqueando una
> operación del core**.

## Responsabilidad

El **dato maestro de a quién se llama** cuando algo de la casa hay que arreglar,
revisar o reclamar: quién arregla, quién cobra y quién responde de una garantía,
con su categoría de servicio, sus datos de contacto y su relación opcional con lo
que el core ya tiene dado de alta.

Es dato maestro compartido, y ese es el motivo de que sea un módulo y no un
campo: sin él lo duplicarían CMMS, Compras, Gastos y Garantías, cada uno con su
propio «teléfono del fontanero» y su propia versión del número cuando cambie.

## Límites

### Incluido

- El **contacto de servicio**: nombre, categoría de servicio, persona de
  contacto, teléfono, correo, web, dirección y notas.
- La **retirada lógica** de un contacto que ya no se usa, que deja de ofrecerse y
  conserva lo que colgaba de él.
- El **enlace opcional** de un contacto con un asset o con una ubicación del
  core: la caldera, el coche, el piso de arriba.
- La regla de que **un contacto sin ninguna forma de contacto no sirve de nada**:
  al menos una de teléfono, correo o web.

### Fuera de alcance

- **Lo que cuesta una reparación.** Es de Gastos y presupuesto. Aquí no hay
  importes, ni tarifas, ni facturas: el core ya guarda la documentación de un
  asset, y la factura es un `Document` suyo.
- **El plan de mantenimiento, la intervención y su histórico.** Son de CMMS, que
  leerá de aquí el servicio técnico asignado a un plan.
- **Qué se compra, dónde y cuándo.** Es de Compras, que leerá de aquí dónde se
  compra.
- **Qué cubre una garantía y hasta cuándo.** Es de Garantías y seguros.
  Proveedores guarda **quién responde**, no qué se responde.
- **Avisar por una fecha.** Proveedores no tiene ninguna regla de fecha, así que
  **no declara ninguna `ScheduledCheck`** —ver [Operación](#operación).
- **Los datos fiscales** de quien factura. Nacen el día que exista Gastos, y
  nacen allí.

## Lenguaje de dominio

| Término | Significado |
|---|---|
| `Supplier` | Un contacto de servicio del hogar. Puede ser una empresa —el servicio técnico de la caldera— o una persona —el electricista del barrio—: el modelo no los distingue porque lo que el hogar necesita de los dos es lo mismo. |
| `ServiceCategory` | Qué clase de servicio presta, de una lista cerrada. Es un identificador, así que va en inglés y en `UPPER_SNAKE_CASE`; lo que se lee en pantalla es un dato y va en castellano. |
| `SupplierLink` | La relación de un contacto con **exactamente una** cosa del core: un asset o una ubicación. Nunca las dos, y nunca ninguna. |
| Retirada (`retiredAt`) | El contacto deja de ofrecerse al enlazar y sigue existiendo donde ya estaba. Es baja lógica, igual que la de una `Category` del core y por el mismo motivo: hay filas que lo referencian. |

## Casos de uso

| Caso de uso | Resultado observable |
|---|---|
| `ListSuppliers` | La lista del hogar, paginada, filtrable por categoría de servicio y por texto, y sin los retirados salvo que se pidan |
| `GetSupplier` | Un contacto con sus enlaces resueltos —el nombre del asset o de la ubicación, no solo su identificador |
| `CreateSupplier` | Un contacto nuevo. Falla si ya hay uno vigente con ese nombre o si no trae ninguna forma de contacto |
| `UpdateSupplier` | Modificación parcial, con la misma semántica de `PATCH` que el core: ausente no se toca, presente a nulo se borra |
| `RetireSupplier` | Deja de ofrecerse. Retirar dos veces no es un error, y no borra sus enlaces |
| `LinkSupplier` | Enlaza el contacto con un asset o una ubicación del hogar. Falla si ese enlace ya existe o si el destino no es del hogar |
| `UnlinkSupplier` | Deshace el enlace. El contacto y el destino siguen donde estaban |

Siete operaciones, y es a propósito el módulo más pequeño de los cuatro: lo que
la Fase 2 prueba aquí es **el camino completo de un módulo** —ficha, dominio,
migración, contrato, gate, siembra, pantallas y recorrido vertical—, no su
dominio. El dominio grande llega con Warehouse.

## Modelo de dominio

Un solo agregado, `Supplier`, con sus enlaces dentro. Los enlaces no tienen vida
propia: no se consultan sin su contacto, no se transfieren y desaparecen con él.

**Invariantes:**

1. **El nombre es único entre los vigentes del hogar**, sin distinguir mayúsculas
   ni acentos. Dos filas con el mismo teléfono son un duplicado que no se ve; dos
   con el mismo nombre, uno que se ve a la primera.
2. **Al menos una forma de contacto** —teléfono, correo o web—. Un contacto de
   servicio del que no se sabe cómo llamar no es un contacto.
3. **Un enlace apunta a exactamente una cosa**: un asset o una ubicación.
4. **Un contacto no se enlaza dos veces con lo mismo.**
5. **Un contacto retirado no admite enlaces nuevos.** Los que ya tenía se
   conservan: la caldera la sigue habiendo instalado quien la instaló.

Las cinco se comprueban en el caso de uso **y** en el esquema, que es la regla
del core y no una duplicación: la comprobación da el mensaje que el contrato
declara y la restricción cierra la carrera entre dos peticiones simultáneas, que
ninguna comprobación previa puede cerrar.

**La categoría de servicio es una lista cerrada y no un catálogo por hogar**, al
contrario que la `Category` del core. Es una decisión con precio y está anotada
en [Decisiones abiertas](#decisiones-abiertas) con su disparador.

## Contratos publicados

### API

Bajo `/api/v1/suppliers`, en [`openapi.yaml`](../../../openapi.yaml), con
`operationId` en las siete operaciones. Todo lo que cuelga de ese prefijo
responde `403 MODULE_INACTIVE` mientras el hogar no lo tenga encendido, y eso no
lo sabe ningún controlador del módulo: lo pone el filtro de plataforma sobre el
prefijo que **declara el propio módulo**.

Códigos de error propios, enumerados en el esquema `Error` del contrato:
`SUPPLIER_DUPLICATE`, `SUPPLIER_CONTACT_REQUIRED`, `SUPPLIER_LINK_DUPLICATE`,
`SUPPLIER_LINK_TARGET_INVALID` y `SUPPLIER_RETIRED`.

> **Y aquí es donde este módulo tuvo que decidir algo que no era suyo.**
> `ErrorCode` era un enumerado de `com.drp.core.domain`, y Proveedores es el
> primer módulo con reglas de negocio. Lanzar `BusinessRuleViolation` desde un
> módulo compila —la dirección `módulo → core` está permitida— y **ninguna regla
> de ArchUnit se queja**, pero deja al core enumerando las reglas de sus módulos,
> que es justo lo que la segunda regla existe para impedir en el otro sentido. La
> [ADR-010](../../common/architecture/decisions/ADR-010-module-boundaries-and-activation.md)
> lo había dejado anotado como deuda con esta condición de revisión exacta. Así
> que la familia entera —`ErrorCode`, `BusinessRuleViolation`, `ValidationFailure`
> y `ResourceNotFound`— se muda a `com.drp.platform.error`, que es la misma salida
> que la ADR-011 tomó con el correo: **mudar la clase, no ensanchar la grieta**.
> Está razonado en [`decisions.md`](../../common/product/decisions.md).

### Eventos

| Evento | Cuándo se publica | Consumidores conocidos | Versión |
|---|---|---|---|
| — | — | — | — |

**Proveedores no publica ningún evento hoy, y no es un olvido.** El criterio del
catálogo de eventos (README 5.2.3) es que **alguien lo necesite**, no la simetría
con las demás entidades, y hoy no hay nadie: sus dos consumidores llegan en los
Hitos 4 y 5. Publicar `SupplierCreated` ahora sería inventarse el contrato de una
conversación que todavía no ha empezado, con el agravante de que un evento
publicado es más difícil de retirar que de añadir.

**Lo que un consumidor futuro sí puede dar por hecho**, y queda declarado aquí
para que el Hito 4 no tenga que adivinarlo:

- Proveedores es **dato maestro de solo lectura** para quien no es Proveedores.
  Nadie más crea, modifica ni retira un contacto.
- Un contacto **retirado sigue siendo legible** por su identificador. Un plan de
  CMMS que apunte a quien ya no se llama tiene que poder decir a quién apuntaba.
- **Un consumidor tiene que degradar limpiamente si Proveedores está apagado**, y
  eso no es cortesía: es la regla de la ADR-010, y ninguna clase de
  `com.drp.module.suppliers` es importable desde otro módulo —ArchUnit falla la
  construcción—.

**Cómo se leerá desde otro módulo está sin decidir**, y su destinatario es el Hito
4: ver [Decisiones abiertas](#decisiones-abiertas).

## Dependencias consumidas

| De dónde | Qué | Para qué |
|---|---|---|
| `com.drp.platform.module` | `ModuleDescriptor`, `ModuleSeeder` | Existir en el catálogo y tener siembra |
| `com.drp.platform.page` | `Page`, `Pagination`, `PageResponse` | La misma paginación que las doce colecciones del core |
| `com.drp.platform.error` | `ErrorCode`, `BusinessRuleViolation`, `ResourceNotFound` | Las reglas de negocio, con la forma única del contrato |
| `com.drp.core.application.port` | `SessionClaims` | La autoría, que apunta a la **pertenencia** y sale del token |
| `com.drp.core` (por SQL, sin importar nada) | `assets`, `locations` | Resolver el destino de un enlace |

La dirección `módulo → core` **está permitida** y aquí se usa en su forma más
suave: el módulo **lee** el estado del core y no escribe en él ni una fila. Lo que
ninguna regla admite es la contraria.

**No consume el event bus.** No hay ningún `ModuleEventHandler` en este módulo:
nada de lo que pasa en el core cambia quién es el fontanero. Cuando Warehouse
estrene el consumo de eventos, lo hará sobre la base que el Hito 0 dejó escrita.

## Datos y transacciones

**Dos tablas, las dos en `public` y las dos con `household_id`, RLS y `FORCE`.**
En `public` y no en un esquema propio: el esquema aparte es la trampa del módulo
de prueba del Hito 0, que lo usa precisamente para **no** falsear el recuento de
tablas del modelo. Un módulo de verdad tiene que aparecer en ese recuento.

| Tabla | Qué guarda |
|---|---|
| `suppliers` | El contacto de servicio, con su categoría, sus datos y su retirada |
| `supplier_links` | El enlace con un asset **o** una ubicación, nunca los dos |

Los nombres son del módulo y quedan declarados aquí para que otro no los tome.

**Migración `V9`**, que deja el esquema completo desde una base vacía, políticas
incluidas.

**Las claves ajenas hacia el core van con `ON DELETE CASCADE`, y esa es la
decisión que esta ficha destapó.** El reflejo del resto del modelo es una clave
ajena compuesta `(household_id, id)` sin más, que impide apuntar a otro hogar; lo
que ese reflejo no ve es que **`DeleteLocation` borra la fila de verdad** —una
ubicación vacía no deja historial que preservar— y comprueba antes que no cuelgue
nada de ella… mirando ubicaciones y assets, que es todo lo que el core conoce. Con
una clave ajena que restringe, un enlace de Proveedores convertiría ese borrado en
una violación de restricción, o sea en un `500` **de una operación del core
causado por un módulo**. Que es exactamente lo que la Fase 2 promete que no puede
pasar.

Así que el enlace cede: borrar la ubicación se lleva por delante el enlace, y el
core no se entera de que existía. El precio —un enlace que desaparece sin que
nadie lo diga— es el correcto: lo que ya no existe no lo arregla nadie.

**Transacciones.** Nada especial: cada caso de uso, la suya, con el gestor
consciente de inquilino que fija `app.household_id`. No hay handlers de evento,
así que no aparece aquí la única regla que puede tumbar al core —el `REQUIRES_NEW`
de `ModuleEventHandler`—, que además desde el Hito 0 la abre la clase base.

**Siembra.** `SuppliersSeeder` **existe y está vacío**, y se escribe igual porque
es el punto de extensión que los tres módulos siguientes sí usan. Proveedores no
tiene nada que sembrar y el motivo se ve mejor desde la regla que desde el módulo:
sembrar es **leer el estado actual del core** para ponerse al día de lo que pasó
antes de encenderse, y en el core no hay ningún fontanero que leer. Warehouse
recorrerá las existencias y CMMS los `DURABLE`; aquí no hay nada detrás.

**Desactivar conserva.** Apagar el módulo no borra ni una fila: las dos tablas se
quedan como estaban y reactivarlo las devuelve tal cual.

## Seguridad

- **Aislamiento en dos capas, igual que el core.** Todo caso de uso filtra por el
  `householdId` del token y nunca por uno del cliente, y las dos tablas llevan
  política de RLS con `FORCE`.
- **Tercera capa que el core no tiene**: el gate. Con el módulo apagado, todo lo
  que cuelga del prefijo responde `403 MODULE_INACTIVE` sin llegar al controlador.
  Es la **primera vez que ese filtro se pone delante de un controlador que
  existe**: hasta este hito, un módulo encendido respondía `404` porque no había
  nada detrás.
- **Datos personales de terceros.** El teléfono y el correo del fontanero son
  datos personales de alguien que no es usuario del sistema. Viven en una tabla
  del hogar con RLS, que es una capa más de la que protege a `identities`, y **no
  se indexan por correo ni se buscan por él**: el filtro de texto de
  `ListSuppliers` mira el nombre, no el contacto.
- **Cualquier miembro del hogar puede leer y escribir.** No hay restricción por
  rol dentro del módulo: quien puede ver el inventario puede ver a quién se llama
  para arreglarlo. Lo que sí es solo de administrador es **encender y apagar el
  módulo**, y eso lo corta plataforma.

## Verificación

| Nivel | Qué se comprueba |
|---|---|
| Dominio | Las cinco invariantes, sin base de datos |
| Aplicación (integración) | Las siete operaciones contra PostgreSQL real, con usuario **sujeto a RLS** |
| Esquema | Que las dos tablas nuevas llevan `household_id`, RLS, `FORCE` y política, y que el recuento del modelo sube de diecisiete a diecinueve sin tocar la lista de tablas sin política |
| Adaptador HTTP | Las dos mitades del gate **sobre una ruta que existe**: apagado responde `403 MODULE_INACTIVE`; encendido, `200` |
| Recorrido vertical | Añadido a la batería existente y no en una suite paralela: encender el módulo, dar de alta un contacto, enlazarlo con una ubicación, verlo en la ficha, con axe en los dos modos, foco, teclado y reflujo de 320 px a ultrawide |

**El barrido de aislamiento** —autenticado como hogar A, ninguna operación
devuelve ni modifica datos del hogar B, ni por identificador directo— lo tiene
asignado el **Hito 6**, que es donde el roadmap lo puso para las operaciones de
toda la fase. Hoy `TenantIsolationSweepTest` cubre las treinta y ocho operaciones
de la Fase 1 y ninguna de plataforma. **Se deja allí a propósito y se dice aquí
para que no se dé por supuesto**: las siete operaciones de este módulo entran en
ese barrido, no en este hito.

## Operación

- **Nada periódico.** Proveedores no declara ninguna `ScheduledCheck`, así que el
  recorrido diario no entra aquí ni cuando el módulo está encendido. El punto de
  extensión existe desde el Hito 1 y **estrenarlo con un aviso inventado sería
  peor que no estrenarlo**: el primer módulo con una regla de fecha es Warehouse,
  con la caducidad.
- **Ningún aviso, ningún correo, ninguna métrica propia.** Lo que el módulo
  escribe lo escribe una persona desde su pantalla.
- **Recuperación.** No hay nada que reconstruir: el módulo no deriva su estado de
  ningún otro, así que no existe el caso «se ha quedado desincronizado» que la
  siembra resuelve en Warehouse y CMMS.

## Decisiones abiertas

- **Cómo lee otro módulo el dato maestro de Proveedores.** Vence en el **Hito 4**,
  que es el primero que lo necesita. ArchUnit prohíbe que Compras importe una
  clase de `com.drp.module.suppliers`, así que las salidas son dos: un **puerto en
  plataforma** que Proveedores implemente —que es lo que la ADR-010 señala en su
  condición de revisión— o **eventos** que el consumidor materialice en su propio
  lado. La primera es más directa y hace que el consumidor dependa de que el
  módulo esté encendido en el instante de la consulta; la segunda no, y a cambio
  obliga a cada consumidor a guardar su copia. **No se decide aquí** porque
  decidirla sin el consumidor delante es decidirla a ciegas, que es justo lo que
  esta ficha existe para evitar en el otro sentido.
- **Si la categoría de servicio tiene que pasar a catálogo por hogar.** Hoy es una
  lista cerrada con `OTHER`, que es lo contrario de lo que el core decidió para su
  `Category` —y la diferencia es deliberada: la del core clasifica **lo que el
  hogar tiene**, que es ilimitado, y esta clasifica **a qué se dedica quien viene
  a casa**, que en un hogar son doce entradas y no mil. El disparador está
  escrito: **el día que `OTHER` sea la categoría más usada**, la lista cerrada
  dejó de clasificar y toca convertirla en catálogo por hogar, con la siembra de
  este módulo —hoy vacía— como el sitio natural donde nacen sus valores por
  defecto. Responsable: quien abra el Hito 6, que es quien mira los datos reales
  de la fase.
- **Si un contacto retirado se puede recuperar.** Hoy no, igual que una `Category`
  del core, y por el mismo motivo: nadie lo ha pedido y la salida —volver a darlo
  de alta— existe. Se anota porque el nombre único **entre vigentes** hace que esa
  salida funcione sin chocar.

## Referencias

- [`ADR-010`](../../common/architecture/decisions/ADR-010-module-boundaries-and-activation.md):
  fronteras de paquete, activación por hogar y el gate en tres capas.
- [`ADR-011`](../../common/architecture/decisions/ADR-011-scheduled-checks-and-notice-delivery.md):
  el recorrido periódico y los avisos, que este módulo **no** usa.
- [`phase-2-roadmap.md`](../../common/product/phase-2-roadmap.md): el Hito 2 y su
  sitio en la fase.
- [`decisions.md`](../../common/product/decisions.md): las decisiones que este
  hito tomó y que la definición no preveía.
- Sección 4.2 del [`README`](../../../README.md): el estado y la prioridad del
  módulo, que viven allí y solo allí.

## Historial de cambios

| Fecha | Cambio | Autor |
|---|---|---|
| 2026-08-18 | Creación, **antes de la primera línea de código** del módulo, como pide el catálogo. Declara las siete operaciones, las dos tablas, las cinco invariantes, los cinco códigos de error, que no publica eventos ni declara comprobación periódica, y las tres decisiones abiertas con su destinatario. | Equipo DRP |
