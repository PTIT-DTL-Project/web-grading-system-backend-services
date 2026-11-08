package vn.edu.ptit.web_grading_system.result_service.config;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit test cho {@link ServiceTraceAspect} của result-service.
 */
@ExtendWith(MockitoExtension.class)
class ServiceTraceAspectTest {

    @Mock
    private Tracer tracer;

    @Mock
    private Span span;

    @Mock
    private Signature signature;

    @InjectMocks
    private ServiceTraceAspect aspect;

    @Test
    void traceService_shouldCreateSpanWithServiceNameAndProceed() throws Throwable {
        Object target = new Object() {};
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getTarget()).thenReturn(target);
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("calculate");
        when(pjp.proceed()).thenReturn("result");

        when(tracer.nextSpan()).thenReturn(span);
        when(span.name(anyString())).thenReturn(span);
        when(span.start()).thenReturn(span);

        Object result = aspect.traceService(pjp);

        assertEquals("result", result);
        verify(span).name("service." + target.getClass().getSimpleName() + ".calculate");
        verify(span).start();
        verify(pjp).proceed();
        verify(span, never()).error(any());
    }

    @Test
    void traceService_shouldRecordErrorWhenExceptionThrown() throws Throwable {
        Object target = new Object() {};
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getTarget()).thenReturn(target);
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("calculate");
        RuntimeException ex = new RuntimeException("boom");
        when(pjp.proceed()).thenThrow(ex);

        when(tracer.nextSpan()).thenReturn(span);
        when(span.name(anyString())).thenReturn(span);
        when(span.start()).thenReturn(span);

        assertThrows(RuntimeException.class, () -> aspect.traceService(pjp));

        verify(span).error(ex);
    }
}
