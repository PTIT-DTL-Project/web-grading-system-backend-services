package vn.edu.ptit.web_grading_system.user_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Keycloak Admin API connection settings.
 * Bound from the {@code keycloak} prefix in application.yaml.
 */
@ConfigurationProperties(prefix = "keycloak")
public record KeycloakProperties(
        String serverUrl,
        String realm,
        String adminClientId,
        String adminClientSecret
) {}
