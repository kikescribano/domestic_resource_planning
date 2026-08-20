import { useId, useRef, useState } from 'react'
import type { ReactNode } from 'react'

import {
  ALLOWED_FILE_TYPES,
  ApiError,
  CONVERTIBLE_FILE_TYPES,
  UPLOAD_CANCELLED,
  formatBytes,
  humanMessage,
  uploadFile,
  type StoredFile,
} from '../api/client'
import { Button, EmptyState } from './primitives'

/**
 * Las piezas que el Hito 3 pone a prueba: subir con progreso, y ver lo subido.
 *
 * Van en su propio fichero y no en `primitives.tsx` porque el criterio que ese
 * fichero se puso a sí mismo era «mientras quepa en algo que se lee de una
 * sentada»: con nueve componentes ya iba justo, y estos tres traen estado propio
 * y una petición en curso, que es otra clase de complejidad.
 *
 * Implementan las fichas de
 * [`upload-field.md`](../../../docs/frontend/design-system/components/upload-field.md),
 * [`file-gallery.md`](../../../docs/frontend/design-system/components/file-gallery.md)
 * y [`avatar.md`](../../../docs/frontend/design-system/components/avatar.md).
 */

/**
 * Los ocho estados. Dos de ellos son huecos en los que no hay barra que mover y
 * conviene no confundirlos:
 *
 * - `converting` va **antes** del primer byte —el HEIC se está decodificando
 *   aquí, en esta máquina (ADR-014)—, y en una foto de 12 MP es del orden de un
 *   segundo en un escritorio y de varios en un móvil.
 * - `processing` va **después** del último: es lo que el servidor tarda en
 *   inspeccionar, recodificar, hacer la miniatura y cerrar la fila.
 */
type UploadState =
  | 'idle'
  | 'selected'
  | 'converting'
  | 'uploading'
  | 'processing'
  | 'done'
  | 'error'
  | 'cancelled'

interface UploadFieldProps {
  label: string
  /** Decide los textos y el atributo `accept`. **No valida nada**: quien decide el tipo es el servidor. */
  accept?: 'image' | 'document'
  hint?: string
  /** El error de *adjuntar*, que viene de fuera. El de la subida lo lleva esto por dentro. */
  error?: string
  onUploaded: (file: StoredFile) => void
  onCleared?: () => void
  disabled?: boolean
  accessToken: string
}

/**
 * HEIC va en los dos, y no porque la lista blanca haya crecido: sigue teniendo
 * cuatro tipos. Va porque el cliente lo convierte antes de enviarlo (ADR-014), y
 * dejarlo en gris en el diálogo sería el mismo muro que el `415` con otra cara.
 */
const ACCEPT_ATTRIBUTE = {
  image: ['image/jpeg', 'image/png', 'image/webp', ...CONVERTIBLE_FILE_TYPES].join(','),
  document: [...ALLOWED_FILE_TYPES, ...CONVERTIBLE_FILE_TYPES].join(','),
}

const DEFAULT_HINT = {
  image: 'JPEG, PNG, WebP o HEIC. Hasta 25 MB.',
  document: 'JPEG, PNG, WebP, HEIC o PDF. Hasta 25 MB.',
}

/**
 * Elegir un fichero y **subirlo**. Nada más: produce un `fileId`.
 *
 * Lo que **no** hace es adjuntarlo, y esa frontera da forma a todo lo demás:
 * adjuntar es un paso aparte y posterior, precisamente para que se pueda estar
 * subiendo la foto mientras se rellena el resto del formulario. De ahí el estado
 * que hay que tener presente: **fichero ya subido, formulario aún sin enviar**,
 * que es lo normal y no una anomalía.
 *
 * **El 100 % no es el final.** `upload.onprogress` mide bytes enviados; cuando
 * llega al total, al servidor todavía le queda inspeccionar el contenido,
 * recodificar la imagen —que es lo que borra el EXIF con las coordenadas de la
 * casa—, generar la miniatura y cerrar la fila. Una barra clavada en 100 % se lee
 * como una aplicación colgada, así que ahí se pasa a «Procesando».
 *
 * **Al desmontarse no borra nada.** Un fichero subido y no adjuntado ocupa cuota
 * y lo retira el proceso diario a las 24 h; intentar borrarlo al salir es una
 * petición en el camino de salida, que es justo lo que no llega a ejecutarse en
 * un móvil que se bloquea.
 */
