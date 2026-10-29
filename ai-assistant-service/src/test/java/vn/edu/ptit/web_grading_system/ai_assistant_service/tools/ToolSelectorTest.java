package vn.edu.ptit.web_grading_system.ai_assistant_service.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.JacksonConfig;
import vn.edu.ptit.web_grading_system.ai_assistant_service.service.LlmClient;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
    void selectsModelProposalsWithoutFixedCapAndEnforcesRoles() {
        ToolRegistry registry = new ToolRegistry(List.of(
                tool("listMyClasses", "Liệt kê lớp học", "ROLE_STUDENT"),
                tool("getMyClass", "Xem chi tiết lớp học", "ROLE_STUDENT"),
                tool("getClassRoster", "Danh sách sinh viên trong lớp", "ROLE_STUDENT"),
                tool("listMySubmissions", "Liệt kê bài nộp", "ROLE_STUDENT")
        ), mapper);
        ToolSelector selector = new ToolSelector(registry);
        LlmClient llm = mock(LlmClient.class);
        when(llm.chooseRelevantTools(anyString(), anyList())).thenReturn(List.of(
                "getMyClass", "listMyClasses", "getClassRoster", "listMySubmissions", "getMyClass", "invented"));
        assertEquals(List.of("getMyClass", "listMyClasses", "getClassRoster", "listMySubmissions"),
                selector.selectFromModel("Xem lớp học của tôi", Set.of("ROLE_STUDENT"), llm));
    }

    @Test
    void noModelProposalsDoesNotDumpRegistry() {
        ToolRegistry registry = new ToolRegistry(List.of(tool("getMyClass", "Xem lớp", "ROLE_STUDENT")), mapper);
        LlmClient llm = mock(LlmClient.class);
        when(llm.chooseRelevantTools(anyString(), anyList())).thenReturn(List.of());
        assertTrue(new ToolSelector(registry).selectFromModel("", Set.of("ROLE_STUDENT"), llm).isEmpty());
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

}
