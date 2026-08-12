import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { App } from '../App'
import { api, connectSessionRenewal } from '../api/client'
import {
  SESSION_CLAIMS,
  fakeAccessToken,
  fakeTokenPair,
  stubFetch,
  type StubbedResponse,
  type StubbedRoute,
} from '../test/http'

/**
 * La renovación de la sesión.
 *
 * Estaba escrita y **muerta**: el refresh token se guardaba en `localStorage` y
 * no lo leía nadie, así que a los quince minutos toda petición respondía `401`,
 * TanStack Query no la reintentaba —está configurado para no reintentar 401— y
 * la pantalla se quedaba en blanco sin explicar nada.
 *
 * Lo que se prueba aquí es lo que solo se ve desde el navegador: que el `401`
 * dispara **una** renovación, que la petición original se rehace con el token
 * nuevo, y que cuando la renovación tampoco vale se acaba en la pantalla de
 * entrar **diciendo por qué** en lugar de en una pantalla muerta.
 */

function goTo(path: string) {
  window.history.pushState({}, '', path)
}

const EXPIRED: StubbedResponse = {
  status: 401,
  body: { code: 'UNAUTHORIZED', message: 'Falta el token o no es válido' },
}

const NO_USERS = { status: 200, body: { items: [], page: 0, size: 50, total: 0 } }

/** Un par nuevo, distinguible del primero por el `sid`. */
function renewedPair() {
  return {
    accessToken: fakeAccessToken({ ...SESSION_CLAIMS, sid: 'sesion-renovada' }),
    refreshToken: 'refresh-renovado',
    expiresIn: 900,
  }
}

async function signIn(responses: Record<string, StubbedRoute>) {
  goTo('/entrar')
  const stub = stubFetch({
    'POST /api/v1/auth/login': { status: 200, body: fakeTokenPair() },
    'GET /api/v1/users?includeDeactivated=false': NO_USERS,
    ...responses,
  })

  render(<App />)
  await userEvent.type(screen.getByLabelText('Correo'), 'kike@example.test')
  await userEvent.type(screen.getByLabelText('Contraseña'), 'el gato duerme en el sofa')
  await userEvent.click(screen.getByRole('button', { name: 'Entrar' }))
  await screen.findByRole('heading', { level: 1, name: 'Tu hogar' })

  return stub
}

beforeEach(() => {
  localStorage.clear()
  goTo('/')
})

