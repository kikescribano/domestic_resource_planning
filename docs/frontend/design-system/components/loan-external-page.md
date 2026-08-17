# LoanExternalPage

| Campo | Valor |
|---|---|
| Estado | Implementado |
| Responsable | Equipo DRP |
| Ámbito | frontend |
| Última revisión | 2026-08-17 |

> **Esta ficha se escribió antes que la pantalla**, como especificación de lo que
> el Hito 4 tenía que construir, y **la pantalla ya existe**: vive en
> [`routes/loans.tsx`](../../../../frontend/src/routes/loans.tsx) como
> `ExternalLoanPage`. Lo que sigue describe el código de hoy; donde lo construido
> se separó de lo especificado, se dice en [Estado de
> implementación](#estado-de-implementación-y-enlace-al-componente-real) con el
> motivo, y no se reescribe la especificación para que parezca que acertó.
>
> Escribirla por delante pagó dos veces: encontró un hueco del contrato —el papel
> no viajaba en la vista acotada, y de él depende la mitad del texto— antes de que
> hubiera código que arreglar, y dejó escrita la regla del anuncio único, que es
> la que delató un defecto real al cerrar la fase.

## Propósito y situaciones de uso

La pantalla que abre **una persona ajena al hogar** al pulsar el enlace que le
llegó por correo cuando el préstamo se inició. Le dice qué tiene o qué prestó, en
qué estado está y para cuándo, y le deja hacer exactamente una cosa: confirmar la
devolución.

Es la única superficie del producto que se ve **sin cuenta, sin sesión y sin
saber qué es DRP**. Quien llega no ha instalado nada, no se ha registrado y puede
no haber oído el nombre del producto en su vida: lo que ha hecho es pulsar un
enlace de un correo que decía que le habían prestado un taladro. Toda la ficha
sale de ahí.

Se llega por un único camino y no se sale por ninguno:

| | Qué ocurre |
|---|---|
| **Cómo se entra** | Por el enlace del correo que se envía al iniciar el préstamo con un participante externo (ver [`loans.md`](../../../common/product/loans.md), 4.1.5). No hay ningún enlace a esta pantalla dentro de la aplicación |
| **Cómo se sale** | Cerrando la pestaña. No hay navegación, ni acceso, ni alta, ni vuelta atrás |

**Es terminal en los dos sentidos, y eso es la decisión de fondo**: no se llega
desde dentro y no se va hacia dentro. La consecuencia práctica es que aquí no hay
shell —ni barra inferior, ni lateral, ni cabecera de aplicación, ni buscador, ni
avatar—, y que el patrón de [`navigation.md`](../patterns/navigation.md) **no se
aplica**. No es que se haya simplificado: es que no hay a dónde ir.

### Por qué es una ficha de componente y no un patrón

La pregunta hay que hacérsela, porque esto describe **una pantalla entera** y las
pantallas son territorio de [`patterns/`](../patterns/README.md). La respuesta es
componente, por tres razones:

1. **Un patrón responde a «en qué orden van las piezas» en una situación que se
   repite**, y esta situación ocurre una vez en todo el producto: hay una sola
   pantalla externa y no habrá una segunda. Un patrón con una única instancia es
   una ficha con el nombre equivocado.
2. **Los cinco patrones existentes dan por supuestas dos cosas que aquí no hay**:
   una sesión y el shell. Listado, jerarquía, formulario y navegación se apoyan
   en las dos; feedback, en la primera.
3. **Tiene anatomía fija, estados propios y una frontera de seguridad que ninguna
   otra pieza cubre.** Eso es la definición de componente que da
   [`patterns/README.md`](../patterns/README.md), y es lo mismo que llevó a
   [`FileGallery`](file-gallery.md) al mismo sitio por el camino contrario.

Encaja además en el segundo tipo de ficha que admite
[`components/`](README.md): una anatomía entera **prevista**, escrita para que la
implementación se guíe por ella.

## Los cinco datos que salen de casa, y los que no

Es lo primero que hay que fijar, antes de la anatomía, porque decide qué se puede
pintar. El contrato lo declara como esquema propio —`LoanExternalView`, cerrado
con `additionalProperties: false`— y lo que **no** lleva es tan deliberado como
lo que lleva.

| Sale | Por qué |
|---|---|
| `assetName` | Sin el nombre no se sabe de qué se está hablando. Es el único dato del asset que sale del hogar: un texto, **sin identificador, sin foto y sin enlace** |
| `status` | `ACTIVE`, `OVERDUE` o `RETURNED` |
| `startedAt` | Desde cuándo |
| `dueAt` | Para cuándo, si se fijó fecha. Puede ser nula: un préstamo sin fecha no vence nunca |
| `returnedAt` | Informado tras confirmar, para que quien lo confirmó lo vea hecho |

Y lo que **no sale, y la pantalla no debe intentar averiguar**: quién presta,
quién recibe, el `assetId`, las notas, la autoría, y absolutamente nada del
hogar —ni su nombre, ni sus miembros, ni sus ubicaciones, ni otros préstamos—.

**Esto no es una carencia del contrato que la interfaz tenga que compensar**, y
conviene decirlo aquí porque es la tentación obvia el día que alguien pregunte
«¿y no queda raro que no diga quién?». La credencial da acceso **a un préstamo,
no al hogar que lo registró**: su alcance son dos operaciones —`GET /loans/{id}`
de ese préstamo y `POST /loans/{id}/return`— y ninguna otra. Pedir el asset, el
listado o el hogar desde aquí devuelve `401`, y así tiene que ser.

De ahí una regla de implementación que parece redundante y no lo es: **la
pantalla pinta los campos de `LoanExternalView` y solo esos, aunque le lleguen
más**. Los dos endpoints aceptan también la sesión del hogar, así que un miembro
que abra el enlace en el navegador donde tiene sesión puede recibir el `Loan`
completo —con `lender` y `borrower` dentro— si la llamada se hace mal (ver
[El token viaja en la URL](#el-token-viaja-en-la-url-y-eso-condiciona-la-llamada)).
Acotar en el cliente es la segunda barrera, no la primera.

## Anatomía, variantes y estados

Una columna centrada sobre el papel de la página, con **un dato importante, una
acción y nada más**. De arriba abajo, seis piezas, y las dos últimas no siempre
están:

1. **La línea de procedencia** — `text-caption text-ink-muted`: «Un préstamo
   anotado en DRP». Una línea de texto, **sin enlace y sin logotipo**. Existe
   porque una página sin ningún contexto que pide confirmar algo se lee como una
   estafa; no lleva enlace porque la pantalla es terminal y llevar a alguien sin
   cuenta a un formulario de acceso no es ayudarle, es publicidad.
2. **El `<h1>` con el nombre del asset** — serif, `text-display`. **Es el único
   dato importante de la pantalla y por eso es el titular**: no se pone «Detalle
   del préstamo» arriba y el nombre pequeño debajo, que es el reparto de una
   aplicación con navegación y esta no la tiene.
3. **La frase de encuadre** — el subtítulo, `text-lead text-ink-muted`, que es lo
   único que cambia con el papel: quien prestó lee una cosa y quien recibió, otra
   (ver [Las dos voces](#las-dos-voces-de-la-misma-pantalla)).
4. **La línea de estado** — el [`StatusBadge`](status-badge.md) y, al lado o
   debajo, la frase que lo explica en cristiano.
5. **Las fechas** — un `<dl>` con dos o tres pares: «Prestado el», «Devolución
   prevista» y, cuando existe, «Devuelto el».
6. **La acción** — un [`Button`](button.md) `variant="primary"`, y bajo él, en
   `text-caption`, la consecuencia de pulsarlo. Desaparece en cuanto el préstamo
   está devuelto.
7. **El pie** — una línea: «Este enlace es personal, no lo compartas».

| Rasgo | Valor | Token |
|---|---|---|
| Lienzo | El de una pantalla de una sola cosa | [`AuthCard`](card.md) |
| Anchura de la columna | 544 px, centrada | `max-w-form`, `mx-auto` |
| Margen lateral | 16 px | `px-gutter` |
| Separación entre bloques | 24 px | `gap-6` |
| Titular | 28 → 40 px, serif | `text-display text-ink` |
| Frase de encuadre | 17 px, tinta secundaria | `text-lead text-ink-muted` |
| Etiquetas del `<dl>` | 13 px, tinta secundaria | `text-caption text-ink-muted` |
| Valores del `<dl>` | 14 px, tinta principal | `text-body-sm text-ink` |
| Acción | 44 px de alto, ancho completo en móvil | `min-h-touch`, `w-full` |
| Procedencia y pie | 13 px, tinta secundaria | `text-caption text-ink-muted` |

**El lienzo es [`AuthCard`](card.md) y no un `<div>` nuevo.** Resuelve las cuatro
cosas que esta pantalla necesita y ya están decididas: el `<main>` de la página
—que aquí puede declararse sin conflicto, porque no hay shell con el suyo—, la
anchura de composición, el centrado vertical y la única aparición de la serif.
Que se llame `AuthCard` y esto no sea ni una tarjeta ni autenticación es el
segundo argumento para el renombrado que su propia ficha pide; no es motivo para
copiarlo.

**Ninguna ilustración en la pantalla cargada.**
[`iconography.md`](../foundations/iconography.md) la admite en tres sitios
—onboarding, vacío y error bloqueante— y esta no es ninguno de los tres. Sí lo es
la pantalla del enlace roto, y allí va.

### Los seis estados de la pantalla

| Estado | Qué se ve | Qué se puede hacer |
|---|---|---|
| **Cargando** | Titular «Abriendo el préstamo» y un [`Spinner`](spinner.md) con su etiqueta | Nada |
| **Abierto** (`ACTIVE` u `OVERDUE`) | La pantalla entera, con su acción | Confirmar la devolución |
| **Devuelto** (`RETURNED`) | Lo mismo **sin acción**, con la fecha de devolución y una línea que cierra | Nada. No queda nada que hacer y se dice |
| **Confirmado** | El estado anterior, más el mensaje de que acaba de hacerse | Nada |
| **Enlace no válido** | Pantalla de error bloqueante, sin dato ninguno del préstamo | Nada dentro del producto |
| **Error de red** | La pantalla que hubiera, más un [`Notice`](notice.md) `tone="danger"` con reintento | Reintentar |

Hay un séptimo que no es un adorno y que se olvida al enumerar los seis:
**Confirmando**, el hueco entre el clic y la respuesta. Es el estado ocupado del
botón que fija [`look-and-feel.md`](../../product-design/look-and-feel.md)
—conserva su anchura, se deshabilita, y por encima de 400 ms añade texto—, y
aquí importa más que en ninguna otra pantalla: es la única acción que existe, así
que un botón mudo durante dos segundos es la aplicación entera colgada.

Y hay un octavo que no es un estado sino una carrera, y sale en producción el
primer mes: **el préstamo se cerró desde casa mientras esta pestaña estaba
abierta**. Está en su apartado, más abajo.

### Las dos voces de la misma pantalla

El mismo lienzo sirve para los dos papeles y **solo cambia el texto**. Ni el
orden de las piezas, ni los colores, ni la acción: es un `POST
/loans/{id}/return` en los dos casos.

| | `LENDER` — prestó | `BORROWER` — recibió |
|---|---|---|
| Frase de encuadre | «Lo prestaste el 1 de agosto» | «Lo recibiste prestado el 1 de agosto» |
| Con fecha | «Quedasteis en que volvería el 15 de agosto» | «Quedaste en devolverlo el 15 de agosto» |
| Sin fecha | «No pusisteis fecha de vuelta» | «No pusisteis fecha de vuelta» |
| Vencido | «Tenía que haber vuelto hace tres días» | «Tenías que haberlo devuelto hace tres días» |
| Acción | «Ya me lo han devuelto» | «Ya lo he devuelto» |
| Consecuencia | «Se cerrará el préstamo y este enlace dejará de servir» | La misma |
| Confirmado | «Anotado: lo has recuperado» | «Anotado: lo has devuelto» |

**El botón dice el verbo y no «Aceptar»**, que es lo que pide la dirección
visual, y el sujeto lo pone el papel. Ninguna de las dos voces nombra a la otra
parte —no puede— y ninguna de las dos hace falta que la nombre: quien lee ya sabe
con quién trató.

Estos textos acabarán en `content/`, que está previsto y vacío. Se escriben aquí
porque sin ellos la ficha no se puede implementar, no porque este sea su sitio
definitivo.

## El estado: qué sirve de `StatusBadge` y qué no

Sirve **la anatomía**: la píldora de redondeo completo, con color, etiqueta e
icono, es exactamente lo que hace falta. No sirven **los tonos**, y aquí se ve
peor que en ningún otro sitio.

El tipo de su propiedad es `NoticeTone | 'neutral'`, así que hoy pintar este
estado obliga a mapear `OVERDUE` → `danger`, que es el rojo de **que algo ha
fallado**. Sobre una fila de inventario eso ya es un error de fondo —y su ficha lo
tiene anotado—; sobre esta pantalla es un problema de producto, porque **quien lo
lee es una persona que simplemente se olvidó**, no un sistema que ha roto nada. Un
vencido es un recordatorio entre conocidos, no una incidencia.

La correspondencia correcta, con los tokens de dominio que
[`color.md`](../foundations/color.md) creó justamente para esto:

| `status` | Tono | Etiqueta | Icono | Cómo se lee |
|---|---|---|---|---|
| `ACTIVE` | `state-lent` | «Prestado» | `hand-helping` | Un hecho, sin urgencia |
| `OVERDUE` | `state-overdue` | «Se pasó la fecha» | `alarm-clock` | Ámbar y despertador: llama a actuar sin acusar |
| `RETURNED` | `state-available` | «Devuelto» | `check-circle` | Cerrado |

Tres decisiones dentro de esa tabla:

- **`OVERDUE` es ámbar, no rojo, y la llamada a la acción no la hace el color.**
  La hace la frase de al lado —«Tenías que haberlo devuelto hace tres días»— y el
  botón, que es lo único pulsable de la pantalla. El color acompaña; no regaña.
- **La etiqueta dice «Se pasó la fecha» y no «Vencido».** «Vencido» es vocabulario
  de recibo impagado; el vocabulario de casa es el que fija la dirección visual.
  En el listado del hogar, donde la columna es estrecha y el lector es de la
  casa, «Vencido» está bien; aquí no.
- **`RETURNED` no tiene token propio en el sistema.** Los cinco estados de dominio
  son los del asset —disponible, prestado, vencido, dado de baja y sin
  existencias— y los del préstamo son otros tres. `state-available` es el más
  cercano y significa lo mismo desde el lado del hogar (la cosa ha vuelto), pero
  es una elección, no una correspondencia. Está en [Lo que falta](#lo-que-falta).

**Y nada de un segundo distintivo.** Un préstamo vencido no lleva además un
`Notice tone="danger"`: sería el segundo color de la pantalla, un `role="alert"`
que interrumpe al lector de pantalla al montarse, y el registro equivocado dos
veces. La frontera de [`notice.md`](notice.md) es exactamente esta: el aviso
cuenta lo que le ha pasado a **una petición**, y aquí no ha fallado ninguna.

## El enlace roto no puede delatar nada

Es el estado delicado de la pantalla y tiene dos exigencias que tiran en
direcciones distintas: **no filtrar** y **servir de algo**.

**La regla de no filtrar.** El `401` —token ausente, caducado, revocado o
falsificado— y el `404` —préstamo inexistente— **pintan la misma pantalla, con
las mismas palabras**. Si el mensaje distinguiera los dos casos, un enlace
manipulado diría si ese préstamo existe, y eso es información del hogar saliendo
por una puerta que se abrió para otra cosa. Es la misma disciplina que ya cumple
el login —«con la contraseña equivocada no dice si el correo existe»— y que
[`App.test.tsx`](../../../../frontend/src/App.test.tsx) comprueba allí con una
aserción propia.

**La regla de servir de algo.** Al otro lado hay una persona con un taladro en el
recibidor. Decirle «no autorizado» y dejarla ahí es cumplir la primera regla e
incumplir la segunda mitad de la dirección visual, que exige que cada error diga
qué se puede hacer. Lo que se puede hacer es **hablar con la persona con quien
trató**, porque desde DRP no hay nada que hacer, y eso es lo que tiene que decir.

La pantalla, entonces:

| Pieza | Contenido |
|---|---|
| Titular | «Este enlace ya no sirve» |
| Cuerpo | «Puede que el préstamo ya esté cerrado o que el enlace se haya quedado antiguo. No podemos decirte más desde aquí.» |
| Salida | «Ponte en contacto con la persona con quien hiciste el trato.» |
| Ilustración | La del error bloqueante, la única de esta ficha |
| Enlaces | **Ninguno.** Ni acceso, ni alta, ni soporte |

Las tres posibilidades que enumera el cuerpo no confirman ninguna: se ofrecen
como lista, que es lo que permite ser útil sin afirmar. Y el «no podemos decirte
más» es literal, no una fórmula: la pantalla de verdad no lo sabe.

**Sin enlace de acceso, y esto merece decirse aparte.** La tentación es ofrecer
«Entrar en DRP» por si quien mira es del hogar. No: quien tiene cuenta ve su
préstamo en su aplicación y no necesita este enlace, y quien no la tiene recibe
un formulario de acceso como respuesta a un problema que no es suyo. Una pantalla
terminal que ofrece una salida deja de ser terminal y empieza a ser un embudo.

## Confirmar sin diálogo, y por qué

La devolución es **irreversible desde este lado**: confirmada, el préstamo se
cierra, el segundo intento responde `409` y el enlace deja de servir. La regla de
las operaciones destructivas de
[`look-and-feel.md`](../../product-design/look-and-feel.md) pediría confirmación
—nombrando el objeto y la consecuencia—, y aun así **la respuesta aquí es que no
hay paso intermedio**. Los tres motivos, en orden de peso:

1. **La pantalla ya es la confirmación.** El objeto está en el `<h1>` en serif a
   40 px, la consecuencia está escrita bajo el botón antes de pulsarlo, y no hay
   nada más en la página. Un diálogo encima repetiría la misma pregunta sobre la
   misma pregunta, y lo que enseña ese patrón es a pulsar dos veces sin leer.
2. **No hay `Dialog`.** Es una de las cinco piezas que el Hito 2 dejó pendientes.
   Construirla para esto —y con ella el foco atrapado, el cierre con `Escape` y la
   variante de hoja inferior en móvil— es mucha máquina para una pantalla de una
   sola acción, y la máquina se rompe más que la pantalla que protege.
3. **No es del todo destructiva.** No se borra nada: el préstamo pasa a
   `RETURNED` y se queda en el historial del hogar. Que no exista operación de
   reabrir es un hueco del contrato, no un argumento a favor del diálogo — y está
   anotado como tal.

Lo que sustituye al diálogo no es nada: son **tres decisiones de disposición**,
que es donde de verdad se evita el toque accidental.

- **La acción va debajo del dato**, nunca arriba. Para llegar al botón hay que
  haber pasado por el nombre, el estado y las fechas.
- **El botón no recibe el foco al cargar.** Una pantalla que se abre con el foco
  en el único botón que tiene es una pantalla que se confirma con la barra
  espaciadora sin haberla leído.
- **La consecuencia se escribe antes, no después.** «Se cerrará el préstamo y este
  enlace dejará de servir» va bajo el botón desde el primer pintado; no es un
  mensaje posterior.

### El préstamo que se cierra desde casa mientras la pestaña está abierta

Es la carrera real de esta pantalla y hay que resolverla, porque la mitad de las
devoluciones se van a anotar desde dentro: quien prestó abre su aplicación, marca
el préstamo devuelto, y la pestaña que el vecino tiene abierta desde el martes
sigue enseñando el botón.

Cuando eso pasa, `POST /loans/{id}/return` responde **`409` con
`LOAN_ALREADY_RETURNED`**, y el tratamiento correcto **no es un error**:

1. No se pinta ningún `Notice tone="danger"`. No ha fallado nada; lo que quería
   hacer ya está hecho.
2. Se vuelve a pedir `GET /loans/{id}`, porque el `409` trae un `Error` y no el
   préstamo, y hace falta `returnedAt` para pintar el estado cerrado.
3. Se pasa al estado **Devuelto**, con un `Notice tone="info"`: «Ya estaba
   anotado como devuelto».

Es la diferencia entre una interfaz que informa y una que echa la culpa. Y
conviene notar el contraste con el camino normal: **el `200` sí trae la vista
acotada entera**, `returnedAt` incluido, así que después de confirmar bien **no
hace falta releer nada** —al revés que en [`Avatar`](avatar.md), cuyo `204` obliga
a volver a pedir a la persona—.

## El token viaja en la URL, y eso condiciona la llamada

El enlace del correo lleva la credencial dentro. Cuatro consecuencias, y la
tercera es la que va a costar una tarde si no está escrita:

1. **El token se queda en la URL.** Es tentador limpiarlo con `replaceState`
   después de leerlo, y sería un error: quien recarga la página, o vuelve a ella
   desde el correo dos semanas después, necesita que el enlace siga siendo el
   enlace. Lo que sí exige es que la pantalla **no pida nada a terceros** —no hay
   imágenes, ni fuentes descargadas, ni analítica, ni una sola petición fuera del
   propio origen—, porque cualquiera de ellas se lleva la URL en la cabecera
   `Referer`.
2. **El token no es una sesión y no se guarda como tal.** No entra en
   `localStorage`, no toca el almacén de sesión y **no se conecta a la renovación**
   de [`client.ts`](../../../../frontend/src/api/client.ts): no hay refresh token
   que gastar, y un `401` aquí significa «este enlace ya no vale», no «renueva y
   reintenta». La llamada va con `renewable: false`.
3. **`request()` tal cual manda la credencial equivocada.** Adjunta el access
   token del módulo cuando lo hay, así que un miembro del hogar que abra el
   enlace en el navegador donde tiene la sesión iniciada haría la petición con su
   token de sesión. No fallaría —los dos endpoints aceptan las dos credenciales—:
   **devolvería el `Loan` completo**, con `lender`, `borrower` y `notes` dentro,
   sobre una pantalla escrita para no enseñarlos. `LoanView` es un `oneOf` de dos
   ramas disjuntas justamente para que esto se vea al tipar. La pantalla necesita
   su propio camino de llamada, con la cabecera puesta a mano.
4. **La página no se indexa ni se precarga.** `noindex, nofollow` y nada de
   `prefetch`: un enlace con credencial dentro que acabe en un rastreador es un
   préstamo ajeno abierto para quien pase por allí.

## API pública

Prevista. Es la propuesta de esta ficha, no un contrato ya escrito.

La ruta, en la línea de las siete pantallas sin sesión que ya existen en
[`App.tsx`](../../../../frontend/src/App.tsx) —`/verificar-correo`,
`/aceptar-invitacion`— y con el token en la cadena de consulta, como todas ellas:

```
/prestamo/:id?token=…
```

El identificador va en la ruta porque `GET /loans/{id}` lo pide, y **no se saca
decodificando el token** aunque lo lleve dentro como claim.

```ts
type LoanStatus = 'ACTIVE' | 'OVERDUE' | 'RETURNED'

interface LoanExternalView {
  id: string
  assetName: string
  status: LoanStatus
  startedAt: string
  dueAt: string | null
  returnedAt: string | null
}

// Sin propiedades: todo lo que necesita entra por la URL.
function LoanExternalPage(): JSX.Element
```

Y las dos llamadas que hacen falta, que son las únicas de toda la aplicación que
mandan una credencial que no es la de la sesión:

```ts
api.getExternalLoan(loanId: string, loanToken: string): Promise<LoanExternalView>
api.confirmExternalReturn(loanId: string, loanToken: string): Promise<LoanExternalView>
```

| Decisión | Por qué |
|---|---|
| Sin propiedades | Es una pantalla de ruta. Lo que la parametriza es el enlace |
| El token como argumento explícito | Nunca desde el estado del módulo: ver el punto 3 de más arriba |
| `renewable: false` en las dos | No hay nada que renovar, y renovar significaría mandar la credencial de otro |
| Devuelven `LoanExternalView`, no `LoanView` | El cliente acota la rama del `oneOf` que esta pantalla puede pintar |

### El papel no llega en la vista acotada, y hace falta

**El contrato no manda el papel.** `LoanExternalView` tiene cuatro campos y
ninguno dice si quien mira prestó o recibió; el dato existe, pero como claim
`role` del token acotado. Sin él, la mitad de esta ficha —las dos voces— no se
puede escribir. Las tres salidas, y cuál se propone:

| Salida | Veredicto |
|---|---|
| Decodificar el JWT en el cliente para leer `role` | **No.** Ata la pantalla a la forma interna de una credencial; el día que el token se vuelva opaco, la pantalla deja de saber hablar. Un cliente no lee dentro de su credencial |
| Añadir el papel al enlace del correo, como segundo parámetro | **No.** Es una segunda fuente de verdad, editable a mano, y produce la pantalla que le dice «devuélvelo» a quien prestó |
| **Añadir `role` a `LoanExternalView`** | **Sí.** Un campo de enumerado, sin información nueva —quien lee ya sabe de qué lado está— y en el sitio donde la pantalla ya mira |

Es un cambio de contrato y esta ficha no lo hace: lo pide. **Hasta que exista, la
pantalla se escribe con voz neutra** —«Este préstamo empezó el 1 de agosto», y el
botón «Confirmar la devolución»—, que funciona para los dos papeles y no acierta
del todo con ninguno.

## Comportamiento responsive y con contenido extremo

- **A 375 px** la columna ocupa el ancho menos los 16 px de `px-gutter`. El `<dl>`
  apila etiqueta sobre valor —nada de dos columnas, que a esa anchura dejan la
  fecha partida en dos líneas— y el botón ocupa el ancho completo, con sus 44 px.
- **La acción no va en banda inferior fija.** El sitio de la acción principal en
  móvil lo fija [`navigation.md`](../patterns/navigation.md) para las pantallas del
  shell, donde hay contenido que desplazar por debajo. Aquí no hay scroll que
  valga: la página entera cabe, así que el botón va en el flujo, después del dato.
- **Nombre de asset larguísimo** —«Taladro percutor Bosch GSB 18V-55 con
  maletín»—: **no se trunca, y esta es la excepción**. En una fila de listado
  truncar es correcto porque hay veinte nombres más; aquí el nombre **es** la
  pantalla, y truncar el único dato que se ha venido a leer es absurdo. Fluye en
  las líneas que haga falta, con `text-balance` para que no quede una palabra
  suelta en la última, y `overflow-wrap: anywhere` para el nombre sin espacios que
  a 40 px se sale de la columna.
- **De 544 px en adelante no pasa nada**, y ese es el problema de verdad de esta
  pantalla. Una columna de 544 px con seis líneas de contenido, centrada en un
  monitor de 3440 px, es un sello de correos en medio de una mesa. Las dos
  salidas fáciles están descartadas por escrito:
  - **Ensanchar la columna**: da una línea de texto de doscientos caracteres y una
    fecha a un palmo de su etiqueta. Es justo lo que el escenario ultrawide de
    [`look-and-feel.md`](../../product-design/look-and-feel.md) manda validar que
    **no** ocurra.
  - **Rellenar el sobrante**: no hay con qué. Todo lo que se podría poner es o
    publicidad del producto a quien no la ha pedido, o un dato del hogar que esta
    pantalla no puede enseñar. **El sobrante se queda en margen**, que es la regla
    de [`space.md`](../foundations/space.md), y aquí además es la única opción
    honesta.

  Lo que sí trabaja a favor es que el titular ya crece solo: `text-display` va con
  `clamp()` de 28 a 40 px, así que en pantalla grande el nombre del asset se lee
  como un cartel y no como un recibo. **Esa es toda la adaptación que hay, y es
  deliberado que sea una sola**: si además creciera el cuerpo, o el botón, la
  pantalla dejaría de tener una jerarquía para tener dos tamaños grandes.
- **El centrado vertical es correcto aquí**, al revés que en una vista con filas:
  `min-h-dvh` con `justify-center` de [`AuthCard`](card.md) pone las seis líneas
  en el centro óptico en lugar de dejarlas colgando del borde superior con dos
  metros de papel debajo. Y es `dvh`, que es lo que sostiene el centrado mientras
  la barra del navegador móvil aparece y desaparece.
- **Zoom al 200 % y anchura de 320 px**: la columna es una sola y no hay tabla, así
  que el reflujo no tiene dónde romperse. Es de las pocas pantallas del producto
  donde eso se puede afirmar sin medirlo.
- **Ninguna animación de entrada.** Ni el titular apareciendo, ni el distintivo
  creciendo. Lo único que se mueve es el estado ocupado del botón, que responde a
  un toque, y el indicador de carga.

### Las fechas

Se pintan **sin hora**: «1 de agosto de 2026», con
`Intl.DateTimeFormat('es-ES', { dateStyle: 'long' })`. La hora a la que se prestó
un taladro no le importa a nadie y arrastra un problema de husos que no compensa:
los campos son `date-time` en UTC y formatearlos en el huso del lector puede
correr la fecha un día cuando el préstamo se anotó cerca de medianoche.
Escribiendo solo el día, la imprecisión es de un día en un caso raro; escribiendo
la hora, se está prometiendo una precisión que la pantalla no tiene.

Para `OVERDUE` va además el relativo —«hace tres días»—, porque **ese es el dato
que mueve a actuar**, y solo ahí: en un préstamo al día, «hace 197 días» no
informa de nada y suena a reproche.

## Teclado, foco, semántica y anuncios asistivos

- **El foco no se mueve al cargar.** La página se abre en una pestaña nueva desde
  un correo, con el foco al principio del documento, que es exactamente donde
  tiene que estar: **toda la pantalla es el mensaje**, y un lector de pantalla la
  recorre entera desde arriba. Llevar el foco al `<h1>` es el remedio para un
  cambio de vista dentro de una aplicación; aquí no hay vista anterior.
- **El cambio de «Abriendo el préstamo» a la pantalla cargada sí es un cambio de
  vista**, y en una aplicación de una sola página nada lo anuncia —el hueco que
  [`navigation.md`](../patterns/navigation.md) ya tiene anotado—. Se anuncia con
  una frase en una región `role="status"`: «Préstamo de Taladro, se pasó la
  fecha».
- **Tras confirmar, el foco pasa al mensaje de confirmación**, un contenedor con
  `tabIndex={-1}` que lo recibe. El motivo es duro: el botón **desaparece**, y el
  foco que estaba en él cae al `<body>`; quien navega con teclado se queda sin
  sitio, que es el mismo descuido que [`UploadField`](upload-field.md) documenta
  al cancelar.
- **Y entonces la región `role="status"` no repite ese texto.** Mover el foco a un
  mensaje ya lo lee; anunciarlo además en una región viva lo lee dos veces. La
  regla que sale de aquí, y que vale para toda la pantalla: **el foco se mueve o
  se anuncia, nunca las dos cosas para el mismo cambio**. La región viva se
  reserva para lo que no mueve el foco —el fin de la carga y el fallo de red—.
- **El recorrido de teclado es cortísimo, y en un estado es de longitud cero.**
  Con el préstamo abierto: una parada, el botón, y la siguiente tabulación sale
  del documento. Con el préstamo devuelto: **ninguna parada**. Una página sin un
  solo elemento enfocable es correcta cuando es un documento —se lee con el cursor
  del lector, no con el tabulador—, y decirlo evita que alguien «arregle» el
  problema añadiendo un enlace que la pantalla no debe tener.
- **Sin enlace de salto al contenido.** No hay navegación por delante que
  saltarse, igual que en las siete pantallas de enrolamiento.
- **Un `<h1>` y ninguno más.** Es lo que permite a las pruebas decir «estoy en esta
  pantalla», y aquí el `h1` es el nombre del asset: la aserción es
  `getByRole('heading', { level: 1, name: 'Taladro' })`.
- **Las fechas van en un `<dl>`**, que es la estructura de pares etiqueta-valor
  que un lector de pantalla sabe recorrer. Dos `<p>` con un guion en medio se ven
  igual y no significan nada.
- **El `<title>` del documento es el nombre del asset.** Esta pestaña puede quedarse
  abierta semanas, que es precisamente el caso que `AuthCard` no cubre —«las siete
  pantallas comparten el `<title>DRP</title>`»—, y aquí sí importa.
- **El distintivo no se anuncia solo al cambiar**, porque no cambia solo: cuando
  pasa a «Devuelto» es porque el usuario acaba de confirmar, y de eso ya se
  encarga el foco.

## Ejemplos correctos, antiusos y evidencias de prueba

Correcto — la pantalla con el préstamo abierto, sobre el lienzo que ya existe:

```tsx
<AuthCard title={loan.assetName} subtitle={FRAME[role][loan.status]}>
  <p className="text-caption text-ink-muted">Un préstamo anotado en DRP</p>
  <LoanStatusLine status={loan.status} dueAt={loan.dueAt} />
  <dl>…</dl>
  <Button variant="primary" onClick={confirm} busy={confirming}>
    {RETURN_LABEL[role]}
  </Button>
  <p className="text-caption text-ink-muted">
    Se cerrará el préstamo y este enlace dejará de servir.
  </p>
</AuthCard>
```

El `title` es el nombre del asset y no cambia mientras la pantalla vive, que es
lo que [`card.md`](card.md) exige. La pantalla de carga es **otra** instancia con
**otro** titular, igual que el enrolamiento instancia dos veces el mismo lienzo
para sus dos momentos.

Antiusos:

| Antiuso | Por qué |
|---|---|
| Enseñar quién presta o quién recibe | No está en la vista acotada, y su ausencia es el alcance del token, no un olvido |
| Pedir el asset, el hogar o el listado desde aquí | El token no alcanza: son `401`. Y no hay `assetId` que pedir |
| Distinguir en pantalla el `401` del `404` | Delata si el préstamo existe. Los dos pintan lo mismo |
| Ofrecer «Entrar» o «Crea tu hogar» en el enlace roto | Una pantalla terminal que ofrece salida es un embudo, y quien mira no ha pedido un producto |
| `Notice tone="danger"` para un préstamo vencido | `danger` es fallo del sistema e interrumpe con `role="alert"`. Quien lo lee solo se olvidó |
| Mapear `OVERDUE` a `danger` en el distintivo | Anula la separación entre feedback y estado del dominio que los tokens `state-*` existen para mantener |
| Un diálogo de confirmación sobre la acción | La pantalla ya es la confirmación: nombra el objeto y la consecuencia, y no tiene nada más |
| Enfocar el botón al cargar | Se confirma con la barra espaciadora sin haber leído la pantalla |
| Reutilizar `request()` sin pasar la credencial a mano | Manda el token de sesión si lo hay, y devuelve el `Loan` completo a una pantalla que no debe pintarlo |
| Renovar la sesión ante un `401` | No hay refresh token de un token acotado. Un `401` aquí es «este enlace ya no vale» |
| Guardar el token en `localStorage` o en el almacén de sesión | No es una sesión: es una credencial de un enlace, de vida larga y revocable |
| Decodificar el JWT para sacar el papel | Ata la pantalla a la forma interna de la credencial |
| Truncar el nombre del asset | Es el único dato de la pantalla. Truncar aquí es truncar el contenido |
| Ensanchar la columna en pantalla grande | Líneas de doscientos caracteres. El sobrante va a margen |
| Tratar el `409` como un error | El préstamo se cerró desde casa: lo que quería hacer ya está hecho |
| Un `Toast` al confirmar | No existe, y no hace falta: el cambio se ve en la propia pantalla |

Evidencias de prueba: las de componente están en
[`loans.test.tsx`](../../../../frontend/src/routes/loans.test.tsx) y el recorrido
de punta a punta en
[`vertical-journey.spec.ts`](../../../../frontend/e2e/vertical-journey.spec.ts).
Ese segundo es el que de verdad demuestra lo que ninguna prueba de componente
puede: que el enlace del correo real de Mailpit abre esta pantalla **en un
navegador sin sesión** —contexto nuevo, no la misma pestaña— y que desde ahí se
cierra el préstamo.

Del recorrido salen además cuatro comprobaciones que esta pantalla es el único
sitio donde tienen sentido, porque es la única sin shell y sin sesión:

- Que ni el nombre del externo ni el landmark de navegación existen en el DOM
  entregado. Es la aserción que protege la frontera de los cinco datos.
- Que se llega al botón **con el tabulador** y se confirma con `Enter`. Aquí no es
  un extra: la pantalla tiene una sola acción y es terminal, así que sin teclado
  no hay forma de cerrar el préstamo.
- Que el anillo de foco se ve en **cada** parada del camino.
- Que la devolución se anuncia **una sola vez** —la regla de más arriba, ahora con
  una prueba que la cuenta— y que la pantalla no desborda a lo ancho a 320 ni a
  375 px ni se estira sin tope en ultrawide.

## Estado de implementación y enlace al componente real

**Implementado.** La pantalla es `ExternalLoanPage` en
[`routes/loans.tsx`](../../../../frontend/src/routes/loans.tsx), con
`ExternalLoanView` para el préstamo y `BrokenLink` para el enlace que no vale. La
ruta `/prestamo` está en [`App.tsx`](../../../../frontend/src/App.tsx) **fuera de
`RequireSession`**, que es la mitad del asunto, y las dos llamadas acotadas viven
en [`client.ts`](../../../../frontend/src/api/client.ts).

De las piezas del sistema que esta ficha pedía prestadas, el Hito 4 resolvió seis:
`role` viaja ya en `LoanExternalView`, `StatusBadge` admite los tonos de dominio,
los tres estados del préstamo tienen tono y etiqueta, `client.ts` sabe llamar con
una credencial que no es la de la sesión, los códigos `LOAN_*` están tipados con su
mensaje en castellano y `formatDate` existe.

### Dónde lo construido se separó de lo especificado

Dos cosas, y las dos conviene que estén escritas aquí y no descubrirlas leyendo el
código:

- **El enlace roto no usa un error bloqueante, porque no se construyó.**
  `BrokenLink` es `AuthCard` con una frase y ninguna salida: cumple la regla de no
  delatar nada y de no ofrecer embudo, pero **sin ilustración y sin ser un
  componente reutilizable**. La propuesta de `BlockingError` sigue en pie y sigue
  abajo, con lo que cambia ahora que hay un caso resuelto a mano.
- **Tras confirmar, el foco no se mueve: se anuncia.** La ficha especificaba lo
  contrario —el foco al mensaje, con `tabIndex={-1}`— y lo construido deja que el
  botón desaparezca y la pantalla se quede **sin ninguna parada de tabulador**,
  que es el estado final que esta misma ficha declara correcto. Como el foco no se
  mueve, la noticia va en la región viva, y ahí estaba el defecto: había **dos**
  —el anuncio invisible y el `Notice`, que también es `role="status"`—, así que un
  lector de pantalla leía el mismo cambio dos veces. Se quitó el invisible, que
  decía menos, y el recorrido de punta a punta ahora **cuenta** las regiones para
  que no vuelvan a ser dos. La regla de la que salió el hallazgo estaba escrita
  aquí antes de que hubiera código: el foco se mueve o se anuncia, nunca las dos
  cosas.

### Lo que falta

- **Sin iconos.** `lucide-react` sigue fuera de
  [`package.json`](../../../../frontend/package.json), así que `hand-helping`,
  `alarm-clock` y `check-circle` no existen y el estado se dice con color y
  etiqueta. Aquí eso duele más que en un listado: **el ámbar sin despertador al
  lado es la mitad del mensaje de un préstamo vencido**.
- **No hay error bloqueante a pantalla completa**, ni la ilustración que pide:
  `assets/` sigue vacío.
- **No hay `<title>` por pantalla.** Sigue siendo el `<title>DRP</title>` de
  [`index.html`](../../../../frontend/index.html) para todo el producto, y esta
  pestaña es la que puede quedarse abierta semanas: es el hueco que `AuthCard`
  tiene anotado y donde más se nota.
- **El fin de la carga no se anuncia.** La ficha pedía una frase en región viva al
  pasar de «Abriendo el préstamo» a la pantalla montada, y no está: es el mismo
  hueco del anuncio de ruta que [`navigation.md`](../patterns/navigation.md)
  arrastra, y aquí no lo tapa nada porque no hay shell.
- **No existe reabrir un préstamo.** El contrato tiene iniciar y devolver, y nada
  entre medias: una devolución confirmada por error solo se arregla iniciando otro
  préstamo. No es un problema de esta pantalla —es la razón por la que su acción
  no necesita diálogo— pero conviene que esté escrito antes de que alguien lo
  descubra confirmando sin querer.

### La primitiva nueva que sí hace falta: `BlockingError`

De todo lo anterior, una sola cosa es un componente nuevo. Las demás son
correcciones de piezas que ya existen o cambios de contrato.

**`BlockingError` — la vista entera de error, con ilustración, una frase y como
mucho una salida.** [`look-and-feel.md`](../../product-design/look-and-feel.md) la
describe entre sus estados de experiencia, [`notice.md`](notice.md) dice
expresamente que `Notice` **no** es eso, y el producto la necesita ya en cuatro
sitios: sesión caducada, hogar sin acceso, error del servidor y este enlace roto.

Por qué no vale una composición de lo que hay:

- **`AuthCard` + `Notice tone="danger"`** da una caja roja con borde en medio de
  una página por lo demás vacía, y un `role="alert"` que interrumpe al montarse
  cuando lo que ha ocurrido es que la pantalla entera **es** el mensaje. Un aviso
  es un bloque dentro de un flujo; aquí no hay flujo.
- **No hay sitio para la ilustración.** Es una de las tres apariciones que
  `iconography.md` admite, y `AuthCard` no tiene ranura para ella —hueco que su
  propia ficha ya reconoce—.
- **La salida es opcional, y ese es el caso que obliga a diseñar la API.** Los
  otros tres errores bloqueantes llevan una acción («Volver a entrar»); este no
  lleva ninguna, y sostener «como mucho una salida, a veces cero» es una decisión
  de componente, no algo que se resuelva pasando `children`.

Lo que **no** hace falta, y conviene decirlo para que no se construya de paso:
ningún `Dialog` —argumentado más arriba—, ningún lienzo nuevo —`AuthCard` sirve—
y ninguna variante de `Button`, `Notice` ni `Spinner`.

**No se construyó, y el argumento cambia poco.** El Hito 4 resolvió este caso con
`BrokenLink`, local a la ruta: `AuthCard`, una frase y ninguna salida. Lo que eso
demuestra es que la parte difícil de `BlockingError` no era el enlace roto —el más
fácil de los cuatro, porque no lleva acción— sino los otros tres, que sí la
llevan. Así que la primitiva sigue pendiente con **tres sitios esperándola**
—sesión caducada, hogar sin acceso y error del servidor— y uno ya resuelto que
sirve de punto de partida en lugar de página en blanco. Lo que `BrokenLink` no
resuelve y la primitiva sí tendría que resolver es la ilustración, la salida
opcional y no estar copiado en cada ruta que lo necesite.

## Referencias

- [`../README.md`](../README.md): la ficha mínima de un componente.
- [`card.md`](card.md): el lienzo, el `main`, la anchura y el `h1` en serif.
- [`status-badge.md`](status-badge.md): el distintivo, y por qué sus tonos no son
  los del dominio todavía.
- [`notice.md`](notice.md): la frontera entre feedback del sistema y estado del
  dominio, y la ausencia del error bloqueante.
- [`button.md`](button.md) y [`spinner.md`](spinner.md): la acción y la carga.
- [`upload-field.md`](upload-field.md): el foco que cae al `<body>` cuando
  desaparece el control que lo tenía.
- [`avatar.md`](avatar.md): la operación cuya respuesta **no** trae el dato nuevo,
  al revés que esta.
- [`patterns/feedback.md`](../patterns/feedback.md): qué hace el cliente con cada
  código de la API.
- [`patterns/navigation.md`](../patterns/navigation.md): el shell que esta
  pantalla no tiene, y el cambio de vista que nadie anuncia.
- [`foundations/color.md`](../foundations/color.md) y
  [`foundations/iconography.md`](../foundations/iconography.md): los tokens
  `state-*` y el icono fijo de cada estado.
- [`foundations/space.md`](../foundations/space.md) y
  [`foundations/typography.md`](../foundations/typography.md): la regla del
  sobrante y la serif del titular.
- [`look-and-feel.md`](../../product-design/look-and-feel.md): el escenario
  ultrawide, el error bloqueante y las operaciones destructivas.
- [`loans.md`](../../../common/product/loans.md): el préstamo, sus estados y por
  qué un externo necesita un canal de contacto.
- [`json-examples.md`](../../../common/contracts/json-examples.md): las dos
  formas de la misma respuesta, una al lado de la otra.
- [`openapi.yaml`](../../../../openapi.yaml): `LoanExternalView`, `LoanView`, el
  esquema de seguridad `loanToken` y las dos operaciones que alcanza.

## Historial de cambios

| Fecha | Cambio | Autor |
|---|---|---|
| 2026-08-16 | Creación de la ficha al arrancar el Hito 4. La pantalla está **prevista**: no existe ni la ruta ni la llamada. Se documenta por qué la vista externa es componente y no patrón, la regla de que el enlace roto no delate nada, la ausencia de diálogo de confirmación y el papel que falta en la vista acotada. Se propone una primitiva nueva, `BlockingError`. | Equipo DRP |
| 2026-08-17 | La pantalla existe: pasa a **Implementado** y la ficha describe el código. Se anotan las dos separaciones entre lo especificado y lo construido —el enlace roto sin error bloqueante y el anuncio en lugar del movimiento de foco—, el defecto de las dos regiones vivas que la regla del anuncio único delató y su arreglo, las seis piezas del sistema que el hito resolvió y las cuatro que siguen faltando. `BlockingError` no se construyó: queda con tres sitios esperándola y uno resuelto a mano. | Equipo DRP |
