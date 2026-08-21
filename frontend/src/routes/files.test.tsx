import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { App } from '../App'
import { ALLOWED_FILE_TYPES, uploadFile } from '../api/client'
import { fakeTokenPair, stubFetch, type StubbedRoute } from '../test/http'

/**
 * La cara de ficheros, vista desde el navegador.
 *
 * Lo que se comprueba aquí es lo que solo se ve aquí: que la pantalla pinta el
 * consumo de la cuota, que la subida **no pasa por `fetch`** —y por qué—, y que
 * los tres errores de subida llegan con el texto que le sirve a una persona,
 * incluido el único que no se arregla eligiendo otro fichero.
 *
 * La subida de verdad, con sus bytes y su recodificación, es del backend y está
 * probada allí contra PostgreSQL real.
 */

function goTo(path: string) {
  window.history.pushState({}, '', path)
}

const SESSION_ROUTES = {
  'POST /api/v1/auth/login': { status: 200, body: fakeTokenPair() },
  'GET /api/v1/users?includeDeactivated=false': {
    status: 200,
    body: { items: [], page: 0, size: 50, total: 0 },
  },
}

/**
 * Entra y navega, como una persona.
 *
 * La sesión **no se restaura de `localStorage`** --solo el refresh token vive
 * ahí, y el access token se queda en memoria-- así que una prueba de pantalla
 * autenticada tiene que pasar por el login.
 */
async function signInAndVisit(link: string, responses: Record<string, StubbedRoute>) {
  goTo('/entrar')
  const stub = stubFetch({ ...SESSION_ROUTES, ...responses })

  render(<App />)
  await userEvent.type(screen.getByLabelText('Correo'), 'kike@example.test')
  await userEvent.type(screen.getByLabelText('Contraseña'), 'el gato duerme en el sofa')
  await userEvent.click(screen.getByRole('button', { name: 'Entrar' }))
  await screen.findByRole('heading', { level: 1, name: /^Hogar/ })

  await userEvent.click(screen.getByRole('link', { name: link }))
  return stub
}

const USAGE = {
  status: 200,
  body: { usedBytes: 1_000_000_000, quotaBytes: 1_073_741_824, maxFileBytes: 26_214_400 },
}

const FILES = {
  status: 200,
  body: {
    items: [
      {
        id: 'file-1',
        originalName: 'manual-caldera.pdf',
        contentType: 'application/pdf',
        sizeBytes: 2_400_000,
        checksum: 'a'.repeat(64),
        url: 'https://ficheros.drp.test/f/original/h/fi/file-1?e=1&s=x',
        thumbnailUrl: null,
        uploadedAt: '2026-08-13T10:00:00Z',
        createdAt: '2026-08-13T10:00:00Z',
        createdBy: null,
      },
      {
        id: 'file-2',
        originalName: 'estanteria.jpg',
        contentType: 'image/jpeg',
        sizeBytes: 350_000,
        checksum: 'b'.repeat(64),
        url: 'https://ficheros.drp.test/f/original/h/fi/file-2?e=1&s=y',
        thumbnailUrl: 'https://ficheros.drp.test/f/thumbnail/h/fi/file-2?e=1&s=z',
        uploadedAt: '2026-08-13T10:00:00Z',
        createdAt: '2026-08-13T10:00:00Z',
        createdBy: null,
      },
    ],
    page: 0,
    size: 200,
    total: 2,
  },
}

