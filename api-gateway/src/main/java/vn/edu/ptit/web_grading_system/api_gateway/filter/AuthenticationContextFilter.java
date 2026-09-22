package vn.edu.ptit.web_grading_system.api_gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * GlobalFilter that runs after JWT authentication is validated by Spring Security.
 * Extracts user identity and roles from the validated JWT claims, then injects
 * them as HTTP request headers for downstream microservices.
 *
 * Security note: strips any incoming X-User-* headers from the original client
 * request before setting our own, preventing clients from spoofing identity.
 */
@Component
public class AuthenticationContextFilter implements GlobalFilter, Ordered {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_EMAIL = "X-User-Email";
    private static final String HEADER_USER_ROLES = "X-User-Roles";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal()
                .filter(principal -> principal instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .map(jwtAuth -> {
                    var jwt = jwtAuth.getToken();

                    String userId = jwt.getSubject();
                    String email = jwt.getClaimAsString("email");
                    String roles = extractRoles(jwt.getClaims());

                    var mutatedRequest = exchange.getRequest().mutate()
                            // Strip client-provided identity headers first (security)
                            .headers(headers -> {
                                headers.remove(HEADER_USER_ID);
                                headers.remove(HEADER_USER_EMAIL);
                                headers.remove(HEADER_USER_ROLES);
                            })
                            // Inject validated identity from JWT
                            .header(HEADER_USER_ID, userId != null ? userId : "")
                            .header(HEADER_USER_EMAIL, email != null ? email : "")
                            .header(HEADER_USER_ROLES, roles)
                            .build();

                    return exchange.mutate().request(mutatedRequest).build();
                })
                .defaultIfEmpty(exchange)
                .flatMap(chain::filter);
    }

    /**
     * Extracts realm roles from the JWT claim {@code realm_access.roles},
     * filtering to only include roles prefixed with "ROLE_" to exclude
     * Keycloak-internal roles (e.g., "offline_access", "uma_authorization").
     *
     * @param claims all JWT claims
     * @return comma-separated role string, e.g. "ROLE_STUDENT" or ""
     */
    private String extractRoles(Map<String, Object> claims) {
        Object realmAccess = claims.get("realm_access");
        if (!(realmAccess instanceof Map<?, ?> realmAccessMap)) {
            return "";
        }
        Object rolesObj = realmAccessMap.get("roles");
        if (!(rolesObj instanceof List<?> rolesList)) {
            return "";
        }
        return rolesList.stream()
                .filter(r -> r instanceof String)
                .map(r -> (String) r)
                .filter(r -> r.startsWith("ROLE_"))
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }

    @Override
    public int getOrder() {
        // Spring Security filters run at -100.
        // This filter runs after them at 0 to read the validated JWT principal.
        return 0;
    }
}
