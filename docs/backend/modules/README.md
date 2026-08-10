# Módulos del backend

Este directorio será el catálogo canónico de módulos del monolito. Cada módulo
debe tener un documento propio y declarar sus límites antes de depender de otro.

**El estado y la prioridad de cada módulo se mantienen en la sección 4.2 del
[`README.md`](../../../README.md) raíz, y solo allí.** Este catálogo aporta la
responsabilidad de cada uno y el enlace a su documento; duplicar aquí el estado
solo consigue que las dos listas se contradigan.

## Catálogo

| Módulo | Responsabilidad | Documento |
|---|---|---|
| Core de recursos y activos | Gestión común de recursos y activos domésticos | [`README.md`](../../../README.md) §4.1 (fuente vigente hasta el reparto de la Fase 1) |
| Proveedores y contactos de servicio | Quién arregla, quién cobra y quién responde de una garantía | Pendiente |
| Compras y lista de la compra | Qué falta, qué hay que reponer y qué está pedido | Pendiente |
| Warehouse | Existencias, ubicaciones y movimientos | Pendiente |
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

## Dos reglas que ya condicionan cualquier módulo

- **Un módulo no depende de que otro esté activo.** Toda comunicación entre
  módulos pasa por el event bus, y el core publica sin saber quién escucha. Es el
  motivo por el que avisar por una fecha —caducidad, revisión, vencimiento,
  devolución, riego— no tiene módulo propio: cada uno posee su regla, y programar
  la comprobación y entregar el aviso son capacidad de plataforma.
- **Lo que el core no guarda, no se le pide.** El core mantiene un contador de
  cantidad, la procedencia de un asset y su documentación; consumos, mínimos,
  caducidades, importes y coberturas son de quien corresponda de esta lista. Y hay
  cosas que el core no modela en absoluto: un ser vivo no es material del hogar,
  así que mascotas y plantas trae su propia entidad en vez de forzarla en `assets`.
