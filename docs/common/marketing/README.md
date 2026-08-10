# Marketing

| Campo | Valor |
|---|---|
| Estado | Borrador |
| Responsable | Por decidir |
| Ámbito | Toda la solución |
| Última revisión | 2026-08-08 |
| Procedimiento | [SKILL-001 · Presentación de resumen a partir del README](../skills/SKILL-001-readme-to-deck.md) |

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
| [`DRP-resumen.pptx`](DRP-resumen.pptx) | Presentación de 19 diapositivas que resume el README principal: objetivo, analogía ERP → DRP, core mínimo, modelo de dominio, módulos futuros, arquitectura, event bus, aislamiento multi-tenant, casos de uso, stack, testing y roadmap. Cada diapositiva lleva en sus notas la sección del README de la que procede. Refleja el estado del README a 2026-08-10 (Fase 0 completada). |
| [`assets/build-drp-resumen.js`](assets/build-drp-resumen.js) | Generador de la presentación anterior: es su fuente editable. La presentación **no se retoca a mano**, se regenera ejecutando este script. El procedimiento y su verificación están en [SKILL-001](../skills/SKILL-001-readme-to-deck.md). |
| [`brand-guidelines-template.docx`](brand-guidelines-template.docx) | Plantilla en blanco de manual de marca (v1.0), en siete secciones: identidad, logo y sus variantes, paleta con HEX/RGB/CMYK, tipografía y jerarquía, iconografía, tono de voz y design tokens. Todo lo que aparece entre corchetes es un hueco por rellenar. |

La plantilla es la única pieza de aquí que no describe a DRP: es un **formulario**,
no material derivado, así que no procede de ninguna versión del README ni se queda
desfasada cuando este cambia. Y se rellena a mano en Word, de modo que el propio
`.docx` es su fuente editable y no necesita un generador al lado. Los assets que
salgan de rellenarla —logo, paleta, tipografías— van a `brand/`, y esos sí quedan
sujetos a las convenciones de más abajo.

## Contenido previsto

| Pieza | Contenido |
|---|---|
| `messaging.md` | Slogan, propuesta de valor, descripción corta y larga, y tono con el que se habla del producto. |
| `brand/` | Logo y sus variantes (color, monocromo, versión reducida), paleta, tipografías y usos prohibidos: los huecos que define la plantilla, ya resueltos y con los archivos al lado. |
| `presentations/` | Presentaciones adicionales cuando haya más de una: comercial, técnica, demo. Mientras solo exista la de resumen, se queda en la raíz de esta carpeta. |
| `screenshots/` | Capturas del producto para material divulgativo, con la versión a la que corresponden. |

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
