import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Store } from 'lucide-react'
import { useState, type FormEvent } from 'react'

import {
  SERVICE_CATEGORY_LABELS,
  api,
  humanMessage,
  type ServiceCategory,
  type Supplier,
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
 * Proveedores y contactos de servicio: **la agenda de a quién se llama**.
 *
 * Su ficha se escribió antes que esta pantalla y está en
 * `docs/frontend/design-system/components/suppliers-page.md`.
 *
 * Lo que **no** hay aquí, y no es un olvido: esta pantalla no se pregunta si el
 * módulo está encendido. De eso se encarga el guardián de la ruta —`ModuleScreen`,
 * que la envuelve—, con el catálogo que ya está en la caché de la sesión. Si
 * alguien la montase sin guardián, sus peticiones responderían `403
 * MODULE_INACTIVE` y la pantalla enseñaría un error donde el producto ofrece un
 * botón: la tercera capa del gate es esa envoltura, no una comprobación repetida
 * en cada componente.
 */

const CATEGORIES = Object.keys(SERVICE_CATEGORY_LABELS) as ServiceCategory[]

export function SuppliersPage() {
  const { accessToken } = useAuthenticatedSession()
  const queryClient = useQueryClient()

  const [category, setCategory] = useState<ServiceCategory | ''>('')
  const [query, setQuery] = useState('')
  const [includeRetired, setIncludeRetired] = useState(false)
  const [adding, setAdding] = useState(false)

  const filters = {
    serviceCategory: category || undefined,
    q: query.trim() || undefined,
    includeRetired: includeRetired || undefined,
  }

  const suppliers = useQuery({
    queryKey: ['suppliers', filters],
    queryFn: () => api.listSuppliers(accessToken, filters),
  })

  const invalidate = () => void queryClient.invalidateQueries({ queryKey: ['suppliers'] })
  const isFiltered = Boolean(category || query.trim())

  return (
    <>
      <PageHeading
        title="Proveedores"
        icon={Store}
        action={
          <Button variant="primary" onClick={() => setAdding((open) => !open)} aria-expanded={adding}>
            Añadir contacto
          </Button>
        }
      />

      <p className="max-w-prose text-body text-ink-muted">
        Quién arregla, quién cobra y quién responde de una garantía. Puedes
        enlazar a cada uno con lo que atiende: la caldera, el coche, un sitio de
        la casa.
      </p>

      {adding && (
        <SupplierForm
          onCancel={() => setAdding(false)}
          onCreated={() => {
            setAdding(false)
            invalidate()
          }}
        />
      )}

      <div className="mt-6 flex flex-wrap items-end gap-3">
        {/* «Filtrar por categoría» y no «Categoría de servicio»: el formulario
            de alta tiene un campo con ese nombre, y dos controles con el mismo
            nombre accesible en la misma pantalla son indistinguibles para quien
            navega con lector de pantalla --y para una prueba, que es como se
            descubrió. */}
        <SelectField
          label="Filtrar por categoría"
          value={category}
          onChange={(event) => setCategory(event.target.value as ServiceCategory | '')}
        >
          <option value="">Todas</option>
          {CATEGORIES.map((key) => (
            <option key={key} value={key}>
              {SERVICE_CATEGORY_LABELS[key]}
            </option>
          ))}
        </SelectField>

        <Field
          label="Buscar"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          // La búsqueda mira el nombre y no los datos de contacto, y eso se dice
          // aquí: son datos personales de alguien que no es usuario del sistema,
          // y un buscador por correo convertiría esto en un directorio.
          hint="Busca por nombre."
        />

        <label className="flex min-h-touch items-center gap-2 text-body-sm text-ink">
          <input
            type="checkbox"
            checked={includeRetired}
            onChange={(event) => setIncludeRetired(event.target.checked)}
          />
          Ver también los retirados
        </label>
      </div>

      <div className="mt-6">
        {suppliers.isPending && <Spinner label="Cargando los contactos de servicio…" />}
        {suppliers.isError && <Notice tone="danger">{humanMessage(suppliers.error)}</Notice>}

        {suppliers.data && suppliers.data.items.length === 0 && (
          // «No hay ninguno» y «no hay ninguno que cumpla esto» piden cosas
          // distintas del usuario, así que se dicen distinto.
          <EmptyState title={isFiltered ? 'Nada con ese filtro' : 'Todavía no hay ningún contacto'}>
            {isFiltered
              ? 'Prueba con otra categoría o borra la búsqueda.'
              : 'Empieza por quien más llamas: el fontanero, el servicio técnico de la caldera, el taller.'}
          </EmptyState>
        )}

        {suppliers.data && suppliers.data.items.length > 0 && (
          <ul className="flex flex-col gap-3">
            {suppliers.data.items.map((supplier) => (
              <SupplierRow key={supplier.id} supplier={supplier} onChanged={invalidate} />
            ))}
          </ul>
        )}
      </div>
    </>
  )
}

/**
 * Una fila del listado, con su ficha desplegada dentro.
 *
 * Desplegar es un `<button>` con `aria-expanded` y no un `div` con `onClick`: es
 * lo que hace que se llegue con el tabulador y se abra con `Enter` y con
 * `Espacio` sin escribir un solo manejador de teclado.
 */
function SupplierRow({ supplier, onChanged }: { supplier: Supplier; onChanged: () => void }) {
  const [open, setOpen] = useState(false)
  const retired = supplier.retiredAt !== null

  return (
    <li className="rounded-lg border border-border-subtle bg-surface-raised">
      <button
        type="button"
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
        className="flex min-h-touch w-full flex-wrap items-center justify-between gap-2 p-4 text-left"
      >
        <span className="flex flex-wrap items-center gap-2">
          <span className="text-body font-medium text-ink">{supplier.name}</span>
          {/* El estado con etiqueta y no solo con color, que es la regla 4 de la
              dirección visual. */}
          {retired && <StatusBadge tone="neutral">Retirado</StatusBadge>}
        </span>
        <span className="flex flex-wrap items-center gap-3 text-body-sm text-ink-muted">
          <span>{SERVICE_CATEGORY_LABELS[supplier.serviceCategory]}</span>
          {supplier.phone && <span>{supplier.phone}</span>}
        </span>
      </button>

      {open && <SupplierDetailPanel supplier={supplier} onChanged={onChanged} />}
    </li>
  )
}

function SupplierDetailPanel({ supplier, onChanged }: { supplier: Supplier; onChanged: () => void }) {
  const { accessToken } = useAuthenticatedSession()
  const queryClient = useQueryClient()
  const [failure, setFailure] = useState<string | null>(null)
  const [linking, setLinking] = useState(false)
  const retired = supplier.retiredAt !== null

  const detail = useQuery({
    queryKey: ['supplier', supplier.id],
    queryFn: () => api.getSupplier(supplier.id, accessToken),
  })

  const refresh = () => void queryClient.invalidateQueries({ queryKey: ['supplier', supplier.id] })

  const retire = useMutation({
    mutationFn: () => api.retireSupplier(supplier.id, accessToken),
    onSuccess: () => {
      setFailure(null)
      onChanged()
    },
    onError: (error) => setFailure(humanMessage(error)),
  })

  const unlink = useMutation({
    mutationFn: (linkId: string) => api.unlinkSupplier(supplier.id, linkId, accessToken),
    onSuccess: refresh,
    onError: (error) => setFailure(humanMessage(error)),
  })

  return (
    <div className="border-t border-border-subtle p-4">
      <dl className="flex flex-col gap-1 text-body-sm">
        {supplier.contactName && <DetailLine label="Contacto" value={supplier.contactName} />}
        {supplier.phone && <DetailLine label="Teléfono" value={supplier.phone} />}
        {supplier.email && <DetailLine label="Correo" value={supplier.email} />}
        {supplier.website && <DetailLine label="Web" value={supplier.website} />}
        {supplier.address && <DetailLine label="Dirección" value={supplier.address} />}
        {supplier.notes && <DetailLine label="Notas" value={supplier.notes} />}
      </dl>

      <div className="mt-4">
        <p className="text-body-sm font-medium text-ink">Atiende</p>

        {detail.isPending && <Spinner label="Cargando los enlaces…" />}
        {detail.data && detail.data.links.length === 0 && (
          <p className="mt-1 text-body-sm text-ink-muted">Todavía no está enlazado con nada.</p>
        )}
        {detail.data && detail.data.links.length > 0 && (
          <ul className="mt-1 flex flex-col gap-1">
            {detail.data.links.map((link) => (
              <li key={link.id} className="flex flex-wrap items-center gap-2 text-body-sm text-ink">
                <span>
                  {link.targetName}{' '}
                  <span className="text-ink-muted">
                    ({link.targetType === 'ASSET' ? 'cosa' : 'sitio'})
                  </span>
                </span>
                <Button
                  variant="ghost"
                  onClick={() => unlink.mutate(link.id)}
                  busy={unlink.isPending}
                  busyLabel="Quitando…"
                >
                  Quitar {link.targetName}
                </Button>
              </li>
            ))}
          </ul>
        )}
      </div>

      {failure && (
        <Notice tone="danger">
          {failure}
        </Notice>
      )}

      <div className="mt-4 flex flex-wrap gap-2">
        {/* Un contacto retirado no admite enlaces nuevos: el servidor responde
            409, así que el botón no se pinta. Un control que solo sirve para
            recibir un error es peor que no tenerlo. */}
        {!retired && (
          <Button onClick={() => setLinking((open) => !open)} aria-expanded={linking}>
            Enlazar {supplier.name} con algo
          </Button>
        )}
        {!retired && (
          <Button
            variant="danger"
            onClick={() => retire.mutate()}
            busy={retire.isPending}
            busyLabel="Retirando…"
          >
            Retirar {supplier.name}
          </Button>
        )}
      </div>

      {linking && !retired && (
        <LinkForm
          supplier={supplier}
          onLinked={() => {
            setLinking(false)
            refresh()
          }}
          onFailure={setFailure}
        />
      )}
    </div>
  )
}

function DetailLine({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex flex-wrap gap-2">
      <dt className="text-ink-muted">{label}</dt>
      <dd className="text-ink">{value}</dd>
    </div>
  )
}

/**
 * El alta de un contacto.
 *
 * Los cinco datos de contacto son opcionales uno a uno y obligatorios en
 * conjunto, y esa regla **no se replica aquí**: la comprueba el servidor y su
 * `409 SUPPLIER_CONTACT_REQUIRED` se enseña donde ocurrió. Duplicarla en el
 * cliente daría dos versiones de la misma regla, y la de aquí sería la que se
 * quedaría vieja.
 */
function SupplierForm({ onCancel, onCreated }: { onCancel: () => void; onCreated: () => void }) {
  const { accessToken } = useAuthenticatedSession()
  const [name, setName] = useState('')
  const [serviceCategory, setServiceCategory] = useState<ServiceCategory>('PLUMBING')
  const [contactName, setContactName] = useState('')
  const [phone, setPhone] = useState('')
  const [email, setEmail] = useState('')
  const [website, setWebsite] = useState('')
  const [failure, setFailure] = useState<string | null>(null)

  const create = useMutation({
    mutationFn: () =>
      api.createSupplier(
        {
          name,
          serviceCategory,
          contactName: contactName || null,
          phone: phone || null,
          email: email || null,
          website: website || null,
        },
        accessToken,
      ),
    onSuccess: () => {
      setFailure(null)
      onCreated()
    },
    onError: (error) => setFailure(humanMessage(error)),
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    create.mutate()
  }

  return (
    <form onSubmit={submit} className="mt-6 flex max-w-form flex-col gap-3 rounded-lg border border-border-subtle p-4">
      <Field
        label="Nombre"
        value={name}
        onChange={(event) => setName(event.target.value)}
        required
        hint="Como lo llamas tú: «Fontanería Pérez», «el del gas»."
      />

      <SelectField
        label="Categoría de servicio"
        value={serviceCategory}
        onChange={(event) => setServiceCategory(event.target.value as ServiceCategory)}
      >
        {CATEGORIES.map((key) => (
          <option key={key} value={key}>
            {SERVICE_CATEGORY_LABELS[key]}
          </option>
        ))}
      </SelectField>

      <Field
        label="Persona de contacto"
        value={contactName}
        onChange={(event) => setContactName(event.target.value)}
      />
      <Field
        label="Teléfono"
        value={phone}
        onChange={(event) => setPhone(event.target.value)}
        hint="Con el teléfono, el correo o la web basta: hace falta uno de los tres."
      />
      <Field label="Correo" value={email} onChange={(event) => setEmail(event.target.value)} />
      <Field label="Web" value={website} onChange={(event) => setWebsite(event.target.value)} />

      {failure && <Notice tone="danger">{failure}</Notice>}

      <div className="flex flex-wrap gap-2">
        <Button type="submit" variant="primary" busy={create.isPending} busyLabel="Guardando…">
          Guardar contacto
        </Button>
        <Button type="button" onClick={onCancel}>
          Cancelar
        </Button>
      </div>
    </form>
  )
}

/**
 * Con qué se enlaza.
 *
 * Un `SelectField` y no un `Combobox`, que es lo que este caso pide de verdad y
 * el sistema de diseño sigue sin tener desde el Hito 2 de la Fase 1. Aguanta
 * mientras un hogar tenga decenas de ubicaciones y no cientos; está dicho en la
 * ficha de la pantalla para que la deuda se vea.
 */
function LinkForm({
  supplier,
  onLinked,
  onFailure,
}: {
  supplier: Supplier
  onLinked: () => void
  onFailure: (message: string) => void
}) {
  const { accessToken } = useAuthenticatedSession()
  const [locationId, setLocationId] = useState('')

  const locations = useQuery({
    queryKey: ['locations'],
    queryFn: () => api.listLocations(accessToken),
  })

  const link = useMutation({
    mutationFn: () => api.linkSupplier(supplier.id, { locationId }, accessToken),
    onSuccess: onLinked,
    onError: (error) => onFailure(humanMessage(error)),
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    if (locationId) link.mutate()
  }

  return (
    <form onSubmit={submit} className="mt-3 flex max-w-form flex-col gap-3">
      <SelectField
        label={`Sitio que atiende ${supplier.name}`}
        value={locationId}
        onChange={(event) => setLocationId(event.target.value)}
        hint="De momento se enlaza con sitios de la casa."
      >
        <option value="">Elige un sitio</option>
        {(locations.data?.items ?? []).map((location) => (
          <option key={location.id} value={location.id}>
            {location.name}
          </option>
        ))}
      </SelectField>

      <div>
        <Button type="submit" variant="primary" busy={link.isPending} busyLabel="Enlazando…">
          Enlazar
        </Button>
      </div>
    </form>
  )
}
