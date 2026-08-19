# ADR-012: Supresión de datos — baja de hogar y cierre de cuenta

- Estado: accepted
- Fecha: 2026-08-20
- Responsables: Equipo DRP
- Ámbito: common
- Sustituye: Ninguna

## Contexto

Hasta hoy **DRP no sabía olvidar**. Todo lo que el producto llama «baja» es
lógica: un asset dado de baja conserva su fila porque sus préstamos la
referencian, una categoría retirada sigue clasificando lo que ya clasificaba, y
una persona que deja el hogar mantiene su historial. El único borrado real del
core es `PurgeUnverifiedHouseholds`, y se justifica precisamente porque **allí no
hay nada que conservar**: unas categorías sembradas y una identidad que nunca
llegó a entrar.

Faltaban por tanto las dos operaciones que sí borran, y las dos estaban escritas
como huecos deliberados:

- **La baja de un hogar** quedó anotada al cerrar la Fase 1 en
  [`decisions.md`](../../product/decisions.md) y en el
  [roadmap de la Fase 1](../../product/roadmap.md), con su motivo: no era una
  pregunta de ficheros sino de un caso de uso que el core no contemplaba.
- **El cierre de cuenta** lleva escrito desde la Fase 1 en la sección 4.1.4
  ([`users-and-access.md`](../../product/users-and-access.md)) —«dejar un hogar no
  es cerrar la cuenta»— **sin nada a lo que engancharse**: `DeactivateUser` da de
  baja la pertenencia, no la identidad. El borrado del avatar se intentó tratar
  como pregunta de avatares en el Hito 3 de la Fase 1 y no tenía dónde colgar.

Hay además dos cosas que dependían de esto y que lo pedían por su nombre. La
ficha de `PurgeUnverifiedHouseholds` en [5.7](../../product/use-cases/README.md)
dejó una promesa autoprogramada: no puede dejar bytes huérfanos porque sin correo
verificado no hay sesión y sin sesión no hay subidas, «**el día que eso cambie,
tendrá que borrar también el directorio del hogar**». Y
[`capacity-measurements.md`](../../../backend/operations/capacity-measurements.md)
fijó el criterio de retención de las cuatro tablas que son historial del hogar
diciendo que **lo que se lleva sus filas es la baja del hogar**, que entonces no
existía.

Tres restricciones condicionan cualquier solución, y ninguna es negociable:

1. **`identities` no cuelga del hogar.** Es la única tabla del modelo con datos
   personales **sin política de RLS**, porque una persona no pertenece a un
   hogar: su pertenencia sí. La cascada de `households` no la alcanza.
2. **Los ficheros están fuera de PostgreSQL** ([ADR-005](ADR-005-local-file-storage.md)),
   así que su borrado **no es transaccional** y hay que elegir un orden.
3. **Nada que no nazca de una petición puede usar `BYPASSRLS`**
   ([ADR-003](ADR-003-row-level-security.md)), y una purga programada no nace de
   ninguna.

## Decisión

### 1. La baja del hogar es con periodo de gracia de treinta días

Un `HOUSEHOLD_ADMIN` la solicita, el hogar queda marcado y **lo purga el
recorrido diario que ya existe**. No hay borrado inmediato: en la supresión
irreversible de la casa entera, **poder arrepentirse importa**, y treinta días son
plazo de sobra para que alguien que no estaba de acuerdo se entere por el resumen
diario y reaccione.

**Durante la gracia el hogar funciona exactamente igual.** Nada de solo lectura:
eso castigaría precisamente a quien todavía puede cancelar. Lo único que lo
distingue es que lo dice —en el aviso que se levanta al pedirla y en la lectura de
su estado.

La marca son **tres columnas de `households`** —cuándo se pidió, quién la pidió y
cuándo vence—, las tres juntas o ninguna, con la autoría apuntando a la
**pertenencia** como en todo el modelo. La fecha de vencimiento **se guarda en
lugar de calcularse al leer**: el plazo es una decisión de producto que puede
cambiar, y derivarlo cada noche movería hacia atrás una fecha que ya se le había
prometido a una persona.

### 2. La purga es una `ScheduledCheck` más, no un recorrido nuevo

`PurgeClosedHouseholds`, con `CheckOwner.Core` —el core no se apaga, así que un
hogar se borra igual sin ningún módulo encendido—, entra en el `DailySweep` que la
[ADR-011](ADR-011-scheduled-checks-and-notice-delivery.md) puso en pie: hogar a
hogar, fijando `app.household_id` en cada transacción y **nunca con `BYPASSRLS`**.

