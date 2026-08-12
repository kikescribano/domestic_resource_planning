/**
 * El cliente HTTP del core.
 *
 * Es el único sitio del frontend que sabe que hay una API REST detrás; los
 * componentes hablan con hooks, y los hooks con esto (ADR-001, regla de
 * dependencia de Clean Architecture).
 *
 * Los tipos salen del contrato, que la ADR-007 declara fuente de verdad. Hoy se
 * escriben a mano contra `openapi.yaml`; generarlos es lo que esa ADR deja
 * preparado y llega cuando haya suficiente superficie que generar.
 */

/** Códigos de error de negocio, tal y como los enumera `openapi.yaml`. */
export type ApiErrorCode =
  | 'ALREADY_MEMBER'
  | 'ARTICLE_DUPLICATE'
  | 'ARTICLE_HAS_EXISTENCES'
  | 'ARTICLE_UNIT_IMMUTABLE'
  | 'ASSET_HAS_ACTIVE_LOAN'
  | 'ASSET_HAS_CHILDREN'
  | 'ASSET_LOCATION_CONFLICT'
  | 'ASSET_QUANTITY_NEGATIVE'
  | 'ASSET_QUANTITY_NOT_APPLICABLE'
  | 'CATEGORY_DUPLICATE'
  | 'CURRENT_PASSWORD_INVALID'
  | 'EMAIL_NOT_VERIFIED'
  | 'EXISTENCE_ALREADY_IN_LOCATION'
  | 'IDENTITY_ALREADY_MEMBER'
  | 'INTAKE_QUANTITY_NOT_POSITIVE'
  | 'INVITATION_ALREADY_PENDING'
  | 'INVITATION_TOKEN_INVALID'
  | 'LOCATION_CYCLE'
  | 'LOCATION_DUPLICATE'
  | 'LOCATION_HAS_ASSETS'
  | 'LOCATION_HAS_CHILDREN'
  | 'MERGE_ARTICLE_MISMATCH'
  | 'MERGE_ASSET_DEACTIVATED'
  | 'MERGE_NOT_CONSUMABLE'
  | 'MERGE_SAME_ASSET'
  | 'RATE_LIMITED'
  | 'RESET_TOKEN_INVALID'
  | 'USER_LAST_ADMIN'
  | 'VALIDATION_ERROR'
  | 'VERIFICATION_TOKEN_INVALID'
  | 'UNAUTHORIZED'
  | 'FORBIDDEN'
  | 'NOT_FOUND'
  | 'INTERNAL_ERROR'

/**
 * El texto que se le enseña a una persona para cada código.
 *
 * Vive aquí y no en cada pantalla porque el mismo código sale por varias: el
 * `message` que trae la respuesta es texto de diagnóstico —lo escribe el
 * backend, para el log— y no está pensado para leerse en una interfaz.
 */
const ERROR_MESSAGES: Partial<Record<ApiErrorCode, string>> = {
  ARTICLE_DUPLICATE: 'Ya hay un artículo con ese nombre o ese código de barras.',
  ARTICLE_HAS_EXISTENCES: 'No se puede retirar: todavía quedan existencias de este artículo.',
  ARTICLE_UNIT_IMMUTABLE: 'La unidad no se puede cambiar mientras haya existencias contadas en ella.',
  ASSET_HAS_ACTIVE_LOAN: 'No se puede dar de baja: está prestado.',
  ASSET_HAS_CHILDREN: 'No se puede dar de baja: todavía tiene cosas dentro.',
  ASSET_LOCATION_CONFLICT: 'Ahí no cabe: solo un asset duradero puede contener otros.',
  ASSET_QUANTITY_NEGATIVE: 'La cantidad no puede ser negativa.',
  ASSET_QUANTITY_NOT_APPLICABLE: 'Este asset no lleva cantidad.',
  CATEGORY_DUPLICATE: 'Ya hay una categoría con ese nombre.',
  EXISTENCE_ALREADY_IN_LOCATION: 'Ahí ya hay una existencia de este artículo. Únelas en lugar de moverla.',
  INTAKE_QUANTITY_NOT_POSITIVE: 'La cantidad que entra tiene que ser mayor que cero.',
  LOCATION_CYCLE: 'No se puede mover ahí: el destino está dentro de lo que quieres mover.',
  LOCATION_DUPLICATE: 'Ya hay algo con ese nombre en el mismo sitio.',
  LOCATION_HAS_ASSETS: 'No se puede borrar: todavía hay cosas guardadas ahí.',
  LOCATION_HAS_CHILDREN: 'No se puede borrar: cuelgan otras ubicaciones de ella.',
  MERGE_ARTICLE_MISMATCH: 'Solo se pueden unir existencias del mismo artículo.',
  MERGE_ASSET_DEACTIVATED: 'Alguna de las dos existencias está dada de baja.',
  MERGE_NOT_CONSUMABLE: 'Solo se unen existencias de consumible.',
  MERGE_SAME_ASSET: 'Origen y destino son la misma existencia.',
  NOT_FOUND: 'Eso ya no está.',
  FORBIDDEN: 'No tienes permiso para hacer esto.',
  RATE_LIMITED: 'Demasiados intentos. Prueba de nuevo en un momento.',
}

