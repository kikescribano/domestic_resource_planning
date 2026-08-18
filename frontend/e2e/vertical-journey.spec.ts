import AxeBuilder from '@axe-core/playwright'
import { expect, test, type APIRequestContext, type Locator, type Page } from '@playwright/test'

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
 * que el foco cae donde se dijo, que el contraste es el que se auditó y que el
 * reflujo aguanta en todo el rango declarado.
 *
 * Esas cuatro cosas son, exactamente, la condición que la [ADR-006] pone para
 * que `look-and-feel.md` pase de `Borrador` a `Vigente`: **contraste, foco
 * visible y navegación completa por teclado, a 375 px y en ultrawide**. Por eso
 * el recorrido no solo hace clic: en los dos puntos donde una persona decide
 * algo llega con el tabulador, comprueba el anillo de foco en cada parada y
 * pulsa `Enter`. Un recorrido que solo hace clic deja sin medir la mitad del
 * compromiso.
 *
 * Corre contra el `compose.yaml`, así que PostgreSQL y Mailpit tienen que estar
 * arriba. El correo se lee de Mailpit **como lo leería una persona**, igual que
 * en el backend: es lo que impide que la prueba conozca un token que el usuario
 * no tendría.
 */

const MAILPIT = 'http://localhost:8025'

/** El ancho por defecto del proyecto, al que se vuelve tras medir el reflujo. */
const DESKTOP = { width: 1280, height: 720 }

/**
 * Los tres anchos en los que se mide el reflujo.
 *
 * 320 px es donde lo mide el criterio 1.4.10 y 375 es el suelo que declara el
 * diseño: entre uno y otro caben 55 px en los que un `min-width` olvidado se
 * nota o no se nota, así que hay que pasar por los dos. El techo va aparte
 * porque su fallo es el contrario —la línea de texto que se estira sin freno—, y
 * ese no lo delata ninguna medida del documento.
 */
const WIDTHS = [
  { name: '320 px', size: { width: 320, height: 640 } },
  { name: '375 px', size: { width: 375, height: 812 } },
  { name: 'ultrawide', size: { width: 2560, height: 1080 } },
]

