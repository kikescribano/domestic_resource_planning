# Consumo medido y elección del VPS

| Campo | Valor |
|---|---|
| Estado | Vigente |
| Responsable | Equipo DRP |
| Ámbito | Dimensionado del servidor, medido al cerrar la Fase 1 y vuelto a medir al cerrar la Fase 2 — y, desde el Hito 2 del cierre de huecos, **lo que cuesta llegar al navegador** |
| Última revisión | 2026-08-20 |

La [ADR-001](../../common/architecture/decisions/ADR-001-solution-architecture-baseline.md)
dejó el despliegue abierto a propósito y el
[roadmap](../../common/product/roadmap.md) puso la condición para cerrarlo: elegir
entre **VPS-2 y VPS-3 con datos medidos en lugar de estimados**. Esto es la
medición y la decisión que sale de ella.

> **Vuelto a medir el 2026-08-19, al cerrar la Fase 2**, con los cuatro módulos
> dentro. **La decisión no cambia —sigue siendo VPS-3 y sigue siendo por disco—**
> pero los números sí, y uno de ellos es de una clase que aquí no existía. Lo que
> cambió está al final, en «Qué cambió con la Fase 2».

## Cómo se mide

Con [`CapacityMeasurementTest`](../../../backend/src/test/kotlin/com/drp/CapacityMeasurementTest.kt),
que **no es una prueba**: no afirma nada, no falla si un número sube y por eso
lleva la etiqueta `capacity` y queda fuera de la suite. Se pide por su nombre:

```bash
cd backend && ./gradlew capacityMeasurement --no-daemon
```

Dos cosas se comportan muy distinto y se tratan distinto:

- **Los bytes son portables.** Lo que ocupa una fila en PostgreSQL no depende de
  la máquina, así que medirlos en cualquier equipo vale para el servidor.
- **Los milisegundos no lo son**, y aquí se dice en lugar de disimularlo. La
  misma medición de Argon2id dio **40 ms y 89 ms en dos ejecuciones seguidas** en
  el equipo de desarrollo, con Docker Desktop sobre Windows —y volvió a darlo al
  remedirlo: **32 ms y 87 ms**, dos pasadas seguidas del mismo código—. Por eso la
  parte de CPU la ejecuta **la CI, en su runner de Linux con 2 vCPU**, que se
  parece mucho más a un VPS-2 que un portátil, y **los números de abajo son los de
  allí**.

**Y desde la Fase 2 hay que medir dos cosas y no una**, porque el modelo tiene
ahora dos magnitudes que se comportan distinto:

