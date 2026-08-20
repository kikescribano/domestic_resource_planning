-- Juego de datos de demostración: un hogar español completo, con histórico.
--
-- Carga de una sentada un hogar de cuatro personas --dos adultas y dos menores--
-- con vivienda y trastero, el inventario y la despensa que tendría una casa
-- corriente, y datos para el core y para **los cuatro módulos de la Fase 2**. La
-- idea es abrir la aplicación recién levantada y verla entera sin teclear nada,
-- tantas veces como haga falta.
--
-- Cómo se ejecuta (con la base ya migrada, es decir, tras arrancar el backend al
-- menos una vez; ver .claude/skills/run-local):
--
--   docker compose exec -T -e PGPASSWORD=drp_app postgres \
--       psql -U drp_app -d drp -v ON_ERROR_STOP=1 < scripts/seed-demo-data.sql
--
-- Tres decisiones que conviene entender antes de tocarlo:
--
-- 1. **Se ejecuta como `drp_app`, no como el propietario del esquema.** Es el
--    usuario de la aplicación, sujeto a Row-Level Security y sin `BYPASSRLS`
--    (ADR-003), así que este fichero fija `app.household_id` como haría
--    cualquier petición. No es ceremonia: es lo que garantiza que lo que se
--    siembra aquí es exactamente lo que la aplicación podría haber escrito. Con
--    `drp_owner` --que en el compose local es superusuario-- las políticas no se
--    aplicarían y este fichero podría sembrar lo que la aplicación no permite.
--
-- 2. **Los identificadores son deterministas**, derivados del nombre natural de
--    cada fila con `md5()`. Así el fichero no lleva trescientos UUID escritos a
--    mano, las claves ajenas se resuelven sin consultar nada, y volver a
--    ejecutarlo reconstruye el mismo hogar con los mismos identificadores --de
--    modo que un enlace guardado del navegador sigue valiendo--.
--
-- 3. **Todas las fechas son relativas a `now()`.** No hay ni una fecha fija: las
--    caducidades, los vencimientos de préstamo y las próximas revisiones se
--    calculan al cargar. Un juego de datos con fechas absolutas envejece, y a los
--    seis meses enseña una despensa entera caducada.
--
-- Es **idempotente**: empieza borrando su propio hogar --y solo el suyo-- así que
-- se puede volver a lanzar cuantas veces se quiera. No toca ningún otro hogar de
-- la instalación.
--
-- Las contraseñas van como hash de Argon2id, que es lo que guarda la aplicación
-- (`SecurityConfig`). Las cuatro cuentas comparten la contraseña
-- `DemoDRP2026Local`, que está escrita aquí a propósito: esto es una demostración
-- local y ningún dato de este fichero corresponde a nadie real.

BEGIN;

-- Identificadores deterministas a partir del nombre natural de cada fila. Va en
-- el esquema temporal, que muere con la sesión: el fichero no deja nada instalado.
CREATE FUNCTION pg_temp.demo_id(clave text) RETURNS uuid
    LANGUAGE sql IMMUTABLE STRICT
    AS 'SELECT md5(''drp-demo:'' || $1)::uuid';

-- El contexto de inquilino, igual que lo fija cualquier transacción de la
-- aplicación. Sin esto, RLS rechaza hasta el primer INSERT.
SELECT set_config('app.household_id', pg_temp.demo_id('hogar')::text, false);

-- Borrado de la carga anterior. El hogar arrastra en cascada todo lo suyo; las
-- identidades no, porque no pertenecen a ningún hogar (README 4.1.4) y hay que
-- nombrarlas una a una.
DELETE FROM households WHERE id = pg_temp.demo_id('hogar');
DELETE FROM identities WHERE email IN (
    'marta@hogar-serrano.test',
    'javier@hogar-serrano.test',
    'lucia@hogar-serrano.test',
    'hugo@hogar-serrano.test'
);


-- =====================================================================
-- 1. El hogar y las cuatro personas
-- =====================================================================
--
-- Persona y papel van separados (README 4.1.4): `identities` son las credenciales
-- y `household_members` la pertenencia. Todo lo que el dominio llama «usuario»
-- --propietario, prestador, autoría-- apunta a la pertenencia.

INSERT INTO households (id, name, time_zone, created_at, updated_at)
VALUES (
    pg_temp.demo_id('hogar'),
    'Familia Serrano Ruiz',
    'Europe/Madrid',
    now() - interval '14 months',
    now() - interval '14 months'
);

INSERT INTO identities (id, name, email, phone, password_hash, email_verified_at, last_login_at, created_at, updated_at)
SELECT pg_temp.demo_id('identidad:' || v.clave), v.nombre, v.correo, v.telefono, v.hash,
       now() - interval '14 months',
       now() - (v.ultimo_acceso || ' hours')::interval,
       now() - interval '14 months', now() - interval '14 months'
FROM (VALUES
    ('marta',  'Marta Ruiz Alonso',  'marta@hogar-serrano.test',  '+34 611 22 33 44',
     '{argon2}$argon2id$v=19$m=19456,t=2,p=1$f23gAPDOcWa7v33yD0WWwQ$tZKjit5Oa+z27f1F5koGDsl2pXS/OevTct3ZiVS1kdA', '3'),
    ('javier', 'Javier Serrano Gil', 'javier@hogar-serrano.test', '+34 622 33 44 55',
     '{argon2}$argon2id$v=19$m=19456,t=2,p=1$VSP/NmoLEdtHp4zZR9tHjw$5RRJqTcA7Z5vY8rDoDu5vdJ6Z5rjIGVoK5tg42axyxY', '30'),
    ('lucia',  'Lucía Serrano Ruiz', 'lucia@hogar-serrano.test',  NULL,
     '{argon2}$argon2id$v=19$m=19456,t=2,p=1$KnxZHCmoR2WD1+hQAS1bpA$bQbt6Mx/JlWS1Remq2PCXDjPy7RniQSpvhey84xo8oM', '80'),
    ('hugo',   'Hugo Serrano Ruiz',  'hugo@hogar-serrano.test',   NULL,
     '{argon2}$argon2id$v=19$m=19456,t=2,p=1$D8zC8aWauqEH1LV36IFDCQ$kcTdlX1OWc0iH4FmRihIUCZXYm7Dw5SFtnWsh+jcDHY', '400')
) AS v(clave, nombre, correo, telefono, hash, ultimo_acceso);

-- Marta dio de alta el hogar, así que su pertenencia no tiene autoría --la creó
-- el sistema y no una persona-- y las otras tres las crea ella.
INSERT INTO household_members (id, household_id, identity_id, role, created_at, updated_at, created_by, updated_by)
SELECT pg_temp.demo_id('miembro:' || v.clave), pg_temp.demo_id('hogar'),
       pg_temp.demo_id('identidad:' || v.clave), v.papel,
       now() - (v.antiguedad || ' months')::interval,
       now() - (v.antiguedad || ' months')::interval,
       CASE WHEN v.clave = 'marta' THEN NULL ELSE pg_temp.demo_id('miembro:marta') END,
       CASE WHEN v.clave = 'marta' THEN NULL ELSE pg_temp.demo_id('miembro:marta') END
FROM (VALUES
    ('marta',  'HOUSEHOLD_ADMIN',  '14'),
    ('javier', 'HOUSEHOLD_ADMIN',  '14'),
    ('lucia',  'HOUSEHOLD_MEMBER', '13'),
    ('hugo',   'HOUSEHOLD_MEMBER', '13')
) AS v(clave, papel, antiguedad);


-- =====================================================================
-- 2. Categorías
-- =====================================================================
--
-- Las cinco que siembra el alta de un hogar (`DEFAULT_CATEGORIES`) sin autoría,
-- porque las pone el sistema, más las que esta casa ha ido añadiendo. Sus nombres
-- son datos que se leen en pantalla, así que van en castellano.
--
-- **Las doce llevan icono y color** desde el Hito 4 del cierre de huecos, y no
-- por adorno: la pantalla del catálogo y las filas del inventario existen para
-- enseñar la identidad visual, y sin ella el hogar de demostración las enseñaría
-- vacías. Los iconos son los del juego cerrado (ADR-015) y **el color se repite
-- a propósito** --seis colores para doce categorías-- porque eso es exactamente
-- lo que el color hace: agrupar. Lo que identifica es el nombre.
--
-- Se agrupan por afinidad: lo que se enchufa en índigo, lo de comer y cocinar en
-- musgo, lo de limpiar y asear en turquesa, el taller y el deporte en cielo, y
-- lo que se ve en rosa y ciruela.

INSERT INTO categories (id, household_id, name, notes, icon, color,
                        created_at, updated_at, created_by, updated_by)
SELECT pg_temp.demo_id('categoria:' || v.clave), pg_temp.demo_id('hogar'), v.nombre, v.notas,
       v.icono, v.color,
       now() - (v.antiguedad || ' months')::interval,
       now() - (v.antiguedad || ' months')::interval,
       CASE WHEN v.sembrada THEN NULL ELSE pg_temp.demo_id('miembro:marta') END,
       CASE WHEN v.sembrada THEN NULL ELSE pg_temp.demo_id('miembro:marta') END
FROM (VALUES
    ('mobiliario',        'Mobiliario',           NULL,                            'SOFA',     'PLUM',   true,  '14'),
    ('alimentacion',      'Alimentación',         NULL,                            'UTENSILS', 'MOSS',   true,  '14'),
    ('limpieza',          'Limpieza',             NULL,                            'SPRAY',    'TEAL',   true,  '14'),
    ('herramientas',      'Herramientas',         NULL,                            'TOOL',     'SKY',    true,  '14'),
    ('decoracion',        'Decoración',           NULL,                            'FRAME',    'ROSE',   true,  '14'),
    ('electrodomesticos', 'Electrodomésticos',    'Lo que se enchufa y se avería', 'PLUG',     'INDIGO', false, '13'),
    ('menaje',            'Menaje y cocina',      NULL,                            'POT',      'MOSS',   false, '13'),
    ('higiene',           'Higiene y salud',      'Baño y botiquín',               'PILL',     'TEAL',   false, '13'),
    ('electronica',       'Electrónica',          NULL,                            'MONITOR',  'INDIGO', false, '12'),
    ('textil',            'Ropa y textil',        NULL,                            'SHIRT',    'PLUM',   false, '11'),
    ('deporte',           'Deporte y aire libre', NULL,                            'BIKE',     'SKY',    false, '10'),
    -- La única sin color a propósito: nulo es un valor con significado --nadie lo
    -- eligió-- y una demostración en la que todo está relleno no enseña cómo se
    -- ve el caso normal de una categoría recién creada.
    ('papeleria',         'Papelería y escolar',  'Se dispara cada septiembre',    'PENCIL',   NULL,     false, '9')
) AS v(clave, nombre, notas, icono, color, sembrada, antiguedad);


-- =====================================================================
-- 2 bis. Etiquetas
-- =====================================================================
--
-- El vocabulario propio de esta casa, que es lo que la categoría no puede dar
-- porque la categoría es una: la tienda de campaña es de Deporte, y a la vez es
-- *camping* y *se presta*.
--
-- Seis, que es lo que una casa real acumula en un año: ni dos --que no enseñan
-- el filtro-- ni treinta, que serían un catálogo inventado. Una está **retirada**
-- a propósito, porque retirada es un estado del modelo que ninguna pantalla
-- enseña si nadie lo siembra: sigue puesta en lo que la llevaba y deja de
-- ofrecerse al etiquetar.

INSERT INTO tags (id, household_id, name, created_at, updated_at, retired_at, created_by, updated_by)
SELECT pg_temp.demo_id('etiqueta:' || v.clave), pg_temp.demo_id('hogar'), v.nombre,
       now() - (v.antiguedad || ' months')::interval,
       now() - (v.antiguedad || ' months')::interval,
       CASE WHEN v.retirada THEN now() - interval '2 months' ELSE NULL END,
       pg_temp.demo_id('miembro:' || v.quien),
       pg_temp.demo_id('miembro:' || v.quien)
FROM (VALUES
    ('camping',    'Camping',            'javier', false, '11'),
    ('prestable',  'Se presta',          'marta',  false, '10'),
    ('heredado',   'Heredado',           'marta',  false, '12'),
    ('reparar',    'Para reparar',       'javier', false, '4'),
    ('cole',       'Cosas del cole',     'lucia',  false, '9'),
    ('mudanza',    'Mudanza 2025',       'marta',  true,  '13')
) AS v(clave, nombre, quien, retirada, antiguedad);


-- =====================================================================
-- 3. Ubicaciones
-- =====================================================================
--
-- Dos raíces: la vivienda y el trastero del edificio, que no está dentro de ella.
-- El nombre solo es único entre hermanas, así que dos armarios podrían llamarse
-- igual en cuartos distintos; aquí se distinguen a propósito para que las listas
-- se lean sin contexto.

INSERT INTO locations (id, household_id, name, type, parent_location_id, capacity, environmental_conditions, notes, created_at, updated_at, created_by, updated_by)
SELECT pg_temp.demo_id('ubicacion:' || v.clave), pg_temp.demo_id('hogar'), v.nombre, v.tipo,
       CASE WHEN v.padre IS NULL THEN NULL ELSE pg_temp.demo_id('ubicacion:' || v.padre) END,
       v.capacidad::jsonb, v.ambiente::jsonb, v.notas,
       now() - interval '14 months', now() - interval '14 months',
       pg_temp.demo_id('miembro:marta'), pg_temp.demo_id('miembro:marta')
