import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'

import {
  CONDITIONS,
  CONDITION_LABELS,
  DOCUMENT_TYPE_LABELS,
  UNIT_LABELS,
  api,
  formatDay,
  humanMessage,
  type ApiWarning,
  type Asset,
  type AssetCondition,
  type AssetStatus,
  type AssetType,
  type DocumentType,
} from '../api/client'
import { useAuthenticatedSession } from '../auth/SessionProvider'
import { FileGallery, UploadField, type GalleryItem } from '../ui/files'
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
 * Los assets del hogar: el listado y la ficha.
 *
 * Las **dos naturalezas** se comportan distinto y la interfaz lo respeta en
 * lugar de aplanarlo: un duradero se da de alta con su nombre; un consumible
 * **no se da de alta**, se le da entrada, y si ya había existencia en esa
 * ubicación la operación suma en vez de crear otra fila.
 */

const STATUS_LABELS: Record<AssetStatus, string> = {
  AVAILABLE: 'Disponible',
  LENT: 'Prestado',
  DECOMMISSIONED: 'De baja',
}

const STATUS_TONES: Record<AssetStatus, 'available' | 'lent' | 'decommissioned'> = {
  AVAILABLE: 'available',
  LENT: 'lent',
  DECOMMISSIONED: 'decommissioned',
}

/** Un consumible agotado no está de baja, pero tampoco disponible: es su propio estado. */
function statusOf(asset: Asset): { label: string; tone: 'available' | 'lent' | 'decommissioned' | 'out-of-stock' } {
  if (asset.status === 'AVAILABLE' && asset.type === 'CONSUMABLE' && asset.quantity === 0) {
    return { label: 'Agotado', tone: 'out-of-stock' }
  }
  return { label: STATUS_LABELS[asset.status], tone: STATUS_TONES[asset.status] }
}

function quantityOf(asset: Asset): string | null {
  if (asset.type !== 'CONSUMABLE' || asset.quantity === null) return null
  return `${asset.quantity} ${asset.unit ? UNIT_LABELS[asset.unit] : ''}`.trim()
}

// ---------------------------------------------------------------------------
// Listado
// ---------------------------------------------------------------------------

export function AssetsPage() {
  const { accessToken } = useAuthenticatedSession()
  const [type, setType] = useState<AssetType | ''>('')
  const [condition, setCondition] = useState<AssetCondition | ''>('')

  const assets = useQuery({
    queryKey: ['assets', type, condition],
    queryFn: () =>
      api.listAssets(accessToken, {
        ...(type ? { type } : {}),
        ...(condition ? { condition } : {}),
      }),
  })

  return (
    <>
      <PageHeading
        title="Inventario"
        action={
          <div className="flex gap-2">
            <Link
              to="/inventario/nuevo"
              className="inline-flex min-h-touch items-center rounded-md border border-border bg-surface-raised px-4 text-body font-medium text-ink"
            >
              Dar de alta
            </Link>
            <Link
              to="/inventario/entrada"
              className="inline-flex min-h-touch items-center rounded-md bg-accent px-4 text-body font-medium text-ink-inverse"
            >
              Dar entrada
            </Link>
          </div>
        }
      />

      <div className="flex flex-col gap-4">
        <div role="group" aria-label="Filtrar por naturaleza" className="flex flex-wrap gap-2">
          <FilterChip active={type === ''} onClick={() => setType('')}>
            Todo
          </FilterChip>
          <FilterChip active={type === 'DURABLE'} onClick={() => setType('DURABLE')}>
            Duraderos
          </FilterChip>
          <FilterChip active={type === 'CONSUMABLE'} onClick={() => setType('CONSUMABLE')}>
            Consumibles
          </FilterChip>
        </div>

        {/* En desplegable y no en más pastillas: son cinco valores, y cinco
            pastillas más al lado de las tres de arriba convierten la cabecera
            del listado en un panel de filtros. Va con `SelectField` porque es
            quien pone la pista en `aria-describedby` en vez de dentro del
            nombre accesible del campo. */}
        <SelectField
          label="Estado de conservación"
          hint="Lo que nadie ha anotado no aparece en ningún filtro: sin anotar no es un estado."
          className="max-w-form"
          value={condition}
          onChange={(event) => setCondition(event.target.value as AssetCondition | '')}
        >
          <option value="">Cualquiera</option>
          {CONDITIONS.map((value) => (
            <option key={value} value={value}>
              {CONDITION_LABELS[value]}
            </option>
          ))}
        </SelectField>

        {assets.isPending ? (
          <Spinner label="Cargando inventario" />
        ) : assets.isError ? (
          <Notice tone="danger">{humanMessage(assets.error)}</Notice>
        ) : assets.data.items.length === 0 ? (
          <EmptyState title="Aquí no hay nada todavía">
            Da de alta un duradero, o dale entrada a un consumible del catálogo.
          </EmptyState>
        ) : (
          <ul className="flex flex-col gap-2">
            {assets.data.items.map((asset) => (
              <AssetRow key={asset.id} asset={asset} />
            ))}
          </ul>
        )}
      </div>
    </>
  )
}

