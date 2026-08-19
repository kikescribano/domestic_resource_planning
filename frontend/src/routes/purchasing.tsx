import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'

import {
  ITEM_ORIGIN_LABELS,
  SERVICE_CATEGORY_LABELS,
  api,
  humanMessage,
  type ServiceCategory,
  type ShoppingItem,
} from '../api/client'
import { useAuthenticatedSession } from '../auth/SessionProvider'
import {
  Button,
  Combobox,
  EmptyState,
  Field,
  Notice,
  PageHeading,
  SelectField,
  Spinner,
  StatusBadge,
} from '../ui/primitives'

/**
 * Compras: **qué falta, qué está pedido y qué ha entrado en casa**.
 *
 * Su ficha se escribió antes que esta pantalla y está en
 * `docs/backend/modules/purchasing.md`, incluidas las dos fronteras que esta
 * pantalla respeta sin decirlo:
 *
 * **Contra el almacén.** Aquí no se ve cuánto queda de nada: eso es del almacén,
 * y lo que llega de él es una línea ya puesta en la lista con su motivo. Por eso
 * la pantalla **no cambia de forma según ese módulo esté encendido o apagado**;
 * lo único que cambia es de dónde salen las líneas, y eso ya lo dice cada una en
 * su etiqueta de origen.
 *
 * **Contra el core.** Recibir una compra acaba dando entrada en el inventario, y
 * eso lo hace el servidor invocando la operación del core. Aquí no se construye
 * ninguna existencia.
 *
 * Lo que **no** hay, y no es un olvido: no se pregunta si el módulo está
 * encendido. De eso se encarga `ModuleScreen`, que la envuelve.
 */

export function PurchasingPage() {
  const [tab, setTab] = useState<'list' | 'purchases'>('list')

  return (
    <>
      <PageHeading title="Compras" />

      <p className="max-w-prose text-body text-ink-muted">
        Lo que hace falta en casa. Si tienes el almacén encendido, lo que se acaba
        entra solo; si no, lo apuntas tú. Cuando vayas a comprar, agrupa lo que te
        llevas y márcalo al volver: lo que compres entra en el inventario.
      </p>

      {/* Dos pestañas con `role="tab"` y no dos enlaces: son dos vistas del mismo
          recurso y no dos páginas, así que cambiar de una a otra no debería
          cambiar la URL ni recargar la navegación. */}
      <div role="tablist" aria-label="Qué ver de las compras" className="mt-6 flex gap-2">
        <TabButton current={tab} value="list" onSelect={setTab}>
          La lista
        </TabButton>
        <TabButton current={tab} value="purchases" onSelect={setTab}>
          Las compras
        </TabButton>
      </div>

      <div id={`panel-${tab}`} role="tabpanel" aria-labelledby={`tab-${tab}`} className="mt-6">
        {tab === 'list' ? <ShoppingListPanel /> : <PurchasesPanel />}
      </div>
    </>
  )
}

function TabButton({
  current,
  value,
  onSelect,
  children,
}: {
  current: string
  value: 'list' | 'purchases'
  onSelect: (value: 'list' | 'purchases') => void
  children: string
}) {
  const selected = current === value

  return (
    <button
      type="button"
      role="tab"
      id={`tab-${value}`}
      aria-selected={selected}
      aria-controls={`panel-${value}`}
      onClick={() => onSelect(value)}
      className={[
        'min-h-touch rounded-md px-4 py-2 text-body font-medium',
        // `text-ink-inverse` sobre `bg-accent`, que es el par que usa el botón
        // primario y el que `check-contrast.py` mide. Un token inventado
        // —`text-accent-contrast`— no falla al construir: hereda el color del
        // texto y deja 2,67:1 sobre el naranja, que es lo que axe encontró.
        selected
          ? 'bg-accent text-ink-inverse'
          : 'border border-border bg-surface-raised text-ink',
      ].join(' ')}
    >
      {children}
    </button>
  )
}

// ---------------------------------------------------------------------------
// La lista de la compra
// ---------------------------------------------------------------------------

