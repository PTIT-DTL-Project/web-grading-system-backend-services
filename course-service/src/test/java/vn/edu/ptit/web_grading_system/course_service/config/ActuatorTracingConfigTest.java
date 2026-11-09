package vn.edu.ptit.web_grading_system.course_service.config;

import io.micrometer.observation.ObservationPredicate;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.observation.ServerRequestObservationContext;

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

    private static ServerRequestObservationContext context(String uri) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(uri);
        return new ServerRequestObservationContext(
                request, mock(HttpServletResponse.class));
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
