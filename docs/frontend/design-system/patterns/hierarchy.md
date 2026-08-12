# Jerarquía navegable

| Campo | Valor |
|---|---|
| Estado | Borrador |
| Responsable | Equipo DRP |
| Ámbito | frontend |
| Última revisión | 2026-08-12 |

## Propósito

Fijar cómo se recorre el árbol de ubicaciones de un hogar —vivienda, planta,
habitación, mueble, estante— y cómo se elige un sitio dentro de él. Es la
superficie más nueva del Hito 2: **ninguna pantalla del Hito 1 tenía nada
jerárquico**, y la jerarquía es lo que peor cabe en 375 px.

## Alcance

### Incluido

- Las dos jerarquías del core y por qué el usuario ve una sola.
- Las dos formas de cargar el árbol, con lo que cuesta cada una.
- Cómo se recorre a 375 px, donde un árbol clásico no entra.
- Elegir un destino y mover un nodo.

### Fuera de alcance

- Las colecciones planas y paginadas, en [`listing.md`](listing.md).
- La navegación de la aplicación, que es otra cosa aunque también sea un árbol de
  sitios: [`navigation.md`](navigation.md).
- El modelo de ubicaciones y sus reglas, en
  [`core-model.md`](../../../common/product/core-model.md).

## Estado

**Previsto por completo en el frontend.** No existe ningún árbol, ningún selector
de ubicación y ninguna migaja de pan. Este documento describe una pantalla que
todavía no está escrita.

**El backend sí existe**: las seis operaciones de ubicaciones están implementadas,
con el anti-ciclo en el caso de uso. Así que lo que falta es exactamente la parte
de la que trata este documento.

## Contenido

### Son dos jerarquías, y el usuario ve una

| Jerarquía | Cómo se expresa | Quién puede ser padre |
|---|---|---|
| **Ubicaciones** | `parentLocationId`; nulo la convierte en raíz | Cualquier ubicación |
| **Composición de assets** | `location`, que es un `LocationRef` con `type: ASSET` | **Solo un `DURABLE`** |

La ubicación de un asset es polimórfica: apunta a otra `Location` **o** a un asset
`DURABLE`, nunca a las dos. Y eso significa que el camino que el usuario recorre
las atraviesa las dos sin enterarse: *Casa del Pinar → Planta baja → Cocina →
Armario alto → caja de herramientas → destornillador*. Los tres primeros saltos
son ubicaciones, los dos últimos son assets que contienen assets.

**El patrón tiene que presentarlas como un solo árbol** y, a la vez, no confundir
sus reglas: una ubicación se elimina de verdad y un asset se da de baja; una
ubicación admite cualquier hija y un asset solo puede colgar de un `DURABLE`.

Un aviso sobre el tipo, que se malinterpreta con facilidad: `LocationType`
—`HOUSE`, `FLOOR`, `ROOM`, `FURNITURE`, `SHELF`, `OTHER`— es **vocabulario, no
nivel**. Nada en el contrato impide que un `SHELF` contenga una `ROOM`, y la
profundidad no está acotada. Quien dibuje el árbol asumiendo cinco niveles fijos
o un orden de tipos va a acertar en el 95 % de los hogares y a romperse en el
resto.

### Dos formas de cargarlo

El contrato ofrece las dos, y las dos están implementadas:

| Forma | Petición | Qué cuesta |
|---|---|---|
| **De una vez** | `GET /locations`, que devuelve el hogar entero paginado | Una petición si el hogar cabe en una página; varias si pasa de 200 nodos |
| **Por niveles, bajo demanda** | `GET /locations/{id}/children`, solo las hijas directas | Una petición por nodo que se despliega |

Lo que inclina la balanza no es el número de peticiones sino **para qué hace falta
el árbol**:

- La **migaja de pan** de una ficha de asset necesita los antepasados de un nodo,
  y `Location` **no trae ningún campo de camino ni de antepasados**: solo `id` y
  `parentLocationId`. Con carga por niveles, componer «Cocina → Armario alto»
  cuesta una petición por escalón hacia arriba; con el árbol entero en memoria es
  gratis.
- El **selector de destino** de un movimiento necesita el árbol completo de todas
  formas, porque hay que poder elegir cualquier nodo y descartar los
  descendientes del que se mueve.

Así que la recomendación es **cargar el hogar entero** y quedarse la carga por
niveles para el caso en que un hogar resulte tener cientos de ubicaciones. Un
hogar doméstico son decenas de nodos, no miles; y si se descubre lo contrario, el
contrato ya ofrece la otra vía sin cambiar nada.

### A 375 px un árbol clásico no entra

Es el problema central. Con cinco niveles y una sangría legible se van entre 60 y
80 px de los 375, y lo que queda tiene que alojar un nombre de ubicación, algún
indicio de lo que hay dentro y un objetivo táctil de 44 px. La sangría se come la
pantalla justo en los nodos profundos, que son los que interesan.

La respuesta es la misma que ya toman el shell y el listado —**una sola
estructura recolocada, no dos**— con esta forma:

| Ancho | Forma | Cómo se recorre |
|---|---|---|
| Por debajo de `md` | **Un nivel cada vez** | Se entra en un nodo y la lista se sustituye por la de sus hijas; arriba, el camino recorrido; una acción de subir un nivel |
| Desde `md` | **Árbol con sangría** | Se despliega y se pliega en el sitio, con varias ramas abiertas a la vez |

Las dos vistas leen el mismo árbol en memoria, así que cambiar de una a otra al
girar el móvil no pierde el sitio.