function ShoppingListPanel() {
  const { accessToken } = useAuthenticatedSession()
  const queryClient = useQueryClient()
  const [selected, setSelected] = useState<string[]>([])
  const [failure, setFailure] = useState<string | null>(null)

  const list = useQuery({
    queryKey: ['shopping-list'],
    queryFn: () => api.listShoppingList(accessToken),
  })

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['shopping-list'] })
    void queryClient.invalidateQueries({ queryKey: ['purchases'] })
  }

  // Solo lo que todavía no va en una compra: lo que ya va no se puede volver a
  // llevar, y ofrecerlo sería ofrecer un botón que responde 409.
  const available = (list.data?.items ?? []).filter((item) => item.status === 'NEEDED')

  return (
    <>
      {failure && <Notice tone="danger">{failure}</Notice>}

      <AddItemForm onDone={invalidate} onFailure={setFailure} />

      <div className="mt-6">
        {list.isPending && <Spinner label="Cargando la lista de la compra…" />}
        {list.isError && <Notice tone="danger">{humanMessage(list.error)}</Notice>}

        {list.data && list.data.items.length === 0 && (
          <EmptyState title="No hace falta nada">
            Cuando algo se acabe, el almacén lo pondrá aquí. Y lo que no lleve la
            cuenta, lo apuntas tú arriba.
          </EmptyState>
        )}

        {list.data && list.data.items.length > 0 && (
          <ul className="flex flex-col gap-2">
            {list.data.items.map((item) => (
              <ShoppingItemRow
                key={item.id}
                item={item}
                checked={selected.includes(item.id)}
                onToggle={() =>
                  setSelected((current) =>
                    current.includes(item.id)
                      ? current.filter((id) => id !== item.id)
                      : [...current, item.id],
                  )
                }
                onDone={invalidate}
                onFailure={setFailure}
              />
            ))}
          </ul>
        )}
      </div>

      {available.length > 0 && (
        <NewPurchaseForm
          itemIds={selected.filter((id) => available.some((item) => item.id === id))}
          onDone={() => {
            setSelected([])
            invalidate()
          }}
          onFailure={setFailure}
        />
      )}
    </>
  )
}

/**
 * Apuntar algo a mano.
 *
 * **Es lo que hace que la lista sirva con el almacén apagado**, así que no es un
 * añadido de comodidad: sin ese módulo nadie detecta la falta, y sin esto la
 * lista estaría vacía para siempre en un hogar que no lo quiera.
 *
 * Admite las dos formas que el contrato declara: un artículo del catálogo, o un
 * nombre suelto para lo que todavía no está dado de alta. Lo segundo se compra y
 * ahí acaba —no entra en el inventario— y la pantalla lo dice en la pista en vez
 * de dejar que se descubra al recibir.
 */
