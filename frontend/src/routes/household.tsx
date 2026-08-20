import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, NavLink, Navigate, Outlet, useNavigate } from 'react-router'

import {
  ApiError,
  CONVERTIBLE_FILE_TYPES,
  api,
  formatDate,
  humanMessage,
  type Household,
  type UserRole,
} from '../api/client'
import { useAuthenticatedSession, useSession } from '../auth/SessionProvider'
import { useActiveModuleScreens } from './modules'
import { Avatar } from '../ui/files'
import { Button, DangerZone, Field, Notice, PageHeading, Spinner, StatusBadge } from '../ui/primitives'

/**
 * Lo que vive detrás del login.
 *
 * El shell es responsive de verdad y no por defecto: **navegación inferior en
 * móvil** —al alcance del pulgar— y **lateral desde `md`**, que es el punto
 * donde el aire de la barra inferior empieza a costar más de lo que aporta.
 */

/**
 * Las cuatro paradas de todos los días, que son las que caben en el pulgar.
 *
 * El corte entre estas y las de abajo no es de importancia sino de **frecuencia**:
 * lo de arriba se usa a diario y lo de abajo se toca al montar el hogar o cuando
 * hay algo que arreglar.
 */
const PRIMARY_NAVIGATION = [
  { to: '/', label: 'Hogar', end: true },
  { to: '/inventario', label: 'Inventario', end: false },
  { to: '/ubicaciones', label: 'Sitios', end: false },
  { to: '/prestamos', label: 'Préstamos', end: false },
]

/**
 * El resto del core. En escritorio va en la barra lateral; en móvil, en «Más».
 *
 * **La bandeja de avisos entra aquí y no en la barra inferior**, y no es una
 * apreciación: el tope medido son cinco paradas a 320 px —cuatro y «Más»— y una
 * sexta las deja por debajo de los 44 px que exige la dirección visual. Así que
 * lo que gana una pantalla nueva es sitio en la columna del escritorio y en
 * «Más», no un hueco en el pulgar.
 */
const SECONDARY_NAVIGATION = [
  { to: '/avisos', label: 'Avisos', end: false },
  { to: '/catalogo', label: 'Catálogo', end: false },
  { to: '/usuarios', label: 'Personas', end: false },
  { to: '/almacenamiento', label: 'Archivo', end: false },
  { to: '/cuenta', label: 'Cuenta', end: false },
]

/**
 * El hogar de la sesión, resuelto **una vez** y compartido.
 *
 * La clave de consulta es fija, así que React Query la reparte entre el shell
 * —que pinta el aviso de la baja— y la pantalla del hogar, que la pide y la
 * cancela. Es la misma economía que `listModules`.
 *
 * **No sale del token**, y esa es la razón entera de que exista esta lectura: el
 * access token vive quince minutos y se emite al entrar, así que un hogar
 * marcado después mentiría hasta la siguiente renovación —justo en la pantalla
 * que sirve para cancelar la baja.
 */
export function useHousehold() {
  const session = useAuthenticatedSession()

  return useQuery({
    queryKey: ['household'],
    queryFn: () => api.getCurrentHousehold(session.accessToken),
  })
}

/**
 * El aviso de que el hogar va a desaparecer, **en todas las pantallas**.
 *
 * Vive en el shell y no en «Tu hogar» porque durante la gracia todo sigue
 * funcionando igual: alguien puede pasarse treinta días dando de alta cosas en
 * el inventario sin volver a la pantalla del hogar y sin enterarse de nada. Ese
 * es exactamente el caso que la gracia existe para atrapar.
 *
 * Lleva **la fecha** y no «en 30 días», que es la diferencia entre saberlo y
 * tener que contar.
 */
function ClosureBanner({ household }: { household: Household }) {
  if (!household.closure) return null

  return (
    <div className="mb-6">
      <Notice tone="warning" title={`Este hogar se borrará el ${formatDate(household.closure.effectiveAt)}`}>
        Se pidió darlo de baja. Hasta esa fecha todo sigue funcionando igual, y quien administre el
        hogar puede cancelarlo desde <Link to="/" className="underline">Tu hogar</Link>. Después no se
        podrá recuperar nada.
      </Notice>
    </div>
  )
}

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
 *
 * **Y desde la Fase 2, dos grupos y no una lista.** Cuatro módulos llevaban la
 * navegación de ocho entradas a doce, y doce no caben en una barra inferior: a
 * 320 px, las ocho de antes ya daban 40 px de ancho por parada, por debajo de los
 * 44 px que la dirección visual exige de todo objetivo táctil. Así que el móvil
 * lleva **cuatro paradas y «Más»**, que es una pantalla con el resto; y el
 * escritorio, donde la columna no se queda corta, las enseña todas repartidas en
 * **el hogar** y **los módulos**. Un hogar sin módulos activos ve sus ocho
 * enlaces del core intactos y una novena entrada, que es la puerta para encender
 * alguno.
 */
