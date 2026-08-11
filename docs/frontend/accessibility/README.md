# Accesibilidad

| Campo | Valor |
|---|---|
| Estado | Vigente |
| Responsable | Equipo DRP |
| Ámbito | Interfaz web responsive |
| Última revisión | 2026-08-11 |

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

**36 pares medidos en los dos modos**, con estos resultados:

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
  se ve: la auditoría estaría midiendo un color que nadie tiene delante.

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
el sistema en oscuro pueda quedarse en claro—. **Los 36 pares están medidos en
los dos modos**, no solo en el claro.

## Lo que todavía no está comprobado

La medición de esta página es de **tokens**, no de pantallas. Falta lo que solo
se puede verificar sobre la interfaz montada, y llega en el Hito 4 con la batería
E2E:

- Recorrido completo por teclado de cada flujo, con el foco visible en cada
  parada.
- Anuncios de lector de pantalla en los cambios que no mueven el foco.
- Reflujo real a 320 px y en ultrawide, sobre las pantallas construidas.
- Auditoría automática (axe) integrada en Playwright.

Hasta entonces, `look-and-feel.md` sigue en `Borrador` por este motivo, y no por
falta de decisiones.

## Historial

| Fecha | Cambio |
|---|---|
| 2026-08-11 | Se fija WCAG 2.2 AA como objetivo normativo y se documenta la auditoría de contraste de los 36 pares, con su script de comprobación en la CI. Se anota lo que queda pendiente de verificar sobre pantallas reales. |
