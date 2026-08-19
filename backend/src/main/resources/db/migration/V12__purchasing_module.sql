-- El modulo Compras y lista de la compra (Fase 2, Hito 4).
--
-- Su ficha esta en `docs/backend/modules/purchasing.md` y se escribio antes que
-- esto. La frontera contra Warehouse esta alli sin ambiguedad y aqui se cumple:
--
--   **Warehouse detecta la falta; Compras decide que se compra y cuando.** No hay
--   en estas dos tablas ni una columna que guarde una cantidad disponible, ni un
--   minimo, ni una caducidad. Lo que llega de Warehouse es un hecho sobre un
--   articulo del core --«esto bajo del minimo», «esto se acabo»-- y lo que se
--   guarda aqui es lo que el hogar decide hacer con ese hecho.
--
-- **En `public` y no en un esquema propio**, por lo mismo que la V9 y la V10: el
-- esquema aparte es la trampa del modulo de prueba del Hito 0, que lo usa justo
-- para NO falsear el recuento de tablas del modelo. Estas dos lo suben de
-- veintitres a veinticinco y **no tocan** la lista de tablas sin politica, porque
-- las dos llevan household_id, RLS y FORCE.
--
-- Los nombres estan declarados en su ficha, que es lo que impide que otro modulo
-- los tome ahora que todos comparten esquema.

-- ---------------------------------------------------------------------------
-- La compra
-- ---------------------------------------------------------------------------
-- Un acto de comprar: donde, cuando, y en que punto esta. Existe como cosa
-- aparte de sus lineas porque **una compra se prepara antes de hacerse**: se
-- decide que se lleva, se va, y se recibe --o no.
CREATE TABLE purchases (
    id            uuid PRIMARY KEY,
    household_id  uuid        NOT NULL REFERENCES households (id) ON DELETE CASCADE,
    -- **Sin clave ajena hacia `suppliers`, y con el nombre dentro.** Dos razones
    -- que apuntan al mismo sitio:
    --
    --   1. Una compra es historia. Que el 3 de marzo se compro arroz en aquel
    --      sitio siguio siendo cierto aunque el sitio se retire o el hogar apague
    --      Proveedores. Es el argumento de la V8 para los avisos y el de la V10
    --      para el cuaderno.
    --   2. Una clave ajena hacia la tabla de otro modulo es **una dependencia de
    --      esquema que ArchUnit no puede ver**. Un JOIN desde aqui incumpliria la
    --      frontera igual que un import, solo que sin nada que lo delate.
    --
    -- El nombre se copia al crear la compra --leido por el puerto de plataforma,
    -- que responde vacio si Proveedores esta apagado-- y no se vuelve a mirar.
    supplier_id   uuid,
    supplier_name text,
    status        text        NOT NULL,
    note          text,
    -- Cuando entro en casa, y cuando se anulo. Nulas mientras siga abierta.
    received_at   timestamptz,
    cancelled_at  timestamptz,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    created_by    uuid,
    updated_by    uuid,
    CONSTRAINT purchases_status_valid CHECK (status IN ('OPEN', 'RECEIVED', 'CANCELLED')),
    -- O las dos cosas o ninguna: un identificador sin nombre no se puede pintar
    -- cuando Proveedores esta apagado, que es justo el caso para el que se copia.
    CONSTRAINT purchases_supplier_pair
        CHECK ((supplier_id IS NULL) = (supplier_name IS NULL)),
    -- La fecha y el estado no pueden decir cosas distintas.
    CONSTRAINT purchases_dates_match_status CHECK (
        (status = 'OPEN' AND received_at IS NULL AND cancelled_at IS NULL)
        OR (status = 'RECEIVED' AND received_at IS NOT NULL AND cancelled_at IS NULL)
        OR (status = 'CANCELLED' AND cancelled_at IS NOT NULL AND received_at IS NULL)
    ),
    CONSTRAINT purchases_household_scoped_id UNIQUE (household_id, id)
);

CREATE INDEX purchases_by_recency ON purchases (household_id, created_at DESC);

-- ---------------------------------------------------------------------------
-- La lista de la compra
-- ---------------------------------------------------------------------------
-- La unidad de trabajo del modulo: existe sola, entra en una compra y vuelve a
-- salir si esa compra se anula --lo que hacia falta sigue haciendo falta.
CREATE TABLE shopping_list_items (
    id                uuid PRIMARY KEY,
    household_id      uuid        NOT NULL REFERENCES households (id) ON DELETE CASCADE,
    -- **Un articulo del core O un nombre suelto**, nunca los dos y nunca ninguno.
    -- El nombre suelto es para lo que todavia no esta en el catalogo --«pilas
    -- AA»-- y es real: una lista de la compra de verdad las tiene. Lo que una
    -- linea asi no puede hacer es dar entrada en el almacen, porque inventarle
    -- una categoria y una unidad seria decidir por el hogar.
    article_id        uuid,
    name              text,
    -- Opcional: «hace falta arroz» es una linea completa. Lo que no puede es ser
    -- cero o negativa.
    quantity          numeric,
    -- Por que entro. MANUAL la puso una persona; LOW_STOCK y DEPLETED los puso
    -- Warehouse al publicar su hecho. Con Warehouse apagado solo hay MANUAL, y
    -- eso es lo correcto: nadie esta detectando la falta.
    origin            text        NOT NULL,
    status            text        NOT NULL,
    note              text,
    -- La compra en la que va, mientras va en una.
    purchase_id       uuid,
    -- La existencia del core que resulto de recibirla. Es la traza de que el
    -- ciclo se cerro, y lo unico que este modulo guarda de lo que escribio fuera.
    received_asset_id uuid,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    created_by        uuid,
    updated_by        uuid,
    CONSTRAINT shopping_list_items_article_xor_name CHECK (
        (article_id IS NOT NULL AND name IS NULL) OR (article_id IS NULL AND name IS NOT NULL)
    ),
    CONSTRAINT shopping_list_items_quantity_positive
        CHECK (quantity IS NULL OR quantity > 0),
    CONSTRAINT shopping_list_items_origin_valid
        CHECK (origin IN ('MANUAL', 'LOW_STOCK', 'DEPLETED')),
    CONSTRAINT shopping_list_items_status_valid
        CHECK (status IN ('NEEDED', 'IN_PURCHASE', 'BOUGHT', 'DISMISSED')),
    -- Estar en una compra y no tener compra son incompatibles; lo contrario no,
    -- porque una linea comprada conserva de que compra vino.
    CONSTRAINT shopping_list_items_in_purchase_has_purchase
        CHECK (status <> 'IN_PURCHASE' OR purchase_id IS NOT NULL),
    -- Solo una linea con articulo pudo dar entrada al core.
    CONSTRAINT shopping_list_items_received_implies_article
        CHECK (received_asset_id IS NULL OR article_id IS NOT NULL),
    CONSTRAINT shopping_list_items_household_scoped_id UNIQUE (household_id, id)
);