La razón de fondo es la misma de siempre: por debajo de `md` se toca con el dedo
y los 44 px imponen holgura, así que no caben ni la sangría ni los nodos
plegados; con puntero, ver varias ramas a la vez es exactamente lo que hace útil
un árbol.

### Recorrerlo con el teclado

Está **por decidir** cuál de los dos patrones se implementa, y conviene decidirlo
antes de escribir el componente porque no se convierte uno en otro:

- **Árbol ARIA** (`role="tree"`): un solo punto de tabulación para todo el árbol y
  las flechas para moverse dentro. Es lo correcto para un árbol grande y es
  bastante trabajo.
- **Lista anidada de desplegables**: cada nodo es un `<button>` con
  `aria-expanded` dentro de `<ul>` anidados. Cada nodo entra en el orden de
  tabulación. Más simple, y en un árbol de decenas de nodos, perfectamente
  utilizable.

Lo que **no** depende de esa elección:

- `aria-expanded` en todo nodo que se pliega, siempre.
- **El estado plegado no se dice solo con el giro de un icono**: lleva
  `aria-expanded` y un objetivo pulsable con nombre.
- 44 px de alto por nodo por debajo de `md`.
- Desplegar es un cambio de 220 ms (`--duration-base`) animado con
  `grid-template-rows` o con transformación, **nunca estirando una caja**, que es
  lo que fija [`motion.md`](../foundations/motion.md).
- Con `prefers-reduced-motion` el despliegue es instantáneo y no se pierde nada.

### Elegir un destino y mover un nodo

Mover una ubicación es `PATCH /locations/{id}` con otro `parentLocationId`; con
`null` pasa a ser raíz, que es lo que representa una vivienda. Mover un asset es
`PATCH /assets/{id}` con otro `location`.

Tres reglas para el selector:

1. **No se ofrece lo que va a fallar.** Los descendientes del nodo que se mueve no
   se listan como destino: el servidor los rechaza con `LOCATION_CYCLE`, y esa
   negativa es la red de seguridad, no el camino normal.
2. **El destino se elige sobre el árbol**, no en un desplegable plano. Una lista
   de cien nombres sin camino no distingue dos armarios que se llaman igual en
   cuartos distintos —que el contrato permite a propósito, porque el nombre solo
   es único entre hermanas—.
3. **Arrastrar y soltar, si llega, nunca es el único camino.** A 375 px arrastrar
   entre niveles plegados es inviable, y con teclado no existe.

Y una diferencia que cambia el tono de la confirmación: **eliminar una ubicación
es un borrado de verdad**, no una baja lógica como la del asset o la retirada de
un artículo. Solo se permite si está vacía —si no, llegan
`LOCATION_HAS_CHILDREN` o `LOCATION_HAS_ASSETS`—, así que no se pierde nada; pero
es la única operación del core que borra una fila, y merece decirlo con esas
palabras.

### Antiusos

| Antiuso | Por qué |
|---|---|
| Dibujar el árbol con sangría fija a 375 px | A partir del tercer nivel el nombre no cabe |
| Duplicar el árbol en el DOM para tener las dos vistas | Dos recorridos para quien navega con lector de pantalla; es el mismo error que evita el `<nav>` único del shell |
| Un desplegable plano para elegir ubicación | Sin camino, dos nombres iguales son indistinguibles |
| Suponer que el tipo determina el nivel | `LocationType` es vocabulario; la profundidad es libre |
| Pedir las hijas de cada nodo al pintarlo | Es la carga por niveles hecha a la vez que la carga completa, con el coste de las dos |
| Dejar que se ofrezcan destinos que crearán un ciclo | El error existe para lo imprevisto, no para lo evitable |

## Decisiones abiertas

- **Árbol ARIA o lista de desplegables**, con lo dicho más arriba.
- **Si el nodo abierto viaja en la URL.** Compartir «dónde está esto» es una de
  las cosas que un hogar hace de verdad, y hoy no hay ruta para una ubicación.
- **Qué se muestra en cada nodo además del nombre**: el número de assets que
  contiene es lo más útil y no hay ninguna operación del contrato que lo
  devuelva sin listarlos.
- **Si el árbol muestra los assets o solo las ubicaciones.** Mezclarlos es fiel al
  modelo y multiplica los nodos; separarlos obliga a explicar por qué una caja de
  herramientas no aparece donde el usuario la ve.

## Lo que falta

- **No hay componente de árbol**, ni de nodo, ni de migaja de pan.
- **No hay selector de ubicación**, que es la pieza que reaparece en cinco
  formularios del hito.
- **No hay `Skeleton`**, así que la primera carga del árbol sería hoy un
  `Spinner` centrado, que es justo lo que la dirección visual descarta para una
  vista con forma conocida.
- **`Location` no expone su camino.** Componer la ruta legible de un nodo es
  trabajo del cliente. Si algún día pesa, es una pregunta para el contrato.

## Referencias

- [`core-model.md`](../../../common/product/core-model.md): las dos jerarquías,
  la ubicación polimórfica y el anti-ciclo.
- [`openapi.yaml`](../../../../openapi.yaml): las seis operaciones de ubicaciones
  y `LocationRef`.
- [`listing.md`](listing.md): la colección plana, que es el otro caso.
- [`form.md`](form.md): el movimiento y la fusión, que usan el selector.
- [`foundations/density.md`](../foundations/density.md) y
  [`foundations/motion.md`](../foundations/motion.md)

## Historial de cambios

| Fecha | Cambio | Autor |
|---|---|---|
| 2026-08-12 | Creación del documento. El patrón está previsto: el backend de ubicaciones existe y la interfaz no. | Equipo DRP |
