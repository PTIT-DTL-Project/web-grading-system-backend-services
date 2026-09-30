package vn.edu.ptit.web_grading_system.submission_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Secret the API Gateway stamps on every forwarded request as {@code X-Gateway-Secret}.
 * The service only builds a SecurityContext for requests that present it, so a caller
 * reaching the service directly cannot assert its own X-User-Id.
 *
 * <p>Bound from {@code gateway.security.secret} in {@code application.yaml}, which reads
 * the {@code GATEWAY_TRUSTED_SECRET} environment variable. Blank by default and treated
 * as fail-closed — see
 * {@link vn.edu.ptit.web_grading_system.submission_service.security.HeaderAuthenticationFilter}.
 *
 * <p>Review: 2026-09-30, Pullfrog review (feat/DAT-8).
 */
@ConfigurationProperties(prefix = "gateway.security")
public record GatewayTrustProperties(String secret) {
}
