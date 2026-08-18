# Producto

| Campo | Valor |
|---|---|
| Estado | Vigente |
| Responsable | Equipo DRP |
| Ámbito | Toda la solución |
| Última revisión | 2026-08-18 |

Este espacio describe qué problema resuelve DRP y qué comportamiento espera el
usuario, sin entrar en la implementación.

## Definición del core

Trasladada aquí desde la sección 4.1 del [`README principal`](../../../README.md)
al arrancar la Fase 1. **Los números de sección se conservan**, porque hay más de
cien referencias cruzadas del tipo «ver 4.1.1» por todo el repositorio.

| Documento | Contenido |
|---|---|
| [`core-model.md`](core-model.md) | 4.1.1 – 4.1.3. Assets con sus dos naturalezas, artículos, categorías, documentos, ficheros y ubicaciones; sus atributos, sus reglas de negocio y el diagrama de dominio. |
| [`users-and-access.md`](users-and-access.md) | 4.1.4. Identidad frente a pertenencia, enrolamiento, invitaciones, roles, política de contraseñas y tokens. |
| [`loans.md`](loans.md) | 4.1.5. El concepto mínimo de préstamo que vive en el core. |
| [`decisions.md`](decisions.md) | 4.1.7. Registro vivo de decisiones: las validadas con su alternativa descartada, y las que siguen abiertas. |
| [`use-cases/`](use-cases/README.md) | 5.7. Catálogo de comandos y queries, con la regla clave y el evento de cada uno. |

## Ejecución

| Documento | Contenido |
|---|---|
| [`phase-2-roadmap.md`](phase-2-roadmap.md) | Los siete hitos de la Fase 2 —activación de módulos, plataforma de avisos y los cuatro módulos de prioridad alta—, su alcance y su criterio de aceptación. **Es lo que hay que leer para arrancar un hito**, y lo que hay que actualizar al cerrarlo. |
| [`roadmap.md`](roadmap.md) | Los cinco hitos de la Fase 1, ya cerrados. Se conserva como historia de cómo se hizo el core. El estado de las *fases* sigue en la sección 8 del README. |

## Documentos previstos

| Documento | Contenido |
|---|---|
| `vision.md` | Propósito, usuarios, resultados y límites del producto. |
| `glossary.md` | Lenguaje ubicuo y diferencias entre recurso, activo y otros conceptos. |
| `capabilities.md` | Mapa de los módulos funcionales. El del core ya está en `core-model.md`. |
