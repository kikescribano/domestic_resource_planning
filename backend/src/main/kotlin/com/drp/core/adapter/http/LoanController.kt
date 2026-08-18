package com.drp.core.adapter.http

import com.drp.core.application.port.LoanFilter
import com.drp.core.application.port.SessionClaims
import com.drp.core.application.usecase.ConfirmReturn
import com.drp.core.application.usecase.GetLoan
import com.drp.core.application.usecase.GetLoanForExternal
import com.drp.core.application.usecase.ListLoans
import com.drp.core.application.usecase.LoanAccess
import com.drp.core.application.usecase.StartLoan
import com.drp.core.application.usecase.StartLoanCommand
import com.drp.core.domain.loan.LoanStatus
import com.drp.platform.page.PageResponse
import com.drp.platform.page.Pagination
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/loans")
class LoanController(
    private val startLoan: StartLoan,
    private val getLoan: GetLoan,
    private val getLoanForExternal: GetLoanForExternal,
    private val confirmReturn: ConfirmReturn,
    private val listLoans: ListLoans,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun start(
        @AuthenticationPrincipal session: SessionClaims,
        @Valid @RequestBody input: LoanInput,
    ): LoanResponse = LoanResponse.of(
        startLoan.handle(
            session,
            StartLoanCommand(
                assetId = input.assetId!!,
                lender = input.lender!!.toCommand(),
                borrower = input.borrower!!.toCommand(),
                dueAt = input.dueAt,
                notes = input.notes,
            ),
        ),
    )

    /**
     * **Solo para usuarios del hogar.** Un token acotado no llega aqui: alcanza
     * un prestamo concreto y no la lista de lo que el hogar tiene fuera de casa.
     * Lo impide el tipo del principal, no una comprobacion escrita.
     */
    @GetMapping
    fun list(
        @AuthenticationPrincipal session: SessionClaims,
        @RequestParam(required = false) status: LoanStatus?,
        @RequestParam(required = false) assetId: UUID?,
        @RequestParam(defaultValue = "false") open: Boolean,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): PageResponse<LoanResponse> = PageResponse.of(
        listLoans.handle(LoanFilter(status = status, assetId = assetId, open = open), Pagination(page, size)),
    ) { LoanResponse.of(it) }

    /**
     * **La unica operacion de la API que devuelve dos formas distintas segun
     * quien pregunta.**
     *
     * Lo que decide no es el recurso sino la credencial, y se despacha por el
     * tipo del principal en lugar de por un `if` sobre un rol: con sesion del
     * hogar llega un `SessionClaims` y con token acotado un [LoanAccess], asi
     * que la rama equivocada **no compila** en lugar de fallar en produccion.
     *
     * Y en la rama externa el identificador viene de la credencial, no de la
     * ruta: el `id` del camino ya lo comprobo el filtro contra el del token, y
     * usarlo aqui otra vez seria darle una segunda oportunidad de no coincidir.
     */
    @GetMapping("/{id}")
    fun get(
        @AuthenticationPrincipal principal: Any,
        @PathVariable id: UUID,
    ): Any = when (principal) {
        is LoanAccess -> ExternalLoanResponse.of(getLoanForExternal.handle(principal))
        else -> LoanResponse.of(getLoan.handle(id))
    }

    @PostMapping("/{id}/return")
    fun confirmReturn(
        @AuthenticationPrincipal principal: Any,
        @PathVariable id: UUID,
    ): Any = when (principal) {
        is LoanAccess -> ExternalLoanResponse.of(confirmReturn.handle(principal))
        is SessionClaims -> LoanResponse.of(confirmReturn.handle(principal, id))
        else -> throw IllegalStateException("Principal no reconocido: ${principal::class.simpleName}")
    }
}
