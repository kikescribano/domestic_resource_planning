# Accesibilidad

| Campo | Valor |
|---|---|
| Estado | Vigente |
| Responsable | Equipo DRP |
| Ámbito | Interfaz web responsive |
| Última revisión | 2026-08-17 |

## Objetivo normativo

**WCAG 2.2, nivel AA.** Lo fija la
[ADR-006](../../common/architecture/decisions/ADR-006-frontend-stack-and-design-system.md),
que además explica por qué se acepta el coste: es un compromiso verificable y,
por tanto, **incumplible de forma visible**. Un objetivo que no se puede medir no
obliga a nada.

De ahí el criterio que gobierna este documento: aquí no se escribe ninguna
afirmación que no venga acompañada de cómo se comprueba.

## Contraste de color

Los valores viven en [`frontend/src/index.css`](../../../frontend/src/index.css)
y su significado en
[`design-system/foundations/color.md`](../design-system/foundations/color.md).
Lo que sigue es la medición.

**Se mide con
[`scripts/check-contrast.py`](../../../scripts/check-contrast.py)**, que lee los
valores `oklch()` reales del fichero de tokens, los convierte a sRGB con los
coeficientes de CSS Color 4 y calcula el ratio de cada par. Forma parte de
[la CI](../../../.github/workflows/ci.yml), así que esta tabla no puede quedarse
desfasada en silencio: si alguien retoca un token y algún par baja de su mínimo,
la construcción falla.

```bash
python scripts/check-contrast.py
```

**48 pares medidos en los dos modos**, con estos resultados:

| Medida | Valor | Mínimo exigido |
|---|---|---|
| Peor caso de **texto** | **4,70:1** | 4,5:1 (1.4.3) |
| Peor caso **no textual** | **3,49:1** | 3:1 (1.4.11) |
| Anillo de foco sobre el fondo, modo claro | 9,69:1 | 3:1 (1.4.11) |
| Anillo de foco sobre el fondo, modo oscuro | 11,68:1 | 3:1 (1.4.11) |
| Tokens fuera del gamut sRGB | ninguno | — |

Tres notas sobre por qué se mide así:

- **El peor caso de texto es el distintivo de «dado de baja»** (4,70:1), que es
  el único gris del sistema y por tanto el que menos margen tiene. Está por
  encima del mínimo, pero es el par que hay que volver a medir antes que ningún
  otro si se toca la paleta.
- **El peor caso no textual es el borde de control** (3,49:1) sobre el papel.
  `border-subtle` y `border` no son intercambiables por esto: el primero es
  decorativo —separa filas— y el segundo delimita un control, así que está
  obligado a 3:1. Usar el sutil en un `input` incumple 1.4.11 sin que se note a
  simple vista.
- **Ningún token se sale del gamut sRGB.** Importa porque un color fuera de gamut
  lo recorta el navegador, y entonces el número medido deja de ser el número que
  se ve: la auditoría estaría midiendo un color que nadie tiene delante. **Y esa
  comprobación es la que más ha trabajado** al añadir los seis colores de
  categoría: tres de los doce valores nuevos —el suave del índigo y los fuertes
  del cielo y el turquesa— nacieron fuera de gamut y hubo que bajarles el croma
  antes de que el número significara algo.
- **Doce de los cuarenta y ocho pares los elige el usuario**, y son los seis
  colores de categoría de la [ADR-015](../../common/architecture/decisions/ADR-015-user-chosen-category-identity.md):
  dos por color, contra su propio fondo suave —el marcador— y contra
  `surface-sunken` —el hueco de una foto que falta—. Es la única parte de la
  interfaz cuyo color no lo decide el sistema, y por eso el juego es cerrado:
  con un selector libre no habría lista que medir.

Los pares con transparencia (`scrim`) no se miden: su contraste depende de lo que
haya debajo, así que el número no significaría nada. Se compensa por otra vía —el
diálogo que va encima del velo tiene su propia superficie opaca, que sí está
medida.

