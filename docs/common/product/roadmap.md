# Roadmap de la Fase 1 (Core MVP)

| Campo | Valor |
|---|---|
| Estado | Vigente |
| Responsable | Equipo DRP |
| Ámbito | Ejecución de la Fase 1 |
| Última revisión | 2026-08-19 |

> El estado de **las fases** vive en la sección 8 del
> [`README principal`](../../../README.md), y solo allí. Este documento baja al
> detalle de la Fase 1: qué entra en cada hito, en qué estado va y cómo se
> trabaja.

> **La Fase 1 está completa (2026-08-17).** Los cinco hitos cerrados, las 54
> operaciones del contrato implementadas y los seis criterios de aceptación
> demostrados con pruebas que se ejecutan. Este documento se conserva como
> historia de cómo se hizo; el plan de lo que viene está en
> [`phase-2-roadmap.md`](phase-2-roadmap.md), y es el que hay que leer para
> arrancar un hito a partir de ahora.

## Alcance

El **core completo** definido en la Fase 0 —36 comandos, 16 queries y las 54
operaciones del contrato—, con cliente web para todos sus flujos. Se descartó
recortarlo: cada pieza que se deja fuera deja sin validar invariantes que ya
están definidos.

## Cómo se trabaja

- **Un hito por sesión.** Cada hito se ejecuta en una sesión propia, que arranca
  leyendo este documento. Son bloques grandes y mezclarlos hace que ninguno se
  cierre del todo.
- **Un pull request por hito**, y no se abre el siguiente hasta que el anterior
  está fusionado. Al fusionar, seguir el procedimiento de alineación local del
  `CLAUDE.md`: las ramas remotas se borran al fusionar y las referencias locales
  no se enteran solas.
- **Cada hito atraviesa las capas en vertical** —dominio, aplicación, adaptador,
  frontend y sus pruebas—, no una capa entera de cada vez. Construir por capas
  horizontales deja la validación para el final, que es justo lo que el criterio
  de aceptación quiere evitar.

## Criterio de aceptación

No hay que inventarlo: está repartido entre las ADR y basta con consolidarlo.

| Origen | Qué debe demostrarse |
|---|---|
| [ADR-001](../architecture/decisions/ADR-001-solution-architecture-baseline.md) | Un recorrido vertical que atraviese frontend, API autenticada, aplicación, dominio y PostgreSQL, con pruebas en los tres niveles |
| [ADR-002](../architecture/decisions/ADR-002-multi-tenancy-and-backend-framework.md) | Autenticado como hogar A, ninguna operación devuelve ni modifica datos del hogar B, ni siquiera por identificador directo |
| [ADR-003](../architecture/decisions/ADR-003-row-level-security.md) | Las pruebas corren contra PostgreSQL real con un usuario **sujeto a RLS**; los tres procesos diarios recorren los hogares sin `BYPASSRLS` |
| [ADR-004](../architecture/decisions/ADR-004-database-migrations.md) | Un arranque en limpio sobre una base vacía produce el esquema completo, políticas incluidas |
| [ADR-005](../architecture/decisions/ADR-005-local-file-storage.md) | Subir una foto, verla adjunta a un asset y descargarla; más las pruebas de tipo real, EXIF, cuota concurrente y URL firmada |
| [ADR-009](../architecture/decisions/ADR-009-outbound-email.md) | El alta de un hogar se verifica leyendo el correo real de Mailpit, sin pasos manuales |

## Hitos

### Hito 0 — Andamiaje, contrato y reparto documental · **Completado (2026-08-10)**

Sin funcionalidad de producto: es lo que permite que los hitos siguientes se
midan solos.

