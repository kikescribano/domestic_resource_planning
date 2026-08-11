# Look and feel

| Campo | Valor |
|---|---|
| Estado | Borrador |
| Responsable | Equipo DRP |
| Ámbito | Interfaz web responsive |
| Última revisión | 2026-08-10 |

## Propósito

Definir la dirección visual y de interacción de DRP de forma comprobable. Este
documento expresa intención; los tokens y componentes implementables viven en el
[`design system`](../design-system/README.md).

La personalidad está elegida: **cálida y doméstica**. DRP es la casa, no la
oficina. Lo que sigue concreta esa elección hasta poder discutirla con números,
y resuelve el problema que trae consigo: **en un inventario de cientos de assets
la calidez estorba**, y hay que decir dónde se contiene en lugar de esperar que
no aparezca el problema.

## Personalidad de la interfaz

| Atributo | Se manifiesta en | Evitar |
|---|---|---|
| **Doméstica** | Base de papel cálido y tinta parda, sin un solo gris azulado; acento de terracota; serif en el `h1` y en los estados vacíos; ilustración de línea donde no hay datos todavía; vocabulario de casa —«Prestado a», «Se acabó», «¿Dónde está?»— en lugar de vocabulario de ERP. | La estética de panel de control: fondo gris azulado, rejilla de tarjetas de métricas, gráficas que nadie ha pedido y un logotipo arriba a la izquierda como toda identidad. |
| **Serena bajo volumen** | Una lista de 300 filas usa una familia, un tamaño y un peso; el color aparece solo en el estado; no hay sombra ni tarjeta por fila; el acento sólido sale una vez por pantalla. | Acumular señales sobre la misma fila —cebra, borde, sombra, fondo de color y negrita a la vez— y llamar a eso jerarquía. |
| **Directa** | Cada vacío nombra la siguiente acción y la ofrece; cada error dice qué ha fallado y qué se puede hacer; cada confirmación destructiva nombra el objeto y la consecuencia: «Dar de baja *Taladro Bosch*. Seguirá en el historial y dejará de poder prestarse». | «Ha ocurrido un error», «¿Estás seguro?», y el aviso de éxito que se va antes de haberse leído. |
| **Táctil y de una mano** | 44 px de objetivo mínimo; acción principal al alcance del pulgar en móvil; campos de 16 px para que iOS no haga zoom al enfocar; formulario de una columna hasta 640 px; toda la fila es pulsable, no solo su icono. | Esconder una acción frecuente detrás de un menú de tres puntos, o dejar como única zona pulsable un icono de 24 px en el extremo de la fila. |

## Principios visuales

**1. La calidez vive en el marco; el contenido trabaja tranquilo.** La identidad
se juega en el fondo, la cabecera, la navegación, los botones y los estados
vacíos. En cuanto empieza una lista, el sistema baja el tono: mismo papel de
fondo, pero sin adornos por fila. Esto decide la **jerarquía** —el marco es
reconocible, el contenido es legible—, la **densidad** —la calidez es color y
forma, no aire, así que no cuesta filas por pantalla—, el **espacio** —el aire
crece hacia fuera, en los márgenes del marco, nunca hacia dentro de la fila— y
el **movimiento** —dentro del área de contenido solo se mueve aquello que el
usuario acaba de tocar.

**2. La jerarquía se construye con tamaño y espacio, y solo con eso.** El color
queda reservado para significar. Como máximo dos tamaños de texto y dos pesos
conviven en una misma vista; lo que se quiere destacar se destaca separándolo,
no coloreándolo. Consecuencia directa sobre el **color**: en una fila de
inventario, lo único coloreado es el estado; el nombre, la ubicación y la
cantidad van en tinta. Y sobre el **movimiento**: no se anima nada para atraer
la atención hacia algo que no ha cambiado.

**3. Una pantalla, una acción principal.** El relleno de acento aparece una sola
vez; si aparece dos, una de las dos no era la principal. Tiene sitio fijo —barra
inferior en móvil, arriba a la derecha en escritorio— para que se encuentre sin
buscarla. El resto de acciones son secundarias (borde) o terciarias (solo texto).
Sobre el **espacio**, esto reserva una banda propia a la acción principal en
móvil, que no se rellena con otra cosa; sobre el **movimiento**, la única
transición larga del sistema es la que abre o cierra algo a petición del usuario.

