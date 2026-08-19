# ADR-011: Programación de comprobaciones y entrega de avisos

- Estado: accepted
- Fecha: 2026-08-18
- Responsables: Equipo DRP
- Ámbito: common
- Sustituye: Ninguna

## Contexto

La Fase 1 dejó tres procesos diarios escritos, probados y **sin programar**:
`PurgeUnverifiedHouseholds`, `PurgeUnusedFiles` y `MarkOverdueLoans`. No había un
solo `@Scheduled` en el código de producción, así que **en un despliegue no se
ejecutaba ninguno**. La Fase 1 los dejó así con criterio —lo que hacía falta
demostrar era que recorren los hogares sin `BYPASSRLS`, y eso lo demuestra la
prueba— pero es un hueco y la planificación de la Fase 2 lo destapó como el
tercero de los que había que cerrar antes del primer módulo.

Al mismo tiempo, la [ADR-010](ADR-010-module-boundaries-and-activation.md) acaba
de introducir módulos que se encienden y se apagan por hogar, y **cuatro de los
cinco módulos que avisan lo hacen por una fecha**: caducidad en Warehouse,
revisión en CMMS, vencimiento en garantías, vacuna o riego en mascotas y plantas
—y devolución en préstamos, que ya está en el core—. La sección 4.2 del README
identificó ese patrón hace tiempo y decidió que **no lo posee nadie**.

Tres decisiones de producto vienen dadas del
[roadmap de la Fase 2](../../product/phase-2-roadmap.md) y esta ADR las
formaliza, no las revisa:

1. **Programar y entregar avisos es plataforma, no un módulo.** Cada módulo posee
   su regla de qué se avisa y cuándo; plataforma pone el recorrido periódico y el
   canal de entrega.
2. **La entrega es un resumen diario por hogar**, no un correo por aviso.
3. **El canal reutiliza el `EmailSender`** de la [ADR-009](ADR-009-outbound-email.md).

Lo que esta ADR decide es cómo se construye eso sin romper las dos cosas que ya
estaban fijadas: el aislamiento en dos capas de la
[ADR-003](ADR-003-row-level-security.md) y las fronteras de paquete de la
ADR-010.

## Decisión

### 1. Un solo recorrido, y lo pone plataforma

`DailySweep`, en `com.drp.platform.schedule`, recorre los hogares **uno a uno,
fijando `app.household_id` en cada transacción y nunca con `BYPASSRLS`**, que es
exactamente la forma que ya tenían los tres procesos diarios.

**Los tres pierden el suyo.** Cada uno pedía la lista de hogares y abría
transacción por hogar; con el suyo más el de plataforma habría **dos recorridos**,
y solo uno de los dos puede saltarse los hogares con el módulo apagado. Así que
pasan a mirar **el hogar actual** y quien itera es plataforma.

La salida fácil para un proceso que no nace de una petición —dar `BYPASSRLS` al
usuario de la aplicación— desactivaría la segunda capa para **toda** la
aplicación y no solo para el proceso. Sigue habiendo una prueba que afirma que
ese usuario no lo tiene, porque sin esa propiedad todas las demás dejan de
demostrar nada.

### 2. La dirección se invierte con un puerto, como con la siembra

Plataforma **no puede importar al core** (tercera y cuarta regla de ArchUnit de
la ADR-010), así que lo que necesita lo declara ella y lo implementa quien lo
sabe:

| Puerto de plataforma | Lo implementa | Para qué |
|---|---|---|
| `ScheduledCheck` | El core, y mañana cada módulo | Qué hay que mirar en un hogar |
| `HouseholdDirectory` | El core | Qué hogares hay |
| `NoticeRecipients` | El core | A quién se le manda el resumen |

Es la misma forma que `ModuleSeeder` tiene desde el Hito 0. La dirección
`módulo → plataforma` y `core → plataforma` sí está permitida; la contraria no.

### 3. Enviar correo y saber qué hogares hay se mudan a plataforma

