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
  | 'DOCUMENT_CONTENT_INVALID'
  | 'DOCUMENT_TARGET_INVALID'
  | 'EMAIL_NOT_VERIFIED'
  | 'EXISTENCE_ALREADY_IN_LOCATION'
  | 'FILE_ALREADY_ATTACHED'
  | 'FILE_IN_USE'
  | 'FILE_TOO_LARGE'
  | 'FILE_TYPE_NOT_ALLOWED'
  | 'IDENTITY_ALREADY_MEMBER'
  | 'INTAKE_QUANTITY_NOT_POSITIVE'
  | 'INVITATION_ALREADY_PENDING'
  | 'INVITATION_TOKEN_INVALID'
  | 'LOAN_ALREADY_RETURNED'
  | 'LOAN_ASSET_ALREADY_LENT'
  | 'LOAN_ASSET_NOT_DURABLE'
  | 'LOCATION_CYCLE'
  | 'LOCATION_DUPLICATE'
  | 'LOCATION_HAS_ASSETS'
  | 'LOCATION_HAS_CHILDREN'
  | 'MERGE_ARTICLE_MISMATCH'
  | 'MERGE_ASSET_DEACTIVATED'
  | 'MERGE_NOT_CONSUMABLE'
  | 'MERGE_SAME_ASSET'
  | 'MODULE_INACTIVE'
  | 'RATE_LIMITED'
  | 'RESET_TOKEN_INVALID'
  | 'STORAGE_QUOTA_EXCEEDED'
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
  DOCUMENT_CONTENT_INVALID: 'Un documento es un enlace o un fichero, no las dos cosas.',
  DOCUMENT_TARGET_INVALID: 'Un documento cuelga de una cosa o de un artículo, no de las dos.',
  FILE_ALREADY_ATTACHED: 'Ese fichero ya está adjunto en otro sitio. Sube otro.',
  FILE_IN_USE: 'No se puede borrar: todavía cuelga de algo. Quítalo de ahí primero.',
  FILE_TOO_LARGE: 'El fichero pesa demasiado. El máximo son 25 MB.',
  FILE_TYPE_NOT_ALLOWED: 'Ese tipo de fichero no se admite. Solo JPEG, PNG, WebP y PDF.',
  EXISTENCE_ALREADY_IN_LOCATION: 'Ahí ya hay una existencia de este artículo. Únelas en lugar de moverla.',
  INTAKE_QUANTITY_NOT_POSITIVE: 'La cantidad que entra tiene que ser mayor que cero.',
  LOAN_ALREADY_RETURNED: 'Este préstamo ya estaba devuelto.',
  LOAN_ASSET_ALREADY_LENT: 'Eso ya está prestado. Apunta la devolución antes de volver a prestarlo.',
  LOAN_ASSET_NOT_DURABLE: 'Un consumible no se presta: descuenta la cantidad que has dado.',
  LOCATION_CYCLE: 'No se puede mover ahí: el destino está dentro de lo que quieres mover.',
  LOCATION_DUPLICATE: 'Ya hay algo con ese nombre en el mismo sitio.',
  LOCATION_HAS_ASSETS: 'No se puede borrar: todavía hay cosas guardadas ahí.',
  LOCATION_HAS_CHILDREN: 'No se puede borrar: cuelgan otras ubicaciones de ella.',
  MERGE_ARTICLE_MISMATCH: 'Solo se pueden unir existencias del mismo artículo.',
  MERGE_ASSET_DEACTIVATED: 'Alguna de las dos existencias está dada de baja.',
  MERGE_NOT_CONSUMABLE: 'Solo se unen existencias de consumible.',
  MERGE_SAME_ASSET: 'Origen y destino son la misma existencia.',
  // Se ve poco a propósito: la pantalla de una ruta apagada ofrece activar el
  // módulo en lugar de enseñar un error. Este texto es para el caso raro de
  // que alguien lo reciba en medio de otra cosa, por haberlo desactivado desde
  // otra pestaña.
  MODULE_INACTIVE: 'Ese módulo no está activo en este hogar.',
  NOT_FOUND: 'Eso ya no está.',
  FORBIDDEN: 'No tienes permiso para hacer esto.',
  RATE_LIMITED: 'Demasiados intentos. Prueba de nuevo en un momento.',
  // Es el único de los tres de subida que NO se arregla eligiendo otro fichero:
  // hay que hacer sitio. De ahí que el texto diga qué hacer y no qué ha pasado.
  STORAGE_QUOTA_EXCEEDED: 'No queda espacio en el hogar. Borra algún fichero para hacer sitio.',
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
  photoUrl: string | null
  photoThumbnailUrl: string | null
  photoFileId: string | null
}

export type CapacityType = 'WEIGHT' | 'VOLUME' | 'UNITS'

