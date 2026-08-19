-- El modulo Mantenimiento (CMMS) (Fase 2, Hito 5).
--
-- Su ficha esta en `docs/backend/modules/maintenance.md` y se escribio antes que
-- esto. Las dos fronteras que declara se cumplen aqui, y la primera es la unica
-- que ningun modulo anterior tuvo que escribir sin el otro lado delante:
--
--   **De CMMS es el CUANDO; del planificador de tareas, el QUIEN LO HACE.** No hay
--   en estas tres tablas ni una columna que apunte a un miembro del hogar como
--   RESPONSABLE de que algo se haga --la autoria apunta a quien escribio la fila,
--   que es otra cosa-- y no hay ninguna tabla con una fila por ocurrencia futura:
--   lo que hay es UNA fecha por plan, `next_due_on`. Materializar el calendario
--   seria construir la mitad del planificador, porque en cuanto existan filas por
--   dia alguien las querra asignar.
--
--   **Y contra el core: este modulo solo lee.** No invoca ningun caso de uso suyo
--   ni escribe en ninguna de sus tablas. Que la caldera se haya revisado es un
--   hecho sobre la caldera que el core no necesita saber, y meterlo alli le daria
--   un concepto que dejaria de tener sentido el dia que el hogar apague esto.
--
-- **En `public` y no en un esquema propio**, por lo mismo que la V9, la V10 y la
-- V12: el esquema aparte es la trampa del modulo de prueba del Hito 0, que lo usa
-- justo para NO falsear el recuento de tablas del modelo. Estas tres lo suben de
-- veinticinco a veintiocho y **no tocan** la lista de tablas sin politica, porque
-- las tres llevan household_id, RLS y FORCE.
--
-- Los nombres estan declarados en su ficha, que es lo que impide que otro modulo
-- los tome ahora que todos comparten esquema.

-- ---------------------------------------------------------------------------
-- La ficha de una maquina
-- ---------------------------------------------------------------------------
-- La entrada de un `DURABLE` en el radar del modulo. **No la crea nadie a mano**:
-- la abren la siembra y dos de los tres handlers, compartiendo funcion.
--
-- Y es la respuesta a la pregunta que la definicion no contestaba. El catalogo de
-- eventos (README 5.2.3) decia que CMMS «genera un plan de mantenimiento por
-- defecto» al darse de alta un asset, y eso no se sostiene: **por defecto ¿de
-- que?** Una caldera pide revision anual y una silla no pide nada, y el core no
-- modela de que clase es cada maquina --su `Category` es un catalogo por hogar con
-- nombres que cada casa edita--. Un plan por cada `DURABLE` inunda el hogar el dia
-- que enciende el modulo; ninguno deja la siembra vacia y al handler sin trabajo.
--
-- Asi que lo que se abre es ESTO, que es una por maquina, y el plan lo pone quien
-- sabe si su caldera es de gas. Es la misma forma que Warehouse eligio al abrir
-- fichas de articulo **sin minimo**.
CREATE TABLE maintenance_items (
    id                  uuid PRIMARY KEY,
    household_id        uuid        NOT NULL REFERENCES households (id) ON DELETE CASCADE,
    asset_id            uuid        NOT NULL,
    -- **Cual de los documentos de esa maquina es el manual**, que es una decision
    -- del modulo y no del core: el core sabe que un documento es de tipo MANUAL,
    -- no cual de los tres manuales adjuntos es el que hay que tener a mano al
    -- revisarla. Es un PUNTERO y no una copia: el documento sigue siendo del core,
    -- con su fichero y su descarga.
    manual_document_id  uuid,
    notes               text,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_by          uuid,
    -- **Una ficha por maquina**, y este indice es la idempotencia de la siembra y
    -- de dos de los tres handlers: `ON CONFLICT DO NOTHING` detras, sin comprobar
    -- antes. Comprobar y despues insertar deja una ventana entre las dos cosas por
    -- la que caben dos entregas simultaneas del mismo evento, que es la leccion
    -- que dejaron escrita los Hitos 3 y 4.
    CONSTRAINT maintenance_items_one_per_asset UNIQUE (household_id, asset_id),
    CONSTRAINT maintenance_items_household_scoped_id UNIQUE (household_id, id)
);

