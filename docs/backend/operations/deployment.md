# Despliegue de producción

| Campo | Valor |
|---|---|
| Estado | Vigente |
| Responsable | Equipo DRP |
| Ámbito | El VPS de producción: qué hay en él, cómo se despliega una versión nueva y cómo se restaura una copia |
| Última revisión | 2026-08-21 |

La decisión —por qué un VPS con compose, por qué GHCR, por qué Gmail, por qué
el 8081— está en la
[ADR-016](../../common/architecture/decisions/ADR-016-production-deployment.md).
Este documento es el manual de operación: los comandos, en el orden en que se
ejecutan.

DRP está en `http://vps-7f6cfe1b.vps.ovh.net/` — **HTTP plano, sin dominio ni
TLS todavía**; el porqué y el hasta cuándo, al final.

## Qué hay en el VPS

| | |
|---|---|
| Máquina | VPS de OVH (Ubuntu 24.04, 4 vCores, 8 GB, 72 GB), zona `Europe/Madrid` |
| Acceso | `ssh drp-vps` (alias local: `ubuntu@51.255.36.93`, puerto 55222, solo clave) |
| Endurecimiento | UFW con solo 55222/80/443, fail2ban, unattended-upgrades |
| Docker | Docker CE y Compose, con `ubuntu` en el grupo `docker` |
| El repositorio | Clonado en `/opt/drp`; el despliegue corre desde `/opt/drp/deploy` |
| Secretos | `/opt/drp/deploy/.env`, modo 600, **nunca en el repositorio** |
| Datos | `/opt/drp/deploy/data/postgres` (base de datos) y `data/files` (ficheros) — ignorados por git |
| Backup | Copia diaria del VPS entero, de OVH, incluida en el contrato |

Los tres contenedores y sus puertos:

| Servicio | Imagen | Puertos publicados |
|---|---|---|
| `postgres` | `postgres:16-alpine` | ninguno (red interna) |
| `backend` | `ghcr.io/kikescribano/drp-backend` | ninguno (red interna) |
| `web` | `ghcr.io/kikescribano/drp-web` | **80** (aplicación) y **8081** (ficheros, ADR-005) |

Dos cosas de los puertos que conviene no olvidar: **los que publica Docker
puentean UFW** —la lista de `ports:` del compose es el cortafuegos efectivo— y
el 8081 no es un descuido sino el **otro origen** desde el que se sirven los
ficheros con URL firmada.

El programador de la pasada diaria y el relay del outbox van **encendidos**
(su valor por omisión), con una sola instancia del backend: es la premisa de la
[ADR-011](../../common/architecture/decisions/ADR-011-scheduled-checks-and-notice-delivery.md)
y de [`scheduled-jobs.md`](scheduled-jobs.md).

## El `.env`

Las variables, con su ejemplo y sus notas, están en
[`deploy/.env.example`](../../../deploy/.env.example). Tres avisos que el
ejemplo también da pero cuestan caros si se pasan por alto:

- **`DRP_FILES_LINK_SECRET` lo comparten backend y nginx.** Si divergen, el
  síntoma es que **todas** las imágenes dan 403 y ninguna otra cosa falla.
- **Las contraseñas de BD solo se aplican en el primer arranque** (el script de
  init corre con el volumen de datos vacío). Cambiarlas después es un
  `ALTER ROLE ... PASSWORD` a mano además de editar el `.env`.
- **`DRP_MAX_HOUSEHOLDS` es el tope de hogares de esta fase** (decisión de la
  ADR-016; la holgura del VPS está medida en
  [`capacity-measurements.md`](capacity-measurements.md)) y su valor real vive
  solo aquí. El hogar de demostración cuenta como uno.

La contraseña del correo es una **contraseña de aplicación** de la cuenta de
Gmail dedicada (requiere verificación en dos pasos). Rotarla es generar otra en
`https://myaccount.google.com/apppasswords`, cambiar `DRP_SMTP_PASSWORD` y
`docker compose up -d backend`.

