# 4.1.4 Usuarios y roles

| Campo | Valor |
|---|---|
| Estado | Vigente |
| Responsable | Equipo DRP |
| Ámbito | Identidad, pertenencia, roles y credenciales |
| Última revisión | 2026-08-10 |

> Trasladado desde la sección 4.1.4 del [`README principal`](../../../README.md) al iniciar la Fase 1. **Los números de sección se conservan**: hay más de cien referencias cruzadas del tipo «ver 4.1.1» repartidas por el repositorio, y renumerarlas las rompería todas.

Se contemplan cuatro roles, agrupados en dos tipos según su alcance:

| Rol | Tipo | Alcance | Permisos típicos |
|---|---|---|---|
| Administrador del hogar | Estructural | Todo el hogar | CRUD completo de assets, ubicaciones y usuarios; gestión de roles; activar/desactivar módulos |
| Miembro del hogar | Estructural | Todo el hogar | CRUD de assets y ubicaciones; iniciar y gestionar préstamos; sin gestión de usuarios ni módulos |
| Prestador | Contextual (ligado a un préstamo) | Un préstamo concreto | Consultar el estado del préstamo; confirmar la entrega del asset |
| Receptor del préstamo | Contextual (ligado a un préstamo) | Un préstamo concreto | Consultar el estado y la fecha prevista de devolución; confirmar la devolución |

**Atributos mínimos de un Hogar:**

El hogar es la unidad de aislamiento que agrupa a todo lo demás (ver 5.6), y hasta ahora solo tenía nombre.

| Atributo | Descripción |
|---|---|
| Identificador (`id`) | — |
| Nombre (`name`) | — |
| Zona horaria (`timeZone`) | Identificador IANA, p. ej. `Europe/Madrid`. **La necesita el proceso diario de vencidos** (ver 4.1.5): «la fecha prevista ya pasó» no significa nada sin saber respecto a qué huso, y un hogar no tiene por qué estar donde esté el servidor |
| Fecha de alta (`createdAt`) | — |
| Última modificación (`updatedAt`) | — |

**Quién eres y qué eres aquí: identidad y pertenencia.**

Una persona y su papel en un hogar son dos cosas distintas, y el core las separa en dos entidades:

| Concepto | Qué es | Qué guarda |
|---|---|---|
| **`Identity`** | Quién eres en la instalación | Correo, contraseña, verificación, últimos accesos |
| **`HouseholdMember`** | Qué eres dentro de un hogar concreto | Rol, y todo lo que el dominio cuelga de «un usuario» |

En el MVP **una identidad tiene como mucho una pertenencia activa**, así que en la práctica se comporta igual que antes. Se separan ahora porque los casos que rompen esa suposición son cotidianos —custodia compartida, un piso de estudiantes además de la casa familiar, quien lleva también el inventario de sus padres— y porque hacerlo después obligaría a tocar la identidad, el token y todas las consultas a la vez. Se prepara la estructura y se limita el comportamiento.

De la separación salen cuatro consecuencias que conviene tener presentes:

- **El correo es único en toda la instalación**, no dentro del hogar, y ya no se libera al dejar uno: la identidad sobrevive a la pertenencia y podrá entrar en otro hogar más adelante.
- **Todo lo que el dominio llama «usuario» apunta a la pertenencia**, no a la identidad: el propietario de un asset, el prestador y el receptor de un préstamo, y el `createdBy`/`updatedBy` de cualquier fila. Así sigue funcionando la clave ajena compuesta que impide atribuir algo a alguien de otro hogar (ver 5.6).
- **Los refresh tokens cuelgan de la identidad**, porque la sesión es de la persona, no de su papel.
- **`Identity` no lleva `householdId`**, y por tanto queda fuera de Row-Level Security. Es la primera tabla con datos personales sin esa segunda capa, así que su control de acceso vive **solo** en la aplicación: una identidad solo puede leerse a sí misma. Conviene no perderlo de vista al escribir ese repositorio.

**Atributos mínimos de una Identity:**