## El color nunca es el único portador

Es el criterio 1.4.1 y la parte más fácil de incumplir sin darse cuenta, porque
en la pantalla del que diseña siempre se distingue. En DRP:

| Situación | Además del color |
|---|---|
| Estado del dominio (disponible, prestado, vencido, dado de baja, sin existencias) | Etiqueta de texto **e** icono propio |
| Identidad visual de una categoría (icono y color) | **El nombre de la categoría, siempre al lado**; el icono dentro del marcador |
| Campo con error | Borde, icono **y** mensaje bajo el campo |
| Fila seleccionada | Fondo, casilla marcada **y** barra de acento en el borde de inicio |
| Enlace dentro de un párrafo | Subrayado, no solo color |

El azul de «sin existencias» es el único color frío del sistema, y lo es a
propósito: sobrevive a una deuteranopia que sí confunde verde, ámbar y rojo entre
sí.

## Foco

- **Visible siempre**, con un anillo de 2 px y 2 px de separación, declarado en
  la capa base de `index.css` para que ningún componente pueda olvidarlo.
- Los 2 px de separación no son estéticos: dejan ver el fondo entre el control y
  el anillo, que es lo que salva el caso difícil —un botón de relleno sólido,
  donde el anillo pegado se confundiría con el propio relleno.
- **`:focus-visible` y no `:focus`**, para no dibujarlo al hacer clic con el
  ratón, que es lo que empuja a la gente a quitarlo entero.
- El orden de tabulación sigue el orden visual; no se usa `tabindex` positivo.
- Criterios cubiertos: 2.4.7 (foco visible), 2.4.11 (foco no tapado) y 2.4.13
  (apariencia del foco).

## Objetivo táctil

**44 × 44 px**, por encima de los 24 × 24 px que exige el criterio 2.5.8. Se sube
porque esto se usa de pie, en la cocina, con una mano y a veces con el móvil en
la otra.

El token `--spacing-touch-min` (24 px) existe solo para las excepciones que la
propia norma admite —un enlace dentro de un párrafo de texto—, no como
alternativa cómoda.

## Movimiento

`prefers-reduced-motion: reduce` deja todas las duraciones en 1 ms y corta
cualquier animación o transición ya declarada. Esto **no degrada el producto**, y
esa es justamente la condición: por el principio 5 de
[`look-and-feel.md`](../product-design/look-and-feel.md), toda animación responde
a un cambio de estado que ya ha ocurrido y puede desaparecer sin pérdida de
información. Nada se mueve en bucle salvo el indicador de carga.

Cubre 2.3.3 (animación por interacción) y, al no haber nada que parpadee ni se
mueva solo, también 2.2.2.

## Zoom y reflujo

- Utilizable con **zoom al 200 %** (1.4.4). `-webkit-text-size-adjust: 100%`
  impide que el navegador móvil desactive el ajuste.
- **Reflujo a 320 px sin scroll en dos ejes** (1.4.10). Es el criterio que más se
  rompe sin que nadie lo note, porque la anchura de referencia del diseño es 375
  px y a 320 px todavía tiene que funcionar.
- Todo contenido ancho —tablas, bloques de código— desplaza dentro de su propio
  contenedor; el cuerpo de la página nunca desplaza en horizontal.

## Modo oscuro

No es un extra: es un ajuste de accesibilidad para quien tiene fotofobia o
sensibilidad a la luz. Sigue al sistema por defecto y admite conmutación
explícita con `data-theme`, que gana en las dos direcciones —para que quien tenga
el sistema en oscuro pueda quedarse en claro—. **Los 48 pares están medidos en
los dos modos**, no solo en el claro.

## Lo que se comprueba sobre pantallas montadas