| | Crece con | Techo | Dónde vive |
|---|---|---|---|
| **Existencias** | Lo que el hogar **tiene** — assets, artículos, sitios, fichas de módulo | **Sí.** Una casa no pasa de unos cientos de cosas | Casi todo el esquema |
| **Actividad** | Lo que el hogar **hace** — consumos, líneas de la compra, compras, intervenciones, avisos | **No.** Crece mientras el hogar exista | Las cinco tablas de la [purga](#la-purga-y-su-criterio-de-retención) |

Un solo número de «bytes por hogar» mezcla las dos y **envejece mal**: significa
una cosa el primer día y otra al tercer año. Hasta el Hito 3 de la Fase 2 no hacía
falta distinguirlas porque todo tenía techo.

## Bytes por hogar: lo que el hogar tiene

Se siembran hogares con un perfil realista —8 ubicaciones, 40 artículos, 50
duraderos, 40 existencias, 15 documentos y 5 préstamos, **y los cuatro módulos
encendidos**— y se mide `pg_database_size` en **tres puntos**, no en uno.

| Hogares sembrados | Ocupado sobre el esquema vacío | «Por hogar», dividiendo |
|---|---|---|
| 1 | 1,3 MiB | 1 400 832 B |
| 5 | 1,8 MiB | 385 024 B |
| 25 | 4,0 MiB | 167 772 B |
| **Pendiente entre el primero y el último** | | **116 394 B** |

**Los tres puntos son la parte importante de la medición, y con uno solo el
número habría sido doce veces mayor.** Con un hogar, el coste fijo del esquema
—índices vacíos, catálogo, las cinco categorías sembradas, la primera página de
cada tabla— se reparte entre ese hogar y sale «1,3 MB por hogar». Con veinticinco
baja a 168 kB, y la pendiente real es de **~116 kB**. Un solo punto no habría sido
una medición imprecisa: habría sido una medición equivocada, y con suficiente
aspecto de dato como para que nadie la revisara.

El esquema vacío ocupa **9,3 MiB**, que es el suelo de cualquier despliegue.

Las tablas que más pesan con 25 hogares, que es donde iría a mirar quien quiera
optimizar. **Las cuatro primeras ya no son todas del core:**

| Tabla | Con 25 hogares | De quién |
|---|---|---|
| `assets` | 872 kiB | Core |
| `warehouse_movements` | 640 kiB | Warehouse |
| `articles` | 528 kiB | Core |
| `maintenance_items` | 464 kiB | CMMS |
| `warehouse_articles` | 360 kiB | Warehouse |
| `locations` | 168 kiB | Core |
| `documents` | 168 kiB | Core |
| `warehouse_locations` | 144 kiB | Warehouse |

> **Las dos tablas del Hito 4 del cierre de huecos caen en esta magnitud, no en
> la de actividad, y conviene dejarlo escrito antes de volver a medir.** `tags` es
> un catálogo del tamaño del vocabulario de la casa —del orden de decenas de
> filas, como `categories`— y `asset_tags` crece con **lo que el hogar tiene y
> etiqueta**, no con lo que hace: 500 assets a cinco etiquetas cada uno son 2 500
> filas de tres `uuid`, muy por debajo de `documents`. Ninguna de las dos entra en
> la lista de la purga, y las dos se van con la baja del hogar como el resto de
> tablas con `household_id`. **El número no se vuelve a medir aquí**: la remedición
> completa, con las dos dentro, es del hito de cierre del bloque.

## Bytes por año: lo que el hogar hace

Un hogar ya sembrado, con los cuatro módulos encendidos, al que **no se le añade
ni una cosa más**: solo se le hace vivir. El perfil diario es modesto a propósito
—tres consumos y dos líneas de la compra— y conviene leerlo así: es una casa que
**apunta las cosas**, que es el caso peor razonable. Quien no apunte nada no
escribe ninguna de estas filas.

| Días vividos | Ocupado sobre el hogar recién sembrado |
|---|---|
| 60 | 288 kiB |
| 180 | 576 kiB |
| **Pendiente por día** | **2457 B** |
| **Por hogar y año** | **~875 kiB** |

Lo que crece son dos tablas, y las dos por el mismo motivo —una fila por cosa
hecha—: `warehouse_movements` y `shopping_list_items`. Las otras tres candidatas
a la purga —`purchases`, `maintenance_interventions` y `household_notices`— crecen
mucho más despacio: una compra a la semana, unas pocas intervenciones al año y un
aviso solo cuando hay algo que avisar.

> **El tramo es largo —cuatro meses entre los dos puntos— y eso también es una
> decisión medida.** La primera versión medía entre el día 30 y el 90 y dio
> pendientes con **un tercio de diferencia entre dos ejecuciones seguidas**. La
> causa no era el ruido de la máquina sino la granularidad de PostgreSQL: reserva
> páginas de 8 kB y las divisiones de índice llegan a saltos, así que sobre
> trescientas filas cada salto decidía el resultado. Con seis veces más filas, dos
> ejecuciones seguidas dan **el mismo número**.

## Coste de CPU

Medido **en el runner de la CI**: Linux, **2 vCPU**, JVM con 512 MiB. Mediana y
p95 de 100 repeticiones —10 en las de imagen, que cuestan órdenes de magnitud
más—:

| Operación | Mediana | p95 |
|---|---|---|
| Argon2id, un login | 78,3 ms | 100,7 ms |
| Recodificar una foto de 12 MP | 775,9 ms | 845,6 ms |
| Miniatura de 320 px | 88,0 ms | 90,2 ms |

**La Fase 2 no toca esta tabla**, y se dice en vez de volver a copiarla: lo que se
mide aquí son las tres operaciones caras **del core** —el hash de un login y las
dos de imagen—, y ningún módulo ha añadido ninguna comparable. Un módulo escribe
filas; no cifra ni recodifica.

Estos son los números que cuentan, y por eso se toman ahí. Los mismos en el
equipo de desarrollo —12 núcleos, Docker Desktop sobre Windows— dieron **32 ms y
87 ms para Argon2id en dos ejecuciones seguidas** al remedir: un factor de casi
tres entre dos pasadas del mismo código en la misma máquina. Un solo dato de esa
serie habría servido para justificar cualquier conclusión.

Conviene fijarse en que los bytes, en cambio, **coinciden entre las dos
máquinas**, que es exactamente lo que significa que sean portables.

Lo que sí se puede leer de ellos, porque no depende de la máquina:

- **Argon2id domina el login y nada más.** A 19 MiB y 2 iteraciones —el suelo de
  OWASP— salen **13 logins por segundo y núcleo**, o sea unos 26 en un VPS-2. Para
  un producto doméstico donde una persona entra una vez al día, sobra con enorme
  holgura.
- **La subida de una foto es la operación cara**, y por un orden de magnitud. Una
  foto de 12 MP cuesta cerca de un segundo de núcleo entre recodificar y hacer la
  miniatura. Es un pico y no una carga sostenida: se inventaría la casa una vez.
- **La memoria transitoria de Argon2id es de 19 MiB por login simultáneo**, que
  es lo que hay que sumar al montón de la JVM al dimensionar.

## Bytes por navegador: lo que cuesta llegar al cliente

**Es una tercera magnitud y llega con el Hito 2 del cierre de huecos**, que fue
el primero que tuvo que elegir entre pagar en el servidor y pagar en el cliente.
Hasta entonces no hacía falta: nada de lo que se había construido movía la aguja
de lo que el navegador descarga.

Se mide con la construcción de producción, que es la única que dice la verdad —el
servidor de desarrollo no minifica ni comprime—:

```bash
cd frontend && npm run build
```

| | Sin minificar | Comprimido (gzip) |
|---|---|---|
| `index.js`, **antes** del Hito 2 | 407,28 kB | 119,74 kB |
| `index.js`, **después** | **409,77 kB** | 120,78 kB |
| `index.css` | 26,00 kB | 6,01 kB |
| `heic-to`, **fragmento aparte** | **2 995,46 kB** | **734,16 kB** |

> **El número de referencia al planificar eran 402 kB y no lo son.** El plan del
> cierre de huecos pedía volver a medirlo antes de comparar, precisamente porque
> un bundle envejece: entre la planificación y el hito habían entrado la baja de
> hogar y el cierre de cuenta con sus pantallas. Son 407,28 kB, y de ahí sale
> todo lo demás.

**Lo que hace legible esta tabla es la última fila, y no está donde se esperaba.**
El decodificador de HEIC pesa casi tres megabytes, siete veces la aplicación
entera; y sobre lo que descarga quien abre DRP cuesta **2,49 kB, un 0,61 %**. La
diferencia es que va en un `import()` dinámico: los 2 995 kB salen en su propio
fragmento y solo los pide el navegador de quien elige una foto HEIC, una vez y
luego de su caché.

Por eso la medición se expresa en dos filas y no en una suma. Un solo número
—«3,4 MB»— habría descrito una aplicación que nadie usa: la que descarga el
decodificador sin necesitarlo.

**Y por eso el aviso de la construcción se deja puesto.** Vite avisa de que hay un
fragmento por encima de 500 kB y su consejo —usar `import()` dinámico— ya está
aplicado. Subir `chunkSizeWarningLimit` lo taparía a cambio de dejar de avisar el
día en que crezca el fragmento de la aplicación, que es el aviso que sí importa.
Está escrito en [`vite.config.ts`](../../../frontend/vite.config.ts) para que
nadie lo «arregle».

### Y lo que habría costado en el servidor

La otra mitad de la decisión de la
[ADR-014](../../common/architecture/decisions/ADR-014-heic-conversion.md), medida
antes de elegir y no después. Con `libheif` 1.15.1 en Debian 12, **limitado a 2
vCPU** para parecerse al runner, y una imagen de 4032 × 3024 —12 MP, la misma
talla que mide la tabla de arriba—, mediana de 10 repeticiones:

| Origen, 12 MP | Solo decodificar | Decodificar y recodificar a JPEG |
|---|---|---|
| JPEG típico, 172 kB | 139 ms | 233 ms |
| **HEIC típico, 594 kB** | **691 ms** | **790 ms** |
| JPEG con mucho detalle, 1,51 MB | 167 ms | 333 ms |
| **HEIC con mucho detalle, 7,78 MB** | **1 671 ms** | **1 865 ms** |

Es decir: **de ×3,4 a ×5,6 sobre la misma operación con un JPEG dentro**. Llevado
a la cifra del runner de la tabla anterior —775,9 ms de mediana para recodificar
una foto de 12 MP—, admitir HEIC en el servidor dejaría esa operación en **2,6 a
4,3 s de núcleo por foto**, sobre las 2 vCPU que también atienden los logins con
sus 19 MiB cada uno. Y era ya la operación cara por un orden de magnitud.

> **Los milisegundos de esta tabla no son del runner** y se dice, con la misma
> regla que el resto del documento: se tomaron en el equipo de desarrollo dentro
> de un contenedor. Lo que sí viaja es **la proporción**, que es lo que aquí se
> usa: las dos filas de cada par se midieron en la misma máquina, con la misma
> herramienta y en la misma pasada.

## La decisión: **VPS-3**

Y el motivo no es el que se esperaba al abrir la pregunta.

**No es la CPU.** Con los números de arriba, 2 vCPU atienden holgadamente el
login y la navegación de decenas de hogares; el pico de subir fotos es
esporádico. Por CPU bastaría el VPS-2.

**No es la base de datos**, y sigue sin serlo después de la Fase 2 aunque el
número se haya doblado. A 116 kB por hogar, mil hogares son 116 MB; sumándoles un
año de actividad de todos ellos, 875 MB más. Cabe en cualquiera de los dos planes
sin acercarse al límite. Lo que cambia no es la conclusión sino **su fecha de
caducidad**: el primer número no crece con el tiempo y el segundo sí, así que esta
frase deja de ser cierta sola. Ver la condición de revisión de abajo.

**Es el disco, y lo es por la cuota de ficheros.** La
[cuota de 1 GB por hogar](storage-sizing-and-backups.md) es un techo por hogar y
**no protege al servidor**: con el alta en autoservicio abierto, la suma de las
cuotas no está acotada. El volumen es lo único que decide cuántos hogares caben,
y la diferencia entre los dos planes es justo esa. Con el disco del VPS-2, y
descontando sistema, PostgreSQL y copias, quedan del orden de decenas de hogares
si todos llenaran su cuota; el VPS-3 dobla esa holgura por un coste marginal
frente al del sobrecompromiso mal calculado.

Dicho de otro modo: **la elección no la fija el core sino la ADR-005**, y por eso
sin medir se habría elegido mirando al sitio equivocado. Con los cuatro módulos
dentro sigue siendo así **por tres órdenes de magnitud**: un hogar que llene su
cuota de ficheros ocupa mil veces lo que ocupan sus filas.

> **Lo que hay que confirmar en la compra.** Las cifras concretas de cada plan
> —vCPU, memoria y tamaño de disco— son del catálogo del proveedor y cambian sin
> avisar. Aquí se decide **el criterio**: el disco es la restricción, la CPU no.
> Al contratar, comprobar el tamaño del volumen y aplicar los dos controles que
> 5.8.2 ya exige —techo global sobre el volumen y sobrecompromiso medido—, que
> son los que convierten esta decisión en una operación sostenible en lugar de en
> una apuesta.

## La purga y su criterio de retención

Las cinco tablas que crecen con lo que el hogar hace no tienen purga, y esta
medición es la que permite por fin decir **si hace falta y cuándo**:

| Tabla | Fila por | Ritmo medido |
|---|---|---|
| `warehouse_movements` | Cada movimiento de existencias | El que más crece |
| `shopping_list_items` | Cada línea apuntada | Casi lo mismo |
| `purchases` | Cada compra cerrada | Una a la semana |
| `maintenance_interventions` | Cada reparación o revisión | Unas pocas al año |
| `household_notices` | Cada aviso levantado | Solo cuando hay algo |

**A 875 kiB por hogar y año, un hogar tarda diez años en llegar a 9 MB.** Eso no
es un problema de disco en ningún plazo razonable, y por eso **la purga no se
escribe ahora**: escribir hoy un borrado irreversible sobre el historial de una
casa, para ahorrar megabytes, sería resolver el problema equivocado —y el
histórico es precisamente lo que estas tablas existen para guardar—.

Lo que sí se fija es **el criterio de retención y el disparador**, que es lo que
faltaba desde el Hito 1 de la Fase 2 y lo que convierte «pendiente» en una tarea:

- **Nada se purga por antigüedad mientras el hogar esté vivo**, salvo
  `household_notices`, que es la única de las cinco que **no es histórico sino
  bandeja**: un aviso leído de hace seis meses no lo va a mirar nadie, y su
  contenido se puede reconstruir mirando el estado. Retención propuesta: **90 días
  desde la lectura, y sin límite mientras siga sin leer.**
- **Las otras cuatro son historial del hogar** y se retiran con él: lo que se
  lleva sus filas es la baja del hogar. **Desde el 2026-08-20 eso ya no es una
  promesa**: la baja existe, con treinta días de gracia, y la purga la ejecuta
  `PurgeClosedHouseholds` desde el recorrido diario
  ([ADR-012](../../common/architecture/decisions/ADR-012-data-erasure-household-closure-and-account-closure.md)).
  Este criterio queda por tanto **cerrado para las cuatro**, y abierto solo para
  `household_notices`.
- **El disparador para revisarlo**: que la suma de las cinco pase de **50 MB en un
  solo hogar**, o que la base de datos entera pase de la mitad del volumen. Lo
  primero son unos sesenta años del perfil medido aquí, así que si llega antes es
  que el perfil real no se parece al medido — y entonces lo que hay que rehacer es
  la medición y no la purga.
- **Dónde iría cuando toque**: una `ScheduledCheck` de plataforma con
  `CheckOwner.Core`, que es una comprobación más del recorrido diario. No es un
  módulo y no puede serlo: purga tablas de cuatro módulos distintos y de
  plataforma, y ninguno puede tocar las del otro.

> **Y no hay una sexta, aunque el Transactional Outbox parecía traerla.** El Hito
> 1 del cierre de huecos añade `event_outbox`, que crece con lo que el hogar
> **hace** —una fila por evento publicado— y que por tanto habría entrado en esta
> lista con su criterio de retención. No entra porque **la fila se borra al
> repartirse**: el outbox es una cola y no un archivo, así que su estado normal es
> vacía y su tamaño no crece con el tiempo sino con lo que haya pendiente en ese
> instante. La [ADR-013](../../common/architecture/decisions/ADR-013-transactional-outbox.md)
> razona por qué se descarta conservarla, y una de las tres razones es
> precisamente esta: convertirla en archivo obligaría a inventar una retención
> para una necesidad que nadie ha expresado, justo después de haber cerrado esa
> misma discusión para las cinco de arriba.
>
> Lo que sí conviene saber para operar: **que `event_outbox` tenga filas en reposo
> es el síntoma de que algo no está repartiendo**, y esa es toda la
> instrumentación que necesita. Está en
> [`scheduled-jobs.md`](scheduled-jobs.md).

## Historial

| Fecha | Cambio |
|---|---|
| 2026-08-20 | **Aparece una tercera magnitud: lo que cuesta llegar al navegador.** La trae el Hito 2 del cierre de huecos, que fue el primero que tuvo que elegir entre pagar en el servidor y pagar en el cliente, y las dos mitades quedan medidas aquí porque es donde viven los números. El bundle se **vuelve a medir** y no son los 402 kB que citaba el plan sino **407,28 kB**; el decodificador de HEIC pesa **2 995,46 kB** (734,16 comprimido) y cuesta **2,49 kB sobre la primera carga**, porque va en un fragmento que solo pide quien elige un HEIC. Enfrente, el coste en servidor medido con `libheif` sobre 2 vCPU: recodificar una foto de 12 MP pasa de ×3,4 a ×5,6, o sea **2,6 a 4,3 s** aplicado a la cifra del runner. La decisión que sale de las dos está en la [ADR-014](../../common/architecture/decisions/ADR-014-heic-conversion.md). **La tabla de CPU del runner no se toca**: el camino elegido no añade ni una operación al servidor. |
| 2026-08-20 | **No hay una sexta tabla sin techo**, aunque el Transactional Outbox parecía traerla. `event_outbox` crece con lo que el hogar hace y **la fila se borra al repartirse**, así que no acumula: su estado normal es vacía y su tamaño mide lo pendiente, no lo ocurrido. Se anota aquí para que nadie tenga que volver a derivarlo, junto con lo que sí sirve para operarla. No se vuelve a medir nada: la cola vacía no ocupa. |
| 2026-08-19 | **Vuelta a medir al cerrar la Fase 2, con los cuatro módulos dentro.** La pendiente por hogar pasa de **61 kB a 116 kB** —casi el doble, y dos tercios de la subida son las entradas de apertura de Warehouse y las fichas de máquina de CMMS— y el esquema vacío, de 8,4 a 9,3 MiB. Aparece **una magnitud nueva que aquí no existía**: lo que crece con lo que el hogar *hace*, medido en **2457 B por día, ~875 kiB por hogar y año**, sin techo. La medición se parte en dos por eso, y el tramo de la segunda es de cuatro meses porque uno de dos daba pendientes con un tercio de diferencia entre ejecuciones. **La decisión no cambia: sigue siendo VPS-3 y sigue siendo por disco**, con tres órdenes de magnitud entre la cuota de ficheros y las filas. Se fija por fin el **criterio de retención** de las cinco tablas de la purga, con su disparador. |
| 2026-08-17 | Se crea al cerrar la Fase 1, con la medición de los tres puntos, el coste de las tres operaciones caras y la elección de VPS-3 por disco y no por CPU. Las cifras son las del runner de la CI —Linux, 2 vCPU—, tomadas en la primera ejecución del trabajo `capacity`; los bytes coincidieron con los del equipo de desarrollo y los milisegundos no, que es la razón de medirlos allí. |