FROM (VALUES
    ('vivienda',       'Vivienda',                 'HOUSE',     NULL,         NULL,                                    NULL,                                                                'Piso de 95 m², tercero exterior'),
    ('cocina',         'Cocina',                   'ROOM',      'vivienda',   NULL,                                    NULL,                                                                NULL),
    ('despensa',       'Despensa',                 'FURNITURE', 'cocina',     '{"type":"UNITS","max":80,"unit":"ud"}', '{"maxTemperature":24,"lightExposure":"DARKNESS"}',                   'El armario alto de la izquierda'),
    ('armario-cocina', 'Armario bajo de la cocina','FURNITURE', 'cocina',     NULL,                                    NULL,                                                                'Debajo del fregadero'),
    ('salon',          'Salón',                    'ROOM',      'vivienda',   NULL,                                    NULL,                                                                NULL),
    ('mueble-salon',   'Mueble del salón',         'FURNITURE', 'salon',      NULL,                                    NULL,                                                                NULL),
    ('pasillo',        'Pasillo',                  'ROOM',      'vivienda',   NULL,                                    NULL,                                                                NULL),
    ('armario-pasillo','Armario del pasillo',      'FURNITURE', 'pasillo',    NULL,                                    NULL,                                                                'Toallas, sábanas y la aspiradora'),
    ('dormitorio',     'Dormitorio principal',     'ROOM',      'vivienda',   NULL,                                    NULL,                                                                NULL),
    ('armario-dorm',   'Armario del dormitorio',   'FURNITURE', 'dormitorio', NULL,                                    NULL,                                                                NULL),
    ('cuarto-lucia',   'Habitación de Lucía',      'ROOM',      'vivienda',   NULL,                                    NULL,                                                                NULL),
    ('cuarto-hugo',    'Habitación de Hugo',       'ROOM',      'vivienda',   NULL,                                    NULL,                                                                NULL),
    ('bano',           'Baño',                     'ROOM',      'vivienda',   NULL,                                    '{"maxHumidity":75}',                                                NULL),
    ('armario-bano',   'Armario del baño',         'FURNITURE', 'bano',       NULL,                                    '{"maxHumidity":75,"lightExposure":"DARKNESS"}',                      'El botiquín, en el estante de arriba'),
    ('terraza',        'Terraza',                  'OTHER',     'vivienda',   NULL,                                    '{"minTemperature":-2,"maxTemperature":42,"lightExposure":"DIRECT"}', NULL),
    ('trastero',       'Trastero',                 'OTHER',     NULL,         NULL,                                    '{"minTemperature":4,"maxTemperature":34,"maxHumidity":70}',          'Plaza 12, en el sótano -1'),
    ('estanteria',     'Estantería del trastero',  'SHELF',     'trastero',   '{"type":"UNITS","max":40,"unit":"ud"}',  NULL,                                                                NULL),
    ('banco-trabajo',  'Banco de trabajo',         'FURNITURE', 'trastero',   NULL,                                    NULL,                                                                NULL)
) AS v(clave, nombre, tipo, padre, capacidad, ambiente, notas);

-- =====================================================================
-- 4. Artículos: la definición, que no es una existencia
-- =====================================================================
--
-- Un artículo **no es un asset**: no ocupa sitio, no tiene cantidad y no se
-- presta (README 4.1.1). Aquí hay de los dos tipos que el modelo admite:
--
-- - Los de consumible, obligatorios, que fijan la unidad en la que se cuenta
--   todo lo que hay de ellos.
-- - Los de duradero, opcionales, que existen para compartir marca, modelo y
--   documentación entre unidades idénticas. Por eso solo los tienen las cosas
--   donde marca y modelo son la identidad real: la caldera sí, el sofá no.

INSERT INTO articles (id, household_id, category_id, name, unit, brand, model, barcode, pack_size,
                      unit_weight_grams, unit_volume_ml, notes, created_at, updated_at, created_by, updated_by)
SELECT pg_temp.demo_id('articulo:' || v.clave), pg_temp.demo_id('hogar'),
       pg_temp.demo_id('categoria:' || v.categoria), v.nombre, v.unidad, v.marca, v.modelo, v.codigo,
       v.envase, v.peso, v.volumen, v.notas,
       now() - interval '13 months', now() - interval '13 months',
       pg_temp.demo_id('miembro:marta'), pg_temp.demo_id('miembro:marta')
FROM (VALUES
    -- Despensa y bebida
    ('aceite',            'Aceite de oliva virgen extra',  'alimentacion',      'LITER',      NULL,       NULL,                  '8412345000019', 5,    916,  1000, NULL),
    ('arroz',             'Arroz redondo',                 'alimentacion',      'KILOGRAM',   NULL,       NULL,                  '8412345000026', 1,    NULL, NULL, NULL),
    ('pasta',             'Macarrones',                    'alimentacion',      'KILOGRAM',   NULL,       NULL,                  NULL,            0.5,  NULL, NULL, NULL),
    ('lentejas',          'Lentejas pardinas',             'alimentacion',      'KILOGRAM',   NULL,       NULL,                  NULL,            1,    NULL, NULL, NULL),
    ('garbanzos',         'Garbanzos secos',               'alimentacion',      'KILOGRAM',   NULL,       NULL,                  NULL,            1,    NULL, NULL, NULL),
    ('harina',            'Harina de trigo',               'alimentacion',      'KILOGRAM',   NULL,       NULL,                  NULL,            1,    NULL, NULL, NULL),
    ('azucar',            'Azúcar blanquilla',             'alimentacion',      'KILOGRAM',   NULL,       NULL,                  NULL,            1,    NULL, NULL, NULL),
    ('sal',               'Sal marina fina',               'alimentacion',      'KILOGRAM',   NULL,       NULL,                  NULL,            1,    NULL, NULL, NULL),
    ('leche',             'Leche entera',                  'alimentacion',      'LITER',      NULL,       NULL,                  '8412345000033', 6,    1030, 1000, 'Brik de litro, pack de seis'),
    ('cafe',              'Café molido natural',           'alimentacion',      'GRAM',       NULL,       NULL,                  NULL,            250,  NULL, NULL, NULL),
    ('cacao',             'Cacao soluble',                 'alimentacion',      'GRAM',       NULL,       NULL,                  NULL,            800,  NULL, NULL, 'El desayuno de Hugo'),
    ('galletas',          'Galletas María',                'alimentacion',      'GRAM',       NULL,       NULL,                  NULL,            800,  NULL, NULL, NULL),
    ('atun',              'Atún claro en aceite de oliva', 'alimentacion',      'UNIT',       NULL,       NULL,                  '8412345000040', 6,    80,   NULL, 'Lata de 80 g escurrido'),
    ('tomate-frito',      'Tomate frito',                  'alimentacion',      'UNIT',       NULL,       NULL,                  NULL,            3,    400,  NULL, NULL),
    ('aceitunas',         'Aceitunas rellenas de anchoa',  'alimentacion',      'UNIT',       NULL,       NULL,                  NULL,            3,    300,  NULL, NULL),
    ('caldo',             'Caldo de pollo',                'alimentacion',      'LITER',      NULL,       NULL,                  NULL,            1,    NULL, 1000, NULL),
    ('chocolate',         'Chocolate negro 70 %',          'alimentacion',      'UNIT',       NULL,       NULL,                  NULL,            1,    125,  NULL, NULL),
    ('agua',              'Agua mineral',                  'alimentacion',      'LITER',      NULL,       NULL,                  '8412345000057', 9,    1000, 1000, 'Pack de seis botellas de litro y medio'),
    -- Limpieza
    ('detergente',        'Detergente líquido para lavadora', 'limpieza',       'LITER',      NULL,       NULL,                  NULL,            3,    NULL, 1000, NULL),
    ('suavizante',        'Suavizante concentrado',        'limpieza',          'LITER',      NULL,       NULL,                  NULL,            1.5,  NULL, 1000, NULL),
    ('lejia',             'Lejía',                         'limpieza',          'LITER',      NULL,       NULL,                  NULL,            1.6,  NULL, 1000, NULL),
    ('friegasuelos',      'Friegasuelos',                  'limpieza',          'LITER',      NULL,       NULL,                  NULL,            1.5,  NULL, 1000, NULL),
    ('lavavajillas-past', 'Pastillas para el lavavajillas','limpieza',          'UNIT',       NULL,       NULL,                  '8412345000064', 60,   20,   NULL, NULL),
    ('multiusos',         'Limpiador multiusos',           'limpieza',          'MILLILITER', NULL,       NULL,                  NULL,            750,  NULL, NULL, NULL),
    ('bolsas-basura',     'Bolsas de basura de 30 litros', 'limpieza',          'UNIT',       NULL,       NULL,                  NULL,            20,   8,    NULL, NULL),
    ('papel-cocina',      'Papel de cocina',               'limpieza',          'UNIT',       NULL,       NULL,                  NULL,            4,    120,  NULL, NULL),
    ('estropajos',        'Estropajos con salvauñas',      'limpieza',          'UNIT',       NULL,       NULL,                  NULL,            5,    15,   NULL, NULL),
    -- Higiene y botiquín
    ('papel-higienico',   'Papel higiénico',               'higiene',           'UNIT',       NULL,       NULL,                  '8412345000071', 12,   130,  NULL, NULL),
    ('gel-ducha',         'Gel de ducha',                  'higiene',           'MILLILITER', NULL,       NULL,                  NULL,            750,  NULL, NULL, NULL),
    ('champu',            'Champú',                        'higiene',           'MILLILITER', NULL,       NULL,                  NULL,            400,  NULL, NULL, NULL),
    ('pasta-dientes',     'Pasta de dientes',              'higiene',           'MILLILITER', NULL,       NULL,                  NULL,            75,   NULL, NULL, NULL),
    ('cuchillas',         'Cuchillas de afeitar',          'higiene',           'UNIT',       NULL,       NULL,                  NULL,            4,    NULL, NULL, NULL),
    ('paracetamol',       'Paracetamol 1 g',               'higiene',           'UNIT',       NULL,       NULL,                  NULL,            20,   NULL, NULL, 'Comprimidos'),
    ('ibuprofeno',        'Ibuprofeno 600 mg',             'higiene',           'UNIT',       NULL,       NULL,                  NULL,            40,   NULL, NULL, 'Comprimidos'),
    ('tiritas',           'Tiritas surtidas',              'higiene',           'UNIT',       NULL,       NULL,                  NULL,            20,   NULL, NULL, NULL),
    -- Papelería
    ('folios',            'Folios A4 de 80 g',             'papeleria',         'UNIT',       NULL,       NULL,                  NULL,            500,  5,    NULL, NULL),
    ('pilas-aa',          'Pilas alcalinas AA',            'papeleria',         'UNIT',       NULL,       NULL,                  NULL,            4,    23,   NULL, 'Las del mando y los juguetes'),
    -- Modelos de duradero: marca y modelo son aquí la identidad real
    ('art-caldera',       'Caldera mural de gas',          'electrodomesticos', 'UNIT',       'Vaillant', 'ecoTEC plus VMW 236', NULL,            NULL, NULL, NULL, NULL),
    ('art-lavadora',      'Lavadora de carga frontal',     'electrodomesticos', 'UNIT',       'Balay',    '3TS993BD',            NULL,            NULL, NULL, NULL, NULL),
    ('art-lavavajillas',  'Lavavajillas de 60 cm',         'electrodomesticos', 'UNIT',       'Bosch',    'SMS4HVI33E',          NULL,            NULL, NULL, NULL, NULL),
    ('art-frigorifico',   'Frigorífico combi',             'electrodomesticos', 'UNIT',       'LG',       'GBB72NSDFN',          NULL,            NULL, NULL, NULL, NULL),
    ('art-horno',         'Horno multifunción',            'electrodomesticos', 'UNIT',       'Teka',     'HLB 840',             NULL,            NULL, NULL, NULL, NULL),
    ('art-microondas',    'Microondas con grill',          'electrodomesticos', 'UNIT',       'Cecotec',  'ProClean 3110',       NULL,            NULL, NULL, NULL, NULL),
    ('art-robot',         'Robot aspirador',               'electrodomesticos', 'UNIT',       'Roborock', 'Q7 Max',              NULL,            NULL, NULL, NULL, NULL),
    ('art-aire',          'Aire acondicionado split',      'electrodomesticos', 'UNIT',       'Daikin',   'TXM35R',              NULL,            NULL, NULL, NULL, NULL),
    ('art-tv',            'Televisor OLED de 55 pulgadas', 'electronica',       'UNIT',       'LG',       'OLED55C4',            NULL,            NULL, NULL, NULL, NULL),
    ('art-router',        'Router de fibra',               'electronica',       'UNIT',       'Askey',    'HGU RTF8115VW',       NULL,            NULL, NULL, NULL, 'Del operador, en depósito'),
    ('art-taladro',       'Taladro percutor',              'herramientas',      'UNIT',       'Bosch',    'UniversalImpact 800', NULL,            NULL, NULL, NULL, NULL)
) AS v(clave, nombre, categoria, unidad, marca, modelo, codigo, envase, peso, volumen, notas);


