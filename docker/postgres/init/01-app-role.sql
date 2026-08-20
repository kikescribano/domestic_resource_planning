-- Usuario de la aplicacion, separado del propietario del esquema.
--
-- Toda la segunda capa de aislamiento entre hogares depende de dos condiciones
-- (ADR-003), y esta es una de ellas: el usuario con el que la aplicacion abre
-- sus conexiones NO puede ser superusuario ni tener BYPASSRLS. Si las tuviera,
-- las politicas de Row-Level Security seguirian existiendo y no filtrarian nada,
-- sin producir ningun error visible.
--
-- Se crea aqui, en la inicializacion del contenedor, para que ese limite este
-- puesto desde el primer arranque en lugar de depender de que alguien lo
-- recuerde al desplegar. La otra condicion --FORCE ROW LEVEL SECURITY-- viaja
-- en las migraciones de Flyway, junto a las politicas.
--
-- Este fichero es el canonico y el que ejecutan las pruebas (DrpPostgres). El
-- despliegue usa deploy/postgres/init/01-app-role.sh, que es esto mismo con
-- las contrasenas venidas del entorno: si se toca esto, hay que mirar aquel.

CREATE ROLE drp_app WITH
    LOGIN
    PASSWORD 'drp_app'
    NOSUPERUSER
    NOCREATEDB
    NOCREATEROLE
    NOBYPASSRLS
    NOINHERIT;

GRANT CONNECT ON DATABASE drp TO drp_app;
GRANT USAGE ON SCHEMA public TO drp_app;

-- La aplicacion lee y escribe filas; no crea ni altera objetos. Lo que Flyway
-- cree despues quedara accesible para drp_app por estos privilegios por defecto.
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO drp_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO drp_app;

-- Rol de resolucion de inquilino: el dueno de las tres funciones que responden
-- "de que hogar es esto" antes de que haya contexto (V4__tenant_resolution.sql).
--
-- Existe por un motivo concreto y poco evidente. Esas funciones son SECURITY
-- DEFINER, asi que se ejecutan con los privilegios de SU PROPIETARIO. La
-- tentacion es dejar que las posea el propietario del esquema y dar por hecho
-- que "el dueno de la tabla ve sus filas" -- pero eso es falso aqui, porque las
-- politicas llevan FORCE ROW LEVEL SECURITY, que las aplica TAMBIEN al
-- propietario de la tabla. Con ese montaje las funciones devolverian cero filas.
--
-- Hoy no lo hacen unicamente porque drp_owner es el usuario de arranque del
-- contenedor y por tanto superusuario, y un superusuario se salta RLS pase lo
-- que pase. Apoyarse en eso tiene dos problemas: la unica grieta deliberada del
-- aislamiento correria con privilegios de superusuario, y endurecer drp_owner
-- --que es hacia donde empuja la ADR-003-- romperia el recorrido de los procesos
-- diarios EN SILENCIO, devolviendo cero hogares sin ningun error.
--
-- Asi que la excepcion se expresa donde se puede auditar: un rol propio, sin
-- login, sin superusuario y SIN BYPASSRLS, al que las politicas le abren la
-- puerta de forma explicita y solo para SELECT, tabla por tabla. Ver
-- V5__tenant_resolution_role.sql.
CREATE ROLE drp_resolver WITH
    NOLOGIN
    NOSUPERUSER
    NOCREATEDB
    NOCREATEROLE
    NOBYPASSRLS
    NOINHERIT;