- [x] **Decisiones que faltaban**, en cuatro ADR nuevas: [frontend y sistema de diseño](../architecture/decisions/ADR-006-frontend-stack-and-design-system.md), [el contrato como fuente de verdad](../architecture/decisions/ADR-007-openapi-contract-as-source-of-truth.md), [monorepo y construcción](../architecture/decisions/ADR-008-repository-layout-and-build-chain.md) y [correo saliente](../architecture/decisions/ADR-009-outbound-email.md). Esta última cubría un hueco que no estaba ni marcado: todo el enrolamiento dependía del correo y nada decía cómo salía.
- [x] **Monorepo**: `backend/` con Gradle y Kotlin DSL, `frontend/` con Vite y React 19, `compose.yaml`, `scripts/` y `.github/workflows/`.
- [x] **Contrato saneado**: `operationId` en las 54 operaciones, paginación uniforme en las once colecciones, respuestas de error transversales y los 41 códigos enumerados en el esquema.
- [x] **Reparto del README a `docs/`**, la tarea de arranque que la Fase 0 había aplazado: 1192 líneas trasladadas.
- [x] **Entorno local verificado** contra PostgreSQL 16 real: el usuario de la aplicación nace sin `SUPERUSER` ni `BYPASSRLS`, y el aislamiento entre hogares funciona.

- [x] **CI verde**, verificada de verdad y no solo en local: contrato y documentación en 28 s, frontend en 22 s y backend en 2 min 13 s.

> **Un detalle de la CI que conviene conocer.** Al abrir el pull request no se
> ejecutó nada —ni un *run* encolado—, probablemente porque el workflow acababa
> de nacer en el repositorio. Arrancó sola con el primer push posterior a la
> rama. Si un PR aparece sin comprobaciones, basta con empujar un commit.
>
> Quedaba pendiente algo menor: `actions/checkout`, `setup-java`,
> `upload-artifact` y `setup-gradle` iban en v4 y el runner ya avisaba de que
> Node 20 estaba descatalogado. **Resuelto el 2026-08-17**, y el aviso había
> pasado de teórico a real: el runner ya estaba forzando esas acciones a
> ejecutarse sobre Node 24, o sea corriendo donde no declaraban. Todas suben a su
> última versión mayor salvo `gradle/actions`, que se queda en v5 a propósito —la
> v6 saca el cacheo a una librería que ya no es código abierto y elimina el
> soporte de configuration-cache, y eso es un cambio de naturaleza y no una
> versión más.

### Hito 1 — Aislamiento y enrolamiento · **Completado (2026-08-11)**

El recorrido vertical de verdad, y el hito que valida ADR-002, ADR-003, ADR-004 y
ADR-009.

- [x] **Flyway: esquema completo de las 15 tablas** con sus `CHECK`, las claves ajenas compuestas de autoría y de fichero, y **todos** los índices únicos parciales —incluido el `NULLS NOT DISTINCT` de existencias con su exclusión de `DECOMMISSIONED`—; políticas de RLS con `FORCE` en las diez tablas con `household_id`; extensión `unaccent`.
- [x] **`TenantContext`** que fija `SET LOCAL app.household_id` al abrir **cada** transacción, desde el claim del token. `PurgeUnverifiedHouseholds` recorre los hogares uno a uno, **sin `BYPASSRLS`**.
- [x] **Argon2id** (19 MiB, 2 iteraciones, paralelismo 1) tras `DelegatingPasswordEncoder`, en los cuatro puntos donde se fija contraseña. JWT HS256 con `sub`, `memberId`, `householdId` y `role`; refresh rotativo y hasheado.
- [x] **Las 16 operaciones del contrato** que caen en este hito, con sus casos de uso: alta, verificación, reenvío, login, renovación, cierre de sesión, contraseñas, invitaciones, roles y baja, más `PurgeUnverifiedHouseholds`.
- [x] **Limitador de frecuencia** (`429 RATE_LIMITED`) sobre los ocho endpoints sin autenticar, por IP **y por correo**.
- [x] **Frontend**: shell responsive de 375 px a ultrawide, primera versión del sistema de diseño con las **ocho dimensiones de [`look-and-feel.md`](../../frontend/product-design/look-and-feel.md) cerradas**, y los siete flujos de enrolamiento.
- [x] **Auditoría de contraste** de los 36 pares en los dos modos, con [`scripts/check-contrast.py`](../../../scripts/check-contrast.py) en la CI para que no envejezca en silencio.

