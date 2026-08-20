import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Wrench } from 'lucide-react'
import { useState, type FormEvent } from 'react'

import {
  INTERVENTION_KIND_LABELS,
  SERVICE_CATEGORY_LABELS,
  api,
  dayInMonths,
  humanMessage,
  type MaintenanceIntervention,
  type MaintenanceMachine,
  type MaintenancePlan,
  type MaintenanceSupplier,
  type ServiceCategory,
} from '../api/client'
import { useAuthenticatedSession } from '../auth/SessionProvider'
import { useHouseholdToday } from './household'
import {
  Button,
  Combobox,
  EmptyState,
  Field,
  FieldAlignedSlot,
  Notice,
  PageHeading,
  SelectField,
  Spinner,
  StatusBadge,
} from '../ui/primitives'

/**
 * Mantenimiento: **qué hay que revisar, cada cuánto y qué se hizo la última vez**.
 *
 * Su ficha se escribió antes que esta pantalla y está en
 * `docs/backend/modules/maintenance.md`, incluidas las dos fronteras que esta
 * pantalla respeta sin decirlo:
 *
 * **Contra el planificador de tareas**, que no existe todavía. Aquí no se asigna
 * nada a nadie: no hay «quién lo hace», no hay «qué día le toca a quién» y no hay
 * un calendario con una fila por ocurrencia. Lo que hay es **cuándo toca** —una
 * fecha por plan— y **qué se hizo**. Si algún día aparece un selector de persona
 * en esta pantalla, la frontera se habrá roto por aquí.
 *
 * **Contra el core.** El nombre de una máquina y su documentación son del
 * inventario; esta pantalla los lee y no los edita. Cambiar el nombre de la
 * caldera se hace en el inventario y se ve aquí sin que nadie sincronice nada.
 *
 * Lo que **no** hay, y no es un olvido: no se pregunta si el módulo está
 * encendido. De eso se encarga `ModuleScreen`, que la envuelve.
 */

export function MaintenancePage() {
  const [tab, setTab] = useState<'due' | 'machines' | 'history'>('due')

  return (
    <>
      <PageHeading title="Mantenimiento" icon={Wrench} />

      <p className="max-w-prose text-body text-ink-muted">
        Lo que hay que revisar de las cosas de casa: la caldera cada año, el filtro
        cada tres meses, la ITV cada dos. Apunta el plan una vez y, cuando esté
        hecho, regístralo: la próxima fecha se calcula sola y el aviso vuelve a
        armarse.
      </p>

      {/* Tres pestañas con `role="tab"` y no tres enlaces: son tres vistas del
          mismo recurso y no tres páginas, así que cambiar de una a otra no debería
          cambiar la URL ni recargar la navegación. */}
      <div role="tablist" aria-label="Qué ver del mantenimiento" className="mt-6 flex flex-wrap gap-2">
        <TabButton current={tab} value="due" onSelect={setTab}>
          Qué toca
        </TabButton>
        <TabButton current={tab} value="machines" onSelect={setTab}>
          Las máquinas
        </TabButton>
        <TabButton current={tab} value="history" onSelect={setTab}>
          El histórico
        </TabButton>
      </div>

      <div id={`panel-${tab}`} role="tabpanel" aria-labelledby={`tab-${tab}`} className="mt-6">
        {tab === 'due' && <PlansPanel />}
        {tab === 'machines' && <MachinesPanel />}
        {tab === 'history' && <HistoryPanel />}
      </div>
    </>
  )
}

type Tab = 'due' | 'machines' | 'history'

function TabButton({
  current,
  value,
  onSelect,
  children,
}: {
  current: string
  value: Tab
  onSelect: (value: Tab) => void
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
        // primario y el que `check-contrast.py` mide. Un token inventado no falla
        // al construir y deja un contraste que solo encuentra axe: le pasó a la
        // pantalla de Compras en el Hito 4.
        selected ? 'bg-accent text-ink-inverse' : 'border border-border bg-surface-raised text-ink',
      ].join(' ')}
    >
      {children}
    </button>
  )
}

