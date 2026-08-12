import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'

import {
  UNIT_LABELS,
  api,
  humanMessage,
  type Article,
  type Category,
  type MeasurementUnit,
} from '../api/client'
import { useAuthenticatedSession } from '../auth/SessionProvider'
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
      <PageHeading title="Catálogo" />

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

function CategoriesPanel() {
  const { accessToken } = useAuthenticatedSession()
  const queryClient = useQueryClient()
  const [name, setName] = useState('')
  const [failure, setFailure] = useState<string | null>(null)

  const categories = useQuery({
    queryKey: ['categories'],
    queryFn: () => api.listCategories(accessToken),
  })

  const create = useMutation({
    mutationFn: () => api.createCategory({ name }, accessToken),
    onSuccess: () => {
      setName('')
      setFailure(null)
      void queryClient.invalidateQueries({ queryKey: ['categories'] })
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
          value={name}
          onChange={(event) => setName(event.target.value)}
          required
          hint="Se muestra tal cual, así que va en tu idioma."
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
            onRetire={() => retire.mutate(category.id)}
            busy={retire.isPending}
          />
        ))}
      </ul>
    </div>
  )
}

function CategoryRow({
  category,
  onRetire,
  busy,
}: {
  category: Category
  onRetire: () => void
  busy: boolean
}) {
  return (
    <li className="flex min-h-touch flex-wrap items-center justify-between gap-2 rounded-md border border-border-subtle bg-surface-raised px-3 py-2">
      <span className="text-body text-ink">{category.name}</span>
      <Button variant="ghost" onClick={onRetire} disabled={busy}>
        Retirar
      </Button>
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
  const [draft, setDraft] = useState({ name: '', categoryId: '', unit: 'UNIT' as MeasurementUnit })

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
        { name: draft.name, categoryId: draft.categoryId, unit: draft.unit },
        accessToken,
      ),
    onSuccess: () => {
      setDraft({ name: '', categoryId: '', unit: 'UNIT' })
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
