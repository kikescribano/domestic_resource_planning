-- Etiquetas libres y la identidad visual de una categoria (cierre de huecos,
-- Hito 4).
--
-- Son **los dos atributos que quedaban** de los cuatro que 4.1.7 dejo propuestos
-- y sin decidir el 2026-08-09. Los otros dos --el estado de conservacion y la
-- condicion en prestamo-- entraron en la V16. Los cuatro son **del core**, por
-- la misma regla que subio el peso y el volumen al articulo en la V11: una regla
-- del core no puede depender de un modulo que se puede apagar.
--
-- Aqui hay dos tablas y dos columnas, y el modelo pasa de 29 a **31 tablas**.

-- ---------------------------------------------------------------------------
-- La etiqueta: catalogo por hogar, y no una columna de texto
-- ---------------------------------------------------------------------------
--
-- Es la pregunta que el plan le asigno a este hito, y se decide **catalogo**.
-- Cuesta una tabla y una relacion; la columna de texto no cuesta ninguna y
-- pierde las tres cosas que hacen util una etiqueta:
--
--   * **Renombrar.** Con catalogo es un `UPDATE` de una fila. Con texto hay que
--     recorrer todos los assets del hogar y reescribir cada uno, y quien tenga
--     la etiqueta mal escrita se queda con las dos versiones.
--   * **Deduplicar sin distinguir mayusculas ni acentos.** Con catalogo lo hace
--     el mismo indice unico que ya protege `categories`, `articles` y
--     `locations`. Con texto habria que normalizar en cada escritura y aun asi
--     nada impediria que «Camping» y «camping» convivieran, porque no hay
--     ninguna fila comun donde ponerle la restriccion.
--   * **Autocompletar.** Con catalogo es un `SELECT` sobre una tabla pequena.
--     Con texto hay que recorrer los assets enteros y hacer un `DISTINCT` sobre
--     un campo repetido tantas veces como cosas tenga la casa.
--
-- La forma es la de `categories` y no otra --misma autoria, misma retirada
-- logica, mismo indice normalizado-- porque es literalmente el mismo problema:
-- un vocabulario del hogar que clasifica assets. Lo unico que cambia es la
-- cardinalidad, y eso vive en la tabla de union.

CREATE TABLE tags (
    id           uuid PRIMARY KEY,
    household_id uuid        NOT NULL REFERENCES households (id) ON DELETE CASCADE,
    name         text        NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    retired_at   timestamptz,
    created_by   uuid,
    updated_by   uuid,
    CONSTRAINT tags_household_scoped_id UNIQUE (household_id, id)
);

-- **El indice NO es parcial por retirada**, y ahi se aparta de `categories` a
-- proposito. Alli el parcial permite que un nombre retirado se vuelva a usar, y
-- no molesta porque un asset tiene **una** categoria: nunca puede llevar la
-- vieja y la nueva a la vez. Una etiqueta si, y entonces la misma fila del
-- inventario pintaria «Camping» dos veces sin que nada estuviera roto.
--
-- Lo que sustituye al parcial es que `CreateTag` **revive** la etiqueta retirada
-- que se llame asi en vez de fallar, que es lo mismo que hace
-- `RegisterConsumableIntake` con un articulo: resolverlo, creandolo si hace
-- falta. Asi ningun nombre se quema y la retirada tiene deshacer sin necesitar
-- una operacion propia.
CREATE UNIQUE INDEX tags_name_unique
    ON tags (household_id, lower(immutable_unaccent(name)));

ALTER TABLE tags ADD CONSTRAINT tags_created_by_same_household
    FOREIGN KEY (household_id, created_by) REFERENCES household_members (household_id, id);
ALTER TABLE tags ADD CONSTRAINT tags_updated_by_same_household
    FOREIGN KEY (household_id, updated_by) REFERENCES household_members (household_id, id);

COMMENT ON TABLE tags IS
    'Vocabulario libre del hogar para clasificar assets por mas de una cosa. Ver 4.1.1.';

-- ---------------------------------------------------------------------------
-- La relacion, con el hogar dentro
-- ---------------------------------------------------------------------------
--
-- `household_id` en una tabla de union puede parecer redundante --las dos puntas
-- ya son del hogar-- y no lo es por dos motivos: sin el no hay politica de RLS
-- que escribir, que es la segunda capa que la ADR-003 exige de **toda** tabla; y
-- es lo que permite declarar las dos claves ajenas **compuestas** contra el
-- `UNIQUE (household_id, id)` de cada punta, que es lo que impide de verdad
-- etiquetar el asset de otro hogar --una clave ajena simple no lo impide, porque
-- su comprobacion no pasa por RLS.
--
-- El borrado **si es real** aqui, al contrario que en las dos puntas. Una fila
-- de esta tabla no es historial de nada: es «esta cosa lleva hoy esta etiqueta»,
-- y quitarla es exactamente lo que el usuario pide cuando la quita. Un
-- `removed_at` obligaria ademas a filtrarlo en cada consulta y en el indice
-- unico, para guardar que algo estuvo etiquetado un rato.
CREATE TABLE asset_tags (
    household_id uuid        NOT NULL REFERENCES households (id) ON DELETE CASCADE,
    asset_id     uuid        NOT NULL,
    tag_id       uuid        NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    created_by   uuid,
    PRIMARY KEY (asset_id, tag_id)
);