-- =====================================================================
-- 5. Assets duraderos
-- =====================================================================
--
-- Un asset es **todo material del hogar** y no solo lo económicamente relevante
-- (README 4.1.1). El `DURABLE` tiene identidad propia, una fila por unidad
-- física, y es el único que puede prestarse y hacer de ubicación de otros: aquí
-- lo hace la caja de herramientas, que guarda dentro tres cosas.
--
-- **El nombre y la categoría van vacíos cuando hay artículo**, y esa columna a
-- nulo no es un descuido: cuando el asset tiene artículo, la aplicación resuelve
-- los dos del artículo al leer (`article?.name ?: asset.name`), así que un nombre
-- guardado aquí no se vería nunca. De ahí que solo tengan artículo las cosas
-- cuyo modelo ES su nombre --la caldera, la lavadora, el taladro-- y que el
-- portátil de Lucía o la bici de Hugo lleven nombre propio y ninguno.
--
-- **El estado de conservación va puesto en 36 y a nulo en diez**, y las dos
-- mitades son a propósito: nulo significa que nadie lo anotó, que es el caso
-- corriente de un inventario doméstico, y un juego de datos en el que todo
-- estuviera anotado enseñaría una aplicación que nadie usa así. Ninguna cosa
-- está `NEW`: lo más joven de esta casa tiene once meses, y «nuevo, sin usar»
-- después de un año sería mentira.
--
-- Los que no están en `GOOD` tienen su motivo escrito al lado cuando lo hay --al
-- ventilador se le rompió el motor y la tienda perdió una piqueta-- y **cuadran
-- con lo que dicen los préstamos** de más abajo: el patinete volvió con un
-- arañazo y su ficha dice `WORN`, que es lo que alguien habría corregido al
-- recibirlo.

INSERT INTO assets (id, household_id, article_id, category_id, name, type, owner_id,
                    location_asset_id, location_id, quantity, status, serial_number, acquired_on,
                    condition, notes, created_at, updated_at, created_by, updated_by)
SELECT pg_temp.demo_id('asset:' || v.clave), pg_temp.demo_id('hogar'),
       CASE WHEN v.articulo   IS NULL THEN NULL ELSE pg_temp.demo_id('articulo:'  || v.articulo)   END,
       CASE WHEN v.categoria  IS NULL THEN NULL ELSE pg_temp.demo_id('categoria:' || v.categoria)  END,
       v.nombre, 'DURABLE', pg_temp.demo_id('miembro:' || v.propietario),
       CASE WHEN v.contenedor IS NULL THEN NULL ELSE pg_temp.demo_id('asset:'     || v.contenedor) END,
       CASE WHEN v.ubicacion  IS NULL THEN NULL ELSE pg_temp.demo_id('ubicacion:' || v.ubicacion)  END,
       NULL, v.estado, v.serie,
       (CURRENT_DATE - (v.antiguedad || ' months')::interval)::date,
       v.condicion, v.notas,
       now() - (v.antiguedad || ' months')::interval - interval '1 day',
       now() - interval '20 days',
       pg_temp.demo_id('miembro:marta'), pg_temp.demo_id('miembro:marta')
FROM (VALUES
    -- Cocina
    ('frigorifico',    NULL,                               'art-frigorifico',  NULL,                'marta',  NULL,                'cocina',          '804KRQD2Z311',    36,  'AVAILABLE', NULL, 'GOOD'),
    ('lavadora',       NULL,                               'art-lavadora',     NULL,                'marta',  NULL,                'cocina',          'FD9912-004521',   48,  'AVAILABLE', NULL, 'GOOD'),
    ('lavavajillas',   NULL,                               'art-lavavajillas', NULL,                'javier', NULL,                'cocina',          'FD0104-771120',   26,  'AVAILABLE', NULL, NULL),
    ('horno',          NULL,                               'art-horno',        NULL,                'javier', NULL,                'cocina',          NULL,              72,  'AVAILABLE', 'Vino con la cocina', 'WORN'),
    ('microondas',     NULL,                               'art-microondas',   NULL,                'marta',  NULL,                'cocina',          NULL,              14,  'AVAILABLE', NULL, 'GOOD'),
    ('campana',        'Campana extractora',               NULL,               'electrodomesticos', 'javier', NULL,                'cocina',          NULL,              72,  'AVAILABLE', NULL, 'WORN'),
    ('cafetera',       'Cafetera de cápsulas',             NULL,               'electrodomesticos', 'marta',  NULL,                'cocina',          NULL,              25,  'AVAILABLE', NULL, 'GOOD'),
    ('batidora',       'Batidora de mano',                 NULL,               'electrodomesticos', 'marta',  NULL,                'armario-cocina',  NULL,              60,  'AVAILABLE', NULL, NULL),
    ('tostadora',      'Tostadora',                        NULL,               'electrodomesticos', 'javier', NULL,                'cocina',          NULL,              33,  'AVAILABLE', NULL, 'WORN'),
    ('olla',           'Olla a presión',                   NULL,               'menaje',            'marta',  NULL,                'armario-cocina',  NULL,              96,  'AVAILABLE', 'La goma se cambió en primavera', 'GOOD'),
    ('vajilla',        'Vajilla de diario',                NULL,               'menaje',            'marta',  NULL,                'cocina',          NULL,              96,  'AVAILABLE', 'Doce piezas, faltan dos platos hondos', 'WORN'),
    -- Instalación
    ('caldera',        NULL,                               'art-caldera',      NULL,                'javier', NULL,                'cocina',          'VA-2019-887413',  84,  'AVAILABLE', 'En el hueco junto a la ventana', 'GOOD'),
    ('aire',           NULL,                               'art-aire',         NULL,                'javier', NULL,                'salon',           'DK-3325-01188',   26,  'AVAILABLE', 'El del salón; los dormitorios no tienen', 'GOOD'),
    -- Salón
    ('tv',             NULL,                               'art-tv',           NULL,                'javier', NULL,                'salon',           NULL,              13,  'AVAILABLE', NULL, 'GOOD'),
    ('barra-sonido',   'Barra de sonido',                  NULL,               'electronica',       'javier', NULL,                'mueble-salon',    NULL,              13,  'AVAILABLE', NULL, NULL),
    ('router',         NULL,                               'art-router',       NULL,                'javier', NULL,                'mueble-salon',    NULL,              38,  'AVAILABLE', 'Es del operador: si se cambia de compañía hay que devolverlo', NULL),
    ('consola',        'Consola de videojuegos',           NULL,               'electronica',       'lucia',  NULL,                'mueble-salon',    NULL,              24,  'AVAILABLE', NULL, 'GOOD'),
    ('sofa',           'Sofá de tres plazas',              NULL,               'mobiliario',        'marta',  NULL,                'salon',           NULL,              62,  'AVAILABLE', NULL, 'WORN'),
    ('mesa-centro',    'Mesa de centro',                   NULL,               'mobiliario',        'marta',  NULL,                'salon',           NULL,              62,  'AVAILABLE', NULL, 'WORN'),
    ('estanteria-libros','Estantería de libros',           NULL,               'mobiliario',        'marta',  NULL,                'salon',           NULL,              62,  'AVAILABLE', NULL, NULL),
    ('robot',          NULL,                               'art-robot',        NULL,                'marta',  NULL,                'salon',           NULL,              18,  'AVAILABLE', NULL, 'GOOD'),
    -- Dormitorios
    ('cama',           'Cama de matrimonio',               NULL,               'mobiliario',        'marta',  NULL,                'dormitorio',      NULL,              110, 'AVAILABLE', NULL, 'GOOD'),
    ('escritorio-lucia','Escritorio de Lucía',             NULL,               'mobiliario',        'lucia',  NULL,                'cuarto-lucia',    NULL,              50,  'AVAILABLE', NULL, 'WORN'),
    ('portatil-lucia', 'Portátil de Lucía',                NULL,               'electronica',       'lucia',  NULL,                'cuarto-lucia',    'PF3K8821',        13,  'AVAILABLE', 'Lenovo IdeaPad Slim 3, del programa del instituto', 'GOOD'),
    ('tablet-hugo',    'Tablet de Hugo',                   NULL,               'electronica',       'hugo',   NULL,                'cuarto-hugo',     NULL,              11,  'AVAILABLE', NULL, 'GOOD'),
    ('litera',         'Cama de Hugo',                     NULL,               'mobiliario',        'hugo',   NULL,                'cuarto-hugo',     NULL,              50,  'AVAILABLE', NULL, 'GOOD'),
    -- Baño y pasillo
    ('secador',        'Secador de pelo',                  NULL,               'electrodomesticos', 'marta',  NULL,                'armario-bano',    NULL,              33,  'AVAILABLE', NULL, NULL),
    ('aspirador',      'Aspirador de trineo',              NULL,               'electrodomesticos', 'marta',  NULL,                'armario-pasillo', NULL,              70,  'AVAILABLE', NULL, 'WORN'),
    ('plancha',        'Plancha de vapor',                 NULL,               'electrodomesticos', 'javier', NULL,                'armario-pasillo', NULL,              45,  'AVAILABLE', NULL, 'GOOD'),
    -- Terraza
    ('barbacoa',       'Barbacoa de gas portátil',         NULL,               'deporte',           'javier', NULL,                'terraza',         NULL,              25,  'AVAILABLE', NULL, 'GOOD'),
    ('mesa-terraza',   'Mesa de terraza con cuatro sillas',NULL,               'mobiliario',        'marta',  NULL,                'terraza',         NULL,              37,  'AVAILABLE', NULL, 'WORN'),
    ('sombrilla',      'Sombrilla',                        NULL,               'decoracion',        'marta',  NULL,                'terraza',         NULL,              37,  'AVAILABLE', NULL, NULL),
    -- Trastero: la caja de herramientas hace de ubicación de lo que lleva dentro
    ('taladro',        NULL,                               'art-taladro',      NULL,                'javier', NULL,                'banco-trabajo',   '3601JB4000',      62,  'AVAILABLE', NULL, 'GOOD'),
    ('caja-herramientas','Caja de herramientas',           NULL,               'herramientas',      'javier', NULL,                'banco-trabajo',   NULL,              96,  'AVAILABLE', 'La de plástico rojo', NULL),
    ('destornilladores','Juego de destornilladores',       NULL,               'herramientas',      'javier', 'caja-herramientas', NULL,              NULL,              96,  'AVAILABLE', NULL, 'WORN'),
    ('llave-inglesa',  'Llave inglesa',                    NULL,               'herramientas',      'javier', 'caja-herramientas', NULL,              NULL,              96,  'AVAILABLE', NULL, 'GOOD'),
    ('metro',          'Cinta métrica',                    NULL,               'herramientas',      'javier', 'caja-herramientas', NULL,              NULL,              96,  'AVAILABLE', NULL, NULL),
    ('escalera',       'Escalera de tijera de cuatro peldaños', NULL,          'herramientas',      'javier', NULL,                'trastero',        NULL,              70,  'LENT',      NULL, 'GOOD'),
    ('bici-hugo',      'Bicicleta de Hugo',                NULL,               'deporte',           'hugo',   NULL,                'trastero',        NULL,              24,  'AVAILABLE', 'Orbea MX 24; se le queda pequeña', 'GOOD'),
    ('patinete',       'Patinete de Lucía',                NULL,               'deporte',           'lucia',  NULL,                'trastero',        NULL,              37,  'AVAILABLE', NULL, 'WORN'),
    ('maleta',         'Maleta grande',                    NULL,               'deporte',           'marta',  NULL,                'estanteria',      NULL,              80,  'AVAILABLE', NULL, 'WORN'),
    ('nevera-portatil','Nevera portátil',                  NULL,               'deporte',           'javier', NULL,                'estanteria',      NULL,              50,  'LENT',      NULL, 'GOOD'),
    ('bomba-bici',     'Bomba de aire para bicicleta',     NULL,               'deporte',           'hugo',   NULL,                'banco-trabajo',   NULL,              24,  'AVAILABLE', NULL, NULL),
    ('tienda-campana', 'Tienda de campaña',                NULL,               'deporte',           'javier', NULL,                'estanteria',      NULL,              75,  'AVAILABLE', 'Cuatro plazas, le falta una piqueta', 'DAMAGED'),
    ('arbol-navidad',  'Árbol de Navidad artificial',      NULL,               'decoracion',        'marta',  NULL,                'estanteria',      NULL,              88,  'AVAILABLE', NULL, 'WORN'),
    ('ventilador',     'Ventilador de pie',                NULL,               'electrodomesticos', 'marta',  NULL,                'trastero',        NULL,              62,  'DECOMMISSIONED', 'Se le rompió el motor el verano pasado', 'UNUSABLE')
) AS v(clave, nombre, articulo, categoria, propietario, contenedor, ubicacion, serie, antiguedad, estado, notas, condicion);


-- =====================================================================
-- 5 bis. Qué lleva etiquetado cada cosa
-- =====================================================================
--
-- Trece parejas sobre once assets, que es la proporción que interesa enseñar:
-- **la mayoría del inventario no lleva ninguna**. Un hogar de demostración con
-- todo etiquetado enseñaría una casa que no existe y, peor, escondería el caso
-- normal --la fila sin pastillas-- que es contra el que hay que mirar si la
-- maquetación aguanta.
--
-- Dos assets llevan dos, que es lo que la categoría no podía dar: la tienda de
-- campaña es *camping* y *se presta* a la vez.

INSERT INTO asset_tags (household_id, asset_id, tag_id, created_at, created_by)
SELECT pg_temp.demo_id('hogar'),
       pg_temp.demo_id('asset:'    || v.asset),
       pg_temp.demo_id('etiqueta:' || v.etiqueta),
       now() - (v.antiguedad || ' months')::interval,
       pg_temp.demo_id('miembro:' || v.quien)
