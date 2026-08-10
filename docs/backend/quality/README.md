# Calidad del backend

Este espacio aterriza la estrategia de calidad para el componente Kotlin.

## Niveles de prueba

- **Dominio:** reglas e invariantes rápidas, deterministas y sin infraestructura.
- **Aplicación:** casos de uso integrados con puertos o adaptadores controlados.
- **Adaptadores y end-to-end:** contratos REST, PostgreSQL, event bus y recorridos críticos.

La distribución objetivo 60/25/15 se define en la
[`ADR-001`](../../common/architecture/decisions/ADR-001-solution-architecture-baseline.md).

## Herramientas

Fijadas en la
[`ADR-008`](../../common/architecture/decisions/ADR-008-repository-layout-and-build-chain.md):
**JUnit 5** como runner único —es lo que `@SpringBootTest` usa de serie—,
**Testcontainers** para levantar PostgreSQL real, y las aserciones de
`kotest-assertions-core`, que se adoptan por legibilidad sin traer un segundo
runner.

Una restricción que no es negociable y ya estaba aceptada en
[`data`](../data/README.md): **toda prueba que toque la base de datos se ejecuta
con un usuario sujeto a RLS.** Ejecutarlas como superusuario, o con `BYPASSRLS`,
haría pasar la batería entera sin comprobar la segunda capa de aislamiento —
cobertura falsa, que es peor que ninguna.

## Contenido previsto

- Convenciones de nomenclatura y organización de pruebas.
- Pirámide por módulo y criterios de selección de escenarios.
- Formato, análisis estático, compilación y cobertura acordada.
- Tests de arquitectura y límites modulares.
- Pruebas con PostgreSQL y control de datos de prueba.
- Puertas de CI y evidencias necesarias para entregar un cambio.
