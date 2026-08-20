# Lo que corre solo: la pasada diaria y el relay del outbox

| Campo | Valor |
|---|---|
| Estado | Vigente |
| Responsable | Equipo DRP |
| Ámbito | Backend Kotlin |
| Última revisión | 2026-08-20 |

Son **dos trabajos programados y no uno**, con periodos que se diferencian en
cuatro órdenes de magnitud: la **pasada diaria** de la
[ADR-011](../../common/architecture/decisions/ADR-011-scheduled-checks-and-notice-delivery.md),
que recorre los hogares de madrugada, y el **relay del outbox** de la
[ADR-013](../../common/architecture/decisions/ADR-013-transactional-outbox.md),
que cada pocos segundos reparte los eventos que nadie llegó a repartir. Aquí está
lo que hace falta para operarlos: qué se ejecuta, con qué se configura y qué
mirar cuando algo no pasa.

## Qué corre: la pasada diaria

`DailySweep`, **de madrugada**, recorre todos los hogares uno a uno. Dentro de
cada hogar ejecuta las comprobaciones que le apliquen y, después, entrega el
resumen diario si hay algo pendiente.

| Comprobación | De quién | Qué hace |
|---|---|---|
| `MarkOverdueLoans` | Core | Pasa a `OVERDUE` los préstamos `ACTIVE` con la fecha superada, publica `LoanOverdue` y deja un aviso |
| `PurgeUnusedFiles` | Core | Desenlaza del disco lo borrado hace más de 24 h, lo subido y nunca adjuntado, y las reservas cortadas a medias |
| `PurgeClosedHouseholds` | Core | **Borra** el hogar cuya baja ha vencido: sus filas, las de los cuatro módulos, sus ficheros en disco y la identidad de quien se quede sin ninguna pertenencia ([ADR-012](../../common/architecture/decisions/ADR-012-data-erasure-household-closure-and-account-closure.md)) |
| `PurgeUnverifiedHouseholds` | Core | **Borra** los hogares sin verificar de más de siete días, con su identidad |

Las del core corren en **todos** los hogares: el core no se apaga. Las que traiga
un módulo corren solo donde ese módulo esté encendido.

El orden es **lo que puede borrar el hogar al final, y el alfabeto por delante**.
Cada comprobación declara `purgesHousehold`, y el recorrido ordena por esa clave
primero y por el nombre después, de modo que dos pasadas siguen haciendo lo mismo
en el mismo orden. Hasta la [ADR-012](../../common/architecture/decisions/ADR-012-data-erasure-household-closure-and-account-closure.md)
solo había una comprobación capaz de hacer desaparecer el hogar en curso y que
fuera la última era **un accidente del alfabeto**; con dos ya no basta —
`PurgeClosedHouseholds` caería la segunda de cuatro— y lo que corriera detrás lo
haría sobre un hogar que acaba de dejar de existir.

Cada una va en su propia transacción y con su propio `catch`: **un hogar que falle
no corta la pasada**, deja una línea de error y el recorrido sigue.

## Configuración de la pasada diaria

| Propiedad | Variable de entorno | Por omisión |
|---|---|---|
| `drp.schedule.enabled` | `DRP_SCHEDULE_ENABLED` | `true` |
| `drp.schedule.daily-cron` | `DRP_SCHEDULE_CRON` | `0 15 3 * * *` |
| `drp.schedule.zone` | `DRP_SCHEDULE_ZONE` | `Europe/Madrid` |

La hora es **local** y es una decisión de despliegue, no de dominio: el
vencimiento de un préstamo compara instantes y no depende de ninguna zona; lo que
la zona gobierna es a qué hora conviene pasar la escoba.

**Apagarlo es una operación legítima** —una ventana de mantenimiento, una
migración larga— y no rompe nada: las cuatro comprobaciones son idempotentes y la
pasada siguiente recoge lo que quedó. Lo único que se pierde es un día de
detección, no los datos.

## Qué corre: el relay del outbox

`OutboxRelay`, cada **cinco segundos**. Reparte los eventos que quedaron en
`event_outbox` sin que nadie confirmara su entrega, que es lo que ocurre cuando el
proceso se cae entre el `COMMIT` del caso de uso y el reparto —o cuando un
suscriptor mal escrito corta el difusor antes de llegar a los handlers—.

**No es una comprobación de la pasada diaria, y no puede serlo**: un evento que
tardase un día en llegar a Warehouse no sería una entrega diferida sino una rota.
De ahí un trabajo aparte, con su periodo y con su interruptor.

Recorre **hogar a hogar, fijando `app.household_id` en cada transacción y nunca
con `BYPASSRLS`**, igual que la pasada diaria. Empieza preguntando **qué hogares
tienen algo pendiente** —una función acotada de `drp_resolver` que devuelve solo
identificadores— para no recorrer en vacío toda la instalación cada pocos
segundos.

| Propiedad | Variable de entorno | Por omisión |
|---|---|---|
| `drp.outbox.enabled` | `DRP_OUTBOX_ENABLED` | `true` |
| `drp.outbox.period` | `DRP_OUTBOX_PERIOD` | `5s` |
| `drp.outbox.grace` | `DRP_OUTBOX_GRACE` | `30s` |
| `drp.outbox.batch-size` | `DRP_OUTBOX_BATCH_SIZE` | `100` |

