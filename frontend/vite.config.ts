/// <reference types="vitest/config" />
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

/**
 * **El aviso de «chunks larger than 500 kB» al construir es esperado, y no se
 * silencia.** Lo levanta el decodificador de HEIC (ADR-014), que son 2 995 kB en
 * un fragmento propio; su consejo —«considera usar import() dinámico»— ya está
 * aplicado, que es precisamente por lo que sale en un fragmento aparte y no
 * dentro del bundle. Subir `build.chunkSizeWarningLimit` lo taparía a cambio de
 * dejar de avisar del día en que el que crezca sea el de la aplicación, que es
 * el aviso que sí importa.
 */
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    // Escucha también en la red local para poder probar desde otro
    // dispositivo (un móvil contra la IP del equipo). Le basta el proxy de
    // abajo: el móvil habla con Vite y Vite con el backend.
    host: true,
    // En desarrollo la API se alcanza por el mismo origen, para que el
    // frontend no tenga que saber nada de CORS ni de dominios. No es solo
    // comodidad: el backend no tiene CORS —no hay ninguna petición
    // cross-origin en ninguna topología del proyecto (ver SecurityConfig)—
    // así que sin este proxy las llamadas a la API no funcionarían.
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: true,
    // Vitest recoge por defecto todo lo que acabe en `.spec.ts`, y el recorrido
    // vertical de Playwright vive en `e2e/` con ese nombre. Sin esta línea,
    // Vitest lo carga en jsdom, no encuentra `@playwright/test` y falla un
    // fichero entero sin que ninguna prueba lo esté midiendo. Los dos corredores
    // se reparten el directorio: `src/` es de Vitest y `e2e/` de Playwright.
    exclude: ['e2e/**', 'node_modules/**', 'dist/**'],
  },
})
