# La imagen del backend (ADR-016). Se construye desde la RAIZ del repositorio:
#
#   docker build -f deploy/backend.Dockerfile .
#
# El contexto tiene que ser la raiz y no backend/ porque `processResources`
# copia openapi.yaml desde el directorio padre del proyecto de Gradle
# (build.gradle.kts): el contrato es fuente de verdad (ADR-007) y viaja dentro
# del jar.

# ---------------------------------------------------------------------------
# Construccion: el jar ejecutable, sin pruebas.
#
# Las pruebas no se saltan por prisa: necesitan Docker (Testcontainers) y ya
# corrieron en su propio trabajo de la CI. Repetirlas dentro de un build de
# imagen seria Docker dentro de Docker para comprobar lo ya comprobado.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /src/backend

# Primero lo que casi nunca cambia, para que la descarga de Gradle y de las
# dependencias quede en una capa cacheada y un cambio de codigo no la repita.
COPY backend/gradlew ./
COPY backend/gradle ./gradle
COPY backend/settings.gradle.kts backend/build.gradle.kts ./
RUN ./gradlew --no-daemon dependencies --quiet > /dev/null 2>&1 || true

COPY openapi.yaml /src/openapi.yaml
COPY backend/src ./src
RUN ./gradlew --no-daemon bootJar -x test

# ---------------------------------------------------------------------------
# Ejecucion: JRE sobre glibc.
#
# **Jammy y no Alpine, y no es una preferencia**: webp-imageio 0.11.0 trae
# binarios nativos que no valen sobre musl (backend/build.gradle.kts), asi que
# sobre Alpine la decodificacion WebP fallaria en tiempo de ejecucion con la
# imagen ya desplegada.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-jammy

# Usuario propio, sin privilegios. El uid 1000 coincide con `ubuntu` en el VPS,
# que es lo que permite compartir el volumen de ficheros con el anfitrion sin
# pelearse con los permisos.
RUN useradd --uid 1000 --user-group --create-home --home-dir /srv/drp drp
USER drp
WORKDIR /srv/drp

# bootJar produce un unico jar; el -plain solo sale de la tarea `jar`, que
# aqui no se ejecuta.
COPY --from=build /src/backend/build/libs/*.jar /srv/drp/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/srv/drp/app.jar"]
