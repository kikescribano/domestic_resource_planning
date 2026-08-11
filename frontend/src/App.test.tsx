import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { App } from './App'
import { fakeTokenPair, stubFetch } from './test/http'

/**
 * Los flujos de enrolamiento vistos desde el navegador.
 *
 * Comprueban lo que solo se puede comprobar aquí: que la pantalla llama a lo que
 * dice llamar, que pinta cada estado y --lo que más fácil es romper sin
 * quererlo-- que **no delata lo que el backend se cuida de no delatar**.
 */

function goTo(path: string) {
  window.history.pushState({}, '', path)
}

beforeEach(() => {
  localStorage.clear()
  goTo('/')
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('acceso', () => {
  it('manda a la pantalla de entrar a quien llega sin sesión', async () => {
    stubFetch({})

    render(<App />)

    expect(await screen.findByRole('heading', { level: 1, name: 'Entrar' })).toBeInTheDocument()
  })

  it('entra con credenciales correctas y aterriza en el hogar', async () => {
    goTo('/entrar')
    stubFetch({
      'POST /api/v1/auth/login': { status: 200, body: fakeTokenPair() },
      'GET /api/v1/users?includeDeactivated=false': {
        status: 200,
        body: { items: [], page: 0, size: 50, total: 0 },
      },
    })

    render(<App />)
    await userEvent.type(screen.getByLabelText('Correo'), 'kike@example.test')
    await userEvent.type(screen.getByLabelText('Contraseña'), 'el gato duerme en el sofa')
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Tu hogar' })).toBeInTheDocument()
  })

  it('cuando el correo no está verificado lo dice y ofrece reenviar el enlace', async () => {
    goTo('/entrar')
    stubFetch({
      'POST /api/v1/auth/login': {
        status: 401,
        body: { code: 'EMAIL_NOT_VERIFIED', message: 'sin verificar' },
      },
    })

    render(<App />)
    await userEvent.type(screen.getByLabelText('Correo'), 'kike@example.test')
    await userEvent.type(screen.getByLabelText('Contraseña'), 'el gato duerme en el sofa')
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }))

    // Es el único motivo de fallo del login que se distingue, y se distingue
    // porque el usuario necesita saber que le toca mirar el correo.
    expect(await screen.findByText(/todavía no está confirmado/i)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Reenviar el enlace' })).toBeInTheDocument()
  })

  it('con la contraseña equivocada no dice si el correo existe', async () => {
    goTo('/entrar')
    stubFetch({
      'POST /api/v1/auth/login': {
        status: 401,
        body: { code: 'UNAUTHORIZED', message: 'Credenciales no válidas' },
      },
    })

    render(<App />)
    await userEvent.type(screen.getByLabelText('Correo'), 'quien-sea@example.test')
    await userEvent.type(screen.getByLabelText('Contraseña'), 'la que no es')
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('El correo o la contraseña no son correctos.')
    // Ni «ese correo no existe» ni «esa contraseña no es la de esa cuenta»: las
    // dos frases convierten la pantalla en un comprobador de quién está
    // registrado.
    expect(alert).not.toHaveTextContent(/no existe|no está registrad/i)
  })
})

