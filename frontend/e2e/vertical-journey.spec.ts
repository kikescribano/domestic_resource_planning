import AxeBuilder from '@axe-core/playwright'
import { expect, test, type APIRequestContext, type Locator, type Page } from '@playwright/test'
import { fileURLToPath } from 'node:url'

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

/**
 * La foto HEIC de prueba, que es **un HEIC de verdad y no un JPEG renombrado**.
 *
 * Vive en `src/test/fixtures/` y no aquí porque la comparten los dos
 * corredores: `heic.test.ts` mide con ella la detección por bytes y este
 * recorrido, la conversión entera. Su procedencia y cómo se rehace están en el
 * [`README`](../src/test/fixtures/README.md) de ese directorio — hace falta
 * porque nada de este repositorio sabe escribir un HEIC.
 */
const HEIC_FIXTURE = fileURLToPath(new URL('../src/test/fixtures/photo-with-gps.heic', import.meta.url))

/**
 * Un PDF mínimo, escrito aquí y no versionado como fichero.
 *
 * A diferencia del HEIC, este **sí** se puede fabricar: son cinco objetos y una
 * cabecera, y el servidor no lo decodifica —de un `application/pdf` la firma de
 * los primeros bytes es toda la comprobación que hay (5.8.3)—, así que meterlo en
 * `fixtures/` sería versionar un binario que cabe en seis líneas.
 *
 * Su papel es dar a la rejilla **una celda sin miniatura**, que es el caso que se
 * quedaba sin nombre accesible.
 */
