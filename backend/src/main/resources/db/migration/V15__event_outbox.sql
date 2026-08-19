-- El Transactional Outbox (ADR-013).
--
-- La tercera tabla de plataforma, y la unica que no guarda nada que nadie vaya a
-- leer: es una **cola**, no un archivo. Una fila significa «este evento se
-- publico y todavia no consta que se haya repartido». Cuando consta, la fila se
-- borra.
--
-- Que exista es lo unico que hace que un evento no se pierda. Hasta hoy el bus
-- era in-process y sin estado: entre el `COMMIT` del caso de uso y el
-- `AFTER_COMMIT` que reparte el evento hay una ventana en la que **caerse
-- significa perderlo**, sin error, sin traza y sin nada que lo delate salvo un
-- modulo que no se entero. La fila se escribe **dentro de la transaccion del
-- caso de uso**, asi que o se guardan las dos cosas o no se guarda ninguna.
--
-- **Lo que esto NO cambia es la garantia.** La entrega sigue siendo
-- at-least-once y los handlers siguen teniendo que ser idempotentes. Lo que el
-- outbox anade es que el evento **no se pierda**, no que llegue una sola vez.
-- Retirarle la idempotencia a un handler porque «ya hay outbox» es el error caro
-- que la ADR-013 nombra con todas las letras.
--
-- Lleva `household_id`, RLS y `FORCE` como cualquier tabla del modelo. Aqui la
-- politica hace el mismo trabajo que en `household_notices` y por el mismo
-- motivo: **quien lee estas filas no nace de una peticion**. El relay no tiene
-- token del que sacar el hogar y va fijando `app.household_id` hogar a hogar,
-- exactamente como el recorrido diario, y nunca con `BYPASSRLS`.
--
-- Lo que **no** lleva, como los avisos de la V8:
--
--   * `created_by` y `updated_by`. Un evento no lo firma nadie: lo publica el
--     caso de uso, y la autoria de lo que ocurrio ya esta en la fila que lo
--     origino. `created_by` valdria nulo siempre.
--   * `updated_at`. **Una fila del outbox no se modifica**: nace pendiente y
--     desaparece al confirmarse. No hay ningun estado intermedio que guardar, y
--     esa es justamente la decision de la ADR-013 sobre que hacer con la fila ya
--     entregada.
--   * Ninguna clave ajena hacia lo que el evento describe. Un evento es lo que
--     ocurrio, no una vista de la fila que sigue viva: apuntarla obligaria
--     ademas a plataforma a conocer el esquema del core y de cada modulo, que es
--     la frontera que la ADR-010 fija.

CREATE TABLE event_outbox (
    -- La clave primaria **es el `eventId`**, no una fila con vida propia. Es lo
    -- que convierte «recordar dos veces el mismo evento» en la misma fila --ver
    -- el ON CONFLICT del adaptador-- y lo que deja que la fila sea la guarda
    -- duradera de ese identificador.
    event_id     uuid PRIMARY KEY,
    household_id uuid        NOT NULL REFERENCES households (id) ON DELETE CASCADE,
    -- El sobre del evento, tal cual lo define README 5.2.1.
    type         text        NOT NULL,
    occurred_at  timestamptz NOT NULL,
    aggregate_id text        NOT NULL,
    version      integer     NOT NULL,
    -- El `payload` va como `jsonb` por lo mismo que va como mapa en el codigo
    -- (ver `DomainEvent`): el catalogo de eventos todavia no tiene una clase por
    -- tipo, y congelar aqui una columna por campo seria fijar una forma que
    -- nadie ha usado. `version` esta para el dia que eso cambie.
    payload      jsonb       NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now()
);

-- La unica consulta del relay dentro de un hogar: lo pendiente, de lo mas
-- antiguo a lo mas nuevo, y **solo lo que ya deberia haber llegado** --de ahi el
-- corte por `created_at`, que la ADR-013 llama periodo de gracia.
--
-- No es un indice parcial como los de la V8 o la V14, y no hace falta que lo
-- sea: aqui **toda** la tabla es lo pendiente. Su estado normal es vacia.
CREATE INDEX event_outbox_pending
    ON event_outbox (household_id, created_at);

