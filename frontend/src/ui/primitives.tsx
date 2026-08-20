import { CircleAlert, CircleCheck, Info, TriangleAlert, type LucideIcon } from 'lucide-react'
import type {
  ButtonHTMLAttributes,
  InputHTMLAttributes,
  KeyboardEvent,
  ReactNode,
  SelectHTMLAttributes,
} from 'react'
import { useId, useState } from 'react'

/**
 * Los primitivos del sistema de diseño.
 *
 * Implementan la dirección de `docs/frontend/product-design/look-and-feel.md`.
 * Tres reglas de esa dirección viven aquí dentro y no en cada pantalla, que es
 * lo que impide incumplirlas por descuido:
 *
 * - **Objetivo táctil de 44 px** en todo lo pulsable (`min-h-touch`).
 * - **Nada se dice solo con color**: el error de un campo lleva borde, icono y
 *   mensaje; el aviso lleva icono y texto.
 * - **Una acción principal por pantalla**: solo `variant="primary"` pinta el
 *   relleno de acento, y si aparece dos veces es que una de las dos no era la
 *   principal.
 *
 * El foco no se declara en ningún componente: está en la capa base de
 * `index.css`, para que ninguno pueda olvidarlo.
 */

type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'ghost-danger'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
  busy?: boolean
  busyLabel?: string
}

const BUTTON_VARIANTS: Record<ButtonVariant, string> = {
  primary: 'bg-accent text-ink-inverse hover:bg-accent-hover',
  secondary: 'border border-border bg-surface-raised text-ink hover:bg-surface-hover',
  ghost: 'text-accent-ink hover:bg-surface-hover',
  danger: 'border border-danger text-danger hover:bg-danger-soft',
  // El ghost de lo destructivo: mismo silencio que `ghost` pero en el rojo del
  // esquema, para la acción de borrar que vive dentro de una fila y no puede
  // cargar con el borde del `danger` en cada una.
  'ghost-danger': 'text-danger hover:bg-danger-soft',
}

export function Button({
  variant = 'secondary',
  busy = false,
  busyLabel,
  children,
  className = '',
  disabled,
  ...props
}: ButtonProps) {
  return (
    <button
      {...props}
      disabled={disabled || busy}
      // aria-busy y no solo el texto: quien usa lector de pantalla necesita
      // enterarse de que hay algo en curso.
      aria-busy={busy || undefined}
      className={[
        'inline-flex min-h-touch items-center justify-center gap-2 rounded-md px-4 py-2',
        'text-body font-medium transition-colors',
        'disabled:cursor-not-allowed disabled:opacity-60',
        BUTTON_VARIANTS[variant],
        className,
      ].join(' ')}
    >
      {/* El botón conserva su anchura al pasar a ocupado: si encogiera, la
          maquetación daría un salto justo cuando el usuario acaba de pulsar. */}
      {busy && busyLabel ? busyLabel : children}
    </button>
  )
}

interface FieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string
  hint?: string
  error?: string
}

export function Field({ label, hint, error, id, className = '', ...props }: FieldProps) {
  const generatedId = useId()
  const fieldId = id ?? generatedId
  const hintId = `${fieldId}-hint`
  const errorId = `${fieldId}-error`

  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={fieldId} className="text-body-sm font-medium text-ink">
        {label}
      </label>

      <input
        {...props}
        id={fieldId}
        aria-invalid={error ? true : undefined}
        aria-describedby={[hint ? hintId : null, error ? errorId : null].filter(Boolean).join(' ') || undefined}
        className={[
          'min-h-touch rounded-md border bg-surface-raised px-3 py-2 text-body text-ink',
          'placeholder:text-ink-subtle',
          error ? 'border-danger' : 'border-border',
          className,
        ].join(' ')}
      />

      {hint && !error && (
        <p id={hintId} className="text-caption text-ink-muted">
          {hint}
        </p>
      )}

      {/* El hueco del error está reservado por el flujo normal: el mensaje
          aparece debajo sin desplazar lo de arriba. Y lleva icono además del
          color, porque el color no puede ser el único portador. */}
      {error && (
        <p id={errorId} className="flex items-start gap-1.5 text-caption text-danger">
          <ErrorIcon />
          <span>{error}</span>
        </p>
      )}
    </div>
  )
}