/** El tope de `--container-shell`, 96rem, que es lo que impide la línea infinita. */
const SHELL_CAP = 1536

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
    await checkReflow(page, 'préstamos del hogar')

    // Con el teclado y desde el principio de la página, que es como llega quien
    // no usa el ratón. La primera parada tiene que ser el salto al contenido: si
    // deja de serlo, recorrer la aplicación con el tabulador pasa por los ocho
    // enlaces de la navegación en cada pantalla.
    const prestar = page.getByRole('button', { name: 'Prestar algo' })
    await startKeyboardAtTop(page, prestar)
    await page.keyboard.press('Tab')
    await expect(page.getByRole('link', { name: 'Saltar al contenido' })).toBeFocused()
    await expectFocusRing(page, 'el salto al contenido')

    await tabTo(page, prestar, 'Prestar algo')
    await page.keyboard.press('Enter')
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
    await checkReflow(stranger, 'la pantalla externa')

    // --- 5. Devolver desde el enlace, con el teclado ------------------------
    // Aquí el teclado no es un extra: esta pantalla tiene **una sola acción** y
    // es terminal. Si no se llega a ella tabulando, para quien no usa el ratón
    // el préstamo no se puede cerrar y no hay otro camino —ni menú, ni sesión,
    // ni segunda pantalla— por el que rodearlo.
    const confirmar = stranger.getByRole('button', { name: 'Ya lo he devuelto' })
    await startKeyboardAtTop(stranger, confirmar)
    await tabTo(stranger, confirmar, 'la confirmación de devolución')
    await stranger.keyboard.press('Enter')

    await expect(stranger.getByText(/ya está cerrado/)).toBeVisible()
    // El cambio no mueve el foco —al desaparecer el botón la pantalla se queda sin
    // paradas—, así que tiene que anunciarse. Y **una sola vez**: aquí había dos
    // regiones vivas dando la misma noticia, que es lo que la ficha de la
    // pantalla prohíbe expresamente. Contar es lo que impide que vuelvan.
    const anuncios = stranger.getByRole('status')
    await expect(anuncios, 'la devolución se anuncia dos veces').toHaveCount(1)
    await expect(anuncios).toHaveText(/ya está cerrado/)
    await strangerContext.close()

    // El asset vuelve a casa: la vuelta entera cerrada, de la pantalla externa
    // a la fila de PostgreSQL y de vuelta a la pantalla del hogar.
    await navigateTo(page, 'Inventario', '/inventario')
    await page.getByText('Taladro').first().click()
    await expect(page.getByText('Disponible').first()).toBeVisible()

    // --- 6. La bandeja de avisos --------------------------------------------
    // La pantalla que trae la plataforma de avisos. Sale vacía a propósito: lo
    // que la llena es el recorrido periódico del backend, que corre de
    // madrugada y no se dispara desde aquí —eso lo miden sus pruebas, que leen
    // el resumen del Mailpit de verdad—. Lo que sí se comprueba aquí es lo
    // único que no se puede comprobar en otro sitio: que una persona llega, que
    // la pantalla vacía **explica por qué** está vacía en vez de parecer rota, y
    // que la parada nueva no rompe la navegación en el ancho más estrecho.
    await navigateTo(page, 'Avisos', '/avisos')
    await expect(page.getByText('Nada pendiente')).toBeVisible()
    await checkAccessibility(page, 'la bandeja de avisos')
    await checkReflow(page, 'la bandeja de avisos')
    await checkTouchTargets(page)
  })

  /**
   * El ciclo de la activación de un módulo, de punta a punta.
   *
   * Es la mitad que ninguna prueba de componente puede dar: el `403` sale del
   * filtro de verdad, la navegación se repinta sobre el DOM real y la pantalla
   * de una ruta apagada se mide con axe y con el tabulador. El módulo que se
   * enciende es **Proveedores**, que en el Hito 0 es una declaración sin dominio
   * — y eso es justo lo que hace la prueba honesta: lo que se está comprobando
   * es el mecanismo, no el módulo.
   *
   * La otra mitad del ciclo —que los datos del módulo siguen ahí al reactivarlo—
   * se comprueba en el backend con el módulo de prueba, que sí tiene tabla. Aquí
   * no hay ninguna que sobreviva porque ninguno de los cuatro tiene datos
   * todavía.
   */
  test('un módulo apagado no existe, se enciende desde su pantalla y vuelve a apagarse', async ({
    page,
    request,
  }) => {
    const email = `admin-${Date.now()}@example.test`
    const password = 'el gato duerme en el sofa'

    await page.goto('/crear-hogar')
    await page.getByLabel('Nombre del hogar').fill('Casa de los Módulos')
    await page.getByLabel('Tu nombre').fill('Kike')
    await page.getByLabel('Correo').fill(email)
    await page.getByLabel('Contraseña', { exact: true }).fill(password)
    await page.getByRole('button', { name: /crear/i }).click()
    await page.goto(await linkFromEmail(email))
    await expect(page.getByRole('heading', { level: 1, name: 'Tu hogar' })).toBeVisible()

    const navigation = page.getByRole('navigation', { name: 'Principal' })

    // --- 1. Apagado: ni navegación ni API -----------------------------------
    await expect(navigation.getByRole('link', { name: 'Proveedores' })).toHaveCount(0)
    await expect(navigation.getByRole('link', { name: 'Módulos del hogar' })).toBeVisible()

    // Y la API, con el token de verdad y contra el filtro de verdad. Es un `403`
    // con código propio y no un `404`: el frontend necesita esa diferencia para
    // poder ofrecer la activación en lugar de enseñar un error.
    const token = await accessToken(request, email, password)
    const closed = await request.get('/api/v1/suppliers', { headers: { Authorization: `Bearer ${token}` } })
    expect(closed.status(), 'la ruta de un módulo apagado no responde 403').toBe(403)
    expect(await closed.text()).toContain('MODULE_INACTIVE')

    // Entrar a mano en su ruta lleva a la pantalla que lo ofrece.
    await page.goto('/proveedores')
    await expect(page.getByText('Este módulo está apagado')).toBeVisible()
    await checkAccessibility(page, 'la ruta de un módulo apagado')
    await checkReflow(page, 'la ruta de un módulo apagado')

    // --- 2. Encenderlo desde la pantalla de módulos -------------------------
    await navigateTo(page, 'Módulos del hogar', '/modulos')
    await checkAccessibility(page, 'módulos del hogar')
    await checkTouchTargets(page)

    await page.getByRole('button', { name: /^Encender Proveedores/ }).click()

    // Aparece en la navegación, y su ruta deja de ofrecer la activación.
    await expect(navigation.getByRole('link', { name: 'Proveedores' })).toBeVisible()
    await navigateTo(page, 'Proveedores', '/proveedores')
    await expect(page.getByText('Este módulo está apagado')).toHaveCount(0)

    // La API ya no la corta el gate. Responde `404` porque el módulo todavía no
    // tiene controlador, y esa es exactamente la diferencia que se quería ver:
    // apagado dice «actívalo», encendido dice «eso no existe».
    const opened = await request.get('/api/v1/suppliers', { headers: { Authorization: `Bearer ${token}` } })
    expect(opened.status(), 'el gate sigue cortando un módulo ya encendido').not.toBe(403)

    // --- 3. Apagarlo otra vez ----------------------------------------------
    await navigateTo(page, 'Módulos del hogar', '/modulos')
    await page.getByRole('button', { name: /^Apagar Proveedores/ }).click()

    await expect(navigation.getByRole('link', { name: 'Proveedores' })).toHaveCount(0)
    await page.goto('/proveedores')
    await expect(page.getByText('Este módulo está apagado')).toBeVisible()
  })
})