**4. Nada se dice solo con color.** Un estado es siempre color **y** etiqueta
**y** icono. Un campo con error es siempre borde **y** icono **y** mensaje bajo
el campo. Una fila seleccionada es fondo **y** casilla marcada **y** barra de
acento en el borde de inicio. Esto cuesta **densidad** —el distintivo ocupa
sitio— y se acepta: el ahorro de quitarlo se paga con una interfaz que no
funciona con daltonismo, en blanco y negro, ni bajo el sol.

**5. El movimiento explica un cambio, nunca lo sustituye.** Toda animación
responde a un cambio de estado que ya ha ocurrido, dura menos de un tercio de
segundo y puede desaparecer sin pérdida de información. Es la condición que hace
que respetar `prefers-reduced-motion` sea gratis en lugar de ser una versión
degradada del producto.

## Dirección visual

Las ocho dimensiones quedan cerradas. Los valores concretos están en
[`design-system/tokens/`](../design-system/tokens/README.md) y su implementación
en [`frontend/src/index.css`](../../../frontend/src/index.css).

| Dimensión | Decisión vigente | Evidencia o referencia |
|---|---|---|
| **Color** | Base de papel cálido (`oklch(98.4% 0.006 85)`) sobre tinta parda; **un único acento**, terracota, para la acción principal y los enlaces; cuatro colores de feedback del sistema y **cinco de estado del dominio** —disponible, prestado, vencido, dado de baja y sin existencias—, con nombre propio aunque hoy compartan valor con su equivalente de feedback. El azul de «sin existencias» es el único color frío del sistema, y lo es a propósito. | [`foundations/color.md`](../design-system/foundations/color.md); auditoría de 36 pares en [`accessibility/`](../accessibility/README.md), con 4,70:1 como peor caso de texto y 3,49:1 como peor caso no textual |
| **Tipografía** | Dos familias con reparto explícito: **serif** (`Iowan Old Style` → `Palatino` → `Georgia`) para el `h1`, los titulares de sección y los estados vacíos, **nunca por debajo de 20 px ni dentro de una fila**; **sans de sistema** para todo lo demás; **monoespaciada** para número de serie, código de barras e identificadores. Escala de siete pasos nombrada por papel, dos de ellos con `clamp()`. Pilas de sistema: cero bytes descargados y ningún salto de fuente al cargar. | [`foundations/typography.md`](../design-system/foundations/typography.md) |
| **Densidad de información** | **Dos densidades, decididas por el dispositivo de entrada y no por el gusto.** Con el dedo no cabe ser denso —44 px de objetivo mínimo—, así que en móvil el listado es de tarjetas y respira; con puntero y teclado el aire se convierte en scroll, así que desde `md` el listado es una tabla de fila compacta (36 px), cifras en `tabular-nums` y cabecera fija. No es una preferencia configurable en la Fase 1. | [`foundations/density.md`](../design-system/foundations/density.md) |
| **Espaciado y ritmo** | Rejilla de **4 px**, con cuatro medidas con nombre —`gutter` (16 px), `gutter-lg` (24 px), `row` (12 px) y `row-compact` (6 px)— y tres anchuras de composición: `form` (544 px), `reading` (68ch) y `shell` (1536 px). El espacio vertical entre bloques crece con la pantalla; el interlineado y la longitud de línea, no. | [`foundations/space.md`](../design-system/foundations/space.md) |
| **Formas, bordes y elevación** | Radios de 4 a 24 px: **8 px en controles, 12 px en tarjetas, 16 px en diálogos**, redondeo completo solo en avatares y distintivos de estado. Cuatro niveles de elevación, con sombra **de tono cálido y nunca negra**; en modo oscuro la sombra apenas se ve, así que allí quien separa planos es el escalón de claridad de la superficie más el borde. **En un listado denso no hay sombra por fila**: separan las líneas de `border-subtle`. | [`foundations/shape-and-elevation.md`](../design-system/foundations/shape-and-elevation.md) |
| **Iconografía e ilustración** | Un solo juego de iconos, **[Lucide](https://lucide.dev)** (ISC), de trazo, cuadrícula de 24, grosor 1,75, en dos tamaños (20 px en línea de texto, 24 px en acción). Un icono fijo por estado del dominio, que es la mitad de la regla «nada se dice solo con color». **Ilustración solo en tres sitios** —onboarding, vacío y error bloqueante—, como máximo una por pantalla, dibujada con los propios tokens (línea de `border-strong` sobre relleno de `accent-soft`), sin degradados ni sombras, y **nunca dentro de una vista con filas**. | [`foundations/iconography.md`](../design-system/foundations/iconography.md) |
| **Fotografía y recursos** | **La única fotografía del producto es la del usuario**: foto de asset, de artículo y avatar. No hay banco de imágenes ni fotografía decorativa —una casa ajena no ilustra la tuya—. Encuadre 1:1 con recorte centrado, esquinas de 8 px; el marcador de posición es el **icono de la categoría** sobre `surface-sunken`, nunca un rectángulo gris. Se sirven desde el dominio de ficheros que fija la [ADR-005](../../common/architecture/decisions/ADR-005-local-file-storage.md), con `loading="lazy"` fuera del primer pliegue. | [`ADR-005`](../../common/architecture/decisions/ADR-005-local-file-storage.md); [`file-storage.md`](../../backend/architecture/file-storage.md) |
| **Movimiento y transiciones** | Cuatro duraciones —80 ms para la respuesta al toque, 140 ms para un cambio de estado, 220 ms para desplegar, 320 ms para abrir un diálogo u hoja— y tres curvas. Se anima opacidad y transformación, nunca la geometría del layout. **Nada se mueve en bucle salvo el indicador de carga.** `prefers-reduced-motion: reduce` deja todo en 1 ms, y por el principio 5 eso no pierde información. | [`foundations/motion.md`](../design-system/foundations/motion.md) |

### Cómo se contiene la calidez en los listados densos

Es el riesgo conocido de esta dirección y merece su propia regla, porque no se
resuelve solo. La calidez que funciona en una pantalla de alta funciona en contra
en un inventario de cientos de filas: cada sombra, cada esquina generosa y cada
tipografía con carácter se multiplican por el número de filas y el resultado es
ruido.

La regla es **el presupuesto de calidez**: en una vista de listado, la
personalidad se gasta en tres sitios y en ninguno más.

1. **El papel de fondo.** El listado se dibuja sobre la misma base cálida que el
   resto de la aplicación; ese tono es lo que impide que la tabla parezca una
   hoja de cálculo, y no cuesta ni un píxel.
2. **La acción principal.** El único relleno de terracota de la pantalla.
3. **El estado.** El único color dentro de la fila, y con su etiqueta y su icono
   al lado.

Y lo que queda expresamente prohibido dentro de una fila: serif, sombra, radio
mayor que `sm`, ilustración, segundo peso tipográfico, fondo de color que no sea
la selección o el paso del puntero, y cualquier animación que no sea la del
propio puntero.

Hay una segunda mitad, que es de estructura y no de estilo. La densidad no se
decide por gusto sino por **el dispositivo de entrada**: con el dedo, los 44 px
de objetivo mínimo imponen holgura y el listado es de tarjetas; con puntero y
teclado esa holgura se convierte en scroll, así que desde `md` el listado pasa a
tabla compacta, con cabecera fija, cifras alineadas a la derecha en
`tabular-nums` y la fila entera pulsable. Dicho de otro modo: **la interfaz nunca
está siendo densa y cálida a la vez en el mismo sitio**, porque nunca está siendo
densa en el sitio donde la calidez se ve.

## Estados de experiencia

**Estado inicial y onboarding.** El alta de un hogar es la única escritura sin
autenticar de la API, y también el primer contacto con el producto: pantalla de
una sola columna sobre `surface`, un campo por paso, serif en el titular y la
ilustración de la casa. Al entrar por primera vez, el hogar ya trae sus
categorías sembradas, así que el inventario nunca arranca del todo vacío: la
primera pantalla propone dar de alta el primer asset, no explica el modelo de
datos. La invitación a otro miembro y la verificación por correo se resuelven en
la misma plantilla.

**Carga, carga progresiva y actualización.** Tres situaciones distintas y tres
respuestas distintas, que es lo que evita el spinner universal:

- **Primera carga de una vista**: esqueleto con la forma real del contenido —el
  número de filas que caben, no tres genéricas—, sin animación de brillo.
- **Actualización de algo ya visible** (`refetch` de TanStack Query): el
  contenido anterior se queda en pantalla y solo se marca con una barra de
  progreso fina bajo la cabecera. No se vacía lo que ya se estaba leyendo.
- **Acción del usuario**: el botón pasa a estado ocupado, conserva su anchura y
  se deshabilita; nada más de la pantalla se bloquea. Por encima de 400 ms se
  añade texto («Guardando…»), porque un botón ocupado y mudo se percibe como una
  aplicación colgada.

**Vacío con la siguiente acción clara.** Nunca «No hay resultados» a secas. Un
vacío lleva ilustración, una frase que dice por qué está vacío y **un botón con
la acción que lo llena**. Se distinguen tres, porque la siguiente acción es
distinta en cada uno: vacío **inicial** (no hay nada todavía → crear), vacío por
**filtro** (hay datos pero el filtro los esconde → limpiar filtro, y se muestra
cuál está aplicado) y vacío por **permiso** (existe pero este rol no lo ve → se
dice quién puede darlo, sin ilustración).

**Éxito, confirmación y feedback no intrusivo.** El éxito por defecto es el
propio cambio en pantalla: si el asset ya aparece movido, no hace falta un aviso
que lo diga. Solo se anuncia lo que no se ve —una operación en segundo plano, un
correo enviado, algo fuera de la vista actual—, con un aviso en la esquina
inferior en móvil y superior derecha en escritorio, de 5 segundos, que no tapa la
acción principal, se puede cerrar y **se anuncia por `role="status"`**. Cuando la
acción es reversible, el aviso lleva el deshacer, y entonces no caduca hasta que
la operación se confirma.

**Error recuperable y error bloqueante.** El recuperable se resuelve donde
ocurrió: validación bajo el campo, con borde e icono además del texto, sin mover
la maquetación —el hueco del mensaje está reservado— y con el foco puesto en el
primer campo con error. El fallo de una operación completa se muestra en una
franja sobre el formulario, **conservando todo lo que el usuario había escrito** y
ofreciendo reintentar. El bloqueante —sesión caducada, hogar sin acceso, error
del servidor— ocupa la vista entera, con ilustración, la explicación en una frase
y una única salida. Los códigos de error del contrato se traducen a lenguaje de
casa; el código queda visible en pequeño, para poder decirlo al pedir ayuda.

**Conectividad degradada: no se acepta el modo sin conexión.** Es una decisión,
no un olvido. Un modo offline de verdad exige cola de mutaciones, resolución de
conflictos y una segunda fuente de verdad en el cliente, y eso es un proyecto en
sí mismo para una aplicación que vive tras un login, en la red de casa y contra
un servidor propio. Lo que sí se exige es **degradar con honestidad**: detectar
la pérdida de conexión y decirlo en una franja persistente, no perder nunca lo
que el usuario ya había escrito, dejar la lectura en caché visible marcada como
posiblemente desactualizada, y ofrecer reintento manual además del automático de
TanStack Query. Se revisará si algún día se quiere una aplicación instalable.

**Operaciones destructivas.** En DRP casi nada se borra: la baja de un asset es
lógica y el historial se conserva. Eso cambia el tono de la confirmación, que
debe explicar la consecuencia real en lugar de asustar. La confirmación nombra el
objeto y lo que pasa con él, el botón dice el verbo («Dar de baja») y no
«Aceptar», y el foco arranca en cancelar. Se pide escribir el nombre solo cuando
la operación es de verdad irreversible y de alcance amplio —hoy, ninguna del
core—. Cuando algo no se puede hacer porque hay una dependencia —un asset con
hijos, o con un préstamo abierto—, no se muestra un error genérico: se dice cuál
es la dependencia y se enlaza a ella.

## Comportamiento responsive

La interfaz debe conservar jerarquía, legibilidad y capacidad de acción desde un
dispositivo equivalente a iPhone X hasta una pantalla ultrawide. Los breakpoints
se definen a partir del contenido y del layout, no de modelos concretos: los de
Tailwind cubren el tramo habitual y DRP añade uno solo, `3xl` a 1600 px, que es
donde la ventana deja de crecer útilmente y hay que decidir qué hacer con el
sobrante.

| Escenario | Qué validar |
|---|---|
| **Móvil vertical mínimo** (375 px) | Ningún scroll horizontal en ninguna vista, incluidas las tablas —que ahí no son tablas sino tarjetas—. Navegación en barra inferior alcanzable con el pulgar. Formulario de una columna, campos de 16 px para que iOS no haga zoom al enfocar, y la acción principal siempre visible sin cerrar el teclado. Todo objetivo pulsable ≥ 44 px. Los nombres largos de asset se truncan en dos líneas con el estado siempre visible. |
| **Móvil horizontal** (≈ 667×375) | Reflujo sin pérdida de contenido con el teclado abierto, que se come la mitad de la altura: los diálogos pasan a hoja con scroll propio y el campo enfocado se desplaza a la vista. Ningún overlay a pantalla completa que tape su propio botón de cerrar. |
| **Tablet o ventana intermedia** (768–1024 px) | El punto donde la navegación deja la barra inferior y pasa a lateral, y el listado deja las tarjetas y pasa a tabla compacta: hay que validar que la transición no pierde ninguna acción por el camino. Formularios siguen a una columna aunque quepan dos. |
| **Escritorio** (1280–1440 px) | Jerarquía con densidad: cabecera de tabla fija, fila entera pulsable, recorrido completo con teclado y foco visible en cada parada, atajos de las acciones frecuentes y ausencia de zonas muertas de más de una pantalla de alto. |
| **Ultrawide** (2560–3440 px) | Que el espacio sobrante se use a propósito y no estirando: el contenido se limita a `shell` (1536 px) y la línea de texto a `reading` (68ch); el sobrante se reparte en margen, o en una segunda columna con el detalle del elemento seleccionado. Validar que no aparecen líneas de 200 caracteres ni filas donde el nombre y su estado quedan a un palmo de distancia. |

Además, y en todos los tramos: la vista debe seguir siendo utilizable con **zoom
al 200 %** y con la **anchura reducida a 320 px** sin scroll en dos ejes, que es
el criterio de reflujo de WCAG y el que suele romperse sin que nadie lo note.

## Accesibilidad visual y motriz

El objetivo normativo es **WCAG 2.2 nivel AA**, fijado por la
[ADR-006](../../common/architecture/decisions/ADR-006-frontend-stack-and-design-system.md).
Lo que la dirección visual aporta a ese compromiso, con sus números, está en
[`accessibility/`](../accessibility/README.md); en resumen:

- **El color no es nunca el único medio.** Los cinco estados del dominio llevan
  color, etiqueta e icono propios; los campos con error llevan borde, icono y
  mensaje; la fila seleccionada lleva fondo, casilla y barra de acento.
- **Contraste comprobado, no afirmado.** Los 36 pares que el sistema usa de
  verdad están medidos en los dos modos: el peor caso de texto es 4,70:1 y el
  peor caso no textual, 3,49:1. Ningún token se sale del gamut sRGB, para que el
  número medido sea el número que se ve. Lo comprueba
  [`scripts/check-contrast.py`](../../../scripts/check-contrast.py) en cada
  ejecución de la CI, sobre los valores reales de `index.css`: la tabla no puede
  quedarse desfasada sin que la construcción falle.
- **Foco visible siempre**, con un solo anillo de 2 px y 2 px de separación,
  declarado en la capa base para que ningún componente pueda olvidarlo: 9,69:1
  sobre el fondo en claro y 11,68:1 en oscuro.
- **Objetivo táctil de 44 px**, por encima de los 24 px que exige el criterio
  2.5.8, porque esto se usa de pie y con una mano.
- **Movimiento reducible a cero** sin pérdida de información, por el principio 5.
- **Modo oscuro real**, que sigue al sistema y admite conmutación explícita, con
  la misma auditoría de contraste que el modo claro.

## Validación

Antes de pasar a `Vigente`, este documento debe incluir referencias visuales,
prototipos responsive, revisión de accesibilidad y evidencia de validación sobre
los principales flujos de usuario.

Lo que falta concretamente, y por qué sigue en `Borrador` pese a tener las ocho
dimensiones cerradas: la ADR-006 condiciona el paso a `Vigente` a que el
recorrido vertical se ejecute con Playwright de punta a punta y a que una
auditoría de accesibilidad confirme contraste, foco y navegación por teclado a
375 px y en ultrawide. Ambas cosas llegan en el **Hito 4** según el
[roadmap](../../common/product/roadmap.md). Hasta entonces la dirección es
ejecutable y medida, pero no está validada sobre pantallas reales.

## Historial

| Fecha | Cambio |
|---|---|
| 2026-08-06 | Se crea la plantilla inicial; no hay dirección visual aprobada. |
| 2026-08-10 | Se cierra la dirección visual del Hito 1: personalidad «cálida y doméstica» concretada en cuatro atributos, cinco principios visuales, las **ocho dimensiones resueltas** sin ningún `Por decidir`, la regla del presupuesto de calidez para contener la densidad en los listados largos, los siete estados de experiencia —incluido el rechazo explícito del modo sin conexión— y la tabla responsive completa. Se fija responsable y se mantiene el estado en `Borrador` hasta la validación con Playwright del Hito 4. |
