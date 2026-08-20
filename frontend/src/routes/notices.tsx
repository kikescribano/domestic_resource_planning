import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Bell, Mail, MailOpen } from 'lucide-react'
import { useState } from 'react'

import { api, formatDate, humanMessage, type Notice } from '../api/client'
import { useAuthenticatedSession } from '../auth/SessionProvider'
import { Button, EmptyState, Notice as NoticeBanner, PageHeading, Spinner, StatusBadge } from '../ui/primitives'

/**
 * La bandeja de avisos del hogar.
 *
 * Lo que llega aquí lo escribe el **recorrido periódico** del backend, no una
 * persona: por eso no hay ningún formulario, ni forma de crear uno, ni de
 * borrarlo. Lo único que se hace con un aviso es leerlo.
 *
 * **Los avisos son del hogar y no de cada persona**, igual que el inventario:
 * si alguien ya se ocupó de la caducidad del yogur, el resto no tiene que volver
 * a verla. Eso es una decisión de producto y se nota en la pantalla —marcar
 * leído lo tacha para todos— así que conviene que el texto lo diga en vez de
 * dejar que se descubra.
 *
 * Arranca en **lo que falta por ver**, que es la pregunta con la que se entra;
 * el histórico está a un clic y no al revés.
 */
export function NoticesPage() {
  const { accessToken } = useAuthenticatedSession()
  const queryClient = useQueryClient()
  const [unreadOnly, setUnreadOnly] = useState(true)

  const notices = useQuery({
    queryKey: ['notices', { unreadOnly }],
    queryFn: () => api.listNotices(accessToken, unreadOnly),
  })

  // Las dos vistas comparten origen, así que al marcar hay que invalidar la
  // clave entera y no solo la que se está mirando: con el filtro puesto, dejar
  // la lista completa con el valor viejo la enseñaría sin tachar al cambiar.
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['notices'] })

  const markAll = useMutation({
    mutationFn: () => api.markAllNoticesRead(accessToken),
    onSuccess: refresh,
  })

  const items = notices.data?.items ?? []
  const unread = items.filter((notice) => notice.readAt === null).length

  return (
    <>
      <PageHeading
        title="Avisos"
        icon={Bell}
        action={
          unread > 0 ? (
            <Button variant="secondary" onClick={() => markAll.mutate()} busy={markAll.isPending} busyLabel="Marcando…">
              Marcar todo como leído
            </Button>
          ) : undefined
        }
      />

      <p className="max-w-prose text-body text-ink-muted">
        Lo que el sistema ha ido encontrando por su cuenta: préstamos que vencen
        y, según lo que tengas encendido, lo que cada módulo vigile. Son del
        hogar, así que marcar uno como leído lo marca para todos.
      </p>

      <div className="mt-4 flex flex-wrap gap-2">
        <Button
          type="button"
          variant={unreadOnly ? 'primary' : 'ghost'}
          aria-pressed={unreadOnly}
          onClick={() => setUnreadOnly(true)}
        >
          Sin leer
        </Button>
        <Button
          type="button"
          variant={unreadOnly ? 'ghost' : 'primary'}
          aria-pressed={!unreadOnly}
          onClick={() => setUnreadOnly(false)}
        >
          Todos
        </Button>
      </div>

      {notices.isPending && <Spinner label="Cargando los avisos del hogar…" />}
      {notices.isError && <NoticeBanner tone="danger">No se han podido cargar los avisos.</NoticeBanner>}
      {markAll.isError && <NoticeBanner tone="danger">{humanMessage(markAll.error)}</NoticeBanner>}

      {notices.data && items.length === 0 && (
        <EmptyState title={unreadOnly ? 'Nada pendiente' : 'Todavía no hay avisos'}>
          {unreadOnly
            ? 'Ya has visto todo lo que había. Lo leído sigue estando en «Todos».'
            : 'Cuando haya algo que contar aparecerá aquí, y además te llegará un resumen por correo.'}
        </EmptyState>
      )}

      {items.length > 0 && (
        // Con nombre, y no por adorno: la pantalla tiene dos botones de filtro
        // cuyo texto coincide con el de las etiquetas de estado, así que quien
        // navega por listas necesita saber cuál de ellas es la bandeja.
        <ul aria-label="Avisos del hogar" className="mt-6 flex flex-col gap-3">
          {items.map((notice) => (
            <NoticeRow key={notice.id} notice={notice} onRead={refresh} />
          ))}
        </ul>
      )}
    </>
  )
}