| Atributo | Descripción |
|---|---|
| Identificador (`id`) | — |
| Nombre (`name`) | Nombre de la persona |
| Correo electrónico (`email`) | Identifica a la persona en toda la instalación. Se compara **normalizado a minúsculas**: `Kike@x.com` y `kike@x.com` son la misma persona |
| Teléfono (`phone`) | Opcional. Al participante externo de un préstamo ya se le exige un canal para mandarle el enlace; al miembro del hogar no se le pedía ninguno |
| Hash de contraseña (`passwordHash`) | `Argon2id`, nunca la contraseña en claro (ver 5.4.1) |
| Correo verificado (`emailVerifiedAt`) | Mientras esté vacío no se puede iniciar sesión |
| Avatar (`avatarUrl` / `avatarFile`) | Opcional. Enlace a una imagen **o** un fichero subido, nunca los dos. El subido **no** es un `StoredFile`: son columnas de esta misma tabla, uno solo, siempre sustituido y con 1 MB de tope, porque una identidad no tiene hogar al que cargarle cuota (ver 4.1.1) |
| Último acceso (`lastLoginAt`) | Deja ver cuentas dormidas antes de decidir una baja |
| Baja (`deactivatedAt`) | La cuenta entera, que es distinto de dejar un hogar |
| Fecha de alta (`createdAt`) | — |
| Última modificación (`updatedAt`) | — |

No lleva autoría propia, por el mismo motivo que el hogar: la identidad que abre un hogar no la crea nadie de dentro, y una autoría sin `householdId` no podría apoyarse en la clave ajena compuesta.

**Atributos mínimos de un HouseholdMember:**

| Atributo | Descripción |
|---|---|
| Identificador (`id`) | — |
| Identidad (`identityId`) | La persona detrás de esta pertenencia |
| Rol (`role`) | `HOUSEHOLD_ADMIN` o `HOUSEHOLD_MEMBER` (ver arriba) |
| Baja (`deactivatedAt`) | Informado si esa persona ha dejado **este** hogar |
| Fecha de alta (`createdAt`) | — |
| Última modificación (`updatedAt`) | — |
| Creado por (`createdBy`) | Ver «Autoría de los cambios» |
| Modificado por (`updatedBy`) | Ídem |

**Alta de un hogar: autoservicio con verificación.**

Crear un hogar es lo que da existencia a un inquilino, así que es **la única escritura que no exige credencial alguna** — ni contraseña ni token. Las otras operaciones abiertas de la API sí llevan algo: el login lleva credenciales, y verificar el correo o aceptar una invitación llevan un token de un solo uso recibido por correo. El recorrido tiene dos pasos, y el hogar no sirve hasta completar el segundo:

1. **`CreateHousehold`** recibe el nombre y la zona horaria del hogar junto al nombre, correo y contraseña de quien lo abre. En una sola transacción genera el `householdId`, crea la identidad sin verificar, el hogar, la pertenencia con rol `HOUSEHOLD_ADMIN` y siembra las categorías por defecto (ver 4.1.1). Emite un token de verificación de un solo uso y lo envía por correo. **No devuelve tokens de sesión**: no hay nada que hacer todavía.
2. **`VerifyEmail`** consume ese token, marca la identidad como verificada y entonces sí devuelve el par de tokens. Es también el momento en que se publica `HouseholdCreated` (ver 5.2.3): un módulo activo no debería sembrar datos para un hogar que quizá no llegue a existir de verdad.

Iniciar sesión con el correo sin verificar se rechaza; `ResendVerification` reenvía el enlace si caducó o se perdió.

> **La infraestructura de correo deja de ser aplazable.** La decisión de 4.1.7 dejaba la invitación por email para más adelante porque no había con qué enviar correos. Exigir verificación en el alta cambia esa premisa: si hay correo el primer día, lo que sostenía el aplazamiento desaparece (ver 4.1.7).

Dos cosas que se derivan de que el endpoint sea anónimo:

- **No puede delatar quién está registrado.** Responder «ese correo ya existe» permitiría a cualquiera comprobar si una persona usa el sistema. La respuesta es siempre la misma, y es el correo recibido —o su ausencia— el que explica lo que ha pasado.
- **Habrá hogares creados y nunca verificados.** Es el precio del registro abierto. Se purgan **a los 7 días**, junto con la identidad que los abrió si no llegó a verificarse. El token de verificación caduca mucho antes, así que una semana da margen de sobra para reenviarlo; alargarlo solo acumula hogares fantasma y mantiene retenido un correo que quizá ni era de quien lo tecleó. Es **el único borrado real del core** —todo lo demás es baja lógica— y se justifica porque ahí no hay nada que conservar: unas categorías sembradas y una identidad que nunca llegó a entrar.

**Sumar a alguien al hogar: invitación, no alta directa.**

Un administrador no crea cuentas ajenas: **invita**. `InviteUser` recibe el correo y el rol, emite un token de un solo uso y lo envía; `AcceptInvitation` lo consume y es quien acepta el que elige su contraseña. Si esa persona ya tiene identidad en la instalación, la invitación **la vincula** en lugar de duplicarla.

Aceptar una invitación **verifica el correo por sí solo**: haber recibido el token demuestra el control de esa dirección, que es exactamente lo que la verificación comprueba. Por eso no hay un segundo paso de verificación para quien entra invitado.

| Atributo de una Invitation | Descripción |
|---|---|
| Identificador (`id`) | — |
| Correo invitado (`email`) | Normalizado a minúsculas, como en la identidad |
| Rol propuesto (`role`) | `HOUSEHOLD_ADMIN` o `HOUSEHOLD_MEMBER` |
| Hash del token (`tokenHash`) | Nunca el token en claro, igual que en préstamos y verificación |
| Caducidad (`expiresAt`) | 7 días, la misma ventana que la retención de hogares sin verificar y por el mismo motivo |
| Aceptada (`acceptedAt`) | — |
| Revocada (`revokedAt`) | Un administrador puede retirarla, p. ej. si se equivocó de dirección |
| Fecha de alta (`createdAt`) | — |
| Creado por (`createdBy`) | Ver «Autoría de los cambios» |

El estado de una invitación no es un campo: se deduce de esas tres fechas y del reloj. Solo puede haber **una invitación viva por correo y hogar**.

Esto sustituye al alta directa, y con ella desaparece `mustChangePassword`: era el apaño para que alguien cambiara una contraseña que otro le había puesto, y ya nadie pone la contraseña de nadie. La contrapartida es que un miembro sin correo —un menor, alguien mayor que no lo usa— no puede entrar por esta vía; si aparece esa necesidad, será una decisión nueva y no la recuperación del alta directa tal cual.

**Contraseñas: olvidarla y cambiarla.**

La contraseña vive en la identidad, así que ambas operaciones son de la persona y no de ninguno de sus hogares.

**Olvidarla** son dos pasos, con el mismo patrón que la verificación: `RequestPasswordReset` recibe un correo y, si hay identidad activa detrás, emite un token de un solo uso y lo envía; `ResetPassword` lo consume y fija la contraseña nueva. Como el alta de hogar, **responde siempre igual** exista o no ese correo — es un endpoint anónimo, y contestar otra cosa diría a cualquiera quién usa el sistema.

Tres cosas que lo diferencian de los demás tokens del sistema:

- **Dura una hora, no siete días.** Este token cambia una credencial; una invitación solo propone entrar en un hogar. No corren el mismo riesgo, así que no merecen el mismo plazo. Una hora cubre de sobra ir al correo y volver.
- **Restablecer cierra todas las sesiones.** Se revocan todos los refresh tokens de esa identidad. Si el motivo del restablecimiento era que alguien más había entrado, dejarle la sesión abierta anula el gesto entero. La revocación ocurre **antes** de emitir el par de tokens nuevo, no después.
- **Restablecer verifica el correo.** Por lo mismo que aceptar una invitación: recibir el token demuestra el control de la dirección, que es justo lo que la verificación comprueba. Un hogar creado y nunca verificado se rescata restableciendo la contraseña, y sale de la cola de purga.

Solo hay **un token de restablecimiento vivo por identidad**: pedir otro invalida el anterior.

