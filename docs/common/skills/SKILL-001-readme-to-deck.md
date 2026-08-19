# SKILL-001: Presentación de resumen a partir del README

| Campo | Valor |
|---|---|
| Estado | Vigente |
| Versión | 1.0.0 |
| Responsable | Por decidir |
| Ámbito | common |
| Implementación | [`../marketing/assets/build-drp-resumen.js`](../marketing/assets/build-drp-resumen.js) (generador) y [`assets/preview-pptx.py`](assets/preview-pptx.py) (verificación visual) |
| Última revisión | 2026-08-08 |

## Propósito

Producir una presentación `.pptx` que resuma un documento del repositorio —hoy el
[`README.md` principal](../../../README.md)— **generada desde un script**, no
montada a mano, y verificada antes de entregarla.

Que el origen sea código es lo que hace repetible el resultado: cuando el
documento fuente cambia, la presentación se regenera en lugar de reeditarse
diapositiva a diapositiva, y el diff de lo que cambió es legible.

## Cuándo usarla

- Hay que presentar el estado del proyecto a alguien que no lee el repositorio.
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
- El script generador **versionado en el repositorio**: es la fuente editable del
  binario, y sin él la presentación deja de ser regenerable.
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

Sobre el contenido: la presentación es material derivado. Si contradice al
README, manda el README (ver [`marketing/`](../marketing/README.md)). No incluye
datos reales de ningún hogar; los ejemplos son inventados.

## Verificación

El bucle de verificación es la parte que de verdad aporta, porque **la primera
versión siempre tiene defectos**: en la generación de `DRP-resumen.pptx` aparecieron
cuatro reales —una nota montada sobre una fila de contenido, otra pisando la
última fila de tarjetas, etiquetas de flecha dentro de las cajas de estado y una
cabecera partida en dos líneas sobre el texto siguiente— más los glifos ausentes
de Calibri. Ninguno se ve leyendo el script.

1. Validación del fichero (paso 3 del procedimiento).
2. Volcado de texto (paso 4).
3. [`assets/preview-pptx.py`](assets/preview-pptx.py): dibuja cada diapositiva
   midiendo el texto con las fuentes reales y lista lo que no cabe en su caja.
   Sustituye a LibreOffice cuando no está instalado, y además es más fiel en las
   medidas, porque no sustituye tipografías.

> **Detalle que hace perder tiempo:** en `python-pptx`, `paragraph.line_spacing`
> devuelve un `Length` en EMU cuando el interlineado se fijó en puntos. Sin
> convertirlo con `.pt`, el cálculo de alto de línea se dispara y el informe de
> desbordes sale con cifras absurdas.

Un desborde de pocos píxeles hacia una zona vacía es tolerable; uno que invade
otro elemento, no.

## Ejemplo

```bash
node docs/common/marketing/assets/build-drp-resumen.js docs/common/marketing/presentations/DRP-resumen.pptx
```

```bash
python docs/common/skills/assets/preview-pptx.py docs/common/marketing/presentations/DRP-resumen.pptx qa/
```

## Historial

| Versión | Fecha | Cambio |
|---|---|---|
| 1.0.0 | 2026-08-08 | Definición inicial, extraída de la generación de `DRP-resumen.pptx`. |