function HouseholdShell() {
  const modules = useActiveModuleScreens()
  const household = useHousehold()

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

        <nav aria-label="Principal" className="flex md:flex-col md:gap-6">
          {/* Los rótulos de grupo se leen siempre aunque solo se vean desde
              `md`: `aria-labelledby` toma el texto de un elemento oculto igual
              que de uno visible, así que en móvil las dos listas siguen estando
              nombradas para quien navega sin verlas. Son párrafos y no
              encabezados a propósito: un `h2` aquí saldría antes que el `h1` del
              contenido y dejaría el documento con los niveles al revés. */}
          <p id="nav-core" className="hidden text-caption text-ink-subtle md:block">
            Tu hogar
          </p>
          <ul className="flex flex-1 md:flex-col md:gap-1" aria-labelledby="nav-core">
            {PRIMARY_NAVIGATION.map((item) => (
              <li key={item.to} className="flex flex-1 md:flex-none">
                <NavLink to={item.to} end={item.end} className={navLinkClass}>
                  {item.label}
                </NavLink>
              </li>
            ))}
            {SECONDARY_NAVIGATION.map((item) => (
              <li key={item.to} className="hidden md:flex">
                <NavLink to={item.to} end={item.end} className={navLinkClass}>
                  {item.label}
                </NavLink>
              </li>
            ))}
            {/* La quinta parada del móvil, y solo del móvil: en escritorio no
                hay nada detrás de ella que no esté ya en la columna. */}
            <li className="flex flex-1 md:hidden">
              <NavLink to="/mas" className={navLinkClass}>
                Más
              </NavLink>
            </li>
          </ul>

          <div className="hidden md:block">
            <p id="nav-modules" className="text-caption text-ink-subtle">
              Módulos
            </p>
            <ul className="mt-1 flex flex-col gap-1" aria-labelledby="nav-modules">
              {modules.map((module) => (
                <li key={module.key} className="flex">
                  <NavLink to={module.path} className={navLinkClass}>
                    {module.label}
                  </NavLink>
                </li>
              ))}
              <li className="flex">
                <NavLink to="/modulos" className={navLinkClass}>
                  Módulos del hogar
                </NavLink>
              </li>
            </ul>
          </div>
        </nav>
      </header>

      <main id="contenido" className="mx-auto w-full max-w-shell flex-1 px-gutter py-6 pb-24 md:pb-6">
        {/* Antes del `Outlet` y no dentro de cada pantalla: es del hogar entero,
            y repetirlo pantalla a pantalla es la forma segura de que falte en
            una. Mientras la consulta va, no se pinta nada — un hueco que
            aparece es menos ruidoso que uno que parpadea. */}
        {household.data && <ClosureBanner household={household.data} />}
        <Outlet />
      </main>
    </div>
  )
}

/**
 * `min-w-touch` además de `min-h-touch`, y no es simetría: es lo que la barra
 * inferior necesita y `flex-1` **no** garantiza.
 *
 * `flex-1` reparte el sobrante, no el total: un ítem cuyo texto es largo se
 * queda con su ancho de contenido y los demás se reparten lo que quede, así que
 * la parada más corta —«Más»— acaba siendo la más estrecha. Medido en la CI:
 * 43,95 px, por debajo del mínimo, mientras que en local pasaba porque la
 * tipografía del sistema mide distinto. Cinco paradas caben a 320 px, pero solo
 * si cada una tiene su suelo **declarado** en vez de heredado del texto.
 *
 * Y por eso el relleno lateral del móvil es `px-1` y no `px-2`: con el suelo
 * puesto, lo que decide si las cinco caben es la suma de sus anchos mínimos, y
 * ahí cada píxel de relleno se paga cinco veces. En la barra lateral, donde
 * sobra sitio, sigue siendo `px-3`.
 */
function navLinkClass({ isActive }: { isActive: boolean }) {
  return [
    'flex min-h-touch min-w-touch w-full flex-1 items-center justify-center px-1 py-3 text-body-sm',
    'md:flex-none md:justify-start md:rounded-md md:px-3 md:text-body',
    // El estado activo no se dice solo con color: además del acento lleva peso
    // tipográfico y `aria-current`, que NavLink pone por su cuenta.
    isActive
      ? 'font-medium text-accent-ink md:bg-accent-soft'
      : 'text-ink-muted md:hover:bg-surface-hover',
  ].join(' ')
}

