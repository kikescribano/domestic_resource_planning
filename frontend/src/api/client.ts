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

import { HeicConversionError, toUploadable } from './heic'

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
  | 'HOUSEHOLD_CLOSURE_ALREADY_REQUESTED'
  | 'HOUSEHOLD_CLOSURE_NOT_REQUESTED'
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
  | 'SUPPLIER_CONTACT_REQUIRED'
  | 'SUPPLIER_DUPLICATE'
  | 'SUPPLIER_LINK_DUPLICATE'
  | 'SUPPLIER_LINK_TARGET_INVALID'
  | 'SUPPLIER_RETIRED'
  | 'STOCK_ITEM_NOT_TRACKED'
  | 'STOCK_CONSUMPTION_NOT_POSITIVE'
  | 'STOCK_CONSUMPTION_EXCEEDS_QUANTITY'
  | 'STOCK_LOT_DUPLICATE'
  | 'STOCK_LOT_EXCEEDS_QUANTITY'
  | 'SHOPPING_ITEM_DUPLICATE'
  | 'SHOPPING_ITEM_NOT_PENDING'
  | 'PURCHASE_EMPTY'
  | 'PURCHASE_NOT_OPEN'
  | 'PURCHASE_SUPPLIER_UNKNOWN'
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
  // Los dos de la baja de hogar se ven poco —la pantalla esconde el gesto
  // que sobra— pero llegan cuando alguien lo hace desde dos pestañas o desde
  // otro dispositivo, que en un hogar compartido no es raro.
  HOUSEHOLD_CLOSURE_ALREADY_REQUESTED: 'El hogar ya tiene una baja pedida. Recarga para ver la fecha.',
  HOUSEHOLD_CLOSURE_NOT_REQUESTED: 'El hogar no tiene ninguna baja pedida. Puede que alguien ya la cancelara.',
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
  // Sale en dos sitios distintos —al cambiar un rol y al cerrar la cuenta
  // propia— así que dice la regla y no el sujeto, que cambia.
  USER_LAST_ADMIN: 'Dejaría al hogar sin ninguna persona que lo administre. Nombra antes a otra.',
  SUPPLIER_CONTACT_REQUIRED: 'Hace falta al menos un teléfono, un correo o una web.',
  SUPPLIER_DUPLICATE: 'Ya hay un contacto de servicio con ese nombre.',
  SUPPLIER_LINK_DUPLICATE: 'Ese contacto ya está enlazado con eso.',
  SUPPLIER_LINK_TARGET_INVALID: 'Elige una cosa con la que enlazarlo.',
  SUPPLIER_RETIRED: 'Ese contacto está retirado: no admite enlaces nuevos.',
  STOCK_ITEM_NOT_TRACKED: 'El almacén solo lleva la cuenta de los consumibles que hay en casa.',
  STOCK_CONSUMPTION_NOT_POSITIVE: 'Lo que has gastado tiene que ser mayor que cero.',
  STOCK_CONSUMPTION_EXCEEDS_QUANTITY: 'No puedes gastar más de lo que hay.',
  STOCK_LOT_DUPLICATE: 'Ya tienes anotada esa misma caducidad para este artículo.',
  STOCK_LOT_EXCEEDS_QUANTITY: 'Las caducidades anotadas sumarían más de lo que hay.',
  SHOPPING_ITEM_DUPLICATE: 'Eso ya está en la lista de la compra.',
  SHOPPING_ITEM_NOT_PENDING: 'Esa línea ya se compró o se descartó.',
  PURCHASE_EMPTY: 'Una compra necesita al menos una cosa que comprar.',
  PURCHASE_NOT_OPEN: 'Esa compra ya está cerrada.',
  // Dice las dos cosas porque el servidor no distingue las dos cosas, y eso es
  // deliberado: Compras no sabe --ni tiene por qué-- si Proveedores está apagado
  // o si ese sitio no existe.
  PURCHASE_SUPPLIER_UNKNOWN: 'No se puede usar ese sitio: o ya no está, o el módulo de proveedores está apagado.',
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
  // La conversión de HEIC falla **antes** de que haya petición, así que no trae
  // `code` y tampoco es un fallo de red: el mensaje por defecto mandaría a
  // comprobar la conexión, que es mirar donde no es.
  if (error instanceof HeicConversionError) {
    return 'No se ha podido convertir esta foto. Vuelve a intentarlo, o súbela en JPEG.'
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

/**
 * La baja solicitada de un hogar (ADR-012).
 *
 * `effectiveAt` es **la fecha que se le prometió a una persona**: el instante a
 * partir del cual el hogar desaparece. Viaja como instante y se pinta en la zona
 * de quien mira.
 */
export interface HouseholdClosure {
  requestedAt: string
  /** La **pertenencia** de quien la pidió, como toda la autoría del contrato. */
  requestedBy: string
  effectiveAt: string
}

/**
 * El hogar de la sesion.
 *
 * Es la primera lectura del hogar que tiene el contrato, y existe sobre todo por
 * [closure]: el aviso persistente y la zona de peligro necesitan saber si el
 * hogar se está dando de baja. **No sale del token** a propósito —el access
 * token vive quince minutos, así que un hogar marcado después mentiría hasta la
 * siguiente renovación—.
 */
export interface Household {
  id: string
  name: string
  timeZone: string
  createdAt: string
  updatedAt: string
  /** Nula es lo normal: solo tiene valor mientras corre el periodo de gracia. */
  closure: HouseholdClosure | null
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
  /**
   * Lo que pesa y ocupa **una** unidad del artículo, en gramos y mililitros.
   *
   * Nulo es «no se sabe», que es el caso normal. De aquí sale el aviso de
   * capacidad de una ubicación cuando la capacidad es de peso o de volumen: es
   * la pregunta que la Fase 1 dejó abierta y que el Hito 3 de la Fase 2 resolvió
   * a favor del core, porque una regla del core no puede depender de un módulo
   * que se puede apagar.
   */
  unitWeightGrams: number | null
  unitVolumeMl: number | null
  notes: string | null
  retiredAt: string | null
  photoUrl: string | null
  photoThumbnailUrl: string | null
  photoFileId: string | null
}

// --- Almacén (módulo WAREHOUSE) ---------------------------------------------

/**
 * Una existencia vista por el almacén.
 *
 * `quantity` **es la del core**: Warehouse no lleva un segundo contador —el core
 * mantiene uno, y el módulo guarda consumos, mínimos, caducidad y lotes—. Que
 * aquí llegue una sola cifra es porque solo hay una, no porque se hayan
 * reconciliado dos.
 */
export interface StockItem {
  assetId: string
  articleId: string
  article: string
  unit: MeasurementUnit
  locationId: string | null
  location: string | null
  quantity: number
  minimumQuantity: number | null
  belowMinimum: boolean
  nearestExpiry: string | null
  lotCount: number
}

/**
 * Una línea de la lista de la compra.
 *
 * `name` es el del artículo del core cuando lo tiene y el nombre suelto cuando
 * no: **siempre hay uno y nunca hay dos**. `unit` y `packLabel` salen también del
 * artículo, así que una línea de texto suelto los trae a nulo.
 *
 * `packLabel` es la presentación de compra **compuesta y no guardada** —«6 UNIT»—
 * a partir del `packSize` del core. Es la respuesta del módulo a si esa
 * presentación necesita nombre propio: no.
 */
export interface ShoppingItem {
  id: string
  articleId: string | null
  name: string
  unit: MeasurementUnit | null
  packLabel: string | null
  quantity: number | null
  origin: ShoppingItemOrigin
  status: ShoppingItemStatus
  note: string | null
  purchaseId: string | null
  receivedAssetId: string | null
  createdAt: string
  updatedAt: string
}

/**
 * Por qué entró la línea. **El orden no es alfabético**: es el que decide qué
 * noticia manda y el que ordena la lista.
 *
 * `MANUAL` es el único que existe con el almacén apagado, y está bien que así
 * sea: sin ese módulo nadie está detectando la falta.
 */
export type ShoppingItemOrigin = 'MANUAL' | 'LOW_STOCK' | 'DEPLETED'

export type ShoppingItemStatus = 'NEEDED' | 'IN_PURCHASE' | 'BOUGHT' | 'DISMISSED'

/** Cómo se lee cada origen en pantalla. Los identificadores van en inglés; esto es dato. */
export const ITEM_ORIGIN_LABELS: Record<ShoppingItemOrigin, string> = {
  MANUAL: 'Apuntado a mano',
  LOW_STOCK: 'Queda poco',
  DEPLETED: 'Se ha acabado',
}

export type PurchaseStatus = 'OPEN' | 'RECEIVED' | 'CANCELLED'

/**
 * Una compra.
 *
 * `supplier` es **el nombre de aquel día**, copiado al abrirla: una compra es
 * historia, y además es lo único que se puede pintar cuando el módulo de
 * proveedores está apagado.
 */
export interface Purchase {
  id: string
  supplierId: string | null
  supplier: string | null
  status: PurchaseStatus
  note: string | null
  receivedAt: string | null
  cancelledAt: string | null
  createdAt: string
}

export interface PurchaseDetail {
  purchase: Purchase
  lines: ShoppingItem[]
}

/**
 * Dónde se puede comprar, leído del módulo de proveedores por un puerto del
 * servidor.
 *
 * `detail` trae el **identificador** de la categoría de servicio y no su rótulo,
 * porque el texto que se lee en pantalla es un dato en castellano y lo pone el
 * cliente —que ya tiene ese mapa en `SERVICE_CATEGORY_LABELS`.
 */
export interface PurchasingSupplier {
  id: string
  name: string
  detail: string | null
}

/**
 * Una máquina del hogar, tal y como Mantenimiento la ve.
 *
 * `name` **sale del core** —del asset, o de su artículo— y no se guarda en
 * ninguna tabla del módulo: dos copias del mismo nombre acaban diciendo cosas
 * distintas el día que alguien renombre el asset.
 *
 * La ficha de cada máquina la abre el módulo solo, al sembrarse y al llegar
 * `AssetCreated`. **No hay operación de crearla**, y no es un olvido: una máquina
 * entra en el radar porque existe en el core.
 */
export interface MaintenanceMachine {
  assetId: string
  name: string
  manualDocumentId: string | null
  notes: string | null
  planCount: number
  nextDueOn: string | null
}

export interface MaintenanceMachineDetail {
  machine: MaintenanceMachine
  plans: MaintenancePlan[]
  interventions: MaintenanceIntervention[]
}

/**
 * Una **regla recurrente** sobre una máquina.
 *
 * Fíjate en lo que no lleva: ni responsable ni día concreto. Eso es la frontera
 * contra el planificador de tareas escrita en el propio tipo — **de aquí es el
 * cuándo, de allí el quién lo hace**. Un plan es una regla; una tarea, un
 * encargo.
 *
 * `supplierId` viene **sin nombre al lado**, al revés que en una intervención: un
 * plan es una regla viva y tiene que decir el nombre de hoy, así que el servidor
 * lo resuelve al leer la ficha del plan.
 */
export interface MaintenancePlan {
  id: string
  assetId: string
  name: string
  /** Cada cuántos meses toca. En meses y no en días, para que el aniversario no se desplace. */
  intervalMonths: number
  leadDays: number
  nextDueOn: string
  lastPerformedOn: string | null
  supplierId: string | null
  notes: string | null
  cancelledAt: string | null
  createdAt: string
  updatedAt: string
}

export interface MaintenancePlanDetail {
  plan: MaintenancePlan
  machineName: string
  /**
   * Resuelto al leer, no copiado. **Un contacto retirado sigue saliendo aquí** —un
   * plan que apunte a quien ya no se llama tiene que poder decir a quién
   * apuntaba— aunque deje de ofrecerse en el selector. Nulo si no hay, si ya no
   * existe o si el módulo de proveedores está apagado, sin que se puedan
   * distinguir los tres casos.
   */
  supplier: MaintenanceSupplier | null
  interventions: MaintenanceIntervention[]
}

export type InterventionKind = 'PREVENTIVE' | 'CORRECTIVE'

/** Cómo se lee cada clase en pantalla. Los identificadores van en inglés; esto es dato. */
export const INTERVENTION_KIND_LABELS: Record<InterventionKind, string> = {
  PREVENTIVE: 'Preventiva',
  CORRECTIVE: 'Correctiva',
}

/**
 * Lo que se hizo, y cuándo. Es un **libro**: se escribe y no se toca, así que no
 * lleva `updatedAt` ni hay forma de modificarla.
 *
 * `supplier` es **el nombre de aquel día**, copiado al registrarla: una
 * intervención es historia y siguió siendo cierta aunque el contacto se retire o
 * el hogar apague proveedores.
 */
export interface MaintenanceIntervention {
  id: string
  assetId: string
  planId: string | null
  kind: InterventionKind
  performedOn: string
  summary: string
  supplierId: string | null
  supplier: string | null
  notes: string | null
  createdAt: string
}

/**
 * A quién se puede llamar, leído del módulo de proveedores por un puerto del
 * servidor.
 *
 * `detail` trae el **identificador** de la categoría de servicio y no su rótulo,
 * y es con lo que esta pantalla **agrupa** el selector —el mapa está en
 * `SERVICE_CATEGORY_LABELS`—. Es también la razón de que el puerto no se haya
 * ensanchado: filtrar por categoría en el servidor escondería justo al contacto
 * que hace falta.
 */
export interface MaintenanceSupplier {
  id: string
  name: string
  detail: string | null
}

export type MovementKind =
  | 'OPENING'
  | 'INTAKE'
  | 'ADJUSTMENT'
  | 'MERGE'
  | 'DECOMMISSION'
  | 'RELOCATION'

/** Cómo se lee cada motivo en pantalla. Los identificadores van en inglés; esto es dato. */
export const MOVEMENT_KIND_LABELS: Record<MovementKind, string> = {
  OPENING: 'Al encender el almacén',
  INTAKE: 'Entrada',
  ADJUSTMENT: 'Ajuste',
  MERGE: 'Fusión',
  DECOMMISSION: 'Baja',
  RELOCATION: 'Cambio de sitio',
}

export interface StockMovement {
  id: string
  assetId: string
  articleId: string | null
  locationId: string | null
  /** El nombre que el sitio tenía **ese día**: el core borra ubicaciones de verdad. */
  location: string | null
  kind: MovementKind
  previousQuantity: number | null
  quantity: number | null
  delta: number | null
  occurredAt: string
}

export interface StockLot {
  id: string
  assetId: string
  articleId: string
  lotCode: string | null
  expiresOn: string
  quantity: number
  consumedAt: string | null
}

export interface StockItemDetail {
  item: StockItem
  expiryLeadDays: number | null
  lots: StockLot[]
  movements: StockMovement[]
}

export interface WarehouseArticle {
  articleId: string
  minimumQuantity: number | null
  expiryLeadDays: number | null
  lowStockSince: string | null
}

export interface StockFilters {
  q?: string
  locationId?: string
  belowMinimum?: boolean
  expiringWithinDays?: number
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

// --- Avisos del hogar (Fase 2, Hito 1) ---------------------------------------

/**
 * Algo que el hogar tiene que saber, encontrado por el recorrido periódico del
 * backend.
 *
 * **No se crean ni se borran desde aquí**: los escribe la pasada diaria y lo
 * único que una persona hace con un aviso es leerlo. De ahí que el cliente solo
 * tenga tres llamadas y ninguna sea de escritura de contenido.
 *
 * `moduleKey` a nulo significa **del core**, que es lo que hace que un préstamo
 * vencido siga avisando en un hogar sin ningún módulo encendido.
 */
export interface Notice {
  id: string
  moduleKey: string | null
  kind: string
  title: string
  body: string
  createdAt: string
  readAt: string | null
  readBy: string | null
}

// --- Proveedores y contactos de servicio (Fase 2, Hito 2) --------------------

/**
 * A qué se dedica un contacto de servicio.
 *
 * **Lista cerrada**, al contrario que la categoría de un asset, que es un
 * catálogo por hogar: aquella clasifica lo que el hogar tiene, que no tiene fin;
 * esta clasifica a qué se dedica quien viene a casa. `OTHER` es la salida.
 */
export type ServiceCategory =
  | 'PLUMBING'
  | 'ELECTRICITY'
  | 'HEATING_COOLING'
  | 'APPLIANCES'
  | 'CARPENTRY'
  | 'MASONRY'
  | 'PAINTING'
  | 'LOCKSMITH'
  | 'CLEANING'
  | 'GARDENING'
  | 'PEST_CONTROL'
  | 'VEHICLE'
  | 'UTILITIES'
  | 'OTHER'

/**
 * El rótulo en castellano de cada categoría.
 *
 * Vive aquí y no en el backend a propósito: la clave es un **identificador** y
 * viaja en inglés; lo que lee una persona es un **dato** de presentación. Que el
 * servidor devolviera las dos cosas daría dos versiones del mismo nombre, que es
 * exactamente el defecto que el catálogo de módulos existe para evitar.
 */
export const SERVICE_CATEGORY_LABELS: Record<ServiceCategory, string> = {
  PLUMBING: 'Fontanería',
  ELECTRICITY: 'Electricidad',
  HEATING_COOLING: 'Climatización',
  APPLIANCES: 'Electrodomésticos',
  CARPENTRY: 'Carpintería',
  MASONRY: 'Albañilería',
  PAINTING: 'Pintura',
  LOCKSMITH: 'Cerrajería',
  CLEANING: 'Limpieza',
  GARDENING: 'Jardinería',
  PEST_CONTROL: 'Control de plagas',
  VEHICLE: 'Vehículos',
  UTILITIES: 'Suministros',
  OTHER: 'Otros',
}

/**
 * Quién arregla, quién cobra y quién responde de una garantía.
 *
 * Los cinco datos de contacto son opcionales uno a uno y obligatorios en
 * conjunto: hace falta al menos teléfono, correo o web. Esa regla no se puede
 * expresar campo a campo, así que el servidor responde `409` y no `400`.
 */
export interface Supplier {
  id: string
  name: string
  serviceCategory: ServiceCategory
  contactName: string | null
  phone: string | null
  email: string | null
  website: string | null
  address: string | null
  notes: string | null
  createdAt: string
  updatedAt: string
  /** Con valor, está retirado: no se ofrece al enlazar ni sale en el listado por defecto. */
  retiredAt: string | null
  createdBy: string | null
  updatedBy: string | null
}

/**
 * El enlace de un contacto con algo del core, aplanado a tres campos.
 *
 * Dentro son dos columnas excluyentes; aquí `targetType` dice a cuál apunta. El
 * `targetName` lo resuelve el servidor **al leer**, así que renombrar la caldera
 * se ve sin que nadie sincronice nada.
 */
export interface SupplierLink {
  id: string
  targetType: 'ASSET' | 'LOCATION'
  targetId: string
  targetName: string
  createdAt: string
  createdBy: string | null
}

export interface SupplierDetail {
  supplier: Supplier
  links: SupplierLink[]
}

export interface SupplierInput {
  name: string
  serviceCategory: ServiceCategory
  contactName?: string | null
  phone?: string | null
  email?: string | null
  website?: string | null
  address?: string | null
  notes?: string | null
}

// --- Ficheros y documentos (Hito 3) ------------------------------------------

export type FileContentType = 'image/jpeg' | 'image/png' | 'image/webp' | 'application/pdf'

/** Los cuatro que admite la lista blanca de 5.8.3. Es lo que el servidor guarda, y no cambia. */
export const ALLOWED_FILE_TYPES: FileContentType[] = ['image/jpeg', 'image/png', 'image/webp', 'application/pdf']

/**
 * Lo que el selector ofrece **además** de la lista blanca, porque el cliente lo
 * convierte a JPEG antes de enviarlo (ADR-014).
 *
 * No amplía nada del servidor: HEIC sigue fuera de `ALLOWED_FILE_TYPES`, del
 * `CHECK` de `files.content_type` y del contrato. Lo único que dice es que un
 * fichero así ya no hay que dejarlo en gris en el diálogo, porque ahora se puede
 * subir.
 *
 * Van los tipos **y** las extensiones a propósito: `image/heic` no está
 * registrado en todos los sistemas, y ahí el diálogo solo casa por extensión.
 */
export const CONVERTIBLE_FILE_TYPES = ['image/heic', 'image/heif', '.heic', '.heif']

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

function queryString(
  params: Record<string, string | number | boolean | readonly string[] | undefined>,
): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    // Un array se repite en lugar de unirse por comas, que es lo que declara el
    // contrato para los parámetros repetibles —el `status` de la lista de la
    // compra es el primero—. Unirlos funcionaría hoy, porque Spring parte por
    // comas, pero ataría el cliente a esa conveniencia.
    if (Array.isArray(value)) {
      for (const entry of value) search.append(key, entry)
    } else if (value !== undefined && value !== '') {
      search.set(key, String(value))
    }
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
 *
 * **Y convierte el HEIC antes de enviar nada** (ADR-014). Va aquí y no en el
 * campo de subida porque aquí pasan las dos vías —los ficheros del hogar y el
 * avatar, que tiene su propio `<input>`— y la tercera que se escriba. El coste
 * de quien no sube un HEIC es leer doce bytes.
 */
export async function uploadFile(
  file: File,
  accessToken: string,
  onProgress?: (fraction: number) => void,
  options: { path?: string; method?: string; onConverting?: () => void } = {},
): Promise<StoredFile> {
  const { path = '/files', method = 'POST', onConverting } = options
  const sendable = await toUploadable(file, onConverting)

  try {
    return await sendFile(sendable, accessToken, onProgress, path, method)
  } catch (error) {
    if (!(error instanceof ApiError) || error.status !== 401 || error.code !== 'UNAUTHORIZED') throw error

    const renewed = await renewSession()
    if (!renewed) throw error
    // El reintento va con lo ya convertido, no con lo que eligió la persona:
    // volver al original mandaría el HEIC y lo respondería un 415 detrás de una
    // renovación que sí había funcionado.
    return sendFile(sendable, renewed, onProgress, path, method)
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

  // --- El hogar y su baja ---------------------------------------------------
  getCurrentHousehold: (accessToken: string) =>
    request<Household>('/households/current', { accessToken }),

  requestHouseholdClosure: (accessToken: string) =>
    request<Household>('/households/current/closure', { method: 'POST', accessToken }),

  cancelHouseholdClosure: (accessToken: string) =>
    request<Household>('/households/current/closure', { method: 'DELETE', accessToken }),

  // --- Usuarios e invitaciones ----------------------------------------------
  listUsers: (accessToken: string, includeDeactivated = false) =>
    request<Page<User>>(`/users?includeDeactivated=${includeDeactivated}`, { accessToken }),

  changeUserRole: (memberId: string, role: UserRole, accessToken: string) =>
    request<User>(`/users/${memberId}/roles`, { method: 'PATCH', body: { role }, accessToken }),

  deactivateUser: (memberId: string, accessToken: string) =>
    request<void>(`/users/${memberId}`, { method: 'DELETE', accessToken }),

  /**
   * Cerrar la cuenta propia. **Sin identificador**: el sujeto lo pone el token,
   * y `deactivateUser` —que sí lo lleva— es otra cosa, sacar a alguien del
   * hogar.
   *
   * Deja la sesión inservible, así que quien lo llame tiene que descartar los
   * tokens y volver a la pantalla de entrar.
   */
  closeAccount: (accessToken: string) =>
    request<void>('/users/me', { method: 'DELETE', accessToken }),

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

  // --- Avisos ---------------------------------------------------------------
  listNotices: (accessToken: string, unreadOnly = false) =>
    request<Page<Notice>>(`/notices${queryString({ unreadOnly, size: 100 })}`, { accessToken }),

  markNoticeRead: (id: string, accessToken: string) =>
    request<Notice>(`/notices/${id}/read`, { method: 'POST', accessToken }),

  markAllNoticesRead: (accessToken: string) =>
    request<void>('/notices/read', { method: 'POST', accessToken }),

  // --- Proveedores y contactos de servicio ----------------------------------
  // Todo lo que cuelga de `/suppliers` responde 403 MODULE_INACTIVE si el hogar
  // no tiene el módulo encendido. El cliente no lo comprueba antes de llamar:
  // lo hace el guardián de la ruta, con el catálogo que ya está en caché.
  listSuppliers: (
    accessToken: string,
    filters: { serviceCategory?: ServiceCategory; q?: string; includeRetired?: boolean } = {},
  ) =>
    request<Page<Supplier>>(
      `/suppliers${queryString({ ...filters, size: 200 })}`,
      { accessToken },
    ),

  getSupplier: (id: string, accessToken: string) =>
    request<SupplierDetail>(`/suppliers/${id}`, { accessToken }),

  createSupplier: (body: SupplierInput, accessToken: string) =>
    request<Supplier>('/suppliers', { method: 'POST', body, accessToken }),

  updateSupplier: (id: string, body: Partial<SupplierInput>, accessToken: string) =>
    request<Supplier>(`/suppliers/${id}`, { method: 'PATCH', body, accessToken }),

  retireSupplier: (id: string, accessToken: string) =>
    request<void>(`/suppliers/${id}`, { method: 'DELETE', accessToken }),

  // Exactamente uno de los dos. Ni ninguno ni los dos: eso es
  // SUPPLIER_LINK_TARGET_INVALID, que es una regla y no un error de forma.
  linkSupplier: (id: string, body: { assetId?: string; locationId?: string }, accessToken: string) =>
    request<SupplierLink>(`/suppliers/${id}/links`, { method: 'POST', body, accessToken }),

  unlinkSupplier: (id: string, linkId: string, accessToken: string) =>
    request<void>(`/suppliers/${id}/links/${linkId}`, { method: 'DELETE', accessToken }),

  // --- Almacén --------------------------------------------------------------
  // Todo lo que cuelga de /warehouse responde 403 MODULE_INACTIVE mientras el
  // hogar no lo tenga encendido. La pantalla no lo comprueba: la envuelve
  // `ModuleScreen`, que ya tiene el catálogo en la caché de la sesión.

  listStock: (accessToken: string, filters: StockFilters = {}) =>
    request<Page<StockItem>>(`/warehouse/stock${queryString({ ...filters, size: 200 })}`, { accessToken }),

  getStockItem: (assetId: string, accessToken: string) =>
    request<StockItemDetail>(`/warehouse/stock/${assetId}`, { accessToken }),

  // Un **delta**, no un absoluto: es la diferencia entera con el PATCH del core.
  recordConsumption: (assetId: string, quantity: number, accessToken: string) =>
    request<void>(`/warehouse/stock/${assetId}/consumptions`, {
      method: 'POST',
      body: { quantity },
      accessToken,
    }),

  listStockMovements: (accessToken: string, filters: { assetId?: string; articleId?: string } = {}) =>
    request<Page<StockMovement>>(`/warehouse/movements${queryString({ ...filters, size: 200 })}`, {
      accessToken,
    }),

  listStockLots: (accessToken: string, filters: { assetId?: string } = {}) =>
    request<Page<StockLot>>(`/warehouse/lots${queryString({ ...filters, size: 200 })}`, { accessToken }),

  registerStockLot: (body: Record<string, unknown>, accessToken: string) =>
    request<StockLot>('/warehouse/lots', { method: 'POST', body, accessToken }),

  // Da el lote por consumido. **No toca el contador del core**: eso es un consumo.
  discardStockLot: (lotId: string, accessToken: string) =>
    request<void>(`/warehouse/lots/${lotId}`, { method: 'DELETE', accessToken }),

  updateWarehouseArticle: (articleId: string, body: Record<string, unknown>, accessToken: string) =>
    request<WarehouseArticle>(`/warehouse/articles/${articleId}`, { method: 'PATCH', body, accessToken }),

  // --- Compras --------------------------------------------------------------
  // Todo lo que cuelga de /purchasing responde 403 MODULE_INACTIVE mientras el
  // hogar no lo tenga encendido. La pantalla no lo comprueba: la envuelve
  // `ModuleScreen`.

  listShoppingList: (accessToken: string, filters: { status?: ShoppingItemStatus[]; q?: string } = {}) =>
    request<Page<ShoppingItem>>(`/purchasing/list${queryString({ ...filters, size: 200 })}`, { accessToken }),

  // Un artículo O un nombre suelto, nunca los dos. Es la operación que hace que
  // la lista siga sirviendo con el almacén apagado.
  addShoppingListItem: (
    body: { articleId?: string; name?: string; quantity?: number; note?: string },
    accessToken: string,
  ) => request<ShoppingItem>('/purchasing/list', { method: 'POST', body, accessToken }),

  updateShoppingListItem: (id: string, body: Record<string, unknown>, accessToken: string) =>
    request<ShoppingItem>(`/purchasing/list/${id}`, { method: 'PATCH', body, accessToken }),

  // Baja lógica: descartar «sal» es un dato sobre lo que el hogar no quiere
  // comprar, y borrar la fila dejaría que el mismo evento la volviera a meter.
  dismissShoppingListItem: (id: string, accessToken: string) =>
    request<void>(`/purchasing/list/${id}`, { method: 'DELETE', accessToken }),

  listPurchases: (accessToken: string, filters: { status?: PurchaseStatus } = {}) =>
    request<Page<Purchase>>(`/purchasing/purchases${queryString({ ...filters, size: 200 })}`, { accessToken }),

  getPurchase: (id: string, accessToken: string) =>
    request<PurchaseDetail>(`/purchasing/purchases/${id}`, { accessToken }),

  createPurchase: (body: { supplierId?: string; note?: string; itemIds: string[] }, accessToken: string) =>
    request<PurchaseDetail>('/purchasing/purchases', { method: 'POST', body, accessToken }),

  // **El cierre del ciclo**: acaba invocando la entrada de consumibles del core,
  // que suma sobre la existencia de esa ubicación.
  receivePurchase: (
    id: string,
    body: { lines: Array<{ itemId: string; quantity?: number; ownerId?: string; locationId?: string }> },
    accessToken: string,
  ) => request<PurchaseDetail>(`/purchasing/purchases/${id}/receipt`, { method: 'POST', body, accessToken }),

  // Sus líneas vuelven a la lista: lo que hacía falta sigue haciendo falta.
  cancelPurchase: (id: string, accessToken: string) =>
    request<void>(`/purchasing/purchases/${id}`, { method: 'DELETE', accessToken }),

  // Cuelga de /purchasing y no de /suppliers, así que con proveedores apagado
  // devuelve **lista vacía y no 403**: la degradación la pone el servidor.
  listPurchasingSuppliers: (accessToken: string, q?: string) =>
    request<PurchasingSupplier[]>(`/purchasing/suppliers${queryString({ q })}`, { accessToken }),

  // --- Mantenimiento --------------------------------------------------------
  // Todo lo que cuelga de /maintenance responde 403 MODULE_INACTIVE mientras el
  // hogar no lo tenga encendido. La pantalla no lo comprueba: la envuelve
  // `ModuleScreen`.

  // Las máquinas que el módulo vigila: los DURABLE vivos. **No hay operación de
  // crear una ficha**: la abre el servidor al sembrarse y al llegar `AssetCreated`.
  listMaintenanceMachines: (accessToken: string, q?: string) =>
    request<Page<MaintenanceMachine>>(`/maintenance/machines${queryString({ q, size: 200 })}`, { accessToken }),

  // Por el identificador **del asset**, no por el de la ficha.
  getMaintenanceMachine: (assetId: string, accessToken: string) =>
    request<MaintenanceMachineDetail>(`/maintenance/machines/${assetId}`, { accessToken }),

  updateMaintenanceMachine: (assetId: string, body: Record<string, unknown>, accessToken: string) =>
    request<MaintenanceMachineDetail>(`/maintenance/machines/${assetId}`, {
      method: 'PATCH',
      body,
      accessToken,
    }),

  // `dueWithinDays` no pone suelo: lo que ya se pasó sigue apareciendo, que es
  // justo lo que hay que decir.
  listMaintenancePlans: (
    accessToken: string,
    filters: { assetId?: string; q?: string; dueWithinDays?: number; includeCancelled?: boolean } = {},
  ) =>
    request<Page<MaintenancePlan>>(`/maintenance/plans${queryString({ ...filters, size: 200 })}`, {
      accessToken,
    }),

  getMaintenancePlan: (id: string, accessToken: string) =>
    request<MaintenancePlanDetail>(`/maintenance/plans/${id}`, { accessToken }),

  createMaintenancePlan: (
    body: {
      assetId: string
      name: string
      intervalMonths: number
      leadDays?: number
      nextDueOn: string
      supplierId?: string
      notes?: string
    },
    accessToken: string,
  ) => request<MaintenancePlan>('/maintenance/plans', { method: 'POST', body, accessToken }),

  // Mover `nextDueOn` **rearma el aviso** sin que haga falta pedirlo: el estado
  // del aviso cuelga de la fecha prevista y no del plan.
  updateMaintenancePlan: (id: string, body: Record<string, unknown>, accessToken: string) =>
    request<MaintenancePlan>(`/maintenance/plans/${id}`, { method: 'PATCH', body, accessToken }),

  // Baja lógica: el plan deja de vigilarse y conserva su histórico.
  cancelMaintenancePlan: (id: string, accessToken: string) =>
    request<void>(`/maintenance/plans/${id}`, { method: 'DELETE', accessToken }),

  listMaintenanceInterventions: (
    accessToken: string,
    filters: { assetId?: string; planId?: string } = {},
  ) =>
    request<Page<MaintenanceIntervention>>(
      `/maintenance/interventions${queryString({ ...filters, size: 200 })}`,
      { accessToken },
    ),

  // **Lo que rearma el ciclo**: si cumple un plan, le avanza la próxima fecha.
  registerMaintenanceIntervention: (
    body: {
      assetId: string
      planId?: string
      kind: InterventionKind
      performedOn: string
      summary: string
      supplierId?: string
      notes?: string
    },
    accessToken: string,
  ) => request<MaintenanceIntervention>('/maintenance/interventions', { method: 'POST', body, accessToken }),

  // Cuelga de /maintenance y no de /suppliers, así que con proveedores apagado
  // devuelve **lista vacía y no 403**: la degradación la pone el servidor.
  listMaintenanceSuppliers: (accessToken: string, q?: string) =>
    request<MaintenanceSupplier[]>(`/maintenance/suppliers${queryString({ q })}`, { accessToken }),

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
  setOwnAvatar: (
    file: File,
    accessToken: string,
    onProgress?: (fraction: number) => void,
    onConverting?: () => void,
  ) => uploadFile(file, accessToken, onProgress, { path: '/users/me/avatar', method: 'PUT', onConverting }),

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
