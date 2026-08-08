# Documentación común

`common` contiene las fuentes que condicionan a DRP como solución completa. No
debe incluir detalles que puedan evolucionar de forma independiente dentro de un
solo componente.

## Índice

- [`product/`](product/README.md): visión, alcance, lenguaje y evolución del producto.
- [`architecture/`](architecture/README.md): vistas de sistema y decisiones transversales.
- [`contracts/`](contracts/README.md): contratos compartidos entre componentes.
- [`standards/`](standards/README.md): convenciones aplicables a toda la solución.
- [`skills/`](skills/README.md): catálogo y documentación de capacidades reutilizables.
- [`marketing/`](marketing/README.md): material con el que se presenta el producto hacia fuera.
- [`templates/`](templates/document-template.md): punto de partida para documentos nuevos.

Los detalles internos deben permanecer en [`../backend/`](../backend/README.md) o
[`../frontend/`](../frontend/README.md).
