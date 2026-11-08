package vn.edu.ptit.web_grading_system.submission_service.config;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * Trace tất cả method trong package {@code repository} để thấy chi tiết SQL/query
 * trong mỗi trace. Áp dụng qua AOP, không cần sửa từng repository interface.
 *
 * Review: 2026-11-08, method-level tracing cho submission-service.
 * Fix: span được stop trong finally, activated làm current span qua SpanInScope.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RepositoryTraceAspect {

    private final Tracer tracer;

    @Around("execution(* vn.edu.ptit.web_grading_system.submission_service..repository..*.*(..))")
    public Object traceRepository(ProceedingJoinPoint pjp) throws Throwable {
        String className = pjp.getTarget().getClass().getSimpleName();
        String methodName = pjp.getSignature().getName();
        String spanName = "repository." + className + "." + methodName;

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
