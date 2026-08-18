---
name: run-local
description: Arranca y para DRP en local — PostgreSQL, Mailpit y nginx con compose, el backend de Spring Boot y el servidor de desarrollo de Vite — con el orden que hay que respetar y los escollos ya medidos. Úsala siempre que haya que levantar la aplicación, verla en el navegador, probar un cambio a mano, entrar con un usuario, dar de alta un hogar, leer un correo de verificación, o apagar y limpiar lo que se levantó. También cuando alguien pida «ejecuta el proyecto», «lánzalo en local», «quiero probarlo», «déjamelo corriendo» o «apaga lo que has arrancado», aunque no nombre Docker ni ningún servicio.
---

# Arrancar y parar DRP en local

Levantar DRP son **tres piezas y un orden**. El orden importa: saltárselo no da un
error claro sino un backend que arranca y se muere solo.

| Pieza | Qué es | Dónde escucha |
|---|---|---|
| `compose.yaml` | PostgreSQL, Mailpit y nginx | 5432, 1025/8025, 8090/8091 |
| Backend | Spring Boot, con Gradle | 8080 |
| Frontend | Vite en modo desarrollo | 5173 |

La aplicación se usa por **http://localhost:5173**, no por el 8080: el servidor de
desarrollo hace de proxy de `/api` hacia el backend, así que el frontend no tiene
que saber nada de CORS ni de dominios. nginx solo sirve para la entrega de
ficheros, y en desarrollo no hace falta pasar por él.

## Arranque

**1. Los contenedores, y esperar a que PostgreSQL esté sano.**

```bash
docker compose up -d && docker compose ps
```

No sigas hasta ver `healthy` en PostgreSQL. Tarda unos segundos y **ese es el
escollo que más cuesta**: si el backend arranca antes, no espera ni reintenta —
falla al abrir la conexión y el proceso muere. El síntoma no se parece a la causa,
porque el arranque parece haber ido bien y lo que se ve después es un puerto 8080
que no responde.

**2. El backend.**

```bash
node ./frontend/e2e/start-backend.mjs
```

Ese lanzador y no `./gradlew bootRun` a pelo: resuelve el wrapper que toca en cada
plataforma con rutas absolutas —`cmd` no entiende `./gradlew` y `sh` no ejecuta
`gradlew.bat`— y es el mismo que usa Playwright, así que si se rompe, se rompe
para los dos y no solo aquí.

Está vivo cuando el endpoint autenticado contesta **401**, que es la respuesta
correcta sin token. Esperar un 200 exigiría inventarse una sesión:

```bash
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/v1/loans
```

**3. El frontend.**

```bash
npm --prefix frontend run dev
```

Si arrancas desde un agente con herramientas de previsualización, hay un
[`.claude/launch.json`](../../launch.json) con las dos configuraciones —
`drp-backend` y `drp-frontend`— para no repetir estos comandos a mano.

## Entrar: no hay ningún usuario sembrado

Un arranque en limpio no tiene con qué iniciar sesión. Se da de alta un hogar en
`/crear-hogar` y **el hogar no sirve hasta verificar el correo**, que no sale a
Internet: lo recoge Mailpit.

```bash
python .claude/skills/run-local/scripts/mailpit-link.py alguien@casa.test
```

Imprime el enlace del último correo dirigido a esa dirección. Vale para los cuatro
que manda el producto: verificación, invitación, restablecimiento de contraseña y
la vista externa de un préstamo. La interfaz de Mailpit está en
**http://localhost:8025** si prefieres mirarlo a ojo.

Con el hogar verificado ya se navega con normalidad, y las cinco categorías por
defecto están sembradas.

## Parada, que es parte del trabajo

El `CLAUDE.md` lo pide explícitamente: **lo que se levanta para comprobar algo se
apaga en cuanto la comprobación está hecha.** Si el arranque fue para que una
persona pruebe a mano, déjalo vivo y dile cómo apagarlo; si fue para verificar un
cambio tú, apágalo al terminar.

```bash
docker compose down && rm -rf .data
```

`.data/` es el volcado de PostgreSQL que crea el arranque: se regenera solo, y
borrarlo es lo que deja la base en limpio para la próxima. Los dos servidores de
desarrollo se paran con la herramienta que los arrancó, o cerrando su proceso.

Y comprueba que no queda nada vivo, que es donde se acumula la basura en silencio:

```bash
docker ps && docker compose ls
```

## Escollos ya medidos

- **Otro worktree con el compose levantado se lleva los puertos.** El síntoma es
  `Bind for 0.0.0.0:1025 failed: port is already allocated` al arrancar Mailpit, y
  lo peligroso es lo que pasa después: el backend encuentra **el PostgreSQL del
  otro proyecto** y escribe ahí. Mira `docker ps` antes de arrancar; si hay un
  `*-postgres-1` de otro directorio, o lo paras o pruebas contra ese sabiendo que
  compartes datos.
- **Gradle deja daemon vivo unas tres horas.** Bien si vas a construir en bucle;
  estorba en una ejecución puntual, y por eso las comprobaciones de la CI usan
  `--no-daemon`. Ojo al limpiar: `--stop` solo alcanza a los daemons de **su misma
  versión**, y el servidor de la extensión Gradle de VS Code (`vscode-gradle`,
  reconocible por su `--parentPid`) es del editor, no del build.
- **Las pruebas no necesitan nada de esto.** Las de integración levantan su propio
  PostgreSQL con Testcontainers y lo destruyen al terminar; el recorrido vertical
  de Playwright arranca por su cuenta el backend y Vite, y lo único que le hace
  falta de aquí es **PostgreSQL y Mailpit**:

  ```bash
  docker compose up -d postgres mailpit && npm --prefix frontend run test:e2e
  ```

- **El `Enter` no siempre envía un formulario** cuando se dirige el navegador con
  herramientas de automatización: el evento sintético no dispara el envío
  implícito. **Comprobado en los dos sentidos**: a mano, en un navegador de
  verdad, el mismo formulario sí se envía con `Enter`. Así que cuando no funcione,
  pulsa el botón y sigue; apuntarlo como defecto de la aplicación sin repetirlo
  fuera de la automatización es acusar al producto de un fallo de la herramienta.

## Comprobar que de verdad funciona, no solo que arrancó

Un puerto que responde solo demuestra que el proceso está en pie. Para saber que
la aplicación funciona hay que atravesarla: dar de alta un hogar, verificarlo con
el correo, crear una ubicación, dar de alta algo en el inventario y mirar que la
ficha lo pinta. Es el mismo recorrido que hace la prueba de punta a punta, y
hacerlo a mano una vez es lo que distingue «arranca» de «funciona».