interface SelectFieldProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label: string
  hint?: string
  error?: string
}

/**
 * Un desplegable con la misma anatomía que [Field].
 *
 * Existe porque el Hito 2 trae seis, y la versión a mano —un `<label>` que
 * envuelve al `<select>` con la pista dentro— tiene un defecto que no se ve
 * mirando la pantalla: **la pista pasa a formar parte del nombre accesible**, así
 * que un lector de pantalla anuncia «Unidad, la fija el artículo, todas sus
 * existencias se llevan en ella» cada vez que el foco entra en el campo. La
 * pista va en `aria-describedby`, que es lo que se lee después y una sola vez.
 */
export function SelectField({ label, hint, error, id, children, className = '', ...props }: SelectFieldProps) {
  const generatedId = useId()
  const fieldId = id ?? generatedId
  const hintId = `${fieldId}-hint`
  const errorId = `${fieldId}-error`

  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={fieldId} className="text-body-sm font-medium text-ink">
        {label}
      </label>

      <select
        {...props}
        id={fieldId}
        aria-invalid={error ? true : undefined}
        aria-describedby={[hint ? hintId : null, error ? errorId : null].filter(Boolean).join(' ') || undefined}
        className={[
          'min-h-touch rounded-md border bg-surface-raised px-3 py-2 text-body text-ink',
          error ? 'border-danger' : 'border-border',
          className,
        ].join(' ')}
      >
        {children}
      </select>

      {hint && !error && (
        <p id={hintId} className="text-caption text-ink-muted">
          {hint}
        </p>
      )}

      {error && (
        <p id={errorId} className="flex items-start gap-1.5 text-caption text-danger">
          <ErrorIcon />
          <span>{error}</span>
        </p>
      )}
    </div>
  )
}

type NoticeTone = 'info' | 'success' | 'warning' | 'danger'

const NOTICE_TONES: Record<NoticeTone, { box: string; icon: ReactNode }> = {
  info: { box: 'border-info bg-info-soft text-info', icon: <InfoIcon /> },
  success: { box: 'border-success bg-success-soft text-success', icon: <SuccessIcon /> },
  warning: { box: 'border-warning bg-warning-soft text-warning', icon: <WarningIcon /> },
  danger: { box: 'border-danger bg-danger-soft text-danger', icon: <ErrorIcon /> },
}

/**
 * Un aviso en el sitio donde ocurrió.
 *
 * `role="alert"` solo en los de error: un `alert` interrumpe al lector de
 * pantalla, y hacerlo para confirmar un éxito es exactamente el ruido que la
 * dirección visual llama «feedback intrusivo».
 */
export function Notice({
  tone = 'info',
  title,
  children,
}: {
  tone?: NoticeTone
  title?: string
  children: ReactNode
}) {
  const { box, icon } = NOTICE_TONES[tone]

  return (
    <div
      role={tone === 'danger' ? 'alert' : 'status'}
      className={['flex gap-3 rounded-lg border p-3 text-body-sm', box].join(' ')}
    >
      <span className="mt-0.5 shrink-0" aria-hidden="true">
        {icon}
      </span>
      <div className="text-ink">
        {title && <p className="font-medium">{title}</p>}
        <div className="text-ink-muted">{children}</div>
      </div>
    </div>
  )
}

/** La tarjeta de las pantallas de una sola columna: alta, login, verificación. */
export function AuthCard({ title, subtitle, children }: { title: string; subtitle?: ReactNode; children: ReactNode }) {
  return (
    <main className="mx-auto flex min-h-dvh w-full max-w-form flex-col justify-center gap-6 px-gutter py-10">
      <header className="flex flex-col gap-2">
        {/* La serif entra aquí y solo aquí: un h1 por pantalla, nunca dentro de
            una fila de listado. */}
        <h1 className="text-display text-ink">{title}</h1>
        {subtitle && <p className="text-lead text-ink-muted">{subtitle}</p>}
      </header>
      {children}
    </main>
  )
}

