# Marketing

| Campo | Valor |
|---|---|
| Estado | Borrador |
| Responsable | Por decidir |
| Ámbito | Toda la solución |
| Última revisión | 2026-08-21 |
| Procedimiento | [SKILL-001 · Presentaciones a partir del README](../skills/SKILL-001-readme-to-deck.md) y [SKILL-002 · Presentaciones sobre la plantilla de Slidesgo](../../../.claude/skills/marketing-deck/SKILL.md) |

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
| [`presentations/DRP-resumen.pptx`](presentations/DRP-resumen.pptx) | Presentación de 20 diapositivas que resume el README principal: objetivo, analogía ERP → DRP, core mínimo, modelo de dominio, módulos, arquitectura, event bus, aislamiento multi-tenant, casos de uso, stack, testing, **capturas reales del cliente en los dos modos** y roadmap. Cada diapositiva lleva en sus notas la sección del README de la que procede. **Refleja el estado del README a 2026-08-21**: Fases 1 y 2, cierre de huecos y despliegue cerrados — DRP en producción (ADR-016), con el core completo y los cuatro módulos de prioridad alta sobre la activación por hogar. |
| [`assets/build-drp-resumen.js`](assets/build-drp-resumen.js) | Generador de la presentación anterior: es su fuente editable. La presentación **no se retoca a mano**, se regenera ejecutando este script. El procedimiento y su verificación están en [SKILL-001](../skills/SKILL-001-readme-to-deck.md). |
| [`presentations/DRP-comercial.pptx`](presentations/DRP-comercial.pptx) | Presentación comercial de 17 diapositivas, dirigida a quien **no conoce el proyecto, no lee el repositorio y no quiere tecnicismos**: inversión, colaboradores y primeros hogares piloto. Recorre el arco de un pitch —problema, idea, cómo funciona, qué resuelve hoy, un día con DRP, los módulos en tres oleadas, avisos, aislamiento contado en llano, **capturas reales de la aplicación**, dónde está el proyecto, qué falta y qué se busca—, y de las secciones de arquitectura, stack y testing **no toma nada** salvo esa única diapositiva de confianza. Cada diapositiva lleva en sus notas la sección del README de la que procede. **Refleja el estado del README a 2026-08-21**: Fases 1 y 2, cierre de huecos y despliegue cerrados —**DRP en producción** (ADR-016)— y cuatro de trece módulos construidos, que siguen «En desarrollo» porque **no hay ningún hogar real dentro**, cosa que la presentación declara en vez de esquivar. Su última diapositiva es **la de agradecimiento de [Slidesgo](https://slidesgo.com)**, con mención a Freepik y Flaticon: no es decorativa, la exige la licencia de la plantilla de `references/` de la que se tomó la dirección visual, y **no se retira**. |
| [`assets/build-drp-comercial.js`](assets/build-drp-comercial.js) | Generador de la presentación comercial. Comparte paleta, tipografías y lienzo con el de resumen para que las dos piezas se reconozcan como del mismo producto; lo que no comparte es el reparto de diapositivas, que es justo lo que distingue a un destinatario de otro. Deja **el bloque de contacto de la penúltima diapositiva sin rellenar a propósito**: se completa aquí, en el script, antes de cada envío, porque inventarlo sería exactamente lo que la variante comercial de [SKILL-001](../skills/SKILL-001-readme-to-deck.md) prohíbe. |
| [`presentations/DRP-comercial-minitheme.pptx`](presentations/DRP-comercial-minitheme.pptx) | **La misma intención que la anterior en otro lenguaje visual**: 20 diapositivas para quien no conoce el producto —el problema, la idea, qué trae el core, cómo entra un módulo, los cuatro construidos, el catálogo entero, las cifras, **dos capturas reales en modo oscuro** y el camino—, esta vez **con el aspecto de la plantilla de Slidesgo**, porque no se imita sino que se parte de ella. Refleja el estado del README a 2026-08-21. |
| [`presentations/DRP-tecnico-minitheme.pptx`](presentations/DRP-tecnico-minitheme.pptx) | Presentación de 18 diapositivas sobre **cómo está construido**: capas, paquetes del backend, las cuatro fronteras de ArchUnit, el aislamiento en dos capas, las cifras del contrato, el stack con sus ADR y los tres niveles de la batería de pruebas. Es la única pieza de aquí dirigida a quien sí quiere tecnicismos. Misma plantilla y mismo estado. |
| [`screenshots/`](screenshots/) | **Capturas reales de la aplicación** para material divulgativo: ocho, tomadas el **2026-08-21** sobre la instancia en producción con el hogar de demostración de [`seed-demo-data.sql`](../../../scripts/seed-demo-data.sql) dentro — «Hogar», «Inventario», «Compras», el menú «Más» y el login, en claro y oscuro, escritorio y móvil. Son **la fuente de las imágenes de los decks**: los generadores las referencian por ruta relativa, así que renombrarlas rompe la regeneración. Al cambiar el aspecto del cliente se retoman, se sustituyen aquí y se regeneran los decks que las usan. |
| [`assets/build-drp-comercial-minitheme.py`](assets/build-drp-comercial-minitheme.py) y [`assets/build-drp-tecnico-minitheme.py`](assets/build-drp-tecnico-minitheme.py) | Generadores de las dos anteriores: son su fuente editable. No se retocan los `.pptx`, se corrige el generador y se vuelve a ejecutar. Procedimiento, catálogo de composiciones y verificación en la skill [`marketing-deck`](../../../.claude/skills/marketing-deck/SKILL.md). |
| [`brand-guidelines-template.docx`](brand-guidelines-template.docx) | Plantilla en blanco de manual de marca (v1.0), en siete secciones: identidad, logo y sus variantes, paleta con HEX/RGB/CMYK, tipografía y jerarquía, iconografía, tono de voz y design tokens. Todo lo que aparece entre corchetes es un hueco por rellenar. |
| [`references/pitch-deck-minitheme-slidesgo.pptx`](references/pitch-deck-minitheme-slidesgo.pptx) | Plantilla comercial de [Slidesgo](https://slidesgo.com) —«Pitch Deck Minitheme»—, 42 diapositivas con contenido de relleno en inglés, dos patrones y veintitrés diseños. **Es material de terceros, no una pieza de DRP**: no se rellena en sitio y no describe el producto. Cumple dos papeles distintos, y conviene no confundirlos: es **dirección visual** de la que toman ideas las piezas de SKILL-001, y es **el fichero de partida** del que salen las dos de sufijo `-minitheme`. Ver más abajo: tiene condiciones de licencia. |

**Hay dos presentaciones comerciales, y es a propósito.** Dicen lo mismo y no se
parecen en nada: una lleva el aspecto propio de DRP —el mismo del deck de
resumen, para que las piezas de dentro se reconozcan entre ellas— y la otra, el
de la plantilla de Slidesgo. Se elige por el destinatario y no por el gusto del
día, y **si las dos se contradicen manda el README**, como todo lo de esta
carpeta. Lo que no se hace es dejar que una envejezca mientras se actualiza la
otra: caducan a la vez porque salen del mismo sitio.

> **Este material no avisa cuando se queda atrás**, que es la razón de que
> repasarlo entre en la lista de cierre de cada fase. Al cerrar la Fase 2 el deck
> seguía diciendo «Fase 1 en curso», nueve ADR y cuatro módulos por diseñar, nueve
> días después de que las tres cosas dejaran de ser verdad — y el `.pptx` se
> generaba igual, sin un solo error. **Los cuatro datos que más rápido caducan**,
> y que hay que repasar siempre, son **la fase en curso, el número de ADR, el de
> operaciones del contrato y cuántos módulos hay construidos**. Están anotados
> también en la cabecera de cada generador, que es donde los va a leer quien lo
> toque.
>
> **Ahora son cuatro las piezas que caducan a la vez, y no caducan igual de caro.**
> La de resumen se lee dentro del proyecto, donde alguien la contradice; las
> comerciales se envían, y una vez enviadas ya no se corrigen.

**Dos piezas de aquí no describen a DRP, y no lo hacen por motivos distintos.**

El manual de marca es un **formulario**: no procede de ninguna versión del README
ni se queda desfasado cuando este cambia, y se rellena a mano en Word, de modo que
el propio `.docx` es su fuente editable y no necesita un generador al lado. Los
assets que salgan de rellenarla —logo, paleta, tipografías— van a `brand/`, y esos
sí quedan sujetos a las convenciones de más abajo.

La plantilla de Slidesgo es otra cosa: **material de terceros que entra, no
material nuestro que sale.** De ella salen las presentaciones de DRP, unas veces
como ideas y otras como diapositivas, y por eso vive en `references/` y no en
`assets/` —esa
carpeta significa «la fuente editable de lo nuestro», y confundir las dos acaba en
que alguien busque ahí el generador de un fichero que no genera nada—. Tres cosas
que hay que saber antes de tocarla:

- **Sirve de dos maneras, y no se mezclan.** Para las piezas de
  [SKILL-001](../skills/SKILL-001-readme-to-deck.md) —la de resumen y la
  comercial— es solo **dirección visual** —retícula, jerarquía, uso del color—,
  que se lleva a su generador a mano: **sus diapositivas no se copian**, porque
  el lienzo de la plantilla es de **10 × 5,62 pulgadas** y el de esos decks es de
  **13,3**, y lo que cae fuera se escribe igual, simplemente no se ve (esa skill
  lo tiene documentado en primer lugar). Para las dos de sufijo `-minitheme` es
  el **fichero de partida**: se seleccionan sus diapositivas y se les sustituye
  el texto, conservando su lienzo y con él las ilustraciones, la textura y las
  tipografías incrustadas (ver la skill
  [`marketing-deck`](../../../.claude/skills/marketing-deck/SKILL.md)).
- **Lleva atribución obligatoria, y ya se ha cobrado tres veces.** Se descargó con
  cuenta gratuita, y esa licencia permite modificarla y usarla con fines
  comerciales pero **exige conservar la diapositiva de agradecimiento** en
  cualquier presentación derivada; prohíbe sublicenciarla, venderla o alquilarla.
  Los recursos que trae dentro son de Freepik y Flaticon. Aquí quedó escrito que
  si algún día una presentación de DRP salía fuera, esa diapositiva iría dentro:
  **la 17 de `DRP-comercial.pptx` es esa diapositiva**, y las dos de sufijo
  `-minitheme` la llevan también — estas últimas **no por acordarse**, sino
  porque su generador falla al guardar si no está y el verificador vuelve a
  comprobarlo sobre el fichero escrito. No se retira mientras los decks se
  entreguen; la otra salida es pasar a cuenta premium, que es lo que levanta la
  condición. El deck de resumen no la lleva porque no sale de aquí; el día que
  salga, la lleva.
- **Pesa 13,4 MB**, que la convierte en **el fichero más grande del repositorio con
  diferencia**: casi seis veces el siguiente, que son las dos presentaciones que
  parten de ella. Se anota porque un binario de ese tamaño en la historia de git no se
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

Y dos carpetas que ya existen:

| Carpeta | Contenido |
|---|---|
| `presentations/` | **Las presentaciones entregables**, una por destinatario. Vivió como «contenido previsto» mientras solo había una, porque una carpeta con un único fichero dentro no ordena nada; la condición que declaraba —«cuando haya más de una»— se cumplió al llegar la comercial, y las dos se mudaron aquí a la vez. Su fuente editable **no** vive aquí, sino en `assets/`, que es donde están los generadores. |
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