/**
 * Qué decirle al usuario de un error cualquiera.
 *
 * Con el genérico incluido: sin él, una pantalla que solo contempla los códigos
 * que espera enmudece ante un fallo de red o un `500`, y el usuario se queda
 * mirando un formulario que no hace nada.
 */
export function humanMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return ERROR_MESSAGES[error.code] ?? 'No se ha podido completar la operación.'
  }
  return 'No se ha podido conectar. Comprueba la conexión e inténtalo otra vez.'
}

export interface ApiErrorBody {
  code: ApiErrorCode
  message: string
  details?: Record<string, string>
}

/**
 * Un error que la API ha devuelto de forma deliberada.
 *
 * Se distingue de un fallo de red porque lleva `code`, y el `code` es lo que el
 * cliente trata: el `message` es texto de diagnóstico, no para mostrar tal cual.
 */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: ApiErrorCode,
    message: string,
    readonly details?: Record<string, string>,
    readonly retryAfterSeconds?: number,
  ) {
    super(message)
    this.name = 'ApiError'
  }

  /** Los errores de forma traen un campo por atributo rechazado. */
  fieldError(field: string): string | undefined {
    return this.details?.[field]
  }
}

export type UserRole = 'HOUSEHOLD_ADMIN' | 'HOUSEHOLD_MEMBER'

export interface TokenPair {
  accessToken: string
  refreshToken: string
  expiresIn: number
}

export interface User {
  id: string
  identityId: string
  name: string
  email: string
  phone: string | null
  role: UserRole
  avatarUrl: string | null
  lastLoginAt: string | null
  emailVerifiedAt: string | null
  deactivatedAt: string | null
}

export interface Invitation {
  id: string
  email: string
  role: UserRole
  expiresAt: string
  acceptedAt: string | null
  revokedAt: string | null
  createdAt: string
  createdBy: string | null
}

export interface Page<T> {
  items: T[]
  page: number
  size: number
  total: number
}

// --- Catálogo, ubicaciones y assets (Hito 2) ---------------------------------

export type MeasurementUnit = 'UNIT' | 'GRAM' | 'KILOGRAM' | 'MILLILITER' | 'LITER' | 'METER' | 'PACK'
export type LocationType = 'HOUSE' | 'FLOOR' | 'ROOM' | 'FURNITURE' | 'SHELF' | 'OTHER'
export type AssetType = 'DURABLE' | 'CONSUMABLE'
export type AssetStatus = 'AVAILABLE' | 'LENT' | 'DECOMMISSIONED'

/** Cómo se escribe cada unidad al mostrarla. Son datos, así que van en castellano. */
export const UNIT_LABELS: Record<MeasurementUnit, string> = {
  UNIT: 'unidades',
  GRAM: 'g',
  KILOGRAM: 'kg',
  MILLILITER: 'ml',
  LITER: 'l',
  METER: 'm',
  PACK: 'paquetes',
}

export const LOCATION_TYPE_LABELS: Record<LocationType, string> = {
  HOUSE: 'Vivienda',
  FLOOR: 'Planta',
  ROOM: 'Habitación',
  FURNITURE: 'Mueble',
  SHELF: 'Estante',
  OTHER: 'Otro',
}

export interface Category {
  id: string
  name: string
  notes: string | null
  createdAt: string
  retiredAt: string | null
}

export interface Article {
  id: string
  name: string
  categoryId: string
  category: string | null
  unit: MeasurementUnit
  brand: string | null
  model: string | null
  barcode: string | null
  packSize: number | null
  notes: string | null
  retiredAt: string | null
}

export interface Capacity {
  type: 'WEIGHT' | 'VOLUME' | 'UNITS'
  max: number
  unit: string
}

