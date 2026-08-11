-- Segunda capa de aislamiento entre hogares (ADR-003).
--
-- La primera capa es la aplicacion: todo caso de uso y todo repositorio filtra
-- por el householdId del token. Esta es la red que hay debajo para cuando uno se
-- despiste. Se versiona como migracion igual que las tablas, porque una politica
-- forma parte del esquema tanto como una columna.
--
-- Dos condiciones invalidan la proteccion entera y no viven aqui:
--
--   1. El usuario con el que la aplicacion conecta no puede ser superusuario ni
--      tener BYPASSRLS. Eso se fija al crear el rol, en
--      docker/postgres/init/01-app-role.sql.
--   2. FORCE ROW LEVEL SECURITY, que si viaja en esta migracion: sin el, la
--      politica no se aplica al propietario de la tabla.
--
-- Por que la forma con nullif, y no current_setting('app.household_id')::uuid a
-- secas. Comprobado contra PostgreSQL 16 (2026-08-10): la version directa lanza
-- un error de conversion en dos situaciones normales --si el ajuste nunca se
-- fijo en la sesion, y despues de un RESET, que no lo deja sin valor sino en
-- cadena vacia, y ''::uuid no es convertible. El segundo argumento true de
-- current_setting evita lo primero devolviendo NULL, y el nullif convierte la
-- cadena vacia en NULL. En ambos casos la comparacion da NULL, la politica no
-- deja pasar ninguna fila y la peticion falla CERRADA. Nunca al reves: sin
-- contexto, cero filas.
--
-- Las politicas no llevan WITH CHECK propio a proposito: sin el, PostgreSQL
-- aplica la expresion de USING tambien a las filas nuevas, que es exactamente lo
-- que se quiere --no se puede leer ni escribir en un hogar que no sea el de la
-- sesion.

-- La tabla del hogar se aisla por su propia clave: es el hogar, no algo que
-- pertenezca a uno.
ALTER TABLE households ENABLE ROW LEVEL SECURITY;
ALTER TABLE households FORCE ROW LEVEL SECURITY;
CREATE POLICY households_household_isolation ON households
    USING (id = nullif(current_setting('app.household_id', true), '')::uuid);

ALTER TABLE household_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE household_members FORCE ROW LEVEL SECURITY;
CREATE POLICY household_members_household_isolation ON household_members
    USING (household_id = nullif(current_setting('app.household_id', true), '')::uuid);

ALTER TABLE invitations ENABLE ROW LEVEL SECURITY;
ALTER TABLE invitations FORCE ROW LEVEL SECURITY;
CREATE POLICY invitations_household_isolation ON invitations
    USING (household_id = nullif(current_setting('app.household_id', true), '')::uuid);

ALTER TABLE categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE categories FORCE ROW LEVEL SECURITY;
CREATE POLICY categories_household_isolation ON categories
    USING (household_id = nullif(current_setting('app.household_id', true), '')::uuid);

ALTER TABLE files ENABLE ROW LEVEL SECURITY;
ALTER TABLE files FORCE ROW LEVEL SECURITY;
CREATE POLICY files_household_isolation ON files
    USING (household_id = nullif(current_setting('app.household_id', true), '')::uuid);

ALTER TABLE articles ENABLE ROW LEVEL SECURITY;
ALTER TABLE articles FORCE ROW LEVEL SECURITY;
CREATE POLICY articles_household_isolation ON articles
    USING (household_id = nullif(current_setting('app.household_id', true), '')::uuid);

ALTER TABLE locations ENABLE ROW LEVEL SECURITY;
ALTER TABLE locations FORCE ROW LEVEL SECURITY;
CREATE POLICY locations_household_isolation ON locations
    USING (household_id = nullif(current_setting('app.household_id', true), '')::uuid);

ALTER TABLE assets ENABLE ROW LEVEL SECURITY;
ALTER TABLE assets FORCE ROW LEVEL SECURITY;
CREATE POLICY assets_household_isolation ON assets
    USING (household_id = nullif(current_setting('app.household_id', true), '')::uuid);

ALTER TABLE documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE documents FORCE ROW LEVEL SECURITY;
CREATE POLICY documents_household_isolation ON documents
    USING (household_id = nullif(current_setting('app.household_id', true), '')::uuid);

ALTER TABLE loans ENABLE ROW LEVEL SECURITY;
ALTER TABLE loans FORCE ROW LEVEL SECURITY;
CREATE POLICY loans_household_isolation ON loans
    USING (household_id = nullif(current_setting('app.household_id', true), '')::uuid);

-- Las cinco tablas restantes se quedan deliberadamente sin politica porque no
-- llevan household_id, y hay una prueba que lo comprueba tabla por tabla para
-- que anadir una tabla nueva sin politica rompa la construccion:
--
--   identities                 una persona no pertenece a un hogar; su
--                              pertenencia si. Es la unica tabla con datos
--                              personales defendida por una sola capa, asi que
--                              su repositorio resuelve siempre por identidad
--                              autenticada: nada de listados ni de busquedas por
--                              correo fuera del login.
--   email_verification_tokens  cuelgan de la identidad, y no guardan mas que un
--   password_reset_tokens      hash, una caducidad y una marca de uso.
--   refresh_tokens
--   loan_access_tokens         cuelga del prestamo.
