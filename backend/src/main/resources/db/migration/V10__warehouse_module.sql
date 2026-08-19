-- El modulo Warehouse (Fase 2, Hito 3).
--
-- Su ficha esta en `docs/backend/modules/warehouse.md` y se escribio antes que
-- esto. La frontera contra el core esta alli sin ambiguedad y aqui se cumple:
--
--   **El core mantiene un contador y este modulo no lleva ninguno.** No hay en
--   estas cuatro tablas ni una columna que guarde «cuanto hay» de una existencia.
--   Cuando hace falta --pintar la lista, comparar con el minimo, no dejar
--   consumir de mas-- se lee de `assets.quantity`. Dos contadores es lo unico que
--   esta frontera no puede permitirse, porque el dia que discrepen no hay forma
--   de saber cual miente.
--
-- La unica cantidad que se guarda aqui es la de un **lote**, y no es la misma
-- pregunta: «de los 900 g que hay, 400 son del lote que caduca en marzo».
--
-- **En `public` y no en un esquema propio**, por lo mismo que la V9: el esquema
-- aparte es la trampa del modulo de prueba del Hito 0, que lo usa justo para NO
-- falsear el recuento de tablas del modelo. Estas cuatro lo suben de diecinueve a
-- veintitres y **no tocan** la lista de tablas sin politica, porque las cuatro
-- llevan household_id, RLS y FORCE.
--
-- Los nombres llevan el prefijo del modulo y estan declarados en su ficha, que es
-- lo que impide que otro los tome ahora que todos comparten esquema.

-- ---------------------------------------------------------------------------
-- La ficha del modulo sobre un articulo
-- ---------------------------------------------------------------------------
-- Donde vive la regla: cuanto hay que tener siempre y con cuanta antelacion
-- avisar de que algo caduca. No duplica nada del `articles` del core --ni el
-- nombre, ni la unidad, ni el envase-- porque eso ya esta ahi y se lee.
CREATE TABLE warehouse_articles (
    id                uuid PRIMARY KEY,
    household_id      uuid        NOT NULL REFERENCES households (id) ON DELETE CASCADE,
    article_id        uuid        NOT NULL,
    -- Por debajo de esto hay que reponer. Nulo es «no lo vigilo», que es el caso
    -- normal: un hogar pone minimo a la leche y no a la canela.
    minimum_quantity  numeric,
    -- Con cuantos dias de antelacion avisar de una caducidad. Nulo cae en la del
    -- sitio, y si tampoco la tiene, en la del modulo.
    expiry_lead_days  integer,
    -- **Desde cuando esta bajo minimos**, y no un booleano calculado al vuelo.
    -- Es lo que permite avisar UNA vez: sin una fecha de entrada, la comprobacion
    -- nocturna no puede distinguir «acaba de caer» de «lleva tres semanas caido»,
    -- y avisaria las treinta noches. Nulo significa que esta por encima.
    low_stock_since   timestamptz,
    -- Si ya se dijo. Se borra junto con `low_stock_since` al reponer, que es lo
    -- que vuelve a armar el aviso para la proxima caida.
    low_stock_notified_at timestamptz,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    created_by        uuid,
    updated_by        uuid,
    CONSTRAINT warehouse_articles_minimum_not_negative
        CHECK (minimum_quantity IS NULL OR minimum_quantity >= 0),
    CONSTRAINT warehouse_articles_lead_not_negative
        CHECK (expiry_lead_days IS NULL OR expiry_lead_days >= 0),
    -- No se puede haber avisado de algo que no esta pasando.
    CONSTRAINT warehouse_articles_notified_implies_low
        CHECK (low_stock_notified_at IS NULL OR low_stock_since IS NOT NULL),
    CONSTRAINT warehouse_articles_one_per_article UNIQUE (household_id, article_id),
    CONSTRAINT warehouse_articles_household_scoped_id UNIQUE (household_id, id)
);