> **Tres cosas que la definición no preveía y que hubo que decidir al
> implementar.** Están razonadas en [`data-model.md`](../architecture/data-model.md)
> y en [`decisions.md`](decisions.md):
>
> - **Las «16 tablas» eran 15.** La cifra salía de contar las filas de la tabla de
>   restricciones, donde `assets` aparece dos veces. Corregida en los tres sitios
>   donde estaba escrita.
> - **`unaccent` no es `IMMUTABLE`**, así que los tres índices que comparan
>   nombres sin acentos no se pueden crear tal cual: hace falta un envoltorio.
> - **Hay tres momentos en los que el hogar todavía no se conoce** —login,
>   aceptación de invitación y procesos diarios— y la política deniega
>   correctamente en los tres, impidiendo arrancar. Se resuelven con tres
>   funciones `SECURITY DEFINER` que devuelven **solo identificadores de hogar**,
>   en lugar de con `BYPASSRLS`.
>
> **Y dos barridos de verificación independientes al cerrar**, que encontraron
> cuatro cosas que la implementación daba por buenas y **corregidas con prueba
> propia**: que las funciones de resolución funcionaban solo porque su propietario
> era superusuario —con `FORCE`, ser dueño de la tabla no basta—; que **la
> comprobación de una clave ajena no pasa por RLS**, así que una fila invisible se
> puede referenciar; que la clave de firma de ejemplo pasaba la validación de
> arranque por medir más de 32 bytes; y que el límite por correo, aplicado también
> al login, permitía bloquear la cuenta de otro.
>
> Un quinto hallazgo **queda anotado y no corregido**: la entrega de correo es
> síncrona y deja un oráculo temporal en los dos endpoints que solo envían cuando
> la identidad existe. El intento de sacarla del hilo de la petición se revirtió
> porque hacía inverificable el recorrido vertical; el motivo completo está en
> [`decisions.md`](decisions.md).
>
> Merece la pena anotar de dónde salieron: las cinco eran **afirmaciones escritas
> en comentarios y en la documentación**. Ninguna se cazó leyendo, sino
> comprobándolas contra PostgreSQL y contra el código en ejecución.
>
> **Este es el hito en el que aparece Testcontainers**, así que Docker tiene que
> estar arrancado para poder construir. Ojo con la distinción: Testcontainers
> levanta y destruye lo suyo —PostgreSQL **y Mailpit**—, y `compose.yaml` es para
> ejecutar la aplicación: no hace falta para las pruebas. Y ojo con una trampa de
> versión: el cliente de Docker que arrastra Testcontainers negocia una API que
> Docker 29 rechaza, y el síntoma («Could not find a valid Docker environment») no
> se parece a la causa. Se fija con `systemProperty("api.version", ...)` en
> `backend/build.gradle.kts`.

### Hito 2 — Catálogo, ubicaciones y assets · **Completado (2026-08-12)**

- [x] **Las 23 operaciones del contrato** que caen en este hito, con sus casos de uso: cuatro de categorías, cinco de artículos, seis de ubicaciones y ocho de assets. Son más casos de uso que endpoints, porque `MoveAsset` y `AdjustAssetQuantity` no tienen ruta propia: los dos entran por `PATCH /assets/{id}`, que despacha según lo que traiga el cuerpo.
- [x] **Las dos naturalezas**, con la entrada que suma sobre la existencia, la fusión, el ajuste absoluto y la baja lógica que da por perdido lo que quedaba.
- [x] **Las tres validaciones que la base de datos no puede garantizar**: anti-ciclo de las dos jerarquías —con el mismo helper, probado hasta el ciclo de tres nodos—, que la ubicación destino sea `DURABLE`, y el aviso de capacidad, que **advierte sin bloquear**.
- [x] **Event bus**: puerto propio sobre `ApplicationEventPublisher`, con la clase base que resuelve las tres garantías —entrega tras el commit, aislamiento del fallo e idempotencia por `eventId`—. Publica los siete eventos del catálogo que tocan a este hito y el `HouseholdCreated` que el Hito 1 dejó esperando.
- [x] **Frontend**: catálogo, árbol de ubicaciones, ficha de asset y las cinco operaciones de existencias, sobre las primitivas del Hito 1 y con dos nuevas, `SelectField` y `EmptyState`.
- [x] **Las fichas del sistema de diseño** que el Hito 1 aplazó «hasta que un listado largo pusiera a prueba su anatomía»: las seis primitivas reales y seis patrones, con los que describen pantallas que aún no existen marcados como previstos.

