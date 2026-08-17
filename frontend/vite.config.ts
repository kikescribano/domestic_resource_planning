/// <reference types="vitest/config" />
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    // En desarrollo la API se alcanza por el mismo origen, para que el
    // frontend no tenga que saber nada de CORS ni de dominios.
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
