import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode, SelectHTMLAttributes } from 'react'
import { useId } from 'react'

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

type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger'

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
 */
export function PageHeading({ title, action }: { title: string; action?: ReactNode }) {
  return (
    // Sin la serif a propósito: esa entra solo en `AuthCard`, que es la pantalla
    // de una sola columna. Aquí el `h1` compite con filas de listado y la serif
    // a ese tamaño empieza a pesar.
    <header className="mb-6 flex flex-wrap items-center justify-between gap-3">
      <h1 className="text-title text-ink">{title}</h1>
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

// Iconos de línea, cuadrícula de 24, como fija la dirección visual. Van con
// aria-hidden porque el significado lo lleva siempre el texto de al lado.

function iconProps() {
  return {
    width: 20,
    height: 20,
    viewBox: '0 0 24 24',
    fill: 'none',
    stroke: 'currentColor',
    strokeWidth: 1.75,
    strokeLinecap: 'round' as const,
    strokeLinejoin: 'round' as const,
    'aria-hidden': true,
  }
}

function ErrorIcon() {
  return (
    <svg {...iconProps()}>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7.5v5M12 16h.01" />
    </svg>
  )
}

function InfoIcon() {
  return (
    <svg {...iconProps()}>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 11v5M12 8h.01" />
    </svg>
  )
}

function SuccessIcon() {
  return (
    <svg {...iconProps()}>
      <circle cx="12" cy="12" r="9" />
      <path d="m8.5 12.5 2.5 2.5 4.5-5" />
    </svg>
  )
}

function WarningIcon() {
  return (
    <svg {...iconProps()}>
      <path d="M10.3 4.3 2.8 17.2a2 2 0 0 0 1.7 3h15a2 2 0 0 0 1.7-3L13.7 4.3a2 2 0 0 0-3.4 0Z" />
      <path d="M12 9.5v4M12 17h.01" />
    </svg>
  )
}
