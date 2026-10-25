package vn.edu.ptit.web_grading_system.ai_assistant_service.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.JacksonConfig;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ToolSelectorTest {
    private final ObjectMapper mapper = new JacksonConfig().objectMapper();

    private AgentTool tool(String name, String description, String role) {
        return new AgentTool() {
            public String getName() { return name; }
            public String getDescription() { return description; }
            public ToolDefinition getDefinition() {
                return new ToolDefinition(name, description, "1", "course", "read", "course",
                        EffectType.READ, Set.of(role), Duration.ofSeconds(5), 1024);
            }
            public ToolResult execute(JsonNode args, AgentContext context) { return ToolResult.ok(name); }
        };
    }

    @Test
    void selectsCappedRelevantToolsWithStableOrder() {
        ToolRegistry registry = new ToolRegistry(List.of(
                tool("listMyClasses", "Liệt kê lớp học", "ROLE_STUDENT"),
                tool("getMyClass", "Xem chi tiết lớp học", "ROLE_STUDENT"),
                tool("getClassRoster", "Danh sách sinh viên trong lớp", "ROLE_STUDENT"),
                tool("listMySubmissions", "Liệt kê bài nộp", "ROLE_STUDENT")
        ), mapper);
        ToolSelector selector = new ToolSelector(registry);
        assertEquals(List.of("getMyClass", "listMyClasses"),
                selector.selectInitial("Xem lớp học của tôi", Set.of("ROLE_STUDENT"), 2));
    }

    @Test
    void emptyQueryDoesNotDumpRegistry() {
        ToolRegistry registry = new ToolRegistry(List.of(tool("getMyClass", "Xem lớp", "ROLE_STUDENT")), mapper);
        assertTrue(new ToolSelector(registry).selectInitial("", Set.of("ROLE_STUDENT"), 4).isEmpty());
    }

    @Test
    void searchOnlyReturnsAuthorizedToolsAndCanExpandSelection() {
        ToolRegistry registry = new ToolRegistry(List.of(
                tool("getMyClass", "Xem lớp", "ROLE_STUDENT"),
                tool("listMySubmissions", "Liệt kê bài nộp", "ROLE_STUDENT"),
                tool("getClassTranscript", "Xem bảng điểm lớp", "ROLE_LECTURER")
        ), mapper);
        var found = registry.searchAuthorized("bài nộp", Set.of("ROLE_STUDENT"), 5);
        assertEquals(List.of("listMySubmissions"), found.stream().map(ToolDefinition::name).toList());
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_STUDENT"), UUID.randomUUID(), UUID.randomUUID(), "");
        assertEquals(List.of("listMySubmissions"), registry.callbacksForNames(
                Set.of("listMySubmissions", "getClassTranscript"), context, (tool, result) -> {})
                .stream().map(callback -> callback.getToolDefinition().name()).toList());
    }

    @Test
    void searchCallbackReturnsDirectSoNextRequestCanExposeDiscoveredTools() {
        ToolRegistry registry = new ToolRegistry(List.of(tool("listMySubmissions", "Liệt kê bài nộp", "ROLE_STUDENT")), mapper);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_STUDENT"), UUID.randomUUID(), UUID.randomUUID(), "");
        Set<String> selected = new java.util.HashSet<>();
        var callback = new SearchToolsTool(registry, mapper, 5).callback(context, selected);
        assertTrue(callback.getToolMetadata().returnDirect());
        assertTrue(callback.call("{\"query\":\"bài nộp\"}").contains("listMySubmissions"));
        assertTrue(selected.contains("listMySubmissions"));
    }

    @Test
    void englishQueryCanMatchCamelCaseToolName() {
        ToolRegistry registry = new ToolRegistry(List.of(tool("listMySubmissions", "Liệt kê bài nộp", "ROLE_STUDENT")), mapper);
        assertEquals(List.of("listMySubmissions"),
                new ToolSelector(registry).selectInitial("my submissions", Set.of("ROLE_STUDENT"), 4));
    }
}
