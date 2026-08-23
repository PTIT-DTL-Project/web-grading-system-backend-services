package vn.edu.ptit.web_grading_system.submission_service.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * When Keycloak resource-server security is wired in, permit docs paths:
 * .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI().info(new Info()
                .title("Submission Service API")
                .version("v1")
                .description("Presigned uploads, submissions, webhooks"));
    }
}