export interface Capacity {
  type: CapacityType
  max: number
  unit: string
}

/**
 * Qué se declara al poner capacidad, y en qué se mide por defecto.
 *
 * Solo `UNITS` se comprueba de verdad: superar un máximo en peso o en volumen no
 * se puede detectar porque el asset no lleva ninguno de los dos. Se admiten
 * igualmente porque el dato le sirve a quien lo mira aunque el sistema no pueda
 * contarlo, y decirlo es más honesto que ofrecer solo uno de los tres.
 */
export const CAPACITY_TYPE_LABELS: Record<CapacityType, string> = {
  UNITS: 'Unidades',
  WEIGHT: 'Peso',
  VOLUME: 'Volumen',
}

export const CAPACITY_DEFAULT_UNITS: Record<CapacityType, string> = {
  UNITS: 'cajas',
  WEIGHT: 'kg',
  VOLUME: 'l',
}

export interface Location {
  id: string
  name: string
  type: LocationType
  parentLocationId: string | null
  capacity: Capacity | null
  notes: string | null
  photoUrl: string | null
  photoThumbnailUrl: string | null
  photoFileId: string | null
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
  /** `YYYY-MM-DD`: es una fecha sin hora, no un instante. */
  acquiredOn: string | null
  notes: string | null
  photoUrl: string | null
  photoThumbnailUrl: string | null
  photoFileId: string | null
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

// --- Módulos activables (Fase 2, Hito 0) -------------------------------------

export type ModuleStatus = 'ACTIVE' | 'INACTIVE'

/**
 * Una entrada del catálogo con lo que este hogar ha decidido sobre ella.
 *
 * El catálogo llega **entero** aunque no se administre: ver que un módulo existe
 * no es poder encenderlo, y quien no administra necesita saber que hay para
 * poder pedirlo.
 */
export interface HouseholdModule {
  key: string
  name: string
  description: string
  routePrefix: string
  status: ModuleStatus
  activatedAt: string | null
  deactivatedAt: string | null
}

// --- Ficheros y documentos (Hito 3) ------------------------------------------

export type FileContentType = 'image/jpeg' | 'image/png' | 'image/webp' | 'application/pdf'

/** Los cuatro que admite la lista blanca. Sirve para el `accept` del selector. */
export const ALLOWED_FILE_TYPES: FileContentType[] = ['image/jpeg', 'image/png', 'image/webp', 'application/pdf']

export interface StoredFile {
  id: string
  originalName: string
  contentType: FileContentType
  sizeBytes: number
  checksum: string
  /**
   * URL **firmada de vida corta**, servida desde el dominio de ficheros. Caduca
   * con el access token que la generó —unos quince minutos— y **no se guarda**:
   * vale para pintar ahora, no para almacenar en el estado ni para compartir.
   */
  url: string
  /** Nula en un PDF: solo las imágenes tienen miniatura. */
  thumbnailUrl: string | null
  /** Nulo mientras la subida está en curso. */
  uploadedAt: string | null
  createdAt: string
  createdBy: string | null
}

export interface StorageUsage {
  usedBytes: number
  quotaBytes: number
  maxFileBytes: number
}

export type DocumentType = 'INVOICE' | 'WARRANTY' | 'MANUAL' | 'OTHER'

/** Son datos que lee una persona, así que van en castellano. */
export const DOCUMENT_TYPE_LABELS: Record<DocumentType, string> = {
  INVOICE: 'Factura',
  WARRANTY: 'Garantía',
  MANUAL: 'Manual',
  OTHER: 'Otro',
}

export interface StoredDocument {
  id: string
  assetId: string | null
  articleId: string | null
  type: DocumentType
  /** Exactamente uno de los dos: el documento vive fuera o vive aquí. */
  url: string | null
  fileId: string | null
  description: string | null
  date: string | null
  validUntil: string | null
  createdAt: string
}

// --- Préstamos (Hito 4) ------------------------------------------------------

export type LoanStatus = 'ACTIVE' | 'RETURNED' | 'OVERDUE'
export type LoanRole = 'LENDER' | 'BORROWER'

/** Son datos que lee una persona, así que van en castellano. */
export const LOAN_STATUS_LABELS: Record<LoanStatus, string> = {
  ACTIVE: 'Prestado',
  OVERDUE: 'Fuera de plazo',
  RETURNED: 'Devuelto',
}

/** Nombre y al menos un canal: es por donde se le manda el enlace. */
export interface ExternalParty {
  name: string
  email: string | null
  phone: string | null
}

/** Exactamente uno de los dos, nunca los dos ni ninguno. */
export interface LoanParticipant {
  userId: string | null
  external: ExternalParty | null
}

export interface Loan {
  id: string
  assetId: string
  assetName: string | null
  lender: LoanParticipant
  borrower: LoanParticipant
  status: LoanStatus
  startedAt: string
  dueAt: string | null
  returnedAt: string | null
  notes: string | null
  createdBy: string | null
  updatedBy: string | null
}

/**
 * Lo que ve quien llega con el enlace del correo.
 *
 * **Es el único recurso de la API con dos formas**, y esta es la acotada: menos
 * campos que [Loan] a propósito. Que sea un tipo distinto y no un `Partial<Loan>`
 * es lo que impide que la pantalla externa lea un `lender` que nunca llega.
 */
export interface ExternalLoan {
  id: string
  assetName: string | null
  role: LoanRole
  status: LoanStatus
  startedAt: string
  dueAt: string | null
  returnedAt: string | null
}

export interface LoanFilters {
  status?: LoanStatus
  assetId?: string
  /** `ACTIVE` y `OVERDUE` juntos: qué hay fuera de casa. */
  open?: boolean
}

/**
 * Una fecha como la escribiría una persona.
 *
 * Es la primera del cliente: hasta el Hito 4 ninguna pantalla mostraba fechas.
 * Sin hora, porque en un préstamo doméstico el día es toda la precisión que
 * significa algo.
 */
export function formatDate(iso: string | null): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString('es-ES', { day: 'numeric', month: 'long', year: 'numeric' })
}

