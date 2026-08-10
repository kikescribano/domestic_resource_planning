# Documentación de DRP

Este directorio es la fuente de documentación mantenible de DRP. Separa el
conocimiento transversal de los detalles propios del backend y del frontend, y
ofrece índices y plantillas para que la documentación crezca con el producto.

## Estructura

```text
docs/
├── common/                   Producto y decisiones que afectan a toda la solución
│   ├── product/              Visión, alcance, glosario, roadmap y casos de uso
│   ├── architecture/         Contexto, contenedores, flujos y ADRs
│   ├── contracts/            Contratos compartidos entre frontend y backend
│   ├── standards/            Convenciones transversales
│   ├── skills/               Catálogo y definición de skills compartidas
│   ├── marketing/            Presentaciones, identidad visual y mensajes
│   └── templates/            Plantillas documentales
├── backend/                  Documentación exclusiva del backend
│   ├── architecture/         Estructura interna y dependencias
│   ├── modules/              Límites y diseño de módulos
│   ├── api/                  Implementación de la entrada REST
│   ├── data/                 Persistencia, migraciones y propiedad de datos
│   ├── security/             Controles y modelo de amenazas del backend
│   ├── quality/              Estrategia y evidencias de calidad
│   ├── operations/           Ejecución, configuración y observabilidad
│   └── skills/               Skills específicas de backend
└── frontend/                 Documentación exclusiva del frontend
    ├── architecture/         Capas, estado, navegación y dependencias
    ├── product-design/       Experiencia, flujos y look and feel
    ├── design-system/        Tokens, componentes y patrones visuales
    ├── accessibility/        Criterios y verificaciones de accesibilidad
    ├── api-integration/      Consumo de contratos y gestión de errores
    ├── quality/              Pruebas y calidad del frontend
    └── skills/               Skills específicas de frontend
```

## Regla de ubicación

| Si la información... | Debe vivir en... |
|---|---|
| Define producto, vocabulario o una decisión que afecta a toda la solución | `common/` |
| Define un contrato observable por frontend y backend | `common/contracts/` |
| Explica cómo el backend implementa una responsabilidad | `backend/` |
| Explica experiencia, interfaz o implementación web | `frontend/` |
| Describe una capacidad reutilizable en toda la solución | `common/skills/` |
| Describe una capacidad exclusiva de un componente | `<componente>/skills/` |
| Presenta el producto hacia fuera: presentaciones, marca, mensajes | `common/marketing/` |

Cuando un tema cruza componentes, `common/` conserva la decisión o el contrato y
cada componente documenta únicamente su implementación, enlazando de vuelta a la
fuente común.

## Estado actual: el reparto ya está hecho

| Campo | Valor |
|---|---|
| Estado | Completado |
| Fecha | 2026-08-10 |
| Decidido el | 2026-08-07, y ejecutado al arrancar la Fase 1 |

Durante toda la Fase 0 la definición del core vivió en el
[`README principal`](../README.md), en contra de la regla de ubicación de más
arriba y a propósito: el README funcionaba como documento único, se leía de
principio a fin y las decisiones se tomaban leyéndolo entero. Partirlo antes de
escribir la primera línea de código habría añadido indirección sin resolver
ningún problema real — nadie estaba buscando el modelo de datos y fallando al
encontrarlo.

Al arrancar la **Fase 1** aparece documentación propia de backend y frontend que
compite con él por ser la fuente de verdad, que era la condición que se había
fijado para repartir. Hecho el reparto, el README pasó de 1821 líneas a poco más
de 600 y conserva la visión de conjunto.

**Dónde vive ahora cada sección:**

| Sección del README | Destino |
|---|---|
| 4.1.1 – 4.1.3 Assets, artículos y ubicaciones | [`common/product/core-model.md`](common/product/core-model.md) |
| 4.1.4 Usuarios y roles | [`common/product/users-and-access.md`](common/product/users-and-access.md) |
| 4.1.5 Préstamos | [`common/product/loans.md`](common/product/loans.md) |
| 4.1.7 Decisiones de diseño | [`common/product/decisions.md`](common/product/decisions.md) |
| 5.4.3 Contratos JSON | [`common/contracts/json-examples.md`](common/contracts/json-examples.md) |
| 5.6 Modelo de datos | [`common/architecture/data-model.md`](common/architecture/data-model.md) |
| 5.7 Casos de uso del core | [`common/product/use-cases/`](common/product/use-cases/README.md) |
| 5.8 Almacenamiento de ficheros | [`backend/architecture/`](backend/architecture/file-storage.md), con los controles OWASP en [`backend/security/`](backend/security/file-upload-controls.md) y el dimensionado y las copias en [`backend/operations/`](backend/operations/storage-sizing-and-backups.md) |

De los dos destinos que se habían previsto para los casos de uso —
`common/architecture/` o `common/product/use-cases/`— se eligió el segundo: un
catálogo de comandos y queries describe **qué hace** el producto, no cómo está
construido.

> **Los números de sección se conservan.** Hay más de cien referencias cruzadas
> del tipo «ver 4.1.1» repartidas por el repositorio, así que 4.1.1 sigue
> llamándose 4.1.1 aunque su cuerpo viva ahora aquí. Renumerarlas las habría
> roto todas de golpe, y sin que ninguna herramienta lo detectara.

**La regla que esto hace fácil de incumplir:** el detalle ya no se escribe en el
README. Un cambio en el modelo de datos, en los casos de uso o en la definición
del core va a su documento; el README solo se toca si cambia el resumen.

## Convenciones

- Un `README.md` actúa como índice de cada directorio.
- Los nombres de archivo usan minúsculas y `kebab-case.md`.
- Los enlaces internos son relativos para funcionar en cualquier clon.
- Un documento sustantivo declara estado, responsable, ámbito y última revisión.
- Las decisiones duraderas se registran como ADR; no se reescribe su historia.
- Los valores no decididos se marcan como `Por decidir`, sin inventar una solución.
- La documentación cambia en el mismo incremento que el comportamiento al que se refiere.
- Diagramas y recursos se guardan junto al documento que los usa, en una carpeta
  `assets/`, con una fuente editable cuando corresponda.

## Estados documentales

| Estado | Significado |
|---|---|
| Borrador | Propuesta todavía incompleta o no validada. |
| En revisión | Contenido listo para recibir validación. |
| Vigente | Fuente aceptada para implementar o mantener el producto. |
| Obsoleto | Conservado como contexto histórico y sustituido por otro documento. |

## Cómo ampliar la documentación

1. Identifica el ámbito con la tabla anterior.
2. Parte de [`common/templates/document-template.md`](common/templates/document-template.md)
   o de una plantilla especializada.
3. Añade el documento al `README.md` de su directorio.
4. Enlaza las decisiones, contratos y documentos relacionados sin copiar su contenido.
5. Revisa metadatos, enlaces y estado al entregar el cambio.
