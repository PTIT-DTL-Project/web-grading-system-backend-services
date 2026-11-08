package vn.edu.ptit.web_grading_system.api_gateway.config;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import org.springframework.cloud.gateway.filter.headers.observation.GatewayContext;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tuỳ chỉnh tên span của Spring Cloud Gateway để hiển thị target service.
 *
 * Trước:
 *   api-gateway: http get (generic)
 *
 * Sau:
 *   GET /api/v1/student/assignments/... → course-service
 *
 * Dùng ObservationHandler với GatewayContext (observation context của
 * Spring Cloud Gateway) để đặt contextual name bao gồm method, path và
 * URI của route mục tiêu.
 *
 * Review: 2026-11-08, trace service name through gateway.
 */
@Configuration
public class GatewayObservationConfig {

    @Bean
    public ObservationHandler<GatewayContext> gatewaySpanNameHandler() {
        return new ObservationHandler<>() {
            @Override
            public void onStart(GatewayContext context) {
                var exchange = context.getServerWebExchange();
                var route = exchange.getAttribute(
                        ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
                if (route instanceof Route r) {
                    var request = context.getRequest();
                    String method = request.getMethod().name();
                    String path = request.getPath().value();
                    String target = r.getUri().toString();

                    // Ngắn hóa path nếu quá dài để không làm rách bảng trace
                    String shortPath = path.length() > 60
                            ? path.substring(0, 57) + "..."
                            : path;

                    context.setContextualName(
                            method + " " + shortPath + " → " + target);
                }
            }

            @Override
            public boolean supportsContext(Observation.Context context) {
                return context instanceof GatewayContext;
            }
        };
    }
}
