import { render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { App } from '../App'
import { SESSION_CLAIMS, fakeTokenPair, stubFetch, type StubbedRoute } from '../test/http'

/**
 * «Tu foto», en la pantalla de cuenta.
 *
 * Lo que se fija aquí es quién es «yo»: no hay `GET /users/me`, así que la
 * sección se busca en `GET /users` por el `identityId` del token. La lista
 * llega **sin orden garantizado**, y dar por hecho que el primero eres tú
 * enseña el avatar de otra persona en cuanto el hogar tiene más de un miembro
 * —y entonces subir una foto responde 204 y la pantalla no refleja nada—.
 * Por eso las dos pruebas ponen a otra persona en la primera posición.
 */

function member(overrides: Record<string, unknown>) {
  return {
    id: 'aaaaaaaa-0000-4000-8000-000000000001',
    identityId: 'bbbbbbbb-0000-4000-8000-000000000001',
    name: 'Marta Ruiz Alonso',
    email: 'marta@example.test',
    phone: null,
    role: 'HOUSEHOLD_ADMIN',
    avatarUrl: null,
    lastLoginAt: null,
    emailVerifiedAt: '2026-08-11T10:00:00Z',
    deactivatedAt: null,
    ...overrides,
  }
}

function mine(overrides: Record<string, unknown> = {}) {
  return member({
    id: 'aaaaaaaa-0000-4000-8000-000000000002',
    identityId: SESSION_CLAIMS.sub,
    name: 'Javier Serrano Gil',
    email: 'javier@example.test',
    ...overrides,
  })
}

function goTo(path: string) {
  window.history.pushState({}, '', path)
}

async function resumeAtAccount(routes: Record<string, StubbedRoute>) {
  localStorage.setItem('drp.refreshToken', 'refresh-de-mentira')
  goTo('/cuenta')

  const stub = stubFetch({
    'POST /api/v1/auth/refresh': { status: 200, body: fakeTokenPair() },
    ...routes,
  })

  render(<App />)
  await screen.findByRole('heading', { name: 'Tu foto' })
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

describe('tu foto', () => {
  it('las iniciales son las tuyas aunque no seas el primero de la lista', async () => {
    await resumeAtAccount({
      'GET /api/v1/users?includeDeactivated=false': {
        status: 200,
        body: { items: [member({}), mine()], page: 0, size: 50, total: 2 },
      },
    })

    expect(await screen.findByText('JG')).toBeInTheDocument()
    expect(screen.queryByText('MA')).not.toBeInTheDocument()
  })

  it('con tu foto puesta lo dice, aunque el primero de la lista no tenga ninguna', async () => {
    await resumeAtAccount({
      'GET /api/v1/users?includeDeactivated=false': {
        status: 200,
        body: {
          items: [member({}), mine({ avatarUrl: '/files/avatar-firmado.webp' })],
          page: 0,
          size: 50,
          total: 2,
        },
      },
    })

    expect(await screen.findByText('Cambiar la foto')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Quitarla' })).toBeInTheDocument()
    expect(screen.queryByText('Elegir una foto')).not.toBeInTheDocument()
  })
})