/** Un access token de verdad, por el mismo camino por el que lo obtiene el cliente. */
async function accessToken(
  request: APIRequestContext,
  email: string,
  password: string,
): Promise<string> {
  const response = await request.post('/api/v1/auth/login', { data: { email, password } })
  expect(response.ok(), 'no se pudo abrir sesión por la API').toBe(true)
  return ((await response.json()) as { accessToken: string }).accessToken
}

/**
 * Que toda parada de la navegación siga siendo pulsable con el pulgar en el
 * ancho más estrecho del rango.
 *
 * Es lo que obligó a reorganizar la navegación en la Fase 2, y es una medida y
 * no una opinión: la dirección visual exige 44 px de objetivo mínimo, y con las
 * ocho paradas que había a 320 px salían **40 px de ancho** cada una. Cuatro
 * módulos lo habrían llevado a doce. Sin esta comprobación, el mismo defecto
 * vuelve con el módulo siguiente y nadie se entera mirando la pantalla.
 */
async function checkTouchTargets(page: Page) {
  await page.setViewportSize({ width: 320, height: 640 })

  const stops = page.getByRole('navigation', { name: 'Principal' }).getByRole('link')
  const total = await stops.count()
  expect(total, 'la navegación principal se quedó sin paradas').toBeGreaterThan(0)

  const measured: Array<{ label: string; width: number; height: number }> = []

  for (let index = 0; index < total; index++) {
    const stop = stops.nth(index)
    // Solo las que se ven: desde `md` la barra lateral enseña más paradas, y en
    // móvil esas están con `display:none` y no las pulsa nadie.
    if (!(await stop.isVisible())) continue

    const box = await stop.boundingBox()
    measured.push({
      label: (await stop.textContent())?.trim() ?? `parada ${index}`,
      width: box?.width ?? 0,
      height: box?.height ?? 0,
    })
  }

  // El reparto entero en el mensaje, y no solo la parada culpable: lo que falla
  // aquí casi nunca es esa parada sino **cómo se repartió el ancho**, y saber
  // cuánto se llevó cada una es la diferencia entre arreglarlo y adivinarlo.
  const layout = measured.map((stop) => `${stop.label} ${stop.width.toFixed(1)}`).join(' · ')

  for (const stop of measured) {
    expect(stop.width, `«${stop.label}» a 320 px. Reparto: ${layout}`).toBeGreaterThanOrEqual(44)
    expect(stop.height, `«${stop.label}» mide menos de 44 px de alto a 320 px`).toBeGreaterThanOrEqual(44)
  }

  // Y la otra mitad de la misma garantía: que darle a cada parada su suelo de
  // 44 px no haya empujado la barra fuera de la pantalla. Las dos van juntas
  // porque se compran una a costa de la otra, y comprobar solo una deja pasar
  // el arreglo que rompe la contraria.
  const overflows = await page.evaluate(
    () => document.documentElement.scrollWidth > document.documentElement.clientWidth,
  )
  expect(overflows, `la navegación desborda a 320 px. Reparto: ${layout}`).toBe(false)

  await page.setViewportSize(DESKTOP)
}

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
 * La auditoría automática de accesibilidad sobre la pantalla montada, **en los
 * dos modos**.
 *
 * Es la mitad que [`check-contrast.py`](../../scripts/check-contrast.py) no
 * puede hacer: aquel comprueba los **tokens** en abstracto y este los comprueba
 * ya aplicados, junto al resto de reglas —nombres accesibles, orden de
 * encabezados, roles— que solo existen sobre un DOM.
 *
 * El modo oscuro se fija con `data-theme` y no con `emulateMedia`, que es
 * justamente para lo que los tokens implementan las dos vías: la preferencia del
 * sistema depende de la máquina que ejecuta la prueba y el atributo no depende
 * de nada. Los 36 pares están medidos en los dos modos, pero medidos **en
 * abstracto**; que un token no se aplique donde debía es un fallo que solo
 * aparece aquí.
 *
 * **Dos esperas que no son ceremonia**, y las dos salieron de un falso positivo
 * y de un falso negativo medidos aquí:
 *
 * - Con el spinner en pantalla, axe recorre cuatro elementos y pasa. Así pasó la
 *   primera versión de esta prueba, y por eso la auditoría de una pantalla que
 *   aún carga no dice nada de la pantalla.
 * - Al cambiar de tema hay 140 ms de `transition-colors` en los que el color es
 *   una **mezcla de los dos modos**, y esa mezcla no está en ningún diseño ni la
 *   mide ningún script. Auditar ahí acusó al botón principal de dar 3,55:1 en
 *   oscuro cuando sus tokens dan 6,77:1: el contraste real de un color que no
 *   existe.
 *
 * Acotado a A y AA, que es el objetivo normativo que fija la ADR-006.
 */