-- ---------------------------------------------------------------------------
-- La ficha del modulo sobre un sitio
-- ---------------------------------------------------------------------------
-- Existe porque «en la nevera avisame con tres dias y en la despensa con treinta»
-- es una regla **del sitio**, y el core no tiene donde ponerla: su `Location`
-- describe el contenedor, no como se vigila lo que hay dentro.
--
-- No guarda el nombre del sitio, a proposito: no hay ningun evento de renombrado
-- de ubicacion, asi que una copia del nombre se quedaria vieja sin que nada lo
-- dijera. El nombre se lee del core cuando se necesita.
CREATE TABLE warehouse_locations (
    id               uuid PRIMARY KEY,
    household_id     uuid        NOT NULL REFERENCES households (id) ON DELETE CASCADE,
    location_id      uuid        NOT NULL,
    expiry_lead_days integer,
    notes            text,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    created_by       uuid,
    updated_by       uuid,
    CONSTRAINT warehouse_locations_lead_not_negative
        CHECK (expiry_lead_days IS NULL OR expiry_lead_days >= 0),
    CONSTRAINT warehouse_locations_one_per_location UNIQUE (household_id, location_id),
    CONSTRAINT warehouse_locations_household_scoped_id UNIQUE (household_id, id)
);

-- ---------------------------------------------------------------------------
-- Los lotes con caducidad
-- ---------------------------------------------------------------------------
CREATE TABLE warehouse_lots (
    id           uuid PRIMARY KEY,
    household_id uuid        NOT NULL REFERENCES households (id) ON DELETE CASCADE,
    asset_id     uuid        NOT NULL,
    article_id   uuid        NOT NULL,
    -- El codigo del envase, cuando lo trae. Opcional porque la mitad de lo que
    -- hay en una despensa no lo lleva a la vista.
    lot_code     text,
    expires_on   date        NOT NULL,
    -- **La unica cantidad que este modulo guarda**, y no es un segundo contador:
    -- responde a otra pregunta --cuanto de lo que hay es de ESTE lote-- y la suma
    -- de los lotes de una existencia puede quedarse por debajo de lo que el core
    -- dice que hay. Lo que no esta en ningun lote es lo que nadie fecho.
    quantity     numeric     NOT NULL,
    -- Nulo es vivo. Se consume o se tira, y en los dos casos deja de vigilarse.
    consumed_at  timestamptz,
    -- En que fase se aviso: NEAR al entrar en la ventana de antelacion, EXPIRED
    -- el dia que caduca de verdad. Son **dos hechos distintos y no una
    -- repeticion**, y con esta columna un lote avisa como mucho dos veces en toda
    -- su vida en lugar de todas las noches.
    notified_stage text,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    created_by   uuid,
    updated_by   uuid,
    CONSTRAINT warehouse_lots_quantity_positive CHECK (quantity > 0),
    CONSTRAINT warehouse_lots_stage_valid
        CHECK (notified_stage IS NULL OR notified_stage IN ('NEAR', 'EXPIRED')),
    CONSTRAINT warehouse_lots_household_scoped_id UNIQUE (household_id, id)
);

-- No hay dos lotes vivos iguales en la misma existencia. Con NULLS NOT DISTINCT
-- para que dos lotes **sin codigo** y con la misma fecha tambien choquen: sin
-- eso, el caso mas comun --nadie apunta el codigo-- seria justo el que se puede
-- duplicar sin limite.
CREATE UNIQUE INDEX warehouse_lots_unique_live
    ON warehouse_lots (asset_id, lot_code, expires_on) NULLS NOT DISTINCT
    WHERE consumed_at IS NULL;

-- La comprobacion nocturna recorre los que vencen pronto y no han avisado.
CREATE INDEX warehouse_lots_by_expiry
    ON warehouse_lots (household_id, expires_on)
    WHERE consumed_at IS NULL;

