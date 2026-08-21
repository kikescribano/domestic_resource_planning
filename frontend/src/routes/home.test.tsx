import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { App } from '../App'
import { SESSION_CLAIMS, fakeTokenPair, stubFetch, type StubbedRoute } from '../test/http'

/**
 * El panel de «Hogar».
 *
 * Lo que se comprueba aquí es lo que la pantalla promete y solo se ve aquí:
 * que la cabecera lleva el nombre del hogar, que cada tarjeta pinta el `total`
 * de su consulta y se lee entera como un enlace, que las tarjetas de módulo no
 * existen —ni piden nada— con el módulo apagado, y que descartar la ayuda se
 * recuerda en el navegador. El recorrido vertical comprueba lo mismo contra la
 * aplicación de verdad.
 */

function goTo(path: string) {
  window.history.pushState({}, '', path)
}

/** El catálogo entero, con los módulos que se pidan activos. */
function catalogue(...active: string[]) {
  const entry = (key: string, name: string) => ({
    key,
    name,
    description: 'Da igual aquí.',
    routePrefix: `/api/v1/${key.toLowerCase()}`,
    status: active.includes(key) ? 'ACTIVE' : 'INACTIVE',
    activatedAt: active.includes(key) ? '2026-08-18T10:00:00Z' : null,
    deactivatedAt: null,
  })

  return {
    status: 200,
    body: {
      items: [
        entry('MAINTENANCE', 'Mantenimiento'),
        entry('PURCHASING', 'Compras y lista de la compra'),
        entry('SUPPLIERS', 'Proveedores y contactos de servicio'),
        entry('WAREHOUSE', 'Almacén'),
      ],
      page: 0,
      size: 4,
      total: 4,
    },
  }
}

/** Una página vacía cuyo `total` es el dato: lo único que el panel lee. */
function counted(total: number): StubbedRoute {
  return { status: 200, body: { items: [], page: 0, size: 1, total } }
}

/** Abre la aplicación **ya dentro**, reanudando con el refresh token guardado. */
async function resumeAt(path: string, routes: Record<string, StubbedRoute>, role = 'HOUSEHOLD_ADMIN') {
  localStorage.setItem('drp.refreshToken', 'refresh-de-mentira')
  goTo(path)

  const stub = stubFetch({
    'POST /api/v1/auth/refresh': { status: 200, body: fakeTokenPair({ ...SESSION_CLAIMS, role }) },
    ...routes,
  })

  render(<App />)
  await screen.findByRole('navigation', { name: 'Principal' })
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

describe('el panel de «Hogar»', () => {
  it('la cabecera concatena el nombre del hogar, y las tarjetas de bienvenida ya no están', async () => {
    await resumeAt('/', {})

    // El nombre sale de `GET /households/current`, que el stub de base responde
    // con «Casa de prueba». Mientras carga, el título dice «Hogar» a secas.
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Hogar Casa de prueba' }),
    ).toBeInTheDocument()

    expect(screen.queryByText('Tu papel aquí')).not.toBeInTheDocument()
    expect(screen.queryByText('Cómo se llama')).not.toBeInTheDocument()
  })

  it('cada tarjeta del core pinta su contador y se lee entera como enlace', async () => {
    await resumeAt('/', {
      'GET /api/v1/notices?unreadOnly=true&size=1': counted(3),
      'GET /api/v1/loans?open=true&size=1': counted(2),
      'GET /api/v1/loans?status=OVERDUE&size=1': counted(1),
      'GET /api/v1/assets?size=1': counted(12),
    })

    // El nombre accesible es el texto entero de la tarjeta: quien no la ve la
    // entiende igual que quien la ve.
    expect(await screen.findByRole('link', { name: 'Avisos 3 sin leer' })).toBeInTheDocument()
    expect(await screen.findByRole('link', { name: 'Inventario 12 cosas en casa' })).toBeInTheDocument()
    expect(
      await screen.findByRole('link', { name: 'Préstamos 2 fuera de casa 1 vencido' }),
    ).toBeInTheDocument()
  })

  it('sin ningún vencido, la tarjeta de préstamos no enseña el distintivo', async () => {
    await resumeAt('/', {
      'GET /api/v1/loans?open=true&size=1': counted(2),
    })

    expect(await screen.findByRole('link', { name: 'Préstamos 2 fuera de casa' })).toBeInTheDocument()
    expect(screen.queryByText(/vencido/)).not.toBeInTheDocument()
  })

  it('con los módulos apagados no hay tarjetas de módulo, y no se pide nada a sus rutas', async () => {
    const { calls } = await resumeAt('/', {})

    expect(await screen.findByRole('link', { name: 'Avisos 0 sin leer' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Almacén/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Mantenimiento/ })).not.toBeInTheDocument()

    // La primera capa del gate: con el módulo apagado la petición no sale.
    const moduleCalls = calls.filter((call) =>
      /\/(warehouse|purchasing|maintenance|suppliers)\//.test(call.url),
    )
    expect(moduleCalls).toHaveLength(0)
  })

  it('un módulo activo trae su tarjeta con sus contadores, y la cuenta de módulos activos', async () => {
    await resumeAt('/', {
      'GET /api/v1/modules': catalogue('WAREHOUSE'),
      'GET /api/v1/warehouse/stock?expiringWithinDays=30&size=1': counted(5),
      'GET /api/v1/warehouse/stock?belowMinimum=true&size=1': counted(2),
    })

    // Con margen: es la tarjeta que más viajes encadena —renovación, catálogo
    // y recién entonces sus dos contadores— y con la suite entera en paralelo
    // el segundo de serie del `findByRole` se queda corto.
    expect(
      await screen.findByRole(
        'link',
        { name: 'Almacén 5 caducan en 30 días 2 bajo mínimo' },
        { timeout: 5000 },
      ),
    ).toBeInTheDocument()
    expect(
      await screen.findByRole('link', { name: 'Módulos del hogar 1 de 4 activos' }, { timeout: 5000 }),
    ).toBeInTheDocument()
  })

  it('quien no administra no ve la tarjeta de módulos, como no ve su parada', async () => {
    await resumeAt('/', {}, 'HOUSEHOLD_MEMBER')

    expect(await screen.findByRole('link', { name: 'Avisos 0 sin leer' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Módulos del hogar/ })).not.toBeInTheDocument()
  })

  it('descartar la ayuda la quita y lo deja apuntado en el navegador', async () => {
    await resumeAt('/', {})

    expect(await screen.findByText('Por dónde empezar')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Descartar el aviso' }))

    expect(screen.queryByText('Por dónde empezar')).not.toBeInTheDocument()
    expect(localStorage.getItem('drp.homeIntro')).toBe('descartado')
  })

  it('con la ayuda ya descartada, no vuelve a pintarse', async () => {
    localStorage.setItem('drp.homeIntro', 'descartado')
    await resumeAt('/', {})

    expect(await screen.findByRole('link', { name: 'Avisos 0 sin leer' })).toBeInTheDocument()
    expect(screen.queryByText('Por dónde empezar')).not.toBeInTheDocument()
  })
})