function AddItemForm({ onDone, onFailure }: { onDone: () => void; onFailure: (message: string) => void }) {
  const { accessToken } = useAuthenticatedSession()
  const [text, setText] = useState('')
  const [articleId, setArticleId] = useState<string | null>(null)
  const [quantity, setQuantity] = useState('')

  // Un `Combobox` y no un `SelectField`: un catálogo doméstico tiene cientos de
  // artículos, y `GET /articles` lleva un parámetro `q` que existe para esto.
  const suggestions = useQuery({
    queryKey: ['article-suggestions', text],
    queryFn: () => api.listArticles(accessToken, { q: text.trim() || undefined }),
    staleTime: 30_000,
  })

  const options = (suggestions.data?.items ?? [])
    .slice(0, 20)
    .map((article) => ({ id: article.id, label: article.name, detail: article.category ?? undefined }))

  const add = useMutation({
    mutationFn: () =>
      api.addShoppingListItem(
        {
          // Uno de los dos y nunca los dos: si se ha elegido del catálogo va el
          // artículo, y si no, el texto tal cual se escribió.
          ...(articleId ? { articleId } : { name: text.trim() }),
          ...(quantity ? { quantity: Number(quantity) } : {}),
        },
        accessToken,
      ),
    onSuccess: () => {
      setText('')
      setArticleId(null)
      setQuantity('')
      onDone()
    },
    onError: (error) => onFailure(humanMessage(error)),
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    if (text.trim()) add.mutate()
  }

  return (
    <form onSubmit={submit} className="mt-6 flex flex-wrap items-end gap-3">
      <div className="min-w-[16rem] flex-1">
        <Combobox
          label="Qué hace falta"
          value={text}
          options={options}
          onQueryChange={(value) => {
            setText(value)
            // Escribir después de haber elegido deshace la elección: si no, la
            // línea se daría de alta con el artículo viejo y el texto nuevo.
            setArticleId(null)
          }}
          onSelect={(option) => {
            setText(option.label)
            setArticleId(option.id)
          }}
          hint="Elige uno del catálogo, o escribe lo que sea. Lo que no esté en el catálogo se compra igual, pero no entra en el inventario."
          placeholder="Arroz, pilas AA…"
        />
      </div>

      <Field
        label="Cuánta"
        type="number"
        min="0"
        step="any"
        value={quantity}
        onChange={(event) => setQuantity(event.target.value)}
        hint="Opcional"
      />

      <Button type="submit" variant="primary" busy={add.isPending} busyLabel="Apuntando…">
        Apuntar
      </Button>
    </form>
  )
}

function ShoppingItemRow({
  item,
  checked,
  onToggle,
  onDone,
  onFailure,
}: {
  item: ShoppingItem
  checked: boolean
  onToggle: () => void
  onDone: () => void
  onFailure: (message: string) => void
}) {
  const { accessToken } = useAuthenticatedSession()

  const dismiss = useMutation({
    mutationFn: () => api.dismissShoppingListItem(item.id, accessToken),
    onSuccess: onDone,
    onError: (error) => onFailure(humanMessage(error)),
  })

  const inPurchase = item.status === 'IN_PURCHASE'

  return (
    <li className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-border-subtle bg-surface-raised p-4">
      <label className="flex min-h-touch flex-1 items-center gap-3">
        {/* Deshabilitado y no oculto cuando ya va en una compra: quitarlo movería
            las filas de sitio cada vez que alguien abre una compra. */}
        <input type="checkbox" checked={checked} disabled={inPurchase} onChange={onToggle} />
        <span className="flex flex-wrap items-center gap-2">
          <span className="text-body font-medium text-ink">{item.name}</span>
          {item.quantity !== null && (
            <span className="text-body-sm text-ink-muted">
              {item.quantity} {item.unit ? item.unit.toLowerCase() : ''}
            </span>
          )}
          {item.packLabel && <span className="text-caption text-ink-subtle">envase de {item.packLabel}</span>}
          {/* Estado con etiqueta y no solo con color, que es la regla 4 de la
              dirección visual. */}
          <StatusBadge tone={item.origin === 'DEPLETED' ? 'warning' : 'neutral'}>
            {ITEM_ORIGIN_LABELS[item.origin]}
          </StatusBadge>
          {inPurchase && <StatusBadge tone="success">En una compra</StatusBadge>}
        </span>
      </label>

      {!inPurchase && (
        <Button
          variant="secondary"
          onClick={() => dismiss.mutate()}
          busy={dismiss.isPending}
          busyLabel="Quitando…"
        >
          No hace falta
        </Button>
      )}
    </li>
  )
}

/**
 * Abrir una compra con lo marcado.
 *
 * El selector de dónde se compra sale de **este** módulo y no del de proveedores,
 * y por eso **con proveedores apagado no falla: no aparece**. La lista llega
 * vacía del servidor —la degradación la pone él— así que aquí no hay ninguna
 * comprobación de si ese módulo está encendido.
 */
function NewPurchaseForm({
  itemIds,
  onDone,
  onFailure,
}: {
  itemIds: string[]
  onDone: () => void
  onFailure: (message: string) => void
}) {
  const { accessToken } = useAuthenticatedSession()
  const [supplierId, setSupplierId] = useState('')

  const shops = useQuery({
    queryKey: ['purchasing-suppliers'],
    queryFn: () => api.listPurchasingSuppliers(accessToken),
    staleTime: 5 * 60_000,
  })

  const open = useMutation({
    mutationFn: () =>
      api.createPurchase({ itemIds, ...(supplierId ? { supplierId } : {}) }, accessToken),
    onSuccess: () => {
      setSupplierId('')
      onDone()
    },
    onError: (error) => onFailure(humanMessage(error)),
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    if (itemIds.length > 0) open.mutate()
  }

  return (
    <form onSubmit={submit} className="mt-6 flex flex-wrap items-end gap-3 border-t border-border-subtle pt-6">
      {/* Solo si hay dónde elegir. Con el módulo de proveedores apagado la lista
          llega vacía, así que el campo no se pinta y nadie tiene que enterarse
          de por qué. */}
      {shops.data && shops.data.length > 0 && (
        <SelectField
          label="Dónde vas a comprar"
          value={supplierId}
          onChange={(event) => setSupplierId(event.target.value)}
          hint="Opcional. Se guarda el nombre de hoy, así que la compra lo seguirá diciendo aunque el sitio cambie."
        >
          <option value="">Sin decirlo</option>
          {shops.data.map((shop) => (
            <option key={shop.id} value={shop.id}>
              {shop.name}
              {shop.detail && shop.detail in SERVICE_CATEGORY_LABELS
                ? ` · ${SERVICE_CATEGORY_LABELS[shop.detail as ServiceCategory]}`
                : ''}
            </option>
          ))}
        </SelectField>
      )}

      <Button type="submit" variant="primary" disabled={itemIds.length === 0} busy={open.isPending} busyLabel="Abriendo…">
        {itemIds.length === 0
          ? 'Marca lo que te llevas'
          : `Me llevo ${itemIds.length} ${itemIds.length === 1 ? 'cosa' : 'cosas'}`}
      </Button>
    </form>
  )
}

// ---------------------------------------------------------------------------
// Las compras
// ---------------------------------------------------------------------------

function PurchasesPanel() {
  const { accessToken } = useAuthenticatedSession()
  const queryClient = useQueryClient()
  const [failure, setFailure] = useState<string | null>(null)

  const purchases = useQuery({
    queryKey: ['purchases'],
    queryFn: () => api.listPurchases(accessToken),
  })

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['purchases'] })
    void queryClient.invalidateQueries({ queryKey: ['shopping-list'] })
    // La compra recibida ha dado entrada en el inventario, así que lo que el
    // core y el almacén tengan en caché acaba de quedarse viejo.
    void queryClient.invalidateQueries({ queryKey: ['assets'] })
    void queryClient.invalidateQueries({ queryKey: ['stock'] })
  }

  return (
    <>
      {failure && <Notice tone="danger">{failure}</Notice>}

      {purchases.isPending && <Spinner label="Cargando las compras…" />}
      {purchases.isError && <Notice tone="danger">{humanMessage(purchases.error)}</Notice>}

      {purchases.data && purchases.data.items.length === 0 && (
        <EmptyState title="Todavía no has hecho ninguna compra">
          Marca en la lista lo que te llevas y ábrela desde ahí.
        </EmptyState>
      )}

      {purchases.data && purchases.data.items.length > 0 && (
        <ul className="flex flex-col gap-3">
          {purchases.data.items.map((purchase) => (
            <PurchaseRow
              key={purchase.id}
              id={purchase.id}
              summary={purchase}
              onChanged={invalidate}
              onFailure={setFailure}
            />
          ))}
        </ul>
      )}
    </>
  )
}

