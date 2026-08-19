---
name: marketing-deck
description: Genera y regenera presentaciones de DRP con el look and feel de la plantilla «Pitch Deck Minitheme» de Slidesgo (cuenta gratuita), partiendo de la plantilla real en lugar de imitarla. Úsala siempre que haya que crear, rehacer o poner al día un deck del proyecto —comercial, técnico, de demo o de cierre de fase—, cuando alguien pida «una presentación de DRP», «un pitch», «pasa esto a diapositivas», «actualiza el deck» o «hazme un pptx con la plantilla de Slidesgo», y también cuando toque comprobar que una presentación ya generada sigue siendo cierta y sigue cumpliendo la atribución que la licencia exige.
---

# Presentaciones de DRP con la plantilla de Slidesgo

Un deck de DRP **no se dibuja: se parte de la plantilla**. La referencia vive en
[`docs/common/marketing/references/pitch-deck-minitheme-slidesgo.pptx`](../../../docs/common/marketing/references/pitch-deck-minitheme-slidesgo.pptx)
y una presentación nuestra es una **selección de sus diapositivas**, reordenadas
y con el texto sustituido.

Esa decisión es lo que conserva el look and feel, y no es una comodidad: el
negro con verde `#9CFC34`, los círculos a mano alzada, los bordes de papel
rasgado, las ilustraciones vectoriales y **las tipografías incrustadas dentro del
fichero** —Montserrat, Roboto, Roboto Black y Bebas Neue— viajan con la
diapositiva. Reproducir todo eso desde `pptxgenjs` o desde `python-pptx` en
blanco cuesta un orden de magnitud más y sale peor: se pierden las
ilustraciones, se pierde la textura y **se pierden las fuentes**, que es lo que
hace que la presentación se vea igual en una máquina donde nadie ha instalado
nada.

## Lo que la licencia obliga

La plantilla se descargó con **cuenta gratuita**. Esa licencia permite
modificarla y usarla en proyectos personales y comerciales, y a cambio **exige
conservar la diapositiva de agradecimiento** —la 21 de la plantilla, la del
«THANKS»—, que es donde va la atribución. Prohíbe sublicenciarla, venderla,
alquilarla, distribuirla o meterla en un catálogo descargable.

Dos detalles que conviene saber antes de tocarla:

- **Los créditos no están en la diapositiva, están en su patrón** (`BLANK_1`).
  Dicen que la plantilla es de Slidesgo, con iconos de Flaticon e infografías e
  imágenes de Freepik. Llegan solos con la diapositiva y **no se editan**.
- El rótulo pequeño de abajo (`id=731`) es una nota para quien edita, en inglés.
  Ese sí se traduce, y debe seguir diciendo por qué la diapositiva está ahí.

`Deck.save()` **falla** si la presentación no incluye esa diapositiva, y
`qa-deck.py` vuelve a comprobarlo sobre el fichero ya escrito. La regla está en
el código y no en la buena memoria de nadie.

## Las herramientas

Todas viven en [`scripts/`](scripts/) y necesitan `pip install python-pptx
Pillow`; el rasterizado además pide `pypdfium2` y LibreOffice.

| Herramienta | Para qué |
|---|---|
| [`inspect-template.py`](scripts/inspect-template.py) | Qué hay en cada diapositiva de la plantilla y **con qué `id` se rellena**. Sin resumen, `python inspect-template.py 9 21` da el detalle. |
| [`slidesgo_deck.py`](scripts/slidesgo_deck.py) | La biblioteca: `Deck.use(n)` elige una diapositiva, `.text()`, `.table()`, `.drop()` y `.notes()` la rellenan, `.save()` la escribe. |
| [`qa-deck.py`](scripts/qa-deck.py) | Verificación: atribución, relleno olvidado, presupuesto de texto y encaje. Sale con código 1 si hay algo que impide entregar. |
| [`render-deck.py`](scripts/render-deck.py) | Un PNG por diapositiva, y hojas de contactos con `--hojas`, para **mirarlas**. |

Los `id` de forma son **únicos en toda la plantilla**, no solo dentro de su
diapositiva: `id=402` es el título de las tarjetas y no hay otro 402 en ninguna
parte. Por eso el generador se escribe con números, y por eso conviene dejar al
lado el texto que ese hueco traía.

