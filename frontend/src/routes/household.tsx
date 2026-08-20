import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Bell,
  Blocks,
  BookOpen,
  Boxes,
  CircleUserRound,
  Ellipsis,
  Handshake,
  HardDrive,
  House,
  LogOut,
  MapPin,
  Settings,
  Users,
} from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link, NavLink, Navigate, Outlet, useNavigate } from 'react-router'

import {
  ApiError,
  CONVERTIBLE_FILE_TYPES,
  api,
  formatDate,
  humanMessage,
  todayIn,
  type Household,
  type UserRole,
} from '../api/client'
import { useAuthenticatedSession, useSession } from '../auth/SessionProvider'
import { useActiveModuleScreens } from './modules'
import { Avatar } from '../ui/files'
import { BrandMark, Button, DangerZone, Field, Notice, PageHeading, Spinner, StatusBadge } from '../ui/primitives'

/**
 * Lo que vive detrás del login.
 *
 * El shell es responsive de verdad y no por defecto: **navegación inferior en
 * móvil** —al alcance del pulgar— y **lateral desde `md`**, que es el punto
 * donde el aire de la barra inferior empieza a costar más de lo que aporta.
 */

/**
 * Las cuatro paradas que caben en el pulgar: **las cuatro primeras de «Tu
 * hogar», en su mismo orden**. Desde el reagrupado del 2026-08-20 el orden de
 * la navegación es uno solo para las dos plataformas, así que la barra
 * inferior es un recorte de la columna y no otra lista: «Avisos» entró en el
 * pulgar y «Ubicaciones» salió hacia «Datos maestros».
 *
 * El tope sigue siendo aritmética: cinco paradas a 320 px —estas cuatro y
 * «Más»— y una sexta las deja por debajo de los 44 px que exige la dirección
 * visual. Lo que gana una pantalla nueva es sitio en la columna y en «Más»,
 * no un hueco en el pulgar.
 */
const THUMB_STOPS = new Set(['/', '/avisos', '/inventario', '/prestamos'])

/**
 * Los tres grupos de la navegación, en el orden en que se enseñan. **El orden
 * de los grupos y el de sus paradas es una decisión de producto, no una
 * casualidad del código**: lo de todos los días arriba, lo que estructura el
 * hogar en medio y lo que lo configura al final.
 *
 * - **Tu hogar** es la actividad: lo que pasa y lo que hay que atender.
 * - **Datos maestros** es lo que las demás pantallas consultan: quién, qué y
 *   dónde. El nombre es el término de un ERP a conciencia — este es doméstico,
 *   pero es un ERP.
 * - **Configuración** solo existe para quien administra: un miembro no puede
 *   tocar nada de lo que hay dentro, y un grupo entero de puertas cerradas es
 *   peor que ningún grupo.
 *
 * Las paradas de módulo son huecos con posición fija: si el módulo está activo
 * la parada aparece ahí, y si no, no está — la tercera capa del gate de la
 * ADR-010. Ya no hay un grupo «Módulos»: cada módulo vive donde su contenido
 * pertenece, y la puerta para encenderlos es «Módulos del hogar», dentro de
 * Configuración.
 *
 * «Cuenta» sigue sin ser una parada: es de la persona y no del hogar, y
 * acompaña a la marca junto a la salida directa. Ver [AccountControls].
 */
function useNavigationGroups() {
  const modules = useActiveModuleScreens()
  const { isAdmin } = useSession()

  const moduleStop = (key: string) =>
    modules
      .filter((screen) => screen.key === key)
      .map((screen) => ({ to: screen.path, label: screen.label, end: false, icon: screen.icon }))

  const groups = [
    {
      id: 'nav-home',
      label: 'Tu hogar',
      items: [
        { to: '/', label: 'Hogar', end: true, icon: House },
        { to: '/avisos', label: 'Avisos', end: false, icon: Bell },
        { to: '/inventario', label: 'Inventario', end: false, icon: Boxes },
        { to: '/prestamos', label: 'Préstamos', end: false, icon: Handshake },
        ...moduleStop('MAINTENANCE'),
        ...moduleStop('PURCHASING'),
        ...moduleStop('WAREHOUSE'),
      ],
    },
    {
      id: 'nav-master',
      label: 'Datos maestros',
      items: [
        { to: '/usuarios', label: 'Personas', end: false, icon: Users },
        { to: '/catalogo', label: 'Catálogo', end: false, icon: BookOpen },
        { to: '/ubicaciones', label: 'Ubicaciones', end: false, icon: MapPin },
        ...moduleStop('SUPPLIERS'),
        { to: '/almacenamiento', label: 'Archivo', end: false, icon: HardDrive },
      ],
    },
  ]

  if (isAdmin) {
    groups.push({
      id: 'nav-config',
      label: 'Configuración',
      items: [
        { to: '/configuracion', label: 'General', end: false, icon: Settings },
        { to: '/modulos', label: 'Módulos del hogar', end: false, icon: Blocks },
      ],
    })
  }

  return groups
}