`EmailSender`, `EmailMessage` y `SmtpEmailSender` estaban en `com.drp.core.*`, y
`allHouseholdIds()` vivía en `TenantResolver`. Plataforma los necesita los dos, y
la tercera regla de ArchUnit lo prohíbe.

**Ampliar la lista de excepciones habría sido la salida equivocada.** Esa lista
tiene un solo nombre —`SessionClaims`— y una prueba que afirma que sigue teniendo
uno; la propia ADR-010 pone como condición de revisión que deje de tenerlo. Así
que se hace lo que esa condición señala: **mudar la clase, no ensanchar la
grieta**. Enviar correo y saber qué hogares hay son capacidad de plataforma —el
core las usa, igual que usa la paginación— y no al revés.

**`EmailAddress` se muda con el puerto.** La alternativa era que el puerto hablara
de texto, que ahorra el renombrado y pierde en la frontera la única garantía que
ese tipo da: al no tener constructor público, no existe forma de construir uno sin
normalizar. Un puerto de correo que acepta `String` acepta `" Kike@X.com "`.

### 4. Cada comprobación declara de quién es

```kotlin
sealed interface CheckOwner {
    data object Core : CheckOwner            // corre en todos los hogares
    data class Module(val key: String)       // solo donde ese módulo está encendido
}
```

Es un **tipo sellado y no una clave nullable**, y la diferencia importa: con un
`String?`, «del core» y «se me olvidó declararlo» se escriben igual, y el segundo
caso haría correr en todos los hogares algo que solo debía correr en unos pocos
—sin que nada fallara—. Que el core corra en todos no es una excepción sino la
regla que le corresponde: **el core no se apaga**, así que un préstamo vence igual
en un hogar sin ningún módulo.

La pregunta la responde `ModuleActivation`, que funciona igual dentro de un
`runAs` que dentro de una petición. Su caché es por hilo y va **indexada por
hogar**, que es lo que la hace segura precisamente en este camino —un solo hilo
pasando por todos los hogares seguidos—; hay una prueba que lo mide en las dos
direcciones dentro de un mismo barrido, en lugar de darlo por bueno leyendo el
comentario.

### 5. Los avisos son una tabla del hogar

`household_notices`, con `household_id`, **RLS y `FORCE`** como cualquier tabla
del core. Aquí la política hace más trabajo que en ninguna otra tabla, porque
quien más escribe en ella **no nace de una petición**: es lo que convierte un
fallo del recorrido en cero filas en lugar de un aviso en el buzón del vecino.

Un aviso es **el texto que se escribió ese día**, no una vista de la fila que lo
originó: si el préstamo se devuelve mañana, el aviso de que venció siguió siendo
cierto. De ahí que lleve título y cuerpo dentro y **ninguna clave ajena hacia lo
que describe** —que además obligaría a plataforma a conocer el esquema de cada
módulo—. `module_key` a nulo significa del core, y un aviso **sobrevive a que su
módulo se apague**, que es coherente con que desactivar conserve los datos.

Es la única tabla del modelo **sin `created_by` ni `updated_by`**: ningún aviso lo
crea una persona, así que `created_by` valdría nulo siempre, y la única
modificación posible es marcarlo leído, que tiene su propia fecha y su propio
autor con nombre —`read_at` y `read_by`—. La autoría sigue apuntando a la
**pertenencia**, con su clave ajena compuesta.

**Leído es del hogar y no de cada persona.** Un hogar es un grupo pequeño que
comparte la bandeja igual que comparte el inventario: si alguien ya se ocupó de la
caducidad del yogur, el resto no tiene que volver a verla. Un estado por persona
multiplicaría las filas por miembro para responder una pregunta que nadie se hace.

### 6. El resumen diario: uno por hogar, y ninguno cuando no hay nada

El recorrido, dentro de cada hogar, corre primero las comprobaciones que apliquen
y **después** entrega el resumen, de modo que incluya lo que se acaba de encontrar
hoy en lugar de dejarlo para mañana.