## Desplegar una versión nueva

Las imágenes las publica la CI en cada push a `main`
([`deploy.yml`](../../../.github/workflows/deploy.yml)), así que **lo primero
es que el cambio esté fusionado**. Después, en el VPS:

```bash
ssh drp-vps
cd /opt/drp && git pull --ff-only        # compose, plantillas y scripts al día
cd deploy
docker compose pull
docker compose up -d
```

Flyway migra solo al arrancar el backend; no hay paso de migración aparte. Un
despliegue corta el servicio los segundos que tarda el backend en levantar —
con diez hogares no hay ventana que negociar.

Para comprobar que lo desplegado es lo publicado:

```bash
docker compose ps && docker compose logs backend --since 5m
```

## Fijar o volver a una versión

Cada commit de `main` deja sus imágenes en GHCR etiquetadas con su SHA. Volver
atrás es fijarlo en el `.env` y repetir el pull:

```bash
sed -i 's/^DRP_VERSION=.*/DRP_VERSION=<sha-del-commit>/' .env
docker compose pull && docker compose up -d
```

`DRP_VERSION=latest` vuelve al último publicado. **Esto no deshace
migraciones**: volver a una versión anterior al esquema vigente solo es seguro
si la migración de por medio era compatible hacia atrás; si no, lo que toca es
restaurar la copia.

## Restaurar una copia

La copia diaria de OVH es **del VPS entero**, así que base de datos y ficheros
viajan juntos y del mismo instante — que es exactamente el requisito de
coherencia de
[`storage-sizing-and-backups.md`](storage-sizing-and-backups.md): ninguna fila
de `files` puede quedar apuntando a bytes que no están. Restaurar desde el
panel de OVH (o montando el backup como disco y copiando `deploy/data/`) y
después:

```bash
cd /opt/drp/deploy && docker compose up -d
```

Para una copia lógica puntual antes de una operación delicada:

```bash
docker compose exec postgres pg_dump -U drp_owner -Fc drp > drp-$(date +%F).dump
```

## La primera puesta en marcha (histórico, y arranque en frío)

Queda escrita por si hay que repetirla sobre una máquina nueva:

1. Clonar el repositorio en `/opt/drp` y crear el `.env` desde la plantilla
   (`cp .env.example .env && chmod 600 .env`), generando los secretos con
   `openssl rand -base64 48`.
2. `mkdir -p data/postgres data/files` — el de ficheros lo escribe el backend
   con uid 1000, que es `ubuntu` en el anfitrión.
3. La primera vez, o si GHCR no está accesible: `docker compose build` (el
   compose lleva las secciones `build:` exactamente para esto). El camino
   normal es `docker compose pull`.
4. `docker compose up -d`. El primer arranque de `postgres` ejecuta
   `postgres/init/01-app-role.sh` —los roles de la ADR-003, con las
   contraseñas del `.env`— y el backend migra con Flyway al levantar.
5. **Los paquetes de GHCR nacen privados** aunque el repositorio sea público:
   la primera publicación exige hacerlos públicos una vez, a mano, en los
   ajustes del paquete en GitHub, o el `pull` anónimo del VPS responderá 401.

## El juego de demostración en producción

El hogar Serrano está cargado en la instancia real —decisión de la ADR-016—
con el mismo script idempotente de siempre, que borra y reconstruye **solo su
hogar**. Necesita la base ya migrada (el backend arrancado al menos una vez) y
se ejecuta desde el checkout del VPS:

```bash
cd /opt/drp && docker compose --project-directory deploy exec -T \
  -e PGPASSWORD="$(grep ^DRP_DB_APP_PASSWORD deploy/.env | cut -d= -f2)" \
  postgres psql -U drp_app -d drp -v ON_ERROR_STOP=1 < scripts/seed-demo-data.sql
```

Cuentas y contenido, en [`demo-dataset.md`](demo-dataset.md). **La contraseña
de producción no es la del repositorio**: el seed deja la pública
(`DemoDRP2026Local`) y por eso cada carga termina ejecutando