function PurchaseRow({
  id,
  summary,
  onChanged,
  onFailure,
}: {
  id: string
  summary: { status: string; supplier: string | null; createdAt: string }
  onChanged: () => void
  onFailure: (message: string) => void
}) {
  const [open, setOpen] = useState(false)

  const tone = summary.status === 'OPEN' ? 'warning' : summary.status === 'RECEIVED' ? 'success' : 'neutral'
  const label = summary.status === 'OPEN' ? 'Abierta' : summary.status === 'RECEIVED' ? 'Recibida' : 'Anulada'

  return (
    <li className="rounded-lg border border-border-subtle bg-surface-raised">
      {/* Desplegar es un `<button>` con `aria-expanded` y no un `div` con
          `onClick`: es lo que hace que se llegue con el tabulador y se abra con
          `Enter` y con `Espacio` sin escribir un manejador de teclado. */}
      <button
        type="button"
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
        className="flex min-h-touch w-full flex-wrap items-center justify-between gap-2 p-4 text-left"
      >
        <span className="flex flex-wrap items-center gap-2">
          <span className="text-body font-medium text-ink">{summary.supplier ?? 'Sin decir dónde'}</span>
          <StatusBadge tone={tone}>{label}</StatusBadge>
        </span>
        <span className="text-body-sm text-ink-muted">{summary.createdAt.slice(0, 10)}</span>
      </button>

      {open && <PurchasePanel id={id} onChanged={onChanged} onFailure={onFailure} />}
    </li>
  )
}

/**
 * La ficha de una compra, y **el cierre del ciclo**.
 *
 * Recibir es lo único de este módulo que escribe fuera de él: cada línea con
 * artículo acaba dando entrada en el inventario, que **suma** sobre la existencia
 * que ya haya en ese sitio.
 *
 * Los tres valores por omisión que el servidor aplica —cuánta, de quién y dónde—
 * no se replican aquí: la pantalla ofrece cambiar la cantidad y el sitio, y lo
 * que no se diga lo decide el servidor. Replicar esa lógica daría dos versiones
 * de la misma regla y la de aquí sería la que se quedaría vieja.
 */
