import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { App } from '../App'
import { todayIn } from '../api/client'
import { fakeTokenPair, stubFetch, type StubbedRoute } from '../test/http'

/**
 * La pantalla de mantenimiento.
 *
 * Lo que se comprueba aquí es lo que solo se ve en el navegador y no en el
 * backend:
 *
 * - Que **la frontera contra el planificador de tareas se sostiene en pantalla**:
 *   no hay ningún campo que asigne el trabajo a una persona. Si algún día aparece
 *   uno, esta prueba se entera.
 * - Que **con el módulo de proveedores apagado la pantalla no se rompe ni explica
 *   nada**: el selector de a quién se llama simplemente no está, porque la lista
 *   llega vacía del servidor.
 * - Que el selector **agrupa por categoría** traduciendo el identificador que el
 *   puerto entrega, que es la razón de que no haya hecho falta ensancharlo.
 * - Que el estado de una fecha se dice **con etiqueta y no solo con color**.
 *
 * Lo que **no** se comprueba aquí es el gate: eso vive en el backend y en el
 * recorrido vertical, que sí llega hasta el `403`. Y el guardián de la ruta tiene
 * sus dos mitades comprobadas en `modules.test.tsx`, que es donde vive.
 */

const PLANS = 'GET /api/v1/maintenance/plans?size=200'
const MACHINES = 'GET /api/v1/maintenance/machines?size=200'
const HISTORY = 'GET /api/v1/maintenance/interventions?size=200'
const CALLERS = 'GET /api/v1/maintenance/suppliers'

const ASSET = 'aaaaaaaa-0000-0000-0000-000000000001'
const PLAN = 'bbbbbbbb-0000-0000-0000-000000000001'

/** La del hogar que `stubFetch` da de base. Ver [inDays] y `el día que se propone`. */
const HOUSEHOLD_ZONE = 'Europe/Madrid'

/**
 * Una fecha relativa a hoy: una fija caduca y hace que la prueba falle sola un
 * día cualquiera.
 *
 * **Cuenta desde el día del hogar**, que es el que la pantalla usa desde que el
 * «hoy» de una regla de calendario es el suyo. Con `toISOString()` esto daba el
 * día de Greenwich y las dos mitades de la prueba dejaban de hablar del mismo
 * día durante las últimas horas de la tarde.
 */
function inDays(days: number): string {
  const day = new Date(`${todayIn(HOUSEHOLD_ZONE)}T00:00:00Z`)
  day.setUTCDate(day.getUTCDate() + days)
  return day.toISOString().slice(0, 10)
}

function plan(overrides: Record<string, unknown> = {}) {
  return {
    id: PLAN,
    assetId: ASSET,
    name: 'Revisión anual',
    intervalMonths: 12,
    leadDays: 15,
    nextDueOn: inDays(200),
    lastPerformedOn: null,
    supplierId: null,
    notes: null,
    cancelledAt: null,
    createdAt: '2026-08-19T10:00:00Z',
    updatedAt: '2026-08-19T10:00:00Z',
    ...overrides,
  }
}

function machine(overrides: Record<string, unknown> = {}) {
  return {
    assetId: ASSET,
    name: 'Caldera',
    manualDocumentId: null,
    notes: null,
    planCount: 1,
    nextDueOn: inDays(200),
    ...overrides,
  }
}

function page(items: unknown[]) {
  return { status: 200, body: { items, page: 0, size: 200, total: items.length } }
}

/** El catálogo con Mantenimiento encendido, que es la única forma de llegar a la pantalla. */
function catalogueWithMaintenance() {
  return {
    status: 200,
    body: {
      items: [
        {
          key: 'MAINTENANCE',
          name: 'Mantenimiento',
          description: 'Planes de mantenimiento preventivo, intervenciones e histórico.',
          routePrefix: '/api/v1/maintenance',
          status: 'ACTIVE',
          activatedAt: '2026-08-19T10:00:00Z',
          deactivatedAt: null,
        },
      ],
      page: 0,
      size: 1,
      total: 1,
    },
  }
}

async function openMaintenance(routes: Record<string, StubbedRoute>) {
  localStorage.setItem('drp.refreshToken', 'refresh-de-mentira')
  window.history.pushState({}, '', '/mantenimiento')

  const stub = stubFetch({
    'POST /api/v1/auth/refresh': { status: 200, body: fakeTokenPair() },
    'GET /api/v1/modules': catalogueWithMaintenance(),
    [MACHINES]: page([machine()]),
    [PLANS]: page([]),
    [HISTORY]: page([]),
    [CALLERS]: { status: 200, body: [] },
    ...routes,
  })

  render(<App />)
  await screen.findByRole('heading', { level: 1, name: 'Mantenimiento' })
  return stub
}

