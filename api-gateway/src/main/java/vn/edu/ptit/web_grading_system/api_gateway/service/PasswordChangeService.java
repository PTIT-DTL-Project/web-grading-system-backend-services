package vn.edu.ptit.web_grading_system.api_gateway.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import vn.edu.ptit.web_grading_system.api_gateway.web.ChangePasswordRequest;

/**
 * The four-step change-password flow of UC-14 (plan §5): verify current
 * password → look up user → set the new password. Returns an empty {@code Mono} on
 * success, which the controller turns into {@code 204 No Content}; every failure is a
 * {@link ChangePasswordException} carrying the envelope's status + machine code.
 *
 * <p>The order is load-bearing: verification runs BEFORE the user lookup, and "wrong
 * password" and "no such user" answer the same {@code current_password_invalid} — an
 * unauthenticated caller must never learn whether a username exists in the realm.
 *
 * <p>Review: 2026-10-03, Phase 1 plan keycloak-password-gateway-plan-2026-10-03-v1.
 */
@Service
@RequiredArgsConstructor
public class PasswordChangeService {

    private final KeycloakAdminClient keycloak;

    public Mono<Void> changePassword(ChangePasswordRequest request) {
        if (!StringUtils.hasText(request.username()) || !StringUtils.hasText(request.currentPassword())
                || !StringUtils.hasText(request.newPassword())) {
            return Mono.error(ChangePasswordException.validationFailed());
        }
        return verifyCurrentPassword(request);
    }

    /** Step 2 — must run before any lookup, so an unproven caller cannot enumerate. */
    private Mono<Void> verifyCurrentPassword(ChangePasswordRequest request) {
        return keycloak.verifyPassword(request.username(), request.currentPassword())
                .onErrorMap(error -> ChangePasswordException.identityProviderUnavailable(error))
                .flatMap(correct -> correct
                        ? lookupAndReset(request)
                        : Mono.error(ChangePasswordException.currentPasswordInvalid()));
    }

    /** Steps 3 + 4. */
    private Mono<Void> lookupAndReset(ChangePasswordRequest request) {
        return keycloak.findUserId(request.username())
                .onErrorMap(error -> ChangePasswordException.identityProviderUnavailable(error))
                // MUST stay after onErrorMap: empty means "no such user" and answers the
                // SAME code as a wrong password (no user enumeration).
                .switchIfEmpty(Mono.defer(() -> Mono.error(ChangePasswordException.currentPasswordInvalid())))
                .flatMap(userId -> keycloak.resetPassword(userId, request.newPassword())
                        .onErrorMap(error -> ChangePasswordException.identityProviderUnavailable(error))
                        // FALSE = Keycloak's password policy rejected the new password.
                        .flatMap(applied -> applied
                                ? Mono.empty()
                                : Mono.error(ChangePasswordException.weakPassword())));
    }
}
