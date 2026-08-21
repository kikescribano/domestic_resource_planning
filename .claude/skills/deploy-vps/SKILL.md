---
name: deploy-vps
description: Despliega, verifica, diagnostica y revierte DRP en el VPS de producción de OVH — el gesto de pull desde GHCR, las verificaciones que distinguen «arrancó» de «funciona» y los escollos ya pagados una vez. Úsala siempre que haya que desplegar una versión, saber qué hay en producción, mirar logs o contenedores del VPS, comprobar el barrido diario, recargar la demo de producción, revertir a una versión anterior, o diagnosticar cualquier síntoma de la instancia real. También cuando alguien diga «despliega», «sube la última versión», «producción está rara», «mira el servidor» o nombre el VPS, aunque no diga Docker ni GHCR.
---

# Desplegar DRP en el VPS de producción

**El manual canónico es
[`deployment.md`](../../../docs/backend/operations/deployment.md)** y la
decisión entera está en la ADR-016; si esta skill y aquel discrepan, gana el
manual. Lo que esta skill añade es lo operativo: el orden, las verificaciones
y los escollos que ya costaron un despliegue roto.

El mapa mínimo: `ssh drp-vps` (alias local ya configurado). El repo vive en
`/opt/drp` sobre la rama `main`; el compose, el `.env` (600, todos los
secretos) y `data/` en `/opt/drp/deploy`. Tres contenedores: `postgres` y
`backend` en red interna, `web` (nginx) publicando **80** (solo ACME y
redirección) y **443**, donde `https://drp.kikescribano.es` es la aplicación y
`https://files.drp.kikescribano.es` los ficheros firmados (el otro origen de
la ADR-005, por SNI). El certificado es de Let's Encrypt, uno con los dos
nombres, renovado por el certbot del anfitrión contra `deploy/data/acme` con
un gancho que recarga nginx. Las imágenes las publica la CI en GHCR en cada
push a `main`, con etiqueta `latest` y el SHA.

**Es producción: lo desplegado se queda corriendo.** La regla de apagar lo que
se arranca aplica a lo local, no al VPS.

## Antes de tocar el VPS: ¿existen las imágenes de la punta?

**Fusionar no despliega, y publicar tampoco es instantáneo.** El workflow tarda
unos minutos; desplegar antes de que acabe baja imágenes de la versión
anterior con cara de éxito. Comprueba que el último run de `deploy.yml` está
`success` **y sobre el mismo SHA** que la punta de `origin/main`:

```bash
gh run list --repo kikescribano/domestic_resource_planning --workflow deploy.yml --limit 1 --json status,conclusion,headSha
```

## El gesto

```bash
ssh drp-vps
cd /opt/drp && git pull --ff-only
git log --oneline -1        # ¿de verdad estamos en la punta?
cd deploy
docker compose pull
docker compose up -d
```

El `git log` intermedio no es ceremonia, es la lección más cara de la primera
vez: un `pull` abortado que pase inadvertido deja el `compose up` corriendo
**imágenes nuevas sobre compose y plantillas viejos**, una combinación que
nadie ha probado. El síntoma real de aquel día: nginx muriendo en bucle con
`unknown variable`, porque el compose antiguo no pasaba una variable que la
plantilla nueva necesitaba.

Flyway migra solo al arrancar el backend. El corte de servicio son los ~12 s
que tarda en levantar. Si puedes elegir el momento, evita las 03:15 de Madrid:
es la pasada diaria, y un reinicio a mitad deja hogares sin barrer hasta el
día siguiente.

## Verificar: «arrancó» no es «funciona»

Las cuatro comprobaciones, todas desde fuera y por código de salida:

```bash
ssh drp-vps 'cd /opt/drp/deploy && docker compose ps --format "{{.Name}} {{.Status}}"'
```

Nada en `Restarting`. Después, el backend arrancó limpio:

```bash
ssh drp-vps 'cd /opt/drp/deploy && docker compose logs backend --since 5m 2>&1 | grep -icE "error|fail"'
```

(`grep -c` sale con 1 cuando cuenta 0 — el número es la respuesta, no el
exit.) La SPA responde con sus cabeceras:

```bash
curl -s -D - -o /dev/null https://drp.kikescribano.es/ | grep -iE "^HTTP|content-security|strict-transport"
```

Y la aplicación se atraviesa: login con `lucia@hogar-serrano.test` y la
contraseña de producción de la demo — que es la de `DRP_DEMO_PASSWORD` en el
`.env` **del servidor** (`grep ^DRP_DEMO_PASSWORD /opt/drp/deploy/.env`), no
la pública del repositorio, que en producción da 401 a propósito.

## Revertir

`DRP_VERSION=<sha-de-main>` en el `.env` del VPS y `docker compose pull &&
docker compose up -d`. Vuelta a `latest` para el último. **No deshace
migraciones**: si la versión de por medio migró incompatible, lo que toca es
la copia diaria de OVH (procedimiento en el manual).

## Escollos ya pagados

- **Código de salida, no tubería — también dentro de un `ssh '...'`.** Un
  `git pull | tail` bajo `set -e` abortó en silencio (el exit era el de
  `tail`) y el despliegue siguió con el checkout viejo. Captura el exit del
  comando, no del envoltorio, y confirma la punta con `git log`.
- **El `pull` aborta por cambios locales.** El checkout del VPS es un espejo
  de `main`: nada local en él tiene valor. Remedio: `git fetch && git reset
  --hard origin/main` — seguro porque solo toca ficheros rastreados y el
  `.env` y `deploy/data/` están ignorados por git, así que sobreviven
  (comprobado).
- **Consultar la base a pelo devuelve cero filas, y es lo correcto.** RLS con
  `FORCE` niega todo a una sesión sin `app.household_id`. Para contar u operar
  fuera de un hogar está `SELECT * FROM list_household_ids()` como `drp_app`;
  para una lectura de administración puntual, `psql -U drp_owner` (superusuario
  del contenedor) — solo lectura de diagnóstico, nunca para escribir a mano.
- **Todas las imágenes dan 403** → los dos `DRP_FILES_LINK_SECRET` (backend y
  nginx) no coinciden. **`pull` da 401** → el paquete de GHCR volvió a privado.
  **Nginx en bucle con `unknown variable`** → compose o plantilla viejos con
  imagen nueva: el checkout no está en la punta.
- **El correo no falla peticiones**: el envío se traga los errores SMTP a
  propósito (ADR-009). Si no llega nada, `docker compose logs backend | grep -i
  mail`. Y los rebotes diarios de `@hogar-serrano.test` son normales — el
  resumen del hogar de demo escribiendo a direcciones que no existen.
- **La demo se recarga en dos pasos que van juntos**: el seed (idempotente,
  solo toca su hogar) y `./change-demo-password.sh`, que quita la contraseña
  pública del repo. El seed solo, en producción, deja el hogar Serrano abierto
  a cualquiera que lea el código. Comandos exactos, en el manual.
- **Arranque en frío o GHCR caído**: el compose lleva secciones `build:` — se
  construye en el VPS desde el checkout (`docker compose build`). Es la salida
  de emergencia, no el camino.

## Lo que no se hace

Ni `BYPASSRLS`, ni una segunda instancia del backend con el programador
encendido (ADR-011: la pasada diaria correría dos veces), ni secretos del
`.env` por el chat o el repositorio — se generan en el servidor
(`openssl rand`) o los coloca el usuario en persona. Y el certificado no se
gestiona a mano: lo renueva el timer de certbot; si el navegador avisa de
caducidad, `sudo certbot renew --dry-run` dice el motivo y el manual trae el
resto.
