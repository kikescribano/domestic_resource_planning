# ADR-007: El contrato OpenAPI como fuente de verdad de la API

- Estado: accepted
- Fecha: 2026-08-10
- Responsables: Equipo DRP
- Ámbito: common
- Sustituye: Ninguna

## Contexto

El contrato de la API vive en [`openapi.yaml`](../../../../openapi.yaml) desde la
Fase 0, con 47 operaciones y 34 esquemas. Pero se escribió como documentación de
una definición, no como contrato ejecutable, y eso se nota:

- **Ninguna** de las 47 operaciones declara `operationId`, lo que impide generar
  clientes con nombres estables y nombrar operaciones en las pruebas.
- **Ninguna** de las colecciones está paginada: las diez devuelven arrays
  desnudos. Añadir paginación después es un cambio incompatible simultáneo en
  todas ellas.
- No se declaran `400`, `429` ni `500`, ni ninguna respuesta `default`. El
  frontend no tiene contrato para el error más frecuente, el de validación.
- El esquema `Error` no tiene `required` ni enumera sus códigos: los 34 códigos
  de negocio viven en prosa, donde nada los verifica.
- Restricciones de tipo «exactamente uno de» —documento con `url` o `fileId`,
  participante de préstamo interno o externo— están descritas en texto y no
  expresadas en el esquema.

En la Fase 1 el backend y el frontend se escriben a la vez, y ambos necesitan un
punto fijo contra el que trabajar sin esperarse. Además, el 15 % de la pirámide
de pruebas de la ADR-001 es «contrato de adaptadores»: sin un contrato
verificable, ese tramo es opinión.

## Decisión

`openapi.yaml` es la **fuente de verdad** de la API y se edita a mano, antes que
el código que lo implementa.

- El **frontend genera** tipos y cliente a partir del contrato. No escribe a mano
  la forma de ninguna petición ni respuesta.
- El **backend escribe los controladores a mano** y se verifica contra el
  contrato con las pruebas de adaptador del 15 %. No se genera código de servidor.
- **Spectral** valida el contrato en cada integración, junto al validador de
  esquema OpenAPI que ya se usaba a mano.
- Las colecciones se paginan con un **envoltorio uniforme**
  `{ items, page, size, total }` y parámetros `page` y `size`, en las diez, sin
  excepción por tamaño esperado.
- Un cambio de contrato entra en **el mismo incremento** que su implementación y
  que los ejemplos del README, como exige la convención de
  [`docs/README.md`](../../../README.md).

## Alternativas consideradas

- **Code-first con springdoc:** el contrato se generaría desde las anotaciones
  del backend. Nunca mentiría sobre lo implementado, pero subordina al código una
  definición que la Fase 0 elaboró primero, y deja al frontend sin contrato
  estable contra el que avanzar en paralelo: tendría que esperar a que el backend
  existiera para saber qué llamar.
- **Contract-first generando también los controladores Kotlin:** haría imposible
  desviarse del contrato, a costa de meter código generado en la capa de
  adaptadores de Clean Architecture y un paso de generación en el build del
  backend. La disciplina que aporta ya la dan las pruebas de adaptador, que
  además documentan.
- **Dejar el contrato como documentación no verificada:** es la situación actual.
  Se descarta porque un contrato que nada comprueba se desincroniza en semanas, y
  porque el frontend generaría su cliente desde algo que puede estar mintiendo.
- **Paginar solo las colecciones que pueden crecer:** menos ceremonia en listas
  acotadas por diseño, como categorías o invitaciones. Se descarta a favor de una
  sola forma que aprender en el cliente y ninguna sorpresa futura si una lista
  que se creía pequeña deja de serlo.

## Consecuencias

### Positivas

- Backend y frontend avanzan en paralelo desde el primer día contra un punto fijo.
- El 15 % de la pirámide tiene una definición objetiva de qué comprobar: cada
  operación del contrato, una prueba de adaptador.
- La paginación deja de ser una deuda incompatible con el cliente ya escrito.
- El catálogo de códigos de error pasa a ser verificable, y con él los mensajes
  que el frontend debe saber tratar.

### Costes y riesgos

- Editar YAML a mano es más laborioso que anotar controladores, y el contrato
  crece en cada operación nueva antes de que exista el código.
- El contrato **puede** desviarse de la implementación si una operación se queda
  sin prueba de adaptador. La cobertura de ese tramo es la salvaguarda, y hay que
  vigilarla.
- Los ejemplos del README y el contrato siguen siendo dos sitios que mantener
  sincronizados hasta que el reparto documental los reúna.

## Validación o reversión

Se considera validada cuando la integración continua falle ante un contrato que
no valide o que infrinja las reglas de Spectral, cuando exista una prueba de
adaptador por operación, y cuando el cliente TypeScript del frontend se genere
sin correcciones manuales.

Revisar si el coste de mantener el YAML a mano supera el beneficio —señal:
operaciones que llegan al contrato después de estar implementadas— en cuyo caso
la alternativa a evaluar es code-first con el contrato generado como artefacto.
