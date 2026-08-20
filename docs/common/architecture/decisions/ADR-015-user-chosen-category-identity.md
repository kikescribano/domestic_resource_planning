# ADR-015: Color e icono elegidos por el usuario, dentro de un juego certificado

- Estado: accepted
- Fecha: 2026-08-20
- Responsables: Equipo DRP
- Ámbito: common
- Sustituye: Ninguna

## Contexto

Un hogar clasifica sus cosas por categorías —«Herramientas», «Alimentación»— y
las lee en listas largas. En un móvil, distinguir de un vistazo cuál es cuál es
justo lo que un rótulo de 13 px no consigue: hace falta algo que se reconozca
antes de leerlo, que es lo que **el icono y el color** hacen.

Ese atributo lleva propuesto desde el 2026-08-09 en
[4.1.7](../../product/decisions.md) y fuera del producto desde entonces, con un
motivo escrito: **son presentación**. El motivo era bueno y ha dejado de serlo
por una razón concreta —el sistema de diseño tiene hoy tokens medidos y un script
que falla la construcción si un par baja de WCAG AA— y por otra que la Fase 2
introdujo: **un módulo no puede añadir una columna a una tabla del core**, así
que si el atributo ha de existir, la decisión es del core.

**Y con eso aparece un problema que el proyecto no tenía.** Hasta hoy, todos los
colores de la interfaz los decide el sistema y están medidos:
[`scripts/check-contrast.py`](../../../../scripts/check-contrast.py) lee los
`oklch()` reales de `frontend/src/index.css`, convierte a sRGB y comprueba **cada
par que acaba en pantalla** en los dos modos. Su lista se mantiene a mano a
propósito, porque medir todas las combinaciones posibles daría cientos de números
sin significado.

Un color que elija el usuario **no está en ningún token**. Nadie lo mide, nadie
lo puede medir, y sería lo único de la interfaz cuyo contraste **se afirma en vez
de comprobarse**. Eso choca de frente con el compromiso que la
[ADR-006](ADR-006-frontend-stack-and-design-system.md) fija —WCAG 2.2 AA como
objetivo normativo— y con la frase que
[`look-and-feel.md`](../../../frontend/product-design/look-and-feel.md) usa para
describirlo: «contraste comprobado, no afirmado».

Esta ADR **extiende la ADR-006, no la sustituye ni la reescribe**: el objetivo
normativo, el sistema de tokens y la auditoría siguen exactamente como estaban.
Lo que añade es cómo entra en ese sistema un dato que elige el usuario.

## Decisión

### 1. El color y el icono se eligen dentro de un **juego cerrado**

Seis colores y dieciséis iconos, enumerados en el contrato, en un `CHECK` de la
tabla y en el sistema de diseño. Un valor que no esté en la lista es un `400`, no
una fila con un color inventado dentro.

La lista vive **en tres sitios a la vez** y esa duplicación es deliberada, la
misma que ya tiene la lista blanca de `files.content_type`: ampliar el juego
exige tocar el enumerado, el `CHECK` —o sea, una migración— y la lista del script
de contraste. Esa fricción es lo que impide que un color nuevo exista antes de
estar medido.

| Dónde | Qué es |
|---|---|
| `CategoryColor` y `CategoryIcon` en el contrato y en el dominio | Los valores admitidos |
| `categories_color_valid` y `categories_icon_valid` en la `V17` | La misma lista, en la base de datos |
| `--color-category-*` en `index.css` | Los doce tokens que los pintan |
| `PAIRS` en `scripts/check-contrast.py` | Los doce pares que los certifican |

### 2. Los doce pares entran en `check-contrast.py`, y la auditoría pasa de 36 a 48

Dos por color, que son **las dos superficies sobre las que se pinta**:

- `category-X` sobre `category-X-soft`: el marcador, que es un cuadradito
  redondeado con el icono dentro.
- `category-X` sobre `surface-sunken`: el hueco de una foto que falta, que es
  donde el icono ocupa más pantalla en un móvil.

Se les exige **4,5:1**, el mínimo de texto, y no los 3:1 que WCAG 1.4.11 pediría
a un icono. No es prudencia gratuita: el marcador es la pieza que puede acabar
llevando el nombre de la categoría dentro, y un umbral que hay que subir después
es un umbral que nadie sube.

La comprobación de gamut del script hizo aquí más trabajo que en toda la paleta
anterior: **tres de los doce valores nuevos nacieron fuera de sRGB** —el suave del
índigo y los fuertes del cielo y el turquesa— y hubo que bajarles el croma. Un
color fuera de gamut lo recorta el navegador, y entonces el número medido deja de
ser el número que se ve.

### 3. Ninguno de los seis cae encima de los cinco estados del dominio

Los tonos tomados son 27 (error y vencido), 42 (el acento), 75 (aviso y
prestado), 152 (éxito y disponible) y 250 (información y sin existencias). Los
seis nuevos se reparten lo que queda: 350, 310, 275, 230, 195 y 130.

