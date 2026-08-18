import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { type ReactNode } from 'react'
import { Link } from 'react-router'

import { api, humanMessage, type HouseholdModule } from '../api/client'
import { useAuthenticatedSession, useSession } from '../auth/SessionProvider'
import { Button, Notice, PageHeading, Spinner, StatusBadge } from '../ui/primitives'

/**
 * Los módulos del hogar.
 *
 * Una sola consulta por sesión alimenta tres cosas: esta pantalla, la
 * navegación y el guardián de cada ruta de módulo. La clave de React Query es
 * fija —`['modules']`— así que las tres comparten la misma respuesta y no hay
 * una consulta por pantalla.
 */

/**
 * Dónde vive cada módulo en el cliente, y cómo se llama en un sitio donde no
 * caben cinco palabras.
 *
 * La ruta del navegador **no es la del contrato**: la API publica
 * `/api/v1/suppliers` y el cliente `/proveedores`, que es la misma frontera de
 * siempre —identificador en inglés, texto para el usuario en castellano— ya
 * aplicada a las rutas públicas del enrolamiento.
 *
 * Y la etiqueta corta tampoco es el `name` del catálogo: «Proveedores y
 * contactos de servicio» es el nombre que explica de qué va el módulo cuando
 * alguien decide si lo enciende, y no cabe en una barra inferior de móvil. El
 * nombre largo lo da el backend; el corto es decisión de presentación y vive
 * aquí.
 *
 * Un módulo del catálogo que no esté en este mapa se activa igual y no aparece
 * en la navegación: es lo que le pasa al módulo de prueba del backend, que no
 * tiene pantalla ninguna.
 */
export const MODULE_SCREENS: Record<string, { path: string; label: string; milestone?: string }> = {
  // Sin `milestone`: **este ya tiene pantalla**. El campo era la promesa
  // autoprogramada de «sus pantallas llegan en el Hito 2» y este es ese hito; se
  // retira en lugar de dejarlo apuntando al pasado, que es como una promesa
  // cumplida se convierte en documentación falsa.
  SUPPLIERS: { path: '/proveedores', label: 'Proveedores' },
  WAREHOUSE: { path: '/almacen', label: 'Almacén', milestone: 'Hito 3' },
  PURCHASING: { path: '/compras', label: 'Compras', milestone: 'Hito 4' },
  MAINTENANCE: { path: '/mantenimiento', label: 'Mantenimiento', milestone: 'Hito 5' },
}

/** El catálogo del hogar, compartido por todo lo que necesita saber qué está activo. */
export function useModules() {
  const session = useAuthenticatedSession()

  return useQuery({
    queryKey: ['modules'],
    queryFn: () => api.listModules(session.accessToken),
    // Lo que un hogar tiene encendido cambia muy de tarde en tarde, y de esta
    // respuesta depende la navegación entera: revalidarla a cada rato haría
    // parpadear enlaces sin que nadie haya cambiado nada.
    staleTime: 5 * 60_000,
  })
}

/** Los módulos activos que además tienen pantalla en el cliente. */
export function useActiveModuleScreens() {
  const { data } = useModules()

  return (data?.items ?? [])
    .filter((module) => module.status === 'ACTIVE')
    .flatMap((module) => {
      const screen = MODULE_SCREENS[module.key]
      return screen ? [{ key: module.key, ...screen }] : []
    })
}

export function ModulesPage() {
  const { isAdmin } = useSession()
  const modules = useModules()

  return (
    <>
      <PageHeading title="Módulos" />

      <p className="max-w-prose text-body text-ink-muted">
        DRP viene con lo básico de casa: las cosas, dónde están y a quién se las
        has prestado. Lo demás se enciende cuando hace falta.{' '}
        {isAdmin
          ? 'Apagar un módulo no borra nada: sus datos se quedan guardados y vuelven al encenderlo.'
          : 'Quien administra el hogar es quien los enciende y los apaga.'}
      </p>

      {modules.isPending && <Spinner label="Cargando los módulos del hogar…" />}
      {modules.isError && <Notice tone="danger">No se ha podido cargar la lista de módulos.</Notice>}

      {modules.data && (
        <ul className="mt-6 flex flex-col gap-3">
          {modules.data.items.map((module) => (
            <ModuleRow key={module.key} module={module} canDecide={isAdmin} />
          ))}
        </ul>
      )}
    </>
  )
}

