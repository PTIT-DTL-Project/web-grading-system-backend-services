package vn.edu.ptit.web_grading_system.user_service.config;

import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class KeycloakAdminConfig {

    private final KeycloakProperties properties;

    /**
     * Admin client using service account (client_credentials grant).
     * The wgs-user-service client must have service accounts enabled in Keycloak
     * and the service account must be assigned realm-management > manage-users roles.
     */
    @Bean
    public Keycloak keycloakAdminClient() {
        return KeycloakBuilder.builder()
                .serverUrl(properties.serverUrl())
                .realm(properties.realm())
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .clientId(properties.adminClientId())
                .clientSecret(properties.adminClientSecret())
                .build();
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
