-- El estado de conservacion de un duradero, y la condicion en la que un
-- prestamo sale y vuelve (cierre de huecos, Hito 3).
--
-- Son **dos de los cuatro atributos** que 4.1.7 dejo propuestos y sin decidir el
-- 2026-08-09 con el criterio «no entran hasta que haya un caso de uso que lo
-- pida». Ese criterio se retiro al planificar el cierre de huecos, y con su
-- motivo escrito: el destinatario del primero --CMMS-- llego y **no lo quiso**,
-- y el del segundo esta al final de la cola de la Fase 3, de modo que esperarlo
-- equivalia a decidir que no existiera. Los dos son **del core**, por la misma
-- regla que subio el peso y el volumen al articulo en la V11: una regla del core
-- no puede depender de un modulo que se puede apagar.
--
-- No hay tabla nueva. Son tres columnas anulables sobre dos tablas que ya
-- existen, y **nulo no es un hueco**: significa que nadie lo anoto, que es el
-- caso normal de un inventario domestico y el unico valor honesto cuando la
-- pregunta no se hizo.

-- ---------------------------------------------------------------------------
-- Una sola escala, y para los dos sitios
-- ---------------------------------------------------------------------------
--
-- Los cinco valores son los mismos en `assets` y en `loans`, y esa es la
-- decision: el motivo entero del atributo es poder decir «salio bien y volvio
-- rayado», y dos escalas distintas no se pueden comparar. Van del mejor al peor
-- porque la escala tiene orden --el desplegable lo ofrece asi y una consulta
-- puede aprovecharlo-- aunque nada dependa hoy de el.
--
-- No es un tipo `enum` de PostgreSQL sino un `CHECK` sobre `text`, como `type`,
-- `status` y `unit`: es como esta escrito todo el modelo, y ampliar la lista
-- cuesta lo mismo en las dos formas --una migracion-- mientras que un tipo
-- propio ademas no se puede reducir.

ALTER TABLE assets ADD COLUMN condition text;

ALTER TABLE assets ADD CONSTRAINT assets_condition_valid
    CHECK (condition IS NULL OR condition IN ('NEW', 'GOOD', 'WORN', 'DAMAGED', 'UNUSABLE'));

-- El estado de conservacion describe **una unidad fisica**, asi que solo vale
-- sobre un duradero: exactamente igual que el numero de serie y la fecha de
-- adquisicion, y por el mismo motivo. Trescientos gramos de harina no estan
-- «desgastados», y lo que le pasa a un lote --que caduque, que se estropee-- es
-- del modulo Warehouse y se sigue en su tabla.
--
-- Se amplia la restriccion que ya existia en vez de anadir una segunda: la regla
-- es una sola --«lo que describe una unidad fisica no cabe en una existencia»--
-- y partirla en dos dejaria dos sitios donde acordarse de la siguiente.
ALTER TABLE assets DROP CONSTRAINT assets_durable_only_attributes;
ALTER TABLE assets ADD CONSTRAINT assets_durable_only_attributes
    CHECK (type = 'DURABLE'
        OR (serial_number IS NULL AND acquired_on IS NULL AND condition IS NULL));

COMMENT ON COLUMN assets.condition IS
    'Estado de conservacion de un DURABLE. Nulo = nadie lo anoto. Ver 4.1.1.';

-- ---------------------------------------------------------------------------
-- Los dos momentos del prestamo
-- ---------------------------------------------------------------------------
--
-- No se guardan en el asset sino en el prestamo, y son dos columnas y no una
-- porque **la pareja es el dato**: una condicion de devolucion sin la de entrega
-- no dice si la cosa volvio peor, que es justo lo que se queria poder decir.

ALTER TABLE loans
    ADD COLUMN condition_at_start  text,
    ADD COLUMN condition_on_return text;

ALTER TABLE loans ADD CONSTRAINT loans_condition_at_start_valid
    CHECK (condition_at_start IS NULL
        OR condition_at_start IN ('NEW', 'GOOD', 'WORN', 'DAMAGED', 'UNUSABLE'));
ALTER TABLE loans ADD CONSTRAINT loans_condition_on_return_valid
    CHECK (condition_on_return IS NULL
        OR condition_on_return IN ('NEW', 'GOOD', 'WORN', 'DAMAGED', 'UNUSABLE'));

-- **La condicion de devolucion solo existe si hubo devolucion.** Se anota al
-- confirmarla, que es cuando se sabe, y no hay ninguna otra operacion que la
-- escriba: un prestamo abierto con condicion de vuelta seria una afirmacion
-- sobre algo que todavia esta fuera de casa. `returned_at` y no `status`, porque
-- es el mismo instante y es la columna que la operacion escribe.
ALTER TABLE loans ADD CONSTRAINT loans_condition_on_return_needs_return
    CHECK (condition_on_return IS NULL OR returned_at IS NOT NULL);

COMMENT ON COLUMN loans.condition_at_start IS
    'En que estado salio de casa. Nulo = nadie lo anoto. Ver 4.1.5.';
COMMENT ON COLUMN loans.condition_on_return IS
    'En que estado volvio, anotado al confirmar la devolucion. Ver 4.1.5.';
