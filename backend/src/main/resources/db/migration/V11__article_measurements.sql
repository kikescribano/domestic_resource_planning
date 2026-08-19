-- Cuanto pesa y cuanto ocupa una unidad de un articulo (Fase 2, Hito 3).
--
-- **Esto es un cambio del core, no del modulo Warehouse**, y va en su propia
-- migracion justamente por eso: colarlo en la V10 lo habria dejado revisado como
-- si fuera de un modulo, y su reversion habria arrastrado tablas que no tienen
-- nada que ver. El numero es posterior al del modulo porque la V10 esta escrita
-- en la definicion del hito; las dos son independientes y ninguna depende de la
-- otra.
--
-- Resuelve la pregunta que la Fase 1 dejo abierta en 4.1.7 con destinatario
-- asignado: el aviso de capacidad de una ubicacion (4.1.2) **solo podia contar
-- unidades**, porque nada en el modelo decia cuanto ocupa una cosa, asi que una
-- ubicacion que declara «50 kg» no recibia ningun aviso nunca. La pregunta llego
-- dirigida a Warehouse, y al escribir su ficha resulto no ser suya:
--
--   * El aviso de capacidad es **una regla del core**, y una regla del core no
--     puede depender de un modulo que se puede apagar. Con el dato en una tabla
--     de Warehouse, el mismo hogar recibiria un aviso distinto segun tuviera el
--     modulo encendido, y la Fase 2 promete lo contrario: el core se comporta
--     igual con los cuatro modulos apagados.
--   * Peso y volumen son propiedades **del material**, y el core define un asset
--     como todo el material del hogar. Lo de Warehouse son los consumos, los
--     minimos, la caducidad y los lotes: cosas que se **siguen**, no cosas que se
--     **son**.
--
-- **Va en `articles` y no en `assets`**, que es la otra mitad de la decision. En
-- una existencia de consumible el peso total es cantidad x peso unitario, asi que
-- guardarlo en la fila **se quedaria viejo en cada cambio de cantidad** y habria
-- que recalcularlo desde quien mueve el contador. En el articulo no envejece
-- nunca: un gramo de azucar pesa un gramo. Es ademas donde ya vive `pack_size`,
-- que es exactamente el mismo tipo de hecho --cuanto trae un envase-- y la misma
-- regla que el core aplica al nombre y a la categoria: cuando el asset tiene
-- articulo, no se guardan por duplicado.
--
-- Las unidades son **fijas y absolutas**, gramos y mililitros, y no la `unit` del
-- articulo. Es a proposito: la `unit` dice en que se cuenta --unidades, gramos,
-- metros-- y aqui hace falta algo con lo que poder **sumar entre articulos
-- distintos** dentro de una misma ubicacion. Con la unidad de cada uno, sumar
-- arroz en gramos con lejia en litros no daria ningun numero.
ALTER TABLE articles
    ADD COLUMN unit_weight_grams numeric,
    ADD COLUMN unit_volume_ml    numeric;

-- Cero no es un peso: significa «no lo se», y para eso ya esta el nulo. Admitir
-- cero dejaria que media despensa sumara nada y que el aviso dijera que cabe.
ALTER TABLE articles ADD CONSTRAINT articles_unit_weight_positive
    CHECK (unit_weight_grams IS NULL OR unit_weight_grams > 0);
ALTER TABLE articles ADD CONSTRAINT articles_unit_volume_positive
    CHECK (unit_volume_ml IS NULL OR unit_volume_ml > 0);

COMMENT ON COLUMN articles.unit_weight_grams IS
    'Lo que pesa UNA unit del articulo, en gramos. Nulo = no se sabe.';
COMMENT ON COLUMN articles.unit_volume_ml IS
    'Lo que ocupa UNA unit del articulo, en mililitros. Nulo = no se sabe.';
