import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Bike,
  Box,
  Car,
  CookingPot,
  Frame,
  Leaf,
  Monitor,
  PawPrint,
  Pencil,
  Pill,
  Plug,
  Shirt,
  Sofa,
  SprayCan,
  Utensils,
  Wrench,
  X,
  type LucideIcon,
} from 'lucide-react'
import { useRef, useState } from 'react'

import {
  api,
  humanMessage,
  type CategoryColor,
  type CategoryIcon,
  type Tag,
} from '../api/client'
import { Combobox } from './primitives'

/**
 * Las piezas del catálogo: cómo se clasifica lo que hay en casa.
 *
 * Tres componentes —el marcador de una categoría, el selector con el que se
 * elige y el campo de etiquetas— y el juego cerrado de dieciséis iconos, que es
 * la razón de que esto sea un fichero propio y no más líneas de
 * `primitives.tsx`. El criterio del Hito 3 partía por «primitiva pura frente a
 * pieza con peticiones», y aquí se parte **por dominio**: los dieciséis iconos
 * son una tabla de datos cerrada más que un componente, y metidos en
 * `primitives.tsx` acaban con el «se lee de una sentada» que su registro exige.
 *
 * Sus fichas están en `docs/frontend/design-system/components/`, y se
 * escribieron antes que este fichero.
 */

// ---------------------------------------------------------------------------
// El juego cerrado de iconos
// ---------------------------------------------------------------------------

/**
 * Los dieciséis, con el nombre por el que se ofrecen.
 *
 * Cerrado y no un buscador sobre las mil y pico de Lucide: eso obligaría a
 * mantener una traducción de mil nombres al castellano y dejaría elegir una
 * papelera para «Alimentación». La lista normativa está en
 * `docs/frontend/design-system/foundations/iconography.md` y la misma lista
 * cerrada vive en el `CHECK` de la migración `V17`.
 *
 * `Record` y no un `switch`: así, añadir un valor al enumerado del contrato sin
 * darle icono y nombre no compila.
 */
const CATEGORY_ICONS: Record<CategoryIcon, { icon: LucideIcon; label: string }> = {
  BOX: { icon: Box, label: 'Caja' },
  SOFA: { icon: Sofa, label: 'Sofá' },
  UTENSILS: { icon: Utensils, label: 'Cubiertos' },
  SPRAY: { icon: SprayCan, label: 'Limpieza' },
  TOOL: { icon: Wrench, label: 'Herramienta' },
  FRAME: { icon: Frame, label: 'Cuadro' },
  PLUG: { icon: Plug, label: 'Enchufe' },
  POT: { icon: CookingPot, label: 'Cazuela' },
  PILL: { icon: Pill, label: 'Medicina' },
  MONITOR: { icon: Monitor, label: 'Pantalla' },
  SHIRT: { icon: Shirt, label: 'Ropa' },
  BIKE: { icon: Bike, label: 'Bicicleta' },
  PENCIL: { icon: Pencil, label: 'Lápiz' },
  CAR: { icon: Car, label: 'Coche' },
  LEAF: { icon: Leaf, label: 'Planta' },
  PAW: { icon: PawPrint, label: 'Mascota' },
}

export const CATEGORY_ICON_VALUES = Object.keys(CATEGORY_ICONS) as CategoryIcon[]

/**
 * Las clases de cada color, **escritas enteras**.
 *
 * `bg-category-${color}-soft` no lo ve Tailwind, así que no genera ninguna regla
 * y el marcador saldría transparente **sin que fallara nada**: es exactamente el
 * defecto que el Hito 3 destapó en la pantalla de Préstamos, donde `text-muted`
 * y `border-line` no producían ni una línea de CSS.
 *
 * El nombre en castellano no es decoración: es lo único que tiene quien elige el
 * color sin verlo.
 */