describe('alta de un hogar', () => {
  it('crea el hogar y manda a mirar el correo, sin devolver sesión', async () => {
    goTo('/crear-hogar')
    const { calls } = stubFetch({ 'POST /api/v1/households': { status: 202 } })

    render(<App />)
    await userEvent.type(screen.getByLabelText('Nombre del hogar'), 'Casa del Pinar')
    await userEvent.type(screen.getByLabelText('Tu nombre'), 'Kike')
    await userEvent.type(screen.getByLabelText('Tu correo'), 'kike@example.test')
    await userEvent.type(screen.getByLabelText('Contraseña'), 'el gato duerme en el sofa')
    await userEvent.click(screen.getByRole('button', { name: 'Crear el hogar' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Mira tu correo' })).toBeInTheDocument()

    // El alta no devuelve sesión: no hay nada que hacer hasta verificar.
    expect(localStorage.getItem('drp.refreshToken')).toBeNull()

    // Y manda la zona horaria, que el proceso de vencidos necesita.
    const body = calls[0]?.body as { timeZone?: string }
    expect(body.timeZone).toBeTruthy()
  })

  it('muestra el motivo cuando la contraseña no cumple, en el propio campo', async () => {
    goTo('/crear-hogar')
    stubFetch({
      'POST /api/v1/households': {
        status: 400,
        body: {
          code: 'VALIDATION_ERROR',
          message: 'no cumple',
          details: { 'admin.password': 'Esa contraseña es demasiado común; elige otra' },
        },
      },
    })

    render(<App />)
    await userEvent.type(screen.getByLabelText('Nombre del hogar'), 'Casa')
    await userEvent.type(screen.getByLabelText('Tu nombre'), 'Kike')
    await userEvent.type(screen.getByLabelText('Tu correo'), 'kike@example.test')
    await userEvent.type(screen.getByLabelText('Contraseña'), 'contrasena123')
    await userEvent.click(screen.getByRole('button', { name: 'Crear el hogar' }))

    const field = screen.getByLabelText('Contraseña')
    expect(await screen.findByText(/demasiado común/)).toBeInTheDocument()
    // El error no se dice solo con color: el campo queda marcado como inválido y
    // apunta a su mensaje.
    expect(field).toHaveAttribute('aria-invalid', 'true')
    expect(field).toHaveAccessibleDescription(/demasiado común/)
  })
})

describe('recuperar la contraseña', () => {
  it('responde lo mismo aunque el correo no exista', async () => {
    goTo('/recuperar')
    stubFetch({ 'POST /api/v1/auth/password-reset': { status: 202 } })

    render(<App />)
    await userEvent.type(screen.getByLabelText('Tu correo'), 'nadie@example.test')
    await userEvent.click(screen.getByRole('button', { name: 'Enviar el enlace' }))

    // La pantalla dice «si hay una cuenta», nunca «te hemos enviado un correo»:
    // afirmar el envío delataría que la dirección está registrada.
    expect(await screen.findByText(/Si hay una cuenta con nadie@example.test/)).toBeInTheDocument()
  })
})

describe('verificación del correo', () => {
  it('consume el token del enlace y abre sesión', async () => {
    goTo('/verificar-correo?token=un-token-de-prueba')
    const { calls } = stubFetch({
      'POST /api/v1/auth/verify-email': { status: 200, body: fakeTokenPair() },
      'GET /api/v1/users?includeDeactivated=false': {
        status: 200,
        body: { items: [], page: 0, size: 50, total: 0 },
      },
    })

    render(<App />)

    expect(await screen.findByRole('heading', { level: 1, name: 'Tu hogar' })).toBeInTheDocument()
    expect(calls[0]?.body).toEqual({ token: 'un-token-de-prueba' })
  })

  it('no gasta el token dos veces aunque el efecto se vuelva a montar', async () => {
    goTo('/verificar-correo?token=un-token-de-prueba')
    const { calls } = stubFetch({
      'POST /api/v1/auth/verify-email': { status: 200, body: fakeTokenPair() },
      'GET /api/v1/users?includeDeactivated=false': {
        status: 200,
        body: { items: [], page: 0, size: 50, total: 0 },
      },
    })

    render(<App />)
    await screen.findByRole('heading', { level: 1, name: 'Tu hogar' })

    // El token es de un solo uso: una segunda llamada lo invalidaría y la
    // pantalla acabaría diciendo que el enlace no vale justo después de haber
    // funcionado.
    const verifications = calls.filter((call) => call.url.endsWith('/auth/verify-email'))
    expect(verifications).toHaveLength(1)
  })

  it('explica que el enlace ya no vale y ofrece pedir otro', async () => {
    goTo('/verificar-correo?token=caducado')
    stubFetch({
      'POST /api/v1/auth/verify-email': {
        status: 409,
        body: { code: 'VERIFICATION_TOKEN_INVALID', message: 'no vale' },
      },
    })

    render(<App />)

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Este enlace ya no vale' }),
    ).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'pedir uno nuevo' })).toBeInTheDocument()
  })
})

describe('personas del hogar', () => {
  it('lista a los miembros y deja invitar siendo administradora', async () => {
    goTo('/entrar')
    stubFetch({
      'POST /api/v1/auth/login': { status: 200, body: fakeTokenPair() },
      'GET /api/v1/users?includeDeactivated=false': {
        status: 200,
        body: {
          items: [
            {
              id: 'a',
              identityId: 'b',
              name: 'Kike',
              email: 'kike@example.test',
              phone: null,
              role: 'HOUSEHOLD_ADMIN',
              avatarUrl: null,
              lastLoginAt: null,
              emailVerifiedAt: '2026-08-11T10:00:00Z',
              deactivatedAt: null,
            },
          ],
          page: 0,
          size: 50,
          total: 1,
        },
      },
      'GET /api/v1/invitations': { status: 200, body: { items: [], page: 0, size: 50, total: 0 } },
    })

    render(<App />)
    await userEvent.type(screen.getByLabelText('Correo'), 'kike@example.test')
    await userEvent.type(screen.getByLabelText('Contraseña'), 'el gato duerme en el sofa')
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }))

    await userEvent.click(await screen.findByRole('link', { name: 'Personas' }))

    expect(await screen.findByText('kike@example.test')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Enviar la invitación' })).toBeInTheDocument()
  })

  it('un miembro no ve el formulario de invitar', async () => {
    goTo('/entrar')
    stubFetch({
      'POST /api/v1/auth/login': {
        status: 200,
        body: fakeTokenPair({
          sub: '1',
          memberId: '2',
          householdId: '3',
          role: 'HOUSEHOLD_MEMBER',
        }),
      },
      'GET /api/v1/users?includeDeactivated=false': {
        status: 200,
        body: { items: [], page: 0, size: 50, total: 0 },
      },
    })

    render(<App />)
    await userEvent.type(screen.getByLabelText('Correo'), 'miembro@example.test')
    await userEvent.type(screen.getByLabelText('Contraseña'), 'el gato duerme en el sofa')
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }))

    await userEvent.click(await screen.findByRole('link', { name: 'Personas' }))
    await screen.findByRole('heading', { level: 1, name: 'Personas' })

    // Esconderlo es cortesía, no seguridad: quien lo intente igualmente recibe un
    // 403 del backend, que es quien de verdad decide.
    await waitFor(() => {
      expect(screen.queryByRole('button', { name: 'Enviar la invitación' })).not.toBeInTheDocument()
    })
  })
})