afterEach(() => {
  // El puente es estado de módulo: sin descolgarlo, el proveedor de una prueba
  // seguiría atendiendo las renovaciones de la siguiente.
  connectSessionRenewal(null)
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('renovación ante un 401', () => {
  it('renueva y rehace la petición original con el token nuevo', async () => {
    // Se navega a «Personas» porque la pantalla de inicio no consulta nada: sin
    // una petición de verdad no hay 401 que renovar, y la prueba pasaría por
    // vacía en vez de por buena.
    let listings = 0
    const stub = await signIn({
      'POST /api/v1/auth/refresh': { status: 200, body: renewedPair() },
      // «Personas» pide dos cosas. Solo caduca una: así se comprueba el rescate
      // de esa sin que la otra enturbie el recuento de renovaciones.
      'GET /api/v1/invitations': { status: 200, body: { items: [], page: 0, size: 50, total: 0 } },
      'GET /api/v1/users?includeDeactivated=false': () => {
        listings += 1
        // La primera caduca; de la segunda en adelante, con el token nuevo, va.
        return listings === 1
          ? EXPIRED
          : {
              status: 200,
              body: {
                items: [
                  {
                    id: SESSION_CLAIMS.memberId,
                    identityId: SESSION_CLAIMS.sub,
                    name: 'Kike',
                    email: 'kike@example.test',
                    phone: null,
                    role: 'HOUSEHOLD_ADMIN',
                    avatarUrl: null,
                    lastLoginAt: null,
                    emailVerifiedAt: '2026-08-13T00:00:00Z',
                    deactivatedAt: null,
                  },
                ],
                page: 0,
                size: 50,
                total: 1,
              },
            }
      },
    })

    await userEvent.click(screen.getByRole('link', { name: 'Personas' }))

    // El listado acaba pintándose: la renovación rescata la petición en vez de
    // dejar la pantalla muerta.
    expect(await screen.findByText('kike@example.test')).toBeInTheDocument()

    expect(stub.calls.filter((call) => call.url.endsWith('/auth/refresh'))).toHaveLength(1)

    // Y el reintento lleva el token NUEVO, no el que acababa de caducar.
    const listCalls = stub.calls.filter((call) => call.url.includes('/users?'))
    expect(listCalls.length).toBeGreaterThanOrEqual(2)
    expect(listCalls.at(-1)?.authorization).toBe(`Bearer ${renewedPair().accessToken}`)
  })

  it('varias peticiones que caducan a la vez comparten UNA sola renovación', async () => {
    // El backend ROTA los refresh tokens: usar uno invalida el anterior. Con una
    // renovación por petición, la primera dejaría sin token a las demás y esas
    // cerrarían una sesión que estaba viva.
    const stub = await signIn({
      'POST /api/v1/auth/refresh': { status: 200, body: renewedPair() },
      'GET /api/v1/categories?includeRetired=false&size=200': EXPIRED,
      'GET /api/v1/articles?size=200': EXPIRED,
    })

    // El catálogo lanza las dos consultas a la vez, y las dos caducan.
    await userEvent.click(screen.getByRole('link', { name: 'Catálogo' }))

    await waitFor(() => {
      expect(stub.calls.some((call) => call.url.endsWith('/auth/refresh'))).toBe(true)
    })
    expect(stub.calls.filter((call) => call.url.endsWith('/auth/refresh'))).toHaveLength(1)
  })

  it('si la renovación tampoco vale, acaba en la pantalla de entrar y dice por qué', async () => {
    await signIn({
      'POST /api/v1/auth/refresh': {
        status: 401,
        body: { code: 'UNAUTHORIZED', message: 'Token no válido' },
      },
      'GET /api/v1/locations?size=200': EXPIRED,
    })

    await userEvent.click(screen.getByRole('link', { name: 'Sitios' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Entrar' })).toBeInTheDocument()
    expect(
      screen.getByText(/Tu sesión ha caducado por inactividad/),
    ).toBeInTheDocument()
    // Y no deja el token muerto detrás.
    expect(localStorage.getItem('drp.refreshToken')).toBeNull()
  })

  it('una contraseña actual equivocada NO dispara renovación ni cierra la sesión', async () => {
    // Es un 401 en una petición autenticada, igual que el token caducado, y por
    // eso se distinguen por el código: `CURRENT_PASSWORD_INVALID` es «esa
    // credencial no es», no «tu sesión se acabó». Renovar aquí gastaría un
    // refresh token por una errata, y echaría de la aplicación si fallase.
    const stub = await signIn({
      'POST /api/v1/auth/password': {
        status: 401,
        body: { code: 'CURRENT_PASSWORD_INVALID', message: 'La contraseña actual no es correcta' },
      },
    })

    await userEvent.click(screen.getByRole('link', { name: 'Cuenta' }))
    await userEvent.type(screen.getByLabelText('Contraseña actual'), 'la que no es')
    await userEvent.type(screen.getByLabelText('Contraseña nueva'), 'una contrasena nueva larga')
    await userEvent.click(screen.getByRole('button', { name: 'Cambiar la contraseña' }))

    await waitFor(() => {
      expect(stub.calls.some((call) => call.url.endsWith('/auth/password'))).toBe(true)
    })

    expect(stub.calls.filter((call) => call.url.endsWith('/auth/refresh'))).toHaveLength(0)
    // Sigue dentro: no se le ha echado por equivocarse tecleando.
    expect(screen.getByRole('heading', { level: 1, name: 'Tu cuenta' })).toBeInTheDocument()
  })
})

describe('reanudar al cargar la página', () => {
  it('con un refresh token guardado, entra sin pasar por la pantalla de entrar', async () => {
    // Es el caso de recargar la pestaña: el access token vive solo en memoria y
    // se pierde, pero el refresh sigue en `localStorage`. Antes esto echaba de la
    // aplicación aunque la sesión siguiera viva en el servidor.
    localStorage.setItem('drp.refreshToken', 'refresh-de-una-visita-anterior')
    stubFetch({
      'POST /api/v1/auth/refresh': { status: 200, body: renewedPair() },
      'GET /api/v1/users?includeDeactivated=false': NO_USERS,
    })

    render(<App />)

    expect(await screen.findByRole('heading', { level: 1, name: 'Tu hogar' })).toBeInTheDocument()
  })

  it('mientras reanuda espera, en vez de redirigir a la pantalla de entrar', async () => {
    // Es la parte que no se puede comprobar con la renovación ya resuelta: si la
    // ruta protegida decidiera antes de tiempo, la redirección a `/entrar` ya
    // habría ocurrido y no se deshace sola cuando la sesión llega.
    localStorage.setItem('drp.refreshToken', 'refresh-de-una-visita-anterior')

    let completeRefresh: (() => void) | undefined
    const refreshHeld = new Promise<void>((resolve) => {
      completeRefresh = resolve
    })

    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = typeof input === 'string' ? input : input.toString()
        if (url.endsWith('/auth/refresh')) {
          await refreshHeld
          return new Response(JSON.stringify(renewedPair()), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          })
        }
        return new Response(JSON.stringify(NO_USERS.body), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        })
      }),
    )

    render(<App />)

    // Con la renovación en el aire: ni hogar, ni pantalla de entrar. Espera.
    expect(await screen.findByText('Recuperando tu sesión')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { level: 1, name: 'Entrar' })).not.toBeInTheDocument()

    completeRefresh?.()

    expect(await screen.findByRole('heading', { level: 1, name: 'Tu hogar' })).toBeInTheDocument()
  })

  it('con un token guardado que ya no vale, lleva a entrar y en silencio', async () => {
    localStorage.setItem('drp.refreshToken', 'refresh-caducado')
    stubFetch({
      'POST /api/v1/auth/refresh': {
        status: 401,
        body: { code: 'UNAUTHORIZED', message: 'Token no válido' },
      },
    })

    render(<App />)

    expect(await screen.findByRole('heading', { level: 1, name: 'Entrar' })).toBeInTheDocument()
    // Sin aviso de caducidad: quien abre la aplicación no estaba haciendo nada
    // que se le haya interrumpido, así que no hay nada que explicarle.
    expect(screen.queryByText(/Tu sesión ha caducado/)).not.toBeInTheDocument()
    expect(localStorage.getItem('drp.refreshToken')).toBeNull()
  })

  it('sin nada guardado no intenta reanudar: va directo a entrar', async () => {
    const stub = stubFetch({})

    render(<App />)

    expect(await screen.findByRole('heading', { level: 1, name: 'Entrar' })).toBeInTheDocument()
    expect(stub.calls.filter((call) => call.url.endsWith('/auth/refresh'))).toHaveLength(0)
  })
})

describe('el puente con el cliente HTTP', () => {
  it('sin proveedor registrado, un 401 no intenta renovar nada', async () => {
    connectSessionRenewal(null)
    const stub = stubFetch({
      'GET /api/v1/users?includeDeactivated=false': EXPIRED,
    })

    await expect(api.listUsers('un-token-cualquiera')).rejects.toMatchObject({ status: 401 })

    expect(stub.calls.filter((call) => call.url.endsWith('/auth/refresh'))).toHaveLength(0)
  })
})
