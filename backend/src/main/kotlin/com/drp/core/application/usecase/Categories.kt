package com.drp.core.application.usecase

import com.drp.core.application.port.CategoryRepository
import com.drp.platform.page.Page
import com.drp.platform.page.Pagination
import com.drp.core.application.port.SessionClaims
import com.drp.core.domain.BusinessRuleViolation
import com.drp.core.domain.ErrorCode
import com.drp.core.domain.ResourceNotFound
import com.drp.core.domain.catalog.Category
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

/**
 * Las cuatro operaciones del catalogo de categorias.
 *
 * **Ninguna publica evento**, y no es un olvido: crear o retirar una categoria es
 * clasificacion interna del hogar y ningun modulo previsto reacciona a ello. El
 * criterio para entrar en el catalogo de eventos (README 5.2.3) es que alguien lo
 * necesite, no la simetria con las demas entidades.
 */

@Service
class ListCategories(private val categories: CategoryRepository) {

    @Transactional(readOnly = true)
    fun handle(includeRetired: Boolean, pagination: Pagination): Page<Category> =
        categories.list(includeRetired, pagination)
}

@Service
class CreateCategory(
    private val categories: CategoryRepository,
    private val clock: Clock,
) {

    @Transactional
    fun handle(session: SessionClaims, name: String, notes: String?): Category {
        requireNameAvailable(categories, name, exceptId = null)

        val now = clock.instant()
        return categories.save(
            Category(
                id = UUID.randomUUID(),
                name = name.trim(),
                notes = notes,
                createdAt = now,
                updatedAt = now,
                retiredAt = null,
                createdBy = session.memberId,
                updatedBy = session.memberId,
            ),
        )
    }
}

/**
 * Renombrar o anotar. **No toca a los assets ni a los articulos** que la usan:
 * la referencian por identificador, no por texto.
 */
@Service
class UpdateCategory(
    private val categories: CategoryRepository,
    private val clock: Clock,
) {

    @Transactional
    fun handle(session: SessionClaims, categoryId: UUID, name: String, notes: String?): Category {
        val category = categories.findById(categoryId) ?: throw ResourceNotFound("Categoría no encontrada")
        requireNameAvailable(categories, name, exceptId = categoryId)

        return categories.save(
            category.copy(
                name = name.trim(),
                notes = notes,
                updatedAt = clock.instant(),
                updatedBy = session.memberId,
            ),
        )
    }
}

/**
 * Retirada **logica**: los assets y los articulos la referencian por clave ajena,
 * asi que borrar la fila romperia el historial. Deja de ofrecerse al clasificar y
 * quien ya la tenia la conserva.
 */
@Service
class RetireCategory(
    private val categories: CategoryRepository,
    private val clock: Clock,
) {

    @Transactional
    fun handle(session: SessionClaims, categoryId: UUID) {
        val category = categories.findById(categoryId) ?: throw ResourceNotFound("Categoría no encontrada")

        // Retirar dos veces no es un error: el resultado es el mismo y el
        // segundo intento no tiene nada que arreglar. Volver a fijar la fecha
        // falsearia cuando dejo de usarse.
        if (!category.isLive) return

        val now = clock.instant()
        categories.save(category.copy(retiredAt = now, updatedAt = now, updatedBy = session.memberId))
    }
}

/**
 * El nombre es unico **entre las vigentes** del hogar, comparado sin distinguir
 * mayusculas ni acentos.
 *
 * Se comprueba aqui aunque el indice unico ya lo garantice, y las dos vallas
 * hacen falta: el indice convierte el choque en un 500 por violacion de
 * restriccion, y lo que el contrato declara es un `409 CATEGORY_DUPLICATE`. La
 * comprobacion da el mensaje bueno y el indice cierra la carrera entre dos
 * peticiones simultaneas, que ninguna comprobacion previa puede cerrar.
 */
private fun requireNameAvailable(categories: CategoryRepository, name: String, exceptId: UUID?) {
    val clash = categories.findLiveByName(name.trim()) ?: return
    if (clash.id == exceptId) return

    throw BusinessRuleViolation(
        ErrorCode.CATEGORY_DUPLICATE,
        "Ya existe una categoría vigente con ese nombre",
    )
}