const CATEGORY_COLORS: Record<CategoryColor, { classes: string; label: string }> = {
  ROSE: { classes: 'bg-category-rose-soft text-category-rose', label: 'Rosa' },
  PLUM: { classes: 'bg-category-plum-soft text-category-plum', label: 'Ciruela' },
  INDIGO: { classes: 'bg-category-indigo-soft text-category-indigo', label: 'Índigo' },
  SKY: { classes: 'bg-category-sky-soft text-category-sky', label: 'Cielo' },
  TEAL: { classes: 'bg-category-teal-soft text-category-teal', label: 'Turquesa' },
  MOSS: { classes: 'bg-category-moss-soft text-category-moss', label: 'Musgo' },
}

export const CATEGORY_COLOR_VALUES = Object.keys(CATEGORY_COLORS) as CategoryColor[]

/** Sin color elegido no se inventa uno: «nadie lo eligió» no es ninguno de los seis. */
const NO_COLOR = 'bg-surface-sunken text-ink-muted'

const MARKER_SIZES = {
  sm: { box: 'size-7 rounded-md', icon: 20 },
  md: { box: 'size-10 rounded-md', icon: 24 },
  lg: { box: 'size-24 rounded-md', icon: 40 },
} as const

/**
 * Cómo se reconoce una categoría de un vistazo: su icono dentro de un cuadradito
 * de su color.
 *
 * **Es decorativo y va con `aria-hidden`**, porque el nombre de la categoría
 * está siempre al lado en texto y anunciarlo lo diría dos veces. La excepción es
 * el tamaño `lg`, que es el hueco de una foto que falta: ahí no hay texto al
 * lado, así que lleva `role="img"` y su nombre accesible.
 */
export function CategoryMarker({
  icon,
  color,
  size = 'sm',
  label,
}: {
  icon: CategoryIcon | null
  color: CategoryColor | null
  size?: keyof typeof MARKER_SIZES
  label?: string
}) {
  const { box, icon: iconSize } = MARKER_SIZES[size]
  const Icon = CATEGORY_ICONS[icon ?? 'BOX'].icon
  const named = size === 'lg' && label

  return (
    <span
      // `shrink-0` y no `flex-1`: en una fila estrecha lo que se parte es el
      // nombre, no el distintivo.
      className={[
        'inline-flex shrink-0 items-center justify-center',
        box,
        color ? CATEGORY_COLORS[color].classes : NO_COLOR,
      ].join(' ')}
      {...(named
        ? { role: 'img', 'aria-label': `Sin foto. Categoría: ${label}` }
        : { 'aria-hidden': true })}
    >
      <Icon size={iconSize} strokeWidth={1.75} aria-hidden />
    </span>
  )
}

/**
 * Con qué se elige esa cara, dentro del juego cerrado (ADR-015).
 *
 * Dos rejillas de botones, no dos grupos de radios: **se pueden desmarcar**, y
 * un grupo de radios sin ninguno marcado es un estado que el control nativo
 * representa mal —y aquí es el estado inicial de toda categoría.
 *
 * La rejilla de color pinta un marcador con el icono ya elegido, así que elegir
 * color enseña **el resultado** y no una muestra de pintura.
 */
