import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { App } from '../App'
import { SESSION_CLAIMS, fakeTokenPair, stubFetch, type StubbedRoute } from '../test/http'

/**
 * La baja del hogar y el cierre de cuenta, en la pantalla (ADR-012).
 *
 * Lo que se comprueba aquí es lo que **solo se ve aquí**: que la confirmación
 * escrita mantiene el botón deshabilitado hasta que lo tecleado coincide exacto,
 * que el aviso de la baja sale con su fecha **en cualquier pantalla** y no solo
 * en la del hogar, y que quien no administra ve el aviso pero no la zona de
 * peligro.
 *
 * Lo que no se comprueba aquí es la purga, que no tiene pantalla y vive en la
 * batería del backend, donde se puede mover el reloj.
 */

const HOUSEHOLD_ID = '11111111-1111-4111-8111-111111111111'

function household(closure: Record<string, string> | null) {
  return {
    status: 200,
    body: {
      id: HOUSEHOLD_ID,
      name: 'Casa de Kike',
      timeZone: 'Europe/Madrid',
      createdAt: '2026-01-01T10:00:00Z',
      updatedAt: '2026-01-01T10:00:00Z',
      closure,
    },
  }
}

const REQUESTED = {
  requestedAt: '2026-08-19T10:00:00Z',
  requestedBy: '22222222-2222-4222-8222-222222222222',
  effectiveAt: '2026-09-18T10:00:00Z',
}

function goTo(path: string) {
  window.history.pushState({}, '', path)
}

