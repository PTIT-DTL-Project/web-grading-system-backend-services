package vn.edu.ptit.web_grading_system.ai_assistant_service.tools.common;

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
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
public class ListVisibleAssignmentsTool implements AgentTool {
    private final CourseServiceClient courseServiceClient;
    private Tracer tracer;

    public ListVisibleAssignmentsTool(CourseServiceClient courseServiceClient, Tracer tracer) {
        this.courseServiceClient = courseServiceClient;
        this.tracer = tracer;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ListVisibleAssignmentsTool(CourseServiceClient courseServiceClient) {
        this.courseServiceClient = courseServiceClient;
        this.tracer = null;
    }

    

    


        
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setTracer(Tracer tracer) {
        this.tracer = tracer;
    }

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
        return "listVisibleAssignments";
    }

    @Override
    public String getDescription() {
        return "Lấy danh sách các bài tập (assignments) hiển thị cho người dùng, có thể lọc theo mã lớp (classId). "
                + "Kết quả sắp theo thời điểm tạo giảm dần: items[0] của page 0 là bài tạo gần nhất trong bộ lọc.";
    }

    @Override
    public String referenceLookupKeywords() {
        return "bài tập assignment assignmentId tên bài tập";
    }

    @Override
    public Set<String> resolvedIdentifiers() { return Set.of("assignmentId"); }

    @Override
    public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
        return org.springframework.ai.tool.definition.ToolDefinition.builder()
                .name(getName())
                .description(getDescription() + " Dùng khi người dùng nói tên bài tập nhưng chưa biết ID; "
                        + "search là tên hoặc một phần tên, để trống nếu cần liệt kê. "
                        + "Muốn tìm bài tạo gần nhất của lớp, truyền classId, page=0, size=1.")
                .inputSchema("{\"type\":\"object\",\"properties\":{\"classId\":{\"type\":\"string\",\"description\":\"Optional class UUID; omit or use an empty string when unknown\"},"
                        + "\"search\":{\"type\":\"string\"},"
                        + "\"page\":{\"type\":\"integer\",\"minimum\":0,\"description\":\"Zero-based page index; first page is 0. Omit to start at page 0.\"},"
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
            UUID parsedClassId = null;
            if (arguments != null && arguments.has("classId") && !arguments.get("classId").isNull()) {
                String classIdStr = arguments.get("classId").asText();
                if (!classIdStr.isBlank()) {
                    parsedClassId = UUID.fromString(classIdStr);
                }
            }
            final UUID classId = parsedClassId;

            int page = (arguments != null && arguments.has("page")) ? arguments.get("page").asInt(0) : 0;
            int size = (arguments != null && arguments.has("size")) ? arguments.get("size").asInt(20) : 20;
            final int safeSize = Math.max(1, Math.min(50, size));
            String rawSearch = arguments != null ? arguments.path("search").asText("").trim() : "";
            final String search = rawSearch.isBlank() ? null : rawSearch;

            PageDto<AssignmentSummaryDto> result;
            if (context.roles() != null && context.roles().contains("ROLE_LECTURER")) {
                result = traceFeign("client.course-service.listLecturerAssignments",
                        () -> courseServiceClient.listLecturerAssignments(
                                context.userId().toString(),
                                "ROLE_LECTURER",
                                classId,
                                search,
                                page,
                                safeSize,
                                context.gatewaySecret()
                        ));
            } else {
                result = traceFeign("client.course-service.listStudentAssignments",
                        () -> courseServiceClient.listStudentAssignments(
                                context.userId().toString(),
                                context.email(),
                                classId,
                                search,
                                page,
                                safeSize,
                                context.gatewaySecret()
                        ));
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