export function Spinner({ label }: { label: string }) {
  return (
    <p role="status" className="flex items-center gap-2 text-body-sm text-ink-muted">
      <span
        aria-hidden="true"
        className="size-4 animate-spin rounded-full border-2 border-border border-t-accent"
      />
      {label}
    </p>
  )
}

/**
 * Un estado, con color **y etiqueta**: el color nunca va solo, porque quien no
 * lo distingue se queda sin el dato.
 *
 * Sus tonos son de dos familias y conviene no mezclarlas. Los de *feedback*
 * —`info`, `success`, `warning`, `danger`— dicen cómo fue una operación; los de
 * *dominio* —`available`, `lent`, `overdue`, `decommissioned`, `out-of-stock`—
 * dicen en qué estado está una cosa del hogar. Hasta el Hito 2 solo existían los
 * primeros, así que un estado de asset se pedía prestando el tono de un aviso:
 * los valores coincidían y por eso no se veía, pero era la frontera de
 * `foundations/color.md` cruzada.
 *
 * No lleva icono, a diferencia de `Notice`. En una fila de listado se repite en
 * cada línea, y quince iconos idénticos en columna son ruido, no información: la
 * etiqueta ya dice lo que el color sugiere.
 */
type StatusTone = NoticeTone | 'neutral' | 'available' | 'lent' | 'overdue' | 'decommissioned' | 'out-of-stock'

const STATUS_TONES: Record<StatusTone, string> = {
  info: 'bg-info-soft text-info',
  success: 'bg-success-soft text-success',
  warning: 'bg-warning-soft text-warning',
  danger: 'bg-danger-soft text-danger',
  neutral: 'bg-state-decommissioned-soft text-state-decommissioned',
  available: 'bg-state-available-soft text-state-available',
  lent: 'bg-state-lent-soft text-state-lent',
  overdue: 'bg-state-overdue-soft text-state-overdue',
  decommissioned: 'bg-state-decommissioned-soft text-state-decommissioned',
  'out-of-stock': 'bg-state-out-of-stock-soft text-state-out-of-stock',
}

export function StatusBadge({ tone, children }: { tone: StatusTone; children: ReactNode }) {
  return (
    <span
      className={[
        'inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-caption font-medium',
        STATUS_TONES[tone],
      ].join(' ')}
    >
      {children}
    </span>
  )
}

/**
 * La cabecera de una pantalla: un `h1` y, opcionalmente, su acción principal.
 *
 * Vive aquí y no en una ruta concreta porque el Hito 2 trae cuatro pantallas más
 * que la necesitan, y tres copias del mismo `header` es como se acaba con tres
 * tamaños de título distintos.
 *
 * El `icon` es el mismo que la sección lleva en la navegación —la cabecera
 * repite el nombre y el icono del menú, no estrena otros— y va delante del
 * texto, como en la barra lateral. `aria-hidden`, porque el nombre accesible de
 * la pantalla es el título.
 */
export function PageHeading({ title, action, icon: Icon }: { title: string; action?: ReactNode; icon?: LucideIcon }) {
  return (
    <header className="mb-6 flex flex-wrap items-center justify-between gap-3">
      <div className="flex items-center gap-2.5">
        {Icon && <Icon size={24} strokeWidth={1.75} aria-hidden="true" className="shrink-0 text-accent-ink" />}
        <h1 className="text-title text-ink">{title}</h1>
      </div>
      {action}
    </header>
  )
}

/**
 * Lo que se pinta cuando no hay nada que pintar.
 *
 * Una lista vacía sin explicación es indistinguible de una que no ha cargado o
 * de un filtro que no encuentra nada, y las tres piden cosas distintas del
 * usuario.
 */