async function resumeAt(path: string, routes: Record<string, StubbedRoute> = {}, role = 'HOUSEHOLD_ADMIN') {
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

beforeEach(() => {
  localStorage.clear()
  goTo('/')
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('la zona de peligro de la baja del hogar', () => {
  it('el botón está deshabilitado hasta que lo escrito coincide exacto', async () => {
    await resumeAt('/', { 'GET /api/v1/households/current': household(null) })

    const zone = await screen.findByRole('heading', { name: 'Dar de baja el hogar' })
    expect(zone).toBeInTheDocument()

    const button = screen.getByRole('button', { name: 'Dar de baja el hogar' })
    expect(button).toBeDisabled()

    // A medias no vale, y es lo que separa esto de un «¿seguro?»: hay que leer
    // qué se está borrando y teclear justo eso.
    const field = screen.getByLabelText('Escribe «Casa de Kike» para confirmarlo')
    await userEvent.type(field, 'Casa de')
    expect(button).toBeDisabled()

    // Y con distintas mayúsculas tampoco: la comparación es exacta a propósito,
    // porque aquí la tolerancia juega en contra.
    await userEvent.clear(field)
    await userEvent.type(field, 'casa de kike')
    expect(button).toBeDisabled()

    await userEvent.clear(field)
    await userEvent.type(field, 'Casa de Kike')
    expect(button).toBeEnabled()
  })

  it('confirmar pide la baja al servidor', async () => {
    const stub = await resumeAt('/', {
      'GET /api/v1/households/current': household(null),
      'POST /api/v1/households/current/closure': household(REQUESTED),
    })

    await screen.findByRole('heading', { name: 'Dar de baja el hogar' })
    await userEvent.type(screen.getByLabelText('Escribe «Casa de Kike» para confirmarlo'), 'Casa de Kike')
    await userEvent.click(screen.getByRole('button', { name: 'Dar de baja el hogar' }))

    expect(
      stub.calls.some(
        (call) => call.method === 'POST' && call.url === '/api/v1/households/current/closure',
      ),
    ).toBe(true)
  })

  it('quien no administra no ve la zona de peligro del hogar', async () => {
    await resumeAt('/', { 'GET /api/v1/households/current': household(null) }, 'HOUSEHOLD_MEMBER')

    await screen.findByRole('heading', { level: 1, name: 'Tu hogar' })
    expect(screen.queryByRole('heading', { name: 'Dar de baja el hogar' })).not.toBeInTheDocument()
  })
})

describe('el aviso mientras dura la gracia', () => {
  it('lleva la fecha en la que el hogar desaparece', async () => {
    await resumeAt('/', { 'GET /api/v1/households/current': household(REQUESTED) })

    // La fecha y no «en 30 días»: es la diferencia entre saberlo y tener que
    // contar.
    expect(await screen.findByText(/Este hogar se borrará el 18 de septiembre de 2026/)).toBeInTheDocument()
  })

  it('sale también fuera de la pantalla del hogar', async () => {
    await resumeAt('/catalogo', { 'GET /api/v1/households/current': household(REQUESTED) })

    // Vive en el shell y no en «Tu hogar» porque durante la gracia todo sigue
    // funcionando igual: alguien puede pasarse treinta días en el inventario sin
    // volver a la pantalla del hogar.
    expect(await screen.findByText(/Este hogar se borrará el/)).toBeInTheDocument()
  })

  it('lo ve quien no administra, aunque no pueda cancelarla', async () => {
    await resumeAt('/', { 'GET /api/v1/households/current': household(REQUESTED) }, 'HOUSEHOLD_MEMBER')

    // Enterarse no es lo mismo que poder hacerlo.
    expect(await screen.findByText(/Este hogar se borrará el/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Cancelar la baja' })).not.toBeInTheDocument()
  })

  it('con la baja pedida, la pantalla ofrece cancelarla sin ninguna fricción', async () => {
    const stub = await resumeAt('/', {
      'GET /api/v1/households/current': household(REQUESTED),
      'DELETE /api/v1/households/current/closure': household(null),
    })

    // Deshacer algo destructivo no merece confirmación escrita: ponérsela sería
    // castigar el arrepentimiento, que es justo lo que la gracia permite.
    const cancel = await screen.findByRole('button', { name: 'Cancelar la baja' })
    await userEvent.click(cancel)

    expect(
      stub.calls.some(
        (call) => call.method === 'DELETE' && call.url === '/api/v1/households/current/closure',
      ),
    ).toBe(true)
  })
})

describe('cerrar la cuenta', () => {
  it('pide escribir CERRAR y llama a la operación de la identidad, no a la del hogar', async () => {
    const stub = await resumeAt('/cuenta', {
      'DELETE /api/v1/users/me': { status: 204 },
      'POST /api/v1/auth/logout': { status: 204 },
    })

    await screen.findByRole('heading', { name: 'Cerrar tu cuenta' })
    const button = screen.getByRole('button', { name: 'Cerrar mi cuenta' })
    expect(button).toBeDisabled()

    await userEvent.type(screen.getByLabelText('Escribe «CERRAR» para confirmarlo'), 'CERRAR')
    expect(button).toBeEnabled()
    await userEvent.click(button)

    expect(stub.calls.some((call) => call.method === 'DELETE' && call.url === '/api/v1/users/me')).toBe(true)
    // Y **no** la del hogar: cerrar una cuenta no se lleva la casa por delante.
    expect(
      stub.calls.some((call) => call.url === '/api/v1/households/current/closure'),
    ).toBe(false)
  })

  it('el error del último administrador se explica con lo que hay que hacer', async () => {
    await resumeAt('/cuenta', {
      'DELETE /api/v1/users/me': {
        status: 409,
        body: { code: 'USER_LAST_ADMIN', message: 'Eres el único administrador activo del hogar' },
      },
    })

    await screen.findByRole('heading', { name: 'Cerrar tu cuenta' })
    await userEvent.type(screen.getByLabelText('Escribe «CERRAR» para confirmarlo'), 'CERRAR')
    await userEvent.click(screen.getByRole('button', { name: 'Cerrar mi cuenta' }))

    const alert = await screen.findByRole('alert')
    expect(within(alert).getByText(/Nombra antes a otra/)).toBeInTheDocument()

    // Y lo escrito **no se borra**: obligar a teclearlo otra vez tras un fallo
    // castiga a quien no tuvo la culpa.
    expect(screen.getByLabelText('Escribe «CERRAR» para confirmarlo')).toHaveValue('CERRAR')
  })
})
