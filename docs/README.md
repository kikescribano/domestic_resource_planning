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

Cuando un tema cruza componentes, `common/` conserva la decisión o el contrato y
cada componente documenta únicamente su implementación, enlazando de vuelta a la
fuente común.

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
