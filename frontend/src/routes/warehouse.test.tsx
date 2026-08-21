import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { App } from '../App'
import { fakeTokenPair, stubFetch, type StubbedRoute } from '../test/http'

/**
 * La pantalla del almacén.
 *
 * Lo que se comprueba aquí es lo que solo se ve en el navegador y no en el
 * backend: que **el `Combobox` que este hito estrena se puede usar con el
 * teclado**, que el vacío por filtro no se confunde con el vacío de verdad, y
 * que el formulario de consumo pide un delta y no un absoluto —que es la
 * frontera con el core hecha texto de pantalla.
 *
 * Lo que **no** se comprueba aquí es el gate: eso vive en el backend y en el
 * recorrido vertical, que sí llega hasta el `403`. Y el guardián de la ruta tiene
 * sus dos mitades comprobadas en `modules.test.tsx`, que es donde vive.
 */

const STOCK = 'GET /api/v1/warehouse/stock?size=200'
const ASSET = 'aaaaaaaa-0000-0000-0000-000000000001'
const ARTICLE = 'bbbbbbbb-0000-0000-0000-000000000001'

function stockItem(overrides: Record<string, unknown> = {}) {
  return {
    assetId: ASSET,
    articleId: ARTICLE,
    article: 'Arroz',
    unit: 'GRAM',
    locationId: 'cccccccc-0000-0000-0000-000000000001',
    location: 'Despensa',
    quantity: 900,
    minimumQuantity: null,
    belowMinimum: false,
    nearestExpiry: null,
    lotCount: 0,
    ...overrides,
  }
}

function page(items: unknown[]) {
  return { status: 200, body: { items, page: 0, size: 200, total: items.length } }
}

/** El catálogo con Warehouse encendido, que es la única forma de llegar a la pantalla. */
function catalogueWithWarehouse() {
  return {
    status: 200,
    body: {
      items: [
        {
          key: 'WAREHOUSE',
          name: 'Almacén',
          description: 'Existencias de la despensa, el garaje y el trastero.',
          routePrefix: '/api/v1/warehouse',
          status: 'ACTIVE',
          activatedAt: '2026-08-18T10:00:00Z',
          deactivatedAt: null,
        },
      ],
      page: 0,
      size: 1,
      total: 1,
    },
  }
}

