-- Los avisos del hogar (ADR-011).
--
-- La segunda tabla de plataforma, y la primera que guarda algo que una persona
-- lee. Nace del recorrido periodico: cada comprobacion --del core hoy, de
-- Warehouse y CMMS manana-- devuelve lo que ha encontrado y plataforma lo
-- escribe aqui. **Plataforma pone el sitio y el canal; la regla de que se avisa
-- y cuando es de cada modulo**, que es la decision que la seccion 4.2 del README
-- tomo al descartar un modulo de avisos centralizado.
--
-- Lleva household_id, RLS y FORCE como cualquier tabla del core: un aviso dice
-- que le pasa a un hogar --que se le caduca, que tiene un prestamo vencido-- y
-- eso es un dato suyo.
--
-- Lo que **no** lleva, y es una diferencia deliberada con el resto del modelo:
--
--   * `created_by` y `updated_by`. Ningun aviso lo crea una persona: los crea el
--     recorrido periodico, asi que `created_by` seria una columna que vale nulo
--     siempre. Y la unica modificacion posible es marcarlo leido, que ya tiene
--     su fecha y su autor con nombre propio --`read_at` y `read_by`--, mas
--     legibles que un `updated_by` que solo puede significar una cosa.
--   * Ninguna clave ajena hacia lo que el aviso describe. Un aviso es **el texto
--     que se escribio ese dia**, no una vista de la fila que lo origino: si el
--     prestamo se devuelve, el aviso de que vencio siguio siendo cierto. Y
--     apuntar a la fila de un modulo obligaria a plataforma a conocer su
--     esquema, que es la frontera que la ADR-010 fija.
--
-- `module_key` a nulo significa **del core**, que es lo que hace que un aviso de
-- prestamo vencido siga apareciendo en un hogar sin ningun modulo encendido. No
-- hay clave ajena contra `household_modules`: el catalogo vive en codigo por lo
-- que la V7 explica, y un aviso tiene que sobrevivir a que su modulo se apague
-- --sus datos se conservan, y esto es uno de ellos.

CREATE TABLE household_notices (
    id           uuid PRIMARY KEY,
    household_id uuid        NOT NULL REFERENCES households (id) ON DELETE CASCADE,
    -- Nulo = del core. Con valor, la clave del modulo cuya regla lo produjo.
    module_key   text,
    -- Que clase de aviso es. Es un identificador --lo usa el cliente para
    -- agrupar o para elegir un icono-- asi que va en ingles y en
    -- UPPER_SNAKE_CASE, como los codigos de error y las claves de modulo.
    kind         text        NOT NULL,
    -- El titulo y el cuerpo son **datos**: los lee una persona, van en
    -- castellano y los escribe quien conoce la regla.
    title        text        NOT NULL,
    body         text        NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    -- Leido por el hogar, no por cada persona. Un hogar es un grupo pequeno que
    -- comparte la bandeja igual que comparte el inventario; un estado por
    -- persona multiplicaria las filas por miembro para responder una pregunta
    -- que nadie se hace.
    read_at      timestamptz,
    read_by      uuid,
    -- Cuando entro en un resumen diario. Nulo significa **pendiente de
    -- entregar**, y es lo que hace que el resumen no repita lo de ayer.
    notified_at  timestamptz,
    CONSTRAINT household_notices_kind_shape
        CHECK (kind ~ '^[A-Z][A-Z0-9_]*$'),
    CONSTRAINT household_notices_module_key_shape
        CHECK (module_key IS NULL OR module_key ~ '^[A-Z][A-Z0-9_]*$'),
    -- Leido sin quien lo leyo seria un dato a medias, y quien lo leyo sin fecha
    -- no significa nada. O las dos o ninguna.
    CONSTRAINT household_notices_read_is_whole
        CHECK ((read_at IS NULL) = (read_by IS NULL))
);

-- La autoria apunta a la **pertenencia** y no a la identidad, como en todo el
-- core, y con la clave ajena compuesta que impide atribuir la lectura a alguien
-- de otro hogar.
ALTER TABLE household_notices ADD CONSTRAINT household_notices_read_by_same_household
    FOREIGN KEY (household_id, read_by) REFERENCES household_members (household_id, id);

-- La bandeja se lee siempre igual: los del hogar, del mas reciente al mas
-- antiguo. El indice es el de esa consulta.
CREATE INDEX household_notices_by_recency
    ON household_notices (household_id, created_at DESC);

-- Y el resumen diario pregunta por lo pendiente de entregar, que es una minoria
-- diminuta de la tabla: indice parcial, que ademas deja de crecer en cuanto el
-- resumen se ha mandado.
CREATE INDEX household_notices_pending_digest
    ON household_notices (household_id, created_at)
    WHERE notified_at IS NULL;

-- Segunda capa, con la misma forma que la V3 y la V7: `nullif` para que la
-- sesion sin contexto falle CERRADA, y sin WITH CHECK propio para que la
-- expresion de USING gobierne tambien las escrituras.
--
-- Aqui importa mas que en ninguna otra tabla, porque **quien escribe estas filas
-- no nace de una peticion**: el recorrido periodico no tiene token del que sacar
-- el hogar y va fijando `app.household_id` hogar a hogar. La politica es lo que
-- convierte un fallo de ese recorrido en cero filas en lugar de un aviso en el
-- buzon del vecino.
ALTER TABLE household_notices ENABLE ROW LEVEL SECURITY;
ALTER TABLE household_notices FORCE ROW LEVEL SECURITY;
CREATE POLICY household_notices_household_isolation ON household_notices
    USING (household_id = nullif(current_setting('app.household_id', true), '')::uuid);
