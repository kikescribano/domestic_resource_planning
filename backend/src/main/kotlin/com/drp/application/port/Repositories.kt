package com.drp.application.port

import com.drp.domain.catalog.Article
import com.drp.domain.catalog.Category
import com.drp.domain.household.Household
import com.drp.domain.household.HouseholdMember
import com.drp.domain.file.Document
import com.drp.domain.file.DocumentType
import com.drp.domain.file.StoredContentType
import com.drp.domain.file.StoredFile
import com.drp.domain.identity.EmailAddress
import com.drp.domain.identity.Identity
import com.drp.domain.inventory.Asset
import com.drp.domain.inventory.AssetLocation
import com.drp.domain.inventory.AssetStatus
import com.drp.domain.inventory.AssetType
import com.drp.domain.inventory.Location
import com.drp.domain.invitation.Invitation
import com.drp.domain.token.SingleUseToken
import java.time.Instant
import java.util.UUID

/**
 * Los puertos de persistencia del core.
 *
 * Estan escritos en terminos del dominio --`Household`, `Identity`, no
 * entidades JPA-- porque la regla de dependencia de Clean Architecture va hacia
 * dentro: la aplicacion no puede saber que hay PostgreSQL debajo. Lo unico que
 * lo sabe son los adaptadores de `com.drp.adapter.persistence`.
 *
 * Ninguna firma recibe `householdId`. No es un olvido: el hogar sale del
 * `TenantContext`, que a su vez sale del token, y aceptarlo como parametro seria
 * abrir justo la puerta que la ADR-002 cierra --que un identificador de hogar
 * llegue del cliente y alguien lo use tal cual.
 */

/** Pagina de resultados, con la misma envoltura que devuelve la API. */
data class Page<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val total: Long,
)

data class Pagination(val page: Int, val size: Int)

interface HouseholdRepository {
    fun save(household: Household): Household

    fun findCurrent(): Household?

    /**
     * Bloquea la fila del hogar hasta que cierre la transaccion (`SELECT ... FOR
     * UPDATE`).
     *
     * Existe para la **reserva de cuota** (5.8.3), que es un lee-y-luego-escribe
     * clasico: sumar lo que ya ocupa el hogar y decidir si cabe uno mas. Sin
     * cerrojo, dos subidas simultaneas leen la misma suma, las dos deciden que
     * caben y el gigabyte se pasa.
     *
     * **Se toma antes de leer, no entre la lectura y la escritura**, que es la
     * unica posicion que sirve: tomarlo despues de sumar deja abierta justo la
     * ventana que pretende cerrar. Mismo motivo y misma forma que
     * [HierarchyLock].
     */
    fun lockCurrent()

    /** Borrado real, el unico del core: solo lo usa `PurgeUnverifiedHouseholds`. */
    fun deleteCurrent()
}

/**
 * Responde a "de que hogar es esto" **antes** de que haya contexto de inquilino.
 *
 * Es el unico sitio del core que mira fuera del hogar actual, y existe porque
 * hay tres momentos en los que el hogar todavia no se conoce: el login, la
 * aceptacion de una invitacion y los procesos diarios. Se apoya en tres
 * funciones acotadas de PostgreSQL que **solo devuelven identificadores de
 * hogar** (ver `V4__tenant_resolution.sql`), nunca datos: en cuanto se sabe el
 * hogar, el caso de uso lo fija en el `TenantContext` y todo lo demas vuelve a
 * pasar por la politica.
 *
 * Cualquier metodo nuevo aqui merece la misma pregunta: si devuelve algo mas que
 * un identificador, esta esquivando el aislamiento en lugar de resolverlo.
 */
interface TenantResolver {

    /** Para que los procesos diarios recorran los hogares sin `BYPASSRLS`. */
    fun allHouseholdIds(): List<UUID>

    /** En que hogar entra una identidad al iniciar sesion o renovar. */
    fun householdOfActiveMember(identityId: UUID): UUID?

