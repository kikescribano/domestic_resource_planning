import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { App } from '../App'
import { SESSION_CLAIMS, fakeTokenPair, stubFetch, type StubbedRoute } from '../test/http'

/**
 * La bandeja de avisos.
 *
 * Lo que se comprueba aquí es lo que solo se ve aquí: que la pantalla **arranca
 * en lo que falta por ver** y no en el histórico, que marcar uno lo tacha, y que
 * un aviso vive en la navegación del hogar y no en la del pulgar.
 *
 * Lo que no se comprueba aquí es de dónde salen los avisos: los escribe el
 * recorrido periódico del backend, y eso lo miden sus pruebas contra PostgreSQL
 * y Mailpit de verdad.
 */

const UNREAD = {
  id: 'aaaaaaaa-0000-0000-0000-000000000001',
  moduleKey: null,
  kind: 'LOANS_OVERDUE',
  title: '2 préstamos han vencido',
  body: 'Taladro, Escalera. Siguen contando como prestados hasta que confirmes la devolución.',
  createdAt: '2026-08-18T03:15:00Z',
  readAt: null,
  readBy: null,
}

const READ = {
  id: 'aaaaaaaa-0000-0000-0000-000000000002',
  moduleKey: 'WAREHOUSE',
  kind: 'STOCK_EXPIRING',
  title: 'Caducan 3 cosas esta semana',
  body: 'Yogures, leche, jamón cocido.',
  createdAt: '2026-08-17T03:15:00Z',
  readAt: '2026-08-17T09:00:00Z',
  readBy: SESSION_CLAIMS.memberId,
}

function page(...items: unknown[]) {
  return { status: 200, body: { items, page: 0, size: 100, total: items.length } }
}

async function openInbox(routes: Record<string, StubbedRoute> = {}) {
  localStorage.setItem('drp.refreshToken', 'refresh-de-mentira')
  window.history.pushState({}, '', '/avisos')

  const stub = stubFetch({
    'POST /api/v1/auth/refresh': { status: 200, body: fakeTokenPair() },
    'GET /api/v1/notices?unreadOnly=true&size=100': page(UNREAD),
    'GET /api/v1/notices?unreadOnly=false&size=100': page(UNREAD, READ),
    ...routes,
  })

  render(<App />)
  await screen.findByRole('heading', { level: 1, name: 'Avisos' })
  return stub
}

describe('la bandeja de avisos', () => {
  beforeEach(() => localStorage.clear())
  afterEach(() => vi.unstubAllGlobals())

  it('arranca en lo que falta por ver, no en el histórico', async () => {
    // Es la pregunta con la que se entra en una bandeja. El histórico está a un
    // clic y no al revés, que es la diferencia entre una pantalla que sirve para
    // algo y una lista que hay que filtrar cada vez.
    await openInbox()

    expect(await screen.findByText('2 préstamos han vencido')).toBeInTheDocument()
    expect(screen.queryByText('Caducan 3 cosas esta semana')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Todos' }))

    expect(await screen.findByText('Caducan 3 cosas esta semana')).toBeInTheDocument()
  })

  it('distingue leído de sin leer con etiqueta y no solo con color', async () => {
    await openInbox()
    await userEvent.click(screen.getByRole('button', { name: 'Todos' }))

    // La regla 4 de la dirección visual: nada se dice solo con color. Quien no
    // distingue el gris del azul tiene que poder leer en qué estado está.
    // Dentro de la bandeja: «Sin leer» es también el rótulo de uno de los dos
    // filtros, y buscarlo suelto encontraría los dos.
    const inbox = within(await screen.findByRole('list', { name: 'Avisos del hogar' }))
    expect(inbox.getByText('Sin leer')).toBeInTheDocument()
    expect(inbox.getByText('Leído')).toBeInTheDocument()
  })

  it('marcar un aviso lo tacha y deja de ofrecer el botón', async () => {
    let marked = false
    const stub = await openInbox({
      'POST /api/v1/notices/aaaaaaaa-0000-0000-0000-000000000001/read': () => {
        marked = true
        return { status: 200, body: { ...UNREAD, readAt: '2026-08-18T10:00:00Z', readBy: SESSION_CLAIMS.memberId } }
      },
      // Tras marcarlo, el filtro de no leídos ya no lo trae: es lo que el
      // backend devolvería, y lo que hace que la lista se vacíe sola.
      'GET /api/v1/notices?unreadOnly=true&size=100': () => (marked ? page() : page(UNREAD)),
    })

    // El título va dentro del nombre accesible: con quince avisos, quince
    // botones idénticos son quince destinos indistinguibles en una lista de
    // botones.
    await userEvent.click(await screen.findByRole('button', { name: /Marcar como leído: 2 préstamos han vencido/ }))

    expect(await screen.findByText('Nada pendiente')).toBeInTheDocument()
    expect(stub.calls.some((call) => call.method === 'POST' && call.url.endsWith('/read'))).toBe(true)
  })

  it('vaciar la bandeja de una vez solo se ofrece si queda algo sin leer', async () => {
    let emptied = false
    await openInbox({
      'POST /api/v1/notices/read': () => {
        emptied = true
        return { status: 204 }
      },
      'GET /api/v1/notices?unreadOnly=true&size=100': () => (emptied ? page() : page(UNREAD)),
    })

    await userEvent.click(await screen.findByRole('button', { name: 'Marcar todo como leído' }))

    expect(await screen.findByText('Nada pendiente')).toBeInTheDocument()
    // Y deja de ofrecerse: un botón que vacía lo que ya está vacío es ruido.
    expect(screen.queryByRole('button', { name: 'Marcar todo como leído' })).not.toBeInTheDocument()
  })

  it('un fallo al cargar se dice, en vez de parecer una bandeja vacía', async () => {
    // Una lista vacía sin explicación es indistinguible de una que no cargó, y
    // las dos piden cosas distintas de quien mira.
    await openInbox({
      'GET /api/v1/notices?unreadOnly=true&size=100': { status: 500, body: { code: 'INTERNAL_ERROR' } },
    })

    // Con margen: un 500 sí se reintenta --hasta dos veces y con espera entre
    // medias-- así que el mensaje tarda más que un error que no se reintenta.
    expect(await screen.findByText('No se han podido cargar los avisos.', {}, { timeout: 8000 })).toBeInTheDocument()
  })

  it('vive en el grupo «Tu hogar» y es una de las cuatro paradas del pulgar', async () => {
    // Con el reagrupado del 2026-08-20 «Avisos» entró en la barra inferior y
    // «Ubicaciones» salió: el orden de la navegación es uno solo y la barra es
    // el recorte de las cuatro primeras paradas de «Tu hogar». La pertenencia
    // no la dice el orden del DOM sino la clase de la parada: lo que no es de
    // la barra va oculto hasta `md`. Que las cinco quepan a 320 px es cosa del
    // recorrido vertical, que lo mide sobre el DOM real.
    await openInbox()

    const navigation = within(screen.getByRole('navigation', { name: 'Principal' }))
    const homeGroup = within(navigation.getByRole('list', { name: 'Tu hogar' }))
    const avisos = homeGroup.getByRole('link', { name: 'Avisos' })

    expect(avisos.closest('li')).not.toHaveClass('hidden')
    expect(navigation.getByRole('link', { name: 'Ubicaciones' }).closest('li')).toHaveClass('hidden')
  })
})