## El catálogo de composiciones

De las 42 diapositivas de la plantilla, **21 son el deck y las demás son
instrucciones, iconos y recursos de Slidesgo**. Estas son las que se usan:

| Nº | Sirve para | Huecos |
|---|---|---|
| 1 | Portada | Rótulo en tres capas (161, 162, 165), subtítulo (163), marca decorativa (166) |
| 2 | Texto denso: alcance, resumen, qué es | Título (171) y un cuerpo con lista numerada (172) |
| 3 | Índice de cuatro secciones | Título (181), cuatro pares etiqueta/descripción (182–189) |
| 4 | Separador de sección | Número (204), título (206), frase (205) |
| 5 | Lista corta con ilustración | Título (213) y seis párrafos (214) |
| 6 | Dos columnas enfrentadas | Título (268), etiquetas (266, 267), textos (265, 270) |
| 7 | Tres filas con ilustración de lista | Título (286), etiquetas (282, 280, 281), textos (285, 283, 284) |
| 8 | Cuatro cuadrantes con icono | Título (352) y cuatro pares (348/349, 350/351, 353/354, 355/356) |
| 9 | Seis tarjetas | Título (402) y seis pares: 397/404, 398/405, 409/407, 399/403, 400/406, 401/408 |
| 10 | Cuatro cifras con pie | 425/426, 420/419, 421/424, 423/422 — y **`drop(415)`**, que es un duplicado que la plantilla dejó debajo |
| 11 | Una cifra grande | Cifra (433) y pie (432) |
| 12 | Una frase a pantalla completa | El mismo texto en las tres capas del rótulo (438, 439, 440) |
| 17 | Tabla de 4×4 | Título (629) y tabla (630), con fila de cabecera |
| 19 | Cuatro llamadas alrededor de un diagrama | Título (677), subtítulo (692), textos (686–689) |
| 20 | Línea de tiempo | Título (704), cuatro hitos (706, 707, 709, 711), final (714), tres etiquetas (703, 701, 702) y sus pies (716, 717, 718) |
| 21 | **Agradecimiento — obligatoria** | Rótulo en tres capas (726, 727, 729), contacto (730), nota de atribución (731) |

**Repetir una composición está permitido**: la segunda vez que se llama a
`use(n)` se trabaja sobre un duplicado. Así es como los dos decks de DRP tienen
cuatro separadores de sección con una sola composición.

Las que **no** se usan, y por qué: la 13 y la 14 traen fotos corporativas de
oficina de **3 MB cada una** —fuera de tono para un producto doméstico, y el
peso se queda en el repositorio para siempre—; la 16 y la 18 llevan un gráfico y
una pantalla de móvil que **son imágenes**, no datos, así que enseñarlas sería
enseñar algo falso; la 15 es un mapamundi; y de la 22 en adelante es material de
Slidesgo sobre la propia plantilla.

## El procedimiento

1. **Leer la fuente entera** —el README y lo que cuelgue de `docs/`— y repartirla
   en diapositivas: un bloque temático por diapositiva. Los datos se comprueban
   contra el repositorio, no se recuerdan (ver más abajo).
2. **Elegir composición para cada bloque** en la tabla de arriba, y sacar los
   `id` con `inspect-template.py`.
3. **Escribir el generador** en
   [`docs/common/marketing/assets/`](../../../docs/common/marketing/assets/),
   uno por presentación y **con el sufijo `-minitheme` en el nombre**: en esa
   carpeta conviven los generadores de [SKILL-001](../../../docs/common/skills/SKILL-001-readme-to-deck.md),
   que toman de esta plantilla decisiones de diseño y no diapositivas, y sin el
   sufijo acaban un `build-drp-comercial.js` y un `build-drp-comercial.py`
   distintos uno al lado del otro. A partir de ahí **el .pptx no se edita a mano**: se
   corrige el generador y se vuelve a ejecutar.
4. **Anotar cada diapositiva** con `.notes()`, diciendo de qué sección sale. Es
   lo que permite auditar después si sigue siendo cierta.
