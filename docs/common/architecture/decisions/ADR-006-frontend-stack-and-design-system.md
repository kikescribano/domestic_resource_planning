# ADR-006: Stack de frontend y sistema de diseño

- Estado: accepted
- Fecha: 2026-08-10
- Responsables: Equipo DRP
- Ámbito: frontend
- Sustituye: Ninguna

## Contexto

La [ADR-001](ADR-001-solution-architecture-baseline.md) fijó TypeScript y Clean
Architecture para el frontend, pero dejó React como «recomendación pendiente de
confirmación»; [`docs/frontend/architecture/`](../../../frontend/architecture/README.md)
arrastra esa misma frase. A su lado hay dos huecos más:
[`look-and-feel.md`](../../../frontend/product-design/look-and-feel.md) está
entero en `Por decidir` —las ocho dimensiones de dirección visual sin cerrar— y
[`accessibility/`](../../../frontend/accessibility/README.md) no fija objetivo
normativo, pese a que la ADR-001 exige una interfaz utilizable desde 375 px hasta
ultrawide.

Las tres cosas se deciden juntas porque la Fase 1 incluye el cliente completo del
core: todos los flujos con interfaz, no una demostración. Elegir la base sin
elegir cómo se ve y cómo se hace accesible aplaza el problema a un momento en que
ya habrá pantallas escritas encima.

Hay además una restricción heredada: la [ADR-005](ADR-005-local-file-storage.md)
obliga a tener nginx delante del backend para servir ficheros desde un dominio
distinto. Cualquier cosa que el frontend necesite servir ya tiene servidor.

## Decisión

Se confirma **React** y se fija el resto de la cadena:

| Pieza | Elección |
|---|---|
| Construcción | **Vite**, aplicación de página única |
| Enrutado | **React Router** |
| Estado de servidor | **TanStack Query** |
| Estilos y tokens | **Tailwind CSS** |
| Primitivas accesibles | **Radix**, para diálogo, menú, combo, pestañas y formularios |
| Pruebas | **Vitest** + Testing Library; **Playwright** para el recorrido vertical |
| Objetivo de accesibilidad | **WCAG 2.2 nivel AA** |

El sistema de diseño es **propio**: Tailwind aporta los tokens y Radix el
comportamiento accesible, pero la dirección visual la define DRP en
`look-and-feel.md`, cerrando sus ocho dimensiones, y se implementa en
[`docs/frontend/design-system/`](../../../frontend/design-system/README.md).

El diseño es **mobile-first**: la anchura de referencia es 375 px y los
breakpoints se derivan del contenido, no de modelos de dispositivo.

No hay renderizado en servidor. La aplicación entera vive detrás del login, así
que se despliega como estático detrás del mismo nginx que la ADR-005 ya exige.

## Alternativas consideradas

- **Next.js:** aporta enrutado y convenciones de serie, pero introduce un
  servidor Node en un despliegue que es un VPS con nginx y Spring Boot, y sus
  ventajas —SSR, RSC, SEO— no se cobran en una aplicación privada. Añadiría una
  tercera cosa que desplegar y vigilar a cambio de nada medible.
- **React Router en modo framework (v7):** punto intermedio razonable, con carga
  de datos por ruta y opción de SSR más adelante. Se descarta por tener menos
  recorrido documental que las otras dos y porque la opción que abre —SSR— es
  justo la que no se quiere.
- **Librería de componentes completa (MUI, Mantine):** acortaría mucho la Fase 1
  con componentes acabados y accesibles. Se descarta porque DRP acabaría
  pareciéndose a su librería y `look-and-feel.md` pasaría a ser la descripción de
  un tema ajeno en lugar de una decisión propia; y porque salir de ella más tarde
  obliga a reescribir la capa de presentación entera.
- **Aplazar la dirección visual:** interfaz funcional sin identidad en Fase 1 y
  look and feel después. Se descarta por el mismo motivo: «después» significa
  reescribir lo ya escrito.

## Consecuencias

### Positivas

- La construcción es simple y el despliegue es un directorio de estáticos detrás
  de un nginx que ya hacía falta.
- Se cierran de una vez los tres pendientes del frontend: framework, dirección
  visual y objetivo de accesibilidad.
- TanStack Query resuelve caché, reintentos y estados de carga y error, que la
  sección «Estados de experiencia» de `look-and-feel.md` exige cubrir.
- El directorio `design-system/`, hoy vacío, pasa a tener contenido real.

### Costes y riesgos

- Sin SSR el primer render depende de JavaScript. Es irrelevante tras el login,
  pero cierra la puerta a una página pública indexable sin volver a decidir.
- Un sistema de diseño propio es trabajo que una librería regala. Radix cubre
  primitivas, no componentes acabados: tabla, formulario y navegación se escriben.
- WCAG 2.2 AA es un compromiso verificable y, por tanto, incumplible de forma
  visible. Exige revisión de contraste, foco y teclado en cada componente nuevo.

## Validación o reversión

Se considera validada cuando el recorrido vertical de la ADR-001 se ejecute con
Playwright de punta a punta, `look-and-feel.md` pase de `Borrador` a `Vigente`
con sus ocho dimensiones resueltas, y una auditoría de accesibilidad confirme
contraste, foco visible y navegación completa por teclado a 375 px y en ultrawide.

Revisar si aparece un requisito de página pública indexable, que reabriría la
decisión sobre renderizado en servidor pero no necesariamente las demás.
