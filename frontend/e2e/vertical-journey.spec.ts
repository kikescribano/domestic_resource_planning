import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'

/**
 * El recorrido vertical de la Fase 1, en un navegador de verdad.
 *
 * Atraviesa las cinco capas que la [ADR-001] pide demostrar —frontend, API
 * autenticada, aplicación, dominio y PostgreSQL— y termina en el sitio donde
 * este hito puso lo suyo: **una pantalla que se abre desde un correo, sin
 * sesión**.
 *
 * Lo que aporta sobre las pruebas que ya había no es cobertura de la API. Eso lo
 * cubren mejor las de recorrido del backend, que hablan con PostgreSQL real y
 * pueden mirar los bytes en disco. Lo que solo se puede comprobar aquí es que
 * **una persona llega**: que el enlace del correo abre una pantalla utilizable,
 * que el foco cae donde se dijo, que el contraste es el que se auditó y que a
 * 375 px no se rompe nada.
 *
 * Corre contra el `compose.yaml`, así que PostgreSQL y Mailpit tienen que estar
 * arriba. El correo se lee de Mailpit **como lo leería una persona**, igual que
 * en el backend: es lo que impide que la prueba conozca un token que el usuario
 * no tendría.
 */

const MAILPIT = 'http://localhost:8025'

test.describe('recorrido vertical', () => {
  test('de dar de alta un hogar a devolver un préstamo desde el correo', async ({ page, browser }) => {
    const email = `persona-${Date.now()}@example.test`
    const password = 'el gato duerme en el sofa'
    const vecino = `vecino-${Date.now()}@example.test`

    // --- 1. Alta del hogar, y verificación por el correo real ----------------
    await page.goto('/crear-hogar')
    await page.getByLabel('Nombre del hogar').fill('Casa del Pinar')
    await page.getByLabel('Tu nombre').fill('Kike')
    await page.getByLabel('Correo').fill(email)
    await page.getByLabel('Contraseña', { exact: true }).fill(password)
    await page.getByRole('button', { name: /crear/i }).click()

    const verification = await linkFromEmail(email)
    await page.goto(verification)
    await expect(page.getByRole('heading', { level: 1, name: 'Tu hogar' })).toBeVisible()

    // --- 2. Una cosa que prestar --------------------------------------------
    // Por el `<nav>` y no por el texto suelto: «Inventario» aparece también en
    // enlaces dentro del contenido, y una coincidencia ambigua es un fallo que
    // vuelve cada vez que alguien añade una frase.
    await navigateTo(page, 'Inventario', '/inventario')
    await page.getByRole('link', { name: 'Dar de alta' }).click()
    await page.getByLabel('Nombre').fill('Taladro')
    await page.getByLabel('Categoría').selectOption({ label: 'Herramientas' })
    await page.getByRole('button', { name: 'Dar de alta' }).click()
    // A la ficha del asset recién creado, y **esperando a que llegue**. El alta
    // navega desde el `onSuccess` de la mutación, así que salir de aquí antes de
    // que termine deja una navegación pendiente que se ejecuta después y devuelve
    // al usuario a esta pantalla desde donde estuviera. Costó un rato de
    // diagnóstico: la URL era la correcta un instante y la pantalla, otra.
    await page.waitForURL('**/inventario/*')
    await expect(page.getByRole('heading', { level: 1, name: 'Taladro' })).toBeVisible()

    // --- 3. Prestarlo a alguien de fuera ------------------------------------
    await navigateTo(page, 'Préstamos', '/prestamos')
    await checkAccessibility(page, 'préstamos del hogar')

    await page.getByRole('button', { name: 'Prestar algo' }).click()
    await page.getByLabel('Qué prestas').selectOption({ label: 'Taladro' })
    await page.getByLabel('A quién').selectOption({ label: 'Otra persona' })
    await page.getByLabel('Su nombre').fill('Vecino del 3.º')
    await page.getByLabel('Su correo').fill(vecino)
    await page.getByRole('button', { name: 'Prestar', exact: true }).click()

    await expect(page.getByText('Prestado').first()).toBeVisible()

    // --- 4. El externo abre su enlace, en un navegador SIN sesión ------------
    // Un contexto nuevo y no la misma pestaña: con la sesión del hogar viva, la
    // pantalla podría estar funcionando por la credencial equivocada y nadie se
    // enteraría. Esto es lo que ninguna prueba de componente puede comprobar.
    const loanLink = await linkFromEmail(vecino)
    const strangerContext = await browser.newContext()
    const stranger = await strangerContext.newPage()

    await stranger.goto(loanLink)
    await expect(stranger.getByRole('heading', { level: 1, name: 'Taladro' })).toBeVisible()

    // Lo que no puede salir de casa, comprobado sobre el DOM entregado.
    await expect(stranger.getByText('Vecino del 3.º')).toHaveCount(0)
    await expect(stranger.getByRole('navigation')).toHaveCount(0)

    await checkAccessibility(stranger, 'vista externa')

    // Y a 375 px, que es el suelo del rango de dispositivos.
    await stranger.setViewportSize({ width: 375, height: 812 })
    await expect(stranger.getByRole('button', { name: 'Ya lo he devuelto' })).toBeVisible()
    // Nada de desplazamiento horizontal: es el fallo de reflujo más común y el
    // único que se ve solo con un navegador de verdad midiendo el documento.
    const overflows = await stranger.evaluate(
      () => document.documentElement.scrollWidth > document.documentElement.clientWidth,
    )
    expect(overflows, 'la pantalla externa desborda a lo ancho en 375 px').toBe(false)

    // --- 5. Devolver desde el enlace, y que el hogar lo vea -----------------
    await stranger.getByRole('button', { name: 'Ya lo he devuelto' }).click()
    await expect(stranger.getByText(/ya está cerrado/)).toBeVisible()
    await strangerContext.close()

    // El asset vuelve a casa: la vuelta entera cerrada, de la pantalla externa
    // a la fila de PostgreSQL y de vuelta a la pantalla del hogar.
    await navigateTo(page, 'Inventario', '/inventario')
    await page.getByText('Taladro').first().click()
    await expect(page.getByText('Disponible').first()).toBeVisible()
  })
})

