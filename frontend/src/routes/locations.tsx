import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState, type FormEvent } from 'react'

import { LOCATION_TYPE_LABELS, api, humanMessage, type Location, type LocationType } from '../api/client'
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

interface TreeNode {
  location: Location
  children: TreeNode[]
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
  const [draft, setDraft] = useState({ name: '', type: 'ROOM' as LocationType, parentLocationId: '' })

  const locations = useQuery({
    queryKey: ['locations'],
    queryFn: () => api.listLocations(accessToken),
  })

  const tree = useMemo(() => buildTree(locations.data?.items ?? []), [locations.data])

  const create = useMutation({
    mutationFn: () =>
      api.createLocation(
        {
          name: draft.name,
          type: draft.type,
          ...(draft.parentLocationId ? { parentLocationId: draft.parentLocationId } : {}),
        },
        accessToken,
      ),
    onSuccess: () => {
      setDraft({ name: '', type: 'ROOM', parentLocationId: '' })
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
      <PageHeading title="Ubicaciones" />

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
            {locations.data?.items.map((location) => (
              <option key={location.id} value={location.id}>
                {location.name}
              </option>
            ))}
          </SelectField>

          <Button type="submit" variant="primary" busy={create.isPending} busyLabel="Creando…">
            Crear ubicación
          </Button>
        </form>

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
              <LocationBranch key={node.location.id} node={node} level={1} onDelete={remove.mutate} />
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
  onDelete,
}: {
  node: TreeNode
  level: number
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
          {node.location.capacity?.type === 'UNITS' && (
            <span className="text-caption text-ink-subtle">
              hasta {node.location.capacity.max} {node.location.capacity.unit}
            </span>
          )}
        </span>

        <Button variant="ghost" onClick={() => onDelete(node.location.id)}>
          Borrar
        </Button>
      </div>

      {hasChildren && (
        <ul role="group" className="mt-1 flex flex-col gap-1">
          {node.children.map((child) => (
            <LocationBranch key={child.location.id} node={child} level={level + 1} onDelete={onDelete} />
          ))}
        </ul>
      )}
    </li>
  )
}