const MINIMAL_PDF = Buffer.from(
  [
    '%PDF-1.4',
    '1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj',
    '2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj',
    '3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj',
    'trailer<</Root 1 0 R>>',
    '%%EOF',
  ].join('\n'),
  'latin1',
)

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
    // El estado de conservación, que nace aquí y llega hasta la pantalla que se
    // abre sin sesión. Es la mitad del hito que solo se puede comprobar de punta
    // a punta: el desplegable, el `POST`, la columna y la lectura de vuelta.
    await page.getByLabel('Estado de conservación (opcional)').selectOption('GOOD')
    await page.getByRole('button', { name: 'Dar de alta' }).click()
    // A la ficha del asset recién creado, y **esperando a que llegue**. El alta
    // navega desde el `onSuccess` de la mutación, así que salir de aquí antes de
    // que termine deja una navegación pendiente que se ejecuta después y devuelve
    // al usuario a esta pantalla desde donde estuviera. Costó un rato de
    // diagnóstico: la URL era la correcta un instante y la pantalla, otra.
    await page.waitForURL('**/inventario/*')
    await expect(page.getByRole('heading', { level: 1, name: 'Taladro' })).toBeVisible()
    await expect(page.getByText('Buen estado').first()).toBeVisible()

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
    await page.getByLabel('En qué estado sale (opcional)').selectOption('GOOD')
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
    // En qué estado salió de casa: lo ve quien lo tiene, y no dice nada del
    // hogar que se lo prestó.
    await expect(stranger.getByText('Salió')).toBeVisible()

    const confirmar = stranger.getByRole('button', { name: 'Ya lo he devuelto' })
    await startKeyboardAtTop(stranger, confirmar)

    // Y en qué estado lo devuelve, que es **la única escritura que alcanza esta
    // credencial**. Se elige con el teclado y **después de recargar**, que es lo
    // que hace `startKeyboardAtTop`: elegirlo antes lo perdería, y la prueba
    // pasaría por el camino de no anotar nada sin que se notase.
    const volviendo = stranger.getByLabel('En qué estado lo devuelves')
    await tabTo(stranger, volviendo, 'el estado en el que se devuelve')
    await volviendo.selectOption('DAMAGED')

    await tabTo(stranger, confirmar, 'la confirmación de devolución')
    await stranger.keyboard.press('Enter')

    await expect(stranger.getByText(/ya está cerrado/)).toBeVisible()
    // Lo que acaba de escribir, devuelto para que lo vea hecho.
    await expect(stranger.getByText('Volvió')).toBeVisible()
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
    // **El asset no se entera de lo que dijo el vecino**, y eso es la decisión y
    // no un olvido: lo que se afirma al devolver es del préstamo, y el estado de
    // conservación de la ficha lo corrige el hogar. Sigue como lo dejó el alta.
    //
    // Dentro de la lista de datos y no en la pantalla entera: las cinco etiquetas
    // de la escala están también en el desplegable que hay más abajo para
    // corregirla, así que buscarlas sueltas encontraría siempre las dos.
    const ficha = page.locator('dl').first()
    await expect(ficha.getByText('Buen estado')).toBeVisible()
    await expect(ficha.getByText('Deteriorado')).toHaveCount(0)

    // Y en el préstamo cerrado sí están las dos, que es de donde sale «volvió
    // peor de lo que salió».
    await navigateTo(page, 'Préstamos', '/prestamos')
    await page.getByRole('button', { name: 'Todos' }).click()
    await expect(page.getByText('Salió: Buen estado · Volvió: Deteriorado')).toBeVisible()

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
   * enciende es **Proveedores**, y lo que se comprueba aquí sigue siendo **el
   * mecanismo y no el módulo**: lo suyo tiene su propio recorrido, más abajo.
   *
   * La otra mitad del ciclo —que los datos del módulo siguen ahí al reactivarlo—
   * se comprueba en el backend, que puede mirar la fila. Aquí lo que se ve es lo
   * que ve una persona.
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

    // La API ya no la corta el gate, y desde el Hito 2 **responde de verdad**:
    // hasta entonces esto daba `404` porque no había controlador detrás, así que
    // el gate nunca había tapado nada que existiera. Esa es la diferencia entera
    // que se quería ver aquí — apagado dice «actívalo», encendido contesta.
    const opened = await request.get('/api/v1/suppliers', { headers: { Authorization: `Bearer ${token}` } })
    expect(opened.status(), 'el gate sigue cortando un módulo ya encendido').toBe(200)

    // --- 3. Apagarlo otra vez ----------------------------------------------
    await navigateTo(page, 'Módulos del hogar', '/modulos')
    await page.getByRole('button', { name: /^Apagar Proveedores/ }).click()

    await expect(navigation.getByRole('link', { name: 'Proveedores' })).toHaveCount(0)
    await page.goto('/proveedores')
    await expect(page.getByText('Este módulo está apagado')).toBeVisible()
  })

  /**
   * El recorrido del **primer módulo con dominio**, de encenderlo a enlazar un
   * contacto con un sitio de la casa.
   *
   * Va aquí y no en una suite propia a propósito: la batería del recorrido
   * vertical es una y los módulos se añaden a ella, que es lo que la ADR-001
   * pide y lo que evita que cada módulo estrene su forma de comprobar lo mismo.
   *
   * Lo que solo se puede ver aquí son cuatro cosas: que la pantalla del módulo
   * **se monta dentro de su guardián** —que hasta este hito no sabía enseñar
   * nada—, que el enlace con una ubicación del core atraviesa las cinco capas
   * hasta PostgreSQL y vuelve **con el nombre resuelto**, que la parada nueva no
   * estropea la navegación a 320 px, y que todo eso se hace con el teclado y pasa
   * axe en los dos modos.
   */
  test('el módulo de Proveedores: encenderlo, dar de alta un contacto y enlazarlo con un sitio', async ({
    page,
  }) => {
    const email = `casa-${Date.now()}@example.test`
    const password = 'el gato duerme en el sofa'

    await page.goto('/crear-hogar')
    await page.getByLabel('Nombre del hogar').fill('Casa de los Proveedores')
    await page.getByLabel('Tu nombre').fill('Kike')
    await page.getByLabel('Correo').fill(email)
    await page.getByLabel('Contraseña', { exact: true }).fill(password)
    await page.getByRole('button', { name: /crear/i }).click()
    await page.goto(await linkFromEmail(email))
    await expect(page.getByRole('heading', { level: 1, name: 'Tu hogar' })).toBeVisible()

    // --- 1. Un sitio de la casa, que es con lo que se va a enlazar ----------
    await navigateTo(page, 'Sitios', '/ubicaciones')
    await page.getByLabel('Nombre').fill('Sala de calderas')
    await page.getByLabel('Tipo').selectOption({ label: 'Habitación' })
    await page.getByRole('button', { name: 'Crear ubicación' }).click()
    // Por el árbol y no por el texto suelto: el nombre aparece también dentro
    // del `<option>` de «Dentro de», que está en el DOM y no se ve.
    await expect(
      page.getByRole('tree', { name: 'Ubicaciones del hogar' }).getByText('Sala de calderas'),
    ).toBeVisible()

    // --- 2. Encender el módulo, con el teclado ------------------------------
    // Con el tabulador y desde el principio del documento: encender un módulo es
    // la puerta de entrada a todo lo demás, y si no se llega a ella sin ratón,
    // para quien navega así el módulo no existe.
    await navigateTo(page, 'Módulos del hogar', '/modulos')
    const encender = page.getByRole('button', { name: /^Encender Proveedores/ })
    await startKeyboardAtTop(page, encender)
    await tabTo(page, encender, 'encender Proveedores')
    await page.keyboard.press('Enter')

    // --- 3. Su pantalla, dentro del guardián --------------------------------
    await navigateTo(page, 'Proveedores', '/proveedores')
    await expect(page.getByRole('heading', { level: 1, name: 'Proveedores' })).toBeVisible()
    // El vacío de un módulo recién encendido es el vacío **de verdad**: su
    // siembra está vacía a propósito, porque en el core no hay ningún fontanero
    // que heredar.
    await expect(page.getByText('Todavía no hay ningún contacto')).toBeVisible()
    await checkAccessibility(page, 'proveedores, sin nada todavía')

    // --- 4. Dar de alta un contacto -----------------------------------------
    await page.getByRole('button', { name: 'Añadir contacto' }).click()
    await page.getByLabel('Nombre').fill('Servicio Técnico Caldera')
    await page.getByLabel('Categoría de servicio').selectOption('HEATING_COOLING')
    await page.getByLabel('Teléfono').fill('900 100 100')
    await page.getByRole('button', { name: 'Guardar contacto' }).click()

    await expect(page.getByRole('button', { name: /Servicio Técnico Caldera/ })).toBeVisible()

    // --- 5. Enlazarlo con el sitio, y ver el nombre resuelto ----------------
    await page.getByRole('button', { name: /Servicio Técnico Caldera/ }).click()
    await expect(page.getByText('Todavía no está enlazado con nada.')).toBeVisible()

    await page.getByRole('button', { name: /^Enlazar Servicio Técnico Caldera/ }).click()
    await page
      .getByLabel('Sitio que atiende Servicio Técnico Caldera')
      .selectOption({ label: 'Sala de calderas' })
    await page.getByRole('button', { name: 'Enlazar', exact: true }).click()

    // El nombre no lo guarda el módulo: lo resuelve el servidor contra el core al
    // leer. Que aparezca aquí es la vuelta entera cerrada —pantalla, API, caso de
    // uso, dos tablas de dos árboles distintos y de vuelta.
    //
    // Se busca por el botón de quitar, cuyo nombre accesible lleva el del
    // destino: es lo único que solo puede venir de un enlace ya guardado, y no
    // se confunde con la opción del desplegable.
    await expect(page.getByRole('button', { name: 'Quitar Sala de calderas' })).toBeVisible()

    // --- 6. Y lo que solo se mide en un navegador ---------------------------
    await checkAccessibility(page, 'proveedores, con un contacto enlazado')
    await checkReflow(page, 'proveedores')
    // La parada nueva no roba sitio en el pulgar: entra en el grupo de módulos,
    // que en móvil vive dentro de «Más». Medirlo es parte del hito y no un extra
    // — es exactamente el defecto que obligó a reorganizar la navegación.
    await checkTouchTargets(page)
  })

  /**
   * El recorrido del **módulo que reacciona al core**, de encenderlo sobre una
   * despensa que ya existía a apuntar un consumo y verlo en el cuaderno.
   *
   * Va aquí y no en una suite propia a propósito, igual que el de Proveedores:
   * la batería del recorrido vertical es una y los módulos se añaden a ella, que
   * es lo que la ADR-001 pide y lo que evita que cada módulo estrene su forma de
   * comprobar lo mismo.
   *
   * Lo que solo se puede ver aquí son cinco cosas:
   *
   *  1. Que **la siembra enseña lo que ya había**: se da entrada a un consumible
   *     ANTES de encender el módulo, y al encenderlo aparece. Eso es la regla de
   *     la ADR-010 —sembrar desde el estado y no reproduciendo eventos— vista
   *     desde la pantalla.
   *  2. Que **apuntar un consumo mueve el contador del core**: la cifra que baja
   *     en el almacén es la misma que se lee en el inventario, porque no hay dos.
   *  3. Que el `Combobox` que este hito estrena **se usa con el teclado**.
   *  4. Que la parada nueva no estropea la navegación a 320 px.
   *  5. Que todo eso pasa axe en los dos modos.
   */
  test('el módulo Almacén: sembrarlo con lo que ya había, apuntar un consumo y anotar una caducidad', async ({
    page,
  }) => {
    const email = `almacen-${Date.now()}@example.test`
    const password = 'el gato duerme en el sofa'

    await page.goto('/crear-hogar')
    await page.getByLabel('Nombre del hogar').fill('Casa del Almacén')
    await page.getByLabel('Tu nombre').fill('Kike')
    await page.getByLabel('Correo').fill(email)
    await page.getByLabel('Contraseña', { exact: true }).fill(password)
    await page.getByRole('button', { name: /crear/i }).click()
    await page.goto(await linkFromEmail(email))
    await expect(page.getByRole('heading', { level: 1, name: 'Tu hogar' })).toBeVisible()

    // --- 1. Una despensa con algo dentro, ANTES de encender el módulo -------
    await navigateTo(page, 'Sitios', '/ubicaciones')
    await page.getByLabel('Nombre').fill('Despensa')
    await page.getByLabel('Tipo').selectOption({ label: 'Habitación' })
    await page.getByRole('button', { name: 'Crear ubicación' }).click()
    await expect(
      page.getByRole('tree', { name: 'Ubicaciones del hogar' }).getByText('Despensa'),
    ).toBeVisible()

    await navigateTo(page, 'Catálogo', '/catalogo')
    await page.getByLabel('Nombre del artículo').fill('Arroz')
    await page.getByLabel('Categoría').selectOption({ label: 'Alimentación' })
    // `exact` porque el catálogo tiene ahora dos campos cuyo nombre accesible
    // contiene «unidad»: la unidad del artículo y su peso por unidad, que llegó
    // con este mismo hito. Para una persona son distinguibles —los oye enteros—;
    // para una búsqueda por subcadena, no.
    await page.getByLabel('Unidad', { exact: true }).selectOption('GRAM')
    await page.getByRole('button', { name: 'Crear artículo' }).click()
    await expect(page.getByText('Arroz')).toBeVisible()

    await page.goto('/inventario/entrada')
    await page.getByLabel('Artículo').selectOption({ label: 'Arroz' })
    await page.getByLabel('Dónde se guarda').selectOption({ label: 'Despensa' })
    await page.getByLabel(/Cantidad que entra/).fill('900')
    await page.getByRole('button', { name: 'Dar entrada' }).click()
    await expect(page.getByText('Primera existencia creada')).toBeVisible()

    // --- 2. Encender el módulo, con el teclado ------------------------------
    await navigateTo(page, 'Módulos del hogar', '/modulos')
    const encender = page.getByRole('button', { name: /^Encender Almacén/ })
    await startKeyboardAtTop(page, encender)
    await tabTo(page, encender, 'encender Almacén')
    await page.keyboard.press('Enter')

    // --- 3. La siembra: lo que ya había, ahí dentro -------------------------
    await navigateTo(page, 'Almacén', '/almacen')
    await expect(page.getByRole('heading', { level: 1, name: 'Almacén' })).toBeVisible()
    // **No** está vacío, y esa es la diferencia con Proveedores: la siembra de
    // este módulo lee el estado del core, así que los 900 g que entraron antes de
    // encenderlo están aquí sin que ningún evento se haya reproducido.
    await expect(page.getByText('El almacén está vacío')).toHaveCount(0)
    // `/^Arroz/` y no `/Arroz/`: la ficha desplegada trae un botón «Anotar una
    // caducidad de Arroz», y el nombre del artículo aparece dentro. La fila es la
    // que **empieza** por él.
    const fila = page.getByRole('button', { name: /^Arroz/ })
    await expect(fila).toBeVisible()
    await expect(fila).toContainText('900')
    await checkAccessibility(page, 'almacén, recién sembrado')

    // --- 4. El Combobox, con el teclado -------------------------------------
    // Es el primitivo que este hito estrena, después de quedar aplazado desde el
    // Hito 2 de la Fase 1. Lo que aquí se mide y ninguna prueba de componente
    // puede medir es que **el anillo de foco se ve** al llegar a él.
    const buscar = page.getByRole('combobox', { name: 'Buscar' })
    await startKeyboardAtTop(page, buscar)
    await tabTo(page, buscar, 'la búsqueda del almacén')
    await expectFocusRing(page, 'la búsqueda del almacén')
    await page.keyboard.press('ArrowDown')
    await page.keyboard.press('Enter')
    await expect(buscar).toHaveValue('Arroz')

    // --- 5. Apuntar un consumo, que mueve el contador DEL CORE --------------
    await page.getByRole('button', { name: /^Arroz/ }).click()
    await page.getByLabel(/Gastado de Arroz/).fill('250')
    await page.getByRole('button', { name: 'Apuntar consumo' }).click()

    // La cifra baja aquí...
    await expect(page.getByRole('button', { name: /^Arroz/ })).toContainText('650')

    // ...y **la misma cifra** se lee en el inventario del core, que es lo único
    // que demuestra que no hay dos contadores: el almacén no guarda cantidad, la
    // lee. Esta es la vuelta entera cerrada —pantalla, API del módulo, caso de
    // uso del core, evento, manejador, dos árboles de tablas y de vuelta.
    await navigateTo(page, 'Inventario', '/inventario')
    await expect(page.getByText('650')).toBeVisible()

    // --- 6. Anotar una caducidad -------------------------------------------
    await navigateTo(page, 'Almacén', '/almacen')
    await page.getByRole('button', { name: /^Arroz/ }).click()
    await page.getByRole('button', { name: /^Anotar una caducidad/ }).click()
    await page.getByLabel('Caduca el').fill(inDays(20))
    await page.getByLabel(/Cuánto caduca/).fill('300')
    await page.getByRole('button', { name: 'Guardar caducidad' }).click()

    await expect(page.getByRole('button', { name: /^Descartar el lote que caduca/ })).toBeVisible()

    // --- 7. Y lo que solo se mide en un navegador ---------------------------
    await checkAccessibility(page, 'almacén, con consumo y caducidad')
    await checkReflow(page, 'almacén')
    // La parada nueva no roba sitio en el pulgar: entra en el grupo de módulos,
    // que en móvil vive dentro de «Más».
    await checkTouchTargets(page)
  })

  /**
   * El recorrido del módulo que **cierra el ciclo**, y con él el riesgo
   * arquitectónico principal de la fase: dos módulos que se hablan sin depender
   * uno de que el otro esté activo.
   *
   * Va aquí y no en una suite propia a propósito, igual que los dos anteriores:
   * la batería del recorrido vertical es una y los módulos se añaden a ella.
   *
   * Lo que solo se puede ver aquí son cuatro cosas:
   *
   *  1. Que **la lista sirve con el almacén apagado**: se apunta a mano, con el
   *     teclado y con su anillo de foco. Es la mitad de la frontera que ninguna
   *     prueba de backend enseña en pantalla.
   *  2. Que **con Proveedores apagado el selector de dónde se compra no está**, y
   *     la pantalla no explica nada ni falla: la degradación la puso el servidor.
   *  3. Que **el ciclo se cierra de verdad**: recibir la compra suma sobre la
   *     existencia que ya había, y **la misma cifra** se lee en el inventario del
   *     core, que es lo único que demuestra que no se ha creado una segunda.
   *  4. Que todo eso pasa axe en los dos modos, refluye y no roba sitio en el
   *     pulgar.
   *
   * **La siembra no se mide aquí y sí en la batería del backend**, que es donde
   * cabe: enseñarla exige dejar un consumible a cero antes de encender el módulo,
   * y lo que eso probaría de más en un navegador —que la lista pinta una línea—
   * ya lo prueban los pasos de abajo.
   */
  test('el módulo Compras: sembrarlo con lo que falta, apuntar a mano y cerrar el ciclo al recibir', async ({
    page,
  }) => {
    const email = `compras-${Date.now()}@example.test`
    const password = 'el gato duerme en el sofa'

    await page.goto('/crear-hogar')
    await page.getByLabel('Nombre del hogar').fill('Casa de las Compras')
    await page.getByLabel('Tu nombre').fill('Kike')
    await page.getByLabel('Correo').fill(email)
    await page.getByLabel('Contraseña', { exact: true }).fill(password)
    await page.getByRole('button', { name: /crear/i }).click()
    await page.goto(await linkFromEmail(email))
    await expect(page.getByRole('heading', { level: 1, name: 'Tu hogar' })).toBeVisible()

    // --- 1. Una despensa con algo agotado, ANTES de encender el módulo ------
    await navigateTo(page, 'Sitios', '/ubicaciones')
    await page.getByLabel('Nombre').fill('Despensa')
    await page.getByLabel('Tipo').selectOption({ label: 'Habitación' })
    await page.getByRole('button', { name: 'Crear ubicación' }).click()
    await expect(
      page.getByRole('tree', { name: 'Ubicaciones del hogar' }).getByText('Despensa'),
    ).toBeVisible()

    await navigateTo(page, 'Catálogo', '/catalogo')
    await page.getByLabel('Nombre del artículo').fill('Sal')
    await page.getByLabel('Categoría').selectOption({ label: 'Alimentación' })
    await page.getByLabel('Unidad', { exact: true }).selectOption('GRAM')
    await page.getByRole('button', { name: 'Crear artículo' }).click()
    await expect(page.getByText('Sal')).toBeVisible()

    await page.goto('/inventario/entrada')
    await page.getByLabel('Artículo').selectOption({ label: 'Sal' })
    await page.getByLabel('Dónde se guarda').selectOption({ label: 'Despensa' })
    await page.getByLabel(/Cantidad que entra/).fill('500')
    await page.getByRole('button', { name: 'Dar entrada' }).click()
    await expect(page.getByText('Primera existencia creada')).toBeVisible()

    // --- 2. Encender el módulo, con el teclado ------------------------------
    await navigateTo(page, 'Módulos del hogar', '/modulos')
    const encender = page.getByRole('button', { name: /^Encender Compras/ })
    await startKeyboardAtTop(page, encender)
    await tabTo(page, encender, 'encender Compras')
    await page.keyboard.press('Enter')

    // --- 3. La lista, y lo que la siembra pudo leer -------------------------
    await navigateTo(page, 'Compras', '/compras')
    await expect(page.getByRole('heading', { level: 1, name: 'Compras' })).toBeVisible()

    // --- 4. Con Proveedores apagado, no hay dónde elegir --------------------
    // Y la pantalla no lo explica ni falla: el servidor devolvió una lista vacía
    // en lugar de un 403, así que aquí simplemente no hay campo.
    await expect(page.getByLabel('Dónde vas a comprar')).toHaveCount(0)
    await checkAccessibility(page, 'compras, recién encendido')

    // --- 5. Apuntar a mano, que es lo que sirve sin el almacén --------------
    // Es la mitad de la frontera que ninguna prueba de backend enseña en
    // pantalla: sin ese módulo nadie detecta la falta, y la lista se llena así.
    const apuntar = page.getByRole('combobox', { name: 'Qué hace falta' })
    await startKeyboardAtTop(page, apuntar)
    await tabTo(page, apuntar, 'apuntar en la lista de la compra')
    await expectFocusRing(page, 'apuntar en la lista de la compra')
    await apuntar.fill('Sal')
    // **Esperar a que la sugerencia esté antes de elegirla con Enter**, y no es
    // ceremonia: las opciones salen de una consulta al servidor, y con la lista
    // todavía vacía `Enter` no elige nada --no hay opción resaltada-- y llega al
    // formulario, que se envía con el texto suelto en lugar de con el artículo.
    // El síntoma es una línea que se compra y no entra en el inventario, que no
    // se parece nada a la causa.
    await expect(page.getByRole('option', { name: /^Sal/ })).toBeVisible()
    await page.keyboard.press('ArrowDown')
    await page.keyboard.press('Enter')
    await page.getByLabel('Cuánta', { exact: true }).fill('250')
    await page.getByRole('button', { name: 'Apuntar' }).click()
    await expect(page.getByText('Apuntado a mano')).toBeVisible()

    // --- 6. Abrir la compra con lo que se lleva -----------------------------
    await page.getByRole('checkbox').first().check()
    await page.getByRole('button', { name: /^Me llevo/ }).click()

    await page.getByRole('tab', { name: 'Las compras' }).click()
    await page.getByRole('button', { name: /Sin decir dónde/ }).click()

    // --- 7. Recibirla: el ciclo se cierra -----------------------------------
    // Sin decir dónde, va donde ese artículo ya estaba —que es lo que impide que
    // la despensa acabe con dos sales.
    await page.getByRole('button', { name: 'Ya está en casa' }).click()
    // `exact` porque «en el inventario» aparece también en el párrafo de arriba y
    // en el aviso de las líneas de texto suelto. Lo que se busca es la etiqueta
    // de estado, que dice exactamente esto y solo aparece cuando la línea acabó
    // en una existencia del core.
    await expect(page.getByText('En el inventario', { exact: true })).toBeVisible()

    // Y **la misma existencia** en el inventario del core, con los 500 que había
    // más los 250 que acaban de entrar. Esta es la vuelta entera cerrada:
    // pantalla, API del módulo, caso de uso del CORE que suma sobre la existencia
    // de esa ubicación, y de vuelta.
    await navigateTo(page, 'Inventario', '/inventario')
    await expect(page.getByText('750')).toBeVisible()

    // --- 8. Y lo que solo se mide en un navegador ---------------------------
    await navigateTo(page, 'Compras', '/compras')
    await checkAccessibility(page, 'compras, con una compra recibida')
    await checkReflow(page, 'compras')
    // La parada nueva no roba sitio en el pulgar: entra en el grupo de módulos,
    // que en móvil vive dentro de «Más».
    await checkTouchTargets(page)
  })

  test('el módulo Mantenimiento: la máquina que ya había, su plan, y la fecha que avanza al registrarlo', async ({
    page,
    request,
    browser,
  }) => {
    const email = `mantenimiento-${Date.now()}@example.test`
    const password = 'el gato duerme en el sofa'

    await page.goto('/crear-hogar')
    await page.getByLabel('Nombre del hogar').fill('Casa del Mantenimiento')
    await page.getByLabel('Tu nombre').fill('Kike')
    await page.getByLabel('Correo').fill(email)
    await page.getByLabel('Contraseña', { exact: true }).fill(password)
    await page.getByRole('button', { name: /crear/i }).click()
    await page.goto(await linkFromEmail(email))
    await expect(page.getByRole('heading', { level: 1, name: 'Tu hogar' })).toBeVisible()

    // --- 1. Una caldera, ANTES de encender el módulo -----------------------
    // Es lo que hace que la siembra tenga algo que leer: un módulo activado hoy
    // no vio el `AssetCreated` de hace un mes, así que se siembra desde el
    // estado del core y no reproduciendo eventos.
    await navigateTo(page, 'Inventario', '/inventario')
    // Por el enlace de la pantalla y no con `page.goto`: una recarga a estas
    // alturas puede pillar en vuelo la rotacion del refresh token --el que
    // dispara la primera consulta del inventario-- y reanudar con uno ya gastado,
    // que devuelve al login. Navegar dentro de la SPA no recarga nada.
    await page.getByRole('link', { name: 'Dar de alta' }).click()
    await page.waitForURL('**/inventario/nuevo')
    await page.getByLabel('Nombre').fill('Caldera')
    await page.getByLabel('Categoría').selectOption({ label: 'Herramientas' })
    await page.getByRole('button', { name: 'Dar de alta' }).click()
    await expect(page.getByRole('heading', { level: 1, name: 'Caldera' })).toBeVisible()

    // --- 2. Encender el módulo, con el teclado ------------------------------
    await navigateTo(page, 'Módulos del hogar', '/modulos')
    const encender = page.getByRole('button', { name: /^Encender Mantenimiento/ })
    await startKeyboardAtTop(page, encender)
    await tabTo(page, encender, 'encender Mantenimiento')
    await page.keyboard.press('Enter')

    // --- 3. La máquina que la siembra encontró, y SIN ningún plan -----------
    // Es la decisión de este hito hecha pantalla: el catálogo de eventos daba
    // por hecho un «plan por defecto» que no se sostiene —una caldera pide
    // revisión anual y una silla no pide nada—, así que lo que se abre es la
    // ficha y el plan lo pone quien sabe si su caldera es de gas.
    await navigateTo(page, 'Mantenimiento', '/mantenimiento')
    await expect(page.getByRole('heading', { level: 1, name: 'Mantenimiento' })).toBeVisible()

    await page.getByRole('tab', { name: 'Las máquinas' }).click()
    // `exact` porque los dos párrafos de arriba explican el módulo con una
    // caldera de ejemplo: para una persona son texto corrido y para una búsqueda
    // por subcadena, tres coincidencias.
    await expect(page.getByText('Caldera', { exact: true })).toBeVisible()
    await expect(page.getByText('Sin planes')).toBeVisible()

    // --- 4. Con Proveedores apagado, no hay a quién llamar ------------------
    // Y la pantalla no lo explica ni falla: el servidor devolvió una lista vacía
    // en lugar de un 403, así que aquí simplemente no hay campo.
    await page.getByRole('tab', { name: 'Qué toca' }).click()
    await expect(page.getByLabel('A quién se llama')).toHaveCount(0)
    await checkAccessibility(page, 'mantenimiento, recién encendido')

    // --- 5. El plan, con el teclado ----------------------------------------
    const maquina = page.getByRole('combobox', { name: 'Qué máquina' })
    await startKeyboardAtTop(page, maquina)
    await tabTo(page, maquina, 'elegir la máquina')
    await expectFocusRing(page, 'elegir la máquina')
    await maquina.fill('Caldera')
    // **Esperar a que la sugerencia esté antes de elegirla con Enter**: las
    // opciones salen de una consulta al servidor, y con la lista todavía vacía
    // `Enter` no elige nada y llega al formulario, que se enviaría sin máquina.
    await expect(page.getByRole('option', { name: /^Caldera/ })).toBeVisible()
    await page.keyboard.press('ArrowDown')
    await page.keyboard.press('Enter')

    await page.getByLabel('Qué se revisa').fill('Revisión anual')
    await page.getByLabel('Cada cuántos meses').fill('12')
    // Dentro de la ventana de antelación por omisión del módulo, que son quince
    // días: así el plan nace ya en «Toca pronto» y se puede ver el estado.
    await page.getByLabel('Cuándo toca la próxima').fill(inDays(5))
    await page.getByRole('button', { name: 'Crear plan' }).click()

    await expect(page.getByText('Revisión anual')).toBeVisible()
    // Con etiqueta y no solo con color, que es la regla 4 de la dirección visual.
    await expect(page.getByText('Toca pronto')).toBeVisible()

    // --- 6. Registrarlo: la fecha avanza y el aviso se rearma --------------
    // **Es lo que este módulo existe para hacer.** En el almacén el rearme era
    // reponer por encima del mínimo, un hecho que ocurre solo; aquí es un gesto
    // de una persona, y esta es la mitad que ninguna prueba de backend enseña en
    // pantalla.
    await page.getByRole('button', { name: 'Ya está hecho' }).click()
    await page.getByLabel('Qué se hizo').fill('Revisada y limpiada')
    await page.getByRole('button', { name: 'Apuntarlo' }).click()

    // La próxima se cuenta desde LO QUE SE HIZO —hoy— y no desde la que tocaba,
    // así que el plan sale de la ventana y vuelve a estar al día.
    await expect(page.getByText('Al día')).toBeVisible()
    await expect(page.getByText(/La última vez fue el/)).toBeVisible()

    // Y queda en el cuaderno, que no se toca.
    await page.getByRole('tab', { name: 'El histórico' }).click()
    await expect(page.getByText('Revisada y limpiada')).toBeVisible()
    await expect(page.getByText('Preventiva')).toBeVisible()

    // --- 7. El día que propone el campo es el DEL HOGAR ---------------------
    // La otra mitad del mismo defecto vive en el backend y tiene allí su prueba
    // con el reloj parado. Lo que solo se puede ver aquí es lo que la persona
    // encuentra: el campo relleno con el día que tiene delante y un tope que la
    // deja elegirlo. Con el día de Greenwich salía **ayer**, que como síntoma es
    // peor que el error —registra el día equivocado en silencio.
    const zonaDelHogar = await householdTimeZone(request, email, password)

    // De vuelta a «Qué toca», que es donde vive el plan: el paso anterior dejó la
    // pantalla en el histórico, y allí no hay ningún botón que abrir.
    await page.getByRole('tab', { name: 'Qué toca' }).click()
    await page.getByRole('button', { name: 'Ya está hecho' }).click()
    const cuando = page.getByLabel('Cuándo se hizo')
    await expect(cuando).toHaveValue(dayIn(zonaDelHogar))
    await expect(cuando).toHaveAttribute('max', dayIn(zonaDelHogar))

    // Y **desde un navegador que está en otro día**, que es lo que distingue «el
    // día del hogar» de «el día de quien mira»: el calendario de la casa no se
    // muda con el móvil. Las dos zonas de abajo van 25 horas la una de la otra,
    // así que alguna de las dos cae siempre en un día distinto al del hogar; se
    // usa la que caiga, y con eso esto no depende de la hora a la que se lance
    // la suite.
    const zonaAjena = [KIRITIMATI, NIUE].find((zona) => dayIn(zona) !== dayIn(zonaDelHogar))
    expect(zonaAjena, 'ninguna de las dos zonas cae en otro día que el hogar').toBeTruthy()

    const lejosContext = await browser.newContext({ timezoneId: zonaAjena })
    const lejos = await lejosContext.newPage()
    await lejos.goto('/entrar')
    await lejos.getByLabel('Correo').fill(email)
    await lejos.getByLabel('Contraseña', { exact: true }).fill(password)
    await lejos.getByRole('button', { name: 'Entrar' }).click()
    await expect(lejos.getByRole('heading', { level: 1, name: 'Tu hogar' })).toBeVisible()

    await navigateTo(lejos, 'Mantenimiento', '/mantenimiento')
    await lejos.getByRole('button', { name: 'Ya está hecho' }).click()
    await expect(lejos.getByLabel('Cuándo se hizo')).toHaveValue(dayIn(zonaDelHogar))
    await expect(lejos.getByLabel('Cuándo se hizo')).not.toHaveValue(dayIn(zonaAjena!))

    await lejosContext.close()

    // --- 8. Y lo que solo se mide en un navegador ---------------------------
    await checkAccessibility(page, 'mantenimiento, con su plan y su histórico')
    await checkReflow(page, 'mantenimiento')
    // La parada nueva no roba sitio en el pulgar: entra en el grupo de módulos,
    // que en móvil vive dentro de «Más».
    await checkTouchTargets(page)
  })

  /**
   * La baja del hogar: pedirla, ver el aviso, cancelarla y comprobar que sigue
   * todo (ADR-012).
   *
   * **Lo que no está aquí es la purga**, y no por descuido: vencer treinta días
   * de gracia exige mover el reloj, y eso se hace donde hay reloj que mover —la
   * batería del backend, con ficheros de verdad en disco—. Aquí está lo que solo
   * se puede ver en un navegador: que la confirmación hay que **escribirla**, que
   * el aviso persiste **en otras pantallas** y no solo en la del hogar, y que
   * cancelar lo deja todo como estaba.
   */
  test('la baja del hogar: pedirla con el nombre escrito, verla en todas partes y cancelarla', async ({
    page,
  }) => {
    const email = `baja-${Date.now()}@example.test`
    const password = 'el gato duerme en el sofa'
    const householdName = 'Casa que se va'

    await page.goto('/crear-hogar')
    await page.getByLabel('Nombre del hogar').fill(householdName)
    await page.getByLabel('Tu nombre').fill('Kike')
    await page.getByLabel('Correo').fill(email)
    await page.getByLabel('Contraseña', { exact: true }).fill(password)
    await page.getByRole('button', { name: /crear/i }).click()
    await page.goto(await linkFromEmail(email))
    await expect(page.getByRole('heading', { level: 1, name: 'Tu hogar' })).toBeVisible()

    // Algo dentro, para poder afirmar después que cancelar no se llevó nada.
    await navigateTo(page, 'Sitios', '/ubicaciones')
    await page.getByLabel('Nombre').fill('Trastero')
    await page.getByLabel('Tipo').selectOption({ label: 'Habitación' })
    await page.getByRole('button', { name: 'Crear ubicación' }).click()
    await expect(
      page.getByRole('tree', { name: 'Ubicaciones del hogar' }).getByText('Trastero'),
    ).toBeVisible()

    // --- 1. La zona de peligro, que no se dispara con un clic ---------------
    await navigateTo(page, 'Hogar', '/', true)
    const confirm = page.getByRole('button', { name: 'Dar de baja el hogar' })
    await expect(confirm).toBeDisabled()

    // Con el nombre a medias sigue sin poder pulsarse: es la diferencia entera
    // entre esto y un «¿seguro?», que se contesta que sí por reflejo.
    const field = page.getByLabel(`Escribe «${householdName}» para confirmarlo`)
    await field.fill('Casa que')
    await expect(confirm).toBeDisabled()

    await field.fill(householdName)
    await expect(confirm).toBeEnabled()
    await confirm.click()

    // --- 2. El aviso, con su fecha y en todas las pantallas -----------------
    const banner = page.getByText(/Este hogar se borrará el/)
    await expect(banner).toBeVisible()

    // Y fuera de la pantalla del hogar, que es lo que de verdad se comprueba
    // aquí: durante la gracia todo sigue igual, así que alguien puede pasarse
    // treinta días en el inventario sin volver a «Tu hogar».
    await navigateTo(page, 'Inventario', '/inventario')
    await expect(page.getByText(/Este hogar se borrará el/)).toBeVisible()

    // El hogar funciona **exactamente igual**: nada de solo lectura, que
    // castigaría justo a quien todavía puede arrepentirse.
    await navigateTo(page, 'Sitios', '/ubicaciones')
    await page.getByLabel('Nombre').fill('Buhardilla')
    await page.getByLabel('Tipo').selectOption({ label: 'Habitación' })
    await page.getByRole('button', { name: 'Crear ubicación' }).click()
    await expect(
      page.getByRole('tree', { name: 'Ubicaciones del hogar' }).getByText('Buhardilla'),
    ).toBeVisible()

    await checkAccessibility(page, 'el hogar con la baja pedida')
    await checkReflow(page, 'el hogar con la baja pedida')

    // --- 3. Cancelarla, y comprobar que sigue todo --------------------------
    await navigateTo(page, 'Hogar', '/', true)
    await page.getByRole('button', { name: 'Cancelar la baja' }).click()

    await expect(page.getByText(/Este hogar se borrará el/)).toHaveCount(0)
    // La zona de peligro vuelve a estar disponible, que es como se ve que la
    // baja se retiró de verdad y no solo el cartel.
    await expect(page.getByRole('button', { name: 'Dar de baja el hogar' })).toBeVisible()

    await navigateTo(page, 'Sitios', '/ubicaciones')
    const tree = page.getByRole('tree', { name: 'Ubicaciones del hogar' })
    await expect(tree.getByText('Trastero')).toBeVisible()
    await expect(tree.getByText('Buhardilla')).toBeVisible()
  })

  /**
   * La conversión de HEIC, con un HEIC de verdad
   * ([ADR-014](../../docs/common/architecture/decisions/ADR-014-heic-conversion.md)).
   *
   * **Este recorrido no se puede escribir en ningún otro sitio**, y por eso está
   * aquí y no en Vitest. El decodificador es un módulo nativo compilado a wasm
   * que corre en un Worker y vuelca los píxeles en un lienzo: `jsdom` no tiene
   * ninguna de las tres cosas, así que allí solo se puede doblar. Lo que se
   * comprueba de verdad —que 95 kB de HEIC entran por un `<input type=file>` y
   * salen en la rejilla como una foto— solo ocurre en un navegador.
   *
   * Va hasta los bytes servidos a propósito. La foto de prueba lleva **352 B de
   * EXIF con coordenadas GPS** dentro, que es el dato más sensible que atraviesa
   * este mecanismo (5.8.3): con una foto sin metadatos, comprobar que no salen
   * no distinguiría entre haberlos borrado y no haber tenido ninguno.
   */
  test('la conversión de HEIC: una foto de iPhone entra, se ve y no saca las coordenadas de casa', async ({
    page,
    request,
  }) => {
    const email = `heic-${Date.now()}@example.test`
    const password = 'el gato duerme en el sofa'

    await page.goto('/crear-hogar')
    await page.getByLabel('Nombre del hogar').fill('Casa con iPhone')
    await page.getByLabel('Tu nombre').fill('Kike')
    await page.getByLabel('Correo').fill(email)
    await page.getByLabel('Contraseña', { exact: true }).fill(password)
    await page.getByRole('button', { name: /crear/i }).click()
    await page.goto(await linkFromEmail(email))
    await expect(page.getByRole('heading', { level: 1, name: 'Tu hogar' })).toBeVisible()

    // --- 1. Una cosa a la que ponerle la foto -------------------------------
    await navigateTo(page, 'Inventario', '/inventario')
    await page.getByRole('link', { name: 'Dar de alta' }).click()
    await page.getByLabel('Nombre').fill('Caldera')
    await page.getByLabel('Categoría').selectOption({ label: 'Herramientas' })
    await page.getByRole('button', { name: 'Dar de alta' }).click()
    await page.waitForURL('**/inventario/*')
    await expect(page.getByRole('heading', { level: 1, name: 'Caldera' })).toBeVisible()

    // --- 2. El HEIC, por donde lo metería una persona -----------------------
    // Antes de este hito, esto terminaba en un `415` que enumeraba cuatro tipos
    // que quien hizo la foto no eligió.
    await page.getByLabel('Añadir una foto').setInputFiles(HEIC_FIXTURE)

    // La miniatura en la ficha del asset. El plazo es largo a propósito:
    // descargar el decodificador, instanciar el wasm y decodificar 1280 × 960
    // van por delante del primer byte enviado.
    await expect(page.getByRole('img', { name: 'Foto de Caldera' })).toBeVisible({ timeout: 45_000 })

    // --- 3. Y en la galería del hogar ---------------------------------------
    await navigateTo(page, 'Archivo', '/almacenamiento')
    // Con el nombre en `.jpg`: es lo que se ha guardado. Dejarlo en `.heic`
    // describiría unos bytes que ya no existen en ninguna parte.
    await expect(page.getByText('photo-with-gps.jpg')).toBeVisible()
    // Por el botón y no por la imagen: la miniatura es decorativa —`alt=""`—
    // desde que el nombre accesible lo lleva la celda, que es lo que la ficha de
    // `file-gallery` pedía y lo que dejaba mudo al PDF mientras no se cumplió.
    await expect(page.getByRole('button', { name: 'Abrir photo-with-gps.jpg' })).toBeVisible()

    // --- 4. Lo que se guardó, y lo que no ------------------------------------
    const token = await accessToken(request, email, password)
    const listing = await request.get('/api/v1/files?size=200', {
      headers: { Authorization: `Bearer ${token}` },
    })
    expect(listing.ok(), 'no se pudo leer el listado de ficheros').toBe(true)
    const files = (await listing.json()) as { items: { id: string; contentType: string }[] }
    expect(files.items).toHaveLength(1)

    // **HEIC no llega nunca a `files.content_type`**, y de ahí que este hito no
    // traiga migración: el `CHECK` de la tabla sigue admitiendo los cuatro tipos
    // de siempre porque lo que se guarda es lo detectado tras recodificar.
    expect(files.items[0]!.contentType).toBe('image/jpeg')

    const content = await request.get(`/api/v1/files/${files.items[0]!.id}/content`, {
      headers: { Authorization: `Bearer ${token}` },
    })
    expect(content.ok(), 'no se pudo descargar el fichero guardado').toBe(true)

    const bytes = await content.body()
    // Un JPEG de verdad, y **sin un solo bloque de metadatos**: ni el `Exif` que
    // traía el original, ni el `APP1` que lo transporta. Salen dos veces por el
    // camino --el lienzo del navegador al convertir y el del servidor al
    // recodificar-- y basta con que falle una para que las coordenadas de la
    // casa acaben en el disco.
    expect(bytes.subarray(0, 3)).toEqual(Buffer.from([0xff, 0xd8, 0xff]))
    expect(bytes.includes(Buffer.from('Exif'))).toBe(false)
    expect(bytes.includes(Buffer.from('GPS'))).toBe(false)
  })

  /**
   * Las etiquetas libres y la cara de una categoría
   * ([ADR-015](../../docs/common/architecture/decisions/ADR-015-user-chosen-category-identity.md)).
   *
   * **Es el único recorrido del bloque en el que la accesibilidad puede romperse
   * por un dato del usuario y no por una decisión del sistema**, y por eso mide
   * algo que ningún otro mide: el contraste del color **aplicado**, leído del
   * navegador y en los dos modos. `check-contrast.py` mide los doce pares en
   * abstracto; aquí se comprueba que el par que acaba en pantalla cuando alguien
   * elige «cielo» es ese par y no otro —que es exactamente lo que falla cuando
   * una clase se compone con una plantilla y Tailwind no la genera.
   */
  test('las etiquetas y la cara de una categoría, con el color medido ya aplicado', async ({ page }) => {
    const email = `etiquetas-${Date.now()}@example.test`
    const password = 'el gato duerme en el sofa'

    await page.goto('/crear-hogar')
    await page.getByLabel('Nombre del hogar').fill('Casa con etiquetas')
    await page.getByLabel('Tu nombre').fill('Kike')
    await page.getByLabel('Correo').fill(email)
    await page.getByLabel('Contraseña', { exact: true }).fill(password)
    await page.getByRole('button', { name: /crear/i }).click()
    await page.goto(await linkFromEmail(email))
    await expect(page.getByRole('heading', { level: 1, name: 'Tu hogar' })).toBeVisible()

    // --- 1. La cara de una categoría, elegida a mano ------------------------
    await navigateTo(page, 'Catálogo', '/catalogo')
    await page.getByRole('tab', { name: 'Categorías' }).click()

    // Una de las cinco sembradas: nace con su cara puesta, y aquí se cambia.
    await page.getByRole('button', { name: 'Editar Herramientas' }).click()
    await page.getByRole('button', { name: 'Herramienta, Herramientas' }).click()
    await page.getByRole('button', { name: 'Cielo, Herramientas' }).click()
    await page.getByRole('button', { name: 'Guardar' }).click()
    await expect(page.getByRole('button', { name: 'Editar Herramientas' })).toBeVisible()

    await checkAccessibility(page, 'el catálogo con la cara puesta')
    await checkReflow(page, 'el catálogo con la cara puesta')

    // --- 2. Un asset con una etiqueta creada desde su propio campo ----------
    await navigateTo(page, 'Inventario', '/inventario')
    await page.getByRole('link', { name: 'Dar de alta' }).click()
    await page.getByLabel('Nombre').fill('Taladro')
    await page.getByLabel('Categoría').selectOption({ label: 'Herramientas' })

    // No existe todavía: el campo la crea, que es lo único que su pista promete.
    await page.getByRole('combobox', { name: 'Etiquetas (opcional)' }).fill('Camping')
    await page.getByRole('option', { name: 'Crear «Camping»' }).click()
    await expect(page.getByRole('button', { name: 'Quitar la etiqueta Camping' })).toBeVisible()

    await page.getByRole('button', { name: 'Dar de alta' }).click()
    await expect(page.getByRole('heading', { level: 1, name: 'Taladro' })).toBeVisible()

    // --- 3. El color, medido ya aplicado y en los dos modos -----------------
    //
    // El marcador de la foto que falta es el único que lleva nombre accesible
    // propio, porque es el único donde no hay texto al lado que diga de qué es
    // el hueco. Y es el sitio donde el color del usuario ocupa más pantalla.
    const marker = page.getByRole('img', { name: 'Sin foto. Categoría: Herramientas' })
    await expect(marker).toBeVisible()

    expect(
      await appliedContrast(page, marker),
      'el color de categoría elegido no llega a 4,5:1 en modo claro, ya aplicado',
    ).toBeGreaterThanOrEqual(4.5)

    await page.evaluate(() => document.documentElement.setAttribute('data-theme', 'dark'))
    await settleTransitions(page)
    expect(
      await appliedContrast(page, marker),
      'el color de categoría elegido no llega a 4,5:1 en modo oscuro, ya aplicado',
    ).toBeGreaterThanOrEqual(4.5)
    await page.evaluate(() => document.documentElement.removeAttribute('data-theme'))
    await settleTransitions(page)

    await checkAccessibility(page, 'la ficha con etiqueta y color')

    // --- 4. La etiqueta, en la fila y en el filtro --------------------------
    await navigateTo(page, 'Inventario', '/inventario')
    const row = page.getByRole('link', { name: /Taladro/ })
    await expect(row.getByText('Camping')).toBeVisible()

    await page.getByLabel('Etiqueta').selectOption({ label: 'Camping' })
    await expect(page.getByRole('link', { name: /Taladro/ })).toBeVisible()

    // Y un asset sin ella desaparece del filtro, que es lo que demuestra que el
    // filtro filtra en vez de no hacer nada.
    await page.getByLabel('Etiqueta').selectOption('')
    await page.getByRole('link', { name: 'Dar de alta' }).click()
    await page.getByLabel('Nombre').fill('Sofá')
    await page.getByLabel('Categoría').selectOption({ label: 'Mobiliario' })
    await page.getByRole('button', { name: 'Dar de alta' }).click()
    await expect(page.getByRole('heading', { level: 1, name: 'Sofá' })).toBeVisible()

    await navigateTo(page, 'Inventario', '/inventario')
    await expect(page.getByRole('link', { name: /Sofá/ })).toBeVisible()
    await page.getByLabel('Etiqueta').selectOption({ label: 'Camping' })
    await expect(page.getByRole('link', { name: /Taladro/ })).toBeVisible()
    await expect(page.getByRole('link', { name: /Sofá/ })).toHaveCount(0)
  })

  /**
   * **La pasada sistemática de accesibilidad de la lista entera**, que es lo que
   * le faltaba a la Fase 2 y no la primera visita — y que desde el cierre de
   * huecos audita también el core, porque la deuda era la misma.
   *
   * Los recorridos de arriba tocan cada pantalla y cada uno audita **la suya**
   * en el momento en que llega a ella: axe en los dos modos, reflujo, y el
   * tabulador hasta el control que ese recorrido necesita. Eso es la primera
   * visita, y deja tres huecos que solo se ven mirándolas todas juntas:
   *
   * 1. **El teclado se comprueba hasta un control y no hasta el final.** `tabTo`
   *    para en cuanto llega, así que lo que hay *después* de ese control no lo ha
   *    recorrido nadie: un botón de la última fila de una tabla que no se puede
   *    enfocar no lo delata ningún recorrido.
   * 2. **El reflujo no llega solo a todas.** En la Fase 2, dos pantallas se
   *    auditaron con axe pero sin los tres anchos.
   * 3. **Ninguna se audita sola con las doce paradas puestas.** Cada recorrido
   *    enciende un módulo, así que la navegación que miden tiene nueve o diez
   *    entradas. Doce es el caso peor, y es el único que decide si la
   *    reorganización de la navegación aguantó.
   *
   * Por eso esta prueba **no es un recorrido**: enciende los cuatro módulos por
   * la API —el ciclo de la activación ya tiene el suyo, y repetirlo aquí sería
   * medir dos veces lo mismo— y recorre las pantallas de [AUDITED_SCREENS]
   * aplicándoles a todas exactamente lo mismo. Que sea la misma comprobación en
   * todas es el punto: lo que se olvida en una pantalla nueva no es la
   * comprobación difícil sino la de siempre.
   */
  test('la pasada sistemática: la lista entera de pantallas, con teclado, reflujo y axe', async ({
    page,
    request,
  }) => {
    const email = `auditoria-${Date.now()}@example.test`
    const password = 'el gato duerme en el sofa'

    await page.goto('/crear-hogar')
    await page.getByLabel('Nombre del hogar').fill('Casa Auditada')
    await page.getByLabel('Tu nombre').fill('Kike')
    await page.getByLabel('Correo').fill(email)
    await page.getByLabel('Contraseña', { exact: true }).fill(password)
    await page.getByRole('button', { name: /crear/i }).click()
    await page.goto(await linkFromEmail(email))
    await expect(page.getByRole('heading', { level: 1, name: 'Tu hogar' })).toBeVisible()

    // Los cuatro encendidos de una vez, y por la API. Encenderlos a mano desde la
    // pantalla ya lo hace el recorrido de cada módulo; lo que hace falta aquí es
    // **el estado**, que es el que pone las doce paradas en la navegación.
    const token = await accessToken(request, email, password)
    for (const key of ['SUPPLIERS', 'WAREHOUSE', 'PURCHASING', 'MAINTENANCE']) {
      const response = await request.post(`/api/v1/modules/${key}/activation`, {
        headers: { Authorization: `Bearer ${token}` },
      })
      expect(response.status(), `no se pudo encender ${key}`).toBe(200)
    }
    await page.reload()

    // A que la sesión se reanude y la navegación se repinte con las cuatro
    // entradas nuevas. Sin esperar aquí, lo que se mide a continuación es la
    // pantalla a medio montar, que es la misma trampa que el `Spinner` con axe.
    const navigation = page.getByRole('navigation', { name: 'Principal' })
    await expect(navigation.getByRole('link', { name: 'Mantenimiento' })).toBeVisible()

    // **Un fichero dentro, y esto no es decoración de la prueba.** «Archivo»
    // entra en la lista de abajo, y sin nada subido esa pantalla pinta su vacío:
    // axe recorrería una rejilla que no existe y la auditoría diría que pasa sin
    // haber mirado ninguna celda. Es exactamente el agujero por el que la celda
    // de un PDF se pasó una semana sin nombre accesible.
    //
    // Se sube un **PDF** a propósito, que es el caso sin miniatura: el que se
    // quedaba mudo.
    const seeded = await request.post('/api/v1/files', {
      headers: { Authorization: `Bearer ${token}` },
      multipart: {
        file: { name: 'factura-caldera.pdf', mimeType: 'application/pdf', buffer: MINIMAL_PDF },
      },
    })
    expect(seeded.status(), 'no se pudo sembrar el fichero de la auditoría').toBe(201)

    // **Y un asset, por el mismo motivo que el fichero.** «Inventario» entra en
    // la lista de abajo con el cierre de huecos, y era una de las cuatro
    // pantallas del core que nadie auditaba. Vacía pintaría su estado vacío
    // —cuatro elementos— y axe diría que pasa sin haber mirado ninguna fila.
    //
    // Con estado de conservación puesto, que es el campo que este hito añade a
    // esa pantalla: auditar la fila sin él sería auditar la de ayer.
    const categories = await request.get('/api/v1/categories', {
      headers: { Authorization: `Bearer ${token}` },
    })
    // Por nombre y no por posición: el hito le cambia el icono y el color a esta
    // misma, y el recorrido tiene que decir a cuál se los cambia.
    const seededCategory = ((await categories.json()) as {
      items: Array<{ id: string; name: string }>
    }).items.find((item) => item.name === 'Herramientas')!.id
    // **Con una etiqueta y con color puesto**, que es lo que el Hito 4 añade a
    // esta pantalla y a la del catálogo. Sin ellos, axe auditaría la fila de
    // ayer: ni una pastilla de etiqueta ni un marcador de color.
    const tag = await request.post('/api/v1/tags', {
      headers: { Authorization: `Bearer ${token}` },
      data: { name: 'Camping' },
    })
    expect(tag.status(), 'no se pudo sembrar la etiqueta de la auditoría').toBe(201)
    const tagId = ((await tag.json()) as { id: string }).id

    const painted = await request.patch(`/api/v1/categories/${seededCategory}`, {
      headers: { Authorization: `Bearer ${token}` },
      data: { name: 'Herramientas', icon: 'TOOL', color: 'SKY' },
    })
    expect(painted.status(), 'no se pudo pintar la categoría de la auditoría').toBe(200)

    const asset = await request.post('/api/v1/assets', {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        name: 'Taladro',
        type: 'DURABLE',
        categoryId: seededCategory,
        condition: 'WORN',
        tagIds: [tagId],
      },
    })
    expect(asset.status(), 'no se pudo sembrar el asset de la auditoría').toBe(201)

    // **Y una ubicación, por el mismo motivo que el asset.** «Sitios» entra en
    // la lista con el Hito 6, y vacía pintaría su estado vacío: axe diría que
    // pasa sin haber mirado ni una fila de la jerarquía.
    const location = await request.post('/api/v1/locations', {
      headers: { Authorization: `Bearer ${token}` },
      data: { name: 'Trastero', type: 'ROOM' },
    })
    expect(location.status(), 'no se pudo sembrar la ubicación de la auditoría').toBe(201)

    // El caso peor de la fase, medido una sola vez porque la navegación es una:
    // doce paradas a 320 px, con su suelo de 44 px y sin desbordar.
    await checkTouchTargets(page)

    for (const screen of AUDITED_SCREENS) {
      await navigateTo(page, screen.link, screen.path, screen.exact ?? false)
      await expect(
        page.getByRole('heading', { level: 1, name: screen.heading }),
        `«${screen.link}» no llegó a montarse`,
      ).toBeVisible()

      await checkAccessibility(page, screen.link)
      await checkReflow(page, screen.link)
      await sweepKeyboard(page, screen.link)
    }
  })
})