/**
 * Un enlace de la navegación principal.
 *
 * Por el landmark y no por el texto suelto: «Inventario» aparece también en
 * enlaces dentro del contenido. Y esperando a la URL, porque en una SPA el clic
 * vuelve antes de que la ruta haya cambiado y el `await` siguiente se pondría a
 * buscar en la pantalla anterior.
 */
async function navigateTo(page: Page, label: string, path: string) {
  await page.getByRole('navigation', { name: 'Principal' }).getByRole('link', { name: label }).click()
  await page.waitForURL(`**${path}`)
}

/**
 * El enlace del último correo dirigido a esa dirección.
 *
 * Espera activamente porque la entrega es síncrona pero no instantánea, y
 * porque un `waitForTimeout` fijo o sobra o se queda corto según la máquina.
 */
async function linkFromEmail(recipient: string): Promise<string> {
  const deadline = Date.now() + 30_000

  while (Date.now() < deadline) {
    const response = await fetch(`${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:${recipient}`)}`)
    const found = (await response.json()) as { messages?: Array<{ ID: string }> }
    const id = found.messages?.[0]?.ID

    if (id) {
      const message = await fetch(`${MAILPIT}/api/v1/message/${id}`)
      const body = (await message.json()) as { Text?: string }
      const link = /https?:\/\/[^\s]+/.exec(body.Text ?? '')?.[0]
      if (link) return link
    }

    await new Promise((resolve) => setTimeout(resolve, 500))
  }

  throw new Error(`No llegó ningún correo a ${recipient} en 30 s`)
}

/**
 * La auditoría automática de accesibilidad sobre la pantalla montada.
 *
 * Es la mitad que [`check-contrast.py`](../../scripts/check-contrast.py) no
 * puede hacer: aquel comprueba los **tokens** en abstracto y este los comprueba
 * ya aplicados, junto al resto de reglas —nombres accesibles, orden de
 * encabezados, roles— que solo existen sobre un DOM.
 *
 * Acotado a A y AA, que es el objetivo normativo que fija la ADR-006.
 */
async function checkAccessibility(page: Page, screen: string) {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze()

  expect(results.violations, `Accesibilidad en «${screen}»: ${describe(results.violations)}`).toEqual([])
}

function describe(violations: Array<{ id: string; help: string; nodes: unknown[] }>): string {
  if (violations.length === 0) return 'ninguna'
  return violations.map((v) => `${v.id} (${v.nodes.length}): ${v.help}`).join('; ')
}
