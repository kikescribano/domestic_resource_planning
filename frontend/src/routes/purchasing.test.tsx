import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { App } from '../App'
import { fakeTokenPair, stubFetch, type StubbedRoute } from '../test/http'

/**
 * La pantalla de compras.
 *
 * Lo que se comprueba aquí es lo que solo se ve en el navegador y no en el
 * backend:
 *
 * - Que **con el módulo de proveedores apagado la pantalla no se rompe ni
 *   explica nada**: el selector de dónde se compra simplemente no está, porque la
 *   lista llega vacía del servidor. Es la mitad visible de la decisión del hito.
 * - Que el origen de cada línea se dice **con etiqueta y no solo con color**.
 * - Que lo que no está en el catálogo avisa de que no entrará en el inventario
 *   **antes** de comprarlo, en lugar de dejar que se descubra al recibir.
 *
 * Lo que **no** se comprueba aquí es el gate: eso vive en el backend y en el
 * recorrido vertical, que sí llega hasta el `403`. Y el guardián de la ruta tiene
 * sus dos mitades comprobadas en `modules.test.tsx`, que es donde vive.
 */

const LIST = 'GET /api/v1/purchasing/list?size=200'
const PURCHASES = 'GET /api/v1/purchasing/purchases?size=200'
const SHOPS = 'GET /api/v1/purchasing/suppliers'
const ARTICLES = 'GET /api/v1/articles?size=200'

function item(overrides: Record<string, unknown> = {}) {
  return {
    id: 'aaaaaaaa-0000-0000-0000-000000000001',
    articleId: 'bbbbbbbb-0000-0000-0000-000000000001',
    name: 'Arroz',
    unit: 'GRAM',
    packLabel: null,
    quantity: 1000,
    origin: 'MANUAL',
    status: 'NEEDED',
    note: null,
    purchaseId: null,
    receivedAssetId: null,
    createdAt: '2026-08-19T10:00:00Z',
    updatedAt: '2026-08-19T10:00:00Z',
    ...overrides,
  }
}

function page(items: unknown[]) {
  return { status: 200, body: { items, page: 0, size: 200, total: items.length } }
}

/** El catálogo con Compras encendido, que es la única forma de llegar a la pantalla. */
function catalogueWithPurchasing() {
  return {
    status: 200,
    body: {
      items: [
        {
          key: 'PURCHASING',
          name: 'Compras y lista de la compra',
          description: 'Qué falta, qué hay que reponer y qué está pedido.',
          routePrefix: '/api/v1/purchasing',
          status: 'ACTIVE',
          activatedAt: '2026-08-19T10:00:00Z',
          deactivatedAt: null,
        },
      ],
      page: 0,
      size: 1,
      total: 1,
    },
  }
}

async function openPurchasing(routes: Record<string, StubbedRoute>) {
  localStorage.setItem('drp.refreshToken', 'refresh-de-mentira')
  window.history.pushState({}, '', '/compras')

  const stub = stubFetch({
    'POST /api/v1/auth/refresh': { status: 200, body: fakeTokenPair() },
    'GET /api/v1/modules': catalogueWithPurchasing(),
    [ARTICLES]: page([]),
    [SHOPS]: { status: 200, body: [] },
    [PURCHASES]: page([]),
    ...routes,
  })

  render(<App />)
  await screen.findByRole('heading', { level: 1, name: 'Compras' })
  return stub
}