export function EmptyState({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="rounded-lg border border-dashed border-border p-6 text-center">
      <p className="text-body font-medium text-ink">{title}</p>
      {children && <div className="mt-1 text-body-sm text-ink-muted">{children}</div>}
    </div>
  )
}

/**
 * Un campo de texto que sugiere entre muchas opciones.
 *
 * **Llevaba aplazado desde el Hito 2 de la Fase 1**, y se construye aquí porque
 * Warehouse es el primero que lo pide de verdad. La deuda estaba dicha en la
 * ficha de la pantalla de Proveedores, que salió del paso con un `SelectField`
 * sobre ubicaciones —decenas—; buscar un artículo entre los cientos de una
 * despensa no lo resuelve un desplegable, y `GET /articles` lleva un parámetro
 * `q` que existe justamente para alimentar esto.
 *
 * **La accesibilidad es el motivo de que sea un primitivo y no código de
 * pantalla.** Un combobox mal hecho es de los controles que peor se degradan:
 * sin `aria-activedescendant` el lector de pantalla no anuncia la opción
 * resaltada, y moviendo el foco a la lista se pierde lo que se está escribiendo.
 * Así que aquí:
 *
 * - **El foco no se mueve nunca de la caja de texto.** Las flechas cambian
 *   `aria-activedescendant`, que es lo que el patrón combobox de ARIA 1.2
 *   prescribe.
 * - **El listado es un `role="listbox"` con `role="option"`**, y la relación la
 *   declara `aria-controls` sobre el input.
 * - **`Escape` cierra sin elegir** y `Enter` elige lo resaltado. Un combobox del
 *   que no se puede salir sin ratón deja la pantalla bloqueada para quien navega
 *   con teclado.
 * - **El estado se anuncia** con `aria-expanded`, y cuántas opciones hay con una
 *   región `aria-live` discreta: teclear y no oír nada es indistinguible de que
 *   el control esté roto.
 *
 * Lo que **no** hace, a propósito: no busca por su cuenta. Recibe las opciones
 * ya filtradas y avisa de lo que se teclea, de modo que quien lo usa decide si
 * eso es una consulta al servidor o un filtro en memoria. Meterle la consulta
 * dentro lo ataría a una forma de pedir datos.
 */
