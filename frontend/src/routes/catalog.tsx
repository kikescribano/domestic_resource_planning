import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Archive, BookOpen, Pencil } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'

import {
  UNIT_LABELS,
  api,
  humanMessage,
  type Article,
  type Category,
  type CategoryColor,
  type CategoryIcon,
  type MeasurementUnit,
} from '../api/client'
import { useAuthenticatedSession } from '../auth/SessionProvider'
import { CategoryMarker, IconColorPicker } from '../ui/catalog'
import {
  Button,
  EmptyState,
  Field,
  Notice,
  PageHeading,
  SelectField,
  Spinner,
  StatusBadge,
} from '../ui/primitives'

/**
 * El catálogo del hogar: categorías y artículos.
 *
 * Es dato maestro, no inventario: **qué cosas existen y cómo se llaman**. Cuánto
 * queda y dónde está es de la pantalla de assets.
 *
 * Va antes que las demás en el orden de uso porque el alta de cualquier cosa
 * necesita elegir una categoría, y la entrada de un consumible, un artículo.
 */

const UNITS: MeasurementUnit[] = ['UNIT', 'GRAM', 'KILOGRAM', 'MILLILITER', 'LITER', 'METER', 'PACK']

export function CatalogPage() {
  // El alta de una categoría vuelve aquí con su pestaña en el estado de la
  // navegación: sin eso aterrizaría en «Artículos» y lo recién creado no se
  // vería, que es la única prueba de que la operación hizo algo.
  const location = useLocation()
  const [tab, setTab] = useState<'articles' | 'categories'>(
    (location.state as { tab?: 'articles' | 'categories' } | null)?.tab ?? 'articles',
  )

  return (
    <>
      <PageHeading
        title="Catálogo"
        icon={BookOpen}
        action={
          // El artículo delante y la categoría detrás, en el mismo orden que
          // las pestañas de debajo: dos órdenes distintos para la misma pareja
          // obligarían a leer los botones en vez de reconocerlos.
          <div className="flex gap-2">
            <Link
              to="/catalogo/nuevo-articulo"
              className="inline-flex min-h-touch items-center rounded-md bg-accent px-4 text-body font-medium text-ink-inverse"
            >
              Nuevo artículo
            </Link>
            <Link
              to="/catalogo/nueva-categoria"
              className="inline-flex min-h-touch items-center rounded-md border border-border bg-surface-raised px-4 text-body font-medium text-ink"
            >
              Nueva categoría
            </Link>
          </div>
        }
      />

      {/* Dos listas en una pantalla y no dos rutas: se consultan a la vez y
          separarlas obligaría a ir y volver. Las altas sí viven en su propia
          página cada una --como en el inventario--, accesibles desde la
          cabecera: un formulario permanente encima de la lista empujaba lo
          que hay debajo del pliegue. */}
      <div role="tablist" aria-label="Catálogo" className="mb-6 flex gap-1 border-b border-border-subtle">
        <CatalogTab id="articles" current={tab} onSelect={setTab}>
          Artículos
        </CatalogTab>
        <CatalogTab id="categories" current={tab} onSelect={setTab}>
          Categorías
        </CatalogTab>
      </div>

      {tab === 'articles' ? <ArticlesPanel /> : <CategoriesPanel />}
    </>
  )
}

function CatalogTab({
  id,
  current,
  onSelect,
  children,
}: {
  id: 'articles' | 'categories'
  current: string
  onSelect: (id: 'articles' | 'categories') => void
  children: string
}) {
  const isActive = current === id
  return (
    <button
      role="tab"
      aria-selected={isActive}
      onClick={() => onSelect(id)}
      className={[
        'min-h-touch border-b-2 px-4 text-body',
        isActive ? 'border-accent font-medium text-accent-ink' : 'border-transparent text-ink-muted',
      ].join(' ')}
    >
      {children}
    </button>
  )
}

// ---------------------------------------------------------------------------
// Categorías
// ---------------------------------------------------------------------------

type CategoryIdentity = { icon: CategoryIcon | null; color: CategoryColor | null }

