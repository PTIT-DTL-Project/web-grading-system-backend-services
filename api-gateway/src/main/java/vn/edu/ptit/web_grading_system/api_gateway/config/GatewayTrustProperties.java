package vn.edu.ptit.web_grading_system.api_gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Trust settings the gateway stamps on every forwarded request so downstream services can
 * tell a gateway-attested request from a direct one.
 *
 * <ul>
 *   <li>{@code secret} → {@code X-Gateway-Secret}. Blank by default and treated as
 *       fail-closed: with no secret the gateway injects no identity at all, so every
 *       protected downstream route answers 401 instead of trusting an unattested header.</li>
 *   <li>{@code allowedRoles} → filters the Keycloak {@code realm_access.roles} claim before
 *       it becomes {@code X-User-Roles}. This is the single place role policy lives: it
 *       drops noise roles ({@code offline_access}, {@code account}, …) and caps what a
 *       service can ever be told a caller is. An empty list forwards no roles at all, so
 *       every {@code @PreAuthorize} endpoint answers 403.</li>
 * </ul>
 *
 * <p>Bound from {@code gateway.security} in {@code application.yaml}, which reads the
 * {@code GATEWAY_TRUSTED_SECRET} and {@code GATEWAY_ALLOWED_ROLES} environment variables.
 * The allowlist is policy rather than a secret, so its default lives in the yaml.
 *
 * <p>Review: 2026-09-30, Pullfrog review (feat/DAT-8) — the X-User-* trust model needed a
 * boundary before services were allowed to believe it.
 */
@ConfigurationProperties(prefix = "gateway.security")
public record GatewayTrustProperties(String secret, List<String> allowedRoles) {
}