/**
 * Las pantallas que audita la pasada sistemática, con el nombre por el que se
 * llega a ellas desde la navegación. Empezó siendo «las de la Fase 2» y dejó de
 * serlo: hoy están las de la fase, las del core que no tenían auditoría propia y
 * las que el cierre de huecos tocó, y por eso se llama por lo que hace y no por
 * quién la trajo — el renombrado era tarea apuntada del Hito 6 de ese bloque.
 *
 * Es una lista y no llamadas sueltas a propósito: **una pantalla nueva se añade
 * aquí y hereda la auditoría entera**, que es lo contrario de lo que pasó en la
 * Fase 1, donde el criterio de accesibilidad se dio por cubierto pantalla a
 * pantalla y una se quedó sin él.
 *
 * Desde el cierre del bloque **no queda ninguna pantalla de la navegación sin
 * auditar**: «Sitios» y «Personas» eran las dos últimas del core y entraron con
 * el Hito 6. Préstamos y Avisos tienen además su llamada suelta en los
 * recorridos de arriba.
 */
const AUDITED_SCREENS = [
  { link: 'Módulos del hogar', path: '/modulos', heading: 'Módulos' },
  // «Inventario» entra el 2026-08-20, con el estado de conservación: es una de
  // las cuatro pantallas del core que seguían sin auditar, y el hito la toca en
  // sus tres sitios --el filtro del listado, la ficha y el alta--. Se siembra un
  // asset antes, porque una pantalla vacía se audita sola y no dice nada.
  { link: 'Inventario', path: '/inventario', heading: 'Inventario' },
  // «Sitios» y «Personas» entran con el Hito 6 del cierre de huecos: eran las
  // dos últimas pantallas del core sin auditar, y un hito que consolida no deja
  // una deuda contada en un comentario. Sitios con una ubicación sembrada
  // --una pantalla vacía se audita sola--; en Personas ya está quien la mira.
  { link: 'Sitios', path: '/ubicaciones', heading: 'Ubicaciones' },
  { link: 'Personas', path: '/usuarios', heading: 'Personas' },
  // Las dos que la baja de hogar (ADR-012) llenó de contenido nuevo: «Tu hogar»
  // estrena la zona de peligro y «Tu cuenta», el cierre de cuenta. No son rutas
  // nuevas, pero lo que hay dentro sí lo es, y la auditoría se hereda por estar
  // aquí en vez de escribirse aparte.
  { link: 'Hogar', path: '/', heading: 'Tu hogar', exact: true },
  { link: 'Cuenta', path: '/cuenta', heading: 'Tu cuenta' },
  // «Archivo» entra el 2026-08-20, y con un fichero sembrado: es la pantalla de
  // la rejilla de ficheros, y no la miraba axe ni aquí ni en ninguna llamada
  // suelta. La celda de un PDF llevaba una semana sin nombre accesible por eso.
  { link: 'Archivo', path: '/almacenamiento', heading: 'Almacenamiento' },
  // «Catálogo» entra el 2026-08-20, con la identidad visual de las categorías:
  // es donde vive el selector de icono y color, o sea las veintidós parejas de
  // botones que este hito añade y **el único sitio del producto donde un color
  // lo elige el usuario**. Se siembra una categoría con color antes, porque en
  // gris axe no habría mirado ninguno de los seis.
  { link: 'Catálogo', path: '/catalogo', heading: 'Catálogo' },
  { link: 'Avisos', path: '/avisos', heading: 'Avisos' },
  { link: 'Proveedores', path: '/proveedores', heading: 'Proveedores' },
  { link: 'Almacén', path: '/almacen', heading: 'Almacén' },
  { link: 'Compras', path: '/compras', heading: 'Compras' },
  { link: 'Mantenimiento', path: '/mantenimiento', heading: 'Mantenimiento' },
]

