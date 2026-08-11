-- Extensiones y funciones de apoyo del esquema.
--
-- Va en su propia migracion porque la extension tiene que existir antes que los
-- indices que dependen de ella, y porque instalarla exige permisos que el
-- usuario de la aplicacion no tiene: la ejecuta el propietario del esquema.

CREATE EXTENSION IF NOT EXISTS unaccent;

-- El envoltorio IMMUTABLE no es adorno: sin el, los tres indices unicos que
-- comparan nombres sin acentos --categories, articles y locations-- no se pueden
-- crear.
--
-- unaccent(text) es STABLE, no IMMUTABLE, porque resuelve el diccionario de
-- busqueda a partir de la configuracion de la sesion y esa puede cambiar entre
-- llamadas. PostgreSQL rechaza cualquier funcion no IMMUTABLE en la expresion de
-- un indice, con "functions in index expression must be marked IMMUTABLE": el
-- indice guarda el resultado ya calculado, asi que una funcion que pueda
-- devolver otra cosa manana lo dejaria mintiendo en silencio.
--
-- Fijar el diccionario de forma explicita con la variante de dos argumentos
-- elimina esa dependencia y la funcion pasa a ser IMMUTABLE de verdad, no
-- declarada a la fuerza. Cambiar el diccionario de 'public.unaccent' obligaria a
-- reconstruir los tres indices.
CREATE FUNCTION immutable_unaccent(text)
    RETURNS text
    LANGUAGE sql
    IMMUTABLE
    STRICT
    PARALLEL SAFE
    AS $$ SELECT public.unaccent('public.unaccent'::regdictionary, $1) $$;

COMMENT ON FUNCTION immutable_unaccent(text) IS
    'unaccent con el diccionario fijado, para poder usarse en indices. Ver V1__extensions.sql.';
