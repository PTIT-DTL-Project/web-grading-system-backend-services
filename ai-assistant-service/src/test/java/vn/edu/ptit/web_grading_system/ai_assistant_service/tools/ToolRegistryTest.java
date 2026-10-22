package vn.edu.ptit.web_grading_system.ai_assistant_service.tools;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ToolRegistryTest {

    @Test
    void testFilterToolsByRole() {
        AgentTool studentTool = new AgentTool() {
            @Override public String getName() { return "studentTool"; }
            @Override public String getDescription() { return "Student only"; }
            @Override public ToolDefinition getDefinition() {
                return new ToolDefinition("studentTool", "Student only", "1.0", "student", "read", "sub",
                        EffectType.READ, Set.of("ROLE_STUDENT"), Duration.ofSeconds(5), 10000);
            }
            @Override public ToolResult execute(JsonNode args, AgentContext ctx) { return ToolResult.ok("ok"); }
        };

        AgentTool lecturerTool = new AgentTool() {
            @Override public String getName() { return "lecturerTool"; }
            @Override public String getDescription() { return "Lecturer only"; }
            @Override public ToolDefinition getDefinition() {
                return new ToolDefinition("lecturerTool", "Lecturer only", "1.0", "lecturer", "write", "ass",
                        EffectType.DOMAIN_WRITE, Set.of("ROLE_LECTURER"), Duration.ofSeconds(5), 10000);
            }
            @Override public ToolResult execute(JsonNode args, AgentContext ctx) { return ToolResult.ok("ok"); }
        };

        ToolRegistry registry = new ToolRegistry(List.of(studentTool, lecturerTool));

        List<AgentTool> forStudent = registry.getToolsForRoles(Set.of("ROLE_STUDENT"));
        assertEquals(1, forStudent.size());
        assertEquals("studentTool", forStudent.getFirst().getName());

        List<AgentTool> forLecturer = registry.getToolsForRoles(Set.of("ROLE_LECTURER"));
        assertEquals(1, forLecturer.size());
        assertEquals("lecturerTool", forLecturer.getFirst().getName());
    }

    @Test
    void testAgentToolSpringAiCallbackInterface() {
        AgentTool tool = new AgentTool() {
            @Override public String getName() { return "myTool"; }
            @Override public String getDescription() { return "Description"; }
            @Override public ToolDefinition getDefinition() {
                return new ToolDefinition("myTool", "Description", "1.0", "common", "read", "res",
                        EffectType.READ, Set.of("ROLE_USER"), Duration.ofSeconds(5), 1000);
            }
            @Override public ToolResult execute(JsonNode args, AgentContext ctx) {
                return ToolResult.ok("result-data");
            }
        };

        org.springframework.ai.tool.definition.ToolDefinition springAiDef = tool.getToolDefinition();
        assertEquals("myTool", springAiDef.name());
        assertEquals("Description", springAiDef.description());
        assertEquals("\"result-data\"", tool.call("{}"));
    }

    @Test
    void getToolCallbacksForRoles_shouldReturnExecutableToolCallbacks() {
        AgentTool lecturerTool = new AgentTool() {
            @Override public String getName() { return "lecturerTool"; }
            @Override public String getDescription() { return "Lecturer only"; }
            @Override public ToolDefinition getDefinition() {
                return new ToolDefinition("lecturerTool", "Lecturer only", "1.0", "lecturer", "write", "ass",
                        EffectType.DOMAIN_WRITE, Set.of("ROLE_LECTURER"), Duration.ofSeconds(5), 10000);
            }
            @Override public ToolResult execute(JsonNode args, AgentContext ctx) { return ToolResult.ok("ok"); }
        };

        ToolRegistry registry = new ToolRegistry(List.of(lecturerTool));
        AgentContext context = new AgentContext(java.util.UUID.randomUUID(), "test@ptit.edu.vn", Set.of("ROLE_LECTURER"), java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), "secret");
        List<org.springframework.ai.tool.ToolCallback> callbacks = registry.getToolCallbacksForRoles(Set.of("ROLE_LECTURER"), context);

        org.junit.jupiter.api.Assertions.assertNotNull(callbacks);
        org.junit.jupiter.api.Assertions.assertEquals(1, callbacks.size());
        org.springframework.ai.tool.ToolCallback cb = callbacks.get(0);
        org.junit.jupiter.api.Assertions.assertEquals("lecturerTool", cb.getToolDefinition().name());
        org.junit.jupiter.api.Assertions.assertEquals("Lecturer only", cb.getToolDefinition().description());
        org.junit.jupiter.api.Assertions.assertEquals("\"ok\"", cb.call("{}"));
    }
}
