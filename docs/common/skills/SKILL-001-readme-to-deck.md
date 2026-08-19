# SKILL-001: Presentaciones a partir del README

| Campo | Valor |
|---|---|
| Estado | Vigente |
| Versión | 1.1.0 |
| Responsable | Por decidir |
| Ámbito | common |
| Implementación | Un generador por variante: [`../marketing/assets/build-drp-resumen.js`](../marketing/assets/build-drp-resumen.js) (resumen) y [`../marketing/assets/build-drp-comercial.js`](../marketing/assets/build-drp-comercial.js) (comercial); [`assets/preview-pptx.py`](assets/preview-pptx.py) para la verificación |
| Última revisión | 2026-08-19 |

## Propósito

Producir una presentación `.pptx` a partir de un documento del repositorio
—hoy el [`README.md` principal](../../../README.md)— **generada desde un
script**, no montada a mano, y verificada antes de entregarla.

Que el origen sea código es lo que hace repetible el resultado: cuando el
documento fuente cambia, la presentación se regenera en lugar de reeditarse
diapositiva a diapositiva, y el diff de lo que cambió es legible.

## Dos variantes, un solo procedimiento

**El procedimiento de más abajo no cambia entre ellas** —leer la fuente entera,
escribir el generador, validar el fichero, QA de contenido, QA visual, corregir
en el generador y repetir—, y por eso son una skill y no dos. Lo que cambia es
qué se selecciona, cómo se dice y qué obligaciones arrastra la pieza:

| | **Resumen** | **Comercial** |
|---|---|---|
| Destinatario | Quien trabaja en el proyecto o va a revisarlo | Quien no lo conoce y no lee el repositorio: inversión, colaboradores, hogares piloto |
| Selección | El documento entero, sección a sección | Problema, idea, alcance funcional y estado. **De arquitectura, stack y testing no se toma nada**, salvo una diapositiva de confianza en llano |
| Tono | Nombra las cosas por su nombre técnico | **Traduce, no cita.** Nada de vocabulario interno en el texto visible; los términos del dominio se sustituyen por su equivalente doméstico |
| Cifras | Las del repositorio, tal cual | Igual, y **ninguna más**: no se inventan clientes, precios, cuota de mercado, testimonios ni métricas de negocio. Lo que todavía no existe se dice que no existe |
| Créditos | No lleva | **Diapositiva de agradecimiento obligatoria** si se apoya en una plantilla con esa condición de licencia (ver [`marketing/`](../marketing/README.md)) |
| Notas | La sección del README de la que procede | Igual: es lo que permite auditarla después |

La traducción es la parte que más fácil se incumple, porque el vocabulario
interno sale solo. Cuatro equivalencias que ya están decididas:

| En el repositorio | En una presentación comercial |
|---|---|
| Aislamiento multi-tenant con Row-Level Security | «los datos de cada hogar están separados de los de los demás, con dos barreras y no una» |
| Módulos activables por hogar | «enciendes solo lo que tu casa necesita, cuando lo necesita, sin rehacer nada de lo ya cargado» |
| Comprobaciones periódicas y entrega de avisos | «te avisa él: la revisión de la caldera, la ITV, lo que caduca, lo que prestaste y no ha vuelto» |
| Frontend responsive y accesible | «funciona en el móvil, y se puede usar con el teclado y con lector de pantalla» |

## Cuándo usarla

- Hay que presentar el estado del proyecto a alguien que no lee el repositorio.
- Hay que presentar el producto fuera del proyecto, a quien no lo conoce.
- El documento fuente ya existe y está estabilizado; la presentación lo resume,
  no lo redefine.
- Se espera repetir la entrega: cada cierre de fase, cada revisión con terceros.

## Cuándo no usarla

- Para una sola diapositiva suelta o un retoque puntual sobre un `.pptx` ya
  existente: abrirlo y editarlo cuesta menos que montar el generador.
- Para material que **decide** algo (alcance, arquitectura, contratos). Eso vive
  en el README o en una ADR; la presentación va después y siempre por detrás.
- Para partir el README en documentos: eso es la tarea de reparto a `docs/`
  prevista en la Fase 1, no una presentación.

## Entradas y precondiciones

