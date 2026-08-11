-- Resolucion de inquilino: lo unico que necesariamente precede al contexto.
--
-- El problema. Las politicas de RLS parten de que `app.household_id` ya esta
-- fijado, y ese valor sale del token. Pero hay tres momentos del core en los que
-- **todavia no se sabe cual es el hogar**, y son justo los momentos en los que
-- hay que averiguarlo:
--
--   1. El login. Se resuelve la identidad por su correo --`identities` no tiene
--      RLS, asi que eso funciona-- y despues hace falta saber a que hogar
--      pertenece para poder emitir el token. Esa pertenencia esta en
--      `household_members`, que si tiene politica: sin contexto, cero filas.
--   2. Aceptar una invitacion. Llega un token por correo y nada mas; quien lo
--      trae no pertenece aun a ningun hogar. La invitacion vive en `invitations`,
--      con politica.
--   3. Los tres procesos diarios, que no nacen de una peticion y tienen que
--      recorrer todos los hogares uno a uno.
--
-- En los tres casos la peticion falla cerrada --cero filas-- que es el
-- comportamiento correcto de la politica y a la vez impide arrancar.
--
-- La salida facil seria dar BYPASSRLS al usuario de la aplicacion. Eso desactiva
-- la segunda capa para TODA la aplicacion y no solo para estos tres momentos,
-- que es exactamente lo que la ADR-003 prohibe.
--
-- La salida elegida son tres funciones SECURITY DEFINER: se ejecutan con los
-- privilegios de su propietario --el del esquema-- en lugar de los de quien las
-- llama, asi que ven lo que la politica esconderia. Tres propiedades las hacen
-- aceptables donde BYPASSRLS no lo es:
--
--   * El permiso queda en tres funciones concretas y auditables, no en el rol.
--     El usuario de la aplicacion sigue siendo NOBYPASSRLS para todo lo demas.
--   * **Solo devuelven identificadores de hogar.** Ni un dato, ni una fila, ni
--     un correo. Lo que se haga despues con ese identificador vuelve a pasar por
--     la politica, porque el caso de uso fija `app.household_id` y trabaja como
--     cualquier peticion autenticada.
--   * Cada una responde a una pregunta cerrada. No hay ninguna con la que
--     recorrer datos ajenos.
--
-- El `SET search_path` no es opcional en una funcion SECURITY DEFINER: sin el,
-- quien la llame puede anteponer un esquema propio y hacer que el nombre de una
-- tabla resuelva a otra cosa, que se ejecutaria con los privilegios del
-- propietario.

-- Para que los procesos diarios recorran los hogares sin BYPASSRLS.
CREATE FUNCTION list_household_ids()
    RETURNS SETOF uuid
    LANGUAGE sql
    STABLE
    SECURITY DEFINER
    SET search_path = pg_catalog, public
    AS $$ SELECT id FROM public.households $$;

COMMENT ON FUNCTION list_household_ids() IS
    'Identificadores de todos los hogares, para los procesos diarios. Ver V4__tenant_resolution.sql.';

-- Para el login y la renovacion de sesion: en que hogar entra esta identidad.
--
-- Solo mira pertenencias vivas. Una persona que dejo el hogar no vuelve a entrar
-- por aqui, y mientras el MVP admita una sola pertenencia activa la respuesta es
-- como mucho una.
CREATE FUNCTION find_household_for_active_member(p_identity_id uuid)
    RETURNS uuid
    LANGUAGE sql
    STABLE
    SECURITY DEFINER
    SET search_path = pg_catalog, public
    AS $$
        SELECT household_id
        FROM public.household_members
        WHERE identity_id = p_identity_id
          AND deactivated_at IS NULL
        LIMIT 1
    $$;

COMMENT ON FUNCTION find_household_for_active_member(uuid) IS
    'Hogar de la pertenencia activa de una identidad, para poder emitir el token. Ver V4__tenant_resolution.sql.';

-- Para aceptar una invitacion: a que hogar corresponde este token.
--
-- Recibe el hash, nunca el token en claro, igual que la tabla. No comprueba
-- caducidad ni revocacion a proposito: eso es una regla de negocio y se decide
-- en el caso de uso, ya dentro del contexto del hogar y leyendo la fila
-- **por la politica**. Aqui solo se resuelve a donde mirar.
CREATE FUNCTION find_household_for_invitation_token(p_token_hash text)
    RETURNS uuid
    LANGUAGE sql
    STABLE
    SECURITY DEFINER
    SET search_path = pg_catalog, public
    AS $$
        SELECT household_id
        FROM public.invitations
        WHERE token_hash = p_token_hash
    $$;

COMMENT ON FUNCTION find_household_for_invitation_token(text) IS
    'Hogar al que corresponde un token de invitacion, para poder fijar el contexto antes de leerla. Ver V4__tenant_resolution.sql.';

-- Por defecto una funcion nueva es ejecutable por PUBLIC. Se retira y se concede
-- solo al usuario de la aplicacion, que es el unico que las necesita.
REVOKE ALL ON FUNCTION list_household_ids() FROM PUBLIC;
REVOKE ALL ON FUNCTION find_household_for_active_member(uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION find_household_for_invitation_token(text) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION list_household_ids() TO ${applicationRole};
GRANT EXECUTE ON FUNCTION find_household_for_active_member(uuid) TO ${applicationRole};
GRANT EXECUTE ON FUNCTION find_household_for_invitation_token(text) TO ${applicationRole};
