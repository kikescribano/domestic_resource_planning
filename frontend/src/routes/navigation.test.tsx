import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { App } from '../App'
import { SESSION_CLAIMS, fakeTokenPair, stubFetch, type StubbedRoute } from '../test/http'

/**
 * La columna encogida a iconos, que solo existe en escritorio.
 *
 * Lo que se comprueba aquí es lo que solo se ve aquí: que la elección persiste
 * en `drp.nav` y se restaura al volver, y que encogida **ningún enlace pierde
 * su nombre accesible** — la etiqueta pasa a sr-only, no desaparece. Que los
 * iconos midan lo que deben y el foco se vea es del recorrido vertical, que lo
 * mide sobre el DOM real.
 */

function goTo(path: string) {
  window.history.pushState({}, '', path)
}

async function resumeAt(path: string, routes: Record<string, StubbedRoute> = {}) {
  localStorage.setItem('drp.refreshToken', 'refresh-de-mentira')
  goTo(path)

  const stub = stubFetch({
    'POST /api/v1/auth/refresh': { status: 200, body: fakeTokenPair(SESSION_CLAIMS) },
    'GET /api/v1/users?includeDeactivated=false': {
      status: 200,
      body: { items: [], page: 0, size: 50, total: 0 },
    },
    'GET /api/v1/modules': { status: 200, body: { items: [], page: 0, size: 0, total: 0 } },
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

describe('la columna encogida a iconos', () => {
  it('encoger persiste la elección, conserva los nombres y se deshace', async () => {
    await resumeAt('/')

    await userEvent.click(screen.getByRole('button', { name: 'Encoger la navegación' }))
    expect(localStorage.getItem('drp.nav')).toBe('collapsed')

    // Encogida, la etiqueta pasa a sr-only **dentro del enlace**: el nombre
    // accesible no cambia, así que el enlace se sigue encontrando por él.
    const navigation = within(screen.getByRole('navigation', { name: 'Principal' }))
    const inventory = navigation.getByRole('link', { name: 'Inventario' })
    expect(inventory.querySelector('span')).toHaveClass('md:sr-only')

    // Del bloque de la marca queda solo la salida: «Cuenta» es un destino y a
    // icono se confundía con una parada. Se recupera ensanchando.
    expect(screen.queryByRole('link', { name: 'Cuenta' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Salir' })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Ensanchar la navegación' }))
    expect(localStorage.getItem('drp.nav')).toBe('expanded')
    expect(inventory.querySelector('span')).not.toHaveClass('md:sr-only')
    expect(screen.getByRole('link', { name: 'Cuenta' })).toBeInTheDocument()
  })

  it('la elección encogida se restaura al volver a entrar', async () => {
    localStorage.setItem('drp.nav', 'collapsed')
    await resumeAt('/')

    expect(screen.getByRole('button', { name: 'Ensanchar la navegación' })).toBeInTheDocument()
  })
})
