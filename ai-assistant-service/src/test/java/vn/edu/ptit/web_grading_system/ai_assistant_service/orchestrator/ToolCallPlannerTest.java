package vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.JacksonConfig;
import vn.edu.ptit.web_grading_system.ai_assistant_service.service.LlmClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ToolCallPlannerTest {
    @Test
    void acceptsOnlyAnOfferedReadToolAndObjectArguments() {
        LlmClient llm = mock(LlmClient.class);
        ToolCallback read = callback("listVisibleAssignments");
        when(llm.generateStrictCompletion(anyString(), anyString())).thenReturn(
                "{\"tool\":\"listVisibleAssignments\",\"arguments\":{\"search\":\"Quản lý Thư viện\"}}");

        var decision = new ToolCallPlanner(new JacksonConfig().objectMapper())
                .choose(llm, "tạo step", "Cần ID", List.of(read), List.of());

        assertTrue(decision.isPresent());
        assertEquals("listVisibleAssignments", decision.get().toolName());
        assertEquals("Quản lý Thư viện", decision.get().arguments().path("search").asText());
    }

    @Test
    void rejectsToolOutsideTheOfferedList() {
        LlmClient llm = mock(LlmClient.class);
        when(llm.generateStrictCompletion(anyString(), anyString())).thenReturn(
                "{\"tool\":\"deleteTestPlan\",\"arguments\":{}}");

        var decision = new ToolCallPlanner(new JacksonConfig().objectMapper())
                .choose(llm, "tạo step", "Cần ID", List.of(callback("listVisibleAssignments")), List.of());

        assertTrue(decision.isEmpty());
    }

    private ToolCallback callback(String name) {
        ToolCallback callback = mock(ToolCallback.class);
        when(callback.getToolDefinition()).thenReturn(org.springframework.ai.tool.definition.ToolDefinition.builder()
                .name(name).description(name)
                .inputSchema("{\"type\":\"object\",\"properties\":{\"search\":{\"type\":\"string\"}}")
                .build());
        return callback;
    }
}
