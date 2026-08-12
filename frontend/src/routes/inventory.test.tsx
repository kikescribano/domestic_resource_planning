import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { App } from '../App'
import { fakeTokenPair, stubFetch, type StubbedResponse } from '../test/http'

/**
 * El catálogo, el árbol de ubicaciones y las operaciones de existencias, vistos
 * desde el navegador.
 *
 * Lo que se comprueba aquí es lo que solo se ve aquí: que cada pantalla llama a
 * la operación que dice llamar y con el cuerpo que corresponde, y que pinta bien
 * los tres desenlaces que el Hito 2 introduce y que se confunden con facilidad
 * —éxito, error de regla de negocio y **éxito con aviso**.
 */

function goTo(path: string) {
  window.history.pushState({}, '', path)
}

const CATEGORIES = {
  status: 200,
  body: {
    items: [
      { id: 'cat-1', name: 'Alimentación', notes: null, createdAt: '2026-08-12T00:00:00Z', retiredAt: null },
      { id: 'cat-2', name: 'Herramientas', notes: null, createdAt: '2026-08-12T00:00:00Z', retiredAt: null },
    ],
    page: 0,
    size: 200,
    total: 2,
  },
}

const LOCATIONS = {
  status: 200,
  body: {
    items: [
      { id: 'loc-1', name: 'Casa del Pinar', type: 'HOUSE', parentLocationId: null, capacity: null, notes: null },
      { id: 'loc-2', name: 'Despensa', type: 'ROOM', parentLocationId: 'loc-1', capacity: null, notes: null },
    ],
    page: 0,
    size: 200,
    total: 2,
  },
}

const SUGAR = {
  id: 'art-1',
  name: 'Azúcar',
  categoryId: 'cat-1',
  category: 'Alimentación',
  unit: 'GRAM',
  brand: null,
  model: null,
  barcode: null,
  packSize: null,
  notes: null,
  retiredAt: null,
}

const ARTICLES = { status: 200, body: { items: [SUGAR], page: 0, size: 200, total: 1 } }

function stockItem(overrides: Record<string, unknown> = {}) {
  return {
    id: 'asset-1',
    name: 'Azúcar',
    type: 'CONSUMABLE',
    categoryId: 'cat-1',
    category: 'Alimentación',
    articleId: 'art-1',
    ownerId: '22222222-2222-2222-2222-222222222222',
    location: { type: 'LOCATION', id: 'loc-2' },
    status: 'AVAILABLE',
    quantity: 300,
    unit: 'GRAM',
    serialNumber: null,
    notes: null,
    warnings: [],
    ...overrides,
  }
}

/** Lo que hace falta para que el shell se pinte tras entrar. */
const SESSION_ROUTES: Record<string, StubbedResponse> = {
  'POST /api/v1/auth/login': { status: 200, body: fakeTokenPair() },
  'GET /api/v1/users?includeDeactivated=false': {
    status: 200,
    body: { items: [], page: 0, size: 50, total: 0 },
  },
}

/**
 * Entra y navega. La sesión no se restaura de `localStorage` --solo el refresh
 * token vive ahí, y el access token se queda en memoria-- así que una prueba de
 * pantalla autenticada tiene que pasar por el login como una persona.
 */