function ModuleRow({ module, canDecide }: { module: HouseholdModule; canDecide: boolean }) {
  const session = useAuthenticatedSession()
  const queryClient = useQueryClient()
  const active = module.status === 'ACTIVE'
  const screen = MODULE_SCREENS[module.key]

  const decide = useMutation({
    mutationFn: () =>
      active
        ? api.deactivateModule(module.key, session.accessToken)
        : api.activateModule(module.key, session.accessToken),
    // Se invalida la consulta entera y no solo esta fila: de ella cuelga la
    // navegación, y dejarla con el valor viejo enseñaría un enlace a una ruta
    // que acaba de cerrarse.
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['modules'] }),
  })

  return (
    <li className="flex flex-wrap items-start justify-between gap-3 rounded-lg border border-border-subtle bg-surface-raised p-4">
      <div className="max-w-prose">
        <div className="flex flex-wrap items-center gap-2">
          <p className="text-body font-medium text-ink">{module.name}</p>
          {/* Estado con etiqueta y no solo con color, que es la regla 4 de la
              dirección visual. */}
          <StatusBadge tone={active ? 'success' : 'neutral'}>{active ? 'Activo' : 'Apagado'}</StatusBadge>
        </div>
        <p className="mt-1 text-body-sm text-ink-muted">{module.description}</p>

        {active && screen && (
          <Link to={screen.path} className="mt-2 inline-block text-body-sm text-accent-ink underline">
            Ir a {screen.label}
          </Link>
        )}

        {decide.isError && (
          <p className="mt-2 text-caption text-danger">{humanMessage(decide.error)}</p>
        )}
      </div>

      {canDecide && (
        <Button
          variant={active ? 'secondary' : 'primary'}
          onClick={() => decide.mutate()}
          busy={decide.isPending}
          busyLabel={active ? 'Apagando…' : 'Encendiendo…'}
        >
          {active ? `Apagar ${module.name}` : `Encender ${module.name}`}
        </Button>
      )}
    </li>
  )
}

/**
 * El guardián de una ruta de módulo, que desde el Hito 2 **envuelve** a la
 * pantalla de verdad en lugar de sustituirla.
 *
 * Entrar a mano en la ruta de un módulo apagado no lleva a un error sino a la
 * pantalla que **lo ofrece**: es el motivo por el que el backend responde `403`
 * y no `404`, y aquí es donde esa diferencia sirve de algo. Quien administra ve
 * el botón; quien no, a quién pedírselo.
 *
 * Esa mitad es **la tercera capa del gate** de la ADR-010 y no se puede perder al
 * darle contenido: sin ella, entrar a mano en la ruta de un módulo apagado
 * montaría su pantalla, que pediría datos y recibiría `403 MODULE_INACTIVE` — un
 * error donde el producto ofrece un botón. De ahí que los hijos se pinten
 * **solo** en la rama activa, y que ninguna pantalla de módulo tenga que
 * acordarse de comprobar nada.
 *
 * Sin hijos sigue sirviendo, y es lo que les pasa a los tres módulos que aún no
 * tienen pantalla: dice la verdad —está encendido y todavía no hay nada— en lugar
 * de fingir una lista vacía.
 *
 * No se llama a la API del módulo para averiguarlo: el catálogo ya está en la
 * caché de la sesión, así que la decisión no cuesta ninguna petición.
 */
export function ModuleScreen({ moduleKey, children }: { moduleKey: string; children?: ReactNode }) {
  const { isAdmin } = useSession()
  const session = useAuthenticatedSession()
  const queryClient = useQueryClient()
  const modules = useModules()

  const activate = useMutation({
    mutationFn: () => api.activateModule(moduleKey, session.accessToken),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['modules'] }),
  })

  if (modules.isPending) return <Spinner label="Comprobando los módulos del hogar…" />

  const module = modules.data?.items.find((candidate) => candidate.key === moduleKey)
  const screen = MODULE_SCREENS[moduleKey]

  if (!module) {
    return (
      <>
        <PageHeading title="Ese módulo no existe" />
        <Notice tone="danger">
          No hay ningún módulo con esa clave. Míralo en{' '}
          <Link to="/modulos" className="underline">
            los módulos del hogar
          </Link>
          .
        </Notice>
      </>
    )
  }

  if (module.status !== 'ACTIVE') {
    return (
      <>
        <PageHeading title={module.name} />
        <Notice tone="info" title="Este módulo está apagado">
          {module.description}
        </Notice>

        {isAdmin ? (
          <div className="mt-6 flex flex-col items-start gap-2">
            {activate.isError && <Notice tone="danger">{humanMessage(activate.error)}</Notice>}
            <Button
              variant="primary"
              onClick={() => activate.mutate()}
              busy={activate.isPending}
              busyLabel="Encendiendo…"
            >
              Encender {module.name}
            </Button>
            <p className="text-caption text-ink-muted">
              Se puede apagar cuando quieras, y apagarlo no borra nada.
            </p>
          </div>
        ) : (
          <p className="mt-6 text-body text-ink-muted">
            Pídeselo a quien administra el hogar: son los únicos que pueden encenderlo.
          </p>
        )}
      </>
    )
  }

  // Encendido y con pantalla: el guardián se aparta y la pinta. No añade
  // cabecera propia --la pone la pantalla-- porque dos `h1` en el mismo
  // documento dejan la jerarquía de encabezados mal.
  if (children) return <>{children}</>

  return (
    <>
      <PageHeading title={module.name} />
      {/* El módulo está encendido y todavía no tiene pantalla: su dominio llega
          con su hito, y hasta entonces esto dice la verdad en lugar de fingir
          una lista vacía. El reparto está en `phase-2-roadmap.md`. */}
      <Notice tone="info" title="Encendido, y todavía sin nada que enseñar">
        Este módulo está activo en tu hogar. Sus pantallas llegan en el{' '}
        {screen?.milestone ?? 'hito que le toca'} de la Fase 2; mientras tanto no
        hay nada que ver aquí.
      </Notice>
    </>
  )
}
