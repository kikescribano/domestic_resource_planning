import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { App } from '../App'
import { fakeTokenPair, stubFetch, type StubbedResponse } from '../test/http'

/**
 * El catálogo, el árbol de ubicaciones y las operaciones de existencias, vistos
 * desde el navegador.
 *
 * Lo que se comprueba aquí es lo que solo se ve aquí: que cada pantalla llama a
 * la operación que dice llamar y con el cuerpo que corresponde, y que pinta bien
 * los tres desenlaces que el Hito 2 introduce y que se confunden con facilidad
 * —éxito, error de regla de negocio y **éxito con aviso**.
 */

function goTo(path: string) {
  window.history.pushState({}, '', path)
}

const CATEGORIES = {
  status: 200,
  body: {
    items: [
      {
        id: 'cat-1',
        name: 'Alimentación',
        notes: null,
        icon: 'UTENSILS',
        color: 'MOSS',
        createdAt: '2026-08-12T00:00:00Z',
        retiredAt: null,
      },
      // Sin cara a propósito: es el caso normal de una categoría que nadie ha
      // tocado, y el que tiene que salir del marcador sin inventar un color.
      {
        id: 'cat-2',
        name: 'Herramientas',
        notes: null,
        icon: null,
        color: null,
        createdAt: '2026-08-12T00:00:00Z',
        retiredAt: null,
      },
    ],
    page: 0,
    size: 200,
    total: 2,
  },
}

const CAMPING = { id: 'tag-1', name: 'Camping', createdAt: '2026-08-12T00:00:00Z', retiredAt: null }
const HERENCIA = { id: 'tag-2', name: 'Herencia', createdAt: '2026-08-12T00:00:00Z', retiredAt: null }

const TAGS = { status: 200, body: { items: [CAMPING, HERENCIA], page: 0, size: 200, total: 2 } }

const LOCATIONS = {
  status: 200,
  body: {
    items: [
      { id: 'loc-1', name: 'Casa del Pinar', type: 'HOUSE', parentLocationId: null, capacity: null, notes: null },
      { id: 'loc-2', name: 'Despensa', type: 'ROOM', parentLocationId: 'loc-1', capacity: null, notes: null },
    ],
    page: 0,
    size: 200,
    total: 2,
  },
}

const SUGAR = {
  id: 'art-1',
  name: 'Azúcar',
  categoryId: 'cat-1',
  category: 'Alimentación',
  unit: 'GRAM',
  brand: null,
  model: null,
  barcode: null,
  packSize: null,
  notes: null,
  retiredAt: null,
}

const ARTICLES = { status: 200, body: { items: [SUGAR], page: 0, size: 200, total: 1 } }

function stockItem(overrides: Record<string, unknown> = {}) {
  return {
    id: 'asset-1',
    name: 'Azúcar',
    type: 'CONSUMABLE',
    categoryId: 'cat-1',
    category: 'Alimentación',
    articleId: 'art-1',
    ownerId: '22222222-2222-2222-2222-222222222222',
    location: { type: 'LOCATION', id: 'loc-2' },
    status: 'AVAILABLE',
    quantity: 300,
    unit: 'GRAM',
    serialNumber: null,
    acquiredOn: null,
    condition: null,
    categoryIcon: null,
    categoryColor: null,
    tags: [],
    notes: null,
    warnings: [],
    ...overrides,
  }
}

/** Un duradero, que es el único que lleva número de serie y fecha de adquisición. */
function durable(overrides: Record<string, unknown> = {}) {
  return {
    id: 'asset-2',
    name: 'Taladro',
    type: 'DURABLE',
    categoryId: 'cat-2',
    category: 'Herramientas',
    articleId: null,
    ownerId: null,
    location: { type: 'LOCATION', id: 'loc-2' },
    status: 'AVAILABLE',
    quantity: null,
    unit: null,
    serialNumber: null,
    acquiredOn: null,
    condition: null,
    categoryIcon: null,
    categoryColor: null,
    tags: [],
    notes: null,
    warnings: [],
    ...overrides,
  }
}

/** Lo que hace falta para que el shell se pinte tras entrar. */
const SESSION_ROUTES: Record<string, StubbedResponse> = {
  'POST /api/v1/auth/login': { status: 200, body: fakeTokenPair() },
  'GET /api/v1/users?includeDeactivated=false': {
    status: 200,
    body: { items: [], page: 0, size: 50, total: 0 },
  },
  // Desde el Hito 4 del cierre de huecos, el catálogo de etiquetas lo piden el
  // listado --para su filtro-- y la ficha --para el campo--. Va aquí y no en
  // cada prueba porque no es lo que ninguna de ellas mide; las que sí lo miden
  // lo sustituyen con la misma clave.
  'GET /api/v1/tags?size=200': TAGS,
}

