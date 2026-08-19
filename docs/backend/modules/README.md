# Módulos del backend

Este directorio será el catálogo canónico de módulos del monolito. Cada módulo
debe tener un documento propio y declarar sus límites antes de depender de otro.

**Este catálogo fija el nombre y la responsabilidad de cada módulo. El estado y
la prioridad se mantienen en la sección 4.2 del
[`README.md`](../../../README.md) raíz, y solo allí.** Cada dato tiene un único
dueño porque tenerlo en dos sitios acaba, sin falta, en dos versiones distintas —
es exactamente lo que pasó con los nombres de «Mantenimiento (CMMS)» y «Eventos
temporales», que durante un tiempo aparecían de otra forma en el README.

## Catálogo

| Módulo | Responsabilidad | Documento |
|---|---|---|
| Core de recursos y activos | Gestión común de recursos y activos domésticos | [`common/product/`](../../common/product/README.md) (trasladado desde la §4.1 del README al arrancar la Fase 1) |
| Proveedores y contactos de servicio | Quién arregla, quién cobra y quién responde de una garantía | [`suppliers.md`](suppliers.md) |
| Compras y lista de la compra | Qué falta, qué hay que reponer y qué está pedido | [`purchasing.md`](purchasing.md) |
| Warehouse | Existencias, ubicaciones y movimientos | [`warehouse.md`](warehouse.md) |
| Mantenimiento (CMMS) | Planificación y seguimiento del mantenimiento | Pendiente |
| Planificador de tareas | Organización y seguimiento de tareas | Pendiente |
| Gastos y presupuesto | Coste de lo que entra en el hogar y presupuesto por periodo | Pendiente |
| Eventos temporales | Hechos o periodos que afectan a recursos | Pendiente |
| Gestión avanzada de préstamos | Recordatorios, penalizaciones, valoraciones e histórico | Pendiente |
| Recetas y menú semanal | Consumo planificado de existencias a partir de recetas | Pendiente |
| Reservas de uso | Reserva de un asset duradero para una ventana de tiempo | Pendiente |
| Fin de vida | Destino del asset al causar baja: venta, donación o retirada | Pendiente |
| Garantías y seguros | Cobertura y vencimiento sobre un asset duradero | Pendiente |
| Mascotas y plantas | Cuidados recurrentes de los seres vivos de la casa | Pendiente |

Usa [`module-template.md`](module-template.md) para documentar un módulo nuevo.

**Los cuatro de prioridad alta son el alcance de la Fase 2**, y cada uno escribe
su ficha con esa plantilla **antes** de su primera línea de código: este catálogo
existe para que un módulo declare sus límites antes de depender de otro, y una
ficha escrita después es una descripción y no un límite. El reparto en hitos, en
[`phase-2-roadmap.md`](../../common/product/phase-2-roadmap.md).

## Dónde vive un módulo, y qué lo separa del core

Desde el **Hito 0 de la Fase 2** (2026-08-18) el backend está repartido en tres
árboles, y las fronteras no son un acuerdo sino una prueba que falla la
construcción:

```text
com.drp.platform.*      Bus, TenantContext, paginación y la activación de módulos
com.drp.core.*          El core, con su reparto en capas intacto
com.drp.module.<key>.*  Un árbol por módulo, con su propio domain/application/adapter
```

Un módulo declara su `ModuleDescriptor` —clave, nombre, descripción y prefijo de
ruta— en su propio árbol, y con eso entra en el catálogo que un hogar puede
encender. Todo lo que cuelgue de su prefijo responde `403 MODULE_INACTIVE`
mientras esté apagado; sus handlers de evento heredan de `ModuleEventHandler` y no
hacen nada para un hogar que no lo tenga activo; y activarlo ejecuta su
`ModuleSeeder`, que **lee el estado actual del core** en lugar de esperar eventos
que ya pasaron.

