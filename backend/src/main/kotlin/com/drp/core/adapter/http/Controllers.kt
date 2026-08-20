package com.drp.core.adapter.http

import com.drp.core.application.port.SessionClaims
import com.drp.core.application.usecase.AcceptInvitation
import com.drp.core.application.usecase.AcceptInvitationCommand
import com.drp.core.application.usecase.ChangePassword
import com.drp.core.application.usecase.CancelHouseholdClosure
import com.drp.core.application.usecase.ChangeUserRole
import com.drp.core.application.usecase.CloseAccount
import com.drp.core.application.usecase.CreateHousehold
import com.drp.core.application.usecase.CreateHouseholdCommand
import com.drp.core.application.usecase.DeactivateUser
import com.drp.core.application.usecase.DeleteIdentityAvatar
import com.drp.core.application.usecase.GetCurrentHousehold
import com.drp.core.application.usecase.HouseholdUser
import com.drp.core.application.usecase.InviteUser
import com.drp.core.application.usecase.InviteUserCommand
import com.drp.core.application.usecase.ListInvitations
import com.drp.core.application.usecase.ListUsers
import com.drp.core.application.usecase.Login
import com.drp.core.application.usecase.LoginCommand
import com.drp.core.application.usecase.ReactivateUser
import com.drp.core.application.usecase.RefreshSession
import com.drp.core.application.usecase.RequestHouseholdClosure
import com.drp.core.application.usecase.RequestPasswordReset
import com.drp.core.application.usecase.ResendVerification
import com.drp.core.application.usecase.ResetPassword
import com.drp.core.application.usecase.RevokeInvitation
import com.drp.core.application.usecase.RevokeSession
import com.drp.core.application.usecase.SetIdentityAvatar
import com.drp.core.application.usecase.VerifyEmail
import com.drp.platform.page.PageResponse
import com.drp.platform.page.Pagination
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartHttpServletRequest

/**
 * Los controladores del enrolamiento: las dieciseis operaciones del contrato que
 * caen en el Hito 1.
 *
 * Son deliberadamente finos. No hay una sola regla de negocio aqui: traducen
 * HTTP a un caso de uso y su resultado a la forma que declara `openapi.yaml`.
 * Cualquier `if` que decida algo del hogar esta en el sitio equivocado --entre
 * otras cosas porque un controlador no se puede probar sin levantar medio
 * Spring.
 *
 * **Ninguno recibe `householdId`.** Sale del token, lo pone el filtro en el
 * `TenantContext` y de ahi lo toma la transaccion.
 */

@RestController
@RequestMapping("/api/v1/households")
class HouseholdController(
    private val createHousehold: CreateHousehold,
    private val getCurrentHousehold: GetCurrentHousehold,
    private val requestClosure: RequestHouseholdClosure,
    private val cancelClosure: CancelHouseholdClosure,
) {

    /**
     * `202` siempre, sin cuerpo y exista o no ya ese correo. Contestar otra cosa
     * permitiria averiguar quien esta registrado.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun create(@Valid @RequestBody input: HouseholdInput) {
        createHousehold.handle(
            CreateHouseholdCommand(
                name = input.name,
                timeZone = input.timeZone,
                adminName = input.admin.name,
                adminEmail = input.admin.email,
                adminPassword = input.admin.password,
            ),
        )
    }

    /**
     * El hogar del token. **No lleva identificador en la ruta** por lo mismo que
     * ningun otro recurso: el hogar sale del token y aceptarlo del cliente seria
     * abrir justo la puerta que la ADR-002 cierra.
     *
     * La lee cualquier miembro: que la casa esta a punto de desaparecer no es
     * informacion reservada a quien administra.
     */
    @GetMapping("/current")
    fun current(): HouseholdResponse = HouseholdResponse.of(getCurrentHousehold.handle())

    /** Solo `HOUSEHOLD_ADMIN`: es la supresion irreversible de la casa entera. */
    @PostMapping("/current/closure")
    @PreAuthorize("hasRole('HOUSEHOLD_ADMIN')")
    fun requestClosure(@AuthenticationPrincipal session: SessionClaims): HouseholdResponse =
        HouseholdResponse.of(requestClosure.handle(session))

    /**
     * Cancelarla es un `DELETE` sobre la baja y no un `POST /cancel`: lo que
     * desaparece es la solicitud, que es un recurso con su sitio en la ruta.
     */
    @DeleteMapping("/current/closure")
    @PreAuthorize("hasRole('HOUSEHOLD_ADMIN')")
    fun cancelClosure(): HouseholdResponse = HouseholdResponse.of(cancelClosure.handle())
}

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val login: Login,
    private val verifyEmail: VerifyEmail,
    private val resendVerification: ResendVerification,
    private val refreshSession: RefreshSession,
    private val revokeSession: RevokeSession,
    private val requestPasswordReset: RequestPasswordReset,
    private val resetPassword: ResetPassword,
    private val changePassword: ChangePassword,
) {

    @PostMapping("/login")
    fun login(@Valid @RequestBody input: LoginInput): TokenPairResponse =
        TokenPairResponse.of(login.handle(LoginCommand(input.email, input.password)))

    @PostMapping("/verify-email")
    fun verifyEmail(@Valid @RequestBody input: TokenInput): TokenPairResponse =
        TokenPairResponse.of(verifyEmail.handle(input.token))

    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun resendVerification(@Valid @RequestBody input: EmailInput) = resendVerification.handle(input.email)

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody input: RefreshInput): TokenPairResponse =
        TokenPairResponse.of(refreshSession.handle(input.refreshToken))

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(@Valid @RequestBody input: RefreshInput) = revokeSession.handle(input.refreshToken)

    @PostMapping("/password-reset")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun requestPasswordReset(@Valid @RequestBody input: EmailInput) = requestPasswordReset.handle(input.email)

    @PostMapping("/password-reset/confirm")
    fun resetPassword(@Valid @RequestBody input: PasswordResetConfirmInput): TokenPairResponse =
        TokenPairResponse.of(resetPassword.handle(input.token, input.newPassword))

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun changePassword(
        @AuthenticationPrincipal session: SessionClaims,
        @Valid @RequestBody input: ChangePasswordInput,
    ) = changePassword.handle(session, input.currentPassword, input.newPassword)
}

