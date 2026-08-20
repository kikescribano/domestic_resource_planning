package com.drp.core.application.usecase

import com.drp.core.application.port.SessionClaims
import com.drp.core.application.port.TagRepository
import com.drp.core.domain.catalog.Tag
import com.drp.platform.error.BusinessRuleViolation
import com.drp.platform.error.ErrorCode
import com.drp.platform.error.ResourceNotFound
import com.drp.platform.page.Page
import com.drp.platform.page.Pagination
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

/**
 * Las cuatro operaciones del catalogo de etiquetas.
 *
 * Son las mismas cuatro que las de categorias, y eso no es simetria de adorno:
 * una etiqueta es el mismo problema --un vocabulario del hogar que clasifica
 * assets-- con otra cardinalidad. Poner y quitar etiquetas a un asset **no esta
 * aqui**: viaja en `tagIds` dentro de `CreateAsset` y `UpdateAsset`, porque
 * etiquetar es corregir la ficha de un asset y no una operacion del catalogo.
 *
 * **Ninguna publica evento**, por el mismo motivo que las de categorias: es
 * clasificacion interna del hogar y ningun modulo previsto reacciona a ella.
 */

@Service
class ListTags(private val tags: TagRepository) {

    /**
     * El `q` alimenta el autocompletado del campo de etiquetas, que es el uso
     * principal de esta operacion: la pantalla del catalogo la pide entera y el
     * campo de un asset la pide filtrada tecla a tecla.
     */
    @Transactional(readOnly = true)
    fun handle(includeRetired: Boolean, query: String?, pagination: Pagination): Page<Tag> =
        tags.list(includeRetired, query?.takeIf { it.isNotBlank() }, pagination)
}

/**
 * Crear una etiqueta, o **revivir la que ya existiera retirada con ese nombre**.
 *
 * Las dos mitades son la misma decision y salen del indice unico de la `V17`,
 * que **no es parcial por retirada** al contrario que el de categorias. Alli el
 * parcial es inofensivo porque un asset tiene una sola categoria; aqui dos
 * etiquetas del mismo nombre podrian acabar sobre el mismo asset y pintarlo dos
 * veces sin que nada estuviera roto.
 *
 * Con el indice completo, crear «Camping» teniendo una «camping» retirada seria
 * un `409` sobre una fila que el usuario no ve --el peor error posible: cierto y
 * sin explicacion--. Revivirla es lo mismo que hace `RegisterConsumableIntake`
 * con un articulo, resolverlo creandolo si hace falta, y de paso le da deshacer
 * a la retirada sin necesitar una operacion propia.
 *
 * Devuelve ademas **si la creo o la revivio**, que es lo que deja al adaptador
 * responder `201` o `200` como ya hace la entrada de un consumible.
 */
@Service
class CreateTag(
    private val tags: TagRepository,
    private val clock: Clock,
) {

    data class Result(val tag: Tag, val created: Boolean)

    @Transactional
    fun handle(session: SessionClaims, name: String): Result {
        val trimmed = name.trim()
        val now = clock.instant()
        val existing = tags.findByName(trimmed)

        if (existing != null) {
            // Viva y con ese nombre: no hay nada que hacer y tampoco nada que
            // fallar. Pedir dos veces la misma etiqueta es el gesto normal de un
            // campo que autocompleta, no un error.
            if (existing.isLive) return Result(existing, created = false)

            return Result(
                tags.save(
                    existing.copy(
                        // Se conserva el nombre tal y como se acaba de escribir:
                        // revivir «camping» escribiendo «Camping» deja lo escrito
                        // ahora, que es lo que la persona tiene delante.
                        name = trimmed,
                        retiredAt = null,
                        updatedAt = now,
                        updatedBy = session.memberId,
                    ),
                ),
                created = false,
            )
        }

        return Result(
            tags.save(
                Tag(
                    id = UUID.randomUUID(),
                    name = trimmed,
                    createdAt = now,
                    updatedAt = now,
                    retiredAt = null,
                    createdBy = session.memberId,
                    updatedBy = session.memberId,
                ),
            ),
            created = true,
        )
    }
}

