#!/bin/sh
# El aprovisionamiento de roles de produccion. Es docker/postgres/init/
# 01-app-role.sql con UNA diferencia: las contrasenas no van escritas sino que
# llegan del entorno (.env del servidor). El porque de cada rol esta comentado
# alli, que es el fichero canonico ademas de el que ejecutan las pruebas
# (DrpPostgres): si se toca aquel, hay que mirar este.
#
# Solo se ejecuta en el PRIMER arranque del contenedor, con el volumen de datos
# vacio; despues, cambiar una contrasena es un ALTER ROLE a mano.
set -e

psql -v ON_ERROR_STOP=1 \
     -v app_password="$DRP_DB_APP_PASSWORD" \
     --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'EOF'
-- Usuario de la aplicacion: NUNCA superusuario ni BYPASSRLS (ADR-003). Si los
-- tuviera, las politicas de Row-Level Security existirian y no filtrarian
-- nada, sin ningun error visible.
CREATE ROLE drp_app WITH
    LOGIN
    PASSWORD :'app_password'
    NOSUPERUSER
    NOCREATEDB
    NOCREATEROLE
    NOBYPASSRLS
    NOINHERIT;

GRANT CONNECT ON DATABASE drp TO drp_app;
GRANT USAGE ON SCHEMA public TO drp_app;

-- La aplicacion lee y escribe filas; no crea ni altera objetos. Lo que Flyway
-- cree despues queda accesible para drp_app por estos privilegios por defecto.
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO drp_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO drp_app;

-- Rol de resolucion de inquilino: dueno de las funciones SECURITY DEFINER que
-- responden "de que hogar es esto" antes de que haya contexto. Sin login y sin
-- BYPASSRLS; las politicas le abren la puerta de forma explicita y solo para
-- SELECT, tabla por tabla (V5__tenant_resolution_role.sql).
CREATE ROLE drp_resolver WITH
    NOLOGIN
    NOSUPERUSER
    NOCREATEDB
    NOCREATEROLE
    NOBYPASSRLS
    NOINHERIT;
EOF