ALTER TABLE asset_tags ADD CONSTRAINT asset_tags_asset_same_household
    FOREIGN KEY (household_id, asset_id) REFERENCES assets (household_id, id) ON DELETE CASCADE;
ALTER TABLE asset_tags ADD CONSTRAINT asset_tags_tag_same_household
    FOREIGN KEY (household_id, tag_id) REFERENCES tags (household_id, id) ON DELETE CASCADE;
ALTER TABLE asset_tags ADD CONSTRAINT asset_tags_created_by_same_household
    FOREIGN KEY (household_id, created_by) REFERENCES household_members (household_id, id);

-- La clave primaria ya sirve para «que etiquetas lleva este asset». El indice al
-- reves es el que sostiene el filtro del listado --«que hay etiquetado como
-- camping»--, que es la mitad por la que existe el atributo.
CREATE INDEX asset_tags_by_tag ON asset_tags (tag_id);

-- No lleva `updated_at` ni `updated_by`: una fila de union no se modifica. Se
-- pone o se quita, y las dos cosas quedan en `created_*` o en su ausencia.
COMMENT ON TABLE asset_tags IS
    'Que etiquetas lleva un asset. Borrado real: no es historial de nada.';

-- ---------------------------------------------------------------------------
-- El icono y el color de una categoria
-- ---------------------------------------------------------------------------
--
-- Dos columnas anulables sobre `categories`, y **las dos con su lista cerrada en
-- un `CHECK`**, como `type`, `status` y `unit`. Que sean cerradas es la decision
-- entera de la ADR-015: el color que un usuario elige libremente no esta en
-- ningun token del sistema de diseno, asi que **no lo mide nadie** --seria lo
-- unico de la interfaz cuyo contraste se afirma en vez de comprobarse--. Con el
-- juego cerrado, los doce pares que producen los seis colores entran en la lista
-- de `scripts/check-contrast.py` y la construccion falla si alguno baja de WCAG
-- AA.
--
-- Nulo es el caso normal y significa **que nadie lo eligio**, no que sea gris:
-- una categoria recien sembrada no tiene por que tener cara.
--
-- La lista vive tambien aqui y no solo en el enumerado de Kotlin por lo mismo
-- que la lista blanca de `files.content_type`: ampliarla exige una migracion, y
-- esa friccion es deliberada --un color nuevo obliga a pasar por el script de
-- contraste antes de existir.

ALTER TABLE categories
    ADD COLUMN icon  text,
    ADD COLUMN color text;

ALTER TABLE categories ADD CONSTRAINT categories_icon_valid
    CHECK (icon IS NULL OR icon IN (
        'BOX', 'SOFA', 'UTENSILS', 'SPRAY', 'TOOL', 'FRAME', 'PLUG', 'POT',
        'PILL', 'MONITOR', 'SHIRT', 'BIKE', 'PENCIL', 'CAR', 'LEAF', 'PAW'));

ALTER TABLE categories ADD CONSTRAINT categories_color_valid
    CHECK (color IS NULL OR color IN ('ROSE', 'PLUM', 'INDIGO', 'SKY', 'TEAL', 'MOSS'));

COMMENT ON COLUMN categories.icon IS
    'Uno de los dieciseis del juego cerrado. Nulo = nadie lo eligio. Ver ADR-015.';
COMMENT ON COLUMN categories.color IS
    'Uno de los seis del juego cerrado, todos medidos en check-contrast.py. Ver ADR-015.';

-- ---------------------------------------------------------------------------
-- Row-Level Security, la segunda capa (ADR-003)
-- ---------------------------------------------------------------------------
--
-- Las dos tablas nuevas, con `FORCE` porque sin el la politica no se aplica al
-- propietario de la tabla. `categories` ya la tiene desde la V3 y las dos
-- columnas nuevas la heredan: la politica es de la fila, no de la columna.

ALTER TABLE tags ENABLE ROW LEVEL SECURITY;
ALTER TABLE tags FORCE ROW LEVEL SECURITY;
CREATE POLICY tags_household_isolation ON tags
    USING (household_id = nullif(current_setting('app.household_id', true), '')::uuid);

ALTER TABLE asset_tags ENABLE ROW LEVEL SECURITY;
ALTER TABLE asset_tags FORCE ROW LEVEL SECURITY;
CREATE POLICY asset_tags_household_isolation ON asset_tags
    USING (household_id = nullif(current_setting('app.household_id', true), '')::uuid);
