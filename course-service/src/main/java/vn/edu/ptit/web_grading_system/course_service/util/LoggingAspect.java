package vn.edu.ptit.web_grading_system.course_service.util;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;

/**
 * Traces every service/controller/advice method at INFO: entry with args,
 * exit with duration, ERROR on exceptions. Skips known-noise beans and
 * probe endpoints. Flip to debug() + a logging.level override when volume matters.
 */
@Aspect
@Component
public class LoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(LoggingAspect.class);
    private static final int MAX_ARG_LENGTH = 200;

    @Around("within(vn.edu.ptit.web_grading_system.course_service..*) && ("
            + "@within(org.springframework.stereotype.Service)"
            + " || @within(org.springframework.web.bind.annotation.RestController))")
    public Object trace(ProceedingJoinPoint pjp) throws Throwable {
        if (isNoise(pjp)) {
            return pjp.proceed();
        }
        String name = pjp.getSignature().toShortString();
        log.debug("-> {}({})", name, summarize(pjp.getArgs()));
        long start = System.nanoTime();
        try {
            Object result = pjp.proceed();
            log.debug("<- {} ({}ms)", name, (System.nanoTime() - start) / 1_000_000);
            return result;
        } catch (Throwable t) {
            log.error("!! {} ({}ms): {}", name, (System.nanoTime() - start) / 1_000_000, t.toString());
            throw t;
        }
    }

    private boolean isNoise(ProceedingJoinPoint pjp) {
        String cls = pjp.getSignature().getDeclaringType().getSimpleName();
        String method = pjp.getSignature().getName();
        return cls.startsWith("HttpLog") || cls.equals("OpenApiConfig")
                || method.equals("health") || method.equals("version");
    }

    private String summarize(Object[] args) {
        if (args == null || args.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Object a : args) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(describe(a));
        }
        return sb.toString();
    }

    private String describe(Object a) {
        if (a instanceof MultipartFile f) {
            return "file=" + f.getOriginalFilename() + "(" + f.getSize() + "B)";
        }
        if (a == null) {
            return "null";
        }
        String s = String.valueOf(a);
        return s.length() > MAX_ARG_LENGTH ? s.substring(0, MAX_ARG_LENGTH) + "..." : s;
    }
}