/**
 * Entra y navega. La sesión no se restaura de `localStorage` --solo el refresh
 * token vive ahí, y el access token se queda en memoria-- así que una prueba de
 * pantalla autenticada tiene que pasar por el login como una persona.
 */
async function signInAndVisit(link: string, responses: Record<string, StubbedResponse>) {
  goTo('/entrar')
  const stub = stubFetch({ ...SESSION_ROUTES, ...responses })

  render(<App />)
  await userEvent.type(screen.getByLabelText('Correo'), 'kike@example.test')
  await userEvent.type(screen.getByLabelText('Contraseña'), 'el gato duerme en el sofa')
  await userEvent.click(screen.getByRole('button', { name: 'Entrar' }))
  await screen.findByRole('heading', { level: 1, name: 'Hogar' })

  await userEvent.click(screen.getByRole('link', { name: link }))
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

describe('catálogo', () => {
  it('crea un artículo con su categoría y su unidad', async () => {
    const { calls } = await signInAndVisit('Catálogo', {
      'GET /api/v1/categories?includeRetired=false&size=200': CATEGORIES,
      'GET /api/v1/articles?size=200': ARTICLES,
      'POST /api/v1/articles': { status: 201, body: SUGAR },
    })

    await userEvent.type(screen.getByLabelText('Nombre del artículo'), 'Lentejas')
    await userEvent.selectOptions(screen.getByLabelText('Categoría'), 'cat-1')
    await userEvent.selectOptions(screen.getByLabelText('Unidad'), 'GRAM')
    await userEvent.click(screen.getByRole('button', { name: 'Crear artículo' }))

    const created = calls.find((call) => call.method === 'POST' && call.url.endsWith('/articles'))
    expect(created?.body).toEqual({ name: 'Lentejas', categoryId: 'cat-1', unit: 'GRAM' })
  })

  it('un nombre repetido se explica en castellano, no con el código crudo del contrato', async () => {
    await signInAndVisit('Catálogo', {
      'GET /api/v1/categories?includeRetired=false&size=200': CATEGORIES,
      'GET /api/v1/articles?size=200': ARTICLES,
      'POST /api/v1/articles': {
        status: 409,
        body: { code: 'ARTICLE_DUPLICATE', message: 'Ya existe un artículo vigente con ese nombre' },
      },
    })

    await userEvent.type(screen.getByLabelText('Nombre del artículo'), 'Azúcar')
    await userEvent.selectOptions(screen.getByLabelText('Categoría'), 'cat-1')
    await userEvent.click(screen.getByRole('button', { name: 'Crear artículo' }))

    expect(
      await screen.findByText('Ya hay un artículo con ese nombre o ese código de barras.'),
    ).toBeInTheDocument()
  })

  it('la búsqueda va al servidor, no filtra en memoria', async () => {
    const { calls } = await signInAndVisit('Catálogo', {
      'GET /api/v1/categories?includeRetired=false&size=200': CATEGORIES,
      'GET /api/v1/articles?size=200': ARTICLES,
      'GET /api/v1/articles?q=azu&size=200': ARTICLES,
    })

    await userEvent.type(screen.getByLabelText('Buscar'), 'azu')

    await vi.waitFor(() => {
      expect(calls.some((call) => call.url.includes('q=azu'))).toBe(true)
    })
  })
})

/**
 * Entra y abre la ficha del duradero, que es donde vive el campo de etiquetas.
 *
 * No se puede ir directo a `/inventario/asset-2`: la sesión no se restaura de
 * `localStorage` --solo el refresh token vive ahí-- así que una URL profunda
 * pinta el login.
 */
async function openDurableDetail(responses: Record<string, StubbedResponse>) {
  return signInAndVisit('Inventario', {
    'GET /api/v1/assets?size=200': {
      status: 200,
      body: { items: [durable()], page: 0, size: 200, total: 1 },
    },
    'GET /api/v1/assets/asset-2': { status: 200, body: durable() },
    'GET /api/v1/locations?size=200': LOCATIONS,
    'GET /api/v1/documents?assetId=asset-2&size=200': {
      status: 200,
      body: { items: [], page: 0, size: 200, total: 0 },
    },
    ...responses,
  }).then(async (stub) => {
    await userEvent.click(await screen.findByRole('link', { name: /Taladro/ }))
    await screen.findByRole('heading', { level: 1, name: 'Taladro' })
    return stub
  })
}

describe('etiquetas e identidad visual', () => {
  it('el filtro por etiqueta pregunta al servidor, no filtra en memoria', async () => {
    const { calls } = await signInAndVisit('Inventario', {
      'GET /api/v1/assets?size=200': { status: 200, body: { items: [durable()], page: 0, size: 200, total: 1 } },
      'GET /api/v1/assets?tagId=tag-1&size=200': {
        status: 200,
        body: { items: [], page: 0, size: 200, total: 0 },
      },
    })

    await userEvent.selectOptions(await screen.findByLabelText('Etiqueta'), 'tag-1')

    await vi.waitFor(() => {
      expect(calls.some((call) => call.url.includes('tagId=tag-1'))).toBe(true)
    })
  })

  it('la fila enseña las etiquetas puestas, con su nombre y no solo con un color', async () => {
    await signInAndVisit('Inventario', {
      'GET /api/v1/assets?size=200': {
        status: 200,
        body: {
          items: [durable({ tags: [CAMPING], categoryIcon: 'TOOL', categoryColor: 'SKY' })],
          page: 0,
          size: 200,
          total: 1,
        },
      },
    })

    // Dentro de la fila y no en cualquier sitio: «Camping» está también en el
    // desplegable del filtro, y encontrarlo allí no demostraría nada.
    const row = await screen.findByRole('link', { name: /Taladro/ })
    expect(within(row).getByText('Camping')).toBeInTheDocument()
  })

  it('poner una etiqueta manda la lista entera, no la que se acaba de añadir', async () => {
    const { calls } = await openDurableDetail({
      'GET /api/v1/assets/asset-2': { status: 200, body: durable({ tags: [CAMPING] }) },
      'GET /api/v1/tags?q=Heren&size=200': { status: 200, body: { items: [HERENCIA], page: 0, size: 200, total: 1 } },
      'PATCH /api/v1/assets/asset-2': { status: 200, body: durable({ tags: [CAMPING, HERENCIA] }) },
    })

    await userEvent.type(screen.getByRole('combobox', { name: 'Etiquetas de este asset' }), 'Heren')
    await userEvent.click(await screen.findByRole('option', { name: 'Herencia' }))
    await userEvent.click(screen.getByRole('button', { name: 'Guardar etiquetas' }))

    const patched = calls.find((call) => call.method === 'PATCH')
    // Las dos, no solo la nueva: el contrato declara `tagIds` absoluto.
    expect(patched?.body).toEqual({ tagIds: ['tag-1', 'tag-2'] })
  })

  it('quitar la última etiqueta manda la lista vacía, que es lo que desetiqueta', async () => {
    const { calls } = await openDurableDetail({
      'GET /api/v1/assets/asset-2': { status: 200, body: durable({ tags: [CAMPING] }) },
      'PATCH /api/v1/assets/asset-2': { status: 200, body: durable({ tags: [] }) },
    })

    // El botón dice qué quita: quince botones «Quitar» en una fila son quince
    // controles indistinguibles para quien no ve las pastillas.
    await userEvent.click(screen.getByRole('button', { name: 'Quitar la etiqueta Camping' }))
    await userEvent.click(screen.getByRole('button', { name: 'Guardar etiquetas' }))

    const patched = calls.find((call) => call.method === 'PATCH')
    expect(patched?.body).toEqual({ tagIds: [] })
  })

  it('un nombre que no existe se ofrece crear, y uno que ya existe no', async () => {
    await openDurableDetail({
      'GET /api/v1/tags?q=Sótano&size=200': { status: 200, body: { items: [], page: 0, size: 200, total: 0 } },
      'GET /api/v1/tags?q=camping&size=200': { status: 200, body: { items: [CAMPING], page: 0, size: 200, total: 1 } },
    })

    // Por rol y no por rótulo: el patrón combobox de ARIA 1.2 nombra tambien
    // el `listbox` con la etiqueta del campo, asi que `getByLabelText` casa dos.
    const field = screen.getByRole('combobox', { name: 'Etiquetas de este asset' })
    await userEvent.type(field, 'Sótano')
    expect(await screen.findByRole('option', { name: 'Crear «Sótano»' })).toBeInTheDocument()

    // Y con una que ya existe escrita en minúsculas y sin tilde: el catálogo
    // compara normalizado, así que ofrecer crearla sería ofrecer un 409.
    await userEvent.clear(field)
    await userEvent.type(field, 'camping')
    expect(await screen.findByRole('option', { name: 'Camping' })).toBeInTheDocument()
    expect(screen.queryByRole('option', { name: /Crear/ })).not.toBeInTheDocument()
  })

  it('crear una etiqueta desde el campo la da de alta y la deja puesta', async () => {
    const { calls } = await openDurableDetail({
      'GET /api/v1/tags?q=Sótano&size=200': { status: 200, body: { items: [], page: 0, size: 200, total: 0 } },
      'POST /api/v1/tags': {
        status: 201,
        body: { id: 'tag-9', name: 'Sótano', createdAt: '2026-08-20T00:00:00Z', retiredAt: null },
      },
    })

    await userEvent.type(screen.getByRole('combobox', { name: 'Etiquetas de este asset' }), 'Sótano')
    await userEvent.click(await screen.findByRole('option', { name: 'Crear «Sótano»' }))

    const created = calls.find((call) => call.method === 'POST' && call.url.endsWith('/tags'))
    expect(created?.body).toEqual({ name: 'Sótano' })
    expect(await screen.findByRole('button', { name: 'Quitar la etiqueta Sótano' })).toBeInTheDocument()
  })

  it('el icono y el color viajan al crear la categoría, y se pueden dejar sin elegir', async () => {
    const { calls } = await signInAndVisit('Catálogo', {
      'GET /api/v1/categories?includeRetired=false&size=200': CATEGORIES,
      'GET /api/v1/articles?size=200': ARTICLES,
      'POST /api/v1/categories': { status: 201, body: CATEGORIES.body.items[0] },
    })

    await userEvent.click(screen.getByRole('tab', { name: 'Categorías' }))
    await userEvent.type(screen.getByLabelText('Nueva categoría'), 'Escalada')

    // Por su nombre en castellano: es lo único que tiene quien no ve la rejilla.
    await userEvent.click(screen.getByRole('button', { name: 'Bicicleta, categoría nueva' }))
    await userEvent.click(screen.getByRole('button', { name: 'Turquesa, categoría nueva' }))
    await userEvent.click(screen.getByRole('button', { name: 'Crear categoría' }))

    const created = calls.find((call) => call.method === 'POST' && call.url.endsWith('/categories'))
    expect(created?.body).toEqual({ name: 'Escalada', icon: 'BIKE', color: 'TEAL' })
  })

  it('lo elegido se dice con aria-pressed y no solo con el relleno', async () => {
    await signInAndVisit('Catálogo', {
      'GET /api/v1/categories?includeRetired=false&size=200': CATEGORIES,
      'GET /api/v1/articles?size=200': ARTICLES,
    })

    await userEvent.click(screen.getByRole('tab', { name: 'Categorías' }))
    const bike = screen.getByRole('button', { name: 'Bicicleta, categoría nueva' })
    expect(bike).toHaveAttribute('aria-pressed', 'false')

    await userEvent.click(bike)
    expect(bike).toHaveAttribute('aria-pressed', 'true')

    // Y se puede desmarcar, que es por lo que son botones y no radios: una
    // categoría sin cara es el estado inicial de todas.
    await userEvent.click(screen.getByRole('button', { name: 'Sin icono, categoría nueva' }))
    expect(bike).toHaveAttribute('aria-pressed', 'false')
  })

  it('editar una categoría manda su cara entera, que es como se retira un icono', async () => {
    const { calls } = await signInAndVisit('Catálogo', {
      'GET /api/v1/categories?includeRetired=false&size=200': CATEGORIES,
      'GET /api/v1/articles?size=200': ARTICLES,
      'PATCH /api/v1/categories/cat-1': { status: 200, body: CATEGORIES.body.items[0] },
    })

    await userEvent.click(screen.getByRole('tab', { name: 'Categorías' }))
    await userEvent.click(screen.getByRole('button', { name: 'Editar Alimentación' }))
    // Con el nombre de la categoría dentro: en esta pantalla puede haber dos
    // selectores a la vez --el del alta y el de la fila-- y sin eso serían
    // cuarenta y cuatro botones con veintidós nombres repetidos.
    await userEvent.click(screen.getByRole('button', { name: 'Sin icono, Alimentación' }))
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }))

    const patched = calls.find((call) => call.method === 'PATCH')
    expect(patched?.body).toEqual({ name: 'Alimentación', icon: null, color: 'MOSS' })
  })
})

