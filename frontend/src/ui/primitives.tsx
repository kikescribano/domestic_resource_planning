import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode } from 'react'
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
 * Un estado del dominio: color, etiqueta e icono. Los tres juntos, siempre.
 *
 * Aquí se usa para los roles y el estado de una invitación; en el Hito 2 lo
 * heredan los estados de asset, que es donde la regla se cobra de verdad.
 */
export function StatusBadge({ tone, children }: { tone: NoticeTone | 'neutral'; children: ReactNode }) {
  const tones: Record<string, string> = {
    info: 'bg-info-soft text-info',
    success: 'bg-success-soft text-success',
    warning: 'bg-warning-soft text-warning',
    danger: 'bg-danger-soft text-danger',
    neutral: 'bg-state-decommissioned-soft text-state-decommissioned',
  }

  return (
    <span
      className={[
        'inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-caption font-medium',
        tones[tone],
      ].join(' ')}
    >
      {children}
    </span>
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
