import { useQuery } from '@tanstack/react-query'
import {
  Bell,
  Blocks,
  BookOpen,
  Boxes,
  Handshake,
  HardDrive,
  House,
  MapPin,
  ShoppingCart,
  Store,
  Users,
  Warehouse,
  Wrench,
  type LucideIcon,
} from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { Link } from 'react-router'

import { api, formatBytes } from '../api/client'
import { useAuthenticatedSession, useSession } from '../auth/SessionProvider'
import { useHousehold } from './household'
import { useModules } from './modules'
import { BrandMark, Notice, PageHeading, StatusBadge } from '../ui/primitives'

/**
 * «Hogar»: el estado del hogar de un vistazo.
 *
 * La portada dejó de ser una bienvenida —dos tarjetas que repetían lo que el
 * usuario ya sabía— y pasó a ser un panel: **una tarjeta por pantalla de la
 * navegación**, cada una con el número que esa pantalla existe para vigilar.
 * El orden es fijo y es el de la navegación; la personalización por usuario
 * (orden y visibilidad) se aplazó a propósito y está anotada en el registro de
 * decisiones (4.1.7), porque exige preferencia en el backend y no un ajuste
 * del navegador.
 *
 * No hay endpoint de resumen: cada tarjeta pide la página mínima de un listado
 * que ya existe y pinta su `total` (ver los `count*` del cliente). Las de
 * módulo no llegan a pedir nada con el módulo apagado — la misma economía que
 * el guardián de ruta, con el catálogo que ya está en caché.
 */

/**
 * Dónde se guarda que el aviso de arranque ya se leyó. La misma casa que
 * `drp.nav`: es una preferencia del dispositivo y a propósito — persistirla
 * por usuario costaría contrato y tabla para un aviso que solo estorba una vez.
 */
const INTRO_KEY = 'drp.homeIntro'

/**
 * La ventana de «pronto» del panel: la misma que la pantalla de Almacén usa
 * para «caduca pronto», y que se aplica también a las revisiones que tocan.
 */
const SOON_DAYS = 30

