# Consumo medido y elección del VPS

| Campo | Valor |
|---|---|
| Estado | Vigente |
| Responsable | Equipo DRP |
| Ámbito | Dimensionado del servidor, medido al cerrar la Fase 1 y vuelto a medir al cerrar la Fase 2 |
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

## Historial

| Fecha | Cambio |
|---|---|
| 2026-08-19 | **Vuelta a medir al cerrar la Fase 2, con los cuatro módulos dentro.** La pendiente por hogar pasa de **61 kB a 116 kB** —casi el doble, y dos tercios de la subida son las entradas de apertura de Warehouse y las fichas de máquina de CMMS— y el esquema vacío, de 8,4 a 9,3 MiB. Aparece **una magnitud nueva que aquí no existía**: lo que crece con lo que el hogar *hace*, medido en **2457 B por día, ~875 kiB por hogar y año**, sin techo. La medición se parte en dos por eso, y el tramo de la segunda es de cuatro meses porque uno de dos daba pendientes con un tercio de diferencia entre ejecuciones. **La decisión no cambia: sigue siendo VPS-3 y sigue siendo por disco**, con tres órdenes de magnitud entre la cuota de ficheros y las filas. Se fija por fin el **criterio de retención** de las cinco tablas de la purga, con su disparador. |
| 2026-08-17 | Se crea al cerrar la Fase 1, con la medición de los tres puntos, el coste de las tres operaciones caras y la elección de VPS-3 por disco y no por CPU. Las cifras son las del runner de la CI —Linux, 2 vCPU—, tomadas en la primera ejecución del trabajo `capacity`; los bytes coincidieron con los del equipo de desarrollo y los milisegundos no, que es la razón de medirlos allí. |
