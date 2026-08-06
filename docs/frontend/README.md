# Frontend

Este espacio contiene la documentación exclusiva de la aplicación web responsive
de DRP. El comportamiento compartido con el backend se define en
[`../common/contracts/`](../common/contracts/README.md).

## Índice

- [`architecture/`](architecture/README.md): capas, dependencias, navegación y estado.
- [`product-design/`](product-design/README.md): experiencia, flujos y look and feel.
- [`design-system/`](design-system/README.md): tokens, componentes y patrones visuales.
- [`accessibility/`](accessibility/README.md): objetivos, criterios y verificaciones.
- [`api-integration/`](api-integration/README.md): consumo de la API y gestión de errores.
- [`quality/`](quality/README.md): pruebas, análisis y matrices de dispositivos.
- [`skills/`](skills/README.md): capacidades reutilizables específicas de frontend.

## Qué no pertenece aquí

- Reglas de negocio que debe garantizar el backend o el dominio compartido.
- Esquemas REST duplicados desde `common/contracts`.
- Detalles de persistencia PostgreSQL o del event bus interno.
- Decisiones visuales sin estado, contexto o criterio de validación.
