package vn.edu.ptit.web_grading_system.user_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Secret the API Gateway stamps on every forwarded request as {@code X-Gateway-Secret}.
 * Bound from {@code gateway.security.secret} in {@code application.yaml}.
 */
@ConfigurationProperties(prefix = "gateway.security")
public record GatewayTrustProperties(String secret) {
}
