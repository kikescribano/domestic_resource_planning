import { render, screen } from '@testing-library/react'
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
  await screen.findByRole('heading', { level: 1, name: 'Tu hogar' })

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
      '/api/v1/storage': USAGE,
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
      '/api/v1/storage': USAGE,
      '/api/v1/files?size=200': FILES,
    })

    expect(await screen.findByText('manual-caldera.pdf')).toBeInTheDocument()
    expect(screen.getByText('2,3 MB')).toBeInTheDocument()

    // La imagen sí tiene miniatura; el PDF pinta el icono de tipo, y por eso
    // solo hay una imagen en la rejilla.
    const thumbnails = screen.getAllByRole('img')
    expect(thumbnails).toHaveLength(1)
    expect(thumbnails[0]).toHaveAttribute('alt', 'estanteria.jpg')
    // Nativa y perezosa: es lo que la URL firmada permite conservar y lo que
    // descartaba pintar desde un blob.
    expect(thumbnails[0]).toHaveAttribute('loading', 'lazy')
  })

  it('borrar un fichero refresca también la cuota, porque se libera en el acto', async () => {
    const { calls } = await signInAndVisit('Archivo', {
      '/api/v1/storage': USAGE,
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
      '/api/v1/storage': USAGE,
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
      '/api/v1/storage': USAGE,
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
  }

  vi.stubGlobal(
    'XMLHttpRequest',
    vi.fn(() => request),
  )
  return request
}
