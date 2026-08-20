# Auditoría OWASP previa al despliegue

| Campo | Valor |
|---|---|
| Estado | Vigente |
| Responsable | kikescribano |
| Ámbito | common (backend, frontend, infraestructura y despliegue) |
| Última revisión | 2026-08-20 |

## Propósito

Este documento recoge la auditoría de seguridad que se hizo **antes de desplegar
DRP en una VPS pública** (el candidato es una VPS-2 de OVH), contrastando el
código y la configuración reales contra las
[OWASP Cheat Sheets](https://cheatsheetseries.owasp.org/). Responde a una
pregunta concreta: *¿es seguro exponer esto a internet, y qué hay que cerrar
antes?*

Es una **foto del estado a 2026-08-20**, tomada sobre la rama de auditoría con el
cierre de huecos ya fusionado (106 operaciones, 31 tablas). No sustituye a
[`docs/backend/security/`](../../backend/security/README.md), que documenta los
controles por diseño; aquí se listan **hallazgos y deudas**, con su severidad y
la acción que los cierra.

## Alcance

### Incluido

- Autenticación, sesiones, contraseñas y tokens de un solo uso.
- Autorización y aislamiento multi-tenant (RLS, IDOR, mass assignment).
- Subida, almacenamiento y entrega de ficheros.
- Inyección, XSS, cabeceras HTTP, CORS, manejo de errores y logging.
- Infraestructura, secretos, dependencias (CVE) y CI.
- Requisitos de seguridad del despliegue en la VPS, que **todavía no está
  diseñado** y se listan aquí para que entren en su documento de alcance.

### Fuera de alcance

- El diseño del despliegue en sí (compose de producción, TLS, systemd): es un
  bloque de trabajo propio, aún sin planificar. Aquí solo se enumeran sus
  requisitos de seguridad.
- Pruebas dinámicas (DAST) y pentesting sobre una instancia corriendo: esto es
  una revisión de código y configuración.
- El análisis antivirus de ficheros, que sigue **aplazado a propósito** con su
  motivo en [`decisions.md`](../product/decisions.md) y en la
  [ADR-005](../architecture/decisions/ADR-005-local-file-storage.md).

## Método

Cinco revisiones en paralelo, una por superficie, seguidas de una verificación
de los hallazgos graves leyendo el código de primera mano —incluida la
comprobación de las versiones reales en `backend/build.gradle.kts`— para no
trasladar al documento nada apoyado solo en un comentario o en una suposición.
Cada hallazgo cita `fichero:línea`.

## Veredicto

**No hay ninguna vulnerabilidad crítica en el código actual, y la base de
seguridad es notablemente sólida y está razonada por escrito.** Los dos
invariantes de aislamiento están implementados con rigor y fijados por pruebas
que fallarían ante una regresión: las 31 tablas con RLS `FORCE`, el usuario de
aplicación sin `BYPASSRLS`, ningún caso de uso que acepte un `householdId` del
cliente, contraseñas con Argon2id a los parámetros de OWASP, tokens de un solo
uso hasheados y con caducidad, login sin enumeración, y una entrega de ficheros
que sigue de cerca la File Upload Cheat Sheet.

La condición que **sí** hay que tomarse en serio antes de exponerlo es doble:

1. **Cinco puntos del código actual (severidad ALTA)** que conviene cerrar antes
   del despliegue, tres de ellos *promesas que el propio código o la
   documentación hacen y que quedaron a medio cumplir*: el rate limit «cuando
   llegue nginx», la validación de secretos aplicada a un secreto sí y a otro
   no, y el reparto imagen/documento de la ADR-005. **Tres de los cinco ya están
   corregidos** (los dos fail-open de secretos y el rate limit tras el proxy), y
   del cuarto —las dependencias de Spring— está cerrada la parte que importaba
   hoy: **todas las CVE concretas**, más la vigilancia continua que evita repetir
   esta auditoría a mano. Queda el salto a la línea 4.x de Spring Boot, que es un
   bloque propio, y la sustitución de `webp-imageio`.
2. **El despliegue todavía no existe**, y sin TLS, firewall, secretos
   inyectados, backups automatizados y un compose de producción endurecido, la
   solidez del código no basta. Eso es un bloque de trabajo propio (§ Requisitos
   del despliegue).

## Resumen priorizado

Orden de atención recomendado. La columna «Área» remite al detalle más abajo.
**«Detectado» es la revisión que lo encontró y «Corregido» la fecha en que se
cerró**; un guion en la segunda significa que sigue abierto.

| # | Sev. | Hallazgo | Área | Detectado | Corregido |
|---|---|---|---|---|---|
| 1 | ALTA | El rate limit por IP colapsa en un cubo global detrás de nginx (DoS de toda la autenticación) | Auth | 2026-08-20 | 2026-08-20 |
| 2 | ALTA | Fail-open del secreto JWT: sin perfil activo, producción arranca con la clave de ejemplo del repositorio | Infra | 2026-08-20 | 2026-08-20 |
| 3 | ALTA | `DRP_FILES_LINK_SECRET` no se valida al arrancar: puede quedar el secreto de firma de desarrollo en producción | Ficheros | 2026-08-20 | 2026-08-20 |
| 4 | ALTA | `webp-imageio 0.1.6` embebe libwebp anterior al fix de CVE-2023-4863 y decodifica WebP subido por usuarios | Deps | 2026-08-20 | — |
| 5 | ALTA | Spring Boot 3.4.x fuera de soporte OSS y sin detección continua de dependencias | Deps | 2026-08-20 | parcial 2026-08-20 |
| 6 | MEDIA | `CloseAccount` (`DELETE /users/me`) no re-autentica: un access token robado expulsa a la víctima de forma permanente | AuthZ | 2026-08-20 | — |
| 7 | MEDIA | Rotación de refresh sin detección de reutilización (robo indetectable) | Auth | 2026-08-20 | — |
| 8 | MEDIA | Sesión deslizante sin tope absoluto: renovada una vez al mes, vive para siempre | Auth | 2026-08-20 | — |
| 9 | MEDIA | Refresh token de 30 días en `localStorage` (decisión aplazada no revisada) | Auth | 2026-08-20 | — |
| 10 | MEDIA | Sin CSP ni cabeceras de seguridad para la SPA; HSTS no se emitirá sin `forward-headers-strategy` | Cabeceras | 2026-08-20 | — |
| 11 | MEDIA | CORS de desarrollo cableado con `allowCredentials` y `allowedHeaders("*")`, sin variante de producción | Cabeceras | 2026-08-20 | — |
| 12 | MEDIA | Los PDF se sirven con URL firmada de portador, contra el reparto imagen/documento de la ADR-005 | Ficheros | 2026-08-20 | — |
| 13 | MEDIA | `accel-redirect` es fail-open: olvidarlo reabre la entrega de bytes en el origen de la aplicación | Ficheros | 2026-08-20 | — |
| 14 | MEDIA | Decodificación de imágenes sin límite de concurrencia (OOM con pocos PNG de 50 Mpx) | Ficheros | 2026-08-20 | — |
| 15 | MEDIA | El cambio de contraseña autenticado no tiene límite de intentos (fuerza bruta + DoS de CPU) | Auth | 2026-08-20 | — |
| 16 | MEDIA | `AcceptInvitation` emite sesión a una identidad dada de baja (deny-by-default roto) | AuthZ | 2026-08-20 | — |
| 17 | MEDIA | Oráculo de tiempo en `password-reset`/`resend`: enumeración de correos por el reloj | Auth | 2026-08-20 | — |
| 18 | MEDIA | La URL de documento externo no valida esquema y el cliente la abre con `window.open` (XSS almacenado entre miembros) | Inyección | 2026-08-20 | — |
| 19 | MEDIA | El compose publica PostgreSQL, Mailpit y nginx en `0.0.0.0` | Infra | 2026-08-20 | — |
| — | BAJA/INFO | Quince puntos más de endurecimiento (ver detalle) | Varias | 2026-08-20 | — |

> **Las dos fechas coinciden hoy porque la auditoría y el primer cierre son del
> mismo día**, y la columna existe para cuando dejen de coincidir: una revisión
> posterior añadirá filas con otra fecha de detección, y lo que hoy está abierto
> se cerrará en otra. Sin las dos fechas no se puede decir cuánto tiempo estuvo
> vivo un hallazgo, que es lo único que convierte esta tabla en un registro y no
> en una foto.
>
> **Los tres cerrados eran los tres «a medio cumplir»**, y se cerraron juntos por
> eso: los tres consistían en que una mitad del control estaba escrita y la otra
> no. Los dos ALTA que siguen abiertos —`webp-imageio` y la línea de Spring
> Boot— son subidas de dependencia con riesgo de regresión propio, así que van en
> su propio bloque y no mezclados con estos.

---

## Hallazgos ALTA (código actual)

### 1. El rate limit por IP colapsa en un cubo global detrás de nginx

*Señalado de forma independiente por cuatro de las cinco revisiones y verificado
de primera mano.*

`RateLimitFilter.kt:114` — `clientIp()` devuelve `remoteAddr ?: "desconocida"` e
ignora `X-Forwarded-For`. El comentario de las líneas 107-113 dice que la
cabecera «pasa a leerse cuando llegue nginx en el Hito 3, y solo si `remoteAddr`
es el suyo» — **eso nunca se implementó** (no hay `ForwardedHeaderFilter`,
`server.forward-headers-strategy` ni lectura de XFF en todo `backend/src/main`),
y nginx ya está en `compose.yaml` proxando `/` con `X-Forwarded-For` y
`X-Real-IP` (`docker/nginx/templates/default.conf.template:44-48`).

Consecuencia en la topología que el propio repositorio despliega: todas las
peticiones llegan con la IP del contenedor de nginx, así que el cubo de 20
peticiones / 5 min (`application.yml:135-136`) es **uno solo para toda la
instalación** sobre los ocho endpoints sin autenticar, `/auth/login` y
`/auth/refresh` incluidos. Cualquiera bloquea el login, el refresh y el reset de
contraseña de todos los hogares con 20 peticiones cada 5 minutos, sin credencial;
y una instalación con uso legítimo moderado puede autobloquearse. Falla cerrado
(429), así que no es un bypass del límite, pero sí una denegación de servicio
trivial del plano de autenticación, y la protección anti-enumeración por IP real
desaparece.

- **OWASP:** Denial of Service; degrada Authentication / Credential Stuffing.
- **Acción:** implementar lo que el comentario ya prescribe — leer
  `X-Forwarded-For`/`X-Real-IP` **solo** cuando `remoteAddr` sea el proxy de
  confianza (o `server.forward-headers-strategy=FRAMEWORK` con `set_real_ip_from`
  en nginx), con una prueba que fije que tras el proxy cada cliente tiene su
  cubo.
- **Corregido.** `ClientIpResolver` lee la cabecera solo cuando el salto
  inmediato casa con `drp.rate-limit.trusted-proxies` (IP o CIDR, **vacío por
  omisión**: sin proxy declarado no se fía de nadie, que es el comportamiento
  correcto de hoy). Toma la **última** entrada de `X-Forwarded-For`, que es la
  que añade el proxy y la única que no puede escribir quien llama — tomar la
  primera habría leído justo el valor que elige el atacante. Fijado por prueba en
  las dos direcciones: detrás del proxy dos clientes no comparten cubo, y siete
  cabeceras falsificadas distintas siguen cayendo en el mismo contador.

### 2. Fail-open del secreto JWT sin perfil activo

`SecurityConfig.kt:93-94` decide `development = environment.activeProfiles.isEmpty()
|| …`: **un despliegue que no declare ningún perfil de Spring se considera
desarrollo**. La validación de `SecurityProperties.validate` existe y es correcta
(`SecurityAdapters.kt:228-250`: exige ≥32 bytes y rechaza la clave de ejemplo),
pero solo se aplica cuando `developmentEnvironment` es falso. La clave de ejemplo
del `application.yml:124` mide 43 bytes, así que también supera el mínimo de
longitud: sin `SPRING_PROFILES_ACTIVE`, la aplicación arranca sin un aviso
firmando con una clave **publicada en el repositorio**. Quien pueda firmar tokens
elige el `householdId`, que alimenta el `TenantContext` y con él `app.household_id`
de las políticas RLS — es decir, atraviesa **las dos capas de aislamiento a la
vez**, como el propio comentario del código describe (`SecurityAdapters.kt:240-245`).

- **OWASP:** Secrets Management (evitar defaults en código; fallar cerrado).
- **Acción:** invertir el criterio — la ausencia de perfil se trata como
  producción y solo un perfil explícito `dev`/`test`/`local` tolera la clave de
  ejemplo; mejor aún, retirar el default del `application.yml` y moverlo a un
  `application-dev.yml`, de modo que sin `DRP_JWT_SECRET` el arranque falle
  siempre.
- **Corregido.** Criterio invertido: `isDevelopmentEnvironment` exige un perfil
  de desarrollo **declarado**, y la ausencia de perfil pasa a ser producción. El
  precio es que dev y pruebas tienen que declararse, y lo hacen **desde la cadena
  de construcción y no clase a clase**: `bootRun` arranca con `dev` —lo que cubre
  también el recorrido vertical, que arranca el backend por ahí— y la tarea de
  pruebas fija `test`, que era la única forma sensata de cubrir las 47 clases con
  `@SpringBootTest` sin que olvidar la anotación en la próxima se manifieste como
  un fallo de arranque sin relación aparente con la causa.

### 3. El secreto de firma de ficheros no se valida al arrancar

`application.yml:179` — `link-secret: ${DRP_FILES_LINK_SECRET:desarrollo-local-no-usar-en-produccion}`,
el mismo valor en claro en `compose.yaml:65`. Se inyecta directo en
`SignedFileUrls.kt:44` **sin equivalente al `validate()` del JWT** (verificado
por grep: ningún check sobre esa propiedad en `backend/src/main`). Es exactamente
el fallo silencioso que el JWT sí remedia, sin su remedio: en producción sin la
variable, cualquiera forja firmas MD5 de `/f/**`
(`md5(caducidad + ruta + " " + secreto)`) para cualquier ruta y cualquier
caducidad. La caducidad de 15 minutos deja de significar nada, un ex-miembro
re-firma para siempre las claves que llegó a conocer, y las claves de avatar se
derivan solo del `identityId` (`StorageKeys.kt:52`), así que basta conocer ese
UUID para construir y firmar la URL.

- **OWASP:** Secrets Management; File Upload.
- **Acción:** replicar el mecanismo de `SecurityProperties.validate` para
  `drp.files.link-secret` (constante reconocible + rechazo fuera de desarrollo +
  mínimo de longitud) en el mismo bean de arranque; en el compose de producción
  inyectarlo como secret, nunca literal.
- **Corregido.** `FileLinkProperties` replica la validación del JWT —mínimo de
  32 bytes y rechazo del valor de ejemplo fuera de desarrollo— y `SignedFileUrls`
  lo recibe **por esa clase y no por `@Value`**, de modo que no queda ningún
  camino que use el secreto sin haberlo validado. Sigue pendiente lo que no es
  del código: inyectarlo como secret en el compose de producción, que es parte
  del bloque de despliegue.

### 4. `webp-imageio 0.1.6` decodifica WebP subido con una libwebp sin parchear

`build.gradle.kts:58` — `org.sejda.imageio:webp-imageio:0.1.6`, publicada en 2020
y sin releases posteriores; empaqueta los binarios nativos de libwebp dentro del
jar y los extrae al arrancar (confirmado en el comentario de las líneas 50-58).
Cualquier libwebp anterior a 1.3.2 (septiembre de 2023) es vulnerable a
**CVE-2023-4863**, el desbordamiento de heap crítico de WebP explotado
activamente en 2023, y una librería congelada en 2020 no puede llevar el fix. La
aplicación **decodifica WebP subido por usuarios autenticados** para recodificarlo
y generar la miniatura (`image/webp` está en la lista blanca de subida), es decir,
alimenta el decodificador vulnerable con bytes controlados por el atacante; y el
alta de hogares en autoservicio hace que «autenticado» sea una barrera baja.

- **OWASP:** Vulnerable Dependency Management; File Upload (decompression/parsing).
- **Acción:** sustituir por un empaquetado mantenido con libwebp ≥ 1.3.2 (p. ej.
  el fork `com.github.usefulness:webp-imageio`) y verificar la versión nativa que
  va dentro del jar; como mitigación temporal, valorar retirar `image/webp` de la
  lista blanca de subida (la escritura de miniaturas en WebP no depende de la
  entrada del usuario, pero la decodificación de la subida sí).

### 5. Spring Boot 3.4.x fuera de soporte OSS, sin detección continua de dependencias

`build.gradle.kts:7` fija Spring Boot **3.4.5**. La rama 3.4.x está fuera del
soporte OSS gratuito, así que las CVE que aparezcan en las versiones que gestiona
su BOM (Spring Framework, Spring Security, Tomcat embebido, `nimbus-jose-jwt` vía
`spring-security-oauth2-jose`) no reciben parche en esta línea. La auditoría
señaló varias CVE concretas con fuentes públicas —algunas posteriores a la fecha
de conocimiento del auditor y **pendientes de confirmar con un escáner**—; el
punto accionable no depende de ninguna en particular:

- No existe `dependabot.yml`, ni CodeQL, ni escáner de dependencias, ni secret
  scanning en `.github/` (solo `workflows/ci.yml`). Una CVE nueva no la ve nadie
  hasta una auditoría manual — que es justo lo que ha pasado aquí.
- **Nota puntual sobre `nimbus-jose-jwt`:** la aplicación parsea JWT en
  peticiones **sin autenticar** (filtro JWT y tokens de préstamo), así que una
  CVE de DoS por parseo en esa librería sería directamente alcanzable. Es la que
  antes conviene comprobar al montar el escáner.

- **OWASP:** Vulnerable Dependency Management; CI/CD Security.
- **Acción:** (a) migrar a la línea de Spring Boot con soporte vigente; (b) añadir
  `dependabot.yml` con los tres ecosistemas (`gradle`, `npm`, `github-actions`),
  un job de escaneo (Trivy / OWASP Dependency-Check / `gradle dependencyUpdates`)
  y activar secret scanning + push protection. Esto último es lo que evita
  repetir esta auditoría a mano.

> **Corrección: la primera versión de este hallazgo decía «migrar a Spring Boot
> 3.5.x, es un salto menor», y eso era falso.** Al ir a ejecutarlo se comprobó
> contra el repositorio de artefactos que **la 3.5 también está fuera de soporte
> OSS** —terminó el 2026-06-30, con la 3.5.16 como último parche— y que la línea
> con soporte es la **4.x**, hoy en 4.1.1. El destino no es un salto menor sino
> uno mayor, que arrastra **Jackson 3 con cambio de `groupId`**
> (`com.fasterxml.jackson` → `tools.jackson`), Kotlin 2.2+, Spring Security 7,
> Hibernate 7 y la línea 3.x de springdoc.
>
> Merece la pena decir **cómo** se detectó, porque es un modo de fallo que se
> repetirá: las páginas que agregan fechas de fin de vida daban versiones que no
> existen, y el índice de búsqueda de Maven Central respondía que la última
> versión era la 3.5.3. Lo que zanjó la cuestión fue el `maven-metadata.xml` del
> propio repositorio, que es el único sitio donde la respuesta no es una opinión.

### 5.b Peldaño intermedio: 3.5.16

- **Corregido en parte.** El salto a la 4.1 no se hace de una vez: la propia guía
  de migración de Spring exige pasar antes por la última 3.5.x, y ese peldaño
  **ya cierra por sí solo todas las CVE concretas** que este hallazgo listaba.
  Versiones que resuelve el BOM tras el salto, comprobadas con
  `gradle dependencies`: Spring Security **6.5.11** (hacía falta ≥ 6.5.9 para
  CVE-2026-22732), Spring Framework **6.2.19**, Tomcat embebido **10.1.55**
  (CVE-2025-48988) y `nimbus-jose-jwt` **9.37.4** (CVE-2025-53864) — la que más
  importaba, porque se parsea un JWT en peticiones sin autenticar. Con ellas
  entran también springdoc **2.9.0**, que acompaña a Boot y no se elige aparte, y
  BouncyCastle **1.85.2**, que salda el hallazgo BAJA de las CVE de ASN.1.
- **Lo que queda abierto** es el salto a la línea 4.x, que es un bloque propio por
  lo que arrastra. Mientras no se dé, el proyecto sigue en una rama sin parches
  nuevos: lo cerrado son las CVE de hoy, no la capacidad de recibir las de mañana.
- **Corregido también:** la detección continua, que era la otra mitad del
  hallazgo. Entra `.github/dependabot.yml` con los tres ecosistemas y las
  actualizaciones agrupadas —el BOM de Spring mueve decenas de artefactos, y un
  pull request por cada uno convierte la vigilancia en ruido que se acaba
  ignorando—, y la CI declara `permissions: contents: read`. **Sigue pendiente y
  no se puede hacer desde el repositorio:** activar Dependabot alerts, las
  actualizaciones de seguridad y el escaneo de secretos con protección de push,
  que son ajustes de la configuración del repositorio en GitHub.

---

## Hallazgos MEDIA (código actual)

**6. `CloseAccount` no re-autentica.** `DELETE /users/me` no lleva cuerpo
(`Controllers.kt:253-255`, `Users.kt:178-207`) y desactiva identidad y
pertenencia y revoca todas las sesiones **sin pedir la contraseña actual** —
mientras que `ChangePassword` sí la exige precisamente para que un access token
robado (15 min, irrevocable) no baste. Y no hay vuelta atrás: `Login` y
`RequestPasswordReset` descartan identidades dadas de baja, y no existe
reactivación. Token robado de 15 minutos = expulsión permanente de la víctima.
*Transaction Authorization.* **Acción:** exigir la contraseña actual en
`DELETE /users/me`, como en `ChangePassword`.

**7. Rotación de refresh sin detección de reutilización.** `RefreshSession` rota
el token, pero presentar uno ya rotado/revocado solo produce `AuthenticationFailed`
(`Authentication.kt:107-132`) sin reacción. La reutilización de un refresh rotado
es la señal canónica de robo y debería revocar toda la familia de la identidad.
Hoy, un refresh robado de `localStorage` y usado antes que el legítimo se queda
la sesión, y el dueño solo ve «sesión caducada». *JWT / Session Management.*
**Acción:** ante un hash que existe pero está revocado, `revokeAllForIdentity` +
registro del evento.

**8. Sesión deslizante sin tope absoluto.** Cada emisión, incluida cada rotación,
crea un token con `now + 30 días` (`Session.kt:54`, `application.yml:130`). Una
sesión renovada al menos una vez al mes vive para siempre; una robada, también.
*Session Management (absolute timeout).* **Acción:** arrastrar un
`sessionStartedAt` (o la cadena de rotación) y rechazar renovaciones pasado un
máximo absoluto (p. ej. 90 días).

**9. Refresh token en `localStorage`.** El access token vive en memoria (bien),
pero el refresh de 30 días persiste en `localStorage`
(`SessionProvider.tsx:61,103,256`): cualquier XSS se lo lleva, y combinado con los
hallazgos 7 y 8 se vuelve una credencial de vida larga, indetectable e ilimitada.
Es una decisión documentada con revisión aplazada «cuando nginx entre en el Hito
3» — nginx ya entró. *JWT Cheat Sheet.* **Acción:** retomar la decisión ahora que
frontend y API comparten origen tras nginx (cookie `HttpOnly`+`Secure`+`SameSite`
con defensa CSRF, o al menos `sessionStorage`).

**10. Sin CSP ni cabeceras de seguridad para la SPA.** `SecurityConfig.kt` no
toca `http.headers { }` (quedan los defectos de Spring, que solo cubren las
respuestas de la API); `frontend/index.html` no lleva `<meta>` CSP; y el `server`
de la aplicación en nginx (`default.conf.template:26-56`) no declara **ningún**
`add_header`, en contraste deliberado con el de ficheros. No hay CSP,
`Referrer-Policy` ni `Permissions-Policy` para el frontend, y el HSTS de Spring
no se emitirá detrás del proxy porque falta `server.forward-headers-strategy`
(`request.isSecure()` será falso aunque nginx termine TLS). Agravado por el
hallazgo 9. *CSP; HTTP Security Response Headers.* **Acción:** definir en la capa
que sirva el frontend en producción una CSP sin `unsafe-eval` (el trabajo de
`heic.ts` ya se hizo para eso), `frame-ancestors 'none'`, `Referrer-Policy`,
`Permissions-Policy` y HSTS; configurar `forward-headers-strategy` en el backend.
Entra en el bloque de despliegue.

**11. CORS de desarrollo cableado.** `CorsConfig.kt:20-29` — orígenes localhost en
duro, `allowedHeaders = ["*"]` y `allowCredentials = true`, sin propiedad ni
perfil que lo cambie al desplegar; el propio comentario admite que producción
«debería» restringir. `allowCredentials` no aporta nada a una API Bearer sin
cookies y solo amplía superficie. El riesgo práctico hoy es que quien despliegue
«lo arregle» con un comodín. *CORS.* **Acción:** orígenes por propiedad
(`drp.cors.allowed-origins`), lista concreta de cabeceras y `allowCredentials =
false`.

**12. Los PDF se sirven con URL firmada de portador.** `FileDtos.kt:43` emite
`url = urls.original(...)` para **todo** `StoredFile`, PDF incluido. La
documentación sostiene lo contrario — «en un documento, comprobando el hogar en
cada petición; en una imagen, al emitir la URL firmada»
([`file-upload-controls.md`](../../backend/security/file-upload-controls.md)) — y
el razonamiento de proporción de la ADR-005 («lo firmado es una foto sin EXIF»)
queda invalidado por el propio contrato: una factura PDF con nombre y dirección es
alcanzable 15 minutos por una URL de portador sin credencial. *File Upload.*
**Acción:** no emitir `url` firmada para tipos no imagen; para PDF, dejar como
única vía `GET /files/{id}/content`, que ya comprueba el hogar por petición.

**13. `accel-redirect` es fail-open.** Por omisión `false` (`application.yml:187`),
lo que activa `LocalSignedFileController` sobre `/f/**` con `permitAll`. En
producción con nginx delante, olvidar `DRP_FILES_ACCEL_REDIRECT=true` reabre la
«segunda puerta a los bytes» en el mismo origen que la sesión —justo lo que la
ADR-005 separa— y ningún chequeo de arranque liga el perfil de producción con
este interruptor. Ese camino local tampoco fija `Content-Disposition: attachment`
ni `Cache-Control`. *File Upload.* **Acción:** fallar el arranque fuera de
desarrollo si `accel-redirect=false`; añadir `attachment` y `Cache-Control` al
controlador local.

**14. Decodificación de imágenes sin límite de concurrencia.**
`ImageIoFileContentProcessor.kt:120-127` comprueba los píxeles antes de decodificar
(correcto), pero 50 Mpx a 4 B/píxel son ~200 MB por `read(0)`, más lienzo y cadena
de reducción. `UploadRateLimiter` (60/5 min por identidad) limita frecuencia, no
concurrencia, y no hay semáforo alrededor de `processor.process`. Dos o tres
subidas simultáneas de PNG de 50 Mpx (unos KB comprimidos) pueden tumbar la JVM
por OOM en la máquina prevista. *Denial of Service; File Upload.* **Acción:**
semáforo global de decodificaciones simultáneas (2-3) o bajar `max-image-pixels`.

**15. El cambio de contraseña autenticado no tiene límite de intentos.**
`POST /api/v1/auth/password` no está en `LIMITED_PATHS` (`RateLimitFilter.kt:157-166`,
que solo cubre los ocho anónimos) y no hay bloqueo de cuenta. Con un access token
robado (15 min, o XSS) se hace fuerza bruta de la contraseña actual sin freno, y
cada intento cuesta un Argon2id de 19 MiB (DoS de CPU autenticado). *Authentication.*
**Acción:** contador por identidad en ese endpoint reutilizando el limitador
existente.

**16. `AcceptInvitation` emite sesión a una identidad dada de baja.** La rama de
identidad existente solo comprueba que no haya pertenencia activa, nunca
`existing.isActive` (`Invitations.kt:185-223`). Una cuenta cerrada (ADR-012) que
reciba invitación obtiene pertenencia nueva + par de tokens, mientras `Login`,
`RefreshSession` y `VerifyEmail` la rechazan — un estado zombi de 15 minutos con
una pertenencia activa que bloquea a esa persona para siempre. Fallo de
deny-by-default (los otros cuatro caminos sí lo comprueban). *Authorization.*
**Acción:** exigir `isActive` en esa rama, o definir la reactivación explícita
como decisión de producto.

**17. Oráculo de tiempo en `password-reset` y `resend-verification`.** Cuando el
correo no existe se responde en el acto; cuando existe, la respuesta espera la
ida y vuelta SMTP completa (entrega síncrona, sin `@Async`), lo que permite
enumerar correos por el reloj pese a la respuesta 202 uniforme
(`Passwords.kt:52-59`, `Enrollment.kt:298-301`). `CreateHousehold` sí lo mitiga
enviando en las dos ramas. *Forgot Password.* **Acción:** sacar la entrega del
hilo de la petición (cola/executor), que además es el cierre que la propia nota
del código señala.

**18. La URL de documento externo no valida esquema.** El servidor acepta
cualquier texto como `ExternalLink` (`Document.kt:79,86-92`, solo `trim()`); el
contrato declara `format: uri` pero nada lo aplica. El cliente la abre con
`window.open(document.url, '_blank', 'noopener')` (`assets.tsx:1191-1192`): un
miembro guarda `javascript:…` o `data:text/html,…` como «manual» y otro lo abre.
`noopener` y los navegadores modernos mitigan el caso `javascript:`, pero es
defensa del navegador, no de la aplicación. *XSS Prevention; DOM-based XSS.*
**Acción:** aceptar solo `http`/`https` en el caso de uso (con `maxLength`), y
comprobar `new URL(url).protocol` en el cliente antes de `window.open`.

**19. El compose publica servicios internos en `0.0.0.0`.** `compose.yaml` publica
PostgreSQL (`5432`), Mailpit (`1025`/`8025`) y nginx (`8090`/`8091`) sin IP de
bind, así que Docker los expone en todas las interfaces (y suele perforar el
firewall del host). En una red compartida, un tercero llega al PostgreSQL con
`drp_owner/drp_owner` —superusuario— y a un SMTP que acepta cualquier auth.
Aunque sea el compose de desarrollo, no cuesta nada cerrarlo. *Docker Security.*
**Acción:** prefijar loopback (`"127.0.0.1:5432:5432"`, etc.).

---

## Hallazgos BAJA e INFO (código actual)

Endurecimiento recomendado; ninguno bloquea el despliegue por sí solo.

- **[BAJA] Carrera en la rotación del refresh token.** `findByTokenHash` se lee
  fuera de la transacción y la revocación es read-modify-write sin `FOR UPDATE`
  (`Authentication.kt:109`, `RepositoryAdapters.kt:407-411`): dos peticiones
  concurrentes con el mismo refresh pueden obtener dos pares válidos. El frontend
  serializa sus renovaciones para esquivarlo, lo que confirma que la garantía no
  la da el servidor. **Acción:** `UPDATE … WHERE id = :id AND revoked_at IS NULL`
  comprobando filas afectadas, dentro de la transacción.
- **[BAJA] Lista de contraseñas comunes simbólica** (~40 entradas útiles,
  `common-passwords.txt`). El diseño de política sigue OWASP, pero 40 entradas
  filtran poco. **Acción:** empaquetar un top-10k/100k filtrado a ≥12 caracteres
  (sigue siendo offline).
- **[BAJA] Sin longitud máxima de contraseña** (`@Size(min = 12)` sin `max`). El
  endpoint autenticado de cambio no pasa por el límite de 64 KiB, así que
  contraseñas de megabytes llegan a Argon2id. **Acción:** `@Size(min = 12, max =
  128)`.
- **[BAJA] Tope de página no aplicado por el servidor.** El contrato declara
  `maximum: 200` pero `Pagination` no valida y los controladores pasan `size`
  crudo: `size=2000000000` devuelve el hogar entero (acotado por RLS, pero
  incumple el contrato). **Acción:** acotar en el constructor de `Pagination`.
- **[BAJA] Campos de texto libre sin longitud máxima** ni en DTO ni en BD
  (`notes`, `serial_number`, `description`, nombre del hogar). La cuota de 1 GB
  solo cubre ficheros; un cliente autenticado puede almacenar megabytes por
  campo. **Acción:** `@Size` coherente con el contrato + `CHECK` de longitud.
- **[BAJA] Nombres a `Subject` SMTP sin tope ni control de caracteres.**
  `EnrollmentEmails.kt:28,69` interpola `$householdName`/`$assetName` en el
  asunto, y `HouseholdInput.name` solo lleva `@NotBlank`. *Verificado:* el correo
  se arma con `SimpleMailMessage`/Jakarta Mail (`SmtpEmailSender.kt:45-49`), que
  codifica el asunto y neutraliza en gran parte la inyección de cabecera CR/LF;
  el hueco real es la **ausencia de tope de longitud y de validación de
  caracteres de control** en el nombre del hogar. El cuerpo es texto plano y los
  enlaces van con `URLEncoder`, así que ahí no hay riesgo. **Acción:** validación
  compartida que rechace caracteres de control + `@Size` en el nombre del hogar.
- **[BAJA] Tokens de un solo uso en la URL.** Patrón estándar para
  verificación/reset/invitación (y la pantalla hace `navigate` con `replace`),
  pero el del préstamo dura 90 días, el correo pide guardar el enlace y la página
  lo mantiene en la URL (`routes/loans.tsx`): queda en historial y logs
  intermedios. Es un capability-link deliberado y revocable. **Acción:**
  documentar el riesgo residual y valorar `history.replaceState` tras la carga.
- **[BAJA] Las tablas de tokens crecen sin purga.** No hay borrado de
  `refresh_tokens`, `password_reset_tokens` ni `email_verification_tokens`
  caducados/revocados; con rotación en cada refresh, `refresh_tokens` crece con el
  uso. Impacto de disponibilidad a largo plazo, no de confidencialidad (solo
  hashes). **Acción:** retención en la pasada diaria, conforme al criterio de
  [`capacity-measurements.md`](../../backend/operations/capacity-measurements.md).
- **[BAJA] `Cache-Control` ausente en la descarga autorizada de documentos**
  (`FileController.kt:150-160`): cacheo heurístico en disco del navegador de
  facturas con datos personales. **Acción:** `Cache-Control: private, no-store`.
- **[BAJA] Temporales `.staging` huérfanos tras un crash** no se limpian nunca
  (`PurgeUnusedFiles` los excluye). **Acción:** borrar de `.staging` lo más viejo
  de una hora en el arranque o la pasada diaria.
- **[BAJA] Saneado de nombre original incompleto** (`Files.kt:235-241`): pasan
  DEL (0x7F), los controles C1 y los overrides bidi (U+202E permite mostrar
  «gpj.exe» como imagen). Acotado porque el nombre nunca toca la ruta. **Acción:**
  excluir 0x7F–0x9F y los controles bidi.
- **[BAJA] Eventos de seguridad no registrados** (login fallido, 429, 403 no
  emiten log). Con el limitador en memoria, un ataque de enumeración o fuerza
  bruta es invisible a posteriori. **Acción:** `log.warn` con datos no sensibles
  (ruta, IP efectiva, correo enmascarado; nunca contraseña ni token).
- **[BAJA] Correo completo en el log de error de entrega** (`SmtpEmailSender.kt:64`):
  PII en un log sin retención declarada. **Acción:** enmascarar el local-part.
- **[BAJA] BouncyCastle `bcprov-jdk18on:1.80`** en rango de CVE-2025-8885/8916
  (DoS parseando ASN.1), pero el vector no está expuesto: BC solo respalda Argon2
  y el código no parsea ASN.1 de entrada no fiable. **Acción:** subir a 1.80.2+
  en la misma pasada de dependencias. **Corregido** (1.85.2) junto al peldaño de
  Spring Boot.
- **[BAJA] Cambio de rol o expulsión con hasta 15 min de retardo.** El rol viaja
  en el JWT y el filtro no consulta la BD; `ChangeUserRole` no revoca y
  `DeactivateUser` solo revoca refresh. Compromiso consciente y documentado,
  acotado por el TTL de 15 min. **Acción:** solo si se alarga el TTL, comprobar la
  pertenencia viva en las operaciones de gobernanza.
- **[BAJA] Imágenes Docker sin fijar del todo** (`mailpit:latest`, ramas alpine
  sin digest). **Acción:** versión exacta + digest para producción.
- **[BAJA] nginx sin `server_tokens off` ni `limit_req`.** **Acción:**
  `server_tokens off` en la plantilla; el `limit_req` sobre `/api/v1/auth/*` es
  parte del diseño de producción.
- **[BAJA] CI sin `permissions`, con acciones por tag mayor y `npx --yes` sin
  versión.** `ci.yml` no declara `permissions:` (el `GITHUB_TOKEN` hereda el
  default, que puede ser de escritura); las acciones van por `@v7`/`@v5`/`@v6`
  (tags mutables); `npx --yes @stoplight/spectral-cli` (línea 60) ejecuta la
  última versión publicada sin fijar. **Acción:** `permissions: contents: read`,
  fijar acciones por SHA, fijar la versión de spectral.
- **[INFO] Swagger UI y OpenAPI `permitAll` en todos los perfiles**
  (`SecurityConfig.kt:117`). El contrato es público en el repo, pero es superficie
  innecesaria en producción. **Acción:** `springdoc.*.enabled=false` por perfil.
- **[INFO] JWT sin `iss`/`aud`/`typ` y sin fijar el algoritmo en la verificación.**
  Riesgo real neutralizado (`SignedJWT.parse` rechaza `alg=none`, `MACVerifier`
  solo acepta HMAC, y la confusión access↔loan está cerrada en los dos sentidos).
  Recomendación de defensa en profundidad. **Acción:** fijar el algoritmo
  esperado y un claim de tipo/audiencia.
- **[INFO] Sin bloqueo de cuenta ni MFA**; solo rate limit por IP en el login. La
  exclusión del cubo por email en el login es correcta (evita un DoS de cuenta
  ajena). Aceptable para el perfil de la aplicación, pero debe ser una decisión
  registrada.
- **[INFO] Firma de ficheros MD5 con secreto en sufijo**, no HMAC-SHA256.
  Desviación consciente y bien razonada (el módulo `secure_link` de nginx solo
  sabe MD5; el secreto al final anula la extensión de longitud), anotada en la
  4.1.7. La documentación de arquitectura aún dice `s=<HMAC>` sin la nota.
  **Acción:** alinear el documento; migración limpia futura con njs + HMAC.
- **[INFO] PDF políglota / JS embebido.** Un PDF válido puede llevar un ZIP
  anexado o JS embebido; compensado por la entrega (adjunto, otro origen, CSP
  sandbox, `noexec`). Riesgo residual asumible, ya reconocido en el código.
- **[INFO] Credenciales de BD sin patrón `${VAR:default}`** (`application.yml:8-10,22-23`):
  el despliegue tendrá que sobreescribir cuatro propiedades en lugar de exportar
  variables ya previstas.

---

## Requisitos del despliegue (bloque de trabajo aparte)

El despliegue en la VPS **no existe todavía** y es su propio bloque. Estos son sus
requisitos de seguridad imprescindibles, cada uno con su cheat sheet. Ninguno es
un defecto del código: es lo que hay que construir para que la solidez del código
llegue a producción.

1. **TLS/HTTPS** (REST Security): certbot/ACME con redirección 80→443, HSTS, y el
   **segundo dominio de ficheros** también bajo TLS. Configurar
   `server.forward-headers-strategy` en el backend (sin ello Spring no sabe que
   está tras un proxy TLS y HSTS no se emite).
   - **Y declarar `DRP_TRUSTED_PROXIES`** con la IP o el rango del nginx que
     tenga delante: el limitador ya sabe recuperar la IP real del cliente, pero
     por omisión no se fía de nadie, así que sin esta variable vuelve a contar a
     todo el mundo en el mismo cubo. Es la variable que más fácil es olvidar,
     porque nada falla al arrancar sin ella.
2. **Compose de producción endurecido** (Docker / IaC Security): red interna sin
   publicar PostgreSQL ni SMTP; solo 443 expuesto; `restart: unless-stopped`;
   contenedores `user:` no root, `read_only` + `tmpfs`, `cap_drop: [ALL]`,
   `no-new-privileges`; secretos por fichero/secret y no `environment:` en claro;
   imágenes por digest; la aplicación **empaquetada** (jar bajo systemd o
   contenedor propio — hoy el compose asume `bootRun` en el host).
3. **Firewall + fail2ban** (ufw/OVH): denegar todo salvo 22/443; SSH solo con
   clave; fail2ban para sshd (el rate limit de la aplicación ya cubre su login,
   una vez arreglado el hallazgo 1).
4. **Backups automatizados** (Database Security): el diseño existe y es correcto
   ([`storage-sizing-and-backups.md`](../../backend/operations/storage-sizing-and-backups.md):
   orden volcado→ficheros, purga > ventana de copia), pero falta el script, el
   cron y la **restauración ensayada**. `pg_dump` diario + copia del árbol de
   ficheros fuera del VPS, cifrado, con retención.
5. **Endurecer el rol de BD** (Database Security): contraseñas fuertes y distintas
   por rol inyectadas como secreto; `sslmode=verify-full` si la BD no comparte red
   privada con la app; y bajar `drp_owner` de superusuario a rol no-superusuario
   dueño del esquema — comprobando que las funciones `SECURITY DEFINER` de
   `drp_resolver` siguen funcionando (romperían **en silencio**, como avisa
   `01-app-role.sql`).
6. **Montaje del volumen de ficheros** con `noexec,nodev,nosuid`, que
   `application.yml:147-149` ya prescribe y nadie ejecuta aún.
7. **Actualizaciones automáticas** del SO (`unattended-upgrades`).
8. **Monitorización y salud**: no hay endpoint de health (la dependencia de
   actuator no está); añadir `/actuator/health` expuesto solo a la red interna,
   logs con retención, y la métrica de ocupación del volumen que
   `storage-sizing-and-backups.md` ya declara obligatoria.
9. **Apagar Swagger/OpenAPI por perfil** en producción.

---

## Cumple (fortalezas verificadas)

Lo que la auditoría comprobó como correcto, para que no se toque sin querer:

- **Aislamiento en dos capas, con rigor y con pruebas.** 31 tablas con RLS
  `ENABLE`+`FORCE` y política que **falla cerrada**; usuario de aplicación
  `NOSUPERUSER NOBYPASSRLS` fijado por prueba; funciones de resolución de inquilino
  `SECURITY DEFINER` con `search_path`, `REVOKE FROM PUBLIC` y grant exclusivo;
  `TenantAwareTransactionManager` que fija `app.household_id` en **toda**
  transacción con `set_config` parametrizado y falla ruidosamente si no puede;
  `ThreadLocal` restaurado en `finally`. Barrido completo del contrato en
  `TenantIsolationSweepTest`, organizado por forma de ataque.
- **IDOR cerrado por diseño.** Ningún controlador ni caso de uso acepta
  `householdId` del cliente; las referencias del cuerpo están cerradas por claves
  ajenas compuestas `(household_id, id)`; la resolución por identificador directo
  da cero filas fuera del hogar.
- **Autorización por rol** con `@PreAuthorize("hasRole('HOUSEHOLD_ADMIN')")` en
  las nueve operaciones de gobernanza, la regla del «último administrador» en los
  tres sitios, y sin superficie de mass assignment (`status`/`role`/`createdBy`
  fuera del alcance del cliente).
- **Contraseñas y tokens.** Argon2id 19 MiB / t=2 / p=1 con
  `DelegatingPasswordEncoder`; refresh tokens de 256 bits de `SecureRandom`
  persistidos solo como SHA-256, con rotación y revocación en logout/reset/cambio;
  tokens de un solo uso hasheados, con caducidad y comparación en tiempo constante;
  login sin enumeración con hash de descarte para igualar el tiempo.
- **Ficheros.** Lista blanca por magic bytes (SVG/HTML imposibles por omisión);
  nombre en disco UUID sin extensión; recodificación de imágenes sin metadatos
  (EXIF/GPS no llegan al escritor); 50 Mpx comprobados antes de decodificar; cuota
  reservada con `FOR UPDATE` **antes** de transferir; traversal cerrado por doble
  valla; firma con caducidad dentro de lo firmado y comparación en tiempo
  constante; entrega con `attachment`, `nosniff`, CSP `sandbox` y dominio separado.
- **Inyección y salida.** SQL enteramente parametrizado (incluido el `SET` del
  tenant); ningún `ORDER BY` con datos del cliente; React sin
  `dangerouslySetInnerHTML`/`eval`; los mensajes del servidor nunca se pintan (el
  cliente traduce `code` a textos fijos); ADR-015 (icono/color) con enums cerrados;
  correo en texto plano con enlaces `URLEncoder` sobre base-url fija (sin host
  header injection); sin vectores SSRF.
- **Errores.** Forma única `ErrorResponse` sin trazas ni SQL; 404 idéntico para
  «no existe» y «no es tuyo»; 401/403 bien separados; sin `include-stacktrace`.
- **CSRF** deshabilitado con justificación válida (API sin cookies, token solo en
  cabecera); el filtro JWT limpia contexto y tenant en `finally`.
- **Frontend `npm audit`:** 0 vulnerabilidades; versiones al día (React 19.2.8,
  react-router 7.18.2, Vite 7.3.6). El tooling de desarrollo no llega al artefacto
  estático de producción.

## Referencias

- [OWASP Cheat Sheet Series](https://cheatsheetseries.owasp.org/)
- [`docs/backend/security/`](../../backend/security/README.md) — controles por
  diseño; en particular
  [`file-upload-controls.md`](../../backend/security/file-upload-controls.md).
- [ADR-003 — Row-Level Security](../architecture/decisions/ADR-003-row-level-security.md),
  [ADR-005 — Almacenamiento y entrega de ficheros](../architecture/decisions/ADR-005-local-file-storage.md),
  [ADR-012 — Supresión de datos](../architecture/decisions/ADR-012-data-erasure-household-closure-and-account-closure.md).
- [`decisions.md`](../product/decisions.md) — registro de decisiones (incluye el
  aplazamiento del antivirus).
- [`capacity-measurements.md`](../../backend/operations/capacity-measurements.md),
  [`storage-sizing-and-backups.md`](../../backend/operations/storage-sizing-and-backups.md).

## Historial de cambios

| Fecha | Cambio | Autor |
|---|---|---|
| 2026-08-20 | Creación: auditoría OWASP previa al despliegue en VPS, sobre las 106 operaciones y 31 tablas del cierre de huecos. Cinco superficies auditadas; sin vulnerabilidades críticas; cinco ALTA y catorce MEDIA en el código actual, más los requisitos del despliegue. | kikescribano |
| 2026-08-20 | **Corregidos los tres ALTA acotados**, que eran los tres «a medio cumplir»: el rate limit recupera la IP del cliente tras un proxy declarado de confianza (`drp.rate-limit.trusted-proxies`, leyendo la **última** entrada de `X-Forwarded-For`), la ausencia de perfil pasa a contar como producción —con `bootRun` declarando `dev` y la tarea de pruebas `test`— y el secreto de firma de ficheros se valida al arrancar igual que el del JWT. La tabla del resumen gana **fecha de detección y de corrección** por hallazgo, para que se pueda decir cuánto tiempo estuvo vivo cada uno y no solo si está cerrado. Quedan abiertos los dos ALTA de dependencias, que van en su propio bloque. | kikescribano |
| 2026-08-20 | **Hallazgo 5, cerrado en parte, y con una corrección del propio informe delante**: la recomendación original —«migrar a Spring Boot 3.5.x, es un salto menor»— **era falsa**, y se descubrió al ir a ejecutarla. La 3.5 también está fuera de soporte OSS desde el 2026-06-30 y la línea con soporte es la 4.x; el destino es un salto **mayor** que arrastra Jackson 3 con cambio de `groupId`, Kotlin 2.2+, Spring Security 7 y Hibernate 7. Se deja escrito cómo se detectó, porque el modo de fallo se repetirá: las páginas de fechas de fin de vida daban versiones inexistentes y el índice de búsqueda de Maven Central respondía con una versión atrasada; lo zanjó el `maven-metadata.xml` del repositorio. Entra el **peldaño 3.5.16** que la guía de Spring exige antes del salto y que **ya cierra todas las CVE concretas** del hallazgo —Spring Security 6.5.11, Framework 6.2.19, Tomcat 10.1.55 y nimbus-jose-jwt 9.37.4—, con springdoc 2.9.0 y BouncyCastle 1.85.2 detrás. Y entra la **vigilancia continua**, que era la otra mitad: `dependabot.yml` con los tres ecosistemas y actualizaciones agrupadas, más `permissions: contents: read` en la CI. | kikescribano |
