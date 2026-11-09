package vn.edu.ptit.web_grading_system.ai_assistant_service.tools.common;

import com.fasterxml.jackson.databind.JsonNode;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.CourseServiceClient;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.ApiEnvelope;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.AssignmentDetailDto;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.*;

import java.util.function.Supplier;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
public class GetAssignmentTool implements AgentTool {
    public GetAssignmentTool(CourseServiceClient courseServiceClient, Tracer tracer) {
        this.courseServiceClient = courseServiceClient;
        this.tracer = tracer;
    }

    public GetAssignmentTool(CourseServiceClient courseServiceClient) {
        this.courseServiceClient = courseServiceClient;
        this.tracer = null;
    }


    private final CourseServiceClient courseServiceClient;
    private final Tracer tracer;

    // Review: 2026-11-09, PR #93 follow-up — wrap internal Feign calls in explicit spans
    // so service-to-service calls are visible in Tempo (not blank gaps).
    private <T> T traceFeign(String spanName, Supplier<T> call) {
        if (tracer == null) { return call.get(); }
        Span span = tracer.nextSpan().name(spanName).start();
        try (Tracer.SpanInScope scope = tracer.withSpan(span)) {
            return call.get();
        } catch (Throwable ex) {
            if (span != null) { span.error(ex); }
            throw ex;
        } finally {
            if (span != null) { span.end(); }
        }
    }

    @Override
    public String getName() {
        return "getAssignment";
    }

    @Override
    public String getDescription() {
        return "Lấy thông tin chi tiết của một bài tập bao gồm tiêu đề, mô tả và cấu hình giới hạn thời gian / bộ nhớ.";
    }

    @Override
    public ToolDefinition getDefinition() {
        return new ToolDefinition(
                "getAssignment",
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
        if (arguments == null || !arguments.has("assignmentId") || arguments.get("assignmentId").isNull()) {
            return ToolResult.error("INVALID_ARGUMENTS", "Tham số 'assignmentId' là bắt buộc", false);
        }

        try {
            UUID assignmentId = UUID.fromString(arguments.get("assignmentId").asText());
            ApiEnvelope<AssignmentDetailDto> envelope;

            if (context.roles() != null && context.roles().contains("ROLE_LECTURER")) {
                envelope = traceFeign("client.course-service.getOwnedAssignment",
                        () -> courseServiceClient.getOwnedAssignment(
                                assignmentId,
                                context.userId().toString(),
                                "ROLE_LECTURER",
                                context.gatewaySecret()
                        ));
            } else {
                envelope = traceFeign("client.course-service.getAssignment",
                        () -> courseServiceClient.getAssignment(
                                assignmentId,
                                context.userId().toString(),
                                context.gatewaySecret()
                        ));
            }

            if (envelope == null || envelope.getData() == null) {
                return ToolResult.error("NOT_FOUND", "Không tìm thấy bài tập với id " + assignmentId, false);
            }

            return ToolResult.ok(envelope.getData());
        } catch (IllegalArgumentException e) {
            return ToolResult.error("INVALID_UUID", "Mã bài tập 'assignmentId' không hợp lệ: " + e.getMessage(), false);
        } catch (Exception e) {
            log.error("Failed to execute getAssignment for user {}", context.userId(), e);
            return ToolResult.error("COURSE_SERVICE_ERROR", "Không thể lấy thông tin bài tập: " + e.getMessage(), true);
        }
    }
}
