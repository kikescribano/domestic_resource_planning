# Consumo medido y elección del VPS

| Campo | Valor |
|---|---|
| Estado | Vigente |
| Responsable | Equipo DRP |
| Ámbito | Dimensionado del servidor al cerrar la Fase 1 |
| Última revisión | 2026-08-17 |

La [ADR-001](../../common/architecture/decisions/ADR-001-solution-architecture-baseline.md)
dejó el despliegue abierto a propósito y el
[roadmap](../../common/product/roadmap.md) puso la condición para cerrarlo: elegir
entre **VPS-2 y VPS-3 con datos medidos en lugar de estimados**. Esto es la
medición y la decisión que sale de ella.

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
  el equipo de desarrollo, con Docker Desktop sobre Windows. Por eso la parte de
  CPU la ejecuta **la CI, en su runner de Linux con 2 vCPU**, que se parece mucho
  más a un VPS-2 que un portátil.

## Bytes por hogar

Se siembran hogares con un perfil realista —8 ubicaciones, 40 artículos, 50
duraderos, 40 existencias, 15 documentos y 5 préstamos— y se mide
`pg_database_size` en **tres puntos**, no en uno.

| Hogares sembrados | Ocupado sobre el esquema vacío | «Por hogar», dividiendo |
|---|---|---|
| 1 | 1008 kiB | 1 032 192 B |
| 5 | 1,2 MiB | 260 505 B |
| 25 | 2,4 MiB | 100 597 B |
| **Pendiente entre el primero y el último** | | **61 781 B** |

**Los tres puntos son la parte importante de la medición, y con uno solo el
número habría sido dieciséis veces mayor.** Con un hogar, el coste fijo del
esquema —índices vacíos, catálogo, las cinco categorías sembradas, la primera
página de cada tabla— se reparte entre ese hogar y sale «1 MB por hogar». Con
veinticinco baja a 100 kB, y la pendiente real es de **~60 kB**. Un solo punto no
habría sido una medición imprecisa: habría sido una medición equivocada, y con
suficiente aspecto de dato como para que nadie la revisara.

El esquema vacío ocupa **8,4 MiB**, que es el suelo de cualquier despliegue.

Las cuatro tablas que más pesan con 25 hogares, que es donde iría a mirar quien
quiera optimizar:

| Tabla | Con 25 hogares |
|---|---|
| `assets` | 840 kiB |
| `articles` | 520 kiB |
| `locations` | 168 kiB |
| `documents` | 168 kiB |

## Coste de CPU

Medido en el equipo de desarrollo (12 núcleos, Docker Desktop sobre Windows),
mediana y p95 de 100 repeticiones —10 en las de imagen, que cuestan órdenes de
magnitud más—:

| Operación | Mediana | p95 |
|---|---|---|
| Argon2id, un login | 40–89 ms | 58–105 ms |
| Recodificar una foto de 12 MP | 575–828 ms | 639–892 ms |
| Miniatura de 320 px | 68–107 ms | 73–133 ms |

El rango de la primera columna **no es descuido**: son dos ejecuciones seguidas
del mismo código en la misma máquina. Es la razón por la que estos números no
deciden nada por sí solos y por la que la CI los vuelve a tomar en Linux.

Lo que sí se puede leer de ellos, porque no depende de la máquina:

- **Argon2id domina el login y nada más.** A 19 MiB y 2 iteraciones —el suelo de
  OWASP— cada login cuesta entre 11 y 25 por segundo y núcleo. Para un producto
  doméstico donde una persona entra una vez al día, sobra con enorme holgura.
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

**No es la base de datos.** A 60 kB por hogar, mil hogares son 60 MB. Cabe en
cualquiera de los dos sin acercarse siquiera al límite.

**Es el disco, y lo es por la cuota de ficheros.** La
[cuota de 1 GB por hogar](storage-sizing-and-backups.md) es un techo por hogar y
**no protege al servidor**: con el alta en autoservicio abierto, la suma de las
cuotas no está acotada. El volumen es lo único que decide cuántos hogares caben,
y la diferencia entre los dos planes es justo esa. Con el disco del VPS-2, y
descontando sistema, PostgreSQL y copias, quedan del orden de decenas de hogares
si todos llenaran su cuota; el VPS-3 dobla esa holgura por un coste marginal
frente al del sobrecompromiso mal calculado.

Dicho de otro modo: **la elección no la fija el core sino la ADR-005**, y por eso
sin medir se habría elegido mirando al sitio equivocado.

> **Lo que hay que confirmar en la compra.** Las cifras concretas de cada plan
> —vCPU, memoria y tamaño de disco— son del catálogo del proveedor y cambian sin
> avisar. Aquí se decide **el criterio**: el disco es la restricción, la CPU no.
> Al contratar, comprobar el tamaño del volumen y aplicar los dos controles que
> 5.8.2 ya exige —techo global sobre el volumen y sobrecompromiso medido—, que
> son los que convierten esta decisión en una operación sostenible en lugar de en
> una apuesta.

## Historial

| Fecha | Cambio |
|---|---|
| 2026-08-17 | Se crea al cerrar la Fase 1, con la medición de los tres puntos, el coste de las tres operaciones caras y la elección de VPS-3 por disco y no por CPU. |
