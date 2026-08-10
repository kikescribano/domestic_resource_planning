# Accesibilidad

| Campo | Valor |
|---|---|
| Estado | Borrador |
| Responsable | Por decidir |
| Objetivo normativo | WCAG 2.2, nivel AA |
| Última revisión | 2026-08-10 |

El nivel lo fija la
[`ADR-006`](../../common/architecture/decisions/ADR-006-frontend-stack-and-design-system.md),
que lo adopta como compromiso verificable y no como aspiración: la interfaz debe
cumplirlo desde 375 px hasta ultrawide. De ahí viene también la elección de
primitivas headless accesibles en lugar de componentes propios desde cero — el
comportamiento de foco, teclado y anuncio es donde más caro sale equivocarse.

## Contenido previsto
- Semántica, estructura de encabezados y landmarks.
- Navegación completa por teclado y gestión del foco.
- Contraste, zoom, reflow y uso no exclusivo del color.
- Objetivos táctiles, movimiento, temporización y contenido multimedia.
- Mensajes de estado, validación y compatibilidad con tecnologías asistivas.
- Matriz de navegadores, dispositivos y herramientas de prueba.
- Auditorías automáticas y manuales con evidencias y excepciones.

Cada componente del design system debe enlazar sus requisitos y pruebas de
accesibilidad. Las excepciones necesitan alcance, motivo, riesgo y plan de cierre.