// ---------------------------------------------------------------------------
// Qué toca: los planes
// ---------------------------------------------------------------------------

function PlansPanel() {
  const { accessToken } = useAuthenticatedSession()
  const queryClient = useQueryClient()
  const [failure, setFailure] = useState<string | null>(null)

  const plans = useQuery({
    queryKey: ['maintenance-plans'],
    queryFn: () => api.listMaintenancePlans(accessToken),
  })

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['maintenance-plans'] })
    void queryClient.invalidateQueries({ queryKey: ['maintenance-machines'] })
    void queryClient.invalidateQueries({ queryKey: ['maintenance-history'] })
  }

  return (
    <>
      {failure && <Notice tone="danger">{failure}</Notice>}

      <NewPlanForm onDone={invalidate} onFailure={setFailure} />

      <div className="mt-6">
        {plans.isPending && <Spinner label="Cargando los planes de mantenimiento…" />}
        {plans.isError && <Notice tone="danger">{humanMessage(plans.error)}</Notice>}

        {plans.data && plans.data.items.length === 0 && (
          <EmptyState title="Ningún plan todavía">
            Al encender el módulo, tus cosas duraderas entran en «Las máquinas»
            —pero sin ningún plan: una caldera pide revisión anual y una silla no
            pide nada, así que eso lo decides tú.
          </EmptyState>
        )}

        {plans.data && plans.data.items.length > 0 && (
          <ul className="flex flex-col gap-2">
            {plans.data.items.map((plan) => (
              <PlanRow key={plan.id} plan={plan} onDone={invalidate} onFailure={setFailure} />
            ))}
          </ul>
        )}
      </div>
    </>
  )
}

/**
 * Un plan, con lo único que hace falta ver de un vistazo: **cuándo toca**.
 *
 * No lleva responsable ni día asignado, y eso es la frontera contra el
 * planificador de tareas hecha pantalla: aquí se dice que la caldera se revisa
 * cada doce meses y que la próxima es el 3 de marzo, no que le toque a nadie el
 * jueves.
 */
function PlanRow({
  plan,
  onDone,
  onFailure,
}: {
  plan: MaintenancePlan
  onDone: () => void
  onFailure: (message: string) => void
}) {
  const { accessToken } = useAuthenticatedSession()
  const today = useHouseholdToday()
  const [registering, setRegistering] = useState(false)

  const detail = useQuery({
    queryKey: ['maintenance-plan', plan.id],
    queryFn: () => api.getMaintenancePlan(plan.id, accessToken),
    staleTime: 60_000,
  })

  const cancel = useMutation({
    mutationFn: () => api.cancelMaintenancePlan(plan.id, accessToken),
    onSuccess: onDone,
    onError: (error) => onFailure(humanMessage(error)),
  })

  const status = dueStatus(plan.nextDueOn, today)

  return (
    <li className="rounded-lg border border-border-subtle bg-surface-raised p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="max-w-prose">
          <div className="flex flex-wrap items-center gap-2">
            <p className="text-body font-medium text-ink">{plan.name}</p>
            {/* Estado con etiqueta y no solo con color, que es la regla 4 de la
                dirección visual. */}
            <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
          </div>
          <p className="mt-1 text-body-sm text-ink-muted">
            {detail.data?.machineName ?? 'Cargando…'} · cada {plan.intervalMonths}{' '}
            {plan.intervalMonths === 1 ? 'mes' : 'meses'} · toca el {formatDate(plan.nextDueOn)}
          </p>
          {plan.lastPerformedOn && (
            <p className="mt-1 text-caption text-ink-muted">
              La última vez fue el {formatDate(plan.lastPerformedOn)}.
            </p>
          )}
          {/* El servicio técnico viene **resuelto al leer** y no copiado, así que
              dice el nombre de hoy. Un contacto retirado sigue saliendo aquí:
              un plan que apunte a quien ya no se llama tiene que poder decir a
              quién apuntaba. */}
          {detail.data?.supplier && (
            <p className="mt-1 text-caption text-ink-muted">
              A quien se llama: {detail.data.supplier.name}
            </p>
          )}
          {plan.notes && <p className="mt-1 text-caption text-ink-muted">{plan.notes}</p>}
        </div>

        <div className="flex flex-wrap gap-2">
          <Button variant="primary" onClick={() => setRegistering((open) => !open)}>
            {registering ? 'Dejarlo' : 'Ya está hecho'}
          </Button>
          <Button
            variant="secondary"
            onClick={() => cancel.mutate()}
            busy={cancel.isPending}
            busyLabel="Cancelando…"
          >
            Dejar de vigilarlo
          </Button>
        </div>
      </div>

      {registering && (
        <InterventionForm
          assetId={plan.assetId}
          planId={plan.id}
          onDone={() => {
            setRegistering(false)
            onDone()
          }}
          onFailure={onFailure}
        />
      )}
    </li>
  )
}

