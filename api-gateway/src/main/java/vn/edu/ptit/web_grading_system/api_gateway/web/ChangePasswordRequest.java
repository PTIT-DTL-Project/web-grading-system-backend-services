package vn.edu.ptit.web_grading_system.api_gateway.web;

import jakarta.validation.constraints.NotBlank;

/**
 * Body of {@code POST /api/v1/account/change-password}: username (or email — the realm
 * allows login with email) plus the current and the new password. All three are required;
 * blank values answer {@code validation_failed}. The {@code @NotBlank} constraints back
 * the controller's {@code @Valid} parameter, and {@code PasswordChangeService} re-checks
 * them so the flow does not depend on bean validation having run.
 *
 * <p>Review: 2026-10-03, Phase 1 plan keycloak-password-gateway-plan-2026-10-03-v1.
 */
public record ChangePasswordRequest(@NotBlank String username, @NotBlank String currentPassword,
        @NotBlank String newPassword) {

    /**
     * Named factory so call sites never write {@code new X(a, b, c)} (project convention);
     * Jackson binds the canonical constructor directly.
     */
    public static ChangePasswordRequest of(String username, String currentPassword,
            String newPassword) {
        return new ChangePasswordRequest(username, currentPassword, newPassword);
    }
}