beforeEach(() => {
  localStorage.clear()
  window.history.pushState({}, '', '/')
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('los planes', () => {
  it('un hogar sin planes ve el vacío de verdad, con el motivo', async () => {
    await openMaintenance({ [PLANS]: page([]) })

    expect(await screen.findByText('Ningún plan todavía')).toBeInTheDocument()
  })

  it('el estado de una fecha lleva etiqueta y no solo color', async () => {
    await openMaintenance({
      [PLANS]: page([
        plan({ nextDueOn: inDays(-3) }),
        plan({ id: 'p2', name: 'Cambio de filtro', nextDueOn: inDays(5) }),
        plan({ id: 'p3', name: 'Deshollinar', nextDueOn: inDays(300) }),
      ]),
      [`GET /api/v1/maintenance/plans/${PLAN}`]: {
        status: 200,
        body: { plan: plan(), machineName: 'Caldera', supplier: null, interventions: [] },
      },
      'GET /api/v1/maintenance/plans/p2': {
        status: 200,
        body: { plan: plan({ id: 'p2' }), machineName: 'Caldera', supplier: null, interventions: [] },
      },
      'GET /api/v1/maintenance/plans/p3': {
        status: 200,
        body: { plan: plan({ id: 'p3' }), machineName: 'Caldera', supplier: null, interventions: [] },
      },
    })

    // La regla 4 de la dirección visual: nada se dice solo con color. Y son tres
    // hechos distintos, no tres tonos del mismo.
    expect(await screen.findByText('Se ha pasado')).toBeInTheDocument()
    expect(screen.getByText('Toca pronto')).toBeInTheDocument()
    expect(screen.getByText('Al día')).toBeInTheDocument()
  })

  /**
   * **La frontera contra el planificador de tareas, comprobada en pantalla.**
   *
   * De CMMS es el cuándo y del planificador el quién lo hace, y esa línea se rompe
   * el día que alguien añada aquí un selector de persona. La prueba mira lo que no
   * hay, que es lo único que puede vigilar una frontera contra un módulo que
   * todavía no existe.
   */
  it('no hay ningún campo que asigne el trabajo a una persona', async () => {
    await openMaintenance({ [PLANS]: page([]) })

    await screen.findByLabelText('Qué se revisa')

    expect(screen.queryByLabelText(/qui[eé]n lo hace/i)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/responsable/i)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/a qui[eé]n le toca/i)).not.toBeInTheDocument()
  })

  /**
   * **El día que el campo propone es el del hogar, no el de Greenwich.**
   *
   * El reloj se para a las 23:30 UTC, que en `Europe/Madrid` es la 01:30 del día
   * siguiente: es la franja entera en la que esta pantalla proponía **ayer** y el
   * selector se negaba a ofrecer hoy. Se para el reloj y no se cuenta desde la
   * hora real porque el defecto solo aparece de madrugada, que es exactamente
   * como se coló.
   *
   * Se falsea `Date` y no los temporizadores: React Query y `userEvent` los
   * necesitan de verdad, y lo único que esta prueba tiene que congelar es qué día
   * es.
   */
  it('el día que se propone y el máximo del campo son los del hogar', async () => {
    vi.useFakeTimers({ toFake: ['Date'], now: new Date('2026-07-15T23:30:00Z') })

    try {
      await openMaintenance({
        [PLANS]: page([plan()]),
        [`GET /api/v1/maintenance/plans/${PLAN}`]: {
          status: 200,
          body: { plan: plan(), machineName: 'Caldera', supplier: null, interventions: [] },
        },
      })

      await userEvent.click(await screen.findByRole('button', { name: 'Ya está hecho' }))

      const cuando = await screen.findByLabelText('Cuándo se hizo')
      // El 16 y no el 15: en la cocina ya es el día siguiente.
      expect(cuando).toHaveValue('2026-07-16')
      // Y el tope también, que es la otra mitad: con el 15 el selector no dejaba
      // elegir el día que la persona tiene delante.
      expect(cuando).toHaveAttribute('max', '2026-07-16')
    } finally {
      vi.useRealTimers()
    }
  })

  it('crear un plan manda los meses y la fecha, y nada más', async () => {
    const stub = await openMaintenance({
      [PLANS]: page([]),
      [`GET /api/v1/maintenance/machines?q=Caldera&size=200`]: page([machine()]),
      'POST /api/v1/maintenance/plans': { status: 201, body: plan() },
    })

    await userEvent.type(await screen.findByRole('combobox', { name: 'Qué máquina' }), 'Caldera')
    await userEvent.click(await screen.findByRole('option', { name: /Caldera/ }))
    await userEvent.type(screen.getByLabelText('Qué se revisa'), 'Revisión anual')
    await userEvent.click(screen.getByRole('button', { name: 'Crear plan' }))

    // Por la ruta y no solo por el método: la primera petición de toda pantalla
    // autenticada es la renovación del token, que también es un POST.
    const call = stub.calls.find(
      (entry) => entry.method === 'POST' && entry.url.endsWith('/maintenance/plans'),
    )
    expect(call?.body).toMatchObject({ assetId: ASSET, name: 'Revisión anual', intervalMonths: 12 })
    // Ni responsable ni día asignado: no es que no se envíen, es que no existen.
    expect(call?.body).not.toHaveProperty('assigneeId')
  })
})