function NoticeRow({ notice, onRead }: { notice: Notice; onRead: () => void }) {
  const { accessToken } = useAuthenticatedSession()
  const read = notice.readAt !== null

  const mark = useMutation({
    mutationFn: () => api.markNoticeRead(notice.id, accessToken),
    onSuccess: onRead,
  })

  return (
    <li className="flex flex-wrap items-start justify-between gap-3 rounded-lg border border-border-subtle bg-surface-raised p-4">
      <div className="max-w-prose">
        <div className="flex flex-wrap items-center gap-2">
          <p className="text-body font-medium text-ink">{notice.title}</p>
          {/* El estado con etiqueta y no solo con color, que es la regla 4 de la
              dirección visual: sin leer y leído no se pueden distinguir solo por
              el peso del texto. */}
          <StatusBadge tone={read ? 'neutral' : 'accent'}>{read ? 'Leído' : 'Sin leer'}</StatusBadge>
        </div>
        <p className="mt-1 text-body-sm text-ink-muted">{notice.body}</p>
        <p className="mt-1 text-caption text-ink-subtle">{formatDate(notice.createdAt)}</p>

        {mark.isError && <p className="mt-2 text-caption text-danger">{humanMessage(mark.error)}</p>}
      </div>

      {/* El par de sobres cuenta el estado en la misma esquina de todas las
          tarjetas: cerrado y pulsable en las pendientes —abrir el sobre es
          marcarlo leído—, abierto y decorativo en las leídas, donde el
          significado ya lo lleva la etiqueta «Leído». En móvil la esquina es
          la inferior derecha —una fila propia, flex y no absoluto, para que el
          sitio se reserve solo— y desde `md`, la superior derecha. */}
      <span className="flex w-full justify-end md:w-auto">
        {read ? (
          // La misma anchura explícita que el botón de abajo: `w-11` y no
          // `min-w-touch`, porque el `px-0` del botón compite con el `px-4` de
          // la base y quién gana lo decide el orden del CSS generado — con la
          // anchura fijada, los dos sobres caen en el mismo eje siempre.
          <span aria-hidden="true" className="inline-flex min-h-touch w-11 items-center justify-center text-ink-subtle">
            <MailOpen size={20} strokeWidth={1.75} />
          </span>
        ) : (
          <Button
            variant="ghost"
            onClick={() => mark.mutate()}
            busy={mark.isPending}
            // El título dentro del nombre accesible: con quince avisos, quince
            // botones que digan «Marcar como leído» son quince destinos
            // idénticos para quien navega por lista de botones. Sin `busyLabel`
            // a propósito: cambiaría el icono por texto y la anchura daría un
            // salto; el estado en curso lo dice `aria-busy`.
            aria-label={`Marcar como leído: ${notice.title}`}
            title="Marcar como leído"
            // El `px-0` no gana nunca al `px-4` de la base —Tailwind ordena las
            // utilidades en la hoja, no en el atributo—, así que la caja de
            // contenido queda en 12 px: el `shrink-0` del icono es lo que lo
            // mantiene en sus 20 px, centrado sobre el relleno.
            className="w-11 px-0"
          >
            <Mail size={20} strokeWidth={1.75} aria-hidden="true" className="shrink-0" />
          </Button>
        )}
      </span>
    </li>
  )
}
