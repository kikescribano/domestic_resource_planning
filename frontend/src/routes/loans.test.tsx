import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { App } from '../App'
import { fakeTokenPair, stubFetch, type StubbedResponse } from '../test/http'

/**
 * Las dos pantallas de préstamos.
 *
 * La externa es la que más se comprueba aquí, y no por capricho: es **la única
 * del producto que se abre sin sesión**, así que lo que la protege no es el
 * shell sino ella misma. Lo que se mide es que no enseñe lo que no le llega y
 * que un enlace roto no delate nada.
 */

function goTo(path: string) {
  window.history.pushState({}, '', path)
}

const SESSION_ROUTES: Record<string, StubbedResponse> = {
  'POST /api/v1/auth/login': { status: 200, body: fakeTokenPair() },
  'GET /api/v1/users?includeDeactivated=false': {
    status: 200,
    body: { items: [], page: 0, size: 50, total: 0 },
  },
}

function loan(overrides: Record<string, unknown> = {}) {
  return {
    id: 'loan-1',
    assetId: 'asset-1',
    assetName: 'Taladro',
    lender: { userId: '22222222-2222-2222-2222-222222222222', external: null },
    borrower: { userId: null, external: { name: 'Vecino del 3.º', email: 'vecino@example.test', phone: null } },
    status: 'ACTIVE',
    startedAt: '2026-08-01T09:00:00Z',
    dueAt: '2026-08-15T09:00:00Z',
    returnedAt: null,
    conditionAtStart: null,
    conditionOnReturn: null,
    notes: 'Con la broca de widia',
    createdBy: '22222222-2222-2222-2222-222222222222',
    updatedBy: null,
    ...overrides,
  }
}

function externalLoan(overrides: Record<string, unknown> = {}) {
  return {
    id: 'loan-1',
    assetName: 'Taladro',
    role: 'BORROWER',
    status: 'ACTIVE',
    startedAt: '2026-08-01T09:00:00Z',
    dueAt: '2026-08-15T09:00:00Z',
    returnedAt: null,
    conditionAtStart: null,
    conditionOnReturn: null,
    ...overrides,
  }
}

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
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('los préstamos del hogar', () => {
  it('lista lo que está fuera de casa y deja confirmar la devolución', async () => {
    const stub = await signInAndVisit('Préstamos', {
      'GET /api/v1/loans?open=true&size=200': {
        status: 200,
        body: { items: [loan()], page: 0, size: 200, total: 1 },
      },
      'POST /api/v1/loans/loan-1/return': {
        status: 200,
        body: loan({ status: 'RETURNED', returnedAt: '2026-08-10T10:00:00Z' }),
      },
      'GET /api/v1/loans?size=200': { status: 200, body: { items: [], page: 0, size: 200, total: 0 } },
    })

    expect(await screen.findByText('Taladro')).toBeInTheDocument()
    // El nombre del externo sí se ve desde dentro de casa: aquí no hay nada que
    // acotar, la proyección acotada es solo la del token.
    expect(screen.getByText(/Vecino del 3\.º/)).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Ya lo tengo' }))

    const returned = stub.calls.find((call) => call.url.endsWith('/loans/loan-1/return'))
    expect(returned?.method).toBe('POST')
  })

  it('anota en qué estado vuelve, y sin anotar no manda el campo', async () => {
    const stub = await signInAndVisit('Préstamos', {
      'GET /api/v1/loans?open=true&size=200': {
        status: 200,
        body: { items: [loan({ conditionAtStart: 'GOOD' })], page: 0, size: 200, total: 1 },
      },
      'POST /api/v1/loans/loan-1/return': {
        status: 200,
        body: loan({ status: 'RETURNED', returnedAt: '2026-08-10T10:00:00Z', conditionOnReturn: 'DAMAGED' }),
      },
      'GET /api/v1/loans?size=200': { status: 200, body: { items: [], page: 0, size: 200, total: 0 } },
    })

    // Lo que se anotó al prestar se lee en la tarjeta: es la mitad que hace
    // comparable lo que se anote al devolver.
    expect(await screen.findByText(/Salió: Buen estado/)).toBeInTheDocument()

    await userEvent.selectOptions(screen.getByLabelText('Vuelve en estado'), 'DAMAGED')
    await userEvent.click(screen.getByRole('button', { name: 'Ya lo tengo' }))

    const returned = stub.calls.find((call) => call.url.endsWith('/loans/loan-1/return'))
    expect(returned?.body).toEqual({ conditionOnReturn: 'DAMAGED' })
  })

  it('confirmar sin anotar nada no manda cuerpo: ausente y vacío significan lo mismo', async () => {
    const stub = await signInAndVisit('Préstamos', {
      'GET /api/v1/loans?open=true&size=200': {
        status: 200,
        body: { items: [loan()], page: 0, size: 200, total: 1 },
      },
      'POST /api/v1/loans/loan-1/return': {
        status: 200,
        body: loan({ status: 'RETURNED', returnedAt: '2026-08-10T10:00:00Z' }),
      },
      'GET /api/v1/loans?size=200': { status: 200, body: { items: [], page: 0, size: 200, total: 0 } },
    })

    await screen.findByText('Taladro')
    await userEvent.click(screen.getByRole('button', { name: 'Ya lo tengo' }))

    const returned = stub.calls.find((call) => call.url.endsWith('/loans/loan-1/return'))
    expect(returned?.body).toBeUndefined()
  })

  it('el formulario solo ofrece lo que se puede prestar', async () => {
    // Un consumible no se presta y uno ya prestado tampoco: la pantalla filtra
    // por tipo y estado en lugar de dejar que la API responda 409 a algo que ya
    // se sabía.
    const stub = await signInAndVisit('Préstamos', {
      'GET /api/v1/loans?open=true&size=200': {
        status: 200,
        body: { items: [], page: 0, size: 200, total: 0 },
      },
      'GET /api/v1/assets?type=DURABLE&status=AVAILABLE&size=200': {
        status: 200,
        body: { items: [], page: 0, size: 200, total: 0 },
      },
      'GET /api/v1/users?size=200': { status: 200, body: { items: [], page: 0, size: 200, total: 0 } },
    })

    await userEvent.click(screen.getByRole('button', { name: 'Prestar algo' }))

    await screen.findByLabelText('Qué prestas')
    const asked = stub.calls.map((call) => call.url)
    expect(asked.some((url) => url.includes('type=DURABLE') && url.includes('status=AVAILABLE'))).toBe(true)
  })
})