**Un correo y no uno por aviso**: cinco módulos avisando por fecha producen una
bandeja de entrada que se deja de leer en una semana. El detalle está en la
aplicación, y el enlace del correo apunta al frontend —a `/avisos`— y no a la API,
igual que los cinco correos del core.

**Y ninguno cuando no hay nada.** Un correo diario vacío es la forma más rápida de
que se filtren todos: quien recibe treinta que no dicen nada acaba creando una
regla, y con ella se va también el que sí decía algo.

Va a **todas las personas activas del hogar con el correo verificado**. No solo a
quien administra, porque un aviso doméstico lo puede atender cualquiera y
restringirlo convertiría al administrador en el cuello de botella de la casa; y no
a las direcciones sin verificar, porque una dirección sin verificar es una que
**alguien tecleó**, no una que su dueño confirmó.

El envío ocurre **fuera de la transacción**, que es la regla de la ADR-009, y los
avisos se marcan como entregados **después** de enviar. Las dos opciones son
at-least-once imperfectas y hay que elegir cuál falla mejor: marcar primero y
fallar el envío pierde el aviso para siempre —nadie lo vuelve a mirar— mientras
que enviar y fallar el marcado repite mañana un resumen que ya se leyó. Repetir se
nota y se aguanta; perder, ni se nota.

### 7. Una instancia, un programador

El despliegue elegido con
[consumo medido](../../../backend/operations/capacity-measurements.md) es un
**VPS único**, así que `@Scheduled` basta y no hace falta coordinación entre
nodos. **Esto es una premisa, no una propiedad**, y queda escrito aquí con su
condición de revisión más abajo.

El programador se enciende con `@EnableScheduling` en la raíz de composición y
está **gateado por una propiedad**, encendido por omisión. Hace falta poder
apagarlo: con él encendido, la pasada diaria correría dentro de cualquier contexto
que la suite levante, sobre la base de datos que todas las pruebas comparten, y
esa pasada marca préstamos, borra ficheros y **purga hogares**. Que esté apagado
en las pruebas no se afirma «porque lo pone una propiedad» —un
`@ConditionalOnProperty` mal escrito compila igual— sino con una prueba que mide
las tareas registradas en los dos sentidos.

La hora es local y configurable. Es una decisión de despliegue y no de dominio: el
vencimiento de un préstamo compara instantes —`dueAt` es `timestamptz`— y no
depende de ninguna zona; lo que la zona gobierna es a qué hora conviene pasar la
escoba.

### 8. Un fallo no puede cortar el recorrido

Cada comprobación corre en su propia transacción y con su propio `catch`. Sin él,
un solo hogar con un dato raro apaga el recorrido entero para toda la instalación
**y no avisa a nadie**, que es la peor forma de fallar que tiene un proceso que
nadie mira. La transacción por hogar ya estaba; el `catch` es nuevo y es lo que
convierte «un hogar falló» en una línea de registro en lugar de en una pasada
perdida.

## Alternativas consideradas

- **Ampliar la lista de excepciones de la tercera regla de ArchUnit** para que
  plataforma pudiera usar el `EmailSender` del core. Es lo que la ADR-010 marca
  como señal de que la frontera se está disolviendo, y una lista de excepciones de
  un elemento se vigila mientras que una de tres ya es una convención.
- **Que el puerto de correo hable de texto** en vez de mudar `EmailAddress`.
  Ahorra el renombrado y pierde la normalización garantizada en la frontera.
- **Dejar que cada proceso conserve su recorrido** y que plataforma solo invoque a
  los tres. Serían dos recorridos, y solo uno puede saltarse los hogares con el
  módulo apagado; además obligaría a plataforma a conocerlos por su nombre, que es
  justo la dependencia que la ADR-010 prohíbe.
- **Un módulo de avisos que lo centralizara.** Ya estaba descartado en la sección
  4.2 del README: dejaría a cinco módulos dependiendo de que ese estuviera activo,
  que es exactamente lo que el event bus evita.