Los dos que más se acercan son `moss` —a 22° del verde de disponible— y `sky` —a
20° del azul de sin existencias— y **se admiten por una razón de forma, no de
tono**: el color de una categoría nunca va solo. Lleva su nombre al lado y su
icono dentro, y su recipiente es un cuadradito y no la pastilla de un estado, así
que las dos cosas no se confunden ni compartiendo fila.

### 4. **El color nunca es el único portador**, y por eso repetirlo no es un defecto

Es el criterio 1.4.1 y aquí tiene una consecuencia que conviene decir en voz
alta: **seis colores para las doce categorías de un hogar significa que el color
agrupa, no que identifica**. Lo que identifica es el nombre, que está siempre
delante.

De ahí que el marcador sea `aria-hidden` en todos sus tamaños salvo uno. La
excepción es el hueco de una foto que falta, donde no hay texto al lado: allí es
`role="img"` con su nombre accesible, «Sin foto. Categoría: Herramientas».

### 5. Nulo significa **que nadie lo eligió**

Las dos columnas son anulables y su ausencia no se rellena con un valor por
omisión guardado. Un hogar puede tener categorías sin cara, y el marcador de una
categoría sin color usa `surface-sunken` e `ink-muted` —el hueco de siempre, pero
con forma.

**Con una excepción, y es de siembra:** las cinco categorías que
`CreateHousehold` crea nacen con icono y color puestos, los cinco distintos entre
sí. El valor por omisión de un dato que pone el sistema lo pone el sistema, igual
que pone los nombres, y es lo único que hace que un hogar recién creado no vea
cinco cuadraditos grises sin entender para qué sirve el atributo.

### 6. Las clases se escriben enteras, nunca se componen

`bg-category-${color}-soft` **no lo ve Tailwind**, así que no genera ninguna regla
y el marcador sale transparente **sin que falle nada**. Es exactamente el defecto
que el Hito 3 destapó en la pantalla de Préstamos, donde `text-muted` y
`border-line` no producían ni una línea de CSS y nadie se enteró durante dos
fases.

Por eso el mapa de color a clases es un `Record` literal, y por eso el recorrido
vertical **mide el contraste ya aplicado** leyéndolo del navegador en los dos
modos: `check-contrast.py` demuestra que el par existe y está bien; solo el
navegador demuestra que es ese par el que llega a la pantalla.

## Alternativas consideradas

### El selector libre de color

Es lo que un usuario esperaría de un producto de consumo, y se descarta porque
obliga a elegir entre dos malas salidas y no hay una tercera:

- **No certificar nada.** El producto tendría un color cuyo contraste no mide
  nadie, y la frase «contraste comprobado, no afirmado» dejaría de ser cierta el
  día que se activara. Peor: dejaría de ser cierta **en silencio**, porque nada
  fallaría.
- **Calcular automáticamente la tinta de encima**, blanca o negra según la
  luminancia del fondo. Certifica, y a cambio **el usuario acaba sin el color que
  eligió**: un amarillo pálido se convierte en un cuadrado con texto negro que no
  se parece a lo que había en el selector, y dos colores distintos que caen del
  mismo lado del umbral acaban idénticos.

Hay una tercera forma —admitir el color libre y **avisar** cuando no cumple— que
también se descarta: convierte una garantía en una recomendación, y traslada al
usuario una decisión técnica que no puede tomar.

### Un juego cerrado más grande

Doce colores en vez de seis, o el juego entero de Lucide para los iconos. Se
descarta por dos motivos distintos:

- **En color**, porque el hueco no da: con cinco tonos de dominio y el acento
  tomados, doce colores obligarían a bajar a menos de 15° entre vecinos y dos
  categorías contiguas dejarían de distinguirse —y ya no se distinguirían **para
  nadie**, no solo bajo una deuteranopia.
- **En iconos**, porque un buscador sobre las mil y pico de Lucide obliga a
  mantener una traducción de mil nombres al castellano y deja elegir una papelera
  para «Alimentación». Dieciséis cubren el vocabulario doméstico y se pueden leer
  de un vistazo en una rejilla.

### Guardar el color como texto libre y validarlo solo en la aplicación

Se descarta por lo mismo que el resto del modelo usa `CHECK` sobre `text`: sin la
restricción en la base de datos, una migración de datos o un `psql` a mano meten
un color que ninguna pantalla sabe pintar, y el fallo aparece en producción como
un cuadrado sin fondo.

### Dar cara también a las etiquetas

Se descarta y conviene decir por qué, porque la simetría tienta: un asset lleva
**varias** etiquetas, así que darles color multiplicaría por veinte lo que hay que
medir —cada etiqueta contra cada superficie y contra las demás— para decir lo que
su texto ya dice. Una etiqueta es una palabra; una categoría es una de doce.

### Poner icono a las cinco paradas de la navegación