function CategoriesPanel() {
  const { accessToken } = useAuthenticatedSession()
  const queryClient = useQueryClient()
  const [editing, setEditing] = useState<string | null>(null)
  const [failure, setFailure] = useState<string | null>(null)

  const categories = useQuery({
    queryKey: ['categories'],
    queryFn: () => api.listCategories(accessToken),
  })

  const update = useMutation({
    mutationFn: (edited: Category) =>
      api.updateCategory(
        edited.id,
        {
          name: edited.name,
          notes: edited.notes ?? undefined,
          icon: edited.icon,
          color: edited.color,
        },
        accessToken,
      ),
    onSuccess: () => {
      setEditing(null)
      setFailure(null)
      void queryClient.invalidateQueries({ queryKey: ['categories'] })
      // Los assets pintan la cara de su categoría, así que un cambio aquí los
      // deja mintiendo hasta que alguien recargue.
      void queryClient.invalidateQueries({ queryKey: ['assets'] })
    },
    onError: (error) => setFailure(humanMessage(error)),
  })

  const retire = useMutation({
    mutationFn: (id: string) => api.retireCategory(id, accessToken),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['categories'] }),
    onError: (error) => setFailure(humanMessage(error)),
  })

  if (categories.isPending) return <Spinner label="Cargando categorías" />
  if (categories.isError) return <Notice tone="danger">{humanMessage(categories.error)}</Notice>

  return (
    <div className="flex flex-col gap-6">
      {failure && <Notice tone="danger">{failure}</Notice>}

      <ul className="flex flex-col gap-2">
        {categories.data.items.map((category) => (
          <CategoryRow
            key={category.id}
            category={category}
            editing={editing === category.id}
            onEdit={() => setEditing(category.id)}
            onCancel={() => setEditing(null)}
            onSave={update.mutate}
            onRetire={() => retire.mutate(category.id)}
            busy={retire.isPending || update.isPending}
          />
        ))}
      </ul>
    </div>
  )
}

/**
 * Una fila del catálogo, que se convierte en su propio formulario al editarla.
 *
 * En la misma fila y no en otra pantalla: lo que se cambia aquí es sobre todo la
 * cara, y elegirla sin el resto de categorías delante es elegir a ciegas — con
 * doce a la vista se ve enseguida si el verde ya lo gasta otra.
 *
 * Y de paso deja de haber una operación del contrato sin sitio en la interfaz:
 * `updateCategory` existía desde el Hito 2 y no había forma de llamarla.
 */
function CategoryRow({
  category,
  editing,
  onEdit,
  onCancel,
  onSave,
  onRetire,
  busy,
}: {
  category: Category
  editing: boolean
  onEdit: () => void
  onCancel: () => void
  onSave: (edited: Category) => void
  onRetire: () => void
  busy: boolean
}) {
  const [edited, setEdited] = useState(category)

  if (editing) {
    return (
      <li className="flex flex-col gap-3 rounded-md border border-border bg-surface-raised px-3 py-3">
        <Field
          label={`Nombre de ${category.name}`}
          value={edited.name}
          onChange={(event) => setEdited({ ...edited, name: event.target.value })}
        />
        <IconColorPicker
          icon={edited.icon}
          color={edited.color}
          context={category.name}
          onChange={(identity) => setEdited({ ...edited, ...identity })}
        />
        <div className="flex flex-wrap gap-2">
          <Button variant="primary" busy={busy} busyLabel="Guardando…" onClick={() => onSave(edited)}>
            Guardar
          </Button>
          {/* Cancelar **descarta**: sin devolver el borrador a lo que dice la
              fila, volver a abrir el formulario enseñaría los cambios que se
              acaban de tirar. */}
          <Button
            variant="ghost"
            onClick={() => {
              setEdited(category)
              onCancel()
            }}
          >
            Cancelar
          </Button>
        </div>
      </li>
    )
  }

  return (
    // La misma altura fija que las tarjetas de Ubicaciones: una categoría no
    // tiene segunda línea, así que el centrado vertical deja el nombre a la
    // altura de su marcador.
    <li className="flex h-15 items-center justify-between gap-2 rounded-md border border-border-subtle bg-surface-raised px-3 py-2">
      <span className="flex min-w-0 flex-1 items-center gap-2">
        <CategoryMarker icon={category.icon} color={category.color} />
        <span className="min-w-0 truncate text-body text-ink" title={category.name}>
          {category.name}
        </span>
      </span>
      <span className="flex items-center gap-1">
        {/* El nombre de la categoría dentro del nombre accesible: doce botones
            «Editar» en columna son doce controles indistinguibles para quien no
            ve la fila. El lápiz y el archivador son los mismos gestos que en
            Ubicaciones: editar en el teal del acento y lo que quita de en
            medio, en el rojo del esquema — retirar no destruye, y por eso es
            un archivador y no una papelera. */}
        <Button
          variant="ghost"
          onClick={onEdit}
          aria-label={`Editar ${category.name}`}
          title="Editar"
          className="w-11 px-0"
        >
          <Pencil size={20} strokeWidth={1.75} aria-hidden="true" className="shrink-0" />
        </Button>
        <Button
          variant="ghost-danger"
          onClick={onRetire}
          disabled={busy}
          aria-label={`Retirar ${category.name}`}
          title="Retirar"
          className="w-11 px-0"
        >
          <Archive size={20} strokeWidth={1.75} aria-hidden="true" className="shrink-0" />
        </Button>
      </span>
    </li>
  )
}

