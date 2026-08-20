# Iconografía e ilustración

| Campo | Valor |
|---|---|
| Estado | Vigente |
| Responsable | Equipo DRP |
| Ámbito | frontend |
| Última revisión | 2026-08-20 |

## Propósito

Fijar el juego de iconos, el icono fijo de cada estado del dominio —que es la
mitad de la regla «nada se dice solo con color»— y dónde se admite ilustración.

## Alcance

### Incluido

- El juego de iconos elegido y sus reglas de uso.
- El mapa de icono por estado del dominio.
- Dónde se admite ilustración y con qué recursos se dibuja.

### Fuera de alcance

- Los ficheros de los recursos, que irán en `assets/` cuando existan.
- ~~El icono de categoría, que es un dato del hogar y no una decisión visual.~~
  **Entra en alcance el 2026-08-20**: sigue siendo un dato del hogar, pero el
  hogar lo elige dentro de un **juego cerrado**, y decidir cuáles son esos
  dieciséis sí es una decisión visual. Ver «El juego de categoría» más abajo y la
  [ADR-015](../../../common/architecture/decisions/ADR-015-user-chosen-category-identity.md).

## Contenido

### El juego de iconos: Lucide

Se adopta **[Lucide](https://lucide.dev)** (licencia ISC), a través de
`lucide-react`. Los motivos, en orden de peso:

- Es de **trazo**, sobre cuadrícula de 24, que es lo que combina con una interfaz
  de bordes finos y sombras tenues. Un juego relleno pesaría demasiado en una
  fila.
- Tiene el vocabulario doméstico que hace falta —caja, casa, estantería,
  etiqueta, llave, herramienta, carrito— sin quedarse en iconos de oficina.
- Se importa icono a icono, así que el paquete solo se lleva lo que se usa.
- La licencia ISC no impone atribución en la interfaz.

Reglas de uso:

| Regla | Valor |
|---|---|
| Grosor de trazo | 1,75 |
| Tamaño en línea de texto | 20 px |
| Tamaño en acción o botón | 24 px, dentro de un objetivo de 44 px |
| Color | Hereda la tinta del contexto (`currentColor`); nunca color propio salvo en un estado |
| Semántica | `aria-hidden` cuando acompaña a un texto que ya lo dice; nombre accesible propio cuando el icono **es** el botón |

Un icono no sustituye nunca a una etiqueta en una acción destructiva o poco
frecuente. La papelera sola es un acertijo.

### Un icono fijo por estado

Esta tabla es normativa: es lo que hace que los cinco estados se distingan sin
color, y por tanto lo que sostiene el criterio 1.4.1 de WCAG.

| Estado | Icono (Lucide) | Etiqueta visible |
|---|---|---|
| `AVAILABLE` | `check-circle` | Disponible |
| `LENT` | `hand-helping` | Prestado |
| `OVERDUE` | `alarm-clock` | Vencido |
| `DECOMMISSIONED` | `archive` | Dado de baja |
| Consumible a cero | `package-open` | Sin existencias |

Las tres piezas van siempre juntas —color, icono y etiqueta—. En una fila muy
estrecha se puede reducir la etiqueta a su inicial visual, pero **nunca se
elimina el icono ni el nombre accesible**: el distintivo lleva su texto aunque no
se vea.

Los iconos son distinguibles entre sí por silueta, que es la comprobación que
importa: un círculo, una mano, un reloj, una caja cerrada y una caja abierta. Si
dos estados llegaran a compartir silueta, el color no bastaría para separarlos.

### El juego de categoría: dieciséis, y cerrado

Un hogar le pone icono a cada una de sus categorías, y lo elige de esta lista y
no de las mil y pico de Lucide. El motivo de que sea cerrada es el mismo que el
del color y está en la
[ADR-015](../../../common/architecture/decisions/ADR-015-user-chosen-category-identity.md):
un buscador sobre el juego entero obliga a mantener una traducción de mil nombres
al castellano, y deja elegir una papelera para «Alimentación».

| Valor | Icono (Lucide) | Cómo se llama en pantalla |
|---|---|---|
| `BOX` | `box` | Caja |
| `SOFA` | `sofa` | Sofá |
| `UTENSILS` | `utensils` | Cubiertos |
| `SPRAY` | `spray-can` | Limpieza |
| `TOOL` | `wrench` | Herramienta |
| `FRAME` | `frame` | Cuadro |
| `PLUG` | `plug` | Enchufe |
| `POT` | `cooking-pot` | Cazuela |
| `PILL` | `pill` | Medicina |
| `MONITOR` | `monitor` | Pantalla |
| `SHIRT` | `shirt` | Ropa |
| `BIKE` | `bike` | Bicicleta |
| `PENCIL` | `pencil` | Lápiz |
| `CAR` | `car` | Coche |
| `LEAF` | `leaf` | Planta |
| `PAW` | `paw-print` | Mascota |

Las doce primeras cubren las doce categorías del hogar de demostración; las
cuatro últimas —coche, planta, mascota y caja— cubren lo que un hogar añade y el
genérico. **`BOX` es además el que se pinta cuando no hay ninguno elegido**, que
es el caso normal.

Igual que en la tabla de estados, los dieciséis se distinguen **por silueta**. Y
el icono de una categoría **nunca va solo**: su nombre está siempre al lado.

### Ilustración: tres sitios y ninguno más

La ilustración es lo que más aporta a la personalidad doméstica y lo que más daño
hace si se reparte. Se admite en:

1. **Onboarding**, en el alta de hogar y en la invitación.
2. **Estados vacíos**, uno por vista.
3. **Error bloqueante** a pantalla completa.

Y en ningún otro sitio. En particular, **ninguna vista con filas lleva
ilustración**, ni siquiera en la cabecera.

Cómo se dibujan, para que parezcan del mismo sistema y no de un banco de
imágenes:

- SVG de línea, con el mismo grosor de trazo que los iconos.
- Dos colores y no más: línea en `border-strong`, relleno en `accent-soft`.
- Sin degradados, sin sombras y sin perspectiva.
- Motivos de casa: estantes, cajas, una despensa, un cajón de herramientas. Nunca
  personas, que envejecen mal y obligan a decidir a quién se representa.
- `aria-hidden`, porque el texto que la acompaña ya dice lo que hay que saber.
- Se adaptan al modo oscuro solas, porque están dibujadas con tokens.

### Fotografía

La única fotografía del producto es **la del usuario**: foto de asset, de
artículo y avatar. No hay fotografía decorativa ni banco de imágenes; una casa
ajena no ilustra la tuya, y una despensa de catálogo hace que el inventario
propio parezca pobre.

- Encuadre 1:1 con recorte centrado y esquinas de `--radius-md`.
- El marcador de posición no es un rectángulo gris: es el **icono de la
  categoría** sobre `surface-sunken`, que además ya dice algo útil. **Dejó de ser
  una promesa el 2026-08-20**: el icono existe desde que una categoría lo tiene, y
  el marcador es el `CategoryMarker` de tamaño `lg` con su nombre accesible —«Sin
  foto. Categoría: Herramientas»—, porque ahí no hay texto al lado que lo diga.
  Una categoría sin icono elegido cae en `BOX` sobre `surface-sunken`, que es el
  mismo hueco de antes pero con forma.
- `loading="lazy"` fuera del primer pliegue, y `alt` con el nombre del asset.
- Se sirven desde el dominio de ficheros que fija la
  [ADR-005](../../../common/architecture/decisions/ADR-005-local-file-storage.md).

## Decisiones abiertas

- ~~**La dependencia `lucide-react` todavía no está en `frontend/package.json`.**~~
  **Resuelta el 2026-08-20**: está, y con la medida delante —**5,88 kB sobre la
  primera carga, 2,63 kB comprimidos**, con los dieciséis iconos de categoría
  dentro—. El peso, que era lo que la mantenía fuera, resultó ser aproximadamente
  el mismo que dibujarlos a mano; lo que decidió es que un juego adoptado y no
  instalado obliga a imitar a mano lo que ya se había elegido. Los cuatro iconos
  que estaban a mano se migraron a la vez.
- **La tabla de un icono por estado sigue sin cumplirse, y ya no por falta de
  dependencia.** Este documento la declara normativa —«lo que sostiene el criterio
  1.4.1»— y la ficha de
  [`status-badge.md`](../components/status-badge.md) declara lo contrario con su
  motivo: quince iconos idénticos en columna son ruido, no información, y el
  Hito 3 del cierre de huecos lo volvió a confirmar. **Son dos documentos del
  sistema de diseño que se contradicen**, y resolverlo obliga a tocar uno de los
  dos. No se resuelve aquí porque no es de este hito, pero deja de estar
  escondido detrás de la dependencia.
- **El juego real de ilustraciones no existe.** Están definidas las reglas para
  dibujarlas, no los ficheros. Se crean con las pantallas que las necesiten y
  viven en `assets/` con su fuente editable.

## Referencias

- [`color.md`](color.md): los cinco estados y por qué el color no basta.
- [`look-and-feel.md`](../../product-design/look-and-feel.md)
- [`accessibility/`](../../accessibility/README.md)

## Historial de cambios

| Fecha | Cambio | Autor |
|---|---|---|
| 2026-08-20 | **Lucide deja de estar adoptado y no instalado** (cierre de huecos, Hito 4), con la medida delante. Entra el **juego cerrado de dieciséis iconos de categoría**, que sale del «fuera de alcance» al ser hoy una elección dentro de una lista que este documento fija. La promesa del **marcador de una foto que falta** deja de serlo. Y se anota, ya sin la dependencia por delante, que la tabla de icono por estado contradice a `status-badge.md`. | Equipo DRP |
| 2026-08-10 | Creación del documento; se adopta Lucide y se fija el icono de cada estado. | Equipo DRP |