-- ---------------------------------------------------------------------------
-- El cuaderno
-- ---------------------------------------------------------------------------
-- **La unica tabla del modelo que crece sin techo.** Todo lo demas crece con lo
-- que el hogar TIENE; esto crece con lo que el hogar HACE, y una casa que apunta
-- sus consumos escribe varias filas al dia para siempre. Volver a medir la
-- capacidad es del Hito 6; queda dicho aqui y en la ficha para que quien lo haga
-- sepa donde mirar primero. Su purga tampoco existe, y su sitio natural es una
-- comprobacion mas del recorrido diario, igual que la de `household_notices`.
--
-- **Lo escribe siempre el handler de eventos y nunca una operacion del modulo.**
-- Un solo escritor es lo que garantiza que un consumo hecho desde Warehouse y uno
-- hecho con el PATCH del core produzcan exactamente el mismo asiento.
CREATE TABLE warehouse_movements (
    id                uuid        PRIMARY KEY,
    household_id      uuid        NOT NULL REFERENCES households (id) ON DELETE CASCADE,
    asset_id          uuid        NOT NULL,
    article_id        uuid,
    -- **Sin clave ajena, y con el nombre dentro.** `DeleteLocation` borra la fila
    -- de verdad, asi que una clave ajena en cascada BORRARIA el asiento de que
    -- algo se movio al garaje el dia que alguien borre el garaje --y un libro que
    -- se reescribe cuando cambia el mundo no es un libro--. Es el mismo argumento
    -- que la V8 da para que un aviso lleve su texto dentro: lo que se escribio ese
    -- dia siguio siendo cierto.
    location_id       uuid,
    location_name     text,
    kind              text        NOT NULL,
    -- Nulas en una RELOCATION, que no cambia ninguna cantidad: mover algo de
    -- sitio es un movimiento de existencias y va en el mismo libro.
    previous_quantity numeric,
    quantity          numeric,
    -- Derivada y no escrita: dos columnas que alguien tiene que mantener
    -- coherentes acaban discrepando, y esta se puede calcular siempre.
    delta             numeric     GENERATED ALWAYS AS (quantity - previous_quantity) STORED,
    occurred_at       timestamptz NOT NULL,
    -- El evento que lo produjo, cuando lo hubo. La guarda de
    -- `IdempotentEventHandler` vive en memoria y solo cubre este proceso; esto
    -- cierra el caso de verdad. Nulo en el OPENING de la siembra, que no nace de
    -- ningun evento.
    event_id          uuid,
    created_at        timestamptz NOT NULL DEFAULT now(),
    -- Sin `updated_at` ni `updated_by`: **un asiento no se modifica**. Es la
    -- misma excepcion que razonan la V8 para los avisos y la V9 para los enlaces
    -- --dos columnas que solo pueden repetir lo que ya dice `created_by` son una
    -- convencion cumplida sin significado.
    created_by        uuid,
    CONSTRAINT warehouse_movements_kind_valid CHECK (kind IN (
        'OPENING', 'INTAKE', 'ADJUSTMENT', 'MERGE', 'DECOMMISSION', 'RELOCATION'
    )),
    -- Los cuatro de en medio son los del `QuantityChangeReason` del core, con el
    -- mismo nombre a proposito: renombrarlos daria dos vocabularios para el mismo
    -- hecho.
    CONSTRAINT warehouse_movements_quantities_match_kind CHECK (
        (kind = 'RELOCATION' AND quantity IS NULL AND previous_quantity IS NULL)
        OR (kind <> 'RELOCATION' AND quantity IS NOT NULL AND previous_quantity IS NOT NULL)
    ),
    CONSTRAINT warehouse_movements_household_scoped_id UNIQUE (household_id, id)
);

-- **Un solo OPENING por existencia**, y este indice es lo que hace idempotente a
-- la siembra: reactivar el modulo la vuelve a ejecutar --`ActivateModule` solo se
-- la ahorra si YA estaba activo-- y sin esto un hogar que encendiera y apagara
-- tres veces tendria tres asientos de apertura.
CREATE UNIQUE INDEX warehouse_movements_one_opening_per_asset
    ON warehouse_movements (asset_id)
    WHERE kind = 'OPENING';

-- Un evento no se asienta dos veces, ni siquiera si lo reentrega otro proceso.
CREATE UNIQUE INDEX warehouse_movements_unique_event
    ON warehouse_movements (event_id)
    WHERE event_id IS NOT NULL;

-- El cuaderno se lee por existencia y por articulo, siempre de lo mas reciente a
-- lo mas antiguo.
CREATE INDEX warehouse_movements_by_asset
    ON warehouse_movements (household_id, asset_id, occurred_at DESC);
CREATE INDEX warehouse_movements_by_article
    ON warehouse_movements (household_id, article_id, occurred_at DESC);

-- ---------------------------------------------------------------------------
-- Claves ajenas hacia el core: ON DELETE CASCADE
-- ---------------------------------------------------------------------------
-- Es lo que decidio el Hito 2 y por el mismo motivo: con el RESTRICT que rige por
-- omision, una fila de un modulo convertiria una operacion del core en una
-- violacion de restriccion, o sea en un 500 **del core causado por un modulo**.
-- Que es exactamente lo que la Fase 2 promete que no puede pasar.
--
-- El `asset_id` del cuaderno la lleva y no le pasa nada: un asset **no se borra
-- nunca** --se da de baja-- asi que la cascada solo se dispara cuando se borra el
-- hogar entero, que es cuando debe.
ALTER TABLE warehouse_articles ADD CONSTRAINT warehouse_articles_article_same_household
    FOREIGN KEY (household_id, article_id) REFERENCES articles (household_id, id) ON DELETE CASCADE;
