package vn.edu.ptit.web_grading_system.api_gateway.service;

import reactor.core.publisher.Mono;

/**
 * The three Keycloak calls the change-password flow needs, behind an interface so
 * {@link PasswordChangeService} can be unit-tested without a network.
 *
 * <p>Shared contract: an <em>error</em> always means "the provider was unreachable or
 * answered something unexpected" and is turned into {@code identity_provider_unavailable}
 * (502) by the service — never into a verdict about the caller's credentials. Keys and
 * bodies are interpreted here, envelope codes down there.
 *
 * <p>Review: 2026-10-03, Phase 1 plan keycloak-password-gateway-plan-2026-10-03-v1;
 * Phase 3 PKCE plan .opencode/plan/phase-3-pkce.md (D11 — dedicated verify client).
 */
public interface KeycloakAdminClient {

    /**
     * Verifies the CURRENT password with a resource-owner password grant. Since Phase 3
     * (2026-10-03, D11) that grant runs against the gateway's own confidential client
     * {@code wgs-password-verify} (sent with {@code client_secret}) rather than the
     * browser's {@code web-grading-fe}, whose Direct Access Grants are switched off —
     * a blank configured secret means "no client_secret on the wire", which a CONFIDENTIAL
     * client rejects with {@code unauthorized_client} (-> 502): fail-loud, not a working
     * public-client fallback. The issued tokens are discarded inside the implementation —
     * never persisted, never forwarded.
     *
     * @return {@code TRUE} = password correct, including a Keycloak
     *         {@code "Account is not fully set up"} answer (some required action pending —
     *         most often forced UPDATE_PASSWORD, but the marker also fires for
     *         VERIFY_PROFILE / VERIFY_EMAIL / CONFIGURE_TOTP — the password is right, only
     *         the required action blocks the grant);
     *         {@code FALSE} = Keycloak rejected the credentials
     *         ({@code invalid_grant} / {@code Invalid user credentials})
     */
    Mono<Boolean> verifyPassword(String username, String password);

    /**
     * Looks the user up through the admin API with the service-account credentials.
     *
     * <p>Keycloak splits user search into two query parameters ({@code username} and
     * {@code email}) while the realm accepts login by email, so the implementation picks
     * the parameter from the shape of {@code username} and then matches the returned rows
     * EXACTLY — the search itself is a prefix query.
     *
     * @return the id of the user whose {@code username} equals {@code username}, or whose
     *         {@code email} does when the value is an address (case-insensitive);
     *         <b>empty</b> when Keycloak found nobody — the service maps that to the same
     *         {@code current_password_invalid} as a wrong password so the endpoint cannot
     *         be used to enumerate realm users
     */
    Mono<String> findUserId(String username);

    /**
     * Sets the new password via {@code PUT .../users/{id}/reset-password} with the flat
     * credential body.
     *
     * @return {@code TRUE} = applied (204); {@code FALSE} = Keycloak rejected the new
     *         password (400 — password policy), which the service reports as
     *         {@code weak_password}
     */
    Mono<Boolean> resetPassword(String userId, String newPassword);
}