beforeEach(() => {
  localStorage.clear()
  window.history.pushState({}, '', '/')
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('la lista de la compra', () => {
  it('un hogar sin nada que comprar ve el vacío de verdad', async () => {
    await openPurchasing({ [LIST]: page([]) })

    expect(await screen.findByText('No hace falta nada')).toBeInTheDocument()
  })

  it('el origen de cada línea lleva etiqueta y no solo color', async () => {
    await openPurchasing({
      [LIST]: page([
        item({ origin: 'DEPLETED', name: 'Sal' }),
        item({ id: 'x', origin: 'LOW_STOCK', name: 'Azúcar' }),
      ]),
    })

    // La regla 4 de la dirección visual: nada se dice solo con color. Y son dos
    // hechos distintos, no dos tonos del mismo.
    expect(await screen.findByText('Se ha acabado')).toBeInTheDocument()
    expect(screen.getByText('Queda poco')).toBeInTheDocument()
  })

  it('lo que ya va en una compra no se puede volver a llevar', async () => {
    await openPurchasing({
      [LIST]: page([item({ status: 'IN_PURCHASE', purchaseId: 'p1' })]),
    })

    // Deshabilitado y no oculto: quitarlo movería las filas de sitio cada vez
    // que alguien abre una compra.
    expect(await screen.findByText('En una compra')).toBeInTheDocument()
    expect(screen.getByRole('checkbox')).toBeDisabled()
  })

  it('apuntar algo que no está en el catálogo manda el nombre y no un artículo', async () => {
    const stub = await openPurchasing({
      [LIST]: page([]),
      'POST /api/v1/purchasing/list': { status: 201, body: item({ articleId: null, name: 'Pilas AA' }) },
    })

    await userEvent.type(await screen.findByRole('combobox', { name: 'Qué hace falta' }), 'Pilas AA')
    await userEvent.click(screen.getByRole('button', { name: 'Apuntar' }))

    // Por la ruta y no solo por el método: la primera petición de toda pantalla
    // autenticada es la renovación del token, que también es un POST.
    const call = stub.calls.find(
      (entry) => entry.method === 'POST' && entry.url.endsWith('/purchasing/list'),
    )
    expect(call?.body).toEqual({ name: 'Pilas AA' })
  })
})

describe('con el módulo de proveedores apagado', () => {
  it('el selector de dónde se compra no está, y la pantalla no explica nada', async () => {
    // El servidor responde 200 con lista vacía y **no** 403: la degradación la
    // pone él, así que aquí no hay ninguna rama que dependa de otro módulo.
    await openPurchasing({ [LIST]: page([item()]), [SHOPS]: { status: 200, body: [] } })

    await screen.findByText('Arroz')
    expect(screen.queryByLabelText('Dónde vas a comprar')).not.toBeInTheDocument()

    // Y se puede abrir la compra igual, que es lo que hace que la degradación no
    // recorte ninguna funcionalidad.
    await userEvent.click(screen.getByRole('checkbox'))
    expect(screen.getByRole('button', { name: 'Me llevo 1 cosa' })).toBeEnabled()
  })

  it('con proveedores encendido sí aparece, con su rótulo en castellano', async () => {
    await openPurchasing({
      [LIST]: page([item()]),
      [SHOPS]: {
        status: 200,
        // `detail` trae el IDENTIFICADOR de la categoría, no su rótulo: el texto
        // que se lee es un dato en castellano y lo pone el cliente.
        body: [{ id: 's1', name: 'Mercado de la plaza', detail: 'OTHER' }],
      },
    })

    await screen.findByText('Arroz')
    const select = await screen.findByLabelText('Dónde vas a comprar')
    expect(select).toBeInTheDocument()
    expect(screen.getByRole('option', { name: /Mercado de la plaza · Otros/ })).toBeInTheDocument()
  })
})

describe('recibir una compra', () => {
  it('una línea sin artículo avisa de que no entra en el inventario, antes de comprarla', async () => {
    await openPurchasing({
      [LIST]: page([]),
      [PURCHASES]: page([
        {
          id: 'p1',
          supplierId: null,
          supplier: null,
          status: 'OPEN',
          note: null,
          receivedAt: null,
          cancelledAt: null,
          createdAt: '2026-08-19T10:00:00Z',
        },
      ]),
      'GET /api/v1/purchasing/purchases/p1': {
        status: 200,
        body: {
          purchase: {
            id: 'p1',
            supplierId: null,
            supplier: null,
            status: 'OPEN',
            note: null,
            receivedAt: null,
            cancelledAt: null,
            createdAt: '2026-08-19T10:00:00Z',
          },
          lines: [
            item({ status: 'IN_PURCHASE', purchaseId: 'p1' }),
            item({ id: 'b', articleId: null, name: 'Pilas AA', unit: null, quantity: null, status: 'IN_PURCHASE', purchaseId: 'p1' }),
          ],
        },
      },
      'GET /api/v1/locations?size=200': page([]),
    })

    // `role="tab"` y no `button`: son dos vistas del mismo recurso y no dos
    // páginas, así que el rol dice de qué se trata a quien no ve la pantalla.
    await userEvent.click(await screen.findByRole('tab', { name: 'Las compras' }))
    await userEvent.click(await screen.findByRole('button', { name: /Sin decir dónde/ }))

    // Se dice aquí en vez de dejar que se descubra al ver que no aparece en el
    // inventario.
    expect(
      await screen.findByText('No está en el catálogo: no entra en el inventario'),
    ).toBeInTheDocument()
  })
})
