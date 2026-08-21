# ADR-016: Despliegue de producción en un VPS

- Estado: accepted
- Fecha: 2026-08-21
- Responsables: Equipo DRP
- Ámbito: common
- Sustituye: Ninguna

## Contexto

El despliegue llevaba abierto **a propósito** desde la
[ADR-001](ADR-001-solution-architecture-baseline.md), y era lo único que separaba
«En desarrollo» de «En producción»: al cerrar el bloque de huecos, el producto
estaba entero —107 operaciones, 31 tablas, once recorridos verticales— y no
corría en ningún sitio. Lo que sí estaba hecho era el trabajo previo que esta
decisión necesita para no ser una apuesta: el VPS elegido **con consumo medido**
([`capacity-measurements.md`](../../../backend/operations/capacity-measurements.md):
decide el disco, no la CPU), la
[auditoría OWASP](../../security/owasp-audit.md) con los requisitos del
despliegue listados como bloque propio, y un `compose.yaml` de desarrollo que ya
reproducía el reparto de usuarios de PostgreSQL y la entrega de ficheros por
nginx (ADR-003, ADR-005).

Esta ADR cierra el despliegue **en su forma mínima viable**: DRP corriendo en
producción en un VPS, **sin dominio y sin TLS todavía** — aplazados a
conciencia, no olvidados. Se accede por `http://vps-7f6cfe1b.vps.ovh.net/`.

Tres comprobaciones previas condicionaron lo que se decide, y las tres se
hicieron antes de decidir:

1. **Servir por HTTP plano no rompe nada del frontend actual.** La auditoría de
   contexto seguro no encontró ni una llamada a una API restringida a
   `https://`: no hay escáner de cámara —el código de barras es un campo de
   texto del contrato sin pantalla—, la conversión HEIC de la
   [ADR-014](ADR-014-heic-conversion.md) usa WebAssembly, un Worker y canvas
   —ninguno está restringido— y no hay service worker, portapapeles ni
   `crypto.randomUUID()`. La deuda es futura y está localizada: el día que el
   escáner exista, `navigator.mediaDevices` será `undefined` sobre HTTP y TLS
   pasará de pendiente a bloqueante.
2. **La configuración de producción no exige tocar código.** Lo que
   `application.yml` tiene sin placeholder —datasource, Flyway, SMTP,
   remitente, URL base de los correos— se sobrescribe igualmente por variables
   de entorno, porque en Spring Boot el entorno tiene precedencia sobre el
   YAML. Y la validación de secretos del arranque (`SecurityConfig`) considera
   producción la **ausencia** de perfil, así que el despliegue la mantiene viva
   sin declarar nada.
3. **Las migraciones presuponen los roles.** `V5` falla si `drp_resolver` no
   existe: el aprovisionamiento de `docker/postgres/init/01-app-role.sql` es un
   prerrequisito del primer arranque, no un detalle del compose de desarrollo.

## Decisión

### 1. Un VPS de OVH con Docker Compose, y un compose de producción propio

DRP corre en el VPS ya contratado (Ubuntu 24.04, 4 vCores, 8 GB, 72 GB;
endurecido: UFW con solo 55222/80/443, fail2ban, unattended-upgrades) con
**Docker Compose y tres servicios**: PostgreSQL 16, el backend y nginx con el
estático del frontend dentro. El fichero es
[`deploy/compose.yaml`](../../../../deploy/compose.yaml), **separado del
`compose.yaml` de desarrollo**: aquel levanta dependencias para trabajar; este
ejecuta la aplicación entera con `restart: unless-stopped`.

Se descartó todo lo demás por exceso: un orquestador o un PaaS resuelven
problemas —réplicas, autoscaling, flotas— que una instancia con tope de diez
hogares no tiene, y la premisa de instancia única de la
[ADR-011](ADR-011-scheduled-checks-and-notice-delivery.md) hace de la réplica
un peligro, no una mejora.

### 2. Solo nginx publica puertos, y los ficheros van por el 8081

PostgreSQL y el backend viven en la red interna de compose y **no publican
ningún puerto**. nginx publica dos: el **80** (aplicación: SPA + `/api` +
`X-Accel-Redirect`) y el **8081** (ficheros con URL firmada).