describe('con el módulo de proveedores apagado', () => {
  it('el selector de a quién se llama no está, y la pantalla no explica nada', async () => {
    // El servidor responde 200 con lista vacía y **no** 403: la degradación la
    // pone él, así que aquí no hay ninguna rama que dependa de otro módulo.
    await openMaintenance({ [PLANS]: page([]), [CALLERS]: { status: 200, body: [] } })

    await screen.findByLabelText('Qué se revisa')
    expect(screen.queryByLabelText('A quién se llama')).not.toBeInTheDocument()

    // Y se puede crear el plan igual, que es lo que hace que la degradación no
    // recorte ninguna funcionalidad: llamar a alguien es un adorno del plan.
    expect(screen.getByRole('button', { name: 'Crear plan' })).toBeEnabled()
  })

  /**
   * **La razón de que este hito no haya ensanchado el puerto de dato maestro.**
   *
   * El servidor entrega el identificador de la categoría en `detail` y no filtra
   * por ella; lo que hacía falta —distinguir de un vistazo a quién se llama— se
   * resuelve agrupando aquí y traduciendo el identificador al rótulo en
   * castellano, que es un dato y lo pone el cliente.
   */
  it('con proveedores encendido agrupa por categoría, con su rótulo en castellano', async () => {
    await openMaintenance({
      [PLANS]: page([]),
      [CALLERS]: {
        status: 200,
        body: [
          { id: 's1', name: 'Calderas Ruiz', detail: 'HEATING_COOLING' },
          { id: 's2', name: 'Fontanería Pérez', detail: 'PLUMBING' },
        ],
      },
    })

    expect(await screen.findByLabelText('A quién se llama')).toBeInTheDocument()
    expect(screen.getByRole('group', { name: 'Climatización' })).toBeInTheDocument()
    expect(screen.getByRole('group', { name: 'Fontanería' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'Calderas Ruiz' })).toBeInTheDocument()
  })
})

describe('el histórico', () => {
  it('una correctiva se distingue de una preventiva con etiqueta', async () => {
    await openMaintenance({
      [HISTORY]: page([
        {
          id: 'i1',
          assetId: ASSET,
          planId: null,
          kind: 'CORRECTIVE',
          performedOn: inDays(-10),
          summary: 'Se cambió la válvula',
          supplierId: null,
          supplier: 'Calderas Ruiz',
          notes: null,
          createdAt: '2026-08-19T10:00:00Z',
        },
      ]),
    })

    // `role="tab"` y no `button`: son tres vistas del mismo recurso y no tres
    // páginas, así que el rol dice de qué se trata a quien no ve la pantalla.
    await userEvent.click(await screen.findByRole('tab', { name: 'El histórico' }))

    expect(await screen.findByText('Se cambió la válvula')).toBeInTheDocument()
    expect(screen.getByText('Correctiva')).toBeInTheDocument()
    // El nombre de aquel día, copiado al registrarla.
    expect(screen.getByText(/lo hizo Calderas Ruiz/)).toBeInTheDocument()
  })
})

describe('las máquinas', () => {
  it('una máquina sin planes lo dice, en vez de fingir que está al día', async () => {
    await openMaintenance({
      [MACHINES]: page([machine({ planCount: 0, nextDueOn: null })]),
    })

    await userEvent.click(await screen.findByRole('tab', { name: 'Las máquinas' }))

    expect(await screen.findByText('Sin planes')).toBeInTheDocument()
    expect(screen.getByText(/todavía no tiene ningún plan/)).toBeInTheDocument()
  })
})
