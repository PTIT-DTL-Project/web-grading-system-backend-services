package vn.edu.ptit.web_grading_system.ai_assistant_service.tools.student;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.SubmissionServiceClient;
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
public class GetMySubmissionTool implements AgentTool {

    private final SubmissionServiceClient submissionServiceClient;

    @Override
    public String getName() {
        return "getMySubmission";
    }

    @Override
    public String getDescription() {
        return "Lấy thông tin một bài nộp cụ thể hoặc danh sách các bài nộp gần nhất của sinh viên hiện tại.";
    }

    @Override
    public ToolDefinition getDefinition() {
        return new ToolDefinition(
                "getMySubmission",
                getDescription(),
                "1.0",
                "student",
                "read",
                "submission",
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

        try {
            if (arguments != null && arguments.has("submissionId") && !arguments.get("submissionId").isNull()) {
                String subIdStr = arguments.get("submissionId").asText();
                if (!subIdStr.isBlank()) {
                    UUID subId = UUID.fromString(subIdStr);
                    SubmissionSummaryDto submission = submissionServiceClient.getSubmission(
                            subId,
                            context.userId().toString(),
                            context.gatewaySecret()
                    );
                    if (submission == null) {
                        return ToolResult.error("NOT_FOUND", "Không tìm thấy bài nộp với id " + subId, false);
                    }
                    return ToolResult.ok(submission);
                }
            }

            int page = (arguments != null && arguments.has("page")) ? arguments.get("page").asInt(0) : 0;
            int size = (arguments != null && arguments.has("size")) ? arguments.get("size").asInt(10) : 10;

            PageDto<SubmissionSummaryDto> pageDto = submissionServiceClient.listMySubmissions(
                    context.userId().toString(),
                    page,
                    size,
                    context.gatewaySecret()
            );

            return ToolResult.ok(pageDto != null ? pageDto.getContent() : Collections.emptyList());
        } catch (IllegalArgumentException e) {
            return ToolResult.error("INVALID_UUID", "Mã bài nộp không hợp lệ: " + e.getMessage(), false);
        } catch (Exception e) {
            log.error("Failed to execute getMySubmission for student {}", context.userId(), e);
            return ToolResult.error("SUBMISSION_SERVICE_ERROR", "Không thể lấy bài nộp: " + e.getMessage(), true);
        }
    }
}