**Cambiarla estando dentro** es `ChangePassword`, y exige la contraseña actual además de la nueva. No es burocracia: sin ese requisito, quien se hiciera con un access token robado podría cambiar la contraseña y dejar fuera al dueño de la cuenta. Revoca las **demás** sesiones y conserva la que está en uso.

> Una identidad dada de baja no puede restablecer contraseña. No recibe correo, y la respuesta es la misma que en cualquier otro caso.

**Qué se admite como contraseña.**

Una sola regla de forma: **mínimo 12 caracteres**, y ninguna exigencia de mayúsculas, dígitos ni símbolos. Las reglas de composición no producen contraseñas más difíciles de adivinar, sino más difíciles de recordar: empujan hacia el patrón `Password1!` y hacia el papel pegado al monitor. Lo que de verdad encarece un ataque es la longitud.

Sobre eso, una única comprobación de contenido: **se rechazan las contraseñas más comunes**, contra una lista empaquetada con la aplicación. Cubre el grueso del problema real sin depender de ningún servicio externo, que en un camino crítico como el alta significaría latencia y un plan B para cuando no responda; una instalación sin salida a internet se comporta igual.

Lo que **no** hay, y es deliberado: ni caducidad periódica, que solo produce variaciones triviales del mismo secreto, ni historial de contraseñas anteriores, que obligaría a conservar credenciales viejas — un pasivo, no un activo. Una contraseña se cambia cuando hay motivo, no por calendario.

La regla se aplica en los **cuatro** puntos donde se fija una contraseña: al crear un hogar, al aceptar una invitación, al restablecerla y al cambiarla estando dentro.

> **Por eso el hash es `Argon2id` y no `BCrypt`.** BCrypt ignora en silencio todo lo que pase de 72 bytes: no falla, trunca. Con una política que favorece frases largas, dos contraseñas distintas que compartan los primeros 72 bytes serían la misma para el sistema. Argon2id no tiene ese límite, viene de serie en Spring Security y es hoy la recomendación habitual. Cambiarlo ahora no cuesta nada —no hay ni una línea de código escrita—, y envuelto en un `DelegatingPasswordEncoder` el algoritmo deja de ser una puerta de una sola dirección. Su configuración mínima es la que recomienda OWASP: **19 MiB de memoria, 2 iteraciones y grado de paralelismo 1**. Es un suelo, no un objetivo — subirlo es correcto si el hardware lo aguanta, y bajarlo no.

**Bajas: dejar un hogar no es cerrar la cuenta.**

Son dos operaciones distintas, y la separación entre identidad y pertenencia es lo que permite distinguirlas:

- **Dejar el hogar** marca `deactivatedAt` en la **pertenencia**. La persona deja de ver ese hogar, pero su identidad sigue existiendo. La fila permanece porque los préstamos y el historial la referencian.
- **Cerrar la cuenta** marca `deactivatedAt` en la **identidad**, revoca sus refresh tokens, le impide autenticarse en cualquier hogar y **borra su avatar**: es lo único del sistema que retrata a una persona, y la fila que se conserva por historial no necesita su cara. Los ficheros del hogar se quedan, porque son del hogar y no suyos.

Al dejar un hogar, sus assets **quedan sin propietario**, no se reasignan solos. Aparecen en un listado de huérfanos (`ListAssets` con el filtro correspondiente, ver 5.7) y se reasignan cuando el hogar decida. La alternativa —exigir el destino de todo lo suyo en el mismo gesto— convierte una baja en un inventario completo, y con cuarenta cosas a su nombre eso significa que la baja no se hace.

Un `HOUSEHOLD_ADMIN` no puede dejar el hogar si es el único que queda: se quedaría sin quien gestione usuarios y módulos.

Los roles **estructurales** (administrador/miembro) pertenecen a usuarios del hogar con cuenta completa. Los roles **contextuales** (prestador/receptor) pueden recaer tanto en miembros del hogar como en personas externas (p. ej. un vecino al que se le presta un taladro); el acceso acotado por token (ver 5.4.1) se aplica únicamente cuando la persona no tiene una cuenta completa en el sistema.