COMMENT ON TABLE event_outbox IS
    'Cola de eventos publicados y pendientes de repartir. Su estado normal es vacia; que crezca es el sintoma. Ver ADR-013.';

-- Segunda capa, con la misma forma que la V3, la V7 y la V8: `nullif` para que
-- la sesion sin contexto falle CERRADA, y sin WITH CHECK propio para que la
-- expresion de USING gobierne tambien las escrituras --lo que aqui impide que un
-- caso de uso del hogar A deje un evento a nombre del hogar B.
ALTER TABLE event_outbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE event_outbox FORCE ROW LEVEL SECURITY;
CREATE POLICY event_outbox_household_isolation ON event_outbox
    USING (household_id = nullif(current_setting('app.household_id', true), '')::uuid);

-- ---------------------------------------------------------------------------
-- La sexta funcion de resolucion de inquilino
-- ---------------------------------------------------------------------------
--
-- El relay corre cada pocos segundos y tiene que empezar por saber **a que
-- hogares entrar**. Sin esto solo tiene dos salidas y las dos son malas:
-- recorrer los mil hogares de la instalacion en vacio cada pocos segundos, o
-- pedirle a `list_household_ids()` la lista entera y hacer exactamente lo mismo.
-- Lo que hace falta es la pregunta estrecha: **quien tiene algo pendiente**.
--
-- Cumple las tres propiedades de la familia, y por eso entra en ella en lugar de
-- ser una excepcion nueva:
--
--   * **Solo devuelve identificadores de hogar.** Ni un evento, ni un `payload`,
--     ni una fila. Lo que el relay lea despues vuelve a pasar por la politica,
--     porque entra en el hogar fijando `app.household_id` como cualquier
--     peticion autenticada.
--   * Responde a una pregunta cerrada --«quien tiene entregas pendientes»-- y no
--     sirve para recorrer datos ajenos.
--   * Es de drp_resolver, que no es superusuario y no tiene BYPASSRLS.
--
-- La condicion de admision no ha cambiado desde la V4 y conviene repetirla: una
-- funcion que devolviera **un dato** dejaria de ser una grieta acotada para ser
-- un agujero. Y `RowLevelSecurityTest` enumera a mano las que existen, asi que
-- esta cuesta una linea alli --que es exactamente lo que se pretende.
--
-- El corte por fecha es **parametro y no constante**: el periodo de gracia es
-- configuracion de despliegue (`drp.outbox.grace`), y grabarlo en el esquema
-- obligaria a una migracion para moverlo.
CREATE FUNCTION list_households_with_pending_events(p_created_before timestamptz)
    RETURNS SETOF uuid
    LANGUAGE sql
    STABLE
    SECURITY DEFINER
    SET search_path = pg_catalog, public
    AS $$
        SELECT DISTINCT household_id
        FROM public.event_outbox
        WHERE created_at < p_created_before
    $$;

ALTER FUNCTION list_households_with_pending_events(timestamptz) OWNER TO drp_resolver;

COMMENT ON FUNCTION list_households_with_pending_events(timestamptz) IS
    'Hogares con eventos pendientes de repartir, para que el relay no recorra en vacio. Ver V15__event_outbox.sql.';

-- Privilegio de tabla: separado de RLS y necesario igualmente. Solo lectura, y
-- solo sobre la tabla que la funcion consulta.
GRANT SELECT ON event_outbox TO drp_resolver;

-- Y la politica que lo deja pasar, deliberadamente estrecha como las tres de la
-- V5: `FOR SELECT`, `TO drp_resolver` y sobre esta tabla y ninguna mas. Asi la
-- excepcion se lee en `pg_policies` junto a las demas, en vez de esconderse en
-- una propiedad del rol que nadie mira.
CREATE POLICY event_outbox_tenant_resolution ON event_outbox
    FOR SELECT TO drp_resolver USING (true);

REVOKE ALL ON FUNCTION list_households_with_pending_events(timestamptz) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION list_households_with_pending_events(timestamptz) TO ${applicationRole};