export function Combobox({
  label,
  hint,
  error,
  value,
  options,
  onQueryChange,
  onSelect,
  id,
  placeholder,
}: {
  label: string
  hint?: string
  error?: string
  /** Lo que se ve escrito. Lo controla quien lo usa, como en [Field]. */
  value: string
  options: Array<{ id: string; label: string; detail?: string }>
  onQueryChange: (query: string) => void
  onSelect: (option: { id: string; label: string }) => void
  id?: string
  placeholder?: string
}) {
  const generatedId = useId()
  const fieldId = id ?? generatedId
  const listId = `${fieldId}-list`
  const hintId = `${fieldId}-hint`
  const errorId = `${fieldId}-error`
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(0)

  // Acotado al último índice válido: la lista cambia con cada tecla, y un
  // resaltado que apunte fuera deja `aria-activedescendant` señalando a un
  // elemento que no existe —que es peor que no tenerlo.
  const activeIndex = options.length === 0 ? -1 : Math.min(active, options.length - 1)
  const activeOption = activeIndex >= 0 ? options[activeIndex] : undefined

  function choose(option: { id: string; label: string }) {
    onSelect(option)
    setOpen(false)
    setActive(0)
  }

  function onKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault()
      setOpen(true)
      setActive((current) => {
        if (options.length === 0) return 0
        const next = event.key === 'ArrowDown' ? current + 1 : current - 1
        // Da la vuelta: llegar al final y quedarse ahí obliga a contar cuántas
        // veces se ha pulsado para poder volver arriba.
        return (next + options.length) % options.length
      })
      return
    }

    if (event.key === 'Enter' && open && activeOption) {
      event.preventDefault()
      choose(activeOption)
      return
    }

    if (event.key === 'Escape') {
      setOpen(false)
    }
  }

  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={fieldId} className="text-body-sm font-medium text-ink">
        {label}
      </label>

      {/* `relative` en un contenedor propio y no en el campo: la lista se
          posiciona contra esto, y anclada al campo taparía la pista de abajo. */}
      <div className="relative">
        <input
          id={fieldId}
          type="text"
          role="combobox"
          autoComplete="off"
          aria-expanded={open}
          aria-controls={listId}
          aria-autocomplete="list"
          aria-activedescendant={open && activeOption ? `${listId}-${activeOption.id}` : undefined}
          aria-invalid={error ? true : undefined}
          aria-describedby={[hint ? hintId : null, error ? errorId : null].filter(Boolean).join(' ') || undefined}
          value={value}
          placeholder={placeholder}
          onChange={(event) => {
            onQueryChange(event.target.value)
            setOpen(true)
            setActive(0)
          }}
          onKeyDown={onKeyDown}
          onFocus={() => setOpen(true)}
          // Con retardo: sin él, el `blur` cierra la lista antes de que el clic
          // sobre una opción llegue a dispararse, y elegir con el ratón deja de
          // funcionar sin que nada falle.
          onBlur={() => window.setTimeout(() => setOpen(false), 150)}
          className={[
            'min-h-touch w-full rounded-md border bg-surface-raised px-3 py-2 text-body text-ink',
            'placeholder:text-ink-subtle',
            error ? 'border-danger' : 'border-border',
          ].join(' ')}
        />

        <ul
          id={listId}
          role="listbox"
          aria-label={label}
          // Siempre en el DOM y oculto cuando toca: un `listbox` que entra y sale
          // del árbol rompe la referencia de `aria-controls`.
          hidden={!open || options.length === 0}
          className="absolute z-10 mt-1 max-h-64 w-full overflow-auto rounded-md border border-border bg-surface-raised shadow-lg"
        >
          {options.map((option, index) => (
            <li
              key={option.id}
              id={`${listId}-${option.id}`}
              role="option"
              aria-selected={index === activeIndex}
              // `onMouseDown` y no `onClick`: el clic llega después del `blur`, y
              // para entonces la lista ya se está cerrando.
              onMouseDown={(event) => {
                event.preventDefault()
                choose(option)
              }}
              onMouseEnter={() => setActive(index)}
              className={[
                'flex min-h-touch cursor-pointer flex-wrap items-center justify-between gap-2 px-3 py-2 text-body-sm',
                index === activeIndex ? 'bg-surface-hover text-ink' : 'text-ink',
              ].join(' ')}
            >
              <span>{option.label}</span>
              {option.detail && <span className="text-ink-muted">{option.detail}</span>}
            </li>
          ))}
        </ul>
      </div>

      <p className="sr-only" aria-live="polite">
        {open && options.length > 0
          ? `${options.length} ${options.length === 1 ? 'resultado' : 'resultados'}`
          : ''}
      </p>

      {hint && !error && (
        <p id={hintId} className="text-caption text-ink-muted">
          {hint}
        </p>
      )}

      {error && (
        <p id={errorId} className="flex items-start gap-1.5 text-caption text-danger">
          <ErrorIcon />
          <span>{error}</span>
        </p>
      )}
    </div>
  )
}

// Los cuatro iconos de feedback, ya de Lucide.
//
// Estaban dibujados a mano desde la Fase 1 «siguiendo su geometría sin ser
// Lucide», porque `iconography.md` adoptó el juego y dejó la dependencia fuera.
// La dependencia entró con el cierre de huecos (Hito 4), y dejarlos a mano
// habría dejado el sistema con **dos vocabularios de iconos**: estos cuatro y
// los dieciséis de categoría. Son los mismos trazos —cuadrícula de 24, grosor
// 1,75— y ninguno cambia de silueta.
//
// Van con aria-hidden porque el significado lo lleva siempre el texto de al
// lado, y a 20 px, que es la medida en línea de texto que fija la dirección.

