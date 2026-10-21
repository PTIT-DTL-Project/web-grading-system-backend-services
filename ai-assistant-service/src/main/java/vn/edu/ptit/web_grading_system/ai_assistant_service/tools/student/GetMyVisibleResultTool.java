package vn.edu.ptit.web_grading_system.ai_assistant_service.tools.student;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.ResultServiceClient;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.ApiEnvelope;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.ResultDto;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.*;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class GetMyVisibleResultTool implements AgentTool {

    private final ResultServiceClient resultServiceClient;

    @Override
    public String getName() {
        return "getMyVisibleResult";
    }

    @Override
    public String getDescription() {
        return "Lấy kết quả chấm bài kiểm thử công khai (visible test results) của một bài nộp thuộc về sinh viên hiện tại.";
    }

    @Override
    public ToolDefinition getDefinition() {
        return new ToolDefinition(
                "getMyVisibleResult",
                getDescription(),
                "1.0",
                "student",
                "read",
                "result",
                EffectType.READ,
                Set.of("ROLE_STUDENT"),
                Duration.ofSeconds(5),
                10000
        );
    }

    @Override
    public ToolResult execute(JsonNode arguments, AgentContext context) {
        if (context.roles() == null || !context.roles().contains("ROLE_STUDENT")) {
            return ToolResult.error("FORBIDDEN", "Chỉ sinh viên mới có quyền sử dụng công cụ này", false);
        }

        if (arguments == null || !arguments.has("submissionId") || arguments.get("submissionId").isNull()) {
            return ToolResult.error("INVALID_ARGUMENTS", "Tham số 'submissionId' là bắt buộc", false);
        }

        try {
            UUID submissionId = UUID.fromString(arguments.get("submissionId").asText());
            ApiEnvelope<List<ResultDto>> envelope = resultServiceClient.getBySubmissionId(
                    submissionId,
                    context.userId().toString(),
                    context.gatewaySecret()
            );

            List<ResultDto> results = (envelope != null && envelope.getData() != null)
                    ? envelope.getData()
                    : Collections.emptyList();

            return ToolResult.ok(results);
        } catch (IllegalArgumentException e) {
            return ToolResult.error("INVALID_UUID", "Mã bài nộp không hợp lệ: " + e.getMessage(), false);
        } catch (Exception e) {
            log.error("Failed to execute getMyVisibleResult for student {}", context.userId(), e);
            return ToolResult.error("RESULT_SERVICE_ERROR", "Không thể lấy kết quả chấm: " + e.getMessage(), true);
        }
    }
}
