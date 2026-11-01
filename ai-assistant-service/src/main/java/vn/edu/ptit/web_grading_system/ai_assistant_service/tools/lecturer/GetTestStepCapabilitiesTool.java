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
                        "optional", java.util.List.of("headers", "query_params", "expected_status", "assertions", "extract", "auto_inject_headers", "data"),
                        "assertionKinds", java.util.List.of("status", "contains", "json_path", "body_equals", "body_structure", "field_equals", "array_length", "contains_item", "json_path_equals", "greater_than", "less_than", "matches_regex", "any_of"),
                        "extractFields", java.util.List.of("name", "from", "expression", "as", "fallback"),
                        "dataLimit", 10),
                "DB_QUERY", Map.of("required", java.util.List.of("query"), "optional", java.util.List.of("connection", "expected"),
                        "expectedFields", java.util.List.of("row_count")),
                "DB_SCHEMA_CHECK", Map.of("required", java.util.List.of("checks"),
                        "checkKinds", java.util.List.of("TABLE_EXISTS", "COLUMN_EXISTS", "INDEX_EXISTS", "PRIMARY_KEY"),
                        "tableExistsFields", java.util.List.of("table_name"),
                        "columnExistsFields", java.util.List.of("table_name", "column_name"),
                        "indexExistsFields", java.util.List.of("index_name"),
                        "primaryKeyFields", java.util.List.of("column")),
                "DB_MIGRATION", Map.of("required", java.util.List.of("statements"),
                        "optional", java.util.List.of("connection")),
                "connection", Map.of("db_type", java.util.List.of("postgres", "mysql", "mariadb"),
                        "db_service", "db",
                        "db_port", "1–65535",
                        "database", "[A-Za-z0-9_$]+"),
                "requiredRule", "Bước setup đặt required: true; bước âm tính (400/404/409) đặt required: false",
                "autoInjectHeadersRule", "Chỉ có tác dụng trong cùng plan; bước login phải đứng đầu mỗi plan cần auth"
        ));
    }
}