function FilterChip({
  active,
  onClick,
  children,
}: {
  active: boolean
  onClick: () => void
  children: string
}) {
  return (
    <button
      onClick={onClick}
      aria-pressed={active}
      className={[
        'min-h-touch rounded-full border px-4 text-body-sm',
        active ? 'border-accent bg-accent-soft font-medium text-accent-ink' : 'border-border text-ink-muted',
      ].join(' ')}
    >
      {children}
    </button>
  )
}

function AssetRow({ asset }: { asset: Asset }) {
  const status = statusOf(asset)
  const quantity = quantityOf(asset)

  return (
    <li>
      <Link
        to={`/inventario/${asset.id}`}
        className="flex min-h-touch flex-wrap items-center justify-between gap-2 rounded-md border border-border-subtle bg-surface-raised px-3 py-2 hover:bg-surface-hover"
      >
        <span className="flex flex-wrap items-baseline gap-2">
          <span className="text-body text-ink">{asset.name}</span>
          {asset.category && <span className="text-caption text-ink-muted">{asset.category}</span>}
          {/* En tinta y no en un segundo distintivo: la ficha de `StatusBadge`
              declara antiuso dos distintivos en la misma fila, y el presupuesto
              de calidez de `density.md` da un color por fila. El de la derecha
              ya lo gasta el estado, así que esto es un metadato. */}
          {asset.condition && (
            <span className="text-caption text-ink-muted">{CONDITION_LABELS[asset.condition]}</span>
          )}
        </span>
        <span className="flex items-center gap-2">
          {quantity && <span className="text-body-sm tabular-nums text-ink-muted">{quantity}</span>}
          <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
        </span>
      </Link>
    </li>
  )
}

// ---------------------------------------------------------------------------
// Alta de un duradero
// ---------------------------------------------------------------------------