describe('árbol de ubicaciones', () => {
  it('anida las ubicaciones y anuncia el nivel a un lector de pantalla', async () => {
    await signInAndVisit('Ubicaciones', {
      'GET /api/v1/locations?size=200': LOCATIONS,
    })

    const tree = await screen.findByRole('tree', { name: 'Ubicaciones del hogar' })
    const [house, pantry] = within(tree).getAllByRole('treeitem')

    // La sangría es decoración: quien no ve la pantalla necesita el nivel.
    expect(house).toHaveAttribute('aria-level', '1')
    expect(pantry).toHaveAttribute('aria-level', '2')
    expect(within(pantry!).getByText('Despensa')).toBeInTheDocument()
  })

  it('borrar una ubicación que tiene cosas dentro se explica sin jerga', async () => {
    await signInAndVisit('Ubicaciones', {
      'GET /api/v1/locations?size=200': LOCATIONS,
      'DELETE /api/v1/locations/loc-2': {
        status: 409,
        body: { code: 'LOCATION_HAS_ASSETS', message: 'La ubicación todavía tiene assets dentro' },
      },
    })

    const tree = await screen.findByRole('tree', { name: 'Ubicaciones del hogar' })
    const [, despensa] = within(tree).getAllByRole('treeitem')
    await userEvent.click(within(despensa!).getByRole('button', { name: 'Borrar' }))

    expect(await screen.findByText('No se puede borrar: todavía hay cosas guardadas ahí.')).toBeInTheDocument()
  })

  it('la capacidad se declara al crear, con su medida', async () => {
    const { calls } = await signInAndVisit('Ubicaciones', {
      'GET /api/v1/locations?size=200': LOCATIONS,
      'POST /api/v1/locations': {
        status: 201,
        body: { id: 'loc-3', name: 'Estantería', type: 'SHELF', parentLocationId: 'loc-2', capacity: null, notes: null },
      },
    })

    await userEvent.type(await screen.findByLabelText('Nombre'), 'Estantería')
    await userEvent.selectOptions(screen.getByLabelText('Tipo'), 'SHELF')
    await userEvent.selectOptions(screen.getByLabelText('Dentro de'), 'loc-2')

    // Los dos campos de medida no existen hasta que se dice en qué se mide.
    expect(screen.queryByLabelText('Máximo')).not.toBeInTheDocument()
    await userEvent.selectOptions(screen.getByLabelText('Capacidad (opcional)'), 'UNITS')
    await userEvent.type(await screen.findByLabelText('Máximo'), '12')

    await userEvent.click(screen.getByRole('button', { name: 'Crear ubicación' }))

    const created = calls.find((call) => call.method === 'POST' && call.url.endsWith('/locations'))
    expect(created?.body).toMatchObject({
      name: 'Estantería',
      type: 'SHELF',
      parentLocationId: 'loc-2',
      // La unidad se propone sola al elegir el tipo, para que el campo no salga
      // vacío obligando a inventar una palabra.
      capacity: { type: 'UNITS', max: 12, unit: 'cajas' },
    })
  })

  it('editar manda los cuatro campos, porque en un PATCH ausente conserva y nulo borra', async () => {
    const { calls } = await signInAndVisit('Ubicaciones', {
      'GET /api/v1/locations?size=200': LOCATIONS,
      'PATCH /api/v1/locations/loc-2': {
        status: 200,
        body: { id: 'loc-2', name: 'Despensa grande', type: 'ROOM', parentLocationId: 'loc-1', capacity: null, notes: null },
      },
    })

    const tree = await screen.findByRole('tree', { name: 'Ubicaciones del hogar' })
    const [, despensa] = within(tree).getAllByRole('treeitem')
    await userEvent.click(within(despensa!).getByRole('button', { name: 'Editar' }))

    const form = await screen.findByRole('heading', { name: 'Editar «Despensa»' })
    // El foco se va al formulario: quien lo abrió con el teclado estaba en un
    // botón del árbol, y el formulario aparece en otro sitio de la página.
    expect(form).toHaveFocus()

    const name = screen.getAllByLabelText('Nombre')[1]!
    await userEvent.clear(name)
    await userEvent.type(name, 'Despensa grande')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }))

    const saved = calls.find((call) => call.method === 'PATCH' && call.url.endsWith('/locations/loc-2'))
    expect(saved?.body).toEqual({
      name: 'Despensa grande',
      type: 'ROOM',
      parentLocationId: 'loc-1',
      capacity: null,
    })
  })

  it('no se ofrece como destino ni ella misma ni lo que cuelga de ella', async () => {
    await signInAndVisit('Ubicaciones', {
      'GET /api/v1/locations?size=200': LOCATIONS,
    })

    const tree = await screen.findByRole('tree', { name: 'Ubicaciones del hogar' })
    const [casa] = within(tree).getAllByRole('treeitem')
    // El primero: un `treeitem` con hijas los contiene dentro, así que ahí abajo
    // hay más botones «Editar» que no son el suyo. En orden de DOM, la fila del
    // nodo va antes que su lista de hijas.
    await userEvent.click(within(casa!).getAllByRole('button', { name: 'Editar' })[0]!)

    // Meter la casa dentro de su propia despensa es el ciclo que el servidor
    // rechaza con LOCATION_CYCLE. Aquí ni se ofrece: la negativa del servidor es
    // la red de seguridad, no el camino normal.
    const parent = screen.getAllByLabelText('Dentro de')[1]!
    expect(within(parent).getByRole('option', { name: 'Nada: es una vivienda' })).toBeInTheDocument()
    expect(within(parent).queryByRole('option', { name: 'Casa del Pinar' })).not.toBeInTheDocument()
    expect(within(parent).queryByRole('option', { name: 'Despensa' })).not.toBeInTheDocument()
  })
})

