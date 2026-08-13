import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, NavLink, Navigate, Outlet, useNavigate } from 'react-router'

import { ApiError, api, humanMessage, type UserRole } from '../api/client'
import { useAuthenticatedSession, useSession } from '../auth/SessionProvider'
import { Avatar } from '../ui/files'
import { Button, Field, Notice, PageHeading, Spinner, StatusBadge } from '../ui/primitives'

/**
 * Lo que vive detrás del login.
 *
 * El shell es responsive de verdad y no por defecto: **navegación inferior en
 * móvil** —al alcance del pulgar— y **lateral desde `md`**, que es el punto
 * donde el aire de la barra inferior empieza a costar más de lo que aporta.
 */

const NAVIGATION = [
  { to: '/', label: 'Hogar', end: true },
  { to: '/inventario', label: 'Inventario', end: false },
  { to: '/ubicaciones', label: 'Sitios', end: false },
  { to: '/catalogo', label: 'Catálogo', end: false },
  { to: '/usuarios', label: 'Personas', end: false },
  { to: '/almacenamiento', label: 'Archivo', end: false },
  { to: '/cuenta', label: 'Cuenta', end: false },
]

export function RequireSession() {
  const { isAuthenticated, isResuming } = useSession()

  // Mientras se reanuda **no** se decide nada. Redirigir aquí sería irreversible:
  // para cuando la renovación termina, la navegación ya ocurrió y el usuario está
  // mirando la pantalla de entrar con una sesión perfectamente válida.
  if (isResuming) {
    return (
      <div className="flex min-h-dvh items-center justify-center px-gutter">
        <Spinner label="Recuperando tu sesión" />
      </div>
    )
  }

  if (!isAuthenticated) return <Navigate to="/entrar" replace />
  return <HouseholdShell />
}

/**
 * El shell.
 *
 * **Un solo `<nav>`, recolocado con CSS**, y no uno por breakpoint. La versión
 * fácil —una barra inferior con `md:hidden` y una lateral con `hidden md:flex`—
 * pinta los mismos enlaces dos veces en el DOM, y quien navega con lector de
 * pantalla se encuentra dos landmarks de navegación idénticos y recorre la lista
 * dos veces. Que uno esté oculto con `display:none` lo salva en la práctica, pero
 * basta un cambio de clase para que deje de estarlo, y el fallo no se ve mirando
 * la pantalla.
 *
 * Así que el mismo elemento es barra inferior en móvil --donde llega el pulgar--
 * y columna lateral desde `md`, donde ese aire empieza a costar más de lo que
 * aporta.
 */
function HouseholdShell() {
  return (
    <div className="flex min-h-dvh flex-col bg-surface md:flex-row">
      {/* Salto al contenido: primer tabulador de la página, visible solo al
          enfocarlo. Es lo que evita recorrer la navegación entera en cada
          pantalla con el teclado. */}
      <a
        href="#contenido"
        className="sr-only focus:not-sr-only focus:absolute focus:z-50 focus:m-2 focus:rounded-md focus:bg-surface-raised focus:px-4 focus:py-2 focus:text-ink"
      >
        Saltar al contenido
      </a>

      <header
        className={[
          'fixed inset-x-0 bottom-0 z-40 border-t border-border-subtle bg-surface-raised',
          'md:static md:flex md:w-64 md:shrink-0 md:flex-col md:gap-6 md:border-r md:border-t-0 md:p-gutter-lg',
        ].join(' ')}
      >
        <p className="hidden font-display text-title text-ink md:block">DRP</p>

        <nav aria-label="Principal" className="flex md:flex-col md:gap-1">
          {NAVIGATION.map((item) => (
            <NavLink key={item.to} to={item.to} end={item.end} className={navLinkClass}>
              {item.label}
            </NavLink>
          ))}
        </nav>
      </header>

      <main id="contenido" className="mx-auto w-full max-w-shell flex-1 px-gutter py-6 pb-24 md:pb-6">
        <Outlet />
      </main>
    </div>
  )
}

