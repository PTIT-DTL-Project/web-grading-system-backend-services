package vn.edu.ptit.web_grading_system.api_gateway.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import vn.edu.ptit.web_grading_system.api_gateway.config.GatewayTrustProperties;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * GlobalFilter that runs after JWT authentication is validated by Spring Security.
 * Extracts user identity from the validated JWT claims and injects it as HTTP request
 * headers for downstream microservices, together with {@code X-Gateway-Secret} so the
 * services can trust the headers came from this gateway.
 *
 * Security note: the incoming {@code X-User-*} and {@code X-Gateway-Secret} headers are
 * stripped unconditionally, before the principal is inspected, so a client-supplied
 * identity is never forwarded — the guarantee does not depend on the exchange carrying
 * an authenticated principal or on every route staying authenticated.
 *
 * Roles ride the same channel as identity (Review: 2026-09-30, Pullfrog review feat/DAT-8):
 * they are read from the already-validated {@code realm_access.roles} claim, filtered by
 * {@link GatewayTrustProperties#allowedRoles()} and only forwarded alongside the trust
 * secret, so forging them requires the same secret that already lets a caller forge
 * {@code X-User-Id}. When the filtered set is empty the header is omitted entirely, which
 * makes every downstream {@code @PreAuthorize} fail closed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationContextFilter implements GlobalFilter, Ordered {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_EMAIL = "X-User-Email";
    private static final String HEADER_USER_ROLES = "X-User-Roles";
    private static final String HEADER_GATEWAY_SECRET = "X-Gateway-Secret";

    /** Reports a missing secret once instead of once per request. */
    private static final AtomicBoolean SECRET_WARNED = new AtomicBoolean(false);

    private final GatewayTrustProperties properties;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest stripped = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(HEADER_USER_ID);
                    headers.remove(HEADER_USER_EMAIL);
                    headers.remove(HEADER_USER_ROLES);
                    headers.remove(HEADER_GATEWAY_SECRET);
                })
                .build();
        ServerWebExchange cleaned = exchange.mutate().request(stripped).build();

        return cleaned.getPrincipal()
                .filter(principal -> principal instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .map(jwtAuth -> withIdentity(cleaned, jwtAuth.getToken()))
                .defaultIfEmpty(cleaned)
                .flatMap(chain::filter);
    }

    /**
     * Injects the identity of an already-validated JWT plus the trust secret. With no
     * configured secret the exchange is forwarded stripped: downstream then rejects it,
     * so an unset {@code GATEWAY_TRUSTED_SECRET} degrades to 401 rather than to trusting
     * an unattested header.
     */
    private ServerWebExchange withIdentity(ServerWebExchange exchange, Jwt jwt) {
        String secret = properties.secret();
        if (!StringUtils.hasText(secret)) {
            if (SECRET_WARNED.compareAndSet(false, true)) {
                log.error("gateway.security.secret is blank — identity headers are not "
                                + "injected and every protected downstream route will answer 401. "
                                + "Set GATEWAY_TRUSTED_SECRET.");
            }
            return exchange;
        }

        String userId = jwt.getSubject();
        String email = jwt.getClaimAsString("email");

        ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate()
                .header(HEADER_USER_ID, userId != null ? userId : "")
                .header(HEADER_USER_EMAIL, email != null ? email : "")
                .header(HEADER_GATEWAY_SECRET, secret);

        // Omitted rather than sent empty: an absent header means "no roles" downstream.
        String roles = allowedRealmRoles(jwt);
        if (roles != null) {
            requestBuilder.header(HEADER_USER_ROLES, roles);
        }

        return exchange.mutate().request(requestBuilder.build()).build();
    }

    /**
     * Intersects Keycloak's {@code realm_access.roles} with the configured allowlist and
     * returns them comma-separated, or {@code null} when nothing survives the filter.
     * A claim of the wrong shape yields {@code null} too — a malformed token must degrade
     * to "no roles", never to "all roles".
     */
    private String allowedRealmRoles(Jwt jwt) {
        List<String> allowed = properties.allowedRoles();
        if (allowed == null || allowed.isEmpty()) {
            return null;
        }
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null) {
            return null;
        }
        Object rawRoles = realmAccess.get("roles");
        if (!(rawRoles instanceof Collection<?> values)) {
            return null;
        }
        List<String> matched = values.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(String::trim)
                .filter(allowed::contains)
                .distinct()
                .toList();
        return matched.isEmpty() ? null : String.join(",", matched);
    }

    @Override
    public int getOrder() {
        // Spring Security filters run at -100.
        // This filter runs after them at 0 to read the validated JWT principal.
        return 0;
    }
}