describe('existencias', () => {
  it('dar entrada manda la cantidad y dice que ha sumado sobre lo que había', async () => {
    const { calls } = await signInAndVisit('Inventario', {
      'GET /api/v1/assets?size=200': { status: 200, body: { items: [], page: 0, size: 200, total: 0 } },
      'GET /api/v1/articles?size=200': ARTICLES,
      'GET /api/v1/locations?size=200': LOCATIONS,
      'GET /api/v1/assets?articleId=art-1&locationId=loc-2&size=200': {
        status: 200,
        body: { items: [stockItem()], page: 0, size: 200, total: 1 },
      },
      'POST /api/v1/assets/intake': { status: 200, body: stockItem({ quantity: 1300 }) },
    })

    await userEvent.click(screen.getByRole('link', { name: 'Dar entrada' }))
    await userEvent.selectOptions(await screen.findByLabelText('Artículo'), 'art-1')
    await userEvent.selectOptions(screen.getByLabelText('Dónde se guarda'), 'loc-2')
    await userEvent.type(screen.getByLabelText('Cantidad que entra (g)'), '1000')
    await userEvent.click(screen.getByRole('button', { name: 'Dar entrada' }))

    const intake = calls.find((call) => call.url.endsWith('/assets/intake'))
    expect(intake?.body).toMatchObject({
      articleId: 'art-1',
      quantity: 1000,
      location: { type: 'LOCATION', id: 'loc-2' },
    })

    // El desenlace importa: la misma petición crea o suma, y el usuario tiene
    // que saber cuál de las dos ha pasado.
    expect(await screen.findByText('Sumado a lo que había')).toBeInTheDocument()
    expect(screen.getByText(/1300 g/)).toBeInTheDocument()
  })

  it('la ficha de una existencia ofrece mover, corregir cantidad y dar de baja', async () => {
    await signInAndVisit('Inventario', {
      'GET /api/v1/assets?size=200': {
        status: 200,
        body: { items: [stockItem()], page: 0, size: 200, total: 1 },
      },
      'GET /api/v1/assets/asset-1': { status: 200, body: stockItem() },
      'GET /api/v1/locations?size=200': LOCATIONS,
      'GET /api/v1/assets?articleId=art-1&size=200': {
        status: 200,
        body: { items: [stockItem()], page: 0, size: 200, total: 1 },
      },
    })

    await userEvent.click(await screen.findByRole('link', { name: /Azúcar/ }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Azúcar' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Mover aquí' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Guardar cantidad' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Dar de baja' })).toBeInTheDocument()

    // Sin otra existencia del mismo artículo, unir no se ofrece: sería una
    // acción que solo puede fallar.
    expect(screen.queryByRole('button', { name: 'Unir' })).not.toBeInTheDocument()
  })

  it('el ajuste manda la cantidad absoluta, no la diferencia', async () => {
    const { calls } = await signInAndVisit('Inventario', {
      'GET /api/v1/assets?size=200': {
        status: 200,
        body: { items: [stockItem()], page: 0, size: 200, total: 1 },
      },
      'GET /api/v1/assets/asset-1': { status: 200, body: stockItem() },
      'GET /api/v1/locations?size=200': LOCATIONS,
      'GET /api/v1/assets?articleId=art-1&size=200': {
        status: 200,
        body: { items: [stockItem()], page: 0, size: 200, total: 1 },
      },
      'PATCH /api/v1/assets/asset-1': { status: 200, body: stockItem({ quantity: 120 }) },
    })

    await userEvent.click(await screen.findByRole('link', { name: /Azúcar/ }))
    const field = await screen.findByLabelText('Cantidad que hay ahora')
    await userEvent.clear(field)
    await userEvent.type(field, '120')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar cantidad' }))

    const adjust = calls.find((call) => call.method === 'PATCH' && call.url.endsWith('/assets/asset-1'))
    expect(adjust?.body).toEqual({ quantity: 120 })
  })

  it('mover a una ubicación llena avisa pero no lo trata como un error', async () => {
    await signInAndVisit('Inventario', {
      'GET /api/v1/assets?size=200': {
        status: 200,
        body: { items: [stockItem()], page: 0, size: 200, total: 1 },
      },
      'GET /api/v1/assets/asset-1': { status: 200, body: stockItem() },
      'GET /api/v1/locations?size=200': LOCATIONS,
      'GET /api/v1/assets?articleId=art-1&size=200': {
        status: 200,
        body: { items: [stockItem()], page: 0, size: 200, total: 1 },
      },
      'PATCH /api/v1/assets/asset-1': {
        status: 200,
        body: stockItem({
          location: { type: 'LOCATION', id: 'loc-1' },
          warnings: [
            {
              code: 'LOCATION_CAPACITY_EXCEEDED',
              message: 'La ubicación declara un máximo de 2 cosas y ya contiene 3',
            },
          ],
        }),
      },
    })

    await userEvent.click(await screen.findByRole('link', { name: /Azúcar/ }))
    await userEvent.selectOptions(await screen.findByLabelText('Nueva ubicación'), 'loc-1')
    await userEvent.click(screen.getByRole('button', { name: 'Mover aquí' }))

    // El aviso se pinta como aviso --`status`, no `alert`-- porque la operación
    // tuvo éxito: el asset ya está movido.
    const notice = await screen.findByText('La ubicación declara un máximo de 2 cosas y ya contiene 3')
    expect(notice.closest('[role="status"]')).not.toBeNull()
  })

  it('mover una existencia sobre otra del mismo artículo remite a unirlas', async () => {
    await signInAndVisit('Inventario', {
      'GET /api/v1/assets?size=200': {
        status: 200,
        body: { items: [stockItem()], page: 0, size: 200, total: 1 },
      },
      'GET /api/v1/assets/asset-1': { status: 200, body: stockItem() },
      'GET /api/v1/locations?size=200': LOCATIONS,
      'GET /api/v1/assets?articleId=art-1&size=200': {
        status: 200,
        body: { items: [stockItem()], page: 0, size: 200, total: 1 },
      },
      'PATCH /api/v1/assets/asset-1': {
        status: 409,
        body: { code: 'EXISTENCE_ALREADY_IN_LOCATION', message: 'Ya hay una existencia viva ahí' },
      },
    })

    await userEvent.click(await screen.findByRole('link', { name: /Azúcar/ }))
    await userEvent.selectOptions(await screen.findByLabelText('Nueva ubicación'), 'loc-1')
    await userEvent.click(screen.getByRole('button', { name: 'Mover aquí' }))

    expect(
      await screen.findByText('Ahí ya hay una existencia de este artículo. Únelas en lugar de moverla.'),
    ).toBeInTheDocument()
  })

  it('un consumible a cero se distingue de uno disponible y de uno dado de baja', async () => {
    await signInAndVisit('Inventario', {
      'GET /api/v1/assets?size=200': {
        status: 200,
        body: {
          items: [
            stockItem({ id: 'a', quantity: 0 }),
            stockItem({ id: 'b', name: 'Sal', quantity: 500 }),
            stockItem({ id: 'c', name: 'Harina', status: 'DECOMMISSIONED', quantity: 0 }),
          ],
          page: 0,
          size: 200,
          total: 3,
        },
      },
    })

    // Agotado no es lo mismo que de baja: uno se repone y el otro ya no existe.
    expect(await screen.findByText('Agotado')).toBeInTheDocument()
    expect(screen.getByText('Disponible')).toBeInTheDocument()
    expect(screen.getByText('De baja')).toBeInTheDocument()
  })
  it('el filtro de conservación pregunta al servidor en vez de filtrar en memoria', async () => {
    const { calls } = await signInAndVisit('Inventario', {
      'GET /api/v1/assets?size=200': { status: 200, body: { items: [durable()], page: 0, size: 200, total: 1 } },
      'GET /api/v1/assets?condition=UNUSABLE&size=200': {
        status: 200,
        body: { items: [durable({ name: 'Ventilador', condition: 'UNUSABLE' })], page: 0, size: 200, total: 1 },
      },
    })

    await userEvent.selectOptions(await screen.findByLabelText('Estado de conservación'), 'UNUSABLE')

    await vi.waitFor(() => {
      expect(calls.some((call) => call.url.includes('condition=UNUSABLE'))).toBe(true)
    })
    // Y lo que vuelve se pinta con su etiqueta en castellano y no con el valor
    // del enumerado. Dentro de la fila, porque «Inservible» es también una de
    // las opciones del filtro.
    const row = await screen.findByRole('link', { name: /Ventilador/ })
    expect(within(row).getByText('Inservible')).toBeInTheDocument()
  })
})