describe('el almacenamiento del hogar', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('pinta cuánto ocupa el hogar y avisa cuando queda poco', async () => {
    await signInAndVisit('Archivo', {
      'GET /api/v1/storage': USAGE,
      '/api/v1/files?size=200': FILES,
    })

    // El medidor va como `meter` y no como `progressbar`: no mide el avance de
    // una tarea sino una medida dentro de un rango conocido.
    const meter = await screen.findByRole('meter', { name: /espacio ocupado/i })
    expect(meter).toHaveAttribute('aria-valuenow', '1000000000')

    // Y por encima del 90 % lo dice **con texto**, no solo cambiando de color.
    expect(await screen.findByText(/queda poco espacio/i)).toBeInTheDocument()
  })

  it('lista los ficheros con su tamaño, y el PDF sin miniatura', async () => {
    await signInAndVisit('Archivo', {
      'GET /api/v1/storage': USAGE,
      '/api/v1/files?size=200': FILES,
    })

    expect(await screen.findByText('manual-caldera.pdf')).toBeInTheDocument()
    expect(screen.getByText('2,3 MB')).toBeInTheDocument()

    // La imagen sí tiene miniatura; el PDF pinta el icono de tipo, y por eso
    // solo hay un `<img>` en la rejilla. Se busca por etiqueta y no por rol
    // porque la miniatura es **decorativa** desde que el nombre lo lleva el
    // botón: con `alt=""` sale del árbol de accesibilidad y `getAllByRole('img')`
    // ya no la encuentra.
    const thumbnails = document.querySelectorAll('ul[aria-label="Ficheros del hogar"] img')
    expect(thumbnails).toHaveLength(1)
    expect(thumbnails[0]).toHaveAttribute('alt', '')
    // Nativa y perezosa: es lo que la URL firmada permite conservar y lo que
    // descartaba pintar desde un blob.
    expect(thumbnails[0]).toHaveAttribute('loading', 'lazy')
  })

  /**
   * El defecto que el repaso de las fichas destapó el 2026-08-20: la celda
   * tomaba su nombre del `alt` de la miniatura, y **un PDF no tiene miniatura**,
   * así que su botón se quedaba mudo — «botón» y nada más, en la rejilla donde
   * todo son facturas y manuales.
   *
   * Se comprueban **los dos casos**, porque el arreglo mueve el nombre de la
   * imagen al botón y podría haber dejado la imagen anunciándolo dos veces.
   */
  it('cada celda se nombra por su fichero, tenga miniatura o no', async () => {
    await signInAndVisit('Archivo', {
      'GET /api/v1/storage': USAGE,
      '/api/v1/files?size=200': FILES,
    })

    // El PDF, que es el que estaba mudo.
    expect(await screen.findByRole('button', { name: 'Abrir manual-caldera.pdf' })).toBeInTheDocument()
    // Y la imagen, que lo tenía por el `alt` y ahora lo tiene por el botón.
    expect(await screen.findByRole('button', { name: 'Abrir estanteria.jpg' })).toBeInTheDocument()

    // La miniatura queda decorativa: el nombre está escrito al lado y en el
    // botón, y anunciarlo tres veces es ruido.
    expect(screen.queryByRole('img', { name: 'estanteria.jpg' })).not.toBeInTheDocument()
  })

  it('borrar un fichero refresca también la cuota, porque se libera en el acto', async () => {
    const { calls } = await signInAndVisit('Archivo', {
      'GET /api/v1/storage': USAGE,
      '/api/v1/files?size=200': FILES,
      'DELETE /api/v1/files/file-1': { status: 204 },
    })

    const removals = await screen.findAllByRole('button', { name: 'Quitar' })
    await userEvent.click(removals[0]!)

    await vi.waitFor(() => {
      expect(calls.filter((call) => call.method === 'DELETE')).toHaveLength(1)
      // Dos lecturas de `/storage`: la de entrada y la de después de borrar.
      expect(calls.filter((call) => call.url === '/api/v1/storage').length).toBeGreaterThan(1)
    })
  })

  it('ofrece HEIC en el selector, aunque la lista blanca del servidor siga teniendo cuatro tipos', async () => {
    await signInAndVisit('Archivo', {
      'GET /api/v1/storage': USAGE,
      '/api/v1/files?size=200': FILES,
    })

    // Dejarlo fuera del `accept` sería el mismo muro que el `415` con otra
    // cara: el fichero aparecería en gris y no habría nada que hacer. Ahora se
    // convierte antes de subirlo (ADR-014), así que se puede elegir.
    const chooser = await screen.findByLabelText('Subir un fichero')
    expect(chooser).toHaveAttribute('accept', expect.stringContaining('image/heic'))
    // Y también por extensión: `image/heic` no está registrado en todos los
    // sistemas, y ahí el diálogo solo casa por el final del nombre.
    expect(chooser).toHaveAttribute('accept', expect.stringContaining('.heic'))
    // Lo que **no** ha cambiado: el servidor sigue guardando cuatro tipos.
    expect(ALLOWED_FILE_TYPES).toEqual(['image/jpeg', 'image/png', 'image/webp', 'application/pdf'])
  })

  it('un fichero en uso no se borra, y lo dice con lo que hay que hacer', async () => {
    await signInAndVisit('Archivo', {
      'GET /api/v1/storage': USAGE,
      '/api/v1/files?size=200': FILES,
      'DELETE /api/v1/files/file-1': {
        status: 409,
        body: { code: 'FILE_IN_USE', message: 'sigue adjunto' },
      },
    })

    const removals = await screen.findAllByRole('button', { name: 'Quitar' })
    await userEvent.click(removals[0]!)

    expect(await screen.findByText(/quítalo de ahí primero/i)).toBeInTheDocument()
  })
})