/**
 * El icono de una parada de la navegación. Mismo juego (Lucide), mismo trazo
 * (1,75) y mismo tamaño en línea (20 px) que el resto del sistema, y
 * `aria-hidden` porque el significado lo lleva siempre la etiqueta de al lado:
 * el icono orienta, no nombra.
 */
const NAV_ICON = { size: 20, strokeWidth: 1.75, 'aria-hidden': true, className: 'shrink-0' } as const

/**
 * Cerrar la sesión y volver a la puerta. Compartido por los tres sitios desde
 * los que se sale: el bloque de la marca en escritorio, el apartado que cierra
 * «Más» en móvil y la sección de la pantalla de «Cuenta», que es la que
 * explica qué pasa al salir.
 */
function useSignOut() {
  const { signOut } = useSession()
  const navigate = useNavigate()
  return async () => {
    await signOut()
    navigate('/entrar', { replace: true })
  }
}

/**
 * La cuenta, junto a la marca y no entre las paradas.
 *
 * «Cuenta» dejó de ser una parada de la navegación: es de la persona y no del
 * hogar, así que va con la marca, con la salida directa al lado. Son un par de
 * medias filas centradas a propósito —no la anchura entera de una parada—
 * para que se lean como parte del bloque de identidad y no como dos entradas
 * más de la lista.
 */
function AccountControls({ className = '' }: { className?: string }) {
  const exit = useSignOut()
  const itemClass = 'flex min-h-touch flex-1 items-center justify-center gap-2 rounded-md px-3 text-body-sm'

  return (
    <div className={['items-center gap-1', className].join(' ')}>
      <NavLink
        to="/cuenta"
        className={({ isActive }) =>
          [
            itemClass,
            isActive ? 'bg-accent-soft font-medium text-accent-ink' : 'text-ink-muted hover:bg-surface-hover',
          ].join(' ')
        }
      >
        <CircleUserRound {...NAV_ICON} />
        <span>Cuenta</span>
      </NavLink>
      <button
        type="button"
        onClick={exit}
        className={[itemClass, 'text-ink-muted hover:bg-surface-hover'].join(' ')}
      >
        <LogOut {...NAV_ICON} />
        <span>Salir</span>
      </button>
    </div>
  )
}

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
 * **Qué día es hoy en el calendario del hogar**, o `null` mientras no se sepa.
 *
 * Sale de aquí y no de `new Date()` porque el día de una regla de calendario es
 * el del hogar y no el del navegador ni el de Greenwich: es el servidor quien
 * decide si una intervención es «del futuro», y lo hace contra
 * `households.time_zone`. Un cliente que enseñe otro día pone al usuario a
 * registrar una fecha que le van a rechazar —o, peor, una que le van a aceptar
 * y no es la que hizo.
 *
 * **Nulo es un estado normal y dura poco**: el shell pide el hogar al montar y
 * la clave es compartida, así que para cuando una pantalla de módulo tiene datos
 * que pintar esto ya tiene valor. Quien lo use pinta el campo vacío mientras
 * tanto, que es preferible a rellenarlo con un día inventado.
 */
export function useHouseholdToday(): string | null {
  const { data } = useHousehold()
  return data ? todayIn(data.timeZone) : null
}

/**
 * El aviso de que el hogar va a desaparecer, **en todas las pantallas**.
 *
 * Vive en el shell y no en «Hogar» porque durante la gracia todo sigue
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
        hogar puede cancelarlo desde <Link to="/configuracion" className="underline">General</Link>.
        Después no se podrá recuperar nada.
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
 * **Tres grupos y no una lista**: [useNavigationGroups] los define —actividad,
 * datos maestros y, solo para quien administra, configuración— y esta es la
 * pieza que los presenta sin renunciar al `<nav>` único. El orden es el mismo
 * en las dos plataformas: en móvil los grupos se disuelven
 * (`display: contents`) y quedan las cuatro primeras paradas de «Tu hogar»
 * ([THUMB_STOPS]) y «Más» — la barra es un recorte de la columna, no otra
 * lista—; desde `md` cada grupo es su lista rotulada. Cada enlace existe
 * **una sola vez** en el DOM, que es lo que el patrón defiende desde el Hito 0
 * de la Fase 2: dos copias son dos recorridos para quien navega con lector de
 * pantalla.
 *
 * **«Cuenta» no es una parada**: acompaña a la marca con la salida directa al
 * lado —en escritorio bajo el sello, dentro del banner y fuera del landmark de
 * navegación; en móvil, en el apartado que cierra «Más»—.
 */
