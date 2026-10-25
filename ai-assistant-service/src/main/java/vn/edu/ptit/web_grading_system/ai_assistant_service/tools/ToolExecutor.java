package vn.edu.ptit.web_grading_system.ai_assistant_service.tools;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ToolExecutor {

    private final ToolRegistry toolRegistry;

    public ToolResult execute(String toolName, JsonNode arguments, AgentContext context) {
        Optional<AgentTool> optionalTool = toolRegistry.find(toolName);
        if (optionalTool.isEmpty()) {
            return ToolResult.error("TOOL_NOT_FOUND", "Tool '" + toolName + "' is not registered", false);
        }

        AgentTool tool = optionalTool.get();
        ToolDefinition def = tool.getDefinition();

        if (def.requiredRoles() != null && !def.requiredRoles().isEmpty()) {
            boolean authorized = context.roles() != null &&
                    context.roles().stream().anyMatch(def.requiredRoles()::contains);
            if (!authorized) {
                return ToolResult.error("FORBIDDEN", "User lacks required role for tool '" + toolName + "'", false);
            }
        }

        try {
            return tool.execute(arguments, context);
        } catch (Exception e) {
            log.error("Tool execution failed for tool {}", toolName, e);
            return ToolResult.error("TOOL_EXECUTION_ERROR", e.getMessage() != null ? e.getMessage() : "Unknown error", false);
        }
    }
}