async function checkAccessibility(page: Page, screen: string) {
  await expect(page.locator('.animate-spin'), `«${screen}» todavía estaba cargando`).toHaveCount(0)

  for (const mode of ['claro', 'oscuro']) {
    if (mode === 'oscuro') {
      await page.evaluate(() => document.documentElement.setAttribute('data-theme', 'dark'))
      await settleTransitions(page)
    }

    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze()

    expect(
      results.violations,
      `Accesibilidad en «${screen}» (modo ${mode}): ${describe(results.violations)}`,
    ).toEqual([])
  }

  // La pantalla se queda como estaba: lo que sigue al chequeo es el recorrido, y
  // dejarlo en oscuro haría que una captura de fallo no se pareciera a nada de
  // lo que el usuario vio.
  await page.evaluate(() => document.documentElement.removeAttribute('data-theme'))
  await settleTransitions(page)
}

/**
 * Espera a que no quede ninguna transición a medio camino.
 *
 * Se filtra a `CSSTransition` a propósito: el `Spinner` gira con una animación
 * **infinita**, así que esperar a que *todo* termine no terminaría nunca. Y se
 * espera a la transición y no a un plazo fijo porque un plazo o sobra en la
 * máquina rápida o se queda corto en la lenta, que es cómo se convierte una
 * prueba de contraste en una prueba de la carga de la máquina.
 */
async function settleTransitions(page: Page) {
  await page.waitForFunction(() =>
    document
      .getAnimations()
      .filter((animation) => animation instanceof CSSTransition)
      .every((animation) => animation.playState === 'finished'),
  )
}

