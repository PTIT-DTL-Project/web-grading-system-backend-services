package vn.edu.ptit.web_grading_system.ai_assistant_service.tools;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class ToolRegistry {

    private final Map<String, AgentTool> toolsByName = new HashMap<>();

    public ToolRegistry(List<AgentTool> tools) {
        if (tools != null) {
            for (AgentTool tool : tools) {
                toolsByName.put(tool.getName(), tool);
            }
        }
    }

    public Optional<AgentTool> find(String name) {
        return Optional.ofNullable(toolsByName.get(name));
    }

    public List<AgentTool> getToolsForRoles(Set<String> userRoles) {
        if (userRoles == null || userRoles.isEmpty()) {
            return Collections.emptyList();
        }
        return toolsByName.values().stream()
                .filter(t -> t.getDefinition().requiredRoles() != null &&
                        t.getDefinition().requiredRoles().stream().anyMatch(userRoles::contains))
                .collect(Collectors.toList());
    }

    public List<AgentTool> getAllTools() {
        return new ArrayList<>(toolsByName.values());
    }

    public List<org.springframework.ai.tool.ToolCallback> getToolCallbacksForRoles(Set<String> userRoles, AgentContext context) {
        List<AgentTool> authorizedTools = getToolsForRoles(userRoles);
        List<org.springframework.ai.tool.ToolCallback> callbacks = new ArrayList<>();
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        for (AgentTool tool : authorizedTools) {
            callbacks.add(new org.springframework.ai.tool.ToolCallback() {
                @Override
                public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
                    return tool.getToolDefinition();
                }

                @Override
                public String call(String toolInput) {
                    return call(toolInput, null);
                }

                @Override
                public String call(String toolInput, org.springframework.ai.chat.model.ToolContext toolContext) {
                    try {
                        com.fasterxml.jackson.databind.JsonNode args = (toolInput != null && !toolInput.isBlank())
                                ? mapper.readTree(toolInput)
                                : mapper.createObjectNode();
                        ToolResult res = tool.execute(args, context);
                        return res != null && res.getData() != null ? mapper.writeValueAsString(res.getData()) : "{}";
                    } catch (Exception e) {
                        return "{\"error\":\"" + e.getMessage() + "\"}";
                    }
                }
            });
        }
        return callbacks;
    }
}