> **Cuatro decisiones que la definición no preveía y que hubo que tomar al
> implementar.** Están razonadas en [`decisions.md`](decisions.md):
>
> - **El aviso de capacidad no tenía por dónde salir.** El esquema `Asset` no
>   llevaba ningún campo de aviso y las respuestas son un `Asset` pelado. Se
>   añade un array `warnings` al contrato: un `201` **con** advertencia, que no
>   es lo mismo que un `409`.
> - **El puerto del bus solo publica.** El boceto de la Fase 0 dibujaba también
>   un `subscribe`, que no se implementa: sobre `ApplicationEventPublisher` la
>   suscripción es declarativa, y un registro propio en paralelo daría dos
>   mecanismos para lo mismo.
> - **El sobre del evento lleva `householdId`**, un campo más que el boceto. Un
>   handler corre `AFTER_COMMIT`, cuando ya no queda contexto de inquilino del
>   que deducirlo.
> - **Las fotos por fichero responden 404 hasta el Hito 3.** `photoUrl` funciona
>   desde ya —es una columna de texto—; `photoFileId` se resuelve contra `files`,
>   que no puede tener filas todavía.
>
> **Y tres cosas que solo se vieron ejecutando**, no leyendo: `Patch.Absent` como
> `Patch<Nothing>` generaba un puente cuyo retorno se casteaba a `Void` y
> reventaba en tiempo de ejecución compilando sin una queja; Hibernate se
> construye su propio `ObjectMapper` sin el módulo de Kotlin, así que escribía
> los `jsonb` y **no sabía leerlos**; y una propiedad calculada del dominio se
> estaba serializando como una clave más dentro de una columna `jsonb` y en la
> respuesta de la API, donde el contrato no la declara. Esa última la caza una
> prueba que le pregunta a PostgreSQL qué claves hay de verdad: las de recorrido
> no podían, porque escriben y leen con el mismo código y se equivocan igual en
> los dos sentidos.
>
> **Tres barridos de verificación al cerrar**, con instrucción expresa de
> ejecutar y no de leer, que añadieron 33 pruebas y encontraron **un agujero
> real, una guarda rota y cinco afirmaciones falsas**:
>
> - **El tope de profundidad de los dos CTE recursivos hacía lo contrario de lo
>   que pretendía.** Estaba para no colgarse con datos ya cíclicos, y en una
>   jerarquía de más de 100 niveles devolvía la cadena de ancestros **truncada**:
>   el ancestro que faltaba no aparecía, el anti-ciclo respondía que el destino no
>   estaba entre los ancestros y **dejaba crear el ciclo**. Medido con 102
>   niveles, en las dos jerarquías. Se sustituye por la cláusula `CYCLE` de
>   PostgreSQL 14+, que termina siempre sin adivinar ninguna profundidad máxima.
> - **La guarda de idempotencia comprobaba primero y marcaba al terminar**, así
>   que no servía justo cuando hace falta: ocho hilos con el mismo `eventId`
>   pasaban los ocho la comprobación antes de que ninguno marcase. Ahora se
>   **reserva** el identificador antes de atenderlo.
> - **Cinco afirmaciones escritas en comentarios resultaron falsas al medirlas**,
>   todas sobre el entorno en que corre un handler tras el commit. La más
>   importante: la transacción **sigue activa** y unirse a ella devuelve **cero
>   filas**, así que un handler que toque la base de datos necesita
>   `REQUIRES_NEW`. Las que no se pueden arreglar quedan documentadas y escritas
>   como pruebas que afirman el comportamiento **real** en lugar del deseado.

