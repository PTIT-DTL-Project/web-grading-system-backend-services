package vn.edu.ptit.web_grading_system.executor_service.config;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * Trace tất cả public method trong các class @Service để thấy chi tiết
 * class → service → repository trong mỗi trace. Áp dụng qua AOP.
 *
 * Review: 2026-11-08, method-level tracing cho executor-service.
 * Fix: span được stop trong finally, activated làm current span qua SpanInScope.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class ServiceTraceAspect {

    private final Tracer tracer;

    @Around("execution(public * vn.edu.ptit.web_grading_system.executor_service..service..*.*(..))")
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
