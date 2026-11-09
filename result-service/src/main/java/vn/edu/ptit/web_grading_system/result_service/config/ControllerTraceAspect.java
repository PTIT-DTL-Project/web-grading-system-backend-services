package vn.edu.ptit.web_grading_system.result_service.config;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * Trace tất cả public method trong các class @Controller để thấy chi tiết
 * HTTP layer → service → repository trong mỗi trace. Đặt tên span có ý nghĩa
 * (controller.ClassName.method) thay vì tên chung "http get".
 *
 * Review: 2026-11-09 — controller-level tracing cho result-service.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class ControllerTraceAspect {

    private final Tracer tracer;

    @Around("execution(public * vn.edu.ptit.web_grading_system.result_service.controller..*.*(..))")
    public Object traceController(ProceedingJoinPoint pjp) throws Throwable {
        String className = pjp.getTarget().getClass().getSimpleName();
        String methodName = pjp.getSignature().getName();
        String spanName = "controller." + className + "." + methodName;

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