### Hito 3 — Ficheros y documentos · **Completado (2026-08-13)**

La [ADR-005](../architecture/decisions/ADR-005-local-file-storage.md) entera. Es
el hito con más infraestructura propia, y por eso fue después de que el resto
funcionase.

- [x] **Las 11 operaciones del contrato** que caen en este hito: seis de ficheros, tres de documentos y dos de avatar. Con ellas la Fase 1 se queda a cuatro del total —las de préstamos, que son el Hito 4.
- [x] **Puerto `FileStorage`** con adaptador de sistema de ficheros, y los controles de la File Upload Cheat Sheet: lista blanca **por contenido real**, renombrado en disco a partir del identificador, escritura a temporal con movimiento atómico y recodificación de imágenes que **borra el EXIF por construcción** —se decodifica a píxeles y se pinta en un lienzo nuevo, así que lo que no son píxeles no llega al escritor.
- [x] **Cuota de 1 GB por hogar, reservada antes de transmitir**, con la fila del hogar bloqueada solo durante la reserva. Miniaturas de 320 px en WebP, fuera de la cuota, y el proceso diario `PurgeUnusedFiles` con sus tres criterios.
- [x] **nginx en el `compose.yaml`**, con `X-Accel-Redirect`, dominio distinto al de la aplicación, URL firmada de vida corta y el log de acceso **sin cadena de consulta**.
- [x] **Frontend**: subida con progreso, galería, adjuntar documento, avatar y consumo de cuota, sobre tres componentes nuevos escritos contra su ficha antes de existir.

> **Cinco decisiones que la definición no preveía**, razonadas en
> [`decisions.md`](decisions.md). Las dos que más condicionan lo demás:
>
> - **La firma va con MD5 con clave y no con el HMAC-SHA256 que dice 5.8.4.** Es
>   lo único que nginx verifica de serie, y que lo verifique **sin preguntar a la
>   aplicación** es el punto de la decisión. El secreto va al final del mensaje,
>   que es la posición en la que la extensión de longitud no aplica.
> - **La reserva de cuota no cambió el contrato.** Spring parsea el multipart
>   antes de invocar al controlador, así que la reserva habría ocurrido con los
>   25 MB ya en disco; con el multipart resuelto en diferido, el controlador ve la
>   cabecera y todavía no el cuerpo.
>
> **La prueba de nginx levanta un nginx de verdad**, con la misma plantilla que
> `compose.yaml` y no una copia, porque hay cuatro cosas que ningún simulacro
> demuestra: que la firma que emite la aplicación es la que el proxy acepta, que
> una caducidad manipulada da `403`, que `X-Accel-Redirect` entrega los bytes, y
> que el log no lleva cadena de consulta —que es condición de la ADR-005 y no una
> mejora. El resto de la suite corre sin proxy, que es como 5.8.4 describe el
> entorno de desarrollo.
>
> **Un barrido de verificación encontró un agujero real**: un JPEG cortado por la
> mitad entraba como bueno, porque el lector de la JVM no lanza ante un
> truncamiento —rellena lo que falta y se limita a avisar—. PNG y WebP sí lanzan,
> así que era solo de JPEG y por eso no se veía. **Su arreglo hubo que acotarlo**:
> rechazar ante cualquier aviso habría rechazado fotos legítimas, porque un JPEG
> con relleno benigno avisa igual. Medido antes de decidir, en los dos sentidos.
> El segundo barrido murió por el límite de sesión antes de empezar.

### Hito 4 — Préstamos y cierre de fase · **Completado (2026-08-17)**

El último, y el que cierra la Fase 1.