**No produce ningún aviso**, por el mismo motivo que `PurgeUnverifiedHouseholds`:
el destinatario sería un hogar que acaba de dejar de existir. El aviso que sí hay
es el de la solicitud, y lo levanta el caso de uso **una vez** en lugar de
repetirlo cada noche durante treinta días, que es la regla de la ADR-011.

**Y el orden del recorrido deja de ser un accidente del alfabeto.** Hasta hoy
`PurgeUnverifiedHouseholds` era la única comprobación capaz de hacer desaparecer
el hogar en curso, e ir la última de tres lo era por su nombre. Con dos, el
alfabeto ya no basta —`PurgeClosedHouseholds` caería la segunda de cuatro— así
que cada comprobación **declara si puede purgar el hogar** (`purgesHousehold`) y
el recorrido ordena por esa clave primero y por el nombre después. Dos pasadas
siguen haciendo lo mismo en el mismo orden, que es lo único que la ADR-011 pedía
de esa línea.

### 3. Los bytes primero y las filas después, y **por prefijo**

El orden es el que `PurgeUnusedFiles` ya había fijado y aquí se reutiliza en lugar
de inventar otro. Fallar entre medias deja un hogar cuyas filas de `files` apuntan
a bytes que ya no están; **se cura sola**, porque el hogar sigue marcado y la
pasada de mañana vuelve a intentarlo entero.

Lo que sí es una decisión nueva es **cómo** se borran, y el puerto `FileStorage`
gana un método (`deleteTree`) para poder hacerlo:

- **Recorrer las filas de `files`** —lo que hace `PurgeUnusedFiles`— borra
  exactamente lo que la base de datos conoce, y deja fuera cualquier byte que ya
  fuera huérfano.
- **Borrar por prefijo** no deja nada.

Gana el prefijo, porque **lo que esta decisión promete es que no queda un solo
byte del hogar en disco** y el otro camino no lo puede afirmar.

Son **dos prefijos y no uno** —`original/<householdId>` y
`thumbnail/<householdId>`—: el troceado empieza por el tipo y el hogar viene
después, así que no existe «el directorio del hogar». El dibujo de 5.8.1 no
muestra ese primer nivel, y quien escriba la ruta desde el diagrama se dejará las
miniaturas puestas sin que nada dé error.

### 4. La frontera entre hogar e identidad, y su dirección

Son **dos bajas y no una**:

| | Qué se lleva | Quién la pide |
|---|---|---|
| **Baja de hogar** | El hogar entero: sus filas, las de todos los módulos y **sus ficheros en disco** | Un `HOUSEHOLD_ADMIN` |
| **Cierre de cuenta** | Las credenciales de una persona y **su avatar**, que es lo único que la retrata | La propia persona |

**La dirección es de una sola vía: la baja del hogar puede activar la de la
identidad, nunca al revés.** Un hogar que se va deja identidades sin ninguna
pertenencia y hay que resolverlas; una persona que cierra su cuenta **no se lleva
la casa por delante** — se va ella, y el hogar sigue con quien quede.

`CloseAccount` hace cuatro cosas: marca `deactivatedAt` en la identidad, **da de
baja su pertenencia**, revoca sus refresh tokens y **borra su avatar**. Lo segundo
no lo decía 4.1.4 y hace falta: sin ello la persona seguiría apareciendo activa en
el hogar y contando como administradora de un sitio en el que no puede entrar
nunca más. Sus assets quedan **sin propietario**, igual que en `DeactivateUser` y
por el mismo motivo. Los ficheros del hogar se quedan: son del hogar y no suyos.

### 5. Una identidad sin ninguna pertenencia **se borra de verdad**

Es la pregunta que este trabajo tenía que responder, y se responde por el borrado
real y no por la baja lógica. Las dos caras:

- **Conservarla** retiene nombre, correo y teléfono —en la única tabla del modelo
  con datos personales fuera de RLS— de alguien que **ya no puede entrar en ningún
  sitio**. Y no libera su correo: el índice único dejó de ser parcial por baja
  («la identidad sobrevive a cualquier hogar, así que su correo no se libera»), de
  modo que esa persona **no podría volver a registrarse nunca**.
- **Borrarla** es lo que `PurgeUnverifiedHouseholds` ya hace. Allí no había nada
  que conservar; aquí tampoco queda nada que la referencie, porque sus
  pertenencias se fueron con el hogar y sus tokens cuelgan de ella en cascada.