export function UploadField({
  label,
  accept = 'document',
  hint,
  error,
  onUploaded,
  onCleared,
  disabled = false,
  accessToken,
}: UploadFieldProps) {
  const inputId = useId()
  const [state, setState] = useState<UploadState>('idle')
  const [chosen, setChosen] = useState<File | null>(null)
  const [uploaded, setUploaded] = useState<StoredFile | null>(null)
  const [progress, setProgress] = useState(0)
  const [failure, setFailure] = useState<string | null>(null)
  const inFlight = useRef<AbortController | null>(null)
  /**
   * El foco vuelve **al `<input>`**, no al `<label>`.
   *
   * Parece al revés porque lo que se ve es la etiqueta, y es justo por eso: el
   * `<input>` está oculto a la vista pero no al foco, y el anillo lo pinta la
   * etiqueta con `focus-within`. Enfocar el `<label>` no haría nada —no es
   * focusable— y dejaría el foco en el `<body>`, que es como perderlo.
   */
  const trigger = useRef<HTMLInputElement | null>(null)

  function reset(next: UploadState) {
    setState(next)
    setChosen(null)
    setUploaded(null)
    setProgress(0)
    setFailure(null)
    onCleared?.()
  }

  async function start(file: File) {
    setChosen(file)
    setUploaded(null)
    setFailure(null)
    setProgress(0)
    setState('selected')

    const controller = new AbortController()
    inFlight.current = controller

    try {
      const stored = await uploadFile(
        file,
        accessToken,
        (fraction) => {
          setProgress(fraction)
          setState(fraction >= 1 ? 'processing' : 'uploading')
        },
        { onConverting: () => setState('converting'), signal: controller.signal },
      )
      setUploaded(stored)
      setState('done')
      onUploaded(stored)
    } catch (problem) {
      // La cancelación no es un error que haya que explicar: se pidió.
      if (problem instanceof Error && problem.message === UPLOAD_CANCELLED) {
        reset('cancelled')
        // Y el foco vuelve a donde estaba antes de pulsar. Sin esto se queda en
        // un botón que acaba de desaparecer del DOM, y quien navega con teclado
        // se encuentra de vuelta al principio de la página.
        trigger.current?.focus()
        return
      }
      setFailure(humanMessage(problem))
      setState('error')
    } finally {
      inFlight.current = null
    }
  }

  const isQuota = failure !== null && state === 'error' && failure.includes('espacio')

  return (
    <div className="flex flex-col gap-1.5">
      <label
        htmlFor={inputId}
        className={[
          'inline-flex min-h-touch w-fit cursor-pointer items-center justify-center gap-2 rounded-md',
          'border border-border bg-surface-raised px-4 py-2 text-body font-medium text-ink',
          'transition-colors hover:bg-surface-hover',
          // El anillo de foco de la capa base no se ve en un input invisible, así
          // que lo lleva la etiqueta, que es lo que se ve. Es la excepción
          // documentada a la regla de que el foco no se declara por componente.
          'focus-within:outline focus-within:outline-2 focus-within:outline-offset-2 focus-within:outline-accent',
          disabled ? 'pointer-events-none opacity-60' : '',
        ].join(' ')}
      >
        <UploadIcon />
        {label}
        {/* Oculto a la vista pero **no** al foco ni al árbol de accesibilidad:
            `display:none` lo sacaría de los dos. */}
        <input
          id={inputId}
          ref={trigger}
          type="file"
          accept={ACCEPT_ATTRIBUTE[accept]}
          disabled={disabled}
          className="sr-only"
          onChange={(event) => {
            const file = event.target.files?.[0]
            // El valor se limpia para que elegir **el mismo** fichero otra vez
            // vuelva a disparar el evento: si no, reintentar tras un error no
            // hace nada.
            event.target.value = ''
            if (file) void start(file)
          }}
        />
      </label>

      <p className="text-caption text-ink-muted">{hint ?? DEFAULT_HINT[accept]}</p>

      {chosen && (
        <div className="flex items-center gap-3 rounded-md border border-border bg-surface-raised p-2">
          <FileThumbnail file={uploaded} />

          <div className="min-w-0 flex-1">
            <p className="truncate text-body-sm text-ink">{chosen.name}</p>
            <p className="text-caption text-ink-muted">
              {formatBytes(uploaded?.sizeBytes ?? chosen.size)}
              {/* Se dice «convirtiendo» y no «procesando» porque no es lo
                  mismo ni pasa en el mismo sitio: esto ocurre aquí, antes de
                  enviar nada, y puede tardar más que la subida entera. */}
              {state === 'converting' && ' · Convirtiendo la foto…'}
              {state === 'uploading' && ` · ${Math.round(progress * 100)} %`}
              {state === 'processing' && ' · Procesando…'}
              {state === 'done' && ' · Subido'}
            </p>
          </div>

          {/* La misma posición para las dos acciones, que es lo que la ficha
              pide: **Cancelar** mientras sube y **Quitar** cuando ha terminado.
              `converting` se queda fuera a propósito y no por olvido — el
              decodificador de HEIC no ofrece por dónde abortar (ADR-014), así
              que un botón ahí mentiría. */}
          {state === 'uploading' && (
            <Button type="button" variant="ghost" onClick={() => inFlight.current?.abort()}>
              Cancelar
            </Button>
          )}

          {state === 'done' && (
            <Button type="button" variant="ghost" onClick={() => reset('idle')}>
              Quitar
            </Button>
          )}
        </div>
      )}

      {(state === 'converting' || state === 'uploading' || state === 'processing') && (
        <div
          role="progressbar"
          aria-label={
            state === 'converting'
              ? `Convirtiendo ${chosen?.name ?? 'la foto'}`
              : `Subiendo ${chosen?.name ?? 'el fichero'}`
          }
          aria-valuemin={0}
          aria-valuemax={100}
          // Indeterminada al convertir y al procesar: no hay porcentaje que dar,
          // y dar uno sería inventarlo. El decodificador de HEIC tampoco lo
          // ofrece --devuelve la imagen o no la devuelve--, así que aquí la
          // barra tampoco puede medir nada.
          aria-valuenow={
            state === 'processing' || state === 'converting' ? undefined : Math.round(progress * 100)
          }
          className="h-1 overflow-hidden rounded-full bg-surface-sunken"
        >
          <div
            className={[
              'h-full bg-accent transition-[width]',
              state === 'processing' || state === 'converting' ? 'animate-pulse' : '',
            ].join(' ')}
            style={{ width: `${Math.max(progress, 0.02) * 100}%` }}
          />
        </div>
      )}

      {state === 'cancelled' && <p className="text-caption text-ink-muted">Subida cancelada.</p>}

      {(failure || error) && (
        <p className="flex items-start gap-1.5 text-caption text-danger">
          <ErrorMark />
          <span>
            {failure ?? error}
            {/* La cuota no se arregla eligiendo otro fichero: hay que hacer
                sitio. Es el único de los tres errores de subida que necesita
                decir a dónde ir. */}
            {isQuota && (
              <>
                {' '}
                <a className="underline" href="/almacenamiento">
                  Ver qué ocupa espacio
                </a>
              </>
            )}
          </span>
        </p>
      )}
    </div>
  )
}

