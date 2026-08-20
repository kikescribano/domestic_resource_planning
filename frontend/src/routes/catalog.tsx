import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { BookOpen } from 'lucide-react'
import { useState, type FormEvent } from 'react'

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
  const [tab, setTab] = useState<'articles' | 'categories'>('articles')

  return (
    <>
      <PageHeading title="Catálogo" icon={BookOpen} />

      {/* Dos listas en una pantalla y no dos rutas: se editan a la vez --se crea
          una categoría para poder clasificar el artículo que se está creando--
          y separarlas obligaría a ir y volver. */}
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
  const [draft, setDraft] = useState<CategoryIdentity & { name: string }>({
    name: '',
    icon: null,
    color: null,
  })
  const [editing, setEditing] = useState<string | null>(null)
  const [failure, setFailure] = useState<string | null>(null)

  const categories = useQuery({
    queryKey: ['categories'],
    queryFn: () => api.listCategories(accessToken),
  })

  const create = useMutation({
    mutationFn: () =>
      api.createCategory({ name: draft.name, icon: draft.icon, color: draft.color }, accessToken),
    onSuccess: () => {
      setDraft({ name: '', icon: null, color: null })
      setFailure(null)
      void queryClient.invalidateQueries({ queryKey: ['categories'] })
    },
    onError: (error) => setFailure(humanMessage(error)),
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

  function submit(event: FormEvent) {
    event.preventDefault()
    create.mutate()
  }

  if (categories.isPending) return <Spinner label="Cargando categorías" />
  if (categories.isError) return <Notice tone="danger">{humanMessage(categories.error)}</Notice>

  return (
    <div className="flex flex-col gap-6">
      <form onSubmit={submit} className="flex max-w-form flex-col gap-3">
        <Field
          label="Nueva categoría"
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

        <Button type="submit" variant="primary" busy={create.isPending} busyLabel="Creando…">
          Crear categoría
        </Button>
      </form>

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
    <li className="flex min-h-touch flex-wrap items-center justify-between gap-2 rounded-md border border-border-subtle bg-surface-raised px-3 py-2">
      <span className="flex items-center gap-2">
        <CategoryMarker icon={category.icon} color={category.color} />
        <span className="text-body text-ink">{category.name}</span>
      </span>
      <span className="flex flex-wrap gap-1">
        {/* El nombre de la categoría dentro del nombre accesible: doce botones
            «Editar» en columna son doce controles indistinguibles para quien no
            ve la fila. Con `aria-label` y no con un `<span class="sr-only">`
            detrás del texto, que es lo primero que se intentó: **JSX se come el
            espacio inicial de la línea** y el lector de pantalla acababa
            diciendo «EditarAlimentación». Y el rótulo visible sigue estando
            dentro del nombre, que es lo que 2.5.3 exige. */}
        <Button variant="ghost" onClick={onEdit} aria-label={`Editar ${category.name}`}>
          Editar
        </Button>
        <Button
          variant="ghost"
          onClick={onRetire}
          disabled={busy}
          aria-label={`Retirar ${category.name}`}
        >
          Retirar
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

  // La búsqueda entra en la clave: cambiarla es otra consulta, no un filtro en
  // memoria. Con el catálogo entero de un hogar grande, filtrar en el cliente
  // significaría traérselo todo en cada tecla.
  const articles = useQuery({
    queryKey: ['articles', query],
    queryFn: () => api.listArticles(accessToken, { q: query || undefined }),
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
      setDraft({ name: '', categoryId: '', unit: 'UNIT', unitWeightGrams: '' })
      setFailure(null)
      void queryClient.invalidateQueries({ queryKey: ['articles'] })
    },
    onError: (error) => setFailure(humanMessage(error)),
  })

  const retire = useMutation({
    mutationFn: (id: string) => api.retireArticle(id, accessToken),
    onSuccess: () => {
      setFailure(null)
      void queryClient.invalidateQueries({ queryKey: ['articles'] })
    },
    onError: (error) => setFailure(humanMessage(error)),
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    create.mutate()
  }

  return (
    <div className="flex flex-col gap-6">
      <form onSubmit={submit} className="flex max-w-form flex-col gap-3">
        <Field
          label="Nombre del artículo"
          value={draft.name}
          onChange={(event) => setDraft({ ...draft, name: event.target.value })}
          required
        />

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

        <Button type="submit" variant="primary" busy={create.isPending} busyLabel="Creando…">
          Crear artículo
        </Button>
      </form>

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
          {query ? 'Prueba con otras palabras.' : 'Crea el primero con el formulario de arriba.'}
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
    <li className="flex min-h-touch flex-wrap items-center justify-between gap-2 rounded-md border border-border-subtle bg-surface-raised px-3 py-2">
      <span className="flex flex-wrap items-center gap-2">
        <span className="text-body text-ink">{article.name}</span>
        <span className="text-caption text-ink-muted">
          {article.category} · {UNIT_LABELS[article.unit]}
        </span>
        {article.retiredAt && <StatusBadge tone="decommissioned">Retirado</StatusBadge>}
      </span>
      {!article.retiredAt && (
        <Button variant="ghost" onClick={onRetire}>
          Retirar
        </Button>
      )}
    </li>
  )
}
