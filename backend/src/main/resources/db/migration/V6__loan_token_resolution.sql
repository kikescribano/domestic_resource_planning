-- El cuarto momento en el que todavia no se sabe cual es el hogar.
--
-- La V4 enumero tres --el login, la aceptacion de una invitacion y los procesos
-- diarios-- y este es el mismo problema por cuarta vez: quien llega con el token
-- acotado de un prestamo es una persona ajena al hogar, sin cuenta y sin sesion,
-- asi que no hay claim del que sacar `app.household_id`. Sin contexto, la
-- politica de `loans` devuelve cero filas y la peticion no puede ni empezar.
--
-- El caso tiene ademas una vuelta propia: `loan_access_tokens` **no lleva
-- household_id y por tanto no tiene politica**. Cuelga del prestamo, igual que
-- los tokens de correo cuelgan de la identidad. Asi que el token se puede leer
-- sin contexto, pero el hogar no esta en su fila: hay que ir al prestamo, que si
-- esta protegido. De ahi que la funcion tenga que unir las dos tablas.
--
-- La regla de la V4 sigue en pie y es lo unico que hace aceptable esta grieta:
-- **la funcion devuelve un identificador de hogar y nada mas**. Ni el prestamo,
-- ni el rol, ni la caducidad, ni si el token existia. Todo eso se lee despues,
-- ya dentro del contexto y **por la politica**, como cualquier peticion
-- autenticada. Una funcion que devolviera la fila entera no estaria resolviendo
-- el aislamiento sino esquivandolo, y no habria diferencia practica con
-- BYPASSRLS.
--
-- Recibe el hash y nunca el token en claro, igual que la tabla.
--
-- Y resuelve **por token**, no por el `loanId` que el JWT lleva en un claim.
-- Podria hacerse lo segundo --el claim viene firmado por nosotros-- y seria peor:
-- convertiria la funcion en un oraculo al que preguntar de que hogar es
-- cualquier prestamo. Tal como esta, solo responde a quien ya trae un secreto
-- que existe en la tabla, que es la misma forma que
-- find_household_for_invitation_token.
--
-- No comprueba caducidad, ni uso, ni que el prestamo siga abierto: eso son
-- reglas de negocio y se deciden en el caso de uso. Aqui solo se resuelve a
-- donde mirar.
CREATE FUNCTION find_household_for_loan_token(p_token_hash text)
    RETURNS uuid
    LANGUAGE sql
    STABLE
    SECURITY DEFINER
    SET search_path = pg_catalog, public
    AS $$
        SELECT l.household_id
        FROM public.loan_access_tokens t
        JOIN public.loans l ON l.id = t.loan_id
        WHERE t.token_hash = p_token_hash
    $$;

COMMENT ON FUNCTION find_household_for_loan_token(text) IS
    'Hogar al que corresponde un token acotado de prestamo, para poder fijar el contexto antes de leerlo. Ver V6__loan_token_resolution.sql.';

-- Suya, como las otras tres: a partir de aqui se ejecuta como drp_resolver, que
-- NO es superusuario y NO tiene BYPASSRLS. La V5 explica por que ser propietario
-- del esquema no bastaba --FORCE ROW LEVEL SECURITY aplica la politica tambien
-- al dueno de la tabla-- y esta funcion nace ya con la leccion aprendida.
ALTER FUNCTION find_household_for_loan_token(text) OWNER TO drp_resolver;

-- Privilegio de tabla, que es cosa aparte de RLS y hace falta igualmente.
GRANT SELECT ON loans TO drp_resolver;
GRANT SELECT ON loan_access_tokens TO drp_resolver;

-- Y la politica que lo deja pasar por `loans`. Estrecha como las tres de la V5:
-- FOR SELECT, TO drp_resolver y sobre esta tabla y ninguna mas.
--
-- `loan_access_tokens` no necesita politica porque no tiene RLS activado (V3):
-- ahi el GRANT de arriba es todo lo que hacia falta.
CREATE POLICY loans_tenant_resolution ON loans
    FOR SELECT TO drp_resolver USING (true);

REVOKE ALL ON FUNCTION find_household_for_loan_token(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION find_household_for_loan_token(text) TO ${applicationRole};
