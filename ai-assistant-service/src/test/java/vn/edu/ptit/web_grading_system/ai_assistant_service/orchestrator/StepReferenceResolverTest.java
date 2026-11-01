package vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.JacksonConfig;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StepReferenceResolverTest {
    @Test
    void resolvesExactAssignmentAndPlanAndNextStepOrderWithoutLlm() {
        UUID assignmentId = UUID.randomUUID();
        UUID otherAssignment = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        List<String> called = new ArrayList<>();
        var mapper = new JacksonConfig().objectMapper();
        ToolRegistry registry = new ToolRegistry(List.of(
                read("listVisibleAssignments", called, ToolResult.ok(Map.of("items", List.of(
                        Map.of("id", assignmentId, "title", "Quản lý Thư viện"),
                        Map.of("id", otherAssignment, "title", "Quản lý Thư viện – Ứng dụng Web"))))),
                read("listTestPlans", called, ToolResult.ok(List.of(
                        Map.of("id", planId, "name", "Kiểm tra API thêm sách")))),
                read("listTestSteps", called, ToolResult.ok(List.of(
                        Map.of("id", UUID.randomUUID(), "stepOrder", 2))))), mapper);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        var result = new StepReferenceResolver(registry, mapper).resolve(context,
                "tạo giúp tôi step kiểm tra của câu Kiểm tra API thêm sách của bài Quản lý Thư viện",
                (tool, response) -> {});

        assertTrue(result.success());
        assertEquals(List.of("listVisibleAssignments", "listTestPlans", "listTestSteps"), called);
        assertTrue(result.context().contains(assignmentId.toString()));
        assertTrue(result.context().contains(planId.toString()));
        assertTrue(result.context().contains("stepOrder=3"));
    }

    @Test
    void refusesAmbiguousExactAssignments() {
        var mapper = new JacksonConfig().objectMapper();
        ToolRegistry registry = new ToolRegistry(List.of(
                read("listVisibleAssignments", new ArrayList<>(), ToolResult.ok(Map.of("items", List.of(
                        Map.of("id", UUID.randomUUID(), "title", "Quản lý Thư viện"),
                        Map.of("id", UUID.randomUUID(), "title", "Quản lý Thư viện"))))),
                read("listTestPlans", new ArrayList<>(), ToolResult.ok(List.of())),
                read("listTestSteps", new ArrayList<>(), ToolResult.ok(List.of()))), mapper);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        var result = new StepReferenceResolver(registry, mapper).resolve(context,
                "tạo step của câu Kiểm tra API thêm sách của bài Quản lý Thư viện", (tool, response) -> {});

        assertFalse(result.success());
        assertTrue(result.reply().contains("nhiều bài tập"));
    }

    private AgentTool read(String name, List<String> called, ToolResult result) {
        return new AgentTool() {
            public String getName() { return name; }
            public String getDescription() { return name; }
            public ToolDefinition getDefinition() {
                return new ToolDefinition(name, name, "1", "lecturer", "read", "assignment",
                        EffectType.READ, Set.of("ROLE_LECTURER"), Duration.ofSeconds(5), 1000);
            }
            public ToolResult execute(JsonNode args, AgentContext context) {
                called.add(name);
                return result;
            }
        };
    }
}