- **Un correo por aviso.** Es lo que hace que se filtre el remitente entero.
- **Un resumen diario siempre, aunque esté vacío**, para que se note si el proceso
  deja de correr. Es monitorización disfrazada de producto, y sale carísima: el
  precio de detectar un fallo que no ha pasado es que nadie lea el correo que sí
  importa. La ausencia de pasadas se vigila con el registro, no con el buzón del
  usuario.
- **Estado de lectura por persona.** Multiplica las filas por miembro del hogar
  para responder una pregunta que en una casa no se hace.
- **Marcar los avisos como entregados antes de enviar.** Falla peor: pierde en
  silencio en vez de repetir.
- **Un planificador con coordinación entre nodos** —ShedLock, un `LEADER` en
  PostgreSQL, un cron externo—. Resuelve un problema que este despliegue no tiene
  y añade una pieza que hay que operar. Queda como la primera cosa que hacer
  cuando la premisa de la instancia única deje de valer.

## Consecuencias

### Positivas

- **Los tres procesos diarios se ejecutan de verdad en un despliegue**, que es lo
  que la Fase 1 dejó pendiente sin nombrarlo.
- Los cuatro módulos de la Fase 2 encuentran hecho el sitio donde poner sus
  avisos: declarar un `ScheduledCheck` con su clave y devolver lo que encuentren.
- El recorrido periódico es un solo sitio, así que la regla de «hogar a hogar sin
  `BYPASSRLS`» se cumple o se incumple **una vez** en lugar de una vez por proceso.
- La frontera entre plataforma y core queda más limpia que antes del hito: la
  lista de excepciones de ArchUnit sigue teniendo un solo nombre.

### Costes y riesgos

- **La premisa de la instancia única no se comprueba en ningún sitio.** Con dos
  instancias, los tres procesos corren dos veces y el resumen puede salir
  duplicado; `MarkOverdueLoans` aguanta —su consulta bloquea los candidatos— y la
  entrega del resumen no. Ver la condición de revisión.
- **El recorrido crece linealmente con los hogares**, y con las comprobaciones:
  cuatro módulos encendidos son cuatro consultas más por hogar y por noche. Para
  el orden de magnitud del VPS medido no es nada; es la primera cosa que mirar el
  día que la pasada empiece a solaparse consigo misma.
- **Una activación que cambie a mitad de un barrido no se ve hasta el siguiente**,
  por la caché indexada por hogar. Es lo deseable —un barrido que cambiara de
  opinión a la mitad sería peor de explicar— pero conviene saberlo.
- **El resumen se compone con lo pendiente, no con lo de hoy.** Un hogar sin
  destinatario verificado acumula avisos y recibe el primer resumen entero el día
  que alguien verifique. Es preferible a perderlos, y puede dar un correo largo.
- **`household_notices` crece y nadie la poda.** Hoy no hay retención definida: un
  hogar activo acumulará avisos año tras año. No es urgente —son filas de texto
  corto— pero es una purga que alguien tendrá que escribir, y su sitio natural es
  una comprobación más de este mismo recorrido.

## Validación o reversión

Se considera validada cuando:

1. **Los tres procesos diarios se ejecutan en un despliegue** y se pueden apagar
   en las pruebas, comprobado midiendo las tareas registradas con el programador
   encendido y con el programador apagado.
2. **El recorrido va hogar a hogar sin `BYPASSRLS`**, con la prueba de la ADR-003
   que afirma que el usuario de la aplicación no lo tiene.
3. **Un hogar con el módulo apagado no recibe su aviso** y el de al lado sí,
   comprobado con el módulo de prueba del Hito 0 —una tabla, una ruta, un handler
   y ahora una comprobación periódica— sin esperar a que exista Warehouse.
4. **La caché de activación no responde por el hogar equivocado** dentro de un
   mismo barrido, medido en las dos direcciones: encendido→apagado y
   apagado→encendido.
5. **El resumen diario se lee del Mailpit de verdad**, como el enrolamiento de la
   Fase 1, y **no se envía ningún correo cuando no hay nada**.
6. **La tabla de avisos tiene `household_id`, RLS y `FORCE`**, y un arranque en
   limpio sobre una base vacía produce el esquema completo.