    /** A que hogar corresponde un token de invitacion recibido por correo. */
    fun householdOfInvitationToken(tokenHash: String): UUID?
}

interface HouseholdMemberRepository {
    fun save(member: HouseholdMember): HouseholdMember

    fun findById(memberId: UUID): HouseholdMember?

    fun findByIdentity(identityId: UUID): HouseholdMember?

    fun list(includeDeactivated: Boolean, pagination: Pagination): Page<HouseholdMember>

    fun countActiveAdmins(): Long
}

interface IdentityRepository {
    fun save(identity: Identity): Identity

    fun findById(identityId: UUID): Identity?

    /**
     * La unica busqueda por correo del sistema.
     *
     * `identities` no tiene RLS debajo, asi que este metodo es toda la
     * proteccion que hay. Existe porque el login y los flujos anonimos no tienen
     * otra forma de resolver a la persona; **no** debe usarse para listar ni
     * para comprobar si alguien esta registrado de cara al cliente, porque eso
     * es justo lo que las respuestas constantes evitan.
     */
    fun findByEmail(email: EmailAddress): Identity?

    fun delete(identityId: UUID)
}

interface InvitationRepository {
    fun save(invitation: Invitation): Invitation

    fun findById(invitationId: UUID): Invitation?

    /** Sin contexto de inquilino: quien acepta todavia no pertenece a ningun hogar. */
    fun findByTokenHash(tokenHash: String): Invitation?

    fun findLiveByEmail(email: EmailAddress, now: Instant): Invitation?

    fun listLive(now: Instant, pagination: Pagination): Page<Invitation>
}

/**
 * Puerto comun de los dos tipos de token de un solo uso. Cada implementacion
 * mira **solo a su tabla**, que es lo que impide que un token de verificacion se
 * pueda usar para cambiar una contrasena.
 */
interface SingleUseTokenRepository {
    fun save(token: SingleUseToken): SingleUseToken

    fun findByTokenHash(tokenHash: String): SingleUseToken?

    /** Emitir uno nuevo invalida el anterior; de ahi que se marquen, no se borren. */
    fun markAllUsedForIdentity(identityId: UUID, at: Instant)
}

interface EmailVerificationTokenRepository : SingleUseTokenRepository

interface PasswordResetTokenRepository : SingleUseTokenRepository

interface RefreshTokenRepository {
    fun save(token: SingleUseToken): SingleUseToken

    fun findByTokenHash(tokenHash: String): SingleUseToken?

    fun revoke(tokenId: UUID, at: Instant)

    /** Restablecer la contrasena cierra **todas** las sesiones. */
    fun revokeAllForIdentity(identityId: UUID, at: Instant)

    /** Cambiarla estando dentro cierra las **demas** y conserva la que esta en uso. */
    fun revokeAllForIdentityExcept(identityId: UUID, keepTokenId: UUID?, at: Instant)
}

interface CategoryRepository {
    /**
     * Las categorias que siembra el alta de un hogar (ver README 4.1.1).
     *
     * No recibe autoria porque no la tiene: sembrar lo hace el sistema, no una
     * persona, y eso es exactamente lo que significa un `created_by` a nulo.
     */
    fun seed(names: List<String>, at: Instant)

    fun countCurrent(): Long

    fun save(category: Category): Category

    fun findById(categoryId: UUID): Category?

    /**
     * La categoria **vigente** que se llame asi, comparando sin distinguir
     * mayusculas ni acentos.
     *
     * Normaliza con la misma funcion que el indice unico --`immutable_unaccent`,
     * ver `V1__extensions.sql`--, y no con un `lowercase()` de Kotlin: dos formas
     * distintas de normalizar significan que el caso de uso deja pasar nombres
     * que la base de datos rechaza despues con un 500.
     */
    fun findLiveByName(name: String): Category?

    fun list(includeRetired: Boolean, pagination: Pagination): Page<Category>
}

interface ArticleRepository {
    fun save(article: Article): Article

    fun findById(articleId: UUID): Article?

