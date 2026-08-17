# Patrones

| Campo | Valor |
|---|---|
| Estado | Borrador |
| Responsable | Equipo DRP |
| Ámbito | frontend |
| Última revisión | 2026-08-17 |

## Propósito

Documentar cómo se **componen** varios componentes para resolver una situación
que se repite: rellenar un formulario, recorrer una colección, moverse por una
jerarquía, enterarse de lo que ha pasado y llegar de una pantalla a otra.

Un componente responde a «qué es esto y cómo se comporta». Un patrón responde a
«en qué orden van las piezas, y por qué esta y no otra». La diferencia importa
porque casi nada de lo que el Hito 2 tuvo que construir **era un componente
nuevo**: era una composición de los que ya había, más cuatro o cinco que faltaban
y en su mayoría siguen faltando.

Estos documentos siguen la misma regla que las
[fichas de componente](../components/README.md): **describen lo que hay, y
marcan como previsto lo que no**. Un patrón entero en «previsto» es una respuesta
correcta aquí: al nacer lo estaban dos de los cinco, y **hoy queda uno** —el
listado paginado, que sigue sin existir porque ninguna pantalla del core lo pidió—.
Decirlo es lo que evita que alguien lo dé por hecho.

## Alcance

### Incluido

- Un documento por situación recurrente, con su estado de implementación.
- Lo que cada patrón le pide al sistema y todavía no tiene.
- Las decisiones que el Hito 2 tiene que tomar y que ningún componente puede
  tomar por su cuenta.

### Fuera de alcance

- La anatomía y la API de cada pieza, en
  [`components/`](../components/README.md).
- Las decisiones visuales que los patrones consumen sin decidir, en
  [`foundations/`](../foundations/README.md), y sus valores en
  [`tokens/`](../tokens/README.md).
- La intención de producto que los justifica, en
  [`look-and-feel.md`](../../product-design/look-and-feel.md).
- Terminología, voz y etiquetas, que irán en `content/`.

## Registro

| Patrón | Documento | Estado |
|---|---|---|
| Formulario | [`form.md`](form.md) | **Implementado** en su forma de una columna; la operación corta sobre una fila, prevista |
| Feedback | [`feedback.md`](feedback.md) | **Implementado** el aviso en el sitio y el canal de advertencias; el aviso efímero y el error bloqueante, previstos |
| Navegación | [`navigation.md`](navigation.md) | **Implementado** el shell; el anuncio de ruta y el conmutador de tema, previstos |
| Listado | [`listing.md`](listing.md) | **Previsto**: no hay ningún listado paginado |
| Jerarquía navegable | [`hierarchy.md`](hierarchy.md) | **Implementado** en el Hito 2: [`locations.tsx`](../../../../frontend/src/routes/locations.tsx) pinta un `role="tree"` con `aria-level` |

## La regla que comparten los cinco

Aparece en tres de ellos por separado y conviene reconocerla una sola vez:
**una estructura, dos presentaciones.**

- La navegación es **un** `<nav>` recolocado con CSS, no una barra inferior y una
  lateral.
- El listado es **un** listado que se pinta como tarjetas o como tabla, no dos.
- El árbol es **uno**, se recorra por niveles en móvil o desplegado en
  escritorio.

El motivo es siempre el mismo: duplicar la estructura para tener dos aspectos
deja dos copias en el DOM, y quien navega con lector de pantalla recorre la
colección dos veces. Que una esté oculta con `display:none` lo salva en la
práctica, y basta un cambio de clase para que deje de estarlo. Es un fallo que no
se ve mirando la pantalla.

Y una segunda regla, que es la de
[`density.md`](../foundations/density.md): **cuál de las dos presentaciones se
usa lo decide el dispositivo de entrada, no el gusto**. Con el dedo, los 44 px de
objetivo mínimo imponen holgura; con puntero, esa holgura se convierte en scroll.

## Las decisiones que el Hito 2 no puede esquivar

Están razonadas cada una en su documento; se listan aquí porque son de patrón y
no de componente, y porque si no se toman, las toma la primera pantalla que se
escriba:

| Decisión | Dónde |
|---|---|
| Dónde va la acción principal en móvil, si la banda inferior ya es la navegación | [`navigation.md`](navigation.md) |
| Cómo caben seis destinos en la barra inferior a 375 px | [`navigation.md`](navigation.md) |
| Paginación clásica o scroll infinito, y si el estado del listado viaja en la URL | [`listing.md`](listing.md) |
| Árbol ARIA o lista anidada de desplegables | [`hierarchy.md`](hierarchy.md) |
| Cargar el árbol entero o por niveles bajo demanda | [`hierarchy.md`](hierarchy.md) |
| Dónde vive la traducción de código de error a lenguaje de casa | [`feedback.md`](feedback.md) |
| De dónde sale la anchura de un formulario | [`form.md`](form.md) |

## Referencias

- [`../README.md`](../README.md): el índice del sistema de diseño.
- [`components/`](../components/README.md): las fichas de lo que existe y la
  lista de lo que falta.
- [`foundations/`](../foundations/README.md) y [`tokens/`](../tokens/README.md)
- [`look-and-feel.md`](../../product-design/look-and-feel.md): los cinco
  principios y los siete estados de experiencia.
- [`accessibility/`](../../accessibility/README.md)
- [`ADR-006`](../../../common/architecture/decisions/ADR-006-frontend-stack-and-design-system.md)

## Historial de cambios

| Fecha | Cambio | Autor |
|---|---|---|
| 2026-08-12 | Creación del directorio con los cinco patrones que el Hito 2 necesita: tres implementados a medias y dos previstos enteros. | Equipo DRP |
| 2026-08-17 | Corrección de estado al cerrar la Fase 1, que el registro no había recibido desde su creación: la **jerarquía navegable está implementada** —árbol con `role="tree"` desde el Hito 2— y el **canal de advertencias** también, así que de los cinco patrones queda uno entero en previsto, el listado paginado. | Equipo DRP |
