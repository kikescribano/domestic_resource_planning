import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Warehouse } from 'lucide-react'
import { useState, type FormEvent } from 'react'

import {
  MOVEMENT_KIND_LABELS,
  api,
  humanMessage,
  type StockItem,
  type StockLot,
} from '../api/client'
import { useAuthenticatedSession } from '../auth/SessionProvider'
import {
  Button,
  Combobox,
  EmptyState,
  Field,
  Notice,
  PageHeading,
  Spinner,
  StatusBadge,
} from '../ui/primitives'

/**
 * El almacén: **qué hay, cuánto queda y cuándo se acaba o se estropea**.
 *
 * Su ficha se escribió antes que esta pantalla y está en
 * `docs/backend/modules/warehouse.md`, incluida la frontera que esta pantalla
 * respeta sin decirlo: **la cantidad que se ve es la del core**. Warehouse no
 * lleva un segundo contador, así que apuntar un consumo aquí acaba moviendo el
 * contador del core, y el apunte del cuaderno lo escribe el manejador del evento
 * que eso provoca.
 *
 * Lo que **no** hay aquí, y no es un olvido: no se pregunta si el módulo está
 * encendido. De eso se encarga `ModuleScreen`, que la envuelve, con el catálogo
 * que ya está en la caché de la sesión.
 */

export function WarehousePage() {
  const { accessToken } = useAuthenticatedSession()
  const queryClient = useQueryClient()

  const [query, setQuery] = useState('')
  const [belowMinimum, setBelowMinimum] = useState(false)
  const [expiringSoon, setExpiringSoon] = useState(false)

  const filters = {
    q: query.trim() || undefined,
    belowMinimum: belowMinimum || undefined,
    // Treinta días: la ventana con la que una persona hace la compra del mes.
    // La antelación del *aviso* es otra cosa y la fija el sitio o el artículo.
    expiringWithinDays: expiringSoon ? 30 : undefined,
  }

  const stock = useQuery({
    queryKey: ['stock', filters],
    queryFn: () => api.listStock(accessToken, filters),
  })

  const invalidate = () => void queryClient.invalidateQueries({ queryKey: ['stock'] })
  const isFiltered = Boolean(query.trim() || belowMinimum || expiringSoon)

  return (
    <>
      <PageHeading title="Almacén" icon={Warehouse} />

      <p className="max-w-prose text-body text-ink-muted">
        Lo que hay en la despensa, el garaje y el trastero. Apunta lo que gastas,
        pon un mínimo para que te avise cuando quede poco y anota las caducidades
        de lo que se pone malo.
      </p>

      <div className="mt-6 flex flex-wrap items-end gap-3">
        {/* Un `Combobox` y no un `SelectField`: una despensa tiene cientos de
            artículos, y `GET /warehouse/stock` lleva un parámetro `q` que existe
            justamente para esto. El primitivo llegó con este hito, después de
            quedar aplazado desde el Hito 2 de la Fase 1. */}
        <StockSearch value={query} onChange={setQuery} />

        <label className="flex min-h-touch items-center gap-2 text-body-sm text-ink">
          <input
            type="checkbox"
            checked={belowMinimum}
            onChange={(event) => setBelowMinimum(event.target.checked)}
          />
          Solo lo que queda poco
        </label>

        <label className="flex min-h-touch items-center gap-2 text-body-sm text-ink">
          <input
            type="checkbox"
            checked={expiringSoon}
            onChange={(event) => setExpiringSoon(event.target.checked)}
          />
          Solo lo que caduca este mes
        </label>
      </div>

      <div className="mt-6">
        {stock.isPending && <Spinner label="Cargando el almacén…" />}
        {stock.isError && <Notice tone="danger">{humanMessage(stock.error)}</Notice>}

        {stock.data && stock.data.items.length === 0 && (
          <EmptyState title={isFiltered ? 'Nada con ese filtro' : 'El almacén está vacío'}>
            {isFiltered
              ? 'Prueba a quitar algún filtro o a buscar otra cosa.'
              : 'Aquí aparece lo que das de entrada como consumible desde el inventario: el arroz, el detergente, los yogures.'}
          </EmptyState>
        )}

        {stock.data && stock.data.items.length > 0 && (
          <ul className="flex flex-col gap-3">
            {stock.data.items.map((item) => (
              <StockRow key={item.assetId} item={item} onChanged={invalidate} />
            ))}
          </ul>
        )}
      </div>
    </>
  )
}