FROM (VALUES
    ('tienda-campana', 'camping',   'javier', '9'),
    ('tienda-campana', 'prestable', 'javier', '9'),
    ('nevera-portatil','camping',   'javier', '9'),
    ('barbacoa',       'camping',   'javier', '8'),
    ('bomba-bici',     'camping',   'hugo',   '7'),
    ('escalera',       'prestable', 'javier', '10'),
    ('taladro',        'prestable', 'javier', '10'),
    ('olla',           'heredado',  'marta',  '12'),
    ('vajilla',        'heredado',  'marta',  '12'),
    ('arbol-navidad',  'heredado',  'marta',  '12'),
    -- La única que además está estropeada: la etiqueta dice qué hay que hacer
    -- con ella, y la condición, en qué estado está. Son dos preguntas distintas.
    ('tienda-campana', 'reparar',   'javier', '2'),
    ('portatil-lucia', 'cole',      'lucia',  '9'),
    -- Y una con la etiqueta **retirada**: sigue puesta y se sigue viendo, que es
    -- lo que distingue retirar de borrar.
    ('maleta',         'mudanza',   'marta',  '13')
) AS v(asset, etiqueta, quien, antiguedad);


-- =====================================================================
-- 6. Existencias de consumible
-- =====================================================================
--
-- Un `CONSUMABLE` es una fila por **existencia**: un artículo en una ubicación,
-- con su cantidad en la unidad del artículo. De ahí que el papel higiénico y el
-- agua aparezcan dos veces --una en casa y otra de reserva en el trastero--: son
-- dos existencias del mismo artículo, que es justo el caso para el que existe
-- `MergeStockItems`. Ni nombre ni categoría: los pone el artículo.

INSERT INTO assets (id, household_id, article_id, category_id, name, type, owner_id,
                    location_asset_id, location_id, quantity, status, notes,
                    created_at, updated_at, created_by, updated_by)
SELECT pg_temp.demo_id('existencia:' || v.articulo || ':' || v.ubicacion), pg_temp.demo_id('hogar'),
       pg_temp.demo_id('articulo:' || v.articulo), NULL, NULL, 'CONSUMABLE',
       pg_temp.demo_id('miembro:' || v.propietario), NULL,
       pg_temp.demo_id('ubicacion:' || v.ubicacion), v.cantidad, 'AVAILABLE', NULL,
       now() - interval '13 months', now() - interval '3 days',
       pg_temp.demo_id('miembro:marta'), pg_temp.demo_id('miembro:marta')
FROM (VALUES
    -- Despensa
    ('aceite',            'despensa',       'marta',  3),
    ('arroz',             'despensa',       'marta',  2),
    ('pasta',             'despensa',       'marta',  1.5),
    ('lentejas',          'despensa',       'marta',  1),
    ('garbanzos',         'despensa',       'marta',  0.5),
    ('harina',            'despensa',       'marta',  2),
    ('azucar',            'despensa',       'marta',  1),
    ('sal',               'despensa',       'marta',  0.8),
    ('leche',             'despensa',       'javier', 2),
    ('cafe',              'despensa',       'javier', 100),
    ('cacao',             'despensa',       'marta',  0),
    ('galletas',          'despensa',       'marta',  800),
    ('atun',              'despensa',       'marta',  9),
    ('tomate-frito',      'despensa',       'marta',  2),
    ('aceitunas',         'despensa',       'javier', 2),
    ('caldo',             'despensa',       'marta',  1),
    ('chocolate',         'despensa',       'lucia',  3),
    ('agua',              'despensa',       'javier', 9),
    -- Armario bajo de la cocina
    ('detergente',        'armario-cocina', 'marta',  0.8),
    ('suavizante',        'armario-cocina', 'marta',  1.5),
    ('lejia',             'armario-cocina', 'marta',  1.6),
    ('friegasuelos',      'armario-cocina', 'marta',  0.9),
    ('lavavajillas-past', 'armario-cocina', 'javier', 42),
    ('multiusos',         'armario-cocina', 'marta',  750),
    ('bolsas-basura',     'armario-cocina', 'javier', 12),
    ('papel-cocina',      'armario-cocina', 'marta',  3),
    ('estropajos',        'armario-cocina', 'marta',  5),
    -- Armario del baño
    ('papel-higienico',   'armario-bano',   'marta',  4),
    ('gel-ducha',         'armario-bano',   'marta',  1200),
    ('champu',            'armario-bano',   'lucia',  400),
    ('pasta-dientes',     'armario-bano',   'marta',  150),
    ('cuchillas',         'armario-bano',   'javier', 3),
    ('paracetamol',       'armario-bano',   'marta',  14),
    ('ibuprofeno',        'armario-bano',   'marta',  22),
    ('tiritas',           'armario-bano',   'marta',  20),
    -- Estantería del trastero: la reserva
    ('papel-higienico',   'estanteria',     'marta',  6),
    ('agua',              'estanteria',     'javier', 9),
    ('bolsas-basura',     'estanteria',     'javier', 20),
    ('folios',            'estanteria',     'lucia',  500),
    ('pilas-aa',          'estanteria',     'hugo',   0)
) AS v(articulo, ubicacion, propietario, cantidad);


-- =====================================================================
-- 7. Documentos
-- =====================================================================
--
-- Cuelgan de **exactamente uno** de asset o artículo, y esa frontera tiene
-- sentido: la factura y la garantía son de la unidad física que se compró; el
-- manual es del modelo, así que va al artículo y sirve para todas sus unidades.
-- La excepción deliberada son los dos manuales que van al asset --caldera y
-- horno--, porque CMMS solo puede señalar como manual de una máquina un
-- documento de ESA máquina.
--
-- Todos son enlaces externos y ninguno es un fichero subido: sembrar ficheros
-- exigiría escribir bytes en `.data/files`, y este fichero solo toca la base.

INSERT INTO documents (id, household_id, asset_id, article_id, file_id, type, url, description, "date", valid_until,
                       created_at, updated_at, created_by, updated_by)
SELECT pg_temp.demo_id('documento:' || v.clave), pg_temp.demo_id('hogar'),
       CASE WHEN v.asset    IS NULL THEN NULL ELSE pg_temp.demo_id('asset:' || v.asset)       END,
       CASE WHEN v.articulo IS NULL THEN NULL ELSE pg_temp.demo_id('articulo:' || v.articulo) END,
       NULL, v.tipo, v.url, v.descripcion,
       (CURRENT_DATE - (v.fecha || ' months')::interval)::date,
       CASE WHEN v.vigencia IS NULL THEN NULL
            ELSE (CURRENT_DATE - (v.fecha || ' months')::interval + (v.vigencia || ' months')::interval)::date END,
       now() - (v.fecha || ' months')::interval, now() - (v.fecha || ' months')::interval,
       pg_temp.demo_id('miembro:marta'), pg_temp.demo_id('miembro:marta')
FROM (VALUES
    ('manual-caldera',     'caldera',        NULL,               'MANUAL',   'https://manuales.example.test/vaillant/ecotec-plus-vmw-236.pdf', 'Manual de instalación y uso',            84,  NULL),
    ('manual-horno',       'horno',          NULL,               'MANUAL',   'https://manuales.example.test/teka/hlb-840.pdf',                 'Manual del horno, con la tabla de programas', 72,  NULL),
    ('manual-lavadora',    NULL,             'art-lavadora',     'MANUAL',   'https://manuales.example.test/balay/3ts993bd.pdf',               'Manual y guía rápida de programas',      48,  NULL),
    ('manual-lavavajillas',NULL,             'art-lavavajillas', 'MANUAL',   'https://manuales.example.test/bosch/sms4hvi33e.pdf',             'Manual del lavavajillas',                26,  NULL),
    ('manual-robot',       NULL,             'art-robot',        'MANUAL',   'https://manuales.example.test/roborock/q7-max.pdf',              'Manual y despiece de recambios',         18,  NULL),
    ('manual-tv',          NULL,             'art-tv',           'MANUAL',   'https://manuales.example.test/lg/oled55c4.pdf',                  'Manual del televisor',                   13,  NULL),
    ('factura-frigorifico','frigorifico',    NULL,               'INVOICE',  'https://documentos.example.test/facturas/frigorifico-2023.pdf',  'Factura de compra',                      36,  NULL),
    ('garantia-frigorifico','frigorifico',   NULL,               'WARRANTY', 'https://documentos.example.test/garantias/frigorifico.pdf',      'Garantía del fabricante, tres años',     36,  36),
    ('factura-lavadora',   'lavadora',       NULL,               'INVOICE',  'https://documentos.example.test/facturas/lavadora-2022.pdf',     'Factura de compra',                      48,  NULL),
    ('garantia-lavadora',  'lavadora',       NULL,               'WARRANTY', 'https://documentos.example.test/garantias/lavadora.pdf',         'Garantía extendida, ya caducada',        48,  36),
    ('factura-tv',         'tv',             NULL,               'INVOICE',  'https://documentos.example.test/facturas/televisor.pdf',         'Factura de compra',                      13,  NULL),
    ('garantia-tv',        'tv',             NULL,               'WARRANTY', 'https://documentos.example.test/garantias/televisor.pdf',        'Garantía de dos años',                   13,  24),
    ('factura-caldera',    'caldera',        NULL,               'INVOICE',  'https://documentos.example.test/facturas/caldera-2019.pdf',      'Factura de la instalación',              84,  NULL),
    ('contrato-caldera',   'caldera',        NULL,               'OTHER',    'https://documentos.example.test/contratos/mantenimiento-gas.pdf','Contrato de mantenimiento anual',        11,  12),
    ('factura-portatil',   'portatil-lucia', NULL,               'INVOICE',  'https://documentos.example.test/facturas/portatil-lucia.pdf',    'Factura del programa del instituto',     13,  NULL),
    ('garantia-portatil',  'portatil-lucia', NULL,               'WARRANTY', 'https://documentos.example.test/garantias/portatil-lucia.pdf',   'Garantía de dos años',                   13,  24),
    ('factura-aire',       'aire',           NULL,               'INVOICE',  'https://documentos.example.test/facturas/aire-acondicionado.pdf','Factura e instalación',                  26,  NULL),
    ('ficha-bici',         'bici-hugo',      NULL,               'OTHER',    'https://documentos.example.test/otros/ficha-bici-hugo.pdf',      'Número de bastidor y ficha del seguro',  24,  NULL)
) AS v(clave, asset, articulo, tipo, url, descripcion, fecha, vigencia);


-- =====================================================================
-- 8. Préstamos
-- =====================================================================
--
-- Solo un duradero se presta, y prestarlo lo ocupa: los dos abiertos --uno en
-- plazo y uno vencido-- tienen su asset en `LENT`. `OVERDUE` lo pone un proceso
-- diario y no una persona, y **vencer no es devolver**: el vencido sigue
-- ocupando la escalera... perdón, la nevera portátil.
--
-- El prestatario puede ser del hogar o de fuera. Cuando es de fuera va como JSON
-- con al menos un canal de contacto, porque es por donde se le manda el enlace
-- con el token acotado.
--
-- **La condición de entrega y la de devolución son la pareja que hace legible el
-- historial**: por separado no dicen nada, y juntas dicen si la cosa volvió peor.
-- La de vuelta solo la llevan los devueltos --un `CHECK` de la tabla lo exige, y
-- es que anotar cómo volvió algo que sigue fuera de casa no significa nada-- y
-- la maleta no lleva ninguna de las dos, que es el caso más frecuente de todos:
-- nadie lo anotó.

INSERT INTO loans (id, household_id, asset_id, lender_member_id, lender_external,
                   borrower_member_id, borrower_external, status, notes,
                   started_at, due_at, returned_at, condition_at_start, condition_on_return,
                   created_at, updated_at, created_by, updated_by)
SELECT pg_temp.demo_id('prestamo:' || v.clave), pg_temp.demo_id('hogar'),
       pg_temp.demo_id('asset:' || v.asset),
       pg_temp.demo_id('miembro:' || v.prestador), NULL,
       CASE WHEN v.receptor_miembro IS NULL THEN NULL ELSE pg_temp.demo_id('miembro:' || v.receptor_miembro) END,
       v.receptor_externo::jsonb,
       v.estado, v.notas,
       now() - (v.inicio || ' days')::interval,
       CASE WHEN v.plazo IS NULL THEN NULL ELSE now() - (v.plazo || ' days')::interval END,
       CASE WHEN v.devuelto IS NULL THEN NULL ELSE now() - (v.devuelto || ' days')::interval END,
       v.salida, v.vuelta,
       now() - (v.inicio || ' days')::interval,
       now() - (coalesce(v.devuelto, v.inicio) || ' days')::interval,
       pg_temp.demo_id('miembro:' || v.prestador), pg_temp.demo_id('miembro:' || v.prestador)