/**
 * Una fecha **sin hora**, como la que declara el contrato con `format: date`.
 *
 * No vale `formatDate`: `new Date('2019-11-03')` interpreta la cadena como
 * medianoche **UTC**, así que en un huso negativo se imprime el día anterior. Un
 * `acquiredOn` es un día del calendario, no un instante, y el 3 de noviembre
 * tiene que leerse 3 de noviembre en Canarias y en Ciudad de México.
 */
export function formatDay(day: string | null): string {
  if (!day) return '—'
  const [year, month, date] = day.split('-').map(Number)
  if (!year || !month || !date) return day
  return new Date(year, month - 1, date).toLocaleDateString('es-ES', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  })
}

/** Cómo se escribe un tamaño para que lo lea una persona. */
export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  const units = ['kB', 'MB', 'GB']
  let value = bytes / 1024
  let unit = 0
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024
    unit += 1
  }
  // Un decimal por debajo de 10 y ninguno por encima: «1,4 MB» dice algo y
  // «847,3 kB» solo añade ruido.
  return `${value.toLocaleString('es-ES', { maximumFractionDigits: value < 10 ? 1 : 0 })} ${units[unit]}`
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

/**
 * Sube un fichero **informando del progreso**.
 *
 * Va por `XMLHttpRequest` y no por `fetch`, y no es nostalgia: `fetch` no expone
 * el progreso de **subida**. Sin él, una foto de 20 MB desde el móvil es una
 * pantalla quieta durante medio minuto, que es indistinguible de una aplicación
 * colgada.
 *
 * **Reintenta una vez tras renovar la sesión**, igual que `request`. Sin eso, una
 * subida larga que empieza con el token a punto de caducar termina en un `401`
 * después de haber transmitido los 20 MB, que es el peor momento posible para
 * perderlos. Comparte el mismo intento de renovación que el resto del cliente, de
 * modo que varias subidas a la vez no rotan el refresh token unas contra otras.
 */
export async function uploadFile(
  file: File,
  accessToken: string,
  onProgress?: (fraction: number) => void,
  options: { path?: string; method?: string } = {},
): Promise<StoredFile> {
  const { path = '/files', method = 'POST' } = options

  try {
    return await sendFile(file, accessToken, onProgress, path, method)
  } catch (error) {
    if (!(error instanceof ApiError) || error.status !== 401 || error.code !== 'UNAUTHORIZED') throw error

    const renewed = await renewSession()
    if (!renewed) throw error
    return sendFile(file, renewed, onProgress, path, method)
  }
}

function sendFile(
  file: File,
  accessToken: string,
  onProgress: ((fraction: number) => void) | undefined,
  path: string,
  method: string,
): Promise<StoredFile> {
  return new Promise((resolve, reject) => {
    const request = new XMLHttpRequest()
    request.open(method, `${BASE_URL}${path}`)
    request.setRequestHeader('Authorization', `Bearer ${accessToken}`)

    // `lengthComputable` es falso si el navegador no sabe el total. Ahí no se
    // inventa un porcentaje: quien pinta la barra decide qué hacer sin dato.
    request.upload.onprogress = (event) => {
      if (event.lengthComputable && onProgress) onProgress(event.loaded / event.total)
    }

    request.onload = () => {
      if (request.status >= 200 && request.status < 300) {
        // El avatar responde 204 sin cuerpo; la subida de fichero, 201 con él.
        resolve(request.responseText ? (JSON.parse(request.responseText) as StoredFile) : (undefined as never))
        return
      }
      reject(toXhrError(request))
    }

    // Un fallo de red y una cancelación no llevan cuerpo ni código: se
    // distinguen del error del contrato porque no son `ApiError`.
    request.onerror = () => reject(new Error('No se ha podido subir el fichero'))
    request.onabort = () => reject(new Error('Subida cancelada'))

    const body = new FormData()
    body.append('file', file)
    request.send(body)
  })
}