function navLinkClass({ isActive }: { isActive: boolean }) {
  return [
    'flex min-h-touch flex-1 items-center justify-center px-2 py-3 text-body-sm',
    'md:flex-none md:justify-start md:rounded-md md:px-3 md:text-body',
    // El estado activo no se dice solo con color: además del acento lleva peso
    // tipográfico y `aria-current`, que NavLink pone por su cuenta.
    isActive
      ? 'font-medium text-accent-ink md:bg-accent-soft'
      : 'text-ink-muted md:hover:bg-surface-hover',
  ].join(' ')
}

export function HomePage() {
  const session = useAuthenticatedSession()

  return (
    <>
      <PageHeading title="Tu hogar" />
      <Notice tone="info" title="Por dónde empezar">
        Crea primero las <Link to="/ubicaciones" className="underline">ubicaciones</Link> —la vivienda y
        lo que hay dentro— y el <Link to="/catalogo" className="underline">catálogo</Link> de lo que
        sueles tener en casa. Con eso, dar de alta algo en el{' '}
        <Link to="/inventario" className="underline">inventario</Link> es elegir de una lista.
      </Notice>
      <dl className="mt-6 grid gap-4 sm:grid-cols-2">
        <div className="rounded-lg border border-border-subtle bg-surface-raised p-4">
          <dt className="text-caption text-ink-muted">Tu papel aquí</dt>
          <dd className="mt-1 text-body text-ink">
            {session.claims.role === 'HOUSEHOLD_ADMIN' ? 'Administras el hogar' : 'Eres miembro del hogar'}
          </dd>
        </div>
      </dl>
    </>
  )
}

const ROLE_LABEL: Record<UserRole, string> = {
  HOUSEHOLD_ADMIN: 'Administra',
  HOUSEHOLD_MEMBER: 'Miembro',
}