describe('identificación de un duradero', () => {
  const DETAIL_ROUTES: Record<string, StubbedResponse> = {
    'GET /api/v1/assets?size=200': { status: 200, body: { items: [durable()], page: 0, size: 200, total: 1 } },
    'GET /api/v1/assets/asset-2': { status: 200, body: durable() },
    'GET /api/v1/locations?size=200': LOCATIONS,
    'GET /api/v1/documents?assetId=asset-2&size=200': {
      status: 200,
      body: { items: [], page: 0, size: 200, total: 0 },
    },
  }

  it('el número de serie y la fecha se rellenan después del alta', async () => {
    const { calls } = await signInAndVisit('Inventario', {
      ...DETAIL_ROUTES,
      'PATCH /api/v1/assets/asset-2': {
        status: 200,
        body: durable({ serialNumber: 'JU-88-2019-4471', acquiredOn: '2019-11-03' }),
      },
    })

    await userEvent.click(await screen.findByRole('link', { name: /Taladro/ }))
    await userEvent.type(await screen.findByLabelText('Número de serie'), 'JU-88-2019-4471')
    await userEvent.type(screen.getByLabelText('Fecha de adquisición'), '2019-11-03')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }))

    const saved = calls.find((call) => call.method === 'PATCH' && call.url.endsWith('/assets/asset-2'))
    expect(saved?.body).toEqual({ serialNumber: 'JU-88-2019-4471', acquiredOn: '2019-11-03' })
  })

  it('vaciarlos manda null, que borra, y no cadena vacía', async () => {
    const { calls } = await signInAndVisit('Inventario', {
      ...DETAIL_ROUTES,
      'GET /api/v1/assets/asset-2': {
        status: 200,
        body: durable({ serialNumber: 'MAL-COPIADO', acquiredOn: '2019-11-03' }),
      },
      'PATCH /api/v1/assets/asset-2': { status: 200, body: durable() },
    })

    await userEvent.click(await screen.findByRole('link', { name: /Taladro/ }))
    await userEvent.clear(await screen.findByLabelText('Número de serie'))
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }))

    const saved = calls.find((call) => call.method === 'PATCH' && call.url.endsWith('/assets/asset-2'))
    expect(saved?.body).toMatchObject({ serialNumber: null })
  })

  it('la fecha se lee como día de calendario, no como instante UTC', async () => {
    await signInAndVisit('Inventario', {
      ...DETAIL_ROUTES,
      'GET /api/v1/assets/asset-2': { status: 200, body: durable({ acquiredOn: '2019-11-03' }) },
    })

    await userEvent.click(await screen.findByRole('link', { name: /Taladro/ }))

    // Con `new Date('2019-11-03')` esto sería el 2 de noviembre en cualquier huso
    // al oeste de Greenwich: la cadena se interpreta como medianoche UTC.
    expect(await screen.findByText('3 de noviembre de 2019')).toBeInTheDocument()
  })

  it('el estado de conservación se anota en la ficha y se retira mandando null', async () => {
    const { calls } = await signInAndVisit('Inventario', {
      ...DETAIL_ROUTES,
      'PATCH /api/v1/assets/asset-2': { status: 200, body: durable({ condition: 'DAMAGED' }) },
    })

    await userEvent.click(await screen.findByRole('link', { name: /Taladro/ }))
    // Mientras nadie lo anote, la ficha lo dice con palabras y no con un guion:
    // el hueco de este campo significa algo. Se busca dentro de la lista de
    // definición porque «Sin anotar» es también la opción vacía del desplegable,
    // y las dos tienen que estar.
    const fact = (await screen.findByText('Conservación')).parentElement
    expect(fact).toHaveTextContent('Sin anotar')

    await userEvent.selectOptions(screen.getByLabelText('En qué estado está'), 'DAMAGED')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar estado' }))

    const saved = calls.find((call) => call.method === 'PATCH' && call.url.endsWith('/assets/asset-2'))
    expect(saved?.body).toEqual({ condition: 'DAMAGED' })

    // Y retirarlo manda `null`, que borra: dejarlo «sin anotar» no es ninguno de
    // los cinco valores.
    await userEvent.selectOptions(screen.getByLabelText('En qué estado está'), '')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar estado' }))

    const cleared = calls.filter((call) => call.method === 'PATCH' && call.url.endsWith('/assets/asset-2'))
    expect(cleared.at(-1)?.body).toEqual({ condition: null })
  })

  it('una existencia no ofrece identificación: no hay unidad física de la que hablar', async () => {
    await signInAndVisit('Inventario', {
      'GET /api/v1/assets?size=200': { status: 200, body: { items: [stockItem()], page: 0, size: 200, total: 1 } },
      'GET /api/v1/assets/asset-1': { status: 200, body: stockItem() },
      'GET /api/v1/locations?size=200': LOCATIONS,
      'GET /api/v1/assets?articleId=art-1&size=200': {
        status: 200,
        body: { items: [stockItem()], page: 0, size: 200, total: 1 },
      },
    })

    await userEvent.click(await screen.findByRole('link', { name: /Azúcar/ }))
    await screen.findByRole('button', { name: 'Guardar cantidad' })

    expect(screen.queryByLabelText('Número de serie')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Fecha de adquisición')).not.toBeInTheDocument()
    // Ni estado de conservación, que es la tercera del mismo grupo: la API lo
    // rechaza con un 400 y la pantalla no lo ofrece.
    expect(screen.queryByLabelText('En qué estado está')).not.toBeInTheDocument()
  })
})