export function HomePage() {
  const session = useAuthenticatedSession()
  const household = useHousehold()
  const { isAdmin } = useSession()
  const modules = useModules()

  const activeModules = (modules.data?.items ?? []).filter((module) => module.status === 'ACTIVE')
  const isActive = (key: string) => activeModules.some((module) => module.key === key)

  const [showIntro, setShowIntro] = useState(() => localStorage.getItem(INTRO_KEY) === null)
  const dismissIntro = () => {
    localStorage.setItem(INTRO_KEY, 'descartado')
    setShowIntro(false)
  }

  const notices = useIndicator('unread-notices', api.countUnreadNotices)
  const assets = useIndicator('assets', api.countAssets)
  const openLoans = useIndicator('open-loans', api.countOpenLoans)
  const overdueLoans = useIndicator('overdue-loans', api.countOverdueLoans)
  const users = useIndicator('users', api.countUsers)
  const articles = useIndicator('articles', api.countArticles)
  const locations = useIndicator('locations', api.countLocations)
  const storage = useQuery({
    queryKey: ['home-indicator', 'storage'],
    queryFn: () => api.getStorageUsage(session.accessToken),
  })

  const maintenanceDue = useIndicator(
    'maintenance-due',
    (token) => api.countMaintenanceDue(token, SOON_DAYS),
    isActive('MAINTENANCE'),
  )
  const shoppingPending = useIndicator('shopping-pending', api.countShoppingPending, isActive('PURCHASING'))
  const stockExpiring = useIndicator(
    'stock-expiring',
    (token) => api.countStockExpiring(token, SOON_DAYS),
    isActive('WAREHOUSE'),
  )
  const stockBelowMinimum = useIndicator(
    'stock-below-minimum',
    api.countStockBelowMinimum,
    isActive('WAREHOUSE'),
  )
  const suppliers = useIndicator('suppliers', api.countSuppliers, isActive('SUPPLIERS'))

  return (
    <>
      {/* La marca solo en móvil: en escritorio ya la enseña la barra lateral.
          La ven «Hogar» y «Más», las dos puertas de la barra inferior.
          Centrada: presentada a la izquierda parecía un desajuste del título
          de la pantalla. */}
      <BrandMark className="mb-6 flex justify-center md:hidden" />

      {/* La única cabecera dinámica del producto: el nombre del hogar vive en
          el título y no en una tarjeta que lo repita. Mientras carga dice
          «Hogar» a secas, que es preferible a un hueco que parpadea. */}
      <PageHeading
        title={household.data ? `Hogar ${household.data.name}` : 'Hogar'}
        icon={House}
      />

      {showIntro && (
        <Notice tone="info" title="Por dónde empezar" onDismiss={dismissIntro}>
          Crea primero las <Link to="/ubicaciones" className="underline">ubicaciones</Link> —la vivienda y
          lo que hay dentro— y el <Link to="/catalogo" className="underline">catálogo</Link> de lo que
          sueles tener en casa. Con eso, dar de alta algo en el{' '}
          <Link to="/inventario" className="underline">inventario</Link> es elegir de una lista.
        </Notice>
      )}

      <ul className="mt-6 grid list-none gap-4 sm:grid-cols-2 xl:grid-cols-3">
        <IndicatorCard
          to="/avisos"
          icon={Bell}
          label="Avisos"
          figure={figureOf(notices.data)}
          caption="sin leer"
        />
        <IndicatorCard
          to="/inventario"
          icon={Boxes}
          label="Inventario"
          figure={figureOf(assets.data)}
          caption={agreed(assets.data, 'cosa en casa', 'cosas en casa')}
        />
        <IndicatorCard
          to="/prestamos"
          icon={Handshake}
          label="Préstamos"
          figure={figureOf(openLoans.data)}
          caption="fuera de casa"
          detail={
            (overdueLoans.data ?? 0) > 0 && (
              <StatusBadge tone="overdue">
                {overdueLoans.data} {overdueLoans.data === 1 ? 'vencido' : 'vencidos'}
              </StatusBadge>
            )
          }
        />
        {isActive('MAINTENANCE') && (
          <IndicatorCard
            to="/mantenimiento"
            icon={Wrench}
            label="Mantenimiento"
            figure={figureOf(maintenanceDue.data)}
            caption={agreed(maintenanceDue.data, `revisión en ${SOON_DAYS} días`, `revisiones en ${SOON_DAYS} días`)}
          />
        )}
        {isActive('PURCHASING') && (
          <IndicatorCard
            to="/compras"
            icon={ShoppingCart}
            label="Compras"
            figure={figureOf(shoppingPending.data)}
            caption="por comprar"
          />
        )}
        {isActive('WAREHOUSE') && (
          <IndicatorCard
            to="/almacen"
            icon={Warehouse}
            label="Almacén"
            figure={figureOf(stockExpiring.data)}
            caption={agreed(stockExpiring.data, `caduca en ${SOON_DAYS} días`, `caducan en ${SOON_DAYS} días`)}
            detail={
              (stockBelowMinimum.data ?? 0) > 0 && (
                <StatusBadge tone="warning">
                  {stockBelowMinimum.data} bajo mínimo
                </StatusBadge>
              )
            }
          />
        )}
        <IndicatorCard
          to="/usuarios"
          icon={Users}
          label="Personas"
          figure={figureOf(users.data)}
          caption="en el hogar"
        />
        <IndicatorCard
          to="/catalogo"
          icon={BookOpen}
          label="Catálogo"
          figure={figureOf(articles.data)}
          caption={agreed(articles.data, 'artículo', 'artículos')}
        />
        <IndicatorCard
          to="/ubicaciones"
          icon={MapPin}
          label="Ubicaciones"
          figure={figureOf(locations.data)}
          caption={agreed(locations.data, 'sitio donde guardar', 'sitios donde guardar')}
        />
        {isActive('SUPPLIERS') && (
          <IndicatorCard
            to="/proveedores"
            icon={Store}
            label="Proveedores"
            figure={figureOf(suppliers.data)}
            caption={agreed(suppliers.data, 'contacto guardado', 'contactos guardados')}
          />
        )}
        <IndicatorCard
          to="/almacenamiento"
          icon={HardDrive}
          label="Archivo"
          figure={storage.data ? formatBytes(storage.data.usedBytes) : '—'}
          caption={storage.data ? `de ${formatBytes(storage.data.quotaBytes)} ocupados` : 'ocupados'}
          detail={storage.data && <StorageBar usedBytes={storage.data.usedBytes} quotaBytes={storage.data.quotaBytes} />}
        />
        {/* Solo para quien administra, como su parada de la navegación: quien
            no puede encender módulos no necesita saber cuántos hay apagados. */}
        {isAdmin && modules.data && (
          <IndicatorCard
            to="/modulos"
            icon={Blocks}
            label="Módulos del hogar"
            figure={activeModules.length}
            caption={`de ${modules.data.items.length} activos`}
          />
        )}
      </ul>
    </>
  )
}

