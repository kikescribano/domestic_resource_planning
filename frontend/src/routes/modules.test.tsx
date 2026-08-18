import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { App } from '../App'
import { SESSION_CLAIMS, fakeTokenPair, stubFetch, type StubbedRoute } from '../test/http'

/**
 * Los módulos del hogar y lo que hacen con la navegación.
 *
 * Lo que se comprueba aquí es lo que solo se ve aquí: que encender un módulo
 * **añade su parada** a la navegación y apagarlo la quita, que entrar a mano en
 * la ruta de uno apagado lleva a la pantalla que lo ofrece y no a un error, y
 * que quien no administra ve el catálogo sin poder tocarlo.
 *
 * Lo que no se comprueba aquí es el gate de verdad: eso vive en el backend y en
 * el recorrido vertical, que sí llega hasta el `403`.
 */

function goTo(path: string) {
  window.history.pushState({}, '', path)
}

function catalogue(...active: string[]) {
  const entry = (key: string, name: string, description: string, prefix: string) => ({
    key,
    name,
    description,
    routePrefix: prefix,
    status: active.includes(key) ? 'ACTIVE' : 'INACTIVE',
    activatedAt: active.includes(key) ? '2026-08-18T10:00:00Z' : null,
    deactivatedAt: null,
  })

  return {
    status: 200,
    body: {
      items: [
        entry('MAINTENANCE', 'Mantenimiento', 'Planes y revisiones.', '/api/v1/maintenance'),
        entry('PURCHASING', 'Compras y lista de la compra', 'Qué falta y qué está pedido.', '/api/v1/purchasing'),
        entry('SUPPLIERS', 'Proveedores y contactos de servicio', 'Quién arregla y quién cobra.', '/api/v1/suppliers'),
        entry('WAREHOUSE', 'Almacén', 'Existencias, mínimos y caducidad.', '/api/v1/warehouse'),
      ],
      page: 0,
      size: 4,
      total: 4,
    },
  }
}

/**
 * Abre la aplicación **ya dentro** y en la ruta que se pide.
 *
 * Reanuda con el refresh token guardado en lugar de pasar por el login, que es
 * lo que permite aterrizar directamente en la ruta de un módulo: es
 * exactamente lo que hace quien tiene la URL en un marcador, y es el camino por
 * el que se llega a una ruta apagada.
 */
async function resumeAt(path: string, routes: Record<string, StubbedRoute>, role = 'HOUSEHOLD_ADMIN') {
  localStorage.setItem('drp.refreshToken', 'refresh-de-mentira')
  goTo(path)

  const stub = stubFetch({
    'POST /api/v1/auth/refresh': { status: 200, body: fakeTokenPair({ ...SESSION_CLAIMS, role }) },
    'GET /api/v1/users?includeDeactivated=false': {
      status: 200,
      body: { items: [], page: 0, size: 50, total: 0 },
    },
    ...routes,
  })

  render(<App />)
  await screen.findByRole('navigation', { name: 'Principal' })
  return stub
}

function mainNavigation() {
  return within(screen.getByRole('navigation', { name: 'Principal' }))
}