FROM (VALUES
    ('taladro',   'taladro',         'javier', NULL,    '{"name":"Rubén Castaño (4.º B)","email":"ruben@vecinos.test","phone":"+34 633 44 55 66"}', 'RETURNED', 'Para colgar unas baldas',                    300, 286, 288, 'GOOD', 'GOOD'),
    ('escalera',  'escalera',        'javier', NULL,    '{"name":"Nuria Peña (2.º B)","email":"nuria@vecinos.test"}',                               'ACTIVE',   'Está pintando el pasillo',                     9, -12, NULL, 'GOOD', NULL),
    ('nevera',    'nevera-portatil', 'javier', NULL,    '{"name":"Álvaro Gómez","email":"alvaro@familia.test","phone":"+34 644 55 66 77"}',          'OVERDUE',  'Se la llevó a la playa y no ha vuelto',       60,  19, NULL, 'GOOD', NULL),
    ('tienda',    'tienda-campana',  'javier', 'lucia', NULL,                                                                                       'RETURNED', 'Campamento del instituto',                   150, 130, 122, 'GOOD', 'DAMAGED'),
    ('bici',      'bici-hugo',       'hugo',   NULL,    '{"name":"Iván Ruiz","email":"ivan@familia.test"}',                                          'RETURNED', 'Mientras la suya estaba en el taller',        210, 190, 185, 'GOOD', 'GOOD'),
    ('maleta',    'maleta',          'marta',  NULL,    '{"name":"Carmen Alonso","phone":"+34 655 66 77 88"}',                                       'RETURNED', 'Viaje de mi madre a Galicia',                150, 130, 128, NULL, NULL),
    ('patinete',  'patinete',        'lucia',  'hugo',  NULL,                                                                                       'RETURNED', 'Una semana, y volvió con un arañazo',         40,  33,  30, 'GOOD', 'WORN'),
    ('barbacoa',  'barbacoa',        'javier', NULL,    '{"name":"Familia del 3.º A","email":"tercero-a@vecinos.test"}',                             'RETURNED', 'Comida de la comunidad',                      60,  55,  55, NULL, 'GOOD'),
    ('aspirador', 'aspirador',       'marta',  NULL,    '{"name":"Rosa Iglesias (1.º C)","phone":"+34 666 77 88 99"}',                               'RETURNED', 'Se le había roto el suyo',                   390, 380, 376, 'GOOD', 'GOOD')
) AS v(clave, asset, prestador, receptor_miembro, receptor_externo, estado, notas, inicio, plazo, devuelto,
       salida, vuelta);


-- =====================================================================
-- 9. Módulos activados
-- =====================================================================
--
-- Los cuatro de la Fase 2, encendidos hace diez meses. La activación es lo que
-- dispara la siembra de cada módulo --fichas de almacén, líneas de compra y
-- fichas de máquina--, y las secciones que siguen escriben a mano lo que aquella
-- siembra habría dejado, más los diez meses de uso posteriores.

INSERT INTO household_modules (id, household_id, module_key, status, activated_at, deactivated_at,
                               created_at, updated_at, created_by, updated_by)
SELECT pg_temp.demo_id('modulo:' || v.clave), pg_temp.demo_id('hogar'), v.clave, 'ACTIVE',
       now() - (v.antiguedad || ' months')::interval, NULL,
       now() - (v.antiguedad || ' months')::interval,
       now() - (v.antiguedad || ' months')::interval,
       pg_temp.demo_id('miembro:marta'), pg_temp.demo_id('miembro:marta')
FROM (VALUES
    ('SUPPLIERS',   '10'),
    ('WAREHOUSE',   '10'),
    ('PURCHASING',  '9'),
    ('MAINTENANCE', '9')
) AS v(clave, antiguedad);


-- =====================================================================
-- 10. Proveedores y contactos de servicio
-- =====================================================================
--
-- El módulo no distingue empresa de persona: lo que el hogar necesita del
-- servicio técnico de la caldera y del electricista del barrio es lo mismo. La
-- categoría de servicio es una lista cerrada, al contrario que la categoría de un
-- asset. Uno va retirado: sus enlaces se conservan y deja de ofrecerse.

INSERT INTO suppliers (id, household_id, name, service_category, contact_name, phone, email, website, address, notes,
                       created_at, updated_at, retired_at, created_by, updated_by)
SELECT pg_temp.demo_id('proveedor:' || v.clave), pg_temp.demo_id('hogar'), v.nombre, v.categoria,
       v.contacto, v.telefono, v.correo, v.web, v.direccion, v.notas,
       now() - (v.antiguedad || ' months')::interval, now() - (v.antiguedad || ' months')::interval,
       CASE WHEN v.retirado IS NULL THEN NULL ELSE now() - (v.retirado || ' months')::interval END,
       pg_temp.demo_id('miembro:marta'), pg_temp.demo_id('miembro:marta')
FROM (VALUES
    ('fontaneria',  'Fontanería Ramírez',            'PLUMBING',        'Luis Ramírez',  '+34 915 11 22 33', 'avisos@fontaneriaramirez.test',  NULL,                                  'C/ Miguel Servet 14, Madrid', 'Responde rápido por WhatsApp',           10, NULL),
    ('gas',         'Instalaciones Vallés',          'HEATING_COOLING', 'Sonia Vallés',  '+34 915 22 33 44', 'sat@instalacionesvalles.test',   'https://instalacionesvalles.test',    NULL,                          'Lleva el contrato anual de la caldera',  10, NULL),
    ('sat-electro', 'Servicio Técnico Elecdomes',    'APPLIANCES',      NULL,            '+34 902 33 44 55', NULL,                             'https://elecdomes.test/asistencia',   NULL,                          'Piden el número de serie al llamar',     10, NULL),
    ('electricista','Electricidad Nogal',            'ELECTRICITY',     'Paco Nogal',    '+34 915 44 55 66', NULL,                             NULL,                                  NULL,                          NULL,                                     9,  NULL),
    ('cerrajero',   'Cerrajería 24 h Chamartín',     'LOCKSMITH',       NULL,            '+34 915 55 66 77', NULL,                             NULL,                                  NULL,                          'Urgencias, con recargo de noche',        9,  NULL),
    ('carpintero',  'Carpintería Hermanos Díaz',     'CARPENTRY',       'Andrés Díaz',   '+34 915 66 77 88', 'taller@hermanosdiaz.test',       NULL,                                  NULL,                          NULL,                                     8,  NULL),
    ('pintor',      'Pinturas y Reformas Ortega',    'PAINTING',        'Elena Ortega',  '+34 915 77 88 99', NULL,                             NULL,                                  NULL,                          'Presupuesto del pasillo, pendiente',     4,  NULL),
    ('limpieza',    'Limpiezas Aurora',              'CLEANING',        'Aurora Pinto',  '+34 615 88 99 00', NULL,                             NULL,                                  NULL,                          'Viene los martes por la mañana',         8,  NULL),
    ('jardineria',  'Vivero Terraza Verde',          'GARDENING',       NULL,            '+34 915 99 00 11', NULL,                             'https://terrazaverde.test',           NULL,                          NULL,                                     6,  NULL),
    ('plagas',      'Control de Plagas Bierzo',      'PEST_CONTROL',    NULL,            '+34 916 00 11 22', 'citas@plagasbierzo.test',        NULL,                                  NULL,                          'Trataron el trastero en primavera',      5,  NULL),
    ('taller-bici', 'Taller Rueda Libre',            'VEHICLE',         'Marco',         '+34 916 11 22 33', NULL,                             'https://ruedalibre.test',             'C/ Bravo Murillo 210, Madrid','Puesta a punto por 35 €',                7,  NULL),
    ('luz',         'Comercializadora Luz del Norte','UTILITIES',       NULL,            NULL,               'clientes@luzdelnorte.test',      'https://luzdelnorte.test',            NULL,                          'Contrato 3,3 kW, tarifa con discriminación horaria', 10, NULL),
    ('ferreteria',  'Ferretería La Llave',           'OTHER',           'Tomás',         '+34 916 22 33 44', NULL,                             NULL,                                  'C/ Santa Engracia 88, Madrid',NULL,                                     9,  NULL),
    ('antena',      'Antenas Sur',                   'ELECTRICITY',     NULL,            '+34 916 33 44 55', NULL,                             NULL,                                  NULL,                          'Ya no cogen el teléfono',                9,  4)
) AS v(clave, nombre, categoria, contacto, telefono, correo, web, direccion, notas, antiguedad, retirado);

-- Un enlace apunta a **exactamente uno** de asset o ubicación. El nombre que se
-- lee no se copia aquí: se resuelve del core al leer, así que renombrar la
-- caldera lo dice sin que nadie sincronice nada.
INSERT INTO supplier_links (id, household_id, supplier_id, asset_id, location_id, created_at, created_by)
SELECT pg_temp.demo_id('enlace:' || v.proveedor || ':' || coalesce(v.asset, v.ubicacion)),
       pg_temp.demo_id('hogar'), pg_temp.demo_id('proveedor:' || v.proveedor),
       CASE WHEN v.asset     IS NULL THEN NULL ELSE pg_temp.demo_id('asset:' || v.asset)         END,
       CASE WHEN v.ubicacion IS NULL THEN NULL ELSE pg_temp.demo_id('ubicacion:' || v.ubicacion) END,
       now() - (v.antiguedad || ' months')::interval, pg_temp.demo_id('miembro:marta')
FROM (VALUES
    ('fontaneria',  NULL,            'bano',         10),
    ('fontaneria',  NULL,            'cocina',       10),
    ('gas',         'caldera',       NULL,           10),
    ('sat-electro', 'lavadora',      NULL,           10),
    ('sat-electro', 'lavavajillas',  NULL,           10),
    ('sat-electro', 'frigorifico',   NULL,           10),
    ('electricista',NULL,            'vivienda',      9),
    ('cerrajero',   NULL,            'vivienda',      9),
    ('carpintero',  NULL,            'mueble-salon',  8),
    ('pintor',      NULL,            'pasillo',       4),
    ('limpieza',    NULL,            'vivienda',      8),
    ('jardineria',  NULL,            'terraza',       6),
    ('plagas',      NULL,            'trastero',      5),
    ('taller-bici', 'bici-hugo',     NULL,            7),
    ('taller-bici', 'patinete',      NULL,            7),
    ('luz',         NULL,            'vivienda',     10),
    ('antena',      NULL,            'vivienda',      9)
) AS v(proveedor, asset, ubicacion, antiguedad);


-- =====================================================================
-- 11. Almacén
-- =====================================================================
--
-- El módulo **no lleva un segundo contador**: la cantidad sigue siendo la del
-- core. Lo que guarda es lo que el core no sabe --mínimos, caducidad, lotes-- y
-- el cuaderno de movimientos.

-- 11.1 Fichas de artículo. Encender el módulo abre una por cada artículo con
-- existencia, sin mínimo: el mínimo lo pone después quien sabe cuánto gasta su
-- casa. Se deriva del inventario en lugar de repetir la lista, que es lo que
-- garantiza que no falte ninguna.
INSERT INTO warehouse_articles (id, household_id, article_id, created_at, updated_at, created_by, updated_by)
SELECT pg_temp.demo_id('almacen-articulo:' || a.article_id::text), pg_temp.demo_id('hogar'), a.article_id,
       now() - interval '10 months', now() - interval '10 months', NULL, NULL
FROM (SELECT DISTINCT article_id FROM assets WHERE type = 'CONSUMABLE' AND article_id IS NOT NULL) AS a;

UPDATE warehouse_articles w
   SET minimum_quantity = v.minimo,
       expiry_lead_days = v.antelacion,
       updated_at = now() - interval '8 months',
       updated_by = pg_temp.demo_id('miembro:marta')
  FROM (VALUES
    ('aceite', 2, NULL), ('arroz', 1, NULL), ('pasta', 1, NULL), ('lentejas', 0.5, NULL),
    ('garbanzos', 0.5, NULL), ('harina', 1, NULL), ('azucar', 0.5, NULL), ('sal', 0.5, NULL),
    ('leche', 6, 3), ('cafe', 250, NULL), ('cacao', 400, NULL), ('galletas', 400, NULL),
    ('atun', 6, NULL), ('tomate-frito', 3, NULL), ('agua', 6, NULL),
    ('detergente', 1.5, NULL), ('friegasuelos', 1, NULL), ('lejia', 1, NULL),
    ('lavavajillas-past', 20, NULL), ('multiusos', 250, NULL), ('bolsas-basura', 10, NULL),
    ('papel-cocina', 2, NULL), ('estropajos', 2, NULL),
    ('papel-higienico', 12, NULL), ('gel-ducha', 750, NULL), ('champu', 400, NULL),
    ('pasta-dientes', 75, NULL), ('cuchillas', 4, NULL),
    ('paracetamol', 10, NULL), ('ibuprofeno', 10, NULL),
    ('pilas-aa', 4, NULL), ('folios', 200, NULL)
  ) AS v(articulo, minimo, antelacion)
 WHERE w.article_id = pg_temp.demo_id('articulo:' || v.articulo);

-- **Estar bajo mínimos se calcula, no se escribe.** La fecha de entrada es lo que
-- distingue «acaba de caer» de «lleva tres semanas caído», y derivarla del
-- inventario en lugar de listarla a mano es lo que impide que este fichero diga
-- una cosa y las cantidades de arriba digan otra.
UPDATE warehouse_articles w
   SET low_stock_since = now() - interval '9 days',
       low_stock_notified_at = now() - interval '8 days'
 WHERE w.minimum_quantity IS NOT NULL
   AND (SELECT coalesce(sum(a.quantity), 0) FROM assets a
         WHERE a.article_id = w.article_id
           AND a.type = 'CONSUMABLE'
           AND a.status <> 'DECOMMISSIONED') <= w.minimum_quantity;

-- 11.2 Fichas de sitio, también derivadas de dónde hay existencias.
INSERT INTO warehouse_locations (id, household_id, location_id, created_at, updated_at, created_by, updated_by)
SELECT pg_temp.demo_id('almacen-ubicacion:' || l.location_id::text), pg_temp.demo_id('hogar'), l.location_id,
       now() - interval '10 months', now() - interval '10 months', NULL, NULL