/**
 * Un contador del panel. La clave lleva el nombre del indicador y no la ruta
 * que lo alimenta: dos indicadores pueden salir del mismo listado con filtros
 * distintos, como los dos de préstamos.
 *
 * `enabled` es la primera capa del gate para las tarjetas de módulo: con el
 * módulo apagado la petición no llega a salir, que es lo mismo que hace el
 * guardián de ruta con la pantalla entera.
 */
function useIndicator(name: string, fetcher: (accessToken: string) => Promise<number>, enabled = true) {
  const session = useAuthenticatedSession()

  return useQuery({
    queryKey: ['home-indicator', name],
    queryFn: () => fetcher(session.accessToken),
    enabled,
  })
}

/** Mientras no hay número se pinta una raya, no un cero: cero es un dato. */
function figureOf(value: number | undefined): ReactNode {
  return value ?? '—'
}

/**
 * La leyenda concuerda con el número: «1 cosas en casa» es el tipo de descuido
 * que delata un panel hecho deprisa. Mientras el número no ha llegado se usa el
 * plural, que es la forma neutra de la raya.
 */
function agreed(value: number | undefined, singular: string, plural: string): string {
  return value === 1 ? singular : plural
}

/**
 * Una tarjeta del panel: la pantalla a la que apunta, su número grande y la
 * palabra que lo explica. **Toda la tarjeta es el enlace**, y su nombre
 * accesible es el texto entero — «Avisos 3 sin leer» se entiende sin verla.
 */
function IndicatorCard({
  to,
  icon: Icon,
  label,
  figure,
  caption,
  detail,
}: {
  to: string
  icon: LucideIcon
  label: string
  figure: ReactNode
  caption: string
  detail?: ReactNode
}) {
  return (
    <li>
      {/* Los ` ` sueltos no pintan nada —un nodo de solo espacio no genera
          ítem de flex— pero son los que separan las palabras del nombre
          accesible: sin ellos, el enlace entero se lee «Avisos3sin leer». */}
      <Link
        to={to}
        className="flex h-full flex-col gap-1 rounded-lg border border-border-subtle bg-surface-raised p-4 transition-colors hover:bg-surface-hover"
      >
        <span className="flex items-center gap-2 text-body-sm font-medium text-ink-muted">
          <Icon size={18} strokeWidth={1.75} aria-hidden="true" className="shrink-0 text-accent-ink" />
          {label}
        </span>{' '}
        <span className="flex flex-wrap items-baseline gap-x-2">
          <span className="font-display text-display font-semibold text-ink">{figure}</span>{' '}
          <span className="text-body-sm text-ink-muted">{caption}</span>
        </span>
        {detail && <> <span className="mt-1 flex items-center gap-2">{detail}</span></>}
      </Link>
    </li>
  )
}

/**
 * La barra de la tarjeta de Archivo, `aria-hidden`: el dato ya lo dice el
 * texto de la tarjeta, y el `role="meter"` con su nombre vive en el
 * `QuotaMeter` de la pantalla de Archivo, que es donde se gestiona el espacio.
 * Los umbrales de color son los suyos.
 */
function StorageBar({ usedBytes, quotaBytes }: { usedBytes: number; quotaBytes: number }) {
  const fraction = quotaBytes > 0 ? Math.min(usedBytes / quotaBytes, 1) : 0
  const tone = fraction >= 0.9 ? 'bg-danger' : fraction >= 0.75 ? 'bg-warning' : 'bg-accent'

  return (
    <span aria-hidden="true" className="block h-1 w-full overflow-hidden rounded-full bg-surface-sunken">
      <span className={`block h-full ${tone}`} style={{ width: `${Math.round(fraction * 100)}%` }} />
    </span>
  )
}