/**
 * Renombrar. **No toca a los assets que la llevan**: la referencian por
 * identificador, no por texto.
 *
 * Es literalmente la razon por la que la etiqueta es un catalogo y no una
 * columna de texto, asi que existir es parte de la decision: con texto, corregir
 * una falta de ortografia obligaria a recorrer todos los assets del hogar.
 */
@Service
class UpdateTag(
    private val tags: TagRepository,
    private val clock: Clock,
) {

    @Transactional
    fun handle(session: SessionClaims, tagId: UUID, name: String): Tag {
        val tag = tags.findById(tagId) ?: throw ResourceNotFound("Etiqueta no encontrada")
        val trimmed = name.trim()

        // Aqui si es un choque de verdad y no algo que revivir: renombrar a un
        // nombre ocupado dejaria dos etiquetas iguales sobre el mismo asset, que
        // es lo que el indice completo impide. Se comprueba antes para dar el
        // `409` que declara el contrato en vez del `500` de la restriccion, y el
        // indice cierra la carrera entre dos peticiones simultaneas.
        tags.findByName(trimmed)?.takeIf { it.id != tagId }?.let {
            throw BusinessRuleViolation(ErrorCode.TAG_DUPLICATE, "Ya existe una etiqueta con ese nombre")
        }

        return tags.save(tag.copy(name = trimmed, updatedAt = clock.instant(), updatedBy = session.memberId))
    }
}

/**
 * Retirada **logica**, como la de una categoria y por un motivo mas fuerte:
 * borrarla se llevaria por delante la clasificacion de todos los assets que la
 * tuvieran, y eso no se puede deshacer.
 *
 * Deja de ofrecerse al etiquetar y de sugerirse al escribir; los assets que ya
 * la llevaban la conservan y la siguen enseniando. Quitarsela a uno es editar
 * ese asset, que es donde se puso.
 */
@Service
class RetireTag(
    private val tags: TagRepository,
    private val clock: Clock,
) {

    @Transactional
    fun handle(session: SessionClaims, tagId: UUID) {
        val tag = tags.findById(tagId) ?: throw ResourceNotFound("Etiqueta no encontrada")

        // Retirar dos veces no es un error: el resultado es el mismo y volver a
        // fijar la fecha falsearia cuando dejo de usarse. Mismo criterio que
        // `RetireCategory`.
        if (!tag.isLive) return

        val now = clock.instant()
        tags.save(tag.copy(retiredAt = now, updatedAt = now, updatedBy = session.memberId))
    }
}

/**
 * Resuelve las etiquetas que llegan en el cuerpo de un alta o de una
 * modificacion de asset.
 *
 * **Es la valla del aislamiento de este hito.** Un `tagId` es una referencia
 * dentro del cuerpo, que es la segunda forma de ataque del barrido
 * (`TenantIsolationSweepTest`) y la que la clave ajena no detiene limpiamente:
 * la fila ajena es invisible a un `SELECT` por RLS y aun asi se puede **nombrar**,
 * asi que sin resolverla aqui la compuesta `(household_id, tag_id)` la
 * rechazaria con un error de restriccion --un `500`-- en vez del `404` que
 * declara el contrato.
 *
 * Y **una etiqueta retirada no se puede poner**, igual que no se puede clasificar
 * con una categoria retirada: retirada significa que el hogar dejo de usarla.
 */
@Service
class TagReferenceResolver(private val tags: TagRepository) {

    fun resolveAll(tagIds: List<UUID>): List<UUID> {
        tagIds.distinct().forEach { tagId ->
            val tag = tags.findById(tagId) ?: throw ResourceNotFound("Etiqueta no encontrada")
            if (!tag.isLive) throw ResourceNotFound("La etiqueta está retirada")
        }
        return tagIds.distinct()
    }
}