| Entrada | Detalle |
|---|---|
| Documento fuente | El `.md` completo, leído entero antes de decidir el reparto de diapositivas. |
| Node | `npm install pptxgenjs`. |
| Python | `pip install python-pptx Pillow` para la verificación. |
| Fuentes del sistema | Las que se escriben en el `.pptx` deben existir en la máquina que verifica, o las medidas de encaje no valen. |

## Salidas

- El `.pptx`, en [`marketing/presentations/`](../marketing/README.md).
- El script generador **versionado en el repositorio**, en `marketing/assets/`:
  es la fuente editable del binario, y sin él la presentación deja de ser
  regenerable. **Uno por presentación**, no uno con interruptores: dos decks con
  distinto destinatario comparten paleta y helpers, pero no reparto de
  diapositivas.
- Los renders de QA, que son desechables y no se versionan.

## Procedimiento

1. **Leer la fuente entera** y repartirla en diapositivas: un bloque temático por
   diapositiva, ninguna sin elemento visual, y las notas de cada una anotando la
   sección de la que procede. Esa anotación es lo que permite auditar después si
   la presentación sigue estando al día.
2. **Escribir el generador** con `pptxgenjs`, con helpers para las piezas que se
   repiten (tarjeta, cabecera, distintivo, pie). A partir de aquí la presentación
   no se edita a mano: se corrige el script y se vuelve a ejecutar.
3. **Validar el fichero.** El `.pptx` es un ZIP de XML y `pptxgenjs` puede emitir
   XML que PowerPoint rechaza y el resto de herramientas acepta. La skill `pptx`
   de Claude Code trae un `validate.py` que lo comprueba; sin ella, el mínimo es
   abrirlo con `python-pptx` y revisar los gráficos.
4. **QA de contenido:** volcar el texto de todas las diapositivas y contrastarlo
   con la fuente — recuentos ("nueve eventos", "14 comandos"), términos del
   dominio y restos de plantilla.
5. **QA visual:** renderizar y mirar **todas** las diapositivas. Después de
   escribir el generador se ve lo que se esperaba, no lo que salió.
6. **Corregir en el generador** —nunca en el XML ya empaquetado— y repetir 3–5.

## Restricciones y decisiones fijadas

Trampas que ya costaron una iteración y conviene no repetir:

| Punto | Regla |
|---|---|
| Lienzo | Fijar `pres.layout` **antes** de añadir diapositivas: el lienzo por defecto son 10 × 5,625 pulgadas, no 13,3. Lo que cae fuera se escribe igual, simplemente no se ve. |
| Color | Hexadecimal sin `#` y sin alfa (`"FF0000"`); el alfa incrustado corrompe el fichero. La transparencia va en su propia propiedad. |
| Objetos de opciones | `pptxgenjs` los muta al usarlos: no compartir un mismo objeto de sombra u opciones entre dos llamadas. |
| Tipografías | Cambria para titulares, Calibri para texto, Courier New para código: las tres van con Office y miden igual al verificar. |
| Glifos | Calibri **no** trae `✓` ni `✕`: salen como caja. Usar `×`, `+`, `–`, que sí están. |
| Gráficos | Nativos con `addChart`, no imágenes: se siguen editando en PowerPoint. |
| Márgenes | 0,6" de margen; `margin: 0` en las cajas de texto que deban alinearse con una forma, porque traen relleno propio. |
| Giros | Girar una caja amplía su caja envolvente: a 5°, una tarjeta de 3" gana unos 0,13" por arriba y por abajo. Las separaciones se calculan sobre la caja girada, y el texto de dentro se gira **con el mismo valor** o se despega de su tarjeta. |
| Codificación al validar | En Windows, `validate.py` lee el XML con la página de códigos del sistema y falla con `'charmap' codec can't decode` en toda diapositiva con acentos. **No es un defecto del fichero**: se ejecuta con `PYTHONUTF8=1` y pasa. |

Sobre el contenido: la presentación es material derivado. Si contradice al
README, manda el README (ver [`marketing/`](../marketing/README.md)). No incluye
datos reales de ningún hogar; los ejemplos son inventados.

**Y una restricción que en la variante comercial es dura, no de estilo:** una
presentación que sale fuera es la que más tienta a redondear. Si una cifra no
está en el repositorio, no aparece; si algo no existe todavía —un despliegue,
un hogar real usando el producto—, se dice que no existe, y las escenas
ilustrativas se marcan como tales en la propia diapositiva. Un deck comercial que
afirma de más no se corrige después: ya se envió.

