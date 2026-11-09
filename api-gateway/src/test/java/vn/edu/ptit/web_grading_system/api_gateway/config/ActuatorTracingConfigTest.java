package vn.edu.ptit.web_grading_system.api_gateway.config;

import io.micrometer.observation.ObservationPredicate;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.RequestPath;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test cho {@link ActuatorTracingConfig}.
 *
 * Actuator phải bị loại khỏi tracing ngay từ ObservationPredicate để không
 * sinh span; request nghiệp vụ vẫn phải được trace.
 */
class ActuatorTracingConfigTest {

    private final ObservationPredicate predicate =
            new ActuatorTracingConfig().excludeActuatorRequestsFromTracing();

    private static ServerRequestObservationContext context(String path) {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        RequestPath requestPath = mock(RequestPath.class);
        when(request.getPath()).thenReturn(requestPath);
        when(requestPath.value()).thenReturn(path);
        return new ServerRequestObservationContext(
                request, mock(ServerHttpResponse.class), Map.of());
    }

    @Test
    void actuatorRequestIsNotObserved() {
        assertFalse(predicate.test("http.server.requests", context("/actuator/health")));
        assertFalse(predicate.test("http.server.requests", context("/actuator/prometheus")));
    }

    @Test
    void businessRequestIsObserved() {
        assertTrue(predicate.test("http.server.requests", context("/api/v1/classes")));
    }

    @Test
    void otherContextsAreNotFiltered() {
        assertTrue(predicate.test("some.other.observation",
                new io.micrometer.observation.Observation.Context()));
    }
}