-- ---------------------------------------------------------------------------
-- El plan: una regla recurrente sobre una maquina
-- ---------------------------------------------------------------------------
-- Un plan NO es una tarea, y la diferencia no es de tamano sino de naturaleza. Un
-- plan es una regla --«la caldera se revisa cada doce meses»--: no tiene
-- responsable, no tiene dia, no se completa y no desaparece cuando alguien la
-- atiende. Una tarea es un encargo --«Kike revisa la caldera el jueves»--: tiene
-- responsable, tiene dia y se completa una vez.
CREATE TABLE maintenance_plans (
    id               uuid PRIMARY KEY,
    household_id     uuid        NOT NULL REFERENCES households (id) ON DELETE CASCADE,
    asset_id         uuid        NOT NULL,
    name             text        NOT NULL,
    -- **En meses y no en dias**, y no es comodidad: `plusMonths` conserva el dia
    -- del mes. Una revision «anual» de 365 dias se desplaza un dia cada ano
    -- bisiesto, y a los veinte anos la caldera se revisa en una fecha que nadie
    -- eligio. El techo son diez anos, que es mas de lo que dura una maquina.
    interval_months  integer     NOT NULL,
    -- Con cuanta antelacion avisar. Cero es legitimo --«avisame el dia que toque»--
    -- y lo que no cabe es una antelacion mas larga que el propio periodo, que
    -- dejaria el plan permanentemente «a punto de tocar»: eso lo corta el caso de
    -- uso, porque compara dos columnas y el CHECK aqui solo acota el rango.
    lead_days        integer     NOT NULL DEFAULT 15,
    -- **Cuando toca la proxima.** Se guarda y no se calcula al leer, porque de ella
    -- cuelga el estado del aviso: un valor derivado al vuelo no tiene un momento en
    -- el que cambiar, y sin ese momento no hay donde rearmar nada.
    next_due_on      date        NOT NULL,
    last_performed_on date,
    -- **El servicio tecnico, sin clave ajena hacia `suppliers`**, por lo mismo que
    -- la V12 con el proveedor de una compra: una clave ajena hacia la tabla de otro
    -- modulo es una dependencia de esquema que ArchUnit no puede ver, y un JOIN
    -- desde aqui incumpliria la frontera igual que un import, solo que sin nada que
    -- lo delate.
    --
    -- **Y aqui, al reves que en una compra, SIN el nombre dentro.** Un plan es una
    -- regla viva: si el fontanero cambia de nombre, el plan tiene que decir el
    -- nombre de HOY, porque a ese senor es a quien hay que llamar el mes que viene.
    -- Copiarlo daria una segunda version que envejece. Se resuelve al leer, por el
    -- puerto de plataforma, que ademas es lo que obliga a que un contacto RETIRADO
    -- siga siendo legible --la garantia que Proveedores declaro por adelantado y
    -- para este caso exacto.
    supplier_id      uuid,
    notes            text,
    -- **El estado del aviso, y cuelga de LA FECHA y no del plan.** `notified_for`
    -- es la fecha a la que `notified_stage` se refiere, y un aviso solo se calla
    -- cuando coincide con `next_due_on`.
    --
    -- La alternativa era una sola marca --«ya avise»-- que hubiera que borrar al
    -- registrar la intervencion. Se descarta porque **hay mas de un camino que
    -- mueve la fecha**: ademas de la intervencion, cambiar el intervalo y corregir
    -- la proxima fecha con el PATCH. Con una marca suelta, cada camino tiene que
    -- acordarse de limpiarla, y el que se olvide deja un plan que **no vuelve a
    -- avisar nunca** --el peor sintoma posible, porque solo se descubre el dia que
    -- la caldera lleva dos anos sin revisar.
    notified_stage   text,
    notified_for     date,
    -- Baja logica, igual que la retirada de un `Supplier`: el plan deja de
    -- vigilarse y conserva su historico.
    cancelled_at     timestamptz,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    created_by       uuid,
    updated_by       uuid,
    CONSTRAINT maintenance_plans_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT maintenance_plans_interval_sane CHECK (interval_months BETWEEN 1 AND 120),
    CONSTRAINT maintenance_plans_lead_sane CHECK (lead_days BETWEEN 0 AND 365),
    CONSTRAINT maintenance_plans_stage_valid
        CHECK (notified_stage IS NULL OR notified_stage IN ('DUE_SOON', 'OVERDUE')),
    -- O las dos o ninguna: una fase sin fecha no se puede comparar con nada, y una
    -- fecha sin fase no dice que se aviso.
    CONSTRAINT maintenance_plans_notification_pair
        CHECK ((notified_stage IS NULL) = (notified_for IS NULL)),
    CONSTRAINT maintenance_plans_household_scoped_id UNIQUE (household_id, id)
);

