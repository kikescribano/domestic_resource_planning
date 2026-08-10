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
