# Skills compartidas

Una skill es una capacidad o instrucción de trabajo reutilizable, ejecutada por
una persona, un agente o una herramienta. Este directorio documenta skills
aplicables a toda la solución; las exclusivas de un componente viven en su
directorio `skills/`.

Si una skill tiene una implementación ejecutable en otra ubicación, aquí se
mantiene el catálogo y el contrato de uso, no una copia divergente.

Por eso el [`catálogo`](catalog.md) tiene filas de dos formas. Unas apuntan a un
`SKILL-NNN-*.md` de este directorio, que es donde vive la definición. Otras
apuntan **fuera**, a una skill invocable de [`.claude/skills/`](../../../.claude/skills/):
esas ya se documentan a sí mismas junto a su código, y duplicar aquí su
definición sería garantizar que las dos versiones acaben diciendo cosas
distintas. Lo que no cambia es que **el identificador y el estado se llevan
aquí**.

## Contenido mínimo de una skill

- Propósito y resultado observable.
- Cuándo debe usarse y cuándo no.
- Entradas, salidas y precondiciones.
- Flujo de trabajo y herramientas necesarias.
- Restricciones de seguridad y efectos externos.
- Verificación y ejemplos de uso.
- Responsable, estado, versión y ubicación de la implementación.

Consulta el [`catálogo`](catalog.md) y parte de
[`skill-template.md`](skill-template.md) para añadir una nueva skill.
