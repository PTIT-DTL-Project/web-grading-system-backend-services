package vn.edu.ptit.web_grading_system.ai_assistant_service.tools;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.JacksonConfig;
import vn.edu.ptit.web_grading_system.ai_assistant_service.service.LlmClient;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SearchToolsToolTest {
    @Test
    void semanticDiscoveryLoadsOnlyAuthorizedNamesAndReturnsBriefParameters() throws Exception {
        var mapper = new JacksonConfig().objectMapper();
        AgentTool visible = tool("listVisibleAssignments", "List assignments", "ROLE_LECTURER");
        AgentTool forbidden = tool("listPrivateGrades", "List private grades", "ROLE_STUDENT");
        ToolRegistry registry = new ToolRegistry(List.of(visible, forbidden), mapper);
        LlmClient llm = mock(LlmClient.class);
        when(llm.chooseRelevantTools(eq("find assignment four"), anyList()))
                .thenReturn(List.of("listPrivateGrades", "listVisibleAssignments"));
        Set<String> selected = new LinkedHashSet<>();
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        String result = new SearchToolsTool(registry, mapper, 5, llm).callback(context, selected)
                .call("{\"query\":\"find assignment four\"}");

        JsonNode data = mapper.readTree(result);
        assertEquals(Set.of("listVisibleAssignments"), selected);
        assertEquals(1, data.size());
        assertEquals("listVisibleAssignments", data.get(0).get("name").asText());
        assertTrue(result.contains("assignmentId"));
    }

    private AgentTool tool(String name, String description, String role) {
        return new AgentTool() {
            public String getName() { return name; }
            public String getDescription() { return description; }
            public ToolDefinition getDefinition() {
                return new ToolDefinition(name, description, "1", "assignment", "read", "assignment",
                        EffectType.READ, Set.of(role), Duration.ofSeconds(5), 1000);
            }
            public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
                return org.springframework.ai.tool.definition.ToolDefinition.builder().name(name)
                        .description(description).inputSchema("{\"type\":\"object\",\"properties\":{\"assignmentId\":{\"type\":\"string\"}},\"required\":[\"assignmentId\"]}").build();
            }
            public ToolResult execute(JsonNode arguments, AgentContext context) { return ToolResult.ok(List.of()); }
        };
    }
}