Dos cosas quedan escritas porque son fáciles de deshacer sin querer:

- **Los puertos publicados por Docker puentean UFW** — Docker escribe sus
  reglas de iptables por delante. La lista de `ports:` del compose **es** el
  cortafuegos efectivo de la aplicación, y por eso es tan corta.
- **El 8081 es la separación de origen de la ADR-005.** Sin dominio no hay
  subdominios, y para el navegador otro puerto es otro origen, que es lo que el
  aislamiento pide: un fichero que se cuele pese a todos los controles no
  comparte origen con la sesión ni con los datos. La alternativa —todo por el
  80, ficheros en una ruta— se descartó porque renuncia en producción a lo que
  la ADR-005 fija y las cabeceras solo mitigan.

### 3. Las imágenes las construye GitHub Actions y las publica GHCR

El workflow [`deploy.yml`](../../../../.github/workflows/deploy.yml) construye
las dos imágenes —`drp-backend` y `drp-web`, con Dockerfiles en `deploy/`— en
cada push a `main` y las publica en GHCR con dos etiquetas: `latest` y el SHA
del commit. **El VPS no compila nada**: desplegar es `docker compose pull &&
docker compose up -d`, y volver atrás es fijar `DRP_VERSION` a un SHA anterior.

Se descartó construir en el VPS como camino permanente —compilar en producción
en cada despliegue, con el código fuente y una JDK en el servidor como
requisito— aunque el compose conserva las secciones `build:` como arranque en
frío y salida de emergencia si GHCR no está accesible.

Dos consecuencias del empaquetado que no son evidentes: el contexto de
construcción del backend es **la raíz del repositorio** (el jar lleva dentro
`openapi.yaml`, ADR-007), y la imagen de ejecución es **glibc y no Alpine**,
porque `webp-imageio` no trae binarios musl y la decodificación WebP fallaría
ya desplegada.

### 4. Los secretos viven en el servidor, y solo allí

Un `.env` junto al compose (`chmod 600`), creado a partir de
[`deploy/.env.example`](../../../../deploy/.env.example) con valores generados
en el propio servidor. El repositorio no contiene ningún secreto ni ningún
valor de dimensionado real: **el tope de hogares incluido**, que es
`DRP_MAX_HOUSEHOLDS` en ese `.env` y en el código solo existe como «cero es sin
tope». Los roles de PostgreSQL se crean en el primer arranque con
[`deploy/postgres/init/01-app-role.sh`](../../../../deploy/postgres/init/01-app-role.sh)
—el espejo parametrizado del script canónico—, con lo que el invariante de la
ADR-003 llega entero: `drp_app` sin `BYPASSRLS`, Flyway como `drp_owner`.

### 5. El correo sale por una cuenta de Gmail dedicada

`smtp.gmail.com:587` con una cuenta creada para DRP y una **contraseña de
aplicación**. Es la pieza que la [ADR-009](ADR-009-outbound-email.md) dejó
preparada —sustituir Mailpit es configuración, no código— y la opción que
queda en pie cuando se miran las otras dos:

- **Un postfix propio** está descartado por partida doble: OVH bloquea el 25
  saliente por defecto, y sin dominio no hay SPF/DKIM que firmar, así que lo
  entregado acabaría en spam justo donde más duele — el correo de verificación
  sin el que un hogar no llega a existir.
- **Un proveedor transaccional** añade un alta y una dependencia para el mismo
  resultado: sin dominio propio, el remitente verificado sería igualmente una
  dirección de Gmail.

El volumen es minúsculo —con tope de diez hogares, el resumen diario son a lo
sumo diez correos— y queda dos órdenes de magnitud por debajo del límite diario
de Gmail. La cuenta dedicada, en lugar de la personal, separa remitente y
rebotes del buzón de una persona; los rebotes existirán a diario, porque el
hogar de demostración escribe a direcciones `.test` que no entrega nadie.

### 6. El programador encendido, y un tope de hogares como cota de dimensionado