El plan del hito lo pedía —«la categoría con su icono en la navegación móvil, que
es donde un icono se gana el sitio»— y **no entra**: en la navegación no hay
ninguna categoría. Sus cinco paradas son pantallas, y darles icono es iconografía
de navegación, que es otro trabajo y toca un ancho de 44 px medido al límite a
320 px. Queda anotado en la ficha de
[`category-identity.md`](../../../frontend/design-system/components/category-identity.md).

## Consecuencias

**Lo que gana el producto.** Una lista de inventario se recorre de un vistazo, y
la promesa que
[`iconography.md`](../../../frontend/design-system/foundations/iconography.md)
llevaba desde la Fase 1 sin poder cumplir —que el marcador de una foto que falta
es «el icono de la categoría» y no un rectángulo gris— deja de ser una promesa.

**Lo que cuesta.** Dos columnas, doce tokens, doce pares medidos y un enumerado
en tres sitios. Y una obligación permanente: **un color nuevo no existe hasta que
está en la lista del script**. Quien añada el séptimo tendrá que pasar por ahí, y
esa es toda la intención.

**Lo que no cambia.** El objetivo normativo de la ADR-006, el sistema de tokens,
la forma de derivar el modo oscuro y la regla de que nada se dice solo con color.
Esta ADR no toca ninguna de las cuatro: dice cómo entra un dato del usuario en
ellas.

**Y una obligación que hereda el sistema de diseño**: la regla de nombres de
[`tokens/`](../../../frontend/design-system/tokens/README.md) dice que un token se
llama por lo que hace y no por lo que es. Los doce colores de categoría son **la
única excepción**, y está razonada donde vive: el significado lo pone quien
clasifica, no el sistema, así que `--color-category-3` sería ilegible en la
migración, en el contrato y en el CSS.

## Validación o reversión

**Se valida** con tres cosas, y las tres corren en la CI:

1. `python scripts/check-contrast.py` mide los 48 pares en los dos modos y falla
   si alguno baja de su mínimo. **Comprobado que muerde**: subiendo la claridad
   de `moss` a un tono que parece bonito, los dos pares del musgo caen a 2,07:1 y
   2,14:1 y la construcción se para.
2. El recorrido vertical elige un color desde la pantalla y **mide el contraste
   ya aplicado** en los dos modos, leyendo `getComputedStyle` del marcador. Es lo
   único que demuestra que el par medido es el par que llega.
3. La pantalla «Catálogo» entra en la lista de la auditoría sistemática, **con
   una categoría de color puesto sembrada antes**: en gris, axe no habría mirado
   ninguno de los seis.

**Se revierte** quitando las dos columnas y sus doce tokens. No arrastra nada: el
atributo es opcional en todas partes, ninguna regla de dominio depende de él y
ningún módulo lo lee. Es, de hecho, el atributo más barato de retirar de todo el
core.

**El día que alguien quiera abrir el juego** —un selector libre, o cien colores—
lo que hay que traer no es una implementación sino una respuesta a la pregunta de
arriba: qué mide el contraste de un color que no está en ningún token. Mientras
esa respuesta no exista, abrirlo es cambiar una garantía por una esperanza. Y hay
un punto intermedio que sí sería admisible y que nadie ha pedido todavía: **más
colores cerrados**, con sus pares en la lista, que cuesta una migración y una
tarde de medir.

## Posterior a esta decisión

El cuerpo de esta ADR se conserva tal y como se aceptó. El 2026-08-20 la paleta
de marca giró al esquema de la identidad comercial de DRP —neutros de pino y
acento teal— y eso movió una premisa de la sección 3: el acento ya no ocupa el
tono 42 sino el **185**, así que el hueco que los seis colores se repartían
cambia de forma. La categoría `teal` (195) pasa a ser la más cercana a un tono
tomado —a 10° del acento— y se admite por la misma razón de forma que ya
amparaba a `moss` y `sky`: el color de una categoría nunca va solo, y su
recipiente es un cuadradito con icono y nombre, no la pastilla de un estado ni
el relleno de un botón. Los seis colores de categoría, sus valores y sus doce
pares medidos **no cambian**; el razonamiento actualizado está en
[`foundations/color.md`](../../../frontend/design-system/foundations/color.md).

## Referencias

- [ADR-006](ADR-006-frontend-stack-and-design-system.md): el sistema de diseño que
  esta ADR extiende.
- [`foundations/color.md`](../../../frontend/design-system/foundations/color.md) y
  [`tokens/`](../../../frontend/design-system/tokens/README.md): los valores.
- [`iconography.md`](../../../frontend/design-system/foundations/iconography.md):
  el juego de dieciséis y la promesa del marcador de una foto que falta.
- [`accessibility/`](../../../frontend/accessibility/README.md): los 48 pares
  medidos.
- [`category-identity.md`](../../../frontend/design-system/components/category-identity.md):
  la anatomía del marcador y del selector.
- [4.1.1](../../product/core-model.md) y [5.6](../data-model.md): dónde vive el
  dato.