FROM (SELECT DISTINCT location_id FROM assets WHERE type = 'CONSUMABLE' AND location_id IS NOT NULL) AS l;

UPDATE warehouse_locations w
   SET expiry_lead_days = v.antelacion, notes = v.notas,
       updated_at = now() - interval '8 months',
       updated_by = pg_temp.demo_id('miembro:marta')
  FROM (VALUES
    ('despensa',     10, 'Se revisa al hacer la lista de la compra'),
    ('armario-bano', 30, 'El botiquín, con más margen: una caja abierta dura meses')
  ) AS v(ubicacion, antelacion, notas)
 WHERE w.location_id = pg_temp.demo_id('ubicacion:' || v.ubicacion);

-- 11.3 Lotes. Dos caducados, dos en la ventana de aviso y el resto lejos; uno ya
-- dado por consumido, que es histórico y no cuenta para el índice de lotes vivos.
INSERT INTO warehouse_lots (id, household_id, asset_id, article_id, lot_code, expires_on, quantity,
                            consumed_at, notified_stage, created_at, updated_at, created_by, updated_by)
SELECT pg_temp.demo_id('lote:' || v.clave), pg_temp.demo_id('hogar'),
       pg_temp.demo_id('existencia:' || v.articulo || ':' || v.ubicacion),
       pg_temp.demo_id('articulo:' || v.articulo),
       v.codigo, (CURRENT_DATE + v.caducidad)::date, v.cantidad,
       CASE WHEN v.consumido IS NULL THEN NULL ELSE now() - (v.consumido || ' days')::interval END,
       v.avisado,
       now() - interval '4 months', now() - interval '4 months',
       pg_temp.demo_id('miembro:marta'), pg_temp.demo_id('miembro:marta')
FROM (VALUES
    ('leche',            'leche',            'despensa',     'L-2609',  -3,   2,    NULL, 'EXPIRED'),
    ('caldo',            'caldo',            'despensa',     NULL,      -12,  1,    NULL, 'EXPIRED'),
    ('tomate',           'tomate-frito',     'despensa',     NULL,       9,   2,    NULL, 'NEAR'),
    ('ibuprofeno',       'ibuprofeno',       'armario-bano', 'B4471',    25,  22,   NULL, 'NEAR'),
    ('galletas',         'galletas',         'despensa',     NULL,       25,  800,  NULL, NULL),
    ('galletas-anterior','galletas',         'despensa',     NULL,      -60,  800,  55,   NULL),
    ('atun-a',           'atun',             'despensa',     'A-772',    400, 6,    NULL, NULL),
    ('atun-b',           'atun',             'despensa',     'A-815',    200, 3,    NULL, NULL),
    ('arroz',            'arroz',            'despensa',     NULL,       300, 2,    NULL, NULL),
    ('pasta',            'pasta',            'despensa',     NULL,       240, 1.5,  NULL, NULL),
    ('lentejas',         'lentejas',         'despensa',     NULL,       500, 1,    NULL, NULL),
    ('harina',           'harina',           'despensa',     NULL,       120, 2,    NULL, NULL),
    ('azucar',           'azucar',           'despensa',     NULL,       600, 1,    NULL, NULL),
    ('cafe',             'cafe',             'despensa',     NULL,       90,  100,  NULL, NULL),
    ('aceitunas',        'aceitunas',        'despensa',     NULL,       60,  2,    NULL, NULL),
    ('chocolate',        'chocolate',        'despensa',     NULL,       150, 3,    NULL, NULL),
    ('aceite',           'aceite',           'despensa',     NULL,       330, 3,    NULL, NULL),
    ('agua-despensa',    'agua',             'despensa',     NULL,       365, 9,    NULL, NULL),
    ('agua-trastero',    'agua',             'estanteria',   NULL,       430, 9,    NULL, NULL),
    ('paracetamol',      'paracetamol',      'armario-bano', 'P-1180',   200, 14,   NULL, NULL),
    ('gel',              'gel-ducha',        'armario-bano', NULL,       700, 1200, NULL, NULL)
) AS v(clave, articulo, ubicacion, codigo, caducidad, cantidad, consumido, avisado);

-- 11.4 El cuaderno. Primero las existencias con historia --su apertura y lo que
-- pasó después--, y de estas la ÚLTIMA cantidad es por fuerza la que el core
-- tiene hoy: la comprobación del final del fichero lo verifica en lugar de
-- confiar en que cuadre.
INSERT INTO warehouse_movements (id, household_id, asset_id, article_id, location_id, location_name,
                                 kind, previous_quantity, quantity, occurred_at, event_id, created_at, created_by)
SELECT pg_temp.demo_id('movimiento:' || v.articulo || ':' || v.ubicacion || ':' || v.orden),
       pg_temp.demo_id('hogar'),
       pg_temp.demo_id('existencia:' || v.articulo || ':' || v.ubicacion),
       pg_temp.demo_id('articulo:' || v.articulo),
       pg_temp.demo_id('ubicacion:' || v.ubicacion),
       (SELECT name FROM locations WHERE id = pg_temp.demo_id('ubicacion:' || v.ubicacion)),
       v.tipo, v.previa, v.cantidad,
       now() - (v.dias || ' days')::interval, NULL,
       now() - (v.dias || ' days')::interval, NULL
FROM (VALUES
    ('leche',            'despensa',       1, 'OPENING',    0,    6,    300),
    ('leche',            'despensa',       2, 'INTAKE',     6,    12,   240),
    ('leche',            'despensa',       3, 'ADJUSTMENT', 12,   6,    180),
    ('leche',            'despensa',       4, 'INTAKE',     6,    12,   70),
    ('leche',            'despensa',       5, 'ADJUSTMENT', 12,   2,    5),
    ('cafe',             'despensa',       1, 'OPENING',    0,    500,  300),
    ('cafe',             'despensa',       2, 'ADJUSTMENT', 500,  250,  120),
    ('cafe',             'despensa',       3, 'INTAKE',     250,  500,  100),
    ('cafe',             'despensa',       4, 'ADJUSTMENT', 500,  100,  9),
    ('cacao',            'despensa',       1, 'OPENING',    0,    800,  300),
    ('cacao',            'despensa',       2, 'ADJUSTMENT', 800,  400,  150),
    ('cacao',            'despensa',       3, 'ADJUSTMENT', 400,  0,    11),
    ('aceite',           'despensa',       1, 'OPENING',    0,    5,    300),
    ('aceite',           'despensa',       2, 'ADJUSTMENT', 5,    2,    150),
    ('aceite',           'despensa',       3, 'INTAKE',     2,    7,    60),
    ('aceite',           'despensa',       4, 'ADJUSTMENT', 7,    3,    4),
    ('arroz',            'despensa',       1, 'OPENING',    0,    1,    300),
    ('arroz',            'despensa',       2, 'INTAKE',     1,    3,    90),
    ('arroz',            'despensa',       3, 'ADJUSTMENT', 3,    2,    15),
    ('atun',             'despensa',       1, 'OPENING',    0,    6,    300),
    ('atun',             'despensa',       2, 'INTAKE',     6,    12,   45),
    ('atun',             'despensa',       3, 'ADJUSTMENT', 12,   9,    7),
    ('tomate-frito',     'despensa',       1, 'OPENING',    0,    3,    300),
    ('tomate-frito',     'despensa',       2, 'INTAKE',     3,    6,    60),
    ('tomate-frito',     'despensa',       3, 'ADJUSTMENT', 6,    2,    3),
    ('agua',             'despensa',       1, 'OPENING',    0,    9,    300),
    ('agua',             'despensa',       2, 'ADJUSTMENT', 9,    3,    60),
    ('agua',             'despensa',       3, 'INTAKE',     3,    12,   30),
    ('agua',             'despensa',       4, 'ADJUSTMENT', 12,   9,    2),
    ('detergente',       'armario-cocina', 1, 'OPENING',    0,    3,    300),
    ('detergente',       'armario-cocina', 2, 'ADJUSTMENT', 3,    1.5,  120),
    ('detergente',       'armario-cocina', 3, 'INTAKE',     1.5,  4.5,  90),
    ('detergente',       'armario-cocina', 4, 'ADJUSTMENT', 4.5,  0.8,  8),
    ('lavavajillas-past','armario-cocina', 1, 'OPENING',    0,    60,   300),
    ('lavavajillas-past','armario-cocina', 2, 'ADJUSTMENT', 60,   30,   120),
    ('lavavajillas-past','armario-cocina', 3, 'INTAKE',     30,   90,   60),
    ('lavavajillas-past','armario-cocina', 4, 'ADJUSTMENT', 90,   42,   2),
    ('bolsas-basura',    'armario-cocina', 1, 'OPENING',    0,    20,   300),
    ('bolsas-basura',    'armario-cocina', 2, 'ADJUSTMENT', 20,   8,    90),
    ('bolsas-basura',    'armario-cocina', 3, 'INTAKE',     8,    28,   40),
    ('bolsas-basura',    'armario-cocina', 4, 'ADJUSTMENT', 28,   12,   1),
    ('papel-higienico',  'armario-bano',   1, 'OPENING',    0,    12,   300),
    ('papel-higienico',  'armario-bano',   2, 'ADJUSTMENT', 12,   6,    90),
    ('papel-higienico',  'armario-bano',   3, 'INTAKE',     6,    18,   60),
    ('papel-higienico',  'armario-bano',   4, 'ADJUSTMENT', 18,   4,    6),
    ('pilas-aa',         'estanteria',     1, 'OPENING',    0,    8,    300),
    ('pilas-aa',         'estanteria',     2, 'ADJUSTMENT', 8,    4,    180),
    ('pilas-aa',         'estanteria',     3, 'ADJUSTMENT', 4,    0,    22),
    ('agua',             'estanteria',     1, 'OPENING',    0,    9,    300)
) AS v(articulo, ubicacion, orden, tipo, previa, cantidad, dias);

-- Un cambio de sitio también va en este libro, y no cambia ninguna cantidad: por
-- eso las dos son nulas, que es lo que la restricción de la tabla exige.
INSERT INTO warehouse_movements (id, household_id, asset_id, article_id, location_id, location_name,
                                 kind, previous_quantity, quantity, occurred_at, event_id, created_at, created_by)
VALUES (
    pg_temp.demo_id('movimiento:agua:estanteria:2'), pg_temp.demo_id('hogar'),
    pg_temp.demo_id('existencia:agua:estanteria'), pg_temp.demo_id('articulo:agua'),
    pg_temp.demo_id('ubicacion:estanteria'), 'Estantería del trastero',
    'RELOCATION', NULL, NULL, now() - interval '20 days', NULL, now() - interval '20 days',
    pg_temp.demo_id('miembro:javier')
);

-- Y la apertura de todo lo demás: lo que había el día que se encendió el módulo,
-- que para estas existencias es lo que sigue habiendo hoy porque no se han
-- movido. El índice parcial admite un solo `OPENING` por existencia, así que la
-- condición de no duplicar es la misma que hace idempotente a la siembra real.
INSERT INTO warehouse_movements (id, household_id, asset_id, article_id, location_id, location_name,
                                 kind, previous_quantity, quantity, occurred_at, event_id, created_at, created_by)
SELECT pg_temp.demo_id('movimiento-apertura:' || a.id::text), pg_temp.demo_id('hogar'),
       a.id, a.article_id, a.location_id, l.name,
       'OPENING', 0, a.quantity,
       now() - interval '10 months', NULL, now() - interval '10 months', NULL
FROM assets a
LEFT JOIN locations l ON l.id = a.location_id
WHERE a.type = 'CONSUMABLE'
  AND a.status <> 'DECOMMISSIONED'
  AND NOT EXISTS (SELECT 1 FROM warehouse_movements m WHERE m.asset_id = a.id AND m.kind = 'OPENING');


-- =====================================================================
-- 12. Compras y lista de la compra
-- =====================================================================
--
-- El ciclo entero: lo que falta, lo que va en una compra abierta, lo que ya
-- entró en casa --con el asset que resultó, que es la traza de que se cerró-- y
-- lo que se descartó sin comprar.

INSERT INTO purchases (id, household_id, supplier_id, supplier_name, status, note,
                       received_at, cancelled_at, created_at, updated_at, created_by, updated_by)
SELECT pg_temp.demo_id('compra:' || v.clave), pg_temp.demo_id('hogar'),
       CASE WHEN v.proveedor IS NULL THEN NULL ELSE pg_temp.demo_id('proveedor:' || v.proveedor) END,
       v.nombre_proveedor, v.estado, v.nota,
       CASE WHEN v.recibida IS NULL THEN NULL ELSE now() - (v.recibida || ' days')::interval END,
       CASE WHEN v.anulada  IS NULL THEN NULL ELSE now() - (v.anulada  || ' days')::interval END,
       now() - (v.creada || ' days')::interval,
       now() - (coalesce(v.recibida, v.anulada, v.creada) || ' days')::interval,
       pg_temp.demo_id('miembro:marta'), pg_temp.demo_id('miembro:marta')
FROM (VALUES
    ('mensual',   NULL,         NULL,                   'RECEIVED',  'La compra grande del mes',                  92,  90,   NULL),
    ('ferreteria','ferreteria', 'Ferretería La Llave',  'RECEIVED',  'Brocas y tornillería para las baldas',      36,  35,   NULL),
    ('sabado',    NULL,         NULL,                   'OPEN',      'Lista del sábado',                           2,  NULL, NULL),
    ('anulada',   NULL,         NULL,                   'CANCELLED', 'Se anuló: fuimos al súper sin la lista',    45,  NULL, 44)
) AS v(clave, proveedor, nombre_proveedor, estado, nota, creada, recibida, anulada);

