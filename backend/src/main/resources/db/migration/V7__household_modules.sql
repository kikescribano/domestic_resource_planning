-- La activacion de modulos, hogar por hogar (ADR-010).
--
-- Es la primera tabla que no describe nada del mundo del usuario: describe que
-- partes del sistema quiere ese hogar. Aun asi es **un dato del hogar y no una
-- configuracion global**, y por eso lleva household_id, RLS y FORCE como
-- cualquier tabla del core. La alternativa --una tabla de instalacion, sin
-- inquilino-- convertiria "que modulos tiene el hogar A" en algo legible desde
-- el hogar B, que es exactamente lo que la ADR-002 cierra.
--
-- Que **no** hay aqui, a proposito:
--
--   * Ningun CHECK con la lista de claves. El catalogo de modulos vive en
--     codigo, que es donde estan tambien su nombre, su descripcion y su prefijo
--     de ruta; congelarlo aqui obligaria a una migracion por modulo nuevo y
--     dejaria fuera al modulo de prueba, que vive en el arbol de pruebas. Lo
--     que si se comprueba es la **forma** de la clave, que es convencion del
--     proyecto y no catalogo.
--   * Ninguna fila sembrada al crear un hogar. La ausencia de fila **es**
--     inactivo, asi que sembrar catorce filas apagadas por hogar solo anadiria
--     mantenimiento: el dia que se anade un modulo habria que sembrarlo hacia
--     atras en todos los hogares que ya existen.
--
-- Y una cosa que si esta y podria parecer redundante: `status` ademas de las
-- dos fechas. Es lo que hace que desactivar **conserve** --la fila se queda y
-- solo cambia de estado-- en lugar de borrar. Reactivar vuelve a poner ACTIVE
-- sobre la misma fila, y por eso los datos del modulo siguen donde estaban.

CREATE TABLE household_modules (
    id             uuid PRIMARY KEY,
    household_id   uuid        NOT NULL REFERENCES households (id) ON DELETE CASCADE,
    -- La clave es un identificador, asi que va en ingles y en UPPER_SNAKE_CASE
    -- --SUPPLIERS, WAREHOUSE, PURCHASING, MAINTENANCE--. El nombre que ve el
    -- usuario es un dato, va en castellano y vive en el registro en codigo, no
    -- aqui: guardarlo tambien en la fila daria dos versiones del mismo nombre.
    module_key     text        NOT NULL,
    status         text        NOT NULL,
    -- Cuando se encendio por ultima vez y cuando se apago. Las dos conviven
    -- porque el historial de un modulo reactivado es justo la pregunta que se
    -- hace quien ve datos de un modulo que creia apagado.
    activated_at   timestamptz,
    deactivated_at timestamptz,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    created_by     uuid,
    updated_by     uuid,
    CONSTRAINT household_modules_status_valid
        CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT household_modules_key_shape
        CHECK (module_key ~ '^[A-Z][A-Z0-9_]*$')
);

-- Un modulo aparece una sola vez por hogar. No es parcial ni excluye estados: la
-- fila apagada tiene que seguir bloqueando su clave, porque es la misma que se
-- reactiva.
CREATE UNIQUE INDEX household_modules_one_per_household
    ON household_modules (household_id, module_key);

-- La autoria apunta a la **pertenencia** y no a la identidad, como en todo el
-- core, y con la clave ajena compuesta que impide apuntar a un miembro de otro
-- hogar. Nulo significa que lo hizo el sistema y no una persona.
ALTER TABLE household_modules ADD CONSTRAINT household_modules_created_by_same_household
    FOREIGN KEY (household_id, created_by) REFERENCES household_members (household_id, id);
ALTER TABLE household_modules ADD CONSTRAINT household_modules_updated_by_same_household
    FOREIGN KEY (household_id, updated_by) REFERENCES household_members (household_id, id);

-- Segunda capa, con la misma forma que la V3: `nullif` para que la sesion sin
-- contexto falle CERRADA, y sin WITH CHECK propio para que la expresion de
-- USING gobierne tambien las escrituras.
ALTER TABLE household_modules ENABLE ROW LEVEL SECURITY;
ALTER TABLE household_modules FORCE ROW LEVEL SECURITY;
CREATE POLICY household_modules_household_isolation ON household_modules
    USING (household_id = nullif(current_setting('app.household_id', true), '')::uuid);
