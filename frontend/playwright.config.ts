import { defineConfig, devices } from '@playwright/test'

/**
 * El recorrido vertical en un navegador de verdad.
 *
 * Es la condición que la [ADR-006] y la [ADR-008] ponen al sistema de diseño y a
 * la cadena de pruebas, y la que mantuvo `look-and-feel.md` en `Borrador` hasta
 * cumplirse. Lo que aporta sobre lo que ya había —el recorrido desde el backend
 * con Testcontainers— no es cobertura de la API sino **lo que solo ocurre en un
 * navegador**: que la pantalla externa se abra desde un enlace sin sesión, que se
 * llegue a cada acción con el teclado viendo el foco en cada parada, que el
 * contraste sea el que se dijo también en oscuro, y que el reflujo aguante de 320
 * px a ultrawide sobre el DOM real.
 *
 * Deliberadamente **una sola especificación y no una suite paralela**. Duplicar
 * aquí lo que ya comprueban las 45 pruebas de Vitest y las de recorrido del
 * backend costaría minutos de CI por cada cambio y no mediría nada nuevo.
 *
 * Levanta los dos servidores por su cuenta con `webServer`, así que no hace
 * falta arrancar nada a mano — pero **PostgreSQL y Mailpit sí tienen que estar
 * en marcha**: son los del `compose.yaml`, porque esto ejecuta la aplicación de
 * verdad y no un doble.
 *
 * ```bash
 * docker compose up -d postgres mailpit
 * cd frontend && npx playwright test
 * ```
 */
export default defineConfig({
  testDir: './e2e',
  // El recorrido entero es una secuencia: dar de alta el hogar, verificarlo,
  // crear, prestar y devolver. Paralelizarlo no ahorraría nada y haría que dos
  // ficheros compitieran por el mismo Mailpit.
  fullyParallel: false,
  workers: 1,
  // Un fallo aquí casi siempre es un servidor que no arrancó, y reintentar lo
  // esconde. En la CI se reintenta una vez, que cubre el arranque lento.
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['github'], ['html', { open: 'never' }]] : [['list']],
  timeout: 60_000,

  use: {
    baseURL: 'http://localhost:5173',
    // Solo del intento fallido: guardar siempre la traza de todo llena el disco
    // del runner y nadie mira la del caso que pasó.
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },

  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],

  webServer: [
    {
      // El backend de verdad, con su PostgreSQL y su Mailpit detrás.
      //
      // Va por un lanzador de Node y no por `command` a pelo porque el comando
      // lo interpreta el shell del sistema, y ahí las dos plataformas no se
      // parecen: `cmd` no entiende `./gradlew` y `sh` no ejecuta `gradlew.bat`.
      // El lanzador resuelve el wrapper que toca con rutas absolutas.
      command: 'node ./e2e/start-backend.mjs',
      url: 'http://localhost:8080/api/v1/loans',
      // 401 es la respuesta correcta de un endpoint autenticado sin token, y
      // significa que la aplicación está en pie: esperar un 200 exigiría
      // inventar una sesión antes de que el servidor exista.
      timeout: 180_000,
      reuseExistingServer: !process.env.CI,
    },
    {
      // `dev` y no `preview` a propósito: el proxy de `/api` está en la
      // configuración del servidor de desarrollo, y con `preview` habría que
      // duplicarlo o meter CORS en el backend solo para las pruebas.
      command: 'npm run dev',
      url: 'http://localhost:5173',
      timeout: 60_000,
      reuseExistingServer: !process.env.CI,
    },
  ],
})
