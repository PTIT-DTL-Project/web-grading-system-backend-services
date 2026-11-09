package vn.edu.ptit.web_grading_system.api_gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.filter.headers.observation.GatewayContext;
import org.springframework.cloud.gateway.filter.headers.observation.GatewayObservationConvention;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.RequestPath;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.http.server.reactive.observation.ServerRequestObservationConvention;
import org.springframework.web.server.ServerWebExchange;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test cho {@link GatewayObservationConfig}.
 *
 * Span name phải có endpoint pattern (không chứa id) để cardinality thấp,
 * và span client phải cho biết route/service đích.
 */
class GatewayObservationConfigTest {

    private static final String COURSE_ROUTE = "course-service";

    private static RouteDefinition routeDefinition(String id, String uri, String pathPredicate) {
        RouteDefinition definition = new RouteDefinition();
        definition.setId(id);
        definition.setUri(URI.create(uri));
        definition.setPredicates(List.of(new PredicateDefinition(pathPredicate)));
        return definition;
    }

    private static GatewayProperties gatewayProperties() {
        GatewayProperties properties = new GatewayProperties();
        properties.setRoutes(List.of(
                routeDefinition(COURSE_ROUTE, "http://course-service:8081",
                        "Path=/api/v1/classes/**,/api/v1/assignments/**,/api/v1/student/**"),
                routeDefinition("submission-service", "http://submission-service:8082",
                        "Path=/api/v1/submissions/**")));
        return properties;
    }

    private static GatewayRoutePatterns routePatterns() {
        return new GatewayRoutePatterns(gatewayProperties());
    }

    private static Route route(String id) {
        Route route = mock(Route.class);
        when(route.getId()).thenReturn(id);
        return route;
    }

    private static ServerHttpRequest request(String path) {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        RequestPath requestPath = mock(RequestPath.class);
        when(request.getMethod()).thenReturn(HttpMethod.GET);
        when(request.getPath()).thenReturn(requestPath);
        when(requestPath.value()).thenReturn(path);
        return request;
    }

    @Test
    void routePatterns_resolveMatchingPatternWithoutPathVariables() {
        GatewayRoutePatterns patterns = routePatterns();

        assertEquals("/api/v1/student/**",
                patterns.resolve(COURSE_ROUTE, "/api/v1/student/assignments/123/plans"));
        assertEquals("/api/v1/classes/**",
                patterns.resolve(COURSE_ROUTE, "/api/v1/classes/5/assignments"));
        assertEquals("/api/v1/submissions/**",
                patterns.resolve("submission-service", "/api/v1/submissions/42"));
    }

    @Test
    void routePatterns_fallBackToRouteIdWhenNoPatternMatches() {
        assertEquals(COURSE_ROUTE, routePatterns().resolve(COURSE_ROUTE, "/other"));
        assertEquals("unknown-route", routePatterns().resolve("unknown-route", "/api/v1/classes"));
    }

    @Test
    void serverSpanName_usesResolvedRoutePattern() {
        GatewayObservationConfig config = new GatewayObservationConfig();
        ServerRequestObservationConvention convention =
                config.gatewayServerRequestObservationConvention(routePatterns());

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, route(COURSE_ROUTE));
        ServerRequestObservationContext context = new ServerRequestObservationContext(
                request("/api/v1/student/assignments/123/plans"),
                mock(ServerHttpResponse.class),
                attributes);

        assertEquals("GET /api/v1/student/**", convention.getContextualName(context));
    }

    @Test
    void serverSpanName_prefersHandlerPathPattern() {
        GatewayObservationConfig config = new GatewayObservationConfig();
        ServerRequestObservationConvention convention =
                config.gatewayServerRequestObservationConvention(routePatterns());

        ServerRequestObservationContext context = new ServerRequestObservationContext(
                request("/actuator/health"),
                mock(ServerHttpResponse.class),
                new HashMap<>());
        context.setPathPattern("/actuator/health");

        assertEquals("GET /actuator/health", convention.getContextualName(context));
    }

    @Test
    void clientSpanName_includesTargetRoute() {
        GatewayObservationConfig config = new GatewayObservationConfig();
        GatewayObservationConvention convention =
                config.gatewayClientObservationConvention(routePatterns());

        ServerWebExchange exchange = mock(ServerWebExchange.class);
        Route route = route(COURSE_ROUTE);
        when(exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR)).thenReturn(route);
        when(exchange.getAttributes()).thenReturn(Map.of(
                ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, route));

        GatewayContext context = new GatewayContext(
                new HttpHeaders(), request("/api/v1/student/assignments/123/plans"), exchange);

        assertEquals("GET /api/v1/student/** -> course-service",
                convention.getContextualName(context));
    }

    @Test
    void clientSpanName_fallsBackToMethodWhenRouteIsMissing() {
        GatewayObservationConfig config = new GatewayObservationConfig();
        GatewayObservationConvention convention =
                config.gatewayClientObservationConvention(routePatterns());

        ServerWebExchange exchange = mock(ServerWebExchange.class);
        when(exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR)).thenReturn(null);
        when(exchange.getAttributes()).thenReturn(Map.of());

        GatewayContext context = new GatewayContext(
                new HttpHeaders(), request("/api/v1/unknown"), exchange);

        assertEquals("GET", convention.getContextualName(context));
    }
}
