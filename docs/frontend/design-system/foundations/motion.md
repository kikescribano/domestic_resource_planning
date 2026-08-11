# Movimiento

| Campo | Valor |
|---|---|
| Estado | Borrador |
| Responsable | Equipo DRP |
| Ámbito | frontend |
| Última revisión | 2026-08-10 |

## Propósito

Fijar cuánto dura cada cosa que se mueve, con qué curva, y dejar claro por qué
quitarlo entero no cuesta nada.

## Alcance

### Incluido

- Las cuatro duraciones y las tres curvas.
- Qué propiedades se animan y cuáles no.
- Cómo se respeta `prefers-reduced-motion`.

### Fuera de alcance

- Las animaciones concretas de cada componente, que se documentan con él.

## Contenido

### La regla que ordena todo lo demás

**El movimiento explica un cambio que ya ha ocurrido; nunca lo comunica.** Si un
usuario que no ve la animación pierde información, la animación estaba haciendo
un trabajo que no le tocaba. Esta regla no es un adorno: es la condición que
convierte `prefers-reduced-motion` en algo gratis, en lugar de en una versión
degradada del producto.

### Duraciones

| Token | Valor | Para qué |
|---|---|---|
| `--duration-instant` | 80 ms | Respuesta al puntero o a la pulsación: hover de fila, estado activo de un botón |
| `--duration-fast` | 140 ms | Cambio de estado de un control: foco, selección, aparición de un distintivo |
| `--duration-base` | 220 ms | Desplegar y plegar: menú, acordeón, panel lateral |
| `--duration-slow` | 320 ms | Abrir o cerrar algo grande a petición del usuario: diálogo, hoja inferior |

Nada del sistema pasa de 320 ms. Por encima de ese umbral la animación deja de
percibirse como respuesta y empieza a percibirse como espera.

`--duration-fast` es además la transición por defecto de Tailwind
(`--default-transition-duration`), para que un `transition` escrito sin pensar
salga ya con el valor del sistema.

### Curvas

| Token | Valor | Cuándo |
|---|---|---|
| `--ease-standard` | `cubic-bezier(0.2, 0, 0.2, 1)` | Por defecto: algo que cambia estando ya en pantalla |
| `--ease-out-soft` | `cubic-bezier(0.16, 0.84, 0.44, 1)` | Algo que **entra**: arranca rápido y se posa |
| `--ease-in-soft` | `cubic-bezier(0.5, 0, 0.75, 0)` | Algo que **sale**: se va acelerando y desaparece |

Lo que entra se posa; lo que sale se marcha. Nunca lineal: el movimiento lineal
se percibe mecánico, y esto es una casa.

### Qué se anima y qué no

Se animan **opacidad y transformación**, y ya está. Son las dos propiedades que
el navegador resuelve sin recalcular la maquetación, y por eso son las únicas que
van finas en un móvil modesto con una lista larga detrás.

No se animan `width`, `height`, `top`, `left` ni `margin`. Un acordeón se abre
con `grid-template-rows` o con transformación, no estirando una caja.

Y tres prohibiciones más:

- **Nada se mueve en bucle salvo el indicador de carga.** Ningún icono que
  respira, ningún destello que recorre un esqueleto.
- **Nada se anima al aparecer en pantalla al hacer scroll.** No hay
  «revelaciones».
- **Nada se mueve dentro de una fila de listado** salvo la respuesta al puntero,
  de 80 ms.

### Reducción de movimiento

`prefers-reduced-motion: reduce` hace dos cosas en
[`index.css`](../../../../frontend/src/index.css):

1. Deja las cuatro duraciones del sistema en 1 ms, para todo lo que las lea desde
   los tokens.
2. Corta con `!important` cualquier `animation` o `transition` ya declarada, y
   fuerza `scroll-behavior: auto`. Es lo único que alcanza al código que no es
   nuestro, incluidas las primitivas de Radix.

No se anula la animación poniéndola a cero mediante `animation: none`, sino
reduciéndola a 1 ms, a propósito: así los eventos `animationend` y
`transitionend` siguen disparándose y ningún componente que dependa de ellos se
queda esperando para siempre. Es el fallo clásico de esta preferencia.

El resultado tiene que ser una aplicación que **funciona igual**, no una
aplicación sin transiciones que además pierde algo. Si aparece un componente en
el que reducir el movimiento pierde información, el componente está mal, no la
preferencia.

## Decisiones abiertas

- Ninguna.

## Referencias

- [`tokens/`](../tokens/README.md)
- [`look-and-feel.md`](../../product-design/look-and-feel.md), principio 5.
- [`accessibility/`](../../accessibility/README.md)

## Historial de cambios

| Fecha | Cambio | Autor |
|---|---|---|
| 2026-08-10 | Creación del documento con duraciones, curvas y reducción de movimiento. | Equipo DRP |
