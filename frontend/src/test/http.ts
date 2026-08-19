import { vi } from 'vitest'

/**
 * Un `fetch` de mentira, para las pruebas de componente.
 *
 * Las pruebas de interfaz no hablan con el backend de verdad: eso es el
 * recorrido vertical, que corre desde el backend con Testcontainers y Mailpit.
 * Aquí lo que se comprueba es que la pantalla hace la llamada que dice hacer y
 * que pinta lo que corresponde con cada respuesta.
 */

export interface StubbedResponse {
  status: number
  body?: unknown
  headers?: Record<string, string>
}

/**
 * Una respuesta fija, o una función que decide en cada llamada.
 *
 * La forma de función existe para lo que una respuesta fija no puede expresar:
 * que la **misma** ruta conteste distinto la primera vez y las siguientes, que es
 * justo el caso de un token que caduca y se renueva.
 *
 * No se usa un getter para eso, aunque parezca equivalente: los getters **se
 * evalúan al hacer spread**, así que juntar dos mapas de respuestas con `{...a,
 * ...b}` los dispararía una vez y congelaría el resultado. Con una función, lo
 * que viaja por el spread es la función.
 */
export type StubbedRoute = StubbedResponse | (() => StubbedResponse)

export interface RecordedCall {
  url: string
  method: string
  body: unknown
  authorization: string | null
}

/**
 * La respuesta que toda pantalla autenticada necesita aunque no hable de
 * módulos.
 *
 * En cuanto hay sesión, el shell pide el catálogo: de él salen la navegación y
 * el guardián de cada ruta de módulo. Declararlo en cada prueba sería repetir la
 * misma línea treinta veces, así que va de base y **cualquier prueba lo
 * sustituye** poniendo la suya con la misma clave.
 */
const CATALOGUE_WITHOUT_MODULES: Record<string, StubbedRoute> = {
  'GET /api/v1/modules': { status: 200, body: { items: [], page: 0, size: 0, total: 0 } },
}

/**
 * Y lo mismo con el estado del hogar, por el mismo motivo: desde la baja de
 * hogar (ADR-012) el shell lo pide en cuanto hay sesión, para decidir si pinta el
 * aviso de la baja. Va de base **sin ninguna baja pedida**, que es el caso normal;
 * cualquier prueba lo sustituye con la misma clave.
 */
const HOUSEHOLD_WITHOUT_CLOSURE: Record<string, StubbedRoute> = {
  'GET /api/v1/households/current': {
    status: 200,
    body: {
      id: '11111111-1111-4111-8111-111111111111',
      name: 'Casa de prueba',
      timeZone: 'Europe/Madrid',
      createdAt: '2026-01-01T10:00:00Z',
      updatedAt: '2026-01-01T10:00:00Z',
      closure: null,
    },
  },
}

export function stubFetch(routes: Record<string, StubbedRoute>) {
  const responses = { ...CATALOGUE_WITHOUT_MODULES, ...HOUSEHOLD_WITHOUT_CLOSURE, ...routes }
  const calls: RecordedCall[] = []

  const implementation = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = typeof input === 'string' ? input : input.toString()
    const method = init?.method ?? 'GET'
    const headers = new Headers(init?.headers)

    calls.push({
      url,
      method,
      body: init?.body ? JSON.parse(String(init.body)) : undefined,
      authorization: headers.get('Authorization'),
    })

    const route = responses[`${method} ${url}`] ?? responses[url]
    if (!route) {
      throw new Error(`Ninguna respuesta preparada para ${method} ${url}`)
    }

    const stub = typeof route === 'function' ? route() : route

    return new Response(stub.body === undefined ? null : JSON.stringify(stub.body), {
      status: stub.status,
      headers: { 'Content-Type': 'application/json', ...stub.headers },
    })
  })

  vi.stubGlobal('fetch', implementation)

  return { calls }
}

/**
 * Un access token con la forma que lee el frontend.
 *
 * No va firmado, y no hace falta: el navegador no verifica nada --no tiene la
 * clave-- y lo que lee del token solo decide qué pinta. Quien verifica es el
 * backend, en cada petición.
 */
export function fakeAccessToken(claims: Record<string, string>): string {
  const encode = (value: unknown) =>
    btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
  return `${encode({ alg: 'HS256' })}.${encode(claims)}.firma-de-mentira`
}

export const SESSION_CLAIMS = {
  sub: '11111111-1111-1111-1111-111111111111',
  memberId: '22222222-2222-2222-2222-222222222222',
  householdId: '33333333-3333-3333-3333-333333333333',
  role: 'HOUSEHOLD_ADMIN',
}

export function fakeTokenPair(claims: Record<string, string> = SESSION_CLAIMS) {
  return {
    accessToken: fakeAccessToken(claims),
    refreshToken: 'refresh-de-mentira',
    expiresIn: 900,
  }
}
