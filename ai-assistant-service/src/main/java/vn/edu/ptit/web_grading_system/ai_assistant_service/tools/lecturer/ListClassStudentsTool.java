package vn.edu.ptit.web_grading_system.ai_assistant_service.tools.lecturer;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.CourseServiceClient;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.PageDto;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.*;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ListClassStudentsTool implements AgentTool {

    private final CourseServiceClient courseServiceClient;

    @Override
    public String getName() {
        return "listClassStudents";
    }

    @Override
    public String getDescription() {
        return "Lấy danh sách sinh viên trong một lớp học do giảng viên phụ trách.";
    }

    @Override
    public ToolDefinition getDefinition() {
        return new ToolDefinition(
                "listClassStudents",
                getDescription(),
                "1.0",
                "lecturer",
                "read",
                "class_student",
                EffectType.READ,
                Set.of("ROLE_LECTURER"),
                Duration.ofSeconds(5),
                10000
        );
    }

    @Override
    public ToolResult execute(JsonNode arguments, AgentContext context) {
        if (context.roles() == null || !context.roles().contains("ROLE_LECTURER")) {
            return ToolResult.error("FORBIDDEN", "Chỉ giảng viên mới có quyền sử dụng công cụ này", false);
        }

        if (arguments == null || !arguments.has("classId") || arguments.get("classId").isNull()) {
            return ToolResult.error("INVALID_ARGUMENTS", "Tham số 'classId' là bắt buộc", false);
        }

        try {
            UUID classId = UUID.fromString(arguments.get("classId").asText());
            int page = arguments.has("page") ? arguments.get("page").asInt(0) : 0;
            int size = arguments.has("size") ? arguments.get("size").asInt(50) : 50;

            PageDto<Object> result = courseServiceClient.listClassStudents(
                    classId,
                    context.userId().toString(),
                    "ROLE_LECTURER",
                    page,
                    size,
                    context.gatewaySecret()
            );

            return ToolResult.ok(result != null ? result.getContent() : Collections.emptyList());
        } catch (IllegalArgumentException e) {
            return ToolResult.error("INVALID_UUID", "Mã lớp 'classId' không hợp lệ: " + e.getMessage(), false);
        } catch (Exception e) {
            log.error("Failed to list students in class for lecturer {}", context.userId(), e);
            return ToolResult.error("COURSE_SERVICE_ERROR", "Không thể lấy danh sách sinh viên: " + e.getMessage(), true);
        }
    }
}
