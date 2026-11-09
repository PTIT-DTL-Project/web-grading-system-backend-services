package vn.edu.ptit.web_grading_system.ai_assistant_service.config;

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
 * Unit test cho {@link RepositoryTraceAspect} của ai-assistant-service.
 */
@ExtendWith(MockitoExtension.class)
class RepositoryTraceAspectTest {

    @Mock
    private Tracer tracer;

    @Mock
    private Span span;

    @Mock
    private Signature signature;

    @InjectMocks
    private RepositoryTraceAspect aspect;

    @Test
    void traceRepository_shouldCreateSpanWithRepositoryNameAndProceed() throws Throwable {
        Object target = new Object() {};
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getTarget()).thenReturn(target);
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("findById");
        when(pjp.proceed()).thenReturn("result");

        when(tracer.nextSpan()).thenReturn(span);
        when(span.name(anyString())).thenReturn(span);
        when(span.start()).thenReturn(span);

        Object result = aspect.traceRepository(pjp);

        assertEquals("result", result);
        verify(span).name("repository." + target.getClass().getSimpleName() + ".findById");
        verify(span).start();
        verify(pjp).proceed();
        verify(span).end();
        verify(span, never()).error(any());
    }

    @Test
    void traceRepository_shouldRecordErrorWhenExceptionThrown() throws Throwable {
        Object target = new Object() {};
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getTarget()).thenReturn(target);
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("findById");
        RuntimeException ex = new RuntimeException("boom");
        when(pjp.proceed()).thenThrow(ex);

        when(tracer.nextSpan()).thenReturn(span);
        when(span.name(anyString())).thenReturn(span);
        when(span.start()).thenReturn(span);

        assertThrows(RuntimeException.class, () -> aspect.traceRepository(pjp));

        verify(span).error(ex);
        verify(span).end();
    }
}