```bash
cd /opt/drp/deploy && ./change-demo-password.sh
```

que la cambia por la de `DRP_DEMO_PASSWORD` del `.env` —solo del servidor— a
través de la propia aplicación (login + `ChangePassword`, con el Argon2id de
siempre). Es idempotente, así que relanzarlo sobre una demo ya cambiada no
toca nada. **Los dos pasos van juntos**: una carga del seed sin este script
deja el hogar Serrano abierto a cualquiera que lea el código.

El otro coste de la demo en producción sigue asumido en la ADR-016: sus avisos
escriben a direcciones `.test`, así que el buzón de la cuenta de Gmail recibe
sus rebotes; una regla de filtrado (`mailer-daemon` + `hogar-serrano.test`)
los archiva.

## Qué mirar cuando algo falla

| Síntoma | Causa más probable | Dónde mirar |
|---|---|---|
| Todas las imágenes dan 403 | Los dos `DRP_FILES_LINK_SECRET` no coinciden | `.env` frente al entorno del contenedor `web` |
| `pull` responde 401 | El paquete de GHCR sigue privado | Ajustes del paquete en GitHub |
| No llega ningún correo | SMTP: contraseña de aplicación revocada o cuenta bloqueada | `docker compose logs backend \| grep -i mail` — el envío registra el error y **no** falla la petición (ADR-009) |
| El backend no arranca y habla de secretos | Un secreto del `.env` vacío o igual al del repositorio | La validación de `SecurityConfig` es deliberada: poner un secreto real |
| El alta responde 409 | El tope de hogares está alcanzado | `HOUSEHOLD_LIMIT_REACHED` es comportamiento, no error: subir `DRP_MAX_HOUSEHOLDS` o dar de baja un hogar |
| La pasada diaria no deja rastro | El programador apagado o dos instancias | [`scheduled-jobs.md`](scheduled-jobs.md) |

## Lo que queda abierto a propósito

- **TLS, dominio y DNS.** Hoy no rompen nada —la auditoría de contexto seguro
  del frontend salió limpia, ADR-016— y son la condición para el día del
  escáner de códigos de barras o del primer hogar ajeno. Con ellos, los
  ficheros pasarán del puerto 8081 a un subdominio.
- **El análisis antivirus de lo subido**, abierto desde la Fase 1 y con su
  motivo intacto: es la defensa de cuando un fichero pueda salir del hogar que
  lo subió.
- **El firewall de red de OVH** (panel), anotado como opción: duplicaría en un
  segundo sitio la lista de puertos que hoy gobierna el compose.
- **El despliegue continuo (CD)**, anotado como deuda el 2026-08-21. Hoy
  desplegar es deliberadamente manual (ADR-016): fusionar publica las imágenes
  y el `pull` lo ejecuta alguien. El día que la deriva entre `main` y
  producción pese más que ese control, **la forma ya está elegida** y es un job
  final en el propio workflow de imágenes: entra al VPS por SSH con una clave
  dedicada **restringida con `command=` forzado** en el `authorized_keys` —esa
  clave solo puede ejecutar el script de despliegue, ni shell ni ninguna otra
  cosa, que es lo que contiene un secreto de repositorio comprometido—, con
  `concurrency` para que dos merges seguidos no se pisen, **absteniéndose
  cuando el `.env` fije un `DRP_VERSION` distinto de `latest`** —un anclaje de
  vuelta atrás no debe deshacerse solo— y saltando la ventana del barrido de
  las 03:15, para no reiniciar el backend a mitad de la pasada diaria. Se
  descartó el agente de sondeo en el VPS (watchtower o un timer): despliega
  cuando toca el sondeo y no cuando se fusiona, y no actualiza el checkout de
  `deploy/`, así que un cambio del compose o de la plantilla de nginx no
  llegaría. Ejecutarlo será su propio bloque pequeño, con la sección hacia
  adelante que la ADR-016 pide.