function toXhrError(request: XMLHttpRequest): ApiError {
  let body: ApiErrorBody | undefined
  try {
    body = JSON.parse(request.responseText) as ApiErrorBody
  } catch {
    body = undefined
  }
  return new ApiError(
    request.status,
    body?.code ?? 'INTERNAL_ERROR',
    body?.message ?? 'No se ha podido subir el fichero',
    body?.details,
  )
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

  // --- Módulos del hogar ----------------------------------------------------
  // Se resuelve **una vez por sesión** y no una por pantalla: la clave de
  // consulta es fija, así que React Query la comparte entre el shell, la
  // pantalla de módulos y cada ruta de módulo.
  listModules: (accessToken: string) =>
    request<Page<HouseholdModule>>('/modules', { accessToken }),

  activateModule: (key: string, accessToken: string) =>
    request<HouseholdModule>(`/modules/${key}/activation`, { method: 'POST', accessToken }),

  deactivateModule: (key: string, accessToken: string) =>
    request<HouseholdModule>(`/modules/${key}/activation`, { method: 'DELETE', accessToken }),

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

  // --- Ficheros y documentos ------------------------------------------------
  /** Ordenado por tamaño **descendente**: cuando la cuota se agota, la pregunta es qué la ocupa. */
  listFiles: (accessToken: string, filters: { attached?: boolean; type?: FileContentType } = {}) =>
    request<Page<StoredFile>>(`/files${queryString({ ...filters, size: 200 })}`, { accessToken }),

  getFile: (id: string, accessToken: string) => request<StoredFile>(`/files/${id}`, { accessToken }),

  /** Solo sobre ficheros que no cuelgan de nada: primero se desadjunta. */
  deleteFile: (id: string, accessToken: string) =>
    request<void>(`/files/${id}`, { method: 'DELETE', accessToken }),

  getStorageUsage: (accessToken: string) => request<StorageUsage>('/storage', { accessToken }),

  listDocuments: (accessToken: string, filters: { assetId?: string; articleId?: string } = {}) =>
    request<Page<StoredDocument>>(`/documents${queryString({ ...filters, size: 200 })}`, { accessToken }),

  attachDocument: (body: Record<string, unknown>, accessToken: string) =>
    request<StoredDocument>('/documents', { method: 'POST', body, accessToken }),

  /** Si tenía fichero, la misma transacción lo marca: la cuota se libera en el acto. */
  deleteDocument: (id: string, accessToken: string) =>
    request<void>(`/documents/${id}`, { method: 'DELETE', accessToken }),

  /** «me» es la **identidad**, no la pertenencia: el avatar es de la persona. */
  setOwnAvatar: (file: File, accessToken: string, onProgress?: (fraction: number) => void) =>
    uploadFile(file, accessToken, onProgress, { path: '/users/me/avatar', method: 'PUT' }),

  deleteOwnAvatar: (accessToken: string) =>
    request<void>('/users/me/avatar', { method: 'DELETE', accessToken }),

  // --- Préstamos ------------------------------------------------------------
  listLoans: (accessToken: string, filters: LoanFilters = {}) =>
    request<Page<Loan>>(`/loans${queryString({ ...filters, size: 200 })}`, { accessToken }),

  getLoan: (id: string, accessToken: string) => request<Loan>(`/loans/${id}`, { accessToken }),

  startLoan: (body: Record<string, unknown>, accessToken: string) =>
    request<Loan>('/loans', { method: 'POST', body, accessToken }),

  confirmReturn: (id: string, accessToken: string) =>
    request<Loan>(`/loans/${id}/return`, { method: 'POST', accessToken }),

  // --- Préstamos, con el token acotado del correo ---------------------------
  // Las dos únicas llamadas de toda la API que **no van con la sesión**, y por
  // eso llevan `renewable: false`: no hay refresh token que rotar ni sesión que
  // recuperar. Un 401 aquí significa que el enlace ya no vale, y punto.

  getLoanWithToken: (id: string, loanToken: string) =>
    request<ExternalLoan>(`/loans/${id}`, { accessToken: loanToken, renewable: false }),

  confirmReturnWithToken: (id: string, loanToken: string) =>
    request<ExternalLoan>(`/loans/${id}/return`, {
      method: 'POST',
      accessToken: loanToken,
      renewable: false,
    }),
}