@RestController
@RequestMapping("/api/v1/invitations")
class InvitationController(
    private val inviteUser: InviteUser,
    private val listInvitations: ListInvitations,
    private val revokeInvitation: RevokeInvitation,
    private val acceptInvitation: AcceptInvitation,
) {

    @GetMapping
    @PreAuthorize("hasRole('HOUSEHOLD_ADMIN')")
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): PageResponse<InvitationResponse> =
        PageResponse.of(listInvitations.handle(Pagination(page, size)), InvitationResponse::of)

    @PostMapping
    @PreAuthorize("hasRole('HOUSEHOLD_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    fun invite(
        @AuthenticationPrincipal session: SessionClaims,
        @Valid @RequestBody input: InvitationInput,
    ): InvitationResponse =
        InvitationResponse.of(inviteUser.handle(session, InviteUserCommand(input.email, input.role)))

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('HOUSEHOLD_ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revoke(@PathVariable id: UUID) = revokeInvitation.handle(id)

    /** Sin autenticar: lo autoriza el token recibido por correo. */
    @PostMapping("/accept")
    fun accept(@Valid @RequestBody input: InvitationAcceptInput): TokenPairResponse =
        TokenPairResponse.of(
            acceptInvitation.handle(AcceptInvitationCommand(input.token, input.name, input.password)),
        )
}

@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val listUsers: ListUsers,
    private val changeUserRole: ChangeUserRole,
    private val deactivateUser: DeactivateUser,
    private val reactivateUser: ReactivateUser,
    private val closeAccount: CloseAccount,
    private val setAvatar: SetIdentityAvatar,
    private val deleteAvatar: DeleteIdentityAvatar,
    private val urls: SignedFileUrls,
    private val uploadLimiter: UploadRateLimiter,
) {

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "false") includeDeactivated: Boolean,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): PageResponse<UserResponse> =
        PageResponse.of(listUsers.handle(includeDeactivated, Pagination(page, size))) { it.withAvatar() }

    @PatchMapping("/{id}/roles")
    @PreAuthorize("hasRole('HOUSEHOLD_ADMIN')")
    fun changeRole(
        @AuthenticationPrincipal session: SessionClaims,
        @PathVariable id: UUID,
        @Valid @RequestBody input: RoleInput,
    ): UserResponse = changeUserRole.handle(session, id, input.role).withAvatar()

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('HOUSEHOLD_ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deactivate(
        @AuthenticationPrincipal session: SessionClaims,
        @PathVariable id: UUID,
    ) = deactivateUser.handle(session, id)

    /**
     * La activacion como **subrecurso** y no un `PATCH` del estado, la misma
     * pareja que `/modules/{key}/activation` y por su mismo motivo: encender es
     * una operacion con su propio `operationId`, no un efecto que dependa del
     * cuerpo. El apagado se queda en el `DELETE` de arriba, que ya estaba
     * publicado y significa lo que significa.
     */
    @PostMapping("/{id}/activation")
    @PreAuthorize("hasRole('HOUSEHOLD_ADMIN')")
    fun reactivate(
        @AuthenticationPrincipal session: SessionClaims,
        @PathVariable id: UUID,
    ): UserResponse = reactivateUser.handle(session, id).withAvatar()

    /**
     * Cerrar la cuenta propia. **Sin identificador en la ruta**, y no es un
     * atajo: `DELETE /users/{id}` ya existe y significa otra cosa --sacar a
     * alguien del hogar, y solo puede hacerlo quien administra--. Esta es la
     * persona dandose de baja a si misma, asi que el sujeto lo pone el token y no
     * se puede nombrar a nadie mas.
     *
     * Responde `204` y deja la sesion inservible: el cliente tiene que descartar
     * sus tokens y volver a la pantalla de entrar.
     */
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun closeOwnAccount(@AuthenticationPrincipal session: SessionClaims) = closeAccount.handle(session)

    /**
     * «me» resuelve a la **identidad** del token, no a la pertenencia: el avatar
     * es de la persona y sobrevive a cualquier hogar. Por eso no suma al gigabyte
     * de ningun hogar ni tiene politica de RLS detras (README 4.1.1).
     *
     * Recibe el multipart en diferido igual que la subida de un fichero, aunque
     * aqui no haya cuota que reservar: es el mismo camino y una excepcion
     * invitaria a copiarla mal.
     */
    @PutMapping("/me/avatar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun setOwnAvatar(
        @AuthenticationPrincipal session: SessionClaims,
        request: MultipartHttpServletRequest,
    ) {
        uploadLimiter.consume(session.identityId)
        setAvatar.handle(session, MultipartUpload(request))
    }

    @DeleteMapping("/me/avatar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteOwnAvatar(@AuthenticationPrincipal session: SessionClaims) =
        deleteAvatar.handle(session.identityId)

    /**
     * El avatar se firma como cualquier otra imagen, y **no hace falta ninguna
     * consulta mas**: la identidad ya viene resuelta desde el caso de uso, con
     * su clave de almacenamiento dentro.
     */
    private fun HouseholdUser.withAvatar() =
        UserResponse.of(this, identity.avatar?.let { urls.original(it.storageKey) })
}
