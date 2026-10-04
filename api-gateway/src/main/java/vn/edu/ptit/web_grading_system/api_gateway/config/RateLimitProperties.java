package vn.edu.ptit.web_grading_system.api_gateway.config;

import lombok.Builder;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Fixed-window rate limit for the public change-password endpoint (UC-14): at most
 * {@code maxAttempts} attempts per {@code windowSeconds} for one {@code ip + username},
 * counted in Valkey. Disabled by default ({@code RATE_LIMIT_ENABLED=false}) so local dev
 * needs no cache; Keycloak's brute-force protection works regardless of this switch.
 *
 * <p>Bound from {@code rate-limit} in {@code application.yaml}.
 *
 * <p>Review: 2026-10-03, Phase 1 plan keycloak-password-gateway-plan-2026-10-03-v1.
 */
@ConfigurationProperties(prefix = "rate-limit")
@Builder
public record RateLimitProperties(boolean enabled, int maxAttempts, long windowSeconds) {
}