-- Las líneas vivas por falta detectada. **El origen no es alfabético ni casual**:
-- acabarse pesa más que bajar del mínimo, y las dos más que haberlo apuntado a
-- mano. Las tres que van en la compra abierta conservan su origen: entrar en una
-- compra no cambia por qué hacían falta.
INSERT INTO shopping_list_items (id, household_id, article_id, name, quantity, origin, status, note,
                                 purchase_id, received_asset_id, created_at, updated_at, created_by, updated_by)
SELECT pg_temp.demo_id('lista:' || v.clave), pg_temp.demo_id('hogar'),
       CASE WHEN v.articulo IS NULL THEN NULL ELSE pg_temp.demo_id('articulo:' || v.articulo) END,
       v.nombre, v.cantidad, v.origen, v.estado, v.nota,
       CASE WHEN v.compra IS NULL THEN NULL ELSE pg_temp.demo_id('compra:' || v.compra) END,
       CASE WHEN v.recibido IS NULL THEN NULL ELSE pg_temp.demo_id('existencia:' || v.recibido) END,
       now() - (v.creada || ' days')::interval,
       now() - (v.tocada || ' days')::interval,
       CASE WHEN v.origen = 'MANUAL' THEN pg_temp.demo_id('miembro:marta') ELSE NULL END,
       CASE WHEN v.origen = 'MANUAL' THEN pg_temp.demo_id('miembro:marta') ELSE NULL END
FROM (VALUES
    -- Se acabó: lo detecta el almacén y entra solo
    ('cacao',       'cacao',            NULL,                            800,  'DEPLETED',  'NEEDED',      'Hugo lleva una semana desayunando galletas', NULL,        NULL,                          11, 11),
    ('pilas',       'pilas-aa',         NULL,                            4,    'DEPLETED',  'NEEDED',      NULL,                                         NULL,        NULL,                          22, 22),
    -- Bajo mínimos
    ('garbanzos',   'garbanzos',        NULL,                            1,    'LOW_STOCK', 'NEEDED',      NULL,                                         NULL,        NULL,                           9,  9),
    ('cafe',        'cafe',             NULL,                            500,  'LOW_STOCK', 'NEEDED',      NULL,                                         NULL,        NULL,                           9,  9),
    ('tomate',      'tomate-frito',     NULL,                            6,    'LOW_STOCK', 'NEEDED',      NULL,                                         NULL,        NULL,                           9,  9),
    ('friegasuelos','friegasuelos',     NULL,                            1.5,  'LOW_STOCK', 'NEEDED',      NULL,                                         NULL,        NULL,                           9,  9),
    ('champu',      'champu',           NULL,                            400,  'LOW_STOCK', 'NEEDED',      'El de Lucía, sin siliconas',                 NULL,        NULL,                           9,  9),
    ('cuchillas',   'cuchillas',        NULL,                            4,    'LOW_STOCK', 'NEEDED',      NULL,                                         NULL,        NULL,                           9,  9),
    ('leche',       'leche',            NULL,                            12,   'LOW_STOCK', 'IN_PURCHASE', NULL,                                         'sabado',    NULL,                           9,  2),
    ('papel',       'papel-higienico',  NULL,                            12,   'LOW_STOCK', 'IN_PURCHASE', NULL,                                         'sabado',    NULL,                           9,  2),
    ('detergente',  'detergente',       NULL,                            3,    'LOW_STOCK', 'IN_PURCHASE', NULL,                                         'sabado',    NULL,                           9,  2),
    -- Apuntado a mano. Sin artículo es una línea de texto suelto: completa, pero
    -- no puede entrar en el inventario al recibirla
    ('bombillas',   NULL,               'Bombillas LED E27',             4,    'MANUAL',    'IN_PURCHASE', 'Las del pasillo, luz cálida',                'sabado',    NULL,                           6,  2),
    ('cr2032',      NULL,               'Pilas de botón CR2032',         2,    'MANUAL',    'NEEDED',      'Para la báscula del baño',                   NULL,        NULL,                           5,  5),
    ('embalar',     NULL,               'Cinta de embalar',              NULL, 'MANUAL',    'NEEDED',      NULL,                                         NULL,        NULL,                          14, 14),
    ('chocolate',   'chocolate',        NULL,                            3,    'MANUAL',    'NEEDED',      NULL,                                         NULL,        NULL,                           4,  4),
    -- Histórico: lo que ya entró en casa, con la existencia que resultó
    ('h-arroz',     'arroz',            NULL,                            2,    'LOW_STOCK', 'BOUGHT',      NULL,                                         'mensual',   'arroz:despensa',              95, 90),
    ('h-atun',      'atun',             NULL,                            6,    'LOW_STOCK', 'BOUGHT',      NULL,                                         'mensual',   'atun:despensa',               95, 90),
    ('h-aceite',    'aceite',           NULL,                            5,    'LOW_STOCK', 'BOUGHT',      NULL,                                         'mensual',   'aceite:despensa',             95, 90),
    ('h-pastillas', 'lavavajillas-past',NULL,                            60,   'LOW_STOCK', 'BOUGHT',      NULL,                                         'mensual',   'lavavajillas-past:armario-cocina', 95, 90),
    ('h-bolsas',    'bolsas-basura',    NULL,                            20,   'DEPLETED',  'BOUGHT',      NULL,                                         'mensual',   'bolsas-basura:armario-cocina',95, 90),
    ('h-brocas',    NULL,               'Brocas de widia del 6',         3,    'MANUAL',    'BOUGHT',      NULL,                                         'ferreteria', NULL,                         38, 35),
    ('h-tacos',     NULL,               'Tacos y tornillos surtidos',    NULL, 'MANUAL',    'BOUGHT',      NULL,                                         'ferreteria', NULL,                         38, 35),
    -- Descartado sin comprar, que no es lo mismo que anular una compra
    ('d-freidora',  NULL,               'Freidora de aire',              1,    'MANUAL',    'DISMISSED',   'Lo hablamos y no cabe en la cocina',         NULL,        NULL,                          70, 60),
    ('d-capsulas',  NULL,               'Cápsulas de café compatibles',  50,   'MANUAL',    'DISMISSED',   'Mejor café molido',                          NULL,        NULL,                          80, 75)
) AS v(clave, articulo, nombre, cantidad, origen, estado, nota, compra, recibido, creada, tocada);


-- =====================================================================
-- 13. Mantenimiento (CMMS)
-- =====================================================================
--
-- Encender el módulo abre **la ficha de cada máquina y ningún plan**: una caldera
-- pide revisión anual y una silla no pide nada, y el core no sabe distinguirlas.
-- Así que las fichas se derivan del inventario y los planes se escriben uno a uno,
-- como los pondría quien sabe qué tiene en casa.

INSERT INTO maintenance_items (id, household_id, asset_id, manual_document_id, notes,
                               created_at, updated_at, created_by, updated_by)
SELECT pg_temp.demo_id('maquina:' || a.id::text), pg_temp.demo_id('hogar'), a.id, NULL, NULL,
       now() - interval '9 months', now() - interval '9 months', NULL, NULL
FROM assets a
WHERE a.type = 'DURABLE' AND a.status <> 'DECOMMISSIONED';

-- El manual de una máquina tiene que ser un documento de ESA máquina, así que
-- solo lo tienen las dos cuyo manual cuelga del asset y no del artículo.
UPDATE maintenance_items m
   SET manual_document_id = pg_temp.demo_id('documento:' || v.documento),
       notes = v.notas,
       updated_at = now() - interval '6 months',
       updated_by = pg_temp.demo_id('miembro:javier')
  FROM (VALUES
    ('caldera',      'manual-caldera', 'La llave de paso está detrás de la lavadora'),
    ('horno',        'manual-horno',   'La resistencia de arriba calienta de menos')
  ) AS v(asset, documento, notas)
 WHERE m.asset_id = pg_temp.demo_id('asset:' || v.asset);

UPDATE maintenance_items m
   SET notes = v.notas, updated_at = now() - interval '5 months', updated_by = pg_temp.demo_id('miembro:marta')
  FROM (VALUES
    ('lavadora', 'Filtro abajo a la derecha, detrás de la tapa'),
    ('aire',     'Los filtros se lavan con agua templada y se secan a la sombra'),
    ('robot',    'Recambios: cepillo lateral cada seis meses'),
    ('bici-hugo','Cambiar a talla de cuadro más grande el año que viene')
  ) AS v(asset, notas)
 WHERE m.asset_id = pg_temp.demo_id('asset:' || v.asset);

-- Un plan es una REGLA recurrente, no un encargo: no lleva responsable ni día
-- concreto. `nextDueOn` se guarda y no se calcula al leer, porque de ella cuelga
-- el estado del aviso; `lastPerformedOn` se deriva aquí para que no pueda
-- contradecirla.
INSERT INTO maintenance_plans (id, household_id, asset_id, name, interval_months, lead_days,
                               next_due_on, last_performed_on, supplier_id, notes,
                               notified_stage, notified_for, cancelled_at,
                               created_at, updated_at, created_by, updated_by)
SELECT pg_temp.demo_id('plan:' || v.clave), pg_temp.demo_id('hogar'),
       pg_temp.demo_id('asset:' || v.asset), v.nombre, v.intervalo, v.antelacion,
       (CURRENT_DATE + v.proxima)::date,
       ((CURRENT_DATE + v.proxima) - (v.intervalo || ' months')::interval)::date,
       CASE WHEN v.proveedor IS NULL THEN NULL ELSE pg_temp.demo_id('proveedor:' || v.proveedor) END,
       v.notas, v.avisado,
       CASE WHEN v.avisado IS NULL THEN NULL ELSE (CURRENT_DATE + v.proxima)::date END,
       CASE WHEN v.anulado IS NULL THEN NULL ELSE now() - (v.anulado || ' months')::interval END,
       now() - interval '9 months', now() - interval '1 month',
       pg_temp.demo_id('miembro:javier'), pg_temp.demo_id('miembro:javier')
FROM (VALUES
    ('caldera-anual',  'caldera',      'Revisión anual del gas',        12, 30,  18,  'gas',         'Obligatoria por el contrato de mantenimiento', 'DUE_SOON', NULL),
    ('caldera-purga',  'caldera',      'Purgado de radiadores',         12, 15,  120, NULL,          'Antes de encender la calefacción',             NULL,       NULL),
    ('aire-filtros',   'aire',         'Limpieza de filtros',            6, 15,  -12, NULL,          NULL,                                           'OVERDUE',  NULL),
    ('lavadora-goma',  'lavadora',     'Limpieza de goma y filtro',      3, 10,  40,  'sat-electro', NULL,                                           NULL,       NULL),
    ('lavavajillas-cal','lavavajillas','Descalcificación',               6, 15,  75,  NULL,          NULL,                                           NULL,       NULL),
    ('robot-cepillos', 'robot',        'Cambio de cepillos y filtro',    6, 15,  55,  NULL,          NULL,                                           NULL,       NULL),
    ('campana-filtros','campana',      'Cambio de filtros de carbón',    6, 20,  100, NULL,          NULL,                                           NULL,       NULL),
    ('frigo-condensador','frigorifico','Limpieza del condensador',      12, 20,  260, 'sat-electro', NULL,                                           NULL,       NULL),
    ('bici-puesta',    'bici-hugo',    'Puesta a punto',                12, 30,  200, 'taller-bici', NULL,                                           NULL,       NULL),
    ('barbacoa-quemadores','barbacoa', 'Limpieza de quemadores',        12, 20,  150, NULL,          'Antes de la primera del año',                  NULL,       NULL),
    ('aspirador-bolsa','aspirador',    'Cambio de bolsa',                3,  7,  -60, NULL,          'Se anuló: ahora se compran cuando toca',       NULL,       5)
) AS v(clave, asset, nombre, intervalo, antelacion, proxima, proveedor, notas, avisado, anulado);

-- El histórico es un LIBRO: se escribe y no se toca. La última intervención de
-- cada plan se deriva de su `lastPerformedOn` --así no puede decir otra cosa que
-- el plan-- y la anterior, restando el periodo, mientras caiga dentro de la vida
-- del hogar.
INSERT INTO maintenance_interventions (id, household_id, asset_id, plan_id, kind, performed_on, summary,
                                       supplier_id, supplier_name, notes, created_at, created_by)
SELECT pg_temp.demo_id('intervencion:' || v.plan || ':ultima'), pg_temp.demo_id('hogar'),
       p.asset_id, p.id, 'PREVENTIVE', p.last_performed_on, v.resumen,
       p.supplier_id, s.name, v.notas,
       p.last_performed_on::timestamptz + interval '19 hours', pg_temp.demo_id('miembro:javier')
