import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { MapPin } from 'lucide-react'
import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react'

import {
  CAPACITY_DEFAULT_UNITS,
  CAPACITY_TYPE_LABELS,
  LOCATION_TYPE_LABELS,
  api,
  humanMessage,
  type Capacity,
  type CapacityType,
  type Location,
  type LocationType,
} from '../api/client'
import { useAuthenticatedSession } from '../auth/SessionProvider'
import { Button, EmptyState, Field, Notice, PageHeading, SelectField, Spinner } from '../ui/primitives'

/**
 * El árbol de ubicaciones del hogar.
 *
 * Se pide **entero de una vez** y se compone en el cliente, en lugar de cargar
 * cada nivel bajo demanda. Es una decisión de tamaño: las ubicaciones de un
 * hogar son decenas, no miles, y pedirlas de golpe evita que abrir una rama sea
 * una espera. Cuando un hogar tenga cientos, `GET /locations/{id}/children` ya
 * está en el contrato y en el cliente para cambiar a carga por niveles.
 *
 * De un hogar pueden colgar **varias viviendas**: cada una es una raíz. Por eso
 * el árbol tiene varias y no una.
 */

const LOCATION_TYPES: LocationType[] = ['HOUSE', 'FLOOR', 'ROOM', 'FURNITURE', 'SHELF', 'OTHER']
const CAPACITY_TYPES: CapacityType[] = ['UNITS', 'WEIGHT', 'VOLUME']

interface TreeNode {
  location: Location
  children: TreeNode[]
}

/** Los tres campos de la capacidad tal y como se teclean: en texto y por separado. */
interface CapacityDraft {
  type: '' | CapacityType
  max: string
  unit: string
}

interface LocationDraft {
  name: string
  type: LocationType
  parentLocationId: string
  capacity: CapacityDraft
}

const EMPTY_CAPACITY: CapacityDraft = { type: '', max: '', unit: '' }

function emptyDraft(): LocationDraft {
  return { name: '', type: 'ROOM', parentLocationId: '', capacity: { ...EMPTY_CAPACITY } }
}

function draftOf(location: Location): LocationDraft {
  return {
    name: location.name,
    type: location.type,
    parentLocationId: location.parentLocationId ?? '',
    capacity: location.capacity
      ? { type: location.capacity.type, max: String(location.capacity.max), unit: location.capacity.unit }
      : { ...EMPTY_CAPACITY },
  }
}

/**
 * La capacidad, o `null` cuando no se declara ninguna.
 *
 * `null` y no ausente: en un `PATCH` son cosas distintas —ausente conserva, nulo
 * borra— y esta pantalla siempre manda lo que se ve en el formulario, así que
 * quitar la capacidad tiene que poder decirse.
 */
function capacityOf(draft: CapacityDraft): Capacity | null {
  if (!draft.type || !draft.max) return null
  return {
    type: draft.type,
    max: Number(draft.max),
    unit: draft.unit.trim() || CAPACITY_DEFAULT_UNITS[draft.type],
  }
}

/**
 * Los descendientes de una ubicación, que **no pueden ser su nuevo padre**.
 *
 * El servidor rechaza el ciclo con `LOCATION_CYCLE`, pero esa negativa es la red
 * de seguridad y no el camino normal: lo que va a fallar no se ofrece.
 */
function descendantsOf(locations: Location[], id: string): Set<string> {
  const childrenOf = new Map<string, string[]>()
  for (const location of locations) {
    if (!location.parentLocationId) continue
    const siblings = childrenOf.get(location.parentLocationId) ?? []
    siblings.push(location.id)
    childrenOf.set(location.parentLocationId, siblings)
  }

  const found = new Set<string>()
  const pending = [id]
  while (pending.length > 0) {
    for (const child of childrenOf.get(pending.pop()!) ?? []) {
      if (found.has(child)) continue
      found.add(child)
      pending.push(child)
    }
  }
  return found
}

