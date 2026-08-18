-- El modulo de Proveedores y contactos de servicio (Fase 2, Hito 2).
--
-- La primera migracion de un modulo, y por tanto la que fija como se ven las
-- tres siguientes. Su ficha esta en `docs/backend/modules/suppliers.md` y se
-- escribio antes que esto.
--
-- **En `public` y no en un esquema propio.** El modulo de prueba del Hito 0 crea
-- la suya en `module_testbed` justo para NO falsear el recuento de tablas del
-- modelo, que es una prueba: un modulo de verdad tiene que aparecer en ese
-- recuento, y estas dos lo suben de diecisiete a diecinueve. Lo que no toca es
-- la lista de tablas sin politica, porque las dos llevan household_id, RLS y
-- FORCE como cualquier tabla del core.
--
-- Los nombres de tabla son del modulo y estan declarados en su ficha, que es lo
-- que impide que otro los tome ahora que todos comparten esquema.

-- El contacto de servicio: quien arregla, quien cobra y quien responde de una
-- garantia. Es **dato maestro compartido**, y ese es el motivo de que sea un
-- modulo y no un campo: sin el lo duplicarian CMMS, Compras, Gastos y Garantias,
-- cada uno con su propia version del numero cuando cambie.
CREATE TABLE suppliers (
    id               uuid PRIMARY KEY,
    household_id     uuid        NOT NULL REFERENCES households (id) ON DELETE CASCADE,
    name             text        NOT NULL,
    -- Lista cerrada, al contrario que la `Category` del core, y la diferencia es
    -- deliberada: la del core clasifica **lo que el hogar tiene**, que es
    -- ilimitado, y esta clasifica **a que se dedica quien viene a casa**. El
    -- disparador de revisarlo esta escrito en la ficha: el dia que OTHER sea la
    -- mas usada, la lista dejo de clasificar y toca convertirla en catalogo por
    -- hogar. Es un identificador, asi que va en ingles y en UPPER_SNAKE_CASE; lo
    -- que se lee en pantalla es un dato y lo pone el cliente en castellano.
    service_category text        NOT NULL,
    -- La persona con la que se habla dentro de una empresa. Nulo cuando el
    -- contacto **es** la persona, que es la mitad de los casos en una casa.
    contact_name     text,
    phone            text,
    email            text,
    website          text,
    address          text,
    notes            text,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    -- Retirada **logica**, igual que la de una categoria del core y por el mismo
    -- motivo: hay filas que la referencian. Deja de ofrecerse al enlazar y
    -- conserva lo que ya colgaba de ella.
    retired_at       timestamptz,
    created_by       uuid,
    updated_by       uuid,
    CONSTRAINT suppliers_service_category_valid CHECK (service_category IN (
        'PLUMBING', 'ELECTRICITY', 'HEATING_COOLING', 'APPLIANCES', 'CARPENTRY',
        'MASONRY', 'PAINTING', 'LOCKSMITH', 'CLEANING', 'GARDENING',
        'PEST_CONTROL', 'VEHICLE', 'UTILITIES', 'OTHER'
    )),
    -- Un contacto de servicio del que no se sabe como llamar no es un contacto.
    -- Se comprueba tambien en el caso de uso, que es quien da el mensaje que el
    -- contrato declara; esto cierra la puerta a que entre por otro camino.
    CONSTRAINT suppliers_reachable
        CHECK (phone IS NOT NULL OR email IS NOT NULL OR website IS NOT NULL),
    CONSTRAINT suppliers_household_scoped_id UNIQUE (household_id, id)
);

-- Unico entre los **vigentes** del hogar, sin distinguir mayusculas ni acentos,
-- con la misma forma que el de categorias. Que sea parcial es lo que deja volver
-- a dar de alta a quien se retiro por error sin chocar con la fila vieja.
CREATE UNIQUE INDEX suppliers_name_unique_live
    ON suppliers (household_id, lower(immutable_unaccent(name)))
    WHERE retired_at IS NULL;

-- El listado filtra por categoria de servicio, que es la unica agrupacion que
-- este modulo ofrece.
CREATE INDEX suppliers_by_service_category
    ON suppliers (household_id, service_category);

-- La autoria apunta a la **pertenencia** y no a la identidad, como en todo el
-- core, y con la clave ajena compuesta que impide atribuirla a alguien de otro
-- hogar. Nulo significa que lo hizo el sistema y no una persona.
ALTER TABLE suppliers ADD CONSTRAINT suppliers_created_by_same_household
    FOREIGN KEY (household_id, created_by) REFERENCES household_members (household_id, id);
ALTER TABLE suppliers ADD CONSTRAINT suppliers_updated_by_same_household
    FOREIGN KEY (household_id, updated_by) REFERENCES household_members (household_id, id);