/**
 * Recorre **la pantalla entera** con el tabulador, comprobando el anillo de foco
 * en cada parada.
 *
 * Es la diferencia con [tabTo], que para en cuanto llega a su destino: aquí no
 * hay destino, y por eso lo que se mide es lo que a `tabTo` se le queda detrás.
 * Tres cosas se afirman de cada pantalla:
 *
 * - **La primera parada es el salto al contenido.** Si deja de serlo, recorrer la
 *   aplicación con el tabulador pasa por las doce paradas de la navegación en
 *   cada pantalla.
 * - **Toda parada dibuja contorno**, que es el compromiso de la ADR-006 medido ya
 *   aplicado y no sobre el token.
 * - **El recorrido termina**, y termina saliéndose del documento. Un ciclo que no
 *   sale es una trampa de foco, y es de los pocos defectos de accesibilidad que
 *   dejan la aplicación inutilizable en lugar de incómoda.
 *
 * El tope de paradas es holgado y no ajustado: lo que tiene que fallar aquí es la
 * trampa de foco, no una pantalla con una fila de más.
 */
async function sweepKeyboard(page: Page, screen: string, limit = 80) {
  // Desde el principio del documento: se llega hasta aquí haciendo clic, y el
  // clic deja el punto de partida de la navegación secuencial donde cayó.
  await page.reload()
  await expect(page.getByRole('heading', { level: 1 }).first()).toBeVisible()

  const stops: string[] = []

  for (let stop = 1; stop <= limit; stop++) {
    await page.keyboard.press('Tab')

    const at = await page.evaluate(() => {
      const node = document.activeElement
      if (!node || node === document.body || node === document.documentElement) return null
      return `${node.tagName.toLowerCase()}«${(node.textContent ?? '').trim().slice(0, 32)}»`
    })

    // Salió del documento: el recorrido ha dado la vuelta entera.
    if (at === null) break

    if (stop === 1) {
      await expect(
        page.getByRole('link', { name: 'Saltar al contenido' }),
        `«${screen}»: la primera parada del tabulador no es el salto al contenido`,
      ).toBeFocused()
    }

    await expectFocusRing(page, `${screen}, parada ${stop}`)
    stops.push(at)
  }

  expect(stops.length, `«${screen}»: el tabulador no encontró ninguna parada`).toBeGreaterThan(0)
  expect(
    stops.length,
    `«${screen}»: el tabulador no salió del documento en ${limit} paradas, ` +
      `así que hay una trampa de foco. Recorrido: ${stops.join(' → ')}`,
  ).toBeLessThan(limit)
}