async function signInAndVisit(link: string, responses: Record<string, StubbedResponse>) {
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

beforeEach(() => {
  localStorage.clear()
  goTo('/')
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('catálogo', () => {
  it('crea un artículo con su categoría y su unidad', async () => {
    const { calls } = await signInAndVisit('Catálogo', {
      'GET /api/v1/categories?includeRetired=false&size=200': CATEGORIES,
      'GET /api/v1/articles?size=200': ARTICLES,
      'POST /api/v1/articles': { status: 201, body: SUGAR },
    })

    await userEvent.type(screen.getByLabelText('Nombre del artículo'), 'Lentejas')
    await userEvent.selectOptions(screen.getByLabelText('Categoría'), 'cat-1')
    await userEvent.selectOptions(screen.getByLabelText('Unidad'), 'GRAM')
    await userEvent.click(screen.getByRole('button', { name: 'Crear artículo' }))

    const created = calls.find((call) => call.method === 'POST' && call.url.endsWith('/articles'))
    expect(created?.body).toEqual({ name: 'Lentejas', categoryId: 'cat-1', unit: 'GRAM' })
  })

  it('un nombre repetido se explica en castellano, no con el código crudo del contrato', async () => {
    await signInAndVisit('Catálogo', {
      'GET /api/v1/categories?includeRetired=false&size=200': CATEGORIES,
      'GET /api/v1/articles?size=200': ARTICLES,
      'POST /api/v1/articles': {
        status: 409,
        body: { code: 'ARTICLE_DUPLICATE', message: 'Ya existe un artículo vigente con ese nombre' },
      },
    })

    await userEvent.type(screen.getByLabelText('Nombre del artículo'), 'Azúcar')
    await userEvent.selectOptions(screen.getByLabelText('Categoría'), 'cat-1')
    await userEvent.click(screen.getByRole('button', { name: 'Crear artículo' }))

    expect(
      await screen.findByText('Ya hay un artículo con ese nombre o ese código de barras.'),
    ).toBeInTheDocument()
  })

  it('la búsqueda va al servidor, no filtra en memoria', async () => {
    const { calls } = await signInAndVisit('Catálogo', {
      'GET /api/v1/categories?includeRetired=false&size=200': CATEGORIES,
      'GET /api/v1/articles?size=200': ARTICLES,
      'GET /api/v1/articles?q=azu&size=200': ARTICLES,
    })

    await userEvent.type(screen.getByLabelText('Buscar'), 'azu')

    await vi.waitFor(() => {
      expect(calls.some((call) => call.url.includes('q=azu'))).toBe(true)
    })
  })
})

describe('árbol de ubicaciones', () => {
  it('anida las ubicaciones y anuncia el nivel a un lector de pantalla', async () => {
    await signInAndVisit('Sitios', {
      'GET /api/v1/locations?size=200': LOCATIONS,
    })

    const tree = await screen.findByRole('tree', { name: 'Ubicaciones del hogar' })
    const [house, pantry] = within(tree).getAllByRole('treeitem')

    // La sangría es decoración: quien no ve la pantalla necesita el nivel.
    expect(house).toHaveAttribute('aria-level', '1')
    expect(pantry).toHaveAttribute('aria-level', '2')
    expect(within(pantry!).getByText('Despensa')).toBeInTheDocument()
  })

  it('borrar una ubicación que tiene cosas dentro se explica sin jerga', async () => {
    await signInAndVisit('Sitios', {
      'GET /api/v1/locations?size=200': LOCATIONS,
      'DELETE /api/v1/locations/loc-2': {
        status: 409,
        body: { code: 'LOCATION_HAS_ASSETS', message: 'La ubicación todavía tiene assets dentro' },
      },
    })

    const tree = await screen.findByRole('tree', { name: 'Ubicaciones del hogar' })
    const [, despensa] = within(tree).getAllByRole('treeitem')
    await userEvent.click(within(despensa!).getByRole('button', { name: 'Borrar' }))

    expect(await screen.findByText('No se puede borrar: todavía hay cosas guardadas ahí.')).toBeInTheDocument()
  })
})

describe('existencias', () => {
  it('dar entrada manda la cantidad y dice que ha sumado sobre lo que había', async () => {
    const { calls } = await signInAndVisit('Inventario', {
      'GET /api/v1/assets?size=200': { status: 200, body: { items: [], page: 0, size: 200, total: 0 } },
      'GET /api/v1/articles?size=200': ARTICLES,
      'GET /api/v1/locations?size=200': LOCATIONS,
      'GET /api/v1/assets?articleId=art-1&locationId=loc-2&size=200': {
        status: 200,
        body: { items: [stockItem()], page: 0, size: 200, total: 1 },
      },
      'POST /api/v1/assets/intake': { status: 200, body: stockItem({ quantity: 1300 }) },
    })

    await userEvent.click(screen.getByRole('link', { name: 'Dar entrada' }))
    await userEvent.selectOptions(await screen.findByLabelText('Artículo'), 'art-1')
    await userEvent.selectOptions(screen.getByLabelText('Dónde se guarda'), 'loc-2')
    await userEvent.type(screen.getByLabelText('Cantidad que entra (g)'), '1000')
    await userEvent.click(screen.getByRole('button', { name: 'Dar entrada' }))

    const intake = calls.find((call) => call.url.endsWith('/assets/intake'))
    expect(intake?.body).toMatchObject({
      articleId: 'art-1',
      quantity: 1000,
      location: { type: 'LOCATION', id: 'loc-2' },
    })

    // El desenlace importa: la misma petición crea o suma, y el usuario tiene
    // que saber cuál de las dos ha pasado.
    expect(await screen.findByText('Sumado a lo que había')).toBeInTheDocument()
    expect(screen.getByText(/1300 g/)).toBeInTheDocument()
  })

  it('la ficha de una existencia ofrece mover, corregir cantidad y dar de baja', async () => {
    await signInAndVisit('Inventario', {
      'GET /api/v1/assets?size=200': {
        status: 200,
        body: { items: [stockItem()], page: 0, size: 200, total: 1 },
      },
      'GET /api/v1/assets/asset-1': { status: 200, body: stockItem() },
      'GET /api/v1/locations?size=200': LOCATIONS,
      'GET /api/v1/assets?articleId=art-1&size=200': {
        status: 200,
        body: { items: [stockItem()], page: 0, size: 200, total: 1 },
      },
    })

    await userEvent.click(await screen.findByRole('link', { name: /Azúcar/ }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Azúcar' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Mover aquí' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Guardar cantidad' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Dar de baja' })).toBeInTheDocument()

    // Sin otra existencia del mismo artículo, unir no se ofrece: sería una
    // acción que solo puede fallar.
    expect(screen.queryByRole('button', { name: 'Unir' })).not.toBeInTheDocument()
  })

  it('el ajuste manda la cantidad absoluta, no la diferencia', async () => {
    const { calls } = await signInAndVisit('Inventario', {
      'GET /api/v1/assets?size=200': {
        status: 200,
        body: { items: [stockItem()], page: 0, size: 200, total: 1 },
      },
      'GET /api/v1/assets/asset-1': { status: 200, body: stockItem() },
      'GET /api/v1/locations?size=200': LOCATIONS,
      'GET /api/v1/assets?articleId=art-1&size=200': {
        status: 200,
        body: { items: [stockItem()], page: 0, size: 200, total: 1 },
      },
      'PATCH /api/v1/assets/asset-1': { status: 200, body: stockItem({ quantity: 120 }) },
    })

    await userEvent.click(await screen.findByRole('link', { name: /Azúcar/ }))
    const field = await screen.findByLabelText('Cantidad que hay ahora')
    await userEvent.clear(field)
    await userEvent.type(field, '120')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar cantidad' }))

    const adjust = calls.find((call) => call.method === 'PATCH' && call.url.endsWith('/assets/asset-1'))
    expect(adjust?.body).toEqual({ quantity: 120 })
  })

  it('mover a una ubicación llena avisa pero no lo trata como un error', async () => {
    await signInAndVisit('Inventario', {
      'GET /api/v1/assets?size=200': {
        status: 200,
        body: { items: [stockItem()], page: 0, size: 200, total: 1 },
      },
      'GET /api/v1/assets/asset-1': { status: 200, body: stockItem() },
      'GET /api/v1/locations?size=200': LOCATIONS,
      'GET /api/v1/assets?articleId=art-1&size=200': {
        status: 200,
        body: { items: [stockItem()], page: 0, size: 200, total: 1 },
      },
      'PATCH /api/v1/assets/asset-1': {
        status: 200,
        body: stockItem({
          location: { type: 'LOCATION', id: 'loc-1' },
          warnings: [
            {
              code: 'LOCATION_CAPACITY_EXCEEDED',
              message: 'La ubicación declara un máximo de 2 cosas y ya contiene 3',
            },
          ],
        }),
      },
    })

    await userEvent.click(await screen.findByRole('link', { name: /Azúcar/ }))
    await userEvent.selectOptions(await screen.findByLabelText('Nueva ubicación'), 'loc-1')
    await userEvent.click(screen.getByRole('button', { name: 'Mover aquí' }))

    // El aviso se pinta como aviso --`status`, no `alert`-- porque la operación
    // tuvo éxito: el asset ya está movido.
    const notice = await screen.findByText('La ubicación declara un máximo de 2 cosas y ya contiene 3')
    expect(notice.closest('[role="status"]')).not.toBeNull()
  })

  it('mover una existencia sobre otra del mismo artículo remite a unirlas', async () => {
    await signInAndVisit('Inventario', {
      'GET /api/v1/assets?size=200': {
        status: 200,
        body: { items: [stockItem()], page: 0, size: 200, total: 1 },
      },
      'GET /api/v1/assets/asset-1': { status: 200, body: stockItem() },
      'GET /api/v1/locations?size=200': LOCATIONS,
      'GET /api/v1/assets?articleId=art-1&size=200': {
        status: 200,
        body: { items: [stockItem()], page: 0, size: 200, total: 1 },
      },
      'PATCH /api/v1/assets/asset-1': {
        status: 409,
        body: { code: 'EXISTENCE_ALREADY_IN_LOCATION', message: 'Ya hay una existencia viva ahí' },
      },
    })

    await userEvent.click(await screen.findByRole('link', { name: /Azúcar/ }))
    await userEvent.selectOptions(await screen.findByLabelText('Nueva ubicación'), 'loc-1')
    await userEvent.click(screen.getByRole('button', { name: 'Mover aquí' }))

    expect(
      await screen.findByText('Ahí ya hay una existencia de este artículo. Únelas en lugar de moverla.'),
    ).toBeInTheDocument()
  })

  it('un consumible a cero se distingue de uno disponible y de uno dado de baja', async () => {
    await signInAndVisit('Inventario', {
      'GET /api/v1/assets?size=200': {
        status: 200,
        body: {
          items: [
            stockItem({ id: 'a', quantity: 0 }),
            stockItem({ id: 'b', name: 'Sal', quantity: 500 }),
            stockItem({ id: 'c', name: 'Harina', status: 'DECOMMISSIONED', quantity: 0 }),
          ],
          page: 0,
          size: 200,
          total: 3,
        },
      },
    })

    // Agotado no es lo mismo que de baja: uno se repone y el otro ya no existe.
    expect(await screen.findByText('Agotado')).toBeInTheDocument()
    expect(screen.getByText('Disponible')).toBeInTheDocument()
    expect(screen.getByText('De baja')).toBeInTheDocument()
  })
})