describe('la vista externa', () => {
  it('se abre con el token de la URL, sin sesión, y enseña solo lo suyo', async () => {
    goTo('/prestamo?id=loan-1&token=token-del-correo')
    const stub = stubFetch({
      'GET /api/v1/loans/loan-1': { status: 200, body: externalLoan() },
    })

    render(<App />)

    expect(await screen.findByRole('heading', { level: 1, name: 'Taladro' })).toBeInTheDocument()
    expect(screen.getByText('Prestado')).toBeInTheDocument()

    // La credencial es la de la URL y no la de la sesión, que aquí no existe.
    const asked = stub.calls.find((call) => call.url.includes('/loans/loan-1'))
    expect(asked?.authorization).toBe('Bearer token-del-correo')

    // Y no se pinta nada del hogar. Aunque el backend ya lo acota, la pantalla
    // no debe ser el sitio donde eso se dé por supuesto.
    expect(screen.queryByText(/Vecino del 3\.º/)).not.toBeInTheDocument()
    expect(screen.queryByText(/broca/)).not.toBeInTheDocument()
    // Ni el shell: sin sesión no hay navegación a la que volver.
    expect(screen.queryByRole('link', { name: 'Inventario' })).not.toBeInTheDocument()
  })

  it('confirma la devolución y deja de ofrecerla', async () => {
    goTo('/prestamo?id=loan-1&token=token-del-correo')
    let returned = false
    stubFetch({
      'GET /api/v1/loans/loan-1': () => ({
        status: 200,
        body: returned
          ? externalLoan({ status: 'RETURNED', returnedAt: '2026-08-10T10:00:00Z' })
          : externalLoan(),
      }),
      'POST /api/v1/loans/loan-1/return': () => {
        returned = true
        return { status: 200, body: externalLoan({ status: 'RETURNED', returnedAt: '2026-08-10T10:00:00Z' }) }
      },
    })

    render(<App />)
    await screen.findByRole('heading', { level: 1, name: 'Taladro' })

    await userEvent.click(screen.getByRole('button', { name: 'Ya lo he devuelto' }))

    expect(await screen.findByText(/ya está cerrado/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Ya lo he devuelto' })).not.toBeInTheDocument()
  })

  it('el externo dice en qué estado lo devuelve, y ve en qué estado salió', async () => {
    goTo('/prestamo?id=loan-1&token=token-del-correo')
    let returned = false
    const stub = stubFetch({
      'GET /api/v1/loans/loan-1': () => ({
        status: 200,
        body: returned
          ? externalLoan({
              status: 'RETURNED',
              returnedAt: '2026-08-10T10:00:00Z',
              conditionAtStart: 'GOOD',
              conditionOnReturn: 'DAMAGED',
            })
          : externalLoan({ conditionAtStart: 'GOOD' }),
      }),
      'POST /api/v1/loans/loan-1/return': () => {
        returned = true
        return { status: 200, body: externalLoan({ status: 'RETURNED', conditionOnReturn: 'DAMAGED' }) }
      },
    })

    render(<App />)
    await screen.findByRole('heading', { level: 1, name: 'Taladro' })

    // En qué estado se lo dieron: no dice nada del hogar y le protege a quien
    // lo tiene. Se mira junto a su rótulo, porque «Buen estado» es también una
    // de las opciones del desplegable de abajo.
    expect(screen.getByText('Salió').parentElement).toHaveTextContent('Buen estado')

    await userEvent.selectOptions(screen.getByLabelText('En qué estado lo devuelves'), 'DAMAGED')
    await userEvent.click(screen.getByRole('button', { name: 'Ya lo he devuelto' }))

    const confirmed = stub.calls.find((call) => call.url.endsWith('/loans/loan-1/return'))
    expect(confirmed?.body).toEqual({ conditionOnReturn: 'DAMAGED' })
    // Y lo escrito vuelve a la pantalla, para que quien lo anotó lo vea hecho.
    expect(await screen.findByText('Volvió')).toBeInTheDocument()
  })

  it('quien prestó ve el texto del otro extremo', async () => {
    goTo('/prestamo?id=loan-1&token=token-del-correo')
    stubFetch({
      'GET /api/v1/loans/loan-1': { status: 200, body: externalLoan({ role: 'LENDER' }) },
    })

    render(<App />)

    // La misma anatomía y el texto del revés: quien prestó reclama, quien
    // recibió confirma. Es la razón por la que `role` viaja en la respuesta.
    expect(await screen.findByRole('button', { name: 'Ya me lo han devuelto' })).toBeInTheDocument()
  })

  it('un enlace que no vale se ve igual falle por lo que falle', async () => {
    // La propiedad no es que el texto evite una palabra: es que los distintos
    // motivos **no se distinguen**. Un token caducado, uno revocado y un
    // préstamo que no existe tienen que producir la misma pantalla, porque si
    // difirieran el enlace serviría para averiguar qué préstamos hay.
    async function renderFailure(response: StubbedResponse): Promise<string> {
      goTo('/prestamo?id=loan-1&token=token-cualquiera')
      stubFetch({ 'GET /api/v1/loans/loan-1': response })

      const view = render(<App />)
      await screen.findByRole('heading', { level: 1, name: 'Este enlace ya no vale' })
      const text = document.body.textContent ?? ''
      view.unmount()
      vi.restoreAllMocks()
      return text
    }

    const rejected = await renderFailure({
      status: 401,
      body: { code: 'UNAUTHORIZED', message: 'Falta el token o no es válido' },
    })
    const missing = await renderFailure({
      status: 404,
      body: { code: 'NOT_FOUND', message: 'Préstamo no encontrado' },
    })

    expect(rejected).toBe(missing)
    // Y lo que sí dice: qué puede hacer una persona real, que es hablar con
    // quien le prestó la cosa, porque desde aquí no hay nada más.
    expect(rejected).toContain('Habla con la persona')
  })

  it('sin token en la URL no llega a preguntar', async () => {
    goTo('/prestamo')
    const stub = stubFetch({})

    render(<App />)

    expect(await screen.findByRole('heading', { level: 1, name: 'Este enlace ya no vale' })).toBeInTheDocument()
    expect(stub.calls).toHaveLength(0)
  })
})
