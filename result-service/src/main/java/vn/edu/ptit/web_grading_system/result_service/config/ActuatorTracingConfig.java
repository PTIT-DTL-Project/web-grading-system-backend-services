package vn.edu.ptit.web_grading_system.result_service.config;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;

/**
 * Loại request actuator khỏi tracing.
 *
 * Vì sao không dùng management.tracing.web.exclude-patterns: Spring Boot 4.1
 * không có property này (không có trong spring-configuration-metadata), nên
 * khai báo cũ chỉ là no-op và /actuator/health vẫn sinh span. ObservationPredicate
 * chạy trước khi observation/span được tạo, nên chặn được từ gốc.
 *
 * Review: 2026-11-09, actuator vẫn xuất hiện trong Tempo.
 */
@Configuration
public class ActuatorTracingConfig {

    private static final String ACTUATOR_PREFIX = "/actuator";

    @Bean
    public ObservationPredicate excludeActuatorRequestsFromTracing() {
        return (name, context) -> {
            if (context instanceof ServerRequestObservationContext serverRequest) {
                var request = serverRequest.getCarrier();
                if (request == null) {
                    return true;
                }
                String uri = request.getRequestURI();
                return uri == null || !uri.startsWith(ACTUATOR_PREFIX);
            }
            return true;
        };
    }
}
