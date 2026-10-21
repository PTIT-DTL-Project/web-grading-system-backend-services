package vn.edu.ptit.web_grading_system.ai_assistant_service.tools.lecturer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.CourseServiceClient;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.AssignmentSummaryDto;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.operation.Operation;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.operation.OperationStatus;
import vn.edu.ptit.web_grading_system.ai_assistant_service.service.OperationService;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.*;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class CreateAssignmentTool implements AgentTool {

    private final CourseServiceClient courseServiceClient;
    private final OperationService operationService;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "createAssignment";
    }

    @Override
    public String getDescription() {
        return "Tạo một bài tập mới trong lớp học do giảng viên phụ trách với tên, mô tả và cấu hình bài tập.";
    }

    @Override
    public ToolDefinition getDefinition() {
        return new ToolDefinition(
                "createAssignment",
                getDescription(),
                "1.0",
                "lecturer",
                "write",
                "assignment",
                EffectType.DOMAIN_WRITE,
                Set.of("ROLE_LECTURER"),
                Duration.ofSeconds(10),
                10000
        );
    }

    @Override
    public ToolResult execute(JsonNode arguments, AgentContext context) {
        if (context.roles() == null || !context.roles().contains("ROLE_LECTURER")) {
            return ToolResult.error("FORBIDDEN", "Chỉ giảng viên mới có quyền sử dụng công cụ tạo bài tập", false);
        }

        if (arguments == null || !arguments.has("title") || arguments.get("title").asText().isBlank()) {
            return ToolResult.error("INVALID_ARGUMENTS", "Tiêu đề bài tập 'title' là bắt buộc", false);
        }

        if (!arguments.has("classId") || arguments.get("classId").asText().isBlank()) {
            return ToolResult.error("INVALID_ARGUMENTS", "Mã lớp 'classId' là bắt buộc", false);
        }

        String title = arguments.get("title").asText().trim();
        UUID classId;
        try {
            classId = UUID.fromString(arguments.get("classId").asText().trim());
        } catch (IllegalArgumentException e) {
            return ToolResult.error("INVALID_UUID", "Mã lớp 'classId' không đúng định dạng UUID: " + e.getMessage(), false);
        }

        String idempotencyKey = arguments.has("idempotencyKey")
                ? arguments.get("idempotencyKey").asText()
                : "assign-" + classId + "-" + title.toLowerCase().replaceAll("\\s+", "-");

        try {
            Optional<Operation> existing = operationService.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent() && existing.get().getStatus() == OperationStatus.SUCCEEDED) {
                log.info("Idempotent hit for operation {}", idempotencyKey);
                AssignmentSummaryDto cached = objectMapper.readValue(existing.get().getResultRef(), AssignmentSummaryDto.class);
                return ToolResult.ok(cached);
            }

            operationService.recordOperation(null, idempotencyKey, "CREATE_ASSIGNMENT", arguments.toString());

            AssignmentSummaryDto created = courseServiceClient.createAssignment(
                    context.userId().toString(),
                    "ROLE_LECTURER",
                    arguments,
                    context.gatewaySecret()
            );

            operationService.markSuccess(idempotencyKey, objectMapper.writeValueAsString(created));
            return ToolResult.ok(created);
        } catch (Exception e) {
            log.error("Failed to create assignment for lecturer {}", context.userId(), e);
            operationService.markFailure(idempotencyKey, e.getMessage());
            return ToolResult.error("CREATE_ASSIGNMENT_ERROR", "Không thể tạo bài tập: " + e.getMessage(), false);
        }
    }
}