- [x] **Las 4 operaciones del contrato** que faltaban, con las que queda **completo: 54 de 54**. Solo se presta un `DURABLE` y solo si no tiene otro préstamo abierto —`OVERDUE` cuenta, porque vencer no es devolver—, y cada extremo es exactamente un miembro del hogar **o** una persona externa.
- [x] **El token acotado del externo**, con las dos capas que 5.4.1 pedía: JWT firmado sin `sub` **y** hash en `loan_access_tokens`. La firma lo hace infalsificable; la fila, revocable. Su alcance son exactamente dos operaciones de un préstamo, y se comprueba en el **filtro**, así que fuera de ellas no es una credencial.
- [x] **La proyección por rol**, declarada en el contrato como `oneOf` y no solo en la prosa: es lo único de la API que devuelve dos formas según quién pregunta.
- [x] **`MarkOverdueLoans`**, el tercer proceso diario. Con él, los tres recorren los hogares sin `BYPASSRLS`, que es lo que la ADR-003 exige demostrar.
- [x] **La sexta migración**, `find_household_for_loan_token`: el cuarto momento en el que todavía no se sabe cuál es el hogar, y el único en el que quien pregunta **no tiene cuenta**.
- [x] **Frontend**: préstamos del hogar y la vista externa —una pantalla sin sesión, sin shell y sin navegación, escrita contra su ficha antes de existir.
- [x] **Cierre**: recorrido vertical con **Playwright** y auditoría axe, y la **medición de capacidad** que elige el VPS.

> **Cinco decisiones que la definición no preveía**, razonadas en
> [`decisions.md`](decisions.md). Las dos que más condicionan lo demás:
>
> - **El token acotado son las dos capas, no una.** Cada una hace algo que la
>   otra no puede, y la revocación aquí sí hace falta: el token vive **noventa
>   días**, porque un préstamo sin `dueAt` puede durar meses.
> - **La proyección se declara.** Con un solo esquema, el cliente generado
>   prometía `lender` y `borrower` a una pantalla que nunca los recibe. Y
>   `confirmReturn` cambia igual, que se pasaba por alto.
>
> **La ficha de la vista externa encontró un hueco del contrato antes de que
> hubiera código**: el papel no viajaba a la vista acotada, y la mitad del texto
> de esa pantalla depende de él. Es la segunda vez que escribir la ficha antes
> que la pantalla paga.
>
> **Tres barridos de verificación al cerrar**, con instrucción de ejecutar y no
> de leer, y **dos encontraron el mismo tipo de agujero en sitios distintos**:
>
> - **`ConfirmReturn` tenía forma de lee-y-luego-escribe.** Cuatro devoluciones
>   simultáneas del mismo token cerraban el préstamo cuatro veces —`[200, 200,
>   200, 200]` donde el contrato promete un `200` y tres `409`, con `LoanReturned`
>   publicado cuatro veces. No es rebuscado: el enlace del correo se abre en dos
>   sitios o se pulsa dos veces. Aquí no valía el patrón del alta —dejar que un
>   índice único rechace a la segunda— porque no se inserta nada: es un `UPDATE`
>   sobre una fila que ya existe.
> - **Y `MarkOverdueLoans` lo tenía también**, con una diferencia que lo hacía más
>   difícil de ver: el `UPDATE` se serializaba, así que **el estado final era
>   correcto** y solo el evento salía por duplicado. Sin un módulo suscrito
>   escuchando, esa duplicación no se distingue de nada.
>
> Los dos se resuelven con `SELECT ... FOR UPDATE`, acotado en cada caso a la
> operación que lo necesita, y los dos medidos en los dos sentidos: revertido el
> arreglo, la prueba vuelve a fallar. **La lección que queda escrita no es el
> arreglo sino dónde mirar**: cualquier caso de uso que lea, decida y escriba
> sobre una fila que ya existe tiene esta forma, y el estado puede taparlo.
>
> **La medición del VPS dio la respuesta contraria a la esperada.** Por CPU
> bastaría el VPS-2 y la base de datos cabe mil veces en cualquiera de los dos:
> lo que decide es el **disco**, porque la cuota de 1 GB es un techo *por hogar* y
> no protege al servidor. Y los tres puntos de la medición importan — con uno
> solo el número habría salido dieciséis veces mayor. Todo en
> [`capacity-measurements.md`](../../backend/operations/capacity-measurements.md).
>
> **Y dos cosas que solo se vieron ejecutando.** Una propiedad calculada de
> `ExternalParty` se estaba serializando dentro de la columna `jsonb` —el mismo
> fallo del Hito 2 en otra tabla, lo que convierte esa prueba en una que hay que
> escribir **por columna**—; y el regex con el que las pruebas sacan el token del
> correo no admitía puntos, y este es el único de los cinco que es un JWT.
>
> **El cierre se completó en una sesión posterior (2026-08-17)**, porque el hito
> dio por hecho el criterio de la ADR-006 sin haberlo cubierto entero: la batería
> auditaba con axe y medía 375 px, pero **no comprobaba teclado, ni foco, ni
> ultrawide, ni el modo oscuro**, que es la mitad de lo que esa ADR exige. Al
> completarla aparecieron tres cosas que ninguna lectura habría dado:
>
> - **`blur()` no reinicia el punto desde el que se tabula.** El navegador lo
>   mantiene donde estuvo el último elemento enfocado, así que la prueba «arranca
>   arriba» arrancaba por la mitad y el salto al contenido se quedaba sin
>   comprobar. Se resuelve recargando la pantalla.
> - **Auditar a mitad del cambio de tema mide un color que no existe.** Los 140 ms
>   de `transition-colors` acusaron al botón principal de dar 3,55:1 en oscuro
>   cuando sus tokens dan 6,77:1. Y **auditar con el `Spinner` puesto pasa
>   siempre**, porque axe recorre cuatro elementos: era la misma prueba pasando
>   unas veces y fallando otras.
> - **La pantalla externa anunciaba la devolución dos veces**, con dos regiones
>   `role="status"` a la vez, que es exactamente lo que su ficha —escrita antes que
>   el código— prohibía. Corregido, y con una prueba que **cuenta** las regiones.
>
> Y con el criterio cumplido de verdad, `look-and-feel.md` pasa a `Vigente`, que
> era la última condición pendiente de la ADR-006.

