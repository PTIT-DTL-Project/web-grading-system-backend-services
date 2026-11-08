package vn.edu.ptit.web_grading_system.api_gateway.config;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

/**
 * Trace tất cả public method trong các class @Service của api-gateway
 * (ví dụ PasswordChangeService, UserImportService) để thấy chi tiết trong trace.
 *
 * Review: 2026-11-08, method-level tracing cho api-gateway service layer.
 * Fix: span được stop trong finally, activated làm current span qua SpanInScope.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class ServiceTraceAspect {

    private final Tracer tracer;

    @Around("execution(public * vn.edu.ptit.web_grading_system.api_gateway..service..*.*(..))")
    public Object traceService(ProceedingJoinPoint pjp) throws Throwable {
        String className = pjp.getTarget().getClass().getSimpleName();
        String methodName = pjp.getSignature().getName();
        String spanName = "service." + className + "." + methodName;

        Span span = tracer.nextSpan().name(spanName).start();
        try (Tracer.SpanInScope scope = tracer.withSpan(span)) {
            return pjp.proceed();
        } catch (Throwable ex) {
            span.error(ex);
            throw ex;
        } finally {
            span.end();
        }
    }
}