/** Compone el árbol a partir de la lista plana, respetando el orden que llega. */
function buildTree(locations: Location[]): TreeNode[] {
  const nodes = new Map<string, TreeNode>()
  for (const location of locations) nodes.set(location.id, { location, children: [] })

  const roots: TreeNode[] = []
  for (const node of nodes.values()) {
    const parentId = node.location.parentLocationId
    const parent = parentId ? nodes.get(parentId) : undefined
    // Sin padre visible se trata como raíz. No debería pasar --la política deja
    // ver el hogar entero-- pero perder una rama en silencio sería peor que
    // enseñarla suelta.
    if (parent) parent.children.push(node)
    else roots.push(node)
  }
  return roots
}

export function LocationsPage() {
  const { accessToken } = useAuthenticatedSession()
  const queryClient = useQueryClient()
  const [failure, setFailure] = useState<string | null>(null)
  const [draft, setDraft] = useState<LocationDraft>(emptyDraft)
  const [editingId, setEditingId] = useState<string | null>(null)

  const locations = useQuery({
    queryKey: ['locations'],
    queryFn: () => api.listLocations(accessToken),
  })

  const items = locations.data?.items ?? []
  const tree = useMemo(() => buildTree(items), [items])
  const editing = items.find((location) => location.id === editingId) ?? null

  const create = useMutation({
    mutationFn: () => {
      const capacity = capacityOf(draft.capacity)
      return api.createLocation(
        {
          name: draft.name,
          type: draft.type,
          ...(draft.parentLocationId ? { parentLocationId: draft.parentLocationId } : {}),
          ...(capacity ? { capacity } : {}),
        },
        accessToken,
      )
    },
    onSuccess: () => {
      setDraft(emptyDraft())
      setFailure(null)
      void queryClient.invalidateQueries({ queryKey: ['locations'] })
    },
    onError: (error) => setFailure(humanMessage(error)),
  })

  const remove = useMutation({
    mutationFn: (id: string) => api.deleteLocation(id, accessToken),
    onSuccess: () => {
      setFailure(null)
      void queryClient.invalidateQueries({ queryKey: ['locations'] })
    },
    onError: (error) => setFailure(humanMessage(error)),
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    create.mutate()
  }

  return (
    <>
      <PageHeading title="Ubicaciones" icon={MapPin} />

      <div className="flex flex-col gap-6">
        <form onSubmit={submit} className="flex max-w-form flex-col gap-3">
          <Field
            label="Nombre"
            value={draft.name}
            onChange={(event) => setDraft({ ...draft, name: event.target.value })}
            required
            hint="Único entre las que cuelgan del mismo sitio, no en todo el hogar."
          />

          <SelectField
            label="Tipo"
            value={draft.type}
            onChange={(event) => setDraft({ ...draft, type: event.target.value as LocationType })}
          >
            {LOCATION_TYPES.map((type) => (
              <option key={type} value={type}>
                {LOCATION_TYPE_LABELS[type]}
              </option>
            ))}
          </SelectField>

          <SelectField
            label="Dentro de"
            value={draft.parentLocationId}
            onChange={(event) => setDraft({ ...draft, parentLocationId: event.target.value })}
          >
            <option value="">Nada: es una vivienda</option>
            {items.map((location) => (
              <option key={location.id} value={location.id}>
                {location.name}
              </option>
            ))}
          </SelectField>

          <CapacityFields
            value={draft.capacity}
            onChange={(capacity) => setDraft({ ...draft, capacity })}
          />

          <Button type="submit" variant="primary" busy={create.isPending} busyLabel="Creando…">
            Crear ubicación
          </Button>
        </form>

        {editing && (
          <EditLocationForm
            key={editing.id}
            location={editing}
            locations={items}
            accessToken={accessToken}
            onDone={() => {
              setEditingId(null)
              setFailure(null)
              void queryClient.invalidateQueries({ queryKey: ['locations'] })
            }}
            onCancel={() => setEditingId(null)}
          />
        )}

        {failure && <Notice tone="danger">{failure}</Notice>}

        {locations.isPending ? (
          <Spinner label="Cargando ubicaciones" />
        ) : locations.isError ? (
          <Notice tone="danger">{humanMessage(locations.error)}</Notice>
        ) : tree.length === 0 ? (
          <EmptyState title="Todavía no hay ninguna ubicación">
            Empieza por la vivienda y ve colgando de ella plantas, habitaciones y muebles.
          </EmptyState>
        ) : (
          // Un solo `ul` con `role="tree"`: la jerarquía se anuncia con
          // `aria-level`, no con la sangría, que un lector de pantalla no ve.
          <ul role="tree" aria-label="Ubicaciones del hogar" className="flex flex-col gap-1">
            {tree.map((node) => (
              <LocationBranch
                key={node.location.id}
                node={node}
                level={1}
                onEdit={setEditingId}
                onDelete={remove.mutate}
              />
            ))}
          </ul>
        )}
      </div>
    </>
  )
}

function LocationBranch({
  node,
  level,
  onEdit,
  onDelete,
}: {
  node: TreeNode
  level: number
  onEdit: (id: string) => void
  onDelete: (id: string) => void
}) {
  const hasChildren = node.children.length > 0

  return (
    <li role="treeitem" aria-level={level} aria-expanded={hasChildren ? true : undefined}>
      <div
        className="flex min-h-touch flex-wrap items-center justify-between gap-2 rounded-md border border-border-subtle bg-surface-raised px-3 py-2"
        // La sangría se aplica con estilo y no con márgenes anidados para que en
        // 375 px se pueda limitar: con seis niveles, una sangría fija dejaría el
        // nombre en una columna de dos caracteres.
        style={{ marginInlineStart: `${Math.min(level - 1, 4) * 0.75}rem` }}
      >
        <span className="flex flex-wrap items-baseline gap-2">
          <span className="text-body text-ink">{node.location.name}</span>
          <span className="text-caption text-ink-muted">{LOCATION_TYPE_LABELS[node.location.type]}</span>
          {/* Se pinta cualquier capacidad y no solo la de unidades: que el
              sistema no pueda contar kilos no la convierte en un dato que quien
              mira la estantería no quiera ver. */}
          {node.location.capacity && (
            <span className="text-caption text-ink-subtle">
              hasta {node.location.capacity.max} {node.location.capacity.unit}
            </span>
          )}
        </span>

        <span className="flex items-center gap-1">
          <Button variant="ghost" onClick={() => onEdit(node.location.id)}>
            Editar
          </Button>
          <Button variant="ghost" onClick={() => onDelete(node.location.id)}>
            Borrar
          </Button>
        </span>
      </div>

      {hasChildren && (
        <ul role="group" className="mt-1 flex flex-col gap-1">
          {node.children.map((child) => (
            <LocationBranch
              key={child.location.id}
              node={child}
              level={level + 1}
              onEdit={onEdit}
              onDelete={onDelete}
            />
          ))}
        </ul>
      )}
    </li>
  )
}

/**
 * La capacidad declarada, en tres campos que van juntos.
 *
 * Los dos de medida **aparecen al elegir en qué se mide** y no antes: sin tipo no
 * significan nada, y un formulario que pide «máximo» sin decir de qué se responde
 * mal. Al elegir se propone la unidad habitual de ese tipo, que se puede cambiar
 * —hay estanterías que se miden en cajas y otras en botellas.
 */
function CapacityFields({
  value,
  onChange,
}: {
  value: CapacityDraft
  onChange: (capacity: CapacityDraft) => void
}) {
  return (
    <>
      <SelectField
        label="Capacidad (opcional)"
        value={value.type}
        hint="Superarla avisa, no impide guardar: solo el recuento de unidades es fiable."
        onChange={(event) => {
          const type = event.target.value as '' | CapacityType
          onChange(
            type
              ? { type, max: value.max, unit: value.unit || CAPACITY_DEFAULT_UNITS[type] }
              : { ...EMPTY_CAPACITY },
          )
        }}
      >
        <option value="">Sin declarar</option>
        {CAPACITY_TYPES.map((type) => (
          <option key={type} value={type}>
            {CAPACITY_TYPE_LABELS[type]}
          </option>
        ))}
      </SelectField>

      {value.type && (
        // Los envoltorios llevan el `flex-1` y no los campos: el `className` de
        // `Field` viaja al `<input>`, así que ponerlo ahí estiraría la caja del
        // texto y no la columna. En 375 px envuelven y cada uno ocupa su línea.
        <div className="flex flex-wrap gap-3">
          <div className="min-w-32 flex-1">
            <Field
              label="Máximo"
              type="number"
              min="0"
              step="any"
              className="w-full"
              value={value.max}
              onChange={(event) => onChange({ ...value, max: event.target.value })}
              required
            />
          </div>
          <div className="min-w-32 flex-1">
            <Field
              label="En qué se mide"
              className="w-full"
              value={value.unit}
              onChange={(event) => onChange({ ...value, unit: event.target.value })}
              required
            />
          </div>
        </div>
      )}
    </>
  )
}

/**
 * Corregir una ubicación: el nombre, el tipo, dónde cuelga y su capacidad.
 *
 * Va **fuera del árbol** y no dentro de la fila. Meter un formulario dentro de un
 * `treeitem` mezcla dos cosas que se recorren distinto —el árbol con las flechas,
 * el formulario con el tabulador— y a 375 px lo dejaría en una columna sangrada
 * de cuatro niveles. Al abrirse se lleva el foco, porque si no quien lo abrió con
 * el teclado se queda con el foco en un botón y el formulario aparece en otro
 * sitio de la página.
 *
 * Manda **los cuatro campos siempre**, incluidos los que no se tocaron: en un
 * `PATCH` ausente conserva y nulo borra, así que enviar solo lo cambiado haría
 * imposible quitar la capacidad o sacar una ubicación de su padre.
 */
function EditLocationForm({
  location,
  locations,
  accessToken,
  onDone,
  onCancel,
}: {
  location: Location
  locations: Location[]
  accessToken: string
  onDone: () => void
  onCancel: () => void
}) {
  const [draft, setDraft] = useState<LocationDraft>(() => draftOf(location))
  const [failure, setFailure] = useState<string | null>(null)
  const heading = useRef<HTMLHeadingElement>(null)

  useEffect(() => heading.current?.focus(), [])

  // Ni ella misma ni lo que cuelga de ella: eso sería el ciclo que el servidor
  // rechaza con `LOCATION_CYCLE`.
  const forbidden = useMemo(() => {
    const set = descendantsOf(locations, location.id)
    set.add(location.id)
    return set
  }, [locations, location.id])

  const save = useMutation({
    mutationFn: () =>
      api.updateLocation(
        location.id,
        {
          name: draft.name,
          type: draft.type,
          parentLocationId: draft.parentLocationId || null,
          capacity: capacityOf(draft.capacity),
        },
        accessToken,
      ),
    onSuccess: onDone,
    onError: (error) => setFailure(humanMessage(error)),
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    save.mutate()
  }

  return (
    <form
      onSubmit={submit}
      className="flex max-w-form flex-col gap-3 rounded-lg border border-border bg-surface-raised p-4"
    >
      <h2 ref={heading} tabIndex={-1} className="text-body font-medium text-ink">
        Editar «{location.name}»
      </h2>

      <Field
        label="Nombre"
        value={draft.name}
        onChange={(event) => setDraft({ ...draft, name: event.target.value })}
        required
      />

      <SelectField
        label="Tipo"
        value={draft.type}
        onChange={(event) => setDraft({ ...draft, type: event.target.value as LocationType })}
      >
        {LOCATION_TYPES.map((type) => (
          <option key={type} value={type}>
            {LOCATION_TYPE_LABELS[type]}
          </option>
        ))}
      </SelectField>

      <SelectField
        label="Dentro de"
        value={draft.parentLocationId}
        onChange={(event) => setDraft({ ...draft, parentLocationId: event.target.value })}
      >
        <option value="">Nada: es una vivienda</option>
        {locations
          .filter((candidate) => !forbidden.has(candidate.id))
          .map((candidate) => (
            <option key={candidate.id} value={candidate.id}>
              {candidate.name}
            </option>
          ))}
      </SelectField>

      <CapacityFields value={draft.capacity} onChange={(capacity) => setDraft({ ...draft, capacity })} />

      {failure && <Notice tone="danger">{failure}</Notice>}

      <div className="flex flex-wrap gap-2">
        <Button type="submit" variant="primary" busy={save.isPending} busyLabel="Guardando…">
          Guardar cambios
        </Button>
        <Button type="button" variant="ghost" onClick={onCancel}>
          Cancelar
        </Button>
      </div>
    </form>
  )
}