La medición de la sección anterior es de **tokens**, no de pantallas. Lo que solo
se puede verificar sobre la interfaz montada lo comprueba el recorrido vertical
—[`vertical-journey.spec.ts`](../../../frontend/e2e/vertical-journey.spec.ts)—, que
la CI ejecuta en un trabajo propio:

- **Auditoría automática (axe)** acotada a A y AA, **en los dos modos**, sobre los
  préstamos del hogar y la vista externa.
- **Navegación por teclado** hasta la acción de cada una de esas dos pantallas,
  activándola con `Enter`, con el **anillo de foco comprobado en cada parada** del
  camino y el salto al contenido como primera parada.
- **Reflujo a 320 px, a 375 px y en ultrawide**, midiendo que nada desborde a lo
  ancho abajo y que el contenido no se estire sin tope arriba.
- **Un solo anuncio por cambio** en el único cambio que no mueve el foco.

Con esto se cumple la condición que la
[ADR-006](../../common/architecture/decisions/ADR-006-frontend-stack-and-design-system.md)
ponía —contraste, foco visible y teclado a 375 px y en ultrawide—, y
[`look-and-feel.md`](../product-design/look-and-feel.md) pasa a `Vigente`.

### Dos cosas que aprendimos midiendo, y que valen para la próxima auditoría

- **Auditar durante el cambio de tema mide un color que no existe.** Al poner
  `data-theme` hay 140 ms de `transition-colors` en los que cada color es una
  mezcla de los dos modos. Auditando ahí, axe acusó al botón principal de dar
  3,55:1 en oscuro cuando sus tokens dan 6,77:1. La auditoría espera ahora a que
  no quede ninguna transición viva, y no a un plazo fijo.
- **Auditar una pantalla que aún carga no dice nada de la pantalla.** Con el
  `Spinner` puesto, axe recorre cuatro elementos y pasa. La primera versión de la
  prueba lo hacía, y por eso pasaba unas veces y fallaba otras.

## Lo que sigue sin comprobarse

- **Un lector de pantalla de verdad.** Todo lo de arriba es automático; axe cubre
  del orden de la mitad de los criterios y ninguna herramienta juzga si un texto
  alternativo dice algo útil.
- **Las demás pantallas.** El recorrido audita las dos que atraviesa; las otras
  diecisiete rutas de [`App.tsx`](../../../frontend/src/App.tsx) están cubiertas por
  los tokens, por las fichas de componente y por las pruebas de componente, que es
  menos que una auditoría sobre el DOM.
- **El conmutador de tema no existe todavía**, así que `data-theme` hoy solo lo
  pone la prueba. Está anotado como previsto en
  [`patterns/navigation.md`](../design-system/patterns/navigation.md).

## Historial

| Fecha | Cambio |
|---|---|
| 2026-08-20 | La auditoría pasa de **36 pares a 48**: los doce que añaden los seis colores de categoría, que son los primeros que **elige el usuario** y no el sistema (cierre de huecos, Hito 4). Se anota que tres de los doce valores nuevos nacieron fuera del gamut sRGB y que la comprobación de gamut fue lo que lo destapó. |
| 2026-08-11 | Se fija WCAG 2.2 AA como objetivo normativo y se documenta la auditoría de contraste de los 36 pares, con su script de comprobación en la CI. Se anota lo que queda pendiente de verificar sobre pantallas reales. |
| 2026-08-17 | Se sustituye «lo que todavía no está comprobado» por lo que el recorrido vertical comprueba de verdad sobre pantallas montadas —axe en los dos modos, teclado con anillo de foco en cada parada, reflujo a 320, 375 y ultrawide, y anuncio único—, con lo que se cumple la condición de la ADR-006 y `look-and-feel.md` pasa a `Vigente`. Se anotan los dos falsos resultados que la propia medición produjo —auditar a mitad de la transición de tema y auditar con el `Spinner` puesto— y lo que sigue sin comprobarse: un lector de pantalla real, las diecisiete rutas que el recorrido no atraviesa y el conmutador de tema, que no existe. |