ALTER TABLE suppliers ENABLE ROW LEVEL SECURITY;
ALTER TABLE suppliers FORCE ROW LEVEL SECURITY;
CREATE POLICY suppliers_household_isolation ON suppliers
    USING (household_id = nullif(current_setting('app.household_id', true), '')::uuid);

-- El enlace de un contacto con **exactamente una** cosa del core: el asset o la
-- ubicacion. Dos columnas excluyentes y no un par (tipo, identificador), que es
-- la forma que ya usan `documents` y `loans` en el core, y por la misma razon:
-- un identificador polimorfico no admite clave ajena, asi que nada impediria
-- apuntar a algo que no existe.
CREATE TABLE supplier_links (
    id           uuid PRIMARY KEY,
    household_id uuid        NOT NULL REFERENCES households (id) ON DELETE CASCADE,
    supplier_id  uuid        NOT NULL,
    asset_id     uuid,
    location_id  uuid,
    created_at   timestamptz NOT NULL DEFAULT now(),
    -- Sin `updated_at` ni `updated_by`, que son obligatorios en todo el core:
    -- **un enlace no se modifica**. Se crea y se deshace, y no hay ningun campo
    -- que cambiar entre una cosa y la otra. Es la misma excepcion que la V8
    -- razona para los avisos: dos columnas que solo pueden repetir lo que ya
    -- dice `created_by` son una convencion cumplida sin significado.
    created_by   uuid,
    CONSTRAINT supplier_links_single_target
        CHECK ((asset_id IS NULL) <> (location_id IS NULL))
);

-- Un contacto no se enlaza dos veces con lo mismo. Dos indices parciales y no
-- uno: con NULLS NOT DISTINCT sobre las tres columnas, dos enlaces del mismo
-- proveedor a **assets distintos** compartirian el nulo de `location_id` y
-- seguirian siendo distintos --eso funciona--, pero la lectura de por que
-- funciona es mucho peor que la de estos dos.
CREATE UNIQUE INDEX supplier_links_unique_asset
    ON supplier_links (supplier_id, asset_id)
    WHERE asset_id IS NOT NULL;
CREATE UNIQUE INDEX supplier_links_unique_location
    ON supplier_links (supplier_id, location_id)
    WHERE location_id IS NOT NULL;

-- Las tres claves ajenas van con ON DELETE CASCADE, y esto es lo unico de esta
-- migracion que no es un reflejo del core.
--
-- El reflejo seria una clave ajena compuesta a secas, que impide apuntar a otro
-- hogar y **restringe** el borrado. Lo que ese reflejo no ve es que
-- `DeleteLocation` borra la fila de verdad --una ubicacion vacia no deja
-- historial que preservar-- despues de comprobar que no cuelga nada de ella,
-- mirando ubicaciones y assets, que es todo lo que el core conoce. Con una clave
-- ajena que restrinja, un enlace de este modulo convertiria ese borrado en una
-- violacion de restriccion: un 500 **de una operacion del core causado por un
-- modulo**, que es justo lo que la Fase 2 promete que no puede pasar.
--
-- Asi que el enlace cede. El precio --un enlace que desaparece sin que nadie lo
-- diga-- es el correcto: lo que ya no existe no lo arregla nadie.
--
-- Con MATCH SIMPLE, que es el que rige por omision, una compuesta con algun
-- valor a nulo **no se comprueba**: es lo que permite que las dos convivan sobre
-- la misma fila sabiendo que solo una esta informada.
ALTER TABLE supplier_links ADD CONSTRAINT supplier_links_supplier_same_household
    FOREIGN KEY (household_id, supplier_id) REFERENCES suppliers (household_id, id) ON DELETE CASCADE;
ALTER TABLE supplier_links ADD CONSTRAINT supplier_links_asset_same_household
    FOREIGN KEY (household_id, asset_id) REFERENCES assets (household_id, id) ON DELETE CASCADE;
ALTER TABLE supplier_links ADD CONSTRAINT supplier_links_location_same_household
    FOREIGN KEY (household_id, location_id) REFERENCES locations (household_id, id) ON DELETE CASCADE;
ALTER TABLE supplier_links ADD CONSTRAINT supplier_links_created_by_same_household
    FOREIGN KEY (household_id, created_by) REFERENCES household_members (household_id, id);

ALTER TABLE supplier_links ENABLE ROW LEVEL SECURITY;
ALTER TABLE supplier_links FORCE ROW LEVEL SECURITY;
CREATE POLICY supplier_links_household_isolation ON supplier_links
    USING (household_id = nullif(current_setting('app.household_id', true), '')::uuid);
