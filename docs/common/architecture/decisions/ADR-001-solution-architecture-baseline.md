# ADR-001: Base arquitectónica de DRP

- Estado: accepted
- Fecha: 2026-08-06
- Responsables: Equipo DRP
- Ámbito: common
- Sustituye: Ninguna

## Contexto

DRP necesita una base que permita crecer desde un core de recursos y activos
hacia módulos domésticos especializados, manteniendo separados el cliente web,
la lógica de negocio y la infraestructura. Todavía no existen componentes
implementados, por lo que deben fijarse restricciones estables sin cerrar
frameworks que necesitan validación técnica.

## Decisión

- Separar la solución en frontend web y backend, comunicados mediante una API
  REST autenticada.
- Desarrollar el backend en Kotlin como monolito modular, con un event bus interno
  y PostgreSQL 16 o superior.
- Desarrollar el frontend en TypeScript, con React como recomendación pendiente
  de confirmación.
- Aplicar Clean Architecture en ambos componentes.
- Diseñar la interfaz para cubrir desde pantallas ultrawide hasta dispositivos
  móviles equivalentes a un iPhone X o superiores.
- Orientar la batería automatizada a un 60 % de pruebas unitarias de dominio,
  25 % de integración de casos de uso y 15 % de contrato de adaptadores y flujos
  end-to-end. La distribución no representa cobertura de código.

## Alternativas consideradas

- **Microservicios desde el inicio:** se descartan mientras no exista evidencia
  de límites que necesiten despliegue o escalado independiente.
- **Aplicación web y servidor sin contrato explícito:** se descarta porque
  impediría la separación requerida entre componentes.
- **Cerrar todos los frameworks ahora:** se aplaza para poder evaluarlos contra
  el primer recorrido vertical.

## Consecuencias

### Positivas

- Los límites funcionales pueden madurar dentro de una unidad operativa simple.
- Frontend y backend evolucionan contra contratos explícitos.
- El dominio queda protegido de frameworks, transporte y persistencia.

### Costes y riesgos

- El monolito necesita reglas verificables para impedir acoplamiento entre módulos.
- El event bus requiere contratos y criterios de consistencia por caso de uso.
- Autenticación, autorización, frameworks y despliegue siguen por decidir.

## Validación o reversión

La base se validará con el primer recorrido vertical que atraviese frontend, API
autenticada, aplicación, dominio y PostgreSQL, acompañado por pruebas en los tres
niveles. Dividir un módulo en un servicio o sustituir una restricción aceptada
requerirá una nueva ADR con evidencia de la necesidad.

## Posterior a esta decisión

El cuerpo de esta ADR se conserva tal y como se aceptó. De lo que quedaba por
decidir en «Costes y riesgos» —autenticación, autorización, frameworks y
despliegue— se ha resuelto casi todo al arrancar la Fase 1:

- **Autenticación y autorización**, y el framework de backend, en
  [ADR-002](ADR-002-multi-tenancy-and-backend-framework.md): Spring Boot con
  Spring Security y JWT.
- **El framework de frontend**, en
  [ADR-006](ADR-006-frontend-stack-and-design-system.md). Aquella
  «recomendación pendiente de confirmación» queda confirmada: React, sobre Vite,
  con React Router y TanStack Query. La misma ADR cierra los otros dos huecos que
  esta dejaba implícitos —dirección visual y objetivo de accesibilidad— con un
  sistema de diseño propio y WCAG 2.2 AA.
- **La cadena de construcción y las herramientas de prueba** de los tres niveles
  que esta ADR exige, en
  [ADR-008](ADR-008-repository-layout-and-build-chain.md).
- **El despliegue sigue abierto a propósito.** La configuración concreta del VPS
  es lo que fija el tamaño del volumen de ficheros, y se decide al cerrar la
  Fase 1 con consumo medido en lugar de estimado.

La distribución 60 / 25 / 15 y el recorrido vertical como criterio de validación
no cambian; la [ADR-005](ADR-005-local-file-storage.md) amplía ese recorrido para
que incluya subir una foto, verla adjunta a un asset y descargarla.