/**
 * Un plan nuevo.
 *
 * La máquina se elige con un `Combobox` y no con un desplegable: una casa tiene
 * decenas de cosas duraderas, y `GET /maintenance/machines` lleva un parámetro
 * `q` que existe para esto.
 */
function NewPlanForm({ onDone, onFailure }: { onDone: () => void; onFailure: (message: string) => void }) {
  const { accessToken } = useAuthenticatedSession()
  const [text, setText] = useState('')
  const [assetId, setAssetId] = useState<string | null>(null)
  const [name, setName] = useState('')
  const [intervalMonths, setIntervalMonths] = useState('12')
  const [supplierId, setSupplierId] = useState('')

  // Nulo es «no lo ha tocado nadie», y entonces manda el año que viene contado
  // desde el día del hogar. Arrancar el estado con un valor exigiría tenerlo en
  // el primer render, que es justo cuando el hogar puede no haber llegado.
  const today = useHouseholdToday()
  const [chosenDueOn, setChosenDueOn] = useState<string | null>(null)
  const nextDueOn = chosenDueOn ?? (today ? dayInMonths(today, 12) : '')

  const machines = useQuery({
    queryKey: ['maintenance-machine-suggestions', text],
    queryFn: () => api.listMaintenanceMachines(accessToken, text.trim() || undefined),
    staleTime: 30_000,
  })

  const suppliers = useSuppliers()

  const create = useMutation({
    mutationFn: () =>
      api.createMaintenancePlan(
        {
          assetId: assetId!,
          name: name.trim(),
          intervalMonths: Number(intervalMonths),
          nextDueOn,
          ...(supplierId ? { supplierId } : {}),
        },
        accessToken,
      ),
    onSuccess: () => {
      setText('')
      setAssetId(null)
      setName('')
      setSupplierId('')
      onDone()
    },
    onError: (error) => onFailure(humanMessage(error)),
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    if (assetId && name.trim() && nextDueOn) create.mutate()
  }

  return (
    // `items-start` y no `items-end`: alineando por el pie, los campos con
    // pista empujan su input hacia arriba y la fila baila. Con etiquetas de una
    // línea, alinear por la cabeza deja todos los inputs en la misma fila y las
    // pistas cuelgan por debajo sin mover a nadie.
    <form onSubmit={submit} className="mt-6 flex flex-wrap items-start gap-3">
      <div className="min-w-[14rem] flex-1">
        <Combobox
          label="Qué máquina"
          value={text}
          options={(machines.data?.items ?? [])
            .slice(0, 20)
            .map((machine) => ({ id: machine.assetId, label: machine.name }))}
          onQueryChange={(value) => {
            setText(value)
            // Escribir después de haber elegido deshace la elección: si no, el
            // plan se crearía sobre la máquina vieja con el texto nuevo.
            setAssetId(null)
          }}
          onSelect={(option) => {
            setText(option.label)
            setAssetId(option.id)
          }}
          hint="Solo cosas duraderas: un paquete de arroz no se revisa."
          placeholder="Caldera, coche…"
        />
      </div>

      <Field
        label="Qué se revisa"
        value={name}
        onChange={(event) => setName(event.target.value)}
        placeholder="Revisión anual"
      />

      <Field
        label="Cada cuántos meses"
        type="number"
        min="1"
        max="120"
        value={intervalMonths}
        onChange={(event) => setIntervalMonths(event.target.value)}
        hint="En meses, para que el aniversario no se mueva"
      />

      <Field
        label="Cuándo toca la próxima"
        type="date"
        value={nextDueOn}
        onChange={(event) => setChosenDueOn(event.target.value)}
      />

      {/* Solo si hay a quién llamar. Con el módulo de proveedores apagado el
          servidor responde una lista vacía —y no un error—, así que aquí
          simplemente no hay campo: la degradación no la pone esta pantalla. */}
      {suppliers.length > 0 && (
        <SupplierField
          label="A quién se llama"
          suppliers={suppliers}
          value={supplierId}
          onChange={setSupplierId}
        />
      )}

      <FieldAlignedSlot>
        <Button type="submit" variant="primary" busy={create.isPending} busyLabel="Creando…">
          Crear plan
        </Button>
      </FieldAlignedSlot>
    </form>
  )
}

// ---------------------------------------------------------------------------
// Las máquinas
// ---------------------------------------------------------------------------

function MachinesPanel() {
  const { accessToken } = useAuthenticatedSession()

  const machines = useQuery({
    queryKey: ['maintenance-machines'],
    queryFn: () => api.listMaintenanceMachines(accessToken),
  })

  return (
    <>
      <p className="max-w-prose text-body-sm text-ink-muted">
        Las cosas duraderas que tienes dadas de alta. Entran aquí solas —al
        encender el módulo y al darlas de alta en el inventario— y sin ningún
        plan: el plan lo pones tú, que eres quien sabe si tu caldera es de gas.
      </p>

      <div className="mt-4">
        {machines.isPending && <Spinner label="Cargando las máquinas…" />}
        {machines.isError && <Notice tone="danger">{humanMessage(machines.error)}</Notice>}

        {machines.data && machines.data.items.length === 0 && (
          <EmptyState title="Nada duradero todavía">
            Da de alta algo en el inventario y aparecerá aquí solo.
          </EmptyState>
        )}

        {machines.data && machines.data.items.length > 0 && (
          <ul className="flex flex-col gap-2">
            {machines.data.items.map((machine) => (
              <MachineRow key={machine.assetId} machine={machine} />
            ))}
          </ul>
        )}
      </div>
    </>
  )
}

function MachineRow({ machine }: { machine: MaintenanceMachine }) {
  return (
    <li className="rounded-lg border border-border-subtle bg-surface-raised p-4">
      <div className="flex flex-wrap items-center gap-2">
        <p className="text-body font-medium text-ink">{machine.name}</p>
        <StatusBadge tone={machine.planCount > 0 ? 'success' : 'neutral'}>
          {machine.planCount === 0
            ? 'Sin planes'
            : `${machine.planCount} ${machine.planCount === 1 ? 'plan' : 'planes'}`}
        </StatusBadge>
        {machine.manualDocumentId && <StatusBadge tone="info">Con manual</StatusBadge>}
      </div>
      <p className="mt-1 text-body-sm text-ink-muted">
        {machine.nextDueOn
          ? `Lo próximo toca el ${formatDate(machine.nextDueOn)}.`
          : 'Nada previsto: todavía no tiene ningún plan.'}
      </p>
      {machine.notes && <p className="mt-1 text-caption text-ink-muted">{machine.notes}</p>}
    </li>
  )
}

// ---------------------------------------------------------------------------
// El histórico
// ---------------------------------------------------------------------------

function HistoryPanel() {
  const { accessToken } = useAuthenticatedSession()
  const queryClient = useQueryClient()
  const [failure, setFailure] = useState<string | null>(null)

  const history = useQuery({
    queryKey: ['maintenance-history'],
    queryFn: () => api.listMaintenanceInterventions(accessToken),
  })

  return (
    <>
      {failure && <Notice tone="danger">{failure}</Notice>}

      <p className="max-w-prose text-body-sm text-ink-muted">
        Todo lo que se ha hecho, se hubiera planeado o no. Una avería se apunta
        aquí aunque no cuelgue de ningún plan, y lo que se apunta no se cambia:
        esto es un cuaderno.
      </p>

      <CorrectiveForm
        onDone={() => {
          void queryClient.invalidateQueries({ queryKey: ['maintenance-history'] })
          void queryClient.invalidateQueries({ queryKey: ['maintenance-machines'] })
        }}
        onFailure={setFailure}
      />

      <div className="mt-6">
        {history.isPending && <Spinner label="Cargando el histórico…" />}
        {history.isError && <Notice tone="danger">{humanMessage(history.error)}</Notice>}

        {history.data && history.data.items.length === 0 && (
          <EmptyState title="Todavía no se ha hecho nada">
            Cuando registres una revisión o una reparación, quedará aquí.
          </EmptyState>
        )}

        {history.data && history.data.items.length > 0 && (
          <ul className="flex flex-col gap-2">
            {history.data.items.map((intervention) => (
              <InterventionRow key={intervention.id} intervention={intervention} />
            ))}
          </ul>
        )}
      </div>
    </>
  )
}

function InterventionRow({ intervention }: { intervention: MaintenanceIntervention }) {
  return (
    <li className="rounded-lg border border-border-subtle bg-surface-raised p-4">
      <div className="flex flex-wrap items-center gap-2">
        <p className="text-body font-medium text-ink">{intervention.summary}</p>
        <StatusBadge tone={intervention.kind === 'CORRECTIVE' ? 'warning' : 'success'}>
          {INTERVENTION_KIND_LABELS[intervention.kind]}
        </StatusBadge>
      </div>
      <p className="mt-1 text-body-sm text-ink-muted">
        {formatDate(intervention.performedOn)}
        {/* El nombre de aquel día, copiado al registrarla: una intervención es
            historia y siguió siendo cierta aunque el contacto se retire. */}
        {intervention.supplier ? ` · lo hizo ${intervention.supplier}` : ''}
      </p>
      {intervention.notes && <p className="mt-1 text-caption text-ink-muted">{intervention.notes}</p>}
    </li>
  )
}

/** Una avería: no cuelga de ningún plan y no avanza ninguna fecha. */
function CorrectiveForm({ onDone, onFailure }: { onDone: () => void; onFailure: (message: string) => void }) {
  const { accessToken } = useAuthenticatedSession()
  const [text, setText] = useState('')
  const [assetId, setAssetId] = useState<string | null>(null)

  const machines = useQuery({
    queryKey: ['maintenance-machine-suggestions', text],
    queryFn: () => api.listMaintenanceMachines(accessToken, text.trim() || undefined),
    staleTime: 30_000,
  })

  return (
    <div className="mt-4">
      <div className="max-w-md">
        <Combobox
          label="Qué se ha roto"
          value={text}
          options={(machines.data?.items ?? [])
            .slice(0, 20)
            .map((machine) => ({ id: machine.assetId, label: machine.name }))}
          onQueryChange={(value) => {
            setText(value)
            setAssetId(null)
          }}
          onSelect={(option) => {
            setText(option.label)
            setAssetId(option.id)
          }}
          hint="Elige la máquina y cuenta lo que se hizo."
          placeholder="Caldera, lavadora…"
        />
      </div>

      {assetId && (
        <InterventionForm
          assetId={assetId}
          kind="CORRECTIVE"
          onDone={() => {
            setText('')
            setAssetId(null)
            onDone()
          }}
          onFailure={onFailure}
        />
      )}
    </div>
  )
}

// ---------------------------------------------------------------------------
// Registrar lo que se hizo
// ---------------------------------------------------------------------------

/**
 * **Lo que rearma el ciclo.** Si cumple un plan, el servidor le avanza la próxima
 * fecha y con ello vuelve a armar su aviso — sin que esta pantalla tenga que
 * pedirlo ni saber cómo se hace.
 */
function InterventionForm({
  assetId,
  planId,
  kind = 'PREVENTIVE',
  onDone,
  onFailure,
}: {
  assetId: string
  planId?: string
  kind?: 'PREVENTIVE' | 'CORRECTIVE'
  onDone: () => void
  onFailure: (message: string) => void
}) {
  const { accessToken } = useAuthenticatedSession()
  const today = useHouseholdToday()
  const [chosenDay, setChosenDay] = useState<string | null>(null)
  const performedOn = chosenDay ?? today ?? ''
  const [summary, setSummary] = useState('')
  const [supplierId, setSupplierId] = useState('')
  const suppliers = useSuppliers()

  const register = useMutation({
    mutationFn: () =>
      api.registerMaintenanceIntervention(
        {
          assetId,
          ...(planId ? { planId } : {}),
          kind,
          performedOn,
          summary: summary.trim(),
          ...(supplierId ? { supplierId } : {}),
        },
        accessToken,
      ),
    onSuccess: () => {
      setSummary('')
      setSupplierId('')
      onDone()
    },
    onError: (error) => onFailure(humanMessage(error)),
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    if (summary.trim() && performedOn) register.mutate()
  }

  return (
    // El mismo `items-start` que el formulario de planes, por el mismo motivo.
    <form onSubmit={submit} className="mt-4 flex flex-wrap items-start gap-3 border-t border-border-subtle pt-4">
      <Field
        label="Cuándo se hizo"
        type="date"
        max={today ?? undefined}
        value={performedOn}
        onChange={(event) => setChosenDay(event.target.value)}
        hint="No puede ser del futuro"
      />

      <div className="min-w-[14rem] flex-1">
        <Field
          label="Qué se hizo"
          value={summary}
          onChange={(event) => setSummary(event.target.value)}
          placeholder="Revisada y limpiada"
        />
      </div>

      {suppliers.length > 0 && (
        <SupplierField
          label="Quién vino"
          suppliers={suppliers}
          value={supplierId}
          onChange={setSupplierId}
        />
      )}

      <FieldAlignedSlot>
        <Button type="submit" variant="primary" busy={register.isPending} busyLabel="Apuntando…">
          Apuntarlo
        </Button>
      </FieldAlignedSlot>
    </form>
  )
}

// ---------------------------------------------------------------------------
// A quién se llama
// ---------------------------------------------------------------------------

/**
 * El dato maestro de proveedores, leído por el prefijo de **este** módulo.
 *
 * Con el módulo de proveedores apagado esto responde `200` con la lista vacía y
 * no un `403`, así que aquí no hay ninguna rama para ello: la degradación la pone
 * el servidor. Quien la usa solo mira si hay opciones.
 */
function useSuppliers(): MaintenanceSupplier[] {
  const { accessToken } = useAuthenticatedSession()

  const suppliers = useQuery({
    queryKey: ['maintenance-suppliers'],
    queryFn: () => api.listMaintenanceSuppliers(accessToken),
    staleTime: 5 * 60_000,
  })

  return suppliers.data ?? []
}

/**
 * El selector de servicio técnico, **agrupado por categoría**.
 *
 * Aquí es donde se ve la decisión de este módulo sobre el puerto de dato maestro:
 * el servidor entrega el **identificador** de la categoría en `detail` y no filtra
 * por ella —de las catorce, casi todas son servicios técnicos, así que recortar la
 * lista escondería justo al contacto que hace falta—. Lo que hacía falta era
 * distinguirlos de un vistazo, y para eso basta con agrupar aquí y traducir el
 * identificador con el mapa que esta pantalla ya tiene.
 */
function SupplierField({
  label,
  suppliers,
  value,
  onChange,
}: {
  label: string
  suppliers: MaintenanceSupplier[]
  value: string
  onChange: (value: string) => void
}) {
  const groups = new Map<string, MaintenanceSupplier[]>()
  for (const supplier of suppliers) {
    const key = supplier.detail ?? 'OTHER'
    groups.set(key, [...(groups.get(key) ?? []), supplier])
  }

  return (
    <SelectField label={label} value={value} onChange={(event) => onChange(event.target.value)}>
      <option value="">Nadie de fuera</option>
      {[...groups.entries()].map(([category, entries]) => (
        <optgroup
          key={category}
          label={SERVICE_CATEGORY_LABELS[category as ServiceCategory] ?? category}
        >
          {entries.map((supplier) => (
            <option key={supplier.id} value={supplier.id}>
              {supplier.name}
            </option>
          ))}
        </optgroup>
      ))}
    </SelectField>
  )
}

// ---------------------------------------------------------------------------
// Fechas
// ---------------------------------------------------------------------------

/**
 * El «hoy» de esta pantalla **no está aquí**, y esa es la corrección de este
 * hito. Era `new Date().toISOString().slice(0, 10)` —el día de Greenwich— y
 * salía por dos sitios a la vez: como valor inicial del campo de fecha y como su
 * `max`. A las 00:30 de Madrid eso no daba un error, daba algo peor: el campo
 * relleno con **ayer** y el selector negándose a ofrecer hoy.
 *
 * Ahora sale de `useHouseholdToday`, que lo resuelve con `households.time_zone`
 * —el mismo dato con el que el servidor decide si una intervención es del
 * futuro—, y sumar meses es `dayInMonths`, que hace aritmética de calendario en
 * lugar de mezclar el huso del navegador con el formato en UTC.
 */

function formatDate(iso: string): string {
  return new Date(`${iso}T00:00:00`).toLocaleDateString('es-ES', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  })
}

/**
 * En qué punto está una fecha prevista, **con etiqueta y no solo con color**.
 *
 * Los quince días son la antelación por omisión del módulo; aquí solo sirven para
 * pintar, y quien decide de verdad si se avisa es el servidor con la antelación de
 * cada plan. Que las dos cifras coincidan es una comodidad y no un contrato: esta
 * pantalla no calcula ningún aviso.
 *
 * **Pero se cuenta desde el día del hogar**, que es de donde sale [today]. Antes
 * restaba un día de calendario menos `Date.now()`, o sea un día contra un
 * instante: en la franja entre dos medianoches eso pinta «Se ha pasado» sobre un
 * plan que toca hoy. Sin el día del hogar todavía no hay nada que decir, y decir
 * «Al día» por omisión sería justo la mitad tranquilizadora de la duda.
 */
function dueStatus(
  nextDueOn: string,
  today: string | null,
): { tone: 'danger' | 'warning' | 'neutral'; label: string } {
  if (!today) return { tone: 'neutral', label: '—' }

  const days = Math.round((Date.parse(`${nextDueOn}T00:00:00Z`) - Date.parse(`${today}T00:00:00Z`)) / 86_400_000)

  if (days < 0) return { tone: 'danger', label: 'Se ha pasado' }
  if (days <= 15) return { tone: 'warning', label: 'Toca pronto' }
  return { tone: 'neutral', label: 'Al día' }
}
