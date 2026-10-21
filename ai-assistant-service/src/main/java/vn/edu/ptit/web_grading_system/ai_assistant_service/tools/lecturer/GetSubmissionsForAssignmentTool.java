package vn.edu.ptit.web_grading_system.ai_assistant_service.tools.lecturer;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.CourseServiceClient;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.PageDto;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.SubmissionSummaryDto;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.*;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class GetSubmissionsForAssignmentTool implements AgentTool {

    private final CourseServiceClient courseServiceClient;

    @Override
    public String getName() {
        return "getSubmissionsForAssignment";
    }

    @Override
    public String getDescription() {
        return "Lấy danh sách các bài nộp của sinh viên cho một bài tập do giảng viên phụ trách chấm điểm.";
    }

    @Override
    public ToolDefinition getDefinition() {
        return new ToolDefinition(
                "getSubmissionsForAssignment",
                getDescription(),
                "1.0",
                "lecturer",
                "read",
                "submission",
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

        if (arguments == null || !arguments.has("assignmentId") || arguments.get("assignmentId").isNull()) {
            return ToolResult.error("INVALID_ARGUMENTS", "Tham số 'assignmentId' là bắt buộc", false);
        }

        try {
            UUID assignmentId = UUID.fromString(arguments.get("assignmentId").asText());
            int page = arguments.has("page") ? arguments.get("page").asInt(0) : 0;
            int size = arguments.has("size") ? arguments.get("size").asInt(50) : 50;

            PageDto<SubmissionSummaryDto> result = courseServiceClient.listLecturerAssignmentSubmissions(
                    assignmentId,
                    context.userId().toString(),
                    "ROLE_LECTURER",
                    page,
                    size,
                    context.gatewaySecret()
            );

            return ToolResult.ok(result != null ? result.getContent() : Collections.emptyList());
        } catch (IllegalArgumentException e) {
            return ToolResult.error("INVALID_UUID", "Mã bài tập 'assignmentId' không hợp lệ: " + e.getMessage(), false);
        } catch (Exception e) {
            log.error("Failed to get submissions for assignment for lecturer {}", context.userId(), e);
            return ToolResult.error("COURSE_SERVICE_ERROR", "Không thể lấy danh sách bài nộp: " + e.getMessage(), true);
        }
    }
}
