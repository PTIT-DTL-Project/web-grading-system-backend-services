package vn.edu.ptit.web_grading_system.ai_assistant_service.tools.lecturer;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.*;

import java.time.Duration;
import java.util.List;
import java.util.Set;

@Component
public class ListAllowedImagesTool implements AgentTool {

    @Override
    public String getName() {
        return "listAllowedImages";
    }

    @Override
    public String getDescription() {
        return "Trả về danh sách các Docker image hợp lệ được cho phép chạy trong môi trường executor của WGS.";
    }

    @Override
    public ToolDefinition getDefinition() {
        return new ToolDefinition(
                "listAllowedImages",
                getDescription(),
                "1.0",
                "lecturer",
                "read",
                "images",
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
                "openjdk:21-jdk-slim",
                "openjdk:17-jdk-slim",
                "python:3.11-slim",
                "python:3.10-slim",
                "node:20-alpine",
                "postgres:16-alpine",
                "mysql:8.0"
        ));
    }
}
