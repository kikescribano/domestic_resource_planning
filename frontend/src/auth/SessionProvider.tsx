import {
  createContext,
  use,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react'

import { api, connectSessionRenewal, type TokenPair, type UserRole } from '../api/client'

/**
 * La sesión del navegador.
 *
 * El refresh token se guarda en `localStorage` y el access token **solo en
 * memoria**. No es simetría rota por descuido: el access token dura quince
 * minutos y se puede volver a pedir con el refresh, así que sacarlo del
 * almacenamiento reduce lo que un XSS podría llevarse sin costar nada a cambio.
 *
 * Guardar el refresh en `localStorage` es la parte discutible, y se acepta a
 * conciencia: la alternativa buena —cookie `HttpOnly` de dominio propio— exige
 * que el backend ponga la cookie y con ella vuelve el CSRF, que hoy no existe
 * porque la API no usa cookies. Es una decisión a revisar cuando nginx entre en
 * el Hito 3 y frontend y API compartan dominio de verdad.
 */

interface SessionClaims {
  identityId: string
  memberId: string
  householdId: string
  role: UserRole
}

interface Session {
  accessToken: string
  refreshToken: string
  claims: SessionClaims
}

interface SessionContextValue {
  session: Session | null
  isAuthenticated: boolean
  isAdmin: boolean
  /**
   * Cierto mientras se intenta reanudar con el token guardado, al cargar la
   * página. Existe para que las rutas protegidas **esperen** en vez de mandar a
   * la pantalla de entrar: sin él, recargar echaría a todo el mundo durante el
   * instante que tarda la renovación, y esa redirección ya no se deshace.
   */
  isResuming: boolean
  /** La sesión se perdió sola, no la cerró nadie. Lo lee la pantalla de entrar. */
  sessionExpired: boolean
  signIn: (tokens: TokenPair) => void
  signOut: () => Promise<void>
}

const SessionContext = createContext<SessionContextValue | null>(null)

const REFRESH_TOKEN_KEY = 'drp.refreshToken'

/**
 * Lee los claims del access token **sin comprobar la firma**.
 *
 * Es correcto y conviene decir por qué: el navegador no puede verificar nada
 * —no tiene la clave— y no lo necesita. Lo que hay aquí solo decide qué pinta la
 * interfaz; quien decide de verdad es el backend, que sí verifica la firma en
 * cada petición. Un usuario que se falsifique el rol en `localStorage` verá un
 * botón de más y recibirá un 403 al pulsarlo.
 */
function readClaims(accessToken: string): SessionClaims | null {
  try {
    const payload = accessToken.split('.')[1]
    if (!payload) return null
    const decoded = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/'))) as Record<
      string,
      string
    >
    if (!decoded.sub || !decoded.memberId || !decoded.householdId || !decoded.role) return null
    return {
      identityId: decoded.sub,
      memberId: decoded.memberId,
      householdId: decoded.householdId,
      role: decoded.role as UserRole,
    }
  } catch {
    return null
  }
}

export function SessionProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(null)
  const [sessionExpired, setSessionExpired] = useState(false)
  // Se arranca reanudando siempre que haya algo guardado que probar. Decidirlo
  // en el estado inicial y no en un efecto evita el parpadeo de un primer render
  // «sin sesión» que ya habría redirigido.
  const [isResuming, setIsResuming] = useState(() => storedRefreshToken() !== null)

  const signIn = useCallback((tokens: TokenPair) => {
    const claims = readClaims(tokens.accessToken)
    if (!claims) return
    localStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken)
    setSession({ accessToken: tokens.accessToken, refreshToken: tokens.refreshToken, claims })
    setSessionExpired(false)
  }, [])

  const signOut = useCallback(async () => {
    const current = session
    // Se limpia primero y se avisa al servidor después: si la llamada falla
    // —red caída, token ya caducado— la sesión local tiene que irse igualmente.
    // Lo contrario deja a alguien "dentro" de una sesión que creía cerrada.
    localStorage.removeItem(REFRESH_TOKEN_KEY)
    setSession(null)
    if (current) {
      await api.logout(current.refreshToken, current.accessToken).catch(() => undefined)
    }
  }, [session])

  /**
   * El puente con el cliente HTTP, que es quien detecta el `401` y necesita
   * poder renovar sin conocer React.
   *
   * El refresh token se lee de una `ref` y no del estado a propósito: el puente
   * se registra una sola vez, y leer del estado lo dejaría capturado en el valor
   * que hubiera al montar. Con eso, la segunda renovación de la sesión usaría el
   * token que la primera ya invalidó.
   */
  const live = useRef<Session | null>(session)
  live.current = session

  useEffect(() => {
    connectSessionRenewal({
      currentRefreshToken: () => live.current?.refreshToken ?? storedRefreshToken(),
      onRenewed: signIn,
      onSessionLost: () => {
        // Solo se anuncia como caducada si de verdad había sesión: si no la
        // había, quien llegue a la pantalla de entrar no ha perdido nada y no
        // hay nada que explicarle.
        if (live.current !== null) setSessionExpired(true)
        forgetStoredRefreshToken()
        setSession(null)
      },
    })
    return () => connectSessionRenewal(null)
  }, [signIn])

  /**
   * Reanudar al cargar la página, con lo que quedó guardado.
   *
   * Sin esto, el refresh token se escribía en `localStorage` y **no se leía
   * nunca**: recargar la pestaña echaba de la aplicación aunque la sesión
   * siguiera viva en el servidor.
   */
  useEffect(() => {
    if (!isResuming) return

    let cancelled = false
    const stored = storedRefreshToken()
    if (!stored) {
      setIsResuming(false)
      return
    }

    api
      .refresh(stored)
      .then((tokens) => {
        if (!cancelled) signIn(tokens)
      })
      .catch(() => {
        // Reanudar y fallar no es «te ha caducado la sesión»: es que lo guardado
        // ya no servía, y quien abre la aplicación no estaba haciendo nada que
        // se le haya interrumpido. Se limpia en silencio.
        if (!cancelled) forgetStoredRefreshToken()
      })
      .finally(() => {
        if (!cancelled) setIsResuming(false)
      })

    return () => {
      cancelled = true
    }
    // Solo al montar: reanudar es una vez por carga de página.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const value = useMemo<SessionContextValue>(
    () => ({
      session,
      isAuthenticated: session !== null,
      isAdmin: session?.claims.role === 'HOUSEHOLD_ADMIN',
      isResuming,
      sessionExpired,
      signIn,
      signOut,
    }),
    [session, isResuming, sessionExpired, signIn, signOut],
  )

  return <SessionContext value={value}>{children}</SessionContext>
}

export function useSession(): SessionContextValue {
  const context = use(SessionContext)
  if (!context) throw new Error('useSession necesita estar dentro de <SessionProvider>')
  return context
}

/**
 * La sesión ya establecida, para las pantallas que viven detrás del login.
 *
 * Existe para que esas pantallas no tengan que comprobar el nulo en cada uso: si
 * se llega ahí sin sesión, es un fallo de enrutado y conviene que se note.
 */
export function useAuthenticatedSession(): Session {
  const { session } = useSession()
  if (!session) throw new Error('Esta pantalla exige sesión iniciada')
  return session
}

/** Lo que quedó guardado de una visita anterior, para intentar reanudar. */
export function storedRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_TOKEN_KEY)
}

export function forgetStoredRefreshToken(): void {
  localStorage.removeItem(REFRESH_TOKEN_KEY)
}
