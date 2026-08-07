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

## Estado actual: la definición de Fase 0 vive en el README principal

| Campo | Valor |
|---|---|
| Estado | Decidido |
| Fecha | 2026-08-07 |
| Revisar en | Inicio de la Fase 1 |

Aplicando la regla anterior al pie de la letra, buena parte del
[`README principal`](../README.md) debería estar aquí: el modelo de datos (5.6),
el catálogo de casos de uso (5.7) y los contratos de la API (5.4.3) son
conocimiento transversal y les correspondería `common/architecture/` y
`common/contracts/`. Hoy no lo están, y es deliberado.

**Por qué se aplaza.** Durante la Fase 0 el README ha funcionado como documento
único de definición: se lee de principio a fin, cada sección da contexto a la
siguiente y las decisiones se han tomado leyéndolo entero. Partirlo ahora, justo
antes de escribir la primera línea de código, añadiría indirección sin resolver
ningún problema real — nadie está buscando el modelo de datos y fallando al
encontrarlo.

**Cuándo se hace.** Al iniciar la **Fase 1**, cuando aparezca documentación
propia de backend y frontend que necesite un sitio y empiece a competir con el
README por ser la fuente de verdad. Ese es el momento en que el coste de no
repartir supera al de repartir: dos documentos describiendo lo mismo es
exactamente el problema que esta estructura existe para evitar.

**Qué se moverá entonces,** dejando en el README un resumen y el enlace:

| Sección del README | Destino previsto |
|---|---|
| 5.6 Modelo de datos | `common/architecture/` |
| 5.7 Casos de uso del core | `common/architecture/` o `common/product/use-cases/` |
| 5.4.3 Contratos JSON | `common/contracts/` |
| 4.1.x Definición del core | `common/product/` (visión, glosario, capacidades) |

Mientras tanto, **el README principal es la fuente vigente** para todo lo
anterior, y los índices de este directorio enlazan a él en lugar de duplicar su
contenido.

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
