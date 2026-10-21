package vn.edu.ptit.web_grading_system.ai_assistant_service.tools;

import com.fasterxml.jackson.databind.JsonNode;

public interface AgentTool {
    String getName();
    String getDescription();
    ToolDefinition getDefinition();
    ToolResult execute(JsonNode arguments, AgentContext context);
}