/**
 * El reflujo en los tres anchos del rango, sobre la pantalla montada.
 *
 * Mide dos cosas distintas y por eso no basta con una. Abajo, que **nada
 * desborde a lo ancho**: es el fallo de reflujo más común y solo se ve con un
 * navegador de verdad midiendo el documento. Arriba, que el contenido esté
 * **acotado**: en ultrawide no desbordar es gratis —sobra sitio— y el fallo es
 * el contrario, la línea de texto de dos mil píxeles que nadie puede seguir.
 */
async function checkReflow(page: Page, screen: string) {
  const heading = page.getByRole('heading', { level: 1 }).first()

  for (const { name, size } of WIDTHS) {
    await page.setViewportSize(size)
    await expect(heading, `«${screen}» a ${name}: el encabezado dejó de verse`).toBeVisible()

    const overflows = await page.evaluate(
      () => document.documentElement.scrollWidth > document.documentElement.clientWidth,
    )
    expect(overflows, `«${screen}» desborda a lo ancho en ${name}`).toBe(false)
  }

  // El encabezado es un bloque, así que su caja mide lo que mide la columna de
  // contenido: si él está acotado, lo está el texto que va debajo.
  const width = (await heading.boundingBox())?.width ?? 0
  expect(width, `«${screen}»: el contenido se estira sin tope en ultrawide`).toBeLessThanOrEqual(SHELL_CAP)

  await page.setViewportSize(DESKTOP)
  await expect(heading).toBeVisible()
}

/**
 * Deja la pantalla como al abrirla, con el foco al principio del documento.
 *
 * Hace falta porque el recorrido llega hasta aquí haciendo clic, y el clic deja
 * el tabulador continuando desde donde cayó: sin esto, la prueba no diría nada
 * de las paradas anteriores, que son las que se rompen.
 *
 * **Recarga, y no `blur()`.** Fue lo primero que se probó y no vale: `blur()`
 * quita el foco pero no mueve el *punto de partida de la navegación secuencial*,
 * que el navegador mantiene donde estuvo el último elemento enfocado. El
 * síntoma es exacto —el primer tabulador no llega al salto al contenido— y la
 * causa no se parece: parecía que faltaba el salto y lo que fallaba era el
 * punto de partida.
 */
async function startKeyboardAtTop(page: Page, anchor: Locator) {
  await page.reload()
  await expect(anchor, 'la pantalla no volvió a montarse tras recargar').toBeVisible()
}

/**
 * Tabula hasta el destino, comprobando el anillo de foco en **cada** parada.
 *
 * Que se llegue no basta: lo que la ADR-006 exige es que en el camino se vea
 * siempre dónde está uno. Y comprobarlo parada a parada es lo que convierte esto
 * en una prueba de la regla —el `:focus-visible` de la capa base— y no de un
 * componente concreto.
 */
async function tabTo(page: Page, target: Locator, label: string, limit = 40) {
  for (let stop = 1; stop <= limit; stop++) {
    await page.keyboard.press('Tab')
    await expectFocusRing(page, `${label}, parada ${stop}`)

    if (await target.evaluate((node) => node === document.activeElement)) return
  }

  throw new Error(`No se llegó a «${label}» con el tabulador en ${limit} paradas`)
}

/** El contorno de foco del elemento que lo tiene ahora, medido ya aplicado. */
async function expectFocusRing(page: Page, where: string) {
  const ring = await page.evaluate(() => {
    const node = document.activeElement
    if (!node || node === document.body) return null

    const style = getComputedStyle(node)
    return { style: style.outlineStyle, width: Number.parseFloat(style.outlineWidth) }
  })

  expect(ring, `${where}: el tabulador se salió de la página`).not.toBeNull()
  expect(ring?.style, `${where}: el elemento enfocado no dibuja contorno`).not.toBe('none')
  expect(ring?.width ?? 0, `${where}: contorno de foco de menos de 2 px`).toBeGreaterThanOrEqual(2)
}

function describe(violations: Array<{ id: string; help: string; nodes: unknown[] }>): string {
  if (violations.length === 0) return 'ninguna'
  return violations.map((v) => `${v.id} (${v.nodes.length}): ${v.help}`).join('; ')
}