const ICON_IN_TEXT = { size: 20, strokeWidth: 1.75, 'aria-hidden': true } as const

function ErrorIcon() {
  return <CircleAlert {...ICON_IN_TEXT} />
}

function InfoIcon() {
  return <Info {...ICON_IN_TEXT} />
}

function SuccessIcon() {
  return <CircleCheck {...ICON_IN_TEXT} />
}

function WarningIcon() {
  return <TriangleAlert {...ICON_IN_TEXT} />
}

/**
 * El sitio de una acción que **no se puede deshacer**.
 *
 * Es la pieza que estrena la baja de hogar y el cierre de cuenta (ADR-012), que
 * son las dos primeras operaciones del producto que borran datos de verdad y
 * para siempre. Todo lo demás que el sistema llama «baja» es lógica —un asset
 * dado de baja sigue en su fila, alguien que deja el hogar conserva su
 * historial—, y por eso le basta un `Button` de variante `danger`.
 *
 * **La confirmación se escribe, no se pulsa**, y no es teatro. Un «¿seguro?» se
 * contesta que sí por reflejo: es el gesto que se hace cincuenta veces al día
 * para cerrar avisos, y su coste es exactamente un clic más, que es lo que
 * cuesta también equivocarse. Teclear el nombre del hogar obliga a leer qué se
 * está borrando y a escribir justo eso, y ninguna de las dos cosas se hace sin
 * querer.
 *
 * **No es un diálogo**, y ahí se gana lo que más caro sale en accesibilidad: sin
 * modal no hay foco que atrapar ni `Escape` que atender. Es una sección al final
 * de la pantalla, después de todo lo demás.
 *
 * La comparación es **exacta**: ni se recortan espacios ni se ignoran
 * mayúsculas. La tolerancia aquí juega en contra, porque lo que se busca es
 * precisamente que cueste.
 */
export function DangerZone({
  title,
  confirmation,
  confirmationLabel,
  action,
  busyLabel,
  busy = false,
  error,
  onConfirm,
  children,
}: {
  title: string
  confirmation: string
  confirmationLabel: string
  action: string
  busyLabel: string
  busy?: boolean
  error?: string | null
  onConfirm: () => void
  children: ReactNode
}) {
  const [typed, setTyped] = useState('')
  const armed = typed === confirmation

  return (
    <section className="mt-10 flex max-w-form flex-col gap-4 border-t border-border-subtle pt-6">
      {/* Sin fondo de color: la superficie roja de bloque es lo que
          `foundations/color.md` reserva para un error que YA ha ocurrido, y esto
          todavía no ha ocurrido. Lo que la señala es el borde, el encabezado y
          estar al final. */}
      <h2 className="font-display text-title-sm text-danger">{title}</h2>

      <div className="flex flex-col gap-2 break-words text-body-sm text-ink-muted">{children}</div>

      {error && <Notice tone="danger">{error}</Notice>}

      {/* Un `div` y no un `form`: en un formulario, `Enter` dentro del campo lo
          envía, que es la forma más rápida de disparar esto sin haber mirado el
          botón. */}
      <Field
        label={confirmationLabel}
        value={typed}
        onChange={(event) => setTyped(event.target.value)}
        // No es una credencial, así que el navegador no debe ofrecer rellenarlo:
        // sugerirlo sería justo lo contrario de lo que el campo consigue.
        autoComplete="off"
        spellCheck={false}
      />

      <Button
        variant="danger"
        // `disabled` de verdad y no `aria-disabled` sobre un botón vivo: quien
        // navega sin ver tiene que enterarse de que no está disponible, y la
        // etiqueta del campo dice qué falta para que lo esté.
        disabled={!armed}
        busy={busy}
        busyLabel={busyLabel}
        onClick={onConfirm}
      >
        {action}
      </Button>
    </section>
  )
}
