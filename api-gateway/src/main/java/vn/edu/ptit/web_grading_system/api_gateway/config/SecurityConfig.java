package vn.edu.ptit.web_grading_system.api_gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;

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
            "/actuator/prometheus"
    };

    /**
     * Change-password path (UC-14) gets its own chain WITHOUT oauth2ResourceServer:
     * permitAll only relaxes authorization, while the resource-server filter resolves any
     * incoming Authorization header first and answers 401 for an expired/invalid token
     * before the permitAll rule is ever consulted. The FE attaches its token whenever a
     * session exists (http.ts request interceptor), so on the old single chain a stale
     * token broke the "this endpoint never 401s" invariant. With no bearer processing on
     * this chain the guarantee is structural: a bad header here cannot produce 401.
     * Review: 2026-10-04, Pullfrog review (permitAll does not guarantee no-401).
     */
    @Bean
    @Order(1)
    public SecurityWebFilterChain changePasswordFilterChain(ServerHttpSecurity http) {
        return http
                .securityMatcher(ServerWebExchangeMatchers.pathMatchers("/api/v1/account/**"))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .anyExchange().permitAll()
                )
                .build();
    }

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        // Everything else keeps the resource server. The change-password endpoint is not
        // listed here because its dedicated chain above already matched it first; the
        // endpoint itself only exists when rate-limit.enabled=true (application.yaml).
        // Review: 2026-10-04, Pullfrog review (unthrottled public endpoint).
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
