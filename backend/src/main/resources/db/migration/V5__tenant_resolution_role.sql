-- La excepcion de aislamiento, expresada dentro del propio mecanismo de RLS.
--
-- La V4 dejo escrito que las tres funciones de resolucion de inquilino "se
-- ejecutan con los privilegios de su propietario --el del esquema-- en lugar de
-- los de quien las llama, asi que ven lo que la politica esconderia". **Eso era
-- falso**, y conviene dejar constancia de por que, porque es un error facil de
-- repetir:
--
--   SECURITY DEFINER cambia el `current_user` al propietario de la funcion, si.
--   Pero las politicas de la V3 llevan FORCE ROW LEVEL SECURITY, que existe
--   precisamente para que la politica se aplique TAMBIEN al propietario de la
--   tabla. Las dos cosas juntas significan que ser dueno del esquema no da
--   ninguna visibilidad extra: la funcion devolveria cero filas.
--
-- Si hasta ahora funcionaban era por una coincidencia del entorno, no por el
-- diseno: drp_owner es el usuario de arranque del contenedor de PostgreSQL y por
-- tanto **superusuario**, y un superusuario se salta RLS pase lo que pase.
--
-- Apoyarse en eso tiene dos problemas, y el segundo es el peligroso:
--
--   1. La unica grieta deliberada del aislamiento correria con privilegios de
--      superusuario. Hoy el dano estaria acotado --los tres cuerpos son SQL fijo
--      y llevan search_path-- pero la cuarta funcion que alguien anadiese con
--      cuerpo dinamico pasaria de "ve identificadores de hogar" a comprometer la
--      base entera.
--   2. Endurecer drp_owner a rol normal, que es hacia donde empuja la ADR-003,
--      romperia el sistema **en silencio**: el login fallaria de forma visible,
--      pero list_household_ids() devolveria el conjunto vacio y los procesos
--      diarios recorrerian cero hogares sin error, sin log y sin traza.
--
-- La solucion no es dar mas privilegios a nadie sino **decir la excepcion en el
-- idioma de RLS**: un rol propio, sin login y sin BYPASSRLS, y una politica por
-- tabla que le abre la puerta de forma explicita y solo para SELECT. Asi la
-- excepcion se lee en `pg_policies` junto a las demas, en vez de esconderse en
-- una propiedad del rol que nadie mira.

-- Las tres funciones pasan a ser suyas. A partir de aqui se ejecutan como
-- drp_resolver, que NO es superusuario y NO tiene BYPASSRLS.
ALTER FUNCTION list_household_ids() OWNER TO drp_resolver;
ALTER FUNCTION find_household_for_active_member(uuid) OWNER TO drp_resolver;
ALTER FUNCTION find_household_for_invitation_token(text) OWNER TO drp_resolver;

-- Privilegio de tabla: separado de RLS y necesario igualmente. Solo lectura, y
-- solo sobre las tres tablas que las funciones consultan.
GRANT SELECT ON households TO drp_resolver;
GRANT SELECT ON household_members TO drp_resolver;
GRANT SELECT ON invitations TO drp_resolver;

-- Y las politicas que lo dejan pasar. Son deliberadamente estrechas: `FOR
-- SELECT`, `TO drp_resolver` y sobre estas tres tablas y ninguna mas. El resto
-- del esquema le sigue estando vedado igual que a cualquiera, y `drp_app`
-- --con el que corre toda la aplicacion-- no cambia en nada.
CREATE POLICY households_tenant_resolution ON households
    FOR SELECT TO drp_resolver USING (true);

CREATE POLICY household_members_tenant_resolution ON household_members
    FOR SELECT TO drp_resolver USING (true);

CREATE POLICY invitations_tenant_resolution ON invitations
    FOR SELECT TO drp_resolver USING (true);

-- Nadie inicia sesion como drp_resolver: es NOLOGIN. Solo se llega a el
-- atravesando una de las tres funciones, cuyo EXECUTE sigue revocado de PUBLIC y
-- concedido unicamente al usuario de la aplicacion.
COMMENT ON ROLE drp_resolver IS
    'Dueno de las funciones de resolucion de inquilino. Sin login, sin superusuario y sin BYPASSRLS: solo ve lo que sus tres politicas de SELECT le dejan ver. Ver V5__tenant_resolution_role.sql.';
