# Backend

Este espacio contiene solo información necesaria para construir, verificar y
operar el backend de DRP. Los contratos observables por el frontend permanecen en
[`../common/contracts/`](../common/contracts/README.md).

## Índice

- [`architecture/`](architecture/README.md): capas, límites y colaboración interna.
- [`modules/`](modules/README.md): catálogo y diseño de módulos funcionales.
- [`api/`](api/README.md): implementación de la entrada REST.
- [`data/`](data/README.md): PostgreSQL, transacciones, migraciones y propiedad de datos.
- [`security/`](security/README.md): controles y riesgos propios del backend.
- [`quality/`](quality/README.md): estrategia de pruebas y puertas de calidad.
- [`operations/`](operations/README.md): ejecución, configuración y observabilidad.
- [`skills/`](skills/README.md): capacidades reutilizables específicas de backend.

## Qué no pertenece aquí

- La visión y el lenguaje de producto.
- Contratos compartidos copiados desde `common`.
- Decisiones visuales o de interacción del frontend.
- Información temporal de una sesión de desarrollo.
