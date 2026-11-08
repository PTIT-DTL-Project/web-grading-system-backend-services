package vn.edu.ptit.web_grading_system.api_gateway.config;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.headers.observation.GatewayContext;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.RequestPath;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit test cho {@link GatewayObservationConfig} của api-gateway.
 * Verify rằng ObservationHandler đặt contextual name theo format:
 * "<METHOD> <path> → <target-uri>"
 */
@ExtendWith(MockitoExtension.class)
class GatewayObservationConfigTest {

    @Test
    void onStart_shouldSetContextualNameWithRoute() {
        // Given
        GatewayObservationConfig config = new GatewayObservationConfig();
        ObservationHandler<GatewayContext> handler = config.gatewaySpanNameHandler();

        ServerHttpRequest request = mock(ServerHttpRequest.class);
        RequestPath path = mock(RequestPath.class);
        when(path.value()).thenReturn("/api/v1/student/assignments/123/plans");
        when(request.getMethod()).thenReturn(org.springframework.http.HttpMethod.GET);
        when(request.getPath()).thenReturn(path);

        ServerWebExchange exchange = mock(ServerWebExchange.class);
        Route route = mock(Route.class);
        when(route.getUri()).thenReturn(URI.create("lb://course-service"));
        when(exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR)).thenReturn(route);

        GatewayContext context = new GatewayContext(new HttpHeaders(), request, exchange);

        // When
        handler.onStart(context);

        // Then
        assertEquals(
                "GET /api/v1/student/assignments/123/plans → lb://course-service",
                context.getContextualName());
    }

    @Test
    void supportsContext_shouldReturnTrueForGatewayContext() {
        GatewayObservationConfig config = new GatewayObservationConfig();
        ObservationHandler<GatewayContext> handler = config.gatewaySpanNameHandler();

        ServerHttpRequest request = mock(ServerHttpRequest.class);
        ServerWebExchange exchange = mock(ServerWebExchange.class);
        GatewayContext context = new GatewayContext(new HttpHeaders(), request, exchange);

        assertTrue(handler.supportsContext(context));
    }

    @Test
    void supportsContext_shouldReturnFalseForOtherContext() {
        GatewayObservationConfig config = new GatewayObservationConfig();
        ObservationHandler<GatewayContext> handler = config.gatewaySpanNameHandler();

        Observation.Context other = mock(Observation.Context.class);

        assertFalse(handler.supportsContext(other));
    }
}
