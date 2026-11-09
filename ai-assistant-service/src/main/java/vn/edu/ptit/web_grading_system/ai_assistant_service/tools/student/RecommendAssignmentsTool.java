package vn.edu.ptit.web_grading_system.ai_assistant_service.tools.student;

import com.fasterxml.jackson.databind.JsonNode;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.CourseServiceClient;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.AssignmentSummaryDto;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.PageDto;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.*;

import java.util.function.Supplier;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendAssignmentsTool implements AgentTool {

    private final CourseServiceClient courseServiceClient;
    private final Tracer tracer;

    // Review: 2026-11-09, PR #93 follow-up — wrap internal Feign calls in explicit spans
    // so service-to-service calls are visible in Tempo (not blank gaps).
    private <T> T traceFeign(String spanName, Supplier<T> call) {
        Span span = tracer.nextSpan().name(spanName).start();
        try (Tracer.SpanInScope scope = tracer.withSpan(span)) {
            return call.get();
        } catch (Throwable ex) {
            span.error(ex);
            throw ex;
        } finally {
            span.end();
        }
    }

    @Override
    public String getName() {
        return "recommendAssignments";
    }

    @Override
    public String getDescription() {
        return "Gợi ý các bài tập sinh viên nên làm tiếp theo dựa trên hạn nộp (deadline) và tiến độ hiện tại.";
    }

    @Override
    public ToolDefinition getDefinition() {
        return new ToolDefinition(
                "recommendAssignments",
                getDescription(),
                "1.0",
                "student",
                "read",
                "assignment",
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
            PageDto<AssignmentSummaryDto> page = traceFeign("client.course-service.listStudentAssignments",
                    () -> courseServiceClient.listStudentAssignments(
                            context.userId().toString(),
                            context.email(),
                            null,
                            null,
                            0,
                            20,
                            context.gatewaySecret()
                    ));

            List<AssignmentSummaryDto> list = (page != null && page.getContent() != null)
                    ? new ArrayList<>(page.getContent())
                    : Collections.emptyList();

            Instant now = Instant.now();
            List<AssignmentSummaryDto> sorted = list.stream()
                    .filter(a -> a.getDeadline() == null || a.getDeadline().isAfter(now))
                    .sorted(Comparator.comparing(a -> a.getDeadline() != null ? a.getDeadline() : Instant.MAX))
                    .limit(5)
                    .collect(Collectors.toList());

            return ToolResult.ok(sorted);
        } catch (Exception e) {
            log.error("Failed to recommend assignments for student {}", context.userId(), e);
            return ToolResult.error("RECOMMEND_ERROR", "Không thể gợi ý bài tập: " + e.getMessage(), true);
        }
    }
}