export function IconColorPicker({
  icon,
  color,
  context,
  onChange,
}: {
  icon: CategoryIcon | null
  color: CategoryColor | null
  /**
   * De quién es la cara que se está eligiendo: «categoría nueva», o el nombre de
   * la que se edita.
   *
   * **Es obligatorio y entra en el nombre accesible de cada botón**, y no es
   * ceremonia: la pantalla del catálogo puede tener dos selectores a la vez —el
   * del alta y el de la fila que se está editando— y sin esto habría cuarenta y
   * cuatro botones con veintidós nombres repetidos. Es el mismo fallo que la
   * ficha de `SuppliersPage` encontró al escribirse por delante, y aquí lo
   * destapó la prueba de la fila.
   */
  context: string
  onChange: (identity: { icon: CategoryIcon | null; color: CategoryColor | null }) => void
}) {
  return (
    <div className="flex flex-col gap-3">
      <fieldset className="flex flex-col gap-1.5">
        <legend className="text-body-sm font-medium text-ink">Icono</legend>
        <div className="flex flex-wrap gap-1">
          <PickerButton
            pressed={icon === null}
            label={`Sin icono, ${context}`}
            onClick={() => onChange({ icon: null, color })}
          >
            <span className="text-caption text-ink-muted">Ninguno</span>
          </PickerButton>
          {CATEGORY_ICON_VALUES.map((value) => (
            <PickerButton
              key={value}
              pressed={icon === value}
              label={`${CATEGORY_ICONS[value].label}, ${context}`}
              onClick={() => onChange({ icon: value, color })}
            >
              <CategoryMarker icon={value} color={color} />
            </PickerButton>
          ))}
        </div>
      </fieldset>

      <fieldset className="flex flex-col gap-1.5">
        <legend className="text-body-sm font-medium text-ink">Color</legend>
        <div className="flex flex-wrap gap-1">
          <PickerButton
            pressed={color === null}
            label={`Sin color, ${context}`}
            onClick={() => onChange({ icon, color: null })}
          >
            <span className="text-caption text-ink-muted">Ninguno</span>
          </PickerButton>
          {CATEGORY_COLOR_VALUES.map((value) => (
            <PickerButton
              key={value}
              pressed={color === value}
              label={`${CATEGORY_COLORS[value].label}, ${context}`}
              onClick={() => onChange({ icon, color: value })}
            >
              <CategoryMarker icon={icon} color={value} />
            </PickerButton>
          ))}
        </div>
      </fieldset>
    </div>
  )
}

/**
 * Un botón de las dos rejillas.
 *
 * Lo marcado no se dice solo con el relleno —que es justo lo que se está
 * eligiendo— sino con `aria-pressed` **y** un borde reforzado.
 */
function PickerButton({
  pressed,
  label,
  onClick,
  children,
}: {
  pressed: boolean
  label: string
  onClick: () => void
  children: React.ReactNode
}) {
  return (
    <button
      type="button"
      aria-pressed={pressed}
      aria-label={label}
      title={label}
      onClick={onClick}
      className={[
        'flex min-h-touch min-w-touch items-center justify-center rounded-md border-2 px-1',
        pressed ? 'border-border-strong' : 'border-transparent hover:bg-surface-hover',
      ].join(' ')}
    >
      {children}
    </button>
  )
}

// ---------------------------------------------------------------------------
// Etiquetas
// ---------------------------------------------------------------------------

/** El identificador de la opción «crear»; no es un `uuid` y por eso no choca con ninguno. */
const CREATE_OPTION = 'crear'

/**
 * El campo con el que un asset se clasifica por más de una cosa a la vez.
 *
 * **Está hecho de un [Combobox] entero y no de una copia suya**, que es la
 * decisión que más ahorra: la accesibilidad de un combobox es cara —foco que no
 * sale de la caja, `aria-activedescendant`, `listbox` referenciado por
 * `aria-controls`, `Escape` que cierra sin elegir— y ya está resuelta y probada
 * allí.
 *
 * Escribir un nombre que no existe **lo crea**, que es lo único que la pista
 * tiene que decir. Y la opción de crear no aparece cuando el nombre ya existe,
 * ni escrito con otras mayúsculas ni sin acentos: el catálogo compara
 * normalizado, así que ofrecerla sería ofrecer un `409`.
 */