/**
 * La búsqueda de artículos, con sugerencias que salen del propio almacén.
 *
 * Sugiere sobre lo que el hogar **tiene**, no sobre el catálogo entero: quien
 * busca aquí quiere saber cuánto le queda de algo, y ofrecerle artículos de los
 * que no hay nada sería ofrecerle callejones sin salida.
 */
function StockSearch({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  const { accessToken } = useAuthenticatedSession()

  const suggestions = useQuery({
    queryKey: ['stock-suggestions', value],
    queryFn: () => api.listStock(accessToken, { q: value.trim() || undefined }),
    // Sin `q` devuelve la despensa entera, que es justo lo que hace falta para
    // poder abrir la lista sin haber escrito nada.
    staleTime: 30_000,
  })

  // Un artículo puede tener existencias en varios sitios, y como sugerencia es
  // uno solo: repetir «Arroz» tres veces no ayuda a nadie a elegir.
  const options = Array.from(
    new Map(
      (suggestions.data?.items ?? []).map((item) => [
        item.articleId,
        { id: item.articleId, label: item.article, detail: item.location ?? undefined },
      ]),
    ).values(),
  ).slice(0, 20)

  return (
    <Combobox
      label="Buscar"
      value={value}
      options={options}
      onQueryChange={onChange}
      onSelect={(option) => onChange(option.label)}
      hint="Busca por el nombre del artículo. No distingue mayúsculas ni acentos."
      placeholder="Arroz, detergente…"
    />
  )
}

/**
 * Una fila del almacén, con su ficha desplegada dentro.
 *
 * Desplegar es un `<button>` con `aria-expanded` y no un `div` con `onClick`: es
 * lo que hace que se llegue con el tabulador y se abra con `Enter` y con
 * `Espacio` sin escribir un solo manejador de teclado.
 */
function StockRow({ item, onChanged }: { item: StockItem; onChanged: () => void }) {
  const [open, setOpen] = useState(false)

  return (
    <li className="rounded-lg border border-border-subtle bg-surface-raised">
      <button
        type="button"
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
        className="flex min-h-touch w-full flex-wrap items-center justify-between gap-2 p-4 text-left"
      >
        <span className="flex flex-wrap items-center gap-2">
          <span className="text-body font-medium text-ink">{item.article}</span>
          {/* Estado con etiqueta y no solo con color, que es la regla 4 de la
              dirección visual. */}
          {item.belowMinimum && <StatusBadge tone="warning">Queda poco</StatusBadge>}
          {item.nearestExpiry && <StatusBadge tone="neutral">Caduca {item.nearestExpiry}</StatusBadge>}
        </span>
        <span className="flex flex-wrap items-center gap-3 text-body-sm text-ink-muted">
          <span>
            {item.quantity} {item.unit.toLowerCase()}
          </span>
          {item.location && <span>{item.location}</span>}
        </span>
      </button>

      {open && <StockDetailPanel item={item} onChanged={onChanged} />}
    </li>
  )
}

function StockDetailPanel({ item, onChanged }: { item: StockItem; onChanged: () => void }) {
  const { accessToken } = useAuthenticatedSession()
  const queryClient = useQueryClient()
  const [failure, setFailure] = useState<string | null>(null)

  const detail = useQuery({
    queryKey: ['stock-item', item.assetId],
    queryFn: () => api.getStockItem(item.assetId, accessToken),
  })

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['stock-item', item.assetId] })
    onChanged()
  }

  return (
    <div className="border-t border-border-subtle p-4">
      {failure && <Notice tone="danger">{failure}</Notice>}

      <ConsumptionForm item={item} onDone={refresh} onFailure={setFailure} />
      <MinimumForm item={item} onDone={refresh} onFailure={setFailure} />

      <div className="mt-4">
        <p className="text-body-sm font-medium text-ink">Lotes con caducidad</p>

        {detail.isPending && <Spinner label="Cargando los lotes…" />}
        {detail.data && detail.data.lots.length === 0 && (
          <p className="mt-1 text-body-sm text-ink-muted">
            Todavía no has anotado ninguna caducidad. Lo que no está en ningún lote no se vigila.
          </p>
        )}
        {detail.data && detail.data.lots.length > 0 && (
          <ul className="mt-1 flex flex-col gap-1">
            {detail.data.lots.map((lot) => (
              <LotRow key={lot.id} lot={lot} unit={item.unit} onDone={refresh} onFailure={setFailure} />
            ))}
          </ul>
        )}

        <LotForm item={item} onDone={refresh} onFailure={setFailure} />
      </div>

      {detail.data && detail.data.movements.length > 0 && (
        <div className="mt-4">
          <p className="text-body-sm font-medium text-ink">Últimos movimientos</p>
          <ul className="mt-1 flex flex-col gap-1 text-body-sm text-ink-muted">
            {detail.data.movements.map((movement) => (
              <li key={movement.id} className="flex flex-wrap gap-2">
                <span className="text-ink">{MOVEMENT_KIND_LABELS[movement.kind]}</span>
                {movement.delta !== null && (
                  <span>
                    {movement.delta > 0 ? '+' : ''}
                    {movement.delta}
                  </span>
                )}
                {movement.location && <span>{movement.location}</span>}
                <span>{movement.occurredAt.slice(0, 10)}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}

/**
 * Apuntar lo gastado.
 *
 * **Es un delta y no un absoluto**, que es la diferencia entera con el
 * inventario del core: la pregunta que se hace una persona en la cocina es «me
 * he gastado 200 g», no «quedan 800». La conversión la hace el servidor, y la
 * regla de no gastar más de lo que hay también — replicarla aquí daría dos
 * versiones de la misma regla y la de aquí sería la que se quedaría vieja.
 */
function ConsumptionForm({
  item,
  onDone,
  onFailure,
}: {
  item: StockItem
  onDone: () => void
  onFailure: (message: string) => void
}) {
  const { accessToken } = useAuthenticatedSession()
  const [amount, setAmount] = useState('')

  const consume = useMutation({
    mutationFn: () => api.recordConsumption(item.assetId, Number(amount), accessToken),
    onSuccess: () => {
      setAmount('')
      onDone()
    },
    onError: (error) => onFailure(humanMessage(error)),
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    if (amount) consume.mutate()
  }

  return (
    <form onSubmit={submit} className="flex max-w-form flex-wrap items-end gap-2">
      <Field
        label={`Gastado de ${item.article} (${item.unit.toLowerCase()})`}
        type="number"
        inputMode="decimal"
        min="0"
        value={amount}
        onChange={(event) => setAmount(event.target.value)}
        hint="Lo que has gastado, no lo que queda."
      />
      <Button type="submit" variant="primary" busy={consume.isPending} busyLabel="Apuntando…">
        Apuntar consumo
      </Button>
    </form>
  )
}

/** El mínimo por debajo del cual avisa. Es del artículo, no de esta existencia. */
function MinimumForm({
  item,
  onDone,
  onFailure,
}: {
  item: StockItem
  onDone: () => void
  onFailure: (message: string) => void
}) {
  const { accessToken } = useAuthenticatedSession()
  const [minimum, setMinimum] = useState(item.minimumQuantity?.toString() ?? '')

  const save = useMutation({
    mutationFn: () =>
      api.updateWarehouseArticle(
        item.articleId,
        // Vacío manda `null`, que **deja de vigilar** el artículo: no es lo mismo
        // que un mínimo de cero, que significa «avísame cuando no quede nada».
        { minimumQuantity: minimum === '' ? null : Number(minimum) },
        accessToken,
      ),
    onSuccess: onDone,
    onError: (error) => onFailure(humanMessage(error)),
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    save.mutate()
  }

  return (
    <form onSubmit={submit} className="mt-3 flex max-w-form flex-wrap items-end gap-2">
      <Field
        label={`Avisarme cuando quede menos de (${item.unit.toLowerCase()})`}
        type="number"
        inputMode="decimal"
        min="0"
        value={minimum}
        onChange={(event) => setMinimum(event.target.value)}
        hint="Déjalo vacío para no vigilar este artículo."
      />
      <Button type="submit" busy={save.isPending} busyLabel="Guardando…">
        Guardar mínimo
      </Button>
    </form>
  )
}

function LotRow({
  lot,
  unit,
  onDone,
  onFailure,
}: {
  lot: StockLot
  unit: string
  onDone: () => void
  onFailure: (message: string) => void
}) {
  const { accessToken } = useAuthenticatedSession()

  const discard = useMutation({
    mutationFn: () => api.discardStockLot(lot.id, accessToken),
    onSuccess: onDone,
    onError: (error) => onFailure(humanMessage(error)),
  })

  return (
    <li className="flex flex-wrap items-center gap-2 text-body-sm text-ink">
      <span>
        {lot.quantity} {unit.toLowerCase()} · caduca el {lot.expiresOn}
        {lot.lotCode && <span className="text-ink-muted"> · lote {lot.lotCode}</span>}
      </span>
      {/* El nombre accesible lleva la fecha: con tres lotes, tres botones que
          pongan «Descartar» a secas son indistinguibles con lector de pantalla. */}
      <Button
        variant="ghost"
        onClick={() => discard.mutate()}
        busy={discard.isPending}
        busyLabel="Descartando…"
      >
        Descartar el lote que caduca el {lot.expiresOn}
      </Button>
    </li>
  )
}

function LotForm({
  item,
  onDone,
  onFailure,
}: {
  item: StockItem
  onDone: () => void
  onFailure: (message: string) => void
}) {
  const { accessToken } = useAuthenticatedSession()
  const [open, setOpen] = useState(false)
  const [expiresOn, setExpiresOn] = useState('')
  const [quantity, setQuantity] = useState('')
  const [lotCode, setLotCode] = useState('')

  const register = useMutation({
    mutationFn: () =>
      api.registerStockLot(
        {
          assetId: item.assetId,
          expiresOn,
          quantity: Number(quantity),
          lotCode: lotCode || null,
        },
        accessToken,
      ),
    onSuccess: () => {
      setExpiresOn('')
      setQuantity('')
      setLotCode('')
      setOpen(false)
      onDone()
    },
    onError: (error) => onFailure(humanMessage(error)),
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    if (expiresOn && quantity) register.mutate()
  }

  return (
    <div className="mt-3">
      <Button onClick={() => setOpen((value) => !value)} aria-expanded={open}>
        Anotar una caducidad de {item.article}
      </Button>

      {open && (
        <form onSubmit={submit} className="mt-3 flex max-w-form flex-col gap-3">
          <Field
            label="Caduca el"
            type="date"
            value={expiresOn}
            onChange={(event) => setExpiresOn(event.target.value)}
            required
          />
          <Field
            label={`Cuánto caduca (${item.unit.toLowerCase()})`}
            type="number"
            inputMode="decimal"
            min="0"
            value={quantity}
            onChange={(event) => setQuantity(event.target.value)}
            required
            // La regla la comprueba el servidor y su 409 se enseña donde ocurrió:
            // duplicarla aquí daría dos versiones de la misma regla.
            hint={`Puede ser menos de lo que hay (${item.quantity}), nunca más.`}
          />
          <Field
            label="Lote"
            value={lotCode}
            onChange={(event) => setLotCode(event.target.value)}
            hint="Opcional: el código del envase, si lo trae."
          />
          <div>
            <Button type="submit" variant="primary" busy={register.isPending} busyLabel="Guardando…">
              Guardar caducidad
            </Button>
          </div>
        </form>
      )}
    </div>
  )
}
