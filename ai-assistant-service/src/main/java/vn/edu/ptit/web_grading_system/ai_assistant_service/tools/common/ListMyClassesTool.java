package vn.edu.ptit.web_grading_system.ai_assistant_service.tools.common;

import com.fasterxml.jackson.databind.JsonNode;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.CourseServiceClient;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.ClassSummaryDto;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.PageDto;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.*;

import java.util.function.Supplier;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;

@Slf4j
@Component
public class ListMyClassesTool implements AgentTool {

    private final CourseServiceClient courseServiceClient;
    private Tracer tracer;

    public ListMyClassesTool(CourseServiceClient courseServiceClient, Tracer tracer) {
        this.courseServiceClient = courseServiceClient;
        this.tracer = tracer;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ListMyClassesTool(CourseServiceClient courseServiceClient) {
        this(courseServiceClient, null);
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setTracer(Tracer tracer) {
        this.tracer = tracer;
    }

    // Review: 2026-11-09, PR #93 follow-up — wrap internal Feign calls in explicit spans
    // so service-to-service calls are visible in Tempo (not blank gaps).
    private <T> T traceFeign(String spanName, Supplier<T> call) {
        if (tracer == null) {
            return call.get();
        }
        Span span = tracer.nextSpan().name(spanName).start();
        try (Tracer.SpanInScope scope = tracer.withSpan(span)) {
            return call.get();
        } catch (Throwable ex) {
            if (span != null) {
                span.error(ex);
            }
            throw ex;
        } finally {
            if (span != null) {
                span.end();
            }
        }
    }

    @Override
    public String getName() {
        return "listMyClasses";
    }

    @Override
    public String getDescription() {
        return "Lấy danh sách các lớp học mà người dùng hiện tại tham gia hoặc phụ trách giảng dạy.";
    }

    @Override
    public String referenceLookupKeywords() {
        return "lớp học class classId tên lớp";
    }

    @Override
    public Set<String> resolvedIdentifiers() { return Set.of("classId"); }

    @Override
    public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
        return org.springframework.ai.tool.definition.ToolDefinition.builder()
                .name(getName())
                .description(getDescription() + " Dùng khi người dùng nói tên lớp nhưng chưa biết ID; "
                        + "search là tên hoặc một phần tên lớp, để trống nếu cần liệt kê.")
                .inputSchema("{\"type\":\"object\",\"properties\":{\"search\":{\"type\":\"string\"},"
                        + "\"page\":{\"type\":\"integer\",\"minimum\":0,\"description\":\"Zero-based page index; first page is 0. Omit to start at page 0.\"},"
                        + "\"size\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":50}},"
                        + "\"additionalProperties\":false}")
                .build();
    }

    @Override
    public ToolDefinition getDefinition() {
        return new ToolDefinition(
                "listMyClasses",
                getDescription(),
                "1.0",
                "common",
                "read",
                "class",
                EffectType.READ,
                Set.of("ROLE_STUDENT", "ROLE_LECTURER"),
                Duration.ofSeconds(5),
                10000
        );
    }

    @Override
    public ToolResult execute(JsonNode arguments, AgentContext context) {
        try {
            int page = (arguments != null && arguments.has("page")) ? arguments.get("page").asInt(0) : 0;
            int size = (arguments != null && arguments.has("size")) ? arguments.get("size").asInt(20) : 20;
            final int safeSize = Math.max(1, Math.min(50, size));
            String rawSearch = arguments != null ? arguments.path("search").asText("").trim() : "";
            final String search = rawSearch.isBlank() ? null : "name:" + rawSearch;

            PageDto<ClassSummaryDto> result;
            if (context.roles() != null && context.roles().contains("ROLE_LECTURER")) {
                result = traceFeign("client.course-service.listLecturerClasses",
                        () -> courseServiceClient.listLecturerClasses(
                                context.userId().toString(),
                                "ROLE_LECTURER",
                                search,
                                page,
                                safeSize,
                                context.gatewaySecret()
                        ));
            } else {
                result = traceFeign("client.course-service.listStudentClasses",
                        () -> courseServiceClient.listStudentClasses(
                                context.userId().toString(),
                                context.email(),
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
            log.error("Failed to execute listMyClasses for user {}", context.userId(), e);
            return ToolResult.error("COURSE_SERVICE_ERROR", "Không thể lấy danh sách lớp học: " + e.getMessage(), true);
        }
    }
}