// ---------------------------------------------------------------------------
// Artículos
// ---------------------------------------------------------------------------

function ArticlesPanel() {
  const { accessToken } = useAuthenticatedSession()
  const queryClient = useQueryClient()
  const [query, setQuery] = useState('')
  const [failure, setFailure] = useState<string | null>(null)

  // La búsqueda entra en la clave: cambiarla es otra consulta, no un filtro en
  // memoria. Con el catálogo entero de un hogar grande, filtrar en el cliente
  // significaría traérselo todo en cada tecla.
  const articles = useQuery({
    queryKey: ['articles', query],
    queryFn: () => api.listArticles(accessToken, { q: query || undefined }),
  })

  const retire = useMutation({
    mutationFn: (id: string) => api.retireArticle(id, accessToken),
    onSuccess: () => {
      setFailure(null)
      void queryClient.invalidateQueries({ queryKey: ['articles'] })
    },
    onError: (error) => setFailure(humanMessage(error)),
  })

  return (
    <div className="flex flex-col gap-6">
      {failure && <Notice tone="danger">{failure}</Notice>}

      <Field
        label="Buscar"
        type="search"
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        hint="No distingue mayúsculas ni acentos."
      />

      {articles.isPending ? (
        <Spinner label="Cargando artículos" />
      ) : articles.isError ? (
        <Notice tone="danger">{humanMessage(articles.error)}</Notice>
      ) : articles.data.items.length === 0 ? (
        <EmptyState title={query ? 'Ningún artículo coincide' : 'El catálogo está vacío'}>
          {query ? 'Prueba con otras palabras.' : 'Crea el primero con «Nuevo artículo», arriba a la derecha.'}
        </EmptyState>
      ) : (
        <ul className="flex flex-col gap-2">
          {articles.data.items.map((article) => (
            <ArticleRow key={article.id} article={article} onRetire={() => retire.mutate(article.id)} />
          ))}
        </ul>
      )}
    </div>
  )
}

function ArticleRow({ article, onRetire }: { article: Article; onRetire: () => void }) {
  return (
    // La misma altura fija que las categorías y que Ubicaciones. Un artículo
    // siempre trae segunda línea —su categoría y su unidad—, así que aquí el
    // centrado reparte las dos.
    <li className="flex h-15 items-center justify-between gap-2 rounded-md border border-border-subtle bg-surface-raised px-3 py-2">
      <span className="flex min-w-0 flex-1 flex-col">
        <span className="flex min-w-0 items-center gap-2">
          <span className="min-w-0 truncate text-body text-ink" title={article.name}>
            {article.name}
          </span>
          {article.retiredAt && (
            <span className="shrink-0">
              <StatusBadge tone="decommissioned">Retirado</StatusBadge>
            </span>
          )}
        </span>
        <span className="truncate text-caption text-ink-muted">
          {article.category} · {UNIT_LABELS[article.unit]}
        </span>
      </span>
      {!article.retiredAt && (
        <Button
          variant="ghost-danger"
          onClick={onRetire}
          aria-label={`Retirar ${article.name}`}
          title="Retirar"
          className="w-11 shrink-0 px-0"
        >
          <Archive size={20} strokeWidth={1.75} aria-hidden="true" className="shrink-0" />
        </Button>
      )}
    </li>
  )
}

// ---------------------------------------------------------------------------
// Altas, cada una en su página
// ---------------------------------------------------------------------------