## Verificación

El bucle de verificación es la parte que de verdad aporta, porque **la primera
versión siempre tiene defectos**: en la generación de `DRP-resumen.pptx` aparecieron
cuatro reales —una nota montada sobre una fila de contenido, otra pisando la
última fila de tarjetas, etiquetas de flecha dentro de las cajas de estado y una
cabecera partida en dos líneas sobre el texto siguiente— más los glifos ausentes
de Calibri. Ninguno se ve leyendo el script.

**Y volvió a pasar con `DRP-comercial.pptx`, cinco veces**, con el generador
escrito ya sobre la experiencia anterior: un distintivo de estado tapando la
última línea de las cinco tarjetas de una diapositiva, dos tarjetas giradas
solapándose, dos líneas de cierre apoyadas sobre el contenido de encima, una
tarjeta comiéndose el pie de una figura, y un distintivo con **1,18:1** de
contraste —color oscuro sobre tarjeta oscura— que sencillamente no se veía. El
QA de contenido añadió el sexto y el peor, porque este sí habría llegado al
lector: **un recuento equivocado**, «dos de tres» fases cerradas donde son
**tres de cuatro**, porque la Fase 0 también está cerrada.

0. **Repasar contra el README, el día que se genera, los cuatro datos que más
   rápido caducan**: la fase en curso, el número de ADR, el de operaciones del
   contrato y cuántos módulos hay construidos. Va antes que todo lo demás porque
   es lo único que ninguna herramienta detecta: el `.pptx` se genera igual de bien
   con los cuatro equivocados, y así estuvo nueve días.
1. Validación del fichero (paso 3 del procedimiento).
2. Volcado de texto (paso 4). En la variante comercial, el volcado se rastrea
   además en busca de vocabulario interno, con límites de palabra: sin ellos
   «taladro» casa con `ADR` y el informe se llena de falsos positivos.
3. [`assets/preview-pptx.py`](assets/preview-pptx.py): dibuja cada diapositiva
   midiendo el texto con las fuentes reales y lista lo que no cabe en su caja.
   Sustituye a LibreOffice cuando no está instalado, y además es más fiel en las
   medidas, porque no sustituye tipografías.

> **Detalle que hace perder tiempo:** en `python-pptx`, `paragraph.line_spacing`
> devuelve un `Length` en EMU cuando el interlineado se fijó en puntos. Sin
> convertirlo con `.pt`, el cálculo de alto de línea se dispara y el informe de
> desbordes sale con cifras absurdas.

**Lo que el previsualizador no dibuja**, y que por tanto hay que comprobar de otra
forma: las formas que no son rectángulos —un triángulo, una flecha— salen como su
caja envolvente; el giro no se aplica; y un gráfico sale como un hueco rotulado.
Nada de eso es un defecto del `.pptx`. Se comprueba leyendo el XML empaquetado
—que el `prstGeom` sea el que se pidió, que el gráfico exista como
`graphicFrame`— y, en el caso del giro, calculando a mano las separaciones en vez
de mirarlas.

Un desborde de pocos píxeles hacia una zona vacía es tolerable; uno que invade
otro elemento, no.

## Ejemplo

```bash
node docs/common/marketing/assets/build-drp-resumen.js docs/common/marketing/presentations/DRP-resumen.pptx
```

```bash
node docs/common/marketing/assets/build-drp-comercial.js docs/common/marketing/presentations/DRP-comercial.pptx
```

```bash
python docs/common/skills/assets/preview-pptx.py docs/common/marketing/presentations/DRP-comercial.pptx qa/
```

## Historial

| Versión | Fecha | Cambio |
|---|---|---|
| 1.1.0 | 2026-08-19 | **Variante comercial**, extraída de la generación de `DRP-comercial.pptx`. El procedimiento no cambia y por eso no nace una skill nueva; lo que se añade es la selección de contenido, el tono con sus equivalencias de traducción, la restricción de honestidad y la obligación de créditos cuando la dirección visual viene de una plantilla con esa condición. Se suman tres trampas medidas: la caja envolvente de una forma girada, la codificación con la que hay que invocar `validate.py` en Windows, y el repaso de los cuatro datos que caducan como **paso cero** de la verificación. |
| 1.0.0 | 2026-08-08 | Definición inicial, extraída de la generación de `DRP-resumen.pptx`. |
