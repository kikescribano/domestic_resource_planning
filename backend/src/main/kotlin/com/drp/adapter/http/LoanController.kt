package com.drp.adapter.http

import com.drp.application.port.LoanFilter
import com.drp.application.port.Pagination
import com.drp.application.port.SessionClaims
import com.drp.application.usecase.GetLoan
import com.drp.application.usecase.ListLoans
import com.drp.application.usecase.StartLoan
import com.drp.application.usecase.StartLoanCommand
import com.drp.domain.loan.LoanStatus
import jakarta.validation.Valid
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
import java.util.UUID

@RestController
@RequestMapping("/api/v1/loans")
class LoanController(
    private val startLoan: StartLoan,
    private val getLoan: GetLoan,
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

    @GetMapping("/{id}")
    fun get(
        @AuthenticationPrincipal session: SessionClaims,
        @PathVariable id: UUID,
    ): LoanResponse = LoanResponse.of(getLoan.handle(id))
}