-- **Ningun nombre repetido en la misma maquina**, sin distinguir mayusculas ni
-- acentos --misma comparacion que las categorias, los articulos y los contactos de
-- servicio--. Dos revisiones anuales de la misma caldera son un duplicado que se ve
-- a la primera.
--
-- Y **parcial, solo entre los vivos**, por lo mismo que el indice de existencias
-- del core excluye las dadas de baja: un plan cancelado no puede bloquear para
-- siempre volver a crear el mismo. Es ademas lo que hace que la salida de la
-- decision abierta --«¿se puede reactivar un plan cancelado?»-- funcione sin
-- chocar: volver a crearlo.
CREATE UNIQUE INDEX maintenance_plans_one_live_name_per_asset
    ON maintenance_plans (household_id, asset_id, lower(immutable_unaccent(name)))
    WHERE cancelled_at IS NULL;

-- Lo que la comprobacion nocturna pregunta cada noche: que planes vivos tienen la
-- fecha encima.
CREATE INDEX maintenance_plans_by_due_date
    ON maintenance_plans (household_id, next_due_on)
    WHERE cancelled_at IS NULL;

-- ---------------------------------------------------------------------------
-- El historico: lo que se hizo, y cuando
-- ---------------------------------------------------------------------------
-- **Es un libro y no un agregado**: se anade y no se toca. Por eso no lleva
-- `updated_at` ni `updated_by`, igual que el cuaderno de Warehouse.
CREATE TABLE maintenance_interventions (
    id            uuid PRIMARY KEY,
    household_id  uuid        NOT NULL REFERENCES households (id) ON DELETE CASCADE,
    asset_id      uuid        NOT NULL,
    -- Nulo en una correctiva, que no cumple ningun plan.
    plan_id       uuid,
    kind          text        NOT NULL,
    performed_on  date        NOT NULL,
    summary       text        NOT NULL,
    -- **Aqui SI va el nombre dentro**, al reves que en el plan: una intervencion es
    -- HISTORIA. Que el 3 de marzo vino aquel servicio tecnico siguio siendo cierto
    -- aunque despues se retire o el hogar apague Proveedores. Es el argumento de la
    -- V8 para el texto de un aviso, el de la V10 para el nombre de un sitio y el de
    -- la V12 para el proveedor de una compra.
    supplier_id   uuid,
    supplier_name text,
    notes         text,
    created_at    timestamptz NOT NULL DEFAULT now(),
    created_by    uuid,
    CONSTRAINT maintenance_interventions_kind_valid
        CHECK (kind IN ('PREVENTIVE', 'CORRECTIVE')),
    CONSTRAINT maintenance_interventions_summary_not_blank CHECK (btrim(summary) <> ''),
    -- Una correctiva nunca cuelga de un plan; una preventiva puede colgar o no
    -- --cambiar el filtro sin tener plan escrito es un caso normal.
    CONSTRAINT maintenance_interventions_plan_only_preventive
        CHECK (plan_id IS NULL OR kind = 'PREVENTIVE'),
    CONSTRAINT maintenance_interventions_supplier_pair
        CHECK ((supplier_id IS NULL) = (supplier_name IS NULL)),
    CONSTRAINT maintenance_interventions_household_scoped_id UNIQUE (household_id, id)
);

CREATE INDEX maintenance_interventions_by_asset
    ON maintenance_interventions (household_id, asset_id, performed_on DESC);
CREATE INDEX maintenance_interventions_by_plan
    ON maintenance_interventions (household_id, plan_id, performed_on DESC)
    WHERE plan_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- Claves ajenas hacia el core: la regla y sus dos excepciones