describe('la subida de un fichero', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  /**
   * **`fetch` no informa del progreso de subida**, así que la subida va por
   * `XMLHttpRequest`. Esta prueba fija esa decisión: si alguien la reescribiera
   * con `fetch`, la barra pasaría a ser una animación inventada sin que nada
   * fallase, y una barra que miente es peor que no tener barra.
   */
  it('va por XMLHttpRequest y va informando del progreso', async () => {
    const request = fakeXhr({ status: 201, body: { id: 'file-9' } })

    const progress: number[] = []
    const uploading = uploadFile(new File(['x'], 'foto.jpg'), 'token', (fraction) => progress.push(fraction))

    await untilOpened(request)
    request.upload.onprogress?.({ lengthComputable: true, loaded: 5, total: 10 } as ProgressEvent)
    request.upload.onprogress?.({ lengthComputable: true, loaded: 10, total: 10 } as ProgressEvent)
    request.onload?.(new Event('load') as ProgressEvent)

    await expect(uploading).resolves.toMatchObject({ id: 'file-9' })
    expect(progress).toEqual([0.5, 1])
    expect(request.opened).toEqual(['POST', '/api/v1/files'])
    // El `Content-Type` **no** se pone a mano: lo pone el navegador con el
    // `boundary` que ha generado, y escribirlo rompe el multipart.
    expect(request.headers).toEqual({ Authorization: 'Bearer token' })
  })

  /**
   * La ficha de `upload-field` describía **Cancelar** desde el Hito 3 de la Fase
   * 1 y el componente no lo tenía: el `AbortController` estaba declarado y nunca
   * se le asignaba nada, así que el estado `cancelled` era inalcanzable.
   *
   * Se prueba entero y no a trozos —el botón, el abort que llega al
   * `XMLHttpRequest`, la vuelta a reposo y el foco— porque las cuatro cosas
   * juntas son lo que la ficha exige, y las tres primeras sin la cuarta dejan a
   * quien navega con teclado en el principio de la página.
   */
  it('cancelar aborta la petición, vuelve a reposo y devuelve el foco al disparador', async () => {
    const request = fakeXhr({ status: 201, body: { id: 'file-9' } })

    await signInAndVisit('Archivo', {
      'GET /api/v1/storage': USAGE,
      '/api/v1/files?size=200': FILES,
    })

    const chooser = await screen.findByLabelText('Subir un fichero')
    await userEvent.upload(chooser, new File(['x'], 'foto.jpg', { type: 'image/jpeg' }))

    // Hasta que no hay bytes en vuelo no hay nada que cancelar: el botón
    // aparece con el primer `onprogress`, que es lo que lleva al estado
    // `uploading`.
    await untilOpened(request)
    // Dentro de `act` porque esto sí actualiza el estado de un componente
    // montado, a diferencia de las dos pruebas de abajo, que llaman a
    // `uploadFile` sin pantalla delante.
    await act(async () => {
      request.upload.onprogress?.({ lengthComputable: true, loaded: 5, total: 10 } as ProgressEvent)
    })

    const cancel = await screen.findByRole('button', { name: 'Cancelar' })
    await userEvent.click(cancel)

    expect(request.aborted).toBe(true)
    expect(await screen.findByText('Subida cancelada.')).toBeInTheDocument()
    // Y el disparador recupera el foco. Es el `<input>` y no el `<label>`: el
    // anillo lo pinta la etiqueta con `focus-within`, pero lo focusable es el
    // campo que lleva dentro.
    expect(chooser).toHaveFocus()
  })

  it('traduce el 409 de cuota agotada a algo que se puede hacer', async () => {
    const request = fakeXhr({
      status: 409,
      body: { code: 'STORAGE_QUOTA_EXCEEDED', message: 'sin espacio' },
    })

    const uploading = uploadFile(new File(['x'], 'foto.jpg'), 'token')
    await untilOpened(request)
    request.onload?.(new Event('load') as ProgressEvent)

    await expect(uploading).rejects.toMatchObject({ code: 'STORAGE_QUOTA_EXCEEDED', status: 409 })
  })
})