export function NewAssetPage() {
  const { accessToken } = useAuthenticatedSession()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [failure, setFailure] = useState<string | null>(null)
  const [draft, setDraft] = useState({
    name: '',
    categoryId: '',
    locationId: '',
    serialNumber: '',
    acquiredOn: '',
    condition: '' as AssetCondition | '',
  })

  const categories = useQuery({
    queryKey: ['categories'],
    queryFn: () => api.listCategories(accessToken),
  })
  const locations = useQuery({
    queryKey: ['locations'],
    queryFn: () => api.listLocations(accessToken),
  })

  const create = useMutation({
    mutationFn: () =>
      api.createAsset(
        {
          name: draft.name,
          type: 'DURABLE',
          categoryId: draft.categoryId,
          ...(draft.locationId ? { location: { type: 'LOCATION', id: draft.locationId } } : {}),
          // Vacío es ausente, no cadena vacía: son opcionales de verdad y el
          // contrato no declara «sin número de serie» como un número de serie
          // que es «».
          ...(draft.serialNumber.trim() ? { serialNumber: draft.serialNumber.trim() } : {}),
          ...(draft.acquiredOn ? { acquiredOn: draft.acquiredOn } : {}),
          ...(draft.condition ? { condition: draft.condition } : {}),
        },
        accessToken,
      ),
    onSuccess: (asset) => {
      void queryClient.invalidateQueries({ queryKey: ['assets'] })
      void navigate(`/inventario/${asset.id}`)
    },
    onError: (error) => setFailure(humanMessage(error)),
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    create.mutate()
  }

  return (
    <>
      <PageHeading title="Dar de alta" />

      <form onSubmit={submit} className="flex max-w-form flex-col gap-3">
        <Notice tone="info">
          Esto es para lo que <strong>no se agota</strong>: un taladro, un sofá, una caldera. Un consumible
          entra por «Dar entrada», que suma sobre lo que ya haya.
        </Notice>

        <Field
          label="Nombre"
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
          label="Dónde está"
          value={draft.locationId}
          onChange={(event) => setDraft({ ...draft, locationId: event.target.value })}
        >
          <option value="">Sin asignar todavía</option>
          {locations.data?.items.map((location) => (
            <option key={location.id} value={location.id}>
              {location.name}
            </option>
          ))}
        </SelectField>

        <Field
          label="Número de serie (opcional)"
          value={draft.serialNumber}
          onChange={(event) => setDraft({ ...draft, serialNumber: event.target.value })}
          hint="Se puede rellenar después: la etiqueta suele estar detrás del aparato."
        />

        <Field
          label="Fecha de adquisición (opcional)"
          type="date"
          value={draft.acquiredOn}
          onChange={(event) => setDraft({ ...draft, acquiredOn: event.target.value })}
        />

        {/* Sin valor preseleccionado, y no por prudencia: preseleccionar «buen
            estado» convertiría en dato lo que nadie llegó a mirar, y el hueco
            significa exactamente eso —que no se anotó—. */}
        <SelectField
          label="Estado de conservación (opcional)"
          hint="Se puede anotar después, y se corrige cuando cambie."
          value={draft.condition}
          onChange={(event) => setDraft({ ...draft, condition: event.target.value as AssetCondition | '' })}
        >
          <option value="">Sin anotar</option>
          {CONDITIONS.map((value) => (
            <option key={value} value={value}>
              {CONDITION_LABELS[value]}
            </option>
          ))}
        </SelectField>

        {failure && <Notice tone="danger">{failure}</Notice>}

        <Button type="submit" variant="primary" busy={create.isPending} busyLabel="Dando de alta…">
          Dar de alta
        </Button>
      </form>
    </>
  )
}

// ---------------------------------------------------------------------------
// Entrada de consumible
// ---------------------------------------------------------------------------

export function IntakePage() {
  const { accessToken, claims } = useAuthenticatedSession()
  const queryClient = useQueryClient()
  const [failure, setFailure] = useState<string | null>(null)
  const [result, setResult] = useState<{ asset: Asset; summed: boolean } | null>(null)
  const [draft, setDraft] = useState({ articleId: '', locationId: '', quantity: '' })

  const articles = useQuery({
    queryKey: ['articles', ''],
    queryFn: () => api.listArticles(accessToken),
  })
  const locations = useQuery({
    queryKey: ['locations'],
    queryFn: () => api.listLocations(accessToken),
  })

  const chosen = articles.data?.items.find((article) => article.id === draft.articleId)

  const intake = useMutation({
    mutationFn: async () => {
      const before = draft.articleId
        ? await api
            .listAssets(accessToken, {
              articleId: draft.articleId,
              ...(draft.locationId ? { locationId: draft.locationId } : {}),
            })
            .then((page) => page.total)
        : 0

      const asset = await api.registerIntake(
        {
          articleId: draft.articleId,
          ownerId: claims.memberId,
          quantity: Number(draft.quantity),
          ...(draft.locationId ? { location: { type: 'LOCATION', id: draft.locationId } } : {}),
        },
        accessToken,
      )
      return { asset, summed: before > 0 }
    },
    onSuccess: (outcome) => {
      setFailure(null)
      setResult(outcome)
      setDraft({ ...draft, quantity: '' })
      void queryClient.invalidateQueries({ queryKey: ['assets'] })
    },
    onError: (error) => setFailure(humanMessage(error)),
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    intake.mutate()
  }

  return (
    <>
      <PageHeading title="Dar entrada" />

      <form onSubmit={submit} className="flex max-w-form flex-col gap-3">
        <Notice tone="info">
          Traer otro paquete no da de alta nada nuevo: si ya hay existencia de ese artículo en ese sitio,
          la cantidad <strong>se suma</strong> a la que había.
        </Notice>

        <div className="flex flex-col gap-1.5">
          <SelectField
            label="Artículo"
            required
            value={draft.articleId}
            onChange={(event) => setDraft({ ...draft, articleId: event.target.value })}
          >
            <option value="">Elige uno…</option>
            {articles.data?.items.map((article) => (
              <option key={article.id} value={article.id}>
                {article.name}
              </option>
            ))}
          </SelectField>
          {/* Fuera del campo a propósito: lleva un enlace, y un enlace dentro
              de la pista de un `aria-describedby` es inalcanzable con el
              teclado desde el propio campo. */}
          <p className="text-caption text-ink-muted">
            ¿No está? Créalo antes en el{' '}
            <Link to="/catalogo" className="underline">
              catálogo
            </Link>
            .
          </p>
        </div>

        <SelectField
          label="Dónde se guarda"
          value={draft.locationId}
          onChange={(event) => setDraft({ ...draft, locationId: event.target.value })}
        >
          <option value="">Sin asignar todavía</option>
          {locations.data?.items.map((location) => (
            <option key={location.id} value={location.id}>
              {location.name}
            </option>
          ))}
        </SelectField>

        <Field
          label={`Cantidad que entra${chosen ? ` (${UNIT_LABELS[chosen.unit]})` : ''}`}
          type="number"
          min="0"
          step="any"
          value={draft.quantity}
          onChange={(event) => setDraft({ ...draft, quantity: event.target.value })}
          required
          hint="En la unidad del artículo, que la fija él y no la existencia."
        />

        {failure && <Notice tone="danger">{failure}</Notice>}

        {result && (
          <Notice tone="success" title={result.summed ? 'Sumado a lo que había' : 'Primera existencia creada'}>
            <p>
              {result.asset.name}: ahora hay {quantityOf(result.asset)}.{' '}
              <Link to={`/inventario/${result.asset.id}`} className="underline">
                Ver la existencia
              </Link>
            </p>
            <Warnings warnings={result.asset.warnings} />
          </Notice>
        )}

        <Button type="submit" variant="primary" busy={intake.isPending} busyLabel="Dando entrada…">
          Dar entrada
        </Button>
      </form>
    </>
  )
}

// ---------------------------------------------------------------------------
// Ficha
// ---------------------------------------------------------------------------

export function AssetDetailPage() {
  const { id = '' } = useParams()
  const { accessToken } = useAuthenticatedSession()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [failure, setFailure] = useState<string | null>(null)
  const [warnings, setWarnings] = useState<ApiWarning[]>([])

  const asset = useQuery({
    queryKey: ['asset', id],
    queryFn: () => api.getAsset(id, accessToken),
  })
  const locations = useQuery({
    queryKey: ['locations'],
    queryFn: () => api.listLocations(accessToken),
  })

  function refresh(updated?: Asset) {
    setFailure(null)
    setWarnings(updated?.warnings ?? [])
    void queryClient.invalidateQueries({ queryKey: ['asset', id] })
    void queryClient.invalidateQueries({ queryKey: ['assets'] })
  }

  const move = useMutation({
    mutationFn: (locationId: string) =>
      api.updateAsset(
        id,
        { location: locationId ? { type: 'LOCATION', id: locationId } : null },
        accessToken,
      ),
    onSuccess: refresh,
    onError: (error) => setFailure(humanMessage(error)),
  })

  const adjust = useMutation({
    mutationFn: (quantity: number) => api.updateAsset(id, { quantity }, accessToken),
    onSuccess: refresh,
    onError: (error) => setFailure(humanMessage(error)),
  })

  const identify = useMutation({
    mutationFn: (patch: { serialNumber: string | null; acquiredOn: string | null }) =>
      api.updateAsset(id, patch, accessToken),
    onSuccess: refresh,
    onError: (error) => setFailure(humanMessage(error)),
  })

  const recondition = useMutation({
    // A `null` y no a cadena vacía: el contrato declara el campo anulable, y
    // vaciarlo es retirar la anotación —no anotar «ninguno de los cinco».
    mutationFn: (condition: AssetCondition | null) => api.updateAsset(id, { condition }, accessToken),
    onSuccess: refresh,
    onError: (error) => setFailure(humanMessage(error)),
  })

  const merge = useMutation({
    mutationFn: (targetAssetId: string) => api.mergeStockItems(id, targetAssetId, accessToken),
    onSuccess: (target) => {
      refresh()
      void navigate(`/inventario/${target.id}`)
    },
    onError: (error) => setFailure(humanMessage(error)),
  })

  const decommission = useMutation({
    mutationFn: () => api.decommissionAsset(id, accessToken),
    onSuccess: () => {
      refresh()
      void navigate('/inventario')
    },
    onError: (error) => setFailure(humanMessage(error)),
  })

  if (asset.isPending) return <Spinner label="Cargando la ficha" />
  if (asset.isError) return <Notice tone="danger">{humanMessage(asset.error)}</Notice>

  const current = asset.data
  const status = statusOf(current)
  const quantity = quantityOf(current)

  return (
    <>
      <PageHeading title={current.name} />

      <div className="flex flex-col gap-6">
        <AssetPhoto asset={current} accessToken={accessToken} />

        <dl className="grid gap-3 sm:grid-cols-2">
          <Detail label="Estado">
            <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
          </Detail>
          <Detail label="Naturaleza">
            {current.type === 'DURABLE' ? 'Duradero' : 'Consumible'}
          </Detail>
          <Detail label="Categoría">{current.category ?? '—'}</Detail>
          {quantity && <Detail label="Cantidad">{quantity}</Detail>}
          {current.type === 'DURABLE' && (
            <>
              <Detail label="Número de serie">{current.serialNumber ?? '—'}</Detail>
              <Detail label="Adquirido">{formatDay(current.acquiredOn)}</Detail>
              {/* «Sin anotar» y no un guion: el hueco de este campo significa
                  algo —que nadie lo ha mirado— y merece decirlo con palabras. */}
              <Detail label="Conservación">
                {current.condition ? CONDITION_LABELS[current.condition] : 'Sin anotar'}
              </Detail>
            </>
          )}
          {current.articleId && (
            // Lo heredado se señala: el nombre y la categoría de este asset son
            // los de su artículo, así que se corrigen allí y no aquí.
            <Detail label="Definido por">
              <Link to="/catalogo" className="underline">
                Un artículo del catálogo
              </Link>
            </Detail>
          )}
        </dl>

        {failure && <Notice tone="danger">{failure}</Notice>}
        <Warnings warnings={warnings} />

        {current.status !== 'DECOMMISSIONED' && (
          <div className="flex flex-col gap-6">
            <MoveForm
              locations={locations.data?.items ?? []}
              currentLocationId={current.location?.type === 'LOCATION' ? current.location.id : ''}
              onMove={move.mutate}
              busy={move.isPending}
            />

            {current.type === 'DURABLE' && (
              <>
                <IdentificationForm
                  serialNumber={current.serialNumber}
                  acquiredOn={current.acquiredOn}
                  onSave={identify.mutate}
                  busy={identify.isPending}
                />
                <ConditionForm
                  condition={current.condition}
                  onSave={recondition.mutate}
                  busy={recondition.isPending}
                />
              </>
            )}

            {current.type === 'CONSUMABLE' && (
              <>
                <AdjustForm current={current.quantity ?? 0} onAdjust={adjust.mutate} busy={adjust.isPending} />
                <MergeForm assetId={current.id} articleId={current.articleId} onMerge={merge.mutate} busy={merge.isPending} />
              </>
            )}

            <AssetDocuments assetId={current.id} assetName={current.name} accessToken={accessToken} />

            <section className="flex flex-col gap-2 border-t border-border-subtle pt-4">
              <h2 className="text-body font-medium text-ink">Dar de baja</h2>
              <p className="text-body-sm text-ink-muted">
                No se borra nada: la ficha se conserva para el historial.
                {current.type === 'CONSUMABLE' && ' Lo que quede se da por perdido.'}
              </p>
              <Button variant="danger" onClick={() => decommission.mutate()} busy={decommission.isPending}>
                Dar de baja
              </Button>
            </section>
          </div>
        )}
      </div>
    </>
  )
}

function Detail({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-0.5">
      <dt className="text-caption text-ink-muted">{label}</dt>
      <dd className="text-body text-ink">{children}</dd>
    </div>
  )
}

/**
 * Los avisos de una operación **con éxito**.
 *
 * Van en `warning` y después del cambio, nunca en lugar de él: la operación se
 * hizo. Pintarlos como error haría que el usuario buscase qué arreglar cuando no
 * hay nada roto.
 */
function Warnings({ warnings }: { warnings: ApiWarning[] }) {
  if (warnings.length === 0) return null
  return (
    <>
      {warnings.map((warning) => (
        <Notice key={warning.code} tone="warning">
          {warning.message}
        </Notice>
      ))}
    </>
  )
}

/**
 * El número de serie y la fecha de adquisición, que casi nunca se saben al dar
 * de alta.
 *
 * Es la razón de que esto sea un formulario de la ficha y no solo dos campos del
 * alta: la etiqueta con el número está pegada detrás del aparato, así que se
 * apunta el día que alguien lo mueve —o el día que hay que reclamar la garantía,
 * que es cuando de verdad hace falta.
 *
 * Vaciar un campo manda `null`, que **borra**, y no cadena vacía: un número de
 * serie mal copiado se quita, y quedarse con `""` sería fingir que se conoce.
 */
function IdentificationForm({
  serialNumber,
  acquiredOn,
  onSave,
  busy,
}: {
  serialNumber: string | null
  acquiredOn: string | null
  onSave: (patch: { serialNumber: string | null; acquiredOn: string | null }) => void
  busy: boolean
}) {
  const [draft, setDraft] = useState({ serialNumber: serialNumber ?? '', acquiredOn: acquiredOn ?? '' })

  function submit(event: FormEvent) {
    event.preventDefault()
    onSave({
      serialNumber: draft.serialNumber.trim() || null,
      acquiredOn: draft.acquiredOn || null,
    })
  }

  return (
    <form onSubmit={submit} className="flex max-w-form flex-col gap-2">
      <h2 className="text-body font-medium text-ink">Identificación</h2>
      <Field
        label="Número de serie"
        value={draft.serialNumber}
        onChange={(event) => setDraft({ ...draft, serialNumber: event.target.value })}
        hint="Lo que distingue dos unidades idénticas, y lo que pide un fabricante al reclamar una garantía."
      />
      <Field
        label="Fecha de adquisición"
        type="date"
        value={draft.acquiredOn}
        onChange={(event) => setDraft({ ...draft, acquiredOn: event.target.value })}
      />
      <Button type="submit" busy={busy} busyLabel="Guardando…">
        Guardar
      </Button>
    </form>
  )
}

/**
 * El estado de conservación, que es lo único de la ficha que **cambia con el
 * tiempo sin que nadie toque la cosa**.
 *
 * Va en su propia sección y no dentro de «Identificación» porque no identifica
 * nada: el número de serie de un taladro es el mismo el día que se compra y el
 * día que se tira, y esto es justamente lo contrario.
 *
 * Solo en un duradero. Una existencia de consumible no lo lleva —la API lo
 * rechaza con un `400`— y lo que le pasa a un lote es del módulo Almacén.
 */
function ConditionForm({
  condition,
  onSave,
  busy,
}: {
  condition: AssetCondition | null
  onSave: (condition: AssetCondition | null) => void
  busy: boolean
}) {
  const [value, setValue] = useState<AssetCondition | ''>(condition ?? '')

  return (
    <section className="flex max-w-form flex-col gap-2">
      <h2 className="text-body font-medium text-ink">Estado de conservación</h2>
      <SelectField
        label="En qué estado está"
        hint="Sin anotar es un valor legítimo: significa que nadie lo ha mirado."
        value={value}
        onChange={(event) => setValue(event.target.value as AssetCondition | '')}
      >
        <option value="">Sin anotar</option>
        {CONDITIONS.map((option) => (
          <option key={option} value={option}>
            {CONDITION_LABELS[option]}
          </option>
        ))}
      </SelectField>
      <Button onClick={() => onSave(value || null)} busy={busy} busyLabel="Guardando…">
        Guardar estado
      </Button>
    </section>
  )
}

function MoveForm({
  locations,
  currentLocationId,
  onMove,
  busy,
}: {
  locations: { id: string; name: string }[]
  currentLocationId: string
  onMove: (locationId: string) => void
  busy: boolean
}) {
  const [locationId, setLocationId] = useState(currentLocationId)

  return (
    <section className="flex max-w-form flex-col gap-2">
      <h2 className="text-body font-medium text-ink">Mover</h2>
      <SelectField
        label="Nueva ubicación"
        value={locationId}
        onChange={(event) => setLocationId(event.target.value)}
      >
        <option value="">Sin ubicación</option>
        {locations.map((location) => (
          <option key={location.id} value={location.id}>
            {location.name}
          </option>
        ))}
      </SelectField>
      <Button onClick={() => onMove(locationId)} busy={busy} busyLabel="Moviendo…">
        Mover aquí
      </Button>
    </section>
  )
}

function AdjustForm({
  current,
  onAdjust,
  busy,
}: {
  current: number
  onAdjust: (quantity: number) => void
  busy: boolean
}) {
  const [value, setValue] = useState(String(current))

  return (
    <section className="flex max-w-form flex-col gap-2">
      <h2 className="text-body font-medium text-ink">Corregir la cantidad</h2>
      <Field
        label="Cantidad que hay ahora"
        type="number"
        min="0"
        step="any"
        value={value}
        onChange={(event) => setValue(event.target.value)}
        hint="Es la cantidad total, no lo que se añade: para eso está «Dar entrada»."
      />
      <Button onClick={() => onAdjust(Number(value))} busy={busy} busyLabel="Guardando…">
        Guardar cantidad
      </Button>
    </section>
  )
}

function MergeForm({
  assetId,
  articleId,
  onMerge,
  busy,
}: {
  assetId: string
  articleId: string | null
  onMerge: (targetAssetId: string) => void
  busy: boolean
}) {
  const { accessToken } = useAuthenticatedSession()
  const [targetId, setTargetId] = useState('')

  const candidates = useQuery({
    queryKey: ['assets', 'merge-candidates', articleId],
    queryFn: () => api.listAssets(accessToken, { articleId: articleId ?? undefined }),
    enabled: Boolean(articleId),
  })

  const others = (candidates.data?.items ?? []).filter(
    (candidate) => candidate.id !== assetId && candidate.status !== 'DECOMMISSIONED',
  )
  if (others.length === 0) return null

  return (
    <section className="flex max-w-form flex-col gap-2">
      <h2 className="text-body font-medium text-ink">Unir con otra existencia</h2>
      <p className="text-body-sm text-ink-muted">
        Esta desaparece y su cantidad pasa a la que elijas, que conserva su sitio y su propietario.
      </p>
      <SelectField
        label="Existencia que se queda"
        value={targetId}
        onChange={(event) => setTargetId(event.target.value)}
      >
        <option value="">Elige cuál se queda…</option>
        {others.map((candidate) => (
          <option key={candidate.id} value={candidate.id}>
            {candidate.name} — {quantityOf(candidate)}
          </option>
        ))}
      </SelectField>
      <Button onClick={() => onMerge(targetId)} disabled={!targetId} busy={busy} busyLabel="Uniendo…">
        Unir
      </Button>
    </section>
  )
}

/**
 * La foto del asset, y el hueco para ponerle una.
 *
 * Subir y adjuntar son **dos pasos**: primero `POST /files`, que devuelve el
 * `fileId`, y después el `PATCH` con `photoFileId`. Aquí van seguidos porque no
 * hay más formulario que rellenar, pero siguen siendo dos operaciones y el
 * segundo puede fallar por su cuenta —si el fichero ya colgara de otro sitio.
 */
function AssetPhoto({ asset, accessToken }: { asset: Asset; accessToken: string }) {
  const queryClient = useQueryClient()
  const [problem, setProblem] = useState<string | null>(null)

  const attach = useMutation({
    mutationFn: (photoFileId: string | null) =>
      api.updateAsset(asset.id, { photoFileId }, accessToken),
    onSuccess: () => {
      setProblem(null)
      void queryClient.invalidateQueries({ queryKey: ['asset', asset.id] })
      void queryClient.invalidateQueries({ queryKey: ['storage'] })
    },
    onError: (error) => setProblem(humanMessage(error)),
  })

  return (
    <section className="flex flex-col gap-3">
      {asset.photoThumbnailUrl ? (
        <div className="flex items-center gap-3">
          <img
            src={asset.photoThumbnailUrl}
            alt={`Foto de ${asset.name}`}
            loading="lazy"
            // Una miniatura que falla es una URL caducada, no una imagen rota:
            // se vuelve a pedir la ficha, que trae una URL fresca.
            onError={() => void queryClient.invalidateQueries({ queryKey: ['asset', asset.id] })}
            className="size-24 rounded-md object-cover"
          />
          <Button variant="ghost" onClick={() => attach.mutate(null)} busy={attach.isPending}>
            Quitar la foto
          </Button>
        </div>
      ) : (
        <UploadField
          label="Añadir una foto"
          accept="image"
          accessToken={accessToken}
          onUploaded={(file) => attach.mutate(file.id)}
        />
      )}

      {problem && <Notice tone="danger">{problem}</Notice>}
    </section>
  )
}

/**
 * Facturas, garantías y manuales de este asset.
 *
 * Admite las dos vías porque las dos son reales: la factura ya está en el correo
 * y el manual en la web del fabricante —eso es un enlace—, pero la garantía que
 * llegó en un sobre no tiene URL ninguna y hay que subirla.
 */
function AssetDocuments({
  assetId,
  assetName,
  accessToken,
}: {
  assetId: string
  assetName: string
  accessToken: string
}) {
  const queryClient = useQueryClient()
  const [type, setType] = useState<DocumentType>('INVOICE')
  const [url, setUrl] = useState('')
  const [problem, setProblem] = useState<string | null>(null)

  const documents = useQuery({
    queryKey: ['documents', assetId],
    queryFn: () => api.listDocuments(accessToken, { assetId }),
  })

  const files = useQuery({
    queryKey: ['files'],
    queryFn: () => api.listFiles(accessToken),
  })

  function refresh() {
    setProblem(null)
    void queryClient.invalidateQueries({ queryKey: ['documents', assetId] })
    void queryClient.invalidateQueries({ queryKey: ['files'] })
    void queryClient.invalidateQueries({ queryKey: ['storage'] })
  }

  const attach = useMutation({
    mutationFn: (body: Record<string, unknown>) => api.attachDocument({ assetId, type, ...body }, accessToken),
    onSuccess: () => {
      setUrl('')
      refresh()
    },
    onError: (error) => setProblem(humanMessage(error)),
  })

  const remove = useMutation({
    mutationFn: (id: string) => api.deleteDocument(id, accessToken),
    onSuccess: refresh,
    onError: (error) => setProblem(humanMessage(error)),
  })

  // La miniatura de un documento sale de su fichero, que es otra colección: el
  // documento solo lleva el `fileId`.
  const thumbnails = new Map((files.data?.items ?? []).map((file) => [file.id, file]))

  const items: GalleryItem[] = (documents.data?.items ?? []).map((document) => {
    const file = document.fileId ? thumbnails.get(document.fileId) : undefined
    return {
      id: document.id,
      thumbnailUrl: file?.thumbnailUrl ?? null,
      name: file?.originalName ?? document.url ?? 'Documento',
      caption: DOCUMENT_TYPE_LABELS[document.type],
    }
  })

  return (
    <section className="flex flex-col gap-3 border-t border-border-subtle pt-4">
      <h2 className="text-body font-medium text-ink">Documentación</h2>

      <FileGallery
        label={`Documentos de ${assetName}`}
        items={items}
        onOpen={(item) => {
          const document = documents.data?.items.find((candidate) => candidate.id === item.id)
          if (!document) return
          const target = document.url ?? `/api/v1/files/${document.fileId}/content`
          window.open(target, '_blank', 'noopener')
        }}
        onRemove={(item) => remove.mutate(item.id)}
        onStale={() => void queryClient.invalidateQueries({ queryKey: ['files'] })}
        empty={<EmptyState title="Sin documentación todavía." />}
      />

      <SelectField label="Tipo" value={type} onChange={(event) => setType(event.target.value as DocumentType)}>
        {Object.entries(DOCUMENT_TYPE_LABELS).map(([value, label]) => (
          <option key={value} value={value}>
            {label}
          </option>
        ))}
      </SelectField>

      <UploadField
        label="Subir un documento"
        accept="document"
        accessToken={accessToken}
        onUploaded={(file) => attach.mutate({ fileId: file.id })}
      />

      <form
        className="flex flex-col gap-2"
        onSubmit={(event) => {
          event.preventDefault()
          if (url.trim()) attach.mutate({ url: url.trim() })
        }}
      >
        <Field
          label="…o un enlace a donde ya vive"
          type="url"
          value={url}
          hint="La factura que está en el correo, el manual de la web del fabricante."
          onChange={(event) => setUrl(event.target.value)}
        />
        <Button type="submit" disabled={!url.trim()} busy={attach.isPending}>
          Adjuntar el enlace
        </Button>
      </form>

      {problem && <Notice tone="danger">{problem}</Notice>}
    </section>
  )
}
