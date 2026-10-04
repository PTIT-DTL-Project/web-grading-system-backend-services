package vn.edu.ptit.web_grading_system.api_gateway.config;

import lombok.Builder;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Keycloak settings behind the gateway's self-service change-password endpoint (UC-14).
 *
 * <ul>
 *   <li>{@code issuerUri} → realm issuer, e.g. {@code https://host/realms/ptit-wgs}; also
 *       the base of the token endpoint used for both grants.</li>
 *   <li>{@code adminClientId} / {@code adminClientSecret} → the confidential service
 *       account ({@code wgs-user-service}) that looks users up and resets passwords.
 *       The secret comes from the environment only — it must never reach the frontend.</li>
 *   <li>{@code passwordClientId} / {@code passwordClientSecret} → the client whose
 *       resource-owner password grant verifies the current password. Since Phase 3
 *       (2026-10-03, D11) that is the dedicated CONFIDENTIAL client
 *       {@code wgs-password-verify}: Phase 3 turns Direct Access Grants off on
 *       {@code web-grading-fe}, so the gateway can no longer reuse the browser's client
 *       for this grant. The secret comes from the environment only — it must never reach
 *       the frontend. An EMPTY secret keeps the pre-Phase-3 behaviour (public client,
 *       no {@code client_secret} in the form) so nothing breaks for a deployment that
 *       has not set the new variable yet.</li>
 * </ul>
 *
 * <p>The admin base URI is derived from {@code issuerUri} (everything from {@code /realms/}
 * onward is stripped) rather than configured separately, so admin calls always target the
 * same Keycloak server as the configured issuer — with the realm taken from the issuer too.
 *
 * <p>Bound from {@code keycloak.admin} in {@code application.yaml}, which reads the
 * {@code KEYCLOAK_ISSUER_URI}, {@code KEYCLOAK_ADMIN_CLIENT_ID},
 * {@code KEYCLOAK_ADMIN_CLIENT_SECRET}, {@code KEYCLOAK_PASSWORD_CLIENT_ID} and
 * {@code KEYCLOAK_PASSWORD_CLIENT_SECRET} variables.
 *
 * <p>Review: 2026-10-03, Phase 1 plan keycloak-password-gateway-plan-2026-10-03-v1;
 * Phase 3 PKCE plan .opencode/plan/phase-3-pkce.md (D11).
 */
@ConfigurationProperties(prefix = "keycloak.admin")
@Builder
public record KeycloakAdminProperties(String issuerUri, String adminClientId,
        String adminClientSecret, String passwordClientId, String passwordClientSecret) {

    private static final String REALM_PREFIX = "/realms/";

    /**
     * Server root of the issuer, e.g. {@code https://host}. The admin API is relative to
     * the Keycloak server, never to the realm issuer URL.
     *
     * @throws IllegalStateException if {@code issuerUri} is not a realm issuer — raised
     *         while the admin client builds its URLs at startup, so a broken config fails
     *         loud instead of turning every password change into a 502
     */
    public String adminBaseUri() {
        return issuerUri.substring(0, realmStart());
    }

    /**
     * Realm segment of the issuer, e.g. {@code ptit-wgs}.
     *
     * @throws IllegalStateException see {@link #adminBaseUri()}
     */
    public String realm() {
        return issuerUri.substring(realmStart() + REALM_PREFIX.length());
    }

    private int realmStart() {
        int index = issuerUri.indexOf(REALM_PREFIX);
        if (index < 0) {
            throw new IllegalStateException("keycloak.admin.issuer-uri must contain '"
                    + REALM_PREFIX + "' (expected like https://host/realms/ptit-wgs) but was: "
                    + issuerUri);
        }
        return index;
    }
}
