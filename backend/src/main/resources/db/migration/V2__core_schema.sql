-- Esquema completo del core: quince tablas (ver data-model.md, seccion 5.6).
--
-- Se crea entero de una vez aunque el Hito 1 solo use ocho de las tablas. El
-- motivo es la ADR-004: el arranque en limpio de una base vacia tiene que
-- producir el esquema completo, politicas incluidas, y partirlo en trozos por
-- hito significaria que ningun entorno tiene el modelo entero hasta el final.
--
-- Cinco tablas no llevan household_id y por tanto quedan fuera de RLS
-- (V3__row_level_security.sql): identities y las tres de tokens que cuelgan de
-- ella, mas loan_access_tokens, que cuelga del prestamo.

-- ---------------------------------------------------------------------------
-- households e identidad
-- ---------------------------------------------------------------------------

-- La unidad de aislamiento. No lleva autoria porque en el instante de crearse no
-- existe todavia ningun usuario al que apuntar.
CREATE TABLE households (
    id         uuid PRIMARY KEY,
    name       text NOT NULL,
    -- Identificador IANA. Lo valida el caso de uso, no un CHECK: la lista de
    -- husos cambia con el tiempo y congelarla aqui obligaria a migrar el dia que
    -- se anada uno.
    time_zone  text        NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

-- Quien eres en la instalacion, no que eres en un hogar. Es la unica tabla con
-- datos personales que no puede tener politica de RLS, porque no pertenece a
-- ningun hogar: su control de acceso vive solo en la aplicacion.
CREATE TABLE identities (
    id                  uuid PRIMARY KEY,
    name                text NOT NULL,
    email               text NOT NULL,
    phone               text,
    password_hash       text NOT NULL,
    avatar_url          text,
    avatar_storage_key  text,
    avatar_content_type text,
    avatar_size_bytes   bigint,
    email_verified_at   timestamptz,
    last_login_at       timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    deactivated_at      timestamptz,
    -- Enlace externo o fichero subido, nunca los dos.
    CONSTRAINT identities_avatar_single_source
        CHECK (avatar_url IS NULL OR avatar_storage_key IS NULL),
    -- 1 MB de tope. Una identidad no tiene hogar al que cargarle cuota.
    CONSTRAINT identities_avatar_size
        CHECK (avatar_size_bytes IS NULL OR avatar_size_bytes <= 1048576)
);

-- Unico en toda la instalacion y comparado en minusculas: sin el lower(),
-- Kike@x.com y kike@x.com serian dos cuentas. No es parcial por baja --la
-- identidad sobrevive a cualquier hogar, asi que su correo no se libera.
CREATE UNIQUE INDEX identities_email_unique ON identities (lower(email));

-- Que eres dentro de un hogar concreto. Todo lo que el dominio llama "usuario"
-- --propietario de un asset, prestador, receptor, created_by-- apunta aqui.
CREATE TABLE household_members (
    id             uuid PRIMARY KEY,
    household_id   uuid        NOT NULL REFERENCES households (id) ON DELETE CASCADE,
    identity_id    uuid        NOT NULL REFERENCES identities (id) ON DELETE CASCADE,
    role           text        NOT NULL,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    deactivated_at timestamptz,
    created_by     uuid,
    updated_by     uuid,
    CONSTRAINT household_members_role_valid
        CHECK (role IN ('HOUSEHOLD_ADMIN', 'HOUSEHOLD_MEMBER')),
    -- Nadie pertenece dos veces al mismo hogar.
    CONSTRAINT household_members_identity_unique UNIQUE (household_id, identity_id),
    -- Lo que permite declarar las claves ajenas compuestas de autoria: sin este
    -- UNIQUE, ninguna tabla podria referenciar (household_id, id).
    CONSTRAINT household_members_household_scoped_id UNIQUE (household_id, id)
);

-- En el MVP una identidad tiene como mucho una pertenencia activa. Retirar este
-- indice es todo lo que hara falta el dia que se admitan varias.
CREATE UNIQUE INDEX household_members_single_active_membership
    ON household_members (identity_id)
    WHERE deactivated_at IS NULL;

-- La autoria de la pertenencia apunta a la propia tabla. Con created_by a nulo
-- --el primer administrador, que no lo creo nadie de dentro-- la clave ajena no
-- se comprueba, que es justo lo que hace falta para que el alta de un hogar
-- pueda insertar su primera fila.
ALTER TABLE household_members ADD CONSTRAINT household_members_created_by_same_household
    FOREIGN KEY (household_id, created_by) REFERENCES household_members (household_id, id);
ALTER TABLE household_members ADD CONSTRAINT household_members_updated_by_same_household
    FOREIGN KEY (household_id, updated_by) REFERENCES household_members (household_id, id);

-- ---------------------------------------------------------------------------
-- Enrolamiento: invitaciones y tokens que viajan por correo
-- ---------------------------------------------------------------------------

-- El estado no es una columna: se deduce de expires_at, accepted_at y revoked_at.
CREATE TABLE invitations (
    id           uuid PRIMARY KEY,
    household_id uuid        NOT NULL REFERENCES households (id) ON DELETE CASCADE,
    email        text        NOT NULL,
    role         text        NOT NULL,
    token_hash   text        NOT NULL UNIQUE,
    expires_at   timestamptz NOT NULL,
    accepted_at  timestamptz,
    revoked_at   timestamptz,
    created_at   timestamptz NOT NULL DEFAULT now(),
    created_by   uuid,
    CONSTRAINT invitations_role_valid
        CHECK (role IN ('HOUSEHOLD_ADMIN', 'HOUSEHOLD_MEMBER'))
);

-- Una sola invitacion viva por correo y hogar.
CREATE UNIQUE INDEX invitations_one_live_per_email
    ON invitations (household_id, lower(email))
    WHERE accepted_at IS NULL AND revoked_at IS NULL;

ALTER TABLE invitations ADD CONSTRAINT invitations_created_by_same_household
    FOREIGN KEY (household_id, created_by) REFERENCES household_members (household_id, id);

-- Tabla propia y no un "purpose" compartido con el restablecimiento: con una
-- sola tabla, un filtro mal escrito convierte un token de verificacion en uno de
-- cambio de contrasena, que es una clase de vulnerabilidad conocida.
CREATE TABLE email_verification_tokens (
    id          uuid PRIMARY KEY,
    identity_id uuid        NOT NULL REFERENCES identities (id) ON DELETE CASCADE,
    token_hash  text        NOT NULL UNIQUE,
    expires_at  timestamptz NOT NULL,
    used_at     timestamptz
);

CREATE INDEX email_verification_tokens_by_identity
    ON email_verification_tokens (identity_id);

CREATE TABLE password_reset_tokens (
    id          uuid PRIMARY KEY,
    identity_id uuid        NOT NULL REFERENCES identities (id) ON DELETE CASCADE,
    token_hash  text        NOT NULL UNIQUE,
    expires_at  timestamptz NOT NULL,
    used_at     timestamptz
);

-- Un solo token vivo por identidad: pedir uno nuevo marca el anterior como usado
-- antes de insertar.
CREATE UNIQUE INDEX password_reset_tokens_one_live_per_identity
    ON password_reset_tokens (identity_id)
    WHERE used_at IS NULL;

-- Cuelgan de la identidad, no de la pertenencia: la sesion es de la persona, no
-- de su papel. Se marcan revocados en lugar de borrarse, para poder auditar.
CREATE TABLE refresh_tokens (
    id          uuid PRIMARY KEY,
    identity_id uuid        NOT NULL REFERENCES identities (id) ON DELETE CASCADE,
    token_hash  text        NOT NULL UNIQUE,
    expires_at  timestamptz NOT NULL,
    revoked_at  timestamptz
);

CREATE INDEX refresh_tokens_by_identity ON refresh_tokens (identity_id);

-- ---------------------------------------------------------------------------
-- Catalogo: categorias, ficheros y articulos
-- ---------------------------------------------------------------------------

-- Clasificacion funcional del hogar. Los nombres son datos que el usuario ve y
-- edita, asi que van en su idioma: no son valores de un enumerado.
CREATE TABLE categories (
    id           uuid PRIMARY KEY,
    household_id uuid        NOT NULL REFERENCES households (id) ON DELETE CASCADE,
    name         text        NOT NULL,
    notes        text,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    retired_at   timestamptz,
    created_by   uuid,
    updated_by   uuid
);

-- Unico entre las vigentes del hogar, sin distinguir mayusculas ni acentos. La
-- retirada es logica porque assets y articles la referencian.
CREATE UNIQUE INDEX categories_name_unique_live
    ON categories (household_id, lower(immutable_unaccent(name)))
    WHERE retired_at IS NULL;

ALTER TABLE categories ADD CONSTRAINT categories_created_by_same_household
    FOREIGN KEY (household_id, created_by) REFERENCES household_members (household_id, id);
ALTER TABLE categories ADD CONSTRAINT categories_updated_by_same_household
    FOREIGN KEY (household_id, updated_by) REFERENCES household_members (household_id, id);

-- Los bytes viven en disco; aqui solo los metadatos. El borrado es logico aunque
-- no lo referencie nadie, porque los bytes los desenlaza el proceso diario:
-- borrar la fila en el acto dejaria el fichero en disco sin nadie que supiera
-- que sobra.
CREATE TABLE files (
    id            uuid PRIMARY KEY,
    household_id  uuid        NOT NULL REFERENCES households (id) ON DELETE CASCADE,
    original_name text        NOT NULL,
    content_type  text        NOT NULL,
    size_bytes    bigint      NOT NULL,
    checksum      text        NOT NULL,
    storage_key   text        NOT NULL UNIQUE,
    created_at    timestamptz NOT NULL DEFAULT now(),
    created_by    uuid,
    -- A nulo marca la reserva de una subida en curso: ya ocupa cuota y todavia
    -- no se puede adjuntar.
    uploaded_at   timestamptz,
    deleted_at    timestamptz,
    -- 25 MB de tope por fichero. La cuota de 1 GB por hogar no cabe aqui: es una
    -- suma sobre las filas vivas y se valida en el caso de uso con la fila del
    -- hogar bloqueada.
    CONSTRAINT files_size_bounds
        CHECK (size_bytes > 0 AND size_bytes <= 26214400),
    -- La lista blanca de 5.8.3, expresada tambien aqui: ampliarla exige una
    -- migracion, y esa friccion es deliberada.
    CONSTRAINT files_content_type_allowed
        CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp', 'application/pdf')),
    -- Lo que permite declarar las claves ajenas compuestas que impiden adjuntar
    -- el fichero de otro hogar.
    CONSTRAINT files_household_scoped_id UNIQUE (household_id, id)
);

-- Para que la suma de la cuota no recorra los borrados.
CREATE INDEX files_live_by_household ON files (household_id) WHERE deleted_at IS NULL;

-- Un fichero no se modifica, asi que no lleva updated_by.
ALTER TABLE files ADD CONSTRAINT files_created_by_same_household
    FOREIGN KEY (household_id, created_by) REFERENCES household_members (household_id, id);

-- Dato maestro: que cosas existen y como se llaman. Un articulo no es un asset
-- --no ocupa sitio, no tiene cantidad, no se presta.
CREATE TABLE articles (
    id            uuid PRIMARY KEY,
    household_id  uuid        NOT NULL REFERENCES households (id) ON DELETE CASCADE,
    category_id   uuid        NOT NULL REFERENCES categories (id),
    name          text        NOT NULL,
    unit          text        NOT NULL,
    brand         text,
    model         text,
    barcode       text,
    pack_size     numeric,
    photo_url     text,
    photo_file_id uuid,
    notes         text,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    retired_at    timestamptz,
    created_by    uuid,
    updated_by    uuid,
    CONSTRAINT articles_unit_valid
        CHECK (unit IN ('UNIT', 'GRAM', 'KILOGRAM', 'MILLILITER', 'LITER', 'METER', 'PACK')),
    CONSTRAINT articles_pack_size_positive
        CHECK (pack_size IS NULL OR pack_size > 0),
    -- Enlace o fichero, o ninguno de los dos: un articulo sin foto es lo
    -- habitual. De ahi el OR y no el <> de documents.
    CONSTRAINT articles_photo_single_source
        CHECK (photo_url IS NULL OR photo_file_id IS NULL)
);

CREATE UNIQUE INDEX articles_name_unique_live
    ON articles (household_id, lower(immutable_unaccent(name)))
    WHERE retired_at IS NULL;

CREATE UNIQUE INDEX articles_barcode_unique_live
    ON articles (household_id, barcode)
    WHERE barcode IS NOT NULL AND retired_at IS NULL;

-- Un fichero no se adjunta dos veces.
CREATE UNIQUE INDEX articles_photo_file_unique
    ON articles (photo_file_id)
    WHERE photo_file_id IS NOT NULL;

ALTER TABLE articles ADD CONSTRAINT articles_photo_same_household
    FOREIGN KEY (household_id, photo_file_id) REFERENCES files (household_id, id);
ALTER TABLE articles ADD CONSTRAINT articles_created_by_same_household
    FOREIGN KEY (household_id, created_by) REFERENCES household_members (household_id, id);
ALTER TABLE articles ADD CONSTRAINT articles_updated_by_same_household
    FOREIGN KEY (household_id, updated_by) REFERENCES household_members (household_id, id);

-- ---------------------------------------------------------------------------
-- Ubicaciones y assets
-- ---------------------------------------------------------------------------

CREATE TABLE locations (
    id                       uuid PRIMARY KEY,
    household_id             uuid        NOT NULL REFERENCES households (id) ON DELETE CASCADE,
    name                     text        NOT NULL,
    type                     text        NOT NULL,
    -- La validacion anti-ciclo no es expresable como CHECK: va en el caso de uso.
    parent_location_id       uuid REFERENCES locations (id),
    capacity                 jsonb,
    environmental_conditions jsonb,
    photo_url                text,
    photo_file_id            uuid,
    notes                    text,
    created_at               timestamptz NOT NULL DEFAULT now(),
    updated_at               timestamptz NOT NULL DEFAULT now(),
    created_by               uuid,
    updated_by               uuid,
    CONSTRAINT locations_type_valid
        CHECK (type IN ('HOUSE', 'FLOOR', 'ROOM', 'FURNITURE', 'SHELF', 'OTHER')),
    CONSTRAINT locations_photo_single_source
        CHECK (photo_url IS NULL OR photo_file_id IS NULL)
);

-- Unico entre hermanas, no en todo el hogar: dos armarios pueden llamarse igual
-- en cuartos distintos. El NULLS NOT DISTINCT cubre las raices, que no tienen
-- padre; sin el, dos ubicaciones raiz podrian llamarse igual.
CREATE UNIQUE INDEX locations_name_unique_among_siblings
    ON locations (household_id, parent_location_id, lower(immutable_unaccent(name)))
    NULLS NOT DISTINCT;

CREATE UNIQUE INDEX locations_photo_file_unique
    ON locations (photo_file_id)
    WHERE photo_file_id IS NOT NULL;

ALTER TABLE locations ADD CONSTRAINT locations_photo_same_household
    FOREIGN KEY (household_id, photo_file_id) REFERENCES files (household_id, id);
ALTER TABLE locations ADD CONSTRAINT locations_created_by_same_household
    FOREIGN KEY (household_id, created_by) REFERENCES household_members (household_id, id);
ALTER TABLE locations ADD CONSTRAINT locations_updated_by_same_household
    FOREIGN KEY (household_id, updated_by) REFERENCES household_members (household_id, id);

-- Las dos naturalezas en una tabla: un DURABLE es una unidad fisica con
-- identidad propia; un CONSUMABLE es una existencia --un articulo en una
-- ubicacion, con cantidad.
CREATE TABLE assets (
    id                uuid PRIMARY KEY,
    household_id      uuid        NOT NULL REFERENCES households (id) ON DELETE CASCADE,
    article_id        uuid REFERENCES articles (id),
    category_id       uuid REFERENCES categories (id),
    name              text,
    type              text        NOT NULL,
    -- Anulable: lo deja vacio la baja de su propietario.
    owner_id          uuid,
    location_asset_id uuid REFERENCES assets (id),
    location_id       uuid REFERENCES locations (id),
    quantity          numeric,
    status            text        NOT NULL,
    serial_number     text,
    acquired_on       date,
    photo_url         text,
    photo_file_id     uuid,
    notes             text,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    created_by        uuid,
    updated_by        uuid,
    -- Nunca las dos ubicaciones a la vez.
    CONSTRAINT assets_location_single_source
        CHECK (location_asset_id IS NULL OR location_id IS NULL),
    CONSTRAINT assets_type_valid
        CHECK (type IN ('DURABLE', 'CONSUMABLE')),
    CONSTRAINT assets_status_valid
        CHECK (status IN ('AVAILABLE', 'LENT', 'DECOMMISSIONED')),
    -- Coherencia de cantidad y articulo: el consumible los exige, el duradero no
    -- admite cantidad.
    CONSTRAINT assets_quantity_matches_type
        CHECK ((type = 'CONSUMABLE' AND article_id IS NOT NULL AND quantity IS NOT NULL AND quantity >= 0)
            OR (type = 'DURABLE' AND quantity IS NULL)),
    -- Todo asset tiene nombre y categoria efectivos: propios, o heredados del
    -- articulo.
    CONSTRAINT assets_effective_name_and_category
        CHECK (article_id IS NOT NULL OR (name IS NOT NULL AND category_id IS NOT NULL)),
    -- Ceder un consumible es un ajuste de cantidad, no un prestamo.
    CONSTRAINT assets_only_durable_is_lent
        CHECK (type = 'DURABLE' OR status <> 'LENT'),
    CONSTRAINT assets_durable_only_attributes
        CHECK (type = 'DURABLE' OR (serial_number IS NULL AND acquired_on IS NULL)),
    CONSTRAINT assets_photo_single_source
        CHECK (photo_url IS NULL OR photo_file_id IS NULL)
);

-- Una sola existencia VIVA por articulo y ubicacion.
--
-- Los dos matices del indice son igual de necesarios. El NULLS NOT DISTINCT hace
-- que la regla siga aplicando cuando la existencia aun no tiene ubicacion
-- asignada; sin el, cada entrada sin ubicar crearia una fila nueva. Y la
-- exclusion de DECOMMISSIONED evita que una existencia dada de baja o fusionada
-- ocupe su hueco para siempre, impidiendo que ningun RegisterConsumableIntake
-- posterior vuelva a usar esa ubicacion.
CREATE UNIQUE INDEX assets_live_stock_item_unique
    ON assets (household_id, article_id, location_asset_id, location_id)
    NULLS NOT DISTINCT
    WHERE type = 'CONSUMABLE' AND status <> 'DECOMMISSIONED';

CREATE UNIQUE INDEX assets_photo_file_unique
    ON assets (photo_file_id)
    WHERE photo_file_id IS NOT NULL;

ALTER TABLE assets ADD CONSTRAINT assets_photo_same_household
    FOREIGN KEY (household_id, photo_file_id) REFERENCES files (household_id, id);
ALTER TABLE assets ADD CONSTRAINT assets_owner_same_household
    FOREIGN KEY (household_id, owner_id) REFERENCES household_members (household_id, id);
ALTER TABLE assets ADD CONSTRAINT assets_created_by_same_household
    FOREIGN KEY (household_id, created_by) REFERENCES household_members (household_id, id);
ALTER TABLE assets ADD CONSTRAINT assets_updated_by_same_household
    FOREIGN KEY (household_id, updated_by) REFERENCES household_members (household_id, id);

-- ---------------------------------------------------------------------------
-- Documentacion y prestamos
-- ---------------------------------------------------------------------------

CREATE TABLE documents (
    id           uuid PRIMARY KEY,
    household_id uuid        NOT NULL REFERENCES households (id) ON DELETE CASCADE,
    asset_id     uuid REFERENCES assets (id),
    article_id   uuid REFERENCES articles (id),
    file_id      uuid,
    type         text        NOT NULL,
    url          text,
    description  text,
    date         date,
    valid_until  date,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    created_by   uuid,
    updated_by   uuid,
    -- Cuelga de exactamente uno de los dos.
    CONSTRAINT documents_single_target
        CHECK ((asset_id IS NULL) <> (article_id IS NULL)),
    CONSTRAINT documents_type_valid
        CHECK (type IN ('INVOICE', 'WARRANTY', 'MANUAL', 'OTHER')),
    -- El contenido, tambien en exactamente uno: un documento sin contenido no es
    -- nada. De ahi el <> y no el OR de las fotos.
    CONSTRAINT documents_single_content
        CHECK ((url IS NULL) <> (file_id IS NULL)),
    -- Una garantia no puede caducar antes de emitirse.
    CONSTRAINT documents_valid_until_after_date
        CHECK (valid_until IS NULL OR date IS NULL OR valid_until >= date)
);

CREATE UNIQUE INDEX documents_file_unique
    ON documents (file_id)
    WHERE file_id IS NOT NULL;

ALTER TABLE documents ADD CONSTRAINT documents_file_same_household
    FOREIGN KEY (household_id, file_id) REFERENCES files (household_id, id);
ALTER TABLE documents ADD CONSTRAINT documents_created_by_same_household
    FOREIGN KEY (household_id, created_by) REFERENCES household_members (household_id, id);
ALTER TABLE documents ADD CONSTRAINT documents_updated_by_same_household
    FOREIGN KEY (household_id, updated_by) REFERENCES household_members (household_id, id);

CREATE TABLE loans (
    id                 uuid PRIMARY KEY,
    household_id       uuid        NOT NULL REFERENCES households (id) ON DELETE CASCADE,
    asset_id           uuid        NOT NULL REFERENCES assets (id),
    lender_member_id   uuid,
    lender_external    jsonb,
    borrower_member_id uuid,
    borrower_external  jsonb,
    status             text        NOT NULL,
    notes              text,
    started_at         timestamptz NOT NULL DEFAULT now(),
    due_at             timestamptz,
    returned_at        timestamptz,
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    created_by         uuid,
    updated_by         uuid,
    -- Exactamente uno de los dos, en cada extremo del prestamo.
    CONSTRAINT loans_single_lender
        CHECK ((lender_member_id IS NULL) <> (lender_external IS NULL)),
    CONSTRAINT loans_single_borrower
        CHECK ((borrower_member_id IS NULL) <> (borrower_external IS NULL)),
    CONSTRAINT loans_status_valid
        CHECK (status IN ('ACTIVE', 'RETURNED', 'OVERDUE')),
    -- El externo necesita nombre y al menos un canal, que es lo que hace falta
    -- para mandarle el enlace del token acotado.
    CONSTRAINT loans_lender_external_contactable
        CHECK (lender_external IS NULL
            OR (lender_external ? 'name'
                AND (lender_external ? 'email' OR lender_external ? 'phone'))),
    CONSTRAINT loans_borrower_external_contactable
        CHECK (borrower_external IS NULL
            OR (borrower_external ? 'name'
                AND (borrower_external ? 'email' OR borrower_external ? 'phone')))
);

-- No mas de un prestamo abierto por asset. Un prestamo vencido sigue ocupando el
-- asset, asi que el indice no puede mirar solo a ACTIVE.
CREATE UNIQUE INDEX loans_one_open_per_asset
    ON loans (asset_id)
    WHERE status IN ('ACTIVE', 'OVERDUE');

ALTER TABLE loans ADD CONSTRAINT loans_lender_same_household
    FOREIGN KEY (household_id, lender_member_id) REFERENCES household_members (household_id, id);
ALTER TABLE loans ADD CONSTRAINT loans_borrower_same_household
    FOREIGN KEY (household_id, borrower_member_id) REFERENCES household_members (household_id, id);
ALTER TABLE loans ADD CONSTRAINT loans_created_by_same_household
    FOREIGN KEY (household_id, created_by) REFERENCES household_members (household_id, id);
ALTER TABLE loans ADD CONSTRAINT loans_updated_by_same_household
    FOREIGN KEY (household_id, updated_by) REFERENCES household_members (household_id, id);

-- Mismo patron que los tokens de correo, y por el mismo motivo: el token viaja
-- por un canal ajeno y hay que poder comprobar reutilizacion.
CREATE TABLE loan_access_tokens (
    id         uuid PRIMARY KEY,
    loan_id    uuid        NOT NULL REFERENCES loans (id) ON DELETE CASCADE,
    token_hash text        NOT NULL UNIQUE,
    role       text        NOT NULL,
    expires_at timestamptz NOT NULL,
    used_at    timestamptz,
    CONSTRAINT loan_access_tokens_role_valid
        CHECK (role IN ('LENDER', 'BORROWER'))
);

CREATE INDEX loan_access_tokens_by_loan ON loan_access_tokens (loan_id);