5. **Verificar** con `qa-deck.py` hasta que salga limpio.
6. **Mirar todas las diapositivas** con `render-deck.py --hojas`. Después de
   escribir un generador se ve lo que se esperaba, no lo que salió.
7. **Corregir en el generador** —nunca en el XML— y repetir 5 y 6.

## Trampas ya medidas

Ninguna de estas da error. Todas se descubrieron mirando el render.

| Trampa | Qué pasa | Qué hacer |
|---|---|---|
| **Reducción de cuerpo heredada** | La plantilla deja escrito en `normAutofit` cuánto encogió la letra para que cupiera *su* texto. No se recalcula: aparecen dos tarjetas iguales con dos tamaños de letra distintos. | Ya resuelto: `text()` borra `fontScale` y `lnSpcReduction`. |
| **Enlaces disfrazados** | Algunos párrafos del relleno son enlaces, y el verde, la negrita y el subrayado viven en el run. Reutilizarlo deja una línea resaltada en mitad de una lista. | Ya resuelto: `text()` limpia el formato del run cuando encuentra `hlinkClick`. |
| **El castellano ocupa más** | La plantilla está compuesta en inglés. Una descripción de tarjeta pasa de dos líneas a tres y se sale de la tarjeta sin avisar. | `qa-deck.py` compara con el relleno original y mide el encaje. Se corrige acortando, no agrandando la caja. |
| **Etiquetas de once caracteres** | En las tarjetas de la 9 y en la línea de tiempo de la 20 el texto se parte o se recorta a partir de nueve o diez caracteres. | Etiquetas de **nueve caracteres o menos**; lo preciso va en la descripción. |
| **Formas duplicadas** | La diapositiva 10 tiene dos textos superpuestos (415 y 419). Se rellena el de arriba y el de abajo se queda hablando de Venus. | `drop(415)`. Y en general, el aviso de relleno olvidado de `qa-deck.py`. |
| **Las tipografías no se pueden medir** | Van incrustadas como **EOT comprimido con MicroType Express**: ni Pillow ni LibreOffice las abren. | `qa-deck.py` mide con una sustituta y **se calibra contra el relleno original**, medido con la misma fuente equivocada. |
| **El render no es PowerPoint** | LibreOffice sustituye las tipografías, así que los cortes de línea no son los definitivos. | Sirve para composición, no para medir al píxel. Lo que mide es `qa-deck.py`. |

## Lo que caduca en silencio

Una presentación **no avisa cuando se queda atrás**: sigue generándose igual de
bien con datos que ya no son ciertos. Ya pasó en este repositorio, con un deck
que nueve días después del cierre de una fase seguía anunciando la anterior.

Antes de regenerar, se repasan siempre estos cinco, y se comprueban en el
repositorio:

| Dato | Dónde se comprueba |
|---|---|
| Fase en curso y qué está cerrado | Sección 8 del [`README`](../../../README.md) |
| Operaciones del contrato | `grep -c "operationId:" openapi.yaml` |
| Tablas del modelo | `CREATE TABLE` en `backend/src/main/resources/db/migration/` |
| Número de ADR | [`docs/common/architecture/decisions/`](../../../docs/common/architecture/decisions/README.md) |
| Módulos construidos, por diseñar y su prioridad | Sección 4.2 del README |

Y la regla de fondo del material de marketing: **es derivado**. Si una
presentación y el README se contradicen, manda el README y la presentación se
corrige.

## Dónde vive cada cosa

| Pieza | Sitio |
|---|---|
| La plantilla de referencia | `docs/common/marketing/references/` — no se rellena ni se entrega |
| Los generadores (fuente editable) | [`docs/common/marketing/assets/`](../../../docs/common/marketing/assets/) |
| Las presentaciones | [`docs/common/marketing/presentations/`](../../../docs/common/marketing/presentations/) |
| Los renders de QA | Fuera del repositorio: son desechables |

```bash
python docs/common/marketing/assets/build-drp-comercial-minitheme.py
```

```bash
python .claude/skills/marketing-deck/scripts/qa-deck.py docs/common/marketing/presentations/DRP-comercial-minitheme.pptx
```
