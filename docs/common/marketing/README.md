# Marketing

| Campo | Valor |
|---|---|
| Estado | Borrador |
| Responsable | Por decidir |
| Ámbito | Toda la solución |
| Última revisión | 2026-08-19 |
| Procedimiento | [SKILL-001 · Presentación de resumen a partir del README](../skills/SKILL-001-readme-to-deck.md) y [SKILL-002 · Presentaciones sobre la plantilla de Slidesgo](../../../.claude/skills/marketing-deck/SKILL.md) |

Este espacio reúne el material con el que DRP se presenta hacia fuera:
presentaciones, identidad visual, mensajes y cualquier pieza pensada para
explicar el producto a quien todavía no lo conoce.

Es material **derivado**. Describe lo que DRP es; no lo decide. Ninguna pieza de
esta carpeta establece alcance, arquitectura ni comportamiento: eso vive en el
[`README.md` principal](../../../README.md) y en las
[ADR](../architecture/decisions/README.md). Si una presentación y el README se
contradicen, manda el README y la pieza se corrige.

## Por qué vive en `common/`

Cómo se cuenta DRP no pertenece al backend ni al frontend: es una propiedad de la
solución completa, igual que la visión de producto. Aplicando la
[regla de ubicación](../../README.md#regla-de-ubicación), su sitio es `common/`.

## Índice

| Pieza | Contenido |
|---|---|
| [`DRP-resumen.pptx`](DRP-resumen.pptx) | Presentación de 19 diapositivas que resume el README principal: objetivo, analogía ERP → DRP, core mínimo, modelo de dominio, módulos, arquitectura, event bus, aislamiento multi-tenant, casos de uso, stack, testing y roadmap. Cada diapositiva lleva en sus notas la sección del README de la que procede. **Refleja el estado del README a 2026-08-19**: Fases 1 y 2 cerradas, con el core completo y los cuatro módulos de prioridad alta construidos sobre la activación por hogar. |
| [`assets/build-drp-resumen.js`](assets/build-drp-resumen.js) | Generador de la presentación anterior: es su fuente editable. La presentación **no se retoca a mano**, se regenera ejecutando este script. El procedimiento y su verificación están en [SKILL-001](../skills/SKILL-001-readme-to-deck.md). |
| [`presentations/DRP-comercial.pptx`](presentations/DRP-comercial.pptx) | Presentación de 18 diapositivas para quien no conoce el producto: el problema, la idea, qué trae el core, cómo entra un módulo, los cuatro construidos, el catálogo entero, las cifras y el camino. **Sobre la plantilla de Slidesgo**, con su look and feel intacto. Refleja el estado del README a 2026-08-19. |
| [`presentations/DRP-tecnico.pptx`](presentations/DRP-tecnico.pptx) | Presentación de 18 diapositivas sobre cómo está construido: capas, paquetes del backend, las cuatro fronteras de ArchUnit, el aislamiento en dos capas, las cifras del contrato, el stack con sus ADR y los tres niveles de la batería de pruebas. Misma plantilla y mismo estado. |
| [`assets/build-drp-comercial.py`](assets/build-drp-comercial.py) y [`assets/build-drp-tecnico.py`](assets/build-drp-tecnico.py) | Generadores de las dos anteriores: son su fuente editable. No se retocan los `.pptx`, se corrige el generador y se vuelve a ejecutar. Procedimiento, catálogo de composiciones y verificación en la skill [`marketing-deck`](../../../.claude/skills/marketing-deck/SKILL.md). |
| [`brand-guidelines-template.docx`](brand-guidelines-template.docx) | Plantilla en blanco de manual de marca (v1.0), en siete secciones: identidad, logo y sus variantes, paleta con HEX/RGB/CMYK, tipografía y jerarquía, iconografía, tono de voz y design tokens. Todo lo que aparece entre corchetes es un hueco por rellenar. |
| [`references/pitch-deck-minitheme-slidesgo.pptx`](references/pitch-deck-minitheme-slidesgo.pptx) | Plantilla comercial de [Slidesgo](https://slidesgo.com) —«Pitch Deck Minitheme»—, 42 diapositivas con contenido de relleno en inglés, dos patrones y veintitrés diseños. **Es material de terceros, no una pieza de DRP**: no se rellena en sitio y no describe el producto. Cumple dos papeles distintos, y conviene no confundirlos: es **dirección visual** de la que `DRP-resumen.pptx` toma ideas, y es **el fichero de partida** del que salen las dos presentaciones de `presentations/`. Ver más abajo: tiene condiciones de licencia. |

> **Este material no avisa cuando se queda atrás**, que es la razón de que
> repasarlo entre en la lista de cierre de cada fase. Al cerrar la Fase 2 el deck
> seguía diciendo «Fase 1 en curso», nueve ADR y cuatro módulos por diseñar, nueve
> días después de que las tres cosas dejaran de ser verdad — y el `.pptx` se
> generaba igual, sin un solo error. **Los cuatro datos que más rápido caducan**,
> y que hay que repasar siempre, son **la fase en curso, el número de ADR, el de
> operaciones del contrato y cuántos módulos hay construidos**. Están anotados
> también en la cabecera de cada generador, que es donde los va a leer quien lo toque.

**Dos piezas de aquí no describen a DRP, y no lo hacen por motivos distintos.**

El manual de marca es un **formulario**: no procede de ninguna versión del README
ni se queda desfasado cuando este cambia, y se rellena a mano en Word, de modo que
el propio `.docx` es su fuente editable y no necesita un generador al lado. Los
assets que salgan de rellenarla —logo, paleta, tipografías— van a `brand/`, y esos
sí quedan sujetos a las convenciones de más abajo.

La plantilla de Slidesgo es otra cosa: **material de terceros que entra, no
material nuestro que sale.** Por eso vive en `references/` y no en `assets/` —esa
carpeta significa «la fuente editable de lo nuestro», y confundir las dos acaba en
que alguien busque ahí el generador de un fichero que no genera nada—. Tres cosas
que hay que saber antes de tocarla:

- **Sirve de dos maneras, y no se mezclan.** Para `DRP-resumen.pptx` es solo
  dirección visual —retícula, jerarquía, uso del color—, que se lleva a su
  generador a mano: **sus diapositivas no se copian**, porque el lienzo de la
  plantilla es de **10 × 5,62 pulgadas** y el de ese deck es de **13,3**, y lo que
  cae fuera se escribe igual, simplemente no se ve
  ([SKILL-001](../skills/SKILL-001-readme-to-deck.md) lo tiene documentado en
  primer lugar). Para las dos de `presentations/` es el **fichero de partida**:
  se seleccionan sus diapositivas y se les sustituye el texto, conservando su
  lienzo y con él las ilustraciones, la textura y las tipografías incrustadas
  (ver la skill [`marketing-deck`](../../../.claude/skills/marketing-deck/SKILL.md)).
- **Lleva atribución obligatoria.** Se descargó con cuenta gratuita, y esa licencia
  permite modificarla y usarla con fines comerciales pero **exige conservar la
  diapositiva de agradecimiento** en cualquier presentación derivada; prohíbe
  sublicenciarla, venderla o alquilarla. Los recursos que trae dentro son de
  Freepik y Flaticon. **Las dos presentaciones de `presentations/` la llevan
  dentro**, y no por acordarse: su generador falla al guardar si no está y el
  verificador vuelve a comprobarlo sobre el fichero escrito. La otra salida es
  pasar a cuenta premium, que es lo que levanta la condición.
- **Pesa 13,4 MB**, que la convierte en **el fichero más grande del repositorio con
  diferencia**: casi seis veces el siguiente, que son las presentaciones que salen
  de ella. Se anota porque un binario de ese tamaño en la historia de git no se
  quita después sin reescribirla, así que la siguiente que se añada merece la
  pregunta de si hace falta versionarla o basta con enlazarla. De los 13,4 MB, a
  cada deck derivado le llegan **2,3**: se queda con las diapositivas que usa y
  con las tipografías incrustadas, que son 1,8 MB y son justo lo que no se puede
  reproducir de otra manera.

## Contenido previsto

| Pieza | Contenido |
|---|---|
| `messaging.md` | Slogan, propuesta de valor, descripción corta y larga, y tono con el que se habla del producto. |
| `brand/` | Logo y sus variantes (color, monocromo, versión reducida), paleta, tipografías y usos prohibidos: los huecos que define la plantilla, ya resueltos y con los archivos al lado. |
| `screenshots/` | Capturas del producto para material divulgativo, con la versión a la que corresponden. |

Y dos carpetas que ya existen:

| Carpeta | Contenido |
|---|---|
| `presentations/` | Las presentaciones que **no** son la de resumen: hoy la comercial y la técnica, mañana una demo. La de resumen se queda en la raíz de esta carpeta, donde nació y donde la busca su procedimiento. |
| `references/` | **Material de terceros del que se toman ideas**, no material propio: plantillas, decks ajenos que sirvan de referencia visual, guías de estilo de fuera. Lo que entra aquí no se entrega ni se rellena, y **lleva siempre escrita su licencia** en el índice de arriba — que es la mitad del motivo de que tenga carpeta propia en vez de mezclarse con lo nuestro. |

## Convenciones

- **Cada pieza declara de qué versión de la fuente procede.** Una presentación
  sin fecha no se puede auditar contra el README, que es un documento vivo.
- **Un cambio sustantivo del README no actualiza el material solo.** Al tocar
  alcance, modelo de dominio o arquitectura, hay que revisar si alguna pieza de
  aquí queda desfasada y, si lo queda, regenerarla en el mismo incremento.
- **Los binarios conviven con su fuente editable** cuando la exportación no se
  pueda volver a generar desde el repositorio (por ejemplo, un logo: se guarda el
  SVG junto al PNG, no solo el PNG).
- Nombres de archivo en minúsculas y `kebab-case`, salvo que la pieza lleve el
  nombre del producto.
- El material de esta carpeta no es contractual: los contratos observables viven
  en [`../contracts/`](../contracts/README.md).