En producción `drp.schedule.enabled` y `drp.outbox.enabled` quedan en su valor
por omisión —encendidos— con **una sola instancia** del backend (ADR-011). Y el
dimensionamiento de esta fase —**10 hogares** sobre el VPS medido— pasa de
supuesto a regla aplicada: `CreateHousehold` gana un tope configurable
(`drp.enrollment.max-households`, `DRP_MAX_HOUSEHOLDS`) que responde `409
HOUSEHOLD_LIMIT_REACHED` **antes de tocar nada y de forma idéntica exista o no
el correo**, para no reabrir por la puerta del tope el oráculo de direcciones
que el `202` constante cierra. Cero es sin tope; cuentan todos los hogares que
existen, y las dos purgas liberan hueco solas.

### 7. El juego de demostración se carga también en producción

Decisión de producto tomada al arrancar el bloque: el hogar Serrano
(`lucia@hogar-serrano.test` y compañía) está en la instancia real, cargado con
el mismo `scripts/seed-demo-data.sql` idempotente que en local, para poder
enseñar la aplicación con catorce meses de vida dentro.

**Con la contraseña del repositorio fuera.** La que el seed escribe es pública
—está en el código— y dejarla en producción sería una puerta abierta al hogar
Serrano para cualquiera que lo lea, así que cada carga termina con
`deploy/change-demo-password.sh`: cambia las cuatro cuentas a la contraseña de
`DRP_DEMO_PASSWORD`, que vive en el `.env` del servidor como cualquier otro
secreto, y lo hace **por la puerta de la aplicación** —login y
`ChangePassword`— para que la hashee el mismo Argon2id que las demás. Se
descartó editar el hash en la base o mantener un seed distinto para
producción: las dos cosas duplican lo que la aplicación ya sabe hacer.

El coste que sí se asume por escrito: sus avisos rebotan a diario contra el
buzón remitente (direcciones `.test`), archivados por una regla de filtrado. Y
el hogar de demo **cuenta** para el tope de hogares: ocupa disco y pasada
diaria como cualquier otro.

## Validación

La de esta ADR es el recorrido real en producción, sin dobles: alta de un hogar
desde `http://vps-7f6cfe1b.vps.ovh.net/`, correo de verificación recibido en un
buzón de verdad, login, una foto subida y descargada con la URL firmada del
8081, y el barrido diario dejando su rastro —tabla de avisos, resumen— en los
días siguientes. El tope de hogares llega además con su recorrido vertical de
pruebas en las tres capas, como cualquier regla del core.

## Reversión

Dos mandos, según lo que haya que deshacer:

- **La versión**: `DRP_VERSION=<sha>` en el `.env` y `docker compose pull &&
  docker compose up -d`. Las imágenes de cada commit de `main` quedan en GHCR.
- **Los datos**: el backup diario del VPS entero que OVH incluye en el
  contrato, que captura base de datos y ficheros **del mismo instante** — el
  orden de restauración que
  [`storage-sizing-and-backups.md`](../../../backend/operations/storage-sizing-and-backups.md)
  exige queda satisfecho por construcción. El procedimiento, en
  [`deployment.md`](../../../backend/operations/deployment.md).

## Lo que queda abierto a propósito

- **TLS, dominio y DNS.** Aplazados, no descartados: hoy no rompen nada
  (contexto seguro auditado) y contratarlos es una decisión de producto con
  coste recurrente. El día del escáner de códigos de barras, o del primer hogar
  que no sea de confianza, dejan de ser aplazables. Con ellos caerá también la
  separación de ficheros por puerto, que pasará a ser por subdominio.
- **El análisis antivirus de lo subido**, que viene listado desde la Fase 1 y
  sigue fuera por el mismo motivo: es la defensa del día en que un fichero
  pueda salir del hogar que lo subió, y ese día no ha llegado.
- **El firewall de red de OVH** (el del panel, delante de la máquina), que
  duplicaría la lista de puertos en un segundo sitio: se anota como opción, no
  como tarea.
- **El despliegue continuo** (añadido el 2026-08-21, el mismo día de aceptar
  esta ADR). Esta decisión fija que producción cambia cuando alguien lo decide,
  y esa frase es reversible a bajo coste: la forma preferida —un job de SSH en
  el propio workflow, con clave de mando forzado y sus dos salvaguardas— queda
  descrita en la lista de abiertos de
  [`deployment.md`](../../../backend/operations/deployment.md), y ejecutarla
  será un bloque propio que amplíe esta ADR hacia adelante.