Se descarta la **baja lógica**: dejaría una fila que nadie puede volver a usar,
que nadie puede volver a borrar y que retiene datos personales sin ninguna
finalidad, a cambio de un historial que ya no existe en ningún sitio.

**Y «sin ninguna pertenencia» significa ninguna, no ninguna activa.** Alguien que
dejó el hogar A y hoy vive en B consta en los dos; borrar su identidad al purgar B
se llevaría en cascada su rastro en A, con los préstamos de A apuntándolo. Esa
pregunta no cabe dentro del hogar que se está borrando, así que se resuelve con
**una cuarta función `SECURITY DEFINER`** en el rol `drp_resolver`,
`list_households_for_identity`, que cumple las tres propiedades de la familia:
devuelve **solo identificadores de hogar**, responde a una pregunta cerrada y no
sirve para recorrer datos ajenos.

### 6. El único administrador activo no puede cerrar su cuenta

Con `USER_LAST_ADMIN`, que es el mismo código que ya usan `ChangeUserRole` y
`DeactivateUser` porque es literalmente la misma regla. Un hogar sin
administrador no puede invitar, cambiar roles, encender módulos **ni pedir su
propia baja**: queda bloqueado sin forma de salir, y bloqueado por una decisión
personal de alguien que ya no está para arreglarlo.

La salida está en sus manos, que es lo que hace aceptable la regla: nombrar
administradora a otra persona, o pedir la baja del hogar.

## Alternativas consideradas

- **Borrado inmediato del hogar, sin gracia.** Es más simple y no necesita ni
  columnas, ni comprobación periódica, ni aviso. Se descarta porque convierte un
  clic en una pérdida irreversible de todo lo que una casa ha inventariado
  durante años, y porque el gesto lo hace **una** persona sobre datos de
  **varias**.
- **Dejar el hogar en solo lectura durante la gracia.** Parece prudente y es lo
  contrario: castiga a quien todavía puede cancelar, y convierte los treinta días
  de margen en treinta días de producto roto para quien no había pedido nada.
- **Un recorrido propio para la purga**, con su periodo. Serían dos recorridos, y
  la ADR-011 ya explicó por qué solo puede haber uno. Además la purga no tiene
  ninguna urgencia: un día de retraso sobre un plazo de treinta no significa nada.
- **Recorrer las filas de `files` para borrar los bytes**, como hace
  `PurgeUnusedFiles`. Es el camino ya probado y deja fuera los huérfanos previos,
  que es exactamente lo que aquí no se puede permitir.
- **Borrar el hogar primero y los ficheros después.** No es solo el orden
  equivocado: la cascada se lleva las filas de `files`, que es de donde saldrían
  las claves, así que después del borrado **no hay desde dónde derivar qué
  borrar**. Con el borrado por prefijo el problema no aparece —la ruta se deriva
  del `householdId`— pero el orden se conserva igualmente, porque es el criterio
  que ya estaba escrito.
- **Baja lógica de la identidad huérfana.** Ver arriba: retiene datos personales
  de quien no puede entrar y le quita el correo para siempre.
- **Resolver la orfandad con `find_household_for_active_member`**, que ya existe.
  Solo mira pertenencias vivas, así que daría por huérfano a quien conserva una
  pertenencia dada de baja en otro hogar — y borrar su identidad arrastraría en
  cascada el historial de ese otro hogar.
- **Eximir de la regla del último administrador a un hogar que ya tenga la baja
  pedida.** Parece inofensivo porque ese hogar va a desaparecer igual, y no lo es:
  dejaría la baja **sin nadie que pueda cancelarla**, que es la razón entera de que
  haya gracia.
- **Nombrar administradora a otra persona automáticamente** al cerrar la cuenta
  del último. Decide quién gobierna la casa en el gesto de irse de ella, y sin
  preguntar a nadie.
- **Que cerrar la cuenta arrastre el hogar** cuando quien la cierra es la única
  persona que queda. Convierte una decisión sobre uno mismo en una decisión sobre
  todo lo demás, y se salta la gracia por la puerta de atrás.
- **Resolver el estado de la baja con un claim del token**, en lugar de con una
  lectura nueva. El access token vive quince minutos y se emite al entrar, así que
  un hogar marcado después mentiría hasta la siguiente renovación — y lo haría
  justo en la pantalla que sirve para cancelar.
- **Confirmar la baja con un diálogo de «¿seguro?»**. Se contesta que sí por
  reflejo: es el gesto que se hace cincuenta veces al día para cerrar avisos, y su
  coste es un clic, que es lo que cuesta también equivocarse. La confirmación
  escrita obliga a leer qué se borra y a teclear justo eso.

