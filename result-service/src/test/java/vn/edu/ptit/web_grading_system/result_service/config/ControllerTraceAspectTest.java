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
 * Unit test cho {@link ControllerTraceAspect} của result-service.
 */
@ExtendWith(MockitoExtension.class)
class ControllerTraceAspectTest {

    @Mock
    private Tracer tracer;

    @Mock
    private Span span;

    @Mock
    private Signature signature;

    @InjectMocks
    private ControllerTraceAspect aspect;

    @Test
    void traceController_shouldCreateSpanWithControllerNameAndProceed() throws Throwable {
        Object target = new Object() {};
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getTarget()).thenReturn(target);
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("getResult");
        when(pjp.proceed()).thenReturn("result");

        when(tracer.nextSpan()).thenReturn(span);
        when(span.name(anyString())).thenReturn(span);
        when(span.start()).thenReturn(span);

        Object result = aspect.traceController(pjp);

        assertEquals("result", result);
        verify(span).name("controller." + target.getClass().getSimpleName() + ".getResult");
        verify(span).start();
        verify(pjp).proceed();
        verify(span).end();
        verify(span, never()).error(any());
    }

    @Test
    void traceController_shouldRecordErrorWhenExceptionThrown() throws Throwable {
        Object target = new Object() {};
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getTarget()).thenReturn(target);
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("getResult");
        RuntimeException ex = new RuntimeException("boom");
        when(pjp.proceed()).thenThrow(ex);

        when(tracer.nextSpan()).thenReturn(span);
        when(span.name(anyString())).thenReturn(span);
        when(span.start()).thenReturn(span);

        assertThrows(RuntimeException.class, () -> aspect.traceController(pjp));

        verify(span).error(ex);
        verify(span).end();
    }
}