beforeEach(() => {
  localStorage.clear()
  goTo('/')
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('navegación con módulos', () => {
  it('un hogar sin módulos activos conserva sus paradas del core y la puerta para encender alguno', async () => {
    await resumeAt('/', { 'GET /api/v1/modules': catalogue() })

    const nav = mainNavigation()
    for (const label of ['Hogar', 'Inventario', 'Sitios', 'Catálogo', 'Préstamos', 'Personas', 'Archivo', 'Cuenta']) {
      expect(nav.getByRole('link', { name: label })).toBeInTheDocument()
    }
    expect(await nav.findByRole('link', { name: 'Módulos del hogar' })).toBeInTheDocument()

    // Y ninguna parada de módulo: lo apagado no está en la navegación, que es la
    // tercera capa del gate.
    expect(nav.queryByRole('link', { name: 'Proveedores' })).not.toBeInTheDocument()
    expect(nav.queryByRole('link', { name: 'Almacén' })).not.toBeInTheDocument()
  })

  it('un módulo activo tiene su parada propia, separada de las del core', async () => {
    await resumeAt('/', { 'GET /api/v1/modules': catalogue('WAREHOUSE') })

    expect(await mainNavigation().findByRole('link', { name: 'Almacén' })).toBeInTheDocument()
    expect(mainNavigation().queryByRole('link', { name: 'Proveedores' })).not.toBeInTheDocument()
  })

  it('encender un módulo añade su parada, y apagarlo la quita', async () => {
    let active = false
    await resumeAt('/modulos', {
      'GET /api/v1/modules': () => (active ? catalogue('SUPPLIERS') : catalogue()),
      'POST /api/v1/modules/SUPPLIERS/activation': () => {
        active = true
        return { status: 200, body: {} }
      },
      'DELETE /api/v1/modules/SUPPLIERS/activation': () => {
        active = false
        return { status: 200, body: {} }
      },
    })

    await userEvent.click(
      await screen.findByRole('button', { name: 'Encender Proveedores y contactos de servicio' }),
    )
    expect(await mainNavigation().findByRole('link', { name: 'Proveedores' })).toBeInTheDocument()

    await userEvent.click(
      await screen.findByRole('button', { name: 'Apagar Proveedores y contactos de servicio' }),
    )
    await vi.waitFor(() =>
      expect(mainNavigation().queryByRole('link', { name: 'Proveedores' })).not.toBeInTheDocument(),
    )
  })
})

describe('pantalla de módulos', () => {
  it('dice que apagar no borra nada, que es lo que decide si alguien se atreve a probar', async () => {
    await resumeAt('/modulos', { 'GET /api/v1/modules': catalogue('WAREHOUSE') })

    expect(await screen.findByText(/no borra nada/)).toBeInTheDocument()
  })

  it('quien no administra ve el catálogo entero, sin botones y sabiendo a quién pedírselo', async () => {
    await resumeAt('/modulos', { 'GET /api/v1/modules': catalogue() }, 'HOUSEHOLD_MEMBER')

    // El catálogo entero: ver que un módulo existe no es poder encenderlo, y sin
    // verlo no habría forma de pedirlo.
    expect(await screen.findByText('Almacén')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Encender/ })).not.toBeInTheDocument()
    expect(screen.getByText(/Quien administra el hogar es quien los enciende/)).toBeInTheDocument()
  })
})

describe('entrar a mano en la ruta de un módulo', () => {
  it('apagado y administrando, la pantalla lo ofrece en lugar de dar un error', async () => {
    let active = false
    await resumeAt('/proveedores', {
      'GET /api/v1/modules': () => (active ? catalogue('SUPPLIERS') : catalogue()),
      'POST /api/v1/modules/SUPPLIERS/activation': () => {
        active = true
        return { status: 200, body: {} }
      },
    })

    expect(await screen.findByText('Este módulo está apagado')).toBeInTheDocument()

    await userEvent.click(
      screen.getByRole('button', { name: 'Encender Proveedores y contactos de servicio' }),
    )

    // Se queda donde estaba y ahora sí entra: es la diferencia práctica entre el
    // `403` que devuelve el backend y un `404`.
    expect(await screen.findByText(/todavía sin nada que enseñar/)).toBeInTheDocument()
    expect(await mainNavigation().findByRole('link', { name: 'Proveedores' })).toBeInTheDocument()
  })

  it('apagado y sin administrar, dice a quién pedírselo y no ofrece el botón', async () => {
    await resumeAt('/mantenimiento', { 'GET /api/v1/modules': catalogue() }, 'HOUSEHOLD_MEMBER')

    expect(await screen.findByText('Este módulo está apagado')).toBeInTheDocument()
    expect(screen.getByText(/Pídeselo a quien administra el hogar/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Encender/ })).not.toBeInTheDocument()
  })
})
