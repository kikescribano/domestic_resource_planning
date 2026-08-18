import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { App } from '../App'
import { SESSION_CLAIMS, fakeTokenPair, stubFetch, type StubbedRoute } from '../test/http'

/**
 * La pantalla de Proveedores.
 *
 * Su ficha se escribió antes que ella, en
 * `docs/frontend/design-system/components/suppliers-page.md`, y esto comprueba lo
 * que aquella fija: que la ficha de un contacto se despliega **dentro** del
 * listado, que el vacío por filtro no se confunde con el vacío de verdad, que las
 * reglas de negocio se enseñan donde ocurrieron y que a un contacto retirado no
 * se le ofrece enlazar.
 *
 * Lo que **no** se comprueba aquí es el gate: eso vive en el backend y en el
 * recorrido vertical, que sí llega hasta el `403`. Lo que sí se comprueba es que
 * el guardián de la ruta sigue teniendo sus dos mitades, y eso está en
 * `modules.test.tsx`, que es donde vive el guardián.
 */

const LIST = 'GET /api/v1/suppliers?size=200'

function supplier(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    id: 'aaaaaaaa-0000-0000-0000-000000000001',
    name: 'Fontanería Pérez',
    serviceCategory: 'PLUMBING',
    contactName: 'Luis',
    phone: '600 100 200',
    email: null,
    website: null,
    address: null,
    notes: null,
    createdAt: '2026-08-18T10:00:00Z',
    updatedAt: '2026-08-18T10:00:00Z',
    retiredAt: null,
    createdBy: SESSION_CLAIMS.memberId,
    updatedBy: SESSION_CLAIMS.memberId,
    ...overrides,
  }
}

function page(items: unknown[]) {
  return { status: 200, body: { items, page: 0, size: 200, total: items.length } }
}