## Lo que queda abierto a propósito

No son olvidos: están anotados con motivo en
[`decisions.md`](decisions.md) y no se tocan en la Fase 1.

- **Peso y volumen de un asset**, del que depende que el aviso de capacidad sirva de algo. Destinatario asignado: el módulo Warehouse.
- **Baja de un hogar** y la supresión de sus ficheros. No es una pregunta de ficheros sino de un caso de uso que el core no contempla.
- **Análisis antivirus** de lo subido: es la defensa que toca añadir el día que un fichero pueda salir del hogar que lo subió.
- **Cuatro atributos propuestos** —estado de conservación, condición en préstamo, etiquetas libres, e icono y color de categoría—, que no entran hasta que haya un caso de uso que los pida.

Al cerrar la fase se les suman las dos que el Hito 3 dejó anotadas, y **se funden
aquí en vez de resolverse con prisa**:

- **«Cerrar la cuenta borra el avatar»** (4.1.1) no tiene dónde engancharse:
  `DeactivateUser` da de baja la **pertenencia**, no la identidad, y el único
  borrado real de identidades es el de los hogares sin verificar, donde no puede
  haber avatar. No es una pregunta de avatares sino **la misma que la baja de un
  hogar**, que ya estaba en esta lista: se anota junto a ella y se resolverá con
  el caso de uso que las active a las dos.
- **La conversión de HEIC** la asigna 5.8.3 al frontend, y el frontend no tiene
  con qué: hoy la foto que hace un iPhone con los ajustes de fábrica se rechaza
  con un `415` que enumera los tipos admitidos. Cerrarlo son dos caminos y
  ninguno es un ajuste: meter un decodificador wasm de cerca de un megabyte en el
  cliente para un formato que solo produce un fabricante, o decodificar en
  servidor contradiciendo la sección que lo excluyó. **Es la carencia más visible
  para el usuario más probable de una aplicación doméstica**, así que queda
  escrita como tal y no como detalle.