**El interruptor es suyo y no el de la pasada diaria.** Apagarlo es legítimo —una
segunda instancia, una ventana de mantenimiento— y no pierde nada: las filas se
quedan en la cola y la pasada siguiente las recoge.

**El periodo de gracia** es lo que evita que el relay pise al reparto en el acto.
Solo mira filas de hace más de treinta segundos; lo que sobrevive a esa ventana es,
por definición, lo que nadie repartió. Bajarlo hace que el relay reparta dos veces
en el caso normal, que es legal —la entrega es at-least-once— y ruidoso.

> **La cola vacía es el estado normal.** `event_outbox` no guarda lo entregado: la
> fila se borra al repartirse. Que la tabla tenga filas en reposo significa que
> algo no está repartiendo, y esa es toda la instrumentación que hace falta:
>
> ```sql
> SELECT count(*) FROM event_outbox;
> ```

## La restricción que hay que respetar al desplegar

> **Una instancia, y solo una.** El programador es `@Scheduled` sin coordinación
> entre nodos, porque el despliegue elegido con
> [consumo medido](capacity-measurements.md) es un VPS único. **Con dos
> instancias, los procesos se ejecutan dos veces y ninguno avisa de ello**:
> `MarkOverdueLoans` aguanta —su consulta bloquea los candidatos—, el resumen
> diario puede salir duplicado y **dos relays reparten los mismos eventos**. Lo
> último es lo que menos duele, porque la entrega es at-least-once por contrato y
> los handlers son idempotentes, pero suma un sujeto más a esta restricción.

Si alguna vez hace falta más de una instancia, hay dos salidas y ninguna es un
parche: apagar en todas menos una con `DRP_SCHEDULE_ENABLED=false` y
`DRP_OUTBOX_ENABLED=false` —son **dos** interruptores—, o introducir coordinación
—un candado en PostgreSQL basta—, que es una ADR nueva.

## Qué mirar cuando la pasada diaria no pasa

La pasada deja una línea al terminar, con los tres números que la resumen:

```text
Recorrido diario: 42 hogares, 3 avisos nuevos, 2 resúmenes entregados
```

- **No aparece esa línea.** El programador está apagado, o la aplicación no estaba
  arriba a esa hora. Se comprueba con la propiedad, no suponiendo.
- **Aparece con hogares a 0.** El recorrido no está viendo ningún hogar: mirar
  `list_household_ids()` y los permisos del usuario de la aplicación.
- **Hay avisos nuevos y ningún resumen entregado.** Lo normal es que esos hogares
  no tengan ninguna dirección **verificada**: sin destinatario no se envía y los
  avisos se quedan pendientes, de modo que saldrán en el resumen del día que
  alguien verifique. Si no es eso, mirar los errores del `EmailSender`, que
  registra el fallo de entrega y **no lo propaga**.
- **Un hogar recibe el mismo resumen dos días seguidos.** Es el fallo esperado
  cuando el envío sale y el marcado posterior no llega a persistirse; también es
  el síntoma de que hay dos instancias corriendo la pasada.

## Qué mirar cuando el relay entra en acción

Cada evento que el relay tiene que repartir deja una línea `WARN` con su tipo, su
identificador y su hogar, y cada pasada con trabajo deja el resumen:

```text
Relay del outbox: 2 hogares con pendientes, 3 eventos repartidos de nuevo
```

- **No aparece ninguna línea nunca.** Es lo normal y lo deseable: significa que
  todo se repartió en el acto. La pasada sin trabajo no registra nada.
- **Aparecen líneas de forma continua.** Algo está cortando el reparto antes de
  llegar a los handlers. El sospechoso habitual es un `@EventListener` puesto a
  mano —sin heredar de `IdempotentEventHandler`— que propaga: el difusor para ahí
  y el error queda registrado por `SpringEventBus` justo antes.
- **`event_outbox` crece y el relay no dice nada.** O está apagado, o la
  aplicación no está arriba. Se comprueba con la propiedad, no suponiendo.
- **Un handler recibe dos veces el mismo evento.** Es legal —la entrega es
  at-least-once y los handlers son idempotentes— y las causas normales son dos:
  el proceso se cayó después de repartir y antes de confirmar, o hay dos
  instancias corriendo el relay.

## Retención

`household_notices` **crece y nadie la poda todavía**. Para el orden de magnitud
de un hogar doméstico son filas de texto corto y no es urgente, pero es una purga
que alguien tendrá que escribir, y su sitio natural es una comprobación más de
este mismo recorrido. Queda anotado en las consecuencias de la ADR-011.

Lo que sí se cerró es **la otra mitad del criterio**: las cuatro tablas que son
historial del hogar —movimientos, lista de la compra, compras e intervenciones—
no se purgan por antigüedad, sino que **se retiran con el hogar**, y desde la
ADR-012 eso ya no es una promesa sino `PurgeClosedHouseholds`. El detalle está en
[`capacity-measurements.md`](capacity-measurements.md).