-- **Una sola linea viva por articulo**, y este indice es la idempotencia del
-- modulo hacia el lado del bus: la entrega es at-least-once, asi que el mismo
-- `StockBelowMinimum` puede llegar dos veces y **no puede dejar dos lineas**.
--
-- Va con indice y no con una comprobacion previa por lo que aprendio el Hito 3:
-- comprobar y despues insertar deja una ventana entre las dos cosas por la que
-- caben dos entregas simultaneas del mismo evento.
--
-- Solo alcanza a las vivas --NEEDED e IN_PURCHASE--: lo comprado el mes pasado no
-- puede bloquear que hoy vuelva a hacer falta, que es exactamente el defecto que
-- el indice parcial del core evita con las existencias dadas de baja.
CREATE UNIQUE INDEX shopping_list_items_one_live_per_article
    ON shopping_list_items (household_id, article_id)
    WHERE article_id IS NOT NULL AND status IN ('NEEDED', 'IN_PURCHASE');

CREATE INDEX shopping_list_items_by_status
    ON shopping_list_items (household_id, status, created_at DESC);
CREATE INDEX shopping_list_items_by_purchase
    ON shopping_list_items (household_id, purchase_id)
    WHERE purchase_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- Claves ajenas hacia el core: ON DELETE CASCADE
-- ---------------------------------------------------------------------------
-- Es lo que decidio el Hito 2 y por el mismo motivo: con el RESTRICT que rige por
-- omision, una fila de un modulo convertiria una operacion del core en una
-- violacion de restriccion, o sea en un 500 **del core causado por un modulo**.
--
-- Conviene saber que **hoy ninguna de estas cascadas se dispara sola**: un
-- articulo se retira y un asset se da de baja, y ninguno de los dos se borra, asi
-- que las tres solo actuan al borrar el hogar entero. Se ponen igual, y no es
-- ceremonia: el RESTRICT por omision seria una bomba de relojeria armada el dia
-- que el core estrene un borrado de verdad --como ya lo tiene `DeleteLocation`--,
-- y ese dia el fallo apareceria como un 500 del core sin que nadie relacionara la
-- causa con este fichero.
ALTER TABLE purchases ADD CONSTRAINT purchases_created_by_same_household
    FOREIGN KEY (household_id, created_by) REFERENCES household_members (household_id, id);
ALTER TABLE purchases ADD CONSTRAINT purchases_updated_by_same_household
    FOREIGN KEY (household_id, updated_by) REFERENCES household_members (household_id, id);

ALTER TABLE shopping_list_items ADD CONSTRAINT shopping_list_items_article_same_household
    FOREIGN KEY (household_id, article_id) REFERENCES articles (household_id, id) ON DELETE CASCADE;
ALTER TABLE shopping_list_items ADD CONSTRAINT shopping_list_items_purchase_same_household
    FOREIGN KEY (household_id, purchase_id) REFERENCES purchases (household_id, id) ON DELETE CASCADE;
ALTER TABLE shopping_list_items ADD CONSTRAINT shopping_list_items_asset_same_household
    FOREIGN KEY (household_id, received_asset_id) REFERENCES assets (household_id, id) ON DELETE CASCADE;
ALTER TABLE shopping_list_items ADD CONSTRAINT shopping_list_items_created_by_same_household
    FOREIGN KEY (household_id, created_by) REFERENCES household_members (household_id, id);
ALTER TABLE shopping_list_items ADD CONSTRAINT shopping_list_items_updated_by_same_household
    FOREIGN KEY (household_id, updated_by) REFERENCES household_members (household_id, id);

-- ---------------------------------------------------------------------------
-- Aislamiento: las dos con RLS y FORCE
-- ---------------------------------------------------------------------------
-- Como en Warehouse, la politica hace aqui mas trabajo que en una tabla del core,
-- porque parte de lo que se escribe **no nace de una peticion**: las lineas de
-- origen LOW_STOCK y DEPLETED las escribe un handler de evento, y la siembra
-- corre dentro de la activacion.
ALTER TABLE purchases ENABLE ROW LEVEL SECURITY;
ALTER TABLE purchases FORCE ROW LEVEL SECURITY;
CREATE POLICY purchases_household_isolation ON purchases
    USING (household_id = nullif(current_setting('app.household_id', true), '')::uuid);

ALTER TABLE shopping_list_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE shopping_list_items FORCE ROW LEVEL SECURITY;
CREATE POLICY shopping_list_items_household_isolation ON shopping_list_items
    USING (household_id = nullif(current_setting('app.household_id', true), '')::uuid);