export function UsersPage() {
  const session = useAuthenticatedSession()
  const { isAdmin } = useSession()
  const queryClient = useQueryClient()
  const [email, setEmail] = useState('')
  const [role, setRole] = useState<UserRole>('HOUSEHOLD_MEMBER')

  const users = useQuery({
    queryKey: ['users'],
    queryFn: () => api.listUsers(session.accessToken),
  })

  const invitations = useQuery({
    queryKey: ['invitations'],
    queryFn: () => api.listInvitations(session.accessToken),
    // Solo un administrador puede listarlas; pedirlas siendo miembro daría un
    // 403 previsible, y un error previsible no debe llegar a pedirse.
    enabled: isAdmin,
  })

  const invite = useMutation({
    mutationFn: () => api.inviteUser(email, role, session.accessToken),
    onSuccess: () => {
      setEmail('')
      void queryClient.invalidateQueries({ queryKey: ['invitations'] })
    },
  })

  const revoke = useMutation({
    mutationFn: (invitationId: string) => api.revokeInvitation(invitationId, session.accessToken),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['invitations'] }),
  })

  function submitInvitation(event: FormEvent) {
    event.preventDefault()
    invite.mutate()
  }

  return (
    <>
      <PageHeading title="Personas" />

      {users.isPending && <Spinner label="Cargando las personas del hogar…" />}
      {users.isError && <Notice tone="danger">No se ha podido cargar la lista.</Notice>}

      {users.data && (
        <ul className="flex flex-col gap-2">
          {users.data.items.map((user) => (
            <li
              key={user.id}
              className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-border-subtle bg-surface-raised p-3"
            >
              <div>
                <p className="text-body text-ink">{user.name}</p>
                <p className="text-caption text-ink-muted">{user.email}</p>
              </div>
              <StatusBadge tone={user.role === 'HOUSEHOLD_ADMIN' ? 'info' : 'neutral'}>
                {ROLE_LABEL[user.role]}
              </StatusBadge>
            </li>
          ))}
        </ul>
      )}

      {isAdmin && (
        <section className="mt-10">
          <h2 className="font-display text-title-sm text-ink">Invitar a alguien</h2>
          <p className="mt-1 text-body-sm text-ink-muted">
            Le llega un correo con un enlace. Nadie crea la cuenta de nadie: la
            contraseña la elige quien acepta.
          </p>

          <form onSubmit={submitInvitation} className="mt-4 flex flex-col gap-4" noValidate>
            {invite.isError && (
              <Notice tone="danger">
                {invite.error instanceof ApiError && invite.error.code === 'ALREADY_MEMBER'
                  ? 'Esa persona ya está en el hogar.'
                  : invite.error instanceof ApiError && invite.error.code === 'INVITATION_ALREADY_PENDING'
                    ? 'Ya hay una invitación pendiente para ese correo.'
                    : 'No se ha podido enviar la invitación.'}
              </Notice>
            )}
            {invite.isSuccess && <Notice tone="success">Invitación enviada.</Notice>}

            <Field
              label="Correo"
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              autoComplete="off"
              required
            />

            <div className="flex flex-col gap-1.5">
              <label htmlFor="rol-invitado" className="text-body-sm font-medium text-ink">
                Qué podrá hacer
              </label>
              <select
                id="rol-invitado"
                value={role}
                onChange={(event) => setRole(event.target.value as UserRole)}
                className="min-h-touch rounded-md border border-border bg-surface-raised px-3 text-body text-ink"
              >
                <option value="HOUSEHOLD_MEMBER">Miembro: gestiona cosas y préstamos</option>
                <option value="HOUSEHOLD_ADMIN">Administra: además gestiona personas</option>
              </select>
            </div>

            <Button type="submit" variant="primary" busy={invite.isPending} busyLabel="Enviando…">
              Enviar la invitación
            </Button>
          </form>

          {invitations.data && invitations.data.items.length > 0 && (
            <>
              <h3 className="mt-8 text-title-sm text-ink">Invitaciones pendientes</h3>
              <ul className="mt-3 flex flex-col gap-2">
                {invitations.data.items.map((invitation) => (
                  <li
                    key={invitation.id}
                    className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-border-subtle bg-surface-raised p-3"
                  >
                    <div>
                      <p className="text-body text-ink">{invitation.email}</p>
                      <p className="text-caption text-ink-muted">{ROLE_LABEL[invitation.role]}</p>
                    </div>
                    <Button
                      variant="danger"
                      onClick={() => revoke.mutate(invitation.id)}
                      busy={revoke.isPending}
                      busyLabel="Retirando…"
                    >
                      Retirar
                    </Button>
                  </li>
                ))}
              </ul>
            </>
          )}
        </section>
      )}
    </>
  )
}