/** Lo que se pinta a la izquierda de la fila: la miniatura si la hay, y si no el icono del tipo. */
function FileThumbnail({ file }: { file: StoredFile | null }) {
  if (file?.thumbnailUrl) {
    return (
      <img
        src={file.thumbnailUrl}
        alt=""
        loading="lazy"
        className="size-10 shrink-0 rounded-md object-cover"
      />
    )
  }
  return (
    <span
      aria-hidden
      className="flex size-10 shrink-0 items-center justify-center rounded-md bg-surface-sunken text-ink-muted"
    >
      <DocumentIcon />
    </span>
  )
}

export interface GalleryItem {
  id: string
  /** Nula en un PDF: solo las imágenes tienen. La celda pinta entonces el icono del tipo. */
  thumbnailUrl: string | null
  name: string
  caption?: string
}

interface FileGalleryProps {
  label: string
  items: GalleryItem[]
  onOpen: (item: GalleryItem) => void
  onRemove?: (item: GalleryItem) => void
  /** Una miniatura ha caducado: la pantalla vuelve a pedir la entidad y recibe URL frescas. */
  onStale: () => void
  empty?: ReactNode
}

/**
 * La rejilla de lo que cuelga de una cosa.
 *
 * **Una URL de imagen no se guarda.** Las miniaturas llegan firmadas y caducan
 * con el access token que las generó, unos quince minutos. De ahí `onStale`: una
 * pestaña abierta un rato largo tiene las URL caducadas en pantalla, y cuando el
 * navegador falla al cargar una, lo que hay que hacer es **volver a pedir la
 * entidad** —que devuelve URL frescas—, no reintentar la misma URL, que va a
 * seguir dando 403 para siempre.
 */