function HouseholdShell() {
  const groups = useNavigationGroups()
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
        {/* La marca y la cuenta comparten bloque con un aire más corto que el
            de la columna: son la identidad —quién es DRP y quién está dentro—
            y no dos vecinos casuales de la lista de paradas. */}
        <div className="hidden md:flex md:flex-col md:gap-2">
          <BrandMark className="flex" />
          <AccountControls className="flex" />
        </div>

        <nav aria-label="Principal" className="flex md:flex-col md:gap-6">
          {/* Los rótulos de grupo se leen siempre aunque solo se vean desde
              `md`: `aria-labelledby` toma el texto de un elemento oculto igual
              que de uno visible, así que en móvil las listas siguen estando
              nombradas para quien navega sin verlas. Son párrafos y no
              encabezados a propósito: un `h2` aquí saldría antes que el `h1` del
              contenido y dejaría el documento con los niveles al revés.

              El `contents` de cada envoltorio y cada lista es lo que deja que
              a lo ancho del pulgar las paradas y el «Más», que viven en listas
              distintas, convivan en la misma fila; el DOM conserva la
              jerarquía entera —div, ul, li—, así que las listas siguen siendo
              listas para el lector de pantalla. */}
          {groups.map((group) => (
            <div key={group.id} className="contents md:block">
              <p id={group.id} className="hidden text-caption text-ink-subtle md:block">
                {group.label}
              </p>
              <ul className="contents md:mt-1 md:flex md:flex-col md:gap-1" aria-labelledby={group.id}>
                {group.items.map((item) => (
                  <li
                    key={item.to}
                    className={THUMB_STOPS.has(item.to) ? 'flex flex-1 md:flex-none' : 'hidden md:flex'}
                  >
                    <NavLink to={item.to} end={item.end} className={navLinkClass}>
                      <item.icon {...NAV_ICON} />
                      <span>{item.label}</span>
                    </NavLink>
                  </li>
                ))}
              </ul>
            </div>
          ))}

          {/* La quinta parada del móvil, y solo del móvil: en escritorio no
              hay nada detrás de ella que no esté ya en la columna. */}
          <ul className="contents">
            <li className="flex flex-1 md:hidden">
              <NavLink to="/mas" className={navLinkClass}>
                <Ellipsis {...NAV_ICON} />
                <span>Más</span>
              </NavLink>
            </li>
          </ul>
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
    // En móvil el icono va encima de la etiqueta —columna— y el texto baja a
    // `caption`: es lo que deja convivir las dos piezas dentro de los 44 px de
    // alto sin renunciar a la etiqueta, que nunca se quita (el icono orienta,
    // no nombra). Desde `md` vuelve a fila, icono delante.
    'flex min-h-touch min-w-touch w-full flex-1 flex-col items-center justify-center gap-0.5 px-1 py-1.5 text-caption',
    'md:flex-none md:flex-row md:justify-start md:gap-2.5 md:rounded-md md:px-3 md:py-2.5 md:text-body',
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
/** La fila-tarjeta de «Más»: una parada por fila, con su objetivo táctil entero. */
const MORE_ROW =
  'flex min-h-touch items-center gap-3 rounded-lg border border-border-subtle bg-surface-raised px-4 text-body text-ink'

export function MorePage() {
  const groups = useNavigationGroups()
  const exit = useSignOut()

  return (
    <>
      {/* La marca igual que en «Hogar»: solo en móvil, centrada. Las dos
          puertas de la barra inferior la enseñan, y así el móvil la ve
          entre por la que entre. */}
      <BrandMark className="mb-6 flex justify-center md:hidden" />

      <PageHeading title="Más" icon={Ellipsis} />

      {/* Los mismos grupos de la columna, menos lo que ya está en el pulgar.
          Un grupo que se queda sin paradas no pinta ni el rótulo. */}
      <nav aria-label="Resto del hogar">
        {groups.map((group) => {
          const items = group.items.filter((item) => !THUMB_STOPS.has(item.to))
          if (items.length === 0) return null

          return (
            <section key={group.id} className="mt-8 first:mt-0">
              <h2 className="font-display text-title-sm text-ink">{group.label}</h2>
              <ul className="mt-3 flex flex-col gap-2">
                {items.map((item) => (
                  <li key={item.to}>
                    <Link to={item.to} className={MORE_ROW}>
                      <item.icon {...NAV_ICON} />
                      {item.label}
                    </Link>
                  </li>
                ))}
              </ul>
            </section>
          )
        })}
      </nav>

      {/* El apartado de la persona cierra la pantalla, fuera del landmark de
          navegación: la salida es una acción y no un sitio al que ir. Solo en
          móvil — en escritorio la cuenta ya vive junto a la marca y a «Más»
          no llega nadie. */}
      <section className="mt-8 md:hidden">
        <h2 className="font-display text-title-sm text-ink">Tu cuenta</h2>
        <ul className="mt-3 flex flex-col gap-2">
          <li>
            <Link to="/cuenta" className={MORE_ROW}>
              <CircleUserRound {...NAV_ICON} />
              Cuenta
            </Link>
          </li>
          <li>
            <button type="button" onClick={exit} className={[MORE_ROW, 'w-full'].join(' ')}>
              <LogOut {...NAV_ICON} />
              Salir
            </button>
          </li>
        </ul>
      </section>
    </>
  )
}

export function HomePage() {
  const session = useAuthenticatedSession()
  const household = useHousehold()

  return (
    <>
      {/* La marca solo en móvil: en escritorio ya la enseña la barra lateral.
          La ven «Hogar» y «Más», las dos puertas de la barra inferior.
          Centrada: presentada a la izquierda parecía un desajuste del título
          de la pantalla. */}
      <BrandMark className="mb-6 flex justify-center md:hidden" />

      <PageHeading title="Hogar" icon={House} />
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
    </>
  )
}

/**
 * «General»: lo que configura el hogar entero, para quien lo administra.
 *
 * Nace con la baja del hogar, que hasta ahora cerraba la pantalla de «Hogar»:
 * la configuración es del papel y no de la portada, y con el grupo
 * «Configuración» en la navegación la zona de peligro tiene por fin una casa
 * que un miembro ni siquiera ve. Por eso el guardián: el grupo no se pinta
 * para un miembro, y la ruta tecleada a mano lo devuelve al inicio en lugar de
 * enseñarle una pantalla en la que no puede tocar nada. El aviso de la baja no
 * se pierde con el traslado — vive en el shell y lo ven todos.
 */
export function GeneralSettingsPage() {
  const { isAdmin } = useSession()
  const household = useHousehold()

  if (!isAdmin) return <Navigate to="/" replace />

  return (
    <>
      <PageHeading title="General" icon={Settings} />
      <p className="text-body-sm text-ink-muted">
        Lo que afecta al hogar entero. De momento, solo su baja.
      </p>

      {/* Mientras el hogar carga no se pinta nada: un hueco que aparece es
          menos ruidoso que uno que parpadea. */}
      {household.data && <HouseholdClosureSection household={household.data} />}
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
      <PageHeading title="Personas" icon={Users} />

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
              <StatusBadge tone={user.role === 'HOUSEHOLD_ADMIN' ? 'accent' : 'neutral'}>
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
  const exit = useSignOut()
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
      <PageHeading title="Cuenta" icon={CircleUserRound} />

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

      {/* La salida rápida vive con la marca —bajo el sello en escritorio,
          cerrando «Más» en móvil—, pero esta sección se queda: es la única que
          explica qué pasa al salir. Lo que sigue sin existir es un «Salir»
          entre las paradas de la lista, al lado de «Personas», esperando a que
          alguien lo pulse por error con el pulgar. */}
      <section className="mt-10 max-w-form border-t border-border-subtle pt-6">
        <h2 className="font-display text-title-sm text-ink">Cerrar sesión</h2>
        <p className="mt-1 text-body-sm text-ink-muted">
          Se cierra en este dispositivo. Las demás siguen abiertas.
        </p>
        <Button variant="secondary" className="mt-4" onClick={exit}>
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
  const exit = useSignOut()

  const close = useMutation({
    mutationFn: () => api.closeAccount(session.accessToken),
    // La sesión ya no vale para nada: salir es lo que limpia el estado del
    // cliente y revoca el refresh token que quedaba.
    onSuccess: exit,
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
 * El avatar admite HEIC por el mismo motivo que el campo de subida: no porque la
 * lista blanca haya crecido, sino porque el cliente lo convierte antes de enviar
 * (ADR-014). Y aquí el megabyte de tope importa más que allí — un HEIC de 12 MP
 * convertido a JPEG con calidad 0,90 ronda los 240 kB, así que entra con holgura
 * donde el original de varios megabytes no habría entrado.
 */
const AVATAR_ACCEPT = ['image/jpeg', 'image/png', 'image/webp', ...CONVERTIBLE_FILE_TYPES].join(',')

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