export function TagField({
  label,
  value,
  onChange,
  accessToken,
  hint,
}: {
  label: string
  value: Tag[]
  onChange: (tags: Tag[]) => void
  accessToken: string
  hint?: string
}) {
  const queryClient = useQueryClient()
  const [query, setQuery] = useState('')
  const [failure, setFailure] = useState<string | null>(null)
  const boxRef = useRef<HTMLDivElement>(null)

  // La búsqueda entra en la clave: cambiarla es otra consulta al servidor y no
  // un filtro en memoria, igual que la de artículos. Con el catálogo de un hogar
  // grande, filtrar en el cliente significaría traérselo entero en cada tecla.
  const tags = useQuery({
    queryKey: ['tags', query],
    queryFn: () => api.listTags(accessToken, { q: query || undefined }),
  })

  const create = useMutation({
    mutationFn: (name: string) => api.createTag({ name }, accessToken),
    onSuccess: (tag) => {
      setFailure(null)
      put(tag)
      void queryClient.invalidateQueries({ queryKey: ['tags'] })
    },
    onError: (error) => setFailure(humanMessage(error)),
  })

  const chosen = new Set(value.map((tag) => tag.id))
  const suggestions = (tags.data?.items ?? []).filter((tag) => !chosen.has(tag.id))
  const typed = query.trim()

  // Comparación normalizada, la misma que hacen el caso de uso y el índice:
  // ofrecer «Crear camping» con «Camping» ya en la lista sería ofrecer un 409.
  const normalized = (text: string) => text.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase()
  const exists =
    typed.length > 0 &&
    [...(tags.data?.items ?? []), ...value].some((tag) => normalized(tag.name) === normalized(typed))

  const options = [
    ...suggestions.map((tag) => ({ id: tag.id, label: tag.name })),
    ...(typed.length > 0 && !exists && !create.isPending
      ? [{ id: CREATE_OPTION, label: `Crear «${typed}»` }]
      : []),
  ]

  function put(tag: Tag) {
    setQuery('')
    if (!chosen.has(tag.id)) onChange([...value, tag])
  }

  function remove(tag: Tag) {
    onChange(value.filter((current) => current.id !== tag.id))
    // El botón que tenía el foco deja de existir, así que sin esto el foco se
    // cae al `<body>` y quien navega con teclado pierde el sitio.
    boxRef.current?.querySelector('input')?.focus()
  }

  return (
    <div className="flex flex-col gap-1.5">
      {/* Antes de la caja en el DOM y en el tabulador: al revés, quitar la
          tercera etiqueta obligaría a pasar por la caja de texto y a que la
          lista de sugerencias se abriese de camino. */}
      {value.length > 0 && (
        <ul aria-label={`${label} puestas`} className="flex flex-wrap gap-1">
          {value.map((tag) => (
            <li key={tag.id}>
              <span className="inline-flex min-h-touch items-center gap-1 rounded-full bg-surface-sunken pl-3 text-body-sm text-ink">
                <span className="break-words">{tag.name}</span>
                <button
                  type="button"
                  onClick={() => remove(tag)}
                  aria-label={`Quitar la etiqueta ${tag.name}`}
                  className="flex min-h-touch min-w-touch items-center justify-center rounded-full text-ink-muted hover:text-ink"
                >
                  <X size={16} strokeWidth={1.75} aria-hidden />
                </button>
              </span>
            </li>
          ))}
        </ul>
      )}

      <div ref={boxRef}>
        <Combobox
          label={label}
          hint={hint}
          error={failure ?? undefined}
          value={query}
          options={options}
          placeholder="Camping, herencia, sótano…"
          onQueryChange={setQuery}
          onSelect={(option) => {
            if (option.id === CREATE_OPTION) {
              create.mutate(typed)
              return
            }
            const tag = suggestions.find((candidate) => candidate.id === option.id)
            if (tag) put(tag)
          }}
        />
      </div>
    </div>
  )
}

/**
 * Una etiqueta puesta, en modo lectura: en una fila del listado y en la ficha.
 *
 * No es un `StatusBadge`: aquel dice **en qué estado está** una cosa y tiene
 * cinco tonos de dominio detrás. Una etiqueta no tiene estado ni color, y
 * pintarla con uno de esos tonos diría algo que no significa nada.
 */
export function TagChip({ children }: { children: React.ReactNode }) {
  return (
    <span className="inline-flex items-center rounded-full bg-surface-sunken px-2 py-0.5 text-caption text-ink-muted">
      {children}
    </span>
  )
}