/**
 * Una fecha de dentro de tantos dias, en el formato que espera un `input[type=date]`.
 *
 * Relativa a hoy y no fija: una fecha escrita a mano caduca --literalmente-- y
 * convierte la prueba en una que empieza a fallar sola un dia cualquiera.
 */
function inDays(days: number): string {
  const date = new Date()
  date.setDate(date.getDate() + days)
  return dayOf(date, Intl.DateTimeFormat().resolvedOptions().timeZone)
}

/**
 * Las dos zonas con las que se comprueba que el día es **el del hogar** y no el
 * de quien mira: los dos extremos del huso, +14 y −11.
 *
 * Son 25 horas de diferencia, así que su día de calendario **nunca** coincide.
 * De ahí que baste elegir la que no coincida con la del hogar para tener siempre
 * un navegador en otro día, a cualquier hora a la que se lance la suite.
 */
const KIRITIMATI = 'Pacific/Kiritimati'
const NIUE = 'Pacific/Niue'

/** Qué día es hoy en esa zona, en el `YYYY-MM-DD` que usan los campos de fecha. */
function dayIn(timeZone: string): string {
  return dayOf(new Date(), timeZone)
}

/**
 * El día de calendario de un instante en una zona.
 *
 * Con `Intl` y no con `toISOString()`, que da el de Greenwich: es el mismo
 * defecto que este hito corrige en la pantalla, y una prueba que lo repitiera
 * mediría con la regla torcida.
 */