export function FileGallery({ label, items, onOpen, onRemove, onStale, empty }: FileGalleryProps) {
  if (items.length === 0) {
    return <>{empty ?? <EmptyState title="Todavía no hay nada aquí." />}</>
  }

  return (
    <ul
      aria-label={label}
      // Lo que cambia con el ancho es el número de columnas, no la forma: una
      // rejilla de miniaturas no pasa a tabla porque su contenido no es texto en
      // columnas.
      className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4"
    >
      {items.map((item) => (
        <li key={item.id} className="flex flex-col gap-1 rounded-md border border-border bg-surface-raised p-2">
          <button
            type="button"
            onClick={() => onOpen(item)}
            className="aspect-square overflow-hidden rounded-md bg-surface-sunken"
          >
            {item.thumbnailUrl ? (
              <img
                src={item.thumbnailUrl}
                alt={item.name}
                loading="lazy"
                onError={onStale}
                className="size-full object-cover"
              />
            ) : (
              <span className="flex size-full items-center justify-center text-ink-muted">
                <DocumentIcon />
              </span>
            )}
          </button>

          <p className="truncate text-body-sm text-ink" title={item.name}>
            {item.name}
          </p>
          {item.caption && <p className="text-caption text-ink-muted">{item.caption}</p>}

          {onRemove && (
            <Button type="button" variant="ghost" className="self-start" onClick={() => onRemove(item)}>
              Quitar
            </Button>
          )}
        </li>
      ))}
    </ul>
  )
}

/**
 * El consumo de la cuota del hogar.
 *
 * Va como `meter` y no como `progressbar`: no mide el avance de una tarea sino
 * una medida dentro de un rango conocido, y el matiz lo aprovechan los lectores
 * de pantalla. Cambia de color por umbral **y** lo dice con texto, porque el
 * color no puede ser el único portador.
 */
export function QuotaMeter({ usedBytes, quotaBytes }: { usedBytes: number; quotaBytes: number }) {
  const fraction = quotaBytes > 0 ? Math.min(usedBytes / quotaBytes, 1) : 0
  const percent = Math.round(fraction * 100)
  const tone = fraction >= 0.9 ? 'bg-danger' : fraction >= 0.75 ? 'bg-warning' : 'bg-accent'

  return (
    <div className="flex flex-col gap-1.5">
      <div className="flex items-baseline justify-between gap-2">
        <span className="text-body-sm font-medium text-ink">Espacio del hogar</span>
        <span className="text-caption text-ink-muted">
          {formatBytes(usedBytes)} de {formatBytes(quotaBytes)} · {percent} %
        </span>
      </div>

      <div
        role="meter"
        aria-label="Espacio ocupado del hogar"
        aria-valuemin={0}
        aria-valuemax={quotaBytes}
        aria-valuenow={usedBytes}
        aria-valuetext={`${formatBytes(usedBytes)} de ${formatBytes(quotaBytes)}`}
        className="h-1 overflow-hidden rounded-full bg-surface-sunken"
      >
        <div className={`h-full ${tone}`} style={{ width: `${percent}%` }} />
      </div>

      {fraction >= 0.9 && (
        <p className="text-caption text-danger">
          Queda poco espacio. Borra algún fichero antes de que falle una subida.
        </p>
      )}
    </div>
  )
}

/**
 * El retrato de una persona.
 *
 * Cuando no hay imagen se pintan las iniciales, que **no** es un adorno: una
 * lista de personas con huecos vacíos no se lee, y el hueco es el estado normal
 * mientras nadie haya subido nada.
 */
export function Avatar({ name, url, size = 'md' }: { name: string; url: string | null; size?: 'sm' | 'md' | 'lg' }) {
  const box = { sm: 'size-8 text-caption', md: 'size-10 text-body-sm', lg: 'size-16 text-body' }[size]

  if (url) {
    return <img src={url} alt="" loading="lazy" className={`${box} shrink-0 rounded-full object-cover`} />
  }

  return (
    <span
      aria-hidden
      className={`${box} flex shrink-0 items-center justify-center rounded-full bg-accent-soft font-medium text-accent-ink`}
    >
      {initialsOf(name)}
    </span>
  )
}

function initialsOf(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean)
  const first = parts[0]?.[0]
  if (!first) return '?'
  const last = parts.length > 1 ? (parts[parts.length - 1]?.[0] ?? '') : ''
  return (first + last).toUpperCase()
}

/** El error de la API cuando llega como código conocido, para poder mirarlo desde fuera. */
export function isQuotaError(error: unknown): boolean {
  return error instanceof ApiError && error.code === 'STORAGE_QUOTA_EXCEEDED'
}

function UploadIcon() {
  return (
    <svg aria-hidden viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.75">
      <path d="M12 16V4m0 0L8 8m4-4 4 4" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M4 16v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2" strokeLinecap="round" />
    </svg>
  )
}

function DocumentIcon() {
  return (
    <svg aria-hidden viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.75">
      <path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z" strokeLinejoin="round" />
      <path d="M14 3v5h5" strokeLinejoin="round" />
    </svg>
  )
}

function ErrorMark() {
  return (
    <svg aria-hidden viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="1.75">
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7v6" strokeLinecap="round" />
      <circle cx="12" cy="16.5" r="0.75" fill="currentColor" stroke="none" />
    </svg>
  )
}
