-- La baja de un hogar, con periodo de gracia (ADR-012).
--
-- No hay tabla nueva: la baja es un **estado del hogar** y no una entidad. Un
-- hogar tiene como mucho una baja viva, se pide y se cancela sobre la misma
-- fila, y guardarla aparte obligaria a mirar dos sitios para responder «esta
-- este hogar dandose de baja».
--
-- Tres columnas, y las tres juntas o ninguna:
--
--   * cuando se pidio,
--   * quien la pidio --la **pertenencia**, como toda la autoria del modelo--,
--   * y cuando vence la gracia, que es el instante a partir del cual el
--     recorrido diario puede purgar.
--
-- La fecha de vencimiento se **guarda** en lugar de calcularse al leer. No es
-- redundancia: el plazo es una decision de producto que puede cambiar, y
-- derivarlo cada noche haria que cambiarlo moviera hacia atras la fecha que ya
-- se le habia dicho a una persona. Lo que se le prometio a quien pidio la baja
-- es una fecha concreta, y esa fecha es un dato.

ALTER TABLE households
    ADD COLUMN closure_requested_at timestamptz,
    ADD COLUMN closure_requested_by uuid,
    ADD COLUMN closure_effective_at timestamptz;

-- La autoria apunta a la pertenencia y con la clave ajena compuesta, igual que
-- en todo el modelo. Aqui el `household_id` de la compuesta es la propia clave
-- primaria: en la tabla del hogar, el hogar es la fila.
--
-- La referencia es circular --household_members ya apunta a households-- y eso
-- esta bien: las dos columnas de la baja son anulables, asi que un hogar nace
-- sin nadie a quien apuntar y la pertenencia se crea despues. Al borrar el
-- hogar, la cascada se lleva sus pertenencias y esta restriccion se comprueba al
-- final de la sentencia, cuando la fila que referencia ya no existe.
ALTER TABLE households ADD CONSTRAINT households_closure_requested_by_member
    FOREIGN KEY (id, closure_requested_by) REFERENCES household_members (household_id, id);

-- Las tres van juntas. Media baja --pedida sin fecha de vencimiento, o vencida
-- sin que nadie la pidiera-- no es un estado que deba poder representarse, y sin
-- este CHECK bastaria un UPDATE parcial para dejar un hogar en la cola de purga
-- sin que conste quien lo pidio ni cuando.
ALTER TABLE households ADD CONSTRAINT households_closure_is_whole
    CHECK (num_nonnulls(closure_requested_at, closure_requested_by, closure_effective_at) IN (0, 3));

-- La gracia va hacia delante. Una fecha de vencimiento anterior a la solicitud
-- seria una baja ya vencida en el instante de pedirla, es decir, un borrado
-- inmediato por la puerta de atras --justo lo que la ADR-012 descarta.
ALTER TABLE households ADD CONSTRAINT households_closure_grace_forward
    CHECK (closure_effective_at IS NULL OR closure_effective_at > closure_requested_at);

-- La pregunta del recorrido diario es «que hogares han vencido», y es una
-- minoria diminuta: indice parcial, que ademas se vacia solo --la fila
-- desaparece al purgarla-- y no crece con los hogares que nunca piden la baja.
CREATE INDEX households_pending_closure
    ON households (closure_effective_at)
    WHERE closure_effective_at IS NOT NULL;

COMMENT ON COLUMN households.closure_effective_at IS
    'Instante a partir del cual PurgeClosedHouseholds puede borrar el hogar. Ver ADR-012.';

-- ---------------------------------------------------------------------------
-- La cuarta funcion de resolucion de inquilino
-- ---------------------------------------------------------------------------
--
-- La purga borra el hogar entero, y con el sus pertenencias. Eso deja
-- identidades que quiza no pertenezcan ya a ningun hogar, y esas hay que
-- resolverlas (ADR-012): una identidad sin ninguna pertenencia no sobrevive.
--
-- Para saberlo hace falta mirar **fuera del hogar en curso**, que es justo lo
-- que la politica impide y lo que la V4 resolvio para el login y las
-- invitaciones. Sin esto, el caso de uso solo ve las pertenencias del hogar que
-- esta borrando y daria por huerfana a una persona que sigue viviendo en otro
-- --y borrar su identidad arrastraria en cascada la pertenencia del hogar de al
-- lado, que es exactamente el dano que el aislamiento existe para impedir.
--
-- Cumple las tres propiedades de la familia, y por eso entra en ella en lugar de
-- ser una excepcion nueva:
--
--   * **Solo devuelve identificadores de hogar.** Ni un nombre, ni un correo, ni
--     una fila. Lo que se haga despues vuelve a pasar por la politica.
--   * Responde a una pregunta cerrada --«en que hogares consta esta persona»--
--     y no sirve para recorrer datos ajenos.
--   * Es de drp_resolver, que no es superusuario y no tiene BYPASSRLS, y se
--     apoya en la politica de SELECT que la V5 ya le abrio sobre
--     household_members.
--
-- A diferencia de find_household_for_active_member, cuenta **tambien las
-- pertenencias dadas de baja**: la pregunta no es donde puede entrar, sino donde
-- consta. Una pertenencia dada de baja sigue siendo historial de aquel hogar
-- --sus prestamos la referencian-- y borrar la identidad se la llevaria.
CREATE FUNCTION list_households_for_identity(p_identity_id uuid)
    RETURNS SETOF uuid
    LANGUAGE sql
    STABLE
    SECURITY DEFINER
    SET search_path = pg_catalog, public
    AS $$
        SELECT household_id
        FROM public.household_members
        WHERE identity_id = p_identity_id
    $$;

ALTER FUNCTION list_households_for_identity(uuid) OWNER TO drp_resolver;

COMMENT ON FUNCTION list_households_for_identity(uuid) IS
    'Hogares en los que consta una identidad, activa o no, para decidir si queda huerfana al purgar uno. Ver V14__household_closure.sql.';

REVOKE ALL ON FUNCTION list_households_for_identity(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION list_households_for_identity(uuid) TO ${applicationRole};
