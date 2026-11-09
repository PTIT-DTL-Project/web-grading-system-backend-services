package vn.edu.ptit.web_grading_system.api_gateway.config;

import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ánh xạ route id + request path sang Path predicate pattern đã khớp.
 *
 * Mục đích: span name của api-gateway phải có cardinality thấp. Dùng thẳng
 * request path sẽ sinh vô số tên span vì path chứa id ({assignmentId},
 * submissionId...), làm nổ số series của Tempo span metrics. Pattern khai báo
 * trong {@code spring.cloud.gateway.server.webflux.routes[].predicates} chỉ có
 * vài giá trị cố định nên dùng làm tên span an toàn.
 */
public final class GatewayRoutePatterns {

    private static final String PATH_PREDICATE = "Path";

    private final Map<String, List<PathPattern>> patternsByRouteId;

    public GatewayRoutePatterns(GatewayProperties properties) {
        Map<String, List<PathPattern>> patterns = new HashMap<>();
        if (properties.getRoutes() != null) {
            for (RouteDefinition definition : properties.getRoutes()) {
                List<PathPattern> routePatterns = new ArrayList<>();
                if (definition.getPredicates() != null) {
                    definition.getPredicates().stream()
                            .filter(predicate -> PATH_PREDICATE.equals(predicate.getName()))
                            .forEach(predicate -> {
                                if (predicate.getArgs() != null) {
                                    predicate.getArgs().values().stream()
                                            .map(PathPatternParser.defaultInstance::parse)
                                            .forEach(routePatterns::add);
                                }
                            });
                }
                patterns.put(definition.getId(), List.copyOf(routePatterns));
            }
        }
        this.patternsByRouteId = Map.copyOf(patterns);
    }

    /**
     * @return pattern khớp với {@code path}, hoặc route id nếu route không có
     *         Path predicate hay không pattern nào khớp.
     */
    public String resolve(String routeId, String path) {
        PathContainer pathContainer = PathContainer.parsePath(path);
        return patternsByRouteId.getOrDefault(routeId, List.of()).stream()
                .filter(pattern -> pattern.matches(pathContainer))
                .map(PathPattern::getPatternString)
                .findFirst()
                .orElse(routeId);
    }
}