export interface Location {
  id: string
  name: string
  type: LocationType
  parentLocationId: string | null
  capacity: Capacity | null
  notes: string | null
}

/** La referencia polimórfica: los dos campos van juntos. */
export interface LocationRef {
  type: 'ASSET' | 'LOCATION'
  id: string
}

/** Un aviso que acompaña a una operación **con éxito**. No es un error. */
export interface ApiWarning {
  code: string
  message: string
}

export interface Asset {
  id: string
  name: string
  type: AssetType
  categoryId: string | null
  category: string | null
  articleId: string | null
  ownerId: string | null
  location: LocationRef | null
  status: AssetStatus
  quantity: number | null
  unit: MeasurementUnit | null
  serialNumber: string | null
  notes: string | null
  warnings: ApiWarning[]
}

export interface AssetFilters {
  locationId?: string
  parentAssetId?: string
  articleId?: string
  categoryId?: string
  type?: AssetType
  status?: AssetStatus
  withoutOwner?: boolean
}

function queryString(params: Record<string, string | number | boolean | undefined>): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== '') search.set(key, String(value))
  }
  const text = search.toString()
  return text ? `?${text}` : ''
}

const BASE_URL = '/api/v1'

interface RequestOptions {
  method?: string
  body?: unknown
  accessToken?: string | null
  /**
   * Interno. Lo pone a `false` lo que no debe disparar una renovación: la propia
   * renovación —o se llamaría a sí misma— y el cierre de sesión, que ya viene de
   * alguien que se va.
   */
  renewable?: boolean
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, accessToken, renewable = true } = options

  const response = await send(path, method, body, accessToken)

  // La renovación se intenta **solo** ante el 401 que significa «tu token ya no
  // vale», que el backend marca con el código `UNAUTHORIZED`. Los demás 401 son
  // credenciales rechazadas —`CURRENT_PASSWORD_INVALID` al cambiar la contraseña,
  // sin ir más lejos— y renovar ahí no arregla nada: gastaría un refresh token
  // por una errata y, si esa renovación fallase, echaría de la aplicación a
  // alguien que solo se equivocó tecleando.
  if (response.status === 401 && accessToken && renewable) {
    const rejection = await toApiError(response)
    if (rejection.code !== 'UNAUTHORIZED') throw rejection

    const renewed = await renewSession()
    // Sin renovación no hay segundo intento: `renewSession` ya ha avisado de que
    // la sesión se perdió, y repetir la petición solo daría el mismo 401.
    if (!renewed) throw rejection

    return readResponse<T>(await send(path, method, body, renewed))
  }

  return readResponse<T>(response)
}

