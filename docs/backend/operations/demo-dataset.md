# Juego de datos de demostración

Un hogar completo, cargado de una sentada, para poder abrir la aplicación recién
levantada y verla entera sin teclear nada. Vive en
[`scripts/seed-demo-data.sql`](../../../scripts/seed-demo-data.sql) y se ejecuta
sobre el entorno local que describe la skill
[`run-local`](../../../.claude/skills/run-local/SKILL.md).

Un arranque en limpio no tiene con qué iniciar sesión, y montar a mano un hogar
que enseñe algo —inventario, despensa, préstamos, proveedores, planes de
mantenimiento y el histórico de todo ello— son bastantes minutos de teclear cada
vez. Este fichero los sustituye por un comando.

## Cómo se carga

Requiere la base **ya migrada**, es decir, el backend arrancado al menos una vez
sobre esta base: el script escribe filas, no crea tablas.

```bash
docker compose exec -T -e PGPASSWORD=drp_app postgres psql -U drp_app -d drp -v ON_ERROR_STOP=1 < scripts/seed-demo-data.sql
```

Termina imprimiendo el recuento de lo que ha entrado. Se puede volver a lanzar
cuantas veces se quiera: **es idempotente**, porque empieza borrando su propio
hogar —y solo el suyo— antes de reconstruirlo. Ningún otro hogar de la
instalación se toca.

## Con qué se entra

Cuatro cuentas, todas con la misma contraseña, que está escrita en el propio
script porque esto es una demostración local y ningún dato de ahí corresponde a
nadie real:

| Persona | Correo | Papel |
|---|---|---|
| Marta Ruiz Alonso | `marta@hogar-serrano.test` | `HOUSEHOLD_ADMIN` |
| Javier Serrano Gil | `javier@hogar-serrano.test` | `HOUSEHOLD_ADMIN` |
| Lucía Serrano Ruiz | `lucia@hogar-serrano.test` | `HOUSEHOLD_MEMBER` |
| Hugo Serrano Ruiz | `hugo@hogar-serrano.test` | `HOUSEHOLD_MEMBER` |

Contraseña: `DemoDRP2026Local`.

Los cuatro tienen el correo verificado, así que se entra directamente por
`/entrar` sin pasar por Mailpit.

## Qué carga

Un piso con trastero, catorce meses de vida y **los cuatro módulos de la Fase 2
encendidos** desde hace diez.

| | |
|---|---|
| Ubicaciones | 18, en dos raíces: la vivienda y el trastero del edificio |
| Categorías | 12: las cinco que siembra el alta más las que la casa ha añadido |
| Artículos | 48, entre despensa, limpieza, botiquín y los modelos de duradero |
| Assets | 46 duraderos —uno dado de baja— y 40 existencias de consumible |
| Documentos | 18: manuales, facturas, garantías y un contrato |
| Préstamos | 9, con uno en plazo, uno vencido y siete devueltos |
| Proveedores | 14, uno retirado, con 17 enlaces a assets y ubicaciones |
| Almacén | 37 fichas de artículo, 32 con mínimo; 21 lotes; 76 movimientos |
| Compras | 4 compras y 24 líneas de lista, en los cuatro estados |
| Mantenimiento | 45 máquinas, 11 planes y 21 intervenciones |
| Avisos | 11, leídos y sin leer, del core y de dos módulos |

Lo que se ve al entrar no es un inventario plano: hay **estados que solo se
alcanzan usando la aplicación durante meses**, y están puestos a propósito porque
son los que enseñan para qué sirve cada módulo.

- Once artículos **bajo mínimos**, dos de ellos a cero, con su línea en la lista
  de la compra y el origen correcto: `DEPLETED` pesa más que `LOW_STOCK`.
- Dos lotes **caducados** y dos en la ventana de aviso, con la antelación
  resuelta por el sitio —diez días en la despensa, treinta en el botiquín—.
- Un plan de mantenimiento **pasado de fecha**, otro que toca dentro de dos
  semanas y ocho más repartidos por el año.
- Un préstamo **vencido** que sigue ocupando su asset, porque vencer no es
  devolver.
- **Estados de conservación puestos en 36 de los 46 duraderos y a nulo en los
  otros diez**, que es como se ve un inventario doméstico de verdad: nulo significa
  que nadie lo anotó, y un juego de datos con todo anotado enseñaría una
  aplicación que nadie usa así. Ninguna cosa está `NEW` —lo más joven de la casa
  tiene once meses— y las que no están bien tienen su motivo al lado: al
  ventilador de baja se le rompió el motor y a la tienda de campaña le falta una
  piqueta.
