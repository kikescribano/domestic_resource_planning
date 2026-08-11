import { createContext, use, useCallback, useMemo, useState, type ReactNode } from 'react'

import { api, type TokenPair, type UserRole } from '../api/client'

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

  const signIn = useCallback((tokens: TokenPair) => {
    const claims = readClaims(tokens.accessToken)
    if (!claims) return
    localStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken)
    setSession({ accessToken: tokens.accessToken, refreshToken: tokens.refreshToken, claims })
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

  const value = useMemo<SessionContextValue>(
    () => ({
      session,
      isAuthenticated: session !== null,
      isAdmin: session?.claims.role === 'HOUSEHOLD_ADMIN',
      signIn,
      signOut,
    }),
    [session, signIn, signOut],
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