async function openWarehouse(routes: Record<string, StubbedRoute>) {
  localStorage.setItem('drp.refreshToken', 'refresh-de-mentira')
  window.history.pushState({}, '', '/almacen')

  const stub = stubFetch({
    'POST /api/v1/auth/refresh': { status: 200, body: fakeTokenPair() },
    'GET /api/v1/modules': catalogueWithWarehouse(),
    ...routes,
  })

  render(<App />)
  await screen.findByRole('heading', { level: 1, name: 'Almacén' })
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

describe('el almacén', () => {
  it('un hogar que acaba de encender el módulo y no tiene consumibles ve el vacío de verdad', async () => {
    await openWarehouse({ [STOCK]: page([]) })

    expect(await screen.findByText('El almacén está vacío')).toBeInTheDocument()
    expect(screen.queryByText('Nada con ese filtro')).not.toBeInTheDocument()
  })

  it('con un filtro puesto y sin resultados, dice que es el filtro', async () => {
    await openWarehouse({
      [STOCK]: page([stockItem()]),
      'GET /api/v1/warehouse/stock?belowMinimum=true&size=200': page([]),
    })

    await screen.findByRole('button', { name: /Arroz/ })
    await userEvent.click(screen.getByLabelText('Solo lo que queda poco'))

    expect(await screen.findByText('Nada con ese filtro')).toBeInTheDocument()
  })

  it('lo que queda poco lleva etiqueta y no solo color', async () => {
    await openWarehouse({
      [STOCK]: page([stockItem({ minimumQuantity: 1000, belowMinimum: true })]),
    })

    // La regla 4 de la dirección visual: nada se dice solo con color.
    expect(await screen.findByText('Queda poco')).toBeInTheDocument()
  })
})

describe('el Combobox de búsqueda', () => {
  it('se abre, se recorre con las flechas y se elige con Enter, sin sacar el foco de la caja', async () => {
    await openWarehouse({
      [STOCK]: page([stockItem(), stockItem({ assetId: 'x', articleId: 'y', article: 'Azúcar' })]),
    })

    const input = await screen.findByRole('combobox', { name: 'Buscar' })
    expect(input).toHaveAttribute('aria-expanded', 'false')

    await userEvent.click(input)
    expect(input).toHaveAttribute('aria-expanded', 'true')

    // Al abrir ya hay una opción resaltada: la primera.
    const first = input.getAttribute('aria-activedescendant')
    expect(first).toBeTruthy()

    // Las flechas mueven el resaltado con `aria-activedescendant` y **el foco se
    // queda en la caja**, que es lo que el patrón combobox de ARIA 1.2 pide: con
    // el foco en la lista se pierde lo que se está escribiendo.
    await userEvent.keyboard('{ArrowDown}')
    expect(input).toHaveFocus()
    expect(input.getAttribute('aria-activedescendant')).not.toBe(first)

    await userEvent.keyboard('{Enter}')
    expect(input).toHaveValue('Azúcar')
    expect(input).toHaveAttribute('aria-expanded', 'false')
  })

  it('Escape cierra sin elegir: un combobox del que no se sale bloquea el teclado', async () => {
    await openWarehouse({ [STOCK]: page([stockItem()]) })

    const input = await screen.findByRole('combobox', { name: 'Buscar' })
    await userEvent.click(input)
    await userEvent.keyboard('{ArrowDown}')

    await userEvent.keyboard('{Escape}')
    expect(input).toHaveAttribute('aria-expanded', 'false')
    expect(input).toHaveValue('')
  })

  it('la lista es un listbox con opciones, no un montón de divs', async () => {
    await openWarehouse({ [STOCK]: page([stockItem()]) })

    const input = await screen.findByRole('combobox', { name: 'Buscar' })
    await userEvent.click(input)

    // Sin estos roles, un lector de pantalla no anuncia ni que hay una lista ni
    // cuál es la opción resaltada.
    const listbox = await screen.findByRole('listbox', { name: 'Buscar' })
    expect(within(listbox).getAllByRole('option').length).toBeGreaterThan(0)
    expect(input).toHaveAttribute('aria-controls', listbox.id)
  })
})

describe('apuntar un consumo', () => {
  it('pide lo GASTADO y no lo que queda, que es la frontera con el core', async () => {
    await openWarehouse({
      [STOCK]: page([stockItem()]),
      [`GET /api/v1/warehouse/stock/${ASSET}`]: {
        status: 200,
        body: { item: stockItem(), expiryLeadDays: null, lots: [], movements: [] },
      },
    })

    await userEvent.click(await screen.findByRole('button', { name: /Arroz/ }))

    // El texto es la mitad de la decisión: el `PATCH` del core es absoluto y esto
    // es un delta, así que la etiqueta tiene que decir cuál de los dos es.
    expect(await screen.findByLabelText('Gastado de Arroz (gramos)')).toBeInTheDocument()
    expect(screen.getByText('Lo que has gastado, no lo que queda.')).toBeInTheDocument()
  })

  it('el 409 del servidor se enseña donde ocurrió, sin replicar la regla en el cliente', async () => {
    await openWarehouse({
      [STOCK]: page([stockItem()]),
      [`GET /api/v1/warehouse/stock/${ASSET}`]: {
        status: 200,
        body: { item: stockItem(), expiryLeadDays: null, lots: [], movements: [] },
      },
      [`POST /api/v1/warehouse/stock/${ASSET}/consumptions`]: {
        status: 409,
        body: {
          code: 'STOCK_CONSUMPTION_EXCEEDS_QUANTITY',
          message: 'No se puede consumir más de lo que hay: quedan 900',
        },
      },
    })

    await userEvent.click(await screen.findByRole('button', { name: /Arroz/ }))
    await userEvent.type(await screen.findByLabelText('Gastado de Arroz (gramos)'), '5000')
    await userEvent.click(screen.getByRole('button', { name: 'Apuntar consumo' }))

    // El texto es **el del cliente** y no el del servidor: el `message` de la API
    // es diagnóstico, y lo que el cliente trata es el `code`.
    expect(await screen.findByText('No puedes gastar más de lo que hay.')).toBeInTheDocument()
  })

  it('dejar el mínimo vacío manda null, que deja de vigilar el artículo', async () => {
    const stub = await openWarehouse({
      [STOCK]: page([stockItem({ minimumQuantity: 500 })]),
      [`GET /api/v1/warehouse/stock/${ASSET}`]: {
        status: 200,
        body: {
          item: stockItem({ minimumQuantity: 500 }),
          expiryLeadDays: null,
          lots: [],
          movements: [],
        },
      },
      [`PATCH /api/v1/warehouse/articles/${ARTICLE}`]: {
        status: 200,
        body: { articleId: ARTICLE, minimumQuantity: null, expiryLeadDays: null, lowStockSince: null },
      },
    })

    await userEvent.click(await screen.findByRole('button', { name: /Arroz/ }))
    await userEvent.clear(await screen.findByLabelText('Avisarme cuando quede menos de (gramos)'))
    await userEvent.click(screen.getByRole('button', { name: 'Guardar mínimo' }))

    // Vacío es `null` y no cero: cero significaría «avísame cuando no quede
    // nada», que es una regla distinta y perfectamente legítima.
    const call = stub.calls.find(
      (recorded) => recorded.method === 'PATCH' && recorded.url.includes(`/warehouse/articles/${ARTICLE}`),
    )
    expect(call?.body).toEqual({ minimumQuantity: null })
  })
})