Los cuatro módulos de la Fase 2 tienen ya su árbol con la declaración dentro. Lo
demás —dominio, tablas, contrato y pantallas— llega con su hito, y **después** de
su ficha. El detalle completo está en la
[`ADR-010`](../../common/architecture/decisions/ADR-010-module-boundaries-and-activation.md).

**El primero que ha recorrido ese camino entero es Proveedores**, en el Hito 2
(2026-08-18): su [`ficha`](suppliers.md) se escribió antes que su primera línea de
código y es la que enseña qué contiene una de verdad —siete operaciones, dos
tablas en `public` con RLS y `FORCE`, cinco códigos de error propios, ningún
evento publicado y ninguna comprobación periódica, con el motivo de cada
ausencia—. Los tres que vienen detrás se escriben con esa como referencia.

**El segundo es Warehouse**, en el Hito 3 (2026-08-18), y es el que estrena las
dos cosas que Proveedores no podía enseñar: **un módulo que reacciona a lo que
pasa en el core** —seis de los trece eventos, por la base de handler del Hito 0— y
**una siembra que lee algo**. Su [`ficha`](warehouse.md) añade además la sección
que ninguna otra necesitaba todavía: **la frontera contra el core escrita sin
ambigüedad**, porque es el primer módulo cuyo dominio roza un dato que el core ya
guarda. El core mantiene **un contador** —`quantity`, en la `unit` que pone el
artículo— y consumos, mínimos, caducidad y lotes son del módulo; Warehouse **no
lleva un segundo contador** y lee el del core cuando lo necesita.

**El tercero es Compras**, en el Hito 4 (2026-08-19), y es el que retira el
riesgo arquitectónico principal de la fase: **dos módulos que se hablan sin
depender uno de que el otro esté activo**. Su [`ficha`](purchasing.md) trae dos
cosas que ninguna anterior podía traer. La primera es **cómo lee un módulo el
dato maestro de otro**, que Proveedores dejó abierta con destinatario aquí: se
resuelve con un **puerto en plataforma que no nombra a ningún módulo**
—`MasterDataDirectory`, pedido por la clave del módulo dueño, con la misma forma
que ya tenían `ModuleSeeder` y `ScheduledCheck`— y con la degradación puesta en
plataforma, de modo que un directorio de un módulo apagado responde vacío sin que
el consumidor tenga una sola rama para ello. La segunda es que **es el primer
módulo que escribe en el core**: cerrar una compra invoca
`RegisterConsumableIntake`, que crea existencias, y eso deja un asiento en el
cuaderno de Warehouse sin que ninguno de los dos módulos sepa del otro.

**Y una regla que ese módulo estrenó:** la ficha declara **los nombres de tabla
que el módulo posee**, para que otro no los tome. Es lo que convierte «cada módulo
tiene sus tablas» en algo comprobable antes de escribir la migración, ahora que
todas viven en `public` —el esquema propio es cosa del módulo de prueba, que lo
usa para no falsear el recuento de tablas del modelo.

## Dos reglas que ya condicionan cualquier módulo

- **Un módulo no depende de que otro esté activo.** Toda comunicación entre
  módulos pasa por el event bus, y el core publica sin saber quién escucha. Desde
  el Hito 0 esto lo vigila **ArchUnit**: ningún `com.drp.module.a` puede
  referenciar a `com.drp.module.b`, y la regla está medida en los dos sentidos. Es el
  motivo por el que avisar por una fecha —caducidad, revisión, vencimiento,
  devolución, riego— no tiene módulo propio: cada uno posee su regla, y programar
  la comprobación y entregar el aviso son capacidad de plataforma.
- **Lo que el core no guarda, no se le pide.** El core mantiene un contador de
  cantidad, la procedencia de un asset y su documentación; consumos, mínimos,
  caducidades, importes y coberturas son de quien corresponda de esta lista. Y hay
  cosas que el core no modela en absoluto: un ser vivo no es material del hogar,
  así que mascotas y plantas trae su propia entidad en vez de forzarla en `assets`.
