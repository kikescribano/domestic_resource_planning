-- La tabla del modulo de prueba del Hito 0.
--
-- No es una migracion de Flyway y no se despliega: la crea el contenedor de
-- pruebas junto al esquema, y solo alli. El modulo que la usa vive en el arbol
-- de pruebas por lo mismo --es el testigo del mecanismo de activacion, no un
-- modulo del producto.
--
-- Vive en un **esquema propio** y no en `public` a proposito. Las pruebas de
-- esquema afirman cuantas tablas tiene el modelo y cuales se quedan sin
-- politica, y una tabla de mentira en `public` las haria fallar --o, peor,
-- obligaria a excluirla por nombre y a que esa exclusion tapara ademas
-- cualquier tabla nueva que alguien olvidara.
--
-- Lo que si comparte con una tabla de modulo de verdad es todo lo demas:
-- household_id, RLS, FORCE y su politica. Es lo que hace que la prueba del
-- ciclo completo signifique algo: los datos que sobreviven a una desactivacion
-- estan protegidos como los de cualquier hogar.

CREATE SCHEMA IF NOT EXISTS module_testbed;

CREATE TABLE module_testbed.notes (
    id           uuid PRIMARY KEY,
    household_id uuid        NOT NULL REFERENCES public.households (id) ON DELETE CASCADE,
    text         text        NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE module_testbed.notes ENABLE ROW LEVEL SECURITY;
ALTER TABLE module_testbed.notes FORCE ROW LEVEL SECURITY;
CREATE POLICY notes_household_isolation ON module_testbed.notes
    USING (household_id = nullif(current_setting('app.household_id', true), '')::uuid);

-- El esquema es nuevo, asi que no le alcanzan los ALTER DEFAULT PRIVILEGES de
-- `docker/postgres/init/01-app-role.sql`, que solo cubren `public`.
GRANT USAGE ON SCHEMA module_testbed TO drp_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON module_testbed.notes TO drp_app;