Ninguna de las dos bloquea el cierre de la Fase 1: las dos son huecos conocidos,
con su motivo y su destinatario, que es exactamente lo que esta sección existe
para sostener.

> **Tres de esta lista tienen plan desde el 2026-08-19**, y esta sección se queda
> como está para que se lea lo que la Fase 1 decidió y por qué. La baja de un
> hogar —con la del avatar dentro—, la conversión de HEIC y los cuatro atributos
> propuestos se ejecutan en el
> [cierre de huecos](open-gaps-roadmap.md), que no es una fase y se dice allí por
> qué. **El análisis antivirus sigue fuera**, con el mismo motivo que aquí: es la
> defensa que toca añadir el día que un fichero pueda salir del hogar que lo subió.

## Historial

| Fecha | Cambio |
|---|---|
| 2026-08-19 | Se anota, sin tocar lo que la fase decidió, que **tres de los cuatro huecos abiertos a propósito ya tienen plan**: la baja de un hogar, la conversión de HEIC y los cuatro atributos propuestos se ejecutan en el [cierre de huecos](open-gaps-roadmap.md). El análisis antivirus sigue fuera, con el mismo motivo de siempre. |
| 2026-08-17 | **Cierre documental de la Fase 1**, que el Hito 4 dejó a medias: seis documentos del frontend seguían diciendo «esto llega en el Hito 4». Se completa la batería E2E con lo que la ADR-006 exigía y no estaba —teclado, foco visible en cada parada, reflujo a 320 px y en ultrawide, y axe en los dos modos—, con lo que `look-and-feel.md` pasa a `Vigente`. Se corrigen los estados de la ficha de la pantalla externa, del registro de componentes y de dos patrones, y se anotan los tres hallazgos de la medición. `CLAUDE.md` deja de describir un repositorio recién arrancado. |
| 2026-08-17 | **Hito 4 completado, y con él la Fase 1.** Las 4 operaciones que faltaban —contrato al 54 de 54—, el token acotado de dos capas con su alcance comprobado en el filtro, la proyección por rol declarada en el contrato, el tercer proceso diario, la sexta migración, el cliente web con la vista externa sin sesión, el recorrido vertical con Playwright y axe, y la medición que elige el VPS. Se anotan las cinco decisiones tomadas al implementar y los dos agujeros de concurrencia que encontraron los barridos. |
| 2026-08-13 | **Hito 3 completado.** Las 11 operaciones de ficheros, documentos y avatar; el puerto `FileStorage` con los controles de OWASP; la cuota reservada antes de transmitir; nginx con `X-Accel-Redirect` y URL firmada; el proceso diario de purga; y el cliente web con subida, galería y consumo de cuota. Se anotan las cinco decisiones que hubo que tomar al implementar y el agujero que encontró el barrido. |
| 2026-08-12 | **Hito 2 completado.** Las 23 operaciones de catálogo, ubicaciones y assets; las dos naturalezas con entrada, fusión, ajuste y baja; las tres validaciones que la base de datos no puede garantizar; el event bus con handlers idempotentes y los ocho eventos; y el cliente web de las cuatro pantallas. Se anotan las cuatro decisiones que hubo que tomar al implementar y las tres cosas que solo se vieron ejecutando. |
| 2026-08-11 | **Hito 1 completado.** Esquema completo, RLS con `FORCE`, `TenantContext`, Argon2id y JWT, las 16 operaciones del enrolamiento, limitador de frecuencia y los flujos de frontend sobre un sistema de diseño con las ocho dimensiones cerradas. Se anotan las tres decisiones que hubo que tomar al implementar y que la definición no preveía. |
| 2026-08-10 | Se crea al arrancar la Fase 1, con el detalle de los hitos trasladado desde la sección 8.2 del README. Hito 0 completado. |