function dayOf(at: Date, timeZone: string): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(at)

  const value = (type: Intl.DateTimeFormatPartTypes) => parts.find((part) => part.type === type)!.value
  return `${value('year')}-${value('month')}-${value('day')}`
}

/**
 * La zona del hogar, leída **de la API** y no del navegador.
 *
 * Del navegador salió al darlo de alta —el enrolamiento la detecta— pero
 * volverla a leer de ahí sería dar por hecho justo lo que se quiere comprobar.
 */
async function householdTimeZone(
  request: APIRequestContext,
  email: string,
  password: string,
): Promise<string> {
  const token = await accessToken(request, email, password)
  const response = await request.get('/api/v1/households/current', {
    headers: { Authorization: `Bearer ${token}` },
  })
  expect(response.ok(), 'no se pudo leer el hogar por la API').toBe(true)
  return ((await response.json()) as { timeZone: string }).timeZone
}

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
async function navigateTo(page: Page, label: string, path: string, exact = false) {
  // `exact` hace falta desde que la lista incluye «Hogar»: por omisión Playwright
  // busca la subcadena, así que «Hogar» resuelve también a «Módulos del hogar» y
  // el localizador falla por ambigüedad en vez de por ausencia.
  await page
    .getByRole('navigation', { name: 'Principal' })
    .getByRole('link', { name: label, exact })
    .click()
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
 * El contraste **ya aplicado** entre la tinta y el fondo de un elemento, leído
 * del navegador.
 *
 * Existe por los seis colores de categoría, que son lo único de la interfaz que
 * elige el usuario. `check-contrast.py` mide sus doce pares en abstracto, leyendo
 * los `oklch()` de los tokens; lo que no puede saber es si el par que acaba en
 * pantalla es ese. Un `bg-category-${color}-soft` compuesto con una plantilla no
 * lo genera Tailwind y **no falla nadie**: el fondo sale transparente y el
 * contraste medido aquí se desploma. Es el defecto que el Hito 3 destapó en la
 * pantalla de Préstamos, medido esta vez antes de que ocurra.
 *
 * axe no lo cubre porque el icono es un gráfico y no texto: su regla de
 * contraste solo mira nodos con texto.
 *
 * **El color se resuelve pintándolo, no leyéndolo.** La versión obvia --sacar los
 * tres números del valor calculado-- da 1,00:1 sobre esta paleta y tardó una
 * ejecución en entenderse: Chrome **conserva `oklch()` en `getComputedStyle`**
 * para los espacios de color modernos, así que `oklch(0.46 0.09 230)` se parseaba
 * como el sRGB `(0,46, 0,09, 230)` y los dos colores salían casi negros. Un
 * píxel de lienzo devuelve los bytes de verdad, sea cual sea la notación.
 */
async function appliedContrast(page: Page, locator: Locator): Promise<number> {
  return locator.evaluate((node) => {
    const toRgb = (value: string) => {
      const canvas = document.createElement('canvas')
      canvas.width = 1
      canvas.height = 1
      const context = canvas.getContext('2d')!
      context.fillStyle = value
      context.fillRect(0, 0, 1, 1)
      const [r, g, b] = context.getImageData(0, 0, 1, 1).data
      return [r!, g!, b!]
    }

    const luminance = ([r, g, b]: number[]) => {
      const channel = (raw: number) => {
        const c = raw / 255
        return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4
      }
      return 0.2126 * channel(r!) + 0.7152 * channel(g!) + 0.0722 * channel(b!)
    }

    const style = getComputedStyle(node)
    const ink = luminance(toRgb(style.color))
    const paper = luminance(toRgb(style.backgroundColor))
    return (Math.max(ink, paper) + 0.05) / (Math.min(ink, paper) + 0.05)
  })
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
    return {
      style: style.outlineStyle,
      width: Number.parseFloat(style.outlineWidth),
      // Quién es. Sin esto el fallo dice en qué parada pasó y no en cuál de los
      // veintitantos elementos de la pantalla, que es lo que hace falta para
      // arreglarlo sin abrir la traza.
      who:
        `${node.tagName.toLowerCase()}` +
        `${node.id ? `#${node.id}` : ''}` +
        `${node.getAttribute('type') ? `[type=${node.getAttribute('type')}]` : ''}` +
        `${node.className ? `.${String(node.className).split(/\s+/).slice(0, 3).join('.')}` : ''}` +
        `«${(node.textContent ?? '').trim().slice(0, 40)}»`,
    }
  })

  expect(ring, `${where}: el tabulador se salió de la página`).not.toBeNull()
  expect(ring?.style, `${where} (${ring?.who}): el elemento enfocado no dibuja contorno`).not.toBe('none')
  expect(
    ring?.width ?? 0,
    `${where} (${ring?.who}): contorno de foco de menos de 2 px`,
  ).toBeGreaterThanOrEqual(2)
}

function describe(violations: Array<{ id: string; help: string; nodes: unknown[] }>): string {
  if (violations.length === 0) return 'ninguna'
  return violations.map((v) => `${v.id} (${v.nodes.length}): ${v.help}`).join('; ')
}