- **Las dos condiciones del préstamo**, que solo dicen algo juntas: seis de
  los siete devueltos con la de vuelta puesta —**dos de ellos peor de lo que
  salieron**, el patinete que «volvió con un arañazo» y cuya ficha dice desde
  entonces `WORN`, y la tienda de campaña a la que le falta una piqueta— y uno,
  la maleta, **sin ninguna de las dos**, que es el caso más frecuente de todos.
- Una compra abierta con tres líneas dentro, dos recibidas con las existencias
  que resultaron, y una anulada —sin líneas, porque anular las devuelve a la
  lista—.

## Tres decisiones del script

**Se ejecuta como `drp_app` y no como el propietario del esquema.** Es el usuario
de la aplicación, sujeto a Row-Level Security y sin `BYPASSRLS`
([ADR-003](../../common/architecture/decisions/ADR-003-row-level-security.md)), así
que el fichero fija `app.household_id` como haría cualquier petición. No es
ceremonia: es lo que garantiza que lo que se siembra es exactamente lo que la
aplicación podría haber escrito. Con `drp_owner` —que en el compose local es
superusuario— las políticas no se aplicarían y el fichero podría sembrar lo que
la aplicación no permite.

**Los identificadores son deterministas**, derivados del nombre natural de cada
fila con `md5()`. Así no hay trescientos UUID escritos a mano, las claves ajenas
se resuelven sin consultar nada, y recargar reconstruye el mismo hogar con los
mismos identificadores: un enlace guardado del navegador sigue valiendo.

**Todas las fechas son relativas a `now()`.** No hay ni una fecha absoluta: las
caducidades, los vencimientos y las próximas revisiones se calculan al cargar. Un
juego de datos con fechas fijas envejece, y a los seis meses enseña una despensa
entera caducada.

## Lo que el script comprueba antes de confirmar

Un juego de datos que se contradice a sí mismo es peor que no tenerlo: enseña una
pantalla que el uso normal no puede producir y manda a diagnosticar un fallo que
no existe. Por eso la carga termina con cuatro afirmaciones que **abortan la
transacción entera** si no se cumplen, que son justo las que es fácil romper al
editar el fichero:

1. La última cantidad del cuaderno de almacén es la que el core tiene hoy.
2. Cada existencia viva tiene exactamente un asiento de apertura.
3. La próxima fecha de cada plan es la última hecha más su periodo.
4. Todo lo que está bajo mínimos tiene su línea viva en la lista de la compra.

## Al ampliarlo

Los datos van en listas `VALUES` con nombres naturales, una por tabla, y las
claves ajenas se resuelven con `pg_temp.demo_id('<tipo>:<clave>')`. Añadir una
fila es añadir una línea a la lista que le toque.

Dos cosas que conviene no olvidar:

- **Un asset con artículo no enseña su propio nombre.** La aplicación resuelve
  nombre y categoría del artículo al leer, así que un nombre guardado en el asset
  no se vería nunca. Por eso solo tienen artículo las cosas cuyo modelo *es* su
  nombre —la caldera, la lavadora, el taladro— y el portátil de Lucía lleva
  nombre propio y ninguno.
- **Lo que se pueda derivar, se deriva.** Las fichas de almacén salen del
  inventario, el estado de bajo mínimos se calcula sumando existencias y la
  última intervención de cada plan sale del propio plan. Es más corto de escribir
  y, sobre todo, no puede acabar diciendo una cosa distinta de la que dicen las
  cantidades.

## Lo que no carga

**Ficheros subidos.** Los documentos son todos enlaces externos, porque un
fichero exigiría escribir bytes en `.data/files` y este script solo toca la base
de datos. Para probar la subida, la vista previa y la descarga —el recorrido de
la [ADR-005](../../common/architecture/decisions/ADR-005-local-file-storage.md)—
hay que subir una foto a mano desde la aplicación.

**Tokens vivos.** No hay invitaciones pendientes, ni enlaces de préstamo externo,
ni restablecimientos de contraseña: se guardan como hash de un secreto que solo
existe en el correo que se mandó, así que sembrarlos daría filas que no abren
nada. Se generan usándolos, y llegan a Mailpit.
