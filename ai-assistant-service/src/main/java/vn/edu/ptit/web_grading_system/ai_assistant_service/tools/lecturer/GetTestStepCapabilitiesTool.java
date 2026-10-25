package vn.edu.ptit.web_grading_system.ai_assistant_service.tools.lecturer;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.*;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

@Component
public class GetTestStepCapabilitiesTool implements AgentTool {

    @Override
    public String getName() {
        return "getTestStepCapabilities";
    }

    @Override
    public String getDescription() {
        return "Trả về danh sách 4 loại test step được hệ thống WGS hỗ trợ (HTTP_REQUEST, DB_QUERY, DB_SCHEMA_CHECK, DB_MIGRATION) và định dạng schema của chúng.";
    }

    @Override
    public ToolDefinition getDefinition() {
        return new ToolDefinition(
                "getTestStepCapabilities",
                getDescription(),
                "1.0",
                "lecturer",
                "read",
                "capabilities",
                EffectType.READ,
                Set.of("ROLE_LECTURER"),
                Duration.ofSeconds(3),
                5000
        );
    }

    @Override
    public ToolResult execute(JsonNode arguments, AgentContext context) {
        if (context.roles() == null || !context.roles().contains("ROLE_LECTURER")) {
            return ToolResult.error("FORBIDDEN", "Chỉ giảng viên mới có quyền sử dụng công cụ này", false);
        }

        return ToolResult.ok(Map.of(
                "HTTP_REQUEST", Map.of("required", java.util.List.of("method", "path"),
                        "methods", java.util.List.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"),
                        "optional", java.util.List.of("headers", "query_params", "expected_status", "assertions", "extract")),
                "DB_QUERY", Map.of("required", java.util.List.of("query"), "optional", java.util.List.of("connection", "expected")),
                "DB_SCHEMA_CHECK", Map.of("required", java.util.List.of("checks"),
                        "checkKinds", java.util.List.of("TABLE_EXISTS", "COLUMN_EXISTS", "INDEX_EXISTS", "PRIMARY_KEY")),
                "DB_MIGRATION", Map.of("required", java.util.List.of("statements"),
                        "optional", java.util.List.of("connection"))
        ));
    }
}