function PurchasePanel({
  id,
  onChanged,
  onFailure,
}: {
  id: string
  onChanged: () => void
  onFailure: (message: string) => void
}) {
  const { accessToken } = useAuthenticatedSession()
  const queryClient = useQueryClient()
  const [amounts, setAmounts] = useState<Record<string, string>>({})
  const [locationId, setLocationId] = useState('')

  const detail = useQuery({
    queryKey: ['purchase', id],
    queryFn: () => api.getPurchase(id, accessToken),
  })

  /**
   * La ficha **se invalida a sí misma**, además de avisar a quien la envuelve.
   *
   * Es la clave que este componente posee y nadie más conoce, así que dejarla
   * fuera deja la ficha abierta enseñando el formulario de recibir una compra que
   * ya se recibió: la lista de arriba se refresca y esto no, que es de los
   * defectos que peor se ven porque cada mitad de la pantalla dice una cosa.
   */
  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['purchase', id] })
    onChanged()
  }

  const locations = useQuery({
    queryKey: ['locations'],
    queryFn: () => api.listLocations(accessToken),
    staleTime: 5 * 60_000,
  })

  const receive = useMutation({
    mutationFn: () =>
      api.receivePurchase(
        id,
        {
          lines: (detail.data?.lines ?? [])
            .filter((line) => line.status === 'IN_PURCHASE')
            .map((line) => ({
              itemId: line.id,
              ...(amounts[line.id] ? { quantity: Number(amounts[line.id]) } : {}),
              ...(locationId ? { locationId } : {}),
            })),
        },
        accessToken,
      ),
    onSuccess: refresh,
    onError: (error) => onFailure(humanMessage(error)),
  })

  const cancel = useMutation({
    mutationFn: () => api.cancelPurchase(id, accessToken),
    onSuccess: refresh,
    onError: (error) => onFailure(humanMessage(error)),
  })

  if (detail.isPending) return <Spinner label="Cargando la compra…" />
  if (!detail.data) return null

  const isOpen = detail.data.purchase.status === 'OPEN'

  return (
    <div className="border-t border-border-subtle p-4">
      <ul className="flex flex-col gap-2">
        {detail.data.lines.map((line) => (
          <li key={line.id} className="flex flex-wrap items-end justify-between gap-3">
            <span className="flex flex-wrap items-center gap-2">
              <span className="text-body text-ink">{line.name}</span>
              {line.status === 'BOUGHT' && (
                <StatusBadge tone={line.receivedAssetId ? 'success' : 'neutral'}>
                  {line.receivedAssetId ? 'En el inventario' : 'Comprado'}
                </StatusBadge>
              )}
            </span>

            {isOpen && line.articleId && (
              <Field
                label={`Cuánta ${line.name}`}
                type="number"
                min="0"
                step="any"
                value={amounts[line.id] ?? (line.quantity !== null ? String(line.quantity) : '')}
                onChange={(event) =>
                  setAmounts((current) => ({ ...current, [line.id]: event.target.value }))
                }
              />
            )}

            {/* Una línea sin artículo se compra y ahí acaba. Se dice aquí en vez
                de dejar que se descubra al ver que no aparece en el inventario. */}
            {isOpen && !line.articleId && (
              <span className="text-caption text-ink-subtle">No está en el catálogo: no entra en el inventario</span>
            )}
          </li>
        ))}
      </ul>

      {isOpen && (
        <div className="mt-4 flex flex-wrap items-end gap-3">
          <SelectField
            label="Dónde lo guardas"
            value={locationId}
            onChange={(event) => setLocationId(event.target.value)}
            hint="Opcional. Sin decirlo, cada cosa va donde ya estuviera guardada."
          >
            <option value="">Donde ya estuviera</option>
            {(locations.data?.items ?? []).map((location) => (
              <option key={location.id} value={location.id}>
                {location.name}
              </option>
            ))}
          </SelectField>

          <Button
            variant="primary"
            onClick={() => receive.mutate()}
            busy={receive.isPending}
            busyLabel="Guardando…"
          >
            Ya está en casa
          </Button>

          <Button variant="secondary" onClick={() => cancel.mutate()} busy={cancel.isPending} busyLabel="Anulando…">
            Anular la compra
          </Button>
        </div>
      )}
    </div>
  )
}