/**
 * Espera a que la peticion este abierta antes de dispararle los eventos.
 *
 * **La subida ya no empieza en el mismo turno**, y esta espera es lo que lo fija:
 * antes de enviar nada hay que mirar los doce primeros bytes de lo elegido para
 * saber si era un HEIC que hay que convertir (ADR-014), y leer un `Blob` es
 * asincrono. Sin esperar, la prueba dispara `onprogress` sobre un objeto al que
 * `uploadFile` todavia no se ha enganchado y se queda colgada hasta el timeout,
 * que es un sintoma que no se parece en nada a la causa.
 */
async function untilOpened(request: { opened: string[] }) {
  await vi.waitFor(() => expect(request.opened).not.toHaveLength(0))
}

/** Un `XMLHttpRequest` de mentira, con los ganchos a la vista para dispararlos desde la prueba. */
function fakeXhr(response: { status: number; body: unknown }) {
  const request = {
    status: response.status,
    responseText: JSON.stringify(response.body),
    opened: [] as string[],
    headers: {} as Record<string, string>,
    upload: {} as { onprogress?: (event: ProgressEvent) => void },
    onload: undefined as ((event: ProgressEvent) => void) | undefined,
    onerror: undefined as (() => void) | undefined,
    onabort: undefined as (() => void) | undefined,
    open(method: string, url: string) {
      this.opened = [method, url]
    },
    setRequestHeader(name: string, value: string) {
      this.headers[name] = value
    },
    send() {},
    // El doble tiene que abortar de verdad y no solo apuntarlo: lo que la prueba
    // mide es que la señal llega hasta aquí, y con un `abort()` vacío pasaría
    // igual el día que dejara de llegar.
    aborted: false,
    abort() {
      this.aborted = true
      this.onabort?.()
    },
  }

  // **Una función normal y no una flecha, y no es estilo.** El código bajo
  // prueba hace `new XMLHttpRequest()`, y desde Vitest 4 el doble se invoca de
  // verdad con `new`. Una flecha no es constructora, así que el doble no llega a
  // construirse: el `XMLHttpRequest` falso nunca se abre y las tres pruebas de
  // subida fallan con `expected [] to not have a length of +0` --un mensaje que
  // no se parece en nada a la causa--. Con Vitest 3 pasaba porque el mock
  // llamaba a la implementación como función corriente.
  vi.stubGlobal(
    'XMLHttpRequest',
    vi.fn(function () {
      return request
    }),
  )
  return request
}