## Consecuencias

### Positivas

- **Un hogar que pide marcharse desaparece entero**, con las filas de las
  veintidós tablas que cuelgan de él —las de los cuatro módulos incluidas— y sin
  dejar un byte suyo en disco.
- **Se cierra la promesa autoprogramada de `PurgeUnverifiedHouseholds`**: las dos
  purgas comparten el mismo camino de borrado de ficheros.
- **El criterio de retención de `capacity-measurements.md` deja de colgar de algo
  que no existía.** Las cuatro tablas que son historial del hogar se retiran con
  él, que es lo que aquel documento decía y ahora es verdad.
- **La regla de 4.1.4 se activa.** El cierre de cuenta existe, con su avatar.
- **El orden del recorrido periódico pasa de accidente a regla**, declarada y con
  prueba.

### Costes y riesgos

- **Es irreversible, y no hay red debajo.** Vencida la gracia no queda nada que
  restaurar salvo una copia de seguridad. Es lo que se pide.
- **El borrado de ficheros no es transaccional.** Un fallo entre los bytes y las
  filas deja referencias rotas hasta la pasada siguiente. Es el compromiso que
  `PurgeUnusedFiles` ya había elegido, y aquí se hereda con su misma cura.
- **Una cuarta función `SECURITY DEFINER`.** La lista de excepciones deliberadas
  del aislamiento crece de tres a cuatro. Sigue devolviendo solo identificadores y
  sigue perteneciendo a `drp_resolver`, que no es superusuario y no tiene
  `BYPASSRLS`, pero conviene saber que el número subió.
- **Borrar por prefijo es más ancho que borrar por fila.** Si alguna vez se
  compartiera contenido entre hogares —hoy no se hace, y 5.8.1 explica por qué—
  este camino se llevaría por delante lo compartido.
- **Treinta días es un número elegido, no medido.** Nadie tiene todavía datos de
  cuánto tarda una casa en arrepentirse.
- **El hogar en gracia sigue creciendo.** Alguien puede subir un gigabyte el día
  veintinueve y todo eso se borra el treinta. Es coherente con «funciona igual» y
  cuesta disco durante un mes.

## Validación o reversión

Se considera validada cuando:

1. **Solicitada la baja, el hogar sigue funcionando y se puede cancelar**,
   comprobado en un navegador de verdad: pedirla escribiendo el nombre, ver el
   aviso con su fecha en otra pantalla, cancelarla y encontrarlo todo donde
   estaba.
2. **Vencida la gracia, no queda ni una fila ni un fichero suyos**, comprobado
   **tabla por tabla** con un hogar lleno y los cuatro módulos encendidos, y con
   ficheros de verdad en disco. La lista de tablas se toma del catálogo de
   PostgreSQL y no de una constante, de modo que una tabla nueva con
   `household_id` entra sola en la comprobación.
3. **Una identidad sin ninguna pertenencia no sobrevive por accidente**, y una que
   consta en otro hogar —aunque sea de baja— **sí**.
4. **El recorrido sigue yendo hogar a hogar sin `BYPASSRLS`**, con la prueba de la
   ADR-003 que afirma que el usuario de la aplicación no lo tiene.
5. **Lo que puede borrar el hogar corre al final de la pasada**, medido sobre las
   comprobaciones registradas y no leyendo el comentario.
6. **El aviso de la baja se levanta una vez** y no una cada noche.

Revisar cuando ocurra cualquiera de estas cosas:

- **Alguien pide recuperar un hogar ya purgado.** Es la señal de que treinta días
  se quedaron cortos, o de que hace falta una exportación previa —«llévate tus
  datos»— que esta decisión no cubre.
- **Una identidad pasa a poder tener varias pertenencias activas.** Hoy lo impide
  un índice único parcial; el día que se retire, «sin ninguna pertenencia» sigue
  significando lo mismo pero se alcanza por más caminos, y conviene volver a mirar
  la orfandad.
- **El contenido se comparte entre hogares.** El borrado por prefijo deja de ser
  seguro en el momento en que dos hogares puedan apuntar a los mismos bytes.
- **Aparece una obligación legal de plazo** —de conservación o de supresión— que
  no case con los treinta días.

**Revertir** es acotado mientras no haya purgado nada: quitar
`PurgeClosedHouseholds` del recorrido deja los hogares marcados sin borrarse, y la
marca es tres columnas anulables que una migración retira. Lo que **no** se
revierte es un hogar ya purgado.