/** El catálogo con Proveedores encendido, que es la única forma de llegar a la pantalla. */
function catalogueWithSuppliers() {
  return {
    status: 200,
    body: {
      items: [
        {
          key: 'SUPPLIERS',
          name: 'Proveedores y contactos de servicio',
          description: 'Quién arregla y quién cobra.',
          routePrefix: '/api/v1/suppliers',
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

async function openSuppliers(routes: Record<string, StubbedRoute>) {
  localStorage.setItem('drp.refreshToken', 'refresh-de-mentira')
  window.history.pushState({}, '', '/proveedores')

  const stub = stubFetch({
    'POST /api/v1/auth/refresh': { status: 200, body: fakeTokenPair() },
    'GET /api/v1/modules': catalogueWithSuppliers(),
    ...routes,
  })

  render(<App />)
  await screen.findByRole('heading', { level: 1, name: 'Proveedores' })
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

describe('listado de contactos de servicio', () => {
  it('un hogar que acaba de encender el módulo ve el vacío de verdad, no el del filtro', async () => {
    await openSuppliers({ [LIST]: page([]) })

    // Es el estado NORMAL al encender: la siembra del módulo está vacía a
    // propósito, porque en el core no hay ningún fontanero que heredar.
    expect(await screen.findByText('Todavía no hay ningún contacto')).toBeInTheDocument()
    expect(screen.queryByText('Nada con ese filtro')).not.toBeInTheDocument()
  })

  it('con un filtro puesto y sin resultados, dice que es el filtro', async () => {
    await openSuppliers({
      [LIST]: page([supplier()]),
      'GET /api/v1/suppliers?serviceCategory=VEHICLE&size=200': page([]),
    })

    await screen.findByText('Fontanería Pérez')
    await userEvent.selectOptions(screen.getByLabelText('Filtrar por categoría'), 'VEHICLE')

    // «No hay ninguno» y «no hay ninguno que cumpla esto» piden cosas distintas
    // del usuario, así que no se pueden decir igual.
    expect(await screen.findByText('Nada con ese filtro')).toBeInTheDocument()
  })

  it('la ficha se despliega dentro de la fila, sin cambiar de ruta', async () => {
    await openSuppliers({
      [LIST]: page([supplier()]),
      'GET /api/v1/suppliers/aaaaaaaa-0000-0000-0000-000000000001': {
        status: 200,
        body: {
          supplier: supplier(),
          links: [
            {
              id: 'bbbbbbbb-0000-0000-0000-000000000001',
              targetType: 'LOCATION',
              targetId: 'cccccccc-0000-0000-0000-000000000001',
              targetName: 'Cocina',
              createdAt: '2026-08-18T10:00:00Z',
              createdBy: SESSION_CLAIMS.memberId,
            },
          ],
        },
      },
    })

    const row = await screen.findByRole('button', { name: /Fontanería Pérez/ })
    expect(row).toHaveAttribute('aria-expanded', 'false')

    await userEvent.click(row)

    expect(row).toHaveAttribute('aria-expanded', 'true')
    expect(await screen.findByText('Cocina')).toBeInTheDocument()
    // El nombre lo resuelve el servidor al leer: la pantalla no guarda copia ni
    // pinta el identificador.
    expect(screen.queryByText('cccccccc-0000-0000-0000-000000000001')).not.toBeInTheDocument()
    expect(window.location.pathname).toBe('/proveedores')
  })

  /**
   * El backend responde `409 SUPPLIER_RETIRED` a un enlace sobre un retirado, así
   * que el botón no se pinta: un control que solo sirve para recibir un error es
   * peor que no tenerlo.
   */
  it('a un contacto retirado no se le ofrece enlazar ni retirar otra vez', async () => {
    await openSuppliers({
      'GET /api/v1/suppliers?includeRetired=true&size=200': page([
        supplier({ retiredAt: '2026-08-18T12:00:00Z' }),
      ]),
      [LIST]: page([]),
      'GET /api/v1/suppliers/aaaaaaaa-0000-0000-0000-000000000001': {
        status: 200,
        body: { supplier: supplier({ retiredAt: '2026-08-18T12:00:00Z' }), links: [] },
      },
    })

    await userEvent.click(screen.getByLabelText('Ver también los retirados'))
    await userEvent.click(await screen.findByRole('button', { name: /Fontanería Pérez/ }))

    // El estado se dice con etiqueta y no solo con color.
    expect(screen.getByText('Retirado')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Enlazar Fontanería Pérez/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Retirar/ })).not.toBeInTheDocument()
  })
})

describe('alta de un contacto', () => {
  it('manda lo que se ha escrito y refresca la lista', async () => {
    let created = false
    const stub = await openSuppliers({
      [LIST]: () => (created ? page([supplier({ name: 'Taller Ramírez' })]) : page([])),
      'POST /api/v1/suppliers': () => {
        created = true
        return { status: 201, body: supplier({ name: 'Taller Ramírez' }) }
      },
    })

    await userEvent.click(screen.getByRole('button', { name: 'Añadir contacto' }))
    await userEvent.type(screen.getByLabelText('Nombre'), 'Taller Ramírez')
    await userEvent.selectOptions(screen.getByLabelText('Categoría de servicio'), 'VEHICLE')
    await userEvent.type(screen.getByLabelText('Teléfono'), '600 300 400')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar contacto' }))

    expect(await screen.findByText('Taller Ramírez')).toBeInTheDocument()

    const post = stub.calls.find((call) => call.method === 'POST' && call.url === '/api/v1/suppliers')
    expect(post?.body).toMatchObject({
      name: 'Taller Ramírez',
      serviceCategory: 'VEHICLE',
      phone: '600 300 400',
    })
  })

  /**
   * La regla de «al menos una forma de contacto» **no se replica en el cliente**:
   * la comprueba el servidor y su código se traduce a texto donde ocurrió.
   * Duplicarla aquí daría dos versiones de la misma regla, y la de aquí sería la
   * que se quedara vieja.
   */
  it('el 409 del servidor se enseña como texto, en el sitio donde ocurrió', async () => {
    await openSuppliers({
      [LIST]: page([]),
      'POST /api/v1/suppliers': {
        status: 409,
        body: { code: 'SUPPLIER_CONTACT_REQUIRED', message: 'da igual lo que diga' },
      },
    })

    await userEvent.click(screen.getByRole('button', { name: 'Añadir contacto' }))
    await userEvent.type(screen.getByLabelText('Nombre'), 'Alguien')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar contacto' }))

    expect(
      await screen.findByText('Hace falta al menos un teléfono, un correo o una web.'),
    ).toBeInTheDocument()
  })

  it('el nombre repetido también', async () => {
    await openSuppliers({
      [LIST]: page([]),
      'POST /api/v1/suppliers': {
        status: 409,
        body: { code: 'SUPPLIER_DUPLICATE', message: 'da igual lo que diga' },
      },
    })

    await userEvent.click(screen.getByRole('button', { name: 'Añadir contacto' }))
    await userEvent.type(screen.getByLabelText('Nombre'), 'Fontanería Pérez')
    await userEvent.type(screen.getByLabelText('Teléfono'), '600 100 200')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar contacto' }))

    expect(await screen.findByText('Ya hay un contacto de servicio con ese nombre.')).toBeInTheDocument()
  })
})
