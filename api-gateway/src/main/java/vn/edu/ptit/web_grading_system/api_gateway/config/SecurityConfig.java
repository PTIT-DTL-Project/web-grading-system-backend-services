package vn.edu.ptit.web_grading_system.api_gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    // Prometheus scrapes /actuator/prometheus (application.yaml exposes it); without it
    // here the scrape gets 401 and gateway metrics go dark. Named explicitly rather than
    // /actuator/** so /actuator/gateway route mutation stays behind authentication.
    // Review: 2026-09-30, Pullfrog review (feat/DAT-8).
    private static final String[] PUBLIC_PATHS = {
            "/actuator/health",
            "/actuator/info",
            "/actuator/prometheus",
            "/api/v1/auth/**"
    };

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(PUBLIC_PATHS).permitAll()
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> {})
                )
                .build();
    }
}
