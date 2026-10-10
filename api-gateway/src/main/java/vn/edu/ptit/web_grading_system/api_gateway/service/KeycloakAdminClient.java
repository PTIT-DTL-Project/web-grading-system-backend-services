package vn.edu.ptit.web_grading_system.api_gateway.service;

import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

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

    /**
     * Creates a user with an explicit {@code UPDATE_PASSWORD} required action so the
     * first login forces a password change (bulk account import). {@code lastName}
     * is sent because the realm's user profile requires it — without it every
     * fresh account stalls on the update-profile wall after login.
     *
     * <p>Review: 2026-10-10, missing-lastName login wall.
     *
     * @return the new user id parsed from the {@code 201 Location} header
     * @throws CreateUserException on any non-201 answer, carrying the status so
     *         the caller can tell duplicates/races ({@code 409}) and invalid
     *         input ({@code 400}) apart from provider failures
     */
    Mono<String> createUser(String username, String email, String firstName, String lastName);

    /**
     * Same wire call as {@link #resetPassword}, but {@code temporary: true} — Keycloak
     * additionally stamps the {@code UPDATE_PASSWORD} required action itself, so the
     * credential doubles as the forced-change trigger for freshly imported accounts.
     *
     * @return {@code TRUE} = applied (204); {@code FALSE} = rejected (400)
     */
    Mono<Boolean> setTemporaryPassword(String userId, String newPassword);

    /**
     * Fetches a realm role representation ({@code id} + {@code name}) for
     * role-mapping assignment.
     *
     * @return map with {@code id} and {@code name}; error when absent or refused
     */
    Mono<Map<String, String>> findRealmRole(String roleName);

    /**
     * Assigns realm roles ({@code id} + {@code name} maps) to a user.
     */
    Mono<Void> assignRealmRoles(String userId, List<Map<String, String>> roles);

    /**
     * A short-lived session over the same five calls above, sharing ONE admin
     * token fetched once and memoized for the session's lifetime. Per-call
     * tokens stay the default (low-volume flows, no shared state); bulk flows
     * must use a session instead — N users × per-call grants is thousands of
     * token requests otherwise.
     *
     * <p>The session object itself is short-lived by construction (one per
     * import request): never store it in a field, so concurrent imports can
     * never share or race on a token.
     *
     * <p>Review: 2026-10-09, bulk user import plan (Pullfrog: token-per-call
     * amplification).
     */
    interface BulkOperations {

        Mono<String> findUserId(String username);

        Mono<String> createUser(String username, String email, String firstName, String lastName);

        Mono<Boolean> setTemporaryPassword(String userId, String newPassword);

        Mono<Map<String, String>> findRealmRole(String roleName);

        Mono<Void> assignRealmRoles(String userId, List<Map<String, String>> roles);
    }

    BulkOperations bulk();
}
