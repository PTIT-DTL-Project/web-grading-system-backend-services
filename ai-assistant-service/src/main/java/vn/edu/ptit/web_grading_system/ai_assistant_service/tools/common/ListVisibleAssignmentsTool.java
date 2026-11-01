package vn.edu.ptit.web_grading_system.ai_assistant_service.tools.common;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.CourseServiceClient;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.AssignmentSummaryDto;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.PageDto;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.*;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ListVisibleAssignmentsTool implements AgentTool {

    private final CourseServiceClient courseServiceClient;

    @Override
    public String getName() {
        return "listVisibleAssignments";
    }

    @Override
    public String getDescription() {
        return "Lấy danh sách các bài tập (assignments) hiển thị cho người dùng, có thể lọc theo mã lớp (classId).";
    }

    @Override
    public String referenceLookupKeywords() {
        return "bài tập assignment assignmentId tên bài tập";
    }

    @Override
    public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
        return org.springframework.ai.tool.definition.ToolDefinition.builder()
                .name(getName())
                .description(getDescription() + " Dùng khi người dùng nói tên bài tập nhưng chưa biết ID; "
                        + "search là tên hoặc một phần tên, để trống nếu cần liệt kê.")
                .inputSchema("{\"type\":\"object\",\"properties\":{\"classId\":{\"type\":\"string\",\"format\":\"uuid\"},"
                        + "\"search\":{\"type\":\"string\"},"
                        + "\"page\":{\"type\":\"integer\",\"minimum\":0},"
                        + "\"size\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":50}},"
                        + "\"additionalProperties\":false}")
                .build();
    }

    @Override
    public ToolDefinition getDefinition() {
        return new ToolDefinition(
                "listVisibleAssignments",
                getDescription(),
                "1.0",
                "common",
                "read",
                "assignment",
                EffectType.READ,
                Set.of("ROLE_STUDENT", "ROLE_LECTURER"),
                Duration.ofSeconds(5),
                10000
        );
    }

    @Override
    public ToolResult execute(JsonNode arguments, AgentContext context) {
        try {
            UUID classId = null;
            if (arguments != null && arguments.has("classId") && !arguments.get("classId").isNull()) {
                String classIdStr = arguments.get("classId").asText();
                if (!classIdStr.isBlank()) {
                    classId = UUID.fromString(classIdStr);
                }
            }

            int page = (arguments != null && arguments.has("page")) ? arguments.get("page").asInt(0) : 0;
            int size = (arguments != null && arguments.has("size")) ? arguments.get("size").asInt(20) : 20;
            size = Math.max(1, Math.min(50, size));
            String search = arguments != null ? arguments.path("search").asText("").trim() : "";
            if (search.isBlank()) search = null;

            PageDto<AssignmentSummaryDto> result;
            if (context.roles() != null && context.roles().contains("ROLE_LECTURER")) {
                result = courseServiceClient.listLecturerAssignments(
                        context.userId().toString(),
                        "ROLE_LECTURER",
                        classId,
                        search,
                        page,
                        size,
                        context.gatewaySecret()
                );
            } else {
                result = courseServiceClient.listStudentAssignments(
                        context.userId().toString(),
                        context.email(),
                        classId,
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
            log.error("Failed to execute listVisibleAssignments for user {}", context.userId(), e);
            return ToolResult.error("COURSE_SERVICE_ERROR", "Không thể lấy danh sách bài tập: " + e.getMessage(), true);
        }
    }
}
