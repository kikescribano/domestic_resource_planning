# Arquitectura del frontend

| Campo | Valor |
|---|---|
| Estado | Borrador |
| Responsable | Por decidir |
| Ámbito | Frontend web TypeScript |
| Framework | React sobre Vite — confirmado |
| Última revisión | 2026-08-10 |

Este directorio documentará la aplicación de Clean Architecture en el cliente
web y las decisiones que permiten mantener la experiencia separada de frameworks
y mecanismos de transporte.

## Decisiones vigentes

La [`ADR-006`](../../common/architecture/decisions/ADR-006-frontend-stack-and-design-system.md)
cierra la elección que la ADR-001 había dejado como recomendación:

| Pieza | Elección |
|---|---|
| Construcción | Vite, aplicación de página única |
| Enrutado | React Router |
| Estado de servidor | TanStack Query |
| Estilos y tokens | Tailwind CSS |
| Primitivas accesibles | Radix |
| Pruebas | Vitest + Testing Library; Playwright para el recorrido vertical |

No hay renderizado en servidor: la aplicación vive entera detrás del login. Los
tipos y el cliente HTTP **se generan** desde
[`openapi.yaml`](../../../openapi.yaml), que la
[`ADR-007`](../../common/architecture/decisions/ADR-007-openapi-contract-as-source-of-truth.md)
declara fuente de verdad; no se escribe a mano la forma de ninguna petición ni
respuesta.

## Contenido previsto

- Capas de dominio, aplicación, adaptadores e infraestructura del frontend.
- Organización por capacidades y reglas de dependencia.
- Navegación, composición de pantallas y protección de rutas.
- Estado local, estado remoto, caché y sincronización.
- Adaptación de contratos REST a modelos de aplicación.
- Tratamiento transversal de carga, errores y sesiones.
- Rendimiento, división de código y estrategia de renderizado.
- Decisiones sobre React y las librerías asociadas.

Los detalles visuales pertenecen a
[`product-design`](../product-design/README.md) y
[`design-system`](../design-system/README.md).
