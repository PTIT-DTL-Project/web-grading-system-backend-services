package vn.edu.ptit.web_grading_system.ai_assistant_service.tools.lecturer;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.*;

import java.time.Duration;
import java.util.List;
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

        return ToolResult.ok(List.of(
                "HTTP_REQUEST",
                "DB_QUERY",
                "DB_SCHEMA_CHECK",
                "DB_MIGRATION"
        ));
    }
}
