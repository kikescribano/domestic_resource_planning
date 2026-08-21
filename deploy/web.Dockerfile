# La imagen de nginx con el estatico del frontend dentro (ADR-016). Se
# construye desde la RAIZ del repositorio:
#
#   docker build -f deploy/web.Dockerfile .
#
# Es el unico servicio que publica puertos en produccion: sirve la SPA,
# proxya /api al backend y entrega los ficheros con X-Accel-Redirect y URL
# firmada, con la plantilla de deploy/nginx/templates.

# ---------------------------------------------------------------------------
# Construccion del estatico. `npm run build` incluye el tsc --noEmit, asi que
# un error de tipos corta la imagen aqui y no aparece como un bundle raro.
# ---------------------------------------------------------------------------
FROM node:22-alpine AS build
WORKDIR /src/frontend

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ ./
RUN npm run build

# ---------------------------------------------------------------------------
# Ejecucion: el mismo nginx que el compose de desarrollo, con su mecanismo de
# plantillas (${VARIABLE} se sustituye al arrancar desde el entorno).
# ---------------------------------------------------------------------------
FROM nginx:1.27-alpine

COPY deploy/nginx/templates /etc/nginx/templates
COPY --from=build /src/frontend/dist /srv/drp/web
