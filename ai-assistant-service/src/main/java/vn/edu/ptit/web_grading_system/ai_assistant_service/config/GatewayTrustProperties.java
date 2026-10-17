package vn.edu.ptit.web_grading_system.ai_assistant_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.security")
public record GatewayTrustProperties(String secret) {
}
