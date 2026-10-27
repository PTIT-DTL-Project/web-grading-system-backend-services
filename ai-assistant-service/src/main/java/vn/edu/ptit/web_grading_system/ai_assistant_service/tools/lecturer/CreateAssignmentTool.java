package vn.edu.ptit.web_grading_system.ai_assistant_service.tools.lecturer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.CourseServiceClient;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.AssignmentSummaryDto;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.ApiEnvelope;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.CreateAssignmentCommand;
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
        return "Tạo bài tập sau khi giảng viên xác nhận. Cần title, classId (UUID). gradingStrategy mặc định là STUDENT_DOCKER_COMPOSE nếu không ghi rõ.";
    }

    @Override
    public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
        return org.springframework.ai.tool.definition.ToolDefinition.builder()
                .name(getName())
                .description(getDescription())
                .inputSchema("""
                        {"type":"object","properties":{
                          "title":{"type":"string","description":"Tên bài tập"},
                          "classId":{"type":"string","format":"uuid","description":"ID lớp do giảng viên phụ trách"},
                          "gradingStrategy":{"type":"string","enum":["STUDENT_DOCKER_COMPOSE","LECTURER_DOCKER_COMPOSE"]},
                          "description":{"type":"string"},
                          "dockerComposeTemplate":{"type":"string","description":"Bắt buộc với LECTURER_DOCKER_COMPOSE"},
                          "dockerComposePort":{"type":"integer"},
                          "startupTimeoutMs":{"type":"integer"},
                          "executionTimeoutMs":{"type":"integer"},
                          "maxMemoryMb":{"type":"integer"},
                          "maxCpu":{"type":"number"}
                        },"required":["title","classId"],"additionalProperties":false}
                        """)
                .build();
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

        if (arguments == null || !arguments.hasNonNull("title") || arguments.get("title").asText().isBlank()) {
            return ToolResult.error("INVALID_ARGUMENTS", "Tiêu đề bài tập 'title' là bắt buộc", false);
        }

        if (!arguments.hasNonNull("classId") || arguments.get("classId").asText().isBlank()) {
            return ToolResult.error("INVALID_ARGUMENTS", "Mã lớp 'classId' là bắt buộc", false);
        }

        String title = arguments.get("title").asText().trim();
        UUID classId;
        try {
            classId = UUID.fromString(arguments.get("classId").asText().trim());
        } catch (IllegalArgumentException e) {
            return ToolResult.error("INVALID_UUID", "Mã lớp 'classId' không đúng định dạng UUID: " + e.getMessage(), false);
        }

        String strategy = arguments.hasNonNull("gradingStrategy")
                ? arguments.get("gradingStrategy").asText()
                : "STUDENT_DOCKER_COMPOSE";
        if (!strategy.equals("STUDENT_DOCKER_COMPOSE") && !strategy.equals("LECTURER_DOCKER_COMPOSE")) {
            return ToolResult.error("INVALID_ARGUMENTS", "Cần chọn gradingStrategy: STUDENT_DOCKER_COMPOSE hoặc LECTURER_DOCKER_COMPOSE", false);
        }
        String template = optionalText(arguments, "dockerComposeTemplate");
        if (strategy.equals("LECTURER_DOCKER_COMPOSE") && (template == null || template.isBlank())) {
            return ToolResult.error("INVALID_ARGUMENTS", "dockerComposeTemplate là bắt buộc với LECTURER_DOCKER_COMPOSE", false);
        }

        CreateAssignmentCommand command;
        try {
            command = new CreateAssignmentCommand(
                    title, classId, strategy, optionalText(arguments, "description"), template,
                    optionalInt(arguments, "dockerComposePort"),
                    optionalInt(arguments, "startupTimeoutMs"),
                    optionalInt(arguments, "executionTimeoutMs"),
                    optionalInt(arguments, "maxMemoryMb"),
                    optionalDouble(arguments, "maxCpu")
            );
        } catch (IllegalArgumentException e) {
            return ToolResult.error("INVALID_ARGUMENTS", e.getMessage(), false);
        }

        String idempotencyKey = arguments.has("idempotencyKey")
                ? arguments.get("idempotencyKey").asText()
                : "assign-" + classId + "-" + title.toLowerCase().replaceAll("\\s+", "-");

        try {
            Optional<Operation> existing = operationService.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent() && existing.get().getStatus() == OperationStatus.SUCCEEDED) {
                log.info("Idempotent hit for operation {}", idempotencyKey);
                AssignmentSummaryDto cached = objectMapper.readValue(existing.get().getResultRef(), AssignmentSummaryDto.class);
                return cached != null && cached.getId() != null
                        ? ToolResult.ok(cached)
                        : ToolResult.error("INVALID_CACHED_RESULT", "Kết quả tạo bài tập trước đó không có ID; cần kiểm tra lại trước khi báo thành công", false);
            }

            operationService.recordOperation(null, idempotencyKey, "CREATE_ASSIGNMENT", objectMapper.writeValueAsString(command));

            ApiEnvelope<AssignmentSummaryDto> response = courseServiceClient.createAssignment(
                    context.userId().toString(),
                    "ROLE_LECTURER",
                    command,
                    context.gatewaySecret()
            );
            AssignmentSummaryDto created = response != null ? response.getData() : null;
            if (created == null || created.getId() == null) {
                throw new IllegalStateException("Course service không trả về ID bài tập đã tạo");
            }

            operationService.markSuccess(idempotencyKey, objectMapper.writeValueAsString(created));
            return ToolResult.ok(created);
        } catch (FeignException.BadRequest e) {
            log.warn("Course service rejected assignment creation: {}", e.contentUTF8());
            operationService.markFailure(idempotencyKey, e.getMessage());
            return ToolResult.error("VALIDATION_ERROR", "Course service từ chối dữ liệu tạo bài tập: " + e.contentUTF8(), false);
        } catch (feign.RetryableException e) {
            log.warn("Assignment creation outcome is unknown for operation {}", idempotencyKey, e);
            return ToolResult.error("UNKNOWN_OUTCOME", "Mất kết nối khi tạo bài tập; cần đối soát trước khi thử lại", false);
        } catch (FeignException e) {
            if (e.status() >= 500) {
                log.warn("Assignment creation outcome is unknown for operation {}", idempotencyKey, e);
                return ToolResult.error("UNKNOWN_OUTCOME", "Course service lỗi sau khi nhận lệnh; chưa xác định đã tạo bài tập hay chưa", false);
            }
            operationService.markFailure(idempotencyKey, e.getMessage());
            return ToolResult.error("CREATE_ASSIGNMENT_ERROR", "Course service từ chối tạo bài tập: " + e.status(), false);
        } catch (Exception e) {
            log.error("Failed to create assignment for lecturer {}", context.userId(), e);
            operationService.markFailure(idempotencyKey, e.getMessage());
            return ToolResult.error("CREATE_ASSIGNMENT_ERROR", "Không thể tạo bài tập: " + e.getMessage(), false);
        }
    }

    private static String optionalText(JsonNode arguments, String name) {
        return arguments.hasNonNull(name) ? arguments.get(name).asText() : null;
    }

    private static Integer optionalInt(JsonNode arguments, String name) {
        if (!arguments.hasNonNull(name)) return null;
        JsonNode value = arguments.get(name);
        if (!value.canConvertToInt()) throw new IllegalArgumentException(name + " phải là số nguyên");
        return value.intValue();
    }

    private static Double optionalDouble(JsonNode arguments, String name) {
        if (!arguments.hasNonNull(name)) return null;
        JsonNode value = arguments.get(name);
        if (!value.isNumber()) throw new IllegalArgumentException(name + " phải là số");
        return value.doubleValue();
    }
}