ALTER TABLE warehouse_articles ADD CONSTRAINT warehouse_articles_created_by_same_household
    FOREIGN KEY (household_id, created_by) REFERENCES household_members (household_id, id);
ALTER TABLE warehouse_articles ADD CONSTRAINT warehouse_articles_updated_by_same_household
    FOREIGN KEY (household_id, updated_by) REFERENCES household_members (household_id, id);

ALTER TABLE warehouse_locations ADD CONSTRAINT warehouse_locations_location_same_household
    FOREIGN KEY (household_id, location_id) REFERENCES locations (household_id, id) ON DELETE CASCADE;
ALTER TABLE warehouse_locations ADD CONSTRAINT warehouse_locations_created_by_same_household
    FOREIGN KEY (household_id, created_by) REFERENCES household_members (household_id, id);
ALTER TABLE warehouse_locations ADD CONSTRAINT warehouse_locations_updated_by_same_household
    FOREIGN KEY (household_id, updated_by) REFERENCES household_members (household_id, id);

ALTER TABLE warehouse_lots ADD CONSTRAINT warehouse_lots_asset_same_household
    FOREIGN KEY (household_id, asset_id) REFERENCES assets (household_id, id) ON DELETE CASCADE;
ALTER TABLE warehouse_lots ADD CONSTRAINT warehouse_lots_article_same_household
    FOREIGN KEY (household_id, article_id) REFERENCES articles (household_id, id) ON DELETE CASCADE;
ALTER TABLE warehouse_lots ADD CONSTRAINT warehouse_lots_created_by_same_household
    FOREIGN KEY (household_id, created_by) REFERENCES household_members (household_id, id);
ALTER TABLE warehouse_lots ADD CONSTRAINT warehouse_lots_updated_by_same_household
    FOREIGN KEY (household_id, updated_by) REFERENCES household_members (household_id, id);

ALTER TABLE warehouse_movements ADD CONSTRAINT warehouse_movements_asset_same_household
    FOREIGN KEY (household_id, asset_id) REFERENCES assets (household_id, id) ON DELETE CASCADE;
ALTER TABLE warehouse_movements ADD CONSTRAINT warehouse_movements_article_same_household
    FOREIGN KEY (household_id, article_id) REFERENCES articles (household_id, id) ON DELETE CASCADE;
ALTER TABLE warehouse_movements ADD CONSTRAINT warehouse_movements_created_by_same_household
    FOREIGN KEY (household_id, created_by) REFERENCES household_members (household_id, id);

-- ---------------------------------------------------------------------------
-- Aislamiento: las cuatro con RLS y FORCE
-- ---------------------------------------------------------------------------
-- Aqui la politica hace mas trabajo que en Proveedores, porque **quien mas
-- escribe en estas tablas no nace de una peticion**: son los handlers de eventos
-- y el recorrido nocturno. Es lo que convierte un fallo de contexto en cero filas
-- en lugar de en un apunte en el cuaderno del vecino.
ALTER TABLE warehouse_articles ENABLE ROW LEVEL SECURITY;
ALTER TABLE warehouse_articles FORCE ROW LEVEL SECURITY;
CREATE POLICY warehouse_articles_household_isolation ON warehouse_articles
    USING (household_id = nullif(current_setting('app.household_id', true), '')::uuid);

ALTER TABLE warehouse_locations ENABLE ROW LEVEL SECURITY;
ALTER TABLE warehouse_locations FORCE ROW LEVEL SECURITY;
CREATE POLICY warehouse_locations_household_isolation ON warehouse_locations
    USING (household_id = nullif(current_setting('app.household_id', true), '')::uuid);

ALTER TABLE warehouse_lots ENABLE ROW LEVEL SECURITY;
ALTER TABLE warehouse_lots FORCE ROW LEVEL SECURITY;
CREATE POLICY warehouse_lots_household_isolation ON warehouse_lots
    USING (household_id = nullif(current_setting('app.household_id', true), '')::uuid);

ALTER TABLE warehouse_movements ENABLE ROW LEVEL SECURITY;
ALTER TABLE warehouse_movements FORCE ROW LEVEL SECURITY;
CREATE POLICY warehouse_movements_household_isolation ON warehouse_movements
    USING (household_id = nullif(current_setting('app.household_id', true), '')::uuid);
