/// <reference types="vite/client" />

// Los tipos que Vite aporta al código de la aplicación: entre ellos, la
// declaración de los módulos que no son JavaScript —`*.css`, `*.svg`, `*.png`—
// y la de `import.meta.env`.
//
// **Faltaban desde siempre, y nadie lo notó porque TypeScript lo toleraba.**
// El `tsconfig.json` declara `types` de forma explícita —`node`,
// `vitest/globals`, `@testing-library/jest-dom`— y esa lista **sustituye** al
// descubrimiento automático de `@types`, así que `vite/client` nunca entraba.
// Con `import './index.css'` en `main.tsx` eso deberia haber sido un error
// desde el primer día; hasta TypeScript 5 pasaba como importación por efecto
// secundario sin tipos, y TypeScript 7 dejó de aceptarlo:
//
//   src/main.tsx(6,8): error TS2882: Cannot find module or type declarations
//   for side-effect import of './index.css'
//
// De ahí que este fichero no sea una preparación para la subida de TypeScript
// sino el cierre de un hueco que ya existía: la subida solo lo hizo visible.
//
// Va como fichero aparte, y no como una entrada más en el `types` del
// `tsconfig.json`, porque es la convención de Vite --su plantilla lo genera
// asi-- y porque una referencia dentro de `src/` viaja con el codigo al que
// pertenece.
