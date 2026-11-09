package vn.edu.ptit.web_grading_system.api_gateway.config;

import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.filter.headers.observation.DefaultGatewayObservationConvention;
import org.springframework.cloud.gateway.filter.headers.observation.GatewayContext;
import org.springframework.cloud.gateway.filter.headers.observation.GatewayObservationConvention;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.observation.DefaultServerRequestObservationConvention;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.http.server.reactive.observation.ServerRequestObservationConvention;

import java.util.Map;

/**
 * Tuỳ chỉnh tên span của api-gateway.
 *
 * Trước:
 *   span server (root):  http get
 *   span client (route): HTTP GET
 *
 * Sau:
 *   span server (root):  GET /api/v1/student/**
 *   span client (route): GET /api/v1/student/** -> course-service
 *
 * Vì sao phải thay convention thay vì ObservationHandler: khi observation
 * stop, SimpleObservation gọi lại convention.getContextualName() và ghi đè
 * contextual name đã set trong onStart. Đăng ký ObservationHandler chỉ đổi
 * được tên tạm thời, span vẫn mang tên mặc định.
 *
 * Review: 2026-11-09, api-gateway span name vẫn là "http get".
 */
@Configuration
public class GatewayObservationConfig {

    @Bean
    public GatewayRoutePatterns gatewayRoutePatterns(GatewayProperties properties) {
        return new GatewayRoutePatterns(properties);
    }

    /**
     * Span server (root) do HttpWebHandlerAdapter tạo, trước đây không có
     * path pattern nên Tempo chỉ thấy "http get". Convention này lấy route đã
     * resolve từ exchange attributes để đặt tên theo Path predicate pattern.
     */
    @Bean
    public ServerRequestObservationConvention gatewayServerRequestObservationConvention(
            GatewayRoutePatterns routePatterns) {
        return new DefaultServerRequestObservationConvention() {
            @Override
            public String getContextualName(ServerRequestObservationContext context) {
                ServerHttpRequest request = context.getCarrier();
                HttpMethod method = request != null ? request.getMethod() : null;
                if (method == null) {
                    return null;
                }

                String pattern = context.getPathPattern();
                if (pattern == null) {
                    Route route = route(context.getAttributes());
                    if (route != null && request.getPath() != null) {
                        pattern = routePatterns.resolve(route.getId(), request.getPath().value());
                    }
                }
                return pattern != null ? method.name() + " " + pattern : method.name();
            }
        };
    }

    /**
     * Span client do Spring Cloud Gateway tạo cho lời gọi sang service đích.
     * Mặc định của framework là "HTTP GET"; thêm pattern và route id (chính là
     * tên service trong cấu hình route) để biết request đi đâu.
     */
    @Bean
    public GatewayObservationConvention gatewayClientObservationConvention(
            GatewayRoutePatterns routePatterns) {
        return new DefaultGatewayObservationConvention() {
            @Override
            public String getContextualName(GatewayContext context) {
                ServerHttpRequest request = context.getRequest();
                HttpMethod method = request != null ? request.getMethod() : null;
                if (method == null || request.getPath() == null) {
                    return null;
                }

                var exchange = context.getServerWebExchange();
                Route route = exchange != null ? route(exchange.getAttributes()) : null;
                if (route == null) {
                    return method.name();
                }
                String pattern = routePatterns.resolve(route.getId(), request.getPath().value());
                return method.name() + " " + pattern + " -> " + route.getId();
            }
        };
    }

    private static Route route(Map<String, Object> attributes) {
        return attributes.get(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR) instanceof Route route
                ? route
                : null;
    }
}