    /** Unico en el hogar entre los vigentes, comparado sin mayusculas ni acentos. */
    fun findLiveByName(name: String): Article?

    /** El codigo de barras tambien es unico entre los vigentes, si se informa. */
    fun findLiveByBarcode(barcode: String): Article?

    fun list(filter: ArticleFilter, pagination: Pagination): Page<Article>

    /**
     * Cuantas existencias vivas le quedan.
     *
     * Sostiene las dos reglas que dependen de ello: la `unit` deja de ser
     * modificable en cuanto hay cantidad contada en ella, y el articulo no se
     * puede retirar mientras quede algo.
     */
    fun countLiveStockItems(articleId: UUID): Long
}

data class ArticleFilter(
    val query: String? = null,
    val categoryId: UUID? = null,
    val barcode: String? = null,
    val includeRetired: Boolean = false,
)

interface LocationRepository {
    fun save(location: Location): Location

    fun findById(locationId: UUID): Location?

    /**
     * Unico **entre hermanas**, no en todo el hogar: dos «Estanteria 2» pueden
     * convivir en garajes distintos, pero no en el mismo. `parentLocationId` a
     * nulo compara entre las raices, que es lo que cubre el `NULLS NOT DISTINCT`
     * del indice.
     */
    fun findByNameAmongSiblings(name: String, parentLocationId: UUID?): Location?

    /** Con [parentLocationId] a nulo y [onlyChildren] a false devuelve el hogar entero. */
    fun list(parentLocationId: UUID?, onlyChildren: Boolean, pagination: Pagination): Page<Location>

    fun countChildren(locationId: UUID): Long

    fun countAssetsIn(locationId: UUID): Long

    /**
     * La cadena de ancestros de [locationId], del padre hacia la raiz.
     *
     * Es lo que sostiene la comprobacion anti-ciclo, que **no** es expresable como
     * `CHECK`: una restriccion solo ve la fila que se inserta, y la pregunta aqui
     * es por el camino entero hasta la raiz.
     */
    fun ancestorsOf(locationId: UUID): List<UUID>

    /** Borrado **real**: una ubicacion vacia no deja historial que preservar. */
    fun delete(locationId: UUID)
}

interface AssetRepository {
    fun save(asset: Asset): Asset

    fun findById(assetId: UUID): Asset?

    fun list(filter: AssetFilter, pagination: Pagination): Page<Asset>

    /**
     * La existencia **viva** de [articleId] en esa ubicacion, si la hay.
     *
     * Es la consulta sobre la que descansa `RegisterConsumableIntake`, y su
     * criterio es exactamente el del indice `assets_live_stock_item_unique`:
     * mismo articulo, misma ubicacion --comparando nulos como iguales, de ahi el
     * `NULLS NOT DISTINCT`-- y `status <> 'DECOMMISSIONED'`. Cualquier
     * discrepancia con ese indice convierte una entrada normal en un 500.
     */
    fun findLiveStockItem(articleId: UUID, location: AssetLocation?): Asset?

    /** Los assets que tienen a este como ubicacion. Impide darlo de baja con cosas dentro. */
    fun countChildren(assetId: UUID): Long

    /** La cadena de assets contenedores, del padre hacia arriba. Sostiene el anti-ciclo. */
    fun ancestorsOf(assetId: UUID): List<UUID>

    /** Cuantos assets vivos hay en esa ubicacion. Es lo que se compara con la capacidad declarada. */
    fun countLiveIn(location: AssetLocation): Long

    /** `ACTIVE` u `OVERDUE`: un vencido sigue ocupando el asset igual que uno al dia. */
    fun hasOpenLoan(assetId: UUID): Boolean
}

data class AssetFilter(
    val locationId: UUID? = null,
    val parentAssetId: UUID? = null,
    val ownerId: UUID? = null,
    val status: AssetStatus? = null,
    val type: AssetType? = null,
    val articleId: UUID? = null,
    val categoryId: UUID? = null,
    /** Los huerfanos de una baja de usuario, que es la pregunta que deja abierta `DeactivateUser`. */
    val withoutOwner: Boolean = false,
)