FROM (VALUES
    ('caldera-anual',      'Revisión anual y certificado de la instalación', 'Cambiaron el ánodo'),
    ('caldera-purga',      'Purgados los cinco radiadores',                  NULL),
    ('aire-filtros',       'Filtros lavados y secados',                      NULL),
    ('lavadora-goma',      'Goma limpiada y filtro vaciado',                 'Salieron dos monedas y un calcetín'),
    ('lavavajillas-cal',   'Descalcificado con producto específico',         NULL),
    ('robot-cepillos',     'Cepillos y filtro cambiados',                    NULL),
    ('campana-filtros',    'Filtros de carbón nuevos',                       NULL),
    ('frigo-condensador',  'Condensador aspirado por detrás',                NULL),
    ('bici-puesta',        'Puesta a punto completa y cambio de cámaras',    NULL),
    ('barbacoa-quemadores','Quemadores desmontados y limpiados',             NULL)
) AS v(plan, resumen, notas)
JOIN maintenance_plans p ON p.id = pg_temp.demo_id('plan:' || v.plan)
LEFT JOIN suppliers s ON s.id = p.supplier_id;

INSERT INTO maintenance_interventions (id, household_id, asset_id, plan_id, kind, performed_on, summary,
                                       supplier_id, supplier_name, notes, created_at, created_by)
SELECT pg_temp.demo_id('intervencion:' || v.plan || ':anterior'), pg_temp.demo_id('hogar'),
       p.asset_id, p.id, 'PREVENTIVE',
       (p.last_performed_on - (p.interval_months || ' months')::interval)::date,
       v.resumen, p.supplier_id, s.name, NULL,
       (p.last_performed_on - (p.interval_months || ' months')::interval)::timestamptz + interval '18 hours',
       pg_temp.demo_id('miembro:javier')
FROM (VALUES
    ('caldera-anual',      'Revisión anual y certificado de la instalación'),
    ('aire-filtros',       'Filtros lavados'),
    ('lavadora-goma',      'Goma limpiada y filtro vaciado'),
    ('lavavajillas-cal',   'Descalcificado'),
    ('robot-cepillos',     'Cepillos cambiados'),
    ('campana-filtros',    'Filtros de carbón nuevos')
) AS v(plan, resumen)
JOIN maintenance_plans p ON p.id = pg_temp.demo_id('plan:' || v.plan)
LEFT JOIN suppliers s ON s.id = p.supplier_id
WHERE (p.last_performed_on - (p.interval_months || ' months')::interval) > CURRENT_DATE - 400;

-- Lo correctivo nunca cuelga de un plan: arregla algo que se ha roto. El nombre
-- del proveedor se copia --al revés que en un plan-- porque una intervención es
-- historia y tiene que seguir siendo cierta aunque el contacto se retire.
INSERT INTO maintenance_interventions (id, household_id, asset_id, plan_id, kind, performed_on, summary,
                                       supplier_id, supplier_name, notes, created_at, created_by)
SELECT pg_temp.demo_id('intervencion:' || v.clave), pg_temp.demo_id('hogar'),
       pg_temp.demo_id('asset:' || v.asset), NULL, 'CORRECTIVE',
       (CURRENT_DATE - v.dias)::date, v.resumen,
       CASE WHEN v.proveedor IS NULL THEN NULL ELSE pg_temp.demo_id('proveedor:' || v.proveedor) END,
       v.nombre_proveedor, v.notas,
       (CURRENT_DATE - v.dias)::timestamptz + interval '20 hours',
       pg_temp.demo_id('miembro:marta')
FROM (VALUES
    ('lavadora-goma-rota','lavadora',    220, 'Cambio de la goma de la puerta',            'sat-electro','Servicio Técnico Elecdomes', 'Perdía agua por abajo'),
    ('caldera-vaso',      'caldera',     300, 'Sustitución del vaso de expansión',         'gas',        'Instalaciones Vallés',       'La presión bajaba cada semana'),
    ('lavavajillas-atasco','lavavajillas',160,'Desatasco del filtro y del desagüe',        'fontaneria', 'Fontanería Ramírez',         NULL),
    ('patinete-rueda',    'patinete',     95, 'Cambio de la rueda trasera',                NULL,         NULL,                          'Lo hizo Javier con el kit'),
    ('tv-firmware',       'tv',           60, 'Actualización de firmware y ajuste de imagen', NULL,      NULL,                          NULL),
    ('router-reinicio',   'router',      130, 'Sustitución del router por avería de línea','luz',        'Comercializadora Luz del Norte', 'Lo trajo el técnico del operador')
) AS v(clave, asset, dias, resumen, proveedor, nombre_proveedor, notas);


-- =====================================================================
-- 14. Avisos
-- =====================================================================
--
-- Un aviso **guarda su texto dentro** (ADR-011): dice lo que era cierto el día
-- que se escribió, y por eso sigue siendo legible aunque el lote ya no exista.
-- Los del core no llevan módulo; los de los módulos, el suyo.

INSERT INTO household_notices (id, household_id, module_key, kind, title, body, created_at,
                               read_at, read_by, notified_at)
SELECT pg_temp.demo_id('aviso:' || v.clave), pg_temp.demo_id('hogar'), v.modulo, v.tipo, v.titulo, v.cuerpo,
       now() - (v.dias || ' days')::interval,
       CASE WHEN v.leido IS NULL THEN NULL ELSE now() - (v.leido || ' days')::interval END,
       CASE WHEN v.leido IS NULL THEN NULL ELSE pg_temp.demo_id('miembro:' || v.lector) END,
       CASE WHEN v.avisado IS NULL THEN NULL ELSE now() - (v.avisado || ' days')::interval END
FROM (VALUES
    ('leche-caducada',   'WAREHOUSE',   'WAREHOUSE_EXPIRED',             'Leche entera ha caducado',
     'Caducó el ' || to_char(CURRENT_DATE - 3, 'YYYY-MM-DD') || ' (lote L-2609). Míralo antes de usarlo.',          3,  NULL,  NULL,     2),
    ('caldo-caducado',   'WAREHOUSE',   'WAREHOUSE_EXPIRED',             'Caldo de pollo ha caducado',
     'Caducó el ' || to_char(CURRENT_DATE - 12, 'YYYY-MM-DD') || '. Míralo antes de usarlo.',                      12,  10,   'marta', 11),
    ('tomate-caduca',    'WAREHOUSE',   'WAREHOUSE_EXPIRY_NEAR',         'Tomate frito caduca pronto',
     'Caduca el ' || to_char(CURRENT_DATE + 9, 'YYYY-MM-DD') || '. Todavía estás a tiempo de gastarlo.',            1,  NULL,  NULL,  NULL),
    ('ibuprofeno-caduca','WAREHOUSE',   'WAREHOUSE_EXPIRY_NEAR',         'Ibuprofeno 600 mg caduca pronto',
     'Caduca el ' || to_char(CURRENT_DATE + 25, 'YYYY-MM-DD') || ' (lote B4471). Todavía estás a tiempo de gastarlo.', 2, NULL, NULL,   1),
    ('papel-minimo',     'WAREHOUSE',   'WAREHOUSE_STOCK_BELOW_MINIMUM', 'Queda poco de Papel higiénico',
     'Quedan 10 unit y el mínimo que fijaste son 12.',                                                              8,   7,   'javier', 7),
    ('leche-minimo',     'WAREHOUSE',   'WAREHOUSE_STOCK_BELOW_MINIMUM', 'Queda poco de Leche entera',
     'Quedan 2 liter y el mínimo que fijaste son 6.',                                                               8,  NULL,  NULL,    7),
    ('cafe-minimo',      'WAREHOUSE',   'WAREHOUSE_STOCK_BELOW_MINIMUM', 'Queda poco de Café molido natural',
     'Quedan 100 gram y el mínimo que fijaste son 250.',                                                            8,   6,   'marta',  7),
    ('detergente-minimo','WAREHOUSE',   'WAREHOUSE_STOCK_BELOW_MINIMUM', 'Queda poco de Detergente líquido para lavadora',
     'Quedan 0.8 liter y el mínimo que fijaste son 1.5.',                                                           8,  NULL,  NULL,    7),
    ('aire-vencido',     'MAINTENANCE', 'MAINTENANCE_OVERDUE',           'Aire acondicionado split lleva sin revisar',
     '«Limpieza de filtros» tocaba el ' || to_char(CURRENT_DATE - 12, 'YYYY-MM-DD') || ' y sigue sin registrarse. Apúntalo cuando esté hecho.', 5, NULL, NULL, 4),
    ('caldera-toca',     'MAINTENANCE', 'MAINTENANCE_DUE_SOON',          'Toca revisar: Caldera mural de gas',
     '«Revisión anual del gas» toca el ' || to_char(CURRENT_DATE + 18, 'YYYY-MM-DD') || '. Si hace falta llamar a alguien, vas a tiempo.', 11, 9, 'javier', 10),
    ('prestamo-vencido', NULL,          'LOANS_OVERDUE',                 '1 préstamo ha vencido',
     'Nevera portátil. Siguen contando como prestados hasta que confirmes la devolución.',                         19,  17,   'javier', 18)
) AS v(clave, modulo, tipo, titulo, cuerpo, dias, leido, lector, avisado);


-- =====================================================================
-- 15. Comprobaciones
-- =====================================================================
--
-- Un juego de datos que se contradice a sí mismo es peor que no tenerlo: enseña
-- una pantalla que el uso normal no puede producir y manda a diagnosticar un
-- fallo que no existe. Estas cuatro afirmaciones son las que este fichero podría
-- romper al editarlo, así que se comprueban aquí y **abortan la carga entera**.

DO $comprobaciones$
DECLARE
    fallo text;
BEGIN
    -- 1. La última cantidad del cuaderno es la que el core tiene hoy.
    SELECT string_agg(t.nombre, ', ') INTO fallo
    FROM (
        SELECT coalesce(ar.name, 'sin artículo') AS nombre
        FROM assets a
        JOIN articles ar ON ar.id = a.article_id
        JOIN LATERAL (
            SELECT m.quantity
            FROM warehouse_movements m
            WHERE m.asset_id = a.id AND m.kind <> 'RELOCATION'
            ORDER BY m.occurred_at DESC
            LIMIT 1
        ) ultimo ON true
        WHERE a.type = 'CONSUMABLE' AND a.status <> 'DECOMMISSIONED'
          AND ultimo.quantity <> a.quantity
    ) t;
    IF fallo IS NOT NULL THEN
        RAISE EXCEPTION 'El cuaderno no cuadra con el inventario en: %', fallo;
    END IF;

    -- 2. Una apertura por existencia viva, ni ninguna ni dos.
    SELECT count(*) FILTER (WHERE m.id IS NULL) INTO fallo
    FROM assets a
    LEFT JOIN warehouse_movements m ON m.asset_id = a.id AND m.kind = 'OPENING'
    WHERE a.type = 'CONSUMABLE' AND a.status <> 'DECOMMISSIONED';
    IF fallo <> '0' THEN
        RAISE EXCEPTION '% existencias vivas se han quedado sin asiento de apertura', fallo;
    END IF;

    -- 3. La próxima fecha de un plan es la última hecha más su periodo.
    SELECT string_agg(p.name, ', ') INTO fallo
    FROM maintenance_plans p
    WHERE p.cancelled_at IS NULL
      AND p.last_performed_on IS NOT NULL
      AND (p.last_performed_on + (p.interval_months || ' months')::interval)::date <> p.next_due_on;
    IF fallo IS NOT NULL THEN
        RAISE EXCEPTION 'La próxima revisión no cuadra con la última hecha en: %', fallo;
    END IF;

    -- 4. Todo lo que está bajo mínimos tiene su línea viva en la lista, y al
    --    revés: es la promesa que Compras hace al encenderse junto a Almacén.
    SELECT string_agg(ar.name, ', ') INTO fallo
    FROM warehouse_articles w
    JOIN articles ar ON ar.id = w.article_id
    WHERE w.low_stock_since IS NOT NULL
      AND NOT EXISTS (
          SELECT 1 FROM shopping_list_items i
          WHERE i.article_id = w.article_id AND i.status IN ('NEEDED', 'IN_PURCHASE')
      );
    IF fallo IS NOT NULL THEN
        RAISE EXCEPTION 'Bajo mínimos y sin línea en la lista de la compra: %', fallo;
    END IF;
END
$comprobaciones$;

COMMIT;

-- Lo que ha entrado, para poder mirarlo de un vistazo al terminar.
SELECT 'hogares'        AS tabla, count(*) FROM households
UNION ALL SELECT 'personas',      count(*) FROM household_members
UNION ALL SELECT 'categorías',    count(*) FROM categories
UNION ALL SELECT 'etiquetas',    count(*) FROM tags
UNION ALL SELECT 'etiquetado',   count(*) FROM asset_tags
UNION ALL SELECT 'ubicaciones',   count(*) FROM locations
UNION ALL SELECT 'artículos',     count(*) FROM articles
UNION ALL SELECT 'assets',        count(*) FROM assets
UNION ALL SELECT 'documentos',    count(*) FROM documents
UNION ALL SELECT 'préstamos',     count(*) FROM loans
UNION ALL SELECT 'proveedores',   count(*) FROM suppliers
UNION ALL SELECT 'enlaces',       count(*) FROM supplier_links
UNION ALL SELECT 'lotes',         count(*) FROM warehouse_lots
UNION ALL SELECT 'movimientos',   count(*) FROM warehouse_movements
UNION ALL SELECT 'lista compra',  count(*) FROM shopping_list_items
UNION ALL SELECT 'compras',       count(*) FROM purchases
UNION ALL SELECT 'máquinas',      count(*) FROM maintenance_items
UNION ALL SELECT 'planes',        count(*) FROM maintenance_plans
UNION ALL SELECT 'intervenciones',count(*) FROM maintenance_interventions
UNION ALL SELECT 'avisos',        count(*) FROM household_notices;