export function AccountPage() {
  const session = useAuthenticatedSession()
  const { signOut } = useSession()
  const navigate = useNavigate()
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')

  const change = useMutation({
    mutationFn: () => api.changePassword(currentPassword, newPassword, session.accessToken),
    onSuccess: () => {
      setCurrentPassword('')
      setNewPassword('')
    },
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    change.mutate()
  }

  const failure = change.error instanceof ApiError ? change.error : null

  return (
    <>
      <PageHeading title="Tu cuenta" />

      <OwnAvatar accessToken={session.accessToken} />

      <form onSubmit={submit} className="flex max-w-form flex-col gap-4" noValidate>
        <h2 className="font-display text-title-sm text-ink">Cambiar la contraseña</h2>
        <p className="text-body-sm text-ink-muted">
          Se cerrarán tus otras sesiones. Esta se queda abierta.
        </p>

        {failure?.code === 'CURRENT_PASSWORD_INVALID' && (
          <Notice tone="danger">La contraseña actual no es correcta. No se ha cambiado nada.</Notice>
        )}
        {change.isSuccess && <Notice tone="success">Contraseña cambiada.</Notice>}

        <Field
          label="Contraseña actual"
          type="password"
          value={currentPassword}
          onChange={(event) => setCurrentPassword(event.target.value)}
          autoComplete="current-password"
          required
        />
        <Field
          label="Contraseña nueva"
          type="password"
          value={newPassword}
          onChange={(event) => setNewPassword(event.target.value)}
          autoComplete="new-password"
          hint="Mínimo 12 caracteres."
          error={failure?.fieldError('newPassword') ?? failure?.fieldError('password')}
          required
        />

        <Button type="submit" variant="primary" busy={change.isPending} busyLabel="Guardando…">
          Cambiar la contraseña
        </Button>
      </form>

      {/* Cerrar sesión vive aquí y no en la navegación: es una acción, no un
          sitio al que ir, y meterla entre los enlaces la deja al lado de
          «Personas» esperando a que alguien la pulse por error con el pulgar. */}
      <section className="mt-10 max-w-form border-t border-border-subtle pt-6">
        <h2 className="font-display text-title-sm text-ink">Cerrar sesión</h2>
        <p className="mt-1 text-body-sm text-ink-muted">
          Se cierra en este dispositivo. Las demás siguen abiertas.
        </p>
        <Button
          variant="secondary"
          className="mt-4"
          onClick={async () => {
            await signOut()
            navigate('/entrar', { replace: true })
          }}
        >
          Salir
        </Button>
      </section>
    </>
  )
}

/**
 * El avatar propio.
 *
 * Se parece a subir un fichero y es otra operación: `PUT /users/me/avatar`
 * **sube y sustituye a la vez**, con lo que no hay `fileId` que guardar ni paso
 * de adjuntar. Y responde `204` sin cuerpo, así que lo que hay que hacer después
 * es volver a leer a la persona: el `avatarUrl` no llega en la respuesta.
 *
 * No consume la cuota de ningún hogar —una identidad no pertenece a uno— y por
 * eso su tope es otro: 1 MB, y solo imagen.
 */
function OwnAvatar({ accessToken }: { accessToken: string }) {
  const queryClient = useQueryClient()
  const [problem, setProblem] = useState<string | null>(null)
  const [uploading, setUploading] = useState(false)

  const me = useQuery({
    queryKey: ['users'],
    queryFn: () => api.listUsers(accessToken),
  })

  const mine = me.data?.items[0] ?? null

  function refresh() {
    setProblem(null)
    void queryClient.invalidateQueries({ queryKey: ['users'] })
  }

  const remove = useMutation({
    mutationFn: () => api.deleteOwnAvatar(accessToken),
    onSuccess: refresh,
    onError: (error) => setProblem(humanMessage(error)),
  })

  async function choose(file: File) {
    setUploading(true)
    try {
      await api.setOwnAvatar(file, accessToken)
      refresh()
    } catch (error) {
      setProblem(humanMessage(error))
    } finally {
      setUploading(false)
    }
  }

  return (
    <section className="flex flex-col gap-3 border-b border-border-subtle pb-6">
      <h2 className="font-display text-title-sm text-ink">Tu foto</h2>

      <div className="flex items-center gap-4">
        <Avatar name={mine?.name ?? ''} url={mine?.avatarUrl ?? null} size="lg" />

        <div className="flex flex-col gap-1.5">
          <label className="inline-flex min-h-touch w-fit cursor-pointer items-center justify-center rounded-md border border-border bg-surface-raised px-4 py-2 text-body font-medium text-ink transition-colors hover:bg-surface-hover focus-within:outline focus-within:outline-2 focus-within:outline-offset-2 focus-within:outline-accent">
            {uploading ? 'Subiendo…' : mine?.avatarUrl ? 'Cambiar la foto' : 'Elegir una foto'}
            <input
              type="file"
              accept="image/jpeg,image/png,image/webp"
              disabled={uploading}
              className="sr-only"
              onChange={(event) => {
                const file = event.target.files?.[0]
                event.target.value = ''
                if (file) void choose(file)
              }}
            />
          </label>
          <p className="text-caption text-ink-muted">JPEG, PNG o WebP. Hasta 1 MB. Sustituye a la anterior.</p>
        </div>

        {mine?.avatarUrl && (
          <Button variant="ghost" onClick={() => remove.mutate()} busy={remove.isPending}>
            Quitarla
          </Button>
        )}
      </div>

      {problem && <Notice tone="danger">{problem}</Notice>}
    </section>
  )
}