function send(path: string, method: string, body: unknown, accessToken?: string | null) {
  return fetch(`${BASE_URL}${path}`, {
    method,
    headers: {
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
}

async function readResponse<T>(response: Response): Promise<T> {
  if (response.status === 204 || response.status === 202) {
    return undefined as T
  }

  if (!response.ok) {
    throw await toApiError(response)
  }

  return (await response.json()) as T
}

// --- Renovación de la sesión -------------------------------------------------

/**
 * Lo que el cliente HTTP necesita de la sesión para poder renovarla.
 *
 * Es un puente y no un `import` porque la dependencia va al revés: los
 * componentes hablan con este módulo, no este módulo con React. El
 * `SessionProvider` se registra al montar y se descuelga al desmontar.
 */
export interface SessionRenewal {
  /** El refresh token vivo, o el que quedó guardado de una visita anterior. */
  currentRefreshToken: () => string | null
  /** Hay par nuevo: la sesión se actualiza y el refresh anterior ya no vale. */
  onRenewed: (tokens: TokenPair) => void
  /** No se ha podido renovar: la sesión se acabó y hay que decirlo. */
  onSessionLost: () => void
}

let renewal: SessionRenewal | null = null
let inFlight: Promise<string | null> | null = null

export function connectSessionRenewal(next: SessionRenewal | null): void {
  renewal = next
  inFlight = null
}

/**
 * Renueva el par de tokens, **una sola vez** aunque se pida a la vez desde
 * varias peticiones.
 *
 * Compartir el intento no es una optimización: el backend **rota** los refresh
 * tokens, así que usar uno invalida el anterior. Con una renovación por petición,
 * cinco consultas caducando juntas —lo normal al volver a una pestaña abierta—
 * lanzarían cinco renovaciones, la primera invalidaría el token que usan las
 * otras cuatro, y esas cuatro cerrarían la sesión de alguien que la tenía
 * perfectamente viva.
 */
function renewSession(): Promise<string | null> {
  if (inFlight) return inFlight

  const attempt = (async (): Promise<string | null> => {
    const bridge = renewal
    const refreshToken = bridge?.currentRefreshToken() ?? null
    if (!bridge || !refreshToken) {
      bridge?.onSessionLost()
      return null
    }

    try {
      const tokens = await api.refresh(refreshToken)
      bridge.onRenewed(tokens)
      return tokens.accessToken
    } catch {
      // Caducado, revocado o ya rotado: da igual cuál de los tres, porque el
      // desenlace es el mismo y distinguirlos no cambiaría nada de lo que se
      // puede hacer a continuación.
      bridge.onSessionLost()
      return null
    }
  })()

  inFlight = attempt
  void attempt.finally(() => {
    if (inFlight === attempt) inFlight = null
  })
  return attempt
}

async function toApiError(response: Response): Promise<ApiError> {
  const retryAfter = response.headers.get('Retry-After')

  let body: ApiErrorBody | undefined
  try {
    body = (await response.json()) as ApiErrorBody
  } catch {
    // Un error sin cuerpo JSON no es un caso del contrato, pero puede llegar de
    // un proxy o de una caída: se trata como interno en lugar de reventar aquí.
    body = undefined
  }

  return new ApiError(
    response.status,
    body?.code ?? 'INTERNAL_ERROR',
    body?.message ?? 'No se ha podido completar la operación',
    body?.details,
    retryAfter ? Number(retryAfter) : undefined,
  )
}

export interface CreateHouseholdInput {
  name: string
  timeZone: string
  admin: { name: string; email: string; password: string }
}

export interface AcceptInvitationInput {
  token: string
  name?: string
  password?: string
}

export const api = {
  // --- Alta y verificación (sin autenticar) ---------------------------------
  createHousehold: (input: CreateHouseholdInput) =>
    request<void>('/households', { method: 'POST', body: input }),

  verifyEmail: (token: string) =>
    request<TokenPair>('/auth/verify-email', { method: 'POST', body: { token } }),

  resendVerification: (email: string) =>
    request<void>('/auth/resend-verification', { method: 'POST', body: { email } }),

  // --- Sesión ---------------------------------------------------------------
  login: (email: string, password: string) =>
    request<TokenPair>('/auth/login', { method: 'POST', body: { email, password } }),

  refresh: (refreshToken: string) =>
    request<TokenPair>('/auth/refresh', { method: 'POST', body: { refreshToken }, renewable: false }),

  logout: (refreshToken: string, accessToken: string) =>
    request<void>('/auth/logout', {
      method: 'POST',
      body: { refreshToken },
      accessToken,
      // Cerrar sesión con el access token ya caducado es normal --se cierra al
      // volver tras un rato-- y renovarlo para poder cerrarlo no tiene sentido.
      renewable: false,
    }),

  // --- Contraseñas ----------------------------------------------------------
  requestPasswordReset: (email: string) =>
    request<void>('/auth/password-reset', { method: 'POST', body: { email } }),

  resetPassword: (token: string, newPassword: string) =>
    request<TokenPair>('/auth/password-reset/confirm', {
      method: 'POST',
      body: { token, newPassword },
    }),

  changePassword: (currentPassword: string, newPassword: string, accessToken: string) =>
    request<void>('/auth/password', {
      method: 'POST',
      body: { currentPassword, newPassword },
      accessToken,
    }),

  // --- Usuarios e invitaciones ----------------------------------------------
  listUsers: (accessToken: string, includeDeactivated = false) =>
    request<Page<User>>(`/users?includeDeactivated=${includeDeactivated}`, { accessToken }),

  changeUserRole: (memberId: string, role: UserRole, accessToken: string) =>
    request<User>(`/users/${memberId}/roles`, { method: 'PATCH', body: { role }, accessToken }),

  deactivateUser: (memberId: string, accessToken: string) =>
    request<void>(`/users/${memberId}`, { method: 'DELETE', accessToken }),

  listInvitations: (accessToken: string) =>
    request<Page<Invitation>>('/invitations', { accessToken }),

  inviteUser: (email: string, role: UserRole, accessToken: string) =>
    request<Invitation>('/invitations', { method: 'POST', body: { email, role }, accessToken }),

  revokeInvitation: (invitationId: string, accessToken: string) =>
    request<void>(`/invitations/${invitationId}`, { method: 'DELETE', accessToken }),

  acceptInvitation: (input: AcceptInvitationInput) =>
    request<TokenPair>('/invitations/accept', { method: 'POST', body: input }),

  // --- Categorías -----------------------------------------------------------
  listCategories: (accessToken: string, includeRetired = false) =>
    request<Page<Category>>(`/categories${queryString({ includeRetired, size: 200 })}`, { accessToken }),

  createCategory: (body: { name: string; notes?: string }, accessToken: string) =>
    request<Category>('/categories', { method: 'POST', body, accessToken }),

  updateCategory: (id: string, body: { name: string; notes?: string }, accessToken: string) =>
    request<Category>(`/categories/${id}`, { method: 'PATCH', body, accessToken }),

  retireCategory: (id: string, accessToken: string) =>
    request<void>(`/categories/${id}`, { method: 'DELETE', accessToken }),

  // --- Artículos ------------------------------------------------------------
  listArticles: (
    accessToken: string,
    filters: { q?: string; categoryId?: string; includeRetired?: boolean } = {},
  ) => request<Page<Article>>(`/articles${queryString({ ...filters, size: 200 })}`, { accessToken }),

  getArticle: (id: string, accessToken: string) => request<Article>(`/articles/${id}`, { accessToken }),

  createArticle: (body: Record<string, unknown>, accessToken: string) =>
    request<Article>('/articles', { method: 'POST', body, accessToken }),

  updateArticle: (id: string, body: Record<string, unknown>, accessToken: string) =>
    request<Article>(`/articles/${id}`, { method: 'PATCH', body, accessToken }),

  retireArticle: (id: string, accessToken: string) =>
    request<void>(`/articles/${id}`, { method: 'DELETE', accessToken }),

  // --- Ubicaciones ----------------------------------------------------------
  /** Sin `parentLocationId` devuelve el hogar entero, que es como se pinta el árbol. */
  listLocations: (accessToken: string, parentLocationId?: string) =>
    request<Page<Location>>(`/locations${queryString({ parentLocationId, size: 200 })}`, { accessToken }),

  getLocation: (id: string, accessToken: string) => request<Location>(`/locations/${id}`, { accessToken }),

  listLocationChildren: (id: string, accessToken: string) =>
    request<Page<Location>>(`/locations/${id}/children${queryString({ size: 200 })}`, { accessToken }),

  createLocation: (body: Record<string, unknown>, accessToken: string) =>
    request<Location>('/locations', { method: 'POST', body, accessToken }),

  updateLocation: (id: string, body: Record<string, unknown>, accessToken: string) =>
    request<Location>(`/locations/${id}`, { method: 'PATCH', body, accessToken }),

  deleteLocation: (id: string, accessToken: string) =>
    request<void>(`/locations/${id}`, { method: 'DELETE', accessToken }),

  // --- Assets ---------------------------------------------------------------
  listAssets: (accessToken: string, filters: AssetFilters = {}) =>
    request<Page<Asset>>(`/assets${queryString({ ...filters, size: 200 })}`, { accessToken }),

  getAsset: (id: string, accessToken: string) => request<Asset>(`/assets/${id}`, { accessToken }),

  listAssetChildren: (id: string, accessToken: string) =>
    request<Page<Asset>>(`/assets/${id}/children${queryString({ size: 200 })}`, { accessToken }),

  createAsset: (body: Record<string, unknown>, accessToken: string) =>
    request<Asset>('/assets', { method: 'POST', body, accessToken }),

  /**
   * Dar entrada a un consumible. La misma llamada crea la existencia o **suma**
   * sobre la que ya haya en esa ubicación: quien la usa no tiene que saber cuál
   * de las dos cosas va a pasar.
   */
  registerIntake: (body: Record<string, unknown>, accessToken: string) =>
    request<Asset>('/assets/intake', { method: 'POST', body, accessToken }),

  /** Mover, ajustar cantidad y corregir la ficha, según lo que lleve el cuerpo. */
  updateAsset: (id: string, body: Record<string, unknown>, accessToken: string) =>
    request<Asset>(`/assets/${id}`, { method: 'PATCH', body, accessToken }),

  /** El asset de la ruta es el que desaparece; el del cuerpo, el que se queda con la suma. */
  mergeStockItems: (sourceId: string, targetAssetId: string, accessToken: string) =>
    request<Asset>(`/assets/${sourceId}/merge`, { method: 'POST', body: { targetAssetId }, accessToken }),

  decommissionAsset: (id: string, accessToken: string) =>
    request<void>(`/assets/${id}`, { method: 'DELETE', accessToken }),
}