export function NewCategoryPage() {
  const { accessToken } = useAuthenticatedSession()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [failure, setFailure] = useState<string | null>(null)
  const [draft, setDraft] = useState<CategoryIdentity & { name: string }>({
    name: '',
    icon: null,
    color: null,
  })

  const create = useMutation({
    mutationFn: () =>
      api.createCategory({ name: draft.name, icon: draft.icon, color: draft.color }, accessToken),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['categories'] })
      // De vuelta a su pestaña, que no es la que el catálogo abre por omisión:
      // aterrizar en «Artículos» escondería lo que se acaba de crear.
      void navigate('/catalogo', { state: { tab: 'categories' } })
    },
    onError: (error) => setFailure(humanMessage(error)),
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    create.mutate()
  }

  return (
    <>
      <PageHeading title="Nueva categoría" />

      <form onSubmit={submit} className="flex max-w-form flex-col gap-3">
        <Field
          label="Nombre"
          value={draft.name}
          onChange={(event) => setDraft({ ...draft, name: event.target.value })}
          required
          hint="Se muestra tal cual, así que va en tu idioma."
        />

        {/* Opcionales los dos, y sin nada preseleccionado: una categoría sin
            cara es el caso normal de un hogar que acaba de empezar, y elegir por
            él convertiría en dato lo que nadie llegó a mirar. */}
        <IconColorPicker
          icon={draft.icon}
          color={draft.color}
          context="categoría nueva"
          onChange={(identity) => setDraft({ ...draft, ...identity })}
        />

        {failure && <Notice tone="danger">{failure}</Notice>}

        <Button type="submit" variant="primary" busy={create.isPending} busyLabel="Creando…">
          Crear categoría
        </Button>
      </form>
    </>
  )
}

export function NewArticlePage() {
  const { accessToken } = useAuthenticatedSession()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [failure, setFailure] = useState<string | null>(null)
  const [draft, setDraft] = useState({
    name: '',
    categoryId: '',
    unit: 'UNIT' as MeasurementUnit,
    unitWeightGrams: '',
  })

  const categories = useQuery({
    queryKey: ['categories'],
    queryFn: () => api.listCategories(accessToken),
  })

  const create = useMutation({
    mutationFn: () =>
      api.createArticle(
        {
          name: draft.name,
          categoryId: draft.categoryId,
          unit: draft.unit,
          // Se omite en vez de mandarse a nulo: el contrato lo rechaza a cero y
          // «no lo sé» es la ausencia, no un peso de cero gramos.
          ...(draft.unitWeightGrams ? { unitWeightGrams: Number(draft.unitWeightGrams) } : {}),
        },
        accessToken,
      ),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['articles'] })
      void navigate('/catalogo')
    },
    onError: (error) => setFailure(humanMessage(error)),
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    create.mutate()
  }

  return (
    <>
      <PageHeading title="Nuevo artículo" />

      <form onSubmit={submit} className="flex max-w-form flex-col gap-3">
        <Field
          label="Nombre del artículo"
          value={draft.name}
          onChange={(event) => setDraft({ ...draft, name: event.target.value })}
          required
        />

        <div className="flex flex-col gap-1.5">
          <SelectField
            label="Categoría"
            required
            value={draft.categoryId}
            onChange={(event) => setDraft({ ...draft, categoryId: event.target.value })}
          >
            <option value="">Elige una…</option>
            {categories.data?.items.map((category) => (
              <option key={category.id} value={category.id}>
                {category.name}
              </option>
            ))}
          </SelectField>
          {/* Fuera del campo a propósito, como en «Dar entrada»: lleva un
              enlace, y un enlace dentro de la pista de un `aria-describedby`
              es inalcanzable con el teclado desde el propio campo. Es lo que
              queda del flujo «creo la categoría para clasificar lo que estoy
              creando» ahora que cada alta vive en su página. */}
          <p className="text-caption text-ink-muted">
            ¿No está? Créala antes en{' '}
            <Link to="/catalogo/nueva-categoria" className="underline">
              «Nueva categoría»
            </Link>
            .
          </p>
        </div>

        <SelectField
          label="Unidad"
          value={draft.unit}
          onChange={(event) => setDraft({ ...draft, unit: event.target.value as MeasurementUnit })}
          hint="La fija el artículo: todas sus existencias se llevan en ella."
        >
          {UNITS.map((unit) => (
            <option key={unit} value={unit}>
              {UNIT_LABELS[unit]}
            </option>
          ))}
        </SelectField>

        {/* Opcional, y se dice por qué sirve: sin esto, una ubicación que
            declara un máximo en kilos no recibe ningún aviso nunca, que es lo
            que el Hito 3 vino a arreglar. */}
        <Field
          label="Peso de una unidad (g)"
          type="number"
          inputMode="decimal"
          min="0"
          value={draft.unitWeightGrams}
          onChange={(event) => setDraft({ ...draft, unitWeightGrams: event.target.value })}
          hint="Opcional. Es lo que deja avisar cuando un sitio con máximo en peso se llena."
        />

        {failure && <Notice tone="danger">{failure}</Notice>}

        <Button type="submit" variant="primary" busy={create.isPending} busyLabel="Creando…">
          Crear artículo
        </Button>
      </form>
    </>
  )
}