Revisar cuando ocurra cualquiera de estas tres cosas:

- **Aparece una segunda instancia de la aplicación.** Es la condición principal, y
  el síntoma es mudo: los procesos se ejecutan dos veces, el resumen puede llegar
  duplicado y **ninguno avisa de ello**. Lo que hace falta entonces es
  coordinación —un candado en PostgreSQL basta— y es una ADR nueva, no un parche a
  esta.
- **Una comprobación deja de caber en la noche**, o una pasada empieza antes de
  que termine la anterior. Ahí lo que toca no es acelerar el recorrido sino sacar
  las comprobaciones caras a su propio horario, y probablemente separar el
  recorrido de la entrega.
- **Un módulo necesita avisar en el acto y no al día siguiente.** Esta decisión
  cubre el aviso por fecha, que es el patrón que comparten cinco módulos; un aviso
  inmediato es otra cosa —cuelga de un evento, no de un reloj— y pide su propio
  diseño.

**Revertir** es acotado: quitando el `@EnableScheduling` se vuelve exactamente al
estado anterior al hito —los procesos existen y no los invoca nadie— sin perder
ningún dato. La tabla de avisos y su bandeja quedan inertes, con lo que haya
dentro. Lo que **no** es reversible barato es la mudanza del correo a plataforma,
que es un renombrado de paquetes, y por eso va en un commit propio.

## Posterior a esta decisión

Esta ADR **no se reescribe**; lo que sigue enlaza hacia adelante lo que la ha
alcanzado.

- **Warehouse es su primer consumidor real**, en el Hito 3 de la Fase 2. Hasta
  entonces el único que declaraba una `ScheduledCheck` era el módulo de prueba del
  Hito 0, que existe justo para eso; el criterio de validación número 3 —«un hogar
  con el módulo apagado no recibe su aviso y el de al lado sí»— se mide ahora
  también sobre un módulo desplegado, con dos comprobaciones propias: caducidad
  próxima y mínimo alcanzado, las dos con `CheckOwner.Module`.

- **«Tiene que ser idempotente» no bastaba, y el Hito 3 tuvo que decidir qué
  significa para un aviso.** Esta ADR pide que una comprobación sea idempotente
  porque «producir el mismo aviso dos veces es la forma más rápida de que el
  resumen diario se vuelva ilegible», pero no dice qué hacer cuando la **condición
  sigue siendo cierta mañana**: un yogur caducado lo sigue estando treinta noches
  seguidas, y una comprobación perfectamente idempotente dentro de una pasada
  produciría treinta avisos en treinta pasadas. Los dos ejemplos que había no
  servían de modelo — los tres procesos del core no repiten, y el módulo de prueba
  repite **a propósito**, para poder contar pasadas.

  La regla que Warehouse fija, y que los módulos siguientes heredan: **un aviso se
  levanta cuando la condición empieza a ser cierta y no vuelve a levantarse
  mientras siga siéndolo**, con el estado guardado en las tablas **del módulo** y
  no consultando `household_notices` — que obligaría a un módulo a leer una tabla
  de plataforma y a reconocer sus propios avisos por el texto. Una fase que
  **avanza** sí es noticia nueva: un lote avisa al entrar en la ventana de
  antelación y otra vez el día que caduca de verdad, y no darle la segunda dejaría
  al hogar con un aviso de hace tres semanas como única advertencia. Y el estado
  **se rearma solo**, porque si no un artículo avisaría una sola vez en toda su
  vida. Está razonado en la sección 4.1.7 del
  [registro de decisiones](../../product/decisions.md).

- **La purga de `household_notices` sigue sin escribirse.** Esta ADR la dejó
  anotada entre sus costes —«crece y nadie la poda»— con su sitio natural
  señalado: una comprobación más de este mismo recorrido. El Hito 3 trae avisos y
  **no la resuelve**, a propósito: sigue sin hito asignado. Y añade un segundo
  candidato al mismo sitio, `warehouse_movements`, que es la primera tabla del
  modelo que crece con lo que el hogar **hace** y no con lo que tiene.
