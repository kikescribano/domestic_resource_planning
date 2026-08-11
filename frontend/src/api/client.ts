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
  | 'CURRENT_PASSWORD_INVALID'
  | 'EMAIL_NOT_VERIFIED'
  | 'IDENTITY_ALREADY_MEMBER'
  | 'INVITATION_ALREADY_PENDING'
  | 'INVITATION_TOKEN_INVALID'
  | 'RATE_LIMITED'
  | 'RESET_TOKEN_INVALID'
  | 'USER_LAST_ADMIN'
  | 'VALIDATION_ERROR'
  | 'VERIFICATION_TOKEN_INVALID'
  | 'UNAUTHORIZED'
  | 'FORBIDDEN'
  | 'NOT_FOUND'
  | 'INTERNAL_ERROR'

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

const BASE_URL = '/api/v1'

interface RequestOptions {
  method?: string
  body?: unknown
  accessToken?: string | null
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, accessToken } = options

  const response = await fetch(`${BASE_URL}${path}`, {
    method,
    headers: {
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  })

  if (response.status === 204 || response.status === 202) {
    return undefined as T
  }

  if (!response.ok) {
    throw await toApiError(response)
  }

  return (await response.json()) as T
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
    request<TokenPair>('/auth/refresh', { method: 'POST', body: { refreshToken } }),

  logout: (refreshToken: string, accessToken: string) =>
    request<void>('/auth/logout', { method: 'POST', body: { refreshToken }, accessToken }),

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
}