/**
 * «Más», que es la mitad de la navegación que no cabe en el pulgar.
 *
 * Solo existe para el móvil: desde `md` la barra lateral las enseña todas y
 * nadie llega aquí. No es un cajón de sastre —lo que hay dentro son las paradas
 * del core que se tocan al montar el hogar, más los módulos— y por eso se
 * enumera entero en lugar de esconderse tras un menú.
 */
export function MorePage() {
  const modules = useActiveModuleScreens()

  return (
    <>
      <PageHeading title="Más" />

      <nav aria-label="Resto del hogar">
        <ul className="flex flex-col gap-2">
          {SECONDARY_NAVIGATION.map((item) => (
            <li key={item.to}>
              <Link
                to={item.to}
                className="flex min-h-touch items-center rounded-lg border border-border-subtle bg-surface-raised px-4 text-body text-ink"
              >
                {item.label}
              </Link>
            </li>
          ))}
        </ul>

        <h2 className="mt-8 font-display text-title-sm text-ink">Módulos</h2>
        <ul className="mt-3 flex flex-col gap-2">
          {modules.map((module) => (
            <li key={module.key}>
              <Link
                to={module.path}
                className="flex min-h-touch items-center rounded-lg border border-border-subtle bg-surface-raised px-4 text-body text-ink"
              >
                {module.label}
              </Link>
            </li>
          ))}
          <li>
            <Link
              to="/modulos"
              className="flex min-h-touch items-center rounded-lg border border-border-subtle bg-surface-raised px-4 text-body text-ink"
            >
              Módulos del hogar
            </Link>
          </li>
        </ul>
      </nav>
    </>
  )
}

export function HomePage() {
  const session = useAuthenticatedSession()
  const { isAdmin } = useSession()
  const household = useHousehold()

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
        {household.data && (
          <div className="rounded-lg border border-border-subtle bg-surface-raised p-4">
            <dt className="text-caption text-ink-muted">Cómo se llama</dt>
            <dd className="mt-1 text-body text-ink">{household.data.name}</dd>
          </div>
        )}
      </dl>

      {/* La baja solo la ve y la toca quien administra. Un miembro se entera por
          el aviso de arriba, que sí sale para todos: enterarse no es lo mismo
          que poder hacerlo. */}
      {isAdmin && household.data && <HouseholdClosureSection household={household.data} />}
    </>
  )
}

/**
 * Pedir la baja del hogar y cancelarla.
 *
 * Las dos mitades no se parecen a propósito. **Pedirla** es la zona de peligro,
 * con confirmación escrita, porque es irreversible una vez vencida la gracia.
 * **Cancelarla** es un botón normal: deshacer algo destructivo no merece
 * fricción, y ponérsela sería castigar el arrepentimiento, que es justo lo que
 * los treinta días existen para permitir.
 */
function HouseholdClosureSection({ household }: { household: Household }) {
  const session = useAuthenticatedSession()
  const queryClient = useQueryClient()

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['household'] })

  const request = useMutation({
    mutationFn: () => api.requestHouseholdClosure(session.accessToken),
    onSuccess: refresh,
  })

  const cancel = useMutation({
    mutationFn: () => api.cancelHouseholdClosure(session.accessToken),
    onSuccess: refresh,
  })

  if (household.closure) {
    return (
      <section className="mt-10 flex max-w-form flex-col gap-4 border-t border-border-subtle pt-6">
        <h2 className="font-display text-title-sm text-ink">Baja del hogar</h2>
        <p className="text-body-sm text-ink-muted">
          Pedida. El hogar se borrará el <strong>{formatDate(household.closure.effectiveAt)}</strong> y
          hasta entonces todo sigue funcionando igual.
        </p>
        {cancel.isError && <Notice tone="danger">{humanMessage(cancel.error)}</Notice>}
        <Button
          variant="primary"
          className="w-fit"
          busy={cancel.isPending}
          busyLabel="Cancelando…"
          onClick={() => cancel.mutate()}
        >
          Cancelar la baja
        </Button>
      </section>
    )
  }

  return (
    <DangerZone
      title="Dar de baja el hogar"
      confirmation={household.name}
      confirmationLabel={`Escribe «${household.name}» para confirmarlo`}
      action="Dar de baja el hogar"
      busyLabel="Dando de baja…"
      busy={request.isPending}
      error={request.isError ? humanMessage(request.error) : null}
      onConfirm={() => request.mutate()}
    >
      <p>
        Se borrará el hogar entero <strong>30 días después</strong>: las cosas del inventario, las
        ubicaciones, los préstamos, el catálogo, los documentos, las fotos y las personas que lo
        comparten.
      </p>
      <p>
        Durante esos 30 días no cambia nada y cualquiera que administre el hogar puede cancelarlo.
        Pasada la fecha <strong>no se puede recuperar nada</strong>.
      </p>
    </DangerZone>
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

      <CloseAccountSection />
    </>
  )
}

