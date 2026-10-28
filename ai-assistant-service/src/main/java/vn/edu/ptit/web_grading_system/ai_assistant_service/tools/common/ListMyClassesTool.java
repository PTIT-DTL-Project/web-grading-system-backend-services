package vn.edu.ptit.web_grading_system.ai_assistant_service.tools.common;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.CourseServiceClient;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.ClassSummaryDto;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.PageDto;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.*;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class ListMyClassesTool implements AgentTool {

    private final CourseServiceClient courseServiceClient;

    @Override
    public String getName() {
        return "listMyClasses";
    }

    @Override
    public String getDescription() {
        return "Lấy danh sách các lớp học mà người dùng hiện tại tham gia hoặc phụ trách giảng dạy.";
    }

    @Override
    public String referenceLookupKeywords() {
        return "lớp học class classId tên lớp";
    }

    @Override
    public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
        return org.springframework.ai.tool.definition.ToolDefinition.builder()
                .name(getName())
                .description(getDescription() + " Dùng khi người dùng nói tên lớp nhưng chưa biết ID; "
                        + "search là tên hoặc một phần tên lớp, để trống nếu cần liệt kê.")
                .inputSchema("{\"type\":\"object\",\"properties\":{\"search\":{\"type\":\"string\"},"
                        + "\"page\":{\"type\":\"integer\",\"minimum\":0},"
                        + "\"size\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":50}},"
                        + "\"additionalProperties\":false}")
                .build();
    }

    @Override
    public ToolDefinition getDefinition() {
        return new ToolDefinition(
                "listMyClasses",
                getDescription(),
                "1.0",
                "common",
                "read",
                "class",
                EffectType.READ,
                Set.of("ROLE_STUDENT", "ROLE_LECTURER"),
                Duration.ofSeconds(5),
                10000
        );
    }

    @Override
    public ToolResult execute(JsonNode arguments, AgentContext context) {
        try {
            int page = (arguments != null && arguments.has("page")) ? arguments.get("page").asInt(0) : 0;
            int size = (arguments != null && arguments.has("size")) ? arguments.get("size").asInt(20) : 20;
            size = Math.max(1, Math.min(50, size));
            String search = arguments != null ? arguments.path("search").asText("").trim() : "";
            if (search.isBlank()) search = null;
            else search = "name:" + search;

            PageDto<ClassSummaryDto> result;
            if (context.roles() != null && context.roles().contains("ROLE_LECTURER")) {
                result = courseServiceClient.listLecturerClasses(
                        context.userId().toString(),
                        "ROLE_LECTURER",
                        search,
                        page,
                        size,
                        context.gatewaySecret()
                );
            } else {
                result = courseServiceClient.listStudentClasses(
                        context.userId().toString(),
                        context.email(),
                        search,
                        page,
                        size,
                        context.gatewaySecret()
                );
            }
            return ToolResult.ok(java.util.Map.of(
                    "items", result != null ? result.getContent() : Collections.emptyList(),
                    "page", result != null ? result.getNumber() : page,
                    "totalPages", result != null ? result.getTotalPages() : 0,
                    "totalElements", result != null ? result.getTotalElements() : 0L));
        } catch (Exception e) {
            log.error("Failed to execute listMyClasses for user {}", context.userId(), e);
            return ToolResult.error("COURSE_SERVICE_ERROR", "Không thể lấy danh sách lớp học: " + e.getMessage(), true);
        }
    }
}