/**
 * Los metadatos de los ficheros del hogar. Los bytes no pasan por aqui: para eso
 * esta [FileStorage].
 *
 * Que las dos mitades vivan en puertos distintos no es ceremonia. La fila esta
 * sujeta a RLS y el byte no, asi que **el aislamiento lo hereda el disco de la
 * fila**: para construir una ruta hay que haber leido antes una fila que ya paso
 * por la politica. Un atajo que derive rutas de algo que no salga de aqui
 * desactiva esa herencia sin producir ningun error visible (ADR-005).
 */
interface StoredFileRepository {
    fun save(file: StoredFile): StoredFile

    /** En cualquiera de sus tres estados. Por RLS, solo si es de este hogar. */
    fun findById(fileId: UUID): StoredFile?

    /** Excluye los borrados y ordena por tamano **descendente** (ver `ListFiles` en 5.7). */
    fun list(filter: StoredFileFilter, pagination: Pagination): Page<StoredFile>

    /**
     * Lo que ocupa el hogar: la suma de `sizeBytes` de sus ficheros vivos,
     * **incluidas las reservas a medias**, que ya ocupan cuota aunque todavia no
     * se puedan adjuntar. Las miniaturas no cuentan: las decide el sistema.
     */
    fun usedBytes(): Long

    /**
     * Si algo lo referencia: un documento, o la foto de un asset, un articulo o
     * una ubicacion.
     *
     * Sostiene las dos reglas que la base de datos **no** puede garantizar
     * enteras. El indice `documents_file_unique` impide que dos documentos
     * compartan fichero, y ahi se acaba: que un documento y una foto lo compartan,
     * o que dos assets compartan foto, no lo impide nadie mas que esto.
     */
    fun isAttached(fileId: UUID): Boolean

    /**
     * Lo que el proceso diario tiene que desenlazar del disco, en sus tres
     * variantes (ver `PurgeUnusedFiles` en 5.7). Cada corte es una fecha distinta
     * porque cada motivo tiene su plazo.
     */
    fun findPurgeable(
        deletedBefore: Instant,
        neverAttachedBefore: Instant,
        reservedBefore: Instant,
    ): List<StoredFile>

    /** Borrado **real** de la fila. Solo el proceso diario, y siempre despues de los bytes. */
    fun delete(fileId: UUID)
}

data class StoredFileFilter(
    /** Con `false` devuelve los subidos y nunca adjuntados: lo que se puede borrar sin perder nada. */
    val attached: Boolean? = null,
    val contentType: StoredContentType? = null,
)

interface DocumentRepository {
    fun save(document: Document): Document

    fun findById(documentId: UUID): Document?

    fun list(filter: DocumentFilter, pagination: Pagination): Page<Document>

    /** Borrado **real**: no lo referencia ninguna otra entidad, asi que no hay historial que preservar. */
    fun delete(documentId: UUID)
}

data class DocumentFilter(
    val assetId: UUID? = null,
    val articleId: UUID? = null,
    val type: DocumentType? = null,
)

/**
 * Serializa los cambios de jerarquia dentro de un hogar.
 *
 * Existe por un agujero que ni la base de datos ni la comprobacion anti-ciclo
 * pueden cerrar por si solas: **dos peticiones simultaneas**. Colgar A de B y B
 * de A a la vez pasa las dos comprobaciones --ninguna transaccion ve el cambio de
 * la otra-- y deja un ciclo que despues nadie puede deshacer, porque recorrer la
 * jerarquia no termina nunca.
 *
 * Un `SELECT ... FOR UPDATE` sobre las dos filas implicadas cerraria el ciclo de
 * dos nodos y no el de tres. Con un cerrojo por hogar se cierran todos, y el
 * precio es nulo en la practica: reorganizar la casa no es una operacion
 * concurrida.
 */
interface HierarchyLock {
    /** Se toma dentro de la transaccion y se suelta sola al cerrarla. */
    fun acquire()
}