/**
 * Cerrar la cuenta, que **no es dejar el hogar ni cerrar sesión**.
 *
 * Va al final de la pantalla de la persona y no en la del hogar, porque el
 * sujeto es la identidad: sus credenciales y su foto, que la acompañan a
 * cualquier hogar. La casa **no se va con ella** — se va la persona y el hogar
 * sigue con quien quede.
 *
 * La confirmación es una palabra fija y no un nombre: aquí no hay ningún nombre
 * propio que copiar que no sea el de la propia persona, y hacerle teclear su
 * nombre para irse tiene un tono que no queremos.
 */
function CloseAccountSection() {
  const session = useAuthenticatedSession()
  const { signOut } = useSession()
  const navigate = useNavigate()

  const close = useMutation({
    mutationFn: () => api.closeAccount(session.accessToken),
    onSuccess: async () => {
      // La sesión ya no vale para nada: el `signOut` es lo que limpia el estado
      // del cliente y revoca el refresh token que quedaba.
      await signOut()
      navigate('/entrar', { replace: true })
    },
  })

  return (
    <DangerZone
      title="Cerrar tu cuenta"
      confirmation="CERRAR"
      confirmationLabel="Escribe «CERRAR» para confirmarlo"
      action="Cerrar mi cuenta"
      busyLabel="Cerrando…"
      busy={close.isPending}
      error={close.isError ? humanMessage(close.error) : null}
      onConfirm={() => close.mutate()}
    >
      <p>
        Dejarás de poder entrar, aquí y en cualquier otro hogar, y{' '}
        <strong>se borrará tu foto</strong>. Las cosas del hogar se quedan: son del hogar y no tuyas.
      </p>
      <p>
        Lo que tuvieras a tu nombre en el inventario queda <strong>sin propietario</strong>, para que
        el hogar lo reasigne cuando quiera. Esto no da de baja el hogar.
      </p>
    </DangerZone>
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

/**
 * El avatar admite HEIC por el mismo motivo que el campo de subida: no porque la
 * lista blanca haya crecido, sino porque el cliente lo convierte antes de enviar
 * (ADR-014). Y aquí el megabyte de tope importa más que allí — un HEIC de 12 MP
 * convertido a JPEG con calidad 0,90 ronda los 240 kB, así que entra con holgura
 * donde el original de varios megabytes no habría entrado.
 */
const AVATAR_ACCEPT = ['image/jpeg', 'image/png', 'image/webp', ...CONVERTIBLE_FILE_TYPES].join(',')
function OwnAvatar({ accessToken }: { accessToken: string }) {
  const queryClient = useQueryClient()
  const [problem, setProblem] = useState<string | null>(null)
  const [uploading, setUploading] = useState(false)
  /** El hueco de antes del primer byte, cuando lo elegido era un HEIC y hay que decodificarlo aquí. */
  const [converting, setConverting] = useState(false)

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
    setConverting(false)
    try {
      // El avatar no pasa por `UploadField` --tiene su propio `<input>`, porque
      // sustituye en vez de acumular-- pero sí por `uploadFile`, así que la
      // conversión de HEIC le llega sola (ADR-014). Lo único suyo es decirlo.
      await api.setOwnAvatar(file, accessToken, undefined, () => setConverting(true))
      refresh()
    } catch (error) {
      setProblem(humanMessage(error))
    } finally {
      setUploading(false)
      setConverting(false)
    }
  }

  return (
    <section className="flex flex-col gap-3 border-b border-border-subtle pb-6">
      <h2 className="font-display text-title-sm text-ink">Tu foto</h2>

      <div className="flex items-center gap-4">
        <Avatar name={mine?.name ?? ''} url={mine?.avatarUrl ?? null} size="lg" />

        <div className="flex flex-col gap-1.5">
          <label className="inline-flex min-h-touch w-fit cursor-pointer items-center justify-center rounded-md border border-border bg-surface-raised px-4 py-2 text-body font-medium text-ink transition-colors hover:bg-surface-hover focus-within:outline focus-within:outline-2 focus-within:outline-offset-2 focus-within:outline-accent">
            {converting
              ? 'Convirtiendo…'
              : uploading
                ? 'Subiendo…'
                : mine?.avatarUrl
                  ? 'Cambiar la foto'
                  : 'Elegir una foto'}
            <input
              type="file"
              accept={AVATAR_ACCEPT}
              disabled={uploading}
              className="sr-only"
              onChange={(event) => {
                const file = event.target.files?.[0]
                event.target.value = ''
                if (file) void choose(file)
              }}
            />
          </label>
          <p className="text-caption text-ink-muted">
            JPEG, PNG, WebP o HEIC. Hasta 1 MB. Sustituye a la anterior.
          </p>
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