-- ---------------------------------------------------------------------------
-- **La regla es ON DELETE CASCADE**, que es lo que decidio el Hito 2 y por el mismo
-- motivo: con el RESTRICT que rige por omision, una fila de un modulo convertiria
-- una operacion del core en una violacion de restriccion, o sea en un 500 **del
-- core causado por un modulo**.
--
-- El `asset_id` la lleva en las tres tablas, incluida la de historia: **un asset no
-- se borra nunca --se da de baja--**, asi que la cascada solo se dispara al borrar
-- el hogar entero. Es lo mismo que decidio la V10 para el `asset_id` del cuaderno.
--
-- **Excepcion 1: el servicio tecnico va sin clave ajena** --ya esta dicho arriba,
-- en las dos tablas que lo nombran.
--
-- **Excepcion 2: el manual se suelta, no arrastra.** Es la unica clave ajena del
-- modelo con ON DELETE SET NULL, y merece su parrafo. Un `Document` **si se borra
-- de verdad** --el core tiene DELETE /documents/{id}--, asi que RESTRICT
-- convertiria ese borrado en un 500 y **CASCADE borraria la ficha entera de la
-- maquina** por haber borrado un adjunto, y con ella su nota. Lo que se cae es el
-- puntero: el manual desaparece y la caldera sigue teniendo su ficha, sus planes y
-- su historico.
--
-- Y va simple y no compuesta porque **`documents` es la unica tabla del core a la
-- que este modulo apunta que no declara UNIQUE (household_id, id)**. Anadirselo
-- seria un cambio del core desde la migracion de un modulo, que es justo lo que la
-- V11 evito haciendose aparte. La garantia de que el documento es del hogar la dan
-- las dos capas de siempre: la politica de RLS al leerlo y el caso de uso al
-- comprobarlo.
ALTER TABLE maintenance_items ADD CONSTRAINT maintenance_items_asset_same_household
    FOREIGN KEY (household_id, asset_id) REFERENCES assets (household_id, id) ON DELETE CASCADE;
ALTER TABLE maintenance_items ADD CONSTRAINT maintenance_items_manual_document
    FOREIGN KEY (manual_document_id) REFERENCES documents (id) ON DELETE SET NULL;
ALTER TABLE maintenance_items ADD CONSTRAINT maintenance_items_created_by_same_household
    FOREIGN KEY (household_id, created_by) REFERENCES household_members (household_id, id);
ALTER TABLE maintenance_items ADD CONSTRAINT maintenance_items_updated_by_same_household
    FOREIGN KEY (household_id, updated_by) REFERENCES household_members (household_id, id);

ALTER TABLE maintenance_plans ADD CONSTRAINT maintenance_plans_asset_same_household
    FOREIGN KEY (household_id, asset_id) REFERENCES assets (household_id, id) ON DELETE CASCADE;
ALTER TABLE maintenance_plans ADD CONSTRAINT maintenance_plans_created_by_same_household
    FOREIGN KEY (household_id, created_by) REFERENCES household_members (household_id, id);
ALTER TABLE maintenance_plans ADD CONSTRAINT maintenance_plans_updated_by_same_household
    FOREIGN KEY (household_id, updated_by) REFERENCES household_members (household_id, id);

ALTER TABLE maintenance_interventions ADD CONSTRAINT maintenance_interventions_asset_same_household
    FOREIGN KEY (household_id, asset_id) REFERENCES assets (household_id, id) ON DELETE CASCADE;
ALTER TABLE maintenance_interventions ADD CONSTRAINT maintenance_interventions_plan_same_household
    FOREIGN KEY (household_id, plan_id) REFERENCES maintenance_plans (household_id, id) ON DELETE CASCADE;
ALTER TABLE maintenance_interventions ADD CONSTRAINT maintenance_interventions_created_by_same_household
    FOREIGN KEY (household_id, created_by) REFERENCES household_members (household_id, id);

-- ---------------------------------------------------------------------------
-- Aislamiento: las tres con RLS y FORCE
-- ---------------------------------------------------------------------------
-- Como en Warehouse y en Compras, la politica hace aqui mas trabajo que en una
-- tabla del core, porque parte de lo que se escribe **no nace de una peticion**:
-- las fichas las abren los handlers de evento y la siembra, y el estado del aviso
-- lo escribe el recorrido nocturno.
ALTER TABLE maintenance_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE maintenance_items FORCE ROW LEVEL SECURITY;
CREATE POLICY maintenance_items_household_isolation ON maintenance_items
    USING (household_id = nullif(current_setting('app.household_id', true), '')::uuid);

ALTER TABLE maintenance_plans ENABLE ROW LEVEL SECURITY;
ALTER TABLE maintenance_plans FORCE ROW LEVEL SECURITY;
CREATE POLICY maintenance_plans_household_isolation ON maintenance_plans
    USING (household_id = nullif(current_setting('app.household_id', true), '')::uuid);

ALTER TABLE maintenance_interventions ENABLE ROW LEVEL SECURITY;
ALTER TABLE maintenance_interventions FORCE ROW LEVEL SECURITY;
CREATE POLICY maintenance_interventions_household_isolation ON maintenance_interventions
    USING (household_id = nullif(current_setting('app.household_id', true), '')::uuid);
